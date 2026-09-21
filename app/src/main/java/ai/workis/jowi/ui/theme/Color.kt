package ai.workis.jowi.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Canonical Workis tokens — source of truth: iOS WorkisTheme.swift (decision 2026-08-28).

// Fixed (non-flipping) tokens
val Kiremit300 = Color(0xFFF29C73)
val Kiremit400 = Color(0xFFE8703F) // primary button fill, both schemes
val Kiremit500 = Color(0xFFD95F2E) // the slash, icons, focus ring
val Kiremit800 = Color(0xFF6E3319) // motifs, pressed state
val OnKiremitFill = Color(0xFF16171B)

/**
 * The note and status idioms are scheme PAIRS, not dark-only constants
 * (decisions 2026-09-21, WEB_PALETTE_SYNC rules 8 and 10). They read as the
 * same weight in both themes — the light band step matches the dark one — and
 * because the band flips with the theme, text inside it on a scheme token
 * (`ink`, `muted`) comes right by itself: no `on-beige-bg` token is needed.
 *
 * They keep their bare names so no call site had to change; each is a
 * composable getter now, so it resolves against the scheme like the neutrals.
 */
val Beige: Color
    @Composable @ReadOnlyComposable get() = LocalWorkisColors.current.beige

val BeigeBg: Color
    @Composable @ReadOnlyComposable get() = LocalWorkisColors.current.beigeBg

val SuccessGreen: Color
    @Composable @ReadOnlyComposable get() = LocalWorkisColors.current.success

val SuccessBg: Color
    @Composable @ReadOnlyComposable get() = LocalWorkisColors.current.successBg

/**
 * The document idiom — a paper is paper in both schemes. An agreement, an order
 * contract, a scanned tax plate and a PDF page are read as the sheet they are,
 * so they do not flip with the theme; everything AROUND them still does. These
 * four files each kept their own copy until now.
 */
val PaperWhite = Color(0xFFFFFFFF)
val PaperInk = Color(0xFF1A1A1F)

/** The hand that signs a paper, and the cell it is signed in. */
val PenBlue = Color(0xFF1A47B3)
val CellYellow = Color(0xFFFFF9D4)
val CellEdge = Color(0xFFE0C240)

@Immutable
data class WorkisColors(
    val canvas: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val faint: Color,
    val border: Color,
    val accentText: Color,
    val danger: Color,
    val blue: Color,
    /** The note idiom: informational ink and the band it sits on. */
    val beige: Color,
    val beigeBg: Color,
    /** Status ink and its band — a STATUS colour, never an action colour. */
    val success: Color,
    val successBg: Color,
)

val WorkisLightColors = WorkisColors(
    canvas = Color(0xFFF2EEE8),
    surface = Color(0xFFFAF7F2),
    ink = Color(0xFF262220),
    muted = Color(0xFF5A524A),
    faint = Color(0xFF857B6E),
    border = Color(0xFFDDD5C9),
    accentText = Color(0xFF853A22),
    danger = Color(0xFF9E1F16),
    blue = Color(0xFF2F6FD6),
    beige = Color(0xFF4F4713),
    beigeBg = Color(0xFFE2DBAC),
    success = Color(0xFF1E6417),
    successBg = Color(0xFFC4E2C1),
)

val WorkisDarkColors = WorkisColors(
    canvas = Color(0xFF16171B),
    surface = Color(0xFF26272D),
    ink = Color(0xFFECECEE),
    muted = Color(0xFFA6A7AE),
    faint = Color(0xFF72737A),
    border = Color(0xFF3B3C43),
    accentText = Color(0xFFF29C73),
    danger = Color(0xFFE8867B),
    blue = Color(0xFF4F8EF7),
    beige = Color(0xFFD9D2A0),
    beigeBg = Color(0xFF45412D),
    success = Color(0xFF6FBF73),
    successBg = Color(0xFF33432F),
)

val LocalWorkisColors = staticCompositionLocalOf { WorkisLightColors }
