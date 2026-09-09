package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.CheckpointDto
import com.rondasafe.app.data.model.FloorDto
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.ui.components.*
import kotlinx.coroutines.launch

@Composable
fun SimplifiedCheckpointsScreen(
    floor: FloorDto,
    onBack: () -> Unit,
    onSelect: (CheckpointDto) -> Unit,
) {
    var data by remember { mutableStateOf<List<CheckpointDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }

    LaunchedEffect(floor.id) {
        loading = true
        error = null
        runCatching {
            AdminRepository.listCheckpoints(floor.id, includeArchived = false).filter { it.active }
        }.onSuccess { data = it }
            .onFailure { error = userFriendlyError(it, "Não foi possível carregar os pontos deste andar.") }
        loading = false
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar(floor.name, onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeading(
                    "Pontos obrigatórios",
                    if (data.isEmpty()) "Adicione o primeiro ponto obrigatório deste andar."
                    else "${data.size} ponto(s) obrigatório(s). Todos entram nas 3 rondas.",
                )
            }
            item {
                Button(
                    onClick = { addOpen = true },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    androidx.compose.material3.Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Adicionar ponto obrigatório", fontWeight = FontWeight.Bold)
                }
            }

            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(it, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            if (!loading && data.isEmpty()) {
                item { EmptyStateCard("Nenhum ponto cadastrado", "Todo novo ponto será obrigatório e receberá QR Code automaticamente.", Icons.Rounded.Place) }
            }

            items(data, key = { it.id }) { checkpoint ->
                Card(
                    onClick = { onSelect(checkpoint) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, RondaSafeColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(14.dp), color = RondaSafeColors.BlueSoft) {
                            Box(contentAlignment = Alignment.Center) { androidx.compose.material3.Icon(Icons.Rounded.QrCode2, null, tint = RondaSafeColors.Navy) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(checkpoint.name, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                            Text(
                                "Obrigatório nas 3 rondas • toque para ver o QR Code",
                                style = MaterialTheme.typography.bodySmall,
                                color = RondaSafeColors.Muted,
                            )
                        }
                        androidx.compose.material3.Icon(Icons.Rounded.ChevronRight, null, tint = RondaSafeColors.Muted)
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    if (addOpen) {
        SimpleCheckpointDialog(
            onDismiss = { addOpen = false },
            onConfirm = { name ->
                val created = AdminRepository.createCheckpoint(floor.id, name, null)
                data = (data + created).sortedBy { it.sortOrder }
                addOpen = false
                error = null
            },
        )
    }
}

@Composable
private fun SimpleCheckpointDialog(
    onDismiss: () -> Unit,
    onConfirm: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Novo ponto obrigatório") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "O ponto será incluído automaticamente nas 3 rondas e receberá um QR Code ativo.",
                    color = RondaSafeColors.Muted,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do ponto") },
                    placeholder = { Text("Ex.: Hall do elevador") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") } },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        runCatching { onConfirm(name.trim()) }
                            .onFailure { error = userFriendlyError(it, "Não foi possível adicionar este ponto.") }
                        loading = false
                    }
                },
                enabled = name.isNotBlank() && !loading,
            ) { Text(if (loading) "Salvando..." else "Adicionar") }
        },
    )
}
