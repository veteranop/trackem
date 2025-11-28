package com.example.trackemmobile

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

private val MOBILE_VENDORS = setOf(
    "apple", "samsung", "google", "oneplus", "huawei", "lg",
    "motorola", "sony", "nokia", "htc", "xiaomi", "oppo", "vivo"
)

@Parcelize
data class DeviceFingerprint(
    var mac: String? = null,
    var customName: String? = null,
    var isWifiAP: Boolean = false,
    var bestApBssidForWigle: String? = null,
    var makeModel: String = "Unknown",
    var displayName: String = "Unknown Device",
    var wifiSsids: MutableSet<String> = mutableSetOf(),
    var requestCount: Int = 0,
    var lastRssi: Int = -100,
    var lastSeen: Long = 0,
    var bleAddress: String? = null,
    var bleName: String? = null,
    var bleAdvertInterval: Int? = null,
    var bleTxPower: Int? = null,
    var bleRssiSamples: MutableList<Int> = mutableListOf(),
    var bleManufacturerData: String? = null,
    var macVendor: String? = null,

    // RHID SYSTEM
    var rhid: Int = 0,
    var isRandomizedHost: Boolean = false,
    var allMacs: MutableSet<String> = mutableSetOf()
) : Parcelable {

    val finalDisplayName: String
        get() = when {
            customName != null -> customName!!
            isRandomizedHost -> "RHID-$rhid"
            else -> displayName
        }

    val isRandomizedMac: Boolean
        get() = mac?.let { m ->
            val first = m.substringBefore(':').toIntOrNull(16) ?: return@let false
            (first and 0x02) == 0x02
        } ?: false

    fun addMac(newMac: String) {
        if (newMac != mac) {
            allMacs.add(newMac)
            mac = newMac
        }
    }

    fun generateHostKey(): String {
        val prefix = mac?.take(8) ?: ""
        val rssiAvg = if (bleRssiSamples.isNotEmpty()) bleRssiSamples.average().toInt().toString() else lastRssi.toString()
        val ssidHash = wifiSsids.hashCode().toString()
        return "$prefix$rssiAvg$ssidHash"
    }

    fun addWifiProbe(ssid: String, rssi: Int, bssid: String) {
        lastSeen = System.currentTimeMillis()
        if (rssi > lastRssi) {
            lastRssi = rssi
            bestApBssidForWigle = bssid
        }
        wifiSsids.add(ssid)
        requestCount++
        updateVendorInfo()
    }

    fun addBleSample(rssi: Int, interval: Int?, txPower: Int?, mfgData: String?) {
        lastSeen = System.currentTimeMillis()
        lastRssi = rssi
        bleRssiSamples.add(rssi)
        if (bleRssiSamples.size > 10) bleRssiSamples.removeAt(0)
        if (interval != null) bleAdvertInterval = interval
        if (txPower != null) bleTxPower = txPower
        if (mfgData != null) bleManufacturerData = mfgData
        updateVendorInfo()
    }

    fun updateVendorInfo() {
        val vendor = macVendor?.lowercase() ?: ""
        val isMobile = MOBILE_VENDORS.any { vendor.contains(it) }

        makeModel = when {
            isWifiAP -> macVendor ?: "Wi-Fi AP"
            isMobile -> macVendor ?: "Mobile Device"
            wifiSsids.any() -> "Mobile Device"
            bleAddress != null -> "Bluetooth Device"
            else -> "Unknown"
        }

        // ONLY set displayName if it's still default
        if (displayName == "Unknown Device" && !isWifiAP) {
            displayName = bleName ?: macVendor ?: makeModel
        }

        // Show RHID if randomized
        if (isRandomizedHost) {
            displayName = "RHID-$rhid"
        }
    }

    fun fingerprintKey(): String = mac ?: bleAddress ?: "unknown"
}