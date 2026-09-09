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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rondasafe.app.AppTime
import com.rondasafe.app.data.model.PatrolHistoryItemDto
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.data.repository.PatrolHistoryRepository
import com.rondasafe.app.ui.components.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate

private data class DashboardMetrics(
    val condominium: String = "Condomínio",
    val today: List<PatrolHistoryItemDto> = emptyList(),
    val openAlerts: Int = 0,
) {
    val total: Int get() = today.size
    val completed: Int get() = today.count {
        it.displayStatus == "COMPLETED" && !it.isLate && !it.suspicious && it.missingPoints == 0
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
    val dashboardZone = AppTime.zone()
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
                val hasAlerts = metrics.openAlerts > 0
                val title = when {
                    hasAlerts -> "Há alertas para conferir"
                    metrics.total == 0 -> "Sem rondas previstas hoje"
                    else -> "Tudo em ordem"
                }
                val subtitle = when {
                    hasAlerts -> "${metrics.openAlerts} alerta(s) pendente(s). Consulte a aba Alertas para revisar qualquer anomalia."
                    metrics.total == 0 -> "Nenhuma ronda está prevista para hoje."
                    else -> "As rondas de hoje estão sem alertas pendentes."
                }
                val background = if (hasAlerts) Color(0xFFFFF4DF) else RondaSafeColors.GreenSoft
                val foreground = if (hasAlerts) Color(0xFFB66A00) else RondaSafeColors.Green
                Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = background) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            title,
                            fontWeight = FontWeight.ExtraBold,
                            color = foreground,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            subtitle,
                            modifier = Modifier.fillMaxWidth(),
                            color = RondaSafeColors.Text,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Justify,
                        )
                    }
                }
            }

            // Keep the compact layout readable on narrow phones: two cards on top,
            // one full-width card below. This avoids cropped labels/icons.
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumMetricCard(
                        metrics.total.toString(),
                        "Previstas",
                        Icons.Rounded.Schedule,
                        Modifier.weight(1f),
                    )
                    PremiumMetricCard(
                        metrics.completed.toString(),
                        "Concluídas",
                        Icons.Rounded.CheckCircle,
                        Modifier.weight(1f),
                    )
                }
            }
            item {
                PremiumMetricCard(
                    metrics.openAlerts.toString(),
                    "Alertas",
                    Icons.Rounded.NotificationsActive,
                    Modifier.fillMaxWidth(),
                )
            }

            item { Spacer(Modifier.height(4.dp)); SectionHeading("Acompanhar") }
            item {
                PremiumMenuRow(
                    "Rondas de hoje e histórico",
                    "Veja o que foi feito e o que ficou pendente",
                    Icons.Rounded.History,
                    onOpenHistory,
                )
            }
            item {
                PremiumMenuRow(
                    "Alertas",
                    "Todas as anomalias e situações que precisam de revisão",
                    Icons.Rounded.NotificationsActive,
                    onOpenAlerts,
                )
            }

            item { Spacer(Modifier.height(4.dp)); SectionHeading("Gerenciar") }
            item { PremiumMenuRow("Horários das rondas", "Altere somente início e fim das rondas fixas", Icons.Rounded.Schedule, onOpenPatrols) }
            item { PremiumMenuRow("Porteiros", "Equipe, foto e PIN de acesso", Icons.Rounded.Badge, onOpenGuards) }
            item { PremiumMenuRow("Locais e QR Codes", "Andares, pontos e impressão dos QR Codes", Icons.Rounded.Place, onOpenLocations) }
            item { PremiumMenuRow("Relatórios", "Exporte informações quando precisar", Icons.Rounded.TableView, onOpenReports) }
            item { PremiumMenuRow("Ajustes", "Aparelho da portaria e itens arquivados", Icons.Rounded.Settings, onOpenSettings) }

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
