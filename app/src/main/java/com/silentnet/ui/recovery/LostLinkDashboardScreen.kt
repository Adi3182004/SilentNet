package com.silentnet.ui.recovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.silentnet.data.LostDeviceEntity
import com.silentnet.data.LostLinkReportEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostLinkDashboardScreen(
    graph: AppGraph,
    onBack: () -> Unit
) {
    val devices by graph.recoveryRepository.observeLostDevices().collectAsState(initial = emptyList())
    val allReports by graph.recoveryRepository.observeAllLostReports().collectAsState(initial = emptyList())
    val searchHistory by graph.recoveryRepository.observeSearchHistory().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showAddDevice by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Start scanning for community recovery
    DisposableEffect(Unit) {
        graph.lostLinkManager.startScanning()
        onDispose {
            graph.lostLinkManager.stopScanning()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("LostLink Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDevice = true }) {
                        Icon(Icons.Default.Add, null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                LostLinkHeader()
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search Device ID...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { 
                                scope.launch { 
                                    graph.recoveryRepository.insertSearch(searchQuery)
                                    // Trigger mesh search
                                    graph.transportManager.relayLostLinkReport(com.silentnet.data.LostLinkReportEntity(
                                        deviceId = searchQuery,
                                        reporterNodeId = graph.sessionManager.currentUsername() ?: "Searcher",
                                        timestamp = System.currentTimeMillis(),
                                        rssi = 0,
                                        confidence = 0.0,
                                        nodeInfo = "Search Request"
                                    ))
                                }
                            }) {
                                Icon(Icons.Default.Send, "Search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp)
                )
            }

            if (searchHistory.isNotEmpty() && searchQuery.isEmpty()) {
                item {
                    Text("Recent Searches", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                items(searchHistory.take(3)) { history ->
                    ListItem(
                        headlineContent = { Text(history.query) },
                        leadingContent = { Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.clickable { searchQuery = history.query }
                    )
                }
            }

            item {
                Text("My Tracked Devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (devices.isEmpty()) {
                item {
                    Text("No devices registered for LostLink tracking.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            } else {
                items(devices) { device ->
                    DeviceCard(
                        device = device,
                        reports = allReports.filter { it.deviceId == device.deviceId || it.deviceId == device.currentAnonymousId },
                        graph = graph,
                        onToggleLost = {
                            scope.launch {
                                val newStatus = !device.isLost
                                graph.recoveryRepository.updateLostStatus(device.deviceId, newStatus)
                                if (newStatus) {
                                    graph.lostLinkManager.startLostBeacon(device.deviceId)
                                } else {
                                    graph.lostLinkManager.stopLostBeacon()
                                }
                            }
                        }
                    )
                }
            }

            item {
                Text("Community Activity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            items(allReports.size.coerceAtMost(10)) { index ->
                ReportRow(allReports[index])
            }
        }
    }

    if (showAddDevice) {
        AddDeviceDialog(
            onDismiss = { showAddDevice = false },
            onAdd = { name, id ->
                scope.launch {
                    val device = LostDeviceEntity(
                        deviceId = id,
                        deviceName = name,
                        isLost = false
                    )
                    graph.recoveryRepository.insertLostDevice(device)
                    showAddDevice = false
                }
            }
        )
    }
}

@Composable
fun LostLinkHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WifiTethering, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("BLE Community Recovery Active", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Your device is scanning for nearby lost beacons to help the community.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun DeviceCard(
    device: LostDeviceEntity,
    reports: List<LostLinkReportEntity>,
    graph: AppGraph,
    onToggleLost: () -> Unit
) {
    val prediction = remember(reports) {
        val reportMaps = reports.map { 
            mapOf("timestamp" to it.timestamp.toDouble(), "rssi" to it.rssi.toDouble(), "nodeInfo" to (it.nodeInfo ?: "Unknown"))
        }
        graph.pythonBridge.predictLostLocation(reportMaps)
    }
    
    val confidence = remember(reports) {
        graph.pythonBridge.calculateConfidence(reports.map { it.rssi })
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(if (device.isLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (device.isLost) Icons.Default.BluetoothDisabled else Icons.Default.Bluetooth, null, tint = Color.White)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(device.deviceName, fontWeight = FontWeight.Bold)
                    Text("ID: ${device.deviceId.take(8)}...", style = MaterialTheme.typography.labelSmall)
                }
                Switch(checked = device.isLost, onCheckedChange = { onToggleLost() })
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (device.isLost) {
                Text("STATUS: LOST", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Estimated Location:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text(prediction, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { confidence.toFloat() },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Text("Confidence: ${(confidence * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
            } else {
                Text("STATUS: SECURE", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            
            if (reports.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Timeline", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                reports.take(3).forEach { report ->
                    Text("Seen ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(report.timestamp)} at ${report.nodeInfo}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun ReportRow(report: LostLinkReportEntity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.History, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Detected ${report.deviceId.take(6)} with ${report.rssi} dBm by ${report.reporterNodeId.take(6)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun AddDeviceDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf(UUID.randomUUID().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register Tracked Device") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Device Name") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("Beacon ID (Auto-generated)") }, modifier = Modifier.fillMaxWidth())
                Text("This ID will be broadcast anonymously if the device is lost.", style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onAdd(name, id) }) { Text("Register") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
