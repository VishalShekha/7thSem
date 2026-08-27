package com.example

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.core.AreaSummary
import com.example.core.Category
import com.example.core.EmergencyRequest
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.MapScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.theme.*
import com.example.viewmodel.MeshViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: MeshViewModel = viewModel()
                
                MeshAppContent(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MeshAppContent(viewModel: MeshViewModel) {
    val context = LocalContext.current
    val permissions = mutableListOf<String>()
    
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        permissions.add(Manifest.permission.BLUETOOTH_SCAN)
    } else {
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }

    val permissionsState = rememberMultiplePermissionsState(permissions)

    var hasHardwareEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(permissionsState.allPermissionsGranted, hasHardwareEnabled) {
        if (permissionsState.allPermissionsGranted && hasHardwareEnabled) {
            viewModel.startMesh()
        }
    }

    if (!permissionsState.allPermissionsGranted) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Permissions Required", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "MeshLink needs Nearby Devices, Bluetooth, and Location permissions to discover and connect to peers offline.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                Text("Grant Permissions")
            }
            if (permissionsState.shouldShowRationale) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Please grant these permissions in settings if you've permanently denied them.",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
        return
    }

    // Check hardware states
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    val isBluetoothEnabled = bluetoothManager.adapter?.isEnabled == true
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val isWifiEnabled = wifiManager.isWifiEnabled

    hasHardwareEnabled = isBluetoothEnabled && isWifiEnabled

    if (!hasHardwareEnabled) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Enable Radios", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Please enable both Bluetooth and Wi-Fi so MeshLink can form the offline network.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (!isBluetoothEnabled) {
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }) {
                    Text("Enable Bluetooth")
                }
            }
            if (!isWifiEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }) {
                    Text("Enable Wi-Fi")
                }
            }
        }
        return
    }

    // Main App UI
    val navController = rememberNavController()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { BottomNavBar(navController) },
        floatingActionButton = { 
            CustomFab { showAddDialog = true } 
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "requests",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("requests") {
                RequestsScreen(viewModel)
            }
            composable("map") {
                val active = viewModel.activeRequests.collectAsState().value
                MapScreen(activeRequests = active)
            }
            composable("history") {
                val resolved = viewModel.resolvedRequests.collectAsState().value
                HistoryScreen(resolvedRequests = resolved)
            }
            composable("network") {
                val discovered = viewModel.discoveredPeers.collectAsState().value
                val connected = viewModel.connectedPeersList.collectAsState().value
                com.example.ui.screens.NetworkScreen(
                    nodeId = viewModel.nodeId,
                    discoveredPeers = discovered,
                    connectedPeers = connected,
                    onConnect = { viewModel.connectToPeer(it) }
                )
            }
        }

        if (showAddDialog) {
            AddRequestDialog(
                onDismiss = { showAddDialog = false },
                onSubmit = { type, lat, lon, severity ->
                    viewModel.createNewRequest(type, lat, lon, severity)
                    showAddDialog = false
                }
            )
        }
    }
}

@Composable
fun RequestsScreen(viewModel: MeshViewModel) {
    val activeRequests by viewModel.activeRequests.collectAsState()
    val areaSummaries by viewModel.areaSummaries.collectAsState()
    val peers by viewModel.connectedPeersCount.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        Header(peers)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IntelligenceCard(activeRequests, areaSummaries)
            RequestsList(activeRequests, onResolve = { req -> viewModel.resolveRequest(req) })
        }
    }
}

@Composable
fun Header(peers: Int) {
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
                        text = "MESH ACTIVE • $peers PEERS",
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
    HorizontalDivider(color = Color(0xFFF1F5F9))
}

@Composable
fun IntelligenceCard(activeRequests: List<EmergencyRequest>, summaries: Map<String, AreaSummary>) {
    val totalSeverity = summaries.values.sumOf { it.totalSeverity }
    // Pick the most severe cell to display
    val mostSevereCell = summaries.maxByOrNull { it.value.totalSeverity }?.key ?: "N/A"
    
    val topCategory = summaries.values
        .flatMap { it.requestCounts.entries }
        .groupBy({ it.key }, { it.value })
        .mapValues { it.value.sum() }
        .maxByOrNull { it.value }?.key

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
                    text = "CELL: $mostSevereCell",
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
            StatBox(value = "${activeRequests.size}", label = "Active Requests", modifier = Modifier.weight(1f))
            StatBox(value = "$totalSeverity", label = "Area Severity Score", modifier = Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (topCategory != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📍", fontSize = 14.sp)
                Text(
                    text = "High concentration of ${topCategory.name} requests detected.",
                    color = HighlightSubtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                )
            }
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
fun RequestsList(requests: List<EmergencyRequest>, onResolve: (EmergencyRequest) -> Unit) {
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
        
        if (requests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No active requests.", color = TextSecondary)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(requests, key = { it.id }) { req ->
                    RequestItem(req, onResolve)
                }
            }
        }
    }
}

@Composable
fun RequestItem(request: EmergencyRequest, onResolve: (EmergencyRequest) -> Unit) {
    val isCritical = request.severity >= 8
    val icon = if (request.type == Category.MEDICAL) "+" else if (request.type == Category.WATER) "💧" else "⚠️"
    val iconColor = if (isCritical) CriticalText else UrgentText
    val iconBg = if (isCritical) CriticalIconBg else UrgentIconBg
    val tagColor = if (isCritical) CriticalText else UrgentText
    val tagBg = if (isCritical) CriticalBg else UrgentBg
    val priority = if (isCritical) "CRITICAL" else "URGENT"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface)
            .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
            .clickable { onResolve(request) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Text(text = icon, color = iconColor, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "${request.type.name} NEED",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                Box(
                    modifier = Modifier
                        .background(tagBg, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = priority,
                        color = tagColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Lat: ${request.latitude} | Lon: ${request.longitude}", 
                color = TextSecondary, 
                fontSize = 12.sp, 
                maxLines = 1, 
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BottomNavBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = Surface,
        contentColor = TextSecondary,
        tonalElevation = 8.dp
    ) {
        NavigationBarItem(
            selected = currentRoute == "requests",
            onClick = { navController.navigate("requests") { launchSingleTop = true; restoreState = true } },
            icon = { Text("📋", fontSize = 20.sp) },
            label = { Text("REQUESTS", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BrandPrimary,
                selectedTextColor = BrandPrimary,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = currentRoute == "map",
            onClick = { navController.navigate("map") { launchSingleTop = true; restoreState = true } },
            icon = { Text("🗺️", fontSize = 20.sp) },
            label = { Text("MAP", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BrandPrimary,
                selectedTextColor = BrandPrimary,
                indicatorColor = Color.Transparent
            )
        )
        Spacer(modifier = Modifier.weight(1f)) // Space for FAB
        NavigationBarItem(
            selected = currentRoute == "history",
            onClick = { navController.navigate("history") { launchSingleTop = true; restoreState = true } },
            icon = { Text("🔄", fontSize = 20.sp) },
            label = { Text("HISTORY", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BrandPrimary,
                selectedTextColor = BrandPrimary,
                indicatorColor = Color.Transparent
            )
        )
        NavigationBarItem(
            selected = currentRoute == "network",
            onClick = { navController.navigate("network") { launchSingleTop = true; restoreState = true } },
            icon = { Text("📡", fontSize = 20.sp) },
            label = { Text("NETWORK", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = BrandPrimary,
                selectedTextColor = BrandPrimary,
                indicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
fun CustomFab(onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = BrandPrimary,
        contentColor = Color.White,
        shape = CircleShape,
        modifier = Modifier.offset(y = 36.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = "Add Request", modifier = Modifier.size(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRequestDialog(
    onDismiss: () -> Unit,
    onSubmit: (Category, Double, Double, Int) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(Category.MEDICAL) }
    var severity by remember { mutableStateOf(5f) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Emergency Request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Category:")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.values().take(3).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.name) }
                        )
                    }
                }
                Text("Severity (1-10): ${severity.toInt()}")
                Slider(
                    value = severity,
                    onValueChange = { severity = it },
                    valueRange = 1f..10f,
                    steps = 8
                )
            }
        },
        confirmButton = {
            Button(onClick = { 
                // Using dummy coordinates for simplicity representing the local device
                val dummyLat = 34.0522 + (Math.random() * 0.01)
                val dummyLon = -118.2437 + (Math.random() * 0.01)
                onSubmit(selectedCategory, dummyLat, dummyLon, severity.toInt()) 
            }) {
                Text("Broadcast")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
