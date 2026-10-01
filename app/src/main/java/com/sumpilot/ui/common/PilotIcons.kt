package com.sumpilot.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Small bundled icons that are not part of material-icons-core. */
object PilotIcons {
    val Backspace: ImageVector by lazy {
        ImageVector.Builder("Backspace", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 5f); lineTo(21f, 5f); lineTo(21f, 19f); lineTo(9f, 19f); lineTo(3f, 12f); close()
                moveTo(12f, 9f); lineTo(17f, 15f)
                moveTo(17f, 9f); lineTo(12f, 15f)
            }
        }.build()
    }

    val History: ImageVector by lazy {
        ImageVector.Builder("History", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(4f, 12f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 20f)
                moveTo(4f, 12f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 12f, y1 = 4f)
                arcTo(8f, 8f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 20f, y1 = 12f)
                moveTo(12f, 8f); lineTo(12f, 12f); lineTo(15f, 14f)
            }
        }.build()
    }

    val Hint: ImageVector by lazy {
        ImageVector.Builder("Hint", 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(9f, 18f); lineTo(15f, 18f)
                moveTo(10f, 21f); lineTo(14f, 21f)
                moveTo(9f, 15f)
                curveTo(7f, 13.5f, 6f, 11.8f, 6f, 10f)
                arcTo(6f, 6f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 18f, y1 = 10f)
                curveTo(18f, 11.8f, 17f, 13.5f, 15f, 15f)
                close()
            }
        }.build()
    }

    val Plane: ImageVector by lazy {
        ImageVector.Builder("Plane", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(2f, 11f); lineTo(22f, 3f); lineTo(15f, 21f); lineTo(11f, 14f); close()
            }
        }.build()
    }
}
