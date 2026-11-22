package com.netcheck.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.netcheck.app.model.CloudSettings
import com.netcheck.app.model.MonitoringStatus
import com.netcheck.app.model.WifiProbeInfo
import com.netcheck.app.ui.theme.StatusAlarm
import com.netcheck.app.ui.theme.StatusAlarmBg
import com.netcheck.app.ui.theme.StatusChecking
import com.netcheck.app.ui.theme.StatusCheckingBg
import com.netcheck.app.ui.theme.StatusIdle
import com.netcheck.app.ui.theme.StatusIdleBg
import com.netcheck.app.ui.theme.StatusMuted
import com.netcheck.app.ui.theme.StatusMutedBg
import com.netcheck.app.ui.theme.StatusOnline
import com.netcheck.app.ui.theme.StatusOnlineBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MonitorScreen(
    probeState: WifiProbeInfo,
    settings: CloudSettings,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onMuteAlarm: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isMonitoring = probeState.status != MonitoringStatus.STOPPED

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Wi-Fi Loss Alarm (No 5G Fallback)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            StatusBeacon(status = probeState.status)

            Spacer(modifier = Modifier.height(24.dp))

            StatusCard(info = probeState)

            Spacer(modifier = Modifier.height(12.dp))

            CloudBroadcastChip(
                probeState = probeState,
                settings = settings,
                onClick = onOpenSettings
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            AnimatedVisibility(
                visible = probeState.status == MonitoringStatus.ALARMING,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut()
            ) {
                Button(
                    onClick = onMuteAlarm,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(bottom = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusAlarm,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Filled.VolumeOff, contentDescription = "Mute")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "MUTE ALARM",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            }

            Button(
                onClick = {
                    if (isMonitoring) onStopMonitoring() else onStartMonitoring()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMonitoring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = if (isMonitoring) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = if (isMonitoring) "STOP MONITORING" else "START MONITORING",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CloudBroadcastChip(
    probeState: WifiProbeInfo,
    settings: CloudSettings,
    onClick: () -> Unit
) {
    val isMonitoring = probeState.status != MonitoringStatus.STOPPED
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    val (icon, text, tint, bgColor) = when {
        !settings.isConfigured -> Quadruple(
            Icons.Filled.CloudOff,
            "Cloud sync not configured · Tap to set up",
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
        !settings.broadcastEnabled -> Quadruple(
            Icons.Filled.CloudOff,
            "Cloud broadcast disabled",
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
        isMonitoring && probeState.lastSyncError != null -> Quadruple(
            Icons.Filled.ErrorOutline,
            "Sync error: ${probeState.lastSyncError}",
            StatusAlarm,
            StatusAlarmBg
        )
        isMonitoring && probeState.lastSyncTimestamp > 0L -> Quadruple(
            Icons.Filled.CloudDone,
            "Broadcasting as ${settings.deviceName} · synced ${timeFormat.format(Date(probeState.lastSyncTimestamp))}",
            StatusOnline,
            StatusOnlineBg
        )
        isMonitoring -> Quadruple(
            Icons.Filled.Cloud,
            "Waiting for Wi-Fi online heartbeat…",
            StatusChecking,
            StatusCheckingBg
        )
        else -> Quadruple(
            Icons.Filled.Cloud,
            "Will broadcast as ${settings.deviceName} when monitoring starts",
            MaterialTheme.colorScheme.onSurfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

@Composable
fun StatusBeacon(status: MonitoringStatus) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (status == MonitoringStatus.ALARMING) 1.25f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (status == MonitoringStatus.ALARMING) 500 else 1800,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val (beaconColor, beaconBgColor, icon) = when (status) {
        MonitoringStatus.STOPPED -> Triple(StatusIdle, StatusIdleBg, Icons.Filled.WifiOff)
        MonitoringStatus.CHECKING -> Triple(StatusChecking, StatusCheckingBg, Icons.Filled.Wifi)
        MonitoringStatus.CONNECTED_ONLINE -> Triple(StatusOnline, StatusOnlineBg, Icons.Filled.Wifi)
        MonitoringStatus.ALARMING -> Triple(StatusAlarm, StatusAlarmBg, Icons.Filled.Warning)
        MonitoringStatus.MUTED -> Triple(StatusMuted, StatusMutedBg, Icons.Filled.NotificationsOff)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(156.dp)
    ) {
        Box(
            modifier = Modifier
                .size(146.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(beaconColor.copy(alpha = 0.2f))
        )

        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(CircleShape)
                .background(beaconBgColor)
                .border(3.dp, beaconColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = beaconColor,
                modifier = Modifier.size(52.dp)
            )
        }
    }
}

@Composable
fun StatusCard(info: WifiProbeInfo) {
    val (title, titleColor, desc) = when (info.status) {
        MonitoringStatus.STOPPED -> Triple(
            "Service Stopped",
            StatusIdle,
            "Tap Start Monitoring to begin checking Wi-Fi internet."
        )
        MonitoringStatus.CHECKING -> Triple(
            "Verifying Wi-Fi…",
            StatusChecking,
            "Testing connection to Google captive check via Wi-Fi only."
        )
        MonitoringStatus.CONNECTED_ONLINE -> Triple(
            "Wi-Fi Online",
            StatusOnline,
            "Connected to ${info.wifiSsid ?: "Wi-Fi"} with active internet access."
        )
        MonitoringStatus.ALARMING -> Triple(
            "ALARM: NO INTERNET!",
            StatusAlarm,
            if (!info.isWifiConnected) {
                "Device is disconnected from Wi-Fi!"
            } else {
                "Wi-Fi connected (${info.wifiSsid ?: "network"}), but cannot reach internet!"
            }
        )
        MonitoringStatus.MUTED -> Triple(
            "Alarm Muted",
            StatusMuted,
            "Alarm silenced for this outage. Will re-arm once connection recovers."
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (info.status != MonitoringStatus.STOPPED) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    MetricItem(
                        label = "Wi-Fi Network",
                        value = if (info.isWifiConnected) info.wifiSsid ?: "Active" else "Disconnected"
                    )

                    MetricItem(
                        label = "Latency",
                        value = info.latencyMs?.let { "${it}ms" } ?: "--"
                    )

                    MetricItem(
                        label = "Failures",
                        value = "${info.consecutiveFailures}/2"
                    )
                }

                if (info.lastCheckedTimestamp > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    Text(
                        text = "Last probe: ${timeFormat.format(Date(info.lastCheckedTimestamp))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
