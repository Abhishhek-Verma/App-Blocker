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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting service", e)
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
            try {
                checkForegroundApp()
            } catch (t: Throwable) {
                Log.e(TAG, "Watchdog tick error", t)
            } finally {
                handler.postDelayed(this, 120) // Fast 120ms polling interval
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()

        // Robust Android 14+ FGS startup
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIF_ID,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, createNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground with type failed, attempting fallback", e)
            try {
                startForeground(NOTIF_ID, createNotification())
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback startForeground failed", e2)
            }
        }

        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        Log.i(TAG, "FocusShield Watchdog Service started.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handler.removeCallbacks(pollRunnable)
        handler.post(pollRunnable)
        return START_STICKY
    }

    private fun checkForegroundApp() {
        val prefs = getSharedPreferences(AppBlockerConfig.PREFS_NAME, Context.MODE_PRIVATE)
        val isMaster = prefs.getBoolean(AppBlockerConfig.KEY_MASTER_ACTIVE, true)
        if (!isMaster) return

        val foregroundPkg = getForegroundPackage() ?: return

        // Skip own app, system UI, and launchers
        if (foregroundPkg == packageName ||
            foregroundPkg == "com.android.systemui" ||
            foregroundPkg.contains("launcher", ignoreCase = true) ||
            foregroundPkg.contains("home", ignoreCase = true)) {
            return
        }

        val blockedSet = prefs.getStringSet(AppBlockerConfig.KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
        if (!blockedSet.contains(foregroundPkg)) return

        val now = System.currentTimeMillis()
        if (foregroundPkg == lastBlockedPackage && (now - lastBlockedTime) < 1000) {
            return
        }
        lastBlockedPackage = foregroundPkg
        lastBlockedTime = now

        Log.w(TAG, "INTERCEPTED BLOCKED TARGET: $foregroundPkg. Enforcing firewall shield.")

        // 1. Kick user back to Android Home Screen immediately
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(homeIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching home intent", e)
        }

        // 2. Attach full-screen WindowManager overlay shield
        try {
            BlockOverlayManager.show(applicationContext, foregroundPkg)
        } catch (e: Exception) {
            Log.e(TAG, "Failed showing WindowManager overlay", e)
        }

        // 3. Launch dedicated block overlay Activity
        try {
            val overlayIntent = Intent(this, BlockOverlayActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("BLOCKED_PACKAGE", foregroundPkg)
            }
            startActivity(overlayIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching overlay activity", e)
        }
    }

    private fun getForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (1000 * 60) // Last 60 seconds

        // Strategy A: UsageEvents (last 60s)
        try {
            val usageEvents = usm.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var lastResumedPackage: String? = null
            var lastEventTime: Long = 0

            while (usageEvents.hasNextEvent()) {
                usageEvents.getNextEvent(event)
                // 1 = ACTIVITY_RESUMED / MOVE_TO_FOREGROUND
                if ((event.eventType == UsageEvents.Event.ACTIVITY_RESUMED || event.eventType == 1) &&
                    event.timeStamp >= lastEventTime) {
                    lastResumedPackage = event.packageName
                    lastEventTime = event.timeStamp
                }
            }
            if (!lastResumedPackage.isNullOrEmpty()) {
                return lastResumedPackage
            }
        } catch (e: Exception) {
            Log.e(TAG, "UsageEvents query error", e)
        }

        // Strategy B: queryUsageStats fallback (last 10 minutes)
        try {
            val statsList = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                endTime - (1000 * 60 * 10),
                endTime
            )
            if (!statsList.isNullOrEmpty()) {
                var mostRecentStats: android.app.usage.UsageStats? = null
                for (stats in statsList) {
                    if (mostRecentStats == null || stats.lastTimeUsed > mostRecentStats.lastTimeUsed) {
                        mostRecentStats = stats
                    }
                }
                if (mostRecentStats != null && mostRecentStats.lastTimeUsed > (endTime - 30000)) {
                    return mostRecentStats.packageName
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "queryUsageStats fallback error", e)
        }

        // Strategy C: ActivityManager running tasks fallback
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            @Suppress("DEPRECATION")
            val tasks = am?.getRunningTasks(1)
            if (!tasks.isNullOrEmpty()) {
                val top = tasks[0].topActivity?.packageName
                if (!top.isNullOrEmpty()) return top
            }
        } catch (e: Exception) {
            // Ignore
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
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("FocusShield Active")
            .setContentText("Firewall guarding against restricted apps")
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
