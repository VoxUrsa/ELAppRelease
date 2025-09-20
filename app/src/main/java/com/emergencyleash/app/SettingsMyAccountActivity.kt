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
    private lateinit var initialValues: MutableMap<String, String>
    private lateinit var progressBar: ProgressBar // ProgressBar for loading indicator
    private lateinit var dimOverlay: View // Add this for the blur effect

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_my_account)

        // Initialize the container for dynamically added contact fields
        additionalContactsContainer = findViewById(R.id.additionalContactsContainer)
        progressBar = findViewById(R.id.progressBar) // Initialize ProgressBar
        dimOverlay = findViewById(R.id.dimOverlay) // Initialize the dimOverlay

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

        additionalContactsContainer.visibility = if (show) View.GONE else View.VISIBLE
        findViewById<Button>(R.id.saveButton).isEnabled = !show
        findViewById<Button>(R.id.cancelButton).isEnabled = !show
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
            1 -> 5  // Subscription level 1 allows 4 additional contacts
            2 -> 10  // Subscription level 2 allows 9 additional contacts
            else -> 0  // No additional contacts for other subscription levels
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

            contactView.findViewById<EditText>(R.id.cellPhoneNumber).hint = getString(R.string.cell_phone_number_hint, i)
            contactView.findViewById<EditText>(R.id.email).hint = getString(R.string.email_hint, i)
            additionalContactsContainer.addView(contactView)
        }
    }

    private val hasChanges: Boolean
        get() {
            // Check static fields
            if (findViewById<EditText>(R.id.firstName).text.toString() != initialValues["firstName"]) return true
            if (findViewById<EditText>(R.id.lastName).text.toString() != initialValues["lastName"]) return true
            if (findViewById<EditText>(R.id.el_pro_address1).text.toString() != initialValues["el_pro_address1"]) return true
            if (findViewById<EditText>(R.id.el_pro_address2).text.toString() != initialValues["el_pro_address2"]) return true
            if (findViewById<EditText>(R.id.el_pro_city).text.toString() != initialValues["el_pro_city"]) return true
            if (findViewById<EditText>(R.id.el_pro_state).text.toString() != initialValues["el_pro_state"]) return true
            if (findViewById<EditText>(R.id.el_pro_zip).text.toString() != initialValues["el_pro_zip"]) return true
            if (findViewById<EditText>(R.id.el_pro_cell).text.toString() != initialValues["el_pro_cell"]) return true

            // Check additional contacts dynamically added
            for (i in 0 until additionalContactsContainer.childCount) {
                val contactView = additionalContactsContainer.getChildAt(i)
                if (contactView.findViewById<EditText>(R.id.cellPhoneNumber).text.toString() != initialValues["cellPhoneNumber_$i"]) return true
                if (contactView.findViewById<EditText>(R.id.email).text.toString() != initialValues["email_$i"]) return true
            }

            return false
        }


    private fun storeInitialValues() {
        initialValues = mutableMapOf()

        // Store initial values of static fields
        initialValues["firstName"] = findViewById<EditText>(R.id.firstName).text.toString()
        initialValues["lastName"] = findViewById<EditText>(R.id.lastName).text.toString()
        initialValues["el_pro_address1"] = findViewById<EditText>(R.id.el_pro_address1).text.toString()
        initialValues["el_pro_address2"] = findViewById<EditText>(R.id.el_pro_address2).text.toString()
        initialValues["el_pro_city"] = findViewById<EditText>(R.id.el_pro_city).text.toString()
        initialValues["el_pro_state"] = findViewById<EditText>(R.id.el_pro_state).text.toString()
        initialValues["el_pro_zip"] = findViewById<EditText>(R.id.el_pro_zip).text.toString()
        initialValues["el_pro_cell"] = findViewById<EditText>(R.id.el_pro_cell).text.toString()

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
            "el_pro_cell" to findViewById<EditText>(R.id.el_pro_cell).text.toString()
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
        val postData = formData.entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }

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

            // Populate static fields
            findViewById<EditText>(R.id.firstName).setText(jsonObject.optString("first_name", ""))
            findViewById<EditText>(R.id.lastName).setText(jsonObject.optString("last_name", ""))
            findViewById<EditText>(R.id.el_pro_address1).setText(jsonObject.optString("el_pro_address1", ""))
            findViewById<EditText>(R.id.el_pro_address2).setText(jsonObject.optString("el_pro_address2", ""))
            findViewById<EditText>(R.id.el_pro_city).setText(jsonObject.optString("el_pro_city", ""))
            findViewById<EditText>(R.id.el_pro_state).setText(jsonObject.optString("el_pro_state", ""))
            findViewById<EditText>(R.id.el_pro_zip).setText(jsonObject.optString("el_pro_zip", ""))
            findViewById<EditText>(R.id.el_pro_cell).setText(jsonObject.optString("el_pro_cell", ""))

            // Collect non-empty contact fields
            val contacts = mutableListOf<Pair<String, String>>()
            for (i in 2..10) {  // Start from 2 for additional contacts
                val cellPhone = jsonObject.optString("el_pro_cell$i", "")
                val email = jsonObject.optString("user_email$i", "")
                contacts.add(Pair(cellPhone, email))
            }

            // Retrieve user subscription and dynamically add fields
            val userSubscriptionLevel = getUserSubFromPrefs()
            addAdditionalContacts(userSubscriptionLevel, contacts)

            // Store initial values after loading data
            storeInitialValues()
        } catch (e: Exception) {
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

}
