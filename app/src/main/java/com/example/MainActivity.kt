package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = { BottomNavBar() },
                    floatingActionButton = { CustomFab() },
                    floatingActionButtonPosition = FabPosition.Center
                ) { innerPadding ->
                    MeshLinkScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MeshLinkScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Header()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IntelligenceCard()
            RequestsList()
        }
    }
}

@Composable
fun Header() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface)
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .statusBarsPadding(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(BrandPrimary),
                contentAlignment = Alignment.Center
            ) {
                Text("M", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            Column {
                Text(
                    text = "MeshLink",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    lineHeight = 22.sp
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(SuccessEmerald))
                    Text(
                        text = "MESH ACTIVE • 12 PEERS",
                        color = Color(0xFF059669),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
        IconButton(
            onClick = { },
            modifier = Modifier
                .size(40.dp)
                .background(Color(0xFFF8FAFC), CircleShape)
        ) {
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = TextTertiary)
        }
    }
    Divider(color = Color(0xFFF1F5F9))
}

@Composable
fun IntelligenceCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(HighlightBg)
            .border(1.dp, HighlightBorder, RoundedCornerShape(28.dp))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = "LOCAL SITUATIONAL INTELLIGENCE",
                color = HighlightText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp
            )
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "CELL: 7Q9Y",
                    color = HighlightText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatBox(value = "34", label = "Active Requests", modifier = Modifier.weight(1f))
            StatBox(value = "8.4", label = "Area Severity Score", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📍", fontSize = 14.sp)
            Text(
                text = "High concentration of Medical requests near Market St. sector.",
                color = HighlightSubtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.4f))
            .padding(12.dp)
    ) {
        Text(text = value, color = HighlightText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = HighlightSubtitle, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun RequestsList() {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Nearby Emergency Requests",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "FILTER BY PRIORITY",
                color = BrandPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.clickable { }
            )
        }
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item { RequestItem(title = "Medical Supply: Insulin", priority = "Critical", details = "200m away • Sector 4B • Ref: #A92", type = "medical") }
            item { RequestItem(title = "Potable Water Need", priority = "Urgent", details = "450m away • Aggregated (5 reports)", type = "water") }
            item { RequestItem(title = "Blocked Road Clearance", priority = "Resolved", details = "Stopped propagation across mesh", type = "resolved") }
        }
    }
}

@Composable
fun RequestItem(title: String, priority: String, details: String, type: String) {
    val attrs = when(type) {
        "medical" -> listOf("+", CriticalText, CriticalIconBg, CriticalText, CriticalBg, 1f)
        "water" -> listOf("💧", UrgentText, UrgentIconBg, UrgentText, UrgentBg, 1f)
        else -> listOf("✓", ResolvedText, ResolvedIconBg, ResolvedText, ResolvedBg, 0.6f)
    }
    val icon = attrs[0] as String
    val iconColor = attrs[1] as Color
    val iconBg = attrs[2] as Color
    val tagColor = attrs[3] as Color
    val tagBg = attrs[4] as Color
    val alpha = attrs[5] as Float

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface.copy(alpha = alpha))
            .border(1.dp, Color(0xFFE2E8F0).copy(alpha = alpha), RoundedCornerShape(16.dp))
            .padding(16.dp)
            .clickable { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(iconBg as Color),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon as String, color = iconColor as Color, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .background(tagBg as Color, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = priority.uppercase(),
                        color = tagColor as Color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = details, color = TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun BottomNavBar() {
    NavigationBar(
        containerColor = Surface,
        contentColor = TextSecondary,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = true,
            onClick = { },
            icon = { Text("📋", fontSize = 20.sp) },
            label = { Text("REQUESTS", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BrandPrimary,
                selectedTextColor = BrandPrimary,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = false,
            onClick = { },
            icon = { Text("🗺️", fontSize = 20.sp) },
            label = { Text("MAP", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        )
        Spacer(modifier = Modifier.weight(1f)) // Space for FAB
        NavigationBarItem(
            selected = false,
            onClick = { },
            icon = { Text("🔄", fontSize = 20.sp) },
            label = { Text("HISTORY", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        )
        NavigationBarItem(
            selected = false,
            onClick = { },
            icon = { Text("👤", fontSize = 20.sp) },
            label = { Text("PROFILE", fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        )
    }
}

@Composable
fun CustomFab() {
    FloatingActionButton(
        onClick = { },
        containerColor = BrandPrimary,
        contentColor = Color.White,
        shape = CircleShape,
        modifier = Modifier.offset(y = 36.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(32.dp))
    }
}

@Preview(showBackground = true)
@Composable
fun MeshLinkPreview() {
    MyApplicationTheme {
        MeshLinkScreen()
    }
}
