package com.emergencyleash.app

data class Pet(
    val pet_ID: String,
    val pet_name: String,
    val pet_photo: String,
    val pet_type: String,
    val pet_gender: String,
    val pet_age: String,
    val address1: String,
    val address2: String?,
    val city: String,
    val state: String,
    val zip: String
)

