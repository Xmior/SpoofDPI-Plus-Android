package com.xmior.spoofdpiplus;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.ProxyInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.TileService;

import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Proxy'yi arka planda canlı tutan servis. Kalıcı bildirim, macOS sürümündeki
 * menü çubuğu menüsünün karşılığıdır: durum + Başlat/Durdur, Wi-Fi, Tamamen kapat.
 */
public class ProxyService extends Service {
    static final String ACTION_START = "com.xmior.spoofdpiplus.START";
    static final String ACTION_STOP = "com.xmior.spoofdpiplus.STOP";
    static final String ACTION_QUIT = "com.xmior.spoofdpiplus.QUIT";
    static final String CHANNEL = "spoofdpiplus";
    static final int NOTIF_ID = 1;

    private static final ProxyServer proxy = new ProxyServer();
    private static final CopyOnWriteArraySet<Runnable> listeners = new CopyOnWriteArraySet<>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    static volatile String lastError = null;

    private ConnectivityManager.NetworkCallback netCb;

    static boolean isRunning() { return proxy.isRunning(); }

    static void addListener(Runnable r) { listeners.add(r); }
    static void removeListener(Runnable r) { listeners.remove(r); }

    static void notifyChanged(Context ctx) {
        main.post(() -> { for (Runnable r : listeners) r.run(); });
        TileService.requestListeningState(ctx, new ComponentName(ctx, ToggleTile.class));
    }

    /** Wi-Fi (varsayılan ağ) proxy'si bizim adrese ayarlı mı? */
    static boolean proxyConfigured(Context ctx) {
        ConnectivityManager cm = ctx.getSystemService(ConnectivityManager.class);
        ProxyInfo p = cm.getDefaultProxy();
        if (p == null) {
            Network n = cm.getActiveNetwork();
            LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
            p = lp == null ? null : lp.getHttpProxy();
        }
        return p != null && p.getPort() == ProxyServer.PORT
                && ("127.0.0.1".equals(p.getHost()) || "localhost".equalsIgnoreCase(p.getHost()));
    }

    static void start(Context ctx) {
        ctx.startForegroundService(new Intent(ctx, ProxyService.class).setAction(ACTION_START));
    }

    static void stop(Context ctx) {
        ctx.startService(new Intent(ctx, ProxyService.class).setAction(ACTION_STOP));
    }

    /** Uygulamayı tamamen kapatır: proxy, servis, bildirim ve süreç. */
    static void quitApp(Context ctx) {
        proxy.stop();
        prefs(ctx).edit().putBoolean("enabled", false).commit();
        ctx.getSystemService(NotificationManager.class).cancelAll();
        ctx.stopService(new Intent(ctx, ProxyService.class));
        android.app.ActivityManager am = ctx.getSystemService(android.app.ActivityManager.class);
        for (android.app.ActivityManager.AppTask t : am.getAppTasks()) t.finishAndRemoveTask();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("spoofdpiplus", MODE_PRIVATE);
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CHANNEL, getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);

        // Wi-Fi proxy ayarı değişince bildirim ve ekran güncellensin
        netCb = new ConnectivityManager.NetworkCallback() {
            @Override public void onLinkPropertiesChanged(Network n, LinkProperties lp) { refresh(); }
            @Override public void onAvailable(Network n) { refresh(); }
            @Override public void onLost(Network n) { refresh(); }
        };
        try { getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(netCb); }
        catch (Exception ignored) { netCb = null; }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_QUIT.equals(action)) {
            quitApp(this);
            return START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action)) {
            proxy.stop();
            prefs(this).edit().putBoolean("enabled", false).apply();
            stopForeground(STOP_FOREGROUND_REMOVE);
            // Kapalıyken de bildirim kalsın (Başlat düğmesiyle), menü çubuğu gibi.
            getSystemService(NotificationManager.class).notify(NOTIF_ID, build());
            notifyChanged(this);
            stopSelf();
            return START_NOT_STICKY;
        }

        // Önce proxy (anlık), sonra bildirim: bildirim doğru durumla çıksın
        try {
            proxy.start();
            lastError = null;
            prefs(this).edit().putBoolean("enabled", true).apply();
        } catch (Exception e) {
            lastError = e.getMessage();
            Logs.w("Başlatılamadı: " + e.getMessage());
        }
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, build());
        }
        refresh();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (netCb != null) {
            try { getSystemService(ConnectivityManager.class).unregisterNetworkCallback(netCb); }
            catch (Exception ignored) {}
        }
        super.onDestroy();
    }

    private void refresh() {
        if (isRunning()) getSystemService(NotificationManager.class).notify(NOTIF_ID, build());
        notifyChanged(this);
    }

    private Notification build() {
        boolean on = isRunning();
        boolean prx = proxyConfigured(this);
        String title = getString(on ? R.string.notif_on : R.string.notif_off);
        String text;
        if (on && prx) text = getString(R.string.notif_text_ok);
        else if (on) text = getString(R.string.notif_text_noproxy);
        else if (prx) text = getString(R.string.notif_text_danger);
        else text = getString(R.string.notif_text_off);

        int piFlags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), piFlags);
        PendingIntent toggle = on
                ? PendingIntent.getService(this, 1,
                    new Intent(this, ProxyService.class).setAction(ACTION_STOP), piFlags)
                : PendingIntent.getForegroundService(this, 2,
                    new Intent(this, ProxyService.class).setAction(ACTION_START), piFlags);
        PendingIntent wifi = PendingIntent.getActivity(this, 3,
                new Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), piFlags);

        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setOngoing(on)
                .setShowWhen(false)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setColor(0xFF2F6BFF)
                .addAction(new Notification.Action.Builder(null,
                        getString(on ? R.string.btn_stop : R.string.btn_start), toggle).build());

        // Bildirim düğmeleri: Başlat/Durdur, Wi-Fi ayarları, Tamamen kapat
        b.addAction(new Notification.Action.Builder(null, getString(R.string.btn_wifi), wifi).build());
        b.addAction(new Notification.Action.Builder(null, getString(R.string.btn_quit),
                PendingIntent.getService(this, 5,
                        new Intent(this, ProxyService.class).setAction(ACTION_QUIT), piFlags)).build());
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }
}
