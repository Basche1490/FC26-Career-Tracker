package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import coil.compose.AsyncImage
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fctracker.career.AppState
import com.fctracker.career.MainViewModel
import com.fctracker.career.MatchResult
import com.fctracker.career.PlayerPerformance
import com.fctracker.career.isPlaceholderPlayerName
import com.fctracker.career.insights.MatchInsights
import com.fctracker.career.insights.ResultFilter
import com.fctracker.career.ui.theme.StatusWarn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.fctracker.career.ui.theme.DrawColor
import com.fctracker.career.ui.theme.LossColor
import com.fctracker.career.ui.theme.WinColor
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** The scorer's name for the match list: the squad's current name, else the name the game gave, never a raw id. */
internal fun goalScorerLabel(g: com.fctracker.career.GoalDetail, match: MatchResult, state: AppState?): String {
    if (g.scorerId == null) {   // a goal for the other side; "Player" and "<club> Goal" are no names
        return g.scorerName?.takeIf { !isPlaceholderPlayerName(it) && !it.endsWith(" Goal") }
            ?.let { "$it (${match.opponentName})" } ?: match.opponentName
    }
    state?.players?.find { it.id == g.scorerId }?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
    g.scorerName?.takeIf { !isPlaceholderPlayerName(it) }?.let { return it }
    match.playerPerformances.find { it.playerId == g.scorerId }?.playerName?.takeIf { !isPlaceholderPlayerName(it) }?.let { return it }
    return if (looksLikeId(g.scorerId) || isPlaceholderPlayerName(g.scorerId)) "Unknown scorer" else g.scorerId
}

internal fun goalAssisterLabel(g: com.fctracker.career.GoalDetail, state: AppState?): String? {
    val id = g.assisterId?.takeIf { it.isNotBlank() } ?: return null
    state?.players?.find { it.id == id }?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
    g.assisterName?.takeIf { !isPlaceholderPlayerName(it) }?.let { return it }
    return if (looksLikeId(id) || isPlaceholderPlayerName(id)) null else id
}

/**
 * The name for a line-up row: the squad's current name, else the name the game gave, else the name the match itself
 * mentions for him (as a scorer, assister or substitute). A raw game id is never shown.
 */
internal fun lineupNameOf(perf: PlayerPerformance, players: List<com.fctracker.career.Player>, match: MatchResult?): String {
    players.firstOrNull { it.id == perf.playerId }?.displayName?.takeIf { it.isNotBlank() }?.let { return it }
    perf.playerName?.takeIf { !isPlaceholderPlayerName(it) }?.let { return it }
    if (match != null) {
        match.goalDetails.firstOrNull { it.scorerId == perf.playerId }?.scorerName
            ?.takeIf { !isPlaceholderPlayerName(it) }?.let { return it }
        match.goalDetails.firstOrNull { it.assisterId == perf.playerId }?.assisterName
            ?.takeIf { !isPlaceholderPlayerName(it) }?.let { return it }
        for (sub in match.substitutions) {
            if (sub.playerInId == perf.playerId) sub.playerInName?.takeIf { !isPlaceholderPlayerName(it) }?.let { return it }
            if (sub.playerOutId == perf.playerId) sub.playerOutName?.takeIf { !isPlaceholderPlayerName(it) }?.let { return it }
        }
    }
    return if (isPlaceholderPlayerName(perf.playerId)) "Unknown player" else perf.playerId
}

/** " 23'" for a real minute; nothing for a minute the script only guessed. */
internal fun goalMinuteLabel(g: com.fctracker.career.GoalDetail): String =
    if (g.minute != null && !g.minuteEstimated) " ${g.minute}'" else ""

/** Game ids are numbers and the app's own ids are UUIDs: neither is something to show as a name. */
private fun looksLikeId(text: String): Boolean =
    text.isNotEmpty() && (text.all { it.isDigit() } || Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(text))

fun getCompetitionsForCountry(country: String): List<String> {
    return when(country) {
        "England" -> listOf("Premier League", "Championship", "FA Cup", "Carabao Cup", "Champions League", "Europa League", "Conference League", "Community Shield", "Friendly")
        "Spain" -> listOf("La Liga", "Segunda División", "Copa del Rey", "Supercopa de España", "Champions League", "Europa League", "Conference League", "Friendly")
        "Italy" -> listOf("Serie A", "Serie B", "Coppa Italia", "Supercoppa Italiana", "Champions League", "Europa League", "Conference League", "Friendly")
        "Germany" -> listOf("Bundesliga", "2. Bundesliga", "DFB-Pokal", "DFL-Supercup", "Champions League", "Europa League", "Conference League", "Friendly")
        "France" -> listOf("Ligue 1", "Ligue 2", "Coupe de France", "Trophée des Champions", "Champions League", "Europa League", "Conference League", "Friendly")
        else -> listOf("League", "Domestic Cup", "Continental", "Friendly")
    }
}

val clubDomains = mapOf(
        // ENGLAND - PREMIER LEAGUE & CHAMPIONSHIP
        "Arsenal" to "arsenal.com",
        "Aston Villa" to "avfc.co.uk",
        "Bournemouth" to "afcb.co.uk",
        "AFC Bournemouth" to "afcb.co.uk",
        "Brentford" to "brentfordfc.com",
        "Brighton" to "brightonandhovealbion.com",
        "Brighton & Hove Albion" to "brightonandhovealbion.com",
        "Burnley" to "burnleyfootballclub.com",
        "Chelsea" to "chelseafc.com",
        "Crystal Palace" to "cpfc.co.uk",
        "Everton" to "evertonfc.com",
        "Fulham" to "fulhamfc.com",
        "Ipswich Town" to "itfc.co.uk",
        "Liverpool" to "liverpoolfc.com",
        "Luton Town" to "lutontown.co.uk",
        "Manchester City" to "mancity.com",
        "Manchester United" to "manutd.com",
        "Newcastle United" to "nufc.co.uk",
        "Nottingham Forest" to "nottinghamforest.co.uk",
        "Sheffield United" to "sufc.co.uk",
        "Tottenham Hotspur" to "tottenhamhotspur.com",
        "West Ham" to "whufc.com",
        "West Ham United" to "whufc.com",
        "Wolverhampton" to "wolves.co.uk",
        "Wolverhampton Wanderers" to "wolves.co.uk",
        "Leeds United" to "leedsunited.com",
        "Leicester City" to "lcfc.com",
        "Southampton" to "southamptonfc.com",
        "Blackburn Rovers" to "rovers.co.uk",
        "Bristol City" to "bcfc.co.uk",
        "Cardiff City" to "cardiffcityfc.co.uk",
        "Coventry City" to "ccfc.co.uk",
        "Derby County" to "dcfc.co.uk",
        "Hull City" to "wearehullcity.co.uk",
        "Middlesbrough" to "mfc.co.uk",
        "Millwall" to "millwallfc.co.uk",
        "Norwich City" to "canaries.co.uk",
        "Oxford United" to "oufc.co.uk",
        "Plymouth Argyle" to "pafc.co.uk",
        "Portsmouth" to "portsmouthfc.co.uk",
        "Preston North End" to "pnefc.net",
        "Queens Park Rangers" to "qpr.co.uk",
        "QPR" to "qpr.co.uk",
        "Sheffield Wednesday" to "swfc.co.uk",
        "Stoke City" to "stokecityfc.com",
        "Sunderland" to "safc.com",
        "Swansea City" to "swanseacity.com",
        "Watford" to "watfordfc.com",
        "West Bromwich Albion" to "wba.co.uk",
        // ENGLAND - EFL LEAGUE ONE
        "Barnsley" to "barnsleyfc.co.uk",
        "Birmingham City" to "bcfc.com",
        "Blackpool" to "blackpoolfc.co.uk",
        "Bolton Wanderers" to "bwfc.co.uk",
        "Bristol Rovers" to "bristolrovers.co.uk",
        "Burton Albion" to "burtonalbionfc.co.uk",
        "Cambridge United" to "cambridgeunited.com",
        "Charlton Athletic" to "charltonafc.com",
        "Crawley Town" to "crawleytownfc.com",
        "Exeter City" to "exetercityfc.co.uk",
        "Huddersfield Town" to "htafc.com",
        "Leyton Orient" to "leytonorient.com",
        "Lincoln City" to "weareimps.com",
        "Mansfield Town" to "mansfieldtown.net",
        "Northampton Town" to "ntfc.co.uk",
        "Peterborough United" to "theposh.com",
        "Reading" to "readingfc.co.uk",
        "Rotherham United" to "themillers.co.uk",
        "Shrewsbury Town" to "shrewsburytown.com",
        "Stevenage" to "stevenagefc.com",
        "Stockport County" to "stockportcounty.com",
        "Wigan Athletic" to "wiganathletic.com",
        "Wrexham" to "wrexhamafc.co.uk",
        "Wrexham AFC" to "wrexhamafc.co.uk",
        "Wycombe Wanderers" to "wwfc.com",
        // ENGLAND - EFL LEAGUE TWO & NATIONAL LEAGUE
        "Accrington Stanley" to "accringtonstanley.co.uk",
        "AFC Wimbledon" to "afcwimbledon.co.uk",
        "Barrow" to "barrowafc.com",
        "Barrow AFC" to "barrowafc.com",
        "Bradford City" to "bradfordcityafc.com",
        "Bromley" to "bromleyfc.co.uk",
        "Carlisle United" to "carlisleunited.co.uk",
        "Cheltenham Town" to "ctfc.com",
        "Chesterfield" to "chesterfield-fc.co.uk",
        "Colchester United" to "cu-fc.com",
        "Crewe Alexandra" to "crewealex.net",
        "Doncaster Rovers" to "doncasterroversfc.co.uk",
        "Fleetwood Town" to "fleetwoodtownfc.com",
        "Gillingham" to "gillinghamfootballclub.com",
        "Grimsby Town" to "grimsby-townfc.co.uk",
        "Harrogate Town" to "harrogatetownafc.com",
        "Milton Keynes Dons" to "mkdons.com",
        "MK Dons" to "mkdons.com",
        "Morecambe" to "morecambefc.com",
        "Newport County" to "newport-county.co.uk",
        "Notts County" to "nottscountyfc.co.uk",
        "Port Vale" to "port-vale.co.uk",
        "Salford City" to "salfordcityfc.co.uk",
        "Swindon Town" to "swindontownfc.co.uk",
        "Tranmere Rovers" to "tranmererovers.co.uk",
        "Walsall" to "saddlers.co.uk",
        "Torquay United" to "torquayunited.com",
        "Torquay AFC" to "torquayunited.com",

        // SPAIN
        "Real Madrid" to "realmadrid.com",
        "Barcelona" to "fcbarcelona.com",
        "Atletico Madrid" to "atleticodemadrid.com",
        "Sevilla" to "sevillafc.es",
        "Real Sociedad" to "realsociedad.eus",
        "Athletic Club" to "athletic-club.eus",
        "Real Betis" to "realbetisbalompie.es",
        "Valencia" to "valenciacf.com",
        "Villarreal" to "villarrealcf.es",
        "Girona" to "gironafc.cat",

        // GERMANY
        "Bayern Munich" to "fcbayern.com",
        "Borussia Dortmund" to "bvb.de",
        "Bayer Leverkusen" to "bayer04.de",
        "RB Leipzig" to "rbleipzig.com",
        "Eintracht Frankfurt" to "eintracht.de",
        "Stuttgart" to "vfb.de",
        "Monchengladbach" to "borussia.de",
        "Wolfsburg" to "vfl-wolfsburg.de",

        // ITALY
        "Juventus" to "juventus.com",
        "AC Milan" to "acmilan.com",
        "Inter Milan" to "inter.it",
        "Napoli" to "sscnapoli.it",
        "Roma" to "asroma.com",
        "Lazio" to "sslazio.it",
        "Atalanta" to "atalanta.it",
        "Fiorentina" to "acffiorentina.com",
        "Bologna" to "bolognafc.it",

        // FRANCE
        "PSG" to "psg.fr",
        "Monaco" to "asmonaco.com",
        "Marseille" to "om.fr",
        "Lyon" to "ol.fr",
        "Lille" to "losc.fr",
        "Lens" to "rclens.fr",
        "Nice" to "ognice.com",
        "Rennes" to "staderennais.com",

        // SAUDI
        "Al Hilal" to "alhilal.com",
        "Al Nassr" to "alnassr.sa",
        "Al Ittihad" to "ittihadclub.sa",
        "Al Ahli" to "alahlifc.sa",
        "Al Ettifaq" to "ettifaq.com",

        // AMERICA
        "Inter Miami" to "intermiamicf.com",
        "LA Galaxy" to "lagalaxy.com",
        "LAFC" to "lafc.com",
        "Seattle Sounders" to "soundersfc.com",
        "Atlanta United" to "atlutd.com",
        "New York City FC" to "nycfc.com",
        "Philadelphia Union" to "philadelphiaunion.com",
        
        // PORTUGAL
        "Benfica" to "slbenfica.pt",
        "Porto" to "fcporto.pt",
        "Sporting CP" to "sporting.pt",
        "Braga" to "scbraga.pt",
        
        // NETHERLANDS
        "Ajax" to "ajax.nl",
        "PSV" to "psv.nl",
        "Feyenoord" to "feyenoord.nl",
        "AZ Alkmaar" to "az.nl"
    )

val highResClubCrests = mapOf(
    "Arsenal" to "https://cdn.futbin.com/content/fifa24/img/clubs/1.png",
    "Aston Villa" to "https://cdn.futbin.com/content/fifa24/img/clubs/2.png",
    "Bournemouth" to "https://cdn.futbin.com/content/fifa24/img/clubs/1943.png",
    "AFC Bournemouth" to "https://cdn.futbin.com/content/fifa24/img/clubs/1943.png",
    "Brentford" to "https://cdn.futbin.com/content/fifa24/img/clubs/1925.png",
    "Brighton" to "https://cdn.futbin.com/content/fifa24/img/clubs/1808.png",
    "Brighton & Hove Albion" to "https://cdn.futbin.com/content/fifa24/img/clubs/1808.png",
    "Burnley" to "https://cdn.futbin.com/content/fifa24/img/clubs/1796.png",
    "Chelsea" to "https://cdn.futbin.com/content/fifa24/img/clubs/5.png",
    "Crystal Palace" to "https://cdn.futbin.com/content/fifa24/img/clubs/1799.png",
    "Everton" to "https://cdn.futbin.com/content/fifa24/img/clubs/7.png",
    "Fulham" to "https://cdn.futbin.com/content/fifa24/img/clubs/144.png",
    "Ipswich Town" to "https://cdn.futbin.com/content/fifa24/img/clubs/94.png",
    "Ipswich" to "https://cdn.futbin.com/content/fifa24/img/clubs/94.png",
    "Liverpool" to "https://cdn.futbin.com/content/fifa24/img/clubs/9.png",
    "Luton Town" to "https://cdn.futbin.com/content/fifa24/img/clubs/1923.png",
    "Luton" to "https://cdn.futbin.com/content/fifa24/img/clubs/1923.png",
    "Manchester City" to "https://cdn.futbin.com/content/fifa24/img/clubs/10.png",
    "Man City" to "https://cdn.futbin.com/content/fifa24/img/clubs/10.png",
    "Manchester United" to "https://cdn.futbin.com/content/fifa24/img/clubs/11.png",
    "Man United" to "https://cdn.futbin.com/content/fifa24/img/clubs/11.png",
    "Man Utd" to "https://cdn.futbin.com/content/fifa24/img/clubs/11.png",
    "Newcastle United" to "https://cdn.futbin.com/content/fifa24/img/clubs/13.png",
    "Newcastle" to "https://cdn.futbin.com/content/fifa24/img/clubs/13.png",
    "Nottingham Forest" to "https://cdn.futbin.com/content/fifa24/img/clubs/14.png",
    "Sheffield United" to "https://cdn.futbin.com/content/fifa24/img/clubs/1794.png",
    "Tottenham Hotspur" to "https://cdn.futbin.com/content/fifa24/img/clubs/18.png",
    "Tottenham" to "https://cdn.futbin.com/content/fifa24/img/clubs/18.png",
    "Spurs" to "https://cdn.futbin.com/content/fifa24/img/clubs/18.png",
    "West Ham" to "https://cdn.futbin.com/content/fifa24/img/clubs/19.png",
    "West Ham United" to "https://cdn.futbin.com/content/fifa24/img/clubs/19.png",
    "Wolverhampton" to "https://cdn.futbin.com/content/fifa24/img/clubs/110.png",
    "Wolverhampton Wanderers" to "https://cdn.futbin.com/content/fifa24/img/clubs/110.png",
    "Wolves" to "https://cdn.futbin.com/content/fifa24/img/clubs/110.png",
    "Leeds United" to "https://cdn.futbin.com/content/fifa24/img/clubs/8.png",
    "Leeds" to "https://cdn.futbin.com/content/fifa24/img/clubs/8.png",
    "Leicester City" to "https://cdn.futbin.com/content/fifa24/img/clubs/95.png",
    "Leicester" to "https://cdn.futbin.com/content/fifa24/img/clubs/95.png",
    "Southampton" to "https://cdn.futbin.com/content/fifa24/img/clubs/17.png",
    "Blackburn Rovers" to "https://cdn.futbin.com/content/fifa24/img/clubs/3.png",
    "Bristol City" to "https://cdn.futbin.com/content/fifa24/img/clubs/1919.png",
    "Cardiff City" to "https://cdn.futbin.com/content/fifa24/img/clubs/1961.png",
    "Coventry City" to "https://cdn.futbin.com/content/fifa24/img/clubs/1800.png",
    "Derby County" to "https://cdn.futbin.com/content/fifa24/img/clubs/91.png",
    "Hull City" to "https://cdn.futbin.com/content/fifa24/img/clubs/1952.png",
    "Middlesbrough" to "https://cdn.futbin.com/content/fifa24/img/clubs/12.png",
    "Millwall" to "https://cdn.futbin.com/content/fifa24/img/clubs/97.png",
    "Norwich City" to "https://cdn.futbin.com/content/fifa24/img/clubs/1792.png",
    "Oxford United" to "https://cdn.futbin.com/content/fifa24/img/clubs/1951.png",
    "Plymouth Argyle" to "https://cdn.futbin.com/content/fifa24/img/clubs/1929.png",
    "Portsmouth" to "https://cdn.futbin.com/content/fifa24/img/clubs/1790.png",
    "Preston North End" to "https://cdn.futbin.com/content/fifa24/img/clubs/1801.png",
    "Queens Park Rangers" to "https://cdn.futbin.com/content/fifa24/img/clubs/15.png",
    "QPR" to "https://cdn.futbin.com/content/fifa24/img/clubs/15.png",
    "Sheffield Wednesday" to "https://cdn.futbin.com/content/fifa24/img/clubs/1807.png",
    "Stoke City" to "https://cdn.futbin.com/content/fifa24/img/clubs/1806.png",
    "Sunderland" to "https://cdn.futbin.com/content/fifa24/img/clubs/106.png",
    "Swansea City" to "https://cdn.futbin.com/content/fifa24/img/clubs/1960.png",
    "Watford" to "https://cdn.futbin.com/content/fifa24/img/clubs/1795.png",
    "West Bromwich Albion" to "https://cdn.futbin.com/content/fifa24/img/clubs/109.png",
    "West Brom" to "https://cdn.futbin.com/content/fifa24/img/clubs/109.png",
    "Birmingham City" to "https://cdn.futbin.com/content/fifa24/img/clubs/88.png",
    "Blackpool" to "https://cdn.futbin.com/content/fifa24/img/clubs/1926.png",
    "Bolton Wanderers" to "https://cdn.futbin.com/content/fifa24/img/clubs/4.png",
    "Charlton Athletic" to "https://cdn.futbin.com/content/fifa24/img/clubs/89.png",
    "Reading" to "https://cdn.futbin.com/content/fifa24/img/clubs/1793.png",
    "Wigan Athletic" to "https://cdn.futbin.com/content/fifa24/img/clubs/1917.png",
    "Huddersfield Town" to "https://cdn.futbin.com/content/fifa24/img/clubs/1939.png",
    "Wrexham" to "https://cdn.futbin.com/content/fifa24/img/clubs/114251.png",
    "Real Madrid" to "https://cdn.futbin.com/content/fifa24/img/clubs/243.png",
    "Barcelona" to "https://cdn.futbin.com/content/fifa24/img/clubs/241.png",
    "FC Barcelona" to "https://cdn.futbin.com/content/fifa24/img/clubs/241.png",
    "Atletico Madrid" to "https://cdn.futbin.com/content/fifa24/img/clubs/240.png",
    "Atletico" to "https://cdn.futbin.com/content/fifa24/img/clubs/240.png",
    "Bayern Munich" to "https://cdn.futbin.com/content/fifa24/img/clubs/21.png",
    "Bayern" to "https://cdn.futbin.com/content/fifa24/img/clubs/21.png",
    "Borussia Dortmund" to "https://cdn.futbin.com/content/fifa24/img/clubs/22.png",
    "Dortmund" to "https://cdn.futbin.com/content/fifa24/img/clubs/22.png",
    "Bayer Leverkusen" to "https://cdn.futbin.com/content/fifa24/img/clubs/32.png",
    "Leverkusen" to "https://cdn.futbin.com/content/fifa24/img/clubs/32.png",
    "Paris Saint-Germain" to "https://cdn.futbin.com/content/fifa24/img/clubs/73.png",
    "PSG" to "https://cdn.futbin.com/content/fifa24/img/clubs/73.png",
    "Juventus" to "https://cdn.futbin.com/content/fifa24/img/clubs/45.png",
    "Inter Milan" to "https://cdn.futbin.com/content/fifa24/img/clubs/44.png",
    "Inter" to "https://cdn.futbin.com/content/fifa24/img/clubs/44.png",
    "AC Milan" to "https://cdn.futbin.com/content/fifa24/img/clubs/47.png",
    "Milan" to "https://cdn.futbin.com/content/fifa24/img/clubs/47.png",
    "Napoli" to "https://cdn.futbin.com/content/fifa24/img/clubs/48.png",
    "Roma" to "https://cdn.futbin.com/content/fifa24/img/clubs/52.png",
    "Lazio" to "https://cdn.futbin.com/content/fifa24/img/clubs/116.png",
    "Ajax" to "https://cdn.futbin.com/content/fifa24/img/clubs/245.png",
    "PSV" to "https://cdn.futbin.com/content/fifa24/img/clubs/247.png",
    "Feyenoord" to "https://cdn.futbin.com/content/fifa24/img/clubs/246.png",
    "Benfica" to "https://cdn.futbin.com/content/fifa24/img/clubs/234.png",
    "Porto" to "https://cdn.futbin.com/content/fifa24/img/clubs/236.png",
    "Sporting CP" to "https://cdn.futbin.com/content/fifa24/img/clubs/237.png",
    "Sporting" to "https://cdn.futbin.com/content/fifa24/img/clubs/237.png",
    "Al Nassr" to "https://cdn.futbin.com/content/fifa24/img/clubs/112139.png",
    "Al Hilal" to "https://cdn.futbin.com/content/fifa24/img/clubs/605.png",
    "Al Ittihad" to "https://cdn.futbin.com/content/fifa24/img/clubs/607.png",
    "Al Ahli" to "https://cdn.futbin.com/content/fifa24/img/clubs/112391.png",
    "Inter Miami" to "https://cdn.futbin.com/content/fifa24/img/clubs/112885.png",
    "LA Galaxy" to "https://cdn.futbin.com/content/fifa24/img/clubs/697.png",
    "LAFC" to "https://cdn.futbin.com/content/fifa24/img/clubs/114162.png"
)

fun getTeamCrestUrl(teamName: String): String {
    if (teamName.isBlank()) return ""
    val cleanName = teamName.trim()
    
    // 1. Direct high-res match
    val directHighRes = highResClubCrests.entries.firstOrNull { it.key.equals(cleanName, ignoreCase = true) }
    if (directHighRes != null) return directHighRes.value
    
    // 2. Partial high-res match
    val lower = cleanName.lowercase()
    val partialHighRes = highResClubCrests.entries.firstOrNull { 
        val k = it.key.lowercase()
        lower == k || lower.contains(k) || k.contains(lower)
    }
    if (partialHighRes != null) return partialHighRes.value

    // 3. Domain match with high-resolution 256px Google Favicon service
    val exactDomain = clubDomains.entries.firstOrNull { it.key.equals(cleanName, ignoreCase = true) }?.value
    val domain = exactDomain ?: clubDomains.entries.firstOrNull { 
        val k = it.key.lowercase()
        lower.contains(k) || k.contains(lower)
    }?.value

    if (domain != null) {
        return "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://${domain}&size=256"
    }

    // 4. Fuzzy fallback
    val t = lower.replace(" ", "").replace("&", "").replace("-", "")
    val fallbackDomain = when {
        t.contains("barrow") -> "barrowafc.com"
        t.contains("torquay") -> "torquayunited.com"
        t.contains("wrexham") -> "wrexhamafc.co.uk"
        t.contains("arsenal") -> "arsenal.com"
        t.contains("astonvilla") -> "avfc.co.uk"
        t.contains("brentford") -> "brentfordfc.com"
        t.contains("brighton") -> "brightonandhovealbion.com"
        t.contains("chelsea") -> "chelseafc.com"
        t.contains("liverpool") -> "liverpoolfc.com"
        t.contains("manchestercity") -> "mancity.com"
        t.contains("manchesterunited") -> "manutd.com"
        t.contains("newcastle") -> "nufc.co.uk"
        t.contains("tottenham") -> "tottenhamhotspur.com"
        t.contains("realmadrid") -> "realmadrid.com"
        t.contains("barcelona") -> "fcbarcelona.com"
        t.contains("atletico") -> "atleticodemadrid.com"
        t.contains("bayern") -> "fcbayern.com"
        t.contains("dortmund") -> "bvb.de"
        t.contains("juventus") -> "juventus.com"
        t.contains("acmilan") -> "acmilan.com"
        t.contains("intermilan") || t.contains("inter") -> "inter.it"
        t.contains("psg") || t.contains("paris") -> "psg.fr"
        t.contains("ajax") -> "ajax.nl"
        else -> return ""
    }
    return "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://${fallbackDomain}&size=256"
}


@Composable
fun TeamCrestImage(teamName: String, modifier: Modifier = Modifier) {
    val url = getTeamCrestUrl(teamName)
    val fallbackBox = @Composable {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val initials = teamName.split(" ").take(2).joinToString("") { it.take(1) }.uppercase()
            Text(if (initials.isNotEmpty()) initials else "?", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
    
    if (url.isNotEmpty()) {
        coil.compose.SubcomposeAsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(url)
                
                .crossfade(true)
                .build(),
            contentDescription = "$teamName Crest",
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = modifier,
            loading = { fallbackBox() },
            error = { fallbackBox() }
        )
    } else {
        fallbackBox()
    }
}

/** "Estimated stats": the short marker on a match whose detail the game did not send. A tap explains it. */
@Composable
internal fun EstimatedChip(onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(StatusWarn.copy(alpha = 0.14f))
            .border(1.dp, StatusWarn.copy(alpha = 0.5f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Info, contentDescription = null, tint = StatusWarn, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text("Estimated stats", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = StatusWarn)
    }
}


/**
 * One symbol under a lineup name: an icon, and a short word when it says more than the icon ("2" goals, "Off 77'").
 * [description] is what a screen reader says for it ("Yellow card").
 */
@Composable
internal fun LineupSymbol(text: String, color: Color, description: String? = null, icon: @Composable () -> Unit) {
    val m = if (description != null) Modifier.semantics(mergeDescendants = true) { contentDescription = description } else Modifier
    Row(m, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        icon()
        if (text.isNotBlank()) Text(text, fontSize = 11.sp, color = color)
    }
}

/** A count beside an icon only from two up: one goal is just the ball, two are the ball and "2". */
internal fun countText(n: Int) = if (n > 1) "$n" else ""
