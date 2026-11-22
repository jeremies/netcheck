package com.netcheck.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.netcheck.app.MainActivity
import com.netcheck.app.R
import com.netcheck.app.data.SettingsRepository
import com.netcheck.app.model.HeartbeatPayload
import com.netcheck.app.model.MonitoringStatus
import com.netcheck.app.model.WifiProbeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class WifiMonitorService : Service() {

    private val tag = "WifiMonitorService"

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private var monitorJob: Job? = null
    private var stopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private lateinit var prober: NetworkProber
    private lateinit var alarmController: AlarmController
    private lateinit var notificationManager: NotificationManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var cloudflareClient: CloudflareClient

    private var consecutiveFailures = 0

    companion object {
        const val ACTION_START = "com.netcheck.app.action.START"
        const val ACTION_STOP = "com.netcheck.app.action.STOP"
        const val ACTION_MUTE = "com.netcheck.app.action.MUTE"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "netcheck_monitor_channel"
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val OFFLINE_TIMEOUT_MS = 2_500L

        private val _probeState = MutableStateFlow(WifiProbeInfo())
        val probeState: StateFlow<WifiProbeInfo> = _probeState.asStateFlow()

        fun isRunning(): Boolean {
            return _probeState.value.status != MonitoringStatus.STOPPED
        }

        fun start(context: Context) {
            val intent = Intent(context, WifiMonitorService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, WifiMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun mute(context: Context) {
            val intent = Intent(context, WifiMonitorService::class.java).apply {
                action = ACTION_MUTE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(tag, "Service created")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        prober = NetworkProber(applicationContext)
        alarmController = AlarmController(applicationContext)
        settingsRepository = SettingsRepository.getInstance(applicationContext)
        cloudflareClient = CloudflareClient()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "NetCheck:WifiMonitorWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart()
            ACTION_STOP -> handleStop()
            ACTION_MUTE -> handleMute()
            else -> handleStart()
        }
        return START_STICKY
    }

    private fun handleStart() {
        Log.i(tag, "Starting Wi-Fi monitor")
        stopJob?.cancel()
        stopJob = null
        wakeLock?.acquire(24 * 60 * 60 * 1000L) // 24h safety limit

        val currentSettings = settingsRepository.settings.value
        val initialInfo = WifiProbeInfo(
            status = MonitoringStatus.CHECKING,
            consecutiveFailures = 0,
            wifiSsid = prober.getSsid(),
            isWifiConnected = prober.isWifiConnected(),
            isBroadcastConfigured = currentSettings.isConfigured,
            isBroadcastEnabled = currentSettings.broadcastEnabled
        )
        _probeState.value = initialInfo

        val notification = buildNotification(initialInfo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startMonitoringLoop()
    }

    private fun handleStop() {
        Log.i(tag, "Stopping Wi-Fi monitor")
        monitorJob?.cancel()
        monitorJob = null

        alarmController.stopAlarm()
        alarmController.resetMuteState()

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }

        val settings = settingsRepository.settings.value
        val wifiNetwork = prober.getActiveWifiNetwork()

        _probeState.value = WifiProbeInfo(
            status = MonitoringStatus.STOPPED,
            isBroadcastConfigured = settings.isConfigured,
            isBroadcastEnabled = settings.broadcastEnabled
        )

        stopForeground(STOP_FOREGROUND_REMOVE)

        if (settings.broadcastEnabled && settings.isConfigured) {
            stopJob?.cancel()
            stopJob = serviceScope.launch {
                withTimeoutOrNull(OFFLINE_TIMEOUT_MS) {
                    cloudflareClient.sendOffline(wifiNetwork, settings)
                }
                if (isActive) {
                    stopSelf()
                }
            }
        } else {
            stopSelf()
        }
    }

    private fun handleMute() {
        Log.i(tag, "Muting alarm from action")
        alarmController.mute()
        if (_probeState.value.status == MonitoringStatus.ALARMING) {
            val updated = _probeState.value.copy(status = MonitoringStatus.MUTED)
            _probeState.value = updated
            notificationManager.notify(NOTIFICATION_ID, buildNotification(updated))
        }
    }

    private fun startMonitoringLoop() {
        monitorJob?.cancel()
        consecutiveFailures = 0

        monitorJob = serviceScope.launch {
            var lastHeartbeatElapsedMs = 0L
            var lastSyncTimestamp = 0L
            var lastSyncError: String? = null
            var wasOnlinePreviously = false
            var wasBroadcastReadyPreviously = false

            while (isActive) {
                val result = prober.probeWifiInternet()
                val currentTime = System.currentTimeMillis()
                val settings = settingsRepository.settings.value
                val broadcastReady = settings.broadcastEnabled && settings.isConfigured

                val newStatus: MonitoringStatus
                if (result.success) {
                    consecutiveFailures = 0
                    if (alarmController.isAlarming() || alarmController.isMuted()) {
                        Log.i(tag, "Wi-Fi internet recovered! Automatically silencing alarm.")
                        alarmController.stopAlarm()
                        alarmController.resetMuteState()
                    }
                    newStatus = MonitoringStatus.CONNECTED_ONLINE

                    if (broadcastReady) {
                        val nowElapsed = SystemClock.elapsedRealtime()
                        val shouldSend = !wasOnlinePreviously ||
                            !wasBroadcastReadyPreviously ||
                            (nowElapsed - lastHeartbeatElapsedMs >= HEARTBEAT_INTERVAL_MS)
                        if (shouldSend) {
                            val wifiNetwork = prober.getActiveWifiNetwork()
                            if (wifiNetwork != null) {
                                val battery = BatteryReader.read(applicationContext)
                                val payload = HeartbeatPayload(
                                    deviceId = settings.deviceId,
                                    name = settings.deviceName,
                                    wifiSsid = result.ssid ?: prober.getSsid(),
                                    latencyMs = result.latencyMs?.toInt(),
                                    batteryPct = battery.percentage,
                                    isCharging = battery.isCharging
                                )
                                lastHeartbeatElapsedMs = nowElapsed
                                val syncResult = cloudflareClient.sendHeartbeat(wifiNetwork, settings, payload)
                                syncResult.fold(
                                    onSuccess = { serverTime ->
                                        lastSyncTimestamp = serverTime
                                        lastSyncError = null
                                    },
                                    onFailure = { err ->
                                        // Server/sync errors must never trigger the Wi-Fi loss alarm
                                        Log.w(tag, "Cloud heartbeat failed: ${err.message}")
                                        lastSyncError = err.message ?: "Sync failed"
                                    }
                                )
                            }
                        }
                        wasBroadcastReadyPreviously = true
                    } else {
                        wasBroadcastReadyPreviously = false
                        lastSyncError = null
                    }
                    wasOnlinePreviously = true
                } else {
                    wasOnlinePreviously = false
                    if (!broadcastReady) {
                        wasBroadcastReadyPreviously = false
                        lastSyncError = null
                    }

                    consecutiveFailures++
                    Log.w(tag, "Probe failure #$consecutiveFailures: ${result.errorMessage}")

                    if (consecutiveFailures >= 2) {
                        if (alarmController.isMuted()) {
                            newStatus = MonitoringStatus.MUTED
                        } else {
                            newStatus = MonitoringStatus.ALARMING
                            alarmController.startAlarm()
                        }
                    } else {
                        // 1 failure is considered transient jitter
                        newStatus = if (_probeState.value.status == MonitoringStatus.CONNECTED_ONLINE) {
                            MonitoringStatus.CHECKING
                        } else {
                            _probeState.value.status
                        }
                    }
                }

                val updatedInfo = WifiProbeInfo(
                    status = newStatus,
                    consecutiveFailures = consecutiveFailures,
                    latencyMs = result.latencyMs,
                    lastCheckedTimestamp = currentTime,
                    wifiSsid = result.ssid ?: prober.getSsid(),
                    isWifiConnected = result.isWifiConnected,
                    errorMessage = result.errorMessage,
                    lastSyncTimestamp = lastSyncTimestamp,
                    lastSyncError = lastSyncError,
                    isBroadcastConfigured = settings.isConfigured,
                    isBroadcastEnabled = settings.broadcastEnabled
                )

                _probeState.value = updatedInfo
                notificationManager.notify(NOTIFICATION_ID, buildNotification(updatedInfo))

                delay(5000L)
            }
        }
    }

    private fun buildNotification(info: WifiProbeInfo): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, WifiMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(
                if (info.status == MonitoringStatus.ALARMING) {
                    NotificationCompat.PRIORITY_MAX
                } else {
                    NotificationCompat.PRIORITY_LOW
                }
            )

        when (info.status) {
            MonitoringStatus.ALARMING -> {
                builder.setSmallIcon(R.drawable.ic_warning)
                    .setContentTitle("⚠️ NO WI-FI INTERNET!")
                    .setContentText(
                        if (!info.isWifiConnected) "Wi-Fi is disconnected!"
                        else "Connected to ${info.wifiSsid ?: "Wi-Fi"}, but no internet connection!"
                    )
                    .setColor(0xFFE53935.toInt())

                val muteIntent = Intent(this, WifiMonitorService::class.java).apply {
                    action = ACTION_MUTE
                }
                val mutePendingIntent = PendingIntent.getService(
                    this,
                    2,
                    muteIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.drawable.ic_volume_off, "Mute Alarm", mutePendingIntent)
            }
            MonitoringStatus.MUTED -> {
                builder.setSmallIcon(R.drawable.ic_warning)
                    .setContentTitle("⚠️ Alarm Muted (Offline)")
                    .setContentText("Wi-Fi has no internet. Waiting for reconnection…")
                    .setColor(0xFFF57C00.toInt())
            }
            MonitoringStatus.CONNECTED_ONLINE -> {
                builder.setSmallIcon(R.drawable.ic_wifi_check)
                    .setContentTitle("Wi-Fi Online")
                    .setContentText("Monitoring ${info.wifiSsid ?: "Wi-Fi"} (${info.latencyMs ?: 0}ms)")
                    .setColor(0xFF4CAF50.toInt())
            }
            MonitoringStatus.CHECKING -> {
                builder.setSmallIcon(R.drawable.ic_wifi_check)
                    .setContentTitle("Checking Wi-Fi…")
                    .setContentText("Verifying internet connectivity")
                    .setColor(0xFF2196F3.toInt())
            }
            MonitoringStatus.STOPPED -> {
                builder.setSmallIcon(R.drawable.ic_wifi_check)
                    .setContentTitle("NetCheck Idle")
                    .setContentText("Monitoring is stopped")
            }
        }

        builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(tag, "Service destroying")
        serviceJob.cancel()
        prober.release()
        alarmController.release()
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        _probeState.value = WifiProbeInfo(status = MonitoringStatus.STOPPED)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
