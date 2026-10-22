package com.scapp

import android.app.Activity
import android.app.AlertDialog
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
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
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
    private lateinit var tvStatBlocked: TextView
    private lateinit var tvStatEngine: TextView
    private lateinit var layoutAppsContainer: LinearLayout
    private lateinit var etSearchApps: EditText
    private lateinit var btnTabAll: Button
    private lateinit var btnTabBlocked: Button

    private lateinit var layoutPermissionsArmed: View
    private lateinit var layoutPermissionsSetup: View
    private lateinit var btnTogglePermissionsDetails: Button
    private lateinit var pillAccessibility: TextView
    private lateinit var pillOverlay: TextView
    private lateinit var pillUsage: TextView
    private lateinit var layoutRestrictedGuide: View
    private lateinit var btnGrantAccessibility: Button
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantUsage: Button
    private lateinit var btnGrantDnd: Button

    private lateinit var tvDutyModeStatus: TextView
    private lateinit var btnToggleDutyMode: Button

    private lateinit var layoutEmptyState: View
    private lateinit var tvEmptyTitle: TextView
    private lateinit var tvEmptyDesc: TextView

    private val blockedPackages = HashSet<String>()
    private val allInstalledApps = ArrayList<AppEntry>()
    private var currentFilter = FilterMode.ALL
    private var currentSearchQuery = ""
    private var isPermissionsDetailsExpanded = false

    enum class FilterMode { ALL, BLOCKED_ONLY }

    data class AppEntry(val packageName: String, val appName: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swMasterShield = findViewById(R.id.sw_master_shield)
        tvShieldStatus = findViewById(R.id.tv_shield_status)
        tvRenderStatus = findViewById(R.id.tv_render_status)
        tvBlockedCounter = findViewById(R.id.tv_blocked_counter)
        tvStatBlocked = findViewById(R.id.tv_stat_blocked)
        tvStatEngine = findViewById(R.id.tv_stat_engine)
        layoutAppsContainer = findViewById(R.id.layout_apps_container)
        etSearchApps = findViewById(R.id.et_search_apps)
        btnTabAll = findViewById(R.id.btn_tab_all)
        btnTabBlocked = findViewById(R.id.btn_tab_blocked)

        layoutPermissionsArmed = findViewById(R.id.layout_permissions_armed)
        layoutPermissionsSetup = findViewById(R.id.layout_permissions_setup)
        btnTogglePermissionsDetails = findViewById(R.id.btn_toggle_permissions_details)
        pillAccessibility = findViewById(R.id.pill_accessibility)
        pillOverlay = findViewById(R.id.pill_overlay)
        pillUsage = findViewById(R.id.pill_usage)
        layoutRestrictedGuide = findViewById(R.id.layout_restricted_setting_guide)
        btnGrantAccessibility = findViewById(R.id.btn_grant_accessibility)
        btnGrantOverlay = findViewById(R.id.btn_grant_overlay)
        btnGrantUsage = findViewById(R.id.btn_grant_usage)
        btnGrantDnd = findViewById(R.id.btn_grant_dnd)

        tvDutyModeStatus = findViewById(R.id.tv_duty_mode_status)
        btnToggleDutyMode = findViewById(R.id.btn_toggle_duty_mode)

        layoutEmptyState = findViewById(R.id.layout_empty_state)
        tvEmptyTitle = findViewById(R.id.tv_empty_title)
        tvEmptyDesc = findViewById(R.id.tv_empty_desc)

        findViewById<Button>(R.id.btn_empty_browse_all)?.setOnClickListener {
            currentFilter = FilterMode.ALL
            btnTabAll.setBackgroundResource(R.drawable.bg_button_primary)
            btnTabAll.setTextColor(Color.WHITE)
            btnTabBlocked.setBackgroundColor(Color.parseColor("#1E293B"))
            btnTabBlocked.setTextColor(Color.parseColor("#94A3B8"))
            renderAppsList()
        }

        setupPermissionButtons()
        setupMasterToggle()
        setupDutyMode()
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
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val isDuty = prefs.getBoolean(AppBlockerAccessibilityService.KEY_DUTY_MODE_ACTIVE, false)
        updateDutyModeUi(isDuty)
    }

    private fun setupDutyMode() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        var isDuty = prefs.getBoolean(AppBlockerAccessibilityService.KEY_DUTY_MODE_ACTIVE, false)
        updateDutyModeUi(isDuty)

        btnToggleDutyMode.setOnClickListener {
            isDuty = !isDuty
            prefs.edit().putBoolean(AppBlockerAccessibilityService.KEY_DUTY_MODE_ACTIVE, isDuty).apply()
            updateDutyModeUi(isDuty)

            val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notifManager != null) {
                if (notifManager.isNotificationPolicyAccessGranted) {
                    if (isDuty) {
                        notifManager.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        Toast.makeText(this, "🧑‍🏫 Duty Mode Active: Calls & messages silenced", Toast.LENGTH_SHORT).show()
                    } else {
                        notifManager.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                        Toast.makeText(this, "Duty Mode Off: Normal sound restored", Toast.LENGTH_SHORT).show()
                    }
                } else if (isDuty) {
                    Toast.makeText(this, "Tip: Grant DND permission to silence incoming calls during class", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }
        }
    }

    private fun updateDutyModeUi(active: Boolean) {
        if (active) {
            tvDutyModeStatus.text = "ACTIVE • Class in Session"
            tvDutyModeStatus.setTextColor(Color.parseColor("#34D399"))
            btnToggleDutyMode.text = "⏹ End Teacher Duty Mode"
            btnToggleDutyMode.setBackgroundColor(Color.parseColor("#DC2626"))
        } else {
            tvDutyModeStatus.text = "Standby • Ready for Class"
            tvDutyModeStatus.setTextColor(Color.parseColor("#94A3B8"))
            btnToggleDutyMode.text = "🎯 Start Teacher Duty Mode"
            btnToggleDutyMode.setBackgroundResource(R.drawable.bg_button_primary)
        }
    }

    private fun setupPermissionButtons() {
        btnTogglePermissionsDetails.setOnClickListener {
            isPermissionsDetailsExpanded = !isPermissionsDetailsExpanded
            layoutPermissionsSetup.visibility = if (isPermissionsDetailsExpanded) View.VISIBLE else View.GONE
            btnTogglePermissionsDetails.text = if (isPermissionsDetailsExpanded) "Hide ▴" else "Status ▾"
        }

        findViewById<Button>(R.id.btn_open_app_info).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Unlock Restricted Setting (MIUI / Android 13+)")
                .setMessage("Android locks Accessibility for sideloaded APKs.\n\nTo unlock:\n1. Tap 'OPEN SETTINGS' below\n2. On the App Info page, tap the 3 DOTS (⋮) at the top-right corner\n3. Tap 'Allow restricted settings'\n4. Confirm your screen lock / fingerprint\n5. Return here & tap 'Accessibility'!")
                .setPositiveButton("OPEN SETTINGS") { _, _ ->
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        btnGrantAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Find 'FocusShield' under Downloaded apps and toggle it ON", Toast.LENGTH_SHORT).show()
        }

        btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                Toast.makeText(this, "Toggle 'Allow display over other apps' to ON", Toast.LENGTH_SHORT).show()
            }
        }

        btnGrantUsage.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            Toast.makeText(this, "Find 'FocusShield' and toggle 'Permit usage access' ON", Toast.LENGTH_SHORT).show()
        }

        btnGrantDnd.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                Toast.makeText(this, "Find 'FocusShield' and allow Do Not Disturb access", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissions() {
        // 1. Check Accessibility
        val expectedService = "$packageName/${AppBlockerAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
        val isAccessGranted = enabledServices.contains(expectedService) || AppBlockerAccessibilityService.isServiceRunning

        // 2. Check Overlay
        val isOverlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        // 3. Check Usage Access
        val isUsageGranted = isUsageStatsPermissionGranted()

        // Update Pill Texts & Colors
        if (isAccessGranted) {
            pillAccessibility.text = "Accessibility: ON"
            pillAccessibility.setBackgroundColor(Color.parseColor("#064E3B"))
            pillAccessibility.setTextColor(Color.parseColor("#6EE7B7"))
        } else {
            pillAccessibility.text = "Accessibility: OFF"
            pillAccessibility.setBackgroundColor(Color.parseColor("#371D1D"))
            pillAccessibility.setTextColor(Color.parseColor("#FCA5A5"))
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

        if (isUsageGranted) {
            pillUsage.text = "Watchdog: ON"
            pillUsage.setBackgroundColor(Color.parseColor("#064E3B"))
            pillUsage.setTextColor(Color.parseColor("#6EE7B7"))
        } else {
            pillUsage.text = "Watchdog: OFF"
            pillUsage.setBackgroundColor(Color.parseColor("#371D1D"))
            pillUsage.setTextColor(Color.parseColor("#FCA5A5"))
        }

        // SMART HIDING: Hide buttons for permissions already granted!
        // When Overlay is ON -> NO SHOW!
        btnGrantOverlay.visibility = if (isOverlayGranted) View.GONE else View.VISIBLE

        // When Accessibility is ON -> NO SHOW!
        btnGrantAccessibility.visibility = if (isAccessGranted) View.GONE else View.VISIBLE
        layoutRestrictedGuide.visibility = if (isAccessGranted) View.GONE else View.VISIBLE

        // When Usage is ON -> NO SHOW!
        btnGrantUsage.visibility = if (isUsageGranted) View.GONE else View.VISIBLE

        // When DND is ON -> NO SHOW!
        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        val isDndGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && notifManager != null) {
            notifManager.isNotificationPolicyAccessGranted
        } else {
            true
        }
        btnGrantDnd.visibility = if (isDndGranted) View.GONE else View.VISIBLE

        // Determine protection readiness:
        // System is armed if Overlay is granted AND at least one detection engine is operational
        val isProtected = isOverlayGranted && (isAccessGranted || isUsageGranted)

        if (isProtected) {
            layoutPermissionsArmed.visibility = View.VISIBLE
            // When all needed permissions are done, hide the setup card completely!
            if (!isPermissionsDetailsExpanded) {
                layoutPermissionsSetup.visibility = View.GONE
            }
            tvStatEngine.text = if (isAccessGranted && isUsageGranted) {
                "Dual Engine Active"
            } else if (isAccessGranted) {
                "Accessibility Active"
            } else {
                "Watchdog Active"
            }
            tvStatEngine.setTextColor(Color.parseColor("#34D399"))
        } else {
            layoutPermissionsArmed.visibility = View.GONE
            layoutPermissionsSetup.visibility = View.VISIBLE
            tvStatEngine.text = "Setup Needed"
            tvStatEngine.setTextColor(Color.parseColor("#F59E0B"))
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
        // Pass a new HashSet copy so Android SharedPreferences detects the mutation!
        prefs.edit().putStringSet(keyBlocked, HashSet(blockedPackages)).apply()
        updateBlockedCounter()
    }

    private fun updateBlockedCounter() {
        val count = blockedPackages.size
        tvBlockedCounter.text = "INSTALLED APPS ($count BLOCKED)"
        tvStatBlocked.text = if (count == 1) "1 App Restricted" else "$count Apps Restricted"
        updateTabLabels()
    }

    private fun updateTabLabels() {
        btnTabAll.text = "All Apps (${allInstalledApps.size})"
        btnTabBlocked.text = "Blocked (${blockedPackages.size})"
    }

    private fun updateMasterText(active: Boolean) {
        if (active) {
            tvShieldStatus.text = "ACTIVE & BLOCKING"
            tvShieldStatus.setTextColor(Color.parseColor("#34D399"))
        } else {
            tvShieldStatus.text = "SHIELD DISABLED"
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
            btnTabAll.setBackgroundResource(R.drawable.bg_button_primary)
            btnTabAll.setTextColor(Color.WHITE)
            btnTabBlocked.setBackgroundColor(Color.parseColor("#1E293B"))
            btnTabBlocked.setTextColor(Color.parseColor("#94A3B8"))
            renderAppsList()
        }

        btnTabBlocked.setOnClickListener {
            currentFilter = FilterMode.BLOCKED_ONLY
            btnTabBlocked.setBackgroundResource(R.drawable.bg_button_primary)
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
        val addedPackages = HashSet<String>()

        for (resolveInfo in resolvedActivities) {
            val appInfo = resolveInfo.activityInfo.applicationInfo
            val pkg = appInfo.packageName
            if (pkg == packageName) continue

            val appLabel = pm.getApplicationLabel(appInfo).toString()
            allInstalledApps.add(AppEntry(pkg, appLabel))
            addedPackages.add(pkg)
        }

        // Check any package in blockedPackages:
        // If it's installed on the device (even if not in launcher intent), resolve it and ensure it's in list.
        // If it's completely uninstalled / nonexistent (e.g. legacy ghost Instagram from v1), purge it!
        val ghostPackages = ArrayList<String>()
        for (pkg in blockedPackages) {
            if (!addedPackages.contains(pkg)) {
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val label = pm.getApplicationLabel(appInfo).toString()
                    allInstalledApps.add(AppEntry(pkg, label))
                    addedPackages.add(pkg)
                } catch (e: PackageManager.NameNotFoundException) {
                    // Not installed on phone -> purge from blocked set
                    ghostPackages.add(pkg)
                }
            }
        }

        if (ghostPackages.isNotEmpty()) {
            blockedPackages.removeAll(ghostPackages.toSet())
            saveLocalPreferences()
        }

        allInstalledApps.sortBy { it.appName.lowercase() }
        updateBlockedCounter()
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
            layoutEmptyState.visibility = View.VISIBLE
            if (currentFilter == FilterMode.BLOCKED_ONLY) {
                tvEmptyTitle.text = "No Apps Blocked Yet"
                tvEmptyDesc.text = "Search or browse below and toggle any app to restrict distractions."
            } else {
                tvEmptyTitle.text = "No Matching Apps"
                tvEmptyDesc.text = "No apps found matching '$currentSearchQuery'"
            }
            return
        }

        layoutEmptyState.visibility = View.GONE

        for (app in filtered) {
            val isBlocked = blockedPackages.contains(app.packageName)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(20, 18, 20, 18)
                background = ContextCompat.getDrawable(
                    this@MainActivity,
                    if (isBlocked) R.drawable.bg_app_card_blocked else R.drawable.bg_app_card_normal
                )
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
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                val avatarParams = LinearLayout.LayoutParams(80, 80)
                avatarParams.setMargins(0, 0, 18, 0)
                layoutParams = avatarParams
            }

            val textLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            // Title Row with optional "BLOCKED" pill badge
            val titleRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val tvName = TextView(this).apply {
                text = app.appName
                setTextColor(Color.WHITE)
                textSize = 15f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            titleRow.addView(tvName)

            val tvBadge = TextView(this).apply {
                text = "BLOCKED"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 9f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                setBackgroundResource(R.drawable.bg_pill_blocked)
                setPadding(12, 4, 12, 4)
                visibility = if (isBlocked) View.VISIBLE else View.GONE
                val badgeParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(12, 0, 0, 0) }
                layoutParams = badgeParams
            }
            titleRow.addView(tvBadge)

            val tvPkg = TextView(this).apply {
                text = app.packageName
                setTextColor(Color.parseColor("#64748B"))
                textSize = 11f
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 2, 0, 0) }
            }

            textLayout.addView(titleRow)
            textLayout.addView(tvPkg)

            val sw = Switch(this).apply {
                isChecked = isBlocked
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        blockedPackages.add(app.packageName)
                        card.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_app_card_blocked)
                        tvAvatar.setTextColor(Color.parseColor("#F43F5E"))
                        tvAvatar.setBackgroundColor(Color.parseColor("#3B1120"))
                        tvBadge.visibility = View.VISIBLE
                        Toast.makeText(this@MainActivity, "${app.appName} is now BLOCKED!", Toast.LENGTH_SHORT).show()

                        // Ensure Watchdog is active
                        AppBlockerWatchdogService.start(this@MainActivity)

                        val hasAccessibility = AppBlockerAccessibilityService.isServiceRunning
                        val hasOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this@MainActivity) else true
                        val hasUsage = isUsageStatsPermissionGranted()

                        if (!hasAccessibility && (!hasOverlay || !hasUsage)) {
                            Toast.makeText(
                                this@MainActivity,
                                "⚠️ Tip: Enable 'Overlay' & 'Usage Access' above to enforce blocking immediately!",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        blockedPackages.remove(app.packageName)
                        card.background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_app_card_normal)
                        tvAvatar.setTextColor(Color.parseColor("#818CF8"))
                        tvAvatar.setBackgroundColor(Color.parseColor("#1E253B"))
                        tvBadge.visibility = View.GONE
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
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.requestMethod = "GET"
                val code = conn.responseCode
                runOnUiThread {
                    if (code == 200) {
                        tvRenderStatus.text = "● Cloud: Live"
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
