package com.rondasafe.app.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rondasafe.app.data.model.GuardDto
import com.rondasafe.app.data.repository.GuardRepository
import com.rondasafe.app.ui.components.RondaSafeColors
import io.ktor.http.ContentType
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
    var photoGuard by remember { mutableStateOf<GuardDto?>(null) }
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
        containerColor = RondaSafeColors.Background,
        topBar = { AppTopBar("Porteiros", onBack) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Equipe da portaria", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                        Text("Cadastre, atualize foto e gerencie o acesso.", color = RondaSafeColors.Muted)
                    }
                    Switch(checked = includeArchived, onCheckedChange = { includeArchived = it })
                }
            }
            item {
                Button(
                    onClick = { showCreate = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) { Text("+ Novo porteiro", fontWeight = FontWeight.Bold) }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

            items(guards, key = { it.id }) { guard ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!guard.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = guard.photoUrl,
                                    contentDescription = "Foto de ${guard.name}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(62.dp).clip(RoundedCornerShape(16.dp)),
                                )
                            } else {
                                Surface(modifier = Modifier.size(62.dp), shape = RoundedCornerShape(16.dp), color = RondaSafeColors.BlueSoft) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(guard.name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(guard.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(
                                    when {
                                        !guard.active -> "Arquivado"
                                        guard.pinState == "TEMPORARY" -> "PIN temporário • troca pendente"
                                        guard.pinState == "PERSONAL" -> "PIN pessoal configurado"
                                        else -> guard.pinState
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RondaSafeColors.Muted,
                                )
                            }
                        }

                        if (guard.active) {
                            OutlinedButton(onClick = { photoGuard = guard }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (guard.photoUrl.isNullOrBlank()) "Adicionar foto" else "Alterar foto")
                            }
                            OutlinedButton(onClick = { confirmReset = guard }, modifier = Modifier.fillMaxWidth()) {
                                Text("Redefinir PIN")
                            }
                            OutlinedButton(
                                onClick = { confirmArchive = guard },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = RondaSafeColors.Danger),
                            ) { Text("Arquivar porteiro") }
                        } else {
                            OutlinedButton(onClick = {
                                scope.launch {
                                    runCatching { GuardRepository.restore(guard.id) }
                                        .onSuccess { refresh++ }
                                        .onFailure { error = it.message }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Restaurar porteiro") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
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

    photoGuard?.let { guard ->
        GuardPhotoDialog(
            guard = guard,
            onDismiss = { photoGuard = null },
            onSaved = { photoGuard = null; refresh++ },
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
                    Surface(shape = RoundedCornerShape(14.dp), color = RondaSafeColors.BlueSoft) {
                        Text(pin, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                    }
                    Text("Este PIN aparece somente agora. Informe ao porteiro; no primeiro acesso ele criará um PIN pessoal.")
                }
            },
            confirmButton = { Button(onClick = { tempPin = null }) { Text("Já anotei") } },
        )
    }
}

@Composable
private fun NewGuardDialog(
    onDismiss: () -> Unit,
    onCreated: (String, String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> photoUri = uri }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Novo porteiro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { launcher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (photoUri == null) "Selecionar foto (opcional)" else "Foto selecionada • trocar")
                }
                Text("JPEG, PNG ou WebP • máximo 5 MB", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
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
                        runCatching {
                            val photo = photoUri?.let { readPhoto(context.contentResolver, it) }
                            GuardRepository.createWithPhoto(
                                name = name,
                                photoBytes = photo?.bytes,
                                contentType = photo?.contentType,
                                extension = photo?.extension ?: "jpg",
                            )
                        }.onSuccess { response ->
                            val pin = response.temporaryPin ?: error("PIN temporário não retornado.")
                            onCreated(name.trim(), pin)
                        }.onFailure { error = it.message }
                        loading = false
                    }
                },
            ) { Text(if (loading) "Salvando..." else "Cadastrar") }
        },
    )
}

@Composable
private fun GuardPhotoDialog(guard: GuardDto, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var uri by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri = it }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Foto de ${guard.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { launcher.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (uri == null) "Escolher foto" else "Foto selecionada • trocar") }
                Text("JPEG, PNG ou WebP • máximo 5 MB", style = MaterialTheme.typography.bodySmall, color = RondaSafeColors.Muted)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") } },
        confirmButton = {
            Button(enabled = uri != null && !loading, onClick = {
                val selected = uri ?: return@Button
                scope.launch {
                    loading = true
                    runCatching {
                        val photo = readPhoto(context.contentResolver, selected)
                        GuardRepository.uploadPhoto(guard.id, photo.bytes, photo.contentType, photo.extension)
                    }.onSuccess { onSaved() }.onFailure { error = it.message }
                    loading = false
                }
            }) { Text(if (loading) "Enviando..." else "Salvar foto") }
        },
    )
}

private data class SelectedPhoto(val bytes: ByteArray, val contentType: ContentType, val extension: String)

private fun readPhoto(resolver: android.content.ContentResolver, uri: Uri): SelectedPhoto {
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Não foi possível ler a foto.")
    require(bytes.size <= 5 * 1024 * 1024) { "A foto deve ter no máximo 5 MB." }
    val mime = resolver.getType(uri) ?: "image/jpeg"
    val contentType = ContentType.parse(mime)
    val extension = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }
    return SelectedPhoto(bytes, contentType, extension)
}
