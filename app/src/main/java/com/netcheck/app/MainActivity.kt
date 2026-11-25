package com.netcheck.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat
import com.netcheck.app.data.InstallReferrerHelper
import com.netcheck.app.data.SettingsRepository
import com.netcheck.app.service.CloudflareClient
import com.netcheck.app.service.WifiMonitorService
import com.netcheck.app.ui.DevicesScreen
import com.netcheck.app.ui.DevicesUiState
import com.netcheck.app.ui.MonitorScreen
import com.netcheck.app.ui.SettingsScreen
import com.netcheck.app.ui.theme.NetCheckTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private enum class MainTab {
    MONITOR,
    DEVICES
}

data class PendingGroupLink(
    val id: Long,
    val groupCode: String
)

class MainActivity : ComponentActivity() {

    private var nextLinkEventId = 0L
    private var pendingGroupLink by mutableStateOf<PendingGroupLink?>(null)
    private var hadAppLinkOnLaunch by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            val initialCode = SettingsRepository.extractGroupCodeFromUri(intent?.data)
            if (initialCode != null) {
                hadAppLinkOnLaunch = true
                pendingGroupLink = PendingGroupLink(++nextLinkEventId, initialCode)
            }
        }

        setContent {
            NetCheckTheme {
                NetCheckApp(
                    pendingGroupLink = pendingGroupLink,
                    hadAppLinkOnLaunch = hadAppLinkOnLaunch,
                    onConsumePendingGroupLink = { pendingGroupLink = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val code = SettingsRepository.extractGroupCodeFromUri(intent.data)
        if (code != null) {
            hadAppLinkOnLaunch = true
            pendingGroupLink = PendingGroupLink(++nextLinkEventId, code)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetCheckApp(
    pendingGroupLink: PendingGroupLink? = null,
    hadAppLinkOnLaunch: Boolean = false,
    onConsumePendingGroupLink: () -> Unit = {}
) {
    val context = LocalContext.current
    val probeState by WifiMonitorService.probeState.collectAsState()
    val settingsRepository = remember { SettingsRepository.getInstance(context) }
    val settings by settingsRepository.settings.collectAsState()
    val cloudflareClient = remember(context) { CloudflareClient(context) }
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableStateOf(MainTab.MONITOR) }
    var showSettings by remember { mutableStateOf(false) }
    var devicesState by remember { mutableStateOf(DevicesUiState()) }
    var refreshJob by remember { mutableStateOf<Job?>(null) }
    var appliedAppLinkInSession by rememberSaveable { mutableStateOf(hadAppLinkOnLaunch) }

    fun refreshDevices() {
        val currentSettings = settingsRepository.settings.value
        if (!currentSettings.isConfigured) {
            devicesState = DevicesUiState()
            return
        }
        refreshJob?.cancel()
        devicesState = devicesState.copy(isLoading = true)
        refreshJob = coroutineScope.launch {
            val result = cloudflareClient.fetchDevices(currentSettings)
            result.fold(
                onSuccess = { payload ->
                    devicesState = DevicesUiState(
                        isLoading = false,
                        devices = payload.devices,
                        serverTimeMs = payload.serverTimeMs,
                        lastUpdatedMs = System.currentTimeMillis(),
                        errorMessage = null
                    )
                },
                onFailure = { error ->
                    devicesState = devicesState.copy(
                        isLoading = false,
                        errorMessage = error.message ?: context.getString(R.string.error_load_devices)
                    )
                }
            )
        }
    }

    fun deleteDevice(deviceId: String) {
        val currentSettings = settingsRepository.settings.value
        if (!currentSettings.isConfigured) return
        coroutineScope.launch {
            val result = cloudflareClient.deleteDevice(currentSettings, deviceId)
            result.fold(
                onSuccess = {
                    devicesState = devicesState.copy(
                        devices = devicesState.devices.filterNot { it.deviceId == deviceId },
                        errorMessage = null
                    )
                    refreshDevices()
                },
                onFailure = { error ->
                    devicesState = devicesState.copy(
                        errorMessage = error.message ?: context.getString(R.string.error_remove_device)
                    )
                }
            )
        }
    }

    // Fetch device list once on launch / config change so the Devices badge stays populated
    LaunchedEffect(settings.normalizedServerUrl, settings.trimmedGroupCode) {
        if (settings.isConfigured) {
            refreshDevices()
        } else {
            devicesState = DevicesUiState()
        }
    }

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
    var hasAttemptedAutoStart by rememberSaveable { mutableStateOf(false) }
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
        if (!WifiMonitorService.isRunning()) {
            WifiMonitorService.start(context)
        }
    }

    // Apply incoming App Link group code (cold start or singleTop onNewIntent)
    LaunchedEffect(pendingGroupLink?.id) {
        val linkEvent = pendingGroupLink ?: return@LaunchedEffect
        appliedAppLinkInSession = true
        onConsumePendingGroupLink()
        if (settingsRepository.applyGroupCode(linkEvent.groupCode)) {
            showSettings = false
            snackbarHostState.showSnackbar(context.getString(R.string.msg_group_code_updated))
        }
    }

    // Read Google Play Install Referrer once on first launch if no App Link already supplied a code
    LaunchedEffect(Unit) {
        if (!settingsRepository.isInstallReferrerChecked() && !appliedAppLinkInSession) {
            val referrerCode = InstallReferrerHelper.fetchGroupCode(context, settingsRepository)
            if (referrerCode != null && !appliedAppLinkInSession) {
                if (settingsRepository.applyGroupCode(referrerCode)) {
                    showSettings = false
                    snackbarHostState.showSnackbar(context.getString(R.string.msg_group_code_updated))
                }
            }
        }
    }

    // Start monitoring automatically once per app launch
    LaunchedEffect(Unit) {
        if (!hasAttemptedAutoStart) {
            hasAttemptedAutoStart = true
            if (!WifiMonitorService.isRunning()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !hasNotificationPermission
                ) {
                    hasRequestedNotificationPermission = true
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    WifiMonitorService.start(context)
                }
            }
        }
    }

    if (showSettings) {
        SettingsScreen(
            initialSettings = settings,
            cloudflareClient = cloudflareClient,
            onSave = { groupCode, deviceName, broadcastEnabled ->
                settingsRepository.save(
                    groupCode = groupCode,
                    deviceName = deviceName,
                    broadcastEnabled = broadcastEnabled
                )
                showSettings = false
                refreshDevices()
            },
            onBack = { showSettings = false }
        )
        return
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (selectedTab == MainTab.MONITOR) {
                                R.string.app_name
                            } else {
                                R.string.title_online_devices
                            }
                        ),
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    if (selectedTab == MainTab.DEVICES && settings.isConfigured) {
                        IconButton(onClick = { refreshDevices() }) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.cd_refresh_devices)
                            )
                        }
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.cd_settings)
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == MainTab.MONITOR,
                    onClick = { selectedTab = MainTab.MONITOR },
                    icon = {
                        Icon(
                            imageVector = Icons.Filled.Wifi,
                            contentDescription = stringResource(R.string.tab_monitor)
                        )
                    },
                    label = { Text(stringResource(R.string.tab_monitor)) }
                )
                NavigationBarItem(
                    selected = selectedTab == MainTab.DEVICES,
                    onClick = { selectedTab = MainTab.DEVICES },
                    icon = {
                        BadgedBox(
                            badge = {
                                if (devicesState.onlineCount > 0) {
                                    Badge {
                                        Text(devicesState.onlineCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Devices,
                                contentDescription = stringResource(R.string.tab_devices)
                            )
                        }
                    },
                    label = { Text(stringResource(R.string.tab_devices)) }
                )
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            MainTab.MONITOR -> {
                MonitorScreen(
                    probeState = probeState,
                    settings = settings,
                    onStartMonitoring = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            !hasNotificationPermission &&
                            !hasRequestedNotificationPermission
                        ) {
                            hasRequestedNotificationPermission = true
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            WifiMonitorService.start(context)
                        }
                    },
                    onStopMonitoring = { WifiMonitorService.stop(context) },
                    onMuteAlarm = { WifiMonitorService.mute(context) },
                    onOpenSettings = { showSettings = true },
                    modifier = Modifier.padding(innerPadding)
                )
            }
            MainTab.DEVICES -> {
                DevicesScreen(
                    settings = settings,
                    state = devicesState,
                    onRefresh = { refreshDevices() },
                    onDeleteDevice = { deleteDevice(it) },
                    onOpenSettings = { showSettings = true },
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
