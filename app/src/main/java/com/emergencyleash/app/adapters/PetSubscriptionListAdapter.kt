package com.emergencyleash.app.adapters

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.emergencyleash.app.Pet
import com.emergencyleash.app.R

class PetSubscriptionListAdapter(
    private var petList: List<Pet>,
    private val onPetSelected: (Pet) -> Unit
) : RecyclerView.Adapter<PetSubscriptionListAdapter.SubscriptionViewHolder>() {

    private var selectedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SubscriptionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pet_subscription, parent, false)
        return SubscriptionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SubscriptionViewHolder, position: Int) {
        val currentPet = petList[position]
        holder.bind(currentPet, position)
    }

    override fun getItemCount(): Int = petList.size

    fun updateData(newPetList: List<Pet>) {
        petList = newPetList
        selectedPosition = -1
        notifyDataSetChanged()
    }

    inner class SubscriptionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val petImageView: ImageView = itemView.findViewById(R.id.petImageView)
        private val petNameTextView: TextView = itemView.findViewById(R.id.petNameTextView)
        private val petSelectRadio: RadioButton = itemView.findViewById(R.id.petSelectRadio)

        fun bind(pet: Pet, position: Int) {
            petNameTextView.text = pet.pet_name

            // Get the context from the itemView
            val context = itemView.context

            // 1) If the context is an Activity, check whether it’s destroyed or finishing
            //    If it is, skip loading the image to avoid the crash.
            val activity = context as? Activity
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                // Skip loading the image to avoid "You cannot start a load for a destroyed activity"
            } else {
                // Safe to load with Glide
                Glide.with(context)
                    .load(pet.pet_photo)
                    .placeholder(R.drawable.ic_pet_placeholder)
                    .error(R.drawable.ic_alert)
                    .into(petImageView)
            }

            petSelectRadio.isChecked = (position == selectedPosition)
            itemView.setOnClickListener { handleSelection(position) }
            petSelectRadio.setOnClickListener { handleSelection(position) }
        }

        private fun handleSelection(position: Int) {
            if (position != selectedPosition) {
                val oldPosition = selectedPosition
                selectedPosition = position
                notifyItemChanged(oldPosition)
                notifyItemChanged(selectedPosition)
            }
            onPetSelected(petList[position])
        }
    }
}
