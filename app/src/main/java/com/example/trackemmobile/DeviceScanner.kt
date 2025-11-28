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
import com.veteranop.trackem.ui.hunting.HuntingViewModel
import com.veteranop.trackem.utils.LocationHelper
import okhttp3.*
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class AppScanMode {
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
    private val mode: AppScanMode
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
    private val customNamePrefs = context.getSharedPreferences("CustomNames", Context.MODE_PRIVATE)

    private var scanCount = 0
    private var nextRhid = 1
    private val hostKeyToRhid = mutableMapOf<String, Int>()

    private val DEVICE_EXPIRATION_MS = 10000L
    private val UI_UPDATE_INTERVAL_MS = 2000L

    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(contxt: Context?, intent: Intent?) {
            if (!isRunning) return
            try {
                val results = wifiManager?.scanResults ?: return
                processWifiResults(results)
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
            AppScanMode.MOBILE_DEVICES, AppScanMode.WIFI_APS -> startWifiScan()
            AppScanMode.BLUETOOTH -> { /* No Wi-Fi */ }
        }
        if (mode != AppScanMode.WIFI_APS) {
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
        try { context.unregisterReceiver(wifiReceiver) } catch (e: Exception) { }
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth scanning permission missing on stop.", e)
        }
    }

    private fun processWifiResults(results: List<android.net.wifi.ScanResult>) {
        for (result in results) {
            val mac = result.BSSID
            var fp = devices.getOrPut(mac) {
                val customName = customNamePrefs.getString(mac, null)
                DeviceFingerprint(mac = mac, customName = customName).apply {
                    allMacs.add(mac)
                }
            }

            fp.addMac(mac)
            fp.addWifiProbe(result.SSID, result.level, result.BSSID)

            if (result.SSID.isNotEmpty()) {
                fp.isWifiAP = true
                fp.displayName = result.SSID
            }

            // RHID + RANDOMIZATION DETECTION
            if (scanCount >= 3 && fp.isRandomizedMac && !fp.isRandomizedHost) {
                val hostKey = fp.generateHostKey()
                val existingRhid = hostKeyToRhid[hostKey]
                if (existingRhid != null) {
                    fp.rhid = existingRhid
                } else {
                    fp.rhid = nextRhid++
                    hostKeyToRhid[hostKey] = fp.rhid
                }
                fp.isRandomizedHost = true
            }

            // NO MAC LOOKUP FOR RANDOMIZED HOSTS
            if (!fp.isRandomizedHost) {
                fetchVendorForDevice(fp)
            }

            // HUNTING MODE
            if (HuntingViewModel.isHunting &&
                mac.equals(HuntingViewModel.currentTargetMac, ignoreCase = true)) {
                val location = LocationHelper.getLastLocation(context) ?: continue
                HuntingViewModel.instance?.addSample(
                    lat = location.latitude,
                    lng = location.longitude,
                    rssi = result.level
                )
            }
        }
        scanCount++
    }

    private fun processBleResult(result: ScanResult) {
        val mac = result.device.address
        var fp = devices.getOrPut(mac) {
            val customName = customNamePrefs.getString(mac, null)
            DeviceFingerprint(mac = mac, bleAddress = mac, customName = customName).apply {
                allMacs.add(mac)
            }
        }

        val interval = if (result.periodicAdvertisingInterval != 0) result.periodicAdvertisingInterval * 1.25 else null
        val txPower = if (result.txPower != 127) result.txPower else null
        val mfgData = result.scanRecord?.bytes?.let { bytesToHex(it) }

        fp.addBleSample(result.rssi, interval?.toInt(), txPower, mfgData)

        try {
            fp.bleName = result.device.name
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_CONNECT permission to get device name for $mac")
        }

        // RHID + RANDOMIZATION FOR BLE
        if (scanCount >= 3 && fp.isRandomizedMac && !fp.isRandomizedHost) {
            val hostKey = fp.generateHostKey()
            val existingRhid = hostKeyToRhid[hostKey]
            if (existingRhid != null) {
                fp.rhid = existingRhid
            } else {
                fp.rhid = nextRhid++
                hostKeyToRhid[hostKey] = fp.rhid
            }
            fp.isRandomizedHost = true
        }

        if (!fp.isRandomizedHost) fetchVendorForDevice(fp)

        if (HuntingViewModel.isHunting &&
            result.device.address.equals(HuntingViewModel.currentTargetMac, ignoreCase = true)) {
            val location = LocationHelper.getLastLocation(context) ?: return
            HuntingViewModel.instance?.addSample(
                lat = location.latitude,
                lng = location.longitude,
                rssi = result.rssi
            )
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
                if (!response.isSuccessful) return
                try {
                    val body = response.body?.string() ?: return
                    val result = gson.fromJson(body, MacLookupResult::class.java)
                    if (result.success && result.found) {
                        fp.macVendor = result.companyName
                        fp.updateVendorInfo()
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
        expiredKeys.forEach { devices.remove(it) }

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