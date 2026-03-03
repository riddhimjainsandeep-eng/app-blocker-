package com.example.appblocker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class BlockerService : AccessibilityService() {

    private val blockedApps = setOf(
        "com.instagram.android",
        "com.facebook.katana"
    )

    private val blockedWebsites = listOf(
        "instagram.com",
        "facebook.com",
        "youtube.com/shorts"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // 1. Block specific apps
        if (blockedApps.contains(packageName)) {
            blockAndToast()
            return
        }

        // 2. Block specific websites in Google Chrome
        if (packageName == "com.android.chrome") {
            val rootNode = rootInActiveWindow ?: return
            val urlNodes = rootNode.findAccessibilityNodeInfosByViewId("com.android.chrome:id/url_bar")
            
            if (urlNodes != null && urlNodes.isNotEmpty()) {
                val urlBarNode = urlNodes[0]
                val urlText = urlBarNode.text?.toString() ?: ""
                
                for (blockedUrl in blockedWebsites) {
                    if (urlText.contains(blockedUrl, ignoreCase = true)) {
                        blockAndToast()
                        break
                    }
                }
            }
        }
    }

    private fun blockAndToast() {
        Toast.makeText(this, "Sorry, CA and ISC exams are your priority right now.", Toast.LENGTH_LONG).show()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    override fun onInterrupt() {
    }
}
