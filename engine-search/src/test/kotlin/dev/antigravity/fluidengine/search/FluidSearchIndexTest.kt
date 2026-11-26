package dev.antigravity.fluidengine.search

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * L'indice vero (SQLite FTS4 di Robolectric) col corpus italiano di prova: errori di battitura, accenti, apostrofi,
 * plurali, inizi di parola, pertinenza, pezzi evidenziati, tipi, aggiornamenti e cancellazioni, recenti.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidSearchIndexTest {
  private lateinit var index: FluidSearchIndex

  @Before
  fun setUp() = runBlocking {
    index = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
    index.put(FluidSearchSamples.documents)
  }

  @After
  fun tearDown() = index.close()

  private fun ids(text: String, kinds: Set<String> = emptySet()) =
    runBlocking { index.search(FluidSearchQuery(text, kinds = kinds)).hits.map { it.id } }

  @Test
  fun tuttiIDocumentiEntrano() = runBlocking {
    assertEquals(32, index.count())
    val stats = index.stats()
    assertEquals(32, stats.documents)
    assertTrue("parole ${stats.words}", stats.words > 150)
  }

  @Test
  fun unErroreDiBattituraTrovaLaParolaGiustaELoDice() = runBlocking {
    val r = index.search("riunone ")
    assertEquals("riunione", r.corrected)
    assertTrue(r.hits.map { it.id }.containsAll(listOf("n1", "n2", "n9", "n10")))
    // Due lettere scambiate.
    assertTrue("n6" in ids("rivoluzoine "))
    // Una parola che c'e' non si corregge.
    assertNull(index.search("riunione ").corrected)
  }

  @Test
  fun accentiEApostrofiNonContano() {
    assertEquals(listOf("n8"), ids("perche "))
    assertEquals(listOf("n8"), ids("CITTA"))
    assertEquals(listOf("n8"), ids("città "))
    // «dell'apparecchio», «l'ordine», «l'idraulico»: la parola dopo l'apostrofo si trova da sola.
    assertEquals(listOf("n4"), ids("apparecchio "))
    assertTrue("n2" in ids("idraulico "))
    assertTrue(ids("ordine ").containsAll(listOf("n2", "n10")))
  }

  @Test
  fun iPluraliTrovanoIlSingolareEViceversa() {
    assertTrue(ids("riunioni ").containsAll(listOf("n1", "n2", "n9", "n10")))
    assertTrue(ids("verifiche ").containsAll(listOf("c4", "p2")))
    assertTrue("c6" in ids("colloquio "))
    assertTrue("n6" in ids("rivoluzioni "))
  }

  @Test
  fun lInizioDiParolaTrovaMentreSiScrive() {
    assertTrue(ids("riun").containsAll(listOf("n1", "n2", "n9", "n10")))
    // «verifich» si sta scrivendo verso «verifiche»: la radice e' piu' corta di cio' che e' scritto.
    assertTrue(ids("verifich").containsAll(listOf("c4", "p2")))
    // Ogni parola vale anche come inizio: «riun lun» trova «Riunione di lunedì».
    assertEquals("n1", ids("riun lun").first())
  }

  @Test
  fun laPertinenzaMetteInCimaIlTitolo() {
    // «Riunione di lunedì» ha la parola nel titolo (peso 3) e nel testo: viene prima delle note che la dicono una volta.
    assertEquals("n1", ids("riunione ").first())
    // «Studio Odontoiatrico Rossi» sta nelle persone (peso 2) di due mail: tutte e due, e la piu' recente prima.
    assertEquals(listOf("m1", "m2"), ids("odontoiatrico ").take(2))
    // Tutte le parole: «verifica inglese» sono due documenti, non tutti quelli con «verifica».
    assertEquals(setOf("c4", "p2"), ids("verifica inglese ").toSet())
  }

  @Test
  fun seNessunoHaTutteLeParoleVengonoQuelliACuiNeMancaUna() {
    val r = ids("riunione marziani ")
    assertTrue(r.containsAll(listOf("n1", "n2")))
  }

  @Test
  fun iPezziHannoLeParoleEvidenziate() = runBlocking {
    val hit = index.search("riunione ").hits.first { it.id == "n1" }
    assertEquals("Riunione di lunedì", hit.title!!.text)
    assertEquals(listOf("Riunione"), hit.title!!.highlighted)
    assertTrue(hit.snippet!!.highlighted.contains("riunione"))
    // Il pezzo di un testo lungo: tagliato con «…» intorno alla parola.
    val long = FluidSearchDocument(
      "lungo",
      mapOf("title" to "Appunti", "body" to "Prima parte. ".repeat(20) + "Qui si parla della gita a Lucca. " + "Coda finale. ".repeat(20)),
      kind = "nota",
    )
    index.put(long)
    val s = index.search("lucca ").hits.single().snippet!!
    assertTrue(s.text, s.text.startsWith("…") || s.text.startsWith("Qui"))
    assertTrue(s.text.endsWith("…"))
    assertEquals(listOf("Lucca"), s.highlighted)
  }

  @Test
  fun iTipiFiltranoIRisultati() {
    assertEquals(setOf("n9"), ids("riunione ", setOf("trascrizione")).toSet())
    assertTrue(ids("riunione ", setOf("mail")).isEmpty())
  }

  @Test
  fun unDocumentoCambiatoOTolto() = runBlocking {
    index.put(FluidSearchDocument("n8", mapOf("title" to "Paesi da vedere", "body" to "Lisbona e Porto"), kind = "nota"))
    assertTrue(ids("matera ").isEmpty())
    assertEquals(listOf("n8"), ids("lisbona "))
    index.delete(listOf("n8"))
    assertTrue(ids("lisbona ").isEmpty())
    assertEquals(31, index.count())
    // Il vocabolario segue: «lisbona» non c'e' piu', e un errore non la suggerisce.
    assertNull(index.search("lisbna ").corrected)
  }

  @Test
  fun leParoleCheCompletanoEDomandeRecenti() = runBlocking {
    val words = index.completions("riun")
    assertTrue(words.toString(), words.first() == "riunione")
    assertTrue(index.completions("città").contains("città"))
    index.addRecent("riunione")
    index.addRecent("gita")
    index.addRecent("Riunione")
    assertEquals(listOf("Riunione", "gita"), index.recents())
    index.removeRecent("gita")
    assertEquals(listOf("Riunione"), index.recents())
    index.clearRecents()
    assertTrue(index.recents().isEmpty())
  }

  @Test
  fun unoSchemaNuovoRifaLIndice() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val first = FluidSearchIndex.open(context, FluidSearchSamples.schema, name = "rifare")
    first.put(FluidSearchSamples.documents)
    assertFalse(first.rebuilt)
    first.close()
    val same = FluidSearchIndex.open(context, FluidSearchSamples.schema, name = "rifare")
    assertFalse(same.rebuilt)
    assertEquals(32, same.count())
    assertTrue(same.stats().bytes > 0)
    same.close()
    val v2 = FluidSearchSchema("prova", 2, FluidSearchSamples.schema.fields)
    val changed = FluidSearchIndex.open(context, v2, name = "rifare")
    assertTrue(changed.rebuilt)
    assertEquals(0, changed.count())
    changed.destroy()
    assertFalse(FluidSearchIndex.fileOf(context, "rifare").exists())
  }

  @Test
  fun laDomandaVuotaNonTrovaNiente() = runBlocking {
    assertTrue(index.search("").hits.isEmpty())
    assertTrue(index.search("   ").hits.isEmpty())
    assertTrue(index.search("la ").hits.isEmpty())
    assertNotNull(index.search("x").hits)
  }
}
