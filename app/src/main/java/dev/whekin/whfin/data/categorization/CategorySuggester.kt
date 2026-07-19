package dev.whekin.whfin.data.categorization

import dev.whekin.whfin.data.db.CategoryEntity
import dev.whekin.whfin.data.db.CategorySample
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow

/**
 * Умные подсказки категорий для ручного ввода расходов (виджет, composer, category sheet).
 *
 * Скор двухфакторный:
 *  - частота с затуханием по давности: недавние операции весят больше (half-life 60 дней),
 *    поэтому «летняя» категория сама уходит вниз к зиме;
 *  - совместимость суммы: у каждой категории есть характерное распределение сумм
 *    (транспорт ~1–3 ₾, продукты ~20–80 ₾); близость считается гауссовым ядром в
 *    лог-пространстве по robust-статистике (медиана + IQR), отдельно на валюту.
 *    Пока личной истории мало, для стандартных семантик иконок используется широкий
 *    GEL-preset; первые пять личных операций плавно вытесняют его.
 *
 * Сумма модулирует, а не доминирует: базовый порядок задаёт частота, совместимая сумма
 * поднимает категорию, откровенно несовместимая — топит. Без введённой суммы работает
 * чистый частотный порядок. Категории без истории сохраняют переданный порядок в хвосте.
 */
class CategorySuggester(
    samples: List<CategorySample>,
    private val nowMillis: Long,
) {
    private class AmountStats(logAmounts: List<Double>) {
        val median: Double
        val sigma: Double

        init {
            val sorted = logAmounts.sorted()
            median = percentile(sorted, 0.5)
            val iqr = percentile(sorted, 0.75) - percentile(sorted, 0.25)
            // IQR/1.35 ≈ σ нормального распределения; floor держит ядро терпимым
            // к разбросу внутри категории даже при очень «узкой» истории.
            sigma = maxOf(MIN_SIGMA, iqr / 1.35)
        }

        fun affinity(amountMinor: Long): Double {
            val x = ln(amountMinor.toDouble())
            val z = (x - median) / sigma
            return exp(-0.5 * z * z)
        }
    }

    private data class PersonalAmountStats(
        val stats: AmountStats,
        val sampleCount: Int,
    )

    private data class ColdStartAmountPrior(
        val medianMinor: Long,
        val sigma: Double = COLD_START_SIGMA,
    ) {
        fun affinity(amountMinor: Long): Double {
            val z = (ln(amountMinor.toDouble()) - ln(medianMinor.toDouble())) / sigma
            return exp(-0.5 * z * z)
        }
    }

    private val frequencyScore: Map<Long, Double> = samples
        .groupBy(CategorySample::categoryId)
        .mapValues { (_, list) ->
            list.sumOf { sample ->
                val ageDays = (nowMillis - sample.occurredAt).coerceAtLeast(0L) / DAY_MILLIS
                0.5.pow(ageDays / HALF_LIFE_DAYS)
            }
        }

    /** (categoryId, currency) → личная статистика сумм, включая первые обучающие примеры. */
    private val personalAmountStats: Map<Pair<Long, String>, PersonalAmountStats> = samples
        .groupBy { it.categoryId to it.currency }
        .mapValues { (_, list) ->
            PersonalAmountStats(
                stats = AmountStats(list.map { ln(abs(it.amountMinor).toDouble()) }),
                sampleCount = list.size,
            )
        }

    /**
     * Возвращает id категорий в порядке убывания уместности. [categoryIds] задаёт
     * допустимое множество и порядок «по умолчанию» для категорий без сигнала.
     */
    fun rankCategories(
        categories: List<CategoryEntity>,
        amountMinor: Long? = null,
        currency: String? = null,
    ): List<CategoryEntity> {
        val priors = categories.associate { it.id to COLD_START_PRIORS[it.icon] }
        val order = rankInternal(
            categoryIds = categories.map { it.id },
            amountMinor = amountMinor,
            currency = currency,
            priors = priors,
            useColdStartFrequencyFloor = true,
        )
            .withIndex()
            .associate { (index, id) -> id to index }
        return categories.sortedBy { order[it.id] ?: Int.MAX_VALUE }
    }

    fun rank(categoryIds: List<Long>, amountMinor: Long? = null, currency: String? = null): List<Long> =
        rankInternal(categoryIds, amountMinor, currency, emptyMap(), useColdStartFrequencyFloor = false)

    private fun rankInternal(
        categoryIds: List<Long>,
        amountMinor: Long?,
        currency: String?,
        priors: Map<Long, ColdStartAmountPrior?>,
        useColdStartFrequencyFloor: Boolean,
    ): List<Long> {
        val maxFrequency = frequencyScore.values.maxOrNull()
        val scored = categoryIds.mapIndexed { index, id ->
            // sqrt сглаживает частоту: очень частая категория не должна перекрывать
            // явное несовпадение суммы у категории со средней частотой.
            val personalFrequency = if (maxFrequency == null) {
                1.0
            } else {
                kotlin.math.sqrt((frequencyScore[id] ?: 0.0) / maxFrequency)
            }
            val frequency = if (maxFrequency != null && useColdStartFrequencyFloor) {
                COLD_START_FREQUENCY_FLOOR +
                    (1 - COLD_START_FREQUENCY_FLOOR) * personalFrequency
            } else personalFrequency
            val amount = amountAffinity(
                id = id,
                amountMinor = amountMinor,
                currency = currency,
                prior = priors[id],
            )
            Triple(id, frequency * (AMOUNT_FLOOR + (1 - AMOUNT_FLOOR) * amount), index)
        }
        return scored
            .sortedWith(compareByDescending<Triple<Long, Double, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
    }

    private fun amountAffinity(
        id: Long,
        amountMinor: Long?,
        currency: String?,
        prior: ColdStartAmountPrior?,
    ): Double {
        if (amountMinor == null || amountMinor == 0L || currency == null) return NEUTRAL_AFFINITY
        val absoluteAmount = abs(amountMinor)
        val coldStartAffinity = if (currency == "GEL") {
            prior?.affinity(absoluteAmount) ?: NEUTRAL_AFFINITY
        } else NEUTRAL_AFFINITY
        val personal = personalAmountStats[id to currency] ?: return coldStartAffinity
        val personalWeight = (personal.sampleCount.toDouble() / PERSONAL_AMOUNT_SAMPLE_TARGET)
            .coerceIn(0.0, 1.0)
        return coldStartAffinity * (1 - personalWeight) +
            personal.stats.affinity(absoluteAmount) * personalWeight
    }

    companion object {
        /** Окно истории для статистики. */
        const val LOOKBACK_MILLIS = 365L * 86_400_000L

        private const val DAY_MILLIS = 86_400_000.0
        private const val HALF_LIFE_DAYS = 60.0
        private const val PERSONAL_AMOUNT_SAMPLE_TARGET = 5.0
        private const val MIN_SIGMA = 0.55
        private const val COLD_START_SIGMA = 0.90
        private const val COLD_START_FREQUENCY_FLOOR = 0.40
        /** Доля скора, не зависящая от суммы: частый выбор не исчезает, а опускается. */
        private const val AMOUNT_FLOOR = 0.25
        private const val NEUTRAL_AFFINITY = 0.5

        /**
         * Широкие стартовые GEL-профили. Иконка — стабильнее локализованного и
         * редактируемого имени; пользовательская категория с той же семантикой
         * осмысленно наследует тот же холодный старт.
         */
        private val COLD_START_PRIORS = mapOf(
            "DirectionsBus" to ColdStartAmountPrior(250),
            "AccountBalance" to ColdStartAmountPrior(500),
            "Subscriptions" to ColdStartAmountPrior(2_500),
            "ShoppingCart" to ColdStartAmountPrior(4_500),
            "DeliveryDining" to ColdStartAmountPrior(4_500),
            "Restaurant" to ColdStartAmountPrior(6_000),
            "MedicalServices" to ColdStartAmountPrior(8_000),
            "PedalBike" to ColdStartAmountPrior(10_000),
            "Bolt" to ColdStartAmountPrior(12_000),
            "Favorite" to ColdStartAmountPrior(12_000),
            "CardGiftcard" to ColdStartAmountPrior(12_000),
            "VolunteerActivism" to ColdStartAmountPrior(15_000),
            "Terrain" to ColdStartAmountPrior(15_000),
            "LocalShipping" to ColdStartAmountPrior(18_000),
            "Chair" to ColdStartAmountPrior(25_000),
            "Savings" to ColdStartAmountPrior(50_000),
            "Devices" to ColdStartAmountPrior(60_000),
            "Home" to ColdStartAmountPrior(100_000),
        )

        private fun percentile(sorted: List<Double>, q: Double): Double {
            if (sorted.isEmpty()) return 0.0
            val position = q * (sorted.size - 1)
            val low = position.toInt()
            val high = minOf(low + 1, sorted.size - 1)
            val fraction = position - low
            return sorted[low] * (1 - fraction) + sorted[high] * fraction
        }
    }
}
