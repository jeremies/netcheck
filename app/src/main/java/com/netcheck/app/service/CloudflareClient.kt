package com.netcheck.app.service

import android.content.Context
import android.net.Network
import android.os.SystemClock
import com.netcheck.app.R
import com.netcheck.app.model.CloudSettings
import com.netcheck.app.model.DeviceListResult
import com.netcheck.app.model.HeartbeatPayload
import com.netcheck.app.model.RemoteDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class CloudflareClient(context: Context) {

    private val appContext = context.applicationContext

    suspend fun sendHeartbeat(
        wifiNetwork: Network,
        settings: CloudSettings,
        payload: HeartbeatPayload
    ): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = settings.normalizedServerUrl
            require(baseUrl.isNotBlank()) {
                appContext.getString(R.string.error_server_url_not_configured)
            }
            require(settings.trimmedGroupCode.length >= CloudSettings.MIN_GROUP_CODE_LENGTH) {
                appContext.getString(
                    R.string.settings_group_code_error,
                    CloudSettings.MIN_GROUP_CODE_LENGTH
                )
            }

            val body = JSONObject().apply {
                put("deviceId", payload.deviceId)
                put("name", payload.name)
                put("wifiSsid", payload.wifiSsid ?: JSONObject.NULL)
                put("latencyMs", payload.latencyMs ?: JSONObject.NULL)
                put("batteryPct", payload.batteryPct ?: JSONObject.NULL)
                put("isCharging", payload.isCharging)
            }

            val url = URL("$baseUrl/api/heartbeat")
            // Strictly bind heartbeat to Wi-Fi network so cellular fallback never broadcasts online
            val conn = (wifiNetwork.openConnection(url) as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 5000
                readTimeout = 5000
                useCaches = false
                defaultUseCaches = false
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Group-Code", settings.trimmedGroupCode)
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            try {
                conn.outputStream.use { out ->
                    out.write(body.toString().toByteArray(Charsets.UTF_8))
                }
                val code = conn.responseCode
                val responseText = readResponseBody(conn, code)
                if (code !in 200..299) {
                    throw IOException(extractErrorMessage(code, responseText))
                }
                val json = JSONObject(responseText)
                json.optNullableLong("serverTimeMs") ?: System.currentTimeMillis()
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun sendOffline(
        wifiNetwork: Network?,
        settings: CloudSettings
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = settings.normalizedServerUrl
            if (baseUrl.isBlank() || settings.trimmedGroupCode.length < CloudSettings.MIN_GROUP_CODE_LENGTH) {
                return@runCatching
            }

            val body = JSONObject().apply {
                put("deviceId", settings.deviceId)
            }

            val url = URL("$baseUrl/api/offline")
            val connection = wifiNetwork?.openConnection(url) ?: url.openConnection()
            val conn = (connection as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 2500
                readTimeout = 2500
                useCaches = false
                defaultUseCaches = false
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("X-Group-Code", settings.trimmedGroupCode)
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            try {
                conn.outputStream.use { out ->
                    out.write(body.toString().toByteArray(Charsets.UTF_8))
                }
                val code = conn.responseCode
                val responseText = readResponseBody(conn, code)
                if (code !in 200..299) {
                    throw IOException(extractErrorMessage(code, responseText))
                }
            } finally {
                conn.disconnect()
            }
        }
    }

    suspend fun fetchDevices(settings: CloudSettings): Result<DeviceListResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val baseUrl = settings.normalizedServerUrl
                require(baseUrl.isNotBlank()) {
                    appContext.getString(R.string.error_server_url_not_configured)
                }
                require(settings.trimmedGroupCode.length >= CloudSettings.MIN_GROUP_CODE_LENGTH) {
                    appContext.getString(
                        R.string.settings_group_code_error,
                        CloudSettings.MIN_GROUP_CODE_LENGTH
                    )
                }

                val url = URL("$baseUrl/api/devices")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    useCaches = false
                    defaultUseCaches = false
                    setRequestProperty("X-Group-Code", settings.trimmedGroupCode)
                    setRequestProperty("Connection", "close")
                    setRequestProperty("User-Agent", USER_AGENT)
                }

                try {
                    val code = conn.responseCode
                    val responseText = readResponseBody(conn, code)
                    if (code !in 200..299) {
                        throw IOException(extractErrorMessage(code, responseText))
                    }
                    parseDeviceListResult(JSONObject(responseText))
                } finally {
                    conn.disconnect()
                }
            }
        }

    suspend fun deleteDevice(settings: CloudSettings, deviceId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val baseUrl = settings.normalizedServerUrl
                require(baseUrl.isNotBlank()) {
                    appContext.getString(R.string.error_server_url_not_configured)
                }
                require(settings.trimmedGroupCode.length >= CloudSettings.MIN_GROUP_CODE_LENGTH) {
                    appContext.getString(
                        R.string.settings_group_code_error,
                        CloudSettings.MIN_GROUP_CODE_LENGTH
                    )
                }

                val encodedId = URLEncoder.encode(deviceId, "UTF-8").replace("+", "%20")
                val url = URL("$baseUrl/api/devices/$encodedId")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "DELETE"
                    connectTimeout = 5000
                    readTimeout = 5000
                    useCaches = false
                    defaultUseCaches = false
                    setRequestProperty("X-Group-Code", settings.trimmedGroupCode)
                    setRequestProperty("Connection", "close")
                    setRequestProperty("User-Agent", USER_AGENT)
                }

                try {
                    val code = conn.responseCode
                    val responseText = readResponseBody(conn, code)
                    if (code !in 200..299) {
                        throw IOException(extractErrorMessage(code, responseText))
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }

    suspend fun checkHealth(): Result<Long> = withContext(Dispatchers.IO) {
        runCatching {
            val baseUrl = CloudSettings.normalizeUrl(CloudSettings.SERVER_URL)
            require(baseUrl.isNotBlank()) {
                appContext.getString(R.string.error_server_url_not_configured)
            }

            val start = SystemClock.elapsedRealtime()
            val url = URL("$baseUrl/api/health")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                useCaches = false
                defaultUseCaches = false
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            try {
                val code = conn.responseCode
                val responseText = readResponseBody(conn, code)
                val elapsed = SystemClock.elapsedRealtime() - start
                if (code !in 200..299) {
                    throw IOException(extractErrorMessage(code, responseText))
                }
                val json = JSONObject(responseText)
                if (!json.optBoolean("ok", false)) {
                    throw IOException(appContext.getString(R.string.error_unexpected_health_response))
                }
                elapsed
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun parseDeviceListResult(json: JSONObject): DeviceListResult {
        val serverTimeMs = json.optNullableLong("serverTimeMs") ?: System.currentTimeMillis()
        val onlineTimeoutMs = json.optNullableLong("onlineTimeoutMs") ?: 45_000L
        val array = json.optJSONArray("devices") ?: JSONArray()
        val devices = buildList(array.length()) {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val deviceId = obj.optNullableString("deviceId") ?: continue
                val name = obj.optNullableString("name")
                    ?: appContext.getString(R.string.fallback_unknown_device)
                add(
                    RemoteDevice(
                        deviceId = deviceId,
                        name = name,
                        isOnline = obj.optBoolean("isOnline", false),
                        lastSeenMs = obj.optNullableLong("lastSeenMs") ?: serverTimeMs,
                        wifiSsid = obj.optNullableString("wifiSsid"),
                        latencyMs = obj.optNullableInt("latencyMs"),
                        batteryPct = obj.optNullableInt("batteryPct"),
                        isCharging = obj.optBoolean("isCharging", false)
                    )
                )
            }
        }
        return DeviceListResult(
            serverTimeMs = serverTimeMs,
            onlineTimeoutMs = onlineTimeoutMs,
            devices = devices
        )
    }

    private fun readResponseBody(conn: HttpURLConnection, statusCode: Int): String {
        val stream = if (statusCode in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun extractErrorMessage(statusCode: Int, responseText: String): String {
        if (responseText.isNotBlank()) {
            try {
                val json = JSONObject(responseText)
                val serverErr = json.optNullableString("error")
                if (!serverErr.isNullOrBlank()) {
                    return "HTTP $statusCode: $serverErr"
                }
            } catch (_: Exception) {
                // Ignore non-JSON error bodies
            }
        }
        return "HTTP $statusCode"
    }

    companion object {
        private const val USER_AGENT = "NetCheck/1.0 (Android)"

        private fun JSONObject.optNullableString(key: String): String? {
            if (!has(key) || isNull(key)) return null
            return optString(key).takeIf { it.isNotEmpty() }
        }

        private fun JSONObject.optNullableInt(key: String): Int? {
            if (!has(key) || isNull(key)) return null
            val raw = opt(key)
            return (raw as? Number)?.toInt()
        }

        private fun JSONObject.optNullableLong(key: String): Long? {
            if (!has(key) || isNull(key)) return null
            val raw = opt(key)
            return (raw as? Number)?.toLong()
        }
    }
}
