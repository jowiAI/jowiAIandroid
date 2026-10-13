package ai.workis.jowi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import ai.workis.jowi.R

// IBM Plex, bundled — brand rule: no system-font fallback.
// Sans ships as a variable font; weights are pinned via FontVariation.

@OptIn(ExperimentalTextApi::class)
val WorkisSans: FontFamily = FontFamily(
    Font(
        R.font.ibm_plex_sans_var,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.ibm_plex_sans_var,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.ibm_plex_sans_var,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
)

val WorkisMono: FontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_medium, weight = FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, weight = FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_bold, weight = FontWeight.Bold),
)

/**
 * The reading step (2026-10-13, ledger 57 — testers found the public pages small; the
 * web went +2, the web app follows, the phone the same day; `jowiAIs/docs/BRAND.md`
 * "Reading step"). Applied HERE, not at the ~1,000 call sites: the size written at a
 * call site is the DESIGN size. Text sizes up to 15 (the body range) render 2sp larger
 * and never under 11 (HIG's caption2 floor — the 8–10 sites all land on 11); headings
 * from 16 up render as written, they were never the complaint. A line height written
 * next to a stepped size grows by the same amount, so the lines keep their rhythm
 * (iOS has no fixed line heights, so it got this for free). The person's system
 * font-scale still multiplies on top — sp already carries it — which is why the app
 * has no text-size setting of its own.
 *
 * Every `Text` in the app is [ai.workis.jowi.ui.theme.Text] (never material3's), which
 * is where the step is applied; a `TextStyle` built by hand for a text field goes
 * through [readingStep], a `SpanStyle` size through [readingSize].
 */
const val READING_STEP = 2
const val READING_CEILING = 15
const val READING_FLOOR = 11

/** The design size → the rendered size. Only sp is stepped; em and Unspecified pass through. */
fun readingSize(size: TextUnit): TextUnit {
    if (!size.isSpecified || !size.isSp) return size
    val written = size.value
    return if (written <= READING_CEILING) maxOf(written + READING_STEP, READING_FLOOR.toFloat()).sp else size
}

/** The step a written size gains (0 for headings), so a paired line height can follow it. */
fun readingDelta(size: TextUnit): Float =
    if (size.isSpecified && size.isSp) readingSize(size).value - size.value else 0f

/** A style whose fontSize/lineHeight are design sizes, stepped once. Never call twice on the same style. */
fun TextStyle.readingStep(): TextStyle {
    val delta = readingDelta(fontSize)
    if (delta == 0f) return this
    return copy(
        fontSize = readingSize(fontSize),
        lineHeight = if (lineHeight.isSpecified && lineHeight.isSp) (lineHeight.value + delta).sp else lineHeight,
    )
}

private fun Typography.readingStep(): Typography = Typography(
    displayLarge = displayLarge.readingStep(), displayMedium = displayMedium.readingStep(), displaySmall = displaySmall.readingStep(),
    headlineLarge = headlineLarge.readingStep(), headlineMedium = headlineMedium.readingStep(), headlineSmall = headlineSmall.readingStep(),
    titleLarge = titleLarge.readingStep(), titleMedium = titleMedium.readingStep(), titleSmall = titleSmall.readingStep(),
    bodyLarge = bodyLarge.readingStep(), bodyMedium = bodyMedium.readingStep(), bodySmall = bodySmall.readingStep(),
    labelLarge = labelLarge.readingStep(), labelMedium = labelMedium.readingStep(), labelSmall = labelSmall.readingStep(),
)

// Design sizes, as on iOS; the reading step is applied once, below, to every style —
// so a text field or button label that falls back to the theme's typography steps too.
val WorkisTypography = Typography(
    // big in-page title (mono 28 semibold on iOS)
    headlineMedium = TextStyle(
        fontFamily = WorkisMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = WorkisSans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = WorkisSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = WorkisMono,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
    ),
).readingStep()
