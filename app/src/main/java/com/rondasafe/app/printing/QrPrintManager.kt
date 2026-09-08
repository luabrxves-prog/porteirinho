package com.rondasafe.app.printing

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.rondasafe.app.R
import com.rondasafe.app.ui.components.generateQrBitmap
import java.io.FileOutputStream

object QrPrintManager {
    private val Navy = Color.rgb(6, 43, 70)
    private val NavyDark = Color.rgb(3, 30, 50)
    private val Blue = Color.rgb(22, 138, 243)
    private val Text = Color.rgb(18, 32, 46)
    private val Muted = Color.rgb(104, 118, 132)
    private val Border = Color.rgb(225, 231, 237)
    private val SoftBackground = Color.rgb(245, 248, 251)

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

                    canvas.drawColor(Color.WHITE)

                    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NavyDark }
                    canvas.drawRect(0f, 0f, 595f, 170f, headerPaint)

                    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Blue }
                    canvas.drawRect(0f, 166f, 595f, 170f, accentPaint)

                    val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = 12f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    drawCenteredText(canvas, "CONTROLE DE RONDA", 48f, eyebrowPaint)

                    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = 31f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    drawCenteredText(canvas, "RondaSafe", 88f, brandPaint)

                    val checkpointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = 20f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    drawCenteredWrappedText(
                        canvas = canvas,
                        text = checkpointName,
                        centerX = 297.5f,
                        firstBaseline = 126f,
                        maxWidth = 475f,
                        lineHeight = 24f,
                        maxLines = 2,
                        paint = checkpointPaint,
                    )

                    val instructionTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Text
                        textSize = 15f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    drawCenteredText(canvas, "ESCANEIE PARA REGISTRAR SUA PASSAGEM", 207f, instructionTitlePaint)

                    val instructionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Muted
                        textSize = 11.5f
                    }
                    drawCenteredText(canvas, "Abra a ronda no aplicativo e aponte a câmera para este código.", 229f, instructionPaint)

                    val cardRect = RectF(83f, 250f, 512f, 679f)
                    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SoftBackground }
                    canvas.drawRoundRect(cardRect, 24f, 24f, cardPaint)

                    val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Border
                        style = Paint.Style.STROKE
                        strokeWidth = 1.5f
                    }
                    canvas.drawRoundRect(cardRect, 24f, 24f, cardBorderPaint)

                    val qrBitmap = generateQrBitmap(token, 1200)
                    val qrDest = Rect(109, 276, 486, 653)
                    canvas.drawBitmap(qrBitmap, null, qrDest, null)

                    val officialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Navy
                        textSize = 12f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    drawCenteredText(canvas, "PONTO OFICIAL DE RONDA", 713f, officialPaint)

                    val helperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Muted
                        textSize = 10.5f
                    }
                    drawCenteredText(canvas, "Mantenha esta placa fixa e visível no local indicado.", 733f, helperPaint)

                    val footerTop = 760f
                    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Border
                        strokeWidth = 1f
                    }
                    canvas.drawLine(48f, footerTop, 547f, footerTop, dividerPaint)

                    context.getDrawable(R.drawable.breves_logo_qr)?.let { logo ->
                        logo.setBounds(50, 777, 90, 822)
                        logo.draw(canvas)
                    }

                    val poweredPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Muted
                        textSize = 9.5f
                    }
                    canvas.drawText("Tecnologia por Breves", 103f, 794f, poweredPaint)

                    val sitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Blue
                        textSize = 13f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    canvas.drawText("brevestech.com", 103f, 814f, sitePaint)

                    val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Muted
                        textSize = 8.5f
                        textAlign = Paint.Align.RIGHT
                    }
                    val trace = buildString {
                        append("QR v${version ?: "-"}")
                        fingerprint?.takeIf { it.isNotBlank() }?.let { append("  •  ID $it") }
                    }
                    canvas.drawText(trace, 545f, 810f, tracePaint)

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
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .build(),
        )
    }

    private fun drawCenteredText(
        canvas: android.graphics.Canvas,
        text: String,
        baseline: Float,
        paint: Paint,
    ) {
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText(text, 297.5f, baseline, paint)
    }

    private fun drawCenteredWrappedText(
        canvas: android.graphics.Canvas,
        text: String,
        centerX: Float,
        firstBaseline: Float,
        maxWidth: Float,
        lineHeight: Float,
        maxLines: Int,
        paint: Paint,
    ) {
        val words = text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return

        val allLines = mutableListOf<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isBlank()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth || current.isBlank()) {
                current = candidate
            } else {
                allLines += current
                current = word
            }
        }
        if (current.isNotBlank()) allLines += current

        val lines = allLines.take(maxLines).toMutableList()
        if (allLines.size > maxLines && lines.isNotEmpty()) {
            var last = lines.last()
            while (last.isNotEmpty() && paint.measureText("$last…") > maxWidth) {
                last = last.dropLast(1)
            }
            lines[lines.lastIndex] = "$last…"
        }

        paint.textAlign = Paint.Align.CENTER
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, centerX, firstBaseline + (index * lineHeight), paint)
        }
    }
}
