package com.scapp

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import com.scapp.blocker.AppBlockerAccessibilityService
import com.scapp.blocker.AppBlockerWatchdogService
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
    private lateinit var tvRenderStatus: TextView
    private lateinit var tvBlockedCounter: TextView
    private lateinit var layoutAppsContainer: LinearLayout
    private lateinit var etSearchApps: EditText
    private lateinit var btnTabAll: Button
    private lateinit var btnTabBlocked: Button

    private lateinit var pillAccessibility: TextView
    private lateinit var pillOverlay: TextView
    private lateinit var pillUsage: TextView
    private lateinit var layoutRestrictedGuide: View

    private val blockedPackages = HashSet<String>()
    private val allInstalledApps = ArrayList<AppEntry>()
    private var currentFilter = FilterMode.ALL
    private var currentSearchQuery = ""

    enum class FilterMode { ALL, BLOCKED_ONLY }

    data class AppEntry(val packageName: String, val appName: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swMasterShield = findViewById(R.id.sw_master_shield)
        tvShieldStatus = findViewById(R.id.tv_shield_status)
        tvRenderStatus = findViewById(R.id.tv_render_status)
        tvBlockedCounter = findViewById(R.id.tv_blocked_counter)
        layoutAppsContainer = findViewById(R.id.layout_apps_container)
        etSearchApps = findViewById(R.id.et_search_apps)
        btnTabAll = findViewById(R.id.btn_tab_all)
        btnTabBlocked = findViewById(R.id.btn_tab_blocked)

        pillAccessibility = findViewById(R.id.pill_accessibility)
        pillOverlay = findViewById(R.id.pill_overlay)
        pillUsage = findViewById(R.id.pill_usage)
        layoutRestrictedGuide = findViewById(R.id.layout_restricted_setting_guide)

        setupPermissionButtons()
        setupMasterToggle()
        setupSearchAndTabs()

        loadLocalPreferences()
        scanInstalledApps()
        checkPermissions()
        syncWithRender()

        // Start dual-protection watchdog service
        AppBlockerWatchdogService.start(this)
    }

    override fun onResume() {
        super.onResume()
        checkPermissions()
    }

    private fun setupPermissionButtons() {
        findViewById<Button>(R.id.btn_open_app_info).setOnClickListener {
            // Direct shortcut to FocusShield App Info so user can tap ⋮ (3 dots) -> "Allow restricted settings"
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(this, "Tap the ⋮ (3 dots) in the top-right corner, then tap 'Allow restricted settings'", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.btn_grant_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btn_grant_overlay).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            }
        }

        findViewById<Button>(R.id.btn_grant_usage).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun checkPermissions() {
        // 1. Check Accessibility
        val expectedService = "$packageName/${AppBlockerAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val isAccessGranted = enabledServices.contains(expectedService) || AppBlockerAccessibilityService.isServiceRunning

        if (isAccessGranted) {
            pillAccessibility.text = "Accessibility: ON"
            pillAccessibility.setBackgroundColor(Color.parseColor("#064E3B"))
            pillAccessibility.setTextColor(Color.parseColor("#6EE7B7"))
            layoutRestrictedGuide.visibility = View.GONE
        } else {
            pillAccessibility.text = "Accessibility: OFF"
            pillAccessibility.setBackgroundColor(Color.parseColor("#371D1D"))
            pillAccessibility.setTextColor(Color.parseColor("#FCA5A5"))
            layoutRestrictedGuide.visibility = View.VISIBLE
        }

        // 2. Check Overlay
        val isOverlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        if (isOverlayGranted) {
            pillOverlay.text = "Overlay: ON"
            pillOverlay.setBackgroundColor(Color.parseColor("#064E3B"))
            pillOverlay.setTextColor(Color.parseColor("#6EE7B7"))
        } else {
            pillOverlay.text = "Overlay: OFF"
            pillOverlay.setBackgroundColor(Color.parseColor("#371D1D"))
            pillOverlay.setTextColor(Color.parseColor("#FCA5A5"))
        }

        // 3. Check Usage Access (for secondary Watchdog protection)
        val isUsageGranted = isUsageStatsPermissionGranted()
        if (isUsageGranted) {
            pillUsage.text = "Watchdog: ON"
            pillUsage.setBackgroundColor(Color.parseColor("#064E3B"))
            pillUsage.setTextColor(Color.parseColor("#6EE7B7"))
        } else {
            pillUsage.text = "Watchdog: OFF"
            pillUsage.setBackgroundColor(Color.parseColor("#371D1D"))
            pillUsage.setTextColor(Color.parseColor("#FCA5A5"))
        }
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun loadLocalPreferences() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val isMaster = prefs.getBoolean(keyMaster, true)
        swMasterShield.isChecked = isMaster
        updateMasterText(isMaster)

        val savedSet = prefs.getStringSet(keyBlocked, emptySet()) ?: emptySet()
        blockedPackages.clear()
        blockedPackages.addAll(savedSet)
        updateBlockedCounter()
    }

    private fun saveLocalPreferences() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        // CRITICAL FIX: Pass a new HashSet copy so Android SharedPreferences detects the mutation!
        prefs.edit().putStringSet(keyBlocked, HashSet(blockedPackages)).apply()
        updateBlockedCounter()
    }

    private fun updateBlockedCounter() {
        tvBlockedCounter.text = "INSTALLED APPS (${blockedPackages.size} BLOCKED)"
    }

    private fun updateMasterText(active: Boolean) {
        if (active) {
            tvShieldStatus.text = "ACTIVE & BLOCKING"
            tvShieldStatus.setTextColor(Color.parseColor("#34D399"))
        } else {
            tvShieldStatus.text = "PAUSED (ALL ALLOWED)"
            tvShieldStatus.setTextColor(Color.parseColor("#94A3B8"))
        }
    }

    private fun setupMasterToggle() {
        swMasterShield.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(keyMaster, isChecked).apply()
            updateMasterText(isChecked)
            syncMasterToRender(isChecked)
            if (isChecked) {
                AppBlockerWatchdogService.start(this)
            }
        }
    }

    private fun setupSearchAndTabs() {
        etSearchApps.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                currentSearchQuery = s?.toString()?.trim() ?: ""
                renderAppsList()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnTabAll.setOnClickListener {
            currentFilter = FilterMode.ALL
            btnTabAll.setBackgroundColor(Color.parseColor("#312E81"))
            btnTabAll.setTextColor(Color.WHITE)
            btnTabBlocked.setBackgroundColor(Color.parseColor("#1E293B"))
            btnTabBlocked.setTextColor(Color.parseColor("#94A3B8"))
            renderAppsList()
        }

        btnTabBlocked.setOnClickListener {
            currentFilter = FilterMode.BLOCKED_ONLY
            btnTabBlocked.setBackgroundColor(Color.parseColor("#312E81"))
            btnTabBlocked.setTextColor(Color.WHITE)
            btnTabAll.setBackgroundColor(Color.parseColor("#1E293B"))
            btnTabAll.setTextColor(Color.parseColor("#94A3B8"))
            renderAppsList()
        }
    }

    private fun scanInstalledApps() {
        allInstalledApps.clear()
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
            allInstalledApps.add(AppEntry(pkg, appLabel))
        }

        // Sort alphabetically by app name
        allInstalledApps.sortBy { it.appName.lowercase() }
        renderAppsList()
    }

    private fun renderAppsList() {
        layoutAppsContainer.removeAllViews()

        val filtered = allInstalledApps.filter { app ->
            val matchesQuery = currentSearchQuery.isEmpty() ||
                    app.appName.contains(currentSearchQuery, ignoreCase = true) ||
                    app.packageName.contains(currentSearchQuery, ignoreCase = true)

            val matchesFilter = when (currentFilter) {
                FilterMode.ALL -> true
                FilterMode.BLOCKED_ONLY -> blockedPackages.contains(app.packageName)
            }
            matchesQuery && matchesFilter
        }

        if (filtered.isEmpty()) {
            val tvEmpty = TextView(this).apply {
                text = if (currentFilter == FilterMode.BLOCKED_ONLY) "No apps currently blocked." else "No apps match your search."
                setTextColor(Color.parseColor("#64748B"))
                textSize = 13f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 40, 0, 40)
            }
            layoutAppsContainer.addView(tvEmpty)
            return
        }

        for (app in filtered) {
            val isBlocked = blockedPackages.contains(app.packageName)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 18, 20, 18)
                setBackgroundColor(Color.parseColor(if (isBlocked) "#1E1624" else "#111827"))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                params.setMargins(0, 0, 0, 10)
                layoutParams = params
            }

            // Letter Icon Avatar
            val tvAvatar = TextView(this).apply {
                text = app.appName.firstOrNull()?.uppercase() ?: "A"
                setTextColor(Color.parseColor(if (isBlocked) "#F43F5E" else "#818CF8"))
                setBackgroundColor(Color.parseColor(if (isBlocked) "#3B1120" else "#1E253B"))
                textSize = 16f
                gravity = android.view.Gravity.CENTER
                val avatarParams = LinearLayout.LayoutParams(80, 80)
                avatarParams.setMargins(0, 0, 18, 0)
                layoutParams = avatarParams
            }

            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvName = TextView(this).apply {
                text = app.appName
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }

            val tvPkg = TextView(this).apply {
                text = app.packageName
                setTextColor(Color.parseColor("#64748B"))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 2, 0, 0) }
            }

            textLayout.addView(tvName)
            textLayout.addView(tvPkg)

            val sw = Switch(this).apply {
                isChecked = isBlocked
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        blockedPackages.add(app.packageName)
                        card.setBackgroundColor(Color.parseColor("#1E1624"))
                        tvAvatar.setTextColor(Color.parseColor("#F43F5E"))
                        tvAvatar.setBackgroundColor(Color.parseColor("#3B1120"))
                        Toast.makeText(this@MainActivity, "${app.appName} is now BLOCKED!", Toast.LENGTH_SHORT).show()
                    } else {
                        blockedPackages.remove(app.packageName)
                        card.setBackgroundColor(Color.parseColor("#111827"))
                        tvAvatar.setTextColor(Color.parseColor("#818CF8"))
                        tvAvatar.setBackgroundColor(Color.parseColor("#1E253B"))
                        Toast.makeText(this@MainActivity, "${app.appName} Unblocked", Toast.LENGTH_SHORT).show()
                    }
                    saveLocalPreferences()
                    syncAppToggleToRender(app.packageName, app.appName, checked)
                }
            }

            card.addView(tvAvatar)
            card.addView(textLayout)
            card.addView(sw)
            layoutAppsContainer.addView(card)
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
