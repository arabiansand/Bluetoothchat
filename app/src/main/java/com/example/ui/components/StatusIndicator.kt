package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ConnectionStatus
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusFailed
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline

/**
 * Three primary visual states required for peer connectivity in the messaging header:
 * - CONNECTED: Active BLE session
 * - SEARCHING: Scanning or attempting to locate/negotiate with the peer
 * - DISCONNECTED: Offline or disconnected peer
 */
enum class BlePeerStatus {
    CONNECTED,
    SEARCHING,
    DISCONNECTED
}

/**
 * Visual indicator in the messaging screen header that displays the current connection status
 * (connected, disconnected, or searching) for the BLE peer.
 */
@Composable
fun BleHeaderConnectionIndicator(
    status: ConnectionStatus,
    isSearching: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    // Map connection status and search flags to the three canonical BLE peer states
    val peerState: BlePeerStatus = when {
        status == ConnectionStatus.CONNECTED -> BlePeerStatus.CONNECTED
        status == ConnectionStatus.CONNECTING ||
            status == ConnectionStatus.RECONNECTING ||
            status == ConnectionStatus.SEARCHING ||
            isSearching -> BlePeerStatus.SEARCHING
        else -> BlePeerStatus.DISCONNECTED
    }

    // Animation for pulsing search dot/beacon
    val infiniteTransition = rememberInfiniteTransition(label = "BleBeaconPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlePulseAlpha"
    )

    val (primaryColor, backgroundColor, borderColor, icon, statusText, statusTestTag) = when (peerState) {
        BlePeerStatus.CONNECTED -> Tuple6(
            Color(0xFF10B981), // Emerald Green
            Color(0xFF10B981).copy(alpha = 0.12f),
            Color(0xFF10B981).copy(alpha = 0.35f),
            Icons.Default.BluetoothConnected,
            "Connected",
            "ble_status_connected"
        )
        BlePeerStatus.SEARCHING -> Tuple6(
            Color(0xFF2563EB), // Vivid Electric Blue
            Color(0xFF2563EB).copy(alpha = 0.12f),
            Color(0xFF2563EB).copy(alpha = 0.35f),
            Icons.Default.BluetoothSearching,
            "Searching…",
            "ble_status_searching"
        )
        BlePeerStatus.DISCONNECTED -> Tuple6(
            Color(0xFF94A3B8), // Muted Slate
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            Color(0xFF94A3B8).copy(alpha = 0.25f),
            Icons.Default.BluetoothDisabled,
            "Disconnected",
            "ble_status_disconnected"
        )
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .testTag("ble_connection_status_indicator")
            .testTag(statusTestTag)
            .semantics {
                contentDescription = "Bluetooth peer connection status: $statusText"
            }
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(12.dp))
            .then(clickableModifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Visual indicator dot with glow/pulse
        Box(
            modifier = Modifier
                .size(14.dp)
                .testTag("connection_status_dot"),
            contentAlignment = Alignment.Center
        ) {
            if (peerState == BlePeerStatus.SEARCHING) {
                // Expanding pulse halo for searching state
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.25f * pulseAlpha))
                )
            } else if (peerState == BlePeerStatus.CONNECTED) {
                // Subtle static halo for connected state
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(primaryColor.copy(alpha = 0.20f))
                )
            }

            // Core solid indicator dot
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (peerState == BlePeerStatus.SEARCHING) primaryColor.copy(alpha = pulseAlpha)
                        else primaryColor
                    )
            )
        }

        Spacer(modifier = Modifier.width(5.dp))

        // Mini Bluetooth Icon
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier
                .size(12.dp)
                .then(
                    if (peerState == BlePeerStatus.SEARCHING) Modifier.alpha(pulseAlpha)
                    else Modifier
                )
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Status Label Text
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = if (peerState == BlePeerStatus.DISCONNECTED) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                primaryColor
            },
            modifier = Modifier.testTag("connection_status_text")
        )
    }
}

/**
 * General ConnectionStatusChip supporting legacy callers as well as searching state.
 */
@Composable
fun ConnectionStatusChip(
    status: ConnectionStatus,
    modifier: Modifier = Modifier,
    isSearching: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val isEffectivelySearching = isSearching ||
        status == ConnectionStatus.CONNECTING ||
        status == ConnectionStatus.RECONNECTING ||
        status == ConnectionStatus.SEARCHING

    val (dotColor, text) = when {
        status == ConnectionStatus.CONNECTED -> StatusOnline to "Connected"
        isEffectivelySearching -> Color(0xFF2563EB) to "Searching…"
        status == ConnectionStatus.AVAILABLE -> StatusOnline to "Available"
        status == ConnectionStatus.FAILED -> StatusFailed to "Failed"
        else -> StatusOffline to "Disconnected"
    }

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .testTag("status_indicator_chip")
            .testTag("ble_connection_status_indicator")
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(clickableModifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
                .testTag("connection_status_dot")
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("connection_status_text")
        )
    }
}

private data class Tuple6<A, B, C, D, E, F>(
    val a: A,
    val b: B,
    val c: C,
    val d: D,
    val e: E,
    val f: F
)
