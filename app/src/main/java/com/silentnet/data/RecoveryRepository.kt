package com.silentnet.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RecoveryRepository(private val recoveryDao: RecoveryDao) {

    fun observeAllPosts(): Flow<List<RecoveryPostEntity>> = recoveryDao.observeAllPosts()
    suspend fun getLostReports(deviceId: String): List<LostLinkReportEntity> =
        withContext(Dispatchers.IO) {
            recoveryDao.getLostReports(deviceId)
        }

    fun observePostsByCategory(category: String): Flow<List<RecoveryPostEntity>> = recoveryDao.observePostsByCategory(category)

    fun searchPosts(query: String): Flow<List<RecoveryPostEntity>> = recoveryDao.searchPosts(query)

    suspend fun insertPost(post: RecoveryPostEntity) = withContext(Dispatchers.IO) {
        recoveryDao.insertPost(post)
    }

    suspend fun postExists(postId: String): Boolean = withContext(Dispatchers.IO) {
        recoveryDao.postExists(postId)
    }

    // LostLink Methods
    suspend fun insertLostDevice(device: LostDeviceEntity) = withContext(Dispatchers.IO) {
        recoveryDao.insertLostDevice(device)
    }

    fun observeLostDevices(): Flow<List<LostDeviceEntity>> = recoveryDao.observeLostDevices()

    suspend fun findLostDeviceById(deviceId: String): LostDeviceEntity? = withContext(Dispatchers.IO) {
        recoveryDao.findLostDeviceById(deviceId)
    }

    suspend fun updateLostStatus(deviceId: String, isLost: Boolean) = withContext(Dispatchers.IO) {
        recoveryDao.updateLostStatus(deviceId, isLost)
    }

    suspend fun updateAnonymousId(deviceId: String, anonymousId: String?) = withContext(Dispatchers.IO) {
        recoveryDao.updateAnonymousId(deviceId, anonymousId)
    }

    suspend fun insertLostReport(report: LostLinkReportEntity) = withContext(Dispatchers.IO) {
        recoveryDao.insertLostReport(report)
    }

    fun observeReportsForDevice(deviceId: String): Flow<List<LostLinkReportEntity>> = recoveryDao.observeReportsForDevice(deviceId)

    fun observeAllLostReports(): Flow<List<LostLinkReportEntity>> = recoveryDao.observeAllLostReports()

    suspend fun insertSearch(query: String) = withContext(Dispatchers.IO) {
        recoveryDao.insertSearch(SearchHistoryEntity(query = query))
    }

    fun observeSearchHistory(): Flow<List<SearchHistoryEntity>> = recoveryDao.observeSearchHistory()

    suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        recoveryDao.clearSearchHistory()
    }

    suspend fun insertSearchResult(result: SearchResultEntity) = withContext(Dispatchers.IO) {
        recoveryDao.insertSearchResult(result)
    }

    fun observeSearchResults(queryId: String): Flow<List<SearchResultEntity>> = recoveryDao.observeSearchResults(queryId)

    suspend fun cleanupOldSearchResults(cutoff: Long) = withContext(Dispatchers.IO) {
        recoveryDao.cleanupOldSearchResults(cutoff)
    }

    // LostLink Intelligence
    suspend fun calculateRecoveryConfidence(reports: List<LostLinkReportEntity>): Double = withContext(Dispatchers.IO) {
        if (reports.isEmpty()) return@withContext 0.0
        
        // 1. RSSI Factor (Stronger signal = higher confidence)
        val avgRssi = reports.map { it.rssi }.average()
        val rssiScore = ((avgRssi + 100) / 70.0).coerceIn(0.0, 1.0)
        
        // 2. Frequency Factor (More reports = higher confidence)
        val frequencyScore = (reports.size / 10.0).coerceIn(0.0, 1.0)
        
        // 3. Recency Factor (Recent reports = higher confidence)
        val now = System.currentTimeMillis()
        val latestReportTime = reports.maxOf { it.timestamp }
        val recencyScore = (1.0 / (1.0 + (now - latestReportTime) / 3600000.0)).coerceIn(0.0, 1.0)
        
        // 4. Multi-node Factor (Detections by multiple nodes = higher confidence)
        val uniqueReporters = reports.map { it.reporterNodeId }.distinct().size
        val multiNodeScore = (uniqueReporters / 5.0).coerceIn(0.0, 1.0)
        
        (rssiScore * 0.4 + frequencyScore * 0.2 + recencyScore * 0.2 + multiNodeScore * 0.2)
    }

    suspend fun estimateLastSeen(reports: List<LostLinkReportEntity>): String = withContext(Dispatchers.IO) {
        if (reports.isEmpty()) return@withContext "Unknown"
        
        val latest = reports.maxBy { it.timestamp }
        val strongest = reports.maxBy { it.rssi }
        
        val now = System.currentTimeMillis()
        val diffMin = (now - latest.timestamp) / 60000
        
        val timeDesc = when {
            diffMin < 1 -> "Just now"
            diffMin < 60 -> "$diffMin mins ago"
            else -> "${diffMin / 60} hours ago"
        }
        
        val locationDesc = latest.nodeInfo ?: "Nearby Node ${latest.reporterNodeId.take(6)}"
        
        "Last seen $timeDesc near $locationDesc (RSSI: ${strongest.rssi} dBm)"
    }

    fun getRecoveryTimeline(reports: List<LostLinkReportEntity>): List<String> {
        if (reports.isEmpty()) return emptyList()
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return reports.sortedByDescending { it.timestamp }.map { report ->
            "${sdf.format(report.timestamp)}: Detected by ${report.reporterNodeId.take(6)} (${report.rssi} dBm)"
        }
    }
}
