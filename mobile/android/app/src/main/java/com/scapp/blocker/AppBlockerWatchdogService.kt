package com.scapp.blocker

import android.app.*
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log

class AppBlockerWatchdogService : Service() {

    companion object {
        private const val TAG = "WatchdogService"
        private const val CHANNEL_ID = "focus_shield_watchdog"
        private const val NOTIF_ID = 1001
        var isRunning = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, AppBlockerWatchdogService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppBlockerWatchdogService::class.java)
            context.stopService(intent)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastBlockedPackage: String? = null
    private var lastBlockedTime: Long = 0

    private val pollRunnable = object : Runnable {
        override fun run() {
            checkForegroundApp()
            handler.postDelayed(this, 150) // High-frequency 150ms check
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIF_ID, createNotification())
        handler.post(pollRunnable)
        Log.i(TAG, "FocusShield Watchdog Service started.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun isDutyModeWhitelisted(pkg: String): Boolean {
        if (pkg == applicationContext.packageName ||
            pkg == "com.android.systemui" ||
            pkg.contains("launcher", ignoreCase = true) ||
            pkg.contains("inputmethod", ignoreCase = true) ||
            pkg.contains("keyboard", ignoreCase = true)) {
            return true
        }
        if (pkg == "com.openai.chatgpt" ||
            pkg == "com.anthropic.claudebot" ||
            pkg == "com.google.android.apps.bard" ||
            pkg.contains("perplexity", ignoreCase = true) ||
            pkg.contains("chatgpt", ignoreCase = true)) {
            return true
        }
        if (pkg == "com.android.chrome" ||
            pkg == "org.mozilla.firefox" ||
            pkg == "com.brave.browser" ||
            pkg == "com.microsoft.emmx") {
            return true
        }
        if (pkg.contains("calculator", ignoreCase = true) ||
            pkg == "com.google.android.calculator" ||
            pkg == "com.sec.android.app.popupcalculator" ||
            pkg == "com.miui.calculator" ||
            pkg == "com.android.calculator2") {
            return true
        }
        return false
    }

    private fun checkForegroundApp() {
        val prefs = getSharedPreferences(AppBlockerAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
        val isMaster = prefs.getBoolean(AppBlockerAccessibilityService.KEY_MASTER_ACTIVE, true)
        if (!isMaster) return

        val foregroundPkg = getForegroundPackage() ?: return

        // Skip own app, system UI, and launchers
        if (foregroundPkg == packageName ||
            foregroundPkg == "com.android.systemui" ||
            foregroundPkg.contains("launcher", ignoreCase = true)) {
            return
        }

        val isDutyModeActive = prefs.getBoolean(AppBlockerAccessibilityService.KEY_DUTY_MODE_ACTIVE, false)
        val blockedSet = prefs.getStringSet(AppBlockerAccessibilityService.KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()

        val shouldBlock: Boolean
        val isDutyBlock: Boolean

        if (isDutyModeActive) {
            if (isDutyModeWhitelisted(foregroundPkg)) return
            shouldBlock = true
            isDutyBlock = true
        } else {
            if (!blockedSet.contains(foregroundPkg)) return
            shouldBlock = true
            isDutyBlock = false
        }

        if (shouldBlock) {
            val now = System.currentTimeMillis()
            if (foregroundPkg == lastBlockedPackage && (now - lastBlockedTime) < 1500) {
                return
            }
            lastBlockedPackage = foregroundPkg
            lastBlockedTime = now

            Log.w(TAG, "Watchdog intercepted target: $foregroundPkg (DutyMode=$isDutyBlock)")

            try {
                val overlayIntent = Intent(this, BlockOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("BLOCKED_PACKAGE", foregroundPkg)
                    putExtra("IS_DUTY_MODE", isDutyBlock)
                }
                startActivity(overlayIntent)
            } catch (e: Exception) {
                BlockOverlayManager.show(applicationContext, foregroundPkg)
            }
        }
    }

    private fun getForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10000 // Last 10 seconds

        // Strategy A: UsageEvents (precise activity transitions)
        try {
            val usageEvents = usm.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var lastResumedPackage: String? = null
            var lastEventTime: Long = 0

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && event.timeStamp >= lastEventTime) {
                    lastResumedPackage = event.packageName
                    lastEventTime = event.timeStamp
                }
            }
            if (lastResumedPackage != null) {
                return lastResumedPackage
            }
        } catch (e: Exception) {
            Log.e(TAG, "UsageEvents query error", e)
        }

        // Strategy B: queryUsageStats fallback
        try {
            val statsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
            if (!statsList.isNullOrEmpty()) {
                var mostRecentStats: android.app.usage.UsageStats? = null
                for (stats in statsList) {
                    if (mostRecentStats == null || stats.lastTimeUsed > mostRecentStats.lastTimeUsed) {
                        mostRecentStats = stats
                    }
                }
                return mostRecentStats?.packageName
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryUsageStats fallback error", e)
        }

        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusShield Active Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors and intercepts blocked apps to keep you focused"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("FocusShield Active")
            .setContentText("Protecting your focus from blocked apps")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(pollRunnable)
        Log.i(TAG, "FocusShield Watchdog Service stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
