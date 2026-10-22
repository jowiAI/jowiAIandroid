package ai.workis.jowi.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * What this account can walk into — `GET auth/workspaces/`, read once per session
 * (ledger 20). A HOSPITALITY room puts Kararlar on the bar and takes the seatless
 * "Başvuru" door OFF it; a room and no Workis seat (`jowiOnly`) also takes the Jowi
 * tab off — the manager writes questions and instructions into the same field.
 * Unreachable → the last verdict stays (null = not yet read: nothing on the bar changes).
 */
class WorkspacesStore(private val api: WorkisApi) {
    private val _value = MutableStateFlow<JowiWorkspaces?>(null)
    val value: StateFlow<JowiWorkspaces?> = _value

    suspend fun refresh() {
        runCatching { api.workspaces() }.getOrNull()?.let { _value.value = it }
    }

    fun clear() { _value.value = null }
}
