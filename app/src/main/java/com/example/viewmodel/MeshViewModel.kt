package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.*
import com.example.data.AppDatabase
import com.example.data.EmergencyRequestEntity
import com.example.mesh.MeshTransportAdapter
import com.example.mesh.NearbyConnectionsTransportAdapter
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

class MeshViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val requestDao = db.requestDao()
    
    // Core module isolated from Android
    private val coreModule = InformationProcessingModule()
    
    // Transport layer
    private val transport: MeshTransportAdapter = NearbyConnectionsTransportAdapter(application)
    
    private val _activeRequests = MutableStateFlow<List<EmergencyRequest>>(emptyList())
    val activeRequests: StateFlow<List<EmergencyRequest>> = _activeRequests
    
    private val _resolvedRequests = MutableStateFlow<List<EmergencyRequest>>(emptyList())
    val resolvedRequests: StateFlow<List<EmergencyRequest>> = _resolvedRequests

    private val _areaSummaries = MutableStateFlow<Map<String, AreaSummary>>(emptyMap())
    val areaSummaries: StateFlow<Map<String, AreaSummary>> = _areaSummaries
    
    val connectedPeers = transport.connectedPeersCount.stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val nodeId = UUID.randomUUID().toString().take(8)

    init {
        // 1. Observe local DB and feed into core module to restore state
        viewModelScope.launch {
            requestDao.getAllRequests().collect { entities ->
                val requests = entities.map { it.toDomainModel() }
                
                requests.forEach { coreModule.processIncomingRequest(it) }
                
                updateUiState()
            }
        }
        
        // 2. Listen for incoming mesh network requests
        viewModelScope.launch {
            transport.incomingRequests.collect { req ->
                // The core module decides if this is new/important
                if (coreModule.processIncomingRequest(req)) {
                    // Cache locally if it's new
                    requestDao.insertRequest(req.toEntity())
                    updateUiState()
                }
            }
        }
    }

    fun startMesh() {
        viewModelScope.launch {
            transport.startAdvertisingAndDiscovering()
        }
    }

    private fun updateUiState() {
        val all = coreModule.getAllRequests()
        _activeRequests.value = all.filter { it.status != RequestStatus.RESOLVED }.sortedByDescending { it.severity }
        _resolvedRequests.value = all.filter { it.status == RequestStatus.RESOLVED }.sortedByDescending { it.timestamp }
        _areaSummaries.value = coreModule.getAreaSummaries()
    }
    
    fun createNewRequest(type: Category, lat: Double, lon: Double, severity: Int) {
        val req = EmergencyRequest(
            id = UUID.randomUUID().toString(),
            type = type,
            latitude = lat,
            longitude = lon,
            timestamp = System.currentTimeMillis(),
            status = RequestStatus.CREATED,
            severity = severity,
            reporterId = nodeId
        )
        viewModelScope.launch {
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
