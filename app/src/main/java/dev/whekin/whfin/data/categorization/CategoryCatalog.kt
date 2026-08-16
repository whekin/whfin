package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryKind

/**
 * Every category WHFIN knows how to name, and which of them a fresh ledger starts with.
 *
 * The base is deliberately short. A guessed preset is mostly dead weight — on a real four-year
 * ledger half of a twenty-category preset had never been used once — and a category nobody uses
 * still costs a line in every picker and every monthly list. So the base holds only what is true of
 * anyone who spends money in a country, and the rest waits to be earned: either proposed from the
 * history a bank sync just produced, or chosen as an interest pack the user picks by hand.
 *
 * Bank fees and deposit interest are base despite being rare, because their categories are filled
 * automatically from the operation the bank names. Waiting for evidence would mean the evidence
 * could never arrive.
 */
object CategoryCatalog {

    data class Definition(
        val en: String,
        val ru: String,
        val kind: CategoryKind,
        val icon: String,
        val color: Long,
        /** Present on a fresh ledger before anything is known about the person. */
        val base: Boolean = false,
        /**
         * The category this one belongs inside, named by icon because ids do not exist yet.
         *
         * A pack is the one place the shape of a group is known in advance — it exists precisely to
         * describe a whole interest — so losing it at creation would leave the user rebuilding by
         * hand what was already written down. Categories proposed from history stay flat: they are
         * found one merchant at a time and cannot know what they are part of.
         */
        val parentIcon: String? = null,
    ) {
        fun name(isRussian: Boolean): String = if (isRussian) ru else en
    }

    val all: List<Definition> = listOf(
        Definition("Groceries", "Продукты", CategoryKind.EXPENSE, "ShoppingCart", 0xFF4CAF50, base = true),
        Definition("Eating out", "Заведения", CategoryKind.EXPENSE, "Restaurant", 0xFFFF7043, base = true),
        Definition("Transport", "Транспорт", CategoryKind.EXPENSE, "DirectionsBus", 0xFF42A5F5, base = true),
        Definition("Rent", "Аренда", CategoryKind.EXPENSE, "Home", 0xFF5C6BC0, base = true),
        Definition("Utilities", "Коммуналка", CategoryKind.EXPENSE, "Bolt", 0xFF26A69A, base = true),
        Definition("Health", "Здоровье", CategoryKind.EXPENSE, "MedicalServices", 0xFFEF5350, base = true),
        Definition("Bank fees", "Комиссии банка", CategoryKind.EXPENSE, "AccountBalance", 0xFF90A4AE, base = true),
        // Base because the bank fills it by itself: a fresh ledger files every bill payment here
        // from the first import, and a category the automation needs cannot be optional.
        Definition("Bills & charges", "Счета и платежи", CategoryKind.EXPENSE, "ReceiptLong", 0xFF8D9440, base = true),
        Definition("Salary", "Зарплата", CategoryKind.INCOME, "Payments", 0xFF66BB6A, base = true),
        Definition("Interest", "Проценты", CategoryKind.INCOME, "Percent", 0xFF26C6DA, base = true),

        Definition("Food delivery", "Доставка еды", CategoryKind.EXPENSE, "DeliveryDining", 0xFFFFA726),
        Definition("Subscriptions", "Подписки", CategoryKind.EXPENSE, "Subscriptions", 0xFFAB47BC),
        Definition("Tech", "Техника", CategoryKind.EXPENSE, "Devices", 0xFF7E57C2),
        Definition("Home", "Дом", CategoryKind.EXPENSE, "Chair", 0xFF9CCC65),
        Definition("Goods", "Заказы", CategoryKind.EXPENSE, "LocalShipping", 0xFF78909C),
        Definition("Insurance", "Страховка", CategoryKind.EXPENSE, "HealthAndSafety", 0xFF5C8A8A),
        Definition("Personal care", "Уход за собой", CategoryKind.EXPENSE, "ContentCut", 0xFFB07A9E),
        Definition("Entertainment", "Развлечения", CategoryKind.EXPENSE, "Celebration", 0xFFE0846A),
        Definition("Shopping", "Покупки", CategoryKind.EXPENSE, "ShoppingBag", 0xFF7C93B8),
        Definition("Legal", "Документы", CategoryKind.EXPENSE, "Gavel", 0xFF8C7B6B),
        Definition("Internet", "Интернет", CategoryKind.EXPENSE, "Router", 0xFF4DA3A3),
        Definition("Dentist", "Стоматология", CategoryKind.EXPENSE, "Vaccines", 0xFFE0736F, parentIcon = "MedicalServices"),
        Definition("Fitness", "Спортзал", CategoryKind.EXPENSE, "FitnessCenter", 0xFFCC7A66, parentIcon = "MedicalServices"),
        Definition("Bike", "Велосипед", CategoryKind.EXPENSE, "PedalBike", 0xFF66BB6A),
        Definition("Lifts and shuttles", "Заброски", CategoryKind.EXPENSE, "Terrain", 0xFF8D6E63, parentIcon = "PedalBike"),
        Definition("Bike service", "Сервис вела", CategoryKind.EXPENSE, "Handyman", 0xFF7E9E6A, parentIcon = "PedalBike"),
        Definition("Bike rental", "Аренда вела", CategoryKind.EXPENSE, "Key", 0xFF8FA86F, parentIcon = "PedalBike"),
        Definition("Gear", "Снаряга", CategoryKind.EXPENSE, "Backpack", 0xFF6D806F),
        Definition("Snowboard", "Сноуборд", CategoryKind.EXPENSE, "AcUnit", 0xFF5D7F91),
        Definition("Travel", "Поездки", CategoryKind.EXPENSE, "Luggage", 0xFF9A6A55),
        Definition("Music", "Музыка", CategoryKind.EXPENSE, "MusicNote", 0xFF9B7BB8),
        Definition("Volunteering", "Волонтёрство", CategoryKind.EXPENSE, "Diversity3", 0xFF6FA08A),
        Definition("Family help", "Помощь близким", CategoryKind.EXPENSE, "VolunteerActivism", 0xFFD16D5A),
        Definition("Relationships", "Отношения", CategoryKind.EXPENSE, "Favorite", 0xFFC96A78),
        Definition("Gifts", "Подарки", CategoryKind.EXPENSE, "CardGiftcard", 0xFFE0A246),
        Definition("Savings", "Накопления", CategoryKind.EXPENSE, "Savings", 0xFF26C6DA),
        Definition("Other", "Прочее", CategoryKind.EXPENSE, "Category", 0xFFBDBDBD),
        Definition("Side income", "Подработка", CategoryKind.INCOME, "Work", 0xFF9CCC65),
        Definition("Sales", "Продажи", CategoryKind.INCOME, "Sell", 0xFFFFCA28),
    )

    val base: List<Definition> = all.filter { it.base }

    fun byIcon(icon: String, kind: CategoryKind): Definition? =
        all.firstOrNull { it.icon == icon && it.kind == kind }
}

/**
 * Interests a ledger cannot reveal on its own.
 *
 * A pack is offered and chosen, never applied silently — which is the whole difference between a
 * useful default and putting one person's life into everybody's install.
 */
object CategoryPacks {

    data class Pack(val id: String, val en: String, val ru: String, val icons: List<String>) {
        fun name(isRussian: Boolean): String = if (isRussian) ru else en
    }

    val all: List<Pack> = listOf(
        Pack(
            id = "outdoor",
            en = "Outdoor and sport",
            ru = "Аутдор и спорт",
            icons = listOf(
                "PedalBike", "Terrain", "Handyman", "Key",
                "Backpack", "AcUnit", "Luggage",
            ),
        ),
        Pack(
            id = "everyday",
            en = "Everyday life",
            ru = "Повседневное",
            icons = listOf("ContentCut", "Celebration", "ShoppingBag"),
        ),
        Pack(
            id = "household",
            en = "Home and family",
            ru = "Дом и семья",
            icons = listOf("Chair", "VolunteerActivism", "CardGiftcard", "Favorite"),
        ),
        Pack(
            id = "online",
            en = "Online life",
            ru = "Онлайн",
            icons = listOf("Subscriptions", "Devices", "LocalShipping", "DeliveryDining", "Router"),
        ),
        Pack(
            id = "paperwork",
            en = "Living abroad",
            ru = "Жизнь за границей",
            icons = listOf("Gavel", "HealthAndSafety"),
        ),
        Pack(
            id = "wellbeing",
            en = "Health in detail",
            ru = "Здоровье подробнее",
            icons = listOf("Vaccines", "FitnessCenter"),
        ),
        Pack(
            id = "hobbies",
            en = "Other interests",
            ru = "Другие увлечения",
            icons = listOf("MusicNote", "Diversity3"),
        ),
    )

    /**
     * A pack's categories, parents always ahead of their children.
     *
     * Creation resolves a parent by looking it up among categories that already exist, so a child
     * written first would silently land at the top level and the group would be lost in exactly the
     * case packs exist to serve.
     */
    fun definitions(pack: Pack): List<CategoryCatalog.Definition> = pack.icons
        .mapNotNull { icon -> CategoryCatalog.all.firstOrNull { it.icon == icon } }
        .sortedBy { it.parentIcon != null }
}
