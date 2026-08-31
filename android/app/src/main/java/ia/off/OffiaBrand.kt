package ia.off

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val OffiaNavy = Color(0xFF082C67)
val OffiaDeep = Color(0xFF071A3C)
val OffiaBlue = Color(0xFF0B67B5)
val OffiaCyan = Color(0xFF13D8E5)
val OffiaCyanSoft = Color(0xFF16D8E4)
val OffiaTextMuted = Color(0xFF263552)

private val OffiaLightColors = lightColorScheme(
    primary = OffiaBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F4FA),
    onPrimaryContainer = OffiaDeep,
    secondary = OffiaNavy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EEF8),
    onSecondaryContainer = OffiaDeep,
    tertiary = Color(0xFF0E8CC8),
    surface = Color(0xFFFBFCFE),
    surfaceVariant = Color(0xFFF2F6FA),
    background = Color(0xFFFBFCFE),
    onBackground = OffiaDeep,
    onSurface = OffiaDeep,
    outline = Color(0xFF8A98AC),
)

private val OffiaDarkColors = darkColorScheme(
    primary = OffiaCyan,
    onPrimary = Color(0xFF002C33),
    primaryContainer = Color(0xFF084C72),
    onPrimaryContainer = Color(0xFFD8F8FC),
    secondary = Color(0xFF7FB8E7),
    onSecondary = Color(0xFF06265F),
    tertiary = OffiaCyanSoft,
    surface = Color(0xFF07111F),
    surfaceVariant = Color(0xFF102033),
    background = Color(0xFF07111F),
    onBackground = Color(0xFFE8F2FC),
    onSurface = Color(0xFFE8F2FC),
    outline = Color(0xFF8294AA),
)

private val OffiaTypography = Typography(
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
)

@Composable
fun OffiaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) OffiaDarkColors else OffiaLightColors,
        typography = OffiaTypography,
        content = content,
    )
}

/**
 * Compact Compose rendering derived from assets/branding/offia-symbol.svg.
 * It uses the canonical OFF.IA ring/core palette and is intended for UI chrome,
 * while the repository SVG/PNG files remain the canonical brand masters.
 */
@Composable
fun OffiaOrbitalSymbol(
    modifier: Modifier = Modifier.size(34.dp),
) {
    Canvas(modifier = modifier) {
        val diameter = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)
        val ringBrush = Brush.linearGradient(
            colors = listOf(OffiaNavy, OffiaBlue, OffiaCyan),
            start = Offset(0f, diameter),
            end = Offset(diameter, 0f),
        )
        val outer = diameter * 0.78f
        val inner = diameter * 0.47f
        val outerTopLeft = Offset(center.x - outer / 2f, center.y - outer / 2f)
        val innerTopLeft = Offset(center.x - inner / 2f, center.y - inner / 2f)

        drawArc(
            brush = ringBrush,
            startAngle = -42f,
            sweepAngle = 228f,
            useCenter = false,
            topLeft = outerTopLeft,
            size = Size(outer, outer),
            style = Stroke(width = diameter * 0.065f, cap = StrokeCap.Round),
        )
        drawArc(
            brush = ringBrush,
            startAngle = 210f,
            sweepAngle = 72f,
            useCenter = false,
            topLeft = outerTopLeft,
            size = Size(outer, outer),
            style = Stroke(width = diameter * 0.065f, cap = StrokeCap.Round),
        )
        drawArc(
            brush = ringBrush,
            startAngle = -38f,
            sweepAngle = 245f,
            useCenter = false,
            topLeft = innerTopLeft,
            size = Size(inner, inner),
            style = Stroke(width = diameter * 0.04f, cap = StrokeCap.Round),
        )
        drawCircle(
            brush = Brush.radialGradient(listOf(OffiaCyanSoft, Color(0xFF0879BF), Color(0xFF06265F))),
            radius = diameter * 0.06f,
            center = center,
        )
        drawCircle(color = OffiaCyan, radius = diameter * 0.015f, center = Offset(center.x + diameter * 0.29f, center.y - diameter * 0.16f))
        drawCircle(color = OffiaCyan, radius = diameter * 0.012f, center = Offset(center.x + diameter * 0.33f, center.y - diameter * 0.20f))
        drawCircle(color = OffiaNavy, radius = diameter * 0.015f, center = Offset(center.x - diameter * 0.28f, center.y + diameter * 0.21f))
    }
}
