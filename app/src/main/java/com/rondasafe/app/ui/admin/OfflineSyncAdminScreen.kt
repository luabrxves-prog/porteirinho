package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.local.OfflineSyncWorker
import com.rondasafe.app.data.local.PendingEventEntity
import com.rondasafe.app.ui.components.*
import kotlinx.coroutines.delay
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

    fun forceSync() {
        OfflineSyncWorker.schedule(context, force = true)
        message = "Sincronização iniciada agora."
        scope.launch {
            delay(1200)
            reload()
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Sincronização", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeading("Dados da portaria", "Acompanhe registros salvos no aparelho e eventuais falhas de envio.") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumMetricCard(pending.toString(), "Pendentes", Icons.Rounded.Sync, Modifier.weight(1f))
                    PremiumMetricCard(failures.size.toString(), "Com falha", Icons.Rounded.ErrorOutline, Modifier.weight(1f))
                }
            }
            item {
                Button(
                    onClick = { forceSync() },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                ) { Icon(Icons.Rounded.Sync, null); Spacer(Modifier.width(7.dp)); Text("Sincronizar agora", fontWeight = FontWeight.Bold) }
            }
            if (failures.isNotEmpty()) {
                item {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                failures.forEach { dao.requeuePermanentFailure(it.clientEventId) }
                                OfflineSyncWorker.schedule(context, force = true)
                                message = "Falhas reenfileiradas e sincronização iniciada."
                                delay(1200)
                                reload()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Reenfileirar todas as falhas") }
                }
            }
            message?.let { item { Surface(shape = RoundedCornerShape(14.dp), color = RondaSafeColors.BlueSoft) { Text(it, modifier = Modifier.padding(12.dp), color = RondaSafeColors.Navy) } } }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (!loading && failures.isEmpty() && pending == 0) {
                item { EmptyStateCard("Tudo sincronizado", "Nenhuma pendência ou falha registrada neste aparelho.", Icons.Rounded.CloudDone) }
            } else if (!loading && failures.isEmpty() && pending > 0) {
                item { EmptyStateCard("Sincronização em andamento", "$pending registro(s) aguardando envio. Use 'Sincronizar agora' para forçar nova tentativa.", Icons.Rounded.Sync) }
            }
            items(failures, key = { it.clientEventId }) { event ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, RondaSafeColors.Border),
                ) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(event.type, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                        Text(event.createdAtLocal, style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                        Text("Tentativas: ${event.attempts}", style = MaterialTheme.typography.bodySmall)
                        Text(event.lastError ?: "Sem detalhe de erro", color = RondaSafeColors.Danger, style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    dao.requeuePermanentFailure(event.clientEventId)
                                    OfflineSyncWorker.schedule(context, force = true)
                                    message = "Evento reenfileirado e sincronização iniciada."
                                    delay(1200)
                                    reload()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Reenfileirar este registro") }
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}
