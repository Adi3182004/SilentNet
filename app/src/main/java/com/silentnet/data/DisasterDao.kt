package com.silentnet.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DisasterDao {

    // Missing Persons
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMissingPerson(person: MissingPersonEntity)

    @Query("SELECT * FROM missing_persons ORDER BY timestamp DESC")
    fun observeAllMissingPersons(): Flow<List<MissingPersonEntity>>

    @Query("SELECT * FROM missing_persons WHERE status = :status ORDER BY timestamp DESC")
    fun observeMissingPersonsByStatus(status: String): Flow<List<MissingPersonEntity>>

    @Query("SELECT * FROM missing_persons WHERE personName LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchMissingPersons(query: String): Flow<List<MissingPersonEntity>>

    @Query("SELECT COUNT(*) FROM missing_persons")
    suspend fun getMissingPersonCount(): Int

    // Safe Zones
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSafeZone(zone: SafeZoneEntity)

    @Query("SELECT * FROM safe_zones ORDER BY timestamp DESC")
    fun observeAllSafeZones(): Flow<List<SafeZoneEntity>>

    @Query("SELECT COUNT(*) FROM safe_zones")
    suspend fun getSafeZoneCount(): Int

    // Medical Assistance
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicalRequest(request: MedicalAssistanceEntity)

    @Query("SELECT * FROM medical_assistance ORDER BY urgency DESC, timestamp DESC")
    fun observeAllMedicalRequests(): Flow<List<MedicalAssistanceEntity>>

    @Query("SELECT COUNT(*) FROM medical_assistance")
    suspend fun getMedicalRequestCount(): Int

    // Resources
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResource(resource: ResourceEntity)

    @Query("SELECT * FROM disaster_resources ORDER BY timestamp DESC")
    fun observeAllResources(): Flow<List<ResourceEntity>>

    @Query("SELECT * FROM disaster_resources WHERE category = :category ORDER BY timestamp DESC")
    fun observeResourcesByCategory(category: String): Flow<List<ResourceEntity>>

    @Query("SELECT * FROM disaster_resources WHERE title LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchResources(query: String): Flow<List<ResourceEntity>>

    @Query("SELECT COUNT(*) FROM disaster_resources")
    suspend fun getResourceCount(): Int

    // Volunteers
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVolunteer(volunteer: VolunteerEntity)

    @Query("SELECT * FROM volunteers ORDER BY timestamp DESC")
    fun observeAllVolunteers(): Flow<List<VolunteerEntity>>

    @Query("SELECT COUNT(*) FROM volunteers")
    suspend fun getVolunteerCount(): Int

    @Query("SELECT COUNT(*) FROM volunteers WHERE availability = 'Active'")
    suspend fun getActiveVolunteerCount(): Int

    // Incident Reports
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIncidentReport(report: IncidentReportEntity)

    @Query("SELECT * FROM incident_reports ORDER BY priority DESC, timestamp DESC")
    fun observeAllIncidentReports(): Flow<List<IncidentReportEntity>>

    @Query("SELECT COUNT(*) FROM incident_reports")
    suspend fun getIncidentCount(): Int
}
