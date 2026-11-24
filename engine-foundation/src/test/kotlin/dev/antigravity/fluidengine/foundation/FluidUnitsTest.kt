package dev.antigravity.fluidengine.foundation

import java.math.BigDecimal
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.3 · le unità, le conversioni e i formati del campo con unità: esatte (mai Double), in italiano e in inglese, e la
 * lettura larga di un numero incollato da un'altra app.
 */
class FluidUnitsTest {
  private val it = Locale.ITALY
  private val en = Locale.US
  private val nbsp = FluidUnitFormat.NoBreakSpace

  private fun d(text: String) = BigDecimal(text)

  /** Lo stesso numero, qualunque sia la scala («12,5» e «12,50» sono lo stesso importo). */
  private fun num(expected: String, actual: BigDecimal?) {
    assertTrue("atteso $expected, letto $actual", actual != null && actual.compareTo(BigDecimal(expected)) == 0)
  }

  // --- Le conversioni -------------------------------------------------------------------------------------------

  @Test fun `le definizioni sono esatte`() {
    assertEquals(d("1609.344"), FluidUnits.convert(d("1"), FluidUnits.Mile, FluidUnits.Meter, 3))
    assertEquals(d("2.54"), FluidUnits.convert(d("1"), FluidUnits.Inch, FluidUnits.Centimeter, 2))
    assertEquals(d("0.45359237"), FluidUnits.convert(d("1"), FluidUnits.Pound, FluidUnits.Kilogram, 8))
    assertEquals(d("3.785411784"), FluidUnits.convert(d("1"), FluidUnits.GallonUS, FluidUnits.Liter, 9))
    assertEquals(d("1048576"), FluidUnits.convert(d("1"), FluidUnits.Mebibyte, FluidUnits.Byte, 0))
  }

  @Test fun `la temperatura passa dallo scostamento, senza arrotondamenti nascosti`() {
    assertEquals(d("37.0"), FluidUnits.convert(d("98.6"), FluidUnits.Fahrenheit, FluidUnits.Celsius, 1))
    assertEquals(d("-40.0"), FluidUnits.convert(d("-40"), FluidUnits.Celsius, FluidUnits.Fahrenheit, 1))
    assertEquals(d("212.0"), FluidUnits.convert(d("100"), FluidUnits.Celsius, FluidUnits.Fahrenheit, 1))
    assertEquals(d("0.00"), FluidUnits.convert(d("273.15"), FluidUnits.Kelvin, FluidUnits.Celsius, 2))
    assertEquals(d("32.00"), FluidUnits.convert(d("273.15"), FluidUnits.Kelvin, FluidUnits.Fahrenheit, 2))
  }

  @Test fun `andata e ritorno tornano al valore`() {
    val km = d("12.5")
    val mi = FluidQuantity(km, FluidUnits.Kilometer).to(FluidUnits.Mile, 20)!!
    val back = mi.to(FluidUnits.Kilometer, 10)!!
    assertEquals(0, back.value.compareTo(d("12.5")))
  }

  @Test fun `l'arrotondamento è al pari, come le FormatStyle`() {
    // 1 km/h = 5/18 m/s: 0,2777… → 0,28; 2,5 m → 0,0025 km, a tre cifre il pari è 0,002.
    assertEquals(d("0.28"), FluidUnits.convert(d("1"), FluidUnits.KilometersPerHour, FluidUnits.MetersPerSecond, 2))
    assertEquals(d("0.002"), FluidUnits.convert(d("2"), FluidUnits.Meter, FluidUnits.Kilometer, 3))
    assertEquals(d("0.002"), FluidUnits.convert(d("2.5"), FluidUnits.Meter, FluidUnits.Kilometer, 3))
    assertEquals(d("0.004"), FluidUnits.convert(d("3.5"), FluidUnits.Meter, FluidUnits.Kilometer, 3))
  }

  @Test fun `dimensioni diverse e valute senza cambio non si convertono`() {
    assertNull(FluidUnits.convert(d("1"), FluidUnits.Meter, FluidUnits.Kilogram))
    assertNull(FluidUnits.convert(d("1"), FluidUnits.Euro, FluidUnits.Dollar))
    assertFalse(FluidUnits.Euro.canConvertTo(FluidUnits.Dollar))
    // Col cambio dell'app sì: 1 USD = 0,92 EUR (base: euro).
    val euro = FluidUnit.currency("EUR", rate = "1")
    val dollar = FluidUnit.currency("USD", rate = "0.92")
    assertEquals(d("92.00"), FluidUnits.convert(d("100"), dollar, euro))
    assertEquals(2, FluidUnits.Euro.fractionDigits)
    assertEquals(0, FluidUnit.currency("JPY").fractionDigits)
  }

  @Test fun `l'unità base e le unità per dimensione`() {
    assertEquals(0, FluidQuantity(d("1.5"), FluidUnits.Kilometer).inBase()!!.compareTo(d("1500")))
    assertEquals(0, FluidQuantity(d("15"), FluidUnits.Percent).inBase()!!.compareTo(d("0.15")))
    assertTrue(FluidUnits.of(FluidUnitDimension.Length).containsAll(listOf(FluidUnits.Kilometer, FluidUnits.Mile)))
    assertEquals(FluidUnits.Celsius, FluidUnits.byId("celsius"))
  }

  // --- Scrivere --------------------------------------------------------------------------------------------------

  @Test fun `i numeri nelle due lingue`() {
    assertEquals("1.234,5", FluidUnitFormat.number(d("1234.5"), it, 2))
    assertEquals("1,234.5", FluidUnitFormat.number(d("1234.5"), en, 2))
    assertEquals("12,50", FluidUnitFormat.number(d("12.5"), it, 2, 2))
    assertEquals("999", FluidUnitFormat.number(d("999"), it, 2))
    assertEquals("-1.000.000", FluidUnitFormat.number(d("-1000000"), it, 0))
    // Al pari: 0,125 → 0,12 (il 2 è pari), 0,135 → 0,14.
    assertEquals("0,12", FluidUnitFormat.number(d("0.125"), it, 2))
    assertEquals("0,14", FluidUnitFormat.number(d("0.135"), it, 2))
  }

  @Test fun `le unità nelle due lingue, come le FormatStyle abbreviate`() {
    assertEquals("12,5${nbsp}km", FluidUnitFormat.format(FluidQuantity(d("12.5"), FluidUnits.Kilometer), it))
    assertEquals("12.5${nbsp}km", FluidUnitFormat.format(FluidQuantity(d("12.5"), FluidUnits.Kilometer), en))
    assertEquals("21${nbsp}°C", FluidUnitFormat.format(FluidQuantity(d("21"), FluidUnits.Celsius), it))
    assertEquals("21°C", FluidUnitFormat.format(FluidQuantity(d("21"), FluidUnits.Celsius), en))
    assertEquals("12,5%", FluidUnitFormat.format(FluidQuantity(d("12.5"), FluidUnits.Percent), it))
    assertEquals("12.5%", FluidUnitFormat.format(FluidQuantity(d("12.5"), FluidUnits.Percent), en))
    assertEquals("12${nbsp}%", FluidUnitFormat.format(FluidQuantity(d("12"), FluidUnits.Percent), Locale.GERMANY))
  }

  @Test fun `la valuta sta dove la mette la lingua`() {
    assertEquals("1.249,90${nbsp}€", FluidUnitFormat.format(FluidQuantity(d("1249.9"), FluidUnits.Euro), it))
    assertEquals("€1,249.90", FluidUnitFormat.format(FluidQuantity(d("1249.9"), FluidUnits.Euro), en))
    assertEquals("-€3.00", FluidUnitFormat.format(FluidQuantity(d("-3"), FluidUnits.Euro), en))
    assertTrue(FluidUnitFormat.symbolBefore(FluidUnits.Euro, en))
    assertFalse(FluidUnitFormat.symbolBefore(FluidUnits.Euro, it))
  }

  @Test fun `per TalkBack le parole dell'unità`() {
    assertEquals("12,5 chilometri", FluidUnitFormat.spoken(FluidQuantity(d("12.5"), FluidUnits.Kilometer), it))
    assertEquals("1 metro", FluidUnitFormat.spoken(FluidQuantity(d("1"), FluidUnits.Meter), it))
    assertEquals("21 degrees Celsius", FluidUnitFormat.spoken(FluidQuantity(d("21"), FluidUnits.Celsius), en))
    assertEquals("chilometro", FluidUnitFormat.name(FluidUnits.Kilometer, it))
    assertEquals("miles", FluidUnitFormat.name(FluidUnits.Mile, en, plural = true))
    assertNull(FluidUnitFormat.name(FluidUnits.Mile, Locale.GERMANY))
  }

  // --- Leggere ---------------------------------------------------------------------------------------------------

  @Test fun `la lettura nella lingua`() {
    num("1234.5", FluidUnitFormat.parse("1.234,5", it))
    num("1234.5", FluidUnitFormat.parse("1,234.5", en))
    num("1.25", FluidUnitFormat.parse("1,25", it))
    num("1.25", FluidUnitFormat.parse("1.25", en))
  }

  @Test fun `l'incolla da un'altra app si legge in più formati`() {
    // L'altra convenzione, con entrambi i separatori: vince l'ultimo come decimale.
    num("1234.56", FluidUnitFormat.parse("1,234.56", it))
    num("1234.56", FluidUnitFormat.parse("1.234,56", en))
    // Un separatore solo, non quello della lingua: con tre cifre dopo sono migliaia, se no decimale.
    num("1500", FluidUnitFormat.parse("1.500", it))
    num("1.5", FluidUnitFormat.parse("1.5", it))
    num("1500", FluidUnitFormat.parse("1,500", en))
    num("1.5", FluidUnitFormat.parse("1,5", en))
    // Spazi, apostrofi, il meno tipografico, il simbolo attaccato.
    num("1234567.8", FluidUnitFormat.parse("1 234 567,8", it))
    num("1234.5", FluidUnitFormat.parse("1'234.5", en))
    num("-3.5", FluidUnitFormat.parse("−3,5", it))
    num("12.5", FluidUnitFormat.parse("12,5 km", it))
    num("12.5", FluidUnitFormat.parse("€ 12,50", it))
    num("1234567", FluidUnitFormat.parse("1.234.567", it))
  }

  @Test fun `quello che non è un numero non si legge`() {
    assertNull(FluidUnitFormat.parse("", it))
    assertNull(FluidUnitFormat.parse("abc", it))
    assertNull(FluidUnitFormat.parse("12 km 5", it))
    assertNull(FluidUnitFormat.parse("1,2,3.4.5", it))
    assertNull(FluidUnitFormat.parse("-", it))
  }

  @Test fun `la quantità riconosce l'unità attaccata`() {
    val units = FluidUnits.of(FluidUnitDimension.Length)
    assertEquals(FluidQuantity(d("12.5"), FluidUnits.Mile), FluidUnitFormat.parseQuantity("12,5 mi", it, units, FluidUnits.Kilometer))
    assertEquals(FluidQuantity(d("3"), FluidUnits.Kilometer), FluidUnitFormat.parseQuantity("3", it, units, FluidUnits.Kilometer))
    assertEquals(FluidQuantity(d("40"), FluidUnits.Millimeter), FluidUnitFormat.parseQuantity("40mm", it, units, FluidUnits.Kilometer))
    assertNull(FluidUnitFormat.parseQuantity("12 kg", it, units, FluidUnits.Kilometer))
  }
}
