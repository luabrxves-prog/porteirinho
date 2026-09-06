package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.ActiveQrDto
import com.rondasafe.app.data.model.CheckpointDto
import com.rondasafe.app.data.model.QrFunctionResponse
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.printing.QrPrintManager
import com.rondasafe.app.ui.components.QrCodeImage
import com.rondasafe.app.ui.components.RondaSafeColors
import kotlinx.coroutines.launch

@Composable
fun CheckpointDetailWithPrintScreen(checkpoint: CheckpointDto, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var activeQr by remember { mutableStateOf<ActiveQrDto?>(null) }
    var newQr by remember { mutableStateOf<QrFunctionResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmReplace by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching { AdminRepository.getActiveQr(checkpoint.id) }
                .onSuccess { activeQr = it }
                .onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(checkpoint.id) { reload() }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { AppTopBar("Ponto de controle", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.fillMaxWidth()) {
                    Text(checkpoint.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                    checkpoint.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = RondaSafeColors.Muted)
                    }
                }
            }

            if (loading) item { CircularProgressIndicator() }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

            val token = newQr?.tokenValue ?: activeQr?.tokenValue
            val version = newQr?.version ?: activeQr?.version
            val fingerprint = newQr?.fingerprint ?: activeQr?.fingerprint

            if (token != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Surface(shape = RoundedCornerShape(18.dp), color = RondaSafeColors.Background) {
                                Box(Modifier.padding(16.dp)) {
                                    QrCodeImage(token, Modifier.size(230.dp))
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Surface(shape = RoundedCornerShape(12.dp), color = RondaSafeColors.GreenSoft) {
                                Text("QR ATIVO", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Green)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Versão ${version ?: "-"}", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                            Text("ID: ${fingerprint ?: "-"}", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            QrPrintManager.printActiveQr(
                                context = context,
                                checkpointName = checkpoint.name,
                                token = token,
                                version = version,
                                fingerprint = fingerprint,
                            )
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Imprimir QR Code", fontWeight = FontWeight.Bold) }
                }

                item {
                    OutlinedButton(
                        onClick = { confirmReplace = true },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Substituir QR Code") }
                }
            } else if (!loading) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = RondaSafeColors.BlueSoft,
                    ) {
                        Text("Este ponto ainda não possui QR Code.", modifier = Modifier.padding(18.dp), color = RondaSafeColors.Navy)
                    }
                }
                item {
                    Button(
                        onClick = {
                            scope.launch {
                                loading = true
                                runCatching { AdminRepository.createQr(checkpoint.id) }
                                    .onSuccess { newQr = it }
                                    .onFailure { error = it.message }
                                loading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text("Gerar QR Code", fontWeight = FontWeight.Bold) }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Substituir QR Code?") },
            text = { Text("O QR atual será revogado. O histórico de leituras anteriores continua preservado.") },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancelar") } },
            confirmButton = {
                Button(onClick = {
                    confirmReplace = false
                    scope.launch {
                        loading = true
                        error = null
                        runCatching { AdminRepository.replaceQr(checkpoint.id) }
                            .onSuccess { newQr = it }
                            .onFailure { error = it.message }
                        loading = false
                    }
                }) { Text("Substituir") }
            },
        )
    }
}
