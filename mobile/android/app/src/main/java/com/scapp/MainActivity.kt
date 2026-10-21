package com.scapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.*
import com.scapp.blocker.AppBlockerAccessibilityService
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val backendUrl = "https://app-blocker.onrender.com"
    private val prefsName = AppBlockerAccessibilityService.PREFS_NAME
    private val keyBlocked = AppBlockerAccessibilityService.KEY_BLOCKED_PACKAGES
    private val keyMaster = AppBlockerAccessibilityService.KEY_MASTER_ACTIVE

    private lateinit var swMasterShield: Switch
    private lateinit var tvShieldStatus: TextView
    private lateinit var btnToggleInstagram: Button
    private lateinit var tvInstagramBadge: TextView
    private lateinit var cardPermAlert: View
    private lateinit var layoutAppsContainer: LinearLayout
    private lateinit var tvRenderStatus: TextView

    private val blockedPackages = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swMasterShield = findViewById(R.id.sw_master_shield)
        tvShieldStatus = findViewById(R.id.tv_shield_status)
        btnToggleInstagram = findViewById(R.id.btn_toggle_instagram)
        tvInstagramBadge = findViewById(R.id.tv_instagram_badge)
        cardPermAlert = findViewById(R.id.card_permissions_alert)
        layoutAppsContainer = findViewById(R.id.layout_apps_container)
        tvRenderStatus = findViewById(R.id.tv_render_status)

        val btnAccessibility = findViewById<Button>(R.id.btn_grant_accessibility)
        val btnOverlay = findViewById<Button>(R.id.btn_grant_overlay)

        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        loadLocalPreferences()
        setupMasterToggle()
        setupInstagramHero()
        loadInstalledApps()
        checkPermissions()
        syncWithRender()
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun loadLocalPreferences() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val isMaster = prefs.getBoolean(keyMaster, true)
        swMasterShield.isChecked = isMaster
        updateMasterText(isMaster)

        val savedSet = prefs.getStringSet(keyBlocked, setOf("com.instagram.android")) ?: emptySet()
        blockedPackages.clear()
        blockedPackages.addAll(savedSet)
        updateInstagramBadge()
    }

    private fun saveLocalPreferences() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(keyBlocked, blockedPackages).apply()
        updateInstagramBadge()
    }

    private fun updateMasterText(active: Boolean) {
        if (active) {
            tvShieldStatus.text = "ACTIVE & BLOCKING"
            tvShieldStatus.setTextColor(Color.parseColor("#34D399"))
        } else {
            tvShieldStatus.text = "PAUSED (ALL APPS ALLOWED)"
            tvShieldStatus.setTextColor(Color.parseColor("#9CA3AF"))
        }
    }

    private fun updateInstagramBadge() {
        val isBlocked = blockedPackages.contains("com.instagram.android")
        if (isBlocked) {
            tvInstagramBadge.text = "BLOCKED"
            tvInstagramBadge.setTextColor(Color.parseColor("#EF4444"))
            btnToggleInstagram.text = "Unblock Instagram"
        } else {
            tvInstagramBadge.text = "ALLOWED"
            tvInstagramBadge.setTextColor(Color.parseColor("#10B981"))
            btnToggleInstagram.text = "Block Instagram Now"
        }
    }

    private fun setupMasterToggle() {
        swMasterShield.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(keyMaster, isChecked).apply()
            updateMasterText(isChecked)
            syncMasterToRender(isChecked)
        }
    }

    private fun setupInstagramHero() {
        btnToggleInstagram.setOnClickListener {
            val pkg = "com.instagram.android"
            if (blockedPackages.contains(pkg)) {
                blockedPackages.remove(pkg)
                Toast.makeText(this, "Instagram Unblocked", Toast.LENGTH_SHORT).show()
            } else {
                blockedPackages.add(pkg)
                Toast.makeText(this, "Instagram Blocked! Try opening it.", Toast.LENGTH_SHORT).show()
            }
            saveLocalPreferences()
            syncAppToggleToRender(pkg, "Instagram", blockedPackages.contains(pkg))
        }
    }

    private fun checkPermissions() {
        var accessibilityGranted = false
        val expectedService = "$packageName/${AppBlockerAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        if (enabledServices.contains(expectedService) || AppBlockerAccessibilityService.isServiceRunning) {
            accessibilityGranted = true
        }

        val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        if (accessibilityGranted && overlayGranted) {
            cardPermAlert.visibility = View.GONE
        } else {
            cardPermAlert.visibility = View.VISIBLE
        }
    }

    private fun loadInstalledApps() {
        layoutAppsContainer.removeAllViews()

        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolvedActivities = pm.queryIntentActivities(mainIntent, 0)

        for (resolveInfo in resolvedActivities) {
            val appInfo = resolveInfo.activityInfo.applicationInfo
            val pkg = appInfo.packageName

            if (pkg == packageName) continue

            val appLabel = pm.getApplicationLabel(appInfo).toString()
            val isBlocked = blockedPackages.contains(pkg)

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 20, 24, 20)
                setBackgroundColor(Color.parseColor("#141824"))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 14)
                layoutParams = params
            }

            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvName = TextView(this).apply {
                text = appLabel
                setTextColor(Color.WHITE)
                textSize = 15f
            }

            val tvPkg = TextView(this).apply {
                text = pkg
                setTextColor(Color.parseColor("#6B7280"))
                textSize = 11f
            }

            textLayout.addView(tvName)
            textLayout.addView(tvPkg)

            val sw = Switch(this).apply {
                isChecked = isBlocked
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        blockedPackages.add(pkg)
                    } else {
                        blockedPackages.remove(pkg)
                    }
                    saveLocalPreferences()
                    syncAppToggleToRender(pkg, appLabel, checked)
                }
            }

            row.addView(textLayout)
            row.addView(sw)
            layoutAppsContainer.addView(row)
        }
    }

    private fun syncWithRender() {
        thread {
            try {
                val url = URL("$backendUrl/health")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                runOnUiThread {
                    if (code == 200) {
                        tvRenderStatus.text = "● Render: Live"
                        tvRenderStatus.setTextColor(Color.parseColor("#34D399"))
                    } else {
                        tvRenderStatus.text = "● Offline Mode"
                        tvRenderStatus.setTextColor(Color.parseColor("#F59E0B"))
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvRenderStatus.text = "● Offline Mode"
                    tvRenderStatus.setTextColor(Color.parseColor("#F59E0B"))
                }
            }
        }
    }

    private fun syncAppToggleToRender(pkg: String, name: String, isBlocked: Boolean) {
        thread {
            try {
                val url = URL("$backendUrl/api/apps/toggle")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val payload = JSONObject().apply {
                    put("package_name", pkg)
                    put("app_name", name)
                    put("is_blocked", isBlocked)
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                conn.responseCode
            } catch (ignored: Exception) {}
        }
    }

    private fun syncMasterToRender(active: Boolean) {
        thread {
            try {
                val url = URL("$backendUrl/api/settings")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val payload = JSONObject().apply {
                    put("master_blocker_active", active)
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                conn.responseCode
            } catch (ignored: Exception) {}
        }
    }
}
