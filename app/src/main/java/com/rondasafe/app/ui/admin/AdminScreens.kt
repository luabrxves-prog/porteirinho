package com.rondasafe.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.repository.AdminRepository
import com.rondasafe.app.data.repository.AuthRepository
import com.rondasafe.app.ui.components.QrCodeImage
import kotlinx.coroutines.launch

@Composable
fun AdminLoginScreen(onLoginSuccess: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("RondaSafe", style = MaterialTheme.typography.headlineLarge)
            Text("Acesso administrativo", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("E-mail") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Senha") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        AuthRepository.signIn(email, password)
                            .onSuccess { onLoginSuccess() }
                            .onFailure { error = it.message ?: "Não foi possível entrar." }
                        loading = false
                    }
                },
                enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (loading) "Entrando..." else "Entrar")
            }
        }
    }
}

@Composable
fun AdminDashboardScreen(
    onOpenLocations: () -> Unit,
    onLogout: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Scaffold(
        topBar = { AppTopBar("Painel administrativo") },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(20.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("RondaSafe", style = MaterialTheme.typography.headlineMedium)
            Text("Gestão do sistema", style = MaterialTheme.typography.bodyLarge)
            DashboardCard("Locais e QR Codes", "Cadastre blocos, andares, pontos e QR Codes.", onOpenLocations)
            DashboardCard("Porteiros", "Cadastro e PINs serão implementados na próxima etapa.") {}
            DashboardCard("Rondas", "Programações e histórico serão conectados depois.") {}
            Spacer(Modifier.weight(1f))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        AuthRepository.signOut()
                        onLogout()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Sair") }
        }
    }
}

@Composable
private fun DashboardCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun BuildingsScreen(onBack: () -> Unit, onSelect: (BuildingDto) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    var includeArchived by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<List<BuildingDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }

    LaunchedEffect(refresh, includeArchived) {
        loading = true
        runCatching { AdminRepository.listBuildings(includeArchived) }
            .onSuccess { data = it }
            .onFailure { error = it.message }
        loading = false
    }

    AdminEntityListScaffold(
        title = "Prédios",
        onBack = onBack,
        includeArchived = includeArchived,
        onIncludeArchivedChange = { includeArchived = it },
        loading = loading,
        error = error,
        emptyText = "Nenhum prédio cadastrado.",
        addLabel = "Novo prédio",
        onAdd = { addOpen = true },
    ) {
        items(data, key = { it.id }) { item ->
            EntityRow(
                name = item.name,
                active = item.active,
                onOpen = { if (item.active) onSelect(item) },
                onArchiveOrRestore = {
                    if (item.active) AdminRepository.archive("buildings", item.id)
                    else AdminRepository.restore("buildings", item.id)
                },
                onChanged = { refresh++ },
            )
        }
    }

    if (addOpen) NameDialog("Novo prédio", "Nome do prédio", onDismiss = { addOpen = false }) { name ->
        AdminRepository.createBuilding(name)
        addOpen = false
        refresh++
    }
}

@Composable
fun BlocksScreen(building: BuildingDto, onBack: () -> Unit, onSelect: (BlockDto) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    var includeArchived by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<List<BlockDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }

    LaunchedEffect(refresh, includeArchived, building.id) {
        loading = true
        runCatching { AdminRepository.listBlocks(building.id, includeArchived) }
            .onSuccess { data = it }
            .onFailure { error = it.message }
        loading = false
    }

    AdminEntityListScaffold(
        title = "Blocos • ${building.name}",
        onBack = onBack,
        includeArchived = includeArchived,
        onIncludeArchivedChange = { includeArchived = it },
        loading = loading,
        error = error,
        emptyText = "Nenhum bloco cadastrado.",
        addLabel = "Novo bloco",
        onAdd = { addOpen = true },
    ) {
        items(data, key = { it.id }) { item ->
            EntityRow(
                name = item.name,
                active = item.active,
                onOpen = { if (item.active) onSelect(item) },
                onArchiveOrRestore = {
                    if (item.active) AdminRepository.archive("blocks", item.id)
                    else AdminRepository.restore("blocks", item.id)
                },
                onChanged = { refresh++ },
            )
        }
    }

    if (addOpen) NameDialog("Novo bloco", "Ex.: Bloco A", onDismiss = { addOpen = false }) { name ->
        AdminRepository.createBlock(building.id, name)
        addOpen = false
        refresh++
    }
}

@Composable
fun FloorsScreen(block: BlockDto, onBack: () -> Unit, onSelect: (FloorDto) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    var includeArchived by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<List<FloorDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }

    LaunchedEffect(refresh, includeArchived, block.id) {
        loading = true
        runCatching { AdminRepository.listFloors(block.id, includeArchived) }
            .onSuccess { data = it }
            .onFailure { error = it.message }
        loading = false
    }

    AdminEntityListScaffold(
        title = "Andares • ${block.name}",
        onBack = onBack,
        includeArchived = includeArchived,
        onIncludeArchivedChange = { includeArchived = it },
        loading = loading,
        error = error,
        emptyText = "Nenhum andar cadastrado.",
        addLabel = "Novo andar",
        onAdd = { addOpen = true },
    ) {
        items(data, key = { it.id }) { item ->
            EntityRow(
                name = item.name,
                active = item.active,
                onOpen = { if (item.active) onSelect(item) },
                onArchiveOrRestore = {
                    if (item.active) AdminRepository.archive("floors", item.id)
                    else AdminRepository.restore("floors", item.id)
                },
                onChanged = { refresh++ },
            )
        }
    }

    if (addOpen) NameDialog("Novo andar", "Ex.: 5º andar", onDismiss = { addOpen = false }) { name ->
        AdminRepository.createFloor(block.id, name)
        addOpen = false
        refresh++
    }
}

@Composable
fun CheckpointsScreen(floor: FloorDto, onBack: () -> Unit, onSelect: (CheckpointDto) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }
    var includeArchived by remember { mutableStateOf(false) }
    var data by remember { mutableStateOf<List<CheckpointDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var addOpen by remember { mutableStateOf(false) }

    LaunchedEffect(refresh, includeArchived, floor.id) {
        loading = true
        runCatching { AdminRepository.listCheckpoints(floor.id, includeArchived) }
            .onSuccess { data = it }
            .onFailure { error = it.message }
        loading = false
    }

    AdminEntityListScaffold(
        title = "Pontos • ${floor.name}",
        onBack = onBack,
        includeArchived = includeArchived,
        onIncludeArchivedChange = { includeArchived = it },
        loading = loading,
        error = error,
        emptyText = "Nenhum ponto de ronda cadastrado.",
        addLabel = "Novo ponto",
        onAdd = { addOpen = true },
    ) {
        items(data, key = { it.id }) { item ->
            EntityRow(
                name = item.name,
                active = item.active,
                onOpen = { if (item.active) onSelect(item) },
                onArchiveOrRestore = {
                    if (item.active) AdminRepository.archive("checkpoints", item.id)
                    else AdminRepository.restore("checkpoints", item.id)
                },
                onChanged = { refresh++ },
            )
        }
    }

    if (addOpen) CheckpointDialog(onDismiss = { addOpen = false }) { name, description ->
        AdminRepository.createCheckpoint(floor.id, name, description)
        addOpen = false
        refresh++
    }
}

@Composable
fun CheckpointDetailScreen(checkpoint: CheckpointDto, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var activeQr by remember { mutableStateOf<ActiveQrDto?>(null) }
    var newQr by remember { mutableStateOf<QrFunctionResponse?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmReplace by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching { AdminRepository.getActiveQr(checkpoint.id) }
                .onSuccess { activeQr = it }
                .onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(checkpoint.id) { reload() }

    Scaffold(topBar = { AppTopBar("Ponto de ronda", onBack) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(20.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(checkpoint.name, style = MaterialTheme.typography.headlineSmall)
            checkpoint.description?.let {
                Spacer(Modifier.height(4.dp))
                Text(it)
            }
            Spacer(Modifier.height(22.dp))

            if (loading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            val token = newQr?.tokenValue ?: activeQr?.tokenValue
            val version = newQr?.version ?: activeQr?.version
            val fingerprint = newQr?.fingerprint ?: activeQr?.fingerprint

            if (token != null) {
                QrCodeImage(token, Modifier.size(260.dp))
                Spacer(Modifier.height(12.dp))
                Text("QR ativo • versão ${version ?: "-"}")
                Text("Identificação: ${fingerprint ?: "-"}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = { confirmReplace = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Substituir QR Code") }
            } else if (!loading) {
                Text("Este ponto ainda não possui QR Code ativo.")
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        scope.launch {
                            loading = true
                            runCatching { AdminRepository.createQr(checkpoint.id) }
                                .onSuccess { newQr = it }
                                .onFailure { error = it.message }
                            loading = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Gerar QR Code") }
            }
        }
    }

    if (confirmReplace) {
        AlertDialog(
            onDismissRequest = { confirmReplace = false },
            title = { Text("Substituir QR Code?") },
            text = {
                Text("O QR Code atual será revogado e deixará de ser válido para novas rondas. O histórico de leituras anteriores será preservado.")
            },
            dismissButton = {
                TextButton(onClick = { confirmReplace = false }) { Text("Cancelar") }
            },
            confirmButton = {
                Button(onClick = {
                    confirmReplace = false
                    scope.launch {
                        loading = true
                        error = null
                        runCatching { AdminRepository.replaceQr(checkpoint.id) }
                            .onSuccess { newQr = it }
                            .onFailure { error = it.message }
                        loading = false
                    }
                }) { Text("Substituir QR Code") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                TextButton(onClick = onBack) { Text("Voltar") }
            }
        },
    )
}

@Composable
private fun AdminEntityListScaffold(
    title: String,
    onBack: () -> Unit,
    includeArchived: Boolean,
    onIncludeArchivedChange: (Boolean) -> Unit,
    loading: Boolean,
    error: String?,
    emptyText: String,
    addLabel: String,
    onAdd: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Scaffold(
        topBar = { AppTopBar(title, onBack) },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Text("+") } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = includeArchived, onCheckedChange = onIncludeArchivedChange)
                Text("Mostrar arquivados")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onAdd) { Text(addLabel) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
                if (!loading) item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun EntityRow(
    name: String,
    active: Boolean,
    onOpen: () -> Unit,
    onArchiveOrRestore: suspend () -> Unit,
    onChanged: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirmArchive by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                    Text(if (active) "Ativo" else "Arquivado", style = MaterialTheme.typography.bodySmall)
                }
                if (active) TextButton(onClick = onOpen) { Text("Abrir") }
            }
            Row {
                TextButton(onClick = {
                    if (active) confirmArchive = true
                    else scope.launch {
                        runCatching { onArchiveOrRestore() }
                            .onSuccess { onChanged() }
                            .onFailure { error = it.message }
                    }
                }) { Text(if (active) "Arquivar" else "Restaurar") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }

    if (confirmArchive) {
        AlertDialog(
            onDismissRequest = { confirmArchive = false },
            title = { Text("Arquivar item?") },
            text = { Text("Ele deixará de aparecer na operação normal, mas todo o histórico será preservado e poderá ser restaurado depois.") },
            dismissButton = { TextButton(onClick = { confirmArchive = false }) { Text("Cancelar") } },
            confirmButton = {
                Button(onClick = {
                    confirmArchive = false
                    scope.launch {
                        runCatching { onArchiveOrRestore() }
                            .onSuccess { onChanged() }
                            .onFailure { error = it.message }
                    }
                }) { Text("Arquivar") }
            },
        )
    }
}

@Composable
private fun NameDialog(
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: suspend (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var value by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(hint) })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") } },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        runCatching { onConfirm(value) }
                            .onFailure { error = it.message }
                        loading = false
                    }
                },
                enabled = value.isNotBlank() && !loading,
            ) { Text(if (loading) "Salvando..." else "Salvar") }
        },
    )
}

@Composable
private fun CheckpointDialog(
    onDismiss: () -> Unit,
    onConfirm: suspend (String, String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Novo ponto de ronda") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome do ponto") })
                OutlinedTextField(description, { description = it }, label = { Text("Descrição (opcional)") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") } },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        runCatching { onConfirm(name, description.ifBlank { null }) }
                            .onFailure { error = it.message }
                        loading = false
                    }
                },
                enabled = name.isNotBlank() && !loading,
            ) { Text(if (loading) "Salvando..." else "Salvar") }
        },
    )
}
