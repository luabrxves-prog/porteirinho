package com.rondasafe.app.ui.portaria

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.local.OfflineSyncStatusRepository

@Composable
fun OfflineSyncStatusBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val flow = remember(context) { OfflineSyncStatusRepository.observe(context) }
    val status by flow.collectAsState(initial = com.rondasafe.app.data.local.OfflineSyncStatus())

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            when {
                status.failedPermanent > 0 -> {
                    Text(
                        "${status.failedPermanent} registro(s) precisam de atenção",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        status.latestError ?: "Há dados salvos no aparelho que o servidor não aceitou.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                status.pending > 0 -> {
                    Text(
                        "Salvo no aparelho",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "${status.pending} registro(s) aguardando sincronização.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> {
                    Text(
                        "Tudo sincronizado",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "Os registros deste aparelho foram enviados com sucesso.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
