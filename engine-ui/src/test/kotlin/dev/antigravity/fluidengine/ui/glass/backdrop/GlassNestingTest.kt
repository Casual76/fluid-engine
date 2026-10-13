package dev.antigravity.fluidengine.ui.glass.backdrop

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.ReaderCount
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.combinedBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.exportWanted
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.transformedBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Vetro su vetro (2.13.0): dove va il materiale del pannello nella pila di una superficie, e quando
 * un pannello registra il materiale che pubblica.
 */
class GlassNestingTest {

  private class Fake(val name: String) : Backdrop {
    override val isCoordinatesDependent: Boolean = false
    override fun DrawScope.drawBackdrop(
      density: Density,
      coordinates: LayoutCoordinates?,
      layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) = Unit

    override fun toString() = name
  }

  private val page = Fake("page")
  private val canvas = Fake("canvas")
  private val track = Fake("track")
  private val material = Fake("material")

  // --- la regola ----------------------------------------------------------------------------

  @Test
  fun withoutAContainer_theBackdropIsSampledAsItIs() {
    val plan = planNesting(listOf(page), nestable = true, hasContainer = false, includesMaterial = false) { false }
    assertEquals(NestingPlan.Alone, plan)
  }

  @Test
  fun aSurfaceThatOptsOut_neverStandsOnItsContainer() {
    // Il puck della barra flottante: ricostruisce il materiale della barra da solo.
    val plan = planNesting(listOf(page), nestable = false, hasContainer = true, includesMaterial = false) { false }
    assertEquals(NestingPlan.Alone, plan)
  }

  @Test
  fun aHandWiredBackdropThatAlreadyHoldsTheMaterial_isLeftAlone() {
    // La chrome cablata a mano (azioni della barra, chiusura della notifica, contenuto del modale)
    // ha gia' `combined(pagina, materiale)`: il cablaggio vince, niente doppio materiale.
    val plan = planNesting(listOf(page, material), nestable = true, hasContainer = true, includesMaterial = true) { false }
    assertEquals(NestingPlan.AlreadyIncluded, plan)
  }

  @Test
  fun thePageBehindAPopUp_goesUnderThePopUpsMaterial() {
    // Un tasto su un pop-up che riceve la pagina, come lo scrivono le app: la pagina sta fuori dal
    // pannello, quindi sotto il suo materiale. E' il caso del difetto.
    val plan = planNesting(listOf(page), nestable = true, hasContainer = true, includesMaterial = false) { false }
    assertEquals(NestingPlan.Insert(1), plan)
  }

  @Test
  fun whatIsRecordedInsideThePane_staysOnTopOfItsMaterial() {
    // Il pomello di un interruttore su una card di vetro: la pagina (fuori) sotto il materiale
    // della card, la pista (dentro la card) sopra. Altrimenti il materiale coprirebbe la pista.
    val layers = combinedBackdrop(canvas, transformedBackdrop(track) { it() }).stackedBackdrops
    val plan = planNesting(layers, nestable = true, hasContainer = true, includesMaterial = false) {
      it.containsPart(track)
    }
    assertEquals(NestingPlan.Insert(1), plan)
  }

  @Test
  fun aStackRecordedEntirelyInsideThePane_putsTheMaterialAtTheBottom() {
    val plan = planNesting(listOf(page, track), nestable = true, hasContainer = true, includesMaterial = false) { true }
    assertEquals(NestingPlan.Insert(0), plan)
  }

  @Test
  fun anEmptyBackdrop_standsOnTheMaterialAlone() {
    // Un tasto senza fondale (nessun LocalGlassBackdrop) dentro un pannello: campiona il pannello.
    val plan = planNesting(emptyList<Backdrop>(), nestable = true, hasContainer = true, includesMaterial = false) { false }
    assertEquals(NestingPlan.Insert(0), plan)
  }

  // --- le pile ------------------------------------------------------------------------------

  @Test
  fun stacksFlattenBackToFront_andTransformsStayWhole() {
    val wrapped = transformedBackdrop(track) { it() }
    val stack = combinedBackdrop(combinedBackdrop(page, canvas), wrapped)
    val layers = stack.stackedBackdrops
    assertEquals(3, layers.size)
    assertSame(page, layers[0])
    assertSame(canvas, layers[1])
    // Il fondale trasformato e' un gesto solo: non si apre, altrimenti la trasformazione si perde.
    assertSame(wrapped, layers[2])
  }

  @Test
  fun partsAreFoundThroughStacksAndTransforms() {
    val stack = combinedBackdrop(page, transformedBackdrop(track) { it() })
    // Chi registra solo su richiesta deve sapere che la pista e' letta anche dentro un wrapper.
    assertTrue(stack.containsPart(track))
    assertTrue(stack.containsPart(page))
    assertFalse(stack.containsPart(material))
    assertTrue(stack.containsPart(stack))
  }

  // --- la registrazione pigra ---------------------------------------------------------------

  @Test
  fun anEagerLayerAlwaysRecords_anOnDemandOneOnlyWhileRead() {
    assertTrue(exportWanted(onDemand = false, readers = 0))
    assertTrue(exportWanted(onDemand = false, readers = 3))
    // Un pannello senza vetro dentro non registra niente: questo e' il costo zero.
    assertFalse(exportWanted(onDemand = true, readers = 0))
    assertTrue(exportWanted(onDemand = true, readers = 1))
  }

  @Test
  fun theFirstReaderWakesThePane_andTheCountNeverGoesNegative() {
    val readers = ReaderCount()
    assertTrue("il primo lettore sveglia chi registra", readers.add())
    assertFalse(readers.add())
    readers.remove()
    readers.remove()
    readers.remove()
    assertEquals(0, readers.count)
    // Tornato a zero, il prossimo e' di nuovo il primo: il pannello deve ricominciare a registrare.
    assertTrue(readers.add())
    assertEquals(1, readers.count)
  }
}
