package com.rondasafe.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

private fun generateQrBitmap(content: String, size: Int = 768): Bitmap {
    val matrix: BitMatrix = MultiFormatWriter().encode(
        content,
        BarcodeFormat.QR_CODE,
        size,
        size,
    )
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        setPixels(pixels, 0, size, 0, 0, size, size)
    }
}

@Composable
fun QrCodeImage(
    content: String,
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(content) { generateQrBitmap(content) }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "QR Code do ponto de ronda",
        modifier = modifier,
    )
}
