package ai.workis.jowi.data

import android.content.SharedPreferences
import kotlinx.serialization.Serializable

/**
 * `GET /workis/public/home/?lang=` (no token, Django 2026-10-14): the homepage's five
 * cards and the twelve-page "Nasıl çalışır?" story, in one language. Static content —
 * every word is the server's; nothing here goes into string resources.
 */
@Serializable
data class PublicHome(
    val success: Boolean = false,
    val lang: String? = null,
    val intro: String? = null,
    val cards: List<PublicCard> = emptyList(),
    val story: PublicStory? = null,
)

/** One homepage card — web order what · where · who · how · why; `how` opens the story. */
@Serializable
data class PublicCard(
    val id: String = "",
    val title: String = "",
    /** The one-word label of the chip row once something has been asked. */
    val short: String = "",
    val description: String = "",
    /** The sentence Jowi answers when the card is pressed. */
    val question: String = "",
)

@Serializable
data class PublicStory(
    val steps: List<PublicStoryStep> = emptyList(),
    val pages: List<PublicStoryPage> = emptyList(),
)

@Serializable
data class PublicStoryStep(val n: Int = 0, val label: String = "")

@Serializable
data class PublicStoryPage(
    val id: String = "",
    /** "1", "1–2", "9+", "→" — a place on the nine-step spine. */
    val step: String = "",
    /** live | partly | planned */
    val status: String = "live",
    val stepLabel: String = "",
    val feature: String = "",
    val hook: String = "",
    val what: String = "",
    /** A sample line in Jowi's voice — an illustration, never a fact. */
    val jowi: String = "",
    val screenTitle: String = "",
    /** Absolute, or null = draw nothing (the drawn stand-ins are web-only). */
    val frameUrl: String? = null,
)

/** Loads the public home per language; the last good answer is kept and drawn offline. */
class PublicHomeStore(private val api: WorkisApi, private val prefs: SharedPreferences) {
    suspend fun publicHome(lang: String): PublicHome? {
        val key = "workis_public_home_$lang"
        val fresh = try {
            api.publicHome(lang)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (fresh != null && fresh.success) {
            runCatching { prefs.edit().putString(key, WorkisJson.encodeToString(PublicHome.serializer(), fresh)).apply() }
            return fresh
        }
        return prefs.getString(key, null)?.let {
            runCatching { WorkisJson.decodeFromString(PublicHome.serializer(), it) }.getOrNull()
        }
    }
}
