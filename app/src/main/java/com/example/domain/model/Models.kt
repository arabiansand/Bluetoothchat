package com.example.domain.model

enum class ConnectionStatus {
    AVAILABLE,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    RECONNECTING,
    FAILED
}

enum class MessageDeliveryStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

data class User(
    val id: String,
    val displayName: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class Peer(
    val id: String,
    val displayName: String,
    val connectionState: ConnectionStatus = ConnectionStatus.AVAILABLE,
    val lastSeen: Long = System.currentTimeMillis(),
    val deviceAddress: String? = null
)

data class Conversation(
    val id: String,
    val peerId: String,
    val peerDisplayName: String,
    val lastMessage: String? = null,
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0
)

data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "text",
    val payload: String,
    val status: MessageDeliveryStatus = MessageDeliveryStatus.SENDING
)

data class DiagnosticsInfo(
    val bluetoothState: String = "READY",
    val discoveryActive: Boolean = false,
    val transportName: String = "BLE GATT Dual-Role (Stage 1 Contract)",
    val discoveredPeerCount: Int = 0,
    val connectedPeerName: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val messagesSent: Int = 0,
    val messagesReceived: Int = 0
)
