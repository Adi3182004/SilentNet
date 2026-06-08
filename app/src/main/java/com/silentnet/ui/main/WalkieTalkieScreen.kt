package com.silentnet.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentnet.app.AppGraph
import com.silentnet.data.WalkieChannelEntity
import com.silentnet.data.WalkieSegmentEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkieTalkieScreen(
    graph: AppGraph,
    onBack: () -> Unit
) {
    val channels by graph.walkieRepository.observeAllChannels().collectAsState(initial = emptyList())
    var selectedChannel by remember { mutableStateOf<WalkieChannelEntity?>(null) }
    val segments by if (selectedChannel != null) {
        graph.walkieRepository.observeSegmentsForChannel(selectedChannel!!.channelId).collectAsState(initial = emptyList())
    } else {
        remember { mutableStateOf(emptyList<WalkieSegmentEntity>()) }
    }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var isPressingPTT by remember { mutableStateOf(false) }
    var startTime by remember { mutableLongStateOf(0L) }
    var showCreateChannel by remember { mutableStateOf(false) }

    // Seed default channels
    LaunchedEffect(Unit) {
        if (channels.isEmpty()) {
            graph.walkieRepository.insertChannel(WalkieChannelEntity(channelId = "EMERGENCY_CH", name = "Emergency Broadcast", type = "emergency", isJoined = true))
            graph.walkieRepository.insertChannel(WalkieChannelEntity(channelId = "PUBLIC_CH", name = "Public Lobby", type = "public", isJoined = true))
        }
    }

    // Auto-playback for new segments in active channel
    var lastPlayedId by remember { mutableStateOf("") }
    LaunchedEffect(segments) {
        val latest = segments.lastOrNull()
        if (latest != null && latest.segmentId != lastPlayedId && latest.senderNodeId != graph.sessionManager.currentUsername()) {
            scope.launch {
                var attempts = 0
                while (attempts < 20) {
                    val file = File(latest.filePath)
                    if (file.exists()) {
                        graph.voiceNoteManager.play(latest.filePath) {}
                        lastPlayedId = latest.segmentId
                        break
                    }
                    delay(500)
                    attempts++
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(selectedChannel?.name ?: "Walkie Talkie", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { if (selectedChannel != null) selectedChannel = null else onBack() }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    if (selectedChannel == null) {
                        IconButton(onClick = { showCreateChannel = true }) {
                            Icon(Icons.Default.Add, null)
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (showCreateChannel) {
            CreateChannelDialog(
                onDismiss = { showCreateChannel = false },
                onCreate = { name, type ->
                    scope.launch {
                        val channel = WalkieChannelEntity(
                            channelId = "CH_${java.util.UUID.randomUUID()}",
                            name = name,
                            type = type,
                            isJoined = true
                        )
                        graph.walkieRepository.insertChannel(channel)
                        showCreateChannel = false
                    }
                }
            )
        }

        if (selectedChannel == null) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(channels) { channel ->
                    ListItem(
                        headlineContent = { Text(channel.name, fontWeight = FontWeight.Bold) },
                        supportingContent = { Text(channel.type.uppercase(), style = MaterialTheme.typography.labelSmall) },
                        leadingContent = { 
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(if (channel.type == "emergency") Color.Red else MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Radio, null, tint = Color.White)
                            }
                        },
                        modifier = Modifier.clickable { selectedChannel = channel }
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                    items(segments) { segment ->
                        SegmentRow(segment, graph)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (isPressingPTT) "TRANSMITTING..." else "HOLD TO TALK", fontWeight = FontWeight.Bold, color = if (isPressingPTT) Color.Red else MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(
                            modifier = Modifier
                                .size(if (isPressingPTT) 110.dp else 100.dp)
                                .clip(CircleShape)
                                .background(if (isPressingPTT) Color.Red else MaterialTheme.colorScheme.primary)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                scope.launch { snackbarHostState.showSnackbar("Audio permission required") }
                                                return@detectTapGestures
                                            }
                                            isPressingPTT = true
                                            startTime = System.currentTimeMillis()
                                            val recordingFile = graph.voiceNoteManager.startRecording()
                                            if (recordingFile == null) {
                                                isPressingPTT = false
                                                scope.launch { snackbarHostState.showSnackbar("Failed to start recording") }
                                                return@detectTapGestures
                                            }
                                            tryAwaitRelease()
                                            isPressingPTT = false
                                            val file = graph.voiceNoteManager.stopRecording()
                                            val duration = System.currentTimeMillis() - startTime
                                            if (file != null && duration > 500) {
                                                graph.transportManager.sendWalkieSegment(selectedChannel!!.channelId, file, duration)
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Mic, 
                                null, 
                                tint = Color.White, 
                                modifier = Modifier.size(if (isPressingPTT) 56.dp else 48.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateChannelDialog(onDismiss: () -> Unit, onCreate: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("team") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Channel") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Channel Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                Text("Type", style = MaterialTheme.typography.titleSmall)
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    FilterChip(
                        selected = type == "team",
                        onClick = { type = "team" },
                        label = { Text("Team") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = type == "emergency",
                        onClick = { type = "emergency" },
                        label = { Text("Emergency") }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onCreate(name, type) }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SegmentRow(segment: WalkieSegmentEntity, graph: AppGraph) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(Icons.Default.VolumeUp, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text("${segment.senderAlias}: ", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
        Text("${segment.duration / 1000}s segment", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { graph.voiceNoteManager.play(segment.filePath) {} }, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
        }
    }
}
