package com.emergencyleash.app

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class FlyerCreationActivity : AppCompatActivity() {

    private lateinit var selectedImageUrl: String
    private lateinit var imageRecyclerView: RecyclerView
    private val client = OkHttpClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flyer_creation)

        val descriptionField: EditText = findViewById(R.id.flyerDescription)
        val rewardField: EditText = findViewById(R.id.flyerReward)
        val contactField: EditText = findViewById(R.id.flyerContact)
        val generateFlyerBtn: Button = findViewById(R.id.generateFlyerBtn)
        imageRecyclerView = findViewById(R.id.imageRecyclerView)

        // Fetch pet poster data from the server
        fetchPetPosterData()

        generateFlyerBtn.setOnClickListener {
            val description = descriptionField.text.toString()
            val reward = rewardField.text.toString()
            val contact = contactField.text.toString()

            if (description.isNotEmpty() && reward.isNotEmpty() && contact.isNotEmpty() && ::selectedImageUrl.isInitialized) {
                generateFlyer(description, reward, contact)
            } else {
                Toast.makeText(this, "Please fill out all fields and select an image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchPetPosterData() {
        val petId = intent.getStringExtra("petId") ?: ""
        val userId = intent.getStringExtra("userId") ?: ""

        val requestBody = FormBody.Builder()
            .add("userID", userId)
            .add("pet_ID", petId)
            .build()

        val request = Request.Builder()
            .url("https://emergencyleash.com/wp-content/plugins/access-app/pull/pet-poster.php")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@FlyerCreationActivity, "Failed to load data", Toast.LENGTH_SHORT).show()
                    Log.e("FlyerCreationActivity", "Error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseData = response.body?.string()
                runOnUiThread {
                    if (response.isSuccessful && responseData != null) {
                        try {
                            parseAndLoadImages(responseData)
                        } catch (e: Exception) {
                            Log.e("FlyerCreationActivity", "Failed to parse JSON: ${e.message}")
                            Toast.makeText(this@FlyerCreationActivity, "Invalid server response", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@FlyerCreationActivity, "Failed to load data", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun parseAndLoadImages(responseData: String) {
        val jsonObject = JSONObject(responseData)
        val imageUrl = jsonObject.optString("pet_photo1")

        if (!imageUrl.isNullOrEmpty()) {
            selectedImageUrl = imageUrl
         //   setupImageRecyclerView(listOf(imageUrl))
        }
    }

/*    private fun setupImageRecyclerView(images: List<String>) {
        val galleryAdapter = GalleryAdapter(
            this,
            images,
            onAddImageClick = { *//* Handle "Add Image" click if needed *//* },
            onImageClick = { uri ->
                selectedImageUrl = uri.toString()
                Toast.makeText(this, "Image selected!", Toast.LENGTH_SHORT).show()
            }
        )

        imageRecyclerView.layoutManager = GridLayoutManager(this, 1) // Single image display
        imageRecyclerView.adapter = galleryAdapter
    }*/

    private fun generateFlyer(description: String, reward: String, contact: String) {
        val flyerBitmap = Bitmap.createBitmap(2550, 3300, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flyerBitmap)
        val paint = Paint()

        canvas.drawColor(Color.WHITE)

        paint.textSize = 80f
        paint.color = Color.RED
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("MISSING PET", flyerBitmap.width / 2f, 150f, paint)

        // Load and display selected image
        val petImage = Glide.with(this).asBitmap().load(selectedImageUrl).submit().get()
        val resizedPetImage = Bitmap.createScaledBitmap(petImage, 600, 600, false)
        canvas.drawBitmap(resizedPetImage, 975f, 200f, null)

        paint.textSize = 40f
        paint.color = Color.BLACK
        paint.textAlign = Paint.Align.LEFT
        canvas.drawText(description, 100f, 850f, paint)

        paint.textSize = 50f
        paint.color = Color.RED
        canvas.drawText("REWARD: $reward", 100f, 950f, paint)

        paint.textSize = 40f
        paint.color = Color.BLACK
        canvas.drawText("CALL: $contact", 100f, 1050f, paint)

        saveFlyerAsPDF(flyerBitmap)
        Toast.makeText(this, "Flyer generated!", Toast.LENGTH_SHORT).show()
    }

    private fun saveFlyerAsPDF(bitmap: Bitmap) {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(2550, 3300, 1).create()
        val page = pdfDocument.startPage(pageInfo)

        val canvas = page.canvas
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        pdfDocument.finishPage(page)

        val fileName = "Flyer_${System.currentTimeMillis()}.pdf"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/Flyers")
            }

            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                    }
                    Toast.makeText(this, "Flyer saved to Download/Flyers", Toast.LENGTH_LONG).show()
                } catch (e: IOException) {
                    e.printStackTrace()
                    Toast.makeText(this, "Failed to save flyer as PDF", Toast.LENGTH_SHORT).show()
                } finally {
                    pdfDocument.close()
                }
            }
        } else {
            val downloadsDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            try {
                FileOutputStream(file).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
                Toast.makeText(this, "Flyer saved to Downloads folder: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            } catch (e: IOException) {
                e.printStackTrace()
                Toast.makeText(this, "Failed to save flyer as PDF", Toast.LENGTH_SHORT).show()
            } finally {
                pdfDocument.close()
            }
        }
    }
}
