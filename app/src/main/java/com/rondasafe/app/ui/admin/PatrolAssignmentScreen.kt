package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AssignmentInd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.GuardDto
import com.rondasafe.app.data.model.PatrolScheduleWindowDto
import com.rondasafe.app.data.model.PatrolTemplateDto
import com.rondasafe.app.data.repository.GuardRepository
import com.rondasafe.app.data.repository.PatrolRepository
import com.rondasafe.app.ui.components.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private val assignmentDayNames = mapOf(1 to "Segunda", 2 to "Terça", 3 to "Quarta", 4 to "Quinta", 5 to "Sexta", 6 to "Sábado", 7 to "Domingo")

private data class AssignmentPatrolRow(
    val template: PatrolTemplateDto,
    val windows: List<PatrolScheduleWindowDto>,
    val assignedGuardIds: Set<String>,
)

private fun assignmentScheduleSummary(windows: List<PatrolScheduleWindowDto>): String {
    val sortedWindows = windows.sortedBy { it.dayOfWeek }
    val days = sortedWindows.mapNotNull { assignmentDayNames[it.dayOfWeek] }
    val schedules = sortedWindows
        .map { "${it.startTime.take(5)} às ${it.endTime.take(5)}" }
        .distinct()
    val daySummary = if (days.size == 7) "Todos os dias" else days.joinToString(", ")
    val timeSummary = when (schedules.size) {
        1 -> schedules.first()
        else -> "${windows.size} horários"
    }
    return "$daySummary • $timeSummary"
}

@Composable
fun PatrolAssignmentsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var guards by remember { mutableStateOf<List<GuardDto>>(emptyList()) }
    var rows by remember { mutableStateOf<List<AssignmentPatrolRow>>(emptyList()) }
    var selectedRow by remember { mutableStateOf<AssignmentPatrolRow?>(null) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                coroutineScope {
                    val guardsDeferred = async { GuardRepository.list().filter { it.active } }
                    val templatesDeferred = async { PatrolRepository.listTemplates().filter { it.active } }
                    val windowsDeferred = async { PatrolRepository.listActiveWindows() }
                    val assignmentsDeferred = async { PatrolRepository.listActiveAssignments() }

                    val loadedGuards = guardsDeferred.await()
                    val templates = templatesDeferred.await()
                    val windows = windowsDeferred.await()
                    val assignments = assignmentsDeferred.await()

                    val windowsByTemplate = windows.groupBy { it.patrolTemplateId }
                    val assignmentsByWindow = assignments.groupBy { it.scheduleWindowId }

                    val loadedRows = templates.mapNotNull { template ->
                        val templateWindows = windowsByTemplate[template.id].orEmpty().sortedBy { it.dayOfWeek }
                        if (templateWindows.isEmpty()) return@mapNotNull null
                        val assigned = templateWindows
                            .flatMap { window -> assignmentsByWindow[window.id].orEmpty().map { it.guardId } }
                            .toSet()
                        AssignmentPatrolRow(template, templateWindows, assigned)
                    }.sortedBy { it.template.name.lowercase() }

                    loadedGuards to loadedRows
                }
            }.onSuccess { (loadedGuards, loadedRows) ->
                guards = loadedGuards
                rows = loadedRows
            }.onFailure { error = userFriendlyError(it, "Não foi possível carregar os responsáveis pelas rondas.") }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Responsáveis pelas rondas", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeading(
                    "Quem pode fazer cada ronda",
                    "Opcional. Sem seleção, qualquer porteiro ativo pode realizar.",
                )
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!loading && rows.isEmpty()) {
                item { EmptyStateCard("Nenhuma ronda disponível", "As rondas fixas aparecerão aqui quando estiverem ativas.", Icons.Rounded.AssignmentInd) }
            }
            items(rows, key = { it.template.id }) { row ->
                val names = guards.filter { it.id in row.assignedGuardIds }.map { it.name }
                Card(
                    onClick = { if (!saving) selectedRow = row },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, RondaSafeColors.Border),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(modifier = Modifier.size(46.dp), shape = RoundedCornerShape(14.dp), color = RondaSafeColors.BlueSoft) {
                            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.AssignmentInd, null, tint = RondaSafeColors.Navy) }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(row.template.name, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                            Text(assignmentScheduleSummary(row.windows), style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (names.isEmpty()) "Qualquer porteiro" else names.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (names.isEmpty()) RondaSafeColors.Green else RondaSafeColors.Text,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall, color = RondaSafeColors.Muted)
                    }
                }
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    selectedRow?.let { row ->
        AssignmentDialog(
            row = row,
            guards = guards,
            saving = saving,
            onDismiss = { if (!saving) selectedRow = null },
            onSave = { selectedIds ->
                scope.launch {
                    saving = true
                    error = null
                    runCatching { PatrolRepository.setAssignmentsForWindows(row.windows.map { it.id }, selectedIds) }
                        .onSuccess {
                            rows = rows.map {
                                if (it.template.id == row.template.id) it.copy(assignedGuardIds = selectedIds) else it
                            }
                            selectedRow = null
                        }
                        .onFailure { error = userFriendlyError(it, "Não foi possível salvar os responsáveis.") }
                    saving = false
                }
            },
        )
    }
}

@Composable
private fun AssignmentDialog(
    row: AssignmentPatrolRow,
    guards: List<GuardDto>,
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var selected by remember(row.template.id, row.assignedGuardIds) { mutableStateOf(row.assignedGuardIds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Responsáveis") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text(row.template.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text(assignmentScheduleSummary(row.windows), style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(14.dp), color = RondaSafeColors.GreenSoft) {
                        Text("Se ninguém for marcado, qualquer porteiro ativo poderá realizar.", modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Green)
                    }
                }
                items(guards, key = { it.id }) { guard ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = guard.id in selected,
                            enabled = !saving,
                            onCheckedChange = { checked -> selected = if (checked) selected + guard.id else selected - guard.id },
                        )
                        Text(guard.name)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selected) }, enabled = !saving) {
                Text(if (saving) "Salvando..." else "Salvar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") } },
    )
}
