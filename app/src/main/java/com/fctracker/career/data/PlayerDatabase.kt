package com.fctracker.career.data

import android.content.Context
import com.fctracker.career.EafcPlayer
import com.fctracker.career.Player
import com.fctracker.career.logic.PlayerCards
import com.fctracker.career.logic.PlayerIndex
import org.json.JSONArray

/**
 * The built-in EA FC player list (assets/players.json): search, squad import and "who is this signing?".
 * Loaded once, on first use, from any thread.
 */
class PlayerDatabase(private val loader: () -> String) {

    constructor(context: Context) : this({
        context.applicationContext.assets.open("players.json").bufferedReader().use { it.readText() }
    })

    /** Look-ups by the game's id, by name, and players' short names (see [PlayerIndex]). */
    val index: PlayerIndex by lazy { PlayerIndex(all) }

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
        const val FIRST_ID = PlayerCards.FIRST_ID
        const val LAST_ID = PlayerCards.LAST_ID

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

        fun headshotUrl(id: Int): String = PlayerCards.headshotUrl(id)

        /** "6/15/1992 12:00:00 AM" -> "1992-06-15". Anything unreadable is returned as it was. */
        fun isoBirthDate(raw: String): String = PlayerCards.isoBirthDate(raw)

        /** Age counted the way the app counts it everywhere: season year minus birth year. */
        fun ageIn(seasonYear: Int, isoBirthDate: String): Int = PlayerCards.ageIn(seasonYear, isoBirthDate)

        /** A squad player built from a database card. */
        fun toPlayer(e: EafcPlayer, seasonYear: Int): Player = PlayerCards.toPlayer(e, seasonYear)

        fun normalize(s: String): String = PlayerCards.normalize(s)
    }
}
