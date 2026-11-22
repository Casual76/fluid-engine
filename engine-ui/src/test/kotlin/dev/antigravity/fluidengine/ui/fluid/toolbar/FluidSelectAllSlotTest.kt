package dev.antigravity.fluidengine.ui.fluid.toolbar

import dev.antigravity.fluidengine.ui.fluid.FluidSelectionState
import dev.antigravity.fluidengine.ui.fluid.icons.Checklist
import dev.antigravity.fluidengine.ui.fluid.icons.Delete
import dev.antigravity.fluidengine.ui.fluid.icons.DriveFileMove
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * «Seleziona tutto» al posto dell'indietro (scelta di Alessio del 22 novembre 2026, sulle riprese di F6B.12): con le
 * azioni della selezione in alto ([FluidSelectionBarPlacement.Top], il caso di Notes) il tasto resta in barra quando c'è
 * posto (telefono largo, tablet) e va in «Altro» solo quando lo spazio non basta (telefono stretto, testo grande). Il
 * tetto dei tre gruppi resta quello di prima per ogni altro tasto.
 *
 * Le larghezze: 200 dp una finestra divisa stretta, 412 un telefono, 600 un telefono in orizzontale, 669 il pannello della
 * lista sul Tab S9 in verticale (753 dp meno il rail), 900 un tablet. Il testo grande allarga le scritte (200% di sistema),
 * non le misure in dp della barra. Il conteggio sta al centro e la barra gli riserva lo stesso spazio ai due lati: quando
 * la riga non ci sta, a uscire è il conteggio; «Seleziona tutto», sul lato corto, esce solo se nemmeno lui ci sta.
 */
class FluidSelectAllSlotTest {

  private val metrics = fluidToolbarMetrics()
  private val noop: () -> Unit = {}

  /** Una scritta finta e dichiarata, come in `FluidSelectionToolbarTest`: 9 dp per carattere più i margini del tasto. */
  private fun textWidth(scale: Float): (String) -> Float =
    { it.length * 9f * scale + FluidToolbarDefaults.TextPadding.value * 2f }

  private fun notes(count: Int = 3) = buildFluidScreenToolbar(onBack = noop) {
    val state = FluidSelectionState<Int>().apply { select(1) }
    selection(state, count = count, onSelectAll = noop, onDone = noop, placement = FluidSelectionBarPlacement.Top) {
      button("sposta", FluidIcons.DriveFileMove, "Sposta", onClick = noop, enabled = selectionHasItems)
      button("elimina", FluidIcons.Delete, "Elimina", onClick = noop, role = FluidButtonRole.Destructive,
        enabled = selectionHasItems)
    }
  }

  private fun resolve(declaration: FluidToolbarDeclaration, width: Float, scale: Float = 1f) =
    resolveFluidToolbar(fluidToolbarModel(declaration, textWidth(scale)), width, metrics = metrics, bar = FluidToolbarBar.Top)

  private fun FluidToolbarResolved.visible(id: Any) = elements.any { it.item.id == id && !it.inOverflow }
  private fun FluidToolbarResolved.hidden(id: Any) = overflow.any { it.id == id }

  @Test fun `con le azioni in alto Seleziona tutto resta in barra quando c'e' posto`() {
    for (width in listOf(600f, 669f, 900f)) for (count in listOf(0, 3, 120)) {
      val bar = resolve(notes(count), width)
      assertTrue("$width dp, $count: ci sta", bar.fits)
      assertTrue("$width dp, $count: «Seleziona tutto» in barra", bar.visible(FluidSelectionToolbarIds.SelectAll))
      assertTrue("$width dp, $count: il conteggio", bar.visible(FluidSelectionToolbarIds.Count))
      assertTrue("$width dp, $count: «Fine»", bar.visible(FluidSelectionToolbarIds.Toggle))
      // Le azioni stanno nel posto che resta o in «Altro»: nessuna si perde.
      for (id in listOf("sposta", "elimina")) assertTrue("$width dp, $count: $id", bar.visible(id) || bar.hidden(id))
      // «Seleziona tutto» sta a sinistra, dove stava l'indietro.
      assertEquals(FluidToolbarSide.Leading, bar.elements.single { it.item.id == FluidSelectionToolbarIds.SelectAll }.side)
    }
  }

  @Test fun `sul tablet le azioni prendono il posto che Seleziona tutto lascia libero nei tre gruppi`() {
    // «Seleziona tutto» non conta nei tre gruppi: con il conteggio e «Fine» resta un gruppo, e su un tablet le due azioni
    // ci stanno, accanto a «Fine» (come F6B.1 faceva già con una sola azione). Senza «Altro».
    val bar = resolve(notes(3), 900f)
    assertTrue(bar.visible("sposta") && bar.visible("elimina"))
    assertTrue(bar.visible(FluidSelectionToolbarIds.SelectAll))
    assertTrue(bar.overflow.isEmpty())
  }

  @Test fun `al testo grande resta sul tablet e sul telefono esce il conteggio ma non Seleziona tutto`() {
    val tablet = resolve(notes(120), 900f, scale = 2f)
    assertTrue(tablet.fits)
    assertTrue("tablet al 200%: «Seleziona tutto» in barra", tablet.visible(FluidSelectionToolbarIds.SelectAll))
    // Al 200% su un telefono il conteggio al centro non ci sta: esce lui. «Seleziona tutto», sul lato corto, non toglie
    // spazio a nessuno e resta, con «Fine».
    val phone = resolve(notes(120), 412f, scale = 2f)
    assertTrue(phone.fits)
    assertTrue("telefono al 200%: il conteggio esce", phone.hidden(FluidSelectionToolbarIds.Count))
    assertTrue("telefono al 200%: «Seleziona tutto» resta", phone.visible(FluidSelectionToolbarIds.SelectAll))
    assertTrue("telefono al 200%: «Fine» resta", phone.visible(FluidSelectionToolbarIds.Toggle))
  }

  @Test fun `in una finestra stretta va in Altro solo se non ci sta e Fine resta`() {
    val narrow = resolve(notes(120), 200f, scale = 2f)
    assertTrue("«Fine» resta", narrow.visible(FluidSelectionToolbarIds.Toggle))
    assertTrue("«Seleziona tutto» in «Altro»", narrow.hidden(FluidSelectionToolbarIds.SelectAll))
    // Da «Altro» lo raggiunge anche TalkBack: la voce c'è, col suo nome.
    assertEquals("Seleziona tutto", narrow.overflow.single { it.id == FluidSelectionToolbarIds.SelectAll }.label)
  }

  @Test fun `il tetto dei tre gruppi non cambia per un altro tasto a sinistra`() {
    // La stessa riga con un tasto qualunque al posto di «Seleziona tutto»: conta nei tre gruppi ed esce, come prima.
    val other = buildFluidScreenToolbar(onBack = null) {
      button("altro-a-sinistra", FluidIcons.Checklist, "Un tasto", onClick = noop, placement = FluidToolbarPlacement.Leading,
        priority = FluidToolbarPriority.High)
      group("azioni") {
        button("sposta", FluidIcons.DriveFileMove, "Sposta", onClick = noop)
        button("elimina", FluidIcons.Delete, "Elimina", onClick = noop)
      }
      text("conteggio", "3 selezionati", onClick = noop, placement = FluidToolbarPlacement.Principal,
        priority = FluidToolbarPriority.High)
      text("fine", "Fine", onClick = noop, priority = FluidToolbarPriority.High)
    }
    val bar = resolve(other, 900f)
    assertTrue(bar.fits)
    assertTrue("le azioni in «Altro»", bar.hidden("sposta") && bar.hidden("elimina"))
    assertTrue("un tasto qualunque conta nei tre gruppi: esce", bar.hidden("altro-a-sinistra"))
    assertFalse(bar.visible("altro-a-sinistra"))
    // E l'indietro resta fuori dal conto, come sempre.
    val withBack = resolve(buildFluidScreenToolbar(onBack = noop) {
      group("azioni") {
        button("sposta", FluidIcons.DriveFileMove, "Sposta", onClick = noop)
        button("elimina", FluidIcons.Delete, "Elimina", onClick = noop)
      }
      text("conteggio", "3 selezionati", onClick = noop, placement = FluidToolbarPlacement.Principal,
        priority = FluidToolbarPriority.High)
      text("fine", "Fine", onClick = noop, priority = FluidToolbarPriority.High)
    }, 900f)
    assertTrue(withBack.visible(FluidToolbarIds.Back))
  }
}
