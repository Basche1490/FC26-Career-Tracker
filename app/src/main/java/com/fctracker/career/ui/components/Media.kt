package com.fctracker.career.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.fctracker.career.ui.screens.CompetitionLogoImage
import com.fctracker.career.ui.screens.getTeamCrestUrl
import com.fctracker.career.ui.theme.FcTheme
import com.fctracker.career.ui.theme.PlexMono
import java.io.File

/** The plain silhouette for a player without a picture (most youth players and regens). */
@Composable
fun Silhouette(size: Dp, youth: Boolean = false, modifier: Modifier = Modifier) {
    val c = FcTheme.colors
    Box(modifier.size(size).clip(CircleShape).background(if (youth) c.goodBg else c.surface2), contentAlignment = Alignment.BottomCenter) {
        Icon(Icons.Default.Person, contentDescription = null, tint = if (youth) c.good.copy(alpha = 0.7f) else c.muted, modifier = Modifier.size(size * 0.86f).offset(y = size * 0.08f))
    }
}

/** A player's headshot from the player database, or the silhouette. */
@Composable
fun Headshot(url: String?, name: String, size: Dp = 44.dp, modifier: Modifier = Modifier, youth: Boolean = false, ring: Color? = null) {
    val c = FcTheme.colors
    var m = modifier.size(size).clip(CircleShape).background(c.surface2)
    if (ring != null) m = m.border(2.dp, ring, CircleShape)
    if (url.isNullOrBlank()) {
        Silhouette(size, youth, modifier.semantics { contentDescription = name })
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = name,
        contentScale = ContentScale.Crop,
        modifier = m,
        loading = { Silhouette(size, youth) },
        error = { Silhouette(size, youth) }
    )
}

/** Headshot with the shirt number from the game in the corner. */
@Composable
fun NumberedHeadshot(url: String?, name: String, number: Int, size: Dp = 44.dp, modifier: Modifier = Modifier, youth: Boolean = false) {
    val c = FcTheme.colors
    Box(modifier.size(size + 4.dp)) {
        Headshot(url, name, size, youth = youth)
        if (number > 0) {
            Box(
                Modifier.align(Alignment.BottomEnd).offset(x = 2.dp, y = 2.dp).height(20.dp).widthIn(min = 20.dp).clip(RoundedCornerShape(10.dp))
                    .background(c.surface2).border(2.dp, c.surface, RoundedCornerShape(10.dp)).padding(horizontal = 4.dp)
                    .semantics { contentDescription = "Shirt number $number" },
                contentAlignment = Alignment.Center
            ) { Text("$number", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c.text, fontFamily = PlexMono) }
        }
    }
}

fun initialsOf(name: String): String = name.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase().ifEmpty { "?" }

/**
 * The club crest: the image the person uploaded, else the image link they pasted, else (for a real club) the club's
 * crest, else the club's initials on the accent colour. For a real club it opens the club history when tapped.
 */
@Composable
fun ClubCrest(
    clubName: String,
    crestFile: String,
    crestUrl: String,
    isRealClub: Boolean,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val c = FcTheme.colors
    val file = crestFile.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isFile }
    val model: Any? = file ?: crestUrl.takeIf { it.isNotBlank() } ?: if (isRealClub) getTeamCrestUrl(clubName).takeIf { it.isNotBlank() } else null
    var m = modifier.size(size)
    if (onClick != null) m = m.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = "Club history" }
    Box(m, contentAlignment = Alignment.Center) {
        val fallback: @Composable () -> Unit = {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(size * 0.28f)).background(c.accent), contentAlignment = Alignment.Center) {
                Text(initialsOf(clubName), fontSize = (size.value * 0.36f).sp, fontWeight = FontWeight.Bold, color = c.onAccent)
            }
        }
        if (model == null) fallback()
        else SubcomposeAsyncImage(
            model = model, contentDescription = "Club crest", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
            loading = { fallback() }, error = { fallback() }
        )
    }
}

/** A competition's logo in a fixed square, so rows line up. */
@Composable
fun CompLogo(competition: String, size: Dp = 38.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        CompetitionLogoImage(competition, height = size, maxWidth = size)
    }
}

/** An opponent's crest, or his initials. */
@Composable
fun TeamCrest(team: String, size: Dp = 38.dp, modifier: Modifier = Modifier) {
    val c = FcTheme.colors
    val url = remember(team) { getTeamCrestUrl(team) }
    val fallback: @Composable () -> Unit = {
        Box(Modifier.size(size).clip(CircleShape).background(c.surface2), contentAlignment = Alignment.Center) {
            Text(initialsOf(team), fontSize = (size.value * 0.32f).sp, fontWeight = FontWeight.Bold, color = c.muted)
        }
    }
    if (url.isBlank()) fallback()
    else SubcomposeAsyncImage(model = url, contentDescription = "$team crest", contentScale = ContentScale.Fit, modifier = modifier.size(size), loading = { fallback() }, error = { fallback() })
}

/**
 * The league position matchday by matchday: 1 at the top. Tap a dot to see the matchday and the position.
 */
@Composable
fun LeagueChart(positions: List<Int>, clubs: Int, modifier: Modifier = Modifier, height: Dp = 120.dp, onSelect: ((Int?) -> Unit)? = null) {
    val c = FcTheme.colors
    if (positions.isEmpty()) return
    val maxPos = maxOf(clubs, positions.maxOrNull() ?: 1, 2)
    var selected by remember(positions) { mutableStateOf<Int?>(null) }
    Box(modifier.fillMaxWidth().height(height)) {
        Canvas(
            Modifier.fillMaxSize().pointerInput(positions) {
                detectTapGestures { o ->
                    val left = 26.dp.toPx()
                    val w = size.width - left - 8.dp.toPx()
                    val step = if (positions.size > 1) w / (positions.size - 1) else 0f
                    val i = if (step > 0) ((o.x - left) / step).let { kotlin.math.round(it).toInt() } else 0
                    selected = if (i in positions.indices) i else null
                    onSelect?.invoke(selected)
                }
            }.semantics { contentDescription = "League position by matchday, from ${positions.first()} to ${positions.last()}" }
        ) {
            val left = 26.dp.toPx()
            val top = 8.dp.toPx()
            val bottom = 18.dp.toPx()
            val w = size.width - left - 8.dp.toPx()
            val h = size.height - top - bottom
            fun y(p: Int) = top + h * (p - 1) / (maxPos - 1).toFloat()
            val step = if (positions.size > 1) w / (positions.size - 1) else 0f
            for (g in listOf(1, (maxPos + 1) / 2, maxPos)) {
                drawLine(c.border, Offset(left, y(g)), Offset(size.width, y(g)), strokeWidth = 1.dp.toPx())
            }
            val path = Path()
            positions.forEachIndexed { i, p ->
                val x = left + step * i
                if (i == 0) path.moveTo(x, y(p)) else path.lineTo(x, y(p))
            }
            drawPath(path, c.accent2, style = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round))
            positions.forEachIndexed { i, p ->
                val last = i == positions.lastIndex
                drawCircle(if (selected == i) c.text else c.accent2, radius = (if (last || selected == i) 4.5 else 2.5).dp.toPx(), center = Offset(left + step * i, y(p)))
            }
        }
        // axis labels
        Text("1", Modifier.align(Alignment.TopStart), fontSize = 10.sp, color = c.muted, fontFamily = PlexMono)
        Text("$maxPos", Modifier.align(Alignment.BottomStart).padding(bottom = 14.dp), fontSize = 10.sp, color = c.muted, fontFamily = PlexMono)
        Text("1", Modifier.align(Alignment.BottomStart).padding(start = 22.dp), fontSize = 10.sp, color = c.muted, fontFamily = PlexMono)
        Text("${positions.size}", Modifier.align(Alignment.BottomEnd), fontSize = 10.sp, color = c.muted, fontFamily = PlexMono)
        selected?.let { i ->
            Text(
                "MD ${i + 1}: Position ${positions[i]}",
                Modifier.align(Alignment.TopCenter).clip(RoundedCornerShape(6.dp)).background(c.surface2).padding(horizontal = 8.dp, vertical = 3.dp),
                fontSize = 12.sp, color = c.text, fontFamily = PlexMono
            )
        }
    }
}

/** Overall rating by season, each point labelled; the last one (today) filled. */
@Composable
fun OvrChart(labels: List<String>, values: List<Int>, modifier: Modifier = Modifier, height: Dp = 132.dp) {
    val c = FcTheme.colors
    if (values.isEmpty()) return
    val lo = (values.minOrNull() ?: 0) - 3
    val hi = (values.maxOrNull() ?: 0) + 2
    Box(modifier.fillMaxWidth().height(height).semantics { contentDescription = "Overall rating by season: " + labels.zip(values).joinToString(", ") { "${it.first} ${it.second}" } }) {
        Canvas(Modifier.fillMaxSize()) {
            val left = 26.dp.toPx(); val right = 12.dp.toPx(); val top = 18.dp.toPx(); val bottom = 22.dp.toPx()
            val w = size.width - left - right
            val h = size.height - top - bottom
            fun y(v: Int) = top + h * (hi - v) / (hi - lo).toFloat()
            val step = if (values.size > 1) w / (values.size - 1) else 0f
            val pts = values.mapIndexed { i, v -> Offset(left + step * i, y(v)) }
            val area = Path().apply {
                moveTo(pts.first().x, top + h)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, top + h)
                close()
            }
            drawPath(area, c.accent2Bg)
            val line = Path().apply { pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
            drawPath(line, c.accent2, style = Stroke(width = 2.5.dp.toPx(), join = StrokeJoin.Round))
            pts.forEachIndexed { i, p ->
                val last = i == pts.lastIndex
                drawCircle(if (last) c.accent2 else c.surface, radius = (if (last) 5.0 else 3.5).dp.toPx(), center = p)
                drawCircle(c.accent2, radius = (if (last) 5.0 else 3.5).dp.toPx(), center = p, style = Stroke(2.dp.toPx()))
            }
        }
        // value labels above each point, season labels under them
        val n = values.size
        values.forEachIndexed { i, v ->
            val frac = if (n > 1) i / (n - 1f) else 0.5f
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                val usable = maxWidth - 38.dp
                val x = 26.dp + usable * frac - 12.dp
                val yFrac = (hi - v) / (hi - lo).toFloat()
                val yy = 18.dp + (maxHeight - 40.dp) * yFrac - 18.dp
                Text("$v", Modifier.offset(x = x, y = yy).widthIn(min = 24.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = c.text, fontFamily = PlexMono)
                Text(labels.getOrElse(i) { "" }, Modifier.offset(x = x - 4.dp, y = maxHeight - 16.dp), fontSize = 10.sp, color = c.muted)
            }
        }
    }
}
