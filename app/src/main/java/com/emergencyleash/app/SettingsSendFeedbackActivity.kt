package com.emergencyleash.app

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class SettingsSendFeedbackActivity : AppCompatActivity() {

    private var hasChanges = false
    private var selectedIssue: String = "Account Problem" // Default to "Account Problem"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_send_feedback)

        // Handle back navigation
        val chevronLeft: ImageView = findViewById(R.id.chevronLeft)
        chevronLeft.setOnClickListener {
            handleBackPress()
        }

        val cancelButton: Button = findViewById(R.id.cancelButton)
        cancelButton.setOnClickListener {
            handleBackPress()
        }

        val messageEditText: EditText = findViewById(R.id.editTextMessage)
        messageEditText.setOnKeyListener { _, _, _ ->
            hasChanges = true
            false
        }

        // Set up CardView click listeners for selection
        val cardOption1: View = findViewById(R.id.cardOption1)
        val cardOption2: View = findViewById(R.id.cardOption2)
        val cardOption3: View = findViewById(R.id.cardOption3)
        val cardOption4: View = findViewById(R.id.cardOption4)
        val cardOption5: View = findViewById(R.id.cardOption5)
        val circleOption1: ImageView = findViewById(R.id.circleOption1)
        val circleOption2: ImageView = findViewById(R.id.circleOption2)
        val circleOption3: ImageView = findViewById(R.id.circleOption3)
        val circleOption4: ImageView = findViewById(R.id.circleOption4)
        val circleOption5: ImageView = findViewById(R.id.circleOption5)

        // Set "Account Problem" as the default selection
        circleOption1.setImageResource(R.drawable.ic_checked_circle)

        cardOption1.setOnClickListener { selectOption("Account Problem", circleOption1, circleOption2, circleOption3, circleOption4, circleOption5) }
        cardOption2.setOnClickListener { selectOption("Bug or Glitches", circleOption2, circleOption1, circleOption3, circleOption4, circleOption5) }
        cardOption3.setOnClickListener { selectOption("App Suggestion", circleOption3, circleOption1, circleOption2, circleOption4, circleOption5) }
        cardOption4.setOnClickListener { selectOption("Feature Question", circleOption4, circleOption1, circleOption2, circleOption3, circleOption5) }
        cardOption5.setOnClickListener { selectOption("Other", circleOption5, circleOption1, circleOption2, circleOption3, circleOption4) }

        // Handle Save button click
        val saveButton: Button = findViewById(R.id.saveButton)
        saveButton.setOnClickListener {
            val message = messageEditText.text.toString()
            val userId = getUserIDFromPrefs()

            if (selectedIssue.isNotBlank() && message.isNotBlank()) {
                sendFeedbackToServer(userId, selectedIssue, message)
                hasChanges = false
            } else {
                Toast.makeText(this, "Please select an issue and fill in the message", Toast.LENGTH_SHORT).show()
            }
        }

        // Set up the OnBackPressedCallback for handling back button events
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })
    }

    private fun selectOption(issue: String, selectedCircle: ImageView, vararg otherCircles: ImageView) {
        selectedIssue = issue
        selectedCircle.setImageResource(R.drawable.ic_checked_circle) // Change to the checked drawable
        otherCircles.forEach { it.setImageResource(R.drawable.ic_circle_outline) } // Change to the empty circle drawable
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

    private fun sendFeedbackToServer(userID: Int, issue: String, message: String) {
        Thread {
            try {
                val url = URL("https://emergencyleash.com/wp-content/plugins/access-app/push/settings-send-feedback.php")
                val postData = "userID=$userID&issue=$issue&message=$message"

                with(url.openConnection() as HttpURLConnection) {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                    doOutput = true
                    outputStream.write(postData.toByteArray())

                    val responseCode = responseCode
                    val responseMessage = inputStream.bufferedReader().use { it.readText() }

                    if (responseCode == HttpURLConnection.HTTP_OK) {
                        Log.d("SettingsSendFeedback", "Server Response: $responseMessage")
                        showResponseDialog(responseMessage)
                    } else {
                        Log.d("SettingsSendFeedback", "Failed to send feedback to server. Response code: $responseCode, Response message: $responseMessage")
                        showResponseDialog("Failed to send feedback to server.")
                    }
                }
            } catch (e: Exception) {
                Log.e("SettingsSendFeedback", "Error sending feedback to server", e)
                showResponseDialog("Error sending feedback to server.")
            }
        }.start()
    }

    private fun showResponseDialog(responseMessage: String) {
        runOnUiThread {
            val cleanResponseMessage = responseMessage.substringAfter("{").prependIndent("{")

            val message = try {
                val jsonResponse = JSONObject(cleanResponseMessage)
                jsonResponse.optString("message", "An error occurred.")
            } catch (e: Exception) {
                Log.e("SettingsSendFeedback", "Error parsing server response", e)
                "An error occurred."
            }

            AlertDialog.Builder(this)
                .setTitle("Thank you for contacting us!")
                .setMessage("Your feedback has been received, and we’ll get back to you within 1-3 business days. We value your input!")
                .setPositiveButton("Okay") { dialog, _ ->
                    dialog.dismiss()
                    // Refresh the activity
                    finish()
                    startActivity(intent)
                }
                .setCancelable(false)
                .show()
        }
    }

}
