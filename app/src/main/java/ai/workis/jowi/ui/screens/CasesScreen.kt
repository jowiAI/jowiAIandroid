package ai.workis.jowi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.Case
import ai.workis.jowi.data.CreateCaseBody
import ai.workis.jowi.data.safeCall
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CasesUiState {
    data object Loading : CasesUiState
    data class Data(val cases: List<Case>) : CasesUiState
    data class Error(val message: String) : CasesUiState
    data object NoProfile : CasesUiState
}

class CasesViewModel : ViewModel() {
    private val _state = MutableStateFlow<CasesUiState>(CasesUiState.Loading)
    val state: StateFlow<CasesUiState> = _state

    init { load() }

    fun load() {
        _state.value = CasesUiState.Loading
        viewModelScope.launch {
            when (val r = safeCall(Graph.language.value) { Graph.api.cases() }) {
                is ApiResult.Ok -> _state.value = CasesUiState.Data(r.value)
                is ApiResult.Err ->
                    // 403 = routing signal ("no buyer profile yet"), not an error screen
                    _state.value = if (r.status == 403) CasesUiState.NoProfile
                    else CasesUiState.Error(r.message)
            }
        }
    }

    fun create(symptom: String, onDone: () -> Unit) {
        viewModelScope.launch {
            when (safeCall(Graph.language.value) { Graph.api.createCase(CreateCaseBody(symptom)) }) {
                is ApiResult.Ok -> { onDone(); load() }
                is ApiResult.Err -> onDone()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CasesScreen(vm: CasesViewModel = viewModel()) {
    val colors = WorkisTheme.colors
    val state by vm.state.collectAsState()
    var showNewCase by remember { mutableStateOf(false) }
    var symptom by remember { mutableStateOf("") }

    Scaffold(
        containerColor = colors.canvas,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewCase = true },
                containerColor = Kiremit400,
                contentColor = OnKiremitFill,
            ) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.new_case_title)) }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.cases_title),
                    style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { vm.load() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, tint = colors.muted)
                }
            }
            Spacer(Modifier.height(14.dp))

            when (val s = state) {
                is CasesUiState.Loading -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { WorkisMark(size = 26, breathing = true) }

                is CasesUiState.NoProfile -> InfoBand(stringResource(R.string.no_profile_body))

                is CasesUiState.Error -> Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(40.dp))
                    Text(s.message, color = colors.danger, fontSize = 14.sp)
                    TextButton(onClick = { vm.load() }) {
                        Text(stringResource(R.string.retry), color = colors.accentText)
                    }
                }

                is CasesUiState.Data -> if (s.cases.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(60.dp))
                        Text(
                            stringResource(R.string.cases_empty),
                            color = colors.muted,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(s.cases) { case -> CaseCard(case) }
                    }
                }
            }
        }
    }

    if (showNewCase) {
        ModalBottomSheet(
            onDismissRequest = { showNewCase = false },
            containerColor = colors.surface,
        ) {
            Column(Modifier.padding(20.dp).imePadding()) {
                Text(
                    stringResource(R.string.new_case_title),
                    color = colors.ink,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = symptom,
                    onValueChange = { symptom = it },
                    placeholder = {
                        Text(stringResource(R.string.new_case_placeholder), color = colors.faint)
                    },
                    minLines = 3,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ai.workis.jowi.ui.theme.Kiremit500,
                        unfocusedBorderColor = colors.border,
                        focusedTextColor = colors.ink,
                        unfocusedTextColor = colors.ink,
                        cursorColor = colors.blue,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        vm.create(symptom) { symptom = ""; showNewCase = false }
                    },
                    enabled = symptom.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Kiremit400,
                        contentColor = OnKiremitFill,
                    ),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) { Text(stringResource(R.string.send), fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CaseCard(case: Case) {
    val colors = WorkisTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Text(
            case.title ?: case.symptom.orEmpty(),
            color = colors.ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(6.dp))
        Row {
            case.status?.let {
                Text(it.uppercase(), color = colors.accentText, fontSize = 11.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.weight(1f))
            case.createdAt?.take(10)?.let {
                Text(it, color = colors.faint, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun InfoBand(text: String) {
    // beige informational band (Xcode console note idiom) — used by the no-profile banner
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(BeigeBg, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) { Text(text, color = Beige, fontSize = 13.sp) }
}
