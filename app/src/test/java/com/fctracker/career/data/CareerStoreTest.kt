package com.fctracker.career.data

import com.fctracker.career.AppState
import com.fctracker.career.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CareerStoreTest {
    private class FakeSecrets : Secrets {
        val map = mutableMapOf<String, String>()
        override fun get(name: String) = map[name].orEmpty()
        override fun put(name: String, value: String) {
            if (value.isBlank()) map.remove(name) else map[name] = value
        }
    }

    private lateinit var dir: File
    private lateinit var secrets: FakeSecrets
    private val scopes = mutableListOf<CoroutineScope>()

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("career").toFile()
        secrets = FakeSecrets()
    }

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        dir.deleteRecursively()
    }

    private fun open(): CareerStore {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes.add(scope)
        return CareerStore(SaveStore(dir), secrets, scope, autosaveDelayMs = 20)
    }

    @Test
    fun aFreshInstallStartsNotOnboarded() {
        val store = open()
        assertFalse(store.state.value.isOnboarded)
        assertEquals(1, store.activeSaveSlot)
    }

    @Test
    fun flushedDataComesBackAfterARestart() {
        val first = open()
        first.state.value = first.state.value.copy(clubName = "Wrexham", isOnboarded = true)
        first.savedNotes.add(Note(id = "n", title = "t", content = "c"))
        first.flush()

        val second = open()
        assertEquals("Wrexham", second.state.value.clubName)
        assertTrue(second.state.value.isOnboarded)
        assertEquals(1, second.savedNotes.size)
    }

    @Test
    fun theApiKeyIsNeverWrittenToTheSaveFiles() {
        val store = open()
        store.setAiApiKey("sk-very-secret-123")
        store.state.value = store.state.value.copy(clubName = "Wrexham", aiAgent = "Claude")
        store.flush()

        val everything = dir.walkTopDown().filter { it.isFile }.joinToString("\n") { it.readText() }
        assertFalse(everything.contains("sk-very-secret-123"))
        assertEquals("sk-very-secret-123", secrets.get(SecretStore.AI_API_KEY))
        assertEquals("sk-very-secret-123", open().aiApiKey.value)
    }

    @Test
    fun switchingSlotsWritesTheOldSlotFirstAndKeepsBothApart() {
        val store = open()
        store.state.value = store.state.value.copy(clubName = "Slot One FC")

        store.switchSlot(2, createNew = true)
        assertEquals(2, store.activeSaveSlot)
        assertEquals(AppState().clubName, store.state.value.clubName) // a brand-new career
        assertEquals("Save Slot 2", store.saveSlotNames[2])
        store.state.value = store.state.value.copy(clubName = "Slot Two FC")

        store.switchSlot(1, createNew = false)
        assertEquals("Slot One FC", store.state.value.clubName)
        store.flush()

        val reopened = open()
        assertEquals(1, reopened.activeSaveSlot)
        assertEquals("Slot One FC", reopened.saveSlots.getValue(1).clubName)
        assertEquals("Slot Two FC", reopened.saveSlots.getValue(2).clubName)
    }

    @Test
    fun deletingTheActiveSlotMovesToTheNextOne() {
        val store = open()
        store.state.value = store.state.value.copy(clubName = "One")
        store.switchSlot(2, createNew = true)
        store.state.value = store.state.value.copy(clubName = "Two")
        store.flush()

        store.deleteActiveSlot()
        assertEquals(1, store.activeSaveSlot)
        assertEquals("One", store.state.value.clubName)
        assertFalse(File(dir, "slot_2.json").exists())
        assertFalse(open().saveSlots.containsKey(2))
    }

    @Test
    fun deletingTheOnlySlotGivesAnEmptyCareer() {
        val store = open()
        store.state.value = store.state.value.copy(clubName = "Only", isOnboarded = true)
        store.flush()
        store.deleteActiveSlot()
        assertFalse(store.state.value.isOnboarded)
    }

    @Test
    fun aStaleBridgeStatusIsResetOnStart() {
        val first = open()
        first.state.value = first.state.value.copy(bridgeConnectionStatus = "Online (5 events)", bridgeEventCount = 5)
        first.flush()
        val second = open()
        assertEquals("Not Checked", second.state.value.bridgeConnectionStatus)
        assertEquals(0, second.state.value.bridgeEventCount)
    }

    @Test
    fun autosaveWritesWithoutAnExplicitFlush() {
        val store = open()
        store.state.value = store.state.value.copy(clubName = "Autosaved")
        val file = File(dir, "slot_1.json")
        val deadline = System.currentTimeMillis() + 5_000
        while (System.currentTimeMillis() < deadline && !(file.exists() && file.readText().contains("Autosaved"))) {
            Thread.sleep(25)
        }
        assertTrue(file.exists() && file.readText().contains("Autosaved"))
    }
}
