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
    private const val PAGE_WIDTH = 298
    private const val PAGE_HEIGHT = 420
    private const val PAGE_CENTER_X = PAGE_WIDTH / 2f

    private val Navy = Color.rgb(6, 43, 70)
    private val NavyDark = Color.rgb(3, 30, 50)
    private val Blue = Color.rgb(22, 138, 243)
    private val Text = Color.rgb(18, 32, 46)
    private val Muted = Color.rgb(104, 118, 132)
    private val Border = Color.rgb(225, 231, 237)
    private val SoftBackground = Color.rgb(245, 248, 251)
    private val BlueSoft = Color.rgb(234, 244, 255)

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
                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                    val page = document.startPage(pageInfo)
                    val canvas = page.canvas

                    canvas.drawColor(Color.WHITE)

                    val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NavyDark }
                    canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 76f, headerPaint)

                    val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Blue }
                    canvas.drawRect(0f, 72f, PAGE_WIDTH.toFloat(), 76f, accentPaint)

                    val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = 19f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    drawCenteredText(canvas, "RondaSafe", 31f, brandPaint)

                    val eyebrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.WHITE
                        textSize = 8f
                        letterSpacing = 0.12f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    drawCenteredText(canvas, "PONTO DE RONDA", 54f, eyebrowPaint)

                    val checkpointRect = RectF(18f, 86f, 280f, 116f)
                    val checkpointBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BlueSoft }
                    canvas.drawRoundRect(checkpointRect, 13f, 13f, checkpointBg)

                    val checkpointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Navy
                        textSize = 13f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    drawCenteredSingleLineEllipsized(
                        canvas = canvas,
                        text = checkpointName,
                        baseline = 106f,
                        maxWidth = 232f,
                        paint = checkpointPaint,
                    )

                    val instructionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Text
                        textSize = 8.8f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    drawCenteredText(canvas, "Escaneie para registrar sua passagem", 134f, instructionPaint)

                    val qrFrame = RectF(50f, 144f, 248f, 342f)
                    val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SoftBackground }
                    canvas.drawRoundRect(qrFrame, 12f, 12f, framePaint)

                    val frameBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Border
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                    }
                    canvas.drawRoundRect(qrFrame, 12f, 12f, frameBorder)

                    val qrBitmap = generateQrBitmap(token, 1200)
                    val qrDest = Rect(60, 154, 238, 332)
                    canvas.drawBitmap(qrBitmap, null, qrDest, null)

                    val officialBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BlueSoft }
                    val officialRect = RectF(104f, 350f, 194f, 370f)
                    canvas.drawRoundRect(officialRect, 10f, 10f, officialBg)

                    val officialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Navy
                        textSize = 8f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    drawCenteredText(canvas, "✓  Ponto oficial", 363f, officialPaint)

                    val tracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Muted
                        textSize = 7f
                    }
                    val trace = buildString {
                        append("QR v${version ?: "-"}")
                        fingerprint?.takeIf { it.isNotBlank() }?.let { append(" • ID $it") }
                    }
                    drawCenteredText(canvas, trace, 381f, tracePaint)

                    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Border
                        strokeWidth = 1f
                    }
                    canvas.drawLine(30f, 389f, 89f, 389f, dividerPaint)
                    canvas.drawLine(209f, 389f, 268f, 389f, dividerPaint)

                    context.getDrawable(R.drawable.breves_logo_qr)?.let { logo ->
                        logo.setBounds(99, 388, 123, 414)
                        logo.draw(canvas)
                    }

                    val brevesPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Navy
                        textSize = 10.5f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }
                    canvas.drawText("B R E V E S", 131f, 402f, brevesPaint)

                    val sitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Blue
                        textSize = 8.5f
                        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                        textAlign = Paint.Align.CENTER
                    }
                    canvas.drawText("brevestech.com", PAGE_CENTER_X, 416f, sitePaint)

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
                .setMediaSize(PrintAttributes.MediaSize.ISO_A6)
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
        canvas.drawText(text, PAGE_CENTER_X, baseline, paint)
    }

    private fun drawCenteredSingleLineEllipsized(
        canvas: android.graphics.Canvas,
        text: String,
        baseline: Float,
        maxWidth: Float,
        paint: Paint,
    ) {
        var displayed = text.trim()
        if (paint.measureText(displayed) > maxWidth) {
            while (displayed.isNotEmpty() && paint.measureText("$displayed…") > maxWidth) {
                displayed = displayed.dropLast(1)
            }
            displayed += "…"
        }
        drawCenteredText(canvas, displayed, baseline, paint)
    }
}
