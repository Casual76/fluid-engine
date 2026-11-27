@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.novita

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassDrawnLayerBlock
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassSamplePlacement
import dev.antigravity.fluidengine.ui.glass.backdrop.glassDrawOffset
import dev.antigravity.fluidengine.ui.glass.backdrop.glassLayerOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F7.31 · Il vetro di una maniglia spostata nel disegno campiona **lo stesso punto, al bit**, di quando la traslazione e la
 * scala stavano negli strati.
 *
 * Prima: `graphicsLayer { translationX }` sulla maniglia, `graphicsLayer(layerBlock)` sulla goccia, e il fondale partiva da
 * `localPositionOf(vetro)`. Dopo: due strati fermi, la traslazione e la scala nel disegno, e il fondale parte da
 * `localPositionOf(vetro, origine)`, con l'origine di `GlassSamplePlacement`. I due numeri devono avere gli stessi bit:
 * una differenza dell'ultima cifra sposterebbe di un niente il disegno della pagina dentro la cattura, e la cattura non
 * sarebbe più la stessa al bit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F7p31GlassPlacementTest {

  @get:Rule
  val compose = createComposeRule()

  private class Probe : Modifier.Node(), GlobalPositionAwareModifierNode {
    var coordinates: LayoutCoordinates? = null
    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
      this.coordinates = coordinates
    }
  }

  private class ProbeElement(val probe: Probe) : ModifierNodeElement<Probe>() {
    override fun create() = probe
    override fun update(node: Probe) = Unit
    override fun equals(other: Any?) = other is ProbeElement && other.probe === probe
    override fun hashCode() = System.identityHashCode(probe)
  }

  private fun check(shift: Float, block: GraphicsLayerScope.() -> Unit) {
    val before = Probe()
    val after = Probe()
    var root: LayoutCoordinates? = null
    compose.setContent {
      Box(Modifier.fillMaxSize().onGloballyPositioned { root = it }) {
        // Come prima: la corsa e la goccia negli strati.
        Box(
          Modifier
            .padding(start = 13.dp, top = 41.dp)
            .graphicsLayer { translationX = shift }
            .graphicsLayer(block)
            .then(ProbeElement(before))
            .size(40.dp, 24.dp),
        )
        // Come adesso: due strati fermi, la corsa e la goccia nel disegno.
        Box(
          Modifier
            .padding(start = 13.dp, top = 41.dp)
            .glassDrawOffset { Offset(shift, 0f) }
            .then(glassLayerOf(GlassDrawnLayerBlock(block)))
            .then(ProbeElement(after))
            .size(40.dp, 24.dp),
        )
      }
    }
    compose.waitForIdle()
    compose.runOnIdle {
      val r = root!!
      val old = r.localPositionOf(before.coordinates!!)
      val coordinates = after.coordinates!!
      val density = Density(2f)
      val placement: GlassSamplePlacement = GlassSamplePlacement()
      val drawn: GlassDrawnLayerBlock = GlassDrawnLayerBlock(block)
      val origin: Offset = placement.update(after, density, coordinates.size.toSize(), drawn, coordinates)
      val new = r.localPositionOf(coordinates, origin)
      println("F7p31 posto: prima $old, adesso $new (origine $origin)")
      assertEquals("x, ai bit", old.x.toRawBits(), new.x.toRawBits())
      assertEquals("y, ai bit", old.y.toRawBits(), new.y.toRawBits())
    }
  }

  @Test
  fun `la goccia che cresce e corre campiona lo stesso punto al bit`() = check(123.4567f) {
    scaleX = 1.537f
    scaleY = 0.913f
  }

  @Test
  fun `la maniglia ferma che corre campiona lo stesso punto al bit`() = check(-77.31f) {}

  @Test
  fun `la goccia che cresce sul posto campiona lo stesso punto al bit`() = check(0f) {
    scaleX = 1.21f
    scaleY = 1.21f
  }

  @Test
  fun `un perno e una rotazione sul piano campionano lo stesso punto al bit`() = check(31.25f) {
    transformOrigin = TransformOrigin(0.3f, 0.8f)
    rotationZ = 7.5f
    scaleX = 1.1f
    scaleY = 0.9f
    translationY = 3.3f
  }

  /**
   * Il pomello del «prima e dopo» in un visore ingrandito: fra lo spostamento e il vetro c'è uno strato che scala (lo zoom).
   * Lì il conto passa per `localPositionOf`: lo stesso punto, a meno dell'ultima cifra.
   */
  @Test
  fun `un pomello sotto uno strato che scala campiona dove si vede`() {
    val before = Probe()
    val after = Probe()
    var root: LayoutCoordinates? = null
    val shift = Offset(211.37f, 64.5f)
    compose.setContent {
      Box(Modifier.fillMaxSize().onGloballyPositioned { root = it }) {
        Box(
          Modifier
            .padding(start = 7.dp, top = 19.dp)
            .graphicsLayer { translationX = shift.x; translationY = shift.y }
            .graphicsLayer { scaleX = 0.4f; scaleY = 0.4f }
            .then(ProbeElement(before))
            .size(44.dp),
        )
        Box(
          Modifier
            .padding(start = 7.dp, top = 19.dp)
            .glassDrawOffset { shift }
            .graphicsLayer { scaleX = 0.4f; scaleY = 0.4f }
            .then(ProbeElement(after))
            .size(44.dp),
        )
      }
    }
    compose.waitForIdle()
    compose.runOnIdle {
      val r = root!!
      val old = r.localPositionOf(before.coordinates!!)
      val coordinates = after.coordinates!!
      val placement: GlassSamplePlacement = GlassSamplePlacement()
      val origin: Offset = placement.update(after, Density(2f), coordinates.size.toSize(), null, coordinates)
      val new = r.localPositionOf(coordinates, origin)
      println("F7p31 posto sotto lo zoom: prima $old, adesso $new (origine $origin)")
      assertEquals(old.x, new.x, 1e-3f)
      assertEquals(old.y, new.y, 1e-3f)
    }
  }

  @Test
  fun `un vetro che sta dove dice il layout ha origine zero`() {
    val placement: GlassSamplePlacement = GlassSamplePlacement()
    placement.clear()
    val origin: Offset = placement.origin
    assertEquals(Offset.Zero, origin)
  }
}
