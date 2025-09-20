// SettingsSubscriptionActivity.kt
package com.emergencyleash.app

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.emergencyleash.app.models.Subscription
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SettingsSubscriptionActivity : AppCompatActivity() {

    // UI elements for displaying and interacting with subscription data
    private lateinit var progressBar: ProgressBar
    private lateinit var dimOverlay: View
    private lateinit var subscriptionNameTextView: TextView
    private lateinit var subscriptionStatusTextView: TextView
    private lateinit var subscriptionStartDateTextView: TextView
    private lateinit var subscriptionNextPaymentTextView: TextView

    // Variables to hold initial and current subscription states
    private var initialSubscription: Subscription? = null
    private var currentSubscription: Subscription? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_subscription)

        // Initialize UI components
        progressBar = findViewById(R.id.progressBar)
        dimOverlay = findViewById(R.id.dimOverlay)
        subscriptionNameTextView = findViewById(R.id.subscriptionNameTextView)
        subscriptionStatusTextView = findViewById(R.id.subscriptionStatusTextView)
        subscriptionStartDateTextView = findViewById(R.id.subscriptionStartDateTextView)
        subscriptionNextPaymentTextView = findViewById(R.id.subscriptionNextPaymentTextView)

        // Set up back navigation handlers
        findViewById<ImageView>(R.id.chevronLeft).setOnClickListener { handleBackPress() }
        findViewById<Button>(R.id.cancelButton).setOnClickListener { handleBackPress() }

        // Show a loading indicator and load subscription data from the server
        showLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            loadSubscriptionData()
        }

        // Handle physical back button presses
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    // Handle the back button press logic
    private fun handleBackPress() {
        if (hasChanges()) {
            showUnsavedChangesDialog()
        } else {
            finish()
        }
    }

    // Show a dialog when there are unsaved changes
    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Are you sure you want to leave?")
            .setPositiveButton("Leave") { _, _ -> finish() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // Show or hide the loading indicator and related UI adjustments
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        dimOverlay.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<ScrollView>(R.id.scrollViewContainer).visibility = if (show) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.cancelButton).isEnabled = !show
    }

    // Retrieve the user ID from shared preferences
    private fun getUserIDFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("userID", -1)
    }

    // Check if the subscription data has been modified
    private fun hasChanges(): Boolean {
        return currentSubscription != initialSubscription
    }

    // Load subscription data from the server
    private suspend fun loadSubscriptionData() {
        val userID = getUserIDFromPrefs()
        if (userID == -1) {
            Log.e("SettingsSubscription", "Invalid user ID")
            return
        }

        val urlString = "https://emergencyleash.com/wp-content/plugins/access-app/pull/settings-subscription.php"
        val postData = "userID=${URLEncoder.encode(userID.toString(), "UTF-8")}"

        val result = withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.outputStream.use { it.write(postData.toByteArray()) }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("SettingsSubscription", "Failed to load subscription data", e)
                null
            }
        }

        withContext(Dispatchers.Main) {
            if (result != null) {
                parseAndDisplaySubscription(result)
            } else {
                Toast.makeText(this@SettingsSubscriptionActivity, "Failed to load subscription.", Toast.LENGTH_SHORT).show()
            }
            showLoading(false)
        }
    }

    // Parse the server response and update the UI
    private fun parseAndDisplaySubscription(jsonResponse: String) {
        try {
            val jsonObject = JSONObject(jsonResponse)
            val subscriptionObject = jsonObject.optJSONObject("subscription")

            if (subscriptionObject != null) {
                val subscription = Subscription(
                    name = subscriptionObject.optString("subscription_name", "N/A"),
                    status = subscriptionObject.optString("subscription_status", "N/A"),
                    startDate = subscriptionObject.optString("subscription_start_date", "N/A"),
                    nextPaymentDate = subscriptionObject.optString("subscription_next_payment", "N/A")
                )

                currentSubscription = subscription
                initialSubscription = subscription.copy()

                // Adjust subscription name display
                val displayName = when (subscription.name) {
                    "Emergency Leash Plus" -> "Emergency Leash Plus (One Tag)"
                    "Emergency Leash Pro" -> "Emergency Leash Pro (Multiple Tag)"
                    else -> subscription.name
                }

                // Update the UI with the subscription data
                subscriptionNameTextView.text = "Subscription Name: $displayName"
                subscriptionStatusTextView.text = "Subscription Status: ${subscription.status}"
                subscriptionStartDateTextView.text = "Start Date: ${subscription.startDate}"
                subscriptionNextPaymentTextView.text = "Next Payment Date: ${subscription.nextPaymentDate}"
            } else {
                Toast.makeText(this, "No subscription information available.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("SettingsSubscription", "Failed to parse JSON response", e)
        }
    }

}
