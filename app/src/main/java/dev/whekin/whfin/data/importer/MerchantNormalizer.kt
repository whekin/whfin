package dev.whekin.whfin.data.importer

/**
 * Нормализация имени мерчанта/контрагента в канонический ключ словаря.
 * Один и тот же магазин приходит по-разному: `spar>Tbilisi` (SMS), `SPAR` (выписка),
 * `NIKORA TRADE JSC>DIDGORI` vs `NIKORA`.
 */
object MerchantNormalizer {

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
}
