package com.silentnet.util

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class VoiceNoteManager(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var currentFile: File? = null

    fun startRecording(): File? {
        val dir = File(context.filesDir, "silentnet/voicenotes").apply { mkdirs() }
        currentFile = File(dir, "VN_${System.currentTimeMillis()}.m4a")
        
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            MediaRecorder()
        }.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(currentFile!!.absolutePath)
            try {
                prepare()
                start()
                Log.d("VoiceNote", "Recording started: ${currentFile!!.absolutePath}")
            } catch (e: Exception) {
                Log.e("VoiceNote", "Recording failed to start", e)
                return null
            }
        }
        return currentFile
    }

    fun stopRecording(): File? {
        try {
            recorder?.apply {
                stop()
                release()
            }
            Log.d("VoiceNote", "Recording stopped")
        } catch (e: Exception) {
            Log.e("VoiceNote", "Stop recording failed", e)
        }
        recorder = null
        return currentFile
    }

    fun play(path: String, onComplete: () -> Unit) {
        val file = File(path)
        if (!file.exists()) {
            Log.e("VoiceNote", "File does not exist: $path")
            return
        }
        
        player?.release()
        player = MediaPlayer().apply {
            try {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { 
                    onComplete()
                    release()
                }
                Log.d("VoiceNote", "Playback started: $path")
            } catch (e: Exception) {
                Log.e("VoiceNote", "Playback failed", e)
            }
        }
    }

    fun stopPlayback() {
        player?.apply {
            try {
                if (isPlaying) stop()
                release()
            } catch (_: Exception) {}
        }
        player = null
    }
}
