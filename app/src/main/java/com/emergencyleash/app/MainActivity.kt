package com.emergencyleash.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.messaging.FirebaseMessaging
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var callbackManager: CallbackManager
    private lateinit var googleLoginLauncher: ActivityResultLauncher<Intent>

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        refreshToken()

        // Initialize UI elements
        val usernameEditText = findViewById<TextInputEditText>(R.id.usernameEditText)
        val passwordEditText = findViewById<TextInputEditText>(R.id.passwordEditText)
        val loginButton = findViewById<Button>(R.id.loginButton)
        val loginViaGoogleButton = findViewById<LinearLayout>(R.id.loginViaGoogleButton)
        val loginViaFacebookButton = findViewById<LinearLayout>(R.id.loginViaFacebookButton)
        val forgotPasswordText = findViewById<TextInputEditText?>(R.id.passwordEditText)
        val forgotPasswordLink = findViewById<TextView>(R.id.forgotPasswordText)

        val registerButton = findViewById<Button>(R.id.registerButton)

        val webClientId = getString(R.string.stored_web_client_id) // 410514541495-fs5d2jloqs9scn6gv1607dsbc6qvjt67.apps.googleusercontent.com
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()


        // Initialize Google Sign-In client
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Initialize Facebook CallbackManager
        callbackManager = CallbackManager.Factory.create()

        // Register Activity Result Launcher for Google Sign-In
        googleLoginLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null) {
                    val idToken = account.idToken
                    if (idToken != null) {
                        authenticateWithGoogleIdToken(idToken)
                    } else {
                        Log.e(TAG, "Google ID token is null")
                        Toast.makeText(this, "Failed to get Google ID token.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: ApiException) {
                Log.e(TAG, "Google Sign-In failed", e)
                Toast.makeText(this, "Google Sign-In failed.", Toast.LENGTH_SHORT).show()
            }
        }

        // Set onClick listeners
        loginButton.setOnClickListener {
            val username = usernameEditText.text.toString().trim()
            val password = passwordEditText.text.toString().trim()

            if (username.isNotEmpty() && password.isNotEmpty()) {
                authenticateUser(username, password)
            } else {
                Toast.makeText(this@MainActivity, "Please enter both username and password", Toast.LENGTH_SHORT).show()
            }
        }

        loginViaGoogleButton.setOnClickListener {
            signInWithGoogle()
        }

        loginViaFacebookButton.setOnClickListener {
            loginWithFacebook()
        }

        registerButton.setOnClickListener {
            // Start the RegistrationActivity
            val intent = Intent(this, RegistrationActivity::class.java)
            startActivity(intent)
        }

                /* ─── Forgot-Password flow (external site with confirmation modal) ─── */
                forgotPasswordLink.setOnClickListener {
                        AlertDialog.Builder(this)
                            .setTitle(getString(R.string.dialog_leave_app_title))
                            .setMessage(getString(R.string.dialog_leave_app_message))
                            .setPositiveButton(getString(R.string.continue_button)) { dialog, _ ->
                                    val url = "https://emergencyleash.com/forgotpassword"
                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    startActivity(browserIntent)
                                    dialog.dismiss()
                                }
                            .setNegativeButton(android.R.string.cancel, null)
                            .show()
                    }

        getUserSubFromPrefs()
        checkUserIdAndTokenAndNavigate()
    }

    /**
     * Initiates Google Sign-In flow
     */
    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleLoginLauncher.launch(signInIntent)
    }

    /**
     * Authenticates a Google ID token with our WordPress endpoint and then
     * drops the right breadcrumbs so the rest of the app knows we're logged in.
     */
    private fun authenticateWithGoogleIdToken(idToken: String) {
        // Build a tiny JSON snowball with exactly what the endpoint expects.
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = JSONObject().apply {
            put("id_token", idToken)   // this is the star of the show
        }.toString().toRequestBody(mediaType)

        // Same body, new home (our custom VoxUrsa WP endpoint).
        val request = Request.Builder()
            .url("https://emergencyleash.com/wp-json/custom-auth/google/")
            .post(requestBody)
            .build()

        // Keep it snappy but patient enough for the network to have feelings.
        val client = OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Google → WP auth network fail: ${e.message}", e)
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Network error. Try again.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use { resp ->
                    val bodyText = resp.body?.string().orEmpty()

                    if (!resp.isSuccessful) {
                        Log.e(TAG, "Google → WP auth HTTP ${resp.code}: $bodyText")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Sign-in failed (${resp.code}).", Toast.LENGTH_SHORT).show()
                        }
                        return
                    }

                    try {
                        val json = JSONObject(bodyText)

                        // If error=false we’re good to continue.
                        val isError = json.optBoolean("error", true)
                        val message = json.optString("message", "Unknown response")
                        if (isError) {
                            Log.e(TAG, "Google → WP auth app error: $message")
                            runOnUiThread { Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show() }
                            return
                        }

                        // Scoop up the goodies we care about.
                        val user      = json.optJSONObject("user") ?: JSONObject()
                        val userId    = user.optLong("id", -1L)
                        val email     = user.optString("email")
                        val firstName = user.optString("first_name")
                        val lastName  = user.optString("last_name")
                        val display   = user.optString("display")
                        val wpNonce   = json.optString("wp_rest_nonce") // tucked away for future REST calls

                        Log.d(TAG, "Google → WP auth success: id=$userId, email=$email, name=$display, nonce=$wpNonce")

                        // This is where we make the rest of the app happy:
                        // we write the same keys the password flow writes into "prefs".
                        // We don’t have full address data here, so we drop friendly blanks.
                        val userIdInt = if (userId > Int.MAX_VALUE) Int.MAX_VALUE else userId.toInt()
                        saveUserIDToPrefs(
                            userId = userIdInt,
                            memberSince = "",   // no value from Google flow
                            name = if (display.isNotBlank()) display else "$firstName $lastName",
                            firstName = firstName,
                            lastName = lastName,
                            address1 = "",
                            address2 = "",
                            city = "",
                            state = "",
                            zip = 0,
                            subscription = 0
                        )

                        // Hand the Firebase token to WP (same helper we already use).
                        val firebaseToken = getTokenFromPrefs()
                        if (firebaseToken.isNotEmpty()) {
                            storeFirebaseTokenInWordpress(userIdInt, firebaseToken)
                        } else {
                            // If we don’t have one yet, our usual refresh will populate it shortly.
                            Log.d(TAG, "Firebase token not yet available; storing will retry later.")
                        }

                        // Also drop the nonce into a tiny "session" pocket (optional but nice).
                        getSharedPreferences("session", Context.MODE_PRIVATE).edit()
                            .putString("wp_rest_nonce", wpNonce)
                            .putString("email", email)
                            .putString("display", display)
                            .apply()

                        // Showtime—let the existing navigator do its thing.
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Welcome, ${display.ifBlank { firstName }}!", Toast.LENGTH_SHORT).show()
                            checkUserIdAndTokenAndNavigate()
                        }

                    } catch (t: Throwable) {
                        Log.e(TAG, "Google → WP auth parse error: $t\n$bodyText")
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Unexpected response. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        })
    }



    /**
     * Initiates Facebook login flow
     */
    private fun loginWithFacebook() {
        LoginManager.getInstance().logInWithReadPermissions(
            this,
            listOf("email", "public_profile")
        )
        LoginManager.getInstance().registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                val accessToken = result.accessToken.token
                val expiresIn = (result.accessToken.expires.time / 1000).toInt()
                authenticateWithWordPressForFacebook(accessToken, expiresIn)
            }

            override fun onCancel() {
                Toast.makeText(this@MainActivity, "Facebook login canceled.", Toast.LENGTH_SHORT).show()
            }

            override fun onError(error: FacebookException) {
                Log.e(TAG, "Facebook login failed", error)
                Toast.makeText(this@MainActivity, "Facebook login failed.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Authenticates Facebook access token with WordPress
     */
    private fun authenticateWithWordPressForFacebook(accessToken: String, expiresIn: Int) {
        // Build the JSON object as the plugin expects
        val jsonBody = JSONObject().apply {
            put("access_token", accessToken)
            put("expires_in", expiresIn)
            put("token_type", "bearer")
        }

        // Convert the JSON object to a RequestBody
        val requestBody = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

        val request = Request.Builder()
            .url("https://emergencyleash.com/wp-json/nextend-social-login/v1/facebook/get_user")
            .post(requestBody)
            .build()

        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "Failed to authenticate with WordPress via Facebook", e)
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful && responseBody != null) {
                    Log.d(TAG, "Authentication successful with WordPress via Facebook: $responseBody")
                    // Handle successful authentication
                    // Parse the responseBody and save user details as needed
                } else {
                    Log.e(TAG, "Authentication failed with WordPress via Facebook: $responseBody")
                }
            }
        })
    }

    /**
     * Handles standard username/password authentication
     */
    private fun authenticateUser(username: String, password: String) {
        val queue = Volley.newRequestQueue(this)
        val url = "https://emergencyleash.com/wp-content/plugins/access-app/index.php"

        val stringRequest = object : StringRequest(Method.POST, url,
            Response.Listener { response ->
                try {
                    val jsonResponse = JSONObject(response)
                    val userId = jsonResponse.getInt("ID")
                    val memberSince = jsonResponse.getString("member_since")
                    val name = jsonResponse.getString("name")
                    val firstName = jsonResponse.getString("first_name")
                    val lastName = jsonResponse.getString("last_name")
                    val address1 = jsonResponse.getString("address1")
                    val address2 = jsonResponse.getString("address2")
                    val city = jsonResponse.getString("city")
                    val state = jsonResponse.getString("state")
                    val zip = jsonResponse.getInt("zip")
                    val subscription = jsonResponse.getInt("subscription")

                    Toast.makeText(this, "Login successful! Welcome, $name", Toast.LENGTH_LONG).show()

                    // Store user info
                    saveUserIDToPrefs(
                        userId,
                        memberSince,
                        name,
                        firstName,
                        lastName,
                        address1,
                        address2,
                        city,
                        state,
                        zip,
                        subscription
                    )

                    val firebaseToken = getTokenFromPrefs()
                    storeFirebaseTokenInWordpress(userId, firebaseToken)

                    // NEW: Defer navigation until SharedPreferences are applied
                    Handler(Looper.getMainLooper()).post {
                        checkUserIdAndTokenAndNavigate()
                    }

                } catch (e: Exception) {
                    Toast.makeText(this, "Invalid login credentials", Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "Error parsing response: $response", e)
                }
            },
            Response.ErrorListener {
                Toast.makeText(this, "Network error or invalid credentials", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): Map<String, String> {
                val params = HashMap<String, String>()
                params["user_login"] = username
                params["user_pass"] = password
                return params
            }
        }

        queue.add(stringRequest)
    }

    /**
     * Refreshes the Firebase token and saves it in SharedPreferences
     */
    private fun refreshToken() {
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d(TAG, "Forced Refreshed token: $token")
                saveTokenToPrefs(token ?: "")
            } else {
                Log.e(TAG, "Failed to fetch the token", task.exception)
            }
        }
    }

    /**
     * Stores the Firebase token in WordPress
     */
    private fun storeFirebaseTokenInWordpress(userId: Int, firebaseToken: String) {
        val request = Request.Builder()
            .url("https://emergencyleash.com/wp-json/custom/v1/store-token-here/")
            .post(
                FormBody.Builder()
                    .add("user_id", userId.toString())
                    .add("firebase_token", firebaseToken)
                    .build()
            ).build()
        val client = OkHttpClient()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "Failed to store the token in WordPress")
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                val responseBody = response.body?.string()
                response.use {
                    if (response.isSuccessful) {
                        Log.d(TAG, "Firebase token stored successfully in WordPress $responseBody")
                    } else {
                        Log.d(TAG, "Failed to store the token in WordPress: $response $responseBody")
                    }
                }
            }
        })
    }

    /**
     * Checks for stored user ID and Firebase token, then navigates to the next screen if valid
     */
    private fun checkUserIdAndTokenAndNavigate() {
        val userId = getUserIDFromPrefs()
        val firebaseToken = getTokenFromPrefs()
        if (userId != -1 && firebaseToken.isNotEmpty()) {
            val intent = Intent(this, LoggedInActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    /**
     * Saves the Firebase token in SharedPreferences
     */
    private fun saveTokenToPrefs(token: String) {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("firebaseToken", token)
        editor.apply()
    }

    // Saves the user's ID and details in SharedPreferences
    private fun saveUserIDToPrefs(
        userId: Int,            // <- was userID
        memberSince: String,
        name: String,
        firstName: String,
        lastName: String,
        address1: String,
        address2: String,
        city: String,
        state: String,
        zip: Int,
        subscription: Int
    ) {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putInt("userID", userId)  // key stays "userID", value comes from userId var
        editor.putString("MemberSince", memberSince)
        editor.putString("Name", name)
        editor.putString("FirstName", firstName)
        editor.putString("LastName", lastName)
        editor.putString("Address1", address1)
        editor.putString("Address2", address2)
        editor.putString("City", city)
        editor.putString("State", state)
        editor.putInt("Zip", zip)
        editor.putInt("Subscription", subscription)
        editor.apply()
    }


    /**
     * Retrieves the Firebase token from SharedPreferences
     */
    private fun getTokenFromPrefs(): String {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("firebaseToken", "") ?: ""
    }

    /**
     * Retrieves the user ID from SharedPreferences
     */
    private fun getUserIDFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("userID", -1)  // -1 as default, indicating no userID stored
    }

    private fun getUserSubFromPrefs() {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val userReturnedSub = sharedPreferences.getInt("Subscription", 0)  // 0 as default, indicating no user Subscription Exists
        Log.d(TAG, "User Subscription: $userReturnedSub")
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Pass the activity result to the Facebook callback manager
        callbackManager.onActivityResult(requestCode, resultCode, data)
    }
}
