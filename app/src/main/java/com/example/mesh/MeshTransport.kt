package com.example.mesh

import com.example.core.EmergencyRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

// Transport adapter interface implemented via Google Nearby Connections API conceptually
interface MeshTransportAdapter {
    val incomingRequests: Flow<EmergencyRequest>
    val connectedPeersCount: Flow<Int>
    
    suspend fun startAdvertisingAndDiscovering()
    suspend fun broadcastRequest(request: EmergencyRequest)
    fun stop()
}

// Stub implementation for UI development, since we can't run Nearby Connections in emulator easily
class NearbyConnectionsTransportAdapter : MeshTransportAdapter {
    private val _incomingRequests = MutableSharedFlow<EmergencyRequest>()
    override val incomingRequests: Flow<EmergencyRequest> = _incomingRequests
    
    private val _connectedPeersCount = MutableStateFlow(0)
    override val connectedPeersCount: Flow<Int> = _connectedPeersCount

    override suspend fun startAdvertisingAndDiscovering() {
        // Real app: Nearby.getConnectionsClient(context).startAdvertising(...)
        // Real app: Nearby.getConnectionsClient(context).startDiscovery(...)
    }

    override suspend fun broadcastRequest(request: EmergencyRequest) {
        // Real app: Nearby.getConnectionsClient(context).sendPayload(endpoints, Payload.fromBytes(bytes))
    }

    override fun stop() {
        // Real app: Nearby.getConnectionsClient(context).stopAllEndpoints()
    }
}
