package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.AlertDto
import com.rondasafe.app.data.repository.AdminRepository
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

    Scaffold(topBar = { AppTopBar("Alertas", onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).fillMaxSize()) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!loading && alerts.isEmpty()) {
                Text("Nenhum alerta ativo.")
            }
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(alerts, key = { it.id }) { alert ->
                    AlertCard(
                        alert = alert,
                        onOpen = {
                            if (alert.readAt == null) {
                                scope.launch {
                                    runCatching { AdminRepository.markAlertRead(alert.id) }
                                    reload()
                                }
                            }
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
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: AlertDto,
    onOpen: () -> Unit,
    onResolve: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        onClick = {
            expanded = !expanded
            onOpen()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(alert.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AssistChip(
                    onClick = {},
                    label = { Text(alert.severity) },
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(alertTypeLabel(alert.alertType), style = MaterialTheme.typography.labelMedium)
            if (alert.readAt == null) {
                Spacer(Modifier.height(4.dp))
                Text("Não lido", style = MaterialTheme.typography.labelSmall)
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Text(alert.message)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onResolve, modifier = Modifier.fillMaxWidth()) {
                    Text("Resolver alerta")
                }
            }
        }
    }
}

private fun alertTypeLabel(type: String): String = when (type) {
    "PATROL_NOT_STARTED" -> "Ronda não iniciada"
    "PATROL_LATE" -> "Ronda atrasada"
    "PATROL_INCOMPLETE" -> "Ronda incompleta"
    "SUSPICIOUS_SCAN" -> "Leitura suspeita"
    "DEVICE_SYNC_STALE" -> "Aparelho sem sincronização"
    "INVALID_ACCESS" -> "Acesso inválido"
    else -> type
}
