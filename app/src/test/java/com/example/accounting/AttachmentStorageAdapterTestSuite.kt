package com.example.accounting

import com.example.accounting.data.storage.AttachmentStorageAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

/**
 * Phase 7J-B.2 (Slice 1) - [AttachmentStorageAdapter]'s [java.io.InputStream]/[java.io.File]-based
 * core, exercised directly against a JVM temp directory - no Android [android.content.Context]
 * required, matching this project's preference for fast pure-JVM tests over Robolectric wherever
 * the logic doesn't genuinely need a platform API.
 */
class AttachmentStorageAdapterTestSuite {

    @Test
    fun testCopyToDurableStorage_validInput_producesFileUnderVoucherAttachmentsSubdirectory() {
        val baseDir = java.nio.file.Files.createTempDirectory("ledgerprime_attach_test_").toFile()
        try {
            val bytes = "fake receipt bytes".toByteArray()
            val result = AttachmentStorageAdapter.copyToDurableStorage(
                input = ByteArrayInputStream(bytes), baseDir = baseDir, originalFileName = "receipt.jpg", mimeTypeHint = "image/jpeg"
            )

            val destination = File(result.storageReference)
            assertTrue("The copied file must actually exist on disk", destination.exists())
            assertTrue(
                "The storage reference must point inside the intended voucher_attachments subdirectory",
                destination.parentFile?.name == AttachmentStorageAdapter.ATTACHMENTS_SUBDIRECTORY
            )
            assertTrue(
                "The storage reference must live under the given base directory (durable storage), not somewhere unrelated",
                destination.absolutePath.startsWith(baseDir.absolutePath)
            )
            assertEquals(bytes.size.toLong(), result.sizeBytes)
            assertEquals("image/jpeg", result.mimeType)
            assertTrue("The original .jpg extension should be preserved", destination.name.endsWith(".jpg"))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun testCopyToDurableStorage_twoFilesWithSameOriginalName_neverCollide() {
        val baseDir = java.nio.file.Files.createTempDirectory("ledgerprime_attach_test_").toFile()
        try {
            val first = AttachmentStorageAdapter.copyToDurableStorage(
                ByteArrayInputStream("one".toByteArray()), baseDir, "receipt.jpg", "image/jpeg"
            )
            val second = AttachmentStorageAdapter.copyToDurableStorage(
                ByteArrayInputStream("two".toByteArray()), baseDir, "receipt.jpg", "image/jpeg"
            )
            assertTrue("Two copies of a same-named source file must never collide on disk", first.storageReference != second.storageReference)
            assertEquals("one", File(first.storageReference).readText())
            assertEquals("two", File(second.storageReference).readText())
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun testCopyToDurableStorage_noMimeHint_fallsBackToOctetStream_neverAHardcodedGuess() {
        val baseDir = java.nio.file.Files.createTempDirectory("ledgerprime_attach_test_").toFile()
        try {
            val result = AttachmentStorageAdapter.copyToDurableStorage(
                ByteArrayInputStream("data".toByteArray()), baseDir, originalFileName = null, mimeTypeHint = null
            )
            assertEquals("application/octet-stream", result.mimeType)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun testCopyToDurableStorage_emptySourceStream_throwsAndLeavesNoFile() {
        val baseDir = java.nio.file.Files.createTempDirectory("ledgerprime_attach_test_").toFile()
        try {
            try {
                AttachmentStorageAdapter.copyToDurableStorage(
                    ByteArrayInputStream(ByteArray(0)), baseDir, "empty.jpg", "image/jpeg"
                )
                fail("An empty copy must never silently succeed and produce a broken DocumentAsset reference")
            } catch (e: AttachmentStorageAdapter.AttachmentCopyException) {
                // expected
            }
            val attachmentsDir = File(baseDir, AttachmentStorageAdapter.ATTACHMENTS_SUBDIRECTORY)
            val leftoverFiles = attachmentsDir.listFiles()?.toList().orEmpty()
            assertTrue("No partial/broken file must be left behind on failure", leftoverFiles.isEmpty())
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun testCopyToDurableStorage_veryLongOriginalFileName_doesNotCrashAndProducesAValidFile() {
        // Phase 7J-B.2 Slice 2, Step 13.11: a long filename (e.g. a phone camera's or a cloud
        // provider's export name) must never break the copy - the destination name is always a
        // fresh UUID, the original name only contributes its extension.
        val baseDir = java.nio.file.Files.createTempDirectory("ledgerprime_attach_test_").toFile()
        try {
            val longName = "IMG_" + "x".repeat(300) + "_receipt_scan_from_my_phone_camera_app.jpeg"
            val result = AttachmentStorageAdapter.copyToDurableStorage(
                ByteArrayInputStream("data".toByteArray()), baseDir, longName, "image/jpeg"
            )
            val destination = File(result.storageReference)
            assertTrue("A very long original filename must never prevent the file from being written", destination.exists())
            assertTrue("The destination filename itself must stay a reasonable, fixed-shape UUID-based name, never the raw long name", destination.name.length < 100)
            assertTrue(destination.name.endsWith(".jpeg"))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun testCopyToDurableStorage_readFailure_deletesPartialFileAndThrowsStructuredError() {
        val baseDir = java.nio.file.Files.createTempDirectory("ledgerprime_attach_test_").toFile()
        try {
            val failingInput = object : java.io.InputStream() {
                override fun read(): Int = throw IOException("simulated read failure")
            }
            try {
                AttachmentStorageAdapter.copyToDurableStorage(failingInput, baseDir, "broken.jpg", "image/jpeg")
                fail("A stream read failure must propagate as a structured AttachmentCopyException, never succeed")
            } catch (e: AttachmentStorageAdapter.AttachmentCopyException) {
                // expected - structured error, not a silent broken row
            }
            val attachmentsDir = File(baseDir, AttachmentStorageAdapter.ATTACHMENTS_SUBDIRECTORY)
            assertTrue("No partial file must survive a mid-copy failure", attachmentsDir.listFiles()?.toList().orEmpty().isEmpty())
        } finally {
            baseDir.deleteRecursively()
        }
    }
}
