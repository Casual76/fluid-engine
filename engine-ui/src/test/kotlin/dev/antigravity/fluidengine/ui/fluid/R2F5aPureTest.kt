package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.theme.fluidContentColorsOf
import dev.antigravity.fluidengine.ui.theme.fluidContentRoles
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** L'aptica che non suona: le prove del moto non la ascoltano. */
private object SilentHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Off
  override fun play(event: FluidHapticEvent) = Unit
}

/**
 * R2-F5a · le correzioni pure di `engine-ui` della review R2 (`muse-f8-prime.md`): ThumbHash corti, il codice e i link
 * presi dagli appunti, l'arrivo del testo con «Riduci movimento», i separatori dei numeri che scorrono, la ruota e la
 * ghiera (con un orologio dei fotogrammi finto), il cursore a due maniglie al contrario, il quadrante con le liste non
 * allineate e le copertine vivissime.
 */
class R2F5aPureTest {
  private val defaultLocale = Locale.getDefault()

  @After fun restore() {
    Locale.setDefault(defaultLocale)
  }

  // --- 27. ThumbHash di 5 byte col bit alfa ------------------------------------------------------------------------

  @Test fun `un ThumbHash di 5 byte col bit alfa si rifiuta con un errore chiaro`() {
    // Il bit 23 dell'intestazione (il bit alto del terzo byte) dice «c'e' l'alfa»: allora i byte sono almeno 6.
    val hash = byteArrayOf(0x3F, 0x00, 0x80.toByte(), 0x00, 0x00)
    assertThrows(IllegalArgumentException::class.java) { FluidThumbHash.decode(hash) }
    assertThrows(IllegalArgumentException::class.java) { FluidThumbHash.averageColor(hash) }
    // Senza alfa 5 byte bastano ancora.
    val plain = byteArrayOf(0x3F, 0x00, 0x00, 0x00, 0x00)
    FluidThumbHash.decode(plain)
    FluidThumbHash.averageColor(plain)
  }

  // --- Minori del campo codice e dei link ---------------------------------------------------------------------------

  @Test fun `il codice in maiuscolo resta ASCII anche col telefono in turco`() {
    // Il revisore aveva torto: `uppercase()` di Kotlin non usa la lingua del telefono (e' `Locale.ROOT`), quindi
    // «i» resta «I» anche in turco. La prova lo fissa; verde anche sul codice di prima.
    Locale.setDefault(Locale.forLanguageTag("tr-TR"))
    assertEquals("I1I2I3", fluidCodeInput("", "i1i2i3", 6, digitsOnly = false))
    assertEquals("I1I2I3", fluidCodeFromText("i1i2i3", 6..6, digitsOnly = false, strict = true))
  }

  @Test fun `il numero delle modifiche in attesa resta in cifre latine dentro la frase italiana`() {
    Locale.setDefault(Locale.forLanguageTag("ar-EG"))
    assertEquals("Offline · 3 modifiche in attesa", FluidSyncStrings().offlinePending(3))
  }

  @Test fun `un numero piu' lungo del codice non si taglia, si rifiuta`() {
    // Un numero di telefono incollato non e' un codice: prima entravano le prime 6 cifre e onComplete partiva.
    assertNull(fluidCodeFromText("3331234567", 6..6, digitsOnly = true, strict = false))
    assertEquals("", fluidCodeInput("", "3331234567", 6, digitsOnly = true))
    // Un codice dentro una frase, o a pezzi, entra come prima; un pezzo corto incollato si aggiunge.
    assertEquals("482913", fluidCodeFromText("Il tuo codice è 482913, vale 10 min", 6..6, digitsOnly = true, strict = false))
    assertEquals("123456", fluidCodeFromText("12a34b56", 6..6, digitsOnly = true, strict = false))
    assertEquals("1234", fluidCodeInput("12", "1234", 6, digitsOnly = true))
    assertEquals("12345", fluidCodeInput("1234", "12345", 6, digitsOnly = true))
  }

  @Test fun `la parentesi di un link bilanciato resta, quella che chiude la frase no`() {
    assertEquals("https://it.wikipedia.org/wiki/X_(Y)", fluidLinkFromText("vedi https://it.wikipedia.org/wiki/X_(Y)."))
    assertEquals("https://it.wikipedia.org/wiki/X_(Y)", fluidLinkFromText("https://it.wikipedia.org/wiki/X_(Y)"))
    assertEquals("https://esempio.it/a", fluidLinkFromText("(vedi https://esempio.it/a)"))
    assertEquals("https://esempio.it/a", fluidLinkFromText("https://esempio.it/a)."))
  }

  // --- Minore: «Riduci movimento» acceso mentre il testo arriva -------------------------------------------------------

  @Test fun `col movimento spento a meta' arrivo il testo arrivato si chiude subito`() {
    val tracker = RevealTracker()
    tracker.update("Ciao a tutti", revealNanos = 500_000_000L, rewriteNanos = 500_000_000L, animateFirst = true)
    assertTrue(tracker.hasWork)
    // Lo stesso testo, con le durate a zero: «Riduci movimento» si e' acceso.
    tracker.update("Ciao a tutti", revealNanos = 0L, rewriteNanos = 0L, animateFirst = true)
    assertFalse("i token in attesa si chiudono", tracker.hasWork)
  }

  // --- Minore: i separatori di un numero che si allunga ---------------------------------------------------------------

  @Test fun `il separatore nuovo di 999 a 1000 entra sul posto, non scorre`() {
    val slots = FluidNumericRoll.plan("999", "1.000")
    val separator = slots.single { it.to == 1 }
    assertEquals(NumericSlotKind.Swap, separator.kind)
    // Le cifre continuano a scorrere: 99 → 100 fa entrare l'1.
    assertEquals(
      listOf(NumericSlotKind.Roll, NumericSlotKind.Roll, NumericSlotKind.Enter),
      FluidNumericRoll.plan("99", "100").map { it.kind },
    )
    assertEquals(NumericSlotKind.Swap, FluidNumericRoll.plan("1.000", "999").single { it.from == 1 }.kind)
  }

  // --- 31, 32 e minore: la ruota ----------------------------------------------------------------------------------

  private class Frames {
    val clock = BroadcastFrameClock()
    val dispatcher = StandardTestDispatcher()
    val scope = CoroutineScope(dispatcher + clock + Job())
    private var nanos = 0L

    fun run(count: Int) {
      dispatcher.scheduler.runCurrent()
      repeat(count) {
        nanos += 16_666_667L
        clock.sendFrame(nanos)
        dispatcher.scheduler.runCurrent()
      }
    }
  }

  private fun column(count: Int, selected: Int, cyclic: Boolean = false) =
    FluidWheelColumn(count = count, selected = selected, onSelectedChange = {}, label = { it.toString() }, cyclic = cyclic)

  @Test fun `una ruota nata con la scelta fuori dalle voci si posa sull'ultima`() {
    val frames = Frames()
    val c = column(count = 10, selected = 100)
    val driver = FluidWheelDriver(frames.scope, c)
    driver.update(c, rowPx = 64f, reducedMotion = false, haptics = SilentHaptics, enabled = true)
    driver.follow(c.selected)
    frames.run(120)
    assertEquals(9f, driver.position, 1e-3f)
    assertEquals(9, driver.centred)
  }

  @Test fun `una ruota che perde voci mentre sta in fondo torna dentro`() {
    val frames = Frames()
    var c = column(count = 50, selected = 40)
    val driver = FluidWheelDriver(frames.scope, c)
    driver.update(c, rowPx = 64f, reducedMotion = false, haptics = SilentHaptics, enabled = true)
    driver.follow(40)
    frames.run(10)
    c = column(count = 10, selected = 40)
    driver.update(c, rowPx = 64f, reducedMotion = false, haptics = SilentHaptics, enabled = true)
    driver.follow(40)
    frames.run(120)
    assertEquals(9f, driver.position, 1e-3f)
  }

  @Test fun `il secondo cambio da fuori mentre la ruota corre verso il primo non si perde`() {
    val frames = Frames()
    var c = column(count = 20, selected = 0)
    val driver = FluidWheelDriver(frames.scope, c)
    driver.update(c, rowPx = 64f, reducedMotion = false, haptics = SilentHaptics, enabled = true)
    driver.follow(0)
    c = column(count = 20, selected = 3)
    driver.update(c, rowPx = 64f, reducedMotion = false, haptics = SilentHaptics, enabled = true)
    driver.follow(3)
    frames.run(3)
    assertTrue("la molla verso 3 corre", driver.moving)
    // A→B→C: arriva 6 mentre la molla va ancora verso 3.
    c = column(count = 20, selected = 6)
    driver.update(c, rowPx = 64f, reducedMotion = false, haptics = SilentHaptics, enabled = true)
    driver.follow(6)
    frames.run(240)
    assertFalse(driver.moving)
    assertEquals(6, driver.centred)
    assertEquals(6f, driver.position, 1e-3f)
  }

  @Test fun `una ruota che gira all'infinito torna nel primo giro quando si e' allontanata`() {
    // Una ruota di 20 000 voci, sempre avanti di 9 999 per la strada corta: tre passi la portano oltre le 16 384 voci,
    // dove la posizione torna nel primo giro. Prima cresceva senza fine (un Float perde la frazione oltre 2^24).
    val frames = Frames()
    val n = 20_000
    var c = column(count = n, selected = 0, cyclic = true)
    val driver = FluidWheelDriver(frames.scope, c)
    driver.update(c, rowPx = 64f, reducedMotion = false, haptics = SilentHaptics, enabled = true)
    driver.follow(0)
    var expected = 0
    for (step in 1..3) {
      expected = (expected + 9_999) % n
      c = column(count = n, selected = expected, cyclic = true)
      driver.update(c, rowPx = 64f, reducedMotion = false, haptics = SilentHaptics, enabled = true)
      driver.follow(expected)
      frames.run(300)
      assertFalse(driver.moving)
      assertEquals(expected, driver.centred)
    }
    assertTrue("posizione ${driver.position} nel primo giro", driver.position >= 0f && driver.position < n)
    // Vicino allo zero la posizione resta continua: una ruota da 10 voci girata di 24 resta a 24.
    val small = Frames()
    var s = column(count = 10, selected = 0, cyclic = true)
    val wheel = FluidWheelDriver(small.scope, s)
    wheel.update(s, rowPx = 64f, reducedMotion = false, haptics = SilentHaptics, enabled = true)
    for (step in 1..6) {
      s = column(count = 10, selected = (step * 4) % 10, cyclic = true)
      wheel.update(s, rowPx = 64f, reducedMotion = false, haptics = SilentHaptics, enabled = true)
      wheel.follow(s.selected)
      small.run(240)
    }
    assertEquals(24f, wheel.position, 1e-3f)
  }

  // --- 32: la ghiera ------------------------------------------------------------------------------------------------

  @Test fun `il secondo cambio da fuori mentre la ghiera corre verso il primo non si perde`() {
    val frames = Frames()
    val scale = FluidRulerScale(-45f..45f, 1f, 10, 0f)
    var value = 0f
    val valueState: State<() -> Float> = mutableStateOf({ value })
    val change: State<(Float) -> Unit> = mutableStateOf({ v: Float -> value = v })
    val finished: State<(() -> Unit)?> = mutableStateOf(null)
    val driver = FluidRulerDriver(frames.scope, scale.stepsOf(0f))
    driver.update(scale, valueState, change, finished, 18f, false, SilentHaptics, true)
    value = 10f
    driver.follow(10f)
    frames.run(3)
    assertTrue("la molla verso 10 corre", driver.moving)
    value = 20f
    driver.follow(20f)
    frames.run(300)
    assertFalse(driver.moving)
    assertEquals(scale.stepsOf(20f), driver.position, 1e-3f)
  }

  @Test fun `la scritta di serie col passo di serie resta quella di prima`() {
    // Il passo fine (0,01) si prova in sample (R2F5aControlsUiTest), dalla ghiera vera.
    assertEquals("−12", fluidRulerDefaultText(-12f))
    assertEquals("2,5", fluidRulerDefaultText(2.5f))
    assertEquals("0", fluidRulerDefaultText(0f))
  }

  // --- 33. Il cursore a due maniglie con l'intervallo al contrario -----------------------------------------------------

  @Test fun `un intervallo al contrario non fa cadere i passi di TalkBack`() {
    val reversed = 10f..0f
    val step = fluidRangeAccessibilityStep(reversed)
    assertEquals(10f, fluidRangeStepTarget(10f, +1, emptyList(), reversed, step), 0f)
    assertEquals(10f, fluidRangeStepTarget(10f, -1, emptyList(), reversed, step), 0f)
    assertEquals(10f, fluidRangeStepTarget(10f, +1, listOf(2f, 5f), reversed, step), 0f)
    // La fine resta dove l'inizio la lascia: l'intervallo al contrario vale un punto, a 10.
    assertEquals(10f, fluidRangeClampEnd(5f, 10f, reversed, 0f), 0f)
    assertEquals(10f, fluidRangeClampStart(5f, 10f, reversed, 0f), 0f)
  }

  // --- 35. Il quadrante con le liste non allineate -------------------------------------------------------------------

  @Test fun `soglie e testi delle soglie non allineati non fanno cadere la frase`() {
    val thresholds = listOf(FluidGaugeThreshold(25f, "Limite"), FluidGaugeThreshold(50f, "Massimo"))
    val reading = fluidGaugeReading(27f, 0f..100f, thresholds)
    val spoken = fluidGaugeDescription("27 %", "0", "100 %", thresholds, listOf("25 %"), reading)
    assertTrue(spoken, spoken.contains("Limite 25 %"))
    assertTrue(spoken, spoken.contains("Massimo 50"))
    // Testi in piu' si ignorano.
    val more = fluidGaugeDescription("27 %", "0", "100 %", thresholds.take(1), listOf("25 %", "x", "y"), reading)
    assertFalse(more, more.contains("x"))
  }

  // --- 37. Le copertine vivissime -----------------------------------------------------------------------------------

  private fun solid(color: Color, w: Int = 96, h: Int = 96) = IntArray(w * h) { color.toArgb() }

  @Test fun `una copertina gialla o rossa pura e' colorata, non neutra`() {
    for (cover in listOf(Color(0xFFFFE135), Color(0xFFFF0000), Color(0xFFFFFF00), Color(0xFF00A0FF))) {
      val colors = fluidContentColorsOf(solid(cover), 96, 96)!!
      assertFalse("copertina ${Integer.toHexString(cover.toArgb())}: monocroma", colors.monochrome)
      val tint = fluidContentRoles(colors, isDark = false).pageTint
      val hi = maxOf(tint.red, tint.green, tint.blue)
      val lo = minOf(tint.red, tint.green, tint.blue)
      val saturation = if (hi <= 0f) 0f else (hi - lo) / hi
      assertTrue("pagina tinta: saturazione $saturation", saturation > 0.1f)
    }
    // Un bianco appena tinto resta un bianco: monocromo.
    assertTrue(fluidContentColorsOf(solid(Color(0xFFFFF4F4)), 96, 96)!!.monochrome)
  }
}
