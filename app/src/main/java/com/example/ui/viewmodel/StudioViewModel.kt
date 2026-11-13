package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.auth.OAuthConnector
import com.example.data.model.PublishingJob
import com.example.data.model.ScheduledPost
import com.example.data.model.SocialAccount
import com.example.data.model.SocialPlatform
import com.example.data.model.SystemSettings
import com.example.data.model.UserProfile
import com.example.data.model.VideoAsset
import com.example.data.remote.AiStudioAssistant
import com.example.data.remote.ApiConfigStore
import com.example.data.remote.ApiIntegrationConfig
import com.example.data.remote.MetaPageItem
import com.example.data.remote.PublishingEngine
import com.example.data.remote.SocialApiClientFactory
import com.example.data.repository.StudioRepository
import com.example.worker.ScheduledPublishWorker
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AiGenState {
  data object Idle : AiGenState
  data object Loading : AiGenState
  data class Success(val message: String) : AiGenState
  data class Error(val message: String) : AiGenState
}

data class VideoEditorState(
  val selectedUri: String = "",
  val thumbnailBitmap: Bitmap? = null,
  val title: String = "",
  val description: String = "",
  val captionInstagram: String = "",
  val captionFacebook: String = "",
  val captionYoutube: String = "",
  val hashtags: List<String> = emptyList(),
  val durationSeconds: Long = 0L,
  val fileSizeBytes: Long = 0L,
  val aspectRatio: String = "9:16",
  val selectedPlatforms: Set<SocialPlatform> = setOf(SocialPlatform.INSTAGRAM, SocialPlatform.FACEBOOK, SocialPlatform.YOUTUBE),
  val savedVideoId: String? = null,
  val isPublishing: Boolean = false,
  val uploadProgressPercent: Float = 0f,
  val publishOutcomes: Map<SocialPlatform, PublishingEngine.PublishingJobResult> = emptyMap(),
)

data class StudioUiState(
  val userProfile: UserProfile? = null,
  val socialAccounts: List<SocialAccount> = emptyList(),
  val videos: List<VideoAsset> = emptyList(),
  val publishingJobs: List<PublishingJob> = emptyList(),
  val scheduledPosts: List<ScheduledPost> = emptyList(),
  val systemSettings: SystemSettings = SystemSettings(),
  val apiConfig: ApiIntegrationConfig = ApiIntegrationConfig(),
  val availableFacebookPages: List<MetaPageItem> = emptyList(),
  val isLoading: Boolean = true,
  val bannerMessage: String? = null,
  val isBannerError: Boolean = false,
  val relayHealthSummary: String? = null,
)

class StudioViewModel(
  private val appContext: Context,
  private val repository: StudioRepository,
  private val apiConfigStore: ApiConfigStore,
  private val aiAssistant: AiStudioAssistant,
  private val publishingEngine: PublishingEngine,
  private val currentUserId: String,
  private val userEmail: String,
  private val userDisplayName: String,
  private val userPhotoUrl: String?,
) : ViewModel() {

  private val _editorState = MutableStateFlow(VideoEditorState())
  val editorState: StateFlow<VideoEditorState> = _editorState.asStateFlow()

  private val _aiState = MutableStateFlow<AiGenState>(AiGenState.Idle)
  val aiState: StateFlow<AiGenState> = _aiState.asStateFlow()

  private val _bannerState = MutableStateFlow<Pair<String?, Boolean>>(null to false)
  private val _relayHealth = MutableStateFlow<String?>(null)
  private val _fbPages = MutableStateFlow<List<MetaPageItem>>(emptyList())

  val uiState: StateFlow<StudioUiState> =
    combine(
      repository.observeUserProfile(currentUserId).catch { emit(null) },
      repository.observeSocialAccounts(currentUserId).catch { emit(emptyList()) },
      repository.observeUserVideos(currentUserId).catch { emit(emptyList()) },
      repository.observeUserPublishingJobs(currentUserId).catch { emit(emptyList()) },
      repository.observeUserScheduledPosts(currentUserId).catch { emit(emptyList()) },
      repository.observeSystemSettings().catch { emit(SystemSettings()) },
      apiConfigStore.configFlow,
      _bannerState,
      _relayHealth,
      _fbPages,
    ) { values ->
      @Suppress("UNCHECKED_CAST")
      StudioUiState(
        userProfile = values[0] as UserProfile?,
        socialAccounts = values[1] as List<SocialAccount>,
        videos = values[2] as List<VideoAsset>,
        publishingJobs = values[3] as List<PublishingJob>,
        scheduledPosts = values[4] as List<ScheduledPost>,
        systemSettings = values[5] as SystemSettings,
        apiConfig = values[6] as ApiIntegrationConfig,
        bannerMessage = (values[7] as Pair<String?, Boolean>).first,
        isBannerError = (values[7] as Pair<String?, Boolean>).second,
        relayHealthSummary = values[8] as String?,
        availableFacebookPages = values[9] as List<MetaPageItem>,
        isLoading = false,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = StudioUiState(),
    )

  init {
    viewModelScope.launch {
      repository
        .ensureUserProfile(
          userId = currentUserId,
          email = userEmail,
          displayName = userDisplayName,
          photoUrl = userPhotoUrl,
        )
        .onFailure { e ->
          val root = unwrapFirestoreError(e)
          showBanner(root.message ?: "Failed to sync creator profile.", isError = true)
        }
    }
  }

  fun dismissBanner() {
    _bannerState.value = null to false
  }

  private fun showBanner(message: String, isError: Boolean = false) {
    _bannerState.value = message to isError
  }

  // --- VIDEO UPLOAD CENTER ---

  fun selectLocalVideo(uri: Uri) {
    viewModelScope.launch {
      val metadata = extractVideoMetadata(uri)
      val settings = uiState.value.systemSettings
      val maxBytes = settings.maxVideoSizeMb * 1024L * 1024L
      if (metadata.fileSizeBytes > maxBytes && settings.maxVideoSizeMb > 0) {
        showBanner(
          "Video exceeds free fair-use limit (${settings.maxVideoSizeMb} MB max).",
          isError = true,
        )
      } else if (metadata.durationSeconds > settings.maxVideoDurationSec && metadata.durationSeconds > 0) {
        showBanner(
          "Video is ${metadata.durationSeconds}s. Short-form max is ${settings.maxVideoDurationSec}s.",
          isError = true,
        )
      }
      _editorState.update {
        it.copy(
          selectedUri = uri.toString(),
          thumbnailBitmap = metadata.thumbnail,
          title = it.title.ifBlank { metadata.displayName.substringBeforeLast(".") },
          durationSeconds = metadata.durationSeconds,
          fileSizeBytes = metadata.fileSizeBytes,
          aspectRatio = metadata.aspectRatio,
          savedVideoId = null,
          publishOutcomes = emptyMap(),
        )
      }
    }
  }

  fun updateTitle(title: String) {
    _editorState.update { it.copy(title = title.take(150)) }
  }

  fun updateDescription(description: String) {
    _editorState.update { it.copy(description = description.take(2200)) }
  }

  fun updateCaption(platform: SocialPlatform, caption: String) {
    _editorState.update {
      when (platform) {
        SocialPlatform.INSTAGRAM -> it.copy(captionInstagram = caption.take(2200))
        SocialPlatform.FACEBOOK -> it.copy(captionFacebook = caption.take(2200))
        SocialPlatform.YOUTUBE -> it.copy(captionYoutube = caption.take(5000))
      }
    }
  }

  fun addHashtag(tag: String) {
    val clean = tag.trim().replace(" ", "")
    if (clean.isBlank()) return
    val formatted = if (clean.startsWith("#")) clean else "#$clean"
    _editorState.update { state ->
      if (state.hashtags.size >= 20 || state.hashtags.contains(formatted)) state
      else state.copy(hashtags = state.hashtags + formatted)
    }
  }

  fun removeHashtag(tag: String) {
    _editorState.update { it.copy(hashtags = it.hashtags - tag) }
  }

  fun togglePlatformSelection(platform: SocialPlatform) {
    _editorState.update { state ->
      val next =
        if (platform in state.selectedPlatforms) state.selectedPlatforms - platform
        else state.selectedPlatforms + platform
      state.copy(selectedPlatforms = next)
    }
  }

  fun generateAiHashtagsAndCaptions() {
    val editor = _editorState.value
    viewModelScope.launch {
      _aiState.value = AiGenState.Loading
      aiAssistant
        .generateHashtagsAndCaptions(editor.title, editor.description)
        .onSuccess { generated ->
          _editorState.update {
            it.copy(
              hashtags = generated.hashtags,
              captionInstagram = generated.instagramCaption,
              captionFacebook = generated.facebookCaption,
              captionYoutube = generated.youtubeDescription,
              title = it.title.ifBlank { generated.youtubeTitle },
            )
          }
          _aiState.value = AiGenState.Success("Generated hashtags & platform captions.")
        }
        .onFailure { error ->
          _aiState.value = AiGenState.Error(error.localizedMessage ?: "AI generation failed.")
        }
    }
  }

  fun saveVideoToLibrary(onSaved: (String) -> Unit = {}) {
    val editor = _editorState.value
    if (editor.title.isBlank()) {
      showBanner("Please enter a video title before saving.", isError = true)
      return
    }
    if (uiState.value.userProfile?.isBlocked == true) {
      showBanner("Account suspended by administrator. Uploads disabled.", isError = true)
      return
    }
    if (uiState.value.systemSettings.maintenanceMode) {
      showBanner("Platform is currently in maintenance mode.", isError = true)
      return
    }
    viewModelScope.launch {
      val videoId = editor.savedVideoId ?: "vid_${UUID.randomUUID().toString().replace("-", "")}"
      val asset =
        VideoAsset(
          id = videoId,
          userId = currentUserId,
          title = editor.title.trim(),
          description = editor.description.trim(),
          captionInstagram = editor.captionInstagram.ifBlank { editor.description },
          captionFacebook = editor.captionFacebook.ifBlank { editor.description },
          captionYoutube = editor.captionYoutube.ifBlank { editor.description },
          hashtags = editor.hashtags,
          localUri = editor.selectedUri,
          durationSeconds = editor.durationSeconds,
          fileSizeBytes = editor.fileSizeBytes,
          aspectRatio = editor.aspectRatio,
          status = "ready",
        )
      repository
        .createVideo(asset)
        .onSuccess { id ->
          _editorState.update { it.copy(savedVideoId = id) }
          showBanner("Video project saved to Gyanverse Studio.")
          onSaved(id)
        }
        .onFailure { e ->
          showBanner(unwrapFirestoreError(e).message ?: "Failed to save video.", isError = true)
        }
    }
  }

  // --- ONE-CLICK PUBLISHING ("PUBLISH EVERYWHERE") ---

  fun publishEverywhere() {
    val editor = _editorState.value
    if (editor.selectedPlatforms.isEmpty()) {
      showBanner("Select at least one platform to publish.", isError = true)
      return
    }
    if (editor.title.isBlank()) {
      showBanner("Enter a video title before publishing.", isError = true)
      return
    }
    if (editor.selectedUri.isBlank()) {
      showBanner("Select a video from your gallery before publishing.", isError = true)
      return
    }
    if (uiState.value.userProfile?.isBlocked == true) {
      showBanner("Your account is blocked by an administrator.", isError = true)
      return
    }
    if (uiState.value.systemSettings.maintenanceMode) {
      showBanner("Publishing paused during scheduled maintenance.", isError = true)
      return
    }

    viewModelScope.launch {
      _editorState.update {
        it.copy(isPublishing = true, uploadProgressPercent = 0.1f, publishOutcomes = emptyMap())
      }
      val videoId = editor.savedVideoId ?: "vid_${UUID.randomUUID().toString().replace("-", "")}"
      val videoAsset =
        VideoAsset(
          id = videoId,
          userId = currentUserId,
          title = editor.title.trim(),
          description = editor.description.trim(),
          captionInstagram = editor.captionInstagram.ifBlank { editor.description },
          captionFacebook = editor.captionFacebook.ifBlank { editor.description },
          captionYoutube = editor.captionYoutube.ifBlank { editor.description },
          hashtags = editor.hashtags,
          localUri = editor.selectedUri,
          durationSeconds = editor.durationSeconds,
          fileSizeBytes = editor.fileSizeBytes,
          aspectRatio = editor.aspectRatio,
          status = "publishing",
        )

      val createResult = repository.createVideo(videoAsset)
      if (createResult.isFailure) {
        _editorState.update { it.copy(isPublishing = false, uploadProgressPercent = 0f) }
        showBanner(
          unwrapFirestoreError(createResult.exceptionOrNull()!!).message ?: "Failed to register video.",
          isError = true,
        )
        return@launch
      }
      _editorState.update { it.copy(savedVideoId = videoId) }

      val accounts = uiState.value.socialAccounts
      val selected = editor.selectedPlatforms.toList()
      val outcomes = mutableMapOf<SocialPlatform, PublishingEngine.PublishingJobResult>()

      selected.forEachIndexed { index, platform ->
        val caption =
          when (platform) {
            SocialPlatform.INSTAGRAM -> editor.captionInstagram.ifBlank { editor.description }
            SocialPlatform.FACEBOOK -> editor.captionFacebook.ifBlank { editor.description }
            SocialPlatform.YOUTUBE -> editor.captionYoutube.ifBlank { editor.description }
          }
        val hashSuffix =
          if (editor.hashtags.isNotEmpty()) "\n\n${editor.hashtags.joinToString(" ")}" else ""
        val result =
          publishingEngine.publishToPlatform(
            video = videoAsset,
            platform = platform,
            caption = (caption + hashSuffix).trim(),
            accounts = accounts,
          )
        outcomes[platform] = result
        _editorState.update {
          it.copy(
            uploadProgressPercent = (index + 1f) / selected.size.toFloat(),
            publishOutcomes = outcomes.toMap(),
          )
        }
      }

      val allSucceeded = outcomes.values.all { it.status == "success" }
      val anySucceeded = outcomes.values.any { it.status == "success" }
      val anyApiMissing = outcomes.values.any { it.status == "api_connection_required" }
      val finalVideoStatus =
        when {
          allSucceeded -> "published"
          anySucceeded -> "partial"
          else -> "failed"
        }
      repository.updateVideoStatus(videoId, finalVideoStatus)

      _editorState.update { it.copy(isPublishing = false) }
      when {
        allSucceeded -> showBanner("Published to all selected platforms!")
        anyApiMissing ->
          showBanner(
            "API CONNECTION REQUIRED: Configure platform OAuth or Backend Relay URL in Connections.",
            isError = true,
          )
        else -> showBanner("One or more platforms returned an error. Check job details.", isError = true)
      }
    }
  }

  fun retryJob(job: PublishingJob) {
    viewModelScope.launch {
      val video =
        uiState.value.videos.firstOrNull { it.id == job.videoId }
          ?: repository.getVideoById(job.videoId).getOrNull()
      if (video == null) {
        showBanner("Original video record not found for retry.", isError = true)
        return@launch
      }
      val platform = SocialPlatform.fromId(job.platform)
      val outcome =
        publishingEngine.publishToPlatform(
          video = video,
          platform = platform,
          caption = job.caption ?: video.description.orEmpty(),
          accounts = uiState.value.socialAccounts,
          existingJobId = job.id,
          existingRetryCount = job.retryCount,
        )
      if (outcome.status == "success") {
        showBanner("Retry succeeded for ${platform.productName}!")
      } else {
        showBanner(outcome.errorMessage ?: "API CONNECTION REQUIRED", isError = true)
      }
    }
  }

  // --- SOCIAL CONNECTIONS & OAUTH 2.0 ---

  fun saveApiEndpoints(relayUrl: String, metaAppId: String, youtubeClientId: String) {
    viewModelScope.launch {
      apiConfigStore.saveApiEndpoints(relayUrl, metaAppId, youtubeClientId)
      if (relayUrl.isNotBlank()) {
        checkBackendRelayHealth(relayUrl)
      } else {
        _relayHealth.value = null
      }
      showBanner("API integration configuration saved.")
    }
  }

  private fun checkBackendRelayHealth(relayUrl: String) {
    viewModelScope.launch {
      try {
        val response = SocialApiClientFactory.createBackendRelayService(relayUrl).checkHealth()
        if (response.isSuccessful) {
          val body = response.body()
          _relayHealth.value =
            "Relay Online (IG=${body?.instagramConfigured}, FB=${body?.facebookConfigured}, YT=${body?.youtubeConfigured})"
        } else {
          _relayHealth.value = "Relay returned HTTP ${response.code()}"
        }
      } catch (e: Exception) {
        _relayHealth.value = "Relay unreachable: ${e.localizedMessage}"
      }
    }
  }

  fun connectSocialPlatform(activityContext: Context, platform: SocialPlatform) {
    viewModelScope.launch {
      val config = apiConfigStore.getCurrentConfig()
      when (val launch = OAuthConnector.launchOAuthFlow(activityContext, platform, config)) {
        is OAuthConnector.LaunchResult.ApiConnectionRequired -> {
          repository.upsertSocialAccount(
            SocialAccount(
              id = "${currentUserId}_${platform.id}",
              userId = currentUserId,
              platform = platform.id,
              accountName = platform.productName,
              accountHandle = "Not connected",
              status = "api_required",
              oauthMode = "unconfigured",
            )
          )
          showBanner(launch.message, isError = true)
        }
        is OAuthConnector.LaunchResult.Launched -> {
          apiConfigStore.savePendingOAuth(platform.id, launch.state, launch.codeVerifier)
        }
      }
    }
  }

  fun handleOAuthCallback(uri: Uri) {
    viewModelScope.launch {
      val config = apiConfigStore.getCurrentConfig()
      val platformId =
        uri.getQueryParameter("platform") ?: config.pendingOAuthPlatform.ifBlank { return@launch }
      val error = uri.getQueryParameter("error") ?: uri.getQueryParameter("error_description")
      if (!error.isNullOrBlank()) {
        showBanner("OAuth rejected: $error", isError = true)
        return@launch
      }

      // Meta implicit / fragment or query token
      val fragmentParams =
        uri.fragment.orEmpty().split("&").mapNotNull { part ->
          val kv = part.split("=", limit = 2)
          if (kv.size == 2) kv[0] to Uri.decode(kv[1]) else null
        }.toMap()
      val token = uri.getQueryParameter("access_token") ?: fragmentParams["access_token"].orEmpty()
      val code = uri.getQueryParameter("code").orEmpty()

      if (token.isNotBlank()) {
        completeTokenConnection(platformId, token)
      } else if (code.isNotBlank()) {
        showBanner(
          "Authorization code received for $platformId. Complete token exchange via Backend Relay URL.",
          isError = false,
        )
      }
    }
  }

  private suspend fun completeTokenConnection(platformId: String, accessToken: String) {
    try {
      when (platformId) {
        "facebook" -> {
          val pagesRes = SocialApiClientFactory.createMetaService().getFacebookPages(accessToken)
          if (pagesRes.isSuccessful) {
            val pages = pagesRes.body()?.data.orEmpty()
            _fbPages.value = pages
            val firstPage = pages.firstOrNull()
            if (firstPage != null) {
              apiConfigStore.savePlatformToken("facebook", firstPage.accessToken ?: accessToken, firstPage.id)
              repository.upsertSocialAccount(
                SocialAccount(
                  id = "${currentUserId}_facebook",
                  userId = currentUserId,
                  platform = "facebook",
                  accountName = firstPage.name,
                  accountHandle = "Page ID: ${firstPage.id}",
                  pageId = firstPage.id,
                  pageName = firstPage.name,
                  status = "connected",
                  oauthMode = "pkce_oauth",
                )
              )
              showBanner("Connected Facebook Page: ${firstPage.name}")
            } else {
              showBanner("No Facebook Pages found on this Meta account.", isError = true)
            }
          } else {
            showBanner("Meta Pages API HTTP ${pagesRes.code()}", isError = true)
          }
        }
        "instagram" -> {
          apiConfigStore.savePlatformToken("instagram", accessToken)
          repository.upsertSocialAccount(
            SocialAccount(
              id = "${currentUserId}_instagram",
              userId = currentUserId,
              platform = "instagram",
              accountName = "Instagram Business",
              accountHandle = "OAuth Connected",
              status = "connected",
              oauthMode = "pkce_oauth",
            )
          )
          showBanner("Instagram OAuth session connected.")
        }
        "youtube" -> {
          val ytRes =
            SocialApiClientFactory.createYouTubeService().getMyChannels("Bearer $accessToken")
          val channel = ytRes.body()?.items?.firstOrNull()
          apiConfigStore.savePlatformToken("youtube", accessToken, channel?.id.orEmpty())
          repository.upsertSocialAccount(
            SocialAccount(
              id = "${currentUserId}_youtube",
              userId = currentUserId,
              platform = "youtube",
              accountName = channel?.snippet?.title ?: "YouTube Channel",
              accountHandle = channel?.snippet?.customUrl ?: "@youtube",
              externalAccountId = channel?.id,
              status = "connected",
              oauthMode = "pkce_oauth",
            )
          )
          showBanner("Connected YouTube channel.")
        }
      }
    } catch (e: Exception) {
      showBanner("OAuth profile lookup failed: ${e.localizedMessage}", isError = true)
    }
  }

  fun selectFacebookPage(page: MetaPageItem) {
    viewModelScope.launch {
      val currentToken = apiConfigStore.getCurrentConfig().facebookAccessToken
      apiConfigStore.savePlatformToken("facebook", page.accessToken ?: currentToken, page.id)
      repository.upsertSocialAccount(
        SocialAccount(
          id = "${currentUserId}_facebook",
          userId = currentUserId,
          platform = "facebook",
          accountName = page.name,
          accountHandle = "Page ID: ${page.id}",
          pageId = page.id,
          pageName = page.name,
          status = "connected",
          oauthMode = "pkce_oauth",
        )
      )
      showBanner("Selected Facebook Page: ${page.name}")
    }
  }

  fun disconnectPlatform(account: SocialAccount) {
    viewModelScope.launch {
      apiConfigStore.clearPlatformToken(account.platform)
      repository.disconnectSocialAccount(account.id)
      showBanner("Disconnected ${SocialPlatform.fromId(account.platform).productName}.")
    }
  }

  // --- SCHEDULER ---

  fun schedulePost(
    video: VideoAsset,
    platforms: Set<SocialPlatform>,
    scheduledForMillis: Long,
  ) {
    if (platforms.isEmpty()) {
      showBanner("Select at least one platform to schedule.", isError = true)
      return
    }
    if (scheduledForMillis <= System.currentTimeMillis() + 30_000L) {
      showBanner("Choose a date and time at least 1 minute in the future.", isError = true)
      return
    }
    viewModelScope.launch {
      val postId = "sched_${UUID.randomUUID().toString().replace("-", "")}"
      val post =
        ScheduledPost(
          id = postId,
          userId = currentUserId,
          videoId = video.id,
          videoTitle = video.title,
          platforms = platforms.map { it.id },
          captionInstagram = video.captionInstagram,
          captionFacebook = video.captionFacebook,
          captionYoutube = video.captionYoutube,
          scheduledForMillis = scheduledForMillis,
          status = "scheduled",
        )
      repository
        .createScheduledPost(post)
        .onSuccess { id ->
          ScheduledPublishWorker.enqueue(appContext, id, scheduledForMillis)
          showBanner("Scheduled '${video.title}' for background publishing.")
        }
        .onFailure { e ->
          showBanner(unwrapFirestoreError(e).message ?: "Failed to schedule post.", isError = true)
        }
    }
  }

  fun reschedulePost(post: ScheduledPost, newMillis: Long, newPlatforms: Set<SocialPlatform>) {
    if (newPlatforms.isEmpty()) {
      showBanner("Select at least one platform.", isError = true)
      return
    }
    viewModelScope.launch {
      repository
        .updateScheduledPost(
          postId = post.id,
          scheduledForMillis = newMillis,
          platforms = newPlatforms.map { it.id },
          status = "scheduled",
        )
        .onSuccess {
          ScheduledPublishWorker.enqueue(appContext, post.id, newMillis)
          showBanner("Schedule updated.")
        }
        .onFailure { e ->
          showBanner(unwrapFirestoreError(e).message ?: "Failed to update schedule.", isError = true)
        }
    }
  }

  fun cancelScheduledPost(post: ScheduledPost) {
    viewModelScope.launch {
      ScheduledPublishWorker.cancel(appContext, post.id)
      repository
        .updateScheduledPost(postId = post.id, status = "cancelled")
        .onSuccess { showBanner("Cancelled scheduled post.") }
        .onFailure { e ->
          showBanner(unwrapFirestoreError(e).message ?: "Failed to cancel schedule.", isError = true)
        }
    }
  }

  // --- SETTINGS ---

  fun updateProfile(
    displayName: String,
    bio: String,
    notifyOnPublish: Boolean,
    notifyOnFailure: Boolean,
  ) {
    if (displayName.isBlank()) {
      showBanner("Display name cannot be empty.", isError = true)
      return
    }
    viewModelScope.launch {
      apiConfigStore.setPushNotificationsEnabled(notifyOnPublish)
      repository
        .updateUserProfile(currentUserId, displayName, bio, notifyOnPublish, notifyOnFailure)
        .onSuccess { showBanner("Profile & notification preferences saved.") }
        .onFailure { e ->
          showBanner(unwrapFirestoreError(e).message ?: "Failed to save profile.", isError = true)
        }
    }
  }

  fun saveAppDownloadUrl(url: String) {
    val trimmed = url.trim()
    if (trimmed.isNotBlank() && !trimmed.startsWith("https://", ignoreCase = true)) {
      showBanner("Please enter a valid HTTPS APK or release download URL.", isError = true)
      return
    }
    viewModelScope.launch {
      apiConfigStore.saveAppDownloadUrl(trimmed)
      showBanner(
        if (trimmed.isBlank()) "Cleared custom APK download link."
        else "Saved mobile APK download link."
      )
    }
  }

  fun deleteAccountData(onDeleted: () -> Unit) {
    viewModelScope.launch {
      repository
        .deleteCurrentUserAccountData(currentUserId)
        .onSuccess { onDeleted() }
        .onFailure { e ->
          showBanner(unwrapFirestoreError(e).message ?: "Failed to delete account.", isError = true)
        }
    }
  }

  private suspend fun extractVideoMetadata(uri: Uri): ExtractedVideoMeta =
    withContext(Dispatchers.IO) {
      var displayName = "Creator_Video.mp4"
      var fileSize = 0L
      try {
        appContext.contentResolver
          .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
          ?.use { cursor ->
            if (cursor.moveToFirst()) {
              val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
              val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
              if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
              if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
            }
          }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to query OpenableColumns", e)
      }

      var durationSec = 0L
      var aspect = "9:16"
      var thumb: Bitmap? = null
      val retriever = MediaMetadataRetriever()
      try {
        retriever.setDataSource(appContext, uri)
        val durationMs =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        durationSec = (durationMs / 1000L).coerceAtLeast(1L)
        val width =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            ?: 1080
        val height =
          retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            ?: 1920
        aspect = if (height >= width) "9:16" else "16:9"
        thumb = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
      } catch (e: Exception) {
        Log.w(TAG, "MediaMetadataRetriever failed", e)
      } finally {
        try {
          retriever.release()
        } catch (_: Exception) {}
      }

      ExtractedVideoMeta(displayName, fileSize, durationSec, aspect, thumb)
    }

  private fun unwrapFirestoreError(error: Throwable): Throwable =
    generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull() ?: error

  private data class ExtractedVideoMeta(
    val displayName: String,
    val fileSizeBytes: Long,
    val durationSeconds: Long,
    val aspectRatio: String,
    val thumbnail: Bitmap?,
  )

  private companion object {
    const val TAG = "StudioViewModel"
  }
}
