package com.example.transport

import com.example.domain.model.ConnectionStatus
import com.example.domain.model.DiagnosticsInfo
import com.example.domain.model.Peer
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface NearbyTransport {
    val discoveredPeers: StateFlow<List<Peer>>
    val activeConnectionState: StateFlow<ConnectionStatus>
    val activePeer: StateFlow<Peer?>
    val incomingConnectionRequests: SharedFlow<Peer>
    val incomingRawMessages: SharedFlow<IncomingTransportPayload>
    val diagnostics: StateFlow<DiagnosticsInfo>

    fun startDiscovery()
    fun stopDiscovery()
    fun isDiscoveryActive(): Boolean
    fun requestConnection(peer: Peer)
    fun acceptConnection(peer: Peer)
    fun declineConnection(peer: Peer)
    fun disconnect()
    suspend fun sendData(recipientId: String, payload: String): Boolean
}

data class IncomingTransportPayload(
    val senderId: String,
    val payload: String
)
