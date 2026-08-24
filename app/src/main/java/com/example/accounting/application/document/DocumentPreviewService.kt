package com.example.accounting.application.document

import android.content.Context
import android.content.Intent
import com.example.accounting.core.common.AccountingResult
import com.example.accounting.data.repository.AccountingRepository
import com.example.accounting.data.rendering.PdfDocumentRenderer
import com.example.accounting.data.rendering.PrintAdapter
import com.example.accounting.data.rendering.ShareAdapter
import com.example.accounting.domain.document.DocumentType
import com.example.accounting.domain.rendering.DocumentData
import com.example.accounting.domain.rendering.DocumentTemplate
import java.io.File

/**
 * Application-service facade for document Preview/Print/Share (Phase 7J-B, Android-only per this
 * phase's explicit scope decision - Python's export routes are untouched). Every method is a
 * direct delegation to the existing, unmodified [AccountingRepository.assembleDocumentData]/
 * [AccountingRepository.renderDocumentAsJson] plus the existing, unmodified [PdfDocumentRenderer]/
 * [PrintAdapter]/[ShareAdapter] (Phase 7D) - this class recomputes no GST/journal/document fact of
 * its own.
 */
class DocumentPreviewService(private val repository: AccountingRepository) {

    suspend fun preview(companyId: String, documentType: DocumentType, documentId: String): AccountingResult<DocumentData> =
        repository.assembleDocumentData(companyId, documentType, documentId)

    suspend fun previewAsJson(
        companyId: String,
        documentType: DocumentType,
        documentId: String,
        templateId: String? = null
    ): AccountingResult<String> =
        repository.renderDocumentAsJson(companyId, documentType, documentId, templateId)

    fun renderPdf(context: Context, data: DocumentData, template: DocumentTemplate): File =
        PdfDocumentRenderer.render(context, data, template)

    fun print(context: Context, pdfFile: File, jobName: String) = PrintAdapter.print(context, pdfFile, jobName)

    fun buildShareIntent(context: Context, pdfFile: File, mimeType: String = "application/pdf"): Intent =
        ShareAdapter.buildShareIntent(context, pdfFile, mimeType)
}
