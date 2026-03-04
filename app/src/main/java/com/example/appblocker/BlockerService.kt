package com.example.appblocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BlockerService : AccessibilityService() {

    // Prefs keys — must match MainActivity
    private val PREFS_NAME = "BlockerPrefs"
    private val KEY_APPS = "blocked_apps"
    private val KEY_WEBSITES = "blocked_websites"
    private val KEY_KEYWORDS = "blocked_keywords"

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
        "com.sec.android.app.launcher"  // Samsung launcher
    )

    private val handler = Handler(Looper.getMainLooper())
    private var isCooldownActive = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Trigger on app switches AND on content changes (scroll/type/tab switch)
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val packageName = event.packageName?.toString() ?: return

        // SELF-EXEMPTION: Never block our app or system UI
        if (systemExemptions.contains(packageName)) return

        // COOLDOWN: Give user time to navigate away after closing the wall
        if (isCooldownActive) return

        // Load the latest lists from SharedPreferences on every event
        // This means your additions in MainActivity take effect immediately
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val blockedApps = prefs.getStringSet(KEY_APPS, emptySet()) ?: emptySet()
        val blockedWebsites = prefs.getStringSet(KEY_WEBSITES, emptySet()) ?: emptySet()
        val blockedKeywords = prefs.getStringSet(KEY_KEYWORDS, emptySet()) ?: emptySet()

        // RULE 1: Block social media apps the instant they appear
        if (blockedApps.contains(packageName)) {
            launchMotivationScreen()
            return
        }

        // RULE 2: For browsers, scan the URL bar and keywords
        if (targetBrowsers.contains(packageName)) {
            val rootNode = rootInActiveWindow ?: return

            // URL BAR CHECK: Runs every time the browser is active (all event types)
            // Chrome exposes a stable view ID for its URL bar — most reliable method
            if (blockedWebsites.isNotEmpty()) {
                val urlBarId = when (packageName) {
                    "com.android.chrome"         -> "com.android.chrome:id/url_bar"
                    "com.sec.android.app.sbrowser" -> "com.sec.android.app.sbrowser:id/location_bar_edit_text"
                    "com.brave.browser"          -> "com.brave.browser:id/url_bar"
                    "org.mozilla.firefox"        -> "org.mozilla.firefox:id/mozac_browser_toolbar_url_view"
                    else                         -> null
                }

                if (urlBarId != null) {
                    val urlBarNodes = rootNode.findAccessibilityNodeInfosByViewId(urlBarId)
                    if (urlBarNodes != null && urlBarNodes.isNotEmpty()) {
                        val url = urlBarNodes[0].text?.toString()?.lowercase() ?: ""
                        if (blockedWebsites.any { url.contains(it) }) {
                            launchMotivationScreen()
                            return
                        }
                    }
                }
            }

            // KEYWORD DEEP SCAN: Only run if there are keywords to check
            if (blockedKeywords.isNotEmpty() && scanNodeForKeywords(rootNode, blockedKeywords)) {
                launchMotivationScreen()
                return
            }
        }
    }

    // Recursive function: scans every UI node for any blocked keyword
    private fun scanNodeForKeywords(node: AccessibilityNodeInfo, keywords: Set<String>): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (keyword in keywords) {
            if (text.contains(keyword) || contentDesc.contains(keyword)) {
                return true
            }
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

    // Launches Motivation Wall with FLAG_ACTIVITY_CLEAR_TOP and a 3-second cooldown
    private fun launchMotivationScreen() {
        if (isCooldownActive) return
        isCooldownActive = true

        val intent = Intent(this, MotivationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)

        // Reset cooldown after 3 seconds
        handler.postDelayed({
            isCooldownActive = false
        }, 3000)
    }

    override fun onInterrupt() {}
}
