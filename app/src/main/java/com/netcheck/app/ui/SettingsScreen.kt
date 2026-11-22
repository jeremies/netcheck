package com.netcheck.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.netcheck.app.data.SettingsRepository
import com.netcheck.app.model.CloudSettings
import com.netcheck.app.service.CloudflareClient
import com.netcheck.app.ui.theme.StatusAlarm
import com.netcheck.app.ui.theme.StatusOnline
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    initialSettings: CloudSettings,
    cloudflareClient: CloudflareClient,
    onSave: (
        serverUrl: String,
        groupCode: String,
        deviceName: String,
        broadcastEnabled: Boolean
    ) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var serverUrl by remember(initialSettings) { mutableStateOf(initialSettings.serverUrl) }
    var groupCode by remember(initialSettings) { mutableStateOf(initialSettings.groupCode) }
    var deviceName by remember(initialSettings) { mutableStateOf(initialSettings.deviceName) }
    var broadcastEnabled by remember(initialSettings) { mutableStateOf(initialSettings.broadcastEnabled) }
    var showGroupCode by remember { mutableStateOf(false) }

    var isTesting by remember { mutableStateOf(false) }
    var testMessage by remember { mutableStateOf<String?>(null) }
    var testSucceeded by remember { mutableStateOf<Boolean?>(null) }
    var copiedNotice by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val trimmedCode = groupCode.trim()
    val isGroupCodeValid = trimmedCode.isEmpty() || trimmedCode.length >= CloudSettings.MIN_GROUP_CODE_LENGTH

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloudflare Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Broadcast Online Status",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Sends Wi-Fi-only heartbeats every 15s while monitoring is active.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = broadcastEnabled,
                        onCheckedChange = { broadcastEnabled = it }
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = {
                        serverUrl = it
                        testMessage = null
                        testSucceeded = null
                    },
                    label = { Text("Cloudflare Worker URL") },
                    placeholder = { Text("https://netcheck-server.<subdomain>.workers.dev") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        enabled = !isTesting && serverUrl.isNotBlank(),
                        onClick = {
                            isTesting = true
                            testMessage = null
                            testSucceeded = null
                            coroutineScope.launch {
                                val result = cloudflareClient.checkHealth(serverUrl)
                                isTesting = false
                                result.fold(
                                    onSuccess = { ms ->
                                        testSucceeded = true
                                        testMessage = "Connected (${ms}ms)"
                                    },
                                    onFailure = { err ->
                                        testSucceeded = false
                                        testMessage = err.message ?: "Connection failed"
                                    }
                                )
                            }
                        }
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Test Connection")
                    }

                    testMessage?.let { msg ->
                        val color = if (testSucceeded == true) StatusOnline else StatusAlarm
                        val icon = if (testSucceeded == true) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = color
                            )
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = groupCode,
                    onValueChange = {
                        groupCode = it
                        copiedNotice = false
                    },
                    label = { Text("Shared Group Code") },
                    placeholder = { Text("At least 6 characters") },
                    singleLine = true,
                    isError = !isGroupCodeValid,
                    visualTransformation = if (showGroupCode) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showGroupCode = !showGroupCode }) {
                            Icon(
                                imageVector = if (showGroupCode) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = if (showGroupCode) "Hide code" else "Show code"
                            )
                        }
                    },
                    supportingText = {
                        Text(
                            if (!isGroupCodeValid) {
                                "Group code must be at least ${CloudSettings.MIN_GROUP_CODE_LENGTH} characters"
                            } else {
                                "Use the same secret code on every device you want to see together."
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            groupCode = SettingsRepository.generateGroupCode()
                            showGroupCode = true
                            copiedNotice = false
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Generate Code")
                    }

                    OutlinedButton(
                        enabled = groupCode.isNotBlank(),
                        onClick = {
                            clipboardManager.setText(AnnotatedString(groupCode.trim()))
                            copiedNotice = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copiedNotice) "Copied" else "Copy")
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it.take(64) },
                    label = { Text("Device Name") },
                    placeholder = { Text(SettingsRepository.defaultDeviceName()) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Device ID: ${initialSettings.deviceId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                enabled = isGroupCodeValid,
                onClick = {
                    onSave(serverUrl, groupCode, deviceName, broadcastEnabled)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Save Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
