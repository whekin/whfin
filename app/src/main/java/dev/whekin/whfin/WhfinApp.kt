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

class WhfinApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val db: WhfinDatabase by lazy { WhfinDatabase.get(this) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            StatementImporter(db).repairTransferGroups()
            CategorySeeder.seedIfEmpty(
                db,
                isRussian = Locale.getDefault().language == "ru",
            )
            CategorySeeder.applyRenames(db)
            CategorySeeder.ensureCurrentPresets(db, Locale.getDefault().language == "ru")
            val activeAccounts = db.accountDao().allActive()
            if (activeAccounts.none { it.type == AccountType.CASH }) {
                db.accountDao().insert(
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
                .forEach { db.accountDao().update(it.copy(sortOrder = 1000)) }
            GeorgiaMerchantPreset.applyToUncategorized(db)
        }
    }
}
