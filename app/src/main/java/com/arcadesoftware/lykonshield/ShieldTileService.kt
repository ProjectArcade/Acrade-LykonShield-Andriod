package com.arcadesoftware.lykonshield

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.content.Context

class ShieldTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences("lykon_shield_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("protection_enabled", false)
        
        val newState = !isEnabled
        prefs.edit().putBoolean("protection_enabled", newState).apply()
        
        if (newState) {
            val intent = Intent(this, LykonVpnService::class.java).apply {
                action = LykonVpnService.ACTION_START
            }
            startService(intent)
        } else {
            val intent = Intent(this, LykonVpnService::class.java).apply {
                action = LykonVpnService.ACTION_STOP
            }
            startService(intent)
        }
        
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val prefs = getSharedPreferences("lykon_shield_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("protection_enabled", false)
        val isActive = LykonVpnService.isVpnActive

        if (!isEnabled) {
            tile.state = Tile.STATE_INACTIVE
            tile.label = "Lykon Shield"
        } else {
            tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = "Lykon Shield"
        }
        
        tile.icon = Icon.createWithResource(this, R.drawable.dark_icon)
        tile.updateTile()
    }
}
