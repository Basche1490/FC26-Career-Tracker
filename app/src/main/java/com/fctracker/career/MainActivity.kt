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
import com.fctracker.career.ui.screens.DashboardScreen
import com.fctracker.career.ui.screens.OnboardingScreen
import com.fctracker.career.ui.screens.SquadScreen
import com.fctracker.career.ui.screens.AcademyScreen
import com.fctracker.career.ui.screens.TransfersScreen
import com.fctracker.career.ui.screens.MatchesScreen
import com.fctracker.career.ui.screens.SeasonSummaryScreen
import com.fctracker.career.ui.screens.CareerSummaryScreen
import com.fctracker.career.ui.screens.AssistantScreen
import com.fctracker.career.ui.screens.SettingsScreen
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

@Composable
fun MainApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val pendingRoute by viewModel.pendingRoute.collectAsState()

    FCTheme(primaryHex = state.primaryColorHex, secondaryHex = state.secondaryColorHex) {
        val navController = rememberNavController()

        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()
        
        val focusManager = LocalFocusManager.current

        LaunchedEffect(drawerState.isOpen) {
            if (drawerState.isOpen) {
                focusManager.clearFocus()
            }
        }
        
        LaunchedEffect(Unit) {
            viewModel.startTelemetryPolling()
        }

        // Handle navigation triggered by clicking a notification
        LaunchedEffect(pendingRoute, state.isOnboarded) {
            val route = pendingRoute
            if (!route.isNullOrBlank() && state.isOnboarded) {
                try {
                    drawerState.close()
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                } catch (e: Exception) {
                    try {
                        navController.navigate(route)
                    } catch (e2: Exception) {}
                }
                viewModel.clearPendingRoute()
            }
        }
        
        // Request Notification Permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) {}
            LaunchedEffect(Unit) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

    LaunchedEffect(state.isOnboarded) {
        if (state.isOnboarded) {
            try {
                navController.navigate("dashboard") {
                    popUpTo(navController.graph.id) { inclusive = false }
                    launchSingleTop = true
                }
            } catch (e: Exception) {
                // Ignore during initial NavHost graph attachment
            }
        }
    }

    if (!state.isOnboarded) {
        OnboardingScreen(viewModel, 
            onComplete = { manager, club, country, league, startYear, currencySymbol, primary, secondary, logo, isExistingTeam, aiAgent, aiApiUrl, aiApiKey ->
                viewModel.completeOnboarding(manager, club, country, league, startYear, currencySymbol, primary, secondary, logo, isExistingTeam, aiAgent, aiApiUrl, aiApiKey)
                try {
                    navController.navigate("dashboard") {
                        popUpTo(navController.graph.id) { inclusive = false }
                        launchSingleTop = true
                    }
                } catch (e: Exception) {}
            }
        )
    } else {
        ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route ?: "dashboard"
            AppDrawer(
                state = state,
                currentRoute = currentRoute,
                navigateTo = { route ->
                    scope.launch {
                        drawerState.close()
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    }
                }
            )
        }
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            NavHost(navController = navController, startDestination = "dashboard") {
                composable("dashboard") { DashboardScreen(viewModel, onNavigate = { route -> scope.launch { navController.navigate(route) } }) { scope.launch { drawerState.open() } } }
                composable("squad") { SquadScreen(viewModel) { scope.launch { drawerState.open() } } }
                composable("academy") { AcademyScreen(viewModel) { scope.launch { drawerState.open() } } }
                composable("transfers") { TransfersScreen(viewModel) { scope.launch { drawerState.open() } } }
                composable("matches") { MatchesScreen(viewModel) { scope.launch { drawerState.open() } } }
                composable("season_summary") { SeasonSummaryScreen(viewModel) { scope.launch { drawerState.open() } } }
                composable("career_summary") { CareerSummaryScreen(viewModel) { scope.launch { drawerState.open() } } }
                composable("assistant") { AssistantScreen(viewModel) { scope.launch { drawerState.open() } } }
                composable("settings") { SettingsScreen(viewModel) { scope.launch { drawerState.open() } } }
            }
        }
    }
    }
}

}
