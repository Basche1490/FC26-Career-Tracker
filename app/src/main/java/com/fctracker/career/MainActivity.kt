package com.fctracker.career

import coil.Coil
import coil.ImageLoader
import coil.decode.SvgDecoder
import okhttp3.OkHttpClient
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.fctracker.career.ui.screens.HomeScreen
import com.fctracker.career.ui.screens.PlayerProfileScreen
import com.fctracker.career.ui.screens.WorldScreen
import com.fctracker.career.ui.screens.RecapScreen
import com.fctracker.career.ui.screens.MilestonesScreen
import com.fctracker.career.ui.screens.WidgetsScreen
import com.fctracker.career.ui.screens.ThemingScreen
import com.fctracker.career.ui.screens.ClubHistoryEditScreen
import com.fctracker.career.ui.components.GlassBottomBar
import com.fctracker.career.ui.components.LocalOpenDrawer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import com.fctracker.career.ui.screens.OnboardingScreen
import com.fctracker.career.ui.screens.SquadScreen
import com.fctracker.career.ui.screens.AcademyScreen
import com.fctracker.career.ui.screens.TransfersScreen
import com.fctracker.career.ui.screens.MatchesScreen
import com.fctracker.career.ui.screens.SeasonSummaryScreen
import com.fctracker.career.ui.screens.CareerSummaryScreen
import com.fctracker.career.ui.screens.AssistantScreen
import com.fctracker.career.ui.screens.SettingsScreen
import com.fctracker.career.ui.screens.ConnectionScreen
import com.fctracker.career.ui.screens.SetupGuideScreen
import com.fctracker.career.ui.screens.AppDrawer
import com.fctracker.career.ui.theme.FCTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.viewModels

class MainActivity : ComponentActivity() {
    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val okHttpClient = OkHttpClient.Builder()
            .addNetworkInterceptor { chain ->
                val original = chain.request()
                val builder = original.newBuilder()
                builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                if (original.url.host.contains("futbin")) {
                    builder.header("Referer", "https://www.futbin.com/")
                }
                chain.proceed(builder.build())
            }
            .build()

        val imageLoader = ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .components {
                add(SvgDecoder.Factory())
            }
            .crossfade(true)
            .allowHardware(false)
            .build()
        Coil.setImageLoader(imageLoader)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotificationIntent(intent)
        setContent {
            MainApp(viewModel = mainViewModel)
        }
    }

    // Live polling runs while the app is on screen (and, if switched on in Settings, in the background
    // through LiveSyncService). Leaving the screen also writes the career to disk immediately.
    override fun onStart() {
        super.onStart()
        mainViewModel.onAppStarted()
    }

    override fun onStop() {
        mainViewModel.onAppStopped()
        super.onStop()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        val target = intent?.getStringExtra("EXTRA_NAVIGATE_TO")
        if (!target.isNullOrBlank()) {
            mainViewModel.navigateToScreen(target)
        }
    }
}

/** Routes that show the floating bar at the bottom, and which of its four places each one lights up. */
private val NO_BAR_ROUTES = setOf("setup", "recap", "club_history_edit", "widgets")

private fun barPlace(route: String?): String? = when {
    route == null -> null
    route.startsWith("player") -> "squad"
    route in setOf("dashboard", "matches", "squad", "transfers") -> route
    route == "milestones" -> "dashboard"
    else -> null
}

@Composable
fun MainApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val pendingRoute by viewModel.pendingRoute.collectAsState()

    FCTheme(primaryHex = state.primaryColorHex, secondaryHex = state.secondaryColorHex, useClubColours = state.useClubColours) {
        // Request Notification Permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) {}
            LaunchedEffect(Unit) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (!state.isOnboarded) {
            OnboardingScreen(viewModel)
            return@FCTheme
        }

        val navController = rememberNavController()
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        val focusManager = LocalFocusManager.current
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route

        LaunchedEffect(drawerState.isOpen) {
            if (drawerState.isOpen) focusManager.clearFocus()
        }

        val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
        // Top-level places replace each other (Back goes to Home); detail pages stack on top.
        val goTop: (String) -> Unit = { route ->
            try {
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            } catch (e: Exception) {
            }
        }
        val goDetail: (String) -> Unit = { route -> try { navController.navigate(route) { launchSingleTop = true } } catch (e: Exception) {} }
        val back: () -> Unit = { if (!navController.popBackStack()) goTop("dashboard") }
        val topLevel = setOf("dashboard", "squad", "academy", "transfers", "matches", "season_summary", "world", "career_summary", "assistant", "settings", "connection")
        val nav: (String) -> Unit = { r -> if (r.substringBefore("/") in topLevel) goTop(r) else goDetail(r) }

        // Handle navigation triggered by clicking a notification, or by a status pill on some screen.
        LaunchedEffect(pendingRoute) {
            val route = pendingRoute
            if (!route.isNullOrBlank()) {
                drawerState.close()
                if (route == "connection" || route == "setup") goDetail(route) else nav(route)
                viewModel.clearPendingRoute()
            }
        }

        CompositionLocalProvider(LocalOpenDrawer provides openDrawer) {
            ModalNavigationDrawer(
                drawerState = drawerState,
                scrimColor = com.fctracker.career.ui.theme.FcScrim,
                drawerContent = {
                    AppDrawer(
                        viewModel = viewModel,
                        state = state,
                        currentRoute = currentRoute ?: "dashboard",
                        navigateTo = { route -> scope.launch { drawerState.close(); nav(route) } }
                    )
                }
            ) {
                Column(Modifier.fillMaxSize().background(com.fctracker.career.ui.theme.FcBg)) {
                    Box(Modifier.weight(1f)) {
                        NavHost(navController = navController, startDestination = "dashboard") {
                            composable("dashboard") { HomeScreen(viewModel, nav) }
                            composable("squad") { SquadScreen(viewModel, openDrawer) { id -> goDetail("player/$id") } }
                            composable("player/{id}") { entry -> PlayerProfileScreen(viewModel, entry.arguments?.getString("id").orEmpty(), back) }
                            composable("academy") { AcademyScreen(viewModel, openDrawer) }
                            composable("transfers") { TransfersScreen(viewModel, openDrawer) }
                            composable("matches") { MatchesScreen(viewModel, openDrawer) }
                            composable("season_summary") { SeasonSummaryScreen(viewModel, openDrawer, nav) }
                            composable("world") { WorldScreen(viewModel, openDrawer) }
                            composable("recap") { RecapScreen(viewModel, back) }
                            composable("career_summary") { CareerSummaryScreen(viewModel, openDrawer) { id -> goDetail("player/$id") } }
                            composable("assistant") { AssistantScreen(viewModel, openDrawer) }
                            composable("milestones") { MilestonesScreen(viewModel, back) }
                            composable("widgets") { WidgetsScreen(back) }
                            composable("theming") { ThemingScreen(viewModel, back) }
                            composable("club_history_edit") { ClubHistoryEditScreen(viewModel, back) }
                            composable("connection") {
                                ConnectionScreen(viewModel, onOpenSetup = { goDetail("setup") }) { openDrawer() }
                            }
                            composable("setup") { SetupGuideScreen(viewModel) { back() } }
                            composable("settings") { SettingsScreen(viewModel, openDrawer, nav) }
                        }
                    }
                    if (currentRoute != null && currentRoute !in NO_BAR_ROUTES) {
                        GlassBottomBar(current = barPlace(currentRoute), onSelect = { goTop(it) })
                    }
                }
            }
        }
    }
}
