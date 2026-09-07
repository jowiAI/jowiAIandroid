package ai.workis.jowi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.ui.theme.SuccessGreen
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisSans
import ai.workis.jowi.ui.theme.WorkisTheme

/** Grouped settings-style card with a mono eyebrow (iOS formSection / fieldsCard). */
@Composable
fun FormSection(header: String?, content: @Composable ColumnScope.() -> Unit) {
    val colors = WorkisTheme.colors
    Column {
        header?.let {
            Text(
                it,
                fontFamily = WorkisMono,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                color = colors.faint,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, RoundedCornerShape(14.dp))
                .border(1.dp, colors.border, RoundedCornerShape(14.dp)),
            content = content,
        )
    }
}

/**
 * One row of a grouped form. `stacked` = label over a growing field (long
 * answers); otherwise label-left / value-right. Cell-exit autosave: `onCommit`
 * fires when focus LEAVES the field (never per keystroke); `tick` shows the
 * brief green confirmation the caller drives. `locked`/`frozen` render read-only.
 */
@Composable
fun FormRow(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    onCommit: () -> Unit,
    tick: Boolean,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    mono: Boolean = false,
    stacked: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = 4,
    locked: Boolean = false,
    frozen: Boolean = false,
) {
    val colors = WorkisTheme.colors
    val focus = remember { FocusRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    val readOnly = locked || frozen
    val valueStyle = TextStyle(
        fontFamily = if (mono) WorkisMono else WorkisSans,
        fontWeight = if (mono) FontWeight.Medium else FontWeight.Normal,
        fontSize = 14.sp,
        color = colors.ink,
        textAlign = if (stacked) TextAlign.Start else TextAlign.End,
    )
    val field: @Composable (Modifier) -> Unit = { m ->
        Box(m) {
            if (value.isEmpty()) {
                Text(
                    if (readOnly) "—" else placeholder,
                    style = valueStyle.copy(color = colors.faint),
                    maxLines = maxLines,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                readOnly = readOnly,
                singleLine = !stacked,
                minLines = if (stacked) minLines else 1,
                maxLines = if (stacked) maxLines else 1,
                textStyle = valueStyle,
                cursorBrush = SolidColor(colors.blue),
                keyboardOptions = KeyboardOptions(
                    keyboardType = keyboardType,
                    capitalization = when {
                        keyboardType == KeyboardType.Email -> KeyboardCapitalization.None
                        stacked -> KeyboardCapitalization.Sentences
                        else -> KeyboardCapitalization.Words
                    },
                    autoCorrectEnabled = keyboardType != KeyboardType.Email,
                    imeAction = if (stacked) ImeAction.Default else ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .onFocusChanged {
                        if (it.isFocused) hadFocus = true
                        else if (hadFocus) { hadFocus = false; onCommit() }
                    },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = !readOnly,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { focus.requestFocus() },
    ) {
        if (stacked) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, fontFamily = WorkisSans, fontSize = 13.sp, color = colors.muted, modifier = Modifier.weight(1f))
                    SavedTick(tick)
                }
                Spacer(Modifier.height(4.dp))
                field(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp).heightIn(min = 42.dp),
            ) {
                Text(label, fontFamily = WorkisSans, fontSize = 14.sp, color = colors.muted)
                if (locked) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.faint, modifier = Modifier.size(11.dp))
                }
                Spacer(Modifier.width(8.dp))
                field(Modifier.weight(1f))
                SavedTick(tick, leadingGap = true)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 14.dp)
                .height(1.dp)
                .background(colors.border),
        )
    }
}

@Composable
private fun SavedTick(visible: Boolean, leadingGap: Boolean = false) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
    ) {
        Row {
            if (leadingGap) Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = SuccessGreen,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
