package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScheduleSend
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PublishingJob
import com.example.data.model.ScheduledPost
import com.example.data.model.SocialPlatform
import com.example.data.model.VideoAsset
import com.example.ui.components.GlassCard
import com.example.ui.components.PlatformPill
import com.example.ui.components.StatusPill
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusError
import com.example.ui.viewmodel.StudioUiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SchedulerAndHistoryScreen(
  uiState: StudioUiState,
  initialTab: Int = 0,
  onSchedulePost: (VideoAsset, Set<SocialPlatform>, Long) -> Unit,
  onReschedulePost: (ScheduledPost, Long, Set<SocialPlatform>) -> Unit,
  onCancelScheduledPost: (ScheduledPost) -> Unit,
  onRetryJob: (PublishingJob) -> Unit,
  modifier: Modifier = Modifier,
) {
  var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(modifier = Modifier.widthIn(max = 960.dp).fillMaxSize()) {
      TabRow(
        selectedTabIndex = selectedTab,
        containerColor = Color.Transparent,
        contentColor = NeonCyan,
      ) {
        Tab(
          selected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          text = { Text("Schedule Post") },
          icon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
        )
        Tab(
          selected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          text = { Text("Post History") },
          icon = { Icon(Icons.Default.History, contentDescription = null) },
        )
      }

      if (selectedTab == 0) {
        SchedulePostTab(
          uiState = uiState,
          onSchedulePost = onSchedulePost,
          onReschedulePost = onReschedulePost,
          onCancelScheduledPost = onCancelScheduledPost,
        )
      } else {
        PostHistoryTab(
          jobs = uiState.publishingJobs,
          onRetryJob = onRetryJob,
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SchedulePostTab(
  uiState: StudioUiState,
  onSchedulePost: (VideoAsset, Set<SocialPlatform>, Long) -> Unit,
  onReschedulePost: (ScheduledPost, Long, Set<SocialPlatform>) -> Unit,
  onCancelScheduledPost: (ScheduledPost) -> Unit,
) {
  var selectedVideo by remember(uiState.videos) { mutableStateOf(uiState.videos.firstOrNull()) }
  var videoDropdownExpanded by remember { mutableStateOf(false) }
  var selectedPlatforms by remember {
    mutableStateOf(setOf(SocialPlatform.INSTAGRAM, SocialPlatform.YOUTUBE))
  }

  val defaultTime = remember { System.currentTimeMillis() + 3_600_000L }
  var scheduledMillis by remember { mutableLongStateOf(defaultTime) }
  var showDatePicker by remember { mutableStateOf(false) }
  var showTimePicker by remember { mutableStateOf(false) }
  var editingPost by remember { mutableStateOf<ScheduledPost?>(null) }

  val dateFormat = remember { SimpleDateFormat("EEE, MMM d, yyyy • h:mm a", Locale.getDefault()) }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    item {
      GlassCard(modifier = Modifier.fillMaxWidth()) {
        Text(
          text = "Schedule Background Post (WorkManager)",
          style = MaterialTheme.typography.titleLarge,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (uiState.videos.isEmpty()) {
          Text(
            text = "Upload or save a video project in Upload Studio first to schedule it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        } else {
          ExposedDropdownMenuBox(
            expanded = videoDropdownExpanded,
            onExpandedChange = { videoDropdownExpanded = !videoDropdownExpanded },
          ) {
            OutlinedTextField(
              value = selectedVideo?.title ?: "Select Video",
              onValueChange = {},
              readOnly = true,
              label = { Text("Select Video Project") },
              trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = videoDropdownExpanded) },
              modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(
              expanded = videoDropdownExpanded,
              onDismissRequest = { videoDropdownExpanded = false },
            ) {
              uiState.videos.forEach { video ->
                DropdownMenuItem(
                  text = { Text(video.title) },
                  onClick = {
                    selectedVideo = video
                    videoDropdownExpanded = false
                  },
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Platforms",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(6.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SocialPlatform.entries.forEach { p ->
              FilterChip(
                selected = p in selectedPlatforms,
                onClick = {
                  selectedPlatforms =
                    if (p in selectedPlatforms) selectedPlatforms - p else selectedPlatforms + p
                },
                label = { Text(p.shortLabel) },
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
              onClick = { showDatePicker = true },
              modifier = Modifier.weight(1f).height(48.dp).testTag("pick_date_button"),
            ) {
              Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Pick Date")
            }
            OutlinedButton(
              onClick = { showTimePicker = true },
              modifier = Modifier.weight(1f).height(48.dp).testTag("pick_time_button"),
            ) {
              Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Pick Time")
            }
          }

          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Scheduled for: ${dateFormat.format(Date(scheduledMillis))}",
            style = MaterialTheme.typography.labelMedium,
            color = NeonCyan,
          )

          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = {
              selectedVideo?.let { v -> onSchedulePost(v, selectedPlatforms, scheduledMillis) }
            },
            enabled = selectedVideo != null,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("schedule_post_button"),
          ) {
            Icon(Icons.Default.ScheduleSend, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Schedule Multi-Platform Post")
          }
        }
      }
    }

    item {
      Text(
        text = "Scheduled Posts Queue",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }

    if (uiState.scheduledPosts.isEmpty()) {
      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "No scheduled posts queued yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      items(uiState.scheduledPosts, key = { it.id }) { post ->
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = post.videoTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = dateFormat.format(Date(post.scheduledForMillis)),
                style = MaterialTheme.typography.labelMedium,
                color = NeonCyan,
              )
            }
            StatusPill(post.status)
          }
          Spacer(modifier = Modifier.height(8.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            post.platforms.forEach { PlatformPill(SocialPlatform.fromId(it)) }
          }
          if (!post.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
              text = post.errorMessage,
              style = MaterialTheme.typography.bodySmall,
              color = StatusError,
            )
          }
          if (post.status == "scheduled" || post.status == "failed") {
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = { editingPost = post },
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Default.EditCalendar, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Edit")
              }
              OutlinedButton(
                onClick = { onCancelScheduledPost(post) },
                modifier = Modifier.weight(1f),
              ) {
                Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cancel")
              }
            }
          }
        }
      }
    }
  }

  if (showDatePicker) {
    val dpState = rememberDatePickerState(initialSelectedDateMillis = scheduledMillis)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            dpState.selectedDateMillis?.let { pickedDate ->
              val oldCal = Calendar.getInstance().apply { timeInMillis = scheduledMillis }
              val newCal = Calendar.getInstance().apply { timeInMillis = pickedDate }
              newCal.set(Calendar.HOUR_OF_DAY, oldCal.get(Calendar.HOUR_OF_DAY))
              newCal.set(Calendar.MINUTE, oldCal.get(Calendar.MINUTE))
              scheduledMillis = newCal.timeInMillis
            }
            showDatePicker = false
          }
        ) { Text("OK") }
      },
      dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
    ) { DatePicker(state = dpState) }
  }

  if (showTimePicker) {
    val cal = Calendar.getInstance().apply { timeInMillis = scheduledMillis }
    val tpState =
      rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
      )
    AlertDialog(
      onDismissRequest = { showTimePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            val updated =
              Calendar.getInstance().apply {
                timeInMillis = scheduledMillis
                set(Calendar.HOUR_OF_DAY, tpState.hour)
                set(Calendar.MINUTE, tpState.minute)
                set(Calendar.SECOND, 0)
              }
            scheduledMillis = updated.timeInMillis
            showTimePicker = false
          }
        ) { Text("Set Time") }
      },
      dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
      title = { Text("Select Publish Time") },
      text = { TimePicker(state = tpState) },
    )
  }

  editingPost?.let { post ->
    var editPlatforms by remember(post) {
      mutableStateOf(post.platforms.map { SocialPlatform.fromId(it) }.toSet())
    }
    AlertDialog(
      onDismissRequest = { editingPost = null },
      title = { Text("Reschedule '${post.videoTitle}'") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text("Reschedule +1 hour from now or keep selected platforms:")
          FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SocialPlatform.entries.forEach { p ->
              FilterChip(
                selected = p in editPlatforms,
                onClick = {
                  editPlatforms = if (p in editPlatforms) editPlatforms - p else editPlatforms + p
                },
                label = { Text(p.shortLabel) },
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            onReschedulePost(post, System.currentTimeMillis() + 3_600_000L, editPlatforms)
            editingPost = null
          }
        ) { Text("Move +1h") }
      },
      dismissButton = { TextButton(onClick = { editingPost = null }) { Text("Close") } },
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostHistoryTab(
  jobs: List<PublishingJob>,
  onRetryJob: (PublishingJob) -> Unit,
) {
  val context = LocalContext.current
  var filter by remember { mutableStateOf("all") }
  val dateFormat = remember { SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault()) }

  val filtered =
    remember(jobs, filter) {
      when (filter) {
        "success" -> jobs.filter { it.status == "success" }
        "failed" -> jobs.filter { it.status == "failed" }
        "api" -> jobs.filter { it.status == "api_connection_required" }
        else -> jobs
      }
    }

  LazyColumn(
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
    modifier = Modifier.fillMaxSize(),
  ) {
    item {
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("all" to "All", "success" to "Published", "failed" to "Failed", "api" to "API Required").forEach {
          (key, label) ->
          FilterChip(
            selected = filter == key,
            onClick = { filter = key },
            label = { Text(label) },
          )
        }
      }
    }

    if (filtered.isEmpty()) {
      item {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "No publishing records match this filter.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    } else {
      items(filtered, key = { it.id }) { job ->
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
                fontWeight = FontWeight.SemiBold,
              )
              val tsText = job.createdAt?.toDate()?.let { dateFormat.format(it) } ?: "Pending sync"
              Text(
                text = "$tsText • Retries: ${job.retryCount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            StatusPill(job.status)
          }
          Spacer(modifier = Modifier.height(8.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
          ) {
            PlatformPill(SocialPlatform.fromId(job.platform))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              if (!job.externalPostUrl.isNullOrBlank()) {
                OutlinedButton(
                  onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(job.externalPostUrl))
                    context.startActivity(intent)
                  }
                ) {
                  Icon(Icons.Default.OpenInNew, contentDescription = "Open post", modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("View Post")
                }
              }
              if (job.status == "failed" || job.status == "api_connection_required") {
                Button(onClick = { onRetryJob(job) }) {
                  Icon(Icons.Default.Refresh, contentDescription = "Retry", modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(4.dp))
                  Text("Retry")
                }
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
