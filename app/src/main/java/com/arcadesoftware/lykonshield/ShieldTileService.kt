package com.arcadesoftware.lykonshield

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class ShieldTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = getSharedPreferences("lykon_shield_prefs", Context.MODE_PRIVATE)
        val requiresHardUpdate = prefs.getBoolean("requires_hard_update", false)

        if (requiresHardUpdate) {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        val isEnabled = prefs.getBoolean("protection_enabled", false)
        
        val newState = !isEnabled
        prefs.edit().putBoolean("protection_enabled", newState).apply()
        
        if (newState) {
            val intent = Intent(this, LykonVpnService::class.java).apply {
                action = LykonVpnService.ACTION_START
            }
            ContextCompat.startForegroundService(this, intent)
        } else {
            val intent = Intent(this, LykonVpnService::class.java).apply {
                action = LykonVpnService.ACTION_STOP
            }
            ContextCompat.startForegroundService(this, intent)
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
