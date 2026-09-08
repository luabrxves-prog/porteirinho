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

private val fixedPatrolNames = listOf(
    "Ronda Matutina",
    "Ronda Vespertina",
    "Ronda Noturna",
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
                val templatesByName = templates.associateBy { it.name.trim().lowercase() }

                fixedPatrolNames.mapNotNull { expectedName ->
                    val template = templatesByName[expectedName.lowercase()] ?: return@mapNotNull null
                    FixedPatrolRow(
                        template = template,
                        windows = windowsByTemplate[template.id].orEmpty().sortedBy { it.dayOfWeek },
                    )
                }
            }
        }.onSuccess {
            rows = it
            if (it.size != fixedPatrolNames.size) {
                error = "As 3 rondas fixas não estão completas no sistema."
            }
        }.onFailure { error = it.message ?: "Não foi possível carregar as rondas." }
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
                    "3 rondas fixas",
                    "Matutina, Vespertina e Noturna. O administrador altera somente início e fim.",
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
                            "As rondas, os dias e os pontos são fixos. Somente os horários podem ser alterados.",
                            color = RondaSafeColors.Navy,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            items(rows, key = { it.template.id }) { row ->
                FixedPatrolCard(row = row, onEdit = { onEdit(row.template) })
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun FixedPatrolCard(row: FixedPatrolRow, onEdit: () -> Unit) {
    val uniqueTimes = row.windows
        .map { it.startTime.take(5) to it.endTime.take(5) }
        .distinct()
    val timeLabel = when {
        uniqueTimes.isEmpty() -> "Horário não configurado"
        uniqueTimes.size == 1 -> "${uniqueTimes.first().first} – ${uniqueTimes.first().second}"
        else -> "Horários diferentes por dia"
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
                    Text("Todos os dias", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
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

        if (template == null || template.name !in fixedPatrolNames) {
            Column(
                Modifier.padding(padding).padding(RondaSafeUi.ScreenPadding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                EmptyStateCard(
                    "Ronda fixa inválida",
                    "Somente Matutina, Vespertina e Noturna podem ser administradas.",
                    Icons.Rounded.Lock,
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Voltar") }
            }
            return@Scaffold
        }

        val timeRegex = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
        val validTimes = startTime.matches(timeRegex) && endTime.matches(timeRegex)

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { SectionHeading(template.name, "Altere somente o horário desta ronda.") }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = RondaSafeColors.BlueSoft,
                    border = BorderStroke(1.dp, RondaSafeColors.Blue.copy(alpha = .18f)),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Ronda fixa", fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                        Text("Executada todos os dias. Nome e pontos não podem ser alterados.", color = RondaSafeColors.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = { startTime = it.filter { ch -> ch.isDigit() || ch == ':' }.take(5) },
                        label = { Text("Início") },
                        placeholder = { Text("06:00") },
                        singleLine = true,
                        isError = startTime.isNotEmpty() && startTime.length == 5 && !startTime.matches(timeRegex),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    )
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = { endTime = it.filter { ch -> ch.isDigit() || ch == ':' }.take(5) },
                        label = { Text("Fim") },
                        placeholder = { Text("14:00") },
                        singleLine = true,
                        isError = endTime.isNotEmpty() && endTime.length == 5 && !endTime.matches(timeRegex),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
            }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            item {
                Button(
                    onClick = {
                        scope.launch {
                            if (saving) return@launch
                            saving = true
                            error = null
                            runCatching { PatrolRepository.updateTemplateTime(template.id, startTime, endTime) }
                                .onSuccess { onCreated() }
                                .onFailure { error = it.message ?: "Não foi possível salvar o horário." }
                            saving = false
                        }
                    },
                    enabled = !saving && validTimes,
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
