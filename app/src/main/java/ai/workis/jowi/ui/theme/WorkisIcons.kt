package ai.workis.jowi.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Minimal line icons matching the iOS SF Symbols the app uses
// (globe, sparkles, grid, tray, building, bubble). Icon() tints them.

private fun stroked(
    name: String,
    width: Float = 1.8f,
    block: androidx.compose.ui.graphics.vector.ImageVector.Builder.() -> Unit,
): ImageVector = ImageVector.Builder(
    name = name, defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f,
).apply(block).build()

object WorkisIcons {

    val Globe: ImageVector = stroked("Globe") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 12f)
            arcToRelative(9f, 9f, 0f, true, false, 18f, 0f)
            arcToRelative(9f, 9f, 0f, true, false, -18f, 0f)
            moveTo(7.5f, 12f)
            arcToRelative(4.5f, 9f, 0f, true, false, 9f, 0f)
            arcToRelative(4.5f, 9f, 0f, true, false, -9f, 0f)
            moveTo(3.5f, 12f)
            lineTo(20.5f, 12f)
        }
    }

    val Sparkle: ImageVector = ImageVector.Builder(
        name = "Sparkle", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(10f, 5f)
            quadTo(11.3f, 11.2f, 17.5f, 12.5f)
            quadTo(11.3f, 13.8f, 10f, 20f)
            quadTo(8.7f, 13.8f, 2.5f, 12.5f)
            quadTo(8.7f, 11.2f, 10f, 5f)
            close()
            moveTo(17.5f, 2.5f)
            quadTo(18.1f, 5.4f, 21f, 6f)
            quadTo(18.1f, 6.6f, 17.5f, 9.5f)
            quadTo(16.9f, 6.6f, 14f, 6f)
            quadTo(16.9f, 5.4f, 17.5f, 2.5f)
            close()
        }
    }.build()

    val Grid: ImageVector = ImageVector.Builder(
        name = "Grid", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 4f); lineTo(10.5f, 4f); lineTo(10.5f, 10.5f); lineTo(4f, 10.5f); close()
            moveTo(13.5f, 4f); lineTo(20f, 4f); lineTo(20f, 10.5f); lineTo(13.5f, 10.5f); close()
            moveTo(4f, 13.5f); lineTo(10.5f, 13.5f); lineTo(10.5f, 20f); lineTo(4f, 20f); close()
            moveTo(13.5f, 13.5f); lineTo(20f, 13.5f); lineTo(20f, 20f); lineTo(13.5f, 20f); close()
        }
    }.build()

    val Tray: ImageVector = stroked("Tray") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
            moveTo(4f, 13f); lineTo(9f, 13f); lineTo(10.5f, 15.5f); lineTo(13.5f, 15.5f)
            lineTo(15f, 13f); lineTo(20f, 13f)
        }
    }

    val Building: ImageVector = stroked("Building") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5f, 20f); lineTo(5f, 4f); lineTo(14f, 4f); lineTo(14f, 20f)
            moveTo(14f, 9f); lineTo(19f, 9f); lineTo(19f, 20f)
            moveTo(3.5f, 20f); lineTo(20.5f, 20f)
            moveTo(8f, 7.5f); lineTo(11f, 7.5f)
            moveTo(8f, 11f); lineTo(11f, 11f)
            moveTo(8f, 14.5f); lineTo(11f, 14.5f)
        }
    }

    val Bubble: ImageVector = stroked("Bubble") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 16f); lineTo(11f, 16f)
            lineTo(7.5f, 19f); lineTo(7.5f, 16f); lineTo(4f, 16f); close()
        }
    }
}
