package com.veteranop.trackem

data class SimpleDevice(
    val bleName: String?,
    val ssids: List<String>,
    val bssid: String?,
    val requestCount: Int
)