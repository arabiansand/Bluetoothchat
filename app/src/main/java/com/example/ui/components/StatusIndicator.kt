package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.ConnectionStatus
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusFailed
import com.example.ui.theme.StatusOffline
import com.example.ui.theme.StatusOnline

@Composable
fun ConnectionStatusChip(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val (dotColor, text) = when (status) {
        ConnectionStatus.CONNECTED -> StatusOnline to "Connected"
        ConnectionStatus.CONNECTING -> StatusConnecting to "Connecting…"
        ConnectionStatus.RECONNECTING -> StatusConnecting to "Reconnecting…"
        ConnectionStatus.AVAILABLE -> StatusOnline to "Available"
        ConnectionStatus.FAILED -> StatusFailed to "Failed"
        ConnectionStatus.DISCONNECTED -> StatusOffline to "Disconnected"
    }

    Row(
        modifier = modifier
            .testTag("status_indicator_chip")
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
