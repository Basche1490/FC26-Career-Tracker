package com.fctracker.career.data

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import com.fctracker.career.AppState
import com.fctracker.career.Note
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Where an API key is kept. The real implementation is the Keystore-backed [SecretStore]. */
interface Secrets {
    fun get(name: String): String
    fun put(name: String, value: String)
}

/**
 * The single owner of the career data while the app process is alive.
 *
 * It lives in the Application, not in a ViewModel, so the screens and the background sync service work on the
 * same state: a match that arrives while the app is closed is already there when the app opens.
 *
 * Saving: every change to [state] is written to the active slot's file about half a second after the last
 * change, on a background thread. [flush] writes everything immediately (used when the app leaves the screen).
 */
class CareerStore(
    private val saves: SaveStore,
    private val secrets: Secrets,
    private val scope: CoroutineScope,
    private val autosaveDelayMs: Long = 500
) {
    val state = MutableStateFlow(AppState())

    /** The AI API key. Kept apart from [state] so it never lands in save files or exported backups. */
    val aiApiKey = MutableStateFlow("")

    val saveSlots: MutableMap<Int, AppState> = ConcurrentHashMap()
    val saveSlotNames = mutableStateMapOf<Int, String>()
    val savedNotes = mutableStateListOf<Note>()

    @Volatile
    var activeSaveSlot: Int = 1
        private set

    private var metaJob: Job? = null

    /**
     * Guards the pair (active slot, current state). Switching slots changes both, and a save must never see
     * one without the other, or one slot's data could be written into another slot's file.
     */
    private val slotLock = Any()

    init {
        load()
        startAutosave()
    }

    private fun load() {
        val meta = saves.loadMeta()
        saveSlotNames.putAll(meta.slotNames)
        savedNotes.addAll(meta.notes)
        for (id in saves.slotIds()) saves.loadSlot(id)?.let { saveSlots[id] = it }

        activeSaveSlot = when {
            saveSlots.containsKey(meta.activeSlot) -> meta.activeSlot
            saveSlots.isNotEmpty() -> saveSlots.keys.min()
            else -> meta.activeSlot
        }
        // A bridge status saved by an earlier run says nothing about the connection right now.
        saveSlots[activeSaveSlot]?.let {
            state.value = it.copy(bridgeConnectionStatus = "Not Checked", bridgeEventCount = 0)
        }
        aiApiKey.value = secrets.get(SecretStore.AI_API_KEY)
    }

    @OptIn(FlowPreview::class)
    private fun startAutosave() {
        scope.launch {
            // Equal-to-what-is-on-disk values are skipped, so loading a career does not immediately rewrite it.
            state.debounce(autosaveDelayMs).collect { snapshot ->
                synchronized(slotLock) {
                    // Only the newest state is written; if it moved on, another emission follows.
                    if (snapshot === state.value && saveSlots[activeSaveSlot] != snapshot) saveActive(snapshot)
                }
            }
        }
    }

    // -- slots ---------------------------------------------------------------------------------------

    fun switchSlot(slot: Int, createNew: Boolean) = synchronized(slotLock) {
        if (activeSaveSlot == slot) return@synchronized
        flush() // the slot being left is written first, so its last edits are never lost
        activeSaveSlot = slot
        if (createNew || !saveSlots.containsKey(slot)) {
            state.value = AppState()
            saveSlotNames[slot] = "Save Slot $slot"
        } else {
            state.value = saveSlots.getValue(slot)
        }
        saveMeta()
    }

    fun deleteActiveSlot() = synchronized(slotLock) {
        val slot = activeSaveSlot
        saveSlots.remove(slot)
        saveSlotNames.remove(slot)
        saves.deleteSlot(slot)
        val next = saveSlots.keys.minOrNull()
        if (next != null) {
            activeSaveSlot = next
            state.value = saveSlots.getValue(next)
        } else {
            activeSaveSlot = 1
            state.value = AppState(isOnboarded = false)
        }
        saveMeta()
    }

    fun renameSlot(slot: Int, name: String) {
        saveSlotNames[slot] = name
        metaChanged()
    }

    // -- notes and secrets ---------------------------------------------------------------------------

    /** Call after changing [savedNotes] or [saveSlotNames]. Saves the small meta file shortly after. */
    fun metaChanged() {
        metaJob?.cancel()
        metaJob = scope.launch {
            delay(autosaveDelayMs)
            saveMeta()
        }
    }

    fun setAiApiKey(key: String) {
        aiApiKey.value = key
        secrets.put(SecretStore.AI_API_KEY, key)
    }

    // -- writing -------------------------------------------------------------------------------------

    /** Writes the current state without waiting for the autosave delay (still off the calling thread). */
    fun saveNow() {
        scope.launch { synchronized(slotLock) { saveActive(state.value) } }
    }

    /** Writes the active slot and the meta file now, on the calling thread. */
    fun flush() = synchronized(slotLock) {
        saveActive(state.value)
        saveMeta()
    }

    private fun saveActive(snapshot: AppState) {
        val slot = activeSaveSlot
        saveSlots[slot] = snapshot
        if (!saveSlotNames.containsKey(slot)) saveSlotNames[slot] = "Save Slot $slot"
        saves.saveSlot(slot, snapshot)
    }

    private fun saveMeta() {
        saves.saveMeta(SaveStore.Meta(activeSaveSlot, saveSlotNames.toMap(), savedNotes.toList()))
    }
}
