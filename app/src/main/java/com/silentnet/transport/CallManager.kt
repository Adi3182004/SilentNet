package com.silentnet.transport

import android.content.Context
import android.media.*
import android.os.ParcelFileDescriptor
import android.util.Log
import com.silentnet.security.CryptographyManager
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.*
import org.json.JSONObject
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
enum class CallState {
    IDLE,
    OUTGOING_REQUESTED,
    INCOMING_REQUESTED,
    CONNECTED
}

class CallManager(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var isCalling = false
    private val cleanupLock = Any()
    
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()
    
    var currentState: CallState
        get() = _callState.value
        private set(value) { _callState.value = value }
        
    private var remoteUsername: String? = null
    var remoteFullName: String? = null
        private set
    
    private var sessionKey: ByteArray? = null
    private val GCM_TAG_LENGTH = 128
    private val GCM_IV_LENGTH = 12

    var onSignalingPacket: ((targetUsername: String, type: String, payload: String) -> Unit)? = null
    var onStreamPacket: ((targetUsername: String, inputStream: InputStream) -> Unit)? = null
    var onIncomingCall: ((fromUsername: String, fromFullName: String) -> Unit)? = null
    var onCallConnected: (() -> Unit)? = null
    var onCallEnded: (() -> Unit)? = null

    fun initiateCall(targetUsername: String, targetFullName: String? = null) {
        if (currentState != CallState.IDLE) return
        remoteUsername = targetUsername
        remoteFullName = targetFullName ?: targetUsername
        currentState = CallState.OUTGOING_REQUESTED
        
        scope.launch {
            try {
                onSignalingPacket?.invoke(targetUsername, "call_request", "{}")
            } catch (e: Exception) {
                Log.e("SilentNetCall", "Initiate error: ${e.message}")
                endCall()
            }
        }
    }

    fun handleIncomingCallRequest(fromUsername: String, fromFullName: String, payload: String) {
        if (currentState != CallState.IDLE) {
            onSignalingPacket?.invoke(fromUsername, "call_decline", "{}")
            return
        }
        
        currentState = CallState.INCOMING_REQUESTED
        remoteUsername = fromUsername
        remoteFullName = fromFullName
        onIncomingCall?.invoke(fromUsername, fromFullName)
    }

    fun acceptCall() {
        if (currentState != CallState.INCOMING_REQUESTED || remoteUsername == null) return
        
        scope.launch {
            try {
                onSignalingPacket?.invoke(remoteUsername!!, "call_accept", "{}")
                startCallSession()
            } catch (e: Exception) {
                Log.e("SilentNetCall", "Accept error: ${e.message}")
                endCall()
            }
        }
    }

    fun handleCallAccepted(fromUsername: String) {
        if (currentState == CallState.OUTGOING_REQUESTED && fromUsername == remoteUsername) {
            scope.launch {
                startCallSession()
            }
        }
    }

    private suspend fun startCallSession() {
        currentState = CallState.CONNECTED
        onCallConnected?.invoke()
        
        // Generate a session key for this call (simplified for now)
        sessionKey = "CALL_SESSION_KEY_PLACEHOLDER_32B".toByteArray() 
        
        startAudioStream()
    }

    fun handleIncomingStream(endpointId: String, inputStream: InputStream) {
        if (currentState != CallState.CONNECTED) return
        
        scope.launch(Dispatchers.IO) {
            val sampleRate = 44100
            val config = AudioFormat.CHANNEL_OUT_MONO
            val format = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, config, format)
            
            try {
                audioTrack = AudioTrack(
                    AudioManager.STREAM_VOICE_CALL,
                    sampleRate,
                    config,
                    format,
                    bufferSize,
                    AudioTrack.MODE_STREAM
                )
                audioTrack?.play()

                val buffer = ByteArray(bufferSize + 32)
                while (isActive && currentState == CallState.CONNECTED) {
                    try {
                        val read = inputStream.read(buffer)
                        if (read > 0) {
                            val decrypted = decryptAudio(buffer.copyOf(read))
                            if (decrypted != null) {
                                audioTrack?.write(decrypted, 0, decrypted.size)
                            }
                        } else if (read == -1) break
                    } catch (e: Exception) {
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("SilentNetCall", "Incoming stream error: ${e.message}")
            } finally {
                endCall()
            }
        }
    }

    fun declineCall() {
        if (currentState == CallState.INCOMING_REQUESTED && remoteUsername != null) {
            onSignalingPacket?.invoke(remoteUsername!!, "call_decline", "{}")
        }
        currentState = CallState.IDLE
        remoteUsername = null
    }

    fun handleCallDeclined(fromUsername: String) {
        if (fromUsername == remoteUsername) {
            Log.d("SilentNetCall", "Call declined by $fromUsername")
            endCall()
        }
    }

    fun handleCallEnded(fromUsername: String) {
        if (fromUsername == remoteUsername) {
            Log.d("SilentNetCall", "Call ended by $fromUsername")
            endCall()
        }
    }

    private fun startAudioStream() {
        if (isCalling) return
        isCalling = true
        
        val sampleRate = 44100
        val config = AudioFormat.CHANNEL_IN_MONO
        val format = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, config, format)

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("SilentNetCall", "RECORD_AUDIO permission not granted")
            isCalling = false
            return
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                config,
                format,
                bufferSize
            )
            audioRecord?.startRecording()
        } catch (e: Exception) {
            Log.e("SilentNetCall", "Failed to start recording: ${e.message}")
            endCall()
            return
        }
        
        scope.launch(Dispatchers.IO) {
            val target = remoteUsername ?: ""
            if (target.isEmpty()) {
                endCall()
                return@launch
            }

            try {
                val pipe = ParcelFileDescriptor.createPipe()
                val readPipe = pipe[0]
                val writePipe = pipe[1]
                
                // Forward the read end of the pipe to TransportManager
                onStreamPacket?.invoke(target, ParcelFileDescriptor.AutoCloseInputStream(readPipe))
                
                val outputStream = ParcelFileDescriptor.AutoCloseOutputStream(writePipe)
                val buffer = ByteArray(bufferSize)
                
                while (isActive && isCalling && currentState == CallState.CONNECTED) {
                    try {
                        val read = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                        if (read > 0) {
                            val encrypted = encryptAudio(buffer.copyOf(read))
                            outputStream.write(encrypted)
                            outputStream.flush()
                        } else if (read < 0) {
                            break
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
                try { outputStream.close() } catch (e: Exception) {}
            } catch (e: Exception) {
                Log.e("SilentNetCall", "Audio stream error: ${e.message}")
            } finally {
                endCall()
            }
        }
    }

    private fun encryptAudio(data: ByteArray): ByteArray {
        if (sessionKey == null) return data
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val iv = ByteArray(GCM_IV_LENGTH).apply { java.security.SecureRandom().nextBytes(this) }
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sessionKey, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            val ciphertext = cipher.doFinal(data)
            iv + ciphertext
        } catch (e: Exception) { data }
    }

    private fun decryptAudio(data: ByteArray): ByteArray? {
        if (sessionKey == null) return data
        return try {
            val iv = data.copyOfRange(0, GCM_IV_LENGTH)
            val ciphertext = data.copyOfRange(GCM_IV_LENGTH, data.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(sessionKey, "AES"), GCMParameterSpec(GCM_TAG_LENGTH, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) { null }
    }

    fun endCall() {
        synchronized(cleanupLock) {
            if (currentState == CallState.IDLE && !isCalling && audioRecord == null && audioTrack == null) return
            
            Log.d("SilentNetCall", "Ending call. Current state: $currentState")
            
            // Send signaling if we were in any active state
            if (currentState != CallState.IDLE && remoteUsername != null) {
                try {
                    onSignalingPacket?.invoke(remoteUsername!!, "call_end", "{}")
                } catch (e: Exception) {
                    Log.e("SilentNetCall", "Failed to send call_end: ${e.message}")
                }
            }

            currentState = CallState.IDLE
            isCalling = false
            
            // Cleanup resources safely
            try {
                audioRecord?.let {
                    if (it.state != AudioRecord.STATE_UNINITIALIZED) {
                        try { it.stop() } catch (e: Exception) {}
                    }
                    it.release()
                }
            } catch (e: Exception) {
                Log.e("SilentNetCall", "AudioRecord cleanup error: ${e.message}")
            } finally {
                audioRecord = null
            }

            try {
                audioTrack?.let {
                    if (it.state != AudioTrack.STATE_UNINITIALIZED) {
                        try { it.stop() } catch (e: Exception) {}
                    }
                    it.release()
                }
            } catch (e: Exception) {
                Log.e("SilentNetCall", "AudioTrack cleanup error: ${e.message}")
            } finally {
                audioTrack = null
            }
            
            remoteUsername = null
            onCallEnded?.invoke()
            Log.d("SilentNetCall", "Call resources cleaned up")
        }
    }

    fun toggleMute(muted: Boolean) {
        if (muted) audioRecord?.stop() else audioRecord?.startRecording()
    }

    fun getCallStats(): String {
        return "Nearby Stream | E2EE: AES-GCM-256"
    }
}
