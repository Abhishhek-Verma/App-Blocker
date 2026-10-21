package com.scapp.blocker

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.facebook.react.bridge.*

class AppBlockerModule(private val reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String {
        return "AppBlockerModule"
    }

    /**
     * Scans and returns all launchable installed apps on the device.
     */
    @ReactMethod
    fun getInstalledApps(promise: Promise) {
        try {
            val pm = reactContext.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolvedActivities = pm.queryIntentActivities(mainIntent, 0)
            val appList = Arguments.createArray()

            for (resolveInfo in resolvedActivities) {
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val packageName = appInfo.packageName

                // Skip self
                if (packageName == reactContext.packageName) continue

                val appMap = Arguments.createMap().apply {
                    putString("packageName", packageName)
                    putString("appName", pm.getApplicationLabel(appInfo).toString())
                    putBoolean("isSystemApp", (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
                }
                appList.pushMap(appMap)
            }
            promise.resolve(appList)
        } catch (e: Exception) {
            promise.reject("SCAN_ERROR", e.message)
        }
    }

    /**
     * Updates blocked packages directly in SharedPreferences for immediate 0ms native detection.
     */
    @ReactMethod
    fun setBlockedPackages(packages: ReadableArray, promise: Promise) {
        try {
            val packageSet = HashSet<String>()
            for (i in 0 until packages.size()) {
                val pkg = packages.getString(i)
                if (!pkg.isNullOrEmpty()) {
                    packageSet.add(pkg)
                }
            }

            val prefs = reactContext.getSharedPreferences(
                AppBlockerAccessibilityService.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            prefs.edit().putStringSet(AppBlockerAccessibilityService.KEY_BLOCKED_PACKAGES, packageSet).apply()

            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("WRITE_ERROR", e.message)
        }
    }

    /**
     * Reads current blocked packages from local cache.
     */
    @ReactMethod
    fun getBlockedPackages(promise: Promise) {
        try {
            val prefs = reactContext.getSharedPreferences(
                AppBlockerAccessibilityService.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            val blockedSet = prefs.getStringSet(AppBlockerAccessibilityService.KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
            val array = Arguments.createArray()
            for (pkg in blockedSet) {
                array.pushString(pkg)
            }
            promise.resolve(array)
        } catch (e: Exception) {
            promise.reject("READ_ERROR", e.message)
        }
    }

    /**
     * Toggles the master blocker switch.
     */
    @ReactMethod
    fun setMasterActive(active: Boolean, promise: Promise) {
        try {
            val prefs = reactContext.getSharedPreferences(
                AppBlockerAccessibilityService.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            prefs.edit().putBoolean(AppBlockerAccessibilityService.KEY_MASTER_ACTIVE, active).apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("PREFS_ERROR", e.message)
        }
    }

    /**
     * Configures PIN protection.
     */
    @ReactMethod
    fun setPinCode(pin: String?, requirePin: Boolean, promise: Promise) {
        try {
            val prefs = reactContext.getSharedPreferences(
                AppBlockerAccessibilityService.PREFS_NAME,
                Context.MODE_PRIVATE
            )
            prefs.edit()
                .putString(AppBlockerAccessibilityService.KEY_PIN_CODE, pin)
                .putBoolean(AppBlockerAccessibilityService.KEY_REQUIRE_PIN, requirePin)
                .apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("PIN_ERROR", e.message)
        }
    }

    /**
     * Checks if Accessibility Service is enabled in system settings.
     */
    @ReactMethod
    fun isAccessibilityEnabled(promise: Promise) {
        try {
            val expectedServiceName = "${reactContext.packageName}/${AppBlockerAccessibilityService::class.java.canonicalName}"
            val enabledServices = Settings.Secure.getString(
                reactContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val isEnabled = enabledServices.contains(expectedServiceName) || AppBlockerAccessibilityService.isServiceRunning
            promise.resolve(isEnabled)
        } catch (e: Exception) {
            promise.resolve(false)
        }
    }

    /**
     * Opens Android Accessibility Settings.
     */
    @ReactMethod
    fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        reactContext.startActivity(intent)
    }

    /**
     * Checks if Draw Over Other Apps permission is granted.
     */
    @ReactMethod
    fun canDrawOverlays(promise: Promise) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            promise.resolve(Settings.canDrawOverlays(reactContext))
        } else {
            promise.resolve(true)
        }
    }

    /**
     * Opens Draw Over Other Apps permission screen.
     */
    @ReactMethod
    fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${reactContext.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            reactContext.startActivity(intent)
        }
    }
}
