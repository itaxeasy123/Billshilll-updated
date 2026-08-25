package com.example.accounting.presentation.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.accounting.core.common.Money
import com.example.accounting.domain.accounting.Ledger
import com.example.accounting.domain.accounting.StandardSystemGroups
import com.example.accounting.domain.accounting.Voucher
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.inventory.StockItem
import com.example.accounting.domain.taxation.gst.GSTRules
import com.example.accounting.domain.trading.OutstandingInvoice
import com.example.accounting.presentation.viewmodel.AccountingViewModel
import java.time.LocalDate
import java.util.UUID

private data class LineFormState(
    val key: String = UUID.randomUUID().toString(),
    val itemId: String = "",
    val quantityInput: String = "1",
    val rateInput: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateVoucherDialog(
    ledgers: List<Ledger>,
    stockItems: List<StockItem> = emptyList(),
    vouchers: List<Voucher> = emptyList(),
    outstandingInvoices: List<OutstandingInvoice> = emptyList(),
    companyStateCode: String = "",
    defaultVoucherType: VoucherType = VoucherType.PAYMENT,
    onDismiss: () -> Unit,
    onPostQuickVoucher: (VoucherType, LocalDate, String, String, Money, String, String) -> Unit,
    onPostSaleInvoice: (String, String, List<AccountingViewModel.TradingLineForm>, LocalDate, String, String) -> Unit = { _, _, _, _, _, _ -> },
    onPostPurchaseBill: (String, String, List<AccountingViewModel.TradingLineForm>, LocalDate, String, String) -> Unit = { _, _, _, _, _, _ -> },
    onPostCreditNote: (String, LocalDate, String, String) -> Unit = { _, _, _, _ -> },
    onPostDebitNote: (String, LocalDate, String, String) -> Unit = { _, _, _, _ -> },
    onPostSettlement: (VoucherType, LocalDate, String, String, Money, String, String, String, List<Pair<String, Money>>) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    onLoadOutstandingInvoices: (String) -> Unit = {},
    onClearOutstandingInvoices: () -> Unit = {}
) {
    var selectedType by remember { mutableStateOf(defaultVoucherType) }
    val isSaleFlow = selectedType == VoucherType.SALES
    val isPurchaseFlow = selectedType == VoucherType.PURCHASE
    val isTradingFlow = isSaleFlow || isPurchaseFlow
    val isCreditNoteFlow = selectedType == VoucherType.CREDIT_NOTE
    val isDebitNoteFlow = selectedType == VoucherType.DEBIT_NOTE
    val isNoteFlow = isCreditNoteFlow || isDebitNoteFlow
    val isReceiptFlow = selectedType == VoucherType.RECEIPT
    val isPaymentFlow = selectedType == VoucherType.PAYMENT
    val isSettlementFlow = isReceiptFlow || isPaymentFlow
    val isContra = selectedType == VoucherType.CONTRA

    // Contra is restricted to Cash/Bank ledgers only (Phase 4.5/5) - exact groupId-prefix check
    // against the stable system group IDs, never a name/contains() guess. The domain layer
    // (VoucherPostingEngine) enforces this independently of this UI filter (Phase 5, Priority 6).
    fun isCashOrBankLedger(ledger: Ledger) =
        ledger.groupId.startsWith("${StandardSystemGroups.BANK_GROUP_ID}_") || ledger.groupId.startsWith("${StandardSystemGroups.CASH_GROUP_ID}_")
    val cashBankLedgers = remember(ledgers) { ledgers.filter(::isCashOrBankLedger) }
    fun isDebtorLedger(ledger: Ledger) = ledger.groupId.startsWith("${StandardSystemGroups.DEBTORS_GROUP_ID}_")
    fun isCreditorLedger(ledger: Ledger) = ledger.groupId.startsWith("${StandardSystemGroups.CREDITORS_GROUP_ID}_")
    fun isSalesLedger(ledger: Ledger) = ledger.groupId.startsWith("${StandardSystemGroups.SALES_GROUP_ID}_")
    fun isPurchaseLedger(ledger: Ledger) = ledger.groupId.startsWith("${StandardSystemGroups.PURCHASE_GROUP_ID}_")

    // ==== Generic (Contra/Journal) form state ====
    var debitLedgerId by remember {
        mutableStateOf(
            if (selectedType == VoucherType.CONTRA) cashBankLedgers.firstOrNull()?.ledgerId ?: "" else ledgers.firstOrNull()?.ledgerId ?: ""
        )
    }
    var creditLedgerId by remember {
        mutableStateOf(
            if (selectedType == VoucherType.CONTRA) cashBankLedgers.lastOrNull()?.ledgerId ?: "" else ledgers.lastOrNull()?.ledgerId ?: ""
        )
    }
    var amountInput by remember { mutableStateOf("") }
    var narration by remember { mutableStateOf("") }
    var referenceNumber by remember { mutableStateOf("") }
    var debitDropdownExpanded by remember { mutableStateOf(false) }
    var creditDropdownExpanded by remember { mutableStateOf(false) }
    val ledgersMap = remember(ledgers) { ledgers.associateBy { it.ledgerId } }
    val amountMoney = remember(amountInput) { Money.parse(amountInput) }

    // ==== Sale/Purchase item-line form state ====
    var partyLedgerId by remember { mutableStateOf("") }
    var tradeLedgerId by remember {
        mutableStateOf(ledgers.firstOrNull { if (isSaleFlow) isSalesLedger(it) else isPurchaseLedger(it) }?.ledgerId ?: "")
    }
    var lines by remember { mutableStateOf(listOf(LineFormState())) }
    var partyDropdownExpanded by remember { mutableStateOf(false) }
    var tradeDropdownExpanded by remember { mutableStateOf(false) }
    val itemsMap = remember(stockItems) { stockItems.associateBy { it.itemId } }

    // ==== Credit/Debit Note form state ====
    var originalVoucherId by remember { mutableStateOf("") }
    var originalDropdownExpanded by remember { mutableStateOf(false) }
    val eligibleOriginals = remember(vouchers, isCreditNoteFlow) {
        val wantType = if (isCreditNoteFlow) VoucherType.SALES else VoucherType.PURCHASE
        vouchers.filter { it.voucherType == wantType && !it.isCancelled }
    }

    // ==== Receipt/Payment settlement form state ====
    var settlementPartyLedgerId by remember { mutableStateOf("") }
    var settlementCashBankLedgerId by remember { mutableStateOf(cashBankLedgers.firstOrNull()?.ledgerId ?: "") }
    var paymentMode by remember { mutableStateOf("BANK") }
    var settlementAmountInput by remember { mutableStateOf("") }
    var allocationInputs by remember { mutableStateOf(mapOf<String, String>()) }
    var settlementPartyDropdownExpanded by remember { mutableStateOf(false) }
    val eligibleSettlementParties = remember(ledgers, isReceiptFlow) {
        ledgers.filter { if (isReceiptFlow) isDebtorLedger(it) else isCreditorLedger(it) }
    }
    val settlementAmountMoney = remember(settlementAmountInput) { Money.parse(settlementAmountInput) }
    val totalAllocated = remember(allocationInputs) {
        allocationInputs.values.fold(Money.ZERO) { acc, v -> acc + Money.parse(v) }
    }
    val unallocatedRemainder = remember(settlementAmountMoney, totalAllocated) { settlementAmountMoney - totalAllocated }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "New Accounting Voucher",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Sale, Purchase, Receipt, Payment & More",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Voucher Nature",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        VoucherType.SALES,
                        VoucherType.PURCHASE,
                        VoucherType.RECEIPT,
                        VoucherType.PAYMENT,
                        VoucherType.CREDIT_NOTE,
                        VoucherType.DEBIT_NOTE,
                        VoucherType.CONTRA,
                        VoucherType.JOURNAL
                    ).forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = {
                                selectedType = type
                                when (type) {
                                    VoucherType.CONTRA -> {
                                        debitLedgerId = cashBankLedgers.firstOrNull { it.groupId.startsWith("${StandardSystemGroups.BANK_GROUP_ID}_") }?.ledgerId ?: cashBankLedgers.firstOrNull()?.ledgerId ?: ""
                                        creditLedgerId = cashBankLedgers.firstOrNull { it.groupId.startsWith("${StandardSystemGroups.CASH_GROUP_ID}_") }?.ledgerId ?: cashBankLedgers.lastOrNull()?.ledgerId ?: ""
                                    }
                                    VoucherType.SALES -> {
                                        partyLedgerId = ledgers.firstOrNull(::isDebtorLedger)?.ledgerId ?: ""
                                        tradeLedgerId = ledgers.firstOrNull(::isSalesLedger)?.ledgerId ?: ""
                                    }
                                    VoucherType.PURCHASE -> {
                                        partyLedgerId = ledgers.firstOrNull(::isCreditorLedger)?.ledgerId ?: ""
                                        tradeLedgerId = ledgers.firstOrNull(::isPurchaseLedger)?.ledgerId ?: ""
                                    }
                                    VoucherType.RECEIPT -> {
                                        settlementPartyLedgerId = ""
                                        settlementCashBankLedgerId = cashBankLedgers.firstOrNull()?.ledgerId ?: ""
                                        allocationInputs = emptyMap()
                                        onClearOutstandingInvoices()
                                    }
                                    VoucherType.PAYMENT -> {
                                        settlementPartyLedgerId = ""
                                        settlementCashBankLedgerId = cashBankLedgers.firstOrNull()?.ledgerId ?: ""
                                        allocationInputs = emptyMap()
                                        onClearOutstandingInvoices()
                                    }
                                    else -> {}
                                }
                            },
                            label = { Text(type.displayName, fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                when {
                    isTradingFlow -> TradingForm(
                        isSale = isSaleFlow,
                        ledgers = ledgers,
                        stockItems = stockItems,
                        itemsMap = itemsMap,
                        companyStateCode = companyStateCode,
                        partyLedgerId = partyLedgerId,
                        onPartyLedgerChange = { partyLedgerId = it },
                        tradeLedgerId = tradeLedgerId,
                        onTradeLedgerChange = { tradeLedgerId = it },
                        lines = lines,
                        onLinesChange = { lines = it },
                        isDebtorLedger = ::isDebtorLedger,
                        isCreditorLedger = ::isCreditorLedger,
                        isSalesLedger = ::isSalesLedger,
                        isPurchaseLedger = ::isPurchaseLedger,
                        ledgersMap = ledgersMap,
                        partyDropdownExpanded = partyDropdownExpanded,
                        onPartyDropdownExpandedChange = { partyDropdownExpanded = it },
                        tradeDropdownExpanded = tradeDropdownExpanded,
                        onTradeDropdownExpandedChange = { tradeDropdownExpanded = it }
                    )

                    isNoteFlow -> NoteForm(
                        isCredit = isCreditNoteFlow,
                        eligibleOriginals = eligibleOriginals,
                        originalVoucherId = originalVoucherId,
                        onOriginalVoucherChange = { originalVoucherId = it },
                        expanded = originalDropdownExpanded,
                        onExpandedChange = { originalDropdownExpanded = it }
                    )

                    isSettlementFlow -> SettlementForm(
                        isReceipt = isReceiptFlow,
                        eligibleParties = eligibleSettlementParties,
                        partyLedgerId = settlementPartyLedgerId,
                        onPartyLedgerChange = {
                            settlementPartyLedgerId = it
                            allocationInputs = emptyMap()
                            onLoadOutstandingInvoices(it)
                        },
                        expanded = settlementPartyDropdownExpanded,
                        onExpandedChange = { settlementPartyDropdownExpanded = it },
                        cashBankLedgers = cashBankLedgers,
                        cashBankLedgerId = settlementCashBankLedgerId,
                        onCashBankLedgerChange = { settlementCashBankLedgerId = it },
                        paymentMode = paymentMode,
                        onPaymentModeChange = { paymentMode = it },
                        outstandingInvoices = outstandingInvoices,
                        allocationInputs = allocationInputs,
                        onAllocationChange = { voucherId, value -> allocationInputs = allocationInputs + (voucherId to value) },
                        amountInput = settlementAmountInput,
                        onAmountChange = { settlementAmountInput = it },
                        totalAllocated = totalAllocated,
                        unallocatedRemainder = unallocatedRemainder
                    )

                    else -> {
                        val availableLedgers = if (isContra) cashBankLedgers else ledgers

                        ExposedDropdownMenuBox(expanded = debitDropdownExpanded, onExpandedChange = { debitDropdownExpanded = it }) {
                            OutlinedTextField(
                                value = ledgersMap[debitLedgerId]?.name ?: if (isContra) "Select From Account (Cash/Bank)" else "Select Source Account",
                                onValueChange = {}, readOnly = true,
                                label = { Text(if (isContra) "From Account" else "Source Account") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = debitDropdownExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = debitDropdownExpanded, onDismissRequest = { debitDropdownExpanded = false }) {
                                availableLedgers.forEach { led ->
                                    DropdownMenuItem(
                                        text = { Text("${led.name} [${led.groupName}]") },
                                        onClick = { debitLedgerId = led.ledgerId; debitDropdownExpanded = false }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        ExposedDropdownMenuBox(expanded = creditDropdownExpanded, onExpandedChange = { creditDropdownExpanded = it }) {
                            OutlinedTextField(
                                value = ledgersMap[creditLedgerId]?.name ?: if (isContra) "Select To Account (Cash/Bank)" else "Select Adjustment Account",
                                onValueChange = {}, readOnly = true,
                                label = { Text(if (isContra) "To Account" else "Adjustment Account") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = creditDropdownExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(expanded = creditDropdownExpanded, onDismissRequest = { creditDropdownExpanded = false }) {
                                availableLedgers.forEach { led ->
                                    DropdownMenuItem(
                                        text = { Text("${led.name} [${led.groupName}]") },
                                        onClick = { creditLedgerId = led.ledgerId; creditDropdownExpanded = false }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = amountInput, onValueChange = { amountInput = it },
                            label = { Text("Voucher Amount") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth().testTag("voucher_amount_input")
                        )
                    }
                }

                if (!isSettlementFlow) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = referenceNumber, onValueChange = { referenceNumber = it },
                        label = { Text(if (isNoteFlow) "Note Reference (defaults to original invoice no.)" else "Ref / Cheque / Invoice No. (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = narration, onValueChange = { narration = it },
                        label = { Text("Accounting Narration") },
                        placeholder = { Text("Being amount paid / received for...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                val readyTotal = when {
                    isTradingFlow -> lines.sumOf { line ->
                        val item = itemsMap[line.itemId]
                        val qty = line.quantityInput.toDoubleOrNull() ?: 0.0
                        val rate = Money.parse(line.rateInput.ifBlank { "0" }).paise
                        if (item != null) (qty * rate).toLong() else 0L
                    }.let { Money.fromPaise(it) }
                    isSettlementFlow -> settlementAmountMoney
                    else -> amountMoney
                }
                val isReady = when {
                    isTradingFlow -> partyLedgerId.isNotBlank() && tradeLedgerId.isNotBlank() && lines.any { it.itemId.isNotBlank() && (it.quantityInput.toDoubleOrNull() ?: 0.0) > 0.0 }
                    isNoteFlow -> originalVoucherId.isNotBlank()
                    isSettlementFlow -> settlementPartyLedgerId.isNotBlank() && settlementCashBankLedgerId.isNotBlank() && settlementAmountMoney.isPositive && unallocatedRemainder.paise >= 0L
                    else -> amountMoney.isPositive && debitLedgerId.isNotBlank() && creditLedgerId.isNotBlank()
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isReady) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f) else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isReady) Icons.Default.CheckCircle else Icons.Default.Info,
                            contentDescription = null,
                            tint = if (isReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isReady) {
                                if (isNoteFlow) "Ready to Post" else "Ready to Post - Total ${readyTotal.formatPlain()}"
                            } else if (isSettlementFlow && unallocatedRemainder.paise < 0L) {
                                "Allocated amount exceeds the amount entered"
                            } else "Complete the fields to continue",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = if (isReady) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            when {
                                isSaleFlow -> onPostSaleInvoice(
                                    partyLedgerId, tradeLedgerId,
                                    lines.filter { it.itemId.isNotBlank() }.map {
                                        AccountingViewModel.TradingLineForm(it.itemId, it.quantityInput.toDoubleOrNull() ?: 0.0, Money.parse(it.rateInput.ifBlank { "0" }))
                                    },
                                    LocalDate.now(), referenceNumber, narration
                                )
                                isPurchaseFlow -> onPostPurchaseBill(
                                    partyLedgerId, tradeLedgerId,
                                    lines.filter { it.itemId.isNotBlank() }.map {
                                        AccountingViewModel.TradingLineForm(it.itemId, it.quantityInput.toDoubleOrNull() ?: 0.0, Money.parse(it.rateInput.ifBlank { "0" }))
                                    },
                                    LocalDate.now(), referenceNumber, narration
                                )
                                isCreditNoteFlow -> onPostCreditNote(originalVoucherId, LocalDate.now(), referenceNumber, narration)
                                isDebitNoteFlow -> onPostDebitNote(originalVoucherId, LocalDate.now(), referenceNumber, narration)
                                isSettlementFlow -> {
                                    val allocations = allocationInputs.mapNotNull { (voucherId, input) ->
                                        val amt = Money.parse(input)
                                        if (amt.isPositive) voucherId to amt else null
                                    }
                                    val debitId = if (isReceiptFlow) settlementCashBankLedgerId else settlementPartyLedgerId
                                    val creditId = if (isReceiptFlow) settlementPartyLedgerId else settlementCashBankLedgerId
                                    onPostSettlement(
                                        selectedType, LocalDate.now(), debitId, creditId, settlementAmountMoney,
                                        narration.ifBlank { "Being ${selectedType.displayName.lowercase()} ${if (isReceiptFlow) "from" else "to"} ${ledgersMap[settlementPartyLedgerId]?.name ?: "party"}" },
                                        referenceNumber, paymentMode, allocations
                                    )
                                }
                                else -> onPostQuickVoucher(
                                    selectedType, LocalDate.now(), debitLedgerId, creditLedgerId, amountMoney,
                                    narration.ifBlank { "Being ${selectedType.displayName} transaction" }, referenceNumber
                                )
                            }
                            onDismiss()
                        },
                        enabled = isReady,
                        modifier = Modifier.testTag("submit_voucher_button")
                    ) {
                        Text("Post to Ledger")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TradingForm(
    isSale: Boolean,
    ledgers: List<Ledger>,
    stockItems: List<StockItem>,
    itemsMap: Map<String, StockItem>,
    companyStateCode: String,
    partyLedgerId: String,
    onPartyLedgerChange: (String) -> Unit,
    tradeLedgerId: String,
    onTradeLedgerChange: (String) -> Unit,
    lines: List<LineFormState>,
    onLinesChange: (List<LineFormState>) -> Unit,
    isDebtorLedger: (Ledger) -> Boolean,
    isCreditorLedger: (Ledger) -> Boolean,
    isSalesLedger: (Ledger) -> Boolean,
    isPurchaseLedger: (Ledger) -> Boolean,
    ledgersMap: Map<String, Ledger>,
    partyDropdownExpanded: Boolean,
    onPartyDropdownExpandedChange: (Boolean) -> Unit,
    tradeDropdownExpanded: Boolean,
    onTradeDropdownExpandedChange: (Boolean) -> Unit
) {
    Text(
        if (isSale) "Sale - Tax Invoice" else "Purchase - Supplier Bill",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    )
    Spacer(modifier = Modifier.height(8.dp))

    ExposedDropdownMenuBox(expanded = partyDropdownExpanded, onExpandedChange = onPartyDropdownExpandedChange) {
        OutlinedTextField(
            value = ledgersMap[partyLedgerId]?.name ?: if (isSale) "Select Customer" else "Select Supplier",
            onValueChange = {}, readOnly = true,
            label = { Text(if (isSale) "Customer" else "Supplier") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = partyDropdownExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = partyDropdownExpanded, onDismissRequest = { onPartyDropdownExpandedChange(false) }) {
            ledgers.filter { if (isSale) isDebtorLedger(it) else isCreditorLedger(it) }.forEach { led ->
                DropdownMenuItem(
                    text = { Text("${led.name} (${led.groupName})") },
                    onClick = { onPartyLedgerChange(led.ledgerId); onPartyDropdownExpandedChange(false) }
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    ExposedDropdownMenuBox(expanded = tradeDropdownExpanded, onExpandedChange = onTradeDropdownExpandedChange) {
        OutlinedTextField(
            value = ledgersMap[tradeLedgerId]?.name ?: if (isSale) "Select Sales Ledger" else "Select Purchase Ledger",
            onValueChange = {}, readOnly = true,
            label = { Text(if (isSale) "Sales Account" else "Purchase Account") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = tradeDropdownExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = tradeDropdownExpanded, onDismissRequest = { onTradeDropdownExpandedChange(false) }) {
            ledgers.filter { if (isSale) isSalesLedger(it) else isPurchaseLedger(it) }.forEach { led ->
                DropdownMenuItem(text = { Text(led.name) }, onClick = { onTradeLedgerChange(led.ledgerId); onTradeDropdownExpandedChange(false) })
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Text("Items", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
    Spacer(modifier = Modifier.height(6.dp))

    if (stockItems.isEmpty()) {
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
            Text(
                "No items yet - add one from Ledgers > Items before billing a Sale/Purchase.",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp)
            )
        }
    }

    var runningTaxable = Money.ZERO
    var runningTax = Money.ZERO

    lines.forEachIndexed { index, line ->
        val item = itemsMap[line.itemId]
        val qty = line.quantityInput.toDoubleOrNull() ?: 0.0
        val rate = Money.parse(line.rateInput.ifBlank { "0" })
        val lineTaxable = Money.fromPaise((qty * rate.paise).toLong())
        var itemDropdownExpanded by remember(line.key) { mutableStateOf(false) }

        if (item != null) {
            val supplyType = GSTRules.determineSupplyType(companyStateCode, ledgersMap[partyLedgerId]?.stateCode ?: "")
            val breakdown = GSTRules.calculateTax(lineTaxable, item.gstRatePercent, supplyType)
            runningTaxable += breakdown.taxableAmount
            runningTax += breakdown.totalTax
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = itemDropdownExpanded, onExpandedChange = { itemDropdownExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = item?.name ?: "Select Item",
                            onValueChange = {}, readOnly = true,
                            label = { Text("Item") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = itemDropdownExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = itemDropdownExpanded, onDismissRequest = { itemDropdownExpanded = false }) {
                            stockItems.forEach { candidate ->
                                DropdownMenuItem(
                                    text = { Text("${candidate.name} (HSN ${candidate.hsnCode}, ${candidate.gstRatePercent}%)") },
                                    onClick = {
                                        val defaultRate = if (isSale) candidate.standardSellingPrice else candidate.standardCost
                                        onLinesChange(lines.toMutableList().also {
                                            it[index] = line.copy(itemId = candidate.itemId, rateInput = (defaultRate.paise / 100.0).toString())
                                        })
                                        itemDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(onClick = { onLinesChange(lines.filterIndexed { i, _ -> i != index }) }, enabled = lines.size > 1) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove line")
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = line.quantityInput,
                        onValueChange = { onLinesChange(lines.toMutableList().also { l -> l[index] = line.copy(quantityInput = it) }) },
                        label = { Text("Qty${item?.let { " (${it.unit})" } ?: ""}") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = line.rateInput,
                        onValueChange = { onLinesChange(lines.toMutableList().also { l -> l[index] = line.copy(rateInput = it) }) },
                        label = { Text("Rate") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                if (item != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Amount ${lineTaxable.formatPlain()} - GST ${item.gstRatePercent}% - HSN ${item.hsnCode.ifBlank { "-" }}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    TextButton(onClick = { onLinesChange(lines + LineFormState()) }) {
        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text("Add Line")
    }

    if (runningTaxable.isPositive) {
        Spacer(modifier = Modifier.height(4.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Taxable Value:", style = MaterialTheme.typography.bodySmall)
                    Text(runningTaxable.formatPlain(), style = MaterialTheme.typography.bodySmall)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GST:", style = MaterialTheme.typography.bodySmall)
                    Text(runningTax.formatPlain(), style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total (approx.):", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    Text((runningTaxable + runningTax).formatPlain(), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteForm(
    isCredit: Boolean,
    eligibleOriginals: List<Voucher>,
    originalVoucherId: String,
    onOriginalVoucherChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    Text(
        if (isCredit) "Credit Note - Sales Return / Adjustment" else "Debit Note - Purchase Return / Adjustment",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        "The original ${if (isCredit) "Sale" else "Purchase"} is never modified - this creates a new, linked reversal document.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(8.dp))

    val selected = eligibleOriginals.find { it.voucherId == originalVoucherId }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
        OutlinedTextField(
            value = selected?.let { "${it.voucherNumber} - ${it.totalAmount.formatPlain()}" } ?: "Select Original ${if (isCredit) "Sale" else "Purchase"}",
            onValueChange = {}, readOnly = true,
            label = { Text("Original ${if (isCredit) "Sale Invoice" else "Purchase Bill"}") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            if (eligibleOriginals.isEmpty()) {
                DropdownMenuItem(text = { Text("No eligible ${if (isCredit) "sales" else "purchases"} found") }, onClick = {}, enabled = false)
            }
            eligibleOriginals.forEach { v ->
                DropdownMenuItem(
                    text = { Text("${v.voucherNumber} - ${v.totalAmount.formatPlain()} (${v.date})") },
                    onClick = { onOriginalVoucherChange(v.voucherId); onExpandedChange(false) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettlementForm(
    isReceipt: Boolean,
    eligibleParties: List<Ledger>,
    partyLedgerId: String,
    onPartyLedgerChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    cashBankLedgers: List<Ledger>,
    cashBankLedgerId: String,
    onCashBankLedgerChange: (String) -> Unit,
    paymentMode: String,
    onPaymentModeChange: (String) -> Unit,
    outstandingInvoices: List<OutstandingInvoice>,
    allocationInputs: Map<String, String>,
    onAllocationChange: (String, String) -> Unit,
    amountInput: String,
    onAmountChange: (String) -> Unit,
    totalAllocated: Money,
    unallocatedRemainder: Money
) {
    var cashBankDropdownExpanded by remember { mutableStateOf(false) }
    val ledgersById = remember(eligibleParties, cashBankLedgers) { (eligibleParties + cashBankLedgers).associateBy { it.ledgerId } }

    Text(
        if (isReceipt) "Receipt - Money from Customer" else "Payment - Money to Supplier",
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    )
    Spacer(modifier = Modifier.height(8.dp))

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = onExpandedChange) {
        OutlinedTextField(
            value = ledgersById[partyLedgerId]?.name ?: if (isReceipt) "Select Customer" else "Select Supplier",
            onValueChange = {}, readOnly = true,
            label = { Text(if (isReceipt) "Customer" else "Supplier") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            eligibleParties.forEach { led ->
                DropdownMenuItem(text = { Text(led.name) }, onClick = { onPartyLedgerChange(led.ledgerId); onExpandedChange(false) })
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    if (partyLedgerId.isNotBlank()) {
        Text("Outstanding Invoices", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(4.dp))
        if (outstandingInvoices.isEmpty()) {
            Text("No outstanding invoices - this will be recorded as an advance.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            outstandingInvoices.forEach { inv ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(inv.voucherNumber, style = MaterialTheme.typography.bodyMedium)
                        Text("Outstanding ${inv.outstandingAmount.formatPlain()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedTextField(
                        value = allocationInputs[inv.voucherId] ?: "",
                        onValueChange = { onAllocationChange(inv.voucherId, it) },
                        placeholder = { Text("0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(120.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        TextButton(onClick = { outstandingInvoices.forEach { onAllocationChange(it.voucherId, (it.outstandingAmount.paise / 100.0).toString()) } }) {
            Text("Allocate Full Outstanding")
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    ExposedDropdownMenuBox(expanded = cashBankDropdownExpanded, onExpandedChange = { cashBankDropdownExpanded = it }) {
        OutlinedTextField(
            value = ledgersById[cashBankLedgerId]?.name ?: "Select Cash/Bank Account",
            onValueChange = {}, readOnly = true,
            label = { Text("Settlement Account") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = cashBankDropdownExpanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = cashBankDropdownExpanded, onDismissRequest = { cashBankDropdownExpanded = false }) {
            cashBankLedgers.forEach { led ->
                DropdownMenuItem(text = { Text(led.name) }, onClick = { onCashBankLedgerChange(led.ledgerId); cashBankDropdownExpanded = false })
            }
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    Text("Payment Mode", style = MaterialTheme.typography.bodyMedium)
    Spacer(modifier = Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("CASH", "BANK", "UPI").forEach { mode ->
            FilterChip(selected = paymentMode == mode, onClick = { onPaymentModeChange(mode) }, label = { Text(mode) })
        }
    }

    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = amountInput, onValueChange = onAmountChange,
        label = { Text(if (isReceipt) "Amount Received" else "Amount Paid") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag("voucher_amount_input")
    )

    if (totalAllocated.isPositive || Money.parse(amountInput).isPositive) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Allocated ${totalAllocated.formatPlain()} - ${if (unallocatedRemainder.paise < 0) "Exceeds amount by ${unallocatedRemainder.abs().formatPlain()}" else "Unallocated (Advance) ${unallocatedRemainder.formatPlain()}"}",
            style = MaterialTheme.typography.bodySmall,
            color = if (unallocatedRemainder.paise < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
