package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

@Composable
fun SimplifiedLocationsScreen(
    onBack: () -> Unit,
    onOpenFloor: (BuildingDto, BlockDto, FloorDto) -> Unit,
) {
    var building by remember { mutableStateOf<BuildingDto?>(null) }
    var blocks by remember { mutableStateOf<List<BlockDto>>(emptyList()) }
    var selectedBlock by remember { mutableStateOf<BlockDto?>(null) }
    var floors by remember { mutableStateOf<List<FloorDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        runCatching {
            val condominium = AdminRepository.condominium()
            val fixedBlocks = AdminRepository.defaultBlocks().filter { it.active && it.systemFixed }.sortedBy { it.sortOrder }
            condominium to fixedBlocks
        }.onSuccess { (condominium, fixedBlocks) ->
            building = condominium
            blocks = fixedBlocks
            selectedBlock = fixedBlocks.firstOrNull()
        }.onFailure {
            error = userFriendlyError(it, "Não foi possível carregar os blocos do condomínio.")
        }
        loading = false
    }

    LaunchedEffect(selectedBlock?.id) {
        val block = selectedBlock ?: run {
            floors = emptyList()
            return@LaunchedEffect
        }
        runCatching {
            AdminRepository.listFloors(block.id)
                .filter { it.active && it.systemFixed }
                .sortedBy { it.sortOrder }
        }.onSuccess { floors = it }
            .onFailure { error = userFriendlyError(it, "Não foi possível carregar os locais fixos deste bloco.") }
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
                    "Locais fixos",
                    "Blocos A e B possuem Térreo, Play, Garagem, 1º ao 11º andar e Cobertura. Abra um local para consultar o QR Code ou adicionar pontos extras.",
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = RondaSafeColors.BlueSoft,
                ) {
                    Text(
                        "Cada bloco possui 15 locais obrigatórios. Esses locais não podem ser removidos.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = RondaSafeColors.Navy,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (blocks.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        blocks.forEach { block ->
                            FilterChip(
                                selected = selectedBlock?.id == block.id,
                                onClick = { selectedBlock = block; error = null },
                                label = { Text(block.name, maxLines = 1, fontWeight = FontWeight.SemiBold) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { message ->
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(message, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (!loading && error == null && floors.isEmpty()) {
                item { EmptyStateCard("Estrutura não encontrada", "Os locais fixos deste bloco precisam ser restaurados.", Icons.Rounded.Layers) }
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
                        Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(modifier = Modifier.size(42.dp), shape = RoundedCornerShape(13.dp), color = RondaSafeColors.BlueSoft) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Layers, null, tint = RondaSafeColors.Navy) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(floor.name, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy, maxLines = 1)
                            Text("Ponto obrigatório + pontos extras", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = RondaSafeColors.Muted)
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}
