package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.CheckpointDto
import com.rondasafe.app.data.model.FloorDto
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.ui.components.RondaSafeColors
import kotlinx.coroutines.launch

@Composable
fun SimplifiedCheckpointsScreen(
    floor: FloorDto,
    onBack: () -> Unit,
    onSelect: (CheckpointDto) -> Unit,
) {
    var refresh by remember { mutableIntStateOf(0) }
    var includeArchived by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<List<CheckpointDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }

    LaunchedEffect(refresh, includeArchived, floor.id) {
        loading = true
        error = null
        runCatching { AdminRepository.listCheckpoints(floor.id, includeArchived) }
            .onSuccess { data = it }
            .onFailure { error = it.message ?: "Não foi possível carregar os pontos." }
        loading = false
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { AppTopBar(floor.name, onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Pontos de controle", style = MaterialTheme.typography.headlineSmall, color = RondaSafeColors.Navy)
                        Text("Cadastre os locais onde o porteiro fará a leitura do QR.", color = RondaSafeColors.Muted)
                    }
                    Switch(checked = includeArchived, onCheckedChange = { includeArchived = it })
                }
            }

            item {
                Button(
                    onClick = { addOpen = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("+ Adicionar ponto", fontWeight = FontWeight.Bold) }
            }

            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

            if (!loading && data.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = RondaSafeColors.BlueSoft,
                    ) {
                        Text(
                            "Nenhum ponto neste andar. Exemplos: Hall, Elevador, Escada, Garagem ou Área externa.",
                            modifier = Modifier.padding(18.dp),
                            color = RondaSafeColors.Navy,
                        )
                    }
                }
            }

            items(data, key = { it.id }) { checkpoint ->
                CheckpointManagementCard(
                    checkpoint = checkpoint,
                    onOpen = { onSelect(checkpoint) },
                    onChanged = { refresh++ },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
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
}

@Composable
private fun CheckpointManagementCard(
    checkpoint: CheckpointDto,
    onOpen: () -> Unit,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirmArchive by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (checkpoint.active) RondaSafeColors.GreenSoft else RondaSafeColors.Border,
                ) {
                    Text(
                        if (checkpoint.active) "ATIVO" else "ARQUIVADO",
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (checkpoint.active) RondaSafeColors.Green else RondaSafeColors.Muted,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(checkpoint.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    checkpoint.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                    }
                }
            }

            if (checkpoint.active) {
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text("Abrir QR Code")
                }
                OutlinedButton(
                    onClick = { confirmArchive = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Arquivar ponto")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            runCatching { AdminRepository.restore("checkpoints", checkpoint.id) }
                                .onSuccess { onChanged() }
                                .onFailure { error = it.message }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Restaurar ponto") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Arquivar ${checkpoint.name}?") },
            text = { Text("O histórico e as leituras anteriores serão preservados.") },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancelar") } },
            confirmButton = {
                Button(onClick = {
                    confirmArchive = false
                    scope.launch {
                        runCatching { AdminRepository.archive("checkpoints", checkpoint.id) }
                            .onSuccess { onChanged() }
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
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    placeholder = { Text("Ex.: Hall do elevador") },
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
                            .onFailure { error = it.message }
                        loading = false
                    }
                },
                enabled = name.isNotBlank() && !loading,
            ) { Text(if (loading) "Salvando..." else "Adicionar") }
        },
    )
}
