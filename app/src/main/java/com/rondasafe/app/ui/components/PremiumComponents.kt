package com.rondasafe.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

object RondaSafeUi {
    val ScreenPadding = 18.dp
    val CardRadius = 22.dp
    val SmallRadius = 14.dp
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
                color = RondaSafeColors.Text,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Rounded.ArrowBackIosNew, contentDescription = "Voltar", tint = RondaSafeColors.Navy)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White,
            titleContentColor = RondaSafeColors.Text,
        ),
    )
}

@Composable
fun SkylineIllustration(
    modifier: Modifier = Modifier,
    light: Boolean = false,
) {
    val base = if (light) Color(0xFFCFE8FF) else Color(0xFF0B3550)
    val front = if (light) Color(0xFF8CC7F7) else Color(0xFF0E4267)
    val window = if (light) Color.White else Color(0xFFFFD77B)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun tower(left: Float, top: Float, right: Float, bottom: Float, color: Color, cols: Int, rows: Int) {
            drawRoundRect(
                color = color,
                topLeft = Offset(w * left, h * top),
                size = Size(w * (right - left), h * (bottom - top)),
                cornerRadius = CornerRadius(6f, 6f),
            )
            val bw = w * (right - left)
            val bh = h * (bottom - top)
            val marginX = bw * .16f
            val marginY = bh * .12f
            val cellW = (bw - marginX * 2) / cols
            val cellH = (bh - marginY * 2) / rows
            repeat(rows) { r ->
                repeat(cols) { c ->
                    val ww = cellW * .32f
                    val wh = cellH * .30f
                    drawRoundRect(
                        color = window.copy(alpha = if ((r + c) % 3 == 0) .95f else .42f),
                        topLeft = Offset(
                            w * left + marginX + c * cellW + (cellW - ww) / 2,
                            h * top + marginY + r * cellH + (cellH - wh) / 2,
                        ),
                        size = Size(ww, wh),
                        cornerRadius = CornerRadius(2f, 2f),
                    )
                }
            }
        }

        tower(.02f, .46f, .23f, 1f, base, 2, 6)
        tower(.20f, .25f, .46f, 1f, front, 3, 8)
        tower(.43f, .08f, .70f, 1f, base, 3, 10)
        tower(.67f, .34f, .88f, 1f, front, 2, 7)
        tower(.84f, .52f, 1f, 1f, base, 2, 5)
    }
}

@Composable
fun PremiumHeroCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(
                brush = Brush.linearGradient(
                    listOf(RondaSafeColors.Navy, Color(0xFF0B4B78)),
                ),
                shape = RoundedCornerShape(26.dp),
            ),
    ) {
        SkylineIllustration(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxWidth(.54f)
                .height(118.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp, end = 150.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = .76f),
                maxLines = 2,
            )
        }
    }
}

@Composable
fun PremiumMetricCard(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, RondaSafeColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(13.dp),
                color = RondaSafeColors.BlueSoft,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = RondaSafeColors.Blue, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                Text(label, style = MaterialTheme.typography.labelMedium, color = RondaSafeColors.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
fun PremiumMenuRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, RondaSafeColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = if (danger) Color(0xFFFFEEEE) else RondaSafeColors.Navy,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (danger) RondaSafeColors.Danger else Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RondaSafeColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = RondaSafeColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("›", style = MaterialTheme.typography.headlineSmall, color = RondaSafeColors.Muted)
        }
    }
}

@Composable
fun SectionHeading(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = RondaSafeColors.Navy,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RondaSafeColors.Muted)
        }
    }
}

@Composable
fun EmptyStateCard(
    title: String,
    message: String,
    icon: ImageVector,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, RondaSafeColors.Border),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(shape = RoundedCornerShape(16.dp), color = RondaSafeColors.BlueSoft) {
                Icon(icon, null, tint = RondaSafeColors.Blue, modifier = Modifier.padding(12.dp).size(28.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
            Spacer(Modifier.height(4.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
        }
    }
}
