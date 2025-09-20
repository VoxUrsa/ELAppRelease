package com.emergencyleash.app

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.RequestQueue
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.json.JSONException
import org.json.JSONObject

class PetsActivity : AppCompatActivity() {

    private lateinit var myPetsListAdapter: MyPetsListAdapter
    private lateinit var emptyView: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var requestQueue: RequestQueue
    private lateinit var progressBar: View
    private lateinit var dimOverlay: View
    private lateinit var btNewPet: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pets)

        // Initialize RecyclerView
        recyclerView = findViewById(R.id.my_pets_list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        emptyView = findViewById(R.id.emptyView)
        btNewPet = findViewById(R.id.btNewPet)

        btNewPet.setOnClickListener {
            val intent = Intent(this, IntroVideoActivity::class.java)
            startActivity(intent)
        }
        myPetsListAdapter = MyPetsListAdapter(emptyList()) { pet ->
            // Get userID and pet_ID
            val userID = getUserIDFromPrefs().toString()
            val petID = pet.pet_ID.toString()

            // Log the values
            Log.d(
                "PetsActivity",
                "Navigating to PetDetailsActivity with userID: $userID, petID: $petID"
            )

            // Handle navigation to PetDetailsActivity
            val intent = Intent(this, PetDetailsActivity::class.java)
            intent.putExtra("userID", userID)  // Pass the userID
            intent.putExtra("pet_ID", petID)   // Pass the pet_ID
            startActivity(intent)
        }

        recyclerView.adapter = myPetsListAdapter

        // Initialize loading views
        progressBar = findViewById(R.id.progressBar)
        dimOverlay = findViewById(R.id.dimOverlay)

        // Initialize RequestQueue for Volley
        requestQueue = Volley.newRequestQueue(this)

        // Fetch and update pets data
        val userID = getUserIDFromPrefs().toString()
        fetchPetsData(userID)

        val navigationFragment = NavigationFragment.newInstance("Pets")
        supportFragmentManager.beginTransaction()
            .replace(R.id.navigationFragmentContainer, navigationFragment).commit()
    }

    // Fetch the pets data using a request to the server
    private fun fetchPetsData(userID: String) {
        Log.d("PetsActivity", "Fetching pets data for user: $userID")
        setLoadingState(true) // Show loading animation

        val url = "https://emergencyleash.com/wp-content/plugins/access-app/pull/my-pets-list.php"

        // Create a request using StringRequest
        val stringRequest = object : StringRequest(Method.POST, url, { response ->
            setLoadingState(false) // Hide loading animation
            Log.d("PetsActivity", "Response received: $response")
            try {
                val json = JSONObject(response)
                val petsJA = json.optJSONArray("pets")     // ← safe-null read

                val myPetsList: List<Pet> = if (petsJA != null && petsJA.length() > 0) {
                    val listType = object : TypeToken<List<Pet>>() {}.type
                    Gson().fromJson(petsJA.toString(), listType)
                } else {
                    emptyList()
                }

                myPetsListAdapter.updateData(myPetsList)
                toggleEmptyState(myPetsList.isEmpty())
                Log.d("PetsActivity", "Parsed Pets List: $myPetsList")

            } catch (e: JSONException) {
                Log.e("PetsActivity", "Error parsing JSON: ${e.message}")
                toggleEmptyState(true)
            }
        }, { error ->
            setLoadingState(false) // Hide loading animation
            Log.e("PetsActivity", "Error fetching data: ${error.message}")
            Toast.makeText(this, "Error fetching data: ${error.message}", Toast.LENGTH_SHORT).show()
        }) {
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

        // Add request to the queue
        requestQueue.add(stringRequest)
    }

    // Set loading animation visibility
    private fun setLoadingState(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        dimOverlay.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    /** Show placeholder if list is empty, otherwise show RecyclerView */
    private fun toggleEmptyState(isEmpty: Boolean) {
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    // Get the user ID from shared preferences
    private fun getUserIDFromPrefs(): Int {
        val sharedPreferences = getSharedPreferences("prefs", MODE_PRIVATE)
        return sharedPreferences.getInt("userID", -1)  // Default is -1 if no userID is stored
    }
}
