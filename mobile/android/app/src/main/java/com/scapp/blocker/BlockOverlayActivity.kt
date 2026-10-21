package com.scapp.blocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class BlockOverlayActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.scapp.R.layout.activity_block_overlay)

        val blockedPackage = intent.getStringExtra("BLOCKED_PACKAGE") ?: "com.instagram.android"

        // Resolve friendly application label (e.g. 'Instagram')
        val pm = applicationContext.packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(blockedPackage, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            blockedPackage.split('.').lastOrNull()?.replaceFirstChar { it.uppercase() } ?: "App"
        }

        val tvTitle = findViewById<TextView>(com.scapp.R.id.tv_blocked_title)
        val tvMessage = findViewById<TextView>(com.scapp.R.id.tv_blocked_message)
        val btnReturnHome = findViewById<Button>(com.scapp.R.id.btn_return_home)
        val layoutPin = findViewById<LinearLayout>(com.scapp.R.id.layout_pin_entry)
        val etPin = findViewById<EditText>(com.scapp.R.id.et_pin_input)
        val btnUnlockPin = findViewById<Button>(com.scapp.R.id.btn_unlock_pin)

        tvTitle.text = "$appName is Blocked"
        tvMessage.text = "$appName: No, you cannot open it."

        // Read PIN requirements from SharedPreferences
        val prefs = getSharedPreferences(AppBlockerAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
        val requirePin = prefs.getBoolean(AppBlockerAccessibilityService.KEY_REQUIRE_PIN, false)
        val storedPin = prefs.getString(AppBlockerAccessibilityService.KEY_PIN_CODE, null)

        if (requirePin && !storedPin.isNullOrEmpty()) {
            layoutPin.visibility = View.VISIBLE
            btnUnlockPin.setOnClickListener {
                val inputPin = etPin.text.toString().trim()
                if (inputPin == storedPin) {
                    Toast.makeText(this, "$appName temporarily unlocked", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                    etPin.setText("")
                }
            }
        } else {
            layoutPin.visibility = View.GONE
        }

        // Return to Android home screen
        btnReturnHome.setOnClickListener {
            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(homeIntent)
            finish()
        }
    }

    override fun onBackPressed() {
        // Prevent back button from slipping through to the blocked application
        val homeIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(homeIntent)
        finish()
    }
}
