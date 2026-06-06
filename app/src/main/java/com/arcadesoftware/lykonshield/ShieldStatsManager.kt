package com.arcadesoftware.lykonshield

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object ShieldStatsManager {

    private const val TAG = "ShieldStatsManager"
    private const val PREFS_NAME = "lykon_shield_stats"
    private const val KEY_TOTAL_BLOCKED_TRACKERS = "total_blocked_trackers"
    private const val KEY_TOTAL_ADS_BLOCKED = "total_ads_blocked"
    private const val KEY_TOTAL_DATA_SAVED_BYTES = "total_data_saved_bytes"
    private const val KEY_APP_BLOCK_COUNTS = "app_block_counts"
    private const val KEY_DOMAIN_BLOCK_COUNTS = "domain_block_counts"
    private const val KEY_RECENT_BLOCKS = "recent_blocks"
    private const val KEY_DAILY_BLOCKS = "daily_blocks"
    private const val KEY_CATEGORY_BLOCKS = "category_blocks"

    /** Number of blocks between SharedPreferences flushes. */
    private const val PERSIST_INTERVAL = 50

    /** Average bytes saved per blocked DNS request (typical small ad/tracker resource). */
    private const val AVG_BYTES_SAVED_PER_BLOCK = 15 * 1024L

    /** Graph bucket duration in seconds. */
    private const val GRAPH_BUCKET_SECONDS = 30L

    /** Maximum number of graph history entries (30-sec buckets × 60 = 30 minutes). */
    private const val MAX_GRAPH_ENTRIES = 60

    /** Maximum number of recent block entries kept in memory. */
    private const val MAX_RECENT_BLOCKS = 100

    /** Maximum number of recent traffic entries kept in memory. */
    private const val MAX_RECENT_TRAFFIC = 150

    private val mainHandler = Handler(Looper.getMainLooper())
    private var prefs: SharedPreferences? = null
    private var unsavedBlockCount = 0

    // ── Block category classification ────────────────────────────────────────

    enum class BlockCategory {
        AD, TRACKER, ANALYTICS, MALWARE, TELEMETRY, SOCIAL, OTT, DOH, MINER, SPAM, OTHER
    }

    private val AD_KEYWORDS = listOf(
        "doubleclick", "googlesyndication", "adservice", "adnxs", "adsystem",
        "adform", "admob", "pubads", "pagead", "taboola", "outbrain",
        "popads", "adtech", "adserver", "adsrvr", "adroll", "advertising",
        "moatads", "serving-sys", "criteo", "rubiconproject", "openx",
        "bidswitch", "smartadserver", "appnexus", "mediavine",
        "inmobi", "unityads", "applovin", "ironsource", "chartboost",
        "vungle", "mopub", "startapp", "fyber", "ads.", "ad-", "-ad-"
    )

    private val TRACKER_KEYWORDS = listOf(
        "tracker", "tracking", "pixel", "beacon", "scorecardresearch",
        "hotjar", "clarity.ms", "mixpanel", "segment.io", "segment.com",
        "amplitude", "appsflyer", "adjust.com", "branch.io", "kochava",
        "singular.net", "appsflyersdk"
    )

    private val ANALYTICS_KEYWORDS = listOf(
        "google-analytics", "googletagmanager", "analytics",
        "stats.", "statcounter", "newrelic", "flurry", "omtrdc", "demdex",
        "clevertap"
    )

    private val TELEMETRY_KEYWORDS = listOf(
        "telemetry", "metrics", "log", "logs", "event", "events", "cdp",
        "conviva", "cws-", "touchstone", "bifrost", "sntx", "sigma", "crash",
        "bugsnag", "sentry", "crashlytics"
    )

    private val SOCIAL_KEYWORDS = listOf(
        "facebook", "instagram", "twitter", "tiktok", "linkedin", "snapchat",
        "fbcdn", "graph.facebook"
    )

    private val MALWARE_KEYWORDS = listOf(
        "malware", "phishing", "virus", "spyware", "trojan", "ransomware", "hack", "exploit"
    )

    private val OTT_KEYWORDS = listOf(
        "hotstar", "jiocinema", "viacom18", "mxplay", "mxplayer", "spotify", "netflix",
        "primevideo", "disneyplus", "hulu", "peacocktv", "zee5", "sonyliv", "voot",
        "bifrost", "conviva"
    )

    private val DOH_KEYWORDS = listOf(
        "dns.google", "dns.cloudflare", "cloudflare-dns", "quad9", "nextdns",
        "adguard-dns", "controld", "cleanbrowsing", "one.one.one.one"
    )

    private val MINER_KEYWORDS = listOf(
        "miner", "coinhive", "cryptonight", "crypto-loot", "webmine"
    )

    private val SPAM_KEYWORDS = listOf(
        "spam", "clickbank", "popads", "popunder", "adcash", "propellerads",
        "revenuehits", "bidvertiser"
    )

    /**
     * Classify a domain into a [BlockCategory] by checking whether it contains
     * any of the known ad / tracker / analytics keywords.
     */
    fun categorize(domain: String): BlockCategory {
        val lower = domain.lowercase()
        return when {
            DOH_KEYWORDS.any { lower.contains(it) } -> BlockCategory.DOH
            OTT_KEYWORDS.any { lower.contains(it) } -> BlockCategory.OTT
            MALWARE_KEYWORDS.any { lower.contains(it) } -> BlockCategory.MALWARE
            MINER_KEYWORDS.any { lower.contains(it) } -> BlockCategory.MINER
            SPAM_KEYWORDS.any { lower.contains(it) } -> BlockCategory.SPAM
            AD_KEYWORDS.any { lower.contains(it) } -> BlockCategory.AD
            TRACKER_KEYWORDS.any { lower.contains(it) } -> BlockCategory.TRACKER
            ANALYTICS_KEYWORDS.any { lower.contains(it) } -> BlockCategory.ANALYTICS
            TELEMETRY_KEYWORDS.any { lower.contains(it) } -> BlockCategory.TELEMETRY
            SOCIAL_KEYWORDS.any { lower.contains(it) } || lower == "t.co" || lower.endsWith(".t.co") -> BlockCategory.SOCIAL
            else -> BlockCategory.OTHER
        }
    }

    // ── Data model ───────────────────────────────────────────────────────────

    data class BlockedEntry(
        val domain: String,
        val packageName: String,
        val appName: String,
        val category: BlockCategory = BlockCategory.OTHER,
        val timestamp: Long = System.currentTimeMillis()
    )

    data class TrafficEntry(
        val domain: String,
        val packageName: String,
        val appName: String,
        val isBlocked: Boolean,
        val timestamp: Long = System.currentTimeMillis()
    )

    // ── Compose-observable state ─────────────────────────────────────────────

    /** Total number of trackers blocked (TRACKER + ANALYTICS categories, plus one for every block). */
    var totalBlockedTrackers by mutableStateOf(0)
        private set

    /** Total number of ads blocked (AD category). */
    var totalAdsBlocked by mutableStateOf(0)
        private set

    /** Estimated total bytes saved by blocking requests. */
    var totalDataSavedBytes by mutableStateOf(0L)
        private set

    /** Per-app block counts: packageName → blockCount. */
    val appBlockCounts = mutableStateMapOf<String, Int>()

    /** Per-domain block counts: domain → blockCount. */
    val domainBlockCounts = mutableStateMapOf<String, Int>()

    /** Most recent blocked entries, newest first. */
    val recentBlocks = mutableStateListOf<BlockedEntry>()

    /** Most recent network traffic entries (allowed + blocked), newest first. */
    val recentTraffic = mutableStateListOf<TrafficEntry>()

    /** Daily block history: "YYYY-MM-DD" -> blockCount (last 7 days). */
    val dailyBlockHistory = mutableStateMapOf<String, Int>()

    /** Per-category block counts: categoryName -> blockCount. */
    val categoryBlockCounts = mutableStateMapOf<String, Int>()

    /**
     * Graph data: each pair is (bucketTimestamp, count).
     * Buckets are [GRAPH_BUCKET_SECONDS]-second windows covering up to
     * [MAX_GRAPH_ENTRIES] entries (~30 minutes).
     */
    val hourlyBlockHistory = mutableStateListOf<Pair<Long, Int>>()

    // ── Initialisation & persistence ─────────────────────────────────────────

    /**
     * Restore persisted counters from SharedPreferences.
     * Call once from [Application.onCreate] or the main Activity before the
     * stats are displayed.
     */
    fun init(context: Context) {
        val appContext = context.applicationContext
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs?.let { p ->
            mainHandler.post {
                totalBlockedTrackers = p.getInt(KEY_TOTAL_BLOCKED_TRACKERS, 0)
                totalAdsBlocked = p.getInt(KEY_TOTAL_ADS_BLOCKED, 0)
                totalDataSavedBytes = p.getLong(KEY_TOTAL_DATA_SAVED_BYTES, 0L)

                // Restore app block counts
                val appBlockString = p.getString(KEY_APP_BLOCK_COUNTS, "") ?: ""
                if (appBlockString.isNotEmpty()) {
                    appBlockCounts.clear()
                    appBlockString.split(",").forEach { entry ->
                        val parts = entry.split(":")
                        if (parts.size == 2) {
                            parts[1].toIntOrNull()?.let { count ->
                                appBlockCounts[parts[0]] = count
                            }
                        }
                    }
                }

                // Restore domain block counts
                val domainBlockString = p.getString(KEY_DOMAIN_BLOCK_COUNTS, "") ?: ""
                if (domainBlockString.isNotEmpty()) {
                    domainBlockCounts.clear()
                    domainBlockString.split(",").forEach { entry ->
                        val parts = entry.split(":")
                        if (parts.size == 2) {
                            parts[1].toIntOrNull()?.let { count ->
                                domainBlockCounts[parts[0]] = count
                            }
                        }
                    }
                }

                // Restore recent blocks
                val recentString = p.getString(KEY_RECENT_BLOCKS, "") ?: ""
                if (recentString.isNotEmpty()) {
                    recentBlocks.clear()
                    recentString.split("\n").forEach { line ->
                        val parts = line.split("|")
                        if (parts.size == 5) {
                            val category = try { BlockCategory.valueOf(parts[3]) } catch (_: Exception) { BlockCategory.OTHER }
                            val timestamp = parts[4].toLongOrNull() ?: System.currentTimeMillis()
                            recentBlocks.add(BlockedEntry(parts[0], parts[1], parts[2], category, timestamp))
                        }
                    }
                }

                // Restore daily block history
                val dailyString = p.getString(KEY_DAILY_BLOCKS, "") ?: ""
                if (dailyString.isNotEmpty()) {
                    dailyBlockHistory.clear()
                    dailyString.split(",").forEach { entry ->
                        val parts = entry.split(":")
                        if (parts.size == 2) {
                            parts[1].toIntOrNull()?.let { count ->
                                dailyBlockHistory[parts[0]] = count
                            }
                        }
                    }
                }

                // Restore category block counts
                val categoryString = p.getString(KEY_CATEGORY_BLOCKS, "") ?: ""
                if (categoryString.isNotEmpty()) {
                    categoryBlockCounts.clear()
                    categoryString.split(",").forEach { entry ->
                        val parts = entry.split(":")
                        if (parts.size == 2) {
                            parts[1].toIntOrNull()?.let { count ->
                                categoryBlockCounts[parts[0]] = count
                            }
                        }
                    }
                }

            }
        }
    }

    private fun persistIfNeeded() {
        unsavedBlockCount++
        if (unsavedBlockCount >= PERSIST_INTERVAL) {
            unsavedBlockCount = 0
            prefs?.edit()?.apply {
                putInt(KEY_TOTAL_BLOCKED_TRACKERS, totalBlockedTrackers)
                putInt(KEY_TOTAL_ADS_BLOCKED, totalAdsBlocked)
                putLong(KEY_TOTAL_DATA_SAVED_BYTES, totalDataSavedBytes)

                val appBlockString = appBlockCounts.entries.joinToString(",") { "${it.key}:${it.value}" }
                putString(KEY_APP_BLOCK_COUNTS, appBlockString)

                val domainBlockString = domainBlockCounts.entries.joinToString(",") { "${it.key}:${it.value}" }
                putString(KEY_DOMAIN_BLOCK_COUNTS, domainBlockString)

                val recentString = recentBlocks.take(30).joinToString("\n") { 
                    "${it.domain}|${it.packageName}|${it.appName}|${it.category.name}|${it.timestamp}" 
                }
                putString(KEY_RECENT_BLOCKS, recentString)

                val dailyString = dailyBlockHistory.entries.joinToString(",") { "${it.key}:${it.value}" }
                putString(KEY_DAILY_BLOCKS, dailyString)

                val categoryString = categoryBlockCounts.entries.joinToString(",") { "${it.key}:${it.value}" }
                putString(KEY_CATEGORY_BLOCKS, categoryString)

                apply()
            }
        }
    }

    // ── Recording blocks ─────────────────────────────────────────────────────

    fun recordBlock(context: Context, domain: String) {
        recordBlock(context, domain, categorize(domain))
    }

    fun recordBlockWithApp(context: Context, domain: String, packageName: String) {
        recordBlock(context, domain, categorize(domain), packageName)
    }

    fun recordBlock(context: Context, domain: String, category: String) {
        val parsed = try {
            BlockCategory.valueOf(category.uppercase())
        } catch (_: IllegalArgumentException) {
            BlockCategory.OTHER
        }
        recordBlock(context, domain, parsed)
    }

    fun recordBlock(context: Context, domain: String, category: String, packageName: String) {
        val parsed = try {
            BlockCategory.valueOf(category.uppercase())
        } catch (_: IllegalArgumentException) {
            BlockCategory.OTHER
        }
        recordBlock(context, domain, parsed, packageName)
    }

    fun recordBlock(context: Context, domain: String, category: BlockCategory) {
        val packageName = getActiveAppPackage(context) ?: "system"
        recordBlock(context, domain, category, packageName)
    }

    fun recordBlock(context: Context, domain: String, category: BlockCategory, packageName: String) {
        val appName = getAppName(context, packageName)

        mainHandler.post {
            // ── Counters ─────────────────────────────────────────────────
            totalBlockedTrackers++
            if (category == BlockCategory.AD) {
                totalAdsBlocked++
            }

            totalDataSavedBytes += AVG_BYTES_SAVED_PER_BLOCK

            // ── Per-category counts ──────────────────────────────────────
            val catName = category.name
            categoryBlockCounts[catName] = (categoryBlockCounts[catName] ?: 0) + 1

            // ── Daily history counts ──────────────────────────────────────
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val dayKey = sdf.format(java.util.Date())
            dailyBlockHistory[dayKey] = (dailyBlockHistory[dayKey] ?: 0) + 1

            // Keep only the last 7 days in daily history map to avoid infinite growth
            if (dailyBlockHistory.size > 7) {
                val sortedKeys = dailyBlockHistory.keys.sorted()
                val keysToRemove = sortedKeys.take(dailyBlockHistory.size - 7)
                keysToRemove.forEach { dailyBlockHistory.remove(it) }
            }

            // ── Per-app & per-domain maps ────────────────────────────────
            appBlockCounts[packageName] = (appBlockCounts[packageName] ?: 0) + 1
            domainBlockCounts[domain] = (domainBlockCounts[domain] ?: 0) + 1

            // ── Recent blocks (newest first, capped) ─────────────────────
            recentBlocks.add(0, BlockedEntry(domain, packageName, appName, category))
            if (recentBlocks.size > MAX_RECENT_BLOCKS) {
                recentBlocks.removeAt(recentBlocks.lastIndex)
            }

            // Also record to general traffic list
            recentTraffic.add(0, TrafficEntry(domain, packageName, appName, isBlocked = true))
            if (recentTraffic.size > MAX_RECENT_TRAFFIC) {
                recentTraffic.removeAt(recentTraffic.lastIndex)
            }

            // ── Graph history (30-second buckets) ────────────────────────
            val bucketTimestamp =
                (System.currentTimeMillis() / 1000 / GRAPH_BUCKET_SECONDS) * GRAPH_BUCKET_SECONDS
            val bucketIdx = hourlyBlockHistory.indexOfFirst { it.first == bucketTimestamp }
            if (bucketIdx != -1) {
                val existing = hourlyBlockHistory[bucketIdx]
                hourlyBlockHistory[bucketIdx] = Pair(existing.first, existing.second + 1)
            } else {
                hourlyBlockHistory.add(Pair(bucketTimestamp, 1))
                if (hourlyBlockHistory.size > MAX_GRAPH_ENTRIES) {
                    hourlyBlockHistory.removeAt(0)
                }
            }

            // ── Persistence ──────────────────────────────────────────────
            persistIfNeeded()
        }

        Log.d(TAG, "Recorded block [$category]: $domain from $appName ($packageName)")
    }

    fun recordTraffic(context: Context, domain: String, packageName: String, isBlocked: Boolean) {
        val appName = getAppName(context, packageName)
        mainHandler.post {
            recentTraffic.add(0, TrafficEntry(domain, packageName, appName, isBlocked))
            if (recentTraffic.size > MAX_RECENT_TRAFFIC) {
                recentTraffic.removeAt(recentTraffic.lastIndex)
            }
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun getActiveAppPackage(context: Context): String? {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            val runningTasks = am.getRunningTasks(3)
            runningTasks.firstOrNull { task ->
                val pkg = task.topActivity?.packageName
                pkg != null &&
                    pkg != context.packageName &&
                    pkg != "com.google.android.inputmethod.latin"
            }?.topActivity?.packageName
        } catch (_: Exception) {
            // SecurityException or other permission restrictions
            null
        }
    }

    private val appNameCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun getAppName(context: Context, packageName: String): String {
        if (packageName == "system") return "System"
        
        return appNameCache.getOrPut(packageName) {
            try {
                val pm = context.packageManager
                @Suppress("DEPRECATION")
                val info = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                packageName.substringAfterLast('.')
            }
        }
    }
}
