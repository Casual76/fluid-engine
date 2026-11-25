package dev.antigravity.fluidengine.sample.novita

import android.Manifest
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.core.app.ActivityOptionsCompat
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.FluidFirstUseMemory
import dev.antigravity.fluidengine.media.FluidAttachmentMenu
import dev.antigravity.fluidengine.media.FluidAttachmentPanel
import dev.antigravity.fluidengine.media.FluidAttachmentPanelHost
import dev.antigravity.fluidengine.media.FluidAttachmentPanelState
import dev.antigravity.fluidengine.media.FluidAttachmentPanelTags
import dev.antigravity.fluidengine.media.FluidAttachmentSource
import dev.antigravity.fluidengine.media.FluidDeviceMediaSource
import dev.antigravity.fluidengine.media.FluidFakeMediaSource
import dev.antigravity.fluidengine.media.FluidMediaAccess
import dev.antigravity.fluidengine.media.FluidMediaKind
import dev.antigravity.fluidengine.media.FluidMediaPicker
import dev.antigravity.fluidengine.media.FluidMediaPickerState
import dev.antigravity.fluidengine.media.FluidMediaPickerTags
import dev.antigravity.fluidengine.media.FluidMediaRequest
import dev.antigravity.fluidengine.media.FluidMediaSource
import dev.antigravity.fluidengine.media.FluidMediaSourceCapabilities
import dev.antigravity.fluidengine.media.FluidPickedMedia
import dev.antigravity.fluidengine.media.rememberFluidAttachmentPanelState
import dev.antigravity.fluidengine.media.rememberFluidMediaPickerState
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidFirstUseMemory
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

/** Il MediaStore finto della prova del telefono: tre foto, con la condizione sull'id della consegna. */
class F8p14MediaStore : ContentProvider() {
  companion object {
    val ids = mutableListOf(11L, 12L, 13L)
  }

  override fun onCreate(): Boolean = true

  override fun query(uri: Uri, projection: Array<out String>?, queryArgs: Bundle?, cancellationSignal: CancellationSignal?): Cursor {
    val columns = projection ?: arrayOf(MediaStore.MediaColumns._ID)
    val cursor = MatrixCursor(columns)
    val selection = queryArgs?.getString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION).orEmpty()
    val args = queryArgs?.getStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS).orEmpty()
    ids.filter { !selection.startsWith("_id = ?") || it.toString() == args.firstOrNull() }.forEachIndexed { i, id ->
      cursor.addRow(columns.map { column ->
        when (column) {
          MediaStore.MediaColumns._ID -> id
          MediaStore.Files.FileColumns.MEDIA_TYPE -> MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
          MediaStore.MediaColumns.MIME_TYPE -> "image/jpeg"
          "datetaken" -> 1_764_000_000_000L - i * 86_400_000L
          MediaStore.MediaColumns.DISPLAY_NAME -> "IMG_$id.jpg"
          "bucket_id" -> "1"
          "bucket_display_name" -> "Camera"
          else -> null
        }
      }.toTypedArray())
    }
    return cursor
  }

  override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor =
    query(uri, projection, Bundle(), null)

  override fun getType(uri: Uri): String? = null
  override fun insert(uri: Uri, values: ContentValues?): Uri? = null
  override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
  override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}

/**
 * F8.14 · Foto e allegati col dito vero (eventi del sistema, orologio che avanza: `MaterialFinger`) e col vetro vero
 * (verdetti degli shader accettati): la scelta in ordine col numero, il dito di lato, il tetto, gli album, la ricerca
 * della sorgente esterna finta (MyCloud Gallery), il permesso negato e «solo alcune foto» col dialogo di Android finto,
 * TalkBack, l'anteprima che nasce dalla foto, il «+» e il pannello al posto della tastiera, il movimento ridotto.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class F8p14MediaUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: View
  private lateinit var finger: MaterialFinger

  /** Il dialogo di Android finto: ricorda cosa gli si chiede e risponde quando la prova lo dice. */
  private inner class FakeRegistry : ActivityResultRegistry() {
    val asked = mutableListOf<List<String>>()
    private var code = -1

    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
      if (input is Array<*>) {
        asked += input.map { it.toString() }
        code = requestCode
      }
    }

    fun answer(permissions: List<String>, granted: Set<String>) {
      val app = shadowOf(compose.activity.application)
      permissions.forEach { if (it in granted) app.grantPermissions(it) else app.denyPermissions(it) }
      compose.runOnIdle { dispatchResult(code, permissions.associateWith { it in granted }) }
      advance(400)
    }
  }

  private val registry = FakeRegistry()
  private val owner = object : ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry get() = registry
  }

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
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

  private fun show(reduced: Boolean = false, content: @Composable () -> Unit) {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reduced) 0f else 1f),
          LocalFluidGlassModalHostState provides host,
          LocalFluidFirstUseMemory provides remember { FluidFirstUseMemory.inMemory() },
          LocalActivityResultRegistryOwner provides owner,
        ) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    advance(800)
    finger = MaterialFinger(view)
  }

  private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  private fun exists(tag: String): Boolean =
    compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun described(text: String): Boolean =
    compose.onAllNodes(hasContentDescription(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun shown(text: String): Boolean =
    compose.onAllNodes(hasText(text, substring = true, ignoreCase = true), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  /** Tocca la seconda meta' del segmentato «Foto | Album» (col dito vero). */
  private fun tapAlbumsTab() {
    val tabs = bounds(FluidMediaPickerTags.Tabs)
    tap(Offset(tabs.left + tabs.width * 0.75f, tabs.center.y))
    advance(400)
  }

  /** Un avviso dell'host: il tasto si tocca quando l'avviso e' arrivato. */
  private fun alertButton(text: String) {
    advance(600)
    compose.onAllNodes(hasText(text), useUnmergedTree = false).onFirst().performClick()
    advance(600)
  }

  private fun tap(at: Offset) {
    finger.down(at)
    advance(48)
    finger.up(at)
    advance(400)
  }

  private fun tapTag(tag: String) = tap(bounds(tag).center)

  private fun tapText(text: String) {
    val node = compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().first()
    tap(node.boundsInRoot.center)
  }

  private fun longPress(at: Offset) {
    finger.down(at)
    advance(700)
    finger.up(at)
    advance(600)
  }

  private fun moveTo(from: Offset, to: Offset, steps: Int): Offset {
    var at = from
    val dx = (to.x - from.x) / steps
    val dy = (to.y - from.y) / steps
    repeat(steps) {
      advance(16)
      at = Offset(at.x + dx, at.y + dy)
      finger.move(at)
    }
    return at
  }

  private fun cell(source: String, id: String) = FluidMediaPickerTags.cell("$source/$id")

  private lateinit var state: FluidMediaPickerState
  private val results = mutableStateListOf<FluidPickedMedia>()
  private var cancelled = 0

  private fun picker(vararg sources: FluidMediaSource, limit: Int = 10, reduced: Boolean = false) = show(reduced) {
    state = rememberFluidMediaPickerState(sources.toList(), FluidMediaRequest.attachments(limit))
    FluidMediaPicker(state, onResult = { results.clear(); results += it }, onCancel = { cancelled++ }, offerSystemPicker = false)
  }

  private fun cloud(count: Int = 40) = FluidFakeMediaSource.sample(count = count)

  private fun selectedIds(): List<String> = state.selectedItems.map { it.id }

  // --- La scelta ---------------------------------------------------------------------------------------------------

  @Test fun laSceltaEInOrdineColNumero() {
    val cloud = cloud()
    picker(cloud)
    assertTrue("la griglia c'e'", exists(FluidMediaPickerTags.Grid))
    for (id in listOf("m004", "m000", "m001", "m007")) tapTag(cell("mycloud", id))
    assertEquals(listOf("m004", "m000", "m001", "m007"), selectedIds())
    assertTrue(described("Selezionato, numero 1"))
    assertTrue(described("Selezionato, numero 4"))
    // Si toglie la seconda: quelle dopo scalano di uno.
    tapTag(cell("mycloud", "m000"))
    advance(600)
    assertEquals(listOf("m004", "m001", "m007"), selectedIds())
    assertFalse(described("Selezionato, numero 4"))
    assertTrue(described("Selezionato, numero 3"))
    // Il titolo dice la scelta.
    assertTrue(shown("selezionat"))
    // Il ✓ consegna nell'ordine della scelta.
    tapTag(FluidMediaPickerTags.Confirm)
    advance(800)
    assertEquals(listOf("m004", "m001", "m007"), results.map { it.item!!.id })
    assertEquals(listOf("m004", "m001", "m007"), cloud.deliveries.last())
  }

  @Test fun ilDitoDiLatoSceglieInFila() {
    picker(cloud())
    val a = bounds(cell("mycloud", "m000")).center
    val c = bounds(cell("mycloud", "m002")).center
    finger.down(a)
    val end = moveTo(a, c, steps = 12)
    advance(100)
    finger.up(end)
    advance(400)
    assertEquals(listOf("m000", "m001", "m002"), selectedIds())
  }

  @Test fun aTettoPienoLeAltreSiSpengono() {
    picker(cloud(), limit = 3)
    for (id in listOf("m000", "m001", "m002", "m003")) tapTag(cell("mycloud", id))
    assertEquals(listOf("m000", "m001", "m002"), selectedIds())
    assertTrue(state.selection.atLimit)
  }

  @Test fun unaFotoSolaSiSceglieConUnTocco() {
    val cloud = cloud()
    show {
      state = rememberFluidMediaPickerState(listOf(cloud), FluidMediaRequest(kinds = setOf(FluidMediaKind.Photo), limit = 1))
      FluidMediaPicker(state, onResult = { results.clear(); results += it }, onCancel = { cancelled++ }, offerSystemPicker = false)
    }
    // Un tetto di 1: niente segni, niente ✓, il tocco sceglie e torna.
    assertFalse(exists(FluidMediaPickerTags.Confirm))
    tapTag(cell("mycloud", "m001"))
    advance(600)
    assertEquals(listOf("m001"), results.map { it.item!!.id })
  }

  // --- Album e ricerca ---------------------------------------------------------------------------------------------

  @Test fun gliAlbumDiPiuSorgenti() {
    val phone = FluidFakeMediaSource.sample(id = FluidDeviceMediaSource.SourceId, label = "Su questo telefono", count = 12)
    val cloud = cloud()
    picker(phone, cloud)
    // La prima pagina e' la prima sorgente.
    assertTrue(exists(cell("device", "m000")))
    tapAlbumsTab()
    assertTrue(exists(FluidMediaPickerTags.Albums))
    assertTrue(shown("Su questo telefono"))
    assertTrue(shown("MyCloud Gallery"))
    tapTag(FluidMediaPickerTags.album("mycloud", "vacanze"))
    advance(800)
    assertEquals("vacanze", cloud.queries.last().album)
    val inside = cloud.entries.filter { "vacanze" in it.albums }.map { it.item.id }
    val outside = cloud.entries.first { "vacanze" !in it.albums }.item.id
    assertTrue(exists(cell("mycloud", inside.first())))
    assertFalse(exists(cell("mycloud", outside)))
    // Si sceglie anche dentro un album, e la scelta resta tornando indietro.
    tapTag(cell("mycloud", inside.first()))
    compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
    advance(800)
    assertEquals(listOf(inside.first()), selectedIds())
  }

  @Test fun laRicercaPerSignificatoDellaSorgenteEsterna() {
    val cloud = cloud(60)
    // Il telefono cerca solo per nome: la ricerca va prima dove si cerca per significato.
    val phone = FluidFakeMediaSource(
      id = FluidDeviceMediaSource.SourceId, label = "Telefono",
      entries = FluidFakeMediaSource.sample(id = FluidDeviceMediaSource.SourceId, count = 6).entries,
      capabilities = FluidMediaSourceCapabilities(search = true, semanticSearch = false),
    )
    picker(phone, cloud)
    tapTag(FluidMediaPickerTags.SearchButton)
    advance(600)
    compose.onNode(hasSetTextAction()).performTextInput("mare d'estate")
    advance(1200)
    // Cerca prima nella sorgente che sa cercare per significato, e dice cosa ha capito.
    assertEquals("mare d'estate", cloud.queries.last().text)
    assertTrue(shown("Mare · Estate"))
    val tagged = cloud.entries.filter { e -> e.tags.any { it == "mare" || it == "spiaggia" } }.map { it.item.id }.toSet()
    val untagged = cloud.entries.first { it.item.id !in tagged }.item.id
    assertFalse(exists(cell("mycloud", untagged)))
  }

  // --- L'accesso -----------------------------------------------------------------------------------------------------

  @Test fun ilPermessoNegatoPoiSoloAlcune() {
    F8p14MediaStore.ids.clear()
    F8p14MediaStore.ids += listOf(11L, 12L, 13L)
    Robolectric.buildContentProvider(F8p14MediaStore::class.java).create(ProviderInfo().apply { authority = MediaStore.AUTHORITY })
    val device = FluidDeviceMediaSource(compose.activity)
    picker(device)
    assertTrue(device.access is FluidMediaAccess.NotAllowed)
    assertTrue("al posto della griglia la richiesta", exists(FluidMediaPickerTags.Access))
    assertFalse(exists(FluidMediaPickerTags.Grid))
    // Dal tasto: la spiegazione, poi il dialogo di Android.
    tapText("Consenti l'accesso")
    alertButton("Continua")
    val asked = registry.asked.last()
    assertTrue(Manifest.permission.READ_MEDIA_IMAGES in asked)
    assertTrue("android.permission.READ_MEDIA_VISUAL_USER_SELECTED" in asked)
    // «Solo alcune foto»: Android ne concede due, e il selettore lo dice.
    F8p14MediaStore.ids.retainAll { it != 13L }
    registry.answer(asked, setOf("android.permission.READ_MEDIA_VISUAL_USER_SELECTED"))
    advance(800)
    assertEquals(FluidMediaAccess.Limited, device.access)
    assertTrue(exists(FluidMediaPickerTags.Limited))
    assertTrue(exists(cell("device", "11")))
    assertFalse(exists(cell("device", "13")))
    // «Gestisci» riapre la scelta di Android.
    val before = registry.asked.size
    tapText("Gestisci")
    assertEquals(before + 1, registry.asked.size)
  }

  @Test fun bloccataSiSbloccaEIlNasSpentoSiDice() {
    val cloud = cloud()
    cloud.lock()
    picker(cloud)
    assertTrue(exists(FluidMediaPickerTags.Access))
    assertTrue(shown("MyCloud Gallery è bloccata"))
    // Sbloccata dall'app (qui a mano): «Sblocca» rilegge l'accesso.
    cloud.access = FluidMediaAccess.Full
    tapText("Sblocca")
    advance(800)
    assertTrue(exists(FluidMediaPickerTags.Grid))
    // Il NAS spento: una foto solo nel backup non esce, e l'avviso lo dice.
    cloud.backupReachable = false
    val backup = cloud.entries.first { it.item.backupOnly }.item.id
    tapTag(cell("mycloud", backup))
    tapTag(FluidMediaPickerTags.Confirm)
    advance(800)
    assertTrue(results.isEmpty())
    assertTrue(shown("Il backup non risponde"))
  }

  // --- TalkBack, anteprima, movimento ridotto ----------------------------------------------------------------------

  @Test fun talkBackSenteTipoDataENuvola() {
    val cloud = cloud()
    picker(cloud)
    val entry = cloud.entries.first { it.item.backupOnly && it.item.kind == FluidMediaKind.Video }
    val node = compose.onNodeWithTag(cell("mycloud", entry.item.id), useUnmergedTree = true).fetchSemanticsNode()
    val description = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty()
    assertTrue(description, description.startsWith(entry.item.description!!))
    assertTrue(description, "Video" in description && "secondi" in description && "solo nel backup" in description)
    val actions = node.config.getOrNull(SemanticsActions.CustomActions).orEmpty().map { it.label }
    assertTrue("l'anteprima anche senza la pressione lunga", "Anteprima" in actions)
  }

  @Test fun lAnteprimaNasceDallaFotoEDaLiSiSceglie() {
    picker(cloud())
    longPress(bounds(cell("mycloud", "m002")).center)
    assertTrue("l'anteprima e' aperta", exists(FluidMediaPickerTags.Preview))
    assertTrue("la pressione lunga non sceglie", selectedIds().isEmpty())
    tap(compose.onAllNodes(hasContentDescription("Seleziona"), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot.center)
    assertEquals(listOf("m002"), selectedIds())
    compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
    advance(1000)
    assertFalse("tornata nella foto", exists(FluidMediaPickerTags.Preview))
    assertTrue(exists(cell("mycloud", "m002")))
  }

  @Test fun colMovimentoRidottoLAnteprimaArrivaSubito() {
    picker(cloud(), reduced = true)
    finger.down(bounds(cell("mycloud", "m001")).center)
    advance(700)
    finger.up(bounds(cell("mycloud", "m001")).center)
    advance(48)
    assertTrue(exists(FluidMediaPickerTags.Preview))
  }

  // --- Il «+» e il pannello ----------------------------------------------------------------------------------------

  @Test fun ilPiuDiMessaggiAllegaETogliedallaStriscia() {
    val phone = FluidFakeMediaSource.sample(id = FluidDeviceMediaSource.SourceId, label = "Telefono", count = 12)
    val attached = mutableStateListOf<String>()
    val visible = mutableStateOf(false)
    var camera = 0
    show {
      Box(Modifier.fillMaxSize().testTag("pagina"))
      FluidAttachmentMenu(
        visible = visible.value,
        onDismissRequest = { visible.value = false },
        source = null,
        recents = phone,
        sources = listOf(FluidAttachmentSource.camera(onClick = { camera++ }), FluidAttachmentSource.files(onClick = {})),
        attached = attached,
        onAttach = { attached += it.item!!.key },
        onRemove = { attached.remove(it) },
      )
    }
    visible.value = true
    advance(900)
    assertTrue(exists(FluidAttachmentPanelTags.Panel))
    tapTag(FluidAttachmentPanelTags.recent("device/m001"))
    tapTag(FluidAttachmentPanelTags.recent("device/m003"))
    assertEquals(listOf("device/m001", "device/m003"), attached.toList())
    tapTag(FluidAttachmentPanelTags.recent("device/m001"))
    assertEquals(listOf("device/m003"), attached.toList())
    // Una fonte: si apre e la scheda torna nel tasto.
    tapTag(FluidAttachmentPanelTags.source("camera"))
    advance(800)
    assertEquals(1, camera)
    assertFalse(visible.value)
  }

  @Test fun ilPannelloPrendeIlPostoDellaTastiera() = panelArrives(reduced = false)

  @Test fun colMovimentoRidottoIlPannelloArrivaInUnFotogramma() = panelArrives(reduced = true)

  private fun panelArrives(reduced: Boolean) {
    lateinit var panel: FluidAttachmentPanelState
    val phone = FluidFakeMediaSource.sample(id = FluidDeviceMediaSource.SourceId, label = "Telefono", count = 6)
    show(reduced) {
      panel = rememberFluidAttachmentPanelState()
      FluidAttachmentPanelHost(
        state = panel,
        panel = {
          FluidAttachmentPanel(recents = phone, sources = listOf(FluidAttachmentSource.camera(onClick = {})), attached = emptyList(), onAttach = {}, onRemove = {})
        },
      ) { Box(Modifier.fillMaxSize().testTag("schermata")) }
    }
    val full = bounds("schermata").height
    panel.open()
    advance(if (reduced) 32 else 900)
    assertTrue(exists(FluidAttachmentPanelTags.Panel))
    val reservedPx = 300f * 2f // 300 dp a densita' 2; la barra di sistema in Robolectric non c'e'
    assertEquals(full - reservedPx, bounds("schermata").height, 2f)
    assertEquals(reservedPx, bounds(FluidAttachmentPanelTags.Panel).height, 2f)
    panel.close()
    advance(if (reduced) 32 else 900)
    assertEquals(full, bounds("schermata").height, 2f)
    assertNotNull(panel)
  }

  // --- Le scene della galleria -------------------------------------------------------------------------------------

  @Test fun ogniScenaSiApre() {
    var wanted by mutableStateOf<String?>(null)
    show {
      androidx.compose.runtime.key(wanted) { NewElementsPageAt(onBack = {}, initial = wanted) }
    }
    assertEquals(listOf("selettore-foto", "foto-profilo", "accesso-foto", "allegati-piu", "allegati-tastiera"), F8p14Scenes.map { it.id })
    assertTrue(NewElementSections.any { it.pack == "F8.14" && it.scenes.size == 5 })
    val expected = mapOf(
      "selettore-foto" to FluidMediaPickerTags.Grid,
      "foto-profilo" to FluidMediaPickerTags.Grid,
      "accesso-foto" to F8p14Tags.Open,
      "allegati-piu" to F8p14Tags.Plus,
      "allegati-tastiera" to F8p14Tags.Plus,
    )
    for ((id, tag) in expected) {
      wanted = id
      advance(1_200)
      assertTrue("$id non si vede", exists(tag))
    }
    // Il «+» apre la scheda che nasce dal tasto, con le recenti finte.
    wanted = "allegati-piu"
    advance(1_200)
    tapTag(F8p14Tags.Plus)
    advance(800)
    assertTrue(exists(FluidAttachmentPanelTags.Panel))
    assertTrue(exists(FluidAttachmentPanelTags.recent("device/m000")))
  }
}
