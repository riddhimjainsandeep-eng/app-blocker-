package com.example.appblocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BlockerService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val PREFS_NAME = "BlockerPrefs"
    private val KEY_APPS = "blocked_apps"
    private val KEY_WEBSITES = "blocked_websites"
    private val KEY_KEYWORDS = "blocked_keywords"

    private val STATS_PREFS = "BlockerStats"
    private val KEY_TOTAL_BLOCKS = "total_blocks"
    private val KEY_WEEKLY_BLOCKS = "weekly_blocks"
    private val KEY_BLOCK_TIMESTAMPS = "block_timestamps"
    private val KEY_LAST_WEEK_TOTAL = "last_week_total"

    // Productivity categories for Feature 4
    private val PRODUCTIVE_APPS = setOf(
        "com.google.android.calculator", "com.android.calculator2",
        "com.google.android.calendar", "com.android.calendar",
        "com.google.android.apps.docs", "com.notion.id",
        "com.microsoft.office.word", "com.microsoft.office.excel",
        "com.android.documentsui", "com.google.android.keep",
        "app.todoist", "com.ticktick.task"
    )
    private val DISTRACTION_APPS = setOf(
        "com.instagram.android", "com.zhiliaoapp.musically", "com.twitter.android",
        "com.snapchat.android", "com.facebook.katana", "com.reddit.frontpage",
        "com.google.android.youtube", "com.netflix.mediaclient",
        "com.gameloft.android", "com.king.candycrush"
    )

    // Default distraction URL patterns (browser deep-filtering)
    private val DEFAULT_BLOCKED_PATTERNS = setOf(
        "shorts", "reels", "tiktok", "trending", "explore",
        "youtube.com/shorts", "instagram.com/reels", "feed"
    )

    private val targetBrowsers = setOf(
        "com.android.chrome", "com.brave.browser",
        "org.mozilla.firefox", "com.sec.android.app.sbrowser"
    )

    // Base system exemptions — Settings is NOT exempted here anymore,
    // Strict Mode controls whether it gets blocked
    private val hardExemptions = setOf(
        "com.example.appblocker",
        "com.android.systemui",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var isCooldownActive = false

    // Foreground time tracking for productivity ratio (Feature 4)
    private var lastFgPackage = ""
    private var lastFgStartMs = 0L

    // In-memory cache to prevent disk reads on every AccessibilityEvent (Performance fix)
    // In-memory cache to prevent disk reads on every AccessibilityEvent (Performance fix)
    private var isStrictMode = false
    private var isMentalFriction = true
    private var cachedBlockedApps: HashSet<String> = hashSetOf()
    private var cachedBlockedWebsites: Set<String> = emptySet()
    private var cachedBlockedKeywords: Set<String> = emptySet()
    private lateinit var prefs: SharedPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.registerOnSharedPreferenceChangeListener(this)
        loadCachedPrefs()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(this)
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        loadCachedPrefs()
    }

    private fun loadCachedPrefs() {
        isStrictMode          = prefs.getBoolean("is_strict_mode", false)
        isMentalFriction      = prefs.getBoolean("is_mental_friction", true)
        cachedBlockedApps     = prefs.getStringSet(KEY_APPS, emptySet())?.toHashSet() ?: hashSetOf()
        cachedBlockedWebsites = prefs.getStringSet(KEY_WEBSITES, emptySet()) ?: emptySet()
        cachedBlockedKeywords = prefs.getStringSet(KEY_KEYWORDS, emptySet()) ?: emptySet()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // ── HARDCODED UNINSTALL / SETTINGS OVERRIDE ──
        if (packageName == "com.android.settings" || 
            packageName == "com.google.android.packageinstaller" || 
            packageName == "com.samsung.android.packageinstaller" ||
            packageName == "com.android.packageinstaller"
        ) {
            launchMotivationScreen()
            return
        }

        // ── DEVICE ADMIN DEACTIVATION PROTECTION ──
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val className = event.className?.toString() ?: ""
            // If they reach the DeviceAdminAdd screen to untick the admin box
            if (className.contains("DeviceAdminAdd")) {
                launchMotivationScreen()
                return
            }
        }

        // Always exempt hard system apps
        if (hardExemptions.contains(packageName)) return
        if (isCooldownActive) return

        // Build effective blocked set (Strict Mode additions)
        val strictExtras = if (isStrictMode) setOf("com.android.settings", "com.android.vending") else emptySet()

        // ── Feature 4: Track foreground time ──
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            trackForegroundTime(packageName)
        }

        // ── NORMAL BLOCK MODE ──
        val effectiveBlocklist = cachedBlockedApps + strictExtras
        if (effectiveBlocklist.contains(packageName)) {
            if (ChallengeActivity.isExempt(this, packageName)) return
            recordBlock(packageName)
            if (isMentalFriction) launchChallengeScreen(packageName) else launchMotivationScreen()
            return
        }

        // ── BROWSER DEEP SCAN (Feature 3 enhanced) ──
        if (targetBrowsers.contains(packageName)) {
            val rootNode = rootInActiveWindow ?: return
            val allPatterns = cachedBlockedWebsites + cachedBlockedKeywords + DEFAULT_BLOCKED_PATTERNS

            // 1. Check URL bar text directly
            val urlBarId = when (packageName) {
                "com.android.chrome"           -> "com.android.chrome:id/url_bar"
                "com.sec.android.app.sbrowser" -> "com.sec.android.app.sbrowser:id/location_bar_edit_text"
                "com.brave.browser"            -> "com.brave.browser:id/url_bar"
                "org.mozilla.firefox"          -> "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"
                else                           -> null
            }
            if (urlBarId != null) {
                val urlBarNodes = rootNode.findAccessibilityNodeInfosByViewId(urlBarId)
                val url = urlBarNodes?.firstOrNull()?.text?.toString()?.lowercase() ?: ""
                if (allPatterns.any { url.contains(it) }) {
                    recordBlock(packageName)
                    if (isMentalFriction) launchChallengeScreen(packageName) else launchMotivationScreen()
                    return
                }
            }

            // 2. Deep node tree scan for any text/title match
            if (allPatterns.isNotEmpty() && scanNodeForKeywords(rootNode, allPatterns)) {
                recordBlock(packageName)
                if (isMentalFriction) launchChallengeScreen(packageName) else launchMotivationScreen()
                return
            }
        }
    }

    private fun trackForegroundTime(packageName: String) {
        val now = System.currentTimeMillis()
        if (lastFgPackage.isNotEmpty() && lastFgPackage != packageName) {
            val elapsed = now - lastFgStartMs
            if (elapsed in 1_000..300_000) { // 1s to 5min sanity range
                val statsPrefs = getSharedPreferences(STATS_PREFS, Context.MODE_PRIVATE)
                val key = "time_${lastFgPackage.replace('.', '_')}"
                val previous = statsPrefs.getLong(key, 0L)
                statsPrefs.edit().putLong(key, previous + elapsed).apply()

                // Update category totals
                val category = getProductivityCategory(lastFgPackage)
                val catKey = "time_cat_$category"
                val catPrev = statsPrefs.getLong(catKey, 0L)
                statsPrefs.edit().putLong(catKey, catPrev + elapsed).apply()
            }
        }
        lastFgPackage = packageName
        lastFgStartMs = now
    }

    fun getProductivityCategory(pkg: String): String = when {
        PRODUCTIVE_APPS.contains(pkg)   -> "STUDY"
        DISTRACTION_APPS.contains(pkg)  -> "SOCIAL"
        else                            -> "SYSTEM"
    }

    private fun recordBlock(packageName: String) {
        val stats = getSharedPreferences(STATS_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val total = stats.getInt(KEY_TOTAL_BLOCKS, 0) + 1

        val lastWeekSnapshot = stats.getLong("last_week_reset", 0L)
        val msInWeek = 7L * 24 * 60 * 60 * 1000
        val weekly = if (now - lastWeekSnapshot > msInWeek) {
            stats.edit()
                .putInt(KEY_LAST_WEEK_TOTAL, stats.getInt(KEY_WEEKLY_BLOCKS, 0))
                .putLong("last_week_reset", now)
                .apply()
            1
        } else {
            stats.getInt(KEY_WEEKLY_BLOCKS, 0) + 1
        }

        val appKey = "app_count_${packageName.replace('.', '_')}"
        val appCount = stats.getInt(appKey, 0) + 1

        val existing = stats.getString(KEY_BLOCK_TIMESTAMPS, "") ?: ""
        val timestamps = existing.split(",").filter { it.isNotBlank() }.takeLast(99)
        val updatedTimestamps = (timestamps + now.toString()).joinToString(",")

        stats.edit()
            .putInt(KEY_TOTAL_BLOCKS, total)
            .putInt(KEY_WEEKLY_BLOCKS, weekly)
            .putInt(appKey, appCount)
            .putString(KEY_BLOCK_TIMESTAMPS, updatedTimestamps)
            .apply()
    }

    private fun scanNodeForKeywords(node: AccessibilityNodeInfo, keywords: Set<String>): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        for (keyword in keywords) {
            if (text.contains(keyword) || contentDesc.contains(keyword)) return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (scanNodeForKeywords(child, keywords)) { child.recycle(); return true }
            child.recycle()
        }
        return false
    }

    private fun launchChallengeScreen(blockedPackage: String) {
        if (isCooldownActive) return
        isCooldownActive = true
        val intent = Intent(this, ChallengeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            putExtra(ChallengeActivity.EXTRA_PACKAGE, blockedPackage)
        }
        startActivity(intent)
        handler.postDelayed({ isCooldownActive = false }, 3000)
    }

    private fun launchMotivationScreen() {
        if (isCooldownActive) return
        isCooldownActive = true
        val intent = Intent(this, MotivationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        startActivity(intent)
        handler.postDelayed({ isCooldownActive = false }, 3000)
    }

    override fun onInterrupt() {}
}
