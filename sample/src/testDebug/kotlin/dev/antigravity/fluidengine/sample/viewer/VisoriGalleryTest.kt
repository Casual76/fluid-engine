package dev.antigravity.fluidengine.sample.viewer

import android.content.Intent
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.ViewerGallery
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidPageSource
import dev.antigravity.fluidengine.ui.fluid.viewer.FluidViewerContent
import dev.antigravity.fluidengine.ui.fluid.viewer.fluidAnimatedImage
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.viewer.FluidViewerDetail
import dev.antigravity.fluidengine.viewer.FluidViewerDocument
import dev.antigravity.fluidengine.viewer.FluidViewerFamily
import dev.antigravity.fluidengine.viewer.FluidViewerFamilyType
import dev.antigravity.fluidengine.viewer.FluidViewerOpened
import dev.antigravity.fluidengine.viewer.FluidViewerPreset
import dev.antigravity.fluidengine.viewer.FluidViewerRegistry
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import dev.antigravity.fluidengine.viewer.FluidViewerType
import androidx.compose.material3.Text
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * La galleria «Visori» (F6C.5) con il dito, il back di sistema e la semantica veri, su Robolectric.
 *
 * I file sono quelli della galleria, copiati nella sua cartella e aperti da engine-viewer: il risolutore, il testo e
 * i colori, lo sconosciuto e l'eseguibile. I decodificatori d'immagine della piattaforma non lavorano sulla JVM
 * (`ImageDecoder` di Robolectric non decodifica i JPEG della galleria, pdfium non c'è), e la galleria prende da
 * [VisoriDeps] il contenuto delle foto, della WebP animata e del PDF; quelli veri li provano
 * `:engine-viewer:connectedDebugAndroidTest` e il tablet. I player sono finti ([FakePlayer]): la galleria li comanda
 * come ExoPlayer, e si giudica dall'intenzione di suonare (`playWhenReady`).
 */
/** I file delle famiglie che sulla JVM non hanno il decodificatore della piattaforma (`ImageDecoder`). */
private val NativeOnly = setOf("grafica-heic", "grafica-avif", "grafica-dng")

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-mdpi")
class VisoriGalleryTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val players = mutableMapOf<String, FakePlayer>()
  private val animation = FakeAnimation()
  private val pages = FluidViewerContent.Pages(FakePages(6))
  private val photos = mutableMapOf<String, FluidViewerContent>()
  /** Ogni file aperto, con il documento che l'ha deciso: il tipo e la famiglia. */
  private val seen = mutableMapOf<String, FluidViewerDocument>()
  private val deps = VisoriDeps(
    player = { _, uri -> FakePlayer().also { players[uri.toString()] = it } },
    content = { file ->
      when (file.tipo) {
        VisoreTipo.Pagine -> pages
        VisoreTipo.Animata -> photos.getOrPut(file.id) { fluidAnimatedImage(animation, file.name, aspectRatio = 1.5f) }
        VisoreTipo.Foto -> photos.getOrPut(file.id) {
          FluidViewerContent.Image(1.5f) { Box(Modifier.fillMaxSize().semantics { contentDescription = file.name }) }
        }
        else -> null
      }
    },
    opened = { file, document -> seen[file.id] = document },
  )

  /**
   * libarchive è una libreria nativa e sulla JVM non si carica: al suo posto, con gli stessi tipi e le stesse firme
   * del modulo vero (il risolutore resta quello di produzione), una famiglia che apre una pagina finta. Il modulo vero
   * lo provano i suoi test sul dispositivo (F6C.12).
   */
  private val archive = FluidViewerRegistry.families.first { it.id == "archive" }

  @Before fun archivioFinto() {
    FluidViewerRegistry.install(FakeArchiveFamily(archive))
  }

  @After fun archivioVero() {
    while (VisoriSenzaModuli.active) VisoriSenzaModuli.leave()
    FluidViewerRegistry.install(archive)
  }

  private fun scrollTo(tag: String) {
    compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag(tag))
    compose.waitForIdle()
  }

  private fun show() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) { ViewerGallery(deps) {} }
    }
    compose.waitForIdle()
  }

  private fun shown(text: String, substring: Boolean = false) =
    compose.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun described(text: String) =
    compose.onAllNodesWithContentDescription(text, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun waitFor(what: String, condition: () -> Boolean) {
    try {
      compose.waitUntil(10_000) { condition() }
    } catch (e: ComposeTimeoutException) {
      throw AssertionError("Non compare: $what", e)
    }
  }

  private fun back() {
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
  }

  private fun open(id: String) {
    scrollTo("visori-$id")
    compose.onNodeWithTag("visori-$id").performClick()
    compose.waitForIdle()
  }

  /** Sfoglia: un trascinamento di lato in alto nella pagina, lontano dai comandi del video e dal pannello. */
  private fun page(next: Boolean) {
    compose.onRoot().performTouchInput {
      val y = height * .3f
      if (next) swipe(Offset(width * .9f, y), Offset(width * .1f, y), 300)
      else swipe(Offset(width * .1f, y), Offset(width * .9f, y), 300)
    }
    compose.waitForIdle()
  }

  private val player: FakePlayer
    get() = players.entries.lastOrNull { "Fiori" in it.key }?.value
      ?: throw AssertionError("Il player del video non c'è: ${players.keys}")

  /** Il documento a pagine: il contatore non si legge (lo dice il tasto delle miniature), lo stato sì. */
  private fun pagesOf(count: Int) = described("Miniature delle pagine") && compose.onAllNodes(
    SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Pagina 1 di $count"), useUnmergedTree = true,
  ).fetchSemanticsNodes().isNotEmpty()

  private fun wave() = compose.onAllNodesWithTag("visori-onda").fetchSemanticsNodes().isNotEmpty()

  /** Il preset video: Play e la barra del tempo, senza la forma d'onda dell'audio. */
  private fun videoPage() = described("Riproduci") && described("Posizione di riproduzione") && !wave()

  @Test fun sfogliaDaFotoATestoCodiceEDocumentoFreddi() {
    val context = compose.activity.applicationContext
    val files = listOf("lancio", "diario", "codice", "quaderno").map { id -> VisoriFiles.gallery.first { it.id == id } }
    val prepared = kotlinx.coroutines.runBlocking { VisoriFiles.prepare(context, files, false) }
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        VisoriViewer(files, prepared, resumeVideo = false, onBack = {}, deps = deps)
      }
    }
    waitFor("foto di partenza") { described("Lancio.jpg") }
    page(next = true)
    waitFor("testo aperto sfogliando") { shown("Lunedì", substring = true) }
    page(next = true)
    waitFor("codice aperto sfogliando") { shown("package dev.antigravity", substring = true) }
    // A long line owns horizontal motion while wrap is off. Give this swipe to the pager.
    compose.onNodeWithContentDescription("A capo").performClick()
    compose.waitForIdle()
    page(next = true)
    waitFor("documento a sei pagine aperto sfogliando") { pagesOf(6) }
    compose.onNodeWithContentDescription("Miniature delle pagine").performClick()
    compose.onNode(hasContentDescription("Pagina 3 di 6") and hasClickAction()).performClick()
    compose.onNodeWithContentDescription("Miniature delle pagine")
      .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Pagina 3 di 6"))
  }

  @Test fun ogniTipoSiApreColSuoPreset() {
    show()
    val markers: List<Pair<String, () -> Boolean>> = listOf(
      "lancio" to { described("Lancio.jpg") },
      "collins" to { described("Eileen Collins.jpg") },
      "bolla" to { described("Bolla.webp") && animation.running },
      "fiori" to { videoPage() },
      "campanelle" to { described("Riproduci") && wave() },
      "quaderno" to { pagesOf(6) },
      "diario" to { shown("Lunedì", substring = true) },
      "codice" to { shown("package dev.antigravity", substring = true) },
      "scena" to { shown("Scena.blend") && shown("Apri con…") },
      "aggiornamento" to { shown("Aggiornamento.apk") && shown("È un'app da installare.", substring = true) },
    )
    for ((index, marker) in markers.withIndex()) {
      val (id, visible) = marker
      open(id)
      waitFor(id, visible)
      assertTrue("Contatore di $id", shown("${index + 1} di ${markers.size}"))
      back()
      waitFor("griglia dopo $id") { compose.onAllNodesWithTag("visori-$id").fetchSemanticsNodes().isNotEmpty() }
    }
    assertFalse("L'animazione si ferma uscendo", animation.running)
  }

  @Test fun ilVideoSiFermaQuandoLoSiSfogliaViaERiprendeSeLAppLoVuole() {
    show()
    open("fiori")
    waitFor("video") { shown("4 di 10") && videoPage() }
    assertTrue("Alla prima apertura parte da sé", player.wants)
    page(next = true)
    waitFor("audio") { wave() && shown("5 di 10") }
    assertFalse("Sfogliato via, il video è in pausa", player.wants)
    page(next = false)
    waitFor("di nuovo il video") { shown("4 di 10") }
    assertTrue("Al ritorno riparte: la galleria riprende di serie", player.wants)
    // L'utente lo mette in pausa: sfogliando via e tornando resta fermo.
    compose.runOnUiThread { player.pause() }
    compose.waitForIdle()
    assertFalse(player.wants)
    page(next = true)
    page(next = false)
    waitFor("video in pausa") { shown("4 di 10") }
    assertFalse("Era fermo: resta fermo", player.wants)
    back()

    // Con «Riprendi al ritorno» spento, il video sfogliato via resta in pausa anche se suonava.
    waitFor("griglia") { shown("Riprendi al ritorno") || compose.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty() }
    compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("Riprendi al ritorno"))
    compose.onNodeWithText("Riprendi al ritorno").performClick()
    compose.waitForIdle()
    open("fiori")
    waitFor("video, seconda volta") { shown("4 di 10") && videoPage() }
    assertTrue("Alla prima apertura parte da sé", player.wants)
    page(next = true)
    assertFalse(player.wants)
    page(next = false)
    waitFor("video") { shown("4 di 10") }
    assertFalse("L'app ha scelto di non riprendere", player.wants)
  }

  @Test fun loStrumentoDellAppPrendeLaBarraInBasso() {
    show()
    open("lancio")
    waitFor("foto") { described("Lancio.jpg") }
    assertTrue(described("Informazioni"))
    compose.onNodeWithContentDescription("Ritaglia").performClick()
    compose.waitForIdle()
    waitFor("ritaglio") { compose.onAllNodesWithTag("visori-ritaglio").fetchSemanticsNodes().isNotEmpty() }
    assertTrue(described("Ruota"))
    assertTrue(described("Formato libero"))
    assertTrue(described("Fine"))
    assertFalse("La barra è dello strumento", described("Informazioni"))
    assertFalse(described("Ritaglia"))
    compose.onNodeWithContentDescription("Ruota").performClick()
    compose.onNodeWithContentDescription("Formato libero").performClick()
    compose.waitForIdle()
    val state = compose.onNodeWithTag("visori-ritaglio").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription)
    assertEquals("Ritaglio 1:1, ruotata di 90 gradi", state)
    // Indietro chiude prima lo strumento, poi il visore.
    back()
    waitFor("barra del visore") { described("Informazioni") && described("Ritaglia") }
    assertEquals(0, compose.onAllNodesWithTag("visori-ritaglio").fetchSemanticsNodes().size)
    compose.onNodeWithContentDescription("Ritaglia").performClick()
    compose.onNodeWithContentDescription("Fine").performClick()
    compose.waitForIdle()
    assertTrue(described("Informazioni"))
    // Lo strumento c'è solo sulle foto: sul documento a pagine la barra non lo offre.
    back()
    open("quaderno")
    waitFor("pagine") { pagesOf(6) }
    assertFalse(described("Ritaglia"))
    // Il pannello info dice com'è stato aperto il file.
    compose.onNodeWithContentDescription("Informazioni").performClick()
    compose.waitForIdle()
    waitFor("info") { shown("Documento a pagine", substring = true) && shown("Quaderno di campo.pdf") }
  }

  @Test fun loSconosciutoNonEMaiUnVicoloCieco() {
    show()
    open("scena")
    waitFor("scheda") { shown("Scena.blend") && shown("Apri con…") }
    assertTrue(shown("Condividi"))
    compose.onNodeWithText("Apri con…").performClick()
    compose.waitForIdle()
    val chooser = shadowOf(compose.activity).nextStartedActivity
    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    @Suppress("DEPRECATION") val view = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    assertEquals(Intent.ACTION_VIEW, view.action)
    assertEquals("android.resource", view.data?.scheme)
    compose.onNodeWithText("Condividi").performClick()
    compose.waitForIdle()
    @Suppress("DEPRECATION") val send = shadowOf(compose.activity).nextStartedActivity.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    assertEquals(Intent.ACTION_SEND, send.action)
    // C'è sempre la via d'uscita: Indietro del visore e quello di sistema.
    assertTrue(described("Indietro"))
    back()
    waitFor("griglia") { compose.onAllNodesWithTag("visori-scena").fetchSemanticsNodes().isNotEmpty() }

    // L'eseguibile: l'avviso, il nome con l'estensione vera, e «Apri con…» lo stesso.
    open("aggiornamento")
    waitFor("eseguibile") { shown("Aggiornamento.apk") && shown("Apri con…") }
    assertTrue(shown("È un'app da installare.", substring = true))
    assertTrue(described("Indietro"))
  }

  @Test fun rottaCollegamentoERipristino() {
    show()
    compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasTestTag("visori-collegamento"))
    compose.onNodeWithTag("visori-collegamento").performClick()
    compose.waitForIdle()
    waitFor("codice dal collegamento") { shown("8 di 10") && shown("package dev.antigravity", substring = true) }
    // Il codice non va a capo e scorre di lato: verso destra, dal suo bordo, il dito passa al pager.
    page(next = false)
    waitFor("diario") { shown("7 di 10") && shown("Lunedì", substring = true) }
    compose.activityRule.scenario.recreate()
    show()
    waitFor("lo stesso file dopo la ricreazione") { shown("7 di 10") && shown("Lunedì", substring = true) }
    back()
    waitFor("griglia") { compose.onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isNotEmpty() }
    assertFalse(shown("7 di 10"))
  }

  @Test fun iTipiDiUniversalConverterSiApronoColLoroPreset() {
    show()
    scrollTo("visori-converter")
    compose.onNodeWithTag("visori-converter").performClick()
    compose.waitForIdle()
    waitFor("lista di Universal Converter") { shown("colors_50x50.png") }
    // engine-viewer-all: ogni tipo delle prove di Universal Converter va al suo preset, non più al dump esadecimale.
    val families = mapOf("uc-disegno" to "graphics", "uc-markdown" to "data", "uc-pagina" to "data",
      "uc-catalogo" to "data", "uc-config" to "data", "uc-relazione" to "office", "uc-progetto" to "archive",
      "uc-modello" to "3d")
    val markers: Map<String, () -> Boolean> = mapOf(
      "uc-colori" to { described("colors_50x50.png") },
      "uc-foto" to { described("foto.jpg") },
      "uc-animazione" to { described("animazione.gif") && animation.running },
      "uc-lettera" to { shown("Cara Alessia,", substring = true) },
      "uc-documento" to { pagesOf(6) },
      "uc-audio" to { wave() },
      "uc-suono" to { wave() },
      "uc-filmato" to { videoPage() },
      "uc-mondo" to { shown("mondo.wld") && shown("Mostra i primi byte") },
      "uc-vuoto" to { shown("Il file è vuoto") },
      "uc-sparito" to { shown("sparito.pdf") && shown("è stato spostato o cancellato", substring = true) },
    )
    for (file in VisoriFiles.converter) {
      val id = file.id
      scrollTo("converter-$id")
      compose.onNodeWithTag("converter-$id").performClick()
      compose.waitForIdle()
      val family = families[id]
      if (family != null) {
        waitFor("$id aperto da $family") { seen[id]?.type?.family == family }
        assertNull("$id: nessun ripiego (${seen[id]?.failure?.message})", seen[id]?.failure)
      } else {
        waitFor(id, requireNotNull(markers[id]) { "Manca il segno di $id" })
      }
      assertTrue("Via d'uscita in $id", described("Indietro"))
      if (id == "uc-mondo") {
        // Il dump esadecimale di Universal Converter c'è ancora, ma solo a richiesta.
        compose.onNodeWithText("Mostra i primi byte").performClick()
        waitFor("dump") { shown("00000000", substring = true) }
      }
      back()
      waitFor("lista dopo $id") { compose.onAllNodesWithTag("converter-$id").fetchSemanticsNodes().isNotEmpty() }
    }
    assertEquals("Il DOCX è un documento Office", "office", seen["uc-relazione"]?.type?.family)
    assertEquals("Il Markdown è reso dalla famiglia dei dati", "data", seen["uc-markdown"]?.type?.family)
    assertEquals("La lettera UTF-16 resta testo di serie", FluidViewerPreset.Text, seen["uc-lettera"]?.type?.preset)
  }

  /**
   * F6C.15: ogni file di ogni famiglia si apre dalla sua miniatura, col suo modulo e il contatore della famiglia, e
   * torna alla griglia. I tipi che sulla JVM non hanno il decodificatore della piattaforma (HEIC, AVIF, il DNG:
   * `ImageDecoder`) devono comunque arrivare a una pagina che non è un vicolo cieco.
   */
  @Test fun ogniFileDelleFamiglieSiApreDallaSuaMiniatura() {
    show()
    for (family in VisoriFamiglia.entries.drop(1)) {
      val files = family.files
      for ((index, file) in files.withIndex()) {
        val id = file.id
        if (id !in NativeOnly) {
          assertEquals("Il risolutore manda $id a ${family.id}", family.id, FluidViewerType.of(file.mime, file.name).family)
        }
        open(id)
        waitFor("$id aperto") { seen[id] != null }
        val document = requireNotNull(seen[id])
        if (id in NativeOnly) {
          waitFor("$id, mai un vicolo cieco") {
            seen[id]?.type?.family == family.id || shown("Apri con…") || described(file.name)
          }
        } else {
          assertEquals("$id: la famiglia", family.id, document.type.family)
          assertNull("$id: nessun ripiego (${document.failure?.message})", document.failure)
        }
        assertTrue("Contatore di $id", shown("${index + 1} di ${files.size}"))
        assertTrue("Via d'uscita in $id", described("Indietro"))
        back()
        waitFor("griglia dopo $id") { compose.onAllNodesWithTag("visori-$id").fetchSemanticsNodes().isNotEmpty() }
      }
    }
  }

  @Test fun senzaModuliNonEMaiUnVicoloCieco() {
    val before = FluidViewerRegistry.families.map { it.id }.toSet()
    assertTrue("La galleria ha le famiglie", before.containsAll(setOf("office", "archive", "data", "graphics", "3d")))
    show()
    scrollTo("visori-senza")
    compose.onNodeWithTag("visori-senza").performClick()
    compose.waitForIdle()
    waitFor("la scena") { shown("Solo engine-viewer") }
    assertTrue("Nella scena il registro è senza famiglie", FluidViewerRegistry.families.isEmpty())
    val files = VisoriSenzaModuliFiles
    for ((index, file) in files.withIndex()) {
      val id = file.id
      scrollTo("senza-$id")
      // La riga è già composta durante la copia degli asset. Accetta il tocco quando mostra il tipo risolto.
      val outcome = dev.antigravity.fluidengine.sample.visoriOutcome(
        dev.antigravity.fluidengine.viewer.FluidViewerType.of(file.mime, file.name))
      waitFor("$id pronto nella lista") {
        compose.onAllNodes(hasText("Qui: $outcome") and hasAnyAncestor(hasTestTag("senza-$id")),
          useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
      }
      compose.onNodeWithTag("senza-$id").performClick()
      compose.waitForIdle()
      waitFor("$id aperto senza moduli") { seen[id] != null }
      val document = requireNotNull(seen[id])
      assertNull("$id: nessuna famiglia", document.type.family)
      if (document.content is FluidViewerContent.Unknown) {
        waitFor("$id sconosciuto con «Apri con…»") { shown(file.name) && shown("Apri con…") && shown("Condividi") }
      }
      assertTrue("Contatore di $id", shown("${index + 1} di ${files.size}"))
      assertTrue("Via d'uscita in $id", described("Indietro"))
      back()
      waitFor("lista dopo $id") { compose.onAllNodesWithTag("senza-$id").fetchSemanticsNodes().isNotEmpty() }
    }
    assertTrue("Il DOCX senza Office è sconosciuto", seen["office-docx"]?.content is FluidViewerContent.Unknown)
    assertTrue("Lo ZIP senza archivi è sconosciuto", seen["archivio-zip"]?.content is FluidViewerContent.Unknown)
    assertTrue("Il modello senza 3D è sconosciuto", seen["modello-glb"]?.content is FluidViewerContent.Unknown)
    // Il Markdown lo legge anche engine-viewer: testo e codice, non è un vicolo cieco.
    assertEquals(FluidViewerPreset.Text, seen["dati-md"]?.type?.preset)
    // «Apri con…» passa al sistema la copia della galleria, in sola lettura.
    scrollTo("senza-office-docx")
    compose.onNodeWithTag("senza-office-docx").performClick()
    compose.waitForIdle()
    waitFor("DOCX sconosciuto") { shown("Apri con…") }
    compose.onNodeWithText("Apri con…").performClick()
    compose.waitForIdle()
    val chooser = shadowOf(compose.activity).nextStartedActivity
    @Suppress("DEPRECATION") val view = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    assertEquals(Intent.ACTION_VIEW, view.action)
    assertEquals("content", view.data?.scheme)
    assertEquals("Canale di accesso.docx", view.data?.lastPathSegment)
    back()
    back()
    waitFor("la galleria") { compose.onAllNodesWithTag("visori-senza").fetchSemanticsNodes().isNotEmpty() }
    compose.waitForIdle()
    assertEquals("Uscendo, le famiglie tornano", before, FluidViewerRegistry.families.map { it.id }.toSet())
  }

  @Test fun laPaginaPesiLeggeFamiliesJson() {
    // La copia negli asset è quella del build (copyVisoriFamilies), uguale alla lista ammessa dell'engine.
    val disk = listOf(java.io.File("../engine-viewer/families.json"), java.io.File("engine-viewer/families.json"))
      .first { it.isFile }
    val expected = VisoriWeights.parse(disk.readText(Charsets.UTF_8))
    val context = compose.activity.applicationContext
    val packaged = kotlinx.coroutines.runBlocking { VisoriWeights.read(context) }
    assertEquals("Gli asset portano families.json com'è", expected, packaged)
    assertEquals(setOf("office", "archive", "data", "graphics", "3d", "ar", "ocr"), expected.map { it.family }.toSet())
    show()
    scrollTo("visori-pesi")
    compose.onNodeWithTag("visori-pesi").performClick()
    compose.waitForIdle()
    waitFor("la pagina") { shown("Quanto costa ogni modulo") }
    for (module in expected) {
      scrollTo("pesi-${module.module}")
      for (row in weightRows(module).take(VisoriAbis.size)) {
        assertTrue("${module.module}: ${row.title}", shown(row.title))
        row.meta?.let { meta -> assertTrue("${module.module}: $meta", shown(meta)) }
      }
      if (module.abiBytes.isNotEmpty()) {
        assertEquals("${module.module}: le quattro architetture", VisoriAbis, module.abiBytes.keys.toList())
      }
      for (library in module.libraries) {
        assertTrue("${module.module}: la licenza di ${library.name}", library.license.isNotBlank())
      }
    }
    // Le licenze dei moduli in fondo alla pagina, le stesse della pagina Impostazioni.
    assertTrue(VisoriLicenses.isNotEmpty())
  }

  // --- Finti ---------------------------------------------------------------------------------------------------------

  /**
   * Un `Player` di media3-common che ricorda solo l'intenzione: suonare o no. Dice di essere sospeso (il focus audio):
   * un player che suona chiede un fotogramma dopo l'altro per la posizione, e Compose non sarebbe mai inattivo.
   */
  @OptIn(UnstableApi::class)
  private class FakePlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    var wants = false
      private set
    private var prepared = false

    override fun getState(): State = State.Builder()
      .setAvailableCommands(Player.Commands.Builder().addAll(
        Player.COMMAND_PLAY_PAUSE, Player.COMMAND_PREPARE, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
        Player.COMMAND_GET_TIMELINE, Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_SET_VIDEO_SURFACE,
        Player.COMMAND_RELEASE,
      ).build())
      .setPlayWhenReady(wants, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
      .setPlaybackState(if (prepared) Player.STATE_READY else Player.STATE_IDLE)
      .setPlaybackSuppressionReason(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
      .setPlaylist(listOf(MediaItemData.Builder("file").setMediaItem(MediaItem.EMPTY).setDurationUs(12_000_000L)
        .setIsSeekable(true).build()))
      .build()

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
      wants = playWhenReady
      return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
      prepared = true
      return Futures.immediateVoidFuture()
    }

    override fun handleSetVideoOutput(videoOutput: Any): ListenableFuture<*> = Futures.immediateVoidFuture()
    override fun handleClearVideoOutput(videoOutput: Any?): ListenableFuture<*> = Futures.immediateVoidFuture()
    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
  }

  /** Un drawable che si anima per finta: dice se gira. */
  private class FakeAnimation : Drawable(), Animatable {
    var running = false
      private set

    override fun start() { running = true }
    override fun stop() { running = false }
    override fun isRunning() = running
    override fun draw(canvas: Canvas) {}
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Drawable.getOpacity")
    override fun getOpacity() = PixelFormat.OPAQUE
    override fun getIntrinsicWidth() = 480
    override fun getIntrinsicHeight() = 320
  }

  /** L'archivio finto: i tipi del modulo vero, una pagina con il nome del file. */
  private class FakeArchiveFamily(real: FluidViewerFamily) : FluidViewerFamily {
    override val id: String = real.id
    override val types: List<FluidViewerFamilyType> = real.types
    override suspend fun open(request: FluidViewerRequest): FluidViewerOpened =
      FluidViewerOpened(FluidViewerContent.Custom {
        Text("Archivio finto: ${request.source.name}", Modifier.semantics { contentDescription = "Archivio finto" })
      }, listOf(FluidViewerDetail("Formato", request.type.label)))
  }

  /** Un documento di [count] pagine verticali: il preset vero lo sfoglia, la resa è una bitmap bianca. */
  private class FakePages(override val count: Int) : FluidPageSource {
    override suspend fun size(index: Int) = IntSize(420, 595)
    override suspend fun render(index: Int, target: IntSize) = ImageBitmap(target.width.coerceAtLeast(1), target.height.coerceAtLeast(1))
  }
}
