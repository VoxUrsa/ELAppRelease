package com.emergencyleash.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class SettingsAccessibilityActivity : AppCompatActivity() {

    private lateinit var darkModeSwitch: SwitchCompat
    private lateinit var progressBar: ProgressBar
    private lateinit var dimOverlay: View
    private var hasChanges = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply dark mode before setting content view (only at startup)
        DarkModeHelper.applyDarkMode(DarkModeHelper.isDarkModeEnabled(this))

        setContentView(R.layout.activity_settings_accessibility)

        // Initialize UI elements
        initUI()

        // Set up the OnBackPressedCallback for handling back button events
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun initUI() {
        // Handle back navigation
        val chevronLeft: ImageView = findViewById(R.id.chevronLeft)
        chevronLeft.setOnClickListener {
            handleBackPress()
        }

        val cancelButton: Button = findViewById(R.id.cancelButton)
        cancelButton.setOnClickListener {
            handleBackPress()
        }

        // Initialize the Dark Mode SwitchCompat and set change listener
        darkModeSwitch = findViewById(R.id.SwitchAccessibilityDarkMode)
        darkModeSwitch.isChecked = DarkModeHelper.isDarkModeEnabled(this)
        darkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            hasChanges = true
            // Don't apply dark mode immediately; apply when the user saves settings
        }

        // Initialize ProgressBar and dimOverlay for loading state
        progressBar = findViewById(R.id.progressBar)
        dimOverlay = findViewById(R.id.dimOverlay)

        // Handle Save button click
        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            saveDarkModeSetting()
        }
    }

    private fun saveDarkModeSetting() {
        // Show loading state
        setLoadingState(true)

        val darkModeState = darkModeSwitch.isChecked
        DarkModeHelper.setDarkModeEnabled(this, darkModeState)

        // Simulate saving and apply dark mode (this may restart the activity)
        DarkModeHelper.applyDarkMode(darkModeState)

        // Simulate a delay (like network operation) using a postDelayed handler
        progressBar.postDelayed({
            // After saving, hide loading state
            setLoadingState(false)

            hasChanges = false
            finish() // End the activity after saving
        }, 1000) // Simulated delay of 1 second
    }

    private fun handleBackPress() {
        if (hasChanges) {
            showUnsavedChangesDialog()
        } else {
            finish() // Finish the activity to handle back press
        }
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Do you want to leave without saving?")
            .setPositiveButton("Leave") { _, _ ->
                finish() // Finish the activity to handle back press
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            progressBar.visibility = View.VISIBLE
            dimOverlay.visibility = View.VISIBLE
            // Optionally, disable user interaction while loading
            darkModeSwitch.isEnabled = false
            findViewById<Button>(R.id.saveButton).isEnabled = false
            findViewById<Button>(R.id.cancelButton).isEnabled = false
        } else {
            progressBar.visibility = View.GONE
            dimOverlay.visibility = View.GONE
            // Re-enable user interaction after loading
            darkModeSwitch.isEnabled = true
            findViewById<Button>(R.id.saveButton).isEnabled = true
            findViewById<Button>(R.id.cancelButton).isEnabled = true
        }
    }
}
