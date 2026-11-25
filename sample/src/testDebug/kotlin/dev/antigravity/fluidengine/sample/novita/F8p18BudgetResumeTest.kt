package dev.antigravity.fluidengine.sample.novita

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidFileViewer
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidMediaSession
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidTextSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerPosition
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerResumeProvider
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerResumeSlot
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerRaster
import java.util.Collections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** Un posto della posizione finto: quella da applicare, quella da offrire, e ciò che il preset salva. */
private class FakeSlot(
  override val position: FluidViewerPosition? = null,
  stale: FluidViewerPosition? = null,
) : FluidViewerResumeSlot {
  var offered by mutableStateOf(stale)
  override val stale: FluidViewerPosition? get() = offered
  val saved = Collections.synchronizedList(mutableListOf<FluidViewerPosition>())
  var dismissed = false
  override fun save(position: FluidViewerPosition) {
    saved += position
  }
  override fun dismissStale() {
    dismissed = true
    offered = null
  }
}

/** Un player finto: la durata nota, il salto e la pausa. */
private class FakeSession(duration: Long) : FluidMediaSession {
  override var playing by mutableStateOf(false)
  override var positionMillis by mutableStateOf(0L)
  override val durationMillis: Long? = duration
  override val canSeek: Boolean = true
  val seeks = mutableListOf<Long>()
  override fun play() { playing = true }
  override fun pause() { playing = false }
  override fun seekTo(positionMillis: Long) {
    seeks += positionMillis
    this.positionMillis = positionMillis
  }
}

/**
 * F8.18 · Il bilancio dei documenti grandi, **automatico e invisibile**, su un documento di 5000 pagine con le bitmap del
 * registro della memoria vero (le stesse mani di F7.13): la barra di scorrimento è di tutto il documento (anche per
 * TalkBack), si rendono solo le pagine su cui la lista si ferma, la memoria resta sotto il tetto. E la ripresa dallo
 * stesso punto: pagine (pagina e zoom) e testo (riga), con la posizione offerta quando il file è cambiato; un audio
 * riparte dall'inizio (F8.18b).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class F8p18BudgetResumeTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val count = 5000

  /** Il documento: 5000 pagine A4, una orizzontale ogni 250; ogni resa passa dal registro della memoria. */
  private inner class Giant : FluidPageSource {
    val rendered: MutableList<Int> = Collections.synchronizedList(mutableListOf())
    override val count = this@F8p18BudgetResumeTest.count
    override suspend fun size(index: Int) = if (index % 250 == 249) IntSize(842, 595) else IntSize(595, 842)
    override suspend fun render(index: Int, target: IntSize): ImageBitmap {
      rendered += index
      return checkNotNull(FluidViewerRaster.render(compose.activity, target) { size -> ImageBitmap(size.width, size.height) })
    }
  }

  private fun waitPage(number: Int) = compose.waitUntil(20_000) {
    compose.onAllNodesWithContentDescription("Pagina $number di $count").fetchSemanticsNodes().isNotEmpty()
  }

  private fun counter(): String = compose.onNodeWithContentDescription("Miniature delle pagine").fetchSemanticsNode()
    .config.getOrNull(SemanticsProperties.StateDescription).orEmpty()

  private fun currentPage(): Int = counter().removePrefix("Pagina ").substringBefore(" di").toInt()

  private fun show(source: FluidPageSource, slot: FluidViewerResumeSlot? = null) {
    compose.setContent {
      FluidTheme(EngineSettings()) {
        val document = remember { FluidViewerContent.Pages(source) }
        FluidViewerResumeProvider(resume = { slot }) {
          FluidFileViewer(1, content = { document })
        }
      }
    }
  }

  @Test fun `bilancio - la barra percorre tutto il documento e si rendono solo le pagine in vista`() {
    val context = compose.activity
    val process = FluidGlassResources.get(context)
    val base = process.budget.usage(FluidMemoryCategory.VIEWER).usedBytes
    val giant = Giant()
    show(giant)
    waitPage(1)
    // La barra di tutto il documento c'è anche a maniglia nascosta, per TalkBack: un cursore che salta.
    val scrubber = compose.onNode(hasContentDescription("Scorri il documento"), useUnmergedTree = true)
    assertNotNull(scrubber.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo))
    scrubber.performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }
    compose.waitUntil(20_000) { currentPage() > 3500 }
    val landed = currentPage()
    assertTrue("la maniglia all'80% porta verso pagina 4000: $landed", abs(landed - 4000) < 150)
    compose.waitForIdle()
    // Solo le pagine vicine a dove la lista si è fermata (e alla prima): mai le migliaia attraversate.
    val rendered = giant.rendered.toSet()
    assertTrue("pagine rese: ${rendered.size}", rendered.size < 40)
    assertTrue("pagine lontane rese: $rendered", rendered.all { it < 10 || abs(it + 1 - landed) < 10 })
    // La memoria del visore resta sotto il tetto del processo.
    val usage = process.budget.usage(FluidMemoryCategory.VIEWER)
    assertTrue("usati ${usage.usedBytes} su ${usage.limitBytes}", usage.usedBytes <= usage.limitBytes)
    assertTrue(usage.residentBytes > base)

    // La barra è giusta: a metà documento la maniglia sta a metà.
    compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
      .performScrollToIndex(2499)
    compose.waitUntil(20_000) { abs(currentPage() - 2500) < 5 }
    val range = checkNotNull(scrubber.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo))
    val fraction = (range.current - range.range.start) / (range.range.endInclusive - range.range.start)
    assertTrue("la maniglia a metà: $fraction", abs(fraction - 0.5f) < 0.02f)

    // Fino in fondo: il contatore dice l'ultima pagina e la maniglia è in fondo.
    compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 100%"))
      .performScrollToIndex(count - 1)
    compose.waitUntil(20_000) { currentPage() == count }
    val end = checkNotNull(scrubber.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo))
    assertTrue("la maniglia in fondo: ${end.current}", end.current > 0.98f)
  }

  @Test fun `riprendi - il documento riparte dalla pagina e dallo zoom salvati, e salva a riposo`() {
    val slot = FakeSlot(position = FluidViewerPosition(page = 1234, zoom = 1.5f))
    show(Giant(), slot)
    compose.waitUntil(20_000) { runCatching { currentPage() == 1235 }.getOrDefault(false) }
    compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 150%")).assertExists()
    compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Zoom 150%"))
      .performScrollToIndex(1300)
    compose.waitForIdle()
    compose.waitUntil(10_000) { slot.saved.any { it.page == 1300 } }
    assertEquals(1.5f, slot.saved.last { it.page == 1300 }.zoom, 0.001f)
    // Nessuna capsula: la posizione era della stessa revisione.
    assertTrue(compose.onAllNodes(hasText("Riprendi da", substring = true)).fetchSemanticsNodes().isEmpty())
  }

  @Test fun `riprendi - un file cambiato offre la posizione di prima e non la applica da solo`() {
    val slot = FakeSlot(stale = FluidViewerPosition(page = 41))
    show(Giant(), slot)
    waitPage(1)
    assertEquals(1, currentPage())
    compose.waitUntil(10_000) {
      compose.onAllNodes(hasText("Riprendi da pagina 42"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    // Il tasto della barra porta la sua scritta come nome per TalkBack.
    compose.onNode(hasContentDescription("Riprendi da pagina 42")).performSemanticsAction(SemanticsActions.OnClick)
    compose.waitUntil(10_000) { currentPage() == 42 }
    assertTrue(slot.dismissed)
    compose.waitUntil(10_000) {
      compose.onAllNodes(hasText("Riprendi da pagina 42"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    }
  }

  @Test fun `riprendi - un testo riparte dalla riga salvata`() {
    val lines = List(3000) { "Riga ${it + 1} degli appunti" }
    val source = object : FluidTextSource {
      override val lineCount = lines.size
      override suspend fun readLines(start: Int, count: Int) = lines.drop(start).take(count)
    }
    val slot = FakeSlot(position = FluidViewerPosition(page = 1500))
    compose.setContent {
      FluidTheme(EngineSettings()) {
        val document = remember { FluidViewerContent.Text(source) }
        FluidViewerResumeProvider(resume = { slot }) { FluidFileViewer(1, content = { document }) }
      }
    }
    compose.waitUntil(20_000) {
      compose.onAllNodes(hasText("Riga 1501 degli appunti"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    assertTrue(compose.onAllNodes(hasText("Riga 1 degli appunti"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
  }

  @Test fun `riprendi - un testo cambiato offre la riga di prima e la capsula ci porta`() {
    val lines = List(3000) { "Riga ${it + 1} degli appunti" }
    val source = object : FluidTextSource {
      override val lineCount = lines.size
      override suspend fun readLines(start: Int, count: Int) = lines.drop(start).take(count)
    }
    val slot = FakeSlot(stale = FluidViewerPosition(page = 269))
    compose.setContent {
      FluidTheme(EngineSettings()) {
        val document = remember { FluidViewerContent.Text(source) }
        FluidViewerResumeProvider(resume = { slot }) { FluidFileViewer(1, content = { document }) }
      }
    }
    compose.waitUntil(20_000) {
      compose.onAllNodes(hasText("Riga 1 degli appunti"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
        compose.onAllNodes(hasContentDescription("Riprendi dalla riga 270")).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNode(hasContentDescription("Riprendi dalla riga 270")).performSemanticsAction(SemanticsActions.OnClick)
    compose.waitUntil(20_000) {
      compose.onAllNodes(hasText("Riga 270 degli appunti"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    assertTrue(slot.dismissed)
    compose.waitUntil(10_000) { compose.onAllNodes(hasContentDescription("Riprendi dalla riga 270")).fetchSemanticsNodes().isEmpty() }
  }

  @Test fun `riprendi - un audio riparte dall'inizio e non salva il punto`() {
    val session = FakeSession(duration = 120_000)
    // F8.18b: audio e video non riprendono. Una riga salvata prima, col secondo di un audio, si legge come l'inizio;
    // anche una posizione da offrire resta ferma: il preset dei media non legge il posto.
    val slot = FakeSlot(position = FluidViewerPosition.decode("p=0;o=0;z=1;m=60000"), stale = FluidViewerPosition(page = 3))
    assertTrue(slot.position!!.isStart)
    var shown by mutableStateOf(true)
    compose.setContent {
      FluidTheme(EngineSettings()) {
        val content = remember { FluidViewerContent.Media(session) }
        if (shown) FluidViewerResumeProvider(resume = { slot }) { FluidFileViewer(1, content = { content }) }
      }
    }
    compose.waitForIdle()
    compose.runOnIdle {
      session.play()
      session.positionMillis = 75_000
    }
    compose.mainClock.advanceTimeBy(6_000)
    compose.runOnIdle { session.pause() }
    compose.waitForIdle()
    assertTrue(compose.onAllNodes(hasContentDescription("Riprendi da pagina 4")).fetchSemanticsNodes().isEmpty())
    compose.runOnIdle { shown = false }
    compose.waitForIdle()
    assertEquals(emptyList<Long>(), session.seeks)
    assertEquals(emptyList<FluidViewerPosition>(), slot.saved.toList())
    assertTrue(!slot.dismissed)
  }
}
