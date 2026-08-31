package com.example.accounting.domain.rendering

/** One printable financial report as a plain title/subtitle + column-header/rows/optional-totals
 * table - deliberately NOT [DocumentData] (that type's seller/buyer/line-item shape is for trade
 * documents; a Trial Balance/P&L/Balance Sheet/Day Book has no buyer or seller). Every field here
 * is already-formatted display text - whatever builds this (`domain/reports/ReportPdfMapping.kt`)
 * performs no accounting/GST calculation of its own; every value is sourced from an
 * already-generated report model (`domain/reports/ReportModels.kt`), the same one View/JSON/CSV
 * already consume, never a second calculation for print. Kept in `domain/rendering` (no Android
 * dependency) so the mapping stays testable without a `Context`, mirroring [DocumentData]'s own
 * data/domain split from [com.example.accounting.data.rendering.PdfDocumentRenderer].
 */
data class TabularReportData(
    val title: String,
    val subtitle: String,
    val columnHeaders: List<String>,
    val rows: List<List<String>>,
    val totalsRow: List<String>? = null
)
