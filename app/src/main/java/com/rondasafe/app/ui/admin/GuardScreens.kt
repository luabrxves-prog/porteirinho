package com.rondasafe.app.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.RestartAlt
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
import com.rondasafe.app.ui.components.*
import io.ktor.http.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GuardsScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var guards by remember { mutableStateOf<List<GuardDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var photoGuard by remember { mutableStateOf<GuardDto?>(null) }
    var tempPin by remember { mutableStateOf<String?>(null) }
    var tempPinGuardName by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf<GuardDto?>(null) }
    var confirmRemove by remember { mutableStateOf<GuardDto?>(null) }
    var removingId by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching { GuardRepository.list(includeArchived = false).filter { it.active } }
                .onSuccess { guards = it }
                .onFailure { error = userFriendlyError(it, "Não foi possível carregar os porteiros.") }
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { PremiumTopBar("Porteiros", onBack) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(horizontal = RondaSafeUi.ScreenPadding, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            item { SectionHeading("Equipe da portaria", "Cadastre a equipe e gerencie foto e PIN de acesso.") }
            item {
                Button(
                    onClick = { showCreate = true },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Novo porteiro", fontWeight = FontWeight.Bold)
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

            if (!loading && guards.isEmpty()) {
                item { EmptyStateCard("Nenhum porteiro ativo", "Cadastre o primeiro porteiro para começar.", Icons.Rounded.Badge) }
            }

            items(guards, key = { it.id }) { guard ->
                GuardCard(
                    guard = guard,
                    removing = removingId == guard.id,
                    onPhoto = { photoGuard = guard },
                    onResetPin = { confirmReset = guard },
                    onRemove = { confirmRemove = guard },
                )
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
    }

    if (showCreate) {
        NewGuardDialog(
            onDismiss = { showCreate = false },
            onCreated = { guard, pin ->
                showCreate = false
                guards = (guards + guard).sortedBy { it.name.lowercase() }
                tempPinGuardName = guard.name
                tempPin = pin
            },
        )
    }

    photoGuard?.let { guard ->
        GuardPhotoDialog(
            guard = guard,
            onDismiss = { photoGuard = null },
            onSaved = { newUrl ->
                guards = guards.map { if (it.id == guard.id) it.copy(photoUrl = newUrl) else it }
                photoGuard = null
            },
        )
    }

    confirmReset?.let { guard ->
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text("Gerar novo PIN?") },
            text = { Text("O PIN atual de ${guard.name} deixará de funcionar.") },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text("Cancelar") } },
            confirmButton = {
                Button(onClick = {
                    confirmReset = null
                    scope.launch {
                        runCatching { GuardRepository.resetPin(guard.id) }
                            .onSuccess {
                                tempPinGuardName = guard.name
                                tempPin = it.temporaryPin
                            }
                            .onFailure { error = userFriendlyError(it, "Não foi possível gerar um novo PIN.") }
                    }
                }) { Text("Gerar PIN") }
            },
        )
    }

    confirmRemove?.let { guard ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remover ${guard.name}?") },
            text = { Text("O porteiro sairá da equipe ativa. O histórico já registrado continuará preservado.") },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancelar") } },
            confirmButton = {
                Button(onClick = {
                    confirmRemove = null
                    removingId = guard.id
                    val previous = guards
                    guards = guards.filterNot { it.id == guard.id }
                    scope.launch {
                        runCatching { GuardRepository.archive(guard.id) }
                            .onFailure {
                                guards = previous
                                error = userFriendlyError(it, "Não foi possível remover este porteiro.")
                            }
                        removingId = null
                    }
                }) { Text("Remover") }
            },
        )
    }

    tempPin?.let { pin ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("PIN temporário") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(tempPinGuardName, fontWeight = FontWeight.Bold)
                    Surface(shape = RoundedCornerShape(16.dp), color = RondaSafeColors.BlueSoft) {
                        Text(
                            pin,
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = RondaSafeColors.Navy,
                        )
                    }
                    Text("Anote este PIN. No primeiro acesso o porteiro criará um PIN pessoal.")
                }
            },
            confirmButton = { Button(onClick = { tempPin = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun GuardCard(
    guard: GuardDto,
    removing: Boolean,
    onPhoto: () -> Unit,
    onResetPin: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.White),
        border = BorderStroke(1.dp, RondaSafeColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!guard.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = guard.photoUrl,
                        contentDescription = "Foto de ${guard.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)),
                    )
                } else {
                    Surface(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(16.dp), color = RondaSafeColors.BlueSoft) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                guard.name.take(1).uppercase(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = RondaSafeColors.Navy,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(guard.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = RondaSafeColors.Navy)
                    Text(
                        if (guard.pinState == "TEMPORARY") "Primeiro acesso pendente" else "Acesso configurado",
                        style = MaterialTheme.typography.bodySmall,
                        color = RondaSafeColors.Muted,
                    )
                }
                if (removing) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onPhoto, enabled = !removing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.PhotoCamera, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Foto", maxLines = 1)
                }
                OutlinedButton(onClick = onResetPin, enabled = !removing, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Novo PIN", maxLines = 1)
                }
            }
            TextButton(onClick = onRemove, enabled = !removing, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.DeleteOutline, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Remover porteiro", color = RondaSafeColors.Danger)
            }
        }
    }
}

@Composable
private fun NewGuardDialog(
    onDismiss: () -> Unit,
    onCreated: (GuardDto, String) -> Unit,
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
                OutlinedButton(onClick = { launcher.launch("image/*") }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PhotoCamera, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (photoUri == null) "Adicionar foto (opcional)" else "Foto selecionada", maxLines = 1)
                }
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
                            val photo = photoUri?.let { uri ->
                                withContext(Dispatchers.IO) { readPhoto(context.contentResolver, uri) }
                            }
                            val response = GuardRepository.createWithPhoto(
                                name = name,
                                photoBytes = photo?.bytes,
                                contentType = photo?.contentType,
                                extension = photo?.extension ?: "jpg",
                            )
                            val mutation = response.guard ?: error("Porteiro não retornado.")
                            val pin = response.temporaryPin ?: error("PIN temporário não retornado.")
                            val guard = GuardDto(
                                id = mutation.guardId,
                                name = mutation.guardName,
                                photoUrl = null,
                                pinState = mutation.pinState,
                                active = true,
                            )
                            guard to pin
                        }.onSuccess { (guard, pin) -> onCreated(guard, pin) }
                            .onFailure { error = userFriendlyError(it, "Não foi possível cadastrar o porteiro.") }
                        loading = false
                    }
                },
            ) { Text(if (loading) "Salvando..." else "Cadastrar") }
        },
    )
}

@Composable
private fun GuardPhotoDialog(guard: GuardDto, onDismiss: () -> Unit, onSaved: (String) -> Unit) {
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
                OutlinedButton(onClick = { launcher.launch("image/*") }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.PhotoCamera, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (uri == null) "Escolher foto" else "Foto selecionada", maxLines = 1)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !loading) { Text("Cancelar") } },
        confirmButton = {
            Button(enabled = uri != null && !loading, onClick = {
                val selected = uri ?: return@Button
                scope.launch {
                    loading = true
                    error = null
                    runCatching {
                        val photo = withContext(Dispatchers.IO) { readPhoto(context.contentResolver, selected) }
                        GuardRepository.uploadPhoto(guard.id, photo.bytes, photo.contentType, photo.extension)
                    }.onSuccess(onSaved)
                        .onFailure { error = userFriendlyError(it, "Não foi possível salvar a foto.") }
                    loading = false
                }
            }) { Text(if (loading) "Enviando..." else "Salvar") }
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
