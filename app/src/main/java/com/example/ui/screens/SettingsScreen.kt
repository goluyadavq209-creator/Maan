package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.SocialAccount
import com.example.data.model.SocialPlatform
import com.example.ui.components.GlassCard
import com.example.ui.components.PlatformPill
import com.example.ui.components.StatusPill
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusError
import com.example.ui.viewmodel.StudioUiState

@Composable
fun SettingsScreen(
  uiState: StudioUiState,
  isAdmin: Boolean,
  onSaveProfile: (displayName: String, bio: String, notifyPublish: Boolean, notifyFailure: Boolean) -> Unit,
  onSaveAppDownloadUrl: (String) -> Unit,
  onDisconnectAccount: (SocialAccount) -> Unit,
  onOpenAdminPanel: () -> Unit,
  onSignOut: () -> Unit,
  onDeleteAccount: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val profile = uiState.userProfile
  var displayName by remember(profile?.displayName) { mutableStateOf(profile?.displayName.orEmpty()) }
  var bio by remember(profile?.bio) { mutableStateOf(profile?.bio.orEmpty()) }
  var notifyPublish by remember(profile?.notifyOnPublish) {
    mutableStateOf(profile?.notifyOnPublish ?: true)
  }
  var notifyFailure by remember(profile?.notifyOnFailure) {
    mutableStateOf(profile?.notifyOnFailure ?: true)
  }
  var showPrivacyDialog by remember { mutableStateOf(false) }
  var showTermsDialog by remember { mutableStateOf(false) }
  var showDeleteConfirm by remember { mutableStateOf(false) }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      notifyPublish = granted
    }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 960.dp).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Text(
          text = "Account & Studio Settings",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = profile?.email ?: "",
          style = MaterialTheme.typography.labelMedium,
          color = NeonCyan,
        )
      }

      if (isAdmin) {
        item {
          GlassCard(
            modifier = Modifier.fillMaxWidth(),
            borderColor = NeonCyan.copy(alpha = 0.5f),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Super Admin Access Active",
                  style = MaterialTheme.typography.titleMedium,
                  color = NeonCyan,
                )
                Text(
                  text = "Manage users, logs, storage, and platform limits.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Button(
                onClick = onOpenAdminPanel,
                modifier = Modifier.testTag("settings_open_admin_button"),
              ) {
                Icon(Icons.Default.AdminPanelSettings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Admin Panel")
              }
            }
          }
        }
      }

      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Creator Profile",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = displayName,
            onValueChange = { displayName = it },
            label = { Text("Display Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("settings_name_input"),
          )
          Spacer(modifier = Modifier.height(8.dp))
          OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text("Channel Bio") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
          )
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Notify when scheduled post publishes", style = MaterialTheme.typography.bodyMedium)
            Switch(
              checked = notifyPublish,
              onCheckedChange = { enabled ->
                if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                  val granted =
                    ContextCompat.checkSelfPermission(
                      context,
                      Manifest.permission.POST_NOTIFICATIONS,
                    ) == PackageManager.PERMISSION_GRANTED
                  if (!granted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                  } else {
                    notifyPublish = true
                  }
                } else {
                  notifyPublish = enabled
                }
              },
            )
          }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text("Alert on API or upload failure", style = MaterialTheme.typography.bodyMedium)
            Switch(checked = notifyFailure, onCheckedChange = { notifyFailure = it })
          }
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = { onSaveProfile(displayName, bio, notifyPublish, notifyFailure) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_save_profile"),
          ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save Profile & Preferences")
          }
        }
      }

      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Connected Accounts",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(8.dp))
          if (uiState.socialAccounts.isEmpty()) {
            Text(
              text = "No social accounts linked yet.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          } else {
            uiState.socialAccounts.forEach { acc ->
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                  PlatformPill(SocialPlatform.fromId(acc.platform))
                  StatusPill(acc.status)
                }
                TextButton(onClick = { onDisconnectAccount(acc) }) {
                  Text("Disconnect")
                }
              }
            }
          }
        }
      }

      item {
        val effectiveDownloadUrl =
          uiState.apiConfig.appDownloadUrl.ifBlank { uiState.systemSettings.appDownloadUrl.orEmpty() }
        var customUrlInput by remember(effectiveDownloadUrl) { mutableStateOf(effectiveDownloadUrl) }

        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          borderColor = NeonCyan.copy(alpha = 0.35f),
        ) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Text(
              text = "Mobile APK Download & Share",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurface,
            )
            StatusPill(
              if (effectiveDownloadUrl.startsWith("https://", ignoreCase = true)) "ready"
              else "api_connection_required"
            )
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text =
              if (effectiveDownloadUrl.isBlank()) {
                "DOWNLOAD URL NOT CONFIGURED: Generate the signed APK via the AI Studio Settings menu (Generate APK / AAB), host it on GitHub Releases or Firebase App Distribution, and paste the HTTPS link below to download or share on any phone."
              } else {
                "Open the hosted APK release link in your mobile browser or share it directly with another device."
              },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = customUrlInput,
            onValueChange = { customUrlInput = it },
            label = { Text("Hosted APK / Release HTTPS URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("settings_apk_url_input"),
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = { onSaveAppDownloadUrl(customUrlInput) },
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("settings_save_apk_url"),
          ) {
            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Save Release Link")
          }
          Spacer(modifier = Modifier.height(8.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
              onClick = {
                val target = customUrlInput.trim().ifBlank { effectiveDownloadUrl.trim() }
                if (!target.startsWith("https://", ignoreCase = true)) {
                  Toast.makeText(
                    context,
                    "Add a valid https:// APK release URL first.",
                    Toast.LENGTH_SHORT,
                  ).show()
                } else {
                  try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                    context.startActivity(intent)
                  } catch (e: Exception) {
                    Toast.makeText(
                      context,
                      "No browser available to open download link.",
                      Toast.LENGTH_SHORT,
                    ).show()
                  }
                }
              },
              enabled = customUrlInput.isNotBlank() || effectiveDownloadUrl.isNotBlank(),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.weight(1f).height(48.dp).testTag("download_apk_browser_button"),
            ) {
              Icon(Icons.Default.Download, contentDescription = "Download APK", modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Download APK")
            }
            OutlinedButton(
              onClick = {
                val target = customUrlInput.trim().ifBlank { effectiveDownloadUrl.trim() }
                if (target.startsWith("https://", ignoreCase = true)) {
                  val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(Intent.EXTRA_SUBJECT, "Gyanverse Studio Android APK")
                      putExtra(
                        Intent.EXTRA_TEXT,
                        "Download Gyanverse Studio (Create Once, Publish Everywhere): $target",
                      )
                    }
                  context.startActivity(Intent.createChooser(shareIntent, "Share APK Link"))
                }
              },
              enabled = customUrlInput.startsWith("https://", ignoreCase = true) ||
                effectiveDownloadUrl.startsWith("https://", ignoreCase = true),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.height(48.dp).testTag("share_apk_link_button"),
            ) {
              Icon(Icons.Default.Share, contentDescription = "Share link", modifier = Modifier.size(18.dp))
            }
          }
        }
      }

      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Legal & Security",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Authentication is managed securely by Google Identity & Firebase Auth (no passwords stored locally).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = { showPrivacyDialog = true },
              modifier = Modifier.weight(1f).height(48.dp),
            ) {
              Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Privacy Policy")
            }
            OutlinedButton(
              onClick = { showTermsDialog = true },
              modifier = Modifier.weight(1f).height(48.dp),
            ) {
              Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Terms")
            }
          }
        }
      }

      item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(
            onClick = onSignOut,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).height(50.dp).testTag("logout_button"),
          ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Secure Logout")
          }
          Button(
            onClick = { showDeleteConfirm = true },
            colors = ButtonDefaults.buttonColors(containerColor = StatusError),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f).height(50.dp).testTag("delete_account_button"),
          ) {
            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Delete Account", color = Color.White)
          }
        }
      }
    }
  }

  if (showPrivacyDialog) {
    AlertDialog(
      onDismissRequest = { showPrivacyDialog = false },
      title = { Text("Gyanverse Studio Privacy Policy") },
      text = {
        Text(
          "Gyanverse Studio authenticates creators via Google Sign-In and isolates all videos, schedules, and publishing jobs per user UID in Cloud Firestore. Social platform OAuth 2.0 tokens are never obtained via raw passwords and client secrets are never stored in the app. You may disconnect accounts or delete your profile data at any time."
        )
      },
      confirmButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("Close") } },
    )
  }

  if (showTermsDialog) {
    AlertDialog(
      onDismissRequest = { showTermsDialog = false },
      title = { Text("Terms & Free Fair-Use Policy") },
      text = {
        Text(
          "Gyanverse Studio is a 100% free creator publishing platform with zero paid subscriptions or fees. To protect shared infrastructure and official Meta/YouTube API quotas, reasonable daily upload limits, file size caps, and short-form duration limits apply. Creators must comply with Instagram, Facebook, and YouTube Terms of Service."
        )
      },
      confirmButton = { TextButton(onClick = { showTermsDialog = false }) { Text("Accept") } },
    )
  }

  if (showDeleteConfirm) {
    AlertDialog(
      onDismissRequest = { showDeleteConfirm = false },
      title = { Text("Delete Account Data?") },
      text = {
        Text("This removes your Gyanverse Studio profile record and signs you out. This action cannot be undone.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            showDeleteConfirm = false
            onDeleteAccount()
          }
        ) { Text("Delete", color = StatusError) }
      },
      dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
    )
  }
}
