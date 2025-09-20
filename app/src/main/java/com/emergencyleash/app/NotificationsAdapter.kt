package com.emergencyleash.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView

class NotificationsAdapter(
    private val notificationsList: List<NotificationItem>,
    private val onItemClick: (NotificationItem, Int) -> Unit
) : RecyclerView.Adapter<NotificationsAdapter.NotificationViewHolder>() {

    private var expandedPosition: Int = RecyclerView.NO_POSITION // Track expanded notification

    inner class NotificationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val notificationCard: MaterialCardView = itemView.findViewById(R.id.notificationCard)
        val notificationIcon: ImageView = itemView.findViewById(R.id.notificationIcon)
        val notificationTitle: TextView = itemView.findViewById(R.id.notificationTitle)
        val notificationMessage: TextView = itemView.findViewById(R.id.notificationMessage)
        val notificationTimeElapsed: TextView = itemView.findViewById(R.id.notificationTimeElapsed)
        val chevronIcon: ImageView = itemView.findViewById(R.id.chevronIcon)
        val markAsUnreadButton: Button = itemView.findViewById(R.id.markAsUnreadButton)
        val expandedImage: ImageView = itemView.findViewById(R.id.expandedImage) // Added this line
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notificationsList[position]

        holder.notificationTitle.text = notification.subject
        holder.notificationMessage.text = notification.message
        holder.notificationTimeElapsed.text = notification.age

        // Set background color based on active state
        val colorRes = if (notification.active) R.color.activeNotification else R.color.inactiveNotification
        holder.notificationCard.setCardBackgroundColor(
            ContextCompat.getColor(holder.itemView.context, colorRes)
        )

        // Load notification icon
        if (!notification.imageUrl.isNullOrEmpty()) {
            // Load image from URL using Glide
            Glide.with(holder.notificationIcon.context)
                .load(notification.imageUrl)
                .placeholder(android.R.drawable.ic_dialog_info) // Optional placeholder
                .error(android.R.drawable.ic_dialog_info)      // Optional error placeholder
                .into(holder.notificationIcon)
        } else {
            // Set default notification icon
            holder.notificationIcon.setImageResource(android.R.drawable.ic_dialog_info)
        }

        // Handle expanding/collapsing logic
        val isExpanded = notification.isExpanded

        if (isExpanded) {
            holder.notificationMessage.maxLines = Int.MAX_VALUE // Expand to show full message
            holder.chevronIcon.setImageResource(R.drawable.ic_chevron_up)
            holder.markAsUnreadButton.visibility = View.VISIBLE

            // Show expanded image if available
            if (!notification.imageUrl.isNullOrEmpty()) {
                holder.expandedImage.visibility = View.VISIBLE
                Glide.with(holder.expandedImage.context)
                    .load(notification.imageUrl)
                    .into(holder.expandedImage)
            } else {
                holder.expandedImage.visibility = View.GONE
            }
        } else {
            holder.notificationMessage.maxLines = 1 // Collapse to a single line
            holder.chevronIcon.setImageResource(R.drawable.ic_chevron_down)
            holder.markAsUnreadButton.visibility = View.GONE
            holder.expandedImage.visibility = View.GONE
        }

        // Handle card click
        holder.notificationCard.setOnClickListener {
            val previousExpandedPosition = expandedPosition
            expandedPosition = if (isExpanded) RecyclerView.NO_POSITION else position // Toggle expansion

            // Update the isExpanded state
            notification.isExpanded = !isExpanded
            if (previousExpandedPosition != RecyclerView.NO_POSITION && previousExpandedPosition != position) {
                notificationsList[previousExpandedPosition].isExpanded = false
                notifyItemChanged(previousExpandedPosition)
            }
            notifyItemChanged(position)

            // Pass both the NotificationItem and its position
            onItemClick(notification, position)
        }

        // Handle "Mark as Unread" button click (if applicable)
        holder.markAsUnreadButton.setOnClickListener {
            Toast.makeText(holder.itemView.context, "Marking notification as unread", Toast.LENGTH_SHORT).show()
            // Implement the functionality to mark as unread if needed
        }
    }

    override fun getItemCount(): Int = notificationsList.size
}
