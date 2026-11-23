package dev.antigravity.fluidengine.ui.fluid.viewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F6C.19 · Il menu del file, lato puro: il nome senza estensione del titolo, le voci di File e Quick Look nei loro
 * gruppi, quelle dell'app solo se date, nessuna voce due volte, e «Estrai tutto» che resta nella barra in basso.
 */
class FluidViewerFileMenuTest {
  private val strings = FluidViewerStrings()
  private val noop: () -> Unit = {}

  private fun labels(entries: List<ViewerFileEntry>) = entries.map { it.label }

  @Test fun `il titolo e' il nome senza estensione, come in Quick Look`() {
    assertEquals("Quaderno di campo", fluidViewerTitleName("Quaderno di campo.pdf"))
    assertEquals("Lago all’alba", fluidViewerTitleName("Lago all’alba.m4a"))
    assertEquals("build.gradle", fluidViewerTitleName("build.gradle.kts"))
    assertEquals("Progetto", fluidViewerTitleName("Progetto.zip"))
    // Nomi che senza estensione non direbbero niente, o un punto che non separa un'estensione.
    assertEquals(".gitignore", fluidViewerTitleName(".gitignore"))
    assertEquals("Leggimi", fluidViewerTitleName("Leggimi"))
    assertEquals("fine.", fluidViewerTitleName("fine."))
    assertEquals("Verbale v1.2 della riunione", fluidViewerTitleName("Verbale v1.2 della riunione"))
    assertEquals("archivio.molto-lungo-davvero", fluidViewerTitleName("archivio.molto-lungo-davvero"))
  }

  @Test fun `un PDF ha le voci dell'engine, la stampa del visore e Informazioni`() {
    val entries = viewerFileMenuEntries(FluidViewerFileActions(share = noop, openWith = noop, copy = noop),
      emptyList(), print = noop, onInfo = noop, dockLabels = emptySet(), strings = strings)
    assertEquals(listOf("Condividi", "Apri con…", "Copia", "Stampa", "Informazioni"), labels(entries))
  }

  @Test fun `le voci dell'app compaiono solo se date, nei gruppi di iOS, Elimina in fondo e in rosso`() {
    val all = viewerFileMenuEntries(FluidViewerFileActions(share = noop, openWith = noop, copy = noop, rename = noop,
      move = noop, duplicate = noop, delete = noop, more = listOf(FluidViewerInfoAction("Salva in Download", noop))),
      emptyList(), print = noop, onInfo = noop, dockLabels = emptySet(), strings = strings)
    assertEquals(listOf("Duplica", "Sposta", "Rinomina", "Salva in Download", "Condividi", "Apri con…", "Copia", "Stampa",
      "Informazioni", "Elimina"), labels(all))
    val menu = all.toContextActions { it() }
    assertEquals(listOf(false, false, false, false, true, false, false, false, true, true), menu.map { it.startsSection })
    assertEquals(listOf("Elimina"), menu.filter { it.destructive }.map { it.label })
    assertTrue("ogni voce ha la sua icona, tranne le voci in più dell'app",
      menu.filter { it.label != "Salva in Download" }.all { it.icon != null })

    val some = viewerFileMenuEntries(FluidViewerFileActions(share = noop, rename = noop), emptyList(), print = null,
      onInfo = noop, dockLabels = emptySet(), strings = strings)
    assertEquals(listOf("Rinomina", "Condividi", "Informazioni"), labels(some))
  }

  @Test fun `le azioni del pannello info di prima diventano le voci del file, senza doppioni`() {
    var legacyShare = 0
    var engineShare = 0
    val legacy = listOf(FluidViewerInfoAction("Apri con…", noop), FluidViewerInfoAction("Condividi") { legacyShare++ },
      FluidViewerInfoAction("Estrai tutto", noop), FluidViewerInfoAction("Apri in Mappe", noop))
    val entries = viewerFileMenuEntries(FluidViewerFileActions(share = { engineShare++ }), legacy, print = null,
      onInfo = noop, dockLabels = setOf("Estrai tutto", "Condividi"), strings = strings)
    // «Estrai tutto» è nella barra in basso: non torna nel titolo. «Condividi» è quello dell'engine.
    assertEquals(listOf("Condividi", "Apri con…", "Apri in Mappe", "Informazioni"), labels(entries))
    entries.first().run()
    assertEquals(1, engineShare)
    assertEquals(0, legacyShare)
    assertEquals(labels(entries).toSet().size, entries.size)
  }

  @Test fun `senza azioni il menu ha solo Informazioni, e senza pannello niente`() {
    assertEquals(listOf("Informazioni"), labels(viewerFileMenuEntries(null, emptyList(), null, noop, emptySet(), strings)))
    assertTrue(viewerFileMenuEntries(null, emptyList(), null, null, emptySet(), strings).isEmpty())
  }

  @Test fun `Vai alla cella legge i riferimenti dei fogli di calcolo`() {
    assertEquals(0 to 0, fluidCellReference("A1"))
    assertEquals(1 to 11, fluidCellReference(" b12 "))
    assertEquals(26 to 2, fluidCellReference("AA3"))
    assertEquals(2 to 6, fluidCellReference("\$C\$7"))
    assertEquals(null, fluidCellReference("12B"))
    assertEquals(null, fluidCellReference("A0"))
    assertEquals(null, fluidCellReference(""))
    assertEquals(null, fluidCellReference("Riga 4"))
  }

  @Test fun `il timer finisce col brano, anche quando il brano ricomincia, ma non con un salto indietro`() {
    assertTrue(ViewerAudioMenu.endOfTrack(221_000, 221_700, 222_000))
    assertTrue("ripete: dalla fine all'inizio", ViewerAudioMenu.endOfTrack(221_000, 300, 222_000))
    assertTrue("un salto indietro di 15 s non è la fine", !ViewerAudioMenu.endOfTrack(100_000, 85_000, 222_000))
    assertTrue("a metà no", !ViewerAudioMenu.endOfTrack(84_000, 84_500, 222_000))
    assertTrue("senza durata no", !ViewerAudioMenu.endOfTrack(84_000, 84_500, null))
    assertEquals(listOf(.75f, 1f, 1.25f, 1.5f, 2f), ViewerAudioMenu.Speeds)
  }

  @Test fun `il nome per la coda di stampa finisce con pdf`() {
    assertEquals("Quaderno di campo.pdf", viewerPrintFileName("Quaderno di campo"))
    assertEquals("relazione.PDF", viewerPrintFileName("relazione.PDF"))
    assertEquals("documento.pdf", viewerPrintFileName(" "))
  }
}
