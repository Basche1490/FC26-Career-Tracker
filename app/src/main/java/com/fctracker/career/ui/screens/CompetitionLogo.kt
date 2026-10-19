package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/** Where a competition's logo can come from. */
sealed class LogoSource {
    /** The picture at the top of a Wikipedia article: for a league or cup that is its logo. */
    data class WikipediaPage(val title: String) : LogoSource()

    /** A picture link that is used as it is. */
    data class Url(val url: String) : LogoSource()
}

private fun favicon(domain: String) =
    "https://t3.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://$domain&size=256"

private fun page(title: String, siteForFallback: String? = null): List<LogoSource> =
    listOf<LogoSource>(LogoSource.WikipediaPage(title)) +
        (siteForFallback?.let { listOf(LogoSource.Url(favicon(it))) } ?: emptyList())

/** Names that say nothing about which competition it is (the app's own placeholders). */
private val GENERIC_COMPETITIONS = setOf("league", "cup", "domestic cup", "continental", "continental cup", "friendly", "friendlies")

/**
 * Where to look for a competition's logo, best first; the first source that loads is used. Every league and every
 * cup has a source of its own: the Carabao Cup, the EFL Championship, League One and League Two must never share
 * a picture. Empty when there is none (friendlies, placeholders) and the tile then shows the initials.
 *
 * The cup and EFL names are tested before "championship"/"champions": "EFL Championship" contains "champions"
 * and "Trophée des Champions" contains "champion".
 */
fun competitionLogoSources(competition: String): List<LogoSource> {
    val c = competition.trim().lowercase()
    if (c.isEmpty() || c in GENERIC_COMPETITIONS || c.contains("friendl")) return emptyList()
    return when {
        c.contains("carabao") || c.contains("efl cup") || c.contains("league cup") || c.contains("carling") -> page("EFL Cup")
        c.contains("efl trophy") || c.contains("papa john") || c.contains("vertu") -> page("EFL Trophy")
        c.contains("league one") -> page("EFL League One")
        c.contains("league two") -> page("EFL League Two")
        c.contains("community shield") -> page("FA Community Shield", "thefa.com")
        c.contains("fa cup") -> page("FA Cup", "thefa.com")
        c.contains("scottish") -> page("Scottish Premiership")
        c.contains("saudi") || c.contains("roshn") -> page("Saudi Pro League", "spl.com.sa")
        c.contains("premier league") || c == "premier" -> page("Premier League", "premierleague.com")
        c.contains("trophée des champions") || c.contains("trophee des champions") -> page("Trophée des Champions")
        c.contains("usl") -> page("USL Championship", "uslchampionship.com")
        c.contains("championship") && !c.contains("champions league") -> page("EFL Championship")
        c.contains("champions league") || c == "ucl" -> page("UEFA Champions League", "uefa.com")
        c.contains("europa league") || c == "uel" -> page("UEFA Europa League", "uefa.com")
        c.contains("conference") -> page("UEFA Conference League", "uefa.com")
        c.contains("copa del rey") -> page("Copa del Rey")
        c.contains("supercopa") -> page("Supercopa de España")
        c.contains("segunda") -> page("Segunda División")
        c.contains("la liga") || c.contains("laliga") || c.contains("primera div") -> page("La Liga", "laliga.com")
        c.contains("2. bundesliga") || c.contains("2.bundesliga") -> page("2. Bundesliga")
        c.contains("bundesliga") -> page("Bundesliga", "bundesliga.com")
        c.contains("dfb") && c.contains("super") -> page("DFL-Supercup")
        c.contains("dfl") -> page("DFL-Supercup")
        c.contains("dfb") -> page("DFB-Pokal", "dfb.de")
        c.contains("coppa italia") -> page("Coppa Italia")
        c.contains("supercoppa") -> page("Supercoppa Italiana")
        c.contains("serie a") -> page("Serie A", "legaseriea.it")
        c.contains("serie b") -> page("Serie B", "legab.it")
        c.contains("coupe de france") -> page("Coupe de France", "fff.fr")
        c.contains("ligue 1") -> page("Ligue 1", "ligue1.com")
        c.contains("ligue 2") -> page("Ligue 2")
        c.contains("mls") || c.contains("major league soccer") -> page("Major League Soccer", "mlssoccer.com")
        c.contains("eredivisie") -> page("Eredivisie", "eredivisie.eu")
        c.contains("liga portugal") || c.contains("liga nos") || c.contains("primeira liga") -> page("Primeira Liga", "ligaportugal.pt")
        c.contains("world cup") -> page("FIFA World Cup", "fifa.com")
        // Any other competition with a recognisable name: try the article of the same name.
        c.split(" ").size >= 2 && listOf("cup", "league", "liga", "pokal", "coppa", "coupe", "trophy", "shield", "super")
            .any { c.contains(it) } -> page(competition.trim())
        else -> emptyList()
    }
}

/** What worked for each competition in this run of the app, so a long list does not look things up again. */
private object CompetitionLogos {
    val chosen = ConcurrentHashMap<String, Int>()
    val aspect = ConcurrentHashMap<String, Float>()
    private val lead = ConcurrentHashMap<String, String>()

    /** The article's lead picture ("" remembered when the article has none), or null when the lookup failed. */
    suspend fun wikipediaLeadImage(title: String): String? {
        lead[title]?.let { return it.ifEmpty { null } }
        return withContext(Dispatchers.IO) {
            try {
                val encoded = URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20")
                val conn = URL("https://en.wikipedia.org/api/rest_v1/page/summary/$encoded").openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 6000
                    conn.readTimeout = 6000
                    conn.setRequestProperty("User-Agent", "FC26CareerTracker/1.0 (Android career tracker; competition logos)")
                    conn.setRequestProperty("Accept", "application/json")
                    when (conn.responseCode) {
                        200 -> {
                            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                            val url = JSONObject(body).optJSONObject("thumbnail")?.optString("source").orEmpty()
                            lead[title] = if (url.startsWith("http")) url else ""
                            url.takeIf { it.startsWith("http") }
                        }
                        404 -> { lead[title] = ""; null }
                        else -> null
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                null // offline: not remembered, so it is tried again next time
            }
        }
    }
}

/**
 * The competition's logo, shown at [height] and as wide as the logo's own proportions need (up to [maxWidth]),
 * never stretched into a square. While it is looked up the tile stays empty; only when no source has a picture are
 * the initials shown.
 */
@Composable
fun CompetitionLogoImage(
    competition: String,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    maxWidth: Dp = 64.dp,
    tile: Boolean = true
) {
    val sources = remember(competition) { competitionLogoSources(competition) }
    var index by remember(competition) { mutableStateOf(CompetitionLogos.chosen[competition] ?: 0) }
    var aspect by remember(competition) { mutableStateOf(CompetitionLogos.aspect[competition]) }
    var url by remember(competition, index) { mutableStateOf<String?>(null) }

    LaunchedEffect(competition, index) {
        val resolved = when (val source = sources.getOrNull(index)) {
            is LogoSource.Url -> source.url
            is LogoSource.WikipediaPage -> CompetitionLogos.wikipediaLeadImage(source.title)
            null -> null
        }
        if (resolved != null) {
            url = resolved
        } else if (index < sources.size) {
            index += 1
            CompetitionLogos.chosen[competition] = index
        }
    }

    val frame = modifier.height(height).widthIn(max = maxWidth).let { base ->
        val ratio = aspect
        if (ratio != null) base.aspectRatio(ratio, matchHeightConstraintsFirst = true) else base
    }
    val decorated = if (tile) frame.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(3.dp) else frame

    val shownUrl = url
    when {
        index >= sources.size -> {
            val initials = competition.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase()
            Box(
                modifier = modifier.size(height).clip(RoundedCornerShape(8.dp)).background(Color(0xFFECEFF1)),
                contentAlignment = Alignment.Center
            ) {
                Text(initials.ifEmpty { "?" }, color = Color(0xFF37474F), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        shownUrl == null -> Spacer(modifier = modifier.size(height))
        else -> coil.compose.SubcomposeAsyncImage(
            model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                .data(shownUrl)
                .crossfade(true)
                .build(),
            contentDescription = "$competition Logo",
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            modifier = decorated,
            loading = { Box(modifier = Modifier.fillMaxSize()) },
            onSuccess = { state ->
                CompetitionLogos.chosen[competition] = index
                val size = state.painter.intrinsicSize
                if (size.isSpecified && size.width > 0f && size.height > 0f) {
                    val ratio = (size.width / size.height).coerceIn(0.6f, 1.7f)
                    CompetitionLogos.aspect[competition] = ratio
                    aspect = ratio
                }
            },
            onError = {
                index += 1
                CompetitionLogos.chosen[competition] = index
            }
        )
    }
}
