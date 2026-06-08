package com.silentnet.ui.main

import androidx.activity.compose.BackHandler
import java.util.UUID
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentnet.app.AppGraph
import com.silentnet.data.GroupEntity
import com.silentnet.data.MessageEntity
import kotlinx.coroutines.launch

@Composable
fun GroupChatScreen(
    graph: AppGraph,
    currentUsername: String,
    currentFullName: String,
    group: GroupEntity,
    onBack: () -> Unit,
    onShowInfo: () -> Unit
) {
    val messages by graph.groupRepository.observeGroupMessages(group.groupId).collectAsState(initial = emptyList())
    val currentGroupState by graph.groupRepository.observeGroupById(group.groupId).collectAsState(initial = group)
    val isStillJoined = remember(currentGroupState) { currentGroupState?.isJoined == true }
    
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(isStillJoined) {
        if (!isStillJoined) {
            onBack()
        }
    }

    BackHandler {
        onBack()
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(group.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Group Chat",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                IconButton(onClick = onShowInfo) {
                    Icon(Icons.Default.Info, null, tint = Color.White)
                }
            }
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Group Message") },
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                FloatingActionButton(
                    onClick = {
                        scope.launch {
                            val text = input.trim()
                            if (text.isEmpty()) return@launch

                            val outgoing = MessageEntity(
                                contactId = -1, // Not used for group outgoing
                                senderLabel = currentFullName,
                                body = text,
                                isOutgoing = true,
                                deliveryStatus = 0,
                                groupId = group.groupId
                            )
                            // We need to insert this into messageRepository to get an ID
                            val msgId = graph.messageRepository.insert(outgoing)
                            val savedMsg = outgoing.copy(id = msgId)
                            
                            graph.transportManager.sendGroupMessage(group.groupId, savedMsg)
                            input = ""
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Send, null)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "Messages are encrypted for all group members.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(messages.size) { index ->
                val message = messages[index]
                GroupMessageRow(message, graph)
            }
            item {
                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }
}

@Composable
fun GroupMessageRow(message: MessageEntity, graph: AppGraph) {
    val align = if (message.isOutgoing) Alignment.End else Alignment.Start
    val bubbleColor = if (message.isOutgoing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val onBubbleColor = if (message.isOutgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = align
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.sizeIn(maxWidth = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!message.isOutgoing) {
                    Text(
                        text = message.senderLabel,
                        color = onBubbleColor.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (message.attachmentMime?.startsWith("audio/") == true && message.attachmentPath != null) {
                    VoiceNotePlayer(
                        path = message.attachmentPath!!,
                        voiceNoteManager = graph.voiceNoteManager,
                        color = onBubbleColor,
                        isEncrypted = message.attachmentMime == "audio/encrypted",
                        onDecrypt = { path, callback ->
                            scope.launch {
                                val group = graph.groupRepository.findGroupById(message.groupId ?: "")
                                val keyId = message.groupKeyId ?: group?.currentKeyId
                                val groupKeyEntity = keyId?.let { graph.groupRepository.findKey(message.groupId ?: "", it) }
                                
                                val myPrivKey = graph.identityManager.getPrivateKey()
                                if (groupKeyEntity != null && myPrivKey != null) {
                                    val decryptedGroupKeyBase64 = com.silentnet.security.CryptographyManager.decryptPayload(groupKeyEntity.encryptedKey, myPrivKey)
                                    if (decryptedGroupKeyBase64 != null) {
                                        val groupKeyBytes = android.util.Base64.decode(decryptedGroupKeyBase64, android.util.Base64.DEFAULT)
                                        val inputFile = java.io.File(path)
                                        val outputFile = java.io.File(context.cacheDir, "GDEC_${inputFile.name}.m4a")
                                        val success = com.silentnet.security.CryptographyManager.decryptFileWithGroupKey(
                                            inputFile, outputFile, groupKeyBytes
                                        )
                                        callback(if (success) outputFile.absolutePath else null)
                                    } else callback(null)
                                } else callback(null)
                            }
                        }
                    )
                }
                Text(
                    text = message.body ?: "",
                    color = onBubbleColor,
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(message.timestamp),
                        color = onBubbleColor.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (message.isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (message.deliveryStatus >= 1) Icons.Default.DoneAll else Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = onBubbleColor.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
