package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.animation.core.spring
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import dev.antigravity.fluidengine.ui.fluid.FluidContextBirth
import dev.antigravity.fluidengine.ui.fluid.FluidContextBirthProfile
import dev.antigravity.fluidengine.ui.fluid.FluidMenuAnchorGap
import dev.antigravity.fluidengine.ui.fluid.FluidMenuDropSeedDiameter
import dev.antigravity.fluidengine.ui.fluid.FluidMenuDropSeedOverlap
import dev.antigravity.fluidengine.ui.fluid.FluidMenuEdgeMargin
import dev.antigravity.fluidengine.ui.fluid.FluidMenuInflationEdgeMin
import dev.antigravity.fluidengine.ui.fluid.FluidMenuJourney
import dev.antigravity.fluidengine.ui.fluid.FluidMenuPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.fluidContextBirthProgress
import dev.antigravity.fluidengine.ui.fluid.fluidContextBirthSlab
import dev.antigravity.fluidengine.ui.fluid.fluidContextLiftTarget
import dev.antigravity.fluidengine.ui.fluid.fluidDropInflation
import dev.antigravity.fluidengine.ui.fluid.fluidMenuDropSeed
import dev.antigravity.fluidengine.ui.fluid.solveFluidMenuPanel
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S4b sul Tab S9 (NO 6): dopo la scelta di una voce a meta' nascita il contestuale tornava da piccolo a grande (f83 -> f84 in
 * 33 ms), poi riprendeva a stringersi. Qui il congedo a meta' nascita nell'ordine dell'app, a 120 Hz: il verso cambia nella
 * composizione (`journey.retarget(0f)`), il fotogramma si disegna, e solo dopo parte la coroutine di `FluidContextBirth.close`
 * (la sagoma fermata, la molla di ritorno 0,72/900: a meta' apertura il ritorno e' quello liquido, non quello sul contorno). La
 * goccia la muove il conducente del banco ([TodayLiquidDriver]), come `runLiquid`. Si misura la sagoma disegnata (il piano
 * della superficie): nel ritorno non deve mai ricrescere.
 */
class ContestualeCongedoTest {
  private val d = 3f
  private val fps = 120

  private class Run(
    val dismissMs: Float,
    val rows: List<String>,
    val worstRegrowth: Float,
    /** Il bersaglio della goccia al primo fotogramma del congedo, e la sagoma della nascita che si vedeva in quell'istante. */
    val firstTarget: Rect?,
    val shownAtDismiss: Rect?,
  )

  private fun run(dismissMs: Float): Run {
    val host = Size(411f * d, 891f * d)
    val row = Rect(16f * d, 476f * d, 395f * d, 540f * d)
    val margin = FluidMenuEdgeMargin.value * d
    val lifted = fluidContextLiftTarget(row, null, host, margin)
    val radius = FluidRadius.Group.value * d
    val panel = solveFluidMenuPanel(
      anchor = lifted,
      host = host,
      width = 250f * d,
      height = 196f * d,
      gapPx = FluidMenuAnchorGap.value * d,
      marginPx = margin,
      placement = FluidMenuPlacement.BesideAnchor,
    )
    val seed = fluidMenuDropSeed(
      row = lifted,
      panel = panel,
      touchX = row.center.x,
      diameterPx = FluidMenuDropSeedDiameter.value * d,
      overlapPx = FluidMenuDropSeedOverlap.value * d,
    )
    val journey = FluidMenuJourney()
    journey.prepare(
      anchor = null,
      hostOrigin = Offset.Zero,
      panel = panel,
      radius = radius,
      inflation = fluidDropInflation(panel.width, host.width, FluidMenuInflationEdgeMin.value * d),
      seed = seed,
      freshStart = true,
      liquidMenu = true,
    )
    journey.retarget(1f)
    val birth = FluidContextBirth().apply {
      this.radius = radius
      prepare(fresh = true)
    }
    val physics = FluidPhysicsState(FluidForm.circle(Offset(1f, 1f), 1f)).apply {
      density = d
      liquidAllowed = true
      maskInShader = true
      liquidBody = true
    }
    // `installMenuTravel` del contestuale.
    physics.dropAnchorReachDp = FluidMotion.DropAnchorReachDp
    physics.driveExternally(
      from = FluidForm.Slab(seed, FluidCornerRadii.all(seed.minDimension / 2f)),
      to = FluidForm.Slab(panel, FluidCornerRadii.all(radius)),
      progress = { journey.progress.value },
      startFrame = null,
      travelSlab = { birth.travel(journey) },
      travelWarp = { birth.warp(journey) },
    )
    val driver = TodayLiquidDriver(physics, d)

    val dismissFrame = (dismissMs * fps / 1000f).toInt()
    val tDismiss = glassTraceTimeMs(dismissFrame, fps).toFloat()
    // La velocita' del progresso d'apertura al congedo (quella che `retarget` passa alla molla di ritorno).
    val pDismiss = fluidContextBirthProgress(tDismiss)
    val vDismiss = (fluidContextBirthProgress(tDismiss + 1f) - fluidContextBirthProgress(tDismiss - 1f)) / 2f * 1000f
    var close: TodaySpring? = null
    var closeStart = 0L
    val rows = ArrayList<String>()
    val widths = ArrayList<Float>()
    var firstTarget: Rect? = null
    var shownAtDismiss: Rect? = null
    var k = 0
    while (k < dismissFrame + 60) {
      val nanos = glassTraceTimeNanos(k, fps)
      val tMs = glassTraceTimeMs(k, fps).toFloat()
      val closing = k >= dismissFrame
      if (k == dismissFrame) journey.retarget(0f)
      if (k == dismissFrame + 1) {
        // La coroutine di `close`: un fotogramma dopo il congedo. Il codice di oggi ferma qui la sagoma della nascita (se non
        // l'ha gia' fermata il primo disegno), e la molla di ritorno parte da qui.
        freezeLikeClose(birth, journey, tDismiss)
        close = TodaySpring(spring(FluidMotion.DropJourneyDamping, FluidMotion.MenuLiquidCloseStiffness), journey.progress.value, 0f, vDismiss)
        closeStart = nanos
      }
      val p = when {
        !closing -> fluidContextBirthProgress(tMs)
        close == null -> pDismiss
        else -> close!!.value(nanos - closeStart)
      }
      runBlocking {
        journey.progress.snapTo(p)
        if (!closing) birth.clock.snapTo(tMs)
      }
      physics.liquidAllowed = !journey.controlledReturn
      driver.frame(nanos)
      val drawn = physics.ensurePlan().bounds
      if (k == dismissFrame) {
        firstTarget = birth.travel(journey)?.frame
        shownAtDismiss = fluidContextBirthSlab(seed, panel, radius, birth.clock.value, leadForDrop = false)?.frame
      }
      if (closing) {
        widths += drawn.width
        rows += "%6.1f ms  p %.3f  %s  disegnata %6.1f x %6.1f  bersaglio %s".format(
          tMs - tDismiss, p, if (physics.liquid.quiet) "ferma" else "viva ",
          drawn.width, drawn.height,
          birth.travel(journey)?.frame?.let { "%6.1f x %6.1f".format(it.width, it.height) } ?: "-",
        )
      }
      k++
    }
    var worst = 0f
    for (i in 1 until widths.size) worst = maxOf(worst, (widths[i] - widths[i - 1]) / panel.width)
    return Run(tDismiss, rows, worst, firstTarget, shownAtDismiss)
  }

  /** Quello che fa `FluidContextBirth.close` al suo avvio: ferma la sagoma della nascita se il primo disegno non l'ha fatto. */
  private fun freezeLikeClose(birth: FluidContextBirth, journey: FluidMenuJourney, t: Float) {
    if (birth.frozen != null || t >= FluidContextBirthProfile.EndMillis) return
    val seed = journey.seed ?: return
    val frozen = FluidContextBirth::class.java.getDeclaredField("frozen").apply { isAccessible = true }
    frozen.set(birth, fluidContextBirthSlab(seed, journey.to, birth.radius, t))
    val progress = FluidContextBirth::class.java.getDeclaredField("frozenProgress").apply { isAccessible = true }
    progress.setFloat(birth, journey.progress.value.coerceAtLeast(0.001f))
  }

  @Test fun `congedo a meta' nascita, la sagoma non ricresce`() {
    val runs = listOf(300f, 400f, 480f, 560f, 640f, 760f).map(::run)
    for (r in runs) {
      println("S4b NO 6, congedo a %.0f ms: ricrescita peggiore %.1f%% del pannello".format(r.dismissMs, r.worstRegrowth * 100f))
    }
    val shown = runs.first { it.dismissMs >= 470f }
    println("S4b NO 6, i fotogrammi del ritorno dal congedo a %.0f ms:".format(shown.dismissMs))
    shown.rows.forEach { println("  $it") }
    for (r in runs) {
      assertTrue(
        "congedo a %.0f ms: la sagoma ricresce del %.1f%% del pannello".format(r.dismissMs, r.worstRegrowth * 100f),
        r.worstRegrowth <= 0.02f,
      )
    }
  }

  /**
   * Il primo fotogramma del congedo, quello che si disegna prima della coroutine di `close`: la goccia torna gia' dalla sagoma
   * della nascita che si vedeva in quell'istante (senza l'anticipo chiesto alla goccia), non dal pannello pieno. Fino a S4b
   * la sagoma si fermava solo al fotogramma dopo.
   */
  @Test fun `il primo fotogramma del congedo torna dalla sagoma che si vede`() {
    for (dismiss in listOf(200f, 300f, 480f)) {
      val r = run(dismiss)
      val target = requireNotNull(r.firstTarget)
      val shown = requireNotNull(r.shownAtDismiss)
      for ((what, a, b) in listOf(
        Triple("sinistra", target.left, shown.left), Triple("sopra", target.top, shown.top),
        Triple("destra", target.right, shown.right), Triple("sotto", target.bottom, shown.bottom),
      )) {
        assertTrue("congedo a %.0f ms, bordo %s: %.1f contro %.1f".format(r.dismissMs, what, a, b), kotlin.math.abs(a - b) <= 0.5f)
      }
    }
  }
}
