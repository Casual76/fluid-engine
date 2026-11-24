package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.2 · Gesti e ruote, i conti: l'inerzia a scatti di UIKit, le misure della ruota contro le schermate di iOS 26 e 27,
 * la scala della ghiera contro Foto, le due maniglie, i preset collegati. Le prove col dito vero sono nel `sample`
 * (`F8p2GesturesUiTest`).
 */
class F8p2WheelsTest {

  // --- L'inerzia a scatti ------------------------------------------------------------------------------------------

  @Test
  fun `la proiezione e' quella di UIKit, mezzo secondo di velocita', e si ferma sulla voce piu' vicina`() {
    assertEquals(0.499f, FluidDetentMotion.ProjectionSeconds, 1e-4f)
    assertEquals(2.002f, FluidDetentMotion.NaturalDecayPerSecond, 1e-3f)
    // 10,2 + 8 · 0,499 = 14,19: la voce 14.
    assertEquals(14f, fluidDetentLanding(10.2f, 8f, reducedMotion = false, lower = 0f, upper = 59f), 0f)
    // Verso l'alto, e tenuta dentro la lista.
    assertEquals(0f, fluidDetentLanding(3f, -40f, reducedMotion = false, lower = 0f, upper = 59f), 0f)
    assertEquals(59f, fluidDetentLanding(50f, 400f, reducedMotion = false, lower = 0f, upper = 59f), 0f)
    // Una ruota che gira all'infinito non ha limiti.
    assertEquals(-17f, fluidDetentLanding(3f, -40f, reducedMotion = false, lower = null, upper = null), 0f)
  }

  @Test
  fun `col movimento ridotto un lancio porta al piu' una voce oltre quella sotto il dito`() {
    assertEquals(11f, fluidDetentLanding(10.2f, 400f, reducedMotion = true, lower = 0f, upper = 59f), 0f)
    assertEquals(9f, fluidDetentLanding(10.2f, -400f, reducedMotion = true, lower = 0f, upper = 59f), 0f)
    assertEquals(10f, fluidDetentLanding(10.2f, 0f, reducedMotion = true, lower = 0f, upper = 59f), 0f)
  }

  @Test
  fun `un lancio decelera senza superare la voce e finisce esattamente li', con la velocita' del dito`() {
    val inertia = FluidDetentInertia()
    val target = fluidDetentLanding(10.2f, 12f, reducedMotion = false, lower = 0f, upper = 59f)
    inertia.flingTo(10.2f, 12f, target, pxPerStep = 84f)
    assertTrue("e' un decadimento, non una molla", inertia.decayPerSecond > 0f)
    var last = 10.2f
    var first = true
    var frames = 0
    while (inertia.step(1f / 120f)) {
      frames++
      assertTrue("mai oltre la voce: ${inertia.position}", inertia.position <= target + 1e-4f)
      assertTrue("sempre avanti", inertia.position >= last - 1e-5f)
      if (first) {
        // Il primo fotogramma parte con la velocita' del dito: 12 voci al secondo per 8,3 ms.
        assertEquals(12f / 120f, inertia.position - 10.2f, 0.01f)
        first = false
      }
      last = inertia.position
      assertTrue("finisce", frames < 2000)
    }
    assertEquals(target, inertia.position, 0f)
    assertFalse(inertia.moving)
    // Un lancio di sei voci dura qualche secondo di coda, come una lista di iOS, ma la coda sotto il pixel la chiude la
    // molla (F8.2c).
    assertTrue("durata ${frames / 120f} s", frames / 120f in 1.5f..6f)
  }

  @Test
  fun `lasciata piano, la ruota non aspetta l'ultimo pixel e si ferma entro 0,3 s dalla posa che si vede`() {
    // Il pan di agent-device sul tablet (F8.2b): il dito alzato a ~2,9 voci al secondo, a 0,3 voci da una voce. Il
    // decadimento ritoccato ha una costante bassa (~1,7 al secondo): da un pixel a un quarto ci metterebbe ln(4)/k = 0,8 s,
    // e il valore (con AM e PM che lo seguono) arrivava quasi un secondo dopo che la ruota sembrava ferma.
    val px = 84f
    val from = 8.3f
    val speed = 2.86f
    val target = fluidDetentLanding(from, speed, reducedMotion = false, lower = null, upper = null)
    assertEquals(10f, target, 0f)
    val inertia = FluidDetentInertia()
    inertia.flingTo(from, speed, target, px)
    val k = inertia.decayPerSecond
    assertTrue("un decadimento lento: $k", k in 1f..3f)
    val dt = 1f / 120f
    var t = 0f
    var pose = -1f
    var last = from
    var moving = true
    while (moving) {
      moving = inertia.step(dt)
      t += dt
      assertTrue("mai oltre la voce: ${inertia.position}", inertia.position <= target + 1e-5f)
      assertTrue("sempre avanti", inertia.position >= last - 1e-6f)
      last = inertia.position
      // La posa che si vede: entro un pixel dalla voce.
      if (pose < 0f && abs(target - inertia.position) * px <= 1f) pose = t
      assertTrue("finisce", t < 10f)
    }
    assertEquals(target, inertia.position, 0f)
    val tail = t - pose
    println("F8p2c lancio lento: k = $k, posa a $pose s, ferma a $t s, coda $tail s (prima ${ln(4f) / k} s)")
    assertTrue("dalla posa alla fine $tail s", tail <= 0.3f)
    assertEquals(1f, FluidDetentMotion.HandoffPx, 0f)
  }

  @Test
  fun `il moto e' lo stesso a 60 e a 120 Hz, perche' le soluzioni sono esatte`() {
    fun at(hz: Int, seconds: Float): Float {
      val inertia = FluidDetentInertia()
      inertia.flingTo(3f, 9f, fluidDetentLanding(3f, 9f, false, 0f, 59f), 84f)
      val frames = (seconds * hz).toInt()
      repeat(frames) { inertia.step(1f / hz) }
      return inertia.position
    }
    assertEquals(at(60, 0.25f), at(120, 0.25f), 1e-3f)
    assertEquals(at(60, 0.5f), at(120, 0.5f), 1e-3f)
  }

  @Test
  fun `un dito lasciato quasi fermo si assesta con la molla dei tasti, senza scavalcare`() {
    val inertia = FluidDetentInertia()
    inertia.flingTo(10.4f, 0f, 10f, 84f)
    assertEquals("molla", 0f, inertia.decayPerSecond, 0f)
    var t = 0f
    while (inertia.step(1f / 120f)) {
      t += 1f / 120f
      assertTrue(inertia.position >= 10f - 1e-4f)
    }
    assertEquals(10f, inertia.position, 0f)
    // 0,30 s di risposta: posata entro mezzo secondo.
    assertTrue("assestata in $t s", t < 0.5f)
  }

  @Test
  fun `il ritocco del decadimento resta vicino a quello naturale, se no e' una molla`() {
    // Verso opposto: niente decadimento.
    assertEquals(0f, fluidDetentDecayFor(10f, 5f, 9f), 0f)
    // Distanza giusta per la velocita': il decadimento naturale.
    val natural = FluidDetentMotion.NaturalDecayPerSecond
    assertEquals(natural, fluidDetentDecayFor(0f, 10f, 10f / natural), 1e-3f)
    // Troppo corto per tanta velocita' (un freno): molla.
    assertEquals(0f, fluidDetentDecayFor(0f, 100f, 1f), 0f)
  }

  @Test
  fun `l'inerzia non alloca per fotogramma`() {
    val inertia = FluidDetentInertia()
    // Il punto d'arrivo si calcola una volta per lancio (al rilascio del dito), non per fotogramma.
    val target = fluidDetentLanding(0f, 30f, false, 0f, 400f)
    var frames = 0L
    fun cycle() {
      inertia.flingTo(0f, 30f, target, 84f)
      while (inertia.step(1f / 120f)) frames++
      inertia.springTo(inertia.position, 3f, inertia.position - 2f, 84f, FluidDetentMotion.SettleResponseSeconds)
      while (inertia.step(1f / 120f)) frames++
    }
    val counter = AllocationCounter()
    repeat(50) { cycle() } // il JIT
    counter.read()
    // Due serie di lunghezza diversa: cio' che costa la misura (la lettura, il primo giro) e' uguale nelle due e si
    // toglie; resta solo cio' che cresce con i fotogrammi.
    fun measure(cycles: Int): Pair<Long, Long> {
      frames = 0L
      val before = counter.read()
      repeat(cycles) { cycle() }
      return (counter.read() - before) to frames
    }
    val (shortBytes, shortFrames) = measure(100)
    val (longBytes, longFrames) = measure(300)
    val perFrame = (longBytes - shortBytes).toDouble() / (longFrames - shortFrames).toDouble()
    println("F8p2 inerzia: $shortBytes byte in $shortFrames fotogrammi, $longBytes byte in $longFrames: $perFrame byte a fotogramma")
    // Un oggetto per fotogramma costerebbe almeno 16 byte a fotogramma; cio' che resta (centesimi di byte, cioe' un oggetto
    // ogni migliaia di fotogrammi) e' il rumore del JIT e cambia da un giro all'altro (0,005 e 0,011 nei lotti di F8.2).
    assertTrue("$perFrame byte a fotogramma", perFrame < 1.0)
    assertTrue("costo fisso della misura: $longBytes byte", longBytes < 2048)
  }

  @Test
  fun `ruota e ghiera restano nitide anche durante l'inerzia, perche' le muove il dito`() {
    // F8.2b, la scelta di Alessio del 24 novembre 2026: cio' che muove l'utente col dito resta nitido. Nessun effetto di
    // sfocatura nei tre file del moto a scatti (la sfocatura del moto di F8.2 e' stata tolta).
    val root = listOf(File("src/main/kotlin"), File("engine-ui/src/main/kotlin")).first { it.isDirectory }
    for (name in listOf("FluidWheelPicker.kt", "FluidRulerDial.kt", "FluidDetentInertia.kt")) {
      val text = File(root, "dev/antigravity/fluidengine/ui/fluid/$name").readText()
      assertFalse("$name: renderEffect", text.contains("renderEffect"))
      assertFalse("$name: BlurEffect", text.contains("BlurEffect"))
      assertFalse("$name: blur(", text.contains("blur("))
    }
  }

  @Test
  fun `su una ruota che gira la voce chiesta da fuori si raggiunge per la strada corta`() {
    assertEquals(62f, fluidDetentNearest(58f, 2, 60, cyclic = true), 0f)
    assertEquals(-3f, fluidDetentNearest(2f, 57, 60, cyclic = true), 0f)
    assertEquals(57f, fluidDetentNearest(2f, 57, 60, cyclic = false), 0f)
    assertEquals(59, fluidDetentWrap(-1, 60))
  }

  // --- La ruota contro iOS -----------------------------------------------------------------------------------------

  /** Il colore di [fill] sopra una pagina [page] (0–255 per canale). */
  private fun over(fill: Color, page: Int, pageBlue: Int = page): Triple<Float, Float, Float> {
    val a = fill.alpha
    fun mix(c: Float, p: Int) = p * (1f - a) + c * 255f * a
    return Triple(mix(fill.red, page), mix(fill.green, page), mix(fill.blue, pageBlue))
  }

  @Test
  fun `la fascia e' il grigio piatto di iOS 26 e 27, il quaternarySystemFill, e non e' vetro`() {
    // Scuro: sulla pagina 28, 28, 30 la fascia del timer di iOS 27 e' 44, 44, 47 e quella delle HIG 44, 44, 48.
    val (dr, dg, db) = over(fluidWheelBandFill(dark = true), 28, 30)
    assertEquals(44f, dr, 1f)
    assertEquals(44f, dg, 1f)
    assertEquals(47.5f, db, 1f)
    // Chiaro: sulla pagina bianca la fascia delle HIG e' 244, 244, 245.
    val (lr, lg, lb) = over(fluidWheelBandFill(dark = false), 255)
    assertEquals(244f, lr, 1f)
    assertEquals(244f, lg, 1f)
    assertEquals(245f, lb, 1f)
    // La fascia non galleggia sul contenuto: niente vetro nella ruota.
    val root = listOf(File("src/main/kotlin"), File("engine-ui/src/main/kotlin")).first { it.isDirectory }
    val text = File(root, "dev/antigravity/fluidengine/ui/fluid/FluidWheelPicker.kt").readText()
    assertFalse("fluidGlass", text.contains("fluidGlass("))
    assertFalse("glassBackdropSource", text.contains("glassBackdropSource("))
  }

  @Test
  fun `le voci sono in peso normale come in iOS, anche quella scelta, e solo l'unita' e' semibold`() {
    val typography = fluidTypography()
    val style = fluidWheelRowStyle(typography.headlineSmall)
    assertEquals(FontWeight.Normal, style.fontWeight)
    assertEquals(FluidFontFamily, style.fontFamily)
    assertEquals("tnum", style.fontFeatureSettings)
    assertEquals(typography.headlineSmall.fontSize, style.fontSize)
    // La famiglia ha davvero il peso normale. Inter Display no (solo semigrassetto e grassetto): il normale chiesto li'
    // diventava semigrassetto, ed era il «grassetto» delle tavole di F8.2b.
    assertTrue((style.fontFamily as FontListFontFamily).fonts.any { it.weight == FontWeight.Normal })
    assertFalse((typography.headlineSmall.fontFamily as FontListFontFamily).fonts.any { it.weight == FontWeight.Normal })
    // L'unita' («ore», «min», «s») resta semibold.
    assertEquals(FontWeight.SemiBold, typography.titleMedium.fontWeight)
  }

  /** Le voci 1–3 a `R·sin(n·a)` dal centro, in dp, per una ruota di serie. */
  private fun rowOffsetDp(n: Int): Float {
    val height = FluidWheelDefaults.Height.value
    val radius = fluidWheelRadius(height)
    val angle = fluidWheelAngle(n.toFloat(), FluidWheelDefaults.RowHeight.value, radius)
    return radius * sin(angle)
  }

  @Test
  fun `le voci stanno dove le mette iOS 27, entro due punti`() {
    // Il timer di Orologio di iOS 27 (guida dell'iPhone, ~1,15 px a punto): 30,9, 57,8, 76,5 pt.
    assertEquals(30.9f, rowOffsetDp(1), 2f)
    assertEquals(57.8f, rowOffsetDp(2), 2f)
    assertEquals(76.5f, rowOffsetDp(3), 2f)
    assertEquals(216f, FluidWheelDefaults.Height.value, 0f)
    assertEquals(32f, FluidWheelDefaults.RowHeight.value, 0f)
    assertEquals(34f, FluidWheelDefaults.RowHeight.value + FluidWheelLook.BandExtra.value, 0f)
  }

  @Test
  fun `la fascia e' una capsula rientrata di 45 punti come in iOS 26 e 27, e meno su una ruota stretta`() {
    // Il timer di Orologio di iOS 27: la fascia da 73 a 415 px su uno schermo largo 393 pt da 22 a 466 px.
    val pxPerPt = (466 - 22 + 1) / 393f
    assertEquals((73 - 22) / pxPerPt, FluidWheelLook.BandInset.value, 1.5f)
    assertEquals((466 - 415) / pxPerPt, FluidWheelLook.BandInset.value, 1.5f)
    assertEquals(45f, fluidWheelBandInset(393.dp).value, 0f)
    assertEquals(45f, fluidWheelBandInset(411.dp).value, 0f)
    // Una ruota da 200 dp (un popover): la fascia resta larga 152.
    assertEquals(24f, fluidWheelBandInset(200.dp).value, 1e-3f)
    // Alta 34, cioe' 38,7 px a 1,13 px a punto nelle schermate.
    assertEquals(38.7f / pxPerPt, FluidWheelDefaults.RowHeight.value + FluidWheelLook.BandExtra.value, 0.5f)
  }

  @Test
  fun `tre voci piene per lato e una quarta schiacciata, come iOS`() {
    val radius = fluidWheelRadius(FluidWheelDefaults.Height.value)
    val row = FluidWheelDefaults.RowHeight.value
    val a3 = fluidWheelAngle(3f, row, radius)
    val a4 = fluidWheelAngle(4f, row, radius)
    val a5 = fluidWheelAngle(5f, row, radius)
    assertTrue("la terza si legge: ${cos(a3)}", cos(a3) > 0.3f)
    assertTrue("la quarta e' schiacciata: ${cos(a4)}", a4 < PI / 2 && cos(a4) < 0.15f)
    assertTrue("la quinta non c'e'", a5 >= PI / 2)
  }

  @Test
  fun `le voci fuori dalla fascia hanno l'inchiostro di iOS e svaniscono verso i bordi`() {
    val radius = fluidWheelRadius(FluidWheelDefaults.Height.value)
    val row = FluidWheelDefaults.RowHeight.value
    fun ink(n: Int) = FluidWheelLook.OuterInk * fluidWheelFade(fluidWheelAngle(n.toFloat(), row, radius))
    // Le HIG danno 0,28 alla prima voce, la schermata del timer 0,39.
    assertTrue("prima voce ${ink(1)}", ink(1) in 0.26f..0.40f)
    assertTrue(ink(2) < ink(1) && ink(3) < ink(2))
    assertTrue("terza voce ${ink(3)}", ink(3) in 0.10f..0.25f)
    assertEquals(1.12f, FluidWheelLook.Magnification, 0f)
  }

  @Test
  fun `un tocco sotto la fascia porta al centro la voce che sta li'`() {
    val radius = fluidWheelRadius(216f)
    for (n in -3..3) {
      val dy = radius * sin(fluidWheelAngle(n.toFloat(), 32f, radius))
      assertEquals("voce $n", 20f + n, fluidWheelRowAt(dy, 20f, 32f, radius), 0f)
    }
    // Oltre il bordo, la voce di bordo.
    assertEquals(24f, fluidWheelRowAt(500f, 20f, 32f, radius), 0f)
  }

  @Test
  fun `una voce spenta scivola alla piu' vicina che si puo' scegliere`() {
    val days = { i: Int -> i < 30 } // novembre: 30 giorni
    assertEquals(29, fluidWheelNearestEnabled(30, 31, cyclic = false, allowed = days))
    assertEquals(0, fluidWheelNearestEnabled(30, 31, cyclic = true) { it != 30 && it < 29 || it == 0 })
    assertEquals(-1, fluidWheelNearestEnabled(3, 5, cyclic = false) { false })
  }

  // --- Le ruote pronte ---------------------------------------------------------------------------------------------

  @Test
  fun `una durata non scende sotto il minimo, come countDownTimer`() {
    assertEquals(1L, fluidDurationClamp(0L, 23, showSeconds = true, minuteInterval = 1, minimumSeconds = 1L))
    assertEquals(60L, fluidDurationClamp(0L, 23, showSeconds = false, minuteInterval = 1, minimumSeconds = 60L))
    assertEquals(300L, fluidDurationClamp(0L, 23, showSeconds = false, minuteInterval = 5, minimumSeconds = 60L))
    // Minuti al passo, secondi tolti senza secondi, oltre il massimo il massimo.
    assertEquals(5L * 60L, fluidDurationClamp(7L * 60L + 12L, 23, showSeconds = false, minuteInterval = 5, minimumSeconds = 60L))
    assertEquals(23L * 3600L + 59L * 60L + 59L, fluidDurationClamp(999_999L, 23, true, 1, 1L))
    assertEquals(1, fluidWheelMinuteInterval(7))
    assertEquals(15, fluidWheelMinuteInterval(15))
  }

  @Test
  fun `in 12 ore la ruota delle ore che passa da 11 a 12 gira AM e PM`() {
    // 11 AM → «12» (voce 0) in avanti: mezzogiorno.
    assertEquals(LocalTime.of(12, 0), fluidTimeWheelHour12(LocalTime.of(11, 0), 0))
    // 12 PM → 11 indietro: le 11 di mattina.
    assertEquals(LocalTime.of(11, 30), fluidTimeWheelHour12(LocalTime.of(12, 30), 11))
    // 1 PM → 12 indietro: resta PM (mezzogiorno).
    assertEquals(LocalTime.of(12, 0), fluidTimeWheelHour12(LocalTime.of(13, 0), 0))
    // 11 PM → 12 avanti: mezzanotte.
    assertEquals(LocalTime.of(0, 15), fluidTimeWheelHour12(LocalTime.of(23, 15), 0))
    // Senza passare il confine il periodo resta.
    assertEquals(LocalTime.of(15, 0), fluidTimeWheelHour12(LocalTime.of(14, 0), 3))
  }

  @Test
  fun `in 12 ore AM e PM vengono dal moto vero della colonna, anche su 6 voci e sui lanci lunghi`() {
    val eight = LocalTime.of(8, 0)
    // Sei voci esatte: la strada corta e' ambigua, il moto no.
    assertEquals(LocalTime.of(14, 0), fluidTimeWheelHour12(eight, 2, rows = 6))
    assertEquals(LocalTime.of(2, 0), fluidTimeWheelHour12(eight, 2, rows = -6))
    // Un lancio lungo passa il confine fra 11 e 12 piu' volte.
    assertEquals(LocalTime.of(2, 0), fluidTimeWheelHour12(eight, 2, rows = 18))
    assertEquals(LocalTime.of(14, 0), fluidTimeWheelHour12(eight, 2, rows = -18))
    assertEquals(LocalTime.of(1, 0), fluidTimeWheelHour12(eight, 1, rows = 17))
    // Un giro intero torna sulla stessa voce e gira solo AM e PM; due giri, niente.
    assertEquals(LocalTime.of(20, 0), fluidTimeWheelHour12(eight, 8, rows = 12))
    assertEquals(eight, fluidTimeWheelHour12(eight, 8, rows = 24))
    // Mezzanotte e mezzogiorno, avanti e indietro di una voce; i minuti restano.
    assertEquals(LocalTime.of(12, 0), fluidTimeWheelHour12(LocalTime.of(11, 0), 0, rows = 1))
    assertEquals(LocalTime.of(23, 30), fluidTimeWheelHour12(LocalTime.of(0, 30), 11, rows = -1))
    // Un moto che non porta alla voce (non dovrebbe succedere) vale come la strada corta.
    assertEquals(LocalTime.of(2, 0), fluidTimeWheelHour12(eight, 2, rows = 5))
    // La strada corta: al piu' mezzo giro, e a meta' esatta nel verso della differenza.
    assertEquals(-6, fluidWheelShortestRows(8, 2, 12, cyclic = true))
    assertEquals(6, fluidWheelShortestRows(2, 8, 12, cyclic = true))
    assertEquals(1, fluidWheelShortestRows(11, 0, 12, cyclic = true))
    assertEquals(-11, fluidWheelShortestRows(11, 0, 12, cyclic = false))
  }

  // --- La ghiera contro Foto ---------------------------------------------------------------------------------------

  @Test
  fun `la ghiera di Raddrizza ha 90 tacche, una principale ogni 10 contando dallo zero`() {
    val scale = FluidRulerScale(-45f..45f, 1f, 10, 0f)
    assertEquals(90, scale.ticks)
    assertEquals(45, scale.neutralTick)
    assertTrue(scale.isMajor(45) && scale.isMajor(35) && scale.isMajor(55) && scale.isMajor(5))
    assertFalse(scale.isMajor(40))
    assertEquals(-12f, scale.quantize(-12.4f), 1e-5f)
    assertEquals(9f, FluidRulerLook.TickSpacing.value, 0f)
    assertEquals(0.49f, FluidRulerLook.MinorInk, 0f)
    assertEquals(1.8f, FluidRulerLook.TickWidth.value, 0f)
    assertEquals(0.8f, FluidRulerLook.MajorInk, 0f)
  }

  @Test
  fun `un lancio della ghiera si ferma sulla principale vicina, se no sulla tacca`() {
    val scale = FluidRulerScale(-45f..45f, 1f, 10, 0f)
    // 43 tacche: a 2 dalla principale dello zero (45), che e' entro un quarto di intervallo.
    assertEquals(45f, scale.landing(43.0f), 0f)
    assertEquals(41f, scale.landing(41.2f), 0f)
    assertEquals(55f, scale.landing(53.6f), 0f)
    // Mai fuori.
    assertEquals(90f, scale.landing(130f), 0f)
  }

  @Test
  fun `la scritta di serie della ghiera`() {
    assertEquals("−12", fluidRulerDefaultText(-12f))
    assertEquals("0", fluidRulerDefaultText(0f))
    assertEquals("2,5", fluidRulerDefaultText(2.5f))
    assertEquals(0f, fluidRulerEdgeFade(0f, 300f, 30f), 0f)
    assertEquals(1f, fluidRulerEdgeFade(150f, 300f, 30f), 0f)
    assertEquals(0.5f, fluidRulerEdgeFade(285f, 300f, 30f), 1e-5f)
  }

  // --- Le due maniglie ---------------------------------------------------------------------------------------------

  @Test
  fun `le maniglie non si scavalcano e tengono la distanza minima`() {
    val range = 0f..600f
    assertEquals(240f, fluidRangeClampStart(500f, 270f, range, 30f), 0f)
    assertEquals(90f, fluidRangeClampEnd(10f, 60f, range, 30f), 0f)
    assertEquals(0f, fluidRangeClampStart(-20f, 270f, range, 0f), 0f)
    // Un intervallo al contrario si legge ordinato; uno troppo stretto si allarga alla distanza minima.
    assertEquals(60f, fluidRangeStartOf(270f..60f, range, 0f), 0f)
    assertEquals(270f, fluidRangeEndOf(270f..60f, range, 0f), 0f)
    assertEquals(100f, fluidRangeStartOf(100f..110f, range, 30f), 0f)
    assertEquals(130f, fluidRangeEndOf(100f..110f, range, 30f), 0f)
    assertEquals(570f, fluidRangeStartOf(590f..600f, range, 30f), 0f)
  }

  @Test
  fun `chi prende il dito fra due maniglie`() {
    // Sovrapposte: tutte e due, decide il primo movimento.
    assertEquals(RangeBoth, fluidRangePick(100f, 100f, 100f, 30f))
    assertEquals(RangeEnd, fluidRangeNearest(100f, 100f, 100f, towardsEnd = 5f))
    assertEquals(RangeStart, fluidRangeNearest(100f, 100f, 100f, towardsEnd = -5f))
    // Vicine ma non sovrapposte: la piu' vicina al dito.
    assertEquals(RangeStart, fluidRangePick(105f, 100f, 130f, 30f))
    assertEquals(RangeEnd, fluidRangePick(126f, 100f, 130f, 30f))
    assertEquals(RangeNone, fluidRangePick(300f, 100f, 130f, 30f))
  }

  @Test
  fun `aumenta e diminuisci di una maniglia vanno alla tacca dopo, o di un ventesimo`() {
    val stops = listOf(0f, 1f, 5f, 10f, 25f, 50f)
    assertEquals(10f, fluidRangeStepTarget(5f, +1, stops, 0f..50f, 2.5f), 0f)
    assertEquals(1f, fluidRangeStepTarget(5f, -1, stops, 0f..50f, 2.5f), 0f)
    assertEquals(30f, fluidRangeAccessibilityStep(0f..600f), 0f)
    assertEquals(90f, fluidRangeStepTarget(60f, +1, emptyList(), 0f..600f, 30f), 0f)
    assertEquals(0f, fluidRangeStepTarget(10f, -1, emptyList(), 0f..600f, 30f), 0f)
    assertTrue(abs(fluidRangeStepTarget(600f, +1, emptyList(), 0f..600f, 30f) - 600f) < 1e-6f)
  }

  /**
   * I byte allocati finora da questo filo (HotSpot). Per riflessione: la classpath dei test di Android non ha
   * `java.lang.management`, la JVM che li esegue si'. Metodo e argomenti si preparano una volta: una lettura alloca solo
   * il `Long` che torna (16 byte), che il margine della prova tiene in conto.
   */
  private class AllocationCounter {
    private val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
    private val method = Class.forName("com.sun.management.ThreadMXBean")
      .getMethod("getThreadAllocatedBytes", Long::class.javaPrimitiveType)
    private val args = arrayOf<Any>(Thread.currentThread().id)

    fun read(): Long = method.invoke(bean, *args) as Long
  }

}
