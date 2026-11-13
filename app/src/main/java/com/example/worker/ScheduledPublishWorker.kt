package com.example.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.R
import com.example.data.model.SocialPlatform
import com.example.data.remote.ApiConfigStore
import com.example.data.remote.PublishingEngine
import com.example.data.repository.StudioRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

class ScheduledPublishWorker(
  appContext: Context,
  params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

  override suspend fun doWork(): Result {
    val scheduledPostId = inputData.getString(KEY_SCHEDULED_POST_ID) ?: return Result.failure()
    val user = Firebase.auth.currentUser ?: return Result.retry()

    val repository = StudioRepository(applicationContext)
    val apiStore = ApiConfigStore(applicationContext)
    val engine = PublishingEngine(applicationContext, repository, apiStore)

    val post = repository.getScheduledPostById(scheduledPostId).getOrNull() ?: return Result.failure()
    if (post.status == "cancelled" || post.status == "completed") {
      return Result.success()
    }

    repository.updateScheduledPost(scheduledPostId, status = "processing")
    val video = repository.getVideoById(post.videoId).getOrNull()
    if (video == null) {
      repository.updateScheduledPost(
        postId = scheduledPostId,
        status = "failed",
        errorMessage = "Referenced video asset no longer exists.",
      )
      return Result.failure()
    }

    val accounts = repository.observeSocialAccounts(user.uid).first()
    var anySuccess = false
    val errors = mutableListOf<String>()

    for (platformId in post.platforms) {
      val platform = SocialPlatform.fromId(platformId)
      val caption =
        when (platform) {
          SocialPlatform.INSTAGRAM -> post.captionInstagram ?: video.captionInstagram ?: video.title
          SocialPlatform.FACEBOOK -> post.captionFacebook ?: video.captionFacebook ?: video.title
          SocialPlatform.YOUTUBE -> post.captionYoutube ?: video.captionYoutube ?: video.title
        }
      val outcome = engine.publishToPlatform(video, platform, caption, accounts)
      if (outcome.status == "success") {
        anySuccess = true
      } else {
        errors.add("${platform.shortLabel}: ${outcome.errorMessage ?: outcome.status}")
      }
    }

    if (anySuccess && errors.isEmpty()) {
      repository.updateScheduledPost(scheduledPostId, status = "completed")
      maybeShowNotification(
        "Scheduled Post Published",
        "${post.videoTitle} published to ${post.platforms.joinToString()}",
      )
    } else {
      val msg = errors.joinToString(" | ").ifBlank { "API CONNECTION REQUIRED" }
      repository.updateScheduledPost(scheduledPostId, status = "failed", errorMessage = msg)
      maybeShowNotification("Scheduled Post Attention Needed", msg)
    }

    return Result.success()
  }

  private fun maybeShowNotification(title: String, body: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val granted =
        ContextCompat.checkSelfPermission(
          applicationContext,
          android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
      if (!granted) return
    }
    val manager =
      applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val channel =
        NotificationChannel(
          CHANNEL_ID,
          "Publishing Updates",
          NotificationManager.IMPORTANCE_DEFAULT,
        )
      manager.createNotificationChannel(channel)
    }
    val notification =
      NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(body)
        .setAutoCancel(true)
        .build()
    manager.notify(title.hashCode(), notification)
  }

  companion object {
    const val KEY_SCHEDULED_POST_ID = "scheduled_post_id"
    private const val CHANNEL_ID = "gyanverse_publishing_channel"

    fun enqueue(context: Context, scheduledPostId: String, triggerAtMillis: Long) {
      val delayMs = (triggerAtMillis - System.currentTimeMillis()).coerceAtLeast(1_000L)
      val workRequest =
        OneTimeWorkRequestBuilder<ScheduledPublishWorker>()
          .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
          .setInputData(Data.Builder().putString(KEY_SCHEDULED_POST_ID, scheduledPostId).build())
          .addTag("schedule_$scheduledPostId")
          .build()
      WorkManager.getInstance(context)
        .enqueueUniqueWork("schedule_$scheduledPostId", ExistingWorkPolicy.REPLACE, workRequest)
    }

    fun cancel(context: Context, scheduledPostId: String) {
      WorkManager.getInstance(context).cancelUniqueWork("schedule_$scheduledPostId")
    }
  }
}
