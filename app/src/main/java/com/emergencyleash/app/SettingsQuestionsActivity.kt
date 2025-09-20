package com.emergencyleash.app

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsQuestionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_questions)

        // FAQ: How strong is it? collapse/expand logic with chevron direction change
        val chevronDownHowStrong = findViewById<ImageView>(R.id.chevronDownHowStrong)
        val answerTextHowStrong = findViewById<TextView>(R.id.answerTextHowStrong)
        chevronDownHowStrong.setOnClickListener {
            toggleVisibilityWithChevron(answerTextHowStrong, chevronDownHowStrong)
        }

        // FAQ: How do you put it on the collar? collapse/expand logic with chevron direction change
        val chevronDownHowToPutOnCollar = findViewById<ImageView>(R.id.chevronDownHowToPutOnCollar)
        val answerTextHowToPutOnCollar = findViewById<TextView>(R.id.answerTextHowToPutOnCollar)
        chevronDownHowToPutOnCollar.setOnClickListener {
            toggleVisibilityWithChevron(answerTextHowToPutOnCollar, chevronDownHowToPutOnCollar)
        }

        // FAQ: Does it come with a warranty? collapse/expand logic with chevron direction change
        val chevronDown1 = findViewById<ImageView>(R.id.chevronDown1)
        val answerText1 = findViewById<TextView>(R.id.answerText1)
        chevronDown1.setOnClickListener {
            toggleVisibilityWithChevron(answerText1, chevronDown1)
        }

        // FAQ: Do you accept returns? collapse/expand logic with chevron direction change
        val chevronDown2 = findViewById<ImageView>(R.id.chevronDown2)
        val answerText2 = findViewById<TextView>(R.id.answerText2)
        chevronDown2.setOnClickListener {
            toggleVisibilityWithChevron(answerText2, chevronDown2)
        }

        // Back Button logic
        val backButton = findViewById<Button>(R.id.backButton)
        backButton.setOnClickListener {
            finish() // Close the activity and return to the previous screen
        }

        // Left Chevron (Back) logic
        val leftChevron = findViewById<ImageView>(R.id.chevronLeft)
        leftChevron.setOnClickListener {
            finish() // Same action as the back button
        }
    }

    private fun toggleVisibilityWithChevron(answerText: TextView, chevron: ImageView) {
        // Toggle visibility
        if (answerText.visibility == View.GONE) {
            answerText.visibility = View.VISIBLE
            // Change chevron to upward
            chevron.setImageResource(R.drawable.ic_chevron_up)
        } else {
            answerText.visibility = View.GONE
            // Change chevron back to downward
            chevron.setImageResource(R.drawable.ic_chevron_down)
        }
    }
}
