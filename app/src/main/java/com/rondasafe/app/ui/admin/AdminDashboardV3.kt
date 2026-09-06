package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.data.repository.GuardRepository
import com.rondasafe.app.data.repository.PatrolRepository
import com.rondasafe.app.ui.components.RondaSafeColors
import com.rondasafe.app.ui.components.RondaSafeMark
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
                guards = GuardRepository.list().size,
                patrols = PatrolRepository.listTemplates().size,
            )
        }.onSuccess { metrics = it }
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { AppTopBar("Administrador") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = RondaSafeColors.Navy),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RondaSafeMark(Modifier.size(62.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                metrics.condominium,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondary,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "Operação de rondas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.72f),
                            )
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricCard("Pontos", metrics.checkpoints.toString(), Modifier.weight(1f))
                    MetricCard("Blocos", metrics.blocks.toString(), Modifier.weight(1f))
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MetricCard("Porteiros", metrics.guards.toString(), Modifier.weight(1f))
                    MetricCard("Rondas", metrics.patrols.toString(), Modifier.weight(1f))
                }
            }

            item { SectionTitle("Gestão") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickCard("QR", "Locais e QR Codes", "Andares, pontos e impressão", onOpenLocations, Modifier.weight(1f))
                    QuickCard("PE", "Porteiros", "Equipe e acessos", onOpenGuards, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    QuickCard("RO", "Programações", "Horários e frequência", onOpenPatrols, Modifier.weight(1f))
                    QuickCard("HI", "Histórico", "Rondas e ocorrências", onOpenHistory, Modifier.weight(1f))
                }
            }

            item { SectionTitle("Operação") }
            item { DashboardActionCard("Responsáveis por ronda", "Opcional: defina porteiros específicos ou deixe qualquer porteiro ativo realizar.", onOpenAssignments) }
            item { DashboardActionCard("Alertas", "Atrasos, rondas incompletas e leituras suspeitas.", onOpenAlerts) }
            item { DashboardActionCard("Sincronização", "Pendências offline e registros que precisam de atenção.", onOpenSync) }
            item { DashboardActionCard("Configurar aparelho", "Transforme este celular em aparelho da portaria.", onOpenDeviceProvision) }

            item {
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            AuthRepository.signOut()
                            onLogout()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Sair do administrador", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
            Text(label, style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = RondaSafeColors.Navy,
        modifier = Modifier.padding(top = 6.dp, bottom = 1.dp),
    )
}

@Composable
private fun QuickCard(
    initials: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.height(146.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(15.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = RondaSafeColors.BlueSoft) {
                Text(
                    initials,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    fontWeight = FontWeight.ExtraBold,
                    color = RondaSafeColors.Navy,
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RondaSafeColors.Text)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
        }
    }
}

@Composable
private fun DashboardActionCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = RondaSafeColors.Text)
                Spacer(Modifier.height(3.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
            }
            Spacer(Modifier.width(10.dp))
            Text("›", style = MaterialTheme.typography.headlineSmall, color = RondaSafeColors.Blue)
        }
    }
}
