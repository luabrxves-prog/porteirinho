package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.PatrolHistoryItemDto
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.data.repository.PatrolHistoryRepository
import com.rondasafe.app.ui.components.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

private val dashboardZone = ZoneId.of("America/Sao_Paulo")

private data class DashboardMetrics(
    val condominium: String = "Condomínio",
    val today: List<PatrolHistoryItemDto> = emptyList(),
    val openAlerts: Int = 0,
) {
    val total: Int get() = today.size
    val completed: Int get() = today.count { it.displayStatus == "COMPLETED" && !it.isLate && !it.suspicious && it.missingPoints == 0 }
    val attention: Int get() = today.count {
        it.displayStatus in setOf("MISSED", "INCOMPLETE", "LATE") || it.isLate || it.suspicious || it.missingPoints > 0
    }
}

@Composable
fun AdminDashboardScreenV3(
    onOpenLocations: () -> Unit,
    onOpenGuards: () -> Unit,
    onOpenPatrols: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReports: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var metrics by remember { mutableStateOf(DashboardMetrics()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        runCatching {
            coroutineScope {
                val condominiumDeferred = async { AdminRepository.condominium() }
                val alertsDeferred = async { AdminRepository.listAlerts().size }
                val todayDeferred = async {
                    val today = LocalDate.now(dashboardZone)
                    PatrolHistoryRepository.listRange(
                        from = today.atStartOfDay(dashboardZone).toInstant(),
                        to = today.plusDays(1).atStartOfDay(dashboardZone).toInstant().minusMillis(1),
                    )
                }
                DashboardMetrics(
                    condominium = condominiumDeferred.await().name,
                    today = todayDeferred.await(),
                    openAlerts = alertsDeferred.await(),
                )
            }
        }.onSuccess { metrics = it }
        loading = false
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Administração") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                CondoPhotoHeroCard(
                    title = metrics.condominium,
                    subtitle = "Resumo de hoje",
                )
            }

            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

            item {
                val needsAttention = metrics.attention > 0 || metrics.openAlerts > 0
                val title = when {
                    needsAttention -> "Há algo para conferir"
                    metrics.total == 0 -> "Sem rondas previstas hoje"
                    else -> "Tudo em ordem"
                }
                val subtitle = when {
                    metrics.attention > 0 -> "${metrics.attention} ronda(s) precisam da sua atenção."
                    metrics.openAlerts > 0 -> "${metrics.openAlerts} alerta(s) pendente(s)."
                    metrics.total == 0 -> "Nenhuma ronda está prevista para hoje."
                    else -> "As rondas de hoje estão sem pendências."
                }
                val background = if (needsAttention) Color(0xFFFFF4DF) else RondaSafeColors.GreenSoft
                val foreground = if (needsAttention) Color(0xFFB66A00) else RondaSafeColors.Green
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = background) {
                    Column(Modifier.padding(16.dp)) {
                        Text(title, fontWeight = FontWeight.ExtraBold, color = foreground, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(subtitle, color = RondaSafeColors.Text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumMetricCard(metrics.total.toString(), "Previstas", Icons.Rounded.Schedule, Modifier.weight(1f))
                    PremiumMetricCard(metrics.completed.toString(), "Concluídas", Icons.Rounded.CheckCircle, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumMetricCard(metrics.attention.toString(), "Atenção", Icons.Rounded.WarningAmber, Modifier.weight(1f))
                    PremiumMetricCard(metrics.openAlerts.toString(), "Alertas", Icons.Rounded.NotificationsActive, Modifier.weight(1f))
                }
            }

            item { Spacer(Modifier.height(4.dp)); SectionHeading("Acompanhar") }
            item { PremiumMenuRow("Rondas de hoje e histórico", "Veja o que foi feito e o que ficou pendente", Icons.Rounded.History, onOpenHistory) }
            item { PremiumMenuRow("Alertas", "Situações que precisam de atenção", Icons.Rounded.NotificationsActive, onOpenAlerts) }

            item { Spacer(Modifier.height(4.dp)); SectionHeading("Gerenciar") }
            item { PremiumMenuRow("Horários das rondas", "Altere somente início e fim das rondas fixas", Icons.Rounded.Schedule, onOpenPatrols) }
            item { PremiumMenuRow("Porteiros", "Equipe, foto e PIN de acesso", Icons.Rounded.Badge, onOpenGuards) }
            item { PremiumMenuRow("Locais e QR Codes", "Andares, pontos e impressão dos QR Codes", Icons.Rounded.Place, onOpenLocations) }
            item { PremiumMenuRow("Relatórios", "Exporte informações quando precisar", Icons.Rounded.TableView, onOpenReports) }
            item { PremiumMenuRow("Ajustes", "Responsáveis e aparelho da portaria", Icons.Rounded.Settings, onOpenSettings) }

            item {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            AuthRepository.signOut()
                            onLogout()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Rounded.Logout, null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sair", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
