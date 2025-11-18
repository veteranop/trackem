// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    // --- FIX: Corrected the alias for the Kotlin Android plugin ---
    alias(libs.plugins.kotlin.android) apply false
    // --- End of Fix ---

    // Defines the Google Services plugin needed for Firebase
    id("com.google.gms.google-services") version "4.4.2" apply false
}

