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
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SettingsMyAdminsActivity : AppCompatActivity() {

    private lateinit var adminContactsContainer: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var dimOverlay: View
    private lateinit var initialValues: MutableMap<String, String>
    private val scope = CoroutineScope(Dispatchers.Main) // Coroutine scope for main thread

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_my_admins)

        // Initialize progressBar and dimOverlay
        progressBar = findViewById(R.id.progressBar)
        dimOverlay = findViewById(R.id.dimOverlay)

        // Initialize the container for dynamically added admin contact fields
        adminContactsContainer = findViewById(R.id.adminContactsContainer)

        // Set up back navigation using the chevron icon and cancel button
        val chevronLeft: ImageView = findViewById(R.id.chevronLeft)
        chevronLeft.setOnClickListener {
            handleBackPress()
        }

        val cancelButton: Button = findViewById(R.id.cancelButton)
        cancelButton.setOnClickListener {
            handleBackPress()
        }

        // Handle back navigation with unsaved changes
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })

        // Show loading and fetch data from the server
        showLoading(true)
        loadAdminDataFromServer()

        // Set up the save button to submit form data to the server
        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            submitFormData()
        }
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

    private fun handleBackPress() {
        if (hasChanges) {
            showUnsavedChangesDialog()
        } else {
            finish()
        }
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Do you want to leave without saving?")
            .setPositiveButton("Leave") { _, _ -> finish() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val hasChanges: Boolean
        get() {
            // Check dynamically added admin contact fields
            for (i in 0 until adminContactsContainer.childCount) {
                val contactView = adminContactsContainer.getChildAt(i)
                if (contactView.findViewById<EditText>(R.id.adminFirstName).text.toString() != initialValues["adminFirstName_$i"]) return true
                if (contactView.findViewById<EditText>(R.id.adminLastName).text.toString() != initialValues["adminLastName_$i"]) return true
                if (contactView.findViewById<EditText>(R.id.adminCellPhone).text.toString() != initialValues["adminCell_$i"]) return true
                if (contactView.findViewById<EditText>(R.id.adminEmail).text.toString() != initialValues["adminEmail_$i"]) return true
            }
            return false
        }

    private fun loadAdminDataFromServer() {
        val userID = getUserIDFromPrefs()
        scope.launch {
            showLoading(true)  // Show loading before fetching data
            val result = fetchAdminDataFromServer(userID)
            if (result != null) {
                populateFormFields(result)
            } else {
                Log.d("Response", "Failed to load data from the server")
            }
            showLoading(false)  // Hide loading after data is fetched
        }
    }

    private fun submitFormData() {
        scope.launch {
            showLoading(true)  // Show loading while sending data
            val formData = buildFormData()
            val result = sendFormDataToServer(formData)
            showResponseDialog(result)
            showLoading(false)  // Hide loading after submission
        }
    }

    private fun buildFormData(): Map<String, String> {
        val formData = mutableMapOf(
            "userID" to getUserIDFromPrefs().toString()
        )

        // Collect admin contact information dynamically
        for (i in 0 until adminContactsContainer.childCount) {
            val contactView = adminContactsContainer.getChildAt(i)
            formData["admin_first_name${i + 1}"] = contactView.findViewById<EditText>(R.id.adminFirstName).text.toString()
            formData["admin_last_name${i + 1}"] = contactView.findViewById<EditText>(R.id.adminLastName).text.toString()
            formData["admin_cell${i + 1}"] = contactView.findViewById<EditText>(R.id.adminCellPhone).text.toString()
            formData["admin_email${i + 1}"] = contactView.findViewById<EditText>(R.id.adminEmail).text.toString()
        }

        return formData
    }

    private suspend fun sendFormDataToServer(formData: Map<String, String>): String {
        return withContext(Dispatchers.IO) {
            val urlString = "https://emergencyleash.com/wp-content/plugins/access-app/push/settings-my-admins.php"
            val postData = StringBuilder()
            for ((key, value) in formData) {
                if (postData.isNotEmpty()) postData.append('&')
                postData.append(URLEncoder.encode(key, "UTF-8"))
                postData.append('=')
                postData.append(URLEncoder.encode(value, "UTF-8"))
            }

            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true

                val outputStream: OutputStream = connection.outputStream
                val writer = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))
                writer.write(postData.toString())
                writer.flush()
                writer.close()
                outputStream.close()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d("Response", "Server Response: $inputStream")
                    inputStream
                } else {
                    "Failed to connect to the server."
                }
            } catch (e: Exception) {
                Log.e("Error", "Failed to send form data", e)
                "Failed to send form data."
            }
        }
    }

    private fun showResponseDialog(response: String) {
        val jsonObject = JSONObject(response)
        val message = jsonObject.optString("message", "An error occurred.")
        AlertDialog.Builder(this)
            .setTitle("Server Response")
            .setMessage(message)
            .setPositiveButton("Okay") { dialog, _ ->
                dialog.dismiss()
                // Refresh the activity
                finish()
                startActivity(intent)
            }
            .setCancelable(false)
            .show()
    }

    private suspend fun fetchAdminDataFromServer(userID: Int): String? {
        return withContext(Dispatchers.IO) {
            val urlString = "https://emergencyleash.com/wp-content/plugins/access-app/pull/settings-my-admins.php"
            return@withContext try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true

                val postData = "userID=${URLEncoder.encode(userID.toString(), "UTF-8")}"
                val outputStream: OutputStream = connection.outputStream
                val writer = BufferedWriter(OutputStreamWriter(outputStream, "UTF-8"))
                writer.write(postData)
                writer.flush()
                writer.close()
                outputStream.close()

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream.bufferedReader().use { it.readText() }
                    inputStream
                } else {
                    null
                }
            } catch (e: Exception) {
                Log.e("Error", "Failed to load admin data", e)
                null
            }
        }
    }

    private fun populateFormFields(jsonResponse: String) {
        try {
            val jsonObject = JSONObject(jsonResponse)
            val admins = mutableListOf<AdminContact>()

            // Collect non-empty admin contact fields
            for (i in 1..10) {
                val firstName = jsonObject.optString("admin_first_name$i", "")
                val lastName = jsonObject.optString("admin_last_name$i", "")
                val cell = jsonObject.optString("admin_cell$i", "")
                val email = jsonObject.optString("admin_email$i", "")
                if (firstName.isNotEmpty() || lastName.isNotEmpty() || cell.isNotEmpty() || email.isNotEmpty()) {
                    admins.add(AdminContact(firstName, lastName, cell, email))
                }
            }

            // Retrieve user subscription and dynamically add fields
            val userSubscriptionLevel = getUserSubFromPrefs()
            addAdminContacts(userSubscriptionLevel, admins)

            // Store initial values after loading data
            storeInitialValues()
        } catch (e: Exception) {
            Log.e("Error", "Failed to parse JSON response", e)
        }
    }

    private fun storeInitialValues() {
        initialValues = mutableMapOf()

        // Store initial values of dynamically added admin contact fields
        for (i in 0 until adminContactsContainer.childCount) {
            val contactView = adminContactsContainer.getChildAt(i)
            initialValues["adminFirstName_$i"] = contactView.findViewById<EditText>(R.id.adminFirstName).text.toString()
            initialValues["adminLastName_$i"] = contactView.findViewById<EditText>(R.id.adminLastName).text.toString()
            initialValues["adminCell_$i"] = contactView.findViewById<EditText>(R.id.adminCellPhone).text.toString()
            initialValues["adminEmail_$i"] = contactView.findViewById<EditText>(R.id.adminEmail).text.toString()
        }
    }


    private fun getUserIDFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("userID", -1)
    }

    private fun getUserSubFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("Subscription", 0)  // Default is 0, indicating no subscription
    }

    private fun addAdminContacts(subscriptionLevel: Int, admins: List<AdminContact>) {
        val inflater = LayoutInflater.from(this)
        val numOfAdmins = when (subscriptionLevel) {
            1, 2 -> 10  // Both Subscription level 1 and 2 allow 10 admin contacts
            else -> 0  // No admin contacts for other subscription levels
        }

        // Clear previous views to prevent duplication
        adminContactsContainer.removeAllViews()

        for (i in 1..numOfAdmins) {
            val adminContact = if (i <= admins.size) admins[i - 1] else AdminContact("", "", "", "")
            val adminView = inflater.inflate(R.layout.item_admin_contact, adminContactsContainer, false)

            // Set title with the correct contact number starting from 1
            val adminTitle = adminView.findViewById<TextView>(R.id.adminContactTitle)
            adminTitle.text = getString(R.string.admin_contact_title, i)

            adminView.findViewById<EditText>(R.id.adminFirstName).setText(adminContact.firstName)
            adminView.findViewById<EditText>(R.id.adminLastName).setText(adminContact.lastName)
            adminView.findViewById<EditText>(R.id.adminCellPhone).setText(adminContact.cell)
            adminView.findViewById<EditText>(R.id.adminEmail).setText(adminContact.email)
            adminContactsContainer.addView(adminView)
        }
    }

    data class AdminContact(val firstName: String, val lastName: String, val cell: String, val email: String)
}
