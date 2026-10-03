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

    val Photo: ImageVector = stroked("Photo") {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
            moveTo(4f, 16f); lineTo(9f, 11f); lineTo(13f, 15f); lineTo(16f, 12f); lineTo(20f, 16f)
            moveTo(15f, 9f); arcToRelative(1f, 1f, 0f, true, false, 2f, 0f); arcToRelative(1f, 1f, 0f, true, false, -2f, 0f)
        }
    }

    val Camera: ImageVector = stroked("Camera") {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(3f, 8f); lineTo(7f, 8f); lineTo(9f, 5f); lineTo(15f, 5f); lineTo(17f, 8f); lineTo(21f, 8f); lineTo(21f, 19f); lineTo(3f, 19f); close()
            moveTo(8.5f, 13f); arcToRelative(3.5f, 3.5f, 0f, true, false, 7f, 0f); arcToRelative(3.5f, 3.5f, 0f, true, false, -7f, 0f)
        }
    }

    /** SF "qrcode.viewfinder" — the carrier's label scan. */
    val QrScan: ImageVector = stroked("QrScan") {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(3f, 8f); lineTo(3f, 3f); lineTo(8f, 3f)
            moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f)
            moveTo(21f, 16f); lineTo(21f, 21f); lineTo(16f, 21f)
            moveTo(8f, 21f); lineTo(3f, 21f); lineTo(3f, 16f)
            moveTo(7f, 7f); lineTo(11f, 7f); lineTo(11f, 11f); lineTo(7f, 11f); close()
            moveTo(13f, 13f); lineTo(17f, 13f); lineTo(17f, 17f); lineTo(13f, 17f); close()
            moveTo(14f, 7f); lineTo(17f, 7f); lineTo(17f, 10f)
            moveTo(7f, 14f); lineTo(7f, 17f); lineTo(10f, 17f)
        }
    }

    val Link: ImageVector = stroked("Link") {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(10f, 14f); lineTo(14f, 10f)
            moveTo(8.5f, 17.5f); lineTo(6.5f, 19.5f); arcToRelative(3f, 3f, 0f, true, true, -4.2f, -4.2f); lineTo(6.5f, 11f)
            moveTo(15.5f, 6.5f); lineTo(17.5f, 4.5f); arcToRelative(3f, 3f, 0f, true, true, 4.2f, 4.2f); lineTo(17.5f, 13f)
        }
    }

    val ShippingBox: ImageVector = stroked("ShippingBox") {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(12f, 3f); lineTo(20f, 7.5f); lineTo(20f, 16.5f); lineTo(12f, 21f); lineTo(4f, 16.5f); lineTo(4f, 7.5f); close()
            moveTo(4f, 7.5f); lineTo(12f, 12f); lineTo(20f, 7.5f)
            moveTo(12f, 12f); lineTo(12f, 21f)
        }
    }

    /** SF "truck.box" — the carrier seat's Kargo door. */
    val Truck: ImageVector = stroked("Truck") {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(2f, 6f); lineTo(14f, 6f); lineTo(14f, 17f); lineTo(2f, 17f); close()
            moveTo(14f, 10f); lineTo(18.5f, 10f); lineTo(22f, 13.5f); lineTo(22f, 17f); lineTo(14f, 17f)
            moveTo(4.5f, 18.5f); arcToRelative(1.8f, 1.8f, 0f, true, false, 3.6f, 0f); arcToRelative(1.8f, 1.8f, 0f, true, false, -3.6f, 0f)
            moveTo(15.5f, 18.5f); arcToRelative(1.8f, 1.8f, 0f, true, false, 3.6f, 0f); arcToRelative(1.8f, 1.8f, 0f, true, false, -3.6f, 0f)
        }
    }

    val PersonKey: ImageVector = stroked("PersonKey") {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(6f, 8f); arcToRelative(4f, 4f, 0f, true, false, 8f, 0f); arcToRelative(4f, 4f, 0f, true, false, -8f, 0f)
            moveTo(3f, 20f); curveTo(3f, 16f, 6f, 14f, 10f, 14f); curveTo(11.5f, 14f, 12.5f, 14.2f, 13.5f, 14.6f)
            moveTo(16f, 17f); arcToRelative(2f, 2f, 0f, true, false, 4f, 0f); arcToRelative(2f, 2f, 0f, true, false, -4f, 0f)
            moveTo(20f, 17f); lineTo(23f, 17f)
        }
    }

    /** The receiver's delivery code on the order card (SF `key`). */
    val Key: ImageVector = stroked("Key") {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(3f, 12f); arcToRelative(4f, 4f, 0f, true, false, 8f, 0f); arcToRelative(4f, 4f, 0f, true, false, -8f, 0f)
            moveTo(11f, 12f); lineTo(21f, 12f); lineTo(21f, 15f)
            moveTo(17f, 12f); lineTo(17f, 14.5f)
        }
    }

    val DocText: ImageVector = stroked("DocText") {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(6f, 3f); lineTo(14f, 3f); lineTo(19f, 8f); lineTo(19f, 21f); lineTo(6f, 21f); close()
            moveTo(14f, 3f); lineTo(14f, 8f); lineTo(19f, 8f)
            moveTo(9f, 12f); lineTo(16f, 12f); moveTo(9f, 15.5f); lineTo(16f, 15.5f); moveTo(9f, 19f); lineTo(13f, 19f)
        }
    }

    /** The shipment label (SF `printer`). */
    val Printer: ImageVector = stroked("Printer") {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(7f, 8f); lineTo(7f, 3f); lineTo(17f, 3f); lineTo(17f, 8f)
            moveTo(7f, 17f); lineTo(4f, 17f); lineTo(4f, 8f); lineTo(20f, 8f); lineTo(20f, 17f); lineTo(17f, 17f)
            moveTo(7f, 14f); lineTo(17f, 14f); lineTo(17f, 21f); lineTo(7f, 21f); close()
        }
    }

    val Table: ImageVector = stroked("Table") {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 19f); lineTo(3f, 19f); close()
            moveTo(3f, 10f); lineTo(21f, 10f); moveTo(3f, 14.5f); lineTo(21f, 14.5f); moveTo(10f, 5f); lineTo(10f, 19f)
        }
    }

    val Play: ImageVector = stroked("Play") {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(3f, 6f); lineTo(21f, 6f); lineTo(21f, 18f); lineTo(3f, 18f); close()
            moveTo(10f, 9f); lineTo(15f, 12f); lineTo(10f, 15f); close()
        }
    }

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

    /** SF "graduationcap" — the expert door's icon. */
    val GraduationCap: ImageVector = stroked("GraduationCap") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(2.5f, 9.5f); lineTo(12f, 5f); lineTo(21.5f, 9.5f); lineTo(12f, 14f); close()
            moveTo(6.5f, 11.5f); lineTo(6.5f, 16f)
            quadTo(12f, 20.5f, 17.5f, 16f); lineTo(17.5f, 11.5f)
            moveTo(21.5f, 9.5f); lineTo(21.5f, 15f)
        }
    }

    /** SF "circle.lefthalf.filled" — appearance: system. */
    val HalfCircle: ImageVector = ImageVector.Builder(
        name = "HalfCircle", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
        ) {
            moveTo(3f, 12f)
            arcToRelative(9f, 9f, 0f, true, false, 18f, 0f)
            arcToRelative(9f, 9f, 0f, true, false, -18f, 0f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 3f)
            arcToRelative(9f, 9f, 0f, false, false, 0f, 18f)
            close()
        }
    }.build()

    /** SF "sun.max" — appearance: light. */
    val Sun: ImageVector = stroked("Sun") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8f, 12f)
            arcToRelative(4f, 4f, 0f, true, false, 8f, 0f)
            arcToRelative(4f, 4f, 0f, true, false, -8f, 0f)
            moveTo(12f, 2.5f); lineTo(12f, 5f)
            moveTo(12f, 19f); lineTo(12f, 21.5f)
            moveTo(2.5f, 12f); lineTo(5f, 12f)
            moveTo(19f, 12f); lineTo(21.5f, 12f)
            moveTo(5.3f, 5.3f); lineTo(7f, 7f)
            moveTo(17f, 17f); lineTo(18.7f, 18.7f)
            moveTo(5.3f, 18.7f); lineTo(7f, 17f)
            moveTo(17f, 7f); lineTo(18.7f, 5.3f)
        }
    }

    /** SF "moon" — appearance: dark. */
    val Moon: ImageVector = stroked("Moon") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(20f, 14.5f)
            arcToRelative(8.5f, 8.5f, 0f, true, true, -10.5f, -10.5f)
            arcToRelative(7f, 7f, 0f, false, false, 10.5f, 10.5f)
            close()
        }
    }

    /** SF "questionmark.circle" — waiting questions tile. */
    val QuestionCircle: ImageVector = stroked("QuestionCircle") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 12f)
            arcToRelative(9f, 9f, 0f, true, false, 18f, 0f)
            arcToRelative(9f, 9f, 0f, true, false, -18f, 0f)
            moveTo(9.2f, 9.5f)
            arcToRelative(2.8f, 2.8f, 0f, true, true, 4f, 2.5f)
            quadTo(12f, 12.6f, 12f, 14f)
            moveTo(12f, 17f); lineTo(12f, 17.2f)
        }
    }

    /** SF "arrow.right.circle" — Jowi's "Bir örnek daha" on a scene answer. */
    val ArrowRightCircle: ImageVector = stroked("ArrowRightCircle") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 12f)
            arcToRelative(9f, 9f, 0f, true, false, 18f, 0f)
            arcToRelative(9f, 9f, 0f, true, false, -18f, 0f)
            moveTo(8f, 12f); lineTo(16f, 12f)
            moveTo(12.5f, 8.5f); lineTo(16f, 12f); lineTo(12.5f, 15.5f)
        }
    }

    /** SF "hand.thumbsdown" — reviews tile. */
    val ThumbsDown: ImageVector = stroked("ThumbsDown") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(7f, 4f); lineTo(16.5f, 4f); lineTo(16.5f, 14f); lineTo(12.5f, 20.5f)
            quadTo(10f, 20f, 10.5f, 17f); lineTo(11f, 14f); lineTo(5.5f, 14f)
            quadTo(3.5f, 14f, 4f, 12f); lineTo(5f, 6f); quadTo(5.3f, 4f, 7f, 4f); close()
            moveTo(16.5f, 4f); lineTo(20f, 4f); lineTo(20f, 14f); lineTo(16.5f, 14f)
        }
    }

    /** SF "pencil.line" — knowledge tile. */
    val Pencil: ImageVector = stroked("Pencil") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 16f); lineTo(14.5f, 5.5f); lineTo(18.5f, 9.5f); lineTo(8f, 20f); lineTo(4f, 20f); close()
            moveTo(12f, 20f); lineTo(20.5f, 20f)
        }
    }

    /** SF "chart.bar" — this month. */
    val ChartBars: ImageVector = stroked("ChartBars") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 20f); lineTo(4f, 13f); lineTo(8f, 13f); lineTo(8f, 20f); close()
            moveTo(10f, 20f); lineTo(10f, 7f); lineTo(14f, 7f); lineTo(14f, 20f); close()
            moveTo(16f, 20f); lineTo(16f, 4f); lineTo(20f, 4f); lineTo(20f, 20f); close()
        }
    }

    /** SF "checklist" — setup. */
    val Checklist: ImageVector = stroked("Checklist") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3.5f, 7f); lineTo(5f, 8.5f); lineTo(8f, 5.5f)
            moveTo(11f, 7f); lineTo(20.5f, 7f)
            moveTo(3.5f, 15f); lineTo(5f, 16.5f); lineTo(8f, 13.5f)
            moveTo(11f, 15f); lineTo(20.5f, 15f)
        }
    }

    /** SF "ruler" — a list row's variant ("varyant seçin", "Varyant seç"). */
    val Ruler: ImageVector = stroked("Ruler") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 8f); lineTo(21f, 8f); lineTo(21f, 16f); lineTo(3f, 16f); close()
            moveTo(7f, 8f); lineTo(7f, 11.5f)
            moveTo(11f, 8f); lineTo(11f, 13f)
            moveTo(15f, 8f); lineTo(15f, 11.5f)
            moveTo(19f, 8f); lineTo(19f, 13f)
        }
    }

    /** SF "hand.tap" — a list row waiting for its product ("ürünü seçin"). */
    val HandTap: ImageVector = stroked("HandTap") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(9f, 13f); lineTo(9f, 5.5f)
            arcToRelative(1.5f, 1.5f, 0f, false, true, 3f, 0f)
            lineTo(12f, 11f); lineTo(17.5f, 12f)
            arcToRelative(2f, 2f, 0f, false, true, 1.6f, 2.3f)
            lineTo(18.3f, 18.5f)
            arcToRelative(3f, 3f, 0f, false, true, -3f, 2.5f)
            lineTo(11.5f, 21f)
            arcToRelative(3f, 3f, 0f, false, true, -2.4f, -1.2f)
            lineTo(6f, 15.5f)
            arcToRelative(1.4f, 1.4f, 0f, false, true, 2.2f, -1.7f)
            lineTo(9f, 14.8f)
            moveTo(6f, 4f); lineTo(4.5f, 2.5f)
            moveTo(15f, 4f); lineTo(16.5f, 2.5f)
        }
    }

    /** SF "text.alignleft" — a free-text list row (no catalogue match). */
    val TextLeft: ImageVector = stroked("TextLeft") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 6f); lineTo(20f, 6f)
            moveTo(4f, 10f); lineTo(14f, 10f)
            moveTo(4f, 14f); lineTo(20f, 14f)
            moveTo(4f, 18f); lineTo(12f, 18f)
        }
    }

    /** SF "doc.text.badge.plus" — agreement re-acceptance reminder. */
    val DocPlus: ImageVector = stroked("DocPlus") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6f, 3.5f); lineTo(14f, 3.5f); lineTo(18.5f, 8f); lineTo(18.5f, 12f)
            moveTo(18.5f, 12f); lineTo(18.5f, 20.5f); lineTo(6f, 20.5f); close()
            moveTo(9f, 12f); lineTo(14f, 12f)
            moveTo(9f, 15.5f); lineTo(14f, 15.5f)
        }
    }

    /** SF "book" — the earnings guide door. */
    val Book: ImageVector = stroked("Book") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 6.5f)
            quadTo(9f, 4f, 3.5f, 5f); lineTo(3.5f, 18.5f); quadTo(9f, 17.5f, 12f, 20f)
            quadTo(15f, 17.5f, 20.5f, 18.5f); lineTo(20.5f, 5f); quadTo(15f, 4f, 12f, 6.5f)
            lineTo(12f, 20f)
        }
    }

    /** SF "keyboard" — the words as typed, behind the stamp row. */
    val Keyboard: ImageVector = stroked("Keyboard") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 6.5f); lineTo(21f, 6.5f); lineTo(21f, 17.5f); lineTo(3f, 17.5f); close()
            moveTo(6.5f, 10f); lineTo(7f, 10f); moveTo(10f, 10f); lineTo(10.5f, 10f)
            moveTo(13.5f, 10f); lineTo(14f, 10f); moveTo(17f, 10f); lineTo(17.5f, 10f)
            moveTo(8f, 14f); lineTo(16f, 14f)
        }
    }

    /** SF "archivebox" — putting a draft away; never a bin. */
    val Archive: ImageVector = stroked("Archive") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3f, 5f); lineTo(21f, 5f); lineTo(21f, 9f); lineTo(3f, 9f); close()
            moveTo(4.5f, 9f); lineTo(4.5f, 19.5f); lineTo(19.5f, 19.5f); lineTo(19.5f, 9f)
            moveTo(9.5f, 13f); lineTo(14.5f, 13f)
        }
    }

    /** SF "clock". */
    val Clock: ImageVector = stroked("Clock") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3.5f, 12f); arcToRelative(8.5f, 8.5f, 0f, true, false, 17f, 0f); arcToRelative(8.5f, 8.5f, 0f, true, false, -17f, 0f)
            moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15f, 14f)
        }
    }

    /** SF "hourglass" — a proposed term card, waiting for the other seat. */
    val Hourglass: ImageVector = stroked("Hourglass") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(6f, 3.5f); lineTo(18f, 3.5f); moveTo(6f, 20.5f); lineTo(18f, 20.5f)
            moveTo(7.5f, 3.5f); lineTo(7.5f, 6.5f); quadTo(7.5f, 9.5f, 12f, 12f); quadTo(16.5f, 9.5f, 16.5f, 6.5f); lineTo(16.5f, 3.5f)
            moveTo(7.5f, 20.5f); lineTo(7.5f, 17.5f); quadTo(7.5f, 14.5f, 12f, 12f); quadTo(16.5f, 14.5f, 16.5f, 17.5f); lineTo(16.5f, 20.5f)
        }
    }

    /** SF "shield". */
    val Shield: ImageVector = stroked("Shield") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 3f); lineTo(19.5f, 6f); lineTo(19.5f, 11.5f)
            quadTo(19.5f, 17.5f, 12f, 21f); quadTo(4.5f, 17.5f, 4.5f, 11.5f); lineTo(4.5f, 6f); close()
        }
    }

    /** SF "list.clipboard" — a term proposal read from the correspondence. */
    val Clipboard: ImageVector = stroked("Clipboard") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(9f, 5f); lineTo(6f, 5f); lineTo(6f, 21f); lineTo(18f, 21f); lineTo(18f, 5f); lineTo(15f, 5f)
            moveTo(9f, 3.5f); lineTo(15f, 3.5f); lineTo(15f, 6.5f); lineTo(9f, 6.5f); close()
            moveTo(9f, 11f); lineTo(15f, 11f); moveTo(9f, 14.5f); lineTo(15f, 14.5f); moveTo(9f, 18f); lineTo(12.5f, 18f)
        }
    }

    /** SF "arrow.uturn.backward" — returned / withdrawn. */
    val Undo: ImageVector = stroked("Undo") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8.5f, 5f); lineTo(4f, 9.5f); lineTo(8.5f, 14f)
            moveTo(4f, 9.5f); lineTo(14.5f, 9.5f); arcToRelative(5f, 5f, 0f, false, true, 0f, 10f); lineTo(9f, 19.5f)
        }
    }

    /** SF "creditcard" — Cari, the firm's current accounts. */
    val CreditCard: ImageVector = stroked("CreditCard") {
        path(fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(3f, 6f); lineTo(21f, 6f); lineTo(21f, 18f); lineTo(3f, 18f); close()
            moveTo(3f, 10f); lineTo(21f, 10f)
            moveTo(6.5f, 14.5f); lineTo(10f, 14.5f)
        }
    }

    /** SF "calendar" — an open-account term runs on a date, not a lock. */
    val Calendar: ImageVector = stroked("Calendar") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 6f); lineTo(20f, 6f); lineTo(20f, 20f); lineTo(4f, 20f); close()
            moveTo(4f, 10.5f); lineTo(20f, 10.5f)
            moveTo(8.5f, 3.5f); lineTo(8.5f, 7.5f); moveTo(15.5f, 3.5f); lineTo(15.5f, 7.5f)
        }
    }

    /** SF "lock". */
    val Lock: ImageVector = stroked("Lock") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5.5f, 10.5f); lineTo(18.5f, 10.5f); lineTo(18.5f, 20.5f); lineTo(5.5f, 20.5f); close()
            moveTo(8.5f, 10.5f); lineTo(8.5f, 7.5f); arcToRelative(3.5f, 3.5f, 0f, false, true, 7f, 0f); lineTo(15.5f, 10.5f)
        }
    }

    /** SF "exclamationmark.triangle" — stuck / objection; always beside a word. */
    val Warning: ImageVector = stroked("Warning") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(12f, 4f); lineTo(21f, 19.5f); lineTo(3f, 19.5f); close()
            moveTo(12f, 10f); lineTo(12f, 14.5f); moveTo(12f, 17f); lineTo(12f, 17.2f)
        }
    }

    /** SF "arrow.clockwise" — "Yeniden dene". */
    val Retry: ImageVector = stroked("Retry") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(19.5f, 12f); arcToRelative(7.5f, 7.5f, 0f, true, true, -2.6f, -5.7f)
            moveTo(17.5f, 3f); lineTo(17.5f, 7f); lineTo(13.5f, 7f)
        }
    }

    /** SF "checkmark" — one on the table, two side by side once every seat has seen it. */
    val Check: ImageVector = stroked("Check") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 2.2f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19.5f, 7f)
        }
    }

    /** SF "lock.open" — the money let go. */
    val LockOpen: ImageVector = stroked("LockOpen") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(5.5f, 10.5f); lineTo(18.5f, 10.5f); lineTo(18.5f, 20.5f); lineTo(5.5f, 20.5f); close()
            moveTo(8.5f, 10.5f); lineTo(8.5f, 7.5f); arcToRelative(3.5f, 3.5f, 0f, false, true, 7f, 0f)
        }
    }

    /** SF "xmark.circle" — a change not taken. */
    val Cross: ImageVector = stroked("Cross") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(3.5f, 12f); arcToRelative(8.5f, 8.5f, 0f, true, false, 17f, 0f); arcToRelative(8.5f, 8.5f, 0f, true, false, -17f, 0f)
            moveTo(9f, 9f); lineTo(15f, 15f); moveTo(15f, 9f); lineTo(9f, 15f)
        }
    }

    /** The neutral mark for a system line whose kind we do not know. */
    val Dot: ImageVector = stroked("Dot") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4.5f, 12f); arcToRelative(7.5f, 7.5f, 0f, true, false, 15f, 0f); arcToRelative(7.5f, 7.5f, 0f, true, false, -15f, 0f)
            moveTo(9.5f, 12f); arcToRelative(2.5f, 2.5f, 0f, true, false, 5f, 0f); arcToRelative(2.5f, 2.5f, 0f, true, false, -5f, 0f)
        }
    }

    /** SF "magnifyingglass". */
    val Search: ImageVector = stroked("Search") {
        path(
            fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(4f, 10.5f); arcToRelative(6.5f, 6.5f, 0f, true, false, 13f, 0f); arcToRelative(6.5f, 6.5f, 0f, true, false, -13f, 0f)
            moveTo(15.5f, 15.5f); lineTo(20.5f, 20.5f)
        }
    }
}
