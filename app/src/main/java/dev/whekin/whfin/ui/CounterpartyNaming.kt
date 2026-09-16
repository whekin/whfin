package dev.whekin.whfin.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.whekin.whfin.data.importer.GeorgianRomanization

/**
 * How a counterparty is read, when their bank wrote them in an alphabet the owner does not read.
 *
 * The name on a statement line belongs to the counterparty's bank, not to the statement: Credo
 * registers its clients in Georgian, TBC and Bank of Georgia in Latin. So a ledger kept in one city
 * prints the same kind of person two ways, and half of them in letters their reader cannot sound
 * out.
 *
 * This changes the reading only. The stored name stays exactly what the bank wrote, because that is
 * the evidence the ledger is checked against — a row has to be findable on the statement it came
 * from, by eye.
 */

/**
 * Whether Georgian counterparties are romanized on screen.
 *
 * Static, because it changes once from a settings switch and every row printing a name would
 * otherwise recompose through a value nothing reads per-row.
 */
val LocalLatinCounterparties = staticCompositionLocalOf { false }

/** The name as it should be read here: romanized when it is Georgian and the owner asked for it. */
fun counterpartyLabel(name: String, latin: Boolean): String =
    if (latin && GeorgianRomanization.isGeorgian(name)) GeorgianRomanization.romanize(name) else name

@Composable
@ReadOnlyComposable
fun counterpartyLabel(name: String): String =
    counterpartyLabel(name, LocalLatinCounterparties.current)

/**
 * Whether a search needle matches this name, in either alphabet.
 *
 * Both spellings are always searched, whichever way the switch is set: the owner may type what the
 * screen shows or what the statement shows, and neither should come back empty.
 */
fun counterpartyMatches(name: String, needle: String): Boolean =
    name.contains(needle, ignoreCase = true) ||
        (
            GeorgianRomanization.isGeorgian(name) &&
                GeorgianRomanization.romanize(name).contains(needle, ignoreCase = true)
            )
