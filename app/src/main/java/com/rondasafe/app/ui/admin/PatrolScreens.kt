package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
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
import kotlinx.coroutines.launch

@Composable
fun PatrolTemplatesScreen(
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (PatrolTemplateDto) -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var templates by remember { mutableStateOf(emptyList<PatrolTemplateDto>()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        error = null
        runCatching {
            PatrolRepository.listTemplates(includeArchived = false)
                .filter { it.active && it.systemFixed }
                .sortedBy { fixedPatrolOrder(it.name) }
        }.onSuccess { templates = it }
            .onFailure { error = it.message }
        loading = false
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Programações de rondas", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeading(
                    "3 rondas fixas",
                    "Matutina, Vespertina e Noturna acontecem todos os dias. Para manter a operação simples, somente o horário de cada ronda pode ser alterado.",
                )
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = RondaSafeColors.BlueSoft,
                ) {
                    Text(
                        "Todos os pontos ativos do condomínio são obrigatórios e entram automaticamente nas três rondas.",
                        modifier = Modifier.padding(14.dp),
                        color = RondaSafeColors.Navy,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!loading && templates.isEmpty()) {
                item { EmptyStateCard("Programações não encontradas", "As três rondas fixas precisam ser restauradas.", Icons.Rounded.Schedule) }
            }
            items(templates, key = { it.id }) { template ->
                FixedPatrolCard(template = template, onEdit = { onEdit(template) })
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

@Composable
private fun FixedPatrolCard(template: PatrolTemplateDto, onEdit: () -> Unit) {
    var windows by remember(template.id) { mutableStateOf(emptyList<PatrolScheduleWindowDto>()) }
    var checkpointCount by remember(template.id) { mutableIntStateOf(0) }

    LaunchedEffect(template.id) {
        windows = runCatching { PatrolRepository.listWindows(template.id) }.getOrDefault(emptyList())
        checkpointCount = runCatching {
            PatrolRepository.listTemplateCheckpoints(template.id).count { it.active && it.required }
        }.getOrDefault(0)
    }

    val first = windows.firstOrNull()
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
                    if (first != null) {
                        Text(
                            "${first.startTime.take(5)} – ${first.endTime.take(5)} • todos os dias",
                            style = MaterialTheme.typography.bodySmall,
                            color = RondaSafeColors.Muted,
                        )
                    }
                }
                Surface(shape = RoundedCornerShape(50), color = RondaSafeColors.GreenSoft) {
                    Text("Fixa", modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Green, fontWeight = FontWeight.Bold)
                }
            }
            Text("$checkpointCount ponto(s) obrigatório(s)", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
            Button(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Alterar horário") }
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
    var pointCount by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var initialLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(template?.id) {
        initialLoading = true
        error = null
        if (template == null || !template.systemFixed) {
            error = "A criação de novas rondas está desativada. O condomínio utiliza três programações fixas."
        } else {
            runCatching {
                val edit = PatrolRepository.loadForEdit(template)
                val first = edit.windows.firstOrNull() ?: error("Horário da ronda não encontrado.")
                startTime = first.startTime.take(5)
                endTime = first.endTime.take(5)
                pointCount = edit.checkpointIds.size
            }.onFailure { error = it.message }
        }
        initialLoading = false
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar(template?.name ?: "Programação fixa", onBack) },
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
                            Text("Programação diária fixa", fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                            Text("Todos os 7 dias • $pointCount ponto(s) obrigatório(s). Nome, dias e pontos não podem ser removidos aqui.", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                        }
                    }
                }
            }

            template?.let { fixed ->
                item {
                    OutlinedTextField(
                        value = fixed.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Ronda") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                }
                item {
                    Text("Horário", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it.filter { ch -> ch.isDigit() || ch == ':' }.take(5) },
                            label = { Text("Início") },
                            placeholder = { Text("06:00") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        )
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = { endTime = it.filter { ch -> ch.isDigit() || ch == ':' }.take(5) },
                            label = { Text("Fim") },
                            placeholder = { Text("14:00") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                    if (startTime.isNotBlank() && endTime.isNotBlank() && endTime <= startTime) {
                        Text("Esta ronda termina no dia seguinte.", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                    }
                }
            }

            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

            if (template != null && template.systemFixed) {
                item {
                    Button(
                        onClick = {
                            scope.launch {
                                loading = true
                                error = null
                                runCatching { PatrolRepository.updateFixedPatrolHours(template.id, startTime, endTime) }
                                    .onSuccess { onCreated() }
                                    .onFailure { error = it.message }
                                loading = false
                            }
                        },
                        enabled = !loading && startTime.length == 5 && endTime.length == 5,
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(if (loading) "Salvando..." else "Salvar horário", fontWeight = FontWeight.Bold) }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }
}

private fun fixedPatrolOrder(name: String): Int = when (name) {
    "Ronda Matutina" -> 1
    "Ronda Vespertina" -> 2
    "Ronda Noturna" -> 3
    else -> 99
}
