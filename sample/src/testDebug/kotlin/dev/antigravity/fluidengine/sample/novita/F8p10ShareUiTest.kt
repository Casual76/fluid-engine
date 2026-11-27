package dev.antigravity.fluidengine.sample.novita

import android.content.ClipboardManager
import android.content.ClipDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.Signature
import android.net.Uri
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.swipeRight
import androidx.compose.runtime.snapshots.Snapshot
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.qr.FluidZxingQr
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidSheetPagesTags
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.share.FluidCopyCheck
import dev.antigravity.fluidengine.ui.fluid.share.FluidCopyChoice
import dev.antigravity.fluidengine.ui.fluid.share.FluidCopyOption
import dev.antigravity.fluidengine.ui.fluid.share.FluidPrepareCopySheet
import dev.antigravity.fluidengine.ui.fluid.share.FluidPrepareCopyTags
import dev.antigravity.fluidengine.ui.fluid.share.FluidPreparedCopy
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareCopyOptions
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareFiles
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareIntents
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareItem
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareOptions
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareOutcome
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareSheet
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareSheetTags
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareTargets
import dev.antigravity.fluidengine.ui.fluid.share.FluidShareTrust
import dev.antigravity.fluidengine.ui.fluid.share.LocalFluidQrEncoder
import dev.antigravity.fluidengine.ui.fluid.share.FluidQrEncoder
import dev.antigravity.fluidengine.ui.fluid.share.fluidCopyProblems
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Robolectric

/**
 * F8.10 · Il foglio di condivisione e «Prepara la copia» in un albero Compose vero, col dito vero (eventi di tocco sul
 * nodo): ogni azione manda l'intent giusto, i file partono col permesso di lettura, la propria app non compare mai fra le
 * destinazioni, la fila delle app tiene solo le app fidate, e l'invio di una copia che non corrisponde alle opzioni e'
 * bloccato.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p10ShareUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val context: Context get() = compose.activity

  @Before fun start() {
    Robolectric.setupContentProvider(F8p10OtherApp::class.java, F8p10OtherApp.Authority)
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
    FluidShareFiles.overrideUrisForTesting(null)
    File(compose.activity.filesDir, "f8-10-prove").deleteRecursively()
  }

  @Composable
  private fun Hosted(reduced: Boolean = false, content: @Composable () -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val host = rememberFluidGlassModalHostState()
      CompositionLocalProvider(
        LocalFluidGlassModalHostState provides host,
        LocalFluidMotionPolicy provides if (reduced) FluidMotionPolicy(durationScale = 0f) else FluidMotionPolicy.Default,
      ) {
        Box(Modifier.fillMaxSize()) {
          content()
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
  }

  /**
   * Un file da condividere, servito da un'«altra» app di prova. Non dal FileProvider dell'engine: `FileProvider` cerca la
   * radice col separatore «/», e sul JVM di Windows i percorsi hanno «\». Sul dispositivo il FileProvider si prova nelle
   * scene (il voto, la pagella).
   */
  private fun pngItem(): FluidShareItem =
    FluidShareItem.file(F8p10OtherApp.uri("foto.jpg"), "Voto di matematica.png", "image/png", 245L * 1024, title = "Voto di matematica")

  private fun tap(tag: String) {
    compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithTag(tag).performTouchInput { click() }
    compose.waitForIdle()
  }

  private fun nextIntent(): Intent? = shadowOf(compose.activity).nextStartedActivity

  private val PampaKey = "3082010a0282010100c0ffee"
  private val OtherKey = "3082010a0282010100badf00d0"

  /**
   * Un'app finta che riceve `SEND` di ogni tipo, firmata con la stessa firma della galleria ([sameKey]) o con un'altra:
   * `checkSignatures` di Robolectric confronta le firme dei due `PackageInfo`.
   */
  private fun installReceiver(pkg: String, label: String, sameKey: Boolean = true): ComponentName {
    val component = ComponentName(pkg, "$pkg.ShareActivity")
    val pm = shadowOf(context.packageManager)
    val activity = pm.addActivityIfNotPresent(component)
    activity.nonLocalizedLabel = label
    activity.applicationInfo?.nonLocalizedLabel = label
    pm.getInternalMutablePackageInfo(pkg)?.applicationInfo?.nonLocalizedLabel = label
    @Suppress("DEPRECATION")
    pm.getInternalMutablePackageInfo(context.packageName)?.signatures = arrayOf(Signature(PampaKey))
    @Suppress("DEPRECATION")
    pm.getInternalMutablePackageInfo(pkg)?.signatures = arrayOf(Signature(if (sameKey) PampaKey else OtherKey))
    pm.addIntentFilterForActivity(component, IntentFilter(Intent.ACTION_SEND).apply {
      addCategory(Intent.CATEGORY_DEFAULT)
      addDataType("*/*")
    })
    return component
  }

  @Test fun theIntentsCarryTheReadPermissionAndAllTheUris() {
    val one = pngItem()
    val send = FluidShareIntents.send(one)
    assertEquals(Intent.ACTION_SEND, send.action)
    assertEquals("image/png", send.type)
    assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    assertEquals(one.uris.first(), send.clipData!!.getItemAt(0).uri)
    assertEquals("content", one.uris.first().scheme)
    assertEquals("${context.packageName}.fluid.share", FluidShareFiles.authority(context))

    val many = FluidShareItem.files(listOf(pngItem().uris.first(), pngItem().uris.first(), pngItem().uris.first()), "3 foto", "image/*")
    val multiple = FluidShareIntents.send(many)
    assertEquals(Intent.ACTION_SEND_MULTIPLE, multiple.action)
    assertEquals("ogni Uri nel ClipData, non solo il primo", 3, multiple.clipData!!.itemCount)
    assertTrue(multiple.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)

    val text = FluidShareIntents.send(FluidShareItem.link("https://example.org/a", "A"))
    assertEquals("text/plain", text.type)
    assertEquals("https://example.org/a", text.getStringExtra(Intent.EXTRA_TEXT))
    assertEquals("un testo non concede niente", 0, text.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)

    // Il foglio di Android non propone mai questa app (la galleria riceve immagini: ShareInDemoActivity).
    val chooser = FluidShareIntents.chooser(context, one, "Condividi")
    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    @Suppress("DEPRECATION")
    val excluded = chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS).orEmpty().map { it as ComponentName }
    assertTrue("escluse: $excluded", excluded.any { it.packageName == context.packageName })
  }

  // --- Un file dell'app fuori dalla cartella dell'engine (il crash del 24 novembre sul tablet) -------------------------

  /** Un file dell'app in `filesDir`, fuori dalla cartella dell'engine. */
  private fun appFile(name: String, size: Int): File =
    File(context.filesDir, "f8-10-prove/$name").also { file ->
      file.parentFile?.mkdirs()
      file.writeBytes(ByteArray(size) { (it % 251).toByte() })
    }

  /** Il provider finto: sul JVM di Windows il `FileProvider` vero non trova le sue radici. Sul tablet si guarda quello vero. */
  private fun fakeUris() = FluidShareFiles.overrideUrisForTesting { file ->
    Uri.parse("content://${F8p10OtherApp.Authority}/copia/${file.parentFile!!.name}/${Uri.encode(file.name)}")
  }

  private fun waitForTag(tag: String) =
    compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

  @Test fun anAppFileOutsideTheFolderIsCopiedThenShared() {
    fakeUris()
    val photo = appFile("Fiori.jpg", 3_000)
    var item by mutableStateOf<FluidShareItem?>(FluidShareItem.file(context, photo, "image/jpeg", title = "1 foto"))
    compose.setContent { Hosted { FluidShareSheet(item, onDismissRequest = { item = null }) } }
    // La copia lavora fuori dal thread principale; poi arrivano le azioni, e «Altre app…» manda la copia.
    waitForTag(FluidShareSheetTags.MoreApps)
    compose.onNodeWithTag(FluidShareSheetTags.Problem).assertDoesNotExist()
    compose.onNodeWithTag(FluidShareSheetTags.Preparing).assertDoesNotExist()
    tap(FluidShareSheetTags.MoreApps)
    val chooser = nextIntent()!!
    @Suppress("DEPRECATION")
    val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    @Suppress("DEPRECATION")
    val uri = send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
    assertTrue(uri.toString(), uri.toString().contains("/copia/"))
    assertEquals("Fiori.jpg", uri.lastPathSegment)
    assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    val copy = File(File(FluidShareFiles.root(context), uri.pathSegments[1]), "Fiori.jpg")
    assertTrue("la copia sta nella cartella dell'engine", FluidShareFiles.isShareable(context, copy))
    assertTrue(photo.readBytes().contentEquals(copy.readBytes()))
  }

  @Test fun aMissingFileSaysSoInsteadOfCrashing() {
    fakeUris()
    var item by mutableStateOf<FluidShareItem?>(FluidShareItem.file(context, File(context.filesDir, "f8-10-prove/non-c-e.jpg"), "image/jpeg"))
    compose.setContent { Hosted { FluidShareSheet(item, onDismissRequest = { item = null }) } }
    waitForTag(FluidShareSheetTags.Problem)
    compose.onNodeWithText("Il file non c'è più: forse è stato spostato o cancellato.").assertExists()
    // Nessuna azione che possa mandare un file che non c'e'; «Chiudi» chiude il foglio.
    compose.onNodeWithTag(FluidShareSheetTags.Copy).assertDoesNotExist()
    compose.onNodeWithTag(FluidShareSheetTags.MoreApps).assertDoesNotExist()
    compose.onNodeWithText("Chiudi").performTouchInput { click() }
    compose.waitForIdle()
    assertNull(item)
  }

  @Test fun aFolderIsUnreadable() {
    fakeUris()
    val folder = File(context.filesDir, "f8-10-prove/cartella").apply { mkdirs() }
    compose.setContent { Hosted { FluidShareSheet(FluidShareItem.file(context, folder, "image/jpeg"), onDismissRequest = {}) } }
    waitForTag(FluidShareSheetTags.Problem)
    compose.onNodeWithText("Il file non si legge, quindi non si può condividere.").assertExists()
  }

  @Test fun overTheLimitTheSheetSaysHowMuch() {
    fakeUris()
    val video = appFile("Video.mp4", 4_000)
    compose.setContent {
      Hosted {
        FluidShareSheet(FluidShareItem.file(context, video, "video/mp4"), onDismissRequest = {}, options = FluidShareOptions(copyLimitBytes = 1_000))
      }
    }
    waitForTag(FluidShareSheetTags.Problem)
    val text = compose.onAllNodesWithText("troppo grande", substring = true).fetchSemanticsNodes().single()
      .config.getOrNull(SemanticsProperties.Text)!!.joinToString()
    assertTrue(text, text.contains("4 KB") && text.contains("al massimo 1 KB"))
    assertTrue("niente copiato", FluidShareFiles.root(context).walkTopDown().none { it.isFile && it.name == "Video.mp4" })
  }

  @Test fun moreAppsOpensTheAndroidSheetWithTheFileAndCloses() {
    var item by mutableStateOf<FluidShareItem?>(pngItem())
    val outcomes = mutableListOf<FluidShareOutcome>()
    compose.setContent { Hosted { FluidShareSheet(item, onDismissRequest = { item = null }, onShared = { outcomes += it }) } }
    tap(FluidShareSheetTags.MoreApps)
    val chooser = nextIntent()!!
    assertEquals(Intent.ACTION_CHOOSER, chooser.action)
    @Suppress("DEPRECATION")
    val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    assertEquals(Intent.ACTION_SEND, send.action)
    assertTrue(send.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    assertTrue(outcomes.single() is FluidShareOutcome.Chooser)
    assertNull("il foglio si chiude", item)
  }

  @Test fun thePampaRowShowsTrustedAppsOnlyAndNeverThisApp() {
    val notes = installReceiver("dev.antigravity.pampanotes.prova", "Pampa Notes")
    var item by mutableStateOf<FluidShareItem?>(pngItem())
    val outcomes = mutableListOf<FluidShareOutcome>()
    compose.setContent { Hosted { FluidShareSheet(item, onDismissRequest = { item = null }, onShared = { outcomes += it }) } }
    compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag(FluidShareSheetTags.app(notes.packageName))).fetchSemanticsNodes().isNotEmpty() }
    // La galleria riceve immagini (ShareInDemoActivity) ma non e' mai una destinazione del proprio foglio.
    assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasTestTag(FluidShareSheetTags.app(context.packageName))).fetchSemanticsNodes().isEmpty())
    // TalkBack legge il nome dell'app.
    val bubble = compose.onNodeWithTag(FluidShareSheetTags.app(notes.packageName)).fetchSemanticsNode()
    assertEquals("Pampa Notes", bubble.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString())
    tap(FluidShareSheetTags.app(notes.packageName))
    val sent = nextIntent()!!
    assertEquals(notes, sent.component)
    assertEquals(Intent.ACTION_SEND, sent.action)
    assertTrue(sent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    assertTrue((outcomes.single() as FluidShareOutcome.SentTo).target.packageName == notes.packageName)
    assertNull(item)
    // La prossima volta sta in cima: e' fra i recenti.
    val again = FluidShareTargets.find(context, pngItem())
    assertEquals(notes.packageName, again.first().packageName)
  }

  @Test fun aTrustByCertificateIgnoresAppsWithAnotherKey() {
    installReceiver("dev.antigravity.pampa.prova", "Pampa")
    installReceiver("dev.antigravity.altra.prova", "Altra app", sameKey = false)
    val byCertificate = FluidShareTargets.find(context, pngItem(), FluidShareTrust.certificates(setOf("00".repeat(32))))
    assertTrue("nessuna app con quell'impronta: $byCertificate", byCertificate.isEmpty())
    val sameSigner = FluidShareTargets.find(context, pngItem(), FluidShareTrust.SameSigner).map { it.packageName }
    assertTrue("la stessa chiave entra: $sameSigner", "dev.antigravity.pampa.prova" in sameSigner)
    assertFalse("un'altra chiave no: $sameSigner", "dev.antigravity.altra.prova" in sameSigner)
  }

  @Test fun copyPutsTheUriOnTheClipboardThenTheSheetLeaves() {
    var item by mutableStateOf<FluidShareItem?>(pngItem())
    val outcomes = mutableListOf<FluidShareOutcome>()
    compose.setContent { Hosted { FluidShareSheet(item, onDismissRequest = { item = null }, onShared = { outcomes += it }) } }
    compose.waitUntil(5_000) { compose.onAllNodes(androidx.compose.ui.test.hasTestTag(FluidShareSheetTags.Copy)).fetchSemanticsNodes().isNotEmpty() }
    compose.waitForIdle()
    // L'orologio fermo: la spunta si guarda prima che il foglio se ne vada.
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag(FluidShareSheetTags.Copy).performTouchInput { click() }
    compose.mainClock.advanceTimeBy(250)
    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!
    assertEquals(F8p10OtherApp.Authority, clip.getItemAt(0).uri.authority)
    assertTrue(outcomes.single() is FluidShareOutcome.Copied)
    // La spunta «Copiato» resta un attimo, poi il foglio se ne va.
    assertTrue(compose.onAllNodesWithText("Copiato").fetchSemanticsNodes().isNotEmpty())
    assertNotNull(item)
    compose.mainClock.advanceTimeBy(400)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertNull(item)
  }

  @Test fun theActionsFollowWhatTheItemIs() {
    var item by mutableStateOf<FluidShareItem?>(FluidShareItem.link("https://example.org/app", "Fluid Glass"))
    compose.setContent {
      Hosted {
        CompositionLocalProvider(LocalFluidQrEncoder provides FluidZxingQr) {
          FluidShareSheet(item, onDismissRequest = { item = null })
        }
      }
    }
    // Un link: Copia, Salva, QR, Stampa, Altre app; il sottotitolo e' il dominio.
    for (tag in listOf(FluidShareSheetTags.Copy, FluidShareSheetTags.Save, FluidShareSheetTags.Qr, FluidShareSheetTags.Print, FluidShareSheetTags.MoreApps)) {
      compose.onNodeWithTag(tag).assertExists()
    }
    compose.onNodeWithText("example.org").assertExists()
    tap(FluidShareSheetTags.Qr)
    // R2: il codice si calcola alla pressione, fuori dal thread principale.
    waitForTag(FluidShareSheetTags.QrCode)
    val code = compose.onNodeWithTag(FluidShareSheetTags.QrCode).fetchSemanticsNode()
    assertTrue(code.config.getOrNull(SemanticsProperties.ContentDescription)!!.single().contains("example.org/app"))

    // Tre foto: niente QR e niente stampa (che stampa un'immagine sola).
    compose.runOnIdle { item = null }
    compose.waitForIdle()
    compose.runOnIdle { item = FluidShareItem.files(List(3) { pngItem().uris.first() }, "3 foto", "image/*") }
    compose.waitForIdle()
    assertTrue(compose.onAllNodesWithText("3 foto").fetchSemanticsNodes().isNotEmpty())
    compose.onNodeWithTag(FluidShareSheetTags.Qr).assertDoesNotExist()
    compose.onNodeWithTag(FluidShareSheetTags.Print).assertDoesNotExist()
    compose.onNodeWithTag(FluidShareSheetTags.Copy).assertExists()
  }

  private fun displayed(tag: String): Boolean = runCatching { compose.onNodeWithTag(tag).assertIsDisplayed() }.isSuccess

  /** Le opzioni di una foto di prova: la posizione inclusa di serie, e la copia lo dice nella riga sotto il nome. */
  private fun photoOptions(prepared: MutableList<FluidCopyChoice>): FluidShareCopyOptions = FluidShareCopyOptions(
    prepare = { choice ->
      prepared += choice
      val kept = choice.includes("posizione")
      FluidPreparedCopy(
        item = pngItem().copy(detail = if (kept) "Posizione inclusa" else "Senza posizione"),
        checks = listOf(FluidCopyCheck("Posizione", if (kept) "presente" else "tolta", option = "posizione")),
      )
    },
    options = listOf(location),
  )

  @Test fun optionsArePushedInsideTheSheetAndTheNewCopyComesBack() {
    var dismissed = 0
    val prepared = mutableListOf<FluidCopyChoice>()
    val options = photoOptions(prepared)
    compose.setContent {
      Hosted { FluidShareSheet(pngItem().copy(detail = "Posizione inclusa"), onDismissRequest = { dismissed++ }, copyOptions = options) }
    }
    compose.onNodeWithText("Immagine PNG", substring = true).assertExists()
    compose.onNodeWithText("Posizione inclusa", substring = true).assertExists()
    tap(FluidShareSheetTags.Options)
    // La pagina arriva dentro lo stesso foglio; quella della condivisione resta sotto, ferma e nascosta.
    compose.onNodeWithTag(FluidPrepareCopyTags.Page).assertIsDisplayed()
    assertFalse("la pagina sotto non si vede", displayed(FluidShareSheetTags.Copy))
    // Il tasto unisce l'icona: il nome per TalkBack e' quello del chevron.
    val back = compose.onNodeWithTag(FluidSheetPagesTags.Back).fetchSemanticsNode()
    assertEquals("Indietro", back.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString())
    assertEquals(Role.Button, back.config.getOrNull(SemanticsProperties.Role))
    compose.waitUntil(3_000) { runCatching { compose.onNodeWithTag(FluidPrepareCopyTags.Done).assertIsEnabled() }.isSuccess }
    // Il dito spegne «Posizione»: la copia si rifa' e la prova dice «tolta».
    tap(FluidPrepareCopyTags.option("posizione"))
    compose.waitUntil(3_000) { runCatching { compose.onNodeWithText("tolta").assertExists() }.isSuccess }
    compose.waitUntil(3_000) { runCatching { compose.onNodeWithTag(FluidPrepareCopyTags.Done).assertIsEnabled() }.isSuccess }
    tap(FluidPrepareCopyTags.Done)
    // Si torna alla condivisione con la copia nuova, e il foglio non si e' mai chiuso.
    compose.onNodeWithTag(FluidPrepareCopyTags.Page).assertDoesNotExist()
    assertTrue(displayed(FluidShareSheetTags.Copy))
    compose.waitUntil(3_000) { compose.onAllNodesWithText("Senza posizione", substring = true).fetchSemanticsNodes().isNotEmpty() }
    assertEquals(0, dismissed)

    // Riaperte, le opzioni ripartono dalla scelta confermata e non rifanno la copia.
    val before = prepared.size
    tap(FluidShareSheetTags.Options)
    compose.waitUntil(3_000) { runCatching { compose.onNodeWithTag(FluidPrepareCopyTags.Done).assertIsEnabled() }.isSuccess }
    compose.onNodeWithText("tolta").assertExists()
    assertEquals(before, prepared.size)
    // Indietro in alto: si torna senza cambiare niente.
    tap(FluidSheetPagesTags.Back)
    compose.onNodeWithTag(FluidPrepareCopyTags.Page).assertDoesNotExist()
    assertTrue(displayed(FluidShareSheetTags.Copy))
    assertEquals(0, dismissed)
  }

  @Test fun backInsideTheOptionsGoesBackToTheSheetThenAway() {
    var dismissed = 0
    var item by mutableStateOf<FluidShareItem?>(pngItem())
    val options = photoOptions(mutableListOf())
    compose.setContent { Hosted { FluidShareSheet(item, onDismissRequest = { dismissed++; item = null }, copyOptions = options) } }
    tap(FluidShareSheetTags.Options)
    compose.onNodeWithTag(FluidPrepareCopyTags.Page).assertIsDisplayed()
    // Il back di sistema toglie la pagina in cima, non il foglio.
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
    compose.onNodeWithTag(FluidPrepareCopyTags.Page).assertDoesNotExist()
    assertTrue(displayed(FluidShareSheetTags.Copy))
    assertEquals(0, dismissed)
    // Sulla prima pagina il back chiude il foglio, come sempre.
    compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
    assertEquals(1, dismissed)
  }

  @Test fun aFingerFromTheLeftEdgeTakesThePageBack() {
    var dismissed = 0
    val options = photoOptions(mutableListOf())
    compose.setContent { Hosted { FluidShareSheet(pngItem(), onDismissRequest = { dismissed++ }, copyOptions = options) } }
    tap(FluidShareSheetTags.Options)
    compose.onNodeWithTag(FluidPrepareCopyTags.Page).assertIsDisplayed()
    compose.onNodeWithTag(FluidSheetPagesTags.Pages).performTouchInput {
      swipeRight(startX = left + 4f, endX = left + width * 0.7f, durationMillis = 250)
    }
    compose.waitForIdle()
    compose.onNodeWithTag(FluidPrepareCopyTags.Page).assertDoesNotExist()
    assertTrue(displayed(FluidShareSheetTags.Copy))
    assertEquals(0, dismissed)
  }

  /**
   * Di quanto la pagina delle opzioni sta a destra del suo posto due fotogrammi dopo il tocco, in dp. Limiti non ritagliati:
   * una pagina ancora tutta fuori dal contenitore ha limiti ritagliati vuoti.
   */
  private fun optionsOffsetAfterTwoFrames(reduced: Boolean): Float {
    val options = photoOptions(mutableListOf())
    compose.setContent { Hosted(reduced) { FluidShareSheet(pngItem(), onDismissRequest = {}, copyOptions = options) } }
    compose.waitForIdle()
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag(FluidShareSheetTags.Options).performTouchInput { click() }
    Snapshot.sendApplyNotifications()
    repeat(2) { compose.mainClock.advanceTimeByFrame() }
    val pages = compose.onNodeWithTag(FluidSheetPagesTags.Pages).getUnclippedBoundsInRoot()
    val page = compose.onNodeWithTag(FluidPrepareCopyTags.Page).getUnclippedBoundsInRoot()
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    return (page.left - pages.left).value
  }

  @Test fun thePageSlidesInFromTheRight() {
    val offset = optionsOffsetAfterTwoFrames(reduced = false)
    assertTrue("a due fotogrammi la pagina e' ancora a destra: $offset dp", offset > 20f)
    val pages = compose.onNodeWithTag(FluidSheetPagesTags.Pages).getUnclippedBoundsInRoot()
    assertTrue("e non oltre la larghezza: $offset dp", offset <= (pages.right - pages.left).value + 0.5f)
  }

  @Test fun withReducedMotionThePageIsThereAtOnce() {
    assertEquals(0f, optionsOffsetAfterTwoFrames(reduced = true), 0.5f)
  }

  @Test fun theQrDoneStaysInsideTheSheetAtHalfHeight() {
    // Il tablet il 25 novembre: a meta' altezza «Fine» restava sotto il bordo del foglio. Il codice si stringe, il tasto resta.
    compose.setContent {
      Hosted {
        CompositionLocalProvider(LocalFluidQrEncoder provides FluidZxingQr) {
          FluidShareSheet(FluidShareItem.link("https://pampa.example.org/app/dev.antigravity.fluidglass", "Fluid Glass"), onDismissRequest = {})
        }
      }
    }
    tap(FluidShareSheetTags.Qr)
    waitForTag(FluidShareSheetTags.QrDone)
    compose.waitForIdle()
    val pages = compose.onNodeWithTag(FluidSheetPagesTags.Pages).getUnclippedBoundsInRoot()
    val done = compose.onNodeWithTag(FluidShareSheetTags.QrDone).getUnclippedBoundsInRoot()
    val code = compose.onNodeWithTag(FluidShareSheetTags.QrCode).getUnclippedBoundsInRoot()
    assertTrue("«Fine» dentro il foglio: fondo ${done.bottom}, foglio ${pages.bottom}", done.bottom <= pages.bottom + 0.5.dp)
    compose.onNodeWithTag(FluidShareSheetTags.QrDone).assertIsDisplayed()
    // Il codice resta quadrato e abbastanza grande da inquadrare.
    assertEquals((code.right - code.left).value, (code.bottom - code.top).value, 1f)
    assertTrue("il codice: ${code.right - code.left}", code.right - code.left >= 120.dp)
  }

  @Test fun withoutAnEncoderThereIsNoQr() {
    compose.setContent { Hosted { FluidShareSheet(FluidShareItem.link("https://example.org", "Link"), onDismissRequest = {}) } }
    compose.onNodeWithTag(FluidShareSheetTags.Copy).assertExists()
    compose.onNodeWithTag(FluidShareSheetTags.Qr).assertDoesNotExist()
  }

  @Test fun talkBackReadsTheHeaderTheCloseButtonAndTheRows() {
    compose.setContent { Hosted { FluidShareSheet(pngItem(), onDismissRequest = {}) } }
    compose.waitForIdle()
    val close = compose.onNodeWithTag(FluidShareSheetTags.Close).fetchSemanticsNode()
    assertEquals("Chiudi", close.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString())
    val thumb = compose.onNodeWithTag(FluidShareSheetTags.Thumbnail).fetchSemanticsNode()
    assertEquals("Anteprima di Voto di matematica", thumb.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString())
    val copy = compose.onNodeWithTag(FluidShareSheetTags.Copy).fetchSemanticsNode()
    assertEquals(Role.Button, copy.config.getOrNull(SemanticsProperties.Role))
    // Il sottotitolo del file: il tipo e il peso.
    compose.onNodeWithText("Immagine PNG", substring = true).assertExists()
    // Le righe sono alte almeno 44 dp.
    val rows = listOf(FluidShareSheetTags.Copy, FluidShareSheetTags.Save, FluidShareSheetTags.Print, FluidShareSheetTags.MoreApps)
    for (tag in rows) {
      val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().size
      assertTrue("$tag alto ${bounds.height}px", bounds.height >= 44 * 2)
    }
  }

  // --- Prepara la copia ---------------------------------------------------------------------------------------------

  private val location = FluidCopyOption("posizione", "Posizione")

  /** La copia come la verificherebbe l'app: [location] e' cio' che c'e' nel file, [included] cio' che l'utente ha chiesto. */
  private fun copyWith(location: Boolean?, included: Boolean = false): FluidPreparedCopy = FluidPreparedCopy(
    item = pngItem(),
    checks = listOfNotNull(location?.let {
      FluidCopyCheck("Posizione", if (it) "presente" else "tolta", passed = included || !it, option = "posizione")
    }),
  )

  @Test fun theRuleBlocksACopyThatDoesNotMatch() {
    val off = FluidCopyChoice(null, emptySet())
    assertTrue(fluidCopyProblems(listOf(location), off, copyWith(location = false)).isEmpty())
    assertEquals(1, fluidCopyProblems(listOf(location), off, copyWith(location = true)).size)
    assertEquals("Non verificato: Posizione", fluidCopyProblems(listOf(location), off, copyWith(location = null)).single())
    // Incluso: nessuna prova da chiedere.
    assertTrue(fluidCopyProblems(listOf(location), FluidCopyChoice(null, setOf("posizione")), copyWith(location = null)).isEmpty())
  }

  @Test fun theSheetWaitsShowsTheSummaryAndHandsTheCopyOver() {
    var ready: FluidShareItem? = null
    var broken by mutableStateOf(false)
    val choices = mutableListOf<FluidCopyChoice>()
    compose.setContent {
      Hosted {
        FluidPrepareCopySheet(
          visible = true,
          onDismissRequest = {},
          onReady = { ready = it },
          prepare = { choice ->
            choices += choice
            copyWith(location = choice.includes("posizione") || broken, included = choice.includes("posizione"))
          },
          options = listOf(location),
        )
      }
    }
    compose.waitForIdle()
    // Di serie la posizione e' inclusa: la copia e' pronta, «Condividi» acceso.
    compose.waitUntil(3_000) { runCatching { compose.onNodeWithTag(FluidPrepareCopyTags.Share).assertIsEnabled() }.isSuccess }
    // Il dito spegne «Posizione»: la copia si rifa', e la prova dice «tolta».
    tap(FluidPrepareCopyTags.option("posizione"))
    compose.waitUntil(3_000) { choices.lastOrNull()?.includes("posizione") == false }
    compose.waitUntil(3_000) { runCatching { compose.onNodeWithText("tolta").assertExists() }.isSuccess }
    compose.onNodeWithTag(FluidPrepareCopyTags.Share).assertIsEnabled()
    tap(FluidPrepareCopyTags.Share)
    assertNotNull(ready)

    // L'esportatore sbaglia: la posizione resta. «Condividi» si spegne e c'e' «Rifai la copia».
    ready = null
    compose.runOnIdle { broken = true }
    tap(FluidPrepareCopyTags.option("posizione"))
    tap(FluidPrepareCopyTags.option("posizione"))
    compose.waitUntil(3_000) { runCatching { compose.onNodeWithTag(FluidPrepareCopyTags.Retry).assertExists() }.isSuccess }
    compose.onNodeWithTag(FluidPrepareCopyTags.Share).assertIsNotEnabled()
    compose.onNodeWithText("La copia non corrisponde alle opzioni: non parte.").assertExists()
    tap(FluidPrepareCopyTags.Share)
    assertNull("un invio bloccato non parte", ready)
    assertFalse(choices.isEmpty())
  }

  // --- R2 · La privacy della copia: rotazione, processo ucciso, copie in ritardo, scelte di partenza ----------------

  /** L'Uri della copia senza posizione: un file diverso dall'originale, cosi' si vede quale dei due parte. */
  private val strippedUri: Uri get() = F8p10OtherApp.uri("senza-posizione.jpg")

  /**
   * Le opzioni di una foto con la posizione: senza «Posizione» la copia e' un altro file. [broken] e' l'esportatore che
   * dimentica di toglierla; [included] la scelta di partenza.
   */
  private fun strippedOptions(prepared: MutableList<FluidCopyChoice>, broken: Boolean = false, included: Boolean = true) = FluidShareCopyOptions(
    prepare = { choice ->
      prepared += choice
      val kept = choice.includes("posizione") || broken
      val item = if (kept) pngItem().copy(detail = "Posizione inclusa") else
        FluidShareItem.file(strippedUri, "Voto di matematica.png", "image/png", 200L * 1024, title = "Voto di matematica").copy(detail = "Senza posizione")
      FluidPreparedCopy(item, listOf(FluidCopyCheck("Posizione", if (kept) "presente" else "tolta", passed = choice.includes("posizione") || !kept, option = "posizione")))
    },
    options = listOf(FluidCopyOption("posizione", "Posizione", included = included)),
  )

  /** «Altre app…» e l'Uri che parte col foglio di Android. */
  private fun sentStream(): Uri {
    tap(FluidShareSheetTags.MoreApps)
    val chooser = nextIntent()!!
    @Suppress("DEPRECATION")
    val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
    @Suppress("DEPRECATION")
    return send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)!!
  }

  private fun hasText(text: String) = compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

  private fun enabled(tag: String) = runCatching { compose.onNodeWithTag(tag).assertIsEnabled() }.isSuccess

  /** Le opzioni del foglio: «Posizione» spenta e confermata. */
  private fun confirmWithoutLocation() {
    tap(FluidShareSheetTags.Options)
    compose.waitUntil(3_000) { enabled(FluidPrepareCopyTags.Done) }
    tap(FluidPrepareCopyTags.option("posizione"))
    compose.waitUntil(3_000) { hasText("tolta") }
    compose.waitUntil(3_000) { enabled(FluidPrepareCopyTags.Done) }
    tap(FluidPrepareCopyTags.Done)
    compose.waitUntil(3_000) { hasText("Senza posizione") }
  }

  @Test fun aCopyWithoutLocationSurvivesARotation() {
    // L'app tiene l'oggetto in un ViewModel: dopo la rotazione (o col processo ripristinato) il foglio si riapre su di lui.
    val restoration = StateRestorationTester(compose)
    val original = pngItem().copy(detail = "Posizione inclusa")
    val options = strippedOptions(mutableListOf())
    restoration.setContent { Hosted { FluidShareSheet(original, onDismissRequest = {}, copyOptions = options) } }
    confirmWithoutLocation()
    restoration.emulateSavedInstanceStateRestore()
    compose.waitForIdle()
    waitForTag(FluidShareSheetTags.MoreApps)
    assertTrue("dopo la rotazione resta la copia senza posizione", hasText("Senza posizione"))
    assertFalse("l'originale col GPS non torna", hasText("Posizione inclusa"))
    assertEquals("parte la copia, non l'originale", strippedUri, sentStream())
  }

  @Test fun theOptionsSheetKeepsTheChoiceAcrossARotation() {
    val restoration = StateRestorationTester(compose)
    var ready: FluidShareItem? = null
    val choices = mutableListOf<FluidCopyChoice>()
    restoration.setContent {
      Hosted {
        FluidPrepareCopySheet(
          visible = true,
          onDismissRequest = {},
          onReady = { ready = it },
          prepare = { choice -> choices += choice; copyWith(location = choice.includes("posizione"), included = choice.includes("posizione")) },
          options = listOf(location),
        )
      }
    }
    compose.waitUntil(3_000) { enabled(FluidPrepareCopyTags.Share) }
    tap(FluidPrepareCopyTags.option("posizione"))
    compose.waitUntil(3_000) { choices.lastOrNull()?.includes("posizione") == false }
    compose.waitUntil(3_000) { hasText("tolta") }
    restoration.emulateSavedInstanceStateRestore()
    compose.waitForIdle()
    compose.waitUntil(3_000) { enabled(FluidPrepareCopyTags.Share) }
    assertFalse("dopo la rotazione la posizione resta tolta: ${choices.last()}", choices.last().includes("posizione"))
    tap(FluidPrepareCopyTags.Share)
    assertNotNull(ready)
  }

  @Test fun anOldCopyThatFinishesLateIsNeverConfirmed() {
    // L'app prepara con una funzione che non guarda l'annullamento (un callback avvolto in `suspendCoroutine`).
    val pending = mutableListOf<Pair<FluidCopyChoice, Continuation<FluidPreparedCopy>>>()
    val options = FluidShareCopyOptions(prepare = { choice -> suspendCoroutine { pending += choice to it } }, options = listOf(location))
    compose.setContent { Hosted { FluidShareSheet(pngItem(), onDismissRequest = {}, copyOptions = options) } }
    tap(FluidShareSheetTags.Options)
    compose.waitUntil(3_000) { pending.size == 1 }
    tap(FluidPrepareCopyTags.option("posizione"))
    compose.waitUntil(3_000) { pending.size == 2 }
    assertTrue(pending[0].first.includes("posizione"))
    assertFalse(pending[1].first.includes("posizione"))
    // La copia vecchia, con la posizione, finisce dopo che la persona l'ha tolta.
    compose.runOnIdle {
      pending[0].second.resume(FluidPreparedCopy(pngItem().copy(detail = "Posizione inclusa"),
        listOf(FluidCopyCheck("Posizione", "presente", option = "posizione"))))
    }
    compose.waitForIdle()
    assertFalse("la copia vecchia non si conferma con la scelta nuova", enabled(FluidPrepareCopyTags.Done))
    // Arriva quella giusta: si conferma lei.
    compose.runOnIdle {
      pending[1].second.resume(FluidPreparedCopy(FluidShareItem.file(strippedUri, "Voto.png", "image/png").copy(detail = "Senza posizione"),
        listOf(FluidCopyCheck("Posizione", "tolta", option = "posizione"))))
    }
    compose.waitUntil(3_000) { enabled(FluidPrepareCopyTags.Done) }
    tap(FluidPrepareCopyTags.Done)
    compose.waitUntil(3_000) { hasText("Senza posizione") }
    assertEquals(strippedUri, sentStream())
  }

  @Test fun withLocationOffByDefaultTheOriginalNeverLeaves() {
    // L'app sbaglia: «Posizione» e' spenta di serie, ma passa al foglio l'originale col GPS.
    val prepared = mutableListOf<FluidCopyChoice>()
    val options = strippedOptions(prepared, included = false)
    compose.setContent { Hosted { FluidShareSheet(pngItem().copy(detail = "Posizione inclusa"), onDismissRequest = {}, copyOptions = options) } }
    waitForTag(FluidShareSheetTags.MoreApps)
    assertEquals("parte la copia verificata della scelta di partenza", strippedUri, sentStream())
    assertFalse(prepared.single().includes("posizione"))
  }

  @Test fun aStartCopyThatKeepsTheLocationDoesNotLeave() {
    val options = strippedOptions(mutableListOf(), broken = true, included = false)
    compose.setContent { Hosted { FluidShareSheet(pngItem(), onDismissRequest = {}, copyOptions = options) } }
    waitForTag(FluidShareSheetTags.Problem)
    assertTrue(hasText("La copia non corrisponde alle opzioni"))
    compose.onNodeWithTag(FluidShareSheetTags.MoreApps).assertDoesNotExist()
    compose.onNodeWithTag(FluidShareSheetTags.Copy).assertDoesNotExist()
  }

  @Test fun aFileUriIsCopiedAndLeavesAsContent() {
    // Un `file://` passato all'oggetto: sul dispositivo `startActivity` lancerebbe FileUriExposedException.
    fakeUris()
    val photo = appFile("Fiori.jpg", 3_000)
    compose.setContent { Hosted { FluidShareSheet(FluidShareItem.file(Uri.fromFile(photo), "Fiori.jpg", "image/jpeg"), onDismissRequest = {}) } }
    waitForTag(FluidShareSheetTags.MoreApps)
    val uri = sentStream()
    assertEquals("mai un file:// fuori dall'app: $uri", "content", uri.scheme)
    assertTrue(uri.toString(), uri.toString().contains("/copia/"))
  }

  @Test fun afterCopyTheOtherActionsStayQuiet() {
    var item by mutableStateOf<FluidShareItem?>(FluidShareItem.link("https://example.org/a", "A"))
    val outcomes = mutableListOf<FluidShareOutcome>()
    compose.setContent { Hosted { FluidShareSheet(item, onDismissRequest = { item = null }, onShared = { outcomes += it }) } }
    waitForTag(FluidShareSheetTags.Copy)
    waitForTag(FluidShareSheetTags.MoreApps)
    compose.waitForIdle()
    // Nei 450 ms della spunta un secondo tocco su «Altre app…».
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag(FluidShareSheetTags.Copy).performTouchInput { click() }
    compose.mainClock.advanceTimeByFrame()
    compose.onNodeWithTag(FluidShareSheetTags.MoreApps).performTouchInput { click() }
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertEquals("un esito solo", listOf("Copied"), outcomes.map { it.javaClass.simpleName })
    assertNull("nessun foglio di Android dopo «Copiato»", nextIntent())
    assertNull(item)
  }

  @Test fun theQrIsEncodedOnlyWhenAskedAndOffTheMainThread() {
    val threads = java.util.Collections.synchronizedList(mutableListOf<Thread>())
    val encoder = FluidQrEncoder { text -> threads += Thread.currentThread(); FluidZxingQr.encode(text) }
    compose.setContent {
      Hosted {
        CompositionLocalProvider(LocalFluidQrEncoder provides encoder) {
          FluidShareSheet(FluidShareItem.link("https://example.org/app", "Fluid Glass"), onDismissRequest = {})
        }
      }
    }
    waitForTag(FluidShareSheetTags.Qr)
    compose.waitForIdle()
    assertTrue("aprire il foglio non codifica niente: $threads", threads.isEmpty())
    tap(FluidShareSheetTags.Qr)
    waitForTag(FluidShareSheetTags.QrCode)
    val main = Looper.getMainLooper().thread
    assertTrue("codificato fuori dal thread principale: $threads", threads.isNotEmpty() && threads.none { it === main })
  }

  // --- R2 · «Copia» di un contenuto delicato (domanda 4, scelta b) -------------------------------------------------

  /** Vero se gli appunti sono segnati delicati; null se sono vuoti. */
  private fun clipIsSensitive(): Boolean? {
    val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip ?: return null
    return clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
  }

  @Test fun aSensitiveItemIsCopiedAsSensitive() {
    var item by mutableStateOf<FluidShareItem?>(FluidShareItem.text("Codice di recupero 4821-9930", title = "Codice").copy(sensitive = true))
    compose.setContent { Hosted { FluidShareSheet(item, onDismissRequest = { item = null }) } }
    tap(FluidShareSheetTags.Copy)
    assertEquals("un codice va negli appunti segnato delicato", true, clipIsSensitive())
    compose.waitUntil(3_000) { item == null }

    // Un testo qualsiasi no: niente segno.
    compose.runOnIdle { item = FluidShareItem.text("Portare il libro di storia") }
    tap(FluidShareSheetTags.Copy)
    assertEquals(false, clipIsSensitive())
    compose.waitUntil(3_000) { item == null }

    // Senza il dito (la tastiera, TalkBack, l'accesso con interruttori): la stessa azione, lo stesso segno.
    compose.runOnIdle { item = FluidShareItem.text("Password del Wi-Fi: Fiori2026", title = "Wi-Fi").copy(sensitive = true) }
    waitForTag(FluidShareSheetTags.Copy)
    compose.onNodeWithTag(FluidShareSheetTags.Copy).performSemanticsAction(SemanticsActions.OnClick)
    compose.waitForIdle()
    assertEquals(true, clipIsSensitive())
  }

  @Test fun aSensitiveCopyStaysSensitiveAfterARotation() {
    val restoration = StateRestorationTester(compose)
    val original = FluidShareItem.text("Mario Rossi: codice 4821-9930", title = "Codice")
    val name = FluidCopyOption("nome", "Nome")
    val options = FluidShareCopyOptions(
      prepare = { choice ->
        val kept = choice.includes("nome")
        FluidPreparedCopy(
          item = FluidShareItem.text(if (kept) original.text!! else "Codice 4821-9930", title = "Codice")
            .copy(sensitive = true, detail = if (kept) "Con il nome" else "Senza nome"),
          checks = listOf(FluidCopyCheck("Nome", if (kept) "presente" else "tolto", option = "nome")),
        )
      },
      options = listOf(name),
    )
    restoration.setContent { Hosted { FluidShareSheet(original, onDismissRequest = {}, copyOptions = options) } }
    tap(FluidShareSheetTags.Options)
    compose.waitUntil(3_000) { enabled(FluidPrepareCopyTags.Done) }
    tap(FluidPrepareCopyTags.option("nome"))
    compose.waitUntil(3_000) { hasText("tolto") }
    compose.waitUntil(3_000) { enabled(FluidPrepareCopyTags.Done) }
    tap(FluidPrepareCopyTags.Done)
    compose.waitUntil(3_000) { hasText("Senza nome") }
    restoration.emulateSavedInstanceStateRestore()
    compose.waitForIdle()
    waitForTag(FluidShareSheetTags.Copy)
    tap(FluidShareSheetTags.Copy)
    assertEquals("la copia salvata resta delicata", true, clipIsSensitive())
    val text = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip!!.getItemAt(0).text.toString()
    assertEquals("e senza il nome", "Codice 4821-9930", text)
  }
}
