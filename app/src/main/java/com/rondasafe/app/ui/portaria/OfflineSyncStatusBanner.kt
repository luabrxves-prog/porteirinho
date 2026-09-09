package com.rondasafe.app.ui.portaria

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.local.OfflineSyncStatus
import com.rondasafe.app.data.local.OfflineSyncStatusRepository
import com.rondasafe.app.ui.components.userFriendlyError

@Composable
fun OfflineSyncStatusBanner(modifier: Modifier = Modifier, compact: Boolean = false) {
    val context = LocalContext.current.applicationContext
    val flow = remember(context) { OfflineSyncStatusRepository.observe(context) }
    val status by flow.collectAsState(initial = OfflineSyncStatus())
    OfflineSyncStatusContent(status, modifier, compact)
}

@Composable
internal fun OfflineSyncStatusContent(status: OfflineSyncStatus, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (status.pending <= 0 && status.failedPermanent <= 0) return
    var details by remember { mutableStateOf(false) }
    val title = if (status.failedPermanent > 0) "Falha de sincroniza\u00e7\u00e3o" else "Aguardando sincroniza\u00e7\u00e3o"
    val description = if (status.failedPermanent > 0) {
        userFriendlyError(IllegalStateException(status.latestError), "H\u00e1 registros anteriores deste aparelho aguardando revis\u00e3o administrativa.")
    } else "${status.pending} registro(s) ainda est\u00e3o salvos somente neste aparelho. A confirma\u00e7\u00e3o depende do servidor."
    val color = if (status.failedPermanent > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Card(modifier.fillMaxWidth()) {
        if (compact) {
            TextButton(onClick = { details = true }, modifier = Modifier.fillMaxWidth()) {
                Text("$title - ver detalhes", maxLines = 1, overflow = TextOverflow.Ellipsis, color = color)
            }
        } else Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = color)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (details) AlertDialog(
        onDismissRequest = { details = false },
        title = { Text(title) },
        text = { Column { Text(description); status.latestError?.let { Text(it, style = MaterialTheme.typography.bodySmall) } } },
        confirmButton = { TextButton(onClick = { details = false }) { Text("Fechar") } },
    )
}
