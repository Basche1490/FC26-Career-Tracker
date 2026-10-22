package com.fctracker.career.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.fctracker.career.ui.theme.FcTheme
import java.util.concurrent.atomic.AtomicLong

/**
 * The pages that open over the current screen (Log match, Log deal, Edit player, Head to head), drawn in the app's own
 * window above everything else. A dialog window of its own did not always keep the bottom of a long page clear of the
 * phone's navigation bar; the app's window does (the screens behind work the same way).
 */
class FcOverlays {
    internal class Entry(val id: Long, val content: State<@Composable () -> Unit>, val onBack: State<() -> Unit>)

    internal val entries = mutableStateListOf<Entry>()
    private val ids = AtomicLong()
    internal fun nextId() = ids.incrementAndGet()
}

/** Where [FcFullScreen] pages are drawn; null outside the main app (a page then opens in a dialog window). */
val LocalFcOverlays = staticCompositionLocalOf<FcOverlays?> { null }

/**
 * Draws the open pages, the newest on top. Put it last in the app's root box, so the pages cover the screens, the
 * menu and the bottom bar.
 */
@Composable
fun FcOverlayHost(overlays: FcOverlays) {
    overlays.entries.forEach { e ->
        key(e.id) {
            // Back closes the newest page first (handlers registered later win); the page's own handlers come after
            BackHandler { e.onBack.value() }
            Box(
                Modifier.fillMaxSize().background(FcTheme.colors.bg)
                    // touches never reach the screen behind
                    .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                    .navigationBarsPadding()
            ) { e.content.value() }
        }
    }
}

/**
 * A page that opens over the current screen: the whole screen in the app's background, its content kept clear of the
 * status bar, the navigation bar and the keyboard. Back closes it. Put an [FcPage] inside.
 */
@Composable
fun FcFullScreen(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val overlays = LocalFcOverlays.current
    if (overlays == null) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)
        ) {
            Box(Modifier.fillMaxSize().background(FcTheme.colors.bg).navigationBarsPadding()) { content() }
        }
        return
    }
    val current = rememberUpdatedState(content)
    val back = rememberUpdatedState(onDismiss)
    val id = remember { overlays.nextId() }
    DisposableEffect(overlays, id) {
        val entry = FcOverlays.Entry(id, current, back)
        overlays.entries.add(entry)
        onDispose { overlays.entries.remove(entry) }
    }
}
