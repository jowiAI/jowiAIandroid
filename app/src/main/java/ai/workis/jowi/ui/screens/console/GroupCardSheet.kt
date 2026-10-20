package ai.workis.jowi.ui.screens.console

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.workis.jowi.Graph
import ai.workis.jowi.R
import ai.workis.jowi.data.GroupCardChoice
import ai.workis.jowi.data.GroupListRow
import ai.workis.jowi.data.GroupPick
import ai.workis.jowi.data.GroupTarget
import ai.workis.jowi.data.GroupTargetParent
import ai.workis.jowi.ui.theme.Kiremit400
import ai.workis.jowi.ui.theme.Kiremit500
import ai.workis.jowi.ui.theme.OnKiremitFill
import ai.workis.jowi.ui.theme.Text
import ai.workis.jowi.ui.theme.WorkisMono
import ai.workis.jowi.ui.theme.WorkisTheme
import java.text.Normalizer
import java.util.Locale

/**
 * The one group card (ledger 65; web `group_picker.js`): the same sheet opens from a heading on
 * Gruplar's headings tab and from a waiting family on Ürünler. Three sections in the web's order —
 * Jowi suggests ([picks]: null = not read, drawn only as the reading line while [reading]; empty =
 * read, nothing fits), New group (only when [canMakeNew] and [targets] is non-empty; a sector/parent
 * picker opening on [homeSectorSlug] at top level, a name prefilled with [suggestedName], the sheet's
 * one kiremit action), and Choose an existing group (a search over [groups] that starts at two
 * letters, 40 rows at most). [forProduct] only switches the "no fit" / "no match" wording. Nothing
 * here posts: the card calls [onChoose] and the CALLER posts and closes the sheet; [busy] disables
 * every choice while a post is out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupCardSheet(
    title: String,
    picks: List<GroupPick>?,
    reading: Boolean,
    forProduct: Boolean,
    canMakeNew: Boolean,
    targets: List<GroupTarget>,
    suggestedName: String,
    homeSectorSlug: String?,
    groups: List<GroupListRow>,
    busy: Boolean,
    onChoose: (GroupCardChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = WorkisTheme.colors
    val lang by Graph.language.collectAsState()
    val locale = remember(lang) { Locale.forLanguageTag(lang) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.canvas,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Spacer(Modifier.height(16.dp))

            // 1. Jowi suggests
            if (picks != null || reading) {
                CardEyebrow(stringResource(R.string.groups_jowi_suggests), locale)
                CardBox {
                    when {
                        picks == null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.muted)
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.groups_reading), fontSize = 13.sp, color = colors.muted)
                        }
                        picks.isEmpty() -> Text(
                            stringResource(if (forProduct) R.string.groups_no_fit_product else R.string.groups_no_fit_heading),
                            fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted, modifier = Modifier.padding(vertical = 8.dp),
                        )
                        else -> {
                            val rows = picks.filter { !it.groupId.isNullOrEmpty() }
                            rows.forEachIndexed { i, p ->
                                CardRow(
                                    title = p.name.orEmpty(),
                                    subtitle = listOfNotNull(p.sector, p.why).filter { it.isNotBlank() }.joinToString(" · "),
                                    strong = true,
                                    enabled = !busy,
                                ) { onChoose(GroupCardChoice.Existing(p.groupId.orEmpty(), p.name.orEmpty())) }
                                if (i < rows.lastIndex) Hairline()
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
            }

            // 2. New group
            val drawNew = canMakeNew && targets.isNotEmpty()
            if (drawNew) {
                NewGroupSection(targets, suggestedName, homeSectorSlug, busy, locale, onChoose)
                Spacer(Modifier.height(18.dp))
            }

            // 3. Choose an existing group
            if (groups.isNotEmpty()) {
                var query by remember { mutableStateOf("") }
                CardEyebrow(stringResource(R.string.groups_choose_existing), locale)
                CardField(
                    query, { query = it },
                    stringResource(if (drawNew) R.string.groups_search_two_letters else R.string.groups_search_group),
                )
                val q = query.trim()
                if (q.length >= 2) {
                    val f = cardFold(q, locale)
                    fun hit(s: String?) = s != null && cardFold(s, locale).contains(f)
                    val matches = groups.filter { it.id != null && (hit(it.name) || hit(it.sector) || hit(it.parent)) }.take(40)
                    Spacer(Modifier.height(8.dp))
                    if (matches.isEmpty()) {
                        Text(
                            stringResource(if (forProduct) R.string.groups_no_match_product else R.string.groups_no_match_heading),
                            fontSize = 13.sp, lineHeight = 18.sp, color = colors.muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                        )
                    } else CardBox {
                        matches.forEachIndexed { i, g ->
                            CardRow(
                                title = g.name.orEmpty(),
                                subtitle = listOfNotNull(g.sector, g.parent).filter { it.isNotBlank() }.joinToString(" › "),
                                strong = false,
                                enabled = !busy,
                            ) { onChoose(GroupCardChoice.Existing(g.id.orEmpty(), g.name.orEmpty())) }
                            if (i < matches.lastIndex) Hairline()
                        }
                    }
                } else if (!drawNew) {
                    Text(
                        stringResource(R.string.groups_search_two_letters), fontSize = 12.sp, color = colors.faint,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/** One picker option: a target sector, and a top group there or null (top level). */
private data class CardWhere(val sector: GroupTarget, val parent: GroupTargetParent?) {
    val tag: String get() = sector.id.orEmpty() + (parent?.id?.let { ":$it" } ?: "")
}

@Composable
private fun NewGroupSection(
    targets: List<GroupTarget>,
    suggestedName: String,
    homeSectorSlug: String?,
    busy: Boolean,
    locale: Locale,
    onChoose: (GroupCardChoice) -> Unit,
) {
    val colors = WorkisTheme.colors
    val topWord = stringResource(R.string.groups_top_level_word)
    val options = remember(targets) {
        targets.filter { !it.id.isNullOrEmpty() }.flatMap { s ->
            listOf(CardWhere(s, null)) + s.parents.orEmpty().filter { !it.id.isNullOrEmpty() }.map { CardWhere(s, it) }
        }
    }
    fun home(): String? = (targets.firstOrNull { it.slug == homeSectorSlug && !it.id.isNullOrEmpty() }
        ?: targets.firstOrNull { !it.id.isNullOrEmpty() })?.id
    var where by remember { mutableStateOf(home()) }
    // the targets may land after the sheet opened: settle on home when the choice is gone
    LaunchedEffect(options) { if (options.none { it.tag == where }) where = home() }
    val chosen = options.firstOrNull { it.tag == where }
    var name by remember { mutableStateOf(suggestedName) }
    var open by remember { mutableStateOf(false) }
    fun label(o: CardWhere) =
        if (o.parent == null) "${o.sector.name.orEmpty()} — $topWord" else "${o.sector.name.orEmpty()} › ${o.parent.name.orEmpty()}"

    CardEyebrow(stringResource(R.string.groups_new_group), locale)
    CardBox {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, colors.border, RoundedCornerShape(14.dp))
                    .clickable(enabled = !busy) { open = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    chosen?.let(::label).orEmpty(), fontSize = 14.sp, color = colors.ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.groups_parent), tint = colors.faint)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
                options.forEach { o ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                label(o), color = colors.ink,
                                fontWeight = if (o.tag == where) FontWeight.SemiBold else null,
                                modifier = Modifier.padding(start = if (o.parent != null) 12.dp else 0.dp),
                            )
                        },
                        onClick = { open = false; where = o.tag },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        CardField(name, { name = it }, stringResource(R.string.groups_new_placeholder))
        val trimmed = name.trim()
        val path = listOfNotNull(chosen?.sector?.name, chosen?.parent?.name, trimmed).filter { it.isNotBlank() }.joinToString(" › ")
        if (path.isNotEmpty()) {
            Text(path, fontSize = 12.sp, color = colors.faint, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        }
        Spacer(Modifier.height(12.dp))
        val enabled = trimmed.length >= 2 && chosen != null && !busy
        Button(
            onClick = {
                val c = chosen ?: return@Button
                onChoose(GroupCardChoice.New(trimmed, c.sector.id.orEmpty(), c.sector.slug.orEmpty(), c.parent?.id))
            },
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = Kiremit400, contentColor = OnKiremitFill),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = colors.muted)
            } else {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.groups_make_new), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

// MARK: - small pieces (the card's own; GroupsScreen's are file-private)

private fun cardFold(s: String, locale: Locale): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(locale)
        .replace('ı', 'i')

@Composable
private fun CardRow(title: String, subtitle: String, strong: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, fontSize = 14.sp, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Medium, color = colors.ink)
        if (subtitle.isNotBlank()) Text(subtitle, fontSize = 12.sp, lineHeight = 16.sp, color = colors.muted)
    }
}

@Composable
private fun CardBox(content: @Composable () -> Unit) {
    val colors = WorkisTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(20.dp))
            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) { content() }
}

@Composable
private fun Hairline() = Box(Modifier.fillMaxWidth().height(1.dp).background(WorkisTheme.colors.border))

@Composable
private fun CardEyebrow(text: String, locale: Locale) {
    Text(
        text.uppercase(locale),
        fontFamily = WorkisMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.sp,
        color = WorkisTheme.colors.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun CardField(value: String, onChange: (String) -> Unit, placeholder: String) {
    val colors = WorkisTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = colors.faint, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Kiremit500, unfocusedBorderColor = colors.border,
            focusedTextColor = colors.ink, unfocusedTextColor = colors.ink,
            focusedContainerColor = colors.surface, unfocusedContainerColor = colors.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
