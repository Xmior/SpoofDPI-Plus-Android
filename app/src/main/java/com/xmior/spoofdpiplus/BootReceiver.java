package com.xmior.spoofdpiplus;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Telefon açılınca, kapanmadan önce açıksa proxy'yi yeniden başlatır.
 * Wi-Fi proxy'si açık kalırsa ve proxy çalışmazsa o Wi-Fi'da internet kesilir.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context ctx, Intent intent) {
        String a = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        if (ProxyService.prefs(ctx).getBoolean("enabled", false)) {
            try { ProxyService.start(ctx); } catch (Exception e) { Logs.w("Açılışta başlatılamadı: " + e); }
        }
    }
}
