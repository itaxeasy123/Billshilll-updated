package com.example.accounting.data.rendering

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.example.accounting.domain.rendering.TabularReportData
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a [TabularReportData] to a PDF file using Android's built-in `android.graphics.pdf.PdfDocument`
 * (no third-party PDF library, matching [PdfDocumentRenderer]'s own reasoning) - a sibling to that
 * renderer, not a replacement: [PdfDocumentRenderer] stays the only renderer for
 * [com.example.accounting.domain.rendering.DocumentData] (Sale/Purchase/Note trade documents);
 * this one is the only renderer for tabular financial reports (Trial Balance/P&L/Balance
 * Sheet/Day Book). Paginates automatically when rows overflow one page - a real Trial
 * Balance/Day Book can run well past what fits on a single A4 page, unlike a single-invoice
 * document.
 */
object TabularPdfRenderer {
    private const val A4_SHORT_SIDE = 595 // A4 at 72dpi
    private const val A4_LONG_SIDE = 842
    private const val MARGIN = 36f
    private const val ROW_HEIGHT = 16f
    private const val CELL_PADDING = 4f
    /** Beyond this many columns, a portrait page can't give each column enough width to stay
     * readable (Section 4: "portrait/landscape suitability") - landscape roughly doubles the
     * usable width for the same margins. Trial Balance (8 columns) and Day Book (7 columns) need
     * this; Profit & Loss/Balance Sheet (2-4 columns) stay portrait. */
    private const val LANDSCAPE_COLUMN_THRESHOLD = 5

    fun render(context: Context, data: TabularReportData): File {
        val landscape = data.columnHeaders.size >= LANDSCAPE_COLUMN_THRESHOLD
        val pageWidth = if (landscape) A4_LONG_SIDE else A4_SHORT_SIDE
        val pageHeight = if (landscape) A4_SHORT_SIDE else A4_LONG_SIDE

        val pdf = PdfDocument()
        val titlePaint = Paint().apply { color = Color.BLACK; textSize = 14f; isFakeBoldText = true }
        val subtitlePaint = Paint().apply { color = Color.DKGRAY; textSize = 10f }
        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 9f; isFakeBoldText = true }
        val cellPaint = Paint().apply { color = Color.BLACK; textSize = 9f }
        val totalsPaint = Paint().apply { color = Color.BLACK; textSize = 9f; isFakeBoldText = true }

        val columnCount = data.columnHeaders.size.coerceAtLeast(1)
        val usableWidth = pageWidth - 2 * MARGIN
        val colWidth = usableWidth / columnCount

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y = MARGIN

        /** Truncates [value] with an ellipsis if it would overflow [maxWidth] - `Canvas.drawText`
         * never wraps or clips on its own, so a long ledger/account name would otherwise overlap
         * the next column's text (Section 4: "long ledger/account names"). */
        fun fitText(value: String, paint: Paint, maxWidth: Float): String {
            if (paint.measureText(value) <= maxWidth) return value
            var truncated = value
            while (truncated.isNotEmpty() && paint.measureText("$truncated…") > maxWidth) {
                truncated = truncated.dropLast(1)
            }
            return "$truncated…"
        }

        fun drawRow(values: List<String>, paint: Paint) {
            values.forEachIndexed { index, value ->
                canvas.drawText(fitText(value, paint, colWidth - CELL_PADDING), MARGIN + index * colWidth, y, paint)
            }
            y += ROW_HEIGHT
        }

        fun newPage() {
            pdf.finishPage(page)
            pageNumber += 1
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = MARGIN
            drawRow(data.columnHeaders, headerPaint)
            y += 4
        }

        canvas.drawText(data.title, MARGIN, y, titlePaint)
        y += 18f
        if (data.subtitle.isNotBlank()) {
            canvas.drawText(fitText(data.subtitle, subtitlePaint, usableWidth), MARGIN, y, subtitlePaint)
            y += 16f
        }
        y += 6f
        drawRow(data.columnHeaders, headerPaint)
        y += 4

        if (data.rows.isEmpty()) {
            canvas.drawText("No data for this period.", MARGIN, y, cellPaint)
            y += ROW_HEIGHT
        }
        for (row in data.rows) {
            if (y > pageHeight - MARGIN - ROW_HEIGHT) newPage()
            drawRow(row, cellPaint)
        }

        data.totalsRow?.let {
            if (y > pageHeight - MARGIN - ROW_HEIGHT) newPage()
            y += 4
            drawRow(it, totalsPaint)
        }

        pdf.finishPage(page)

        val file = File(context.cacheDir, "${data.title.lowercase().replace(" ", "_")}_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }
}
