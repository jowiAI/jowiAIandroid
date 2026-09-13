package ai.workis.jowi.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException

/**
 * The partner seat verdict — `GET company/`: 2xx = a firm (Pazar tab), 403 =
 * the body names the next door (`seat:"none"` with the application or null,
 * `seat:"operator"` for console seats). Shared by the tab set, the Pazar
 * tab, the Başvuru tab and the Hesap Firma sections.
 */
class PartnerStore(private val api: WorkisApi) {
    private val _seat = MutableStateFlow<PartnerSeatState>(PartnerSeatState.Unknown)
    val seat: StateFlow<PartnerSeatState> = _seat

    val company: PartnerCompany? get() = (_seat.value as? PartnerSeatState.Seat)?.company

    suspend fun refresh() {
        try {
            _seat.value = PartnerSeatState.Seat(api.company())
        } catch (e: HttpException) {
            if (e.code() == 403) {
                val v = runCatching {
                    WorkisJson.decodeFromString<SeatVerdict>(e.response()?.errorBody()?.string().orEmpty())
                }.getOrNull()
                _seat.value = if (v?.seat == "operator") PartnerSeatState.Operator
                else PartnerSeatState.None(v?.application, v?.applyUrl, v?.emailConflict == true)
            }
            // other statuses: keep what we had (unreachable ≠ seatless)
        } catch (_: Exception) {
            // offline: keep the last verdict
        }
    }

    fun clear() { _seat.value = PartnerSeatState.Unknown }
}

/** POST → (ok, message). Partner endpoints answer `{success:false, error}` with the server's sentence. */
suspend fun <T> partnerCall(lang: String, call: suspend () -> T): Pair<T?, String?> =
    try {
        call() to null
    } catch (e: HttpException) {
        null to messageFromBody(e, lang)
    } catch (e: java.io.IOException) {
        null to statusMessage(null, lang)
    } catch (e: Exception) {
        null to statusMessage(-1, lang)
    }

/** Build a small JSON object body from Kotlin values (strings, numbers, booleans, string lists). */
fun jsonBody(vararg pairs: Pair<String, Any?>): JsonObject = kotlinx.serialization.json.buildJsonObject {
    for ((k, v) in pairs) {
        when (v) {
            null -> Unit
            is String -> put(k, kotlinx.serialization.json.JsonPrimitive(v))
            is Boolean -> put(k, kotlinx.serialization.json.JsonPrimitive(v))
            is Int -> put(k, kotlinx.serialization.json.JsonPrimitive(v))
            is Double -> put(k, kotlinx.serialization.json.JsonPrimitive(v))
            is List<*> -> put(k, kotlinx.serialization.json.JsonArray(v.map { kotlinx.serialization.json.JsonPrimitive(it.toString()) }))
            is JsonObject -> put(k, v)
            is kotlinx.serialization.json.JsonElement -> put(k, v)
            else -> put(k, kotlinx.serialization.json.JsonPrimitive(v.toString()))
        }
    }
}
