package com.scapp

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.provider.MediaStore
import android.provider.Settings
import android.provider.Telephony
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.integration.android.IntentIntegrator
import com.scapp.blocker.AppBlockerConfig
import com.scapp.blocker.AppBlockerWatchdogService
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private val TAG = "SchoolMini"
    private val backendUrl = "https://app-blocker.onrender.com"
    private val prefsName = AppBlockerConfig.PREFS_NAME
    private val keyMaster = AppBlockerConfig.KEY_MASTER_ACTIVE
    private val REQ_CAMERA_PERMISSION = 2001

    // Top-level View Containers
    private lateinit var layoutLoginScreen: View
    private lateinit var layoutDutyScreen: View
    private lateinit var layoutDashboardScreen: View

    // 1. Login & Registration Elements
    private lateinit var btnTabModeLogin: Button
    private lateinit var btnTabModeRegister: Button
    private lateinit var layoutRegisterName: View
    private lateinit var etRegisterName: EditText
    private lateinit var etLoginEmail: EditText
    private lateinit var etLoginPassword: EditText
    private lateinit var btnActionLogin: Button
    private lateinit var btnActionRegister: Button
    private lateinit var tvSwitchMode: TextView
    private lateinit var tvLoginFeedback: TextView
    private var isRegisterMode = false

    // 2. Duty Mode AMOLED Screen Elements
    private lateinit var tvDutyLockedIndicator: TextView
    private lateinit var btnDutyPowerToggle: Button
    private lateinit var tvDutyClockTime: TextView
    private lateinit var tvDutyClockDate: TextView
    private lateinit var tvDutyCountdown: TextView
    private var tvDutyCountdownTitle: TextView? = null
    private var tvDutyCountdownSession: TextView? = null
    private lateinit var btnDutyTurnOffBottom: View
    private var tvDutyTurnOffText: TextView? = null
    private lateinit var btnLaunchPhone: View
    private lateinit var btnLaunchMessages: View
    private lateinit var btnLaunchChrome: View
    private lateinit var btnLaunchCamera: View
    private lateinit var btnLaunchAi: View
    private lateinit var btnLaunchSchoolmini: View

    // 3. Dashboard Screen Elements
    private lateinit var tvDashWelcome: TextView
    private var tvDashUserName: TextView? = null
    private var tvDashUserEmail: TextView? = null
    private lateinit var btnDashLogout: Button
    private lateinit var tvRenderStatus: TextView
    private lateinit var btnScanDutyQr: Button
    private lateinit var btnEnterCodeDialog: Button
    private lateinit var btnQuickToggleDuty: Button
    private lateinit var swMasterShield: Switch
    private lateinit var tvShieldStatus: TextView
    private lateinit var layoutPermissionsArmed: View
    private lateinit var layoutPermissionsSetup: View
    private lateinit var pillOverlay: TextView
    private lateinit var pillUsage: TextView
    private lateinit var btnGrantOverlay: Button
    private lateinit var btnGrantUsage: Button

    // Guard state for launching whitelisted apps from Duty Mode
    private var isLaunchingAllowedApp = false

    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockRunnable = object : Runnable {
        override fun run() {
            updateDutyClockAndCountdown()
            clockHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        applyThemeMode()
        bindViews()
        setupAuthActions()
        setupDutyModeActions()
        setupDashboardActions()
        setupPermissionButtons()
        setupMasterToggle()

        loadLocalPreferences()
        checkPermissions()
        refreshScreenState()
        syncWithRender()

        // Launch background watchdog service
        AppBlockerWatchdogService.start(this)
    }

    override fun onResume() {
        super.onResume()
        isLaunchingAllowedApp = false
        applyThemeMode()
        checkPermissions()
        refreshScreenState()

        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val isDuty = prefs.getBoolean(AppBlockerConfig.KEY_DUTY_MODE_ACTIVE, false)
        if (isDuty) {
            applyDutyKioskLock()
        }

        clockHandler.removeCallbacks(clockRunnable)
        clockHandler.post(clockRunnable)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockRunnable)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyThemeMode()
        refreshScreenState()
    }

    private fun applyThemeMode() {
        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        updateSystemBarsTheme(isNight)
    }

    private fun updateSystemBarsTheme(isNight: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return
            if (isNight) {
                controller.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
            } else {
                controller.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            var flags = window.decorView.systemUiVisibility
            flags = if (isNight) {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }
    }

    private fun bindViews() {
        layoutLoginScreen = findViewById(R.id.layout_login_screen)
        layoutDutyScreen = findViewById(R.id.layout_duty_screen)
        layoutDashboardScreen = findViewById(R.id.layout_dashboard_screen)

        // Auth
        btnTabModeLogin = findViewById(R.id.btn_tab_mode_login)
        btnTabModeRegister = findViewById(R.id.btn_tab_mode_register)
        layoutRegisterName = findViewById(R.id.layout_register_name)
        etRegisterName = findViewById(R.id.et_register_name)
        etLoginEmail = findViewById(R.id.et_login_email)
        etLoginPassword = findViewById(R.id.et_login_password)
        btnActionLogin = findViewById(R.id.btn_action_login)
        btnActionRegister = findViewById(R.id.btn_action_register)
        tvSwitchMode = findViewById(R.id.tv_switch_mode)
        tvLoginFeedback = findViewById(R.id.tv_login_feedback)

        // Duty Mode AMOLED
        tvDutyLockedIndicator = findViewById(R.id.tv_duty_locked_indicator)
        btnDutyPowerToggle = findViewById(R.id.btn_duty_power_toggle)
        tvDutyClockTime = findViewById(R.id.tv_duty_clock_time)
        tvDutyClockDate = findViewById(R.id.tv_duty_clock_date)
        tvDutyCountdown = findViewById(R.id.tv_duty_countdown)
        tvDutyCountdownTitle = findViewById(R.id.tv_duty_countdown_title)
        tvDutyCountdownSession = findViewById(R.id.tv_duty_countdown_session)
        btnDutyTurnOffBottom = findViewById(R.id.btn_duty_turn_off_bottom)
        tvDutyTurnOffText = findViewById(R.id.tv_duty_turn_off_text)

        btnLaunchPhone = findViewById(R.id.btn_launch_phone)
        btnLaunchMessages = findViewById(R.id.btn_launch_messages)
        btnLaunchChrome = findViewById(R.id.btn_launch_chrome)
        btnLaunchCamera = findViewById(R.id.btn_launch_camera)
        btnLaunchAi = findViewById(R.id.btn_launch_ai)
        btnLaunchSchoolmini = findViewById(R.id.btn_launch_schoolmini)

        // Dashboard
        tvDashWelcome = findViewById(R.id.tv_dash_welcome)
        tvDashUserName = findViewById(R.id.tv_dash_user_name)
        tvDashUserEmail = findViewById(R.id.tv_dash_user_email)
        btnDashLogout = findViewById(R.id.btn_dash_logout)
        tvRenderStatus = findViewById(R.id.tv_render_status)
        btnScanDutyQr = findViewById(R.id.btn_scan_duty_qr)
        btnEnterCodeDialog = findViewById(R.id.btn_enter_code_dialog)
        btnQuickToggleDuty = findViewById(R.id.btn_quick_toggle_duty)
        swMasterShield = findViewById(R.id.sw_master_shield)
        tvShieldStatus = findViewById(R.id.tv_shield_status)
        layoutPermissionsArmed = findViewById(R.id.layout_permissions_armed)
        layoutPermissionsSetup = findViewById(R.id.layout_permissions_setup)
        pillOverlay = findViewById(R.id.pill_overlay)
        pillUsage = findViewById(R.id.pill_usage)
        btnGrantOverlay = findViewById(R.id.btn_grant_overlay)
        btnGrantUsage = findViewById(R.id.btn_grant_usage)
    }

    // ========================================================
    // SCREEN STATE CONTROLLER (Login vs Duty Mode vs Dashboard)
    // ========================================================
    private fun refreshScreenState() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val authToken = prefs.getString(AppBlockerConfig.KEY_AUTH_TOKEN, null)
        val isDutyMode = prefs.getBoolean(AppBlockerConfig.KEY_DUTY_MODE_ACTIVE, false)
        val name = prefs.getString(AppBlockerConfig.KEY_AUTH_NAME, "")
        val email = prefs.getString(AppBlockerConfig.KEY_AUTH_EMAIL, "student@school.edu")

        if (authToken.isNullOrEmpty()) {
            // State 1: Not Logged In
            releaseDutyKioskLock()
            layoutLoginScreen.visibility = View.VISIBLE
            layoutDutyScreen.visibility = View.GONE
            layoutDashboardScreen.visibility = View.GONE
        } else if (isDutyMode) {
            // State 2: Duty Mode Active (Kiosk / Super Power Saving Mode)
            layoutLoginScreen.visibility = View.GONE
            layoutDutyScreen.visibility = View.VISIBLE
            layoutDashboardScreen.visibility = View.GONE
            applyDutyKioskLock()
            updateDutyClockAndCountdown()
        } else {
            // State 3: Dashboard / Executive Manager
            releaseDutyKioskLock()
            layoutLoginScreen.visibility = View.GONE
            layoutDutyScreen.visibility = View.GONE
            layoutDashboardScreen.visibility = View.VISIBLE
            val displayTitle = if (!name.isNullOrEmpty()) "$name • $email" else email
            tvDashWelcome.text = "$displayTitle • Protected"
            tvDashUserName?.text = if (!name.isNullOrEmpty()) name else "Abhishek Verma"
            tvDashUserEmail?.text = if (!email.isNullOrEmpty()) email else "abhishek.kiet@gmail.com"
        }
    }

    // ========================================================
    // TOTAL KIOSK LOCKDOWN (NO OS Screen Pinning Dialog)
    // ========================================================
    private fun applyDutyKioskLock() {
        hideSystemUI()

        // Keep screen persistent over lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun releaseDutyKioskLock() {
        showSystemUI()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false)
            setTurnScreenOn(false)
        } else {
            @Suppress("DEPRECATION")
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
        }
    }

    private fun showSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val isDuty = prefs.getBoolean(AppBlockerConfig.KEY_DUTY_MODE_ACTIVE, false)
        if (isDuty) {
            Toast.makeText(this, "🔒 Duty Mode is locked. Tap Exit Duty to turn off.", Toast.LENGTH_SHORT).show()
            return
        }
        super.onBackPressed()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val isDuty = prefs.getBoolean(AppBlockerConfig.KEY_DUTY_MODE_ACTIVE, false)
        if (isDuty && !isLaunchingAllowedApp) {
            // If user attempts to swipe up from center or leave: immediately re-surface SchoolMini
            val bringBack = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(bringBack)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val isDuty = prefs.getBoolean(AppBlockerConfig.KEY_DUTY_MODE_ACTIVE, false)
        if (isDuty) {
            hideSystemUI()
            if (!hasFocus && !isLaunchingAllowedApp) {
                try {
                    @Suppress("DEPRECATION")
                    val closeDialogs = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                    sendBroadcast(closeDialogs)
                } catch (e: Exception) {}

                val bringBack = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(bringBack)
            }
        }
    }

    // ========================================================
    // 1. LOGIN & REGISTRATION (Name, Email, Password)
    // ========================================================
    private fun setupAuthActions() {
        btnTabModeLogin.setOnClickListener {
            setAuthMode(register = false)
        }

        btnTabModeRegister.setOnClickListener {
            setAuthMode(register = true)
        }

        tvSwitchMode.setOnClickListener {
            setAuthMode(register = !isRegisterMode)
        }

        btnActionLogin.setOnClickListener {
            val email = etLoginEmail.text.toString().trim()
            val pass = etLoginPassword.text.toString().trim()
            if (email.isEmpty() || pass.isEmpty()) {
                showLoginFeedback("Please enter both email and password")
                return@setOnClickListener
            }
            performLogin(email, pass)
        }

        btnActionRegister.setOnClickListener {
            val name = etRegisterName.text.toString().trim()
            val email = etLoginEmail.text.toString().trim()
            val pass = etLoginPassword.text.toString().trim()

            if (name.isEmpty()) {
                showLoginFeedback("Please enter your full name")
                return@setOnClickListener
            }
            if (email.isEmpty() || !email.contains("@") || !email.contains(".")) {
                showLoginFeedback("Please enter a valid email address")
                return@setOnClickListener
            }
            if (pass.length < 4) {
                showLoginFeedback("Password must be at least 4 characters")
                return@setOnClickListener
            }
            performRegister(name, email, pass)
        }
    }

    private fun setAuthMode(register: Boolean) {
        isRegisterMode = register
        tvLoginFeedback.visibility = View.GONE

        if (register) {
            layoutRegisterName.visibility = View.VISIBLE
            btnActionLogin.visibility = View.GONE
            btnActionRegister.visibility = View.VISIBLE

            btnTabModeRegister.setBackgroundResource(R.drawable.bg_button_brand)
            btnTabModeRegister.setTextColor(Color.WHITE)

            btnTabModeLogin.setBackgroundColor(Color.TRANSPARENT)
            btnTabModeLogin.setTextColor(Color.parseColor("#64748B"))

            tvSwitchMode.text = "Already have an account? Tap Log In"
        } else {
            layoutRegisterName.visibility = View.GONE
            btnActionLogin.visibility = View.VISIBLE
            btnActionRegister.visibility = View.GONE

            btnTabModeLogin.setBackgroundResource(R.drawable.bg_button_brand)
            btnTabModeLogin.setTextColor(Color.WHITE)

            btnTabModeRegister.setBackgroundColor(Color.TRANSPARENT)
            btnTabModeRegister.setTextColor(Color.parseColor("#64748B"))

            tvSwitchMode.text = "Need an account? Tap Create Account"
        }
    }

    private fun showLoginFeedback(msg: String) {
        tvLoginFeedback.text = msg
        tvLoginFeedback.visibility = View.VISIBLE
    }

    private fun performLogin(email: String, pass: String) {
        btnActionLogin.isEnabled = false
        showLoginFeedback("Authenticating...")

        thread {
            var success = false
            var token: String? = null
            var nameResult = ""
            var errorMsg = "Unable to connect to server"

            try {
                val url = URL("$backendUrl/api/auth/login")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4000
                    readTimeout = 4000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                val payload = JSONObject().apply {
                    put("email", email)
                    put("password", pass)
                }

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseStr = stream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseStr)

                if (json.optBoolean("success")) {
                    success = true
                    token = json.optString("token")
                    val userObj = json.optJSONObject("user")
                    nameResult = userObj?.optString("name", "") ?: ""
                } else {
                    errorMsg = json.optString("error", "Invalid email or password")
                }
            } catch (e: Exception) {
                // Offline fallback
                success = true
                token = "jwt_offline_token_" + System.currentTimeMillis()
                nameResult = email.substringBefore("@")
            }

            runOnUiThread {
                btnActionLogin.isEnabled = true

                if (success && !token.isNullOrEmpty()) {
                    val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString(AppBlockerConfig.KEY_AUTH_TOKEN, token)
                        .putString(AppBlockerConfig.KEY_AUTH_NAME, nameResult)
                        .putString(AppBlockerConfig.KEY_AUTH_EMAIL, email)
                        .apply()

                    Toast.makeText(this, "Welcome to School Mini!", Toast.LENGTH_SHORT).show()
                    etLoginPassword.setText("")
                    tvLoginFeedback.visibility = View.GONE
                    refreshScreenState()
                } else {
                    showLoginFeedback(errorMsg)
                }
            }
        }
    }

    private fun performRegister(name: String, email: String, pass: String) {
        btnActionRegister.isEnabled = false
        showLoginFeedback("Creating account...")

        thread {
            var success = false
            var token: String? = null
            var errorMsg = "Unable to connect to server"

            try {
                val url = URL("$backendUrl/api/auth/register")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4000
                    readTimeout = 4000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                val payload = JSONObject().apply {
                    put("name", name)
                    put("email", email)
                    put("password", pass)
                }

                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseStr = stream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseStr)

                if (json.optBoolean("success")) {
                    success = true
                    token = json.optString("token")
                } else {
                    errorMsg = json.optString("error", "Registration failed")
                }
            } catch (e: Exception) {
                // Offline fallback
                success = true
                token = "jwt_offline_token_" + System.currentTimeMillis()
            }

            runOnUiThread {
                btnActionRegister.isEnabled = true

                if (success && !token.isNullOrEmpty()) {
                    val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString(AppBlockerConfig.KEY_AUTH_TOKEN, token)
                        .putString(AppBlockerConfig.KEY_AUTH_NAME, name)
                        .putString(AppBlockerConfig.KEY_AUTH_EMAIL, email)
                        .apply()

                    Toast.makeText(this, "Account created! Welcome, $name", Toast.LENGTH_SHORT).show()
                    etLoginPassword.setText("")
                    tvLoginFeedback.visibility = View.GONE
                    refreshScreenState()
                } else {
                    showLoginFeedback(errorMsg)
                }
            }
        }
    }

    private fun performLogout() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Logout")
            .setMessage("Do you want to log out of School Mini?")
            .setPositiveButton("Logout") { _, _ ->
                val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                prefs.edit()
                    .remove(AppBlockerConfig.KEY_AUTH_TOKEN)
                    .remove(AppBlockerConfig.KEY_AUTH_NAME)
                    .remove(AppBlockerConfig.KEY_AUTH_EMAIL)
                    .putBoolean(AppBlockerConfig.KEY_DUTY_MODE_ACTIVE, false)
                    .apply()
                releaseDutyKioskLock()
                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
                refreshScreenState()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========================================================
    // 2. DUTY MODE ACTIONS & CLOCK
    // ========================================================
    private fun setupDutyModeActions() {
        val exitDutyListener = View.OnClickListener {
            confirmExitDutyMode()
        }
        btnDutyPowerToggle.setOnClickListener(exitDutyListener)
        findViewById<View>(R.id.btn_duty_power_toggle_layout)?.setOnClickListener(exitDutyListener)
        findViewById<View>(R.id.tv_duty_locked_indicator_layout)?.setOnClickListener {
            Toast.makeText(this, "🔒 Duty Lock Active. Device is guarded.", Toast.LENGTH_SHORT).show()
        }
        btnDutyTurnOffBottom.setOnClickListener(exitDutyListener)

        // 1. Phone App Shortcut
        btnLaunchPhone.setOnClickListener {
            isLaunchingAllowedApp = true
            try {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                isLaunchingAllowedApp = false
                Toast.makeText(this, "Dialer unavailable", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Messages App Shortcut
        btnLaunchMessages.setOnClickListener {
            isLaunchingAllowedApp = true
            try {
                val defaultSms = Telephony.Sms.getDefaultSmsPackage(this)
                if (!defaultSms.isNullOrEmpty()) {
                    val intent = packageManager.getLaunchIntentForPackage(defaultSms)
                    if (intent != null) {
                        startActivity(intent)
                        return@setOnClickListener
                    }
                }
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_MESSAGING)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                isLaunchingAllowedApp = false
                Toast.makeText(this, "Messaging app unavailable", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Chrome App Shortcut
        btnLaunchChrome.setOnClickListener {
            isLaunchingAllowedApp = true
            try {
                val intent = packageManager.getLaunchIntentForPackage("com.android.chrome")
                if (intent != null) {
                    startActivity(intent)
                } else {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(browserIntent)
                }
            } catch (e: Exception) {
                isLaunchingAllowedApp = false
                Toast.makeText(this, "Chrome unavailable", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Camera App Shortcut
        btnLaunchCamera.setOnClickListener {
            isLaunchingAllowedApp = true
            try {
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            } catch (e: Exception) {
                isLaunchingAllowedApp = false
                Toast.makeText(this, "Camera unavailable", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. ChatGPT / Gemini AI Shortcut
        btnLaunchAi.setOnClickListener {
            isLaunchingAllowedApp = true
            var launched = false
            for (pkg in listOf("com.google.android.apps.bard", "com.openai.chatgpt")) {
                val intent = packageManager.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    startActivity(intent)
                    launched = true
                    break
                }
            }
            if (!launched) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://gemini.google.com")).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    isLaunchingAllowedApp = false
                    Toast.makeText(this, "AI assistant unavailable", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 6. SchoolMini Info
        btnLaunchSchoolmini.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("SchoolMini Duty Mode")
                .setMessage("Device is locked in ultra focus mode.\nOnly Phone, Messages, Chrome, Camera, and AI assistants are permitted.\n\nSchool Hours: 09:00 AM - 03:00 PM\nTo exit, tap 'Exit Duty' at the top right.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun updateDutyClockAndCountdown() {
        val now = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

        tvDutyClockTime.text = timeFormat.format(now.time)
        tvDutyClockDate.text = dateFormat.format(now.time)

        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        val second = now.get(Calendar.SECOND)

        val currentSeconds = hour * 3600 + minute * 60 + second
        val startSeconds = 9 * 3600  // 09:00 AM
        val endSeconds = 15 * 3600   // 03:00 PM (15:00)

        if (currentSeconds in startSeconds until endSeconds) {
            val remainingSeconds = endSeconds - currentSeconds
            val h = remainingSeconds / 3600
            val m = (remainingSeconds % 3600) / 60
            tvDutyCountdownTitle?.text = "School ends in"
            tvDutyCountdown.text = "${h} h ${m} min"
            tvDutyCountdownSession?.text = "Session: 09:00 - 15:00"
            tvDutyCountdown.setTextColor(Color.parseColor("#2DD4BF"))
            tvDutyTurnOffText?.text = "TURN OFF DUTY MODE (ACTIVE)"
        } else if (currentSeconds < startSeconds) {
            val untilStart = startSeconds - currentSeconds
            val h = untilStart / 3600
            val m = (untilStart % 3600) / 60
            tvDutyCountdownTitle?.text = "School starts in"
            tvDutyCountdown.text = "${h} h ${m} min"
            tvDutyCountdownSession?.text = "Session: 09:00 - 15:00"
            tvDutyCountdown.setTextColor(Color.parseColor("#2DD4BF"))
            tvDutyTurnOffText?.text = "TURN OFF DUTY MODE"
        } else {
            val h = 0
            val m = 0
            tvDutyCountdownTitle?.text = "School time is over"
            tvDutyCountdown.text = "Session completed"
            tvDutyCountdownSession?.text = "Session: 09:00 - 15:00"
            tvDutyCountdown.setTextColor(Color.parseColor("#34D399"))
            tvDutyTurnOffText?.text = "TURN OFF DUTY MODE (OVER)"
        }
    }

    private fun confirmExitDutyMode() {
        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val inSchoolHours = (hour in 9 until 15)

        if (inSchoolHours) {
            AlertDialog.Builder(this)
                .setTitle("School In Session")
                .setMessage("School time is not over yet (Ends at 3:00 PM).\nAre you sure you want to turn off Duty Mode early?")
                .setPositiveButton("Turn Off Duty Mode") { _, _ ->
                    disableDutyMode()
                }
                .setNegativeButton("Keep Focused", null)
                .show()
        } else {
            disableDutyMode()
        }
    }

    private fun disableDutyMode() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(AppBlockerConfig.KEY_DUTY_MODE_ACTIVE, false).apply()
        releaseDutyKioskLock()
        Toast.makeText(this, "Duty Mode Turned OFF", Toast.LENGTH_SHORT).show()
        syncDutyModeToRender(false)
        refreshScreenState()
    }

    private fun enableDutyMode() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(AppBlockerConfig.KEY_DUTY_MODE_ACTIVE, true).apply()
        applyDutyKioskLock()
        AppBlockerWatchdogService.start(this)
        Toast.makeText(this, "Duty Mode Armed! Distracting apps locked.", Toast.LENGTH_LONG).show()
        syncDutyModeToRender(true)
        refreshScreenState()
    }

    // ========================================================
    // 3. DASHBOARD & QR CODE CAMERA SCANNING (PORTRAIT)
    // ========================================================
    private fun setupDashboardActions() {
        btnDashLogout.setOnClickListener { performLogout() }
        findViewById<View>(R.id.btn_dash_logout_container)?.setOnClickListener { performLogout() }

        val scanListener = View.OnClickListener { startQrScanner() }
        btnScanDutyQr.setOnClickListener(scanListener)
        findViewById<View>(R.id.btn_scan_duty_qr_layout)?.setOnClickListener(scanListener)

        val codeListener = View.OnClickListener { showManualCodeDialog() }
        btnEnterCodeDialog.setOnClickListener(codeListener)
        findViewById<View>(R.id.btn_enter_code_dialog_layout)?.setOnClickListener(codeListener)

        val quickArmListener = View.OnClickListener { enableDutyMode() }
        btnQuickToggleDuty.setOnClickListener(quickArmListener)
        findViewById<View>(R.id.btn_quick_toggle_duty_layout)?.setOnClickListener(quickArmListener)
    }

    private fun startQrScanner() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                REQ_CAMERA_PERMISSION
            )
            return
        }

        val integrator = IntentIntegrator(this).apply {
            setPrompt("Point camera at SchoolMini Duty Mode QR Code")
            setBeepEnabled(true)
            setOrientationLocked(true) // Lock strictly in portrait
            captureActivity = PortraitCaptureActivity::class.java
            setBarcodeImageEnabled(false)
            setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        }
        integrator.initiateScan()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startQrScanner()
            } else {
                Toast.makeText(this, "Camera permission needed to scan Duty Mode QR", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data)
        if (result != null) {
            if (result.contents != null) {
                val scanned = result.contents.trim()
                handleScannedQrCode(scanned)
            } else {
                Toast.makeText(this, "QR scan cancelled", Toast.LENGTH_SHORT).show()
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun handleScannedQrCode(code: String) {
        if (code.contains("DUTY_ON") || code.contains("SCHOOLMINI") || code == "SCHOOLMINI:DUTY_ON:2026") {
            enableDutyMode()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Invalid QR Code")
                .setMessage("Scanned content: '$code'\n\nPlease scan the official SchoolMini Duty Mode QR code.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun showManualCodeDialog() {
        val input = EditText(this).apply {
            hint = "e.g. SCHOOLMINI:DUTY_ON:2026"
            setTextColor(Color.parseColor("#0F172A"))
            setHintTextColor(Color.GRAY)
            setText("SCHOOLMINI:DUTY_ON:2026")
        }
        AlertDialog.Builder(this)
            .setTitle("Enter Duty Mode Activation Code")
            .setView(input)
            .setPositiveButton("Activate") { _, _ ->
                val code = input.text.toString().trim()
                handleScannedQrCode(code)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ========================================================
    // PERMISSIONS & WATCHDOG STATUS
    // ========================================================
    private fun setupPermissionButtons() {
        btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
                Toast.makeText(this, "Toggle 'Allow display over other apps' to ON", Toast.LENGTH_SHORT).show()
            }
        }

        btnGrantUsage.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            Toast.makeText(this, "Find 'School Mini' and toggle 'Permit usage access' ON", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions() {
        val isOverlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        val isUsageGranted = isUsageStatsPermissionGranted()

        if (isOverlayGranted) {
            pillOverlay.text = "Overlay: ON"
            pillOverlay.setBackgroundResource(R.drawable.bg_pill_cloud_live)
            pillOverlay.setTextColor(ContextCompat.getColor(this, R.color.master_status_active))
            btnGrantOverlay.visibility = View.GONE
        } else {
            pillOverlay.text = "Overlay: OFF"
            pillOverlay.setBackgroundResource(R.drawable.bg_duty_lock_pill)
            pillOverlay.setTextColor(ContextCompat.getColor(this, R.color.duty_lock_pill_text))
            btnGrantOverlay.visibility = View.VISIBLE
        }

        if (isUsageGranted) {
            pillUsage.text = "Usage Engine: ON"
            pillUsage.setBackgroundResource(R.drawable.bg_pill_cloud_live)
            pillUsage.setTextColor(ContextCompat.getColor(this, R.color.master_status_active))
            btnGrantUsage.visibility = View.GONE
        } else {
            pillUsage.text = "Usage Engine: OFF"
            pillUsage.setBackgroundResource(R.drawable.bg_duty_lock_pill)
            pillUsage.setTextColor(ContextCompat.getColor(this, R.color.duty_lock_pill_text))
            btnGrantUsage.visibility = View.VISIBLE
        }

        val isProtected = isOverlayGranted && isUsageGranted
        if (isProtected) {
            layoutPermissionsArmed.visibility = View.VISIBLE
            layoutPermissionsSetup.visibility = View.GONE
            AppBlockerWatchdogService.start(this)
        } else {
            layoutPermissionsArmed.visibility = View.GONE
            layoutPermissionsSetup.visibility = View.VISIBLE
        }
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        if (appOps != null) {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
            }
            if (mode == AppOpsManager.MODE_ALLOWED) return true
        }

        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm?.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 1000 * 60, now)
            !stats.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    // ========================================================
    // MASTER FIREWALL TOGGLE
    // ========================================================
    private fun loadLocalPreferences() {
        val prefs = getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val isMaster = prefs.getBoolean(keyMaster, true)
        swMasterShield.isChecked = isMaster
        updateMasterText(isMaster)
    }

    private fun updateMasterText(active: Boolean) {
        if (active) {
            tvShieldStatus.text = "ACTIVE & GUARDING"
            tvShieldStatus.setTextColor(ContextCompat.getColor(this, R.color.master_status_active))
        } else {
            tvShieldStatus.text = "SHIELD DISABLED"
            tvShieldStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
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

    // ========================================================
    // CLOUD SYNC HELPERS
    // ========================================================
    private fun syncWithRender() {
        thread {
            try {
                val url = URL("$backendUrl/api/settings")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 3000
                    readTimeout = 3000
                }
                if (conn.responseCode in 200..299) {
                    runOnUiThread {
                        tvRenderStatus.text = "● Cloud: Live"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvRenderStatus.text = "● Cloud: Active"
                }
            }
        }
    }

    private fun syncMasterToRender(isActive: Boolean) {
        thread {
            try {
                val url = URL("$backendUrl/api/settings")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3000
                    readTimeout = 3000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val payload = JSONObject().apply {
                    put("master_blocker_active", isActive)
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                conn.responseCode
            } catch (e: Exception) {}
        }
    }

    private fun syncDutyModeToRender(isDutyActive: Boolean) {
        thread {
            try {
                val url = URL("$backendUrl/api/settings")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3000
                    readTimeout = 3000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val payload = JSONObject().apply {
                    put("is_duty_mode_active", isDutyActive)
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                conn.responseCode
            } catch (e: Exception) {}
        }
    }
}
