package com.example.transport.ble

import java.util.UUID

object BleConstants {
    // 16-bit Compatible Bluetooth Base UUID for NearbyChat Service ("NC" = 0x4E43)
    val SERVICE_UUID: UUID = UUID.fromString("00004E43-0000-1000-8000-00805F9B34FB")

    // Characteristic for reading/broadcasting Peer Identity (User ID + Display Name)
    val CHARACTERISTIC_IDENTITY_UUID: UUID = UUID.fromString("00004E44-0000-1000-8000-00805F9B34FB")

    // Characteristic for writing incoming text/packet bytes
    val CHARACTERISTIC_WRITE_UUID: UUID = UUID.fromString("00004E45-0000-1000-8000-00805F9B34FB")

    // Characteristic for notifying outgoing messages / ACKs
    val CHARACTERISTIC_NOTIFY_UUID: UUID = UUID.fromString("00004E46-0000-1000-8000-00805F9B34FB")

    // Standard Client Characteristic Configuration Descriptor (CCCD) for notifications
    val CCCD_DESCRIPTOR_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Framing prefix for identifying NearbyChat advertisement payload
    const val ADVERT_PREFIX = "NC:"
    const val MAX_MTU = 512
}
