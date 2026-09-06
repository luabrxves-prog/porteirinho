package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.ui.components.RondaSafeColors
import com.rondasafe.app.ui.components.RondaSafeMark
import kotlinx.coroutines.launch

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

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { AppTopBar("Painel administrativo") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = RondaSafeColors.Navy),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RondaSafeMark(Modifier.size(58.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "RondaSafe",
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSecondary,
                            )
                            Text(
                                "Visão geral da operação",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondary.copy(alpha = 0.72f),
                            )
                        }
                    }
                }
            }

            item { SectionTitle("Acompanhar") }
            item { DashboardCardV3("Alertas", "Atrasos, rondas incompletas, leituras suspeitas e falhas de sincronização.", "Atenção", onOpenAlerts) }
            item { DashboardCardV3("Histórico de rondas", "Consulte rondas concluídas, atrasadas, incompletas e não realizadas.", "Relatórios", onOpenHistory) }
            item { DashboardCardV3("Sincronização da portaria", "Veja pendências locais e registros que precisam de atenção.", "Offline", onOpenSync) }

            item { SectionTitle("Cadastros") }
            item { DashboardCardV3("Locais e QR Codes", "Prédios, blocos, andares, pontos e QR Codes.", "Locais", onOpenLocations) }
            item { DashboardCardV3("Porteiros", "Cadastro, foto, PIN temporário e redefinição de acesso.", "Equipe", onOpenGuards) }

            item { SectionTitle("Rondas") }
            item { DashboardCardV3("Programação de rondas", "Crie e edite dias, horários, tolerância e pontos obrigatórios.", "Agenda", onOpenPatrols) }
            item { DashboardCardV3("Responsáveis por ronda", "Opcional: sem responsável específico, qualquer porteiro ativo pode realizar.", "Opcional", onOpenAssignments) }

            item { SectionTitle("Aparelho") }
            item { DashboardCardV3("Configurar aparelho da portaria", "Vincule este celular a um prédio para uso pelos porteiros.", "Dispositivo", onOpenDeviceProvision) }

            item {
                Spacer(Modifier.height(8.dp))
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = RondaSafeColors.Navy,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun DashboardCardV3(
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = RondaSafeColors.Text)
                Spacer(Modifier.height(5.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = RondaSafeColors.Muted)
            }
            Spacer(Modifier.width(12.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = RondaSafeColors.BlueSoft,
            ) {
                Text(
                    badge,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = RondaSafeColors.Navy,
                )
            }
        }
    }
}
