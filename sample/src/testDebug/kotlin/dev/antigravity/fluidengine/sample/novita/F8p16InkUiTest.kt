package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ink.FluidInkEditor
import dev.antigravity.fluidengine.ink.FluidInkEditorTags
import dev.antigravity.fluidengine.ink.FluidInkEraserMode
import dev.antigravity.fluidengine.ink.FluidInkInput
import dev.antigravity.fluidengine.ink.FluidInkPaletteTags
import dev.antigravity.fluidengine.ink.FluidInkShapeKind
import dev.antigravity.fluidengine.ink.FluidInkSheet
import dev.antigravity.fluidengine.ink.FluidInkState
import dev.antigravity.fluidengine.ink.FluidInkTags
import dev.antigravity.fluidengine.ink.FluidInkTool
import dev.antigravity.fluidengine.ink.ready
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
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
 * Una penna finta: eventi veri dello stilo (`TOOL_TYPE_STYLUS`, sorgente stilo), con pressione, inclinazione, il tasto
 * laterale e l'altra estremita' (la gomma, `TOOL_TYPE_ERASER`), consegnati alla vista della composizione come li consegna
 * il sistema. Il dito vero e' [MaterialFinger].
 */
private class FakeStylus(private val view: View) {
  private var downTime = 0L

  fun down(at: Offset, pressure: Float = 0.5f, tilt: Float = 0.6f, eraser: Boolean = false, button: Boolean = false) {
    downTime = SystemClock.uptimeMillis()
    send(MotionEvent.ACTION_DOWN, at, pressure, tilt, eraser, button)
  }

  fun move(to: Offset, pressure: Float = 0.6f, tilt: Float = 0.6f, eraser: Boolean = false, button: Boolean = false) =
    send(MotionEvent.ACTION_MOVE, to, pressure, tilt, eraser, button)

  fun up(at: Offset, eraser: Boolean = false, button: Boolean = false) = send(MotionEvent.ACTION_UP, at, 0.4f, 0.6f, eraser, button)

  private fun send(action: Int, at: Offset, pressure: Float, tilt: Float, eraser: Boolean, button: Boolean) {
    val properties = arrayOf(MotionEvent.PointerProperties().apply {
      id = 0
      toolType = if (eraser) MotionEvent.TOOL_TYPE_ERASER else MotionEvent.TOOL_TYPE_STYLUS
    })
    val coords = arrayOf(MotionEvent.PointerCoords().apply {
      x = at.x
      y = at.y
      this.pressure = pressure
      size = 0.01f
      orientation = 0.3f
      setAxisValue(MotionEvent.AXIS_TILT, tilt)
    })
    val event = MotionEvent.obtain(
      downTime, SystemClock.uptimeMillis(), action, 1, properties, coords, 0,
      if (button) MotionEvent.BUTTON_STYLUS_PRIMARY else 0, 1f, 1f, 0, 0,
      InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN, 0,
    )
    try {
      view.dispatchTouchEvent(event)
    } finally {
      event.recycle()
    }
  }
}

/**
 * F8.16 · Il disegno col dito vero e con la penna finta (il tipo di strumento), col vetro vero (verdetti degli shader
 * accettati): il tratto del dito, la penna che prende il posto del dito e il dito che scorre la pagina, la pressione e
 * l'inclinazione, la gomma (dalla tavolozza, dall'estremita' della penna, col tasto laterale), il lazo che sceglie e
 * sposta, le forme tenendo fermo, due dita che ingrandiscono, annulla e ripristina dai tasti, TalkBack e «Riduci
 * movimento». Sul computer androidx.ink non ha la libreria nativa: disegna il ripiego, col modello di sempre.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h800dp-xhdpi")
class F8p16InkUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var view: View
  private lateinit var finger: MaterialFinger
  private lateinit var stylus: FakeStylus
  private lateinit var state: FluidInkState

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

  private fun show(reduced: Boolean = false, sheet: FluidInkSheet = FluidInkSheet.blank(), content: (@Composable () -> Unit)? = null) {
    state = FluidInkState()
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reduced) 0f else 1f),
          LocalFluidGlassModalHostState provides host,
        ) {
          Box(Modifier.fillMaxSize()) {
            if (content != null) content() else FluidInkEditor(state, sheet.ready(), onCancel = {}, onDone = {})
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
    advance(800)
    finger = MaterialFinger(view)
    stylus = FakeStylus(view)
  }

  private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

  private fun exists(tag: String) = compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

  private fun tap(tag: String) {
    val c = bounds(tag).center
    finger.down(c)
    advance(48)
    finger.up(c)
    advance(400)
  }

  /** Il centro del foglio, lontano dalle barre. */
  private fun middle(): Offset = bounds(FluidInkTags.Canvas).center

  private fun fingerLine(from: Offset, to: Offset, steps: Int = 12, stepMs: Long = 16) {
    finger.down(from)
    advance(stepMs)
    for (i in 1..steps) {
      finger.move(from + (to - from) * (i / steps.toFloat()))
      advance(stepMs)
    }
    finger.up(to)
    advance(200)
  }

  private fun stylusLine(from: Offset, to: Offset, steps: Int = 12, eraser: Boolean = false, button: Boolean = false) {
    stylus.down(from, eraser = eraser, button = button)
    advance(16)
    for (i in 1..steps) {
      stylus.move(from + (to - from) * (i / steps.toFloat()), pressure = 0.3f + 0.05f * i, eraser = eraser, button = button)
      advance(16)
    }
    stylus.up(to, eraser = eraser, button = button)
    advance(200)
  }

  @Test fun theFingerDrawsAStrokeUnderItself() {
    show()
    val c = middle()
    fingerLine(c - Offset(150f, 0f), c + Offset(150f, 0f))
    assertEquals(1, state.strokes.size)
    val s = state.strokes.single()
    assertEquals(FluidInkTool.Pen, s.tool)
    assertEquals(FluidInkInput.Touch, s.input)
    // Dal dito niente pressione (come PencilKit), e i punti sono quelli del dito, nel foglio (dp: la densita' e' 2).
    assertFalse(s.points.hasPressure)
    assertTrue(s.points.size >= 12)
    assertEquals(150f, s.points.bounds.width, 4f)
  }

  @Test fun theStylusTakesOverAndThenTheFingerMovesThePage() {
    show()
    val c = middle()
    stylusLine(c - Offset(100f, 60f), c + Offset(100f, 60f))
    assertEquals(1, state.strokes.size)
    val s = state.strokes.single()
    assertEquals(FluidInkInput.Stylus, s.input)
    assertTrue(s.points.hasPressure && s.points.hasTilt && s.points.hasOrientation)
    assertTrue(s.points.pressure(s.points.size - 2) > s.points.pressure(1))
    assertTrue(state.stylusSeen)
    assertFalse(state.fingerDraws)
    // Due dita ingrandiscono...
    finger.downAll(listOf(c - Offset(80f, 0f), c + Offset(80f, 0f)))
    advance(32)
    for (i in 1..8) {
      finger.moveAll(listOf(c - Offset(80f + i * 20f, 0f), c + Offset(80f + i * 20f, 0f)))
      advance(16)
    }
    finger.upAll(listOf(c - Offset(240f, 0f), c + Offset(240f, 0f)))
    advance(300)
    assertTrue("zoom ${state.zoom}", state.zoom > 1.5f)
    // ...e un dito solo, con la penna gia' vista, scorre: niente tratto nuovo, la pagina si sposta.
    val before = state.pan
    fingerLine(c, c + Offset(0f, 200f), steps = 10, stepMs = 32)
    assertEquals(1, state.strokes.size)
    assertNotEquals(before, state.pan)
  }

  @Test fun theEraserEndOfThePenAndItsSideButtonErase() {
    show()
    // La gomma per oggetti (quella di serie e' a pixel, come in Markup): toglie i tratti interi.
    compose.runOnIdle { state.eraserMode = FluidInkEraserMode.Object }
    val c = middle()
    stylusLine(c - Offset(100f, 0f), c + Offset(100f, 0f))
    stylusLine(c + Offset(-100f, 200f), c + Offset(100f, 200f))
    assertEquals(2, state.strokes.size)
    // L'altra estremita' della penna cancella il primo tratto, senza cambiare strumento.
    stylusLine(c - Offset(0f, 40f), c + Offset(0f, 40f), eraser = true)
    assertEquals(1, state.strokes.size)
    assertEquals(FluidInkTool.Pen, state.tool)
    // Il tasto laterale tenuto premuto (la S Pen) cancella il secondo.
    stylusLine(c + Offset(0f, 160f), c + Offset(0f, 240f), button = true)
    assertTrue(state.isEmpty)
    // Due gesti, due passi di annulla.
    compose.runOnIdle { state.undo() }
    assertEquals(1, state.strokes.size)
  }

  @Test fun thePixelEraserFromThePaletteCutsTheStroke() {
    show()
    val c = middle()
    fingerLine(c - Offset(200f, 0f), c + Offset(200f, 0f))
    tap(FluidInkPaletteTags.tool(FluidInkTool.Eraser))
    assertEquals(FluidInkTool.Eraser, state.tool)
    // Toccata di nuovo, la gomma apre le sue opzioni: a pixel.
    tap(FluidInkPaletteTags.tool(FluidInkTool.Eraser))
    advance(600)
    assertTrue(exists(FluidInkPaletteTags.Options))
    // Di serie e' gia' a pixel, come in Markup di iOS 26.
    assertEquals(FluidInkEraserMode.Area, state.eraserMode)
    compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
    advance(600)
    fingerLine(c - Offset(0f, 60f), c + Offset(0f, 60f))
    assertEquals(2, state.strokes.size)
  }

  @Test fun theLassoSelectsMovesAndDeletes() {
    show()
    val c = middle()
    fingerLine(c - Offset(60f, 0f), c + Offset(60f, 0f))
    fingerLine(c + Offset(-60f, 300f), c + Offset(60f, 300f))
    tap(FluidInkPaletteTags.tool(FluidInkTool.Lasso))
    // Un giro attorno al primo tratto.
    finger.down(c + Offset(-120f, -80f))
    advance(16)
    val loop = 24
    for (i in 1..loop) {
      val a = -PI / 2 + i * 2 * PI / loop
      finger.move(c + Offset((140 * cos(a)).toFloat(), (90 * sin(a)).toFloat()))
      advance(16)
    }
    finger.up(c + Offset(0f, -90f))
    advance(400)
    assertEquals(1, state.selection.size)
    val chosen = state.selection.single()
    assertEquals(state.strokes.first().id, chosen)
    assertTrue(exists(FluidInkTags.SelectionMenu))
    // Trascinata dentro il riquadro, la selezione si sposta (un passo di annulla).
    val before = state.strokes.first { it.id == chosen }.points.x(0)
    fingerLine(c, c + Offset(100f, 0f), steps = 8)
    val after = state.strokes.first { it.id == chosen }.points.x(0)
    assertEquals(50f, after - before, 3f)
    tap(FluidInkTags.Delete)
    assertEquals(1, state.strokes.size)
    tap(FluidInkEditorTags.Undo)
    assertEquals(2, state.strokes.size)
    tap(FluidInkEditorTags.Redo)
    assertEquals(1, state.strokes.size)
  }

  @Test fun holdingStillAtTheEndMakesTheShape() {
    show()
    val c = middle()
    finger.down(c + Offset(150f, 0f))
    advance(16)
    val steps = 40
    for (i in 1..steps) {
      val a = i * 2 * PI / steps * 1.03
      finger.move(c + Offset((150 * cos(a)).toFloat() + if (i % 2 == 0) 2f else -2f, (150 * sin(a)).toFloat()))
      advance(16)
    }
    // Fermo per piu' di mezzo secondo, poi su.
    advance(700)
    finger.up(c + Offset((150 * cos(2 * PI * 1.03)).toFloat(), (150 * sin(2 * PI * 1.03)).toFloat()))
    advance(300)
    assertEquals(1, state.strokes.size)
    assertEquals(FluidInkShapeKind.Circle, state.strokes.single().shape)
  }

  @Test fun aQuickSecondFingerIsAPinchNotAStroke() {
    show()
    val c = middle()
    finger.downAll(listOf(c - Offset(60f, 0f), c + Offset(60f, 0f)))
    advance(16)
    for (i in 1..8) {
      finger.moveAll(listOf(c - Offset(60f + i * 25f, 0f), c + Offset(60f + i * 25f, 0f)))
      advance(16)
    }
    finger.upAll(listOf(c - Offset(260f, 0f), c + Offset(260f, 0f)))
    advance(300)
    assertTrue(state.isEmpty)
    assertTrue(state.zoom > 1.5f)
  }

  @Test fun talkBackHearsTheToolsAndTheSheet() {
    show()
    val c = middle()
    fingerLine(c - Offset(80f, 0f), c + Offset(80f, 0f))
    val pen = compose.onNodeWithTag(FluidInkPaletteTags.tool(FluidInkTool.Pen)).fetchSemanticsNode().config
    assertEquals("Penna", pen.getOrNull(SemanticsProperties.ContentDescription)?.single())
    assertEquals(true, pen.getOrNull(SemanticsProperties.Selected))
    val pencil = compose.onNodeWithTag(FluidInkPaletteTags.tool(FluidInkTool.Pencil)).fetchSemanticsNode().config
    assertEquals(false, pencil.getOrNull(SemanticsProperties.Selected))
    val ruler = compose.onNodeWithTag(FluidInkPaletteTags.Ruler).fetchSemanticsNode().config
    assertNotNull(ruler.getOrNull(SemanticsProperties.ToggleableState))
    val sheet = compose.onNodeWithTag(FluidInkTags.Canvas).fetchSemanticsNode().config
    assertEquals("Foglio da disegno, un tratto", sheet.getOrNull(SemanticsProperties.ContentDescription)?.single())
    // Annulla anche senza vedere i tasti: l'azione del foglio.
    val undo = sheet.getOrNull(SemanticsActions.CustomActions)?.firstOrNull { it.label == "Annulla" }
    assertNotNull(undo)
    compose.runOnIdle { undo!!.action() }
    assertTrue(state.isEmpty)
  }

  /** Ingrandisce con due dita, poi lancia la pagina con un dito (la penna gia' vista): dove sta la pagina al rilascio e dopo. */
  private fun flingAfterZoom(): Pair<Offset, Offset> {
    val c = middle()
    compose.runOnIdle { state.drawingPolicy = dev.antigravity.fluidengine.ink.FluidInkDrawingPolicy.StylusOnly }
    finger.downAll(listOf(c - Offset(60f, 0f), c + Offset(60f, 0f)))
    advance(16)
    for (i in 1..8) {
      finger.moveAll(listOf(c - Offset(60f + i * 25f, 0f), c + Offset(60f + i * 25f, 0f)))
      advance(16)
    }
    finger.upAll(listOf(c - Offset(260f, 0f), c + Offset(260f, 0f)))
    advance(300)
    finger.down(c)
    advance(8)
    for (i in 1..6) {
      finger.move(c + Offset(0f, i * 40f))
      advance(8)
    }
    finger.up(c + Offset(0f, 240f))
    val atRelease = state.pan
    advance(600)
    return atRelease to state.pan
  }

  @Test fun aFlungPageGlides() {
    show(reduced = false)
    val (atRelease, after) = flingAfterZoom()
    assertNotEquals("la pagina lanciata scivola", atRelease, after)
  }

  @Test fun reducedMotionStopsThePageWhereTheFingerLeftIt() {
    show(reduced = true)
    val (atRelease, after) = flingAfterZoom()
    assertEquals(atRelease, after)
  }

  @Test fun reducedMotionStillMakesTheShape() {
    show(reduced = true)
    val c = middle()
    finger.down(c + Offset(120f, -120f))
    advance(16)
    val corners = listOf(Offset(120f, 120f), Offset(-120f, 120f), Offset(-120f, -120f), Offset(120f, -120f))
    var from = Offset(120f, -120f)
    for (to in corners) {
      for (k in 1..8) {
        finger.move(c + from + (to - from) * (k / 8f))
        advance(16)
      }
      from = to
    }
    advance(700)
    finger.up(c + from)
    advance(100)
    assertEquals(FluidInkShapeKind.Square, state.strokes.single().shape)
  }

  @Test fun thePhotoSceneDeliversANewAnnotatedFile() {
    show(content = { NewElementsPageAt(onBack = {}, initial = "annota-foto") })
    tap(F8p16Tags.PhotoLight)
    advance(800)
    val c = middle()
    fingerLine(c - Offset(120f, 0f), c + Offset(120f, 0f))
    tap(FluidInkEditorTags.Done)
    // La consegna gira fuori dal thread principale: si aspetta il risultato.
    var tries = 0
    while (!exists(F8p16Tags.Result) && tries++ < 200) {
      Thread.sleep(25)
      advance(32)
    }
    assertTrue(exists(F8p16Tags.ResultImage))
    val file = java.io.File(compose.activity.cacheDir, "f8p16/foto-annotata.jpg")
    assertTrue(file.length() > 0)
    val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
    android.graphics.BitmapFactory.decodeFile(file.path, bounds)
    val original = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
    android.graphics.BitmapFactory.decodeResource(compose.activity.resources, dev.antigravity.fluidengine.sample.R.drawable.viewer_flower, original)
    // La copia annotata ha la misura della foto intera, non quella dello schermo.
    assertEquals(original.outWidth, bounds.outWidth)
    assertEquals(original.outHeight, bounds.outHeight)
  }

  /** Il palmo (un dito, id 0) e la penna (id 1) nello stesso gesto, come li consegna il sistema. */
  private fun palmAndPen(action: Int, palm: Offset, pen: Offset?, downTime: Long) {
    val count = if (pen == null) 1 else 2
    val properties = Array(count) { i ->
      MotionEvent.PointerProperties().apply {
        id = i
        toolType = if (i == 0) MotionEvent.TOOL_TYPE_FINGER else MotionEvent.TOOL_TYPE_STYLUS
      }
    }
    val coords = Array(count) { i ->
      MotionEvent.PointerCoords().apply {
        val at = if (i == 0) palm else pen!!
        x = at.x; y = at.y
        pressure = if (i == 0) 1f else 0.6f
        size = if (i == 0) 0.3f else 0.01f
        if (i == 1) setAxisValue(MotionEvent.AXIS_TILT, 0.5f)
      }
    }
    val event = MotionEvent.obtain(
      downTime, SystemClock.uptimeMillis(), action, count, properties, coords, 0, 0, 1f, 1f, 0, 0,
      InputDevice.SOURCE_TOUCHSCREEN or InputDevice.SOURCE_STYLUS, 0,
    )
    try {
      view.dispatchTouchEvent(event)
    } finally {
      event.recycle()
    }
  }

  @Test fun aPalmDownFirstLetsThePenDraw() {
    show()
    val c = middle()
    val palm = c + Offset(120f, 260f)
    val down = SystemClock.uptimeMillis()
    // Il palmo si appoggia e scivola un po' (prima della penna: col dito che disegna, comincerebbe un tratto)...
    palmAndPen(MotionEvent.ACTION_DOWN, palm, null, down)
    advance(16)
    palmAndPen(MotionEvent.ACTION_MOVE, palm + Offset(4f, 0f), null, down)
    advance(16)
    // ...poi la punta: disegna lei, e il palmo non lascia niente.
    val pointerDown = MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
    palmAndPen(pointerDown, palm + Offset(4f, 0f), c - Offset(100f, 0f), down)
    advance(16)
    for (i in 1..10) {
      palmAndPen(MotionEvent.ACTION_MOVE, palm + Offset(4f + i, 0f), c - Offset(100f, 0f) + Offset(i * 20f, 0f), down)
      advance(16)
    }
    val pointerUp = MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)
    palmAndPen(pointerUp, palm + Offset(14f, 0f), c + Offset(100f, 0f), down)
    advance(16)
    palmAndPen(MotionEvent.ACTION_UP, palm + Offset(14f, 0f), null, down)
    advance(300)
    assertEquals(1, state.strokes.size)
    assertEquals(FluidInkInput.Stylus, state.strokes.single().input)
    assertTrue(state.stylusSeen)
  }

  /** La finestra in una bitmap, coi pixel veri del foglio (PixelCopy, come le catture). */
  private fun windowPixels(): android.graphics.Bitmap {
    val window = compose.activity.window
    val decor = window.decorView
    val image = android.graphics.Bitmap.createBitmap(decor.width, decor.height, android.graphics.Bitmap.Config.ARGB_8888)
    var result = -1
    android.view.PixelCopy.request(window, image, { result = it }, android.os.Handler(Looper.getMainLooper()))
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", android.view.PixelCopy.SUCCESS, result)
    return image
  }

  /**
   * F8.16c · Un tratto fatto col dito vero (eventi in pixel) su un foglio a densita' 2,625 (un telefono a 420 dpi, come il
   * formato telefono del tablet) cade sotto il dito: il centro della riga d'inchiostro sta entro 1 px dalla riga del dito,
   * e il tratto comincia e finisce dove il dito comincia e finisce. Sul Tab S9 i tratti finiti uscivano piccoli di 2,625
   * volte (la matrice non messa sul canvas): questa prova guarda la catena intera evento → foglio → schermo.
   */
  @Config(qualifiers = "w411dp-h891dp-420dpi")
  @Test fun aStrokeLandsUnderTheFingerAt420dpi() {
    show()
    val c = middle()
    val y = kotlin.math.round(c.y)
    val from = Offset(kotlin.math.round(c.x - 300f), y)
    val to = Offset(kotlin.math.round(c.x + 300f), y)
    fingerLine(from, to, steps = 24)
    advance(600)
    assertEquals(1, state.strokes.size)
    val image = windowPixels()
    val origin = IntArray(2).also { view.getLocationInWindow(it) }
    fun dark(x: Int, y: Int): Boolean {
      val p = image.getPixel(x + origin[0], y + origin[1])
      return android.graphics.Color.red(p) < 100 && android.graphics.Color.green(p) < 100 && android.graphics.Color.blue(p) < 100
    }
    val column = c.x.toInt()
    val rows = (y.toInt() - 40..y.toInt() + 40).filter { dark(column, it) }
    assertTrue("nessun inchiostro sotto il dito", rows.isNotEmpty())
    val center = rows.average().toFloat() + 0.5f
    assertEquals("il centro del tratto (righe $rows) sotto il dito", y, center, 1f)
    // Comincia e finisce dove il dito comincia e finisce (lo spessore della punta tonda, 3,5 dp, oltre gli estremi).
    assertTrue(dark(from.x.toInt() + 6, y.toInt()))
    assertTrue(dark(to.x.toInt() - 6, y.toInt()))
    assertFalse(dark(from.x.toInt() - 30, y.toInt()))
    assertFalse(dark(to.x.toInt() + 30, y.toInt()))
  }
}
