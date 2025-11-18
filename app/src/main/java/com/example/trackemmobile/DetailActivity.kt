package com.example.trackemmobile

import android.content.Intent
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class DetailActivity : AppCompatActivity() {
    private val TAG = "DetailActivity"
    private val client = OkHttpClient()
    private val macLookupApiKey = "01ka9dt34ffqvtf73hmdv0sqyw01ka9dv6g0f92h093tdhxbqebnspr6bkxuclln"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val device: DeviceFingerprint? = intent.getParcelableExtra("device")
        if (device == null) {
            findViewById<TextView>(R.id.tvMakeModel).text = "Error: Device not found"
            return
        }

        findViewById<TextView>(R.id.tvMakeModel).text = device.makeModel
        findViewById<TextView>(R.id.tvName).text = device.finalDisplayName

        val macToLookup = device.mac
        val idType = if (device.bleAddress != null) "Bluetooth MAC" else "Wi-Fi BSSID"
        findViewById<TextView>(R.id.tvIdentifierValue).text = macToLookup ?: "N/A"
        findViewById<TextView>(R.id.tvIdentifierType).text = "($idType)"

        val ssidContainer = findViewById<LinearLayout>(R.id.ssidContainer)
        if (device.wifiSsids.isEmpty()) {
            val tv = TextView(this).apply { text = "No SSIDs beaconed." }
            ssidContainer.addView(tv)
        } else {
            device.wifiSsids.forEach { ssid ->
                if (ssid.isNotEmpty()) { // Only display non-empty SSIDs
                    val tv = TextView(this).apply { text = "• $ssid" }
                    ssidContainer.addView(tv)
                }
            }
            if(ssidContainer.childCount == 0){
                val tv = TextView(this).apply { text = "No public SSIDs beaconed." }
                ssidContainer.addView(tv)
            }
        }

        if (macToLookup != null) {
            fetchVendorDetails(macToLookup)
        } else {
            findViewById<TextView>(R.id.tvVendorCompany).text = "Vendor: No MAC/BSSID available"
        }

        findViewById<Button>(R.id.btnMapDevice).setOnClickListener {
            val bssidForMap = device.bestApBssidForWigle
            if (bssidForMap != null) {
                val intent = Intent(this, MapActivity::class.java).apply {
                    putExtra("bssid", bssidForMap)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "No BSSID available to map", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btnShodanLookup).setOnClickListener {
            shodanLookup(device.mac)
        }
    }

    private fun shodanLookup(mac: String?) {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        val shodanApiKey = prefs.getString("shodan_api_key", null)
        if (shodanApiKey.isNullOrEmpty()) {
            Toast.makeText(this, "Shodan API Key not set", Toast.LENGTH_SHORT).show()
            return
        }

        if (mac == null) {
            showResultsDialog("Shodan Lookup Results", "No MAC address available for lookup.")
            return
        }

        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            val resultsText = performShodanSearch(mac, shodanApiKey)

            withContext(Dispatchers.Main) {
                progressBar.visibility = View.GONE
                showResultsDialog("Shodan Lookup Results", resultsText)
            }
        }
    }

    private suspend fun performShodanSearch(mac: String, apiKey: String): String {
        val formattedMac = mac.replace(":", "")
        val url = "https://api.shodan.io/shodan/host/search?key=$apiKey&query=net:$formattedMac"
        val request = Request.Builder().url(url).build()

        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body == null) {
                "Error: Invalid response from server."
            } else {
                val json = JSONObject(body)
                if (json.has("error")) {
                    "API Error: ${json.getString("error")}"
                } else {
                    val matches = json.getJSONArray("matches")
                    if (matches.length() == 0) {
                        "No public hosts found for this MAC."
                    } else {
                        val builder = StringBuilder()
                        for (i in 0 until matches.length()) {
                            val host = matches.getJSONObject(i)
                            builder.append("IP: ${host.optString("ip_str")}\n")
                            builder.append("Hostnames: ${host.optJSONArray("hostnames")?.join(", ") ?: "N/A"}\n")
                            builder.append("Port: ${host.optInt("port")}\n")
                            val location = host.optJSONObject("location")
                            if (location != null) {
                                builder.append("Location: ${location.optString("city", "N/A")}, ${location.optString("country_name", "N/A")}\n")
                            }
                            builder.append("\n")
                        }
                        builder.toString()
                    }
                }
            }
        } catch (e: Exception) {
            "Search failed: ${e.message}"
        }
    }

    private fun showResultsDialog(title: String, message: String) {
        val textView = TextView(this).apply {
            text = message
            movementMethod = ScrollingMovementMethod()
            val padding = (16 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(textView)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun fetchVendorDetails(mac: String) {
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        val formattedMac = mac.uppercase(Locale.ROOT)
        val url = "https://api.maclookup.app/v2/macs/$formattedMac?apiKey=$macLookupApiKey"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@DetailActivity, "MAC Lookup Failed", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && body != null) {
                        try {
                            val result = Gson().fromJson(body, MacLookupResult::class.java)
                            displayMacLookupResult(result)
                        } catch (e: JsonSyntaxException) {
                            Toast.makeText(this@DetailActivity, "Failed to parse lookup response", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this@DetailActivity, "MAC Lookup API Error", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun displayMacLookupResult(result: MacLookupResult?) {
        if (result == null) return

        if (result.success && result.found) {
            findViewById<TextView>(R.id.tvVendorCompany).text = "Company: ${result.companyName ?: "N/A"}"
            findViewById<TextView>(R.id.tvVendorAddress).text = "Address: ${result.companyAddress ?: "N/A"}"
            findViewById<TextView>(R.id.tvVendorCountry).text = "Country: ${result.countryCode ?: "N/A"}"
            findViewById<TextView>(R.id.tvMacPrefix).text = "MAC Prefix: ${result.macPrefix ?: "N/A"}"
        } else {
            findViewById<TextView>(R.id.tvVendorCompany).text = "Vendor not found"
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
