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
    var block by remember { mutableStateOf<BlockDto?>(null) }
    var floors by remember { mutableStateOf<List<FloorDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        runCatching {
            val condominium = AdminRepository.condominium()
            val fixedBlock = AdminRepository.defaultBlocks().firstOrNull()
                ?: error("Estrutura fixa do condomínio não encontrada.")
            val fixedFloors = AdminRepository.listFloors(fixedBlock.id)
                .filter { it.active && it.systemFixed }
                .sortedBy { it.sortOrder }
            Triple(condominium, fixedBlock, fixedFloors)
        }.onSuccess { (condominium, fixedBlock, fixedFloors) ->
            building = condominium
            block = fixedBlock
            floors = fixedFloors
        }.onFailure {
            error = userFriendlyError(it, "Não foi possível carregar os locais fixos do condomínio.")
        }
        loading = false
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
                    "Térreo, Play, Garagem e os 11 andares já fazem parte da operação. Abra um local para consultar o QR Code ou adicionar pontos extras.",
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = RondaSafeColors.BlueSoft,
                ) {
                    Text(
                        "Esses locais são obrigatórios e não podem ser removidos.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = RondaSafeColors.Navy,
                        fontWeight = FontWeight.SemiBold,
                    )
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
                item { EmptyStateCard("Estrutura não encontrada", "Os locais fixos precisam ser restaurados.", Icons.Rounded.Layers) }
            }

            items(floors, key = { it.id }) { floor ->
                Card(
                    onClick = {
                        val condominium = building ?: return@Card
                        val fixedBlock = block ?: return@Card
                        onOpenFloor(condominium, fixedBlock, floor)
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
