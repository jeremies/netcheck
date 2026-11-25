package com.netcheck.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.netcheck.app.BuildConfig
import com.netcheck.app.R
import com.netcheck.app.model.CloudSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.util.Locale
import java.util.UUID

class SettingsRepository private constructor(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings: MutableStateFlow<CloudSettings>

    val settings: StateFlow<CloudSettings>
        get() = _settings.asStateFlow()

    init {
        val deviceId = getOrCreateDeviceId()
        _settings = MutableStateFlow(loadSettings(deviceId))
    }

    private fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) {
            return existing
        }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    private fun loadSettings(deviceId: String): CloudSettings {
        val savedCode = prefs.getString(KEY_GROUP_CODE, null)
        val groupCode = if (savedCode != null) savedCode else BuildConfig.DEFAULT_GROUP_CODE

        val savedName = prefs.getString(KEY_DEVICE_NAME, null)
        val deviceName = if (!savedName.isNullOrBlank()) savedName else defaultDeviceName(appContext)

        val broadcastEnabled = prefs.getBoolean(KEY_BROADCAST_ENABLED, true)

        return CloudSettings(
            serverUrl = CloudSettings.SERVER_URL,
            groupCode = groupCode,
            deviceName = deviceName,
            deviceId = deviceId,
            broadcastEnabled = broadcastEnabled
        )
    }

    @Synchronized
    fun save(
        groupCode: String,
        deviceName: String,
        broadcastEnabled: Boolean
    ): CloudSettings {
        val normalizedName = deviceName.trim().ifBlank { defaultDeviceName(appContext) }.take(64)
        val trimmedCode = groupCode.trim()
        val deviceId = _settings.value.deviceId

        prefs.edit()
            .remove(KEY_SERVER_URL)
            .putString(KEY_GROUP_CODE, trimmedCode)
            .putString(KEY_DEVICE_NAME, normalizedName)
            .putBoolean(KEY_BROADCAST_ENABLED, broadcastEnabled)
            .apply()

        val updated = CloudSettings(
            serverUrl = CloudSettings.SERVER_URL,
            groupCode = trimmedCode,
            deviceName = normalizedName,
            deviceId = deviceId,
            broadcastEnabled = broadcastEnabled
        )
        _settings.value = updated
        return updated
    }

    companion object {
        private const val PREFS_NAME = "netcheck_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_GROUP_CODE = "group_code"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_BROADCAST_ENABLED = "broadcast_enabled"

        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val secureRandom = SecureRandom()

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context).also { instance = it }
            }
        }

        fun defaultDeviceName(context: Context): String {
            val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
            val model = Build.MODEL?.trim().orEmpty()
            if (model.isEmpty()) return context.getString(R.string.default_device_name)
            if (manufacturer.isEmpty() ||
                model.lowercase(Locale.US).startsWith(manufacturer.lowercase(Locale.US))
            ) {
                return model.take(64)
            }
            val capitalizedManufacturer = manufacturer.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
            }
            return "$capitalizedManufacturer $model".take(64)
        }

        fun generateGroupCode(): String {
            return (0 until 3).joinToString("-") {
                buildString(4) {
                    repeat(4) {
                        append(CODE_ALPHABET[secureRandom.nextInt(CODE_ALPHABET.length)])
                    }
                }
            }
        }
    }
}
