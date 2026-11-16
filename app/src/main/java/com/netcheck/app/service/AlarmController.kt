package com.netcheck.app.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class AlarmController(private val context: Context) {

    private val tag = "AlarmController"
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var isMuted = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    @Synchronized
    fun startAlarm() {
        if (isMuted) {
            Log.d(tag, "Alarm is currently muted for this outage cycle.")
            return
        }

        if (isPlaying) {
            return
        }

        Log.w(tag, "Starting continuous alarm sound and vibration!")
        isPlaying = true

        startSound()
        startVibration()
    }

    private fun startSound() {
        try {
            var alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            if (alarmUri == null) {
                alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(tag, "Error starting alarm media player", e)
        }
    }

    private fun startVibration() {
        try {
            vibrator?.let { vib ->
                if (!vib.hasVibrator()) return@let

                val timings = longArrayOf(0, 600, 300, 600, 300)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)
                    vib.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(timings, 0)
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error starting vibration", e)
        }
    }

    @Synchronized
    fun stopAlarm() {
        if (!isPlaying) return

        Log.i(tag, "Stopping alarm sound and vibration.")
        isPlaying = false

        stopSound()
        stopVibration()
    }

    private fun stopSound() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e(tag, "Error stopping media player", e)
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping vibration", e)
        }
    }

    @Synchronized
    fun mute() {
        Log.i(tag, "Muting alarm.")
        isMuted = true
        stopAlarm()
    }

    @Synchronized
    fun resetMuteState() {
        isMuted = false
    }

    fun isAlarming(): Boolean = isPlaying
    fun isMuted(): Boolean = isMuted

    fun release() {
        stopAlarm()
        resetMuteState()
    }
}
