package com.fctracker.career.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fctracker.career.ui.theme.FcTheme

/**
 * A page that opens over the current screen (Log match, Log deal, Edit player, Head to head): the whole screen in the
 * app's background, drawn edge to edge with its content kept clear of the status bar, the navigation bar and the
 * keyboard. Back closes it. Put an [FcPage] inside.
 */
@Composable
fun FcFullScreen(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)
    ) {
        Box(Modifier.fillMaxSize().background(FcTheme.colors.bg).navigationBarsPadding()) { content() }
    }
}
