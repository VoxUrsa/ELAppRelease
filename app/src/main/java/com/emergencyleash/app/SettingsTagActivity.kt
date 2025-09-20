package com.emergencyleash.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.emergencyleash.app.adapters.TagsAdapter
import com.emergencyleash.app.models.Tag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SettingsTagActivity : AppCompatActivity() {

    private lateinit var tagsRecyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var dimOverlay: View
    private lateinit var tagsAdapter: TagsAdapter
    private var tagsList: MutableList<Tag> = mutableListOf()
    private var initialTagsList: List<Tag> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_tag)

        tagsRecyclerView = findViewById(R.id.tagsRecyclerView)
        progressBar = findViewById(R.id.progressBar)
        dimOverlay = findViewById(R.id.dimOverlay)

        // Set up back navigation using the chevron icon and cancel button
        val chevronLeft: ImageView = findViewById(R.id.chevronLeft)
        chevronLeft.setOnClickListener {
            handleBackPress()
        }

        val orderReplacementButton: Button = findViewById(R.id.orderReplacementButton)
        orderReplacementButton.setOnClickListener {
            showExternalSiteConfirmation()        // <-- ask first
        }

        // Set up RecyclerView
        tagsRecyclerView.layoutManager = LinearLayoutManager(this)
        tagsAdapter = TagsAdapter(tagsList)
        tagsRecyclerView.adapter = tagsAdapter

        // Show loading indicator and load tags data
        showLoading(true)
        CoroutineScope(Dispatchers.IO).launch {
            loadTagsData()
        }

        // Handle back button presses using OnBackPressedCallback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPress()
            }
        })

    }

    private fun handleBackPress() {
        if (hasChanges()) {
            showUnsavedChangesDialog()
        } else {
            finish()
        }
    }

    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this).setTitle(R.string.unsaved_changes_title)
            .setMessage(R.string.unsaved_changes_message)
            .setPositiveButton(R.string.leave) { _, _ ->
                finish()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showLoading(show: Boolean) {
        // Manage visibility of the ProgressBar and DimOverlay
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        dimOverlay.visibility = if (show) View.VISIBLE else View.GONE

        tagsRecyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun getUserIDFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("userID", -1)
    }

    private fun hasChanges(): Boolean {
        // Compare the current tags list with the initial list
        return tagsList != initialTagsList
    }

    private suspend fun loadTagsData() {
        val userID = getUserIDFromPrefs()
        Log.d("SettingsTagActivity", "Retrieved userID: $userID")
        if (userID == -1) {
            Log.e("SettingsTagActivity", "Invalid user ID")
            return
        }

        val urlString =
            "https://emergencyleash.com/wp-content/plugins/access-app/pull/settings-tags.php"
        val postData = "userID=${URLEncoder.encode(userID.toString(), "UTF-8")}"
        Log.d("SettingsTagActivity", "Sending postData: $postData")

        val result = withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = true

                connection.outputStream.use { it.write(postData.toByteArray()) }

                val responseCode = connection.responseCode
                val responseMessage = connection.responseMessage
                Log.d("SettingsTagActivity", "Response Code: $responseCode")
                Log.d("SettingsTagActivity", "Response Message: $responseMessage")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    Log.e(
                        "SettingsTagActivity",
                        "Server returned non-OK status: $responseCode $responseMessage"
                    )
                    null
                }
            } catch (e: Exception) {
                Log.e("SettingsTagActivity", "Failed to load tags data", e)
                null
            }
        }

        withContext(Dispatchers.Main) {
            if (result != null) {
                Log.d("SettingsTagActivity", "Server Response: $result")
                parseAndDisplayTags(result)
            } else {
                Log.d("SettingsTagActivity", "Failed to load data from the server")
                Toast.makeText(this@SettingsTagActivity, "Failed to load tags.", Toast.LENGTH_SHORT)
                    .show()
            }
            showLoading(false)
        }
    }


    private fun parseAndDisplayTags(jsonResponse: String) {
        try {
            val jsonObject = JSONObject(jsonResponse)

            if (jsonObject.has("error")) {
                val message = jsonObject.optString("message", "Unknown error")
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                return
            }

            val tagsJsonArray = jsonObject.optJSONArray("tags")
            if (tagsJsonArray != null) {
                tagsList.clear()
                for (i in 0 until tagsJsonArray.length()) {
                    val tagObject = tagsJsonArray.getJSONObject(i)
                    val tag = Tag(
                        tagID = tagObject.optString("tag_ID", ""),
                        tagNum = tagObject.optString("tag_num", ""),
                        petID = tagObject.optString("pet_ID", ""),
                        petName = tagObject.optString("pet_name", "")
                    )
                    tagsList.add(tag)
                }

                // Store initial tags list for change detection
                initialTagsList = tagsList.map { it.copy() }

                // Notify adapter
                tagsAdapter.notifyDataSetChanged()
            }
        } catch (e: Exception) {
            Log.e("SettingsTagActivity", "Failed to parse JSON response", e)
        }
    }


    // Open web browser with the specified URL when "Get Plus Now" button is clicked
    private fun openEmergencyLeashWebsiteTagReplacement() {
        val url = "https://emergencyleash.com/product/emergency-leash-tag-replacement/"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        }
        // Using a chooser to ensure a valid app can handle the intent
        startActivity(Intent.createChooser(intent, "Open with"))
    }


    /** Play-Store requirement: warn the user before leaving the app */
    private fun showExternalSiteConfirmation() {
        AlertDialog.Builder(this).setTitle(getString(R.string.external_site_title))
            .setMessage(getString(R.string.external_site_message))
            .setPositiveButton(getString(R.string.continue_label)) { _, _ ->
                openEmergencyLeashWebsiteTagReplacement()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }


}