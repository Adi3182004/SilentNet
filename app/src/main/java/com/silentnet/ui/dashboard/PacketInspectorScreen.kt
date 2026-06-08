package com.silentnet.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.silentnet.app.AppGraph
import com.silentnet.data.MeshPacketEntity
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PacketInspectorScreen(graph: AppGraph, onBack: () -> Unit) {
    val packets by graph.database.meshPacketDao().getAllPacketsFlow().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Packet Inspector") },
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
        if (packets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No packets in transit queue.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(packets) { packet ->
                    PacketCard(packet)
                }
            }
        }
    }
}

@Composable
fun PacketCard(packet: MeshPacketEntity) {
    val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ID: ${packet.packetId.take(8)}...", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                Text(packet.payloadType.uppercase(), fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Source: ${packet.sourceNodeId}", style = MaterialTheme.typography.bodySmall)
            Text("Target: ${packet.targetNodeId}", style = MaterialTheme.typography.bodySmall)
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Hops: ${packet.hopCount}", style = MaterialTheme.typography.labelSmall)
                Text("TTL: ${packet.ttl}", style = MaterialTheme.typography.labelSmall)
                Text("Retries: ${packet.retryCount}", style = MaterialTheme.typography.labelSmall)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Text("Timestamp: ${dateFormat.format(Date(packet.timestamp))}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}
