package com.fctracker.career.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerDatabaseTest {
    private val sample = """
        [
          {"id":"209331","name":"Mohamed Salah","commonName":"","birthdate":"6/15/1992 12:00:00 AM","position":"RM",
           "alternatePositions":"RW,ST","nationality":"Egypt","team":"Liverpool","overallRating":91,"potential":91,"value":75.5,"wage":300},
          {"id":"1","name":"Danilo Pereira","commonName":"Danilo","birthdate":"9/9/1991 12:00:00 AM","position":"CB",
           "alternatePositions":"","nationality":"Portugal","team":"PSG","overallRating":80},
          {"id":"2","name":"Danilo Luiz","commonName":"Danilo","birthdate":"7/15/1991 12:00:00 AM","position":"RB",
           "alternatePositions":"","nationality":"Brazil","team":"Juventus","overallRating":79},
          {"id":"3","name":"Kylian Mbappé","commonName":"","birthdate":"12/20/1998 12:00:00 AM","position":"ST",
           "alternatePositions":"LW","nationality":"France","team":"Real Madrid","overallRating":92}
        ]
    """

    private val db = PlayerDatabase { sample }

    @Test
    fun readsTheBundledFileFormat() {
        assertEquals(4, db.all.size)
        val salah = db.all.first { it.name == "Mohamed Salah" }
        assertEquals(209331, salah.id)
        assertEquals(91, salah.rating)
        assertEquals("RW,ST", salah.alternatePositions)
    }

    @Test
    fun birthDatesBecomeIsoAndAgesAreCountedByYear() {
        assertEquals("1992-06-15", PlayerDatabase.isoBirthDate("6/15/1992 12:00:00 AM"))
        assertEquals("1992-06-05", PlayerDatabase.isoBirthDate("6/5/1992"))
        assertEquals("1999-01-01", PlayerDatabase.isoBirthDate("1999-01-01"))
        assertEquals(33, PlayerDatabase.ageIn(2025, "1992-06-15")) // 2025 - 1992
        assertEquals(25, PlayerDatabase.ageIn(2025, "garbage")) // unreadable date keeps the old default
    }

    @Test
    fun anImportedPlayerGetsARealAgeNotAlways25() {
        val salah = PlayerDatabase.toPlayer(db.all.first { it.name == "Mohamed Salah" }, 2025)
        assertEquals(33, salah.age)
        assertEquals("1992-06-15", salah.birthDate)
        assertEquals("RW", salah.secondaryPosition)
        assertEquals("ST", salah.tertiaryPosition)
        assertEquals("https://cdn.futbin.com/content/fifa25/img/players/209331.png", salah.headshotUrl)
    }

    @Test
    fun aSigningIsFoundByNameIgnoringAccentsAndCase() {
        val mbappe = db.signedPlayer("kylian MBAPPE", 2026)
        assertNotNull(mbappe)
        assertEquals(92, mbappe!!.overall)
        assertEquals(28, mbappe.age)
    }

    @Test
    fun aNameThatFitsTwoPlayersIsNotGuessed() {
        assertNull(db.signedPlayer("Danilo", 2026)) // two players use that common name
        assertEquals(80, db.signedPlayer("Danilo Pereira", 2026)!!.overall)
    }

    @Test
    fun unknownAndBlankNamesGiveNothing() {
        assertNull(db.signedPlayer("Nobody Atall", 2026))
        assertNull(db.signedPlayer("  ", 2026))
    }

    @Test
    fun aBrokenFileGivesAnEmptyList() {
        assertEquals(0, PlayerDatabase { "not json" }.all.size)
    }
}
