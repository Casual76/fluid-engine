package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentAnchor
import dev.antigravity.fluidengine.ui.fluid.FluidGlassPlaneDriver
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSwell
import dev.antigravity.fluidengine.ui.fluid.FluidGlassTransition
import dev.antigravity.fluidengine.ui.fluid.GlassContentLook
import dev.antigravity.fluidengine.ui.fluid.GlassPieceContent
import dev.antigravity.fluidengine.ui.fluid.fluidGlassContentFlightOf
import dev.antigravity.fluidengine.ui.fluid.icons.*
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneMotion
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneTransition
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il «+» della barra in basso che resta al suo posto mentre il suo gruppo si richiude (e si riapre) va col bordo del suo
 * vetro, come in Apple (scelte-b, scelta di Alessio del 19 novembre 2026). La barra in alto resta com'era (F4A).
 *
 * Il riferimento: WWDC25 219, Foto, Raccolta → Foto (f8551 l'ultimo fermo; la trascrizione non dice di rallentamenti,
 * 29,97 fps a tempo vero), il centro del «+» misurato fotogramma per fotogramma in `sessioni/scelte-b/rif/` (H = 164 px):
 * fermo fino a 100 ms, poi a destra fino a +0,162 H a 300 ms, di nuovo al posto a ~500, −0,015 H a ~570.
 */
class FluidToolbarEdgeRideTest {
  private val density = 3f
  private val width = 400f
  private val top = 32f
  private val noop: () -> Unit = {}

  /** Il «+» di Apple dal suo posto, in H, ai tempi veri dei fotogrammi (ms dall'ultimo fermo). */
  private val apple = listOf(
    100.1 to 0.001f, 133.5 to 0.012f, 166.8 to 0.028f, 200.2 to 0.067f, 233.6 to 0.109f, 266.9 to 0.155f,
    300.3 to 0.162f, 333.7 to 0.147f, 367.0 to 0.115f, 400.4 to 0.079f, 433.8 to 0.045f, 467.1 to 0.017f,
    500.5 to -0.002f, 533.9 to -0.013f, 567.2 to -0.015f, 600.6 to -0.014f,
  )

  private fun scene(photo: Boolean, split: Boolean = false) = buildFluidToolbar(null) {
    group("comandi", placement = FluidToolbarPlacement.BottomBar) {
      button("elenco", if (photo) FluidIcons.Search else FluidIcons.FormatListBulleted, "Elenco", onClick = noop)
      flexibleSpacer()
      if (!photo) {
        button("cuore", FluidIcons.Favorite, "Preferito", onClick = noop)
        button("condividi", FluidIcons.IosShare, "Condividi", onClick = noop)
        if (split) spacer()
      }
      button("aggiungi", FluidIcons.Add, "Cambia gruppo", onClick = noop)
    }
  }

  private fun resolve(declaration: FluidToolbarDeclaration, bar: FluidToolbarBar): FluidToolbarResolved {
    val metrics = if (bar == FluidToolbarBar.Bottom) fluidBottomBarMetrics() else fluidToolbarMetrics()
    val model = fluidToolbarModel(declaration, textWidthDp = { 0f }, tintOf = { fluidToolbarTint(it, Color.Blue, Color.Red) })
    return resolveFluidToolbar(model, width, metrics = metrics, bar = bar)
  }

  /** Il piano come lo vede il contenitore: un pezzo per gruppo, con l'identità del suo vetro. */
  private fun plane(resolved: FluidToolbarResolved, ids: Map<FluidToolbarGroupId, FluidToolbarGlassId>, rtl: Boolean): GlassPlaneState {
    val h = resolved.metrics.buttonWidthDp
    val pieces = resolved.groups.map { group ->
      val id = ids.getValue(group.id)
      val left = if (rtl) width - group.leftDp - group.widthDp else group.leftDp
      val bounds = Rect(left * density, top * density, (left + group.widthDp) * density, (top + h) * density)
      GlassPlanePiece(id.toString(), bounds, FluidCornerRadii.all(bounds.height / 2), id = id,
        transition = if (fluidToolbarGlassTransition(group) == FluidGlassTransition.MaterializeWhenAlone)
          GlassPlaneTransition.MaterializeWhenAlone else GlassPlaneTransition.MatchedGeometry,
        contentKey = fluidToolbarGlassContentKey(group))
    }
    return GlassPlaneState(pieces, 16f * density, Rect(0f, 0f, width * density, 108f * density))
  }

  private class Run(val trip: FluidToolbarTransfer?, val centers: List<Pair<Double, Float>>, val anchored: List<Pair<Float, Float>>)

  /**
   * Da [from] a [to] alla barra [bar]: il viaggio del «+» col conducente del piano di produzione, a 120 Hz. Ogni centro è
   * quello che disegna `FluidToolbarTravellingButton` (posto fermo più il bordo d'ancoraggio), in dp dal posto; accanto lo
   * spostamento che il contenuto ancorato allo stesso bordo riceve da [fluidGlassContentFlightOf].
   */
  private fun run(from: FluidToolbarDeclaration, to: FluidToolbarDeclaration, bar: FluidToolbarBar, rtl: Boolean = false): Run {
    val memory = FluidToolbarMemory()
    val before = resolve(from, bar)
    val after = resolve(to, bar)
    memory.update(before, FluidToolbarMotion.Full, ridesEdge = bar == FluidToolbarBar.Bottom)
    val startIds = memory.glassIds
    val origin = plane(before, startIds, rtl)
    memory.update(after, FluidToolbarMotion.Full, ridesEdge = bar == FluidToolbarBar.Bottom)
    val target = plane(after, memory.glassIds, rtl)
    val trip = memory.transfers["aggiungi"]
    val element = after.element("aggiungi")!!
    val restPx = fluidToolbarIconCenterPx(after, element, density, rtl)
    val group = after.groups.first { it.id == element.groupId }
    val place = target.pieces.first { it.id == memory.glassIds.getValue(group.id) }.bounds
    val driver = FluidGlassPlaneDriver()
    val motion = GlassPlaneMotion()
    val centers = ArrayList<Pair<Double, Float>>()
    val anchored = ArrayList<Pair<Float, Float>>()
    for (index in -3..240) {
      val ms = index * 1000.0 / 120.0
      driver.step(10_000_000_000L + (index * 1_000_000_000L / 120L), if (index < 0) origin else target, motion)
      if (index < 0) continue
      val flying = driver.frame?.pieces?.firstOrNull { it.piece.id == trip?.glass }
      val shift = if (flying == null) 0f else trip?.ride?.shiftPx(flying.bounds.left, flying.bounds.right, width, density, rtl) ?: 0f
      val center = trip?.center(restPx, restPx, null, null, flying?.phase ?: 1f, shift) ?: restPx
      centers += ms to (center - restPx) / density
      if (flying != null && trip?.ride != null) {
        val anchor = if (group.side == FluidToolbarSide.Trailing) FluidGlassContentAnchor.End else FluidGlassContentAnchor.Start
        val flight = fluidGlassContentFlightOf(flying, GlassPieceContent.Rest, GlassContentLook.Sharp.copy(alpha = 0.999f),
          place, FluidGlassSwell.None, anchor = anchor, layoutDirection = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr)
        if (flight != null) anchored += shift to flight.offset.x
      }
    }
    return Run(trip, centers, anchored)
  }

  private fun Run.at(ms: Double): Float = centers.minBy { abs(it.first - ms) }.second

  @Test fun `in basso il piu che resta va col bordo del suo vetro, fase per fase come Apple`() {
    val shrink = run(scene(photo = false), scene(photo = true), FluidToolbarBar.Bottom)
    val ride = requireNotNull(shrink.trip?.ride) { "in basso il più ha un bordo da seguire" }
    assertTrue("ancorato al bordo di fine", ride.end)
    val h = fluidBottomBarMetrics().buttonWidthDp
    // Fermo dove Apple lo tiene fermo: nei primi 100 ms il vetro non è ancora partito.
    for ((ms, offset) in shrink.centers) if (ms <= 100.0) assertEquals("fermo a $ms ms", 0f, offset, 0.02f * h)
    // Fase per fase entro 0,03 H dalla clip a tempo vero (±1 fotogramma di Apple sul t0).
    for ((ms, expected) in apple) {
      assertEquals("a ${ms.toInt()} ms: noi ${shrink.at(ms) / h} H, Apple $expected H", expected * h, shrink.at(ms), 0.03f * h)
    }
    // A metà del viaggio, a 300 ms: il colmo, a destra (+0,155 H del trascinamento del piano contro +0,162 di Apple).
    val peak = shrink.centers.maxBy { it.second }
    assertTrue("il colmo è fra 250 e 350 ms: ${peak.first}", peak.first in 250.0..350.0)
    assertEquals(0.162f * h, peak.second, 0.02f * h)
    // E alla fine è al suo posto, al pixel.
    assertEquals(0f, shrink.centers.last().second, 0.01f)
    // È la regola del contenuto che resta nel gruppo: lo stesso spostamento del contenuto ancorato al bordo.
    assertTrue(shrink.anchored.size > 40)
    for ((ours, content) in shrink.anchored) assertEquals(content, ours, 0.5f)
  }

  @Test fun `quando il gruppo si riapre il piu va dall'altra parte e torna`() {
    val grow = run(scene(photo = true), scene(photo = false), FluidToolbarBar.Bottom)
    assertNotNull(grow.trip?.ride)
    val h = fluidBottomBarMetrics().buttonWidthDp
    val low = grow.centers.minBy { it.second }
    assertTrue("il colmo è fra 250 e 350 ms: ${low.first}", low.first in 250.0..350.0)
    assertEquals(-0.155f * h, low.second, 0.03f * h)
    assertEquals(0f, grow.centers.last().second, 0.01f)
  }

  @Test fun `anche in RTL va col bordo di fine, che sta a sinistra`() {
    val ltr = run(scene(photo = false), scene(photo = true), FluidToolbarBar.Bottom)
    val rtl = run(scene(photo = false), scene(photo = true), FluidToolbarBar.Bottom, rtl = true)
    for ((a, b) in ltr.centers.zip(rtl.centers)) assertEquals("specchio a ${a.first} ms", -a.second, b.second, 0.02f)
    for ((ours, content) in rtl.anchored) assertEquals(content, ours, 0.5f)
  }

  @Test fun `in alto lo stesso cambio lascia il piu fermo, come ha scelto F4A`() {
    fun top(photo: Boolean) = buildFluidToolbar(null) {
      button("elenco", if (photo) FluidIcons.Search else FluidIcons.FormatListBulleted, "Elenco",
        placement = FluidToolbarPlacement.Leading, onClick = noop)
      if (!photo) {
        button("cuore", FluidIcons.Favorite, "Preferito", onClick = noop)
        button("condividi", FluidIcons.IosShare, "Condividi", onClick = noop)
      }
      button("aggiungi", FluidIcons.Add, "Aggiungi", onClick = noop)
    }
    val bar = run(top(false), top(true), FluidToolbarBar.Top)
    assertNotNull("il più viaggia anche in alto", bar.trip)
    assertNull("ma senza bordo da seguire", bar.trip!!.ride)
    for ((ms, offset) in bar.centers) assertEquals("fermo a $ms ms", 0f, offset, 0f)
  }

  @Test fun `staccato in un vetro suo il piu resta fermo`() {
    val split = run(scene(photo = false), scene(photo = false, split = true), FluidToolbarBar.Bottom)
    assertNull(split.trip?.ride)
    for ((ms, offset) in split.centers) assertEquals("fermo a $ms ms", 0f, offset, 0f)
  }

  @Test fun `con le animazioni ridotte nessun viaggio`() {
    val memory = FluidToolbarMemory()
    memory.update(resolve(scene(photo = false), FluidToolbarBar.Bottom), FluidToolbarMotion.Reduced, ridesEdge = true)
    memory.update(resolve(scene(photo = true), FluidToolbarBar.Bottom), FluidToolbarMotion.Reduced, ridesEdge = true)
    assertTrue(memory.transfers.isEmpty())
  }

  @Test fun `solo un bordo che resta lo stesso si segue`() {
    fun group(side: FluidToolbarSide, left: Float, w: Float) =
      FluidToolbarGroup(FluidToolbarGroupId(setOf("g")), side, emptyList(), left, w, sharedBackground = true)
    val trailing = fluidToolbarEdgeRide(group(FluidToolbarSide.Trailing, 240f, 144f), group(FluidToolbarSide.Trailing, 336f, 48f))
    assertEquals(FluidToolbarEdgeRide(336f, 48f, end = true), trailing)
    assertEquals(FluidToolbarEdgeRide(16f, 48f, end = false),
      fluidToolbarEdgeRide(group(FluidToolbarSide.Leading, 16f, 144f), group(FluidToolbarSide.Leading, 16f, 48f)))
    assertNull("al centro", fluidToolbarEdgeRide(group(FluidToolbarSide.Center, 100f, 144f), group(FluidToolbarSide.Center, 148f, 48f)))
    assertNull("il bordo si sposta", fluidToolbarEdgeRide(group(FluidToolbarSide.Trailing, 240f, 144f), group(FluidToolbarSide.Trailing, 300f, 48f)))
    assertNull("cambia lato", fluidToolbarEdgeRide(group(FluidToolbarSide.Leading, 16f, 48f), group(FluidToolbarSide.Trailing, 16f, 48f)))
    // Lo spostamento usa gli arrotondamenti con cui la barra posa il gruppo, nei due versi.
    val ride = FluidToolbarEdgeRide(336.4f, 48f, end = true)
    val left = (336.4f * 2.125f).let { kotlin.math.round(it) }
    val right = left + kotlin.math.round(48f * 2.125f)
    assertEquals(3f, ride.shiftPx(left, right + 3f, 400f, 2.125f, rtl = false), 0f)
    val rtlRight = kotlin.math.round(400f * 2.125f) - kotlin.math.round(336.4f * 2.125f)
    val rtlLeft = rtlRight - kotlin.math.round(48f * 2.125f)
    assertEquals(-3f, ride.shiftPx(rtlLeft - 3f, rtlRight, 400f, 2.125f, rtl = true), 0f)
  }
}
