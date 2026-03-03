package com.example.appblocker

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class BlockerService : AccessibilityService() {

    private val blockedApps = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.twitter.android",
        "com.facebook.katana"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString() ?: return
            
            if (blockedApps.contains(packageName)) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
        }
    }

    override fun onInterrupt() {
    }
}
