package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import dev.antigravity.fluidengine.editor.FluidAdjustment
import dev.antigravity.fluidengine.editor.FluidCropAspect
import dev.antigravity.fluidengine.editor.FluidCropGeometry
import dev.antigravity.fluidengine.editor.FluidEditorMode
import dev.antigravity.fluidengine.editor.FluidEditorSource
import dev.antigravity.fluidengine.editor.FluidImageEdit
import dev.antigravity.fluidengine.editor.FluidImageEditor
import dev.antigravity.fluidengine.editor.FluidImageEditorState
import dev.antigravity.fluidengine.editor.FluidImageEditorTags
import dev.antigravity.fluidengine.editor.rememberFluidImageEditorState
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEngine
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.FluidHapticGrainStyle
import dev.antigravity.fluidengine.ui.haptics.FluidHaptics
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private class EditorHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  override fun play(event: FluidHapticEvent) {
    played += event
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) = Unit
}

/**
 * F8.15 · L'editor delle foto col dito vero (eventi del sistema, l'orologio che avanza a 16 ms come vuole
 * `MaterialFinger`), nel formato del telefono: la ruota e il righello, annulla e ripristina, l'originale tenendo premuto,
 * le maniglie, lo spostamento, il pizzico e la rotazione a due dita, le proporzioni, «Fine» e «Annulla», TalkBack, e i
 * pixel della rotazione col movimento ridotto. In Robolectric la densita' e' 2: 1 dp = 2 px.
 *
 * Il vetro e' quello di ripiego (verdetti degli shader rifiutati): le capsule dell'editor sono vetro dell'engine, ma qui
 * contano i gesti e la foto, che lo shader delle regolazioni disegna comunque.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xhdpi")
class F8p15EditorUiTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  private val haptics = EditorHaptics()
  private lateinit var view: View
  private lateinit var finger: MaterialFinger
  private lateinit var state: FluidImageEditorState
  private var done: FluidImageEdit? = null
  private var cancelled = 0
  private val out = File("build/reports/f8-15/fotogrammi").apply { mkdirs() }

  @Before
  fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.mainClock.autoAdvance = false
  }

  @After
  fun stop() {
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

  /** La foto di prova a quadranti: rosso, verde in alto; blu, bianco in basso. 400 × 300. */
  private fun quadrants(): Bitmap {
    val b = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
    val c = Canvas(b)
    val p = Paint()
    p.color = android.graphics.Color.rgb(200, 40, 40); c.drawRect(0f, 0f, 200f, 150f, p)
    p.color = android.graphics.Color.rgb(40, 200, 40); c.drawRect(200f, 0f, 400f, 150f, p)
    p.color = android.graphics.Color.rgb(40, 40, 200); c.drawRect(0f, 150f, 200f, 300f, p)
    p.color = android.graphics.Color.rgb(120, 120, 120); c.drawRect(200f, 150f, 400f, 300f, p)
    return b
  }

  private fun show(reduced: Boolean = false, dark: Boolean = false, initial: FluidImageEdit = FluidImageEdit.Original) {
    val photo = quadrants()
    compose.setContent {
      view = LocalView.current
      val mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT
      FluidTheme(settings = remember { EngineSettings(themeMode = mode, dynamicColorEnabled = false) }) {
        CompositionLocalProvider(
          LocalFluidHaptics provides haptics,
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reduced) 0f else 1f),
        ) {
          state = rememberFluidImageEditorState(FluidEditorSource.bitmap(photo), initial, previewMaxSide = 400)
          Box(Modifier.fillMaxSize()) {
            FluidImageEditor(state, onCancel = { cancelled++ }, onDone = { done = it })
          }
        }
      }
    }
    // La foto si legge su un filo vero: si aspetta in tempo vero (fino a 15 s), facendo girare l'orologio e il looper.
    val until = System.currentTimeMillis() + 15_000
    while (!state.isReady && System.currentTimeMillis() < until) {
      advance(32)
      Thread.sleep(5)
    }
    assertTrue("la foto non si e' aperta", state.isReady)
    advance(400)
    finger = MaterialFinger(view)
  }

  /** Aspetta che il gesto sia finito davvero (la ghiera chiama la fine dopo l'inerzia del lancio). */
  private fun settle(maxMs: Long = 8_000) {
    var waited = 0L
    while (state.interacting && waited < maxMs) {
      advance(64)
      waited += 64
    }
    advance(200)
  }

  private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  private fun click(tag: String) {
    compose.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
    advance(400)
  }

  private fun drag(from: Offset, to: Offset, steps: Int = 16, hold: Long = 0) {
    finger.down(from)
    advance(16)
    for (i in 1..steps) {
      finger.move(from + (to - from) * (i.toFloat() / steps))
      advance(16)
    }
    if (hold > 0) advance(hold)
    finger.up(to)
    advance(16)
  }

  /** La cornice di «Ritaglia» sullo schermo, coi conti dell'editor (margini 24 dp = 48 px). */
  private fun cropFrame(): Rect {
    val area = bounds(FluidImageEditorTags.Photo)
    val g = state.edit.geometry
    val (sw, sh) = state.sourceSize!!
    val (ow, oh) = g.orientedSize(sw, sh)
    val w = g.width * ow
    val h = g.height * oh
    val z = min((area.width - 96f) / w, (area.height - 96f) / h)
    val c = area.center
    return Rect(c.x - z * w / 2, c.y - z * h / 2, c.x + z * w / 2, c.y + z * h / 2)
  }

  /** Il ritaglio sta tutto nella foto girata (gli angoli riportati nella sorgente, con un pixel di tolleranza). */
  private fun assertInside(g: FluidCropGeometry) {
    val (sw, sh) = state.sourceSize!!
    val m = g.sourceToOutput(sw, sh).inverted()
    val (w, h) = g.outputSize(sw, sh)
    for ((x, y) in listOf(0f to 0f, w.toFloat() to 0f, 0f to h.toFloat(), w.toFloat() to h.toFloat())) {
      val sx = m.mapX(x, y)
      val sy = m.mapY(x, y)
      assertTrue("angolo fuori: $sx,$sy di $sw x $sh ($g)", sx >= -1.5f && sx <= sw + 1.5f && sy >= -1.5f && sy <= sh + 1.5f)
    }
  }

  @Test
  fun ilRighelloColDitoCambiaLEsposizioneEAnnullaERipristinaUnPassoPerGesto() {
    show()
    val ruler = bounds(FluidImageEditorTags.Ruler)
    val start = Offset(ruler.center.x, ruler.center.y)
    drag(start, start - Offset(220f, 0f), steps = 20)
    settle()
    val value = state.edit.adjustments[FluidAdjustment.Exposure]
    assertTrue("esposizione $value", abs(value) >= 5f)
    assertFalse("il gesto e' finito", state.interacting)
    assertTrue(state.canUndo)
    click(FluidImageEditorTags.Undo)
    assertEquals(0f, state.edit.adjustments[FluidAdjustment.Exposure])
    assertTrue(state.canRedo)
    click(FluidImageEditorTags.Redo)
    assertEquals(value, state.edit.adjustments[FluidAdjustment.Exposure])
    // Un gesto e' un passo solo: un annulla torna all'inizio, non a meta' del righello.
    click(FluidImageEditorTags.Undo)
    assertFalse(state.canUndo)
  }

  @Test
  fun laRuotaDelleRegolazioniScorreColDitoEDiceIlValoreATalkBack() {
    show()
    assertEquals(FluidAdjustment.Exposure, state.selected)
    val strip = bounds(FluidImageEditorTags.Strip)
    val start = Offset(strip.center.x + 100f, strip.center.y)
    drag(start, start - Offset(276f, 0f), steps = 18)
    advance(900)
    val now = state.selected
    assertTrue("scelta $now", now != null && now != FluidAdjustment.Exposure)
    assertTrue("un tic per voce", haptics.played.count { it == FluidHapticEvent.Tick } >= 1)
    // TalkBack: un tocco su «Contrasto» la porta al centro; il righello si regola con «imposta» e fa un passo.
    val contrast = compose.onNodeWithTag(FluidImageEditorTags.item(FluidAdjustment.Contrast.id), useUnmergedTree = true)
    assertEquals("Contrasto", contrast.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)?.single())
    contrast.performSemanticsAction(SemanticsActions.OnClick)
    advance(800)
    assertEquals(FluidAdjustment.Contrast, state.selected)
    compose.onNodeWithTag(FluidImageEditorTags.Ruler, useUnmergedTree = true)
      .performSemanticsAction(SemanticsActions.SetProgress) { it(40f) }
    advance(300)
    assertEquals(40f, state.edit.adjustments[FluidAdjustment.Contrast])
    assertTrue(state.canUndo)
    assertFalse(state.interacting)
    val node = compose.onNodeWithTag(FluidImageEditorTags.item(FluidAdjustment.Contrast.id), useUnmergedTree = true).fetchSemanticsNode()
    assertEquals("40", node.config.getOrNull(SemanticsProperties.StateDescription))
    assertEquals(true, node.config.getOrNull(SemanticsProperties.Selected))
  }

  @Test
  fun autoSiAccendeESiSpegneLasciandoLeModificheAMano() {
    show()
    compose.runOnIdle {
      state.setAdjustment(FluidAdjustment.Exposure, 12f)
      state.commit()
    }
    click(FluidImageEditorTags.item("auto"))
    assertTrue(state.edit.isAuto)
    click(FluidImageEditorTags.item("auto"))
    assertFalse(state.edit.isAuto)
    assertEquals(12f, state.edit.adjustments[FluidAdjustment.Exposure])
  }

  @Test
  fun tenendoPremutaLaFotoSiVedeLOriginale() {
    show()
    compose.runOnIdle {
      state.setAdjustment(FluidAdjustment.Exposure, 60f)
      state.commit()
    }
    advance(200)
    compose.onNodeWithText("ESPOSIZIONE", useUnmergedTree = true).assertExists()
    val photo = bounds(FluidImageEditorTags.Photo)
    finger.down(photo.center)
    advance(48)
    assertFalse("un tocco veloce non mostra l'originale", state.showingOriginal)
    advance(160)
    assertTrue(state.showingOriginal)
    advance(500)
    assertTrue(compose.onAllNodesWithText("ORIGINALE", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
    finger.up(photo.center)
    advance(400)
    assertFalse(state.showingOriginal)
    // La ricetta non e' cambiata: l'originale si guarda, non si applica.
    assertEquals(60f, state.edit.adjustments[FluidAdjustment.Exposure])
    // TalkBack ha l'azione per vedere l'originale.
    val photoNode = compose.onNode(hasContentDescription("Foto"), useUnmergedTree = true).fetchSemanticsNode()
    val action = photoNode.config.getOrNull(SemanticsActions.CustomActions)!!.single { it.label == "Originale" }
    compose.runOnIdle { action.action() }
    advance(100)
    assertTrue(state.showingOriginal)
  }

  @Test
  fun ilRitaglioConLeManiglieLoSpostamentoIlPizzicoELaRotazioneADueDita() {
    show()
    click(FluidImageEditorTags.ModeCrop)
    advance(600)
    assertEquals(FluidEditorMode.Crop, state.mode)
    // L'angolo in alto a sinistra, tirato dentro.
    val frame = cropFrame()
    drag(Offset(frame.left + 2f, frame.top + 2f), Offset(frame.left + 120f, frame.top + 80f))
    advance(1200)
    val g = state.edit.geometry
    assertTrue("ritaglio $g", g.width < 0.9f && g.height < 0.9f)
    assertInside(g)
    assertTrue(state.canUndo)
    // Trascinato oltre il bordo, si ferma sul bordo e non esce.
    val f2 = cropFrame()
    drag(Offset(f2.left + 2f, f2.top + 2f), Offset(f2.left - 400f, f2.top - 400f))
    advance(1200)
    assertInside(state.edit.geometry)
    // Due dita che si allargano: la foto si ingrandisce, il ritaglio si stringe.
    val w0 = state.edit.geometry.width
    val c = cropFrame().center
    finger.downAll(listOf(c - Offset(60f, 0f), c + Offset(60f, 0f)))
    advance(16)
    for (i in 1..12) {
      val d = 60f + i * 10f
      finger.moveAll(listOf(c - Offset(d, 0f), c + Offset(d, 0f)))
      advance(16)
    }
    finger.upAll(listOf(c - Offset(180f, 0f), c + Offset(180f, 0f)))
    advance(400)
    assertTrue("pizzico ${state.edit.geometry.width} da $w0", state.edit.geometry.width < w0 - 0.05f)
    assertInside(state.edit.geometry)
    // La foto spostata sotto la cornice ferma: il ritaglio va dall'altra parte.
    val before = state.edit.geometry.centerX
    val center = cropFrame().center
    drag(center, center + Offset(120f, 0f))
    advance(400)
    assertTrue("centro ${state.edit.geometry.centerX} da $before", state.edit.geometry.centerX < before - 0.01f)
    assertInside(state.edit.geometry)
    // Due dita che girano: il raddrizzamento, sempre dentro la foto.
    val r = 100f
    finger.downAll(listOf(c - Offset(r, 0f), c + Offset(r, 0f)))
    advance(16)
    for (i in 1..10) {
      val a = Math.toRadians(i * 1.5)
      val dx = (r * cos(a)).toFloat()
      val dy = (r * sin(a)).toFloat()
      finger.moveAll(listOf(c - Offset(dx, dy), c + Offset(dx, dy)))
      advance(16)
    }
    finger.upAll(listOf(c - Offset(r, 0f), c + Offset(r, 0f)))
    advance(400)
    assertTrue("angolo ${state.edit.geometry.angle}", abs(state.edit.geometry.angle) > 5f)
    assertInside(state.edit.geometry)
  }

  @Test
  fun ruotaSpecchiaRaddrizzaProporzioniEReimposta() {
    show()
    click(FluidImageEditorTags.ModeCrop)
    click(FluidImageEditorTags.Rotate)
    assertEquals(1, state.edit.geometry.quarterTurns)
    click(FluidImageEditorTags.Flip)
    assertTrue(state.edit.geometry.flipped)
    val ruler = bounds(FluidImageEditorTags.Straighten)
    drag(ruler.center, ruler.center - Offset(120f, 0f))
    settle()
    assertNotEquals(0f, state.edit.geometry.angle)
    assertInside(state.edit.geometry)
    click(FluidImageEditorTags.Aspect)
    assertTrue(state.aspectPanel)
    click(FluidImageEditorTags.aspect(FluidCropAspect.Square.id))
    val (sw, sh) = state.sourceSize!!
    assertEquals(1f, state.edit.geometry.outputRatio(sw, sh), 0.01f)
    assertInside(state.edit.geometry)
    click(FluidImageEditorTags.Reset)
    assertTrue(state.edit.geometry.isIdentity)
  }

  @Test
  fun fineDaLaRicettaEAnnullaChiedeConfermaSeCeQualcosaDaPerdere() {
    show()
    // Senza modifiche «Annulla» esce subito.
    click(FluidImageEditorTags.Cancel)
    assertEquals(1, cancelled)
    compose.runOnIdle {
      state.setAdjustment(FluidAdjustment.Warmth, 30f)
      state.commit()
    }
    click(FluidImageEditorTags.Done)
    assertEquals(30f, done!!.adjustments[FluidAdjustment.Warmth])
    click(FluidImageEditorTags.Cancel)
    advance(600)
    assertEquals(1, cancelled)
    // Il tasto del dialogo (l'albero unito: il testo sta dentro il tasto che si tocca).
    compose.onNode(hasText("Annulla modifiche") and hasClickAction()).performSemanticsAction(SemanticsActions.OnClick)
    advance(600)
    assertEquals(2, cancelled)
  }

  /**
   * F8.15b, dal tablet: nel formato del telefono gli angoli della cornice stanno nella fascia da cui Android fa partire
   * «indietro». In «Ritaglia» i quattro angoli sono esclusi dai gesti di sistema; in «Regola» niente.
   */
  @Test
  fun gliAngoliDellaCorniceNonSonoDelGestoIndietro() {
    show()
    assertTrue(view.systemGestureExclusionRects.isEmpty())
    click(FluidImageEditorTags.ModeCrop)
    advance(800)
    val rects = view.systemGestureExclusionRects
    assertEquals(4, rects.size)
    val f = cropFrame()
    for ((x, y) in listOf(f.left to f.top, f.right to f.top, f.left to f.bottom, f.right to f.bottom)) {
      assertTrue("angolo $x,$y fuori da $rects", rects.any { it.contains(x.toInt(), y.toInt()) })
    }
    click(FluidImageEditorTags.ModeAdjust)
    advance(800)
    assertTrue(view.systemGestureExclusionRects.isEmpty())
  }

  private fun window(): Bitmap {
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(compose.activity.window, android.graphics.Rect(0, 0, view.width, view.height),
        bitmap, { assertEquals(PixelCopy.SUCCESS, it) }, Handler(Looper.getMainLooper()))
    }
    shadowOf(Looper.getMainLooper()).idle()
    return bitmap
  }

  private fun isGreen(c: Int) = android.graphics.Color.green(c) > 150 && android.graphics.Color.red(c) < 90

  private fun rotation(reduced: Boolean) {
    show(reduced = reduced)
    click(FluidImageEditorTags.ModeCrop)
    advance(600)
    compose.onNodeWithTag(FluidImageEditorTags.Rotate, useUnmergedTree = true).performSemanticsAction(SemanticsActions.OnClick)
    advance(32)
    val frame = cropFrame()
    val probe = Offset(frame.left + frame.width * 0.25f, frame.top + frame.height * 0.25f)
    val image = window()
    image.compress(Bitmap.CompressFormat.PNG, 100, File(out, "ruota-${if (reduced) "ridotto" else "pieno"}.png").outputStream())
    val c = image.getPixel(probe.x.toInt(), probe.y.toInt())
    if (reduced) {
      assertTrue("col movimento ridotto il verde e' gia' in alto a sinistra: ${Integer.toHexString(c)}", isGreen(c))
    } else {
      assertFalse("senza movimento ridotto la foto sta ancora girando: ${Integer.toHexString(c)}", isGreen(c))
    }
    advance(800)
    val settled = window().getPixel(probe.x.toInt(), probe.y.toInt())
    assertTrue("a fine giro il verde e' in alto a sinistra: ${Integer.toHexString(settled)}", isGreen(settled))
  }

  @Test
  fun laRotazioneColMovimentoRidottoESubitoAlSuoPosto() = rotation(reduced = true)

  @Test
  fun laRotazioneSenzaMovimentoRidottoGiraDaQuellaDiPrima() = rotation(reduced = false)

  @Test
  fun laFotoSiSchiarisceMentreIlDitoMuoveIlRighello() {
    show(dark = true)
    val photo = bounds(FluidImageEditorTags.Photo)
    // Il quadrante grigio (120) in basso a destra della foto adatta allo spazio.
    val (sw, sh) = state.sourceSize!!
    val z = min(photo.width / sw, photo.height / sh)
    val probe = Offset(photo.center.x + z * sw / 4, photo.center.y + z * sh / 4)
    val before = android.graphics.Color.red(window().getPixel(probe.x.toInt(), probe.y.toInt()))
    val ruler = bounds(FluidImageEditorTags.Ruler)
    finger.down(ruler.center)
    advance(16)
    for (i in 1..30) {
      finger.move(ruler.center - Offset(i * 14f, 0f))
      advance(16)
    }
    // Il dito e' ancora giu': la foto e' gia' cambiata.
    assertTrue(state.interacting)
    val value = state.edit.adjustments[FluidAdjustment.Exposure]
    val during = window()
    during.compress(Bitmap.CompressFormat.PNG, 100, File(out, "esposizione-dito.png").outputStream())
    val now = android.graphics.Color.red(during.getPixel(probe.x.toInt(), probe.y.toInt()))
    finger.up(ruler.center - Offset(420f, 0f))
    advance(800)
    if (value > 0f) assertTrue("grigio $before -> $now con $value", now > before + 8)
    else assertTrue("grigio $before -> $now con $value", now < before - 8)
  }
}
