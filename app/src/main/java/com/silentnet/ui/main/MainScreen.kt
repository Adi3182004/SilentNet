package com.silentnet.ui.main

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentnet.app.AppGraph
import com.silentnet.data.ContactEntity
import com.silentnet.data.GroupEntity
import com.silentnet.data.MessageEntity
import com.silentnet.util.AttachmentDraft
import com.silentnet.util.FileStorage
import com.silentnet.util.ShareHelper
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

private enum class HomeTab {
    Chats, Network, Emergency, Bulletin, Media, Settings
}

@Composable
fun EmergencyPopup(
    message: MessageEntity,
    onAcknowledge: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, "Emergency", tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "EMERGENCY ALERT",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        },
        text = {
            Column {
                Text("Sender: ${message.senderLabel}", fontWeight = FontWeight.Bold)
                Text(
                    "Time: ${
                        java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                            .format(message.timestamp)
                    }"
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    message.emergencyTitle ?: "Emergency Message",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(message.body ?: "", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Priority: HIGH",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAcknowledge,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Acknowledge")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    graph: AppGraph,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val session = graph.sessionManager
    val currentUsername = session.currentUsername() ?: return
    val currentFullName = session.currentFullName() ?: currentUsername
    val contacts by graph.contactRepository.observeContacts(currentUsername)
        .collectAsState(initial = emptyList())
    val groups by graph.groupRepository.observeAllGroups().collectAsState(initial = emptyList())
    val allMessages by graph.messageRepository.observeAllMessages(currentUsername)
        .collectAsState(initial = emptyList())
    val attachedMessages by graph.messageRepository.observeAttachedMessages(currentUsername)
        .collectAsState(initial = emptyList())

    // Tab indices: 0:Chats, 1:Groups, 2:Network, 3:Recovery, 4:Security, 5:SOS, 6:Media, 7:Settings, 8:LostLink
    var tab by rememberSaveable { mutableStateOf(0) }
    var selectedContact by remember { mutableStateOf<ContactEntity?>(null) }
    var selectedGroup by remember { mutableStateOf<GroupEntity?>(null) }
    var showGroupInfo by remember { mutableStateOf(false) }
    var showUserPicker by remember { mutableStateOf(false) }
    var showGroupCreator by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(currentUsername) {
        graph.contactRepository.seedDemoContacts(currentUsername)
    }

    BackHandler(enabled = selectedContact != null || selectedGroup != null || drawerState.isOpen) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else if (showGroupInfo) {
            showGroupInfo = false
        } else if (selectedGroup != null) {
            selectedGroup = null
        } else {
            selectedContact = null
        }
    }

    val callState by graph.callManager.callState.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (selectedContact != null) {
            ChatScreen(
                graph = graph,
                currentUsername = currentUsername,
                currentFullName = currentFullName,
                contact = selectedContact!!,
                onBack = { selectedContact = null },
                onNotify = { scope.launch { snackbar.showSnackbar(it) } }
            )
        } else if (selectedGroup != null) {
            if (showGroupInfo) {
                GroupInfoScreen(
                    graph = graph,
                    group = selectedGroup!!,
                    onBack = { showGroupInfo = false }
                )
            } else {
                GroupChatScreen(
                    graph = graph,
                    currentUsername = currentUsername,
                    currentFullName = currentFullName,
                    group = selectedGroup!!,
                    onBack = { selectedGroup = null },
                    onShowInfo = { showGroupInfo = true }
                )
            }
        } else {
            val previewMap = remember(allMessages) {
                allMessages.groupBy { it.contactId }
                    .mapValues { entry -> entry.value.firstOrNull() }
            }

            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "SilentNet Suite",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalDivider()

                        // Quick Actions
                        Text(
                            "Quick Actions",
                            modifier = Modifier.padding(16.dp, 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        NavigationDrawerItem(
                            label = { Text("Emergency Broadcast") },
                            selected = tab == 5,
                            onClick = { tab = 5; scope.launch { drawerState.close() } },
                            icon = { Icon(Icons.Default.Campaign, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Walkie Talkie") },
                            selected = tab == 9,
                            onClick = { tab = 9; scope.launch { drawerState.close() } },
                            icon = { Icon(Icons.Default.Mic, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Network Dashboard") },
                            selected = tab == 2,
                            onClick = { tab = 2; scope.launch { drawerState.close() } },
                            icon = { Icon(Icons.Default.Dashboard, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Community Recovery") },
                            selected = tab == 3,
                            onClick = { tab = 3; scope.launch { drawerState.close() } },
                            icon = { Icon(Icons.Default.Healing, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("LostLink V2") },
                            selected = tab == 11,
                            onClick = { tab = 11; scope.launch { drawerState.close() } },
                            icon = { Icon(Icons.Default.Link, null) }
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        // Advanced Modules
                        Text(
                            "Communication",
                            modifier = Modifier.padding(16.dp, 8.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                        NavigationDrawerItem(
                            label = { Text("Walkie Talkie") },
                            selected = tab == 9,
                            onClick = { tab = 9; scope.launch { drawerState.close() } },
                            icon = { Icon(Icons.Default.Radio, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Direct Voice/Video") },
                            selected = tab == 10,
                            onClick = { tab = 10; scope.launch { drawerState.close() } },
                            icon = { Icon(Icons.Default.Call, null) }
                        )
                        NavigationDrawerItem(
                            label = { Text("Media Vault") },
                            selected = tab == 6,
                            onClick = { tab = 6; scope.launch { drawerState.close() } },
                            icon = { Icon(Icons.Default.PhotoLibrary, null) }
                        )

                        Text(
                            "Security & System",
                            modifier = Modifier.padding(16.dp, 8.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                        NavigationDrawerItem(
                            label = { Text("Settings") },
                            selected = tab == 7,
                            onClick = { tab = 7; scope.launch { drawerState.close() } },
                            icon = { Icon(Icons.Default.Settings, null) }
                        )

                        Spacer(Modifier.weight(1f))
                        NavigationDrawerItem(
                            label = { Text("Logout") },
                            selected = false,
                            onClick = { onLogout() },
                            icon = { Icon(Icons.Default.ExitToApp, null) }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            ) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbar) },
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "SilentNet Suite",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = when (tab) {
                                            0 -> "Private Chats"
                                            1 -> "Group Chats"
                                            2 -> "Mesh Network"
                                            3 -> "Community Recovery"
                                            4 -> "Security Hardening"
                                            5 -> "Emergency System"
                                            6 -> "Media Vault"
                                            7 -> "Settings"
                                            9 -> "Walkie Talkie"
                                            10 -> "Direct Call"
                                            11 -> "LostLink V2"
                                            else -> "SilentNet"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, null)
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = tab == 0,
                                onClick = { tab = 0 },
                                icon = { Icon(Icons.Default.ChatBubbleOutline, null) },
                                label = { Text("Chats", fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = tab == 1,
                                onClick = { tab = 1 },
                                icon = { Icon(Icons.Default.Groups, null) },
                                label = { Text("Groups", fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = tab == 2,
                                onClick = { tab = 2 },
                                icon = { Icon(Icons.Default.Hub, null) },
                                label = { Text("Network", fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = tab == 3,
                                onClick = { tab = 3 },
                                icon = { Icon(Icons.Default.Healing, null) },
                                label = { Text("Recovery", fontSize = 10.sp) }
                            )
                            NavigationBarItem(
                                selected = tab == 4,
                                onClick = { tab = 4 },
                                icon = { Icon(Icons.Default.Security, null) },
                                label = { Text("Security", fontSize = 10.sp) }
                            )
                        }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        when (tab) {
                            0 -> PrivateChatsTab(
                                graph = graph,
                                currentUsername = currentUsername,
                                contacts = contacts,
                                previews = previewMap,
                                onContactClick = { selectedContact = it },
                                onNewChat = { showUserPicker = true }
                            )

                            1 -> GroupsTab(
                                graph = graph,
                                groups = groups,
                                onGroupClick = { selectedGroup = it },
                                onNewGroup = { showGroupCreator = true }
                            )

                            2 -> com.silentnet.ui.dashboard.NetworkDashboardScreen(graph)
                            3 -> com.silentnet.ui.recovery.RecoveryScreen(graph)
                            4 -> com.silentnet.ui.security.SecurityDashboardScreen(graph)
                            5 -> EmergencyScreen(graph)
                            6 -> MediaTab(
                                currentUsername = currentUsername,
                                attachedMessages = attachedMessages,
                                onOpen = { path, mime -> openAttachment(context, path, mime) },
                                onShare = { path, mime -> shareAttachment(context, path, mime) }
                            )

                            7 -> SettingsTab(
                                graph = graph,
                                currentUsername = currentUsername,
                                currentFullName = currentFullName,
                                totalContacts = contacts.size,
                                totalMessages = allMessages.size,
                                onLogout = onLogout,
                                onClearData = {
                                    scope.launch {
                                        graph.contactRepository.clearOwnerData(currentUsername)
                                        graph.messageRepository.clearOwnerData(currentUsername)
                                        FileStorage.deleteAllAttachments(context)
                                        graph.sessionManager.clear()
                                        onLogout()
                                    }
                                },
                                onShareBackup = {
                                    scope.launch {
                                        val backupFile = createBackupFile(
                                            context,
                                            currentUsername,
                                            currentFullName,
                                            contacts,
                                            allMessages
                                        )
                                        ShareHelper.shareFile(
                                            context,
                                            backupFile,
                                            "text/plain",
                                            "Share backup"
                                        )
                                    }
                                }
                            )

                            9 -> WalkieTalkieScreen(graph, onBack = { tab = 0 })
                            10 -> DirectCallTab(graph)
                            11 -> com.silentnet.lostlink.ui.LostLinkV2Dashboard(
                                bridge = graph.lostLinkBridge,
                                onBack = { tab = 0 }
                            )
                        }
                    }
                }
            }

            if (showUserPicker) {
                UserPicker(
                    graph = graph,
                    onDismiss = { showUserPicker = false },
                    onSelect = { u, f ->
                        scope.launch {
                            val contact =
                                graph.contactRepository.findOrCreateContact(currentUsername, u, f)
                            selectedContact = contact
                            showUserPicker = false
                        }
                    }
                )
            }

            if (showGroupCreator) {
                GroupCreateDialog(
                    graph = graph,
                    onDismiss = { showGroupCreator = false },
                    onCreate = { name, desc ->
                        scope.launch {
                            val myNodeId = graph.sessionManager.currentUsername() ?: ""
                            val myAlias = graph.sessionManager.currentFullName() ?: myNodeId
                            val groupId =
                                graph.groupRepository.createGroup(name, desc, myNodeId, myAlias)
                            graph.transportManager.rotateGroupKey(groupId)
                            showGroupCreator = false
                        }
                    }
                )
            }

        }

        if (callState != com.silentnet.transport.CallState.IDLE) {
            CallOverlay(
                state = callState,
                remoteFullName = graph.callManager.remoteFullName ?: "Unknown",
                onAccept = { graph.callManager.acceptCall() },
                onDecline = { graph.callManager.declineCall() },
                onEnd = { graph.callManager.endCall() }
            )
        }
    }
}

@Composable
private fun PrivateChatsTab(
    graph: AppGraph,
    currentUsername: String,
    contacts: List<ContactEntity>,
    previews: Map<Long, MessageEntity?>,
    onContactClick: (ContactEntity) -> Unit,
    onNewChat: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (contacts.isEmpty()) {
            EmptyState("No private chats", onNewChat, "New Chat")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(contacts.size) { index ->
                    val contact = contacts[index]
                    val preview = previews[contact.id]
                    ContactCard(
                        graph = graph,
                        contact = contact,
                        preview = preview,
                        onClick = { onContactClick(contact) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onNewChat,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Add, null)
        }
    }
}

@Composable
private fun GroupsTab(
    graph: AppGraph,
    groups: List<GroupEntity>,
    onGroupClick: (GroupEntity) -> Unit,
    onNewGroup: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (groups.isEmpty()) {
            EmptyState("No group chats", onNewGroup, "Create Group")
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groups.size) { index ->
                    val group = groups[index]
                    GroupCard(
                        graph = graph,
                        group = group,
                        onClick = { onGroupClick(group) }
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onNewGroup,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Icon(Icons.Default.Groups, null)
        }
    }
}

@Composable
private fun EmptyState(text: String, onClick: () -> Unit, buttonText: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        ElevatedButton(onClick = onClick) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(buttonText)
        }
    }
}

@Composable
private fun GroupCard(
    graph: AppGraph,
    group: GroupEntity,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { showMenu = true }
                )
            }
    ) {
        Box {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = group.name.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.secondary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = group.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (group.isPinned) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.PushPin,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Text(
                        text = group.description ?: "Group Chat",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (group.isPinned) "Unpin Group" else "Pin Group") },
                    onClick = {
                        scope.launch {
                            graph.groupRepository.updatePinnedStatus(
                                group.groupId,
                                !group.isPinned
                            )
                        }
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.PushPin, null) }
                )
                DropdownMenuItem(
                    text = { Text("Leave Group") },
                    onClick = {
                        scope.launch { graph.transportManager.sendGroupLeavePacket(group.groupId) }
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.ExitToApp, null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete Local History") },
                    onClick = {
                        scope.launch { graph.messageRepository.deleteByGroupId(group.groupId) }
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.DeleteSweep, null) }
                )
            }
        }
    }
}

@Composable
fun GroupCreateDialog(
    graph: AppGraph,
    onDismiss: () -> Unit,
    onCreate: (String, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Group") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Group Name") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onCreate(
                        name,
                        desc.takeIf { it.isNotBlank() })
                },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun NetworkScreen(graph: AppGraph) {
    val peers by graph.transportManager.peers.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Mesh Topology",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            NetworkGraph(peers = peers)
        }

        Text(
            "Transport Status",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                "Bluetooth LE",
                "Advertising",
                Icons.Default.WifiTethering,
                Modifier.weight(1f),
                MaterialTheme.colorScheme.primary
            )
            StatusCard(
                "Wi-Fi Direct",
                "Discovering",
                Icons.Default.Wifi,
                Modifier.weight(1f),
                MaterialTheme.colorScheme.secondary
            )
        }

        Text(
            "Packet Statistics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatRow("Nearby Nodes", peers.size.toString(), Icons.Default.Hub)
                StatRow("Active Relays", "2", Icons.Default.Router)
                StatRow("Signal Quality", "Excellent", Icons.Default.SignalCellularAlt)
                StatRow("Mesh Hops", "1", Icons.Default.Info)
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun NetworkGraph(peers: List<com.silentnet.transport.NearbyPeer>) {
    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale *= zoom
                    offset += pan
                }
            }
    ) {
        val centerX = size.width / 2 + offset.x
        val centerY = size.height / 2 + offset.y
        val radius = 100f * scale

        // Draw current device (center)
        drawCircle(
            color = primaryColor,
            radius = 25f * scale,
            center = Offset(centerX, centerY)
        )

        peers.forEachIndexed { index, peer ->
            val angle = (360f / peers.size) * index
            val peerX =
                centerX + radius * 1.5f * cos(Math.toRadians(angle.toDouble())).toFloat()
            val peerY =
                centerY + radius * 1.5f * sin(Math.toRadians(angle.toDouble())).toFloat()

            // Draw edge
            drawLine(
                color = primaryColor.copy(alpha = 0.3f),
                start = Offset(centerX, centerY),
                end = Offset(peerX, peerY),
                strokeWidth = 2f * scale
            )

            // Draw peer node
            drawCircle(
                color = secondaryColor,
                radius = 18f * scale,
                center = Offset(peerX, peerY)
            )
        }
    }
}

@Composable
private fun StatusCard(
    label: String,
    status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmergencyScreen(graph: AppGraph) {
    val session = graph.sessionManager
    val currentUsername = session.currentUsername() ?: ""
    val isAdmin = session.isAdmin()
    val allMessages by graph.messageRepository.observeAllMessages(currentUsername)
        .collectAsState(initial = emptyList())
    val emergencyMessages = remember(allMessages) { allMessages.filter { it.isEmergency } }
    val userCount by graph.database.userDao().countFlow().collectAsState(initial = 0)

    var showCreate by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isAdmin) {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Admin Command Center",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StatItem("Nodes", userCount.toString())
                        StatItem(
                            "Sent",
                            emergencyMessages.filter { it.isOutgoing }.size.toString()
                        )
                        StatItem(
                            "Ack'd",
                            emergencyMessages.filter { it.isOutgoing && it.deliveryStatus >= 2 }.size.toString()
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showCreate = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Campaign, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Broadcast Emergency Alert")
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Security,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Emergency Response Active", fontWeight = FontWeight.Bold)
                    Text(
                        "Monitoring for authenticated admin broadcasts.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (emergencyMessages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Inbox,
                        null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    )
                    Text(
                        "No emergency logs",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(emergencyMessages.size) { index ->
                    EmergencyCard(emergencyMessages[index], onAcknowledge = {
                        graph.transportManager.acknowledgeEmergency(emergencyMessages[index].id)
                    })
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("New Emergency Broadcast") },
            text = {
                Column {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Alert Title") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Fire Drill, Evacuation") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Instructions") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = { Text("Describe the situation and required actions...") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Note: This will trigger an audio alarm on all connected devices.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank() && body.isNotBlank()) {
                            graph.transportManager.sendEmergencyBroadcast(title, body)
                            showCreate = false
                            title = ""
                            body = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Send Broadcast") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EmergencyCard(message: MessageEntity, onAcknowledge: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (message.isAcknowledged) MaterialTheme.colorScheme.surfaceVariant else Color(
                0xFFFFEBEE
            )
        ),
        shape = RoundedCornerShape(20.dp),
        border = if (!message.isAcknowledged) BorderStroke(2.dp, Color.Red) else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Warning,
                    null,
                    tint = Color.Red,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    message.emergencyTitle ?: "Emergency",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                        .format(message.timestamp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(message.body ?: "", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        "SENDER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        message.senderLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (!message.isOutgoing && !message.isAcknowledged) {
                    Button(
                        onClick = onAcknowledge,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.height(36.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Acknowledge", fontSize = 12.sp)
                    }
                } else if (message.isAcknowledged) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CheckCircle,
                            null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Acknowledged",
                            color = Color(0xFF2E7D32),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (message.isOutgoing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon =
                            if (message.deliveryStatus >= 2) Icons.Default.DoneAll else Icons.Default.Done
                        Icon(
                            icon,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (message.deliveryStatus >= 2) "Delivered" else "Sent",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            if (message.emergencySignature != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(modifier = Modifier.alpha(0.1f))
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        null,
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFF2E7D32)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Digitally Verified Admin Broadcast",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ContactCard(
    graph: AppGraph,
    contact: ContactEntity,
    preview: MessageEntity?,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val lastText = preview?.body ?: preview?.attachmentName ?: "No messages yet"
    val badgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { showMenu = true }
                )
            }
    ) {
        Box {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(badgeColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = contact.alias.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = contact.alias,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (contact.isPinned) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.PushPin,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(
                        text = lastText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (preview != null) {
                    Icon(
                        Icons.Default.Info,
                        null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text(if (contact.isPinned) "Unpin Chat" else "Pin Chat") },
                    onClick = {
                        scope.launch {
                            graph.contactRepository.updatePinnedStatus(
                                contact.id,
                                !contact.isPinned
                            )
                        }
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.PushPin, null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete Chat") },
                    onClick = {
                        scope.launch { graph.messageRepository.deleteByContactId(contact.id) }
                        showMenu = false
                    },
                    leadingIcon = { Icon(Icons.Default.Delete, null) }
                )
            }
        }
    }
}

@Composable
private fun ChatScreen(
    graph: AppGraph,
    currentUsername: String,
    currentFullName: String,
    contact: ContactEntity,
    onBack: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val messages by graph.messageRepository.observeMessages(contact.id)
        .collectAsState(initial = emptyList())
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var attachment by remember { mutableStateOf<AttachmentDraft?>(null) }
    var isViewOnceSelected by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordStartTime by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()

    val peers by graph.transportManager.peers.collectAsState()
    val peerStatus =
        remember(peers) { graph.transportManager.getPeerStatus(contact.contactUsername) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) {
                attachment = FileStorage.copyAttachment(context, uri)
                onNotify("Attachment added")
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
                    Text(
                        contact.alias.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(contact.alias, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(
                        "$peerStatus - ${contact.contactUsername}",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(
                    Icons.Default.SmartToy,
                    null,
                    tint = if (contact.autoReplyEnabled) Color(0xFF25D6FF) else Color.White.copy(
                        alpha = 0.3f
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { graph.callManager.initiateCall(contact.contactUsername, contact.alias) }) {
                    Icon(Icons.Default.Call, null, tint = Color.White)
                }
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(12.dp)
            ) {
                attachment?.let {
                    AttachmentPill(
                        text = it.name,
                        isViewOnce = isViewOnceSelected,
                        onViewOnceToggle = { isViewOnceSelected = !isViewOnceSelected },
                        onClear = {
                            attachment = null
                            isViewOnceSelected = false
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { launcher.launch(arrayOf("*/*")) }) {
                        Icon(
                            Icons.Default.AttachFile,
                            null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                val file = graph.voiceNoteManager.stopRecording()
                                if (file != null) {
                                    scope.launch {
                                        val encryptedFile =
                                            File(file.parent, "ENC_${file.name}")
                                        val success =
                                            com.silentnet.security.CryptographyManager.encryptFile(
                                                file, encryptedFile, contact.publicKey ?: ""
                                            )
                                        if (success) {
                                            val msg = MessageEntity(
                                                contactId = contact.id,
                                                senderLabel = currentFullName,
                                                body = "[Voice Note]",
                                                attachmentPath = encryptedFile.absolutePath,
                                                attachmentName = file.name,
                                                attachmentMime = "audio/encrypted",
                                                isOutgoing = true,
                                                deliveryStatus = 0
                                            )
                                            val id = graph.messageRepository.insert(msg)
                                            val savedMsg = msg.copy(id = id)

                                            // Check if peer is nearby for direct transfer
                                            val nearbyPeers = graph.transportManager.peers.value
                                            val isNearby =
                                                nearbyPeers.any { it.username == contact.contactUsername }

                                            if (isNearby) {
                                                graph.transportManager.sendMessage(
                                                    contact.contactUsername,
                                                    savedMsg
                                                )
                                            } else {
                                                val duration =
                                                    (System.currentTimeMillis() - recordStartTime) / 1000
                                                graph.transportManager.sendVoiceNote(
                                                    contact.contactUsername,
                                                    null,
                                                    encryptedFile,
                                                    duration
                                                )
                                                graph.messageRepository.updateDeliveryStatus(
                                                    id,
                                                    1
                                                )
                                            }
                                        }
                                    }
                                }
                                isRecording = false
                            } else {
                                recordStartTime = System.currentTimeMillis()
                                graph.voiceNoteManager.startRecording()
                                isRecording = true
                            }
                        }
                    ) {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            null,
                            tint = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary
                        )
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                val text = input.trim()
                                if (text.isEmpty() && attachment == null) {
                                    return@launch
                                }

                                val priority =
                                    graph.pythonBridge.priorityScore(text, attachment != null)
                                val outgoing = MessageEntity(
                                    contactId = contact.id,
                                    senderLabel = currentFullName,
                                    body = text.ifBlank { null },
                                    attachmentPath = attachment?.path,
                                    attachmentName = attachment?.name,
                                    attachmentMime = attachment?.mime,
                                    isOutgoing = true,
                                    deliveryStatus = 0, // Pending
                                    priority = priority,
                                    isViewOnce = isViewOnceSelected
                                )
                                val msgId = graph.messageRepository.insert(outgoing)
                                val savedMsg = outgoing.copy(id = msgId)

                                graph.transportManager.sendMessage(
                                    contact.contactUsername,
                                    savedMsg
                                )

                                input = ""
                                attachment = null
                                isViewOnceSelected = false
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
                    text = "Messages are delivered over Bluetooth or Wi-Fi Direct.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            items(messages.size) { index ->
                val message = messages[index]
                if (!message.isDeleted || !message.isOutgoing) { // Only show deleted if it's incoming or specifically handled
                    MessageRow(
                        graph = graph,
                        message = message,
                        onOpen = { path, mime ->
                            openAttachment(context, path, mime)
                            if (message.isViewOnce && !message.isOutgoing) {
                                graph.transportManager.consumeViewOnce(message.id)
                            }
                        },
                        onShare = { path, mime -> shareAttachment(context, path, mime) },
                        onDeleteMe = {
                            scope.launch { graph.messageRepository.deleteById(message.id) }
                        },
                        onDeleteEveryone = {
                            graph.transportManager.deleteForEveryone(
                                contact.contactUsername,
                                message.id
                            )
                        }
                    )
                }
            }
            item {
                Spacer(modifier = Modifier.height(90.dp))
            }
        }
    }
}

@Composable
private fun AttachmentPill(
    text: String,
    isViewOnce: Boolean,
    onViewOnceToggle: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        IconButton(onClick = onViewOnceToggle) {
            Icon(
                Icons.Default.LooksOne,
                "View Once",
                tint = if (isViewOnce) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.4f
                )
            )
        }

        IconButton(onClick = onClear) {
            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun MessageRow(
    graph: AppGraph,
    message: MessageEntity,
    onOpen: (String, String?) -> Unit,
    onShare: (String, String?) -> Unit,
    onDeleteMe: () -> Unit,
    onDeleteEveryone: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    val bubbleColor = if (message.isDeleted) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    } else if (message.isOutgoing) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val onBubbleColor = if (message.isDeleted) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    } else if (message.isOutgoing) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val align = if (message.isOutgoing) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { showMenu = true }
                )
            },
        horizontalAlignment = align
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.sizeIn(maxWidth = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (message.isDeleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Block,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = onBubbleColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (message.isOutgoing) "You deleted this message" else "This message was deleted",
                            color = onBubbleColor,
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic
                        )
                    }
                } else {
                    if (message.senderLabel.isNotBlank() && !message.isOutgoing) {
                        Text(
                            text = message.senderLabel,
                            color = onBubbleColor.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    message.body?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = onBubbleColor,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = if (message.senderLabel.isNotBlank() && !message.isOutgoing) 4.dp else 0.dp)
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
                                    val inputFile = File(path)
                                    val outputFile = File(
                                        context.cacheDir,
                                        "DEC_${inputFile.name}.m4a"
                                    )
                                    val privateKey = graph.identityManager.getPrivateKey()
                                    if (privateKey != null) {
                                        val success =
                                            com.silentnet.security.CryptographyManager.decryptFile(
                                                inputFile, outputFile, privateKey
                                            )
                                        callback(if (success) outputFile.absolutePath else null)
                                    } else {
                                        callback(null)
                                    }
                                }
                            }
                        )
                    }
                    if (message.attachmentPath != null || (message.isViewOnce && !message.isOutgoing)) {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (message.isViewOnce && message.isConsumed) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                Icon(
                                    Icons.Default.LooksOne,
                                    null,
                                    tint = onBubbleColor.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "View Once Media (Opened)",
                                    color = onBubbleColor.copy(alpha = 0.5f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else if (message.isViewOnce && !message.isOutgoing && message.attachmentPath == null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = onBubbleColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Downloading View Once Media...",
                                    color = onBubbleColor,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            AttachmentPreview(
                                path = message.attachmentPath ?: "",
                                name = if (message.isViewOnce) "View Once Media" else (message.attachmentName
                                    ?: "attachment"),
                                mime = message.attachmentMime,
                                isViewOnce = message.isViewOnce,
                                isOutgoing = message.isOutgoing,
                                onOpen = {
                                    message.attachmentPath?.let {
                                        onOpen(
                                            it,
                                            message.attachmentMime
                                        )
                                    }
                                },
                                onShare = {
                                    message.attachmentPath?.let {
                                        onShare(
                                            it,
                                            message.attachmentMime
                                        )
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = priorityLabel(message.priority),
                            color = onBubbleColor.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (message.isOutgoing) {
                            val icon = when (message.deliveryStatus) {
                                0 -> Icons.Default.Schedule
                                1 -> Icons.Default.Done
                                2 -> Icons.Default.DoneAll
                                else -> Icons.Default.DoneAll
                            }
                            val tint =
                                if (message.deliveryStatus >= 3) Color(0xFF22C55E) else onBubbleColor.copy(
                                    alpha = 0.6f
                                )
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = tint
                            )
                        }
                    }
                }
            }
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = { Text("Delete for Me") },
                onClick = {
                    onDeleteMe()
                    showMenu = false
                }
            )
            if (message.isOutgoing && !message.isDeleted) {
                DropdownMenuItem(
                    text = { Text("Delete for Everyone") },
                    onClick = {
                        onDeleteEveryone()
                        showMenu = false
                    }
                )
            }
        }
    }
}

@Composable
private fun AttachmentPreview(
    path: String,
    name: String,
    mime: String?,
    isViewOnce: Boolean,
    isOutgoing: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    val isImage = mime?.startsWith("image/") == true
    val isVisible = !isViewOnce || isOutgoing

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(
                alpha = 0.5f
            )
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            if (isImage && isVisible && path.isNotBlank()) {
                val bitmap = remember(path) {
                    try {
                        BitmapFactory.decodeFile(path)?.asImageBitmap()
                    } catch (_: Exception) {
                        null
                    }
                }
                bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
            } else if (isViewOnce && !isOutgoing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.LooksOne, null, modifier = Modifier.size(32.dp))
                        Text("View Once Media", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = if (isImage && isVisible) 10.dp else 4.dp)
            )
            Text(
                text = mime ?: "file",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall
            )
            Row(modifier = Modifier.padding(top = 8.dp)) {
                if (path.isNotBlank()) {
                    TextButton(onClick = onOpen) { Text(if (isViewOnce && !isOutgoing) "View" else "Open") }
                    if (!isViewOnce) {
                        TextButton(onClick = onShare) { Text("Share") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTab(
    currentUsername: String,
    attachedMessages: List<MessageEntity>,
    onOpen: (String, String?) -> Unit,
    onShare: (String, String?) -> Unit
) {
    var subTab by remember { mutableStateOf(0) }
    val titles = listOf("Images", "Videos", "Docs", "Audio")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = subTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[subTab]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            titles.forEachIndexed { index, title ->
                Tab(
                    selected = subTab == index,
                    onClick = { subTab = index },
                    text = { Text(title, style = MaterialTheme.typography.labelLarge) }
                )
            }
        }

        val filtered = remember(attachedMessages, subTab) {
            attachedMessages.filter { msg ->
                val mime = msg.attachmentMime ?: ""
                when (subTab) {
                    0 -> mime.startsWith("image/")
                    1 -> mime.startsWith("video/")
                    2 -> !mime.startsWith("image/") && !mime.startsWith("video/") && !mime.startsWith(
                        "audio/"
                    )

                    3 -> mime.startsWith("audio/")
                    else -> false
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        "No ${titles[subTab].lowercase()} found",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered.size) { index ->
                    val message = filtered[index]
                    MediaCard(
                        message = message,
                        onOpen = {
                            message.attachmentPath?.let {
                                onOpen(
                                    it,
                                    message.attachmentMime
                                )
                            }
                        },
                        onShare = {
                            message.attachmentPath?.let {
                                onShare(
                                    it,
                                    message.attachmentMime
                                )
                            }
                        }
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun MediaCard(
    message: MessageEntity,
    onOpen: () -> Unit,
    onShare: () -> Unit
) {
    val isImage = message.attachmentMime?.startsWith("image/") == true
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                message.senderLabel,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isImage && message.attachmentPath != null) {
                val bitmap = remember(message.attachmentPath) {
                    BitmapFactory.decodeFile(message.attachmentPath)?.asImageBitmap()
                }
                bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            Text(
                text = message.attachmentName ?: "attachment",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = message.attachmentMime ?: "file",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                style = MaterialTheme.typography.labelSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                TextButton(onClick = onOpen) { Text("Open") }
                TextButton(onClick = onShare) { Text("Share") }
            }
        }
    }
}

@Composable
private fun SettingsTab(
    graph: AppGraph,
    currentUsername: String,
    currentFullName: String,
    totalContacts: Int,
    totalMessages: Int,
    onLogout: () -> Unit,
    onClearData: () -> Unit,
    onShareBackup: () -> Unit
) {
    var userCount by remember { mutableStateOf(0) }

    LaunchedEffect(currentUsername) {
        userCount = graph.database.userDao().count()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ProfileCard(currentFullName, currentUsername)
        }

        item {
            Text(
                "General",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        item {
            ActionCard(
                title = "Appearance",
                body = "Customize themes, colors, and chat bubble styles.",
                buttonText = "Customize",
                icon = Icons.Default.Palette,
                onClick = {}
            )
        }

        item {
            ActionCard(
                title = "Security & Privacy",
                body = "Manage hashed passwords and prepare for E2E encryption.",
                buttonText = "Configure",
                icon = Icons.Default.Security,
                onClick = {}
            )
        }

        item {
            Text(
                "Network & Storage",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        item {
            ActionCard(
                title = "Networking",
                body = "Configure Bluetooth and Wi-Fi Direct transport settings.",
                buttonText = "Manage",
                icon = Icons.Default.Router,
                onClick = {}
            )
        }

        item {
            ActionCard(
                title = "Storage & Data",
                body = "Manage attachments and clear local application data.",
                buttonText = "Clear Data",
                icon = Icons.Default.Storage,
                onClick = onClearData
            )
        }

        item {
            ActionCard(
                title = "Backup & Export",
                body = "Create a local backup of your messages for offline sharing.",
                buttonText = "Export Backup",
                icon = Icons.Default.Backup,
                onClick = onShareBackup
            )
        }

        item {
            Text(
                "Account",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        item {
            ActionCard(
                title = "Log out",
                body = "End current session and return to authentication.",
                buttonText = "Logout",
                icon = Icons.Default.ExitToApp,
                onClick = onLogout
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = 0.3f
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("SilentNet Suite v2.0", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "Privacy-first offline communication",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun ProfileCard(fullName: String, username: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    fullName.take(1).uppercase(),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    fullName,
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "@$username",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    body: String,
    buttonText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            ElevatedButton(
                onClick = onClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun UserPicker(
    graph: AppGraph,
    onDismiss: () -> Unit,
    onSelect: (String, String) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val localUsers =
        remember(query) { mutableStateOf<List<com.silentnet.data.UserEntity>>(emptyList()) }
    val currentUsername = graph.sessionManager.currentUsername()
    val nearbyPeers by graph.transportManager.peers.collectAsState()
    val filteredNearbyPeers = nearbyPeers.filter { it.username != currentUsername }

    LaunchedEffect(query) {
        val users = if (query.length >= 2) {
            graph.database.userDao().searchUsers(query)
        } else if (query.isEmpty()) {
            graph.database.userDao().allUsers()
        } else {
            emptyList()
        }
        localUsers.value = users.filter { it.username != currentUsername }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonSearch, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Start a New Chat")
            }
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 450.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search by name or @username") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (filteredNearbyPeers.isNotEmpty()) {
                        item {
                            Text(
                                "Nearby Peers",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        items(filteredNearbyPeers) { peer ->
                            UserRow(
                                peer.fullName,
                                peer.username,
                                peer.nickname,
                                isNearby = true
                            ) {
                                onSelect(peer.username, peer.fullName)
                            }
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }

                    item {
                        Text(
                            "Registered Users",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    if (localUsers.value.isEmpty()) {
                        item {
                            Text(
                                "No users found",
                                modifier = Modifier.padding(8.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    } else {
                        items(localUsers.value) { user ->
                            UserRow(
                                user.fullName,
                                user.username,
                                user.nickname,
                                isNearby = false
                            ) {
                                onSelect(user.username, user.fullName)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun UserRow(
    fullName: String,
    username: String,
    nickname: String?,
    isNearby: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = 0.5f
            )
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isNearby) Color(0xFF22C55E) else MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    fullName.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    fullName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append("@$username")
                        if (!nickname.isNullOrBlank()) append(" ($nickname)")
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (isNearby) {
                Icon(
                    Icons.Default.Group,
                    null,
                    tint = Color(0xFF22C55E),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun priorityLabel(priority: Int): String {
    return when {
        priority >= 80 -> "Priority: critical"
        priority >= 55 -> "Priority: high"
        priority >= 30 -> "Priority: normal"
        else -> "Priority: low"
    }
}

private fun openAttachment(context: Context, path: String, mime: String?) {
    val uri = FileStorage.uriForFile(context, path)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, "Open attachment")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun shareAttachment(context: Context, path: String, mime: String?) {
    val uri = FileStorage.uriForFile(context, path)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime ?: "*/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(intent, "Share attachment").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun createBackupFile(
    context: Context,
    username: String,
    fullName: String,
    contacts: List<ContactEntity>,
    messages: List<MessageEntity>
): File {
    val file = File(context.cacheDir, "silentnet_backup_${System.currentTimeMillis()}.txt")
    val content = buildString {
        appendLine("SilentNet Suite backup")
        appendLine("User: $fullName (@$username)")
        appendLine()
        appendLine("Contacts")
        contacts.forEach {
            appendLine("- ${it.alias} (@${it.contactUsername})")
        }
        appendLine()
        appendLine("Messages")
        messages.sortedBy { it.timestamp }.forEach {
            appendLine("[${it.timestamp}] ${if (it.isOutgoing) "OUT" else "IN"} ${it.senderLabel}: ${it.body ?: it.attachmentName ?: ""}")
        }
    }

    // Priority 9: Backup Encryption
    val encryptedContent = com.silentnet.security.BackupManager.encryptBackup(content)
    file.writeText(encryptedContent ?: content)
    return file
}

@Composable
private fun CallOverlay(
    state: com.silentnet.transport.CallState,
    remoteFullName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onEnd: () -> Unit
) {
    BackHandler {
        onEnd()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Person,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                remoteFullName,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Text(
                when (state) {
                    com.silentnet.transport.CallState.OUTGOING_REQUESTED -> "Calling..."
                    com.silentnet.transport.CallState.INCOMING_REQUESTED -> "Incoming Call..."
                    com.silentnet.transport.CallState.CONNECTED -> "Connected"
                    else -> ""
                },
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(64.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                if (state == com.silentnet.transport.CallState.INCOMING_REQUESTED) {
                    FloatingActionButton(
                        onClick = onDecline,
                        containerColor = Color.Red,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.CallEnd, null)
                    }
                    FloatingActionButton(
                        onClick = onAccept,
                        containerColor = Color.Green,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.Call, null)
                    }
                } else {
                    FloatingActionButton(
                        onClick = onEnd,
                        containerColor = Color.Red,
                        contentColor = Color.White
                    ) {
                        Icon(Icons.Default.CallEnd, null)
                    }
                }
            }
        }
    }
}
@Composable
fun DirectCallTab(graph: AppGraph) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            "Direct Calls",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Text("Voice Call")

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                    }
                ) {
                    Text("Start Voice Call")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {

                Text("Video Call")

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                    }
                ) {
                    Text("Start Video Call")
                }
            }
        }
    }
}