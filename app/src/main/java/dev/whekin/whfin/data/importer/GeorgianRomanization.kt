package dev.whekin.whfin.data.importer

/**
 * How a Georgian name is said in Latin letters.
 *
 * Deliberately NOT [GeorgianLatin.skeleton], which exists to decide whether two spellings are the
 * same name and therefore throws away everything romanization blurs — under the skeleton `მიხეილ`
 * reads `miheil`. The two tables answer different questions: one asks whether two names are the
 * same, this one asks how a name is read. Keeping them apart is the point; folding them into one
 * table would either make comparison miss or make reading wrong.
 *
 * This is the Georgian national system, as passports and the country's other banks print it. It was
 * checked against the seven counterparties whose Latin spelling a second bank supplied
 * independently: all seven come out character for character. The ejective consonants lose their
 * apostrophes — `კ` and `ქ` both become `k` — which is what those banks do, and which makes the
 * table one-way: a reading, never a key.
 */
object GeorgianRomanization {

    private val LETTERS = mapOf(
        'ა' to "a", 'ბ' to "b", 'გ' to "g", 'დ' to "d", 'ე' to "e", 'ვ' to "v", 'ზ' to "z",
        'თ' to "t", 'ი' to "i", 'კ' to "k", 'ლ' to "l", 'მ' to "m", 'ნ' to "n", 'ო' to "o",
        'პ' to "p", 'ჟ' to "zh", 'რ' to "r", 'ს' to "s", 'ტ' to "t", 'უ' to "u", 'ფ' to "p",
        'ქ' to "k", 'ღ' to "gh", 'ყ' to "q", 'შ' to "sh", 'ჩ' to "ch", 'ც' to "ts", 'ძ' to "dz",
        'წ' to "ts", 'ჭ' to "ch", 'ხ' to "kh", 'ჯ' to "j", 'ჰ' to "h",
        // Archaic letters, still seen in stylized company names.
        'ჱ' to "e", 'ჲ' to "i", 'ჳ' to "v", 'ჴ' to "kh", 'ჵ' to "o", 'ჶ' to "f",
    )

    /** Whether this text is written in Georgian letters. */
    fun isGeorgian(value: String): Boolean = value.any { it in 'Ⴀ'..'ჿ' }

    /**
     * The name in Latin letters, capitalized like a name.
     *
     * Georgian has no case at all, so romanizing alone would print a row of lowercase among names
     * that all carry capitals. Each word takes one, at its first letter rather than its first
     * character, so `*chatgpt` does not become `*chatgpt` with the star capitalized.
     */
    fun romanize(value: String): String = buildString(value.length) {
        value.forEach { append(LETTERS[it] ?: it) }
    }.split(" ").joinToString(" ") { word ->
        val index = word.indexOfFirst(Char::isLetter)
        if (index < 0) word else word.take(index) + word[index].titlecase() + word.drop(index + 1)
    }
}
