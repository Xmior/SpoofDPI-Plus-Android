package com.xmior.spoofdpiplus;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

/** Ana ekran: macOS sürümündeki durum paneli ve düğmeler. */
public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable listener = this::render;
    private final Runnable ticker = new Runnable() {
        @Override public void run() { render(); ui.postDelayed(this, 10000); }
    };

    private TextView vService, vPort, vWifi, vDiscord, warn, err;
    private View dService, dPort, dWifi, dDiscord;
    private Button mainBtn, batteryBtn;
    private Boolean discordOK = null;
    private boolean testing = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(buildUi());
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        ProxyService.addListener(listener);
        ui.post(ticker);
        testDiscord();
    }

    @Override protected void onPause() {
        ProxyService.removeListener(listener);
        ui.removeCallbacks(ticker);
        super.onPause();
    }

    // ---- Durum ----

    private void render() {
        boolean on = ProxyService.isRunning();
        boolean prx = ProxyService.proxyConfigured(this);
        row(dService, vService, on, getString(on ? R.string.status_running : R.string.status_stopped));
        row(dPort, vPort, on, getString(on ? R.string.status_listening : R.string.status_closed));
        row(dWifi, vWifi, prx, getString(prx ? R.string.status_set : R.string.status_notset));
        if (testing) row(dDiscord, vDiscord, null, getString(R.string.status_testing));
        else if (discordOK == null) row(dDiscord, vDiscord, null, getString(R.string.status_na));
        else row(dDiscord, vDiscord, discordOK,
                getString(discordOK ? R.string.status_reachable : R.string.status_unreachable));

        mainBtn.setText(on ? R.string.btn_stop : R.string.btn_start);
        mainBtn.setBackground(round(color(on ? R.color.bad : R.color.accent), 14));
        warn.setVisibility(!on && prx ? View.VISIBLE : View.GONE);
        String e = ProxyService.lastError;
        err.setVisibility(e != null ? View.VISIBLE : View.GONE);
        if (e != null) err.setText(getString(R.string.error_start, e));
        PowerManager pm = getSystemService(PowerManager.class);
        batteryBtn.setVisibility(pm.isIgnoringBatteryOptimizations(getPackageName()) ? View.GONE : View.VISIBLE);
    }

    private void row(View dot, TextView v, Boolean good, String text) {
        int c = good == null ? color(R.color.sub) : color(good ? R.color.ok : R.color.bad);
        dot.setBackground(round(c, 6));
        v.setText(text);
    }

    /** discord.com'a proxy üzerinden erişim testi (macOS sürümündeki curl testi). */
    private void testDiscord() {
        if (testing || !ProxyService.isRunning()) { discordOK = null; render(); return; }
        testing = true;
        render();
        new Thread(() -> {
            boolean ok;
            try {
                Proxy p = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(ProxyServer.HOST, ProxyServer.PORT));
                HttpURLConnection c = (HttpURLConnection) new URL("https://discord.com/").openConnection(p);
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                c.setRequestMethod("HEAD");
                c.setInstanceFollowRedirects(false);
                ok = c.getResponseCode() > 0;
                c.disconnect();
            } catch (Exception e) {
                ok = false;
                Logs.w("discord.com testi: " + e.getMessage());
            }
            boolean r = ok;
            ui.post(() -> { discordOK = r; testing = false; render(); });
        }).start();
    }

    // ---- Eylemler ----

    private void toggle() {
        if (ProxyService.isRunning()) {
            ProxyService.stop(this);
        } else {
            ProxyService.start(this);
            ui.postDelayed(this::testDiscord, 800);
        }
    }

    private void openWifi() { startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); }

    private void askBattery() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void info(int title, int body) {
        TextView tv = text("", 14, R.color.text);
        tv.setText(getText(body));
        tv.setLineSpacing(0, 1.25f);
        tv.setTextIsSelectable(true);
        tv.setPadding(dp(22), dp(10), dp(22), dp(10));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this).setTitle(title).setView(sv)
                .setPositiveButton(R.string.close, null).show();
    }

    private void showLogs() {
        String t = Logs.text();
        TextView tv = new TextView(this);
        tv.setText(t.isEmpty() ? getString(R.string.logs_empty) : t);
        tv.setTextIsSelectable(true);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextSize(11);
        tv.setPadding(dp(20), dp(12), dp(20), dp(12));
        ScrollView sv = new ScrollView(this);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_logs)
                .setView(sv)
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.clear, (d, w) -> Logs.clear())
                .show();
        sv.post(() -> sv.fullScroll(View.FOCUS_DOWN));
    }

    private void copy(String s) {
        getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("SpoofDPI+", s));
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    // ---- Arayüz ----

    private View buildUi() {
        LinearLayout root = col();
        root.setPadding(dp(16), dp(20), dp(16), dp(28));

        // Başlık
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        head.addView(logo, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout ht = col();
        ht.setPadding(dp(14), 0, 0, 0);
        TextView title = text(getString(R.string.app_name), 26, R.color.text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        ht.addView(title);
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        ht.addView(text("v" + ver + " · SpoofDPI", 13, R.color.sub));
        head.addView(ht, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(head);

        TextView tag = text(getString(R.string.tagline), 13, R.color.sub);
        tag.setPadding(0, dp(10), 0, dp(14));
        root.addView(tag);

        warn = text(getString(R.string.warn_danger), 14, R.color.text);
        warn.setBackground(round(withAlpha(color(R.color.warn), 0x40), 14));
        warn.setPadding(dp(14), dp(12), dp(14), dp(12));
        root.addView(warn, spaced());
        err = text("", 14, R.color.bad);
        root.addView(err, spaced());

        // Durum kartı
        LinearLayout status = card();
        status.addView(header(R.string.status_title));
        View[] r;
        r = statusRow(status, R.string.row_service); dService = r[0]; vService = (TextView) r[1];
        r = statusRow(status, R.string.row_port); dPort = r[0]; vPort = (TextView) r[1];
        r = statusRow(status, R.string.row_wifi); dWifi = r[0]; vWifi = (TextView) r[1];
        r = statusRow(status, R.string.row_discord); dDiscord = r[0]; vDiscord = (TextView) r[1];
        vDiscord.setOnClickListener(v -> testDiscord());
        root.addView(status, spaced());

        mainBtn = button("", R.color.accent, 0xFFFFFFFF);
        mainBtn.setTextSize(18);
        mainBtn.setOnClickListener(v -> toggle());
        root.addView(mainBtn, new LinearLayout.LayoutParams(-1, dp(58)));
        root.addView(spacer(14));

        // Kurulum kartı
        LinearLayout setup = card();
        setup.addView(header(R.string.setup_title));
        TextView steps = text(getString(R.string.setup_steps), 14, R.color.text);
        steps.setLineSpacing(0, 1.25f);
        setup.addView(steps);
        setup.addView(spacer(10));
        setup.addView(valueRow(R.string.setup_host, ProxyServer.HOST));
        setup.addView(valueRow(R.string.setup_port, String.valueOf(ProxyServer.PORT)));
        setup.addView(spacer(8));
        setup.addView(text(getString(R.string.setup_note), 12, R.color.sub));
        setup.addView(spacer(12));
        Button wifi = button(getString(R.string.btn_wifi), R.color.btn2, color(R.color.text));
        wifi.setOnClickListener(v -> openWifi());
        setup.addView(wifi, new LinearLayout.LayoutParams(-1, dp(48)));
        root.addView(setup, spaced());

        // Araçlar
        LinearLayout tools = card();
        tools.addView(header(R.string.tools_title));
        Button logs = button(getString(R.string.btn_logs), R.color.btn2, color(R.color.text));
        logs.setOnClickListener(v -> showLogs());
        tools.addView(logs, btnLp());
        batteryBtn = button(getString(R.string.btn_battery), R.color.btn2, color(R.color.text));
        batteryBtn.setOnClickListener(v -> askBattery());
        tools.addView(batteryBtn, btnLp());
        Button help = button(getString(R.string.btn_help), R.color.btn2, color(R.color.text));
        help.setOnClickListener(v -> info(R.string.btn_help, R.string.help_text));
        tools.addView(help, btnLp());
        Button about = button(getString(R.string.btn_about), R.color.btn2, color(R.color.text));
        about.setOnClickListener(v -> info(R.string.btn_about, R.string.about_text));
        tools.addView(about, btnLp());
        Button quit = button(getString(R.string.btn_quit), R.color.btn2, color(R.color.bad));
        quit.setOnClickListener(v -> ProxyService.quitApp(this));
        tools.addView(quit, btnLp());
        root.addView(tools, spaced());

        TextView footer = text(getString(R.string.footer), 13, R.color.sub);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(6), 0, 0);
        root.addView(footer, new LinearLayout.LayoutParams(-1, -2));

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.addView(root);
        // Android 15 kenardan kenara çizer: durum ve gezinme çubuklarına pay bırak
        sv.setOnApplyWindowInsetsListener((v, in) -> {
            android.graphics.Insets s = Build.VERSION.SDK_INT >= 30
                    ? in.getInsets(android.view.WindowInsets.Type.systemBars()) : null;
            if (s != null) root.setPadding(dp(16) + s.left, dp(20) + s.top, dp(16) + s.right, dp(28) + s.bottom);
            return in;
        });
        return sv;
    }

    private View[] statusRow(LinearLayout parent, int label) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(7), 0, dp(7));
        View dot = new View(this);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(10), dp(10));
        dl.rightMargin = dp(12);
        row.addView(dot, dl);
        row.addView(text(getString(label), 15, R.color.text), new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = text("", 15, R.color.sub);
        row.addView(v);
        parent.addView(row);
        return new View[]{dot, v};
    }

    private View valueRow(int label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(round(color(R.color.btn2), 10));
        row.addView(text(getString(label), 14, R.color.sub), new LinearLayout.LayoutParams(0, -2, 1));
        TextView v = text(value, 16, R.color.text);
        v.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        row.addView(v);
        row.setOnClickListener(x -> copy(value));
        LinearLayout wrap = col();
        wrap.addView(row);
        wrap.addView(spacer(6));
        return wrap;
    }

    private LinearLayout card() {
        LinearLayout c = col();
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        c.setBackground(round(color(R.color.card), 18));
        return c;
    }

    private TextView header(int res) {
        TextView t = text(getString(res), 13, R.color.sub);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setAllCaps(true);
        t.setPadding(0, 0, 0, dp(8));
        return t;
    }

    private Button button(String label, int bgRes, int fg) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setTextSize(15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setBackground(round(color(bgRes), 12));
        return b;
    }

    private LinearLayout.LayoutParams btnLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.bottomMargin = dp(8);
        return lp;
    }

    private LinearLayout.LayoutParams spaced() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(14);
        return lp;
    }

    private LinearLayout col() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    private View spacer(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h)));
        return v;
    }

    private TextView text(String s, float sp, int colorRes) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color(colorRes));
        return t;
    }

    private GradientDrawable round(int c, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(c);
        g.setCornerRadius(dp(r));
        return g;
    }

    private static int withAlpha(int c, int a) { return (c & 0x00FFFFFF) | (a << 24); }
    private int color(int res) { return getColor(res); }
    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
    }
}
