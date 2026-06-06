package com.arcadesoftware.lykon

import android.content.Context
import com.arcadesoftware.lykonshield.Logger as Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.BitSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Production-grade adblock engine wrapping the Brave adblock-rust native library.
 *
 * Architecture:
 * ┌──────────────────────────────────────────────┐
 * │              AdblockEngine                    │
 * │  ┌────────────┐   ┌───────────────────────┐  │
 * │  │ Native Rust │   │  Kotlin DomainBlocker │  │
 * │  │ (full URL)  │   │  (HashSet + subdomain)│  │
 * │  └────────────┘   └───────────────────────┘  │
 * │       ↑ secondary        ↑ primary (fast)    │
 * └──────────────────────────────────────────────┘
 *
 * The Kotlin DomainBlocker extracts domain-only rules from EasyList/EasyPrivacy
 * and provides O(k) lookups (k = domain label count). This is always ready first.
 * The native Rust engine provides full URL-level matching as a secondary check.
 *
 * Enhanced features:
 * - Loads from internal storage (updated lists) before falling back to assets
 * - Supports hot-reload of filter lists without app restart
 * - Expanded DoH provider domain blocking
 */
object AdblockEngine {

    private const val TAG = "AdblockEngine"

    // ── Filter list download sources ─────────────────────────────────────

    object FilterSources {
        const val EASYLIST_URL = "https://easylist.to/easylist/easylist.txt"
        const val EASYPRIVACY_URL = "https://easylist.to/easylist/easyprivacy.txt"
        const val UBLOCK_FILTERS_URL = "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt"
        const val PETER_LOWE_URL = "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=adblockplus&showintro=0"
        const val OISD_URL = "https://abp.oisd.nl/basic/"
    }

    // ── Filter file names (used in both assets and internal storage) ─────

    private val FILTER_FILES = listOf("easylist.txt", "easyprivacy.txt", "ublock-filters.txt", "ott-filters.txt")
    private val EXTRA_FILTER_FILES = listOf("peter-lowe.txt", "oisd-basic.txt")
    const val FILTERS_DIR = "filters"

    // --- Engine State ---

    enum class EngineState {
        NOT_STARTED,
        INITIALIZING,
        READY,
        FAILED
    }

    @Volatile
    private var state = EngineState.NOT_STARTED

    val observableState = androidx.compose.runtime.mutableStateOf(EngineState.NOT_STARTED)

    @Volatile
    private var nativeReady = false

    @Volatile
    private var domainBlockerReady = false

    private val initStarted = AtomicBoolean(false)
    private val readyLatch = CountDownLatch(1)

    // --- Domain Blocker (Primary - Fast) ---

    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val allowedDomains = ConcurrentHashMap.newKeySet<String>()
    private val loadedRuleCount = AtomicInteger(0)

    // --- Bloom Filter (Verification and Optimization layer) ---
    private val bloomFilter = BloomFilter(1000000, 5) // 1,000,000 bits (~125KB), 5 hash functions

    // Known DoH/DoT provider domains to block (prevents DNS bypass)
    private val dohProviderDomains = setOf(
        // Google
        "dns.google",
        "dns.google.com",
        "dns64.dns.google",
        // Cloudflare
        "cloudflare-dns.com",
        "one.one.one.one",
        "1dot1dot1dot1.cloudflare-dns.com",
        "dns.cloudflare.com",
        "mozilla.cloudflare-dns.com",
        // OpenDNS
        "doh.opendns.com",
        // Quad9
        "dns.quad9.net",
        "dns9.quad9.net",
        "dns10.quad9.net",
        "dns11.quad9.net",
        // CleanBrowsing
        "doh.cleanbrowsing.org",
        // DNS.SB
        "doh.dns.sb",
        // NextDNS
        "dns.nextdns.io",
        // AdGuard
        "dns.adguard-dns.com",
        "dns-unfiltered.adguard.com",
        // Mullvad
        "doh.mullvad.net",
        // ControlD
        "freedns.controld.com",
        // CIRA
        "private.canadianshield.cira.ca",
        // Switch
        "dns.switch.ch",
        // Xfinity
        "doh.xfinity.com",
        // Additional providers
        "doh.applied-privacy.net",
        "dns.digitale-gesellschaft.ch",
        "doh.li",
        "dns.rubyfish.cn",
        "odvr.nic.cz",
        "doh.crypto.sx",
        "dns.aa.net.uk",
        "doh.42l.fr",
        "doh.bortzmeyer.fr",
        "dns.hostux.net",
        "dns.containerpi.com"
    )

    // Critical system domains that must NEVER be blocked
    private val systemAllowlist = setOf(
        "connectivitycheck.gstatic.com",
        "connectivitycheck.android.com",
        "time.android.com",
        "time.google.com",
        "clients3.google.com",
        "fcm.googleapis.com",
        "fcm.google.com",
        "mtalk.google.com",
        "play.googleapis.com",
        "android.clients.google.com",
        "play-fe.googleapis.com",
        "playstoregatewayadapter-pa.googleapis.com"
    )

    // --- Block Categories ---

    enum class BlockCategory {
        AD, TRACKER, ANALYTICS, PRIVACY, OTHER
    }

    private val adKeywords = listOf(
        "doubleclick", "googlesyndication", "adservice", "adnxs", "adsystem",
        "adform", "admob", "pubads", "pagead", "taboola", "outbrain",
        "popads", "adtech", "adserver", "adsrvr", "adroll", "advertising",
        "moatads", "serving-sys", "criteo", "rubiconproject", "openx",
        "bidswitch", "smartadserver", "appnexus", "mediavine",
        "inmobi", "unityads", "applovin", "ironsource", "chartboost",
        "vungle", "mopub", "startapp", "fyber"
    )

    private val trackerKeywords = listOf(
        "tracker", "tracking", "telemetry", "pixel", "beacon",
        "scorecardresearch", "hotjar", "clarity.ms", "mixpanel",
        "segment.io", "segment.com", "amplitude", "appsflyer",
        "adjust.com", "branch.io", "kochava", "singular.net",
        "facebook.com/tr", "bat.bing.com", "t.co",
        "app-measurement", "firebaselogging", "app-analytics"
    )

    private val analyticsKeywords = listOf(
        "google-analytics", "googletagmanager", "analytics",
        "statcounter", "newrelic", "sentry.io", "bugsnag",
        "crashlytics", "flurry", "omtrdc", "demdex"
    )

    // ==========================================================================
    // Public API
    // ==========================================================================

    /**
     * Initialize the engine. Safe to call multiple times — only the first call
     * triggers initialization. The Kotlin domain blocker initializes synchronously
     * and quickly (~200ms); the native Rust engine loads asynchronously.
     */
    fun init(context: Context) {
        if (!initStarted.compareAndSet(false, true)) return
        state = EngineState.INITIALIZING
        observableState.value = EngineState.INITIALIZING

        Thread({
            try {
                // Step 1: Build/Load fast domain blocker (primary)
                val startTime = System.currentTimeMillis()
                val cacheLoaded = loadCache(context)
                if (cacheLoaded) {
                    domainBlockerReady = true
                    val domainLoadTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Domain blocker ready (loaded from cache) in ${domainLoadTime}ms: ${blockedDomains.size} domains, " +
                            "${allowedDomains.size} allowlisted, ${dohProviderDomains.size} DoH providers blocked")
                    state = EngineState.READY
                    observableState.value = EngineState.READY
                    readyLatch.countDown()
                } else {
                    // Fallback to parsing raw text lists
                    loadAllFilters(context)
                    domainBlockerReady = true

                    // Populate Bloom Filter
                    bloomFilter.clear()
                    for (domain in blockedDomains) {
                        bloomFilter.add(domain)
                    }

                    val domainLoadTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Domain blocker ready (parsed from text) in ${domainLoadTime}ms: ${blockedDomains.size} domains, " +
                            "${allowedDomains.size} allowlisted, ${dohProviderDomains.size} DoH providers blocked")
                    state = EngineState.READY
                    observableState.value = EngineState.READY
                    readyLatch.countDown()
                    // Save cache for next run
                    saveCache(context)
                }

                // Step 2: Load native Rust engine asynchronously (secondary, enhanced matching)
                try {
                    System.loadLibrary("adblock")
                    val filters = loadFilterTexts(context)
                    nativeReady = nativeInit(filters.toTypedArray())
                    Log.d(TAG, "Native Rust engine initialized: $nativeReady")
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "libadblock.so not available — using domain blocker only", e)
                } catch (e: Exception) {
                    Log.w(TAG, "Native engine init failed — using domain blocker only", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Engine init failed completely", e)
                state = EngineState.FAILED
                observableState.value = EngineState.FAILED
                readyLatch.countDown()
            }
        }, "adblock-init").start()
    }

    /**
     * Hot-reload filter lists from storage (called after FilterListUpdater
     * downloads fresh lists). This replaces the domain blocker contents
     * without requiring an app restart.
     */
    fun reloadFilters(context: Context) {
        Log.d(TAG, "Reloading filter lists...")
        val startTime = System.currentTimeMillis()

        // Clear existing data
        blockedDomains.clear()
        allowedDomains.clear()
        loadedRuleCount.set(0)
        bloomFilter.clear()

        // Reload from storage (or assets as fallback)
        loadAllFilters(context)
        
        // Populate Bloom Filter
        for (domain in blockedDomains) {
            bloomFilter.add(domain)
        }
        
        saveCache(context)

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Filter reload complete in ${elapsed}ms: ${blockedDomains.size} domains, " +
                "${allowedDomains.size} allowlisted")

        // Re-init native engine with new lists
        if (nativeReady) {
            try {
                nativeReady = false
                val filters = loadFilterTexts(context)
                nativeReady = nativeInit(filters.toTypedArray())
                Log.d(TAG, "Native Rust engine reloaded: $nativeReady")
            } catch (e: Exception) {
                Log.w(TAG, "Native engine reload failed", e)
            }
        }
    }

    /**
     * Returns true once at least the domain blocker is ready.
     */
    fun isReady(): Boolean = state == EngineState.READY

    /**
     * Returns true if the native Rust engine is operational (secondary layer).
     */
    fun isNativeReady(): Boolean = nativeReady

    /**
     * Returns the current engine state.
     */
    fun getState(): EngineState = state

    /**
     * Returns number of blocked domains loaded.
     */
    fun getLoadedDomainCount(): Int = blockedDomains.size

    /**
     * Returns total parsed rules count.
     */
    fun getLoadedRuleCount(): Int = loadedRuleCount.get()

    /**
     * Block until the engine is ready, with timeout.
     * Returns true if engine became ready within the timeout.
     */
    fun awaitReady(timeoutMs: Long = 5000): Boolean {
        return try {
            readyLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            false
        }
    }

    /**
     * Check if a domain should be blocked. This is the primary API for DNS-level blocking.
     *
     * Checks in order:
     * 1. System allowlist (never block)
     * 2. DoH provider domains (always block to prevent DNS bypass)
     * 3. Filter allowlist (parsed from @@|| rules)
     * 4. Domain blocker HashSet with subdomain matching
     * 5. Native Rust engine URL-level check (if available)
     */
    fun shouldBlock(url: String, sourceUrl: String = "", resourceType: String = "other"): Boolean {
        if (state != EngineState.READY) return false

        // Extract domain from URL
        val domain = extractDomain(url) ?: return false

        // 1. Never block critical system domains
        if (isInDomainSet(domain, systemAllowlist)) return false

        // 2. Always block DoH providers to prevent DNS bypass
        if (isInDomainSet(domain, dohProviderDomains)) return true

        // 3. Check allowlist
        if (isInDomainSet(domain, allowedDomains)) return false

        // 4. Primary: Fast domain blocker
        if (domainBlockerReady && isDomainBlocked(domain)) return true

        // 5. Secondary: Native Rust engine (full URL matching)
        if (nativeReady) {
            return try {
                nativeMatches(url, sourceUrl, resourceType)
            } catch (e: Exception) {
                Log.w(TAG, "nativeMatches failed for $url", e)
                false
            }
        }

        return false
    }

    /**
     * Check if a bare domain should be blocked (for DNS queries).
     * Constructs a URL from the domain and delegates to shouldBlock().
     */
    fun shouldBlockDomain(domain: String, packageName: String = ""): Boolean {
        val lower = domain.lowercase()
        val inSystem = isInDomainSet(lower, systemAllowlist)
        val inDoh = isInDomainSet(lower, dohProviderDomains)
        val inAllowed = isInDomainSet(lower, allowedDomains)
        val inBlocked = isDomainBlocked(lower)
        Log.d(TAG, "Checking domain: $lower -> system=$inSystem, doh=$inDoh, allowed=$inAllowed, blocked=$inBlocked")

        // 1. Never block critical system domains
        if (inSystem) return false

        // 2. Always block DoH providers
        if (inDoh) return true

        // 3. Check allowlist
        if (inAllowed) return false

        // 4. Primary: Fast domain blocker
        if (domainBlockerReady && inBlocked) return true

        // 5. Secondary: Native Rust engine
        if (nativeReady) {
            return try {
                val urlToCheck = "http://$lower/"
                // Pass a dummy source URL with the requesting package name as domain
                val sourceUrl = if (packageName.isNotEmpty()) "http://$packageName/" else "http://lykon-shield-context.org/"
                nativeMatches(urlToCheck, sourceUrl, "document") ||
                        nativeMatches(urlToCheck, sourceUrl, "script") ||
                        nativeMatches(urlToCheck, sourceUrl, "image") ||
                        nativeMatches(urlToCheck, sourceUrl, "xmlhttprequest")
            } catch (e: Exception) {
                false
            }
        }

        return false
    }

    /**
     * Hook for future IP-based ad blocking. Currently returns false as
     * IP-level blocking risks false positives on shared CDN infrastructure.
     */
    fun shouldBlockIpDirect(ip: String): Boolean {
        // Reserved for future use — when we have a curated list of
        // dedicated ad-serving IPs that are NOT shared CDNs.
        return false
    }

    /**
     * Classify a blocked domain into a category.
     */
    fun categorize(domain: String): BlockCategory {
        val lower = domain.lowercase()
        return when {
            adKeywords.any { lower.contains(it) } -> BlockCategory.AD
            trackerKeywords.any { lower.contains(it) } -> BlockCategory.TRACKER
            analyticsKeywords.any { lower.contains(it) } -> BlockCategory.ANALYTICS
            else -> BlockCategory.OTHER
        }
    }

    /**
     * Returns true if the given domain is a known DoH/DoT provider.
     */
    fun isDoHProvider(domain: String): Boolean {
        return isInDomainSet(domain, dohProviderDomains)
    }

    // ==========================================================================
    // Filter Loading Implementation
    // ==========================================================================

    /**
     * Load all filter lists — checks internal storage first (for updated lists
     * downloaded by FilterListUpdater), then falls back to bundled assets.
     */
    private fun loadAllFilters(context: Context) {
        val allFiles = FILTER_FILES + EXTRA_FILTER_FILES

        for (file in allFiles) {
            try {
                val storageFile = File(context.filesDir, "$FILTERS_DIR/$file")
                if (storageFile.exists() && storageFile.length() > 0) {
                    // Load from internal storage (updated list)
                    Log.d(TAG, "Loading filter from storage: $file (${storageFile.length()} bytes)")
                    storageFile.bufferedReader().use { reader ->
                        parseFilterLines(reader)
                    }
                } else if (file in FILTER_FILES) {
                    // Fall back to bundled assets (only for the 3 core lists)
                    Log.d(TAG, "Loading filter from assets: $file")
                    context.assets.open(file).use { input ->
                        parseFilterLines(input.bufferedReader())
                    }
                }
                // Extra files (peter-lowe, oisd) are only loaded if downloaded
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse filter list: $file", e)
            }
        }
    }

    /**
     * Load raw filter text for the native Rust engine. Returns a list of
     * complete filter list contents as strings.
     */
    private fun loadFilterTexts(context: Context): List<String> {
        val allFiles = FILTER_FILES + EXTRA_FILTER_FILES
        val filters = mutableListOf<String>()

        for (file in allFiles) {
            try {
                val storageFile = File(context.filesDir, "$FILTERS_DIR/$file")
                if (storageFile.exists() && storageFile.length() > 0) {
                    filters.add(storageFile.readText())
                } else if (file in FILTER_FILES) {
                    context.assets.open(file).use { input ->
                        filters.add(input.bufferedReader().readText())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read filter text: $file", e)
            }
        }

        return filters
    }

    /**
     * Parse filter lines from a reader, extracting domain-only rules
     * into the fast HashSet. Mirrors the parsing logic from Arcade-Lykon
     * browser FilterManager._parseFilterList().
     */
    private fun parseFilterLines(reader: BufferedReader) {
        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachLine

            loadedRuleCount.incrementAndGet()

            // Skip comments, metadata, cosmetic rules
            if (line.startsWith("!") ||
                line.startsWith("[") ||
                line.contains("##") ||
                line.contains("#@#") ||
                line.contains("#?#") ||
                line.contains("#\$#")) {
                return@forEachLine
            }

            // Check for allowlist rules (@@||domain^)
            val isAllowlist = line.startsWith("@@")
            val ruleLine = if (isAllowlist) line.substring(2) else line

            // Check if there are options after $
            val dollarIdx = ruleLine.lastIndexOf('$')
            val options = if (dollarIdx != -1 && !ruleLine.contains("\$/")) {
                ruleLine.substring(dollarIdx + 1)
            } else {
                ""
            }

            // Filter out rules with options we cannot evaluate at the DNS level.
            // Complex rules are skipped here and evaluated with full context in the native Rust engine.
            if (options.isNotEmpty()) {
                val optList = options.lowercase().split(",")
                if (optList.any { 
                        it.contains("generichide") || it.contains("elemhide") || 
                        it.contains("document") || it.contains("genericblock") || 
                        it.contains("badfilter") || it.contains("popup") ||
                        it.contains("domain=")
                    }) {
                    return@forEachLine
                }
            }

            val effectiveLine = if (dollarIdx != -1 && !ruleLine.contains("\$/")) {
                ruleLine.substring(0, dollarIdx)
            } else {
                ruleLine
            }

            if (effectiveLine.isEmpty()) return@forEachLine

            // Extract domain from ||domain^ pattern
            if (effectiveLine.startsWith("||") &&
                effectiveLine.endsWith("^") &&
                !effectiveLine.contains("*") &&
                !effectiveLine.contains("/")) {

                val domain = effectiveLine.substring(2, effectiveLine.length - 1)
                if (domain.isNotEmpty() && domain.contains(".")) {
                    if (isAllowlist) {
                        allowedDomains.add(domain.lowercase())
                    } else {
                        blockedDomains.add(domain.lowercase())
                    }
                }
            }

            // Also handle hosts-file format: "0.0.0.0 domain" or "127.0.0.1 domain"
            if (!isAllowlist && (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 "))) {
                val domain = line.substringAfter(" ").trim()
                if (domain.isNotEmpty() && domain.contains(".") && !domain.startsWith("#")) {
                    blockedDomains.add(domain.lowercase())
                }
            }
        }
    }

    /**
     * Check if a domain matches any entry in the blocked domains set,
     * including subdomain matching. Mirrors FilterManager.matches():
     *
     * For domain "sub.ads.example.com", checks:
     *   sub.ads.example.com → ads.example.com → example.com
     */
    private fun isDomainBlocked(domain: String): Boolean {
        var current = domain.lowercase()
        while (current.isNotEmpty()) {
            // Bloom filter pre-check to speed up and verify
            if (bloomFilter.contains(current)) {
                // Secondary check: confirm in HashSet to avoid false positives
                if (blockedDomains.contains(current)) return true
            }
            val dotIndex = current.indexOf('.')
            if (dotIndex == -1) break
            current = current.substring(dotIndex + 1)
            if (!current.contains('.')) break // Don't check TLD alone
        }
        return false
    }

    /**
     * Check if a domain matches any entry in a domain set (with subdomain matching).
     */
    private fun isInDomainSet(domain: String, domainSet: Set<String>): Boolean {
        var current = domain.lowercase()
        if (domainSet.contains(current)) return true
        while (true) {
            val dotIndex = current.indexOf('.')
            if (dotIndex == -1) break
            current = current.substring(dotIndex + 1)
            if (!current.contains('.')) break
            if (domainSet.contains(current)) return true
        }
        return false
    }

    /**
     * Extract domain from a URL string.
     */
    private fun extractDomain(url: String): String? {
        return try {
            val withScheme = if (url.contains("://")) url else "http://$url"
            java.net.URI(withScheme).host?.lowercase()
        } catch (e: Exception) {
            // Fallback: try simple extraction
            val stripped = url
                .removePrefix("http://")
                .removePrefix("https://")
            val slashIdx = stripped.indexOf('/')
            val hostPart = if (slashIdx > 0) stripped.substring(0, slashIdx) else stripped
            val colonIdx = hostPart.indexOf(':')
            val domain = if (colonIdx > 0) hostPart.substring(0, colonIdx) else hostPart
            if (domain.isNotEmpty() && domain.contains(".")) domain.lowercase() else null
        }
    }

    private fun saveCache(context: Context) {
        try {
            val cacheFile = java.io.File(context.filesDir, "filters.cache")
            cacheFile.bufferedWriter().use { writer ->
                writer.write("1\n") // Cache version code
                writer.write("${blockedDomains.size}\n")
                blockedDomains.forEach { writer.write("$it\n") }
                writer.write("${allowedDomains.size}\n")
                allowedDomains.forEach { writer.write("$it\n") }
            }
            Log.d(TAG, "Saved filters cache: ${cacheFile.length()} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save filters cache", e)
        }
    }

    private fun loadCache(context: Context): Boolean {
        try {
            val cacheFile = java.io.File(context.filesDir, "filters.cache")
            if (!cacheFile.exists() || cacheFile.length() == 0L) return false

            // Invalidate cache if older than last app update (e.g. assets updated)
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                if (cacheFile.lastModified() < packageInfo.lastUpdateTime) {
                    Log.d(TAG, "Cache file is older than last app update, invalidating.")
                    cacheFile.delete()
                    return false
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check package update time", e)
            }

            val lines = cacheFile.readLines()
            if (lines.size < 3) return false

            val version = lines[0].trim().toIntOrNull() ?: return false
            if (version != 1) return false

            val blockedSize = lines[1].trim().toIntOrNull() ?: return false
            if (lines.size < 2 + blockedSize + 1) return false
            val blocked = lines.subList(2, 2 + blockedSize)

            val allowedSize = lines[2 + blockedSize].trim().toIntOrNull() ?: return false
            if (lines.size < 3 + blockedSize + allowedSize) return false
            val allowed = lines.subList(3 + blockedSize, 3 + blockedSize + allowedSize)

            blockedDomains.clear()
            blockedDomains.addAll(blocked)

            allowedDomains.clear()
            allowedDomains.addAll(allowed)

            // Populate Bloom Filter
            bloomFilter.clear()
            for (domain in blocked) {
                bloomFilter.add(domain)
            }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load filters cache - falling back to text lists", e)
            return false
        }
    }

    class BloomFilter(private val numBits: Int, private val numHashFunctions: Int) {
        private val bitSet = BitSet(numBits)

        fun add(element: String) {
            val h1 = element.hashCode()
            val h2 = h1 xor (h1 ushr 16)
            for (i in 0 until numHashFunctions) {
                val hash = h1 + i * h2
                bitSet.set(Math.abs(hash % numBits))
            }
        }

        fun contains(element: String): Boolean {
            val h1 = element.hashCode()
            val h2 = h1 xor (h1 ushr 16)
            for (i in 0 until numHashFunctions) {
                val hash = h1 + i * h2
                val index = Math.abs(hash % numBits)
                if (!bitSet.get(index)) {
                    return false
                }
            }
            return true
        }

        fun clear() {
            bitSet.clear()
        }
    }

    // ==========================================================================
    // Native JNI Bridge
    // ==========================================================================

    private external fun nativeInit(filters: Array<String>): Boolean
    private external fun nativeMatches(url: String, sourceUrl: String, requestType: String): Boolean
    private external fun nativeIsReady(): Boolean
}
