package com.emergencyleash.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class MyPetsListAdapter(
    private var myPetsList: List<Pet>,
    private val onEditClick: (Pet) -> Unit // Callback for the Edit click
) : RecyclerView.Adapter<MyPetsListAdapter.MyPetsListHolder>() {

    class MyPetsListHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val petPhoto: ImageView = itemView.findViewById(R.id.pet_photo)
        val petName: TextView = itemView.findViewById(R.id.pet_name)
        val petType: TextView = itemView.findViewById(R.id.pet_type)
        val petGender: TextView = itemView.findViewById(R.id.pet_gender)
        val petAge: TextView = itemView.findViewById(R.id.pet_age)
        val address1: TextView = itemView.findViewById(R.id.address1)
        val address2: TextView = itemView.findViewById(R.id.address2)
        val city: TextView = itemView.findViewById(R.id.city)
        val state: TextView = itemView.findViewById(R.id.state)
        val zip: TextView = itemView.findViewById(R.id.zip)
        val imageViewEdit: ImageView = itemView.findViewById(R.id.imageViewEdit) // ImageView acting as Edit button
        val editText: TextView = itemView.findViewById(R.id.editText) // TextView acting as Edit button
        val imageViewGender: ImageView = itemView.findViewById(R.id.imageViewGender) // ImageView for gender icon
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyPetsListHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.my_pets_list, parent, false)
        return MyPetsListHolder(view)
    }

    override fun onBindViewHolder(holder: MyPetsListHolder, position: Int) {
        val pet = myPetsList[position]

        // Load pet photo using Glide
        Glide.with(holder.itemView.context)
            .load(pet.pet_photo)
            .into(holder.petPhoto)

        // Bind pet details to TextViews changes
        holder.petName.text = pet.pet_name
        holder.petType.text = pet.pet_type
        holder.petGender.text = pet.pet_gender
        holder.petAge.text = pet.pet_age.toString()
        holder.address1.text = pet.address1
        holder.city.text = pet.city
        holder.state.text = pet.state
        holder.zip.text = pet.zip.toString()

        // Hide address2 if it's empty or null; otherwise, show and set its text.
        if (pet.address2.isNullOrBlank()) {
            holder.address2.visibility = View.GONE
        } else {
            holder.address2.visibility = View.VISIBLE
            holder.address2.text = pet.address2
        }

        // Set the gender icon based on the pet's gender
        when {
            pet.pet_gender.equals("Male", ignoreCase = true) -> {
                holder.imageViewGender.setImageResource(R.drawable.male) // Male icon
            }
            pet.pet_gender.equals("Female", ignoreCase = true) -> {
                holder.imageViewGender.setImageResource(R.drawable.female) // Female icon
            }
            else -> {
                holder.imageViewGender.setImageResource(R.drawable.unknown_gender) // Optional fallback
            }
        }

        // Set click listeners on the Edit icon and the Edit text
        holder.imageViewEdit.setOnClickListener {
            onEditClick(pet) // Trigger callback when ImageViewEdit is clicked
        }
        holder.editText.setOnClickListener {
            onEditClick(pet) // Trigger callback when editText is clicked
        }
    }


    override fun getItemCount(): Int = myPetsList.size

    // Method to update the data
    fun updateData(newPetsList: List<Pet>) {
        myPetsList = newPetsList
        notifyDataSetChanged()
    }
}


