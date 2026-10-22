package com.fctracker.career.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fctracker.career.AppState
import com.fctracker.career.FcApplication
import com.fctracker.career.GoalDetail
import com.fctracker.career.HistoricalSeason
import com.fctracker.career.MainViewModel
import com.fctracker.career.MatchResult
import com.fctracker.career.Player
import com.fctracker.career.PlayerPerformance
import com.fctracker.career.TransferLog
import com.fctracker.career.ui.components.FcOverlayHost
import com.fctracker.career.ui.components.FcOverlays
import com.fctracker.career.ui.components.LocalFcOverlays
import com.fctracker.career.ui.screens.CareerSummaryScreen
import com.fctracker.career.ui.screens.EditPlayerDialog
import com.fctracker.career.ui.screens.HeadToHeadPage
import com.fctracker.career.ui.screens.MatchFormModal
import com.fctracker.career.ui.screens.MatchesScreen
import com.fctracker.career.ui.screens.PlayerProfileScreen
import com.fctracker.career.ui.screens.SquadScreen
import com.fctracker.career.ui.screens.TransferFormModal
import com.fctracker.career.ui.screens.TransfersScreen
import com.fctracker.career.ui.screens.formationsMap
import com.fctracker.career.ui.theme.FCTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * The real screens drawn on the JVM and saved as pictures (app/build/screenshots), so a layout can be checked against
 * the design without a phone. Run with: gradle :app:testDebugUnitTest -Pscreenshots. The window is very tall, so a
 * whole scrolling page shows in one picture.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w400dp-h2400dp-xhdpi")
class ScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val out = File(System.getProperty("screenshots.dir") ?: "build/screenshots").apply { mkdirs() }

    /**
     * Pictures are not fetched: every test gets its own image loader that fails at once, so no screen waits on the
     * network (a loader left over from an earlier test, with requests hanging on a CDN, kept the last tests of a run
     * from ever going idle) and the screens show their stand-in pictures.
     */
    @Before fun offlineImages() {
        val app = RuntimeEnvironment.getApplication()
        val http = okhttp3.OkHttpClient.Builder()
            .addInterceptor { throw java.io.IOException("no network in the screenshot tests") }
            .build()
        coil.Coil.setImageLoader(
            coil.ImageLoader.Builder(app).okHttpClient(http).components { add(coil.decode.SvgDecoder.Factory()) }.crossfade(false).allowHardware(false).build()
        )
    }

    // -- a small career ------------------------------------------------------------------------------------------------

    private val starters = listOf(
        Player(id = "gk", name = "Tom Hale", primaryPosition = "GK", overall = 74, number = 1, birthDate = "1996-02-02", nationality = "England"),
        Player(id = "rb", name = "Jamie Ward", primaryPosition = "RB", overall = 71, number = 2, birthDate = "1999-04-11", nationality = "England"),
        Player(id = "cb1", name = "Luca Ferri", primaryPosition = "CB", overall = 75, number = 5, birthDate = "1997-08-21", nationality = "Italy"),
        Player(id = "cb2", name = "Ben Okafor", primaryPosition = "CB", overall = 73, number = 6, birthDate = "2000-01-30", nationality = "Nigeria"),
        Player(id = "lb", name = "Sam Price", primaryPosition = "LB", overall = 70, number = 3, birthDate = "2001-06-06", nationality = "Wales"),
        Player(id = "cm1", name = "Pedro González López", nickname = "Pedri", primaryPosition = "CM", overall = 86, number = 8, birthDate = "2002-11-25", nationality = "Spain", isLoanedIn = true, loanDirection = "in", loanClub = "FC Barcelona"),
        Player(id = "cm2", name = "Marco Varela", primaryPosition = "CDM", overall = 76, number = 4, birthDate = "1998-03-03", nationality = "Spain"),
        Player(id = "cm3", name = "Kai Moreno", primaryPosition = "CAM", overall = 72, number = 10, birthDate = "2004-09-09", nationality = "Spain"),
        Player(id = "lw", name = "Ryo Sato", primaryPosition = "LW", overall = 73, number = 11, birthDate = "2003-05-05", nationality = "Japan",
            isInjured = true, injuryName = "Hamstring strain", injuryStartDate = "2028-10-01", injuryReturnDate = "2028-11-05", injurySource = "game",
            wageK = 22, squadRole = "Important", contractEnd = "2030-06-30"),
        Player(id = "st", name = "Leo Guerra", primaryPosition = "ST", overall = 77, number = 9, birthDate = "2004-03-03", nationality = "Argentina"),
        Player(id = "rw", name = "Diego Ruiz", primaryPosition = "RW", overall = 72, number = 7, birthDate = "2002-12-12", nationality = "Mexico",
            injuryHistory = listOf(com.fctracker.career.InjuryRecord("Ankle sprain", "2028-09-10", "2028-10-05", "game")))
    )

    private fun squad(): List<Player> {
        val slots = formationsMap.getValue("4-3-3")
        val order = listOf("gk", "lb", "cb1", "cb2", "rb", "cm2", "cm1", "cm3", "lw", "st", "rw")
        val placed = starters.map { p ->
            val slot = slots[order.indexOf(p.id).coerceIn(0, slots.size - 1)]
            p.copy(isStartingXI = true, pitchX = slot.x, pitchY = slot.y)
        }
        return placed + listOf(
            Player(id = "gk2", name = "Adam Kerr", primaryPosition = "GK", overall = 65, number = 13, birthDate = "1995-01-20", nationality = "Scotland"),
            Player(id = "cb3", name = "Josh Mills", primaryPosition = "CB", overall = 66, number = 15, birthDate = "2003-02-14", nationality = "England"),
            Player(id = "cm4", name = "Elliot Shaw", primaryPosition = "CM", overall = 67, number = 16, birthDate = "2001-07-07", nationality = "England"),
            Player(id = "new", name = "Nico Striker", overall = 72),     // a signing made with guessed details (CM, 1 Jan 1999)
            Player(id = "regen", name = "Txus Mendia", primaryPosition = "CDM", overall = 69, number = 21, birthDate = "2010-05-01", nationality = "Spain",
                isRegen = true, regenOriginalName = "Sergio Busquets (Sergio Busquets Burgos)"),
            Player(id = "promo", name = "Jordan Anderson", primaryPosition = "ST", overall = 64, number = 29, birthDate = "2011-03-03", nationality = "England",
                isPromoted = true, promotedSeasonYear = 2028),
            Player(id = "away", name = "Elivélton Jesus", primaryPosition = "LM", overall = 68, number = 17, birthDate = "2005-05-05", nationality = "Brazil",
                status = "Loaned Out", isLoanedOut = true, loanDirection = "out", loanClub = "FC Porto")
        )
    }

    private val leicester = MatchResult(
        id = "m1", opponentName = "Leicester City", isHome = true, goalsFor = 3, goalsAgainst = 1, matchDate = "2028-10-12",
        competition = "EFL Championship", stage = "Matchday 12", possession = 54, possessionConfirmed = true, shotsFor = 14, shotsAgainst = 7,
        expectedGoals = 2.4, expectedGoalsAgainst = 0.9, motmName = "Leo Guerra",
        goalDetails = listOf(GoalDetail(scorerId = "st", minute = 12), GoalDetail(scorerId = "st", minute = 55), GoalDetail(scorerId = "cm3", minute = 80)),
        playerPerformances = listOf(
            PlayerPerformance(playerId = "gk", rating = 6.9, position = "GK"),
            PlayerPerformance(playerId = "rb", rating = 6.8, position = "RB", yellowCard = true),
            PlayerPerformance(playerId = "cb1", rating = 7.2, position = "CB"),
            PlayerPerformance(playerId = "cb2", rating = 7.0, position = "CB"),
            PlayerPerformance(playerId = "lb", rating = 6.7, position = "LB", subOutMinute = 70),
            PlayerPerformance(playerId = "cm2", rating = 7.1, position = "CDM"),
            PlayerPerformance(playerId = "cm1", rating = 7.8, position = "CM", assists = 2),
            PlayerPerformance(playerId = "cm3", rating = 7.4, position = "CAM", goals = 1),
            PlayerPerformance(playerId = "lw", rating = 7.0, position = "LW", assists = 1),
            PlayerPerformance(playerId = "st", rating = 8.6, position = "ST", goals = 2, isMotm = true),
            PlayerPerformance(playerId = "rw", rating = 6.6, position = "RW"),
            PlayerPerformance(playerId = "cb3", rating = 6.5, position = "LB", subInMinute = 70)
        )
    )

    private fun career(): AppState = AppState(
        isOnboarded = true, clubName = "Torquay AFC", leagueName = "EFL Championship", country = "England", startYear = 2028,
        managerName = "Joel", activeFormation = "4-3-3", careerDate = "2028-10-20", currencySymbol = "€",
        players = squad(),
        recentMatches = listOf(
            leicester,
            MatchResult(id = "m2", opponentName = "Exeter City", isHome = false, goalsFor = 2, goalsAgainst = 2, matchDate = "2028-09-24",
                competition = "Carabao Cup", stage = "Round 3", isExtraTime = true, homePenalties = 3, awayPenalties = 4),
            MatchResult(id = "m3", opponentName = "Leicester City", isHome = false, goalsFor = 0, goalsAgainst = 2, matchDate = "2028-08-16",
                competition = "EFL Championship", stage = "Matchday 2")
        ),
        historicalSeasons = listOf(
            HistoricalSeason(year = 2027, leagueName = "EFL League One", finalPosition = 2,
                players = listOf(Player(id = "old9", name = "Gone Striker", primaryPosition = "ST", goals = 160, matchesPlayed = 300, status = "Sold"),
                    Player(id = "st", name = "Leo Guerra", primaryPosition = "ST", goals = 3, matchesPlayed = 2),
                    Player(id = "oldgk", name = "Old Keeper", primaryPosition = "GK", overall = 70, matchesPlayed = 8, status = "Sold")),
                transfers = listOf(TransferLog(playerName = "Leo Guerra", type = "In (Permanent)", feeM = 2.0, fromToClub = "Boca", date = "2027-07-10"),
                    TransferLog(playerName = "Leo Guerra (Leonardo Guerra Diaz)", type = "In (Permanent)", feeM = 2.5, fromToClub = "Boca", date = "2027-07-10")),
                matches = listOf(
                MatchResult(id = "h1", opponentName = "Leicester City", isHome = true, goalsFor = 1, goalsAgainst = 1, matchDate = "2028-02-10", competition = "EFL Championship", stage = "Matchday 30"),
                MatchResult(id = "h2", opponentName = "Leicester City", isHome = false, goalsFor = 2, goalsAgainst = 1, matchDate = "2027-09-01", competition = "EFL Championship", stage = "Matchday 5")
            ))
        ),
        transfers = listOf(
            TransferLog(playerName = "Pedri (Pedro González López)", type = "In (Loan)", fromToClub = "FC Barcelona", date = "2028-08-02"),
            TransferLog(playerName = "Nico Striker", type = "In (Permanent)", feeM = 3.5, fromToClub = "Leeds United", date = "2028-08-01"),
            TransferLog(playerName = "Elivélton Jesus", type = "Out (Loan)", fromToClub = "FC Porto", date = "2028-08-20")
        )
    )

    private fun viewModel(state: AppState = career()): MainViewModel {
        val app = RuntimeEnvironment.getApplication() as FcApplication
        app.store.state.value = state
        return MainViewModel(app)
    }

    /** The content in the app's theme, with full-screen pages drawn over it the way the app does. */
    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            FCTheme(primaryHex = "#FFD400", secondaryHex = "#1F4FA3", useClubColours = true) {
                val overlays = remember { FcOverlays() }
                CompositionLocalProvider(LocalFcOverlays provides overlays) {
                    Box(Modifier.fillMaxSize()) {
                        content()
                        FcOverlayHost(overlays)
                    }
                }
            }
        }
    }

    private fun shoot(name: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width.coerceAtLeast(1), view.height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    // -- the screens ---------------------------------------------------------------------------------------------------

    @Test fun squadRoster() {
        val vm = viewModel()
        show { SquadScreen(vm, {}, {}) }
        shoot("01_squad_roster")
    }

    @Test fun matches() {
        val vm = viewModel()
        show { MatchesScreen(vm, {}) }
        shoot("02_matches")
    }

    @Test fun logMatchNew() {
        val vm = viewModel()
        show { MatchFormModal(vm, vm.state.value, null, {}, {}) }
        shoot("03_log_match_new")
    }

    @Test fun matchDetails() {
        val vm = viewModel()
        show { MatchFormModal(vm, vm.state.value, leicester, {}, {}, onHeadToHead = {}) }
        shoot("04_match_details")
    }

    @Test fun lineup() {
        val vm = viewModel()
        show { MatchFormModal(vm, vm.state.value, leicester, {}, {}, onHeadToHead = {}, initialPage = 1) }
        shoot("05_lineup")
    }

    @Test fun headToHead() {
        val vm = viewModel()
        show { HeadToHeadPage(vm.state.value, "Leicester City", {}, {}) }
        shoot("06_head_to_head")
    }

    @Test fun logDealOut() {
        val vm = viewModel()
        show { TransferFormModal(vm.state.value, vm, null, "Out (Permanent)", {}) }
        shoot("07_log_deal_out")
    }

    @Test fun editPlayer() {
        val vm = viewModel()
        show { EditPlayerDialog(2028, vm.state.value.players.first { it.id == "regen" }, emptyList(), {}, {}, referenceDate = "2028-10-20") }
        shoot("08_edit_player")
    }

    @Test fun profileOfAPromotedPlayer() {
        val vm = viewModel()
        show { PlayerProfileScreen(vm, "promo", {}) }
        shoot("09_profile_promoted")
    }

    @Test fun transfers() {
        val vm = viewModel()
        show { TransfersScreen(vm, {}) }
        shoot("11_transfers")
    }

    @Test fun wagesAfterSorting() {
        val vm = viewModel()
        show { SquadScreen(vm, {}, {}) }
        compose.onNodeWithText("Wages").performClick()
        shoot("12_wages")
        compose.onNodeWithText("Squad role").performClick()
        shoot("13a_wages_by_role")
        compose.onNodeWithText("Contract end").performClick()
        shoot("13b_wages_by_contract")
        compose.onNodeWithText("Highest wage").performClick()
        shoot("13_wages_after_sorting")
    }

    @Test fun formationAndInjuries() {
        val vm = viewModel()
        show { SquadScreen(vm, {}, {}) }
        compose.onNodeWithText("Formation").performClick()
        shoot("14_formation")
        compose.onNodeWithText("Injuries").performClick()
        shoot("15_injuries")
    }

    // a taller window: the all-time squad list is at the bottom of a long page
    @Config(qualifiers = "w400dp-h4000dp-xhdpi")
    @Test fun careerSummary() {
        val vm = viewModel()
        show { CareerSummaryScreen(vm, {}, {}) }
        shoot("16_career_summary")
    }

    @Test fun profileOfALoanPlayer() {
        val vm = viewModel()
        show { PlayerProfileScreen(vm, "cm1", {}) }
        shoot("10_profile_loan")
    }
}
