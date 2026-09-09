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
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.AppTime
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.GuardRepository
import com.rondasafe.app.data.repository.PatrolHistoryRepository
import com.rondasafe.app.ui.components.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val patrolHistoryZone: ZoneId
    get() = AppTime.zone()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatrolHistoryScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<PatrolHistoryItemDto>>(emptyList()) }
    var guards by remember { mutableStateOf<List<GuardDto>>(emptyList()) }
    var blocks by remember { mutableStateOf<List<BlockDto>>(emptyList()) }
    var floors by remember { mutableStateOf<List<FloorDto>>(emptyList()) }

    // 0 = hoje. O histórico abre sempre focado na operação do dia.
    var days by remember { mutableIntStateOf(0) }
    var customRange by remember { mutableStateOf(false) }
    var customFrom by remember { mutableStateOf(LocalDate.now(patrolHistoryZone).minusDays(7)) }
    var customTo by remember { mutableStateOf(LocalDate.now(patrolHistoryZone)) }
    var datePickerTarget by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var guard by remember { mutableStateOf<GuardDto?>(null) }
    var block by remember { mutableStateOf<BlockDto?>(null) }
    var floor by remember { mutableStateOf<FloorDto?>(null) }
    var showMoreFilters by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                val today = LocalDate.now(patrolHistoryZone)
                val fromDate = when {
                    customRange -> customFrom
                    days == 0 -> today
                    else -> today.minusDays((days - 1).toLong())
                }
                val toDate = if (customRange) customTo else today

                PatrolHistoryRepository.listRange(
                    from = fromDate.atStartOfDay(patrolHistoryZone).toInstant(),
                    to = toDate.plusDays(1).atStartOfDay(patrolHistoryZone).toInstant().minusMillis(1),
                    status = status,
                    guardId = guard?.id,
                    blockId = block?.id,
                    floorId = floor?.id,
                )
            }.onSuccess { rows = it }
                .onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching {
            guards = GuardRepository.list(includeArchived = true)
            blocks = AdminRepository.defaultBlocks()
        }.onFailure { error = it.message }
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
                    if (viewingToday)
                        "Veja rapidamente se as rondas previstas para hoje estão sendo realizadas corretamente."
                    else
                        "Consulte rondas anteriores somente quando precisar verificar outro período.",
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
                            Text("Período", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0 to "Hoje", 7 to "7 dias", 30 to "30 dias").forEach { (option, label) ->
                                FilterChip(
                                    selected = !customRange && days == option,
                                    onClick = {
                                        customRange = false
                                        days = option
                                        reload()
                                    },
                                    label = { Text(label, maxLines = 1) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { customRange = !customRange },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(if (customRange) "Fechar outro período" else "Ver outro período", maxLines = 1)
                        }

                        if (customRange) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                DateRangeButton("De", customFrom, Modifier.weight(1f)) { datePickerTarget = "from" }
                                DateRangeButton("Até", customTo, Modifier.weight(1f)) { datePickerTarget = "to" }
                            }
                            Button(
                                onClick = { reload() },
                                enabled = !customFrom.isAfter(customTo),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(14.dp),
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
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.FilterAlt, null, tint = RondaSafeColors.Blue)
                            Spacer(Modifier.width(8.dp))
                            Text("Filtros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        FilterMenu(
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
                            onSelected = { status = it; reload() },
                        )
                        FilterMenu(
                            label = "Porteiro",
                            selected = guard?.name ?: "Todos os porteiros",
                            options = listOf(null to "Todos os porteiros") + guards.map { it to it.name },
                            onSelected = { guard = it; reload() },
                        )

                        OutlinedButton(
                            onClick = { showMoreFilters = !showMoreFilters },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Text(if (showMoreFilters) "Ocultar filtros de local" else "Filtrar por bloco e andar")
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Rounded.KeyboardArrowDown, null)
                        }

                        if (showMoreFilters) {
                            FilterMenu(
                                label = "Bloco",
                                selected = block?.name ?: "Todos os blocos",
                                options = listOf(null to "Todos os blocos") + blocks.map { it to it.name },
                                onSelected = { block = it; reload() },
                            )
                            if (block != null) {
                                FilterMenu(
                                    label = "Andar",
                                    selected = floor?.name ?: "Todos os andares",
                                    options = listOf(null to "Todos os andares") + floors.map { it to it.name },
                                    onSelected = { floor = it; reload() },
                                )
                            }
                        }
                    }
                }
            }

            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

            if (!loading && rows.isEmpty()) {
                item {
                    EmptyStateCard(
                        if (viewingToday) "Nenhuma ronda prevista para hoje" else "Nenhuma ronda encontrada",
                        if (viewingToday)
                            "Não há rondas programadas para hoje com os filtros atuais."
                        else
                            "Não há registros para o período e os filtros selecionados.",
                        Icons.Rounded.History,
                    )
                }
            }

            items(rows, key = { it.id }) { item -> PatrolHistoryCard(item) }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    datePickerTarget?.let { target ->
        val current = if (target == "from") customFrom else customTo
        val state = rememberDatePickerState(
            initialSelectedDateMillis = current.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli(),
        )
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
private fun DateRangeButton(label: String, date: LocalDate, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 10.dp),
    ) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Muted)
            Text(formatLocalDate(date), maxLines = 1, fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
        }
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, RondaSafeColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.patrolName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = RondaSafeColors.Navy,
                    )
                    Text(
                        item.guardName ?: "Sem porteiro definido",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RondaSafeColors.Muted,
                    )
                }
                StatusPill(item.displayStatus)
            }
            HorizontalDivider(color = RondaSafeColors.Border.copy(alpha = .7f))
            Text(
                historyScheduledLabel(item.scheduledFor),
                style = MaterialTheme.typography.bodySmall,
                color = RondaSafeColors.Muted,
            )
            Text(
                "${item.visitedPoints} de ${item.requiredPoints} pontos visitados",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )

            if (expanded) {
                Spacer(Modifier.height(3.dp))
                item.startedAt?.let { Text("Início: ${historyDate(it)}", style = MaterialTheme.typography.bodySmall) }
                item.finishedAt?.let { Text("Fim: ${historyDate(it)}", style = MaterialTheme.typography.bodySmall) }
                Text(
                    "Fuso exibido: ${AppTime.zoneLabel(patrolHistoryZone)} (configuração atual do Android)",
                    style = MaterialTheme.typography.labelSmall,
                    color = RondaSafeColors.Muted,
                )
                if (item.capturedOffline) {
                    Text("Sincronizada após uso offline", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                }
                if (item.suspicious) {
                    Text("Precisa de revisão", color = RondaSafeColors.Danger, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                }
                if (item.missingPoints > 0) {
                    Text(
                        "${item.missingPoints} ponto(s) não visitado(s)",
                        color = RondaSafeColors.Danger,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (loadingPoints) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                points.forEach { point ->
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            "${if (point.visited) "✓" else "○"} ${point.blockName} • ${point.floorName} • ${point.checkpointName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (point.visited) RondaSafeColors.Text else RondaSafeColors.Danger,
                        )
                        if (point.visited) {
                            point.firstScanAt?.let { eventValue ->
                                val eventZone = AppTime.eventZone(point.capturedZoneId, point.capturedOffsetSeconds)
                                Text(
                                    "Escaneado: ${AppTime.dateTime(eventValue, eventZone)} • ${point.capturedZoneId ?: eventZone.id}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RondaSafeColors.Muted,
                                )
                            }
                            point.serverReceivedAt?.let { syncValue ->
                                Text(
                                    "Recebido no servidor: ${AppTime.dateTime(syncValue, patrolHistoryZone)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RondaSafeColors.Muted,
                                )
                            }
                        }
                    }
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
            maxLines = 1,
        )
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
            shape = RoundedCornerShape(14.dp),
            singleLine = true,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
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

private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy • HH:mm")
private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

private fun historyDate(value: String): String = runCatching {
    AppTime.parseInstant(value).atZone(patrolHistoryZone).format(dateTimeFormatter)
}.getOrElse { value }

private fun historyScheduledLabel(value: String): String = runCatching {
    val dateTime = AppTime.parseInstant(value).atZone(patrolHistoryZone)
    if (dateTime.toLocalDate() == LocalDate.now(patrolHistoryZone)) {
        "Prevista para ${dateTime.format(timeFormatter)}"
    } else {
        "Prevista em ${dateTime.format(dateTimeFormatter)}"
    }
}.getOrElse { "Prevista em ${historyDate(value)}" }

private fun formatLocalDate(value: LocalDate): String = value.format(dateFormatter)
