package com.emergencyleash.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.DatePicker
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.source
import org.json.JSONObject
import java.io.IOException

class RegisterTagActivity : AppCompatActivity() {

    // ─── Constants ─────────────────────────────────────────────────────────────
    companion object {
        private const val MAX_IMAGE_SIZE_BYTES = 4_194_304L            // 4 MB
        private const val REGISTER_URL =
            "https://emergencyleash.com/wp-content/plugins/access-app/push/register-tag.php"
    }

    // ─── View references ───────────────────────────────────────────────────────
    private lateinit var tagIdField: EditText
    private lateinit var activationCodeField: EditText
    private lateinit var petNameField: EditText
    private lateinit var petTypeSpinner: Spinner
    private lateinit var petBreedField: EditText
    private lateinit var petGenderSpinner: Spinner
    private lateinit var petBirthdayPicker: DatePicker
    private lateinit var microchipField: EditText
    private lateinit var tattooField: EditText
    private lateinit var neuterSpaySpinner: Spinner
    private lateinit var petNotesField: EditText
    private lateinit var uploadPhotoButton: Button
    private lateinit var petPhotoThumbnail: ImageView
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button

    // ─── Runtime variables ─────────────────────────────────────────────────────
    private var selectedImageUri: Uri? = null
    private var petBirthday: String = ""

    // ─── Helpers ───────────────────────────────────────────────────────────────
    private val client = OkHttpClient()
    private lateinit var imagePickerLauncher: ActivityResultLauncher<Array<String>>

    // ───────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register_tag)

        bindViews()
        setupSpinners()
        setupImagePicker()
        setupButtons()

        // Set up back navigation using the chevron icon and cancel button
        val chevronLeft: ImageView = findViewById(R.id.chevronLeft)
        chevronLeft.setOnClickListener {
            handleBackPress()
        }
    }

    // ─── View binding ──────────────────────────────────────────────────────────
    private fun bindViews() {
        tagIdField = findViewById(R.id.pet_tag_ID)
        activationCodeField = findViewById(R.id.pet_activation_code)
        petNameField = findViewById(R.id.pet_name)
        petTypeSpinner = findViewById(R.id.pet_type)
        petBreedField = findViewById(R.id.pet_breed)
        petGenderSpinner = findViewById(R.id.pet_gender)
        petBirthdayPicker = findViewById(R.id.pet_birthday)
        microchipField = findViewById(R.id.pet_microchip)
        tattooField = findViewById(R.id.pet_tat)
        neuterSpaySpinner = findViewById(R.id.pet_neutspay)
        petNotesField = findViewById(R.id.pet_notes)
        uploadPhotoButton = findViewById(R.id.uploadPhotoButton)
        petPhotoThumbnail = findViewById(R.id.petPhotoThumbnail)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
    }

    // ─── Spinner population ────────────────────────────────────────────────────
    private fun setupSpinners() {
        val petTypes = arrayOf("Select Pet Type", "Dog", "Cat", "Horse", "Other")
        petTypeSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, petTypes).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

        val petGenders = arrayOf("Select Gender", "Male", "Female")
        petGenderSpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, petGenders).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }

        val neuterSpayOptions = arrayOf("Select Neutered/Spayed", "Yes", "No", "Unknown")
        neuterSpaySpinner.adapter =
            ArrayAdapter(this, android.R.layout.simple_spinner_item, neuterSpayOptions).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
    }

    // ─── Image picker (Activity Result API) ────────────────────────────────────
    private fun setupImagePicker() {
        imagePickerLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                uri?.let {
                    contentResolver.takePersistableUriPermission(
                        it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                    selectedImageUri = it
                    petPhotoThumbnail.setImageURI(it)
                    uploadPhotoButton.setText(R.string.photo_selected)
                }
            }

        uploadPhotoButton.setOnClickListener {
            imagePickerLauncher.launch(arrayOf("image/*"))
        }
    }

    // ─── Button actions ────────────────────────────────────────────────────────
    private fun setupButtons() {
        saveButton.setOnClickListener { savePetData() }
        cancelButton.setOnClickListener { finish() }
    }

    // ─── Main upload logic ─────────────────────────────────────────────────────
    private fun savePetData() {
        // 0. Ensure user is logged in
        val userID = getUserIDFromPrefs()
        if (userID < 0) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_LONG).show()
            return
        }

        // 1. Gather & validate fields
        val tagId = tagIdField.text.toString().trim()
        val activationCode = activationCodeField.text.toString().trim()
        val petName = petNameField.text.toString().trim()
        val petType =
            if (petTypeSpinner.selectedItemPosition == 0) "" else petTypeSpinner.selectedItem.toString()
        val petBreed = petBreedField.text.toString().trim()
        val petGender =
            if (petGenderSpinner.selectedItemPosition == 0) "" else petGenderSpinner.selectedItem.toString()

        val day = petBirthdayPicker.dayOfMonth
        val month = petBirthdayPicker.month + 1
        val year = petBirthdayPicker.year
        petBirthday = "%04d-%02d-%02d".format(year, month, day)

        val microchip = microchipField.text.toString().trim()
        val tattoo = tattooField.text.toString().trim()
        val neuterSpay = when (neuterSpaySpinner.selectedItemPosition) {
            1 -> "Yes"
            2 -> "No"
            3 -> "Unknown"
            else -> ""
        }
        val petNotes = petNotesField.text.toString().trim()

        if (tagId.isEmpty() || activationCode.isEmpty() || petName.isEmpty() || petType.isEmpty() || petBreed.isEmpty() || petGender.isEmpty() || petBirthday.isEmpty() || selectedImageUri == null) {
            Toast.makeText(this, R.string.required_fields_missing, Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Prepare multipart request
        saveButton.isEnabled = false

        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("userID", userID.toString()).addFormDataPart("pet_tag_ID", tagId)
            .addFormDataPart("pet_activation_code", activationCode)
            .addFormDataPart("pet_name", petName).addFormDataPart("pet_type", petType)
            .addFormDataPart("pet_breed", petBreed).addFormDataPart("pet_gender", petGender)
            .addFormDataPart("pet_birthday", petBirthday)
            .addFormDataPart("pet_microchip", microchip).addFormDataPart("pet_tat", tattoo)
            .addFormDataPart("pet_neutspay", neuterSpay).addFormDataPart("pet_notes", petNotes)

        // 3. Attach photo with size guard
        try {
            val uri = selectedImageUri!!
            val pfd = contentResolver.openFileDescriptor(uri, "r") ?: run {
                showToastAndEnable(R.string.image_read_error)
                return
            }
            val fileSize = pfd.statSize
            pfd.close()  // close FD

            if (fileSize > MAX_IMAGE_SIZE_BYTES) {
                val mb = MAX_IMAGE_SIZE_BYTES / 1_048_576
                showToastAndEnable(getString(R.string.image_too_large, mb))
                return
            }

            val inputStream = contentResolver.openInputStream(uri) ?: run {
                showToastAndEnable(R.string.image_read_error)
                return
            }

            val fileName = getFileName(uri)
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"

            val requestBody = object : RequestBody() {
                override fun contentType() = mimeType.toMediaTypeOrNull()
                override fun contentLength() = fileSize
                override fun writeTo(sink: BufferedSink) {
                    inputStream.source().use { src -> sink.writeAll(src) }
                }
            }
            builder.addFormDataPart("pet_photo1", fileName, requestBody)

        } catch (e: Exception) {
            showToastAndEnable(R.string.image_read_error)
            return
        }

        val request = Request.Builder().url(REGISTER_URL).post(builder.build()).build()

        // 4. Execute
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = runOnUiThread {
                Log.e("RegisterTagActivity", "Network request failed: ${e.message}")
                showToastAndEnable(getString(R.string.upload_failed, e.message))
            }

            override fun onResponse(call: Call, response: Response) = runOnUiThread {
                val body = response.body?.string()
                val result = body?.let { JSONObject(it).optInt("result", -1) } ?: -1
                val message = body?.let { JSONObject(it).optString("message", "Unknown error") }
                    ?: "Unknown error"

                if (result == 1) {
                    Toast.makeText(this@RegisterTagActivity, message, Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    showToastAndEnable("Error: $message")
                }
            }
        })
    }

    // ─── Utility methods ───────────────────────────────────────────────────────
    private fun showToastAndEnable(@StringRes resId: Int) = showToastAndEnable(getString(resId))

    private fun showToastAndEnable(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_LONG).show()
        saveButton.isEnabled = true
    }

    private fun getFileName(uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    result = cursor.getString(
                        cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                    )
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) result = result?.substring(cut + 1)
        }
        return result ?: "image.jpg"
    }


    private fun handleBackPress() {
        finish()

    }

    private fun getUserIDFromPrefs(): Int =
        getSharedPreferences("prefs", Context.MODE_PRIVATE).getInt("userID", -1)
}
