package com.emergencyleash.app

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SettingsNotificationsActivity : AppCompatActivity() {

    // This is where we declare our UI components.
    private lateinit var pushNotificationsSwitch: SwitchCompat
    private lateinit var emailNotificationsSwitch: SwitchCompat
    private lateinit var smsNotificationsSwitch: SwitchCompat
    private lateinit var progressBar: ProgressBar
    private lateinit var dimOverlay: View

    // This is where we track if any changes have been made.
    private var hasChanges = false

    // These are the initial states of the switches.
    private var initialPushState = false
    private var initialEmailState = false
    private var initialSmsState = false

    // This is where we store whether the user has an allowed subscription.
    private var allowedSubscription: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This is where we set the content view.
        setContentView(R.layout.activity_settings_notifications)

        // This is where we set up back navigation via the chevron.
        val chevronLeft: ImageView = findViewById(R.id.chevronLeft)
        chevronLeft.setOnClickListener {
            // This is where we handle the back press.
            handleBackPress()
        }

        // This is where we set up the cancel button.
        val cancelButton: Button = findViewById(R.id.cancelButton)
        cancelButton.setOnClickListener {
            // This is where we handle the back press.
            handleBackPress()
        }

        // This is where we initialize our SwitchCompat views.
        pushNotificationsSwitch = findViewById(R.id.switchPushNotifications)
        emailNotificationsSwitch = findViewById(R.id.switchEmailNotifications)
        smsNotificationsSwitch = findViewById(R.id.switchSMSNotifications)
        progressBar = findViewById(R.id.progressBar)
        dimOverlay = findViewById(R.id.dimOverlay)

        // This is where we get the subscription name from SharedPreferences.
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val subscriptionName = sharedPreferences.getString("SubscriptionName", "") ?: ""
        // We check to see if the subscription name exists and if it matches one of the allowed values.
        allowedSubscription = subscriptionName == "Emergency Leash Pro" || subscriptionName == "Emergency Leash Plus"


// TEMPORARY: Trigger the test function for "Emergency Leash Pro"
// This line can be removed once testing is complete.
//        testSubscriptionDisplay("")

        // This is where we update the UI based on the allowed subscription.
        updateSubscriptionUI()

        // This is where we show the loading state while fetching data.
        setLoadingState(true)

        // This is where we get the user ID from SharedPreferences.
        val userIdFromPrefs = getUserIDFromPrefs()
        if (userIdFromPrefs != -1) {
            // We check to see if the user ID is valid. If it is, then we pull settings from the server.
            pullSettingsFromServer(userIdFromPrefs)
        } else {
            // If not, then we log an error.
            Log.e("SettingsNotifications", "Invalid user ID")
        }

        // This is where we set up the save button click listener.
        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            // We check to see if the push switch is checked.
            val pushState = pushNotificationsSwitch.isChecked
            // We check to see if the email and SMS switches are checked only if the subscription is allowed.
            val emailState = if (allowedSubscription) emailNotificationsSwitch.isChecked else false
            val smsState = if (allowedSubscription) smsNotificationsSwitch.isChecked else false

            // This is where we check if any changes have been made.
            if (hasChanges) {
                // We send the updated settings to the server.
                sendSettingsToServer(userIdFromPrefs, pushState, emailState, smsState)
                // We reset the change flag.
                hasChanges = false
            }
        }

        // This is where we handle the system back button.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // We check to see if we need to prompt for unsaved changes.
                handleBackPress()
            }
        })
    }

    // This is where we update the UI based on the allowed subscription.
    private fun updateSubscriptionUI() {
        if (allowedSubscription) {
            // We enable email and SMS switches if subscription is allowed.
            emailNotificationsSwitch.isEnabled = true
            smsNotificationsSwitch.isEnabled = true
            // We check to see if the email container exists. If it does then we set its alpha back to normal.
            findViewById<View>(R.id.emailContainer)?.alpha = 1.0f
            // We check to see if the SMS container exists. If it does then we set its alpha back to normal.
            findViewById<View>(R.id.smsContainer)?.alpha = 1.0f
            // We check to see if the email overlay exists. If it does then we hide it.
            findViewById<TextView>(R.id.emailOverlay)?.visibility = View.GONE
            // We check to see if the SMS overlay exists. If it does then we hide it.
            findViewById<TextView>(R.id.smsOverlay)?.visibility = View.GONE
        } else {
            // We disable email and SMS switches if subscription is not allowed.
            emailNotificationsSwitch.isEnabled = false
            smsNotificationsSwitch.isEnabled = false
            // We check to see if the email container exists. If it does then we grey it out.
            findViewById<View>(R.id.emailContainer)?.alpha = 0.5f
            // We check to see if the SMS container exists. If it does then we grey it out.
            findViewById<View>(R.id.smsContainer)?.alpha = 0.5f
            // We check to see if the email overlay exists. If it does then we show it.
            findViewById<TextView>(R.id.emailOverlay)?.visibility = View.VISIBLE
            // We check to see if the SMS overlay exists. If it does then we show it.
            findViewById<TextView>(R.id.smsOverlay)?.visibility = View.VISIBLE
        }
    }

    // This is where we handle the back press.
    private fun handleBackPress() {
        if (hasChanges) {
            // We show a dialog if there are unsaved changes.
            showUnsavedChangesDialog()
        } else {
            // We finish the activity if there are no unsaved changes.
            finish()
        }
    }

    // This is where we show a dialog about unsaved changes.
    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Do you want to leave without saving?")
            .setPositiveButton("Leave") { _, _ ->
                // This is where we finish the activity if the user chooses to leave.
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // This is where we get the user ID from SharedPreferences.
    private fun getUserIDFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        // We check to see if the userID exists. If it does, then we return it; if not, then we return -1.
        return sharedPreferences.getInt("userID", -1)
    }

    // This is where we pull settings from the server.
    private fun pullSettingsFromServer(userID: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // This is where we define the URL for pulling settings.
                val url = URL("https://emergencyleash.com/wp-content/plugins/access-app/pull/settings-notifications.php")
                // This is where we build the POST data.
                val postData = "userID=$userID"

                // This is where we make the POST request.
                val responseMessage = makePostRequest(url, postData)

                // We switch back to the Main thread to update the UI.
                withContext(Dispatchers.Main) {
                    handleServerResponse(responseMessage)
                }
            } catch (e: Exception) {
                // We log an error if the request fails.
                Log.e("SettingsNotifications", "Error fetching settings from server", e)
            }
        }
    }

    // This is where we handle the response from the server.
    private fun handleServerResponse(responseMessage: String) {
        try {
            // We parse the server response as JSON.
            val jsonResponse = JSONObject(responseMessage)
            // This is where we get the push notification state.
            initialPushState = jsonResponse.optString("notifications_push", "0") == "1"
            // This is where we get the email notification state.
            initialEmailState = jsonResponse.optString("notifications_email", "0") == "1"
            // This is where we get the SMS notification state.
            initialSmsState = jsonResponse.optString("notifications_sms", "0") == "1"

            // If the user does not have an allowed subscription, we force email and SMS to false.
            if (!allowedSubscription) {
                initialEmailState = false
                initialSmsState = false
            }

            // This is where we set the initial states for the switches.
            pushNotificationsSwitch.isChecked = initialPushState
            emailNotificationsSwitch.isChecked = initialEmailState
            smsNotificationsSwitch.isChecked = initialSmsState

            // This is where we disable the loading state.
            setLoadingState(false)

            // This is where we set change listeners for the push notifications.
            pushNotificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
                // We check to see if the push state has changed from its initial value.
                hasChanges = isChecked != initialPushState
            }
            // This is where we set change listeners for email notifications only if allowed.
            if (allowedSubscription) {
                emailNotificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
                    // We check to see if the email state has changed from its initial value.
                    hasChanges = isChecked != initialEmailState
                }
                // This is where we set change listeners for SMS notifications only if allowed.
                smsNotificationsSwitch.setOnCheckedChangeListener { _, isChecked ->
                    // We check to see if the SMS state has changed from its initial value.
                    hasChanges = isChecked != initialSmsState
                }
            }
        } catch (e: Exception) {
            // We log an error if the JSON parsing fails.
            Log.e("SettingsNotifications", "Error parsing server response", e)
        }
    }

    // This is where we send settings to the server.
    private fun sendSettingsToServer(userID: Int, pushState: Boolean, emailState: Boolean, smsState: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // This is where we define the URL for pushing settings.
                val url = URL("https://emergencyleash.com/wp-content/plugins/access-app/push/settings-notifications.php")
                // This is where we build the POST data for sending settings.
                val postData = buildPostData(userID, pushState, emailState, smsState)

                // This is where we make the POST request.
                val responseMessage = makePostRequest(url, postData)

                // We switch back to the Main thread to update the UI.
                withContext(Dispatchers.Main) {
                    showResponseDialog(responseMessage)
                }
            } catch (e: Exception) {
                // We log an error if the request fails.
                Log.e("SettingsNotifications", "Error sending settings to server", e)
                withContext(Dispatchers.Main) {
                    showResponseDialog("Error sending settings to server.")
                }
            }
        }
    }

    // This is where we build the POST data string.
    private fun buildPostData(userID: Int, pushState: Boolean, emailState: Boolean, smsState: Boolean): String {
        // We convert boolean states to integer values.
        val pushValue = if (pushState) 1 else 0
        val emailValue = if (emailState) 1 else 0
        val smsValue = if (smsState) 1 else 0
        return "userID=$userID&notifications_push=$pushValue&notifications_email=$emailValue&notifications_sms=$smsValue"
    }

    // This is where we make a POST request.
    private fun makePostRequest(url: URL, postData: String): String {
        return with(url.openConnection() as HttpURLConnection) {
            // This is where we set the request method to POST.
            requestMethod = "POST"
            // This is where we set the Content-Type header.
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            // This is where we allow output.
            doOutput = true
            // This is where we write the POST data.
            outputStream.use { it.write(postData.toByteArray()) }

            // We check to see if the response code is HTTP OK.
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // This is where we read and return the response text.
                inputStream.bufferedReader().use { it.readText() }
            } else {
                // If not HTTP OK, then we throw an exception.
                throw Exception("Failed to make POST request. Response code: $responseCode")
            }
        }
    }

    // This is where we toggle the loading state.
    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            // This is where we show the progress bar and dim overlay.
            progressBar.visibility = View.VISIBLE
            dimOverlay.visibility = View.VISIBLE
            dimOverlay.isClickable = true
            // This is where we disable the switches during loading.
            pushNotificationsSwitch.isEnabled = false
            emailNotificationsSwitch.isEnabled = false
            smsNotificationsSwitch.isEnabled = false
        } else {
            // This is where we hide the progress bar and dim overlay.
            progressBar.visibility = View.GONE
            dimOverlay.visibility = View.GONE
            dimOverlay.isClickable = false
            // This is where we re-enable the push switch.
            pushNotificationsSwitch.isEnabled = true
            // This is where we re-enable email and SMS switches only if allowed.
            if (allowedSubscription) {
                emailNotificationsSwitch.isEnabled = true
                smsNotificationsSwitch.isEnabled = true
            }
        }
    }

    // This is where we show a dialog with the server response.
    private fun showResponseDialog(responseMessage: String) {
        val message = try {
            // This is where we parse the response as JSON.
            val jsonResponse = JSONObject(responseMessage)
            // This is where we get the message from the JSON response.
            jsonResponse.optString("message", "An error occurred.")
        } catch (e: Exception) {
            // If parsing fails, then we set a default error message.
            "An error occurred."
        }

        // This is where we build and show the response dialog.
        AlertDialog.Builder(this)
            .setTitle("Server Response")
            .setMessage(message)
            .setPositiveButton("Okay") { dialog, _ ->
                // This is where we dismiss the dialog and finish the activity.
                dialog.dismiss()
                finish() // Exit the activity after saving.
            }
            .setCancelable(false)
            .show()
    }

    // This is a test function to update the display for different subscription levels.
    // You can call this function with a subscription string (e.g., "Emergency Leash Pro",
    // "Emergency Leash Plus", or any other value) to test how the UI displays for that level.
    fun testSubscriptionDisplay(subscription: String) {
        // We check to see if the subscription matches one of the allowed values.
        allowedSubscription = subscription == "Emergency Leash Pro" || subscription == "Emergency Leash Plus"
        // This is where we update the UI based on the new subscription level.
        updateSubscriptionUI()
    }
}
