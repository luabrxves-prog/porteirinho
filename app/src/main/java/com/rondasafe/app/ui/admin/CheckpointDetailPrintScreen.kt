package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.ActiveQrDto
import com.rondasafe.app.data.model.CheckpointDto
import com.rondasafe.app.data.model.QrFunctionResponse
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.printing.QrPrintManager
import com.rondasafe.app.ui.components.QrCodeImage
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

    Scaffold(topBar = { AppTopBar("Ponto de ronda", onBack) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(20.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(checkpoint.name, style = MaterialTheme.typography.headlineSmall)
            checkpoint.description?.let {
                Spacer(Modifier.height(4.dp))
                Text(it)
            }
            Spacer(Modifier.height(22.dp))

            if (loading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            val token = newQr?.tokenValue ?: activeQr?.tokenValue
            val version = newQr?.version ?: activeQr?.version
            val fingerprint = newQr?.fingerprint ?: activeQr?.fingerprint

            if (token != null) {
                QrCodeImage(token, Modifier.size(260.dp))
                Spacer(Modifier.height(12.dp))
                Text("QR ativo • versão ${version ?: "-"}")
                Text("Identificação: ${fingerprint ?: "-"}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(20.dp))

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
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Imprimir QR Code") }

                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = { confirmReplace = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Substituir QR Code") }
            } else if (!loading) {
                Text("Este ponto ainda não possui QR Code ativo.")
                Spacer(Modifier.height(18.dp))
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
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Gerar QR Code") }
            }
        }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Substituir QR Code?") },
            text = {
                Text("O QR Code atual será revogado e deixará de ser válido para novas rondas. O histórico de leituras anteriores será preservado.")
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) { Text("Cancelar") }
            },
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
                }) { Text("Substituir QR Code") }
            },
        )
    }
}
