package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.emptyBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La tinta per pezzo nei grappoli del contenitore (piano «iOS in Android» F4A.2, review di F3 D R4): un grappolo di
 * pezzi con tinte diverse si disegna con una superficie per tinta, ognuna sulla striscia dei suoi pezzi; con una tinta
 * sola niente cambia. La scelta delle strisce è aritmetica pura, ed è qui; che il contenitore la usi davvero (una
 * superficie in più, la tinta di ogni pezzo nel registro) lo prova `FluidToolbarBarTest` della galleria.
 */
class FluidGlassTintSplitTest {

  private val backdrop = GlassBackdropState(emptyBackdrop(), null, 2.5.dp)
  private val neutral = GlassTint(Color(0x61FFFFFF), Color(0xE6F2F2F7), Color.Transparent, GlassTintMode.Neutral)
  private val accent = GlassTint(Color(0xFF0A84FF), Color(0xFF0A84FF), Color.Transparent, GlassTintMode.Colored)

  private fun look(tint: GlassTint) = fluidGlassPanelLook(
    backdrop, tint, FluidCapsuleShape, GlassRole.Interactive, GlassDefaults.buttonOptics(), null, null, 0L,
  )

  @Test
  fun `una tinta sola non divide niente`() {
    assertNull(fluidGlassTintSplit(listOf(look(neutral), look(neutral), look(neutral))))
    assertNull("un pezzo solo", fluidGlassTintSplit(listOf(look(accent))))
  }

  @Test
  fun `tinte diverse fanno una superficie per tinta, nell'ordine in cui compaiono`() {
    val split = fluidGlassTintSplit(listOf(look(neutral), look(accent), look(neutral)))!!
    assertEquals(listOf(neutral, accent), split.looks.map { it.tint })
    assertEquals(listOf(0, 1, 0), split.slabTints.toList())
  }

  @Test
  fun `fra due pezzi distinti il taglio cade nel varco`() {
    // Un gruppo di due icone e, a 12 dp, la principale tinta: il caso della barra.
    val group = Rect(0f, 0f, 88f, 44f)
    val primary = Rect(100f, 0f, 144f, 44f)
    val tints = intArrayOf(0, 1)
    val first = fluidGlassTintStrips(listOf(group, primary), tints, 0).single()
    val second = fluidGlassTintStrips(listOf(group, primary), tints, 1).single()
    assertEquals("a metà del varco", 94f, first.right, 0f)
    assertEquals(94f, second.left, 0f)
    assertTrue("il gruppo è tutto nella sua", first.left <= group.left && first.right >= group.right)
    assertTrue("la principale tutta nella sua", second.left <= primary.left && second.right >= primary.right)
    assertTrue("senza fine sull'altro asse: l'ombra non si morde", first.top < -1000f && first.bottom > 1000f)
  }

  @Test
  fun `in un'unione la selezione e' la striscia del suo tasto, gli altri restano neutri`() {
    // Tre tasti di un blocco unito a 8 dp; quello di mezzo selezionato.
    val slabs = listOf(Rect(0f, 0f, 44f, 44f), Rect(52f, 0f, 96f, 44f), Rect(104f, 0f, 148f, 44f))
    val tints = intArrayOf(0, 1, 0)
    val neutralStrips = fluidGlassTintStrips(slabs, tints, 0)
    val selected = fluidGlassTintStrips(slabs, tints, 1).single()
    assertEquals("due strisce neutre, ai lati", 2, neutralStrips.size)
    assertEquals(48f, selected.left, 0f)
    assertEquals(100f, selected.right, 0f)
    assertEquals(48f, neutralStrips[0].right, 0f)
    assertEquals(100f, neutralStrips[1].left, 0f)
  }

  @Test
  fun `pezzi vicini della stessa tinta non hanno taglio fra loro, l'ordine viene dalla posizione`() {
    // Dati in disordine: le lastre seguono le unioni, non la riga.
    val slabs = listOf(Rect(104f, 0f, 148f, 44f), Rect(0f, 0f, 44f, 44f), Rect(52f, 0f, 96f, 44f))
    val tints = intArrayOf(1, 0, 0)
    val neutral = fluidGlassTintStrips(slabs, tints, 0).single()
    assertEquals(100f, neutral.right, 0f)
    assertEquals(100f, fluidGlassTintStrips(slabs, tints, 1).single().left, 0f)
  }

  @Test
  fun `una colonna si taglia in orizzontale`() {
    val slabs = listOf(Rect(0f, 0f, 44f, 44f), Rect(0f, 56f, 44f, 100f))
    val top = fluidGlassTintStrips(slabs, intArrayOf(0, 1), 0).single()
    assertEquals(50f, top.bottom, 0f)
    assertTrue(top.left < -1000f && top.right > 1000f)
  }

  @Test
  fun `chi nasce dentro il donatore porta la sua tinta da meta' della sovrapposizione`() {
    // La spunta tinta ancora dentro «Seleziona», a filo del suo bordo destro.
    val donor = Rect(0f, 0f, 90f, 44f)
    val bud = Rect(60f, 4f, 90f, 40f)
    val budStrip = fluidGlassTintStrips(listOf(donor, bud), intArrayOf(0, 1), 1).single()
    assertEquals(75f, budStrip.left, 0f)
  }
}
