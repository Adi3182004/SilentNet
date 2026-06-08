package com.silentnet.analytics

import android.content.Context
import com.silentnet.app.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object MeshReportExporter {
    suspend fun generateReport(context: Context, graph: AppGraph): File = withContext(Dispatchers.IO) {
        val stats = graph.analyticsManager.dashboardStats.value
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val file = File(context.cacheDir, "mesh_report_${System.currentTimeMillis()}.txt")
        
        val content = buildString {
            appendLine("SilentNet Mesh Intelligence Report")
            appendLine("Generated: ${dateFormat.format(Date())}")
            appendLine("======================================")
            appendLine()
            appendLine("NETWORK STATISTICS")
            appendLine("Active Nodes: ${stats.activeNodes}")
            appendLine("Direct Neighbors: ${stats.directNeighbors}")
            appendLine("Known Routes: ${stats.knownRoutes}")
            appendLine("Mesh Health Score: ${stats.healthScore.toInt()}% (${stats.healthLabel})")
            appendLine()
            appendLine("TRAFFIC STATISTICS")
            appendLine("Pending Packets: ${stats.pendingPackets}")
            appendLine("Store-and-Forward Queue: ${stats.storeForwardQueueSize}")
            appendLine("Emergency Queue: ${stats.emergencyQueueSize}")
            appendLine()
            appendLine("COMMUNITY STATISTICS")
            appendLine("Groups Joined: ${stats.groupCount}")
            appendLine("Recovery Posts: ${stats.recoveryPostCount}")
            appendLine()
            appendLine("ROUTING TABLE SUMMARY")
            val table = graph.transportManager.getRoutingTable()
            table.forEach { (target, routes) ->
                appendLine("- Target: $target")
                routes.forEach { (nextHop, route) ->
                    appendLine("  -> Next Hop: $nextHop (Hops: ${route.hopCount}, Confidence: ${(route.deliveryConfidence * 100).toInt()}%)")
                }
            }
            appendLine()
            appendLine("======================================")
            appendLine("End of Report")
        }
        
        file.writeText(content)
        file
    }
}
