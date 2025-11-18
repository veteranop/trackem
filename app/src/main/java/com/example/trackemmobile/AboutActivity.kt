package com.example.trackemmobile

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.BulletSpan
import android.text.style.URLSpan
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "About TrackEm"

        val tvAboutContent = findViewById<TextView>(R.id.tvAboutContent)

        // Make links clickable
        tvAboutContent.movementMethod = LinkMovementMethod.getInstance()

        val fullText = StringBuilder()
        val bulletPoints = mutableListOf<Pair<Int, Int>>()
        val linkPoints = mutableListOf<Pair<Int, Int>>()

        // --- Section 1: What It Does ---
        fullText.append("What is TrackEm?\n\n")
        val whatItDoes = "TrackEm is a powerful network discovery and device fingerprinting tool. It passively listens for Wi-Fi and Bluetooth signals to identify and categorize nearby devices.\n\n"
        fullText.append(whatItDoes)

        // --- Section 2: How to Use It ---
        fullText.append("How to Use It\n\n")
        val howToUse = """
            Select one of the three scan modes on the main screen:
            
        """.trimIndent()
        fullText.append(howToUse)

        val mobileDevicesDesc = "Scan for Mobile Devices: This is the most comprehensive scan. It looks for phones, laptops, and other personal devices by detecting their Wi-Fi probe requests and Bluetooth signals.\n"
        val wifiApsDesc = "Scan for Wi-Fi APs: This mode specifically targets and lists nearby Wi-Fi access points and routers.\n"
        val bluetoothDesc = "Scan for Bluetooth Devices: This mode focuses only on Bluetooth signals, ideal for finding beacons, headphones, and other BLE-only devices.\n\n"

        var start = fullText.length
        fullText.append(mobileDevicesDesc)
        bulletPoints.add(Pair(start, fullText.length))

        start = fullText.length
        fullText.append(wifiApsDesc)
        bulletPoints.add(Pair(start, fullText.length))

        start = fullText.length
        fullText.append(bluetoothDesc)
        bulletPoints.add(Pair(start, fullText.length))


        // --- Section 3: Potential Uses ---
        fullText.append("What Are The Uses?\n\n")
        val uses = """
            TrackEm is designed for security professionals, network administrators, and technology enthusiasts for various purposes:
            
        """.trimIndent()
        fullText.append(uses)

        val securityAuditsDesc = "Security Audits: Identify unauthorized devices on a corporate network or rogue access points in a secure facility.\n"
        val deviceInventoryDesc = "Device Inventory: Quickly create a list of all broadcasting devices in an area for asset tracking or management.\n"
        val privacyAwarenessDesc = "Privacy Awareness: See what information your own devices are broadcasting, such as the names of Wi-Fi networks you have previously connected to.\n"
        val situationalAnalysisDesc = "Situational Analysis: For first responders or physical security teams, get a quick sense of the number and type of personal devices in an immediate area.\n\n"

        start = fullText.length
        fullText.append(securityAuditsDesc)
        bulletPoints.add(Pair(start, fullText.length))

        start = fullText.length
        fullText.append(deviceInventoryDesc)
        bulletPoints.add(Pair(start, fullText.length))

        start = fullText.length
        fullText.append(privacyAwarenessDesc)
        bulletPoints.add(Pair(start, fullText.length))

        start = fullText.length
        fullText.append(situationalAnalysisDesc)
        bulletPoints.add(Pair(start, fullText.length))

        // --- FIX: Add Developer Attribution Section ---
        fullText.append("Developed By\n\n")
        val devInfo = "This application was developed by Mark de Jong, a Wireless Engineer and cyber security professional.\n\n"
        fullText.append(devInfo)

        val linkText = "Visit veteranop.com for more information."
        start = fullText.length
        fullText.append(linkText)
        linkPoints.add(Pair(start, fullText.length))
        // --- End of Fix ---


        // Apply all formatting
        val spannableString = SpannableString(fullText)
        for (range in bulletPoints) {
            spannableString.setSpan(BulletSpan(20), range.first, range.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        for (range in linkPoints) {
            spannableString.setSpan(URLSpan("http://veteranop.com"), range.first, range.second, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        tvAboutContent.text = spannableString
    }

    // Handle the back arrow in the action bar
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
