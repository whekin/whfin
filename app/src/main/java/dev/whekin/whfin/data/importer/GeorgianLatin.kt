package dev.whekin.whfin.data.importer

/**
 * One name written in two alphabets.
 *
 * A Georgian bank prints its counterparties in Georgian on the statement and romanized in SMS: the
 * same exchange office arrives as `შპს უნოტრონ` and as `SHPS UNOTRON`. As strings they share not one
 * character, so anything that identifies a payment by who it was with — reconciling a statement line
 * against the message that announced it, above all — saw two different counterparties and wrote the
 * same money down twice.
 *
 * Romanization is not one-to-one in the direction that matters: several Georgian letters differ only
 * by aspiration, which Latin spells with the same letter, and several more have two spellings in
 * common use (`ც` as `c` or `ts`, `ღ` as `g` or `gh`). So both sides are reduced to the same coarse
 * skeleton rather than one side being converted to the other: the distinctions romanization blurs
 * are dropped everywhere, symmetrically. That trades a little precision for the ability to compare
 * at all, and the callers keep their own guards — a reconciled row must still be the single
 * candidate on that account and day.
 */
internal object GeorgianLatin {

    private val LETTERS = mapOf(
        'ა' to "a", 'ბ' to "b", 'გ' to "g", 'დ' to "d", 'ე' to "e", 'ვ' to "v", 'ზ' to "z",
        'თ' to "t", 'ი' to "i", 'კ' to "k", 'ლ' to "l", 'მ' to "m", 'ნ' to "n", 'ო' to "o",
        'პ' to "p", 'ჟ' to "j", 'რ' to "r", 'ს' to "s", 'ტ' to "t", 'უ' to "u", 'ფ' to "p",
        'ქ' to "k", 'ღ' to "g", 'ყ' to "q", 'შ' to "sh", 'ჩ' to "ch", 'ც' to "c", 'ძ' to "dz",
        'წ' to "c", 'ჭ' to "ch", 'ხ' to "h", 'ჯ' to "j", 'ჰ' to "h",
        // Archaic letters still seen in stylized company names.
        'ჱ' to "e", 'ჲ' to "i", 'ჳ' to "v", 'ჴ' to "h", 'ჵ' to "o", 'ჶ' to "f",
    )

    /** The Latin spellings the table above collapses, longest first so `tch` wins over `ch`. */
    private val DIGRAPHS = listOf(
        "tch" to "ch", "dj" to "j", "zh" to "j", "gh" to "g", "kh" to "h", "ts" to "c", "tz" to "c",
    )

    /** The shared skeleton of a name, whichever alphabet it arrived in. */
    fun skeleton(value: String): String {
        val romanized = buildString(value.length) {
            value.forEach { append(LETTERS[it] ?: it) }
        }
        var folded = romanized
        DIGRAPHS.forEach { (from, to) -> folded = folded.replace(from, to) }
        return folded
    }
}
