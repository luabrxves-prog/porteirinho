package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.PatrolDayConfig
import com.rondasafe.app.data.model.PatrolScheduleWindowDto
import com.rondasafe.app.data.model.PatrolTemplateDto
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.PatrolRepository
import com.rondasafe.app.ui.components.RondaSafeColors
import kotlinx.coroutines.launch

private val dayNames = mapOf(
    1 to "Seg",
    2 to "Ter",
    3 to "Qua",
    4 to "Qui",
    5 to "Sex",
    6 to "Sáb",
    7 to "Dom",
)

@Composable
fun PatrolTemplatesScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (PatrolTemplateDto) -> Unit,
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
        containerColor = RondaSafeColors.Background,
        topBar = { AppTopBar("Programações de rondas", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Rondas", style = MaterialTheme.typography.headlineSmall, color = RondaSafeColors.Navy)
                        Text("Horários simples e todos os pontos incluídos.", color = RondaSafeColors.Muted)
                    }
                    Switch(checked = includeArchived, onCheckedChange = { includeArchived = it })
                }
            }

            item {
                Button(
                    onClick = onCreate,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("+ Nova ronda", fontWeight = FontWeight.Bold) }
            }

            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!loading && templates.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = RondaSafeColors.BlueSoft,
                    ) {
                        Text(
                            "Nenhuma ronda cadastrada ainda. Crie a primeira programação em poucos passos.",
                            modifier = Modifier.padding(18.dp),
                            color = RondaSafeColors.Navy,
                        )
                    }
                }
            }

            items(templates, key = { it.id }) { template ->
                PatrolTemplateCard(template = template, onEdit = { onEdit(template) }, onChanged = ::reload)
            }
            item { Spacer(Modifier.height(28.dp)) }
        }
    }
}

@Composable
private fun PatrolTemplateCard(
    template: PatrolTemplateDto,
    onEdit: () -> Unit,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var windows by remember(template.id) { mutableStateOf(emptyList<PatrolScheduleWindowDto>()) }
    var checkpointCount by remember(template.id) { mutableStateOf(0) }
    var confirmArchive by remember { mutableStateOf(false) }

    LaunchedEffect(template.id, template.active) {
        windows = runCatching { PatrolRepository.listWindows(template.id) }.getOrDefault(emptyList())
        checkpointCount = runCatching { PatrolRepository.listTemplateCheckpoints(template.id) }.getOrDefault(emptyList()).size
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(template.name, style = MaterialTheme.typography.titleLarge, color = RondaSafeColors.Text)
                    val first = windows.firstOrNull()
                    if (first != null) {
                        Text(
                            "${first.startTime.take(5)} – ${first.endTime.take(5)} • $checkpointCount pontos",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RondaSafeColors.Muted,
                        )
                    }
                }
                AssistChip(onClick = {}, label = { Text(if (template.active) "Ativa" else "Arquivada") })
            }

            if (windows.isNotEmpty()) {
                Text(
                    windows.joinToString(" • ") { dayNames[it.dayOfWeek].orEmpty() },
                    style = MaterialTheme.typography.bodySmall,
                    color = RondaSafeColors.Muted,
                )
            }

            if (template.active) {
                Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Editar programação") }
                OutlinedButton(onClick = { confirmArchive = true }, modifier = Modifier.fillMaxWidth()) { Text("Arquivar ronda") }
            } else {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            PatrolRepository.restoreTemplate(template.id)
                            onChanged()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Restaurar ronda") }
            }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Arquivar ronda?") },
            text = { Text("Ela deixará de aparecer para novas execuções. O histórico será preservado.") },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreatePatrolTemplateScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    template: PatrolTemplateDto? = null,
) {
    val scope = rememberCoroutineScope()
    val editing = template != null
    var buildingId by remember { mutableStateOf(template?.buildingId) }
    var buildingName by remember { mutableStateOf("Condomínio") }
    var name by remember { mutableStateOf(template?.name.orEmpty()) }
    var frequency by remember { mutableStateOf("TODOS") }
    var shift by remember { mutableStateOf("NOITE") }
    var startTime by remember { mutableStateOf("22:00") }
    var endTime by remember { mutableStateOf("06:00") }
    var tolerance by remember { mutableStateOf("15") }
    var showAdvanced by remember { mutableStateOf(false) }
    var pointCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var initialLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val customDays = remember { mutableStateListOf(1, 2, 3, 4, 5, 6, 7) }

    LaunchedEffect(template?.id) {
        initialLoading = true
        runCatching {
            val condominium = AdminRepository.condominium()
            buildingId = condominium.id
            buildingName = condominium.name
            pointCount = PatrolRepository.allActiveCheckpointIds(condominium.id).size

            if (template != null) {
                val edit = PatrolRepository.loadForEdit(template)
                val activeDays = edit.windows.map { it.dayOfWeek }.sorted()
                frequency = when (activeDays) {
                    listOf(1, 2, 3, 4, 5, 6, 7) -> "TODOS"
                    listOf(1, 2, 3, 4, 5) -> "SEMANA"
                    listOf(6, 7) -> "FIM_SEMANA"
                    else -> "PERSONALIZADO"
                }
                customDays.clear()
                customDays.addAll(activeDays)
                edit.windows.firstOrNull()?.let { window ->
                    startTime = window.startTime.take(5)
                    endTime = window.endTime.take(5)
                    tolerance = window.lateToleranceMinutes.toString()
                    shift = when (startTime to endTime) {
                        "06:00" to "14:00" -> "MANHA"
                        "14:00" to "22:00" -> "TARDE"
                        "22:00" to "06:00" -> "NOITE"
                        else -> "PERSONALIZADO"
                    }
                }
            }
        }.onFailure { error = it.message ?: "Não foi possível preparar a ronda." }
        initialLoading = false
    }

    fun selectShift(value: String) {
        shift = value
        when (value) {
            "MANHA" -> { startTime = "06:00"; endTime = "14:00" }
            "TARDE" -> { startTime = "14:00"; endTime = "22:00" }
            "NOITE" -> { startTime = "22:00"; endTime = "06:00" }
        }
    }

    fun selectedDays(): List<Int> = when (frequency) {
        "TODOS" -> (1..7).toList()
        "SEMANA" -> (1..5).toList()
        "FIM_SEMANA" -> listOf(6, 7)
        else -> customDays.toList().sorted()
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { AppTopBar(if (editing) "Editar ronda" else "Nova ronda", onBack = onBack) },
    ) { padding ->
        if (initialLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = RondaSafeColors.BlueSoft,
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Todos os pontos entram automaticamente", fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "$pointCount ponto(s) ativo(s) dos Blocos A e B serão incluídos nesta ronda.",
                            color = RondaSafeColors.Muted,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome da ronda") },
                    placeholder = { Text("Ex.: Ronda Noturna") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Text("Turno", style = MaterialTheme.typography.titleMedium, color = RondaSafeColors.Navy)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("MANHA" to "Manhã", "TARDE" to "Tarde", "NOITE" to "Noite").forEach { (value, label) ->
                        FilterChip(
                            selected = shift == value,
                            onClick = { selectShift(value) },
                            label = { Text(label) },
                        )
                    }
                }
            }

            item {
                Text("Frequência", style = MaterialTheme.typography.titleMedium, color = RondaSafeColors.Navy)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "TODOS" to "Todos os dias",
                        "SEMANA" to "Seg a Sex",
                        "FIM_SEMANA" to "Fim de semana",
                        "PERSONALIZADO" to "Personalizar",
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = frequency == value,
                            onClick = { frequency = value },
                            label = { Text(label) },
                        )
                    }
                }
            }

            if (frequency == "PERSONALIZADO") {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        (1..7).forEach { day ->
                            FilterChip(
                                selected = day in customDays,
                                onClick = {
                                    if (day in customDays) customDays.remove(day) else customDays.add(day)
                                },
                                label = { Text(dayNames[day].orEmpty()) },
                            )
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it.filter { ch -> ch.isDigit() || ch == ':' }.take(5); shift = "PERSONALIZADO" },
                        label = { Text("Início") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it.filter { ch -> ch.isDigit() || ch == ':' }.take(5); shift = "PERSONALIZADO" },
                        label = { Text("Fim") },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (endTime <= startTime) {
                    Text("A ronda termina no dia seguinte.", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Opções avançadas", fontWeight = FontWeight.SemiBold)
                                Text("Ajuste a tolerância de atraso se necessário.", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                            }
                            Switch(checked = showAdvanced, onCheckedChange = { showAdvanced = it })
                        }
                        if (showAdvanced) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = tolerance,
                                onValueChange = { tolerance = it.filter(Char::isDigit).take(4) },
                                label = { Text("Tolerância (minutos)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    buildingName,
                    style = MaterialTheme.typography.bodySmall,
                    color = RondaSafeColors.Muted,
                )
            }

            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

            item {
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            error = null
                            runCatching {
                                val id = requireNotNull(buildingId) { "Condomínio não configurado." }
                                val days = selectedDays().map { day ->
                                    PatrolDayConfig(day, true, startTime, endTime)
                                }
                                PatrolRepository.saveTemplate(
                                    templateId = template?.id,
                                    buildingId = id,
                                    name = name,
                                    description = null,
                                    lateToleranceMinutes = tolerance.toIntOrNull() ?: 15,
                                    days = days,
                                    checkpointIds = emptyList(),
                                )
                            }.onSuccess { onCreated() }
                                .onFailure { error = it.message ?: "Não foi possível salvar a ronda." }
                            loading = false
                        }
                    },
                    enabled = !loading && name.isNotBlank() && buildingId != null && selectedDays().isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (loading) "Salvando..." else if (editing) "Salvar alterações" else "Criar ronda", fontWeight = FontWeight.Bold)
                }
            }
            item { Spacer(Modifier.height(30.dp)) }
        }
    }
}
