package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.data.model.SocialPlatform
import com.example.ui.theme.CosmicPurpleSurface
import com.example.ui.theme.DeepNavyBg
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.FacebookAccent
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.GlassCardFill
import com.example.ui.theme.InstagramAccent
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RoseMagenta
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.YouTubeAccent

@Composable
fun GyanverseBackground(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(
    modifier =
      modifier
        .fillMaxSize()
        .background(
          Brush.verticalGradient(
            colors = listOf(DeepNavyBg, CosmicPurpleSurface, Color(0xFF080D22))
          )
        )
        .drawBehind {
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(ElectricViolet.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(size.width * 0.15f, size.height * 0.12f),
                radius = size.maxDimension * 0.45f,
              ),
            center = Offset(size.width * 0.15f, size.height * 0.12f),
            radius = size.maxDimension * 0.45f,
          )
          drawCircle(
            brush =
              Brush.radialGradient(
                colors = listOf(NeonCyan.copy(alpha = 0.14f), Color.Transparent),
                center = Offset(size.width * 0.88f, size.height * 0.78f),
                radius = size.maxDimension * 0.42f,
              ),
            center = Offset(size.width * 0.88f, size.height * 0.78f),
            radius = size.maxDimension * 0.42f,
          )
        },
    content = content,
  )
}

@Composable
fun GlassCard(
  modifier: Modifier = Modifier,
  borderColor: Color = GlassBorderSubtle,
  containerColor: Color = GlassCardFill,
  content: @Composable ColumnScope.() -> Unit,
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(containerColor = containerColor),
    border = BorderStroke(1.dp, borderColor),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
  ) {
    Column(modifier = Modifier.padding(16.dp), content = content)
  }
}

@Composable
fun MetricStatCard(
  label: String,
  value: String,
  icon: ImageVector,
  accentColor: Color,
  modifier: Modifier = Modifier,
) {
  GlassCard(
    modifier = modifier,
    borderColor = accentColor.copy(alpha = 0.35f),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Surface(
        shape = CircleShape,
        color = accentColor.copy(alpha = 0.16f),
        modifier = Modifier.size(36.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = icon,
            contentDescription = label,
            tint = accentColor,
            modifier = Modifier.size(20.dp),
          )
        }
      }
      Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
      )
    }
    Spacer(modifier = Modifier.height(10.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
fun PlatformPill(platform: SocialPlatform, modifier: Modifier = Modifier) {
  val color = platformColor(platform)
  val icon = platformIcon(platform)
  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(50),
    color = color.copy(alpha = 0.16f),
    border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = platform.displayName,
        tint = color,
        modifier = Modifier.size(14.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = platform.shortLabel,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
      )
    }
  }
}

@Composable
fun StatusPill(status: String, modifier: Modifier = Modifier) {
  val (label, color, icon) =
    when (status.lowercase()) {
      "success", "connected", "published", "completed", "ready" ->
        Triple(status.uppercase(), StatusSuccess, Icons.Default.CheckCircle)
      "api_connection_required", "api_required", "unconfigured" ->
        Triple("API CONNECTION REQUIRED", StatusWarning, Icons.Default.Warning)
      "uploading", "processing", "queued", "scheduled" ->
        Triple(status.uppercase(), NeonCyan, Icons.Default.Schedule)
      else -> Triple(status.uppercase(), StatusError, Icons.Default.Error)
    }

  Surface(
    modifier = modifier,
    shape = RoundedCornerShape(50),
    color = color.copy(alpha = 0.16f),
    border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = color,
        modifier = Modifier.size(13.dp),
      )
      Spacer(modifier = Modifier.width(5.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
fun VideoPreviewPlayer(videoUriString: String, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val exoPlayer =
    remember(videoUriString) {
      ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(Uri.parse(videoUriString)))
        prepare()
        playWhenReady = false
      }
    }

  DisposableEffect(exoPlayer) {
    onDispose { exoPlayer.release() }
  }

  AndroidView(
    factory = { ctx ->
      PlayerView(ctx).apply {
        player = exoPlayer
        useController = true
      }
    },
    update = { playerView -> playerView.player = exoPlayer },
    modifier = modifier.clip(RoundedCornerShape(16.dp)),
  )
}

fun platformColor(platform: SocialPlatform): Color =
  when (platform) {
    SocialPlatform.INSTAGRAM -> InstagramAccent
    SocialPlatform.FACEBOOK -> FacebookAccent
    SocialPlatform.YOUTUBE -> YouTubeAccent
  }

fun platformIcon(platform: SocialPlatform): ImageVector =
  when (platform) {
    SocialPlatform.INSTAGRAM -> Icons.Default.CameraAlt
    SocialPlatform.FACEBOOK -> Icons.Default.Public
    SocialPlatform.YOUTUBE -> Icons.Default.PlayCircle
  }

val StudioAccentBrush = Brush.horizontalGradient(listOf(ElectricViolet, RoseMagenta, NeonCyan))
