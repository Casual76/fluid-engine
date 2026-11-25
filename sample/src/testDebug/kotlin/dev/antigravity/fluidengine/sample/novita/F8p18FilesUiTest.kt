package dev.antigravity.fluidengine.sample.novita

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.provider.DocumentsContract
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToKeyAction
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import dev.antigravity.fluidengine.files.FluidFileBrowser
import dev.antigravity.fluidengine.files.FluidFileBrowserState
import dev.antigravity.fluidengine.files.FluidFileBrowserTags
import dev.antigravity.fluidengine.files.FluidFileLayout
import dev.antigravity.fluidengine.files.FluidFileRecents
import dev.antigravity.fluidengine.files.FluidFileSortKey
import dev.antigravity.fluidengine.files.FluidFileSources
import dev.antigravity.fluidengine.files.rememberFluidFileBrowserState
import dev.antigravity.fluidengine.files.rememberFluidFileLocations
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Un provider di documenti finto (gli indirizzi dell'albero di Android), per le cartelle concesse senza il sistema. */
class F8p18TreeProvider : ContentProvider() {
  companion object {
    const val Authority = "dev.prova.f8p18.documenti"
    fun tree(): Uri = DocumentsContract.buildTreeDocumentUri(Authority, "radice")
  }

  override fun onCreate() = true

  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?,
    sortOrder: String?): Cursor {
    val columns = projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
    val cursor = MatrixCursor(columns)
    fun row(id: String, name: String, mime: String, size: Long?) = cursor.addRow(columns.map {
      when (it) {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id
        DocumentsContract.Document.COLUMN_DISPLAY_NAME -> name
        DocumentsContract.Document.COLUMN_MIME_TYPE -> mime
        DocumentsContract.Document.COLUMN_SIZE -> size
        DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 1_764_000_000_000L
        else -> null
      }
    })
    if (uri.pathSegments.lastOrNull() == "children") {
      row("drive-1", "Preventivo.pdf", "application/pdf", 120_000)
      row("drive-2", "Verbale.txt", "text/plain", 2_000)
    } else {
      row("radice", "Drive di prova", DocumentsContract.Document.MIME_TYPE_DIR, null)
    }
    return cursor
  }

  override fun getType(uri: Uri): String? = null
  override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}

/**
 * F8.18 · Lo sfoglia-file col dito vero (eventi del sistema con l'orologio che avanza, `MaterialFinger`) e col vetro vero:
 * Sfoglia, una cartella dell'app in griglia e in elenco, l'ordine, la ricerca senza accenti, i nomi per TalkBack, una
 * cartella lunga a pagine con lo scorrimento rapido, l'apertura di un file nel visore e i recenti; le cartelle concesse
 * con lo Storage Access Framework (un provider finto e il selettore di sistema simulato); «Riduci movimento».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class F8p18FilesUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: View
  private lateinit var finger: MaterialFinger
  private lateinit var state: FluidFileBrowserState
  private lateinit var root: File

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
    root = File(compose.activity.filesDir, "sfoglia-${System.nanoTime()}").apply {
      mkdirs()
      File(this, "alfa.txt").apply { writeText("Prima riga di alfa\nseconda riga\n"); setLastModified(1_000_000_000_000) }
      File(this, "Éclair.md").apply { writeText("# Éclair\n\nUna nota.\n"); setLastModified(1_700_000_000_000) }
      File(this, "zeta.txt").apply { writeText("ultimo\n"); setLastModified(1_500_000_000_000) }
      File(this, "Bozze").apply { mkdirs(); File(this, "una.txt").writeText("1"); File(this, "due.txt").writeText("2") }
      File(this, "Note del corso").apply {
        mkdirs()
        for (i in 1..450) File(this, "Nota %04d.txt".format(i)).writeText("Nota $i\n")
      }
    }
    FluidFileRecents.get(compose.activity).clear()
  }

  @After fun stop() = FluidGlassDiagnostics.resetForTesting()

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
    repeat(500) {
      if (condition()) return
      advance(32)
      Thread.sleep(4)
    }
    throw AssertionError("non è successo: $what")
  }

  private fun has(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
  private fun hasDescription(text: String) =
    compose.onAllNodes(hasContentDescription(text, substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun center(tag: String): Offset = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center

  /** La pagina nuova entra da destra: si tocca quando è ferma. */
  private fun settle() = advance(900)

  private fun tap(tag: String) {
    val at = center(tag)
    finger.down(at)
    advance(48)
    finger.up(at)
    advance(32)
  }

  private fun clickDescription(text: String) {
    // Il menu che si chiude ridà il vetro al suo tasto: si aspetta che il tasto ci sia di nuovo.
    waitFor("il tasto «$text»") {
      compose.onAllNodes(hasContentDescription(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    compose.onNode(hasContentDescription(text), useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
    advance(400)
  }

  private fun clickText(text: String) {
    compose.onNode(hasText(text)).performSemanticsAction(SemanticsActions.OnClick)
    advance(400)
  }

  private val picked = mutableListOf<String>()

  private fun show(reduced: Boolean = false, saf: Boolean = false, pick: Boolean = false) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host,
          LocalFluidMotionPolicy provides if (reduced) FluidMotionPolicy(durationScale = 0f) else FluidMotionPolicy.Default) {
          Box(Modifier.fillMaxSize()) {
            state = rememberFluidFileBrowserState()
            val source = remember { FluidFileSources.appFolder(root, "prova", "In Fluid Glass", "La cartella dell'app") }
            FluidFileBrowser(state = state, sources = listOf(source),
              locations = if (saf) rememberFluidFileLocations() else null,
              onPick = if (pick) ({ files -> picked += files.map { it.name } }) else null)
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    advance(600)
    finger = MaterialFinger(view)
  }

  private fun openAppFolder() {
    waitFor("la pagina Sfoglia") { has(FluidFileBrowserTags.location("prova")) }
    tap(FluidFileBrowserTags.location("prova"))
    waitFor("la cartella con i suoi file") { has(FluidFileBrowserTags.item("alfa.txt")) }
    settle()
  }

  @Test fun `la scena e' nell'indice`() {
    assertEquals(listOf("sfoglia-file", "pdf-gigante", "riprendi"), F8p18Section.scenes.map { it.id })
    assertTrue(NewElementSections.contains(F8p18Section))
  }

  @Test fun `sfoglia - la cartella in griglia, in elenco, ordinata e cercata`() {
    show()
    assertTrue(has(FluidFileBrowserTags.Recents))
    openAppFolder()
    // Le cartelle prima dei file; ogni tessera dice a TalkBack nome, tipo e data, una cartella quanti elementi ha.
    val folder = compose.onNodeWithTag(FluidFileBrowserTags.item("Bozze"), useUnmergedTree = true).fetchSemanticsNode()
    val file = compose.onNodeWithTag(FluidFileBrowserTags.item("alfa.txt"), useUnmergedTree = true).fetchSemanticsNode()
    assertTrue("cartella ${folder.boundsInRoot}, file ${file.boundsInRoot}",
      folder.boundsInRoot.left < file.boundsInRoot.left || folder.boundsInRoot.top < file.boundsInRoot.top)
    assertTrue(folder.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString()
      .startsWith("Bozze, Cartella"))
    assertTrue(folder.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString().contains("2 elementi"))
    assertTrue(file.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().joinToString().startsWith("alfa.txt, Testo"))
    assertEquals(FluidFileLayout.Grid, state.layout)

    // «•••» › Elenco: lo stesso contenuto in righe raggruppate.
    clickDescription("Altro")
    clickText("Elenco")
    assertEquals(FluidFileLayout.List, state.layout)
    waitFor("le righe dell'elenco") { has(FluidFileBrowserTags.item("zeta.txt")) }

    // «•••» › Data: dal più recente; un secondo tocco inverte.
    clickDescription("Altro")
    clickText("Data")
    assertEquals(FluidFileSortKey.Date, state.sort.key)
    assertTrue(state.sort.descending)
    waitFor("l'ordine per data") {
      has(FluidFileBrowserTags.item("Éclair.md")) && has(FluidFileBrowserTags.item("alfa.txt")) &&
        center(FluidFileBrowserTags.item("Éclair.md")).y < center(FluidFileBrowserTags.item("alfa.txt")).y
    }
    clickDescription("Altro")
    clickText("Data")
    assertFalse(state.sort.descending)

    // La ricerca non bada a maiuscole e accenti. La pillola si apre col dito.
    val pill = compose.onNode(hasText("Cerca in In Fluid Glass"), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center
    finger.down(pill)
    advance(48)
    finger.up(pill)
    waitFor("il campo della ricerca") { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty() }
    compose.onNode(hasSetTextAction()).performTextInput("ECLAIR")
    waitFor("solo Éclair") { has(FluidFileBrowserTags.item("Éclair.md")) && !has(FluidFileBrowserTags.item("alfa.txt")) }
  }

  @Test fun `sfoglia - un file si apre nel visore e finisce nei recenti`() {
    show()
    openAppFolder()
    tap(FluidFileBrowserTags.item("alfa.txt"))
    waitFor("il testo del file nel visore") {
      compose.onAllNodes(hasText("Prima riga di alfa", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    val recents = FluidFileRecents.get(compose.activity)
    assertEquals(listOf("alfa.txt"), recents.entries.map { it.item.name })
    assertEquals("prova", recents.entries.first().sourceId)
    compose.activity.onBackPressedDispatcher.onBackPressed()
    waitFor("di nuovo la cartella") { has(FluidFileBrowserTags.item("alfa.txt")) && has(FluidFileBrowserTags.Folder) }
  }

  @Test fun `sfoglia - una cartella lunga arriva a pagine e lo scorrimento rapido la percorre tutta`() {
    show()
    openAppFolder()
    tap(FluidFileBrowserTags.item("Note del corso"))
    waitFor("la prima pagina di note") { has(FluidFileBrowserTags.item("Note del corso/Nota 0001.txt")) }
    settle()
    assertFalse(has(FluidFileBrowserTags.item("Note del corso/Nota 0450.txt")))
    // Lo scorrimento rapido c'è anche per TalkBack: un cursore che salta.
    waitFor("lo scorrimento rapido") { hasDescription("Scorri la cartella") }
    val scrubber = compose.onNode(hasContentDescription("Scorri la cartella"), useUnmergedTree = true)
    // In fondo alla prima pagina: la riga «Caricamento» entra in composizione e chiede la pagina seguente.
    scrubber.performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
    waitFor("la seconda pagina") { has(FluidFileBrowserTags.item("Note del corso/Nota 0201.txt")) ||
      compose.onAllNodes(hasText("Caricamento"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    // E così fino all'ultima: la lista va in fondo finché c'è la riga delle pagine dopo.
    val list = compose.onNode(hasScrollToKeyAction() and hasAnyAncestor(hasTestTag(FluidFileBrowserTags.Folder)))
    // (Arrivata una pagina, la riga esce dallo schermo: la lista ci va di nuovo, come il dito che scende.)
    repeat(20) {
      runCatching { list.performScrollToKey("altre") }
      advance(200)
      Thread.sleep(20)
    }
    list.performScrollToKey("r:149:3")
    waitFor("l'ultima nota, dalla terza pagina") { has(FluidFileBrowserTags.item("Note del corso/Nota 0450.txt")) }
  }

  @Test fun `posizioni - una cartella concessa col selettore di sistema entra fra le posizioni e si sfoglia`() {
    Robolectric.setupContentProvider(F8p18TreeProvider::class.java, F8p18TreeProvider.Authority)
    show(saf = true)
    waitFor("Aggiungi una posizione") { has(FluidFileBrowserTags.AddLocation) }
    tap(FluidFileBrowserTags.AddLocation)
    val asked = shadowOf(compose.activity).nextStartedActivityForResult
    assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, asked.intent.action)
    // Il selettore risponde con la cartella scelta; l'app prende il permesso persistente.
    val tree = F8p18TreeProvider.tree()
    shadowOf(compose.activity).receiveResult(asked.intent, Activity.RESULT_OK,
      Intent().setData(tree).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION))
    waitFor("la cartella concessa, aperta") { has(FluidFileBrowserTags.item("drive-1")) }
    assertTrue(compose.activity.contentResolver.persistedUriPermissions.any { it.uri == tree })
    assertTrue(hasDescription("Preventivo.pdf, PDF"))
    compose.activity.onBackPressedDispatcher.onBackPressed()
    waitFor("la posizione in Sfoglia") { hasDescription("Drive di prova") || compose.onAllNodes(hasText("Drive di prova"),
      useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
  }

  @Test fun `scegliere - un tocco su un file lo consegna all'app, come il selettore di documenti`() {
    show(pick = true)
    openAppFolder()
    tap(FluidFileBrowserTags.item("Bozze"))
    waitFor("dentro Bozze") { has(FluidFileBrowserTags.item("Bozze/una.txt")) }
    settle()
    assertTrue(picked.isEmpty())
    tap(FluidFileBrowserTags.item("Bozze/una.txt"))
    assertEquals(listOf("una.txt"), picked)
  }

  @Test fun `riduci movimento - la cartella e il file si aprono fermi e leggibili`() {
    show(reduced = true)
    openAppFolder()
    tap(FluidFileBrowserTags.item("zeta.txt"))
    waitFor("il testo, senza animazioni") {
      compose.onAllNodes(hasText("ultimo", substring = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }
    val matcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
    assertTrue(compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
  }
}
