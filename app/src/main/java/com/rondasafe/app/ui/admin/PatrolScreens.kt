package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.PatrolDayConfig
import com.rondasafe.app.data.model.PatrolScheduleWindowDto
import com.rondasafe.app.data.model.PatrolTemplateDto
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.PatrolRepository
import com.rondasafe.app.ui.components.*
import kotlinx.coroutines.launch

private val dayNames = mapOf(
    1 to "Seg", 2 to "Ter", 3 to "Qua", 4 to "Qui", 5 to "Sex", 6 to "Sáb", 7 to "Dom",
)

@Composable
fun PatrolTemplatesScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (PatrolTemplateDto) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var templates by remember { mutableStateOf(emptyList<PatrolTemplateDto>()) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching { PatrolRepository.listTemplates(includeArchived = false).filter { it.active } }
                .onSuccess { templates = it }
                .onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Programações de rondas", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SectionHeading("Rondas", "Horários simples e todos os pontos incluídos automaticamente.") }
            item {
                Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Nova ronda", fontWeight = FontWeight.Bold)
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!loading && templates.isEmpty()) {
                item { EmptyStateCard("Nenhuma ronda cadastrada", "Crie a primeira programação em poucos passos.", Icons.Rounded.Schedule) }
            }
            items(templates, key = { it.id }) { template ->
                PatrolTemplateCard(template, { onEdit(template) }, ::reload)
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun PatrolTemplateCard(template: PatrolTemplateDto, onEdit: () -> Unit, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var windows by remember(template.id) { mutableStateOf(emptyList<PatrolScheduleWindowDto>()) }
    var checkpointCount by remember(template.id) { mutableStateOf(0) }
    var confirmArchive by remember { mutableStateOf(false) }

    LaunchedEffect(template.id) {
        windows = runCatching { PatrolRepository.listWindows(template.id) }.getOrDefault(emptyList())
        checkpointCount = runCatching { PatrolRepository.listTemplateCheckpoints(template.id) }.getOrDefault(emptyList()).size
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, RondaSafeColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(46.dp), shape = RoundedCornerShape(14.dp), color = RondaSafeColors.BlueSoft) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Schedule, null, tint = RondaSafeColors.Navy) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                    windows.firstOrNull()?.let { first ->
                        Text("${first.startTime.take(5)} – ${first.endTime.take(5)} • $checkpointCount pontos", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                    }
                }
                Surface(shape = RoundedCornerShape(50), color = RondaSafeColors.GreenSoft) {
                    Text("Ativa", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Green)
                }
            }
            if (windows.isNotEmpty()) {
                Text(windows.joinToString(" • ") { dayNames[it.dayOfWeek].orEmpty() }, style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
            }
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Editar programação") }
            OutlinedButton(
                onClick = { confirmArchive = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = RondaSafeColors.Danger),
            ) {
                Icon(Icons.Rounded.Archive, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Arquivar ronda", maxLines = 1)
            }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Arquivar ronda?") },
            text = { Text("Ela deixará de aparecer para novas execuções e ficará disponível em Arquivados. O histórico será preservado.") },
            confirmButton = {
                Button(onClick = {
                    confirmArchive = false
                    scope.launch { runCatching { PatrolRepository.archiveTemplate(template.id) }.onSuccess { onChanged() } }
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
    template: PatrolTemplateDto? = null,
) {
    val scope = rememberCoroutineScope()
    val editing = template != null
    var buildingId by remember { mutableStateOf(template?.buildingId) }
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
            pointCount = PatrolRepository.allActiveCheckpointIds(condominium.id).size
            if (template != null) {
                val edit = PatrolRepository.loadForEdit(template)
                val activeDays = edit.windows.map { it.dayOfWeek }.sorted()
                frequency = when (activeDays) {
                    listOf(1,2,3,4,5,6,7) -> "TODOS"
                    listOf(1,2,3,4,5) -> "SEMANA"
                    listOf(6,7) -> "FIM_SEMANA"
                    else -> "PERSONALIZADO"
                }
                customDays.clear(); customDays.addAll(activeDays)
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
        }.onFailure { error = it.message }
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
        "FIM_SEMANA" -> listOf(6,7)
        else -> customDays.toList().sorted()
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar(if (editing) "Editar ronda" else "Nova ronda", onBack) },
    ) { padding ->
        if (initialLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = RondaSafeColors.BlueSoft),
                    border = BorderStroke(1.dp, RondaSafeColors.Blue.copy(alpha = .20f)),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = RondaSafeColors.Blue) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CheckCircle, null, tint = Color.White) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Todos os pontos entram automaticamente", fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                            Text("$pointCount ponto(s) ativo(s) dos Blocos A e B serão incluídos.", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                        }
                    }
                }
            }
            item {
                OutlinedTextField(name, { name = it }, label = { Text("Nome da ronda") }, placeholder = { Text("Ex.: Ronda Noturna") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
            }
            item {
                Text("Turno", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ShiftButton("Manhã", Icons.Rounded.WbSunny, shift == "MANHA", Modifier.weight(1f)) { selectShift("MANHA") }
                    ShiftButton("Tarde", Icons.Rounded.LightMode, shift == "TARDE", Modifier.weight(1f)) { selectShift("TARDE") }
                    ShiftButton("Noite", Icons.Rounded.DarkMode, shift == "NOITE", Modifier.weight(1f)) { selectShift("NOITE") }
                }
            }
            item {
                Text("Frequência", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FrequencyButton("Diária", frequency == "TODOS", Modifier.weight(1f)) { frequency = "TODOS" }
                    FrequencyButton("Seg a Sex", frequency == "SEMANA", Modifier.weight(1f)) { frequency = "SEMANA" }
                    FrequencyButton("Fim de semana", frequency == "FIM_SEMANA", Modifier.weight(1f)) { frequency = "FIM_SEMANA" }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { frequency = "PERSONALIZADO" }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Text("Personalizar dias", maxLines = 1)
                }
            }
            if (frequency == "PERSONALIZADO") {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..7).forEach { day ->
                            FilterChip(
                                selected = day in customDays,
                                onClick = { if (day in customDays) customDays.remove(day) else customDays.add(day) },
                                label = { Text(dayNames[day].orEmpty(), maxLines = 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(startTime, { startTime = it.filter { ch -> ch.isDigit() || ch == ':' }.take(5); shift = "PERSONALIZADO" }, label = { Text("Início") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
                    OutlinedTextField(endTime, { endTime = it.filter { ch -> ch.isDigit() || ch == ':' }.take(5); shift = "PERSONALIZADO" }, label = { Text("Fim") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp))
                }
                if (endTime <= startTime) Text("Termina no dia seguinte", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
            }
            item {
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, RondaSafeColors.Border)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Opções avançadas", fontWeight = FontWeight.Bold)
                                Text("Tolerância de atraso", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                            }
                            Switch(checked = showAdvanced, onCheckedChange = { showAdvanced = it })
                        }
                        if (showAdvanced) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(tolerance, { tolerance = it.filter(Char::isDigit).take(4) }, label = { Text("Tolerância (minutos)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp))
                        }
                    }
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            loading = true; error = null
                            runCatching {
                                val id = requireNotNull(buildingId) { "Condomínio não configurado." }
                                PatrolRepository.saveTemplate(
                                    templateId = template?.id,
                                    buildingId = id,
                                    name = name,
                                    description = null,
                                    lateToleranceMinutes = tolerance.toIntOrNull() ?: 15,
                                    days = selectedDays().map { PatrolDayConfig(it, true, startTime, endTime) },
                                    checkpointIds = emptyList(),
                                )
                            }.onSuccess { onCreated() }.onFailure { error = it.message }
                            loading = false
                        }
                    },
                    enabled = !loading && name.isNotBlank() && buildingId != null && selectedDays().isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text(if (loading) "Salvando..." else if (editing) "Salvar alterações" else "Criar ronda", fontWeight = FontWeight.Bold) }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun ShiftButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.height(68.dp), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(6.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null); Spacer(Modifier.height(3.dp)); Text(label, maxLines = 1) }
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.height(68.dp), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(6.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null); Spacer(Modifier.height(3.dp)); Text(label, maxLines = 1) }
        }
    }
}

@Composable
private fun FrequencyButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label, maxLines = 1) }, modifier = modifier)
}
