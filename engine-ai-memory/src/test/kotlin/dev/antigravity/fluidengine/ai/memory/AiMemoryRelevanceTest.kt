package dev.antigravity.fluidengine.ai.memory

import dev.antigravity.fluidengine.ai.orchestrator.HistoryCompactor
import dev.antigravity.fluidengine.search.FluidEmbeddingSpace
import dev.antigravity.fluidengine.search.FluidTextEmbedder
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * F9.3 · La ricerca per pertinenza al posto del blocco fisso, e il tetto dei gettoni: una domanda porta nel prompt solo i
 * ricordi che c'entrano. Il confronto e' col blocco di oggi di PampAI (`MemoryRepository.promptBlock`: i fissati, poi i
 * piu' recenti, fino a 40 righe e 1 500 caratteri, in ogni domanda), rifatto qui riga per riga.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiMemoryRelevanceTest {
  private val clock = TestClock()

  /** I ricordi di prova: una persona vera ne ha di tutti i tipi, detti in giorni diversi. */
  private val corpus = listOf(
    Triple("è in 5F al liceo Galilei", "scuola.classe", listOf("scuola", "classe", "compiti", "verifiche")),
    Triple("ha matematica il lunedì e il giovedì alla prima ora", "scuola.orario", listOf("scuola", "orario", "lezioni")),
    Triple("la prof di inglese è la Bianchi", "scuola.docenti", listOf("scuola", "inglese", "professori")),
    Triple("è vegetariana", "cibo.dieta", listOf("cibo", "cena", "pranzo", "ricette", "ristorante")),
    Triple("è allergica alle noci", "salute.allergie", listOf("cibo", "allergie", "ricette", "ristorante")),
    Triple("abita a Firenze, in zona Campo di Marte", "casa.citta", listOf("casa", "città", "meteo", "quartiere")),
    Triple("prende il 23 alla fermata Dalmazia", "trasporti.autobus", listOf("autobus", "fermata", "bus", "orari")),
    Triple("gioca a pallavolo il martedì sera", "sport.pallavolo", listOf("sport", "allenamento", "pallavolo")),
    Triple("ha una sorella, Marta, di 12 anni", "famiglia.sorella", listOf("famiglia", "sorella", "regali")),
    Triple("ascolta soprattutto indie italiano", "musica.gusti", listOf("musica", "canzoni", "playlist", "concerti")),
    Triple("preferisce risposte brevi", "stile.risposte", listOf("risposte", "stile")),
    Triple("lavora in un bar il sabato mattina", "lavoro.turni", listOf("lavoro", "turni", "sabato")),
  )

  private suspend fun seeded(extra: Int = 0, config: AiMemoryConfig = AiMemoryConfig(zone = MemoryTestTime.Zone, autoSave = true)): AiMemoryBook {
    val b = AiMemoryBook.open(AiMemoryStore.inMemory(), config, clock)
    corpus.forEachIndexed { i, (text, key, topics) ->
      clock.now = MemoryTestTime.Now - (corpus.size - i) * MemoryTestTime.Day
      b.remember(AiMemoryProposal(text, key, topics), AiMemorySource.chat(i.toLong()))
    }
    // Altri ricordi di riempimento, come quelli che si accumulano in mesi: parlano d'altro.
    repeat(extra) { i ->
      clock.now = MemoryTestTime.Now - (extra - i) * MemoryTestTime.Hour
      b.remember(AiMemoryProposal("ha visto il film numero $i al cinema Odeon", "film.visti$i", listOf("film", "cinema")), AiMemorySource.chat(100L + i))
    }
    clock.now = MemoryTestTime.Now
    return b
  }

  private suspend fun AiMemoryBook.top(question: String, n: Int = 3) = relevant(question, n).map { it.memory.text }

  @Test fun `una domanda trova i ricordi che c'entrano, non gli altri`() = runBlocking {
    val b = seeded()
    assertEquals("è in 5F al liceo Galilei", b.top("che compiti ho per domani?").first())
    assertTrue("è vegetariana" in b.top("consigliami una ricetta per cena"))
    assertTrue("è allergica alle noci" in b.top("consigliami una ricetta per cena"))
    assertEquals("prende il 23 alla fermata Dalmazia", b.top("a che ora passa il mio autobus?").first())
    assertEquals("abita a Firenze, in zona Campo di Marte", b.top("che tempo fa domani in città?").first())
    assertEquals("gioca a pallavolo il martedì sera", b.top("a che ora ho allenamento?").first())
    // Con gli errori di battitura e i plurali dell'indice di F8.22.
    assertEquals("ha una sorella, Marta, di 12 anni", b.top("cosa regalo a mia sorela?").first())
    // Una domanda che non c'entra con niente non porta niente.
    assertTrue(b.relevant("quanto fa 15 per 340?").isEmpty())
    assertTrue(b.prompt("quanto fa 15 per 340?").isEmpty)
  }

  @Test fun `il tetto dei gettoni tiene anche con trecento ricordi, e il blocco di oggi e' sempre pieno`() = runBlocking {
    val b = seeded(extra = 280)
    assertEquals(292, b.memories.value.size)
    val prompt = b.prompt("che compiti ho per domani? e la verifica di matematica?")
    assertTrue(prompt.tokens <= AiMemoryPromptBudget().maxTokens)
    assertTrue(prompt.memories.any { it.text == "è in 5F al liceo Galilei" })
    assertFalse(prompt.text.contains("cinema"))
    // Il blocco di oggi: i 40 piu' recenti, cioe' i film, e la classe resta fuori.
    val legacy = legacyBlock(b.memories.value)
    val legacyTokens = HistoryCompactor.estimateTokens(legacy)
    assertFalse(legacy.contains("5F"))
    assertTrue(legacy.length in 1_400..1_500)
    println("F9p3 pertinenza: blocco nuovo ${prompt.tokens} gettoni (${prompt.memories.size} ricordi), blocco di oggi $legacyTokens gettoni, ogni domanda")
    // Una domanda qualsiasi, oggi, paga sempre il blocco intero; qui niente.
    assertEquals(0, b.prompt("chi ha scritto i Promessi Sposi?").tokens)
    // Ma un numero nominato trova il ricordo che lo dice («il film numero 15»).
    assertTrue(b.relevant("ti ricordi il film numero 15?").any { it.memory.text.contains("numero 15 ") })
  }

  @Test fun `il tetto stretto taglia dal meno pertinente e lo conta`() = runBlocking {
    val b = seeded()
    val tight = b.prompt("consigliami una ricetta per cena, un ristorante in città", AiMemoryPromptBudget(maxTokens = 70))
    assertTrue(tight.tokens <= 70)
    assertTrue(tight.memories.isNotEmpty())
    assertTrue(tight.leftOut > 0)
  }

  @Test fun `i fissati e quelli per ogni domanda entrano sempre, dentro il tetto`() = runBlocking {
    val b = seeded()
    val name = b.remember(AiMemoryProposal("si chiama Giulia", "nome", listOf("nome"), always = true), AiMemorySource.chat(9)).memory!!
    val style = b.memories.value.first { it.key == "stile.risposte" }
    b.setPinned(style.id, true)
    val p = b.prompt("quanto fa 15 per 340?")
    assertEquals(listOf("preferisce risposte brevi", "si chiama Giulia"), p.memories.map { it.text })
    assertTrue(p.text.startsWith("Cose che sai dell'utente"))
    assertTrue(p.text.contains("- si chiama Giulia (2026-11-26)"))
    assertTrue(name.always)
  }

  @Test fun `nel prompt la fonte e la scadenza, e mai un ricordo che sembra un codice`() = runBlocking {
    val b = AiMemoryBook.open(AiMemoryStore.inMemory(), AiMemoryConfig(zone = MemoryTestTime.Zone, autoSave = true), clock)
    b.remember(AiMemoryProposal("ha la verifica di matematica venerdì 27", "scuola.verifica", listOf("verifica", "matematica"),
      expiresAtMillis = java.time.LocalDate.of(2026, 11, 28).atStartOfDay(MemoryTestTime.Zone).toInstant().toEpochMilli()),
      AiMemorySource.app("dev.antigravity.classevivaexpressive", "ClasseViva"))
    b.importLegacy(listOf(AiMemoryLegacyRow(4, "il codice del portone è 4521 9087 3321", MemoryTestTime.Now, null, pinned = true)))
    val p = b.prompt("quando ho la verifica di matematica?")
    assertEquals(listOf("- ha la verifica di matematica venerdì 27 (da ClasseViva, 2026-11-26; vale fino al 2026-11-27)"), p.text.lines().drop(1))
  }

  @Test fun `col modello del significato una domanda senza parole in comune trova il ricordo`() = runBlocking {
    val store = AiMemoryStore.inMemory()
    val config = AiMemoryConfig(zone = MemoryTestTime.Zone, embedder = TopicEmbedder, autoSave = true)
    val b = AiMemoryBook.open(store, config, clock)
    corpus.forEach { (text, key, topics) -> b.remember(AiMemoryProposal(text, key, topics), AiMemorySource.chat(1)) }
    // Senza il significato «cosa cucino stasera?» non trova niente: nessuna parola in comune col ricordo o i suoi argomenti.
    val plain = AiMemoryBook.open(store, AiMemoryConfig(zone = MemoryTestTime.Zone, autoSave = true), clock)
    assertTrue(plain.relevant("cosa cucino stasera?").none { it.memory.text == "è vegetariana" })
    val found = b.relevant("cosa cucino stasera?", 3)
    assertTrue(found.any { it.memory.text == "è vegetariana" && it.byMeaning })
    // I vettori stanno nel file della memoria (cifrato con lei): al riavvio non si ricalcolano, solo la domanda.
    b.close()
    TopicEmbedder.calls = 0
    val again = AiMemoryBook.open(store, config, clock)
    assertEquals(0, TopicEmbedder.calls)
    assertTrue(again.relevant("cosa cucino stasera?", 3).any { it.memory.text == "è vegetariana" })
    assertEquals(1, TopicEmbedder.calls)
  }

  /** Il blocco di oggi di PampAI (`MemoryRepository.promptBlock`), riga per riga: i fissati, poi i piu' recenti. */
  private fun legacyBlock(memories: List<AiMemory>): String {
    val ordered = memories.sortedWith(compareByDescending<AiMemory> { it.pinned }.thenByDescending { it.createdAtMillis })
    val lines = ArrayList<String>()
    var chars = 0
    for (m in ordered) {
      val line = "- ${m.text}"
      if (lines.size >= 40 || chars + line.length > 1_500) break
      lines += line
      chars += line.length + 1
    }
    return lines.joinToString("\n")
  }

  /**
   * Un modello del significato finto ma onesto: un vettore per argomento (il cibo, la scuola, i trasporti, …), deciso da
   * parole che il ricordo e la domanda non hanno in comune. Basta a provare il percorso del significato senza il modello
   * vero (157 MiB, provato in F8.22).
   */
  private object TopicEmbedder : FluidTextEmbedder {
    var calls = 0
    override val space = FluidEmbeddingSpace("prova-argomenti", 8)
    private val topics = listOf(
      Regex("cucin|cena|vegetarian|mangi|ricett|noci|allergic"),
      Regex("scuol|classe|compit|liceo|prof|matematica"),
      Regex("autobus|fermata|tram"),
      Regex("firenze|abita|citt"),
      Regex("pallavolo|sport|allenament"),
      Regex("sorella|famiglia"),
      Regex("musica|indie|canzon"),
    )

    override suspend fun embed(texts: List<String>): List<FloatArray> = texts.map { t ->
      calls++
      val v = FloatArray(8) { 0.05f }
      val low = t.lowercase()
      topics.forEachIndexed { i, r -> if (r.containsMatchIn(low)) v[i] = 1f }
      if (topics.none { it.containsMatchIn(low) }) v[7] = 1f
      val n = sqrt(v.fold(0f) { a, x -> a + x * x })
      FloatArray(8) { v[it] / n }
    }

    override suspend fun release() = Unit
  }
}
