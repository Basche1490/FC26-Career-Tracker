package com.fctracker.career.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.MatchResult
import com.fctracker.career.ui.components.FcIcons
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.Mono
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.theme.FcTheme

/** "Poss –  Shots 14–7  xG 2.4–0.9  MOTM Varela": the four key figures of a match. */
fun matchFigures(m: MatchResult): List<Pair<String, String>> {
    val motm = when {
        m.motmName.isBlank() -> "–"
        m.motmIsOpponent -> "Opponent"
        else -> m.motmName.trim().substringAfterLast(' ')
    }
    return listOf(
        "Poss" to if (m.hasTrustedPossession) "${m.possession}%" else "–",
        "Shots" to if (m.shotsFor + m.shotsAgainst > 0) "${m.shotsFor}–${m.shotsAgainst}" else "–",
        "xG" to if (m.expectedGoals > 0 || m.expectedGoalsAgainst > 0) String.format(java.util.Locale.US, "%.1f–%.1f", m.expectedGoals, m.expectedGoalsAgainst) else "–",
        "MOTM" to motm
    )
}

@Composable
fun MatchFiguresRow(m: MatchResult, modifier: Modifier = Modifier) {
    val weights = listOf(0.8f, 1f, 1.1f, 1.2f)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        matchFigures(m).forEachIndexed { i, (k, v) ->
            Column(Modifier.weight(weights[i]), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Muted(k, 11.sp, maxLines = 1)
                Mono(v, 13.sp)
            }
        }
    }
}

/** The goals of a match: the ball, the scorer and the minute; the boot and the assister on the right. */
@Composable
fun GoalLines(m: MatchResult, state: AppState?, modifier: Modifier = Modifier) {
    val c = FcTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        m.goalDetails.forEach { g ->
            val ours = g.scorerId != null
            val col = if (ours) c.text else c.muted
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.SportsSoccer, contentDescription = null, tint = col, modifier = Modifier.size(14.dp))
                    FcText(goalScorerLabel(g, m, state), 12.sp, color = col, maxLines = 1)
                    Muted(goalMinuteLabel(g).trim(), 12.sp)
                }
                goalAssisterLabel(g, state)?.let { a ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(SoccerBootIcon, contentDescription = "Assist", tint = c.muted, modifier = Modifier.size(14.dp))
                        Muted(a, 12.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** "A 3–1 home win against Leicester City". */
fun matchHeadline(m: MatchResult): String {
    val kind = when (m.resultBadge) { "W" -> "win"; "L" -> "defeat"; else -> "draw" }
    val where = if (m.isHome) "home $kind against" else "away $kind at"
    return "A ${m.goalsFor}–${m.goalsAgainst} $where ${m.opponentName}"
}
