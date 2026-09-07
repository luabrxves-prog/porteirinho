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
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var addFloorOpen by remember { mutableStateOf(false) }
    var archiveTarget by remember { mutableStateOf<FloorDto?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        error = null
        runCatching {
            val condominium = AdminRepository.condominium()
            condominium to AdminRepository.defaultBlocks()
        }.onSuccess { (condominium, defaultBlocks) ->
            building = condominium
            blocks = defaultBlocks
            if (selectedBlock == null || defaultBlocks.none { it.id == selectedBlock?.id }) selectedBlock = defaultBlocks.firstOrNull()
        }.onFailure { error = it.message }
        loading = false
    }

    LaunchedEffect(selectedBlock?.id, refresh) {
        val block = selectedBlock ?: return@LaunchedEffect
        runCatching { AdminRepository.listFloors(block.id, includeArchived = false).filter { it.active } }
            .onSuccess { floors = it }
            .onFailure { error = it.message }
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
                    "Organize os andares e pontos de controle de cada bloco.",
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, RondaSafeColors.Border),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Apartment, null, tint = RondaSafeColors.Blue)
                            Spacer(Modifier.width(8.dp))
                            Text("Bloco", fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            blocks.forEach { block ->
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

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    SectionHeading("Andares", "${floors.size} ativo(s)")
                }
            }

            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

            if (!loading && floors.isEmpty()) {
                item { EmptyStateCard("Nenhum andar cadastrado", "Adicione o primeiro andar deste bloco.", Icons.Rounded.Layers) }
            }

            items(floors, key = { it.id }) { floor ->
                Card(
                    onClick = {
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
                        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = RondaSafeColors.BlueSoft) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Layers, null, tint = RondaSafeColors.Navy) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(floor.name, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                            Text("Gerenciar pontos e QR Codes", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                        }
                        IconButton(onClick = { archiveTarget = floor }) {
                            Icon(Icons.Rounded.Archive, contentDescription = "Arquivar", tint = RondaSafeColors.Muted)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = RondaSafeColors.Muted)
                    }
                }
            }

            item {
                Button(
                    onClick = { addFloorOpen = true },
                    enabled = selectedBlock != null,
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
                AdminRepository.createFloor(block.id, name)
                addFloorOpen = false
                refresh++
            },
        )
    }

    archiveTarget?.let { floor ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            title = { Text("Arquivar ${floor.name}?") },
            text = { Text("O andar sairá da lista ativa e ficará disponível em Arquivados.") },
            dismissButton = { TextButton(onClick = { archiveTarget = null }) { Text("Cancelar") } },
            confirmButton = {
                Button(onClick = {
                    archiveTarget = null
                    scope.launch {
                        runCatching { AdminRepository.archive("floors", floor.id) }
                            .onSuccess { refresh++ }
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
                        runCatching { onConfirm(name.trim()) }.onFailure { error = it.message }
                        loading = false
                    }
                },
                enabled = name.isNotBlank() && !loading,
            ) { Text(if (loading) "Salvando..." else "Adicionar") }
        },
    )
}
