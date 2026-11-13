package com.example.data.remote

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.studioPrefsDataStore by preferencesDataStore(name = "gyanverse_studio_prefs")

data class ApiIntegrationConfig(
  val backendRelayUrl: String = "",
  val metaAppId: String = "",
  val youtubeClientId: String = "",
  val instagramAccessToken: String = "",
  val instagramUserId: String = "",
  val facebookAccessToken: String = "",
  val facebookPageId: String = "",
  val youtubeAccessToken: String = "",
  val pendingOAuthPlatform: String = "",
  val pendingOAuthState: String = "",
  val pendingCodeVerifier: String = "",
  val pushNotificationsEnabled: Boolean = true,
  val appDownloadUrl: String = "",
) {
  fun isPlatformConfigured(platformId: String): Boolean {
    if (backendRelayUrl.isNotBlank()) return true
    return when (platformId) {
      "instagram" -> metaAppId.isNotBlank() && instagramAccessToken.isNotBlank() && instagramUserId.isNotBlank()
      "facebook" -> metaAppId.isNotBlank() && facebookAccessToken.isNotBlank() && facebookPageId.isNotBlank()
      "youtube" -> youtubeClientId.isNotBlank() && youtubeAccessToken.isNotBlank()
      else -> false
    }
  }

  fun hasOAuthClientFor(platformId: String): Boolean {
    if (backendRelayUrl.isNotBlank()) return true
    return when (platformId) {
      "instagram", "facebook" -> metaAppId.isNotBlank()
      "youtube" -> youtubeClientId.isNotBlank()
      else -> false
    }
  }
}

class ApiConfigStore(private val context: Context) {

  val configFlow: Flow<ApiIntegrationConfig> =
    context.studioPrefsDataStore.data.map { prefs ->
      ApiIntegrationConfig(
        backendRelayUrl = prefs[KEY_BACKEND_RELAY_URL].orEmpty(),
        metaAppId = prefs[KEY_META_APP_ID].orEmpty(),
        youtubeClientId = prefs[KEY_YOUTUBE_CLIENT_ID].orEmpty(),
        instagramAccessToken = prefs[KEY_IG_TOKEN].orEmpty(),
        instagramUserId = prefs[KEY_IG_USER_ID].orEmpty(),
        facebookAccessToken = prefs[KEY_FB_TOKEN].orEmpty(),
        facebookPageId = prefs[KEY_FB_PAGE_ID].orEmpty(),
        youtubeAccessToken = prefs[KEY_YT_TOKEN].orEmpty(),
        pendingOAuthPlatform = prefs[KEY_PENDING_PLATFORM].orEmpty(),
        pendingOAuthState = prefs[KEY_PENDING_STATE].orEmpty(),
        pendingCodeVerifier = prefs[KEY_PENDING_VERIFIER].orEmpty(),
        pushNotificationsEnabled = prefs[KEY_NOTIFICATIONS] ?: true,
        appDownloadUrl = prefs[KEY_APP_DOWNLOAD_URL].orEmpty(),
      )
    }

  suspend fun getCurrentConfig(): ApiIntegrationConfig = configFlow.first()

  suspend fun saveApiEndpoints(
    backendRelayUrl: String,
    metaAppId: String,
    youtubeClientId: String,
  ) {
    context.studioPrefsDataStore.edit { prefs ->
      prefs[KEY_BACKEND_RELAY_URL] = backendRelayUrl.trim().trimEnd('/')
      prefs[KEY_META_APP_ID] = metaAppId.trim()
      prefs[KEY_YOUTUBE_CLIENT_ID] = youtubeClientId.trim()
    }
  }

  suspend fun savePendingOAuth(platform: String, state: String, codeVerifier: String) {
    context.studioPrefsDataStore.edit { prefs ->
      prefs[KEY_PENDING_PLATFORM] = platform
      prefs[KEY_PENDING_STATE] = state
      prefs[KEY_PENDING_VERIFIER] = codeVerifier
    }
  }

  suspend fun savePlatformToken(
    platform: String,
    accessToken: String,
    externalId: String = "",
  ) {
    context.studioPrefsDataStore.edit { prefs ->
      when (platform) {
        "instagram" -> {
          prefs[KEY_IG_TOKEN] = accessToken
          if (externalId.isNotBlank()) prefs[KEY_IG_USER_ID] = externalId
        }
        "facebook" -> {
          prefs[KEY_FB_TOKEN] = accessToken
          if (externalId.isNotBlank()) prefs[KEY_FB_PAGE_ID] = externalId
        }
        "youtube" -> {
          prefs[KEY_YT_TOKEN] = accessToken
        }
      }
      prefs.remove(KEY_PENDING_PLATFORM)
      prefs.remove(KEY_PENDING_STATE)
      prefs.remove(KEY_PENDING_VERIFIER)
    }
  }

  suspend fun clearPlatformToken(platform: String) {
    context.studioPrefsDataStore.edit { prefs ->
      when (platform) {
        "instagram" -> {
          prefs.remove(KEY_IG_TOKEN)
          prefs.remove(KEY_IG_USER_ID)
        }
        "facebook" -> {
          prefs.remove(KEY_FB_TOKEN)
          prefs.remove(KEY_FB_PAGE_ID)
        }
        "youtube" -> {
          prefs.remove(KEY_YT_TOKEN)
        }
      }
    }
  }

  suspend fun setPushNotificationsEnabled(enabled: Boolean) {
    context.studioPrefsDataStore.edit { prefs -> prefs[KEY_NOTIFICATIONS] = enabled }
  }

  suspend fun saveAppDownloadUrl(url: String) {
    context.studioPrefsDataStore.edit { prefs ->
      prefs[KEY_APP_DOWNLOAD_URL] = url.trim().take(500)
    }
  }

  private companion object {
    val KEY_BACKEND_RELAY_URL = stringPreferencesKey("backend_relay_url")
    val KEY_META_APP_ID = stringPreferencesKey("meta_app_id")
    val KEY_YOUTUBE_CLIENT_ID = stringPreferencesKey("youtube_client_id")
    val KEY_IG_TOKEN = stringPreferencesKey("ig_access_token")
    val KEY_IG_USER_ID = stringPreferencesKey("ig_user_id")
    val KEY_FB_TOKEN = stringPreferencesKey("fb_access_token")
    val KEY_FB_PAGE_ID = stringPreferencesKey("fb_page_id")
    val KEY_YT_TOKEN = stringPreferencesKey("yt_access_token")
    val KEY_PENDING_PLATFORM = stringPreferencesKey("pending_oauth_platform")
    val KEY_PENDING_STATE = stringPreferencesKey("pending_oauth_state")
    val KEY_PENDING_VERIFIER = stringPreferencesKey("pending_oauth_verifier")
    val KEY_NOTIFICATIONS = booleanPreferencesKey("push_notifications_enabled")
    val KEY_APP_DOWNLOAD_URL = stringPreferencesKey("app_download_url")
  }
}
