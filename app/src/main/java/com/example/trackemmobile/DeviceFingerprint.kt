package com.example.trackemmobile

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

private val MOBILE_VENDORS = setOf(
    "apple", "samsung", "google", "oneplus", "huawei", "lg",
    "motorola", "sony", "nokia", "htc", "xiaomi", "oppo", "vivo",
    "intel", "microsoft", "dell", "lenovo", "hp"
)

@Parcelize
data class DeviceFingerprint(
    var mac: String? = null,
    // --- FIX: Add a field for the user's custom name ---
    var customName: String? = null,
    // --- End of Fix ---
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
    var macVendor: String? = null
) : Parcelable {

    // --- FIX: Prioritize customName in the displayName logic ---
    val finalDisplayName: String
        get() = customName ?: displayName
    // --- End of Fix ---

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

    fun updateVendorInfo() {
        val vendor = macVendor?.lowercase() ?: ""
        val isMobileVendor = MOBILE_VENDORS.any { vendor.contains(it) }

        makeModel = when {
            isWifiAP -> macVendor ?: "Wi-Fi AP"
            isMobileVendor -> macVendor ?: "Mobile Device"
            wifiSsids.any() -> "Mobile Device (Probe)"
            bleAddress != null -> "Bluetooth Device"
            else -> "Unknown"
        }

        if (!isWifiAP) {
            displayName = when {
                isMobileVendor -> bleName ?: macVendor ?: "Mobile Device"
                else -> bleName ?: makeModel
            }
        }
    }

    fun fingerprintKey(): String = mac ?: bleAddress ?: wifiSsids.hashCode().toString()

    companion object {
        fun merge(list: List<DeviceFingerprint>): DeviceFingerprint {
            if (list.isEmpty()) throw IllegalArgumentException("Cannot merge an empty list")
            val base = list[0].copy()
            base.wifiSsids = mutableSetOf()
            base.bleRssiSamples = mutableListOf()

            for (fp in list) {
                if (fp.lastSeen > base.lastSeen) {
                    base.lastSeen = fp.lastSeen
                    base.lastRssi = fp.lastRssi
                }
                base.requestCount += fp.requestCount
                base.wifiSsids.addAll(fp.wifiSsids)
                base.bleRssiSamples.addAll(fp.bleRssiSamples)
                if (fp.isWifiAP) base.isWifiAP = true
                if (fp.bleAdvertInterval != null) base.bleAdvertInterval = fp.bleAdvertInterval
                if (fp.bleTxPower != null) base.bleTxPower = fp.bleTxPower
                if (fp.bleManufacturerData != null) base.bleManufacturerData = fp.bleManufacturerData
                if (fp.bleAddress != null) base.bleAddress = fp.bleAddress
                if (fp.bleName != null) base.bleName = fp.bleName
                // --- FIX: Make sure customName is preserved during merge ---
                if (fp.customName != null) base.customName = fp.customName
                // --- End of Fix ---
                if (fp.displayName != "Unknown Device") base.displayName = fp.displayName
                base.macVendor = fp.macVendor ?: base.macVendor
            }

            base.bleRssiSamples = base.bleRssiSamples.takeLast(10).toMutableList()
            base.updateVendorInfo()
            return base
        }
    }
}
