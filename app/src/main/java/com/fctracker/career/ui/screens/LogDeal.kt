package com.fctracker.career.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.AppState
import com.fctracker.career.EafcPlayer
import com.fctracker.career.MainViewModel
import com.fctracker.career.Player
import com.fctracker.career.TransferLog
import com.fctracker.career.data.PlayerDatabase
import com.fctracker.career.logic.CareerOps
import com.fctracker.career.logic.Dates
import com.fctracker.career.ui.components.ButtonKind
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcDateField
import com.fctracker.career.ui.components.FcField
import com.fctracker.career.ui.components.FcFullScreen
import com.fctracker.career.ui.components.FcNumberField
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcSelectField
import com.fctracker.career.ui.components.FcSwitchRow
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.FieldPair
import com.fctracker.career.ui.components.HRule
import com.fctracker.career.ui.components.Headshot
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.NavIcon
import com.fctracker.career.ui.components.Notice
import com.fctracker.career.ui.components.Tone
import com.fctracker.career.ui.theme.FcTheme

/** The transfer types as they are stored, and how the form shows them. */
private val DEAL_TYPES = listOf(
    "In (Permanent)" to "In (permanent)", "Out (Permanent)" to "Out (permanent)", "In (Loan)" to "In (loan)",
    "Out (Loan)" to "Out (loan)", "Free Agent Sign" to "Free agent sign", "Retirement" to "Retirement"
)

/**
 * The first-team players a deal out (or a retirement) can be about: not sold, retired or in the academy, and not away
 * on loan or here on loan from another club. Strikers first, the goalkeeper last (the squad list's order).
 */
internal fun dealCandidates(state: AppState): List<Player> {
    val academyIds = state.academyPlayers.map { it.id }.toSet()
    val academyNames = state.academyPlayers.flatMap { listOf(it.displayName.lowercase().trim(), it.name.lowercase().trim()) }.toSet()
    return state.players.filter { p ->
        !p.isAcademy && p.id !in academyIds && p.displayName.lowercase().trim() !in academyNames &&
            listOf("Academy", "Youth", "Sold", "Retired", "Loaned Out", "Loaned").none { p.status.equals(it, true) } &&
            !p.isLoanedOut && !p.isLoanedIn && p.loanDirection == null
    }.sortedWith(compareBy<Player>({ LineupOrder.rank(it.primaryPosition) }, { LineupOrder.side(it.primaryPosition) }).thenByDescending { it.overall }.thenBy { it.displayName.lowercase() })
}

private fun shortName(p: EafcPlayer) = p.commonName.ifBlank { p.name }

/** Log deal: a transfer in or out, a loan, a free agent or a retirement, in the design's full-screen form. */
@Composable
fun TransferFormModal(
    state: AppState,
    viewModel: MainViewModel,
    logToEdit: TransferLog?,
    initialType: String = "In (Permanent)",
    onDismiss: () -> Unit
) {
    val c = FcTheme.colors
    var type by remember(logToEdit, initialType) { mutableStateOf(logToEdit?.type ?: initialType) }
    val retirement = type == "Retirement"
    val outgoing = type == "Out (Permanent)" || type == "Out (Loan)" || retirement
    var player by remember(logToEdit) { mutableStateOf(logToEdit?.playerName ?: "") }
    var playerPicked by remember(logToEdit) { mutableStateOf(logToEdit != null) }
    var isRegen by remember(logToEdit) { mutableStateOf(logToEdit?.isRegen ?: false) }
    var regenOriginal by remember(logToEdit) { mutableStateOf(logToEdit?.regenOriginalName ?: "") }
    var regenPicked by remember(logToEdit) { mutableStateOf<EafcPlayer?>(null) }
    var regenChosen by remember(logToEdit) { mutableStateOf(logToEdit?.regenOriginalName != null) }
    var includeSwap by remember(logToEdit) { mutableStateOf(logToEdit?.includeSwap ?: false) }
    var swap by remember(logToEdit) { mutableStateOf(logToEdit?.swapPlayerName ?: "") }
    var swapPicked by remember(logToEdit) { mutableStateOf(logToEdit?.swapPlayerName != null) }
    var team by remember(logToEdit) { mutableStateOf(logToEdit?.fromToClub?.takeIf { it != "Retired" } ?: "") }
    var teamPicked by remember(logToEdit) { mutableStateOf(logToEdit != null) }
    var fee by remember(logToEdit) { mutableStateOf(logToEdit?.feeM?.takeIf { it > 0 }?.toString() ?: "") }
    var date by remember(logToEdit) { mutableStateOf(logToEdit?.date?.takeIf { Dates.isDate(it) } ?: CareerOps.today(state)) }
    var confirmDelete by remember { mutableStateOf(false) }
    val candidates = remember(state.players, state.academyPlayers) { dealCandidates(state) }
    val clubs = remember(state.leagueTables, state.recentMatches) {
        (state.leagueTables.flatMap { t -> t.rows.map { it.club } } + state.recentMatches.map { it.opponentName }).filter { it.isNotBlank() && !it.equals(state.clubName, true) }.distinct()
    }

    fun save() {
        val log = TransferLog(
            id = logToEdit?.id ?: java.util.UUID.randomUUID().toString(),
            playerName = player.trim(),
            type = type,
            feeM = if (retirement) 0.0 else (fee.toDoubleOrNull() ?: 0.0),
            fromToClub = if (retirement) "Retired" else team.trim(),
            date = date,
            includeSwap = !retirement && includeSwap,
            swapPlayerName = swap.trim().takeIf { includeSwap && !retirement && it.isNotEmpty() },
            isRegen = !retirement && isRegen,
            // the real player a regen is based on, by his short name ("Sergio Busquets", not "Sergio Busquets Burgos")
            regenOriginalName = if (isRegen && !retirement) regenOriginal.trim().ifBlank { null }?.let { viewModel.shortPlayerName(it) } else null,
            headshotUrl = regenPicked?.let { PlayerDatabase.headshotUrl(it.id) } ?: logToEdit?.headshotUrl ?: "",
            position = logToEdit?.position ?: ""      // blank for a new deal: the player database then supplies it
        )
        if (logToEdit != null) viewModel.updateTransferLog(logToEdit, log) else viewModel.addTransferLog(log)
        onDismiss()
    }
    val canSave = player.isNotBlank() && Dates.isDate(date) && (retirement || team.isNotBlank() || type == "Free Agent Sign")

    FcFullScreen(onDismiss = onDismiss) {
        FcPage(header = { FcTopBar(if (retirement) "Record retirement" else if (logToEdit != null) "Edit deal" else "Log deal", nav = NavIcon.CLOSE, onNav = onDismiss) }) {
            if (retirement) item {
                Notice(Tone.WARN, "Career retirement", "Retire a first-team player: his status becomes Retired and the retirement stays in the transfer records.", icon = Icons.AutoMirrored.Filled.ExitToApp)
            }
            item {
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                    FcSelectField("Transfer type", DEAL_TYPES.firstOrNull { it.first == type }?.second ?: type, DEAL_TYPES.map { it.second }, { label ->
                        val t = DEAL_TYPES.firstOrNull { it.second == label }?.first ?: label
                        val wasOut = outgoing
                        type = t
                        val nowOut = t == "Out (Permanent)" || t == "Out (Loan)" || t == "Retirement"
                        if (wasOut != nowOut && logToEdit == null) { player = ""; playerPicked = false }
                    }, hint = "In (permanent), Out (permanent), In (loan), Out (loan), Free agent sign or Retirement.")

                    if (outgoing) {
                        FcField(if (retirement) "Retiring player" else "Player leaving", player, { player = it; playerPicked = false }, placeholder = "Search your squad",
                            hint = "Your first team, strikers first. Players away on loan, here on loan, sold or in the academy are not listed.",
                            trailing = { Icon(Icons.Default.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp)) })
                        if (!playerPicked) {
                            val shown = candidates.filter { player.isBlank() || it.displayName.contains(player, true) || it.name.contains(player, true) || it.primaryPosition.equals(player.trim(), true) }
                            if (shown.isEmpty()) Muted("No first-team player matches.", 12.sp)
                            else SquadPickList(shown) { p -> player = p.displayName; playerPicked = true }
                        }
                    } else {
                        FcField("Player name", player, { player = it; playerPicked = false }, placeholder = "Type a name",
                            hint = if (isRegen) "A regen is not in the player database, so type his name as the game shows it." else "With Regen off this searches the player database.",
                            trailing = if (!isRegen) ({ Icon(Icons.Default.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp)) }) else null)
                        if (!isRegen && !playerPicked && player.trim().length >= 2) {
                            val found = viewModel.searchPlayersAndTeams(player.trim()).take(5)
                            if (found.isNotEmpty()) DatabasePickList(found) { ep -> player = shortName(ep); playerPicked = true; if (team.isBlank()) team = ep.team }
                        }
                    }

                    if (!retirement) {
                        FcSwitchRow("Regen", isRegen, { isRegen = it }, sub = "A player the game generated")
                        if (isRegen) {
                            FcField("Original player name", regenOriginal, { regenOriginal = it; regenPicked = null; regenChosen = false }, placeholder = "The real player",
                                hint = "The real player this regen is based on. He lends the headshot; the name above stays the regen’s own.",
                                trailing = { Icon(Icons.Default.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp)) })
                            if (!regenChosen && regenOriginal.trim().length >= 3) {
                                val found = viewModel.searchPlayersAndTeams(regenOriginal.trim()).take(4)
                                if (found.isNotEmpty()) DatabasePickList(found) { ep -> regenOriginal = shortName(ep); regenPicked = ep; regenChosen = true }
                            }
                        }
                        FcSwitchRow("Include player swap", includeSwap, { includeSwap = it }, sub = if (outgoing) "A player comes the other way in part exchange" else "Give a first-team player in part exchange")
                        if (includeSwap) {
                            if (outgoing) {
                                FcField("Search incoming player", swap, { swap = it; swapPicked = false },
                                    trailing = { Icon(Icons.Default.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp)) })
                                if (!swapPicked && swap.trim().length >= 2) {
                                    val found = viewModel.searchPlayersAndTeams(swap.trim()).take(4)
                                    if (found.isNotEmpty()) DatabasePickList(found) { ep -> swap = shortName(ep); swapPicked = true }
                                }
                            } else {
                                fun label(p: Player) = "${p.displayName} · ${p.primaryPosition} · OVR ${p.overall} · Age ${p.age}"
                                FcSelectField("Senior player to swap out", candidates.firstOrNull { it.displayName == swap }?.let { label(it) } ?: swap, candidates.map { label(it) }, { pick ->
                                    swap = candidates.firstOrNull { label(it) == pick }?.displayName ?: swap
                                })
                            }
                        }
                        FcField(if (outgoing) "Club he joins" else "Club he comes from", team, { team = it; teamPicked = false }, placeholder = "Search a club",
                            trailing = { Icon(Icons.Default.Search, contentDescription = null, tint = c.muted, modifier = Modifier.size(18.dp)) })
                        if (!teamPicked && team.trim().length >= 2) {
                            val found = (clubs + viewModel.searchPlayersAndTeams(team.trim()).map { it.team }).filter { it.contains(team.trim(), true) }.distinct().take(5)
                            if (found.isNotEmpty()) SuggestionList(found.map { it to null }) { name, _ -> team = name; teamPicked = true }
                        }
                    }
                }
            }
            item {
                FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                    if (retirement) FcDateField("Date", date, { date = it })
                    else FieldPair(
                        { m -> FcNumberField("Fee (${state.currencySymbol}M)", fee, { fee = it }, m, decimal = true, placeholder = "0") },
                        { m -> FcDateField("Date", date, { date = it }, m) }
                    )
                }
            }
            item {
                FcButton(if (logToEdit != null) "Update" else if (retirement) "Confirm retirement" else "Submit", { save() },
                    kind = if (retirement) ButtonKind.WARN else ButtonKind.PRIMARY, icon = Icons.Default.Check, full = true, enabled = canSave)
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FcButton("Cancel", onDismiss, Modifier.weight(1f), kind = ButtonKind.SECONDARY)
                    if (logToEdit != null) FcButton("Delete", { confirmDelete = true }, Modifier.weight(1f), kind = ButtonKind.DANGER)
                }
            }
        }
        if (confirmDelete && logToEdit != null) {
            com.fctracker.career.ui.components.ConfirmDialog("Delete this deal?", "${logToEdit.playerName} · ${logToEdit.type}", "Delete",
                { viewModel.deleteTransferLog(logToEdit.id); onDismiss() }, { confirmDelete = false })
        }
    }
}

/** First-team players to pick from: headshot, name, position, rating and age. */
@Composable
private fun SquadPickList(players: List<Player>, onPick: (Player) -> Unit) {
    val c = FcTheme.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, c.border, RoundedCornerShape(18.dp))) {
        players.forEachIndexed { i, p ->
            if (i > 0) HRule()
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(p) }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Headshot(p.headshotUrl, p.displayName, 36.dp)
                Column(Modifier.weight(1f)) {
                    FcText(p.displayName, 15.sp, FontWeight.SemiBold, maxLines = 1)
                    Muted("${p.primaryPosition} · OVR ${p.overall} · Age ${p.age}", 12.sp)
                }
            }
        }
    }
}

/** Players from the database to pick from, with their headshots. */
@Composable
private fun DatabasePickList(players: List<EafcPlayer>, onPick: (EafcPlayer) -> Unit) {
    val c = FcTheme.colors
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.dp, c.border, RoundedCornerShape(18.dp))) {
        players.forEachIndexed { i, ep ->
            if (i > 0) HRule()
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(ep) }.padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Headshot(PlayerDatabase.headshotUrl(ep.id), shortName(ep), 36.dp)
                Column(Modifier.weight(1f)) {
                    FcText(shortName(ep), 15.sp, FontWeight.SemiBold, maxLines = 1)
                    Muted(listOf(ep.position, ep.team).filter { it.isNotBlank() }.joinToString(" · "), 12.sp, maxLines = 1)
                }
            }
        }
    }
}
