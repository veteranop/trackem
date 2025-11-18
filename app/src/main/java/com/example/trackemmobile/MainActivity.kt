package com.example.trackemmobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Base64
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trackemmobile.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), DeviceScannerListener {

    private var scanner: DeviceScanner? = null
    private lateinit var mobileDeviceAdapter: DeviceAdapter
    private lateinit var wifiApAdapter: DeviceAdapter
    private lateinit var bleOnlyDeviceAdapter: DeviceAdapter

    private val ignored = mutableSetOf<String>()
    private var targetDevice: DeviceFingerprint? = null
    private lateinit var binding: ActivityMainBinding

    private var pendingScanMode: ScanMode? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // After the dialog returns, onResume will handle re-checking permissions.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.trackemLogo.setImageResource(R.drawable.trackem_logo)

        binding.btnMobileDeviceScan.setOnClickListener { checkPermissionsAndStartScan(ScanMode.MOBILE_DEVICES) }
        binding.btnBluetoothScan.setOnClickListener { checkPermissionsAndStartScan(ScanMode.BLUETOOTH) }
        binding.btnWifiApScan.setOnClickListener { checkPermissionsAndStartScan(ScanMode.WIFI_APS) }

        val deviceActionHandler: (DeviceFingerprint, String) -> Unit = { device, action ->
            when (action) {
                "details" -> {
                    val intent = Intent(this, DetailActivity::class.java).apply {
                        putExtra("device", device)
                    }
                    startActivity(intent)
                }
                // --- FIX: Handle the new "rename" action ---
                "rename" -> {
                    showRenameDialog(device)
                }
                // --- End of Fix ---
                "target" -> {
                    targetDevice = device
                    Toast.makeText(this, "Target set to ${device.finalDisplayName}", Toast.LENGTH_SHORT).show()
                }
                "ignore" -> {
                    ignored.add(device.fingerprintKey())
                    updateAllLists()
                    Toast.makeText(this, "Ignoring ${device.finalDisplayName}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        mobileDeviceAdapter = DeviceAdapter(deviceActionHandler)
        wifiApAdapter = DeviceAdapter(deviceActionHandler)
        bleOnlyDeviceAdapter = DeviceAdapter(deviceActionHandler)

        binding.recyclerDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = mobileDeviceAdapter
        }

        binding.recyclerWifiApDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = wifiApAdapter
        }

        binding.recyclerBleOnlyDevices.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = bleOnlyDeviceAdapter
        }

        binding.btnHome.setOnClickListener { showHome() }
        binding.btnStop.setOnClickListener {
            scanner?.stop()
            scanner = null
            showHome()
        }
        binding.btnPause.setOnClickListener {
            scanner?.let {
                if (it.isRunning) {
                    it.stop()
                    binding.btnPause.text = "Resume"
                } else {
                    it.start()
                    binding.btnPause.text = "Pause"
                }
            }
        }

        binding.btnApis.setOnClickListener { showApiMenu(it) }
        binding.btnResources.setOnClickListener { showResourcesMenu(it) }
        binding.btnAbout.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }

        binding.fabMapAll.setOnClickListener {
            val allDevices = mobileDeviceAdapter.getCurrentList() + wifiApAdapter.getCurrentList() + bleOnlyDeviceAdapter.getCurrentList()
            val bssids = allDevices.mapNotNull { it.bestApBssidForWigle }.toTypedArray()
            if (bssids.isNotEmpty()) {
                val intent = Intent(this, MapActivity::class.java).apply {
                    putExtra("bssids", bssids)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "No BSSIDs to map", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        pendingScanMode?.let {
            checkPermissionsAndStartScan(it)
        }
    }

    private fun updateAllLists() {
        val currentMobile = mobileDeviceAdapter.getCurrentList()
        val currentWifi = wifiApAdapter.getCurrentList()
        val currentBle = bleOnlyDeviceAdapter.getCurrentList()

        mobileDeviceAdapter.updateList(currentMobile.filter { !ignored.contains(it.fingerprintKey()) })
        wifiApAdapter.updateList(currentWifi.filter { !ignored.contains(it.fingerprintKey()) })
        bleOnlyDeviceAdapter.updateList(currentBle.filter { !ignored.contains(it.fingerprintKey()) })
    }

    private fun checkPermissionsAndStartScan(mode: ScanMode) {
        pendingScanMode = mode

        val permissionsToRequest = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isEmpty()) {
            // All permissions granted, start scan
            showScan(mode)
            pendingScanMode = null
        } else {
            // If background location is the one missing, show special dialog
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && permissionsToRequest.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                showBackgroundLocationDialog()
            } else {
                // Otherwise, launch the standard permission request
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            }
        }
    }

    // --- FIX: New dialog function to handle renaming ---
    private fun showRenameDialog(device: DeviceFingerprint) {
        val deviceKey = device.fingerprintKey()
        val editText = EditText(this).apply {
            setText(device.customName ?: "")
            hint = "Enter custom name"
        }

        AlertDialog.Builder(this)
            .setTitle("Rename Device")
            .setMessage("Enter an alias for ${device.displayName}")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val customName = editText.text.toString()

                // Save to SharedPreferences
                val prefs = getSharedPreferences("CustomNames", Context.MODE_PRIVATE)
                with(prefs.edit()) {
                    if (customName.isBlank()) {
                        remove(deviceKey) // Remove the key if the name is cleared
                    } else {
                        putString(deviceKey, customName)
                    }
                    apply()
                }

                // Update the device in the current list immediately
                device.customName = if (customName.isBlank()) null else customName
                updateAllLists() // Refresh the UI to show the new name
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    // --- End of Fix ---

    private fun showBackgroundLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Background Location Required")
            .setMessage("For continuous Wi-Fi scanning, this app requires 'Allow all the time' location permission. Please go to settings to grant this permission.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                Toast.makeText(this, "Background location is needed for this scan type.", Toast.LENGTH_LONG).show()
                pendingScanMode = null
            }
            .create()
            .show()
    }

    private fun showHome() {
        binding.homeScroll.visibility = View.VISIBLE
        binding.scanLayout.visibility = View.GONE
        scanner?.stop()
        scanner = null
        pendingScanMode = null
    }

    private fun showScan(mode: ScanMode) {
        scanner?.stop()
        binding.homeScroll.visibility = View.GONE
        binding.scanLayout.visibility = View.VISIBLE

        binding.titleMobileDevices.visibility = if (mode == ScanMode.MOBILE_DEVICES) View.VISIBLE else View.GONE
        binding.recyclerDevices.visibility = if (mode == ScanMode.MOBILE_DEVICES) View.VISIBLE else View.GONE
        binding.titleWifiAps.visibility = if (mode != ScanMode.BLUETOOTH) View.VISIBLE else View.GONE
        binding.recyclerWifiApDevices.visibility = if (mode != ScanMode.BLUETOOTH) View.VISIBLE else View.GONE
        binding.titleBleOnly.visibility = if (mode != ScanMode.WIFI_APS) View.VISIBLE else View.GONE
        binding.recyclerBleOnlyDevices.visibility = if (mode != ScanMode.WIFI_APS) View.VISIBLE else View.GONE

        scanner = DeviceScanner(this, this, mode)
        scanner?.start()
    }

    override fun onDevicesUpdated(
        mobileDevices: List<DeviceFingerprint>,
        wifiApDevices: List<DeviceFingerprint>,
        bleOnlyDevices: List<DeviceFingerprint>
    ) {
        runOnUiThread {
            val sortedMobile = mobileDevices.sortedByDescending { it.lastRssi }
            val sortedWifi = wifiApDevices.sortedByDescending { it.lastRssi }
            val sortedBle = bleOnlyDevices.sortedByDescending { it.lastRssi }

            val filteredMobile = sortedMobile.filter { !ignored.contains(it.fingerprintKey()) }
            val filteredWifi = sortedWifi.filter { !ignored.contains(it.fingerprintKey()) }
            val filteredBle = sortedBle.filter { !ignored.contains(it.fingerprintKey()) }

            mobileDeviceAdapter.updateList(filteredMobile)
            wifiApAdapter.updateList(filteredWifi)
            bleOnlyDeviceAdapter.updateList(filteredBle)
        }
    }

    // ... (The rest of your MainActivity file like showApiMenu, etc. remain unchanged)

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun showApiMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.api_keys_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_set_wigle_key -> showWigleKeyDialog()
                R.id.action_set_shodan_key -> showShodanKeyDialog()
            }
            true
        }
        popup.show()
    }

    private fun showResourcesMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.resources_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            val url = when (item.itemId) {
                R.id.action_open_wigle -> "https://wigle.net"
                R.id.action_open_shodan -> "https://shodan.io"
                else -> return@setOnMenuItemClickListener false
            }
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            true
        }
        popup.show()
    }

    private fun showShodanKeyDialog() {
        val editText = EditText(this)
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        editText.setText(prefs.getString("shodan_api_key", ""))

        AlertDialog.Builder(this)
            .setTitle("Enter Shodan API Key")
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val key = editText.text.toString()
                prefs.edit().putString("shodan_api_key", key).apply()
                Toast.makeText(this, "Shodan API Key saved", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showWigleKeyDialog() {
        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val currentKey = prefs.getString("wigle_api_key", null)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        val apiNameInput = EditText(this).apply { hint = "API Name" }
        val apiTokenInput = EditText(this).apply { hint = "API Token" }

        if (currentKey != null) {
            try {
                val decoded = String(Base64.decode(currentKey, Base64.NO_WRAP))
                val parts = decoded.split(":")
                if (parts.size == 2) {
                    apiNameInput.setText(parts[0])
                    apiTokenInput.setText(parts[1])
                }
            } catch (e: Exception) { /* Malformed key, do nothing */ }
        }

        layout.addView(apiNameInput)
        layout.addView(apiTokenInput)

        AlertDialog.Builder(this)
            .setTitle("Enter WiGLE Credentials")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val apiName = apiNameInput.text.toString()
                val apiToken = apiTokenInput.text.toString()

                if (apiName.isBlank() || apiToken.isBlank()) {
                    Toast.makeText(this, "Both fields are required", Toast.LENGTH_SHORT).show()
                } else {
                    val credentials = "$apiName:$apiToken"
                    val base64Credentials = Base64.encodeToString(credentials.toByteArray(), Base64.NO_WRAP)
                    prefs.edit().putString("wigle_api_key", base64Credentials).apply()
                    Toast.makeText(this, "WiGLE Credentials saved", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    companion object {
        private val REQUIRED_PERMISSIONS =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_WIFI_STATE
                )
            }
    }
}
