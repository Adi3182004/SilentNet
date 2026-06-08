package com.silentnet.emergency

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*

class EmergencyAlertManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var toneGenerator: ToneGenerator? = null
    private var alertJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var audioFocusRequest: AudioFocusRequest? = null

    fun startAlarm() {
        if (alertJob?.isActive == true) return

        Log.d("SilentNetEmergency", "Emergency Alarm Started")
        requestAudioFocus()

        alertJob = scope.launch {
            try {
                // Initialize ToneGenerator with MAX volume and through ALARM stream for better priority
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, 100)
                
                val startTime = System.currentTimeMillis()
                val duration = 7000L // 7 seconds

                Log.d("SilentNetEmergency", "Emergency Audio Started")
                while (System.currentTimeMillis() - startTime < duration) {
                    if (!isActive) break
                    
                    // Pattern: BEEP (500ms) - Pause (500ms)
                    toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 500)
                    delay(1000)
                }
            } catch (e: Exception) {
                Log.e("SilentNetEmergency", "Error playing emergency audio", e)
            } finally {
                stopAlarmInternal()
            }
        }
    }

    fun stopAlarm() {
        if (alertJob?.isActive == true) {
            Log.d("SilentNetEmergency", "Emergency Alarm Stopped (Acknowledged/Dismissed)")
            alertJob?.cancel()
            stopAlarmInternal()
        }
    }

    private fun stopAlarmInternal() {
        toneGenerator?.release()
        toneGenerator = null
        releaseAudioFocus()
        Log.d("SilentNetEmergency", "Emergency Audio Stopped")
        Log.d("SilentNetEmergency", "Emergency Alarm Stopped")
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .setOnAudioFocusChangeListener { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        Log.d("SilentNetEmergency", "Audio Focus Lost")
                        stopAlarm()
                    }
                }
                .build()
            
            audioManager.requestAudioFocus(audioFocusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                { focusChange ->
                    if (focusChange == AudioManager.AUDIOFOCUS_LOSS || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        Log.d("SilentNetEmergency", "Audio Focus Lost")
                        stopAlarm()
                    }
                },
                AudioManager.STREAM_ALARM,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
            )
        }
        Log.d("SilentNetEmergency", "Audio Focus Granted")
    }

    private fun releaseAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus { }
        }
    }
}
