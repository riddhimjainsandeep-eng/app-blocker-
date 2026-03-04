package com.example.appblocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BlockerService : AccessibilityService() {

    // Must match MainActivity
    private val PREFS_NAME = "BlockerPrefs"
    private val KEY_APPS = "blocked_apps"
    private val KEY_WEBSITES = "blocked_websites"
    private val KEY_KEYWORDS = "blocked_keywords"

    // Stats keys — stored separately for report generation
    private val STATS_PREFS = "BlockerStats"
    private val KEY_TOTAL_BLOCKS = "total_blocks"
    private val KEY_WEEKLY_BLOCKS = "weekly_blocks"
    private val KEY_BLOCK_TIMESTAMPS = "block_timestamps"     // comma-separated epoch millis
    private val KEY_LAST_WEEK_TOTAL = "last_week_total"       // snapshot reset on Sundays

    // Browsers to perform deep scans on
    private val targetBrowsers = setOf(
        "com.android.chrome",
        "com.brave.browser",
        "org.mozilla.firefox",
        "com.sec.android.app.sbrowser"
    )

    // Packages that should NEVER be blocked
    private val systemExemptions = setOf(
        "com.example.appblocker",
        "com.android.settings",
        "com.android.systemui",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var isCooldownActive = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val packageName = event.packageName?.toString() ?: return

        if (systemExemptions.contains(packageName)) return
        if (isCooldownActive) return

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isWhitelistMode = prefs.getBoolean("is_whitelist_mode", false)
        
        val blockedApps     = prefs.getStringSet(KEY_APPS, emptySet()) ?: emptySet()
        val whitelistApps   = prefs.getStringSet("whitelist_apps", emptySet()) ?: emptySet()
        val blockedWebsites = prefs.getStringSet(KEY_WEBSITES, emptySet()) ?: emptySet()
        val blockedKeywords = prefs.getStringSet(KEY_KEYWORDS, emptySet()) ?: emptySet()

        // ── WHITELIST MODE (Includes Study Session) ──
        if (isWhitelistMode) {
            // Block everything EXCEPT whitelist apps and system exemptions
            if (!whitelistApps.contains(packageName) && !systemExemptions.contains(packageName)) {
                recordBlock(packageName)
                launchMotivationScreen()
                return
            }
        } else {
            // ── NORMAL BLOCK MODE ──
            if (blockedApps.contains(packageName)) {
                recordBlock(packageName)
                launchMotivationScreen()
                return
            }
        }

        // RULE 2: Deep-scan browsers for URLs + keywords
        if (targetBrowsers.contains(packageName)) {
            val rootNode = rootInActiveWindow ?: return

            if (blockedWebsites.isNotEmpty()) {
                val urlBarId = when (packageName) {
                    "com.android.chrome"           -> "com.android.chrome:id/url_bar"
                    "com.sec.android.app.sbrowser" -> "com.sec.android.app.sbrowser:id/location_bar_edit_text"
                    "com.brave.browser"            -> "com.brave.browser:id/url_bar"
                    "org.mozilla.firefox"          -> "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"
                    else                           -> null
                }
                if (urlBarId != null) {
                    val urlBarNodes = rootNode.findAccessibilityNodeInfosByViewId(urlBarId)
                    if (urlBarNodes != null && urlBarNodes.isNotEmpty()) {
                        val url = urlBarNodes[0].text?.toString()?.lowercase() ?: ""
                        if (blockedWebsites.any { url.contains(it) }) {
                            recordBlock(packageName)
                            launchMotivationScreen()
                            return
                        }
                    }
                }
            }

            if (blockedKeywords.isNotEmpty() && scanNodeForKeywords(rootNode, blockedKeywords)) {
                recordBlock(packageName)
                launchMotivationScreen()
                return
            }
        }
    }

    // Records a block attempt to SharedPreferences for reporting
    private fun recordBlock(packageName: String) {
        val stats = getSharedPreferences(STATS_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        // Total all-time counter
        val total = stats.getInt(KEY_TOTAL_BLOCKS, 0) + 1

        // Weekly counter — reset if it's a new week (Sunday)
        val lastWeekSnapshot = stats.getLong("last_week_reset", 0L)
        val msInWeek = 7L * 24 * 60 * 60 * 1000
        val weekly = if (now - lastWeekSnapshot > msInWeek) {
            // New week — save last week's count, reset
            stats.edit()
                .putInt(KEY_LAST_WEEK_TOTAL, stats.getInt(KEY_WEEKLY_BLOCKS, 0))
                .putLong("last_week_reset", now)
                .apply()
            1 // start fresh this week
        } else {
            stats.getInt(KEY_WEEKLY_BLOCKS, 0) + 1
        }

        // Per-app counter
        val appKey = "app_count_${packageName.replace('.', '_')}"
        val appCount = stats.getInt(appKey, 0) + 1

        // Store timestamps (last 100 entries as comma-separated millis)
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
            if (scanNodeForKeywords(child, keywords)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    private fun launchMotivationScreen() {
        if (isCooldownActive) return
        isCooldownActive = true
        val intent = Intent(this, MotivationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
        handler.postDelayed({ isCooldownActive = false }, 3000)
    }

    override fun onInterrupt() {}
}
