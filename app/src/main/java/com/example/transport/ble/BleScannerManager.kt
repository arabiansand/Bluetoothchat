package com.example.transport.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import com.example.domain.model.ConnectionStatus
import com.example.domain.model.Peer
import java.nio.charset.StandardCharsets

class BleScannerManager(
    private val bluetoothAdapter: BluetoothAdapter?,
    private val currentUserIdProvider: () -> String,
    private val onPeerDiscovered: (Peer) -> Unit,
    private val onScanStateChanged: (isScanning: Boolean, errorMsg: String?) -> Unit
) {

    private var scanner: BluetoothLeScanner? = null
    var isScanning: Boolean = false
        private set

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result ?: return
            val peer = parseScanResult(result, currentUserIdProvider())
            if (peer != null) {
                onPeerDiscovered(peer)
            }
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            results ?: return
            for (result in results) {
                val peer = parseScanResult(result, currentUserIdProvider())
                if (peer != null) {
                    onPeerDiscovered(peer)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            isScanning = false
            val errorDescription = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed with Bluetooth stack"
                SCAN_FAILED_INTERNAL_ERROR -> "Bluetooth driver internal error"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE scanning unsupported on device"
                else -> "Scan error code: $errorCode"
            }
            onScanStateChanged(false, errorDescription)
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(): Boolean {
        if (isScanning) return true
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            onScanStateChanged(false, "Bluetooth is disabled or unavailable")
            return false
        }

        scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            onScanStateChanged(false, "Bluetooth LE Scanner is null")
            return false
        }

        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()

            // Filters list: targeted service UUID filter + fallback open filter
            // so devices with service data or secondary scan response packets are reliably detected.
            val filters = listOf(
                ScanFilter.Builder()
                    .setServiceUuid(ParcelUuid(BleConstants.SERVICE_UUID))
                    .build(),
                ScanFilter.Builder()
                    .build()
            )

            isScanning = true
            scanner?.startScan(filters, settings, scanCallback)
            onScanStateChanged(true, null)
            return true
        } catch (e: Exception) {
            isScanning = false
            onScanStateChanged(false, "Failed to start BLE scan: ${e.message}")
            return false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!isScanning) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (_: Exception) {}
        isScanning = false
        onScanStateChanged(false, null)
    }

    companion object {
        @SuppressLint("MissingPermission")
        fun parseScanResult(result: ScanResult, currentUserId: String): Peer? {
            val device = result.device ?: return null
            val scanRecord = result.scanRecord

            val deviceAddr = device.address ?: return null
            var peerId = deviceAddr
            var peerDisplayName: String? = null
            var isVerifiedNearbyChat = false

            // 1. Inspect service data payload for NearbyChat prefix
            val serviceData = scanRecord?.getServiceData(ParcelUuid(BleConstants.SERVICE_UUID))
            if (serviceData != null) {
                val dataStr = String(serviceData, StandardCharsets.UTF_8)
                if (dataStr.startsWith(BleConstants.ADVERT_PREFIX)) {
                    isVerifiedNearbyChat = true
                    val trimmed = dataStr.removePrefix(BleConstants.ADVERT_PREFIX)
                    val parts = trimmed.split(":")
                    if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                        peerId = parts[0]
                    }
                    if (parts.size > 1 && parts[1].isNotBlank()) {
                        peerDisplayName = parts[1]
                    }
                }
            }

            // 2. Check Service UUID list in advertisement
            val serviceUuids = scanRecord?.serviceUuids
            if (serviceUuids != null && serviceUuids.any { it.uuid == BleConstants.SERVICE_UUID }) {
                isVerifiedNearbyChat = true
            }

            // 3. Fallback name from ScanRecord or Device object
            if (peerDisplayName.isNullOrBlank()) {
                val recordName = scanRecord?.deviceName
                val deviceName = try { device.name } catch (_: Exception) { null }
                peerDisplayName = recordName ?: deviceName
            }

            // Exclude self discovery
            if (peerId == currentUserId || deviceAddr == currentUserId) return null

            val finalName = when {
                !peerDisplayName.isNullOrBlank() -> peerDisplayName
                isVerifiedNearbyChat -> "NearbyChat User (${peerId.take(4)})"
                else -> "Nearby Device (${deviceAddr.take(5)})"
            }

            return Peer(
                id = peerId,
                displayName = finalName,
                connectionState = ConnectionStatus.AVAILABLE,
                lastSeen = System.currentTimeMillis(),
                deviceAddress = deviceAddr,
                rssi = result.rssi
            )
        }
    }
}
