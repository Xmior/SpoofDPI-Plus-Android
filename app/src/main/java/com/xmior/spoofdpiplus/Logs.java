package com.xmior.spoofdpiplus;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

/** Uygulama içi günlük (son 400 satır). */
final class Logs {
    private static final ArrayDeque<String> lines = new ArrayDeque<>();
    private static final SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private Logs() {}

    static void i(String m) { add("I", m); Log.i("SpoofDPI+", m); }
    static void w(String m) { add("W", m); Log.w("SpoofDPI+", m); }

    private static synchronized void add(String lvl, String m) {
        lines.addLast(fmt.format(new Date()) + " " + lvl + " " + m);
        while (lines.size() > 400) lines.removeFirst();
    }

    static synchronized String text() {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(l).append('\n');
        return sb.toString();
    }

    static synchronized void clear() { lines.clear(); }
}
