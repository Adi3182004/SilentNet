package com.silentnet.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.silentnet.app.AppGraph
import com.silentnet.analytics.MeshAnalyticsManager
import com.silentnet.data.NetworkEventEntity
import kotlinx.coroutines.launch

@Composable
fun NetworkDashboardScreen(graph: AppGraph) {
    val liveStats by graph.analyticsManager.dashboardStats.collectAsState()
    val demoStats by graph.demoManager.simulatedStats.collectAsState()
    val disasterDemoStats by graph.demoManager.disasterStats.collectAsState()
    val isDemoMode by graph.demoManager.isDemoMode.collectAsState()
    
    val stats = if (isDemoMode) {
        // Map MeshDemoManager.SimulatedStats to MeshAnalyticsManager.DashboardStats
        MeshAnalyticsManager.DashboardStats(
            activeNodes = demoStats.nodeCount,
            directNeighbors = demoStats.neighborCount,
            knownRoutes = demoStats.routeCount,
            pendingPackets = demoStats.packetCount,
            healthScore = demoStats.healthScore,
            healthLabel = if (demoStats.healthScore >= 75) "Excellent" else "Good",
            activeIncidents = disasterDemoStats.activeIncidents,
            missingPersons = disasterDemoStats.missingPersons,
            volunteersActive = disasterDemoStats.volunteersActive,
            safeZonesOpen = disasterDemoStats.safeZonesOpen,
            medicalRequests = disasterDemoStats.medicalRequests,
            resourcesAvailable = disasterDemoStats.resourcesAvailable
        )
    } else {
        liveStats
    }
    
    var showGraph by remember { mutableStateOf(false) }
    var showEvents by remember { mutableStateOf(false) }
    var showPackets by remember { mutableStateOf(false) }
    var showDisasterCoordination by remember { mutableStateOf(false) }
    var showDemoSettings by remember { mutableStateOf(false) }

    if (showGraph) {
        MeshGraphScreen(graph, onBack = { showGraph = false })
        return
    }

    if (showEvents) {
        EventTimelineScreen(graph, onBack = { showEvents = false })
        return
    }

    if (showPackets) {
        PacketInspectorScreen(graph, onBack = { showPackets = false })
        return
    }

    if (showDisasterCoordination) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showDisasterCoordination = false }) { Icon(Icons.Default.ArrowBack, null) }
                Text("Disaster Coordination", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            com.silentnet.ui.recovery.DisasterCoordinationScreen(graph)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Card: Mesh Health Score
        MeshHealthCard(stats)

        // Demo Mode Indicator
        if (isDemoMode) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Science, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Demo Mode Active (Simulated Data)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { showDemoSettings = true }) { Text("Settings") }
                }
            }
        }

        // Live Actions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DashboardActionCard("Mesh Graph", Icons.Default.Hub, Modifier.weight(1f)) { showGraph = true }
            DashboardActionCard("Coordination", Icons.Default.CrisisAlert, Modifier.weight(1f)) { showDisasterCoordination = true }
        }

        // Network Statistics Grid
        Text("Network Metrics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Active Nodes", stats.activeNodes.toString(), Icons.Default.Group, Modifier.weight(1f))
            StatCard("Direct Peers", stats.directNeighbors.toString(), Icons.Default.WifiTethering, Modifier.weight(1f))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Known Routes", stats.knownRoutes.toString(), Icons.Default.AltRoute, Modifier.weight(1f))
            StatCard("Relay Nodes", stats.relayNodes.toString(), Icons.Default.Router, Modifier.weight(1f))
        }

        Text("Disaster Recovery Stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatRow("Active Incidents", stats.activeIncidents.toString(), Icons.Default.Warning, color = MaterialTheme.colorScheme.error)
                StatRow("Missing Persons", stats.missingPersons.toString(), Icons.Default.PersonSearch)
                StatRow("Medical Requests", stats.medicalRequests.toString(), Icons.Default.MedicalServices, color = Color.Red)
                StatRow("Active Volunteers", stats.volunteersActive.toString(), Icons.Default.VolunteerActivism)
                StatRow("Open Safe Zones", stats.safeZonesOpen.toString(), Icons.Default.HomeWork)
                StatRow("Available Resources", stats.resourcesAvailable.toString(), Icons.Default.Inventory)
            }
        }

        Text("Queue Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Card(
            onClick = { showPackets = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StatRow("Pending Packets", stats.pendingPackets.toString(), Icons.Default.Inventory2)
                StatRow("Store-and-Forward", stats.storeForwardQueueSize.toString(), Icons.Default.CloudQueue)
                StatRow("Emergency Queue", stats.emergencyQueueSize.toString(), Icons.Default.PriorityHigh, color = MaterialTheme.colorScheme.error)
            }
        }

        Text("Community Stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Groups", stats.groupCount.toString(), Icons.Default.Groups, Modifier.weight(1f))
            StatCard("Recovery Posts", stats.recoveryPostCount.toString(), Icons.Default.PostAdd, Modifier.weight(1f))
        }

        // System Performance
        Text("Performance Monitor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        PerformanceMonitorCard(graph)

        // Demo Mode Toggle (at bottom for easy access)
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        
        Button(
            onClick = {
                scope.launch {
                    val reportFile = com.silentnet.analytics.MeshReportExporter.generateReport(context, graph)
                    com.silentnet.util.ShareHelper.shareFile(context, reportFile, "text/plain", "Mesh Network Report")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Icon(Icons.Default.Assessment, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Export Mesh Intelligence Report")
        }

        Button(
            onClick = { showDemoSettings = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = if (isDemoMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
        ) {
            Icon(if (isDemoMode) Icons.Default.Stop else Icons.Default.PlayArrow, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isDemoMode) "Stop Demo Mode" else "Start Demo Mode")
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showDemoSettings) {
        DemoSettingsDialog(graph) { showDemoSettings = false }
    }
}

@Composable
fun MeshHealthCard(stats: MeshAnalyticsManager.DashboardStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Mesh Health Score", color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelLarge)
            Text("${stats.healthScore.toInt()}%", color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black)
            
            Surface(
                color = Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = stats.healthLabel.uppercase(),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DashboardActionCard(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StatCard(label: String, value: String, icon: ImageVector, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
fun StatRow(label: String, value: String, icon: ImageVector, color: Color = MaterialTheme.colorScheme.primary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(20.dp), tint = color)
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
fun ResearchStatRow(label: String, value: String, progress: Float, icon: ImageVector) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun PerformanceMonitorCard(graph: AppGraph) {
    val runtime = Runtime.getRuntime()
    val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    val maxMemory = runtime.maxMemory() / (1024 * 1024)
    
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            LinearProgressIndicator(
                progress = { usedMemory.toFloat() / maxMemory.toFloat() },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Memory Usage", style = MaterialTheme.typography.labelSmall)
                Text("${usedMemory}MB / ${maxMemory}MB", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun DemoSettingsDialog(graph: AppGraph, onDismiss: () -> Unit) {
    var nodeCount by remember { mutableStateOf(10f) }
    val isDemoMode by graph.demoManager.isDemoMode.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Demo Mode Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Simulate a large mesh network to test dashboard visualizations and topologies.", style = MaterialTheme.typography.bodySmall)
                
                Text("Node Count: ${nodeCount.toInt()}")
                Slider(
                    value = nodeCount,
                    onValueChange = { nodeCount = it },
                    valueRange = 5f..50f,
                    steps = 4
                )
                
                Text("Topologies: Mesh (Ring + Cross-links)", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(onClick = {
                graph.demoManager.setDemoMode(!isDemoMode, nodeCount.toInt())
                onDismiss()
            }) {
                Text(if (isDemoMode) "Stop Demo" else "Start Demo")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
