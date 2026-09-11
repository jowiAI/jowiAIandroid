package ai.workis.jowi.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.IOException

const val WORKIS_ORIGIN = "https://workis.ai"
const val WORKIS_BASE_URL = "$WORKIS_ORIGIN/api/v1/"

/** Contract paths (`/sozlesme/uzman/pdf/`, `/workis/console/…/signed.pdf`) → absolute. */
fun absoluteWorkisUrl(path: String): String =
    if (path.startsWith("http")) path else WORKIS_ORIGIN + path

val WorkisJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = false
    coerceInputValues = true
}

fun buildApi(
    tokenProvider: () -> String?,
    languageProvider: () -> String,
): WorkisApi {
    val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }
    val client = OkHttpClient.Builder()
        // Jowi answers are model calls (10–40 s) — the default 10 s read timeout cut them off.
        // (The 8 s validate-token was a cold gunicorn worker after deploys; fixed server-side 2026-09-11.)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("Accept-Language", languageProvider())
            tokenProvider()?.let { builder.header("Authorization", "Token $it") }
            chain.proceed(builder.build())
        }
        .addInterceptor(logging)
        .build()

    return Retrofit.Builder()
        .baseUrl(WORKIS_BASE_URL)
        .client(client)
        .addConverterFactory(WorkisJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(WorkisApi::class.java)
}

sealed interface ApiResult<out T> {
    data class Ok<T>(val value: T) : ApiResult<T>
    data class Err(val status: Int?, val message: String) : ApiResult<Nothing>
}

/** Centralized error mapper (TR/EN), mirroring iOS statusMessage(status:lang:). */
fun statusMessage(status: Int?, lang: String): String {
    val tr = lang.startsWith("tr")
    return when (status) {
        401 -> if (tr) "Oturum süresi doldu, yeniden giriş yapın." else "Session expired, please sign in again."
        403 -> if (tr) "Bu işlem için yetkiniz yok." else "You are not allowed to do this."
        404 -> if (tr) "Kayıt bulunamadı." else "Not found."
        409 -> if (tr) "Bu e-posta zaten kayıtlı." else "This e-mail is already registered."
        429 -> if (tr) "Çok sık denediniz, biraz bekleyin." else "Too many attempts, please wait."
        null -> if (tr) "Bağlantı kurulamadı." else "Could not connect."
        else -> if (tr) "Bir sorun oluştu ($status)." else "Something went wrong ($status)."
    }
}

/** Apply-lane error codes → localized text, mirroring iOS applyErrorMessage(code:lang:). */
fun applyErrorMessage(code: String?, lang: String): String {
    val tr = lang.startsWith("tr")
    return when (code) {
        "signer_required" -> if (tr) "İmza için ad soyad gerekli." else "Signer full name is required."
        "invalid_email" -> if (tr) "Geçerli bir e-posta girin." else "Enter a valid e-mail."
        "missing_fields" -> if (tr) "Zorunlu alanlar eksik." else "Required fields are missing."
        "email_taken" -> if (tr) "Bu e-posta zaten kayıtlı." else "This e-mail is already registered."
        "parse_failed" -> if (tr) "Belge okunamadı — bilgileri elle girebilirsiniz." else "Could not read the document — enter the details manually."
        "bad_file" -> if (tr) "Dosya uygun değil (PDF/JPG/PNG/WebP, en çok 5 MB)." else "Unsupported file (PDF/JPG/PNG/WebP, max 5 MB)."
        "rate_limited" -> if (tr) "Çok sık denediniz, biraz bekleyin." else "Too many attempts, please wait."
        else -> if (tr) "Bir sorun oluştu." else "Something went wrong."
    }
}

private fun bodyField(e: HttpException, vararg keys: String): String? = runCatching {
    val body = e.response()?.errorBody()?.string() ?: return null
    val obj = WorkisJson.decodeFromString<kotlinx.serialization.json.JsonObject>(body)
    keys.firstNotNullOfOrNull { k ->
        (obj[k] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    }
}.getOrNull()

/** Pull the `error` code out of a non-2xx apply response body, if present. */
fun errorCodeFromBody(e: HttpException): String? = bodyField(e, "error")

/** Server-worded `message`/`error` from a non-2xx body, else the status idiom. */
fun messageFromBody(e: HttpException, lang: String): String =
    bodyField(e, "message", "error") ?: statusMessage(e.code(), lang)

suspend fun <T> safeCall(lang: String, block: suspend () -> T): ApiResult<T> =
    try {
        ApiResult.Ok(block())
    } catch (e: HttpException) {
        ApiResult.Err(e.code(), statusMessage(e.code(), lang))
    } catch (e: IOException) {
        ApiResult.Err(null, statusMessage(null, lang))
    } catch (e: Exception) {
        android.util.Log.e("WorkisApi", "unexpected error", e)
        ApiResult.Err(null, statusMessage(-1, lang))
    }
