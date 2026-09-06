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
import androidx.compose.material3.FilterChip
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
import com.rondasafe.app.data.model.BlockDto
import com.rondasafe.app.data.model.BuildingDto
import com.rondasafe.app.data.model.FloorDto
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.ui.components.RondaSafeColors
import kotlinx.coroutines.launch

@Composable
fun SimplifiedLocationsScreen(
    onBack: () -> Unit,
    onOpenFloor: (BuildingDto, BlockDto, FloorDto) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var building by remember { mutableStateOf<BuildingDto?>(null) }
    var blocks by remember { mutableStateOf<List<BlockDto>>(emptyList()) }
    var selectedBlock by remember { mutableStateOf<BlockDto?>(null) }
    var floors by remember { mutableStateOf<List<FloorDto>>(emptyList()) }
    var includeArchived by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var addFloorOpen by remember { mutableStateOf(false) }

    LaunchedEffect(refresh) {
        loading = true
        error = null
        runCatching {
            val condominium = AdminRepository.condominium()
            val defaultBlocks = AdminRepository.defaultBlocks()
            condominium to defaultBlocks
        }.onSuccess { (condominium, defaultBlocks) ->
            building = condominium
            blocks = defaultBlocks
            if (selectedBlock == null || defaultBlocks.none { it.id == selectedBlock?.id }) {
                selectedBlock = defaultBlocks.firstOrNull()
            }
        }.onFailure { error = it.message ?: "Não foi possível carregar os locais." }
        loading = false
    }

    LaunchedEffect(selectedBlock?.id, includeArchived, refresh) {
        val block = selectedBlock ?: return@LaunchedEffect
        runCatching { AdminRepository.listFloors(block.id, includeArchived) }
            .onSuccess { floors = it }
            .onFailure { error = it.message ?: "Não foi possível carregar os andares." }
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { AppTopBar("Locais e QR Codes", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    building?.name ?: "Condomínio",
                    style = MaterialTheme.typography.titleMedium,
                    color = RondaSafeColors.Muted,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Escolha o bloco e gerencie apenas andares, pontos e QR Codes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RondaSafeColors.Muted,
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    blocks.forEach { block ->
                        FilterChip(
                            selected = selectedBlock?.id == block.id,
                            onClick = { selectedBlock = block },
                            label = { Text(block.name, fontWeight = FontWeight.SemiBold) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = RondaSafeColors.BlueSoft,
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Andares", style = MaterialTheme.typography.titleMedium, color = RondaSafeColors.Navy)
                            Text(
                                if (floors.isEmpty()) "Cadastre o primeiro andar deste bloco."
                                else "${floors.count { it.active }} andar(es) ativo(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = RondaSafeColors.Muted,
                            )
                        }
                        Text("Arquivados")
                        Spacer(Modifier.width(8.dp))
                        Switch(checked = includeArchived, onCheckedChange = { includeArchived = it })
                    }
                }
            }

            if (loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }

            if (!loading && floors.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    ) {
                        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nenhum andar cadastrado", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Adicione os andares e, dentro de cada um, os pontos que receberão QR Code.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = RondaSafeColors.Muted,
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = { addFloorOpen = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Adicionar primeiro andar")
                            }
                        }
                    }
                }
            }

            items(floors, key = { it.id }) { floor ->
                FloorManagementCard(
                    floor = floor,
                    onOpen = {
                        val condominium = building ?: return@FloorManagementCard
                        val block = selectedBlock ?: return@FloorManagementCard
                        onOpenFloor(condominium, block, floor)
                    },
                    onChanged = { refresh++ },
                )
            }

            item {
                Button(
                    onClick = { addFloorOpen = true },
                    enabled = selectedBlock != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("+ Adicionar andar")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (addFloorOpen) {
        AddFloorDialog(
            blockName = selectedBlock?.name ?: "Bloco",
            onDismiss = { addFloorOpen = false },
            onConfirm = { name ->
                val block = selectedBlock ?: error("Selecione um bloco.")
                AdminRepository.createFloor(block.id, name)
                addFloorOpen = false
                refresh++
            },
        )
    }
}

@Composable
private fun FloorManagementCard(
    floor: FloorDto,
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (floor.active) RondaSafeColors.GreenSoft else RondaSafeColors.Border,
                ) {
                    Text(
                        if (floor.active) "ATIVO" else "ARQUIVADO",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (floor.active) RondaSafeColors.Green else RondaSafeColors.Muted,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(floor.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            }

            if (floor.active) {
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text("Gerenciar pontos e QR Codes")
                }
                OutlinedButton(
                    onClick = { confirmArchive = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Arquivar andar")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            runCatching { AdminRepository.restore("floors", floor.id) }
                                .onSuccess { onChanged() }
                                .onFailure { error = it.message }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Restaurar andar")
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Arquivar ${floor.name}?") },
            text = { Text("O histórico será preservado. O andar deixará de aparecer na operação normal.") },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancelar") } },
            confirmButton = {
                Button(onClick = {
                    confirmArchive = false
                    scope.launch {
                        runCatching { AdminRepository.archive("floors", floor.id) }
                            .onSuccess { onChanged() }
                            .onFailure { error = it.message }
                    }
                }) { Text("Arquivar") }
            },
        )
    }
}

@Composable
private fun AddFloorDialog(
    blockName: String,
    onDismiss: () -> Unit,
    onConfirm: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Novo andar • $blockName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Exemplos: Térreo, 1º andar, 2º andar.", color = RondaSafeColors.Muted)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do andar") },
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
                            .onFailure { error = it.message }
                        loading = false
                    }
                },
                enabled = name.isNotBlank() && !loading,
            ) {
                Text(if (loading) "Salvando..." else "Adicionar")
            }
        },
    )
}
