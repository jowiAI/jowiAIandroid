package ai.workis.jowi.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
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
)
