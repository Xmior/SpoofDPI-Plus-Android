package com.xmior.spoofdpiplus;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DNS-over-HTTPS çözücü (1.1.1.1). ISS'nin DNS zehirlemesini aşar.
 * Sorgular Proxy.NO_PROXY ile yapılır; yoksa Wi-Fi proxy'si yüzünden kendimize döneriz.
 */
final class Dns {
    private static final String DOH = "https://1.1.1.1/dns-query";
    private static final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    private static final class Entry {
        final InetAddress[] addrs; final long expires;
        Entry(InetAddress[] a, long e) { addrs = a; expires = e; }
    }

    private Dns() {}

    static void clear() { cache.clear(); }

    static InetAddress[] resolve(String host) throws Exception {
        if (isLiteral(host)) return new InetAddress[]{InetAddress.getByName(host)};
        String key = host.toLowerCase();
        Entry e = cache.get(key);
        if (e != null && e.expires > System.currentTimeMillis()) return e.addrs;

        List<InetAddress> out = new ArrayList<>();
        long ttl = 300;
        try {
            ttl = Math.min(ttl, query(host, "A", out));
            if (out.isEmpty()) ttl = Math.min(ttl, query(host, "AAAA", out));
        } catch (Exception ex) {
            Logs.w("DoH başarısız (" + host + "): " + ex.getMessage() + " — sistem DNS'i deneniyor");
        }
        if (out.isEmpty()) {
            for (InetAddress a : InetAddress.getAllByName(host)) out.add(a);
            ttl = 60;
        }
        InetAddress[] arr = out.toArray(new InetAddress[0]);
        cache.put(key, new Entry(arr, System.currentTimeMillis() + Math.max(30, ttl) * 1000));
        return arr;
    }

    private static long query(String host, String type, List<InetAddress> out) throws Exception {
        URL url = new URL(DOH + "?name=" + URLEncoder.encode(host, "UTF-8") + "&type=" + type);
        HttpURLConnection c = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setRequestProperty("accept", "application/dns-json");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[4096]; int n;
            while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            JSONArray ans = new JSONObject(bo.toString("UTF-8")).optJSONArray("Answer");
            long ttl = 300;
            if (ans == null) return ttl;
            int want = type.equals("A") ? 1 : 28;
            for (int i = 0; i < ans.length(); i++) {
                JSONObject a = ans.getJSONObject(i);
                if (a.optInt("type") != want) continue;
                out.add(InetAddress.getByName(a.getString("data")));
                ttl = Math.min(ttl, a.optLong("TTL", 300));
            }
            return ttl;
        } finally {
            c.disconnect();
        }
    }

    private static boolean isLiteral(String h) {
        return h.indexOf(':') >= 0 || h.matches("[0-9.]+");
    }
}
