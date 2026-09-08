package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.PatrolScheduleWindowDto
import com.rondasafe.app.data.model.PatrolTemplateDto
import com.rondasafe.app.data.repository.PatrolRepository
import com.rondasafe.app.ui.components.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val dayNames = mapOf(
    1 to "Seg", 2 to "Ter", 3 to "Qua", 4 to "Qui", 5 to "Sex", 6 to "Sáb", 7 to "Dom",
)

private data class FixedPatrolRow(
    val template: PatrolTemplateDto,
    val windows: List<PatrolScheduleWindowDto>,
)

@Composable
fun PatrolTemplatesScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (PatrolTemplateDto) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var rows by remember { mutableStateOf<List<FixedPatrolRow>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        runCatching {
            coroutineScope {
                val templatesDeferred = async { PatrolRepository.listTemplates(includeArchived = false).filter { it.active } }
                val windowsDeferred = async { PatrolRepository.listActiveWindows() }
                val templates = templatesDeferred.await()
                val windowsByTemplate = windowsDeferred.await().groupBy { it.patrolTemplateId }
                templates
                    .sortedBy { it.name.lowercase() }
                    .map { FixedPatrolRow(it, windowsByTemplate[it.id].orEmpty().sortedBy { window -> window.dayOfWeek }) }
            }
        }.onSuccess { rows = it }
            .onFailure { error = it.message ?: "Não foi possível carregar as rondas." }
        loading = false
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Horários das rondas", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeading(
                    "Rondas fixas",
                    "A estrutura das rondas é fixa. Aqui você altera somente o horário de início e fim.",
                )
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = RondaSafeColors.BlueSoft,
                    border = BorderStroke(1.dp, RondaSafeColors.Blue.copy(alpha = .18f)),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = RondaSafeColors.Navy)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Nome, dias e pontos da ronda não podem ser alterados.",
                            color = RondaSafeColors.Navy,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!loading && rows.isEmpty()) {
                item { EmptyStateCard("Nenhuma ronda ativa", "As rondas fixas precisam estar cadastradas no sistema.", Icons.Rounded.Schedule) }
            }
            items(rows, key = { it.template.id }) { row ->
                FixedPatrolCard(row = row, onEdit = { onEdit(row.template) })
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun FixedPatrolCard(row: FixedPatrolRow, onEdit: () -> Unit) {
    val first = row.windows.firstOrNull()
    val uniqueTimes = row.windows
        .map { it.startTime.take(5) to it.endTime.take(5) }
        .distinct()
    val timeLabel = when {
        uniqueTimes.isEmpty() -> "Horário não configurado"
        uniqueTimes.size == 1 -> "${uniqueTimes.first().first} – ${uniqueTimes.first().second}"
        else -> "Horários diferentes por dia"
    }
    val daysLabel = if (row.windows.size == 7) {
        "Todos os dias"
    } else {
        row.windows.joinToString(" • ") { dayNames[it.dayOfWeek].orEmpty() }
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
                    Text(row.template.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                    Text(timeLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = RondaSafeColors.Text)
                    Text(daysLabel, style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                }
                Surface(shape = RoundedCornerShape(50), color = RondaSafeColors.GreenSoft) {
                    Text("Fixa", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Green)
                }
            }
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
                Text("Alterar horário", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CreatePatrolTemplateScreen(
    onBack: () -> Unit,
    onCreated: () -> Unit,
    template: PatrolTemplateDto? = null,
) {
    val scope = rememberCoroutineScope()
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var daysLabel by remember { mutableStateOf("") }
    var initialLoading by remember { mutableStateOf(template != null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(template?.id) {
        if (template == null) {
            initialLoading = false
            return@LaunchedEffect
        }
        initialLoading = true
        error = null
        runCatching { PatrolRepository.listWindows(template.id) }
            .onSuccess { windows ->
                val first = windows.firstOrNull()
                startTime = first?.startTime?.take(5).orEmpty()
                endTime = first?.endTime?.take(5).orEmpty()
                daysLabel = if (windows.size == 7) "Todos os dias" else windows.joinToString(" • ") { dayNames[it.dayOfWeek].orEmpty() }
            }
            .onFailure { error = it.message ?: "Não foi possível carregar o horário." }
        initialLoading = false
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Alterar horário", onBack) },
    ) { padding ->
        if (initialLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        if (template == null) {
            Column(
                Modifier.padding(padding).padding(RondaSafeUi.ScreenPadding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyStateCard(
                    "Rondas são fixas",
                    "Não é possível criar novas rondas pelo aplicativo. Volte e altere apenas os horários das rondas existentes.",
                    Icons.Rounded.Lock,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionHeading(template.name, "Somente o horário pode ser alterado.")
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = RondaSafeColors.BlueSoft,
                    border = BorderStroke(1.dp, RondaSafeColors.Blue.copy(alpha = .18f)),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Estrutura bloqueada", fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                        Text("Dias: $daysLabel", color = RondaSafeColors.Muted, style = MaterialTheme.typography.bodySmall)
                        Text("Nome, dias, pontos e responsáveis permanecem inalterados.", color = RondaSafeColors.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it.filter { ch -> ch.isDigit() || ch == ':' }.take(5) },
                        label = { Text("Início") },
                        placeholder = { Text("22:00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it.filter { ch -> ch.isDigit() || ch == ':' }.take(5) },
                        label = { Text("Fim") },
                        placeholder = { Text("06:00") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                if (startTime.length == 5 && endTime.length == 5 && endTime <= startTime) {
                    Text("A ronda termina no dia seguinte.", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            saving = true
                            error = null
                            runCatching { PatrolRepository.updateTemplateTime(template.id, startTime, endTime) }
                                .onSuccess { onCreated() }
                                .onFailure { error = it.message ?: "Não foi possível salvar o horário." }
                            saving = false
                        }
                    },
                    enabled = !saving && startTime.length == 5 && endTime.length == 5,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Salvar horário", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
