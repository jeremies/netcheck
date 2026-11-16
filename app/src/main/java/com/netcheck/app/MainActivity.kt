package com.netcheck.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.netcheck.app.model.MonitoringStatus
import com.netcheck.app.model.WifiProbeInfo
import com.netcheck.app.service.WifiMonitorService
import com.netcheck.app.ui.theme.NetCheckTheme
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

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NetCheckTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val context = LocalContext.current
    val probeState by WifiMonitorService.probeState.collectAsState()

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (isGranted) {
            WifiMonitorService.start(context)
        }
    }

    val isMonitoring = probeState.status != MonitoringStatus.STOPPED

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // App Header
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "NetCheck",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Wi-Fi Loss Alarm (No 5G Fallback)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Central Status Indicator & Card
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                StatusBeacon(status = probeState.status)

                Spacer(modifier = Modifier.height(28.dp))

                StatusCard(info = probeState)
            }

            // Action Buttons
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // If alarming, show prominent MUTE ALARM button
                AnimatedVisibility(
                    visible = probeState.status == MonitoringStatus.ALARMING,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut()
                ) {
                    Button(
                        onClick = { WifiMonitorService.mute(context) },
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

                // Master Start / Stop Toggle Button
                Button(
                    onClick = {
                        if (isMonitoring) {
                            WifiMonitorService.stop(context)
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                WifiMonitorService.start(context)
                            }
                        }
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
}

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
        modifier = Modifier.size(160.dp)
    ) {
        // Outer pulsing glow
        Box(
            modifier = Modifier
                .size(150.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(beaconColor.copy(alpha = 0.2f))
        )

        // Middle solid ring
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(beaconBgColor)
                .border(3.dp, beaconColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = beaconColor,
                modifier = Modifier.size(54.dp)
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
