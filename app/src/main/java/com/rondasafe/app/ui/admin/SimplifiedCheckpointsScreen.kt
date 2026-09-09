package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
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
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<List<CheckpointDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<CheckpointDto?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }

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
                val extraCount = data.count { it.sortOrder > 0 }
                SectionHeading(
                    "Pontos de ronda",
                    "1 ponto padrão fixo${if (extraCount > 0) " + $extraCount ponto(s) extra(s)" else ""}.",
                )
            }
            item {
                Button(
                    onClick = { addOpen = true },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Adicionar ponto extra", fontWeight = FontWeight.Bold)
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
                item { EmptyStateCard("Ponto padrão indisponível", "O ponto fixo deste andar não foi carregado.", Icons.Rounded.Place) }
            }

            items(data, key = { it.id }) { checkpoint ->
                val fixed = checkpoint.sortOrder == 0
                val removing = removingId == checkpoint.id
                Card(
                    onClick = { if (!removing) onSelect(checkpoint) },
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
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.QrCode2, null, tint = RondaSafeColors.Navy) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(checkpoint.name, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                            Text(
                                if (fixed) "Ponto padrão fixo • toque para ver ou imprimir o QR Code"
                                else "Ponto extra • toque para ver ou imprimir o QR Code",
                                style = MaterialTheme.typography.bodySmall,
                                color = RondaSafeColors.Muted,
                            )
                        }
                        when {
                            removing -> CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            !fixed -> IconButton(onClick = { removeTarget = checkpoint }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remover ponto extra", tint = RondaSafeColors.Muted)
                            }
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
                val created = AdminRepository.createCheckpoint(floor.id, name, description)
                data = (data + created).sortedBy { it.sortOrder }
                addOpen = false
                error = null
            },
        )
    }

    removeTarget?.let { checkpoint ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remover ${checkpoint.name}?") },
            text = { Text("Este ponto extra sairá da operação. O ponto padrão fixo do andar continuará preservado.") },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancelar") } },
            confirmButton = {
                Button(onClick = {
                    removeTarget = null
                    removingId = checkpoint.id
                    val previous = data
                    data = data.filterNot { it.id == checkpoint.id }
                    scope.launch {
                        runCatching { AdminRepository.archive("checkpoints", checkpoint.id) }
                            .onFailure {
                                data = previous
                                error = userFriendlyError(it, "Não foi possível remover este ponto extra.")
                            }
                        removingId = null
                    }
                }) { Text("Remover") }
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
        title = { Text("Novo ponto extra") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    placeholder = { Text("Ex.: Hall secundário") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Observação (opcional)") },
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
                        runCatching { onConfirm(name.trim(), description.trim().takeIf { it.isNotBlank() }) }
                            .onFailure { error = userFriendlyError(it, "Não foi possível adicionar este ponto extra.") }
                        loading = false
                    }
                },
                enabled = name.isNotBlank() && !loading,
            ) { Text(if (loading) "Salvando..." else "Adicionar") }
        },
    )
}
