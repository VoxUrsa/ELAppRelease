package com.emergencyleash.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.cardview.widget.CardView

class NavigationFragment : Fragment() {

    companion object {
        private const val ACTIVE_ACTIVITY_KEY = "active_activity_key"

        fun newInstance(activeActivity: String): NavigationFragment {
            val fragment = NavigationFragment()
            val args = Bundle()
            args.putString(ACTIVE_ACTIVITY_KEY, activeActivity)
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var imageViewHome: ImageView
    private lateinit var imageViewPets: ImageView
    private lateinit var imageViewNotifications: ImageView
    private lateinit var imageViewSettings: ImageView

    private lateinit var titleHeaderContainer: CardView // For title visibility and styling
    private lateinit var settingsHeader: TextView // For updating the title text

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_navigation, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        imageViewHome = view.findViewById(R.id.imageViewHome)
        imageViewPets = view.findViewById(R.id.imageViewPets)
        imageViewNotifications = view.findViewById(R.id.imageViewNotifications)
        imageViewSettings = view.findViewById(R.id.imageViewSettings)

        titleHeaderContainer = view.findViewById(R.id.titleHeaderContainer) // Access CardView for the header
        settingsHeader = view.findViewById(R.id.settingsHeader) // Access TextView for the header

        setNavigationColorsAndTitle()

        // Set up navigation click listeners
        imageViewHome.setOnClickListener {
            navigateToActivity(LoggedInActivity::class.java)
        }

        imageViewPets.setOnClickListener {
            navigateToActivity(PetsActivity::class.java)
        }

        imageViewNotifications.setOnClickListener {
            navigateToActivity(NotificationsActivity::class.java)
        }

        imageViewSettings.setOnClickListener {
            navigateToActivity(SettingsActivity::class.java)
        }
    }

    private fun setNavigationColorsAndTitle() {
        val activeActivity = arguments?.getString(ACTIVE_ACTIVITY_KEY)

        val activeColor = ContextCompat.getColor(requireContext(), R.color.primaryMed)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.white)
        val activeTint = ContextCompat.getColor(requireContext(), R.color.white)
        val inactiveTint = ContextCompat.getColor(requireContext(), R.color.grey99)

        when (activeActivity) {
            "Home" -> {
                imageViewHome.setBackgroundColor(activeColor)
                imageViewHome.setColorFilter(activeTint)
                // Hide the header for the LoggedInActivity (Home)
                titleHeaderContainer.visibility = View.GONE
            }
            "Pets" -> {
                imageViewPets.setBackgroundColor(activeColor)
                imageViewPets.setColorFilter(activeTint)
                // Set header text to "My Pets" for PetsActivity
                titleHeaderContainer.visibility = View.VISIBLE
                settingsHeader.text = getString(R.string.my_pets)
            }
            "Notifications" -> {
                imageViewNotifications.setBackgroundColor(activeColor)
                imageViewNotifications.setColorFilter(activeTint)
                // Set header text to "Notifications" for NotificationsActivity
                titleHeaderContainer.visibility = View.VISIBLE
                settingsHeader.text = getString(R.string.notifications)
            }
            "Settings" -> {
                imageViewSettings.setBackgroundColor(activeColor)
                imageViewSettings.setColorFilter(activeTint)
                // Set header text to "Settings" for SettingsActivity
                titleHeaderContainer.visibility = View.VISIBLE
                settingsHeader.text = getString(R.string.settings)
            }
        }

        // Set the colors for inactive items
        if (activeActivity != "Home") {
            imageViewHome.setBackgroundColor(inactiveColor)
            imageViewHome.setColorFilter(inactiveTint)
        }
        if (activeActivity != "Pets") {
            imageViewPets.setBackgroundColor(inactiveColor)
            imageViewPets.setColorFilter(inactiveTint)
        }
        if (activeActivity != "Notifications") {
            imageViewNotifications.setBackgroundColor(inactiveColor)
            imageViewNotifications.setColorFilter(inactiveTint)
        }
        if (activeActivity != "Settings") {
            imageViewSettings.setBackgroundColor(inactiveColor)
            imageViewSettings.setColorFilter(inactiveTint)
        }
    }

    private fun navigateToActivity(activityClass: Class<*>) {
        val context = activity ?: return
        if (context::class.java != activityClass) {
            val intent = Intent(context, activityClass)
            startActivity(intent)
        }
    }
}
