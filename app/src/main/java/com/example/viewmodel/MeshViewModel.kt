package com.example.viewmodel

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.*
import com.example.data.AppDatabase
import com.example.data.EmergencyRequestEntity
import com.example.mesh.MeshTransportAdapter
import com.example.mesh.NearbyConnectionsTransportAdapter
import com.example.network.NetworkMonitor
import com.example.network.RetrofitClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

enum class SyncStatus {
    SUCCESS,
    FAILED,
    IN_PROGRESS
}

@OptIn(FlowPreview::class)
class MeshViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val requestDao = db.requestDao()

    // Core module isolated from Android
    private val coreModule = InformationProcessingModule()

    // Transport layer
    private val transport: MeshTransportAdapter =
        NearbyConnectionsTransportAdapter(application)

    // Real GPS location
    private val locationProvider = LocationProvider(application)

    // Network monitor for Cloud Sync
    private val networkMonitor = NetworkMonitor(application)

    val isOnline: StateFlow<Boolean> =
        networkMonitor.isOnline.stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            false
        )

    private val _activeRequests =
        MutableStateFlow<List<EmergencyRequest>>(emptyList())
    val activeRequests: StateFlow<List<EmergencyRequest>> = _activeRequests

    private val _resolvedRequests =
        MutableStateFlow<List<EmergencyRequest>>(emptyList())
    val resolvedRequests: StateFlow<List<EmergencyRequest>> = _resolvedRequests

    private val _areaSummaries =
        MutableStateFlow<Map<String, AreaSummary>>(emptyMap())
    val areaSummaries: StateFlow<Map<String, AreaSummary>> = _areaSummaries

    // Cloud sync status surfaced to the UI
    private val _syncStatus =
        MutableStateFlow(SyncStatus.SUCCESS)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    val connectedPeersCount =
        transport.connectedPeersCount.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            0
        )

    val discoveredPeers =
        transport.discoveredPeers.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        )

    val connectedPeersList =
        transport.connectedPeers.stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        )

    // Mesh startup failures -> UI
    private val _meshStartupErrors =
        MutableSharedFlow<String>(extraBufferCapacity = 10)
    val meshStartupErrors: SharedFlow<String> = _meshStartupErrors

    // Request creation failures (e.g. no GPS) -> UI
    private val _requestErrors =
        MutableSharedFlow<String>(extraBufferCapacity = 5)
    val requestErrors: SharedFlow<String> = _requestErrors

    // Stable ID: same value every time the app starts
    val nodeId: String = DeviceIdentity.getId(application)

    // Benchmark helper (logs with tag "BENCH")
    private val bench = BenchRunner(transport, viewModelScope, locationProvider, nodeId)

    init {
        // 1. Observe local DB and feed into core module to restore state
        viewModelScope.launch {
            requestDao.getAllRequests().collect { entities ->
                val requests = entities.map { it.toDomainModel() }

                requests.forEach {
                    coreModule.processIncomingRequest(it)
                }

                updateUiState()
            }
        }

        // 2. Listen for incoming mesh network requests
        viewModelScope.launch {
            transport.incomingRequests.collect { req ->
                // Benchmark packets (PING/PONG/LOSS) are handled separately
                if (bench.handleIncoming(req)) return@collect

                val t0 = SystemClock.elapsedRealtimeNanos()
                if (coreModule.processIncomingRequest(req)) {
                    requestDao.insertRequest(req.toEntity())
                    updateUiState()

                    // Flood-fill across the mesh
                    transport.broadcastRequest(req)

                    BenchLog.log(
                        "PROC",
                        "id" to req.id.take(8),
                        "ms" to ((SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0)
                    )
                }
            }
        }

        // Benchmark: log when peers are discovered / connected
        viewModelScope.launch {
            transport.discoveredPeers.collect { list ->
                BenchLog.log("DISCOVERED", "n" to list.size, "peers" to list.toString())
            }
        }
        viewModelScope.launch {
            transport.connectedPeers.collect { list ->
                BenchLog.log("CONNECTED", "n" to list.size, "peers" to list.toString())
            }
        }

        // 3. AUTO SYNC: whenever we are online AND the DB changes, push to the
        //    cloud. Retries with backoff if it fails. No app restart needed.
        viewModelScope.launch {
            combine(isOnline, requestDao.getAllRequests()) { online, entities ->
                online to entities
            }
                .debounce(1_000) // batch bursts of requests into one sync
                .collectLatest { (online, entities) ->
                    if (!online || entities.isEmpty()) return@collectLatest

                    val requests = entities.map { it.toDomainModel() }
                    var attempt = 0

                    // collectLatest cancels this loop when new data / network state arrives
                    while (!syncToCloud(requests)) {
                        attempt++
                        delay(minOf(30_000L, 2_000L * (1 shl attempt.coerceAtMost(4))))
                    }
                }
        }

        // 4. Relay mesh transport startup failures to the UI layer
        viewModelScope.launch {
            transport.startupErrors.collect { message ->
                Log.e("MeshViewModel", "Mesh startup error: $message")
                _meshStartupErrors.tryEmit(message)
            }
        }
    }

    /** Update the cloud server base URL. */
    fun updateServerUrl(url: String) {
        RetrofitClient.setBaseUrl(getApplication(), url)
    }

    /** Get the currently configured cloud server base URL. */
    fun getServerUrl(): String {
        return RetrofitClient.getBaseUrl(getApplication())
    }

    private suspend fun syncToCloud(requests: List<EmergencyRequest>): Boolean {
        _syncStatus.value = SyncStatus.IN_PROGRESS
        return try {
            val baseUrl = RetrofitClient.getBaseUrl(getApplication())
            Log.d("CloudSync", "Syncing ${requests.size} request(s) to $baseUrl")

            val response = RetrofitClient
                .getApi(getApplication())
                .syncRequests(requests)

            Log.d("CloudSync", "HTTP ${response.code()} | success=${response.isSuccessful}")

            if (response.isSuccessful) {
                _syncStatus.value = SyncStatus.SUCCESS
                true
            } else {
                Log.e("CloudSync", "SYNC FAILED: ${response.errorBody()?.string()}")
                _syncStatus.value = SyncStatus.FAILED
                false
            }
        } catch (e: CancellationException) {
            throw e // don't swallow cancellation from collectLatest
        } catch (e: Exception) {
            Log.e("CloudSync", "Failed to sync to cloud", e)
            _syncStatus.value = SyncStatus.FAILED
            false
        }
    }

    fun startMesh() {
        viewModelScope.launch {
            BenchLog.log("MESH_START")
            transport.startAdvertisingAndDiscovering()
        }
    }

    fun connectToPeer(endpointId: String) {
        viewModelScope.launch {
            BenchLog.log("CONNECT_REQ", "endpoint" to endpointId)
            transport.connectToPeer(endpointId)
        }
    }

    // ---- Benchmark triggers (temporary, remove after collecting data) ----
    fun benchPing() = bench.runPing()
    fun benchLoss() = bench.runLoss()
    fun benchGps() = bench.runGps()

    private fun updateUiState() {
        val all = coreModule.getAllRequests()

        _activeRequests.value =
            all.filter { it.status != RequestStatus.RESOLVED }
                .sortedByDescending { it.severity }

        _resolvedRequests.value =
            all.filter { it.status == RequestStatus.RESOLVED }
                .sortedByDescending { it.timestamp }

        _areaSummaries.value = coreModule.getAreaSummaries()
    }

    /** Creates a request using the phone's REAL location. */
    fun createNewRequest(
        type: Category,
        severity: Int
    ) {
        viewModelScope.launch {
            val loc = locationProvider.getCurrentLocation()

            if (loc == null) {
                _requestErrors.tryEmit(
                    "Couldn't get your location. Turn on GPS and allow location permission."
                )
                return@launch
            }

            val req = EmergencyRequest(
                id = UUID.randomUUID().toString(),
                type = type,
                latitude = loc.latitude,
                longitude = loc.longitude,
                timestamp = System.currentTimeMillis(),
                status = RequestStatus.CREATED,
                severity = severity,
                reporterId = nodeId
            )

            if (coreModule.processIncomingRequest(req)) {
                requestDao.insertRequest(req.toEntity())
                updateUiState()
                transport.broadcastRequest(req)
            }
        }
    }

    fun resolveRequest(req: EmergencyRequest) {
        val resolved = req.copy(status = RequestStatus.RESOLVED)

        viewModelScope.launch {
            if (coreModule.processIncomingRequest(resolved)) {
                requestDao.insertRequest(resolved.toEntity())
                updateUiState()
                transport.broadcastRequest(resolved)
            }
        }
    }
}

fun EmergencyRequestEntity.toDomainModel() = EmergencyRequest(
    id = id,
    type = Category.valueOf(type),
    latitude = latitude,
    longitude = longitude,
    timestamp = timestamp,
    status = RequestStatus.valueOf(status),
    severity = severity,
    reporterId = reporterId
)

fun EmergencyRequest.toEntity() = EmergencyRequestEntity(
    id = id,
    type = type.name,
    latitude = latitude,
    longitude = longitude,
    timestamp = timestamp,
    status = status.name,
    severity = severity,
    reporterId = reporterId
)