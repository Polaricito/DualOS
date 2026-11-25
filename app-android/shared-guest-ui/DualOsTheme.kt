package com.fahadali.oneos.shared

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class DualOsStatusColors(
    val ok: Color,
    val okContainer: Color,
    val onOkContainer: Color,
    val warn: Color,
    val warnContainer: Color,
    val onWarnContainer: Color,
    val block: Color,
    val blockContainer: Color,
    val onBlockContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,
)

private val LightStatusColors = DualOsStatusColors(
    ok = Color(0xFF0E7F4D),
    okContainer = Color(0xFFD6F5E5),
    onOkContainer = Color(0xFF063922),
    warn = Color(0xFFB4460F),
    warnContainer = Color(0xFFFFE5D4),
    onWarnContainer = Color(0xFF4A1900),
    block = Color(0xFFC42B22),
    blockContainer = Color(0xFFFFDAD6),
    onBlockContainer = Color(0xFF410002),
    info = Color(0xFF1C5FD6),
    infoContainer = Color(0xFFD8E4FF),
    onInfoContainer = Color(0xFF001A43),
)

private val DarkStatusColors = DualOsStatusColors(
    ok = Color(0xFF6DD9A3),
    okContainer = Color(0xFF0A3D25),
    onOkContainer = Color(0xFFD6F5E5),
    warn = Color(0xFFFFB68B),
    warnContainer = Color(0xFF4E2307),
    onWarnContainer = Color(0xFFFFE5D4),
    block = Color(0xFFFFB4AB),
    blockContainer = Color(0xFF5C110C),
    onBlockContainer = Color(0xFFFFDAD6),
    info = Color(0xFFAFC6FF),
    infoContainer = Color(0xFF0E2E6E),
    onInfoContainer = Color(0xFFD8E4FF),
)

val LocalDualOsStatusColors = staticCompositionLocalOf { LightStatusColors }

private val FallbackLightColorScheme = lightColorScheme(
    primary = Color(0xFF1C5FD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8E4FF),
    onPrimaryContainer = Color(0xFF001B47),
    secondary = Color(0xFF4A5D82),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE4F8),
    onSecondaryContainer = Color(0xFF0E1D38),
    tertiary = Color(0xFF5B3FCB),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6DEFF),
    onTertiaryContainer = Color(0xFF1A0062),
    surface = Color(0xFFF7F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F4FC),
    surfaceContainer = Color(0xFFE9EFFA),
    surfaceContainerHigh = Color(0xFFE2E8F5),
    surfaceContainerHighest = Color(0xFFDCE3F0),
)

private val FallbackDarkColorScheme = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF002E6E),
    primaryContainer = Color(0xFF0B44A8),
    onPrimaryContainer = Color(0xFFD8E4FF),
    secondary = Color(0xFFB5C7E8),
    onSecondary = Color(0xFF1E304E),
    secondaryContainer = Color(0xFF354666),
    onSecondaryContainer = Color(0xFFDCE4F8),
    tertiary = Color(0xFFCABEFF),
    onTertiary = Color(0xFF2D0A93),
    tertiaryContainer = Color(0xFF4326B2),
    onTertiaryContainer = Color(0xFFE6DEFF),
    surface = Color(0xFF0F131C),
    surfaceContainerLowest = Color(0xFF0A0E16),
    surfaceContainerLow = Color(0xFF171C27),
    surfaceContainer = Color(0xFF1D2330),
    surfaceContainerHigh = Color(0xFF262D3C),
    surfaceContainerHighest = Color(0xFF303849),
)

private val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val ExpressiveTypography = Typography(
    displaySmall = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.1.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DualOsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> FallbackDarkColorScheme
        else -> FallbackLightColorScheme
    }
    val statusColors = if (darkTheme) DarkStatusColors else LightStatusColors
    CompositionLocalProvider(LocalDualOsStatusColors provides statusColors) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = ExpressiveShapes,
            typography = ExpressiveTypography,
            content = content,
        )
    }
}
