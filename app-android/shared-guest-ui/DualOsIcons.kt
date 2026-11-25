package com.fahadali.oneos.shared

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lightweight Material Design vector icons shared by :app and :guest.
 * Keeps APKs compact (avoids bundling the ~65 MB material-icons-extended DEX).
 */
object DualOsIcons {
    private inline fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                fillAlpha = 1f,
                stroke = null,
                strokeAlpha = 1f,
                strokeLineWidth = 1f,
                strokeLineCap = StrokeCap.Butt,
                strokeLineJoin = StrokeJoin.Bevel,
                strokeLineMiter = 1f,
                pathFillType = PathFillType.NonZero,
                pathBuilder = block,
            )
        }.build()

    val Apps: ImageVector by lazy {
        icon("Apps") {
            moveTo(4f, 8f); horizontalLineToRelative(4f); verticalLineTo(4f); horizontalLineTo(4f); verticalLineToRelative(4f); close()
            moveTo(10f, 20f); horizontalLineToRelative(4f); verticalLineToRelative(-4f); horizontalLineToRelative(-4f); verticalLineToRelative(4f); close()
            moveTo(4f, 20f); horizontalLineToRelative(4f); verticalLineToRelative(-4f); horizontalLineTo(4f); verticalLineToRelative(4f); close()
            moveTo(4f, 14f); horizontalLineToRelative(4f); verticalLineToRelative(-4f); horizontalLineTo(4f); verticalLineToRelative(4f); close()
            moveTo(10f, 14f); horizontalLineToRelative(4f); verticalLineToRelative(-4f); horizontalLineToRelative(-4f); verticalLineToRelative(4f); close()
            moveTo(16f, 4f); verticalLineToRelative(4f); horizontalLineToRelative(4f); verticalLineTo(4f); horizontalLineToRelative(-4f); close()
            moveTo(10f, 8f); horizontalLineToRelative(4f); verticalLineTo(4f); horizontalLineToRelative(-4f); verticalLineToRelative(4f); close()
            moveTo(16f, 14f); horizontalLineToRelative(4f); verticalLineToRelative(-4f); horizontalLineToRelative(-4f); verticalLineToRelative(4f); close()
            moveTo(16f, 20f); horizontalLineToRelative(4f); verticalLineToRelative(-4f); horizontalLineToRelative(-4f); verticalLineToRelative(4f); close()
        }
    }

    val Layers: ImageVector by lazy {
        icon("Layers") {
            moveTo(11.99f, 18.54f); lineToRelative(-7.37f, -5.73f); lineTo(3f, 14.07f); lineToRelative(9f, 7f); lineToRelative(9f, -7f); lineToRelative(-1.63f, -1.27f); lineToRelative(-7.38f, 5.74f); close()
            moveTo(12f, 16f); lineToRelative(7.36f, -5.73f); lineTo(21f, 9f); lineToRelative(-9f, -7f); lineToRelative(-9f, 7f); lineToRelative(1.63f, 1.27f); lineTo(12f, 16f); close()
        }
    }

    val Extension: ImageVector by lazy {
        icon("Extension") {
            moveTo(20.5f, 11f); horizontalLineTo(19f); verticalLineTo(7f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); horizontalLineToRelative(-4f); verticalLineTo(3.5f)
            curveTo(13f, 2.12f, 11.88f, 1f, 10.5f, 1f); reflectiveCurveTo(8f, 2.12f, 8f, 3.5f); verticalLineTo(5f); horizontalLineTo(4f)
            curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f); verticalLineToRelative(3.8f); horizontalLineTo(3.5f); curveToRelative(1.49f, 0f, 2.7f, 1.21f, 2.7f, 2.7f)
            reflectiveCurveToRelative(-1.21f, 2.7f, -2.7f, 2.7f); horizontalLineTo(2f); verticalLineTo(20f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f); horizontalLineToRelative(3.8f)
            verticalLineToRelative(-1.5f); curveToRelative(0f, -1.49f, 1.21f, -2.7f, 2.7f, -2.7f); reflectiveCurveToRelative(2.7f, 1.21f, 2.7f, 2.7f); verticalLineTo(22f); horizontalLineTo(17f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineToRelative(-4f); horizontalLineToRelative(1.5f); curveToRelative(1.38f, 0f, 2.5f, -1.12f, 2.5f, -2.5f)
            reflectiveCurveTo(21.88f, 11f, 20.5f, 11f); close()
        }
    }

    val Security: ImageVector by lazy {
        icon("Security") {
            moveTo(12f, 1f); lineTo(3f, 5f); verticalLineToRelative(6f); curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f)
            curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f); verticalLineTo(5f); lineToRelative(-9f, -4f); close()
            moveTo(12f, 11.99f); horizontalLineToRelative(7f); curveToRelative(-0.53f, 4.12f, -3.28f, 7.79f, -7f, 8.94f)
            verticalLineTo(12f); horizontalLineTo(5f); verticalLineTo(6.3f); lineToRelative(7f, -3.11f); verticalLineToRelative(8.8f); close()
        }
    }

    val Shield: ImageVector by lazy {
        icon("Shield") {
            moveTo(12f, 1f); lineTo(3f, 5f); verticalLineToRelative(6f); curveToRelative(0f, 5.55f, 3.84f, 10.74f, 9f, 12f)
            curveToRelative(5.16f, -1.26f, 9f, -6.45f, 9f, -12f); verticalLineTo(5f); lineToRelative(-9f, -4f); close()
            moveTo(10f, 17f); lineToRelative(-4f, -4f); lineToRelative(1.41f, -1.41f); lineTo(10f, 14.17f); lineToRelative(6.59f, -6.59f); lineTo(18f, 9f); lineToRelative(-8f, 8f); close()
        }
    }

    val PhoneAndroid: ImageVector by lazy {
        icon("PhoneAndroid") {
            moveTo(16f, 1f); horizontalLineTo(8f); curveTo(6.34f, 1f, 5f, 2.34f, 5f, 4f); verticalLineToRelative(16f)
            curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f); horizontalLineToRelative(8f); curveToRelative(1.66f, 0f, 3f, -1.34f, 3f, -3f)
            verticalLineTo(4f); curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f); close()
            moveTo(14f, 21f); horizontalLineToRelative(-4f); verticalLineToRelative(-1f); horizontalLineToRelative(4f); verticalLineToRelative(1f); close()
            moveTo(17.25f, 18f); horizontalLineTo(6.75f); verticalLineTo(4f); horizontalLineToRelative(10.5f); verticalLineToRelative(14f); close()
        }
    }

    val RestartAlt: ImageVector by lazy {
        icon("RestartAlt") {
            moveTo(12f, 5f); verticalLineTo(2f); lineTo(8f, 6f); lineToRelative(4f, 4f); verticalLineTo(7f)
            curveToRelative(3.31f, 0f, 6f, 2.69f, 6f, 6f); curveToRelative(0f, 2.97f, -2.17f, 5.43f, -5f, 5.91f)
            verticalLineToRelative(2.02f); curveToRelative(3.95f, -0.49f, 7f, -3.85f, 7f, -7.93f)
            curveToRelative(0f, -4.42f, -3.58f, -8f, -8f, -8f); close()
            moveTo(6f, 13f); curveToRelative(0f, -1.65f, 0.67f, -3.15f, 1.76f, -4.24f); lineTo(6.34f, 7.34f)
            curveTo(4.9f, 8.79f, 4f, 10.79f, 4f, 13f); curveToRelative(0f, 4.08f, 3.05f, 7.44f, 7f, 7.93f)
            verticalLineToRelative(-2.02f); curveToRelative(-2.83f, -0.48f, -5f, -2.94f, -5f, -5.91f); close()
        }
    }

    val BugReport: ImageVector by lazy {
        icon("BugReport") {
            moveTo(20f, 8f); horizontalLineToRelative(-2.81f); curveToRelative(-0.45f, -0.78f, -1.07f, -1.45f, -1.82f, -1.96f)
            lineTo(17f, 4.41f); lineTo(15.59f, 3f); lineToRelative(-2.17f, 2.17f); curveTo(12.96f, 5.06f, 12.49f, 5f, 12f, 5f)
            curveToRelative(-0.49f, 0f, -0.96f, 0.06f, -1.41f, 0.17f); lineTo(8.41f, 3f); lineTo(7f, 4.41f); lineToRelative(1.62f, 1.63f)
            curveTo(7.88f, 6.55f, 7.26f, 7.22f, 6.81f, 8f); horizontalLineTo(4f); verticalLineToRelative(2f); horizontalLineToRelative(2.09f)
            curveToRelative(-0.05f, 0.33f, -0.09f, 0.66f, -0.09f, 1f); verticalLineToRelative(1f); horizontalLineTo(4f); verticalLineToRelative(2f)
            horizontalLineToRelative(2f); verticalLineToRelative(1f); curveToRelative(0f, 0.34f, 0.04f, 0.67f, 0.09f, 1f); horizontalLineTo(4f)
            verticalLineToRelative(2f); horizontalLineToRelative(2.81f); curveToRelative(1.04f, 1.79f, 2.97f, 3f, 5.19f, 3f)
            reflectiveCurveToRelative(4.15f, -1.21f, 5.19f, -3f); horizontalLineTo(20f); verticalLineToRelative(-2f); horizontalLineToRelative(-2.09f)
            curveToRelative(0.05f, -0.33f, 0.09f, -0.66f, 0.09f, -1f); verticalLineToRelative(-1f); horizontalLineToRelative(2f); verticalLineToRelative(-2f)
            horizontalLineToRelative(-2f); verticalLineToRelative(-1f); curveToRelative(0f, -0.34f, -0.04f, -0.67f, -0.09f, -1f); horizontalLineTo(20f); verticalLineTo(8f); close()
            moveTo(14f, 16f); horizontalLineToRelative(-4f); verticalLineToRelative(-2f); horizontalLineToRelative(4f); verticalLineToRelative(2f); close()
            moveTo(14f, 12f); horizontalLineToRelative(-4f); verticalLineToRelative(-2f); horizontalLineToRelative(4f); verticalLineToRelative(2f); close()
        }
    }

    val Checklist: ImageVector by lazy {
        icon("Checklist") {
            moveTo(22f, 7f); horizontalLineToRelative(-9f); verticalLineToRelative(2f); horizontalLineToRelative(9f); verticalLineTo(7f); close()
            moveTo(22f, 15f); horizontalLineToRelative(-9f); verticalLineToRelative(2f); horizontalLineToRelative(9f); verticalLineToRelative(-2f); close()
            moveTo(5.54f, 11f); lineTo(2f, 7.46f); lineToRelative(1.41f, -1.41f); lineToRelative(2.12f, 2.12f); lineToRelative(4.24f, -4.24f); lineToRelative(1.41f, 1.41f); lineTo(5.54f, 11f); close()
            moveTo(5.54f, 19f); lineTo(2f, 15.46f); lineToRelative(1.41f, -1.41f); lineToRelative(2.12f, 2.12f); lineToRelative(4.24f, -4.24f); lineToRelative(1.41f, 1.41f); lineTo(5.54f, 19f); close()
        }
    }

    val Science: ImageVector by lazy {
        icon("Science") {
            moveTo(19.8f, 18.4f); lineTo(14f, 10.67f); verticalLineTo(6.5f); lineToRelative(1.35f, -1.69f)
            curveTo(15.61f, 4.48f, 15.38f, 4f, 14.96f, 4f); horizontalLineTo(9.04f); curveToRelative(-0.42f, 0f, -0.65f, 0.48f, -0.39f, 0.81f)
            lineTo(10f, 6.5f); verticalLineToRelative(4.17f); lineTo(4.2f, 18.4f); curveTo(3.71f, 19.06f, 4.18f, 20f, 5f, 20f)
            horizontalLineToRelative(14f); curveTo(19.82f, 20f, 20.29f, 19.06f, 19.8f, 18.4f); close()
        }
    }

    val Storage: ImageVector by lazy {
        icon("Storage") {
            moveTo(2f, 20f); horizontalLineToRelative(20f); verticalLineToRelative(-4f); horizontalLineTo(2f); verticalLineToRelative(4f); close()
            moveTo(4f, 17f); horizontalLineToRelative(2f); verticalLineToRelative(2f); horizontalLineTo(4f); verticalLineToRelative(-2f); close()
            moveTo(2f, 4f); verticalLineToRelative(4f); horizontalLineToRelative(20f); verticalLineTo(4f); horizontalLineTo(2f); close()
            moveTo(6f, 7f); horizontalLineTo(4f); verticalLineTo(5f); horizontalLineToRelative(2f); verticalLineToRelative(2f); close()
            moveTo(2f, 14f); horizontalLineToRelative(20f); verticalLineToRelative(-4f); horizontalLineTo(2f); verticalLineToRelative(4f); close()
            moveTo(4f, 11f); horizontalLineToRelative(2f); verticalLineToRelative(2f); horizontalLineTo(4f); verticalLineToRelative(-2f); close()
        }
    }

    val Battery: ImageVector by lazy {
        icon("Battery") {
            moveTo(15.67f, 4f); horizontalLineTo(14f); verticalLineTo(2f); horizontalLineToRelative(-4f); verticalLineToRelative(2f); horizontalLineTo(8.33f)
            curveTo(7.6f, 4f, 7f, 4.6f, 7f, 5.33f); verticalLineToRelative(15.33f); curveTo(7f, 21.4f, 7.6f, 22f, 8.33f, 22f)
            horizontalLineToRelative(7.33f); curveToRelative(0.74f, 0f, 1.34f, -0.6f, 1.34f, -1.33f); verticalLineTo(5.33f)
            curveTo(17f, 4.6f, 16.4f, 4f, 15.67f, 4f); close()
        }
    }

    val Nfc: ImageVector by lazy {
        icon("Nfc") {
            moveTo(20f, 2f); horizontalLineTo(4f); curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f); verticalLineToRelative(16f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f); horizontalLineToRelative(16f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(4f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
            moveTo(20f, 20f); horizontalLineTo(4f); verticalLineTo(4f); horizontalLineToRelative(16f); verticalLineToRelative(16f); close()
            moveTo(18f, 6f); horizontalLineToRelative(-5f); curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f); verticalLineToRelative(2.28f)
            curveToRelative(-0.6f, 0.35f, -1f, 0.98f, -1f, 1.72f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            reflectiveCurveToRelative(2f, -0.9f, 2f, -2f); curveToRelative(0f, -0.74f, -0.4f, -1.38f, -1f, -1.72f)
            verticalLineTo(8f); horizontalLineToRelative(3f); verticalLineToRelative(8f); horizontalLineTo(8f); verticalLineTo(8f)
            horizontalLineToRelative(2f); verticalLineTo(6f); horizontalLineTo(6f); verticalLineToRelative(12f); horizontalLineToRelative(12f); verticalLineTo(6f); close()
        }
    }

    val SdCard: ImageVector by lazy {
        icon("SdCard") {
            moveTo(18f, 2f); horizontalLineToRelative(-8f); lineTo(4.02f, 8f); lineTo(4f, 20f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(12f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineTo(4f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
            moveTo(12f, 8f); horizontalLineToRelative(-2f); verticalLineTo(4f); horizontalLineToRelative(2f); verticalLineToRelative(4f); close()
            moveTo(15f, 8f); horizontalLineToRelative(-2f); verticalLineTo(4f); horizontalLineToRelative(2f); verticalLineToRelative(4f); close()
            moveTo(18f, 8f); horizontalLineToRelative(-2f); verticalLineTo(4f); horizontalLineToRelative(2f); verticalLineToRelative(4f); close()
        }
    }

    val BlurOn: ImageVector by lazy {
        icon("BlurOn") {
            moveTo(6f, 13f); curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f); reflectiveCurveToRelative(0.45f, 1f, 1f, 1f); reflectiveCurveToRelative(1f, -0.45f, 1f, -1f); reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f); close()
            moveTo(6f, 9f); curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f); reflectiveCurveToRelative(0.45f, 1f, 1f, 1f); reflectiveCurveToRelative(1f, -0.45f, 1f, -1f); reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f); close()
            moveTo(14f, 17f); curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f); reflectiveCurveToRelative(0.45f, 1f, 1f, 1f); reflectiveCurveToRelative(1f, -0.45f, 1f, -1f); reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f); close()
            moveTo(10f, 17f); curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f); reflectiveCurveToRelative(0.45f, 1f, 1f, 1f); reflectiveCurveToRelative(1f, -0.45f, 1f, -1f); reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f); close()
            moveTo(14f, 5f); curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f); reflectiveCurveToRelative(0.45f, 1f, 1f, 1f); reflectiveCurveToRelative(1f, -0.45f, 1f, -1f); reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f); close()
            moveTo(10f, 5f); curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f); reflectiveCurveToRelative(0.45f, 1f, 1f, 1f); reflectiveCurveToRelative(1f, -0.45f, 1f, -1f); reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f); close()
            moveTo(18f, 9f); curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f); reflectiveCurveToRelative(0.45f, 1f, 1f, 1f); reflectiveCurveToRelative(1f, -0.45f, 1f, -1f); reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f); close()
            moveTo(18f, 13f); curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f); reflectiveCurveToRelative(0.45f, 1f, 1f, 1f); reflectiveCurveToRelative(1f, -0.45f, 1f, -1f); reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f); close()
            moveTo(10f, 9f); curveToRelative(-0.83f, 0f, -1.5f, 0.67f, -1.5f, 1.5f); reflectiveCurveTo(9.17f, 12f, 10f, 12f); reflectiveCurveToRelative(1.5f, -0.67f, 1.5f, -1.5f); reflectiveCurveTo(10.83f, 9f, 10f, 9f); close()
            moveTo(14f, 9f); curveToRelative(-0.83f, 0f, -1.5f, 0.67f, -1.5f, 1.5f); reflectiveCurveTo(13.17f, 12f, 14f, 12f); reflectiveCurveToRelative(1.5f, -0.67f, 1.5f, -1.5f); reflectiveCurveTo(14.83f, 9f, 14f, 9f); close()
            moveTo(10f, 13f); curveToRelative(-0.83f, 0f, -1.5f, 0.67f, -1.5f, 1.5f); reflectiveCurveTo(9.17f, 16f, 10f, 16f); reflectiveCurveToRelative(1.5f, -0.67f, 1.5f, -1.5f); reflectiveCurveTo(10.83f, 13f, 10f, 13f); close()
            moveTo(14f, 13f); curveToRelative(-0.83f, 0f, -1.5f, 0.67f, -1.5f, 1.5f); reflectiveCurveTo(13.17f, 16f, 14f, 16f); reflectiveCurveToRelative(1.5f, -0.67f, 1.5f, -1.5f); reflectiveCurveTo(14.83f, 13f, 14f, 13f); close()
        }
    }

    val FolderOpen: ImageVector by lazy {
        icon("FolderOpen") {
            moveTo(20f, 6f); horizontalLineToRelative(-8f); lineToRelative(-2f, -2f); horizontalLineTo(4f)
            curveTo(2.9f, 4f, 2.01f, 4.9f, 2.01f, 6f); lineTo(2f, 18f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineToRelative(16f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineTo(8f)
            curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); close()
            moveTo(20f, 18f); horizontalLineTo(4f); verticalLineTo(8f); horizontalLineToRelative(16f); verticalLineToRelative(10f); close()
        }
    }

    val Download: ImageVector by lazy {
        icon("Download") {
            moveTo(5f, 20f); horizontalLineToRelative(14f); verticalLineToRelative(-2f); horizontalLineTo(5f); verticalLineToRelative(2f); close()
            moveTo(19f, 9f); horizontalLineToRelative(-4f); verticalLineTo(3f); horizontalLineTo(9f); verticalLineToRelative(6f)
            horizontalLineTo(5f); lineToRelative(7f, 7f); lineToRelative(7f, -7f); close()
        }
    }

    val OpenInNew: ImageVector by lazy {
        icon("OpenInNew") {
            moveTo(19f, 19f); horizontalLineTo(5f); verticalLineTo(5f); horizontalLineToRelative(7f); verticalLineTo(3f); horizontalLineTo(5f)
            curveToRelative(-1.11f, 0f, -2f, 0.9f, -2f, 2f); verticalLineToRelative(14f); curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f)
            horizontalLineToRelative(14f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineToRelative(-7f); horizontalLineToRelative(-2f); verticalLineToRelative(7f); close()
            moveTo(14f, 3f); verticalLineToRelative(2f); horizontalLineToRelative(3.59f); lineToRelative(-9.83f, 9.83f); lineToRelative(1.41f, 1.41f)
            lineTo(19f, 6.41f); verticalLineTo(10f); horizontalLineToRelative(2f); verticalLineTo(3f); horizontalLineToRelative(-7f); close()
        }
    }

    val Block: ImageVector by lazy {
        icon("Block") {
            moveTo(12f, 2f); curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f); reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
            reflectiveCurveToRelative(10f, -4.48f, 10f, -10f); reflectiveCurveTo(17.52f, 2f, 12f, 2f); close()
            moveTo(4f, 12f); curveToRelative(0f, -4.42f, 3.58f, -8f, 8f, -8f); curveToRelative(1.85f, 0f, 3.55f, 0.63f, 4.9f, 1.69f)
            lineTo(5.69f, 16.9f); curveTo(4.63f, 15.55f, 4f, 13.85f, 4f, 12f); close()
            moveTo(12f, 20f); curveToRelative(-1.85f, 0f, -3.55f, -0.63f, -4.9f, -1.69f)
            lineTo(18.31f, 7.1f); curveTo(19.37f, 8.45f, 20f, 10.15f, 20f, 12f); curveToRelative(0f, 4.42f, -3.58f, 8f, -8f, 8f); close()
        }
    }

    val Tune: ImageVector by lazy {
        icon("Tune") {
            moveTo(3f, 17f); verticalLineToRelative(2f); horizontalLineToRelative(6f); verticalLineToRelative(-2f); horizontalLineTo(3f); close()
            moveTo(3f, 5f); verticalLineToRelative(2f); horizontalLineToRelative(10f); verticalLineTo(5f); horizontalLineTo(3f); close()
            moveTo(13f, 21f); verticalLineToRelative(-2f); horizontalLineToRelative(8f); verticalLineToRelative(-2f); horizontalLineToRelative(-8f)
            verticalLineToRelative(-2f); horizontalLineToRelative(-2f); verticalLineToRelative(6f); horizontalLineToRelative(2f); close()
            moveTo(7f, 9f); verticalLineToRelative(2f); horizontalLineTo(3f); verticalLineToRelative(2f); horizontalLineToRelative(4f)
            verticalLineToRelative(2f); horizontalLineToRelative(2f); verticalLineTo(9f); horizontalLineTo(7f); close()
            moveTo(21f, 13f); verticalLineToRelative(-2f); horizontalLineTo(11f); verticalLineToRelative(2f); horizontalLineToRelative(10f); close()
            moveTo(15f, 9f); horizontalLineToRelative(2f); verticalLineTo(7f); horizontalLineToRelative(4f); verticalLineTo(5f); horizontalLineToRelative(-4f)
            verticalLineTo(3f); horizontalLineToRelative(-2f); verticalLineToRelative(6f); close()
        }
    }

    val VolumeUp: ImageVector by lazy {
        icon("VolumeUp") {
            moveTo(3f, 9f); verticalLineToRelative(6f); horizontalLineToRelative(4f); lineToRelative(5f, 5f); verticalLineTo(4f); lineTo(7f, 9f); horizontalLineTo(3f); close()
            moveTo(16.5f, 12f); curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f); verticalLineToRelative(8.05f)
            curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f); close()
            moveTo(14f, 3.23f); verticalLineToRelative(2.06f); curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.71f)
            reflectiveCurveToRelative(-2.11f, 5.85f, -5f, 6.71f); verticalLineToRelative(2.06f)
            curveToRelative(4.01f, -0.91f, 7f, -4.49f, 7f, -8.77f); reflectiveCurveToRelative(-2.99f, -7.86f, -7f, -8.77f); close()
        }
    }

    val QuickTile: ImageVector by lazy {
        icon("QuickTile") {
            moveTo(18f, 1.01f); lineTo(8f, 1f); curveTo(6.9f, 1f, 6f, 1.9f, 6f, 3f); verticalLineToRelative(3f); horizontalLineToRelative(2f)
            verticalLineTo(5f); horizontalLineToRelative(10f); verticalLineToRelative(14f); horizontalLineTo(8f); verticalLineToRelative(-1f)
            horizontalLineTo(6f); verticalLineToRelative(3f); curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f); horizontalLineToRelative(10f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f); verticalLineTo(3f); curveToRelative(0f, -1.1f, -0.9f, -1.99f, -2f, -1.99f); close()
            moveTo(10f, 15f); horizontalLineToRelative(2f); verticalLineTo(8f); horizontalLineTo(5f); verticalLineToRelative(2f); horizontalLineToRelative(3.59f)
            lineTo(3f, 15.59f); lineTo(4.41f, 17f); lineTo(10f, 11.41f); verticalLineTo(15f); close()
        }
    }

    val Code: ImageVector by lazy {
        icon("Code") {
            moveTo(9.4f, 16.6f); lineTo(4.8f, 12f); lineToRelative(4.6f, -4.6f); lineTo(8f, 6f); lineToRelative(-6f, 6f); lineToRelative(6f, 6f); lineToRelative(1.4f, -1.4f); close()
            moveTo(14.6f, 16.6f); lineToRelative(4.6f, -4.6f); lineToRelative(-4.6f, -4.6f); lineTo(16f, 6f); lineToRelative(6f, 6f); lineToRelative(-6f, 6f); lineToRelative(-1.4f, -1.4f); close()
        }
    }

    val History: ImageVector by lazy {
        icon("History") {
            moveTo(13f, 3f); curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f); horizontalLineTo(1f); lineToRelative(3.89f, 3.89f)
            lineToRelative(0.07f, 0.14f); lineTo(9f, 12f); horizontalLineTo(6f); curveToRelative(0f, -3.87f, 3.13f, -7f, 7f, -7f)
            reflectiveCurveToRelative(7f, 3.13f, 7f, 7f); reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
            curveToRelative(-1.93f, 0f, -3.68f, -0.79f, -4.94f, -2.06f); lineToRelative(-1.42f, 1.42f)
            curveTo(8.27f, 19.99f, 10.51f, 21f, 13f, 21f); curveToRelative(4.97f, 0f, 9f, -4.03f, 9f, -9f); reflectiveCurveToRelative(-4.03f, -9f, -9f, -9f); close()
            moveTo(12f, 8f); verticalLineToRelative(5f); lineToRelative(4.28f, 2.54f); lineToRelative(0.72f, -1.21f); lineToRelative(-3.5f, -2.08f); verticalLineTo(8f); horizontalLineTo(12f); close()
        }
    }

    val Power: ImageVector by lazy {
        icon("Power") {
            moveTo(13f, 3f); horizontalLineToRelative(-2f); verticalLineToRelative(10f); horizontalLineToRelative(2f); verticalLineTo(3f); close()
            moveTo(17.83f, 5.17f); lineToRelative(-1.42f, 1.42f); curveTo(17.99f, 7.86f, 19f, 9.81f, 19f, 12f)
            curveToRelative(0f, 3.87f, -3.13f, 7f, -7f, 7f); reflectiveCurveToRelative(-7f, -3.13f, -7f, -7f)
            curveToRelative(0f, -2.19f, 1.01f, -4.14f, 2.58f, -5.42f); lineTo(6.17f, 5.17f)
            curveTo(4.23f, 6.82f, 3f, 9.26f, 3f, 12f); curveToRelative(0f, 4.97f, 4.03f, 9f, 9f, 9f)
            reflectiveCurveToRelative(9f, -4.03f, 9f, -9f); curveToRelative(0f, -2.74f, -1.23f, -5.18f, -3.17f, -6.83f); close()
        }
    }

    val Save: ImageVector by lazy {
        icon("Save") {
            moveTo(17f, 3f); horizontalLineTo(5f); curveTo(3.89f, 3f, 3f, 3.9f, 3f, 5f); verticalLineToRelative(14f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f); horizontalLineToRelative(14f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(7f); lineToRelative(-4f, -4f); close()
            moveTo(12f, 19f); curveToRelative(-1.66f, 0f, -3f, -1.34f, -3f, -3f); reflectiveCurveToRelative(1.34f, -3f, 3f, -3f)
            reflectiveCurveToRelative(3f, 1.34f, 3f, 3f); reflectiveCurveToRelative(-1.34f, 3f, -3f, 3f); close()
            moveTo(15f, 9f); horizontalLineTo(5f); verticalLineTo(5f); horizontalLineToRelative(10f); verticalLineToRelative(4f); close()
        }
    }

    val Sparkles: ImageVector by lazy {
        icon("Sparkles") {
            moveTo(19f, 9f); lineToRelative(1.25f, -2.75f); lineTo(23f, 5f); lineToRelative(-2.75f, -1.25f); lineTo(19f, 1f)
            lineToRelative(-1.25f, 2.75f); lineTo(15f, 5f); lineToRelative(2.75f, 1.25f); lineTo(19f, 9f); close()
            moveTo(11.5f, 9.5f); lineTo(9f, 4f); lineTo(6.5f, 9.5f); lineTo(1f, 12f); lineToRelative(5.5f, 2.5f)
            lineTo(9f, 20f); lineToRelative(2.5f, -5.5f); lineTo(17f, 12f); lineToRelative(-5.5f, -2.5f); close()
            moveTo(19f, 15f); lineToRelative(-1.25f, 2.75f); lineTo(15f, 19f); lineToRelative(2.75f, 1.25f)
            lineTo(19f, 23f); lineToRelative(1.25f, -2.75f); lineTo(23f, 19f); lineToRelative(-2.75f, -1.25f); lineTo(19f, 15f); close()
        }
    }

    val SwapHoriz: ImageVector by lazy {
        icon("SwapHoriz") {
            moveTo(6.99f, 11f); lineTo(3f, 15f); lineToRelative(3.99f, 4f); verticalLineToRelative(-3f); horizontalLineTo(14f)
            verticalLineToRelative(-2f); horizontalLineTo(6.99f); verticalLineToRelative(-3f); close()
            moveTo(21f, 9f); lineToRelative(-3.99f, -4f); verticalLineToRelative(3f); horizontalLineTo(10f)
            verticalLineToRelative(2f); horizontalLineToRelative(7.01f); verticalLineToRelative(3f); lineTo(21f, 9f); close()
        }
    }

    val Description: ImageVector by lazy {
        icon("Description") {
            moveTo(14f, 2f); horizontalLineTo(6f); curveTo(4.9f, 2f, 4.01f, 2.9f, 4.01f, 4f); lineTo(4f, 20f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f); horizontalLineTo(18f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(8f); lineToRelative(-6f, -6f); close()
            moveTo(16f, 18f); horizontalLineTo(8f); verticalLineToRelative(-2f); horizontalLineToRelative(8f); verticalLineToRelative(2f); close()
            moveTo(16f, 14f); horizontalLineTo(8f); verticalLineToRelative(-2f); horizontalLineToRelative(8f); verticalLineToRelative(2f); close()
            moveTo(13f, 9f); verticalLineTo(3.5f); lineTo(18.5f, 9f); horizontalLineTo(13f); close()
        }
    }

    val Camera: ImageVector by lazy {
        icon("Camera") {
            moveTo(12f, 15.2f); curveToRelative(1.77f, 0f, 3.2f, -1.43f, 3.2f, -3.2f); reflectiveCurveToRelative(-1.43f, -3.2f, -3.2f, -3.2f)
            reflectiveCurveToRelative(-3.2f, 1.43f, -3.2f, 3.2f); reflectiveCurveToRelative(1.43f, 3.2f, 3.2f, 3.2f); close()
            moveTo(9f, 2f); lineTo(7.17f, 4f); horizontalLineTo(4f); curveTo(2.9f, 4f, 2f, 4.9f, 2f, 6f); verticalLineToRelative(12f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f); horizontalLineToRelative(16f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(6f); curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f); horizontalLineToRelative(-3.17f); lineTo(15f, 2f); horizontalLineTo(9f); close()
            moveTo(12f, 17f); curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f); reflectiveCurveToRelative(2.24f, -5f, 5f, -5f)
            reflectiveCurveToRelative(5f, 2.24f, 5f, 5f); reflectiveCurveToRelative(-2.24f, 5f, -5f, 5f); close()
        }
    }

    val Thermostat: ImageVector by lazy {
        icon("Thermostat") {
            moveTo(15f, 13f); verticalLineTo(5f); curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f); reflectiveCurveTo(9f, 3.34f, 9f, 5f)
            verticalLineToRelative(8f); curveToRelative(-1.21f, 0.91f, -2f, 2.37f, -2f, 4f); curveToRelative(0f, 2.76f, 2.24f, 5f, 5f, 5f)
            reflectiveCurveToRelative(5f, -2.24f, 5f, -5f); curveToRelative(0f, -1.63f, -0.79f, -3.09f, -2f, -4f); close()
            moveTo(11f, 11f); verticalLineTo(5f); curveToRelative(0f, -0.55f, 0.45f, -1f, 1f, -1f); reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
            verticalLineToRelative(6f); horizontalLineToRelative(-2f); close()
        }
    }

    val Terminal: ImageVector by lazy {
        icon("Terminal") {
            moveTo(20f, 4f); horizontalLineTo(4f); curveTo(2.89f, 4f, 2f, 4.9f, 2f, 6f); verticalLineToRelative(12f)
            curveToRelative(0f, 1.1f, 0.89f, 2f, 2f, 2f); horizontalLineToRelative(16f); curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(6f); curveToRelative(0f, -1.1f, -0.89f, -2f, -2f, -2f); close()
            moveTo(20f, 18f); horizontalLineTo(4f); verticalLineTo(8f); horizontalLineToRelative(16f); verticalLineToRelative(10f); close()
            moveTo(18f, 17f); horizontalLineToRelative(-6f); verticalLineToRelative(-2f); horizontalLineToRelative(6f); verticalLineToRelative(2f); close()
            moveTo(7.5f, 17f); lineToRelative(-1.41f, -1.41f); lineTo(8.67f, 13f); lineToRelative(-2.59f, -2.59f)
            lineTo(7.5f, 9f); lineToRelative(4f, 4f); lineToRelative(-4f, 4f); close()
        }
    }

    val Sync: ImageVector by lazy {
        icon("Sync") {
            moveTo(12f, 4f); verticalLineTo(1f); lineTo(8f, 5f); lineToRelative(4f, 4f); verticalLineTo(6f)
            curveToRelative(3.31f, 0f, 6f, 2.69f, 6f, 6f); curveToRelative(0f, 1.01f, -0.25f, 1.97f, -0.7f, 2.8f)
            lineToRelative(1.46f, 1.46f); curveTo(19.54f, 15.03f, 20f, 13.57f, 20f, 12f)
            curveToRelative(0f, -4.42f, -3.58f, -8f, -8f, -8f); close()
            moveTo(12f, 18f); curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f); curveToRelative(0f, -1.01f, 0.25f, -1.97f, 0.7f, -2.8f)
            lineTo(5.24f, 7.74f); curveTo(4.46f, 8.97f, 4f, 10.43f, 4f, 12f); curveToRelative(0f, 4.42f, 3.58f, 8f, 8f, 8f)
            verticalLineToRelative(3f); lineToRelative(4f, -4f); lineToRelative(-4f, -4f); verticalLineToRelative(3f); close()
        }
    }

    val ArrowDropDown: ImageVector by lazy {
        icon("ArrowDropDown") {
            moveTo(7f, 10f); lineToRelative(5f, 5f); lineToRelative(5f, -5f); close()
        }
    }

    val ArrowDropUp: ImageVector by lazy {
        icon("ArrowDropUp") {
            moveTo(7f, 14f); lineToRelative(5f, -5f); lineToRelative(5f, 5f); close()
        }
    }
}
