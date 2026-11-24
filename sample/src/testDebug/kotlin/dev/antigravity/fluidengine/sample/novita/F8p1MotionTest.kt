@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.novita

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidCountdownBadge
import dev.antigravity.fluidengine.ui.fluid.FluidExpandableText
import dev.antigravity.fluidengine.ui.fluid.FluidLabels
import dev.antigravity.fluidengine.ui.fluid.FluidLiveClock
import dev.antigravity.fluidengine.ui.fluid.FluidLiveClockProvider
import dev.antigravity.fluidengine.ui.fluid.FluidLiveTimeDiagnostics
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicyProvider
import dev.antigravity.fluidengine.ui.fluid.FluidNumericText
import dev.antigravity.fluidengine.ui.fluid.FluidRelativeText
import dev.antigravity.fluidengine.ui.fluid.FluidRevealingText
import dev.antigravity.fluidengine.ui.fluid.FluidTextStyles
import dev.antigravity.fluidengine.ui.fluid.FluidTimeSource
import dev.antigravity.fluidengine.ui.fluid.LocalFluidLabels
import dev.antigravity.fluidengine.ui.fluid.LocalFluidRevealProbe
import dev.antigravity.fluidengine.ui.fluid.LocalFluidTimeSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidLiveClock
import dev.antigravity.fluidengine.ui.theme.FluidContentState
import dev.antigravity.fluidengine.ui.theme.FluidEmptyPreset
import dev.antigravity.fluidengine.ui.theme.FluidEmptyState
import dev.antigravity.fluidengine.ui.theme.FluidLoadState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * F8.1 · Il testo e i numeri che si muovono, in un albero Compose vero con la grafica nativa: le cifre giuste che scorrono
 * e nel verso giusto, il testo che arriva in ordine e alla fine è tutto leggibile, il testo che si espande, il conto alla
 * rovescia che a riposo non ricompone più di una volta al secondo, il tempo relativo che batte da solo, gli stati pronti.
 * E «Riduci movimento».
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "it-rIT-w400dp-h800dp-xhdpi")
class F8p1MotionTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private fun advance(millis: Long) {
    repeat(((millis + 15) / 16).toInt()) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    }
  }

  /** A passi larghi, per i minuti: i fotogrammi in mezzo non servono a un testo che batte una volta al minuto. */
  private fun advanceCoarse(millis: Long) {
    repeat((millis / 250).toInt()) {
      Snapshot.sendApplyNotifications()
      compose.mainClock.advanceTimeBy(250)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(250))
    }
  }

  private fun show(reduced: Boolean = false, content: @Composable () -> Unit) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        FluidMotionPolicyProvider(if (reduced) FluidMotionPolicy(durationScale = 0f) else FluidMotionPolicy.Default) {
          Box(Modifier.background(Color.White).padding(24.dp)) { content() }
        }
      }
    }
  }

  /** L'inchiostro di un nodo: per ogni pixel quanto è scuro (0 bianco, 1 nero). */
  private class Ink(val width: Int, val height: Int, val dark: FloatArray) {
    fun at(x: Int, y: Int) = dark[y * width + x]

    /** Il baricentro verticale dell'inchiostro nelle colonne [from, to). */
    fun centroidY(from: Int, to: Int): Float {
      var sum = 0.0
      var weight = 0.0
      for (y in 0 until height) for (x in from until to) {
        val d = at(x, y)
        sum += d * y
        weight += d
      }
      return if (weight == 0.0) Float.NaN else (sum / weight).toFloat()
    }

    fun total(from: Int, to: Int): Float {
      var sum = 0f
      for (y in 0 until height) for (x in from until to) sum += at(x, y)
      return sum
    }
  }

  /**
   * L'inchiostro di un nodo, dalla finestra intera con `PixelCopy` (come le prove di F6D): `captureToImage` aspetta una
   * ridisegnata che a orologio fermo non arriva.
   */
  private fun ink(tag: String): Ink {
    val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
    val window = compose.activity.window
    val view = window.decorView
    // Una ridisegnata forzata, come le prove di F6B.8: senza, la finestra ha ancora il primo disegno.
    val node = android.graphics.RenderNode("F8.1")
    node.setPosition(0, 0, view.width, view.height)
    val canvas = node.beginRecording()
    try { view.draw(canvas) } finally { node.endRecording() }
    val image = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    var result = -1
    PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper()))
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    val left = bounds.left.toInt().coerceAtLeast(0)
    val top = bounds.top.toInt().coerceAtLeast(0)
    val width = (bounds.width.toInt()).coerceAtMost(image.width - left)
    val height = (bounds.height.toInt()).coerceAtMost(image.height - top)
    val dark = FloatArray(width * height)
    for (y in 0 until height) for (x in 0 until width) {
      val c = image.getPixel(left + x, top + y)
      val luma = (android.graphics.Color.red(c) + android.graphics.Color.green(c) + android.graphics.Color.blue(c)) / (3f * 255f)
      dark[y * width + x] = (1f - luma).coerceIn(0f, 1f)
    }
    return Ink(width, height, dark)
  }

  // ---- I numeri che scorrono ------------------------------------------------------------------------

  /** Il baricentro delle cifre di destra: a riposo prima, a metà del cambio, a riposo dopo. */
  private class Roll(val before: Float, val mid: Float, val after: Float) {
    val low get() = minOf(before, after)
    val high get() = maxOf(before, after)
    override fun toString() = "prima $before, a metà $mid, dopo $after"
  }

  private fun roll(from: Int, to: Int, reduced: Boolean): Roll {
    var value by mutableIntStateOf(from)
    show(reduced) {
      FluidNumericText(
        text = "$value",
        value = value.toDouble(),
        style = FluidTextStyles.largeNumeric,
        color = Color.Black,
        modifier = Modifier.testTag("numero"),
      )
    }
    advance(300)
    val rest = ink("numero")
    val third = rest.width / 3
    val restLeft = rest.centroidY(0, third)
    val before = rest.centroidY(third, rest.width)
    value = to
    advance(64)
    val mid = ink("numero")
    assertEquals("la prima cifra non cambia e non si muove", restLeft, mid.centroidY(0, third), 0.6f)
    advance(900)
    val end = ink("numero")
    assertEquals("ferma, la prima cifra è ancora lì", restLeft, end.centroidY(0, third), 0.6f)
    return Roll(before, mid.centroidY(third, mid.width), end.centroidY(third, end.width))
  }

  @Test
  fun `un valore che cresce fa salire solo le cifre cambiate`() {
    val r = roll(128, 131, reduced = false)
    assertTrue("le cifre cambiate salgono: $r", r.mid < r.low - 1.5f)
  }

  @Test
  fun `un valore che cala le fa scendere`() {
    val r = roll(131, 128, reduced = false)
    assertTrue("le cifre cambiate scendono: $r", r.mid > r.high + 1.5f)
  }

  @Test
  fun `col movimento ridotto le cifre cambiano a dissolvenza, sul posto`() {
    val r = roll(128, 131, reduced = true)
    assertTrue("nessun viaggio, solo la dissolvenza fra le due: $r", r.mid in (r.low - 0.75f)..(r.high + 0.75f))
  }

  @Test
  fun `TalkBack sente solo il valore finale`() {
    var value by mutableIntStateOf(3)
    show { FluidNumericText(value, Modifier.testTag("numero")) }
    advance(100)
    value = 4
    advance(32)
    val description = compose.onNodeWithTag("numero").fetchSemanticsNode().config[SemanticsProperties.ContentDescription]
    assertEquals(listOf("4"), description)
  }

  // ---- Il testo che arriva ---------------------------------------------------------------------------

  private val words = "Le parole arrivano una dopo l'altra sfocate"
  private val style = TextStyle(fontSize = 22.sp)

  /** Ogni fotogramma dell'arrivo: quanto è ancora nascosta ogni parola (1 nascosta, 0 dentro), nell'ordine del testo. */
  private fun arrivingFrames(reduced: Boolean, frames: MutableList<List<Float>>, work: MutableList<Boolean>) {
    show(reduced) {
      CompositionLocalProvider(
        LocalFluidRevealProbe provides { tracker, now ->
          if (now == Long.MIN_VALUE) work += tracker.hasWork else frames += tracker.all.map { it.mask(now) }
        },
      ) {
        FluidRevealingText(AnnotatedString(words), style, Color.Black, Modifier.testTag("arriva"), animateFirst = true)
      }
    }
  }

  @Test
  fun `il testo arriva in ordine, sfocato, e alla fine e' tutto leggibile`() {
    val frames = ArrayList<List<Float>>()
    val work = ArrayList<Boolean>()
    arrivingFrames(reduced = false, frames, work)
    advance(1_000)
    assertEquals("nasce con del lavoro", listOf(true), work)
    assertTrue("l'arrivo dura più fotogrammi: ${frames.size}", frames.size > 10)
    frames.forEachIndexed { index, masks ->
      for (i in 1 until masks.size) {
        assertTrue("fotogramma $index: la parola $i è più avanti di quella prima: $masks", masks[i] >= masks[i - 1] - 1e-4f)
      }
    }
    // Non tutto insieme: c'è un fotogramma in cui la prima parola è quasi dentro e l'ultima ancora quasi nascosta.
    assertTrue("gradualmente", frames.any { it.first() < 0.35f && it.last() > 0.65f })
    // Sfocato: per molti fotogrammi le parole stanno a metà (il fantasma ha una σ = picco × maschera).
    assertTrue("a metà strada, sfocate", frames.count { m -> m.any { it in 0.2f..0.8f } } >= 4)
    assertTrue("il fantasma si disegna su questo Android", dev.antigravity.fluidengine.ui.fluid.FluidTextArrival.Streaming.blur)
    assertTrue("alla fine tutto è dentro", frames.last().all { it == 0f })
    compose.onNodeWithText(words).assertExists()
  }

  @Test
  fun `col movimento ridotto il testo appare intero, senza sfocatura`() {
    val frames = ArrayList<List<Float>>()
    val work = ArrayList<Boolean>()
    arrivingFrames(reduced = true, frames, work)
    advance(200)
    assertEquals("nessun arrivo da fare", listOf(false), work)
    assertTrue("nessun fotogramma d'arrivo", frames.isEmpty())
    compose.onNodeWithText(words).assertExists()
  }

  // ---- Il testo che si espande -------------------------------------------------------------------------

  @Test
  fun `il testo si apre con altro, le righe arrivano e lo stato resta`() {
    val restoration = StateRestorationTester(compose)
    compose.mainClock.autoAdvance = false
    restoration.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        Box(Modifier.background(Color.White).width(320.dp)) {
          FluidExpandableText(F8p1Description, Modifier.testTag("testo"), collapsedMaxLines = 3)
        }
      }
    }
    advance(200)
    val text = compose.onNodeWithText("… altro", substring = true)
    val collapsedText = text.fetchSemanticsNode().config[SemanticsProperties.Text].joinToString()
    assertTrue("«… altro» in fondo: $collapsedText", collapsedText.endsWith("… altro"))
    assertEquals("Espandi", text.fetchSemanticsNode().config[SemanticsActions.OnClick].label)
    val before = compose.onNodeWithTag("testo").fetchSemanticsNode().size.height
    text.performClick()
    advance(48)
    val growing = compose.onNodeWithTag("testo").fetchSemanticsNode().size.height
    advance(1_000)
    val open = compose.onNodeWithTag("testo").fetchSemanticsNode().size.height
    compose.onNodeWithText(F8p1Description).assertExists()
    compose.onNodeWithText("… altro", substring = true).assertDoesNotExist()
    assertTrue("l'altezza cresce con la molla: $before → $growing → $open", growing in (before + 1) until open)
    restoration.emulateSavedInstanceStateRestore()
    advance(200)
    compose.onNodeWithText(F8p1Description).assertExists()
  }

  // ---- Il conto alla rovescia -----------------------------------------------------------------------

  @Test
  fun `a riposo il conto alla rovescia ricompone una volta al secondo, e a zero finisce una volta`() {
    val base = 1_700_000_000_000L
    val start = compose.mainClock.currentTime
    val source = FluidTimeSource { base + compose.mainClock.currentTime - start }
    var expired = 0
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidTimeSource provides source) {
          FluidCountdownBadge(
            endsAt = Instant.ofEpochMilli(base + 10_000),
            total = Duration.ofSeconds(10),
            onExpire = { expired++ },
            modifier = Modifier.testTag("capsula"),
          )
        }
      }
    }
    advance(500)
    FluidLiveTimeDiagnostics.timerCompositions = 0
    advance(5_000)
    val compositions = FluidLiveTimeDiagnostics.timerCompositions
    assertTrue("in 5 s al più sei composizioni: $compositions", compositions in 4..6)
    val spoken = compose.onNodeWithTag("capsula").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
    assertTrue(spoken, spoken.startsWith("Mancano") && spoken.contains("secondi"))
    advance(5_200)
    assertEquals("a zero, una volta", 1, expired)
    assertEquals("Tempo scaduto", compose.onNodeWithTag("capsula").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single())
    advance(2_000)
    assertEquals(1, expired)
  }

  // ---- Il tempo relativo vivo -----------------------------------------------------------------------

  @Test
  fun `il tempo relativo batte da solo, con un orologio solo per la pagina`() {
    val base = 1_700_000_000_000L
    val start = compose.mainClock.currentTime
    val source = FluidTimeSource { base + compose.mainClock.currentTime - start }
    var clock: FluidLiveClock? = null
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidLabels provides FluidLabels(), LocalFluidTimeSource provides source) {
          val live = rememberFluidLiveClock().also { clock = it }
          FluidLiveClockProvider(live) {
            Column {
              FluidRelativeText(Instant.ofEpochMilli(base - 20_000), Modifier.testTag("a"), locale = Locale.ITALY)
              FluidRelativeText(Instant.ofEpochMilli(base - 125_000), Modifier.testTag("b"), locale = Locale.ITALY)
              FluidRelativeText(Instant.ofEpochMilli(base - 3_600_000 - 60_000), Modifier.testTag("c"), locale = Locale.ITALY)
            }
          }
        }
      }
    }
    advance(200)
    compose.onNodeWithText("ora").assertExistsCompat()
    compose.onNodeWithText("2 min fa").assertExistsCompat()
    val wakeupsBefore = clock!!.wakeups
    advanceCoarse(42_000)
    compose.onNodeWithText("1 min fa").assertExistsCompat()
    compose.onNodeWithText("2 min fa").assertExistsCompat()
    val wakeups = clock!!.wakeups - wakeupsBefore
    // In 42 s cambia un solo testo («ora» → «1 min fa»; «2 min fa» cambia a 55 s): l'orologio si sveglia per lui.
    assertTrue("risvegli in 42 s: $wakeups", wakeups in 1..3)
    val spoken = compose.onNodeWithTag("b").fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
    assertEquals("2 minuti fa", spoken)
  }

  private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertExistsCompat() {
    assertExists()
  }

  // ---- Gli stati pronti -------------------------------------------------------------------------------

  @Test
  fun `gli stati pronti hanno le parole, l'inglese e il riprova automatico non a raffica`() {
    var online by mutableStateOf(false)
    var attempts = 0
    var english by mutableStateOf(false)
    var preset by mutableStateOf<FluidEmptyPreset>(FluidEmptyPreset.NoResults("cena"))
    show {
      CompositionLocalProvider(LocalFluidLabels provides if (english) FluidLabels.english() else FluidLabels()) {
        FluidEmptyState(
          preset = preset,
          online = online,
          onRetry = {
            attempts++
            delay(50)
          },
        )
      }
    }
    advance(200)
    compose.onNodeWithText("Nessun risultato per «cena»").assertExists()
    english = true
    advance(100)
    compose.onNodeWithText("No Results for “cena”").assertExists()
    english = false
    preset = FluidEmptyPreset.Offline
    advance(200)
    compose.onNodeWithText("Sei offline").assertExists()
    online = true
    advance(600)
    assertEquals("torna la rete: un tentativo da solo", 1, attempts)
    online = false
    advance(100)
    online = true
    advance(600)
    assertEquals("la rete che va e viene in un attimo non riprova a raffica", 1, attempts)
  }

  @Test
  fun `il contenuto passa dallo scheletro al contenuto e all'errore`() {
    var state by mutableStateOf<FluidLoadState<String>>(FluidLoadState.Loading)
    show { FluidContentState(state) { androidx.compose.material3.Text(it) } }
    advance(200)
    compose.onNodeWithText("Compiti").assertDoesNotExist()
    state = FluidLoadState.Ready("Compiti")
    advance(800)
    compose.onNodeWithText("Compiti").assertExists()
    state = FluidLoadState.Unavailable(FluidEmptyPreset.Error("Il registro non risponde."))
    advance(800)
    compose.onNodeWithText("Il registro non risponde.").assertExists()
    assertFalse(onAllNodesCompat("Compiti"))
  }

  private fun onAllNodesCompat(text: String): Boolean =
    compose.onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().isNotEmpty()
}
