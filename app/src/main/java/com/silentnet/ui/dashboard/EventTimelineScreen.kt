package com.silentnet.ui.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentnet.app.AppGraph
import com.silentnet.data.NetworkEventEntity
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventTimelineScreen(graph: AppGraph, onBack: () -> Unit) {
    val isDemoMode by graph.demoManager.isDemoMode.collectAsState()
    val localEvents by graph.database.analyticsDao().getRecentEventsFlow().collectAsState(initial = emptyList())
    val demoEvents = remember { mutableStateListOf<NetworkEventEntity>() }
    
    LaunchedEffect(isDemoMode) {
        if (isDemoMode) {
            graph.demoManager.simulatedEvents.collect {
                demoEvents.add(0, it)
                if (demoEvents.size > 50) demoEvents.removeAt(50)
            }
        }
    }

    val displayEvents = if (isDemoMode) demoEvents else localEvents

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Event Timeline") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        if (displayEvents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No network events recorded yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(displayEvents) { event ->
                    EventCard(event)
                }
            }
        }
    }
}

@Composable
fun EventCard(event: NetworkEventEntity) {
    val icon = when (event.type) {
        "Node Joined" -> Icons.Default.PersonAdd
        "Node Left" -> Icons.Default.PersonRemove
        "Route Learned" -> Icons.Default.AltRoute
        "Route Updated" -> Icons.Default.EditRoad
        "Packet Delivered" -> Icons.Default.DoneAll
        "Packet Failed" -> Icons.Default.Error
        "Packet Relayed" -> Icons.Default.Router
        "Emergency Broadcast" -> Icons.Default.Warning
        "Recovery Broadcast" -> Icons.Default.Backup
        "Group Event" -> Icons.Default.Groups
        else -> Icons.Default.Info
    }

    val color = when (event.type) {
        "Packet Failed" -> MaterialTheme.colorScheme.error
        "Emergency Broadcast" -> MaterialTheme.colorScheme.error
        "Packet Delivered" -> Color(0xFF2E7D32)
        "Node Joined" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(event.type, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(event.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            Text(timeFormat.format(Date(event.timestamp)), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}
