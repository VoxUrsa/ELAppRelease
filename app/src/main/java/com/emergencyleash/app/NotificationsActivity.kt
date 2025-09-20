package com.emergencyleash.app

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class NotificationsActivity : AppCompatActivity() {

    private lateinit var progressBar: View
    private lateinit var dimOverlay: View
    private lateinit var notificationsRecyclerView: RecyclerView
    private val client = OkHttpClient()
    private val notificationsList = mutableListOf<NotificationItem>()
    private lateinit var adapter: NotificationsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_notifications)

        // Initialize UI elements
        progressBar = findViewById(R.id.progressBar)
        dimOverlay = findViewById(R.id.dimOverlay)
        notificationsRecyclerView = findViewById(R.id.notificationsRecyclerView)

        notificationsRecyclerView.layoutManager = LinearLayoutManager(this)
        adapter = NotificationsAdapter(notificationsList) { notification, position ->
            // Handle notification click (expand and mark as read)
            if (notification.active) {
                markAsRead(notification.id, position)
            }
        }
        notificationsRecyclerView.adapter = adapter

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val navigationFragment = NavigationFragment.newInstance("Notifications")
        supportFragmentManager.beginTransaction()
            .replace(R.id.navigationFragmentContainer, navigationFragment)
            .commit()

        // Fetch notifications from server
        val userIdFromPrefs = getUserIDFromPrefs()
        pullNotificationsFromServer(userIdFromPrefs)
    }

    private fun getUserIDFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", MODE_PRIVATE)
        return sharedPreferences.getInt("userID", -1)
    }

    private fun pullNotificationsFromServer(userID: Int) {
        setLoadingState(true) // Show loading state

        val url = "https://emergencyleash.com/wp-content/plugins/access-app/pull/notification-list.php"
        val requestBody = FormBody.Builder()
            .add("userID", userID.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setLoadingState(false) // Hide loading state
                    Toast.makeText(this@NotificationsActivity, "Error fetching notifications", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBodyString = response.body?.string() // Retrieve the raw response body as String

                // 1. Log the entire server response to Logcat changes
                Log.d("NotificationsActivity", "Server Response: $responseBodyString")

                // 2. Proceed with JSON parsing (guard against nulls)
                if (!responseBodyString.isNullOrEmpty()) {
                    val jsonResponse = JSONObject(responseBodyString)
                    val notificationsArray: JSONArray = jsonResponse.getJSONArray("notifications")

                    notificationsList.clear()
                    for (i in 0 until notificationsArray.length()) {
                        val notificationJson = notificationsArray.getJSONObject(i)
                        val notification = NotificationItem(
                            id = notificationJson.getInt("notification_ID"),
                            subject = notificationJson.getString("notification_subject"),
                            message = notificationJson.getString("notification_message"),
                            active = notificationJson.getString("notification_is_active") == "1",
                            age = notificationJson.getString("notification_age"),
                            imageUrl = if (notificationJson.has("notification_image")) {
                                notificationJson.getString("notification_image")
                            } else {
                                null
                            }
                        )
                        notificationsList.add(notification)
                    }

                    runOnUiThread {
                        setLoadingState(false) // Hide loading state
                        adapter.notifyDataSetChanged() // Update the RecyclerView
                    }
                } else {
                    runOnUiThread {
                        setLoadingState(false)
                        Toast.makeText(this@NotificationsActivity, "No response from server", Toast.LENGTH_SHORT).show()
                    }
                }
            }

        })
    }

    private fun setLoadingState(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        dimOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    private fun markAsRead(notificationId: Int, position: Int) {
        // Update the notification's "read" status on the server
        val url = "https://emergencyleash.com/wp-content/plugins/access-app/push/notifications-is-active.php"
        val requestBody = FormBody.Builder()
            .add("notification_ID", notificationId.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@NotificationsActivity, "Error marking notification as read", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    // Mark the notification as read locally and update the view
                    notificationsList[position].active = false
                    adapter.notifyItemChanged(position)

                    Toast.makeText(this@NotificationsActivity, "Notification $notificationId marked as read", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
}