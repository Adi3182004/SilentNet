package com.silentnet.ui.dashboard

import androidx.compose.foundation.Canvas
import kotlinx.coroutines.flow.flowOf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentnet.app.AppGraph
import com.silentnet.transport.MeshRoute
import com.silentnet.transport.NearbyPeer
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeshGraphScreen(graph: AppGraph, onBack: () -> Unit) {
    val isDemoMode by graph.demoManager.isDemoMode.collectAsState()
    val peers by (if (isDemoMode) graph.demoManager.simulatedPeers else graph.transportManager.peers).collectAsState(initial = emptyList())
    val routes by (if (isDemoMode) graph.demoManager.simulatedRoutes else flowOf(graph.transportManager.getRoutingTable())).collectAsState(initial = emptyMap())

    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var scale by remember { mutableStateOf(1f) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Mesh Graph") },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale *= zoom
                            offset += pan
                        }
                    }
                    .pointerInput(peers) {
                        detectTapGestures { tapOffset ->
                            // Hit testing for nodes
                            val centerX = size.width / 2 + offset.x
                            val centerY = size.height / 2 + offset.y
                            val radius = 150f * scale
                            
                            // Check local node
                            if ((tapOffset - Offset(centerX, centerY)).getDistance() < 30f * scale) {
                                selectedNodeId = "LOCAL"
                                return@detectTapGestures
                            }

                            peers.forEachIndexed { index, peer ->
                                val angle = (360f / peers.size.coerceAtLeast(1)) * index
                                val peerX = centerX + radius * 1.5f * cos(Math.toRadians(angle.toDouble())).toFloat()
                                val peerY = centerY + radius * 1.5f * sin(Math.toRadians(angle.toDouble())).toFloat()
                                if ((tapOffset - Offset(peerX, peerY)).getDistance() < 25f * scale) {
                                    selectedNodeId = peer.nodeId
                                }
                            }
                        }
                    }
            ) {
                val centerX = size.width / 2 + offset.x
                val centerY = size.height / 2 + offset.y
                val radius = 150f * scale

                // Draw links from routing table
                routes.forEach { (targetId, nextHops) ->
                    nextHops.forEach { (nextHopId, route) ->
                        // Visualization logic: simplify for now, just draw links between peers
                    }
                }

                // Draw edges to direct peers
                peers.forEachIndexed { index, peer ->
                    val angle = (360f / peers.size.coerceAtLeast(1)) * index
                    val peerX = centerX + radius * 1.5f * cos(Math.toRadians(angle.toDouble())).toFloat()
                    val peerY = centerY + radius * 1.5f * sin(Math.toRadians(angle.toDouble())).toFloat()

                    drawLine(
                        color = primaryColor.copy(alpha = 0.3f),
                        start = Offset(centerX, centerY),
                        end = Offset(peerX, peerY),
                        strokeWidth = 2f * scale
                    )
                    
                    drawCircle(
                        color = secondaryColor,
                        radius = 20f * scale,
                        center = Offset(peerX, peerY)
                    )
                }

                // Draw Local Node
                drawCircle(
                    color = primaryColor,
                    radius = 30f * scale,
                    center = Offset(centerX, centerY)
                )
            }

            // Legend/Info
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                LegendItem("You (Local Node)", MaterialTheme.colorScheme.primary)
                LegendItem("Direct Peer", MaterialTheme.colorScheme.secondary)
                if (isDemoMode) {
                    Text("DEMO MODE", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
    }

    if (selectedNodeId != null) {
        val peer = peers.find { it.nodeId == selectedNodeId }
        val routeMap = routes[selectedNodeId] ?: emptyMap()
        RouteInspectorDialog(
            nodeId = selectedNodeId!!,
            peer = peer,
            routes = routeMap,
            onDismiss = { selectedNodeId = null }
        )
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteInspectorDialog(
    nodeId: String,
    peer: NearbyPeer?,
    routes: Map<String, MeshRoute>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(peer?.username ?: nodeId) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (nodeId == "LOCAL") {
                    Text("This is your device.")
                    Text("Node ID: ${nodeId}")
                } else {
                    Text("Node ID: $nodeId")
                    Text("Username: ${peer?.username ?: "Unknown"}")
                    Text("Status: ${if (peer != null) "Direct Connection" else "Relay Only"}")
                    
                    if (routes.isNotEmpty()) {
                        Divider()
                        Text("Active Routes:", fontWeight = FontWeight.Bold)
                        routes.forEach { (nextHop, route) ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Via: $nextHop", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                    Text("Hops: ${route.hopCount}", style = MaterialTheme.typography.bodySmall)
                                    Text("Quality: ${(route.deliveryConfidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                    Text("Latency: ${route.averageLatency}ms", style = MaterialTheme.typography.bodySmall)
                                    Text("Success/Fail: ${route.successCount} / ${route.failureCount}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    } else {
                        Text("No direct or multi-hop routes cached.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}
