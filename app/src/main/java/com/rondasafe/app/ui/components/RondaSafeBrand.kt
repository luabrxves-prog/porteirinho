package com.rondasafe.app.ui.components

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
    buildingColor: Color = Color.White,
    accentColor: Color = RondaSafeColors.Blue,
) {
    RondaSafeAppLogoImage(modifier = modifier.size(104.dp))
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
