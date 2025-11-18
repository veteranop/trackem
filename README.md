# trackem
Passive Device Tracker (Android)TrackEm Mobile is a real-time, passive Wi-Fi + Bluetooth LE probe-request scanner designed for OSINT, red-team ops, and wireless research.It listens for client devices (phones, laptops, IoT) that are actively broadcasting the names of Wi-Fi networks.


TrackEm MobilePassive Wi-Fi + Bluetooth LE Probe Request Tracker

VeteranOp, LLC – TrackEm 1.0.0.1

TrackEm Mobile passively monitors nearby client devices (phones, laptops, IoT) by listening for probe requests — the signals devices broadcast when searching for saved Wi-Fi networks.
Even with MAC randomization and modern privacy features enabled, the app reveals cached network history, device manufacturer, signal strength, and persistent fingerprints.Perfect for authorized:Physical penetration testing
Wireless site surveys
Red-team reconnaissance
Privacy & OSINT research
Rogue device detection

100% passive – no transmissions, no deauthentication, no active attacks.FeaturesFeature
Description
Cached-Network Only Mode
Filter to only devices actively looking for saved networks
Full SSID History
See every Wi-Fi network a device has ever saved
MAC Randomization Defeat
OUI + SSID set + BLE TX power + advert interval clustering
Live RSSI Display
Color-coded signal strength (Green = strong, Red = weak)
Pause / Resume Scanning
Freeze the view to interact with results
Target Lock
Select one device and filter everything else out
One-Tap WiGLE Search
Instant geolocation lookup for any SSID/BSSID
In-App Map (OSMDroid)
Plot strongest BSSID hits via WiGLE API
Copy BSSID / All SSIDs
Quick clipboard export
Ignore List & Multi-Select
Clean up noise
WiGLE & Shodan API Ready
Add your keys for deeper intel

Requirements
Android 6.0+ (API 23)
Location permission (required for Wi-Fi/BLE scanning on modern Android)
Nearby Devices permission (Android 12+)

No root required.

Permissions 

ExplainedPermission
Why Needed
ACCESS_FINE_LOCATION
Required by Android to scan Wi-Fi networks
BLUETOOTH_SCAN / BLUETOOTH_CONNECT
Required for Bluetooth LE scanning (Android 12+)
INTERNET
WiGLE API lookups and map tiles

Legal & Ethical UseThis tool is for authorized security testing and research only.Passive monitoring of probe requests is legal in many jurisdictions when performed on networks you own or have explicit permission to test.
Unauthorized monitoring may violate wiretap laws (US ECPA, EU GDPR, Canadian PIPEDA, etc.).VeteranOp, LLC is not responsible for misuse.

LicenseMIT License (LICENSE) – Free to use, modify, and distribute (including commercially).

Author
VeteranOp, LLC
veteranop.com

Built in US – 2025 You’re not just scanning the airwaves.
You’re reading digital memories.Happy hunting.


