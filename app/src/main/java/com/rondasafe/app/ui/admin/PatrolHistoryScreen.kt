package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.GuardRepository
import com.rondasafe.app.data.repository.PatrolHistoryRepository
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrolHistoryScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<PatrolHistoryItemDto>>(emptyList()) }
    var guards by remember { mutableStateOf<List<GuardDto>>(emptyList()) }
    var buildings by remember { mutableStateOf<List<BuildingDto>>(emptyList()) }
    var blocks by remember { mutableStateOf<List<BlockDto>>(emptyList()) }
    var floors by remember { mutableStateOf<List<FloorDto>>(emptyList()) }

    var days by remember { mutableIntStateOf(30) }
    var customRange by remember { mutableStateOf(false) }
    var customFrom by remember { mutableStateOf(LocalDate.now().minusDays(30)) }
    var customTo by remember { mutableStateOf(LocalDate.now()) }
    var datePickerTarget by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var guard by remember { mutableStateOf<GuardDto?>(null) }
    var building by remember { mutableStateOf<BuildingDto?>(null) }
    var block by remember { mutableStateOf<BlockDto?>(null) }
    var floor by remember { mutableStateOf<FloorDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                if (customRange) {
                    val zone = ZoneId.systemDefault()
                    PatrolHistoryRepository.listRange(
                        from = customFrom.atStartOfDay(zone).toInstant(),
                        to = customTo.plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1),
                        status = status,
                        guardId = guard?.id,
                        buildingId = building?.id,
                        blockId = block?.id,
                        floorId = floor?.id,
                    )
                } else {
                    PatrolHistoryRepository.list(
                        days = days,
                        status = status,
                        guardId = guard?.id,
                        buildingId = building?.id,
                        blockId = block?.id,
                        floorId = floor?.id,
                    )
                }
            }.onSuccess { rows = it }
                .onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            guards = GuardRepository.list(includeArchived = true)
            buildings = AdminRepository.listBuildings(includeArchived = true)
        }.onFailure { error = it.message }
        reload()
    }

    LaunchedEffect(building?.id) {
        block = null
        floor = null
        blocks = building?.let { runCatching { AdminRepository.listBlocks(it.id, includeArchived = true) }.getOrDefault(emptyList()) } ?: emptyList()
        floors = emptyList()
    }

    LaunchedEffect(block?.id) {
        floor = null
        floors = block?.let { runCatching { AdminRepository.listFloors(it.id, includeArchived = true) }.getOrDefault(emptyList()) } ?: emptyList()
    }

    Scaffold(topBar = { AppTopBar("Histórico de rondas", onBack) }) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp, vertical = 12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Período", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 30, 90).forEach { option ->
                    FilterChip(
                        selected = !customRange && days == option,
                        onClick = { customRange = false; days = option; reload() },
                        label = { Text("$option dias") },
                    )
                }
                FilterChip(
                    selected = customRange,
                    onClick = { customRange = true },
                    label = { Text("Personalizado") },
                )
            }

            if (customRange) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { datePickerTarget = "from" }, modifier = Modifier.weight(1f)) {
                        Text("De ${formatLocalDate(customFrom)}")
                    }
                    OutlinedButton(onClick = { datePickerTarget = "to" }, modifier = Modifier.weight(1f)) {
                        Text("Até ${formatLocalDate(customTo)}")
                    }
                }
                Button(onClick = { reload() }, enabled = !customFrom.isAfter(customTo), modifier = Modifier.fillMaxWidth()) {
                    Text("Aplicar período")
                }
            }

            FilterMenu(
                label = "Status",
                selected = status?.let(::historyStatusLabel) ?: "Todos",
                options = listOf(
                    null to "Todos",
                    "COMPLETED" to "Concluída",
                    "LATE" to "Atrasada",
                    "INCOMPLETE" to "Incompleta",
                    "MISSED" to "Não realizada",
                    "IN_PROGRESS" to "Em andamento",
                ),
                onSelected = { status = it; reload() },
            )

            FilterMenu(
                label = "Porteiro",
                selected = guard?.name ?: "Todos",
                options = listOf(null to "Todos") + guards.map { it to it.name },
                onSelected = { guard = it; reload() },
            )

            FilterMenu(
                label = "Prédio",
                selected = building?.name ?: "Todos",
                options = listOf(null to "Todos") + buildings.map { it to it.name },
                onSelected = { building = it; reload() },
            )

            if (building != null) {
                FilterMenu(
                    label = "Bloco",
                    selected = block?.name ?: "Todos",
                    options = listOf(null to "Todos") + blocks.map { it to it.name },
                    onSelected = { block = it; reload() },
                )
            }

            if (block != null) {
                FilterMenu(
                    label = "Andar",
                    selected = floor?.name ?: "Todos",
                    options = listOf(null to "Todos") + floors.map { it to it.name },
                    onSelected = { floor = it; reload() },
                )
            }

            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (!loading && rows.isEmpty()) Text("Nenhuma ronda encontrada para os filtros selecionados.")

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(rows, key = { it.id }) { item -> PatrolHistoryCard(item) }
            }
        }
    }

    datePickerTarget?.let { target ->
        val current = if (target == "from") customFrom else customTo
        val zone = ZoneId.systemDefault()
        val state = rememberDatePickerState(initialSelectedDateMillis = current.atStartOfDay(zone).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        if (target == "from") customFrom = selected else customTo = selected
                    }
                    datePickerTarget = null
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { datePickerTarget = null }) { Text("Cancelar") } },
        ) { DatePicker(state = state) }
    }
}

@Composable
private fun PatrolHistoryCard(item: PatrolHistoryItemDto) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var loadingPoints by remember { mutableStateOf(false) }
    var points by remember { mutableStateOf<List<PatrolHistoryPointDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(
        onClick = {
            expanded = !expanded
            if (expanded && points.isEmpty() && !loadingPoints) {
                scope.launch {
                    loadingPoints = true
                    error = null
                    runCatching { PatrolHistoryRepository.points(item) }
                        .onSuccess { points = it }
                        .onFailure { error = it.message }
                    loadingPoints = false
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.patrolName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(historyStatusLabel(item.displayStatus)) })
            }
            Text(item.guardName ?: "Sem porteiro definido", style = MaterialTheme.typography.bodyMedium)
            Text(item.buildingName, style = MaterialTheme.typography.bodySmall)
            Text("Prevista: ${historyDate(item.scheduledFor)}", style = MaterialTheme.typography.bodySmall)
            Text("Pontos: ${item.visitedPoints}/${item.requiredPoints}", style = MaterialTheme.typography.bodySmall)

            if (item.capturedOffline || item.suspicious) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (item.capturedOffline) Text("Offline", style = MaterialTheme.typography.labelSmall)
                    if (item.suspicious) Text("Suspeita", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }

            if (expanded) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                item.startedAt?.let { Text("Início: ${historyDate(it)}", style = MaterialTheme.typography.bodySmall) }
                item.finishedAt?.let { Text("Fim: ${historyDate(it)}", style = MaterialTheme.typography.bodySmall) }
                if (item.missingPoints > 0) {
                    Text("${item.missingPoints} ponto(s) obrigatório(s) faltante(s)", color = MaterialTheme.colorScheme.error)
                }
                if (loadingPoints) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                points.forEach { point ->
                    val mark = if (point.visited) "✓" else "○"
                    Text(
                        "$mark ${point.blockName} > ${point.floorName} > ${point.checkpointName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (point.visited) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> FilterMenu(
    label: String,
    selected: String,
    options: List<Pair<T?, String>>,
    onSelected: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = { expanded = false; onSelected(value) },
                )
            }
        }
    }
}

private fun historyStatusLabel(status: String): String = when (status) {
    "COMPLETED" -> "Concluída"
    "LATE" -> "Atrasada"
    "INCOMPLETE" -> "Incompleta"
    "MISSED" -> "Não realizada"
    "IN_PROGRESS" -> "Em andamento"
    else -> status
}

private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun historyDate(value: String): String = runCatching {
    Instant.parse(value).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)
}.getOrElse { value.replace("T", " ").substringBefore(".").replace("Z", "") }

private fun formatLocalDate(value: LocalDate): String = value.format(dateFormatter)
