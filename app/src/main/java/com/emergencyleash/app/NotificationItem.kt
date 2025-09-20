package com.emergencyleash.app

data class NotificationItem(
    val id: Int,
    val subject: String,
    val message: String,
    var active: Boolean,
    val age: String,
    var isExpanded: Boolean = false,
    val imageUrl: String? = null // Use this to store the image URL from 'notification_image'
)