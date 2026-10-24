# TrackEm Mobile

**A passive Wi-Fi + Bluetooth LE observer for Android.**
TrackEm listens to what nearby devices are already broadcasting — Wi-Fi probe requests,
BLE advertisements, and visible access points — and turns it into a live, filterable
inventory of the radios around you. Built for wireless research, RF site surveys,
red-team recon, and OSINT work where you want to *observe* rather than interfere.

> **TrackEm never transmits Wi-Fi or Bluetooth frames.** It is an observer: no probe
> requests, no association frames, no deauthentication, no frame injection. See
> [What TrackEm does NOT do](#what-trackem-does-not-do).

Package: `com.veteranop.trackem` · License: [MIT](LICENSE) · Android 7.0+ (API 24)

---

## What it does

TrackEm runs three scan modes, each from the home screen:

| Mode | What it observes | Typical use |
|---|---|---|
| **Mobile devices** | Wi-Fi probe requests plus BLE advertisements from phones, laptops, watches, IoT | Presence detection, device counting, probe-request research |
| **Bluetooth LE** | BLE advertisers only (no Wi-Fi radio needed) | Beacon / tracker / BLE device surveys |
| **Wi-Fi access points** | Visible APs (SSID, BSSID, RSSI), plus associated clients where the platform reports them | Site surveys, rogue-AP hunting |

### Features

- **Live device list** with MAC, vendor, RSSI, SSID probe history, and last-seen time.
- **Vendor resolution** via the [maclookup.app](https://maclookup.app) API (key injected at
  build time — see [Building](#building)).
- **MAC randomization handling** — randomized / locally-administered addresses are grouped
  into a *randomized host ID* (RHID) so one phone doesn't appear as a dozen devices.
- **Device detail** view: probe list, signal, first/last seen, timestamps, and a map pin.
- **Hunting mode** — pick a target MAC and TrackEm plots RSSI samples on a map with an
  estimated confidence radius, so you can walk the signal down.
- **Wi-Fi AP map** — BSSID → location lookup via the WiGLE API (bring your own key).
- **Rename devices** — local, per-device labels you control (stored in app preferences).
- **BLE connect** — connect to a BLE peripheral and read its battery service.
- **Device data stays on the device.** No account, no cloud sync, and no upload of the
  devices it observes. The only outbound calls are the vendor lookup (`api.maclookup.app`)
  and the optional WiGLE lookup — both send a MAC/BSSID and nothing else.

> **Analytics, stated honestly:** if you supply your own `app/google-services.json`,
> Firebase Analytics records three app-level events — `app_opened`, `start_scan` (with the
> scan mode) and `device_renamed` (with a boolean only, never the MAC). No identifier of an
> observed device is sent. Without that file the app builds and runs with analytics disabled:
> the calls are nullable, so a missing Firebase config can never crash startup.

---

## What TrackEm does NOT do

This matters for both licensing and expectations, so it is stated plainly:

- **No deauthentication.** A real deauth attack requires injecting raw 802.11 frames, which a
  stock, unrooted Android app cannot do. TrackEm has no such capability.
- **No probe-request forcing.** You cannot make another device transmit. What the app *can* do
  is ask the local Wi-Fi radio to re-scan (`WifiManager.startScan()`), which gives a quiet
  device another chance to appear in the next scan window. That button is labelled
  **FORCE WI-FI RESCAN (10x)** and that is all it does — ten rescans.
- **No packet capture, no monitor mode, no injection, no jamming.** Those need root, a
  supporting chipset, and (for most of them) a licence you probably don't have.
- **No hidden "passive 100%" claim.** TrackEm is passive in the sense that it does not
  transmit Wi-Fi/BLE frames towards other devices, but the radio *does* actively scan, and
  with a MAC lookup key configured it makes outbound HTTPS calls to `api.maclookup.app`.
  Nothing about the devices it observes is sent anywhere.

If you need active wireless testing, use dedicated hardware and tooling (and get written
authorization for the network first).

---

## Permissions

| Permission | Why |
|---|---|
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | Android requires location permission before it will hand an app Wi-Fi scan results or BLE scan results. This is an OS requirement, not a TrackEm preference. |
| `ACCESS_BACKGROUND_LOCATION` | Optional — only if you want scans to continue while the app is backgrounded. |
| `BLUETOOTH_SCAN` (`neverForLocation`) / `BLUETOOTH_CONNECT` | BLE scanning and connecting to a peripheral. |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` | Read scan results; trigger a Wi-Fi re-scan. |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Vendor lookup (maclookup.app) and the optional WiGLE lookup. |

Location is used to *resolve scan results*, and (if you use hunting/map mode) to place
samples on a map. TrackEm does not store a location history anywhere off-device.

---

## Building

Requirements: Android Studio (or just the SDK + JDK 17), Android SDK platform 34.

```bash
git clone https://github.com/veteranop/trackem.git
cd trackem
```

### 1. Provide your own MAC lookup key

The maclookup.app API key is **not** in this repo (it was hard-coded in an earlier commit
and has since been rotated and purged from history). Supply your own — either in
`local.properties` (gitignored):

```properties
sdk.dir=/path/to/Android/Sdk
MACLOOKUP_API_KEY=your-key-from-maclookup.app
```

…or in the environment for CI:

```bash
export MACLOOKUP_API_KEY=...
```

The value is injected as `BuildConfig.MACLOOKUP_API_KEY`. **With no key set the app still
builds and runs** — vendor lookup is simply skipped.

### 2. (Optional) Firebase

`app/google-services.json` is gitignored, because it contains a Firebase API key. If you
want Firebase Analytics, create your own Android app entry in the Firebase console for
package `com.veteranop.trackem` and drop the generated `google-services.json` into `app/`.
Without the file, the Google Services Gradle plugin is not applied and the app builds
without Firebase.

### 3. Build

```bash
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:test                   # JVM unit tests
```

Install the debug build with `adb install -r app/build/outputs/apk/debug/app-debug.apk`.

> **Installing an older build?** The application ID is now `com.veteranop.trackem`
> (previously `com.example.trackemmobile`), so it installs alongside an old copy instead of
> replacing it. Uninstall the old one to avoid confusion.

---

## Secrets policy

Two credentials caused trouble in this repo's history; both are handled the same way now:

| Secret | Where it lives now |
|---|---|
| `MACLOOKUP_API_KEY` | `local.properties` / CI secret → `BuildConfig.MACLOOKUP_API_KEY` |
| Firebase API key | `app/google-services.json`, gitignored, generated per developer/CI |

`.gitignore` blocks `google-services.json`, `.kotlin/`, `.gradle/`, `*.apk`/`*.aab`, and
keystores. If you fork this repo, **rotate both keys for your own use** — the old values
were public and must be treated as burned.

Note that a `BuildConfig` field is not a vault: it is compiled into the APK and can be
extracted. For a rate-limited MAC lookup key that is an acceptable trade; do not put
anything high-value there.

---

## Repo layout

```
app/src/main/java/com/veteranop/trackem/
├── MainActivity.kt          scan modes, device lists, API-key menu
├── DeviceScanner.kt         Wi-Fi + BLE scanning, fingerprinting, vendor lookup
├── DetailActivity.kt        single-device view, rescan, BLE connect
├── MapActivity.kt           Wi-Fi AP map (WiGLE)
├── DeviceFingerprint.kt     device model + RHID grouping
├── ui/hunting/              hunting mode (Compose + Maps)
└── utils/, data/            location helper, RSSI samples
```

---

## Legal and ethical use

Observing radio traffic is regulated differently depending on where you are, and using a
tool like this on people who have not consented may be illegal even when you are only
listening. TrackEm is published for research, education, site surveys, and authorized
security testing. **You** are responsible for having the authority to do what you are doing
with it. The authors accept no liability for misuse — see [LICENSE](LICENSE).

---

## License

MIT — see [LICENSE](LICENSE).
