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

    @Volatile
    private var blockedDomainHashes = LongArray(0)

    @Volatile
    private var allowedDomainHashes = LongArray(0)

    private val loadedRuleCount = AtomicInteger(0)

    // Known DoH/DoT provider domains to block (prevents DNS bypass)
    private val dohProviderDomains = setOf(
        "dns.google", "dns.google.com", "dns64.dns.google",
        "cloudflare-dns.com", "one.one.one.one", "1dot1dot1dot1.cloudflare-dns.com", "dns.cloudflare.com", "mozilla.cloudflare-dns.com",
        "doh.opendns.com",
        "dns.quad9.net", "dns9.quad9.net", "dns10.quad9.net", "dns11.quad9.net",
        "doh.cleanbrowsing.org",
        "doh.dns.sb",
        "dns.nextdns.io",
        "dns.adguard-dns.com", "dns-unfiltered.adguard.com",
        "doh.mullvad.net",
        "freedns.controld.com",
        "private.canadianshield.cira.ca",
        "dns.switch.ch",
        "doh.xfinity.com",
        "doh.applied-privacy.net", "dns.digitale-gesellschaft.ch", "doh.li", "dns.rubyfish.cn", "odvr.nic.cz", "doh.crypto.sx", "dns.aa.net.uk", "doh.42l.fr", "doh.bortzmeyer.fr", "dns.hostux.net", "dns.containerpi.com"
    )

    // Critical system domains that must NEVER be blocked
    private val systemAllowlist = setOf(
        "connectivitycheck.gstatic.com", "connectivitycheck.android.com", "time.android.com", "time.google.com",
        "clients3.google.com", "fcm.googleapis.com", "fcm.google.com", "mtalk.google.com", "play.googleapis.com",
        "android.clients.google.com", "play-fe.googleapis.com", "playstoregatewayadapter-pa.googleapis.com"
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

    fun init(context: Context) {
        if (!initStarted.compareAndSet(false, true)) return
        state = EngineState.INITIALIZING
        observableState.value = EngineState.INITIALIZING

        Thread({
            try {
                val startTime = System.currentTimeMillis()
                val tempBlocked = HashSet<String>()
                val tempAllowed = HashSet<String>()
                
                val cacheLoaded = loadCache(context, tempBlocked, tempAllowed)
                if (cacheLoaded) {
                    blockedDomainHashes = tempBlocked.map { fnv1a64(it) }.toLongArray()
                    blockedDomainHashes.sort()
                    allowedDomainHashes = tempAllowed.map { fnv1a64(it) }.toLongArray()
                    allowedDomainHashes.sort()
                    
                    domainBlockerReady = true
                    val domainLoadTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Domain blocker ready (loaded from cache) in ${domainLoadTime}ms: ${blockedDomainHashes.size} domains, " +
                            "${allowedDomainHashes.size} allowlisted, ${dohProviderDomains.size} DoH providers blocked")
                    state = EngineState.READY
                    observableState.value = EngineState.READY
                    readyLatch.countDown()
                } else {
                    loadAllFilters(context, tempBlocked, tempAllowed)
                    domainBlockerReady = true

                    blockedDomainHashes = tempBlocked.map { fnv1a64(it) }.toLongArray()
                    blockedDomainHashes.sort()
                    allowedDomainHashes = tempAllowed.map { fnv1a64(it) }.toLongArray()
                    allowedDomainHashes.sort()

                    val domainLoadTime = System.currentTimeMillis() - startTime
                    Log.d(TAG, "Domain blocker ready (parsed from text) in ${domainLoadTime}ms: ${blockedDomainHashes.size} domains, " +
                            "${allowedDomainHashes.size} allowlisted, ${dohProviderDomains.size} DoH providers blocked")
                    state = EngineState.READY
                    observableState.value = EngineState.READY
                    readyLatch.countDown()
                    saveCache(context, tempBlocked, tempAllowed)
                }

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

    fun reloadFilters(context: Context) {
        Log.d(TAG, "Reloading filter lists...")
        val startTime = System.currentTimeMillis()

        blockedDomainHashes = LongArray(0)
        allowedDomainHashes = LongArray(0)
        loadedRuleCount.set(0)

        val tempBlocked = HashSet<String>()
        val tempAllowed = HashSet<String>()

        loadAllFilters(context, tempBlocked, tempAllowed)
        
        blockedDomainHashes = tempBlocked.map { fnv1a64(it) }.toLongArray()
        blockedDomainHashes.sort()
        allowedDomainHashes = tempAllowed.map { fnv1a64(it) }.toLongArray()
        allowedDomainHashes.sort()
        
        saveCache(context, tempBlocked, tempAllowed)

        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Filter reload complete in ${elapsed}ms: ${blockedDomainHashes.size} domains, " +
                "${allowedDomainHashes.size} allowlisted")

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

    fun isReady(): Boolean = state == EngineState.READY
    fun isNativeReady(): Boolean = nativeReady
    fun getState(): EngineState = state
    fun getLoadedDomainCount(): Int = blockedDomainHashes.size
    fun getLoadedRuleCount(): Int = loadedRuleCount.get()

    fun awaitReady(timeoutMs: Long = 5000): Boolean {
        return try {
            readyLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            false
        }
    }

    fun shouldBlock(url: String, sourceUrl: String = "", resourceType: String = "other"): Boolean {
        if (state != EngineState.READY) return false
        val domain = extractDomain(url) ?: return false

        if (isInDomainSet(domain, systemAllowlist)) return false
        if (isInDomainSet(domain, dohProviderDomains)) return true
        if (isInDomainSet(domain, allowedDomainHashes)) return false

        if (domainBlockerReady && isDomainBlocked(domain)) return true

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

    fun shouldBlockDomain(domain: String, packageName: String = ""): Boolean {
        val lower = domain.lowercase()
        val inSystem = isInDomainSet(lower, systemAllowlist)
        val inDoh = isInDomainSet(lower, dohProviderDomains)
        val inAllowed = isInDomainSet(lower, allowedDomainHashes)
        val inBlocked = isDomainBlocked(lower)
        Log.d(TAG, "Checking domain: $lower -> system=$inSystem, doh=$inDoh, allowed=$inAllowed, blocked=$inBlocked")

        if (inSystem) return false
        if (inDoh) return true
        if (inAllowed) return false
        if (domainBlockerReady && inBlocked) return true

        if (nativeReady) {
            return try {
                val urlToCheck = "http://$lower/"
                val sourceUrl = if (packageName.isNotEmpty()) "http://$packageName/" else "http://lykon-shield-context.org/"
                nativeMatches(urlToCheck, sourceUrl, "document") ||
                        nativeMatches(urlToCheck, sourceUrl, "script") ||
                        nativeMatches(urlToCheck, sourceUrl, "image") ||
                        nativeMatches(urlToCheck, sourceUrl, "xmlhttprequest") ||
                        nativeMatches(urlToCheck, sourceUrl, "subdocument") ||
                        nativeMatches(urlToCheck, sourceUrl, "other")
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    fun shouldBlockIpDirect(ip: String): Boolean = false

    fun categorize(domain: String): BlockCategory {
        val lower = domain.lowercase()
        return when {
            adKeywords.any { lower.contains(it) } -> BlockCategory.AD
            trackerKeywords.any { lower.contains(it) } -> BlockCategory.TRACKER
            analyticsKeywords.any { lower.contains(it) } -> BlockCategory.ANALYTICS
            else -> BlockCategory.OTHER
        }
    }

    fun isDoHProvider(domain: String): Boolean = isInDomainSet(domain, dohProviderDomains)

    // ==========================================================================
    // Filter Loading Implementation
    // ==========================================================================

    private fun loadAllFilters(context: Context, blocked: MutableSet<String>, allowed: MutableSet<String>) {
        val allFiles = FILTER_FILES + EXTRA_FILTER_FILES
        for (file in allFiles) {
            try {
                val storageFile = File(context.filesDir, "$FILTERS_DIR/$file")
                if (storageFile.exists() && storageFile.length() > 0) {
                    Log.d(TAG, "Loading filter from storage: $file (${storageFile.length()} bytes)")
                    storageFile.bufferedReader().use { reader -> parseFilterLines(reader, blocked, allowed) }
                } else if (file in FILTER_FILES) {
                    Log.d(TAG, "Loading filter from assets: $file")
                    context.assets.open(file).use { input -> parseFilterLines(input.bufferedReader(), blocked, allowed) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse filter list: $file", e)
            }
        }
    }

    private fun loadFilterTexts(context: Context): List<String> {
        val allFiles = FILTER_FILES + EXTRA_FILTER_FILES
        val filters = mutableListOf<String>()
        for (file in allFiles) {
            try {
                val storageFile = File(context.filesDir, "$FILTERS_DIR/$file")
                if (storageFile.exists() && storageFile.length() > 0) {
                    filters.add(storageFile.readText())
                } else if (file in FILTER_FILES) {
                    context.assets.open(file).use { input -> filters.add(input.bufferedReader().readText()) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read filter text: $file", e)
            }
        }
        return filters
    }

    private fun parseFilterLines(reader: BufferedReader, blocked: MutableSet<String>, allowed: MutableSet<String>) {
        reader.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachLine
            loadedRuleCount.incrementAndGet()
            if (line.startsWith("!") || line.startsWith("[") || line.contains("##") || line.contains("#@#") || line.contains("#?#") || line.contains("#$#")) return@forEachLine
            val isAllowlist = line.startsWith("@@")
            val ruleLine = if (isAllowlist) line.substring(2) else line
            val dollarIdx = ruleLine.lastIndexOf('$')
            val options = if (dollarIdx != -1 && !ruleLine.contains("$/")) ruleLine.substring(dollarIdx + 1) else ""
            if (options.isNotEmpty()) {
                val optList = options.lowercase().split(",")
                if (optList.any { it.contains("generichide") || it.contains("elemhide") || it.contains("document") || it.contains("genericblock") || it.contains("badfilter") || it.contains("popup") || it.contains("domain=") }) return@forEachLine
            }
            val effectiveLine = if (dollarIdx != -1 && !ruleLine.contains("$/")) ruleLine.substring(0, dollarIdx) else ruleLine
            if (effectiveLine.isEmpty()) return@forEachLine
            if (effectiveLine.startsWith("||")) {
                var domain = effectiveLine.substring(2)
                if (domain.endsWith("^")) {
                    domain = domain.substring(0, domain.length - 1)
                }
                if (domain.isNotEmpty() && !domain.contains("*") && !domain.contains("/")) {
                    val cleanDomain = domain.lowercase().trim()
                    if (cleanDomain.contains(".")) {
                        if (isAllowlist) allowed.add(cleanDomain) else blocked.add(cleanDomain)
                    }
                }
            }
            if (!isAllowlist) {
                val isHosts = line.startsWith("0.0.0.0") || line.startsWith("127.0.0.1")
                if (isHosts) {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 2) {
                        val domain = parts[1].trim()
                        if (domain.isNotEmpty() && domain.contains(".") && !domain.startsWith("#")) {
                            blocked.add(domain.lowercase())
                        }
                    }
                }
            }
        }
    }

    private fun isDomainBlocked(domain: String): Boolean {
        var current = domain.lowercase()
        while (current.isNotEmpty()) {
            val hash = fnv1a64(current)
            if (java.util.Arrays.binarySearch(blockedDomainHashes, hash) >= 0) return true
            val dotIndex = current.indexOf('.')
            if (dotIndex == -1) break
            current = current.substring(dotIndex + 1)
            if (!current.contains('.')) break
        }
        return false
    }

    private fun isInDomainSet(domain: String, hashes: LongArray): Boolean {
        var current = domain.lowercase()
        val hash = fnv1a64(current)
        if (java.util.Arrays.binarySearch(hashes, hash) >= 0) return true
        while (true) {
            val dotIndex = current.indexOf('.')
            if (dotIndex == -1) break
            current = current.substring(dotIndex + 1)
            if (!current.contains('.')) break
            val subHash = fnv1a64(current)
            if (java.util.Arrays.binarySearch(hashes, subHash) >= 0) return true
        }
        return false
    }

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

    private fun extractDomain(url: String): String? {
        return try {
            val withScheme = if (url.contains("://")) url else "http://$url"
            java.net.URI(withScheme).host?.lowercase()
        } catch (e: Exception) {
            val stripped = url.removePrefix("http://").removePrefix("https://")
            val slashIdx = stripped.indexOf('/')
            val hostPart = if (slashIdx > 0) stripped.substring(0, slashIdx) else stripped
            val colonIdx = hostPart.indexOf(':')
            val domain = if (colonIdx > 0) hostPart.substring(0, colonIdx) else hostPart
            if (domain.isNotEmpty() && domain.contains(".")) domain.lowercase() else null
        }
    }

    private fun fnv1a64(str: String): Long {
        var hash = -3750763034362895579L
        for (i in 0 until str.length) {
            hash = hash xor (str[i].code.toLong() and 0xffL)
            hash *= 1099511628211L
        }
        return hash
    }

    private fun saveCache(context: Context, blocked: Set<String>, allowed: Set<String>) {
        try {
            val cacheFile = File(context.filesDir, "filters.cache")
            cacheFile.bufferedWriter().use { writer ->
                writer.write("1\n")
                writer.write("${blocked.size}\n")
                blocked.forEach { writer.write("$it\n") }
                writer.write("${allowed.size}\n")
                allowed.forEach { writer.write("$it\n") }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save filters cache", e)
        }
    }

    private fun loadCache(context: Context, blocked: MutableSet<String>, allowed: MutableSet<String>): Boolean {
        try {
            val cacheFile = File(context.filesDir, "filters.cache")
            if (!cacheFile.exists() || cacheFile.length() == 0L) return false
            try {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                if (cacheFile.lastModified() < packageInfo.lastUpdateTime) {
                    cacheFile.delete()
                    return false
                }
            } catch (e: Exception) { Log.w(TAG, "Failed to check package update time", e) }

            cacheFile.bufferedReader().use { reader ->
                val versionLine = reader.readLine() ?: return false
                if (versionLine.trim().toIntOrNull() != 1) return false
                val blockedSize = reader.readLine()?.trim()?.toIntOrNull() ?: return false
                for (i in 0 until blockedSize) blocked.add(reader.readLine() ?: "")
                val allowedSize = reader.readLine()?.trim()?.toIntOrNull() ?: return false
                for (i in 0 until allowedSize) allowed.add(reader.readLine() ?: "")
            }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load filters cache - falling back to text lists", e)
            return false
        }
    }

    // ==========================================================================
    // Native JNI Bridge
    // ==========================================================================

    private external fun nativeInit(filters: Array<String>): Boolean
    private external fun nativeMatches(url: String, sourceUrl: String, requestType: String): Boolean
    private external fun nativeIsReady(): Boolean
}
