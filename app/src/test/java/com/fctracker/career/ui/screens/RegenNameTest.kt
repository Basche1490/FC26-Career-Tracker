package com.fctracker.career.ui.screens

import com.fctracker.career.EafcPlayer
import com.fctracker.career.Player
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegenNameTest {

    private fun card(name: String, common: String) = EafcPlayer(
        id = 1, name = name, commonName = common, position = "ST", alternatePositions = "", rating = 91, potential = 91,
        team = "Man City", value = 150.0, wage = 400, birthdate = "7/21/2000", height = 195, weight = 94,
        preferredFoot = "Left", weakFoot = 3, nationality = "Norway"
    )

    private val haaland = card("Erling Haaland", "Haaland")
    private val database = listOf(haaland, card("Lionel Messi", "Messi"))

    @Test
    fun theRegensOwnNameShowsInsteadOfTheTemplatesCommonName() {
        // template picked in this dialog, then the Full Name was changed to the regen's own name
        assertNull(nicknameToKeep(true, "Ole Brandt", "Haaland", null, haaland, database))
        // the typed name may still be the template's: nothing to hide
        assertEquals("Haaland", nicknameToKeep(true, "Erling Haaland", "Haaland", null, haaland, database))
    }

    @Test
    fun aLeftoverTemplateNicknameFromAnOldVersionGoesWhenTheNameIsCorrected() {
        val old = Player(id = "r1", name = "Haaland", nickname = "Haaland", isRegen = true)
        assertNull(nicknameToKeep(true, "Ole Brandt", "Haaland", old, null, database))
    }

    @Test
    fun aNicknameThePersonTypedIsKept() {
        val old = Player(id = "r1", name = "Ole Brandt", nickname = "Brandy", isRegen = true)
        assertEquals("Brandy", nicknameToKeep(true, "Ole Brandt", "Brandy", old, null, database))
        assertEquals("Brandy", nicknameToKeep(true, "Ole B. Brandt", "Brandy", old, haaland, database))
        assertNull(nicknameToKeep(true, "Ole Brandt", "  ", old, null, database))
    }

    @Test
    fun aPlayerWhoIsNotARegenKeepsHisNickname() {
        val old = Player(id = "p1", name = "Haaland", nickname = "Haaland", isRegen = false)
        assertEquals("Haaland", nicknameToKeep(false, "Erling Haaland", "Haaland", old, haaland, database))
    }

    @Test
    fun theOriginalPlayerIsSavedWithTheRegenAndOldSavesLoadWithout() {
        val regen = Player(id = "r1", name = "Ole Brandt", isRegen = true, regenOriginalName = "Haaland (Erling Haaland)")
        val back = Gson().fromJson(Gson().toJson(regen), Player::class.java)
        assertEquals("Haaland (Erling Haaland)", back.regenOriginalName)
        assertEquals("Ole Brandt", back.name)

        val old = Gson().fromJson("""{"id":"r2","name":"Old Regen","isRegen":true}""", Player::class.java)
        assertNull(old.regenOriginalName)
    }
}
