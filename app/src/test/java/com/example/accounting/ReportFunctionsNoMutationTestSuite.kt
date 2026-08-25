package com.example.accounting

import com.example.accounting.Phase7JBFixtures.seedCompanyAndFy
import com.example.accounting.data.local.dao.AccountingDao
import com.example.accounting.data.repository.AccountingRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Test-only [AccountingDao] recording proxy - a dynamic [Proxy], the same reflection-based
 * verification family already used elsewhere in this suite (e.g.
 * [Phase7JBDataImportTestSuite.testAdapter_neverReachesAccountingRepository]'s declared-field
 * scan), adapted for a question field reflection cannot answer: not "does this class hold a
 * reference," but "does calling this specific function ever invoke a mutation method on the DAO."
 *
 * Records the name of every invoked method whose name starts with insert/update/delete/upsert
 * (case-insensitive) and forwards every call - mutation or not - unchanged to [delegate], so the
 * function under test behaves identically to being given the real DAO. Matches by name prefix
 * across the whole interface rather than hand-listing each of [AccountingDao]'s ~76 mutation-shaped
 * methods - a hand-written list would silently stop protecting anything the day a new mutation
 * method is added and the list isn't updated to match.
 */
class MutationRecordingDaoHandler(private val delegate: AccountingDao) : InvocationHandler {
    val invokedMutationMethodNames: MutableList<String> = mutableListOf()

    override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>?): Any? {
        if (MUTATION_PREFIXES.any { method.name.startsWith(it, ignoreCase = true) }) {
            invokedMutationMethodNames += method.name
        }
        return try {
            if (args == null) method.invoke(delegate) else method.invoke(delegate, *args)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    companion object {
        private val MUTATION_PREFIXES = listOf("insert", "update", "delete", "upsert")

        /** Wraps [delegate] in a recording proxy - returns it typed as [AccountingDao] (so it can
         * be handed to [AccountingRepository] exactly like a real DAO) alongside the handler whose
         * [invokedMutationMethodNames] the test asserts on. */
        fun wrap(delegate: AccountingDao): Pair<AccountingDao, MutationRecordingDaoHandler> {
            val handler = MutationRecordingDaoHandler(delegate)
            val proxy = Proxy.newProxyInstance(
                AccountingDao::class.java.classLoader,
                arrayOf(AccountingDao::class.java),
                handler
            ) as AccountingDao
            return proxy to handler
        }
    }
}

/**
 * Turns the read-only accounting-report audit's verified architectural guarantee - Ledger ->
 * Trial Balance -> P&L -> Balance Sheet are strictly downstream readers of the posting engine's
 * output, never a second write path - into an automated regression test. Proves it at runtime via
 * [MutationRecordingDaoHandler] rather than by re-reading the source each time. Asserts nothing
 * about the reports' own computed figures (already covered by `Phase3TestSuite`/`Phase4TestSuite`)
 * - this suite protects exactly one guarantee: zero DAO mutation.
 */
class ReportFunctionsNoMutationTestSuite {

    private fun freshDao() = Phase7JBAwareDao(Phase7BTestSuite.Phase7BAwareDao(FakeAccountingDao()))

    @Test
    fun testGenerateTrialBalance_performsNoDaoMutation() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val (recordingDao, handler) = MutationRecordingDaoHandler.wrap(dao)
        val repository = AccountingRepository(recordingDao, db = null)

        repository.generateTrialBalance(Phase7JBFixtures.COMPANY_ID, Phase7JBFixtures.FY_ID)

        assertTrue(
            "generateTrialBalance must never mutate the DAO, but called: ${handler.invokedMutationMethodNames}",
            handler.invokedMutationMethodNames.isEmpty()
        )
    }

    @Test
    fun testGenerateProfitAndLoss_performsNoDaoMutation() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val (recordingDao, handler) = MutationRecordingDaoHandler.wrap(dao)
        val repository = AccountingRepository(recordingDao, db = null)

        repository.generateProfitAndLoss(Phase7JBFixtures.COMPANY_ID, Phase7JBFixtures.FY_ID)

        assertTrue(
            "generateProfitAndLoss must never mutate the DAO, but called: ${handler.invokedMutationMethodNames}",
            handler.invokedMutationMethodNames.isEmpty()
        )
    }

    @Test
    fun testGenerateBalanceSheet_performsNoDaoMutation() = runBlocking {
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val (recordingDao, handler) = MutationRecordingDaoHandler.wrap(dao)
        val repository = AccountingRepository(recordingDao, db = null)

        // Exercises generateTrialBalance + generateProfitAndLoss internally too (Balance Sheet
        // reuses both) - one call, but the strongest test of the three, since it walks the full
        // Ledger -> Trial Balance -> P&L -> Balance Sheet chain in a single pass.
        repository.generateBalanceSheet(Phase7JBFixtures.COMPANY_ID, Phase7JBFixtures.FY_ID)

        assertTrue(
            "generateBalanceSheet must never mutate the DAO, but called: ${handler.invokedMutationMethodNames}",
            handler.invokedMutationMethodNames.isEmpty()
        )
    }

    @Test
    fun testRecordingProxy_forwardsReadsCorrectly_soAnEmptyMutationListIsMeaningful() = runBlocking {
        // Harness sanity check: an empty invokedMutationMethodNames list only proves something if
        // the proxy actually forwarded the call and produced a real, correct result - otherwise a
        // broken proxy that swallowed every call would make all three tests above pass vacuously.
        val dao = freshDao()
        dao.seedCompanyAndFy()
        val (recordingDao, _) = MutationRecordingDaoHandler.wrap(dao)
        val repository = AccountingRepository(recordingDao, db = null)

        val report = repository.generateTrialBalance(Phase7JBFixtures.COMPANY_ID, Phase7JBFixtures.FY_ID)

        assertTrue(report.isBalanced)
    }
}
