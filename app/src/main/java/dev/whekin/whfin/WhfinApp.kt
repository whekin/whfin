package dev.whekin.whfin

import android.app.Application
import dev.whekin.whfin.data.db.CategorySeeder
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.categorization.CategoryMaintenance
import dev.whekin.whfin.data.importer.StatementImporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import android.util.Log
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.demo.DemoDataInstaller
import dev.whekin.whfin.data.demo.RuntimeModeStore
import dev.whekin.whfin.data.integrity.DataIntegrityChecker
import dev.whekin.whfin.data.sms.CredoOtpInbox
import dev.whekin.whfin.data.notifications.PhysicalCardBalanceMonitor

class WhfinApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val credoOtpInbox = CredoOtpInbox()
    val physicalCardBalanceMonitor by lazy { PhysicalCardBalanceMonitor(this, userDb, appScope) }

    val runtimeModes by lazy { RuntimeModeStore(this) }
    val userDb: WhfinDatabase by lazy { WhfinDatabase.get(this) }
    private val demoDb: WhfinDatabase by lazy { WhfinDatabase.open(this, DemoDataInstaller.DATABASE_NAME) }
    val db: WhfinDatabase
        get() = if (runtimeModes.demoMode) demoDb else userDb

    val isDemoMode: Boolean
        get() = runtimeModes.demoMode

    private val _integrityIssues = MutableStateFlow(0)

    /**
     * How many contradictions the last check found in the personal ledger.
     *
     * The check is a full pass over the data, so it runs at startup and when something asks for it,
     * not on every screen. Screens read the answer; they never sit on the ledger recomputing it.
     */
    val integrityIssues: StateFlow<Int> = _integrityIssues.asStateFlow()

    /**
     * One piece of startup maintenance, insulated from the others.
     *
     * These steps share a coroutine but nothing else. Run as one body, a single failure — a repair
     * pass tripping over one contradictory row — silently cancels everything after it, so category
     * seeding and rule backfill would stop running for as long as that row existed, with the app
     * looking entirely healthy.
     */
    private suspend fun startupStep(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.e("WHFIN", "Startup step '$name' failed", failure)
        }
    }

    suspend fun refreshIntegrity() {
        val report = DataIntegrityChecker(userDb).run()
        if (report.issues.isNotEmpty()) {
            Log.e("WHFIN", "Ledger integrity issues: ${report.issues.joinToString { it.code }}")
        }
        _integrityIssues.value = report.issues.size
    }

    suspend fun setDemoMode(enabled: Boolean) {
        if (enabled) resetDemoData()
        runtimeModes.demoMode = enabled
    }

    suspend fun resetDemoData() {
        DemoDataInstaller(this, demoDb).install()
        runtimeModes.demoFixtureVersion = DemoDataInstaller.FIXTURE_VERSION
    }

    fun setDeveloperMode(enabled: Boolean) {
        runtimeModes.developerMode = enabled
    }

    /**
     * Whether a ledger already existed before this process touched anything.
     *
     * Read before the database is opened, because Room creates the file the moment it is used and the
     * answer would then be "yes" on a genuinely first run.
     */
    var hadExistingUserData: Boolean = false
        private set

    override fun onCreate() {
        super.onCreate()
        hadExistingUserData = getDatabasePath(WhfinDatabase.NAME).exists()
        if (
            runtimeModes.demoMode &&
            (!getDatabasePath(DemoDataInstaller.DATABASE_NAME).exists() ||
                runtimeModes.demoFixtureVersion != DemoDataInstaller.FIXTURE_VERSION)
        ) {
            runtimeModes.demoMode = false
        }
        appScope.launch {
            val isRussian = Locale.getDefault().language == "ru"
            startupStep("repairTransferGroups") {
                StatementImporter(userDb).repairTransferGroups()
            }
            startupStep("seedCategories") {
                CategorySeeder.seedIfEmpty(userDb, isRussian = isRussian)
                CategorySeeder.applyRenames(userDb)
                CategorySeeder.ensureCurrentPresets(userDb, isRussian)
            }
            startupStep("seedCash") {
                val activeAccounts = userDb.accountDao().allActive()
                if (activeAccounts.none { it.type == AccountType.CASH }) {
                    userDb.accountDao().insert(
                        AccountEntity(
                            name = if (isRussian) "Наличные" else "Cash",
                            type = AccountType.CASH,
                            currency = "GEL",
                            sortOrder = 1000,
                        ),
                    )
                }
                // Исправляет порядок Cash, созданного ранней dev-версией сидера.
                activeAccounts.filter { it.type == AccountType.CASH && it.sortOrder == -100 }
                    .forEach { userDb.accountDao().update(it.copy(sortOrder = 1000)) }
            }
            startupStep("categoryMaintenance") { CategoryMaintenance.run(userDb) }
            startupStep("refreshIntegrity") { refreshIntegrity() }
        }
        physicalCardBalanceMonitor.start()
    }
}
