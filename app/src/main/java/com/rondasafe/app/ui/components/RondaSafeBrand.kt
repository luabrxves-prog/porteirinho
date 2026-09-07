package com.rondasafe.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    buildingColor: Color = Color.White,
    accentColor: Color = RondaSafeColors.Blue,
) {
    Canvas(modifier = modifier.size(104.dp)) {
        val w = size.width
        val h = size.height

        val body = Path().apply {
            moveTo(w * .28f, h * .38f)
            lineTo(w * .55f, h * .16f)
            lineTo(w * .76f, h * .28f)
            lineTo(w * .76f, h * .84f)
            lineTo(w * .28f, h * .84f)
            close()
        }
        drawPath(body, buildingColor)

        val leftFace = Path().apply {
            moveTo(w * .28f, h * .38f)
            lineTo(w * .40f, h * .31f)
            lineTo(w * .40f, h * .84f)
            lineTo(w * .28f, h * .84f)
            close()
        }
        drawPath(leftFace, buildingColor.copy(alpha = .72f))

        val rightFace = Path().apply {
            moveTo(w * .76f, h * .28f)
            lineTo(w * .84f, h * .33f)
            lineTo(w * .84f, h * .84f)
            lineTo(w * .76f, h * .84f)
            close()
        }
        drawPath(rightFace, accentColor.copy(alpha = .9f))

        val leftWing = Path().apply {
            moveTo(w * .10f, h * .64f)
            lineTo(w * .28f, h * .54f)
            lineTo(w * .28f, h * .84f)
            lineTo(w * .10f, h * .84f)
            close()
        }
        drawPath(leftWing, buildingColor.copy(alpha = .88f))

        val rightWing = Path().apply {
            moveTo(w * .84f, h * .60f)
            lineTo(w * .94f, h * .65f)
            lineTo(w * .94f, h * .84f)
            lineTo(w * .84f, h * .84f)
            close()
        }
        drawPath(rightWing, buildingColor.copy(alpha = .88f))

        val window = RondaSafeColors.NavyDark
        val rows = 5
        val cols = 2
        repeat(rows) { r ->
            repeat(cols) { c ->
                drawRect(
                    color = window,
                    topLeft = Offset(w * (.49f + c * .12f), h * (.31f + r * .105f)),
                    size = Size(w * .045f, h * .048f),
                )
            }
        }
        repeat(3) { r ->
            drawRect(
                color = RondaSafeColors.Navy,
                topLeft = Offset(w * .16f, h * (.66f + r * .065f)),
                size = Size(w * .04f, h * .035f),
            )
            drawRect(
                color = RondaSafeColors.Navy,
                topLeft = Offset(w * .88f, h * (.66f + r * .065f)),
                size = Size(w * .03f, h * .035f),
            )
        }
        drawRect(
            color = accentColor,
            topLeft = Offset(w * .57f, h * .73f),
            size = Size(w * .075f, h * .11f),
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
        Spacer(Modifier.height(12.dp))
        Text(
            text = "RondaSafe",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
        )
        if (showTagline) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "SEGURANÇA QUE ACOMPANHA CADA PASSO DA SUA RONDA",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.68f),
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}
