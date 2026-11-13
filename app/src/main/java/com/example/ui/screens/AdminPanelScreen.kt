package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.UserProfile
import com.example.ui.components.GlassCard
import com.example.ui.components.MetricStatCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.AdminUiState
import java.util.Locale

@Composable
fun AdminPanelScreen(
  adminState: AdminUiState,
  onBack: () -> Unit,
  onToggleBlockUser: (UserProfile) -> Unit,
  onDeleteUser: (UserProfile) -> Unit,
  onSaveSystemSettings: (
    maxDailyUploads: Long,
    maxSizeMb: Long,
    maxDurationSec: Long,
    maintenanceMode: Boolean,
    relayUrl: String,
    metaAppId: String,
    youtubeClientId: String,
    appDownloadUrl: String,
  ) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember { mutableIntStateOf(0) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.widthIn(max = 960.dp).fillMaxSize()) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
      ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("admin_back_button")) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column {
          Text(
            text = "Super-Admin Console",
            style = MaterialTheme.typography.headlineSmall,
            color = NeonCyan,
          )
          Text(
            text = "Gyanverse Studio Infrastructure & Governance",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }

      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color.Transparent,
        contentColor = NeonCyan,
      ) {
        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Overview") })
        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Users") })
        Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Logs") })
        Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Limits") })
      }

      when (selectedTab) {
        0 -> AdminOverviewTab(adminState)
        1 -> AdminUsersTab(adminState, onToggleBlockUser, onDeleteUser)
        2 -> AdminLogsTab(adminState)
        3 -> AdminSettingsTab(adminState, onSaveSystemSettings)
      }
    }
  }
}

@Composable
private fun AdminOverviewTab(adminState: AdminUiState) {
  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricStatCard(
          label = "Registered Users",
          value = adminState.users.size.toString(),
          icon = Icons.Default.Group,
          accentColor = ElectricViolet,
          modifier = Modifier.weight(1f),
        )
        MetricStatCard(
          label = "Active Users",
          value = adminState.activeUsersCount.toString(),
          icon = Icons.Default.CheckCircle,
          accentColor = StatusSuccess,
          modifier = Modifier.weight(1f),
        )
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricStatCard(
          label = "Published Videos",
          value = adminState.videos.size.toString(),
          icon = Icons.Default.VideoLibrary,
          accentColor = NeonCyan,
          modifier = Modifier.weight(1f),
        )
        MetricStatCard(
          label = "Connected Accounts",
          value = adminState.accounts.count { it.status == "connected" }.toString(),
          icon = Icons.Default.Link,
          accentColor = StatusSuccess,
          modifier = Modifier.weight(1f),
        )
      }
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MetricStatCard(
          label = "Failed Jobs",
          value = adminState.failedJobsCount.toString(),
          icon = Icons.Default.Error,
          accentColor = StatusError,
          modifier = Modifier.weight(1f),
        )
        MetricStatCard(
          label = "Storage Usage (MB)",
          value = String.format(Locale.US, "%.1f", adminState.totalStorageMb),
          icon = Icons.Default.Storage,
          accentColor = StatusWarning,
          modifier = Modifier.weight(1f),
        )
      }
    }
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.CloudQueue, contentDescription = null, tint = NeonCyan)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "API Connection Monitoring",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
        Spacer(modifier = Modifier.height(8.dp))
        val igCount = adminState.accounts.count { it.platform == "instagram" && it.status == "connected" }
        val fbCount = adminState.accounts.count { it.platform == "facebook" && it.status == "connected" }
        val ytCount = adminState.accounts.count { it.platform == "youtube" && it.status == "connected" }
        Text(
          text = "Active Links — Instagram: $igCount • Facebook: $fbCount • YouTube: $ytCount",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text =
            "Relay Endpoint: ${adminState.systemSettings.backendRelayUrl?.ifBlank { "Not configured" } ?: "Not configured"}",
          style = MaterialTheme.typography.labelMedium,
          color = NeonCyan,
        )
      }
    }
  }
}

@Composable
private fun AdminUsersTab(
  adminState: AdminUiState,
  onToggleBlockUser: (UserProfile) -> Unit,
  onDeleteUser: (UserProfile) -> Unit,
) {
  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    items(adminState.users, key = { it.userId }) { user ->
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "${user.displayName} (${user.role.uppercase()})",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = user.email,
              style = MaterialTheme.typography.bodySmall,
              color = NeonCyan,
            )
          }
          StatusPill(if (user.isBlocked) "blocked" else "connected")
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            onClick = { onToggleBlockUser(user) },
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (user.isBlocked) "Unblock" else "Block")
          }
          OutlinedButton(
            onClick = { onDeleteUser(user) },
            modifier = Modifier.weight(1f),
          ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Delete", color = StatusError)
          }
        }
      }
    }
  }
}

@Composable
private fun AdminLogsTab(adminState: AdminUiState) {
  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    if (adminState.logs.isEmpty()) {
      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Text("No system logs recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    } else {
      items(adminState.logs, key = { it.id }) { log ->
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = log.action,
              style = MaterialTheme.typography.labelLarge,
              color = NeonCyan,
              fontWeight = FontWeight.Bold,
            )
            StatusPill(if (log.severity == "info") "success" else "failed")
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "${log.actorEmail} • ${log.platform ?: "system"}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = log.details,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
          )
        }
      }
    }
  }
}

@Composable
private fun AdminSettingsTab(
  adminState: AdminUiState,
  onSaveSystemSettings: (Long, Long, Long, Boolean, String, String, String, String) -> Unit,
) {
  val s = adminState.systemSettings
  var dailyLimit by remember(s.maxDailyUploadsPerUser) { mutableStateOf(s.maxDailyUploadsPerUser.toString()) }
  var maxSizeMb by remember(s.maxVideoSizeMb) { mutableStateOf(s.maxVideoSizeMb.toString()) }
  var maxDuration by remember(s.maxVideoDurationSec) { mutableStateOf(s.maxVideoDurationSec.toString()) }
  var maintenance by remember(s.maintenanceMode) { mutableStateOf(s.maintenanceMode) }
  var relayUrl by remember(s.backendRelayUrl) { mutableStateOf(s.backendRelayUrl.orEmpty()) }
  var metaAppId by remember(s.metaAppId) { mutableStateOf(s.metaAppId.orEmpty()) }
  var ytClientId by remember(s.youtubeClientId) { mutableStateOf(s.youtubeClientId.orEmpty()) }
  var appDownloadUrl by remember(s.appDownloadUrl) { mutableStateOf(s.appDownloadUrl.orEmpty()) }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "Free Platform Fair-Use Limits & API Config",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
          value = dailyLimit,
          onValueChange = { dailyLimit = it.filter { c -> c.isDigit() } },
          label = { Text("Max Daily Uploads / User") },
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = maxSizeMb,
          onValueChange = { maxSizeMb = it.filter { c -> c.isDigit() } },
          label = { Text("Max Video Size (MB)") },
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = maxDuration,
          onValueChange = { maxDuration = it.filter { c -> c.isDigit() } },
          label = { Text("Max Short Duration (Seconds)") },
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = relayUrl,
          onValueChange = { relayUrl = it },
          label = { Text("Global Backend Relay URL") },
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = metaAppId,
          onValueChange = { metaAppId = it },
          label = { Text("Global Meta App ID") },
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = ytClientId,
          onValueChange = { ytClientId = it },
          label = { Text("Global YouTube Client ID") },
          modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
          value = appDownloadUrl,
          onValueChange = { appDownloadUrl = it },
          label = { Text("Official Mobile APK / Release URL (https://...)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("admin_app_download_url_input"),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text("Maintenance Mode", style = MaterialTheme.typography.bodyMedium)
          Switch(checked = maintenance, onCheckedChange = { maintenance = it })
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
          onClick = {
            onSaveSystemSettings(
              dailyLimit.toLongOrNull() ?: 25L,
              maxSizeMb.toLongOrNull() ?: 200L,
              maxDuration.toLongOrNull() ?: 90L,
              maintenance,
              relayUrl,
              metaAppId,
              ytClientId,
              appDownloadUrl,
            )
          },
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth().height(48.dp).testTag("admin_save_settings"),
        ) {
          Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Save System Configuration")
        }
      }
    }
  }
}
