package dev.whekin.whfin.data.importer

/**
 * Нормализация имени мерчанта/контрагента в канонический ключ словаря.
 * Один и тот же магазин приходит по-разному: `spar>Tbilisi` (SMS), `SPAR` (выписка),
 * `NIKORA TRADE JSC>DIDGORI` vs `NIKORA`.
 */
object MerchantNormalizer {

    private const val PREFIX_MINIMUM = 5

    fun normalize(raw: String): String =
        raw.substringBefore('>')
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
            .trimEnd('.', ',', ';')

    /** Человекочитаемое имя из сырого: без хвоста локации, схлопнутые пробелы. */
    fun displayName(raw: String): String =
        raw.substringBefore('>')
            .replace(Regex("""\s+"""), " ")
            .trim()

    /**
     * Whether two bank channels describe the same merchant.
     *
     * Processors routinely append a product/site suffix in SMS while statements retain only the
     * acquirer prefix (`ANTHROPIC* CLAUDE.AI` versus `ANTHROPIC`). Short prefixes are refused so two
     * unrelated shops cannot collapse merely because their names start alike.
     */
    fun equivalent(firstRaw: String?, secondRaw: String?): Boolean {
        val first = firstRaw?.let(::normalize).orEmpty()
        val second = secondRaw?.let(::normalize).orEmpty()
        if (first.isEmpty() || second.isEmpty()) return false
        if (first == second) return true
        if (first.length == second.length) return false
        val (shorter, longer) = if (first.length < second.length) first to second else second to first
        return shorter.length >= PREFIX_MINIMUM && longer.startsWith(shorter)
    }
}
