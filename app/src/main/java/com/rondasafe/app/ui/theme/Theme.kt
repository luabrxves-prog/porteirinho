package com.rondasafe.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rondasafe.app.ui.components.RondaSafeColors

private val RondaSafeColorScheme = lightColorScheme(
    primary = RondaSafeColors.Blue,
    onPrimary = Color.White,
    primaryContainer = RondaSafeColors.BlueSoft,
    onPrimaryContainer = RondaSafeColors.Navy,
    secondary = RondaSafeColors.Navy,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8F0F6),
    onSecondaryContainer = RondaSafeColors.NavyDark,
    tertiary = RondaSafeColors.Green,
    onTertiary = Color.White,
    tertiaryContainer = RondaSafeColors.GreenSoft,
    onTertiaryContainer = Color(0xFF07563A),
    background = RondaSafeColors.Background,
    onBackground = RondaSafeColors.Text,
    surface = RondaSafeColors.Surface,
    onSurface = RondaSafeColors.Text,
    surfaceVariant = Color(0xFFF0F3F6),
    onSurfaceVariant = RondaSafeColors.Muted,
    outline = RondaSafeColors.Border,
    error = RondaSafeColors.Danger,
    onError = Color.White,
)

private val RondaSafeTypography = Typography(
    headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 23.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
)

private val RondaSafeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun RondaSafeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RondaSafeColorScheme,
        typography = RondaSafeTypography,
        shapes = RondaSafeShapes,
        content = content,
    )
}
