package com.fctracker.career.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.ui.theme.FcTheme
import com.fctracker.career.ui.theme.Outfit
import com.fctracker.career.ui.theme.PlexMono

/** The colour pairs of the design: a strong colour for text and icons, a dark one behind it. */
enum class Tone { GOOD, WARN, BAD, INFO, ACCENT, ACCENT2, MUTED, TEXT }

@Composable
fun toneColors(tone: Tone): Pair<Color, Color> {
    val c = FcTheme.colors
    return when (tone) {
        Tone.GOOD -> c.good to c.goodBg
        Tone.WARN -> c.warn to c.warnBg
        Tone.BAD -> c.bad to c.badBg
        Tone.INFO -> c.info to c.infoBg
        Tone.ACCENT -> c.accent to c.accentBg
        Tone.ACCENT2 -> c.accent2 to c.accent2Bg
        Tone.MUTED -> c.muted to c.surface2
        Tone.TEXT -> c.text to c.surface2
    }
}

fun resultTone(result: String): Tone = when (result) {
    "W" -> Tone.GOOD
    "D" -> Tone.WARN
    else -> Tone.BAD
}

fun resultWord(result: String): String = when (result) {
    "W" -> "Win"
    "D" -> "Draw"
    else -> "Loss"
}

// -------------------------------------------------------------------------------------------------------------------
// Text
// -------------------------------------------------------------------------------------------------------------------

@Composable
fun FcText(
    text: String,
    size: TextUnit = 14.sp,
    weight: FontWeight = FontWeight.Normal,
    color: Color = FcTheme.colors.text,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    lineHeight: TextUnit = TextUnit.Unspecified,
    align: TextAlign? = null,
    family: FontFamily = Outfit
) {
    Text(
        text, modifier = modifier, fontSize = size, fontWeight = weight, color = color, fontFamily = family,
        maxLines = maxLines, overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        lineHeight = lineHeight, textAlign = align
    )
}

@Composable
fun Muted(text: String, size: TextUnit = 12.sp, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, weight: FontWeight = FontWeight.Normal, align: TextAlign? = null) =
    FcText(text, size, weight, FcTheme.colors.muted, modifier, maxLines, lineHeight = (size.value * 1.4f).sp, align = align)

/** A figure in the monospaced face, so numbers line up. */
@Composable
fun Mono(text: String, size: TextUnit = 14.sp, color: Color = FcTheme.colors.text, weight: FontWeight = FontWeight.Medium, modifier: Modifier = Modifier, maxLines: Int = 1, align: TextAlign? = null) =
    FcText(text, size, weight, color, modifier, maxLines, align = align, family = PlexMono)

/** A headline figure: large ones in Outfit, smaller ones monospaced (the design's "mixed" number face). */
@Composable
fun Figure(text: String, size: TextUnit = 24.sp, color: Color = FcTheme.colors.text, weight: FontWeight = FontWeight.Medium, modifier: Modifier = Modifier) {
    val display = size.value >= 30f
    FcText(text, size, weight, color, modifier, maxLines = 1, lineHeight = if (display) size else (size.value * 1.15f).sp, family = if (display) Outfit else PlexMono)
}

/** Small upper-case label in the monospaced face ("NEXT MATCH"). */
@Composable
fun FcLabel(text: String, modifier: Modifier = Modifier, color: Color = FcTheme.colors.muted, size: TextUnit = 11.sp) =
    Text(text.uppercase(), modifier = modifier, fontSize = size, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp, color = color, fontFamily = PlexMono, maxLines = 1, overflow = TextOverflow.Ellipsis)

/** The quieter sentence-case label used on Home ("Next match"). */
@Composable
fun QuietLabel(text: String, modifier: Modifier = Modifier) =
    FcText(text, 12.sp, FontWeight.Medium, FcTheme.colors.muted, modifier, maxLines = 1)

// -------------------------------------------------------------------------------------------------------------------
// Surfaces
// -------------------------------------------------------------------------------------------------------------------

/** The design's card: rounded 22, the surface colour, a hairline border. [glow] adds a soft light of the accent. */
@Composable
fun FcCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    gap: Dp = 12.dp,
    glow: Boolean = false,
    tone: Tone? = null,
    background: Color? = null,
    border: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val c = FcTheme.colors
    val shape = RoundedCornerShape(22.dp)
    val (toneFg, toneBg) = if (tone != null) toneColors(tone) else c.text to c.surface
    val bg = background ?: if (tone != null) toneBg else c.surface
    val edge = if (tone != null) toneFg.copy(alpha = 0.33f) else c.border
    var m = modifier.clip(shape).background(bg)
    if (glow) {
        val accentBg = c.accentBg
        m = m.drawBehind {
            drawRect(Brush.radialGradient(listOf(accentBg, Color.Transparent), center = Offset(size.width, 0f), radius = size.width * 0.9f))
        }
    }
    if (border) m = m.border(1.dp, edge, shape)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), verticalArrangement = Arrangement.spacedBy(gap), content = content)
}

/** A section title with the accent icon tile, an optional line under it and an optional action on the right. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    icon: ImageVector? = Icons.Default.Star,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val c = FcTheme.colors
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (icon != null) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(c.accent2Bg), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = c.accent2, modifier = Modifier.size(17.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            FcText(title, 18.sp, FontWeight.SemiBold)
            if (sub != null) Muted(sub)
        }
        if (action != null && onAction != null) {
            FcText(action, 13.sp, FontWeight.Bold, c.accent, Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 6.dp, vertical = 8.dp))
        }
        trailing?.invoke()
    }
}

@Composable
fun HRule(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(FcTheme.colors.border))

/** A coloured message box: a title, a line of explanation, optional buttons. */
@Composable
fun Notice(tone: Tone, title: String, body: String? = null, modifier: Modifier = Modifier, icon: ImageVector? = null, actions: (@Composable RowScope.() -> Unit)? = null) {
    val (fg, bg) = toneColors(tone)
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier.fillMaxWidth().clip(shape).background(bg).border(1.dp, fg.copy(alpha = 0.33f), shape).padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon ?: if (tone == Tone.WARN || tone == Tone.BAD) Icons.Default.Warning else Icons.Default.Info, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            FcText(title, 13.sp, FontWeight.Bold, fg)
            if (body != null) FcText(body, 12.sp, color = FcTheme.colors.text.copy(alpha = 0.9f), lineHeight = 17.sp)
            if (actions != null) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

@Composable
fun EmptyCard(text: String, modifier: Modifier = Modifier) {
    FcCard(modifier.fillMaxWidth(), padding = 14.dp) {
        Muted(text, 13.sp, Modifier.fillMaxWidth().padding(vertical = 14.dp), align = TextAlign.Center)
    }
}

// -------------------------------------------------------------------------------------------------------------------
// Tags, pills, chips
// -------------------------------------------------------------------------------------------------------------------

/** A small upper-case tag ("YOUTH", "FROM THE GAME"). */
@Composable
fun Tag(text: String, tone: Tone = Tone.MUTED, modifier: Modifier = Modifier) {
    val (fg, bg) = toneColors(tone)
    Box(modifier.height(20.dp).clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 7.dp), contentAlignment = Alignment.Center) {
        Text(text.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = fg, fontFamily = PlexMono, maxLines = 1)
    }
}

/** An information pill ("Contract 2y 8m · Jun 2029"). */
@Composable
fun Pill(text: String, tone: Tone = Tone.MUTED, modifier: Modifier = Modifier, warn: Boolean = false) {
    val (fg, bg) = toneColors(tone)
    val shape = RoundedCornerShape(50)
    Row(
        modifier.clip(shape).background(bg).border(1.dp, fg.copy(alpha = 0.33f), shape).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (warn) Icon(Icons.Default.Warning, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
        Text(text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = fg, fontFamily = PlexMono)
    }
}

/** A filter chip, 44 high. */
@Composable
fun FcChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val c = FcTheme.colors
    val shape = RoundedCornerShape(50)
    val (bg, fg, bd) = if (selected) Triple(c.accentBg, c.accent, c.accent) else Triple(c.surface, c.text, c.border)
    Row(
        modifier.height(44.dp).clip(shape).background(bg).border(1.dp, bd, shape).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
        FcText(label, 13.sp, if (selected) FontWeight.Bold else FontWeight.Medium, fg, maxLines = 1)
    }
}

@Composable
fun ChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, l -> FcChip(l, i == selected, { onSelect(i) }) }
    }
}

/** Two to four options side by side ("Played (16)" / "Upcoming (30)"). */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = FcTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val shape = RoundedCornerShape(50)
            Box(
                Modifier.weight(1f).height(44.dp).clip(shape).background(if (on) c.accentBg else c.surface)
                    .border(1.dp, if (on) c.accent else c.border, shape).clickable(role = Role.Tab) { onSelect(i) }.padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center
            ) { FcText(o, 13.sp, if (on) FontWeight.Bold else FontWeight.Medium, if (on) c.accent else c.text, maxLines = 1) }
        }
    }
}

/** The soft tabs at the top of a page (Roster / Formation / Tactics / Wages / Injuries). */
@Composable
fun SoftTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = FcTheme.colors
    val small = tabs.size >= 5
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.surface2).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tabs.forEachIndexed { i, t ->
            val on = i == selected
            Box(
                Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(50)).background(if (on) c.surface else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(i) }.padding(horizontal = if (small) 2.dp else 6.dp),
                contentAlignment = Alignment.Center
            ) { FcText(t, if (small) 13.sp else 14.sp, if (on) FontWeight.SemiBold else FontWeight.Medium, if (on) c.text else c.muted, maxLines = 1) }
        }
    }
}

// -------------------------------------------------------------------------------------------------------------------
// Buttons
// -------------------------------------------------------------------------------------------------------------------

enum class ButtonKind { PRIMARY, SECONDARY, GHOST, DANGER, WARN }

@Composable
fun FcButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    icon: ImageVector? = null,
    small: Boolean = false,
    enabled: Boolean = true,
    full: Boolean = false
) {
    val c = FcTheme.colors
    val (bg, fg, bd) = when (kind) {
        ButtonKind.PRIMARY -> Triple(c.accent, c.onAccent, c.accent)
        ButtonKind.SECONDARY -> Triple(c.surface2, c.text, c.border)
        ButtonKind.GHOST -> Triple(Color.Transparent, c.accent, Color.Transparent)
        ButtonKind.DANGER -> Triple(c.badBg, c.bad, c.bad.copy(alpha = 0.4f))
        ButtonKind.WARN -> Triple(c.warnBg, c.warn, c.warn.copy(alpha = 0.4f))
    }
    val shape = RoundedCornerShape(50)
    var m = modifier.heightIn(min = if (small) 44.dp else 50.dp)
    if (full) m = m.fillMaxWidth()
    Row(
        m.clip(shape).background(if (enabled) bg else c.surface2).border(1.dp, if (enabled) bd else c.border, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = if (small) 12.dp else 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        val tint = if (enabled) fg else c.muted
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        FcText(label, if (small) 13.sp else 15.sp, FontWeight.SemiBold, tint, maxLines = 1)
    }
}

/** Two buttons sharing a row equally. */
@Composable
fun ButtonPair(first: @Composable (Modifier) -> Unit, second: @Composable (Modifier) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        first(Modifier.weight(1f))
        second(Modifier.weight(1f))
    }
}

@Composable
fun FcIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 44.dp, background: Color? = null, tint: Color? = null, border: Boolean = true) {
    val c = FcTheme.colors
    var m = modifier.size(size).clip(CircleShape).background(background ?: c.surface)
    if (border) m = m.border(1.dp, c.border, CircleShape)
    Box(m.clickable(role = Role.Button, onClick = onClick).semantics { this.contentDescription = contentDescription }, contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint ?: c.text, modifier = Modifier.size(20.dp))
    }
}

/** The extended floating button ("+ Log match"). */
@Composable
fun Fab(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector = Icons.Default.Add) {
    val c = FcTheme.colors
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(28.dp)).background(c.accent).clickable(role = Role.Button, onClick = onClick).padding(start = 18.dp, end = 22.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = c.onAccent, modifier = Modifier.size(22.dp))
        FcText(label, 15.sp, FontWeight.SemiBold, c.onAccent, maxLines = 1)
    }
}

// -------------------------------------------------------------------------------------------------------------------
// Football bits: results, form, ratings
// -------------------------------------------------------------------------------------------------------------------

@Composable
fun ResultBadge(result: String, size: Dp = 26.dp, modifier: Modifier = Modifier) {
    val (fg, bg) = toneColors(resultTone(result))
    Box(modifier.size(size).clip(CircleShape).background(bg).semantics { contentDescription = resultWord(result) }, contentAlignment = Alignment.Center) {
        Text(result, fontSize = (size.value / 2 - 1).coerceAtLeast(10f).sp, fontWeight = FontWeight.Bold, color = fg, fontFamily = PlexMono)
    }
}

/** W / D / L circles, oldest first. */
@Composable
fun FormStrip(results: List<String>, size: Dp = 26.dp, gap: Dp = 6.dp, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = "Form: " + results.joinToString(", ") { resultWord(it).lowercase() } }, horizontalArrangement = Arrangement.spacedBy(gap)) {
        results.forEach { ResultBadge(it, size) }
    }
}

/** Tiny form dots for tables. */
@Composable
fun FormDots(form: String, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        form.forEach { ch ->
            Box(Modifier.size(7.dp).clip(CircleShape).background(toneColors(resultTone(ch.toString())).first))
        }
    }
}

@Composable
fun OvrBadge(ovr: Int, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val tone = if (ovr >= 75) Tone.GOOD else if (ovr >= 70) Tone.WARN else Tone.MUTED
    val (fg, bg) = toneColors(tone)
    Box(modifier.size(size).clip(CircleShape).background(bg).semantics { contentDescription = "Overall $ovr" }, contentAlignment = Alignment.Center) {
        Text("$ovr", fontSize = (size.value * 0.45f).sp, fontWeight = FontWeight.Bold, color = fg, fontFamily = PlexMono)
    }
}

fun ratingTone(v: Double): Tone = if (v >= 8.0) Tone.GOOD else if (v >= 7.0) Tone.WARN else Tone.BAD

@Composable
fun RatingChip(rating: Double, modifier: Modifier = Modifier) {
    val (fg, bg) = toneColors(ratingTone(rating))
    Box(modifier.height(26.dp).widthIn(min = 40.dp).clip(RoundedCornerShape(13.dp)).background(bg).padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
        Text(String.format(java.util.Locale.US, "%.1f", rating), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = fg, fontFamily = PlexMono)
    }
}

@Composable
fun ProgressBar(value: Float, max: Float, color: Color = FcTheme.colors.accent, modifier: Modifier = Modifier, height: Dp = 8.dp, track: Color = FcTheme.colors.surface2) {
    val f = if (max <= 0f) 0f else (value / max).coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(track)) {
        if (f > 0f) Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(height / 2)).background(color))
    }
}

/** "Label ........ value" with the value monospaced. */
@Composable
fun KeyValue(label: String, value: String, valueColor: Color = FcTheme.colors.text, modifier: Modifier = Modifier, mono: Boolean = true) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Muted(label, 13.sp, Modifier.weight(1f))
        if (mono) Mono(value, 13.sp, valueColor, FontWeight.SemiBold, maxLines = 3, align = TextAlign.End, modifier = Modifier.widthIn(max = 220.dp))
        else FcText(value, 14.sp, FontWeight.SemiBold, valueColor, Modifier.widthIn(max = 220.dp), align = TextAlign.End)
    }
}

@Composable
fun KeyValues(rows: List<Pair<String, String>>, modifier: Modifier = Modifier, gap: Dp = 10.dp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(gap)) { rows.forEach { (k, v) -> KeyValue(k, v) } }
}

/** A figure tile: a small label, the figure, an optional line under it. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, tone: Tone? = null, size: TextUnit = 22.sp) {
    val c = FcTheme.colors
    val col = if (tone != null) toneColors(tone).first else c.text
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(c.surface).border(1.dp, c.border, RoundedCornerShape(18.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FcLabel(label, size = 10.sp)
        Figure(value, size, col, FontWeight.SemiBold)
        if (sub != null) Mono(sub, 11.sp, c.muted, FontWeight.Normal, maxLines = 2)
    }
}

/** A plain figure cell for the Home grids (label, figure, line under it). */
@Composable
fun StatCell(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier, tone: Tone? = null, size: TextUnit = 24.sp) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Muted(label, 12.sp, maxLines = 1)
        Figure(value, size, if (tone != null) toneColors(tone).first else FcTheme.colors.text)
        if (sub != null) Muted(sub, 11.sp, maxLines = 1)
    }
}

/** Space at the end of a scrolling page so the last card clears the floating button. */
@Composable
fun BottomSpace(height: Dp = 24.dp) = Spacer(Modifier.height(height))

@Composable
fun Gap(width: Dp) = Spacer(Modifier.width(width))

/** A bordered small square used as a stand-in icon tile (milestone rows, awards). */
@Composable
fun IconTile(icon: ImageVector, tone: Tone = Tone.ACCENT, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val (fg, bg) = toneColors(tone)
    Box(modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size / 2))
    }
}

/** A thin bordered outline, for previews and dashed boxes. */
fun Modifier.hairline(color: Color, radius: Dp = 14.dp): Modifier = this.border(BorderStroke(1.dp, color), RoundedCornerShape(radius))
