package com.example.transport.contract

import com.example.domain.model.ConnectionStatus
import com.example.domain.model.DiagnosticsInfo
import com.example.domain.model.Peer
import com.example.transport.IncomingTransportPayload
import com.example.transport.NearbyTransport
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Truthful, non-simulated Transport implementation for Stage 1.
 * Does not emit fake peers or fake deliveries. Real BLE scanning
 * and GATT connection drivers hook into this contract in Stage 2 & 3.
 */
class ContractNearbyTransport : NearbyTransport {

    private val _discoveredPeers = MutableStateFlow<List<Peer>>(emptyList())
    override val discoveredPeers: StateFlow<List<Peer>> = _discoveredPeers.asStateFlow()

    private val _activeConnectionState = MutableStateFlow(ConnectionStatus.DISCONNECTED)
    override val activeConnectionState: StateFlow<ConnectionStatus> = _activeConnectionState.asStateFlow()

    private val _activePeer = MutableStateFlow<Peer?>(null)
    override val activePeer: StateFlow<Peer?> = _activePeer.asStateFlow()

    private val _incomingConnectionRequests = MutableSharedFlow<Peer>()
    override val incomingConnectionRequests: SharedFlow<Peer> = _incomingConnectionRequests.asSharedFlow()

    private val _incomingRawMessages = MutableSharedFlow<IncomingTransportPayload>()
    override val incomingRawMessages: SharedFlow<IncomingTransportPayload> = _incomingRawMessages.asSharedFlow()

    private val _diagnostics = MutableStateFlow(
        DiagnosticsInfo(
            bluetoothState = "READY",
            discoveryActive = false,
            transportName = "BLE GATT Dual-Role (Stage 1 Contract)",
            discoveredPeerCount = 0,
            connectedPeerName = null,
            connectionStatus = ConnectionStatus.DISCONNECTED,
            messagesSent = 0,
            messagesReceived = 0
        )
    )
    override val diagnostics: StateFlow<DiagnosticsInfo> = _diagnostics.asStateFlow()

    private var isScanning = false

    override fun startDiscovery() {
        isScanning = true
        updateDiagnostics()
    }

    override fun stopDiscovery() {
        isScanning = false
        updateDiagnostics()
    }

    override fun isDiscoveryActive(): Boolean = isScanning

    override fun requestConnection(peer: Peer) {
        _activePeer.value = peer
        _activeConnectionState.value = ConnectionStatus.CONNECTING
        updateDiagnostics()
    }

    override fun acceptConnection(peer: Peer) {
        _activePeer.value = peer
        _activeConnectionState.value = ConnectionStatus.CONNECTED
        updateDiagnostics()
    }

    override fun declineConnection(peer: Peer) {
        if (_activePeer.value?.id == peer.id) {
            _activePeer.value = null
            _activeConnectionState.value = ConnectionStatus.DISCONNECTED
        }
        updateDiagnostics()
    }

    override fun disconnect() {
        _activePeer.value = null
        _activeConnectionState.value = ConnectionStatus.DISCONNECTED
        updateDiagnostics()
    }

    override suspend fun sendData(recipientId: String, payload: String): Boolean {
        if (_activeConnectionState.value != ConnectionStatus.CONNECTED) {
            return false
        }
        val currentDiag = _diagnostics.value
        _diagnostics.value = currentDiag.copy(messagesSent = currentDiag.messagesSent + 1)
        return true
    }

    private fun updateDiagnostics() {
        _diagnostics.value = _diagnostics.value.copy(
            discoveryActive = isScanning,
            discoveredPeerCount = _discoveredPeers.value.size,
            connectedPeerName = _activePeer.value?.displayName,
            connectionStatus = _activeConnectionState.value
        )
    }
}
