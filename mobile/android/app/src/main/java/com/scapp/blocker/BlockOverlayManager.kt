package com.scapp.blocker

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.scapp.MainActivity
import com.scapp.R

object BlockOverlayManager {

    private const val TAG = "BlockOverlayManager"
    private var windowManager: WindowManager? = null
    private var currentOverlayView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val autoDismissRunnable = Runnable {
        hide()
    }

    @Synchronized
    fun show(context: Context, blockedPackage: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Cannot draw overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        mainHandler.post {
            try {
                if (windowManager == null) {
                    windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                }
                val wm = windowManager ?: return@post

                // If an overlay is already showing, update its content instead of adding duplicate
                if (currentOverlayView != null) {
                    updateViewContent(context, currentOverlayView!!, blockedPackage)
                    return@post
                }

                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutType,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                val inflater = LayoutInflater.from(context.applicationContext)
                val overlayView = inflater.inflate(R.layout.overlay_window, null)

                updateViewContent(context, overlayView, blockedPackage)

                overlayView.findViewById<Button>(R.id.btn_overlay_return_home).setOnClickListener {
                    hide()
                    val focusIntent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    context.startActivity(focusIntent)
                }

                wm.addView(overlayView, params)
                currentOverlayView = overlayView
                Log.i(TAG, "Successfully attached WindowManager overlay for: $blockedPackage")

                // Auto-dismiss safety timer (8 seconds)
                mainHandler.removeCallbacks(autoDismissRunnable)
                mainHandler.postDelayed(autoDismissRunnable, 8000)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to show WindowManager overlay", e)
            }
        }
    }

    private fun updateViewContent(context: Context, view: View, blockedPackage: String) {
        val pm = context.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(blockedPackage, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            blockedPackage
        }

        view.findViewById<TextView>(R.id.tv_overlay_app_name)?.text = "$appName is Blocked"
        view.findViewById<TextView>(R.id.tv_overlay_package_name)?.text = blockedPackage
    }

    @Synchronized
    fun hide() {
        mainHandler.post {
            try {
                mainHandler.removeCallbacks(autoDismissRunnable)
                currentOverlayView?.let {
                    windowManager?.removeView(it)
                    Log.i(TAG, "Removed WindowManager overlay")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            } finally {
                currentOverlayView = null
            }
        }
    }
}
