package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.data.repository.GuardRepository
import com.rondasafe.app.data.repository.PatrolRepository
import com.rondasafe.app.ui.components.*
import kotlinx.coroutines.launch

private data class DashboardMetrics(
    val condominium: String = "Condomínio",
    val blocks: Int = 2,
    val checkpoints: Int = 0,
    val guards: Int = 0,
    val patrols: Int = 0,
)

@Composable
fun AdminDashboardScreenV3(
    onOpenLocations: () -> Unit,
    onOpenGuards: () -> Unit,
    onOpenPatrols: () -> Unit,
    onOpenAssignments: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenArchived: () -> Unit,
    onOpenDeviceProvision: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var metrics by remember { mutableStateOf(DashboardMetrics()) }

    LaunchedEffect(Unit) {
        runCatching {
            val condominium = AdminRepository.condominium()
            val blocks = AdminRepository.defaultBlocks()
            var checkpoints = 0
            blocks.forEach { block ->
                AdminRepository.listFloors(block.id).forEach { floor ->
                    checkpoints += AdminRepository.listCheckpoints(floor.id).size
                }
            }
            DashboardMetrics(
                condominium = condominium.name,
                blocks = blocks.size,
                checkpoints = checkpoints,
                guards = GuardRepository.list().count { it.active },
                patrols = PatrolRepository.listTemplates().count { it.active },
            )
        }.onSuccess { metrics = it }
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Painel administrativo") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            item {
                CondoPhotoHeroCard(
                    title = metrics.condominium,
                    subtitle = "Gestão de rondas e segurança",
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumMetricCard(metrics.checkpoints.toString(), "Pontos", Icons.Rounded.LocationOn, Modifier.weight(1f))
                    PremiumMetricCard(metrics.blocks.toString(), "Blocos", Icons.Rounded.Apartment, Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PremiumMetricCard(metrics.guards.toString(), "Porteiros", Icons.Rounded.Groups, Modifier.weight(1f))
                    PremiumMetricCard(metrics.patrols.toString(), "Rondas", Icons.Rounded.CalendarMonth, Modifier.weight(1f))
                }
            }

            item { Spacer(Modifier.height(4.dp)); SectionHeading("Gestão") }
            item { PremiumMenuRow("Locais e QR Codes", "Andares, pontos de controle e QR Codes", Icons.Rounded.Place, onOpenLocations) }
            item { PremiumMenuRow("Porteiros", "Equipe, fotos e acessos por PIN", Icons.Rounded.Badge, onOpenGuards) }
            item { PremiumMenuRow("Programações de rondas", "Horários, frequência e responsáveis", Icons.Rounded.Schedule, onOpenPatrols) }
            item { PremiumMenuRow("Histórico de rondas", "Relatórios, status e ocorrências", Icons.Rounded.History, onOpenHistory) }

            item { Spacer(Modifier.height(4.dp)); SectionHeading("Operação") }
            item { PremiumMenuRow("Responsáveis por ronda", "Opcional — sem responsável, qualquer porteiro pode realizar", Icons.Rounded.AssignmentInd, onOpenAssignments) }
            item { PremiumMenuRow("Alertas", "Atrasos, rondas incompletas e leituras suspeitas", Icons.Rounded.NotificationsActive, onOpenAlerts) }
            item { PremiumMenuRow("Arquivados", "Porteiros, andares, pontos e rondas arquivados", Icons.Rounded.Archive, onOpenArchived) }
            item { PremiumMenuRow("Configurar aparelho", "Vincule este celular à operação da portaria", Icons.Rounded.PhoneAndroid, onOpenDeviceProvision) }
            item { PremiumMenuRow("Sincronização", "Pendências offline e registros que precisam de atenção", Icons.Rounded.Sync, onOpenSync) }

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
                    Text("Sair do administrador", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}
