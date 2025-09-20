package com.emergencyleash.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityOptionsCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // 1. My Pets Section
        // a) Register Pet
        val cardRegisterPet = findViewById<CardView>(R.id.cardRegisterPet)
        cardRegisterPet.setOnClickListener {
            val intent = Intent(this, IntroVideoActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // b) Tag ID Inventory
        val cardTagIDInventory = findViewById<CardView>(R.id.cardTagIDInventory)
        cardTagIDInventory.setOnClickListener {
            val intent = Intent(this, SettingsTagActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // c) Tag Replacement (opens website)
        val cardTagReplacement = findViewById<CardView>(R.id.cardTagReplacement)
        cardTagReplacement.setOnClickListener {
            showExternalSiteConfirmation()
        }

        // d) Subscriptions
        val cardMembership = findViewById<CardView>(R.id.cardMembership)
        cardMembership.setOnClickListener {
            val intent = Intent(this, SettingsSubscriptionActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // 2. Dashboard Section
        // a) My Account
        val cardMyAccount = findViewById<CardView>(R.id.cardMyAccount)
        cardMyAccount.setOnClickListener {
            val intent = Intent(this, SettingsMyAccountActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // b) My Contacts
        val cardMyContacts = findViewById<CardView>(R.id.cardMyContacts)
        cardMyContacts.setOnClickListener {
            val intent = Intent(this, SettingsMyContactsActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // c) My Admins
        val cardMyAdmins = findViewById<CardView>(R.id.cardMyAdmins)
        cardMyAdmins.setOnClickListener {
            val intent = Intent(this, SettingsMyAdminsActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // d) My Addresses
        val cardMyAddresses = findViewById<CardView>(R.id.cardMyAddresses)
        cardMyAddresses.setOnClickListener {
            val intent = Intent(this, SettingsMyAddressesActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // 3. Options Section
        // a) Notifications
        val cardNotifications = findViewById<CardView>(R.id.cardNotifications)
        cardNotifications.setOnClickListener {
            val intent = Intent(this, SettingsNotificationsActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // b) Accessibility
        val cardAccessibility = findViewById<CardView>(R.id.cardAccessibility)
        cardAccessibility.setOnClickListener {
            val intent = Intent(this, SettingsAccessibilityActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // c) Measurements
        val cardMeasurements = findViewById<CardView>(R.id.cardMeasurements)
        cardMeasurements.setOnClickListener {
            val intent = Intent(this, SettingsMeasurementsActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // 4. Support Section
        // a) Privacy Policy
        val cardPrivacyPolicy = findViewById<CardView>(R.id.cardPrivacyPolicy)
        cardPrivacyPolicy.setOnClickListener {
            val intent = Intent(this, SettingsPrivacyPolicyActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // b) Terms of Use
        val cardTermsOfUse = findViewById<CardView>(R.id.cardTermsOfUse)
        cardTermsOfUse.setOnClickListener {
            val intent = Intent(this, SettingsTermsOfUseActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // c) Send Feedback
        val cardSendFeedback = findViewById<CardView>(R.id.cardSendFeedback)
        cardSendFeedback.setOnClickListener {
            val intent = Intent(this, SettingsSendFeedbackActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // d) FAQs
        val cardFAQ = findViewById<CardView>(R.id.cardFAQ)
        cardFAQ.setOnClickListener {
            val intent = Intent(this, SettingsQuestionsActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // 5. About Section
        val cardAbout = findViewById<CardView>(R.id.cardAbout)
        cardAbout.setOnClickListener {
            val intent = Intent(this, SettingsAboutActivity::class.java)
            val options = ActivityOptionsCompat.makeCustomAnimation(
                this, R.anim.slide_in_right, R.anim.slide_out_left
            )
            startActivity(intent, options.toBundle())
        }

        // 6. Logout Section
        val cardLogout = findViewById<CardView>(R.id.cardLogout)
        cardLogout.setOnClickListener {
            clearDataFromPrefs()
            // optional: reset "hasLoggedInBefore"
            val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
            sharedPreferences.edit().putBoolean("hasLoggedInBefore", false).apply()

            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }

        // Finally, load NavigationFragment for this screen
        val navigationFragment = NavigationFragment.newInstance("Settings")
        supportFragmentManager.beginTransaction()
            .replace(R.id.navigationFragmentContainer, navigationFragment).commit()
    }

    // Open web browser with the specified URL when Tag Replacement is clicked
    private fun openEmergencyLeashWebsiteTagReplacement() {
        val url = "https://emergencyleash.com/product/emergency-leash-tag-replacement/"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        }
        startActivity(Intent.createChooser(intent, "Open with"))
    }

    private fun clearDataFromPrefs() {
        val sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove("firebaseToken")
        editor.remove("userID")
        editor.apply()
    }

    /** Play-Store compliance – warn user that an external site will open. */
    private fun showExternalSiteConfirmation() {
        AlertDialog.Builder(this).setTitle(getString(R.string.external_site_title))
            .setMessage(getString(R.string.external_site_message))
            .setPositiveButton(getString(R.string.continue_label)) { _, _ ->
                openEmergencyLeashWebsiteTagReplacement()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

}
