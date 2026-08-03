package com.loomytrip.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LoomyTripColors = lightColorScheme(
    primary = Color(0xFF0A8374),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF3EE),
    onPrimaryContainer = Color(0xFF075A51),
    secondary = Color(0xFFF19A2A),
    onSecondary = Color(0xFF3A2500),
    secondaryContainer = Color(0xFFFFE9C5),
    onSecondaryContainer = Color(0xFF704500),
    tertiary = Color(0xFF173A56),
    onTertiary = Color.White,
    background = Color(0xFFFBF8F1),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEAF3F0),
    onBackground = Color(0xFF17324B),
    onSurface = Color(0xFF17324B),
    outline = Color(0xFFC2D0CB),
    outlineVariant = Color(0xFFE2E9E6),
    error = Color(0xFFB93632)
)

private val LoomyTripTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 31.sp,
        lineHeight = 36.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 31.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    )
)

private val LoomyTripShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp)
)

@Composable
fun LoomyTripTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LoomyTripColors,
        typography = LoomyTripTypography,
        shapes = LoomyTripShapes,
        content = content
    )
}
