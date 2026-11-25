package com.netcheck.app.data

import android.content.Context
import android.util.Log
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerClient.InstallReferrerResponse
import com.android.installreferrer.api.InstallReferrerStateListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

object InstallReferrerHelper {

    private const val TAG = "InstallReferrerHelper"
    private const val CONNECTION_TIMEOUT_MS = 10_000L

    /**
     * Queries Google Play's Install Referrer API once to extract a shared group code from
     * the Play Store link's `referrer` parameter. Marks the check completed for terminal
     * responses so manual group edits are never overwritten on later launches.
     */
    suspend fun fetchGroupCode(
        context: Context,
        settingsRepository: SettingsRepository
    ): String? {
        if (settingsRepository.isInstallReferrerChecked()) {
            return null
        }

        return withTimeoutOrNull(CONNECTION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val client = InstallReferrerClient.newBuilder(context.applicationContext).build()
                val finished = AtomicBoolean(false)

                fun complete(code: String?, markChecked: Boolean) {
                    if (!finished.compareAndSet(false, true)) return
                    if (markChecked) {
                        settingsRepository.markInstallReferrerChecked()
                    }
                    runCatching { client.endConnection() }
                    if (continuation.isActive) {
                        continuation.resume(code)
                    }
                }

                continuation.invokeOnCancellation {
                    if (finished.compareAndSet(false, true)) {
                        runCatching { client.endConnection() }
                    }
                }

                try {
                    client.startConnection(object : InstallReferrerStateListener {
                        override fun onInstallReferrerSetupFinished(responseCode: Int) {
                            when (responseCode) {
                                InstallReferrerResponse.OK -> {
                                    val rawReferrer = try {
                                        client.installReferrer?.installReferrer
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed reading install referrer details", e)
                                        null
                                    }
                                    val code = SettingsRepository.extractGroupCodeFromReferrer(rawReferrer)
                                    complete(code = code, markChecked = true)
                                }

                                InstallReferrerResponse.FEATURE_NOT_SUPPORTED,
                                InstallReferrerResponse.DEVELOPER_ERROR -> {
                                    Log.i(TAG, "Install referrer unavailable (code=$responseCode)")
                                    complete(code = null, markChecked = true)
                                }

                                else -> {
                                    Log.w(TAG, "Install referrer transient failure (code=$responseCode)")
                                    complete(code = null, markChecked = false)
                                }
                            }
                        }

                        override fun onInstallReferrerServiceDisconnected() {
                            complete(code = null, markChecked = false)
                        }
                    })
                } catch (e: Exception) {
                    Log.w(TAG, "Failed starting InstallReferrerClient", e)
                    complete(code = null, markChecked = false)
                }
            }
        }
    }
}
