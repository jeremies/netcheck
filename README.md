# NetCheck 📡🚨

An Android application that continuously monitors your Wi-Fi internet connectivity and sounds a continuous loud alarm when internet access is lost, strictly preventing cellular (5G/4G/LTE) fallback.

---

## 💡 How It Works

Android devices by default quickly switch or route traffic over cellular data (5G/4G) when a Wi-Fi network's upstream connection goes down. Standard apps probing the internet would normally reach the internet through mobile data without realizing Wi-Fi has lost connectivity.

**NetCheck solves this problem by strictly binding socket connections to the physical Wi-Fi interface:**
1. **Network Callback Binding**: Registers with Android's `ConnectivityManager` requesting `NetworkCapabilities.TRANSPORT_WIFI`.
2. **Wi-Fi-Only Probe**: Invokes `network.openConnection(URL("https://connectivitycheck.gstatic.com/generate_204"))`. This forces the HTTPS request to route exclusively over Wi-Fi. Mobile data (5G) is completely bypassed.
3. **Continuous Background Monitoring**: Operates inside an Android **Foreground Service** with a persistent notification and partial `WakeLock`, guaranteeing continuous 5-second checks even when your phone is locked or screen is off.
4. **Jitter-Filtering & Alarm Trigger**:
   - Probes every **5 seconds**.
   - Requires **2 consecutive failed probes** before sounding the alarm (prevents false alarms from transient channel hops or roaming jitter).
   - Also triggers if Wi-Fi disconnects entirely.
5. **Continuous Alarm & Vibration**:
   - Loops the system alarm sound using `AudioAttributes.USAGE_ALARM`.
   - Continuous pulsing vibration pattern.
6. **Smart Recovery & Mute**:
   - **Auto-stop**: As soon as Wi-Fi internet recovers, the alarm and vibration immediately stop on their own.
   - **Mute / Dismiss**: Includes a quick-mute button in the notification and in the app UI to silence the alarm while connection is restored, re-arming automatically once online.

---

## 📱 User Interface

- **Minimalist Dashboard**:
  - One large **START / STOP MONITORING** button.
  - Live pulsing status beacon (Green for Online, Red pulse for Alarming, Blue for Checking, Gray for Idle, Orange for Muted).
  - Metrics display: Current Wi-Fi SSID, probe round-trip latency (ms), and failure counter.
  - One-tap **MUTE ALARM** button when sounding.

---

## 🛠️ Tech Stack & Requirements

- **Language**: Kotlin 1.9.24
- **UI**: Jetpack Compose + Material 3
- **Min SDK**: API 26 (Android 8.0 Oreo)
- **Target SDK**: API 34 (Android 14) / API 35
- **Concurrency**: Kotlin Coroutines & `StateFlow`
- **Build System**: Gradle 8.7 + Kotlin DSL

---

## 🚀 Building & Running

### Requirements
- Android Studio Hedgehog / Iguana / Jellyfish (or higher)
- JDK 17
- Android SDK with Platform 34

### Building via Terminal
```bash
./gradlew assembleDebug
```
The resulting APK will be located at:
`app/build/outputs/apk/debug/app-debug.apk`

### Installing to Device
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 🧪 Verification & Testing Scenarios

1. **Unplug Modem / Drop WAN**:
   - Keep phone connected to Wi-Fi router.
   - Disconnect the WAN ethernet cable from the router or block WAN DNS.
   - Even with 5G active on the phone, NetCheck detects the failure on probe 1, verifies on probe 2 (within 10s), and continuously alarms.
2. **Automatic Recovery**:
   - Reconnect the WAN cable.
   - Within 5 seconds, NetCheck receives HTTP 204 over Wi-Fi, stops the alarm sound and vibration automatically, and returns to green "Wi-Fi Online".
3. **Wi-Fi Disconnect**:
   - Disable Wi-Fi in Android Quick Settings.
   - NetCheck immediately detects Wi-Fi loss and alarms.
4. **Mute Functionality**:
   - When alarm sounds, tap "MUTE ALARM" either in the notification shade or on the main screen. The alarm silences immediately without stopping background monitoring.
