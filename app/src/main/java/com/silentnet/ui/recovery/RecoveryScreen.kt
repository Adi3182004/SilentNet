package com.silentnet.ui.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentnet.app.AppGraph
import com.silentnet.data.RecoveryPostEntity
import com.silentnet.data.RecoveryGroupEntity
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun RecoveryScreen(graph: AppGraph) {
    var tabIndex by remember { mutableStateOf(0) }
    val posts by graph.database.recoveryDao().observeAllPosts().collectAsState(initial = emptyList())
    val groups by graph.database.recoveryDao().observeAllGroups().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    var showCreatePost by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("Bulletin") })
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("Groups") })
        }

        Box(modifier = Modifier.weight(1f)) {
            if (tabIndex == 0) {
                BulletinTab(posts)
            } else {
                GroupsTab(groups, onJoin = { graph.transportManager.joinRecoveryGroup(it) })
            }
            
            FloatingActionButton(
                onClick = { if (tabIndex == 0) showCreatePost = true else showCreateGroup = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(if (tabIndex == 0) Icons.Default.PostAdd else Icons.Default.GroupAdd, null)
            }
        }
    }

    if (showCreatePost) {
        CreatePostDialog(
            onDismiss = { showCreatePost = false },
            onSend = { cat, txt, pri, anon ->
                graph.transportManager.sendRecoveryPost(cat, txt, pri, anon)
                showCreatePost = false
            }
        )
    }

    if (showCreateGroup) {
        CreateGroupDialog(
            onDismiss = { showCreateGroup = false },
            onSend = { name, desc ->
                graph.transportManager.createRecoveryGroup(name, desc)
                showCreateGroup = false
            }
        )
    }
}

@Composable
private fun BulletinTab(posts: List<RecoveryPostEntity>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(posts) { post ->
            PostCard(post)
        }
    }
}

@Composable
private fun PostCard(post: RecoveryPostEntity) {
    val priorityColor = when (post.priority) {
        2 -> Color(0xFFFFEBEE) // Critical
        1 -> Color(0xFFFFF3E0) // High
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when (post.priority) {
        2 -> Color.Red
        1 -> Color(0xFFFF9800)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = priorityColor),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        post.category.uppercase(),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (post.priority >= 1) {
                    Text(
                        if (post.priority == 2) "CRITICAL" else "HIGH PRIORITY",
                        color = if (post.priority == 2) Color.Red else Color(0xFFFF9800),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(post.timestamp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(post.content, style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (post.isAnonymous) Icons.Default.AccountCircle else Icons.Default.Person,
                    null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    post.authorAlias,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun GroupsTab(groups: List<RecoveryGroupEntity>, onJoin: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(groups) { group ->
            GroupCard(group, onJoin = { onJoin(group.groupNodeId) })
        }
    }
}

@Composable
private fun GroupCard(group: RecoveryGroupEntity, onJoin: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                group.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
            }
            if (group.isJoined) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF2E7D32))
            } else {
                Button(onClick = onJoin) { Text("Join") }
            }
        }
    }
}

@Composable
private fun CreatePostDialog(onDismiss: () -> Unit, onSend: (String, String, Int, Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Help Request") }
    var priority by remember { mutableStateOf(0) }
    var anonymous by remember { mutableStateOf(false) }
    val categories = listOf("Medical", "Shelter", "Food", "Water", "Missing Person", "Help Request", "Rescue Request", "Safe Zone", "Supply Update")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Recovery Post") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Simplified Category Dropdown/Selection
                Text("Category", style = MaterialTheme.typography.labelMedium)
                ScrollableRow(categories, category) { category = it }
                
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Information / Request") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = anonymous,
                        onCheckedChange = { anonymous = it }
                    )
                    Text("Post Anonymously")
                }
                
                Text("Priority", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = priority == 0, onClick = { priority = 0 }, label = { Text("Normal") })
                    FilterChip(selected = priority == 1, onClick = { priority = 1 }, label = { Text("High") })
                    FilterChip(selected = priority == 2, onClick = { priority = 2 }, label = { Text("Critical") })
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onSend(category, text, priority, anonymous) }) {
                Text("Post to Mesh")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ScrollableRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items) { item ->
            FilterChip(selected = selected == item, onClick = { onSelect(item) }, label = { Text(item) })
        }
    }
}

@Composable
private fun CreateGroupDialog(onDismiss: () -> Unit, onSend: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Emergency Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Group Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onSend(name, desc) }) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
