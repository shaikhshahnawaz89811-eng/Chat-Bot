package com.brain.offlineai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.core.content.ContextCompat
import androidx.navigation.navArgument
import com.brain.offlineai.data.settings.AppSettingsRepository
import com.brain.offlineai.data.settings.AppSettingsState
import com.brain.offlineai.navigation.Screen
import com.brain.offlineai.server.LocalApiForegroundService
import com.brain.offlineai.ui.components.AppDrawerContent
import com.brain.offlineai.ui.components.BrainBottomNavBar
import com.brain.offlineai.ui.screens.about.AboutScreen
import com.brain.offlineai.ui.screens.analytics.AnalyticsScreen
import com.brain.offlineai.ui.screens.apikeys.ApiKeysListScreen
import com.brain.offlineai.ui.screens.apikeys.CopyKeyScreen
import com.brain.offlineai.ui.screens.apikeys.CreateApiKeyScreen
import com.brain.offlineai.ui.screens.apikeys.KeyDetailsScreen
import com.brain.offlineai.ui.screens.apikeys.KeyOptionsScreen
import com.brain.offlineai.ui.screens.chat.ChatScreen
import com.brain.offlineai.ui.screens.chat.CurrentChatSessionStore
import com.brain.offlineai.ui.screens.history.HistoryScreen
import com.brain.offlineai.ui.screens.localapi.LocalApiScreen
import com.brain.offlineai.ui.screens.modelsettings.ModelSettingsScreen
import com.brain.offlineai.ui.screens.models.ModelsScreen
import com.brain.offlineai.ui.screens.settings.GeneralSettingsScreen
import com.brain.offlineai.ui.screens.storage.StorageScreen
import com.brain.offlineai.ui.theme.BrainOfflineAITheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Phase 6: seed the real theme + animations state from the
        // persisted AppSettingsRepository before the first composition, so
        // the app opens in whatever the user last chose instead of always
        // flashing the dark default first.
        AppSettingsState.init(applicationContext)

        // Phase 6: General Settings screen's real "Auto-Start on Launch"
        // toggle - if enabled, start the same real foreground service
        // (LocalApiForegroundService) the Local API screen's own Start
        // button uses, once per process launch.
        if (AppSettingsRepository(applicationContext).isAutoStartLocalApi()) {
            ContextCompat.startForegroundService(this, LocalApiForegroundService.startIntent(this))
        }

        setContent {
            BrainOfflineAITheme {
                BrainApp()
            }
        }
    }
}

@Composable
fun BrainApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: Screen.Chat.route

    // Bug fix (user request) - "app kill na ho": the single most effective
    // real, programmatic protection Android actually offers against the OS
    // killing this process in the background is exempting it from Doze/
    // App Standby battery restrictions (on top of ChatTaskForegroundService
    // already raising this process's priority while a reply is actively
    // generating - see that class's own doc). This shows the REAL system
    // dialog once per install (skipped instantly if already granted, e.g.
    // on every later launch) - it can't be silently auto-granted, Android
    // requires the user to explicitly approve it.
    LaunchedEffect(Unit) {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val alreadyIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
        if (!alreadyIgnoring) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                )
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawerContent(
                currentRoute = currentRoute,
                onNavigate = { screen ->
                    navController.navigateSingleTop(screen.route)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Scaffold(
            modifier = Modifier.imePadding(),
            bottomBar = {
                if (currentRoute in Screen.bottomNavItems.map { it.route }) {
                    BrainBottomNavBar(currentRoute = currentRoute) { screen ->
                        navController.navigateSingleTop(screen.route)
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Chat.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Chat.route) {
                    ChatScreen(onMenuClick = { scope.launch { drawerState.open() } })
                }
                composable(Screen.History.route) {
                    HistoryScreen(onOpenSession = { sessionId ->
                        // Bug fix (user request) - the bottom-nav Chat tab
                        // (Screen.Chat) and Screen.ChatSession are two
                        // genuinely separate NavBackStackEntries, each with
                        // its own ChatViewModel instance. Screen.ChatSession
                        // only ever loads what's already persisted in the
                        // DB, so opening the CURRENTLY-active session that
                        // way (e.g. while it's still generating a reply)
                        // showed a disconnected, stale copy that never saw
                        // the real live stream - looked like "the work isn't
                        // shown happening". If the tapped session really is
                        // the live one, route back into the same Chat tab
                        // instance instead of spinning up a second, orphaned
                        // ChatViewModel for it.
                        val activeSessionId = CurrentChatSessionStore.get(context)
                        if (sessionId == activeSessionId) {
                            navController.navigateSingleTop(Screen.Chat.route)
                        } else {
                            navController.navigate(Screen.ChatSession.routeFor(sessionId))
                        }
                    })
                }
                composable(
                    route = Screen.ChatSession.route,
                    arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                    ChatScreen(onMenuClick = { scope.launch { drawerState.open() } }, openSessionId = sessionId)
                }
                composable(Screen.Models.route) {
                    ModelsScreen(onOpenSettings = { navController.navigate(Screen.ModelSettings.route) })
                }
                composable(Screen.ModelSettings.route) {
                    ModelSettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Settings.route) {
                    GeneralSettingsScreen(
                        onOpenStorage = { navController.navigate(Screen.Storage.route) },
                        onOpenAbout = { navController.navigate(Screen.About.route) }
                    )
                }
                composable(Screen.Storage.route) {
                    StorageScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.ApiKeys.route) {
                    ApiKeysListScreen(
                        onCreateClick = { navController.navigate(Screen.ApiKeyCreate.route) },
                        onViewKey = { keyId -> navController.navigate(Screen.ApiKeyDetails.routeFor(keyId)) },
                        onOptionsKey = { keyId -> navController.navigate(Screen.ApiKeyOptions.routeFor(keyId)) }
                    )
                }
                composable(Screen.ApiKeyCreate.route) {
                    CreateApiKeyScreen(
                        onBack = { navController.popBackStack() },
                        onKeyCreated = { keyId ->
                            navController.navigate(Screen.ApiKeyDetails.routeFor(keyId)) {
                                popUpTo(Screen.ApiKeys.route)
                            }
                        }
                    )
                }
                composable(
                    route = Screen.ApiKeyDetails.route,
                    arguments = listOf(navArgument("keyId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val keyId = backStackEntry.arguments?.getString("keyId") ?: return@composable
                    KeyDetailsScreen(
                        keyId = keyId,
                        onBack = { navController.popBackStack(Screen.ApiKeys.route, inclusive = false) },
                        onCopy = { copiedId -> navController.navigate(Screen.ApiKeyCopied.routeFor(copiedId)) }
                    )
                }
                composable(
                    route = Screen.ApiKeyOptions.route,
                    arguments = listOf(navArgument("keyId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val keyId = backStackEntry.arguments?.getString("keyId") ?: return@composable
                    KeyOptionsScreen(
                        keyId = keyId,
                        onBack = { navController.popBackStack() },
                        onViewDetails = { id -> navController.navigate(Screen.ApiKeyDetails.routeFor(id)) },
                        onCopy = { copiedId -> navController.navigate(Screen.ApiKeyCopied.routeFor(copiedId)) },
                        onDeleted = { navController.popBackStack(Screen.ApiKeys.route, inclusive = false) }
                    )
                }
                composable(
                    route = Screen.ApiKeyCopied.route,
                    arguments = listOf(navArgument("keyId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val keyId = backStackEntry.arguments?.getString("keyId") ?: return@composable
                    CopyKeyScreen(
                        keyId = keyId,
                        onDone = { navController.popBackStack(Screen.ApiKeys.route, inclusive = false) }
                    )
                }
                composable(Screen.LocalApi.route) {
                    LocalApiScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Analytics.route) { AnalyticsScreen() }
                composable(Screen.About.route) { AboutScreen() }
            }
        }
    }
}

private fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
