package dev.whekin.whfin.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/**
 * The calendar a ledger row belongs to: the one the bank keeps.
 *
 * A stored row is an instant, and an instant has no day until some zone is asked. WHFIN used to ask
 * two different ones. The importers read the wall clock a bank prints — "06/09/2026 22:14:03" — in
 * Tbilisi time, which is right, because that is the clock the bank wrote it by. The feed and
 * analytics then bucketed the resulting instant into a day using the phone's own zone, which is also
 * right on its own terms: an instant does fall on the reader's day where the reader stands.
 *
 * Together they only agree while the phone sits on +04. Away from it — a week abroad is enough — an
 * operation near the end of a Tbilisi day moves to the next day in the feed and in the monthly
 * totals, and the ledger's days stop lining up with the statement's days. How much of the evening
 * moves depends on how far the reader has gone: one hour of it in Almaty, four in Bangkok. That is
 * the wrong half to give up: a ledger exists to be checked against the bank's, and a
 * Sunday-evening payment that the statement files under Sunday must not be filed here under Monday
 * because its owner happened to be in Almaty.
 *
 * So one zone owns the question, and it is the bank's. Every place that turns an instant into a day,
 * a month or a period reads it from here, and so does every place that turns a day the owner picked
 * into an instant — otherwise a manual entry would be written on one calendar and read back on
 * another.
 *
 * What this is *not* for: the time something happened to the reader rather than to their money. When
 * a backup was taken, when a sync last ran, when a diagnostic was recorded — those are events in the
 * reader's own day and keep using the system zone.
 *
 * A second country makes this a property of the bank rather than of the app: [zone] would move onto
 * `BankProfile`, and a row would take the calendar of the bank that issued it. Until WHFIN reads a
 * bank outside Georgia there is one answer, and pretending otherwise would be a setting nobody can
 * fill in correctly.
 */
object LedgerCalendar {

    /** The zone every ledger day, month and period is counted in. */
    val zone: ZoneId = ZoneId.of("Asia/Tbilisi")

    /** The day an instant belongs to. */
    fun dayOf(epochMillis: Long): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** The month an instant belongs to. */
    fun monthOf(epochMillis: Long): YearMonth = YearMonth.from(dayOf(epochMillis))

    /** Today, on the ledger's calendar rather than the reader's. */
    fun today(): LocalDate = LocalDate.now(zone)

    /** The month in progress, on the ledger's calendar. */
    fun currentMonth(): YearMonth = YearMonth.now(zone)

    /** The instant a day begins, for range queries against stored timestamps. */
    fun startOfDay(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    /** The instant a chosen day and time name, for rows the owner writes by hand. */
    fun instantOf(day: LocalDate, time: LocalTime): Long =
        day.atTime(time).atZone(zone).toInstant().toEpochMilli()
}
