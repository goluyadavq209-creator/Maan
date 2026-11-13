package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

@JsonClass(generateAdapter = true)
data class RelayHealthResponse(
  val status: String = "",
  val instagramConfigured: Boolean = false,
  val facebookConfigured: Boolean = false,
  val youtubeConfigured: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class RelayPublishRequest(
  val userId: String,
  val videoId: String,
  val title: String,
  val caption: String,
  val platform: String,
  val videoUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class RelayPublishResponse(
  val success: Boolean = false,
  val externalPostUrl: String? = null,
  val error: String? = null,
  val code: String? = null,
)

@JsonClass(generateAdapter = true)
data class MetaContainerResponse(
  val id: String? = null,
  @Json(name = "status_code") val statusCode: String? = null,
  val status: String? = null,
)

@JsonClass(generateAdapter = true)
data class MetaPagesResponse(
  val data: List<MetaPageItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class MetaPageItem(
  val id: String = "",
  val name: String = "",
  @Json(name = "access_token") val accessToken: String? = null,
)

@JsonClass(generateAdapter = true)
data class MetaFacebookReelStartResponse(
  @Json(name = "video_id") val videoId: String? = null,
  @Json(name = "upload_url") val uploadUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class MetaFacebookReelFinishResponse(
  val success: Boolean = false,
  @Json(name = "post_id") val postId: String? = null,
)

@JsonClass(generateAdapter = true)
data class YouTubeChannelsResponse(
  val items: List<YouTubeChannelItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class YouTubeChannelItem(
  val id: String = "",
  val snippet: YouTubeChannelSnippet? = null,
)

@JsonClass(generateAdapter = true)
data class YouTubeChannelSnippet(
  val title: String = "",
  val customUrl: String? = null,
)

@JsonClass(generateAdapter = true)
data class YouTubeVideoMetadata(
  val snippet: YouTubeVideoSnippet,
  val status: YouTubeVideoStatus,
)

@JsonClass(generateAdapter = true)
data class YouTubeVideoSnippet(
  val title: String,
  val description: String,
  val tags: List<String> = emptyList(),
  val categoryId: String = "22",
)

@JsonClass(generateAdapter = true)
data class YouTubeVideoStatus(
  val privacyStatus: String = "public",
  val selfDeclaredMadeForKids: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class YouTubeUploadResponse(
  val id: String? = null,
)

interface BackendRelayApiService {
  @GET("api/health")
  suspend fun checkHealth(): Response<RelayHealthResponse>

  @POST("api/publish")
  suspend fun publishVideo(
    @Header("Authorization") bearerToken: String,
    @Body request: RelayPublishRequest,
  ): Response<RelayPublishResponse>
}

interface MetaGraphApiService {
  @POST("v21.0/{igUserId}/media")
  suspend fun createInstagramReelContainer(
    @Path("igUserId") igUserId: String,
    @Query("media_type") mediaType: String = "REELS",
    @Query("video_url") videoUrl: String,
    @Query("caption") caption: String,
    @Query("access_token") accessToken: String,
  ): Response<MetaContainerResponse>

  @GET("v21.0/{containerId}")
  suspend fun getInstagramContainerStatus(
    @Path("containerId") containerId: String,
    @Query("fields") fields: String = "status_code,status",
    @Query("access_token") accessToken: String,
  ): Response<MetaContainerResponse>

  @POST("v21.0/{igUserId}/media_publish")
  suspend fun publishInstagramMedia(
    @Path("igUserId") igUserId: String,
    @Query("creation_id") creationId: String,
    @Query("access_token") accessToken: String,
  ): Response<MetaContainerResponse>

  @GET("v21.0/me/accounts")
  suspend fun getFacebookPages(
    @Query("access_token") accessToken: String,
  ): Response<MetaPagesResponse>

  @POST("v21.0/{pageId}/video_reels")
  suspend fun startFacebookReelUpload(
    @Path("pageId") pageId: String,
    @Query("upload_phase") uploadPhase: String = "start",
    @Query("access_token") accessToken: String,
  ): Response<MetaFacebookReelStartResponse>

  @POST
  suspend fun uploadFacebookReelBinary(
    @Url uploadUrl: String,
    @Header("Authorization") oauthHeader: String,
    @Header("offset") offset: Long = 0L,
    @Header("file_size") fileSize: Long,
    @Body body: RequestBody,
  ): Response<ResponseBody>

  @POST("v21.0/{pageId}/video_reels")
  suspend fun finishFacebookReelUpload(
    @Path("pageId") pageId: String,
    @Query("upload_phase") uploadPhase: String = "finish",
    @Query("video_id") videoId: String,
    @Query("video_state") videoState: String = "PUBLISHED",
    @Query("description") description: String,
    @Query("access_token") accessToken: String,
  ): Response<MetaFacebookReelFinishResponse>
}

interface YouTubeDataApiService {
  @GET("youtube/v3/channels")
  suspend fun getMyChannels(
    @Header("Authorization") bearerToken: String,
    @Query("part") part: String = "snippet",
    @Query("mine") mine: Boolean = true,
  ): Response<YouTubeChannelsResponse>

  @POST("upload/youtube/v3/videos")
  suspend fun initiateResumableUpload(
    @Header("Authorization") bearerToken: String,
    @Header("X-Upload-Content-Length") contentLength: Long,
    @Header("X-Upload-Content-Type") contentType: String = "video/*",
    @Query("uploadType") uploadType: String = "resumable",
    @Query("part") part: String = "snippet,status",
    @Body metadata: YouTubeVideoMetadata,
  ): Response<ResponseBody>

  @PUT
  suspend fun uploadResumableVideoBinary(
    @Url uploadSessionUrl: String,
    @Header("Content-Type") contentType: String = "video/*",
    @Body body: RequestBody,
  ): Response<YouTubeUploadResponse>
}

object SocialApiClientFactory {
  private val moshi: Moshi by lazy {
    Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
  }

  private val okHttpClient: OkHttpClient by lazy {
    val logger = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
    OkHttpClient.Builder()
      .connectTimeout(30, TimeUnit.SECONDS)
      .readTimeout(120, TimeUnit.SECONDS)
      .writeTimeout(180, TimeUnit.SECONDS)
      .addInterceptor(logger)
      .build()
  }

  fun createMetaService(): MetaGraphApiService =
    Retrofit.Builder()
      .baseUrl("https://graph.facebook.com/")
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(MetaGraphApiService::class.java)

  fun createYouTubeService(): YouTubeDataApiService =
    Retrofit.Builder()
      .baseUrl("https://www.googleapis.com/")
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(YouTubeDataApiService::class.java)

  fun createBackendRelayService(baseUrl: String): BackendRelayApiService {
    val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    return Retrofit.Builder()
      .baseUrl(normalized)
      .client(okHttpClient)
      .addConverterFactory(MoshiConverterFactory.create(moshi))
      .build()
      .create(BackendRelayApiService::class.java)
  }
}
