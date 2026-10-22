package com.scapp.blocker

object AppBlockerConfig {
    const val PREFS_NAME = "AppBlockerPrefs"
    const val KEY_BLOCKED_PACKAGES = "blocked_packages"
    const val KEY_MASTER_ACTIVE = "master_active"
    const val KEY_REQUIRE_PIN = "require_pin"
    const val KEY_PIN_CODE = "pin_code"

    // JWT Authentication
    const val KEY_AUTH_TOKEN = "auth_jwt_token"
    const val KEY_AUTH_USERNAME = "auth_username"
    const val KEY_AUTH_NAME = "auth_name"
    const val KEY_AUTH_EMAIL = "auth_email"

    // Duty Mode (School Mode)
    const val KEY_DUTY_MODE_ACTIVE = "duty_mode_active"
    const val KEY_SCHOOL_START_HOUR = "school_start_hour" // 9
    const val KEY_SCHOOL_START_MIN = "school_start_min"   // 0
    const val KEY_SCHOOL_END_HOUR = "school_end_hour"     // 15 (3 PM)
    const val KEY_SCHOOL_END_MIN = "school_end_min"       // 0

    // Whitelist packages allowed in Duty Mode
    val DUTY_MODE_ALLOWED_PREFIXES = listOf(
        "com.scapp",                        // SchoolMini App itself
        "com.google.android.dialer",        // Google Phone
        "com.android.dialer",               // Android Phone
        "com.samsung.android.dialer",       // Samsung Phone
        "com.oneplus.dialer",               // OnePlus Phone
        "com.google.android.apps.messaging",// Google Messages
        "com.android.mms",                  // Standard SMS
        "com.samsung.android.messaging",    // Samsung Messages
        "com.android.chrome",               // Google Chrome
        "com.google.android.GoogleCamera",  // Google Camera
        "com.android.camera",               // Standard Camera
        "com.samsung.android.camera",       // Samsung Camera
        "com.oneplus.camera",               // OnePlus Camera
        "com.openai.chatgpt",               // ChatGPT
        "com.google.android.apps.bard"      // Gemini
    )
}
