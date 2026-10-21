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

@Composable
private fun Pending(title: String, nav: NavIcon, onNav: () -> Unit) {
    FcPage(header = { FcTopBar(title, nav = nav, onNav = onNav) }) { item { Muted("Coming in this build.") } }
}
