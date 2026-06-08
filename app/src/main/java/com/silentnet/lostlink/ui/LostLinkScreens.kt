@file:OptIn(ExperimentalMaterial3Api::class)

package com.silentnet.lostlink.ui

import androidx.compose.foundation.background
import com.silentnet.lostlink.data.RecoverySightingEntity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silentnet.lostlink.LostLinkV2Manager
import com.silentnet.lostlink.data.AssetEntity
import com.silentnet.lostlink.data.LostCaseEntity
import kotlinx.coroutines.launch
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext

@Composable
fun LostDeviceRecoveryScreen(manager: LostLinkV2Manager, onBack: () -> Unit) {
    val isDemoMode by manager.demoManager.isDemoMode.collectAsState()
    val realAssets by manager.assetManager.getAssets("CURRENT_USER").collectAsState(initial = emptyList())
    val demoAssets by manager.demoManager.simulatedAssets.collectAsState()
    
    val assets = if (isDemoMode) demoAssets else realAssets

    var viewMode by remember { mutableStateOf("list") } // list, register
    var selectedAssetId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredAssets = if (searchQuery.isBlank()) assets 
                         else assets.filter { it.assetName.contains(searchQuery, ignoreCase = true) || 
                                              it.assetType.contains(searchQuery, ignoreCase = true) ||
                                              it.assetId.contains(searchQuery, ignoreCase = true) }

    if (selectedAssetId != null) {
        RecoveryResultsScreen(manager = manager, assetId = selectedAssetId!!) {
            selectedAssetId = null
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recovery Dashboard", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (viewMode == "list") {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Search by name, type or ID...") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                    
                    Spacer(Modifier.height(16.dp))

                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Text("My Protected Assets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        if (filteredAssets.isEmpty()) {
                            item {
                                EmptyStatePlaceholder(if (searchQuery.isEmpty()) "No assets registered." else "No assets match '$searchQuery'")
                            }
                        } else {
                            items(filteredAssets) { asset ->
                                DeviceItemPremium(
                                    asset = asset,
                                    onClick = { selectedAssetId = asset.assetId },
                                    onToggleLost = { 
                                        if (!isDemoMode) {
                                            manager.assetManager.setAssetLost(asset.assetId, !asset.isLost) 
                                        }
                                    }
                                )
                            }
                        }
                        item {
                            Button(
                                onClick = { viewMode = "register" },
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Register New Asset")
                            }
                        }
                    }
                }
            } else {
                RegisterDeviceForm(onCancel = { viewMode = "list" }) { name, id, type ->
                    manager.assetManager.trackAsset(id, "CURRENT_USER", name, type)
                    viewMode = "list"
                }
            }
        }
    }
}

@Composable
fun LostLinkAssetTrackingScreen(manager: LostLinkV2Manager, onBack: () -> Unit) {
    val isDemoMode by manager.demoManager.isDemoMode.collectAsState()
    val realAssets by manager.assetManager.getAssets("CURRENT_USER").collectAsState(initial = emptyList())
    val demoAssets by manager.demoManager.simulatedAssets.collectAsState()
    
    val assets = if (isDemoMode) demoAssets else realAssets

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Asset Registry", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Asset Inventory", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (assets.isEmpty()) {
                item { EmptyStatePlaceholder("No assets tracked yet.") }
            } else {
                items(assets) { asset ->
                    AssetItemPremium(asset)
                }
            }
        }
    }
}

@Composable
fun RecoveryResultsScreen(manager: LostLinkV2Manager, assetId: String, onBack: () -> Unit) {
    val isDemoMode by manager.demoManager.isDemoMode.collectAsState()
    
    // In real mode, we need to find the active case for this asset
    val scope = rememberCoroutineScope()
    var activeCase by remember { mutableStateOf<LostCaseEntity?>(null) }
    
    LaunchedEffect(assetId) {
        activeCase = manager.searchEngine.repository.getActiveCaseForAsset(assetId)
    }

    val realSightings by (if (activeCase != null) {
        manager.searchEngine.repository.getSightingsForCase(activeCase!!.caseId)
    } else {
        kotlinx.coroutines.flow.flowOf(emptyList())
    }).collectAsState(initial = emptyList())

    val demoSightings by manager.demoManager.simulatedRecoverySightings.collectAsState()
    
    val sightings = if (isDemoMode) demoSightings.filter { it.detectedDeviceId == assetId } else realSightings
    
    val asset by produceState<AssetEntity?>(initialValue = null) {
        value = manager.assetManager.repository.getAsset(assetId)
    }

    var currentTab by remember { mutableStateOf("timeline") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(asset?.assetName ?: "Asset Recovery", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = if (currentTab == "timeline") 0 else 1) {
                Tab(selected = currentTab == "timeline", onClick = { currentTab = "timeline" }, text = { Text("Timeline") })
                Tab(selected = currentTab == "map", onClick = { currentTab = "map" }, text = { Text("Map") })
            }

            if (currentTab == "timeline") {
                RecoveryTimelineUI(sightings, asset)
            } else {
                RecoveryMapUI(sightings)
            }
        }
    }
}

@Composable
fun RecoveryTimelineUI(sightings: List<RecoverySightingEntity>, asset: AssetEntity?) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Recovery Timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (sightings.isEmpty()) {
            item { EmptyStatePlaceholder("Awaiting sightings for ${asset?.assetName ?: "asset"}...") }
        } else {
            items(sightings) { sighting ->
                TimelineItemPremium(sighting, asset)
            }
        }
    }
}

@Composable
fun RecoveryMapUI(sightings: List<RecoverySightingEntity>) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        MapPlaceholderPremium(sightings.firstOrNull(), sightings)
        
        if (sightings.isNotEmpty()) {
            val latest = sightings.first()
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Last Known Location", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                            Text(
                                "${(latest.confidence * 100).toInt()}% Confidence",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.width(8.dp))
                        Text("Reporter: ${latest.reporterUsername}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.width(8.dp))
                        Text("Coordinates: ${latest.latitude}, ${latest.longitude}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.width(8.dp))
                        Text("Detected: ${java.text.SimpleDateFormat("MMM dd, HH:mm:ss").format(latest.timestamp)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SignalCellularAlt, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.width(8.dp))
                        Text("Signal Strength: ${latest.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(20.dp))
                    
                    Button(
                        onClick = { 
                            if (latest.latitude != null && latest.longitude != null) {
                                openInGoogleMaps(context, latest.latitude, latest.longitude)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Map, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open In Google Maps", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineItemPremium(sighting: RecoverySightingEntity, asset: AssetEntity?) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(14.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Box(modifier = Modifier.width(2.dp).height(120.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Spacer(Modifier.width(16.dp))
        Card(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(java.text.SimpleDateFormat("HH:mm:ss").format(sighting.timestamp), fontWeight = FontWeight.Black, fontSize = 12.sp)
                    Text("${sighting.rssi} dBm", fontWeight = FontWeight.Bold, color = if (sighting.rssi > -70) Color(0xFF22C55E) else Color.Gray, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                Text(asset?.assetName ?: "Asset Detected", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Detected By: ${sighting.reporterUsername}", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodySmall)
                Text("Pune, Maharashtra", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("${sighting.latitude}, ${sighting.longitude}", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
                
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        if (sighting.latitude != null && sighting.longitude != null) {
                            openInGoogleMaps(context, sighting.latitude, sighting.longitude)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Maps", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun openInGoogleMaps(context: Context, latitude: Double, longitude: Double) {
    val uri = "geo:$latitude,$longitude?q=$latitude,$longitude"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
    intent.setPackage("com.google.android.apps.maps")
    try {
        context.startActivity(intent)
        android.util.Log.e("RUNTIME_LOG", "GOOGLE_MAP_OPENED lat=$latitude lon=$longitude")
    } catch (e: Exception) {
        val fallbackUri = "https://maps.google.com/?q=$latitude,$longitude"
        val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUri))
        context.startActivity(fallbackIntent)
        android.util.Log.e("RUNTIME_LOG", "GOOGLE_MAP_OPENED lat=$latitude lon=$longitude fallback=true")
    }
}

@Composable
private fun MapPlaceholderPremium(latest: RecoverySightingEntity?, all: List<RecoverySightingEntity>) {
    Box(
        modifier = Modifier.fillMaxWidth().height(300.dp).background(Color(0xFFF1F5F9)),
        contentAlignment = Alignment.Center
    ) {
        // Grid lines to look like a map
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 40.dp.toPx()
            for (i in 0..size.width.toInt() step step.toInt()) {
                drawLine(Color.LightGray.copy(alpha = 0.3f), start = androidx.compose.ui.geometry.Offset(i.toFloat(), 0f), end = androidx.compose.ui.geometry.Offset(i.toFloat(), size.height))
            }
            for (i in 0..size.height.toInt() step step.toInt()) {
                drawLine(Color.LightGray.copy(alpha = 0.3f), start = androidx.compose.ui.geometry.Offset(0f, i.toFloat()), end = androidx.compose.ui.geometry.Offset(size.width, i.toFloat()))
            }
        }

        if (latest != null) {
            // Newest location highlight
            Box(
                modifier = Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
            
            Column(modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Surface(color = Color.White, shape = RoundedCornerShape(8.dp), shadowElevation = 2.dp) {
                    Text(
                        "LIVE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF22C55E),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Map, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                Text("SCANNING MESH", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun LostLinkEmergencyScreen(manager: LostLinkV2Manager, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SOS Mesh", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
            Text("Broadcast Emergency Signal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text("All nearby LostLink nodes will receive your coordinates and alert status.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            
            Spacer(Modifier.height(32.dp))
            
            EmergencyButtonPremium("MEDICAL ASSISTANCE", Icons.Default.MedicalServices, Color(0xFFEF4444))
            EmergencyButtonPremium("FIRE / HAZARD", Icons.Default.LocalFireDepartment, Color(0xFFF59E0B))
            EmergencyButtonPremium("PERSONAL SAFETY", Icons.Default.Warning, Color(0xFF2563EB))
            EmergencyButtonPremium("DISASTER RELIEF", Icons.Default.Public, Color(0xFF7C3AED))
            
            Spacer(Modifier.weight(1f))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(12.dp))
                    Text("Abuse of emergency signals may result in reputation penalties.", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun LostLinkAnalyticsScreen(manager: LostLinkV2Manager, onBack: () -> Unit) {
    val isDemoMode by manager.demoManager.isDemoMode.collectAsState()
    val demoStats by manager.demoManager.simulatedAnalytics.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network Analytics", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text("Global Mesh Stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AnalyticsMetricSmall("Protected", if (isDemoMode) demoStats.devicesProtected.toString() else "0", Icons.Default.Shield, Modifier.weight(1f))
                    AnalyticsMetricSmall("Success", if (isDemoMode) demoStats.recoverySuccessRate else "N/A", Icons.Default.CheckCircle, Modifier.weight(1f))
                }
            }
            item {
                AnalyticsMetricLarge("Active Community Nodes", if (isDemoMode) demoStats.communityNodes.toString() else "1", Icons.Default.Groups)
            }
            item {
                AnalyticsMetricLarge("Discoveries Today", if (isDemoMode) demoStats.discoveriesToday.toString() else "0", Icons.Default.Search)
            }
            item {
                AnalyticsMetricLarge("Network Density", if (isDemoMode) demoStats.networkCoverage else "Scanning...", Icons.Default.Map)
            }
        }
    }
}

@Composable
fun LostLinkAdvancedScreen(manager: LostLinkV2Manager, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Engine", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Subsystem Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            item { StatusIndicatorCard("BLE ADVERTISING", "OPERATIONAL", true) }
            item { StatusIndicatorCard("BLE SCANNER", "ACTIVE", true) }
            item { StatusIndicatorCard("RECOVERY MESH", "SYNCED", true) }
            item { StatusIndicatorCard("SECURE STORAGE", "ENCRYPTED", true) }
            item { StatusIndicatorCard("DATABASE ENGINE", "OPTIMIZED", true) }
            item { StatusIndicatorCard("RELAY PROTOCOL", "V2.4", true) }
            
            item { Spacer(Modifier.height(16.dp)) }
            item {
                Button(
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Hard Reset Subsystem")
                }
            }
        }
    }
}

@Composable
private fun EmergencyButtonPremium(label: String, icon: ImageVector, color: Color) {
    Button(
        onClick = {},
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(16.dp)
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(16.dp))
        Text(label, fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AnalyticsMetricSmall(label: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun AnalyticsMetricLarge(label: String, value: String, icon: ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun StatusIndicatorCard(label: String, status: String, isOk: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(if (isOk) Color(0xFF22C55E) else Color.Red, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(status, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = if (isOk) Color(0xFF22C55E) else Color.Red)
            }
        }
    }
}

@Composable
private fun DeviceItemPremium(asset: AssetEntity, onClick: () -> Unit, onToggleLost: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (asset.isLost) MaterialTheme.colorScheme.error.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        when(asset.assetType) {
                            "Phone" -> Icons.Default.Smartphone
                            "Laptop" -> Icons.Default.Laptop
                            "Bag" -> Icons.Default.Inventory
                            else -> Icons.Default.Devices
                        }, 
                        null, 
                        tint = if (asset.isLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(asset.assetName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Surface(
                    color = if (asset.isLost) MaterialTheme.colorScheme.errorContainer else Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        if (asset.isLost) "LOST" else "SECURE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (asset.isLost) MaterialTheme.colorScheme.error else Color(0xFF166534),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Type: ${asset.assetType} | ID: ${asset.assetId.take(8)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onToggleLost,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (asset.isLost) Color(0xFF22C55E) else MaterialTheme.colorScheme.errorContainer,
                        contentColor = if (asset.isLost) Color.White else MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (asset.isLost) "MARK AS FOUND" else "MARK AS LOST", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Canvas(modifier: Modifier, onDraw: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit) {
    androidx.compose.foundation.Canvas(modifier = modifier, onDraw = onDraw)
}

@Composable
private fun AssetItemPremium(asset: AssetEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when(asset.assetType) {
                        "Bag" -> Icons.Default.Inventory
                        "Wallet" -> Icons.Default.AccountBalanceWallet
                        "Bike" -> Icons.Default.DirectionsBike
                        else -> Icons.Default.Category
                    }, 
                    null, 
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(asset.assetName, fontWeight = FontWeight.Bold)
                Text(asset.assetType, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            if (asset.isLost) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun RegisterDeviceForm(onCancel: () -> Unit, onComplete: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var id by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Phone") }
    
    val types = listOf("Phone", "Laptop", "Tablet", "Watch", "Wallet", "Bag", "Pet", "Custom")

    Column(modifier = Modifier.padding(24.dp)) {
        Text("Register Asset", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Link a new hardware beacon or smartphone to your account.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Asset Name") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = id, onValueChange = { id = it }, label = { Text("Beacon ID (Hex)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
        Spacer(Modifier.height(12.dp))
        
        Text("Asset Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Simple selection for demo
            types.take(4).forEach { t ->
                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
            }
        }
        
        Spacer(Modifier.height(24.dp))
        Button(onClick = { onComplete(name, id, type) }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) {
            Text("Add to Registry")
        }
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
private fun EmptyStatePlaceholder(msg: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Text(msg, modifier = Modifier.padding(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
    }
}
