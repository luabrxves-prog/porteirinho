package com.rondasafe.app.ui.admin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.GuardRepository
import com.rondasafe.app.data.repository.PatrolRepository
import com.rondasafe.app.ui.components.*
import kotlinx.coroutines.launch

private data class ArchivedUiItem(
    val id: String,
    val type: String,
    val category: String,
    val name: String,
    val subtitle: String,
    val icon: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ArchivedUiItem>>(emptyList()) }
    var category by remember { mutableStateOf("Todos") }
    var categoryOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var deleteTarget by remember { mutableStateOf<ArchivedUiItem?>(null) }

    LaunchedEffect(refresh) {
        loading = true
        error = null
        runCatching {
            buildList {
                GuardRepository.list(includeArchived = true).filter { !it.active }.forEach {
                    add(ArchivedUiItem(it.id, "guard", "Porteiros", it.name, "Porteiro arquivado", Icons.Rounded.Badge))
                }
                AdminRepository.defaultBlocks().forEach { block ->
                    val floors = AdminRepository.listFloors(block.id, includeArchived = true)
                    floors.filter { !it.active }.forEach { floor ->
                        add(ArchivedUiItem(floor.id, "floor", "Andares", floor.name, block.name, Icons.Rounded.Layers))
                    }
                    floors.forEach { floor ->
                        AdminRepository.listCheckpoints(floor.id, includeArchived = true).filter { !it.active }.forEach { checkpoint ->
                            add(ArchivedUiItem(checkpoint.id, "checkpoint", "Pontos", checkpoint.name, "${block.name} • ${floor.name}", Icons.Rounded.Place))
                        }
                    }
                }
                PatrolRepository.listTemplates(includeArchived = true).filter { !it.active }.forEach {
                    add(ArchivedUiItem(it.id, "patrol_template", "Rondas", it.name, "Programação de ronda", Icons.Rounded.Schedule))
                }
            }.sortedWith(compareBy({ it.category }, { it.name.lowercase() }))
        }.onSuccess { items = it }
            .onFailure { error = userFriendlyError(it, "Não foi possível carregar os itens arquivados.") }
        loading = false
    }

    val filtered = remember(items, category) {
        if (category == "Todos") items else items.filter { it.category == category }
    }
    val categories = listOf("Todos", "Porteiros", "Andares", "Pontos", "Rondas")

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Arquivados", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionHeading("Itens arquivados", "Restaure um cadastro ou exclua definitivamente quando não houver histórico vinculado.")
            }
            item {
                ExposedDropdownMenuBox(expanded = categoryOpen, onExpandedChange = { categoryOpen = !categoryOpen }) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Categoria") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryOpen) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                    )
                    ExposedDropdownMenu(expanded = categoryOpen, onDismissRequest = { categoryOpen = false }) {
                        categories.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { category = option; categoryOpen = false })
                        }
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(it, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
            if (!loading && error == null && filtered.isEmpty()) {
                item { EmptyStateCard("Nada arquivado", "Os itens arquivados aparecerão aqui.", Icons.Rounded.Archive) }
            }
            items(filtered, key = { "${it.type}:${it.id}" }) { item ->
                ArchivedItemCard(
                    item = item,
                    onRestore = {
                        scope.launch {
                            runCatching {
                                when (item.type) {
                                    "guard" -> GuardRepository.restore(item.id)
                                    "floor" -> AdminRepository.restore("floors", item.id)
                                    "checkpoint" -> AdminRepository.restore("checkpoints", item.id)
                                    "patrol_template" -> PatrolRepository.restoreTemplate(item.id)
                                }
                            }.onSuccess { error = null; refresh++ }
                                .onFailure { error = userFriendlyError(it, "Não foi possível restaurar este item.") }
                        }
                    },
                    onDelete = { deleteTarget = item },
                )
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = RondaSafeColors.Danger) },
            title = { Text("Excluir definitivamente?") },
            text = { Text("${target.name} será removido permanentemente. Cadastros com histórico serão preservados automaticamente.") },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancelar") } },
            confirmButton = {
                Button(
                    onClick = {
                        deleteTarget = null
                        scope.launch {
                            runCatching { AdminRepository.deleteArchived(target.type, target.id) }
                                .onSuccess { error = null; refresh++ }
                                .onFailure { error = userFriendlyError(it, "Não foi possível excluir este item.") }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RondaSafeColors.Danger),
                ) { Text("Excluir") }
            },
        )
    }
}

@Composable
private fun ArchivedItemCard(item: ArchivedUiItem, onRestore: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, RondaSafeColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(46.dp), shape = RoundedCornerShape(14.dp), color = RondaSafeColors.BlueSoft) {
                    Box(contentAlignment = Alignment.Center) { Icon(item.icon, null, tint = RondaSafeColors.Navy, modifier = Modifier.size(23.dp)) }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.name, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy, maxLines = 1)
                    Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted, maxLines = 1)
                    Text(item.category, style = MaterialTheme.typography.labelSmall, color = RondaSafeColors.Blue)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Restore, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Restaurar", maxLines = 1)
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = RondaSafeColors.Danger)) {
                    Icon(Icons.Rounded.DeleteForever, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(5.dp)); Text("Excluir", maxLines = 1)
                }
            }
        }
    }
}
