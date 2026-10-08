package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.database.AppDatabase
import com.example.data.repository.ChatRepository
import com.example.data.repository.ChatRepositoryImpl
import com.example.data.repository.UserRepository
import com.example.domain.model.ConnectionStatus
import com.example.domain.model.DiagnosticsInfo
import com.example.domain.model.Message
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.Peer
import com.example.domain.model.User
import com.example.transport.NearbyTransport
import com.example.transport.contract.ContractNearbyTransport
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class NearbyChatViewModel(
    application: Application,
    private val userRepository: UserRepository,
    private val chatRepository: ChatRepository,
    private val transport: NearbyTransport
) : AndroidViewModel(application) {

    val currentUser: StateFlow<User> = userRepository.currentUser

    val discoveredPeers: StateFlow<List<Peer>> = transport.discoveredPeers

    private val _isDiscoveryActive = MutableStateFlow(false)
    val isDiscoveryActive: StateFlow<Boolean> = _isDiscoveryActive.asStateFlow()

    val activeConnectionState: StateFlow<ConnectionStatus> = transport.activeConnectionState
    val activePeer: StateFlow<Peer?> = transport.activePeer

    private val _incomingConnectionRequest = MutableStateFlow<Peer?>(null)
    val incomingConnectionRequest: StateFlow<Peer?> = _incomingConnectionRequest.asStateFlow()

    private val _activeChatPeerId = MutableStateFlow<String?>(null)
    val activeChatPeerId: StateFlow<String?> = _activeChatPeerId.asStateFlow()

    val activeConversationMessages: StateFlow<List<Message>> = _activeChatPeerId
        .flatMapLatest { peerId ->
            if (peerId != null) {
                chatRepository.getMessagesForConversation("conv_$peerId")
            } else {
                flowOf(emptyList())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val diagnostics: StateFlow<DiagnosticsInfo> = transport.diagnostics

    init {
        viewModelScope.launch {
            transport.incomingConnectionRequests.collect { peer ->
                _incomingConnectionRequest.value = peer
            }
        }
    }

    fun updateDisplayName(name: String) {
        userRepository.updateDisplayName(name)
    }

    fun toggleDiscovery() {
        if (_isDiscoveryActive.value) {
            transport.stopDiscovery()
            _isDiscoveryActive.value = false
        } else {
            transport.startDiscovery()
            _isDiscoveryActive.value = true
        }
    }

    fun startDiscovery() {
        transport.startDiscovery()
        _isDiscoveryActive.value = true
    }

    fun stopDiscovery() {
        transport.stopDiscovery()
        _isDiscoveryActive.value = false
    }

    fun requestConnect(peer: Peer) {
        transport.requestConnection(peer)
    }

    fun acceptConnection(peer: Peer) {
        transport.acceptConnection(peer)
        _incomingConnectionRequest.value = null
        _activeChatPeerId.value = peer.id
    }

    fun declineConnection(peer: Peer) {
        transport.declineConnection(peer)
        _incomingConnectionRequest.value = null
    }

    fun disconnect() {
        transport.disconnect()
    }

    fun selectChatPeer(peer: Peer) {
        _activeChatPeerId.value = peer.id
    }

    fun selectChatPeerId(peerId: String) {
        _activeChatPeerId.value = peerId
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val currentPeer = activePeer.value
        val sender = currentUser.value
        val peerId = currentPeer?.id ?: _activeChatPeerId.value ?: return

        val messageId = UUID.randomUUID().toString()
        val conversationId = "conv_$peerId"

        val message = Message(
            id = messageId,
            conversationId = conversationId,
            senderId = sender.id,
            recipientId = peerId,
            timestamp = System.currentTimeMillis(),
            type = "text",
            payload = trimmed,
            status = MessageDeliveryStatus.SENDING
        )

        viewModelScope.launch {
            // Save locally first
            chatRepository.saveMessage(message)

            // Attempt delivery via transport
            val isDelivered = transport.sendData(peerId, trimmed)
            val updatedStatus = if (isDelivered) {
                MessageDeliveryStatus.DELIVERED
            } else {
                if (transport.activeConnectionState.value == ConnectionStatus.CONNECTED) {
                    MessageDeliveryStatus.SENT
                } else {
                    MessageDeliveryStatus.FAILED
                }
            }
            chatRepository.updateMessageStatus(messageId, updatedStatus)
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch {
            chatRepository.clearAll()
        }
    }
}

class NearbyChatViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NearbyChatViewModel::class.java)) {
            val db = AppDatabase.getInstance(application)
            val userRepo = UserRepository(application)
            val chatRepo = ChatRepositoryImpl(db.conversationDao(), db.messageDao())
            val transport = ContractNearbyTransport()
            return NearbyChatViewModel(application, userRepo, chatRepo, transport) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
