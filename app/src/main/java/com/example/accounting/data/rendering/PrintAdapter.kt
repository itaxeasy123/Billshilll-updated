package com.example.accounting.data.rendering

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import java.io.File

/**
 * Prints an already-generated PDF [File] (Phase 7D, Section 16) via Android's `PrintManager` -
 * consumes the same artifact [PdfDocumentRenderer] produces, never recomputes anything or opens a
 * second invoice-calculation path for printing. Not wired into any screen (UI is Phase 7J) - this
 * is callable infrastructure only.
 */
object PrintAdapter {
    fun print(context: Context, file: File, jobName: String) {
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = FilePrintDocumentAdapter(context, file, jobName)
        printManager.print(jobName, adapter, PrintAttributes.Builder().build())
    }
}

/** Wraps a plain [File] (already-rendered PDF) as a `PrintDocumentAdapter` - reads bytes only,
 * writes nothing back, never touches accounting data. */
private class FilePrintDocumentAdapter(
    private val context: Context,
    private val file: File,
    private val jobName: String
) : android.print.PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: android.os.CancellationSignal?,
        callback: LayoutResultCallback,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        val info = android.print.PrintDocumentInfo.Builder(file.name)
            .setContentType(android.print.PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<out android.print.PageRange>?,
        destination: android.os.ParcelFileDescriptor,
        cancellationSignal: android.os.CancellationSignal?,
        callback: WriteResultCallback
    ) {
        try {
            file.inputStream().use { input ->
                java.io.FileOutputStream(destination.fileDescriptor).use { output ->
                    input.copyTo(output)
                }
            }
            callback.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback.onWriteFailed(e.message)
        }
    }
}
