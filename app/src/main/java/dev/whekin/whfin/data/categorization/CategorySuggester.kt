package dev.whekin.whfin.data.categorization

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

    private val frequencyScore: Map<Long, Double> = samples
        .groupBy(CategorySample::categoryId)
        .mapValues { (_, list) ->
            list.sumOf { sample ->
                val ageDays = (nowMillis - sample.occurredAt).coerceAtLeast(0L) / DAY_MILLIS
                0.5.pow(ageDays / HALF_LIFE_DAYS)
            }
        }

    /** (categoryId, currency) → статистика сумм; только при достаточной выборке. */
    private val amountStats: Map<Pair<Long, String>, AmountStats> = samples
        .groupBy { it.categoryId to it.currency }
        .filterValues { it.size >= MIN_AMOUNT_SAMPLES }
        .mapValues { (_, list) -> AmountStats(list.map { ln(abs(it.amountMinor).toDouble()) }) }

    /**
     * Возвращает id категорий в порядке убывания уместности. [categoryIds] задаёт
     * допустимое множество и порядок «по умолчанию» для категорий без сигнала.
     */
    fun rankCategories(
        categories: List<dev.whekin.whfin.data.db.CategoryEntity>,
        amountMinor: Long? = null,
        currency: String? = null,
    ): List<dev.whekin.whfin.data.db.CategoryEntity> {
        val order = rank(categories.map { it.id }, amountMinor, currency)
            .withIndex()
            .associate { (index, id) -> id to index }
        return categories.sortedBy { order[it.id] ?: Int.MAX_VALUE }
    }

    fun rank(categoryIds: List<Long>, amountMinor: Long? = null, currency: String? = null): List<Long> {
        val maxFrequency = frequencyScore.values.maxOrNull() ?: return categoryIds
        val scored = categoryIds.mapIndexed { index, id ->
            // sqrt сглаживает частоту: очень частая категория не должна перекрывать
            // явное несовпадение суммы у категории со средней частотой.
            val frequency = kotlin.math.sqrt((frequencyScore[id] ?: 0.0) / maxFrequency)
            val amount = if (amountMinor != null && amountMinor != 0L && currency != null) {
                amountStats[id to currency]?.affinity(abs(amountMinor)) ?: NEUTRAL_AFFINITY
            } else {
                NEUTRAL_AFFINITY
            }
            Triple(id, frequency * (AMOUNT_FLOOR + (1 - AMOUNT_FLOOR) * amount), index)
        }
        return scored
            .sortedWith(compareByDescending<Triple<Long, Double, Int>> { it.second }.thenBy { it.third })
            .map { it.first }
    }

    companion object {
        /** Окно истории для статистики. */
        const val LOOKBACK_MILLIS = 365L * 86_400_000L

        private const val DAY_MILLIS = 86_400_000.0
        private const val HALF_LIFE_DAYS = 60.0
        private const val MIN_AMOUNT_SAMPLES = 5
        private const val MIN_SIGMA = 0.55
        /** Доля скора, не зависящая от суммы: частый выбор не исчезает, а опускается. */
        private const val AMOUNT_FLOOR = 0.25
        private const val NEUTRAL_AFFINITY = 0.5

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
