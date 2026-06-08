package com.silentnet.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentnet.app.AppGraph
import com.silentnet.data.GroupEntity
import com.silentnet.data.GroupMemberEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    graph: AppGraph,
    group: GroupEntity,
    onBack: () -> Unit
) {
    val members by graph.groupRepository.observeMembers(group.groupId).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showInviteDialog by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Group Info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { graph.transportManager.rotateGroupKey(group.groupId) } }) {
                        Icon(Icons.Default.Refresh, "Rotate Key")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showInviteDialog = true }) {
                Icon(Icons.Default.PersonAdd, null)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(group.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(group.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    group.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }

            item {
                Text("Members (${members.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(members) { member ->
                MemberRow(member)
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }

            item {
                Button(
                    onClick = { scope.launch { graph.groupRepository.leaveGroup(group.groupId); onBack() } },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Leave Group")
                }
            }
        }
    }

    if (showInviteDialog) {
        InviteMemberDialog(
            graph = graph,
            onDismiss = { showInviteDialog = false },
            onInvite = { username ->
                graph.transportManager.inviteMember(group.groupId, username)
                showInviteDialog = false
            }
        )
    }
}

@Composable
fun MemberRow(member: GroupMemberEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(member.alias.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(member.alias, fontWeight = FontWeight.SemiBold)
                Text(member.nodeId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            if (member.role == 1) {
                Text("Admin", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun InviteMemberDialog(
    graph: AppGraph,
    onDismiss: () -> Unit,
    onInvite: (String) -> Unit
) {
    val currentUsername = graph.sessionManager.currentUsername() ?: ""
    val contacts by graph.contactRepository.observeContacts(currentUsername).collectAsState(initial = emptyList())
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invite Member") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                items(contacts) { contact ->
                    ListItem(
                        headlineContent = { Text(contact.alias) },
                        supportingContent = { Text("@${contact.contactUsername}") },
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                        trailingContent = {
                            Button(onClick = { onInvite(contact.contactUsername) }) {
                                Text("Invite")
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
