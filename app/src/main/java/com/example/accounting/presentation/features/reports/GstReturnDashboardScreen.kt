package com.example.accounting.presentation.features.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.accounting.core.common.Money
import com.example.accounting.presentation.components.ActionButton
import com.example.accounting.presentation.components.ActionButtonStyle
import com.example.accounting.presentation.components.Amount
import com.example.accounting.presentation.components.FormField
import com.example.accounting.presentation.components.SectionCard
import com.example.accounting.presentation.components.StatusBadge
import com.example.accounting.presentation.components.TableRow
import com.example.accounting.presentation.theme.Spacing
import com.example.accounting.presentation.viewmodel.AccountingUiState
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoTax
import com.example.accounting.domain.taxation.gstreturn.GstFilingMode
import com.example.accounting.domain.taxation.gstreturn.GstQuarter
import com.example.accounting.domain.taxation.gstreturn.GstReturn
import com.example.accounting.domain.taxation.gstreturn.GstReturnApplicability
import com.example.accounting.domain.taxation.gstreturn.GstReturnPeriodicity
import com.example.accounting.domain.taxation.gstreturn.GstReturnSection
import com.example.accounting.domain.taxation.gstreturn.GstReturnSectionStatus
import com.example.accounting.domain.taxation.gstreturn.GstReturnStatus
import com.example.accounting.domain.taxation.gstreturn.GstReturnType
import com.example.accounting.domain.taxation.gstreturn.GstScheme
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types

/**
 * The GST Return Dashboard (Rule 33) - reached from Reports Center's existing GST category, using
 * only the existing [SectionCard]/[ActionButton]/[TableRow]/[Amount] primitives (no new design
 * system). Redesigned to be compact: FY/scheme/registration render as one dense strip instead of
 * three separate cards, and a return's own status/actions collapse into fewer, denser rows so the
 * real controls sit near the top of the screen rather than below several stacked headers.
 */
@Composable
fun GstReturnDashboardView(
    uiState: AccountingUiState,
    onSelectPeriod: (GstQuarter, Int?, GstReturnType, GstReturnPeriodicity, GstFilingMode) -> Unit,
    onOpenReturn: (String) -> Unit,
    onClearSelection: () -> Unit,
    onPrepare: () -> Unit,
    onValidate: () -> Unit,
    onGenerateJson: () -> Unit,
    onShareArtifact: (String) -> Unit,
    onImportResponseFile: () -> Unit,
    onMarkFiled: (String) -> Unit,
    onSubmitOnline: () -> Unit,
    onUpdateGstEnabled: (Boolean) -> Unit,
    onUpdateGstScheme: (GstScheme) -> Unit,
    onUpdateGstFilingFrequency: (GstReturnPeriodicity) -> Unit
) {
    val selected = uiState.selectedGstReturn
    if (selected == null) {
        GstReturnPeriodPicker(uiState, onSelectPeriod, onOpenReturn, onUpdateGstEnabled, onUpdateGstScheme, onUpdateGstFilingFrequency)
    } else {
        GstReturnDetailView(
            uiState, selected, onClearSelection, onPrepare, onValidate, onGenerateJson,
            onShareArtifact, onImportResponseFile, onMarkFiled, onSubmitOnline
        )
    }
}

@Composable
private fun GstReturnPeriodPicker(
    uiState: AccountingUiState,
    onSelectPeriod: (GstQuarter, Int?, GstReturnType, GstReturnPeriodicity, GstFilingMode) -> Unit,
    onOpenReturn: (String) -> Unit,
    onUpdateGstEnabled: (Boolean) -> Unit,
    onUpdateGstScheme: (GstScheme) -> Unit,
    onUpdateGstFilingFrequency: (GstReturnPeriodicity) -> Unit
) {
    val company = uiState.currentCompany
    val scheme = company?.gstScheme
    val registered = company?.gstEnabled ?: false
    var quarter by remember { mutableStateOf<GstQuarter?>(null) }
    var month by remember { mutableStateOf<Int?>(null) }
    var returnType by remember { mutableStateOf<GstReturnType?>(null) }
    var filingMode by remember { mutableStateOf(GstFilingMode.OFFLINE) }

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // Play Store / user-trust requirement: this screen prepares return figures and a GSTR
        // JSON file only - it never transmits anything to the GST Network on its own
        // (UnconfiguredGstOnlineFilingGateway always reports itself as not configured, never a
        // fabricated success). Stated once, up front, rather than only after a failed tap.
        item {
            SectionCard {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            "Not a Government Filing Service",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            "This screen prepares your GST return figures and a downloadable JSON file. It does " +
                                "not submit anything to the GST Network on your behalf - use Offline mode to " +
                                "download the JSON and file it yourself at gst.gov.in, or hand it to your tax " +
                                "professional.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        // One compact strip: FY (read from the app's own top-bar selector, never a second one
        // here) + registration/scheme/frequency toggles, replacing three separate cards. The small
        // Indigo receipt icon is this app's own dedicated GST/tax accent (`ui/theme/Color.kt`'s
        // `IndigoTax` - defined at the Phase 7J UI theme pass, deliberately distinct from Royal
        // Purple navigation, but never actually used anywhere until this pass) - gives every GST
        // screen its own consistent visual identity instead of reading as generic Royal Purple UI.
        item {
            SectionCard(
                title = uiState.currentFinancialYear?.fyCode?.let { "FY $it" } ?: "No Financial Year",
                trailing = { GstIconBadge() }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("GST Settings", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ChoiceRow(listOf(true, false), registered, { if (it) "Registered" else "Unregistered" }) { onUpdateGstEnabled(it) }
                    if (registered) {
                        ChoiceRow(GstScheme.entries, scheme, { it.name }) { onUpdateGstScheme(it) }
                        if (scheme == GstScheme.REGULAR) {
                            ChoiceRow(
                                GstReturnPeriodicity.entries, company?.gstFilingFrequency,
                                { if (it == GstReturnPeriodicity.QUARTERLY) "QRMP" else "Monthly" }
                            ) { onUpdateGstFilingFrequency(it) }
                        }
                    }
                }
            }
        }

        if (registered && scheme != null) {
            item {
                SectionCard(title = "Quarter") {
                    ChoiceRow(GstQuarter.entries, quarter, { it.label }) { quarter = it; month = null }
                }
            }
        }
        if (registered && scheme != null && quarter != null) {
            val applicable = GstReturnApplicability.availableReturns(scheme, company?.gstFilingFrequency ?: GstReturnPeriodicity.MONTHLY)
            item {
                SectionCard(title = "Return") {
                    ChoiceRow(applicable.map { it.returnType }, returnType, { it.name }) { returnType = it }
                }
            }
            val periodicity = applicable.firstOrNull { it.returnType == returnType }?.periodicity
            if (periodicity == GstReturnPeriodicity.MONTHLY && quarter != null) {
                item {
                    SectionCard(title = "Month") {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            quarter!!.months.chunked(3).forEach { row ->
                                ChoiceRow(row, month, { monthLabel(it) }) { month = it }
                            }
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Filing Mode") {
                    ChoiceRow(GstFilingMode.entries, filingMode, { it.name }) { filingMode = it }
                }
            }
            val monthResolved = if (periodicity == GstReturnPeriodicity.MONTHLY) month else null
            val ready = returnType != null && periodicity != null && (periodicity != GstReturnPeriodicity.MONTHLY || month != null)
            item {
                ActionButton(
                    text = "Open Return",
                    enabled = ready,
                    onClick = { onSelectPeriod(quarter!!, monthResolved, returnType!!, periodicity!!, filingMode) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        if (uiState.gstReturns.isNotEmpty()) {
            item { Text("Saved Returns", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)) }
            items(uiState.gstReturns, key = { it.gstReturnId }) { gr ->
                SectionCard(
                    onClick = { onOpenReturn(gr.gstReturnId) },
                    title = "${gr.returnType} - ${gr.periodKey}",
                    subtitle = "${gr.scheme} - ${gr.filingMode} - ${gr.status}"
                ) {}
            }
        }
    }
}

/** This screen's own GST/tax identity accent - `IndigoTax`/`IndigoContainer`
 * (`ui/theme/Color.kt`, Phase 7J UI) were defined specifically so a GST badge never reads as a
 * second "primary" color next to Royal Purple navigation, but were never actually wired into a
 * screen until this pass. One small badge, reused everywhere this dashboard needs a GST mark. */
@Composable
private fun GstIconBadge() {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(IndigoContainer, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Receipt, contentDescription = "GST", tint = IndigoTax, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T?, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { option ->
            ActionButton(
                text = label(option),
                style = if (option == selected) ActionButtonStyle.PRIMARY else ActionButtonStyle.SECONDARY,
                onClick = { onSelect(option) }
            )
        }
    }
}

private fun monthLabel(month: Int): String = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)[month - 1]

private val sectionMapMoshi = Moshi.Builder().build()
private val sectionMapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
private val sectionMapAdapter = sectionMapMoshi.adapter<Map<String, Any?>>(sectionMapType)

/** Reads back the plain `{count, taxableValuePaise, cgstPaise, sgstPaise, igstPaise, cessPaise}`
 * map [com.example.accounting.data.repository.AccountingRepository.bucketTotals] wrote - never a
 * second calculation, purely a display parse of already-computed numbers. */
private fun parseSectionTotals(json: String?): Map<String, Any?>? =
    json?.let { runCatching { sectionMapAdapter.fromJson(it) }.getOrNull() }

private fun Map<String, Any?>.paiseOf(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L

/**
 * One GSTR section's small widget card (Rule 33 follow-up) - [section.sectionKey] is a real
 * statutory GST Network table name (B2B/B2C/EXP/NIL_EXEMPT/HSN for GSTR-1; the 3.1/Section-4
 * buckets for GSTR-3B - see `AccountingRepository.buildGstReturnSections`'s own doc comment for the
 * exact mapping and its deliberate limits), never an invented one. Deliberately small/dense - a
 * label, a count, and the taxable+tax totals in one row - not a full report page.
 */
@Composable
private fun GstSectionWidgetCard(section: GstReturnSection) {
    val totals = parseSectionTotals(section.resultDataJson)
    val (bg, fg) = gstSectionStatusColors(section.status)
    SectionCard(
        title = sectionLabel(section.sectionKey),
        trailing = { StatusBadge(text = section.status.name.replace('_', ' '), containerColor = bg, contentColor = fg) }
    ) {
        if (totals != null) {
            val tax = totals.paiseOf("cgstPaise") + totals.paiseOf("sgstPaise") + totals.paiseOf("igstPaise")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${totals["count"] ?: 0} txn", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Amount(Money.fromPaise(totals.paiseOf("taxableValuePaise")), style = MaterialTheme.typography.bodySmall)
                Amount(Money.fromPaise(tax), style = MaterialTheme.typography.bodySmall, emphasize = tax > 0)
            }
        }
    }
}

private fun sectionLabel(key: String): String = when (key) {
    "B2B" -> "B2B - Registered"
    "B2C" -> "B2C - Unregistered"
    "EXP" -> "Exports"
    "NIL_EXEMPT" -> "Nil-Rated / Exempt"
    "HSN" -> "HSN Summary"
    "OUTWARD_TAXABLE" -> "3.1 Outward Taxable"
    "OUTWARD_ZERO_RATED" -> "3.1 Zero-Rated"
    "OUTWARD_NIL_EXEMPT" -> "3.1 Nil / Exempt"
    "RCM_LIABILITY" -> "3.1(d) RCM Liability"
    "ITC_FORWARD" -> "4A ITC (Forward Charge)"
    "ITC_RCM" -> "4A ITC (Reverse Charge)"
    else -> key
}

/** Maps [GstReturnStatus]/[GstReturnSectionStatus] onto this app's own existing semantic accents
 * (Emerald=success, Crimson=error/failed, Amber=in-progress - the same three "status accents"
 * every other screen already uses, per `ui/theme/Color.kt`'s own doc comment: "unchanged by the
 * Royal Purple swap"). Read through `MaterialTheme.colorScheme.*` (never the raw Color constants)
 * so both light and dark theme stay correct automatically. */
@Composable
private fun gstReturnStatusColors(status: GstReturnStatus): Pair<Color, Color> = when (status) {
    GstReturnStatus.DRAFT ->
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    GstReturnStatus.VALIDATION_FAILED, GstReturnStatus.FAILED, GstReturnStatus.REJECTED ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    GstReturnStatus.READY, GstReturnStatus.FILED ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    GstReturnStatus.SUBMITTING, GstReturnStatus.SUBMITTED, GstReturnStatus.PROCESSING ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
}

@Composable
private fun gstSectionStatusColors(status: GstReturnSectionStatus): Pair<Color, Color> = when (status) {
    GstReturnSectionStatus.PENDING ->
        MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    GstReturnSectionStatus.PREPARED ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    GstReturnSectionStatus.VALIDATION_PASSED ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    GstReturnSectionStatus.VALIDATION_FAILED ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
}

@Composable
private fun GstReturnDetailView(
    uiState: AccountingUiState,
    gstReturn: GstReturn,
    onClearSelection: () -> Unit,
    onPrepare: () -> Unit,
    onValidate: () -> Unit,
    onGenerateJson: () -> Unit,
    onShareArtifact: (String) -> Unit,
    onImportResponseFile: () -> Unit,
    onMarkFiled: (String) -> Unit,
    onSubmitOnline: () -> Unit
) {
    var ackNumber by remember(gstReturn.gstReturnId) { mutableStateOf("") }
    val canPrepare = gstReturn.status in setOf(GstReturnStatus.DRAFT, GstReturnStatus.VALIDATION_FAILED)

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 40.dp)
    ) {
        // Status + Prepare/Validate collapsed into one compact card (was two stacked cards with
        // their own headings) - the two actions sit in a single row.
        item {
            val (statusBg, statusFg) = gstReturnStatusColors(gstReturn.status)
            SectionCard(
                title = "${gstReturn.returnType} - ${gstReturn.periodKey}",
                subtitle = "${gstReturn.scheme} - ${gstReturn.filingMode}",
                trailing = { GstIconBadge() }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Status", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusBadge(text = gstReturn.status.name.replace('_', ' '), containerColor = statusBg, contentColor = statusFg)
                    }
                    if (gstReturn.errorMessage != null) TableRow("Message", value = gstReturn.errorMessage)
                    if (gstReturn.acknowledgementNumber != null) TableRow("Acknowledgement No.", value = gstReturn.acknowledgementNumber)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ActionButton(text = "Prepare", onClick = onPrepare, enabled = canPrepare, modifier = Modifier.weight(1f))
                        ActionButton(text = "Validate", style = ActionButtonStyle.SECONDARY, onClick = onValidate, enabled = canPrepare, modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        if (uiState.selectedGstReturnSections.isNotEmpty()) {
            item { Text("Sections", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)) }
            items(uiState.selectedGstReturnSections, key = { it.sectionId }) { section -> GstSectionWidgetCard(section) }
        }

        if (gstReturn.filingMode == GstFilingMode.OFFLINE) {
            item {
                SectionCard(title = "Offline Filing") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            ActionButton(
                                text = "Generate JSON", onClick = onGenerateJson, modifier = Modifier.weight(1f),
                                enabled = gstReturn.status == GstReturnStatus.READY
                            )
                            val artifact = uiState.selectedGstReturnArtifacts.firstOrNull { it.artifactId == gstReturn.latestRequestArtifactId }
                            ActionButton(
                                text = "Share", style = ActionButtonStyle.SECONDARY,
                                onClick = { artifact?.let { onShareArtifact(it.jsonContent) } },
                                modifier = Modifier.weight(1f), enabled = artifact != null
                            )
                        }
                        ActionButton(
                            text = "Import GST Response JSON", style = ActionButtonStyle.SECONDARY,
                            onClick = onImportResponseFile, modifier = Modifier.fillMaxWidth(),
                            enabled = gstReturn.status in setOf(GstReturnStatus.READY, GstReturnStatus.PROCESSING)
                        )
                        if (gstReturn.status == GstReturnStatus.PROCESSING) {
                            FormField(value = ackNumber, onValueChange = { ackNumber = it }, label = "Acknowledgement Number", modifier = Modifier.fillMaxWidth())
                            ActionButton(text = "Mark as Filed", onClick = { onMarkFiled(ackNumber) }, modifier = Modifier.fillMaxWidth(), enabled = ackNumber.isNotBlank())
                        }
                    }
                }
            }
        } else {
            item {
                SectionCard(title = "Online Filing", subtitle = "No GST Network integration configured") {
                    ActionButton(text = "Submit Online", onClick = onSubmitOnline, modifier = Modifier.fillMaxWidth(), enabled = gstReturn.status == GstReturnStatus.READY)
                }
            }
        }

        if (uiState.selectedGstReturnArtifacts.isNotEmpty()) {
            item { Text("Artifact History", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)) }
            items(uiState.selectedGstReturnArtifacts, key = { it.artifactId }) { artifact ->
                SectionCard(title = artifact.artifactType.name, subtitle = "Schema ${artifact.schemaVersion}") {}
            }
        }

        item {
            ActionButton(text = "Back to Period Selection", style = ActionButtonStyle.TEXT, onClick = onClearSelection, modifier = Modifier.fillMaxWidth())
        }
    }
}
