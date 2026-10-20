package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.Player
import com.fctracker.career.telemetry.ContractTime
import com.fctracker.career.telemetry.Urgency
import com.fctracker.career.ui.theme.StatusDown
import com.fctracker.career.ui.theme.StatusWarn

/** Red when it needs a decision now, amber when soon, quiet otherwise. The words carry the meaning too. */
@Composable
internal fun urgencyColor(urgency: Urgency): Color = when (urgency) {
    Urgency.URGENT -> StatusDown
    Urgency.SOON -> StatusWarn
    Urgency.OK -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun InfoPill(text: String, color: Color, warn: Boolean = false) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.4f), shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (warn) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = color, modifier = Modifier.size(10.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

/** "2y 10m left", "<1m left", or "over". */
internal fun leftText(chip: ContractTime.Chip): String = if (chip.months < 0) "over" else "${chip.text} left"

/**
 * The contract and loan of a player, as the game reports them (nothing here is estimated). With the game's date known
 * the time left is shown and coloured; without it only the end month is, in a quiet colour.
 */
@Composable
internal fun ContractInfoRows(player: Player, referenceDate: String?) {
    val contractChip = ContractTime.contractChip(player, referenceDate)
    val contractEndText = ContractTime.monthYear(player.contractEnd)
    val loanChip = ContractTime.loanChip(player, referenceDate)
    val onLoan = player.loanDirection != null
    if (contractEndText.isEmpty() && !onLoan) return

    Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (contractEndText.isNotEmpty()) {
            if (contractChip != null) {
                InfoPill(
                    "Contract ${contractChip.text} · $contractEndText",
                    urgencyColor(contractChip.urgency),
                    warn = contractChip.urgency != Urgency.OK
                )
            } else {
                InfoPill("Contract to $contractEndText", MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (onLoan) {
            val club = player.loanClub.orEmpty().ifBlank { "another club" }
            val direction = if (player.loanDirection == "out") "Loan to" else "Loan from"
            val endText = ContractTime.monthYear(player.loanEnd)
            val label = buildString {
                append("$direction $club")
                if (loanChip != null) append(" · ${loanChip.text}")
                else if (endText.isNotEmpty()) append(" · to $endText")
                if (player.loanOptionToBuy) append(" · option to buy")
            }
            InfoPill(
                label,
                if (loanChip != null) urgencyColor(loanChip.urgency) else MaterialTheme.colorScheme.onSurfaceVariant,
                warn = loanChip != null && loanChip.urgency != Urgency.OK
            )
        }
    }
}

@Composable
private fun GameLine(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

/** "FROM THE GAME": the values the capture script reads from the career, shown read-only in the player's dialog. */
@Composable
internal fun FromTheGameSection(player: Player, referenceDate: String?) {
    val contractChip = ContractTime.contractChip(player, referenceDate)
    val contractDate = ContractTime.fullDate(player.contractEnd)
    val onLoan = player.loanDirection != null
    if (contractDate.isEmpty() && !onLoan) return
    val loanChip = ContractTime.loanChip(player, referenceDate)
    val loanDate = ContractTime.fullDate(player.loanEnd)
    val quiet = MaterialTheme.colorScheme.onBackground

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "FROM THE GAME",
            fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.primary
        )
        if (contractDate.isNotEmpty()) {
            GameLine(
                "Contract ends",
                contractDate + (contractChip?.let { " (${leftText(it)})" } ?: ""),
                if (contractChip != null && contractChip.urgency != Urgency.OK) urgencyColor(contractChip.urgency) else quiet
            )
        }
        if (onLoan) {
            val club = player.loanClub.orEmpty().ifBlank { "another club" }
            GameLine(if (player.loanDirection == "out") "On loan to" else "On loan from", club, quiet)
            if (loanDate.isNotEmpty()) {
                GameLine(
                    "Loan ends",
                    loanDate + (loanChip?.let { " (${leftText(it)})" } ?: ""),
                    if (loanChip != null && loanChip.urgency != Urgency.OK) urgencyColor(loanChip.urgency) else quiet
                )
            }
            if (player.loanOptionToBuy) GameLine("Option to buy", "Yes", quiet)
        }
        Text(
            "Read from your career by the capture script and kept up to date by it, so it is not editable here.",
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
