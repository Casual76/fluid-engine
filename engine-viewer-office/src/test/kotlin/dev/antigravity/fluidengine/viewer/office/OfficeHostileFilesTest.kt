package dev.antigravity.fluidengine.viewer.office

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R2 · I file Office malformati o costruiti apposta: un formato numero di migliaia di caratteri in `styles.xml`, e una
 * tabella dei colori RTF con valori fuori da 0..255.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfficeHostileFilesTest {
  @Test
  fun aNumberFormatOfThousandsOfCharactersIsNotApplied() {
    val code = "0." + "0".repeat(5_000)
    val text = formatNumber("3.25", code, false, Locale.ITALY)
    assertTrue("${text.length} caratteri", text.length <= 64)
    assertEquals("3,25", text)
    // Una data lunghissima, lo stesso.
    val date = formatNumber("45000", "yyyy-".repeat(2_000), false, Locale.ITALY)
    assertTrue("${date.length} caratteri", date.length <= 64)
    // I formati veri non cambiano.
    assertEquals("1.234,50", formatNumber("1234.5", "#,##0.00", false, Locale.ITALY))
    assertEquals("12%", formatNumber("0.12", "0%", false, Locale.ITALY))
  }

  @Test
  fun rtfColorsOutOfRangeAreClampedLikeWordDoes() {
    val rtf = "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Arial;}}{\\colortbl;\\red999\\green-4\\blue300;\\red10\\green20\\blue30;}" +
      "\\f0\\cf1 Viola\\cf2  Blu\\par}"
    val runs = readRtf(rtf.toByteArray(Charsets.ISO_8859_1), OfficeInfo())
      .flatMap { it.blocks }.filterIsInstance<Paragraph>().flatMap { it.runs }
    val violet = runs.first { "Viola" in it.text }.ink.color
    assertEquals(Integer.toHexString(android.graphics.Color.rgb(255, 0, 255)), Integer.toHexString(violet))
    assertEquals(android.graphics.Color.rgb(10, 20, 30), runs.first { "Blu" in it.text }.ink.color)
  }

  @Test
  fun numberFormatsArePreparedOncePerSheetNotAtEveryCell() {
    val code = "#,##0.00 \"€\";[Red]-#,##0.00 \"€\";\"zero\""
    val prepared = HashMap<String, Any>()
    // Lo stesso testo con e senza i formati preparati, per le tre sezioni.
    for (raw in listOf("1234.5", "-7.25", "0")) {
      assertEquals(formatNumber(raw, code, false, Locale.ITALY), formatNumber(raw, code, false, Locale.ITALY, prepared))
    }
    assertEquals(3, prepared.size)
    val probe = AllocationProbe()
    repeat(200) { formatNumber("1234.5", code, false, Locale.ITALY, prepared) }
    repeat(200) { formatNumber("1234.5", code, false, Locale.ITALY) }
    val a = probe.now()
    repeat(2_000) { formatNumber("1234.5", code, false, Locale.ITALY) }
    val everyCell = probe.now() - a
    val b = probe.now()
    repeat(2_000) { formatNumber("1234.5", code, false, Locale.ITALY, prepared) }
    val once = probe.now() - b
    assertTrue("preparati $once byte, a ogni cella $everyCell", once * 3 < everyCell)
  }

  /** I byte allocati dal filo, dal contatore di HotSpot (java.lang.management non è nella classpath delle prove Android). */
  private class AllocationProbe {
    private val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
    private val method = Class.forName("com.sun.management.ThreadMXBean")
      .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
    private val thread = Thread.currentThread().id

    fun now(): Long = method.invoke(bean, thread) as Long
  }
}
