package com.rondasafe.app

import android.Manifest
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.rondasafe.app.data.local.OfflineSyncStatus
import com.rondasafe.app.ui.portaria.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CameraRegressionTest {
    @get:Rule val cameraPermission = GrantPermissionRule.grant(Manifest.permission.CAMERA)
    @get:Rule val ui = createComposeRule()
    private fun findPreview(view: View): PreviewView? {
        if (view is PreviewView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) findPreview(view.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun texturePreviewStreamsInsideBoundsAndSurvivesBusyFlagChanges() {
        var root: View? = null
        var enabled by mutableStateOf(true)
        ui.setContent {
            val view = LocalView.current
            SideEffect { root = view.rootView }
            MaterialTheme { SafeQrCamera(Modifier.fillMaxWidth().height(260.dp), enabled) {} }
        }
        lateinit var preview: PreviewView
        ui.waitUntil(20_000) { root?.let(::findPreview) != null }
        ui.runOnIdle { preview = findPreview(root!!)!! }
        ui.waitUntil(30_000) { preview.previewStreamState.value == PreviewView.StreamState.STREAMING }
        assertEquals(PreviewView.ImplementationMode.COMPATIBLE, preview.implementationMode)
        ui.onNodeWithTag("qr_preview").assertHeightIsEqualTo(260.dp).assertIsDisplayed()
        ui.runOnIdle { enabled = false }
        ui.waitForIdle()
        ui.runOnIdle { assertSame(preview, findPreview(root!!)); enabled = true }
        ui.waitForIdle()
        ui.waitUntil(10_000) { preview.previewStreamState.value == PreviewView.StreamState.STREAMING }
        val image = ui.onRoot().captureToImage().asAndroidBitmap()
        val out = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir,"camera-preview.png")
        out.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
    }
    @Test fun exactCameraDecoderReadsExistingQrPayloadFormat() {
        val token = "rondasafe:v1:camera-regression-no-server-write"
        val matrix = QRCodeWriter().encode(token,BarcodeFormat.QR_CODE,600,600)
        val pixels = IntArray(600*600) { i -> if(matrix[i%600,i/600]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        val bitmap = Bitmap.createBitmap(pixels,600,600,Bitmap.Config.ARGB_8888)
        val scanner = createQrBarcodeScanner()
        try {
            val codes = Tasks.await(scanner.process(InputImage.fromBitmap(bitmap,0)),20,TimeUnit.SECONDS)
            assertTrue(codes.any { it.rawValue==token })
        } finally { scanner.close(); bitmap.recycle() }
    }
    @Test fun verboseSyncErrorIsCompactButStillAccessible() {
        ui.setContent {
            MaterialTheme {
                OfflineSyncStatusContent(OfflineSyncStatus(failedPermanent=1,latestError="GUARD_ALREADY_HAS_ACTIVE_SHIFT"),compact=true)
            }
        }
        ui.onNodeWithText("ver detalhes",substring=true).performClick()
        ui.onNodeWithText("GUARD_ALREADY_HAS_ACTIVE_SHIFT").assertIsDisplayed()
        ui.onNodeWithText("Fechar").performClick()
        ui.onNodeWithText("GUARD_ALREADY_HAS_ACTIVE_SHIFT").assertDoesNotExist()
    }
}
