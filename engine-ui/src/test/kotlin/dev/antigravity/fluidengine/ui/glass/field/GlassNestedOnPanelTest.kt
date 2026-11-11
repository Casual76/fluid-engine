package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.NestingPlan
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.combinedBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.emptyBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.glassStandsOnGlass
import dev.antigravity.fluidengine.ui.glass.backdrop.planNesting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F1A.13: il vetro annidato della notifica «Operazione aggiornata» (S2-pre, difetto 1).
 *
 * La causa, misurata sul Tab S9: in «Avvisi con azione» l'host non ha un fondo, la card è una `Surface` piena, e la
 * chiusura e «Ripristina ultima modifica» campionavano il **fondo vuoto**. Una cattura vuota non ha operazioni, e
 * la piattaforma non esegue il corpo di un layer senza operazioni: il corpo c'era solo se c'era un margine, cioè
 * solo con una smerigliatura più larga della banda della lente (r12, C: la toppa piena), mai con D o M (l'anello
 * vuoto). E senza un vetro fra gli antenati la regola dell'annidato non poteva arrivare.
 */
class GlassNestedOnPanelTest {

  // ---- La cattura che apre sempre con un'operazione ------------------------------------------------------------

  /** Un canvas di piattaforma che annota i rettangoli: è tutto ciò che il fondo vuoto e l'aggancio disegnano. */
  private class Log : android.graphics.Canvas() {
    val events = ArrayList<String>()

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: android.graphics.Paint) {
      events += "rect"
    }
  }

  /** Un fondo che disegna un rettangolo, come una registrazione vera. */
  private object Drawing : Backdrop {
    override val isCoordinatesDependent: Boolean = false
    override fun DrawScope.drawBackdrop(
      density: Density,
      coordinates: LayoutCoordinates?,
      layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
      drawRect(androidx.compose.ui.graphics.Color.Red)
    }
  }

  /** Esegue [hook] come lo esegue il nodo del vetro quando registra la cattura, con [backdrop] come fondo. */
  private fun record(
    hook: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit,
    backdrop: Backdrop,
  ): List<String> {
    val log = Log()
    CanvasDrawScope().draw(Density(2.125f), LayoutDirection.Ltr, Canvas(log), Size(94f, 94f)) {
      hook {
        val scope = this
        with(backdrop) { scope.drawBackdrop(scope, null, null) }
      }
    }
    return log.events
  }

  @Test
  fun theDefaultHookRecordsNothingForAnEmptyBackdrop() {
    // È l'aggancio di serie di `drawBackdrop` (`{ it() }`): con il fondo vuoto la cattura resta senza operazioni,
    // e il corpo non gira. Il difetto, scritto.
    val events = record({ it() }, emptyBackdrop())
    assertEquals(emptyList<String>(), events)
  }

  @Test
  fun theLiquidHookAlwaysRecordsAnOperation_evenForAnEmptyBackdrop() {
    val events = record(GlassLiquidOnDrawBackdrop, emptyBackdrop())
    assertEquals(listOf("rect"), events)
  }

  @Test
  fun theLiquidHookClearsBeforeTheBackdropNeverAfter() {
    // Con un fondo vero la cancellazione viene prima: dopo, cancellerebbe il fondo.
    val log = Log()
    var drawnAt = -1
    CanvasDrawScope().draw(Density(2.125f), LayoutDirection.Ltr, Canvas(log), Size(94f, 94f)) {
      GlassLiquidOnDrawBackdrop {
        val scope = this
        drawnAt = log.events.size
        with(Drawing) { scope.drawBackdrop(scope, null, null) }
      }
    }
    assertEquals(1, drawnAt)
    assertEquals(listOf("rect", "rect"), log.events)
  }

  // ---- Il margine che decideva se il corpo c'era -------------------------------------------------------------

  /** Il margine della cattura in pixel del Tab S9 (2,125), per un tasto di vetro di [heightDp] e [profile]. */
  private fun paddingPx(widthDp: Float, heightDp: Float, profile: GlassLiquidProfile): Float {
    val recipe = glassRecipe(
      role = GlassRole.Interactive,
      widthDp = widthDp,
      heightDp = heightDp,
      darkSide = true,
      optics = GlassDefaults.buttonOptics(),
      nested = true,
      profile = profile,
    )
    val density = 2.125f
    return glassDrawInputs(recipe, FluidCapsuleShape, Size(widthDp * density, heightDp * density), density).paddingPx
  }

  private fun tuned(vararg knobs: Pair<String, String>): GlassLiquidProfile =
    GlassLiquidTuning.apply(GlassLiquidProfile.Shipped, mapOf(*knobs)).profile

  @Test
  fun theNotificationsControlsHadNoMarginWithTheShippedAndTheMiddleFrost() {
    // «Ripristina ultima modifica» (alto 48 dp) e la croce (44 dp): come le misure di S2-pre, D e M senza corpo,
    // r12 e C con il corpo. Il margine è `max(0, sfocatura − banda della lente)`.
    for (frost in listOf("ios26", "mid")) {
      assertEquals("tasto, $frost", 0f, paddingPx(698f, 48f, tuned("frost" to frost)), 0f)
      assertEquals("croce, $frost", 0f, paddingPx(44f, 44f, tuned("frost" to frost)), 0f)
    }
    for (frost in listOf("r12", "wide")) {
      assertTrue("tasto, $frost", paddingPx(698f, 48f, tuned("frost" to frost)) > 0f)
      assertTrue("croce, $frost", paddingPx(44f, 44f, tuned("frost" to frost)) > 0f)
    }
  }

  @Test
  fun theTabletExperimentThatSeparatedTheTwoControls() {
    // `gallery.tune blur=11.4` sul Tab S9: il tasto (banda 11,5 dp) resta vuoto, la croce (banda 10,6) prende la
    // toppa. Una sola sfocatura, due margini: è il margine a decidere, non la smerigliatura né la risoluzione.
    val profile = tuned("blur" to "11.4")
    assertEquals(0f, paddingPx(698f, 48f, profile), 0f)
    assertTrue(paddingPx(44f, 44f, profile) > 0f)
  }

  // ---- I materiali pubblicati dalle lastre `Liquid` ---------------------------------------------------------

  private class Fake(val name: String) : Backdrop {
    override val isCoordinatesDependent: Boolean = false
    override fun DrawScope.drawBackdrop(
      density: Density,
      coordinates: LayoutCoordinates?,
      layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) = Unit

    override fun toString() = name
  }

  @Test
  fun aControlThatSamplesAPublishedLiquidMaterialStandsOnThatPanel() {
    // Lo schema di `exports` con i controlli in un nodo fratello: `combined(pagina, materiale)`.
    val page = Fake("pagina")
    val material = Fake("materiale")
    val sibling = combinedBackdrop(page, material)
    assertFalse(glassSamplesLiquidPanel(sibling, GlassLiquidPanels::isPublished))
    GlassLiquidPanels.publish(material)
    try {
      assertTrue(glassSamplesLiquidPanel(sibling, GlassLiquidPanels::isPublished))
      assertTrue(glassSamplesLiquidPanel(material, GlassLiquidPanels::isPublished))
      // Chi campiona la sola pagina sta sulla pagina.
      assertFalse(glassSamplesLiquidPanel(page, GlassLiquidPanels::isPublished))
    } finally {
      GlassLiquidPanels.withdraw(material)
    }
    assertFalse(glassSamplesLiquidPanel(sibling, GlassLiquidPanels::isPublished))
  }

  @Test
  fun aMaterialStaysPublishedWhileAnyPanelPublishesIt() {
    val material = Fake("materiale")
    GlassLiquidPanels.publish(material)
    GlassLiquidPanels.publish(material)
    GlassLiquidPanels.withdraw(material)
    assertTrue(GlassLiquidPanels.isPublished(material))
    GlassLiquidPanels.withdraw(material)
    assertFalse(GlassLiquidPanels.isPublished(material))
    // Ritirare più volte di quante si è pubblicato non porta il conto sotto zero.
    GlassLiquidPanels.withdraw(material)
    GlassLiquidPanels.publish(material)
    assertTrue(GlassLiquidPanels.isPublished(material))
    GlassLiquidPanels.withdraw(material)
  }

  // ---- Il pannello opaco ---------------------------------------------------------------------------------------

  @Test
  fun anOpaquePanelPublishesItsFillOnlyForTheNewMaterial() {
    assertTrue(glassOpaquePanelPublishes(33, { true }, FluidGlassModel.Liquid))
    // Il vetro di prima resta com'era: niente materiale, il fondo vuoto di sempre.
    assertFalse(glassOpaquePanelPublishes(33, { true }, FluidGlassModel.Classic))
    assertFalse(glassOpaquePanelPublishes(32, { true }, FluidGlassModel.Liquid))
    assertFalse(glassOpaquePanelPublishes(33, { false }, FluidGlassModel.Liquid))
  }

  @Test
  fun onTheOpaqueCardTheControlsNowStandOnGlass() {
    // Prima: nessun contenitore fra gli antenati (la card era una `Surface`), il fondo vuoto, la lastra «sulla
    // pagina». Adesso la card fa da contenitore col proprio fondo: i controlli che ricevono il fondo vuoto ci
    // infilano il materiale della card (Insert), quelli che ricevono la card la trovano già (AlreadyIncluded).
    val before = planNesting(emptyList<Backdrop>(), nestable = true, hasContainer = false, includesMaterial = false) { false }
    assertEquals(NestingPlan.Alone, before)
    assertFalse(glassStandsOnGlass(before, inserted = false))

    val empty = planNesting(emptyList<Backdrop>(), nestable = true, hasContainer = true, includesMaterial = false) { false }
    assertEquals(NestingPlan.Insert(0), empty)
    assertTrue(glassStandsOnGlass(empty, inserted = true))

    val card = Fake("card")
    val wired = planNesting(listOf<Backdrop>(card), nestable = true, hasContainer = true, includesMaterial = true) { false }
    assertEquals(NestingPlan.AlreadyIncluded, wired)
    assertTrue(glassStandsOnGlass(wired, inserted = false))
  }
}
