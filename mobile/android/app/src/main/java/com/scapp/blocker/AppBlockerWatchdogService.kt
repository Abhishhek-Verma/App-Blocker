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

    private fun checkForegroundApp() {
        val prefs = getSharedPreferences(AppBlockerAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
        val isMaster = prefs.getBoolean(AppBlockerAccessibilityService.KEY_MASTER_ACTIVE, true)
        if (!isMaster) return

        val blockedSet = prefs.getStringSet(AppBlockerAccessibilityService.KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
        if (blockedSet.isEmpty()) return

        val foregroundPkg = getForegroundPackage() ?: return

        // Skip own app, system UI, and launchers
        if (foregroundPkg == packageName ||
            foregroundPkg == "com.android.systemui" ||
            foregroundPkg.contains("launcher", ignoreCase = true)) {
            return
        }

        if (blockedSet.contains(foregroundPkg)) {
            val now = System.currentTimeMillis()
            // Avoid duplicate rapid-fire launches for the same app within 1.2 seconds
            if (foregroundPkg == lastBlockedPackage && (now - lastBlockedTime) < 1200) {
                return
            }
            lastBlockedPackage = foregroundPkg
            lastBlockedTime = now

            Log.w(TAG, "Watchdog intercepted blocked app: $foregroundPkg")

            // 1. Kick user out of blocked app to phone Home screen immediately
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            try {
                startActivity(homeIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start home intent", e)
            }

            // 2. Display WindowManager system overlay
            BlockOverlayManager.show(applicationContext, foregroundPkg)

            // 3. Activity fallback
            try {
                val overlayIntent = Intent(this, BlockOverlayActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    putExtra("BLOCKED_PACKAGE", foregroundPkg)
                }
                startActivity(overlayIntent)
            } catch (e: Exception) {
                // Background activity restrictions on Android 10+ / MIUI are handled by BlockOverlayManager
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
