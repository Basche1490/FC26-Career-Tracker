package com.fctracker.career.ui.screens
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue


import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.fctracker.career.AppState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

@Composable
fun AppDrawer(
    state: AppState,
    currentRoute: String,
    navigateTo: (String) -> Unit
) {
    val clubDomains = mapOf(
        "Arsenal" to "arsenal.com",
        "Aston Villa" to "avfc.co.uk",
        "Chelsea" to "chelseafc.com",
        "Everton" to "evertonfc.com",
        "Liverpool" to "liverpoolfc.com",
        "Manchester City" to "mancity.com",
        "Manchester United" to "manutd.com",
        "Newcastle United" to "nufc.co.uk",
        "Tottenham Hotspur" to "tottenhamhotspur.com",
        "Real Madrid" to "realmadrid.com",
        "Barcelona" to "fcbarcelona.com",
        "Atletico Madrid" to "atleticodemadrid.com",
        "Bayern Munich" to "fcbayern.com",
        "Borussia Dortmund" to "bvb.de",
        "Bayer Leverkusen" to "bayer04.de",
        "Juventus" to "juventus.com",
        "AC Milan" to "acmilan.com",
        "Inter Milan" to "inter.it",
        "Napoli" to "sscnapoli.it",
        "Roma" to "asroma.com",
        "Paris Saint-Germain" to "psg.fr",
        "Marseille" to "om.fr",
        "Lyon" to "ol.fr",
        "Ajax" to "ajax.nl",
        "PSV" to "psv.nl",
        "Feyenoord" to "feyenoord.com",
        "Benfica" to "slbenfica.pt",
        "Porto" to "fcporto.pt",
        "Sporting CP" to "sporting.pt",
        "Al Nassr" to "alnassr.sa",
        "Al Hilal" to "alhilal.com",
        "Al Ittihad" to "ittihadclub.sa",
        "Inter Miami" to "intermiamicf.com",
        "LA Galaxy" to "lagalaxy.com"
    )
    val cleanClubName = state.clubName.replace(" ", "").lowercase()
    val domain = clubDomains[state.clubName] ?: "$cleanClubName.com"
    val fallbackCrestUrl = if (state.clubCrestUrl.isNotEmpty()) state.clubCrestUrl else "https://icon.horse/icon/$domain"

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp)
    ) {
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)) {
            coil.compose.SubcomposeAsyncImage(
                model = fallbackCrestUrl,
                contentDescription = "Club Crest",
                modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                error = {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(state.clubName.split(" ").mapNotNull { it.firstOrNull() }.joinToString("").take(2).uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                }
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "FC Tracker",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(16.dp))
        
        val items = listOf(
            DrawerItem("dashboard", "Dashboard", Icons.Default.Dashboard),
            DrawerItem("squad", "Squad & Roster", Icons.Default.Group),
            DrawerItem("academy", "Youth Academy", Icons.Default.School),
            DrawerItem("transfers", "Transfers", Icons.Default.SyncAlt),
            DrawerItem("matches", "Matches", Icons.Default.SportsSoccer),
            DrawerItem("season_summary", "Season Summary", Icons.Default.BarChart),
            DrawerItem("career_summary", "Career Summary", Icons.Default.History),
            DrawerItem("assistant", "AI Assistant", Icons.Default.SmartToy),
            DrawerItem("connection", "PC Connection", Icons.Default.Wifi),
            DrawerItem("settings", "Settings", Icons.Default.Settings)
        )

        items.forEach { item ->
            NavigationDrawerItem(
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = { navigateTo(item.route) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
            )
        }
    }
}

data class DrawerItem(val route: String, val title: String, val icon: ImageVector)
