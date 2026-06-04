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

    /**
     * Download sources mapped to their local file names.
     */
    private val DOWNLOAD_MAP = mapOf(
        AdblockEngine.FilterSources.EASYLIST_URL to "easylist.txt",
        AdblockEngine.FilterSources.EASYPRIVACY_URL to "easyprivacy.txt",
        AdblockEngine.FilterSources.UBLOCK_FILTERS_URL to "ublock-filters.txt",
        AdblockEngine.FilterSources.PETER_LOWE_URL to "peter-lowe.txt",
        AdblockEngine.FilterSources.OISD_URL to "oisd-basic.txt"
    )

    /**
     * Check if filter lists need updating and download them if necessary.
     * Runs on a background thread. Safe to call from any thread.
     *
     * @param context Application context
     * @param force If true, ignore the 24-hour cooldown and update immediately
     */
    fun checkAndUpdate(context: Context, force: Boolean = false) {
        val appContext = context.applicationContext

        if (!force) {
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
                performUpdate(appContext)
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

    private fun performUpdate(context: Context) {
        Log.d(TAG, "Starting filter list update...")
        val startTime = System.currentTimeMillis()

        // Ensure the filters directory exists
        val filtersDir = File(context.filesDir, AdblockEngine.FILTERS_DIR)
        if (!filtersDir.exists()) {
            filtersDir.mkdirs()
        }

        var successCount = 0
        var failCount = 0

        for ((url, fileName) in DOWNLOAD_MAP) {
            val targetFile = File(filtersDir, fileName)
            val success = downloadWithRetry(url, targetFile)
            if (success) {
                successCount++
                Log.d(TAG, "✓ Downloaded: $fileName (${targetFile.length() / 1024} KB)")
            } else {
                failCount++
                Log.w(TAG, "✗ Failed to download: $fileName from $url")
            }
        }

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Filter update complete in ${elapsed}ms: $successCount succeeded, $failCount failed")

        if (successCount > 0) {
            // Record update time
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_LAST_UPDATE, System.currentTimeMillis())
                .apply()

            // Hot-reload the engine with new lists
            try {
                AdblockEngine.reloadFilters(context)
                Log.d(TAG, "AdblockEngine reloaded with fresh filter lists")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to reload AdblockEngine after update", e)
            }
        }
    }

    /**
     * Download a URL to a local file with retry logic.
     * Writes to a temporary file first, then atomically renames to prevent
     * corruption if the download is interrupted.
     */
    private fun downloadWithRetry(urlStr: String, targetFile: File): Boolean {
        for (attempt in 1..MAX_RETRIES + 1) {
            try {
                val tempFile = File(targetFile.parent, "${targetFile.name}.tmp")
                val success = downloadToFile(urlStr, tempFile)
                if (success && tempFile.exists() && tempFile.length() > 100) {
                    // Atomic rename
                    if (targetFile.exists()) targetFile.delete()
                    tempFile.renameTo(targetFile)
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
     * Download a single URL to a file.
     */
    private fun downloadToFile(urlStr: String, file: File): Boolean {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "LykonShield/1.0 FilterUpdater")
            connection.setRequestProperty("Accept-Encoding", "identity") // No compression for simplicity

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP $responseCode for $urlStr")
                return false
            }

            connection.inputStream.use { input ->
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            return true
        } catch (e: Exception) {
            throw e // Let the caller handle retry
        } finally {
            connection?.disconnect()
        }
    }
}
