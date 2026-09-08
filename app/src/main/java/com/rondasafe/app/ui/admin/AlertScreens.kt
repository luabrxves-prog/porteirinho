package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rondasafe.app.AppTime
import com.rondasafe.app.data.model.AlertDto
import com.rondasafe.app.presentation.admin.viewmodel.AlertsViewModel
import com.rondasafe.app.ui.components.*

@Composable
fun AlertsScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
    viewModel: AlertsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val showResolved = state.resolution == "RESOLVED"

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Alertas", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeading(
                    "Situações que precisam de atenção",
                    "Consulte somente o período necessário e refine por situação e severidade.",
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("PENDING" to "Pendentes", "RESOLVED" to "Resolvidos").forEach { (value, label) ->
                        FilterChip(
                            selected = state.resolution == value,
                            onClick = { viewModel.setResolution(value) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 30, 90).forEach { days ->
                        FilterChip(
                            selected = state.days == days,
                            onClick = { viewModel.setDays(days) },
                            label = { Text("$days dias") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to "Todas", "CRITICAL" to "Crítico", "WARNING" to "Atenção").forEach { (value, label) ->
                        FilterChip(
                            selected = state.severity == value,
                            onClick = { viewModel.setSeverity(value) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!state.loading && state.items.isEmpty()) {
                item {
                    EmptyStateCard(
                        if (showResolved) "Nenhum alerta resolvido" else "Tudo em ordem",
                        "Nenhum alerta foi encontrado para os filtros e período selecionados.",
                        Icons.Rounded.NotificationsActive,
                    )
                }
            }
            items(state.items, key = { it.id }) { alert ->
                AlertCard(
                    alert = alert,
                    showResolved = showResolved,
                    onOpen = { if (alert.readAt == null) viewModel.markRead(alert.id) },
                    onOpenHistory = onOpenHistory,
                    onResolve = { viewModel.resolve(alert.id) },
                )
            }
            if (state.hasMore) {
                item {
                    OutlinedButton(
                        onClick = viewModel::loadMore,
                        enabled = !state.loadingMore,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.loadingMore) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Carregar mais alertas")
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun AlertCard(
    alert: AlertDto,
    showResolved: Boolean,
    onOpen: () -> Unit,
    onOpenHistory: () -> Unit,
    onResolve: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val severityColor = when (alert.severity) {
        "CRITICAL" -> RondaSafeColors.Danger
        "WARNING" -> Color(0xFFE29019)
        else -> RondaSafeColors.Blue
    }
    Card(
        onClick = { expanded = !expanded; onOpen() },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, RondaSafeColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(alert.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
            Text(AppTime.dateTime(alert.createdAt), style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
            Surface(shape = RoundedCornerShape(50), color = severityColor.copy(alpha = .12f)) {
                Text(
                    if (showResolved) "Resolvido" else alertTypeLabel(alert.alertType),
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = severityColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (alert.readAt == null && !showResolved) {
                Text("Novo", style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Blue, fontWeight = FontWeight.Bold)
            }
            if (expanded) {
                HorizontalDivider(color = RondaSafeColors.Border)
                Text(alert.message, style = MaterialTheme.typography.bodyMedium)
                if (alert.patrolRunId != null) {
                    OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) { Text("Ver ronda") }
                }
                if (!showResolved) {
                    Button(onClick = onResolve, modifier = Modifier.fillMaxWidth()) { Text("Marcar como resolvido") }
                }
            } else {
                Text("Toque para ver detalhes", style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Blue)
            }
        }
    }
}

private fun alertTypeLabel(type: String): String = when (type) {
    "PATROL_NOT_STARTED" -> "Ronda não realizada"
    "PATROL_LATE" -> "Ronda atrasada"
    "PATROL_INCOMPLETE" -> "Ronda incompleta"
    "PATROL_TOO_FAST" -> "Ronda rápida demais"
    "GUARD_OCCURRENCE" -> "Ocorrência informada"
    "SUSPICIOUS_SCAN" -> "Atividade para revisar"
    "DEVICE_SYNC_STALE" -> "Portaria sem sincronização"
    "INVALID_ACCESS" -> "Acesso inválido"
    else -> "Atenção"
}
