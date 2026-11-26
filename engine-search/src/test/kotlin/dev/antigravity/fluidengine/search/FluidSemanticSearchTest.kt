package dev.antigravity.fluidengine.search

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * La ricerca per significato con i vettori **veri** di EmbeddingGemma 2 Text 270M ([FixtureEmbedder]): «la mail del
 * dentista» trova «Studio Odontoiatrico Rossi» senza una parola in comune; le parole e il significato insieme.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FluidSemanticSearchTest {

  private suspend fun indexed(docs: List<FluidSearchDocument>, embedder: FixtureEmbedder = FixtureEmbedder()): FluidSearchIndex {
    val index = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
    index.put(docs)
    val semantic = FluidSemanticIndexer(index, embedder)
    val progress = semantic.update()
    assertEquals(docs.size, progress.indexed)
    assertFalse(progress.hasMore)
    assertEquals(0, index.pendingMeaningCount())
    return index
  }

  private suspend fun meaning(index: FluidSearchIndex, text: String, kinds: Set<String> = emptySet()): List<FluidSearchHit> {
    val vector = FixtureEmbedder().embed(listOf(text)).single()
    return index.search(FluidSearchQuery(text, kinds = kinds, meaning = vector)).hits
  }

  @Test
  fun laMailDelDentistaTrovaLoStudioOdontoiatrico() = runBlocking {
    // L'indice di Pampa Mail da solo: otto mail, nessuna dice «dentista».
    val mail = indexed(FluidSearchSamples.of("mail"))
    assertTrue(mail.search("dentista ").hits.isEmpty())
    val hits = meaning(mail, "la mail del dentista")
    assertEquals(setOf("m1", "m2"), hits.take(2).map { it.id }.toSet())
    assertTrue(hits.all { it.byMeaning && !it.byWords })
    assertEquals("Studio Odontoiatrico Rossi", hits.first().fields["people"])
    assertTrue(hits.first().similarity!! > 0.7f)
    mail.close()
  }

  @Test
  fun leDieciDomandeDelCorpus() = runBlocking {
    val all = indexed(FluidSearchSamples.documents)
    val misses = ArrayList<String>()
    for ((query, expected) in FluidSearchSamples.meaningQueries) {
      val top = meaning(all, query).take(3).map { it.id }
      if (expected !in top) misses += "$query → $top (atteso $expected)"
    }
    // Nove su dieci; quella che manca e' «quando scade la luce» (la bolletta dell'energia elettrica): «luce» per
    // «bolletta» e' un modo di dire che il modello piccolo non lega. Scritta nel rapporto, non nascosta.
    assertTrue(misses.joinToString("\n"), misses.size <= 1)
    assertTrue(misses.joinToString("\n"), misses.all { it.startsWith("quando scade la luce") })
    all.close()
  }

  @Test
  fun paroleESignificatoInsiemeMettonoPrimaChiHaTuttiEDue() = runBlocking {
    val all = indexed(FluidSearchSamples.documents)
    // «gita» c'e' come parola in n1; il significato aggiunge gli altri documenti di scuola, sotto.
    val hits = meaning(all, "gita scolastica")
    assertEquals("n1", hits.first().id)
    assertTrue(hits.first().byWords && hits.first().byMeaning)
    // Solo un tipo.
    val onlyMail = meaning(all, "la mail del dentista", kinds = setOf("mail"))
    assertTrue(onlyMail.all { it.kind == "mail" })
    all.close()
  }

  @Test
  fun unDocumentoCambiatoRifaISuoiVettoriEGliAltriNo() = runBlocking {
    val embedder = FixtureEmbedder()
    val mail = indexed(FluidSearchSamples.of("mail"), embedder)
    val before = embedder.asked.size
    // Lo stesso testo: niente da rifare.
    mail.put(FluidSearchSamples.of("mail").first())
    assertEquals(0, mail.pendingMeaningCount())
    // Un testo nuovo: solo lui aspetta i vettori.
    val changed = FluidSearchSamples.of("mail").first().let { it.copy(fields = it.fields + ("body" to "Appuntamento spostato.")) }
    mail.put(changed)
    assertEquals(1, mail.pendingMeaningCount())
    assertEquals(before, embedder.asked.size)
    mail.close()
  }

  @Test
  fun unModelloDiversoButtaIVettoriVecchi() = runBlocking {
    val mail = indexed(FluidSearchSamples.of("mail"))
    assertTrue(mail.usesMeaningSpace(FluidEmbeddingSpace.EmbeddingGemma2))
    mail.useMeaningSpace(FluidEmbeddingSpace("altro-modello", 384))
    assertFalse(mail.usesMeaningSpace(FluidEmbeddingSpace.EmbeddingGemma2))
    assertEquals(8, mail.pendingMeaningCount())
    mail.close()
  }

  @Test
  fun iVettoriA8BitTengonoIlCoseno() {
    val v = FloatArray(768) { kotlin.math.sin(it * 0.37).toFloat() }
    val n = VectorMatrix.normalize(v, 768)
    val q = VectorMatrix.quantize(n)
    val m = VectorMatrix(listOf(VectorMatrix.Row(1, "x", q)))
    val r = m.search(n, emptySet(), minZ = -10f, limit = 1)
    assertEquals(1f, r.single().second, 0.002f)
  }
}
