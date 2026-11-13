package com.example

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.auth.GoogleAuthManager
import com.example.auth.configureAppCheck
import com.example.data.remote.AiStudioAssistant
import com.example.data.remote.ApiConfigStore
import com.example.data.remote.PublishingEngine
import com.example.data.repository.StudioRepository
import com.example.ui.components.GyanverseBackground
import com.example.ui.screens.AdminPanelScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ConnectionsScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SchedulerAndHistoryScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.UploadStudioScreen
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.GyanverseStudioTheme
import com.example.ui.theme.MidnightNavySurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusError
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.AdminViewModel
import com.example.ui.viewmodel.StudioViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable

@Serializable data object DashboardRoute

@Serializable data object UploadRoute

@Serializable data object ConnectionsRoute

@Serializable data class SchedulerRoute(val initialTab: Int = 0)

@Serializable data object SettingsRoute

@Serializable data object AdminRoute

private data class TopDestination(
  val route: Any,
  val label: String,
  val icon: ImageVector,
  val matches: (androidx.navigation.NavDestination?) -> Boolean,
)

class MainActivity : ComponentActivity() {

  private val pendingOAuthUri = MutableStateFlow<Uri?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    configureAppCheck(this, intent)
    extractOAuthDeepLink(intent)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    )

    setContent {
      GyanverseStudioTheme {
        val currentUser by
          remember { Firebase.auth.authStateFlow() }
            .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val credentialManager = remember(context) { CredentialManager.create(context) }

        LaunchedEffect(Unit) {
          if (Firebase.auth.currentUser == null) {
            GoogleAuthManager.attemptAutoSignIn(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {},
              onUnauthenticated = {},
              scope = scope,
            )
          }
        }

        val oauthUri by pendingOAuthUri.collectAsStateWithLifecycle()

        if (currentUser == null) {
          AuthScreen(onAuthSuccess = {})
        } else {
          val activeUser = currentUser!!
          key(activeUser.uid) {
            GyanverseAuthenticatedApp(
              user = activeUser,
              pendingOAuthUri = oauthUri,
              onConsumeOAuthUri = { pendingOAuthUri.value = null },
              onSignOut = {
                GoogleAuthManager.signOut(credentialManager = credentialManager, scope = scope)
              },
            )
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    extractOAuthDeepLink(intent)
  }

  private fun extractOAuthDeepLink(intent: Intent?) {
    val data = intent?.data ?: return
    if (data.scheme == "gyanverse" && data.host == "oauth") {
      pendingOAuthUri.value = data
    }
  }
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth -> trySend(auth.currentUser) }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
private fun GyanverseAuthenticatedApp(
  user: FirebaseUser,
  pendingOAuthUri: Uri?,
  onConsumeOAuthUri: () -> Unit,
  onSignOut: () -> Unit,
) {
  val context = LocalContext.current
  val appContext = context.applicationContext

  val studioViewModel: StudioViewModel =
    viewModel(
      key = "studio_${user.uid}",
      factory =
        viewModelFactory {
          initializer {
            val repo = StudioRepository(appContext)
            val apiConfigStore = ApiConfigStore(appContext)
            val aiAssistant = AiStudioAssistant()
            val publishingEngine = PublishingEngine(appContext, repo, apiConfigStore)
            StudioViewModel(
              appContext = appContext,
              repository = repo,
              apiConfigStore = apiConfigStore,
              aiAssistant = aiAssistant,
              publishingEngine = publishingEngine,
              currentUserId = user.uid,
              userEmail = user.email ?: "",
              userDisplayName = user.displayName ?: user.email?.substringBefore("@") ?: "Creator",
              userPhotoUrl = user.photoUrl?.toString(),
            )
          }
        },
    )

  val uiState by studioViewModel.uiState.collectAsStateWithLifecycle()
  val editorState by studioViewModel.editorState.collectAsStateWithLifecycle()
  val aiState by studioViewModel.aiState.collectAsStateWithLifecycle()

  LaunchedEffect(pendingOAuthUri) {
    val uri = pendingOAuthUri ?: return@LaunchedEffect
    studioViewModel.handleOAuthCallback(uri)
    onConsumeOAuthUri()
  }

  val isAdmin =
    (user.isEmailVerified && user.email.equals(StudioRepository.SUPER_ADMIN_EMAIL, ignoreCase = true)) ||
      uiState.userProfile?.role == "admin"

  val navController = rememberNavController()
  val backStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = backStackEntry?.destination
  val isOnAdminScreen = currentDestination?.hasRoute<AdminRoute>() == true

  val snackbarHostState = remember { SnackbarHostState() }
  LaunchedEffect(uiState.bannerMessage) {
    val msg = uiState.bannerMessage
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(
        message = msg,
        duration = if (uiState.isBannerError) SnackbarDuration.Long else SnackbarDuration.Short,
      )
      studioViewModel.dismissBanner()
    }
  }

  val topDestinations =
    remember {
      listOf(
        TopDestination(
          route = DashboardRoute,
          label = "Home",
          icon = Icons.Default.Dashboard,
          matches = { it?.hasRoute<DashboardRoute>() == true },
        ),
        TopDestination(
          route = UploadRoute,
          label = "Upload",
          icon = Icons.Default.CloudUpload,
          matches = { it?.hasRoute<UploadRoute>() == true },
        ),
        TopDestination(
          route = ConnectionsRoute,
          label = "APIs",
          icon = Icons.Default.Link,
          matches = { it?.hasRoute<ConnectionsRoute>() == true },
        ),
        TopDestination(
          route = SchedulerRoute(0),
          label = "Schedule",
          icon = Icons.Default.Schedule,
          matches = { it?.hasRoute<SchedulerRoute>() == true },
        ),
        TopDestination(
          route = SettingsRoute,
          label = "Settings",
          icon = Icons.Default.Settings,
          matches = { it?.hasRoute<SettingsRoute>() == true },
        ),
      )
    }

  val navigateTopLevel: (Any) -> Unit = { route ->
    navController.navigate(route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
    }
  }

  val appContent: @Composable () -> Unit = {
    GyanverseBackground {
      Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
          SnackbarHost(hostState = snackbarHostState) { data ->
            Snackbar(
              snackbarData = data,
              containerColor =
                if (uiState.isBannerError) StatusError.copy(alpha = 0.92f)
                else MidnightNavySurface,
              contentColor = Color.White,
              actionColor = NeonCyan,
            )
          }
        },
      ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
          NavHost(navController = navController, startDestination = DashboardRoute) {
            composable<DashboardRoute> {
              DashboardScreen(
                uiState = uiState,
                isAdmin = isAdmin,
                onNavigateToUpload = { navigateTopLevel(UploadRoute) },
                onNavigateToConnections = { navigateTopLevel(ConnectionsRoute) },
                onNavigateToScheduler = { navigateTopLevel(SchedulerRoute(0)) },
                onNavigateToAdmin = {
                  if (isAdmin) {
                    navController.navigate(AdminRoute) { launchSingleTop = true }
                  }
                },
                onRetryJob = { job -> studioViewModel.retryJob(job) },
              )
            }
            composable<UploadRoute> {
              UploadStudioScreen(
                uiState = uiState,
                editor = editorState,
                aiState = aiState,
                onSelectVideoUri = { uri -> studioViewModel.selectLocalVideo(uri) },
                onTitleChange = { studioViewModel.updateTitle(it) },
                onDescriptionChange = { studioViewModel.updateDescription(it) },
                onCaptionChange = { platform, text ->
                  studioViewModel.updateCaption(platform, text)
                },
                onAddHashtag = { studioViewModel.addHashtag(it) },
                onRemoveHashtag = { studioViewModel.removeHashtag(it) },
                onTogglePlatform = { studioViewModel.togglePlatformSelection(it) },
                onGenerateAi = { studioViewModel.generateAiHashtagsAndCaptions() },
                onSaveDraft = { studioViewModel.saveVideoToLibrary() },
                onPublishEverywhere = { studioViewModel.publishEverywhere() },
              )
            }
            composable<ConnectionsRoute> {
              ConnectionsScreen(
                uiState = uiState,
                onConnectPlatform = { platform ->
                  studioViewModel.connectSocialPlatform(context, platform)
                },
                onDisconnectPlatform = { acc -> studioViewModel.disconnectPlatform(acc) },
                onSelectFacebookPage = { page -> studioViewModel.selectFacebookPage(page) },
                onSaveApiEndpoints = { relay, metaAppId, ytClientId ->
                  studioViewModel.saveApiEndpoints(relay, metaAppId, ytClientId)
                },
              )
            }
            composable<SchedulerRoute> { entry ->
              val routeArgs = entry.toRoute<SchedulerRoute>()
              SchedulerAndHistoryScreen(
                uiState = uiState,
                initialTab = routeArgs.initialTab,
                onSchedulePost = { video, platforms, millis ->
                  studioViewModel.schedulePost(video, platforms, millis)
                },
                onReschedulePost = { post, newMillis, platforms ->
                  studioViewModel.reschedulePost(post, newMillis, platforms)
                },
                onCancelScheduledPost = { post -> studioViewModel.cancelScheduledPost(post) },
                onRetryJob = { job -> studioViewModel.retryJob(job) },
              )
            }
            composable<SettingsRoute> {
              SettingsScreen(
                uiState = uiState,
                isAdmin = isAdmin,
                onSaveProfile = { name, bio, notifyPub, notifyFail ->
                  studioViewModel.updateProfile(name, bio, notifyPub, notifyFail)
                },
                onSaveAppDownloadUrl = { url -> studioViewModel.saveAppDownloadUrl(url) },
                onDisconnectAccount = { acc -> studioViewModel.disconnectPlatform(acc) },
                onOpenAdminPanel = {
                  if (isAdmin) {
                    navController.navigate(AdminRoute) { launchSingleTop = true }
                  }
                },
                onSignOut = onSignOut,
                onDeleteAccount = {
                  studioViewModel.deleteAccountData(onDeleted = onSignOut)
                },
              )
            }
            composable<AdminRoute> {
              if (isAdmin) {
                val adminViewModel: AdminViewModel =
                  viewModel(
                    key = "admin_${user.uid}",
                    factory =
                      viewModelFactory {
                        initializer { AdminViewModel(StudioRepository(appContext)) }
                      },
                  )
                val adminState by adminViewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(adminState.message) {
                  val msg = adminState.message
                  if (!msg.isNullOrBlank()) {
                    snackbarHostState.showSnackbar(msg)
                    adminViewModel.clearMessage()
                  }
                }
                AdminPanelScreen(
                  adminState = adminState,
                  onBack = { navController.popBackStack() },
                  onToggleBlockUser = { target -> adminViewModel.toggleBlockUser(target) },
                  onDeleteUser = { target -> adminViewModel.deleteUser(target) },
                  onSaveSystemSettings = { daily, maxMb, maxSec, maint, relay, meta, yt, apkUrl ->
                    adminViewModel.saveSystemSettings(
                      maxDailyUploads = daily,
                      maxVideoSizeMb = maxMb,
                      maxVideoDurationSec = maxSec,
                      maintenanceMode = maint,
                      backendRelayUrl = relay,
                      metaAppId = meta,
                      youtubeClientId = yt,
                      appDownloadUrl = apkUrl,
                    )
                  },
                )
              } else {
                LaunchedEffect(Unit) { navController.popBackStack() }
              }
            }
          }
        }
      }
    }
  }

  if (isOnAdminScreen) {
    appContent()
  } else {
    NavigationSuiteScaffold(
      navigationSuiteColors =
        NavigationSuiteDefaults.colors(
          navigationBarContainerColor = MidnightNavySurface,
          navigationBarContentColor = TextSecondary,
          navigationRailContainerColor = MidnightNavySurface,
          navigationRailContentColor = TextSecondary,
          navigationDrawerContainerColor = MidnightNavySurface,
          navigationDrawerContentColor = TextSecondary,
        ),
      navigationSuiteItems = {
        topDestinations.forEach { dest ->
          val selected = dest.matches(currentDestination)
          item(
            selected = selected,
            onClick = { navigateTopLevel(dest.route) },
            icon = {
              Icon(
                imageVector = dest.icon,
                contentDescription = dest.label,
                tint = if (selected) NeonCyan else TextSecondary,
              )
            },
            label = {
              Text(
                text = dest.label,
                color = if (selected) NeonCyan else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            },
          )
        }
      },
      content = appContent,
    )
  }
}
