package com.emergencyleash.app

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.text.SimpleDateFormat
import java.util.*

/**
 * A BottomSheetDialogFragment for editing a field.
 */
class FieldEditFragment : BottomSheetDialogFragment() {

    // Variables to store arguments
    private var fieldId: String? = null
    private var initialValue: String? = null
    private var title: String? = null

    // Listener to communicate back to the activity
    private var listener: FieldEditListener? = null

    // UI Components
    private lateinit var chevronDown: ImageView
    private lateinit var editFieldTitle: TextView
    private lateinit var editTextField: EditText
    private lateinit var genderSpinner: Spinner
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var inputContainer: LinearLayout
    private lateinit var selectedDateTextView: TextView
    private lateinit var datePicker: DatePicker

    // Veterinarian contact UI components
    private lateinit var vetNameField: EditText
    private lateinit var vetAddress1Field: EditText
    private lateinit var vetAddress2Field: EditText
    private lateinit var vetCityField: EditText
    private lateinit var vetStateField: EditText
    private lateinit var vetZipField: EditText
    private lateinit var vetEmailField: EditText
    private lateinit var vetContactContainer: LinearLayout

    // Date components
    private val selectedDate: Calendar = Calendar.getInstance()

    /**
     * Interface for communicating the saved value back to the activity.
     */
    interface FieldEditListener {
        fun onFieldSaved(fieldId: String, newValue: String)
    }

    /**
     * Attach the listener to the context.
     */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FieldEditListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement FieldEditListener")
        }
    }

    /**
     * Retrieve arguments passed to the fragment.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            fieldId = it.getString(ARG_FIELD_ID)
            initialValue = it.getString(ARG_INITIAL_VALUE)
            title = it.getString(ARG_TITLE)
            Log.d("FieldEditFragment", "Arguments - fieldId: $fieldId, initialValue: $initialValue, title: $title")
        }
    }

    /**
     * Inflate the layout.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_edit_field, container, false)
    }

    /**
     * Set up the view components and handle interactions.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Initialize UI components
        chevronDown = view.findViewById(R.id.chevronDown)
        editFieldTitle = view.findViewById(R.id.editFieldTitle)
        editTextField = view.findViewById(R.id.editTextField)
        genderSpinner = view.findViewById(R.id.genderSpinner)
        saveButton = view.findViewById(R.id.saveButton)
        cancelButton = view.findViewById(R.id.cancelButton)
        inputContainer = view.findViewById(R.id.inputContainer)
        selectedDateTextView = view.findViewById(R.id.selectedDateTextView)
        datePicker = view.findViewById(R.id.datePicker)

        // Veterinarian contact fields
        vetContactContainer = view.findViewById(R.id.vetContactContainer)
        vetNameField = view.findViewById(R.id.vetNameField)
        vetAddress1Field = view.findViewById(R.id.vetAddress1Field)
        vetAddress2Field = view.findViewById(R.id.vetAddress2Field)
        vetCityField = view.findViewById(R.id.vetCityField)
        vetStateField = view.findViewById(R.id.vetStateField)
        vetZipField = view.findViewById(R.id.vetZipField)
        vetEmailField = view.findViewById(R.id.vetEmailField)

        // Set the title
        editFieldTitle.text = title
        Log.d("FieldEditFragment", "Setting title: $title")

        // Set up the chevron down icon to close the dialog
        chevronDown.setOnClickListener {
            Log.d("FieldEditFragment", "Chevron clicked - dismissing fragment")
            dismiss()
        }

        // Set up the cancel button to close the dialog
        cancelButton.setOnClickListener {
            Log.d("FieldEditFragment", "Cancel button clicked - dismissing fragment")
            dismiss()
        }

        // Determine the input type based on the fieldId
        when (fieldId) {
            "pet_gender" -> {
                // Use Spinner for Gender with integer mappings
                setupGenderSpinner()
            }
            "pet_birthday" -> {
                // Use embedded DatePicker for Birthday
                setupDatePicker()
            }
            "pet_type", "pet_neutspay" -> {
                // Use Spinner for other dropdown selection fields
                setupDropdown()
            }
            "pet_weight" -> {
                // Use NumberPicker for numeric input
                setupNumberPicker()
            }
            "vet_contact" -> {
                // Show veterinarian contact fields and hide others
                vetContactContainer.visibility = View.VISIBLE
                editTextField.visibility = View.GONE
                genderSpinner.visibility = View.GONE
                datePicker.visibility = View.GONE
                selectedDateTextView.visibility = View.GONE

                // Populate fields with existing data
                populateVetContactFields(initialValue)
            }
            else -> {
                // Use EditText for text input
                setupEditText()
            }
        }

        // Handle save button click
        saveButton.setOnClickListener {
            Log.d("FieldEditFragment", "Save button clicked")
            val newValue = when (fieldId) {
                "vet_contact" -> collectVetContactData()
                else -> getCurrentInputValue()
            }

            listener?.onFieldSaved(fieldId ?: "", newValue)
            dismiss()
        }
    }

    /**
     * Set up the EditText for text input fields.
     */
    private fun setupEditText() {
        editTextField.visibility = View.VISIBLE
        genderSpinner.visibility = View.GONE
        datePicker.visibility = View.GONE
        selectedDateTextView.visibility = View.GONE
        vetContactContainer.visibility = View.GONE

        // Set the initial value
        editTextField.setText(initialValue)

        // Handle "Done" action on keyboard
        editTextField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveButton.performClick()
                true
            } else {
                false
            }
        }
    }

    /**
     * Set up the Spinner for Gender field with integer mappings.
     */
    private fun setupGenderSpinner() {
        editTextField.visibility = View.GONE
        genderSpinner.visibility = View.VISIBLE
        datePicker.visibility = View.GONE
        selectedDateTextView.visibility = View.GONE
        vetContactContainer.visibility = View.GONE

        // Define the options and their corresponding integer values
        val options = listOf("Unknown", "Male", "Female")
        val values = listOf("0", "1", "2")

        // Create an ArrayAdapter for the Spinner
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        genderSpinner.adapter = adapter

        // Set the initial selection based on the initialValue
        val initialIndex = values.indexOf(initialValue)
        if (initialIndex != -1) {
            genderSpinner.setSelection(initialIndex)
        } else {
            genderSpinner.setSelection(0)
        }
    }

    /**
     * Sets up the embedded DatePicker for Pet Birthday field.
     */
    private fun setupDatePicker() {
        editTextField.visibility = View.GONE
        genderSpinner.visibility = View.GONE
        datePicker.visibility = View.VISIBLE
        selectedDateTextView.visibility = View.VISIBLE
        vetContactContainer.visibility = View.GONE

        // Initialize the DatePicker with the stored date or current date
        if (!initialValue.isNullOrEmpty()) {
            try {
                val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val date = formatter.parse(initialValue!!)
                if (date != null) {
                    selectedDate.time = date
                    datePicker.init(
                        selectedDate.get(Calendar.YEAR),
                        selectedDate.get(Calendar.MONTH),
                        selectedDate.get(Calendar.DAY_OF_MONTH)
                    ) { _, year, monthOfYear, dayOfMonth ->
                        selectedDate.set(Calendar.YEAR, year)
                        selectedDate.set(Calendar.MONTH, monthOfYear)
                        selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                        updateSelectedDateTextView()
                    }
                }
            } catch (e: Exception) {
                val today = Calendar.getInstance()
                selectedDate.time = today.time
                datePicker.init(
                    today.get(Calendar.YEAR),
                    today.get(Calendar.MONTH),
                    today.get(Calendar.DAY_OF_MONTH)
                ) { _, year, monthOfYear, dayOfMonth ->
                    selectedDate.set(Calendar.YEAR, year)
                    selectedDate.set(Calendar.MONTH, monthOfYear)
                    selectedDate.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    updateSelectedDateTextView()
                }
            }
        }
        updateSelectedDateTextView()
    }

    /**
     * Set up the Spinner for other dropdown selection fields.
     */
    private fun setupDropdown() {
        editTextField.visibility = View.GONE
        genderSpinner.visibility = View.VISIBLE
        datePicker.visibility = View.GONE
        selectedDateTextView.visibility = View.GONE
        vetContactContainer.visibility = View.GONE

        val options = when (fieldId) {
            "pet_type" -> arrayOf("Dog", "Cat", "Other")
            "pet_neutspay" -> arrayOf("Yes", "No", "Unknown")
            else -> emptyArray()
        }

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, options)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        genderSpinner.adapter = adapter

        val initialIndex = options.indexOf(initialValue)
        if (initialIndex != -1) {
            genderSpinner.setSelection(initialIndex)
        } else {
            genderSpinner.setSelection(0)
        }
    }

    /**
     * Populates veterinarian contact fields with initial values.
     */
    private fun populateVetContactFields(initialValue: String?) {
        val lines = initialValue?.split("\n") ?: emptyList()
        vetNameField.setText(lines.getOrNull(0) ?: "")
        vetAddress1Field.setText(lines.getOrNull(1) ?: "")
        vetAddress2Field.setText(lines.getOrNull(2) ?: "")
        val cityStateZip = lines.getOrNull(3)?.split(",") ?: emptyList()
        vetCityField.setText(cityStateZip.getOrNull(0) ?: "")
        val stateZip = cityStateZip.getOrNull(1)?.trim()?.split(" ") ?: emptyList()
        vetStateField.setText(stateZip.getOrNull(0) ?: "")
        vetZipField.setText(stateZip.getOrNull(1) ?: "")
        vetEmailField.setText(lines.getOrNull(4) ?: "")
    }

    /**
     * Collects veterinarian contact data and formats it as a single string.
     */
    private fun collectVetContactData(): String {
        val vetName = vetNameField.text.toString().trim()
        val vetAddress1 = vetAddress1Field.text.toString().trim()
        val vetAddress2 = vetAddress2Field.text.toString().trim()
        val vetCity = vetCityField.text.toString().trim()
        val vetState = vetStateField.text.toString().trim()
        val vetZip = vetZipField.text.toString().trim()
        val vetEmail = vetEmailField.text.toString().trim()

        return "$vetName\n$vetAddress1\n$vetAddress2\n$vetCity, $vetState $vetZip\n$vetEmail"
    }
    /**
     * Updates the setupNumberPicker function to use a single NumberPicker with ID numberPicker1.
     */
    private fun setupNumberPicker() {
        // Hide irrelevant views
        editTextField.visibility = View.GONE
        genderSpinner.visibility = View.GONE
        datePicker.visibility = View.GONE
        selectedDateTextView.visibility = View.GONE
        vetContactContainer.visibility = View.GONE

        // Get reference to the single NumberPicker
        val numberPicker = view?.findViewById<NumberPicker>(R.id.numberPicker1)

        // Ensure visibility
        numberPicker?.visibility = View.VISIBLE

        // Set permissible range for the picker (e.g., 1–999)
        numberPicker?.minValue = 1
        numberPicker?.maxValue = 999

        // Set the initial value if provided, defaulting to 1
        numberPicker?.value = initialValue?.toIntOrNull() ?: 1
    }

    /**
     * Adjusts getCurrentInputValue to handle the single NumberPicker for weight.
     */
    private fun getCurrentInputValue(): String {
        // 1) If user is editing plain text in EditText
        if (editTextField.visibility == View.VISIBLE) {
            return editTextField.text.toString()
        }

        // 2) If user is using a Spinner (e.g., genderSpinner, pet_type, pet_neutspay)
        if (genderSpinner.visibility == View.VISIBLE) {
            return genderSpinner.selectedItem.toString()
        }

        // 3) If user is picking a date (pet_birthday)
        if (datePicker.visibility == View.VISIBLE) {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return formatter.format(selectedDate.time)
        }

        // 4) If user is editing veterinarian contact fields
        if (vetContactContainer.visibility == View.VISIBLE) {
            return collectVetContactData() // The method you already have
        }

        // 5) If user is using the single NumberPicker for weight
        val numberPicker = view?.findViewById<NumberPicker>(R.id.numberPicker1)
        if (numberPicker != null && numberPicker.visibility == View.VISIBLE) {
            return numberPicker.value.toString()
        }

        // 6) Otherwise, no recognized input
        return ""
    }


    /**
     * Updates the TextView to display the selected date.
     */
    private fun updateSelectedDateTextView() {
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateString = formatter.format(selectedDate.time)
        selectedDateTextView.text = getString(R.string.selected_date, dateString)
    }

    /**
     * Detach the listener when fragment is destroyed.
     */
    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        private const val ARG_FIELD_ID = "fieldId"
        private const val ARG_INITIAL_VALUE = "initialValue"
        private const val ARG_TITLE = "title"

        /**
         * Factory method to create a new instance of FieldEditFragment.
         */
        @JvmStatic
        fun newInstance(fieldId: String, initialValue: String, title: String) =
            FieldEditFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_FIELD_ID, fieldId)
                    putString(ARG_INITIAL_VALUE, initialValue)
                    putString(ARG_TITLE, title)
                }
            }
    }
}