package com.netcheck.app.model

data class CloudSettings(
    val serverUrl: String = SERVER_URL,
    val groupCode: String = "",
    val deviceName: String = "",
    val deviceId: String = "",
    val broadcastEnabled: Boolean = true
) {
    val normalizedServerUrl: String
        get() = normalizeUrl(serverUrl)

    val trimmedGroupCode: String
        get() = groupCode.trim()

    val isConfigured: Boolean
        get() = normalizedServerUrl.isNotBlank() && trimmedGroupCode.length >= MIN_GROUP_CODE_LENGTH

    companion object {
        const val SERVER_URL = "https://netcheck-server.jmonserrat-54c.workers.dev"
        const val MIN_GROUP_CODE_LENGTH = 6
        const val MAX_GROUP_CODE_LENGTH = 128

        fun normalizeUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isEmpty()) return ""
            return if (trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true)
            ) {
                trimmed
            } else {
                "https://$trimmed"
            }
        }
    }
}

data class RemoteDevice(
    val deviceId: String,
    val name: String,
    val isOnline: Boolean,
    val lastSeenMs: Long,
    val wifiSsid: String?,
    val latencyMs: Int?,
    val batteryPct: Int?,
    val isCharging: Boolean
)

data class DeviceListResult(
    val serverTimeMs: Long,
    val onlineTimeoutMs: Long,
    val devices: List<RemoteDevice>
)

data class HeartbeatPayload(
    val deviceId: String,
    val name: String,
    val wifiSsid: String?,
    val latencyMs: Int?,
    val batteryPct: Int?,
    val isCharging: Boolean
)

data class BatteryInfo(
    val percentage: Int?,
    val isCharging: Boolean
)
