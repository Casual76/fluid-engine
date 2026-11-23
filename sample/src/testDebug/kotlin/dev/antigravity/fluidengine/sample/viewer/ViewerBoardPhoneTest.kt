package dev.antigravity.fluidengine.sample.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.R
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.IosShare
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.viewer.*
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidTextDecoder
import dev.antigravity.fluidengine.viewer.FluidViewerDocument
import dev.antigravity.fluidengine.viewer.data.fluidDataViewerInfo
import dev.antigravity.fluidengine.viewer.rememberFluidViewerFiles
import dev.antigravity.fluidengine.viewer.office.officeInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h891dp-mdpi")
open class ViewerBoardPhoneTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  protected open val screen = 412

  @Test fun audio() {
    val cover = image(R.drawable.viewer_campanelle_cover)
    val session = object : FluidMediaSession, FluidAudioSpeed {
      override val playing = false
      override val positionMillis = 84_000L
      override val durationMillis = 222_000L
      override val canSeek = true
      override fun play() = Unit
      override fun pause() = Unit
      override fun seekTo(positionMillis: Long) = Unit
      override var playbackSpeed by mutableStateOf(1f)
        private set
      override val canChangePlaybackSpeed = true
      override fun changePlaybackSpeed(speed: Float) { playbackSpeed = speed }
    }
    val info = FluidViewerInfo(FluidFileInfo("Lago all’alba.m4a"), title = "Lago all’alba",
      subtitle = "Pampa · Note di campo", thumbnail = cover, audioWaveform = { Waveform(session) },
      chapters = listOf(0L, 60_000L, 120_000L, 180_000L).mapIndexed { index, time ->
        FluidAudioChapter("Capitolo ${index + 1}", time)
      }, facts = listOf("AAC", "256 kbps"), actions = listOf(FluidViewerInfoAction("Apri con…") {}),
      audioFormat = "AAC · 256 kbps", audioOutput = {})
    scenes("audio", info, renderer = { media, visible ->
      FluidMediaPreset(media as FluidViewerContent.Media, visible, strings = FluidMediaPresetStrings(
        skipSeconds = 15, skipBack = "Indietro di 15 secondi", skipForward = "Avanti di 15 secondi"))
    }) { FluidViewerContent.Media(session) }
  }

  @Test fun pdf() = document(inspector = false)
  @Test fun info() = document(inspector = true)

  private fun document(inspector: Boolean) {
    val cover = image(R.drawable.viewer_notes_cover)
    val info = FluidViewerInfo(FluidFileInfo("Quaderno di campo.pdf", "application/pdf", 2_400_000),
      title = "Quaderno di campo", thumbnail = cover, facts = listOf("6 pagine", "A4", "PDF 1.7", "Testo ricercabile"),
      sections = listOf(FluidViewerInfoSection("Documento", listOf(
        FluidViewerInfoRow("Titolo", "Quaderno di campo"), FluidViewerInfoRow("Autore", "Alessio"),
        FluidViewerInfoRow("Creato con", "LibreOffice 24.8"), FluidViewerInfoRow("Creato", "14 nov 2026, 18:02")))),
      searchPages = { emptyList() })
    // Android's PdfRenderer is native. This uses the sample art while exercising the production page layout.
    val pages = FluidViewerContent.Pages(object : FluidPageSource {
      override val count = 6
      override suspend fun size(index: Int) = IntSize(330, 467)
      override suspend fun render(index: Int, target: IntSize) = cover
    })
    scenes(if (inspector) "info" else "pdf", info, inspector) { pages }
  }

  @Test fun code() {
    val source = compose.activity.resources.openRawResource(R.raw.viewer_codice).use {
      requireNotNull(FluidTextDecoder.read(it, "VisoriFiles.kt"))
    }
    scenes("testo", FluidViewerInfo(FluidFileInfo("VisoriFiles.kt"))) { FluidViewerContent.Text(source) }
  }

  @Test fun executable() = unknown("apk", "Aggiornamento.apk", "application/vnd.android.package-archive")
  @Test fun unknownFile() = unknown("sconosciuto", "Scena.blend", null)

  private fun unknown(scene: String, name: String, mime: String?) {
    val info = FluidViewerInfo(FluidFileInfo(name, mime, 12_400_000), facts = if (mime != null)
      listOf("Fluid Glass 4.0.1", "Firmato", "Non installato") else listOf("BLEND"))
    scenes(scene, info, share = false, renderer = { content, _ ->
      FluidUnknownPreset(content as FluidViewerContent.Unknown, firstBytes = { byteArrayOf(1, 2, 3) })
    }) { FluidViewerContent.Unknown(info.file, openWith = {}, share = {}) }
  }

  @Test fun word() = family("word", VisoriFamiglia.Office, "office-docx")
  @Test fun spreadsheet() = family("foglio", VisoriFamiglia.Office, "office-xlsx")
  @Test fun slides() = family("slide", VisoriFamiglia.Office, "office-pptx")
  @Test fun json() = family("json", VisoriFamiglia.Dati, "dati-json")

  private fun family(scene: String, family: VisoriFamiglia, id: String) {
    val sample = family.files.single { it.id == id }
    val files = runBlocking { VisoriFiles.prepare(compose.activity, listOf(sample), family.scene) }
    var opened by mutableStateOf<FluidViewerDocument?>(null)
    scenes(scene, FluidViewerInfo(files.single().info, subtitle = sample.title), ready = { opened != null },
      infoOverride = { opened?.let { if (family == VisoriFamiglia.Office) it.officeInfo() else fluidDataViewerInfo(it) } }) {
      val reader = rememberFluidViewerFiles(files)
      val document = reader.document(0)
      SideEffect { opened = document?.takeIf { it.type.family == family.id && it.failure == null } }
      reader.content(0)
    }
  }

  private fun image(resource: Int) =
    BitmapFactory.decodeResource(compose.activity.resources, resource).asImageBitmap()

  private fun scenes(scene: String, info: FluidViewerInfo, inspector: Boolean = false, share: Boolean = true,
    ready: () -> Boolean = { true }, renderer: (@Composable (FluidViewerContent, Boolean) -> Unit)? = null,
    infoOverride: () -> FluidViewerInfo? = { null },
    file: @Composable () -> FluidViewerContent,
  ) {
    var dark by mutableStateOf(false)
    compose.setContent {
      FluidTheme(EngineSettings(themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)) {
        val current = file()
        FluidViewerInfoProvider({ infoOverride() ?: info }) {
          val toolbar: dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope.(Int) -> Unit = {
            if (share) button("share", FluidIcons.IosShare, "Condividi", {}, placement = FluidToolbarPlacement.BottomBar)
          }
          if (renderer == null) FluidFileViewer(1, content = { current }, toolbar = toolbar, modifier = Modifier.fillMaxSize())
          else FluidFileViewer(1, content = { current }, toolbar = toolbar, modifier = Modifier.fillMaxSize(), renderContent = renderer)
        }
      }
    }
    compose.waitUntil(15_000, ready)
    compose.waitForIdle()
    if (inspector) compose.onNodeWithContentDescription("Informazioni").performClick()
    compose.onNodeWithContentDescription(if (inspector) "Chiudi" else "Informazioni").assertIsDisplayed()
    capture(scene, "chiaro")
    verifyLayout(scene)
    compose.runOnIdle { dark = true }
    compose.waitForIdle()
    capture(scene, "scuro")
    verifyLayout(scene)
  }

  private fun verifyLayout(scene: String) {
    assertEquals(screen.toFloat(), compose.onRoot().fetchSemanticsNode().boundsInRoot.width, 1f)
    fun action(label: String) = compose.onNodeWithContentDescription(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    fun text(label: String) = compose.onNodeWithText(label).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
    fun beside(vararg labels: String) {
      val nodes = labels.map(::action)
      assertTrue("$scene $screen: $nodes", nodes.first().left >= 0 && nodes.last().right <= screen + 1 &&
        nodes.zipWithNext().all { (a, b) -> a.right <= b.left + 4.1f })
    }
    beside("Indietro", "Informazioni")
    when (scene) {
      "audio" -> {
        val wave = action("Posizione di riproduzione")
        assertEquals(44f, wave.height, 1f)
        assertTrue(wave.width <= 303f && wave.width > 250f)
        assertTrue("L'onda parte sotto il titolo: $wave", wave.top in 493f..516f)
        text("−2:18")
        beside("Indietro di 15 secondi", "Riproduci", "Avanti di 15 secondi")
      }
      "pdf", "word", "slide" -> {
        assertEquals(34f, action("Miniature delle pagine").height, 2f)
        beside(if (scene == "slide") "Presenta" else "Trova", "Condividi")
      }
      "info" -> {
        val close = action("Chiudi")
        val facts = text("6 pagine")
        assertTrue("$scene $screen: $close $facts", close.bottom < facts.top)
        assertTrue(text("DOCUMENTO").top >= facts.bottom)
      }
      "testo" -> beside("Dimensione del testo", "A capo", "Trova", "Condividi")
      "apk", "sconosciuto" -> {
        val name = if (scene == "apk") "Aggiornamento.apk" else "Scena.blend"
        val heading = compose.onAllNodesWithText(name, useUnmergedTree = true).fetchSemanticsNodes()
          .first { it.boundsInRoot.top > 150f }.boundsInRoot
        assertEquals(296f, heading.top, 2f)
        val open = text("Apri con…")
        val share = text("Condividi")
        assertEquals(46f, open.height, 2f)
        assertTrue(open.bottom + 9f <= share.top)
      }
      "foglio" -> {
        val share = action("Condividi")
        val tab = compose.onNodeWithContentDescription("Entrate, foglio", substring = true)
          .assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertTrue("Scheda $tab, condivisione $share", tab.right <= share.left || tab.bottom < share.top)
      }
      "json" -> {
        beside("Albero", "Testo", "Condividi")
        val first = action("nome, stringa, Fluid Glass")
        assertEquals(33f, first.height, 1f)
      }
    }
  }

  private fun capture(scene: String, theme: String) {
    if (screen != 412) return
    val output = System.getenv("FLUID_F6C18_EVIDENCE") ?: return
    val folder = File(output, theme).apply { mkdirs() }
    val nodes = compose.onRoot(useUnmergedTree = true).fetchSemanticsNode()
    val report = buildString {
      fun appendNode(node: androidx.compose.ui.semantics.SemanticsNode) {
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        val tag = node.config.getOrNull(SemanticsProperties.TestTag)
        if (text != null || description != null || tag != null) appendLine(
          listOf(tag, description, text, node.boundsInRoot).joinToString("\t") { it?.toString().orEmpty() })
        node.children.forEach(::appendNode)
      }
      appendNode(nodes)
    }
    File(folder, "$scene.tsv").writeText(report, Charsets.UTF_8)
    val window = compose.activity.window
    val view = window.decorView
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    try {
      var result = PixelCopy.ERROR_UNKNOWN
      compose.runOnIdle {
        PixelCopy.request(window, android.graphics.Rect(0, 0, view.width, view.height), bitmap,
          { result = it }, Handler(Looper.getMainLooper()))
      }
      shadowOf(Looper.getMainLooper()).idle()
      assertEquals(PixelCopy.SUCCESS, result)
      File(folder, "$scene.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } finally {
      bitmap.recycle()
    }
  }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h780dp-mdpi")
class ViewerBoardNarrowPhoneTest : ViewerBoardPhoneTest() {
  override val screen = 360
}
