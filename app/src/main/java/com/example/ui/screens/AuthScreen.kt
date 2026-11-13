package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.GoogleAuthManager
import com.example.data.model.SocialPlatform
import com.example.ui.components.GlassCard
import com.example.ui.components.GyanverseBackground
import com.example.ui.components.PlatformPill
import com.example.ui.components.StudioAccentBrush
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  GyanverseBackground(modifier = modifier) {
    Column(
      modifier =
        Modifier.fillMaxSize()
          .safeDrawingPadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp, vertical = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Column(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        // Hero Banner
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(210.dp)
              .clip(RoundedCornerShape(26.dp))
        ) {
          Image(
            painter = painterResource(id = R.drawable.img_studio_hero),
            contentDescription = "Gyanverse Studio hero artwork",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
          )
          Box(
            modifier =
              Modifier.fillMaxSize()
                .background(
                  Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xD9060918))
                  )
                )
          )
          Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)
          ) {
            Surface(
              shape = RoundedCornerShape(50),
              color = ElectricViolet.copy(alpha = 0.28f),
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
              ) {
                Icon(
                  imageVector = Icons.Default.Verified,
                  contentDescription = "Free platform",
                  tint = NeonCyan,
                  modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "100% FREE CREATOR SAAS",
                  style = MaterialTheme.typography.labelMedium,
                  color = NeonCyan,
                )
              }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "GYANVERSE STUDIO",
              style = MaterialTheme.typography.displaySmall,
              color = Color.White,
              fontWeight = FontWeight.Bold,
            )
            Text(
              text = "Create Once, Publish Everywhere.",
              style = MaterialTheme.typography.titleMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth(),
        ) {
          PlatformPill(SocialPlatform.INSTAGRAM, modifier = Modifier.weight(1f))
          PlatformPill(SocialPlatform.FACEBOOK, modifier = Modifier.weight(1f))
          PlatformPill(SocialPlatform.YOUTUBE, modifier = Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(18.dp))

        GlassCard(modifier = Modifier.fillMaxWidth()) {
          Text(
            text = "Creator Studio Workspace",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text =
              "Sign in with your Google / Gmail account to upload vertical reels, generate AI captions, schedule background posts, and connect official social APIs.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(modifier = Modifier.height(16.dp))

          FeatureRow(
            icon = Icons.Default.Bolt,
            title = "One-Click Multi-Platform Publishing",
            subtitle = "Instagram Reels, Facebook Reels & YouTube Shorts",
          )
          Spacer(modifier = Modifier.height(10.dp))
          FeatureRow(
            icon = Icons.Default.AutoAwesome,
            title = "AI Hashtag & Caption Studio",
            subtitle = "Tailored platform copy powered by Firebase AI",
          )
          Spacer(modifier = Modifier.height(10.dp))
          FeatureRow(
            icon = Icons.Default.Schedule,
            title = "Automated Background Scheduler",
            subtitle = "Queue posts reliably with WorkManager & Firestore",
          )
          Spacer(modifier = Modifier.height(10.dp))
          FeatureRow(
            icon = Icons.Default.Lock,
            title = "OAuth 2.0 Security",
            subtitle = "Never enter social media passwords directly",
          )

          Spacer(modifier = Modifier.height(20.dp))

          if (errorMessage != null) {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = StatusError.copy(alpha = 0.16f),
              modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
              Text(
                text = errorMessage!!,
                style = MaterialTheme.typography.bodySmall,
                color = StatusError,
                modifier = Modifier.padding(12.dp),
              )
            }
          }

          Button(
            onClick = {
              errorMessage = null
              isLoading = true
              GoogleAuthManager.onGoogleSignInClicked(
                context = context,
                credentialManager = credentialManager,
                onAuthSuccess = {
                  isLoading = false
                  onAuthSuccess()
                },
                onAuthError = { err ->
                  isLoading = false
                  errorMessage = err
                },
                scope = scope,
                onAuthCancelled = {
                  isLoading = false
                  errorMessage = "Sign-in cancelled. Select your Google account to continue."
                },
              )
            },
            enabled = !isLoading,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            modifier =
              Modifier.fillMaxWidth()
                .height(54.dp)
                .background(StudioAccentBrush, RoundedCornerShape(16.dp))
                .testTag("google_sign_in_button"),
          ) {
            if (isLoading) {
              CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = Color.White,
                strokeWidth = 2.dp,
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text("Signing in...", color = Color.White)
            } else {
              Text(
                text = "Continue with Google / Gmail",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text = "Free forever for creators. No subscriptions or paywalls.",
            style = MaterialTheme.typography.bodySmall,
            color = StatusSuccess,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Surface(
      shape = CircleShape,
      color = ElectricViolet.copy(alpha = 0.18f),
      modifier = Modifier.size(34.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          imageVector = icon,
          contentDescription = title,
          tint = NeonCyan,
          modifier = Modifier.size(18.dp),
        )
      }
    }
    Spacer(modifier = Modifier.width(12.dp))
    Column {
      Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}
