# NetCheck 📡🚨

An Android application that continuously monitors your Wi-Fi internet connectivity, sounds a continuous loud alarm when internet access is lost (strictly preventing cellular 5G/4G/LTE fallback), and optionally broadcasts device online status to a Cloudflare Workers + D1 backend so you can view all online and offline devices in your group right inside the app.

---

## 💡 How It Works

### 1. Local Wi-Fi Loss Alarm (No 5G Fallback)
Android devices by default quickly switch or route traffic over cellular data (5G/4G) when a Wi-Fi network's upstream connection goes down. Standard apps probing the internet would normally reach the internet through mobile data without realizing Wi-Fi has lost connectivity.

**NetCheck solves this problem by strictly binding socket connections to the physical Wi-Fi interface:**
1. **Network Callback Binding**: Registers with Android's `ConnectivityManager` requesting `NetworkCapabilities.TRANSPORT_WIFI`.
2. **Wi-Fi-Only Probe**: Invokes `network.openConnection(URL("https://connectivitycheck.gstatic.com/generate_204"))`. This forces the HTTPS request to route exclusively over Wi-Fi. Mobile data (5G) is completely bypassed.
3. **Continuous Background Monitoring**: Operates inside an Android **Foreground Service** (`WifiMonitorService`) with a persistent notification and partial `WakeLock`, guaranteeing continuous 5-second checks even when your phone is locked or screen is off.
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

### 2. Cloud Device Presence (Cloudflare Workers + D1)
When configured in **Settings**, NetCheck also reports your device's Wi-Fi availability to your Cloudflare backend:
1. **Wi-Fi-Only Heartbeats**: Every **15 seconds** while monitoring is active (and immediately on connecting or recovering), NetCheck sends a `POST /api/heartbeat` strictly over the bound `TRANSPORT_WIFI` network (`wifiNetwork.openConnection(...)`). Cellular data never marks a device as Wi-Fi online, and server errors never trigger the local outage alarm.
2. **Server-Side Presence Window**: Cloudflare marks a device **Online** if it has not explicitly stopped and its last heartbeat was within **45 seconds**, and **Offline** otherwise (with relative last-seen timestamp).
3. **Explicit Offline on Stop**: Stopping monitoring sends a best-effort `POST /api/offline` so the device shows as offline right away instead of waiting for the 45-second timeout.
4. **Private Shared Group Code**: Devices sharing the same secret `X-Group-Code` (minimum 6 characters, SHA-256 hashed on the server before storage) appear in the same group list.
5. **View List on Any Network**: Fetching and removing devices (`GET /api/devices`, `DELETE /api/devices/:deviceId`) uses Android's default network (Wi-Fi or 5G) and works even when local monitoring is stopped.

---

## 📱 User Interface

- **2-Tab Navigation**:
  - **Monitor**: Start/Stop monitoring button (monitoring starts automatically on app/webapp launch), live pulsing status beacon, Wi-Fi SSID / latency / failure metrics, one-tap **MUTE ALARM** button when sounding, and a live cloud broadcast status chip.
  - **Devices** (with live online count badge): Auto-refreshes every **10 seconds** while open, listing Online devices first followed by Offline devices with last-seen time, Wi-Fi SSID (when available), probe latency, battery % + charging state, and delete confirmation for removing old entries.
- **Settings (Gear Icon)**:
  - Toggle **Broadcast Online Status** on/off and test sync server connectivity (`Test Connection` checks `/api/health`; the backend Worker URL is preconfigured and hidden from users).
  - Enter, generate, copy, or show/hide your **Shared Group Code** (in `webapp/`, a group code can also be supplied via `?group=`, `?groupCode=`, or `?code=` query parameter or copied as a share link).
  - Customize your **Device Name** (defaults to device manufacturer/model) with a persistent generated UUID.

---

## ☁️ Cloudflare Server (`server/`)

The backend lives in `server/` and runs on **Cloudflare Workers** + **D1 (SQLite)**.

### API Endpoints
- `GET /api/health` — Database health check and server timestamp (`onlineTimeoutMs: 45000`).
- `POST /api/heartbeat` — Requires `X-Group-Code`; upserts device telemetry and marks online.
- `POST /api/offline` — Requires `X-Group-Code`; marks device offline immediately.
- `GET /api/devices` — Requires `X-Group-Code`; returns all Online and Offline devices in the group sorted online-first.
- `DELETE /api/devices/:deviceId` — Requires `X-Group-Code`; removes a device from the group.

### Server Development & Testing
```bash
cd server
npm install
npm run cf-typegen        # Regenerate worker-configuration.d.ts from wrangler.jsonc
npm run typecheck         # TypeScript check (tsc --noEmit)
npm run db:migrate:local  # Apply D1 migrations locally
npm test                  # Run Workers runtime tests via @cloudflare/vitest-plugin
npm run dev               # Start local Wrangler dev server
```

### Deploying to Your Cloudflare Account
```bash
cd server
npx wrangler login                              # Authenticate with Cloudflare
npx wrangler d1 create netcheck-db                # Create D1 database (put database_id in wrangler.jsonc)
npx wrangler d1 migrations apply netcheck-db --remote
npx wrangler deploy                               # Deploy Worker and print *.workers.dev URL
```

---

## 🛠️ Tech Stack & Requirements

- **Android App**:
  - **Language**: Kotlin 1.9.24
  - **UI**: Jetpack Compose + Material 3
  - **Min SDK**: API 26 (Android 8.0 Oreo)
  - **Target SDK**: API 34 (Android 14)
  - **Concurrency**: Kotlin Coroutines & `StateFlow`
  - **Networking**: Standard `HttpURLConnection` + `org.json` (no extra HTTP/WebSocket dependencies)
  - **Build System**: Gradle 8.7 + Kotlin DSL
- **Cloudflare Backend**:
  - Cloudflare Workers (`nodejs_compat`, Workers Logs & Traces enabled)
  - Cloudflare D1 (SQLite)
  - Wrangler 4 + TypeScript + `@cloudflare/vitest-plugin`

---

## 🚀 Building & Running Android App

### Requirements
- Android Studio Hedgehog / Iguana / Jellyfish (or higher)
- JDK 17
- Android SDK with Platform 34

### Optional Pre-configured Default (`local.properties`)
You can prefill the default Group Code baked into `BuildConfig` at build time by adding this gitignored property to `local.properties`:
```properties
netcheck.groupCode=YOUR-GROUP-CODE
```
This value remains editable inside the app's **Settings** screen.

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
   - Within 5 seconds, NetCheck receives HTTP 204 over Wi-Fi, stops the alarm sound and vibration automatically, returns to green "Wi-Fi Online", and immediately sends a heartbeat to Cloudflare.
3. **Wi-Fi Disconnect & Cloud Timeout**:
   - Disable Wi-Fi in Android Quick Settings.
   - NetCheck immediately detects Wi-Fi loss locally and alarms; because heartbeats strictly require Wi-Fi, another phone viewing the **Devices** tab (even on 5G) sees that device switch to **Offline** after ~45s (or immediately when tapping **Stop Monitoring**).
4. **Mute Functionality**:
   - When alarm sounds, tap "MUTE ALARM" either in the notification shade or on the main screen. The alarm silences immediately without stopping background monitoring.
