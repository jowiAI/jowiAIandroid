package ai.workis.jowi.ui.screens.expert

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.CloseReviewBody
import ai.workis.jowi.data.ConsoleQuestion
import ai.workis.jowi.data.ExpertAnswerBody
import ai.workis.jowi.data.ExpertConsult
import ai.workis.jowi.data.ExpertConsultDetail
import ai.workis.jowi.data.ExpertReviews
import ai.workis.jowi.data.ExpertSummary
import ai.workis.jowi.data.TextBody
import ai.workis.jowi.data.UnavailableBody
import ai.workis.jowi.data.messageFromBody
import ai.workis.jowi.data.safeCall
import ai.workis.jowi.data.statusMessage
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/**
 * The expert seat's console state (role 5) — /workis/expert/… slice 1.
 * Nullable datasets = "not loaded yet"; actions hand back the server's
 * message (beige note), never a red error for a business wall.
 */
class ExpertViewModel : ViewModel() {
    var summary by mutableStateOf<ExpertSummary?>(null)
    var summaryError by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)

    var questions by mutableStateOf<List<ConsoleQuestion>?>(null)
    var questionsError by mutableStateOf<String?>(null)

    var reviews by mutableStateOf<ExpertReviews?>(null)
    var reviewsError by mutableStateOf<String?>(null)

    var consults by mutableStateOf<List<ExpertConsult>?>(null)
    var consultsError by mutableStateOf<String?>(null)
    var thread by mutableStateOf<ExpertConsultDetail?>(null)
    var threadError by mutableStateOf<String?>(null)

    var busyKey by mutableStateOf<String?>(null)
    var actionNote by mutableStateOf<String?>(null)

    private val lang get() = Graph.language.value

    private fun <T> fetch(onError: (String?) -> Unit, assign: (T) -> Unit, call: suspend () -> T) {
        onError(null)
        viewModelScope.launch {
            when (val r = safeCall(lang) { call() }) {
                is ApiResult.Ok -> assign(r.value)
                is ApiResult.Err -> onError(r.message)
            }
        }
    }

    fun loadSummary() {
        loading = summary == null
        fetch({ summaryError = it }, { summary = it; loading = false }) { Graph.api.expertSummary() }
    }

    fun loadQuestions() =
        fetch({ questionsError = it }, { questions = it.questions.orEmpty() }) { Graph.api.expertQuestions() }

    fun loadReviews() = fetch({ reviewsError = it }, { reviews = it }) { Graph.api.expertReviews() }

    fun loadConsults() =
        fetch({ consultsError = it }, { consults = it.consults.orEmpty() }) { Graph.api.expertConsults() }

    fun loadThread(id: String) = fetch({ threadError = it }, { thread = it }) { Graph.api.expertConsult(id) }

    /** POST → (ok, server message). Business walls (403 affiliate…) arrive as text, not exceptions. */
    private suspend fun post(call: suspend () -> Pair<Boolean, String?>): Pair<Boolean, String?> =
        try {
            call()
        } catch (e: HttpException) {
            false to messageFromBody(e, lang)
        } catch (e: IOException) {
            false to statusMessage(null, lang)
        } catch (e: Exception) {
            false to statusMessage(-1, lang)
        }

    private fun action(key: String, after: (Boolean, String?) -> Unit, call: suspend () -> Pair<Boolean, String?>) {
        busyKey = key
        viewModelScope.launch {
            val (ok, msg) = post(call)
            busyKey = null
            actionNote = msg
            after(ok, msg)
            if (ok) loadSummary()
        }
    }

    fun answer(id: String, text: String, teach: Boolean, onDone: () -> Unit) =
        action(id, { ok, _ -> if (ok) { loadQuestions(); onDone() } }) {
            Graph.api.expertAnswer(id, ExpertAnswerBody(text, teach)).let { it.success to (it.message ?: it.error) }
        }

    fun closeReview(sessionKey: String, retire: List<String>, correction: String) =
        action(sessionKey, { ok, _ -> if (ok) loadReviews() }) {
            Graph.api.expertCloseReview(sessionKey, CloseReviewBody(retire, correction.ifBlank { null }))
                .let { it.success to (it.message ?: it.error) }
        }

    fun reply(consultId: String, text: String, onDone: () -> Unit) =
        action(consultId, { ok, _ -> if (ok) { loadThread(consultId); onDone() } }) {
            Graph.api.expertConsultReply(consultId, TextBody(text)).let { it.success to (it.message ?: it.error) }
        }

    /** Agreement 2.1 — the buyer hears it in the channel and on their feed. */
    fun unavailable(consultId: String, note: String) =
        action(consultId, { ok, _ -> if (ok) loadThread(consultId) }) {
            Graph.api.expertConsultUnavailable(consultId, UnavailableBody(note.ifBlank { null }))
                .let { it.success to (it.message ?: it.error) }
        }
}
