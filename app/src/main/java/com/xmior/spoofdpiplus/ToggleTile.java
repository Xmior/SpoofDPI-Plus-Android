package com.xmior.spoofdpiplus;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Hızlı ayarlar kutucuğu: bildirim panelinden tek dokunuşla aç/kapat. */
public class ToggleTile extends TileService {
    private final Runnable update = this::update;

    @Override public void onStartListening() {
        ProxyService.addListener(update);
        update();
    }

    @Override public void onStopListening() { ProxyService.removeListener(update); }

    @Override public void onClick() {
        if (ProxyService.isRunning()) ProxyService.stop(this);
        else ProxyService.start(this);
    }

    private void update() {
        Tile t = getQsTile();
        if (t == null) return;
        boolean on = ProxyService.isRunning();
        t.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.setLabel(getString(R.string.app_name));
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            t.setSubtitle(getString(on ? R.string.status_on : R.string.status_off));
        }
        t.updateTile();
    }
}
