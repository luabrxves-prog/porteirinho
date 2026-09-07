package com.rondasafe.app.ui.admin

import android.content.Intent
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.repository.ReportRepository
import com.rondasafe.app.ui.components.RondaSafeColors
import kotlinx.coroutines.launch

@Composable
fun ReportExportScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedDays by remember { mutableIntStateOf(30) }
    var pendingBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingName by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    ) { uri ->
        val bytes = pendingBytes
        if (uri != null && bytes != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("Não foi possível abrir o arquivo para gravação.")
            }.onSuccess {
                success = "Planilha salva com sucesso."
                error = null
            }.onFailure {
                error = it.message ?: "Não foi possível salvar a planilha."
            }
        }
        pendingBytes = null
        pendingName = null
    }

    fun exportReport() {
        if (loading) return
        scope.launch {
            loading = true
            error = null
            success = null
            runCatching { ReportRepository.export(selectedDays) }
                .onSuccess { result ->
                    val bytes = Base64.decode(result.fileBase64, Base64.DEFAULT)
                    pendingBytes = bytes
                    pendingName = result.fileName
                    saveLauncher.launch(result.fileName ?: "RondaSafe_Relatorio.xlsx")
                }
                .onFailure {
                    error = it.message ?: "Não foi possível gerar o relatório."
                }
            loading = false
        }
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { AppTopBar("Relatórios", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = RondaSafeColors.Navy,
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.TableChart, null, tint = Color.White)
                            Spacer(Modifier.padding(4.dp))
                            Text(
                                "Exportar relatório operacional",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Gere uma planilha Excel estruturada com resumo, rondas, pontos, leituras de QR, turnos, alertas e auditoria.",
                            color = Color.White.copy(alpha = 0.76f),
                        )
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Período", fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                        Spacer(Modifier.height(4.dp))
                        Text("Escolha quanto histórico deseja incluir.", color = RondaSafeColors.Muted)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 30, 90).forEach { days ->
                                FilterChip(
                                    selected = selectedDays == days,
                                    onClick = { selectedDays = days },
                                    label = { Text("$days dias") },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(180 to "6 meses", 366 to "1 ano").forEach { (days, label) ->
                                FilterChip(
                                    selected = selectedDays == days,
                                    onClick = { selectedDays = days },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RondaSafeColors.BlueSoft),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Estrutura da planilha", fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                        Spacer(Modifier.height(8.dp))
                        Text("• Resumo executivo", color = RondaSafeColors.Navy)
                        Text("• Rondas e status", color = RondaSafeColors.Navy)
                        Text("• Pontos obrigatórios e visitados", color = RondaSafeColors.Navy)
                        Text("• Leituras de QR", color = RondaSafeColors.Navy)
                        Text("• Turnos", color = RondaSafeColors.Navy)
                        Text("• Alertas", color = RondaSafeColors.Navy)
                        Text("• Auditoria", color = RondaSafeColors.Navy)
                    }
                }
            }

            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            success?.let { item { Text(it, color = RondaSafeColors.Navy, fontWeight = FontWeight.SemiBold) } }

            item {
                Button(
                    onClick = ::exportReport,
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RondaSafeColors.Navy),
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(22.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Icon(Icons.Rounded.Download, null)
                        Spacer(Modifier.padding(4.dp))
                        Text("Gerar Excel", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
