package com.fctracker.career.ai

import com.fctracker.career.AppState
import com.fctracker.career.logic.SeasonView
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The club's history: for a real club the opening of its Wikipedia article; for the person's own club a text they
 * write, or one the AI writes from a few answers (only those answers and the club's facts are sent, never the save).
 */
object ClubHistory {

    data class Wiki(val text: String, val url: String)

    private val http by lazy { OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build() }

    private fun get(url: String): JSONObject? = try {
        val req = Request.Builder().url(url).header("User-Agent", "FC-Tracker/2.0 (Android; career mode companion)").build()
        http.newCall(req).execute().use { r -> if (r.isSuccessful) JSONObject(r.body?.string().orEmpty()) else null }
    } catch (e: Exception) {
        null
    }

    /** Finds the club's article and returns its summary; null when offline or nothing fits. */
    fun fetchWikipedia(club: String): Wiki? {
        if (club.isBlank()) return null
        val search = "https://en.wikipedia.org/w/api.php".toHttpUrl().newBuilder()
            .addQueryParameter("action", "query").addQueryParameter("list", "search").addQueryParameter("format", "json")
            .addQueryParameter("srlimit", "3").addQueryParameter("srsearch", "$club football club").build().toString()
        val hits = get(search)?.optJSONObject("query")?.optJSONArray("search") ?: return null
        val titles = (0 until hits.length()).mapNotNull { hits.optJSONObject(it)?.optString("title") }.filter { it.isNotBlank() }
        val title = titles.firstOrNull { t -> t.contains("F.C.", true) || t.contains("FC", false) || t.contains("football", true) || t.contains("club", true) } ?: titles.firstOrNull() ?: return null
        val summaryUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/".toHttpUrl().newBuilder().addPathSegment(title.replace(' ', '_')).build().toString()
        val summary = get(summaryUrl) ?: return null
        val text = summary.optString("extract").trim()
        if (text.isEmpty()) return null
        val page = summary.optJSONObject("content_urls")?.optJSONObject("mobile")?.optString("page").orEmpty()
            .ifBlank { "https://en.m.wikipedia.org/wiki/" + title.replace(' ', '_') }
        return Wiki(text, page)
    }

    val ORIGINS = listOf("Founded by fans", "A works team", "Two clubs merged", "Reborn after going bust", "A wealthy owner")
    val TONES = listOf("Proud", "Humble", "Fun", "Epic")
    val LENGTHS = listOf("Short", "Medium", "Long")

    /** The rules and the request for the AI agent. Only the club's facts and the answers go into it. */
    fun aiPrompt(state: AppState, answers: Map<String, String>): Pair<String, String> {
        val ci = state.clubInfo
        val seasons = state.historicalSeasons.sortedBy { it.year }.joinToString("; ") { "${SeasonView.seasonLabel(it.year)}: ${it.finalPosition}${suffix(it.finalPosition)} in the ${it.leagueName}" }
        val facts = listOfNotNull(
            "Club name: ${state.clubName}",
            ci.founded.takeIf { it.isNotBlank() }?.let { "Founded: $it" },
            ci.stadium.takeIf { it.isNotBlank() }?.let { "Stadium: $it" },
            ci.rival.takeIf { it.isNotBlank() }?.let { "Rival: $it" },
            "Colours: ${ci.colours.ifBlank { "${state.primaryColorHex} and ${state.secondaryColorHex}" }}",
            "League: ${state.leagueName}, ${state.country}",
            "Manager: ${state.managerName}",
            seasons.takeIf { it.isNotBlank() }?.let { "Seasons in this career: $it" },
            state.teamTrophies.takeIf { it.isNotEmpty() }?.let { t -> "Trophies in this career: ${t.joinToString(", ") { it.name }}" }
        ).joinToString("\n")
        val length = when (answers["length"]) { "Short" -> "about 60 words"; "Long" -> "about 220 words"; else -> "about 120 words" }
        val system = """
You write the history of a fictional football club created in EA SPORTS FC 26 career mode, for the club's page in the FC Tracker app.
Write only the history text: no title, no lists, no notes. Use the facts and answers given; invent plausible local detail around them, but never contradict them.
Do not mention real people other than those named in the answers. Keep it suitable for everyone.
""".trim()
        val prompt = """
FACTS
$facts

ANSWERS
Where the club is from: ${answers["hometown"].orEmpty().ifBlank { "not given" }}
Nickname: ${answers["nickname"].orEmpty().ifBlank { "not given (suggest one)" }}
How the club started: ${answers["origin"].orEmpty().ifBlank { "not given" }}
Must include: ${answers["mustInclude"].orEmpty().ifBlank { "nothing in particular" }}
Tone: ${answers["tone"].orEmpty().ifBlank { "Proud" }}

Write the history in $length.
""".trim()
        return system to prompt
    }

    private fun suffix(n: Int) = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
}
