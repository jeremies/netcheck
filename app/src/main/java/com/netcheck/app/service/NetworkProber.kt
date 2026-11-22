package com.netcheck.app.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.netcheck.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class NetworkProber(private val context: Context) {

    private val tag = "NetworkProber"
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    @Volatile
    private var activeWifiNetwork: Network? = null

    @Volatile
    private var currentSsid: String? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(tag, "Wi-Fi network became available: $network")
            activeWifiNetwork = network
            updateSsid(network)
        }

        override fun onLost(network: Network) {
            Log.d(tag, "Wi-Fi network lost: $network")
            if (activeWifiNetwork == network) {
                activeWifiNetwork = null
                currentSsid = null
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            if (networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                activeWifiNetwork = network
                updateSsid(network)
            }
        }
    }

    init {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
    }

    private fun updateSsid(network: Network) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val caps = connectivityManager.getNetworkCapabilities(network)
                val wifiInfo = caps?.transportInfo as? WifiInfo
                val ssid = wifiInfo?.ssid?.trim('\"')
                if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                    currentSsid = ssid
                    return
                }
            }
            @Suppress("DEPRECATION")
            val connectionInfo = wifiManager?.connectionInfo
            val ssid = connectionInfo?.ssid?.trim('\"')
            if (!ssid.isNullOrEmpty() && ssid != "<unknown ssid>") {
                currentSsid = ssid
            }
        } catch (e: Exception) {
            Log.w(tag, "Could not resolve SSID: ${e.message}")
        }
    }

    data class ProbeResult(
        val success: Boolean,
        val latencyMs: Long? = null,
        val ssid: String? = null,
        val isWifiConnected: Boolean = false,
        val errorMessage: String? = null
    )

    suspend fun probeWifiInternet(): ProbeResult = withContext(Dispatchers.IO) {
        val wifiNetwork = activeWifiNetwork
        if (wifiNetwork == null) {
            return@withContext ProbeResult(
                success = false,
                isWifiConnected = false,
                ssid = null,
                errorMessage = context.getString(R.string.error_wifi_disconnected)
            )
        }

        val startTime = SystemClock.elapsedRealtime()
        var urlConnection: HttpURLConnection? = null

        try {
            // Strictly bind connection to the Wi-Fi Network object
            // This prevents Android from falling back to Cellular/5G
            val probeUrl = URL("https://connectivitycheck.gstatic.com/generate_204")
            urlConnection = wifiNetwork.openConnection(probeUrl) as HttpURLConnection

            urlConnection.apply {
                instanceFollowRedirects = false
                connectTimeout = 3500
                readTimeout = 3500
                useCaches = false
                defaultUseCaches = false
                setRequestProperty("Connection", "close")
                setRequestProperty("User-Agent", "NetCheck/1.0 (Android)")
            }

            val responseCode = urlConnection.responseCode
            val elapsed = SystemClock.elapsedRealtime() - startTime

            // HTTP 204 No Content is Google's connectivity check success response
            if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                ProbeResult(
                    success = true,
                    latencyMs = elapsed,
                    ssid = currentSsid,
                    isWifiConnected = true
                )
            } else {
                ProbeResult(
                    success = false,
                    latencyMs = elapsed,
                    ssid = currentSsid,
                    isWifiConnected = true,
                    errorMessage = context.getString(R.string.error_probe_http_status, responseCode)
                )
            }
        } catch (e: IOException) {
            val elapsed = SystemClock.elapsedRealtime() - startTime
            Log.d(tag, "Wi-Fi probe failed (${elapsed}ms): ${e.message}")
            ProbeResult(
                success = false,
                latencyMs = elapsed,
                ssid = currentSsid,
                isWifiConnected = true,
                errorMessage = e.message ?: context.getString(R.string.error_probe_connection_timeout)
            )
        } catch (e: Exception) {
            Log.e(tag, "Unexpected probe error", e)
            ProbeResult(
                success = false,
                ssid = currentSsid,
                isWifiConnected = true,
                errorMessage = e.message ?: context.getString(R.string.error_unknown)
            )
        } finally {
            urlConnection?.disconnect()
        }
    }

    fun getSsid(): String? = currentSsid

    fun isWifiConnected(): Boolean = activeWifiNetwork != null

    fun getActiveWifiNetwork(): Network? = activeWifiNetwork

    fun release() {
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            Log.e(tag, "Error unregistering network callback", e)
        }
    }
}
