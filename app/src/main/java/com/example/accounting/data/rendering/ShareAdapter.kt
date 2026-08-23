package com.example.accounting.data.rendering

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Builds a share [Intent] for an already-generated document artifact (Phase 7D, Section 17) - a
 * pure function of a [File]; never mutates the invoice/voucher it came from, never regenerates the
 * document. Not wired into any screen (UI is Phase 7J) - callable infrastructure only. Requires the
 * `com.example.accounting.fileprovider` `<provider>` declared in `AndroidManifest.xml`.
 */
object ShareAdapter {
    private const val FILE_PROVIDER_AUTHORITY = "com.example.accounting.fileprovider"

    fun buildShareIntent(context: Context, file: File, mimeType: String = "application/pdf"): Intent {
        val uri = FileProvider.getUriForFile(context, FILE_PROVIDER_AUTHORITY, file)
        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
