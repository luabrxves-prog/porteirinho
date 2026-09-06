package com.rondasafe.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object RondaSafeColors {
    val Navy = Color(0xFF062B46)
    val NavyDark = Color(0xFF031E32)
    val Blue = Color(0xFF168AF3)
    val BlueSoft = Color(0xFFEAF4FF)
    val Green = Color(0xFF1C9B69)
    val GreenSoft = Color(0xFFE8F7F0)
    val Background = Color(0xFFF5F7FA)
    val Surface = Color(0xFFFFFFFF)
    val Text = Color(0xFF12202E)
    val Muted = Color(0xFF687684)
    val Border = Color(0xFFE1E7ED)
    val Danger = Color(0xFFD94A4A)
}

@Composable
fun RondaSafeMark(
    modifier: Modifier = Modifier,
    shieldColor: Color = RondaSafeColors.Blue,
    buildingColor: Color = Color.White,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = size.minDimension * 0.055f

        val shield = Path().apply {
            moveTo(w * 0.50f, h * 0.05f)
            lineTo(w * 0.86f, h * 0.18f)
            lineTo(w * 0.80f, h * 0.61f)
            quadraticBezierTo(w * 0.75f, h * 0.76f, w * 0.50f, h * 0.91f)
            quadraticBezierTo(w * 0.25f, h * 0.76f, w * 0.20f, h * 0.61f)
            lineTo(w * 0.14f, h * 0.18f)
            close()
        }
        drawPath(shield, color = shieldColor.copy(alpha = 0.16f))
        drawPath(shield, color = shieldColor, style = Stroke(width = stroke))

        val roof = Path().apply {
            moveTo(w * 0.31f, h * 0.42f)
            lineTo(w * 0.50f, h * 0.29f)
            lineTo(w * 0.69f, h * 0.42f)
        }
        drawPath(roof, color = buildingColor, style = Stroke(width = stroke * 0.75f))

        drawRect(
            color = buildingColor,
            topLeft = Offset(w * 0.34f, h * 0.42f),
            size = Size(w * 0.32f, h * 0.28f),
            style = Stroke(width = stroke * 0.7f),
        )

        val windowSize = Size(w * 0.055f, h * 0.055f)
        listOf(
            Offset(w * 0.40f, h * 0.49f),
            Offset(w * 0.545f, h * 0.49f),
            Offset(w * 0.40f, h * 0.59f),
            Offset(w * 0.545f, h * 0.59f),
        ).forEach { drawRect(buildingColor, it, windowSize) }

        drawRect(
            color = buildingColor,
            topLeft = Offset(w * 0.465f, h * 0.625f),
            size = Size(w * 0.07f, h * 0.075f),
        )
    }
}

@Composable
fun RondaSafeBrand(
    modifier: Modifier = Modifier,
    darkBackground: Boolean = false,
    showTagline: Boolean = true,
) {
    val textColor = if (darkBackground) Color.White else RondaSafeColors.Navy
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        RondaSafeMark()
        Spacer(Modifier.height(14.dp))
        Text(
            text = "RondaSafe",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
        if (showTagline) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "SEGURANÇA EM CADA PASSO",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.68f),
            )
        }
    }
}
