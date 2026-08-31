package com.example.accounting.data.storage

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Copies a picked SAF/Photo-Picker source into durable app-private storage (`context.filesDir`,
 * survives cache eviction) - Phase 7J-B.2's first caller needing a copy that must outlive
 * process/cache churn, since the user expects "their receipt photo" to persist for the life of the
 * voucher. Deliberately distinct from `copyUriToCacheFile` (`presentation/MainAppScreen.kt`), which
 * stays `cacheDir`-only and is out of scope for this slice - that existing behavior for
 * logo/signature/OCR/import is untouched.
 *
 * [copyToDurableStorage] never exposes a physical path/file/directory outward except as the final
 * [AttachmentCopyResult.storageReference] string - callers (a future ViewModel) pass that string
 * straight into `AccountingRepository.createDocumentAsset`, exactly as every other
 * `DocumentAsset.storageReference` producer already does. The domain layer never sees a [File]/[Uri].
 *
 * [copyToDurableStorage] is [InputStream]/[File]-based only, directly unit-testable against a
 * plain temp directory with no Android `Context` required. It never resolves a MIME type itself
 * (the earlier `Context`/`Uri` convenience overload that did was removed - it silently trusted
 * `ContentResolver.getType`, bypassing [com.example.accounting.data.storage.AttachmentFileValidator]'s
 * signature-based validation entirely) - callers must resolve/validate the file and its MIME type
 * themselves (see [com.example.accounting.presentation.viewmodel.AccountingViewModel.attachDocumentToVoucher])
 * before calling this function, and pass the validated MIME type as [mimeTypeHint].
 */
object AttachmentStorageAdapter {
    const val ATTACHMENTS_SUBDIRECTORY = "voucher_attachments"

    /** Thrown on any copy failure - a caller must never construct a
     * [com.example.accounting.domain.rendering.DocumentAsset] row from a broken/missing file. */
    class AttachmentCopyException(message: String, cause: Throwable? = null) : IOException(message, cause)

    data class AttachmentCopyResult(
        val storageReference: String,
        val mimeType: String,
        val sizeBytes: Long
    )

    /**
     * Copies [input] into `<baseDir>/voucher_attachments/<random-name>[.ext]`, preserving the
     * original extension (from [originalFileName]) when available, and a caller-resolved
     * [mimeTypeHint] (falls back to `application/octet-stream`, never a hardcoded guess like
     * `"image/jpeg"`). The destination name is always a fresh [UUID] - collisions are structurally
     * impossible, never a same-name overwrite. On any I/O failure, or if the copy somehow produces
     * an empty file, the partial file is deleted and [AttachmentCopyException] is thrown - this
     * function never returns a result pointing at a broken file.
     */
    fun copyToDurableStorage(
        input: InputStream,
        baseDir: File,
        originalFileName: String?,
        mimeTypeHint: String?
    ): AttachmentCopyResult {
        val dir = File(baseDir, ATTACHMENTS_SUBDIRECTORY).apply { mkdirs() }
        val extension = originalFileName?.substringAfterLast('.', "")?.takeIf { it.isNotBlank() }
        val safeName = if (extension != null) "${UUID.randomUUID()}.$extension" else UUID.randomUUID().toString()
        val destination = File(dir, safeName)
        try {
            destination.outputStream().use { output -> input.copyTo(output) }
        } catch (e: IOException) {
            destination.delete()
            throw AttachmentCopyException("Failed to copy attachment into durable storage", e)
        }
        if (destination.length() == 0L) {
            destination.delete()
            throw AttachmentCopyException("Copied attachment file is empty - refusing to create a broken reference")
        }
        return AttachmentCopyResult(
            storageReference = destination.absolutePath,
            mimeType = mimeTypeHint?.takeIf { it.isNotBlank() } ?: "application/octet-stream",
            sizeBytes = destination.length()
        )
    }
}
