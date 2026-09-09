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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rondasafe.app.data.local.OfflineDatabase
import com.rondasafe.app.data.local.OfflineOperationalCache
import com.rondasafe.app.data.model.FinishPatrolDto
import com.rondasafe.app.data.model.PatrolRunDto
import com.rondasafe.app.data.repository.OccurrenceRepository
import com.rondasafe.app.data.repository.PortariaRepository
import com.rondasafe.app.ui.admin.AppTopBar
import com.rondasafe.app.ui.components.RondaSafeColors
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun PatrolScannerScreenV2(
    run: PatrolRunDto,
    patrolName: String,
    onFinished: (FinishPatrolDto) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dao = remember(context) { OfflineDatabase.get(context).offlineDao() }

    var visited by remember { mutableIntStateOf(0) }
    var statusMessage by remember { mutableStateOf("Posicione o QR Code dentro do quadro") }
    var error by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    var lastCameraQr by remember { mutableStateOf<String?>(null) }
    var occurrenceOpen by remember { mutableStateOf(false) }
    var occurrenceText by remember { mutableStateOf("") }
    var occurrenceSaving by remember { mutableStateOf(false) }

    LaunchedEffect(run.runId) {
        visited = dao.localVisitCount(run.runId)
    }

    val missing = (run.requiredPoints - visited).coerceAtLeast(0)
    val progress = if (run.requiredPoints <= 0) 0f else visited.toFloat() / run.requiredPoints.toFloat()
    val canFinish = run.requiredPoints > 0 && missing == 0 && !processing && !occurrenceSaving

    if (occurrenceOpen) {
        AlertDialog(
            onDismissRequest = { if (!occurrenceSaving) occurrenceOpen = false },
            title = { Text("Registrar ocorrência") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Descreva o que aconteceu durante a ronda.",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Justify,
                    )
                    OutlinedTextField(
                        value = occurrenceText,
                        onValueChange = { if (it.length <= 1000) occurrenceText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Descrição") },
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
                                    statusMessage = "Ocorrência salva. Ela será sincronizada automaticamente."
                                }
                                .onFailure { error = it.message }
                            occurrenceSaving = false
                        }
                    },
                ) { Text(if (occurrenceSaving) "Salvando..." else "Registrar") }
            },
            dismissButton = {
                TextButton(enabled = !occurrenceSaving, onClick = { occurrenceOpen = false }) {
                    Text("Cancelar")
                }
            },
        )
    }

    Scaffold(
        containerColor = Color(0xFFF5F7F9),
        topBar = { AppTopBar(patrolName) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Ronda em andamento",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = RondaSafeColors.Navy,
                        )
                        Text(
                            "$visited/${run.requiredPoints}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = RondaSafeColors.Blue,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(10.dp)),
                        color = RondaSafeColors.Blue,
                        trackColor = Color(0xFFE7EDF2),
                    )
                    Text(
                        if (missing == 0) "Todos os pontos foram confirmados." else "Faltam $missing ponto(s) para concluir a ronda.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = RondaSafeColors.Muted,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.Black),
            ) {
                PatrolQrCamera(
                    modifier = Modifier.fillMaxSize(),
                    enabled = !processing && !occurrenceOpen,
                    onQr = { rawQr ->
                        if (rawQr == lastCameraQr || processing) {
                            return@PatrolQrCamera
                        }
                        lastCameraQr = rawQr
                        processing = true
                        error = null

                        scope.launch {
                            val match = OfflineOperationalCache.qrMatch(context, rawQr)
                            val alreadyVisited = match != null &&
                                match.checkpointId in dao.localVisitedCheckpointIds(run.runId).toSet()

                            if (alreadyVisited) {
                                statusMessage = "${match?.checkpointName ?: "Este ponto"} já foi confirmado."
                                processing = false
                                return@launch
                            }

                            runCatching {
                                PortariaRepository.scan(run.runId, rawQr, SystemClock.elapsedRealtime())
                            }.onSuccess { result ->
                                visited = result.visitedPoints
                                statusMessage = when (result.scanResult) {
                                    "ACCEPTED" -> "${result.checkpointName ?: "Ponto"} confirmado com sucesso."
                                    "DUPLICATE" -> "${result.checkpointName ?: "Este ponto"} já foi confirmado."
                                    "REVOKED_QR" -> "Este QR Code foi revogado."
                                    "NOT_IN_ROUND" -> "Este ponto não pertence a esta ronda."
                                    else -> "QR Code não reconhecido."
                                }
                            }.onFailure {
                                error = it.message ?: "Não foi possível registrar a leitura."
                            }
                            processing = false
                        }
                    },
                    onNoQr = { lastCameraQr = null },
                )

                ScannerCorners(Modifier.fillMaxSize())

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black.copy(alpha = 0.68f),
                ) {
                    Text(
                        if (processing) "Registrando ponto..." else statusMessage,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            error?.let {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFECEC),
                ) {
                    Text(
                        it,
                        modifier = Modifier.padding(12.dp),
                        color = RondaSafeColors.Danger,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            OfflineSyncStatusBanner()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { occurrenceOpen = true },
                    enabled = !processing,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Ocorrência", fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        if (!canFinish) return@Button
                        scope.launch {
                            runCatching { PortariaRepository.finishPatrol(run.runId) }
                                .onSuccess(onFinished)
                                .onFailure { error = it.message }
                        }
                    },
                    enabled = canFinish,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(if (missing == 0) "Finalizar" else "Faltam $missing", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ScannerCorners(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val inset = size.minDimension * 0.18f
        val left = (size.width - size.minDimension + inset) / 2f
        val right = size.width - left
        val top = (size.height - size.minDimension + inset) / 2f
        val bottom = size.height - top
        val arm = size.minDimension * 0.10f
        val stroke = 7f
        val c = Color.White

        drawLine(c, Offset(left, top), Offset(left + arm, top), stroke)
        drawLine(c, Offset(left, top), Offset(left, top + arm), stroke)
        drawLine(c, Offset(right, top), Offset(right - arm, top), stroke)
        drawLine(c, Offset(right, top), Offset(right, top + arm), stroke)
        drawLine(c, Offset(left, bottom), Offset(left + arm, bottom), stroke)
        drawLine(c, Offset(left, bottom), Offset(left, bottom - arm), stroke)
        drawLine(c, Offset(right, bottom), Offset(right - arm, bottom), stroke)
        drawLine(c, Offset(right, bottom), Offset(right, bottom - arm), stroke)
    }
}

@Composable
private fun PatrolQrCamera(
    modifier: Modifier,
    enabled: Boolean,
    onQr: (String) -> Unit,
    onNoQr: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
    }

    LaunchedEffect(Unit) {
        if (!permissionGranted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!permissionGranted) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Permita o uso da câmera para ler os QR Codes.",
                modifier = Modifier.padding(24.dp),
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    val provider = providerFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(executor) { proxy ->
                        val mediaImage = proxy.image
                        if (mediaImage == null || !enabled) {
                            proxy.close()
                            return@setAnalyzer
                        }

                        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                        scanner.process(image)
                            .addOnSuccessListener { codes ->
                                val raw = codes.firstOrNull()?.rawValue
                                if (raw.isNullOrBlank()) onNoQr() else onQr(raw)
                            }
                            .addOnCompleteListener { proxy.close() }
                    }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }, ContextCompat.getMainExecutor(ctx))
            }
        },
    )
}
