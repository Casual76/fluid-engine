
package dev.antigravity.fluidengine.sample.novita

import dev.antigravity.fluidengine.foundation.FluidByteStyle
import dev.antigravity.fluidengine.foundation.FluidFormat
import dev.antigravity.fluidengine.foundation.FluidFormatWidth
import dev.antigravity.fluidengine.net.EngineNetworkState
import dev.antigravity.fluidengine.ui.fluid.FluidThumbHash
import dev.antigravity.fluidengine.ui.fluid.fluidFormatFileSize
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F8.1 · `FluidFormat` in italiano e in inglese, con le ICU vere di Android 15 (Robolectric). Le frasi sono quelle di
 * CLDR, le stesse tabelle delle `FormatStyle` di Apple.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class F8p1FormatTest {
  private val it = Locale.ITALY
  private val en = Locale.US
  private val rome = ZoneId.of("Europe/Rome")
  private val now: Instant = ZonedDateTime.of(2026, 11, 23, 15, 30, 0, 0, rome).toInstant()

  private fun ago(seconds: Long): Instant = now.minusSeconds(seconds)

  @Test
  fun `tempo relativo, corto e intero`() {
    assertEquals("ora", FluidFormat.relative(ago(20), now, it))
    assertEquals("now", FluidFormat.relative(ago(20), now, en))
    assertEquals("12 min fa", FluidFormat.relative(ago(12 * 60 + 30), now, it))
    assertEquals("12 minuti fa", FluidFormat.relative(ago(12 * 60 + 30), now, it, FluidFormatWidth.Wide))
    assertEquals("12 minutes ago", FluidFormat.relative(ago(12 * 60 + 30), now, en, FluidFormatWidth.Wide))
    // Si tronca, come Apple: 1 h 59 min è un'ora.
    assertEquals("1 ora fa", FluidFormat.relative(ago(119 * 60), now, it, FluidFormatWidth.Wide))
    assertEquals("tra 2 giorni", FluidFormat.relative(now.plusSeconds(2 * 86_400 + 600), now, it, FluidFormatWidth.Wide))
    assertEquals("in 2 days", FluidFormat.relative(now.plusSeconds(2 * 86_400 + 600), now, en, FluidFormatWidth.Wide))
    assertEquals("3 settimane fa", FluidFormat.relative(ago(22 * 86_400), now, it, FluidFormatWidth.Wide))
    assertEquals("1 mese fa", FluidFormat.relative(ago(31 * 86_400), now, it, FluidFormatWidth.Wide))
  }

  @Test
  fun `il testo vivo sa quando cambia`() {
    // A 12 min 30 s fa cambia fra 30 s (diventa 13 min).
    assertEquals(30_000L, FluidFormat.relativeNextChangeMillis(ago(12 * 60 + 30), now))
    // Sotto il minuto, al minuto.
    assertEquals(40_000L, FluidFormat.relativeNextChangeMillis(ago(20), now))
    // A 28 giorni («4 settimane») il prossimo cambio è a 30, il mese: non a 35.
    assertEquals(2 * 86_400_000L, FluidFormat.relativeNextChangeMillis(ago(28 * 86_400), now))
    // Nel futuro scende: «tra 5 min» a 5 min 30 s diventa «tra 5 min» … fino a sotto i 5.
    assertEquals(30_001L, FluidFormat.relativeNextChangeMillis(now.plusSeconds(5 * 60 + 30), now))
  }

  @Test
  fun `giorno e ora, e la data di una riga`() {
    val yesterday = ZonedDateTime.of(2026, 11, 22, 9, 4, 0, 0, rome).toInstant()
    val itText = FluidFormat.dayAndTime(yesterday, now, it, rome)
    assertTrue(itText, itText.startsWith("ieri") && itText.contains("09:04"))
    val enText = FluidFormat.dayAndTime(yesterday, now, en, rome)
    assertTrue(enText, enText.startsWith("yesterday") && enText.contains("9:04"))
    assertEquals("ieri", FluidFormat.dayAndTime(yesterday, now, it, rome, includeTime = false))
    // Oggi l'ora, nella settimana il giorno, nell'anno giorno e mese.
    assertEquals("15:00", FluidFormat.listDate(ago(30 * 60), now, it, rome))
    assertEquals("giovedì", FluidFormat.listDate(ZonedDateTime.of(2026, 11, 19, 12, 0, 0, 0, rome).toInstant(), now, it, rome))
    assertEquals("12 mar", FluidFormat.listDate(ZonedDateTime.of(2026, 3, 12, 12, 0, 0, 0, rome).toInstant(), now, it, rome))
    assertEquals("Thursday", FluidFormat.listDate(ZonedDateTime.of(2026, 11, 19, 12, 0, 0, 0, rome).toInstant(), now, en, rome))
  }

  @Test
  fun `pesi dei file e della memoria`() {
    assertEquals("2,4 MB", FluidFormat.bytes(2_420_000, FluidByteStyle.File, it))
    assertEquals("2.4 MB", FluidFormat.bytes(2_420_000, FluidByteStyle.File, en))
    assertEquals("532 B", FluidFormat.bytes(532, FluidByteStyle.File, it))
    // Mai «1000 KB»: l'arrotondamento passa all'unità dopo.
    assertEquals("1 MB", FluidFormat.bytes(999_960, FluidByteStyle.File, it))
    assertEquals("3 GB", FluidFormat.bytes(3L * 1024 * 1024 * 1024, FluidByteStyle.Memory, it))
    // `fluidFormatFileSize` dell'interfaccia è la stessa regola, col testo di prima.
    assertEquals(FluidFormat.bytes(123_456_789, FluidByteStyle.File, it), fluidFormatFileSize(123_456_789, it))
  }

  @Test
  fun `elenchi di nomi, con il resto`() {
    assertEquals("Anna, Luca e Marco", FluidFormat.list(listOf("Anna", "Luca", "Marco"), it))
    assertEquals("Anna, Luca, and Marco", FluidFormat.list(listOf("Anna", "Luca", "Marco"), en))
    val five = listOf("Anna", "Luca", "Marco", "Sara", "Piero")
    assertEquals("Anna, Luca e altri 3", FluidFormat.list(five, it, limit = 2))
    assertEquals("Anna, Luca, and 3 others", FluidFormat.list(five, en, limit = 2))
    assertEquals("Anna e un altro", FluidFormat.list(five.take(2), it, limit = 1))
    assertEquals("", FluidFormat.list(emptyList(), it))
  }

  @Test
  fun `durate in parole e da cronometro`() {
    val short = FluidFormat.duration(Duration.ofMinutes(85), it)
    assertTrue(short, short.startsWith("1 h") && short.contains("25 min"))
    val wide = FluidFormat.duration(Duration.ofMinutes(85), it, FluidFormatWidth.Wide)
    assertTrue(wide, wide.contains("1 ora") && wide.contains("25 minuti"))
    val wideEn = FluidFormat.duration(Duration.ofMinutes(85), en, FluidFormatWidth.Wide)
    assertTrue(wideEn, wideEn.contains("1 hour") && wideEn.contains("25 minutes"))
    // Due unità di serie: i secondi si troncano.
    assertFalse(FluidFormat.duration(Duration.ofSeconds(85 * 60 + 40), it).contains("40"))
    assertEquals("1:02:07", FluidFormat.timer(Duration.ofSeconds(3_727), it))
    assertEquals("1:07", FluidFormat.timer(Duration.ofSeconds(67), it))
    assertEquals("0:07", FluidFormat.timer(Duration.ofSeconds(7), en))
    // Per eccesso: l'ultimo istante dice ancora «0:01».
    assertEquals("0:01", FluidFormat.timer(Duration.ofMillis(200), it))
    assertEquals("0:00", FluidFormat.timer(Duration.ZERO, it))
  }

  @Test
  fun `numeri brevi e percentuali`() {
    val itCompact = FluidFormat.compact(1_234_567.0, it)
    assertTrue(itCompact, itCompact.contains("Mln") && itCompact.contains("1,2"))
    assertEquals("1.2M", FluidFormat.compact(1_234_567.0, en))
    // In italiano le migliaia non si abbreviano (CLDR, come iOS).
    assertFalse(FluidFormat.compact(1_200.0, it).contains("k", ignoreCase = true))
    assertEquals("43%", FluidFormat.percent(0.426, it))
    assertEquals("42,5%", FluidFormat.percent(0.425, it, fractionDigits = 1))
    assertEquals("1.234,5", FluidFormat.number(1_234.5, it))
    assertEquals("1,234.50", FluidFormat.number(1_234.5, en, fractionDigits = 2))
  }

  // ---- ThumbHash ----------------------------------------------------------------------------------

  @Test
  fun `un'impronta ThumbHash torna coi colori e le proporzioni dell'immagine`() {
    val w = 40
    val h = 30
    val rgba = ByteArray(w * h * 4)
    var r = 0.0
    var g = 0.0
    var b = 0.0
    for (y in 0 until h) for (x in 0 until w) {
      val i = (y * w + x) * 4
      // Un tramonto: dal rosso in alto all'azzurro in basso.
      val red = 230 - y * 4
      val green = 120 + x
      val blue = 60 + y * 5
      rgba[i] = red.toByte(); rgba[i + 1] = green.toByte(); rgba[i + 2] = blue.toByte(); rgba[i + 3] = 255.toByte()
      r += red; g += green; b += blue
    }
    val hash = FluidThumbHash.encode(w, h, rgba)
    assertTrue("poche decine di byte: ${hash.size}", hash.size in 5..32)
    assertEquals("le proporzioni", w.toFloat() / h, FluidThumbHash.aspectRatio(hash), 0.2f)
    val average = FluidThumbHash.averageColor(hash)
    val n = (w * h).toDouble()
    assertTrue("il rosso medio: ${average.red}", abs(average.red - r / n / 255) < 0.08)
    assertTrue("il verde medio: ${average.green}", abs(average.green - g / n / 255) < 0.08)
    assertTrue("il blu medio: ${average.blue}", abs(average.blue - b / n / 255) < 0.08)
    val decoded = FluidThumbHash.decode(hash)
    assertEquals(32, decoded.width)
    // In alto più rosso che in basso: la miniatura tiene la forma dell'immagine, non solo il colore.
    val topRed = decoded.rgba[0].toInt() and 255
    val bottomRed = decoded.rgba[(decoded.height - 1) * decoded.width * 4].toInt() and 255
    assertTrue("rosso in alto $topRed, in basso $bottomRed", topRed > bottomRed + 30)
    // La cache: la stessa impronta è la stessa immagine.
    assertTrue(FluidThumbHash.imageBitmap(hash) === FluidThumbHash.imageBitmap(hash.copyOf()))
  }

  // ---- La rete --------------------------------------------------------------------------------------

  @Test
  fun `una rete senza internet verificato non e' online`() {
    assertTrue(EngineNetworkState.of(hasInternet = true, validated = true, notMetered = true).online)
    assertFalse("Wi-Fi d'albergo", EngineNetworkState.of(hasInternet = true, validated = false, notMetered = true).online)
    assertFalse(EngineNetworkState.Offline.online)
    assertTrue("dati mobili", EngineNetworkState.of(hasInternet = true, validated = true, notMetered = false).metered)
    assertFalse(EngineNetworkState.of(hasInternet = false, validated = true, notMetered = false).connected)
  }
}
