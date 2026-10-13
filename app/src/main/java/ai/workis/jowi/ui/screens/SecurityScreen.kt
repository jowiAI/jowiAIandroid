package ai.workis.jowi.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import ai.workis.jowi.ui.theme.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.Passkey
import ai.workis.jowi.data.PasskeyList
import ai.workis.jowi.data.PasskeyService
import ai.workis.jowi.data.SignInEvent
import ai.workis.jowi.data.SignIns
import ai.workis.jowi.data.deviceLabel
import ai.workis.jowi.data.shortDate
import ai.workis.jowi.data.userId
import ai.workis.jowi.ui.screens.console.ConsoleSub
import ai.workis.jowi.ui.screens.expert.NoteBand
import ai.workis.jowi.ui.screens.partner.ConfirmDialog
import ai.workis.jowi.ui.theme.Beige
import ai.workis.jowi.ui.theme.BeigeBg
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import android.content.Context
import kotlinx.coroutines.launch

/**
 * Hesap → Güvenlik (API_CONTRACT §2 "Passkeys"). Every door here is the
 * server's to open: the four `can…` flags say what THIS session may do and the
 * screen never derives one — a missing flag is a shut door. A closed system
 * sheet is not a failure and says nothing; a refusal is printed in the server's
 * own sentence.
 */
class SecurityViewModel : ViewModel() {
    var data by mutableStateOf<PasskeyList?>(null)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var note by mutableStateOf<String?>(null)
    var busy by mutableStateOf(false)
        private set

    val passkeys: List<Passkey> get() = data?.passkeys.orEmpty()
    /** Absent means closed: the app never grants itself a door. */
    val canAdd get() = data?.canAddPasskey == true
    val canRemove get() = data?.canRemovePasskey == true
    val canChangeCodeSignIn get() = data?.canChangeCodeSignIn == true
    val codeSignInAllowed get() = data?.codeSignInAllowed == true

    /** "Son girişler" — an unreachable history is not an error screen. */
    var signIns by mutableStateOf<SignIns?>(null)
        private set

    /**
     * A code-opened session on an account that HAS a passkey can get to a
     * passkey session from here — which is why its switch is not dead.
     */
    val canReopen get() =
        Graph.passkeys.available && passkeys.isNotEmpty() && Graph.auth.signedInWith() != "passkey"

    private suspend fun fetchList() {
        runCatching { Graph.passkeys.list() }
            .onSuccess { data = it; loadError = null }
            .onFailure { e -> if (data == null) loadError = (e as? PasskeyService.Refused)?.message }
    }

    fun load() {
        viewModelScope.launch { fetchList() }
        viewModelScope.launch {
            runCatching { Graph.api.signIns() }.onSuccess { if (it.success) signIns = it }
        }
    }

    /** One place for the three rules: nothing on a cancel, the server's words on a refusal. */
    private fun run(block: suspend () -> String?) {
        if (busy) return
        busy = true; note = null
        viewModelScope.launch {
            try {
                note = block()
            } catch (e: PasskeyService.Cancelled) {
                // the person closed the sheet — that is an answer, not an error
            } catch (e: PasskeyService.Refused) {
                note = e.message
            }
            busy = false
            load()
        }
    }

    fun create(context: Context, created: String) = run {
        Graph.passkeys.register(context, deviceLabel())
        created
    }

    fun remove(id: Int) = run {
        Graph.passkeys.remove(id)
        null
    }

    /**
     * The switch takes the server's answer as truth, never the tap's optimism —
     * and it is never a dead control. In a code-opened session the change is not
     * allowed yet, but the way there is: touching the switch first re-opens the
     * session with the passkey, then ASKS AGAIN, and only a server that now says
     * `canChangeCodeSignIn` gets the change with its own fresh assertion. Two
     * system sheets in a row, deliberately: the first opens a passkey session,
     * the second is the proof the change must carry. Nothing is derived — being
     * touchable only means "you can get there from here".
     *
     * Closing the door needs that proof as much as opening it does: someone who
     * came in through the code because the phone is lost would otherwise lock
     * themselves out for good.
     */
    fun setCodeSignIn(context: Context, allowed: Boolean, otherAccount: String, stillRefused: String) = run {
        if (!canChangeCodeSignIn) {
            if (!Graph.auth.reopenWithPasskey(Graph.passkeys.signIn(context))) return@run otherAccount
            fetchList()
            // the server's word still stands — but two system sheets and then
            // silence reads as a broken control, so the screen says that much.
            // It is never sent anyway to collect a refusal.
            if (!canChangeCodeSignIn) return@run stillRefused
        }
        val now = Graph.passkeys.setCodeSignIn(context, allowed)
        data = data?.copy(codeSignInAllowed = now)
        null
    }

    /**
     * The way out of the dead end: a code-opened session on an account that HAS
     * a passkey cannot turn the switch, and signing out would only send the
     * person back to the inbox. The new key is adopted in place; a key belonging
     * to a DIFFERENT account changes nothing and says so.
     */
    fun reopen(context: Context, otherAccount: String) = run {
        val resp = Graph.passkeys.signIn(context)
        if (Graph.auth.reopenWithPasskey(resp)) null else otherAccount
    }
}

@Composable
fun SecurityScreen(onBack: () -> Unit) {
    val vm: SecurityViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    var removeTarget by remember { mutableStateOf<Passkey?>(null) }
    LaunchedEffect(Unit) { vm.load() }

    val createdWord = stringResource(R.string.passkey_created)
    val otherAccount = stringResource(R.string.passkey_other_account)
    val stillRefused = stringResource(R.string.st_error)

    ConsoleSub(stringResource(R.string.security_title), onBack, trailing = {
        if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Kiremit500)
    }) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NoteBand(vm.note)
            vm.loadError?.let { Text(it, fontSize = 13.sp, color = colors.danger) }

            Spacer(Modifier.height(4.dp))
            Eyebrow(stringResource(R.string.passkeys_section))
            SecurityCard {
                if (vm.passkeys.isEmpty()) {
                    Text(stringResource(R.string.passkeys_empty), fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(vertical = 6.dp))
                } else {
                    vm.passkeys.forEachIndexed { i, key ->
                        if (i > 0) Divider()
                        PasskeyRow(key, canRemove = vm.canRemove, busy = vm.busy) { removeTarget = key }
                    }
                }
            }
            Footnote(stringResource(R.string.passkeys_footer))

            if (vm.canAdd) {
                Button(
                    onClick = { vm.create(context, createdWord) },
                    enabled = !vm.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.passkey_create), fontWeight = FontWeight.SemiBold, fontSize = 15.sp) }
            }

            // the switch is not a mode: it opens or closes ONE door, and only an
            // account that already has a key can be asked about it
            if (vm.passkeys.isNotEmpty()) {
                SecurityCard {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            stringResource(R.string.code_sign_in_toggle),
                            fontSize = 14.sp, lineHeight = 19.sp,
                            color = if (vm.canChangeCodeSignIn) colors.ink else colors.muted,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Switch(
                            checked = vm.codeSignInAllowed,
                            // dead only when the change is refused AND there is no
                            // way to a passkey session from here
                            enabled = (vm.canChangeCodeSignIn || vm.canReopen) && !vm.busy,
                            onCheckedChange = { vm.setCodeSignIn(context, it, otherAccount, stillRefused) },
                            colors = SwitchDefaults.colors(checkedTrackColor = colors.blue),
                        )
                    }
                }
                Footnote(
                    listOfNotNull(
                        stringResource(R.string.code_sign_in_footer),
                        // said once: while the way out is offered below, "locked"
                        // is not the whole truth and repeating it is noise
                        stringResource(R.string.passkey_needs_passkey_session)
                            .takeIf { !vm.canChangeCodeSignIn && !vm.canReopen },
                    ).joinToString(" "),
                )
            }

            // THIS session lives in one place: how it was opened, when, from
            // where — and the way to re-open it. Its address was the thing the
            // screen could not answer while the row hid among the others.
            val openedWithPasskey = Graph.auth.signedInWith() == "passkey"
            val current = vm.signIns?.recent.orEmpty().firstOrNull { it.current == true }
            Spacer(Modifier.height(4.dp))
            Eyebrow(stringResource(R.string.session_opened_with))
            SecurityCard {
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        if (openedWithPasskey) WorkisIcons.PersonKey else WorkisIcons.Tray,
                        contentDescription = null, tint = Kiremit500, modifier = Modifier.size(16.dp).padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            stringResource(if (openedWithPasskey) R.string.session_with_passkey else R.string.session_with_code),
                            fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink,
                        )
                        // until the history answers — or on a session older than
                        // the server's `current` — the one line stands alone
                        current?.let { EventDetail(it) }
                    }
                }
            }
            if (!openedWithPasskey && vm.canReopen) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BeigeBg, RoundedCornerShape(16.dp))
                        .clickable(enabled = !vm.busy) { vm.reopen(context, otherAccount) }
                        .padding(14.dp),
                ) {
                    Icon(WorkisIcons.PersonKey, contentDescription = null, tint = Beige, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.session_reopen_with_passkey),
                        fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Beige,
                    )
                }
                Footnote(stringResource(R.string.session_reopen_footer))
            }

            // the OTHER sign-ins, under their own heading
            vm.signIns?.let { EarlierSignIns(it, current) }

            Spacer(Modifier.height(30.dp))
        }
    }

    removeTarget?.let { key ->
        ConfirmDialog(
            stringResource(R.string.passkey_remove_title),
            stringResource(R.string.passkey_remove_body),
            destructive = true,
            onDismiss = { removeTarget = null },
        ) { key.id?.let { vm.remove(it) } }
    }
}

/** label, then what the key IS: synced or device-bound, born, last used. */
@Composable
private fun PasskeyRow(key: Passkey, canRemove: Boolean, busy: Boolean, onRemove: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(key.label.orEmpty().ifEmpty { "—" }, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink)
            Text(
                listOfNotNull(
                    stringResource(if (key.synced == true) R.string.passkey_synced else R.string.passkey_device_only),
                    shortDate(key.createdAt).takeIf { it.isNotEmpty() }?.let { stringResource(R.string.passkey_created_at) + " " + it },
                    shortDate(key.lastUsedAt).takeIf { it.isNotEmpty() }?.let { stringResource(R.string.passkey_last_used) + " " + it },
                ).joinToString(" · "),
                fontFamily = WorkisMono, fontSize = 10.sp, lineHeight = 15.sp, color = colors.faint,
            )
        }
        if (canRemove) {
            Text(
                stringResource(R.string.passkey_remove),
                fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.danger,
                modifier = Modifier.clickable(enabled = !busy, onClick = onRemove).padding(start = 10.dp, top = 4.dp, bottom = 4.dp),
            )
        }
    }
}

/**
 * "Son girişler". `previous` leads — the sign-in before this session — and
 * `signInsFirst` stands in when there is none: the history starts at its
 * deploy, the account does not. A refused attempt is the most interesting line
 * an owner can read here, so it keeps its method word and says why it failed,
 * as a shape and words rather than colour alone.
 */
@Composable
private fun EarlierSignIns(s: SignIns, current: SignInEvent?) {
    val colors = WorkisTheme.colors
    // this session has its own section above; what is left is everyone else
    val others = s.recent.orEmpty().filter { it !== current && it.current != true }
    val previous = s.previous
    // `previous` is not a row of its own — it IS one of these, matched by the
    // moment and the method. It only needs a caption when it is not the one
    // leading the list, i.e. a newer sign-in from another device sits above it.
    val prevIndex = previous?.let { p -> others.indexOfFirst { it.at == p.at && it.method == p.method } } ?: -1
    // older than the ten shown: it follows them, captioned
    val prevBelow = previous.takeIf { it != null && prevIndex < 0 }

    Spacer(Modifier.height(4.dp))
    Eyebrow(stringResource(R.string.sign_ins_section))
    SecurityCard {
        if (others.isEmpty() && prevBelow == null) {
            Text(
                stringResource(R.string.sign_ins_first),
                fontSize = 14.sp, color = colors.muted, modifier = Modifier.padding(vertical = 8.dp),
            )
        }
        others.forEachIndexed { i, e ->
            if (i > 0) Divider()
            SignInRow(e, caption = stringResource(R.string.sign_ins_previous).takeIf { i == prevIndex && i > 0 })
        }
        prevBelow?.let {
            if (others.isNotEmpty()) Divider()
            SignInRow(it, caption = stringResource(R.string.sign_ins_previous))
        }
    }
    Footnote(stringResource(R.string.sign_ins_footer))

    // Anyone who knows an address can produce refusals at will, so they keep
    // their own list instead of crowding the real sign-ins out of `recent`.
    val refused = s.refusedRecent.orEmpty()
    if (refused.isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Eyebrow(stringResource(R.string.sign_ins_refused_section))
        SecurityCard {
            refused.forEachIndexed { i, e ->
                if (i > 0) Divider()
                SignInRow(e)
            }
        }
    }
}

@Composable
private fun SignInRow(e: SignInEvent, caption: String? = null) {
    val colors = WorkisTheme.colors
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        // no badge marks this session here: it has its own section, and a
        // coloured mark would read as the app vouching for a row the PERSON
        // is the one to judge
        caption?.let { Text(it, fontSize = 12.sp, color = colors.muted) }
        Text(methodWord(e.method), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = colors.ink)
        EventDetail(e)
        // a refusal is a status row: the mark and the word, never the colour alone
        e.refused?.takeIf { it.isNotEmpty() }?.let { reason ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(WorkisIcons.Warning, contentDescription = null, tint = Beige, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(
                        when (reason) {
                            "passkey_required" -> R.string.sign_in_refused_passkey
                            "account_frozen" -> R.string.sign_in_refused_frozen
                            else -> R.string.sign_in_refused
                        },
                    ),
                    fontSize = 12.sp, lineHeight = 17.sp, color = Beige,
                )
            }
        }
        // one person re-opening a session in place, not a second device
        if (e.note == "reopen") {
            Text(stringResource(R.string.sign_in_reopen), fontSize = 12.sp, lineHeight = 17.sp, color = colors.muted)
        }
    }
}

/**
 * "date · door · device", then the address on a LINE OF ITS OWN. As the tail of
 * one line it wrapped and disappeared — and it is the fact a person comes to
 * this screen to read. Mono, so digits line up, and selectable, so it can be
 * carried into a message. (A `172.31.x.x` from before Django's fix is shown as
 * it arrives; nothing is repaired here.)
 */
@Composable
private fun EventDetail(e: SignInEvent) {
    val colors = WorkisTheme.colors
    Text(
        listOfNotNull(
            listOf(shortDate(e.at), clockOf(e.at)).filter { it.isNotEmpty() }.joinToString(" ").takeIf { it.isNotEmpty() },
            stringResource(if (e.door == "web") R.string.sign_in_door_web else R.string.sign_in_door_app),
            e.device?.takeIf { it.isNotEmpty() },
        ).joinToString(" · "),
        fontSize = 12.sp, lineHeight = 17.sp, color = colors.muted,
    )
    e.ip?.takeIf { it.isNotEmpty() }?.let {
        SelectionContainer {
            Text(it, fontFamily = WorkisMono, fontSize = 13.sp, lineHeight = 18.sp, color = colors.faint)
        }
    }
}

/** Google and Apple are names and stay as they are; the rest are ours to say. */
@Composable
private fun methodWord(method: String?): String = when (method) {
    "passkey" -> stringResource(R.string.sign_in_method_passkey)
    "code" -> stringResource(R.string.sign_in_method_code)
    "link" -> stringResource(R.string.sign_in_method_link)
    "password" -> stringResource(R.string.sign_in_method_password)
    "register" -> stringResource(R.string.sign_in_method_register)
    "google" -> "Google"
    "apple" -> "Apple"
    else -> stringResource(R.string.sign_in_method_other)
}

/**
 * The enrolment offer: once per account per device, in a CODE-opened session
 * that may add a key and has none. "Şimdi değil" is an answer too — the offer
 * never comes back on this device for this account.
 */
@Composable
fun PasskeyOfferScreen(state: PasskeyOfferState) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(60.dp))
        Icon(WorkisIcons.PersonKey, contentDescription = null, tint = Kiremit500, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.passkey_offer_title),
            fontSize = 24.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, color = colors.ink,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.passkey_offer_body), fontSize = 15.sp, lineHeight = 22.sp, color = colors.muted)
        Spacer(Modifier.height(16.dp))
        // the user's own sentence, whole: it says what the tap changes
        Text(
            stringResource(R.string.passkey_offer_consequence),
            fontSize = 14.sp, lineHeight = 20.sp, color = Beige,
            modifier = Modifier.fillMaxWidth().background(BeigeBg, RoundedCornerShape(14.dp)).padding(14.dp),
        )
        note?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, fontSize = 13.sp, lineHeight = 18.sp, color = colors.danger)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                if (busy) return@Button
                busy = true; note = null
                scope.launch {
                    try {
                        Graph.passkeys.register(context, deviceLabel())
                        state.answer()
                    } catch (e: PasskeyService.Cancelled) {
                        // the sheet was closed: stay on the offer, say nothing
                    } catch (e: PasskeyService.Refused) {
                        note = e.message
                    }
                    busy = false
                }
            },
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
            else Text(stringResource(R.string.passkey_create), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { state.answer() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.passkey_offer_later), color = colors.muted, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(40.dp))
    }
}

/** Whether the offer is owed, and the one place that records the answer. */
class PasskeyOfferState {
    var show by mutableStateOf(false)
        internal set
    internal var key: String? = null

    /** Create or "Şimdi değil" — both close the question for good on this device. */
    fun answer() {
        key?.let { Graph.prefs.edit().putBoolean(it, true).apply() }
        show = false
    }
}

/**
 * Asked once per launch, and only while [enabled] — the §10.2 paper comes
 * first and the offer must never sit on top of it.
 */
@Composable
fun rememberPasskeyOffer(enabled: Boolean): PasskeyOfferState {
    val state = remember { PasskeyOfferState() }
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) {
        if (!enabled || asked || !Graph.passkeys.available) return@LaunchedEffect
        asked = true
        val id = Graph.auth.user.value.userId() ?: return@LaunchedEffect
        val prefKey = "passkey_offer_$id"
        if (Graph.prefs.getBoolean(prefKey, false)) return@LaunchedEffect
        // only a code-opened session that MAY add a key and has none is asked
        if (Graph.auth.signedInWith() != "code") return@LaunchedEffect
        val list = runCatching { Graph.passkeys.list() }.getOrNull() ?: return@LaunchedEffect
        if (list.canAddPasskey == true && list.passkeys.orEmpty().isEmpty()) {
            state.key = prefKey
            state.show = true
        }
    }
    return state
}

// ---------- small parts, in the Hesap grammar

@Composable
private fun SecurityCard(content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) { content() }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text.uppercase(),
        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.5.sp,
        color = WorkisTheme.colors.faint, modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun Footnote(text: String) {
    Text(
        text,
        fontSize = 12.sp, lineHeight = 17.sp, color = WorkisTheme.colors.faint,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(WorkisTheme.colors.border))
}
