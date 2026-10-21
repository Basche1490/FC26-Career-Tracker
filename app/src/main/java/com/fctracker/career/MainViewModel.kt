package com.fctracker.career
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewModelScope
import com.fctracker.career.data.CareerStore
import com.fctracker.career.data.PlayerDatabase
import com.google.gson.Gson
import com.fctracker.career.telemetry.Contact
import com.fctracker.career.telemetry.Health
import com.fctracker.career.telemetry.LiveSyncService
import com.fctracker.career.telemetry.NotificationPrefs
import com.fctracker.career.telemetry.TelemetrySync
import com.fctracker.career.logic.CareerOps


class MainViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    private val app = application as FcApplication
    private val store = app.store
    private val sync: TelemetrySync = app.telemetry
    private val playerDb: PlayerDatabase = app.playerDatabase

    /** Tactical notes. Owned by the store so they survive the Activity. */
    val savedNotes get() = store.savedNotes

    private val _pendingRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val pendingRoute: kotlinx.coroutines.flow.StateFlow<String?> = _pendingRoute.asStateFlow()

    fun navigateToScreen(route: String) {
        _pendingRoute.value = route
    }

    fun clearPendingRoute() {
        _pendingRoute.value = null
    }

    /** Saves the bridge address and asks the bridge right away; [onDone] is called when its answer (or its silence) is in. */
    fun saveBridgeAddress(url: String, onDone: (() -> Unit)? = null) {
        val normalized = TelemetrySync.normalizeBridgeUrl(url)
        if (normalized != _state.value.telemetryServerUrl) {
            _state.value = _state.value.copy(telemetryServerUrl = normalized)
            persistData()
            sync.addressChanged()
        }
        recheckConnection { onDone?.invoke() }
    }

    fun updateTelemetryServerUrl(url: String) {
        saveBridgeAddress(url)
    }

    /** The PC bridge's pairing code as stored (letters and digits only, "" when none). */
    val bridgeCode: StateFlow<String> = store.bridgeCode

    /** Saves the address and the pairing code together and asks the bridge right away. */
    fun saveBridgeConnection(url: String, code: String, onDone: (() -> Unit)? = null) {
        val normalizedUrl = TelemetrySync.normalizeBridgeUrl(url)
        val codeChanged = com.fctracker.career.telemetry.BridgeAuth.normalize(code) != store.bridgeCode.value
        if (codeChanged) store.setBridgeCode(code)
        if (normalizedUrl != _state.value.telemetryServerUrl) {
            _state.value = _state.value.copy(telemetryServerUrl = normalizedUrl)
            persistData()
            sync.addressChanged()
        } else if (codeChanged) {
            sync.pairingChanged()
        }
        recheckConnection { onDone?.invoke() }
    }

    /** "Forget this PC": the pairing code is deleted from the phone; the bridge then refuses this phone until a code is typed again. */
    fun forgetPc() {
        store.setBridgeCode("")
        sync.pairingChanged()
        recheckConnection()
    }

    // -- connection health (the status pill, the Connection screen and the setup guide) ---------------

    /** One answer to "is everything working?": Live, Check or Offline, with the Phone / Bridge / Script steps behind it. */
    val health: StateFlow<Health> = sync.health

    /** The raw contact with the bridge: its address, whether it answered, and its own health report. */
    val contact: StateFlow<Contact> = sync.contact

    /** Asks the bridge again right now (the "Check again" / retry button). */
    fun recheckConnection(onDone: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = sync.poll(notify = true)
            if (onDone != null) withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    /** The "Re-send last match" button: the capture script sends its latest match again. */
    fun resendLastMatch(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = sync.resendLatestMatch()
            withContext(Dispatchers.Main) { onResult(result.ok, result.message) }
        }
    }

    /** "Find my PC": looks for the bridge on the phone's Wi-Fi network. */
    fun findMyPc(onResult: (TelemetrySync.FindResult) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = sync.findMyPc()
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    // -- device-level settings: which alerts, and the first-run guide ----------------------------------

    val notificationPrefs: StateFlow<NotificationPrefs> = app.syncPrefs.notificationPrefsFlow

    fun setNotificationPrefs(prefs: NotificationPrefs) {
        app.syncPrefs.notificationPrefs = prefs
    }

    /** True once the first-run setup guide was finished or skipped. */
    val setupGuideDone: StateFlow<Boolean> = app.syncPrefs.setupGuideDoneFlow

    fun setSetupGuideDone(done: Boolean) {
        app.syncPrefs.setupGuideDone = done
    }

    private val _setupPrompt = MutableStateFlow(false)

    /** True right after a new career was set up and the guide has not been finished: the Dashboard opens it once. */
    val setupPrompt: StateFlow<Boolean> = _setupPrompt.asStateFlow()

    fun consumeSetupPrompt() {
        _setupPrompt.value = false
    }
    
    // -- live telemetry ------------------------------------------------------------------------------

    /** The app came on screen: watch the bridge, and keep doing so when it closes if background sync is on. */
    fun onAppStarted() {
        sync.acquire("ui")
        if (app.syncPrefs.backgroundSync) LiveSyncService.start(getApplication())
    }

    /** The app left the screen: stop foreground polling and write everything to disk now. */
    fun onAppStopped() {
        sync.release("ui")
        store.flush()
    }

    val backgroundSync: StateFlow<Boolean> = app.syncPrefs.backgroundSyncFlow

    fun setBackgroundSync(enabled: Boolean) {
        app.syncPrefs.backgroundSync = enabled
        if (enabled) LiveSyncService.start(getApplication()) else LiveSyncService.stop(getApplication())
    }

    fun testBridgeConnection(onResult: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = sync.testConnection()
            if (onResult != null) withContext(Dispatchers.Main) { onResult(result.ok, result.message) }
        }
    }

    fun injectTestMatchEvent() {
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        
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

        sync.apply(listOf(dummyWrapper), notify = true)
    }

    /**
     * Pulls the bridge's events now. Whatever is new raises its phone alert here too: the refresh button and the
     * dashboard's own refresh can fetch a match before the background loop does, and that must not swallow the alert.
     */
    fun fetchLiveTelemetry() {
        viewModelScope.launch(Dispatchers.IO) { sync.poll(notify = true) }
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

        sync.apply(listOf(dummyWrapper), notify = true)
    }
    fun addNote(title: String, content: String) { savedNotes.add(Note(title = title, content = content)); store.metaChanged() }
    fun removeNote(note: Note) { savedNotes.removeAll { it.id == note.id }; store.metaChanged() }
    
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
            store.metaChanged()
        }
    }

    private val _state: MutableStateFlow<AppState> get() = store.state
    val state: StateFlow<AppState> = store.state.asStateFlow()

    /** The AI API key: kept in encrypted storage, never in save files or exported backups. */
    val aiApiKey: StateFlow<String> = store.aiApiKey.asStateFlow()

    

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
                aiApiUrl = aiApiUrl
            )
        }
        store.setAiApiKey(aiApiKey)
        if (existingPlayers.isEmpty()) {
            initializeGameData(isExistingTeam, clubName)
        }
        persistData()
        if (!app.syncPrefs.setupGuideDone && _state.value.platform != Platform.CONSOLE) _setupPrompt.value = true
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
        val list = playerDb.all
        allEafcPlayers = list
        _eafcPlayers.value = list
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
                        
                        val birthdate = PlayerDatabase.isoBirthDate(eafcPlayer.birthdate)
                        val age = PlayerDatabase.ageIn(_state.value.startYear, birthdate)

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

    fun updateSettings(clubName: String, managerName: String, leagueName: String, country: String, currencySymbol: String, primaryColorHex: String, secondaryColorHex: String, clubCrestUrl: String, aiAgent: String = "", aiApiUrl: String = "", aiApiKey: String? = null, aiModel: String = "") {
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
                aiModel = aiModel
            )
        }
        if (aiApiKey != null) store.setAiApiKey(aiApiKey)
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

    
    private fun mapEafcPlayerToPlayer(eafcPlayer: EafcPlayer): Player =
        PlayerDatabase.toPlayer(eafcPlayer, _state.value.startYear)

    fun addTransferLog(log: TransferLog) {
        recordTransfer(log)
        ensureSwapCounterpart(log)
    }

    /** Records the other move of a swap deal as an entry of its own, once (see [swapCounterpart]). */
    private fun ensureSwapCounterpart(log: TransferLog) {
        log.swapCounterpart(_state.value.transfers)?.let { recordTransfer(it) }
    }

    private fun recordTransfer(log: TransferLog) {
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
                        regenOriginalName = log.regenOriginalName,
                        headshotUrl = log.headshotUrl
                    ))
                }
            }
            state.copy(transfers = state.transfers + log, players = newPlayers)
        }
    }
    
    /**
     * Saves an edited transfer. When only the fee, club, date or flags changed, the entry is replaced in place and the
     * squad is left alone (deleting and re-adding an incoming transfer would throw the player away and add a blank card).
     */
    fun updateTransferLog(old: TransferLog, updated: TransferLog) {
        if (old.playerName == updated.playerName && old.type == updated.type) {
            _state.update { state ->
                state.copy(transfers = state.transfers.map { if (it.id == old.id) updated else it })
            }
            ensureSwapCounterpart(updated)
        } else {
            deleteTransferLog(old.id)
            addTransferLog(updated)
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


    
    // Save slots. The data itself is owned by CareerStore (it keeps one file per slot).
    val activeSaveSlot: Int get() = store.activeSaveSlot
    val saveSlots: Map<Int, AppState> get() = store.saveSlots
    val saveSlotNames get() = store.saveSlotNames

    fun switchSaveSlot(slot: Int, createNew: Boolean = false) = store.switchSlot(slot, createNew)
    fun deleteCurrentSave() = store.deleteActiveSlot()
    fun renameSaveSlot(slot: Int, name: String) = store.renameSlot(slot, name)


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
                // Backups from the earlier app carry the AI key inside the file. Move it to encrypted storage,
                // but never replace a key that is already set.
                if (store.aiApiKey.value.isBlank()) {
                    CareerStore.legacyApiKey(json)?.let { store.setAiApiKey(it) }
                }
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

    /** Archives the season with everything its pages show and starts the next one (see [CareerOps.endSeason]). */
    fun endSeason() {
        if (_state.value.viewingSeasonYear != null) return
        _state.update { CareerOps.endSeason(it) }
        persistData()
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
            CareerOps.dropPlayedFixture(state.copy(
                recentMatches = listOf(match) + state.recentMatches,
                players = updatedPlayers
            ), match)
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
            current.copy(players = current.players.filter { it.id != id }, academyPlayers = current.academyPlayers.filter { it.id != id })
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
                academyPlayers = current.academyPlayers.map { if (it.id == player.id) player else it },
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


    // =================================================================================================================
    // The final design
    // =================================================================================================================

    /** Runs a change to the season's data; an archived season is read-only, so nothing happens while one is shown. */
    private fun edit(block: (AppState) -> AppState) {
        if (_state.value.viewingSeasonYear != null) return
        _state.update(block)
    }

    fun setPlatform(platform: String) {
        _state.update { it.copy(platform = if (platform == Platform.CONSOLE) Platform.CONSOLE else Platform.PC) }
        if (platform == Platform.CONSOLE && app.syncPrefs.backgroundSync) setBackgroundSync(false)
        persistData()
    }

    fun setUseClubColours(on: Boolean) { _state.update { it.copy(useClubColours = on) }; persistData() }

    fun setClubColours(primary: String, secondary: String) { _state.update { it.copy(primaryColorHex = primary, secondaryColorHex = secondary) }; persistData() }

    /** Club profile from Settings and onboarding. */
    fun updateClubProfile(
        clubName: String, managerName: String, leagueName: String, country: String, currencySymbol: String,
        primaryColorHex: String, secondaryColorHex: String, clubCrestUrl: String, isExistingTeam: Boolean, useClubColours: Boolean
    ) {
        _state.update {
            it.copy(
                clubName = clubName.trim().ifBlank { it.clubName }, managerName = managerName.trim(), leagueName = leagueName.trim().ifBlank { it.leagueName },
                country = country, currencySymbol = currencySymbol.split(" ")[0], primaryColorHex = primaryColorHex, secondaryColorHex = secondaryColorHex,
                clubCrestUrl = clubCrestUrl.trim(), isExistingTeam = isExistingTeam, useClubColours = useClubColours
            )
        }
        persistData()
    }

    fun updateAiSettings(agent: String, apiUrl: String, apiKey: String?, model: String) {
        _state.update { it.copy(aiAgent = agent, aiApiUrl = apiUrl.trim(), aiModel = model.trim()) }
        if (apiKey != null) store.setAiApiKey(apiKey)
        persistData()
    }

    /** Copies the chosen image into the app's own storage, so it stays when the original is moved or deleted. */
    fun importCrestImage(uri: android.net.Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                val dir = java.io.File(getApplication<android.app.Application>().filesDir, "crests").apply { mkdirs() }
                val target = java.io.File(dir, "crest_${store.activeSaveSlot}_${System.currentTimeMillis()}.img")
                getApplication<android.app.Application>().contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                } ?: throw java.io.IOException("could not open the image")
                if (target.length() > 6_000_000L) { target.delete(); throw java.io.IOException("image too large") }
                val old = _state.value.clubCrestFile
                _state.update { it.copy(clubCrestFile = target.absolutePath) }
                if (old.isNotBlank() && old != target.absolutePath) java.io.File(old).delete()
                persistData()
                true
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) { onDone(ok) }
        }
    }

    fun removeCrestImage() {
        val old = _state.value.clubCrestFile
        _state.update { it.copy(clubCrestFile = "") }
        if (old.isNotBlank()) java.io.File(old).delete()
        persistData()
    }

    fun updateClubInfo(info: ClubInfo) { _state.update { it.copy(clubInfo = info) }; persistData() }

    fun saveOwnHistory(text: String) {
        _state.update { it.copy(clubInfo = it.clubInfo.copy(history = text.trim(), historySource = if (text.isBlank()) "" else "own")) }
        persistData()
    }

    /** A real club's history: the first part of its Wikipedia article (needs the internet). */
    fun fetchWikipediaHistory(onDone: (String?) -> Unit) {
        val club = _state.value.clubName
        viewModelScope.launch(Dispatchers.IO) {
            val result = com.fctracker.career.ai.ClubHistory.fetchWikipedia(club)
            if (result != null) {
                _state.update { it.copy(clubInfo = it.clubInfo.copy(history = result.text, historySource = "wikipedia", historyUrl = result.url)) }
                persistData()
            }
            withContext(Dispatchers.Main) { onDone(result?.text) }
        }
    }

    /** The person's own club: the AI writes a history from their answers (only the answers and the facts are sent). */
    fun writeHistoryWithAi(answers: Map<String, String>, onDone: (String?, String?) -> Unit) {
        val s = _state.value
        viewModelScope.launch(Dispatchers.IO) {
            val (system, prompt) = com.fctracker.career.ai.ClubHistory.aiPrompt(s, answers)
            val reply = com.fctracker.career.ai.AiClient.ask(prompt, s.aiAgent, s.aiApiUrl, store.aiApiKey.value, s.aiModel, system = system)
            val failed = reply.startsWith("Error") || reply.startsWith("API key needed") || reply.startsWith("The AI ") || com.fctracker.career.ai.AiGuard.isOffTopicReply(reply)
            if (!failed) {
                _state.update { it.copy(clubInfo = it.clubInfo.copy(history = reply.trim(), historySource = "ai", aiAnswers = answers)) }
                persistData()
            }
            withContext(Dispatchers.Main) { if (failed) onDone(null, reply) else onDone(reply.trim(), null) }
        }
    }

    fun setWageBudget(k: Double) { _state.update { it.copy(wageBudgetK = k.coerceAtLeast(0.0)) }; persistData() }
    fun setMilestonePrefs(p: MilestonePrefs) { _state.update { it.copy(milestonePrefs = p) }; persistData() }
    fun setRecapExcluded(pages: List<Int>) { _state.update { it.copy(recapExcludedPages = pages.distinct().sorted()) }; persistData() }
    fun dismissWorldReminder(season: Int) { _state.update { it.copy(worldReminderDismissed = season) }; persistData() }

    // -- fixtures, tables, injuries, notes, awards, winners, ranking -----------------------------------------------

    fun addFixture(f: Fixture) { edit { CareerOps.addFixture(it, f) }; persistData() }
    fun updateFixture(f: Fixture) { edit { CareerOps.updateFixture(it, f) }; persistData() }
    fun deleteFixture(id: String) { edit { CareerOps.deleteFixture(it, id) }; persistData() }

    fun saveTableRow(league: String, oldClub: String, row: TableRow) { edit { CareerOps.editRow(it, league, oldClub, row) }; persistData() }
    fun addTableClub(league: String, club: String) {
        edit { s ->
            val t = s.leagueTables.firstOrNull { it.league.equals(league, true) } ?: CareerOps.ownTable(s)
            CareerOps.updateTable(s, com.fctracker.career.logic.LeagueTables.addClub(t, club, club.equals(s.clubName, true)))
        }
        persistData()
    }
    fun removeTableClub(league: String, club: String) {
        edit { s -> s.leagueTables.firstOrNull { it.league.equals(league, true) }?.let { CareerOps.updateTable(s, com.fctracker.career.logic.LeagueTables.removeClub(it, club)) } ?: s }
        persistData()
    }
    fun renameTableClub(league: String, old: String, new: String) {
        edit { s -> s.leagueTables.firstOrNull { it.league.equals(league, true) }?.let { CareerOps.updateTable(s, com.fctracker.career.logic.LeagueTables.renameClub(it, old, new)) } ?: s }
        persistData()
    }
    /** Console: the person's own row from their logged league matches. */
    fun fillOwnRowFromMatches() {
        edit { s ->
            val t = CareerOps.ownTable(s)
            val row = com.fctracker.career.logic.LeagueTables.rowFromMatches(s.clubName, s.recentMatches, s.leagueName)
            val exists = t.rows.any { it.isUser || it.club.equals(s.clubName, true) }
            val rows = if (exists) t.rows.map { if (it.isUser || it.club.equals(s.clubName, true)) row else it } else t.rows + row
            CareerOps.updateTable(s, t.copy(rows = rows, source = Source.MANUAL))
        }
        persistData()
    }
    fun moveLeague(newLeague: String, movedWithMe: List<String>, promoted: Boolean) {
        edit { CareerOps.moveLeague(it, newLeague, movedWithMe, promoted) }
        persistData()
    }
    fun saveLeaders(leaders: CompetitionLeaders) {
        edit { s -> s.copy(leaders = listOf(leaders.copy(source = Source.MANUAL)) + s.leaders.filterNot { it.competition.equals(leaders.competition, true) }) }
        persistData()
    }
    fun applyNewSeasonClubs(leaving: List<String>, promotedIn: List<String>, relegatedIn: List<String>) {
        edit { CareerOps.applyNewSeasonClubs(it, leaving, promotedIn, relegatedIn) }
        persistData()
    }

    fun setInjury(playerId: String, name: String, start: String?, back: String?, followGame: Boolean, note: String?) {
        edit { CareerOps.setInjury(it, playerId, name, start, back, followGame, note) }
        persistData()
    }
    fun clearInjury(playerId: String) { edit { CareerOps.clearInjury(it, playerId) }; persistData() }

    fun addPlayerNote(playerId: String, text: String) { edit { CareerOps.addPlayerNote(it, playerId, text) }; persistData() }
    fun deletePlayerNote(id: String) { edit { CareerOps.deletePlayerNote(it, id) }; persistData() }

    fun addAward(a: AwardEntry) { edit { CareerOps.addAward(it, a) }; persistData() }
    fun deleteAward(id: String) { edit { CareerOps.deleteAward(it, id) }; persistData() }

    fun addWorldWinner(w: WorldWinner) { _state.update { CareerOps.addWorldWinner(it, w) }; persistData() }
    fun deleteWorldWinner(season: Int, competition: String) { _state.update { CareerOps.deleteWorldWinner(it, season, competition) }; persistData() }
    fun addWorldTopScorer(t: WorldTopScorer) { _state.update { CareerOps.addWorldTopScorer(it, t) }; persistData() }

    fun setOwnCoefficient(season: Int, points: Double) { edit { CareerOps.setOwnCoefficient(it, season, points) }; persistData() }
    fun addCoefficientClub(c: ClubCoefficient) {
        edit { s -> s.copy(coefficients = (s.coefficients.filterNot { it.club.equals(c.club, true) } + c).sortedWith(compareBy({ if (it.rank > 0) it.rank else Int.MAX_VALUE }, { -it.total }))) }
        persistData()
    }
    fun removeCoefficientClub(club: String) { edit { s -> s.copy(coefficients = s.coefficients.filterNot { it.club.equals(club, true) }) }; persistData() }

    /** "Use my team sheet from the game": false when the sheet names too few players the app knows. */
    fun useGameTeamSheet(): Boolean {
        val applied = CareerOps.applyTeamSheet(_state.value) ?: return false
        val placed = com.fctracker.career.ui.screens.applySmartFormation(applied.players, applied.activeFormation) ?: applied.players
        _state.update { it.copy(activeFormation = applied.activeFormation, players = placed) }
        persistData()
        return true
    }

    /** Promotes a prospect to the first team, wherever he is kept (the game's academy list or the squad list). */
    fun promoteAcademyPlayer(p: Player) {
        edit { s ->
            val promoted = p.copy(isAcademy = false, isPromoted = true, promotedSeasonYear = s.startYear, status = "Active")
            val inSquad = s.players.any { it.id == p.id }
            s.copy(
                players = if (inSquad) s.players.map { if (it.id == p.id) promoted else it } else s.players + promoted,
                academyPlayers = s.academyPlayers.filterNot { it.id == p.id }
            )
        }
        persistData()
    }

    fun removeAcademyPlayer(id: String) {
        edit { s -> s.copy(players = s.players.filterNot { it.id == id }, academyPlayers = s.academyPlayers.filterNot { it.id == id }) }
        persistData()
    }

    // -- the assistant ----------------------------------------------------------------------------------------------

    /**
     * Answers one message. Questions about anything but the career are answered by the app itself and never sent;
     * the rest go to the AI agent with everything the app holds.
     */
    /** The assistant's conversation, kept while the app is open (not saved). */
    val assistantChat = androidx.compose.runtime.mutableStateListOf<com.fctracker.career.ai.ChatTurn>()

    fun askAssistant(message: String, mode: String, history: List<com.fctracker.career.ai.ChatTurn>, onReply: (String, Boolean) -> Unit) {
        val s = _state.value
        val step = com.fctracker.career.ai.Assistant.prepare(s, savedNotes.toList(), message, mode, history)
        when (step) {
            is com.fctracker.career.ai.Assistant.Step.Local -> onReply(step.reply, true)
            is com.fctracker.career.ai.Assistant.Step.Send -> viewModelScope.launch(Dispatchers.IO) {
                val reply = com.fctracker.career.ai.AiClient.ask(step.prompt, s.aiAgent, s.aiApiUrl, store.aiApiKey.value, s.aiModel, system = step.system, history = step.history)
                val final = com.fctracker.career.ai.Assistant.finish(s, reply)
                withContext(Dispatchers.Main) { onReply(final, final != reply.trim()) }
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) { loadAllEafcPlayers() }
    }

    /** Writes the current career to disk right away (it is also saved by itself shortly after every change). */
    private fun persistData() = store.saveNow()
}
