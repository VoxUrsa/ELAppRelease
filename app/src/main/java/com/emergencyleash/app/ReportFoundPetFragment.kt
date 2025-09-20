package com.emergencyleash.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.emergencyleash.app.databinding.FragmentReportFoundPetBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import okhttp3.*
import java.io.IOException
import com.emergencyleash.app.OnPetStatusChangedListener

class ReportFoundPetFragment : BottomSheetDialogFragment() {

    private val client = OkHttpClient()
    private var _binding: FragmentReportFoundPetBinding? = null
    private val binding get() = _binding!!

    private var petId: String? = null
    private var listener: OnPetStatusChangedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        petId = arguments?.getString("pet_ID")
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentReportFoundPetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onAttach(context: android.content.Context) {
        super.onAttach(context)
        listener = if (parentFragment is OnPetStatusChangedListener) {
            parentFragment as OnPetStatusChangedListener
        } else {
            context as? OnPetStatusChangedListener
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set click listener for cancel button
        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        // Set click listener for continue button
        binding.continueButton.setOnClickListener {
            petId?.let {
                reportPetAsFound(petId = it, isLost = false)
            } ?: run {
                Toast.makeText(requireContext(), "Pet ID is missing", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun reportPetAsFound(petId: String, isLost: Boolean) {
        val url = "https://emergencyleash.com/wp-content/plugins/access-app/push/pet-missing.php"
        val petMissingValue = if (isLost) "1" else "0"

        val formBody = FormBody.Builder()
            .add("pet_ID", petId)
            .add("pet_missing", petMissingValue)
            .build()

        Log.d("ReportFoundPetFragment", "Sending request to $url with pet_ID=$petId and pet_missing=$petMissingValue")

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        // Disable buttons to prevent multiple clicks
        binding.continueButton.isEnabled = false
        binding.cancelButton.isEnabled = false

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed to update pet status", Toast.LENGTH_SHORT).show()
                    // Re-enable buttons
                    binding.continueButton.isEnabled = true
                    binding.cancelButton.isEnabled = true
                }
            }

            override fun onResponse(call: Call, response: Response) {
                activity?.runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "Pet reported as found successfully.", Toast.LENGTH_SHORT).show()
                        listener?.onPetStatusChanged(false)
                        dismiss()
                    } else {
                        Toast.makeText(requireContext(), "Failed to update pet status", Toast.LENGTH_SHORT).show()
                        // Re-enable buttons
                        binding.continueButton.isEnabled = true
                        binding.cancelButton.isEnabled = true
                    }
                }
            }
        })
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(petId: String): ReportFoundPetFragment {
            val fragment = ReportFoundPetFragment()
            val args = Bundle()
            args.putString("pet_ID", petId)
            fragment.arguments = args
            return fragment
        }
    }
}
