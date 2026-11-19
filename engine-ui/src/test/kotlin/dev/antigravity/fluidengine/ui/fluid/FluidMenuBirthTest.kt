package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Rect
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La nascita del menu dal tasto (B1 a) e dal gruppo (la graffetta, B1 b): le funzioni pure di `FluidMenuBirth.kt`, in
 * millisecondi dalla nascita. Il pannello si apre verso l'alto sopra il tasto, come le azioni della barra (`OverAnchor`).
 */
class FluidMenuBirthTest {
  private val panel = Rect(24f, 300f, 276f, 504f)
  private val radius = 22f

  /** Il tasto in basso a sinistra, sul margine del pannello. */
  private val button = Rect(24f, 458f, 70f, 504f)

  /** La capsula di tre tasti e il tasto premuto a destra. */
  private val capsule = Rect(24f, 458f, 162f, 504f)
  private val trigger = Rect(116f, 458f, 162f, 504f)

  private fun fractions(body: Rect) = floatArrayOf(
    (panel.bottom - body.bottom) / panel.height,
    (panel.bottom - body.top) / panel.height,
    body.width / panel.width,
  )

  @Test
  fun theButtonBirthStartsOnTheButtonAndEndsOnThePanel() {
    val start = requireNotNull(fluidButtonBirthSlab(button, panel, radius, 0f))
    assertEquals(button.left, start.frame.left, 0.01f)
    assertEquals(button.top, start.frame.top, 0.01f)
    assertEquals(button.right, start.frame.right, 0.01f)
    assertEquals(button.bottom, start.frame.bottom, 0.01f)
    assertEquals(button.minDimension / 2f, start.cornerRadii.topLeft, 0.01f)
    assertNull(fluidButtonBirthSlab(button, panel, radius, FluidButtonBirthProfile.EndMillis))
    // Un istante prima della fine e' gia' il pannello, col suo raggio: nessun salto quando la nascita finisce.
    val last = requireNotNull(fluidButtonBirthSlab(button, panel, radius, FluidButtonBirthProfile.EndMillis - 1f))
    assertEquals(panel.left, last.frame.left, 0.01f)
    assertEquals(panel.top, last.frame.top, 0.01f)
    assertEquals(panel.right, last.frame.right, 0.01f)
    assertEquals(panel.bottom, last.frame.bottom, 0.01f)
    assertEquals(radius, last.cornerRadii.topLeft, 0.01f)
  }

  @Test
  fun withoutTheLeadTheButtonBirthIsApplesShape() {
    val p = FluidButtonBirthProfile
    for (ms in listOf(66.7f, 100.1f, 133.5f, 166.8f, 200.2f, 266.9f, 333.7f)) {
      val body = requireNotNull(fluidButtonBirthSlab(button, panel, radius, ms, leadForDrop = false)).frame
      val (near, far, width) = fractions(body).toList()
      assertEquals("vicino a $ms", p.Near.at(ms), near, 0.002f)
      assertEquals("lontano a $ms", p.Far.at(ms), far, 0.002f)
      assertEquals("larghezza a $ms", p.Width.at(ms), width, 0.002f)
      // Il tasto sta sul margine del pannello: quel lato resta fermo e il menu cresce dall'altro, come il telefono.
      assertEquals("sinistro a $ms", panel.left, body.left, 0.01f)
    }
    // B1 (a) e' pieno a ~133 ms e supera: il corpo sale sopra la posa e si allarga del 3-4%.
    val peak = requireNotNull(fluidButtonBirthSlab(button, panel, radius, 166.8f, leadForDrop = false)).frame
    assertTrue(peak.top < panel.top - panel.height * 0.06f)
    assertTrue(peak.width > panel.width * 1.03f)
  }

  @Test
  fun theButtonCornersAreApplesDropNotACircle() {
    // A 33 ms la goccia di Apple ha gli angoli al 31% del lato corto, a 100 ms al 20%: un rettangolo molto arrotondato.
    val at33 = requireNotNull(fluidButtonBirthSlab(button, panel, radius, 33.4f, leadForDrop = false))
    assertEquals(0.31f, at33.cornerRadii.topLeft / at33.frame.minDimension, 0.02f)
    val at100 = requireNotNull(fluidButtonBirthSlab(button, panel, radius, 100.1f, leadForDrop = false))
    assertEquals(0.20f, at100.cornerRadii.topLeft / at100.frame.minDimension, 0.01f)
  }

  @Test
  fun theButtonProgressFollowsTheFarEdgeAndEndsExactlyAtOne() {
    assertEquals(0f, fluidButtonBirthProgress(0f), 0f)
    assertEquals(1f, fluidButtonBirthProgress(FluidButtonBirthProfile.EndMillis), 0f)
    assertTrue(fluidButtonBirthProgress(100.1f) > 0.9f)
    assertTrue(fluidButtonBirthProgress(166.8f) > 1.05f)
    var last = 0f
    for (ms in 0..100) {
      val v = fluidButtonBirthProgress(ms.toFloat())
      assertTrue("sale a $ms ms", v >= last - 1e-5f)
      last = v
    }
    for (target in listOf(0.1f, 0.4f, 0.8f, 0.99f)) {
      assertEquals(target, fluidButtonBirthProgress(fluidMenuBirthMillisAt(group = false, progress = target)), 0.002f)
    }
  }

  @Test
  fun theButtonInkComesInAtOnceAndTheGroupInkLikeTheContextMenu() {
    assertEquals(0f, fluidButtonBirthInk(0f), 0f)
    assertEquals(1f, fluidButtonBirthInk(33.4f), 0f)
    assertEquals(0f, fluidMenuBirthInk(group = true, ms = 66f), 0f)
    assertEquals(1f, fluidMenuBirthInk(group = true, ms = 117f), 0f)
  }

  @Test
  fun theGroupCapsuleGathersIntoADropOnTheButton() {
    val start = requireNotNull(fluidGroupBirthSlab(capsule, trigger, panel, radius, 0f))
    assertEquals(capsule, start.frame)
    assertEquals(FluidCornerRadii.all(capsule.minDimension / 2f), start.cornerRadii)
    // A 33 ms la capsula di Apple e' al 63% della sua larghezza; a 67 ms e' la goccia sul tasto.
    val at33 = requireNotNull(fluidGroupBirthSlab(capsule, trigger, panel, radius, 33.4f)).frame
    assertEquals(0.63f, at33.width / capsule.width, 0.08f)
    val drop = fluidGroupBirthDrop(trigger)
    val at67 = requireNotNull(fluidGroupBirthSlab(capsule, trigger, panel, radius, FluidGroupGatherMillis))
    assertEquals(fluidContextBirthSlab(drop, panel, radius, FluidGroupGatherMillis), at67)
    assertEquals(trigger.center.x, at67.frame.center.x, 2f)
    // Poi e' la nascita del contestuale col seme sul tasto: la graffetta.
    assertEquals(fluidContextBirthSlab(drop, panel, radius, 200f), fluidGroupBirthSlab(capsule, trigger, panel, radius, 200f))
    assertNull(fluidGroupBirthSlab(capsule, trigger, panel, radius, FluidContextBirthProfile.EndMillis))
  }

  @Test
  fun theGroupEggLeansWithItsTailOnTheButton() {
    // Il tasto a sinistra, come la graffetta: la coda dell'uovo sta sul tasto, la testa sul centro del bordo lontano.
    val left = Rect(24f, 458f, 70f, 504f)
    val slab = requireNotNull(fluidGroupBirthSlab(Rect(24f, 458f, 162f, 504f), left, panel, radius, 166.8f))
    val warp = requireNotNull(fluidGroupBirthWarp(left, panel, slab, 166.8f))
    assertEquals(left.center.x.coerceIn(slab.frame.left, slab.frame.right), warp.tailX, 0.01f)
    assertEquals(slab.frame.bottom, warp.tailY, 0.01f)
    assertEquals(slab.frame.center.x, warp.headX, 0.01f)
    assertTrue(warp.tailTaper > 0.3f)
    // Mentre la capsula si raccoglie la forma non la piega; la lente c'e' gia'.
    val gathering = requireNotNull(fluidGroupBirthSlab(capsule, trigger, panel, radius, 16f))
    val early = requireNotNull(fluidGroupBirthWarp(trigger, panel, gathering, 16f))
    assertTrue(early.headTaper < FluidContextBirthProfile.HeadTaper.at(16f))
    assertTrue(early.lens > 0.5f)
    assertNull(fluidGroupBirthWarp(trigger, panel, slab, FluidContextBirthProfile.WarpEndMillis))
  }

  @Test
  fun aHalfwayCloseFadesTheEggWhileTheReturnLeavesTheFrozenShape() {
    val slab = requireNotNull(fluidGroupBirthSlab(capsule, trigger, panel, radius, 166.8f))
    val full = requireNotNull(fluidGroupBirthWarpFading(trigger, panel, slab, 166.8f, q = 1f))
    assertEquals(fluidGroupBirthWarp(trigger, panel, slab, 166.8f), full)
    val half = requireNotNull(fluidGroupBirthWarpFading(trigger, panel, slab, 166.8f, q = 0.86f))
    assertTrue(half.tailTaper < full.tailTaper && half.tailTaper > 0f)
    assertNull(fluidGroupBirthWarpFading(trigger, panel, slab, 166.8f, q = 0.6f))
  }

  @Test
  fun theFingerChoosesTheRowItSeesWhileTheMenuIsBorn() {
    // Tre righe da 48 px; a meta' nascita il corpo e' staccato di 30 px verso l'alto e le voci sono all'80%.
    val rows = Rect(0f, 0f, 200f, 144f)
    val geometry = FluidMenuGeometry(
      cells = (0..2).map { Rect(0f, it * 48f, 200f, it * 48f + 48f) },
      enabled = listOf(true, true, true),
      panel = rows,
    )
    val map = FluidMenuContentMap(androidx.compose.ui.geometry.Offset(0f, -30f), 0.8f)
    fun seen(p: androidx.compose.ui.geometry.Offset) = rows.center + (p - rows.center) * 0.8f + map.translation
    // L'inversa della mappa del layer: il punto visto torna al suo posto a riposo; senza mappa non cambia niente.
    val p = androidx.compose.ui.geometry.Offset(40f, 100f)
    val back = fluidMenuPointerAtRest(seen(p), rows, map)
    assertEquals(p.x, back.x, 0.001f)
    assertEquals(p.y, back.y, 0.001f)
    assertEquals(p, fluidMenuPointerAtRest(p, rows, FluidMenuContentMap.Identity))
    // La terza riga si vede dove a riposo c'e' la seconda: senza la mappa il dito sceglierebbe quella.
    val third = seen(androidx.compose.ui.geometry.Offset(100f, 120f))
    assertEquals(1, fluidMenuCellAt(geometry, third))
    val picks = mutableListOf<Int>()
    val selection = FluidMenuSelection(touchSlop = 10f, onPick = { picks += it }).also {
      it.geometry = geometry
      it.pointerMap = { root -> fluidMenuPointerAtRest(root, rows, map) }
    }
    selection.handOffBegan(androidx.compose.ui.geometry.Offset(100f, 400f))
    selection.hover(third)
    assertEquals(2, selection.hovered)
    selection.release(third)
    assertEquals(listOf(2), picks)
  }

  @Test
  fun theFaceLeavesWithinApplesFirstFrames() {
    // La faccia del tasto e' spenta prima del fotogramma di Apple a 33 ms; le icone del gruppo sparite prima di 67 ms.
    assertEquals(0f, fluidMenuFaceAlpha(fluidMenuBirthFaceProgress(30f)), 0f)
    val u = fluidMenuBirthFaceProgress(55f) / FluidMotion.MenuGroupFaceProgressSpan
    assertEquals(0f, GlassContentCurves.alpha(GlassContentRole.Exit, u, blurAvailable = true), 0f)
    assertEquals(0f, fluidMenuBirthFaceProgress(0f), 0f)
  }
}
