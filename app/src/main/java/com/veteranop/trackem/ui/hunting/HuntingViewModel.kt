package com.veteranop.trackem.ui.hunting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.veteranop.trackem.data.RssiSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.pow
import kotlin.math.sqrt

class HuntingViewModel : ViewModel() {
    companion object {
        var instance: HuntingViewModel? = null
        var isHunting = false
        var currentTargetMac = ""

        fun startHunting(mac: String, vm: HuntingViewModel) {
            instance = vm
            currentTargetMac = mac.uppercase()
            isHunting = true
            vm.clear()
        }

        fun stopHunting() {
            isHunting = false
            currentTargetMac = ""
            instance = null
        }
    }

    private val _samples = MutableStateFlow<List<RssiSample>>(emptyList())
    val samples = _samples.asStateFlow()

    private val _estimatedPosition = MutableStateFlow<LatLng?>(null)
    val estimatedPosition = _estimatedPosition.asStateFlow()

    private val _confidenceRadius = MutableStateFlow<Double?>(null)
    val confidenceRadius = _confidenceRadius.asStateFlow()

    fun addSample(lat: Double, lng: Double, rssi: Int) {
        viewModelScope.launch {
            val newSample = RssiSample(lat, lng, rssi)
            val updated = (_samples.value + newSample)
                .filter { it.timestamp > System.currentTimeMillis() - 90_000 }

            _samples.value = updated
            recalculate()
        }
    }

    private fun recalculate() {
        val list = _samples.value
        if (list.size < 3) {
            _estimatedPosition.value = null
            _confidenceRadius.value = null
            return
        }

        var sumLat = 0.0
        var sumLng = 0.0
        var totalWeight = 0.0
        var varianceSum = 0.0

        list.forEach { s ->
            val weight = (s.rssi + 100f).pow(2).toDouble()
            sumLat += s.lat * weight
            sumLng += s.lng * weight
            totalWeight += weight
        }

        val centroidLat = sumLat / totalWeight
        val centroidLng = sumLng / totalWeight
        val centroid = LatLng(centroidLat, centroidLng)
        _estimatedPosition.value = centroid

        list.forEach { s ->
            val weight = (s.rssi + 100f).pow(2).toDouble() / totalWeight
            val dist = distanceMeters(centroidLat, centroidLng, s.lat, s.lng)
            varianceSum += weight * dist * dist
        }
        _confidenceRadius.value = sqrt(varianceSum) * 1.2
    }

    fun clear() {
        _samples.value = emptyList()
        _estimatedPosition.value = null
        _confidenceRadius.value = null
    }

    private fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371000.0
        val φ1 = Math.toRadians(lat1)
        val φ2 = Math.toRadians(lat2)
        val Δφ = Math.toRadians(lat2 - lat1)
        val Δλ = Math.toRadians(lng2 - lng1)
        val a = kotlin.math.sin(Δφ / 2).pow(2) + kotlin.math.cos(φ1) * kotlin.math.cos(φ2) * kotlin.math.sin(Δλ / 2).pow(2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return R * c
    }
}