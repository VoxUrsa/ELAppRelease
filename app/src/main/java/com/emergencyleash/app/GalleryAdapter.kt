package com.emergencyleash.app

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class GalleryAdapter(
    private val context: Context,
    private val imageUrls: List<String>,
    private val maxImages: Int,
    private val onAddImageClick: () -> Unit,
    private val onImageClick: (Uri) -> Unit // New parameter for image click listener
) : RecyclerView.Adapter<GalleryAdapter.ViewHolder>() {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView? = itemView.findViewById(R.id.galleryImageView)
        val addIcon: ImageView? = itemView.findViewById(R.id.addIcon)
    }

    override fun getItemViewType(position: Int): Int {
        // if position is the “last” item AND we haven’t hit the limit => it’s the add item
        // otherwise => it's a real image
        return if (position == imageUrls.size && imageUrls.size < maxImages) {
            ITEM_TYPE_ADD_IMAGE
        } else {
            ITEM_TYPE_IMAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (viewType == ITEM_TYPE_ADD_IMAGE) {
            R.layout.item_add_image
        } else {
            R.layout.item_gallery_image
        }
        val view = LayoutInflater.from(context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // If it’s the add item
        if (getItemViewType(position) == ITEM_TYPE_ADD_IMAGE) {
            holder.addIcon?.setOnClickListener {
                onAddImageClick()
            }
        } else {
            val imageUrl = imageUrls[position]  // real image
            holder.imageView?.let { imageView ->
                val uri = Uri.parse(imageUrl)
                Glide.with(context)
                    .load(uri)
                    .placeholder(R.drawable.ic_pet_placeholder)
                    .into(imageView)

                imageView.setOnClickListener {
                    onImageClick(uri)
                }
            }
        }
    }


    override fun getItemCount(): Int {
        // Real images plus (maybe) one extra for "Add"
        val realCount = imageUrls.size
        return if (realCount < maxImages) {
            realCount + 1 // +1 for the Add placeholder
        } else {
            realCount
        }
    }

    companion object {
        private const val ITEM_TYPE_IMAGE = 0
        private const val ITEM_TYPE_ADD_IMAGE = 1
    }
}
