package com.rondasafe.app.ui.portaria

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun SafeQrCamera(modifier: Modifier, enabled: Boolean, onQr: (String) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentEnabled by rememberUpdatedState(enabled)
    val currentCallback by rememberUpdatedState(onQr)
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permission = it }
    LaunchedEffect(Unit) { if (!permission) launcher.launch(Manifest.permission.CAMERA) }
    if (!permission) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Permita o uso da câmera para ler os QR Codes.", color = Color.White)
                TextButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Permitir câmera") }
            }
        }
        return
    }
    val previewView = remember(context) { PreviewView(context) }
    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        error?.let { message ->
            Surface(Modifier.align(Alignment.Center)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(message)
                    TextButton(onClick = { error = null; attempt++ }) { Text("Tentar câmera novamente") }
                }
            }
        }
    }
    DisposableEffect(owner, previewView, attempt) {
        val disposed = AtomicBoolean(false)
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val executor = Executors.newSingleThreadExecutor()
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
                        if (disposed.get() || !currentEnabled) { proxy.close(); return@setAnalyzer }
                        val media = proxy.image
                        if (media == null) { proxy.close(); return@setAnalyzer }
                        try {
                            scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                                .addOnSuccessListener { codes ->
                                    if (!disposed.get() && currentEnabled) codes.firstOrNull()?.rawValue?.let(currentCallback)
                                }
                                .addOnCompleteListener { proxy.close() }
                        } catch (_: Exception) { proxy.close() }
                    }
                    cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                } catch (_: Exception) {
                    if (!disposed.get()) error = "Não foi possível abrir a câmera. Verifique a permissão e tente novamente."
                }
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            disposed.set(true)
            analysis.clearAnalyzer()
            runCatching { provider?.unbind(preview, analysis) }
            scanner.close()
            executor.shutdown()
        }
    }
}
