package com.example

import com.example.domain.model.ConnectionStatus
import com.example.domain.model.DiagnosticsInfo
import com.example.domain.model.Message
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.Peer
import com.example.domain.model.User
import com.example.transport.ble.BleConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class ExampleUnitTest {

    @Test
    fun userModel_initialization_isCorrect() {
        val user = User(
            id = "a8f4c291",
            displayName = "User 4821"
        )
        assertEquals("a8f4c291", user.id)
        assertEquals("User 4821", user.displayName)
        assertTrue(user.createdAt > 0L)
    }

    @Test
    fun messageModel_statusTransitions_isCorrect() {
        val message = Message(
            id = "msg-123",
            conversationId = "conv_peer456",
            senderId = "a8f4c291",
            recipientId = "peer456",
            payload = "Hello from Android",
            status = MessageDeliveryStatus.SENDING
        )

        assertEquals(MessageDeliveryStatus.SENDING, message.status)

        val sentMessage = message.copy(status = MessageDeliveryStatus.SENT)
        assertEquals(MessageDeliveryStatus.SENT, sentMessage.status)

        val deliveredMessage = sentMessage.copy(status = MessageDeliveryStatus.DELIVERED)
        assertEquals(MessageDeliveryStatus.DELIVERED, deliveredMessage.status)
    }

    @Test
    fun peerModel_initialConnectionState_isAvailable() {
        val peer = Peer(
            id = "peer-001",
            displayName = "Alex",
            rssi = -64
        )
        assertEquals(ConnectionStatus.AVAILABLE, peer.connectionState)
        assertEquals(-64, peer.rssi)
    }

    @Test
    fun diagnosticsInfo_initialState_isTruthful() {
        val diag = DiagnosticsInfo()
        assertEquals(0, diag.discoveredPeerCount)
        assertEquals(0, diag.messagesSent)
        assertEquals(0, diag.messagesReceived)
        assertEquals(ConnectionStatus.DISCONNECTED, diag.connectionStatus)
    }

    @Test
    fun bleConstants_serviceUuidAndFraming_areValid() {
        assertNotNull(BleConstants.SERVICE_UUID)
        assertNotNull(BleConstants.CHARACTERISTIC_WRITE_UUID)
        assertNotNull(BleConstants.CHARACTERISTIC_NOTIFY_UUID)
        assertEquals("NC:", BleConstants.ADVERT_PREFIX)

        val payload = "${BleConstants.ADVERT_PREFIX}usr123:Alex"
        assertTrue(payload.startsWith(BleConstants.ADVERT_PREFIX))
        val parts = payload.removePrefix(BleConstants.ADVERT_PREFIX).split(":")
        assertEquals("usr123", parts[0])
        assertEquals("Alex", parts[1])
    }
}
