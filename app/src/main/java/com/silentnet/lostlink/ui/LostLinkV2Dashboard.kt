package com.silentnet.lostlink.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import com.silentnet.lostlink.LostLinkV2Manager
import com.silentnet.lostlink.integration.LostLinkBridge

/**
 * LostLinkV2Dashboard
 * 
 * A completely isolated dashboard for LostLink V2.
 * It does not depend on any core SilentNet systems.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LostLinkV2Dashboard(
    bridge: LostLinkBridge,
    onBack: () -> Unit
) {
    val manager = bridge.manager
    val isDemoMode by manager.demoManager.isDemoMode.collectAsState()
    val isEnabled by bridge.isEnabled.collectAsState()
    
    var selectedTab by remember { mutableStateOf("home") }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.all { it.value }) {
            bridge.setLostLinkEnabled(true)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("LostLink", fontWeight = FontWeight.Black, style = MaterialTheme.typography.titleLarge)
                        if (isDemoMode) {
                            Text("PORTFOLIO DEMO MODE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                },
                actions = {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Demo", style = MaterialTheme.typography.labelSmall)
                        Switch(
                            checked = isDemoMode,
                            onCheckedChange = { manager.demoManager.setDemoMode(it) },
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (selectedTab) {
                "home" -> V2HomeContent(
                    bridge = bridge,
                    isEnabled = isEnabled,
                    isDemoMode = isDemoMode,
                    onToggle = { enabled ->
                        if (enabled) {
                            val hasPerms = manager.blePermissionManager.hasPermissions()
                            if (hasPerms) {
                                bridge.setLostLinkEnabled(true)
                            } else {
                                permissionLauncher.launch(manager.blePermissionManager.foregroundPermissions)
                            }
                        } else {
                            bridge.setLostLinkEnabled(false)
                        }
                    },
                    onNavigate = { selectedTab = it }
                )
                "recovery" -> LostDeviceRecoveryScreen(manager = manager, onBack = { selectedTab = "home" })
                "emergency" -> LostLinkEmergencyScreen(manager = manager, onBack = { selectedTab = "home" })
                "assets" -> LostLinkAssetTrackingScreen(manager = manager, onBack = { selectedTab = "home" })
                "analytics" -> LostLinkAnalyticsScreen(manager = manager, onBack = { selectedTab = "home" })
                "advanced" -> LostLinkAdvancedScreen(manager = manager, onBack = { selectedTab = "home" })
            }
        }
    }
}

@Composable
private fun V2HomeContent(
    bridge: LostLinkBridge,
    isEnabled: Boolean,
    isDemoMode: Boolean,
    onToggle: (Boolean) -> Unit,
    onNavigate: (String) -> Unit
) {
    val manager = bridge.manager
    val realDiscoveries by manager.allEnrichedObservations.collectAsState(initial = emptyList())
    val demoDiscoveries by manager.demoManager.simulatedEnriched.collectAsState()
    
    val discoveries = if (isDemoMode) demoDiscoveries else realDiscoveries

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. User Profile Card
        item {
            UserProfileCard(isDemoMode)
        }

        // 2. Main System Toggle
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(28.dp),
                border = if (isEnabled) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) else null
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                if (isEnabled) "Mesh Engine Active" else "Mesh Engine Standby",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                if (isEnabled) "Broadcasting recovery signals" else "Not participating in recovery",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { onToggle(it) }
                        )
                    }
                }
            }
        }

        item {
            Text("Ecosystem Services", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }

        // 3. Action Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    V2ActionCardSmall("My Assets", "Track personal items", Icons.Default.Category, Modifier.weight(1f)) { onNavigate("assets") }
                    V2ActionCardSmall("Recovery", "Search lost devices", Icons.Default.Devices, Modifier.weight(1f)) { onNavigate("recovery") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    V2ActionCardSmall("Pair Device", "Simulate trusted pairing", Icons.Default.BluetoothConnected, Modifier.weight(1f)) { 
                        // Simulate Phase 14 & 15
                        manager.assetManager.registerTrustedDevice(
                            deviceId = "DEV_${System.currentTimeMillis().toString().takeLast(6)}",
                            userId = "CURRENT_USER",
                            username = "Aditya Andhalkar",
                            deviceName = "Aditya's Pixel 8",
                            deviceType = "Phone",
                            publicKey = "DEMO_KEY_" + java.util.UUID.randomUUID().toString().take(8)
                        )
                    }
                    V2ActionCardSmall("SOS Mesh", "Emergency signals", Icons.Default.Emergency, Modifier.weight(1f)) { onNavigate("emergency") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    V2ActionCardSmall("Analytics", "Mesh performance", Icons.Default.BarChart, Modifier.weight(1f)) { onNavigate("analytics") }
                    V2ActionCardSmall("Advanced", "System engine", Icons.Default.SettingsSuggest, Modifier.weight(1f)) { onNavigate("advanced") }
                }
            }
        }

        item {
            V2ActionCardLarge(
                "Mesh Network Status",
                "Internal engine state and BLE telemetry.",
                Icons.Default.CellTower,
                onNavigate = { onNavigate("advanced") }
            )
        }

        // 4. Recent Discoveries
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text("Recent Discoveries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (discoveries.isNotEmpty()) {
                    Text("Live", style = MaterialTheme.typography.labelSmall, color = Color(0xFF22C55E), fontWeight = FontWeight.Bold)
                }
            }
        }

        if (discoveries.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ) {
                    Text(
                        "No nearby beacons detected yet.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            items(discoveries.take(8)) { discovery ->
                DiscoveryRowPremium(discovery)
            }
        }
        
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun UserProfileCard(isDemo: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(64.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(
                    if (isDemo) "Aditya Andhalkar" else "Unregistered User",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Verified, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        if (isDemo) "Reputation: 984 | Elite Recoverer" else "Join LostLink to build reputation",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveryRowPremium(enriched: com.silentnet.lostlink.repository.EnrichedObservation) {
    val discovery = enriched.observation
    val profile = enriched.profile
    val asset = enriched.asset

    val displayName = profile?.username ?: asset?.assetName ?: "Unknown Beacon"
    val subName = if (profile != null) profile.deviceName else if (asset != null) asset.assetType else discovery.beaconId.take(12) + "..."
    val isLost = asset?.isLost == true

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = if (isLost) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(44.dp).background(
                    if (isLost) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) 
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.05f), 
                    RoundedCornerShape(14.dp)
                ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        isLost -> Icons.Default.Warning
                        profile != null -> Icons.Default.Smartphone
                        asset != null -> Icons.Default.Inventory
                        else -> Icons.Default.Bluetooth
                    }, 
                    null, 
                    modifier = Modifier.size(20.dp), 
                    tint = if (isLost) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${discovery.rssi} dBm",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (discovery.rssi > -70) Color(0xFF22C55E) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    timeAgo(discovery.lastSeen),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun V2ActionCardSmall(
    title: String,
    description: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onNavigate: () -> Unit
) {
    Card(
        onClick = onNavigate,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium)
            Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun V2ActionCardLarge(
    title: String,
    description: String,
    icon: ImageVector,
    onNavigate: () -> Unit
) {
    Card(
        onClick = onNavigate,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.secondary)
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyLarge)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
        }
    }
}

private fun timeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        else -> "${diff / 3600000}h ago"
    }
}

