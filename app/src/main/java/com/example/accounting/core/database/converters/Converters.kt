package com.example.accounting.core.database.converters

import androidx.room.TypeConverter
import com.example.accounting.core.common.DrCr
import com.example.accounting.core.common.Money
import com.example.accounting.domain.accounting.PrimaryGroup
import com.example.accounting.domain.accounting.SyncState
import com.example.accounting.domain.accounting.VoucherType
import com.example.accounting.domain.audit.AuditAction
import com.example.accounting.domain.company.AccountingMode
import com.example.accounting.domain.company.BusinessType
import com.example.accounting.domain.document.DocumentStatus
import com.example.accounting.domain.document.DocumentType
import com.example.accounting.domain.financialyear.PeriodStatus
import com.example.accounting.domain.invoice.InvoiceType
import com.example.accounting.domain.inventory.StockDirection
import com.example.accounting.domain.inventory.StockMovementType
import com.example.accounting.domain.party.PartyEntityType
import com.example.accounting.domain.party.PartyRole
import com.example.accounting.domain.party.PaymentTermsType
import com.example.accounting.domain.recurring.RecurringFrequency
import com.example.accounting.domain.recurring.RecurringVoucherDraftStatus
import com.example.accounting.domain.rendering.ConstitutionType
import com.example.accounting.domain.rendering.DocumentAssetType
import com.example.accounting.domain.rendering.TemplateStatus
import com.example.accounting.domain.taxation.gst.GstDirection
import com.example.accounting.domain.taxation.gst.SupplyType
import java.time.LocalDate

class RoomConverters {

    @TypeConverter
    fun fromDrCr(value: DrCr): String = value.name

    @TypeConverter
    fun toDrCr(value: String): DrCr = DrCr.valueOf(value)

    @TypeConverter
    fun fromPrimaryGroup(value: PrimaryGroup): String = value.name

    @TypeConverter
    fun toPrimaryGroup(value: String): PrimaryGroup = try {
        PrimaryGroup.valueOf(value)
    } catch (e: Exception) {
        PrimaryGroup.fromCode(value)
    }

    @TypeConverter
    fun fromVoucherType(value: VoucherType): String = value.name

    @TypeConverter
    fun toVoucherType(value: String): VoucherType = VoucherType.valueOf(value)

    @TypeConverter
    fun fromSyncState(value: SyncState): String = value.name

    @TypeConverter
    fun toSyncState(value: String): SyncState = SyncState.valueOf(value)

    @TypeConverter
    fun fromPeriodStatus(value: PeriodStatus): String = value.name

    @TypeConverter
    fun toPeriodStatus(value: String): PeriodStatus = PeriodStatus.valueOf(value)

    @TypeConverter
    fun fromAuditAction(value: AuditAction): String = value.name

    @TypeConverter
    fun toAuditAction(value: String): AuditAction = AuditAction.valueOf(value)

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let {
        try { LocalDate.parse(it) } catch (e: Exception) { null }
    }

    @TypeConverter
    fun fromMoney(money: Money?): Long = money?.paise ?: 0L

    @TypeConverter
    fun toMoney(paise: Long?): Money = Money(paise ?: 0L)

    @TypeConverter
    fun fromAccountingMode(value: AccountingMode): String = value.name

    @TypeConverter
    fun toAccountingMode(value: String): AccountingMode = try { AccountingMode.valueOf(value) } catch (e: Exception) { AccountingMode.ACCOUNT_ONLY }

    @TypeConverter
    fun fromBusinessType(value: BusinessType): String = value.name

    @TypeConverter
    fun toBusinessType(value: String): BusinessType = try { BusinessType.valueOf(value) } catch (e: Exception) { BusinessType.TRADING }

    @TypeConverter
    fun fromStockDirection(value: StockDirection): String = value.name

    @TypeConverter
    fun toStockDirection(value: String): StockDirection = StockDirection.valueOf(value)

    @TypeConverter
    fun fromStockMovementType(value: StockMovementType): String = value.name

    @TypeConverter
    fun toStockMovementType(value: String): StockMovementType = StockMovementType.valueOf(value)

    @TypeConverter
    fun fromSupplyType(value: SupplyType): String = value.name

    @TypeConverter
    fun toSupplyType(value: String): SupplyType = try { SupplyType.valueOf(value) } catch (e: Exception) { SupplyType.INTRA_STATE }

    @TypeConverter
    fun fromGstDirection(value: GstDirection): String = value.name

    @TypeConverter
    fun toGstDirection(value: String): GstDirection = GstDirection.valueOf(value)

    // ==================== Phase 7A: Party + Invoice ====================
    @TypeConverter
    fun fromPartyRole(value: PartyRole): String = value.name

    @TypeConverter
    fun toPartyRole(value: String): PartyRole = PartyRole.valueOf(value)

    @TypeConverter
    fun fromTemplateStatus(value: TemplateStatus): String = value.name

    @TypeConverter
    fun toTemplateStatus(value: String): TemplateStatus = TemplateStatus.valueOf(value)

    @TypeConverter
    fun fromDocumentAssetType(value: DocumentAssetType): String = value.name

    @TypeConverter
    fun toDocumentAssetType(value: String): DocumentAssetType = DocumentAssetType.valueOf(value)

    @TypeConverter
    fun fromRecurringFrequency(value: RecurringFrequency): String = value.name

    @TypeConverter
    fun toRecurringFrequency(value: String): RecurringFrequency = RecurringFrequency.valueOf(value)

    @TypeConverter
    fun fromRecurringVoucherDraftStatus(value: RecurringVoucherDraftStatus): String = value.name

    @TypeConverter
    fun toRecurringVoucherDraftStatus(value: String): RecurringVoucherDraftStatus = RecurringVoucherDraftStatus.valueOf(value)

    @TypeConverter
    fun fromConstitutionType(value: ConstitutionType): String = value.name

    @TypeConverter
    fun toConstitutionType(value: String): ConstitutionType = try { ConstitutionType.valueOf(value) } catch (e: Exception) { ConstitutionType.PROPRIETORSHIP }

    @TypeConverter
    fun fromPartyEntityType(value: PartyEntityType): String = value.name

    @TypeConverter
    fun toPartyEntityType(value: String): PartyEntityType = PartyEntityType.valueOf(value)

    @TypeConverter
    fun fromPaymentTermsType(value: PaymentTermsType): String = value.name

    @TypeConverter
    fun toPaymentTermsType(value: String): PaymentTermsType = try { PaymentTermsType.valueOf(value) } catch (e: Exception) { PaymentTermsType.DUE_ON_RECEIPT }

    @TypeConverter
    fun fromInvoiceType(value: InvoiceType): String = value.name

    @TypeConverter
    fun toInvoiceType(value: String): InvoiceType = InvoiceType.valueOf(value)

    // ==================== Phase 7B: Document/Voucher Lifecycle ====================
    @TypeConverter
    fun fromDocumentType(value: DocumentType): String = value.name

    @TypeConverter
    fun toDocumentType(value: String): DocumentType = DocumentType.valueOf(value)

    @TypeConverter
    fun fromDocumentStatus(value: DocumentStatus): String = value.name

    @TypeConverter
    fun toDocumentStatus(value: String): DocumentStatus = try { DocumentStatus.valueOf(value) } catch (e: Exception) { DocumentStatus.DRAFT }
}
