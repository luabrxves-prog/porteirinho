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
    shieldColor: Color = RondaSafeColors.Blue,
    buildingColor: Color = Color.White,
) {
    Canvas(modifier = modifier.size(112.dp)) {
        val w = size.width
        val h = size.height
        val radius = size.minDimension * 0.22f

        drawRoundRect(
            color = RondaSafeColors.NavyDark,
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
        )
        drawRoundRect(
            color = shieldColor.copy(alpha = 0.20f),
            topLeft = Offset(w * 0.06f, h * 0.06f),
            size = Size(w * 0.88f, h * 0.88f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * 0.78f, radius * 0.78f),
        )

        fun building(left: Float, top: Float, right: Float, bottom: Float, columns: Int, rows: Int) {
            drawRect(
                color = buildingColor,
                topLeft = Offset(w * left, h * top),
                size = Size(w * (right - left), h * (bottom - top)),
            )
            val bw = w * (right - left)
            val bh = h * (bottom - top)
            val marginX = bw * 0.16f
            val marginY = bh * 0.10f
            val cellW = (bw - marginX * 2) / columns
            val cellH = (bh - marginY * 2) / rows
            for (r in 0 until rows) {
                for (c in 0 until columns) {
                    val windowW = cellW * 0.45f
                    val windowH = cellH * 0.42f
                    val x = w * left + marginX + c * cellW + (cellW - windowW) / 2
                    val y = h * top + marginY + r * cellH + (cellH - windowH) / 2
                    drawRect(
                        color = RondaSafeColors.NavyDark,
                        topLeft = Offset(x, y),
                        size = Size(windowW, windowH),
                    )
                }
            }
        }

        building(0.15f, 0.43f, 0.37f, 0.82f, columns = 2, rows = 5)
        building(0.38f, 0.20f, 0.66f, 0.82f, columns = 3, rows = 8)
        building(0.68f, 0.40f, 0.88f, 0.82f, columns = 2, rows = 5)

        drawRect(
            color = buildingColor,
            topLeft = Offset(w * 0.45f, h * 0.14f),
            size = Size(w * 0.14f, h * 0.06f),
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
                text = "SEGURANÇA QUE MANTÉM O CONDOMÍNIO EM MOVIMENTO",
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
