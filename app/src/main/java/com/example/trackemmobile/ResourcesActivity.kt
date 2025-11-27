package com.example.trackemmobile

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class ResourcesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_resources)

        // Set up the action bar with a title and a back button
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = "Resources & How-To"
    }

    // Handle the back arrow in the action bar
    override fun onSupportNavigateUp(): Boolean {
        finish() // Close this activity and go back to the previous one
        return true
    }
}
