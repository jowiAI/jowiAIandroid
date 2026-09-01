package ai.workis.jowi.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Canonical Workis tokens — source of truth: iOS WorkisTheme.swift (decision 2026-08-28).

// Fixed (non-flipping) tokens
val Kiremit300 = Color(0xFFF29C73)
val Kiremit400 = Color(0xFFE8703F) // primary button fill, both schemes
val Kiremit500 = Color(0xFFD95F2E) // the slash, icons, focus ring
val Kiremit800 = Color(0xFF6E3319) // motifs, pressed state
val OnKiremitFill = Color(0xFF16171B)
val SuccessGreen = Color(0xFF6FBF73)
val SuccessBg = Color(0xFF2C382C)
val Beige = Color(0xFFD9D2A0)
val BeigeBg = Color(0xFF45412D)

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
)

val LocalWorkisColors = staticCompositionLocalOf { WorkisLightColors }
