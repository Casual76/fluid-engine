package dev.antigravity.fluidengine.ui.page

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSourceGeometry
import dev.antigravity.fluidengine.ui.fluid.fluidSheetZoomBlurFraction
import dev.antigravity.fluidengine.ui.fluid.fluidSheetZoomBounds
import dev.antigravity.fluidengine.ui.fluid.fluidSheetZoomVeil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La geometria dello zoom (F5B.3): sagome, dito, contenuto, raggi delle sorgenti, e i numeri del foglio dal tasto. */
class FluidZoomTest {
  private val density = Density(3f)
  private val full = FluidZoomShape(Rect(0f, 0f, 1080f, 2400f), FluidPageCorners(120f, 120f, 120f, 120f))
  private val card = FluidZoomShape(Rect(100f, 900f, 500f, 1400f), FluidPageCorners(40f, 40f, 40f, 40f))

  private fun assertRect(expected: Rect, actual: Rect, tolerance: Float = 0.01f, message: String = "") {
    assertEquals("$message sinistra", expected.left, actual.left, tolerance)
    assertEquals("$message alto", expected.top, actual.top, tolerance)
    assertEquals("$message destra", expected.right, actual.right, tolerance)
    assertEquals("$message basso", expected.bottom, actual.bottom, tolerance)
  }

  @Test fun `la sagoma va dalla sorgente all arrivo con i raggi`() {
    assertEquals(card, FluidZoom.lerp(card, full, 0f))
    assertEquals(full, FluidZoom.lerp(card, full, 1f))
    val half = FluidZoom.lerp(card, full, 0.5f)
    assertRect(Rect(50f, 450f, 790f, 1900f), half.bounds)
    assertEquals(80f, half.corners.topLeft, 0.01f)
    // Una molla che supera porta la sagoma appena oltre, sempre dritta.
    val over = FluidZoom.lerp(card, full, 1.03f)
    assertTrue(over.bounds.width > full.bounds.width && over.bounds.height > full.bounds.height)
  }

  @Test fun `la frazione di un viaggio sulla corsa`() {
    assertEquals(0f, FluidZoom.travelFraction(1350f, 1350f, 0f), 0f)
    assertEquals(1f, FluidZoom.travelFraction(0f, 1350f, 0f), 0f)
    assertEquals(0.5f, FluidZoom.travelFraction(200f, 100f, 300f), 1e-6f)
    assertEquals("partenza e arrivo uguali: finito", 1f, FluidZoom.travelFraction(5f, 300f, 300f), 0f)
  }

  @Test fun `sotto il dito il punto toccato resta sotto il dito e la pagina si rimpicciolisce`() {
    val pivot = Offset(30f, 1200f)
    var previous = 1f
    for (drag in listOf(0f, 50f, 200f, 378f, 800f, 1080f, 2000f)) {
      val held = FluidZoom.follow(full, full, drag, grabTravel = 0f, direction = 1, pivot = pivot, width = 1080f)
      val k = FluidZoom.followScale(drag, 1080f)
      assertTrue("si rimpicciolisce con la distanza ($drag)", k <= previous)
      previous = k
      // Il punto della pagina che era sotto il dito: (pivot) -> pivot + drag.
      val mappedX = held.bounds.left + (pivot.x - full.bounds.left) * k
      val mappedY = held.bounds.top + (pivot.y - full.bounds.top) * k
      assertEquals("x sotto il dito a $drag", pivot.x + drag, mappedX, 0.01f)
      assertEquals("y sotto il dito a $drag", pivot.y, mappedY, 0.01f)
      assertEquals("i raggi restano quelli della pagina", full.corners, held.corners)
    }
    assertEquals(1f - FluidZoom.FollowShrink, FluidZoom.followScale(1080f, 1080f), 1e-6f)
    // In RTL la pagina esce a sinistra, sempre sotto il dito.
    val rtl = FluidZoom.follow(full, full, 100f, 0f, direction = -1, pivot = Offset(1050f, 1200f), width = 1080f)
    val k = FluidZoom.followScale(100f, 1080f)
    assertEquals(950f, rtl.bounds.left + (1050f - 0f) * k, 0.01f)
  }

  @Test fun `ripresa a meta ritorno il dito che torna indietro riapre la pagina`() {
    val base = FluidZoom.lerp(card, full, 0.6f)
    val grabTravel = 400f
    val pivot = Offset(500f, 900f)
    assertRect(base.bounds, FluidZoom.follow(base, full, 0f, grabTravel, 1, pivot, 1080f).bounds, 0.01f, "al contatto")
    val halfBack = FluidZoom.follow(base, full, -200f, grabTravel, 1, pivot, 1080f)
    assertEquals(FluidZoom.lerp(base, full, 0.5f), halfBack)
    val back = FluidZoom.follow(base, full, -400f, grabTravel, 1, pivot, 1080f)
    assertRect(full.bounds, back.bounds, 0.01f, "a corsa zero e' la pagina intera")
    assertEquals(full.corners.topLeft, back.corners.topLeft, 0.01f)
  }

  @Test fun `il contenuto copre la sagoma ancorato in alto al centro e ritorna con il ritaglio`() {
    val target = full.bounds
    val content = FluidZoom.content(card, target)
    val scale = maxOf(400f / 1080f, 500f / 2400f)
    assertEquals(scale, content.scale, 1e-5f)
    val c = target.center
    fun map(p: Offset) = Offset(c.x + content.scale * (p.x - c.x) + content.translation.x, c.y + content.scale * (p.y - c.y) + content.translation.y)
    assertEquals("la cima della pagina sulla cima della sagoma", card.bounds.top, map(Offset(540f, 0f)).y, 0.01f)
    assertEquals("il centro sul centro", card.bounds.center.x, map(Offset(540f, 0f)).x, 0.01f)
    val clip = FluidZoom.contentClip(card, target, content)
    assertEquals(card.bounds.left, map(clip.bounds.topLeft).x, 0.01f)
    assertEquals(card.bounds.top, map(clip.bounds.topLeft).y, 0.01f)
    assertEquals(card.bounds.right, map(clip.bounds.bottomRight).x, 0.01f)
    assertEquals(card.bounds.bottom, map(clip.bounds.bottomRight).y, 0.01f)
    assertEquals(40f / content.scale, clip.corners.topLeft, 0.01f)
    assertEquals(FluidZoomContent.Identity, FluidZoom.content(full, target))
  }

  @Test fun `i raggi della sorgente continui capsula e ad angoli`() {
    val size = Size(400f, 300f)
    val rounded = FluidZoom.cornersOf(RoundedCornerShape(CornerSize(24f)), size, density, LayoutDirection.Ltr)
    assertEquals(FluidPageCorners(24f, 24f, 24f, 24f), rounded)
    val fifty = CornerSize(50f)
    val continuous = FluidZoom.cornersOf(ContinuousCornerShape(fifty, fifty, fifty, fifty), size, density, LayoutDirection.Ltr)
    assertEquals("il raggio circolare equivalente", 50f * ContinuousCornerShape.RoundedEquivalentFactor, continuous.topLeft, 0.01f)
    val capsule = FluidZoom.cornersOf(FluidCapsuleShape, Size(132f, 132f), density, LayoutDirection.Ltr)
    assertEquals("una capsula resta mezzo lato", 66f, capsule.topLeft, 0.01f)
    assertEquals(FluidPageCorners.Zero, FluidZoom.cornersOf(RectangleShape, size, density, LayoutDirection.Ltr))
    // In RTL l'inizio e' a destra.
    val start = RoundedCornerShape(topStart = CornerSize(10f), topEnd = CornerSize(0f), bottomEnd = CornerSize(0f), bottomStart = CornerSize(0f))
    assertEquals(10f, FluidZoom.cornersOf(start, size, density, LayoutDirection.Ltr).topLeft, 0f)
    assertEquals(10f, FluidZoom.cornersOf(start, size, density, LayoutDirection.Rtl).topRight, 0f)
  }

  @Test fun `la sorgente nelle coordinate di chi disegna`() {
    val geometry = FluidTransitionSourceGeometry(Rect(160f, 1000f, 560f, 1500f), RoundedCornerShape(CornerSize(40f)))
    val shape = FluidZoom.shapeOf(geometry, Offset(60f, 100f), density, LayoutDirection.Ltr)
    assertEquals(card.bounds, shape.bounds)
    assertEquals(card.corners, shape.corners)
  }

  // --- Il foglio dal tasto (F5B.3) -----------------------------------------------------------------------------------

  @Test fun `il vetro del foglio nasce dalla sorgente e non esce dai lati`() {
    val button = Rect(60f, 2200f, 330f, 2332f)
    val sheet = Rect(15f, 200f, 1065f, 2385f)
    assertEquals(button, fluidSheetZoomBounds(button, sheet, 0f))
    assertEquals(sheet, fluidSheetZoomBounds(button, sheet, 1f))
    val over = fluidSheetZoomBounds(button, sheet, 1.03f)
    assertEquals("in larghezza mai oltre il foglio", sheet.left, over.left, 0.01f)
    assertEquals(sheet.right, over.right, 0.01f)
    assertEquals("in fondo mai sotto", sheet.bottom, over.bottom, 0.01f)
    assertTrue("in cima supera come il foglio che sale", over.top < sheet.top)
  }

  @Test fun `il foglio dal tasto diventa pagina mentre cresce come quello di Apple`() {
    // Sulla molla dello zoom: 50% a 100 ms, 65% a 133, 77% a 167 (284 f25411-f25413: vetro, meta', quasi bianco).
    assertTrue("vetro a 100 ms", fluidSheetZoomVeil(0.50f, 1f) < 0.1f)
    assertEquals("meta' a 133 ms", 0.5f, fluidSheetZoomVeil(0.655f, 1f), 0.08f)
    assertTrue("quasi pieno a 167 ms", fluidSheetZoomVeil(0.769f, 1f) > 0.85f)
    assertEquals("arrivato: il velo del detent", 1f, fluidSheetZoomVeil(1f, 1f), 0f)
    assertEquals("un detent parziale resta vetro", 0f, fluidSheetZoomVeil(0.9f, 0f), 0f)
    var previous = 0f
    for (i in 0..100) {
      val v = fluidSheetZoomVeil(i / 100f, 1f)
      assertTrue("continuo e senza tornare indietro", v >= previous && v - previous < 0.05f)
      previous = v
    }
  }

  @Test fun `il contenuto del foglio dal tasto e sfocato dentro e nitido a 300 ms`() {
    assertEquals("al primo fotogramma, del tutto", 1f, fluidSheetZoomBlurFraction(0f, 0f), 0f)
    assertTrue("a meta' strada ancora sfocato", fluidSheetZoomBlurFraction(150f, 0.85f) > 0.3f)
    assertEquals("a 300 ms, arrivato, nitido", 0f, fluidSheetZoomBlurFraction(300f, 1f), 0f)
    assertTrue("arrivato prima dei 300 ms resta un po' sfocato", fluidSheetZoomBlurFraction(220f, 1f) > 0f)
    assertTrue("alla chiusura torna sfocato mentre rientra", fluidSheetZoomBlurFraction(5_000f, 0.5f) > 0.5f)
  }

  @Test fun `la dissolvenza della variante si legge dalla strada della sagoma`() {
    // Pagina intera: niente sorgente; fino a un terzo della strada ancora niente; all'arrivo tutta la sorgente.
    assertEquals(0f, FluidZoom.crossfade(full, full, card), 0f)
    val third = FluidZoom.lerp(full, card, FluidZoom.CrossfadeFrom)
    assertEquals(0f, FluidZoom.crossfade(third, full, card), 0.01f)
    assertEquals(1f, FluidZoom.crossfade(card, full, card), 0f)
    assertEquals(1f, FluidZoom.crossfade(FluidZoom.lerp(full, card, FluidZoom.CrossfadeTo), full, card), 0.01f)
    // In mezzo cresce sempre, senza salti.
    var last = 0f
    for (k in 0..100) {
      val f = FluidZoom.crossfade(FluidZoom.lerp(full, card, k / 100f), full, card)
      assertTrue("cresce: $f dopo $last", f >= last - 1e-6f)
      assertTrue("nessun salto a $k", f - last <= 1f / ((FluidZoom.CrossfadeTo - FluidZoom.CrossfadeFrom) * 100f) + 1e-3f)
      last = f
    }
    // Il dito che rimpicciolisce la pagina sotto di sé conta come strada fatta, come il tasto.
    val held = FluidZoom.follow(full, full, dragPx = 600f, grabTravel = 0f, direction = 1, pivot = Offset(540f, 1200f), width = 1080f)
    val f = FluidZoom.crossfade(held, full, card)
    assertTrue("col dito: $f", f in 0f..1f)
  }

  @Test fun `la fotografia della sorgente e larga quanto la sagoma e in alto`() {
    val snap = Size(400f, 500f)
    val r = FluidZoom.snapshotRect(Rect(50f, 80f, 650f, 1480f), snap)
    assertEquals(50f, r.left, 0f)
    assertEquals(80f, r.top, 0f)
    assertEquals(600f, r.width, 0.01f)
    assertEquals(750f, r.height, 0.01f)
    // All'arrivo la sagoma e' la sorgente: la fotografia la copre esattamente.
    val home = FluidZoom.snapshotRect(card.bounds, card.bounds.size)
    assertEquals(card.bounds, home)
  }
}
