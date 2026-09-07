package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.EmergencyRequest
import com.example.ui.theme.*
import com.example.viewmodel.SyncStatus

@Composable
fun MapScreen(
    activeRequests: List<EmergencyRequest>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🗺️", fontSize = 48.sp)

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Map Visualization",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Coordinates Only View",
            color = TextSecondary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (activeRequests.isEmpty()) {
            Text(
                "No active requests to map.",
                color = TextSecondary
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(activeRequests) { req ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = Surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${req.type} @ [${req.latitude}, ${req.longitude}]",
                            modifier = Modifier.padding(16.dp),
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    resolvedRequests: List<EmergencyRequest>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp)
    ) {
        Text(
            "Resolved Requests",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (resolvedRequests.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No resolved requests history.",
                    color = TextSecondary
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(resolvedRequests) { req ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = ResolvedBg
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                req.type.name,
                                fontWeight = FontWeight.Bold,
                                color = ResolvedText
                            )

                            Text(
                                "Resolved by: ${req.reporterId}",
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    nodeId: String,
    serverUrl: String,
    syncStatus: SyncStatus,
    onServerUrlChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
            .padding(16.dp)
    ) {
        // Profile header
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(
                        BrandPrimary,
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("👤", fontSize = 36.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Volunteer Node",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "ID: $nodeId",
                color = TextSecondary,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Cloud Settings
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Cloud Settings",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    "Server URL",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = onServerUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = {
                        Text("Server URL")
                    },
                    placeholder = {
                        Text("https://example.com/")
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = when (syncStatus) {
                        SyncStatus.SUCCESS ->
                            "Last sync: Successful"

                        SyncStatus.FAILED ->
                            "Last sync failed"

                        SyncStatus.IN_PROGRESS ->
                            "Sync in progress..."
                    },
                    color = when (syncStatus) {
                        SyncStatus.SUCCESS -> ResolvedText
                        SyncStatus.FAILED -> MaterialTheme.colorScheme.error
                        SyncStatus.IN_PROGRESS -> TextSecondary
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Network stats
        Card(
            colors = CardDefaults.cardColors(
                containerColor = Surface
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Network Stats",
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Transmissions Deduplicated: Local",
                    fontSize = 14.sp,
                    color = TextSecondary
                )

                Text(
                    "Connection Interface: DTN Wi-Fi Direct / BLE",
                    fontSize = 14.sp,
                    color = TextSecondary
                )
            }
        }
    }
}
