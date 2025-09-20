package com.emergencyleash.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.*
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class RegistrationActivity : AppCompatActivity() {

    private val client = OkHttpClient()
    companion object { private const val TAG = "RegistrationActivity" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_registration)

        val etUsername: EditText = findViewById(R.id.etUsername)
        val etEmail: EditText = findViewById(R.id.etEmail)
        val tilPassword: TextInputLayout = findViewById(R.id.tilPassword)
        val etPassword: TextInputEditText = findViewById(R.id.etPassword)
        val etFirstName: EditText = findViewById(R.id.etFirstName)
        val etLastName: EditText = findViewById(R.id.etLastName)
        val etAddress1: EditText = findViewById(R.id.etAddress1)
        val etAddress2: EditText = findViewById(R.id.etAddress2)
        val etCity: EditText = findViewById(R.id.etCity)
        val etZip: EditText = findViewById(R.id.etZip)
        val autoState: MaterialAutoCompleteTextView = findViewById(R.id.autoState)
        val btnRegister: Button = findViewById(R.id.btnRegister)

        // Map of states to their 2-letter codes
        val stateMap = mapOf(
            "Alabama - AL" to "AL", "Alaska - AK" to "AK", "Arizona - AZ" to "AZ",
            "Arkansas - AR" to "AR", "California - CA" to "CA", "Colorado - CO" to "CO",
            "Connecticut - CT" to "CT", "Delaware - DE" to "DE", "District of Columbia - DC" to "DC",
            "Florida - FL" to "FL", "Georgia - GA" to "GA", "Hawaii - HI" to "HI",
            "Idaho - ID" to "ID", "Illinois - IL" to "IL", "Indiana - IN" to "IN",
            "Iowa - IA" to "IA", "Kansas - KS" to "KS", "Kentucky - KY" to "KY",
            "Louisiana - LA" to "LA", "Maine - ME" to "ME", "Maryland - MD" to "MD",
            "Massachusetts - MA" to "MA", "Michigan - MI" to "MI", "Minnesota - MN" to "MN",
            "Mississippi - MS" to "MS", "Missouri - MO" to "MO", "Montana - MT" to "MT",
            "Nebraska - NE" to "NE", "Nevada - NV" to "NV", "New Hampshire - NH" to "NH",
            "New Jersey - NJ" to "NJ", "New Mexico - NM" to "NM", "New York - NY" to "NY",
            "North Carolina - NC" to "NC", "North Dakota - ND" to "ND", "Ohio - OH" to "OH",
            "Oklahoma - OK" to "OK", "Oregon - OR" to "OR", "Pennsylvania - PA" to "PA",
            "Rhode Island - RI" to "RI", "South Carolina - SC" to "SC", "South Dakota - SD" to "SD",
            "Tennessee - TN" to "TN", "Texas - TX" to "TX", "Utah - UT" to "UT",
            "Vermont - VT" to "VT", "Virginia - VA" to "VA", "Washington - WA" to "WA",
            "West Virginia - WV" to "WV", "Wisconsin - WI" to "WI", "Wyoming - WY" to "WY"
        )

                // Drop-down shows only "Alabama - AL"… etc.; typing disabled
                val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, stateMap.keys.toList())

                autoState.setAdapter(adapter)
                /* Disable manual text entry; force selection */
                autoState.keyListener = null
                autoState.setOnClickListener { autoState.showDropDown() }
                /* Zip limited to 5 chars in UI as extra safety */
                etZip.filters = arrayOf<InputFilter>(LengthFilter(5))

        btnRegister.setOnClickListener {
                        val username   = etUsername.text.toString().trim()
                        val email      = etEmail.text.toString().trim()
                        val password   = etPassword.text.toString().trim()
                        val firstName  = etFirstName.text.toString().trim()
                        val lastName   = etLastName.text.toString().trim()
                        val address1   = etAddress1.text.toString().trim()
                        val address2   = etAddress2.text.toString().trim()   // optional
                        val city       = etCity.text.toString().trim()
                        val zip        = etZip.text.toString().trim()
                        val selectedStateName = autoState.text.toString().trim()
                        val stateCode = stateMap[selectedStateName] ?: ""

                        /* ───  DEBUG LOGGING  ─── */
                        Log.d(TAG, "username='$username' empty=${username.isEmpty()}")
                        Log.d(TAG, "email='$email' empty=${email.isEmpty()}")
                        Log.d(TAG, "password='${"*".repeat(password.length)}' empty=${password.isEmpty()}")
                        Log.d(TAG, "firstName='$firstName' empty=${firstName.isEmpty()}")
                        Log.d(TAG, "lastName='$lastName' empty=${lastName.isEmpty()}")
                        Log.d(TAG, "address1='$address1' empty=${address1.isEmpty()}")
                        Log.d(TAG, "address2='$address2' empty=${address2.isEmpty()}  (optional)")
                        Log.d(TAG, "city='$city' empty=${city.isEmpty()}")
                        Log.d(TAG, "stateName='$selectedStateName'  stateCode='$stateCode'  empty=${stateCode.isEmpty()}")
                        Log.d(TAG, "zip='$zip' empty=${zip.isEmpty()}")

            // Check if all required fields are filled
                        if (username.isNotEmpty() && email.isNotEmpty() && password.isNotEmpty() &&
                                  firstName.isNotEmpty() && lastName.isNotEmpty() &&
                                  address1.isNotEmpty() &&                       // Address 2 is NOT required
                                  city.isNotEmpty() && stateCode.isNotEmpty() && zip.isNotEmpty()
                            ) {
                if (!isValidZipCode(zip)) {
                    Toast.makeText(this, getString(R.string.invalid_zip_code), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                registerUser(username, email, password, firstName, lastName, address1, address2, city, stateCode, zip)
            } else {
                Toast.makeText(this, getString(R.string.fill_required_fields), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isValidZipCode(zip: String): Boolean {
        val zipPattern = Regex("^[0-9]{5}$")
        return zipPattern.matches(zip)
    }

    private fun registerUser(
        username: String, email: String, password: String,
        firstName: String, lastName: String, address1: String, address2: String,
        city: String, state: String, zip: String
    ) {
        val json = JSONObject().apply {
            put("username", username)
            put("email", email)
            put("password", password)
            put("first_name", firstName)
            put("last_name", lastName)
            put("address1", address1)
            put("address2", address2)
            put("city", city)
            put("state", state)
            put("zip", zip)
        }

        val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://emergencyleash.com/wp-json/custom/v1/register")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Log.e("RegistrationActivity", "Registration failed: ${e.message}")
                    Toast.makeText(this@RegistrationActivity, getString(R.string.registration_failed_network), Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        showRegistrationSuccessDialog()
                    } else {
                        val errorBody = response.body?.string()
                        val errorMessage = extractErrorMessage(errorBody)
                        Log.e("RegistrationActivity", "Registration failed with code: ${response.code}")
                        Toast.makeText(this@RegistrationActivity, errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun showRegistrationSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_registration_success_title)
            .setMessage(R.string.dialog_registration_success_message)
            .setPositiveButton(R.string.dialog_button_ok) { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun extractErrorMessage(errorBody: String?): String {
        return try {
            val jsonError = JSONObject(errorBody ?: "")
            jsonError.optString("message", getString(R.string.registration_failed_generic))
        } catch (e: Exception) {
            getString(R.string.registration_failed_generic)
        }
    }
}
