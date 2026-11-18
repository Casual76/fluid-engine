package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.glass.field.*
import dev.antigravity.fluidengine.ui.glass.field.trace.*
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Il conducente e il volo del contenuto di produzione, con posizioni risolte dal modello della barra. */
class FluidToolbarContentAnchorTest {
  @Test fun ilPopDuranteIlTrasferimentoMantieneIlCentroMisuratoAncheAlPrimoFrameLungo() {
    val item = FluidToolbarItem("more", symbol = "more")
    val placed = 1519.5f
    val trip = FluidToolbarTransfer(FluidToolbarGlassId(FluidToolbarGroupId(setOf("more"))),
      true, false, 715f, 715f, placed, false, 44f, item)
    assertTrue(trip.sameSlot)
    for (phase in listOf(0f, .17f, .6f, 1f)) {
      assertEquals(placed, trip.center(placed, placed, null, null, phase), 0f)
    }
  }
  @Test fun `fermo in entrambi gli stati, il glifo non segue il rinculo della lastra`() {
    val item = FluidToolbarItem("more", symbol = "more")
    val trip = FluidToolbarTransfer(
      FluidToolbarGlassId(FluidToolbarGroupId(setOf("more"))),
      leavesSingle = true, entersSingle = false, startDp = 362f, endDp = 362f,
      interruptedCenter = null, softened = false, targetWidthDp = 88f, startItem = item,
    )
    for (frame in 0..120) {
      val fraction = frame / 120f
      assertEquals(769.5f, trip.center(769.5f, 769.5f, 768.5f + fraction * 3f, 44f + fraction * 44f, fraction), 0f)
    }
  }

  @Test fun `il layer del glifo usa il centro realmente posato alla densita del tablet`() {
    // 44 dp a 2,125 = 94 px di slot. Il glifo di 26 dp ne misura 55.
    val slotWidth = 94
    val glyphWidth = 55
    assertEquals(47.5f, fluidToolbarPlacedIconCenterPx(slotWidth, glyphWidth), 0f)
    assertEquals(0.5f, fluidToolbarPlacedIconCenterPx(slotWidth, glyphWidth) - slotWidth / 2f, 0f)
  }

  @Test fun `la selezione usa la stessa curva nei due versi dello scambio`() {
    val phases = (0..120).map { FluidToolbarSelectionEasing.transform(it / 120f) }
    assertEquals(0f, phases.first(), 0f)
    assertEquals(1f, phases.last(), 0f)
    assertTrue(phases.zipWithNext().all { (before, after) -> after >= before })
    assertTrue(phases.any { it > 0f && it < 1f })
  }
  private val metrics = fluidToolbarMetrics()
  private val width = 240f
  private val height = 108f
  private val top = 32f

  private fun resolved(count: Int, side: FluidToolbarSide): FluidToolbarResolved {
    val ids = if (side == FluidToolbarSide.Leading) listOf("resta", "nuova-a", "nuova-b") else listOf("nuova-a", "nuova-b", "resta")
    val items = if (count == 1) listOf("resta") else ids
    return resolveFluidToolbar(FluidToolbarModel(items.map {
      FluidToolbarItem(it, symbol = it, placement = if (side == FluidToolbarSide.Leading) FluidToolbarPlacement.Leading else FluidToolbarPlacement.Trailing)
    }), width, metrics = metrics)
  }

  private fun plane(count: Int, side: FluidToolbarSide, density: Float, direction: LayoutDirection): GlassPlaneState {
    val group = resolved(count, side).groups.single()
    val left = if (direction == LayoutDirection.Ltr) group.leftDp else width - group.leftDp - group.widthDp
    val bounds = Rect(left * density, top * density, (left + group.widthDp) * density, (top + metrics.buttonWidthDp) * density)
    return GlassPlaneState(listOf(GlassPlanePiece("gruppo", bounds, FluidCornerRadii.all(bounds.height / 2f), id = "gruppo")),
      metrics.groupSpacingDp * density, Rect(0f, 0f, width * density, height * density))
  }

  private fun anchor(side: FluidToolbarSide) = if (side == FluidToolbarSide.Leading) FluidGlassContentAnchor.Start else FluidGlassContentAnchor.End

  @Test fun `un gruppo da una a tre icone e ritorno non fa scivolare quella che resta, che va solo col suo vetro, anche RTL`() {
    // F4A.2b: l'icona che resta non scivola dentro il vetro che cambia larghezza (al centro scivolava di 44 dp). F4A.5b:
    // il vetro intero è trascinato dal bordo che si muove e torna (`FluidMotion.PlaneResizeDrag`, le due clip di Apple a
    // tempo vero: il «+» va a destra di 0,16 H e torna), e l'icona va con lui, non oltre; alla fine è al suo posto.
    for (direction in LayoutDirection.entries) for (side in listOf(FluidToolbarSide.Leading, FluidToolbarSide.Trailing)) {
      for (density in listOf(1f, 1.5f, 2f, 3f, 3.5f)) {
        val driver = FluidGlassPlaneDriver()
        val motion = GlassPlaneMotion()
        var peak = 0f
        var oldPeak = 0f
        var last = Offset.Zero
        val drag = FluidMotion.PlaneResizeDrag * metrics.buttonWidthDp * density
        for (index in -3..300) {
          val count = if (index in 0 until 120) 3 else 1
          val state = plane(count, side, density, direction)
          driver.step(10_000_000_000L + glassTraceTimeNanos(index, 120), state, motion)
          val flying = driver.frame?.pieces?.single() ?: continue
          val place = state.pieces.single().bounds
          val flight = fluidGlassContentFlightOf(flying, GlassPieceContent.Rest, GlassContentLook.Sharp, place,
            FluidGlassSwell.None, anchor = anchor(side), layoutDirection = direction)!!
          peak = maxOf(peak, abs(flight.offset.x), abs(flight.offset.y))
          oldPeak = maxOf(oldPeak, abs(flying.bounds.center.x - place.center.x))
          last = flight.offset
          assertEquals(GlassContentLook.Sharp, flight.look)
          // L'ancora cambia solo il volo: il ritaglio continua a essere la lastra in viaggio.
          assertEquals(flying.bounds.translate(-place.left, -place.top), flight.clip)
          // Il bordo d'ancoraggio non ha strada da fare: lo sposta solo il trascinamento, che sposta tutto il vetro.
          val edge = if ((side == FluidToolbarSide.Leading) == (direction == LayoutDirection.Ltr)) flying.bounds.left - place.left
            else flying.bounds.right - place.right
          assertEquals("l'icona va col bordo del suo vetro", edge, flight.offset.x, 0.001f)
        }
        assertTrue("$side $direction @$density: spostamento $peak px, trascinamento al più $drag", peak <= drag + 0.5f)
        assertTrue("alla fine al suo posto: $last", abs(last.x) <= 0.5f && abs(last.y) <= 0.5f)
        assertTrue("la controprova al centro deve riprodurre il difetto", oldPeak >= 43.9f * density)
        println("1-3-1 $side $direction densita=$density: picco=$peak px (trascinamento), centro=$oldPeak px")
      }
    }
  }

  @Test fun `il centro di serie conserva esattamente F3_12a anche nelle nascite e confluenze`() {
    val one = plane(1, FluidToolbarSide.Trailing, 3f, LayoutDirection.Ltr)
    val neighbour = one.pieces.single().copy(key = "nuovo", id = "nuovo", bounds = one.pieces.single().bounds.translate(-168f, 0f))
    val two = GlassPlaneState(one.pieces + neighbour, one.spacing, one.bounds)
    for ((from, to) in listOf(one to two, two to one)) {
      val journey = GlassPlaneJourney(from, to)
      for (index in 0..120) for (flying in journey.frame(index * journey.durationMs / 120).pieces) {
        val place = (to.pieces + from.pieces).first { it.key == (flying.flightId.targetKey ?: flying.flightId.sourceKey) }.bounds
        for (role in listOf(GlassPieceContent.Rest, GlassPieceContent(GlassPieceContentRole.Born), GlassPieceContent(GlassPieceContentRole.Exit), GlassPieceContent.Hidden)) {
          val look = GlassContentLook.Sharp.copy(alpha = 0.99f)
          val current = fluidGlassContentFlightOf(flying, role, look, place, FluidGlassSwell.None)!!
          val old = if (role.role == GlassPieceContentRole.Hidden) Offset.Zero else flying.bounds.center - place.center
          assertEquals(old, current.offset)
          assertEquals(current.offset, fluidGlassContentFlightOf(flying, role, look, place, FluidGlassSwell.None,
            anchor = FluidGlassContentAnchor.Center, layoutDirection = LayoutDirection.Rtl)!!.offset)
        }
      }
    }
  }

  @Test fun `l'ancora rispetta l'ordine degli attributi e non passa il vetro successivo`() {
    assertEquals(FluidGlassContentAnchor.Center, fluidGlassAttributesOf(Modifier).contentAnchor)
    val start = FluidGlassContentAnchorElement(FluidGlassContentAnchor.Start)
    val end = FluidGlassContentAnchorElement(FluidGlassContentAnchor.End)
    assertEquals(FluidGlassContentAnchor.End, fluidGlassAttributesOf(Modifier.then(start).then(end)).contentAnchor)
    assertTrue(start.consumed && end.consumed)
    val before = FluidGlassContentAnchorElement(FluidGlassContentAnchor.Start)
    assertEquals(FluidGlassContentAnchor.Center, fluidGlassAttributesOf(Modifier.then(before).then(FluidGlassMarkerElement)).contentAnchor)
    assertFalse(before.consumed)
    assertEquals(FluidGlassContentAnchor.End, fluidGlassAttributesOf(Modifier.then(before).then(FluidGlassMarkerElement)
      .fluidGlassContentAnchor(FluidGlassContentAnchor.End)).contentAnchor)
  }

  @Test fun `la lente non trasla il contenuto anche con ancora al bordo`() {
    val one = plane(1, FluidToolbarSide.Trailing, 3f, LayoutDirection.Ltr)
    val journey = GlassPlaneJourney(GlassPlaneState(emptyList(), one.spacing, one.bounds), one)
    for (flying in journey.frame(150.0).pieces) {
      assertEquals(GlassPlaneChange.Materialize, flying.change)
      assertEquals(Offset.Zero, fluidGlassContentFlightOf(flying, GlassPieceContent.Rest, GlassContentLook.Sharp,
        one.pieces.single().bounds, FluidGlassSwell.None, anchor = FluidGlassContentAnchor.End)!!.offset)
    }
  }

  @Test fun `parole di serie italiane e inglesi e personalizzazione indipendente dal calendario`() {
    assertEquals("Altro", FluidLabels().more)
    assertEquals("Conferma", FluidLabels().confirm)
    assertEquals("More", FluidLabels.english().more)
    assertEquals("Confirm", FluidLabels.english().confirm)
    val custom = FluidLabels.english().copy(more = "Options", confirm = "Save")
    assertEquals("Options", custom.more)
    assertEquals("Save", custom.confirm)
    assertEquals("Confirm", custom.dateTimeConfirm)
  }

  /** Il vetro viene dal banco F3; qui si aggiungono solo i posti delle icone e il loro volo di produzione. */
  private fun trace(side: FluidToolbarSide, centered: Boolean, dark: Boolean): GlassTrace {
    val density = 3f
    val name = "gruppo-${if (side == FluidToolbarSide.Leading) "inizio" else "fine"}-${if (dark) "scuro" else "chiaro"}-${if (centered) "prima" else "ancora"}"
    val one = plane(1, side, density, LayoutDirection.Ltr)
    val three = plane(3, side, density, LayoutDirection.Ltr)
    val base = F38aTraces.build(F38aTraces.Scene(name, "gruppo 1-3-1, icona resta ferma, nuove sfocate", width, height, dark,
      one, listOf(F38aTraces.Step(0.0, three), F38aTraces.Step(1000.0, one)), emptyMap(), 240,
      density = density, spacingDp = metrics.groupSpacingDp, phase = "f4a-2b"))
    val driver = FluidGlassPlaneDriver()
    val motion = GlassPlaneMotion()
    val frames = base.frames.mapIndexed { index, frame ->
      val expanded = frame.tMs >= 0.0 && frame.tMs < 1000.0
      val place = (if (expanded) three else one).pieces.single().bounds
      driver.step(10_000_000_000L + glassTraceTimeNanos(index - 3, 120), if (expanded) three else one, motion)
      val flying = driver.frame?.pieces?.single()
      val swell = FluidGlassSwell(driver.journeyCharacter, driver.journeySwell, FluidMotion.PlaneSwellMaxDp * density, density)
      val flight = flying?.let { fluidGlassContentFlightOf(it, GlassPieceContent.Rest, GlassContentLook.Sharp, place, swell,
        anchor = if (centered) FluidGlassContentAnchor.Center else anchor(side)) }
      val offset = flight?.offset ?: Offset.Zero
      val element = LinkedHashMap(frame.elements.single())
      val ex = (element["x"] as Number).toFloat()
      val ey = (element["y"] as Number).toFloat()
      val members = resolved(3, side).elements
      val content = members.mapNotNull { member ->
        val stays = member.item.id == "resta"
        val elapsed = if (frame.tMs < 1000.0) frame.tMs else frame.tMs - 1000.0
        val look = if (stays) GlassContentLook.Sharp else if (frame.tMs < 0.0) return@mapNotNull null else
          glassContentFrame(elapsed, if (expanded) null else member.item.id, if (expanded) member.item.id else null).layers
            .lastOrNull()?.look ?: return@mapNotNull null
        val x = if (stays) resolved(if (expanded) 3 else 1, side).element("resta")!!.leftDp else member.leftDp
        linkedMapOf<String, Any?>("name" to member.item.id.toString(), "x" to x + offset.x / density - ex,
          "y" to top + offset.y / density - ey, "w" to metrics.buttonWidthDp, "h" to metrics.buttonWidthDp,
          "icon" to when (member.item.id) { "resta" -> "search"; "nuova-a" -> "share"; else -> "plus" },
          "iconSizeDp" to FluidMetrics.BarIconSize.value, "color" to if (dark) "#F2F2F7" else "#17171A",
          "alpha" to look.alpha, "scale" to look.scale,
          "blurDp" to glassContentBlurRadiusPx(glassContentSigmaPx(look.blur, FluidMetrics.BarIconSize.value * density)) / density,
          "marginDp" to 12,
        ).also { layer ->
          flight?.clip?.translate(place.left, place.top)?.let { clip ->
            layer["clip"] = linkedMapOf("x" to clip.left / density - ex, "y" to clip.top / density - ey,
              "w" to clip.width / density, "h" to clip.height / density, "radius" to flight.radii.topLeft / density)
          }
        }
      }
      element["content"] = content
      GlassTraceFrame(frame.tMs, listOf(element))
    }
    return GlassTrace(name, base.phase, density, base.theme, width, height, frames, base.fps, base.head)
  }

  @Test fun `esporta tracce del gruppo e del piano di serie solo su richiesta`() {
    val dir = GlassTraceOutput.requestedDir(System.getenv("GLASS_F4A2B_DIR"))
    for (side in listOf(FluidToolbarSide.Leading, FluidToolbarSide.Trailing)) for (dark in listOf(false, true)) for (centered in listOf(false, true)) {
      val trace = trace(side, centered, dark)
      assertEquals(244, trace.frames.size)
      dir?.let { trace.write(File(it, "${trace.name}/trace.json")) }
    }
    // Le scene di F3 aggiornate in f3-corr: il nuovo attributo assente deve lasciarle identiche.
    if (dir != null) for (scene in F38aTraces.scenes() + F38aTraces.appleScenes() + F38aTraces.f310Scenes()) {
      F38aTraces.build(scene).write(File(dir, "piano-serie/${scene.name}/trace.json"))
    }
  }
}
