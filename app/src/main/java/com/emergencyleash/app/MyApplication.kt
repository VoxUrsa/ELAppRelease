package com.emergencyleash.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 1) Read user preference for dark mode from SharedPreferences (or your existing helper)
        val darkModeEnabled = DarkModeHelper.isDarkModeEnabled(this)

        // 2) Apply day/night mode *before* any Activity is launched
        AppCompatDelegate.setDefaultNightMode(
            if (darkModeEnabled) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
        )
    }
}
