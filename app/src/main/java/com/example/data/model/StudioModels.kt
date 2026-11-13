package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SocialPlatform(
  val id: String,
  val displayName: String,
  val shortLabel: String,
  val productName: String,
) {
  INSTAGRAM("instagram", "Instagram", "IG Reels", "Instagram Reels"),
  FACEBOOK("facebook", "Facebook", "FB Reels", "Facebook Reels"),
  YOUTUBE("youtube", "YouTube", "YT Shorts", "YouTube Shorts");

  companion object {
    fun fromId(id: String): SocialPlatform =
      entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: INSTAGRAM
  }
}

@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val userId: String = "",
  val email: String = "",
  val displayName: String = "",
  val photoUrl: String? = null,
  val bio: String? = null,
  val role: String = "creator",
  val isBlocked: Boolean = false,
  val notifyOnPublish: Boolean = true,
  val notifyOnFailure: Boolean = true,
  @ServerTimestamp val lastActiveAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SocialAccount(
  @DocumentId val id: String = "",
  val userId: String = "",
  val platform: String = "instagram",
  val accountName: String = "",
  val accountHandle: String = "",
  val externalAccountId: String? = null,
  val pageId: String? = null,
  val pageName: String? = null,
  val avatarUrl: String? = null,
  val status: String = "api_required",
  val oauthMode: String = "unconfigured",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class VideoAsset(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val description: String? = null,
  val captionInstagram: String? = null,
  val captionFacebook: String? = null,
  val captionYoutube: String? = null,
  val hashtags: List<String> = emptyList(),
  val localUri: String? = null,
  val thumbnailUri: String? = null,
  val durationSeconds: Long = 0L,
  val fileSizeBytes: Long = 0L,
  val aspectRatio: String? = null,
  val status: String = "ready",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class PublishingJob(
  @DocumentId val id: String = "",
  val userId: String = "",
  val videoId: String = "",
  val videoTitle: String = "",
  val platform: String = "instagram",
  val caption: String? = null,
  val status: String = "queued",
  val progressPercent: Long = 0L,
  val externalPostUrl: String? = null,
  val errorMessage: String? = null,
  val retryCount: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class ScheduledPost(
  @DocumentId val id: String = "",
  val userId: String = "",
  val videoId: String = "",
  val videoTitle: String = "",
  val platforms: List<String> = emptyList(),
  val captionInstagram: String? = null,
  val captionFacebook: String? = null,
  val captionYoutube: String? = null,
  val scheduledForMillis: Long = 0L,
  val status: String = "scheduled",
  val errorMessage: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class AdminLog(
  @DocumentId val id: String = "",
  val actorId: String = "",
  val actorEmail: String = "",
  val action: String = "",
  val platform: String? = null,
  val severity: String = "info",
  val details: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class SystemSettings(
  @DocumentId val id: String = "",
  val maxDailyUploadsPerUser: Long = 25L,
  val maxVideoSizeMb: Long = 200L,
  val maxVideoDurationSec: Long = 90L,
  val maintenanceMode: Boolean = false,
  val backendRelayUrl: String? = null,
  val metaAppId: String? = null,
  val youtubeClientId: String? = null,
  val appDownloadUrl: String? = null,
  val updatedBy: String = "",
  @ServerTimestamp val updatedAt: Timestamp? = null,
)
