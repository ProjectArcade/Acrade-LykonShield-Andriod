package com.arcadesoftware.lykonshield

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

object AppConfigManager {
    private const val CONFIG_URL = "https://cdn.jsdelivr.net/gh/ProjectArcade/Acrade-LykonShield-list@main/version.json"
    
    var requiresHardUpdate = false
    var requiresSoftUpdate = false
    var updateUrl = ""
    var updateMessage = ""

    suspend fun checkRemoteConfig(context: Context, currentVersionCode: Int) {
        withContext(Dispatchers.IO) {
            try {
                val jsonString = URL(CONFIG_URL).readText()
                val root = JSONObject(jsonString)
                
                val updates = root.getJSONObject("app_updates").getJSONObject("android")
                
                val minSupportedCode = updates.getInt("min_supported_version_code")
                val maxSupportedCode = updates.getInt("max_supported_version_code")
                
                updateUrl = updates.optString("update_url", "")
                updateMessage = updates.optString("release_notes", "")
                
                requiresHardUpdate = currentVersionCode < minSupportedCode
                requiresSoftUpdate = !requiresHardUpdate && currentVersionCode < maxSupportedCode
                
                val prefs = context.getSharedPreferences("lykon_shield_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("requires_hard_update", requiresHardUpdate).apply()
                
            } catch (e: Exception) {
                Log.e("AppConfigManager", "Failed to fetch config", e)
            }
        }
    }
}
