package dev.whekin.whfin.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCatalogTest {

    private val catalog = listOf(
        SettingsSection(
            id = "bank",
            label = "Банк и импорт",
            rows = listOf(
                SettingsRow(id = "credo", title = "MyCredo", summary = "Сверено вчера", keywords = "банк синхронизация"),
                SettingsRow(id = "sms", title = "Сообщения банка", summary = "Выключено", keywords = "смс карты"),
            ),
        ),
        SettingsSection(
            id = "data",
            label = "Данные и безопасность",
            rows = listOf(
                SettingsRow(id = "app-lock", title = "Блокировка входа", keywords = "пин код отпечаток"),
                SettingsRow(id = "backup", title = "Бэкап", summary = "Только вручную", keywords = "копия восстановление"),
            ),
        ),
    )

    @Test
    fun `a blank query is not a filter`() {
        assertEquals(catalog, filterSettings(catalog, "   "))
    }

    @Test
    fun `a row is found by what people call it, not by its title`() {
        val found = filterSettings(catalog, "отпечаток").flatMap { it.rows }.map { it.id }

        assertEquals(listOf("app-lock"), found)
    }

    @Test
    fun `each further word narrows the result`() {
        assertEquals(
            listOf("credo", "sms"),
            filterSettings(catalog, "банк").flatMap { it.rows }.map { it.id },
        )
        assertEquals(
            listOf("sms"),
            filterSettings(catalog, "банк смс").flatMap { it.rows }.map { it.id },
        )
    }

    @Test
    fun `the section name is part of what a row can be found by`() {
        val found = filterSettings(catalog, "безопасность").flatMap { it.rows }.map { it.id }

        assertEquals(listOf("app-lock", "backup"), found)
    }

    @Test
    fun `state shown on a row is searchable too`() {
        assertEquals(
            listOf("backup"),
            filterSettings(catalog, "вручную").flatMap { it.rows }.map { it.id },
        )
    }

    @Test
    fun `an empty section disappears instead of standing empty`() {
        val filtered = filterSettings(catalog, "пин")

        assertEquals(listOf("data"), filtered.map { it.id })
    }

    @Test
    fun `nothing matching gives nothing, not everything`() {
        assertTrue(filterSettings(catalog, "квартира").isEmpty())
    }

    @Test
    fun `an optional letter does not hide a row`() {
        val sections = listOf(
            SettingsSection(
                id = "app",
                label = "Приложение",
                rows = listOf(SettingsRow(id = "theme", title = "Тёмная тема")),
            ),
        )

        assertEquals(1, filterSettings(sections, "темная").flatMap { it.rows }.size)
        assertEquals(1, filterSettings(sections, "ТЁМНАЯ").flatMap { it.rows }.size)
    }
}
