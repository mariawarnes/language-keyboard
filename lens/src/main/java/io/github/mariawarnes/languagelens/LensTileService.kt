package io.github.mariawarnes.languagelens

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick settings tile: pause or resume Language Lens from anywhere. */
class LensTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        LensPrefs.setPaused(this, !LensPrefs.paused(this))
        update()
    }

    private fun update() {
        val tile = qsTile ?: return
        tile.state = if (LensPrefs.paused(this)) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
