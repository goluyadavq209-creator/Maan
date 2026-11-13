package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.SocialPlatform
import com.example.ui.components.GlassCard
import com.example.ui.components.PlatformPill
import com.example.ui.components.StatusPill
import com.example.ui.components.StudioAccentBrush
import com.example.ui.components.VideoPreviewPlayer
import com.example.ui.components.platformColor
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.viewmodel.AiGenState
import com.example.ui.viewmodel.StudioUiState
import com.example.ui.viewmodel.VideoEditorState
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UploadStudioScreen(
  uiState: StudioUiState,
  editor: VideoEditorState,
  aiState: AiGenState,
  onSelectVideoUri: (Uri) -> Unit,
  onTitleChange: (String) -> Unit,
  onDescriptionChange: (String) -> Unit,
  onCaptionChange: (SocialPlatform, String) -> Unit,
  onAddHashtag: (String) -> Unit,
  onRemoveHashtag: (String) -> Unit,
  onTogglePlatform: (SocialPlatform) -> Unit,
  onGenerateAi: () -> Unit,
  onSaveDraft: () -> Unit,
  onPublishEverywhere: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val videoPicker =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.PickVisualMedia(),
      onResult = { uri -> uri?.let { onSelectVideoUri(it) } },
    )

  var newTagText by remember { mutableStateOf("") }
  var selectedCaptionTab by remember { mutableIntStateOf(0) }
  val captionPlatforms = SocialPlatform.entries

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 960.dp).fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      item {
        Text(
          text = "Video Upload Center",
          style = MaterialTheme.typography.headlineMedium,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text =
            "Max ${uiState.systemSettings.maxVideoSizeMb} MB • ${uiState.systemSettings.maxVideoDurationSec}s • Free Limit ${uiState.systemSettings.maxDailyUploadsPerUser}/day",
          style = MaterialTheme.typography.labelMedium,
          color = NeonCyan,
        )
      }

      // Drop / Pick Zone & Video Preview
      item {
        GlassCard(
          modifier = Modifier.fillMaxWidth(),
          borderColor = ElectricViolet.copy(alpha = 0.45f),
        ) {
          if (editor.selectedUri.isBlank()) {
            Box(
              modifier =
                Modifier.fillMaxWidth()
                  .height(180.dp)
                  .clip(RoundedCornerShape(16.dp))
                  .background(Color(0x338B5CF6))
                  .clickable {
                    videoPicker.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                    )
                  }
                  .testTag("pick_video_zone"),
              contentAlignment = Alignment.Center,
            ) {
              Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                  imageVector = Icons.Default.CloudUpload,
                  contentDescription = "Select video",
                  tint = NeonCyan,
                  modifier = Modifier.size(44.dp),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                  text = "Tap to Pick Video from Gallery",
                  style = MaterialTheme.typography.titleMedium,
                  color = Color.White,
                )
                Text(
                  text = "9:16 Vertical MP4 / MOV supported via Android Photo Picker",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          } else {
            VideoPreviewPlayer(
              videoUriString = editor.selectedUri,
              modifier = Modifier.fillMaxWidth().height(250.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
              modifier = Modifier.fillMaxWidth(),
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                editor.thumbnailBitmap?.let { bmp ->
                  Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Video thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                } ?: Icon(
                  imageVector = Icons.Default.VideoFile,
                  contentDescription = null,
                  tint = NeonCyan,
                  modifier = Modifier.size(32.dp),
                )
                Column {
                  val sizeMb =
                    String.format(Locale.US, "%.2f MB", editor.fileSizeBytes / (1024.0 * 1024.0))
                  val mins = editor.durationSeconds / 60
                  val secs = editor.durationSeconds % 60
                  Text(
                    text = String.format(Locale.US, "%02d:%02d • %s • %s", mins, secs, sizeMb, editor.aspectRatio),
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonCyan,
                  )
                  Text(
                    text = "Preview & thumbnail ready",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusSuccess,
                  )
                }
              }
              OutlinedButton(
                onClick = {
                  videoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                  )
                },
              ) {
                Text("Replace")
              }
            }
          }
        }
      }

      // Title, Description & AI Hashtag Generator
      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          OutlinedTextField(
            value = editor.title,
            onValueChange = onTitleChange,
            label = { Text("Video Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("video_title_input"),
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedTextField(
            value = editor.description,
            onValueChange = onDescriptionChange,
            label = { Text("Description / Topic Context") },
            minLines = 3,
            maxLines = 5,
            modifier = Modifier.fillMaxWidth().testTag("video_description_input"),
          )
          Spacer(modifier = Modifier.height(12.dp))

          Button(
            onClick = onGenerateAi,
            enabled = aiState !is AiGenState.Loading,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("ai_generate_button"),
          ) {
            if (aiState is AiGenState.Loading) {
              CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = Color.White,
                strokeWidth = 2.dp,
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Generating AI Hashtags & Captions...")
            } else {
              Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("AI Hashtag & Caption Generator")
            }
          }

          if (aiState is AiGenState.Error) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = aiState.message,
              style = MaterialTheme.typography.bodySmall,
              color = StatusError,
            )
          } else if (aiState is AiGenState.Success) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = aiState.message,
              style = MaterialTheme.typography.bodySmall,
              color = StatusSuccess,
            )
          }

          Spacer(modifier = Modifier.height(12.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedTextField(
              value = newTagText,
              onValueChange = { newTagText = it },
              label = { Text("Add Hashtag") },
              singleLine = true,
              modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
              onClick = {
                onAddHashtag(newTagText)
                newTagText = ""
              },
              modifier = Modifier.size(48.dp),
            ) {
              Icon(Icons.Default.Add, contentDescription = "Add hashtag", tint = NeonCyan)
            }
          }

          if (editor.hashtags.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              editor.hashtags.forEach { tag ->
                AssistChip(
                  onClick = { onRemoveHashtag(tag) },
                  label = { Text(tag, style = MaterialTheme.typography.labelMedium) },
                  trailingIcon = {
                    Icon(
                      imageVector = Icons.Default.Close,
                      contentDescription = "Remove $tag",
                      modifier = Modifier.size(14.dp),
                    )
                  },
                )
              }
            }
          }
        }
      }

      // Platform-specific Caption Editor
      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Platform-Specific Caption Editor",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(8.dp))
          TabRow(
            selectedTabIndex = selectedCaptionTab,
            containerColor = Color.Transparent,
            contentColor = NeonCyan,
          ) {
            captionPlatforms.forEachIndexed { idx, platform ->
              Tab(
                selected = selectedCaptionTab == idx,
                onClick = { selectedCaptionTab = idx },
                text = { Text(platform.shortLabel, maxLines = 1) },
              )
            }
          }
          Spacer(modifier = Modifier.height(10.dp))
          val activePlatform = captionPlatforms[selectedCaptionTab]
          val activeCaption =
            when (activePlatform) {
              SocialPlatform.INSTAGRAM -> editor.captionInstagram
              SocialPlatform.FACEBOOK -> editor.captionFacebook
              SocialPlatform.YOUTUBE -> editor.captionYoutube
            }
          OutlinedTextField(
            value = activeCaption,
            onValueChange = { onCaptionChange(activePlatform, it) },
            label = { Text("${activePlatform.productName} Caption") },
            minLines = 3,
            maxLines = 6,
            modifier = Modifier.fillMaxWidth().testTag("caption_editor_${activePlatform.id}"),
          )
        }
      }

      // One-Click Publishing Section
      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Select Target Platforms",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(10.dp))
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            SocialPlatform.entries.forEach { platform ->
              val selected = platform in editor.selectedPlatforms
              val accent = platformColor(platform)
              FilterChip(
                selected = selected,
                onClick = { onTogglePlatform(platform) },
                label = { Text(platform.productName) },
                colors =
                  FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.25f),
                    selectedLabelColor = Color.White,
                  ),
                border =
                  FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selected,
                    selectedBorderColor = accent,
                  ),
                modifier = Modifier.testTag("select_platform_${platform.id}"),
              )
            }
          }

          Spacer(modifier = Modifier.height(14.dp))
          if (editor.isPublishing) {
            LinearProgressIndicator(
              progress = { editor.uploadProgressPercent },
              modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
              color = NeonCyan,
            )
            Spacer(modifier = Modifier.height(10.dp))
          }

          Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
          ) {
            OutlinedButton(
              onClick = onSaveDraft,
              enabled = !editor.isPublishing,
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.height(52.dp).testTag("save_draft_button"),
            ) {
              Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Save")
            }

            Button(
              onClick = onPublishEverywhere,
              enabled = !editor.isPublishing,
              shape = RoundedCornerShape(14.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
              modifier =
                Modifier.weight(1f)
                  .height(52.dp)
                  .background(StudioAccentBrush, RoundedCornerShape(14.dp))
                  .testTag("publish_everywhere_button"),
            ) {
              Icon(
                Icons.Default.RocketLaunch,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (editor.isPublishing) "PUBLISHING..." else "PUBLISH EVERYWHERE",
                color = Color.White,
                fontWeight = FontWeight.Bold,
              )
            }
          }

          if (editor.publishOutcomes.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            editor.publishOutcomes.forEach { (platform, result) ->
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0A0F24),
                border = BorderStroke(1.dp, platformColor(platform).copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
              ) {
                Column(modifier = Modifier.padding(12.dp)) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                  ) {
                    PlatformPill(platform)
                    StatusPill(result.status)
                  }
                  if (!result.errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = result.errorMessage,
                      style = MaterialTheme.typography.bodySmall,
                      color = StatusError,
                    )
                  }
                  if (!result.externalPostUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                      text = result.externalPostUrl,
                      style = MaterialTheme.typography.labelMedium,
                      color = NeonCyan,
                    )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
