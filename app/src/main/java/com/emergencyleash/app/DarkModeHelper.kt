package com.emergencyleash.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

object DarkModeHelper {

    private const val PREFS_NAME = "prefs"
    private const val DARK_MODE_KEY = "dark_mode"

    // Get dark mode state from shared preferences
    fun isDarkModeEnabled(context: Context): Boolean {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(DARK_MODE_KEY, false)
    }

    // Set dark mode state in shared preferences and apply it
    fun setDarkModeEnabled(context: Context, isEnabled: Boolean) {
        val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean(DARK_MODE_KEY, isEnabled).apply()

        // Apply the theme
        applyDarkMode(isEnabled)
    }

    // Apply dark mode based on the preference
    fun applyDarkMode(isEnabled: Boolean) {
        val mode = if (isEnabled) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}
