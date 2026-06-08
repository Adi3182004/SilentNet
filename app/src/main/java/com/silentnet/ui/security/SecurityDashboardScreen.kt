package com.silentnet.ui.security

import androidx.compose.foundation.background
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silentnet.app.AppGraph

@Composable
fun SecurityDashboardScreen(graph: AppGraph) {
    val viewModel: SecurityViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return SecurityViewModel(graph.sessionManager, graph.identityManager, graph.database) as T
            }
        }
    )
    val report by viewModel.report.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        SecurityScoreHeader(report.score)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            "Security Audit Details",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
        )
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(report.items) { item ->
                SecurityItemCard(item)
            }
        }
    }
}

@Composable
private fun SecurityScoreHeader(score: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (score >= 80) Color(0xFFE8F5E9) else if (score >= 60) Color(0xFFFFF3E0) else Color(0xFFFFEBEE)
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (score >= 80) Color(0xFF2E7D32) else if (score >= 60) Color(0xFFEF6C00) else Color(0xFFC62828)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$score%",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black
                )
            }
            
            Spacer(modifier = Modifier.width(20.dp))
            
            Column {
                Text(
                    "Security Score",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black.copy(alpha = 0.8f)
                )
                Text(
                    text = if (score >= 80) "Hardened" else if (score >= 60) "Standard Protection" else "Action Required",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Black.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun SecurityItemCard(item: SecurityStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (item.isSecure) Icons.Default.VerifiedUser else Icons.Default.GppMaybe,
                    contentDescription = null,
                    tint = if (item.isSecure) Color(0xFF2E7D32) else Color(0xFFC62828),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = if (item.isSecure) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        item.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (item.isSecure) Color(0xFF2E7D32) else Color(0xFFC62828),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                item.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}
