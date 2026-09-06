package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.GuardDto
import com.rondasafe.app.data.model.PatrolScheduleWindowDto
import com.rondasafe.app.data.model.PatrolTemplateDto
import com.rondasafe.app.data.repository.GuardRepository
import com.rondasafe.app.data.repository.PatrolRepository
import kotlinx.coroutines.launch

private val assignmentDayNames = mapOf(
    1 to "Segunda",
    2 to "Terça",
    3 to "Quarta",
    4 to "Quinta",
    5 to "Sexta",
    6 to "Sábado",
    7 to "Domingo",
)

private data class AssignmentWindowRow(
    val template: PatrolTemplateDto,
    val window: PatrolScheduleWindowDto,
    val assignedGuardIds: Set<String>,
)

@Composable
fun PatrolAssignmentsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var guards by remember { mutableStateOf<List<GuardDto>>(emptyList()) }
    var rows by remember { mutableStateOf<List<AssignmentWindowRow>>(emptyList()) }
    var selectedRow by remember { mutableStateOf<AssignmentWindowRow?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                val activeGuards = GuardRepository.list()
                val templates = PatrolRepository.listTemplates()
                val windowRows = mutableListOf<AssignmentWindowRow>()
                for (template in templates) {
                    for (window in PatrolRepository.listWindows(template.id)) {
                        val assigned = PatrolRepository.listAssignments(window.id, includeArchived = false)
                            .map { it.guardId }
                            .toSet()
                        windowRows += AssignmentWindowRow(template, window, assigned)
                    }
                }
                activeGuards to windowRows.sortedWith(
                    compareBy<AssignmentWindowRow> { it.template.name.lowercase() }
                        .thenBy { it.window.dayOfWeek }
                        .thenBy { it.window.startTime }
                )
            }.onSuccess { (loadedGuards, loadedRows) ->
                guards = loadedGuards
                rows = loadedRows
            }.onFailure { error = it.message ?: "Não foi possível carregar as atribuições." }
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }

    Scaffold(topBar = { AppTopBar("Responsáveis por Ronda", onBack) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Sem porteiro atribuído, a janela fica disponível para qualquer porteiro ativo.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.window.id }) { row ->
                    val names = guards.filter { it.id in row.assignedGuardIds }.map { it.name }
                    Card(onClick = { selectedRow = row }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(row.template.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${assignmentDayNames[row.window.dayOfWeek]} • ${row.window.startTime.take(5)} às ${row.window.endTime.take(5)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                if (names.isEmpty()) "Qualquer porteiro ativo" else names.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
    }

    selectedRow?.let { row ->
        AssignmentDialog(
            row = row,
            guards = guards,
            onDismiss = { selectedRow = null },
            onSave = { selectedIds ->
                scope.launch {
                    runCatching { PatrolRepository.setAssignments(row.window.id, selectedIds) }
                        .onSuccess {
                            selectedRow = null
                            reload()
                        }
                        .onFailure { error = it.message ?: "Não foi possível salvar as atribuições." }
                }
            },
        )
    }
}

@Composable
private fun AssignmentDialog(
    row: AssignmentWindowRow,
    guards: List<GuardDto>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
) {
    var selected by remember(row.window.id, row.assignedGuardIds) {
        mutableStateOf(row.assignedGuardIds)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Definir responsáveis") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${row.template.name} • ${assignmentDayNames[row.window.dayOfWeek]} • ${row.window.startTime.take(5)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (guards.isEmpty()) {
                    Text("Nenhum porteiro ativo cadastrado.")
                } else {
                    guards.forEach { guard ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = guard.id in selected,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + guard.id else selected - guard.id
                                },
                            )
                            Text(guard.name)
                        }
                    }
                }
                Text(
                    "Se nenhum porteiro for marcado, qualquer porteiro ativo poderá executar essa janela.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selected) }) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
