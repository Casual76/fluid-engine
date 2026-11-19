package dev.antigravity.fluidengine.sample

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.NavHostController
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainerDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.page.FluidPageHostState
import dev.antigravity.fluidengine.ui.page.rememberFluidPageHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

/**
 * Il titolo della pagina nello zoom (corr-s4-zoom, S4 NO 4), nella scena vera della galleria ([ZoomDemo]) e nei pixel
 * della finestra (`PixelCopy`, come `FluidTabBarExtensionsTest`).
 *
 * Il titolo grande di una pagina lo disegna la barra dell'host, sopra le pagine: è contenuto e viaggia con la sagoma, ma
 * fino a corr-s4-zoom non seguiva la dissolvenza. Sul Tab S9 il «Faro» nero restava sulla foto della card fino all'arrivo
 * (≈480 ms), mentre il resto della pagina se n'era andato verso 200 ms, come in Apple. Qui, fotogramma per fotogramma:
 *  - finita la dissolvenza, dentro la foto della card non c'è più inchiostro scuro, fino a quando la card rinasce;
 *  - finché le righe della pagina si leggono, si legge anche il titolo: se ne va **con** la pagina, non prima. Il primo
 *    tentativo di correzione lo sbiadiva nell'opacità del layer della barra, che sotto 1 passa per un buffer alto quanto
 *    la riga dei controlli: il titolo grande, più in basso, spariva tutto al primo fotogramma della dissolvenza.
 *
 * Dove sta il titolo lo dice il nodo del titolo della barra (fra i nodi non uniti: la riga dei controlli li unisce), che
 * passa per il layer del titolo come il disegno. Le righe della pagina perdono la semantica mentre la pagina esce: la riga
 * di [ZoomDetailMark] si misura a riposo e si porta dove sta con la stessa scala e lo stesso spostamento del titolo, che
 * viaggia con la pagina allo stesso pixel.
 *
 * La barra di stato è finta (24 dp): il titolo sta nella riga dei controlli, sotto di lei, e la sagoma dello zoom va
 * portata nelle sue coordinate. Con `GLASS_ZOOM_TITLE_DIR` il test scrive anche i fotogrammi dell'apertura e del ritorno
 * (metà risoluzione) e i tempi, per il confronto con Apple.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidZoomTitleUiTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private var diagnosticsWere = false
  private lateinit var nav: NavHostController
  private lateinit var pages: FluidPageHostState
  private lateinit var root: View

  /** La barra di stato finta: 24 dp, 48 px a xhdpi. */
  private val statusBarPx = 48

  @Before fun start() {
    diagnosticsWere = FluidGlassDiagnostics.enabled
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
    FluidGlassDiagnostics.enabled = true
    // Il vetro senza AGSL, il gradino che ha sotto Android 13: la cattura disegna davvero, e sotto Robolectric il corpo
    // grande del vetro non compila («program is too large», come su swiftshader). Il titolo dal vetro non dipende.
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
  }

  @After fun stop() {
    FluidGlassDiagnostics.enabled = diagnosticsWere
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassContainerDiagnostics.resetForTesting()
  }

  private fun show() {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        root = LocalView.current
        val controller = rememberFluidNavController()
        nav = controller
        val state = rememberFluidPageHostState()
        pages = state
        ZoomDemo(navController = controller, state = state)
      }
    }
    compose.waitForIdle()
    compose.runOnUiThread {
      val insets = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, statusBarPx, 0, 0))
        .setVisible(WindowInsetsCompat.Type.statusBars(), true)
        .build()
      ViewCompat.dispatchApplyWindowInsets(root, insets)
    }
    compose.waitForIdle()
  }

  /** La finestra intera, nei suoi pixel. */
  private fun window(): Bitmap {
    val decor = compose.activity.window.decorView
    val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(compose.activity.window, android.graphics.Rect(0, 0, decor.width, decor.height), bitmap, { result ->
        assertEquals(PixelCopy.SUCCESS, result)
      }, Handler(Looper.getMainLooper()))
    }
    return bitmap
  }

  /** Quanti pixel di inchiostro scuro (nessun canale sopra il 30%) ci sono in [area]. */
  private fun Bitmap.ink(area: Rect): Int {
    val left = area.left.roundToInt().coerceIn(0, width)
    val right = area.right.roundToInt().coerceIn(0, width)
    val top = area.top.roundToInt().coerceIn(0, height)
    val bottom = area.bottom.roundToInt().coerceIn(0, height)
    val w = right - left
    val h = bottom - top
    if (w <= 0 || h <= 0) return 0
    val pixels = IntArray(w * h)
    getPixels(pixels, 0, w, left, top, w, h)
    return pixels.count { c -> maxOf((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF) < 77 }
  }

  private val out: File? = System.getenv("GLASS_ZOOM_TITLE_DIR")?.takeIf { it.isNotBlank() }?.let(::File)
  private val times = StringBuilder("fase,fotogramma,ms,inchiostro,titolo,righe\n")

  /**
   * Dove si vedono adesso i nodi «Faro» che non sono l'intestazione della pagina (i limiti passano per i layer: il titolo
   * in scala con la pagina). Fra questi c'è il titolo della barra.
   */
  private fun faroNodes(): List<Rect> =
    compose.onAllNodes(hasText("Faro") and !isHeading(), useUnmergedTree = true).fetchSemanticsNodes()
      .map { it.boundsInWindow }.filter { it.width > 0f && it.height > 0f }

  private fun keep(phase: String, frame: Int, ms: Long, bitmap: Bitmap, ink: Int, title: Int? = null, rows: Int? = null) {
    times.append("$phase,$frame,$ms,$ink,${title ?: ""},${rows ?: ""}\n")
    val dir = out ?: return
    val file = File(dir, "$phase/f%03d.png".format(frame))
    file.parentFile?.mkdirs()
    val half = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)
    file.outputStream().use { half.compress(Bitmap.CompressFormat.PNG, 100, it) }
  }

  @Test fun `nel ritorno col tasto il titolo della pagina se ne va con la pagina e la card resta sola`() {
    show()
    val card = compose.onNodeWithTag(zoomCardTag(0)).fetchSemanticsNode().boundsInWindow
    // La foto della card (proporzione 1,25), un po' dentro i bordi: lì la card non ha scritte, e il titolo della pagina in
    // scala ci cadeva sopra.
    val inset = 8f
    val photo = Rect(card.left + inset, card.top + inset, card.right - inset, card.top + card.width / 1.25f - inset)

    // L'apertura, fotogramma per fotogramma (solo per i fotogrammi da guardare).
    compose.mainClock.autoAdvance = false
    compose.onNodeWithTag(zoomCardTag(0)).performClick()
    val openStart = compose.mainClock.currentTime
    for (frame in 0 until 45) {
      val shot = window()
      keep("apertura", frame, compose.mainClock.currentTime - openStart, shot, shot.ink(photo))
      compose.mainClock.advanceTimeByFrame()
    }
    compose.mainClock.advanceTimeBy(1_200L)
    compose.mainClock.autoAdvance = true
    compose.waitForIdle()
    assertTrue("la pagina è aperta e ferma", !pages.isMoving)

    // A riposo il titolo grande si vede, in alto a sinistra: la cattura vede l'inchiostro del titolo.
    val open = window()
    // Sotto la riga dei controlli (46 dp, 92 px), dove sta il titolo grande e non la freccia dell'indietro.
    val titleZone = Rect(0f, statusBarPx + 92f, open.width * 0.5f, statusBarPx + 92f + 160f)
    val titleInk = open.ink(titleZone)
    assertTrue("a riposo il titolo della pagina si vede ($titleInk pixel scuri)", titleInk > 40)
    // Il titolo della barra a riposo: il nodo «Faro» che sta sull'intestazione della pagina (la barra lo disegna lì).
    val heading = compose.onAllNodes(hasText("Faro") and isHeading()).fetchSemanticsNodes().single().boundsInWindow
    val titleRest = faroNodes().maxByOrNull { (it.intersect(heading).let { r -> maxOf(0f, r.width) * maxOf(0f, r.height) }) }
    assertTrue("il titolo della barra sta sull'intestazione: $titleRest, $heading", titleRest != null && titleRest.overlaps(heading))
    val rowRest = compose.onAllNodes(hasText(ZoomDetailMark)).fetchSemanticsNodes().single().boundsInWindow
    var titleNow: Rect = titleRest!!

    // Il ritorno col tasto: lo stesso pop del tasto indietro della barra.
    compose.mainClock.autoAdvance = false
    compose.runOnUiThread { nav.popBackStack() }
    val backStart = compose.mainClock.currentTime
    val late = ArrayList<Pair<Long, Int>>()
    val early = ArrayList<Triple<Long, Int, Int>>()
    var frame = 0
    var arrived = -1L
    while (frame < 90) {
      compose.mainClock.advanceTimeByFrame()
      frame++
      val ms = compose.mainClock.currentTime - backStart
      val shot = window()
      val ink = shot.ink(photo)
      // Nei primi 160 ms il titolo è lontano dalla card (il nodo più vicino a quello di prima è il suo), e la fotografia
      // della card, che compare sopra la pagina, è ancora pallida: l'inchiostro scuro nelle righe è della pagina.
      var headInk: Int? = null
      var rowsInk: Int? = null
      if (pages.isMoving && ms <= 160L) {
        faroNodes().minByOrNull { (it.center - titleNow.center).getDistance() }?.let { now ->
          titleNow = now
          val k = now.width / titleRest.width
          fun map(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(
            now.left + k * (x - titleRest.left), now.top + k * (y - titleRest.top),
          )
          val row = Rect(map(rowRest.left, rowRest.top), map(rowRest.right, rowRest.bottom))
          val h = shot.ink(now)
          val r = shot.ink(row)
          headInk = h
          rowsInk = r
          early += Triple(ms, h, r)
        }
      }
      keep("ritorno", frame, ms, shot, ink, headInk, rowsInk)
      if (!pages.isMoving) {
        if (arrived < 0) arrived = ms
        if (ms > arrived + 100) break
        continue
      }
      // La dissolvenza finisce quando la sagoma ha fatto 0,9 della strada (≈217 ms col tasto, più il fotogramma in cui il
      // pop arriva all'host): da lì dentro la sagoma c'è solo la card.
      if (ms >= 270L) late += ms to ink
    }
    out?.let { File(it, "tempi.csv").apply { parentFile?.mkdirs() }.writeText(times.toString()) }
    assertTrue("la card rinasce dopo il ritorno", arrived > 0)
    assertTrue("il ritorno resta in volo oltre 270 ms (arrivo a $arrived ms)", late.size >= 3)
    for ((ms, ink) in late) {
      assertTrue("a $ms ms sulla foto della card resta inchiostro della pagina: $ink pixel scuri", ink <= 2)
    }
    // Il titolo se ne va con la pagina: finché le righe si leggono, si legge anche lui.
    val readable = early.filter { (_, _, rows) -> rows >= 12 }
    assertTrue("nei primi 160 ms del ritorno le righe della pagina si leggono: $early", readable.size >= 3)
    for ((ms, title, rows) in readable) {
      assertTrue("a $ms ms le righe si leggono ($rows pixel scuri) e il titolo no ($title): se ne va prima della pagina", title >= 12)
    }
  }
}
