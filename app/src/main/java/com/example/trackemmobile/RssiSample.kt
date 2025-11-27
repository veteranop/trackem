package com.veteranop.trackem.data

data class RssiSample(
    val lat: Double,
    val lng: Double,
    val rssi: Int,
    val timestamp: Long = System.currentTimeMillis()
)