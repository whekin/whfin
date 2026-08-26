package dev.whekin.whfin.ui.settings

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One line of Settings, described rather than drawn.
 *
 * Settings grew into five sections of doors, and finding the one you came for meant reading all of
 * them. Describing the rows makes two things possible that hand-written rows could not have: they can
 * be searched by what the person calls them, and the order of the sections becomes a decision in one
 * place instead of a property of the file's layout.
 */
internal data class SettingsRow(
    val id: String,
    val title: String,
    /** What is true right now, when the row can say it: a last sync, a state, a count. */
    val summary: String? = null,
    /**
     * What the person might type looking for this row.
     *
     * People search for the thing, not for the app's name for it: "пин" and "отпечаток" are how App
     * Lock is looked for, "копия" is how a backup is. Kept apart from the summary so the visible text
     * never has to carry synonyms it does not need.
     */
    val keywords: String = "",
    val icon: ImageVector? = null,
    val control: SettingsControl = SettingsControl.Navigate,
    val enabled: Boolean = true,
    val destructive: Boolean = false,
    val onClick: (() -> Unit)? = null,
)

internal sealed interface SettingsControl {
    /** Opens something; draws the chevron. */
    data object Navigate : SettingsControl

    data class Toggle(
        val checked: Boolean,
        val contentDescription: String,
        val onCheckedChange: (Boolean) -> Unit,
    ) : SettingsControl

    /** The row is the control itself — a choice rendered under its own title. */
    data object Inline : SettingsControl
}

internal data class SettingsSection(
    val id: String,
    val label: String,
    val rows: List<SettingsRow>,
)

/**
 * Keeps the rows a query asks for, dropping sections that end up empty.
 *
 * Every word of the query has to land somewhere in the same row, so a second word narrows instead of
 * widening. Matching is on the row's own words plus its synonyms; a blank query is not a filter and
 * returns the catalogue untouched.
 */
internal fun filterSettings(sections: List<SettingsSection>, query: String): List<SettingsSection> {
    val terms = normalizeSettingsQuery(query).split(' ').filter(String::isNotEmpty)
    if (terms.isEmpty()) return sections
    return sections.mapNotNull { section ->
        val rows = section.rows.filter { row -> matchesSettingsQuery(row, section.label, terms) }
        if (rows.isEmpty()) null else section.copy(rows = rows)
    }
}

private fun matchesSettingsQuery(
    row: SettingsRow,
    sectionLabel: String,
    terms: List<String>,
): Boolean {
    // The section label is part of the haystack: "данные" should find what lives under Data, even
    // when the row itself never repeats the word.
    val haystack = normalizeSettingsQuery(
        listOfNotNull(row.title, row.summary, row.keywords, sectionLabel).joinToString(" "),
    )
    return terms.all(haystack::contains)
}

/**
 * Lowercases, collapses whitespace and folds `ё` to `е`.
 *
 * The fold is not cosmetic: Russian keyboards make `ё` optional, so a catalogue that spells it and a
 * person who does not would otherwise never meet.
 */
internal fun normalizeSettingsQuery(value: String): String = value
    .lowercase()
    .replace('ё', 'е')
    .replace(Regex("\\s+"), " ")
    .trim()
