package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.exp

/**
 * Il filtro che fa di una serie di letture un lato che non sfarfalla (F2.1, piano 3.9), e la statistica che gli
 * arriva.
 *
 * Il piano chiede quattro prove: gradino, rampa, scacchiera, rumore attorno alla soglia. In più: la prima lettura che
 * decide subito, la permanenza che trattiene un secondo giro, la scadenza a cui il filtro va richiamato quando nessuna
 * lettura nuova arriva (la sonda legge solo quando qualcosa cambia: dopo l'ultima lettura lo sfondo è fermo, ma la
 * media deve ancora arrivarci).
 *
 * Le statistiche stanno qui perché sono ciò che il filtro mangia, e perché il perimetro di F2.1 ha due file di test:
 * riduzione e formula sono le stesse di `tools/glassbench/probe_stats.py` (`box_reduce`, `stats`), e un disegno fisso
 * con numeri esatti le inchioda da entrambe le parti (`probe_stats.py --selftest`).
 */
class GlassAdaptiveFilterTest {

  private fun filter(initialDark: Boolean = false) = GlassAdaptiveFilter(
    initialDark = initialDark,
    timeConstantMillis = 200f,
    darkBelow = 0.45f,
    lightAbove = 0.62f,
    minDwellMillis = 400L,
  )

  /** Fa arrivare [samples] (istante, luminanza) come la sonda, richiamando il filtro alle sue scadenze; dà i giri. */
  private fun replay(filter: GlassAdaptiveFilter, samples: List<Pair<Long, Float>>, until: Long): List<Pair<Long, Boolean>> {
    val flips = mutableListOf<Pair<Long, Boolean>>()
    var index = 0
    var now = 0L
    while (now <= until) {
      val nextSample = samples.getOrNull(index)?.first ?: Long.MAX_VALUE
      val deadline = filter.nextDeadlineMillis()
      now = minOf(nextSample, deadline)
      if (now == Long.MAX_VALUE || now > until) break
      if (now == nextSample) {
        if (filter.sample(now, samples[index].second)) flips += now to filter.dark
        index++
      } else if (filter.advance(now)) {
        flips += now to filter.dark
      } else if (now == deadline) {
        // Una scadenza che non ha prodotto un giro deve spostarsi avanti, o il ciclo non finirebbe.
        assertNotEquals("la scadenza $deadline non si sposta", deadline, filter.nextDeadlineMillis())
      }
    }
    return flips
  }

  // --- Prima della prima lettura, e la prima ----------------------------------------------------------------

  @Test
  fun `senza letture resta il lato del tema, senza scadenze`() {
    val light = filter(initialDark = false)
    assertFalse(light.dark)
    assertFalse(light.measured)
    assertEquals(Long.MAX_VALUE, light.nextDeadlineMillis())
    assertFalse(light.advance(5_000L))
    assertTrue(filter(initialDark = true).dark)
    assertEquals(GlassAdaptiveTarget.unmeasured(dark = false), light.target(0L, null))
  }

  @Test
  fun `la prima lettura decide subito, con le due soglie a partire dal tema`() {
    val overBlack = filter(initialDark = false)
    assertTrue("su nero un vetro nasce scuro, senza mostrarsi chiaro prima", overBlack.sample(0L, 0.05f))
    assertTrue(overBlack.dark)
    val overMiddle = filter(initialDark = false)
    assertFalse("a metà fra le soglie resta il lato del tema", overMiddle.sample(0L, 0.55f))
    assertFalse(overMiddle.dark)
    val darkOverMiddle = filter(initialDark = true)
    darkOverMiddle.sample(0L, 0.55f)
    assertTrue(darkOverMiddle.dark)
  }

  @Test
  fun `una lettura non finita si scarta`() {
    val filter = filter()
    assertFalse(filter.sample(0L, Float.NaN))
    assertFalse(filter.measured)
    filter.sample(0L, 1f)
    assertFalse(filter.sample(500L, Float.NaN))
    assertEquals(1f, filter.smoothedAt(600L), 1e-6f)
  }

  // --- Le quattro prove del piano ---------------------------------------------------------------------------

  @Test
  fun `gradino da bianco a nero, una lettura sola, il giro arriva alla scadenza`() {
    val filter = filter()
    filter.sample(0L, 1f)
    // Lo sfondo diventa nero; la sonda lo legge una volta e poi non legge più (niente cambia).
    assertFalse("la media non salta: la lettura nuova vale da adesso", filter.sample(1_000L, 0f))
    assertFalse(filter.dark)
    val deadline = filter.nextDeadlineMillis()
    // La media passa 0,45 dopo τ·ln(1/0,45) = 160 ms.
    assertTrue("scadenza a $deadline", deadline in 1_155L..1_165L)
    assertFalse("prima della soglia niente giro", filter.advance(1_100L))
    assertTrue(filter.advance(deadline))
    assertTrue(filter.dark)
    assertEquals("girato, non ci sono altre scadenze", Long.MAX_VALUE, filter.nextDeadlineMillis())
  }

  @Test
  fun `gradino con letture fitte, un giro solo, nello stesso istante della scadenza`() {
    val samples = buildList {
      add(0L to 1f)
      var t = 1_000L
      while (t <= 3_000L) {
        add(t to 0f)
        t += 120L
      }
    }
    val flips = replay(filter(), samples, until = 4_000L)
    assertEquals("un giro: $flips", 1, flips.size)
    // Letture a 1 000, 1 120 (media 0,549), 1 240 (0,202): la soglia cade fra le due, e la scadenza la prende a 1 161.
    assertTrue("giro a ${flips.single().first}", flips.single().first in 1_155L..1_165L)
    assertTrue(flips.single().second)
  }

  @Test
  fun `rampa, un giro all'andata e uno al ritorno, alle soglie giuste`() {
    val down = (0..40).map { i -> (i * 100L) to (1f - i / 40f) }
    val up = (1..40).map { i -> (4_000L + i * 100L) to (i / 40f) }
    val filter = filter()
    val flips = replay(filter, down + up, until = 9_000L)
    assertEquals("due giri: $flips", 2, flips.size)
    val (toDark, toLight) = flips
    assertTrue(toDark.second)
    assertFalse(toLight.second)
    // All'andata la rampa passa 0,45 a 2 200 ms; la media insegue con un ritardo di circa τ.
    assertTrue("giro allo scuro a ${toDark.first}", toDark.first in 2_250L..2_550L)
    // Al ritorno la rampa passa 0,62 a 6 480 ms.
    assertTrue("giro al chiaro a ${toLight.first}", toLight.first in 6_550L..6_850L)
  }

  @Test
  fun `scacchiera sotto il vetro, a meta' fra le soglie non gira mai`() {
    // La scacchiera del banco: luminanza media 0,505, che trema di ±0,003 quando scorre (probe_stats, 16×16 engine).
    val samples = (0 until 100).map { i -> (i * 120L) to (0.505f + if (i % 2 == 0) 0.003f else -0.003f) }
    assertEquals(emptyList<Pair<Long, Boolean>>(), replay(filter(initialDark = false), samples, until = 13_000L))
    assertEquals(emptyList<Pair<Long, Boolean>>(), replay(filter(initialDark = true), samples, until = 13_000L))
  }

  @Test
  fun `rumore attorno alla soglia, al piu' un giro, mai avanti e indietro`() {
    for (center in listOf(0.45f, 0.62f)) {
      for (amplitude in listOf(0.02f, 0.05f, 0.08f)) {
        val noise = Lcg(seed = 7L)
        val samples = (0 until 200).map { i -> (i * 120L) to (center + amplitude * noise.nextSigned()) }
        val start = if (center < 0.5f) 0.7f else 0.3f
        val flips = replay(filter(initialDark = center >= 0.5f), listOf(-1_000L to start) + samples, until = 25_000L)
        assertTrue(
          "rumore ±$amplitude attorno a $center: ${flips.size} giri ($flips)",
          flips.size <= 1,
        )
      }
    }
  }

  @Test
  fun `rumore largo che attraversa tutte e due le soglie, i giri rispettano la permanenza`() {
    val noise = Lcg(seed = 11L)
    val samples = (0 until 300).map { i -> (i * 120L) to (0.535f + 0.35f * noise.nextSigned()) }
    val flips = replay(filter(), samples, until = 40_000L)
    for (i in 1 until flips.size) {
      assertTrue("due giri a ${flips[i - 1].first} e ${flips[i].first}", flips[i].first - flips[i - 1].first >= 400L)
    }
  }

  // --- Permanenza e scadenze --------------------------------------------------------------------------------

  @Test
  fun `la permanenza trattiene il secondo giro fino alla sua fine`() {
    val filter = filter()
    filter.sample(0L, 1f)
    filter.sample(1_000L, 0f)
    val firstFlip = filter.nextDeadlineMillis()
    assertTrue(filter.advance(firstFlip))
    // Subito di nuovo bianco: la media lo raggiungerebbe in un centinaio di millisecondi.
    assertFalse(filter.sample(firstFlip + 40L, 1f))
    val second = filter.nextDeadlineMillis()
    assertEquals("il secondo giro aspetta la fine della permanenza", firstFlip + 400L, second)
    assertFalse(filter.advance(second - 1L))
    assertTrue(filter.advance(second))
    assertFalse(filter.dark)
    assertEquals(2, filter.flips)
  }

  @Test
  fun `la media a tempo continuo insegue l'ultima lettura`() {
    val filter = filter()
    filter.sample(0L, 1f)
    filter.sample(1_000L, 0f)
    assertEquals(1f, filter.smoothedAt(1_000L), 1e-6f)
    assertEquals(exp(-1f), filter.smoothedAt(1_200L), 1e-5f)
    assertEquals(exp(-5f), filter.smoothedAt(2_000L), 1e-5f)
    // Una lettura che arriva dopo un secondo di silenzio non fa saltare la media: prima corre col valore di prima.
    filter.sample(3_000L, 1f)
    assertEquals(0f, filter.smoothedAt(3_000L), 1e-3f)
  }

  @Test
  fun `il bersaglio porta l'ultima lettura e il lato`() {
    val filter = filter()
    val stats = GlassBackdropStats(
      luminance = 0.2f, variance = 0.01f, gradientEnergy = 0.0081f,
      red = 0.3f, green = 0.2f, blue = 0.1f, coverage = 1f,
    )
    filter.sample(0L, stats.luminance)
    val target = filter.target(0L, stats)
    assertTrue(target.measured)
    assertTrue(target.dark)
    assertEquals(0.2f, target.luminance, 0f)
    assertEquals(stats.texture, target.texture, 0f)
    assertEquals(Color(0.3f, 0.2f, 0.1f), target.color)
  }

  @Test
  fun `lo stato pubblicato segue il filtro, anche alla scadenza senza letture`() {
    val state = GlassProbeState(initialDark = false, filter = filter())
    assertFalse(state.target.measured)
    state.publish(0L, flat(1f))
    assertFalse(state.target.dark)
    state.publish(1_000L, flat(0f))
    assertFalse("la media non è ancora arrivata", state.target.dark)
    state.advance(state.nextDeadlineMillis())
    assertTrue(state.target.dark)
    assertEquals(2, state.readings)
    assertEquals(0f, state.stats?.luminance)
  }

  // --- La statistica ----------------------------------------------------------------------------------------

  @Test
  fun `righe di testo finte, i numeri esatti del banco`() {
    // 64×64, righe nere dove y % 8 < 3, bianche altrove: tre righe d'inchiostro ogni otto, come una riga di testo.
    // Ridotte a 16×16 (blocchi 4×4) le righe di celle alternano 0,25 e 1. Gli stessi numeri li dà
    // `probe_stats.py --selftest` con `box_reduce` e `stats`.
    val stats = glassProbeStats(stripes())
    assertEquals(0.625f, stats.luminance, 1e-5f)
    assertEquals(0.140625f, stats.variance, 1e-5f)
    assertEquals(0.28125f, stats.gradientEnergy, 1e-5f)
    assertEquals(0.375f, stats.deviation, 1e-5f)
    assertEquals(1f, stats.coverage, 1e-6f)
    assertEquals(0.625f, stats.red, 1e-5f)
    assertEquals(1f, stats.texture, 0f)
  }

  @Test
  fun `a 8x8 le stesse righe si cancellano, e' il rischio di una miniatura che media bene`() {
    // Blocchi 8×8: ogni cella prende tre righe nere e cinque bianche, tutte uguali. Niente varianza, niente gradiente.
    val stats = glassProbeStats(stripes(), readSide = 64, statsSide = 8)
    assertEquals(0.625f, stats.luminance, 1e-5f)
    assertEquals(0f, stats.variance, 1e-7f)
    assertEquals(0f, stats.gradientEnergy, 1e-7f)
    assertEquals(0f, stats.texture, 0f)
  }

  @Test
  fun `un fondo pieno, luminanza e colore, nessuna trama`() {
    val pixels = ByteArray(64 * 64 * 4)
    for (i in 0 until 64 * 64) {
      pixels[i * 4] = 200.toByte()
      pixels[i * 4 + 1] = 100.toByte()
      pixels[i * 4 + 2] = 50.toByte()
      pixels[i * 4 + 3] = 255.toByte()
    }
    val stats = glassProbeStats(pixels)
    assertEquals((0.2126f * 200 + 0.7152f * 100 + 0.0722f * 50) / 255f, stats.luminance, 1e-5f)
    assertEquals(0f, stats.variance, 1e-9f)
    assertEquals(0f, stats.gradientEnergy, 1e-9f)
    assertEquals(200f / 255f, stats.red, 1e-5f)
    assertEquals(100f / 255f, stats.green, 1e-5f)
    assertEquals(50f / 255f, stats.blue, 1e-5f)
    assertEquals(0f, stats.texture, 0f)
  }

  @Test
  fun `una miniatura vuota ha copertura zero`() {
    val stats = glassProbeStats(ByteArray(64 * 64 * 4))
    assertEquals(0f, stats.coverage, 0f)
    assertTrue("sotto la soglia: non si consegna", stats.coverage < GlassProbeMinCoverage)
  }

  @Test
  fun `il riduttore legge una riga da un punto qualsiasi del buffer, come il lettore`() {
    // Il lettore copia ogni riga della casella da un'immagine larga otto caselle e con un margine di riga: il
    // riduttore riceve la riga da un offset, non da zero.
    val pixelStride = 4
    val padded = ByteArray(80 * pixelStride)
    val reducer = GlassProbeStatsReducer()
    val packed = stripes()
    reducer.begin()
    for (y in 0 until 64) {
      System.arraycopy(packed, y * 64 * 4, padded, 8, 64 * 4)
      reducer.addRow(y, padded, 8, pixelStride)
    }
    assertEquals(glassProbeStats(packed), reducer.finish())
  }

  @Test
  fun `la trama sale morbida fra le due soglie`() {
    assertEquals(0f, glassBackdropTexture(0f), 0f)
    assertEquals(0f, glassBackdropTexture(GlassProbeTextureFlat), 0f)
    assertEquals(0.5f, glassBackdropTexture((GlassProbeTextureFlat + GlassProbeTextureBusy) / 2f), 1e-6f)
    assertEquals(1f, glassBackdropTexture(GlassProbeTextureBusy), 0f)
    assertEquals(0f, glassBackdropTexture(Float.NaN), 0f)
  }

  private fun stripes(): ByteArray {
    val out = ByteArray(64 * 64 * 4)
    for (y in 0 until 64) {
      val value = if (y % 8 < 3) 0 else 255
      for (x in 0 until 64) {
        val at = (y * 64 + x) * 4
        out[at] = value.toByte()
        out[at + 1] = value.toByte()
        out[at + 2] = value.toByte()
        out[at + 3] = 255.toByte()
      }
    }
    return out
  }

  private fun flat(luminance: Float) = GlassBackdropStats(
    luminance = luminance, variance = 0f, gradientEnergy = 0f,
    red = luminance, green = luminance, blue = luminance, coverage = 1f,
  )

  /** Numeri pseudo-casuali ripetibili (lo stesso seme, la stessa serie su ogni macchina). */
  private class Lcg(seed: Long) {
    private var state = seed

    /** Fra -1 e 1. */
    fun nextSigned(): Float {
      state = (state * 6364136223846793005L + 1442695040888963407L)
      val bits = (state ushr 40).toInt() and 0xFFFFFF
      return bits / 0x7FFFFF.toFloat() - 1f
    }
  }
}
