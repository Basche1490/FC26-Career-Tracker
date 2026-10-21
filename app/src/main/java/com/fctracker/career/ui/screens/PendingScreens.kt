package com.fctracker.career.ui.screens

import androidx.compose.runtime.Composable
import com.fctracker.career.MainViewModel
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.NavIcon

// Temporary stand-ins while the final screens are written (compile check only).

@Composable fun MilestonesScreen(viewModel: MainViewModel, onBack: () -> Unit) = Pending("Milestones", NavIcon.BACK, onBack)
@Composable fun WidgetsScreen(onBack: () -> Unit) = Pending("Widgets", NavIcon.BACK, onBack)
@Composable fun ThemingScreen(viewModel: MainViewModel, onBack: () -> Unit) = Pending("Club colours", NavIcon.BACK, onBack)
@Composable fun SettingsScreen(viewModel: MainViewModel, onMenuClick: () -> Unit, nav: (String) -> Unit) = SettingsScreen(viewModel, onMenuClick)
@Composable fun OnboardingScreen(viewModel: MainViewModel) = OnboardingScreen(viewModel) { manager, club, country, league, startYear, currencySymbol, primary, secondary, logo, isExistingTeam, aiAgent, aiApiUrl, aiApiKey ->
    viewModel.completeOnboarding(manager, club, country, league, startYear, currencySymbol, primary, secondary, logo, isExistingTeam, aiAgent, aiApiUrl, aiApiKey)
}

@Composable
private fun Pending(title: String, nav: NavIcon, onNav: () -> Unit) {
    FcPage(header = { FcTopBar(title, nav = nav, onNav = onNav) }) { item { Muted("Coming in this build.") } }
}
