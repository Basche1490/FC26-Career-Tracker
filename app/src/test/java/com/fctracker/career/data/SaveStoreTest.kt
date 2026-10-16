package com.fctracker.career.data

import com.fctracker.career.AppState
import com.fctracker.career.Note
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SaveStoreTest {
    private lateinit var dir: File
    private lateinit var store: SaveStore

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("saves").toFile()
        store = SaveStore(dir)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun aSlotSurvivesAReload() {
        assertTrue(store.saveSlot(1, AppState(clubName = "Richmond", transferBudget = 12.5)))
        val loaded = SaveStore(dir).loadSlot(1)!!
        assertEquals("Richmond", loaded.clubName)
        assertEquals(12.5, loaded.transferBudget, 0.0)
    }

    @Test
    fun anUnknownSlotLoadsAsNull() {
        assertNull(store.loadSlot(7))
    }

    @Test
    fun aCorruptedMainFileFallsBackToTheLastGoodCopy() {
        store.saveSlot(1, AppState(clubName = "Version 1"))
        store.saveSlot(1, AppState(clubName = "Version 2"))
        File(dir, "slot_1.json").writeText("{ half a file")
        assertEquals("Version 1", store.loadSlot(1)!!.clubName)
    }

    @Test
    fun aWriteThatWasInterruptedLeavesTheSaveReadable() {
        store.saveSlot(1, AppState(clubName = "Good"))
        // a crash while writing leaves a partial temporary file next to the intact save
        File(dir, "slot_1.json.tmp").writeText("{\"clubName\": \"Hal")
        assertEquals("Good", store.loadSlot(1)!!.clubName)
    }

    @Test
    fun aCompleteTemporaryFileIsUsedWhenTheMainFileIsMissing() {
        store.saveSlot(1, AppState(clubName = "Fresh"))
        File(dir, "slot_1.json").renameTo(File(dir, "slot_1.json.tmp")) // crashed between the two renames
        assertEquals("Fresh", store.loadSlot(1)!!.clubName)
    }

    @Test
    fun noTemporaryFileIsLeftBehindAfterASave() {
        store.saveSlot(1, AppState())
        store.saveSlot(1, AppState(clubName = "again"))
        assertFalse(File(dir, "slot_1.json.tmp").exists())
    }

    @Test
    fun slotIdsListEveryStoredSlotAndDeleteRemovesAllCopies() {
        store.saveSlot(1, AppState())
        store.saveSlot(1, AppState(clubName = "twice")) // creates the .bak
        store.saveSlot(3, AppState())
        assertEquals(listOf(1, 3), store.slotIds())

        store.deleteSlot(1)
        assertEquals(listOf(3), store.slotIds())
        assertNull(store.loadSlot(1))
    }

    @Test
    fun metaRoundTrips() {
        store.saveMeta(
            SaveStore.Meta(
                activeSlot = 2,
                slotNames = mapOf(1 to "Career A", 2 to "Career B"),
                notes = listOf(Note(id = "n1", title = "Press high", content = "4-3-3"))
            )
        )
        val meta = SaveStore(dir).loadMeta()
        assertEquals(2, meta.activeSlot)
        assertEquals("Career B", meta.slotNames[2])
        assertEquals("Press high", meta.notes.single().title)
    }

    @Test
    fun missingOrBrokenMetaGivesDefaults() {
        assertEquals(1, store.loadMeta().activeSlot)
        File(dir, "meta.json").writeText("not json at all")
        val meta = store.loadMeta()
        assertEquals(1, meta.activeSlot)
        assertTrue(meta.notes.isEmpty())
    }
}
