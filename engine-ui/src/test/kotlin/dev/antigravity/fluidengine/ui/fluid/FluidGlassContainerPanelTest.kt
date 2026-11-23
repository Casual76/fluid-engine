package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.emptyBackdrop
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidVariant
import dev.antigravity.fluidengine.ui.glass.field.GlassPlanePiece
import dev.antigravity.fluidengine.ui.glass.field.GlassPlaneState
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Il pezzo-pannello del contenitore (4.0, piano «iOS in Android» F3.2e), nelle sue parti pure: l'aspetto che il
 * contenitore riceve da `fluidGlassPanel` (gli ingressi di `glassSurface`, il grande dichiarato o del ruolo,
 * l'export), la decisione del grande del contenitore, e i vincoli del contenuto. Nell'albero lo prova
 * `FluidGlassContainerPanelTest` della galleria.
 */
class FluidGlassContainerPanelTest {

  private val backdrop = GlassBackdropState(emptyBackdrop(), null, 2.5.dp)
  private val material = GlassBackdropState(emptyBackdrop(), null, 2.5.dp)

  private val modal = GlassTint(
    overlay = Color(0x8AFFFFFF),
    fallback = Color(0xFAF2F2F7),
    hairline = Color(0x14000000),
    mode = GlassTintMode.Neutral,
  )

  private val shape = ContinuousCornerShape(FluidRadius.Sheet)

  @Test fun `la pillola mantiene una lastra fisica a riposo`() {
    val container = FluidGlassContainerState()
    val piece = FluidGlassPiece(container).apply {
      look = fluidGlassPanelLook(backdrop, modal, FluidCapsuleShape, GlassRole.Interactive,
        GlassDefaults.buttonOptics(), large = null, exports = null, resampleIntervalMillis = 0)
    }
    val capsule = GlassPlanePiece(piece, Rect(10f, 10f, 160f, 56f), FluidCornerRadii.all(23f),
      softMaterialize = true)
    val bounds = Rect(0f, 0f, 288f, 96f)
    container.publishRest(GlassPlaneState(listOf(capsule), 0f, bounds))
    val rest = container.restFields.single()
    assertSame(piece, rest.key)
    assertNotNull("la lastra continua ad essere disegnata come sul palco", rest.plane)
    container.publishRest(GlassPlaneState(listOf(capsule.copy(softMaterialize = false)), 0f, bounds))
    assertNull("i controlli con la comparsa normale tengono il disegno di prima", container.restFields.single().plane)
  }

  private fun look(role: GlassRole, large: Boolean?, exports: GlassBackdropState? = material, resample: Long = 0L) =
    fluidGlassPanelLook(
      backdrop = backdrop,
      tint = modal,
      shape = shape,
      role = role,
      optics = GlassDefaults.optics(role),
      large = large,
      exports = exports,
      resampleIntervalMillis = resample,
    )

  @Test
  fun `il pannello porta al contenitore gli ingressi di glassSurface, non la ricetta dei controlli`() {
    val panel = look(GlassRole.Modal, large = null, resample = 100L)
    assertSame("la pellicola dell'app, non quella dei controlli", modal, panel.tint)
    assertEquals(GlassRole.Modal, panel.role)
    assertEquals("l'ottica del ruolo, passata da sanitized() come fa glassSurface", GlassDefaults.optics(GlassRole.Modal).sanitized(), panel.optics)
    assertEquals(GlassLiquidVariant.Regular, panel.variant)
    assertSame(shape, panel.shape)
    assertSame("esporta il suo materiale ai vetri annidati", material, panel.exports)
    assertEquals(100L, panel.resampleIntervalMillis)
    assertNull("nessuna pressione: un pannello non reagisce al dito", panel.press)
    assertNull(panel.layerBlock)
  }

  @Test
  fun `il grande lo dice il ruolo, o il pannello quando il ruolo non basta`() {
    val profile = GlassLiquidProfile.Default
    assertTrue("un Modal scurisce, come i fogli", profile.adaptive.darkensContinuously(GlassRole.Modal))
    assertEquals(true, look(GlassRole.Modal, large = null).large)
    assertEquals(false, look(GlassRole.Floating, large = null).large)
    // La barra di scrittura: Modal ma alta una riga, gira.
    assertEquals(false, look(GlassRole.Modal, large = false).large)
    // Il dock: Floating ma a raggio di foglio e a tutta larghezza, scurisce.
    assertEquals(true, look(GlassRole.Floating, large = true).large)
  }

  @Test
  fun `il contenitore e' grande se un pannello lo dichiara, altrimenti per la taglia come prima`() {
    // Senza dichiarazione: la taglia del grappolo più grande (un tasto da 44 dp gira, una lastra da 400 scurisce).
    assertFalse(fluidGlassContainerLarge(declared = null, shortSideDp = 44f))
    assertTrue(fluidGlassContainerLarge(declared = null, shortSideDp = 400f))
    // Dichiarato: vince sulla taglia nei due sensi. La capsula larga del composer è alta più di cento dp e gira.
    assertFalse(fluidGlassContainerLarge(declared = false, shortSideDp = 400f))
    assertTrue(fluidGlassContainerLarge(declared = true, shortSideDp = 56f))
  }

  @Test
  fun `gli altri pezzi non dichiarano niente e non esportano`() {
    val control = fluidGlassLook(
      glass = FluidGlass.Regular,
      shape = FluidCapsuleShape,
      backdrop = backdrop,
      baseTint = modal,
      press = null,
      touch = null,
      layerBlock = null,
    )
    assertNull(control.large)
    assertNull(control.exports)
    assertEquals(0L, control.resampleIntervalMillis)
  }

  @Test
  fun `il contenuto riceve i minimi solo se il contenitore li propaga`() {
    val parent = Constraints(minWidth = 600, maxWidth = 600, minHeight = 160, maxHeight = 900)
    // Di serie come un Box: i minimi non passano (il layout di chi sta dentro non cambia).
    val loose = fluidGlassContentConstraints(parent, propagateMinConstraints = false)
    assertEquals(0, loose.minWidth)
    assertEquals(0, loose.minHeight)
    assertEquals(600, loose.maxWidth)
    assertEquals(900, loose.maxHeight)
    // Un pezzo solo: il `weight` o la misura imposta arrivano al vetro.
    assertEquals(parent, fluidGlassContentConstraints(parent, propagateMinConstraints = true))
  }
}
