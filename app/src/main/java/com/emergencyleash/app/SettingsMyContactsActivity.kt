package com.emergencyleash.app

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SettingsMyContactsActivity : AppCompatActivity() {

    private lateinit var emergencyContactsContainer: LinearLayout
    private lateinit var initialValues: MutableMap<String, String>
    private lateinit var caretakerNameEditText: EditText
    private lateinit var caretakerLastNameEditText: EditText
    private lateinit var caretakerCellEditText: EditText
    private lateinit var caretakerEmailEditText: EditText
    private lateinit var holidayStartDate: EditText
    private lateinit var holidayEndDate: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var dimOverlay: View
    private val dateFormat =
        SimpleDateFormat("MM/dd/yyyy", Locale.US) // Customize the format as needed
    private var currentSubscriptionName: String = ""


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_my_contacts)

        // Initialize progressBar and dimOverlay
        progressBar = findViewById(R.id.progressBar)
        dimOverlay = findViewById(R.id.dimOverlay)

        // Load existing user data from the server
        // Initialize the container for dynamically added contact fields
        emergencyContactsContainer = findViewById(R.id.emergencyContactsContainer)

        caretakerNameEditText = findViewById(R.id.cc_first_name)
        caretakerLastNameEditText = findViewById(R.id.cc_last_name)
        caretakerCellEditText = findViewById(R.id.cc_cell)
        caretakerEmailEditText = findViewById(R.id.cc_email)
        holidayStartDate = findViewById(R.id.cc_hm_start)
        holidayEndDate = findViewById(R.id.cc_hm_end)
        // Set up back navigation using the chevron icon and cancel button
        val chevronLeft: ImageView = findViewById(R.id.chevronLeft)
        chevronLeft.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val cancelButton: Button = findViewById(R.id.cancelButton)
        cancelButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Handle the back button press with OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasChanges) {
                    showUnsavedChangesDialog()
                } else {
                    finish()
                }
            }
        })

        // Set up the save button to submit form data to the server
        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            submitFormData()
        }
        holidayStartDate = findViewById(R.id.cc_hm_start)
        holidayEndDate = findViewById(R.id.cc_hm_end)

        // Setup the date picker for the start date
        holidayStartDate.setOnClickListener {
            showDatePickerDialog(holidayStartDate)
        }

        // Setup the date picker for the end date
        holidayEndDate.setOnClickListener {
            showDatePickerDialog(holidayEndDate)
        }

        lifecycleScope.launch {
            showLoading(true) // Show spinner

            // 1. (Optional) fetch subscription data purely for logging
            //    This is your "fetchAndLogSubscriptionLevel()" logic, if you still want logs:
            fetchAndLogSubscriptionLevel()

            // 2. Actually fetch subscription details so we can store subscriptionName
            val subscriptionResponse = fetchSubscriptionLevelFromServer()
            if (subscriptionResponse != null) {
                handleSubscriptionData(subscriptionResponse)
            } else {
                Log.e("SubscriptionLevel", "Failed to fetch subscription level from server")
                // We have no subscription data, so hide holiday fields
                showHolidayModeFields(false)
            }

            // 3. Now fetch the contact data (we already have currentSubscriptionName set!)
            val contactResponse = fetchContactDataFromServer()
            if (contactResponse != null) {
                populateContactFields(contactResponse)
            } else {
                Log.e("ContactData", "Failed to load contact data from the server")
            }

            showLoading(false) // Done loading
        }
    }

    private fun getUserIDFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("userID", -1)
    }

    private fun getUserSubFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt(
            "Subscription", 0
        ) // Default is 0, indicating no subscription
    }

    private fun addEmergencyContacts(numOfContacts: Int, contacts: List<Map<String, String>>) {
        val inflater = LayoutInflater.from(this)

        // Clear anything previously added
        emergencyContactsContainer.removeAllViews()

        var count = 1
        for (contact in contacts) {
            if (count > numOfContacts) break

            val contactView =
                inflater.inflate(R.layout.item_emergency_contact, emergencyContactsContainer, false)
            val contactTitle = contactView.findViewById<TextView>(R.id.contactTitle)
            contactTitle.text = getString(R.string.emergency_contact_title, count)

            contactView.findViewById<EditText>(R.id.ec_first_name).setText(contact["firstName"])
            contactView.findViewById<EditText>(R.id.ec_last_name).setText(contact["lastName"])
            contactView.findViewById<EditText>(R.id.ec_cell).setText(contact["cell"])
            contactView.findViewById<EditText>(R.id.ec_email).setText(contact["email"])

            emergencyContactsContainer.addView(contactView)
            count++
        }

        // If we have not reached the max number yet, add empty fields
        for (i in count..numOfContacts) {
            val contactView =
                inflater.inflate(R.layout.item_emergency_contact, emergencyContactsContainer, false)
            val contactTitle = contactView.findViewById<TextView>(R.id.contactTitle)
            contactTitle.text = getString(R.string.emergency_contact_title, i)
            emergencyContactsContainer.addView(contactView)
        }
    }


    private val hasChanges: Boolean
        get() {
            // Check if initialValues are initialized
            if (!::initialValues.isInitialized) {
                return false // If initialValues hasn't been initialized, there can't be any changes
            }

            // Check static fields for caretaker contact details
            if (caretakerNameEditText.text.toString() != initialValues["cc_first_name"]) return true
            if (caretakerLastNameEditText.text.toString() != initialValues["cc_last_name"]) return true
            if (caretakerCellEditText.text.toString() != initialValues["cc_cell"]) return true
            if (caretakerEmailEditText.text.toString() != initialValues["cc_email"]) return true
            if (holidayStartDate.text.toString() != initialValues["cc_hm_start"]) return true
            if (holidayEndDate.text.toString() != initialValues["cc_hm_end"]) return true

            // Check dynamic fields for emergency contacts
            for (i in 0 until emergencyContactsContainer.childCount) {
                val contactView = emergencyContactsContainer.getChildAt(i)
                if (contactView.findViewById<EditText>(R.id.ec_first_name).text.toString() != initialValues["ec_first_name_$i"]) return true
                if (contactView.findViewById<EditText>(R.id.ec_last_name).text.toString() != initialValues["ec_last_name_$i"]) return true
                if (contactView.findViewById<EditText>(R.id.ec_cell).text.toString() != initialValues["ec_cell_$i"]) return true
                if (contactView.findViewById<EditText>(R.id.ec_email).text.toString() != initialValues["ec_email_$i"]) return true
            }

            return false
        }


    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this).setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Do you want to leave without saving?")
            .setPositiveButton("Leave") { _, _ -> finish() }.setNegativeButton("Cancel", null)
            .show()
    }

    private fun storeInitialValues() {
        initialValues = mutableMapOf()

        // Store initial values of caretaker contact details
        initialValues["cc_first_name"] = caretakerNameEditText.text.toString()
        initialValues["cc_last_name"] = caretakerLastNameEditText.text.toString()
        initialValues["cc_cell"] = caretakerCellEditText.text.toString()
        initialValues["cc_email"] = caretakerEmailEditText.text.toString()
        initialValues["cc_hm_start"] = holidayStartDate.text.toString()
        initialValues["cc_hm_end"] = holidayEndDate.text.toString()

        // Store initial values of dynamically added emergency contact fields
        for (i in 0 until emergencyContactsContainer.childCount) {
            val contactView = emergencyContactsContainer.getChildAt(i)
            initialValues["ec_first_name_$i"] =
                contactView.findViewById<EditText>(R.id.ec_first_name).text.toString()
            initialValues["ec_last_name_$i"] =
                contactView.findViewById<EditText>(R.id.ec_last_name).text.toString()
            initialValues["ec_cell_$i"] =
                contactView.findViewById<EditText>(R.id.ec_cell).text.toString()
            initialValues["ec_email_$i"] =
                contactView.findViewById<EditText>(R.id.ec_email).text.toString()
        }
    }


    private fun loadContactData() {
        lifecycleScope.launch {
            showLoading(true)  // Show loading before fetching data
            val response = fetchContactDataFromServer()
            if (response != null) {
                populateContactFields(response)
            } else {
                Log.d("Response", "Failed to load data from the server")
            }
            showLoading(false)  // Hide loading after data is fetched
        }
    }


    private suspend fun fetchContactDataFromServer(): String? {
        return withContext(Dispatchers.IO) {
            val userID = getUserIDFromPrefs()
            if (userID == -1) {
                Log.e("Error", "Invalid user ID")
                return@withContext null
            }

            val urlString =
                "https://emergencyleash.com/wp-content/plugins/access-app/pull/settings-my-contacts.php"
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true

                val postData = "userID=${URLEncoder.encode(userID.toString(), "UTF-8")}"
                connection.outputStream.use { outputStream ->
                    BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                        writer.write(postData)
                        writer.flush()
                    }
                }

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { reader ->
                        reader.readText()
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("Error", "Failed to load form data", e)
                null
            }
        }
    }

    private fun populateContactFields(jsonResponse: String) {
        try {
            val jsonObject = JSONObject(jsonResponse)

            caretakerNameEditText.setText(jsonObject.optString("cc_first_name", ""))
            caretakerLastNameEditText.setText(jsonObject.optString("cc_last_name", ""))
            caretakerCellEditText.setText(jsonObject.optString("cc_cell", ""))
            caretakerEmailEditText.setText(jsonObject.optString("cc_email", ""))
            holidayStartDate.setText(jsonObject.optString("cc_hm_start", ""))
            holidayEndDate.setText(jsonObject.optString("cc_hm_end", ""))

            val contacts = mutableListOf<Map<String, String>>()
            for (i in 1..10) {
                val firstName = jsonObject.optString("ec_first_name$i", "")
                val lastName = jsonObject.optString("ec_last_name$i", "")
                val cell = jsonObject.optString("ec_cell$i", "")
                val email = jsonObject.optString("ec_email$i", "")
                contacts.add(
                    mapOf(
                        "firstName" to firstName,
                        "lastName" to lastName,
                        "cell" to cell,
                        "email" to email
                    )
                )
            }

            // Decide how many contacts to show based on the subscription name
            val numOfContacts =
                if (currentSubscriptionName == "Emergency Leash Multiple Tags" || currentSubscriptionName == "Emergency Leash One Tag") {
                    10
                } else {
                    5
                }

            addEmergencyContacts(numOfContacts, contacts)

            storeInitialValues()

        } catch (e: Exception) {
            Log.e("Error", "Failed to parse JSON response", e)
        }
    }


    private fun submitFormData() {
        lifecycleScope.launch {
            val formData = buildFormData()
            val response = sendFormDataToServer(formData)
            if (response != null) {
                handleServerResponse(response)
            } else {
                Toast.makeText(
                    this@SettingsMyContactsActivity,
                    "Failed to connect to the server.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun buildFormData(): Map<String, String> {
        val formData = mutableMapOf(
            "userID" to getUserIDFromPrefs().toString(),
            "cc_first_name" to caretakerNameEditText.text.toString(),
            "cc_last_name" to caretakerLastNameEditText.text.toString(),
            "cc_cell" to caretakerCellEditText.text.toString(),
            "cc_email" to caretakerEmailEditText.text.toString(),
            "cc_hm_start" to holidayStartDate.text.toString(),
            "cc_hm_end" to holidayEndDate.text.toString()
        )

        for (i in 0 until emergencyContactsContainer.childCount) {
            val contactView = emergencyContactsContainer.getChildAt(i)
            formData["ec_first_name${i + 1}"] =
                contactView.findViewById<EditText>(R.id.ec_first_name).text.toString()
            formData["ec_last_name${i + 1}"] =
                contactView.findViewById<EditText>(R.id.ec_last_name).text.toString()
            formData["ec_cell${i + 1}"] =
                contactView.findViewById<EditText>(R.id.ec_cell).text.toString()
            formData["ec_email${i + 1}"] =
                contactView.findViewById<EditText>(R.id.ec_email).text.toString()
        }

        return formData
    }

    private suspend fun sendFormDataToServer(formData: Map<String, String>): String? {
        return withContext(Dispatchers.IO) {
            val urlString =
                "https://emergencyleash.com/wp-content/plugins/access-app/push/settings-my-contacts.php"
            val postData = StringBuilder()
            for ((key, value) in formData) {
                if (postData.isNotEmpty()) postData.append('&')
                postData.append(URLEncoder.encode(key, "UTF-8"))
                postData.append('=')
                postData.append(URLEncoder.encode(value, "UTF-8"))
            }

            Log.d("SettingsMyContacts", "Sending data to server: $postData")

            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true

                connection.outputStream.use { outputStream ->
                    BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                        writer.write(postData.toString())
                        writer.flush()
                    }
                }

                val responseCode = connection.responseCode
                val responseMessage = connection.inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }

                Log.d(
                    "SettingsMyContacts",
                    "Response Code: $responseCode, Response Message: $responseMessage"
                )

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    responseMessage
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("Error", "Failed to send form data", e)
                null
            }
        }
    }


    private fun handleServerResponse(response: String) {
        try {
            val jsonObject = JSONObject(response)
            val message = jsonObject.optString("message", "Saved.")
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            // Pull fresh data so change-tracking resets without a popup
            loadContactData()
        } catch (e: Exception) {
            Log.e("Error", "Failed to parse server response", e)
            Toast.makeText(this, "An error occurred.", Toast.LENGTH_SHORT).show()
        }
    }


    private fun showDatePickerDialog(editText: EditText) {
        val calendar = Calendar.getInstance()

        // Create a new DatePickerDialog and show it
        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                // Set the date chosen on the EditText
                calendar.set(year, month, dayOfMonth)
                editText.setText(dateFormat.format(calendar.time)) // Format the date and display it
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePickerDialog.show()
    }

    private fun showLoading(show: Boolean) {
        if (show) {
            progressBar.visibility = View.VISIBLE
            dimOverlay.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.GONE
            dimOverlay.visibility = View.GONE
        }
    }

    private fun loadSubscriptionLevel() {
        lifecycleScope.launch {
            val response = fetchSubscriptionLevelFromServer()
            if (response != null) {
                handleSubscriptionData(response)
            } else {
                Log.e("SubscriptionLevel", "Failed to fetch subscription level from server")
                // Hide holiday mode fields if there's no valid response
                showHolidayModeFields(false)
            }
        }
    }


    private fun fetchAndLogSubscriptionLevel() {
        lifecycleScope.launch {
            showLoading(true) // Show loading indicator
            val subscriptionData = fetchSubscriptionLevelFromServer()
            if (subscriptionData != null) {
                try {
                    val jsonObject = JSONObject(subscriptionData)
                    val subscriptionArray = jsonObject.optJSONArray("subscription")
                    if (subscriptionArray != null && subscriptionArray.length() > 0) {
                        for (i in 0 until subscriptionArray.length()) {
                            val subscriptionItem = subscriptionArray.getJSONObject(i)
                            val name = subscriptionItem.optString("subscription_name", "Unknown")
                            val status =
                                subscriptionItem.optString("subscription_status", "Unknown")
                            val startDate =
                                subscriptionItem.optString("subscription_start_date", "Unknown")
                            val nextPayment =
                                subscriptionItem.optString("subscription_next_payment", "Unknown")
                            Log.d(
                                "SubscriptionLevel",
                                "Name: $name, Status: $status, Start Date: $startDate, Next Payment: $nextPayment"
                            )
                        }
                    } else {
                        Log.d("SubscriptionLevel", "No subscription data available.")
                    }
                } catch (e: Exception) {
                    Log.e("SubscriptionLevel", "Failed to parse subscription data: ${e.message}")
                }
            } else {
                Log.e("SubscriptionLevel", "Failed to fetch subscription data from server.")
            }
            showLoading(false) // Hide loading indicator
        }
    }


    private suspend fun fetchSubscriptionLevelFromServer(): String? {
        return withContext(Dispatchers.IO) {
            val userID = getUserIDFromPrefs()
            if (userID == -1) {
                Log.e("Error", "Invalid user ID")
                return@withContext null
            }

            val urlString =
                "https://emergencyleash.com/wp-content/plugins/access-app/pull/settings-subscription.php"
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true

                val postData = "userID=${URLEncoder.encode(userID.toString(), "UTF-8")}"
                connection.outputStream.use { outputStream ->
                    BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                        writer.write(postData)
                        writer.flush()
                    }
                }

                val responseCode = connection.responseCode
                val responseBody = connection.inputStream.bufferedReader().use { reader ->
                    reader.readText()
                }

                // Log the raw server response
                Log.d("SubscriptionResponse", "Raw Server Response: $responseBody")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    responseBody
                } else {
                    Log.e("SubscriptionResponse", "Server returned response code: $responseCode")
                    null
                }
            } catch (e: Exception) {
                Log.e("Error", "Failed to fetch subscription data", e)
                null
            }
        }
    }

    private fun handleSubscriptionData(response: String) {
        try {
            val jsonObject = JSONObject(response)
            val subscription = jsonObject.optJSONObject("subscription")

            if (subscription == null) {
                // No active subscription found
                Log.d("SubscriptionLevel", "No active subscription found")
                currentSubscriptionName = ""  // or "No Subscription" as a fallback
                showHolidayModeFields(false)  // Hide Holiday Mode for unknown or no subscription
                return
            }

            // Extract subscription name
            val subscriptionName = subscription.optString("subscription_name", "")
            currentSubscriptionName = subscriptionName  // Store globally
            Log.d("SubscriptionLevel", "Subscription Name: $subscriptionName")

            // Show or hide holiday mode fields based on the subscriptionName
            if (subscriptionName == "Emergency Leash Multiple Tags" || subscriptionName == "Emergency Leash One Tag") {
                showHolidayModeFields(true)
            } else {
                showHolidayModeFields(false)
            }
        } catch (e: Exception) {
            Log.e("SubscriptionLevel", "Failed to parse subscription data: ${e.message}")
        }
    }


    private fun showHolidayModeFields(show: Boolean) {
        val startText = findViewById<TextView>(R.id.cc_hm_start_text)
        val startField = findViewById<View>(R.id.cc_hm_start)
        val endText = findViewById<TextView>(R.id.cc_hm_end_text)
        val endField = findViewById<View>(R.id.cc_hm_end)

        if (show) {
            startField.visibility = View.VISIBLE
            startText.visibility = View.VISIBLE
            endField.visibility = View.VISIBLE
            endText.visibility = View.VISIBLE

        } else {
            startText.visibility = View.GONE
            startField.visibility = View.GONE
            endText.visibility = View.GONE
            endField.visibility = View.GONE


        }
    }


}
