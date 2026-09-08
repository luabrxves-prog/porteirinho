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

    // Sincronização normal e silenciosa. Pendências momentâneas fazem parte do
    // mecanismo offline-first e não devem parecer erro para o porteiro.
    if (status.failedPermanent <= 0) return

    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Falha de sincronização",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                status.latestError ?: "Há registros deste aparelho que precisam de atenção do administrador.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
