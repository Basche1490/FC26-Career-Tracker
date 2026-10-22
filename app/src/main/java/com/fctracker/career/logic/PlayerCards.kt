package com.fctracker.career.logic

import com.fctracker.career.EA_FC_NATION_MAP
import com.fctracker.career.EafcPlayer
import com.fctracker.career.Player
import org.json.JSONObject
import java.text.Normalizer

/**
 * Squad cards from the built-in player database, the game's own details for a card, and players' short names.
 * Plain Kotlin (no Android), so the rules are tested on the JVM.
 */
object PlayerCards {
    const val FIRST_ID = 1
    const val LAST_ID = 280000

    /** The birthday a card gets when nothing better is known (the Player default): such a card is a placeholder. */
    const val PLACEHOLDER_BIRTH_DATE = "1999-01-01"

    fun normalize(s: String): String {
        val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
        return decomposed.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase().trim()
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
            potential = if (e.potential > 0) e.potential else e.rating,   // the bundled list has no potential
            valueM = e.value,
            wageK = e.wage,
            nationality = e.nationality,
            birthDate = iso,
            height = e.height,
            weight = e.weight,
            preferredFoot = e.preferredFoot,
            weakFoot = e.weakFoot,
            headshotUrl = headshotUrl(e.id),
            gamePlayerId = e.id.takeIf { it > 0 }
        )
    }

    /** A card made without real details: the app's default birthday (or none). Its details may be replaced. */
    fun isPlaceholder(p: Player): Boolean = p.birthDate.isBlank() || p.birthDate == PLACEHOLDER_BIRTH_DATE

    private fun unknownNation(p: Player) = p.nationality.isBlank() || p.nationality.equals("Unknown", ignoreCase = true)

    /**
     * Puts details on a card. A placeholder card (or [force], for a card made a moment ago) takes all of them; any other
     * card only fills what it is missing (an unknown nation), so what the person typed and the database's details stay.
     * [nation] is the nation's name for [GameProfile.nation].
     */
    fun withProfile(p: Player, g: GameProfile, nation: String?, seasonYear: Int, force: Boolean = false): Player {
        var out = p
        if (force || isPlaceholder(p)) {
            if (g.positions.isNotEmpty()) {
                out = out.copy(primaryPosition = g.positions[0], secondaryPosition = g.positions.getOrNull(1), tertiaryPosition = g.positions.getOrNull(2))
            }
            g.birthDate?.let { out = out.copy(birthDate = it, age = ageIn(seasonYear, it)) }
            g.height?.let { out = out.copy(height = it) }
            g.weight?.let { out = out.copy(weight = it) }
            g.foot?.let { out = out.copy(preferredFoot = it) }
            g.weakFoot?.let { out = out.copy(weakFoot = it) }
            g.overall?.let { out = out.copy(overall = it, startingOverall = if (out.startingOverall <= 0 || isPlaceholder(p)) it else out.startingOverall) }
            g.potential?.let { out = out.copy(potential = it) }
        }
        // the nation only fills an unknown one: a database card already names it (better than the app's id list)
        if (unknownNation(out) && nation != null) {
            out = out.copy(nationality = nation, nationalityId = g.nation ?: out.nationalityId)
        }
        return out
    }

    /**
     * A placeholder card corrected from the database card of the same player: birthday, nation and the headshot. Its position is taken only while it is still the default (CM): a position the person set stays.
     */
    fun withDatabase(p: Player, db: Player): Player {
        if (!isPlaceholder(p) || isPlaceholder(db)) return p
        // the database has no height, weight or foot: those stay as they are
        var out = p.copy(
            birthDate = db.birthDate, age = db.age, nationality = if (unknownNation(p) && db.nationality.isNotBlank()) db.nationality else p.nationality,
            headshotUrl = p.headshotUrl.ifBlank { db.headshotUrl }, gamePlayerId = p.gamePlayerId ?: db.gamePlayerId
        )
        if (p.primaryPosition == Player().primaryPosition && p.secondaryPosition == null) {
            out = out.copy(primaryPosition = db.primaryPosition, secondaryPosition = db.secondaryPosition, tertiaryPosition = db.tertiaryPosition)
        }
        return out
    }
}

/** A player's details as the game keeps them, sent by the capture script with a signing and with the career report. */
data class GameProfile(
    val positions: List<String> = emptyList(),
    val birthDate: String? = null,
    val nation: Int? = null,
    val height: Int? = null,
    val weight: Int? = null,
    val foot: String? = null,
    val weakFoot: Int? = null,
    val overall: Int? = null,
    val potential: Int? = null
) {
    companion object {
        private val POSITIONS = setOf("GK", "CB", "LB", "RB", "LWB", "RWB", "CDM", "CM", "CAM", "LM", "RM", "LW", "RW", "CF", "ST")

        /** The "profile" object of a payload; null when it carries nothing usable. Impossible values are left out. */
        fun parse(o: JSONObject?): GameProfile? {
            if (o == null) return null
            val positions = o.optJSONArray("positions")?.let { a -> (0 until a.length()).map { a.optString(it).trim().uppercase() } }
                .orEmpty().filter { it in POSITIONS }.distinct()
            fun int(key: String, range: IntRange): Int? = if (o.has(key) && !o.isNull(key)) o.optInt(key, -1).takeIf { it in range } else null
            val birth = o.optString("birthDate").trim().takeIf { Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) }
            val foot = o.optString("foot").trim().takeIf { it == "Left" || it == "Right" }
            val g = GameProfile(
                positions = positions, birthDate = birth, nation = int("nation", 1..999), height = int("height", 140..220),
                weight = int("weight", 40..130), foot = foot, weakFoot = int("weakFoot", 1..5), overall = int("overall", 1..99),
                potential = int("potential", 1..99)
            )
            return if (g == GameProfile()) null else g
        }

        /** A nation's name from the game's id: what the career itself shows for that id first, then the app's list. */
        fun nationName(id: Int?, learned: Map<Int, String> = emptyMap()): String? =
            id?.let { learned[it] ?: EA_FC_NATION_MAP[it] }
    }
}

/** Finds players in the built-in database: by the game's id (real players share it), by name, and their short name. */
interface PlayerLookup {
    /** The database card of the player with this game id, or null (regens and youth are not in the database). */
    fun byGameId(id: Int, seasonYear: Int): Player?

    /** The database card for a name, only when it fits exactly one player (a shared name is never guessed). */
    fun byName(name: String, seasonYear: Int): Player?

    /** The name the game and the app show: "Pedri" for "Pedro González López"; the name itself when there is none shorter. */
    fun shortName(name: String): String

    companion object {
        val NONE: PlayerLookup = object : PlayerLookup {
            override fun byGameId(id: Int, seasonYear: Int): Player? = null
            override fun byName(name: String, seasonYear: Int): Player? = null
            override fun shortName(name: String): String = PlayerNames.short(name) { null } ?: name
        }
    }
}

/** [PlayerLookup] over the database's players. */
class PlayerIndex(private val all: List<EafcPlayer>) : PlayerLookup {
    private val byId by lazy { all.filter { it.id > 0 }.associateBy { it.id } }
    private val byFull by lazy { all.groupBy { PlayerCards.normalize(it.name) } }
    private val byCommon by lazy { all.filter { it.commonName.isNotBlank() }.groupBy { PlayerCards.normalize(it.commonName) } }

    override fun byGameId(id: Int, seasonYear: Int): Player? = byId[id]?.let { PlayerCards.toPlayer(it, seasonYear) }

    fun entryByName(name: String): EafcPlayer? {
        val key = PlayerCards.normalize(name)
        if (key.isEmpty()) return null
        return (byFull[key].orEmpty() + byCommon[key].orEmpty()).distinctBy { it.id }.singleOrNull()
    }

    override fun byName(name: String, seasonYear: Int): Player? = entryByName(name)?.let { PlayerCards.toPlayer(it, seasonYear) }

    /** The short (common) name for a full name, when every database player with that full name has the same one. */
    fun commonNameOf(fullName: String): String? =
        byFull[PlayerCards.normalize(fullName)]?.map { it.commonName.trim() }?.distinct()?.singleOrNull()?.takeIf { it.isNotBlank() }

    override fun shortName(name: String): String = PlayerNames.short(name, ::commonNameOf) ?: name
}

object PlayerNames {
    private val WITH_FULL = Regex("^(.+?)\\s*\\((.+)\\)$")

    private fun words(s: String) = PlayerCards.normalize(s).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    /**
     * The short name of a player: "Sergio Busquets" for "Sergio Busquets Burgos" (from the database's common names),
     * and for "Sergio Busquets (Sergio Busquets Burgos)", the way the app once stored a pick from the database.
     * [commonOf] gives the short name for a full name, or null. A name with nothing shorter comes back as it is.
     */
    fun short(name: String?, commonOf: (String) -> String?): String? {
        val n = name?.trim()?.takeIf { it.isNotEmpty() } ?: return name
        WITH_FULL.matchEntire(n)?.let { m ->
            val first = m.groupValues[1].trim()
            val inner = m.groupValues[2].trim()
            commonOf(inner)?.let { return it }
            commonOf(first)?.let { return it }
            // "Short (Full Name)": the part in brackets spells out the name in front of it
            val shortWords = words(first)
            if (shortWords.isNotEmpty() && words(inner).containsAll(shortWords)) return first
            return n
        }
        return commonOf(n)?.takeIf { !it.equals(n, ignoreCase = true) } ?: n
    }
}
