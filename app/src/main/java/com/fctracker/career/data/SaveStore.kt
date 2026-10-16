package com.fctracker.career.data

import android.util.Log
import com.fctracker.career.AppState
import com.fctracker.career.Note
import com.google.gson.Gson
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Keeps every save slot in its own JSON file inside [dir], plus one small meta file for slot names, the
 * active slot and the tactical notes.
 *
 * Why not one big preferences string, as before:
 *  - only the slot that changed is rewritten, not every slot;
 *  - a write goes to a temporary file first and is renamed into place, so a crash or a full disk in the
 *    middle of saving can never leave a half-written save behind;
 *  - the previous good copy is kept as ".bak" and used automatically if the main file is unreadable.
 *
 * All methods are safe to call from any thread, but they do disk work, so call them off the main thread.
 */
class SaveStore(private val dir: File, private val gson: Gson = Gson()) {

    data class Meta(
        val activeSlot: Int = 1,
        val slotNames: Map<Int, String> = emptyMap(),
        val notes: List<Note> = emptyList()
    )

    private val lock = Any()

    init {
        dir.mkdirs()
    }

    private fun slotFile(slot: Int) = File(dir, "slot_$slot.json")
    private val metaFile get() = File(dir, "meta.json")

    /** Slot numbers that have a save on disk (the main file or at least a backup of it). */
    fun slotIds(): List<Int> = synchronized(lock) {
        val pattern = Regex("""slot_(\d+)\.json(\.bak|\.tmp)?""")
        dir.listFiles().orEmpty()
            .mapNotNull { pattern.matchEntire(it.name)?.groupValues?.get(1)?.toIntOrNull() }
            .distinct()
            .sorted()
    }

    fun loadSlot(slot: Int): AppState? = synchronized(lock) { read(slotFile(slot), AppState::class.java) }

    fun saveSlot(slot: Int, state: AppState): Boolean = synchronized(lock) { write(slotFile(slot), state) }

    fun deleteSlot(slot: Int) = synchronized(lock) {
        val f = slotFile(slot)
        listOf(f, File(f.path + ".bak"), File(f.path + ".tmp")).forEach { it.delete() }
    }

    fun loadMeta(): Meta = synchronized(lock) {
        read(metaFile, MetaDto::class.java)?.let {
            Meta(it.activeSlot ?: 1, it.slotNames ?: emptyMap(), it.notes ?: emptyList())
        } ?: Meta()
    }

    fun saveMeta(meta: Meta): Boolean = synchronized(lock) {
        write(metaFile, MetaDto(meta.activeSlot, meta.slotNames, meta.notes))
    }

    // Gson fills missing fields with null, so the on-disk shape is nullable and converted to [Meta] above.
    private class MetaDto(
        val activeSlot: Int? = null,
        val slotNames: Map<Int, String>? = null,
        val notes: List<Note>? = null
    )

    // -- file handling -------------------------------------------------------------------------------

    private fun write(target: File, value: Any): Boolean {
        val tmp = File(target.path + ".tmp")
        val bak = File(target.path + ".bak")
        return try {
            FileOutputStream(tmp).use { out ->
                val writer = OutputStreamWriter(out, Charsets.UTF_8)
                gson.toJson(value, writer)
                writer.flush()
                out.fd.sync() // make sure the bytes are on disk before the rename makes them "the" save
            }
            if (target.exists()) {
                bak.delete()
                target.renameTo(bak)
            }
            if (!tmp.renameTo(target)) throw IOException("could not move ${tmp.name} into place")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Saving ${target.name} failed", e)
            tmp.delete()
            false
        }
    }

    /** Tries the main file, then a leftover temporary file, then the backup; the first one that parses wins. */
    private fun <T : Any> read(target: File, type: Class<T>): T? {
        val candidates = listOf(target, File(target.path + ".tmp"), File(target.path + ".bak"))
        for (file in candidates) {
            if (!file.isFile || file.length() == 0L) continue
            try {
                FileInputStream(file).use { input ->
                    gson.fromJson(InputStreamReader(input, Charsets.UTF_8), type)?.let { return it }
                }
            } catch (e: Exception) {
                Log.w(TAG, "${file.name} is unreadable, trying the next copy", e)
            }
        }
        return null
    }

    private companion object {
        const val TAG = "SaveStore"
    }
}
