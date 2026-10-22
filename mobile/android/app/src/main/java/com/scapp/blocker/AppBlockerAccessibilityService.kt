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

        const val KEY_DUTY_MODE_ACTIVE = "duty_mode_active"

        var isServiceRunning = false
            private set
    }

    private var lastInterceptedPackage: String? = null
    private var lastInterceptedTime: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        Log.i(TAG, "FocusShield Accessibility Service connected and active.")
    }

    private fun isDutyModeWhitelisted(pkg: String): Boolean {
        // Essential system components
        if (pkg == applicationContext.packageName ||
            pkg == "com.android.systemui" ||
            pkg.contains("launcher", ignoreCase = true) ||
            pkg.contains("inputmethod", ignoreCase = true) ||
            pkg.contains("keyboard", ignoreCase = true)) {
            return true
        }

        // Classroom Tools: ChatGPT / AI Assistants
        if (pkg == "com.openai.chatgpt" ||
            pkg == "com.anthropic.claudebot" ||
            pkg == "com.google.android.apps.bard" ||
            pkg.contains("perplexity", ignoreCase = true) ||
            pkg.contains("chatgpt", ignoreCase = true)) {
            return true
        }

        // Classroom Tools: Chrome & Educational Browsers
        if (pkg == "com.android.chrome" ||
            pkg == "org.mozilla.firefox" ||
            pkg == "com.brave.browser" ||
            pkg == "com.microsoft.emmx") {
            return true
        }

        // Classroom Tools: Calculator
        if (pkg.contains("calculator", ignoreCase = true) ||
            pkg == "com.google.android.calculator" ||
            pkg == "com.sec.android.app.popupcalculator" ||
            pkg == "com.miui.calculator" ||
            pkg == "com.android.calculator2") {
            return true
        }

        return false
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

        val isDutyModeActive = prefs.getBoolean(KEY_DUTY_MODE_ACTIVE, false)
        val blockedSet = prefs.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()

        val shouldBlock: Boolean
        val isDutyBlock: Boolean

        if (isDutyModeActive) {
            // In Duty Mode: Strictly whitelist classroom tools only (ChatGPT, Chrome, Calculator).
            if (isDutyModeWhitelisted(packageName)) {
                return
            }
            shouldBlock = true
            isDutyBlock = true
        } else {
            // Standard Mode: Block user's selected blocked set (e.g. Instagram, etc.)
            if (!blockedSet.contains(packageName)) {
                return
            }
            shouldBlock = true
            isDutyBlock = false
        }

        if (shouldBlock) {
            val now = System.currentTimeMillis()
            if (packageName == lastInterceptedPackage && (now - lastInterceptedTime) < 1500) {
                return
            }
            lastInterceptedPackage = packageName
            lastInterceptedTime = now

            Log.w(TAG, "INTERCEPTED TARGET: $packageName (DutyMode=$isDutyBlock). Enforcing shield.")

            // ScreenZen style polite intervention activity
            try {
                val overlayIntent = Intent(this, BlockOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("BLOCKED_PACKAGE", packageName)
                    putExtra("IS_DUTY_MODE", isDutyBlock)
                }
                startActivity(overlayIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Activity launch fallback error", e)
                BlockOverlayManager.show(applicationContext, packageName)
            }
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
