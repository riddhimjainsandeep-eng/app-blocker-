package com.example.appblocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class BlockerService : AccessibilityService() {

    // --- Apps to block outright when they become the foreground window ---
    private val blockedApps = setOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.zhiliaoapp.musically",   // TikTok
        "com.twitter.android",
        "com.snapchat.android"
    )

    // --- Websites to block when detected in Chrome's URL bar ---
    private val blockedWebsites = listOf(
        "instagram.com",
        "facebook.com",
        "youtube.com/shorts",
        "tiktok.com",
        "twitter.com",
        "snapchat.com"
    )

    // --- Keywords to scan for in browser content ---
    private val blockedKeywords = setOf(
        "reels", "shorts", "explore", "trending", "foryou", "story",
        "porn", "pornhub", "xxx", "onlyfans", "nsfw", "adult", "nude",
        "bypass", "vpn", "proxy", "unblock", "mirror"
    )

    // --- Browsers that should be scanned for keywords/URLs ---
    private val targetBrowsers = setOf(
        "com.android.chrome",
        "com.brave.browser",
        "org.mozilla.firefox",
        "com.sec.android.app.sbrowser"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var isCooldownActive = false

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // STATE VERIFICATION: Trigger on BOTH window switches AND content changes
        // (typing, scrolling, tab switches within an app)
        val eventType = event.eventType
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) return

        val packageName = event.packageName?.toString() ?: return

        // EARLY EXIT: Never block our own app or system settings
        if (packageName == "com.example.appblocker" ||
            packageName == "com.android.settings" ||
            packageName == "com.android.systemui"
        ) return

        // COOLDOWN CHECK: Prevent re-trigger right after the user exits the wall
        if (isCooldownActive) return

        // RULE 1: Block social media apps outright the moment they appear
        if (blockedApps.contains(packageName)) {
            launchMotivationScreen()
            return
        }

        // RULE 2: For browsers, perform a deep scan for blocked URLs and keywords
        if (targetBrowsers.contains(packageName)) {
            val rootNode = rootInActiveWindow ?: return

            // CHROME URL FORCE-CHECK: Check the URL bar directly every time
            if (packageName == "com.android.chrome") {
                val urlBarNodes = rootNode.findAccessibilityNodeInfosByViewId(
                    "com.android.chrome:id/url_bar"
                )
                if (urlBarNodes != null && urlBarNodes.isNotEmpty()) {
                    val url = urlBarNodes[0].text?.toString()?.lowercase() ?: ""
                    val isBlockedSite = blockedWebsites.any { url.contains(it) }
                    if (isBlockedSite) {
                        launchMotivationScreen()
                        return
                    }
                }
            }

            // KEYWORD DEEP SCAN: Scan all visible UI nodes for blocked words
            if (scanNodeForKeywords(rootNode)) {
                launchMotivationScreen()
                return
            }
        }
    }

    // Recursive function: scans every node in the UI tree for a blocked keyword
    private fun scanNodeForKeywords(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (keyword in blockedKeywords) {
            if (text.contains(keyword) || contentDesc.contains(keyword)) {
                return true
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (scanNodeForKeywords(child)) {
                child.recycle()
                return true
            }
            child.recycle()
        }
        return false
    }

    // Launches the Motivation Wall with a 3-second cooldown to prevent re-loops
    private fun launchMotivationScreen() {
        if (isCooldownActive) return
        isCooldownActive = true

        val intent = Intent(this, MotivationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)

        // Reset cooldown after 3 seconds — enough time to leave the blocked app
        handler.postDelayed({
            isCooldownActive = false
        }, 3000)
    }

    override fun onInterrupt() {}
}
