package com.automatelinux.tally.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlin.random.Random

/**
 * The app state, backed by the tally database rather than by the phone.
 *
 * The device holds a view, not the only copy. That is deliberate: an app whose
 * records live only in its own storage loses them to a wipe, a reinstall, a lost
 * phone — or to anyone with adb — and money records are exactly the kind of thing
 * that must survive all four. The server never destroys a row either; delete and
 * reset mark them, so undo is a fact about the data rather than a five-second
 * window in the UI.
 *
 * Mutations apply locally first and are sent in the background, so tapping Add
 * feels instant on a slow link. If the send fails, the error is shown and the
 * local state is put back to whatever the server actually holds — an optimistic
 * update that is never reconciled is just a lie with good manners.
 */
class TallyStore(private val api: TallyApi, private val scope: CoroutineScope) {

    var tallies: List<Tally> by mutableStateOf(emptyList())
        private set

    /** True until the first answer arrives, so an empty list is never mistaken for "no tallies". */
    var loading: Boolean by mutableStateOf(true)
        private set

    var error: String? by mutableStateOf(null)
        private set

    /** What the server said would undo the last destructive act, plus the local
     *  state to show immediately so Undo does not wait on a round trip. */
    private var undo: Pair<Undo, List<Tally>>? = null

    init { refresh() }

    fun refresh() {
        scope.launch {
            val r = api.list()
            if (r.ok) {
                tallies = r.tallies
                error = null
            } else {
                error = r.error ?: "Could not load your tallies"
            }
            loading = false
        }
    }

    fun dismissError() { error = null }

    fun tally(id: String?): Tally? = tallies.firstOrNull { it.id == id }

    // ---- tallies ----------------------------------------------------------------

    fun createTally(name: String, currency: String, accent: Int): String {
        val t = Tally(id = newId(), name = name.trim(), currency = currency, accent = accent, createdAt = now())
        tallies = tallies + t
        push { api.createTally(t.id, t.name, t.currency, t.accent) }
        return t.id
    }

    fun updateTally(id: String, name: String, currency: String, accent: Int) {
        val clean = name.trim()
        tallies = tallies.map { if (it.id == id) it.copy(name = clean, currency = currency, accent = accent) else it }
        push { api.updateTally(id, clean, currency, accent) }
    }

    fun resetTally(id: String) {
        val before = tallies
        tallies = tallies.map { if (it.id == id) it.copy(entries = emptyList()) else it }
        destructive(before) { api.resetTally(id) }
    }

    fun deleteTally(id: String) {
        val before = tallies
        tallies = tallies.filterNot { it.id == id }
        destructive(before) { api.deleteTally(id) }
    }

    // ---- entries ----------------------------------------------------------------

    fun addEntry(tallyId: String, direction: Direction, amount: Long, note: String, category: String) {
        val e = Entry(id = newId(), direction = direction, amount = amount, note = note.trim(), category = category, at = now())
        tallies = tallies.map { if (it.id == tallyId) it.copy(entries = it.entries + e) else it }
        push { api.addEntry(tallyId, e.id, e.direction, e.amount, e.note, e.category) }
    }

    fun updateEntry(tallyId: String, entryId: String, direction: Direction, amount: Long, note: String, category: String) {
        val clean = note.trim()
        tallies = tallies.map { t ->
            if (t.id != tallyId) t else t.copy(entries = t.entries.map { e ->
                if (e.id != entryId) e
                else e.copy(direction = direction, amount = amount, note = clean, category = category)
            })
        }
        push { api.updateEntry(entryId, direction, amount, clean, category) }
    }

    fun deleteEntry(tallyId: String, entryId: String) {
        val before = tallies
        tallies = tallies.map { t ->
            if (t.id != tallyId) t else t.copy(entries = t.entries.filterNot { it.id == entryId })
        }
        destructive(before) { api.deleteEntry(entryId) }
    }

    /** Puts back what the last reset or delete took away. */
    fun undoLast() {
        val (token, before) = undo ?: return
        undo = null
        tallies = before                    // instant; the server catches up below
        scope.launch {
            val e = api.restore(token)
            if (e != null) error = e
            refresh()
        }
    }

    // ---- plumbing ---------------------------------------------------------------

    /** A change with nothing to undo: on failure, resync with the server. */
    private fun push(call: suspend () -> String?) {
        scope.launch {
            val e = call()
            if (e != null) {
                error = e
                refresh()
            }
        }
    }

    /** A change the user can take back: on success keep the undo token, on failure
     *  put the local state straight back. */
    private fun destructive(before: List<Tally>, call: suspend () -> TallyApi.Result) {
        scope.launch {
            val r = call()
            if (r.error != null) {
                error = r.error
                tallies = before
            } else if (r.undo != null) {
                undo = r.undo to before
            }
        }
    }

    private fun now() = Clock.System.now().toEpochMilliseconds()

    private fun newId() = now().toString(36) + "-" + Random.nextInt(0, 1 shl 20).toString(36)
}
