package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mesh.MeshPeer
import com.example.ui.theme.*

@Composable
fun NetworkScreen(
    nodeId: String,
    discoveredPeers: List<MeshPeer>,
    connectedPeers: List<MeshPeer>,
    onConnect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .statusBarsPadding()
        ) {
            Text(
                text = "Mesh Network",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF1F5F9))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("MY NODE ID", fontSize = 10.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                    Text(nodeId, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                }
                Box(
                    modifier = Modifier
                        .background(SuccessEmerald.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        "BROADCASTING",
                        color = SuccessEmerald,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        HorizontalDivider(color = Color(0xFFE2E8F0))

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "CONNECTED PEERS (${connectedPeers.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
            }
            if (connectedPeers.isEmpty()) {
                item {
                    Text(
                        text = "No active connections.",
                        fontSize = 14.sp,
                        color = TextTertiary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(connectedPeers) { peer ->
                    PeerCard(peer = peer, isConnected = true, onConnect = {})
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "DISCOVERED PEERS (${discoveredPeers.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
            }
            if (discoveredPeers.isEmpty()) {
                item {
                    Text(
                        text = "Scanning for nearby devices...",
                        fontSize = 14.sp,
                        color = TextTertiary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else {
                items(discoveredPeers) { peer ->
                    val isConnected = connectedPeers.any { it.id == peer.id }
                    if (!isConnected) {
                        PeerCard(peer = peer, isConnected = false, onConnect = { onConnect(peer.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun PeerCard(peer: MeshPeer, isConnected: Boolean, onConnect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(if (isConnected) SuccessEmerald.copy(alpha = 0.1f) else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "📱",
                    fontSize = 18.sp
                )
            }
            Column {
                Text(
                    text = peer.name,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "Endpoint ID: ${peer.id}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
        
        if (isConnected) {
            Text(
                "CONNECTED",
                color = SuccessEmerald,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(containerColor = BrandPrimary),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text("Connect", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
