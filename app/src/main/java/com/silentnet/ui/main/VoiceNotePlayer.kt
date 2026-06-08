package com.silentnet.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.silentnet.util.VoiceNoteManager

@Composable
fun VoiceNotePlayer(
    path: String,
    voiceNoteManager: VoiceNoteManager,
    color: Color = MaterialTheme.colorScheme.primary,
    isEncrypted: Boolean = false,
    onDecrypt: (String, (String?) -> Unit) -> Unit = { _, _ -> }
) {
    var isPlaying by remember { mutableStateOf(false) }
    var decryptedPath by remember { mutableStateOf<String?>(null) }
    var isDecrypting by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    voiceNoteManager.stopPlayback()
                    isPlaying = false
                } else {
                    if (isEncrypted && decryptedPath == null) {
                        isDecrypting = true
                        onDecrypt(path) { result ->
                            isDecrypting = false
                            decryptedPath = result
                            if (result != null) {
                                voiceNoteManager.play(result) {
                                    isPlaying = false
                                }
                                isPlaying = true
                            }
                        }
                    } else {
                        voiceNoteManager.play(decryptedPath ?: path) {
                            isPlaying = false
                        }
                        isPlaying = true
                    }
                }
            },
            enabled = !isDecrypting
        ) {
            if (isDecrypting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = color)
            } else {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = color)
            }
        }
        Text(
            text = if (isDecrypting) "Decrypting..." else if (isEncrypted && decryptedPath == null) "Encrypted Voice Note" else "Voice Note",
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}
