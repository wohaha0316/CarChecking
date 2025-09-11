package com.example.carchecking

data class CheckRow(
    val bl: String,
    val haju: String,
    val carInfo: String,
    val qty: String,
    val clearance: String,
    var isChecked: Boolean = false,
    var checkOrder: Int = 0,
    val isLabelRow: Boolean = false // TERMINAL 구분행 등
)
