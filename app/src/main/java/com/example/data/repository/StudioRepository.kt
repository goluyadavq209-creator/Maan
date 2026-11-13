package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.AdminLog
import com.example.data.model.PublishingJob
import com.example.data.model.ScheduledPost
import com.example.data.model.SocialAccount
import com.example.data.model.SystemSettings
import com.example.data.model.UserProfile
import com.example.data.model.VideoAsset
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class StudioRepository(private val db: FirebaseFirestore) {

  constructor(
    context: Context
  ) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private val auth = Firebase.auth

  private fun requireUserId(): String =
    auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

  // --- USER PROFILE ---

  fun observeUserProfile(userId: String): Flow<UserProfile?> =
    db.collection(COL_USERS)
      .document(userId)
      .snapshots()
      .map { snap ->
        snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logSnapshotError(error, OperationType.GET, "$COL_USERS/$userId", userId)
        throw error
      }

  suspend fun ensureUserProfile(
    userId: String,
    email: String,
    displayName: String,
    photoUrl: String?,
  ): Result<UserProfile> = runCatching {
    val docRef = db.collection(COL_USERS).document(userId)
    val existing =
      try {
        docRef.get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, docRef.path, auth)
        throw e
      }

    if (existing.exists()) {
      val updates =
        mapOf(
          "lastActiveAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
      try {
        docRef.update(updates).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
        throw e
      }
      return@runCatching docRef
        .get()
        .await()
        .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?: UserProfile(id = userId, userId = userId, email = email, displayName = displayName)
    }

    val safeName = displayName.trim().ifEmpty { email.substringBefore("@") }.take(100)
    val isBootstrapAdmin = email.equals(SUPER_ADMIN_EMAIL, ignoreCase = true)
    val payload =
      mutableMapOf<String, Any>(
        "userId" to userId,
        "email" to email.take(254),
        "displayName" to safeName,
        "role" to if (isBootstrapAdmin) "admin" else "creator",
        "isBlocked" to false,
        "notifyOnPublish" to true,
        "notifyOnFailure" to true,
        "lastActiveAt" to FieldValue.serverTimestamp(),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    if (!photoUrl.isNullOrBlank()) {
      payload["photoUrl"] = photoUrl.take(500)
    }
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
    UserProfile(
      id = userId,
      userId = userId,
      email = email,
      displayName = safeName,
      photoUrl = photoUrl,
      role = if (isBootstrapAdmin) "admin" else "creator",
    )
  }

  suspend fun updateUserProfile(
    userId: String,
    displayName: String,
    bio: String?,
    notifyOnPublish: Boolean,
    notifyOnFailure: Boolean,
  ): Result<Unit> = runCatching {
    val docRef = db.collection(COL_USERS).document(userId)
    val payload =
      mutableMapOf<String, Any>(
        "displayName" to displayName.trim().take(100),
        "notifyOnPublish" to notifyOnPublish,
        "notifyOnFailure" to notifyOnFailure,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    if (bio != null) {
      payload["bio"] = bio.trim().take(300)
    }
    try {
      docRef.update(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun deleteCurrentUserAccountData(userId: String): Result<Unit> = runCatching {
    val docRef = db.collection(COL_USERS).document(userId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  // --- SOCIAL ACCOUNTS ---

  fun observeSocialAccounts(userId: String): Flow<List<SocialAccount>> =
    db.collection(COL_SOCIAL_ACCOUNTS)
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snap ->
        snap.toObjects(
          SocialAccount::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, COL_SOCIAL_ACCOUNTS, userId)
        throw error
      }

  suspend fun upsertSocialAccount(account: SocialAccount): Result<String> = runCatching {
    val uid = account.userId.ifBlank { requireUserId() }
    val docId = account.id.ifBlank { "${uid}_${account.platform}" }
    val docRef = db.collection(COL_SOCIAL_ACCOUNTS).document(docId)
    val existing =
      try {
        docRef.get().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.GET, docRef.path, auth)
        throw e
      }

    val payload =
      mutableMapOf<String, Any>(
        "userId" to uid,
        "platform" to account.platform,
        "accountName" to account.accountName.take(120),
        "accountHandle" to account.accountHandle.take(120),
        "status" to account.status,
        "oauthMode" to account.oauthMode,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    account.externalAccountId?.takeIf { it.isNotBlank() }?.let {
      payload["externalAccountId"] = it.take(128)
    }
    account.pageId?.takeIf { it.isNotBlank() }?.let { payload["pageId"] = it.take(128) }
    account.pageName?.takeIf { it.isNotBlank() }?.let { payload["pageName"] = it.take(120) }
    account.avatarUrl?.takeIf { it.isNotBlank() }?.let { payload["avatarUrl"] = it.take(500) }

    try {
      if (existing.exists()) {
        docRef.update(payload).await()
      } else {
        payload["createdAt"] = FieldValue.serverTimestamp()
        docRef.set(payload).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(
        e,
        if (existing.exists()) OperationType.UPDATE else OperationType.CREATE,
        docRef.path,
        auth,
      )
      throw e
    }
    docId
  }

  suspend fun disconnectSocialAccount(accountId: String): Result<Unit> = runCatching {
    val docRef = db.collection(COL_SOCIAL_ACCOUNTS).document(accountId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  // --- VIDEOS ---

  fun observeUserVideos(userId: String): Flow<List<VideoAsset>> =
    db.collection(COL_VIDEOS)
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(VideoAsset::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, COL_VIDEOS, userId)
        throw error
      }

  suspend fun getVideoById(videoId: String): Result<VideoAsset?> = runCatching {
    val docRef = db.collection(COL_VIDEOS).document(videoId)
    try {
      docRef
        .get()
        .await()
        .toObject(VideoAsset::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      throw e
    }
  }

  suspend fun createVideo(video: VideoAsset): Result<String> = runCatching {
    val uid = video.userId.ifBlank { requireUserId() }
    val docRef =
      if (video.id.isNotBlank()) db.collection(COL_VIDEOS).document(video.id)
      else db.collection(COL_VIDEOS).document()
    val payload =
      mutableMapOf<String, Any>(
        "userId" to uid,
        "title" to video.title.trim().take(150),
        "hashtags" to video.hashtags.take(20).map { it.take(60) },
        "durationSeconds" to video.durationSeconds.coerceIn(0L, 3600L),
        "fileSizeBytes" to video.fileSizeBytes.coerceIn(0L, 2147483648L),
        "status" to video.status,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    video.description?.takeIf { it.isNotBlank() }?.let { payload["description"] = it.take(2200) }
    video.captionInstagram?.takeIf { it.isNotBlank() }?.let {
      payload["captionInstagram"] = it.take(2200)
    }
    video.captionFacebook?.takeIf { it.isNotBlank() }?.let {
      payload["captionFacebook"] = it.take(2200)
    }
    video.captionYoutube?.takeIf { it.isNotBlank() }?.let {
      payload["captionYoutube"] = it.take(5000)
    }
    video.localUri?.takeIf { it.isNotBlank() }?.let { payload["localUri"] = it.take(500) }
    video.thumbnailUri?.takeIf { it.isNotBlank() }?.let { payload["thumbnailUri"] = it.take(500) }
    video.aspectRatio?.takeIf { it.isNotBlank() }?.let { payload["aspectRatio"] = it.take(20) }

    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
    docRef.id
  }

  suspend fun updateVideoStatus(videoId: String, status: String): Result<Unit> = runCatching {
    val docRef = db.collection(COL_VIDEOS).document(videoId)
    try {
      docRef
        .update(mapOf("status" to status, "updatedAt" to FieldValue.serverTimestamp()))
        .await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  // --- PUBLISHING JOBS ---

  fun observeUserPublishingJobs(userId: String): Flow<List<PublishingJob>> =
    db.collection(COL_PUBLISHING_JOBS)
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(PublishingJob::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, COL_PUBLISHING_JOBS, userId)
        throw error
      }

  suspend fun createPublishingJob(job: PublishingJob): Result<String> = runCatching {
    val uid = job.userId.ifBlank { requireUserId() }
    val docRef =
      if (job.id.isNotBlank()) db.collection(COL_PUBLISHING_JOBS).document(job.id)
      else db.collection(COL_PUBLISHING_JOBS).document()
    val payload =
      mutableMapOf<String, Any>(
        "userId" to uid,
        "videoId" to job.videoId,
        "videoTitle" to job.videoTitle.trim().take(150),
        "platform" to job.platform,
        "status" to job.status,
        "progressPercent" to job.progressPercent.coerceIn(0L, 100L),
        "retryCount" to job.retryCount.coerceIn(0L, 20L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    job.caption?.takeIf { it.isNotBlank() }?.let { payload["caption"] = it.take(5000) }
    job.externalPostUrl?.takeIf { it.isNotBlank() }?.let {
      payload["externalPostUrl"] = it.take(500)
    }
    job.errorMessage?.takeIf { it.isNotBlank() }?.let { payload["errorMessage"] = it.take(1000) }

    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
    docRef.id
  }

  suspend fun updatePublishingJob(
    jobId: String,
    status: String,
    progressPercent: Long,
    externalPostUrl: String? = null,
    errorMessage: String? = null,
    retryCount: Long? = null,
  ): Result<Unit> = runCatching {
    val docRef = db.collection(COL_PUBLISHING_JOBS).document(jobId)
    val payload =
      mutableMapOf<String, Any>(
        "status" to status,
        "progressPercent" to progressPercent.coerceIn(0L, 100L),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    if (externalPostUrl != null) payload["externalPostUrl"] = externalPostUrl.take(500)
    if (errorMessage != null) payload["errorMessage"] = errorMessage.take(1000)
    if (retryCount != null) payload["retryCount"] = retryCount.coerceIn(0L, 20L)

    try {
      docRef.update(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  // --- SCHEDULED POSTS ---

  fun observeUserScheduledPosts(userId: String): Flow<List<ScheduledPost>> =
    db.collection(COL_SCHEDULED_POSTS)
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(ScheduledPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedBy { it.scheduledForMillis }
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, COL_SCHEDULED_POSTS, userId)
        throw error
      }

  suspend fun createScheduledPost(post: ScheduledPost): Result<String> = runCatching {
    val uid = post.userId.ifBlank { requireUserId() }
    val docRef =
      if (post.id.isNotBlank()) db.collection(COL_SCHEDULED_POSTS).document(post.id)
      else db.collection(COL_SCHEDULED_POSTS).document()
    val payload =
      mutableMapOf<String, Any>(
        "userId" to uid,
        "videoId" to post.videoId,
        "videoTitle" to post.videoTitle.trim().take(150),
        "platforms" to post.platforms.distinct().take(3),
        "scheduledForMillis" to post.scheduledForMillis,
        "status" to post.status,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    post.captionInstagram?.takeIf { it.isNotBlank() }?.let {
      payload["captionInstagram"] = it.take(2200)
    }
    post.captionFacebook?.takeIf { it.isNotBlank() }?.let {
      payload["captionFacebook"] = it.take(2200)
    }
    post.captionYoutube?.takeIf { it.isNotBlank() }?.let {
      payload["captionYoutube"] = it.take(5000)
    }
    post.errorMessage?.takeIf { it.isNotBlank() }?.let { payload["errorMessage"] = it.take(1000) }

    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
    docRef.id
  }

  suspend fun updateScheduledPost(
    postId: String,
    scheduledForMillis: Long? = null,
    platforms: List<String>? = null,
    status: String? = null,
    errorMessage: String? = null,
  ): Result<Unit> = runCatching {
    val docRef = db.collection(COL_SCHEDULED_POSTS).document(postId)
    val payload = mutableMapOf<String, Any>("updatedAt" to FieldValue.serverTimestamp())
    if (scheduledForMillis != null) payload["scheduledForMillis"] = scheduledForMillis
    if (platforms != null) payload["platforms"] = platforms.distinct().take(3)
    if (status != null) payload["status"] = status
    if (errorMessage != null) payload["errorMessage"] = errorMessage.take(1000)

    try {
      docRef.update(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
      throw e
    }
  }

  suspend fun getScheduledPostById(postId: String): Result<ScheduledPost?> = runCatching {
    val docRef = db.collection(COL_SCHEDULED_POSTS).document(postId)
    try {
      docRef
        .get()
        .await()
        .toObject(ScheduledPost::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path, auth)
      throw e
    }
  }

  // --- SYSTEM SETTINGS & LOGS ---

  fun observeSystemSettings(): Flow<SystemSettings> =
    db.collection(COL_SYSTEM_SETTINGS)
      .document(GLOBAL_SETTINGS_DOC)
      .snapshots()
      .map { snap ->
        snap.toObject(
          SystemSettings::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        ) ?: SystemSettings(id = GLOBAL_SETTINGS_DOC)
      }
      .catch { error ->
        logSnapshotError(
          error,
          OperationType.GET,
          "$COL_SYSTEM_SETTINGS/$GLOBAL_SETTINGS_DOC",
          auth.currentUser?.uid,
        )
        throw error
      }

  suspend fun saveSystemSettings(settings: SystemSettings): Result<Unit> = runCatching {
    val uid = requireUserId()
    val docRef = db.collection(COL_SYSTEM_SETTINGS).document(GLOBAL_SETTINGS_DOC)
    val payload =
      mutableMapOf<String, Any>(
        "maxDailyUploadsPerUser" to settings.maxDailyUploadsPerUser.coerceIn(1L, 500L),
        "maxVideoSizeMb" to settings.maxVideoSizeMb.coerceIn(10L, 2048L),
        "maxVideoDurationSec" to settings.maxVideoDurationSec.coerceIn(15L, 600L),
        "maintenanceMode" to settings.maintenanceMode,
        "updatedBy" to uid,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
    settings.backendRelayUrl?.let { payload["backendRelayUrl"] = it.trim().take(300) }
    settings.metaAppId?.let { payload["metaAppId"] = it.trim().take(128) }
    settings.youtubeClientId?.let { payload["youtubeClientId"] = it.trim().take(200) }
    settings.appDownloadUrl?.let { payload["appDownloadUrl"] = it.trim().take(500) }

    try {
      docRef.set(payload, SetOptions.merge()).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path, auth)
      throw e
    }
  }

  suspend fun logSystemEvent(
    action: String,
    platform: String?,
    severity: String,
    details: String,
  ): Result<Unit> = runCatching {
    val user = auth.currentUser ?: return@runCatching
    val docRef = db.collection(COL_ADMIN_LOGS).document()
    val payload =
      mutableMapOf<String, Any>(
        "actorId" to user.uid,
        "actorEmail" to (user.email ?: "unknown@gyanverse.studio").take(254),
        "action" to action.take(100),
        "severity" to severity.take(20),
        "details" to details.take(1000),
        "createdAt" to FieldValue.serverTimestamp(),
      )
    if (!platform.isNullOrBlank()) {
      payload["platform"] = platform.take(40)
    }
    try {
      docRef.set(payload).await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path, auth)
      throw e
    }
  }

  // --- ADMIN QUERIES (Only invoke when authenticated user is verified admin) ---

  fun observeAllUsersForAdmin(): Flow<List<UserProfile>> =
    db.collection(COL_USERS)
      .snapshots()
      .map { snap ->
        snap.toObjects(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, COL_USERS, auth.currentUser?.uid)
        throw error
      }

  fun observeAllSocialAccountsForAdmin(): Flow<List<SocialAccount>> =
    db.collection(COL_SOCIAL_ACCOUNTS)
      .snapshots()
      .map { snap ->
        snap.toObjects(
          SocialAccount::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, COL_SOCIAL_ACCOUNTS, auth.currentUser?.uid)
        throw error
      }

  fun observeAllVideosForAdmin(): Flow<List<VideoAsset>> =
    db.collection(COL_VIDEOS)
      .snapshots()
      .map { snap ->
        snap.toObjects(VideoAsset::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, COL_VIDEOS, auth.currentUser?.uid)
        throw error
      }

  fun observeAllJobsForAdmin(): Flow<List<PublishingJob>> =
    db.collection(COL_PUBLISHING_JOBS)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(PublishingJob::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, COL_PUBLISHING_JOBS, auth.currentUser?.uid)
        throw error
      }

  fun observeAdminLogs(): Flow<List<AdminLog>> =
    db.collection(COL_ADMIN_LOGS)
      .snapshots()
      .map { snap ->
        snap
          .toObjects(AdminLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          .sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .catch { error ->
        logSnapshotError(error, OperationType.LIST, COL_ADMIN_LOGS, auth.currentUser?.uid)
        throw error
      }

  suspend fun adminSetUserBlocked(targetUserId: String, blocked: Boolean): Result<Unit> =
    runCatching {
      val docRef = db.collection(COL_USERS).document(targetUserId)
      try {
        docRef
          .update(
            mapOf("isBlocked" to blocked, "updatedAt" to FieldValue.serverTimestamp())
          )
          .await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, docRef.path, auth)
        throw e
      }
    }

  suspend fun adminDeleteUser(targetUserId: String): Result<Unit> = runCatching {
    val docRef = db.collection(COL_USERS).document(targetUserId)
    try {
      docRef.delete().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path, auth)
      throw e
    }
  }

  private fun logSnapshotError(
    error: Throwable,
    op: OperationType,
    path: String,
    expectedUid: String?,
  ) {
    val firestoreError =
      generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val ex = firestoreError ?: (error as? Exception)
    if (ex != null && (expectedUid == null || auth.currentUser?.uid == expectedUid)) {
      handleFirestoreError(ex, op, path, auth)
    }
  }

  companion object {
    const val COL_USERS = "users"
    const val COL_SOCIAL_ACCOUNTS = "social_accounts"
    const val COL_VIDEOS = "videos"
    const val COL_PUBLISHING_JOBS = "publishing_jobs"
    const val COL_SCHEDULED_POSTS = "scheduled_posts"
    const val COL_ADMIN_LOGS = "admin_logs"
    const val COL_SYSTEM_SETTINGS = "system_settings"
    const val GLOBAL_SETTINGS_DOC = "global"
    const val SUPER_ADMIN_EMAIL = "maansinghyadav095@gmail.com"
  }
}
