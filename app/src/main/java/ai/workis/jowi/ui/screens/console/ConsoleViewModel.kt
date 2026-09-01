package ai.workis.jowi.ui.screens.console

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.AnswerBody
import ai.workis.jowi.data.ConsoleApplications
import ai.workis.jowi.data.ConsoleConversations
import ai.workis.jowi.data.ConsolePartners
import ai.workis.jowi.data.ConsoleQuestions
import ai.workis.jowi.data.ConsoleSummary
import ai.workis.jowi.data.ConversationDetail
import ai.workis.jowi.data.ReplyBody
import ai.workis.jowi.data.safeCall
import kotlinx.coroutines.launch

/** Android twin of the WorkisService console slice: nullable datasets = "not loaded yet". */
class ConsoleViewModel : ViewModel() {
    var summary by mutableStateOf<ConsoleSummary?>(null)
    var apps by mutableStateOf<ConsoleApplications?>(null)
    var questions by mutableStateOf<ConsoleQuestions?>(null)
    var partners by mutableStateOf<ConsolePartners?>(null)
    var conversations by mutableStateOf<ConsoleConversations?>(null)
    var thread by mutableStateOf<ConversationDetail?>(null)

    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var busyId by mutableStateOf<String?>(null) // id whose action is in flight

    private val lang get() = Graph.language.value

    private fun <T> fetch(assign: (T) -> Unit, call: suspend () -> T) {
        loading = true
        error = null
        viewModelScope.launch {
            when (val r = safeCall(lang) { call() }) {
                is ApiResult.Ok -> assign(r.value)
                is ApiResult.Err -> error = r.message
            }
            loading = false
        }
    }

    fun loadSummary() = fetch({ summary = it }) { Graph.api.consoleSummary() }
    fun loadApps() = fetch({ apps = it }) { Graph.api.consoleApplications() }
    fun loadQuestions() = fetch({ questions = it }) { Graph.api.consoleQuestions() }
    fun loadPartners() = fetch({ partners = it }) { Graph.api.consolePartners() }
    fun loadConversations() = fetch({ conversations = it }) { Graph.api.consoleConversations() }
    fun loadThread(id: String) = fetch({ thread = it }) { Graph.api.conversationDetail(id) }

    private fun action(id: String, after: () -> Unit = {}, call: suspend () -> Unit) {
        busyId = id
        error = null
        viewModelScope.launch {
            when (val r = safeCall(lang) { call() }) {
                is ApiResult.Ok -> after()
                is ApiResult.Err -> error = r.message
            }
            busyId = null
        }
    }

    fun createPartner(id: String) =
        action(id, after = { loadApps(); loadSummary() }) { Graph.api.applicationCreatePartner(id) }

    fun invite(id: String) = action(id) { Graph.api.applicationInvite(id) }

    fun closeLead(id: String) =
        action(id, after = { loadApps() }) { Graph.api.applicationClose(id) }

    fun approvePartner(partnerId: String) =
        action(partnerId, after = { loadApps(); loadSummary() }) { Graph.api.partnerApprove(partnerId) }

    fun questionDone(id: String) =
        action(id, after = { loadQuestions(); loadSummary() }) { Graph.api.questionDone(id) }

    fun answerQuestion(id: String, answer: String, teach: Boolean, onDone: () -> Unit) =
        action(id, after = { loadQuestions(); loadSummary(); onDone() }) {
            Graph.api.questionAnswer(id, AnswerBody(answer, teach))
        }

    fun reply(convId: String, text: String, internal: Boolean, onDone: () -> Unit) =
        action(convId, after = { loadThread(convId); onDone() }) {
            Graph.api.conversationReply(convId, ReplyBody(text, if (internal) true else null))
        }
}
