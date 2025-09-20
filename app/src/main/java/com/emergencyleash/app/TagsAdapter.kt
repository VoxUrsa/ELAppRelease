// TagsAdapter.kt
package com.emergencyleash.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.emergencyleash.app.R
import com.emergencyleash.app.models.Tag

class TagsAdapter(
    private val tagsList: List<Tag>
) : RecyclerView.Adapter<TagsAdapter.TagViewHolder>() {

    inner class TagViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tagNumberTextView: TextView = itemView.findViewById(R.id.tagNumberTextView)
        val petNameTextView: TextView = itemView.findViewById(R.id.petNameTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_tag, parent, false)
        return TagViewHolder(view)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        val tag = tagsList[position]
        holder.tagNumberTextView.text = "Tag Number: ${tag.tagNum}"
        holder.petNameTextView.text = "Pet Name: ${tag.petName}"
    }

    override fun getItemCount(): Int {
        return tagsList.size
    }
}
