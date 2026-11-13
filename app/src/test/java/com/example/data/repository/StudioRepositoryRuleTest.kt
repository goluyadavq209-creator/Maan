package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.PublishingJob
import com.example.data.model.ScheduledPost
import com.example.data.model.SocialAccount
import com.example.data.model.VideoAsset
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StudioRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun ensureUserProfile_authenticatedUser_createsAndReadsOwnProfile() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = StudioRepository(firestore)

    val createResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.ensureUserProfile(
          userId = uid,
          email = ALICE_EMAIL,
          displayName = "Alice Creator",
          photoUrl = null,
        )
      }
    assertTrue("Profile creation failed: ${createResult.exceptionOrNull()}", createResult.isSuccess)
    val profile = withTimeout(FLOW_TIMEOUT_MS) { repo.observeUserProfile(uid).first { it != null } }
    assertEquals("creator", profile?.role)
    assertEquals(ALICE_EMAIL, profile?.email)
  }

  @Test
  fun createVideoAndPublishingJob_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repo = StudioRepository(firestore)
    val videoId = "vid_${UUID.randomUUID().toString().replace("-", "")}"
    val jobId = "job_${UUID.randomUUID().toString().replace("-", "")}"

    val vidResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createVideo(
          VideoAsset(
            id = videoId,
            userId = uid,
            title = "Gyanverse Demo Reel",
            description = "Vertical tech short",
            captionInstagram = "IG caption #gyanverse",
            captionFacebook = "FB caption",
            captionYoutube = "YT #Shorts",
            hashtags = listOf("#gyanverse", "#reels"),
            durationSeconds = 30L,
            fileSizeBytes = 4_200_000L,
            aspectRatio = "9:16",
            status = "ready",
          )
        )
      }
    assertTrue("createVideo failed: ${vidResult.exceptionOrNull()}", vidResult.isSuccess)

    val videos =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserVideos(uid).first { list -> list.any { it.id == videoId } }
      }
    assertTrue(videos.any { it.id == videoId })

    val jobResult =
      withTimeout(DEFAULT_TIMEOUT_MS) {
        repo.createPublishingJob(
          PublishingJob(
            id = jobId,
            userId = uid,
            videoId = videoId,
            videoTitle = "Gyanverse Demo Reel",
            platform = "instagram",
            status = "api_connection_required",
            progressPercent = 0L,
            errorMessage = "API CONNECTION REQUIRED",
            retryCount = 0L,
          )
        )
      }
    assertTrue("createPublishingJob failed: ${jobResult.exceptionOrNull()}", jobResult.isSuccess)

    val jobs =
      withTimeout(FLOW_TIMEOUT_MS) {
        repo.observeUserPublishingJobs(uid).first { list -> list.any { it.id == jobId } }
      }
    assertEquals("api_connection_required", jobs.first { it.id == jobId }.status)
  }

  @Test
  fun socialAccountsAndScheduledPosts_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = StudioRepository(firestore)
    val videoId = "vid_${UUID.randomUUID().toString().replace("-", "")}"
    val postId = "sched_${UUID.randomUUID().toString().replace("-", "")}"

    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.upsertSocialAccount(
        SocialAccount(
          userId = aliceUid,
          platform = "youtube",
          accountName = "Alice Channel",
          accountHandle = "@alice",
          status = "api_required",
          oauthMode = "unconfigured",
        )
      ).getOrThrow()

      aliceRepo.createVideo(
        VideoAsset(
          id = videoId,
          userId = aliceUid,
          title = "Alice Private Draft",
          durationSeconds = 20L,
          fileSizeBytes = 1_000_000L,
          status = "ready",
        )
      ).getOrThrow()

      aliceRepo.createScheduledPost(
        ScheduledPost(
          id = postId,
          userId = aliceUid,
          videoId = videoId,
          videoTitle = "Alice Private Draft",
          platforms = listOf("youtube"),
          scheduledForMillis = System.currentTimeMillis() + 3_600_000L,
          status = "scheduled",
        )
      ).getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = StudioRepository(firestore)
    val bobGetResult = withTimeout(DEFAULT_TIMEOUT_MS) { bobRepo.getVideoById(videoId) }
    assertTrue(bobGetResult.isFailure)
    val ex = bobGetResult.exceptionOrNull()?.unwrapFirestoreException()
    assertNotNull(ex)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
  }

  @Test
  fun observeUserVideos_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repo = StudioRepository(firestore)
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeUserVideos("some_uid").first() }
      fail("Expected PERMISSION_DENIED for unauthenticated user")
    } catch (e: Exception) {
      val firestoreEx = e.unwrapFirestoreException()
      assertNotNull("Expected FirebaseFirestoreException, got $e", firestoreEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice.rule@test.com"
    const val BOB_EMAIL = "bob.rule@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
