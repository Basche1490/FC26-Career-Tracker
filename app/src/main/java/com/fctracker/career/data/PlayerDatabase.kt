package com.fctracker.career.data

import android.content.Context
import com.fctracker.career.EafcPlayer
import com.fctracker.career.Player
import org.json.JSONArray
import java.text.Normalizer

/**
 * The built-in EA FC player list (assets/players.json): search, squad import and "who is this signing?".
 * Loaded once, on first use, from any thread.
 */
class PlayerDatabase(private val loader: () -> String) {

    constructor(context: Context) : this({
        context.applicationContext.assets.open("players.json").bufferedReader().use { it.readText() }
    })

    val all: List<EafcPlayer> by lazy {
        try {
            parse(loader())
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * The database card for a signing, but only when the name matches exactly one player. A common name that
     * fits several players returns null so a wrong card is never attached.
     */
    fun signedPlayer(name: String, startYear: Int): Player? {
        val key = normalize(name)
        if (key.isBlank()) return null
        val matches = all.filter {
            normalize(it.name) == key || (it.commonName.isNotBlank() && normalize(it.commonName) == key)
        }
        return matches.singleOrNull()?.let { toPlayer(it, startYear) }
    }

    companion object {
        const val FIRST_ID = 1
        const val LAST_ID = 280000

        fun parse(json: String): List<EafcPlayer> {
            val array = JSONArray(json)
            val list = ArrayList<EafcPlayer>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    EafcPlayer(
                        id = obj.optInt("id", 0),
                        name = obj.optString("name", ""),
                        commonName = obj.optString("commonName", ""),
                        position = obj.optString("position", ""),
                        alternatePositions = obj.optString("alternatePositions", ""),
                        rating = obj.optInt("overallRating", 0),
                        potential = obj.optInt("potential", 0),
                        team = obj.optString("team", ""),
                        value = obj.optDouble("value", 0.0),
                        wage = obj.optInt("wage", 0),
                        birthdate = obj.optString("birthdate", ""),
                        height = obj.optInt("height", 180),
                        weight = obj.optInt("weight", 75),
                        preferredFoot = obj.optString("preferredFoot", "Right"),
                        weakFoot = obj.optInt("weakFoot", 3),
                        nationality = obj.optString("nationality", "")
                    )
                )
            }
            return list
        }

        fun headshotUrl(id: Int): String =
            if (id in FIRST_ID..LAST_ID) "https://cdn.futbin.com/content/fifa25/img/players/$id.png" else ""

        /** "6/15/1992 12:00:00 AM" -> "1992-06-15". Anything unreadable is returned as it was. */
        fun isoBirthDate(raw: String): String {
            val datePart = raw.trim().split(" ")[0]
            val parts = datePart.split("/")
            return if (parts.size == 3 && parts[2].length == 4) {
                "${parts[2]}-${parts[0].padStart(2, '0')}-${parts[1].padStart(2, '0')}"
            } else datePart
        }

        /** Age counted the way the app counts it everywhere: season year minus birth year. */
        fun ageIn(seasonYear: Int, isoBirthDate: String): Int {
            val birthYear = isoBirthDate.substringBefore("-").toIntOrNull() ?: return 25
            return seasonYear - birthYear
        }

        /** A squad player built from a database card. */
        fun toPlayer(e: EafcPlayer, seasonYear: Int): Player {
            val alt = e.alternatePositions.split(",")
            val iso = isoBirthDate(e.birthdate)
            return Player(
                name = e.name,
                nickname = e.commonName.takeIf { it.isNotBlank() },
                primaryPosition = e.position,
                secondaryPosition = alt.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() },
                tertiaryPosition = alt.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() },
                age = ageIn(seasonYear, iso),
                startingOverall = e.rating,
                overall = e.rating,
                potential = e.potential,
                valueM = e.value,
                wageK = e.wage,
                nationality = e.nationality,
                birthDate = iso,
                height = e.height,
                weight = e.weight,
                preferredFoot = e.preferredFoot,
                weakFoot = e.weakFoot,
                headshotUrl = headshotUrl(e.id)
            )
        }

        fun normalize(s: String): String {
            val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
            return decomposed.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase().trim()
        }
    }
}
