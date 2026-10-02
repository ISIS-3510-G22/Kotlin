package com.example.plansync.model

data class PlaceResult(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double? = null,
    val lng: Double? = null
)
