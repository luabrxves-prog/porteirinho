package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun SimplifiedCheckpointsScreen(
    floor: FloorDto,
    onBack: () -> Unit,
    onSelect: (CheckpointDto) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var data by remember { mutableStateOf<List<CheckpointDto>>(emptyList()) }
    var qrReady by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var archiveTarget by remember { mutableStateOf<CheckpointDto?>(null) }

    LaunchedEffect(refresh, floor.id) {
        loading = true
        error = null
        runCatching {
            coroutineScope {
                val checkpointsDeferred = async {
                    AdminRepository.listCheckpoints(floor.id, includeArchived = false).filter { it.active }
                }
                val optionsDeferred = async { AdminRepository.checkpointOptions() }
                val checkpoints = checkpointsDeferred.await()
                val readiness = optionsDeferred.await()
                    .asSequence()
                    .filter { it.floorId == floor.id }
                    .associate { it.checkpointId to it.qrReady }
                checkpoints to readiness
            }
        }.onSuccess { (checkpoints, readiness) ->
            data = checkpoints
            qrReady = readiness
        }.onFailure { error = it.message }
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
                val ready = qrReady.values.count { it }
                SectionHeading(
                    "Pontos de controle",
                    if (data.isEmpty()) "Cadastre os locais que farão parte da ronda."
                    else "$ready de ${data.size} QR Code(s) prontos para uso.",
                )
            }
            item {
                Button(
                    onClick = { addOpen = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Adicionar ponto", fontWeight = FontWeight.Bold)
                }
            }

            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!loading && data.isEmpty()) {
                item { EmptyStateCard("Nenhum ponto cadastrado", "Ex.: Hall, Elevador, Escada, Garagem ou Área externa.", Icons.Rounded.Place) }
            }

            items(data, key = { it.id }) { checkpoint ->
                val isReady = qrReady[checkpoint.id] == true
                Card(
                    onClick = { onSelect(checkpoint) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, RondaSafeColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(48.dp), shape = RoundedCornerShape(14.dp), color = RondaSafeColors.BlueSoft) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.QrCode2, null, tint = RondaSafeColors.Navy) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(checkpoint.name, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                            checkpoint.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted, maxLines = 2)
                            }
                            Spacer(Modifier.height(5.dp))
                            Surface(shape = RoundedCornerShape(50), color = if (isReady) RondaSafeColors.GreenSoft else Color(0xFFFFF4DF)) {
                                Text(
                                    if (isReady) "QR pronto" else "QR pendente",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isReady) RondaSafeColors.Green else Color(0xFFB66A00),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        IconButton(onClick = { archiveTarget = checkpoint }) {
                            Icon(Icons.Rounded.Archive, contentDescription = "Arquivar ponto", tint = RondaSafeColors.Muted)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = RondaSafeColors.Muted)
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    if (addOpen) {
        SimpleCheckpointDialog(
            onDismiss = { addOpen = false },
            onConfirm = { name, description ->
                AdminRepository.createCheckpoint(floor.id, name, description)
                addOpen = false
                refresh++
            },
        )
    }

    archiveTarget?.let { checkpoint ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text("Arquivar ${checkpoint.name}?") },
            text = { Text("O ponto sairá da lista ativa. Leituras e históricos anteriores serão preservados.") },
            dismissButton = { TextButton(onClick = { archiveTarget = null }) { Text("Cancelar") } },
            confirmButton = {
                Button(onClick = {
                    archiveTarget = null
                    scope.launch {
                        runCatching { AdminRepository.archive("checkpoints", checkpoint.id) }
                            .onSuccess { refresh++ }
                            .onFailure { error = it.message }
                    }
                }) { Text("Arquivar") }
            },
        )
    }
}

@Composable
private fun SimpleCheckpointDialog(
    onDismiss: () -> Unit,
    onConfirm: suspend (String, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Novo ponto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, placeholder = { Text("Ex.: Hall do elevador") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Observação (opcional)") }, modifier = Modifier.fillMaxWidth())
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
                        runCatching { onConfirm(name.trim(), description.trim().takeIf { it.isNotBlank() }) }
                            .onFailure { error = it.message }
                        loading = false
                    }
                },
                enabled = name.isNotBlank() && !loading,
            ) { Text(if (loading) "Salvando..." else "Adicionar") }
        },
    )
}
