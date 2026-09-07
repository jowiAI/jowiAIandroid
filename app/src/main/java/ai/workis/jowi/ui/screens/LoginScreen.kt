package ai.workis.jowi.ui.screens

import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.ApiResult
import ai.workis.jowi.data.ApplyAskBody
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
fun LoginScreen(onApply: () -> Unit = {}) {
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
    val codeFocus = remember { FocusRequester() }

    fun requestCode() {
        if (loading || email.isBlank()) return
        loading = true
        error = null
        scope.launch {
            when (val r = Graph.auth.requestOtp(email.trim())) {
                is ApiResult.Ok -> step = LoginStep.Code
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
                is ApiResult.Ok -> Unit // root observes isAuthenticated
                is ApiResult.Err -> error = r.message
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
                        trailing = {
                            if (email.isNotBlank()) {
                                CircularSubmit(loading = loading, enabled = true) { requestCode() }
                            }
                        },
                    )
                    Spacer(Modifier.height(14.dp))
                    HintLine(stringResource(R.string.email_hint))
                    Spacer(Modifier.height(22.dp))
                    ApplyCard(onApply)
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton(stringResource(R.string.resend), enabled = !loading) {
                            scope.launch {
                                loading = true
                                Graph.auth.requestOtp(email.trim())
                                loading = false
                            }
                        }
                        PillButton(stringResource(R.string.different_email), enabled = !loading) {
                            step = LoginStep.Email
                            code = ""
                            error = null
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HintLine(stringResource(R.string.link_hint))
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
                modifier = Modifier.clickable { openInApp(context, "https://workis.ai/gizlilik/") },
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
                Graph.setLanguage("tr")
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("tr"))
            }
            LangTab(
                label = stringResource(R.string.lang_en),
                icon = WorkisIcons.Globe,
                selected = !showJowi && lang == "en",
            ) {
                showJowi = false
                Graph.setLanguage("en")
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
            }
            LangTab(
                label = stringResource(R.string.jowi_tab),
                icon = WorkisIcons.Sparkle,
                selected = showJowi,
            ) { showJowi = true }
        }
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
private fun ApplyCard(onApply: () -> Unit) {
    val colors = WorkisTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.new_title),
                fontFamily = WorkisSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = colors.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.new_sub),
                fontFamily = WorkisSans,
                fontSize = 13.sp,
                color = colors.muted,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(R.string.apply),
            fontFamily = WorkisSans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = colors.accentText,
            modifier = Modifier
                .border(1.dp, colors.border, CircleShape)
                .clickable(onClick = onApply)
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
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
    val scope = rememberCoroutineScope()
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf<String?>(null) }
    var asking by remember { mutableStateOf(false) }
    var askError by remember { mutableStateOf<String?>(null) }

    fun ask() {
        val q = question.trim()
        if (q.isEmpty() || asking) return
        asking = true
        askError = null
        scope.launch {
            when (val r = safeCall(Graph.language.value) {
                Graph.api.applyAsk(ApplyAskBody(q = q, lang = Graph.language.value))
            }) {
                is ApiResult.Ok -> answer = r.value.text
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
            Text(it, fontFamily = WorkisSans, fontSize = 15.sp, lineHeight = 21.sp, color = colors.ink)
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
                CircularSubmit(loading = asking, enabled = true) { ask() }
            }
        },
    )
}
