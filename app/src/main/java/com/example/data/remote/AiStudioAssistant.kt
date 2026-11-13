package com.example.data.remote

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class GeneratedSocialMetadata(
  val hashtags: List<String>,
  val instagramCaption: String,
  val facebookCaption: String,
  val youtubeTitle: String,
  val youtubeDescription: String,
)

class AiStudioAssistant {

  private val metadataSchema =
    Schema.obj(
      properties =
        mapOf(
          "hashtags" to
            Schema.array(
              items = Schema.string(description = "Hashtag starting with #"),
              description = "8 to 12 high-reach vertical short-video hashtags",
            ),
          "instagramCaption" to
            Schema.string(
              description = "Engaging Instagram Reels caption with hook, CTA, and hashtags"
            ),
          "facebookCaption" to
            Schema.string(
              description = "Shareable Facebook Reels caption optimized for comments and shares"
            ),
          "youtubeTitle" to
            Schema.string(description = "Punchy YouTube Shorts title under 95 characters"),
          "youtubeDescription" to
            Schema.string(
              description = "YouTube Shorts description including #Shorts and relevant keywords"
            ),
        )
    )

  private val model by lazy {
    Firebase.ai.generativeModel(
      modelName = "gemini-3.8-flash",
      generationConfig =
        generationConfig {
          responseMimeType = "application/json"
          responseSchema = metadataSchema
        },
    )
  }

  suspend fun generateHashtagsAndCaptions(
    title: String,
    description: String,
  ): Result<GeneratedSocialMetadata> {
    val prompt =
      """
      You are a short-form social media strategist for Instagram Reels, Facebook Reels, and YouTube Shorts.
      Generate platform-optimized metadata for a creator's vertical video:
      Video Title: ${title.ifBlank { "Untitled Creator Short" }}
      Video Context/Description: ${description.ifBlank { "Short vertical creator video" }}
      Keep hashtags concise, relevant, and starting with '#'. Keep YouTube title <= 95 chars.
      """
        .trimIndent()

    return try {
      val response = withTimeout(60_000L) { model.generateContent(prompt) }
      val jsonText =
        response.text ?: throw IllegalStateException("AI model returned an empty response.")
      val json = JSONObject(jsonText)
      val tagsArray = json.getJSONArray("hashtags")
      val tags =
        (0 until tagsArray.length())
          .map { tagsArray.getString(it).trim() }
          .filter { it.isNotBlank() }
          .map { if (it.startsWith("#")) it else "#$it" }
          .distinct()
          .take(15)

      Result.success(
        GeneratedSocialMetadata(
          hashtags = tags,
          instagramCaption = json.getString("instagramCaption"),
          facebookCaption = json.getString("facebookCaption"),
          youtubeTitle = json.getString("youtubeTitle"),
          youtubeDescription = json.getString("youtubeDescription"),
        )
      )
    } catch (e: TimeoutCancellationException) {
      Log.e(TAG, "AI caption generation timed out", e)
      Result.failure(IllegalStateException("AI generation timed out after 60s. Please retry.", e))
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "AI caption generation failed", e)
      Result.failure(e)
    }
  }

  private companion object {
    const val TAG = "AiStudioAssistant"
  }
}
