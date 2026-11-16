package com.xmior.spoofdpiplus;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SpoofDPI'ın Android karşılığı: 127.0.0.1:PORT üzerinde HTTP proxy.
 *  - DNS'i DNS-over-HTTPS ile çözer (Dns sınıfı)
 *  - HTTPS bağlantısının ilk paketini (TLS ClientHello) 1 baytlık parçalar halinde
 *    gönderir; DPI site adını (SNI) okuyamaz. macOS sürümündeki chunk-size = 1 ayarı.
 */
final class ProxyServer {
    static final String HOST = "127.0.0.1";
    static final int PORT = 8080;

    private ServerSocket server;
    private ExecutorService pool;

    synchronized void start() throws IOException {
        if (server != null) return;
        ServerSocket ss = new ServerSocket();
        ss.setReuseAddress(true);
        ss.bind(new InetSocketAddress(InetAddress.getByName(HOST), PORT), 256);
        server = ss;
        pool = Executors.newCachedThreadPool();
        Thread t = new Thread(() -> acceptLoop(ss), "sw-accept");
        t.setDaemon(true);
        t.start();
        Logs.i("Proxy başladı: " + HOST + ":" + PORT);
    }

    synchronized void stop() {
        if (server == null) return;
        try { server.close(); } catch (IOException ignored) {}
        pool.shutdownNow();
        server = null;
        pool = null;
        Dns.clear();
        Logs.i("Proxy durdu");
    }

    synchronized boolean isRunning() { return server != null && !server.isClosed(); }

    private void acceptLoop(ServerSocket ss) {
        while (!ss.isClosed()) {
            try {
                Socket c = ss.accept();
                ExecutorService p = pool;
                if (p == null) { c.close(); break; }
                p.execute(() -> handle(c));
            } catch (Exception e) {
                if (!ss.isClosed()) Logs.w("accept: " + e.getMessage());
            }
        }
    }

    private void handle(Socket client) {
        Socket remote = null;
        try {
            client.setSoTimeout(30000);
            InputStream cin = client.getInputStream();
            OutputStream cout = client.getOutputStream();

            String reqLine = readLine(cin);
            if (reqLine == null || reqLine.isEmpty()) { client.close(); return; }
            List<String> headers = new ArrayList<>();
            for (String h; (h = readLine(cin)) != null && !h.isEmpty(); ) headers.add(h);

            String[] p = reqLine.split(" ");
            if (p.length < 3) { client.close(); return; }
            String method = p[0], target = p[1], version = p[2];

            if (method.equalsIgnoreCase("CONNECT")) {
                int colon = target.lastIndexOf(':');
                String host = target.substring(0, colon);
                int port = Integer.parseInt(target.substring(colon + 1));
                if (host.startsWith("[")) host = host.substring(1, host.length() - 1);
                remote = connect(host, port);
                cout.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                cout.flush();

                // İlk paket: TLS ClientHello. Parçalayarak gönder.
                byte[] buf = new byte[16384];
                int n = cin.read(buf);
                if (n <= 0) return;
                OutputStream rout = remote.getOutputStream();
                if (buf[0] == 0x16) {
                    writeHello(rout, buf, n);
                } else {
                    rout.write(buf, 0, n);
                }
                rout.flush();
            } else {
                // Düz HTTP: http://host/yol -> /yol
                URI u = new URI(target);
                String host = u.getHost();
                if (host == null) { reject(cout); return; }
                int port = u.getPort() > 0 ? u.getPort() : 80;
                String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
                if (u.getRawQuery() != null) path += "?" + u.getRawQuery();
                remote = connect(host, port);
                StringBuilder sb = new StringBuilder();
                sb.append(method).append(' ').append(path).append(' ').append(version).append("\r\n");
                for (String h : headers) {
                    String l = h.toLowerCase();
                    if (l.startsWith("proxy-connection:") || l.startsWith("connection:")
                            || l.startsWith("keep-alive:")) continue;
                    sb.append(h).append("\r\n");
                }
                // Proxy bağlantısını tek isteğe indir; başka hosta yeniden kullanılmasın.
                sb.append("Connection: close\r\n\r\n");
                OutputStream rout = remote.getOutputStream();
                rout.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
                rout.flush();
            }

            client.setSoTimeout(0);
            remote.setSoTimeout(0);
            Socket r = remote;
            Thread up = new Thread(() -> pipe(client, r), "sw-up");
            up.setDaemon(true);
            up.start();
            pipe(r, client);
            remote = null; // pipe kapatır
        } catch (Exception e) {
            Logs.w("bağlantı: " + e.getClass().getSimpleName() + " " + e.getMessage());
            try { reject(client.getOutputStream()); } catch (Exception ignored) {}
        } finally {
            if (remote != null) try { remote.close(); } catch (IOException ignored) {}
        }
    }

    /** İlk kaç bayt tek tek gönderilsin (DPI ~20 küçük paketten sonra bakmayı bırakıyor). */
    static final int FRAGMENTS = 40;
    /** Parçalar arası bekleme (ms). Linux/Android çekirdeği art arda gelen küçük
     *  yazmaları birleştirir (TCP autocorking); bekleme her baytın ayrı pakette gitmesini sağlar. */
    static final int GAP_MS = 2;

    private static void writeHello(OutputStream out, byte[] b, int n) throws IOException {
        int k = Math.min(FRAGMENTS, n);
        for (int i = 0; i < k; i++) {
            out.write(b, i, 1);
            out.flush();
            try { Thread.sleep(GAP_MS); } catch (InterruptedException e) { throw new IOException(e); }
        }
        if (n > k) out.write(b, k, n - k);
    }

    private static Socket connect(String host, int port) throws Exception {
        Exception last = null;
        for (InetAddress a : Dns.resolve(host)) {
            Socket s = new Socket();
            try {
                s.setTcpNoDelay(true);
                s.connect(new InetSocketAddress(a, port), 10000);
                return s;
            } catch (IOException e) {
                last = e;
                s.close();
            }
        }
        throw last != null ? last : new IOException("adres yok: " + host);
    }

    private static void reject(OutputStream out) {
        try {
            out.write("HTTP/1.1 502 Bad Gateway\r\nConnection: close\r\nContent-Length: 0\r\n\r\n"
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();
        } catch (IOException ignored) {}
    }

    /** Bir yönü kopyalar; bitince iki soketi de kapatır. */
    private static void pipe(Socket from, Socket to) {
        byte[] b = new byte[32768];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            for (int n; (n = in.read(b)) > 0; ) { out.write(b, 0, n); out.flush(); }
        } catch (IOException ignored) {
        } finally {
            try { from.close(); } catch (IOException ignored) {}
            try { to.close(); } catch (IOException ignored) {}
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        for (int c; (c = in.read()) != -1; ) {
            if (c == '\n') break;
            if (c != '\r') bo.write(c);
            if (bo.size() > 16384) throw new IOException("başlık çok uzun");
        }
        return bo.size() == 0 && in.available() < 0 ? null : bo.toString("ISO-8859-1");
    }
}
