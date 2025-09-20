package com.emergencyleash.app

import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class EnterZipCodeFragment : BottomSheetDialogFragment() {

    private var listener: ZipCodeListener? = null

    interface ZipCodeListener {
        fun onZipCodeEntered(zipCode: String)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = if (parentFragment != null) {
            parentFragment as? ZipCodeListener
        } else {
            context as? ZipCodeListener
        }
        if (listener == null) {
            throw RuntimeException("$context must implement ZipCodeListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_enter_zip_code, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val zipCodeEditText = view.findViewById<EditText>(R.id.zipCodeEditText)
        val cancelButton = view.findViewById<Button>(R.id.cancelButton)
        val continueButton = view.findViewById<Button>(R.id.continueButton)

        cancelButton.setOnClickListener {
            dismiss()
        }

        continueButton.setOnClickListener {
            val zipCode = zipCodeEditText.text.toString().trim()
            if (zipCode.isNotEmpty()) {
                listener?.onZipCodeEntered(zipCode)
                dismiss()
            } else {
                Toast.makeText(context, "Please enter a zip code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }
}
