package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.ai.bridge.AiToolClient
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.search.FluidEmbeddingSpace
import dev.antigravity.fluidengine.search.FluidPampaSearch
import dev.antigravity.fluidengine.search.FluidSearchIndex
import dev.antigravity.fluidengine.search.FluidSearchSamples
import dev.antigravity.fluidengine.search.FluidSearchSemantics
import dev.antigravity.fluidengine.search.FluidSearchState
import dev.antigravity.fluidengine.search.FluidSemanticIndexer
import dev.antigravity.fluidengine.search.FluidTextEmbedder
import dev.antigravity.fluidengine.search.fluidSearchResults
import dev.antigravity.fluidengine.search.rememberFluidSearchState
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.nio.ByteBuffer
import java.time.Duration
import java.util.Base64
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** I vettori veri di EmbeddingGemma 2 270M per i dati di prova (la stessa risorsa delle prove di engine-search). */
internal class F8p22FixtureEmbedder : FluidTextEmbedder {
  override val space: FluidEmbeddingSpace = FluidEmbeddingSpace.EmbeddingGemma2
  private val vectors: Map<String, FloatArray> by lazy {
    val stream = javaClass.classLoader!!.getResourceAsStream("fluid-search/meaning-embeddinggemma2-270m.tsv")!!
    stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
      lines.filter { it.isNotBlank() && !it.startsWith("#") }.associate { line ->
        val tab = line.lastIndexOf('\t')
        val bytes = Base64.getDecoder().decode(line.substring(tab + 1))
        val scale = ByteBuffer.wrap(bytes).float
        val v = FloatArray(bytes.size - 4) { bytes[4 + it] * scale }
        val n = kotlin.math.sqrt(v.fold(0.0) { a, x -> a + x * x }).toFloat()
        line.substring(0, tab).replace("\\n", "\n") to FloatArray(v.size) { v[it] / n }
      }
    }
  }

  override suspend fun embed(texts: List<String>): List<FloatArray> = texts.map { vectors[it] ?: error("nessun vettore: $it") }
}

/**
 * F8.22 · La ricerca nella galleria, con l'indice vero (SQLite FTS4) e il campo di ricerca dell'engine: i risultati per
 * tipo con la parola corretta, TalkBack (righe, intestazioni, area viva, il campo), il significato che arriva sfocato e
 * fermo con «Riduci movimento», e «Cerca in Pampa» con le cinque app finte della build di debug attraverso il ponte vero.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class F8p22SearchUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var state: FluidSearchState
  private val indexes = ArrayList<FluidSearchIndex>()

  @Before fun start() {
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() = indexes.forEach { it.close() }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      left -= 16
    }
  }

  private fun waitFor(what: String, condition: () -> Boolean) {
    repeat(400) {
      if (condition()) return
      advance(32)
      Thread.sleep(3)
    }
    throw AssertionError("non è successo: $what")
  }

  private fun index(app: String, meaning: Boolean = false): FluidSearchIndex = runBlocking {
    val idx = FluidSearchIndex.inMemory(FluidSearchSamples.schema)
    idx.put(FluidSearchSamples.of(app))
    if (meaning) FluidSemanticIndexer(idx, F8p22FixtureEmbedder()).update()
    indexes += idx
    idx
  }

  private fun show(index: FluidSearchIndex, app: String, embedder: FluidTextEmbedder? = null, reduced: Boolean = false) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides if (reduced) FluidMotionPolicy(0f) else FluidMotionPolicy.Default) {
          state = rememberFluidSearchState(index, embedder)
          val kinds = remember { F8p22Kinds.filterKeys { k -> FluidSearchSamples.of(app).any { it.kind == k } } }
          FluidScreen(title = "Note", search = state.spec(prompt = "Cerca nelle note", kinds = kinds)) {
            fluidSearchResults(state, kinds, onOpen = {})
          }
        }
      }
    }
    advance(400)
  }

  private fun has(text: String, substring: Boolean = false) =
    compose.onAllNodes(hasText(text, substring = substring), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  @Test fun `le scene sono nell'indice`() {
    assertEquals(listOf("ricerca-note", "ricerca-significato", "cerca-in-pampa", "ricerca-misure"), F8p22Section.scenes.map { it.id })
    assertTrue(NewElementSections.contains(F8p22Section))
    assertEquals(4, F8p22TryItems.size)
  }

  @Test fun `un errore di battitura - i gruppi per tipo e la parola corretta`() {
    show(index("notes"), "notes")
    state.query = "riunone"
    waitFor("i risultati") { state.results.hits.isNotEmpty() }
    advance(200)
    assertTrue(has("Risultati per riunione · cercato anche «riunone»"))
    // Le intestazioni di FluidSectionHeader sono in maiuscolo, come le sezioni delle liste di iOS.
    assertTrue(has("NOTE · 2"))
    assertTrue(has("TRASCRIZIONI · 1"))
    assertTrue(has("ALLEGATI · 1"))
    // Il titolo e il pezzo, con la parola trovata in evidenza (nel testo annotato).
    val n1 = state.results.hits.first { it.id == "n1" }
    assertEquals(listOf("Riunione"), n1.title!!.highlighted)
    assertTrue(has("Riunione di lunedì"))
  }

  @Test fun `TalkBack - righe, intestazioni, area viva e campo`() {
    show(index("notes"), "notes")
    // Il campo si presenta con le sue parole.
    assertTrue(compose.onAllNodes(hasText("Cerca nelle note", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() ||
      compose.onAllNodes(SemanticsMatcher("descrizione") { node ->
        node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.contains("Cerca") }
      }, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    state.query = "riunione "
    waitFor("i risultati") { state.results.hits.size >= 4 }
    advance(200)
    // Le intestazioni dei gruppi sono titoli.
    val headings = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true).fetchSemanticsNodes()
    assertTrue(headings.size >= 3)
    // Quanti risultati ci sono lo dice un'area viva.
    val live = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite), useUnmergedTree = true)
      .fetchSemanticsNodes()
    assertTrue(live.any { it.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { d -> d.contains("risultati") } })
    // Ogni riga e' un nodo solo col titolo e il pezzo.
    val row = compose.onAllNodes(hasText("Riunione di lunedì", substring = true)).fetchSemanticsNodes().first()
    val text = row.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
    assertTrue(text, text.contains("Riunione di lunedì") && text.contains("verbale della riunione"))
  }

  @Test fun `il significato arriva sfocato`() {
    show(index("mail", meaning = true), "mail", F8p22FixtureEmbedder())
    state.query = "la mail del dentista"
    waitFor("i risultati per significato") { state.results.meaning && state.results.hits.isNotEmpty() }
    advance(32)
    assertEquals(setOf("m1", "m2"), state.results.hits.take(2).map { it.id }.toSet())
    assertTrue(has("per significato", substring = true))
    val arriving = compose.onAllNodes(SemanticsMatcher.expectValue(FluidSearchSemantics.Arriving, true), useUnmergedTree = true).fetchSemanticsNodes()
    assertTrue("righe che arrivano: ${arriving.size}", arriving.isNotEmpty())
  }

  @Test fun `con Riduci movimento il significato arriva fermo`() {
    show(index("mail", meaning = true), "mail", F8p22FixtureEmbedder(), reduced = true)
    state.query = "la mail del dentista"
    waitFor("i risultati per significato") { state.results.meaning && state.results.hits.isNotEmpty() }
    advance(32)
    assertTrue(has("Conferma appuntamento del 28/11", substring = true))
    val arriving = compose.onAllNodes(SemanticsMatcher.expectValue(FluidSearchSemantics.Arriving, true), useUnmergedTree = true).fetchSemanticsNodes()
    assertTrue(arriving.isEmpty())
  }

  @Test fun `cerca in Pampa con le cinque app della galleria e il ponte vero`() = runBlocking {
    val context = compose.activity.applicationContext
    F8p22Demo.prepare(context)
    val client = AiToolClient(context)
    val apps = F8p22Demo.discovered(context, client)
    assertEquals(FluidSearchSamples.apps.values.toList(), apps.map { it.label })
    val last = FluidPampaSearch(client, discover = { apps }).search("inglese ").toList().last()
    val shown = last.shown.map { it.app.label }
    assertEquals(listOf("ClasseViva", "PampAI"), shown)
    assertEquals("c4", last.groups.first { it.app.label == "ClasseViva" }.hits.first().id)
    // Notes bloccata: lo dice, senza dati.
    F8p22Demo.notesLocked = true
    try {
      val locked = FluidPampaSearch(client, discover = { apps }).search("riunione ").toList().last()
      assertTrue(locked.groups.first { it.app.label == "Pampa Notes" }.locked)
      assertFalse(locked.groups.first { it.app.label == "Pampa Notes" }.hits.isNotEmpty())
    } finally {
      F8p22Demo.notesLocked = false
    }
  }
}
