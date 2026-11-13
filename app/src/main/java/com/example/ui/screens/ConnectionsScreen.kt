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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SocialAccount
import com.example.data.model.SocialPlatform
import com.example.data.remote.MetaPageItem
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.components.platformColor
import com.example.ui.components.platformIcon
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusWarning
import com.example.ui.viewmodel.StudioUiState

@Composable
fun ConnectionsScreen(
  uiState: StudioUiState,
  onConnectPlatform: (SocialPlatform) -> Unit,
  onDisconnectPlatform: (SocialAccount) -> Unit,
  onSelectFacebookPage: (MetaPageItem) -> Unit,
  onSaveApiEndpoints: (relayUrl: String, metaAppId: String, youtubeClientId: String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var relayUrl by remember(uiState.apiConfig.backendRelayUrl) {
    mutableStateOf(uiState.apiConfig.backendRelayUrl.ifBlank { uiState.systemSettings.backendRelayUrl.orEmpty() })
  }
  var metaAppId by remember(uiState.apiConfig.metaAppId) {
    mutableStateOf(uiState.apiConfig.metaAppId.ifBlank { uiState.systemSettings.metaAppId.orEmpty() })
  }
  var youtubeClientId by remember(uiState.apiConfig.youtubeClientId) {
    mutableStateOf(uiState.apiConfig.youtubeClientId.ifBlank { uiState.systemSettings.youtubeClientId.orEmpty() })
  }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 960.dp).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Text(
          text = "Social Media Connections",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = "Official OAuth 2.0 integration — social passwords are never requested or stored.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      item {
        PlatformConnectionCard(
          platform = SocialPlatform.INSTAGRAM,
          subtitle = "Official Meta Instagram Graph API • Business / Creator Reels",
          connectButtonLabel = "Connect Instagram",
          account = uiState.socialAccounts.firstOrNull { it.platform == "instagram" },
          hasApiConfig = uiState.apiConfig.hasOAuthClientFor("instagram"),
          onConnect = { onConnectPlatform(SocialPlatform.INSTAGRAM) },
          onDisconnect = { acc -> onDisconnectPlatform(acc) },
        )
      }

      item {
        PlatformConnectionCard(
          platform = SocialPlatform.FACEBOOK,
          subtitle = "Official Meta Graph API • Facebook Page Reels Publishing",
          connectButtonLabel = "Connect Facebook Page",
          account = uiState.socialAccounts.firstOrNull { it.platform == "facebook" },
          hasApiConfig = uiState.apiConfig.hasOAuthClientFor("facebook"),
          onConnect = { onConnectPlatform(SocialPlatform.FACEBOOK) },
          onDisconnect = { acc -> onDisconnectPlatform(acc) },
          extraContent = {
            if (uiState.availableFacebookPages.isNotEmpty()) {
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                text = "Select Facebook Page:",
                style = MaterialTheme.typography.labelLarge,
                color = NeonCyan,
              )
              Spacer(modifier = Modifier.height(6.dp))
              uiState.availableFacebookPages.forEach { page ->
                OutlinedButton(
                  onClick = { onSelectFacebookPage(page) },
                  modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                ) {
                  Text("${page.name} (${page.id})")
                }
              }
            }
          },
        )
      }

      item {
        PlatformConnectionCard(
          platform = SocialPlatform.YOUTUBE,
          subtitle = "Official YouTube Data API v3 • Google OAuth 2.0 PKCE",
          connectButtonLabel = "Connect YouTube Account",
          account = uiState.socialAccounts.firstOrNull { it.platform == "youtube" },
          hasApiConfig = uiState.apiConfig.hasOAuthClientFor("youtube"),
          onConnect = { onConnectPlatform(SocialPlatform.YOUTUBE) },
          onDisconnect = { acc -> onDisconnectPlatform(acc) },
        )
      }

      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Default.SettingsEthernet,
              contentDescription = null,
              tint = NeonCyan,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Official API & Backend Relay Setup",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text =
              "Provide your Node.js Backend Relay URL (recommended so OAuth tokens stay server-side) or public OAuth Client IDs. Secrets are never stored in the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = relayUrl,
            onValueChange = { relayUrl = it },
            label = { Text("Backend Relay Base URL (https://...)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("relay_url_input"),
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = metaAppId,
            onValueChange = { metaAppId = it },
            label = { Text("Meta App ID (Instagram & Facebook)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("meta_app_id_input"),
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = youtubeClientId,
            onValueChange = { youtubeClientId = it },
            label = { Text("YouTube OAuth Client ID (Google Cloud)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("youtube_client_id_input"),
          )
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = { onSaveApiEndpoints(relayUrl, metaAppId, youtubeClientId) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("save_api_config_button"),
          ) {
            Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save & Verify API Integration")
          }

          if (!uiState.relayHealthSummary.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = uiState.relayHealthSummary,
              style = MaterialTheme.typography.labelMedium,
              color = NeonCyan,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun PlatformConnectionCard(
  platform: SocialPlatform,
  subtitle: String,
  connectButtonLabel: String,
  account: SocialAccount?,
  hasApiConfig: Boolean,
  onConnect: () -> Unit,
  onDisconnect: (SocialAccount) -> Unit,
  extraContent: @Composable () -> Unit = {},
) {
  val accent = platformColor(platform)
  val isConnected = account?.status == "connected"

  GlassCard(
    modifier = Modifier.fillMaxWidth(),
    borderColor = accent.copy(alpha = 0.45f),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = accent.copy(alpha = 0.18f),
          modifier = Modifier.size(44.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = platformIcon(platform),
              contentDescription = platform.displayName,
              tint = accent,
              modifier = Modifier.size(24.dp),
            )
          }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text(
            text = platform.productName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      StatusPill(
        status =
          when {
            isConnected -> "connected"
            !hasApiConfig -> "api_connection_required"
            else -> account?.status ?: "api_connection_required"
          }
      )
      if (account != null && isConnected) {
        Text(
          text = "${account.accountName} (${account.accountHandle})",
          style = MaterialTheme.typography.labelMedium,
          color = NeonCyan,
        )
      }
    }

    if (!hasApiConfig && !isConnected) {
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = "API CONNECTION REQUIRED — Add Backend Relay URL or OAuth Client ID below to connect.",
        style = MaterialTheme.typography.bodySmall,
        color = StatusWarning,
      )
    }

    extraContent()

    Spacer(modifier = Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
      Button(
        onClick = onConnect,
        colors = ButtonDefaults.buttonColors(containerColor = accent),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.weight(1f).height(48.dp).testTag("connect_${platform.id}_button"),
      ) {
        Icon(Icons.Default.VerifiedUser, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = if (isConnected) "Reconnect OAuth" else connectButtonLabel,
          color = Color.White,
        )
      }
      if (account != null && isConnected) {
        OutlinedButton(
          onClick = { onDisconnect(account) },
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.height(48.dp),
        ) {
          Icon(Icons.Default.LinkOff, contentDescription = "Disconnect", modifier = Modifier.size(16.dp))
        }
      }
    }
  }
}
