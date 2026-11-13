package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.model.PublishingJob
import com.example.data.model.SocialAccount
import com.example.data.model.SocialPlatform
import com.example.data.model.VideoAsset
import com.example.data.repository.StudioRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source

class PublishingEngine(
  private val context: Context,
  private val repository: StudioRepository,
  private val apiConfigStore: ApiConfigStore,
) {

  suspend fun publishToPlatform(
    video: VideoAsset,
    platform: SocialPlatform,
    caption: String,
    accounts: List<SocialAccount>,
    existingJobId: String? = null,
    existingRetryCount: Long = 0L,
  ): PublishingJobResult {
    val user = Firebase.auth.currentUser
      ?: return PublishingJobResult(
        jobId = existingJobId.orEmpty(),
        status = "failed",
        errorMessage = "Authentication required before publishing.",
      )

    val jobId =
      if (!existingJobId.isNullOrBlank()) {
        repository.updatePublishingJob(
          jobId = existingJobId,
          status = "uploading",
          progressPercent = 10L,
          errorMessage = "",
          retryCount = existingRetryCount + 1L,
        )
        existingJobId
      } else {
        repository
          .createPublishingJob(
            PublishingJob(
              userId = user.uid,
              videoId = video.id,
              videoTitle = video.title,
              platform = platform.id,
              caption = caption,
              status = "queued",
              progressPercent = 5L,
              retryCount = 0L,
            )
          )
          .getOrElse { ex ->
            return PublishingJobResult(
              jobId = "",
              status = "failed",
              errorMessage = ex.localizedMessage ?: "Failed to create publishing job record.",
            )
          }
      }

    val config = apiConfigStore.getCurrentConfig()
    val account = accounts.firstOrNull { it.platform == platform.id && it.status == "connected" }

    // 1. Check Backend Relay first (server-side Meta/YouTube token handling)
    if (config.backendRelayUrl.isNotBlank()) {
      return executeViaBackendRelay(
        jobId = jobId,
        relayUrl = config.backendRelayUrl,
        userId = user.uid,
        video = video,
        platform = platform,
        caption = caption,
      )
    }

    // 2. Check Direct Official API OAuth connection + access token
    if (account == null || !config.isPlatformConfigured(platform.id)) {
      val msg =
        "API CONNECTION REQUIRED: Connect ${platform.productName} via OAuth 2.0 or configure Backend Relay URL in Connections."
      repository.updatePublishingJob(
        jobId = jobId,
        status = "api_connection_required",
        progressPercent = 0L,
        errorMessage = msg,
      )
      repository.logSystemEvent(
        action = "PUBLISH_BLOCKED_UNCONFIGURED",
        platform = platform.id,
        severity = "warning",
        details = msg,
      )
      return PublishingJobResult(
        jobId = jobId,
        status = "api_connection_required",
        errorMessage = msg,
      )
    }

    // 3. Execute Official Platform API integration
    return try {
      when (platform) {
        SocialPlatform.YOUTUBE -> publishToYouTubeShorts(jobId, video, caption, config.youtubeAccessToken)
        SocialPlatform.FACEBOOK ->
          publishToFacebookReels(
            jobId = jobId,
            video = video,
            caption = caption,
            pageId = config.facebookPageId.ifBlank { account.pageId.orEmpty() },
            accessToken = config.facebookAccessToken,
          )
        SocialPlatform.INSTAGRAM ->
          publishToInstagramReels(
            jobId = jobId,
            video = video,
            caption = caption,
            igUserId = config.instagramUserId.ifBlank { account.externalAccountId.orEmpty() },
            accessToken = config.instagramAccessToken,
          )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Publishing failed for ${platform.id}", e)
      val realError = e.localizedMessage ?: "Platform API rejected upload."
      repository.updatePublishingJob(
        jobId = jobId,
        status = "failed",
        progressPercent = 0L,
        errorMessage = realError,
      )
      repository.logSystemEvent(
        action = "PUBLISH_ERROR",
        platform = platform.id,
        severity = "error",
        details = realError,
      )
      PublishingJobResult(jobId = jobId, status = "failed", errorMessage = realError)
    }
  }

  private suspend fun executeViaBackendRelay(
    jobId: String,
    relayUrl: String,
    userId: String,
    video: VideoAsset,
    platform: SocialPlatform,
    caption: String,
  ): PublishingJobResult {
    return try {
      repository.updatePublishingJob(jobId, status = "uploading", progressPercent = 25L)
      val idToken = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token.orEmpty()
      if (idToken.isBlank()) {
        val msg = "Firebase ID token unavailable for backend relay authorization."
        repository.updatePublishingJob(jobId, status = "failed", progressPercent = 0L, errorMessage = msg)
        return PublishingJobResult(jobId, "failed", errorMessage = msg)
      }
      val relayApi = SocialApiClientFactory.createBackendRelayService(relayUrl)
      val response =
        relayApi.publishVideo(
          bearerToken = "Bearer $idToken",
          request =
            RelayPublishRequest(
              userId = userId,
              videoId = video.id,
              title = video.title,
              caption = caption,
              platform = platform.id,
              videoUrl = video.localUri,
            ),
        )
      if (!response.isSuccessful) {
        val rawBody = response.errorBody()?.string().orEmpty().ifBlank { "HTTP ${response.code()}" }
        val errMsg = "Backend Relay HTTP ${response.code()}: $rawBody"
        repository.updatePublishingJob(jobId, status = "failed", progressPercent = 0L, errorMessage = errMsg)
        repository.logSystemEvent("RELAY_PUBLISH_FAILED", platform.id, "error", errMsg)
        return PublishingJobResult(jobId, "failed", errorMessage = errMsg)
      }
      val body = response.body()
      if (body?.success == true) {
        repository.updatePublishingJob(
          jobId = jobId,
          status = "success",
          progressPercent = 100L,
          externalPostUrl = body.externalPostUrl.orEmpty(),
        )
        PublishingJobResult(jobId, "success", externalPostUrl = body.externalPostUrl)
      } else {
        val status = if (body?.code == "API_CONNECTION_REQUIRED") "api_connection_required" else "failed"
        val errMsg = body?.error ?: "API CONNECTION REQUIRED: Relay did not return success."
        repository.updatePublishingJob(jobId, status = status, progressPercent = 0L, errorMessage = errMsg)
        PublishingJobResult(jobId, status, errorMessage = errMsg)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      val errMsg = "Backend Relay unreachable: ${e.localizedMessage ?: e.javaClass.simpleName}"
      repository.updatePublishingJob(jobId, status = "failed", progressPercent = 0L, errorMessage = errMsg)
      repository.logSystemEvent("RELAY_EXCEPTION", platform.id, "error", errMsg)
      PublishingJobResult(jobId, "failed", errorMessage = errMsg)
    }
  }

  private suspend fun publishToYouTubeShorts(
    jobId: String,
    video: VideoAsset,
    caption: String,
    accessToken: String,
  ): PublishingJobResult {
    val uriString = video.localUri
    if (uriString.isNullOrBlank()) {
      val msg = "No local video URI selected for YouTube resumable upload."
      repository.updatePublishingJob(jobId, "failed", 0L, errorMessage = msg)
      return PublishingJobResult(jobId, "failed", errorMessage = msg)
    }
    val uri = Uri.parse(uriString)
    val contentLength = resolveContentLength(uri, video.fileSizeBytes)
    repository.updatePublishingJob(jobId, "uploading", 30L)

    val ytApi = SocialApiClientFactory.createYouTubeService()
    val initResponse =
      ytApi.initiateResumableUpload(
        bearerToken = "Bearer $accessToken",
        contentLength = contentLength,
        metadata =
          YouTubeVideoMetadata(
            snippet =
              YouTubeVideoSnippet(
                title = video.title.take(100),
                description = "$caption\n#Shorts",
                tags = video.hashtags.map { it.removePrefix("#") },
              ),
            status = YouTubeVideoStatus(privacyStatus = "public"),
          ),
      )

    if (!initResponse.isSuccessful) {
      val err = "YouTube API HTTP ${initResponse.code()}: ${initResponse.errorBody()?.string().orEmpty()}"
      repository.updatePublishingJob(jobId, "failed", 0L, errorMessage = err)
      return PublishingJobResult(jobId, "failed", errorMessage = err)
    }

    val sessionUrl = initResponse.headers()["Location"]
    if (sessionUrl.isNullOrBlank()) {
      val err = "YouTube API did not return a resumable upload Location header."
      repository.updatePublishingJob(jobId, "failed", 0L, errorMessage = err)
      return PublishingJobResult(jobId, "failed", errorMessage = err)
    }

    repository.updatePublishingJob(jobId, "uploading", 65L)
    val body = createUriRequestBody(uri, contentLength)
    val uploadResponse = ytApi.uploadResumableVideoBinary(uploadSessionUrl = sessionUrl, body = body)
    if (!uploadResponse.isSuccessful) {
      val err = "YouTube upload HTTP ${uploadResponse.code()}: ${uploadResponse.errorBody()?.string().orEmpty()}"
      repository.updatePublishingJob(jobId, "failed", 0L, errorMessage = err)
      return PublishingJobResult(jobId, "failed", errorMessage = err)
    }

    val ytId = uploadResponse.body()?.id.orEmpty()
    val postUrl = if (ytId.isNotBlank()) "https://youtube.com/shorts/$ytId" else ""
    repository.updatePublishingJob(jobId, "success", 100L, externalPostUrl = postUrl)
    return PublishingJobResult(jobId, "success", externalPostUrl = postUrl)
  }

  private suspend fun publishToFacebookReels(
    jobId: String,
    video: VideoAsset,
    caption: String,
    pageId: String,
    accessToken: String,
  ): PublishingJobResult {
    val uriString = video.localUri
    if (uriString.isNullOrBlank() || pageId.isBlank()) {
      val msg = "API CONNECTION REQUIRED: Facebook Page ID or local video missing."
      repository.updatePublishingJob(jobId, "api_connection_required", 0L, errorMessage = msg)
      return PublishingJobResult(jobId, "api_connection_required", errorMessage = msg)
    }
    val uri = Uri.parse(uriString)
    val contentLength = resolveContentLength(uri, video.fileSizeBytes)
    val metaApi = SocialApiClientFactory.createMetaService()

    repository.updatePublishingJob(jobId, "uploading", 25L)
    val startRes = metaApi.startFacebookReelUpload(pageId = pageId, accessToken = accessToken)
    if (!startRes.isSuccessful) {
      val err = "Meta FB Reels Start HTTP ${startRes.code()}: ${startRes.errorBody()?.string().orEmpty()}"
      repository.updatePublishingJob(jobId, "failed", 0L, errorMessage = err)
      return PublishingJobResult(jobId, "failed", errorMessage = err)
    }
    val fbVideoId = startRes.body()?.videoId.orEmpty()
    val uploadUrl = startRes.body()?.uploadUrl.orEmpty()
    if (fbVideoId.isBlank() || uploadUrl.isBlank()) {
      val err = "Meta Graph API did not return Facebook Reel upload_url/video_id."
      repository.updatePublishingJob(jobId, "failed", 0L, errorMessage = err)
      return PublishingJobResult(jobId, "failed", errorMessage = err)
    }

    repository.updatePublishingJob(jobId, "uploading", 65L)
    val binaryRes =
      metaApi.uploadFacebookReelBinary(
        uploadUrl = uploadUrl,
        oauthHeader = "OAuth $accessToken",
        fileSize = contentLength,
        body = createUriRequestBody(uri, contentLength),
      )
    if (!binaryRes.isSuccessful) {
      val err = "Meta FB Reels Binary HTTP ${binaryRes.code()}: ${binaryRes.errorBody()?.string().orEmpty()}"
      repository.updatePublishingJob(jobId, "failed", 0L, errorMessage = err)
      return PublishingJobResult(jobId, "failed", errorMessage = err)
    }

    val finishRes =
      metaApi.finishFacebookReelUpload(
        pageId = pageId,
        videoId = fbVideoId,
        description = caption,
        accessToken = accessToken,
      )
    if (!finishRes.isSuccessful || finishRes.body()?.success != true) {
      val err = "Meta FB Reels Finish HTTP ${finishRes.code()}: ${finishRes.errorBody()?.string().orEmpty()}"
      repository.updatePublishingJob(jobId, "failed", 0L, errorMessage = err)
      return PublishingJobResult(jobId, "failed", errorMessage = err)
    }

    val postUrl = "https://www.facebook.com/reel/$fbVideoId"
    repository.updatePublishingJob(jobId, "success", 100L, externalPostUrl = postUrl)
    return PublishingJobResult(jobId, "success", externalPostUrl = postUrl)
  }

  private suspend fun publishToInstagramReels(
    jobId: String,
    video: VideoAsset,
    caption: String,
    igUserId: String,
    accessToken: String,
  ): PublishingJobResult {
    val videoUrl = video.localUri.orEmpty()
    if (!videoUrl.startsWith("http")) {
      val msg =
        "API CONNECTION REQUIRED: Meta Instagram Reels Graph API requires a public HTTPS video_url (via Backend Relay URL)."
      repository.updatePublishingJob(jobId, "api_connection_required", 0L, errorMessage = msg)
      return PublishingJobResult(jobId, "api_connection_required", errorMessage = msg)
    }
    val metaApi = SocialApiClientFactory.createMetaService()
    repository.updatePublishingJob(jobId, "uploading", 35L)
    val containerRes =
      metaApi.createInstagramReelContainer(
        igUserId = igUserId,
        videoUrl = videoUrl,
        caption = caption,
        accessToken = accessToken,
      )
    if (!containerRes.isSuccessful || containerRes.body()?.id.isNullOrBlank()) {
      val err = "Instagram Container HTTP ${containerRes.code()}: ${containerRes.errorBody()?.string().orEmpty()}"
      repository.updatePublishingJob(jobId, "failed", 0L, errorMessage = err)
      return PublishingJobResult(jobId, "failed", errorMessage = err)
    }
    val containerId = containerRes.body()!!.id!!
    repository.updatePublishingJob(jobId, "uploading", 75L)
    val publishRes =
      metaApi.publishInstagramMedia(
        igUserId = igUserId,
        creationId = containerId,
        accessToken = accessToken,
      )
    if (!publishRes.isSuccessful || publishRes.body()?.id.isNullOrBlank()) {
      val err = "Instagram Publish HTTP ${publishRes.code()}: ${publishRes.errorBody()?.string().orEmpty()}"
      repository.updatePublishingJob(jobId, "failed", 0L, errorMessage = err)
      return PublishingJobResult(jobId, "failed", errorMessage = err)
    }
    val mediaId = publishRes.body()!!.id!!
    val postUrl = "https://www.instagram.com/reel/$mediaId/"
    repository.updatePublishingJob(jobId, "success", 100L, externalPostUrl = postUrl)
    return PublishingJobResult(jobId, "success", externalPostUrl = postUrl)
  }

  private fun resolveContentLength(uri: Uri, fallbackSize: Long): Long {
    return try {
      context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
        if (c.moveToFirst()) {
          val idx = c.getColumnIndex(OpenableColumns.SIZE)
          if (idx >= 0) c.getLong(idx) else fallbackSize
        } else fallbackSize
      } ?: fallbackSize
    } catch (_: Exception) {
      fallbackSize
    }.coerceAtLeast(1L)
  }

  private fun createUriRequestBody(uri: Uri, length: Long): RequestBody {
    return object : RequestBody() {
      override fun contentType() = "video/mp4".toMediaTypeOrNull()
      override fun contentLength(): Long = length
      override fun writeTo(sink: BufferedSink) {
        val stream =
          context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open selected video stream.")
        stream.use { input -> sink.writeAll(input.source()) }
      }
    }
  }

  data class PublishingJobResult(
    val jobId: String,
    val status: String,
    val externalPostUrl: String? = null,
    val errorMessage: String? = null,
  )

  private companion object {
    const val TAG = "PublishingEngine"
  }
}
