package com.automatelinux.tally.data

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

@Serializable
data class Undo(val kind: String, val id: String)

@Serializable
private data class Ack(val ok: Boolean = false, val error: String? = null, val undo: Undo? = null)

@Serializable
private data class TallyBody(val id: String, val name: String, val currency: String, val accent: Int)

@Serializable
private data class EntryBody(
    val id: String,
    val direction: Direction,
    val amount: Long,
    val note: String,
    val category: String,
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
        val r = httpRequest("GET", "$base/api/tallies", token, null)
        return runCatching { json.decodeFromString(TalliesResponse.serializer(), r.body) }
            .getOrElse { TalliesResponse(error = describe(r)) }
    }

    suspend fun createTally(id: String, name: String, currency: String, accent: Int): String? =
        send("POST", "/api/tallies", json.encodeToString(TallyBody.serializer(), TallyBody(id, name, currency, accent)))

    suspend fun updateTally(id: String, name: String, currency: String, accent: Int): String? =
        send("PATCH", "/api/tallies/$id", json.encodeToString(TallyBody.serializer(), TallyBody(id, name, currency, accent)))

    suspend fun deleteTally(id: String): Result = call("DELETE", "/api/tallies/$id", null)

    suspend fun resetTally(id: String): Result = call("POST", "/api/tallies/$id/reset", "{}")

    suspend fun addEntry(
        tallyId: String, id: String, direction: Direction, amount: Long, note: String, category: String,
    ): String? = send(
        "POST", "/api/tallies/$tallyId/entries",
        json.encodeToString(EntryBody.serializer(), EntryBody(id, direction, amount, note, category)),
    )

    suspend fun updateEntry(
        id: String, direction: Direction, amount: Long, note: String, category: String,
    ): String? = send(
        "PATCH", "/api/entries/$id",
        json.encodeToString(EntryBody.serializer(), EntryBody(id, direction, amount, note, category)),
    )

    suspend fun deleteEntry(id: String): Result = call("DELETE", "/api/entries/$id", null)

    suspend fun restore(undo: Undo): String? =
        send("POST", "/api/restore", json.encodeToString(Undo.serializer(), undo))

    /** An acknowledgement plus, for the destructive calls, what would undo it. */
    data class Result(val error: String?, val undo: Undo?)

    private suspend fun call(method: String, path: String, body: String?): Result {
        if (!configured) return Result(NO_TOKEN, null)
        val r = httpRequest(method, "$base$path", token, body)
        val ack = runCatching { json.decodeFromString(Ack.serializer(), r.body) }.getOrNull()
        if (ack?.ok == true) return Result(null, ack.undo)
        return Result(ack?.error ?: describe(r), null)
    }

    private suspend fun send(method: String, path: String, body: String?): String? =
        call(method, path, body).error

    /** The status code alone is useless to someone holding a phone. */
    private fun describe(r: HttpResult): String = when (r.code) {
        401 -> "This build is not authorised by the server"
        0 -> "No connection — is the VPN up?"
        in 500..599 -> "The server could not reach its database"
        else -> "Unexpected reply from the server (${r.code})"
    }

    private companion object {
        const val NO_TOKEN = "Built without an API token — rebuild with mobile/.env"
    }
}
