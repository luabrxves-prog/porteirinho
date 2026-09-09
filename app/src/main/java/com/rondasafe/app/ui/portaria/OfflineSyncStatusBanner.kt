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
import com.rondasafe.app.data.local.OfflineSyncStatus
import com.rondasafe.app.data.local.OfflineSyncStatusRepository

@Composable
fun OfflineSyncStatusBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val flow = remember(context) { OfflineSyncStatusRepository.observe(context) }
    val status by flow.collectAsState(initial = OfflineSyncStatus())

    if (status.pending <= 0 && status.failedPermanent <= 0) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            when {
                status.failedPermanent > 0 -> {
                    Text(
                        "Falha de sincronização",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        status.latestError ?: "Há registros deste aparelho que não chegaram ao servidor.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                else -> {
                    Text(
                        "Aguardando sincronização",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "${status.pending} registro(s) ainda estão salvos somente neste aparelho. Não considere a ronda confirmada no sistema até este aviso desaparecer.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
