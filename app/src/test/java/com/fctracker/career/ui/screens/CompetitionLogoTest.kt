package com.fctracker.career.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionLogoTest {

    private fun first(competition: String): LogoSource? = competitionLogoSources(competition).firstOrNull()

    @Test
    fun theCarabaoCupAndTheChampionshipNeverShareALogo() {
        assertEquals(LogoSource.WikipediaPage("EFL Cup"), first("Carabao Cup"))
        assertEquals(LogoSource.WikipediaPage("EFL Cup"), first("EFL Cup"))
        assertEquals(LogoSource.WikipediaPage("EFL Championship"), first("EFL Championship"))
        assertNotEquals(first("Carabao Cup"), first("EFL Championship"))
        assertNotEquals(first("League One"), first("EFL Championship"))
        assertNotEquals(first("League Two"), first("League One"))
        assertNotEquals(first("FA Cup"), first("Carabao Cup"))
    }

    @Test
    fun theChampionshipIsNotTheChampionsLeague() {
        assertEquals(LogoSource.WikipediaPage("EFL Championship"), first("Championship"))
        assertEquals(LogoSource.WikipediaPage("UEFA Champions League"), first("UEFA Champions League"))
        assertEquals(LogoSource.WikipediaPage("UEFA Europa League"), first("Europa League"))
    }

    @Test
    fun everyKnownCompetitionHasALogoSourceOfItsOwn() {
        val names = listOf(
            "Premier League", "EFL Championship", "League One", "League Two", "FA Cup", "Carabao Cup", "EFL Trophy",
            "La Liga", "Copa del Rey", "Bundesliga", "DFB-Pokal", "Serie A", "Coppa Italia", "Ligue 1", "Coupe de France",
            "UEFA Champions League", "Europa League", "Conference League"
        )
        val firsts = names.map { first(it) }
        assertTrue(firsts.all { it != null })
        assertEquals("each competition looks up its own page", names.size, firsts.toSet().size)
    }

    @Test
    fun placeholdersAndFriendliesHaveNoLogo() {
        listOf("", "League", "Cup", "Domestic Cup", "Continental", "Friendlies", "Club Friendly")
            .forEach { assertTrue("'$it'", competitionLogoSources(it).isEmpty()) }
    }
}
