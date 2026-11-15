package com.briancodez.padstudio;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public final class MappingTileService extends TileService {
    @Override public void onStartListening() { update(); }
    @Override public void onClick() {
        if (MappingService.instance == null) { update(); return; }
        Store store = new Store(this);
        Model.Settings settings = store.load(); settings.enabled = !settings.enabled; store.save(settings);
        update();
    }
    private void update() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean available = MappingService.instance != null;
        boolean enabled = new Store(this).load().enabled;
        tile.setLabel("PadStudio");
        tile.setSubtitle(available ? enabled ? "Mapping active" : "Mapping paused" : "Enable accessibility");
        tile.setState(!available ? Tile.STATE_UNAVAILABLE : enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
