package com.arcadesoftware.lykonshield

import android.content.Context
import android.util.Log
import com.arcadesoftware.lykon.AdblockEngine
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background filter list updater that periodically downloads fresh ad/tracker
 * blocklists from well-known sources and hot-reloads them into AdblockEngine.
 *
 * This ensures the blocker stays effective against new ad domains (domain fluxing)
 * without requiring an app update.
 *
 * Usage:
 *   FilterListUpdater.checkAndUpdate(context)         // Auto-check (respects 24h interval)
 *   FilterListUpdater.checkAndUpdate(context, true)   // Force update now
 */
object FilterListUpdater {

    private const val TAG = "FilterListUpdater"
    private const val PREFS_NAME = "lykon_filter_updater"
    private const val KEY_LAST_UPDATE = "last_update_time"
    private const val UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L  // 24 hours
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_RETRIES = 2

    private val isUpdating = AtomicBoolean(false)

    val updateProgress = kotlinx.coroutines.flow.MutableStateFlow(-1f)
    val updateStatus = kotlinx.coroutines.flow.MutableStateFlow("")

    /**
     * Check if filter lists need updating and download them if necessary.
     * Runs on a background thread. Safe to call from any thread.
     *
     * @param context Application context
     * @param force If true, ignore the 24-hour cooldown and update immediately
     */
    fun checkAndUpdate(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!force) {
            val lastUpdate = prefs.getLong(KEY_LAST_UPDATE, 0)
            val elapsed = System.currentTimeMillis() - lastUpdate
            if (elapsed < UPDATE_INTERVAL_MS) {
                Log.d(TAG, "Filter lists are fresh (updated ${elapsed / 3600000}h ago). Skipping.")
                return
            }
        }

        // Prevent concurrent updates
        if (!isUpdating.compareAndSet(false, true)) {
            Log.d(TAG, "Update already in progress. Skipping.")
            return
        }

        Thread({
            try {
                val autoUpdate = prefs.getBoolean("auto_update_filters", true)
                val notifyUpdate = prefs.getBoolean("notify_app_update", true)
                
                if (!force && !autoUpdate) {
                    if (notifyUpdate) {
                        checkForUpdatesAndNotify(appContext)
                    }
                } else {
                    performUpdate(appContext)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Filter update failed", e)
            } finally {
                isUpdating.set(false)
            }
        }, "filter-updater").start()
    }

    /**
     * Returns the timestamp of the last successful update, or 0 if never updated.
     */
    fun getLastUpdateTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_UPDATE, 0)
    }

    /**
     * Returns true if an update is currently in progress.
     */
    fun isUpdateInProgress(): Boolean = isUpdating.get()

    // ══════════════════════════════════════════════════════════════════════
    // Internal
    // ══════════════════════════════════════════════════════════════════════

    private fun checkForUpdatesAndNotify(context: Context) {
        try {
            val url = URL("https://raw.githubusercontent.com/ProjectArcade/Acrade-LykonShield-list/main/version.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(response)
                val remoteVersion = json.optString("version", "1.0.0")
                
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val lastNotifiedVersion = prefs.getString("last_notified_version", "")
                
                if (remoteVersion != lastNotifiedVersion) {
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    val channelId = "update_alerts"
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        val channel = android.app.NotificationChannel(channelId, "Shield Updates", android.app.NotificationManager.IMPORTANCE_DEFAULT).apply {
                            description = "Alerts for new shield blocklists and filter updates"
                        }
                        notificationManager.createNotificationChannel(channel)
                    }
                    val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("New Shield Filters Available")
                        .setContentText("Version v$remoteVersion is ready to protect your device.")
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                        .setColor(android.graphics.Color.parseColor("#0A84FF")) // Brand color blue
                        .setAutoCancel(true)
                        .build()
                    notificationManager.notify(889, notification)
                    
                    prefs.edit().putString("last_notified_version", remoteVersion).apply()
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
        }
    }

    private fun performUpdate(context: Context) {
        Log.d(TAG, "Starting filter list update...")
        val startTime = System.currentTimeMillis()

        // Ensure the filters directory exists
        val filtersDir = File(context.filesDir, AdblockEngine.FILTERS_DIR)
        if (!filtersDir.exists()) {
            filtersDir.mkdirs()
        }

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "update_progress"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Update Progress", android.app.NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows progress of downloading shield filter lists"
            }
            notificationManager.createNotificationChannel(channel)
        }

        fun updateNotif(progress: Int, text: String) {
            val notif = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .setContentTitle("Updating Shield Filters")
                .setContentText(text)
                .setProgress(100, progress, false)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_LOW)
                .setColor(android.graphics.Color.parseColor("#0A84FF"))
                .setOngoing(true)
                .build()
            notificationManager.notify(887, notif)
        }

        updateProgress.value = 0f
        updateStatus.value = "Fetching metadata..."
        updateNotif(0, "Fetching metadata...")

        var remoteVersionStr = "1.0.0"
        val downloadMap = mutableMapOf<String, String>()
        try {
            val url = URL("https://raw.githubusercontent.com/ProjectArcade/Acrade-LykonShield-list/main/version.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val jsonStr = connection.inputStream.bufferedReader().use { it.readText() }
                val json = org.json.JSONObject(jsonStr)
                remoteVersionStr = json.optString("version", "1.0.0")
                if (json.has("easylist")) downloadMap["easylist.txt"] = json.getString("easylist")
                if (json.has("easyprivacy")) downloadMap["easyprivacy.txt"] = json.getString("easyprivacy")
                if (json.has("malware")) downloadMap["malware.txt"] = json.getString("malware")
                if (json.has("ublock")) downloadMap["ublock-filters.txt"] = json.getString("ublock")
            }
            connection.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote JSON", e)
        }

        if (downloadMap.isEmpty()) {
            // Fallback
            downloadMap["easylist.txt"] = AdblockEngine.FilterSources.EASYLIST_URL
            downloadMap["easyprivacy.txt"] = AdblockEngine.FilterSources.EASYPRIVACY_URL
            downloadMap["ublock-filters.txt"] = AdblockEngine.FilterSources.UBLOCK_FILTERS_URL
            downloadMap["oisd-basic.txt"] = AdblockEngine.FilterSources.OISD_URL
        }

        var successCount = 0
        var failCount = 0
        val total = downloadMap.size
        var currentIdx = 0

        for ((fileName, urlStr) in downloadMap) {
            val baseProgress = currentIdx.toFloat() / total
            updateStatus.value = "Downloading $fileName (${currentIdx + 1}/$total)"
            
            val targetFile = File(filtersDir, fileName)
            val success = downloadWithRetry(urlStr, targetFile) { fileProg ->
                val totalProg = baseProgress + (fileProg / total)
                updateProgress.value = totalProg
                updateNotif((totalProg * 100).toInt(), "Downloading $fileName (${currentIdx + 1}/$total)")
            }
            if (success) {
                successCount++
            } else {
                failCount++
            }
            currentIdx++
        }

        updateProgress.value = 1f
        updateStatus.value = "Filters updated successfully!"
        updateNotif(100, "Filters updated successfully!")
        
        try {
            Thread.sleep(1000)
        } catch (_: Exception) {}

        notificationManager.cancel(887)
        updateProgress.value = -1f
        updateStatus.value = ""

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Filter update complete in ${elapsed}ms: $successCount succeeded, $failCount failed")

        if (successCount > 0) {
            // Record update time and version
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .putString("local_filter_version", remoteVersionStr)
                .apply()

            // Hot-reload the engine with new lists
            try {
                AdblockEngine.reloadFilters(context)
                Log.d(TAG, "AdblockEngine reloaded with fresh filter lists")
                showRestartNotification(context)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reload AdblockEngine after update", e)
            }
        }
    }

    private fun showRestartNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "update_alerts"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Shield Updates", android.app.NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alerts for new shield blocklists and filter updates"
            }
            notificationManager.createNotificationChannel(channel)
        }
        val details = "Shield filters have been updated to the latest version. Please restart the app to ensure all new rules are fully active."
        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("Filters Updated")
            .setContentText("Restart the app to load new shield rules.")
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(details))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setColor(android.graphics.Color.parseColor("#34C759")) // Green brand color for success
            .setAutoCancel(true)
            .build()
        notificationManager.notify(888, notification)
    }

    /**
     * Download a URL to a local file with retry logic.
     * Writes to a temporary file first, then atomically renames to prevent
     * corruption if the download is interrupted.
     */
    private fun downloadWithRetry(urlStr: String, targetFile: File, onProgress: (Float) -> Unit): Boolean {
        for (attempt in 1..MAX_RETRIES + 1) {
            try {
                val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")
                val responseCode = downloadToFile(urlStr, tempFile, targetFile, onProgress)
                if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                    onProgress(1f)
                    return true
                }
                if (responseCode == HttpURLConnection.HTTP_OK && tempFile.exists() && tempFile.length() > 100) {
                    // Atomic rename
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
                    onProgress(1f)
                    return true
                } else {
                    tempFile.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Download attempt $attempt failed for $urlStr: ${e.message}")
                if (attempt <= MAX_RETRIES) {
                    // Exponential backoff: 2s, 4s
                    Thread.sleep((2000L * attempt))
                }
            }
        }
        return false
    }

    /**
     * Download a single URL to a file, using If-Modified-Since if targetFile exists.
     * Returns the HTTP response code.
     */
    private fun downloadToFile(urlStr: String, file: File, targetFile: File, onProgress: (Float) -> Unit): Int {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "LykonShield/1.0 FilterUpdater")
            connection.setRequestProperty("Accept-Encoding", "identity") // No compression for simplicity

            if (targetFile.exists() && targetFile.length() > 0) {
                val dateFormat = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US)
                dateFormat.timeZone = java.util.TimeZone.getTimeZone("GMT")
                val lastModifiedDate = dateFormat.format(java.util.Date(targetFile.lastModified()))
                connection.setRequestProperty("If-Modified-Since", lastModifiedDate)
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                Log.d(TAG, "File not modified on server: $urlStr")
                return HttpURLConnection.HTTP_NOT_MODIFIED
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP $responseCode for $urlStr")
                return responseCode
            }
            
            val contentLength = connection.contentLength
            var totalRead = 0L

            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastUpdate = System.currentTimeMillis()
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        
                        if (contentLength > 0 && System.currentTimeMillis() - lastUpdate > 100) {
                            onProgress(totalRead.toFloat() / contentLength)
                            lastUpdate = System.currentTimeMillis()
                        }
                    }
                }
            }

            // Set last modified time of the downloaded file to match server if possible
            val lastModifiedHeader = connection.getHeaderField("Last-Modified")
            if (lastModifiedHeader != null) {
                try {
                    val dateFormat = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US)
                    dateFormat.timeZone = java.util.TimeZone.getTimeZone("GMT")
                    val parsedDate = dateFormat.parse(lastModifiedHeader)
                    if (parsedDate != null) {
                        file.setLastModified(parsedDate.time)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse Last-Modified header: $lastModifiedHeader")
                }
            }

            return HttpURLConnection.HTTP_OK

        } catch (e: Exception) {
            throw e // Let the caller handle retry
        } finally {
            connection?.disconnect()
        }
    }
}
