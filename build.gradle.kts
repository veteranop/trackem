// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.1.4" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false  // FIXED: Exact match for Compose Compiler 1.5.4
    id("com.google.gms.google-services") version "4.4.0" apply false
}