package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.BlockDto
import com.rondasafe.app.data.model.BuildingDto
import com.rondasafe.app.data.model.FloorDto
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.ui.components.*
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
    var loading by remember { mutableStateOf(true) }
    var floorsLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var addFloorOpen by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<FloorDto?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        runCatching {
            val condominium = AdminRepository.condominium()
            val defaultBlocks = AdminRepository.defaultBlocks()
            condominium to defaultBlocks
        }.onSuccess { (condominium, defaultBlocks) ->
            building = condominium
            blocks = defaultBlocks
            selectedBlock = defaultBlocks.firstOrNull()
        }.onFailure {
            error = userFriendlyError(it, "Não foi possível carregar os locais do condomínio.")
        }
        loading = false
    }

    LaunchedEffect(selectedBlock?.id) {
        val block = selectedBlock ?: run {
            floors = emptyList()
            return@LaunchedEffect
        }
        floorsLoading = true
        error = null
        runCatching { AdminRepository.listFloors(block.id).filter { it.active } }
            .onSuccess { floors = it }
            .onFailure { error = userFriendlyError(it, "Não foi possível carregar os andares deste bloco.") }
        floorsLoading = false
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Locais e QR Codes", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeading(
                    building?.name ?: "Condomínio",
                    "Escolha o bloco e depois o andar para gerenciar os pontos.",
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, RondaSafeColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Apartment, null, tint = RondaSafeColors.Blue)
                            Spacer(Modifier.width(8.dp))
                            Text("Bloco", fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            blocks.take(2).forEach { block ->
                                FilterChip(
                                    selected = selectedBlock?.id == block.id,
                                    onClick = { selectedBlock = block },
                                    label = { Text(block.name, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            item { SectionHeading("Andares", if (floorsLoading) "Carregando..." else "${floors.size} cadastrado(s)") }

            if (loading || floorsLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            it,
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            if (!loading && !floorsLoading && error == null && floors.isEmpty()) {
                item { EmptyStateCard("Nenhum andar cadastrado", "Adicione o primeiro andar deste bloco.", Icons.Rounded.Layers) }
            }

            items(floors, key = { it.id }) { floor ->
                val removing = removingId == floor.id
                Card(
                    onClick = {
                        if (removing) return@Card
                        val condominium = building ?: return@Card
                        val block = selectedBlock ?: return@Card
                        onOpenFloor(condominium, block, floor)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, RondaSafeColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = RondaSafeColors.BlueSoft) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Layers, null, tint = RondaSafeColors.Navy) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(floor.name, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy, maxLines = 1)
                            Text("Ver pontos de ronda", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted, maxLines = 1)
                        }
                        if (removing) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { removeTarget = floor }) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remover andar", tint = RondaSafeColors.Muted)
                            }
                            Icon(Icons.Rounded.ChevronRight, null, tint = RondaSafeColors.Muted)
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { addFloorOpen = true },
                    enabled = selectedBlock != null && !loading && !floorsLoading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Adicionar andar", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (addFloorOpen) {
        AddFloorDialog(
            blockName = selectedBlock?.name ?: "Bloco",
            onDismiss = { addFloorOpen = false },
            onConfirm = { name ->
                val block = selectedBlock ?: error("Selecione um bloco.")
                val created = AdminRepository.createFloor(block.id, name)
                floors = (floors + created).sortedBy { it.sortOrder }
                addFloorOpen = false
                error = null
            },
        )
    }

    removeTarget?.let { floor ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remover ${floor.name}?") },
            text = { Text("O andar sairá da operação. O histórico já registrado continuará preservado.") },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancelar") } },
            confirmButton = {
                Button(onClick = {
                    removeTarget = null
                    removingId = floor.id
                    val previous = floors
                    floors = floors.filterNot { it.id == floor.id }
                    scope.launch {
                        runCatching { AdminRepository.archive("floors", floor.id) }
                            .onFailure {
                                floors = previous
                                error = userFriendlyError(it, "Não foi possível remover este andar.")
                            }
                        removingId = null
                    }
                }) { Text("Remover") }
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
        title = { Text("Novo andar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$blockName • Ex.: Térreo, 1º andar, 2º andar.", color = RondaSafeColors.Muted)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do andar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
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
                            .onFailure { error = userFriendlyError(it, "Não foi possível adicionar este andar.") }
                        loading = false
                    }
                },
                enabled = name.isNotBlank() && !loading,
            ) { Text(if (loading) "Salvando..." else "Adicionar") }
        },
    )
}
