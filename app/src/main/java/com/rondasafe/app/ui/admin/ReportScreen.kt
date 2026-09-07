package com.rondasafe.app.ui.admin

import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.TableView
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.repository.ReportRepository
import com.rondasafe.app.ui.components.PremiumTopBar
import com.rondasafe.app.ui.components.RondaSafeColors
import com.rondasafe.app.ui.components.RondaSafeUi
import com.rondasafe.app.ui.components.SectionHeading
import kotlinx.coroutines.launch

@Composable
fun ReportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by remember { mutableIntStateOf(30) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingFileName by remember { mutableStateOf("RondaSafe_Relatorio.xlsx") }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ),
    ) { uri ->
        if (uri == null) {
            pendingBytes = null
            return@rememberLauncherForActivityResult
        }
        val bytes = pendingBytes
        if (bytes == null) {
            error = "Não foi possível localizar o arquivo gerado."
            return@rememberLauncherForActivityResult
        }
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("Não foi possível abrir o destino selecionado.")
        }.onSuccess {
            message = "Planilha salva com sucesso."
            error = null
        }.onFailure {
            error = it.message ?: "Não foi possível salvar a planilha."
        }
        pendingBytes = null
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Relatórios", onBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeading(
                "Exportar planilha",
                "Gere um Excel estruturado com o histórico operacional do condomínio.",
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, RondaSafeColors.Border),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.TableView, null, tint = RondaSafeColors.Blue)
                        Spacer(Modifier.width(9.dp))
                        Text("Período do relatório", fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 30).forEach { option ->
                            FilterChip(
                                selected = days == option,
                                onClick = { days = option },
                                label = { Text("$option dias") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(90, 365).forEach { option ->
                            FilterChip(
                                selected = days == option,
                                onClick = { days = option },
                                label = { Text(if (option == 365) "1 ano" else "$option dias") },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = RondaSafeColors.BlueSoft),
                border = BorderStroke(1.dp, RondaSafeColors.Blue.copy(alpha = .18f)),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Description, null, tint = RondaSafeColors.Navy)
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("O Excel é separado por abas", fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                        Text(
                            "Resumo, Rondas, Pontos, Leituras QR, Turnos, Alertas, Ocorrências e Auditoria.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RondaSafeColors.Muted,
                        )
                        Text(
                            "Os horários são exibidos no fuso de São Paulo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = RondaSafeColors.Muted,
                        )
                    }
                }
            }

            error?.let {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            message?.let {
                Surface(shape = RoundedCornerShape(14.dp), color = RondaSafeColors.GreenSoft) {
                    Text(it, modifier = Modifier.padding(12.dp), color = RondaSafeColors.Green)
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        message = null
                        runCatching { ReportRepository.export(days) }
                            .onSuccess { report ->
                                runCatching {
                                    val bytes = Base64.decode(report.fileBase64, Base64.DEFAULT)
                                    require(bytes.isNotEmpty()) { "O arquivo Excel retornou vazio." }
                                    pendingBytes = bytes
                                    pendingFileName = report.fileName ?: "RondaSafe_Relatorio.xlsx"
                                    saveLauncher.launch(pendingFileName)
                                }.onFailure { error = it.message ?: "Não foi possível preparar a planilha." }
                            }
                            .onFailure { error = it.message ?: "Não foi possível gerar a planilha." }
                        loading = false
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Download, null)
                }
                Spacer(Modifier.width(8.dp))
                Text(if (loading) "Gerando Excel..." else "Gerar e salvar Excel", fontWeight = FontWeight.Bold)
            }
        }
    }
}
