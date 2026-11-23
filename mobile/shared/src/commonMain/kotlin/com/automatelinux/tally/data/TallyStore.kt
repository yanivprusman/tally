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
 * that must survive all four.
 *
 * Deleting, though, deletes. The database move protects against *accidents*; a
 * delete the user chose is not one, and a row kept behind a flag is not a delete.
 * Undo works because this class still holds the previous state in memory for as
 * long as the snackbar is up, and puts it back with its original ids and times.
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

    /**
     * True once the server has answered with the list at least once. Unlike `loading`,
     * a failed first load leaves this false — so a tally the app reopened into, but has
     * not been able to fetch yet, is still waiting rather than taken for deleted.
     */
    var loaded: Boolean by mutableStateOf(false)
        private set

    var error: String? by mutableStateOf(null)
        private set

    /**
     * What the last destructive act removed. Held only in memory: once it is
     * replaced, the delete is final everywhere.
     *
     * `removed` is exactly the entries that disappeared, not the whole tally —
     * writing the whole tally back would also overwrite entries the delete never
     * touched, and quietly undo anything changed elsewhere in the meantime.
     */
    private data class Undoable(val before: List<Tally>, val tally: Tally, val removed: List<Entry>)

    private var undo: Undoable? = null

    init { refresh() }

    fun refresh() {
        scope.launch {
            val r = api.list()
            if (r.ok) {
                tallies = r.tallies
                loaded = true
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
        // A new tally starts collecting — the server creates it open too.
        val t = Tally(id = newId(), name = name.trim(), currency = currency, accent = accent, open = true, createdAt = now())
        tallies = tallies + t
        push { api.createTally(t.id, t.name, t.currency, t.accent, t.createdAt) }
        return t.id
    }

    /**
     * A copy of a tally, entries and all.
     *
     * The point is to fork a count and then change it — take the bag out, see what
     * the total would have been — so the copy has to start out identical, and it has
     * to be a separate tally rather than a view: the original must stay untouched
     * while its fork is edited.
     *
     * Ids are new (no two rows may share one) but every `at` is the original, so the
     * copy groups under exactly the same day headings as what it came from and the
     * two balances are comparable line for line. `createdAt` is now, because that is
     * when this tally started existing.
     *
     * Returns the new id, or null if the tally is already gone.
     */
    fun duplicateTally(id: String): String? {
        val source = tally(id) ?: return null
        val copy = source.copy(
            id = newId(),
            name = copyName(source.name),
            createdAt = now(),
            // A copy is a what-if: it does not collect, and its entries belong to it
            // alone, so editing a work day in countdown changes only the original.
            open = false,
            entries = source.entries.map { it.copy(id = newId(), sourceRef = null) },
        )
        tallies = tallies + copy
        push { api.saveTally(copy) }
        return copy.id
    }

    fun updateTally(id: String, name: String, currency: String, accent: Int) {
        val clean = name.trim()
        val current = tally(id) ?: return
        tallies = tallies.map { if (it.id == id) it.copy(name = clean, currency = currency, accent = accent) else it }
        // The edit screen has no opinion about the VAT view, so it sends back the one
        // the tally already has rather than silently resetting it.
        push { api.updateTally(id, clean, currency, accent, current.exVat) }
    }

    /**
     * Which way this tally is read: as written, or with VAT taken back out of the
     * entries that carry it. It is a view rather than a record — but a view worth
     * keeping, so it lives on the tally and is therefore the same on every device
     * and still there after a reinstall.
     */
    fun setExVat(id: String, on: Boolean) {
        val t = tally(id) ?: return
        tallies = tallies.map { if (it.id == id) it.copy(exVat = on) else it }
        push { api.updateTally(id, t.name, t.currency, t.accent, on) }
    }

    /** Start or stop collecting work days from the other apps. */
    fun setOpen(id: String, open: Boolean) {
        tally(id) ?: return
        tallies = tallies.map { if (it.id == id) it.copy(open = open) else it }
        push { api.setOpen(id, open) }
    }

    fun resetTally(id: String) {
        val before = tallies
        val t = tally(id) ?: return
        tallies = tallies.map { if (it.id == id) it.copy(entries = emptyList()) else it }
        destructive(before, t, t.entries) { api.resetTally(id) }
    }

    fun deleteTally(id: String) {
        val before = tallies
        val t = tally(id) ?: return
        tallies = tallies.filterNot { it.id == id }
        destructive(before, t, t.entries) { api.deleteTally(id) }
    }

    // ---- entries ----------------------------------------------------------------

    fun addEntry(
        tallyId: String, direction: Direction, amount: Long, note: String, category: String,
        vatIncluded: Boolean,
    ) {
        val e = Entry(
            id = newId(), direction = direction, amount = amount, note = note.trim(),
            category = category, at = now(), vatIncluded = vatIncluded,
        )
        tallies = tallies.map { if (it.id == tallyId) it.copy(entries = it.entries + e) else it }
        push { api.addEntry(tallyId, e.id, e.direction, e.amount, e.note, e.category, e.vatIncluded, e.at) }
    }

    fun updateEntry(
        tallyId: String, entryId: String, direction: Direction, amount: Long, note: String,
        category: String, vatIncluded: Boolean,
    ) {
        val clean = note.trim()
        tallies = tallies.map { t ->
            if (t.id != tallyId) t else t.copy(entries = t.entries.map { e ->
                if (e.id != entryId) e
                else e.copy(
                    direction = direction, amount = amount, note = clean,
                    category = category, vatIncluded = vatIncluded,
                )
            })
        }
        push { api.updateEntry(entryId, direction, amount, clean, category, vatIncluded) }
    }

    fun deleteEntry(tallyId: String, entryId: String) {
        val before = tallies
        val t = tally(tallyId) ?: return
        val gone = t.entries.filter { it.id == entryId }
        tallies = tallies.map { x ->
            if (x.id != tallyId) x else x.copy(entries = x.entries.filterNot { it.id == entryId })
        }
        destructive(before, t, gone) { api.deleteEntry(entryId) }
    }

    /** Puts back what the last reset or delete took away, by writing it again. */
    fun undoLast() {
        val u = undo ?: return
        undo = null
        tallies = u.before                  // instant; the server catches up below
        scope.launch {
            val e = api.saveTally(u.tally.copy(entries = u.removed))
            if (e != null) {
                error = e
                refresh()
            }
        }
    }

    // ---- add my last purchase ---------------------------------------------------

    /**
     * The newest press of "add my last purchase" per tally, as the server last told us.
     * The wait itself lives on the server, so closing the app does not stop it; this is
     * only what the screen shows.
     */
    var watches: Map<String, PurchaseWatch> by mutableStateOf(emptyMap())
        private set

    /** Outcomes the user has closed, so the strip goes back to the button. */
    private var dismissed: Set<String> by mutableStateOf(emptySet())

    /** The press to show on this tally's screen, if any: a wait, or a recent outcome not yet closed. */
    fun watchFor(tallyId: String): PurchaseWatch? {
        val w = watches[tallyId] ?: return null
        if (w.waiting) return w
        if (w.status == "cancelled" || w.id in dismissed) return null
        // An old outcome is history, not news.
        return if (now() - w.until < OutcomeShownForMs) w else null
    }

    fun dismissWatch(tallyId: String) {
        watches[tallyId]?.let { dismissed = dismissed + it.id }
    }

    fun addLastPurchase(tallyId: String) = followUp(tallyId, "POST")

    fun stopLastPurchase(tallyId: String) = followUp(tallyId, "DELETE")

    /** One GET. The screen calls it on a timer while a wait is on. */
    suspend fun pollWatch(tallyId: String) {
        val r = api.lastPurchase(tallyId, "GET")
        if (r.ok) apply(tallyId, r.watch) // a failed poll is retried by the next tick
    }

    private fun followUp(tallyId: String, method: String) {
        scope.launch {
            val r = api.lastPurchase(tallyId, method)
            if (r.ok) apply(tallyId, r.watch) else error = r.error ?: "Could not reach the server"
        }
    }

    private fun apply(tallyId: String, w: PurchaseWatch?) {
        val before = watches[tallyId]
        watches = if (w == null) watches - tallyId else watches + (tallyId to w)
        // The purchase is now a row on the server: fetch it so it appears in the list.
        if (w?.status == "added" && (before?.status != "added" || before.id != w.id)) refresh()
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

    /** A change the user can take back: on success remember what it replaced, on
     *  failure put the local state straight back. */
    private fun destructive(
        before: List<Tally>,
        tally: Tally,
        removed: List<Entry>,
        call: suspend () -> String?,
    ) {
        scope.launch {
            val e = call()
            if (e != null) {
                error = e
                tallies = before
            } else {
                undo = Undoable(before, tally, removed)
            }
        }
    }

    /**
     * "Trip" becomes "Trip copy", then "Trip copy 2" — a fork is usually one of
     * several ("what if I skip the bag", "what if I skip the taxi"), so the second
     * one must not arrive wearing the first one's name. Forking a fork drops the old
     * suffix rather than stacking another on top of it.
     */
    private fun copyName(source: String): String {
        val base = source.trim().replace(CopySuffix, "").trim().ifBlank { source.trim() }
        val taken = tallies.map { it.name }.toSet()
        var n = 1
        while (true) {
            val candidate = withSuffix(base, if (n == 1) "copy" else "copy $n")
            if (candidate !in taken) return candidate
            n++
        }
    }

    /** The name field holds 40 characters, so a long name loses its tail, never its suffix. */
    private fun withSuffix(base: String, suffix: String): String {
        val room = MaxNameLength - suffix.length - 1
        return (if (base.length <= room) base else base.take(room).trimEnd()) + " " + suffix
    }

    private var seq = 0

    private fun now() = Clock.System.now().toEpochMilliseconds()

    /**
     * Unique by construction inside a run, not by luck. A duplicate mints one id per
     * entry inside a single millisecond, and two entries that collided would be
     * folded into one row by the upsert — a copy silently missing a line. The
     * millisecond and the random tail keep it unique against earlier runs too.
     */
    private fun newId() =
        now().toString(36) + "-" + (seq++).toString(36) + Random.nextInt(0, 1 shl 20).toString(36)

    private companion object {
        const val MaxNameLength = 40
        const val OutcomeShownForMs = 60 * 60_000L
        val CopySuffix = Regex(" copy( \\d+)?$")
    }
}
