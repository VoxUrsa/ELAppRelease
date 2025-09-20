package com.emergencyleash.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MembershipActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_membership)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Handle back navigation
        val chevronLeft: ImageView = findViewById(R.id.chevronLeft)
        chevronLeft.setOnClickListener {
            finish() // Close the current activity
        }

        // Set onClickListeners for buttons
        findViewById<View>(R.id.cancelButton).setOnClickListener { closeActivity() }
        // NEW – confirm before sending the user to their browser
        findViewById<View>(R.id.getPlusButton).setOnClickListener { showExternalSiteConfirmation() }

    }

    // Close activity when cancel button or chevron is pressed
    private fun closeActivity() {
        finish() // Exits the activity
    }

    // Open web browser with the specified URL when "Get Plus Now" button is clicked
    private fun openEmergencyLeashWebsite() {
        val url = "https://emergencyleash.com/product/emergency-leash-tag/"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent) // Opens the URL in a web browser
    }

    /** Play-Store compliance – warn user that an external site will open. */
    private fun showExternalSiteConfirmation() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.external_site_title))
            .setMessage(getString(R.string.external_site_message))
            .setPositiveButton(getString(R.string.continue_label)) { _, _ ->
                openEmergencyLeashWebsite()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

}
