package com.example.carchecking

data class VinData(
    val vin: String,
    val model: String,
    val color: String,
    var isConfirmed: Boolean
)