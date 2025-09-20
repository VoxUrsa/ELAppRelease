package com.emergencyleash.app

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
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

class SettingsMyAddressesActivity : AppCompatActivity() {

    private lateinit var billingFirstName: EditText
    private lateinit var billingLastName: EditText
    private lateinit var billingCompany: EditText
    private lateinit var billingAddress1: EditText
    private lateinit var billingAddress2: EditText
    private lateinit var billingCity: EditText
    private lateinit var billingState: EditText
    private lateinit var billingPostcode: EditText
    private lateinit var billingPhone: EditText
    private lateinit var billingEmail: EditText

    private lateinit var shippingFirstName: EditText
    private lateinit var shippingLastName: EditText
    private lateinit var shippingCompany: EditText
    private lateinit var shippingAddress1: EditText
    private lateinit var shippingAddress2: EditText
    private lateinit var shippingCity: EditText
    private lateinit var shippingState: EditText
    private lateinit var shippingPostcode: EditText
    private lateinit var shippingPhone: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var dimOverlay: View
    private lateinit var initialValues: MutableMap<String, String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_my_addresses)

        // Initialize EditText fields for billing and shipping addresses
        billingFirstName = findViewById(R.id.billingFirstName)
        billingLastName = findViewById(R.id.billingLastName)
        billingCompany = findViewById(R.id.billingCompany)
        billingAddress1 = findViewById(R.id.billingAddress1)
        billingAddress2 = findViewById(R.id.billingAddress2)
        billingCity = findViewById(R.id.billingCity)
        billingState = findViewById(R.id.billingState)
        billingPostcode = findViewById(R.id.billingZip)
        billingPhone = findViewById(R.id.billingPhone)
        billingEmail = findViewById(R.id.billingEmail)

        shippingFirstName = findViewById(R.id.shippingFirstName)
        shippingLastName = findViewById(R.id.shippingLastName)
        shippingCompany = findViewById(R.id.shippingCompany)
        shippingAddress1 = findViewById(R.id.shippingAddress1)
        shippingAddress2 = findViewById(R.id.shippingAddress2)
        shippingCity = findViewById(R.id.shippingCity)
        shippingState = findViewById(R.id.shippingState)
        shippingPostcode = findViewById(R.id.shippingZip)
        shippingPhone = findViewById(R.id.shippingPhone)

        // Initialize progress bar and dim overlay
        progressBar = findViewById(R.id.progressBar)
        dimOverlay = findViewById(R.id.dimOverlay)

        // Set up back navigation
        val chevronLeft: ImageView = findViewById(R.id.chevronLeft)
        chevronLeft.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        val cancelButton: Button = findViewById(R.id.cancelButton)
        cancelButton.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        // Handle back button press with unsaved changes check
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasChanges()) {
                    showUnsavedChangesDialog()
                } else {
                    finish()
                }
            }
        })

        // Load existing address data from server
        loadAddressData()

        // Save button click event
        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            submitAddressData()
        }
    }

    private fun hasChanges(): Boolean {
        if (billingFirstName.text.toString() != initialValues["billing_first_name"]) return true
        if (billingLastName.text.toString() != initialValues["billing_last_name"]) return true
        if (billingCompany.text.toString() != initialValues["billing_company"]) return true
        if (billingAddress1.text.toString() != initialValues["billing_address_1"]) return true
        if (billingAddress2.text.toString() != initialValues["billing_address_2"]) return true
        if (billingCity.text.toString() != initialValues["billing_city"]) return true
        if (billingState.text.toString() != initialValues["billing_state"]) return true
        if (billingPostcode.text.toString() != initialValues["billing_postcode"]) return true
        if (billingPhone.text.toString() != initialValues["billing_phone"]) return true
        if (billingEmail.text.toString() != initialValues["billing_email"]) return true

        if (shippingFirstName.text.toString() != initialValues["shipping_first_name"]) return true
        if (shippingLastName.text.toString() != initialValues["shipping_last_name"]) return true
        if (shippingCompany.text.toString() != initialValues["shipping_company"]) return true
        if (shippingAddress1.text.toString() != initialValues["shipping_address_1"]) return true
        if (shippingAddress2.text.toString() != initialValues["shipping_address_2"]) return true
        if (shippingCity.text.toString() != initialValues["shipping_city"]) return true
        if (shippingState.text.toString() != initialValues["shipping_state"]) return true
        if (shippingPostcode.text.toString() != initialValues["shipping_postcode"]) return true
        if (shippingPhone.text.toString() != initialValues["shipping_phone"]) return true

        return false
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.unsaved_changes)
            .setMessage(R.string.unsaved_changes_message)
            .setPositiveButton(R.string.leave) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun storeInitialValues() {
        initialValues = mutableMapOf(
            "billing_first_name" to billingFirstName.text.toString(),
            "billing_last_name" to billingLastName.text.toString(),
            "billing_company" to billingCompany.text.toString(),
            "billing_address_1" to billingAddress1.text.toString(),
            "billing_address_2" to billingAddress2.text.toString(),
            "billing_city" to billingCity.text.toString(),
            "billing_state" to billingState.text.toString(),
            "billing_postcode" to billingPostcode.text.toString(),
            "billing_phone" to billingPhone.text.toString(),
            "billing_email" to billingEmail.text.toString(),

            "shipping_first_name" to shippingFirstName.text.toString(),
            "shipping_last_name" to shippingLastName.text.toString(),
            "shipping_company" to shippingCompany.text.toString(),
            "shipping_address_1" to shippingAddress1.text.toString(),
            "shipping_address_2" to shippingAddress2.text.toString(),
            "shipping_city" to shippingCity.text.toString(),
            "shipping_state" to shippingState.text.toString(),
            "shipping_postcode" to shippingPostcode.text.toString(),
            "shipping_phone" to shippingPhone.text.toString()
        )
    }

    private fun loadAddressData() {
        setLoadingState(true) // Show loading state

        lifecycleScope.launch {
            val response = fetchAddressDataFromServer()
            if (response != null) {
                populateAddressFields(response)
            } else {
                Log.d("Response", "Failed to load data from the server")
            }
            setLoadingState(false) // Hide loading state
        }
    }

    private suspend fun fetchAddressDataFromServer(): String? {
        return withContext(Dispatchers.IO) {
            val userID = getUserIDFromPrefs()
            if (userID == -1) {
                Log.e("Error", "Invalid user ID")
                return@withContext null
            }

            val urlString = "https://emergencyleash.com/wp-content/plugins/access-app/pull/settings-my-addresses.php"
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

    private fun populateAddressFields(jsonResponse: String) {
        try {
            val jsonObject = JSONObject(jsonResponse)

            billingFirstName.setText(jsonObject.optString("billing_first_name", ""))
            billingLastName.setText(jsonObject.optString("billing_last_name", ""))
            billingCompany.setText(jsonObject.optString("billing_company", ""))
            billingAddress1.setText(jsonObject.optString("billing_address_1", ""))
            billingAddress2.setText(jsonObject.optString("billing_address_2", ""))
            billingCity.setText(jsonObject.optString("billing_city", ""))
            billingState.setText(jsonObject.optString("billing_state", ""))
            billingPostcode.setText(jsonObject.optString("billing_postcode", ""))
            billingPhone.setText(jsonObject.optString("billing_phone", ""))
            billingEmail.setText(jsonObject.optString("billing_email", ""))

            shippingFirstName.setText(jsonObject.optString("shipping_first_name", ""))
            shippingLastName.setText(jsonObject.optString("shipping_last_name", ""))
            shippingCompany.setText(jsonObject.optString("shipping_company", ""))
            shippingAddress1.setText(jsonObject.optString("shipping_address_1", ""))
            shippingAddress2.setText(jsonObject.optString("shipping_address_2", ""))
            shippingCity.setText(jsonObject.optString("shipping_city", ""))
            shippingState.setText(jsonObject.optString("shipping_state", ""))
            shippingPostcode.setText(jsonObject.optString("shipping_postcode", ""))
            shippingPhone.setText(jsonObject.optString("shipping_phone", ""))

            storeInitialValues()
        } catch (e: Exception) {
            Log.e("Error", "Failed to parse JSON response", e)
        }
    }

    private fun submitAddressData() {
        setLoadingState(true) // Show loading state

        lifecycleScope.launch {
            val formData = buildFormData()
            val response = sendAddressDataToServer(formData)
            if (response != null) {
                handleServerResponse(response)
            } else {
                showResponseDialog("Failed to connect to the server.")
            }
            setLoadingState(false) // Hide loading state
        }
    }

    private fun buildFormData(): Map<String, String> {
        return mutableMapOf(
            "userID" to getUserIDFromPrefs().toString(),
            "billing_first_name" to billingFirstName.text.toString(),
            "billing_last_name" to billingLastName.text.toString(),
            "billing_company" to billingCompany.text.toString(),
            "billing_address_1" to billingAddress1.text.toString(),
            "billing_address_2" to billingAddress2.text.toString(),
            "billing_city" to billingCity.text.toString(),
            "billing_state" to billingState.text.toString(),
            "billing_postcode" to billingPostcode.text.toString(),
            "billing_phone" to billingPhone.text.toString(),
            "billing_email" to billingEmail.text.toString(),
            "shipping_first_name" to shippingFirstName.text.toString(),
            "shipping_last_name" to shippingLastName.text.toString(),
            "shipping_company" to shippingCompany.text.toString(),
            "shipping_address_1" to shippingAddress1.text.toString(),
            "shipping_address_2" to shippingAddress2.text.toString(),
            "shipping_city" to shippingCity.text.toString(),
            "shipping_state" to shippingState.text.toString(),
            "shipping_postcode" to shippingPostcode.text.toString(),
            "shipping_phone" to shippingPhone.text.toString()
        )
    }

    private suspend fun sendAddressDataToServer(formData: Map<String, String>): String? {
        return withContext(Dispatchers.IO) {
            val urlString = "https://emergencyleash.com/wp-content/plugins/access-app/push/settings-my-addresses.php"
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

                connection.outputStream.use { outputStream ->
                    BufferedWriter(OutputStreamWriter(outputStream, "UTF-8")).use { writer ->
                        writer.write(postData.toString())
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
                Log.e("Error", "Failed to send address data", e)
                null
            }
        }
    }

    private fun handleServerResponse(response: String) {
        try {
            val jsonObject = JSONObject(response)
            val message = jsonObject.optString("message", "An error occurred.")
            showResponseDialog(message)
        } catch (e: Exception) {
            Log.e("Error", "Failed to parse server response", e)
            showResponseDialog("An error occurred.")
        }
    }

    private fun showResponseDialog(message: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.server_response)
            .setMessage(message)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
                // Refresh the activity
                finish()
                startActivity(intent)
            }
            .setCancelable(false)
            .show()
    }

    private fun getUserIDFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("userID", -1)
    }

    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            progressBar.visibility = View.VISIBLE
            dimOverlay.visibility = View.VISIBLE
        } else {
            progressBar.visibility = View.GONE
            dimOverlay.visibility = View.GONE
        }

        // Disable UI interaction while loading
        setInteractionEnabled(!isLoading)
    }

    private fun setInteractionEnabled(isEnabled: Boolean) {
        billingFirstName.isEnabled = isEnabled
        billingLastName.isEnabled = isEnabled
        billingCompany.isEnabled = isEnabled
        billingAddress1.isEnabled = isEnabled
        billingAddress2.isEnabled = isEnabled
        billingCity.isEnabled = isEnabled
        billingState.isEnabled = isEnabled
        billingPostcode.isEnabled = isEnabled
        billingPhone.isEnabled = isEnabled
        billingEmail.isEnabled = isEnabled
        shippingFirstName.isEnabled = isEnabled
        shippingLastName.isEnabled = isEnabled
        shippingCompany.isEnabled = isEnabled
        shippingAddress1.isEnabled = isEnabled
        shippingAddress2.isEnabled = isEnabled
        shippingCity.isEnabled = isEnabled
        shippingState.isEnabled = isEnabled
        shippingPostcode.isEnabled = isEnabled
        shippingPhone.isEnabled = isEnabled
        findViewById<Button>(R.id.saveButton).isEnabled = isEnabled
        findViewById<Button>(R.id.cancelButton).isEnabled = isEnabled
    }
}
