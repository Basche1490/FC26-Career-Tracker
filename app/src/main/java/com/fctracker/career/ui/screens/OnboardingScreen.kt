package com.fctracker.career.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fctracker.career.MainViewModel
import com.fctracker.career.Platform
import com.fctracker.career.ui.components.FcButton
import com.fctracker.career.ui.components.FcCard
import com.fctracker.career.ui.components.FcField
import com.fctracker.career.ui.components.FcPage
import com.fctracker.career.ui.components.FcSelectField
import com.fctracker.career.ui.components.FcSwitchRow
import com.fctracker.career.ui.components.FcText
import com.fctracker.career.ui.components.FcTopBar
import com.fctracker.career.ui.components.FieldPair
import com.fctracker.career.ui.components.Muted
import com.fctracker.career.ui.components.NavIcon
import com.fctracker.career.ui.components.SectionHeader
import com.fctracker.career.ui.components.Segmented
import com.fctracker.career.ui.theme.FcTheme

/** First launch: where the person plays, then import a save or set the club up by hand. */
@Composable
fun OnboardingScreen(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    val c = FcTheme.colors
    var platform by remember { mutableStateOf(if (state.platform == Platform.CONSOLE) Platform.CONSOLE else Platform.PC) }
    var real by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var clubName by remember { mutableStateOf("") }
    var manager by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("England") }
    var league by remember { mutableStateOf("EFL Championship") }
    var year by remember { mutableStateOf("2025") }
    var currency by remember { mutableStateOf(ClubSetup.CURRENCIES.first()) }
    var crestUrl by remember { mutableStateOf("") }
    var ownCrestForReal by remember { mutableStateOf(false) }
    var primary by remember { mutableStateOf("#0B57D0") }
    var secondary by remember { mutableStateOf("#0E1A2B") }
    var useClub by remember { mutableStateOf(true) }
    var agent by remember { mutableStateOf(ClubSetup.AI_AGENTS.first()) }
    var apiUrl by remember { mutableStateOf(ClubSetup.defaultApiUrl(ClubSetup.AI_AGENTS.first())) }
    var key by remember { mutableStateOf("") }
    val clubs = remember { viewModel.getUniqueClubs() }
    val import = rememberCareerImport(viewModel) { viewModel.setPlatform(platform) }

    FcPage(header = { FcTopBar("Club setup", sub = "Welcome to FC Tracker", nav = NavIcon.NONE) }) {
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                SectionHeader("Where do you play FC 26?", icon = Icons.Default.SportsSoccer)
                Segmented(listOf("Console", "PC"), if (platform == Platform.CONSOLE) 0 else 1, { platform = if (it == 0) Platform.CONSOLE else Platform.PC })
                Muted("On PC the app can fill in matches, transfers, contracts and youth players by itself through FC Live Editor and the PC helper. On console you log everything yourself, and the PC settings are hidden. You can change this later in Settings.", 12.sp)
            }
        }
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 10.dp) {
                SectionHeader("Import career mode save", icon = Icons.Default.Upload)
                Muted("Fills in your setup, squad, trophies and past seasons from an exported save or an FC Tracker backup.", 12.sp)
                FcButton("Choose JSON file to import", import, icon = Icons.Default.Upload, full = true)
            }
        }
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                SectionHeader("Or set up by hand", icon = Icons.Default.Edit)
                FcSwitchRow("Manage an existing real club", real, { real = it },
                    sub = if (real) "Fills in the name, crest and colours from the club." else "Off: your own club (created in the game). Turn on to pick a real club.")
                if (real) {
                    FcField("Search real club", search, { search = it }, placeholder = "Leicester City", leadingIcon = Icons.Default.Search)
                    val hits = remember(search) { if (search.length < 2) emptyList() else clubs.filter { it.contains(search, true) }.take(6) }
                    hits.forEach { h ->
                        FcText(h, 14.sp, color = c.accent, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                            clubName = h; search = ""
                            ClubSetup.coloursOf(h)?.let { (p, s) -> primary = p; secondary = s }
                        }.padding(8.dp))
                    }
                }
                FcField("Club name", clubName, { clubName = it }, placeholder = if (real) "Pick the club above" else "Your Club")
                FcField("Manager name", manager, { manager = it })
                FieldPair(
                    { m -> FcSelectField("Country", country, ClubSetup.COUNTRIES, { country = it; league = ClubSetup.leaguesFor(it).firstOrNull() ?: "" }, m) },
                    { m -> val options = ClubSetup.leaguesFor(country, league); if (options.isEmpty()) FcField("League", league, { league = it }, m) else FcSelectField("League", league, options, { league = it }, m) }
                )
                FieldPair(
                    { m -> FcSelectField("Starting year", year, (2025..2040).map { "$it" }, { year = it }, m) },
                    { m -> FcSelectField("Currency", currency, ClubSetup.CURRENCIES, { currency = it }, m) }
                )
                if (real && !ownCrestForReal) RealCrestRow(viewModel, state, clubName, onUseOwn = { ownCrestForReal = true })
                else CrestBlock(viewModel, state, clubName.ifBlank { "Your Club" }, crestUrl, { crestUrl = it })
                ColourRows(primary, secondary, { p, s -> primary = p; secondary = s }, useClub, { useClub = it },
                    if (real) "Taken from the club you picked. Tap a colour, or More colours, to change them." else "Your club’s colours. Tap a colour, or More colours, to pick any colour.")
                Muted("Countries: England, Spain, Italy, Germany, France, Portugal, Netherlands, Saudi Arabia, America and Scotland; pick Other to type a league.", 11.sp)
            }
        }
        item {
            FcCard(Modifier.fillMaxWidth(), padding = 14.dp, gap = 12.dp) {
                SectionHeader("AI agent", icon = Icons.Default.SmartToy)
                FcSelectField("Agent", agent, ClubSetup.AI_AGENTS, { agent = it; apiUrl = ClubSetup.defaultApiUrl(it).ifBlank { apiUrl } })
                FcField("API URL", apiUrl, { apiUrl = it })
                FcField("API key", key, { key = it }, password = true, leadingIcon = Icons.Default.Lock, hint = "Optional now; add it later in Settings. Kept in encrypted storage on this phone, never in backups.")
            }
        }
        item {
            FcButton("Complete setup", {
                viewModel.setPlatform(platform)
                viewModel.setUseClubColours(useClub)
                viewModel.completeOnboarding(
                    manager.trim(), clubName.trim(), country, league.trim().ifBlank { "League" }, year.toIntOrNull() ?: 2025, ClubSetup.currencySymbol(currency),
                    primary, secondary, if (real && !ownCrestForReal) "" else crestUrl.trim(), real, agent, apiUrl.trim(), key.trim()
                )
            }, icon = Icons.Default.Check, full = true, enabled = clubName.isNotBlank() && manager.isNotBlank())
        }
        if (clubName.isBlank() || manager.isBlank()) item { Muted("Fill in the club name and your manager name to finish.", 12.sp) }
    }
}
