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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import ai.workis.jowi.ui.theme.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.AuthRepository
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.AskOption
import ai.workis.jowi.data.PasskeyService
import ai.workis.jowi.ui.components.JowiOptions
import ai.workis.jowi.data.VisStyle
import ai.workis.jowi.data.vis
import ai.workis.jowi.data.ApplyAskBody
import ai.workis.jowi.ui.components.AnswerText
import ai.workis.jowi.data.safeCall
import ai.workis.jowi.ui.components.WorkisMark
import ai.workis.jowi.ui.components.openInApp
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.WorkisIcons
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisSans
import ai.workis.jowi.ui.theme.WorkisTheme
import kotlinx.coroutines.launch

private enum class LoginStep { Email, Code }

@Composable
fun LoginScreen(onApply: (String) -> Unit = {}) {
    val colors = WorkisTheme.colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang by Graph.language.collectAsState()

    var showJowi by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(LoginStep.Email) }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmSignup by remember { mutableStateOf<AuthRepository.SignIn.ConfirmSignup?>(null) }
    val codeFocus = remember { FocusRequester() }

    /**
     * A stored session the server refused does not land on a bare screen: a
     * refusal with a reason (`account_frozen`, `passkey_required`) speaks in the
     * server's own sentence, and an ordinary dead key gets one neutral line.
     * Devices that signed in before the per-device keys shared one key, so a
     * sign-out elsewhere ends this one — a person can meet it once.
     */
    val refused by Graph.auth.sessionWasRefused.collectAsState()
    val refusalSentence by Graph.auth.refusalSentence.collectAsState()
    val sessionEnded = stringResource(R.string.session_ended)
    LaunchedEffect(refused) {
        if (refused) { error = refusalSentence ?: sessionEnded; Graph.auth.clearRefusal() }
    }

    /**
     * One field, the server decides (ledger 56/58): a known address gets its code and
     * the code step; an unknown one gets the wizard with the address filled in — no
     * code, no account, no sentence of ours. The login is ONE door, as the homepage.
     */
    fun requestCode() {
        if (loading || email.isBlank()) return
        loading = true
        error = null
        scope.launch {
            when (val r = Graph.auth.door(email)) {
                is ApiResult.Ok -> when (val d = r.value) {
                    is AuthRepository.Door.Known -> step = LoginStep.Code
                    is AuthRepository.Door.Unknown -> onApply(d.email)
                }
                is ApiResult.Err -> error = r.message
            }
            loading = false
        }
    }

    fun verify() {
        if (loading || code.length != 6) return
        loading = true
        error = null
        scope.launch {
            when (val r = Graph.auth.verifyOtp(email.trim(), code.trim())) {
                // a correct code, but nobody has an account under this address:
                // ask before opening one — a mistyped address used to become an
                // empty Guest seat that nobody ever found again
                is ApiResult.Ok -> when (val outcome = r.value) {
                    is AuthRepository.SignIn.Opened -> Unit // root observes isAuthenticated
                    is AuthRepository.SignIn.ConfirmSignup -> confirmSignup = outcome
                }
                is ApiResult.Err -> error = r.message
            }
            loading = false
        }
    }

    /** "Hesap aç ve devam et" — the only door that creates the account. */
    fun openAccount(ticket: String) {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            when (val r = Graph.auth.confirmSignup(ticket)) {
                is ApiResult.Ok -> Unit // the session opened; root observes it
                // a spent or stale ticket: the server's sentence, back to the address
                is ApiResult.Err -> { error = r.message; step = LoginStep.Email; code = "" }
            }
            loading = false
        }
    }

    /**
     * The passkey door. No e-mail is asked — the credential is discoverable, so
     * the system's own sheet names the account. A closed sheet is an answer, not
     * a failure: nothing is shown. The answer lands exactly like an OTP verify.
     */
    fun signInWithPasskey() {
        if (loading) return
        loading = true
        error = null
        scope.launch {
            try {
                Graph.auth.adoptPasskeySession(Graph.passkeys.signIn(context))
            } catch (e: PasskeyService.Cancelled) {
                Unit
            } catch (e: PasskeyService.Refused) {
                error = e.message
            }
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .imePadding(),
    ) {
        Spacer(Modifier.height(56.dp))

        Column(Modifier.padding(horizontal = 28.dp)) {
            // Brand in the header, web-card style; the scheme picker sits up
            // here as a secondary circle (the language lives in the tab bar).
            Row(verticalAlignment = Alignment.CenterVertically) {
                WorkisMark(size = 24)
                Spacer(Modifier.weight(1f))
                AppearanceButton()
            }
            Spacer(Modifier.height(36.dp))

            if (!showJowi) {
                // title block (pinned under the header on both steps)
                Text(
                    stringResource(if (step == LoginStep.Email) R.string.sign_in_title else R.string.check_title),
                    fontFamily = WorkisMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 26.sp,
                    color = colors.ink,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (step == LoginStep.Email) R.string.sign_in_copy else R.string.check_copy),
                    fontFamily = WorkisSans,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    color = colors.muted,
                )
                if (step == LoginStep.Code) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        email.trim(),
                        fontFamily = WorkisMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = colors.ink,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Column(Modifier.padding(horizontal = 28.dp)) {
            when {
                showJowi -> PublicAskContent()

                step == LoginStep.Email -> {
                    GlassField(
                        value = email,
                        onChange = { email = it; error = null },
                        placeholder = stringResource(R.string.email_placeholder),
                        keyboardType = KeyboardType.Email,
                        fieldModifier = Modifier.semantics { contentType = ContentType.Username },
                        trailing = {
                            if (email.isNotBlank()) {
                                CircularSubmit(loading = loading, enabled = true) { requestCode() }
                            }
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    HintLine(stringResource(R.string.email_hint))
                    // The system offers the passkey inline above the keyboard, but
                    // that suggestion is easy to miss — the door is drawn too.
                    if (Graph.passkeys.available) {
                        Spacer(Modifier.height(16.dp))
                        PasskeyDoor(enabled = !loading) { signInWithPasskey() }
                    }
                }

                else -> {
                    GlassField(
                        value = code,
                        onChange = {
                            code = it.filter(Char::isDigit).take(6)
                            error = null
                            if (code.length == 6) verify()
                        },
                        placeholder = stringResource(R.string.code_placeholder),
                        keyboardType = KeyboardType.Number,
                        textStyle = TextStyle(
                            fontFamily = WorkisMono,
                            fontWeight = FontWeight.Medium,
                            fontSize = 24.sp,
                            letterSpacing = 6.sp,
                            color = colors.ink,
                        ),
                        focusRequester = codeFocus,
                        trailing = {
                            if (code.isNotEmpty()) {
                                CircularSubmit(loading = loading, enabled = code.length == 6) { verify() }
                            }
                        },
                    )
                    LaunchedEffect(Unit) { codeFocus.requestFocus() }
                    Spacer(Modifier.height(14.dp))
                    // ONE way back (web, owner 2026-10-12; app ledger 59): the e-mail step
                    // with the address kept — fix it, or send it again and the door issues
                    // a fresh code. "Resend" and "different e-mail" said the same thing twice.
                    PillButton(stringResource(R.string.fix_or_resend), enabled = !loading) {
                        step = LoginStep.Email
                        code = ""
                        error = null
                    }
                    Spacer(Modifier.height(14.dp))
                    HintLine(stringResource(R.string.code_validity))
                    // The passkey door on both steps: an account closed to the code gets a
                    // mail instead of a code, and the screen says the same neutral thing
                    // to everyone (no oracle).
                    if (Graph.passkeys.available) {
                        Spacer(Modifier.height(16.dp))
                        PasskeyDoor(enabled = !loading) { signInWithPasskey() }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    it,
                    color = colors.danger,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.weight(1.2f))

        // footer: workis.ai · Privacy Policy
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("workis.ai", fontFamily = WorkisMono, fontSize = 13.sp, color = colors.faint)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.privacy),
                fontFamily = WorkisSans,
                fontSize = 13.sp,
                color = colors.faint,
                // the web footer's page, by language (/gizlilik/ alone is a 404)
                modifier = Modifier.clickable { openInApp(context, if (lang == "en") "https://workis.ai/legal/privacy/" else "https://workis.ai/yasal/gizlilik/") },
            )
        }
        Spacer(Modifier.height(14.dp))

        // login's own tab bar: Türkçe | English | Jowi (tab switch = language switch)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            LangTab(
                label = stringResource(R.string.lang_tr),
                icon = WorkisIcons.Globe,
                selected = !showJowi && lang == "tr",
            ) {
                showJowi = false
                Graph.setLanguage("tr") // the per-app locale follows it
            }
            LangTab(
                label = stringResource(R.string.lang_en),
                icon = WorkisIcons.Globe,
                selected = !showJowi && lang == "en",
            ) {
                showJowi = false
                Graph.setLanguage("en") // the per-app locale follows it
            }
            LangTab(
                label = stringResource(R.string.jowi_tab),
                icon = WorkisIcons.Sparkle,
                selected = showJowi,
            ) { showJowi = true }
        }
    }

    // The account is opened only here, and only on a yes. "Farklı bir adresle
    // giriş yap" is the same as closing the window: no call, back to the
    // address, the code cleared — nothing was created.
    confirmSignup?.let { ask ->
        AlertDialog(
            onDismissRequest = { confirmSignup = null; step = LoginStep.Email; code = "" },
            containerColor = colors.surface,
            title = { Text(stringResource(R.string.signup_confirm_title), color = colors.ink) },
            text = {
                Text(
                    ask.email + "\n\n" + stringResource(R.string.signup_confirm_body),
                    fontSize = 14.sp, lineHeight = 20.sp, color = colors.muted,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmSignup = null; openAccount(ask.ticket) }) {
                    Text(stringResource(R.string.signup_confirm_yes), color = colors.accentText, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignup = null; step = LoginStep.Email; code = "" }) {
                    Text(stringResource(R.string.signup_confirm_other), color = colors.muted)
                }
            },
        )
    }
}

/** Appearance (System / Light / Dark) before sign-in — iOS's Menu+Picker circle. */
@Composable
private fun AppearanceButton() {
    val colors = WorkisTheme.colors
    val appearance by Graph.appearance.collectAsState()
    var open by remember { mutableStateOf(false) }
    val options = listOf(
        Triple("system", R.string.appearance_system, WorkisIcons.HalfCircle),
        Triple("light", R.string.appearance_light, WorkisIcons.Sun),
        Triple("dark", R.string.appearance_dark, WorkisIcons.Moon),
    )
    Box {
        IconButton(
            onClick = { open = true },
            modifier = Modifier
                .size(36.dp)
                .background(colors.surface, CircleShape)
                .border(1.dp, colors.border, CircleShape),
        ) {
            Icon(
                options.firstOrNull { it.first == appearance }?.third ?: WorkisIcons.HalfCircle,
                contentDescription = stringResource(R.string.appearance),
                tint = colors.ink,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (value, labelRes, icon) ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(labelRes),
                            fontFamily = WorkisSans,
                            fontSize = 14.sp,
                            color = if (appearance == value) colors.accentText else colors.ink,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            icon, contentDescription = null, modifier = Modifier.size(18.dp),
                            tint = if (appearance == value) Kiremit500 else colors.muted,
                        )
                    },
                    onClick = { Graph.setAppearance(value); open = false },
                )
            }
        }
    }
}

/** iOS glassField: capsule surface, leading kiremit slash, in-field trailing action. */
@Composable
private fun GlassField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    textStyle: TextStyle? = null,
    focusRequester: FocusRequester? = null,
    /** The e-mail step marks itself as the username field so the system can
     *  offer the account's passkey above the keyboard. */
    fieldModifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val colors = WorkisTheme.colors
    val shape = RoundedCornerShape(30.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            "/",
            fontFamily = WorkisMono,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = Kiremit500,
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    placeholder,
                    fontFamily = WorkisSans,
                    fontSize = 16.sp,
                    color = colors.faint,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = textStyle ?: TextStyle(
                    fontFamily = WorkisSans,
                    fontSize = 16.sp,
                    color = colors.ink,
                ),
                cursorBrush = SolidColor(colors.blue),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(fieldModifier)
                    .let { m -> focusRequester?.let { m.focusRequester(it) } ?: m },
            )
        }
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { trailing() }
    }
}

@Composable
private fun CircularSubmit(loading: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = Modifier
            .size(44.dp)
            .background(if (enabled) Kiremit400 else WorkisTheme.colors.border, CircleShape),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnKiremitFill)
        } else {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (enabled) OnKiremitFill else WorkisTheme.colors.muted,
            )
        }
    }
}

@Composable
private fun HintLine(text: String) {
    val colors = WorkisTheme.colors
    Row {
        Text(
            "/",
            fontFamily = WorkisMono,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Kiremit500,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            fontFamily = WorkisSans,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = colors.muted,
        )
    }
}

/**
 * "Geçiş anahtarıyla gir" — an outlined capsule, not a filled one: the screen's
 * one tinted action stays the submit circle. Person + key is the system's own
 * sign for a passkey.
 */
@Composable
private fun PasskeyDoor(enabled: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(colors.surface, CircleShape)
            .border(1.dp, colors.border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Icon(
            WorkisIcons.PersonKey,
            contentDescription = null,
            tint = if (enabled) colors.accentText else colors.faint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.passkey_sign_in),
            fontFamily = WorkisSans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = if (enabled) colors.accentText else colors.faint,
        )
    }
}

@Composable
private fun PillButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Text(
        label,
        fontFamily = WorkisSans,
        fontSize = 14.sp,
        color = if (enabled) colors.ink else colors.faint,
        modifier = Modifier
            .background(colors.surface, CircleShape)
            .border(1.dp, colors.border, CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}


@Composable
private fun LangTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = WorkisTheme.colors
    val tint = if (selected) Kiremit500 else colors.muted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontFamily = WorkisSans,
            fontSize = 12.sp,
            color = if (selected) colors.ink else colors.muted,
        )
    }
}

/** Anonymous applicant Jowi (login's third tab) → POST /workis/apply/ask/. */
@Composable
private fun PublicAskContent() {
    val colors = WorkisTheme.colors
    // what the sign-in screen actually draws — the public door reads this too,
    // so Jowi stops offering controls this screen has not got (2026-09-17)
    val screenControls = listOf(
        vis(stringResource(R.string.sign_in_title)), vis(stringResource(R.string.email_placeholder)),
        vis(stringResource(R.string.email_hint)),
        vis(stringResource(R.string.privacy), VisStyle.SECONDARY), vis(stringResource(R.string.lang_tr), VisStyle.SECONDARY),
        vis(stringResource(R.string.lang_en), VisStyle.SECONDARY), vis(stringResource(R.string.jowi_tab), VisStyle.SECONDARY),
        vis(stringResource(R.string.apply_ask_prompt)), vis(stringResource(R.string.appearance), VisStyle.SECONDARY),
    )
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var options by remember { mutableStateOf<List<AskOption>?>(null) }
    var optionPicked by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    var askError by remember { mutableStateOf<String?>(null) }
    val askFocus = remember { FocusRequester() }

    /** Where the visitor is (2026-09-17): the login screen's own Jowi tab. */
    fun ask(text: String) {
        val q = text.trim()
        if (q.isEmpty() || asking) return
        asking = true
        askError = null
        scope.launch {
            when (val r = safeCall(Graph.language.value) {
                Graph.api.applyAsk(
                    ApplyAskBody(q = q, lang = Graph.language.value, screen = "public.login", visible = screenControls),
                )
            }) {
                is ApiResult.Ok -> {
                    answer = r.value.text
                    options = r.value.options?.takeIf { it.isNotEmpty() }
                    optionPicked = false
                }
                is ApiResult.Err -> askError = r.message
            }
            asking = false
        }
    }

    answer?.let {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(20.dp))
                .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                .padding(16.dp),
        ) {
            // the public answer carries the md_lite grammar too (2026-09-27)
            AnswerText(it, color = colors.ink, fontSize = 15.sp, lineHeight = 21.sp)
            options?.let { opts ->
                Spacer(Modifier.height(10.dp))
                JowiOptions(opts, optionPicked, enabled = !asking) { o ->
                    optionPicked = true
                    val q = o.q
                    if (!q.isNullOrEmpty()) { question = ""; ask(q) } else askFocus.requestFocus()
                }
            }
        }
        Spacer(Modifier.height(14.dp))
    }
    if (asking) {
        Text("Jowi …", fontFamily = WorkisSans, fontSize = 14.sp, color = colors.faint)
        Spacer(Modifier.height(10.dp))
    }
    askError?.let {
        Text(it, color = colors.danger, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
    }
    GlassField(
        value = question,
        onChange = { question = it },
        placeholder = stringResource(R.string.apply_ask_prompt),
        keyboardType = KeyboardType.Text,
        trailing = {
            if (question.isNotBlank()) {
                CircularSubmit(loading = asking, enabled = true) { val q = question; question = ""; ask(q) }
            }
        },
        focusRequester = askFocus,
    )
}
