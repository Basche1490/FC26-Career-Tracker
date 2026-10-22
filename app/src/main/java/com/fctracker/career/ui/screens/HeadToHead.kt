package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.MatchResult
import com.fctracker.career.data.PlayerDatabase
import com.fctracker.career.insights.MatchInsights
import com.fctracker.career.logic.Dates
import com.fctracker.career.logic.SeasonView
import com.fctracker.career.ui.components.CompLogo
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcFullScreen
import com.fctracker.career.ui.components.FcLabel
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.Figure
import com.fctracker.career.ui.components.HRule
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.NavIcon
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.StatTile
import com.fctracker.career.ui.components.TeamCrest
import com.fctracker.career.ui.components.Tone
import com.fctracker.career.ui.components.resultTone
import com.fctracker.career.ui.components.toneColors
import com.fctracker.career.ui.theme.FcTheme

/** One match against the opponent, with the season it was played in. */
data class HeadToHeadMatch(val match: MatchResult, val season: Int, val archived: Boolean)

object HeadToHead {
    private fun same(a: String, b: String) = PlayerDatabase.normalize(a) == PlayerDatabase.normalize(b)

    /** Every match against [opponent] in the career, newest first. */
    fun matches(state: AppState, opponent: String): List<HeadToHeadMatch> =
        (state.historicalSeasons.flatMap { h -> h.matches.map { HeadToHeadMatch(it, h.year, true) } } +
            state.recentMatches.map { HeadToHeadMatch(it, state.startYear, false) })
            .filter { same(it.match.opponentName, opponent) }
            .distinctBy { it.match.id }
            .sortedByDescending { it.match.matchDate }
}

/**
 * Head to head: won, drawn and lost against one club over the whole career, a bar in the result colours, the goals,
 * and every match (tap one to open it).
 */
@Composable
fun HeadToHeadPage(state: AppState, opponent: String, onClose: () -> Unit, onOpenMatch: (HeadToHeadMatch) -> Unit) {
    val c = FcTheme.colors
    val list = HeadToHead.matches(state, opponent)
    val r = MatchInsights.record(list.map { it.match })
    FcFullScreen(onDismiss = onClose) {
        FcPage(header = { FcTopBar("Head to head", sub = "${state.clubName} vs $opponent · every season", nav = NavIcon.BACK, onNav = onClose) }) {
            item {
                FcCard(Modifier.fillMaxWidth(), padding = 16.dp, gap = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TeamCrest(opponent, 44.dp)
                        Column(Modifier.weight(1f)) {
                            FcText(opponent, 18.sp, FontWeight.SemiBold, maxLines = 1)
                            Muted(if (r.played == 1) "1 match" else "${r.played} matches", 12.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatTile("Wins", "${r.won}", Modifier.weight(1f), tone = Tone.GOOD)
                        StatTile("Draws", "${r.drawn}", Modifier.weight(1f), tone = Tone.WARN)
                        StatTile("Losses", "${r.lost}", Modifier.weight(1f), tone = Tone.BAD)
                    }
                    ResultBar(r.won, r.drawn, r.lost)
                }
            }
            item {
                FcCard(Modifier.fillMaxWidth(), padding = 16.dp, gap = 12.dp) {
                    SectionHeader("Goals", icon = Icons.Default.SportsSoccer)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val gd = r.goalsFor - r.goalsAgainst
                        StatTile("Goals for", "${r.goalsFor}", Modifier.weight(1f))
                        StatTile("Goals against", "${r.goalsAgainst}", Modifier.weight(1f))
                        StatTile("Difference", "${if (gd > 0) "+" else ""}$gd", Modifier.weight(1f), tone = if (gd > 0) Tone.GOOD else if (gd < 0) Tone.BAD else null)
                    }
                    if (r.played > 0) Muted("Average ${String.format(java.util.Locale.US, "%.1f", r.goalsFor.toDouble() / r.played)}–${String.format(java.util.Locale.US, "%.1f", r.goalsAgainst.toDouble() / r.played)} a match", 12.sp)
                }
            }
            item { SectionHeader("Match history", sub = "Tap a match to open it", icon = Icons.Default.History) }
            if (list.isEmpty()) item { Muted("No matches against $opponent yet.", 13.sp) }
            else item {
                FcCard(Modifier.fillMaxWidth(), padding = 0.dp, gap = 0.dp) {
                    var lastSeason: Int? = null
                    list.forEachIndexed { i, h ->
                        if (h.season != lastSeason) {
                            if (i > 0) HRule()
                            FcLabel(SeasonView.seasonLabel(h.season), Modifier.padding(start = 14.dp, top = 12.dp, bottom = 4.dp))
                            lastSeason = h.season
                        } else HRule()
                        H2HRow(h) { onOpenMatch(h) }
                    }
                }
            }
        }
    }
}

/** Wins, draws and losses as one line in their colours, each part as long as its share. */
@Composable
private fun ResultBar(won: Int, drawn: Int, lost: Int) {
    val c = FcTheme.colors
    val total = won + drawn + lost
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(c.surface2)
                .semantics { contentDescription = "$won won, $drawn drawn, $lost lost" },
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (total > 0) {
                if (won > 0) Box(Modifier.weight(won.toFloat()).height(12.dp).background(c.good))
                if (drawn > 0) Box(Modifier.weight(drawn.toFloat()).height(12.dp).background(c.warn))
                if (lost > 0) Box(Modifier.weight(lost.toFloat()).height(12.dp).background(c.bad))
            }
        }
        if (total > 0) Row(Modifier.fillMaxWidth()) {
            fun pct(n: Int) = "${Math.round(100.0 * n / total)}%"
            Muted("Won ${pct(won)}", 11.sp, Modifier.weight(1f))
            Muted("Drawn ${pct(drawn)}", 11.sp, Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.Center)
            Muted("Lost ${pct(lost)}", 11.sp, Modifier.weight(1f), align = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}

@Composable
private fun H2HRow(h: HeadToHeadMatch, onClick: () -> Unit) {
    val c = FcTheme.colors
    val m = h.match
    val (fg, _) = toneColors(resultTone(m.resultBadge))
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CompLogo(m.competition, 32.dp)
        Column(Modifier.weight(1f)) {
            FcText("${if (m.isHome) "Home" else "Away"} · ${m.competition}", 14.sp, FontWeight.SemiBold, maxLines = 1)
            Muted(listOf(Dates.full(m.matchDate), m.stage).filter { it.isNotBlank() }.joinToString(" · "), 12.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Figure("${m.goalsFor}–${m.goalsAgainst}", 20.sp, fg, FontWeight.SemiBold)
            if (m.homePenalties > 0 || m.awayPenalties > 0) {
                val (p, q) = if (m.isHome) m.homePenalties to m.awayPenalties else m.awayPenalties to m.homePenalties
                Muted("$p–$q pens", 10.sp)
            } else if (m.isExtraTime) Muted("AET", 10.sp)
        }
        Spacer(Modifier.width(2.dp))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp))
    }
}
