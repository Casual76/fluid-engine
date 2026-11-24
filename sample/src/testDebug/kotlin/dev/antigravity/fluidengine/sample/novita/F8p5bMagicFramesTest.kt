package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidSymbol
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Mic
import dev.antigravity.fluidengine.ui.fluid.icons.MicOff
import dev.antigravity.fluidengine.ui.fluid.icons.Notifications
import dev.antigravity.fluidengine.ui.fluid.icons.NotificationsActive
import dev.antigravity.fluidengine.ui.fluid.icons.NotificationsOff
import dev.antigravity.fluidengine.ui.fluid.icons.Pause
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import kotlin.math.abs
import org.junit.After
import org.junit.Assert.assertEquals
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
 * F8.5b · La Magic Replace nell'albero Compose vero, coi pixel (PixelCopy): la campana che diventa barrata disegna la
 * barra dall'alto a sinistra e resta nitida; la barra che se ne va si ritira; play e pausa restano la sostituzione
 * sfocata. I fermi agli istanti misurati su Apple finiscono in `build/reports/f8-5b/fermi/` per la tavola accanto ad Apple
 * (`sessioni/f8-5b/tavola-magic.py`).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h900dp-xhdpi")
class F8p5bMagicFramesTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val out = File("build/reports/f8-5b/fermi")
  private val view get() = compose.activity.window.decorView
  private var icon by mutableStateOf<ImageVector>(FluidIcons.Notifications)

  /** L'arancio dei simboli nel video di Apple, per una tavola che si confronta a colpo d'occhio. */
  private val ink = Color(0xFFFDAD60)

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
    out.mkdirs()
  }

  @After fun stop() {
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun frame() {
    compose.mainClock.advanceTimeBy(16)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    Snapshot.sendApplyNotifications()
  }

  private fun show(first: ImageVector) {
    icon = first
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        Box(Modifier.fillMaxSize().background(Color(0xFFF2F2F4)), contentAlignment = Alignment.Center) {
          FluidSymbol(icon, null, Modifier.testTag("simbolo"), tint = ink, size = 160.dp)
        }
      }
    }
    repeat(30) { frame() }
  }

  /** Porta il simbolo su [first] e aspetta che sia fermo (setContent si chiama una volta sola per prova). */
  private fun reset(first: ImageVector) {
    compose.runOnIdle {
      icon = first
      Snapshot.sendApplyNotifications()
    }
    repeat(60) { frame() }
  }

  /** Il riquadro del simbolo, catturato dalla finestra. */
  private fun capture(): Bitmap {
    val window = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(compose.activity.window, android.graphics.Rect(0, 0, view.width, view.height), window,
        { assertEquals(PixelCopy.SUCCESS, it) }, Handler(Looper.getMainLooper()))
    }
    shadowOf(Looper.getMainLooper()).idle()
    val b = compose.onNodeWithTag("simbolo").fetchSemanticsNode().boundsInWindow
    return Bitmap.createBitmap(window, b.left.toInt(), b.top.toInt(), b.width.toInt(), b.height.toInt())
  }

  /** Cambia l'icona e cattura i fotogrammi agli istanti (ms) dati; il primo fotogramma dello scambio e' lo 0. */
  private fun replace(to: ImageVector, name: String, times: List<Int>): Map<Int, Bitmap> {
    val before = capture()
    compose.runOnIdle {
      icon = to
      Snapshot.sendApplyNotifications()
    }
    val shots = linkedMapOf(-1 to before)
    var t = 0
    frame() // il fotogramma in cui la sostituzione parte
    for (target in times) {
      while (t + 16 <= target) { frame(); t += 16 }
      shots[target] = capture()
    }
    repeat(40) { frame() }
    shots[9999] = capture()
    for ((ms, bitmap) in shots) {
      File(out, "$name-${if (ms < 0) "prima" else if (ms == 9999) "dopo" else "%04d".format(ms)}.png").outputStream()
        .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    return shots
  }

  private val times = listOf(0, 33, 67, 100, 133, 167, 200, 267, 400, 533, 700)

  /** Quanto un pixel e' inchiostro (0 fondo, 1 inchiostro pieno), dal verde: il fondo e' 0xF2, l'inchiostro 0xAD. */
  private fun inkOf(p: Int): Float = ((0xF2 - ((p shr 8) and 0xFF)) / (0xF2 - 0xAD).toFloat()).coerceIn(0f, 1f)

  @Test fun `la campana diventa barrata, la barra cresce dall'alto a sinistra, nitida, e si posa`() {
    show(FluidIcons.Notifications)
    val shots = replace(FluidIcons.NotificationsOff, "campana-barrata", times)
    val start = shots.getValue(-1)
    val end = shots.getValue(9999)
    val w = start.width
    val h = start.height
    // L'inchiostro nuovo: c'e' alla fine e non all'inizio (la barra).
    val fresh = mutableListOf<Int>()
    for (y in 0 until h) for (x in 0 until w) {
      if (inkOf(end.getPixel(x, y)) > 0.8f && inkOf(start.getPixel(x, y)) < 0.2f) fresh += y * w + x
    }
    assertTrue("la barra c'e': ${fresh.size} pixel", fresh.size > w * h / 60)
    val mid = shots.getValue(133)
    val drawn = fresh.filter { inkOf(mid.getPixel(it % w, it / w)) > 0.5f }
    val share = drawn.size.toFloat() / fresh.size
    assertTrue("a meta' penna e' disegnata in parte: $share", share in 0.15f..0.9f)
    fun reading(list: List<Int>) = list.map { (it % w) + (it / w) }.average()
    assertTrue("la penna parte dall'alto a sinistra", reading(drawn) < reading(fresh))
    // Nitida: i pixel a meta' fra fondo e inchiostro non crescono rispetto al riposo (una sfocatura li moltiplica).
    fun soft(b: Bitmap): Int {
      var n = 0
      for (y in 0 until h) for (x in 0 until w) { val k = inkOf(b.getPixel(x, y)); if (k > 0.15f && k < 0.85f) n++ }
      return n
    }
    assertTrue("niente sfocatura: ${soft(mid)} contro ${soft(end)}", soft(mid) < soft(end) * 2 + 200)
    // Si posa: alla fine e' ferma.
    val after = run { repeat(20) { frame() }; capture() }
    var diff = 0
    for (y in 0 until h) for (x in 0 until w) if (abs(inkOf(after.getPixel(x, y)) - inkOf(end.getPixel(x, y))) > 0.05f) diff++
    assertEquals("ferma dopo la sostituzione", 0, diff)
  }

  @Test fun `la barra se ne va ritirandosi, e le altre coppie lasciano i loro fermi`() {
    show(FluidIcons.NotificationsOff)
    val back = replace(FluidIcons.Notifications, "campana-torna", times)
    val start = back.getValue(-1)
    val mid = back.getValue(133)
    val w = start.width
    val h = start.height
    // La barra a meta' ritirata: c'e' ancora vicino al suo inizio (in alto a sinistra), non in fondo.
    var topLeft = 0
    var bottomRight = 0
    for (y in 0 until h) for (x in 0 until w) {
      if (inkOf(start.getPixel(x, y)) > 0.8f && inkOf(back.getValue(9999).getPixel(x, y)) < 0.2f) {
        if (inkOf(mid.getPixel(x, y)) > 0.5f) { if (x + y < w) topLeft++ else bottomRight++ }
      }
    }
    assertTrue("si ritira verso l'inizio: $topLeft in alto, $bottomRight in basso", topLeft > bottomRight)
    reset(FluidIcons.Notifications)
    replace(FluidIcons.NotificationsActive, "campana-suona", times)
    reset(FluidIcons.Mic)
    replace(FluidIcons.MicOff, "microfono-muto", times)
    // Play e pausa non sono parenti: la sostituzione sfocata di F8.5, per confronto. Il primo fotogramma dello scambio
    // mostra ancora solo play: mai le due icone nitide insieme (il difetto visto qui la prima volta).
    reset(FluidIcons.PlayArrow)
    val swap = replace(FluidIcons.Pause, "play-pausa", times)
    val before = swap.getValue(-1)
    val first = swap.getValue(0)
    val final = swap.getValue(9999)
    var pauseOnly = 0
    var pauseShown = 0
    for (y in 0 until before.height) for (x in 0 until before.width) {
      if (inkOf(final.getPixel(x, y)) > 0.8f && inkOf(before.getPixel(x, y)) < 0.2f) {
        pauseOnly++
        if (inkOf(first.getPixel(x, y)) > 0.5f) pauseShown++
      }
    }
    assertTrue("al primo fotogramma la pausa non c'e' ancora: $pauseShown su $pauseOnly", pauseShown < pauseOnly / 20)
  }
}
