package com.fctracker.career

import java.util.UUID

data class Note(val id: String = java.util.UUID.randomUUID().toString(), val title: String, val content: String)



data class EafcPlayer(
    val id: Int,
    val name: String,
    val commonName: String,
    val position: String,
    val alternatePositions: String,
    val rating: Int,
    val potential: Int,
    val team: String,
    val value: Double,
    val wage: Int,
    val birthdate: String,
    val height: Int,
    val weight: Int,
    val preferredFoot: String,
    val weakFoot: Int,
    val nationality: String
)

val EA_FC_NATION_MAP: Map<Int, String> = mapOf(
    1 to "Albania", 2 to "Algeria", 4 to "Andorra", 5 to "Angola", 6 to "Antigua and Barbuda",
    7 to "Argentina", 8 to "Armenia", 9 to "Australia", 10 to "Austria", 11 to "Azerbaijan",
    13 to "Bahrain", 14 to "England", 15 to "Barbados", 16 to "Belarus", 17 to "Belgium",
    18 to "France", 19 to "Bermuda", 20 to "Bolivia", 21 to "Germany", 22 to "Bosnia and Herzegovina",
    25 to "Bulgaria", 26 to "Burkina Faso", 27 to "Italy", 28 to "Cameroon", 29 to "Canada",
    30 to "Cape Verde", 32 to "Central African Republic", 33 to "Chad", 34 to "Netherlands",
    35 to "China PR", 36 to "Chinese Taipei", 37 to "Colombia", 38 to "Portugal", 39 to "Comoros",
    40 to "Congo", 41 to "DR Congo", 42 to "Scotland", 43 to "Costa Rica", 44 to "Ivory Coast",
    45 to "Spain", 46 to "Cuba", 47 to "Cyprus", 48 to "Czech Republic", 49 to "Denmark",
    51 to "Ecuador", 52 to "Argentina", 53 to "Egypt", 54 to "Brazil", 55 to "El Salvador",
    56 to "Equatorial Guinea", 57 to "Chile", 58 to "Colombia", 59 to "Estonia", 60 to "Croatia",
    61 to "Faroe Islands", 62 to "Fiji", 63 to "Finland", 64 to "France", 65 to "Gabon",
    66 to "Gambia", 67 to "Georgia", 68 to "Ghana", 69 to "Greece", 70 to "Grenada",
    71 to "Guatemala", 72 to "Guinea", 73 to "Guinea-Bissau", 74 to "Guyana", 75 to "Haiti",
    76 to "Honduras", 77 to "Hong Kong", 78 to "Hungary", 79 to "Iceland", 80 to "India",
    81 to "Indonesia", 82 to "Iran", 83 to "Iraq", 84 to "Israel", 86 to "Jamaica",
    87 to "Japan", 88 to "Jordan", 89 to "Kazakhstan", 90 to "Kenya", 91 to "Korea DPR",
    92 to "Korea Republic", 93 to "Kosovo", 94 to "Kuwait", 95 to "United States", 96 to "Latvia",
    97 to "Lebanon", 98 to "Lesotho", 99 to "Liberia", 100 to "Libya", 101 to "Liechtenstein",
    102 to "Lithuania", 103 to "Luxembourg", 105 to "Madagascar", 106 to "Malawi", 107 to "Malaysia",
    108 to "Maldives", 109 to "Mali", 110 to "Malta", 111 to "Mauritania", 112 to "Mauritius",
    113 to "Mexico", 114 to "Moldova", 115 to "Monaco", 116 to "Mongolia", 117 to "Montenegro",
    118 to "Montserrat", 119 to "Morocco", 120 to "Mozambique", 121 to "Namibia", 123 to "Nepal",
    125 to "New Zealand", 126 to "Nicaragua", 127 to "Niger", 128 to "Nigeria", 129 to "North Macedonia",
    130 to "Northern Ireland", 131 to "Norway", 132 to "Oman", 133 to "Pakistan", 134 to "Palestine",
    135 to "Panama", 136 to "Papua New Guinea", 137 to "Paraguay", 138 to "Peru", 139 to "Philippines",
    140 to "Poland", 141 to "Portugal", 142 to "Puerto Rico", 143 to "Qatar", 144 to "Republic of Ireland",
    145 to "Romania", 146 to "Russia", 147 to "Rwanda", 148 to "Saint Kitts and Nevis", 149 to "Saint Lucia",
    150 to "Saint Vincent and the Grenadines", 152 to "San Marino", 153 to "Sao Tome and Principe",
    154 to "Saudi Arabia", 155 to "Senegal", 156 to "Serbia", 157 to "Seychelles", 158 to "Sierra Leone",
    159 to "Singapore", 160 to "Slovakia", 161 to "Slovenia", 163 to "Somalia", 164 to "South Africa",
    165 to "South Sudan", 166 to "Sri Lanka", 167 to "Sudan", 168 to "Suriname", 169 to "Sweden",
    170 to "Switzerland", 171 to "Syria", 172 to "Tahiti", 173 to "Tajikistan", 174 to "Tanzania",
    175 to "Thailand", 176 to "Timor-Leste", 177 to "Togo", 178 to "Tonga", 179 to "Trinidad and Tobago",
    180 to "Tunisia", 181 to "Turkey", 182 to "Turkmenistan", 183 to "Uganda", 184 to "Ukraine",
    185 to "United Arab Emirates", 186 to "Uruguay", 187 to "Uzbekistan", 188 to "Vanuatu",
    189 to "Venezuela", 190 to "Vietnam", 191 to "Wales", 192 to "Yemen", 193 to "Zambia", 194 to "Zimbabwe"
)

val NATION_NAME_TO_ID_MAP: Map<String, Int> = EA_FC_NATION_MAP.entries.associate { (k, v) -> v.lowercase() to k }

data class Player(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val nickname: String? = null,
    @com.google.gson.annotations.SerializedName("displayName") val customDisplayName: String? = null,
    val number: Int = 0,
    val primaryPosition: String = "CM",
    val secondaryPosition: String? = null,
    val tertiaryPosition: String? = null,
    val altPosition: String? = null,
    val age: Int = 20,
    val startingOverall: Int = 0,
    val overall: Int = 70,
    val potential: Int = 75,
    val potentialRange: String? = null,
    val valueM: Double = 1.0,
    val wageK: Int = 5,
    val isAcademy: Boolean = false,
    val form: String = "Good",
    val morale: String = "Happy",
    val headshotUrl: String = "",
    val matchesPlayed: Int = 0,
    val goals: Int = 0,
    val assists: Int = 0,
    val averageRating: Double = 0.0,
    val isStartingXI: Boolean = false,
    val pitchX: Float = 0.5f,
    val pitchY: Float = 0.5f,
    val isInjured: Boolean = false,
    val injuryName: String? = null,
    val injuryStartDate: String? = null,
    val injuryReturnDate: String? = null,
    val nationality: String = "Unknown",
    val nationalityId: Int? = null,
    val birthDate: String = "1999-01-01",
    val height: Int = 180,
    val weight: Int = 75,
    val preferredFoot: String = "Right",
    val weakFoot: Int = 3,
    val isLoanedOut: Boolean = false,
    val isLoanedIn: Boolean = false,
    val isRegen: Boolean = false,
    /** For a regen: the real player he is based on (his picture comes from that player's card). Null when not set. */
    val regenOriginalName: String? = null,
    val isPromoted: Boolean = false,
    val promotedSeasonYear: Int? = null,
    val status: String = "Active",
    /**
     * What the game itself says (reported by the capture script on the PC, never guessed). Null until the first report
     * after the player was added. [contractEnd] is the last day of the contract as yyyy-MM-dd.
     */
    val contractEnd: String? = null,
    /** "in" = on loan to this club, "out" = on loan from this club; null when not on loan (or not reported yet). */
    val loanDirection: String? = null,
    /** The other club of the loan: where the player came from ("in") or went to ("out"). Blank when the game has none. */
    val loanClub: String? = null,
    /** The day the loan ends, yyyy-MM-dd; null when the game gave none. */
    val loanEnd: String? = null,
    val loanOptionToBuy: Boolean = false,
    /** The game's own id for this player, remembered from a squad report so a renamed player is still recognised. */
    val gamePlayerId: Int? = null,
    /** The squad role promised in the game: Crucial, Important, Rotation, Sporadic or Prospect. Null when not known. */
    val squadRole: String? = null,
    /** A contract bonus as the game describes it ("20 league goals"); null when none. */
    val bonus: String? = null,
    /** "game" when the injury came from the game, "manual" when the person added it. */
    val injurySource: String? = null,
    /**
     * True: the injury's return date follows the game whenever the game changes it. False: the person changed it and
     * their date stays until they turn this back on.
     */
    val injuryFollowGame: Boolean = true,
    /** The return date the game gave (kept so the edit dialog can say "The game said about 31 Oct"). */
    val injuryGameReturnDate: String? = null,
    val injuryNote: String? = null,
    /** Matches still to miss through suspension (0 = not suspended) and the day it ends, from the game. */
    val suspendedGames: Int = 0,
    val suspensionEnd: String? = null,
    /** The overall rating at the end of each finished season. The current season is [overall]. */
    val ratingHistory: List<SeasonRating> = emptyList(),
    /** Today's attributes from the game (finishing, positioning...), and a copy from the start of the season. */
    val attributes: Map<String, Int> = emptyMap(),
    val seasonStartAttributes: Map<String, Int> = emptyMap()
) {
    val displayName: String get() = when {
        !customDisplayName.isNullOrBlank() -> customDisplayName
        !nickname.isNullOrBlank() -> nickname
        else -> name
    }

    val formationDisplayName: String get() = when {
        !nickname.isNullOrBlank() -> nickname.trim()
        name.trim().contains(" ") -> name.trim().substringAfterLast(" ")
        else -> name.trim()
    }
}

data class Objective(
    val id: String = UUID.randomUUID().toString(),
    val category: String,
    val priority: String,
    val description: String,
    val status: String = "In Progress"
)

data class PlayerPerformance(
    val playerId: String,
    val rating: Double = 6.5,
    val goals: Int = 0,
    val goalAssisters: List<String?> = emptyList(),
    val assists: Int = 0,
    val passAccuracy: Int = 0,
    val passes: Int = 0,
    val shotAccuracy: Int = 0,
    val shots: Int = 0,
    val saves: Int = 0,
    val penaltySaves: Int = 0,
    val tackles: Int = 0,
    val tacklePercentage: Int = 0,
    val interceptions: Int = 0,
    val subInMinute: Int? = null,
    val subOutMinute: Int? = null,
    val yellowCard: Boolean = false,
    val redCard: Boolean = false,
    val cleanSheet: Boolean = false,
    val isMotm: Boolean = false,
    /**
     * The player's name as the game reported it. The match list shows it for a player who is not (or no longer)
     * in the squad, such as a loan signing. Nullable because matches saved by older versions do not have it.
     */
    val playerName: String? = null,
    /** The position he played in this match (ST, CAM, GK ...) when the game said so; the line-up is sorted by it. */
    val position: String? = null
)

/**
 * What the scripts write when the game gave no name ("Player", "Player 123456", "Unknown player", a bare id).
 * It is never something to show as a person's name.
 */
fun isPlaceholderPlayerName(name: String?): Boolean {
    val n = name?.trim().orEmpty()
    if (n.isEmpty()) return true
    return PLACEHOLDER_PLAYER_NAME.matches(n)
}

private val PLACEHOLDER_PLAYER_NAME = Regex("(?i)^((unknown )?player( ?#?\\d+)?|unknown|opponent( player)?|\\d+)$")

data class GoalDetail(
    val minute: Int? = null,
    val scorerId: String? = null,
    val assisterId: String? = null,
    /**
     * Names as the game reported them, so a scorer who is no longer in the squad (or never was, such as a loan
     * signing or an opposing player) is still shown by name. Nullable because older matches do not have them.
     */
    val scorerName: String? = null,
    val assisterName: String? = null,
    /** True when [minute] is only a guess (the game's export has no goal minutes): it is not shown as a fact. */
    val minuteEstimated: Boolean = false
)

data class Substitution(
    val minute: Int = 0,
    val playerOutId: String,
    val playerInId: String,
    /** Names as the game reported them (see [PlayerPerformance.playerName]). */
    val playerOutName: String? = null,
    val playerInName: String? = null
)

data class MatchResult(
    val id: String = UUID.randomUUID().toString(),
    val opponentName: String,
    val opponentCrestUrl: String = "",
    val isHome: Boolean,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val matchDate: String,
    val competition: String,
    val stage: String = "",
    val isExtraTime: Boolean = false,
    val homePenalties: Int = 0,
    val awayPenalties: Int = 0,
    /** Possession in percent; 0 means the game gave none (a guess is never stored). Matches saved earlier may hold one. */
    val possession: Int = 0,
    val passingAccuracy: Int = 80,
    val shotsFor: Int = 0,
    val shotsAgainst: Int = 0,
    val expectedGoals: Double = 0.0,
    val expectedGoalsAgainst: Double = 0.0,
    /** The league table position after the match. 0 = none: a cup match has no league position. */
    val leaguePosition: Int = 1,
    val motmName: String = "",
    /** True when the man of the match played for the other team (his name is in [motmName]). */
    val motmIsOpponent: Boolean = false,
    /** True when [possession] is a guess from the pass counts: the game's export has no possession figure. */
    val possessionEstimated: Boolean = false,
    /** True when [possession] was typed in by a person or given by the game itself (see [hasTrustedPossession]). */
    val possessionConfirmed: Boolean = false,
    val playerPerformances: List<PlayerPerformance> = emptyList(),
    val goalDetails: List<GoalDetail> = emptyList(),
    val substitutions: List<Substitution> = emptyList(),
    /**
     * True when the match arrived without full detail and the app filled the gaps with typical values
     * (line-up, ratings, passes, shots, xG, possession). The match list shows an "Estimated" tag for these.
     */
    val statsEstimated: Boolean = false
) {
    /**
     * Whether [possession] is a figure worth showing. The game's export has none, and an automatic match saved by an
     * earlier version may hold a pass-count guess without saying so (its id starts with "live-"); such a figure only
     * counts once it is confirmed. A match entered by hand, or one whose number was typed in later, is trusted.
     */
    val hasTrustedPossession: Boolean
        get() = possession > 0 && !possessionEstimated && !statsEstimated &&
            (possessionConfirmed || !id.startsWith("live-"))

    val resultBadge: String
        get() = when {
            goalsFor > goalsAgainst -> "W"
            goalsFor < goalsAgainst -> "L"
            else -> "D"
        }

}


data class FinancialBreakdown(
    val initialBudget: Double = 0.0,
    val finalTransferBudget: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val totalPlayerSales: Double = 0.0,
    val netSpend: Double = 0.0,
    val wageExpenditure: Double = 0.0
)

data class TeamTrophy(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val iconUrl: String = ""
)
data class ScoutReport(
    val id: String = java.util.UUID.randomUUID().toString(),
    val playerName: String,
    val position: String,
    val age: String,
    val ratingRange: String,
    val potentialRange: String,
    val value: String,
    val scoutName: String,
    val recommendation: String
)

data class PlayerAward(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val playerId: String = "",
    val monthYear: String = "",
    val iconUrl: String = ""
)

data class HistoricalSeason(
    val id: String = java.util.UUID.randomUUID().toString(),
    val year: Int = 2025,
    val leagueName: String = "",
    val finalPosition: Int = 1,
    val matches: List<MatchResult> = emptyList(),
    val players: List<Player> = emptyList(),
    val transfers: List<TransferLog> = emptyList(),
    val matchesPlayed: Int = 0,
    val points: Int = 0,
    val wins: Int = 0,
    val draws: Int = 0,
    val losses: Int = 0,
    val goalsFor: Int = 0,
    val goalsAgainst: Int = 0,
    /** The season's objectives as they stood when the season ended. Read-only once archived. */
    val objectives: List<Objective> = emptyList(),
    // Everything else the season pages show, kept so every page can show this season again (read-only).
    val academyPlayers: List<Player> = emptyList(),
    val leagueTables: List<LeagueTableData> = emptyList(),
    val leaders: List<CompetitionLeaders> = emptyList(),
    val squadStats: List<PlayerSeasonStat> = emptyList(),
    val awards: List<AwardEntry> = emptyList(),
    val coefficients: List<ClubCoefficient> = emptyList(),
    val nationCoefficients: List<NationCoefficient> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val transferBudget: Double = 0.0,
    val wageBudgetK: Double = 0.0,
    /** The trophies won this season (names), for the season list and the recap. */
    val trophies: List<String> = emptyList()
)

data class LeagueTable(
    val finalRank: String = "",
    val totalPoints: String = "",
    val wins: String = "",
    val draws: String = "",
    val losses: String = "",
    val goalsFor: String = "",
    val goalsAgainst: String = "",
    val goalDifference: String = ""
)


data class TransferLog(
    val id: String = java.util.UUID.randomUUID().toString(),
    val playerName: String = "",
    val type: String = "Transfer",
    val feeM: Double = 0.0,
    val fromToClub: String = "",
    val date: String = "",
    val includeSwap: Boolean = false,
    val isRegen: Boolean = false,
    val headshotUrl: String = "",
    val position: String = "CM",
    /** The game does not store most transfer fees: true when the fee is not known (shown as "Fee ?" until edited). */
    val feeUnknown: Boolean = false,
    /**
     * The other player of a player swap: the one who went the other way in the same deal. Null for deals without a
     * swap (and for everything saved by older versions).
     */
    val swapPlayerName: String? = null,
    /** For a regen: the real player he is based on (he lends the headshot). */
    val regenOriginalName: String? = null
)

/**
 * True for a player arriving (bought, loaned in, promoted, signed as a free agent), false for one leaving (sold,
 * loaned out, released, retired). The older check "type contains In" missed "Free Agent Sign".
 */
fun TransferLog.isIncomingDeal(): Boolean {
    val t = type.trim().lowercase()
    return t.startsWith("in") || t == "bought" || t == "free agent sign" || t.startsWith("promoted") || t == "loaned in"
}

/**
 * A deal that includes a player swap is two moves: the player in the deal and the player who went the other way.
 * This is the other move, as an entry of its own (the way the PC import records such deals): the swap player leaves
 * when the deal is an incoming one, and arrives when it is an outgoing one. Null when the deal has no swap, or when
 * [existing] already holds that move (so saving the same deal twice adds nothing).
 */
fun TransferLog.swapCounterpart(existing: List<TransferLog>): TransferLog? {
    val swap = swapPlayerName?.trim().orEmpty()
    if (!includeSwap || swap.isEmpty() || type == "Retirement") return null
    if (swap.equals(playerName.trim(), ignoreCase = true)) return null
    val incoming = isIncomingDeal()
    val alreadyThere = existing.any {
        it.playerName.trim().equals(swap, ignoreCase = true) && it.date == date &&
            it.isIncomingDeal() != incoming && it.fromToClub.trim().equals(fromToClub.trim(), ignoreCase = true)
    }
    if (alreadyThere) return null
    return TransferLog(
        playerName = swap,
        type = if (incoming) "Out (Permanent)" else "In (Permanent)",
        feeM = 0.0,
        fromToClub = fromToClub,
        date = date,
        position = "", // a blank position lets the player database supply the real one
        swapPlayerName = playerName
    )
}

/** "€26.0M", "Swap" (a player went the other way and no cash), "Free", or "Fee ?" when the fee is not known yet. */
fun TransferLog.feeLabel(currencySymbol: String): String = when {
    feeM > 0 -> "$currencySymbol${feeM}M"
    !swapPlayerName.isNullOrBlank() -> "Swap"
    feeUnknown -> "Fee ?"
    else -> "Free"
}

data class LiveEvent(val title: String, val message: String, val timestamp: Long)

data class AppState(
    val clubName: String = "Richmond FC",
    val managerName: String = "Ted Lasso",
    val leagueName: String = "Premier League",
    val activeFormation: String = "4-3-3",
    val squadValue: Double = 450.5,
    val previousSquadValue: Double = 410.0,
    val transferBudget: Double = 85.0,
    val players: List<Player> = emptyList(),
    val academyPlayers: List<Player> = emptyList(),
    val objectives: List<Objective> = emptyList(),
    val recentMatches: List<MatchResult> = emptyList(),
    val transfers: List<TransferLog> = emptyList(),
    val seasonRecord: String = "25-6-4",
    val activeInjuries: Int = 0,
    val teamTrophies: List<TeamTrophy> = emptyList(),
    val playerAwards: List<PlayerAward> = emptyList(),
    val isOnboarded: Boolean = false,
    val primaryColorHex: String = "#1E88E5",
    val secondaryColorHex: String = "#D81B60",
    val country: String = "England",
    val currencySymbol: String = "€",
    val clubCrestUrl: String = "",
    val isExistingTeam: Boolean = false,
    val startYear: Int = 2025,
    val aiAgent: String = "Google Gemini - Free",
    val aiApiUrl: String = "",
    /** Model name for the AI assistant. Blank = the built-in default for the chosen provider. */
    val aiModel: String = "",

    val financialBreakdown: FinancialBreakdown = FinancialBreakdown(),
    val leagueTable: LeagueTable = LeagueTable(),
    val historicalSeasons: List<HistoricalSeason> = emptyList(),
    val viewingSeasonYear: Int? = null,
    val scoutReports: List<ScoutReport> = emptyList(),
    val liveEvents: List<LiveEvent> = emptyList(),
    val telemetryServerUrl: String = "http://192.168.2.24:5050",
    val bridgeConnectionStatus: String = "Not Checked",
    val bridgeEventCount: Int = 0,
    /** The game's date (yyyy-MM-dd) as of the last squad report from the PC. Contract and loan time left is worked out against it. */
    val careerDate: String? = null,

    // -- added for the final design -------------------------------------------------------------------------------
    /** Where the person plays (see [Platform]). Console hides everything that needs the PC. */
    val platform: String = Platform.PC,
    /** The crest image the person uploaded, copied into the app's own storage (absolute path); blank when none. */
    val clubCrestFile: String = "",
    /** Club colours as the app's accent; off = the default mint. */
    val useClubColours: Boolean = true,
    val clubInfo: ClubInfo = ClubInfo(),
    val fixtures: List<Fixture> = emptyList(),
    /** Every league table the game reports (or the person keeps); the career's own league is among them. */
    val leagueTables: List<LeagueTableData> = emptyList(),
    val leaders: List<CompetitionLeaders> = emptyList(),
    val squadStats: List<PlayerSeasonStat> = emptyList(),
    val awards: List<AwardEntry> = emptyList(),
    /** Winners of every finished season, kept for all seasons (each entry names its season). */
    val worldWinners: List<WorldWinner> = emptyList(),
    val worldTopScorers: List<WorldTopScorer> = emptyList(),
    val coefficients: List<ClubCoefficient> = emptyList(),
    val nationCoefficients: List<NationCoefficient> = emptyList(),
    /** Console: the person's own European points per season ("2026" -> 12.5). */
    val ownCoefficientPoints: Map<String, Double> = emptyMap(),
    val playerNotes: List<PlayerNote> = emptyList(),
    val milestones: List<Milestone> = emptyList(),
    val milestonePrefs: MilestonePrefs = MilestonePrefs(),
    /** The weekly wage budget in thousands. The game keeps it out of reach, so the person types it; 0 = not set. */
    val wageBudgetK: Double = 0.0,
    /** Recap pages the person switched off (page numbers, 1 to 10). */
    val recapExcludedPages: List<Int> = emptyList(),
    /** Console: the season whose June reminder to fill in Around the World was dismissed. */
    val worldReminderDismissed: Int = 0,
    /** The starting XI and bench picked in the game (PC), for "Use my team sheet from the game". */
    val gameTeamSheet: TeamSheet? = null,
    /** True once the milestones already reached when the app first looked were stored quietly (no alert flood). */
    val milestonesPrimed: Boolean = false
)
