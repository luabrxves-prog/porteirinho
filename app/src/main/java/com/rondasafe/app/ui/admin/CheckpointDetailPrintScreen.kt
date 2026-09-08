package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.ActiveQrDto
import com.rondasafe.app.data.model.CheckpointDto
import com.rondasafe.app.data.model.QrFunctionResponse
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.printing.QrPrintManager
import com.rondasafe.app.ui.components.*
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
            loading = true; error = null
            runCatching { AdminRepository.getActiveQr(checkpoint.id) }
                .onSuccess { activeQr = it }
                .onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(checkpoint.id) { reload() }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Ponto de controle", onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(RondaSafeUi.ScreenPadding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SectionHeading(
                if (checkpoint.systemFixed) "Ponto obrigatório" else checkpoint.name,
                checkpoint.description?.takeIf { it.isNotBlank() } ?: "QR Code do ponto de controle",
            )
            Spacer(Modifier.height(16.dp))
            if (loading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            val token = newQr?.tokenValue ?: activeQr?.tokenValue
            val version = newQr?.version ?: activeQr?.version
            val fingerprint = newQr?.fingerprint ?: activeQr?.fingerprint

            if (token != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, RondaSafeColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                ) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(shape = RoundedCornerShape(50), color = RondaSafeColors.GreenSoft) {
                            Text("QR ativo", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Green, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(16.dp))
                        QrCodeImage(token, Modifier.size(230.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Versão ${version ?: "-"}", fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                        Text("ID ${fingerprint ?: "-"}", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { QrPrintManager.printActiveQr(context, checkpoint.name, token, version, fingerprint) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Icon(Icons.Rounded.Print, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Imprimir", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { confirmReplace = true },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                    ) {
                        Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Substituir", maxLines = 1)
                    }
                }
            } else if (!loading) {
                EmptyStateCard("QR ainda não gerado", "Gere o QR Code deste ponto para começar a utilizá-lo nas rondas.", Icons.Rounded.QrCode2)
                Spacer(Modifier.height(14.dp))
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
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Substituir este QR Code?") },
            text = {
                Text(
                    "Ao confirmar, o QR Code atual será revogado imediatamente e deixará de funcionar nas próximas rondas. Você precisará imprimir e instalar o novo QR Code neste local. O histórico das leituras já realizadas será preservado.",
                )
            },
            dismissButton = { TextButton(onClick = { confirmReplace = false }) { Text("Cancelar") } },
            confirmButton = {
                Button(onClick = {
                    confirmReplace = false
                    scope.launch {
                        loading = true; error = null
                        runCatching { AdminRepository.replaceQr(checkpoint.id) }
                            .onSuccess { newQr = it }
                            .onFailure { error = it.message }
                        loading = false
                    }
                }) { Text("Entendi, substituir") }
            },
        )
    }
}
