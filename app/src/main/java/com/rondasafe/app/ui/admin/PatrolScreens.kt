package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.PatrolRepository
import kotlinx.coroutines.launch

private val dayNames = mapOf(
    1 to "Segunda",
    2 to "Terça",
    3 to "Quarta",
    4 to "Quinta",
    5 to "Sexta",
    6 to "Sábado",
    7 to "Domingo",
)

@Composable
fun PatrolTemplatesScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var includeArchived by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var templates by remember { mutableStateOf(emptyList<PatrolTemplateDto>()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching { PatrolRepository.listTemplates(includeArchived) }
                .onSuccess { templates = it }
                .onFailure { error = it.message ?: "Não foi possível carregar as rondas." }
            loading = false
        }
    }

    LaunchedEffect(includeArchived) { reload() }

    Scaffold(
        topBar = { AppTopBar("Programação de Rondas", onBack = onBack) },
        floatingActionButton = { FloatingActionButton(onClick = onCreate) { Text("+") } },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = includeArchived, onCheckedChange = { includeArchived = it })
                Spacer(Modifier.width(8.dp))
                Text("Mostrar arquivadas")
            }

            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(templates, key = { it.id }) { template ->
                    PatrolTemplateCard(template = template, onChanged = ::reload)
                }
            }
        }
    }
}

@Composable
private fun PatrolTemplateCard(
    template: PatrolTemplateDto,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var windows by remember(template.id) { mutableStateOf(emptyList<PatrolScheduleWindowDto>()) }
    var checkpointCount by remember(template.id) { mutableStateOf(0) }
    var confirmArchive by remember { mutableStateOf(false) }

    LaunchedEffect(template.id) {
        windows = runCatching { PatrolRepository.listWindows(template.id) }.getOrDefault(emptyList())
        checkpointCount = runCatching { PatrolRepository.listTemplateCheckpoints(template.id) }.getOrDefault(emptyList()).size
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(template.name, style = MaterialTheme.typography.titleMedium)
                AssistChip(
                    onClick = {},
                    label = { Text(if (template.active) "Ativa" else "Arquivada") },
                )
            }
            template.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text("$checkpointCount pontos obrigatórios")

            windows.forEach { window ->
                Text(
                    "${dayNames[window.dayOfWeek]} • ${window.startTime.take(5)} às ${window.endTime.take(5)} • tolerância ${window.lateToleranceMinutes} min",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (template.active) {
                OutlinedButton(onClick = { confirmArchive = true }) { Text("Arquivar") }
            } else {
                OutlinedButton(onClick = {
                    scope.launch {
                        PatrolRepository.restoreTemplate(template.id)
                        onChanged()
                    }
                }) { Text("Restaurar") }
            }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Arquivar ronda?") },
            text = { Text("Ela deixará de aparecer para novas programações. O histórico será preservado.") },
            confirmButton = {
                Button(onClick = {
                    confirmArchive = false
                    scope.launch {
                        PatrolRepository.archiveTemplate(template.id)
                        onChanged()
                    }
                }) { Text("Arquivar") }
            },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
fun CreatePatrolTemplateScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var buildings by remember { mutableStateOf(emptyList<BuildingDto>()) }
    var selectedBuildingId by remember { mutableStateOf<String?>(null) }
    var checkpointOptions by remember { mutableStateOf(emptyList<PatrolCheckpointOption>()) }
    var selectedCheckpointIds by remember { mutableStateOf(setOf<String>()) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tolerance by remember { mutableStateOf("15") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var buildingMenu by remember { mutableStateOf(false) }

    val days = remember {
        mutableStateListOf(
            * (1..7).map { day ->
                PatrolDayConfig(day, enabled = day <= 5, startTime = "06:00", endTime = "08:00")
            }.toTypedArray()
        )
    }

    LaunchedEffect(Unit) {
        buildings = runCatching { AdminRepository.listBuildings() }.getOrDefault(emptyList())
        selectedBuildingId = buildings.firstOrNull()?.id
    }

    LaunchedEffect(selectedBuildingId) {
        checkpointOptions = selectedBuildingId?.let {
            runCatching { PatrolRepository.listCheckpointOptions(it) }.getOrDefault(emptyList())
        } ?: emptyList()
        selectedCheckpointIds = emptySet()
    }

    Scaffold(topBar = { AppTopBar("Nova ronda", onBack = onBack) }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da ronda") },
                    placeholder = { Text("Ex.: Ronda Matinal") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descrição (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Box {
                    OutlinedButton(onClick = { buildingMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(buildings.firstOrNull { it.id == selectedBuildingId }?.name ?: "Selecione o prédio")
                    }
                    DropdownMenu(expanded = buildingMenu, onDismissRequest = { buildingMenu = false }) {
                        buildings.forEach { building ->
                            DropdownMenuItem(
                                text = { Text(building.name) },
                                onClick = {
                                    selectedBuildingId = building.id
                                    buildingMenu = false
                                },
                            )
                        }
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = tolerance,
                    onValueChange = { tolerance = it.filter(Char::isDigit).take(4) },
                    label = { Text("Tolerância para atraso (minutos)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Text("Dias e horários", style = MaterialTheme.typography.titleMedium) }

            items(days.size) { index ->
                val day = days[index]
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = day.enabled,
                                onCheckedChange = { checked -> days[index] = day.copy(enabled = checked) },
                            )
                            Text(dayNames[day.dayOfWeek] ?: "Dia")
                        }
                        if (day.enabled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = day.startTime,
                                    onValueChange = { days[index] = day.copy(startTime = it.take(5)) },
                                    label = { Text("Início") },
                                    placeholder = { Text("06:00") },
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = day.endTime,
                                    onValueChange = { days[index] = day.copy(endTime = it.take(5)) },
                                    label = { Text("Fim") },
                                    placeholder = { Text("08:00") },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }

            item { Text("Pontos obrigatórios", style = MaterialTheme.typography.titleMedium) }
            if (checkpointOptions.isEmpty()) {
                item { Text("Cadastre primeiro os locais e pontos de ronda deste prédio.") }
            } else {
                items(checkpointOptions, key = { it.checkpoint.id }) { option ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = option.checkpoint.id in selectedCheckpointIds,
                            onCheckedChange = { checked ->
                                selectedCheckpointIds = if (checked) {
                                    selectedCheckpointIds + option.checkpoint.id
                                } else {
                                    selectedCheckpointIds - option.checkpoint.id
                                }
                            },
                        )
                        Text(option.label)
                    }
                }
            }

            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

            item {
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            runCatching {
                                PatrolRepository.createTemplate(
                                    buildingId = requireNotNull(selectedBuildingId) { "Selecione o prédio." },
                                    name = name,
                                    description = description,
                                    lateToleranceMinutes = tolerance.toIntOrNull() ?: 15,
                                    days = days.toList(),
                                    checkpointIds = selectedCheckpointIds.toList(),
                                )
                            }.onSuccess { onCreated() }
                                .onFailure { error = it.message ?: "Não foi possível criar a ronda." }
                            loading = false
                        }
                    },
                    enabled = !loading && name.isNotBlank() && selectedBuildingId != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (loading) "Salvando..." else "Criar ronda")
                }
            }
            item { Spacer(Modifier.height(60.dp)) }
        }
    }
}
