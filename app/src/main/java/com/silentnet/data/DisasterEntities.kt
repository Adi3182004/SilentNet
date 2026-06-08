package com.silentnet.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "missing_persons",
    indices = [Index(value = ["reportId"], unique = true), Index(value = ["status"])]
)
data class MissingPersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportId: String,
    val reporterNodeId: String,
    val personName: String,
    val age: String,
    val gender: String,
    val photoReference: String?, // Reference to local file or URI
    val description: String,
    val lastKnownLocation: String,
    val timestamp: Long,
    val status: String // Missing, Found, Safe, etc.
)

@Entity(
    tableName = "safe_zones",
    indices = [Index(value = ["zoneId"], unique = true)]
)
data class SafeZoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val zoneId: String,
    val title: String,
    val description: String,
    val capacity: Int,
    val occupancy: Int,
    val contactInfo: String,
    val timestamp: Long
)

@Entity(
    tableName = "medical_assistance",
    indices = [Index(value = ["requestId"], unique = true), Index(value = ["urgency"])]
)
data class MedicalAssistanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestId: String,
    val patientType: String,
    val urgency: String, // Critical, High, Normal
    val medicalNeed: String,
    val timestamp: Long
)

@Entity(
    tableName = "disaster_resources",
    indices = [Index(value = ["resourceId"], unique = true), Index(value = ["category"])]
)
data class ResourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val resourceId: String,
    val title: String,
    val category: String, // Food, Water, Shelter, Medicine, Charging Station, Communication Point
    val description: String,
    val location: String,
    val priorityTag: String, // High, Medium, Low
    val timestamp: Long
)

@Entity(
    tableName = "volunteers",
    indices = [Index(value = ["volunteerId"], unique = true)]
)
data class VolunteerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val volunteerId: String,
    val nodeId: String,
    val skills: String,
    val availability: String,
    val locationArea: String,
    val category: String, // Medical, Rescue, Logistics, Communication, General
    val timestamp: Long
)

@Entity(
    tableName = "incident_reports",
    indices = [Index(value = ["reportId"], unique = true), Index(value = ["type"])]
)
data class IncidentReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reportId: String,
    val type: String, // Flood, Fire, Earthquake, Building Collapse, Landslide, Medical Emergency, Security Threat
    val description: String,
    val location: String,
    val priority: Int, // 10 for emergency traffic as per requirement
    val timestamp: Long
)
