package com.emergencyleash.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class HeaderFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_header, container, false)

        // Find the button by ID
        val memberButton: Button = view.findViewById(R.id.memberButton)

        // Set the click listener for the button
        memberButton.setOnClickListener {
            // Start MembershipActivity when the button is clicked
            val intent = Intent(activity, MembershipActivity::class.java)
            startActivity(intent)
        }

        return view
    }
}
