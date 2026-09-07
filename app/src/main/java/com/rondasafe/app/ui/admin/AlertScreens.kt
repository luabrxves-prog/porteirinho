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
import com.rondasafe.app.data.model.AlertDto
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun AlertsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var alerts by remember { mutableStateOf<List<AlertDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching { AdminRepository.listAlerts() }
                .onSuccess { alerts = it }
                .onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Alertas", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeading("Atenção à operação", "Atrasos, rondas incompletas, ocorrências e atividades suspeitas.") }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!loading && alerts.isEmpty()) {
                item { EmptyStateCard("Tudo em ordem", "Nenhum alerta ativo neste momento.", Icons.Rounded.NotificationsActive) }
            }
            items(alerts, key = { it.id }) { alert ->
                AlertCard(
                    alert = alert,
                    onOpen = {
                        if (alert.readAt == null) scope.launch { runCatching { AdminRepository.markAlertRead(alert.id) }; reload() }
                    },
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
private fun AlertCard(alert: AlertDto, onOpen: () -> Unit, onResolve: () -> Unit) {
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
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(alert.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                    Text(alertTypeLabel(alert.alertType), style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                }
                Surface(shape = RoundedCornerShape(50), color = severityColor.copy(alpha = .12f)) {
                    Text(alert.severity, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = severityColor, fontWeight = FontWeight.Bold)
                }
            }
            if (alert.readAt == null) Text("Novo", style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Blue, fontWeight = FontWeight.Bold)
            if (expanded) {
                HorizontalDivider(color = RondaSafeColors.Border)
                Text(alert.message, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = onResolve, modifier = Modifier.fillMaxWidth()) { Text("Marcar como resolvido") }
            } else {
                Text("Toque para ver detalhes", style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Blue)
            }
        }
    }
}

private fun alertTypeLabel(type: String): String = when (type) {
    "PATROL_NOT_STARTED" -> "Ronda não iniciada"
    "PATROL_LATE" -> "Ronda atrasada"
    "PATROL_INCOMPLETE" -> "Ronda incompleta"
    "PATROL_TOO_FAST" -> "Ronda rápida demais"
    "GUARD_OCCURRENCE" -> "Ocorrência informada pelo porteiro"
    "SUSPICIOUS_SCAN" -> "Leitura suspeita"
    "DEVICE_SYNC_STALE" -> "Aparelho sem sincronização"
    "INVALID_ACCESS" -> "Acesso inválido"
    else -> type
}
