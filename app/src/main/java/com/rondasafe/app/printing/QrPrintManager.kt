package com.rondasafe.app.printing

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.rondasafe.app.ui.components.generateQrBitmap
import java.io.FileOutputStream

object QrPrintManager {
    fun printActiveQr(
        context: Context,
        checkpointName: String,
        token: String,
        version: Int?,
        fingerprint: String?,
    ) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val jobName = "RondaSafe - $checkpointName"

        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes,
                cancellationSignal: CancellationSignal,
                callback: LayoutResultCallback,
                extras: android.os.Bundle?,
            ) {
                if (cancellationSignal.isCanceled) {
                    callback.onLayoutCancelled()
                    return
                }

                callback.onLayoutFinished(
                    PrintDocumentInfo.Builder("rondasafe-${checkpointName}.pdf")
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(1)
                        .build(),
                    true,
                )
            }

            override fun onWrite(
                pages: Array<out android.print.PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal,
                callback: WriteResultCallback,
            ) {
                val document = PdfDocument()
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas

                    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        textSize = 28f
                        isFakeBoldText = true
                    }
                    val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f }
                    val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f }

                    canvas.drawText("RondaSafe", 48f, 64f, titlePaint)
                    canvas.drawText(checkpointName, 48f, 100f, bodyPaint)

                    val qrBitmap = generateQrBitmap(token, 900)
                    val dest = android.graphics.Rect(72, 145, 523, 596)
                    canvas.drawBitmap(qrBitmap, null, dest, null)

                    canvas.drawText("QR ativo • versão ${version ?: "-"}", 48f, 640f, bodyPaint)
                    canvas.drawText("Identificação: ${fingerprint ?: "-"}", 48f, 670f, smallPaint)
                    canvas.drawText("Use este QR Code somente neste ponto de ronda.", 48f, 710f, smallPaint)

                    document.finishPage(page)

                    FileOutputStream(destination.fileDescriptor).use { output ->
                        document.writeTo(output)
                    }
                    callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                } catch (t: Throwable) {
                    callback.onWriteFailed(t.message)
                } finally {
                    document.close()
                }
            }
        }

        printManager.print(
            jobName,
            adapter,
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .build(),
        )
    }
}
