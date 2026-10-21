package com.fctracker.career.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.ui.theme.FcTheme

enum class NavIcon { MENU, BACK, CLOSE, NONE }

/** Opens the side menu from any screen (provided by the app shell). */
val LocalOpenDrawer = compositionLocalOf<() -> Unit> { {} }

/** The page header: menu or back, the title and a line under it, then actions and the Live pill. */
@Composable
fun FcTopBar(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    nav: NavIcon = NavIcon.MENU,
    onNav: (() -> Unit)? = null,
    pill: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val c = FcTheme.colors
    val openDrawer = LocalOpenDrawer.current
    Row(modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (nav) {
            NavIcon.MENU -> FcIconButton(Icons.Default.Menu, "Open menu", onNav ?: openDrawer, border = false)
            NavIcon.BACK -> FcIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onNav ?: {}, border = false)
            NavIcon.CLOSE -> FcIconButton(Icons.Default.Close, "Close", onNav ?: {}, border = false)
            NavIcon.NONE -> {}
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            FcText(title, if (title.length <= 12) 28.sp else 23.sp, FontWeight.SemiBold, c.text, maxLines = 1)
            if (!sub.isNullOrBlank()) Muted(sub, 13.sp, maxLines = 1)
        }
        actions?.invoke(this)
        pill?.invoke()
    }
}

/** The blue sign on top of every archived screen. */
@Composable
fun ArchivedBanner(season: String, onBackToCurrent: () -> Unit, modifier: Modifier = Modifier) {
    val c = FcTheme.colors
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.infoBg).padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = c.info, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            FcText("Archived season · read-only", 13.sp, FontWeight.Bold, c.info, maxLines = 1)
            FcText(season, 12.sp, color = c.text.copy(alpha = 0.85f), maxLines = 1)
        }
        Box(
            Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).border(1.dp, c.info.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                .clickable(role = Role.Button, onClick = onBackToCurrent).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) { FcText("Back to current", 12.sp, FontWeight.SemiBold, c.info, maxLines = 1) }
    }
}

/** Current / Archived, top right of Home. */
@Composable
fun SeasonToggle(archived: Boolean, onCurrent: () -> Unit, onArchived: () -> Unit, modifier: Modifier = Modifier) {
    val c = FcTheme.colors
    Row(modifier.clip(RoundedCornerShape(23.dp)).background(c.surface).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        listOf("Current" to !archived, "Archived" to archived).forEachIndexed { i, (label, on) ->
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (on) c.accentBg else Color.Transparent)
                    .clickable(role = Role.Tab) { if (i == 0) onCurrent() else onArchived() }.padding(horizontal = 13.dp),
                contentAlignment = Alignment.Center
            ) { FcText(label, 13.sp, if (on) FontWeight.SemiBold else FontWeight.Medium, if (on) c.accent else c.muted, maxLines = 1) }
        }
    }
}

/** A small chip with a season label ("2024/25"), shown instead of the Live pill on archived pages. */
@Composable
fun SeasonChip(label: String, modifier: Modifier = Modifier) {
    val c = FcTheme.colors
    Box(modifier.height(34.dp).clip(RoundedCornerShape(17.dp)).background(c.infoBg).padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
        Mono(label, 12.sp, c.info, FontWeight.SemiBold)
    }
}

data class BottomItem(val route: String, val label: String, val icon: ImageVector)

val BOTTOM_ITEMS = listOf(
    BottomItem("dashboard", "Story", Icons.Default.Home),
    BottomItem("matches", "Matches", Icons.AutoMirrored.Filled.List),
    BottomItem("squad", "Squad", Icons.Default.Groups),
    BottomItem("transfers", "Deals", Icons.Default.SwapHoriz)
)

/** The floating bar at the bottom: Story, Matches, Squad, Deals. The active place in the accent, with a light under it. */
@Composable
fun GlassBottomBar(current: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = FcTheme.colors
    val shape = RoundedCornerShape(34.dp)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 14.dp, top = 6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(68.dp).shadow(16.dp, shape, ambientColor = Color.Black, spotColor = Color.Black).clip(shape)
                .background(c.navBg.copy(alpha = 0.97f)).border(1.dp, Color.White.copy(alpha = 0.07f), shape).padding(6.dp)
        ) {
            BOTTOM_ITEMS.forEach { item ->
                val on = item.route == current
                val fg = if (on) c.accent else c.muted
                Column(
                    Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp)).clickable(role = Role.Tab) { onSelect(item.route) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Icon(item.icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(3.dp))
                    FcText(item.label, 11.sp, if (on) FontWeight.SemiBold else FontWeight.Medium, fg, maxLines = 1)
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.width(16.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (on) c.accent else Color.Transparent))
                }
            }
        }
    }
}

/**
 * A standard page: the header (and the archived sign), then a scrolling list of cards with the design's spacing,
 * and an optional floating button.
 */
@Composable
fun FcPage(
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    fab: (@Composable () -> Unit)? = null,
    state: LazyListState = rememberLazyListState(),
    gap: Dp = 14.dp,
    content: LazyListScope.() -> Unit
) {
    val c = FcTheme.colors
    Box(modifier.fillMaxSize().background(c.bg).imePadding()) {
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding()) { header() }
            LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = if (fab != null) 96.dp else 28.dp),
                verticalArrangement = Arrangement.spacedBy(gap),
                content = content
            )
        }
        if (fab != null) Box(Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 16.dp)) { fab() }
    }
}

/** A round dot, for status lines and bullets. */
@Composable
fun Dot(color: Color, size: Dp = 8.dp, modifier: Modifier = Modifier) = Box(modifier.size(size).clip(CircleShape).background(color))

/** A bullet line: an accent dot and the text. */
@Composable
fun Bullet(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Dot(FcTheme.colors.accent, 6.dp, Modifier.padding(top = 7.dp))
        FcText(text, 13.sp, lineHeight = 19.sp, modifier = Modifier.weight(1f))
    }
}
