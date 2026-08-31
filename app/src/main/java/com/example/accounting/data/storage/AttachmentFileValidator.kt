package com.example.accounting.data.storage

import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Phase 7J-B.2 hardening - validates a picked attachment BEFORE any durable copy, [com.example.accounting.domain.rendering.DocumentAsset],
 * or [com.example.accounting.data.local.entity.VoucherDocumentReferenceEntity] is created, so a
 * rejected file never reaches durable storage or Room at all ("no partial file, no DocumentAsset,
 * no VoucherDocumentReference" for a rejected upload).
 *
 * Two layers, deliberately never trusting either alone:
 * 1. The resolved filename's extension must be one of the seven accepted formats.
 * 2. For formats with a real lightweight container/magic-number signature (JPEG/PNG/WEBP/PDF/
 *    XLS/XLSX), the actual leading bytes must match it - an OS-reported MIME type or a
 *    user-controlled extension is never sufficient by itself. CSV has no binary signature, so it
 *    is instead checked for plausible textual/tabular content (mostly-printable bytes, not
 *    arbitrary binary data renamed to `.csv`).
 *
 * XLSX validation uses only [java.util.zip] (already part of the JDK/Android runtime - zero new
 * dependency) to confirm both the ZIP container signature and the presence of an `xl/` entry,
 * which is what actually distinguishes an XLSX from any other OOXML container (`.docx` uses
 * `word/`, `.pptx` uses `ppt/`) without a full spreadsheet-parsing dependency. XLS (the legacy
 * binary/OLE2 format) is signature-only - deliberately not deep-parsed, since that would require
 * a real dependency; this is a known, reported limitation, not a silently-skipped check.
 */
object AttachmentFileValidator {

    enum class AttachmentCategory { IMAGE, PDF, SPREADSHEET, TABULAR }

    sealed class ValidationResult {
        data class Accepted(val category: AttachmentCategory, val mimeType: String) : ValidationResult()
        data class Rejected(val reason: String) : ValidationResult()
    }

    val USER_FACING_ALLOWED_TYPES_MESSAGE = "Unsupported file type. Select JPG, PNG, WEBP, PDF, CSV, XLS or XLSX."

    /** The canonical, validator-owned MIME type per accepted extension - the source of truth for
     * [com.example.accounting.domain.rendering.DocumentAsset.mimeType], never the raw
     * `ContentResolver`-reported type (which some providers report unreliably, e.g. a generic
     * `application/octet-stream` for a real XLSX). */
    private val EXTENSION_TO_CATEGORY_AND_MIME: Map<String, Pair<AttachmentCategory, String>> = mapOf(
        "jpg" to (AttachmentCategory.IMAGE to "image/jpeg"),
        "jpeg" to (AttachmentCategory.IMAGE to "image/jpeg"),
        "png" to (AttachmentCategory.IMAGE to "image/png"),
        "webp" to (AttachmentCategory.IMAGE to "image/webp"),
        "pdf" to (AttachmentCategory.PDF to "application/pdf"),
        "csv" to (AttachmentCategory.TABULAR to "text/csv"),
        "xls" to (AttachmentCategory.SPREADSHEET to "application/vnd.ms-excel"),
        "xlsx" to (AttachmentCategory.SPREADSHEET to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    )

    private val JPEG_SIGNATURE = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    private val OLE2_SIGNATURE = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte())
    private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04)
    private const val HEADER_PEEK_BYTES = 4096

    /**
     * [openStream] must return a FRESH [InputStream] positioned at the start of the source each
     * time it is invoked (this function may call it more than once, e.g. once for the header peek
     * and once more internally for the XLSX zip-entry check) - the caller's own subsequent full
     * copy is a separate stream open and is unaffected either way.
     */
    fun validate(originalFileName: String?, openStream: () -> InputStream?): ValidationResult {
        val extension = originalFileName?.substringAfterLast('.', "")?.lowercase()?.takeIf { it.isNotBlank() }
            ?: return ValidationResult.Rejected(USER_FACING_ALLOWED_TYPES_MESSAGE)
        val (category, mimeType) = EXTENSION_TO_CATEGORY_AND_MIME[extension]
            ?: return ValidationResult.Rejected(USER_FACING_ALLOWED_TYPES_MESSAGE)

        val header = try {
            openStream()?.use { readBounded(it, HEADER_PEEK_BYTES) }
        } catch (e: Exception) {
            null
        }
        if (header == null || header.isEmpty()) {
            return ValidationResult.Rejected("Could not read the selected file. Choose a different file.")
        }

        val signatureOk = when (extension) {
            "jpg", "jpeg" -> header.startsWithBytes(JPEG_SIGNATURE)
            "png" -> header.startsWithBytes(PNG_SIGNATURE)
            "webp" -> header.size >= 12 &&
                header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" &&
                header.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP"
            "pdf" -> header.size >= 4 && header.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "%PDF"
            "xls" -> header.startsWithBytes(OLE2_SIGNATURE)
            "xlsx" -> header.startsWithBytes(ZIP_SIGNATURE) && hasXlsxEntry(openStream)
            "csv" -> looksLikePlausibleText(header)
            else -> false
        }
        if (!signatureOk) {
            return ValidationResult.Rejected("The selected file does not look like a valid ${extension.uppercase()} file.")
        }
        return ValidationResult.Accepted(category, mimeType)
    }

    private fun ByteArray.startsWithBytes(signature: ByteArray): Boolean =
        size >= signature.size && copyOfRange(0, signature.size).contentEquals(signature)

    /** XLSX-specific: an OOXML ZIP container always has an `xl/` entry (Word uses `word/`,
     * PowerPoint uses `ppt/`) - the cheapest real signal that distinguishes XLSX from any other
     * ZIP-based Office format without a full spreadsheet parser. Reads only entry names, never
     * entry contents; gives up (rejects) after 50 entries rather than scanning an entire archive. */
    private fun hasXlsxEntry(openStream: () -> InputStream?): Boolean = try {
        openStream()?.use { stream ->
            ZipInputStream(stream).use { zip ->
                var checked = 0
                var entry = zip.nextEntry
                var found = false
                while (entry != null && checked < 50 && !found) {
                    if (entry.name.startsWith("xl/")) found = true
                    entry = zip.nextEntry
                    checked++
                }
                found
            }
        } ?: false
    } catch (e: Exception) {
        false
    }

    /** CSV has no binary signature - this instead rejects anything that looks like arbitrary
     * binary data renamed to `.csv` (a real image/PDF/executable, etc.): a plausible text file's
     * bytes are overwhelmingly printable ASCII/UTF-8, with none of the control bytes (other than
     * tab/newline/carriage-return) that dense binary data reliably contains. */
    private fun looksLikePlausibleText(header: ByteArray): Boolean {
        if (header.isEmpty()) return false
        var suspiciousBytes = 0
        for (b in header) {
            val i = b.toInt() and 0xFF
            val isControlButAllowed = i == 0x09 || i == 0x0A || i == 0x0D // tab, LF, CR
            val isPrintableOrUtf8Continuation = i in 0x20..0x7E || i >= 0x80
            if (!isControlButAllowed && !isPrintableOrUtf8Continuation) suspiciousBytes++
        }
        return suspiciousBytes.toDouble() / header.size < 0.01
    }

    private fun readBounded(stream: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(maxBytes)
        var totalRead = 0
        while (totalRead < maxBytes) {
            val read = stream.read(buffer, totalRead, maxBytes - totalRead)
            if (read == -1) break
            totalRead += read
        }
        return buffer.copyOf(totalRead)
    }
}
