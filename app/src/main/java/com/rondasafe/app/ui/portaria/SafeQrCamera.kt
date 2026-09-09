package com.rondasafe.app.ui.portaria

import android.Manifest
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal fun createQrBarcodeScanner(): BarcodeScanner = BarcodeScanning.getClient(
    BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
)

@Composable
fun SafeQrCamera(modifier: Modifier, enabled: Boolean, onQr: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentEnabled by rememberUpdatedState(enabled)
    val currentCallback by rememberUpdatedState(onQr)
    fun hasPermission() = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    var permission by remember { mutableStateOf(hasPermission()) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) permission = hasPermission() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { if (!permission) launcher.launch(Manifest.permission.CAMERA) }
    if (!permission) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Permita o uso da c\u00e2mera para ler os QR Codes.", color = Color.White)
                TextButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Permitir c\u00e2mera") }
            }
        }
        return
    }
    val previewView = remember(context) {
        PreviewView(context).apply {
            // TextureView respects Compose bounds/clipping, unlike a separate SurfaceView.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }
    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize().testTag("qr_preview"))
        error?.let { message ->
            Surface(Modifier.align(Alignment.Center)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(message)
                    TextButton(onClick = { error = null; attempt++ }) { Text("Tentar c\u00e2mera novamente") }
                }
            }
        }
    }
    // Synchronization or a changed enabled flag never recreates the camera session.
    DisposableEffect(owner, previewView, attempt) {
        val disposed = AtomicBoolean(false)
        val inFlight = AtomicBoolean(false)
        val scanner = createQrBarcodeScanner()
        val executor = Executors.newSingleThreadExecutor()
        val main = ContextCompat.getMainExecutor(context)
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        var provider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!disposed.get()) {
                try {
                    val cameraProvider = future.get()
                    provider = cameraProvider
                    check(cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) { "CAMERA_UNAVAILABLE" }
                    analysis.setAnalyzer(executor) { proxy ->
                        if (disposed.get() || !currentEnabled || !inFlight.compareAndSet(false, true)) {
                            proxy.close(); return@setAnalyzer
                        }
                        try {
                            val media = proxy.image
                            if (media == null) { proxy.close(); inFlight.set(false); return@setAnalyzer }
                            scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                                .addOnSuccessListener(main) { codes ->
                                    if (!disposed.get() && currentEnabled) {
                                        codes.firstNotNullOfOrNull { it.rawValue?.takeIf(String::isNotBlank) }?.let(currentCallback)
                                    }
                                }
                                .addOnFailureListener(main) {
                                    if (!disposed.get()) error = "N\u00e3o foi poss\u00edvel analisar a imagem. Tente a c\u00e2mera novamente."
                                }
                                .addOnCompleteListener { proxy.close(); inFlight.set(false) }
                        } catch (_: Exception) { proxy.close(); inFlight.set(false) }
                    }
                    cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) {
                    if (!disposed.get()) error = "N\u00e3o foi poss\u00edvel abrir a c\u00e2mera. Verifique a permiss\u00e3o e tente novamente."
                }
            }
        }, main)
        onDispose {
            disposed.set(true)
            analysis.clearAnalyzer()
            runCatching { provider?.unbind(preview, analysis) }
            scanner.close()
            executor.shutdown()
        }
    }
}
