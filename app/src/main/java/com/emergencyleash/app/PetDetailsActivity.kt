package com.emergencyleash.app

import android.content.ContentValues
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ImageSlot(val slotName: String, var imageUrl: String)

class PetDetailsActivity : AppCompatActivity(), FieldEditFragment.FieldEditListener,
    OnPetStatusChangedListener, EnterZipCodeFragment.ZipCodeListener {

    // For documents
    private var isDocumentsSectionExpanded = false
    private lateinit var documentUrls: MutableList<String>
    private lateinit var documentsAdapter: DocumentsAdapter
    private var documentSlots: MutableList<DocumentSlot> = mutableListOf()
    private var currentSubscriptionName: String = ""

    // Data class for document slots
    data class DocumentSlot(val slotName: String, var documentUrl: String)

    // Variables to keep track of changes and gallery expansion state
    private var hasChanges = false
    private var isGallerySectionExpanded = false

    private lateinit var imageUrls: MutableList<String>
    private lateinit var galleryAdapter: GalleryAdapter

    // Variable for the Pet Photo URL that will be used in the flyer
    private var petPhotoUrl: String? = null
    private var petData: JSONObject? = null


    // Variables to store userID and petID
    private var userID: String? = null
    private var petID: String? = null

    // OkHttpClient instance
    private val client = OkHttpClient()

    //Scans Variables
    private var isScansSectionExpanded = false
    private lateinit var scansRecyclerView: RecyclerView
    private lateinit var scansAdapter: ScansAdapter
    private val scansList = mutableListOf<ScanItem>()
    private var isScansEmpty = false  // We'll set this when we parse the scans


    // Loading indicator views
    private lateinit var progressBar: View
    private lateinit var dimOverlay: View
    private lateinit var contentContainer: View

    private var isInitializingPrivacySwitches = false
    private var petBirthday: String = ""
    private lateinit var sendAlertCard: CardView
    private lateinit var sendAlertText: TextView

    val maxImages = when (currentSubscriptionName) {
        "Emergency Leash Pro" -> 40
        "Emergency Leash Plus" -> 30
        else -> 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pet_details)

        // Initialize progressBar, dimOverlay, and contentContainer
        progressBar = findViewById(R.id.progressBar)
        dimOverlay = findViewById(R.id.dimOverlay)
        sendAlertCard = findViewById<CardView>(R.id.sendAlertCard)
        sendAlertText = sendAlertCard.findViewById<TextView>(R.id.sendAlertText)
        sendAlertCard.setOnClickListener {
            val enterZipCodeFragment = EnterZipCodeFragment()
            enterZipCodeFragment.show(supportFragmentManager, "EnterZipCodeFragment")
        }


        contentContainer = findViewById(R.id.contentContainer)
        contentContainer.visibility = View.GONE // Hide content initially
        // Get userID and petID from intent extras
        userID = intent.getStringExtra("userID")
        petID = intent.getStringExtra("pet_ID")
        Log.d("PetDetailsActivity", "Received userID: $userID, petID: $petID")


        // Fetch the measurement preference
        fetchMeasurementPreference(userID)
        // Fetch and display the pet details using userID and petID

        loadAllData(userID, petID)

        // Handle back navigation
        val chevronLeft: ImageView = findViewById(R.id.chevronLeft)
        chevronLeft.setOnClickListener {
            handleBackPress()
        }

        val createFlyerCard: CardView = findViewById(R.id.createFlyerCard)
        createFlyerCard.setOnClickListener {
            petData?.let { data ->
                generateFlyerPDF(data) // Pass the fetched data to generateFlyerPDF
            } ?: Toast.makeText(this, "Data not loaded yet. Please wait.", Toast.LENGTH_SHORT)
                .show()
        }

        val reportLostPetCard: CardView = findViewById(R.id.markPetAsLostCard)
        reportLostPetCard.setOnClickListener {
            // Here we call the reportLostPet function to show the ReportLostPetFragment
            reportLostPet()
        }

        // Handle the "Mark Pet As Found" card click
        val markPetAsFoundCard: CardView = findViewById(R.id.markPetAsFoundCard)
        markPetAsFoundCard.setOnClickListener {
            // Show the ReportFoundPetFragment
            reportFoundPet()
        }

        // Handle the About, Social, General Info, Health, Privacy Settings, and Gallery sections expand/collapse logic
        handleAboutSection()/*        handleSocialSection()*/
        handleGeneralInfoSection()
        handleHealthSection()
        handlePrivacySettingsSection()
        handleGallerySection()
        handleDocumentsSection()
        // Set up click listeners for editable fields
        setupFieldClickListeners()
        handleScansSection()

    }


    /**
     * Sets the loading state of the activity.
     */
    private fun setLoadingState(isLoading: Boolean) {
        if (isLoading) {
            // Show a progress indicator (e.g., a ProgressBar)
            progressBar.visibility = View.VISIBLE
            // Optionally, show an overlay to prevent user interaction
            dimOverlay.visibility = View.VISIBLE
            // Disable the sendAlertCard to prevent multiple clicks
            sendAlertCard.isEnabled = false
            sendAlertCard.isClickable = false
            sendAlertCard.alpha = 0.5f // Optional: visually indicate that it's disabled
        } else {
            // Hide the progress indicator
            progressBar.visibility = View.GONE
            // Hide the overlay
            dimOverlay.visibility = View.GONE
            // Re-enable the sendAlertCard
            sendAlertCard.isEnabled = true
            sendAlertCard.isClickable = true
            sendAlertCard.alpha = 1.0f
        }
    }


    /**
     * Sets up click listeners for all editable fields.
     * When a field is clicked, a bottom sheet dialog will appear for editing.
     */
    private fun setupFieldClickListeners() {
        // Pet Name
        findViewById<ConstraintLayout>(R.id.petNameContainer).setOnClickListener {
            // Find the TextView within the container and get its text
            val petNameTextView = findViewById<TextView>(R.id.petName)
            val currentValue = petNameTextView.text.toString()
            showEditFragment("pet_name", currentValue, "Edit Pet Name")
        }

        // Pet Type (change to container click listener)
        findViewById<LinearLayout>(R.id.petTypeContainer).setOnClickListener {
            val currentValue = findViewById<TextView>(R.id.petType).text.toString()
            showEditFragment("pet_type", currentValue, "Edit Pet Type")
        }

        // Pet Gender (change to container click listener)
        findViewById<LinearLayout>(R.id.petGenderContainer).setOnClickListener {
            val currentValue = findViewById<TextView>(R.id.petGender).text.toString()
            val value = when (currentValue) {
                "Unknown" -> "0"
                "Male" -> "1"
                "Female" -> "2"
                else -> "0"
            }
            showEditFragment("pet_gender", value, "Edit Pet Gender")
        }

        // Pet Birthday (change to container click listener)
        findViewById<LinearLayout>(R.id.petAgeContainer).setOnClickListener {
            val currentValue = findViewById<TextView>(R.id.petAge).text.toString()
            val currentBirthday =
                getCurrentBirthday() // Implement this method to retrieve the actual birthday
            showEditFragment("pet_birthday", currentBirthday, "Edit Pet Birthday")
        }


        // Address1
        findViewById<TextView>(R.id.address1).setOnClickListener {
            val currentValue = (it as TextView).text.toString()
            showEditFragment("address1", currentValue, "Edit Address Line 1")
        }

        // Address2
        findViewById<TextView>(R.id.address2).setOnClickListener {
            val currentValue = (it as TextView).text.toString()
            showEditFragment("address2", currentValue, "Edit Address Line 2")
        }

        // City
        findViewById<TextView>(R.id.city).setOnClickListener {
            val currentValue = (it as TextView).text.toString()
            showEditFragment("city", currentValue, "Edit City")
        }

        // State
        findViewById<TextView>(R.id.state).setOnClickListener {
            val currentValue = (it as TextView).text.toString()
            showEditFragment("state", currentValue, "Edit State")
        }

        // Zip
        findViewById<TextView>(R.id.zip).setOnClickListener {
            val currentValue = (it as TextView).text.toString()
            showEditFragment("zip", currentValue, "Edit Zip Code")
        }

        // About Section
        findViewById<LinearLayout>(R.id.aboutContentContainer).setOnClickListener {
            val currentValue = findViewById<TextView>(R.id.aboutContent).text.toString()
            showEditFragment("pet_notes", currentValue, "Edit About Section")
        }

        /*        // Social Media Fields
                findViewById<TextView>(R.id.addInstagram).setOnClickListener {
                    val currentValue = (it as TextView).text.toString()
                    showEditFragment("pet_social_instagram", currentValue, "Edit Instagram")
                }

                findViewById<TextView>(R.id.addTikTok).setOnClickListener {
                    val currentValue = (it as TextView).text.toString()
                    showEditFragment("pet_social_tiktok", currentValue, "Edit TikTok")
                }

                findViewById<TextView>(R.id.addFacebook).setOnClickListener {
                    val currentValue = (it as TextView).text.toString()
                    showEditFragment("pet_social_facebook", currentValue, "Edit Facebook")
                }*/

        // General Info Fields
        // Breed Section
        findViewById<ConstraintLayout>(R.id.breedContainer).setOnClickListener {
            val currentValue = findViewById<TextView>(R.id.petBreed).text.toString()
            showEditFragment("pet_breed", currentValue, "Edit Breed")
        }

// Weight Section
        findViewById<ConstraintLayout>(R.id.weightContainer).setOnClickListener {
            val currentValue = findViewById<TextView>(R.id.petWeight).text.toString()
            showEditFragment("pet_weight", currentValue, "Edit Weight")
        }

        // Spayed Section
        findViewById<ConstraintLayout>(R.id.spayedContainer).setOnClickListener {
            val currentValue = findViewById<TextView>(R.id.petSpayed).text.toString()
            showEditFragment("pet_neutspay", currentValue, "Edit Spayed/Neutered")
        }


        // Health Section Fields
//        findViewById<TextView>(R.id.vetContactInfo).setOnClickListener {
//            val currentValue = (it as TextView).text.toString()
//            showEditFragment("vet_contact", currentValue, "Edit Veterinarian Contact")
//        }


        val vetContactContainer = findViewById<LinearLayout>(R.id.vetContactContentContainer)
        vetContactContainer.setOnClickListener {
            showVetContactEditFragment()
        }
        findViewById<ImageView>(R.id.editVetContactIcon).setOnClickListener {
            showVetContactEditFragment()
        }



        findViewById<TextView>(R.id.microchipInfo).setOnClickListener {

            val currentValue = (it as TextView).text.toString()

            showEditFragment("pet_microchip", currentValue, "Edit Microchip Number")

        }



        findViewById<TextView>(R.id.tattooInfo).setOnClickListener {
            val currentValue = (it as TextView).text.toString()
            showEditFragment("pet_tat", currentValue, "Edit Tattoo Information")
        }



        findViewById<TextView>(R.id.allergiesInfo).setOnClickListener {

            val currentValue = (it as TextView).text.toString()

            showEditFragment("pet_allergies", currentValue, "Edit Allergies")

        }



        findViewById<TextView>(R.id.healthConditionsInfo).setOnClickListener {

            val currentValue = (it as TextView).text.toString()

            showEditFragment("pet_health_conditions", currentValue, "Edit Health Conditions")

        }



        findViewById<TextView>(R.id.medicationsInfo).setOnClickListener {

            val currentValue = (it as TextView).text.toString()

            showEditFragment("pet_medications", currentValue, "Edit Medications")

        }



        findViewById<TextView>(R.id.vaccinationsInfo).setOnClickListener {

            val currentValue = (it as TextView).text.toString()

            showEditFragment("pet_vaccinations", currentValue, "Edit Vaccinations")

        }

        // Note: Click listeners for Health subsections are handled in handleSubsectionToggle()
    }

    private fun showVetContactEditFragment() {
        val vetName = findViewById<TextView>(R.id.vetName).text.toString()
        val vetAddress1 = findViewById<TextView>(R.id.vetAddress1).text.toString()
        val vetAddress2 = findViewById<TextView>(R.id.vetAddress2).text.toString()
        val vetCityStateZip = findViewById<TextView>(R.id.vetCityStateZip).text.toString()
        val vetEmail = findViewById<TextView>(R.id.vetEmail).text.toString()

        val editFragment = FieldEditFragment.newInstance(
            "vet_contact", // Field identifier
            "$vetName\n$vetAddress1\n$vetAddress2\n$vetCityStateZip\n$vetEmail", // Initial value formatted
            "Edit Veterinarian Contact" // Title
        )
        editFragment.show(supportFragmentManager, "editVetContact")
    }


    /**
     * Updates a single privacy setting on the server.
     *
     * Instead of user-facing Toasts, this version writes success/failure info
     * to Logcat so devs can trace updates without bothering the user.
     */
    private fun updatePrivacySettingOnServer(fieldId: String, newValue: String) {
        val url = "https://emergencyleash.com/wp-content/plugins/access-app/push/pet-profile.php"

        // Work-around for PHP empty() quirk: if we mean “false/0” send “2”.
        val adjustedValue = if (newValue == "0") "2" else newValue

        val requestBody = FormBody.Builder().add("userID", userID ?: "").add("pet_ID", petID ?: "")
            .add(fieldId, adjustedValue).build()

        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Silent to user; log for devs
                Log.e(
                    TAG,
                    "Privacy update FAILED  field=$fieldId  value=$newValue  error=${e.message}"
                )
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body?.string()
                if (response.isSuccessful) {
                    Log.d(
                        TAG,
                        "Privacy update OK     field=$fieldId  value=$newValue  response=$responseText"
                    )
                } else {
                    Log.e(
                        TAG,
                        "Privacy update ERROR  ${response.code}  field=$fieldId  response=$responseText"
                    )
                }
            }
        })
    }

    /**
     * Handles the Privacy Settings section expand/collapse logic.
     */
    private fun handlePrivacySettingsSection() {
        var isPrivacySettingsExpanded = false
        val privacySettingsHeader = findViewById<LinearLayout>(R.id.privacySettingsHeader)
        val privacySettingsContent = findViewById<LinearLayout>(R.id.privacySettingsContent)
        val privacySettingsChevronIcon = findViewById<ImageView>(R.id.privacySettingsChevronIcon)

        // Initially hide the content
        privacySettingsContent.visibility = View.GONE
        privacySettingsChevronIcon.setImageResource(R.drawable.ic_chevron_down)

        privacySettingsHeader.setOnClickListener {
            if (isPrivacySettingsExpanded) {
                privacySettingsContent.visibility = View.GONE
                privacySettingsChevronIcon.setImageResource(R.drawable.ic_chevron_down)
            } else {
                privacySettingsContent.visibility = View.VISIBLE
                privacySettingsChevronIcon.setImageResource(R.drawable.ic_chevron_up)
            }
            isPrivacySettingsExpanded = !isPrivacySettingsExpanded
        }
    }

    /**
     * Handles the About section expand/collapse logic.
     */
    private fun handleAboutSection() {
        var isAboutSectionExpanded = false
        val aboutHeader = findViewById<LinearLayout>(R.id.aboutHeader)
        val aboutContentContainer = findViewById<LinearLayout>(R.id.aboutContentContainer)
        val chevronIcon = findViewById<ImageView>(R.id.chevronIcon)

        // Initially hide the content
        aboutContentContainer.visibility = View.GONE
        chevronIcon.setImageResource(R.drawable.ic_chevron_down)

        aboutHeader.setOnClickListener {
            if (isAboutSectionExpanded) {
                aboutContentContainer.visibility = View.GONE
                chevronIcon.setImageResource(R.drawable.ic_chevron_down)
            } else {
                aboutContentContainer.visibility = View.VISIBLE
                chevronIcon.setImageResource(R.drawable.ic_chevron_up)
            }
            isAboutSectionExpanded = !isAboutSectionExpanded
        }
    }

    /*    */
    /**
     * Handles the Social section expand/collapse logic.
     *//*
    private fun handleSocialSection() {
        var isSocialSectionExpanded = false
        val socialHeader = findViewById<LinearLayout>(R.id.socialHeader)
        val socialContent = findViewById<LinearLayout>(R.id.socialContent)
        val chevronIcon = findViewById<ImageView>(R.id.socialChevronIcon)

        // Initially hide the content
        socialContent.visibility = View.GONE
        chevronIcon.setImageResource(R.drawable.ic_chevron_down)

        socialHeader.setOnClickListener {
            if (isSocialSectionExpanded) {
                socialContent.visibility = View.GONE
                chevronIcon.setImageResource(R.drawable.ic_chevron_down)
            } else {
                socialContent.visibility = View.VISIBLE
                chevronIcon.setImageResource(R.drawable.ic_chevron_up)
            }
            isSocialSectionExpanded = !isSocialSectionExpanded
        }
    }*/

    /**
     * Handles the General Info section expand/collapse logic.
     */
    private fun handleGeneralInfoSection() {
        var isGeneralInfoExpanded = false
        val generalInfoHeader = findViewById<LinearLayout>(R.id.generalInfoHeader)
        val generalInfoContent = findViewById<LinearLayout>(R.id.generalInfoContent)
        val generalInfoChevronIcon = findViewById<ImageView>(R.id.generalInfoChevronIcon)

        // Initially hide the content
        generalInfoContent.visibility = View.GONE
        generalInfoChevronIcon.setImageResource(R.drawable.ic_chevron_down)

        generalInfoHeader.setOnClickListener {
            if (isGeneralInfoExpanded) {
                generalInfoContent.visibility = View.GONE
                generalInfoChevronIcon.setImageResource(R.drawable.ic_chevron_down)
            } else {
                generalInfoContent.visibility = View.VISIBLE
                generalInfoChevronIcon.setImageResource(R.drawable.ic_chevron_up)
            }
            isGeneralInfoExpanded = !isGeneralInfoExpanded
        }
    }

    /**
     * Handles the Health section expand/collapse logic.
     */
    /**
     * Handles the Health section expand/collapse logic.
     */
    /**
     * Handles the Health section expand/collapse logic.
     */
    private fun handleHealthSection() {
        var isHealthSectionExpanded = false
        val healthHeader = findViewById<LinearLayout>(R.id.healthHeader)
        val healthContent = findViewById<LinearLayout>(R.id.healthContent)
        val healthChevronIcon = findViewById<ImageView>(R.id.healthChevronIcon)

        // Initially hide the content
        healthContent.visibility = View.GONE
        healthChevronIcon.setImageResource(R.drawable.ic_chevron_down)

        healthHeader.setOnClickListener {
            if (isHealthSectionExpanded) {
                healthContent.visibility = View.GONE
                healthChevronIcon.setImageResource(R.drawable.ic_chevron_down)
            } else {
                healthContent.visibility = View.VISIBLE
                healthChevronIcon.setImageResource(R.drawable.ic_chevron_up)
            }
            isHealthSectionExpanded = !isHealthSectionExpanded
        }

        // Sub-sections inside Health card

        // Handle veterinarian contact (multiple TextViews in the container)
        handleSubsectionToggle(
            headerId = R.id.vetContactHeader,
            contentContainerId = R.id.vetContactContentContainer,
            chevronId = R.id.vetContactChevronIcon,
            editIconId = R.id.editVetContactIcon,
            fieldId = "vet_contact",
            editTitle = "Edit Veterinarian Contact",
            fallbackText = "No information for Veterinarian found."
        )

        // Handle microchip (single TextView)
        handleSubsectionToggle(
            headerId = R.id.microchipHeader,
            contentContainerId = R.id.microchipContentContainer,
            chevronId = R.id.microchipChevronIcon,
            editIconId = R.id.editMicrochipIcon,
            fieldId = "pet_microchip",
            editTitle = "Edit Microchip Number",
            fallbackText = "No information for Microchip Number found."
        )

        // Handle tattoo (single TextView)
        handleSubsectionToggle(
            headerId = R.id.tattooHeader,
            contentContainerId = R.id.tattooContentContainer,
            chevronId = R.id.tattooChevronIcon,
            editIconId = R.id.editTattooIcon,
            fieldId = "pet_tat",
            editTitle = "Edit Tattoo",
            fallbackText = "No information for Tattoo found."
        )

        // Handle allergies (single TextView)
        handleSubsectionToggle(
            headerId = R.id.allergiesHeader,
            contentContainerId = R.id.allergiesContentContainer,
            chevronId = R.id.allergiesChevronIcon,
            editIconId = R.id.editAllergiesIcon,
            fieldId = "pet_allergies",
            editTitle = "Edit Allergies",
            fallbackText = "No information for Allergies found."
        )

        // Handle health conditions (single TextView)
        handleSubsectionToggle(
            headerId = R.id.healthConditionsHeader,
            contentContainerId = R.id.healthConditionsContentContainer,
            chevronId = R.id.healthConditionsChevronIcon,
            editIconId = R.id.editHealthConditionsIcon,
            fieldId = "pet_health_conditions",
            editTitle = "Edit Health Conditions",
            fallbackText = "No information for Health Conditions found."
        )

        // Handle medications (single TextView)
        handleSubsectionToggle(
            headerId = R.id.medicationsHeader,
            contentContainerId = R.id.medicationsContentContainer,
            chevronId = R.id.medicationsChevronIcon,
            editIconId = R.id.editMedicationsIcon,
            fieldId = "pet_medications",
            editTitle = "Edit Medications",
            fallbackText = "No information for Medications found."
        )

        // Handle vaccinations (single TextView)
        handleSubsectionToggle(
            headerId = R.id.vaccinationsHeader,
            contentContainerId = R.id.vaccinationsContentContainer,
            chevronId = R.id.vaccinationsChevronIcon,
            editIconId = R.id.editVaccinationsIcon,
            fieldId = "pet_vaccinations",
            editTitle = "Edit Vaccinations",
            fallbackText = "No information for Vaccinations found."
        )
    }


    /**
     * Handles the toggling of sub-sections within the Health section.
     */

    private fun handleSubsectionToggle(
        headerId: Int,
        contentContainerId: Int,
        chevronId: Int,
        editIconId: Int,
        fieldId: String,
        editTitle: String,
        fallbackText: String
    ) {
        var isExpanded = false
        val header = findViewById<LinearLayout>(headerId)
        val contentContainer = findViewById<LinearLayout>(contentContainerId)
        val chevronIcon = findViewById<ImageView>(chevronId)
        val editIcon = findViewById<ImageView>(editIconId)

        // Initially hide the content
        contentContainer.visibility = View.GONE
        chevronIcon.setImageResource(R.drawable.ic_chevron_down)

        // Handle expand/collapse
        header.setOnClickListener {
            if (isExpanded) {
                contentContainer.visibility = View.GONE
                chevronIcon.setImageResource(R.drawable.ic_chevron_down)
            } else {
                contentContainer.visibility = View.VISIBLE
                chevronIcon.setImageResource(R.drawable.ic_chevron_up)
            }
            isExpanded = !isExpanded
        }

        // Retrieve the current value for the field from the content
        val currentValue = getCurrentFieldValue(fieldId)

        // Set click listener for content container to trigger edit
        contentContainer.setOnClickListener {
            val valueToEdit = currentValue.ifEmpty { "" }
            showEditFragment(fieldId, valueToEdit, editTitle)
        }

        // Set click listener for edit icon
        editIcon.setOnClickListener {
            val valueToEdit = currentValue.ifEmpty { "" }
            showEditFragment(fieldId, valueToEdit, editTitle)
        }
    }

    private fun getCurrentFieldValue(fieldId: String): String {
        return when (fieldId) {
            "vet_contact" -> {
                val vetName = findViewById<TextView>(R.id.vetName).text.toString()
                val vetAddress1 = findViewById<TextView>(R.id.vetAddress1).text.toString()
                val vetAddress2 = findViewById<TextView>(R.id.vetAddress2).text.toString()
                val vetCityStateZip = findViewById<TextView>(R.id.vetCityStateZip).text.toString()
                val vetEmail = findViewById<TextView>(R.id.vetEmail).text.toString()

                // Construct a multi-line string for veterinarian contact information
                listOfNotNull(vetName.takeIf { it.isNotEmpty() },
                    vetAddress1.takeIf { it.isNotEmpty() },
                    vetAddress2.takeIf { it.isNotEmpty() },
                    vetCityStateZip.takeIf { it.isNotEmpty() },
                    vetEmail.takeIf { it.isNotEmpty() }).joinToString("\n")
            }

            "pet_microchip" -> findViewById<TextView>(R.id.microchipInfo).text.toString()
            "pet_tat" -> findViewById<TextView>(R.id.tattooInfo).text.toString()
            "pet_allergies" -> findViewById<TextView>(R.id.allergiesInfo).text.toString()
            "pet_health_conditions" -> findViewById<TextView>(R.id.healthConditionsInfo).text.toString()
            "pet_medications" -> findViewById<TextView>(R.id.medicationsInfo).text.toString()
            "pet_vaccinations" -> findViewById<TextView>(R.id.vaccinationsInfo).text.toString()
            else -> ""
        }
    }


    private fun getCurrentBirthday(): String {
        return petBirthday
    }

    /**
     * Handles the Gallery section expand/collapse logic.
     */
    private fun handleGallerySection() {
        val galleryHeader = findViewById<LinearLayout>(R.id.galleryHeader)
        val galleryContent = findViewById<RecyclerView>(R.id.galleryContent)
        val galleryChevronIcon = findViewById<ImageView>(R.id.galleryChevronIcon)

        // Initially hide the content
        galleryContent.visibility = View.GONE
        galleryChevronIcon.setImageResource(R.drawable.ic_chevron_down)

        galleryHeader.setOnClickListener {
            if (isGallerySectionExpanded) {
                galleryContent.visibility = View.GONE
                galleryChevronIcon.setImageResource(R.drawable.ic_chevron_down)
            } else {
                galleryContent.visibility = View.VISIBLE
                galleryChevronIcon.setImageResource(R.drawable.ic_chevron_up)
            }
            isGallerySectionExpanded = !isGallerySectionExpanded
        }
    }

    /**
     * Handles the back button press. If there are unsaved changes, prompts the user.
     */
    private fun handleBackPress() {
        if (hasChanges) {
            showUnsavedChangesDialog()
        } else {
            finish()
        }
    }

    private fun calculateAge(birthday: Date): Pair<Int, Int> {
        val birthCalendar = Calendar.getInstance().apply { time = birthday }
        val today = Calendar.getInstance()

        var years = today.get(Calendar.YEAR) - birthCalendar.get(Calendar.YEAR)
        var months = today.get(Calendar.MONTH) - birthCalendar.get(Calendar.MONTH)

        if (months < 0) {
            years--
            months += 12
        }

        return Pair(years, months)
    }

    private fun calculateAndFormatAge(birthday: String): String {
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val birthdayDate = formatter.parse(birthday)
            if (birthdayDate != null) {
                val age = calculateAge(birthdayDate)
                "${age.first} yrs ${age.second} mos"
            } else {
                "Unknown"
            }
        } catch (e: Exception) {
            Log.e("PetDetailsActivity", "Date parsing error: ${e.message}")
            "Unknown"
        }
    }

    /**
     * Shows a dialog warning the user about unsaved changes.
     */
    private fun showUnsavedChangesDialog() {
        AlertDialog.Builder(this).setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Do you want to leave without saving?")
            .setPositiveButton("Leave") { _, _ -> finish() }.setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Displays the FieldEditFragment as a bottom sheet for editing fields.
     */
    private fun showEditFragment(fieldId: String, initialValue: String, title: String) {
        val editFragment = FieldEditFragment.newInstance(fieldId, initialValue, title)
        editFragment.show(supportFragmentManager, "editField")
    }

    /**
     * Called when a field is saved in the FieldEditFragment.
     * Updates the UI and sends the updated value to the server.
     */

    override fun onFieldSaved(fieldId: String, newValue: String) {
        hasChanges = true

        when (fieldId) {
            "pet_name" -> findViewById<TextView>(R.id.petName).text = newValue
            "pet_type" -> findViewById<TextView>(R.id.petType).text = newValue
            "pet_gender" -> {
                // Directly use the string value (Male, Female, Unknown)
                val genderLabel = mapGenderValue(newValue)
                findViewById<TextView>(R.id.petGender).text = genderLabel
            }

            "pet_birthday" -> {
                // Update birthday and age dynamically without reloading the page
                petBirthday = newValue // Save the new birthday value
                val formattedAge = calculateAndFormatAge(petBirthday) // Recalculate the age
                findViewById<TextView>(R.id.petAge).text = formattedAge // Update the age in the UI
            }

            "pet_age" -> findViewById<TextView>(R.id.petAge).text = newValue
            "address1" -> findViewById<TextView>(R.id.address1).text = newValue
            "address2" -> findViewById<TextView>(R.id.address2).apply {
                text = newValue
                visibility = if (newValue.isEmpty()) TextView.GONE else TextView.VISIBLE
            }

            "city" -> findViewById<TextView>(R.id.city).text = newValue
            "state" -> findViewById<TextView>(R.id.state).text = newValue
            "zip" -> findViewById<TextView>(R.id.zip).text = newValue
            "pet_notes" -> findViewById<TextView>(R.id.aboutContent).text = newValue/*            "pet_social_instagram" -> findViewById<TextView>(R.id.addInstagram).text = newValue
                        "pet_social_tiktok" -> findViewById<TextView>(R.id.addTikTok).text = newValue
                        "pet_social_facebook" -> findViewById<TextView>(R.id.addFacebook).text = newValue*/
            "pet_breed" -> findViewById<TextView>(R.id.petBreed).text = newValue
            "pet_weight" -> {
                // Since the fragment will now supply something like "12.5" or "8.0"
                // just keep it as a string:
                findViewById<TextView>(R.id.petWeight).text = "$newValue"
                updatePetDetailsOnServer(fieldId, newValue)
            }

            "pet_neutspay" -> {
                // Map the integer value back to its corresponding spayed/neutered status
                val neutspayLabel = when (newValue.toIntOrNull()) {
                    1 -> "Yes"
                    0 -> "No"
                    2 -> "Unknown"
                    else -> "Unknown"
                }
                findViewById<TextView>(R.id.petSpayed).text = neutspayLabel
            }

            "vet_contact" -> {
                // Update individual veterinarian contact fields
                val lines = newValue.split("\n")
                findViewById<TextView>(R.id.vetName).text = lines.getOrNull(0) ?: ""
                findViewById<TextView>(R.id.vetAddress1).apply {
                    text = lines.getOrNull(1) ?: ""
                    visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                }
                findViewById<TextView>(R.id.vetAddress2).apply {
                    text = lines.getOrNull(2) ?: ""
                    visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                }
                findViewById<TextView>(R.id.vetCityStateZip).apply {
                    text = lines.getOrNull(3) ?: ""
                    visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                }
                findViewById<TextView>(R.id.vetEmail).apply {
                    text = lines.getOrNull(4) ?: ""
                    visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
                }
            }

            "pet_microchip" -> findViewById<TextView>(R.id.microchipInfo).text = newValue
            "pet_tat" -> findViewById<TextView>(R.id.tattooInfo).text = newValue
            "pet_allergies" -> findViewById<TextView>(R.id.allergiesInfo).text = newValue
            "pet_health_conditions" -> findViewById<TextView>(R.id.healthConditionsInfo).text =
                newValue

            "pet_medications" -> findViewById<TextView>(R.id.medicationsInfo).text = newValue
            "pet_vaccinations" -> findViewById<TextView>(R.id.vaccinationsInfo).text = newValue
        }

        // Save the updated value to the server
        updatePetDetailsOnServer(fieldId, newValue)
    }

    /**
     * Maps the gender integer value to its corresponding label.
     * We modify this as it appears our PHP dev has decided on strings rather than integers for now
     */
    private fun mapGenderValue(genderValue: String): String {
        return when (genderValue) {
            "Male" -> "Male"
            "Female" -> "Female"
            else -> "Unknown"
        }
    }


    /**
     *   // Health Section Fields

    findViewById<TextView>(R.id.vetContactInfo).setOnClickListener {

    val currentValue = (it as TextView).text.toString()

    showEditFragment("vet_contact", currentValue, "Edit Veterinarian Contact")

    }



    findViewById<TextView>(R.id.microchipInfo).setOnClickListener {

    val currentValue = (it as TextView).text.toString()

    showEditFragment("pet_microchip", currentValue, "Edit Microchip Number")

    }



    findViewById<TextView>(R.id.tattooInfo).setOnClickListener {

    val currentValue = (it as TextView).text.toString()

    showEditFragment("pet_tat", currentValue, "Edit Tattoo Information")

    }



    findViewById<TextView>(R.id.allergiesInfo).setOnClickListener {

    val currentValue = (it as TextView).text.toString()

    showEditFragment("pet_allergies", currentValue, "Edit Allergies")

    }



    findViewById<TextView>(R.id.healthConditionsInfo).setOnClickListener {

    val currentValue = (it as TextView).text.toString()

    showEditFragment("pet_health_conditions", currentValue, "Edit Health Conditions")

    }



    findViewById<TextView>(R.id.medicationsInfo).setOnClickListener {

    val currentValue = (it as TextView).text.toString()

    showEditFragment("pet_medications", currentValue, "Edit Medications")

    }



    findViewById<TextView>(R.id.vaccinationsInfo).setOnClickListener {

    val currentValue = (it as TextView).text.toString()

    showEditFragment("pet_vaccinations", currentValue, "Edit Vaccinations")

    }es pet details from the server using OkHttp.
     */
    private fun fetchPetDetails(userID: String?, petID: String?, onComplete: () -> Unit) {
        setLoadingState(true)
        val url = "https://emergencyleash.com/wp-content/plugins/access-app/pull/pet-profile.php"

        val requestBody =
            FormBody.Builder().add("userID", userID ?: "").add("pet_ID", petID ?: "").build()

        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setLoadingState(false)
                    Toast.makeText(
                        this@PetDetailsActivity, "Failed to fetch pet details.", Toast.LENGTH_SHORT
                    ).show()
                    onComplete()  // Let the caller know we're done anyway
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                runOnUiThread {
                    setLoadingState(false)
                    if (responseBody != null) {
                        updatePetDetailsUI(responseBody)
                    }
                    // Once we're done updating UI
                    contentContainer.visibility = View.VISIBLE
                    onComplete()
                }
            }
        })
    }


    private fun updatePetDetailsUI(responseBody: String) {
        try {
            val jsonResponse = JSONObject(responseBody)

            // -- 1) BASIC PET FIELDS --
            findViewById<TextView>(R.id.petName).text = jsonResponse.getString("pet_name")
            findViewById<TextView>(R.id.petType).text = jsonResponse.getString("pet_type")
            findViewById<TextView>(R.id.petGender).text =
                mapGenderValue(jsonResponse.getString("pet_gender"))

            // Store & calculate age
            petBirthday = jsonResponse.getString("pet_birthday")
            findViewById<TextView>(R.id.petAge).text = calculateAndFormatAge(petBirthday)

            // Address lines
            findViewById<TextView>(R.id.address1).text = jsonResponse.getString("address1")
            findViewById<TextView>(R.id.address2).apply {
                text = jsonResponse.getString("address2")
                visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
            }
            findViewById<TextView>(R.id.city).text = jsonResponse.getString("city")
            findViewById<TextView>(R.id.state).text = jsonResponse.getString("state")
            findViewById<TextView>(R.id.zip).text = jsonResponse.getString("zip")

            // -- 2) ABOUT SECTION --
            findViewById<TextView>(R.id.aboutContent).text = getTextOrFallback(
                jsonResponse.optString("pet_notes"), "No information available."
            )

            // -- 3) GENERAL INFO --
            findViewById<TextView>(R.id.petBreed).text = jsonResponse.getString("pet_breed")
            findViewById<TextView>(R.id.petWeight).text = jsonResponse.getString("pet_weight")

            val neutspayLabel = when (jsonResponse.optString("pet_neutspay").toIntOrNull()) {
                1 -> "Yes"
                0 -> "No"
                2 -> "Unknown"
                else -> "Unknown"
            }
            findViewById<TextView>(R.id.petSpayed).text = neutspayLabel

            // -- 4) VETERINARIAN CONTACT --
            val vetName = jsonResponse.optString("pet_vet_name", "")
            val vetAddress1 = jsonResponse.optString("pet_vet_address1", "")
            val vetAddress2 = jsonResponse.optString("pet_vet_address2", "")
            val vetCityStateZip = "${
                jsonResponse.optString(
                    "pet_vet_city", ""
                )
            }, " + "${
                jsonResponse.optString(
                    "pet_vet_state", ""
                )
            } " + jsonResponse.optString("pet_vet_zip", "")
            val vetEmail = jsonResponse.optString("pet_vet_email", "")

            findViewById<TextView>(R.id.vetName).text = vetName
            findViewById<TextView>(R.id.vetAddress1).apply {
                text = vetAddress1
                visibility = if (vetAddress1.isEmpty()) View.GONE else View.VISIBLE
            }
            findViewById<TextView>(R.id.vetAddress2).apply {
                text = vetAddress2
                visibility = if (vetAddress2.isEmpty()) View.GONE else View.VISIBLE
            }
            findViewById<TextView>(R.id.vetCityStateZip).apply {
                text = vetCityStateZip
                visibility = if (vetCityStateZip.isBlank()) View.GONE else View.VISIBLE
            }
            findViewById<TextView>(R.id.vetEmail).apply {
                text = vetEmail
                visibility = if (vetEmail.isBlank()) View.GONE else View.VISIBLE
            }

            // -- 5) HEALTH DETAILS --
            findViewById<TextView>(R.id.microchipInfo).text = getTextOrFallback(
                jsonResponse.optString("pet_microchip"),
                "No information for Microchip Number found."
            )
            findViewById<TextView>(R.id.tattooInfo).text = getTextOrFallback(
                jsonResponse.optString("pet_tat"), "No information for Tattoo found."
            )
            findViewById<TextView>(R.id.allergiesInfo).text = getTextOrFallback(
                jsonResponse.optString("pet_allergies"), "No information for Allergies found."
            )
            findViewById<TextView>(R.id.healthConditionsInfo).text = getTextOrFallback(
                jsonResponse.optString("pet_health_conditions"),
                "No information for Health Conditions found."
            )
            findViewById<TextView>(R.id.medicationsInfo).text = getTextOrFallback(
                jsonResponse.optString("pet_medications"), "No information for Medications found."
            )
            findViewById<TextView>(R.id.vaccinationsInfo).text = getTextOrFallback(
                jsonResponse.optString("pet_vaccinations"), "No information for Vaccinations found."
            )

            // -- 6) PET PHOTO --
            val petPhotoView = findViewById<ImageView>(R.id.petPhoto)
            Glide.with(this).load(jsonResponse.getString("pet_photo1"))
                .placeholder(R.drawable.ic_pet_placeholder).into(petPhotoView)

            // -- 7) GALLERY & DOCUMENTS --
            populateGallery(jsonResponse)
            populateDocuments(jsonResponse)

            // -- 8) PRIVACY SWITCHES --
            initializePrivacySwitches(jsonResponse)

            // -- 9) MISSING PET LOGIC --
            val petMissing = jsonResponse.optString("pet_missing", "0") == "1"

            // 10) SUBSCRIPTION CHECKS
            // Make sure your PHP returns "pet_plus_sub" => $pet->pet_plus_sub
            val petPlusSubFlag = jsonResponse.optInt("pet_plus_sub", 0)
            val subscriptionName =
                currentSubscriptionName // e.g. "Emergency Leash One Tag", "Emergency Leash Multiple Tags", etc.

            // Our two valid sub scenarios:
            val isMultiTag =
                subscriptionName.equals("Emergency Leash Multiple Tags", ignoreCase = true)
            val isSingleTagWithThisPetAssigned = subscriptionName.equals(
                "Emergency Leash Plus", ignoreCase = true
            ) && (petPlusSubFlag == 1)

            // Only these users/pets can do "Send Alert" + "Create Flyer"
            val isEligibleForAlertsAndFlyer = (isMultiTag || isSingleTagWithThisPetAssigned)

            // 11) GRAB REFERENCES
            val markPetAsLostCard = findViewById<CardView>(R.id.markPetAsLostCard)
            val markPetAsFoundCard = findViewById<CardView>(R.id.markPetAsFoundCard)
            val petMissingOptions = findViewById<LinearLayout>(R.id.petMissingOptions)
            val sendAlertCard = findViewById<CardView>(R.id.sendAlertCard)
            val createFlyerCard = findViewById<CardView>(R.id.createFlyerCard)
            Log.d("PetDetailsActivity", "Subscription name from server: '$subscriptionName'")

            // -- Clear everything first --
            markPetAsLostCard.visibility = View.GONE
            markPetAsFoundCard.visibility = View.GONE
            petMissingOptions.visibility = View.GONE
            sendAlertCard.visibility = View.GONE
            createFlyerCard.visibility = View.GONE

            // 12) ALWAYS ALLOW "REPORT PET AS LOST" IF NOT MISSING
            if (!petMissing) {
                markPetAsLostCard.visibility = View.VISIBLE
            }

            // 13) ALWAYS ALLOW "REPORT PET AS FOUND" IF MISSING
            if (petMissing) {
                markPetAsFoundCard.visibility = View.VISIBLE
            }

            // 14) SHOW ALERT & FLYER ONLY IF SUBSCRIPTION IS VALID
            if (isEligibleForAlertsAndFlyer) {
                // If the pet is missing, we typically show these under 'petMissingOptions'
                // (You might show them even if not missing, but usually these are "lost pet" features.)
                if (petMissing) {
                    petMissingOptions.visibility = View.VISIBLE
                    // The layout presumably contains sendAlertCard & createFlyerCard inside it
                    sendAlertCard.visibility = View.VISIBLE
                    createFlyerCard.visibility = View.VISIBLE
                } else {
                    // If you want the user to be able to create a flyer / send alert even before officially "missing",
                    // then show them here as well:
                    // sendAlertCard.visibility = View.VISIBLE
                    // createFlyerCard.visibility = View.VISIBLE
                    // or if not, leave them hidden until missing
                }
            }

        } catch (e: JSONException) {
            Log.e("PetDetailsActivity", "JSON Parsing error: ${e.message}")
        }
    }


    private fun populateDocuments(jsonResponse: JSONObject) {
        val documentsRecyclerView = findViewById<RecyclerView>(R.id.documentsContent)
        documentUrls = mutableListOf()
        documentSlots = mutableListOf()

        for (i in 1..10) {
            val slotName = "pet_doc$i"
            val documentUrl = jsonResponse.optString(slotName).trim()
            documentSlots.add(DocumentSlot(slotName, documentUrl))

            if (documentUrl.isNotEmpty() && documentUrl.lowercase() != "null" && documentUrl.lowercase() != "false" && Patterns.WEB_URL.matcher(
                    documentUrl
                ).matches()
            ) {
                documentUrls.add(documentUrl)
            }
        }

        // Add placeholder for "Add Document"
        documentUrls.add("add_document_placeholder")

        // Set up the RecyclerView with DocumentsAdapter
        documentsRecyclerView.layoutManager = LinearLayoutManager(this)
        documentsAdapter = DocumentsAdapter(this, documentUrls) {
            pickDocumentFromDevice()
        }
        documentsRecyclerView.adapter = documentsAdapter
    }

    private val DOCUMENT_PICK_CODE = 2000

    private fun pickDocumentFromDevice() {
        val allowedMimeTypes = arrayOf(
            "application/pdf", "application/msword", // .doc
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", // .docx
            "text/plain" // .txt
        )

        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*" // A broad type so we can narrow with EXTRA_MIME_TYPES below
            putExtra(Intent.EXTRA_MIME_TYPES, allowedMimeTypes)
        }
        startActivityForResult(intent, DOCUMENT_PICK_CODE)
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) {
            when (requestCode) {
                IMAGE_PICK_CODE -> {
                    data.data?.let { uri ->
                        contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        uploadImageToServer(uri)
                    }
                }

                DOCUMENT_PICK_CODE -> {
                    data.data?.let { uri ->
                        contentResolver.takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        uploadDocumentToServer(uri)
                    }
                }
            }
        }
    }

    private fun uploadDocumentToServer(documentUri: Uri) {
        val url = "https://emergencyleash.com/wp-content/plugins/access-app/push/pet-profile.php"

        val inputStream = contentResolver.openInputStream(documentUri)
        val fileBytes = inputStream?.readBytes()
        inputStream?.close()

        if (fileBytes != null) {
            // Determine the next available slot for the document
            val nextAvailableSlot = findNextAvailableDocumentSlot()
            Log.d("PetDetailsActivity", "Next available document slot: $nextAvailableSlot")

            if (nextAvailableSlot == null) {
                Toast.makeText(this, "No available slots for documents.", Toast.LENGTH_SHORT).show()
                return
            }

            val fileName = getFileName(documentUri)
            val mimeType = contentResolver.getType(documentUri) ?: "application/octet-stream"

            val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("userID", userID ?: "").addFormDataPart("pet_ID", petID ?: "")
                .addFormDataPart(
                    nextAvailableSlot,
                    fileName,
                    fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
                ).build()

            val request = Request.Builder().url(url).post(requestBody).build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(
                            this@PetDetailsActivity, "Failed to upload document", Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseText = response.body?.string()
                    Log.d("PetDetailsActivity", "Document upload response: $responseText")
                    runOnUiThread {
                        if (response.isSuccessful) {
                            Toast.makeText(
                                this@PetDetailsActivity,
                                "Document uploaded successfully",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Update the documents list with the new document
                            updateDocumentsWithNewDocument(documentUri)

                            // Update documentSlots to reflect the new occupied slot
                            documentSlots.find { it.slotName == nextAvailableSlot }?.let {
                                it.documentUrl = documentUri.toString()
                            }
                        } else {
                            Toast.makeText(
                                this@PetDetailsActivity,
                                "Document upload failed: ${response.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

            })
        } else {
            Toast.makeText(this, "Unable to get document data", Toast.LENGTH_SHORT).show()
        }
    }


    private fun formatVetContactInfo(jsonResponse: JSONObject): String {
        val vetName =
            jsonResponse.optString("pet_vet_name").takeIf { it.isNotEmpty() && it != "null" }
        val vetAddress1 =
            jsonResponse.optString("pet_vet_address1").takeIf { it.isNotEmpty() && it != "null" }
        val vetAddress2 =
            jsonResponse.optString("pet_vet_address2").takeIf { it.isNotEmpty() && it != "null" }
        val vetCity =
            jsonResponse.optString("pet_vet_city").takeIf { it.isNotEmpty() && it != "null" }
        val vetState =
            jsonResponse.optString("pet_vet_state").takeIf { it.isNotEmpty() && it != "null" }
        val vetZip =
            jsonResponse.optString("pet_vet_zip").takeIf { it.isNotEmpty() && it != "null" }
        val vetEmail =
            jsonResponse.optString("pet_vet_email").takeIf { it.isNotEmpty() && it != "null" }

        // Format the address
        val address = mutableListOf<String>()
        vetName?.let { address.add(it) }
        vetAddress1?.let { address.add(it) }
        vetAddress2?.let { address.add(it) }

        // City, State, Zip
        if (!vetCity.isNullOrEmpty() && !vetState.isNullOrEmpty() && !vetZip.isNullOrEmpty()) {
            address.add("$vetCity, $vetState $vetZip")
        }

        vetEmail?.let { address.add(it) }

        return if (address.isEmpty()) {
            "No information for Veterinarian found."
        } else {
            address.joinToString("\n")
        }
    }

    private fun findNextAvailableDocumentSlot(): String? {
        for (documentSlot in documentSlots) {
            if (documentSlot.documentUrl.isEmpty() || documentSlot.documentUrl.lowercase() == "null" || documentSlot.documentUrl.lowercase() == "false") {
                return documentSlot.slotName
            }
        }
        return null // No available slots
    }


    private fun updateDocumentsWithNewDocument(documentUri: Uri) {
        // Remove the "add_document_placeholder" if it's present
        if (documentUrls.contains("add_document_placeholder")) {
            documentUrls.remove("add_document_placeholder")
        }

        // Add the new document URI as a string
        documentUrls.add(documentUri.toString())

        // Add the placeholder back
        documentUrls.add("add_document_placeholder")

        // Notify the adapter that the data set has changed
        documentsAdapter.notifyDataSetChanged()
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = contentResolver.query(uri, null, null, null, null)
            try {
                cursor?.let {
                    if (it.moveToFirst()) {
                        result =
                            it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                    }
                }
            } catch (e: Exception) {
                Log.e("PetDetailsActivity", "Error getting file name: ${e.message}")
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "document"
    }


    /**
     * Initializes the privacy switches without triggering their listeners.
     */
    private fun initializePrivacySwitches(jsonResponse: JSONObject) {
        isInitializingPrivacySwitches = true

        // Set the switches' states based on the jsonResponse
        findViewById<Switch>(R.id.privacyBreedSwitch).isChecked =
            jsonResponse.optString("pet_hide_breed") == "1"
        findViewById<Switch>(R.id.privacyGenderSwitch).isChecked =
            jsonResponse.optString("pet_hide_gender") == "1"
        findViewById<Switch>(R.id.privacyBirthdaySwitch).isChecked =
            jsonResponse.optString("pet_hide_birthday") == "1"
        findViewById<Switch>(R.id.privacyNotesSwitch).isChecked =
            jsonResponse.optString("pet_hide_notes") == "1"
        findViewById<Switch>(R.id.privacyTattooSwitch).isChecked =
            jsonResponse.optString("pet_hide_tat") == "1"
        findViewById<Switch>(R.id.privacyMicrochipSwitch).isChecked =
            jsonResponse.optString("pet_hide_mic") == "1"
        findViewById<Switch>(R.id.privacySpayedSwitch).isChecked =
            jsonResponse.optString("pet_hide_neutspay") == "1"
        findViewById<Switch>(R.id.privacyOwnerLastNameSwitch).isChecked =
            jsonResponse.optString("pet_hide_lname") == "1"
        findViewById<Switch>(R.id.privacyOwnerCellSwitch).isChecked =
            jsonResponse.optString("pet_hide_cell") == "1"
        findViewById<Switch>(R.id.privacyAddressSwitch).isChecked =
            jsonResponse.optString("pet_hide_address") == "1"

        isInitializingPrivacySwitches = false

        // Set up privacy switches listeners after initialization
        setupPrivacySwitches()
    }

    /**
     * Sets up listeners for privacy toggle switches.
     */
    private fun setupPrivacySwitches() {
        val privacySwitches = mapOf(
            "pet_hide_breed" to R.id.privacyBreedSwitch,
            "pet_hide_gender" to R.id.privacyGenderSwitch,
            "pet_hide_birthday" to R.id.privacyBirthdaySwitch,
            "pet_hide_notes" to R.id.privacyNotesSwitch,
            "pet_hide_tat" to R.id.privacyTattooSwitch,
            "pet_hide_mic" to R.id.privacyMicrochipSwitch,
            "pet_hide_neutspay" to R.id.privacySpayedSwitch,
            "pet_hide_lname" to R.id.privacyOwnerLastNameSwitch,
            "pet_hide_cell" to R.id.privacyOwnerCellSwitch,
            "pet_hide_address" to R.id.privacyAddressSwitch
        )

        for ((fieldId, switchId) in privacySwitches) {
            val privacySwitch = findViewById<Switch>(switchId)
            privacySwitch.setOnCheckedChangeListener { _, isChecked ->
                if (!isInitializingPrivacySwitches) {
                    val newValue = if (isChecked) "1" else "0"
                    updatePrivacySettingOnServer(fieldId, newValue)
                }
            }
        }
    }


    /**
     * Returns the text if not null or empty, otherwise returns the fallback text.
     */
    private fun getTextOrFallback(text: String?, fallback: String): String {
        return if (text.isNullOrEmpty() || text == "null") fallback else text
    }

    /**
     * Populates the gallery with images from the server response.
     */
    private var imageSlots: MutableList<ImageSlot> = mutableListOf()

    private fun populateGallery(jsonResponse: JSONObject) {
        val galleryRecyclerView = findViewById<RecyclerView>(R.id.galleryContent)
        imageUrls = mutableListOf()
        imageSlots = mutableListOf()

        for (i in 1..40) {
            val slotName = "pet_photo$i"
            val imageUrl = jsonResponse.optString(slotName).trim()
            imageSlots.add(ImageSlot(slotName, imageUrl))

            if (imageUrl.isNotEmpty() && imageUrl.lowercase() != "null" && imageUrl.lowercase() != "false" && Patterns.WEB_URL.matcher(
                    imageUrl
                ).matches()
            ) {
                imageUrls.add(imageUrl)
            }
        }

        // Add placeholder for "Add Image"
        imageUrls.add("add_image_placeholder")

        // Set up the RecyclerView with GalleryAdapter
        galleryRecyclerView.layoutManager = GridLayoutManager(this, 3)
// Decide maxImages based on subscription
        val maxImages = when (currentSubscriptionName) {
            "Emergency Leash Multiple Tags" -> 40
            "Emergency Leash One Tag" -> 30
            else -> 5
        }

// Then create the adapter and pass it along
        galleryAdapter = GalleryAdapter(context = this,
            imageUrls = imageUrls,
            maxImages = maxImages,
            onAddImageClick = { pickImageFromGallery() },
            onImageClick = { uri -> /* handle full-screen preview if needed */ })
        galleryRecyclerView.adapter = galleryAdapter
    }


    private fun updatePetDetailsOnServer(fieldId: String, newValue: String) {
        val url = "https://emergencyleash.com/wp-content/plugins/access-app/push/pet-profile.php"

        val requestBodyBuilder =
            FormBody.Builder().add("userID", userID ?: "").add("pet_ID", petID ?: "")

        when (fieldId) {
            "pet_weight" -> {
                // **No** integer parse. Directly send the string (e.g. "12.5")
                Log.d("PetDetailsActivity", "Weight value being sent to server: $newValue")
                requestBodyBuilder.add(fieldId, newValue)
            }

            "vet_contact" -> {
                // Parse and send the veterinarian contact fields
                val vetContactFields = parseVetContact(newValue)
                vetContactFields.forEach { (key, value) ->
                    requestBodyBuilder.add(key, value)
                }
            }

            else -> {
                // For other fields, just add the new value
                requestBodyBuilder.add(fieldId, newValue)
            }
        }

        val requestBody = requestBodyBuilder.build()
        val request = Request.Builder().url(url).post(requestBody).build()

        // Send the request
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PetDetailsActivity", "Update failed: ${e.message}")
                runOnUiThread {
                    Toast.makeText(
                        this@PetDetailsActivity,
                        "Failed to update. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body?.string()
                Log.d("PetDetailsActivity", "Update response: $responseText")
                runOnUiThread {
                    Toast.makeText(
                        this@PetDetailsActivity,
                        "Pet details updated successfully.",
                        Toast.LENGTH_SHORT
                    ).show()
                    hasChanges = false
                }
            }
        })
    }

    /**
     * Parses the veterinarian contact fields from a single formatted string into a map of field keys and values.
     */
    private fun parseVetContact(newValue: String): Map<String, String> {
        val lines = newValue.split("\n")
        return mapOf(
            "pet_vet_name" to (lines.getOrNull(0) ?: ""),
            "pet_vet_address1" to (lines.getOrNull(1) ?: ""),
            "pet_vet_address2" to (lines.getOrNull(2) ?: ""),
            "pet_vet_city" to (lines.getOrNull(3)?.split(",")?.firstOrNull() ?: ""),
            "pet_vet_state" to (lines.getOrNull(3)?.split(",")?.getOrNull(1)?.trim()?.split(" ")
                ?.firstOrNull() ?: ""),
            "pet_vet_zip" to (lines.getOrNull(3)?.split(" ")?.lastOrNull() ?: ""),
            "pet_vet_email" to (lines.getOrNull(4) ?: "")
        )
    }


    private val STORAGE_PERMISSION_CODE = 1001

    private fun checkStoragePermission() {
        if (checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED || checkSelfPermission(
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_DENIED
        ) {

            // Show rationale if needed
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.READ_EXTERNAL_STORAGE) || shouldShowRequestPermissionRationale(
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            ) {

                AlertDialog.Builder(this).setTitle("Storage Permission Needed")
                    .setMessage("This app requires access to your storage to upload images.")
                    .setPositiveButton("OK") { _, _ ->
                        val permissions = arrayOf(
                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                        requestPermissions(permissions, STORAGE_PERMISSION_CODE)
                    }.setNegativeButton("Cancel", null).create().show()
            } else {
                // Directly request the permission if no rationale is needed
                val permissions = arrayOf(
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                requestPermissions(permissions, STORAGE_PERMISSION_CODE)
            }
        } else {
            // Permission already granted, proceed with the image upload
            pickImageFromGallery()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with the image upload
                pickImageFromGallery()
            } else {
                Toast.makeText(
                    this, "Storage permission is required to upload images", Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    private val IMAGE_PICK_CODE = 1000

    private fun pickImageFromGallery() {
        // Check if we’re at or over max images
        if (imageUrls.size >= maxImages) {
            Toast.makeText(this, "You already have $maxImages images!", Toast.LENGTH_SHORT).show()
            return
        }

        // Otherwise, open the document picker or gallery
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }


    private fun uploadImageToServer(imageUri: Uri) {
        val url = "https://emergencyleash.com/wp-content/plugins/access-app/push/pet-profile.php"

        val inputStream = contentResolver.openInputStream(imageUri)
        val fileBytes = inputStream?.readBytes()
        inputStream?.close()

        if (fileBytes != null) {
            // Determine the next available slot for the image
            val nextAvailableSlot = findNextAvailableImageSlot()

            if (nextAvailableSlot == null) {
                Toast.makeText(this, "No available slots for images.", Toast.LENGTH_SHORT).show()
                return
            }

            val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("userID", userID ?: "").addFormDataPart("pet_ID", petID ?: "")
                .addFormDataPart(
                    nextAvailableSlot, // Upload to the next available slot
                    "image.jpg", RequestBody.create("image/jpeg".toMediaTypeOrNull(), fileBytes)
                ).build()

            val request = Request.Builder().url(url).post(requestBody).build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    runOnUiThread {
                        Toast.makeText(
                            this@PetDetailsActivity, "Failed to upload image", Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseText = response.body?.string()
                    Log.d("PetDetailsActivity", "Image upload response: $responseText")
                    runOnUiThread {
                        if (response.isSuccessful) {
                            Toast.makeText(
                                this@PetDetailsActivity,
                                "Image uploaded successfully",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Update the gallery with the new image without reloading the entire page
                            updateGalleryWithNewImage(imageUri)

                            // Update imageSlots to reflect the new occupied slot
                            imageSlots.find { it.slotName == nextAvailableSlot }?.let {
                                it.imageUrl = imageUri.toString()
                            }
                        } else {
                            Toast.makeText(
                                this@PetDetailsActivity,
                                "Image upload failed: ${response.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

            })
        } else {
            Toast.makeText(this, "Unable to get image data", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateGalleryWithNewImage(imageUri: Uri) {
        // Remove the "add_image_placeholder" if it's present
        if (imageUrls.contains("add_image_placeholder")) {
            imageUrls.remove("add_image_placeholder")
        }

        // Add the new image URI as a string
        imageUrls.add(imageUri.toString())

        // Add the placeholder back
        imageUrls.add("add_image_placeholder")

        // Notify the adapter that the data set has changed
        galleryAdapter.notifyDataSetChanged()
    }


    /**
     * Determine the next available slot for an image.
     */
    private fun findNextAvailableImageSlot(): String? {
        for (imageSlot in imageSlots) {
            if (imageSlot.imageUrl.isEmpty() || imageSlot.imageUrl.lowercase() == "null" || imageSlot.imageUrl.lowercase() == "false") {
                return imageSlot.slotName
            }
        }
        return null // No available slots
    }


    private fun getRealPathFromURI(contentUri: Uri): String? {
        var result: String? = null
        val cursor = contentResolver.query(contentUri, null, null, null, null)
        if (cursor != null) {
            cursor.moveToFirst()
            val idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
            if (idx != -1) {
                result = cursor.getString(idx)
            }
            cursor.close()
        }
        return result
    }


    private fun handleDocumentsSection() {
        val documentsHeader = findViewById<LinearLayout>(R.id.documentsHeader)
        val documentsContent = findViewById<RecyclerView>(R.id.documentsContent)
        val documentsChevronIcon = findViewById<ImageView>(R.id.documentsChevronIcon)

        // Initially hide the content
        documentsContent.visibility = View.GONE
        documentsChevronIcon.setImageResource(R.drawable.ic_chevron_down)

        documentsHeader.setOnClickListener {
            if (isDocumentsSectionExpanded) {
                documentsContent.visibility = View.GONE
                documentsChevronIcon.setImageResource(R.drawable.ic_chevron_down)
            } else {
                documentsContent.visibility = View.VISIBLE
                documentsChevronIcon.setImageResource(R.drawable.ic_chevron_up)
            }
            isDocumentsSectionExpanded = !isDocumentsSectionExpanded
        }
    }

    private fun fetchPetDetailsFromAPI(userID: String?, petID: String?) {
        val url = "https://emergencyleash.com/wp-content/plugins/access-app/pull/pet-poster.php"

        val requestBody =
            FormBody.Builder().add("userID", userID ?: "").add("pet_ID", petID ?: "").build()

        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(
                        this@PetDetailsActivity,
                        "Failed to fetch flyer details.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    // Log the entire server response
                    Log.d(
                        "PetDetailsActivity",
                        "Full Server Response from PetPoster API: $responseBody"
                    )

                    try {
                        petData = JSONObject(responseBody) // Store the fetched data in petData
                        runOnUiThread {
                            Toast.makeText(
                                this@PetDetailsActivity,
                                "Data fetched successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e: JSONException) {
                        e.printStackTrace()
                        Log.e("PetDetailsActivity", "JSON Parsing error: ${e.message}")
                    }
                }
            }
        })
    }


    private fun generateFlyerPDF(petData: JSONObject) {
        setLoadingState(true)

        // Load image asynchronously with Glide
        val petImageUrl = petData.optString("pet_photo1")
        Log.d("PetDetailsActivity", "Attempting to load image from URL: $petImageUrl")

        Glide.with(this).asBitmap().load(petImageUrl).into(object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                // Image loaded successfully, proceed with PDF generation
                createPDFDocument(resource, petData)
            }

            override fun onLoadFailed(errorDrawable: Drawable?) {
                Log.e("PetDetailsActivity", "Failed to load image, using placeholder instead.")
                val placeholderBitmap =
                    BitmapFactory.decodeResource(resources, R.drawable.ic_image_placeholder)
                createPDFDocument(placeholderBitmap, petData)
            }

            override fun onLoadCleared(placeholder: Drawable?) {
                // Handle any cleanup if needed
            }
        })
    }


    private fun createPDFDocument(petBitmap: Bitmap, petData: JSONObject) {
        setLoadingState(true) // Start the loading animation
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas

        // Paints
        val titlePaint = Paint().apply {
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val infoPaint = Paint().apply {
            textSize = 16f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val boldInfoPaint = Paint(infoPaint).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val linePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
            pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        }

        // Text data
        val petName = petData.optString("pet_name", "Unknown Pet")
        val ownerName = petData.optString("first_name", "Unknown Owner") + " " + petData.optString(
            "last_name", ""
        )

        val contactInfo = "emergencyleash.com/found"
        val uniqueCode = petData.optString("pet_tag_ID", "N/A")

        // Draw the header
        canvas.drawText("MISSING", pageInfo.pageWidth / 2f, 80f, titlePaint)

        // Draw the pet image
        val resizedBitmap = Bitmap.createScaledBitmap(petBitmap, 250, 250, false)
        canvas.drawBitmap(
            resizedBitmap, (pageInfo.pageWidth - resizedBitmap.width) / 2f, 100f, null
        )

        // Draw pet and owner details
        canvas.drawText("Pet Name: $petName", pageInfo.pageWidth / 2f, 400f, infoPaint)
        canvas.drawText("Owner's Name: $ownerName", pageInfo.pageWidth / 2f, 430f, infoPaint)
        canvas.drawText(
            "If found, please go to: $contactInfo", pageInfo.pageWidth / 2f, 460f, infoPaint
        )
        canvas.drawText("and enter code:", pageInfo.pageWidth / 2f, 490f, infoPaint)
        canvas.drawText(uniqueCode, pageInfo.pageWidth / 2f, 520f, boldInfoPaint)

        // Tear-off sections
        val startX = 40f // Shifted inward for better alignment
        val tearOffTopY = 650f
        val tearOffBottomY = 890f
        val sectionWidth = 59f // Adjusted width for 10 sections
        val textPadding = 10f // Padding between dashed line and text

        for (i in 0 until 10) {
            val xPos = startX + i * sectionWidth

            // Draw the dashed line between text sections
            canvas.drawLine(
                xPos + sectionWidth / 2,
                tearOffTopY,
                xPos + sectionWidth / 2,
                tearOffBottomY,
                linePaint
            )

            // Save the current canvas state before rotation
            canvas.save()

            // Move and rotate canvas to draw vertical text with padding after dashed line
            canvas.rotate(-90f, xPos - 5f, 750f) // Rotate -90 degrees around the text position

            // Draw vertical text in tear-off, shifted slightly inward
            canvas.drawText("Owner: $ownerName", xPos + 5f, 740f, infoPaint)
            canvas.drawText("Phone: 818-393-6788", xPos + 5f, 754f, infoPaint)
            canvas.drawText("Goto emergencyleash.com/found", xPos + 5f, 767f, infoPaint)
            canvas.drawText("Code: $uniqueCode", xPos + 5f, 781f, infoPaint)

            // Restore canvas to its original orientation
            canvas.restore()
        }

        pdfDocument.finishPage(page)

        // Save to the correct location
        val fileName = "Pet_Flyer_${System.currentTimeMillis()}.pdf"
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/Flyers")
                }

                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                    }
                    showFlyerDialog()
                    Toast.makeText(this, "Flyer saved to Download/Flyers", Toast.LENGTH_LONG).show()
                }
            } else {
                val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val file = File(downloadsDir, fileName)
                FileOutputStream(file).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                showFlyerDialog()
                Toast.makeText(
                    this, "Flyer saved to Downloads folder: ${file.absolutePath}", Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to save flyer as PDF", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDocument.close()
            setLoadingState(false) // Stop the loading animation here, regardless of success or failure
        }
    }


    private fun showFlyerDialog() {

        // Otherwise, show the dialog
        val builder = android.app.AlertDialog.Builder(this)
        val inflater = layoutInflater
        val dialogLayout = inflater.inflate(R.layout.dialog_flyer, null)
        builder.setView(dialogLayout)

        val alertDialog = builder.create()
        alertDialog.show()

        val okButton = dialogLayout.findViewById<Button>(R.id.okButton)

        okButton.setOnClickListener {
            alertDialog.dismiss()
        }
    }


    private fun reportLostPet() {
        // Show the ReportLostPetFragment as a BottomSheetDialogFragment
        val petId = intent.getStringExtra("pet_ID") ?: ""
        val reportLostPetFragment = ReportLostPetFragment.newInstance(petId)
        reportLostPetFragment.show(supportFragmentManager, "ReportLostPetFragment")
    }

    private fun reportFoundPet() {
        // Show the ReportFoundPetFragment as a BottomSheetDialogFragment
        val petId = intent.getStringExtra("pet_ID") ?: ""
        val reportFoundPetFragment = ReportFoundPetFragment.newInstance(petId)
        reportFoundPetFragment.show(supportFragmentManager, "ReportFoundPetFragment")
    }

    override fun onPetStatusChanged(isLost: Boolean) {
        if (isLost) {
            // Hide the "Mark Pet As Lost" card
            findViewById<CardView>(R.id.markPetAsLostCard).visibility = View.GONE
            // Show "Mark Pet As Found" card and missing options
            findViewById<LinearLayout>(R.id.petMissingOptions).visibility = View.VISIBLE
            findViewById<CardView>(R.id.markPetAsFoundCard).visibility = View.VISIBLE
            // Show the "Send Alert" card
            sendAlertCard.visibility = View.VISIBLE
        } else {
            // Pet is found, hide "Mark Pet As Found" card
            findViewById<CardView>(R.id.markPetAsFoundCard).visibility = View.GONE
            findViewById<LinearLayout>(R.id.petMissingOptions).visibility = View.GONE
            // Show "Mark Pet As Lost" card
            findViewById<CardView>(R.id.markPetAsLostCard).visibility = View.VISIBLE
            // Hide the "Send Alert" card
            sendAlertCard.visibility = View.GONE
            // Reset the "Send Alert" card state
            resetSendAlertCard()
        }
    }

    private fun resetSendAlertCard() {
        sendAlertCard.isEnabled = true
        sendAlertCard.isClickable = true
        sendAlertCard.alpha = 1.0f
        sendAlertText.text = "Send Alert"
    }

    override fun onZipCodeEntered(zipCode: String) {
        sendPetAlert(zipCode)
    }

    private fun sendPetAlert(petZipCode: String) {
        val petId = petID ?: ""

        if (petId.isEmpty() || petZipCode.isEmpty()) {
            Toast.makeText(this, "Pet ID or Zip Code is missing.", Toast.LENGTH_SHORT).show()
            return
        }

        val url = "https://emergencyleash.com/wp-content/plugins/access-app/push/pet-send-alert.php"

        val formBody =
            FormBody.Builder().add("pet_ID", petId).add("pet_zipcode", petZipCode).build()

        val request = Request.Builder().url(url).post(formBody).build()

        // Show a loading indicator if desired
        setLoadingState(true)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setLoadingState(false)
                    Toast.makeText(
                        this@PetDetailsActivity,
                        "Failed to send alert. Please try again.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseText = response.body?.string()
                runOnUiThread {
                    setLoadingState(false)
                    if (response.isSuccessful) {
                        try {
                            val jsonResponse = JSONObject(responseText)
                            val result = jsonResponse.optInt("result")
                            val message = jsonResponse.optString("message")

                            Toast.makeText(this@PetDetailsActivity, message, Toast.LENGTH_LONG)
                                .show()

                            if (result == 1) {
                                // Alert was successfully sent
                                disableSendAlertCard("Alert Sent")
                            } else {
                                // Cooldown period hasn't passed
                                val daysLeft = extractDaysLeft(message)
                                disableSendAlertCard("Cooldown: $daysLeft days left")
                            }
                        } catch (e: JSONException) {
                            Toast.makeText(
                                this@PetDetailsActivity,
                                "Unexpected server response.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
                        Toast.makeText(
                            this@PetDetailsActivity,
                            "Failed to send alert. Please try again.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }


    private fun extractDaysLeft(message: String): Int {
        // Assuming the message is in the format "You have X days for Pet Alert Cooldown."
        val regex = Regex("""You have (\d+) days for Pet Alert Cooldown.""")
        val matchResult = regex.find(message)
        return matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }

    private fun disableSendAlertCard(displayText: String) {
        sendAlertCard.isEnabled = false
        sendAlertCard.isClickable = false
        sendAlertCard.alpha = 0.5f // Optional: visually indicate that it's disabled
        sendAlertText.text = displayText
    }

    /**
     * Fetches the user's measurement preference from the server.
     */
    private fun fetchMeasurementPreference(userID: String?) {
        val url =
            "https://emergencyleash.com/wp-content/plugins/access-app/pull/settings-measurements.php"

        val requestBody = FormBody.Builder().add("userID", userID ?: "").build()

        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Log.e(
                        "PetDetailsActivity", "Failed to fetch measurement preference: ${e.message}"
                    )
                    Toast.makeText(
                        this@PetDetailsActivity,
                        "Failed to load measurement settings. Using default (lbs).",
                        Toast.LENGTH_SHORT
                    ).show()
                    // Default to lbs if fetching fails
                    updateMeasurementLabel("lbs")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.string()?.let { responseBody ->
                    Log.d("PetDetailsActivity", "Measurement preference response: $responseBody")

                    try {
                        val jsonResponse = JSONObject(responseBody)
                        if (jsonResponse.optInt("result") == 1) {
                            val measurement =
                                jsonResponse.getString("measurement") // "0" for lbs, "1" for kg
                            val unit = if (measurement == "1") "kg" else "lbs"
                            runOnUiThread { updateMeasurementLabel(unit) }
                        } else {
                            runOnUiThread {
                                Toast.makeText(
                                    this@PetDetailsActivity,
                                    "Error retrieving measurement settings. Using default (lbs).",
                                    Toast.LENGTH_SHORT
                                ).show()
                                updateMeasurementLabel("lbs")
                            }
                        }
                    } catch (e: JSONException) {
                        Log.e("PetDetailsActivity", "JSON Parsing error: ${e.message}")
                        runOnUiThread {
                            updateMeasurementLabel("lbs")
                            Toast.makeText(
                                this@PetDetailsActivity,
                                "Error processing measurement settings. Using default (lbs).",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        })
    }

    /**
     * Updates the weight unit label in the UI.
     */
    private fun updateMeasurementLabel(unit: String) {
        val weightLabelTextView = findViewById<TextView>(R.id.weightUnitLabel)
        weightLabelTextView.text = unit
    }


    /**
     * Fetches the user's subscription details from the server.
     */
    private fun fetchSubscriptionDetails(
        userID: String?, onComplete: (Boolean) -> Unit
    ) {
        val url =
            "https://emergencyleash.com/wp-content/plugins/access-app/pull/settings-subscription.php"
        val requestBody = FormBody.Builder().add("userID", userID ?: "").build()

        val request = Request.Builder().url(url).post(requestBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("PetDetailsActivity", "Failed to fetch subscription details: ${e.message}")
                runOnUiThread {
                    onComplete(false)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                Log.d("PetDetailsActivity", "Subscription API Response: $responseBody")

                var success = false
                if (!responseBody.isNullOrEmpty()) {
                    try {
                        val jsonResponse = JSONObject(responseBody)
                        val subscriptionObject = jsonResponse.optJSONObject("subscription")
                        if (subscriptionObject != null) {
                            val subscriptionName =
                                subscriptionObject.optString("subscription_name", "Unknown")
                            // Store subscription name
                            currentSubscriptionName = subscriptionName
                            Log.d(
                                "PetDetailsActivity", "Parsed Subscription Name: $subscriptionName"
                            )
                            success = true
                        }
                    } catch (e: JSONException) {
                        Log.e("PetDetailsActivity", "JSON Parsing error: ${e.message}")
                    }
                }
                runOnUiThread {
                    onComplete(success)
                }
            }
        })
    }


    private fun loadAllData(userID: String?, petID: String?) {
        // 1) Fetch subscription details first
        fetchSubscriptionDetails(userID) { subscriptionFetched ->
            if (!subscriptionFetched) {
                Log.e("PetDetailsActivity", "Failed to fetch subscription, continuing anyway.")
            }

            fetchPetDetails(userID, petID) {
                fetchPetDetailsFromAPI(userID, petID)
                fetchScans(petID)
            }
        }
    }


    data class ScanItem(
        val scanID: String,
        val petID: String,
        val userID: String,
        val tagNum: String,   // changed from tagID
        val latitude: String,
        val longitude: String,
        val isActive: String, // or Boolean
        val scanDate: String,
        val scanAge: String,
        var isExpanded: Boolean = false
    )


    class ScansAdapter(
        private val scans: MutableList<ScanItem>
    ) : RecyclerView.Adapter<ScansAdapter.ScanViewHolder>() {

        // Inner ViewHolder class
        inner class ScanViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val scanTopRow: LinearLayout = itemView.findViewById(R.id.scanTopRow)
            val scanTitle: TextView = itemView.findViewById(R.id.scanTitleText)
            val scanAddress: TextView = itemView.findViewById(R.id.scanAddressText)
            val scanDetailsContainer: LinearLayout =
                itemView.findViewById(R.id.scanDetailsContainer)
            val scanDate: TextView = itemView.findViewById(R.id.scanDateText)
            val scanDevice: TextView = itemView.findViewById(R.id.scanDeviceText)
            val scanLatitude: TextView = itemView.findViewById(R.id.scanLatitudeText)
            val scanLongitude: TextView = itemView.findViewById(R.id.scanLongitudeText)
            val viewMapButton: Button = itemView.findViewById(R.id.viewMapButton)
            val shareButton: ImageView = itemView.findViewById(R.id.shareScanIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            val itemView = inflater.inflate(R.layout.item_scan, parent, false)
            return ScanViewHolder(itemView)
        }

        override fun onBindViewHolder(holder: ScanViewHolder, position: Int) {
            val scanItem = scans[position]

            // Example fields
            holder.scanTitle.text = "Scan ${scanItem.scanID} - Precise Location"
            // You might have an address or partial snippet. Hard-coded for example:
            holder.scanAddress.text = "Sample address text if you have it..."

            holder.scanDate.text = "Date: ${scanItem.scanDate}"
            holder.scanDevice.text =
                "Device: ${scanItem.tagNum}"  // or "Device: AZ7493" as in sample
            holder.scanLatitude.text = "Latitude\n${scanItem.latitude}"
            holder.scanLongitude.text = "Longitude\n${scanItem.longitude}"

            // Expand/collapse logic
            if (scanItem.isExpanded) {
                holder.scanDetailsContainer.visibility = View.VISIBLE
            } else {
                holder.scanDetailsContainer.visibility = View.GONE
            }

            // The top row (the smaller card portion) toggles expand/collapse
            holder.scanTopRow.setOnClickListener {
                scanItem.isExpanded = !scanItem.isExpanded
                notifyItemChanged(position)
            }

            // "View Map" button could open a map Intent, or do nothing:
            holder.viewMapButton.setOnClickListener {
                val mapUrl = "http://maps.google.com/?q=${scanItem.latitude},${scanItem.longitude}"
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mapUrl))
                holder.itemView.context.startActivity(browserIntent)
            }

            // Share button to share the scan
            holder.shareButton.setOnClickListener {
                val shareMessage =
                    "Found scan at: \nLat: ${scanItem.latitude}, Lng: ${scanItem.longitude}"
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareMessage)
                    type = "text/plain"
                }
                holder.itemView.context.startActivity(
                    Intent.createChooser(
                        shareIntent, "Share scan"
                    )
                )
            }
        }

        override fun getItemCount(): Int = scans.size
    }

    private fun handleScansSection() {
        val scanHeader = findViewById<LinearLayout>(R.id.scanHeader)
        val scanChevronIcon = findViewById<ImageView>(R.id.scanChevronIcon)

        val scansRecyclerView = findViewById<RecyclerView>(R.id.scansContent)
        val noScansPlaceholder = findViewById<TextView>(R.id.noScansPlaceholder)

        // Initially hide the content and placeholder
        scansRecyclerView.visibility = View.GONE
        noScansPlaceholder.visibility = View.GONE
        scanChevronIcon.setImageResource(R.drawable.ic_chevron_down)

        scanHeader.setOnClickListener {
            isScansSectionExpanded = !isScansSectionExpanded
            if (isScansSectionExpanded) {
                scanChevronIcon.setImageResource(R.drawable.ic_chevron_up)

                // If the user expands, decide what to show
                if (isScansEmpty) {
                    // No scans => show placeholder, hide list
                    noScansPlaceholder.visibility = View.VISIBLE
                    scansRecyclerView.visibility = View.GONE
                } else {
                    // We have scans => show the list, hide placeholder
                    noScansPlaceholder.visibility = View.GONE
                    scansRecyclerView.visibility = View.VISIBLE
                }
            } else {
                // Collapsing => hide both
                scanChevronIcon.setImageResource(R.drawable.ic_chevron_down)
                scansRecyclerView.visibility = View.GONE
                noScansPlaceholder.visibility = View.GONE
            }
        }
    }


    /**
     * Fetches the user's scans from the server (scan-list.php).
     */
    private fun fetchScans(petID: String?) {
        if (petID.isNullOrEmpty()) {
            Log.e("PetDetailsActivity", "Cannot fetch scans, petID is null or empty.")
            return
        }

        setLoadingState(true) // Show your loading spinner

        val url = "https://emergencyleash.com/wp-content/plugins/access-app/pull/scan-list.php"

        // **Log what we're about to send**
        Log.d("PetDetailsActivity", "fetchScans() -> Sending pet_ID: $petID to $url")

        val requestBody = FormBody.Builder().add("pet_ID", petID)  // The parameter to send
            .build()

        // Optionally log a string version of the request body:
        // (The simplest is just to log the key-values you're adding, as done above.)
        // More advanced usage might require a helper function to print the exact form data.

        val request = Request.Builder().url(url).post(requestBody).build()

        // Now make the network call
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    setLoadingState(false)
                    Toast.makeText(
                        this@PetDetailsActivity, "Failed to fetch scans.", Toast.LENGTH_SHORT
                    ).show()
                }
                // Also log the failure
                Log.e("PetDetailsActivity", "fetchScans -> Network request failed: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                // **Log the entire server response** for debugging
                //  Log.d("PetDetailsActivity", "Scans server response: $responseBody")

                runOnUiThread {
                    setLoadingState(false)
                    if (!responseBody.isNullOrEmpty()) {
                        parseAndDisplayScans(responseBody)
                    } else {
                        // Handle empty response if needed
                        // ...
                    }
                }
            }
        })
    }


    private fun parseAndDisplayScans(responseBody: String) {
        try {
            val json = JSONObject(responseBody)
            if (json.has("scans")) {
                val scansArray = json.getJSONArray("scans")
                scansList.clear()

                for (i in 0 until scansArray.length()) {
                    val scanObj = scansArray.getJSONObject(i)
                    val scanItem = ScanItem(
                        scanID = scanObj.optString("scan_ID", ""),
                        petID = scanObj.optString("pet_ID", ""),
                        userID = scanObj.optString("ID", ""),  // from "ID" in JSON
                        tagNum = scanObj.optString("tag_num", ""),
                        latitude = scanObj.optString("scan_latitude", ""),
                        longitude = scanObj.optString("scan_longitude", ""),
                        isActive = scanObj.optString("scan_is_active", ""), // or parse bool
                        scanDate = scanObj.optString("scan_date", ""),
                        scanAge = scanObj.optString("scan_age", ""),
                        isExpanded = false
                    )
                    scansList.add(scanItem)
                }

                isScansEmpty = scansList.isEmpty()

                if (!::scansAdapter.isInitialized) {
                    scansAdapter = ScansAdapter(scansList)
                    val scansRecyclerView = findViewById<RecyclerView>(R.id.scansContent)
                    scansRecyclerView.layoutManager = LinearLayoutManager(this)
                    scansRecyclerView.adapter = scansAdapter
                } else {
                    scansAdapter.notifyDataSetChanged()
                }
            } else {
                // "scans" not found => no data
                isScansEmpty = true
                scansList.clear()
            }
        } catch (e: JSONException) {
            Log.e("PetDetailsActivity", "Error parsing scans JSON: ${e.message}")
            isScansEmpty = true
            scansList.clear()
        }
        // Update UI based on isScansEmpty, or do refreshScansUI()
        refreshScansUI()
    }


    private fun refreshScansUI() {
        val scansRecyclerView = findViewById<RecyclerView>(R.id.scansContent)
        val noScansPlaceholder = findViewById<TextView>(R.id.noScansPlaceholder)

        if (isScansSectionExpanded) {
            if (isScansEmpty) {
                noScansPlaceholder.visibility = View.VISIBLE
                scansRecyclerView.visibility = View.GONE
            } else {
                noScansPlaceholder.visibility = View.GONE
                scansRecyclerView.visibility = View.VISIBLE
            }
        } else {
            // If the user never expanded or is collapsed, hide both
            noScansPlaceholder.visibility = View.GONE
            scansRecyclerView.visibility = View.GONE
        }
    }


}
