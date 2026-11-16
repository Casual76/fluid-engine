package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * L'ordine dei modificatori del piano flottante è contratto (4.0, piano «iOS in Android» F3.2): `fluidGlassId`,
 * `fluidGlassUnion` e `fluidGlassTransition` valgono per il `fluidGlass` che li **segue** nella stessa catena, e per
 * nessun altro. Qui la regola come funzione pura della catena ([fluidGlassAttributesOf], che il vetro chiama sulla
 * catena alla sua sinistra); l'avviso in debug e l'effetto nell'albero li prova `FluidGlassContainerTest` nel modulo
 * della galleria.
 */
class FluidGlassContainerOrderTest {

  private val ns = FluidGlassNamespace()
  private val other = FluidGlassNamespace()

  @Test
  fun `l'id scritto prima vale per il vetro che segue`() {
    val chain = Modifier.fluidGlassId("play", ns)
    val attributes = fluidGlassAttributesOf(chain)
    assertEquals(FluidGlassQualifiedId(ns, "play"), attributes.id)
    assertNull(attributes.unionId)
    assertEquals(FluidGlassTransition.MatchedGeometry, attributes.transition)
  }

  @Test
  fun `scritto dopo un vetro vale per il vetro dopo, non per quello prima`() {
    // `Modifier.fluidGlass().fluidGlassId(x)`: il vetro vede solo la sua sinistra, cioè niente.
    val glassFirst = Modifier.then(FluidGlassMarkerElement)
    assertEquals(FluidGlassAttributes.None, fluidGlassAttributesOf(glassFirst))
    // E un secondo vetro più a destra prende soltanto ciò che sta fra i due.
    val id = FluidGlassIdElement("dopo", ns)
    val chain = Modifier.fluidGlassId("prima", ns).then(FluidGlassMarkerElement).then(id)
    assertEquals(FluidGlassQualifiedId(ns, "dopo"), fluidGlassAttributesOf(chain).id)
    assertTrue(id.consumed)
  }

  @Test
  fun `solo gli elementi dopo l'ultimo vetro sono consumati, quelli prima restano a chi li aveva`() {
    val before = FluidGlassIdElement("a", ns)
    val after = FluidGlassTransitionElement(FluidGlassTransition.Materialize)
    val chain = Modifier.then(before).then(FluidGlassMarkerElement).then(after)
    val attributes = fluidGlassAttributesOf(chain)
    assertNull(attributes.id)
    assertEquals(FluidGlassTransition.Materialize, attributes.transition)
    assertFalse("l'id prima del primo vetro è di quel vetro, non di questo", before.consumed)
    assertTrue(after.consumed)
  }

  @Test
  fun `il piu' a destra vince, i tre attributi si sommano, e i modificatori estranei non contano`() {
    val chain = Modifier
      .fluidGlassId("uno", ns)
      .alpha(0.5f)
      .fluidGlassUnion("barra", ns)
      .fluidGlassTransition(FluidGlassTransition.Identity)
      .fluidGlassId("due", other)
    val attributes = fluidGlassAttributesOf(chain)
    assertEquals(FluidGlassQualifiedId(other, "due"), attributes.id)
    assertEquals(FluidGlassQualifiedId(ns, "barra"), attributes.unionId)
    assertEquals(FluidGlassTransition.Identity, attributes.transition)
  }

  @Test
  fun `lo stesso id in due spazi dei nomi sono due vetri`() {
    assertFalse(FluidGlassQualifiedId(ns, "x") == FluidGlassQualifiedId(other, "x"))
    assertTrue(FluidGlassQualifiedId(ns, "x") == FluidGlassQualifiedId(ns, "x"))
  }

  @Test
  fun `l'uguaglianza degli elementi non guarda se sono consumati, cosi' un ricomporre non rifa' il nodo`() {
    val a = FluidGlassIdElement("x", ns)
    val b = FluidGlassIdElement("x", ns)
    a.consumed = true
    assertEquals(a, b)
    assertEquals(a.hashCode(), b.hashCode())
    assertFalse(FluidGlassIdElement("x", ns) == FluidGlassIdElement("x", other))
    assertFalse(FluidGlassUnionElement("x", ns).equals(FluidGlassIdElement("x", ns)))
  }
}
