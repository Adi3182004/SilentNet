package com.silentnet.ui.recovery

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.sp
import com.silentnet.app.AppGraph
import com.silentnet.data.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DisasterCoordinationScreen(graph: AppGraph) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Incidents", "Missing", "Medical", "Resources", "Safe Zones", "Volunteers")

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (selectedTab) {
                0 -> IncidentsTab(graph)
                1 -> MissingPersonsTab(graph)
                2 -> MedicalTab(graph)
                3 -> ResourcesTab(graph)
                4 -> SafeZonesTab(graph)
                5 -> VolunteersTab(graph)
            }
        }
    }
}

@Composable
private fun IncidentsTab(graph: AppGraph) {
    val incidents by graph.database.disasterDao().observeAllIncidentReports().collectAsState(initial = emptyList())
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(incidents) { incident ->
            DisasterCard(
                title = incident.type,
                subtitle = incident.location,
                description = incident.description,
                timestamp = incident.timestamp,
                priority = incident.priority,
                icon = Icons.Default.Warning
            )
        }
    }
}

@Composable
private fun MissingPersonsTab(graph: AppGraph) {
    val persons by graph.database.disasterDao().observeAllMissingPersons().collectAsState(initial = emptyList())
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(persons) { person ->
            DisasterCard(
                title = person.personName,
                subtitle = "Last seen: ${person.lastKnownLocation}",
                description = person.description,
                timestamp = person.timestamp,
                priority = if (person.status == "Missing") 2 else 0,
                icon = Icons.Default.PersonSearch,
                extraInfo = "Status: ${person.status}"
            )
        }
    }
}

@Composable
private fun MedicalTab(graph: AppGraph) {
    val requests by graph.database.disasterDao().observeAllMedicalRequests().collectAsState(initial = emptyList())
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(requests) { request ->
            DisasterCard(
                title = request.medicalNeed,
                subtitle = "Patient: ${request.patientType}",
                description = "Urgency: ${request.urgency}",
                timestamp = request.timestamp,
                priority = when (request.urgency) {
                    "Critical" -> 2
                    "High" -> 1
                    else -> 0
                },
                icon = Icons.Default.MedicalServices
            )
        }
    }
}

@Composable
private fun ResourcesTab(graph: AppGraph) {
    val resources by graph.database.disasterDao().observeAllResources().collectAsState(initial = emptyList())
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(resources) { resource ->
            DisasterCard(
                title = resource.title,
                subtitle = resource.category,
                description = resource.description,
                timestamp = resource.timestamp,
                priority = if (resource.priorityTag == "High") 1 else 0,
                icon = Icons.Default.Inventory
            )
        }
    }
}

@Composable
private fun SafeZonesTab(graph: AppGraph) {
    val zones by graph.database.disasterDao().observeAllSafeZones().collectAsState(initial = emptyList())
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(zones) { zone ->
            DisasterCard(
                title = zone.title,
                subtitle = "Capacity: ${zone.occupancy}/${zone.capacity}",
                description = zone.description,
                timestamp = zone.timestamp,
                priority = if (zone.occupancy >= zone.capacity) 1 else 0,
                icon = Icons.Default.HomeWork,
                extraInfo = "Contact: ${zone.contactInfo}"
            )
        }
    }
}

@Composable
private fun VolunteersTab(graph: AppGraph) {
    val volunteers by graph.database.disasterDao().observeAllVolunteers().collectAsState(initial = emptyList())
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(volunteers) { volunteer ->
            DisasterCard(
                title = volunteer.category,
                subtitle = "Node: ${volunteer.nodeId}",
                description = "Skills: ${volunteer.skills}",
                timestamp = volunteer.timestamp,
                priority = 0,
                icon = Icons.Default.VolunteerActivism,
                extraInfo = "Availability: ${volunteer.availability}"
            )
        }
    }
}

@Composable
private fun DisasterCard(
    title: String,
    subtitle: String,
    description: String,
    timestamp: Long,
    priority: Int,
    icon: ImageVector,
    extraInfo: String? = null
) {
    val priorityColor = when (priority) {
        10, 2 -> Color(0xFFFFEBEE) // Emergency / Critical
        1 -> Color(0xFFFFF3E0) // High
        else -> MaterialTheme.colorScheme.surface
    }
    val borderColor = when (priority) {
        10, 2 -> Color.Red
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
                Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(timestamp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium)
            extraInfo?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}
