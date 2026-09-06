package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.repository.AuthRepository
import kotlinx.coroutines.launch

@Composable
fun AdminDashboardScreenV3(
    onOpenLocations: () -> Unit,
    onOpenGuards: () -> Unit,
    onOpenPatrols: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenDeviceProvision: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { AppTopBar("Painel administrativo") }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("RondaSafe", style = MaterialTheme.typography.headlineMedium)
            Text("Gestão do sistema", style = MaterialTheme.typography.bodyLarge)
            DashboardCardV3("Alertas", "Acompanhe atrasos, rondas incompletas, leituras suspeitas e falhas de sincronização.", onOpenAlerts)
            DashboardCardV3("Locais e QR Codes", "Cadastre blocos, andares, pontos e QR Codes.", onOpenLocations)
            DashboardCardV3("Porteiros", "Cadastre porteiros, gere PIN temporário e redefina acessos.", onOpenGuards)
            DashboardCardV3(
                "Programação de Rondas",
                "Crie rondas com dias, horários, tolerância e pontos obrigatórios.",
                onOpenPatrols,
            )
            DashboardCardV3(
                "Configurar aparelho da portaria",
                "Pareie este celular com um prédio para uso pelos porteiros.",
                onOpenDeviceProvision,
            )
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        AuthRepository.signOut()
                        onLogout()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sair") }
        }
    }
}

@Composable
private fun DashboardCardV3(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
