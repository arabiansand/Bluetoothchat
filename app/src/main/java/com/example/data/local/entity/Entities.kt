package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["peerId"], unique = true)
    ]
)
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val peerId: String,
    val peerDisplayName: String,
    val lastMessage: String?,
    val lastMessageTimestamp: Long
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId"]),
        Index(value = ["senderId"]),
        Index(value = ["timestamp"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val timestamp: Long,
    val type: String,
    val payload: String,
    val status: String
)
