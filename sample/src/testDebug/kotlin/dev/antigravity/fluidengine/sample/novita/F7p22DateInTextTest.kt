package dev.antigravity.fluidengine.sample.novita

import android.icu.text.DateFormatSymbols
import android.icu.text.SimpleDateFormat
import android.icu.util.TimeZone
import android.icu.util.ULocale
import dev.antigravity.fluidengine.foundation.FluidFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F7.22 · La data «25 Nov 2026» del Tab S9. Sul tablet la lingua dell'app è `it-IT` e il modello è `d MMM y`, ma le ICU di
 * Android 16 (One UI 8) scrivono il mese abbreviato con la maiuscola (una riga di diagnosi in logcat, 26 novembre 2026), e il
 * contesto «in mezzo alla frase» non abbassa niente. Qui le ICU di Robolectric ricevono gli stessi nomi del tablet: la
 * data dentro una riga deve uscire minuscola in italiano e restare com'è in inglese.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class F7p22DateInTextTest {
  private val instant: Date = Date.from(Instant.parse("2026-11-26T12:00:00Z"))

  private fun tabletFormat(locale: Locale, pattern: String): SimpleDateFormat {
    val symbols = DateFormatSymbols(ULocale.forLocale(locale))
    // I nomi come li dà il tablet: iniziale maiuscola anche dentro la frase.
    val months = symbols.getMonths(DateFormatSymbols.FORMAT, DateFormatSymbols.ABBREVIATED)
      .map { name -> name.replaceFirstChar { it.titlecase(locale) } }.toTypedArray()
    symbols.setMonths(months, DateFormatSymbols.FORMAT, DateFormatSymbols.ABBREVIATED)
    val days = symbols.getWeekdays(DateFormatSymbols.FORMAT, DateFormatSymbols.WIDE)
      .map { name -> name.replaceFirstChar { it.titlecase(locale) } }.toTypedArray()
    symbols.setWeekdays(days, DateFormatSymbols.FORMAT, DateFormatSymbols.WIDE)
    return SimpleDateFormat(pattern, symbols).also { it.timeZone = TimeZone.getTimeZone("UTC") }
  }

  @Test
  fun `in italiano il mese e il giorno escono minuscoli anche con le ICU del tablet`() {
    val italian = tabletFormat(Locale.ITALY, "d MMM y")
    assertEquals("26 Nov 2026", italian.format(instant))
    assertEquals("26 nov 2026", FluidFormat.inText(italian, instant, Locale.ITALY))
    assertEquals("giovedì 26", FluidFormat.inText(tabletFormat(Locale.ITALY, "EEEE d"), instant, Locale.ITALY))
  }

  @Test
  fun `in inglese e in tedesco i nomi restano maiuscoli`() {
    assertEquals("26 Nov 2026", FluidFormat.inText(tabletFormat(Locale.UK, "d MMM y"), instant, Locale.UK))
    assertEquals("Thursday 26", FluidFormat.inText(tabletFormat(Locale.UK, "EEEE d"), instant, Locale.UK))
    assertEquals("26. Nov. 2026", FluidFormat.inText(tabletFormat(Locale.GERMANY, "d. MMM y"), instant, Locale.GERMANY))
  }

  @Test
  fun `la data di serie resta quella della lingua`() {
    assertEquals("26 nov 2026", FluidFormat.date(Instant.parse("2026-11-26T12:00:00Z"), locale = Locale.ITALY,
      zone = java.time.ZoneId.of("UTC")))
    assertEquals("Nov 26, 2026", FluidFormat.date(Instant.parse("2026-11-26T12:00:00Z"), locale = Locale.US,
      zone = java.time.ZoneId.of("UTC")))
  }
}
