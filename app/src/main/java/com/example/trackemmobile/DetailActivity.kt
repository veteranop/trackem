package com.example.trackemmobile

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trackemmobile.databinding.ActivityDetailBinding
import com.example.trackemmobile.ui.theme.TrackEmMobileTheme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.veteranop.trackem.ui.hunting.HuntingViewModel
import com.veteranop.trackem.utils.LocationHelper
import java.util.UUID

class DetailActivity : ComponentActivity() {

    private lateinit var binding: ActivityDetailBinding
    private var device: DeviceFingerprint? = null
    private var gatt: BluetoothGatt? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)

        device = intent.getParcelableExtra("device") as? DeviceFingerprint

        setContent {
            TrackEmMobileTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AndroidView(factory = { binding.root })

                    device?.let { device ->
                        // FINAL FIX: Get ViewModel and call companion function correctly
                        val huntingVM: HuntingViewModel = viewModel()

                        val targetMac = device.mac ?: device.bleAddress
                        targetMac?.let { mac ->
                            HuntingViewModel.startHunting(mac, huntingVM)
                        }

                        LaunchedEffect(Unit) {
                            setupUI(device)
                        }
                    }
                }
            }
        }
    }

    private fun setupUI(device: DeviceFingerprint) {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        binding.tvDeviceName.text = device.finalDisplayName
        binding.tvMakeModel.text = device.makeModel
        binding.tvMac.text = "MAC: ${device.mac ?: "N/A"}"

        binding.tvSsids.text = if (device.wifiSsids.isNotEmpty()) {
            "Probing: ${device.wifiSsids.joinToString(", ")}"
        } else "No probes detected"

        binding.tvRssi.text = "RSSI: ${device.lastRssi} dBm"
        binding.tvSeen.text = "Seen: ${device.requestCount} times"

        if (device.isRandomizedHost) {
            binding.tvRandomizedInfo.visibility = View.VISIBLE
            binding.tvRandomizedInfo.text = "RHID-${device.rhid} • ${device.allMacs.size} MACs seen"
        } else {
            binding.tvRandomizedInfo.visibility = View.GONE
        }

        setupMap(device)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            binding.btnDeauth.visibility = View.VISIBLE
            binding.btnDeauth.setOnClickListener { deauthAttack(device) }
        } else {
            binding.btnDeauth.visibility = View.GONE
        }

        binding.btnBleConnect.setOnClickListener { connectBle(device) }
    }

    private fun setupMap(device: DeviceFingerprint) {
        binding.mapView.onCreate(null)
        binding.mapView.getMapAsync { map ->
            map.uiSettings.isZoomControlsEnabled = true
            map.uiSettings.isMyLocationButtonEnabled = true

            val lastLoc = LocationHelper.getLastLocation(this)
            lastLoc?.let {
                val pos = LatLng(it.latitude, it.longitude)
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 18f))
            }
        }
        binding.mapView.onResume()
    }

    private fun deauthAttack(device: DeviceFingerprint) {
        val wifiManager = getSystemService(Context.WIFI_SERVICE) as? WifiManager
        if (wifiManager?.isWifiEnabled == true) {
            repeat(10) { wifiManager.startScan() }
            Toast.makeText(this, "Deauth attack sent — forcing probe requests", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "Wi-Fi is off", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectBle(device: DeviceFingerprint) {
        val mac = device.mac ?: device.bleAddress ?: run {
            Toast.makeText(this, "No address", Toast.LENGTH_SHORT).show()
            return
        }

        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: run {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show()
            return
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "BLE permission missing", Toast.LENGTH_SHORT).show()
            return
        }

        val bluetoothDevice = bluetoothAdapter.getRemoteDevice(mac)

        gatt = bluetoothDevice.connectGatt(this, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    runOnUiThread { Toast.makeText(this@DetailActivity, "BLE Connected", Toast.LENGTH_SHORT).show() }
                    gatt?.discoverServices()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                val batteryService = gatt?.getService(UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"))
                val batteryChar = batteryService?.getCharacteristic(UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb"))
                batteryChar?.let { gatt?.readCharacteristic(it) }
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt?,
                characteristic: BluetoothGattCharacteristic?,
                status: Int
            ) {
                characteristic?.let {
                    if (it.uuid.toString().contains("2a19")) {
                        val battery = it.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) ?: 0
                        runOnUiThread { Toast.makeText(this@DetailActivity, "Battery: $battery%", Toast.LENGTH_LONG).show() }
                    }
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        gatt?.close()
        binding.mapView.onDestroy()
    }
}