package dev.antigravity.fluidengine.ui.fluid.toolbar

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * F4A.5b: la manopola `barkeep` (il contenuto che resta in un vetro che cambia forma: «morbido» come le clip di Apple, di
 * serie per scelta di Alessio del 17/11/2026; `sharp` nitido come prima), la forma che la barra passa ai posti e ciò che
 * dichiara di portare ogni vetro.
 */
class FluidToolbarKeptContentTest {
  @After fun backToShipped() {
    FluidToolbarTuning.apply(null)
  }

  private fun group(vararg items: FluidToolbarItem) =
    FluidToolbarGroup(FluidToolbarGroupId(items.map { it.id }.toSet()), FluidToolbarSide.Trailing, items.toList(), 0f, 44f * items.size, true)

  @Test fun `la manopola legge sharp e soft, e senza manopola torna di serie`() {
    assertEquals(FluidToolbarKeptContent.Soft, FluidToolbarTuning.keptContent)
    assertEquals(FluidToolbarKeptContent.Soft, FluidToolbarTuning.parse(mapOf(" BarKeep " to " Soft ")))
    assertEquals(FluidToolbarKeptContent.Sharp, FluidToolbarTuning.parse(mapOf("barkeep" to "sharp")))
    assertNull(FluidToolbarTuning.parse(mapOf("barkeep" to "boh")))
    assertNull(FluidToolbarTuning.parse(mapOf("edgeveil" to "0.5")))
    assertEquals(mapOf("barkeep" to "soft"), FluidToolbarTuning.apply(mapOf("barkeep" to "soft", "blur" to "24")))
    assertEquals(FluidToolbarKeptContent.Soft, FluidToolbarTuning.keptContent)
    assertEquals(emptyMap<String, String>(), FluidToolbarTuning.apply(mapOf("barkeep" to "boh")))
    assertEquals("un valore sconosciuto torna di serie", FluidToolbarKeptContent.Soft, FluidToolbarTuning.keptContent)
    FluidToolbarTuning.apply(mapOf("barkeep" to "sharp"))
    FluidToolbarTuning.apply(emptyMap())
    assertEquals(FluidToolbarKeptContent.Soft, FluidToolbarTuning.keptContent)
  }

  @Test fun `la forma dei posti cambia solo con la variante morbida e solo se il gruppo cambia tasti`() {
    val heart = FluidToolbarItem("heart", symbol = "cuore")
    val share = FluidToolbarItem("share", symbol = "condividi")
    val add = FluidToolbarItem("add", symbol = "piu")
    val three = group(heart, share, add)
    val one = group(add)
    assertNull(fluidToolbarSlotShapeKey(three, FluidToolbarKeptContent.Sharp))
    assertEquals(fluidToolbarSlotShapeKey(three, FluidToolbarKeptContent.Sharp), fluidToolbarSlotShapeKey(one, FluidToolbarKeptContent.Sharp))
    assertNotEquals(fluidToolbarSlotShapeKey(three, FluidToolbarKeptContent.Soft), fluidToolbarSlotShapeKey(one, FluidToolbarKeptContent.Soft))
    // Un'icona che cambia senza che il gruppo cambi tasti non è un cambio di forma: è uno scambio.
    val swapped = group(heart, share, add.copy(symbol = "meno"))
    assertEquals(fluidToolbarSlotShapeKey(three, FluidToolbarKeptContent.Soft), fluidToolbarSlotShapeKey(swapped, FluidToolbarKeptContent.Soft))
  }

  @Test fun `ogni vetro dichiara le facce che porta, e cambia quando cambia un simbolo, la tinta o la selezione`() {
    val list = group(FluidToolbarItem("lead", symbol = "elenco"))
    val lens = group(FluidToolbarItem("lead", symbol = "lente"))
    assertNotEquals(fluidToolbarGlassContentKey(list), fluidToolbarGlassContentKey(lens))
    assertEquals(fluidToolbarGlassContentKey(list), fluidToolbarGlassContentKey(group(FluidToolbarItem("lead", symbol = "elenco"))))
    assertNotEquals(fluidToolbarGlassContentKey(list),
      fluidToolbarGlassContentKey(group(FluidToolbarItem("lead", symbol = "elenco", selected = true))))
    assertNotEquals(fluidToolbarGlassContentKey(list),
      fluidToolbarGlassContentKey(group(FluidToolbarItem("lead", symbol = "elenco", tintArgb = 0xFF007AFFL))))
  }
}
