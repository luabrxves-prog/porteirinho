package com.rondasafe.app.ui.admin

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.BuildingDto
import com.rondasafe.app.data.model.DeviceProvisionRequest
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.ui.components.*
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
            .onFailure { error = it.message }
        initialLoading = false
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Configurar aparelho", onBack) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(RondaSafeUi.ScreenPadding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionHeading("Usar este celular na portaria", "Faça esta configuração somente no aparelho compartilhado pelos porteiros.")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, RondaSafeColors.Border),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(15.dp), color = RondaSafeColors.BlueSoft) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PhoneAndroid, null, tint = RondaSafeColors.Navy) }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Condomínio", style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Muted)
                        if (initialLoading) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
                            Text(condominium?.name ?: "Não configurado", fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = RondaSafeColors.Green, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Vinculado automaticamente", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Green)
                            }
                        }
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
                shape = RoundedCornerShape(14.dp),
            )

            Surface(shape = RoundedCornerShape(16.dp), color = RondaSafeColors.BlueSoft) {
                Text(
                    "Depois de configurar, a opção Portaria será liberada na tela inicial deste celular.",
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = RondaSafeColors.Navy,
                )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = {
                    val building = condominium ?: return@Button
                    scope.launch {
                        loading = true; error = null
                        runCatching {
                            PortariaRepository.provisionDevice(
                                DeviceProvisionRequest(
                                    action = "provision_portaria",
                                    installationId = installationId,
                                    buildingId = building.id,
                                    name = name.trim(),
                                    model = Build.MODEL.orEmpty(),
                                    androidVersion = Build.VERSION.RELEASE.orEmpty(),
                                    appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty(),
                                )
                            )
                            PortariaRepository.persistDeviceCredential(context)
                        }.onSuccess { onProvisioned() }.onFailure { error = it.message }
                        loading = false
                    }
                },
                enabled = condominium != null && name.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (loading) "Configurando..." else "Configurar como portaria", fontWeight = FontWeight.Bold) }
        }
    }
}
