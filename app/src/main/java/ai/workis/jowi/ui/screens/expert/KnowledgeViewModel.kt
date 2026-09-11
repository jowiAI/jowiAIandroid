package ai.workis.jowi.ui.screens.expert

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.CompanionPair
import ai.workis.jowi.data.ConsoleKnowledgeBody
import ai.workis.jowi.data.ExpertKnowledge
import ai.workis.jowi.data.KnowledgeBody
import ai.workis.jowi.data.KnowledgeConflict
import ai.workis.jowi.data.VerdictBody
import ai.workis.jowi.data.messageFromBody
import ai.workis.jowi.data.safeCall
import ai.workis.jowi.data.statusMessage
import kotlinx.coroutines.launch
import retrofit2.HttpException

/** Which seat's knowledge door a screen talks to — one implementation, two doors. */
enum class KnowledgeSeat { Expert, Console }

class KnowledgeViewModel : ViewModel() {
    var seat: KnowledgeSeat = KnowledgeSeat.Expert
        private set
    var data by mutableStateOf<ExpertKnowledge?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var conflicts by mutableStateOf<List<KnowledgeConflict>>(emptyList())
        private set
    var saveNote by mutableStateOf<String?>(null)
    var saving by mutableStateOf(false)
        private set
    /** Console only: the product pairs awaiting a staff verdict (role 3; a lead's 403 keeps it empty). */
    var companions by mutableStateOf<List<CompanionPair>>(emptyList())
        private set
    var busyPair by mutableStateOf<String?>(null)
        private set
    /** The Enter search on the console = the web's semantic search (hits marked). */
    var semantic by mutableStateOf(false)

    private val lang get() = Graph.language.value

    fun bind(seat: KnowledgeSeat) { this.seat = seat }

    fun load(q: String) {
        error = null
        viewModelScope.launch {
            val r = safeCall(lang) {
                when (seat) {
                    KnowledgeSeat.Expert -> Graph.api.expertKnowledge(q.ifBlank { null })
                    KnowledgeSeat.Console -> Graph.api.consoleKnowledge(q.ifBlank { null }, if (semantic && q.isNotBlank()) 1 else null)
                }
            }
            when (r) {
                is ApiResult.Ok -> data = r.value
                is ApiResult.Err -> error = r.message
            }
            if (seat == KnowledgeSeat.Console) {
                // 403 for a lead → silently empty, no error shown
                companions = runCatching { Graph.api.consoleCompanions().cards.orEmpty() }.getOrDefault(emptyList())
            }
        }
    }

    fun add(text: String, query: String, onSaved: () -> Unit) {
        if (saving) return
        saving = true
        saveNote = null
        viewModelScope.launch {
            val (ok, msg) = try {
                val r = when (seat) {
                    KnowledgeSeat.Expert -> Graph.api.expertAddKnowledge(KnowledgeBody(text))
                    KnowledgeSeat.Console -> Graph.api.consoleAddKnowledge(ConsoleKnowledgeBody(text))
                }
                if (r.success) conflicts = r.conflicts.orEmpty()
                r.success to (r.message ?: r.error)
            } catch (e: HttpException) { false to messageFromBody(e, lang) } catch (e: Exception) { false to statusMessage(null, lang) }
            saving = false
            saveNote = msg
            if (ok) { onSaved(); load(query) }
        }
    }

    fun toggle(id: String, query: String) {
        viewModelScope.launch {
            runCatching {
                when (seat) {
                    KnowledgeSeat.Expert -> Graph.api.expertToggleUnit(id)
                    KnowledgeSeat.Console -> Graph.api.consoleToggleUnit(id)
                }
            }
            load(query)
        }
    }

    fun pairVerdict(id: String, confirm: Boolean) {
        if (busyPair != null) return
        busyPair = id
        viewModelScope.launch {
            val (ok, msg) = try {
                Graph.api.consoleCompanionVerdict(id, VerdictBody(if (confirm) "confirm" else "reject")).let { it.success to (it.message ?: it.error) }
            } catch (e: HttpException) { false to messageFromBody(e, lang) } catch (e: Exception) { false to statusMessage(null, lang) }
            busyPair = null
            if (ok) companions = companions.filter { it.id != id } else saveNote = msg
        }
    }
}
