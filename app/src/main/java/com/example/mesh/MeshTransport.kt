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

interface MeshTransportAdapter {
    val incomingRequests: Flow<EmergencyRequest>
    val connectedPeersCount: Flow<Int>
    
    fun startAdvertisingAndDiscovering()
    fun broadcastRequest(request: EmergencyRequest)
    fun stop()
}

class NearbyConnectionsTransportAdapter(private val context: Context) : MeshTransportAdapter {
    private val TAG = "MeshTransport"
    
    // We use a unique service ID for our application
    private val SERVICE_ID = "com.example.meshlink.SERVICE_ID"
    
    // Using P2P_CLUSTER for true mesh networking support where everyone can connect to everyone
    private val STRATEGY = Strategy.P2P_CLUSTER
    
    private val myEndpointName = UUID.randomUUID().toString()
    
    private val _incomingRequests = MutableSharedFlow<EmergencyRequest>(extraBufferCapacity = 100)
    override val incomingRequests: Flow<EmergencyRequest> = _incomingRequests
    
    private val _connectedPeersCount = MutableStateFlow(0)
    override val connectedPeersCount: Flow<Int> = _connectedPeersCount
    
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

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Not strictly needed for small payloads (like our emergency requests)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            Log.d(TAG, "Connection initiated by $endpointId")
            // Automatically accept the connection
            Nearby.getConnectionsClient(context).acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                Log.d(TAG, "Connected to $endpointId")
                connectedEndpoints.add(endpointId)
                _connectedPeersCount.value = connectedEndpoints.size
            } else {
                Log.w(TAG, "Connection failed to $endpointId")
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d(TAG, "Disconnected from $endpointId")
            connectedEndpoints.remove(endpointId)
            _connectedPeersCount.value = connectedEndpoints.size
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            Log.d(TAG, "Found endpoint: $endpointId")
            // Request connection when we find someone
            Nearby.getConnectionsClient(context).requestConnection(
                myEndpointName,
                endpointId,
                connectionLifecycleCallback
            ).addOnFailureListener {
                Log.e(TAG, "Failed to request connection to $endpointId", it)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d(TAG, "Lost endpoint: $endpointId")
        }
    }

    override fun startAdvertisingAndDiscovering() {
        startAdvertising()
        startDiscovery()
    }

    private fun startAdvertising() {
        val advertisingOptions = AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(context).startAdvertising(
            myEndpointName,
            SERVICE_ID,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnSuccessListener {
            Log.d(TAG, "Advertising started")
        }.addOnFailureListener {
            Log.e(TAG, "Advertising failed", it)
        }
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
        Nearby.getConnectionsClient(context).startDiscovery(
            SERVICE_ID,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnSuccessListener {
            Log.d(TAG, "Discovery started")
        }.addOnFailureListener {
            Log.e(TAG, "Discovery failed", it)
        }
    }

    override fun broadcastRequest(request: EmergencyRequest) {
        if (connectedEndpoints.isEmpty()) {
            Log.d(TAG, "No peers connected to broadcast to.")
            return
        }
        
        try {
            val json = jsonAdapter.toJson(request)
            val bytes = json.toByteArray()
            val payload = Payload.fromBytes(bytes)
            
            // Send to all connected endpoints
            Nearby.getConnectionsClient(context).sendPayload(connectedEndpoints.toList(), payload)
                .addOnSuccessListener {
                    Log.d(TAG, "Broadcast request ${request.id} to ${connectedEndpoints.size} peers")
                }
                .addOnFailureListener {
                    Log.e(TAG, "Failed to broadcast payload", it)
                }
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
    }
}
