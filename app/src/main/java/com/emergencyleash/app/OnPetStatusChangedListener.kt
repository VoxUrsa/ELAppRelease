package com.emergencyleash.app

interface OnPetStatusChangedListener {
    fun onPetStatusChanged(isLost: Boolean)
}