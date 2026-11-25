package ai.workis.jowi.ui.screens.console

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ConsoleSystemPage
import ai.workis.jowi.data.ScreenContext
import ai.workis.jowi.data.SystemPilot
import ai.workis.jowi.data.jsonBody
import ai.workis.jowi.data.partnerCall
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisSans
import ai.workis.jowi.ui.theme.WorkisTheme
import ai.workis.jowi.ui.theme.readingStep
import kotlinx.coroutines.launch

/**
 * Console → Sistem (web /workis/console/sistem/, page key `system`, ledger 128) — the
 * coordinator alone. What the app carries of the web page: the identity seals and API keys
 * (a broken seal FIRST, as a warning), the pilots and their page ticks, the test app
 * invitation. The acts that recover an account and the database door stay on the web —
 * `webOnly` names them and each gets an "opens on the web" line, nothing to press.
 */
class ConsoleSystemViewModel : ViewModel() {
    var page by mutableStateOf<ConsoleSystemPage?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set

    /** The last pilot act's sentence as the server sent it, and whether it was a refusal. */
    var pilotNote by mutableStateOf<String?>(null)
        private set
    var pilotNoteIsError by mutableStateOf(false)
        private set
    var appointEmail by mutableStateOf("")

    /** The open pilot's page (its userId); null = the system page. */
    var openPilot by mutableStateOf<String?>(null)
    /** The open pilot's ticks as shown, and its refusal. */
    var ticks by mutableStateOf<Set<String>>(emptySet())
    var pilotError by mutableStateOf<String?>(null)
        private set

    // the test app invitation
    var testerEmails by mutableStateOf("")
    var testerPlatform by mutableStateOf("android")
        private set
    var testerLink by mutableStateOf("")
    var testerLang by mutableStateOf("tr")
    var testerNote by mutableStateOf<String?>(null)
        private set
    var testerNoteIsError by mutableStateOf(false)
        private set
    private var seeded = false

    private val lang get() = Graph.language.value

    fun load() {
        viewModelScope.launch {
            val (p, err) = partnerCall(lang) { Graph.api.consoleSystem() }
            refreshing = false
            if (p != null) {
                page = p; loadError = null
                if (!seeded) { testerLink = p.testerLinks?.get(testerPlatform).orEmpty(); seeded = true }
            } else if (page == null) loadError = err
        }
    }

    fun refresh() { refreshing = true; load() }

    fun pickPlatform(p: String) {
        testerPlatform = p
        testerLink = page?.testerLinks?.get(p).orEmpty()
    }

    fun pilot(id: String?): SystemPilot? = page?.pilots?.firstOrNull { it.userId == id }

    fun openPilotPage(p: SystemPilot) {
        openPilot = p.userId
        ticks = p.pages.orEmpty().filter { it.on == true }.mapNotNull { it.key }.toSet()
        pilotError = null
    }

    /** The answer carries the new pilots — the list is drawn from it; the rest is re-read. */
    private fun takePilots(fresh: List<SystemPilot>?) {
        if (fresh != null) page = page?.copy(pilots = fresh)
        load()
    }

    private fun userIdValue(id: String): Any = id.toIntOrNull() ?: id

    fun appoint() {
        val email = appointEmail.trim()
        if (email.isEmpty() || busy) return
        busy = true
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.consoleSystemPilots(jsonBody("action" to "appoint", "email" to email)) }
            busy = false
            val ok = r?.success == true
            pilotNote = if (ok) r?.message else r?.error ?: err
            pilotNoteIsError = !ok
            if (ok) { appointEmail = ""; takePilots(r?.pilots) }
        }
    }

    /** The FULL list of ticked keys in the web's order — an unsent key is closed; `jowi` travels in the same list. */
    fun savePages() {
        val p = pilot(openPilot) ?: return
        val id = p.userId ?: return
        if (busy) return
        val keys = p.pages.orEmpty().mapNotNull { it.key }.filter { it in ticks }
        busy = true; pilotError = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) {
                Graph.api.consoleSystemPilots(jsonBody("action" to "pages", "userId" to userIdValue(id), "pages" to keys))
            }
            busy = false
            if (r?.success == true) { pilotNote = r.message; pilotNoteIsError = false; openPilot = null; takePilots(r.pilots) }
            else pilotError = r?.error ?: err
        }
    }

    fun dismissPilot() {
        val id = pilot(openPilot)?.userId ?: return
        if (busy) return
        busy = true; pilotError = null
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) { Graph.api.consoleSystemPilots(jsonBody("action" to "dismiss", "userId" to userIdValue(id))) }
            busy = false
            if (r?.success == true) { pilotNote = r.message; pilotNoteIsError = false; openPilot = null; takePilots(r.pilots) }
            else pilotError = r?.error ?: err
        }
    }

    fun sendInvite() {
        val emails = testerEmails.trim()
        if (emails.isEmpty() || busy) return
        busy = true
        viewModelScope.launch {
            val (r, err) = partnerCall(lang) {
                Graph.api.consoleSystemTesterInvite(
                    jsonBody("emails" to emails, "platform" to testerPlatform, "link" to testerLink.trim(), "lang" to testerLang),
                )
            }
            busy = false
            val ok = r?.success == true
            testerNote = if (ok) r?.message else r?.error ?: err
            testerNoteIsError = !ok
            if (ok) { testerEmails = ""; load() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleSystemScreen(onBack: () -> Unit) {
    val vm: ConsoleSystemViewModel = viewModel()
    val colors = WorkisTheme.colors
    LaunchedEffect(Unit) { ScreenContext.clear(); vm.load() }
    val open = vm.pilot(vm.openPilot)
    if (open != null) {
        BackHandler { vm.openPilot = null }
        SysPilotPage(open, vm)
        return
    }
    ConsoleSub(stringResource(R.string.console_system_title), onBack) {
        val page = vm.page
        when {
            page == null && vm.loadError != null -> Text(vm.loadError.orEmpty(), fontSize = 13.sp, color = colors.danger)
            page == null -> Box(Modifier.fillMaxWidth().padding(top = 30.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = Kiremit500)
            }
            else -> PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    item { SysIntegrity(page) }
                    item { SysPilots(page, vm) }
                    item { SysTester(page, vm) }
                    val known = page.webOnly.orEmpty().mapNotNull { k -> sysWebOnly(k) }
                    if (known.isNotEmpty()) item { SysWebOnly(known) }
                    item { Spacer(Modifier.height(30.dp)) }
                }
            }
        }
    }
}

// ── building blocks ──────────────────────────────────────────────────────────────────────

@Composable
private fun SysSection(icon: ImageVector, title: String, hint: String?, content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
            Icon(icon, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(15.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.ink)
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(20.dp))
                .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
        hint?.let { SysHint(it, Modifier.padding(horizontal = 4.dp)) }
    }
}

@Composable
private fun SysHint(words: String, modifier: Modifier = Modifier) {
    Text(words, fontSize = 12.sp, lineHeight = 16.sp, color = WorkisTheme.colors.faint, modifier = modifier)
}

/** The server's sentence after an act: a tick in green, a refusal with its cross — never colour alone. */
@Composable
private fun SysNote(words: String, error: Boolean) {
    val colors = WorkisTheme.colors
    val tint = if (error) colors.danger else colors.success
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(if (error) WorkisIcons.Cross else WorkisIcons.Check, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(14.dp))
        Text(words, fontSize = 13.sp, lineHeight = 18.sp, color = tint)
    }
}

@Composable
private fun SysPair(label: String, value: String, mono: Boolean = false) {
    val colors = WorkisTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
        Text(
            value, fontFamily = if (mono) WorkisMono else null, fontWeight = if (mono) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 14.sp, color = colors.ink,
        )
    }
}

@Composable
private fun SysField(value: String, onChange: (String) -> Unit, placeholder: String, keyboard: KeyboardType, mono: Boolean = false, singleLine: Boolean = true, modifier: Modifier = Modifier) {
    val colors = WorkisTheme.colors
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = singleLine, minLines = if (singleLine) 1 else 2, maxLines = if (singleLine) 1 else 5,
        placeholder = { Text(placeholder, fontSize = 14.sp, color = colors.faint) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false),
        textStyle = TextStyle(fontFamily = if (mono) WorkisMono else WorkisSans, fontSize = if (mono) 12.sp else 14.sp, color = colors.ink).readingStep(),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border, cursorColor = Kiremit500),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier,
    )
}

@Composable
private fun SysSegments(options: List<Pair<String, String>>, selected: String, enabled: Boolean, onPick: (String) -> Unit) {
    val colors = WorkisTheme.colors
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (key, label) ->
            SegmentedButton(
                selected = selected == key, onClick = { onPick(key) }, enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.ink.copy(alpha = 0.08f), activeContentColor = colors.ink,
                    inactiveContainerColor = colors.surface, inactiveContentColor = colors.muted,
                    activeBorderColor = colors.border, inactiveBorderColor = colors.border,
                ),
                icon = {},
            ) { Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium) }
        }
    }
}

// ── sections ─────────────────────────────────────────────────────────────────────────────

/** First on the phone: a broken seal is a warning the coordinator must see. */
@Composable
private fun SysIntegrity(page: ConsoleSystemPage) {
    val colors = WorkisTheme.colors
    val broken = page.seals?.broken.orEmpty()
    val enforcing = page.seals?.enforcing == true
    SysSection(WorkisIcons.Shield, stringResource(R.string.sys_integrity), stringResource(R.string.sys_integrity_hint)) {
        if (broken.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(WorkisIcons.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(15.dp))
                Text(
                    stringResource(R.string.sys_seals_intact) + if (enforcing) "" else " · " + stringResource(R.string.sys_observe_mode),
                    fontSize = 14.sp, color = colors.success,
                )
            }
        } else {
            Column(
                Modifier.fillMaxWidth().background(colors.danger.copy(alpha = 0.08f), RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(WorkisIcons.Warning, contentDescription = null, tint = colors.danger, modifier = Modifier.size(15.dp))
                    Text("${stringResource(R.string.sys_broken_seals)}: ${broken.size}", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colors.danger)
                }
                broken.forEach { b ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(b.email.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
                        b.lastKnown?.takeIf { it.isNotEmpty() && it != b.email }?.let {
                            Text("← ${stringResource(R.string.sys_last_written)}: $it", fontSize = 12.sp, color = colors.muted)
                        }
                    }
                }
                SysHint(stringResource(R.string.sys_broken_hint))
            }
        }
        SysPair(stringResource(R.string.sys_seal_check), stringResource(if (enforcing) R.string.sys_seal_enforcing else R.string.sys_observe_mode))
        page.tokens?.let { tok ->
            HorizontalDivider(color = colors.border)
            SysPair(stringResource(R.string.sys_tokens_hashed), "${tok.hashed ?: 0}", mono = true)
            SysPair(stringResource(R.string.sys_tokens_legacy), "${tok.legacy ?: 0}", mono = true)
            SysPair(stringResource(R.string.sys_tokens_legacy_used), "${tok.legacyUsed ?: 0}", mono = true)
            SysPair(stringResource(R.string.sys_tokens_plain), "${tok.plain ?: 0}", mono = true)
        }
    }
}

@Composable
private fun SysPilots(page: ConsoleSystemPage, vm: ConsoleSystemViewModel) {
    val colors = WorkisTheme.colors
    val pilots = page.pilots.orEmpty()
    SysSection(WorkisIcons.People, stringResource(R.string.sys_pilots), stringResource(R.string.sys_pilots_hint)) {
        vm.pilotNote?.takeIf { it.isNotEmpty() }?.let { SysNote(it, vm.pilotNoteIsError) }
        if (pilots.isEmpty()) SysHint(stringResource(R.string.sys_pilots_none))
        pilots.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider(color = colors.border)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(enabled = p.userId != null) { vm.openPilotPage(p) }.padding(vertical = 2.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(p.name?.takeIf { it.isNotEmpty() } ?: p.email.orEmpty(), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.ink)
                    if (!p.name.isNullOrEmpty()) p.email?.let { Text(it, fontSize = 12.sp, color = colors.muted) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val all = p.pages.orEmpty()
                        Text(
                            "${all.count { it.on == true }}/${all.size} ${stringResource(R.string.sys_open_word)}",
                            fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.muted,
                        )
                        if (p.active == false) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(WorkisIcons.Lock, contentDescription = null, tint = colors.danger, modifier = Modifier.size(12.dp))
                            Text(stringResource(R.string.sys_account_closed), fontWeight = FontWeight.Medium, fontSize = 11.sp, color = colors.danger)
                        }
                    }
                }
                Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.faint, modifier = Modifier.size(20.dp))
            }
        }
        HorizontalDivider(color = colors.border)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SysField(vm.appointEmail, { vm.appointEmail = it }, stringResource(R.string.sys_appoint_email), KeyboardType.Email, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = vm::appoint, enabled = !vm.busy && vm.appointEmail.contains("@")) {
                Text(stringResource(R.string.sys_appoint), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
            }
        }
    }
}

@Composable
private fun SysTester(page: ConsoleSystemPage, vm: ConsoleSystemViewModel) {
    val colors = WorkisTheme.colors
    val remembered = page.testerLinks.orEmpty().values.any { !it.isNullOrEmpty() }
    SysSection(Icons.Filled.Email, stringResource(R.string.sys_tester), stringResource(R.string.sys_tester_hint)) {
        vm.testerNote?.takeIf { it.isNotEmpty() }?.let { SysNote(it, vm.testerNoteIsError) }
        SysHint(stringResource(if (remembered) R.string.sys_link_remembered else R.string.sys_no_link))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.sys_tester_platform), fontSize = 12.sp, color = colors.muted)
            SysSegments(
                listOf("android" to stringResource(R.string.sys_android), "ios" to stringResource(R.string.sys_iphone)),
                vm.testerPlatform, !vm.busy, vm::pickPlatform,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.sys_tester_lang), fontSize = 12.sp, color = colors.muted)
            SysSegments(
                listOf("tr" to stringResource(R.string.sys_lang_turkish), "en" to stringResource(R.string.sys_lang_english)),
                vm.testerLang, !vm.busy,
            ) { vm.testerLang = it }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SysField(vm.testerLink, { vm.testerLink = it }, stringResource(R.string.sys_tester_link), KeyboardType.Uri, mono = true, modifier = Modifier.fillMaxWidth())
            SysHint(stringResource(R.string.sys_tester_link_hint))
        }
        SysField(vm.testerEmails, { vm.testerEmails = it }, stringResource(R.string.sys_tester_emails), KeyboardType.Email, singleLine = false, modifier = Modifier.fillMaxWidth())
        // the page's one filled action
        Button(
            onClick = vm::sendInvite, enabled = !vm.busy && vm.testerEmails.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = OnKiremitFill)
            else Text(stringResource(R.string.sys_tester_send), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OnKiremitFill)
        }
    }
}

private data class SysWebRow(val title: Int, val icon: ImageVector)

private fun sysWebOnly(key: String): SysWebRow? = when (key) {
    "database" -> SysWebRow(R.string.sys_web_database, WorkisIcons.Archive)
    "accountDoctor" -> SysWebRow(R.string.sys_web_account_doctor, WorkisIcons.PersonKey)
    "reseal" -> SysWebRow(R.string.sys_web_reseal, WorkisIcons.Shield)
    "passkeys" -> SysWebRow(R.string.sys_web_passkeys, WorkisIcons.Key)
    else -> null
}

@Composable
private fun SysWebOnly(rows: List<SysWebRow>) {
    val colors = WorkisTheme.colors
    SysSection(WorkisIcons.Globe, stringResource(R.string.sys_web_only), stringResource(R.string.sys_web_only_hint)) {
        rows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = colors.border)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(r.icon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp))
                Text(stringResource(r.title), fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.sys_opens_on_web), fontSize = 12.sp, color = colors.faint)
            }
        }
    }
}

// ── one pilot's page ticks ───────────────────────────────────────────────────────────────

/**
 * One switch per entry of the pilot's `pages` (label from the server); "Kaydet" sends the FULL
 * list of ticked keys — an unsent key is closed, `jowi` travels in the same list. "Görevden al"
 * asks first.
 */
@Composable
private fun SysPilotPage(p: SystemPilot, vm: ConsoleSystemViewModel) {
    val colors = WorkisTheme.colors
    var confirm by remember { mutableStateOf(false) }
    val pages = p.pages.orEmpty().filter { !it.key.isNullOrEmpty() }
    val original = pages.filter { it.on == true }.mapNotNull { it.key }.toSet()
    ConsoleSub(p.name?.takeIf { it.isNotEmpty() } ?: p.email.orEmpty(), { vm.openPilot = null }) {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            vm.pilotError?.takeIf { it.isNotEmpty() }?.let { item { SysNote(it, error = true) } }
            item {
                SysSection(WorkisIcons.Checklist, stringResource(R.string.sys_pilot_pages), stringResource(R.string.sys_pilot_pages_hint)) {
                    pages.forEachIndexed { i, pg ->
                        val key = pg.key.orEmpty()
                        if (i > 0) HorizontalDivider(color = colors.border)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(pg.label?.takeIf { it.isNotEmpty() } ?: key, fontSize = 14.sp, color = colors.ink, modifier = Modifier.weight(1f))
                            Switch(
                                checked = key in vm.ticks, enabled = !vm.busy,
                                onCheckedChange = { on -> vm.ticks = if (on) vm.ticks + key else vm.ticks - key },
                                colors = SwitchDefaults.colors(checkedTrackColor = Kiremit500, checkedThumbColor = colors.surface),
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { vm.ticks = pages.mapNotNull { it.key }.toSet() }, enabled = !vm.busy) {
                            Text(stringResource(R.string.sys_tick_all), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
                        }
                        OutlinedButton(onClick = { vm.ticks = emptySet() }, enabled = !vm.busy) {
                            Text(stringResource(R.string.sys_clear_all), fontWeight = FontWeight.Medium, fontSize = 13.sp, color = colors.ink)
                        }
                    }
                    // only when the ticks differ from the server's — the page's one filled action
                    if (vm.ticks != original) Button(
                        onClick = vm::savePages, enabled = !vm.busy,
                        colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (vm.busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = OnKiremitFill)
                        else Text(stringResource(R.string.sys_save), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = OnKiremitFill)
                    }
                }
            }
            item {
                OutlinedButton(onClick = { confirm = true }, enabled = !vm.busy, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.sys_dismiss), fontWeight = FontWeight.Medium, fontSize = 14.sp, color = colors.danger)
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        containerColor = colors.surface,
        title = { Text(stringResource(R.string.sys_dismiss_confirm), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colors.ink) },
        confirmButton = {
            TextButton(onClick = { confirm = false; vm.dismissPilot() }) {
                Text(stringResource(R.string.sys_dismiss), fontWeight = FontWeight.SemiBold, color = colors.danger)
            }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel), color = colors.muted) } },
    )
}
