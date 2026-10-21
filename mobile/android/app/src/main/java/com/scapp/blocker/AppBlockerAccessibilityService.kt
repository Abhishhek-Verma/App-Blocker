package com.scapp.blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AppBlockerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AppBlockerService"
        const val PREFS_NAME = "AppBlockerPrefs"
        const val KEY_BLOCKED_PACKAGES = "blocked_packages"
        const val KEY_MASTER_ACTIVE = "master_active"
        const val KEY_REQUIRE_PIN = "require_pin"
        const val KEY_PIN_CODE = "pin_code"

        var isServiceRunning = false
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.i(TAG, "FocusShield Accessibility Service connected and active.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return

        // 1. Never intercept our own app or the system launcher / System UI
        if (packageName == applicationContext.packageName ||
            packageName == "com.android.systemui" ||
            packageName.contains("launcher", ignoreCase = true)) {
            return
        }

        // 2. Read SharedPreferences fast local cache (<1ms response time)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isMasterActive = prefs.getBoolean(KEY_MASTER_ACTIVE, true)
        if (!isMasterActive) {
            return
        }

        val blockedSet = prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()

        // 3. Intercept blocked target (e.g. com.instagram.android)
        if (blockedSet.contains(packageName)) {
            Log.w(TAG, "INTERCEPTED BLOCKED APP: $packageName! Enforcing shield.")

            // Send user to Home screen to dismiss blocked app instantly
            performGlobalAction(GLOBAL_ACTION_HOME)

            // Launch full-screen custom blocker overlay activity
            val overlayIntent = Intent(this, BlockOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("BLOCKED_PACKAGE", packageName)
            }
            startActivity(overlayIntent)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        Log.i(TAG, "Accessibility Service destroyed.")
    }
}
