package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.GuardRepository
import com.rondasafe.app.data.repository.PatrolHistoryRepository
import com.rondasafe.app.presentation.admin.viewmodel.PatrolHistoryViewModel
import com.rondasafe.app.ui.components.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val patrolHistoryZone = ZoneId.of("America/Sao_Paulo")
private val historyDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
private val localDateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrolHistoryScreen(
    onBack: () -> Unit,
    viewModel: PatrolHistoryViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var guards by remember { mutableStateOf<List<GuardDto>>(emptyList()) }
    var blocks by remember { mutableStateOf<List<BlockDto>>(emptyList()) }
    var floors by remember { mutableStateOf<List<FloorDto>>(emptyList()) }
    var configError by remember { mutableStateOf<String?>(null) }

    var days by remember { mutableIntStateOf(0) }
    var customRange by remember { mutableStateOf(false) }
    var customFrom by remember { mutableStateOf(LocalDate.now(patrolHistoryZone).minusDays(7)) }
    var customTo by remember { mutableStateOf(LocalDate.now(patrolHistoryZone)) }
    var datePickerTarget by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var guard by remember { mutableStateOf<GuardDto?>(null) }
    var block by remember { mutableStateOf<BlockDto?>(null) }
    var floor by remember { mutableStateOf<FloorDto?>(null) }
    var showLocationFilters by remember { mutableStateOf(false) }

    fun reload() {
        val today = LocalDate.now(patrolHistoryZone)
        val fromDate = when {
            customRange -> customFrom
            days == 0 -> today
            else -> today.minusDays((days - 1).toLong())
        }
        val toDate = if (customRange) customTo else today
        viewModel.load(
            from = fromDate.atStartOfDay(patrolHistoryZone).toInstant(),
            to = toDate.plusDays(1).atStartOfDay(patrolHistoryZone).toInstant().minusMillis(1),
            status = status,
            guardId = guard?.id,
            blockId = block?.id,
            floorId = floor?.id,
        )
    }

    LaunchedEffect(Unit) {
        runCatching {
            guards = GuardRepository.list(includeArchived = true)
            blocks = AdminRepository.defaultBlocks()
        }.onFailure { configError = it.message }
        reload()
    }

    LaunchedEffect(block?.id) {
        floor = null
        floors = block?.let {
            runCatching { AdminRepository.listFloors(it.id, includeArchived = true) }.getOrDefault(emptyList())
        } ?: emptyList()
    }

    val viewingToday = !customRange && days == 0

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Histórico de rondas", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeading(
                    if (viewingToday) "Rondas de hoje" else "Rondas do período",
                    "Os registros são carregados em páginas, do mais recente para o mais antigo.",
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, RondaSafeColors.Border),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.CalendarMonth, null, tint = RondaSafeColors.Blue)
                            Spacer(Modifier.width(8.dp))
                            Text("Período", fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Hoje", 7 to "7 dias", 30 to "30 dias").forEach { (value, label) ->
                                FilterChip(
                                    selected = !customRange && days == value,
                                    onClick = { customRange = false; days = value; reload() },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { customRange = !customRange },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (customRange) "Fechar outro período" else "Ver outro período") }
                        if (customRange) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DateRangeButton("De", customFrom, Modifier.weight(1f)) { datePickerTarget = "from" }
                                DateRangeButton("Até", customTo, Modifier.weight(1f)) { datePickerTarget = "to" }
                            }
                            Button(
                                onClick = ::reload,
                                enabled = !customFrom.isAfter(customTo),
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Aplicar período") }
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, RondaSafeColors.Border),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.FilterAlt, null, tint = RondaSafeColors.Blue)
                            Spacer(Modifier.width(8.dp))
                            Text("Filtros", fontWeight = FontWeight.Bold)
                        }
                        HistoryFilterMenu(
                            label = "Status",
                            selected = status?.let(::historyStatusLabel) ?: "Todos os status",
                            options = listOf(
                                null to "Todos os status",
                                "COMPLETED" to "Concluída",
                                "LATE" to "Atrasada",
                                "INCOMPLETE" to "Incompleta",
                                "MISSED" to "Não realizada",
                                "IN_PROGRESS" to "Em andamento",
                            ),
                        ) { status = it; reload() }
                        HistoryFilterMenu(
                            label = "Porteiro",
                            selected = guard?.name ?: "Todos os porteiros",
                            options = listOf(null to "Todos os porteiros") + guards.map { it to it.name },
                        ) { guard = it; reload() }
                        OutlinedButton(
                            onClick = { showLocationFilters = !showLocationFilters },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (showLocationFilters) "Ocultar bloco e andar" else "Filtrar por bloco e andar") }
                        if (showLocationFilters) {
                            HistoryFilterMenu(
                                label = "Bloco",
                                selected = block?.name ?: "Todos os blocos",
                                options = listOf(null to "Todos os blocos") + blocks.map { it to it.name },
                            ) { block = it; reload() }
                            block?.let {
                                HistoryFilterMenu(
                                    label = "Andar",
                                    selected = floor?.name ?: "Todos os andares",
                                    options = listOf(null to "Todos os andares") + floors.map { it to it.name },
                                ) { floor = it; reload() }
                            }
                        }
                    }
                }
            }

            if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            (state.error ?: configError)?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!state.loading && state.items.isEmpty()) {
                item {
                    EmptyStateCard(
                        if (viewingToday) "Nenhuma ronda prevista para hoje" else "Nenhuma ronda encontrada",
                        "Não há registros para o período e filtros selecionados.",
                        Icons.Rounded.History,
                    )
                }
            }
            items(state.items, key = { it.id }) { item -> PatrolHistoryCard(item) }
            if (state.hasMore) {
                item {
                    OutlinedButton(
                        onClick = viewModel::loadMore,
                        enabled = !state.loadingMore,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.loadingMore) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("Carregar mais rondas")
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    datePickerTarget?.let { target ->
        val current = if (target == "from") customFrom else customTo
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = current.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
                        if (target == "from") customFrom = selected else customTo = selected
                    }
                    datePickerTarget = null
                }) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { datePickerTarget = null }) { Text("Cancelar") } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun DateRangeButton(label: String, date: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.height(58.dp)) {
        Column(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Muted)
            Text(date.format(localDateFormatter), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun <T> HistoryFilterMenu(
    label: String,
    selected: String,
    options: List<Pair<T?, String>>,
    onSelected: (T?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Muted)
                Text(selected, maxLines = 1)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { expanded = false; onSelected(value) })
            }
        }
    }
}

@Composable
private fun PatrolHistoryCard(item: PatrolHistoryItemDto) {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var loadingPoints by remember { mutableStateOf(false) }
    var points by remember { mutableStateOf<List<PatrolHistoryPointDto>>(emptyList()) }
    var pointError by remember { mutableStateOf<String?>(null) }

    Card(
        onClick = {
            expanded = !expanded
            if (expanded && points.isEmpty() && !loadingPoints) {
                scope.launch {
                    loadingPoints = true
                    runCatching { PatrolHistoryRepository.points(item) }
                        .onSuccess { points = it }
                        .onFailure { pointError = it.message }
                    loadingPoints = false
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, RondaSafeColors.Border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(item.patrolName, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                    Text(item.guardName ?: "Sem porteiro definido", color = RondaSafeColors.Muted)
                }
                StatusPill(item.displayStatus)
            }
            Text(historyScheduledLabel(item.scheduledFor), style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
            Text("${item.visitedPoints} de ${item.requiredPoints} pontos visitados", fontWeight = FontWeight.SemiBold)
            if (expanded) {
                item.startedAt?.let { Text("Início: ${historyDate(it)}", style = MaterialTheme.typography.bodySmall) }
                item.finishedAt?.let { Text("Fim: ${historyDate(it)}", style = MaterialTheme.typography.bodySmall) }
                if (item.capturedOffline) Text("Sincronizada após uso offline", style = MaterialTheme.typography.bodySmall)
                if (item.suspicious) Text("Precisa de revisão", color = RondaSafeColors.Danger)
                if (loadingPoints) LinearProgressIndicator(Modifier.fillMaxWidth())
                pointError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                points.forEach { point ->
                    Text(
                        "${if (point.visited) "✓" else "○"} ${point.blockName} • ${point.floorName} • ${point.checkpointName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (point.visited) RondaSafeColors.Text else RondaSafeColors.Danger,
                    )
                }
            } else {
                Text("Toque para ver detalhes", style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Blue)
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = when (status) {
        "COMPLETED" -> RondaSafeColors.Green
        "LATE" -> Color(0xFFE29019)
        "INCOMPLETE", "MISSED" -> RondaSafeColors.Danger
        else -> RondaSafeColors.Blue
    }
    Surface(shape = RoundedCornerShape(50), color = color.copy(alpha = .12f)) {
        Text(
            historyStatusLabel(status),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
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

private fun historyDate(value: String): String = runCatching {
    Instant.parse(value).atZone(patrolHistoryZone).format(historyDateFormatter)
}.getOrDefault(value)

private fun historyScheduledLabel(value: String): String = runCatching {
    val zdt = Instant.parse(value).atZone(patrolHistoryZone)
    val today = LocalDate.now(patrolHistoryZone)
    if (zdt.toLocalDate() == today) "Prevista para ${zdt.format(DateTimeFormatter.ofPattern("HH:mm"))}"
    else "Prevista em ${zdt.format(historyDateFormatter)}"
}.getOrDefault(value)
