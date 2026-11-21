@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.antigravity.fluidengine.ui.fluidphysics

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import dev.antigravity.fluidengine.ui.fluid.FluidPressTransform
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.fluidGlassPressedSlab
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScope
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScopeImpl
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectsUpdates
import dev.antigravity.fluidengine.ui.glass.field.GlassDrawInputs
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.glassBodyProbe
import dev.antigravity.fluidengine.ui.glass.interaction.FingerGlow
import dev.antigravity.fluidengine.ui.glass.interaction.FingerGlowLevels
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F6-c: la tenuta sul grappolo dei tre tondi di «Bagliore ai vicini» (`TouchLab`), contata.
 *
 * Sul tablet (S5d) il nodo del grappolo installava 73 e 74 effetti nuovi per tenuta, con zero ricatture. I log dicono
 * quando: nei ~265 ms della crescita del tondo premuto e nei ~325 ms del rilascio, non nella parte della tenuta in cui
 * cambia solo la luce. Qui la stessa cosa sul JVM, con i pezzi veri: la superficie fisica del grappolo fermo
 * ([PhysicsLiquidSurface], guidata «a fotogramma» come la guida `FluidGlassRestPlane`), la regola con cui il nodo rifà
 * gli effetti ([BackdropEffectsUpdates], con un osservatore di Compose al posto di `observeReads`), il bagliore vero del
 * contenitore ([FingerGlow]) su un orologio a 120 Hz, e la sonda del corpo ([glassBodyProbe], un giro per catena, come
 * l'evento `installed` del dispositivo). Sul JVM il corpo esce dopo la sonda, prima di compilare: su Robolectric il
 * programma a otto lastre è troppo grande.
 *
 * 1. Solo la luce: un secondo di bagliore che sale, zero catene nuove.
 * 2. La pressione: la sagoma del primo tondo cresce fino al ×1,35 del gel; la catena si rifà (è la flessione di F6-a), e
 *    il **materiale** resta quello di riposo per tutti e tre.
 * 3. Di nuovo solo la luce, col tondo cresciuto: zero catene nuove.
 * 4. Il controllo: senza il lato corto a riposo (com'era prima della correzione) il tondo cresciuto passa la soglia dei
 *    grandi (48 dp) e il velo del grappolo cambia.
 */
class ClusterPressChainTest {

  private class Frames(private val scheduler: TestCoroutineScheduler) : MonotonicFrameClock {
    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
      delay(FrameMs)
      return onFrame(scheduler.currentTime * 1_000_000L)
    }
  }

  /** Ciò che di un corpo installato non è geometria. */
  private data class Material(
    val veil: Color,
    val veilAdapt: Float,
    val blurPx: Float,
    val saturation: Float,
    val mix: Float,
    val thickness: Float,
    val refraction: Float,
    val dome: Float,
    val tint: Color,
    val shade: Color,
    val dim: Float,
    val padding: Float,
  )

  private fun GlassDrawInputs.material() = Material(
    veilColor, veilAdapt, blurPx, saturation, mix, thickness, refraction, dome, tintColor, bodyShade, dim, paddingPx,
  )

  private val density = 2f

  /** Tre tondi da 44 dp a 12 dp l'uno dall'altro, come in `TouchLab`, nel nodo del grappolo (pixel di layout). */
  private val restSlabs = List(3) { i ->
    val left = 40f + i * (88f + 24f)
    FluidForm.Slab(Rect(left, 56f, left + 88f, 144f), FluidCornerRadii(44f, 44f, 44f, 44f))
  }
  private val rest = PhysicsPlaneShape(restSlabs, nominalBlend = 0f, neck = -1f, resting = true)
  private val restShort = physicsFormShortPx(rest.form())

  /** Il grappolo col primo tondo premuto alla scala [scale]; [restRecipe] è la correzione di F6-c. */
  private fun pressed(scale: Float, restRecipe: Boolean) = PhysicsPlaneShape(
    restSlabs.mapIndexed { i, slab ->
      if (i == 0) fluidGlassPressedSlab(slab, FluidPressTransform(scale, scale, 0f, 0f)) else slab
    },
    nominalBlend = 0f,
    neck = -1f,
    resting = true,
    recipeShortPx = if (restRecipe) restShort else Float.NaN,
  )

  private val observer = SnapshotStateObserver { it() }.also { it.start() }
  private val scope = object : BackdropEffectScopeImpl() {
    override val shape: Shape = RectangleShape
  }
  private val key = Any()
  private var stale = false
  private var rebuilds = 0
  private lateinit var effects: BackdropEffectScope.() -> Unit
  private val updates = BackdropEffectsUpdates(
    observe = { block -> observer.observeReads(key, { stale = true }, block) },
    rebuild = {
      scope.apply(effects)
      rebuilds++
    },
  )

  @After fun stop() {
    observer.stop()
    observer.clear()
  }

  /** Il draw del nodo, a taglia ferma. */
  private fun draw() {
    val size = Size(400f, 200f)
    val changed = scope.size != size
    if (changed) scope.size = size
    updates.onDraw(changed)
  }

  /** Compose applica: se la superficie aveva letto qualcosa che è cambiato, la catena è sporca. */
  private fun apply() {
    Snapshot.sendApplyNotifications()
    if (stale) {
      stale = false
      updates.invalidate()
    }
  }

  @Test
  fun theHeldLightLeavesTheClusterChainAloneAndThePressKeepsItsMaterial() = runTest {
    val job = SupervisorJob()
    val frames = CoroutineScope(coroutineContext.minusKey(Job) + job + Frames(testScheduler))
    val shape = mutableStateOf(rest)
    val state = FluidPhysicsState(rest.form()).apply { this.density = this@ClusterPressChainTest.density }
    state.driveByFrame({ shape.value }, { 1f })
    val surface = PhysicsLiquidSurface(
      state = state,
      role = GlassRole.Floating,
      material = PhysicsMaterialSource(null, GlassDefaults.buttonOptics().sanitized()),
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
      layoutWidth = { 400f },
      onPanel = { false },
    )
    effects = surface.effects
    val bodies = mutableListOf<Material>()
    val previous = glassBodyProbe
    glassBodyProbe = { _, inputs -> bodies += inputs.material() }
    fun frame() {
      advanceTimeBy(FrameMs)
      runCurrent()
      apply()
      draw()
    }
    try {
      Snapshot.notifyObjectsInitialized()
      updates.invalidate()
      apply()
      repeat(3) { draw() }
      apply()
      assertTrue("controllo positivo: il grappolo ha un corpo", bodies.isNotEmpty())
      val restMaterial = bodies.last()

      // 1. Solo la luce: il dito scende sul primo tondo, il bagliore sale per un secondo e arriva ai vicini.
      val glow = FingerGlow(frames)
      val finger = Any()
      val atRest = rebuilds
      val lights = mutableSetOf<FingerGlowLevels>()
      glow.down(finger, restSlabs[0].frame, restSlabs[0].frame.center, reduce = false)
      repeat(130) {
        frame()
        lights += glow.levels
      }
      assertTrue("la luce doveva cambiare a ogni fotogramma: ${lights.size} livelli", lights.size > 30)
      assertTrue("la luce doveva arrivare ai vicini", glow.levels.neighbours > 0.99f)
      assertEquals("solo luce: nessuna catena nuova", atRest, rebuilds)

      // 2. La pressione: la sagoma del primo tondo cresce fino a ×1,35.
      bodies.clear()
      for (k in 1..20) {
        shape.value = pressed(1f + 0.35f * k / 20f, restRecipe = true)
        frame()
      }
      val grown = rebuilds
      assertTrue("la crescita è sagoma: la catena si rifà (${grown - atRest} volte)", grown > atRest)
      val changed = bodies.filter { it != restMaterial }
      assertTrue(
        "il materiale del grappolo resta quello di riposo (${changed.size} su ${bodies.size}): riposo $restMaterial, " +
          "primo diverso ${changed.firstOrNull()}",
        changed.isEmpty(),
      )

      // 3. Di nuovo solo la luce, col tondo cresciuto e fermo: il rilascio del bagliore, mezzo secondo.
      glow.up(finger)
      repeat(70) { frame() }
      assertEquals("il bagliore che si spegne", 0f, glow.levels.local, 0f)
      assertEquals("solo luce, col tondo cresciuto: nessuna catena nuova", grown, rebuilds)

      // 4. Il controllo: com'era prima della correzione il tondo cresciuto cambiava il velo di tutto il grappolo.
      bodies.clear()
      shape.value = pressed(1.35f, restRecipe = false)
      frame()
      assertTrue("il controllo ha installato un corpo", bodies.isNotEmpty())
      assertNotEquals("senza il lato corto a riposo il velo cambia: la prova sa vedere il difetto", restMaterial.veil, bodies.last().veil)
      println("F6-c: luce sola 0 catene; crescita ${grown - atRest} catene col materiale di riposo; velo prima ${restMaterial.veil}, " +
        "senza correzione ${bodies.last().veil}")
    } finally {
      glassBodyProbe = previous
      job.cancel()
    }
  }

  private companion object {
    const val FrameMs = 8L
    val NeutralTint = GlassTint(
      overlay = Color.Transparent,
      fallback = Color.Transparent,
      hairline = Color.Transparent,
      mode = GlassTintMode.Neutral,
    )
  }
}
