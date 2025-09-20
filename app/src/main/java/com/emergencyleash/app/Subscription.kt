package com.emergencyleash.app.models

data class Subscription(
    val name: String,
    val status: String,
    val startDate: String,
    val nextPaymentDate: String
)
