package com.example.mesh

import android.content.Context
import android.util.Log
import com.example.core.EmergencyRequest
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

data class MeshPeer(val id: String, val name: String)

interface MeshTransportAdapter {
    val incomingRequests: Flow<EmergencyRequest>
    val connectedPeersCount: Flow<Int>
    val discoveredPeers: Flow<List<MeshPeer>>
    val connectedPeers: Flow<List<MeshPeer>>
    
    fun startAdvertisingAndDiscovering()
    fun connectToPeer(endpointId: String)
    fun broadcastRequest(request: EmergencyRequest)
    fun stop()
}

class NearbyConnectionsTransportAdapter(private val context: Context) : MeshTransportAdapter {
    private val TAG = "MeshTransport"
    
    private val SERVICE_ID = "com.example.meshlink.SERVICE_ID"
    private val STRATEGY = Strategy.P2P_CLUSTER
    private val myEndpointName = UUID.randomUUID().toString().take(6)
    
    private val _incomingRequests = MutableSharedFlow<EmergencyRequest>(extraBufferCapacity = 100)
    override val incomingRequests: Flow<EmergencyRequest> = _incomingRequests
    
    private val _connectedPeersCount = MutableStateFlow(0)
    override val connectedPeersCount: Flow<Int> = _connectedPeersCount
    
    private val _discoveredPeers = MutableStateFlow<List<MeshPeer>>(emptyList())
    override val discoveredPeers: Flow<List<MeshPeer>> = _discoveredPeers
    
    private val _connectedPeers = MutableStateFlow<List<MeshPeer>>(emptyList())
    override val connectedPeers: Flow<List<MeshPeer>> = _connectedPeers
    
    private val connectedEndpoints = mutableSetOf<String>()
    
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val jsonAdapter = moshi.adapter(EmergencyRequest::class.java)

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    val json = String(bytes)
                    try {
                        val request = jsonAdapter.fromJson(json)
                        if (request != null) {
                            Log.d(TAG, "Received request ${request.id} from $endpointId")
                            _incomingRequests.tryEmit(request)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to parse payload from $endpointId", e)
                    }
                }
            }
        }
        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated by $endpointId")
            Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d(TAG, "Connected to $endpointId")
                connectedEndpoints.add(endpointId)
                _connectedPeersCount.value = connectedEndpoints.size
                
                val peer = _discoveredPeers.value.find { it.id == endpointId } ?: MeshPeer(endpointId, "Unknown")
                val currentList = _connectedPeers.value.toMutableList()
                if (!currentList.any { it.id == endpointId }) {
                    currentList.add(peer)
                    _connectedPeers.value = currentList
                }
            } else {
                Log.w(TAG, "Connection failed to $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            connectedEndpoints.remove(endpointId)
            _connectedPeersCount.value = connectedEndpoints.size
            
            _connectedPeers.value = _connectedPeers.value.filter { it.id != endpointId }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Found endpoint: $endpointId (${info.endpointName})")
            val newPeer = MeshPeer(endpointId, info.endpointName)
            val currentList = _discoveredPeers.value.toMutableList()
            if (!currentList.any { it.id == endpointId }) {
                currentList.add(newPeer)
                _discoveredPeers.value = currentList
            }
            
            // Auto request
            Nearby.getConnectionsClient(context).requestConnection(
                myEndpointName,
                endpointId,
                connectionLifecycleCallback
            )
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Lost endpoint: $endpointId")
            _discoveredPeers.value = _discoveredPeers.value.filter { it.id != endpointId }
        }
    }
    
    override fun connectToPeer(endpointId: String) {
        Nearby.getConnectionsClient(context).requestConnection(
            myEndpointName,
            endpointId,
            connectionLifecycleCallback
        ).addOnSuccessListener {
            Log.d(TAG, "Manual connection request sent to $endpointId")
        }.addOnFailureListener {
            Log.e(TAG, "Manual connection request failed", it)
        }
    }

    override fun startAdvertisingAndDiscovering() {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(context).startAdvertising(
            myEndpointName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        )

        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(context).startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        )
    }

    override fun broadcastRequest(request: EmergencyRequest) {
        if (connectedEndpoints.isEmpty()) {
            Log.d(TAG, "No peers connected to broadcast to.")
            return
        }
        
        try {
            val json = jsonAdapter.toJson(request)
            val payload = Payload.fromBytes(json.toByteArray())
            Nearby.getConnectionsClient(context).sendPayload(connectedEndpoints.toList(), payload)
        } catch (e: Exception) {
            Log.e(TAG, "Error serializing request", e)
        }
    }

    override fun stop() {
        Nearby.getConnectionsClient(context).stopAdvertising()
        Nearby.getConnectionsClient(context).stopDiscovery()
        Nearby.getConnectionsClient(context).stopAllEndpoints()
        connectedEndpoints.clear()
        _connectedPeersCount.value = 0
        _discoveredPeers.value = emptyList()
        _connectedPeers.value = emptyList()
    }
}
