package com.emergencyleash.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.emergencyleash.app.adapters.PetSubscriptionListAdapter
import com.google.firebase.messaging.Constants
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONException
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*

class LoggedInActivity : AppCompatActivity() {

    private lateinit var myPetsListAdapter: MyPetsListAdapter
    private lateinit var recyclerView: RecyclerView
    private lateinit var myPetsProgressBar: ProgressBar
    private lateinit var emptyPetsText: TextView
    private val requestQueue by lazy { Volley.newRequestQueue(this) }
    private lateinit var bannerViewPager: ViewPager2
    private lateinit var bannerAdapter: BannerAdapter
    private lateinit var editProfileLayout: LinearLayout
    private lateinit var addContactsSection: LinearLayout
    private lateinit var btNewPet: Button
    private lateinit var notificationsPermissionButton: LinearLayout

    private val handler = Handler(Looper.getMainLooper())
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            val itemCount = bannerAdapter.itemCount
            val nextItem = (bannerViewPager.currentItem + 1) % itemCount
            bannerViewPager.currentItem = nextItem
            handler.postDelayed(this, 3000) // Change slide every 3 seconds
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_AccessApp)
        super.onCreate(savedInstanceState)

        // If you previously called DarkModeHelper, it's removed to prevent re-launch loops:
        // DarkModeHelper.applyDarkMode(DarkModeHelper.isDarkModeEnabled(this))

        setContentView(R.layout.activity_logged_in)

        val navigationFragment = NavigationFragment.newInstance("Home")
        supportFragmentManager.beginTransaction()
            .replace(R.id.navigationFragmentContainer, navigationFragment)
            .commit()

        recyclerView = findViewById(R.id.my_pets_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        Log.d("LoggedInActivity", "RecyclerView initialized")

        myPetsProgressBar = findViewById(R.id.myPetsProgressBar)
        emptyPetsText = findViewById(R.id.emptyPetsText)

        // Initialize editProfileLayout
        editProfileLayout = findViewById(R.id.editProfileLayout)
        editProfileLayout.setOnClickListener {
            startActivity(Intent(this, SettingsMyAccountActivity::class.java))
        }

        // Initialize addContactsSection
        addContactsSection = findViewById(R.id.addContactsSection)
        addContactsSection.setOnClickListener {
            startActivity(Intent(this, SettingsMyContactsActivity::class.java))
        }

        // Initially, show the ProgressBar and hide the RecyclerView and emptyPetsText
        myPetsProgressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyPetsText.visibility = View.GONE

        // Existing adapter for the main "My Pets" list
        myPetsListAdapter = MyPetsListAdapter(emptyList()) { pet ->
            val userID = getUserIDFromPrefs().toString()
            val petID = pet.pet_ID

            // Log the values for debugging
            Log.d("LoggedInActivity", "Navigating to PetDetailsActivity with userID: $userID, petID: $petID")

            val intent = Intent(this, PetDetailsActivity::class.java)
            intent.putExtra("userID", userID)  // Pass the userID
            intent.putExtra("pet_ID", petID)   // Pass the pet_ID
            startActivity(intent)
        }
        recyclerView.adapter = myPetsListAdapter
        Log.d("LoggedInActivity", "Adapter set to RecyclerView")

        val userID = getUserIDFromPrefs().toString()

        // 1) Fetch user info (profile + subscription) in one request
        fetchUserFullInfo(userID.toInt())

        // 2) Fetch the pet list
        fetchPetsData(userID)

        displayDate()
        // We still keep the old fetchSubscriptionData call if you want,
        // but it's largely redundant now if the new endpoint also returns subscription.
        // You can remove it if you don't need it anymore.
        fetchSubscriptionData(userID.toInt())

        // We load info from SharedPreferences into the UI (will update again once fetchUserFullInfo finishes)
        changeInfo()

        showWelcomeDialogIfFirstTime()

        notificationsPermissionButton = findViewById(R.id.addPermissionsSection)
        if (!areNotificationsEnabled()) {
            notificationsPermissionButton.visibility = View.VISIBLE
        } else {
            notificationsPermissionButton.visibility = View.GONE
        }

        btNewPet = findViewById(R.id.btNewPet)
        btNewPet.setOnClickListener {
            val intent = Intent(this, IntroVideoActivity::class.java)
            startActivity(intent)
        }

        // Set up the banner ViewPager
        bannerViewPager = findViewById(R.id.bannerViewPager)
        val banners = listOf(
            R.drawable.becomeamember,
            R.drawable.becomeamember,
            R.drawable.becomeamember
        )
        bannerAdapter = BannerAdapter(this, banners) { banner ->
            Toast.makeText(this, "Banner clicked: $banner", Toast.LENGTH_SHORT).show()
        }
        bannerViewPager.adapter = bannerAdapter
        handler.postDelayed(autoScrollRunnable, 3000) // Start auto-scrolling after 3 seconds

        notificationsPermissionButton.setOnClickListener {
            openNotificationSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        handler.postDelayed(autoScrollRunnable, 3000) // Resume auto-scrolling

        if (!areNotificationsEnabled()) {
            Log.d("Notifications Permissions:", "Notifications not enabled")
            notificationsPermissionButton.visibility = View.VISIBLE
        } else {
            Log.d("Notifications Permissions:", "Notifications enabled")
            notificationsPermissionButton.visibility = View.GONE
        }

        // Re-fetch user info each time we come back (if desired)
        fetchUserFullInfo(getUserIDFromPrefs())
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(autoScrollRunnable) // Stop auto-scrolling
    }

    //--------------------------------------------------------------------------------------------
    // Show an AlertDialog with a RecyclerView using PetSubscriptionListAdapter
    //--------------------------------------------------------------------------------------------
    private fun showSubscriptionDialogWithAdapter(petsList: List<Pet>) {
        // Inflate the custom layout for the dialog
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_pet_subscription, null)
        val subscriptionRecycler = dialogView.findViewById<RecyclerView>(R.id.subscriptionRecyclerView)
        val cancelBtn = dialogView.findViewById<Button>(R.id.subscriptionCancelBtn)
        val okBtn = dialogView.findViewById<Button>(R.id.subscriptionOkBtn)

        val subscriptionAdapter = PetSubscriptionListAdapter(emptyList()) { selectedPet ->
            selectedPetForSubscription = selectedPet
        }

        // Set up the RecyclerView
        subscriptionRecycler.layoutManager = LinearLayoutManager(this)
        subscriptionRecycler.adapter = subscriptionAdapter

        // Load our list into the adapter
        subscriptionAdapter.updateData(petsList)

        // Build AlertDialog
        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)  // user must tap either Cancel or OK
        val alertDialog = builder.create()

        // When "Cancel" is clicked
        cancelBtn.setOnClickListener {
            alertDialog.dismiss()
            selectedPetForSubscription = null
        }

        // When "OK" is clicked
        okBtn.setOnClickListener {
            if (selectedPetForSubscription != null) {
                val petID = selectedPetForSubscription!!.pet_ID
                activatePlusSubscription(petID) // Our existing function
            } else {
                Toast.makeText(this, "No pet selected.", Toast.LENGTH_SHORT).show()
            }
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    private var selectedPetForSubscription: Pet? = null

    private fun showWelcomeDialogIfFirstTime() {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val hasLoggedInBefore = sharedPreferences.getBoolean("hasLoggedInBefore", false)
        val doNotRemindMe = sharedPreferences.getBoolean("doNotRemindMe", false)

        if (doNotRemindMe || hasLoggedInBefore) {
            return
        }
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialog_welcome, null)
        builder.setView(dialogLayout)
        val alertDialog = builder.create()
        alertDialog.show()
        val okButton = dialogLayout.findViewById<Button>(R.id.okButton)
        val doNotRemindMeCheckbox = dialogLayout.findViewById<CheckBox>(R.id.doNotRemindMeCheckbox)
        okButton.setOnClickListener {
            if (doNotRemindMeCheckbox.isChecked) {
                sharedPreferences.edit().putBoolean("doNotRemindMe", true).apply()
            } else {
                sharedPreferences.edit().putBoolean("hasLoggedInBefore", true).apply()
            }
            alertDialog.dismiss()
        }
    }

    private fun fetchPetsData(userID: String) {
        Log.d("Fetch Started", "Fetching pets data for userID: $userID")
        val url = "https://emergencyleash.com/wp-content/plugins/access-app/pull/my-pets-list.php"

        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                Log.d("Server Response", "Full response: $response")
                try {
                    val gson = Gson()
                    val listType = object : TypeToken<List<Pet>>() {}.type
                    val jsonResponse = JSONObject(response)
                    val myPetsList: List<Pet> = gson.fromJson(jsonResponse.getJSONArray("pets").toString(), listType)
                    myPetsListAdapter.updateData(myPetsList)

                    myPetsProgressBar.visibility = View.GONE
                    if (myPetsList.isEmpty()) {
                        emptyPetsText.visibility = View.VISIBLE
                        recyclerView.visibility = View.GONE
                    } else {
                        emptyPetsText.visibility = View.GONE
                        recyclerView.visibility = View.VISIBLE
                    }

                } catch (e: JSONException) {
                    Log.e("Fetch Error", "Error parsing JSON: ${e.message}")
                    Toast.makeText(this, "Error parsing data: ${e.message}", Toast.LENGTH_SHORT).show()
                    myPetsProgressBar.visibility = View.GONE
                    recyclerView.visibility = View.GONE
                    emptyPetsText.visibility = View.VISIBLE
                    emptyPetsText.text = getString(R.string.error_parsing_data)
                }
            },
            { error ->
                // Existing error log line
                Log.e("Fetch Error", "Error fetching data: ${error.message}")

                // Extended error logging
                error.networkResponse?.let {
                    val statusCode = it.statusCode
                    val responseBody = String(it.data, Charsets.UTF_8)
                    Log.e("Fetch Error", "Status code: $statusCode")
                    Log.e("Fetch Error", "Body: $responseBody")
                }
                Log.e("Fetch Error", "Volley error type: ${error.javaClass.simpleName}")
                Log.e("Fetch Error", "Cause: ${error.cause}")

                Toast.makeText(this, "Error fetching data: ${error.message}", Toast.LENGTH_SHORT).show()
                myPetsProgressBar.visibility = View.GONE
                recyclerView.visibility = View.GONE
                emptyPetsText.visibility = View.VISIBLE
                emptyPetsText.text = getString(R.string.error_fetching_data)
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["userID"] = userID
                return params
            }

            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers["Content-Type"] = "application/x-www-form-urlencoded"
                return headers
            }
        }

        // Show the ProgressBar while the request is in-flight
        myPetsProgressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyPetsText.visibility = View.GONE

        // Add the request to the Volley queue
        requestQueue.add(stringRequest)
    }

    private fun areNotificationsEnabled(): Boolean {
        return NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun openNotificationSettings() {
        val intent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
        }
        startActivity(intent)
    }

    private fun displayDate() {
        val date = Calendar.getInstance().time
        val formatter: DateFormat = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.US)
        val today = formatter.format(date)
        val textView = findViewById<TextView>(R.id.todayText)
        textView.text = getString(R.string.today_date, today)
    }

    data class UserInfo(
        val userID: Int,
        val memberSince: String?,
        val name: String?,
        val firstName: String?,
        val lastName: String?,
        val address1: String?,
        val address2: String?,
        val city: String?,
        val state: String?,
        val zip: Int,
        val subscriptionName: String?
    )

    private fun getUserInfoFromPrefs(): UserInfo {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return UserInfo(
            userID = sharedPreferences.getInt("userID", -1),
            memberSince = sharedPreferences.getString("MemberSince", "Null"),
            name = sharedPreferences.getString("Name", "Null"),
            firstName = sharedPreferences.getString("FirstName", "Null"),
            lastName = sharedPreferences.getString("LastName", "Null"),
            address1 = sharedPreferences.getString("Address1", "Null"),
            address2 = sharedPreferences.getString("Address2", "Null"),
            city = sharedPreferences.getString("City", "Null"),
            state = sharedPreferences.getString("State", "Null"),
            zip = sharedPreferences.getInt("Zip", 0),
            subscriptionName = sharedPreferences.getString("SubscriptionName", "")
        )
    }

    private fun changeInfo() {
        val userInfo = getUserInfoFromPrefs()

        val memberSinceTV = findViewById<TextView>(R.id.member_sinceT)
        memberSinceTV.text = getString(R.string.member_since, userInfo.memberSince)

        val firstNameTV = findViewById<TextView>(R.id.first_nameT)
        firstNameTV.text = userInfo.firstName

        val lastNameTV = findViewById<TextView>(R.id.last_nameT)
        lastNameTV.text = userInfo.lastName

        val address1TV = findViewById<TextView>(R.id.address1T)
        address1TV.text = userInfo.address1

        val address2TV = findViewById<TextView>(R.id.address2T)
        address2TV.text = userInfo.address2

        val cityTV = findViewById<TextView>(R.id.cityT)
        cityTV.text = userInfo.city

        val stateTV = findViewById<TextView>(R.id.stateT)
        stateTV.text = userInfo.state

        val zipTV = findViewById<TextView>(R.id.zipT)
        zipTV.text = userInfo.zip.toString()

        val subscriptionNameTV = findViewById<TextView>(R.id.subscriptionNameT)
        if (userInfo.subscriptionName.isNullOrEmpty()) {
            subscriptionNameTV.text = "Loading..."
        } else {
            subscriptionNameTV.text = userInfo.subscriptionName
        }
    }

    //--------------------------------------------------------------------------------------------
    // Check if there's an unassigned subscription, then show our custom dialog if needed
    //--------------------------------------------------------------------------------------------
    private fun checkPlusSubscription(userID: String) {
        val url = "https://emergencyleash.com/wp-content/plugins/access-app/pull/plus-subscription-pet-list.php"

        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                Log.d("checkPlusSubscription", "Response: $response")
                try {
                    val jsonObject = JSONObject(response)
                    val petsArray = jsonObject.getJSONArray("pets")
                    if (petsArray.length() > 0) {
                        val gson = Gson()
                        val listType = object : TypeToken<List<Pet>>() {}.type
                        val petsList: List<Pet> = gson.fromJson(petsArray.toString(), listType)
                        showSubscriptionDialogWithAdapter(petsList)
                    } else {
                        Log.d("chasSubscription", "No unassigned subscription.")
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error parsing subscription data", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e("checkPlusSubscription", "Error: ${error.message}")
                Toast.makeText(this, "Error checking subscription: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return hashMapOf("user_ID" to userID)
            }

            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf("Content-Type" to "application/x-www-form-urlencoded")
            }
        }
        requestQueue.add(stringRequest)
    }

    private fun activatePlusSubscription(petID: String) {
        val url = "https://emergencyleash.com/wp-content/plugins/access-app/push/plus-subscription-is-active.php"

        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                Log.d("activatePlusSubscription", "Response: $response")
                try {
                    val jsonObject = JSONObject(response)
                    val result = jsonObject.getInt("result")
                    val message = jsonObject.getString("message")
                    if (result == 1) {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        fetchPetsData(getUserIDFromPrefs().toString()) // Refresh pet list
                    } else {
                        Toast.makeText(this, "Error: $message", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error parsing activation response", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e("activatePlusSubscription", "Error: ${error.message}")
                Toast.makeText(this, "Error activating subscription: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return hashMapOf("pet_ID" to petID)
            }

            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf("Content-Type" to "application/x-www-form-urlencoded")
            }
        }
        requestQueue.add(stringRequest)
    }

    private fun getUserIDFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("userID", -1)
    }

    /**
     * Fetches subscription data from the server and updates SharedPreferences.
     * (You can remove this if the new user-full-info endpoint already returns the subscription.)
     */
    private fun fetchSubscriptionData(userID: Int) {
        val url = "https://emergencyleash.com/wp-content/plugins/access-app/pull/settings-subscription.php"
        var subscriptionNameDisplay = "Loading Subscription.."

        val stringRequest = object : StringRequest(
            Method.POST, url,
            { response ->
                try {
                    val jsonObject = JSONObject(response)
                    val subscriptionObject = jsonObject.optJSONObject("subscription")
                    if (subscriptionObject != null) {
                        val subscriptionName = subscriptionObject.optString("subscription_name", "No Subscription")

                        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
                        sharedPreferences.edit()
                            .putString("SubscriptionName", subscriptionName)
                            .apply()

                        when (subscriptionName) {
                            "Emergency Leash Plus" -> {
                                subscriptionNameDisplay = "$subscriptionName (One Tag)"
                                checkPlusSubscription(userID.toString())
                                bannerViewPager.visibility = View.GONE
                            }
                            "Emergency Leash Pro" -> {
                                subscriptionNameDisplay = "$subscriptionName (Multiple Tags)"
                                bannerViewPager.visibility = View.GONE
                            }
                            "Emergency Leash Tag" -> {
                                subscriptionNameDisplay = subscriptionName
                                bannerViewPager.visibility = View.VISIBLE
                            }
                            else -> {
                                // Do nothing special
                            }
                        }

                        val subscriptionNameTV = findViewById<TextView>(R.id.subscriptionNameT)
                        subscriptionNameTV.text = subscriptionNameDisplay
                        Log.d("FetchSubscription", subscriptionNameDisplay)
                    } else {
                        Log.e("FetchSubscription", "No subscription data found.")
                    }
                } catch (e: JSONException) {
                    Log.e("FetchSubscription", "Error parsing subscription data: ${e.message}")
                    Toast.makeText(this, "Error parsing subscription data.", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e("FetchSubscription", "Error fetching subscription data: ${error.message}")
                Toast.makeText(this, "Error fetching subscription data.", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return hashMapOf("userID" to userID.toString())
            }
        }
        Volley.newRequestQueue(this).add(stringRequest)
    }

    // -------------------------------------------------------------------------------------------
    // NEW FUNCTION: fetchUserFullInfo - get user profile and subscription in one volley request
    // -------------------------------------------------------------------------------------------
    private fun fetchUserFullInfo(userID: Int) {
        // If userID is invalid, just skip
        if (userID < 1) {
            Log.e("fetchUserFullInfo", "Invalid userID, cannot fetch info.")
            return
        }

        val url = "https://emergencyleash.com/wp-content/plugins/access-app/pull/user-info.php"

        val stringRequest = object : StringRequest(
            Method.POST, url,
            StringRequest@{ response ->
                Log.d("fetchUserFullInfo", "Response: $response")
                try {
                    val json = JSONObject(response)
                    val errorCode = json.optInt("error", 0)
                    if (errorCode != 0) {
                        // The server reported an error
                        val msg = json.optString("message", "Unknown error")
                        Log.e("fetchUserFullInfo", "Server error: $msg")
                        Toast.makeText(this, "Server error: $msg", Toast.LENGTH_SHORT).show()
                        return@StringRequest
                    }

                    // If we get here, error=0
                    val userObj = json.optJSONObject("user")
                    if (userObj == null) {
                        Log.e("fetchUserFullInfo", "No 'user' object in JSON.")
                        Toast.makeText(this, "Error: No user data returned.", Toast.LENGTH_SHORT).show()
                        return@StringRequest
                    }

                    // 1) Extract the user fields
                    val firstName = userObj.optString("first_name", "")
                    val lastName = userObj.optString("last_name", "")
                    val address1 = userObj.optString("el_pro_address1", "")
                    val address2 = userObj.optString("el_pro_address2", "")
                    val city = userObj.optString("el_pro_city", "")
                    val state = userObj.optString("el_pro_state", "")
                    val zipStr = userObj.optString("el_pro_zip", "0")
                    val zipInt = zipStr.toIntOrNull() ?: 0

                    // 2) Extract subscription info if present
                    val subObj = userObj.optJSONObject("subscription")
                    var subName = ""
                    if (subObj != null) {
                        subName = subObj.optString("subscription_name", "")
                    }

                    // 3) Save everything in SharedPreferences
                    // (You can also store 'memberSince' or 'Name' if your new script includes them.)
                    saveUserInfoToPrefs(
                        userID = userID,
                        firstName = firstName,
                        lastName = lastName,
                        address1 = address1,
                        address2 = address2,
                        city = city,
                        state = state,
                        zip = zipInt,
                        subscriptionName = subName
                    )

                    // 4) Update UI with these new values
                    changeInfo()

                    // 5) Additional subscription checks (similar to what you do in fetchSubscriptionData)
                    when (subName) {
                        "Emergency Leash Plus" -> {
                            // Hide the banner, check plus subscription
                            bannerViewPager.visibility = View.GONE
                            checkPlusSubscription(userID.toString())
                        }

                        "Emergency Leash Pro" -> {
                            bannerViewPager.visibility = View.GONE
                        }

                        "Emergency Leash Tag" -> {
                            bannerViewPager.visibility = View.VISIBLE
                        }
                    }

                } catch (ex: JSONException) {
                    Log.e("fetchUserFullInfo", "JSON parse error: ${ex.message}")
                    Toast.makeText(this, "Error parsing user info: ${ex.message}", Toast.LENGTH_SHORT).show()
                }
            },
            { error ->
                Log.e("fetchUserFullInfo", "Network/Volley error: ${error.message}")
                error.networkResponse?.let {
                    Log.e("fetchUserFullInfo", "Status code: ${it.statusCode}")
                    Log.e("fetchUserFullInfo", "Body: ${String(it.data, Charsets.UTF_8)}")
                }
                Log.e("fetchUserFullInfo", "Volley error type: ${error.javaClass.simpleName}")
                Log.e("fetchUserFullInfo", "Cause: ${error.cause}")
                Toast.makeText(this, "Failed to load user info.", Toast.LENGTH_SHORT).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                return hashMapOf("userID" to userID.toString())
            }
        }

        // Optionally show a small spinner if you want to indicate loading user info
        // For now we won't block the UI since it's quick
        requestQueue.add(stringRequest)
    }

    /**
     * Helper to save user info from the new endpoint into SharedPreferences
     * so we can read it with getUserInfoFromPrefs() and display via changeInfo().
     */
    private fun saveUserInfoToPrefs(
        userID: Int,
        firstName: String,
        lastName: String,
        address1: String,
        address2: String,
        city: String,
        state: String,
        zip: Int,
        subscriptionName: String
    ) {
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putInt("userID", userID)
            .putString("FirstName", firstName)
            .putString("LastName", lastName)
            .putString("Address1", address1)
            .putString("Address2", address2)
            .putString("City", city)
            .putString("State", state)
            .putInt("Zip", zip)
            .putString("SubscriptionName", subscriptionName)
            .apply()
    }
}
