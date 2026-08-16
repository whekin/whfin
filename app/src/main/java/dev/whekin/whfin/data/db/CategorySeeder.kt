package dev.whekin.whfin.data.db

import dev.whekin.whfin.data.categorization.CategoryCatalog
import kotlinx.coroutines.flow.first

/**
 * Первичное наполнение категорий (один раз, при пустой таблице).
 *
 * Сеется только база из [CategoryCatalog]: остальное предлагается по истории после банковского
 * синка или выбирается набором. Имена — на языке устройства; дальше это данные пользователя.
 *
 * Досеивать базу существующим базам нельзя: удалённая пользователем категория воскресала бы при
 * каждом запуске. Всё, что появляется после первого запуска, приходит предложением по истории или
 * набором по интересам — то есть с согласия.
 */
object CategorySeeder {

    const val UNACCOUNTED = "unaccounted"


    suspend fun seedIfEmpty(db: WhfinDatabase, isRussian: Boolean) {
        val dao = db.categoryDao()
        if (dao.observeAll().first().isNotEmpty()) return

        // Системная категория для корректировок баланса — имя-ключ, локализуется в UI
        dao.insert(
            CategoryEntity(
                name = UNACCOUNTED,
                kind = CategoryKind.EXPENSE,
                icon = "HelpOutline",
                color = 0xFF9E9E9E.toInt(),
                isSystem = true,
                sortOrder = 999,
            ),
        )
        CategoryCatalog.base.forEachIndexed { index, definition ->
            dao.insert(
                CategoryEntity(
                    name = definition.name(isRussian),
                    kind = definition.kind,
                    icon = definition.icon,
                    color = definition.color.toInt(),
                    sortOrder = index,
                ),
            )
        }
    }
}
