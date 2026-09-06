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
import com.rondasafe.app.data.repository.GuardRepository
import kotlinx.coroutines.launch

@Composable
fun GuardsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var includeArchived by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var guards by remember { mutableStateOf<List<GuardDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var tempPin by remember { mutableStateOf<String?>(null) }
    var tempPinGuardName by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf<GuardDto?>(null) }
    var confirmArchive by remember { mutableStateOf<GuardDto?>(null) }

    LaunchedEffect(includeArchived, refresh) {
        loading = true
        error = null
        runCatching { GuardRepository.list(includeArchived) }
            .onSuccess { guards = it }
            .onFailure { error = it.message }
        loading = false
    }

    Scaffold(
        topBar = { AppTopBar("Porteiros", onBack) },
        floatingActionButton = { FloatingActionButton(onClick = { showCreate = true }) { Text("+") } },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = includeArchived, onCheckedChange = { includeArchived = it })
                Text("Mostrar arquivados")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showCreate = true }) { Text("Novo porteiro") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(guards, key = { it.id }) { guard ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(guard.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                when {
                                    !guard.active -> "Arquivado"
                                    guard.pinState == "TEMPORARY" -> "PIN temporário • troca pendente"
                                    guard.pinState == "PERSONAL" -> "PIN pessoal configurado"
                                    else -> guard.pinState
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (guard.active) {
                                    TextButton(onClick = { confirmReset = guard }) { Text("Redefinir PIN") }
                                    TextButton(onClick = { confirmArchive = guard }) { Text("Arquivar") }
                                } else {
                                    TextButton(onClick = {
                                        scope.launch {
                                            runCatching { GuardRepository.restore(guard.id) }
                                                .onSuccess { refresh++ }
                                                .onFailure { error = it.message }
                                        }
                                    }) { Text("Restaurar") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        NewGuardDialog(
            onDismiss = { showCreate = false },
            onCreated = { name, pin ->
                showCreate = false
                tempPinGuardName = name
                tempPin = pin
                refresh++
            },
        )
    }

    confirmReset?.let { guard ->
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text("Redefinir PIN de ${guard.name}?") },
            text = { Text("O PIN atual deixará de funcionar. Um novo PIN temporário será gerado e o porteiro deverá criar um PIN pessoal no próximo acesso.") },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text("Cancelar") } },
            confirmButton = {
                Button(onClick = {
                    confirmReset = null
                    scope.launch {
                        runCatching { GuardRepository.resetPin(guard.id) }
                            .onSuccess {
                                tempPinGuardName = guard.name
                                tempPin = it.temporaryPin
                                refresh++
                            }
                            .onFailure { error = it.message }
                    }
                }) { Text("Gerar novo PIN") }
            },
        )
    }

    confirmArchive?.let { guard ->
        AlertDialog(
            onDismissRequest = { confirmArchive = null },
            title = { Text("Arquivar ${guard.name}?") },
            text = { Text("O porteiro deixará de aparecer para novos turnos, mas todo o histórico será preservado e poderá ser restaurado.") },
            dismissButton = { TextButton(onClick = { confirmArchive = null }) { Text("Cancelar") } },
            confirmButton = {
                Button(onClick = {
                    confirmArchive = null
                    scope.launch {
                        runCatching { GuardRepository.archive(guard.id) }
                            .onSuccess { refresh++ }
                            .onFailure { error = it.message }
                    }
                }) { Text("Arquivar") }
            },
        )
    }

    tempPin?.let { pin ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("PIN temporário gerado") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Porteiro: $tempPinGuardName")
                    Text(pin, style = MaterialTheme.typography.headlineMedium)
                    Text("Este PIN é exibido somente agora. Anote ou informe ao porteiro. No primeiro acesso ele será obrigado a criar um PIN pessoal.")
                }
            },
            confirmButton = {
                Button(onClick = { tempPin = null }) { Text("Já anotei") }
            },
        )
    }
}

@Composable
private fun NewGuardDialog(
    onDismiss: () -> Unit,
    onCreated: (String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Novo porteiro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome") },
                    singleLine = true,
                )
                Text("A foto será adicionada quando implementarmos o upload de imagens.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") } },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && !loading,
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        runCatching { GuardRepository.create(name) }
                            .onSuccess { response ->
                                val pin = response.temporaryPin ?: error("PIN temporário não retornado.")
                                onCreated(name.trim(), pin)
                            }
                            .onFailure { error = it.message }
                        loading = false
                    }
                },
            ) { Text(if (loading) "Salvando..." else "Cadastrar") }
        },
    )
}
