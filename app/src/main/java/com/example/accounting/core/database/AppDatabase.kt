package com.example.accounting.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.accounting.core.database.converters.RoomConverters
import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.data.local.entity.AccountingPeriodEntity
import com.example.accounting.data.local.entity.AuditLogEntity
import com.example.accounting.data.local.entity.BankUpiProfileEntity
import com.example.accounting.data.local.entity.BranchEntity
import com.example.accounting.data.local.entity.BusinessProfileEntity
import com.example.accounting.data.local.entity.CompanyEntity
import com.example.accounting.data.local.entity.CompanySubscriptionEntity
import com.example.accounting.data.local.entity.DocumentAssetEntity
import com.example.accounting.data.local.entity.DocumentTemplateEntity
import com.example.accounting.data.local.entity.FinancialYearEntity
import com.example.accounting.data.local.entity.GroupEntity
import com.example.accounting.data.local.entity.GstFilingPeriodEntity
import com.example.accounting.data.local.entity.GstTransactionEntity
import com.example.accounting.data.local.entity.IndividualProfileEntity
import com.example.accounting.data.local.entity.InvoiceEntity
import com.example.accounting.data.local.entity.InvoiceLineEntity
import com.example.accounting.data.local.entity.JournalItemEntity
import com.example.accounting.data.local.entity.LedgerEntity
import com.example.accounting.data.local.entity.OutboxSyncEntity
import com.example.accounting.data.local.entity.PartyEntity
import com.example.accounting.data.local.entity.RecurringVoucherDraftEntity
import com.example.accounting.data.local.entity.RecurringVoucherDraftLineEntity
import com.example.accounting.data.local.entity.RecurringVoucherLineEntity
import com.example.accounting.data.local.entity.RecurringVoucherScheduleEntity
import com.example.accounting.data.local.entity.RenderedDocumentRecordEntity
import com.example.accounting.data.local.entity.SettlementAllocationEntity
import com.example.accounting.data.local.entity.StockItemEntity
import com.example.accounting.data.local.entity.StockMovementEntity
import com.example.accounting.data.local.entity.TradeDocumentEntity
import com.example.accounting.data.local.entity.TradeDocumentLineEntity
import com.example.accounting.data.local.entity.VoucherDocumentReferenceEntity
import com.example.accounting.data.local.entity.VoucherDraftEntity
import com.example.accounting.data.local.entity.VoucherDraftLineEntity
import com.example.accounting.data.local.entity.VoucherEntity
import com.example.accounting.data.local.entity.VoucherStockLineEntity

@Database(
    entities = [
        CompanyEntity::class,
        BranchEntity::class,
        FinancialYearEntity::class,
        AccountingPeriodEntity::class,
        GroupEntity::class,
        LedgerEntity::class,
        VoucherEntity::class,
        JournalItemEntity::class,
        StockItemEntity::class,
        VoucherStockLineEntity::class,
        StockMovementEntity::class,
        AuditLogEntity::class,
        OutboxSyncEntity::class,
        GstTransactionEntity::class,
        SettlementAllocationEntity::class,
        GstFilingPeriodEntity::class,
        PartyEntity::class,
        InvoiceEntity::class,
        InvoiceLineEntity::class,
        TradeDocumentEntity::class,
        TradeDocumentLineEntity::class,
        DocumentTemplateEntity::class,
        BusinessProfileEntity::class,
        IndividualProfileEntity::class,
        DocumentAssetEntity::class,
        RenderedDocumentRecordEntity::class,
        RecurringVoucherScheduleEntity::class,
        RecurringVoucherLineEntity::class,
        RecurringVoucherDraftEntity::class,
        RecurringVoucherDraftLineEntity::class,
        VoucherDraftEntity::class,
        VoucherDraftLineEntity::class,
        VoucherDocumentReferenceEntity::class,
        CompanySubscriptionEntity::class,
        BankUpiProfileEntity::class
    ],
    version = 10,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun accountingDao(): AccountingDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ledgerprime_accounting.db"
                )
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        db.setForeignKeyConstraintsEnabled(true)
                        // Invariant: Ensure GRP_SYS_SUSPENSE exists and LED_SYS_SUSPENSE is correctly parented without data loss
                        try {
                            db.execSQL("""
                                INSERT OR IGNORE INTO account_groups (groupId, companyId, name, primaryGroup, parentGroupId, isSystem, affectsGrossProfit, displayOrder)
                                SELECT 'GRP_SYS_SUSPENSE_' || companyId, companyId, 'Suspense A/c', 'SPECIAL_CONTROL', NULL, 1, 0, 999
                                FROM companies
                            """.trimIndent())
                            db.execSQL("""
                                UPDATE ledgers
                                SET groupId = 'GRP_SYS_SUSPENSE_' || companyId
                                WHERE ledgerId LIKE 'LED_SYS_SUSPENSE_%' AND groupId NOT LIKE 'GRP_SYS_SUSPENSE_%'
                            """.trimIndent())
                        } catch (_: Exception) {
                            // Tables may not yet be initialized on initial creation
                        }
                    }
                })
                .addMigrations(*ALL_MIGRATIONS)
                .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Migration 1 -> 2 (Phase 4: Inventory & COGS).
         * Purely additive: two new tables plus two new columns on `companies`, each with a
         * default that reproduces pre-Phase-4 behavior exactly (ACCOUNT_ONLY / TRADING) so every
         * existing row keeps working unchanged. No column is dropped, renamed, or retyped, and no
         * existing row is rewritten - satisfies Invariant 21 (no destructive fallback migration).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE companies ADD COLUMN accountingMode TEXT NOT NULL DEFAULT 'ACCOUNT_ONLY'")
                db.execSQL("ALTER TABLE companies ADD COLUMN businessType TEXT NOT NULL DEFAULT 'TRADING'")
                db.execSQL("ALTER TABLE stock_items ADD COLUMN currentAvgCostPaise INTEGER NOT NULL DEFAULT 0")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS voucher_stock_lines (
                        lineId TEXT NOT NULL PRIMARY KEY,
                        voucherId TEXT NOT NULL,
                        companyId TEXT NOT NULL,
                        financialYearId TEXT NOT NULL,
                        itemId TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        quantityRaw INTEGER NOT NULL,
                        ratePaise INTEGER NOT NULL,
                        amountPaise INTEGER NOT NULL,
                        lineOrder INTEGER NOT NULL,
                        FOREIGN KEY(voucherId) REFERENCES vouchers(voucherId) ON DELETE CASCADE,
                        FOREIGN KEY(itemId) REFERENCES stock_items(itemId) ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_voucher_stock_lines_voucherId ON voucher_stock_lines(voucherId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_voucher_stock_lines_itemId ON voucher_stock_lines(itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_voucher_stock_lines_companyId ON voucher_stock_lines(companyId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS stock_movements (
                        movementId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        financialYearId TEXT NOT NULL,
                        itemId TEXT NOT NULL,
                        voucherId TEXT,
                        date TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        movementType TEXT NOT NULL,
                        quantityRaw INTEGER NOT NULL,
                        ratePaise INTEGER NOT NULL,
                        amountPaise INTEGER NOT NULL,
                        runningAvgCostAfterPaise INTEGER NOT NULL,
                        reference TEXT NOT NULL,
                        narration TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        createdBy TEXT NOT NULL,
                        FOREIGN KEY(itemId) REFERENCES stock_items(itemId) ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_companyId ON stock_movements(companyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_financialYearId ON stock_movements(financialYearId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_itemId ON stock_movements(itemId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_voucherId ON stock_movements(voucherId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_stock_movements_date ON stock_movements(date)")
            }
        }

        /**
         * Migration 2 -> 3 (Phase 5: GST & Statutory Accounting Engine).
         * Purely additive: two new nullable/defaulted columns on `vouchers` plus three new tables,
         * none of which alter or remove any existing column or row. `gst_transactions` replaces
         * the old `ledgerName.contains("Output CGST")` report-time reconstruction with a real
         * per-line GST fact record; `settlement_allocations` models Receipt/Payment invoice
         * allocation without storing any redundant balance; `gst_filing_periods` is deliberately
         * unreferenced by any accounting-period or posting-engine code (Priority 10 - GST filing
         * governance must stay isolated from accounting-period governance).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vouchers ADD COLUMN referenceVoucherId TEXT")
                db.execSQL("ALTER TABLE vouchers ADD COLUMN paymentMode TEXT NOT NULL DEFAULT ''")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS gst_transactions (
                        gstTransactionId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        financialYearId TEXT NOT NULL,
                        voucherId TEXT NOT NULL,
                        voucherType TEXT NOT NULL,
                        partyLedgerId TEXT NOT NULL,
                        partyGstin TEXT NOT NULL,
                        placeOfSupply TEXT NOT NULL,
                        supplyType TEXT NOT NULL,
                        itemId TEXT,
                        hsnSacCode TEXT NOT NULL,
                        quantityRaw INTEGER,
                        taxableAmountPaise INTEGER NOT NULL,
                        gstRatePercent REAL NOT NULL,
                        cgstPaise INTEGER NOT NULL,
                        sgstPaise INTEGER NOT NULL,
                        igstPaise INTEGER NOT NULL,
                        cessPaise INTEGER NOT NULL,
                        direction TEXT NOT NULL,
                        lineOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(voucherId) REFERENCES vouchers(voucherId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_gst_transactions_companyId ON gst_transactions(companyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_gst_transactions_financialYearId ON gst_transactions(financialYearId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_gst_transactions_voucherId ON gst_transactions(voucherId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_gst_transactions_partyLedgerId ON gst_transactions(partyLedgerId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_gst_transactions_direction ON gst_transactions(direction)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS settlement_allocations (
                        allocationId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        financialYearId TEXT NOT NULL,
                        settlementVoucherId TEXT NOT NULL,
                        invoiceVoucherId TEXT,
                        allocatedAmountPaise INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(settlementVoucherId) REFERENCES vouchers(voucherId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_settlement_allocations_companyId ON settlement_allocations(companyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_settlement_allocations_settlementVoucherId ON settlement_allocations(settlementVoucherId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_settlement_allocations_invoiceVoucherId ON settlement_allocations(invoiceVoucherId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS gst_filing_periods (
                        filingPeriodId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        periodLabel TEXT NOT NULL,
                        startDate TEXT NOT NULL,
                        endDate TEXT NOT NULL,
                        isLocked INTEGER NOT NULL,
                        lockedAt INTEGER,
                        lockedBy TEXT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_gst_filing_periods_companyId ON gst_filing_periods(companyId)")
            }
        }

        /**
         * Migration 3 -> 4 (Phase 7A: Party + Invoice Domain Foundation).
         * Purely additive: three new tables only, none of which alter, drop, or rename any
         * existing column or row. `parties` is a thin 1:1 extension of an existing `ledgers` row
         * (never a replacement); `invoices`/`invoice_lines` are a genuinely separate pre-posting
         * concept - `VoucherPostingEngine` and its tables are completely untouched by this
         * migration. Satisfies Invariant 21 (no destructive fallback migration).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS parties (
                        partyId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        ledgerId TEXT NOT NULL,
                        role TEXT NOT NULL,
                        entityType TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        contactName TEXT NOT NULL,
                        creditLimitPaise INTEGER,
                        paymentTermsType TEXT NOT NULL,
                        paymentTermsCustomDays INTEGER,
                        isActive INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(ledgerId) REFERENCES ledgers(ledgerId) ON DELETE RESTRICT,
                        FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_parties_companyId ON parties(companyId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_parties_ledgerId ON parties(ledgerId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS invoices (
                        invoiceId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        financialYearId TEXT NOT NULL,
                        invoiceType TEXT NOT NULL,
                        invoiceNumber TEXT,
                        partyId TEXT NOT NULL,
                        date TEXT NOT NULL,
                        dueDate TEXT,
                        voucherId TEXT,
                        referenceInvoiceId TEXT,
                        narration TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE,
                        FOREIGN KEY(partyId) REFERENCES parties(partyId) ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_companyId ON invoices(companyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_partyId ON invoices(partyId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_invoices_voucherId ON invoices(voucherId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_referenceInvoiceId ON invoices(referenceInvoiceId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS invoice_lines (
                        lineId TEXT NOT NULL PRIMARY KEY,
                        invoiceId TEXT NOT NULL,
                        itemId TEXT NOT NULL,
                        itemName TEXT NOT NULL,
                        hsnSacCode TEXT NOT NULL,
                        quantityRaw INTEGER NOT NULL,
                        ratePaise INTEGER NOT NULL,
                        gstRatePercent REAL NOT NULL,
                        cessRatePercent REAL NOT NULL,
                        lineOrder INTEGER NOT NULL,
                        FOREIGN KEY(invoiceId) REFERENCES invoices(invoiceId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_lines_invoiceId ON invoice_lines(invoiceId)")
            }
        }

        /**
         * Migration 4 -> 5 (Phase 7B: Document/Voucher Lifecycle Architecture).
         * Purely additive: two new tables (`trade_documents`, `trade_document_lines`) plus one
         * new nullable column on `invoices` - no existing row is rewritten, no existing column
         * altered/dropped/renamed. Satisfies Invariant 21 (no destructive fallback migration).
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE invoices ADD COLUMN sourceTradeDocumentId TEXT")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trade_documents (
                        tradeDocumentId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        financialYearId TEXT NOT NULL,
                        documentType TEXT NOT NULL,
                        documentNumber TEXT NOT NULL,
                        partyId TEXT NOT NULL,
                        date TEXT NOT NULL,
                        status TEXT NOT NULL,
                        sourceTradeDocumentId TEXT,
                        narration TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE,
                        FOREIGN KEY(partyId) REFERENCES parties(partyId) ON DELETE RESTRICT
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trade_documents_companyId ON trade_documents(companyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trade_documents_partyId ON trade_documents(partyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trade_documents_sourceTradeDocumentId ON trade_documents(sourceTradeDocumentId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trade_document_lines (
                        lineId TEXT NOT NULL PRIMARY KEY,
                        tradeDocumentId TEXT NOT NULL,
                        itemId TEXT NOT NULL,
                        itemName TEXT NOT NULL,
                        hsnSacCode TEXT NOT NULL,
                        quantityRaw INTEGER NOT NULL,
                        ratePaise INTEGER NOT NULL,
                        gstRatePercent REAL NOT NULL,
                        cessRatePercent REAL NOT NULL,
                        lineOrder INTEGER NOT NULL,
                        FOREIGN KEY(tradeDocumentId) REFERENCES trade_documents(tradeDocumentId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trade_document_lines_tradeDocumentId ON trade_document_lines(tradeDocumentId)")
            }
        }

        /**
         * Migration 5 -> 6 (Phase 7D: Document Template & Rendering Architecture).
         * Purely additive: five new tables only, none of which alter, drop, or rename any
         * existing column or row. None of these tables are read by `VoucherPostingEngine`,
         * `apply_voucher_event`, or any GST/inventory/settlement engine - this migration cannot
         * affect any accounting calculation. Satisfies Invariant 21 (no destructive fallback
         * migration).
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS document_templates (
                        id TEXT NOT NULL PRIMARY KEY,
                        templateId TEXT NOT NULL,
                        companyId TEXT NOT NULL,
                        documentType TEXT NOT NULL,
                        templateName TEXT NOT NULL,
                        version INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        isDefault INTEGER NOT NULL,
                        configJson TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_document_templates_companyId ON document_templates(companyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_document_templates_templateId ON document_templates(templateId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_document_templates_companyId_documentType ON document_templates(companyId, documentType)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS business_profiles (
                        businessProfileId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        businessName TEXT NOT NULL,
                        legalName TEXT NOT NULL,
                        address TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        email TEXT NOT NULL,
                        website TEXT NOT NULL,
                        gstin TEXT NOT NULL,
                        pan TEXT NOT NULL,
                        logoAssetId TEXT,
                        bankName TEXT NOT NULL,
                        bankAccountNumber TEXT NOT NULL,
                        bankIfsc TEXT NOT NULL,
                        bankBranch TEXT NOT NULL,
                        upiId TEXT NOT NULL,
                        qrCodeAssetId TEXT,
                        signatureAssetId TEXT,
                        termsAndConditions TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_business_profiles_companyId ON business_profiles(companyId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS individual_profiles (
                        individualProfileId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        address TEXT NOT NULL,
                        pan TEXT NOT NULL,
                        phone TEXT NOT NULL,
                        email TEXT NOT NULL,
                        signatureAssetId TEXT,
                        termsAndConditions TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_individual_profiles_companyId ON individual_profiles(companyId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS document_assets (
                        assetId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        storageReference TEXT NOT NULL,
                        checksum TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        sizeBytes INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_document_assets_companyId ON document_assets(companyId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rendered_document_records (
                        recordId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        documentId TEXT NOT NULL,
                        documentType TEXT NOT NULL,
                        templateId TEXT NOT NULL,
                        templateVersion INTEGER NOT NULL,
                        format TEXT NOT NULL,
                        storageReference TEXT,
                        generatedAt INTEGER NOT NULL,
                        FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rendered_document_records_companyId ON rendered_document_records(companyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_rendered_document_records_documentId ON rendered_document_records(documentId)")
            }
        }

        /**
         * Explicit Room Migrations Array.
         * In accordance with Accounting Invariant 21, production accounting databases must NEVER
         * execute destructive fallback migrations (fallbackToDestructiveMigration is strictly omitted).
         */
        /**
         * Migration 6 -> 7 (Business Profile hardening: constitution type, TAN, UDYAM).
         * Purely additive: three new nullable/defaulted columns on `business_profiles` only - no
         * existing column altered, dropped, or renamed, no existing row rewritten.
         * `constitutionType` defaults to `'PROPRIETORSHIP'` so every profile created before this
         * migration keeps behaving exactly as it did (a company with no constitution recorded is
         * the common individual/proprietor case, matching `ConstitutionType`'s own Kotlin default).
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_profiles ADD COLUMN constitutionType TEXT NOT NULL DEFAULT 'PROPRIETORSHIP'")
                db.execSQL("ALTER TABLE business_profiles ADD COLUMN tan TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE business_profiles ADD COLUMN udyam TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * Migration 7 -> 8 (Phase 7F: Recurring Voucher Engine, draft-first).
         * Purely additive: four new tables only, no existing table altered. Generation produces a
         * `recurring_voucher_drafts` row - not a `vouchers` row - so a generated candidate has no
         * journal/ledger/balance/GST/inventory effect until the user explicitly posts it. The
         * unique composite index on `(scheduleId, periodKey)` in `recurring_voucher_drafts` is the
         * idempotency guarantee - a monthly automation cycle can never generate a second candidate
         * for the same period, and since draft rows are never deleted (only status-transitioned to
         * POSTED/DISCARDED), a period the user has already decided on is never re-proposed either.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recurring_voucher_schedules (
                        scheduleId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        financialYearId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        voucherType TEXT NOT NULL,
                        frequency TEXT NOT NULL,
                        dayOfMonth INTEGER NOT NULL,
                        narration TEXT NOT NULL,
                        startDate TEXT NOT NULL,
                        endDate TEXT,
                        isActive INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_voucher_schedules_companyId ON recurring_voucher_schedules(companyId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recurring_voucher_lines (
                        lineId TEXT NOT NULL PRIMARY KEY,
                        scheduleId TEXT NOT NULL,
                        ledgerId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        amountPaise INTEGER NOT NULL,
                        narration TEXT NOT NULL,
                        lineOrder INTEGER NOT NULL,
                        FOREIGN KEY(scheduleId) REFERENCES recurring_voucher_schedules(scheduleId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_voucher_lines_scheduleId ON recurring_voucher_lines(scheduleId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recurring_voucher_drafts (
                        draftId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        scheduleId TEXT NOT NULL,
                        financialYearId TEXT NOT NULL,
                        periodKey TEXT NOT NULL,
                        voucherType TEXT NOT NULL,
                        date TEXT NOT NULL,
                        narration TEXT NOT NULL,
                        status TEXT NOT NULL,
                        generatedVoucherId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(scheduleId) REFERENCES recurring_voucher_schedules(scheduleId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_voucher_drafts_companyId ON recurring_voucher_drafts(companyId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_recurring_voucher_drafts_scheduleId_periodKey ON recurring_voucher_drafts(scheduleId, periodKey)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recurring_voucher_draft_lines (
                        draftLineId TEXT NOT NULL PRIMARY KEY,
                        draftId TEXT NOT NULL,
                        ledgerId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        amountPaise INTEGER NOT NULL,
                        narration TEXT NOT NULL,
                        lineOrder INTEGER NOT NULL,
                        FOREIGN KEY(draftId) REFERENCES recurring_voucher_drafts(draftId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recurring_voucher_draft_lines_draftId ON recurring_voucher_draft_lines(draftId)")
            }
        }

        /**
         * Migration 8 -> 9 (Phase 7J-B: Management Layer).
         * Purely additive: five new tables only, no existing table/column altered, dropped, or
         * renamed. `voucher_drafts`/`voucher_draft_lines` mirror `recurring_voucher_drafts`/
         * `recurring_voucher_draft_lines`'s exact shape - deliberately not the `vouchers`/
         * `journal_items` tables, so a draft has no journal/ledger/balance/GST/inventory effect
         * until explicitly posted through the existing, unmodified `postVoucher`.
         * `voucher_document_references` is a thin metadata join table (an attached supporting
         * document, never a rendered output - see `RenderedDocumentRecordEntity`, left untouched).
         * `company_subscriptions` gives `CompanySubscription` (Phase 7J) its first real persistence
         * - the unique `(companyId, financialYearId)` index enforces one row per company per FY;
         * paid validity is always derived from the referenced FinancialYear's own stored dates, no
         * date column is stored here at all. `bank_upi_profiles` gives `BankUpiProfile` (Phase 7G)
         * its first real persistence - settlement/contact metadata, structurally outside the
         * double-entry stream (no Ledger/Voucher/JournalItem foreign key). Satisfies Invariant 21
         * (no destructive fallback migration).
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS voucher_drafts (
                        draftId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        financialYearId TEXT NOT NULL,
                        voucherType TEXT NOT NULL,
                        date TEXT NOT NULL,
                        referenceNumber TEXT NOT NULL,
                        narration TEXT NOT NULL,
                        status TEXT NOT NULL,
                        postedVoucherId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_voucher_drafts_companyId ON voucher_drafts(companyId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS voucher_draft_lines (
                        draftLineId TEXT NOT NULL PRIMARY KEY,
                        draftId TEXT NOT NULL,
                        ledgerId TEXT NOT NULL,
                        type TEXT NOT NULL,
                        amountPaise INTEGER NOT NULL,
                        narration TEXT NOT NULL,
                        lineOrder INTEGER NOT NULL,
                        FOREIGN KEY(draftId) REFERENCES voucher_drafts(draftId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_voucher_draft_lines_draftId ON voucher_draft_lines(draftId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS voucher_document_references (
                        referenceId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        voucherId TEXT NOT NULL,
                        documentAssetId TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_voucher_document_references_companyId ON voucher_document_references(companyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_voucher_document_references_voucherId ON voucher_document_references(voucherId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS company_subscriptions (
                        subscriptionId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        financialYearId TEXT NOT NULL,
                        planType TEXT NOT NULL,
                        planName TEXT NOT NULL,
                        entitlementsCsv TEXT NOT NULL DEFAULT '',
                        isActive INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_company_subscriptions_companyId ON company_subscriptions(companyId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_company_subscriptions_companyId_financialYearId ON company_subscriptions(companyId, financialYearId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS bank_upi_profiles (
                        bankUpiProfileId TEXT NOT NULL PRIMARY KEY,
                        companyId TEXT NOT NULL,
                        partyId TEXT,
                        bankName TEXT NOT NULL,
                        accountHolderName TEXT NOT NULL,
                        accountNumber TEXT NOT NULL,
                        ifscCode TEXT NOT NULL,
                        branchName TEXT NOT NULL,
                        upiId TEXT,
                        upiPayeeName TEXT NOT NULL DEFAULT '',
                        upiIsVerified INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(companyId) REFERENCES companies(companyId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_upi_profiles_companyId ON bank_upi_profiles(companyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_bank_upi_profiles_partyId ON bank_upi_profiles(partyId)")
            }
        }

        /**
         * Migration 9 -> 10 (Company-level GST configuration). Purely additive: one new column on
         * `companies`, `gstEnabled`, defaulted to `1` (true) so every existing company keeps its
         * current, always-on GST behavior unchanged - `Voucher.isGstApplicable` is already
         * hardcoded `true` at every Sale/Purchase/Credit-Debit-Note construction site today,
         * regardless of any company setting, so `true` is the value that reproduces existing
         * behavior, never `false`. No column dropped, renamed, or retyped; no existing row
         * rewritten - satisfies Invariant 21 (no destructive fallback migration).
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE companies ADD COLUMN gstEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
    }
}
