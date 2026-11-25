package ai.workis.jowi.data

import ai.workis.jowi.Graph
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

// Console → Kapsama (ledger 128, page key `coverage`). Django: api/v1/workis/console_api.py →
// workis/console.py console_coverage_* → services/coverage_desk.py. These doors give the WEB's
// own JSON: field names are snake_case (explicit @SerialName below).

/** `GET console/coverage/?days=30` → `{success, rows, uncovered, floor, target, days, totals, labels}`. */
@Serializable
data class ConsoleCoveragePage(
    val success: Boolean? = null,
    val rows: List<CoverageRow>? = null,
    val uncovered: List<CoverageNeed>? = null,
    @Serializable(with = FlexInt::class) val floor: Int? = null,
    @Serializable(with = FlexInt::class) val target: Int? = null,
    @Serializable(with = FlexInt::class) val days: Int? = null,
    val totals: CoverageTotals? = null,
)

@Serializable
data class CoverageTotals(
    @Serializable(with = FlexInt::class) val groups: Int? = null,
    @Serializable(with = FlexInt::class) val short: Int? = null,
    @Serializable(with = FlexInt::class) val thin: Int? = null,
    @Serializable(with = FlexInt::class) val asks: Int? = null,
    @Serializable(with = FlexInt::class) val uncovered: Int? = null,
)

/** One seller chip: `seat` = member | shadow (a virtual seller). */
@Serializable
data class CoverageSeller(
    @Serializable(with = FlexString::class) val id: String? = null,
    val name: String? = null,
    val seat: String? = null,
    @Serializable(with = FlexInt::class) val families: Int? = null,
)

/** The stored research's head: `{at, firms (count), auto}`. */
@Serializable
data class CoverageFoundHead(
    val at: String? = null,
    @Serializable(with = FlexInt::class) val firms: Int? = null,
    @Serializable(with = FlexBool::class) val auto: Boolean? = null,
)

@Serializable
data class CoverageRow(
    @Serializable(with = FlexString::class) val id: String? = null,
    val group: String? = null,
    val sector: String? = null,
    @SerialName("sector_slug") val sectorSlug: String? = null,
    val parent: String? = null,
    @SerialName("sub_groups") @Serializable(with = FlexInt::class) val subGroups: Int? = null,
    val sellers: List<CoverageSeller>? = null,
    @SerialName("n_sellers") @Serializable(with = FlexInt::class) val nSellers: Int? = null,
    /** short · thin · ok */
    val status: String? = null,
    @Serializable(with = FlexInt::class) val asks: Int? = null,
    @SerialName("uncovered_rows") @Serializable(with = FlexInt::class) val uncoveredRows: Int? = null,
    @SerialName("last_research") val lastResearch: String? = null,
    @SerialName("research_firms") @Serializable(with = FlexInt::class) val researchFirms: Int? = null,
    val found: CoverageFoundHead? = null,
)

/** An ask no group covers: `{text, count, last}`. */
@Serializable
data class CoverageNeed(
    val text: String? = null,
    @Serializable(with = FlexInt::class) val count: Int? = null,
    val last: String? = null,
)

/** A research answer (fresh or stored): the firm objects UNTOUCHED — they go back to `batch/` as they came. */
class CoverageResearch(json: JsonObject) {
    val firms: List<JsonObject> = (json["firms"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    val searches: Int = json.covInt("searches") ?: 0
    val known: Int = json.covInt("known") ?: 0
    val segment: String = json.covStr("segment")
}

/** `batch` of `POST batch/` and `GET batch/{id}/`: `{id, state, at, updatedAt, firms[…]}`. */
@Serializable
data class CoverageBatch(
    @Serializable(with = FlexString::class) val id: String? = null,
    val state: String? = null,
    val at: String? = null,
    val updatedAt: String? = null,
    val firms: List<CoverageBatchFirm>? = null,
) {
    /** Still moving: a firm waiting, born or being read, and the batch not done. */
    val running: Boolean
        get() = state != "done" && firms.orEmpty().any { it.state in CoverageMoving }
}

val CoverageMoving = setOf("queued", "candidate", "reading")

@Serializable
data class CoverageBatchFirm(
    val company: String? = null,
    val website: String? = null,
    val host: String? = null,
    /** queued · candidate · reading · read · failed · skipped */
    val state: String? = null,
    val note: String? = null,
    /** The server's own words for `note` (Django batch_view) — an unknown note's text. */
    val noteWords: String? = null,
    @SerialName("profile_id") @Serializable(with = FlexString::class) val profileId: String? = null,
    @Serializable(with = FlexInt::class) val families: Int? = null,
)

// ── reading raw firm objects (decode only what is drawn) ─────────────────────────────────

fun JsonObject.covStr(key: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content.orEmpty()

fun JsonObject.covBool(key: String): Boolean? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull

fun JsonObject.covInt(key: String): Int? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { it.intOrNull ?: it.content.toDoubleOrNull()?.toInt() }

fun covBatchOf(el: JsonElement?): CoverageBatch? =
    (el as? JsonObject)?.let { runCatching { WorkisJson.decodeFromJsonElement(CoverageBatch.serializer(), it) }.getOrNull() }

/**
 * The research doors take ~60 s; the shared client (Network.kt) reads for 60 s by design and
 * is not reachable outside `buildApi`. So the two research calls go through a second instance
 * of the SAME `WorkisApi` interface over its own client with a 150 s read timeout (iOS waits
 * 150 s too) — the same four headers as the shared client, nothing else changed.
 */
object CoverageNet {
    val slow: WorkisApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(150, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(170, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val b = chain.request().newBuilder()
                    .header("Accept-Language", Graph.language.value)
                    .header("X-Workis-Client", Graph.client)
                    .header("X-Workis-Timezone", java.util.TimeZone.getDefault().id)
                Graph.tokenStore.token?.let { b.header("Authorization", "Token $it") }
                chain.proceed(b.build())
            }
            .build()
        Retrofit.Builder()
            .baseUrl(WORKIS_BASE_URL)
            .client(client)
            .addConverterFactory(WorkisJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(WorkisApi::class.java)
    }
}

/**
 * One call to this page's raw doors → (HTTP status, JSON body). Status 0 = no answer (lost
 * connection, timeout). The statuses mean something here: 404 on `found/` = nothing stored,
 * 402 on research = the web-search credit ran out (its `error` is the sentence to show).
 */
suspend fun covCall(block: suspend () -> JsonObject): Pair<Int, JsonObject> =
    try {
        200 to block()
    } catch (e: HttpException) {
        val body = runCatching {
            e.response()?.errorBody()?.string()?.let { WorkisJson.decodeFromString(JsonObject.serializer(), it) }
        }.getOrNull()
        e.code() to (body ?: JsonObject(emptyMap()))
    } catch (e: java.io.IOException) {
        0 to JsonObject(emptyMap())
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        0 to JsonObject(emptyMap())
    }
