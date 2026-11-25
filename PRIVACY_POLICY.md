## NetCheck: Privacy policy

Welcome to the NetCheck app for Android!

This is an open source Android app developed by Joaquim Monserrat. The source code is available on [GitHub](https://github.com/jeremies/wificheck) under the MIT license; the app is also available on Google Play.

As an avid Android user myself, I take privacy very seriously.
I know how irritating it is when apps collect your data without your knowledge.

NetCheck contains **no ads, no analytics SDKs, and no third-party tracking**, and it does not require an account or collect personally identifiable information such as your name, email address, or GPS location. Because NetCheck monitors Wi-Fi connectivity and optionally shares device online status across your devices, here is exactly how data is handled:

### 1. Data stored on your device
NetCheck stores your settings locally in the app's private preferences:
- Your configured **Shared Group Code**
- Your customizable **Device Name** (defaults to your device manufacturer and model)
- A randomly generated **Device ID** (UUID)
- Whether **Broadcast Online Status** is enabled

All local data can be erased at any time by clearing the app's data in Android Settings or uninstalling the app.

### 2. Wi-Fi connectivity probes
While monitoring is active, NetCheck sends a lightweight HTTPS request every 5 seconds strictly over your Wi-Fi interface to Google's standard connectivity check endpoint (`https://connectivitycheck.gstatic.com/generate_204`) to confirm real internet access without falling back to mobile data. No personal payload is sent—only standard HTTP request metadata (such as your public IP address and the app's `User-Agent` header) needed to complete the request.

### 3. Cloud Device Presence (Cloudflare Workers + D1)
When **Broadcast Online Status** is enabled and configured with the Cloudflare backend, NetCheck periodically sends device status heartbeats (every 15 seconds while monitoring over Wi-Fi, plus an offline notice when monitoring stops) to the developer-operated Cloudflare Worker and D1 database so your other devices in the same group can see whether this device is online:
- **What is sent and stored:** the randomly generated `deviceId`, your chosen device `name`, the current Wi-Fi network name (`wifiSsid`, when exposed by Android), probe latency (`latencyMs`), battery percentage (`batteryPct`), charging state (`isCharging`), and last-seen timestamps.
- **Group Code protection:** your shared group code (`X-Group-Code`) is hashed using SHA-256 on the server before storage—the raw group code is never stored in the database. Anyone who knows the same group code can view and manage devices in that group.
- **Infrastructure processing:** backend requests are processed on Cloudflare Workers and D1, which may temporarily process standard HTTP metadata (such as IP addresses and operational logs/traces) to run and secure the service.

### 4. Data retention and deletion
- You can stop sending cloud telemetry at any time by disabling **Broadcast Online Status** in **Settings** or stopping monitoring.
- Device records remain stored in the Cloudflare D1 database until you delete the device directly from the **Devices** tab in the app, or request removal via [GitHub Issues](https://github.com/jeremies/wificheck/issues).

If you find any security vulnerability that has been inadvertently caused by me, or have any question regarding how the app protects your privacy, please open an issue on GitHub and I will surely try to fix it or help you.

Yours sincerely,  
Joaquim Monserrat.  
Barcelona, Spain.  
https://github.com/jeremies/wificheck/issues
