package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.AppTime
import com.rondasafe.app.data.model.AlertDto
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun AlertsScreen(onBack: () -> Unit, onOpenHistory: () -> Unit) {
    val scope = rememberCoroutineScope()
    var alerts by remember { mutableStateOf<List<AlertDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showResolved by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching { AdminRepository.listAlerts(includeResolved = showResolved) }
                .onSuccess { loaded -> alerts = if (showResolved) loaded.filter { it.resolvedAt != null } else loaded }
                .onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(showResolved) { reload() }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Atenção", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeading(
                    "Situações que precisam de atenção",
                    "Veja o que aconteceu, quando aconteceu e consulte a ronda antes de concluir a análise.",
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showResolved,
                        onClick = { showResolved = false },
                        label = { Text("Pendentes") },
                        modifier = Modifier.weight(1f),
                    )
                    FilterChip(
                        selected = showResolved,
                        onClick = { showResolved = true },
                        label = { Text("Resolvidos") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!loading && alerts.isEmpty()) {
                item {
                    EmptyStateCard(
                        if (showResolved) "Nenhuma situação resolvida" else "Tudo em ordem",
                        if (showResolved) "As situações concluídas aparecerão aqui." else "Nada precisa de atenção neste momento.",
                        Icons.Rounded.WarningAmber,
                    )
                }
            }
            items(alerts, key = { it.id }) { alert ->
                AttentionCard(
                    alert = alert,
                    showResolved = showResolved,
                    onOpen = {
                        if (alert.readAt == null) scope.launch {
                            runCatching { AdminRepository.markAlertRead(alert.id) }
                            reload()
                        }
                    },
                    onOpenHistory = onOpenHistory,
                    onResolve = {
                        scope.launch {
                            runCatching { AdminRepository.resolveAlert(alert.id) }
                                .onSuccess { reload() }
                                .onFailure { error = it.message }
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun AttentionCard(
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
                    OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
                        Text("Ver ronda")
                    }
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
