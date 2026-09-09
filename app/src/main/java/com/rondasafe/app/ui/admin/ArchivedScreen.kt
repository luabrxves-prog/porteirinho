package com.rondasafe.app.ui.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.repository.ArchivedDataSource
import com.rondasafe.app.data.repository.ArchivedRecord
import com.rondasafe.app.data.repository.ArchivedRepository
import com.rondasafe.app.data.sync.SharedSyncBus
import com.rondasafe.app.ui.components.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    onBack: () -> Unit,
    source: ArchivedDataSource = ArchivedRepository,
    changes: Flow<Long> = SharedSyncBus.epoch,
    requestTimeoutMs: Long = 20_000,
) {
    val scope = rememberCoroutineScope()
    val serial = remember { Mutex() }
    val requests = remember { Channel<Unit>(Channel.CONFLATED) }
    var records by remember { mutableStateOf<List<ArchivedRecord>>(emptyList()) }
    var category by remember { mutableStateOf("Todos") }
    var categoryOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ArchivedRecord?>(null) }

    // Refresh the data, never recreate the screen/dialog or cancel an in-flight mutation.
    LaunchedEffect(source, changes) {
        requests.trySend(Unit)
        changes.collect { requests.trySend(Unit) }
    }
    LaunchedEffect(source) {
        for (ignored in requests) {
            serial.withLock {
                loading = true
                try {
                    records = withTimeout(requestTimeoutMs) { source.list() }.distinctBy { it.key }
                } catch (e: TimeoutCancellationException) {
                    error = "O servidor demorou para responder. Tente atualizar novamente."
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    error = userFriendlyError(e, "Não foi possível carregar os itens arquivados.")
                } finally {
                    loading = false
                }
            }
        }
    }

    fun mutate(item: ArchivedRecord, deleting: Boolean) {
        if (busy != null) return
        busy = item.key // synchronous: a second tap cannot start another request
        error = null
        message = null
        scope.launch {
            try {
                serial.withLock {
                    withTimeout(requestTimeoutMs) {
                        if (deleting) source.delete(item) else source.restore(item)
                    }
                    records = records.filterNot { it.key == item.key }
                    deleteTarget = null
                    message = if (deleting) "Exclusão confirmada pelo servidor." else "Restauração confirmada pelo servidor."
                    requests.trySend(Unit)
                }
            } catch (e: TimeoutCancellationException) {
                error = "Não foi possível confirmar a resposta. Atualize a lista antes de tentar novamente."
                deleteTarget = null
                requests.trySend(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = userFriendlyError(e, "Não foi possível concluir a operação. O item foi mantido na lista.")
                deleteTarget = null
            } finally {
                busy = null
            }
        }
    }

    BackHandler(enabled = busy != null) { /* wait for the bounded request */ }
    val visible = records.filter { category == "Todos" || it.category == category }
    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Itens arquivados", { if (busy == null) onBack() }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().testTag("archives_list"),
            contentPadding = PaddingValues(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeading("Itens arquivados", "Cadastros com histórico e itens fixos são preservados.")
                OutlinedButton(onClick = { error = null; requests.trySend(Unit) }, enabled = busy == null) { Text("Atualizar lista") }
            }
            item {
                ExposedDropdownMenuBox(expanded = categoryOpen, onExpandedChange = { if (busy == null) categoryOpen = !categoryOpen }) {
                    OutlinedTextField(
                        value = category, onValueChange = {}, readOnly = true,
                        label = { Text("Categoria") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryOpen) },
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    )
                    ExposedDropdownMenu(categoryOpen, onDismissRequest = { categoryOpen = false }) {
                        listOf("Todos", "Porteiros", "Andares", "Pontos", "Rondas").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { category = option; categoryOpen = false })
                        }
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth().testTag("archives_loading")) }
            error?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("archives_error")) } }
            message?.let { text -> item { Text(text, color = RondaSafeColors.Green, modifier = Modifier.testTag("archives_success")) } }
            if (!loading && error == null && visible.isEmpty()) {
                item { EmptyStateCard("Nada arquivado", "Não há itens nesta categoria.", Icons.Rounded.Archive) }
            }
            items(visible, key = { it.key }) { item ->
                Card(modifier = Modifier.fillMaxWidth().testTag("archive_${item.id}"), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.name, fontWeight = FontWeight.Bold)
                        Text(item.subtitle, style = MaterialTheme.typography.bodySmall)
                        item.blockedReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { mutate(item, false) }, enabled = busy == null,
                                modifier = Modifier.weight(1f).testTag("restore_${item.id}"),
                            ) { Text("Restaurar") }
                            OutlinedButton(
                                onClick = { error = null; deleteTarget = item }, enabled = busy == null && item.canDelete,
                                modifier = Modifier.weight(1f).testTag("delete_${item.id}"),
                            ) { Text("Excluir") }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { if (busy == null) deleteTarget = null },
            icon = { Icon(Icons.Rounded.DeleteForever, null) },
            title = { Text("Excluir definitivamente?") },
            text = { Text("Excluir ${item.name}? Esta ação não pode ser desfeita. O servidor verificará novamente se há histórico vinculado.") },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }, enabled = busy == null, modifier = Modifier.testTag("cancel_delete")) { Text("Cancelar") }
            },
            confirmButton = {
                Button(onClick = { mutate(item, true) }, enabled = busy == null, modifier = Modifier.testTag("confirm_delete")) {
                    Text(if (busy != null) "Excluindo..." else "Excluir")
                }
            },
        )
    }
}
