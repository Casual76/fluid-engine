package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.antigravity.fluidengine.files.FluidFileBrowser
import dev.antigravity.fluidengine.files.FluidFileBrowserTags
import dev.antigravity.fluidengine.files.FluidFileItem
import dev.antigravity.fluidengine.files.FluidFileLayout
import dev.antigravity.fluidengine.files.FluidFilePage
import dev.antigravity.fluidengine.files.FluidFileQuery
import dev.antigravity.fluidengine.files.FluidFileSource
import dev.antigravity.fluidengine.files.rememberFluidFileBrowserState
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.search.FluidSearchCipher
import dev.antigravity.fluidengine.search.FluidSearchIndex
import dev.antigravity.fluidengine.search.FluidSearchSamples
import dev.antigravity.fluidengine.search.FluidSearchState
import dev.antigravity.fluidengine.search.fluidSearchResults
import dev.antigravity.fluidengine.search.rememberFluidSearchState
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
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

/**
 * R3-M3 · Le correzioni della review R3 nello sfoglia-file e nella ricerca, viste nella pagina vera:
 * - (Muse 6) una sorgente dell'app con pagine che si sovrappongono (gli stessi id due volte) non fa cadere l'elenco;
 * - (Muse 18) un indice che non risponde (la cassaforte chiusa sotto la ricerca) non sembra «Nessun risultato» e non
 *   lascia i risultati della domanda di prima; ricordare la domanda in quel momento non fa cadere l'app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class R3m3FilesSearchTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: View
  private lateinit var finger: MaterialFinger
  private lateinit var search: FluidSearchState
  private val indexes = ArrayList<FluidSearchIndex>()

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    indexes.forEach { it.close() }
    FluidGlassDiagnostics.resetForTesting()
  }

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

  private fun count(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().size

  /** Vero se un nodo dice [text], scritto o per TalkBack. */
  private fun says(text: String) = compose.onAllNodes(SemanticsMatcher("dice «$text»") { node ->
    node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.contains(text) } ||
      node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it.contains(text) }
  }, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  // --- Muse 6 · lo sfoglia-file -----------------------------------------------------------------------------------

  /**
   * Gli allegati di una casella, a pagine di 4 col segno a spostamento: ogni pagina ripete le ultime 2 della pagina prima
   * (come una sorgente in cui arriva un messaggio nuovo mentre si scorre). In memoria si ordinano per nome: i doppioni
   * finiscono uno accanto all'altro, sullo schermo insieme.
   */
  private class Overlapping : FluidFileSource {
    override val id = "allegati"
    override val title = "Allegati"

    override suspend fun list(query: FluidFileQuery): FluidFilePage {
      val start = query.cursor?.toInt() ?: 0
      val end = minOf(Count, start + 4)
      val items = (start until end).map {
        FluidFileItem("a$it", "Allegato %02d.txt".format(it), mimeType = "text/plain", sizeBytes = 100L + it,
          modifiedAtMillis = 1_764_000_000_000L)
      }
      return FluidFilePage(items, next = if (end >= Count) null else (end - 2).toString())
    }

    companion object {
      const val Count = 10
    }
  }

  @Test fun `sfoglia-file - pagine sovrapposte con gli stessi id, ogni file una volta`() {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            val state = rememberFluidFileBrowserState(layout = FluidFileLayout.List)
            val source = remember { Overlapping() }
            FluidFileBrowser(state = state, sources = listOf(source), locations = null, recents = null)
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    advance(600)
    finger = MaterialFinger(view)
    waitFor("la posizione degli allegati") { count(FluidFileBrowserTags.location("allegati")) > 0 }
    val at = compose.onNodeWithTag(FluidFileBrowserTags.location("allegati"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
    finger.down(at)
    advance(48)
    finger.up(at)
    // Le pagine arrivano una dopo l'altra (la riga «altre» chiede la prossima): con i doppioni l'elenco cadeva qui.
    waitFor("tutti gli allegati") { (0 until Overlapping.Count).all { count(FluidFileBrowserTags.item("a$it")) > 0 } }
    advance(600)
    for (i in 0 until Overlapping.Count) {
      assertEquals("l'allegato a$i una volta sola", 1, count(FluidFileBrowserTags.item("a$i")))
    }
  }

  // --- Muse 18 · la ricerca in pagina -----------------------------------------------------------------------------

  /** Una cassaforte che si chiude (all'impronta, a tempo): chiusa, non apre e non chiude più niente. */
  private class Vault : FluidSearchCipher {
    @Volatile var locked = false

    override suspend fun seal(plain: ByteArray, associated: ByteArray): ByteArray {
      check(!locked) { "cassaforte chiusa" }
      return plain.reversedArray()
    }

    override suspend fun open(sealed: ByteArray, associated: ByteArray): ByteArray {
      check(!locked) { "cassaforte chiusa" }
      return sealed.reversedArray()
    }
  }

  private fun lockedIndex(vault: Vault): FluidSearchIndex = runBlocking {
    val idx = FluidSearchIndex.inMemory(FluidSearchSamples.schema, vault)
    idx.put(FluidSearchSamples.of("notes"))
    indexes += idx
    idx
  }

  private fun showSearch(index: FluidSearchIndex) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        search = rememberFluidSearchState(index)
        FluidScreen(title = "Note", search = search.spec(prompt = "Cerca nelle note")) {
          fluidSearchResults(search, onOpen = {})
        }
      }
    }
    advance(400)
  }

  /** La domanda nuova parte (la ricerca gira sul filo dell'indice, in tempo vero) e finisce. */
  private fun settled() {
    advance(160)
    waitFor("la ricerca finita") { !search.searching }
    advance(200)
  }

  @Test fun `ricerca - un indice che non risponde non sembra nessun risultato`() {
    val vault = Vault()
    showSearch(lockedIndex(vault))
    vault.locked = true
    search.query = "riunione"
    settled()
    assertFalse("un errore detto come «nessun risultato»", says("Nessun risultato"))
    assertTrue("l'errore si vede e TalkBack lo dice", says("Impossibile cercare"))
  }

  @Test fun `ricerca - dopo un errore non restano i risultati della domanda di prima`() {
    val vault = Vault()
    showSearch(lockedIndex(vault))
    search.query = "riunione"
    waitFor("i risultati") { search.results.hits.isNotEmpty() }
    advance(200)
    assertTrue(says("Riunione di lunedì"))
    vault.locked = true
    search.query = "verbale"
    settled()
    assertFalse("i risultati di «riunione» sotto «verbale»", says("Riunione di lunedì"))
    assertTrue(says("Impossibile cercare"))
    // Ricordare la domanda con la cassaforte chiusa non fa cadere l'app (prima: un'eccezione nel lancio).
    search.saveRecent()
    advance(200)
    vault.locked = false
    search.query = "riunione"
    waitFor("i risultati con la cassaforte di nuovo aperta") { search.results.hits.isNotEmpty() }
    advance(200)
    assertFalse(says("Impossibile cercare"))
    assertTrue(says("Riunione di lunedì"))
  }
}
