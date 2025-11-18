package com.example.trackemmobile

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
// The incorrect 'androidx.paging.map' import has been removed.
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import okhttp3.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.IOException

class MapActivity : AppCompatActivity() {

    private val TAG = "MapActivity"
    private lateinit var mapView: MapView
    private val client = OkHttpClient()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This must be called before setting the content view for osmdroid
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.map)
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)

        val mapController = mapView.controller
        mapController.setZoom(15.0)

        centerMapOnUserLocation()

        val bssids = intent.getStringArrayExtra("bssids")
        val singleBssid = intent.getStringExtra("bssid")

        if (bssids != null) {
            bssids.forEach { searchWigleV2ByBssid(it) }
        } else if (singleBssid != null) {
            searchWigleV2ByBssid(singleBssid)
        }
    }

    private fun centerMapOnUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // If permissions aren't granted, center on a default location
            mapView.controller.setCenter(GeoPoint(40.7128, -74.0060)) // Default to NYC
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userGeoPoint = GeoPoint(location.latitude, location.longitude)
                mapView.controller.setCenter(userGeoPoint)
            } else {
                // Fallback to default if location is null
                mapView.controller.setCenter(GeoPoint(40.7128, -74.0060))
            }
        }
    }

    private fun searchWigleV2ByBssid(bssid: String) {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val apiKey = prefs.getString("wigle_api_key", null)
        if (apiKey == null) {
            Log.e(TAG, "WiGLE API key not set.")
            return
        }

        val url = "https://api.wigle.net/api/v2/network/detail?netid=$bssid"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Basic $apiKey")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "WiGLE API call failed for BSSID: $bssid", e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    try {
                        val result = gson.fromJson(body, WigleDetailResult::class.java)
                        if (result.success && result.results.isNotEmpty()) {
                            val network = result.results[0]
                            if (network.trilat != 0.0 && network.trilong != 0.0) {
                                runOnUiThread {
                                    addMarkerToMap(network.trilat, network.trilong, network.ssid ?: bssid)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse WiGLE response for BSSID: $bssid", e)
                    }
                } else {
                    Log.e(TAG, "WiGLE API error for BSSID: $bssid - Code: ${response.code}")
                }
            }
        })
    }

    private fun addMarkerToMap(lat: Double, lon: Double, title: String) {
        val geoPoint = GeoPoint(lat, lon)
        val marker = Marker(mapView)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = title

        // Use a default marker icon from the library
        val defaultMarker: Drawable? = ContextCompat.getDrawable(this, org.osmdroid.library.R.drawable.marker_default)
        marker.icon = defaultMarker

        mapView.overlays.add(marker)
        mapView.invalidate() // Refresh the map to show the new marker
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

// Data classes for parsing WiGLE API response
data class WigleDetailResult(
    val success: Boolean,
    val results: List<WigleNetworkDetail>
)

data class WigleNetworkDetail(
    val trilat: Double,
    val trilong: Double,
    val ssid: String?
)
