package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.local.OfflineSyncWorker
import com.rondasafe.app.data.local.PendingEventEntity
import kotlinx.coroutines.launch

@Composable
fun OfflineSyncAdminScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { OfflineDatabase.get(context).offlineDao() }
    var failures by remember { mutableStateOf<List<PendingEventEntity>>(emptyList()) }
    var pending by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            failures = dao.permanentFailures()
            pending = dao.pendingCount()
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(topBar = { AppTopBar("Sincronização da portaria", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Aguardando envio: $pending", style = MaterialTheme.typography.titleMedium)
            Text("Falhas que precisam de atenção: ${failures.size}", style = MaterialTheme.typography.titleMedium)
            Text(
                "Falhas permanentes não são apagadas nem reenviadas automaticamente. Reenvie somente depois de corrigir a causa indicada.",
                style = MaterialTheme.typography.bodySmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    OfflineSyncWorker.schedule(context)
                    message = "Sincronização solicitada."
                    reload()
                }) { Text("Sincronizar pendentes") }

                if (failures.isNotEmpty()) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            failures.forEach { dao.requeuePermanentFailure(it.clientEventId) }
                            OfflineSyncWorker.schedule(context)
                            message = "Falhas reenfileiradas manualmente."
                            reload()
                        }
                    }) { Text("Reenfileirar todas") }
                }
            }

            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (!loading && failures.isEmpty()) {
                Text("Nenhuma falha permanente registrada neste aparelho.")
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(failures, key = { it.clientEventId }) { event ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(event.type, style = MaterialTheme.typography.titleSmall)
                            Text(event.createdAtLocal, style = MaterialTheme.typography.bodySmall)
                            Text("Tentativas: ${event.attempts}", style = MaterialTheme.typography.bodySmall)
                            Text(event.lastError ?: "Sem detalhe de erro", color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = {
                                scope.launch {
                                    dao.requeuePermanentFailure(event.clientEventId)
                                    OfflineSyncWorker.schedule(context)
                                    message = "Evento reenfileirado."
                                    reload()
                                }
                            }) { Text("Reenfileirar este evento") }
                        }
                    }
                }
            }
        }
    }
}
