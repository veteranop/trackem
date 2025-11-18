package com.example.trackemmobile

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class ScanMode {
    MOBILE_DEVICES,
    BLUETOOTH,
    WIFI_APS
}

interface DeviceScannerListener {
    fun onDevicesUpdated(
        mobileDevices: List<DeviceFingerprint>,
        wifiApDevices: List<DeviceFingerprint>,
        bleOnlyDevices: List<DeviceFingerprint>
    )
}

class DeviceScanner(
    private val context: Context,
    private val listener: DeviceScannerListener,
    private val mode: ScanMode
) {
    private val handler = Handler(Looper.getMainLooper())
    private var wifiManager: WifiManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    var isRunning = false
        private set
    private val devices = ConcurrentHashMap<String, DeviceFingerprint>()
    private val TAG = "DeviceScanner"
    private val macLookupApiKey = "01ka9dt34ffqvtf73hmdv0sqyw01ka9dv6g0f92h093tdhxbqebnspr6bkxuclln"
    private val client = OkHttpClient()
    private val gson = Gson()
    // --- FIX: Add a reference to the custom names preferences ---
    private val customNamePrefs = context.getSharedPreferences("CustomNames", Context.MODE_PRIVATE)
    // --- End of Fix ---

    private val DEVICE_EXPIRATION_MS = 10000L
    private val UI_UPDATE_INTERVAL_MS = 2000L

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            if (!isRunning) return
            try {
                val results = wifiManager?.scanResults
                if (results != null) {
                    processWifiResults(results)
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Missing permission for wifiManager.scanResults", e)
            }
        }
    }

    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            if (!isRunning || result == null) return
            processBleResult(result)
        }
    }

    private val wifiScanRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            try {
                wifiManager?.startScan()
            } catch (e: SecurityException) {
                Log.e(TAG, "Missing permission for wifiManager.startScan()", e)
            }
            handler.postDelayed(this, 10000)
        }
    }

    private val uiUpdateRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            publishUpdate()
            handler.postDelayed(this, UI_UPDATE_INTERVAL_MS)
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        when (mode) {
            ScanMode.MOBILE_DEVICES, ScanMode.WIFI_APS -> startWifiScan()
            ScanMode.BLUETOOTH -> { /* No Wi-Fi */ }
        }
        if (mode != ScanMode.WIFI_APS) {
            startBleScan()
        }
        handler.postDelayed(uiUpdateRunnable, UI_UPDATE_INTERVAL_MS)
    }

    private fun startWifiScan() {
        wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        handler.post(wifiScanRunnable)
    }

    private fun startBleScan() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build()
        try {
            bluetoothAdapter?.bluetoothLeScanner?.startScan(null, scanSettings, bleCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth scanning permission missing on start.", e)
        }
    }

    fun stop() {
        if (!isRunning) return
        isRunning = false
        handler.removeCallbacks(wifiScanRunnable)
        handler.removeCallbacks(uiUpdateRunnable)
        try {
            context.unregisterReceiver(wifiReceiver)
        } catch (e: Exception) { /* Already unregistered */ }
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth scanning permission missing on stop.", e)
        }
    }

    private fun processWifiResults(results: List<android.net.wifi.ScanResult>) {
        for (result in results) {
            val mac = result.BSSID
            val fp = devices.getOrPut(mac) {
                // --- FIX: When creating a device, immediately apply its saved custom name ---
                val customName = customNamePrefs.getString(mac, null)
                DeviceFingerprint(mac = mac, customName = customName).also { fetchVendorForDevice(it) }
                // --- End of Fix ---
            }
            if (result.SSID.isNotEmpty()) {
                fp.isWifiAP = true
                fp.displayName = result.SSID
            }
            fp.addWifiProbe(result.SSID, result.level, result.BSSID)
        }
    }

    private fun processBleResult(result: ScanResult) {
        val mac = result.device.address
        val fp = devices.getOrPut(mac) {
            // --- FIX: When creating a device, immediately apply its saved custom name ---
            val customName = customNamePrefs.getString(mac, null)
            DeviceFingerprint(mac = mac, bleAddress = mac, customName = customName).also { fetchVendorForDevice(it) }
            // --- End of Fix ---
        }
        val interval = if (result.periodicAdvertisingInterval != 0) result.periodicAdvertisingInterval * 1.25 else null
        val txPower = if (result.txPower != 127) result.txPower else null
        val mfgData = result.scanRecord?.bytes?.let { bytesToHex(it) }

        fp.addBleSample(result.rssi, interval?.toInt(), txPower, mfgData)
        try {
            fp.bleName = result.device.name
        } catch(e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission to get device name for $mac")
        }
    }

    private fun fetchVendorForDevice(fp: DeviceFingerprint) {
        val macToLookup = fp.mac ?: return
        val formattedMac = macToLookup.replace(":", "").uppercase(Locale.ROOT)
        val url = "https://api.maclookup.app/v2/macs/$formattedMac?apiKey=$macLookupApiKey"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "MAC lookup failed for $macToLookup", e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "MAC lookup API error for $macToLookup: ${response.code}")
                    return
                }

                try {
                    val body = response.body?.string()
                    if (body != null) {
                        val result = gson.fromJson(body, MacLookupResult::class.java)
                        if (result.success && result.found) {
                            fp.macVendor = result.companyName
                            fp.updateVendorInfo()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse MAC lookup response for $macToLookup", e)
                }
            }
        })
    }

    private fun publishUpdate() {
        val now = System.currentTimeMillis()
        val expiredKeys = devices.filter { (now - it.value.lastSeen) > DEVICE_EXPIRATION_MS }.keys
        if (expiredKeys.isNotEmpty()) {
            expiredKeys.forEach { devices.remove(it) }
        }

        val allDevices = devices.values.toList()
        val wifiAps = allDevices.filter { it.isWifiAP }
        val nonApDevices = allDevices.filter { !it.isWifiAP }
        val (mobileDevices, bleOnlyDevices) = nonApDevices.partition { it.wifiSsids.isNotEmpty() }

        handler.post {
            listener.onDevicesUpdated(mobileDevices, wifiAps, bleOnlyDevices)
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }.uppercase(Locale.ROOT)
    }
}
