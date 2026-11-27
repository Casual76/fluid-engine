package dev.antigravity.fluidengine.sample.novita

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.runtime.saveable.SaveableStateRegistry
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.SyncState
import dev.antigravity.fluidengine.foundation.SyncStatus
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferControls
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferDirection
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferItem
import dev.antigravity.fluidengine.foundation.transfer.FluidTransferState
import dev.antigravity.fluidengine.ui.fluid.FluidChartEntry
import dev.antigravity.fluidengine.ui.fluid.FluidColorPicker
import dev.antigravity.fluidengine.ui.fluid.FluidColorPickerMode
import dev.antigravity.fluidengine.ui.fluid.FluidColorPickerTags
import dev.antigravity.fluidengine.ui.fluid.FluidConnectionCapsule
import dev.antigravity.fluidengine.ui.fluid.FluidConnectionCapsuleTags
import dev.antigravity.fluidengine.ui.fluid.FluidDonutChart
import dev.antigravity.fluidengine.ui.fluid.FluidGauge
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeStyle
import dev.antigravity.fluidengine.ui.fluid.FluidGaugeThreshold
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidRangeSlider
import dev.antigravity.fluidengine.ui.fluid.FluidRangeSliderDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidRulerDial
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidColorHex
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.transfer.FluidTransferRow
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidContentColorCache
import dev.antigravity.fluidengine.ui.theme.FluidContentColors
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import dev.antigravity.fluidengine.ui.theme.rememberFluidContentColors
import java.lang.ref.WeakReference
import java.time.Duration
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode


/**
 * Una ricreazione come quella di una rotazione: lo stato salvabile si salva, il contenuto esce dalla composizione (un
 * fotogramma vero, non solo uno scambio fra due fotogrammi) e rientra con lo stato salvato. `StateRestorationTester`
 * con l'orologio fermo non faceva uscire niente: scambiava e rimetteva i figli prima del fotogramma.
 */
private class ControlsRecreation {
  var registry by mutableStateOf(newRegistry(null))
  var shown by mutableStateOf(true)

  private fun newRegistry(saved: Map<String, List<Any?>>?) =
    SaveableStateRegistry(saved) { it is java.io.Serializable || it is android.os.Parcelable }

  @Composable
  fun Host(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSaveableStateRegistry provides registry) { if (shown) content() }
  }

  fun recreate(frames: (Long) -> Unit) {
    val saved = registry.performSave()
    shown = false
    frames(64)
    registry = newRegistry(saved)
    shown = true
    frames(64)
  }
}

/** I comandi di una coda finta: contano le pause. */
private class CountingControls : FluidTransferControls {
  var pauses = 0
  override fun pause(id: String) { pauses++ }
  override fun resume(id: String) = Unit
  override fun retry(id: String) = Unit
  override fun force(id: String) = Unit
  override fun cancel(id: String) = Unit
  override fun clearFinished() = Unit
}

/**
 * R2-F5a · le correzioni dei controlli, dei grafici, del colore e della rete della review R2 (`muse-f8-prime.md`), in
 * Robolectric col vetro vero: il numero lungo della ciambella, l'esadecimale a 8 cifre, il segno dello spettro, il
 * cursore a due maniglie al contrario con TalkBack, la scritta della ghiera col passo fine, il colore del quadrante con
 * «Riduci movimento», la cache dei colori senza le immagini, la capsula della rete (il tetto, gli annunci, la
 * ricreazione) e la riga dei trasferimenti.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class R2F5aControlsUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: View

  @Before fun start() {
    Locale.setDefault(Locale.ITALY)
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Long) {
    var left = ms
    while (left > 0) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
      left -= 16
    }
  }

  @Composable
  private fun Harness(reduced: Boolean = false, fontScale: Float? = null, content: @Composable () -> Unit) {
    view = LocalView.current
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides if (fontScale != null) Density(density.density, fontScale) else density) {
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reduced) 0f else 1f),
          LocalFluidGlassModalHostState provides host,
        ) {
          Box(Modifier.fillMaxSize()) {
            content()
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
  }

  private fun show(reduced: Boolean = false, fontScale: Float? = null, content: @Composable () -> Unit) {
    compose.setContent { Harness(reduced, fontScale, content) }
    advance(300)
  }

  private fun nodes(matcher: SemanticsMatcher): List<SemanticsNode> =
    compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes()

  /** I pixel della finestra, col disegno vero (PixelCopy). */
  private fun snapshot(): Bitmap {
    val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().first()
    val window = activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnUiThread { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    return image
  }

  // --- 34. Il numero lungo della ciambella --------------------------------------------------------------------------

  @Test fun `il numero lungo al centro della ciambella si riduce finche' entra`() {
    show(fontScale = 2f) {
      FluidDonutChart(
        entries = listOf(FluidChartEntry("Foto", 3f), FluidChartEntry("Video", 1f)),
        centerValue = "12.345.678.901,23 €",
        centerLabel = "Totale",
        modifier = Modifier.width(200.dp),
      )
    }
    advance(1_000)
    val node = nodes(hasText("12.345.678.901,23 €")).single()
    val results = mutableListOf<TextLayoutResult>()
    node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
    val layout = results.single()
    // Il risultato della semantica impagina il paragrafo nella larghezza della colonna: un numero che non ci sta va a capo
    // oltre la sua riga (che a schermo viene tagliata), quindi il segno e' la riga in piu', non la larghezza.
    assertFalse(
      "il numero esce dal foro: righe chieste oltre la prima, largo ${layout.multiParagraph.width}, posto ${layout.size.width}",
      layout.multiParagraph.didExceedMaxLines,
    )
  }

  // --- 36. L'esadecimale a 8 cifre ----------------------------------------------------------------------------------

  @Test fun `un esadecimale a 8 cifre incollato porta anche l'opacita'`() {
    var color by mutableStateOf(Color(0xFF007AFF))
    show {
      FluidColorPicker(color = color, onColorChange = { color = it }, modifier = Modifier.fillMaxWidth(), initialMode = FluidColorPickerMode.Sliders)
    }
    val field = hasSetTextAction() and (hasTestTag(FluidColorPickerTags.Hex) or hasAnyAncestor(hasTestTag(FluidColorPickerTags.Hex)))
    compose.onNode(field).performTextReplacement("#11223380")
    advance(100)
    assertEquals("#112233", fluidColorHex(color))
    assertEquals(0x80 / 255f, color.alpha, 0.01f)
    // Sei cifre tengono l'opacita' di adesso.
    compose.onNode(field).performTextReplacement("445566")
    advance(100)
    assertEquals("#445566", fluidColorHex(color))
    assertEquals(0x80 / 255f, color.alpha, 0.01f)
  }

  @Test fun `senza opacita' l'esadecimale a 8 cifre non la cambia`() {
    var color by mutableStateOf(Color(0xFF007AFF))
    show {
      FluidColorPicker(color = color, onColorChange = { color = it }, modifier = Modifier.fillMaxWidth(),
        initialMode = FluidColorPickerMode.Sliders, supportsOpacity = false)
    }
    val field = hasSetTextAction() and (hasTestTag(FluidColorPickerTags.Hex) or hasAnyAncestor(hasTestTag(FluidColorPickerTags.Hex)))
    compose.onNode(field).performTextReplacement("11223380")
    advance(100)
    assertEquals("#112233", fluidColorHex(color))
    assertEquals(1f, color.alpha, 0.01f)
  }

  // --- Minore: il segno dello spettro -------------------------------------------------------------------------------

  @Test fun `il segno dello spettro ha il colore scelto, anche smorzato e scuro`() {
    // Un verde smorzato e scuro, come lo lasciano i cursori: tinta 120°, saturazione 0,22, valore 0,29.
    val muted = Color(0xFF3A4A3A)
    show {
      FluidColorPicker(color = muted, onColorChange = {}, modifier = Modifier.fillMaxWidth(), initialMode = FluidColorPickerMode.Spectrum)
    }
    advance(600)
    val b: Rect = compose.onNodeWithTag(FluidColorPickerTags.Spectrum).fetchSemanticsNode().boundsInRoot
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(muted.toArgb(), hsv)
    val x = b.left + hsv[0] / 360f * b.width
    val y = b.top + (0.5f + (1f - hsv[2]) * 0.5f) * b.height
    val pixel = snapshot().getPixel(x.roundToInt(), y.roundToInt())
    val r = android.graphics.Color.red(pixel)
    val g = android.graphics.Color.green(pixel)
    val bl = android.graphics.Color.blue(pixel)
    assertTrue("segno ($r, $g, $bl), colore (58, 74, 58)", abs(r - 58) <= 8 && abs(g - 74) <= 8 && abs(bl - 58) <= 8)
  }

  // --- 33. Il cursore a due maniglie al contrario con TalkBack -------------------------------------------------------

  @Test fun `con un intervallo al contrario le azioni di TalkBack non fanno cadere niente`() {
    var value by mutableStateOf(2f..8f)
    show {
      FluidRangeSlider(value = value, onValueChange = { value = it }, valueRange = 10f..0f, modifier = Modifier.fillMaxWidth().padding(16.dp))
    }
    for (name in listOf(FluidRangeSliderDefaults.StartDescription, FluidRangeSliderDefaults.EndDescription)) {
      val matcher = hasContentDescription(name)
      compose.onNode(matcher, useUnmergedTree = true).performSemanticsAction(SemanticsActions.SetProgress) { it(5f) }
      advance(100)
      val actions = nodes(matcher).single().config[SemanticsActions.CustomActions]
      for (action in actions) {
        compose.runOnUiThread { action.action() }
        advance(100)
      }
    }
    // Un intervallo al contrario vale un punto: niente si e' mosso fuori da li'.
    assertTrue("$value", value.start in 0f..10f && value.endInclusive in 0f..10f)
  }

  // --- Minore: la scritta della ghiera col passo fine ----------------------------------------------------------------

  @Test fun `col passo di un centesimo la ghiera dice i centesimi`() {
    var value by mutableStateOf(2.51f)
    show {
      FluidRulerDial(value = value, onValueChange = { value = it }, valueRange = 0f..5f, step = 0.01f, majorEvery = 10,
        neutralValue = null, contentDescription = "Esposizione", modifier = Modifier.fillMaxWidth())
    }
    advance(300)
    fun said() = nodes(hasContentDescription("Esposizione")).single().config.getOrNull(SemanticsProperties.StateDescription)
    assertEquals("2,51", said())
    value = 2.54f
    advance(800)
    assertEquals("2,54", said())
  }

  // --- Minore: il colore del quadrante con «Riduci movimento» ---------------------------------------------------------

  @Test fun `col movimento ridotto il colore della soglia si posa subito`() {
    var value by mutableStateOf(20f)
    show(reduced = true) {
      FluidGauge(
        value = value,
        range = 0f..100f,
        style = FluidGaugeStyle.Capacity,
        minLabel = "",
        maxLabel = "",
        thresholds = listOf(FluidGaugeThreshold(25f)),
        modifier = Modifier.fillMaxWidth().padding(24.dp).testTag("quadrante"),
      )
    }
    advance(600)
    val b = compose.onNodeWithTag("quadrante").fetchSemanticsNode().boundsInRoot
    // La barra: i 12 dp in fondo al quadrante (sopra ci sono l'etichetta e il numero, che col movimento ridotto sfuma).
    fun bar(image: Bitmap): IntArray {
      val top = (b.bottom - 24f).roundToInt()
      val bottom = b.bottom.roundToInt().coerceAtMost(image.height)
      val left = b.left.roundToInt()
      val width = b.width.roundToInt()
      val out = IntArray(width * (bottom - top))
      image.getPixels(out, 0, width, left, top, width, bottom - top)
      return out
    }
    val before = bar(snapshot())
    value = 80f
    Snapshot.sendApplyNotifications()
    // Il primo fotogramma in cui la barra cambia, e tre fotogrammi dopo (48 ms: a meta' della dissolvenza di 240 ms).
    var changed = false
    repeat(20) {
      if (!changed) {
        advance(16)
        changed = !bar(snapshot()).contentEquals(before)
      }
    }
    assertTrue("la barra cambia", changed)
    advance(48)
    val first = bar(snapshot())
    advance(1_000)
    val settled = bar(snapshot())
    val differing = first.indices.count { first[it] != settled[it] }
    assertEquals("pixel della barra che cambiano ancora dopo il primo fotogramma", 0, differing)
  }

  // --- Minore: la cache dei colori senza le immagini -----------------------------------------------------------------

  @Test fun `la cache dei colori non tiene in vita le immagini lette senza chiave`() {
    FluidContentColorCache.clear()
    var image: ImageBitmap? = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFE0457B.toInt()) }.asImageBitmap()
    val weak = WeakReference(image)
    var shown by mutableStateOf(true)
    var colors: FluidContentColors? = null
    show {
      if (shown) {
        val state = rememberFluidContentColors(image)
        colors = state.value
      }
    }
    // La lettura gira su Dispatchers.Default: si aspetta il tempo vero.
    val deadline = System.currentTimeMillis() + 5_000
    while (colors == null && System.currentTimeMillis() < deadline) {
      Thread.sleep(20)
      advance(32)
    }
    assertTrue("i colori sono arrivati", colors != null)
    assertEquals(1, FluidContentColorCache.size)
    shown = false
    image = null
    advance(200)
    repeat(10) {
      System.gc()
      Thread.sleep(20)
      advance(16)
    }
    assertNull("l'immagine resta nella cache", weak.get())
    FluidContentColorCache.clear()
  }

  // --- La capsula della rete (minori lasciati da R2-F3) -------------------------------------------------------------

  private var online by mutableStateOf(true)
  private var status by mutableStateOf(SyncStatus())

  @Composable
  private fun Capsule() {
    Box(Modifier.fillMaxSize()) {
      FluidConnectionCapsule(online = online, status = status, onSyncNow = {}, modifier = Modifier.align(Alignment.BottomCenter))
    }
  }

  private fun capsuleShown(): Boolean = nodes(hasTestTag(FluidConnectionCapsuleTags.Capsule)).isNotEmpty()

  @Test fun `di nuovo in linea con le modifiche che calano piano sparisce lo stesso dopo 10 s`() {
    show { Capsule() }
    status = SyncStatus(pending = 6)
    online = false
    advance(600)
    online = true
    status = SyncStatus(state = SyncState.SYNCING, pending = 6)
    advance(400)
    assertTrue(capsuleShown())
    // Una modifica mandata ogni 1,5 s, sempre sincronizzando.
    for (left in 5 downTo 0) {
      advance(1_500)
      status = SyncStatus(state = SyncState.SYNCING, pending = left)
    }
    advance(2_000)
    assertFalse("«Di nuovo in linea» oltre il tetto di 10 s", capsuleShown())
  }

  @Test fun `offline ogni modifica nuova non si riannuncia, solo il cambio di fase`() {
    val manager = RuntimeEnvironment.getApplication().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    shadowOf(manager).setEnabled(true)
    shadowOf(manager).setTouchExplorationEnabled(true)
    shadowOf(manager).setEnabledAccessibilityServiceList(listOf(AccessibilityServiceInfo()))
    show { Capsule() }
    status = SyncStatus(pending = 3)
    online = false
    advance(600)
    // TalkBack legge da se' una regione viva quando cambia il suo testo: una regione viva con un testo nuovo e' un annuncio.
    fun liveTexts(): List<String> = nodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion))
      .map { it.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ").orEmpty() }
    fun announcements() = shadowOf(manager).sentAccessibilityEvents.count { it.eventType == AccessibilityEvent.TYPE_ANNOUNCEMENT }
    val before = liveTexts()
    assertEquals("l'ingresso nella fase si annuncia", listOf("Offline · 3 modifiche in attesa"), before)
    val announcedOffline = announcements()
    for (pending in 4..5) {
      status = SyncStatus(pending = pending)
      advance(300)
      val now = liveTexts()
      assertTrue("una regione viva col testo nuovo si riannuncia: $now", now.all { it in before })
    }
    assertEquals("nessun annuncio per le modifiche", announcedOffline, announcements())
    // Toccata, la capsula dice sempre il conteggio di adesso.
    assertTrue(nodes(hasContentDescription("Offline · 5 modifiche in attesa")).isNotEmpty())
    // Il cambio di fase si annuncia: la regione viva torna, col testo della fase nuova.
    online = true
    status = SyncStatus(state = SyncState.SYNCING, pending = 5)
    advance(400)
    assertEquals("il ritorno in linea si annuncia", listOf("Di nuovo in linea · sincronizzo"), liveTexts())
  }

  @Test fun `la ricreazione tiene la frase di passaggio e il pannello aperto`() {
    val tester = ControlsRecreation()
    compose.setContent { tester.Host { Harness { Capsule() } } }
    advance(300)
    status = SyncStatus(pending = 2)
    online = false
    advance(600)
    online = true
    status = SyncStatus(state = SyncState.SYNCING, pending = 2)
    advance(400)
    assertTrue(nodes(hasContentDescription("Di nuovo in linea · sincronizzo")).isNotEmpty())
    compose.onNodeWithTag(FluidConnectionCapsuleTags.Capsule).performSemanticsAction(SemanticsActions.OnClick)
    advance(600)
    assertTrue(nodes(hasTestTag(FluidConnectionCapsuleTags.Panel)).isNotEmpty())
    tester.recreate { advance(it) }
    advance(536)
    assertTrue("la frase di passaggio", nodes(hasContentDescription("Di nuovo in linea · sincronizzo")).isNotEmpty())
    assertTrue("il pannello", nodes(hasTestTag(FluidConnectionCapsuleTags.Panel)).isNotEmpty())
    // Il tetto conta dall'inizio, non dalla ricreazione: a 10 s dal ritorno in linea la capsula se ne va.
    advance(9_600)
    assertFalse(nodes(hasContentDescription("Di nuovo in linea · sincronizzo")).isNotEmpty())
  }

  // --- La riga dei trasferimenti: un nodo, una «Pausa» ---------------------------------------------------------------

  @Test fun `la riga di un trasferimento offre Pausa a TalkBack una volta sola`() {
    val controls = CountingControls()
    val item = FluidTransferItem(
      id = "apk", title = "Pampa Notes 2.4", direction = FluidTransferDirection.Download, state = FluidTransferState.Running,
      bytesDone = 12_000_000L, bytesTotal = 48_000_000L, bytesPerSecond = 2_000_000L,
    )
    show { FluidTransferRow(item = item, controls = controls, modifier = Modifier.fillMaxWidth()) }
    advance(600)
    val all = compose.onAllNodes(SemanticsMatcher("ogni nodo") { true }, useUnmergedTree = false).fetchSemanticsNodes()
    val offering = all.filter { n ->
      val custom = n.config.getOrNull(SemanticsActions.CustomActions).orEmpty().any { it.label == "Pausa" }
      val click = n.config.getOrNull(SemanticsActions.OnClick) != null &&
        (n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text == "Pausa" } ||
          n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { it == "Pausa" } ||
          n.config.getOrNull(SemanticsActions.OnClick)?.label == "Pausa")
      custom || click
    }
    assertEquals("nodi che offrono Pausa: ${offering.map { it.config }}", 1, offering.size)
    // La pausa dall'azione della riga funziona.
    val action = offering.single().config[SemanticsActions.CustomActions].first { it.label == "Pausa" }
    compose.runOnUiThread { action.action() }
    assertEquals(1, controls.pauses)
  }
}
