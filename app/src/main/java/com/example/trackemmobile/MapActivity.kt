package com.example.trackemmobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONException
import org.json.JSONObject
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MapActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var mapController: IMapController
    private val client = OkHttpClient()
    private var wigleApiKey: String? = null
    private val TAG = "MapActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getPreferences(MODE_PRIVATE))
        setContentView(R.layout.activity_map)

        val prefs = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        wigleApiKey = prefs.getString("wigle_api_key", null)

        mapView = findViewById(R.id.mapView)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapController = mapView.controller
        mapController.setZoom(16.0)

        centerMapOnUserLocation()

        val bssids = intent.getStringArrayExtra("bssids")
        val singleBssid = intent.getStringExtra("bssid")
        val isBle = intent.getBooleanExtra("isBle", false)

        when {
            bssids != null -> {
                Toast.makeText(this, "Mapping ${bssids.size} BSSIDs...", Toast.LENGTH_SHORT).show()
                mapMultipleBssids(bssids)
            }
            singleBssid != null -> {
                if(isBle) searchWigleV2ByBle(singleBssid) else searchWigleV2ByBssid(singleBssid)
            }
            else -> {
                Toast.makeText(this, "No BSSID provided to map", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun centerMapOnUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val startPoint = GeoPoint(location.latitude, location.longitude)
                mapController.setCenter(startPoint)
            }
        }
    }

    private fun mapMultipleBssids(bssids: Array<String>) {
        if (wigleApiKey.isNullOrEmpty()) {
            Toast.makeText(this, "Set your WiGLE API Key from the main screen", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.YEAR, -10)
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        val tenYearsAgo = dateFormat.format(calendar.time)

        CoroutineScope(Dispatchers.IO).launch {
            val uniqueBssids = bssids.toSet()
            var totalPins = 0

            for (bssid in uniqueBssids) {
                if (bssid.isBlank()) continue

                val formattedBssid = bssid.uppercase(Locale.ROOT) 
                val url = "https://api.wigle.net/api/v2/network/search?netid=$formattedBssid&lastupdt=$tenYearsAgo"
                val request = Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Basic $wigleApiKey")
                    .addHeader("Accept", "application/json")
                    .build()

                try {
                    val response = client.newCall(request).execute()
                    val body = response.body?.string()

                    if (response.isSuccessful && body != null) {
                        val json = JSONObject(body)
                        if (json.optBoolean("success") && json.has("results")) {
                            val results = json.getJSONArray("results")
                            if (results.length() > 0) {
                                val net = results.getJSONObject(0)
                                val lat = net.optDouble("trilat", 0.0)
                                val lon = net.optDouble("trilong", 0.0)

                                if (lat != 0.0 && lon != 0.0) {
                                    runOnUiThread {
                                        val point = GeoPoint(lat, lon)
                                        val marker = Marker(mapView).apply {
                                            position = point
                                            title = net.optString("ssid", formattedBssid)
                                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        }
                                        mapView.overlays.add(marker)
                                        if (totalPins == 0) mapController.setCenter(point)
                                        totalPins++
                                        mapView.invalidate()
                                    }
                                }
                            }
                        }
                    } else {
                        Log.e(TAG, "Request failed for BSSID $formattedBssid: ${response.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Exception mapping BSSID: $formattedBssid", e)
                }
            }

            runOnUiThread {
                Toast.makeText(this@MapActivity, "Finished mapping. Added $totalPins pins.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun searchWigleV2ByBssid(bssid: String) {
        if (wigleApiKey.isNullOrEmpty()) {
            Toast.makeText(this, "Set your WiGLE API Key from the main screen", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.YEAR, -10)
        val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        val tenYearsAgo = dateFormat.format(calendar.time)

        val formattedBssid = bssid.uppercase(Locale.ROOT)
        val url = "https://api.wigle.net/api/v2/network/search?netid=$formattedBssid&lastupdt=$tenYearsAgo"
        Log.d(TAG, "Querying WiGLE v2 for: $url")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Basic $wigleApiKey")
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MapActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                Log.d(TAG, "WiGLE v2 Response: $body")

                if (body == null) {
                    runOnUiThread { Toast.makeText(this@MapActivity, "Empty response from WiGLE", Toast.LENGTH_SHORT).show() }
                    return
                }

                try {
                    val json = JSONObject(body)
                    if (!json.getBoolean("success")) {
                        val message = json.optString("message", "Search failed")
                        runOnUiThread { Toast.makeText(this@MapActivity, "WiGLE Error: $message", Toast.LENGTH_LONG).show() }
                        return
                    }

                    val results = json.getJSONArray("results")
                    if (results.length() == 0) {
                        runOnUiThread { Toast.makeText(this@MapActivity, "No locations found for this BSSID in the last 10 years", Toast.LENGTH_SHORT).show() }
                        return
                    }

                    runOnUiThread {
                        var pinCount = 0
                        for (i in 0 until results.length()) {
                            val network = results.getJSONObject(i)
                            val lat = network.getDouble("trilat")
                            val lon = network.getDouble("trilong")

                            if (lat != 0.0 && lon != 0.0) {
                                val point = GeoPoint(lat, lon)
                                val marker = Marker(mapView).apply {
                                    position = point
                                    title = network.optString("ssid", "<Unknown SSID>")
                                    subDescription = network.optString("netid", "<Unknown BSSID>")
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                mapView.overlays.add(marker)
                                if (pinCount == 0) mapController.setCenter(point)
                                pinCount++
                            }
                        }
                        mapView.invalidate()
                        Toast.makeText(this@MapActivity, "Added $pinCount location pins for BSSID: $bssid", Toast.LENGTH_LONG).show()
                    }

                } catch (e: JSONException) {
                    runOnUiThread {
                        val errorMsg = "Failed to parse WiGLE data. Response: $body"
                        Log.e(TAG, errorMsg, e)
                        Toast.makeText(this@MapActivity, errorMsg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
    
    private fun searchWigleV2ByBle(bleMac: String) {
        if (wigleApiKey.isNullOrEmpty()) {
            Toast.makeText(this, "Set your WiGLE API Key from the main screen", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val url = "https://api.wigle.net/api/v2/bluetooth/search?netid=${bleMac.uppercase(Locale.ROOT)}"
        Log.d(TAG, "Querying WiGLE v2 for BLE: $url")

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Basic $wigleApiKey")
            .addHeader("Accept", "application/json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(this@MapActivity, "Network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                Log.d(TAG, "WiGLE v2 BLE Response: $body")

                if (body == null) {
                    runOnUiThread { Toast.makeText(this@MapActivity, "Empty response from WiGLE", Toast.LENGTH_SHORT).show() }
                    return
                }

                try {
                    val json = JSONObject(body)
                    if (!json.getBoolean("success")) {
                        val message = json.optString("message", "Search failed")
                        runOnUiThread { Toast.makeText(this@MapActivity, "WiGLE Error: $message", Toast.LENGTH_LONG).show() }
                        return
                    }

                    val results = json.getJSONArray("results")
                    if (results.length() == 0) {
                        runOnUiThread { Toast.makeText(this@MapActivity, "No locations found for this BLE device", Toast.LENGTH_SHORT).show() }
                        return
                    }

                    runOnUiThread {
                        var pinCount = 0
                        for (i in 0 until results.length()) {
                            val network = results.getJSONObject(i)
                            val lat = network.getDouble("trilat")
                            val lon = network.getDouble("trilong")

                            if (lat != 0.0 && lon != 0.0) {
                                val point = GeoPoint(lat, lon)
                                val marker = Marker(mapView).apply {
                                    position = point
                                    title = network.optString("name", "<Unknown Name>")
                                    subDescription = network.optString("netid", "<Unknown MAC>")
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                }
                                mapView.overlays.add(marker)
                                if (pinCount == 0) mapController.setCenter(point)
                                pinCount++
                            }
                        }
                        mapView.invalidate()
                        Toast.makeText(this@MapActivity, "Added $pinCount location pins for BLE device: $bleMac", Toast.LENGTH_LONG).show()
                    }

                } catch (e: JSONException) {
                    runOnUiThread {
                        val errorMsg = "Failed to parse WiGLE data. Response: $body"
                        Log.e(TAG, errorMsg, e)
                        Toast.makeText(this@MapActivity, errorMsg, Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }
}