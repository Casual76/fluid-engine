package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.antigravity.fluidengine.foundation.memory.fluidSurfaceBytes
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import dev.antigravity.fluidengine.ui.fluidphysics.PlanModeSlabs
import dev.antigravity.fluidengine.ui.fluidphysics.journeyCaptureRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F8.8b · la finestra di un popover che nasce da una sorgente (F5B.1), col materiale `Liquid`.
 *
 * Sul Tab S9 (24 novembre 2026, il popover della capsula della data) il corpo di vetro non si vedeva per tutto il
 * viaggio, compariva all'arrivo con la sagoma ondulata della goccia e restava cosi', ferma, senza smerigliatura: la
 * pagina nitida sotto un velo. La finestra era una superficie a tutto schermo che catturava il fondale a tutto schermo;
 * in viaggio l'uscita della cattura non si riduce (si riduce solo il campione, `DrawBackdropNode.draw`), e da sola chiede
 * quasi venti MiB alla memoria del vetro. I menu, con la stessa fisica, catturano solo la regione del viaggio
 * (`menuSource`, `limitMissingJourney`). Dalla F8.8b il popover fa come loro.
 *
 * Qui, sul JVM: la regione che il popover cattura adesso e quanto costa rispetto allo schermo intero; l'ottica senza
 * velo; e che la geometria del viaggio del popover, guidata come in `FluidSourceJourney.drive` e passo per passo come
 * il conducente della goccia (`runLiquid`), si posa in un rettangolo arrotondato pulito, quello del pannello: una sagoma
 * ondulata ferma a riposo non puo' venire dalla fisica, solo da una cattura vecchia rigiocata.
 */
class FluidPopoverWindowCaptureTest {

  private val density = 2.625f

  // Il formato telefono del Tab S9 (1080 x 2340 a 420), con la capsula e il pannello dei fermi del 24 novembre.
  private val window = Size(1080f, 2340f)
  private val capsule = Rect(657f, 743f, 973f, 831f)
  private val pane = Rect(41f, 860f, 1030f, 1761f)
  private val radius = FluidPopoverRadius.value * density
  private val frame = 16_666_667L

  /** Il margine ottico che `fluidPhysicsSurface` aggiunge alla regione: ombra, tre sigma della sfocatura e lente. */
  private val opticsMargin = (24f + 3f * 12f + 18f * 2f) * density + 2f

  @Test
  fun `senza velo il popover ha l'ottica dei pannelli sopra una pagina nitida, col velo quella di sempre`() {
    assertEquals(GlassDefaults.panelOptics(overSharpPage = true), fluidPopoverWindowOptics(modal = false))
    assertEquals(GlassDefaults.panelOptics(), fluidPopoverWindowOptics(modal = true))
    assertTrue(
      "la smerigliatura senza velo non sale",
      fluidPopoverWindowOptics(modal = false).blurScale > fluidPopoverWindowOptics(modal = true).blurScale,
    )
  }

  @Test
  fun `la finestra del popover cattura la regione del viaggio, non lo schermo intero`() {
    assertTrue(FluidPopoverLimitsCapture)
    val region = journeyCaptureRegion(
      from = capsule, to = pane, source = capsule, opticsMargin = opticsMargin, size = window, scale = 1f,
      useRoute = true, limitMissingJourney = FluidPopoverLimitsCapture,
    ) { pane }
    assertNotNull("senza regione la cattura torna a tutto schermo", region)
    val r = region!!
    assertTrue("la regione $r non contiene il pannello", r.left <= pane.left && r.top <= pane.top &&
      r.right >= pane.right && r.bottom >= pane.bottom)
    assertTrue("la regione $r non contiene la capsula", r.top <= capsule.top && r.right >= capsule.right)

    // Quanto chiede alla memoria del vetro, in viaggio: l'uscita filtrata (due copie) piu' il campione a mezza scala.
    fun travelBytes(w: Int, h: Int): Long {
      fun bytes(width: Long, height: Long) = fluidSurfaceBytes(width, height, 4, 2, (width * 4 + 63) / 64 * 64)
      return bytes(w.toLong(), h.toLong()) + bytes((w + 1L) / 2, (h + 1L) / 2)
    }
    val full = travelBytes(window.width.toInt(), window.height.toInt())
    val windowed = travelBytes(r.width, r.height)
    println("F8.8b cattura in viaggio: schermo intero ${full / 1024 / 1024} MiB, regione $r ${windowed / 1024 / 1024} MiB")
    // A tutto schermo sono piu' di 24 MiB: piu' dell'intero tetto del vetro sul banco (24 MiB, F7.12c), prima ancora di
    // contare le catture della pagina, e una richiesta che in viaggio non si riduce. La regione del viaggio e' piu'
    // piccola, ma per un pannello grande resta gran parte dello schermo (la via si allarga del 20% del lato lungo): lo
    // dice il rapporto, e la ripresa di controllo legge `glassBudgetDenied`.
    assertTrue("schermo intero: $full", full > 24L * 1024 * 1024)
    assertTrue("regione: $windowed contro $full", windowed < full)
  }

  @Test
  fun `a pannello posato e viaggio dimenticato la cattura resta sul pannello`() {
    // Senza capi (lo stato prima della prima guida) la regione e' il pannello vivo e il suo margine ottico: largo quanto
    // il telefono (il margine passa i bordi), ma non lo schermo intero.
    val region = journeyCaptureRegion(
      from = null, to = null, source = null, opticsMargin = opticsMargin, size = window, scale = 1f,
      useRoute = true, limitMissingJourney = FluidPopoverLimitsCapture,
    ) { pane }
    assertNotNull(region)
    val r = region!!
    println("F8.8b cattura senza capi: regione $r")
    assertTrue("regione $r", r.top <= pane.top && r.bottom >= pane.bottom && r.height < window.height)
    assertTrue("regione $r", r.width.toLong() * r.height * 3 < window.width.toLong() * window.height.toLong() * 2)
  }

  @Test
  fun `la goccia del viaggio dalla capsula si posa nel rettangolo arrotondato del pannello`() {
    val state = FluidPhysicsState(FluidForm.circle(androidx.compose.ui.geometry.Offset(1f, 1f), 1f)).apply {
      liquidAllowed = true
      maskInShader = true
      liquidBody = true
      this.density = this@FluidPopoverWindowCaptureTest.density
    }
    val clock = TargetBasedAnimation(FluidMotion.PopoverTravelOpen, Float.VectorConverter, 0f, 1f, AnimationVector1D(0f))
    var t = 0f
    // Come `FluidSourceJourney.drive`: la capsula (raggio mezzo lato corto) verso il pannello, col gonfiore del popover.
    val start = FluidForm.Slab(capsule, FluidCornerRadii.all(capsule.height / 2f))
    val end = FluidForm.Slab(pane, FluidCornerRadii.all(radius))
    val inflation = fluidMenuInflation(pane.width, pane.height, window.width, FluidMenuInflationEdgeMin.value * density,
      FluidMenuInflationReferenceHeight.value * density, FluidPopoverWindowPopGain, FluidPopoverPeakOvershoot)
    state.driveExternally(
      from = start, to = end, progress = { t },
      overshootInflationX = inflation.x, overshootInflationY = inflation.y, startFrame = null,
      travelSlab = { t.takeIf { it < 1f }?.let { fluidModalSourceSlab(start, end, it) } },
    )
    // Il conducente della goccia: un passo a fotogramma finche' la goccia e' viva.
    var arrival = -1
    var quietAt = -1
    var wobbledAfterArrival = false
    for (i in 0 until 240) {
      val nanos = i * frame
      t = if (clock.isFinishedFromNanos(nanos)) 1f else clock.getValueFromNanos(nanos)
      val slab = state.dropRestSlabNow()!!
      val alive = state.stepDrop(slab, nanos)
      if (t >= 1f && arrival < 0) arrival = i
      if (arrival >= 0 && !state.liquid.quiet) wobbledAfterArrival = true
      if (arrival >= 0 && !alive) {
        quietAt = i
        break
      }
    }
    assertTrue("il viaggio non e' arrivato", arrival > 0)
    assertTrue("la goccia non era viva all'arrivo: il caso non e' provato", wobbledAfterArrival)
    assertTrue("la goccia non si e' posata in 4 s", quietAt > 0)
    val settleMillis = (quietAt - arrival) * 1000 / 60
    println("F8.8b goccia: arrivo al fotogramma $arrival, posata $settleMillis ms dopo")
    // Sul tablet la sagoma ondulata restava ferma per piu' di due secondi: la fisica si posa ben prima.
    assertTrue("posata dopo $settleMillis ms", settleMillis < 1500)

    // A riposo: la lastra del pannello, un rettangolo arrotondato col suo raggio, senza goccia ne' ritaglio.
    assertTrue(state.liquid.quiet)
    val plan = state.ensurePlan()
    assertEquals(PlanModeSlabs, plan.mode)
    assertEquals(null, plan.dropRest)
    val round = plan.silhouetteRoundRect
    assertNotNull("a riposo la sagoma non e' un rettangolo arrotondato", round)
    assertEquals(pane.left, round!!.left, 0.5f)
    assertEquals(pane.top, round.top, 0.5f)
    assertEquals(pane.right, round.right, 0.5f)
    assertEquals(pane.bottom, round.bottom, 0.5f)
    for (corner in listOf(round.topLeftCornerRadius, round.topRightCornerRadius, round.bottomRightCornerRadius,
      round.bottomLeftCornerRadius)) {
      assertEquals(radius, corner.x, 0.5f)
      assertEquals(radius, corner.y, 0.5f)
    }
    assertEquals(end, state.restFormNow())
  }
}
