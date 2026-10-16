package com.fctracker.career
import kotlinx.coroutines.isActive

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import android.content.Context
import android.content.SharedPreferences
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson

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
    val isPromoted: Boolean = false,
    val promotedSeasonYear: Int? = null,
    val status: String = "Active"
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
    val isMotm: Boolean = false
)

data class GoalDetail(
    val minute: Int? = null,
    val scorerId: String? = null,
    val assisterId: String? = null
)

data class Substitution(
    val minute: Int = 0,
    val playerOutId: String,
    val playerInId: String
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
    val possession: Int = 50,
    val passingAccuracy: Int = 80,
    val shotsFor: Int = 0,
    val shotsAgainst: Int = 0,
    val expectedGoals: Double = 0.0,
    val expectedGoalsAgainst: Double = 0.0,
    val leaguePosition: Int = 1,
    val motmName: String = "",
    val playerPerformances: List<PlayerPerformance> = emptyList(),
    val goalDetails: List<GoalDetail> = emptyList(),
    val substitutions: List<Substitution> = emptyList()
) {
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
    val goalsAgainst: Int = 0
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
    val position: String = "CM"
)

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
    val aiApiKey: String = "",
    val financialBreakdown: FinancialBreakdown = FinancialBreakdown(),
    val leagueTable: LeagueTable = LeagueTable(),
    val historicalSeasons: List<HistoricalSeason> = emptyList(),
    val viewingSeasonYear: Int? = null,
    val scoutReports: List<ScoutReport> = emptyList(),
    val liveEvents: List<LiveEvent> = emptyList(),
    val telemetryServerUrl: String = "http://192.168.2.24:5050",
    val bridgeConnectionStatus: String = "Not Checked",
    val bridgeEventCount: Int = 0
)

class MainViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    var savedNotes = androidx.compose.runtime.mutableStateListOf<Note>()
        private set

    private fun normalizeBridgeUrl(raw: String): String {
        var trimmed = raw.trim()
        if (trimmed.isEmpty()) return "http://192.168.2.24:5050"
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://$trimmed"
        }
        trimmed = trimmed.removeSuffix("/")
        if (trimmed.endsWith("/events")) trimmed = trimmed.removeSuffix("/events")
        if (trimmed.endsWith("/json")) trimmed = trimmed.removeSuffix("/json")
        if (trimmed.endsWith("/api/telemetry")) trimmed = trimmed.removeSuffix("/api/telemetry")
        
        // If user provided IP without port (e.g. http://192.168.1.50), default to port 5050
        val hostPart = trimmed.substringAfter("://")
        if (!hostPart.contains(":")) {
            trimmed = "$trimmed:5050"
        }
        return trimmed
    }

    private val _pendingRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val pendingRoute: kotlinx.coroutines.flow.StateFlow<String?> = _pendingRoute.asStateFlow()

    fun navigateToScreen(route: String) {
        _pendingRoute.value = route
    }

    fun clearPendingRoute() {
        _pendingRoute.value = null
    }

    fun updateTelemetryServerUrl(url: String) {
        val normalized = normalizeBridgeUrl(url)
        _state.value = _state.value.copy(telemetryServerUrl = normalized)
        persistData()
    }
    
    private var lastEventId: String = ""

    private fun showLocalNotification(
        title: String, 
        message: String, 
        targetRoute: String = "dashboard", 
        isTransfer: Boolean = false
    ) {
        val context = getApplication<android.app.Application>()
        val channelId = "ea_fc_telemetry"
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Live Updates", android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Live Career match results and transfer updates"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = android.content.Intent(context, MainActivity::class.java).apply {
            action = android.content.Intent.ACTION_VIEW
            flags = android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("EXTRA_NAVIGATE_TO", targetRoute)
        }

        val requestCode = (System.currentTimeMillis() % 100000).toInt()
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = if (isTransfer) R.drawable.ic_notification_transfer else R.drawable.ic_notification_ball
        
        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
            
        notificationManager.notify(requestCode, notification)
    }

    fun startTelemetryPolling() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            while (isActive) {
                fetchLiveTelemetry(silent = true)
                kotlinx.coroutines.delay(10000) // Poll every 10 seconds
            }
        }
    }

    fun testBridgeConnection(onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val base = normalizeBridgeUrl(_state.value.telemetryServerUrl)
            val targetUrl = "$base/events"
            val startTime = System.currentTimeMillis()
            try {
                val url = java.net.URL(targetUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                val elapsed = System.currentTimeMillis() - startTime
                if (code == 200) {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream))
                    val body = reader.readText().trim()
                    reader.close()
                    conn.disconnect()
                    val count = try {
                        val arr = org.json.JSONArray(body)
                        arr.length()
                    } catch (e: Exception) { 0 }

                    _state.update {
                        it.copy(
                            bridgeConnectionStatus = "Connected (${elapsed}ms, $count queued)",
                            bridgeEventCount = count
                        )
                    }
                    if (onResult != null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onResult(true, "✅ Connected! (${elapsed}ms) - $count event(s) in queue.")
                        }
                    }
                    fetchLiveTelemetry(silent = true)
                } else {
                    conn.disconnect()
                    val hint = if (code == 503) " (Windows HTTP.sys queue unassigned. Run updated Start_Bridge.bat as Admin or use Start_Bridge_Python.bat)" else " Check PC bridge window."
                    _state.update { it.copy(bridgeConnectionStatus = "HTTP $code: $hint") }
                    if (onResult != null) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            onResult(false, "Bridge responded with HTTP $code.$hint")
                        }
                    }
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                val currentPort = base.substringAfterLast(":", "5050").substringBefore("/")
                val detailMsg = when (e) {
                    is java.net.ConnectException -> "Connection refused on port $currentPort ($base). Ensure Start_Bridge.bat is open."
                    is java.net.SocketTimeoutException -> "Timed out (${elapsed}ms) on port $currentPort. Windows Firewall or Public Wi-Fi profile is blocking incoming connections."
                    is java.net.NoRouteToHostException -> "No route to host ($base). Verify your PC's IP and make sure both phone and PC are on the same Wi-Fi network."
                    else -> e.localizedMessage ?: e.javaClass.simpleName
                }
                _state.update { it.copy(bridgeConnectionStatus = "Offline: $detailMsg") }
                if (onResult != null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onResult(false, "❌ $detailMsg")
                    }
                }
            }
        }
    }

    fun injectTestMatchEvent() {
        val todayStr = try {
            java.time.LocalDate.now().toString()
        } catch (e: Exception) { "2025-10-18" }
        
        val matchJson = """
            {
                "dataType": "match",
                "title": "Live Match Final",
                "message": "Full Time: vs 3 - 1 Arsenal (Premier League)",
                "opponentName": "Arsenal",
                "isHome": true,
                "goalsFor": 3,
                "goalsAgainst": 1,
                "resultBadge": "W",
                "date": "$todayStr",
                "competition": "Premier League",
                "stage": "Matchday 24",
                "shotsFor": 14,
                "shotsAgainst": 7,
                "possession": 58,
                "passingAccuracy": 89,
                "expectedGoals": 2.85,
                "expectedGoalsAgainst": 0.92,
                "leaguePosition": 1,
                "isExtraTime": false,
                "homePenalties": 0,
                "awayPenalties": 0,
                "motmName": "Bukayo Saka",
                "playerPerformances": [
                    {"playerName": "Bukayo Saka", "goals": 2, "assists": 1, "rating": 9.1, "passes": 42, "passAccuracy": 88, "shots": 5, "shotAccuracy": 80, "tackles": 2, "tacklePercentage": 100, "interceptions": 1, "yellowCard": false, "redCard": false, "isMotm": true},
                    {"playerName": "Declan Rice", "goals": 1, "assists": 0, "rating": 8.2, "passes": 68, "passAccuracy": 94, "shots": 2, "shotAccuracy": 50, "tackles": 5, "tacklePercentage": 80, "interceptions": 4, "yellowCard": true, "redCard": false},
                    {"playerName": "Martin Odegaard", "goals": 0, "assists": 2, "rating": 8.6, "passes": 62, "passAccuracy": 90, "shots": 3, "shotAccuracy": 67, "tackles": 1, "tacklePercentage": 100, "interceptions": 2, "yellowCard": false, "redCard": false},
                    {"playerName": "William Saliba", "goals": 0, "assists": 0, "rating": 7.5, "passes": 55, "passAccuracy": 92, "shots": 0, "shotAccuracy": 0, "tackles": 4, "tacklePercentage": 75, "interceptions": 3, "yellowCard": false, "redCard": false},
                    {"playerName": "Gabriel Magalhaes", "goals": 0, "assists": 0, "rating": 7.3, "passes": 50, "passAccuracy": 89, "shots": 1, "shotAccuracy": 0, "tackles": 3, "tacklePercentage": 67, "interceptions": 2, "yellowCard": false, "redCard": false},
                    {"playerName": "David Raya", "goals": 0, "assists": 0, "rating": 7.4, "passes": 28, "passAccuracy": 82, "shots": 0, "shotAccuracy": 0, "saves": 5, "penaltySaves": 0, "tackles": 0, "tacklePercentage": 0, "interceptions": 0, "cleanSheet": false}
                ],
                "goalDetails": [
                    {"playerName": "Bukayo Saka", "minute": 24, "assisterName": "Martin Odegaard", "isOpponent": false},
                    {"playerName": "Kai Havertz (Opponent)", "minute": 41, "isOpponent": true},
                    {"playerName": "Declan Rice", "minute": 67, "assisterName": "Bukayo Saka", "isOpponent": false},
                    {"playerName": "Bukayo Saka", "minute": 88, "assisterName": "Martin Odegaard", "isOpponent": false}
                ],
                "substitutions": [
                    {"playerOutName": "Martin Odegaard", "playerInName": "Ethan Nwaneri", "minute": 89}
                ]
            }
        """.trimIndent()

        val dummyWrapper = org.json.JSONObject().apply {
            put("time", System.currentTimeMillis() / 1000)
            put("title", "Live Match Final")
            put("message", matchJson)
        }

        applyTelemetryEvents(listOf(dummyWrapper), notifyUser = true)
    }

    fun fetchLiveTelemetry(silent: Boolean = false) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val base = normalizeBridgeUrl(_state.value.telemetryServerUrl)
                val targetUrl = "$base/events"
                val url = java.net.URL(targetUrl)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                
                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    conn.disconnect()
                    _state.update { it.copy(bridgeConnectionStatus = "HTTP $responseCode") }
                    return@launch
                }
                
                val reader = java.io.BufferedReader(java.io.InputStreamReader(conn.inputStream))
                val response = reader.readText().trim()
                reader.close()
                conn.disconnect()
                
                val eventObjects = mutableListOf<org.json.JSONObject>()

                if (response.startsWith("[")) {
                    val jsonArray = org.json.JSONArray(response)
                    for (i in 0 until jsonArray.length()) {
                        // One malformed entry must not throw away every other event in the list
                        jsonArray.optJSONObject(i)?.let { eventObjects.add(it) }
                    }
                } else {
                    for (line in response.lines()) {
                        val trimmed = line.trim()
                        if (trimmed.isNotBlank()) {
                            try {
                                eventObjects.add(org.json.JSONObject(trimmed))
                            } catch (e: Exception) {}
                        }
                    }
                }

                _state.update {
                    it.copy(
                        bridgeConnectionStatus = "Online (${eventObjects.size} events)",
                        bridgeEventCount = eventObjects.size
                    )
                }

                applyTelemetryEvents(eventObjects, notifyUser = silent)
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: e.javaClass.simpleName
                _state.update { it.copy(bridgeConnectionStatus = "Offline: $msg") }
            }
        }
    }

    private fun sanitizeEventMessage(text: String): String {
        if (text.isBlank()) return text
        return text
            .replace(Regex("\\s*\\(Pens:\\s*255-\\d+\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Pens:\\s*\\d+-255\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(Pens:\\s*0-0\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\b255\\s*penalties\\b", RegexOption.IGNORE_CASE), "0 penalties")
            .replace(Regex("\\b255\\b"), "0")
    }

    private fun applyTelemetryEvents(eventObjects: List<org.json.JSONObject>, notifyUser: Boolean) {
        val events = _state.value.liveEvents.toMutableList()
        var newTransfers = _state.value.transfers.toMutableList()
        var newMatches = _state.value.recentMatches.toMutableList()
        var newSquadPlayers = _state.value.players.toMutableList()
        var newInjuries = _state.value.activeInjuries
        var hasNew = false
        
        for (json in eventObjects) {
            val timestamp = json.optLong("time", System.currentTimeMillis() / 1000)
            val rawMessage = json.optString("message", "")
            val rawTitle = json.optString("title", "Career Update")
            val trimmedMessage = rawMessage.trim()
            
            if (trimmedMessage.startsWith("{") && trimmedMessage.endsWith("}")) {
                // A malformed event is skipped; it used to throw and stop every event after it
                val dataObj = try { org.json.JSONObject(trimmedMessage) } catch (e: Exception) { null }
                if (dataObj == null) continue
                val dataType = dataObj.optString("dataType")
                val evTitle = dataObj.optString("title", rawTitle)
                val rawEvMsg = dataObj.optString("message", "")
                
                if (dataType == "transfer") {
                    val pName = dataObj.optString("playerName")
                    val tType = dataObj.optString("type")
                    val tDate = dataObj.optString("date")
                    val feeM = dataObj.optDouble("feeM", 0.0)
                    val fromToClub = dataObj.optString("fromToClub")

                    val isDuplicate = newTransfers.any { 
                        it.playerName.equals(pName, ignoreCase = true) && 
                        it.type.equals(tType, ignoreCase = true) && 
                        it.date == tDate 
                    }
                    if (!isDuplicate) {
                        if (!events.any { it.title == evTitle && it.timestamp == timestamp }) {
                            events.add(LiveEvent(evTitle, rawEvMsg, timestamp))
                        }
                        hasNew = true
                        newTransfers.add(0, TransferLog(
                            playerName = pName,
                            type = tType,
                            feeM = feeM,
                            fromToClub = fromToClub,
                            date = tDate
                        ))

                        val existingIndex = newSquadPlayers.indexOfFirst { 
                            it.name.equals(pName, ignoreCase = true) || it.displayName.equals(pName, ignoreCase = true) 
                        }
                        if (existingIndex >= 0) {
                            val existing = newSquadPlayers[existingIndex]
                            val updated = when {
                                tType.contains("Out", ignoreCase = true) && tType.contains("Loan", ignoreCase = true) ->
                                    existing.copy(status = "Loaned Out", isLoanedOut = true, isStartingXI = false)
                                tType.contains("Out", ignoreCase = true) || tType.equals("Sold", ignoreCase = true) ->
                                    existing.copy(status = "Sold", isLoanedOut = false, isStartingXI = false)
                                tType.contains("In", ignoreCase = true) && tType.contains("Loan", ignoreCase = true) ->
                                    existing.copy(status = "Loaned In", isLoanedIn = true, isLoanedOut = false)
                                tType.equals("Retirement", ignoreCase = true) || tType.equals("Retired", ignoreCase = true) ->
                                    existing.copy(status = "Retired", isStartingXI = false)
                                else ->
                                    existing.copy(status = "Active", isLoanedOut = false, isAcademy = false)
                            }
                            newSquadPlayers[existingIndex] = updated
                        } else if (tType.contains("In", ignoreCase = true) || tType.contains("Sign", ignoreCase = true)) {
                            newSquadPlayers.add(Player(
                                name = pName,
                                overall = 72,
                                valueM = if (feeM > 0) feeM else 2.0,
                                status = if (tType.contains("Loan", ignoreCase = true)) "Loaned In" else "Active",
                                isLoanedIn = tType.contains("Loan", ignoreCase = true)
                            ))
                        }

                        if (notifyUser) {
                            showLocalNotification(evTitle, rawEvMsg, targetRoute = "transfers", isTransfer = true)
                        }
                    }
                } else if (dataType == "match") {
                    val oppName = dataObj.optString("opponentName", "Opponent")
                    val isHome = dataObj.optBoolean("isHome", true)
                    val gf = dataObj.optInt("goalsFor", 0)
                    val ga = dataObj.optInt("goalsAgainst", 0)
                    val mDate = dataObj.optString("date", try { java.time.LocalDate.now().toString() } catch (e: Exception) { "2025-10-18" })
                    val rawComp = dataObj.optString("competition", "League")
                    val rawStage = dataObj.optString("stage", "")
                    val rawHp = dataObj.optInt("homePenalties", 0)
                    val rawAp = dataObj.optInt("awayPenalties", 0)

                    val isPlayoff = rawComp.contains("Play-off", ignoreCase = true) ||
                                    rawComp.contains("Playoff", ignoreCase = true) ||
                                    rawComp.contains("Promotion", ignoreCase = true) ||
                                    rawStage.contains("Play-off", ignoreCase = true) ||
                                    rawStage.contains("Playoff", ignoreCase = true)

                    // Whenever there is a playoff match happening for lower division teams going through promotion playoff, the competition should STILL be the league the team is currently in!
                    val comp = if (isPlayoff) {
                        _state.value.leagueName.takeIf { it.isNotBlank() } ?: "EFL Championship"
                    } else {
                        rawComp
                    }

                    val isLeagueComp = (!isPlayoff) && (
                        comp.contains("League", ignoreCase = true) ||
                        comp.contains("Division", ignoreCase = true) ||
                        comp.contains("Championship", ignoreCase = true) ||
                        comp.contains("Premier", ignoreCase = true) ||
                        comp == "Serie A" || comp == "La Liga" || comp == "Bundesliga" || comp == "Ligue 1"
                    )

                    val isPlayoffLeg1 = isPlayoff && (rawStage.contains("Leg 1", ignoreCase = true) || rawStage.contains("playoff", ignoreCase = true) && !rawStage.contains("Leg 2", ignoreCase = true) && !rawStage.contains("Final", ignoreCase = true))

                    // EA FC memory uses 255 (0xFF) as sentinel for 'no penalties taken'
                    val hp = if (isLeagueComp || isPlayoffLeg1 || rawHp >= 255 || rawHp < 0) 0 else rawHp
                    val ap = if (isLeagueComp || isPlayoffLeg1 || rawAp >= 255 || rawAp < 0) 0 else rawAp
                    val isET = if (isLeagueComp || isPlayoffLeg1) false else (dataObj.optBoolean("isExtraTime", false) || (hp > 0) || (ap > 0))

                    val stageVal = if (isPlayoff) {
                        if (rawStage.contains("Play-off", ignoreCase = true) || rawStage.contains("Playoff", ignoreCase = true)) {
                            rawStage
                        } else {
                            "Play-off"
                        }
                    } else if (isLeagueComp) {
                        val existingLeagueMatches = (newMatches + _state.value.recentMatches).filter { m ->
                            val c = m.competition
                            c.contains("League", true) || c.contains("Division", true) || c.contains("Championship", true) || c.contains("Premier", true) || c == "Serie A" || c == "La Liga" || c == "Bundesliga" || c == "Ligue 1"
                        }
                        val matchdayNum = if (rawStage.contains("Matchday", ignoreCase = true) && !rawStage.contains("Final", ignoreCase = true)) {
                            Regex("\\d+").find(rawStage)?.value?.toIntOrNull() ?: (existingLeagueMatches.size + 1)
                        } else {
                            existingLeagueMatches.size + 1
                        }
                        "Matchday $matchdayNum"
                    } else {
                        if (rawStage.isNotBlank() && !rawStage.equals("Matchday Final", ignoreCase = true)) {
                            rawStage
                        } else {
                            if (isET || hp > 0 || ap > 0) "Knockout Stage" else "Round 1"
                        }
                    }

                    // Construct pristine, 100% clean notification & live event string without any 255 or false extra time
                    val cleanEtStr = if (hp > 0 || ap > 0) " (Pens: $hp-$ap)" else if (isET) " (A.E.T.)" else ""
                    val cleanedEvMsg = sanitizeEventMessage("Full Time: ${_state.value.clubName} ${if (isHome) "vs" else "@"} $gf - $ga $oppName ($comp - $stageVal)$cleanEtStr")

                    // The same match can arrive from two sources (database + Atlas export): ignore the second one
                    val isDuplicateMatch = newMatches.any { m ->
                        m.opponentName.filter { ch -> ch.isLetterOrDigit() }.equals(oppName.filter { ch -> ch.isLetterOrDigit() }, ignoreCase = true) &&
                        m.goalsFor == gf && m.goalsAgainst == ga && m.matchDate == mDate && m.isHome == isHome
                    }
                    var isNewMatchEvent = false

                    if (!isDuplicateMatch && !events.any { it.title == evTitle && it.timestamp == timestamp }) {
                        isNewMatchEvent = true
                        events.add(LiveEvent(evTitle, cleanedEvMsg, timestamp))
                        hasNew = true

                        val perfList = parseMatchPerformances(dataObj.optJSONArray("playerPerformances"), newSquadPlayers).toMutableList()

                        // Auto-fill starting XI if fewer than 11 players in lineup
                        val existingIds = perfList.map { it.playerId }.toSet()
                        val availableSeniorSquad = newSquadPlayers.filter { 
                            !it.isAcademy && !it.isLoanedOut && !it.status.equals("Sold", true) && !existingIds.contains(it.id) && !existingIds.contains(it.displayName)
                        }.sortedByDescending { it.overall }

                        // Real exported line-ups (detailSource = atlas) are complete: never invent players for them
                        val neededStarters = if (dataObj.optString("detailSource") == "atlas") 0 else (11 - perfList.size).coerceAtLeast(0)
                        for (i in 0 until neededStarters.coerceAtMost(availableSeniorSquad.size)) {
                            val p = availableSeniorSquad[i]
                            val pos = p.primaryPosition.uppercase()
                            val isGk = pos == "GK"
                            val isDef = pos in listOf("CB", "LB", "RB", "LWB", "RWB")
                            val isMid = pos in listOf("CM", "CDM", "CAM", "LM", "RM")
                            
                            val pRating = when {
                                gf > ga -> 7.2 + (i % 4) * 0.2
                                gf < ga -> 6.4 + (i % 3) * 0.2
                                else -> 6.8 + (i % 3) * 0.2
                            }.coerceIn(5.5, 9.2)
                            
                            val pCleanSheet = (isGk || isDef) && ga == 0
                            val pPasses = when {
                                isMid -> 50 + (i * 3) % 25
                                isDef -> 40 + (i * 2) % 20
                                isGk -> 20 + (i * 2) % 10
                                else -> 25 + (i * 3) % 15
                            }
                            val pPassAcc = 82 + (i * 2) % 10
                            val pShots = if (!isGk && !isDef) (1 + (i % 3)) else 0
                            val pShotAcc = if (pShots > 0) 65 else 0
                            val pTackles = if (!isGk) (2 + (i % 4)) else 0
                            val pTacklePct = if (pTackles > 0) 75 else 0
                            val pInterceptions = if (!isGk) (1 + (i % 3)) else 0
                            val pSaves = if (isGk) maxOf(0, dataObj.optInt("shotsAgainst", 5) - ga) else 0
                            
                            perfList.add(PlayerPerformance(
                                playerId = p.id,
                                rating = Math.round(pRating * 10.0) / 10.0,
                                goals = 0,
                                assists = 0,
                                passes = pPasses,
                                passAccuracy = pPassAcc,
                                shots = pShots,
                                shotAccuracy = pShotAcc,
                                saves = pSaves,
                                penaltySaves = if (isGk && ap > 0) maxOf(0, 5 - ap) else 0,
                                tackles = pTackles,
                                tacklePercentage = pTacklePct,
                                interceptions = pInterceptions,
                                cleanSheet = pCleanSheet
                            ))
                        }

                        // Clean sheet updates for GK and defenders
                        if (ga == 0) {
                            for (idx in perfList.indices) {
                                val perf = perfList[idx]
                                val player = newSquadPlayers.find { it.id == perf.playerId || it.displayName == perf.playerId }
                                val pos = player?.primaryPosition?.uppercase() ?: ""
                                if (pos == "GK" || pos in listOf("CB", "LB", "RB", "LWB", "RWB")) {
                                    perfList[idx] = perf.copy(cleanSheet = true)
                                }
                            }
                        }

                        // Determine MOTM
                        val bestPlayer = perfList.maxByOrNull { (it.goals * 3) + (it.assists * 2) + (if (it.cleanSheet) 1.5 else 0.0) + it.rating }
                        val rawMotm = dataObj.optString("motmName").takeIf { it.isNotBlank() }
                        val motmName = rawMotm ?: if (bestPlayer != null) {
                            newSquadPlayers.find { it.id == bestPlayer.playerId }?.displayName ?: bestPlayer.playerId
                        } else ""

                        val finalPerfList = perfList.map { p ->
                            val pName = newSquadPlayers.find { it.id == p.playerId }?.displayName ?: p.playerId
                            if (pName.equals(motmName, ignoreCase = true) || (bestPlayer?.playerId == p.playerId && motmName.isNotBlank())) {
                                p.copy(isMotm = true)
                            } else p
                        }

                        val parsedSubs = parseSubstitutions(dataObj.optJSONArray("substitutions"), newSquadPlayers)
                        val parsedGoals = parseGoalDetails(dataObj.optJSONArray("goalDetails"), newSquadPlayers)

                        val shotsF = dataObj.optInt("shotsFor", if (gf > 0) gf * 3 + 2 else 6)
                        val shotsA = dataObj.optInt("shotsAgainst", if (ga > 0) ga * 3 + 1 else 4)
                        val xG = dataObj.optDouble("expectedGoals", if (gf > 0) Math.round((gf * 0.82 + 0.35) * 100.0) / 100.0 else 0.45)
                        val xGA = dataObj.optDouble("expectedGoalsAgainst", if (ga > 0) Math.round((ga * 0.78 + 0.28) * 100.0) / 100.0 else 0.32)
                        val poss = dataObj.optInt("possession", if (gf >= ga) 54 else 46)
                        val passAcc = dataObj.optInt("passingAccuracy", 86)
                        val leaguePos = if (dataObj.has("leaguePosition") && dataObj.optInt("leaguePosition") > 0) {
                            dataObj.optInt("leaguePosition")
                        } else {
                            _state.value.leagueTable.finalRank.toIntOrNull() ?: 1
                        }

                        val matchObj = MatchResult(
                            id = "live-${timestamp}",
                            opponentName = oppName,
                            isHome = isHome,
                            goalsFor = gf,
                            goalsAgainst = ga,
                            matchDate = mDate,
                            competition = comp,
                            stage = stageVal,
                            isExtraTime = isET,
                            homePenalties = hp,
                            awayPenalties = ap,
                            possession = poss,
                            passingAccuracy = passAcc,
                            shotsFor = shotsF,
                            shotsAgainst = shotsA,
                            expectedGoals = xG,
                            expectedGoalsAgainst = xGA,
                            motmName = motmName,
                            leaguePosition = leaguePos,
                            opponentCrestUrl = com.fctracker.career.ui.screens.getTeamCrestUrl(oppName),
                            playerPerformances = finalPerfList,
                            goalDetails = parsedGoals,
                            substitutions = parsedSubs
                        )

                        newMatches.add(0, matchObj)

                        // Update squad player season totals
                        val perfMap = finalPerfList.associateBy { it.playerId }
                        newSquadPlayers = newSquadPlayers.map { player ->
                            val perf = perfMap[player.id] ?: perfMap[player.displayName] ?: perfMap[player.name]
                            if (perf != null) {
                                val newMatchesPlayed = player.matchesPlayed + 1
                                val newGoals = player.goals + perf.goals
                                val newAssists = player.assists + perf.assists
                                val newAvgRating = if (perf.rating > 0) {
                                    if (player.matchesPlayed == 0) perf.rating
                                    else Math.round(((player.averageRating * player.matchesPlayed + perf.rating) / newMatchesPlayed) * 10.0) / 10.0
                                } else player.averageRating

                                if (player.isInjured && player.injuryReturnDate != null && mDate >= player.injuryReturnDate) {
                                    player.copy(
                                        isInjured = false, injuryName = null, injuryStartDate = null, injuryReturnDate = null,
                                        matchesPlayed = newMatchesPlayed, goals = newGoals, assists = newAssists, averageRating = newAvgRating
                                    )
                                } else {
                                    player.copy(
                                        matchesPlayed = newMatchesPlayed, goals = newGoals, assists = newAssists, averageRating = newAvgRating
                                    )
                                }
                            } else {
                                player
                            }
                        }.toMutableList()

                    } else if (dataType == "injury") {
                        newInjuries += 1
                    }
                    
                    // Only notify for a match that was just added (this used to fire again for every stored
                    // match on every 10-second poll)
                    if (notifyUser && isNewMatchEvent) {
                        val route = when (dataType) {
                            "match" -> "matches"
                            "transfer" -> "transfers"
                            "injury" -> "squad"
                            else -> "dashboard"
                        }
                        showLocalNotification(evTitle, cleanedEvMsg, targetRoute = route, isTransfer = (dataType == "transfer"))
                    }
                }
            } else if (trimmedMessage.isNotEmpty()) {
                val sanitizedRaw = sanitizeEventMessage(rawMessage)
                if (!events.any { it.title == rawTitle && it.timestamp == timestamp }) {
                    events.add(LiveEvent(rawTitle, sanitizedRaw, timestamp))
                    hasNew = true
                    if (notifyUser) {
                        val isTransfer = rawTitle.contains("transfer", ignoreCase = true) || 
                                         sanitizedRaw.contains("transfer", ignoreCase = true) || 
                                         sanitizedRaw.contains("signed", ignoreCase = true) || 
                                         sanitizedRaw.contains("fee", ignoreCase = true)
                        val isMatch = rawTitle.contains("match", ignoreCase = true) || 
                                      sanitizedRaw.contains(" vs ", ignoreCase = true) || 
                                      sanitizedRaw.contains("Full Time", ignoreCase = true) || 
                                      sanitizedRaw.contains("FT:", ignoreCase = true) ||
                                      sanitizedRaw.contains("Goal", ignoreCase = true)
                        val route = if (isMatch) "matches" else if (isTransfer) "transfers" else "dashboard"
                        showLocalNotification(rawTitle, sanitizedRaw, targetRoute = route, isTransfer = isTransfer)
                    }
                }
            }
        }
        
        if (hasNew) {
            _state.update { 
                it.copy(
                    liveEvents = events.sortedByDescending { ev -> ev.timestamp },
                    transfers = newTransfers,
                    recentMatches = newMatches,
                    activeInjuries = newInjuries,
                    players = newSquadPlayers
                ) 
            }
        }
    }

    fun injectTestTransferEvent() {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val transferJson = """
            {
                "dataType": "transfer",
                "title": "Transfer Finalized",
                "message": "Erling Haaland signed from Manchester City for €120.0M",
                "playerName": "Erling Haaland",
                "type": "Bought",
                "feeM": 120.0,
                "fromToClub": "Manchester City",
                "date": "$todayStr"
            }
        """.trimIndent()

        val dummyWrapper = org.json.JSONObject().apply {
            put("time", System.currentTimeMillis() / 1000)
            put("title", "Transfer Finalized")
            put("message", transferJson)
        }

        applyTelemetryEvents(listOf(dummyWrapper), notifyUser = true)
    }
fun addNote(title: String, content: String) { savedNotes.add(Note(title = title, content = content)) }
    fun removeNote(note: Note) { savedNotes.removeAll { it.id == note.id } }
    
    fun addScoutReport(report: ScoutReport) {
        _state.value = _state.value.copy(scoutReports = _state.value.scoutReports + report)
    }

    fun removeScoutReport(reportId: String) {
        _state.value = _state.value.copy(scoutReports = _state.value.scoutReports.filter { it.id != reportId })
    }

    fun updateNote(id: String, title: String, content: String) {
        val index = savedNotes.indexOfFirst { it.id == id }
        if (index != -1) {
            savedNotes[index] = savedNotes[index].copy(title = title, content = content)
        }
    }

    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    

    fun completeOnboarding(
        managerName: String,
        clubName: String,
        country: String,
        leagueName: String,
        startYear: Int,
        currencySymbol: String,
        primaryColor: String,
        secondaryColor: String,
        clubCrestUrl: String,
        isExistingTeam: Boolean,
        aiAgent: String,
        aiApiUrl: String,
        aiApiKey: String
    ) {
        val existingPlayers = _state.value.players
        val existingHistorical = _state.value.historicalSeasons
        val effectiveStartYear = if (existingHistorical.isNotEmpty() && _state.value.startYear > startYear) _state.value.startYear else startYear

        _state.update {
            it.copy(
                isOnboarded = true,
                managerName = managerName,
                clubName = clubName,
                country = country,
                leagueName = leagueName,
                startYear = effectiveStartYear,
                currencySymbol = currencySymbol.split(" ")[0],
                primaryColorHex = primaryColor,
                secondaryColorHex = secondaryColor,
                clubCrestUrl = clubCrestUrl,
                isExistingTeam = isExistingTeam,
                aiAgent = aiAgent,
                aiApiUrl = aiApiUrl,
                aiApiKey = aiApiKey
            )
        }
        if (existingPlayers.isEmpty()) {
            initializeGameData(isExistingTeam, clubName)
        }
        saveSlots[activeSaveSlot] = _state.value
        persistData()
    }
    var allEafcPlayers: List<EafcPlayer> = emptyList()
        private set

    private val _eafcPlayers = MutableStateFlow<List<EafcPlayer>>(emptyList())
    val eafcPlayers: StateFlow<List<EafcPlayer>> = _eafcPlayers.asStateFlow()

    fun updateActiveFormation(formation: String) {
        _state.update { it.copy(activeFormation = formation) }
        persistData()
    }

    fun searchPlayersAndTeams(query: String): List<EafcPlayer> {
        if (query.isBlank()) return emptyList<EafcPlayer>()
        return allEafcPlayers.filter { 
            it.name.contains(query, ignoreCase = true) || 
            it.commonName.contains(query, ignoreCase = true) ||
            it.team.contains(query, ignoreCase = true)
        }.take(10)
    }

        fun getUniqueClubs(): List<String> {
        loadAllEafcPlayers()
        return allEafcPlayers.map { it.team }.distinct().sorted()
    }

    fun resolvePlayerHeadshotUrl(
        playerId: String?,
        playerName: String?,
        playerDisplayName: String? = null,
        isAcademy: Boolean = false
    ): String {
        if (isAcademy) return ""
        loadAllEafcPlayers()

        // 1. Direct check on numeric FIFA player ID
        val numericId = playerId?.trim()?.toLongOrNull()
        if (numericId != null && numericId in 1..280000) {
            return "https://cdn.futbin.com/content/fifa25/img/players/$numericId.png"
        }

        // 2. Lookup in EAFC players database by name or common name (case-insensitive & accent-insensitive)
        val cleanName = playerName?.trim().orEmpty()
        val cleanDisplay = playerDisplayName?.trim().orEmpty()

        fun stripAccents(s: String): String {
            val normalized = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
            return normalized.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "").lowercase().trim()
        }

        val normName = stripAccents(cleanName)
        val normDisplay = stripAccents(cleanDisplay)

        if (normName.isNotBlank() || normDisplay.isNotBlank()) {
            val match = allEafcPlayers.firstOrNull { ep ->
                val epName = stripAccents(ep.name)
                val epCommon = stripAccents(ep.commonName)
                (normName.isNotBlank() && (epName == normName || (epCommon.isNotBlank() && epCommon == normName))) ||
                (normDisplay.isNotBlank() && (epName == normDisplay || (epCommon.isNotBlank() && epCommon == normDisplay)))
            } ?: allEafcPlayers.firstOrNull { ep ->
                val epName = stripAccents(ep.name)
                (normName.length >= 4 && (epName.contains(normName) || normName.contains(epName))) ||
                (normDisplay.length >= 4 && (epName.contains(normDisplay) || normDisplay.contains(epName)))
            }

            if (match != null && match.id in 1..280000) {
                return "https://cdn.futbin.com/content/fifa25/img/players/${match.id}.png"
            }
        }

        // 3. Fallback to numeric ID if valid positive integer
        if (numericId != null && numericId > 0) {
            return "https://cdn.futbin.com/content/fifa25/img/players/$numericId.png"
        }

        return ""
    }

    private fun loadAllEafcPlayers() {
        if (allEafcPlayers.isNotEmpty()) return
        try {
            val jsonStr = getApplication<android.app.Application>().assets.open("players.json").bufferedReader().use { it.readText() }
            val jsonArray = org.json.JSONArray(jsonStr)
            val list = mutableListOf<EafcPlayer>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(EafcPlayer(
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
                ))
            }
            allEafcPlayers = list
            _eafcPlayers.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initializeGameData(isExistingTeam: Boolean, clubName: String) {
        loadAllEafcPlayers()
        val mockPlayers = mutableListOf<Player>()
        if (isExistingTeam) {
            try {
                
                
                
                var teamSearchName = clubName
                if (clubName == "Paris Saint-Germain") teamSearchName = "Paris SG"
                if (clubName == "Inter Milan") teamSearchName = "Inter"
                if (clubName == "AC Milan") teamSearchName = "Milan"
                if (clubName == "Tottenham Hotspur") teamSearchName = "Spurs" // Or Tottenham Hotspur
                

                for (eafcPlayer in allEafcPlayers) {
                    val team = eafcPlayer.team
                    if (team.contains(teamSearchName, ignoreCase = true) || teamSearchName.contains(team, ignoreCase = true)) {
                        val idStr = eafcPlayer.id.toString()
                        val headshotUrl = resolvePlayerHeadshotUrl(idStr, eafcPlayer.name, eafcPlayer.commonName, isAcademy = false)
                        
                        val name = eafcPlayer.name
                        val commonName = eafcPlayer.commonName
                        val finalName = name
                        
                        val altPos = eafcPlayer.alternatePositions.split(",")
                        val secPos = altPos.getOrNull(0)?.trim() ?: ""
                        val tertPos = altPos.getOrNull(1)?.trim() ?: ""
                        
                        val rawBirthdate = eafcPlayer.birthdate
                        var birthdate = rawBirthdate.split(" ")[0]
                        try {
                            val parts = birthdate.split("/")
                            if (parts.size == 3) {
                                birthdate = "${parts[2]}-${parts[0].padStart(2, '0')}-${parts[1].padStart(2, '0')}"
                            }
                        } catch(e: Exception) {}
                        // rough age calc:
                        val birthYear = try { birthdate.split("/")[2].split(" ")[0].toInt() } catch(e: Exception) { 2000 }
                        val age = 2025 - birthYear

                        mockPlayers.add(
                            Player(
                                id = java.util.UUID.randomUUID().toString(),
                                name = finalName,
                                nickname = commonName,
                                primaryPosition = eafcPlayer.position,
                                secondaryPosition = secPos,
                                tertiaryPosition = tertPos,
                                overall = eafcPlayer.rating,
                                startingOverall = eafcPlayer.rating,
                                potential = eafcPlayer.potential,
                                age = age,
                                isStartingXI = mockPlayers.size < 11,
                                pitchX = if (mockPlayers.size < 11) (mockPlayers.size % 4) * 0.25f else 0f,
                                pitchY = if (mockPlayers.size < 11) (mockPlayers.size / 4) * 0.25f + 0.1f else 0f,
                                valueM = eafcPlayer.value,
                                wageK = eafcPlayer.wage,
                                nationality = eafcPlayer.nationality,
                                birthDate = birthdate,
                                height = eafcPlayer.height,
                                weight = eafcPlayer.weight,
                                headshotUrl = headshotUrl
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (mockPlayers.isEmpty()) {
                mockPlayers.addAll(listOf(
                    Player(name = "Jamie Tartt", primaryPosition = "ST", secondaryPosition = "RW", age = 24, startingOverall = 85, overall = 85, potential = 89, valueM = 75.0, wageK = 120, isStartingXI = true, pitchX = 0.5f, pitchY = 0.85f, matchesPlayed = 15, goals = 12, assists = 3, averageRating = 8.2),
                    Player(name = "Roy Kent", primaryPosition = "CDM", secondaryPosition = "CM", age = 34, startingOverall = 81, overall = 81, potential = 81, valueM = 15.0, wageK = 80, isStartingXI = true, pitchX = 0.5f, pitchY = 0.35f, matchesPlayed = 14, goals = 1, assists = 2, averageRating = 7.1),
                    Player(name = "Sam Obisanya", primaryPosition = "RM", secondaryPosition = "RW", age = 22, startingOverall = 83, overall = 83, potential = 87, valueM = 55.0, wageK = 75, isStartingXI = true, pitchX = 0.85f, pitchY = 0.65f, matchesPlayed = 15, goals = 4, assists = 6, averageRating = 7.8)
                ))
            }
        }
        
        // Sort players by overall descending
        mockPlayers.sortByDescending { it.overall }

        val mockObjectives = emptyList<Objective>()


        val mockMatches = emptyList<MatchResult>()

        _state.update {
            it.copy(
                players = mockPlayers,
                objectives = mockObjectives,
                recentMatches = mockMatches
            )
        }
    }

    fun updateSettings(clubName: String, managerName: String, leagueName: String, country: String, currencySymbol: String, primaryColorHex: String, secondaryColorHex: String, clubCrestUrl: String, aiAgent: String = "", aiApiUrl: String = "", aiApiKey: String = "") {
        _state.update { current ->
            current.copy(
                clubName = clubName,
                managerName = managerName,
                leagueName = leagueName,
                country = country,
                currencySymbol = currencySymbol.split(" ")[0],
                primaryColorHex = primaryColorHex,
                secondaryColorHex = secondaryColorHex,
                clubCrestUrl = clubCrestUrl,
                aiAgent = aiAgent,
                aiApiUrl = aiApiUrl,
                aiApiKey = aiApiKey
            )
        }
    }

        fun addTeamTrophy(trophy: TeamTrophy) {
        _state.update { it.copy(teamTrophies = it.teamTrophies + trophy) }
    }
    fun addPlayerAward(award: PlayerAward) {
        _state.update { it.copy(playerAwards = it.playerAwards + award) }
    }
    fun addObjective(objective: Objective) {
        _state.update { it.copy(objectives = it.objectives + objective) }
    }

    
    private fun mapEafcPlayerToPlayer(eafcPlayer: EafcPlayer): Player {
        val idStr = eafcPlayer.id.toString()
        val headshotUrl = resolvePlayerHeadshotUrl(idStr, eafcPlayer.name, eafcPlayer.commonName, isAcademy = false)
        val altPos = eafcPlayer.alternatePositions.split(",")
        val secPos = altPos.getOrNull(0)?.trim() ?: ""
        val tertPos = altPos.getOrNull(1)?.trim() ?: ""

        val rawBirthdate = eafcPlayer.birthdate
        var birthDateStr = rawBirthdate.split(" ")[0]
        try {
            val parts = birthDateStr.split("/")
            if (parts.size == 3) {
                birthDateStr = "${parts[2]}-${parts[0].padStart(2, '0')}-${parts[1].padStart(2, '0')}"
            }
        } catch(e: Exception) {}
        val birthYear = try { birthDateStr.split("-")[0].toInt() } catch(e: Exception) { 2000 }
        val ageCalc = _state.value.startYear - birthYear
        
        return Player(
            name = eafcPlayer.name,
            nickname = eafcPlayer.commonName.takeIf { it.isNotBlank() },
            primaryPosition = eafcPlayer.position,
            secondaryPosition = secPos.takeIf { it.isNotBlank() },
            tertiaryPosition = tertPos.takeIf { it.isNotBlank() },
            age = ageCalc,
            startingOverall = eafcPlayer.rating,
            overall = eafcPlayer.rating,
            potential = eafcPlayer.potential,
            valueM = eafcPlayer.value,
            wageK = eafcPlayer.wage,
            nationality = eafcPlayer.nationality,
            birthDate = birthDateStr,
            height = eafcPlayer.height,
            weight = eafcPlayer.weight,
            preferredFoot = eafcPlayer.preferredFoot,
            weakFoot = eafcPlayer.weakFoot,
            headshotUrl = headshotUrl
        )
    }

    fun addTransferLog(log: TransferLog) {
        _state.update { state ->
            val existingPlayer = state.players.find { it.name == log.playerName || it.displayName == log.playerName }
            val newPlayers = state.players.toMutableList()
            if (existingPlayer != null) {
                val index = newPlayers.indexOf(existingPlayer)
                when (log.type) {
                    "Out (Permanent)" -> newPlayers[index] = existingPlayer.copy(status = "Sold", isStartingXI = false)
                    "Out (Loan)" -> newPlayers[index] = existingPlayer.copy(status = "Loaned Out", isLoanedOut = true, isStartingXI = false)
                    "Retirement" -> newPlayers[index] = existingPlayer.copy(status = "Retired", isStartingXI = false)
                    "In (Loan)" -> newPlayers[index] = existingPlayer.copy(isLoanedIn = true, status = "Active", isLoanedOut = false)
                    "In (Permanent)", "Free Agent Sign" -> newPlayers[index] = existingPlayer.copy(isLoanedIn = false, isLoanedOut = false, status = "Active")
                }
            } else if (log.type.startsWith("In") || log.type == "Free Agent Sign") {
                val eafcMatch = allEafcPlayers.find { it.name == log.playerName || it.commonName == log.playerName || "${it.commonName} (${it.name})" == log.playerName }
                if (eafcMatch != null) {
                    val p = mapEafcPlayerToPlayer(eafcMatch).copy(
                        status = "Active",
                        isLoanedIn = log.type == "In (Loan)",
                        isRegen = log.isRegen,
                        headshotUrl = if (log.headshotUrl.isNotBlank()) log.headshotUrl else "https://cdn.futbin.com/content/fifa25/img/players/${eafcMatch.id}.png",
                        primaryPosition = if (log.position.isNotBlank()) log.position else eafcMatch.position
                    )
                    newPlayers.add(p)
                } else {
                    newPlayers.add(Player(
                        name = log.playerName,
                        primaryPosition = if (log.position.isNotBlank()) log.position else "CM",
                        age = 22,
                        startingOverall = 70,
                        overall = 70,
                        potential = 75,
                        valueM = log.feeM,
                        wageK = 10,
                        isLoanedIn = log.type == "In (Loan)",
                        status = "Active",
                        isRegen = log.isRegen,
                        headshotUrl = log.headshotUrl
                    ))
                }
            }
            state.copy(transfers = state.transfers + log, players = newPlayers)
        }
    }
    
    fun deleteTransferLog(logId: String) {
        _state.update { state ->
            val log = state.transfers.find { it.id == logId } ?: return@update state
            val newTransfers = state.transfers.filter { it.id != logId }
            val newPlayers = state.players.toMutableList()
            val existingPlayer = newPlayers.find { it.name == log.playerName || it.displayName == log.playerName }
            
            if (existingPlayer != null) {
                val index = newPlayers.indexOf(existingPlayer)
                when (log.type) {
                    "Out (Permanent)", "Out (Loan)", "Retirement" -> {
                        newPlayers[index] = existingPlayer.copy(status = "Active", isLoanedOut = false)
                    }
                    "In (Permanent)", "In (Loan)", "Free Agent Sign" -> {
                        newPlayers.removeAt(index)
                    }
                }
            }
            state.copy(transfers = newTransfers, players = newPlayers)
        }
    }


    fun updateTransferBudget(budget: Double) {
        _state.update { it.copy(transferBudget = budget) }
    }


    
    // Save Slot Logic
    var activeSaveSlot = 1
    val saveSlots = mutableMapOf<Int, AppState>()
    val saveSlotNames = androidx.compose.runtime.mutableStateMapOf<Int, String>()
    
    fun switchSaveSlot(slot: Int, createNew: Boolean = false) {
        if (activeSaveSlot == slot) return
        
        // Save current to map
        saveSlots[activeSaveSlot] = _state.value
        if (!saveSlotNames.containsKey(activeSaveSlot)) {
            saveSlotNames[activeSaveSlot] = "Save Slot $activeSaveSlot"
        }
        
        activeSaveSlot = slot
        if (createNew || !saveSlots.containsKey(slot)) {
            _state.value = AppState()
            saveSlotNames[slot] = "Save Slot $slot"
        } else {
            _state.value = saveSlots[slot]!!
        }
    }
    
    fun deleteCurrentSave() {
        saveSlots.remove(activeSaveSlot)
        saveSlotNames.remove(activeSaveSlot)
        
        val nextSlot = saveSlots.keys.firstOrNull()
        if (nextSlot != null) {
            activeSaveSlot = nextSlot
            _state.value = saveSlots[nextSlot]!!
        } else {
            activeSaveSlot = 1
            _state.value = AppState(isOnboarded = false)
        }
        persistData()
    }
    
    fun renameSaveSlot(slot: Int, name: String) {
        saveSlotNames[slot] = name
    }

    fun exportStateToJson(): String {
        return Gson().toJson(_state.value)
    }

    fun importStateFromJson(
        json: String,
        clubNameOverride: String? = null,
        primaryColorOverride: String? = null,
        secondaryColorOverride: String? = null
    ): Boolean {
        return try {
            val importedState = com.google.gson.Gson().fromJson(json, AppState::class.java)
            if (importedState != null) {
                // 1. Resolve Club Name dynamically without hardcoding
                val rawClubInJson = importedState.clubName ?: ""
                val sanitizedClubName = when {
                    !clubNameOverride.isNullOrBlank() && !clubNameOverride.startsWith("*") -> clubNameOverride.trim()
                    !rawClubInJson.startsWith("*") && rawClubInJson.isNotBlank() && rawClubInJson != "Created Club" -> rawClubInJson.trim()
                    _state.value.clubName.isNotBlank() && !_state.value.clubName.startsWith("*") && _state.value.clubName != "Richmond FC" -> _state.value.clubName.trim()
                    else -> if (rawClubInJson.isNotBlank() && !rawClubInJson.startsWith("*")) rawClubInJson.trim() else "My Custom Club"
                }

                // 2. Resolve Primary and Secondary Colors for Theming
                val jsonPrimary = importedState.primaryColorHex ?: ""
                val resolvedPrimary = when {
                    !primaryColorOverride.isNullOrBlank() -> primaryColorOverride.trim()
                    jsonPrimary.isNotBlank() && jsonPrimary != "#1E88E5" -> jsonPrimary.trim()
                    _state.value.primaryColorHex.isNotBlank() && _state.value.primaryColorHex != "#1E88E5" -> _state.value.primaryColorHex.trim()
                    else -> jsonPrimary.ifBlank { "#1E88E5" }
                }
                val jsonSecondary = importedState.secondaryColorHex ?: ""
                val resolvedSecondary = when {
                    !secondaryColorOverride.isNullOrBlank() -> secondaryColorOverride.trim()
                    jsonSecondary.isNotBlank() && jsonSecondary != "#D81B60" -> jsonSecondary.trim()
                    _state.value.secondaryColorHex.isNotBlank() && _state.value.secondaryColorHex != "#D81B60" -> _state.value.secondaryColorHex.trim()
                    else -> jsonSecondary.ifBlank { "#D81B60" }
                }

                // 3. Process Players (Senior + Academy) and map Alt Positions to Secondary & Tertiary
                val rawAllPlayers = mutableListOf<Player>()
                if (importedState.players != null) {
                    rawAllPlayers.addAll(importedState.players)
                }
                if (!importedState.academyPlayers.isNullOrEmpty()) {
                    importedState.academyPlayers.forEach { ap ->
                        rawAllPlayers.add(ap.copy(isAcademy = true))
                    }
                }

                val processedPlayers = rawAllPlayers.distinctBy { 
                    val pid = it.id?.takeIf { id -> id.isNotBlank() }
                    pid ?: (it.name ?: UUID.randomUUID().toString())
                }.map { player ->
                    val safeId = if (player.id.isNullOrBlank()) UUID.randomUUID().toString() else player.id
                    val safeName = if (player.name.isNullOrBlank()) (player.displayName ?: "Player") else player.name
                    var sec = player.secondaryPosition?.trim()
                    var tert = player.tertiaryPosition?.trim()

                    val rawAlt = when {
                        !player.altPosition.isNullOrBlank() -> player.altPosition
                        sec != null && sec.contains("/") -> sec
                        else -> null
                    }

                    if (!rawAlt.isNullOrBlank()) {
                        val altParts = rawAlt.split("/").map { it.trim() }.filter { it.isNotEmpty() }
                        if (altParts.isNotEmpty()) {
                            sec = altParts.getOrNull(0)
                        }
                        if (altParts.size > 1) {
                            tert = altParts.getOrNull(1)
                        }
                    }

                    val safePrimary = when {
                        player.primaryPosition.isNullOrBlank() -> "CM"
                        player.primaryPosition.equals("SUB", ignoreCase = true) || player.primaryPosition.equals("RES", ignoreCase = true) -> {
                            sec?.takeIf { it.isNotBlank() && !it.equals("SUB", true) && !it.equals("RES", true) } ?: "CM"
                        }
                        else -> player.primaryPosition
                    }

                    val resolvedHeadshot = resolvePlayerHeadshotUrl(
                        playerId = safeId,
                        playerName = safeName,
                        playerDisplayName = player.displayName ?: safeName,
                        isAcademy = player.isAcademy
                    )

                    val resolvedNatName = when {
                        !player.nationality.isNullOrBlank() && player.nationality != "Unknown" && player.nationality.toIntOrNull() == null -> player.nationality
                        player.nationalityId != null && EA_FC_NATION_MAP.containsKey(player.nationalityId) -> EA_FC_NATION_MAP[player.nationalityId]!!
                        player.nationality.toIntOrNull() != null && EA_FC_NATION_MAP.containsKey(player.nationality.toInt()) -> EA_FC_NATION_MAP[player.nationality.toInt()]!!
                        !player.nationality.isNullOrBlank() -> player.nationality
                        else -> "Unknown"
                    }
                    val resolvedNatId = player.nationalityId ?: player.nationality.toIntOrNull() ?: NATION_NAME_TO_ID_MAP[resolvedNatName.lowercase()]

                    player.copy(
                        id = safeId,
                        name = safeName,
                        primaryPosition = safePrimary,
                        secondaryPosition = sec?.takeIf { it.isNotBlank() && it != safePrimary },
                        tertiaryPosition = tert?.takeIf { it.isNotBlank() && it != safePrimary && it != sec },
                        headshotUrl = if (player.headshotUrl.isNullOrBlank()) resolvedHeadshot else player.headshotUrl,
                        form = if (player.form.isNullOrBlank()) "Good" else player.form,
                        morale = if (player.morale.isNullOrBlank()) "Happy" else player.morale,
                        nationality = resolvedNatName,
                        nationalityId = resolvedNatId,
                        birthDate = if (player.birthDate.isNullOrBlank()) "1999-01-01" else player.birthDate,
                        preferredFoot = if (player.preferredFoot.isNullOrBlank()) "Right" else player.preferredFoot,
                        status = if (player.status.isNullOrBlank()) "Active" else player.status
                    )
                }

                val seniorSquad = processedPlayers.filter { !it.isAcademy }
                val youthAcademySquad = processedPlayers.filter { it.isAcademy }

                // 4. Process Past Seasons (Historical Seasons)
                val rawSeasons = importedState.historicalSeasons ?: emptyList()
                val distinctYears = rawSeasons.map { it.year }.filter { it > 1900 }.distinct()
                val hasValidUniqueYears = distinctYears.size == rawSeasons.size && rawSeasons.isNotEmpty()

                val baseStartYear = if (importedState.startYear > 1900) importedState.startYear else 2025
                val processedSeasons = rawSeasons.mapIndexed { index, season ->
                    val assignedYear = if (hasValidUniqueYears) season.year else (if (season.year > 1900) season.year else (baseStartYear + index))
                    val safeLeague = if (!season.leagueName.isNullOrBlank() && season.leagueName != "League") season.leagueName else (importedState.leagueName ?: "League")
                    val safePlayers = (season.players ?: emptyList()).map { sp ->
                        val headshot = resolvePlayerHeadshotUrl(sp.id, sp.name, sp.displayName, isAcademy = sp.isAcademy)
                        sp.copy(headshotUrl = headshot)
                    }
                    val safeMatches = (season.matches ?: emptyList()).map { m ->
                        if (m.opponentCrestUrl.isBlank()) m.copy(opponentCrestUrl = com.fctracker.career.ui.screens.getTeamCrestUrl(m.opponentName)) else m
                    }
                    val safeTransfers = (season.transfers ?: emptyList()).map { t ->
                        if (t.id.isNullOrBlank()) t.copy(id = UUID.randomUUID().toString()) else t
                    }
                    season.copy(
                        id = if (season.id.isNullOrBlank()) UUID.randomUUID().toString() else season.id,
                        year = assignedYear,
                        leagueName = safeLeague,
                        players = safePlayers,
                        matches = safeMatches,
                        transfers = safeTransfers
                    )
                }

                // Active season year is the year after the latest completed season, or startYear if in season 1
                val maxPastYear = processedSeasons.map { it.year }.maxOrNull()
                val activeCurrentYear = when {
                    maxPastYear != null -> maxPastYear + 1
                    importedState.startYear > 1900 -> importedState.startYear
                    else -> 2025
                }

                // 5. Process Trophies
                val trophiesList = (importedState.teamTrophies ?: emptyList()).map { trophy ->
                    if (trophy.id.isNullOrBlank()) trophy.copy(id = UUID.randomUUID().toString()) else trophy
                }.toMutableList()
                val existingTrophyNames = trophiesList.map { it.name.lowercase().trim() }.toSet()

                processedSeasons.forEach { season ->
                    // ONLY award champions trophy if they truly finished 1st
                    if (season.finalPosition == 1) {
                        val leagueTitle = "${season.leagueName} Champions (${season.year})"
                        if (!existingTrophyNames.contains(leagueTitle.lowercase().trim())) {
                            trophiesList.add(
                                TeamTrophy(
                                    name = leagueTitle,
                                    iconUrl = ""
                                )
                            )
                        }
                    }
                }

                // 6. Set state with current season as active (viewingSeasonYear = null)
                val processedRecentMatches = (importedState.recentMatches ?: emptyList()).map { m ->
                    if (m.opponentCrestUrl.isBlank()) m.copy(opponentCrestUrl = com.fctracker.career.ui.screens.getTeamCrestUrl(m.opponentName)) else m
                }
                val processedTransfers = (importedState.transfers ?: emptyList()).map { t ->
                    if (t.id.isNullOrBlank()) t.copy(id = UUID.randomUUID().toString()) else t
                }

                val resolvedCountry = when {
                    !importedState.country.isNullOrBlank() -> importedState.country
                    importedState.leagueName.contains("Premier", true) || importedState.leagueName.contains("Championship", true) || importedState.leagueName.contains("League One", true) || importedState.leagueName.contains("League Two", true) || importedState.leagueName.contains("EFL", true) -> "England"
                    importedState.leagueName.contains("Serie", true) -> "Italy"
                    importedState.leagueName.contains("Ligue", true) -> "France"
                    importedState.leagueName.contains("Bundesliga", true) || importedState.leagueName.contains("Liga 3", true) -> "Germany"
                    importedState.leagueName.contains("La Liga", true) || importedState.leagueName.contains("Segunda", true) -> "Spain"
                    importedState.leagueName.contains("Saudi", true) -> "Saudi"
                    importedState.leagueName.contains("MLS", true) || importedState.leagueName.contains("USL", true) -> "America"
                    importedState.leagueName.contains("Liga Portugal", true) -> "Portugal"
                    importedState.leagueName.contains("Eredivisie", true) -> "Netherlands"
                    else -> _state.value.country.ifBlank { "England" }
                }

                val finalState = importedState.copy(
                    recentMatches = processedRecentMatches,
                    clubName = sanitizedClubName,
                    startYear = activeCurrentYear,
                    country = resolvedCountry,
                    primaryColorHex = resolvedPrimary,
                    secondaryColorHex = resolvedSecondary,
                    players = processedPlayers,
                    academyPlayers = youthAcademySquad,
                    transfers = processedTransfers,
                    historicalSeasons = processedSeasons,
                    teamTrophies = trophiesList,
                    leagueTable = importedState.leagueTable ?: LeagueTable(),
                    financialBreakdown = importedState.financialBreakdown ?: FinancialBreakdown(),
                    objectives = importedState.objectives ?: emptyList(),
                    scoutReports = importedState.scoutReports ?: emptyList(),
                    liveEvents = importedState.liveEvents ?: emptyList(),
                    viewingSeasonYear = null,
                    isOnboarded = true
                )
                _state.value = finalState
                saveSlots[activeSaveSlot] = finalState
                persistData()
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }


    
    fun setViewingSeasonYear(year: Int?) {
        _state.update { it.copy(viewingSeasonYear = year) }
    }

    fun endSeason() {
        _state.update { state ->
            val finalPosition = state.recentMatches.filter { it.competition == state.leagueName }.maxByOrNull { it.matchDate }?.leaguePosition ?: 1
            val completedSeason = HistoricalSeason(
                year = state.startYear,
                leagueName = state.leagueName,
                finalPosition = finalPosition,
                matches = state.recentMatches,
                players = state.players,
                transfers = state.transfers
            )
            
            // Reset for new season
            val resetPlayers = state.players.map { 
                val birthYear = try { it.birthDate?.split("-")?.get(0)?.toInt() ?: 2000 } catch(e: Exception) { 2000 }
                val newAge = (state.startYear + 1) - birthYear
                it.copy(matchesPlayed = 0, goals = 0, assists = 0, age = newAge) 
            }
            
            state.copy(
                historicalSeasons = state.historicalSeasons + completedSeason,
                recentMatches = emptyList(),
                transfers = emptyList(),
                players = resetPlayers,
                startYear = state.startYear + 1
            )
        }
    }

    fun addMatchResult(match: MatchResult) {
        _state.update { state ->
            val updatedPlayers = state.players.map { player ->
                if (player.isInjured && player.injuryReturnDate != null && match.matchDate >= player.injuryReturnDate) {
                    player.copy(isInjured = false, injuryName = null, injuryStartDate = null, injuryReturnDate = null)
                } else {
                    player
                }
            }
            state.copy(
                recentMatches = listOf(match) + state.recentMatches,
                players = updatedPlayers
            )
        }
    }
    
    fun updateMatchResult(match: MatchResult) {
        _state.update { state -> 
            val updated = state.recentMatches.map { if (it.id == match.id) match else it }
            state.copy(recentMatches = updated)
        }
    }
    
    fun deleteMatchResult(matchId: String) {
        _state.update { state ->
            val updated = state.recentMatches.filter { it.id != matchId }
            state.copy(recentMatches = updated)
        }
    }

    fun updateFinancialBreakdown(fb: FinancialBreakdown) {
        _state.update { it.copy(financialBreakdown = fb) }
    }
    fun updateLeagueTable(lt: LeagueTable) {
        _state.update { it.copy(leagueTable = lt) }
    }
    fun updateObjective(objective: Objective) {
        _state.update { current ->
            current.copy(objectives = current.objectives.map { if (it.id == objective.id) objective else it })
        }
    }

    fun deleteObjective(id: String) {
        _state.update { current ->
            current.copy(objectives = current.objectives.filter { it.id != id })
        }
    }

    fun addPlayer(player: Player) {
        _state.update { it.copy(players = it.players + player) }
    }

    fun deletePlayer(id: String) {
        _state.update { current ->
            current.copy(players = current.players.filter { it.id != id })
        }
    }

    fun updatePlayer(player: Player) {
        _state.update { current ->
            val oldPlayer = current.players.find { it.id == player.id }
            var updatedTransfers = current.transfers
            if (player.status.equals("Retired", ignoreCase = true) && oldPlayer?.status != "Retired") {
                if (updatedTransfers.none { (it.playerName == player.displayName || it.playerName == player.name) && it.type.equals("Retirement", ignoreCase = true) }) {
                    val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                    cal.set(java.util.Calendar.YEAR, current.startYear)
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                    val dateStr = sdf.format(cal.time)
                    updatedTransfers = updatedTransfers + TransferLog(
                        id = UUID.randomUUID().toString(),
                        playerName = player.displayName,
                        type = "Retirement",
                        feeM = 0.0,
                        fromToClub = "Retired",
                        date = dateStr
                    )
                }
            }
            current.copy(
                players = current.players.map { if (it.id == player.id) player else it },
                transfers = updatedTransfers
            )
        }
    }

        fun swapPlayers(id1: String, id2: String) {
        _state.update { current ->
            val list = current.players.toMutableList()
            val idx1 = list.indexOfFirst { it.id == id1 }
            val idx2 = list.indexOfFirst { it.id == id2 }
            if (idx1 != -1 && idx2 != -1) {
                val temp = list[idx1]
                list[idx1] = list[idx2]
                list[idx2] = temp
            }
            current.copy(players = list)
        }
    }

    fun updatePlayers(updatedPlayers: List<Player>) {
        _state.update { current ->
            val updatedMap = updatedPlayers.associateBy { it.id }
            current.copy(players = current.players.map { updatedMap[it.id] ?: it })
        }
    }

    private val prefs: SharedPreferences = getApplication<android.app.Application>().getSharedPreferences("fc_manager_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    init {
        loadPersistedData()
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            loadAllEafcPlayers()
        }
        viewModelScope.launch {
            var lastSavedState: AppState? = null
            var lastSavedNotes: List<Note>? = null
            while (true) {
                delay(3000)
                val currentState = _state.value
                val currentNotes = savedNotes.toList()
                if (currentState != lastSavedState || currentNotes != lastSavedNotes) {
                    persistData()
                    lastSavedState = currentState
                    lastSavedNotes = currentNotes
                }
            }
        }
    }

    private fun persistData() {
        saveSlots[activeSaveSlot] = _state.value
        if (!saveSlotNames.containsKey(activeSaveSlot)) {
            saveSlotNames[activeSaveSlot] = "Save Slot $activeSaveSlot"
        }
        
        val slotsJson = gson.toJson(saveSlots)
        val namesJson = gson.toJson(saveSlotNames.toMap())
        val notesJson = gson.toJson(savedNotes.toList())
        
        prefs.edit()
            .putInt("activeSaveSlot", activeSaveSlot)
            .putString("saveSlots", slotsJson)
            .putString("saveSlotNames", namesJson)
            .putString("savedNotes", notesJson)
            .apply()
    }

    private fun loadPersistedData() {
        activeSaveSlot = prefs.getInt("activeSaveSlot", 1)
        
        val slotsJson = prefs.getString("saveSlots", null)
        if (slotsJson != null) {
            val type = object : TypeToken<Map<Int, AppState>>() {}.type
            try {
                val loadedSlots: Map<Int, AppState> = gson.fromJson(slotsJson, type)
                saveSlots.clear()
                saveSlots.putAll(loadedSlots)
            } catch (e: Exception) {}
        }
        
        val namesJson = prefs.getString("saveSlotNames", null)
        if (namesJson != null) {
            val type = object : TypeToken<Map<Int, String>>() {}.type
            try {
                val loadedNames: Map<Int, String> = gson.fromJson(namesJson, type)
                saveSlotNames.clear()
                saveSlotNames.putAll(loadedNames)
            } catch (e: Exception) {}
        }
        
        val notesJson = prefs.getString("savedNotes", null)
        if (notesJson != null) {
            val type = object : TypeToken<List<Note>>() {}.type
            try {
                val loadedNotes: List<Note> = gson.fromJson(notesJson, type)
                savedNotes.clear()
                savedNotes.addAll(loadedNotes)
            } catch (e: Exception) {}
        }
        
        if (saveSlots.containsKey(activeSaveSlot)) {
            _state.value = saveSlots[activeSaveSlot]!!
        }
    }
}

private fun parseMatchPerformances(array: org.json.JSONArray?, players: List<Player>): List<PlayerPerformance> {
    if (array == null) return emptyList()
    val list = mutableListOf<PlayerPerformance>()
    for (i in 0 until array.length()) {
        val p = array.optJSONObject(i) ?: continue
        val pName = p.optString("playerName")
        val pid = p.optString("playerId")
        val matchedPlayer = players.find { 
            (pid.isNotBlank() && it.id == pid) ||
            it.displayName.equals(pName, ignoreCase = true) ||
            it.name.equals(pName, ignoreCase = true)
        }
        val mappedId = matchedPlayer?.id ?: pid.ifBlank { pName }
        val pos = matchedPlayer?.primaryPosition?.uppercase() ?: "CM"
        val isGk = pos == "GK"
        val isDef = pos in listOf("CB", "LB", "RB", "LWB", "RWB")
        val isMid = pos in listOf("CM", "CDM", "CAM", "LM", "RM")

        val rawPasses = p.optInt("passes", 0)
        val defaultPasses = when {
            isMid -> 48 + (i * 3) % 20
            isDef -> 38 + (i * 2) % 15
            isGk -> 22 + (i * 2) % 8
            else -> 26 + (i * 3) % 12
        }
        val passes = if (rawPasses > 0) rawPasses else defaultPasses
        val passAccuracy = if (p.has("passAccuracy") && p.optInt("passAccuracy") > 0) p.optInt("passAccuracy") else 85
        
        val rawShots = p.optInt("shots", 0)
        val defaultShots = if (!isGk && !isDef) (1 + (i % 3)) else 0
        val shots = if (p.has("shots")) rawShots else defaultShots
        val shotAccuracy = if (shots > 0) (if (p.has("shotAccuracy") && p.optInt("shotAccuracy") > 0) p.optInt("shotAccuracy") else 65) else 0

        val rawTackles = p.optInt("tackles", 0)
        val defaultTackles = if (!isGk) (2 + (i % 3)) else 0
        val tackles = if (p.has("tackles")) rawTackles else defaultTackles
        val tacklePct = if (tackles > 0) (if (p.has("tacklePercentage") && p.optInt("tacklePercentage") > 0) p.optInt("tacklePercentage") else 75) else 0
        val interceptions = if (p.has("interceptions")) p.optInt("interceptions") else (if (!isGk) (1 + (i % 3)) else 0)

        list.add(PlayerPerformance(
            playerId = mappedId,
            rating = if (p.has("rating")) p.optDouble("rating", 7.0) else 7.0,
            goals = p.optInt("goals", 0),
            assists = p.optInt("assists", 0),
            passes = passes,
            passAccuracy = passAccuracy,
            shots = shots,
            shotAccuracy = shotAccuracy,
            saves = p.optInt("saves", 0),
            penaltySaves = p.optInt("penaltySaves", 0),
            tackles = tackles,
            tacklePercentage = tacklePct,
            interceptions = interceptions,
            subInMinute = if (p.has("subInMinute")) p.optInt("subInMinute") else null,
            subOutMinute = if (p.has("subOutMinute")) p.optInt("subOutMinute") else null,
            yellowCard = p.optBoolean("yellowCard", false),
            redCard = p.optBoolean("redCard", false),
            cleanSheet = p.optBoolean("cleanSheet", false),
            isMotm = p.optBoolean("isMotm", false)
        ))
    }
    return list
}

private fun parseGoalDetails(array: org.json.JSONArray?, players: List<Player>): List<GoalDetail> {
    if (array == null) return emptyList()
    val list = mutableListOf<GoalDetail>()
    for (i in 0 until array.length()) {
        val p = array.optJSONObject(i) ?: continue
        val pName = p.optString("playerName")
        val aName = p.optString("assisterName")
        val mappedId = players.find { 
            it.displayName.equals(pName, ignoreCase = true) || it.name.equals(pName, ignoreCase = true)
        }?.id ?: pName
        val mappedAssisterId = if (aName.isNotBlank()) {
            players.find { 
                it.displayName.equals(aName, ignoreCase = true) || it.name.equals(aName, ignoreCase = true)
            }?.id ?: aName
        } else null
        val isOpp = p.optBoolean("isOpponent")
        list.add(GoalDetail(
            scorerId = if (isOpp) null else mappedId,
            assisterId = mappedAssisterId,
            minute = if (p.has("minute")) p.optInt("minute") else null
        ))
    }
    return list
}

private fun parseSubstitutions(array: org.json.JSONArray?, players: List<Player>): List<Substitution> {
    if (array == null) return emptyList()
    val list = mutableListOf<Substitution>()
    for (i in 0 until array.length()) {
        val s = array.optJSONObject(i) ?: continue
        val pOutName = s.optString("playerOutName")
        val pInName = s.optString("playerInName")
        val pOutId = players.find { 
            it.displayName.equals(pOutName, ignoreCase = true) || it.name.equals(pOutName, ignoreCase = true)
        }?.id ?: s.optString("playerOutId", pOutName)
        val pInId = players.find { 
            it.displayName.equals(pInName, ignoreCase = true) || it.name.equals(pInName, ignoreCase = true)
        }?.id ?: s.optString("playerInId", pInName)
        list.add(Substitution(
            minute = s.optInt("minute", 65),
            playerOutId = pOutId,
            playerInId = pInId
        ))
    }
    return list
}
