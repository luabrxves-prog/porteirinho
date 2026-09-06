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
fun AdminDashboardScreenV2(
    onOpenLocations: () -> Unit,
    onOpenGuards: () -> Unit,
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
            AdminDashboardCard("Locais e QR Codes", "Cadastre blocos, andares, pontos e QR Codes.", onOpenLocations)
            AdminDashboardCard("Porteiros", "Cadastre porteiros, gere PIN temporário e redefina acessos.", onOpenGuards)
            AdminDashboardCard("Rondas", "Programações e histórico serão conectados na próxima etapa.") {}
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
private fun AdminDashboardCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
