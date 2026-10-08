package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.ConnectionRequestDialog
import com.example.ui.screens.chat.ChatScreen
import com.example.ui.screens.nearby.NearbyScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.welcome.WelcomeScreen
import com.example.ui.theme.NearbyChatTheme
import com.example.ui.viewmodel.NearbyChatViewModel
import com.example.ui.viewmodel.NearbyChatViewModelFactory

class MainActivity : ComponentActivity() {

    private val viewModel: NearbyChatViewModel by viewModels {
        NearbyChatViewModelFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            NearbyChatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NearbyChatApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun NearbyChatApp(
    viewModel: NearbyChatViewModel,
    navController: NavHostController = rememberNavController()
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val discoveredPeers by viewModel.discoveredPeers.collectAsStateWithLifecycle()
    val isDiscoveryActive by viewModel.isDiscoveryActive.collectAsStateWithLifecycle()
    val activeConnectionState by viewModel.activeConnectionState.collectAsStateWithLifecycle()
    val activePeer by viewModel.activePeer.collectAsStateWithLifecycle()
    val incomingRequest by viewModel.incomingConnectionRequest.collectAsStateWithLifecycle()
    val messages by viewModel.activeConversationMessages.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()

    // Global Connection Request Dialog
    incomingRequest?.let { requestingPeer ->
        ConnectionRequestDialog(
            peer = requestingPeer,
            onAccept = {
                viewModel.acceptConnection(requestingPeer)
                navController.navigate("chat/${requestingPeer.id}") {
                    launchSingleTop = true
                }
            },
            onDecline = {
                viewModel.declineConnection(requestingPeer)
            }
        )
    }

    NavHost(
        navController = navController,
        startDestination = "welcome"
    ) {
        composable("welcome") {
            WelcomeScreen(
                user = currentUser,
                onUpdateDisplayName = { newName ->
                    viewModel.updateDisplayName(newName)
                },
                onFindNearbyClicked = {
                    viewModel.startDiscovery()
                    navController.navigate("nearby")
                },
                onOpenSettingsClicked = {
                    navController.navigate("settings")
                }
            )
        }

        composable("nearby") {
            NearbyScreen(
                discoveredPeers = discoveredPeers,
                isDiscoveryActive = isDiscoveryActive,
                activeConnectionState = activeConnectionState,
                activePeer = activePeer,
                onToggleDiscovery = { viewModel.toggleDiscovery() },
                onRefreshScan = { viewModel.startDiscovery() },
                onPeerClicked = { peer ->
                    viewModel.requestConnect(peer)
                    viewModel.selectChatPeer(peer)
                    navController.navigate("chat/${peer.id}")
                },
                onOpenSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("chat/{peerId}") { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId")
            androidx.compose.runtime.LaunchedEffect(peerId) {
                if (peerId != null) {
                    viewModel.selectChatPeerId(peerId)
                }
            }
            val displayPeer = activePeer?.takeIf { it.id == peerId }
                ?: peerId?.let { com.example.domain.model.Peer(id = it, displayName = "Peer ${it.take(4)}") }

            ChatScreen(
                peer = displayPeer,
                currentUser = currentUser,
                connectionStatus = activeConnectionState,
                messages = messages,
                onSendMessage = { text ->
                    viewModel.sendMessage(text)
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                user = currentUser,
                diagnostics = diagnostics,
                onUpdateDisplayName = { newName ->
                    viewModel.updateDisplayName(newName)
                },
                onClearConversations = {
                    viewModel.clearAllConversations()
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
