package com.netcheck.app.model

enum class MonitoringStatus {
    STOPPED,
    CHECKING,
    CONNECTED_ONLINE,
    ALARMING,
    MUTED
}

data class WifiProbeInfo(
    val status: MonitoringStatus = MonitoringStatus.STOPPED,
    val consecutiveFailures: Int = 0,
    val latencyMs: Long? = null,
    val lastCheckedTimestamp: Long = 0L,
    val wifiSsid: String? = null,
    val isWifiConnected: Boolean = false,
    val errorMessage: String? = null,
    val lastSyncTimestamp: Long = 0L,
    val lastSyncError: String? = null,
    val isBroadcastConfigured: Boolean = false,
    val isBroadcastEnabled: Boolean = false
)
