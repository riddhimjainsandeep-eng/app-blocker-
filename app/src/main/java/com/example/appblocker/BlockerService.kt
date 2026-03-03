package com.example.appblocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class BlockerService : AccessibilityService() {

    private val blockedApps = setOf(
        "com.instagram.android",
        "com.facebook.katana",
        "com.zhiliaoapp.musically", // TikTok
        "com.google.android.youtube"
    )

    private val blockedWebsites = listOf(
        "instagram.com",
        "facebook.com",
        "youtube.com/shorts"
    )

    private val blockedKeywords = setOf(
        "reels", "shorts", "explore", "trending", "feed", "story"
    )

    private val handler = Handler(Looper.getMainLooper())
    private var blockRunnable: Runnable? = null
    private var currentUrl = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // 1. Block specific apps instantly
        if (blockedApps.contains(packageName)) {
            launchMotivationScreen()
            return
        }

        // 2. Perform Deep Scan for Blocked Keywords in UI
        val rootNode = rootInActiveWindow ?: return
        if (scanNodesForKeywords(rootNode)) {
            launchMotivationScreen()
            return
        }

        // 3. Block specific websites in Google Chrome with a 3-second delay
        if (packageName == "com.android.chrome") {
            val urlNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
            
            if (urlNodes != null && urlNodes.isNotEmpty()) {
                val urlBarNode = urlNodes[0]
                currentUrl = urlBarNode.text?.toString() ?: ""
                
                val isCurrentlyBlocked = blockedWebsites.any { currentUrl.contains(it, ignoreCase = true) }
                
                if (isCurrentlyBlocked) {
                    if (blockRunnable == null) {
                        var countdown = 3
                        blockRunnable = object : Runnable {
                            override fun run() {
                                // Re-check the URL to see if the user switched tabs
                                val stillBlocked = blockedWebsites.any { currentUrl.contains(it, ignoreCase = true) }
                                if (!stillBlocked) {
                                    cancelWarning()
                                    return
                                }
                                
                                if (countdown > 0) {
                                    Toast.makeText(this@BlockerService, "Blocked site detected! You have 3 seconds to switch tabs or close it.", Toast.LENGTH_SHORT).show()
                                    countdown--
                                    handler.postDelayed(this, 1000)
                                } else {
                                    launchMotivationScreen()
                                    cancelWarning()
                                }
                            }
                        }
                        handler.post(blockRunnable!!)
                    }
                } else {
                    // Safe site -> cancel any pending blocks
                    cancelWarning()
                }
            }
        }
    }

    private fun scanNodesForKeywords(node: AccessibilityNodeInfo): Boolean {
        // Check text
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""

        for (keyword in blockedKeywords) {
            if (text.contains(keyword) || contentDesc.contains(keyword)) {
                return true
            }
        }

        // Recursively check children
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (scanNodesForKeywords(child)) {
                    child.recycle() // Important for memory
                    return true
                }
                child.recycle()
            }
        }
        return false
    }

    private fun cancelWarning() {
        blockRunnable?.let { handler.removeCallbacks(it) }
        blockRunnable = null
    }

    private fun launchMotivationScreen() {
        val intent = Intent(this, MotivationActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
    }
}
