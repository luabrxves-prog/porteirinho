package com.rondasafe.app.ui.admin

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.BuildingDto
import com.rondasafe.app.data.model.DeviceProvisionRequest
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.PortariaRepository
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun DeviceProvisionScreen(onBack: () -> Unit, onProvisioned: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var buildings by remember { mutableStateOf<List<BuildingDto>>(emptyList()) }
    var selected by remember { mutableStateOf<BuildingDto?>(null) }
    var name by remember { mutableStateOf("Celular da Portaria") }
    var installationId by remember { mutableStateOf(UUID.randomUUID().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { AdminRepository.listBuildings() }
            .onSuccess { buildings = it }
            .onFailure { error = it.message }
    }

    Scaffold(topBar = { AppTopBar("Configurar aparelho", onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).fillMaxSize()) {
            Text("Transformar este aparelho em dispositivo de portaria", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Faça isso apenas no celular compartilhado da portaria.")
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Nome do aparelho") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))
            Text("Prédio", style = MaterialTheme.typography.titleMedium)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(buildings, key = { it.id }) { building ->
                    Card(onClick = { selected = building }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp)) {
                            RadioButton(selected = selected?.id == building.id, onClick = { selected = building })
                            Spacer(Modifier.width(8.dp))
                            Text(building.name)
                        }
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val building = selected ?: return@Button
                    scope.launch {
                        loading = true
                        error = null
                        runCatching {
                            PortariaRepository.provisionDevice(
                                DeviceProvisionRequest(
                                    installationId = installationId,
                                    buildingId = building.id,
                                    name = name,
                                    model = Build.MODEL ?: "",
                                    androidVersion = Build.VERSION.RELEASE ?: "",
                                    appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "",
                                )
                            )
                            PortariaRepository.persistDeviceCredential(context)
                        }.onSuccess { onProvisioned() }
                            .onFailure { error = it.message }
                        loading = false
                    }
                },
                enabled = selected != null && name.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (loading) "Configurando..." else "Configurar este aparelho") }
        }
    }
}
