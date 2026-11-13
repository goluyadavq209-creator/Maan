package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.model.PublishingJob
import com.example.data.model.SocialPlatform
import com.example.ui.components.GlassCard
import com.example.ui.components.MetricStatCard
import com.example.ui.components.PlatformPill
import com.example.ui.components.StatusPill
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.FacebookAccent
import com.example.ui.theme.InstagramAccent
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.YouTubeAccent
import com.example.ui.viewmodel.StudioUiState

@Composable
fun DashboardScreen(
  uiState: StudioUiState,
  isAdmin: Boolean,
  onNavigateToUpload: () -> Unit,
  onNavigateToConnections: () -> Unit,
  onNavigateToScheduler: () -> Unit,
  onNavigateToAdmin: () -> Unit,
  onRetryJob: (PublishingJob) -> Unit,
  modifier: Modifier = Modifier,
) {
  val totalPublished = uiState.publishingJobs.count { it.status == "success" }
  val igPublished =
    uiState.publishingJobs.count { it.platform == "instagram" && it.status == "success" }
  val fbPublished =
    uiState.publishingJobs.count { it.platform == "facebook" && it.status == "success" }
  val ytPublished =
    uiState.publishingJobs.count { it.platform == "youtube" && it.status == "success" }
  val failedUploads =
    uiState.publishingJobs.count {
      it.status == "failed" || it.status == "api_connection_required"
    }
  val connectedAccounts = uiState.socialAccounts.count { it.status == "connected" }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 960.dp).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.weight(1f),
            ) {
              val photoUrl = uiState.userProfile?.photoUrl
              if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                  model = photoUrl,
                  contentDescription = "Creator avatar",
                  contentScale = ContentScale.Crop,
                  modifier = Modifier.size(48.dp).clip(CircleShape),
                )
              } else {
                Surface(
                  shape = CircleShape,
                  color = ElectricViolet.copy(alpha = 0.25f),
                  modifier = Modifier.size(48.dp),
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Text(
                      text =
                        (uiState.userProfile?.displayName ?: "G").take(1).uppercase(),
                      style = MaterialTheme.typography.titleLarge,
                      color = NeonCyan,
                    )
                  }
                }
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text =
                    "Welcome, ${uiState.userProfile?.displayName ?: "Creator"}",
                  style = MaterialTheme.typography.titleLarge,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Text(
                  text = "Create Once, Publish Everywhere • Free Tier",
                  style = MaterialTheme.typography.bodySmall,
                  color = NeonCyan,
                )
              }
            }
            if (isAdmin) {
              IconButton(
                onClick = onNavigateToAdmin,
                modifier = Modifier.testTag("admin_panel_shortcut"),
              ) {
                Icon(
                  imageVector = Icons.Default.AdminPanelSettings,
                  contentDescription = "Open Admin Panel",
                  tint = NeonCyan,
                )
              }
            }
          }

          if (uiState.userProfile?.isBlocked == true) {
            Spacer(modifier = Modifier.height(10.dp))
            StatusPill("ACCOUNT SUSPENDED BY ADMIN")
          }
        }
      }

      item {
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          MetricStatCard(
            label = "Total Videos Published",
            value = totalPublished.toString(),
            icon = Icons.Default.VideoLibrary,
            accentColor = ElectricViolet,
            modifier = Modifier.weight(1f),
          )
          MetricStatCard(
            label = "Connected Accounts",
            value = "$connectedAccounts/3",
            icon = Icons.Default.Link,
            accentColor = StatusSuccess,
            modifier = Modifier.weight(1f),
          )
        }
      }

      item {
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          MetricStatCard(
            label = "Instagram Reels",
            value = igPublished.toString(),
            icon = Icons.Default.CameraAlt,
            accentColor = InstagramAccent,
            modifier = Modifier.weight(1f),
          )
          MetricStatCard(
            label = "Facebook Reels",
            value = fbPublished.toString(),
            icon = Icons.Default.Public,
            accentColor = FacebookAccent,
            modifier = Modifier.weight(1f),
          )
        }
      }

      item {
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          MetricStatCard(
            label = "YouTube Shorts",
            value = ytPublished.toString(),
            icon = Icons.Default.PlayCircle,
            accentColor = YouTubeAccent,
            modifier = Modifier.weight(1f),
          )
          MetricStatCard(
            label = "Failed / Pending API",
            value = failedUploads.toString(),
            icon = Icons.Default.ErrorOutline,
            accentColor = StatusError,
            modifier = Modifier.weight(1f),
          )
        }
      }

      item {
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Button(
            onClick = onNavigateToUpload,
            modifier = Modifier.weight(1f).height(48.dp).testTag("dash_upload_cta"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Upload Video")
          }
          FilledTonalButton(
            onClick = onNavigateToConnections,
            modifier = Modifier.weight(1f).height(48.dp).testTag("dash_connect_cta"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Connect APIs")
          }
          FilledTonalButton(
            onClick = onNavigateToScheduler,
            modifier = Modifier.height(48.dp).testTag("dash_schedule_cta"),
            shape = RoundedCornerShape(14.dp),
          ) {
            Icon(Icons.Default.Schedule, contentDescription = "Schedule", modifier = Modifier.size(18.dp))
          }
        }
      }

      item {
        Text(
          text = "Recent Publishing Jobs",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
      }

      if (uiState.publishingJobs.isEmpty()) {
        item {
          GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.fillMaxWidth().padding(12.dp),
            ) {
              Image(
                painter = painterResource(id = R.drawable.img_empty_reels),
                contentDescription = "No publishing jobs yet",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)),
              )
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "Ready to Publish Everywhere",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Upload your first vertical video or connect your Instagram, Facebook, and YouTube accounts to start.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      } else {
        items(uiState.publishingJobs.take(8), key = { it.id }) { job ->
          GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = job.videoTitle,
                  style = MaterialTheme.typography.titleMedium,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  PlatformPill(SocialPlatform.fromId(job.platform))
                  StatusPill(job.status)
                }
              }
              if (job.status == "failed" || job.status == "api_connection_required") {
                FilledTonalButton(
                  onClick = { onRetryJob(job) },
                  colors = ButtonDefaults.filledTonalButtonColors(),
                ) {
                  Text("Retry")
                }
              }
            }
            if (!job.errorMessage.isNullOrBlank()) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = job.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = StatusError,
              )
            }
          }
        }
      }
    }
  }
}
