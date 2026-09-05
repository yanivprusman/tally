package com.automatelinux.tally.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.russhwolf.settings.Settings
import kotlinx.datetime.Clock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.random.Random

/**
 * The whole app state, held in memory and mirrored to platform key-value storage on every
 * mutation. Everything lives on the device: a tally is personal, is written while standing
 * in a bus queue, and must not need a network to be usable.
 */
class TallyStore(private val settings: Settings) {

    var tallies: List<Tally> by mutableStateOf(emptyList())
        private set

    /** Last destructive act, kept just long enough for one Undo. */
    private var undo: (() -> Unit)? = null

    init {
        val raw = settings.getStringOrNull(KEY)
        tallies = if (raw.isNullOrBlank()) emptyList() else runCatching {
            json.decodeFromString(TalliesSerializer, raw)
        }.getOrDefault(emptyList())
    }

    fun tally(id: String?): Tally? = tallies.firstOrNull { it.id == id }

    // ---- tallies ----------------------------------------------------------------

    fun createTally(name: String, currency: String, accent: Int): String {
        val t = Tally(
            id = newId(),
            name = name.trim(),
            currency = currency,
            accent = accent,
            createdAt = now(),
        )
        commit(tallies + t)
        return t.id
    }

    fun updateTally(id: String, name: String, currency: String, accent: Int) {
        commit(tallies.map {
            if (it.id == id) it.copy(name = name.trim(), currency = currency, accent = accent) else it
        })
    }

    /** Empties a tally but keeps it — the trip is the same trip, the slate is clean. */
    fun resetTally(id: String) {
        val before = tallies
        commit(tallies.map { if (it.id == id) it.copy(entries = emptyList()) else it })
        undo = { commit(before) }
    }

    fun deleteTally(id: String) {
        val before = tallies
        commit(tallies.filterNot { it.id == id })
        undo = { commit(before) }
    }

    // ---- entries ----------------------------------------------------------------

    fun addEntry(tallyId: String, direction: Direction, amount: Long, note: String, category: String) {
        val e = Entry(
            id = newId(),
            direction = direction,
            amount = amount,
            note = note.trim(),
            category = category,
            at = now(),
        )
        commit(tallies.map { if (it.id == tallyId) it.copy(entries = it.entries + e) else it })
    }

    fun updateEntry(tallyId: String, entryId: String, direction: Direction, amount: Long, note: String, category: String) {
        commit(tallies.map { t ->
            if (t.id != tallyId) t else t.copy(entries = t.entries.map { e ->
                if (e.id != entryId) e
                else e.copy(direction = direction, amount = amount, note = note.trim(), category = category)
            })
        })
    }

    fun deleteEntry(tallyId: String, entryId: String) {
        val before = tallies
        commit(tallies.map { t ->
            if (t.id != tallyId) t else t.copy(entries = t.entries.filterNot { it.id == entryId })
        })
        undo = { commit(before) }
    }

    /** Restores the state from just before the last reset / delete. One level, which is all a snackbar offers. */
    fun undoLast() {
        undo?.invoke()
        undo = null
    }

    // ---- plumbing ---------------------------------------------------------------

    private fun commit(next: List<Tally>) {
        tallies = next
        settings.putString(KEY, json.encodeToString(TalliesSerializer, next))
    }

    private fun now() = Clock.System.now().toEpochMilliseconds()

    private fun newId() = now().toString(36) + "-" + Random.nextInt(0, 1 shl 20).toString(36)

    private companion object {
        const val KEY = "tallies.v1"
        val TalliesSerializer = ListSerializer(Tally.serializer())
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    }
}
