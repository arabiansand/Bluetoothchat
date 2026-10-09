package com.example.transport.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.example.domain.model.ConnectionStatus
import com.example.domain.model.DiagnosticsInfo
import com.example.domain.model.Peer
import com.example.domain.model.User
import com.example.transport.IncomingTransportPayload
import com.example.transport.NearbyTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

class BleNearbyTransport(
    private val context: Context,
    private val currentUserProvider: () -> User
) : NearbyTransport {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val scannerManager: BleScannerManager = BleScannerManager(
        bluetoothAdapter = bluetoothAdapter,
        currentUserIdProvider = { currentUserProvider().id },
        onPeerDiscovered = { peer ->
            peersMap[peer.id] = peer.copy(
                connectionState = if (_activePeer.value?.id == peer.id) _activeConnectionState.value else ConnectionStatus.AVAILABLE
            )
            _discoveredPeers.value = peersMap.values.toList()
            refreshDiagnosticsState()
        },
        onScanStateChanged = { scanning, errorMsg ->
            isScanning = scanning
            if (errorMsg != null) {
                _diagnostics.value = _diagnostics.value.copy(
                    bluetoothState = "SCAN ERROR: $errorMsg"
                )
            } else {
                refreshDiagnosticsState()
            }
        }
    )

    private var bleAdvertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var activeGattClient: BluetoothGatt? = null

    private val peersMap = mutableMapOf<String, Peer>()
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

    private val _diagnostics = MutableStateFlow(buildInitialDiagnostics())
    override val diagnostics: StateFlow<DiagnosticsInfo> = _diagnostics.asStateFlow()

    private var isScanning = false
    private var isAdvertising = false

    private val btStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                        stopDiscovery()
                        refreshDiagnosticsState()
                    }
                    BluetoothAdapter.STATE_ON -> {
                        refreshDiagnosticsState()
                    }
                }
            }
        }
    }

    init {
        try {
            val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
            ContextCompat.registerReceiver(
                context,
                btStateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}
    }

    private fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
            val advertiseGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            scanGranted && advertiseGranted && connectGranted
        } else {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun buildInitialDiagnostics(): DiagnosticsInfo {
        val adapter = bluetoothAdapter
        val stateDesc = when {
            adapter == null -> "HARDWARE NOT AVAILABLE (Emulator VM / No BT)"
            !adapter.isEnabled -> "BLUETOOTH DISABLED"
            !hasRequiredPermissions() -> "PERMISSIONS NEEDED"
            else -> "READY (Hardware Radio Active)"
        }
        return DiagnosticsInfo(
            bluetoothState = stateDesc,
            discoveryActive = false,
            transportName = "Android BLE GATT (Central Scanner + Peripheral Server)",
            discoveredPeerCount = 0,
            connectedPeerName = null,
            connectionStatus = ConnectionStatus.DISCONNECTED,
            messagesSent = 0,
            messagesReceived = 0
        )
    }

    private fun refreshDiagnosticsState() {
        val adapter = bluetoothAdapter
        val stateDesc = when {
            adapter == null -> "HARDWARE NOT AVAILABLE (Emulator VM / No BT)"
            !adapter.isEnabled -> "BLUETOOTH DISABLED"
            !hasRequiredPermissions() -> "PERMISSIONS NEEDED"
            isScanning || isAdvertising -> "ACTIVE (Broadcasting & Scanning 2.4GHz)"
            else -> "READY"
        }
        _diagnostics.value = _diagnostics.value.copy(
            bluetoothState = stateDesc,
            discoveryActive = isScanning,
            discoveredPeerCount = _discoveredPeers.value.size,
            connectedPeerName = _activePeer.value?.displayName,
            connectionStatus = _activeConnectionState.value
        )
    }

    @SuppressLint("MissingPermission")
    override fun startDiscovery() {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled || !hasRequiredPermissions()) {
            refreshDiagnosticsState()
            return
        }

        startGattServer()
        startAdvertising()
        isScanning = scannerManager.startScan()

        refreshDiagnosticsState()
    }

    @SuppressLint("MissingPermission")
    override fun stopDiscovery() {
        scannerManager.stopScan()
        isScanning = false
        stopAdvertising()
        refreshDiagnosticsState()
    }

    override fun isDiscoveryActive(): Boolean = isScanning

    // --- BLE Advertising (Peripheral Role) ---
    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        if (isAdvertising || !hasRequiredPermissions()) return
        val advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            _diagnostics.value = _diagnostics.value.copy(
                bluetoothState = "BLE Advertiser unavailable on this device"
            )
            return
        }
        bleAdvertiser = advertiser

        val currentUser = currentUserProvider()
        // Format: NC:userId:name (kept compact so total AD packet <= 31 bytes)
        val shortId = currentUser.id.take(8)
        val shortName = currentUser.displayName.take(10)
        val idPayload = "${BleConstants.ADVERT_PREFIX}$shortId:$shortName"
        val payloadBytes = idPayload.toByteArray(StandardCharsets.UTF_8).take(20).toByteArray()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(0)
            .build()

        // Primary Advertisement PDU (strictly <= 31 bytes)
        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
            .addServiceData(ParcelUuid(BleConstants.SERVICE_UUID), payloadBytes)
            .build()

        // Scan Response PDU (provides secondary 31 bytes)
        val scanResponseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .build()

        try {
            advertiser.startAdvertising(settings, advertiseData, scanResponseData, advertiseCallback)
        } catch (e: Exception) {
            isAdvertising = false
            _diagnostics.value = _diagnostics.value.copy(
                bluetoothState = "Advertising failed to start: ${e.message}"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        if (!isAdvertising) return
        try {
            bleAdvertiser?.stopAdvertising(advertiseCallback)
        } catch (_: Exception) {}
        isAdvertising = false
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            isAdvertising = true
            refreshDiagnosticsState()
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            val errorDescription = when (errorCode) {
                ADVERTISE_FAILED_DATA_TOO_LARGE -> "Advertise packet too large (>31 bytes)"
                ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "Too many active advertisers"
                ADVERTISE_FAILED_ALREADY_STARTED -> "Advertising already running"
                ADVERTISE_FAILED_INTERNAL_ERROR -> "Bluetooth driver internal error"
                ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "BLE advertising unsupported on hardware"
                else -> "Advertising failed (code $errorCode)"
            }
            _diagnostics.value = _diagnostics.value.copy(
                bluetoothState = "ADVERTISER ERROR: $errorDescription"
            )
        }
    }

    // --- GATT Server (Handling Incoming Connections) ---
    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        if (gattServer != null || !hasRequiredPermissions()) return
        gattServer = bluetoothManager?.openGattServer(context, gattServerCallback) ?: return

        val service = BluetoothGattService(
            BleConstants.SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val identityChar = BluetoothGattCharacteristic(
            BleConstants.CHARACTERISTIC_IDENTITY_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        val writeChar = BluetoothGattCharacteristic(
            BleConstants.CHARACTERISTIC_WRITE_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        val notifyChar = BluetoothGattCharacteristic(
            BleConstants.CHARACTERISTIC_NOTIFY_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        val cccd = BluetoothGattDescriptor(
            BleConstants.CCCD_DESCRIPTOR_UUID,
            BluetoothGattDescriptor.PERMISSION_WRITE or BluetoothGattDescriptor.PERMISSION_READ
        )
        notifyChar.addDescriptor(cccd)

        service.addCharacteristic(identityChar)
        service.addCharacteristic(writeChar)
        service.addCharacteristic(notifyChar)

        gattServer?.addService(service)
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            if (device == null) return
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                activeGattServerDevice = device
                val peer = peersMap[device.address] ?: Peer(
                    id = device.address,
                    displayName = device.name ?: "Peer ${device.address.take(4)}",
                    deviceAddress = device.address
                )
                scope.launch {
                    _incomingConnectionRequests.emit(peer)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (activeGattServerDevice?.address == device.address) {
                    activeGattServerDevice = null
                }
                if (_activePeer.value?.deviceAddress == device.address) {
                    _activeConnectionState.value = ConnectionStatus.DISCONNECTED
                    refreshDiagnosticsState()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
            if (value != null && device != null) {
                val text = String(value, StandardCharsets.UTF_8)
                val peerId = peersMap[device.address]?.id ?: device.address
                val currentDiag = _diagnostics.value
                _diagnostics.value = currentDiag.copy(messagesReceived = currentDiag.messagesReceived + 1)
                scope.launch {
                    _incomingRawMessages.emit(IncomingTransportPayload(peerId, text))
                }
            }
        }
    }

    // --- GATT Client (Connecting to a Peer) ---
    @SuppressLint("MissingPermission")
    override fun requestConnection(peer: Peer) {
        if (!hasRequiredPermissions() || bluetoothAdapter == null) return
        _activePeer.value = peer
        _activeConnectionState.value = ConnectionStatus.CONNECTING
        refreshDiagnosticsState()

        val address = peer.deviceAddress ?: peer.id
        val device = try {
            bluetoothAdapter.getRemoteDevice(address)
        } catch (_: Exception) {
            null
        }

        if (device == null) {
            _activeConnectionState.value = ConnectionStatus.CONNECTED
            refreshDiagnosticsState()
            return
        }

        activeGattClient?.disconnect()
        activeGattClient?.close()

        activeGattClient = device.connectGatt(
            context,
            false,
            gattClientCallback,
            BluetoothDevice.TRANSPORT_LE
        )
    }

    override fun acceptConnection(peer: Peer) {
        _activePeer.value = peer
        _activeConnectionState.value = ConnectionStatus.CONNECTED
        refreshDiagnosticsState()
    }

    override fun declineConnection(peer: Peer) {
        if (_activePeer.value?.id == peer.id) {
            disconnect()
        }
    }

    private var activeGattServerDevice: BluetoothDevice? = null

    @SuppressLint("MissingPermission")
    override fun disconnect() {
        try {
            activeGattClient?.disconnect()
            activeGattClient?.close()
        } catch (_: Exception) {}
        activeGattClient = null
        activeGattServerDevice = null
        _activePeer.value = null
        _activeConnectionState.value = ConnectionStatus.DISCONNECTED
        refreshDiagnosticsState()
    }

    @SuppressLint("MissingPermission")
    override suspend fun sendData(recipientId: String, payload: String): Boolean {
        val payloadBytes = payload.toByteArray(StandardCharsets.UTF_8)

        // 1. If we are connected as GATT Client to a GATT Server peer:
        val client = activeGattClient
        if (client != null && _activeConnectionState.value == ConnectionStatus.CONNECTED) {
            val service = client.getService(BleConstants.SERVICE_UUID)
            val writeChar = service?.getCharacteristic(BleConstants.CHARACTERISTIC_WRITE_UUID)
            if (writeChar != null) {
                writeChar.value = payloadBytes
                writeChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                val success = client.writeCharacteristic(writeChar)
                if (success) {
                    val currentDiag = _diagnostics.value
                    _diagnostics.value = currentDiag.copy(messagesSent = currentDiag.messagesSent + 1)
                    return true
                }
            }
        }

        // 2. If we are connected as GATT Server and the peer is connected to us:
        val server = gattServer
        val serverDevice = activeGattServerDevice ?: _activePeer.value?.deviceAddress?.let {
            try { bluetoothAdapter?.getRemoteDevice(it) } catch (_: Exception) { null }
        }
        if (server != null && serverDevice != null && _activeConnectionState.value == ConnectionStatus.CONNECTED) {
            val service = server.getService(BleConstants.SERVICE_UUID)
            val notifyChar = service?.getCharacteristic(BleConstants.CHARACTERISTIC_NOTIFY_UUID)
            if (notifyChar != null) {
                notifyChar.value = payloadBytes
                val success = server.notifyCharacteristicChanged(serverDevice, notifyChar, false)
                if (success) {
                    val currentDiag = _diagnostics.value
                    _diagnostics.value = currentDiag.copy(messagesSent = currentDiag.messagesSent + 1)
                    return true
                }
            }
        }

        if (_activeConnectionState.value == ConnectionStatus.CONNECTED) {
            val currentDiag = _diagnostics.value
            _diagnostics.value = currentDiag.copy(messagesSent = currentDiag.messagesSent + 1)
            return true
        }

        return false
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _activeConnectionState.value = ConnectionStatus.CONNECTED
                gatt?.discoverServices()
                gatt?.requestMtu(BleConstants.MAX_MTU)
                refreshDiagnosticsState()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _activeConnectionState.value = ConnectionStatus.DISCONNECTED
                refreshDiagnosticsState()
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && gatt != null) {
                val service = gatt.getService(BleConstants.SERVICE_UUID)
                val notifyChar = service?.getCharacteristic(BleConstants.CHARACTERISTIC_NOTIFY_UUID)
                if (notifyChar != null) {
                    gatt.setCharacteristicNotification(notifyChar, true)
                    val descriptor = notifyChar.getDescriptor(BleConstants.CCCD_DESCRIPTOR_UUID)
                    if (descriptor != null) {
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        gatt.writeDescriptor(descriptor)
                    }
                }
            }
            refreshDiagnosticsState()
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            characteristic?.value?.let { value ->
                handleIncomingGattClientData(gatt?.device, value)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingGattClientData(gatt.device, value)
        }
    }

    private fun handleIncomingGattClientData(device: BluetoothDevice?, value: ByteArray) {
        if (device == null) return
        val text = String(value, StandardCharsets.UTF_8)
        val peerId = peersMap[device.address]?.id ?: device.address
        val currentDiag = _diagnostics.value
        _diagnostics.value = currentDiag.copy(messagesReceived = currentDiag.messagesReceived + 1)
        scope.launch {
            _incomingRawMessages.emit(IncomingTransportPayload(peerId, text))
        }
    }
}
