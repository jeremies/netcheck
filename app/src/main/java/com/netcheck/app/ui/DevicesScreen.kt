package com.netcheck.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.netcheck.app.R
import com.netcheck.app.model.CloudSettings
import com.netcheck.app.model.RemoteDevice
import com.netcheck.app.ui.theme.StatusAlarm
import com.netcheck.app.ui.theme.StatusAlarmBg
import com.netcheck.app.ui.theme.StatusIdle
import com.netcheck.app.ui.theme.StatusIdleBg
import com.netcheck.app.ui.theme.StatusOnline
import com.netcheck.app.ui.theme.StatusOnlineBg
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DevicesUiState(
    val isLoading: Boolean = false,
    val devices: List<RemoteDevice> = emptyList(),
    val serverTimeMs: Long = 0L,
    val lastUpdatedMs: Long = 0L,
    val errorMessage: String? = null
) {
    val onlineCount: Int
        get() = devices.count { it.isOnline }

    val offlineCount: Int
        get() = devices.count { !it.isOnline }
}

@Composable
fun DevicesScreen(
    settings: CloudSettings,
    state: DevicesUiState,
    onRefresh: () -> Unit,
    onDeleteDevice: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!settings.isConfigured) {
        UnconfiguredDevicesState(
            onOpenSettings = onOpenSettings,
            modifier = modifier
        )
        return
    }

    val latestRefresh by rememberUpdatedState(onRefresh)

    LaunchedEffect(settings.normalizedServerUrl, settings.trimmedGroupCode) {
        while (isActive) {
            latestRefresh()
            delay(10_000L)
        }
    }

    var deviceToDelete by remember { mutableStateOf<RemoteDevice?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        DevicesSummaryBar(state = state)

        if (state.errorMessage != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = StatusAlarmBg
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = StatusAlarm,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = state.errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAlarm
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (state.devices.isEmpty() && !state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.devices_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.devices_empty_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = state.devices,
                    key = { it.deviceId }
                ) { device ->
                    DeviceCard(
                        device = device,
                        isSelf = device.deviceId == settings.deviceId,
                        serverTimeMs = state.serverTimeMs,
                        onDeleteClick = { deviceToDelete = device }
                    )
                }
            }
        }
    }

    deviceToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { deviceToDelete = null },
            title = { Text(stringResource(R.string.dialog_remove_device_title)) },
            text = {
                Text(stringResource(R.string.dialog_remove_device_message, target.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteDevice(target.deviceId)
                        deviceToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.action_remove), color = StatusAlarm)
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun UnconfiguredDevicesState(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.devices_unconfigured_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(
                    R.string.devices_unconfigured_desc,
                    CloudSettings.MIN_GROUP_CODE_LENGTH
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(stringResource(R.string.btn_open_settings))
            }
        }
    }
}

@Composable
private fun DevicesSummaryBar(state: DevicesUiState) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(
                        R.string.devices_summary_counts,
                        state.onlineCount,
                        state.offlineCount
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (state.lastUpdatedMs > 0L) {
                    Text(
                        text = stringResource(
                            R.string.devices_summary_updated,
                            timeFormat.format(Date(state.lastUpdatedMs))
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.devices_summary_auto_refresh),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: RemoteDevice,
    isSelf: Boolean,
    serverTimeMs: Long,
    onDeleteClick: () -> Unit
) {
    val statusColor = if (device.isOnline) StatusOnline else StatusIdle
    val statusBgColor = if (device.isOnline) StatusOnlineBg else StatusIdleBg
    val statusLabel = stringResource(
        if (device.isOnline) R.string.device_status_online else R.string.device_status_offline
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (isSelf) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.device_badge_this_device),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = statusBgColor
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.cd_remove_device),
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(
                    R.string.device_last_seen,
                    formatRelativeLastSeen(serverTimeMs, device.lastSeenMs)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val hasTelemetry = device.wifiSsid != null ||
                device.latencyMs != null ||
                device.batteryPct != null

            if (hasTelemetry) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    device.wifiSsid?.let { ssid ->
                        TelemetryBadge(
                            icon = Icons.Filled.Wifi,
                            text = ssid
                        )
                    }
                    device.latencyMs?.let { latency ->
                        TelemetryBadge(
                            icon = Icons.Filled.Speed,
                            text = stringResource(R.string.metric_latency_value, latency)
                        )
                    }
                    device.batteryPct?.let { batteryPct ->
                        TelemetryBadge(
                            icon = if (device.isCharging) {
                                Icons.Filled.BatteryChargingFull
                            } else {
                                Icons.Filled.BatteryStd
                            },
                            text = if (device.isCharging) "${batteryPct}% ⚡" else "${batteryPct}%"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryBadge(
    icon: ImageVector,
    text: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun formatRelativeLastSeen(serverTimeMs: Long, lastSeenMs: Long): String {
    val referenceNow = if (serverTimeMs > 0L) serverTimeMs else System.currentTimeMillis()
    val deltaMs = (referenceNow - lastSeenMs).coerceAtLeast(0L)
    val seconds = deltaMs / 1000L
    return when {
        seconds < 10L -> stringResource(R.string.relative_time_just_now)
        seconds < 60L -> stringResource(R.string.relative_time_seconds_ago, seconds)
        seconds < 3600L -> stringResource(R.string.relative_time_minutes_ago, seconds / 60L)
        seconds < 86_400L -> stringResource(R.string.relative_time_hours_ago, seconds / 3600L)
        else -> stringResource(R.string.relative_time_days_ago, seconds / 86_400L)
    }
}
