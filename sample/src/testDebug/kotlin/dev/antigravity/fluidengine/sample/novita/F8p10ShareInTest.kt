package dev.antigravity.fluidengine.sample.novita

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import android.content.pm.ProviderInfo
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sharein.FluidCopiedFile
import dev.antigravity.fluidengine.sharein.FluidIncoming
import dev.antigravity.fluidengine.sharein.FluidShareIn
import dev.antigravity.fluidengine.sharein.FluidShareInContent
import dev.antigravity.fluidengine.sharein.FluidShareInDestination
import dev.antigravity.fluidengine.sharein.FluidShareInFiles
import dev.antigravity.fluidengine.sharein.FluidShareInPolicy
import dev.antigravity.fluidengine.sharein.FluidShareInProblem
import dev.antigravity.fluidengine.sharein.FluidShareInSheet
import dev.antigravity.fluidengine.sharein.FluidShareInTags
import dev.antigravity.fluidengine.sharein.copy
import dev.antigravity.fluidengine.sharein.rememberFluidShareInState
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareFiles
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import org.robolectric.Shadows.shadowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileNotFoundException

/**
 * Un'altra app che condivide: serve file veri con il loro nome e il loro peso (anche bugiardo), rifiuta un file
 * (`SecurityException`, il permesso scaduto) e ricorda su quale thread e' stata letta.
 */
class F8p10OtherApp : ContentProvider() {
  companion object {
    const val Authority = "dev.antigravity.altraapp.condivisione"
    @Volatile var reads = 0
    @Volatile var mainThreadReads = 0
    val opened = java.util.Collections.synchronizedList(ArrayList<String>())

    fun uri(name: String): Uri = Uri.parse("content://$Authority/$name")

    /** nome → (byte veri, peso dichiarato, tipo). */
    val files: Map<String, Triple<Int, Long?, String>> = mapOf(
      "foto.jpg" to Triple(300 * 1024, 300L * 1024, "image/jpeg"),
      "nota.txt" to Triple(2 * 1024, null, "text/plain"),
      "enorme.mp4" to Triple(1024, 200L * 1024 * 1024, "video/mp4"),
      "bugiardo.bin" to Triple(7 * 1024 * 1024, 1024L, "application/octet-stream"),
      "negato.jpg" to Triple(1024, 1024L, "image/jpeg"),
      "strano.jpg" to Triple(1024, 1024L, "image/jpeg"),
    )
  }

  override fun onCreate() = true
  override fun getType(uri: Uri): String? = files[uri.lastPathSegment]?.third

  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? {
    val (_, size, _) = files[uri.lastPathSegment] ?: return null
    return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any?>(uri.lastPathSegment, size)) }
  }

  override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
    val name = uri.lastPathSegment ?: throw FileNotFoundException()
    if (name == "negato.jpg") throw SecurityException("Permesso scaduto")
    // R2: un provider guasto lancia un'eccezione che non e' ne' di permesso ne' di lettura.
    if (name == "strano.jpg") throw IllegalStateException("Il provider dell'altra app e' guasto")
    val (bytes, _, _) = files[name] ?: throw FileNotFoundException(name)
    reads++
    opened += name
    if (Looper.myLooper() == Looper.getMainLooper()) mainThreadReads++
    val file = File(context!!.cacheDir, "altra-app-$name")
    if (!file.exists() || file.length() != bytes.toLong()) file.outputStream().use { out -> out.write(ByteArray(bytes) { (it % 251).toByte() }) }
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
  }

  override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
  override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}

/**
 * F8.10 · Gli arrivi da altre app: le quattro forme di Android lette in un modo solo (uno, tanti, testo selezionato, «apri
 * con»), un tipo sconosciuto, un file enorme oltre il tetto (dichiarato e no), il permesso negato, gli Uri che non si
 * leggono mai; la copia fuori dal thread principale; il foglio col dito vero, TalkBack e «Riduci movimento».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p10ShareInTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val context: Context get() = compose.activity
  private val policy = FluidShareInPolicy(maxFileBytes = 5L * 1024 * 1024, maxTotalBytes = 20L * 1024 * 1024, maxFiles = 3)

  @Before fun start() {
    Robolectric.setupContentProvider(F8p10OtherApp::class.java, F8p10OtherApp.Authority)
    F8p10OtherApp.reads = 0
    F8p10OtherApp.mainThreadReads = 0
    F8p10OtherApp.opened.clear()
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After fun stop() = FluidGlassDiagnostics.resetForTesting()

  private fun send(name: String) = Intent(Intent.ACTION_SEND).setType(F8p10OtherApp.files[name]!!.third)
    .putExtra(Intent.EXTRA_STREAM, F8p10OtherApp.uri(name))

  private fun read(intent: Intent?, with: FluidShareInPolicy = policy): FluidIncoming = runBlocking { FluidShareIn.read(context, intent, with) }

  private fun copy(incoming: FluidIncoming, with: FluidShareInPolicy = policy): List<FluidCopiedFile> =
    runBlocking { FluidShareIn.copy(context, (incoming as FluidIncoming.Files).files, with) }

  @Test fun oneFileIsReadWithItsNameAndCopiedOffTheMainThread() {
    val incoming = read(send("foto.jpg")) as FluidIncoming.Files
    val file = incoming.files.single()
    assertEquals("foto.jpg", file.name)
    assertEquals("image/jpeg", file.mimeType)
    assertEquals(300L * 1024, file.sizeBytes)
    val progress = ArrayList<Float?>()
    val copies = runBlocking { FluidShareIn.copy(context, incoming.files, policy) { progress += it.fraction } }
    val copied = copies.single()
    assertTrue(copied.ok)
    assertEquals(300L * 1024, copied.file!!.length())
    assertEquals("foto.jpg", copied.file!!.name)
    assertTrue("l'avanzamento arriva: $progress", progress.isNotEmpty() && progress.last() == 1f)
    assertEquals("mai letto sul thread principale", 0, F8p10OtherApp.mainThreadReads)
  }

  @Test fun manyFilesGoOnEvenIfOneIsDenied() {
    val uris = arrayListOf(F8p10OtherApp.uri("foto.jpg"), F8p10OtherApp.uri("negato.jpg"), F8p10OtherApp.uri("nota.txt"))
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    val copies = copy(read(intent))
    assertEquals(listOf(true, false, true), copies.map { it.ok })
    assertEquals(FluidShareInProblem.Unreadable, copies[1].problem)
    assertEquals("un peso non detto si conta copiando", 2L * 1024, copies[2].file!!.length())
  }

  @Test fun theFormsOfTextSelectionLinkAndOpenWith() {
    val selection = read(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain")
      .putExtra(Intent.EXTRA_PROCESS_TEXT, "la Rivoluzione francese").putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)) as FluidIncoming.Text
    assertTrue(selection.selection)
    assertTrue(selection.readOnly)
    assertEquals("la Rivoluzione francese", selection.text)

    val link = read(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Mappa https://example.org/m")
      .putExtra(Intent.EXTRA_SUBJECT, "Mappa del museo")) as FluidIncoming.Link
    assertEquals("https://example.org/m", link.url)
    assertEquals("Mappa del museo", link.title)

    val text = read(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Portare il libro")) as FluidIncoming.Text
    assertFalse(text.selection)

    val viewed = read(Intent(Intent.ACTION_VIEW).setDataAndType(F8p10OtherApp.uri("nota.txt"), "text/plain")) as FluidIncoming.Files
    assertTrue(viewed.viewed)
    assertEquals("nota.txt", viewed.files.single().name)
    assertTrue(read(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.org"))) is FluidIncoming.Link)
  }

  @Test fun whatCannotBeKeptSaysWhy() {
    assertEquals(FluidShareInProblem.NotAShare, (read(Intent(Intent.ACTION_DIAL)) as FluidIncoming.Unsupported).problem)
    assertEquals(FluidShareInProblem.NotAShare, (read(null) as FluidIncoming.Unsupported).problem)
    assertEquals(FluidShareInProblem.Empty, (read(Intent(Intent.ACTION_SEND).setType("text/plain")) as FluidIncoming.Unsupported).problem)
    // Un file:// e un Uri della galleria stessa non si leggono mai.
    val file = read(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_STREAM, Uri.parse("file:///sdcard/x.jpg")))
    assertEquals(FluidShareInProblem.UnsafeUri, (file as FluidIncoming.Unsupported).problem)
    val own = Uri.parse("content://${FluidShareFiles.authority(context)}/fluid_share/x/y.png")
    val ownIntent = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, own)
    assertEquals(FluidShareInProblem.UnsafeUri, (read(ownIntent) as FluidIncoming.Unsupported).problem)
    assertTrue("se l'app la dichiara fidata, si'", read(ownIntent, policy.copy(trustedAuthorities = setOf(FluidShareFiles.authority(context)))) is FluidIncoming.Files)
  }

  @Test fun theCapStopsHugeFilesDeclaredOrNot() {
    val declared = copy(read(send("enorme.mp4"))).single()
    assertEquals(FluidShareInProblem.TooLarge, declared.problem)
    assertFalse("un file dichiarato enorme non si apre nemmeno", "enorme.mp4" in F8p10OtherApp.opened)
    val lying = copy(read(send("bugiardo.bin"))).single()
    assertEquals(FluidShareInProblem.TooLarge, lying.problem)
    assertNull(lying.file)
    val leftovers = File(context.cacheDir, "fluid-share-in").walk().filter { it.isFile && it.name.startsWith("bugiardo") }.toList()
    assertTrue("la copia a meta' si cancella: $leftovers", leftovers.isEmpty())
  }

  @Test fun typesAndCountFollowThePolicy() {
    val photos = policy.copy(acceptedTypes = listOf("image/*"))
    val uris = arrayListOf(F8p10OtherApp.uri("foto.jpg"), F8p10OtherApp.uri("nota.txt"), F8p10OtherApp.uri("foto.jpg"), F8p10OtherApp.uri("nota.txt"))
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    val incoming = read(intent) as FluidIncoming.Files
    // Lo stesso Uri due volte e' un file solo.
    assertEquals(2, incoming.files.size)
    val copies = copy(incoming, photos)
    assertEquals(listOf(null, FluidShareInProblem.TypeNotAccepted), copies.map { it.problem })
    val many = arrayListOf(F8p10OtherApp.uri("foto.jpg"), F8p10OtherApp.uri("nota.txt"), F8p10OtherApp.uri("enorme.mp4"), F8p10OtherApp.uri("bugiardo.bin"))
    val four = copy(read(Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, many)))
    assertEquals(FluidShareInProblem.TooMany, four.last().problem)
  }

  // --- Il foglio ----------------------------------------------------------------------------------------------------

  private val destinations = listOf(FluidShareInDestination("appunti", "Appunti"), FluidShareInDestination("scuola", "Scuola"))

  private fun sheet(intent: Intent, reduced: Boolean = false, onSave: (FluidShareInDestination, FluidShareInContent) -> Unit, onClose: () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides if (reduced) FluidMotionPolicy(0f) else FluidMotionPolicy.Default) {
          val state = rememberFluidShareInState(intent, policy)
          FluidShareInSheet(state, destinations, onSave = { d, c -> onSave(d, c) }, onClose = onClose, title = "Fluid Glass")
        }
      }
    }
  }

  @Test fun theSheetSavesWhereTheFingerChoseAndCloses() {
    var saved: Pair<FluidShareInDestination, FluidShareInContent>? = null
    var closed = false
    sheet(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Portare il libro di storia"),
      onSave = { d, c -> saved = d to c }, onClose = { closed = true })
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithText("Portare il libro di storia").assertExists() }.isSuccess }
    // TalkBack: il nome del foglio.
    val pane = compose.onNodeWithTag(FluidShareInTags.Sheet).fetchSemanticsNode()
    assertEquals("Fluid Glass", pane.config.getOrNull(SemanticsProperties.PaneTitle))
    compose.onNodeWithTag(FluidShareInTags.destination("scuola")).performTouchInput { click() }
    compose.onNodeWithTag(FluidShareInTags.Save).performTouchInput { click() }
    compose.waitUntil(5_000) { closed }
    assertEquals("scuola", saved!!.first.id)
    assertEquals("Portare il libro di storia", saved!!.second.text)
  }

  @Test fun copiedFilesReachTheSaveAndTheStatusSpeaksUp() {
    var saved: FluidShareInContent? = null
    val uris = arrayListOf(F8p10OtherApp.uri("foto.jpg"), F8p10OtherApp.uri("bugiardo.bin"))
    sheet(Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris),
      onSave = { _, c -> saved = c }, onClose = {})
    compose.waitUntil(8_000) { runCatching { compose.onNodeWithText("Non arrivati", substring = true).assertExists() }.isSuccess }
    val status = compose.onNodeWithTag(FluidShareInTags.Status).fetchSemanticsNode()
    assertNotNull("lo stato lo annuncia TalkBack", status.config.getOrNull(SemanticsProperties.LiveRegion))
    compose.onNodeWithTag(FluidShareInTags.Save).performTouchInput { click() }
    compose.waitUntil(5_000) { saved != null }
    assertEquals(listOf("foto.jpg"), saved!!.files.map { it.name })
  }

  private fun sheetBounds(): Rect = compose.onNodeWithTag(FluidShareInTags.Sheet).getUnclippedBoundsInRoot().let {
    Rect(it.left.value, it.top.value, it.right.value, it.bottom.value)
  }

  @Test fun theSheetRunsUnderTheBottomEdgeAndKeepsItsButtonsAbove() {
    // Il tablet il 25 novembre: sotto il foglio una fascia bianca. Il vetro continua oltre il bordo dello schermo, il
    // contenuto resta sopra.
    sheet(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "x"), reduced = true, onSave = { _, _ -> }, onClose = {})
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag(FluidShareInTags.Save).assertExists() }.isSuccess }
    val root = compose.onRoot().getUnclippedBoundsInRoot()
    val sheet = sheetBounds()
    assertTrue("il foglio continua sotto il bordo: ${sheet.bottom} contro ${root.bottom.value}", sheet.bottom >= root.bottom.value + 40f)
    val save = compose.onNodeWithTag(FluidShareInTags.Save).getUnclippedBoundsInRoot()
    assertTrue("«Salva» sopra il bordo", save.bottom <= root.bottom)
    val last = compose.onNodeWithTag(FluidShareInTags.destination("scuola")).getUnclippedBoundsInRoot()
    assertTrue("l'ultima destinazione sopra il bordo: ${last.bottom} contro ${root.bottom}", last.bottom <= root.bottom)
  }

  @Test fun reducedMotionShowsTheSheetInPlaceAtOnce() {
    compose.mainClock.autoAdvance = false
    sheet(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "x"), reduced = true, onSave = { _, _ -> }, onClose = {})
    compose.mainClock.advanceTimeByFrame()
    val first = compose.onNodeWithTag(FluidShareInTags.Sheet).fetchSemanticsNode().positionInRoot.y
    compose.mainClock.advanceTimeBy(600)
    val settled = compose.onNodeWithTag(FluidShareInTags.Sheet).fetchSemanticsNode().positionInRoot.y
    assertEquals("fermo dal primo fotogramma", settled, first, 0.5f)
    compose.mainClock.autoAdvance = true
  }

  @Test fun theSheetRisesFromBelowAndAFingerPullsItAway() {
    var closed = false
    compose.mainClock.autoAdvance = false
    sheet(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "x"), onSave = { _, _ -> }, onClose = { closed = true })
    compose.mainClock.advanceTimeByFrame()
    val first = compose.onNodeWithTag(FluidShareInTags.Sheet).fetchSemanticsNode().positionInRoot.y
    compose.mainClock.advanceTimeBy(800)
    val settled = compose.onNodeWithTag(FluidShareInTags.Sheet).fetchSemanticsNode().positionInRoot.y
    assertTrue("sale dal basso: $first → $settled", first > settled + 100)
    compose.mainClock.autoAdvance = true
    val root = compose.onRoot().getUnclippedBoundsInRoot()
    assertTrue(root.bottom > 0.dp)
    compose.onNodeWithTag(FluidShareInTags.Sheet).performTouchInput { swipeDown(startY = top + 20f, endY = top + 600f, durationMillis = 150) }
    compose.waitUntil(5_000) { closed }
  }

  // --- R2 · Provider guasti, Uri insicuri fra tanti, tipi sbagliati, salvataggio e rotazione ---------------------

  private fun many(vararg uris: Uri) = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(*uris))

  @Test fun aStrangeErrorOfTheOtherAppLosesOnlyThatFile() {
    val copies = copy(read(many(F8p10OtherApp.uri("foto.jpg"), F8p10OtherApp.uri("strano.jpg"), F8p10OtherApp.uri("nota.txt"))))
    assertEquals("un arrivo rotto non butta gli altri", listOf(true, false, true), copies.map { it.ok })
    assertEquals(FluidShareInProblem.Unreadable, copies[1].problem)
  }

  @Test fun aFileUriAmongManyIsRejectedOutLoud() {
    val incoming = read(many(F8p10OtherApp.uri("foto.jpg"), Uri.parse("file:///sdcard/segreto.jpg"), F8p10OtherApp.uri("nota.txt"))) as FluidIncoming.Files
    assertEquals("il file:// c'e', per dire che non e' arrivato: ${incoming.files}", 3, incoming.files.size)
    val copies = copy(incoming)
    assertEquals(listOf(null, FluidShareInProblem.UnsafeUri, null), copies.map { it.problem })
    assertFalse("mai letto", F8p10OtherApp.opened.any { "segreto" in it })
  }

  @Test fun aWrongTypeInTheStreamIsNotACrash() {
    // Un intent malevolo: nella lista di EXTRA_STREAM un oggetto che non e' un Uri.
    val bad = Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
      .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf<Parcelable>(Intent("non.un.uri"), F8p10OtherApp.uri("foto.jpg")))
    val incoming = runCatching { read(bad) }
    assertTrue("niente eccezione: ${incoming.exceptionOrNull()}", incoming.isSuccess)
    assertEquals(listOf("foto.jpg"), (incoming.getOrThrow() as FluidIncoming.Files).files.map { it.name })
  }

  @Test fun anOwnProviderWithAnotherAuthorityIsStillOwn() {
    // Un provider dell'app con un'autorita' che non comincia col nome del pacchetto.
    shadowOf(context.packageManager).addOrUpdateProvider(ProviderInfo().apply {
      authority = "dev.antigravity.note.archivio"
      name = "dev.antigravity.note.ArchivioProvider"
      packageName = context.packageName
      applicationInfo = context.applicationInfo
    })
    val own = Intent(Intent.ACTION_SEND).setType("application/octet-stream")
      .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://dev.antigravity.note.archivio/privato.db"))
    assertEquals(FluidShareInProblem.UnsafeUri, (read(own) as? FluidIncoming.Unsupported)?.problem)
  }

  @Test fun nothingClosesTheSheetWhileItSaves() {
    val gate = CompletableDeferred<Unit>()
    var closed = 0
    val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Portare il libro")
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val state = rememberFluidShareInState(intent, policy)
        FluidShareInSheet(state, destinations, onSave = { _, _ -> gate.await() }, onClose = { closed++ }, title = "Fluid Glass")
      }
    }
    compose.waitUntil(5_000) { runCatching { compose.onNodeWithTag(FluidShareInTags.Save).assertIsEnabled() }.isSuccess }
    compose.onNodeWithTag(FluidShareInTags.Save).performTouchInput { click() }
    compose.waitForIdle()
    // Mentre l'app salva, ne' «Annulla» ne' il dito chiudono il foglio: la coroutine dell'app non si cancella.
    compose.onNodeWithTag(FluidShareInTags.Cancel).performTouchInput { click() }
    compose.onNodeWithTag(FluidShareInTags.Sheet).performTouchInput { swipeDown(startY = top + 20f, endY = top + 600f, durationMillis = 150) }
    compose.waitForIdle()
    assertEquals("chiuso a meta' salvataggio", 0, closed)
    gate.complete(Unit)
    compose.waitUntil(5_000) { closed == 1 }
  }

  @Test fun aRotationKeepsTheDestinationAndDoesNotCopyTwice() {
    val restoration = StateRestorationTester(compose)
    var saved: String? = null
    val intent = send("foto.jpg")
    restoration.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val state = rememberFluidShareInState(intent, policy)
        FluidShareInSheet(state, destinations, onSave = { d, _ -> saved = d.id }, onClose = {}, title = "Fluid Glass")
      }
    }
    fun ready() = runCatching { compose.onNodeWithTag(FluidShareInTags.Save).assertIsEnabled() }.isSuccess
    fun sessions() = FluidShareInFiles.root(context).listFiles().orEmpty().count { it.isDirectory }
    compose.waitUntil(5_000) { ready() }
    compose.onNodeWithTag(FluidShareInTags.destination("scuola")).performTouchInput { click() }
    compose.waitForIdle()
    val before = sessions()
    restoration.emulateSavedInstanceStateRestore()
    compose.waitForIdle()
    compose.waitUntil(5_000) { ready() }
    assertEquals("nessuna copia orfana in una cartella nuova", before, sessions())
    compose.onNodeWithTag(FluidShareInTags.Save).performTouchInput { click() }
    compose.waitUntil(5_000) { saved != null }
    assertEquals("la destinazione scelta resta dopo la rotazione", "scuola", saved)
  }
}
