package com.example.trackemmobile

data class SimpleDevice(
    val bleName: String?,
    val ssids: List<String>,
    val bssid: String?,
    val requestCount: Int
)