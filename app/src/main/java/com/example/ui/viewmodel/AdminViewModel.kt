package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AdminLog
import com.example.data.model.PublishingJob
import com.example.data.model.SocialAccount
import com.example.data.model.SystemSettings
import com.example.data.model.UserProfile
import com.example.data.model.VideoAsset
import com.example.data.repository.StudioRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AdminUiState(
  val users: List<UserProfile> = emptyList(),
  val accounts: List<SocialAccount> = emptyList(),
  val videos: List<VideoAsset> = emptyList(),
  val jobs: List<PublishingJob> = emptyList(),
  val logs: List<AdminLog> = emptyList(),
  val systemSettings: SystemSettings = SystemSettings(),
  val message: String? = null,
  val isError: Boolean = false,
) {
  val totalStorageMb: Double
    get() = videos.sumOf { it.fileSizeBytes } / (1024.0 * 1024.0)

  val activeUsersCount: Int
    get() = users.count { !it.isBlocked }

  val failedJobsCount: Int
    get() = jobs.count { it.status == "failed" || it.status == "api_connection_required" }
}

class AdminViewModel(private val repository: StudioRepository) : ViewModel() {

  private val _message = MutableStateFlow<Pair<String?, Boolean>>(null to false)

  val uiState: StateFlow<AdminUiState> =
    combine(
      repository.observeAllUsersForAdmin().catch { emit(emptyList()) },
      repository.observeAllSocialAccountsForAdmin().catch { emit(emptyList()) },
      repository.observeAllVideosForAdmin().catch { emit(emptyList()) },
      repository.observeAllJobsForAdmin().catch { emit(emptyList()) },
      repository.observeAdminLogs().catch { emit(emptyList()) },
      repository.observeSystemSettings().catch { emit(SystemSettings()) },
      _message,
    ) { values ->
      @Suppress("UNCHECKED_CAST")
      AdminUiState(
        users = values[0] as List<UserProfile>,
        accounts = values[1] as List<SocialAccount>,
        videos = values[2] as List<VideoAsset>,
        jobs = values[3] as List<PublishingJob>,
        logs = values[4] as List<AdminLog>,
        systemSettings = values[5] as SystemSettings,
        message = (values[6] as Pair<String?, Boolean>).first,
        isError = (values[6] as Pair<String?, Boolean>).second,
      )
    }.stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000L),
      initialValue = AdminUiState(),
    )

  fun clearMessage() {
    _message.value = null to false
  }

  fun toggleBlockUser(user: UserProfile) {
    viewModelScope.launch {
      val nextBlocked = !user.isBlocked
      repository
        .adminSetUserBlocked(user.userId, nextBlocked)
        .onSuccess {
          repository.logSystemEvent(
            action = if (nextBlocked) "ADMIN_BLOCK_USER" else "ADMIN_UNBLOCK_USER",
            platform = "system",
            severity = "warning",
            details = "${user.email} (${user.userId}) blocked=$nextBlocked",
          )
          _message.value =
            "${user.displayName} ${if (nextBlocked) "blocked" else "unblocked"}." to false
        }
        .onFailure { e ->
          _message.value = (e.localizedMessage ?: "Admin action failed") to true
        }
    }
  }

  fun deleteUser(user: UserProfile) {
    viewModelScope.launch {
      repository
        .adminDeleteUser(user.userId)
        .onSuccess {
          repository.logSystemEvent(
            action = "ADMIN_DELETE_USER",
            platform = "system",
            severity = "warning",
            details = "Deleted user ${user.email} (${user.userId})",
          )
          _message.value = "Removed user ${user.displayName}." to false
        }
        .onFailure { e ->
          _message.value = (e.localizedMessage ?: "Failed to delete user") to true
        }
    }
  }

  fun saveSystemSettings(
    maxDailyUploads: Long,
    maxVideoSizeMb: Long,
    maxVideoDurationSec: Long,
    maintenanceMode: Boolean,
    backendRelayUrl: String,
    metaAppId: String,
    youtubeClientId: String,
    appDownloadUrl: String,
  ) {
    viewModelScope.launch {
      repository
        .saveSystemSettings(
          SystemSettings(
            maxDailyUploadsPerUser = maxDailyUploads,
            maxVideoSizeMb = maxVideoSizeMb,
            maxVideoDurationSec = maxVideoDurationSec,
            maintenanceMode = maintenanceMode,
            backendRelayUrl = backendRelayUrl,
            metaAppId = metaAppId,
            youtubeClientId = youtubeClientId,
            appDownloadUrl = appDownloadUrl,
          )
        )
        .onSuccess {
          repository.logSystemEvent(
            action = "ADMIN_UPDATE_SYSTEM_SETTINGS",
            platform = "system",
            severity = "info",
            details = "Updated limits: daily=$maxDailyUploads, maxMB=$maxVideoSizeMb, maxSec=$maxVideoDurationSec, maintenance=$maintenanceMode",
          )
          _message.value = "System settings updated." to false
        }
        .onFailure { e ->
          _message.value = (e.localizedMessage ?: "Failed to save settings") to true
        }
    }
  }
}
