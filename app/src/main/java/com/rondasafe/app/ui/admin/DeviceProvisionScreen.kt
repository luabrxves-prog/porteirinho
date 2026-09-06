package com.rondasafe.app.ui.admin

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.BuildingDto
import com.rondasafe.app.data.model.DeviceProvisionRequest
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.ui.components.RondaSafeColors
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun DeviceProvisionScreen(onBack: () -> Unit, onProvisioned: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var condominium by remember { mutableStateOf<BuildingDto?>(null) }
    var name by remember { mutableStateOf("Portaria Principal") }
    val installationId = remember { UUID.randomUUID().toString() }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var initialLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching { AdminRepository.condominium() }
            .onSuccess { condominium = it }
            .onFailure { error = it.message ?: "Não foi possível carregar o condomínio." }
        initialLoading = false
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { AppTopBar("Configurar aparelho", onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 20.dp, vertical = 18.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Usar este celular na portaria",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = RondaSafeColors.Navy,
            )
            Text(
                "A configuração é feita uma vez. Depois, os porteiros poderão entrar com nome e PIN neste aparelho.",
                style = MaterialTheme.typography.bodyMedium,
                color = RondaSafeColors.Muted,
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                color = RondaSafeColors.BlueSoft,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Condomínio", style = MaterialTheme.typography.labelMedium, color = RondaSafeColors.Muted)
                    Spacer(Modifier.height(4.dp))
                    if (initialLoading) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            condominium?.name ?: "Não configurado",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = RondaSafeColors.Navy,
                        )
                        Text(
                            "Vinculado automaticamente",
                            style = MaterialTheme.typography.bodySmall,
                            color = RondaSafeColors.Green,
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome deste aparelho") },
                supportingText = { Text("Ex.: Portaria Principal") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = {
                    val building = condominium ?: return@Button
                    scope.launch {
                        loading = true
                        error = null
                        runCatching {
                            PortariaRepository.provisionDevice(
                                DeviceProvisionRequest(
                                    installationId = installationId,
                                    buildingId = building.id,
                                    name = name.trim(),
                                    model = Build.MODEL.orEmpty(),
                                    androidVersion = Build.VERSION.RELEASE.orEmpty(),
                                    appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
                                )
                            )
                            PortariaRepository.persistDeviceCredential(context)
                        }.onSuccess { onProvisioned() }
                            .onFailure { error = it.message ?: "Não foi possível configurar o aparelho." }
                        loading = false
                    }
                },
                enabled = condominium != null && name.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(if (loading) "Configurando..." else "Configurar como portaria", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
