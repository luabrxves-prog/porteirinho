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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rondasafe.app.AppTime
import com.rondasafe.app.data.model.AvailablePatrolDto
import com.rondasafe.app.data.model.FinishPatrolDto
import com.rondasafe.app.data.model.PatrolRunDto
import com.rondasafe.app.data.model.PortariaGuardDto
import com.rondasafe.app.data.model.ShiftDto
import com.rondasafe.app.data.repository.OccurrenceRepository
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.ui.admin.AppTopBar
import com.rondasafe.app.ui.components.RondaSafeColors
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun GuardSelectionScreen(onGuardSelected: (PortariaGuardDto) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var guards by remember { mutableStateOf<List<PortariaGuardDto>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        runCatching { PortariaRepository.listGuards() }
            .onSuccess { guards = it }
            .onFailure { error = it.message }
        loading = false
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { AppTopBar("Portaria", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = RondaSafeColors.Navy,
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Selecionar porteiro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(4.dp))
                        Text("Escolha seu perfil para continuar.", color = Color.White.copy(alpha = 0.72f))
                    }
                }
            }
            item { OfflineSyncStatusBanner() }
            if (loading) item { CircularProgressIndicator() }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            items(guards, key = { it.id }) { guard ->
                Card(
                    onClick = { onGuardSelected(guard) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!guard.photoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = guard.photoUrl,
                                contentDescription = "Foto de ${guard.name}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(62.dp).clip(RoundedCornerShape(16.dp)),
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(62.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = RondaSafeColors.BlueSoft,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(guard.name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                                }
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(guard.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(
                                if (guard.pinState == "TEMPORARY") "Primeiro acesso" else "PIN pessoal",
                                style = MaterialTheme.typography.bodySmall,
                                color = RondaSafeColors.Muted,
                            )
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall, color = RondaSafeColors.Blue)
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

    fun submit() {
        if (pin.length != 6 || loading) return
        scope.launch {
            loading = true
            error = null
            runCatching { PortariaRepository.loginGuard(guard.id, pin) }
                .onSuccess { onSuccess(it.mustChangePin) }
                .onFailure {
                    error = it.message ?: "PIN inválido."
                    pin = ""
                }
            loading = false
        }
    }

    Scaffold(
        containerColor = RondaSafeColors.Background,
        topBar = { AppTopBar("Acesso do porteiro", onBack) },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 22.dp, vertical = 18.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(modifier = Modifier.size(64.dp), shape = CircleShape, color = RondaSafeColors.BlueSoft) {
                Box(contentAlignment = Alignment.Center) {
                    Text(guard.name.take(1).uppercase(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Digite seu PIN", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
            Text("Olá, ${guard.name}!", color = RondaSafeColors.Muted)
            Spacer(Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { index ->
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, RondaSafeColors.Border),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (index < pin.length) "•" else "", style = MaterialTheme.typography.headlineSmall, color = RondaSafeColors.Navy)
                        }
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(20.dp))
            NumericKeypad(
                onDigit = { digit -> if (pin.length < 6 && !loading) pin += digit },
                onBackspace = { if (pin.isNotEmpty() && !loading) pin = pin.dropLast(1) },
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = ::submit,
                enabled = pin.length == 6 && !loading,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(if (loading) "Entrando..." else "Entrar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun NumericKeypad(onDigit: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit -> KeypadButton(digit) { onDigit(digit) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.width(76.dp))
            KeypadButton("0") { onDigit("0") }
            KeypadButton("⌫", onBackspace)
        }
    }
}

@Composable
private fun KeypadButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.size(width = 76.dp, height = 54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = RondaSafeColors.Navy),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun ChangeGuardPinScreen(onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var first by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var stage by remember { mutableIntStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    Scaffold(containerColor = RondaSafeColors.Background, topBar = { AppTopBar("Criar PIN pessoal") }) { padding ->
        Column(Modifier.padding(padding).padding(22.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (stage == 1) "Escolha um PIN de 6 dígitos" else "Digite o PIN novamente", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))
            val current = if (stage == 1) first else confirm
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(6) { index -> Text(if (index < current.length) "●" else "○", style = MaterialTheme.typography.headlineSmall, color = RondaSafeColors.Navy) }
            }
            error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(24.dp))
            NumericKeypad(
                onDigit = { digit ->
                    if (!saving && stage == 1 && first.length < 6) first += digit
                    if (!saving && stage == 2 && confirm.length < 6) confirm += digit
                },
                onBackspace = {
                    if (!saving && stage == 1 && first.isNotEmpty()) first = first.dropLast(1)
                    if (!saving && stage == 2 && confirm.isNotEmpty()) confirm = confirm.dropLast(1)
                },
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (stage == 1) {
                        stage = 2
                    } else if (first != confirm) {
                        error = "Os PINs não conferem."
                        confirm = ""
                    } else {
                        scope.launch {
                            saving = true
                            error = null
                            runCatching { PortariaRepository.changePin(first) }
                                .onSuccess { onChanged() }
                                .onFailure { error = it.message }
                            saving = false
                        }
                    }
                },
                enabled = current.length == 6 && !saving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (saving) "Salvando no servidor..." else if (stage == 1) "Continuar" else "Salvar PIN") }
        }
    }
}

@Composable
fun ShiftHomeScreen(onShiftStarted: (ShiftDto) -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    Scaffold(containerColor = RondaSafeColors.Background, topBar = { AppTopBar("Meu turno", onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(22.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            OfflineSyncStatusBanner()
            Spacer(Modifier.weight(1f))
            Text("Olá, ${PortariaRepository.guardSession?.guardName ?: "Porteiro"}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = RondaSafeColors.Navy)
            Spacer(Modifier.height(6.dp))
            Text(
                "Quando estiver pronto, inicie seu turno.",
                modifier = Modifier.fillMaxWidth(),
                color = RondaSafeColors.Muted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        error = null
                        runCatching { PortariaRepository.startShift() }
                            .onSuccess(onShiftStarted)
                            .onFailure { error = it.message }
                        loading = false
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (loading) "Iniciando..." else "Iniciar turno", fontWeight = FontWeight.Bold) }
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
    var actionLoading by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            runCatching { PortariaRepository.availablePatrols() }
                .onSuccess { patrols = it }
                .onFailure { error = it.message }
            loading = false
        }
    }
    LaunchedEffect(Unit) { refresh() }

    Scaffold(containerColor = RondaSafeColors.Background, topBar = { AppTopBar("Rondas do horário") }) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { OfflineSyncStatusBanner() }
            if (!shift.synced) {
                item {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color(0xFFFFF4DF)) {
                        Text("Turno salvo neste aparelho. Aguardando confirmação do servidor.", Modifier.padding(14.dp), color = Color(0xFF8A5700))
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }
            if (!loading && patrols.isEmpty()) {
                item {
                    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = RondaSafeColors.BlueSoft) {
                        Text("Nenhuma ronda disponível neste horário.", modifier = Modifier.padding(18.dp), color = RondaSafeColors.Navy)
                    }
                }
            }
            items(patrols, key = { "${it.scheduleWindowId}:${it.scheduledFor}" }) { patrol ->
                val completed = patrol.executionStatus == "COMPLETED"
                val incomplete = patrol.executionStatus == "INCOMPLETE"
                val inProgress = patrol.executionStatus == "IN_PROGRESS"
                val available = patrol.executionStatus == "AVAILABLE"
                val statusText = when {
                    completed -> "Concluída"
                    incomplete -> "Incompleta"
                    inProgress -> "Em andamento"
                    patrol.isLate -> "Atrasada"
                    else -> "Disponível"
                }
                val statusColor = when {
                    completed -> RondaSafeColors.Green
                    incomplete -> RondaSafeColors.Danger
                    inProgress -> RondaSafeColors.Blue
                    patrol.isLate -> RondaSafeColors.Danger
                    else -> RondaSafeColors.Green
                }
                val statusBackground = when {
                    completed -> RondaSafeColors.GreenSoft
                    incomplete -> Color(0xFFFFECEC)
                    inProgress -> RondaSafeColors.BlueSoft
                    patrol.isLate -> Color(0xFFFFECEC)
                    else -> RondaSafeColors.GreenSoft
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(patrol.patrolName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Horário previsto: ${AppTime.time(patrol.scheduledFor)}", color = RondaSafeColors.Navy, fontWeight = FontWeight.SemiBold)
                                Text("${patrol.requiredPoints} pontos de controle", color = RondaSafeColors.Muted)
                            }
                            Surface(shape = RoundedCornerShape(12.dp), color = statusBackground) {
                                Text(statusText, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (!patrol.executedByGuardName.isNullOrBlank() && !available) {
                            Text(
                                when {
                                    completed -> "Finalizada por ${patrol.executedByGuardName}."
                                    incomplete -> "Encerrada incompleta por ${patrol.executedByGuardName}."
                                    else -> "Iniciada por ${patrol.executedByGuardName}."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = RondaSafeColors.Muted,
                            )
                        }

                        if (available) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        actionLoading = true
                                        error = null
                                        runCatching { PortariaRepository.startPatrol(shift.shiftId, patrol) }
                                            .onSuccess { onStart(patrol, it) }
                                            .onFailure {
                                                error = it.message
                                                refresh()
                                            }
                                        actionLoading = false
                                    }
                                },
                                enabled = !actionLoading,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                shape = RoundedCornerShape(14.dp),
                            ) { Text(if (actionLoading) "Confirmando..." else "Iniciar ronda", fontWeight = FontWeight.Bold) }
                        } else {
                            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = statusBackground) {
                                Text(
                                    when {
                                        completed -> "Esta ronda já foi finalizada neste horário."
                                        incomplete -> "Esta ronda já foi encerrada neste horário."
                                        else -> "Esta ronda já está em andamento ou aguardando sincronização."
                                    },
                                    Modifier.padding(13.dp),
                                    color = statusColor,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            actionLoading = true
                            runCatching { PortariaRepository.endShift(shift.shiftId) }
                                .onSuccess { onEndShift() }
                                .onFailure { error = it.message }
                            actionLoading = false
                        }
                    },
                    enabled = !actionLoading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Encerrar turno") }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun PatrolScannerScreen(run: PatrolRunDto, patrolName: String, onFinished: (FinishPatrolDto) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var visited by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf(if (run.synced) "Aproxime o QR Code do ponto" else "Ronda iniciada offline • aguardando sincronização") }
    var error by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf(false) }
    var lastQr by remember { mutableStateOf<String?>(null) }
    var lastQrAt by remember { mutableStateOf(0L) }
    var occurrenceOpen by remember { mutableStateOf(false) }
    var occurrenceText by remember { mutableStateOf("") }
    var occurrenceSaving by remember { mutableStateOf(false) }
    val progress = if (run.requiredPoints <= 0) 0f else visited.toFloat() / run.requiredPoints.toFloat()

    if (occurrenceOpen) {
        AlertDialog(
            onDismissRequest = { if (!occurrenceSaving) occurrenceOpen = false },
            title = { Text("Registrar ocorrência") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Descreva algo diferente, suspeito ou que precise ser comunicado ao administrador.")
                    OutlinedTextField(
                        value = occurrenceText,
                        onValueChange = { if (it.length <= 1000) occurrenceText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Descrição da ocorrência") },
                        minLines = 4,
                        maxLines = 7,
                        supportingText = { Text("${occurrenceText.length}/1000") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = occurrenceText.trim().length >= 3 && !occurrenceSaving,
                    onClick = {
                        scope.launch {
                            occurrenceSaving = true
                            error = null
                            runCatching { OccurrenceRepository.report(context, run.runId, occurrenceText) }
                                .onSuccess {
                                    occurrenceText = ""
                                    occurrenceOpen = false
                                    message = "Ocorrência registrada • aguardando confirmação do servidor se necessário"
                                }
                                .onFailure { error = it.message }
                            occurrenceSaving = false
                        }
                    },
                ) { Text(if (occurrenceSaving) "Salvando..." else "Registrar") }
            },
            dismissButton = { TextButton(enabled = !occurrenceSaving, onClick = { occurrenceOpen = false }) { Text("Cancelar") } },
        )
    }

    Scaffold(containerColor = Color(0xFF081018), topBar = { AppTopBar(patrolName) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().background(Color(0xFF081018))) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text("$visited de ${run.requiredPoints} pontos registrados", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = RondaSafeColors.Blue, trackColor = Color.White.copy(alpha = 0.12f))
                Spacer(Modifier.height(12.dp))
                Text(message, color = Color.White.copy(alpha = 0.82f))
            }

            Box(
                Modifier.padding(horizontal = 18.dp).weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)).border(2.dp, RondaSafeColors.Blue, RoundedCornerShape(24.dp)),
            ) {
                QrCameraScanner(
                    modifier = Modifier.fillMaxSize(),
                    enabled = !processing && !occurrenceOpen && !finishing,
                    onQr = { qr ->
                        val now = SystemClock.elapsedRealtime()
                        val repeatedTooSoon = qr == lastQr && now - lastQrAt < 15_000L
                        if (!repeatedTooSoon) {
                            lastQr = qr
                            lastQrAt = now
                            processing = true
                            scope.launch {
                                error = null
                                runCatching { PortariaRepository.scan(run.runId, qr, now) }
                                    .onSuccess {
                                        visited = it.visitedPoints
                                        message = when {
                                            !it.synced && it.scanResult == "ACCEPTED" -> "${it.checkpointName ?: "Ponto"} salvo no aparelho • aguardando sincronização"
                                            !it.synced && it.scanResult == "DUPLICATE" -> "Ponto já registrado neste aparelho • sincronização pendente"
                                            it.scanResult == "ACCEPTED" -> "${it.checkpointName ?: "Ponto"} confirmado pelo servidor"
                                            it.scanResult == "DUPLICATE" -> "Este ponto já foi lido"
                                            it.scanResult == "REVOKED_QR" -> "QR Code revogado"
                                            it.scanResult == "NOT_IN_ROUND" -> "Este ponto não pertence à ronda"
                                            else -> "QR Code não reconhecido"
                                        }
                                    }
                                    .onFailure { error = it.message }
                                processing = false
                            }
                        }
                    },
                )
            }

            error?.let { Text(it, color = Color(0xFFFF8C8C), modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) }
            OfflineSyncStatusBanner(Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
            OutlinedButton(
                onClick = { occurrenceOpen = true },
                enabled = !processing && !finishing,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp).fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text("Registrar ocorrência", fontWeight = FontWeight.Bold) }
            Button(
                onClick = {
                    scope.launch {
                        finishing = true
                        error = null
                        runCatching { PortariaRepository.finishPatrol(run.runId) }
                            .onSuccess(onFinished)
                            .onFailure { error = it.message }
                        finishing = false
                    }
                },
                enabled = !processing && !occurrenceSaving && !finishing,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp).fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (finishing) "Confirmando finalização..." else "Finalizar ronda", fontWeight = FontWeight.Bold) }
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
        Box(modifier, contentAlignment = Alignment.Center) { Text("Permita o uso da câmera para ler os QR Codes.", color = Color.White) }
        return
    }

    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
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
    val confirmed = result.synced
    val completed = result.status == "COMPLETED"
    val accent = when {
        !confirmed -> Color(0xFFB66A00)
        completed -> RondaSafeColors.Green
        else -> RondaSafeColors.Danger
    }
    val background = when {
        !confirmed -> Color(0xFFFFF4DF)
        completed -> RondaSafeColors.GreenSoft
        else -> Color(0xFFFFECEC)
    }

    Column(
        Modifier.fillMaxSize().background(RondaSafeColors.Background).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OfflineSyncStatusBanner()
        Spacer(Modifier.weight(1f))
        Surface(modifier = Modifier.size(82.dp), shape = CircleShape, color = background) {
            Box(contentAlignment = Alignment.Center) {
                Text(if (!confirmed) "↻" else if (completed) "✓" else "!", style = MaterialTheme.typography.headlineLarge, color = accent)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            when {
                !confirmed -> "Ronda salva no aparelho"
                completed -> "Ronda concluída"
                else -> "Ronda incompleta"
            },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = RondaSafeColors.Navy,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text("${result.visitedPoints}/${result.totalPoints} pontos registrados", color = RondaSafeColors.Muted)
        if (!confirmed) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Aguardando sincronização. A ronda só será considerada concluída no sistema depois da confirmação do servidor.",
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(26.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text("Concluir", fontWeight = FontWeight.Bold) }
        Spacer(Modifier.weight(1f))
    }
}
