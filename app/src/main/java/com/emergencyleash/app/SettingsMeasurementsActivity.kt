package com.emergencyleash.app

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class SettingsMeasurementsActivity : AppCompatActivity() {

    private lateinit var imperialContainer: LinearLayout
    private lateinit var metricContainer: LinearLayout
    private lateinit var sliderThumb: View
    private lateinit var progressBar: ProgressBar
    private lateinit var dimOverlay: View

    private var hasChanges = false
    private var initialMeasurement: String? = null // Track the initial state (0 = Imperial, 1 = Metric)
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply dark mode before setting content view
        DarkModeHelper.applyDarkMode(DarkModeHelper.isDarkModeEnabled(this))

        setContentView(R.layout.activity_settings_measurements)

        // Initialize the slider and other elements
        imperialContainer = findViewById(R.id.imperialContainer)
        metricContainer = findViewById(R.id.metricContainer)
        sliderThumb = findViewById(R.id.sliderThumb)
        progressBar = findViewById(R.id.progressBar)
        dimOverlay = findViewById(R.id.dimOverlay)

        // Set the initial state of the slider
        imperialContainer.setOnClickListener {
            setMeasurementSelection(false)
        }
        metricContainer.setOnClickListener {
            setMeasurementSelection(true)
        }

        // Handle back navigation
        val chevronLeft: ImageView = findViewById(R.id.chevronLeft)
        chevronLeft.setOnClickListener {
            handleBackPress()
        }

        val cancelButton: Button = findViewById(R.id.cancelButton)
        cancelButton.setOnClickListener {
            handleBackPress()
        }

        // Handle Save button click
        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            val measurement = if (metricContainer.isSelected) "1" else "0" // 0 for Imperial, 1 for Metric
            sendMeasurementsToServer(measurement)
            hasChanges = false
        }

        // Fetch initial values from server
        val userIdFromPrefs = getUserIDFromPrefs()
        pullMeasurementsFromServer(userIdFromPrefs)
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

    private fun getUserIDFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", MODE_PRIVATE)
        return sharedPreferences.getInt("userID", -1)
    }

    private fun pullMeasurementsFromServer(userID: Int) {
        setLoadingState(true) // Show loading state

        val url = "https://emergencyleash.com/wp-content/plugins/access-app/pull/settings-measurements.php"
        val requestBody = FormBody.Builder()
            .add("userID", userID.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setLoadingState(false) // Hide loading state
                    Toast.makeText(this@SettingsMeasurementsActivity, "Error fetching measurements", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    val jsonResponse = JSONObject(responseBody)
                    val result = jsonResponse.getInt("result")

                    if (result == 1) {
                        val measurement = jsonResponse.getString("measurement")
                        runOnUiThread {
                            initialMeasurement = measurement // Store initial measurement
                            setMeasurementSelection(measurement == "1")
                            setLoadingState(false) // Hide loading state
                        }
                    } else {
                        runOnUiThread {
                            setLoadingState(false) // Hide loading state
                            Toast.makeText(this@SettingsMeasurementsActivity, jsonResponse.getString("message"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }

    private fun sendMeasurementsToServer(measurement: String) {
        setLoadingState(true) // Show loading state

        val userID = getUserIDFromPrefs()
        val url = "https://emergencyleash.com/wp-content/plugins/access-app/push/settings-measurements.php"

        val requestBody = FormBody.Builder()
            .add("userID", userID.toString())
            .add("measurement", measurement)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setLoadingState(false) // Hide loading state
                    Toast.makeText(this@SettingsMeasurementsActivity, "Error saving measurements", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    val jsonResponse = JSONObject(responseBody)
                    val message = jsonResponse.getString("message")

                    runOnUiThread {
                        setLoadingState(false) // Hide loading state
                        Toast.makeText(this@SettingsMeasurementsActivity, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun setMeasurementSelection(isMetric: Boolean) {
        val parentWidth = findViewById<FrameLayout>(R.id.sliderContainer).width
        val thumbWidth = sliderThumb.width
        val targetX = if (isMetric) parentWidth - thumbWidth else 0

        sliderThumb.animate().x(targetX.toFloat()).setDuration(300).start()

        metricContainer.isSelected = isMetric
        imperialContainer.isSelected = !isMetric

        hasChanges = (initialMeasurement == "0" && isMetric) || (initialMeasurement == "1" && !isMetric)
    }

    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            progressBar.visibility = View.VISIBLE
            dimOverlay.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.GONE
            dimOverlay.visibility = View.GONE
        }

        // Ensure enabling/disabling interaction works properly
        setInteractionEnabled(!isLoading)
    }

    private fun setInteractionEnabled(isEnabled: Boolean) {
        imperialContainer.isEnabled = isEnabled
        metricContainer.isEnabled = isEnabled
        findViewById<Button>(R.id.saveButton).isEnabled = isEnabled
        findViewById<Button>(R.id.cancelButton).isEnabled = isEnabled
    }
}
