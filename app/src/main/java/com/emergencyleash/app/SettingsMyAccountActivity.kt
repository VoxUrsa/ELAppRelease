package com.emergencyleash.app

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.checkbox.MaterialCheckBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SettingsMyAccountActivity : AppCompatActivity() {

    private lateinit var additionalContactsContainer: LinearLayout

    // Our "baseline snapshot" of what the screen looked like after loading from the server.
    // If the user changes anything, we compare against this to decide whether to nag them
    // with the "unsaved changes" dialog like the responsible adults we pretend to be.
    private val initialValues: MutableMap<String, String> = mutableMapOf()

    private lateinit var progressBar: ProgressBar // ProgressBar for loading indicator
    private lateinit var dimOverlay: View // Add this for the blur effect

    // The SMS toggle (MaterialCheckBox in the XML). This is the star of today’s show.
    private lateinit var smsAlertsCheckbox: MaterialCheckBox

    // Guard flag so we don’t accidentally “react” to our own programmatic UI updates.
    // i.e., while we populate the form from the server, we do NOT want to fire network calls.
    private var isPopulatingForm: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_my_account)

        // Initialize the container for dynamically added contact fields
        additionalContactsContainer = findViewById(R.id.additionalContactsContainer)
        progressBar = findViewById(R.id.progressBar) // Initialize ProgressBar
        dimOverlay = findViewById(R.id.dimOverlay) // Initialize the dimOverlay

        // Wire up the SMS checkbox.
        smsAlertsCheckbox = findViewById(R.id.el_pro_enable_sms_alerts)

        // When the user toggles SMS alerts, we push the new value to the server immediately.
        // Why? Because it’s a setting, not a “draft.” And because the server would like to
        // know what timeline we’re currently living in.
        smsAlertsCheckbox.setOnCheckedChangeListener { _, isChecked ->
            // If we're currently populating the form, ignore changes caused by our own code.
            if (isPopulatingForm) return@setOnCheckedChangeListener
            updateSmsToggleOnServer(isChecked)
        }

        // Set up back navigation using the chevron icon and cancel button
        val chevronLeft: ImageView = findViewById(R.id.chevronLeft)
        chevronLeft.setOnClickListener {
            handleBackPress()
        }

        val cancelButton: Button = findViewById(R.id.cancelButton)
        cancelButton.setOnClickListener {
            handleBackPress()
        }

        // Show loading indicator and load user data
        showLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            loadFormData()
        }

        // Set up the save button to submit form data to the server
        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            submitFormData()
        }

        // Handle back button presses using OnBackPressedCallback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun handleBackPress() {
        // If we have edits that are not saved, we do the polite thing and ask if they meant it.
        // If not, we vanish like a cryptid in the woods.
        if (hasChanges) {
            showUnsavedChangesDialog()
        } else {
            finish()
        }
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_changes_title)
            .setMessage(R.string.unsaved_changes_message)
            .setPositiveButton(R.string.leave) { _, _ ->
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showLoading(show: Boolean) {
        // Manage visibility of the ProgressBar and DimOverlay
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        dimOverlay.visibility = if (show) View.VISIBLE else View.GONE

        // Keeping your existing behavior intact: this hides the dynamic contacts container.
        // The overlay blocks taps anyway, so the rest of the screen being visible is fine.
        additionalContactsContainer.visibility = if (show) View.GONE else View.VISIBLE

        findViewById<Button>(R.id.saveButton).isEnabled = !show
        findViewById<Button>(R.id.cancelButton).isEnabled = !show

        // Also freeze the SMS checkbox while loading/updating so it doesn’t get spam-tapped.
        findViewById<MaterialCheckBox>(R.id.el_pro_enable_sms_alerts).isEnabled = !show
    }

    private fun getUserIDFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("userID", -1)
    }

    private fun getUserSubFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("Subscription", 0)  // Default is 0, indicating no subscription
    }

    private fun addAdditionalContacts(subscriptionLevel: Int, contacts: List<Pair<String, String>>) {
        val inflater = LayoutInflater.from(this)
        val numOfContacts = when (subscriptionLevel) {
            1 -> 5   // Subscription level 1 allows 4 additional contacts (2..5)
            2 -> 10  // Subscription level 2 allows 9 additional contacts (2..10)
            else -> 0
        }

        // Clear previous views to prevent duplication
        additionalContactsContainer.removeAllViews()

        // Start adding contacts from "Additional Contact 2"
        var count = 2
        for ((cell, email) in contacts) {
            if (count > numOfContacts) break // Stop adding fields if we've reached the subscription limit

            if (cell.isNotEmpty() || email.isNotEmpty()) {
                val contactView = inflater.inflate(R.layout.item_additional_contact, additionalContactsContainer, false)

                // Set title with the correct contact number starting from 2
                val contactTitle = contactView.findViewById<TextView>(R.id.contactTitle)
                contactTitle.text = getString(R.string.additional_contact, count)

                contactView.findViewById<EditText>(R.id.cellPhoneNumber).setText(cell)
                contactView.findViewById<EditText>(R.id.email).setText(email)
                additionalContactsContainer.addView(contactView)
                count++
            }
        }

        // If fewer than numOfContacts are filled, add remaining empty fields
        for (i in count..numOfContacts) {
            val contactView = inflater.inflate(R.layout.item_additional_contact, additionalContactsContainer, false)


            // Set title with the correct contact number starting from 2
            val contactTitle = contactView.findViewById<TextView>(R.id.contactTitle)
            contactTitle.text = getString(R.string.additional_contact, i)

            contactView.findViewById<EditText>(R.id.cellPhoneNumber).hint =
                getString(R.string.cell_phone_number_hint, i)
            contactView.findViewById<EditText>(R.id.email).hint =
                getString(R.string.email_hint, i)

            additionalContactsContainer.addView(contactView)
        }
    }

    private val hasChanges: Boolean
        get() {
            // If we haven't loaded anything yet, there's no baseline to compare against.
            // Translation: don't crash and don't accuse the user of crimes we can't prove.
            if (initialValues.isEmpty()) return false

            // Check static fields
            if (findViewById<EditText>(R.id.firstName).text.toString() != initialValues["firstName"]) return true
            if (findViewById<EditText>(R.id.lastName).text.toString() != initialValues["lastName"]) return true
            if (findViewById<EditText>(R.id.el_pro_address1).text.toString() != initialValues["el_pro_address1"]) return true
            if (findViewById<EditText>(R.id.el_pro_address2).text.toString() != initialValues["el_pro_address2"]) return true
            if (findViewById<EditText>(R.id.el_pro_city).text.toString() != initialValues["el_pro_city"]) return true
            if (findViewById<EditText>(R.id.el_pro_state).text.toString() != initialValues["el_pro_state"]) return true
            if (findViewById<EditText>(R.id.el_pro_zip).text.toString() != initialValues["el_pro_zip"]) return true
            if (findViewById<EditText>(R.id.el_pro_cell).text.toString() != initialValues["el_pro_cell"]) return true

            // Check SMS toggle
            val currentSms = if (smsAlertsCheckbox.isChecked) "1" else "0"
            if (currentSms != initialValues["enableSMS"]) return true

            // Check additional contacts dynamically added
            for (i in 0 until additionalContactsContainer.childCount) {
                val contactView = additionalContactsContainer.getChildAt(i)
                if (contactView.findViewById<EditText>(R.id.cellPhoneNumber).text.toString() != initialValues["cellPhoneNumber_$i"]) return true
                if (contactView.findViewById<EditText>(R.id.email).text.toString() != initialValues["email_$i"]) return true
            }

            return false
        }

    private fun storeInitialValues() {
        // We keep the same map instance and just reset it.
        // It’s like wiping fingerprints off the evidence board before the next round.
        initialValues.clear()

        // Store initial values of static fields
        initialValues["firstName"] = findViewById<EditText>(R.id.firstName).text.toString()
        initialValues["lastName"] = findViewById<EditText>(R.id.lastName).text.toString()
        initialValues["el_pro_address1"] = findViewById<EditText>(R.id.el_pro_address1).text.toString()
        initialValues["el_pro_address2"] = findViewById<EditText>(R.id.el_pro_address2).text.toString()
        initialValues["el_pro_city"] = findViewById<EditText>(R.id.el_pro_city).text.toString()
        initialValues["el_pro_state"] = findViewById<EditText>(R.id.el_pro_state).text.toString()
        initialValues["el_pro_zip"] = findViewById<EditText>(R.id.el_pro_zip).text.toString()
        initialValues["el_pro_cell"] = findViewById<EditText>(R.id.el_pro_cell).text.toString()

        // Store initial value of SMS toggle
        initialValues["enableSMS"] = if (smsAlertsCheckbox.isChecked) "1" else "0"

        // Store initial values of dynamically added contact fields
        for (i in 0 until additionalContactsContainer.childCount) {
            val contactView = additionalContactsContainer.getChildAt(i)
            initialValues["cellPhoneNumber_$i"] = contactView.findViewById<EditText>(R.id.cellPhoneNumber).text.toString()
            initialValues["email_$i"] = contactView.findViewById<EditText>(R.id.email).text.toString()
        }
    }

    private fun submitFormData() {
        val formData = mutableMapOf(
            "userID" to getUserIDFromPrefs().toString(),
            "first_name" to findViewById<EditText>(R.id.firstName).text.toString(),
            "last_name" to findViewById<EditText>(R.id.lastName).text.toString(),
            "el_pro_address1" to findViewById<EditText>(R.id.el_pro_address1).text.toString(),
            "el_pro_address2" to findViewById<EditText>(R.id.el_pro_address2).text.toString(),
            "el_pro_city" to findViewById<EditText>(R.id.el_pro_city).text.toString(),
            "el_pro_state" to findViewById<EditText>(R.id.el_pro_state).text.toString(),
            "el_pro_zip" to findViewById<EditText>(R.id.el_pro_zip).text.toString(),
            "el_pro_cell" to findViewById<EditText>(R.id.el_pro_cell).text.toString(),

            // Include enableSMS in the Save payload so the server never gets "blanked out"
            // by accident (your PHP currently updates enableSMS unconditionally).
            "enableSMS" to if (smsAlertsCheckbox.isChecked) "1" else "0"
        )

        // Collect additional contact information dynamically
        for (i in 2 until additionalContactsContainer.childCount + 2) { // Start from 2 for additional contacts
            val contactView = additionalContactsContainer.getChildAt(i - 2)
            formData["el_pro_cell$i"] = contactView.findViewById<EditText>(R.id.cellPhoneNumber).text.toString()
            formData["user_email$i"] = contactView.findViewById<EditText>(R.id.email).text.toString()
        }

        // Execute form data submission using coroutines
        CoroutineScope(Dispatchers.IO).launch {
            sendFormData(formData)
        }
    }

    private suspend fun loadFormData() {
        val userID = getUserIDFromPrefs()
        if (userID == -1) {
            Log.e("Error", "Invalid user ID")
            withContext(Dispatchers.Main) { showLoading(false) }
            return
        }

        val urlString = "https://emergencyleash.com/wp-content/plugins/access-app/pull/settings-my-account.php"
        val result = withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true

                val postData = "userID=${URLEncoder.encode(userID.toString(), "UTF-8")}"
                connection.outputStream.use { it.write(postData.toByteArray()) }

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("Error", "Failed to load form data", e)
                null
            }
        }

        withContext(Dispatchers.Main) {
            if (result != null) {
                Log.d("Response", result)
                populateFormFields(result)
            } else {
                Log.d("Response", "Failed to load data from the server")
            }
            showLoading(false) // Hide loading indicator after loading data
        }
    }

    private suspend fun sendFormData(formData: Map<String, String>) {
        val urlString = "https://emergencyleash.com/wp-content/plugins/access-app/push/settings-my-account.php"
        val postData = formData.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

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
                Log.e("Error", "Failed to send form data", e)
                null
            }
        }

        withContext(Dispatchers.Main) {
            if (result != null) {
                try {
                    val jsonObject = JSONObject(result)
                    val message = jsonObject.optString("message", getString(R.string.error_occurred))
                    showResponseDialog(message)
                } catch (e: Exception) {
                    Log.e("Error", "Failed to parse server response", e)
                    showResponseDialog(getString(R.string.error_occurred))
                }
            } else {
                showResponseDialog(getString(R.string.failed_to_connect))
            }
        }
    }

    private fun populateFormFields(jsonResponse: String) {
        try {
            val jsonObject = JSONObject(jsonResponse)

            // We are about to set UI values programmatically.
            // If we don't guard this, the SMS checkbox listener will panic and call the server.
            isPopulatingForm = true

            // Populate static fields
            findViewById<EditText>(R.id.firstName).setText(jsonObject.optString("first_name", ""))
            findViewById<EditText>(R.id.lastName).setText(jsonObject.optString("last_name", ""))
            findViewById<EditText>(R.id.el_pro_address1).setText(jsonObject.optString("el_pro_address1", ""))
            findViewById<EditText>(R.id.el_pro_address2).setText(jsonObject.optString("el_pro_address2", ""))
            findViewById<EditText>(R.id.el_pro_city).setText(jsonObject.optString("el_pro_city", ""))
            findViewById<EditText>(R.id.el_pro_state).setText(jsonObject.optString("el_pro_state", ""))
            findViewById<EditText>(R.id.el_pro_zip).setText(jsonObject.optString("el_pro_zip", ""))
            findViewById<EditText>(R.id.el_pro_cell).setText(jsonObject.optString("el_pro_cell", ""))

            // Populate SMS toggle from server value
            val enableSmsRaw = jsonObject.optString("enableSMS", "0")
            smsAlertsCheckbox.isChecked = parseServerBoolean(enableSmsRaw)

            // Collect contact fields
            val contacts = mutableListOf<Pair<String, String>>()
            for (i in 2..10) {  // Start from 2 for additional contacts
                val cellPhone = jsonObject.optString("el_pro_cell$i", "")
                val email = jsonObject.optString("user_email$i", "")
                contacts.add(Pair(cellPhone, email))
            }

            // Retrieve user subscription and dynamically add fields
            val userSubscriptionLevel = getUserSubFromPrefs()
            addAdditionalContacts(userSubscriptionLevel, contacts)

            // Done with programmatic UI updates.
            isPopulatingForm = false

            // Store initial values after loading data
            storeInitialValues()
        } catch (e: Exception) {
            // If we blow up parsing JSON, at least don’t leave the guard flag stuck on.
            isPopulatingForm = false
            Log.e("Error", "Failed to parse JSON response", e)
        }
    }

    private fun showResponseDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.update_successful)
            .setMessage(message)
            .setPositiveButton(R.string.okay) { dialog, _ ->
                dialog.dismiss()
                // Refresh the activity
                finish()
                startActivity(intent)
            }
            .setCancelable(false)
            .show()
    }

    // ---------------------------------------------------------------------------------------------
    // SMS toggle support: Load current state + push changes instantly + keep hasChanges honest.
    // ---------------------------------------------------------------------------------------------

    private fun parseServerBoolean(raw: String?): Boolean {
        // Server values tend to be… “creative.” We accept a few common forms.
        // If it’s not obviously true, we assume it’s false. Skepticism is healthy.
        if (raw.isNullOrBlank()) return false
        return raw == "1" ||
                raw.equals("true", true) ||
                raw.equals("on", true) ||
                raw.equals("yes", true)
    }

    private fun updateSmsToggleOnServer(isEnabled: Boolean) {
        val userID = getUserIDFromPrefs()
        if (userID == -1) {
            Toast.makeText(this, "Invalid user session. Please log in again.", Toast.LENGTH_LONG).show()
            return
        }

        // Remember the previous state so we can snap back if the server rejects our reality.
        val previousState = !isEnabled

        // Dramatic pause. Cue dim overlay. The server is being consulted by the council.
        showLoading(true)

        CoroutineScope(Dispatchers.IO).launch {
            // Only send what we need for this micro-update:
            // userID + enableSMS. Short, sweet, and suspiciously efficient.
            val payload = mapOf(
                "userID" to userID.toString(),
                "enableSMS" to if (isEnabled) "1" else "0"
            )

            val response = postUrlEncoded(
                "https://emergencyleash.com/wp-content/plugins/access-app/push/settings-my-account.php",
                payload
            )

            withContext(Dispatchers.Main) {
                showLoading(false)

                if (response.isNullOrBlank()) {
                    // If the network ghosts us, revert the checkbox to its prior state.
                    isPopulatingForm = true
                    smsAlertsCheckbox.isChecked = previousState
                    isPopulatingForm = false

                    Toast.makeText(
                        this@SettingsMyAccountActivity,
                        getString(R.string.failed_to_connect),
                        Toast.LENGTH_LONG
                    ).show()
                    return@withContext
                }

                try {
                    val json = JSONObject(response)
                    val result = json.optInt("result", 0)
                    val message = json.optString("message", getString(R.string.error_occurred))

                    if (result == 1) {
                        // Server accepted our offering. Update baseline so hasChanges stays accurate.
                        initialValues["enableSMS"] = if (isEnabled) "1" else "0"

                        // A tiny toast so the user knows the switch wasn't placebo.
                        Toast.makeText(this@SettingsMyAccountActivity, message, Toast.LENGTH_SHORT).show()
                    } else {
                        // Server rejected our offering. Revert, with style.
                        isPopulatingForm = true
                        smsAlertsCheckbox.isChecked = previousState
                        isPopulatingForm = false

                        Toast.makeText(this@SettingsMyAccountActivity, message, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    Log.e("Error", "Failed to parse SMS toggle response", e)

                    // If response parsing is cursed, revert anyway.
                    isPopulatingForm = true
                    smsAlertsCheckbox.isChecked = previousState
                    isPopulatingForm = false

                    Toast.makeText(
                        this@SettingsMyAccountActivity,
                        getString(R.string.error_occurred),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private suspend fun postUrlEncoded(urlString: String, formData: Map<String, String>): String? {
        // A small helper that posts application/x-www-form-urlencoded data to the server.
        // No Retrofit. No fancy. Just us and HttpURLConnection in a dimly lit alley.
        val postData = formData.entries.joinToString("&") {
            "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
        }

        return withContext(Dispatchers.IO) {
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
                Log.e("Error", "POST failed: $urlString", e)
                null
            }
        }
    }
}
