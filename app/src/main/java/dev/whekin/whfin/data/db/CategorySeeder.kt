package dev.whekin.whfin.data.db

import kotlinx.coroutines.flow.first

/**
 * Первичное наполнение категорий (один раз, при пустой таблице).
 * Имена — на языке устройства (ru/en); дальше это данные пользователя, он их редактирует.
 */
object CategorySeeder {

    const val UNACCOUNTED = "unaccounted"

    private data class Preset(
        val en: String,
        val ru: String,
        val kind: CategoryKind,
        val icon: String,
        val color: Long,
    )

    private val presets = listOf(
        Preset("Groceries", "Продукты", CategoryKind.EXPENSE, "ShoppingCart", 0xFF4CAF50),
        Preset("Eating out", "Заведения", CategoryKind.EXPENSE, "Restaurant", 0xFFFF7043),
        Preset("Food delivery", "Доставка еды", CategoryKind.EXPENSE, "DeliveryDining", 0xFFFFA726),
        Preset("Rent", "Аренда", CategoryKind.EXPENSE, "Home", 0xFF5C6BC0),
        Preset("Utilities", "Коммуналка", CategoryKind.EXPENSE, "Bolt", 0xFF26A69A),
        Preset("Transport", "Транспорт", CategoryKind.EXPENSE, "DirectionsBus", 0xFF42A5F5),
        Preset("Subscriptions", "Подписки", CategoryKind.EXPENSE, "Subscriptions", 0xFFAB47BC),
        Preset("Bike", "Велосипед", CategoryKind.EXPENSE, "PedalBike", 0xFF66BB6A),
        Preset("Bike trips", "Велозаброски", CategoryKind.EXPENSE, "Terrain", 0xFF8D6E63),
        Preset("Goods", "Заказы", CategoryKind.EXPENSE, "LocalShipping", 0xFF78909C),
        Preset("Health", "Здоровье", CategoryKind.EXPENSE, "MedicalServices", 0xFFEF5350),
        Preset("Family help", "Помощь близким", CategoryKind.EXPENSE, "VolunteerActivism", 0xFFD16D5A),
        Preset("Relationships", "Отношения", CategoryKind.EXPENSE, "Favorite", 0xFFC96A78),
        Preset("Gifts", "Подарки", CategoryKind.EXPENSE, "CardGiftcard", 0xFFE0A246),
        Preset("Tech", "Техника", CategoryKind.EXPENSE, "Devices", 0xFF7E57C2),
        Preset("Home", "Дом", CategoryKind.EXPENSE, "Chair", 0xFF9CCC65),
        Preset("Bank fees", "Комиссии банка", CategoryKind.EXPENSE, "AccountBalance", 0xFF90A4AE),
        Preset("Savings", "Накопления", CategoryKind.EXPENSE, "Savings", 0xFF26C6DA),
        Preset("Other", "Прочее", CategoryKind.EXPENSE, "Category", 0xFFBDBDBD),
        Preset("Salary", "Зарплата", CategoryKind.INCOME, "Payments", 0xFF66BB6A),
        Preset("Side income", "Подработка", CategoryKind.INCOME, "Work", 0xFF9CCC65),
        Preset("Sales", "Продажи", CategoryKind.INCOME, "Sell", 0xFFFFCA28),
        Preset("Interest", "Проценты", CategoryKind.INCOME, "Percent", 0xFF26C6DA),
    )

    /** Точечные переименования пресетов на существующих базах (имена = данные пользователя). */
    suspend fun applyRenames(db: WhfinDatabase) {
        db.categoryDao().rename("Goods from abroad", "Goods")
        db.categoryDao().rename("Заказы из-за границы", "Заказы")
        db.categoryDao().rename("Family & giving", "Family help")
        db.categoryDao().rename("Близкие и помощь", "Помощь близким")
    }

    /** Добавляет новые пресеты без сброса и без изменения пользовательских категорий. */
    suspend fun ensureCurrentPresets(db: WhfinDatabase, isRussian: Boolean) {
        val existing = db.categoryDao().all()
        presets.filter { it.icon in setOf("VolunteerActivism", "Favorite", "CardGiftcard") }
            .filter { preset -> existing.none { it.icon == preset.icon && it.kind == preset.kind } }
            .forEach { preset ->
                db.categoryDao().insert(
                    CategoryEntity(
                        name = if (isRussian) preset.ru else preset.en,
                        kind = preset.kind,
                        icon = preset.icon,
                        color = preset.color.toInt(),
                        sortOrder = presets.indexOf(preset),
                    ),
                )
            }
    }

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
        presets.forEachIndexed { index, preset ->
            dao.insert(
                CategoryEntity(
                    name = if (isRussian) preset.ru else preset.en,
                    kind = preset.kind,
                    icon = preset.icon,
                    color = preset.color.toInt(),
                    sortOrder = index,
                ),
            )
        }
    }
}
