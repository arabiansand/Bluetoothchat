package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.database.AppDatabase
import com.example.data.local.database.ConversationDao
import com.example.data.local.database.MessageDao
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomDatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var messageDao: MessageDao
    private lateinit var conversationDao: ConversationDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        messageDao = db.messageDao()
        conversationDao = db.conversationDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveMessages_withTimestampsAndSenderIds() = runBlocking {
        val convId = "conv_peer_alex"
        val senderA = "user_me_123"
        val senderB = "peer_alex_456"

        val t1 = 1700000000000L
        val t2 = 1700000005000L

        val message1 = MessageEntity(
            id = "msg_001",
            conversationId = convId,
            senderId = senderA,
            recipientId = senderB,
            timestamp = t1,
            type = "text",
            payload = "Hello Alex!",
            status = "DELIVERED"
        )

        val message2 = MessageEntity(
            id = "msg_002",
            conversationId = convId,
            senderId = senderB,
            recipientId = senderA,
            timestamp = t2,
            type = "text",
            payload = "Hi! Nice to meet you offline.",
            status = "DELIVERED"
        )

        messageDao.insertMessage(message1)
        messageDao.insertMessage(message2)

        // Verify retrieval ordered by timestamp ascending
        val messages = messageDao.getMessagesForConversation(convId).first()
        assertEquals(2, messages.size)
        assertEquals("msg_001", messages[0].id)
        assertEquals(senderA, messages[0].senderId)
        assertEquals(t1, messages[0].timestamp)

        assertEquals("msg_002", messages[1].id)
        assertEquals(senderB, messages[1].senderId)
        assertEquals(t2, messages[1].timestamp)

        // Query by senderId
        val senderAMessages = messageDao.getMessagesBySender(senderA).first()
        assertEquals(1, senderAMessages.size)
        assertEquals("msg_001", senderAMessages[0].id)

        // Update status
        messageDao.updateStatus("msg_001", "READ")
        val updated = messageDao.getMessageById("msg_001")
        assertNotNull(updated)
        assertEquals("READ", updated?.status)
    }

    @Test
    fun conversationDao_insertAndLinkLatestMessage() = runBlocking {
        val conv = ConversationEntity(
            id = "conv_alex",
            peerId = "peer_alex",
            peerDisplayName = "Alex",
            lastMessage = "Hey there!",
            lastMessageTimestamp = 1700000010000L
        )

        conversationDao.insertOrUpdate(conv)

        val retrieved = conversationDao.getConversationByPeerId("peer_alex")
        assertNotNull(retrieved)
        assertEquals("Alex", retrieved?.peerDisplayName)
        assertEquals(1700000010000L, retrieved?.lastMessageTimestamp)

        // Clear all
        conversationDao.clearAll()
        val empty = conversationDao.getConversationById("conv_alex")
        assertNull(empty)
    }
}
