package dev.whekin.whfin

import android.app.Application
import dev.whekin.whfin.data.db.CategorySeeder
import dev.whekin.whfin.data.db.WhfinDatabase
import dev.whekin.whfin.data.categorization.GeorgiaMerchantPreset
import dev.whekin.whfin.data.importer.StatementImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale
import dev.whekin.whfin.data.db.AccountEntity
import dev.whekin.whfin.data.db.AccountType
import dev.whekin.whfin.data.demo.DemoDataInstaller
import dev.whekin.whfin.data.demo.RuntimeModeStore

class WhfinApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val runtimeModes by lazy { RuntimeModeStore(this) }
    val userDb: WhfinDatabase by lazy { WhfinDatabase.get(this) }
    private val demoDb: WhfinDatabase by lazy { WhfinDatabase.open(this, DemoDataInstaller.DATABASE_NAME) }
    val db: WhfinDatabase
        get() = if (runtimeModes.demoMode) demoDb else userDb

    val isDemoMode: Boolean
        get() = runtimeModes.demoMode

    suspend fun setDemoMode(enabled: Boolean) {
        if (enabled) ensureDemoData()
        runtimeModes.demoMode = enabled
    }

    suspend fun resetDemoData() {
        DemoDataInstaller(this, demoDb).install()
        runtimeModes.demoFixtureVersion = DemoDataInstaller.FIXTURE_VERSION
    }

    fun setDeveloperMode(enabled: Boolean) {
        runtimeModes.developerMode = enabled
    }

    private suspend fun ensureDemoData() {
        val needsInstall = !getDatabasePath(DemoDataInstaller.DATABASE_NAME).exists() ||
            runtimeModes.demoFixtureVersion != DemoDataInstaller.FIXTURE_VERSION
        if (needsInstall) resetDemoData()
    }

    override fun onCreate() {
        super.onCreate()
        if (
            runtimeModes.demoMode &&
            (!getDatabasePath(DemoDataInstaller.DATABASE_NAME).exists() ||
                runtimeModes.demoFixtureVersion != DemoDataInstaller.FIXTURE_VERSION)
        ) {
            runtimeModes.demoMode = false
        }
        appScope.launch {
            StatementImporter(userDb).repairTransferGroups()
            CategorySeeder.seedIfEmpty(
                userDb,
                isRussian = Locale.getDefault().language == "ru",
            )
            CategorySeeder.applyRenames(userDb)
            CategorySeeder.ensureCurrentPresets(userDb, Locale.getDefault().language == "ru")
            val activeAccounts = userDb.accountDao().allActive()
            if (activeAccounts.none { it.type == AccountType.CASH }) {
                userDb.accountDao().insert(
                    AccountEntity(
                        name = if (Locale.getDefault().language == "ru") "Наличные" else "Cash",
                        type = AccountType.CASH,
                        currency = "GEL",
                        sortOrder = 1000,
                    ),
                )
            }
            // Исправляет порядок Cash, созданного ранней dev-версией сидера.
            activeAccounts.filter { it.type == AccountType.CASH && it.sortOrder == -100 }
                .forEach { userDb.accountDao().update(it.copy(sortOrder = 1000)) }
            GeorgiaMerchantPreset.applyToUncategorized(userDb)
        }
    }
}
