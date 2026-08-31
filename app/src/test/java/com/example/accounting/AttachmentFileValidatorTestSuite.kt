package com.example.accounting

import com.example.accounting.data.storage.AttachmentFileValidator
import com.example.accounting.data.storage.AttachmentFileValidator.AttachmentCategory
import com.example.accounting.data.storage.AttachmentFileValidator.ValidationResult
import com.example.accounting.data.storage.AttachmentStorageAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Phase 7J-B.2 attachment-hardening fix - [AttachmentFileValidator], pure-JVM (no Android Context
 * needed, matching this project's existing preference over Robolectric wherever possible). Every
 * fixture below uses the REAL magic-number/container bytes each format actually has - never a
 * fake/stub signature - so a passing test is a genuine claim about the production signature check.
 */
class AttachmentFileValidatorTestSuite {

    private fun streamOf(bytes: ByteArray): () -> InputStream = { ByteArrayInputStream(bytes) }

    private val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10, 0x4A, 0x46, 0x49, 0x46) + ByteArray(20)
    private val pngBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(20)
    private val webpBytes = "RIFF".toByteArray(Charsets.US_ASCII) + byteArrayOf(0, 0, 0, 0) + "WEBP".toByteArray(Charsets.US_ASCII) + ByteArray(10)
    private val pdfBytes = "%PDF-1.4\n%âãÏÓ\n1 0 obj".toByteArray(Charsets.ISO_8859_1)
    private val xlsBytes = byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0xA1.toByte(), 0xB1.toByte(), 0x1A, 0xE1.toByte()) + ByteArray(50)
    private val csvBytes = "Date,Ledger,Amount\n2026-08-28,Cash in Hand,5000.00\n".toByteArray(Charsets.UTF_8)

    /** A genuine, minimal ZIP with an `xl/` entry - a real XLSX signal, not a stub. */
    private fun realXlsxBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write("<workbook/>".toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    /** A genuine ZIP that is NOT an XLSX (a DOCX-shaped container) - the "misleading extension"
     * case: real ZIP magic bytes, wrong internal structure. */
    private fun docxShapedZipBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write("<document/>".toByteArray())
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    @Test
    fun testJpg_accepted() {
        val result = AttachmentFileValidator.validate("receipt.jpg", streamOf(jpegBytes))
        assertTrue(result is ValidationResult.Accepted)
        assertEquals(AttachmentCategory.IMAGE, (result as ValidationResult.Accepted).category)
        assertEquals("image/jpeg", result.mimeType)
    }

    @Test
    fun testJpeg_accepted() {
        val result = AttachmentFileValidator.validate("receipt.jpeg", streamOf(jpegBytes))
        assertTrue(result is ValidationResult.Accepted)
    }

    @Test
    fun testPng_accepted() {
        val result = AttachmentFileValidator.validate("scan.png", streamOf(pngBytes))
        assertTrue(result is ValidationResult.Accepted)
        assertEquals("image/png", (result as ValidationResult.Accepted).mimeType)
    }

    @Test
    fun testWebp_accepted() {
        val result = AttachmentFileValidator.validate("photo.webp", streamOf(webpBytes))
        assertTrue(result is ValidationResult.Accepted)
        assertEquals("image/webp", (result as ValidationResult.Accepted).mimeType)
    }

    @Test
    fun testPdf_accepted() {
        val result = AttachmentFileValidator.validate("statement.pdf", streamOf(pdfBytes))
        assertTrue(result is ValidationResult.Accepted)
        assertEquals(AttachmentCategory.PDF, (result as ValidationResult.Accepted).category)
        assertEquals("application/pdf", result.mimeType)
    }

    @Test
    fun testCsv_accepted() {
        val result = AttachmentFileValidator.validate("ledger_export.csv", streamOf(csvBytes))
        assertTrue(result is ValidationResult.Accepted)
        assertEquals(AttachmentCategory.TABULAR, (result as ValidationResult.Accepted).category)
    }

    @Test
    fun testXls_accepted() {
        val result = AttachmentFileValidator.validate("book.xls", streamOf(xlsBytes))
        assertTrue(result is ValidationResult.Accepted)
        assertEquals(AttachmentCategory.SPREADSHEET, (result as ValidationResult.Accepted).category)
        assertEquals("application/vnd.ms-excel", result.mimeType)
    }

    @Test
    fun testXlsx_accepted() {
        val result = AttachmentFileValidator.validate("book.xlsx", streamOf(realXlsxBytes()))
        assertTrue(result is ValidationResult.Accepted)
        assertEquals(AttachmentCategory.SPREADSHEET, (result as ValidationResult.Accepted).category)
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", result.mimeType)
    }

    @Test
    fun testUnsupportedExtension_rejected() {
        val exeBytes = byteArrayOf(0x4D, 0x5A) + ByteArray(30) // "MZ" - real EXE/APK-adjacent header
        for (name in listOf("installer.exe", "app.apk", "archive.zip", "archive.rar", "report.docx", "deck.pptx", "notes.txt")) {
            val result = AttachmentFileValidator.validate(name, streamOf(exeBytes))
            assertTrue("$name must be rejected - not one of the 7 accepted formats", result is ValidationResult.Rejected)
        }
    }

    @Test
    fun testNoExtension_rejected() {
        val result = AttachmentFileValidator.validate("IMG20260828", streamOf(jpegBytes))
        assertTrue(result is ValidationResult.Rejected)
    }

    @Test
    fun testNullFileName_rejected() {
        val result = AttachmentFileValidator.validate(null, streamOf(jpegBytes))
        assertTrue(result is ValidationResult.Rejected)
    }

    @Test
    fun testMisleadingExtension_realJpegRenamedToPdf_rejected() {
        // Real JPEG bytes, but named .pdf - the extension says PDF, the signature says otherwise.
        val result = AttachmentFileValidator.validate("fake.pdf", streamOf(jpegBytes))
        assertTrue("A JPEG's real bytes under a .pdf name must fail the PDF signature check", result is ValidationResult.Rejected)
    }

    @Test
    fun testMisleadingExtension_docxRenamedToXlsx_rejected() {
        // A real ZIP (so the raw ZIP-signature check alone would pass), but shaped like a DOCX, not
        // an XLSX - only the xl/ entry check catches this, proving the deeper validation matters.
        val result = AttachmentFileValidator.validate("fake.xlsx", streamOf(docxShapedZipBytes()))
        assertTrue("A DOCX-shaped ZIP under a .xlsx name must be rejected (no xl/ entry)", result is ValidationResult.Rejected)
    }

    @Test
    fun testEmptyFile_rejected() {
        val result = AttachmentFileValidator.validate("empty.jpg", streamOf(ByteArray(0)))
        assertTrue(result is ValidationResult.Rejected)
    }

    @Test
    fun testUnreadableSource_rejected() {
        val result = AttachmentFileValidator.validate("receipt.jpg") { null }
        assertTrue("A source that cannot even be opened must be rejected, never crash", result is ValidationResult.Rejected)
    }

    @Test
    fun testInvalidPdf_wrongSignature_rejected() {
        val notReallyAPdf = "This is just plain text pretending to be a PDF".toByteArray()
        val result = AttachmentFileValidator.validate("fake.pdf", streamOf(notReallyAPdf))
        assertTrue(result is ValidationResult.Rejected)
    }

    @Test
    fun testInvalidImage_wrongSignature_rejected() {
        val notReallyAnImage = "not an image".toByteArray()
        val result = AttachmentFileValidator.validate("fake.png", streamOf(notReallyAnImage))
        assertTrue(result is ValidationResult.Rejected)
    }

    @Test
    fun testInvalidXls_wrongSignature_rejected() {
        val notReallyXls = "plain text".toByteArray()
        val result = AttachmentFileValidator.validate("fake.xls", streamOf(notReallyXls))
        assertTrue(result is ValidationResult.Rejected)
    }

    @Test
    fun testBinaryDataRenamedToCsv_rejected() {
        // Dense binary bytes (few printable characters) must not pass as "plausible tabular text".
        val binaryJunk = ByteArray(200) { (it % 256).toByte() }
        val result = AttachmentFileValidator.validate("fake.csv", streamOf(binaryJunk))
        assertTrue("Arbitrary binary data renamed to .csv must be rejected", result is ValidationResult.Rejected)
    }

    @Test
    fun testAcceptedFile_thenCopy_isStoredDurably() {
        // Full pipeline (Part 9, item 14): validate -> accept -> copyToDurableStorage -> real file
        // on disk. Both pieces are plain-JVM testable; this proves they compose correctly together.
        val baseDir = java.nio.file.Files.createTempDirectory("ledgerprime_validator_test_").toFile()
        try {
            val validation = AttachmentFileValidator.validate("receipt.jpg", streamOf(jpegBytes))
            assertTrue(validation is ValidationResult.Accepted)
            val accepted = validation as ValidationResult.Accepted

            val copyResult = AttachmentStorageAdapter.copyToDurableStorage(
                ByteArrayInputStream(jpegBytes), baseDir, "receipt.jpg", accepted.mimeType
            )
            val destination = java.io.File(copyResult.storageReference)
            assertTrue("The accepted file must actually be written to durable storage", destination.exists())
            assertEquals("image/jpeg", copyResult.mimeType)
        } finally {
            baseDir.deleteRecursively()
        }
    }
}
