package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScopeImpl
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il velo dei grandi segue **la meta effettiva del gesto** (giro di correzione unico di F1B: rilievo 1 del critico A,
 * R2 del critico C, 8 del critico D), sulla strada vera: uno stato di Fluid-physics vero guidato da fuori come lo guida un
 * menu (tasto → pannello per tutta la vita, il progresso che sale aprendo e scende richiudendo), la superficie vera e i
 * suoi effetti, un orologio a 120 Hz, e **le molle vere dei menu** (`FluidMotion.dropOpen` e `dropClose`, ζ 0,72: la prima
 * oltrepassa l'1, la seconda scende sotto lo zero del 3,8% e risale).
 *
 * Prima il velo mirava sempre al capo `to` della guida: richiudendo un menu restava quello del pannello, e sul tablet, in
 * scuro, il tasto ripreso era verde scuro per ~330 ms da fermo e tornava grigio in un fotogramma (−31 livelli). E dopo un
 * salto (`snapTo`, il movimento ridotto) il primo disegno restituiva il velo di prima, e nessuno ne programmava un altro.
 * La prima correzione contava come riapertura anche la risalita del sottopasso della chiusura: sul tablet il velo tornava
 * verso il pannello per ~150 ms sul tasto fermo (−15 livelli) e scattava di +17 alla posa. La molla di chiusura della
 * prima stesura del test (ζ 0,9) non scendeva sotto lo zero abbastanza da farlo vedere.
 */
class PhysicsVeilGoalTest {

  private val density = 2f
  /** «Ordina» a riposo: 100 × 47 dp, velo dei piccoli. */
  private val button = FluidForm.Slab(Rect(400f, 1200f, 600f, 1294f), FluidCornerRadii(47f, 47f, 47f, 47f))
  /** Il pannello del menu: 280 × 230 dp, velo dei grandi. */
  private val panel = FluidForm.Slab(Rect(220f, 640f, 780f, 1100f), FluidCornerRadii(56f, 56f, 56f, 56f))

  private var nanos = 0L
  private val frameNanos = 8_333_333L
  private var progress = 0f

  private val state = FluidPhysicsState(button).apply {
    this.density = this@PhysicsVeilGoalTest.density
    liquidAllowed = false
  }
  private val surface = PhysicsLiquidSurface(
    state = state,
    role = GlassRole.Modal,
    material = PhysicsMaterialSource(GlassDefaults.buttonOptics().sanitized(), GlassDefaults.panelOptics(compact = true).sanitized()),
    darkSide = true,
    screenDensity = density,
    tint = NeutralTint,
    tintFrom = NeutralTint,
    pressState = null,
    glowWeight = 1f,
    profile = GlassLiquidProfile.Default,
    intensity = { 1f },
    tintBlend = { 1f },
    opticsBlend = { 1f },
    quality = { 1f },
    layoutWidth = { 1600f },
    onPanel = { false },
    clock = { nanos },
  )
  private val effectScope: BackdropEffectScopeImpl = object : BackdropEffectScopeImpl() {
    override val shape: Shape = RectangleShape
  }.also { it.size = Size(1600f, 2560f) }

  /** Un disegno a 120 Hz, col progresso dato: il velo che la superficie mostra. */
  private fun draw(p: Float): Float {
    nanos += frameNanos
    progress = p
    surface.effects.invoke(effectScope)
    return surface.largeVeilShown
  }

  /** La molla d'apertura (sottosmorzata, oltrepassa) o di chiusura, da [from] a [to]: i suoi valori a 120 Hz, fino a posarsi. */
  private fun spring(from: Float, to: Float, damping: Float, periodS: Float): List<Float> {
    val w0 = 2.0 * PI / periodS
    val wd = w0 * sqrt(1.0 - damping * damping)
    val out = ArrayList<Float>()
    var t = 0.0
    while (true) {
      t += frameNanos / 1e9
      val e = exp(-damping * w0 * t) * (cos(wd * t) + damping / sqrt(1.0 - damping * damping) * sin(wd * t))
      val x = to + (from - to) * e.toFloat()
      if (abs(x - to) < 0.0005f && abs(e) < 0.002) break
      out += x
    }
    out += to // la molla del chiamante si posa in punto sul traguardo
    return out
  }

  /** Il periodo proprio di una molla di Compose (massa uno) di rigidezza [stiffness]. */
  private fun period(stiffness: Float): Float = (2.0 * PI / sqrt(stiffness.toDouble())).toFloat()

  private val openPeriod = period(FluidMotion.DropOpenStiffness)
  private val closePeriod = period(FluidMotion.DropCloseStiffness)
  private val open = spring(0f, 1f, damping = FluidMotion.DropJourneyDamping, periodS = openPeriod)
  private val close = spring(1f, 0f, damping = FluidMotion.DropJourneyDamping, periodS = closePeriod)
  private val step = (frameNanos / 1e9f) / PhysicsLargeVeilSeconds

  private fun drive() = state.driveExternally(button, panel) { progress }

  @Test
  fun closingAMenuWalksTheVeilBackToTheButtonBeforeTheShapeLands() {
    drive()
    assertEquals("il tasto a riposo ha il velo dei piccoli", 0f, draw(0f), 0f)
    open.forEach { draw(it) }
    assertEquals("il pannello aperto ha il velo dei grandi", 1f, draw(1f), 0f)
    repeat(30) { assertEquals(1f, draw(1f), 0f) }
    assertTrue("la molla di chiusura vera scende sotto lo zero e risale (${close.min()})", close.min() < -PhysicsDriveReturnMargin)
    var last = 1f
    val seen = close.map { p -> draw(p).also { w ->
      assertTrue("richiudendo il velo non torna verso il pannello (a p=$p: $last → $w)", w <= last + 1e-6f)
      assertTrue("richiudendo il velo cammina, non salta (a p=$p: $last → $w)", last - w <= step + 1e-4f)
      last = w
    } }
    assertEquals("a molla posata sul tasto, il velo è quello del tasto", 0f, seen.last(), 0f)
    // Già arrivato prima che la forma si posi: l'ultimo passo non è uno scatto.
    val beforeLanding = seen[seen.size - 2]
    assertEquals("un fotogramma prima della posa il velo è già quello del tasto", 0f, beforeLanding, step + 1e-4f)
    val atNinetyPercent = close.indexOfFirst { it < 0.1f }
    assertTrue("a 90% della chiusura il velo è già sceso (${seen[atNinetyPercent]})", seen[atNinetyPercent] < 0.5f)
  }

  @Test
  fun aMenuCancelledHalfwayReturnsToTheButtonsVeil() {
    drive()
    draw(0f)
    val half = open.takeWhile { it < 0.5f }
    half.forEach { draw(it) }
    val reached = surface.largeVeilShown
    assertTrue("a metà apertura il velo ha cominciato a scendere verso il pannello ($reached)", reached > 0f)
    val back = spring(half.last(), 0f, damping = FluidMotion.DropJourneyDamping, periodS = closePeriod)
    var last = reached
    back.forEach { p -> draw(p).also { w ->
      assertTrue("nell'annullo il velo non va più verso il pannello (a p=$p: $last → $w)", w <= last + 1e-6f)
      last = w
    } }
    assertEquals(0f, surface.largeVeilShown, 0f)
  }

  @Test
  fun aMenuReopenedWhileClosingWalksBackToThePanel() {
    drive()
    draw(0f)
    open.forEach { draw(it) }
    draw(1f)
    val closing = close.takeWhile { it > 0.6f }
    closing.forEach { draw(it) }
    val low = surface.largeVeilShown
    assertTrue("richiudendo il velo è sceso ($low)", low < 1f)
    val reopen = spring(closing.last(), 1f, damping = FluidMotion.DropJourneyDamping, periodS = openPeriod)
    reopen.forEach { draw(it) }
    assertEquals("riaperto e posato, il velo è quello del pannello", 1f, surface.largeVeilShown, 0f)
  }

  @Test
  fun theOvershootOfTheOpeningIsNotAReturn() {
    drive()
    draw(0f)
    var peak = 0f
    var after = Float.NaN
    open.forEachIndexed { i, p ->
      val w = draw(p)
      if (i > 0 && open[i - 1] > 1f && p < open[i - 1]) {
        // L'oltrepasso che torna verso l'1: il velo non deve tornare indietro.
        assertTrue("sull'oltrepasso che rientra il velo non scende (a p=$p: $peak → $w)", w >= peak - 1e-6f)
        after = w
      }
      peak = maxOf(peak, w)
    }
    assertTrue("la molla d'apertura del test oltrepassa", after.isFinite())
  }

  @Test
  fun aJumpSettlesTheVeilAtOnce() {
    val still = FluidPhysicsState(button).apply { this.density = this@PhysicsVeilGoalTest.density }
    val jumping = PhysicsLiquidSurface(
      state = still,
      role = GlassRole.Modal,
      material = PhysicsMaterialSource(null, GlassDefaults.panelOptics(compact = true).sanitized()),
      darkSide = true,
      screenDensity = density,
      tint = NeutralTint,
      tintFrom = null,
      pressState = null,
      glowWeight = 1f,
      profile = GlassLiquidProfile.Default,
      intensity = { 1f },
      tintBlend = { 1f },
      opticsBlend = { 1f },
      quality = { 1f },
      layoutWidth = { 1600f },
      onPanel = { false },
      clock = { nanos },
    )
    nanos += frameNanos
    jumping.effects.invoke(effectScope)
    assertEquals(0f, jumping.largeVeilShown, 0f)
    // Il movimento ridotto (o un `snapTo`): la forma arriva senza viaggiare, e il primo disegno dopo è l'unico.
    still.snapTo(panel)
    nanos += frameNanos
    jumping.effects.invoke(effectScope)
    assertEquals("dopo un salto al pannello il primo disegno ha il velo del pannello", 1f, jumping.largeVeilShown, 0f)
    still.snapTo(button)
    nanos += frameNanos
    jumping.effects.invoke(effectScope)
    assertEquals("e dopo il salto indietro quello del tasto", 0f, jumping.largeVeilShown, 0f)
  }

  private companion object {
    val NeutralTint = GlassTint(
      overlay = Color.Transparent,
      fallback = Color.Transparent,
      hairline = Color.Transparent,
      mode = GlassTintMode.Neutral,
    )
  }
}
