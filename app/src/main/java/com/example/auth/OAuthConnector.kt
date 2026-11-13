package com.example.auth

import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.example.data.model.SocialPlatform
import com.example.data.remote.ApiIntegrationConfig
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

object OAuthConnector {

  const val REDIRECT_URI = "gyanverse://oauth/callback"

  sealed interface LaunchResult {
    data class Launched(val state: String, val codeVerifier: String) : LaunchResult
    data class ApiConnectionRequired(val message: String) : LaunchResult
  }

  fun launchOAuthFlow(
    context: Context,
    platform: SocialPlatform,
    config: ApiIntegrationConfig,
  ): LaunchResult {
    val state = UUID.randomUUID().toString()
    val codeVerifier = generateCodeVerifier()
    val codeChallenge = generateCodeChallenge(codeVerifier)

    val authUri: Uri? =
      when {
        config.backendRelayUrl.isNotBlank() -> {
          Uri.parse("${config.backendRelayUrl}/api/oauth/${platform.id}/start")
            .buildUpon()
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("state", state)
            .build()
        }
        platform == SocialPlatform.INSTAGRAM && config.metaAppId.isNotBlank() -> {
          Uri.parse("https://www.facebook.com/v21.0/dialog/oauth")
            .buildUpon()
            .appendQueryParameter("client_id", config.metaAppId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("state", state)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter(
              "scope",
              "instagram_basic,instagram_content_publish,pages_show_list,pages_read_engagement",
            )
            .build()
        }
        platform == SocialPlatform.FACEBOOK && config.metaAppId.isNotBlank() -> {
          Uri.parse("https://www.facebook.com/v21.0/dialog/oauth")
            .buildUpon()
            .appendQueryParameter("client_id", config.metaAppId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("state", state)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter(
              "scope",
              "pages_show_list,pages_read_engagement,pages_manage_posts,publish_video",
            )
            .build()
        }
        platform == SocialPlatform.YOUTUBE && config.youtubeClientId.isNotBlank() -> {
          Uri.parse("https://accounts.google.com/o/oauth2/v2/auth")
            .buildUpon()
            .appendQueryParameter("client_id", config.youtubeClientId)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("state", state)
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter(
              "scope",
              "https://www.googleapis.com/auth/youtube.upload https://www.googleapis.com/auth/youtube.readonly",
            )
            .appendQueryParameter("access_type", "offline")
            .build()
        }
        else -> null
      }

    if (authUri == null) {
      val missing =
        when (platform) {
          SocialPlatform.INSTAGRAM, SocialPlatform.FACEBOOK ->
            "API CONNECTION REQUIRED: Configure Backend Relay URL or Meta App ID in API Integration Setup before starting ${platform.productName} OAuth 2.0."
          SocialPlatform.YOUTUBE ->
            "API CONNECTION REQUIRED: Configure Backend Relay URL or YouTube OAuth Client ID in API Integration Setup before starting YouTube OAuth 2.0."
        }
      return LaunchResult.ApiConnectionRequired(missing)
    }

    val customTabsIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
    customTabsIntent.launchUrl(context, authUri)
    return LaunchResult.Launched(state = state, codeVerifier = codeVerifier)
  }

  private fun generateCodeVerifier(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  }

  private fun generateCodeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
    return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
  }
}
