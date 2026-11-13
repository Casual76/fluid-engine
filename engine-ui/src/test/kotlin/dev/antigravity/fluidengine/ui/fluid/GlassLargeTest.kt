package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveMaterial
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveTarget
import dev.antigravity.fluidengine.ui.glass.field.GlassBackdropStats
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.glassBlackShare
import dev.antigravity.fluidengine.ui.fluidphysics.PhysicsMaterialSource
import dev.antigravity.fluidengine.ui.fluidphysics.physicsLiquidRecipe
import dev.antigravity.fluidengine.ui.theme.FluidThemeSides
import androidx.compose.ui.graphics.compositeOver
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F2.3b, chi ancora non girava e non scuriva: la regola dei modi (l'inventario), il lato continuo dei grandi contro R4 A6,
 * il passaggio dell'inchiostro di un grande una volta per attraversamento e mai col lato sbagliato, l'isteresi di un
 * foglio fermo sopra uno sfondo a metà, e il tema dell'altro lato.
 */
class GlassLargeTest {

  private val adapt = GlassLiquidProfile.Default.adaptive

  // ---- L'inventario --------------------------------------------------------------------------------------------

  @Test
  fun theTabBarFlipsTheLargeOnesDarkenAndTheRestKeepsTheTheme() {
    // La tab bar flottante, le barre, i tasti: `Floating` / `Interactive`, piccoli, col patto: girano.
    assertEquals(GlassInkMode.Flip, glassInkMode(inkFollows = true, large = adapt.darkensContinuously(GlassRole.Floating), flipAllowed = true))
    assertEquals(GlassInkMode.Flip, glassInkMode(inkFollows = true, large = adapt.darkensContinuously(GlassRole.Interactive), flipAllowed = true))
    // Menu, pop-up, fogli, avvisi, pannello laterale, notifiche (`Modal`), card (`Content`): scuriscono in continuo.
    assertEquals(GlassInkMode.Continuous, glassInkMode(inkFollows = true, large = adapt.darkensContinuously(GlassRole.Modal), flipAllowed = true))
    assertEquals(GlassInkMode.Continuous, glassInkMode(inkFollows = true, large = adapt.darkensContinuously(GlassRole.Content), flipAllowed = true))
    // La barra laterale: `Floating`, ma pannello laterale, lo dice il suo patto (`GlassInk(large = true)`).
    assertEquals(GlassInkMode.Continuous, glassInkMode(inkFollows = true, large = GlassInk(large = true).large!!, flipAllowed = true))
    // Il tasto tinto e la selezione d'accento (pellicola fissa): il lato del tema.
    assertEquals(GlassInkMode.Theme, glassInkMode(inkFollows = true, large = false, flipAllowed = false))
    // `glassSurface` pubblico, senza contenuto suo: il lato del tema, grande o piccolo.
    assertEquals(GlassInkMode.Theme, glassInkMode(inkFollows = false, large = true, flipAllowed = true))
    assertEquals(GlassInkMode.Theme, glassInkMode(inkFollows = false, large = false, flipAllowed = true))
  }

  // ---- Il lato continuo dei grandi, contro R4 A6 -----------------------------------------------------------------

  /** Uno sfondo nero per la quota [q] e grigio 226 per il resto (la scena di R4 A6), come lo vede la miniatura. */
  private fun twoParts(q: Float): GlassBackdropStats {
    val light = 226f / 255f
    val mean = (1f - q) * light
    val variance = q * (1f - q) * light * light
    return GlassBackdropStats(mean, variance, 0f, mean, mean, mean, 1f)
  }

  @Test
  fun theBlackShareIsTheQuotaOfA6() {
    // Tolleranza dichiarata: un punto di quota (la stima è esatta su due parti nette; la miniatura vera ha i bordi
    // della fascia sfumati dal filtro bilineare, `bench/f2-1/probe_stats.md`).
    for (q in listOf(0f, 0.25f, 0.47f, 0.74f, 1f)) {
      val s = twoParts(q)
      assertEquals("quota $q", q, glassBlackShare(s.luminance, s.variance), 0.01f)
    }
  }

  private fun reading(luminance: Float, dark: Boolean = luminance < 0.45f) = GlassAdaptiveTarget(
    dark = dark, luminance = luminance, smoothedLuminance = luminance, texture = 0f,
    color = Color(luminance, luminance, luminance), measured = true,
  )

  /**
   * Fa girare [block] con un orologio dei fotogrammi da 16 ms, e chiama [onFrame] dopo ogni fotogramma (quando le
   * coroutine di quel fotogramma hanno lavorato), col tempo in millisecondi da quando è partito.
   */
  private fun frames(untilMillis: Long, onFrame: (Long) -> Unit = {}, block: suspend CoroutineScope.() -> Unit) {
    val clock = BroadcastFrameClock()
    runBlocking(clock) {
      val job = launch { block() }
      var t = 0L
      while (t <= untilMillis) {
        repeat(3) { yield() }
        clock.sendFrame((10_000L + t) * 1_000_000L)
        repeat(3) { yield() }
        onFrame(t)
        t += 16L
      }
      job.cancel()
    }
  }

  @Test
  fun aLargeDarkensWithTheBlackShareInAboutOneAndAHalfSeconds() {
    val state = GlassAdaptiveState()
    state.reset(darkSide = false, flipping = false, inking = true)
    val out = GlassAdaptiveMaterial()
    val darkness = HashMap<Long, Float>()
    frames(untilMillis = 2_000L, onFrame = { t -> darkness[t] = state.fill(out).darkness }) {
      // La prima misura (bianco) decide subito; poi il nero arriva.
      state.follow(reading(226f / 255f), twoParts(0f), darkSide = false, continuous = true)
      state.follow(reading(0f), twoParts(1f), darkSide = false, continuous = true)
    }
    val at = { t: Long -> darkness.filterKeys { it <= t }.maxByOrNull { it.key }!!.value }
    // A metà del tempo (750 ms) a metà strada circa, a 1,5 s arrivato (R4 A6: da 143 a 48 in ≈1,5 s).
    assertTrue("a 750 ms: ${at(752L)}", at(752L) in 0.3f..0.95f)
    assertEquals("a 1,5 s", 1f, at(1_504L), 0.01f)
    // Monotono: scurisce, non oscilla.
    val series = darkness.toSortedMap().values.toList()
    assertTrue(series.zipWithNext().all { (a, b) -> b >= a - 1e-6f })
  }

  @Test
  fun theInkOfALargeTurnsOnceAndNeverWithTheWrongSide() {
    val state = GlassAdaptiveState()
    state.reset(darkSide = false, flipping = false, inking = true)
    val black = reading(0f)
    var inkChanges = 0
    var sideChanges = 0
    var lastInk = state.inkOnDark
    var lastSide = state.appearance.isDark
    var composed = state.inkOnDark
    var wrongFrames = 0
    frames(
      untilMillis = 3_000L,
      onFrame = {
        // Quello che un contenuto vedrebbe in questo fotogramma: l'inchiostro composto, alla trasparenza di adesso.
        if (composed != state.inkOnDark && state.inkAlpha() > 0f) wrongFrames++
        // La composizione del fotogramma: `GlassInkContent` legge `inkOnDark` e l'aspetto, una volta sola se cambiano.
        if (state.inkOnDark != lastInk) {
          inkChanges++
          lastInk = state.inkOnDark
        }
        if (state.appearance.isDark != lastSide) {
          sideChanges++
          lastSide = state.appearance.isDark
        }
        composed = state.inkOnDark
        state.noteComposedInk(composed)
      },
    ) {
      // La prima misura: bianco, il lato chiaro subito.
      state.follow(reading(226f / 255f), twoParts(0f), darkSide = false, continuous = true)
      state.turn(state.wanted(reading(226f / 255f)), darkSide = false) { 0.89f }
      // Il nero: il lato continuo sale, e quando passa metà (più l'isteresi) l'inchiostro passa di lato.
      launch { state.follow(black, twoParts(1f), darkSide = false, continuous = true) }
      var last = state.wanted(black)
      while (true) {
        val wanted = state.wanted(black)
        if (wanted != last) {
          last = wanted
          state.turn(wanted, darkSide = false) { 0f }
        }
        kotlinx.coroutines.yield()
        androidx.compose.runtime.withFrameMillis { }
      }
    }
    assertTrue("alla fine scuro", state.inkOnDark)
    assertEquals("l'inchiostro cambia lato una volta", 1, inkChanges)
    assertEquals("il tema del contenuto cambia una volta", 1, sideChanges)
    assertEquals("mai un fotogramma con l'inchiostro del lato sbagliato", 0, wrongFrames)
  }

  // ---- L'isteresi ------------------------------------------------------------------------------------------------

  @Test
  fun aSheetStillOverAHalfBlackBackgroundDoesNotDance() {
    // Le soglie: sullo scuro a metà più l'isteresi, sul chiaro a metà meno.
    assertEquals(0, glassLargeInkSide(0.55f, onDark = false))
    assertEquals(1, glassLargeInkSide(0.56f, onDark = false))
    assertEquals(1, glassLargeInkSide(0.45f, onDark = true))
    assertEquals(0, glassLargeInkSide(0.44f, onDark = true))
    // Un foglio fermo sopra uno sfondo nero per metà: la quota letta trema di qualche punto da una lettura all'altra.
    for (start in listOf(false, true)) {
      var onDark = start
      var changes = 0
      for (i in 0 until 400) {
        val d = 0.5f + 0.04f * sin(i * 0.7f)
        val side = glassLargeInkSide(d, onDark) == 1
        if (side != onDark) changes++
        onDark = side
      }
      assertEquals("partendo ${if (start) "scuro" else "chiaro"}", 0, changes)
    }
  }

  // ---- Il tema dell'altro lato -----------------------------------------------------------------------------------

  @Test
  fun theOtherSideOfTheThemeIsComputedOnceAndKeepsTheThemeSideAsItIs() {
    val light = lightColorScheme(primary = Color(0xFF1F9E6E))
    var computed = 0
    val sides = FluidThemeSides(isDark = false, current = light) { dark ->
      computed++
      darkColorScheme(primary = if (dark) Color(0xFF2ED8A0) else Color(0xFF1F9E6E))
    }
    assertSame("il lato del tema è lo schema del tema, al bit", light, sides.scheme(false))
    assertEquals(0, computed)
    val dark = sides.scheme(true)
    assertSame(dark, sides.scheme(true))
    assertEquals("l'altro lato si calcola una volta", 1, computed)
    assertFalse(dark == light)
  }

  // ---- La finestra del pop-up (visto sul tablet) -----------------------------------------------------------------

  /**
   * La finestra del pop-up a riposo usa la pellicola **fissa** del lato del tema (il pannello con la sua quota di scrim).
   * Sul tablet, sopra il nero, il corpo restava a 136 su 255 col testo gia' bianco: la pellicola fissa si dipingeva uguale
   * sui due lati. Adesso vale sul lato del tema, al bit di prima, e cede alla ricetta adattiva quanto la finestra si e'
   * allontanata dal lato del tema; i viaggi dei menu (senza `restingFilmYields`) restano com'erano.
   */
  @Test
  fun theRestingFilmOfThePopoverYieldsWhenItDarkensAndIsUnchangedOnTheThemeSide() {
    val material = PhysicsMaterialSource(null, GlassDefaults.panelOptics().sanitized()).at(1f)
    val neutral = GlassTint(Color.White.copy(alpha = 0.54f), Color.White, Color.Transparent, GlassTintMode.Neutral)
    val withScrim = neutral.copy(overlay = neutral.overlay.compositeOver(Color.Black.copy(alpha = 0.12f)))
    assertEquals("la pellicola con lo scrim e' fissa", GlassTintMode.Fixed, glassTintLiquidMode(withScrim))
    // Il velo e la pellicola (una pellicola fissa sta in `tintColor`, col velo ad alfa zero), e il corpo che danno sopra
    // il nero: la pellicola sopra il velo sopra il nero.
    fun layers(darkness: Float, yields: Boolean, from: GlassTint? = withScrim): Pair<Color, Color> = physicsLiquidRecipe(
      role = GlassRole.Modal, material = material, shortDp = 192f, darkSide = false, amount = 1f, quality = 1f,
      press = 0f, tint = neutral, tintFrom = from, tintBlend = 0f, nested = false, motion = 0f,
      scratch = dev.antigravity.fluidengine.ui.fluidphysics.PhysicsLiquidRecipes(),
      adaptive = GlassAdaptiveMaterial().measure(0f, 0f, 0f, Color.Black).also { it.darkness = darkness },
      restingFilmYields = yields,
    ).let { it.veilColor to it.tintColor }
    val lum = { c: Color -> 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue }
    fun overBlack(l: Pair<Color, Color>): Float {
      val veil = l.first.alpha * lum(l.first)
      return l.second.alpha * lum(l.second) + (1f - l.second.alpha) * veil
    }
    // Sul lato del tema: com'era, al bit.
    assertEquals(layers(0f, yields = false), layers(0f, yields = true))
    // Sopra il nero, scurita: la ricetta adattiva di adesso (quella del foglio), non la pellicola chiara.
    assertEquals(layers(1f, yields = false, from = null), layers(1f, yields = true))
    val fixed = overBlack(layers(1f, yields = false))
    val dark = overBlack(layers(1f, yields = true))
    assertTrue("corpo sul nero: pellicola fissa $fixed, scurita $dark", dark < fixed - 0.2f)
    // A meta' strada, in mezzo.
    val half = overBlack(layers(0.5f, yields = true))
    assertTrue("pellicola fissa $fixed, a meta' $half, scuro $dark", half < fixed && half > dark)
  }

  /**
   * Sul tema scuro la finestra del pop-up non schiarisce: la pellicola fissa resta sopra la pagina scura (al bit di
   * prima), e sopra uno sfondo chiaro cede alla ricetta col soffitto del velo dei grandi, come il foglio. Sul tablet,
   * prima, il corpo sopra il bianco stava a 117-133 col testo bianco (circa 3,3:1); il foglio a 89.
   */
  @Test
  fun inTheDarkThemeThePopoverTakesTheCeilingOverWhiteAndStaysAsItWasOverTheDarkPage() {
    val ceiling = dev.antigravity.fluidengine.ui.glass.field.GlassLiquidDarkCeiling
    fun measured(luminance: Float) = GlassAdaptiveMaterial().measure(luminance, 0f, 0f, Color(luminance, luminance, luminance))
      .also { it.darkness = 1f }
    val dark = GlassAdaptiveMaterial().measure(0.05f, 0f, 0f, Color.Black).also { it.darkness = 1f }
    assertEquals("sulla pagina scura del tema: la pellicola di prima", 0f,
      dev.antigravity.fluidengine.ui.fluidphysics.physicsAwayFromThemeSide(dark, darkSide = true, ceiling = ceiling), 0f)
    assertEquals("sul bianco: la ricetta col soffitto", 1f,
      dev.antigravity.fluidengine.ui.fluidphysics.physicsAwayFromThemeSide(measured(1f), darkSide = true, ceiling = ceiling), 1e-6f)
    val mid = dev.antigravity.fluidengine.ui.fluidphysics.physicsAwayFromThemeSide(measured(0.7f), darkSide = true, ceiling = ceiling)
    assertTrue("in mezzo: $mid", mid > 0f && mid < 1f)
    // Senza misura, o senza soffitto: niente.
    assertEquals(0f, dev.antigravity.fluidengine.ui.fluidphysics.physicsAwayFromThemeSide(
      GlassAdaptiveMaterial().unmeasured().also { it.darkness = 1f }, darkSide = true, ceiling = ceiling), 0f)
    assertEquals(0f, dev.antigravity.fluidengine.ui.fluidphysics.physicsAwayFromThemeSide(measured(1f), darkSide = true, ceiling = 1f), 0f)
  }
}
