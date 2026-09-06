package com.rondasafe.app.ui.portaria

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rondasafe.app.data.model.*
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.ui.admin.AppTopBar
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun GuardSelectionScreen(onGuardSelected: (PortariaGuardDto) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var guards by remember { mutableStateOf<List<PortariaGuardDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    fun reload() {
        scope.launch {
            loading = true
            runCatching { PortariaRepository.listGuards() }
                .onSuccess { guards = it }
                .onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(topBar = { AppTopBar("Portaria", onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).fillMaxSize()) {
            OfflineSyncStatusBanner()
            Spacer(Modifier.height(16.dp))
            Text("Quem está iniciando o turno?", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            if (loading) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(guards, key = { it.id }) { guard ->
                    Card(onClick = { onGuardSelected(guard) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(16.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!guard.photoUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = guard.photoUrl,
                                    contentDescription = "Foto de ${guard.name}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(58.dp).clip(CircleShape),
                                )
                                Spacer(Modifier.width(14.dp))
                            }
                            Column {
                                Text(guard.name, style = MaterialTheme.typography.titleMedium)
                                Text(if (guard.pinState == "TEMPORARY") "Primeiro acesso" else "PIN pessoal")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GuardPinScreen(guard: PortariaGuardDto, onSuccess: (Boolean) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Scaffold(topBar = { AppTopBar(guard.name, onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Digite seu PIN", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true,
                label = { Text("PIN de 6 dígitos") },
            )
            error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    scope.launch {
                        loading = true; error = null
                        runCatching { PortariaRepository.loginGuard(guard.id, pin) }
                            .onSuccess { onSuccess(it.mustChangePin) }
                            .onFailure { error = it.message ?: "PIN inválido." }
                        loading = false
                    }
                },
                enabled = pin.length == 6 && !loading,
            ) { Text(if (loading) "Entrando..." else "Entrar") }
        }
    }
}

@Composable
fun ChangeGuardPinScreen(onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { AppTopBar("Criar PIN pessoal") }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp)) {
            Text("Troque o PIN temporário por um PIN pessoal de 6 dígitos.")
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(pin, { if (it.length <= 6 && it.all(Char::isDigit)) pin = it }, label = { Text("Novo PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(confirm, { if (it.length <= 6 && it.all(Char::isDigit)) confirm = it }, label = { Text("Confirmar PIN") }, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(20.dp))
            Button(onClick = {
                if (pin != confirm) { error = "Os PINs não conferem."; return@Button }
                scope.launch { runCatching { PortariaRepository.changePin(pin) }.onSuccess { onChanged() }.onFailure { error = it.message } }
            }, enabled = pin.length == 6 && confirm.length == 6) { Text("Salvar PIN") }
        }
    }
}

@Composable
fun ShiftHomeScreen(onShiftStarted: (ShiftDto) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    Scaffold(topBar = { AppTopBar("Meu turno", onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            OfflineSyncStatusBanner()
            Spacer(Modifier.weight(1f))
            Text(PortariaRepository.guardSession?.guardName ?: "Porteiro", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(24.dp))
            Button(onClick = { scope.launch { runCatching { PortariaRepository.startShift() }.onSuccess(onShiftStarted).onFailure { error = it.message } } }) { Text("Iniciar turno") }
            error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun AvailablePatrolsScreen(shift: ShiftDto, onStart: (AvailablePatrolDto, PatrolRunDto) -> Unit, onEndShift: () -> Unit) {
    val scope = rememberCoroutineScope()
    var patrols by remember { mutableStateOf<List<AvailablePatrolDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun refresh() { scope.launch { loading = true; runCatching { PortariaRepository.availablePatrols() }.onSuccess { patrols = it }.onFailure { error = it.message }; loading = false } }
    LaunchedEffect(Unit) { refresh() }

    Scaffold(topBar = { AppTopBar("Rondas disponíveis") }) { padding ->
        Column(Modifier.padding(padding).padding(20.dp).fillMaxSize()) {
            OfflineSyncStatusBanner()
            Spacer(Modifier.height(12.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (!loading && patrols.isEmpty()) Text("Nenhuma ronda disponível neste horário.")
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                items(patrols, key = { it.scheduleWindowId }) { patrol ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(patrol.patrolName, style = MaterialTheme.typography.titleLarge)
                            Text("${patrol.requiredPoints} pontos obrigatórios")
                            if (patrol.isLate) Text("Ronda atrasada", color = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.height(10.dp))
                            Button(onClick = { scope.launch { runCatching { PortariaRepository.startPatrol(shift.shiftId, patrol) }.onSuccess { onStart(patrol, it) }.onFailure { error = it.message } } }) { Text("Iniciar ronda") }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { scope.launch { runCatching { PortariaRepository.endShift(shift.shiftId) }.onSuccess { onEndShift() }.onFailure { error = it.message } } }, modifier = Modifier.fillMaxWidth()) { Text("Encerrar turno") }
        }
    }
}

@Composable
fun PatrolScannerScreen(run: PatrolRunDto, patrolName: String, onFinished: (FinishPatrolDto) -> Unit) {
    val scope = rememberCoroutineScope()
    var visited by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Aponte a câmera para um QR Code") }
    var error by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }

    Scaffold(topBar = { AppTopBar(patrolName) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OfflineSyncStatusBanner(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Text("$visited/${run.requiredPoints} pontos visitados", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            QrCameraScanner(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                enabled = !processing,
                onQr = { qr ->
                    processing = true
                    scope.launch {
                        runCatching { PortariaRepository.scan(run.runId, qr, SystemClock.elapsedRealtime()) }
                            .onSuccess {
                                visited = it.visitedPoints
                                message = when (it.scanResult) {
                                    "ACCEPTED" -> "${it.checkpointName ?: "Ponto"} confirmado"
                                    "DUPLICATE" -> "QR já lido nesta ronda"
                                    "REVOKED_QR" -> "QR revogado"
                                    "NOT_IN_ROUND" -> "Este ponto não pertence à ronda"
                                    else -> "QR desconhecido"
                                }
                            }
                            .onFailure { error = it.message }
                        processing = false
                    }
                },
            )
            Text(message, modifier = Modifier.padding(horizontal = 16.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            Button(onClick = { scope.launch { runCatching { PortariaRepository.finishPatrol(run.runId) }.onSuccess(onFinished).onFailure { error = it.message } } }, modifier = Modifier.padding(16.dp).fillMaxWidth()) { Text("Finalizar ronda") }
        }
    }
}

@Composable
private fun QrCameraScanner(modifier: Modifier, enabled: Boolean, onQr: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permission by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    LaunchedEffect(Unit) { launcher.launch(Manifest.permission.CAMERA) }

    if (!permission) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("Permita o uso da câmera para ler os QR Codes.") }
        return
    }

    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { scanner.close(); executor.shutdown() } }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(executor) { proxy ->
                        val mediaImage = proxy.image
                        if (mediaImage == null || !enabled) { proxy.close(); return@setAnalyzer }
                        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { codes -> codes.firstOrNull()?.rawValue?.let(onQr) }
                            .addOnCompleteListener { proxy.close() }
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
    )
}

@Composable
fun PatrolFinishedScreen(result: FinishPatrolDto, onDone: () -> Unit) {
    Column(Modifier.padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        OfflineSyncStatusBanner()
        Spacer(Modifier.weight(1f))
        Text(if (result.status == "COMPLETED") "Ronda concluída" else "Ronda incompleta", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        Text("${result.visitedPoints}/${result.totalPoints} pontos visitados")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone) { Text("Voltar às rondas") }
        Spacer(Modifier.weight(1f))
    }
}
