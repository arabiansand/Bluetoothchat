package com.example.data.repository

import com.example.data.local.database.ConversationDao
import com.example.data.local.database.MessageDao
import com.example.data.local.entity.ConversationEntity
import com.example.data.local.entity.MessageEntity
import com.example.domain.model.Conversation
import com.example.domain.model.Message
import com.example.domain.model.MessageDeliveryStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ChatRepository {
    fun getAllConversations(): Flow<List<Conversation>>
    fun getMessagesForConversation(conversationId: String): Flow<List<Message>>
    suspend fun saveMessage(message: Message)
    suspend fun updateMessageStatus(messageId: String, status: MessageDeliveryStatus)
    suspend fun getOrCreateConversation(peerId: String, peerDisplayName: String): Conversation
    suspend fun clearAll()
}

class ChatRepositoryImpl(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : ChatRepository {

    override fun getAllConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getMessagesForConversation(conversationId: String): Flow<List<Message>> {
        return messageDao.getMessagesForConversation(conversationId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getOrCreateConversation(peerId: String, peerDisplayName: String): Conversation {
        val existing = conversationDao.getConversationByPeerId(peerId)
        if (existing != null) {
            return existing.toDomain()
        }

        val id = "conv_$peerId"
        val newConv = ConversationEntity(
            id = id,
            peerId = peerId,
            peerDisplayName = peerDisplayName,
            lastMessage = null,
            lastMessageTimestamp = System.currentTimeMillis()
        )
        conversationDao.insertOrUpdate(newConv)
        return newConv.toDomain()
    }

    override suspend fun saveMessage(message: Message) {
        val messageEntity = MessageEntity(
            id = message.id,
            conversationId = message.conversationId,
            senderId = message.senderId,
            recipientId = message.recipientId,
            timestamp = message.timestamp,
            type = message.type,
            payload = message.payload,
            status = message.status.name
        )
        messageDao.insertMessage(messageEntity)

        // Also update or insert the conversation entity
        val existingConv = conversationDao.getConversationById(message.conversationId)
        val resolvedPeerId = existingConv?.peerId
            ?: message.conversationId.removePrefix("conv_").takeIf { it.isNotEmpty() }
            ?: if (message.recipientId.isNotEmpty()) message.recipientId else "unknown"
        val resolvedPeerName = existingConv?.peerDisplayName ?: "Peer ${resolvedPeerId.take(4)}"

        val convEntity = ConversationEntity(
            id = message.conversationId,
            peerId = resolvedPeerId,
            peerDisplayName = resolvedPeerName,
            lastMessage = message.payload,
            lastMessageTimestamp = message.timestamp
        )
        conversationDao.insertOrUpdate(convEntity)
    }

    override suspend fun updateMessageStatus(messageId: String, status: MessageDeliveryStatus) {
        messageDao.updateStatus(messageId, status.name)
    }

    override suspend fun clearAll() {
        messageDao.clearAll()
        conversationDao.clearAll()
    }

    private fun ConversationEntity.toDomain() = Conversation(
        id = id,
        peerId = peerId,
        peerDisplayName = peerDisplayName,
        lastMessage = lastMessage,
        lastMessageTimestamp = lastMessageTimestamp
    )

    private fun MessageEntity.toDomain() = Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        recipientId = recipientId,
        timestamp = timestamp,
        type = type,
        payload = payload,
        status = runCatching { MessageDeliveryStatus.valueOf(status) }.getOrDefault(MessageDeliveryStatus.SENDING)
    )
}
