package com.loomytrip.mobile.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.loomytrip.mobile.data.network.UserNotificationDto

@Composable
fun NotificationScreen(
    notifications: List<UserNotificationDto>,
    isLoading: Boolean,
    errorMessage: String?,
    onNotificationClick: (UserNotificationDto) -> Unit,
    onRetry: () -> Unit
) {
    when {
        isLoading -> Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) { CircularProgressIndicator() }

        errorMessage != null -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
            Text(
                "Tap to try again",
                modifier = Modifier.padding(top = 12.dp).clickable(onClick = onRetry),
                color = MaterialTheme.colorScheme.primary
            )
        }

        notifications.isEmpty() -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.NotificationsNone, contentDescription = null)
            Text("No notifications yet", modifier = Modifier.padding(top = 12.dp))
            Text("Import updates will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(notifications, key = { it.id }) { notification ->
                val isFailure = notification.type == "IMPORT_FAILED"
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onNotificationClick(notification) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (notification.readAt == null) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Icon(
                            imageVector = if (isFailure) Icons.Default.ErrorOutline else Icons.Default.TaskAlt,
                            contentDescription = null,
                            tint = if (isFailure) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        Text(notification.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                        Text(notification.body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        Text(
                            if (isFailure) "Open import" else "Review itinerary",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }
        }
    }
}
