package com.automatelinux.tally.data

import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One HTTP round trip. Android supplies it with the JDK client; iOS will supply
 *  NSURLSession. Kept small so neither platform grows a second place where a
 *  request can be built differently. */
expect suspend fun httpRequest(
    method: String,
    url: String,
    token: String,
    jsonBody: String?,
): HttpResult

data class HttpResult(val code: Int, val body: String, val transportError: String? = null)

@Serializable
data class TalliesResponse(
    val ok: Boolean = false,
    val tallies: List<Tally> = emptyList(),
    val error: String? = null,
)

/** What "add my last purchase" put in the tally (server lib/lastPurchase.ts). */
@Serializable
data class CardEntry(
    val id: String,
    val amount: Long,
    val note: String = "",
    val category: String = "other",
    val at: Long,
)

/**
 * One press of "add my last purchase", as the server tracks it. `waiting` with no
 * checks yet means the first look at the card is still running.
 */
@Serializable
data class PurchaseWatch(
    val id: String,
    val tallyId: String,
    val requestedAt: Long,
    val status: String,
    val checks: Int = 0,
    val error: String? = null,
    val until: Long,
    val entry: CardEntry? = null,
) {
    val waiting: Boolean get() = status == "waiting"
}

@Serializable
data class WatchResponse(val ok: Boolean = false, val watch: PurchaseWatch? = null, val error: String? = null)

@Serializable
private data class PushBody(val endpoint: String)

@Serializable
private data class Ack(val ok: Boolean = false, val error: String? = null)

@Serializable
private data class TallyBody(
    val id: String, val name: String, val currency: String, val accent: Int,
    val exVat: Boolean = false,
    val createdAt: Long = 0,
)

@Serializable
private data class EntryBody(
    val id: String,
    val direction: Direction,
    val amount: Long,
    val note: String,
    val category: String,
    val vatIncluded: Boolean = false,
    val at: Long = 0,
)

/**
 * The app's whole conversation with the backend.
 *
 * Failures are values, never exceptions thrown at the UI: this screen gets used
 * standing in a bus queue, so "the VPN is not up" has to render as a sentence
 * rather than as a crash or a spinner that never ends.
 */
class TallyApi(baseUrl: String, private val token: String) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    val configured: Boolean get() = token.isNotEmpty()

    suspend fun list(): TalliesResponse {
        if (!configured) return TalliesResponse(error = NO_TOKEN)
        val r = send("GET", "$base/api/tallies", null)
        return runCatching { json.decodeFromString(TalliesResponse.serializer(), r.body) }
            .getOrElse { TalliesResponse(error = describe(r)) }
    }

    suspend fun createTally(
        id: String, name: String, currency: String, accent: Int, createdAt: Long,
    ): String? = call(
        "POST", "/api/tallies",
        json.encodeToString(
            TallyBody.serializer(),
            TallyBody(id, name, currency, accent, exVat = false, createdAt = createdAt),
        ),
    )

    /** Undo. The whole tally goes back — original ids, original timestamps — because
     *  a restored entry dated "now" would land under the wrong day heading. */
    suspend fun saveTally(tally: Tally): String? =
        call("POST", "/api/tallies", json.encodeToString(Tally.serializer(), tally))

    /** The whole presentation of a tally in one call: its name, its colour, its currency
     *  and whether it is being shown without VAT. */
    suspend fun updateTally(
        id: String, name: String, currency: String, accent: Int, exVat: Boolean,
    ): String? = call(
        "PATCH", "/api/tallies/$id",
        json.encodeToString(TallyBody.serializer(), TallyBody(id, name, currency, accent, exVat)),
    )

    suspend fun deleteTally(id: String): String? = call("DELETE", "/api/tallies/$id", null)

    suspend fun resetTally(id: String): String? = call("POST", "/api/tallies/$id/reset", "{}")

    suspend fun addEntry(
        tallyId: String, id: String, direction: Direction, amount: Long, note: String,
        category: String, vatIncluded: Boolean, at: Long,
    ): String? = call(
        "POST", "/api/tallies/$tallyId/entries",
        json.encodeToString(
            EntryBody.serializer(),
            EntryBody(id, direction, amount, note, category, vatIncluded, at),
        ),
    )

    suspend fun updateEntry(
        id: String, direction: Direction, amount: Long, note: String, category: String,
        vatIncluded: Boolean,
    ): String? = call(
        "PATCH", "/api/entries/$id",
        json.encodeToString(
            EntryBody.serializer(),
            EntryBody(id, direction, amount, note, category, vatIncluded),
        ),
    )

    suspend fun deleteEntry(id: String): String? = call("DELETE", "/api/entries/$id", null)

    /** "Add my last purchase": POST presses the button, GET follows the press, DELETE stops waiting. */
    suspend fun lastPurchase(tallyId: String, method: String): WatchResponse {
        if (!configured) return WatchResponse(error = NO_TOKEN)
        val r = send(method, "$base/api/tallies/$tallyId/last-purchase", if (method == "POST") "{}" else null)
        return runCatching { json.decodeFromString(WatchResponse.serializer(), r.body) }
            .getOrElse { WatchResponse(error = describe(r)) }
    }

    /** Where the server should push when a wait ends (UnifiedPush, through the suite's ntfy). */
    suspend fun registerPush(endpoint: String): String? =
        call("POST", "/api/push/register", json.encodeToString(PushBody.serializer(), PushBody(endpoint)))

    /** Null means it worked; anything else is a sentence to put on screen. */
    private suspend fun call(method: String, path: String, body: String?): String? {
        if (!configured) return NO_TOKEN
        val r = send(method, "$base$path", body)
        val ack = runCatching { json.decodeFromString(Ack.serializer(), r.body) }.getOrNull()
        return if (ack?.ok == true) null else ack?.error ?: describe(r)
    }

    /**
     * One request, waiting out a server that is still waking up.
     *
     * An idle dev server is stopped by the daemon, which then holds its port and
     * answers 503 ("Starting…", Retry-After) while it starts the app again — about
     * 10–20 s. Tally's own routes never answer 503 (a failed handler is a 500 with
     * the reason in the body), so a 503 means exactly that state, and the right
     * response is to wait for it, not to show an error the moment the phone wakes
     * a sleeping server.
     */
    private suspend fun send(method: String, url: String, body: String?): HttpResult {
        var waitedMs = 0L
        while (true) {
            val r = httpRequest(method, url, token, body)
            if (r.code != 503 || waitedMs >= STARTUP_WAIT_MS) return r
            delay(STARTUP_POLL_MS)
            waitedMs += STARTUP_POLL_MS
        }
    }

    /** The status code alone is useless to someone holding a phone. Reached only when
     *  the body is not one of the server's own `{ok, error}` replies. */
    private fun describe(r: HttpResult): String = when (r.code) {
        401 -> "This build is not authorised by the server"
        0 -> "No connection — is the VPN up?"
        503 -> "The server is still starting — try again in a moment"
        in 500..599 -> "The server failed (${r.code})"
        else -> "Unexpected reply from the server (${r.code})"
    }

    private companion object {
        const val NO_TOKEN = "Built without an API token — rebuild with mobile/.env"
        const val STARTUP_POLL_MS = 2_000L
        const val STARTUP_WAIT_MS = 60_000L
    }
}
