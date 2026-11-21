@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.action

import android.graphics.Bitmap
import android.graphics.RenderNode
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.ThemeMode
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.action.*
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import kotlin.math.roundToInt
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Riprese PixelCopy dell'entry point di produzione: sei tasti, riuscita/rifiuto, temi chiaro/scuro. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w420dp-h400dp-xhdpi")
class F6b3GlyphFramesTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private val presets = listOf(FluidActionPreset.Send, FluidActionPreset.Save, FluidActionPreset.Copy,
    FluidActionPreset.Download, FluidActionPreset.Upload, FluidActionPreset.Favorite)
  private val names = listOf("invia", "salva", "copia", "scarica", "carica", "preferito")
  private var fraction by mutableFloatStateOf(0f)
  private val reader = { fraction }
  private val pending = FluidActionState.Working(null, pending = true)
  private val working = FluidActionState.Working()
  private val transfer = FluidActionState.Working(reader)
  private val paused = FluidActionState.Working(reader, paused = true)
  private val states = mutableStateListOf<FluidActionState>().apply { repeat(12) { add(FluidActionState.Idle) } }
  private var favoriteAccepted by mutableStateOf(false)
  private val out = File("build/reports/f6b-3b/fotogrammi")
  private val view get() = compose.activity.window.decorView

  private fun frame() {
    compose.mainClock.advanceTimeBy(16)
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
    Snapshot.sendApplyNotifications()
  }

  private fun window(): Bitmap {
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    compose.runOnIdle {
      PixelCopy.request(compose.activity.window, android.graphics.Rect(0, 0, view.width, view.height),
        bitmap, { assertEquals(PixelCopy.SUCCESS, it) }, Handler(Looper.getMainLooper()))
    }
    return bitmap
  }

  @Composable
  private fun Scene(dark: Boolean, reduced: Boolean) {
    val mode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT
    FluidTheme(settings = remember { EngineSettings(themeMode = mode, dynamicColorEnabled = false) }) {
      CompositionLocalProvider(
        LocalFluidMotionPolicy provides FluidMotionPolicy(if (reduced) 0f else 1f),
        LocalContentColor provides MaterialTheme.colorScheme.onSurface,
      ) {
        val backdrop = rememberGlassBackdrop()
        val colors = MaterialTheme.colorScheme
        Box(Modifier.fillMaxWidth().height(132.dp)) {
          Box(Modifier.matchParentSize().glassBackdropSource(backdrop)
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.surface, colors.tertiaryContainer))))
          Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            for (result in 0..1) {
              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                presets.forEachIndexed { i, preset ->
                  val active = if (i == 5 && result == 0 && favoriteAccepted) FluidActionPreset.favorite(true) else preset
                  FluidActionButton(active, states[result * 6 + i], onClick = {},
                    modifier = Modifier.testTag("${if (dark) "scuro" else "chiaro"}-$result-${names[i]}"), backdrop = backdrop)
                }
              }
            }
          }
        }
      }
    }
  }

  private fun stateAt(ms: Int, i: Int, failed: Boolean): FluidActionState {
    if (ms < 160) return FluidActionState.Idle
    val outcome = if (i == 2) 192 else 1680
    if (ms < outcome) return when {
      ms < 464 || i == 5 -> pending
      i == 3 || i == 4 -> if (ms in 880 until 1200) paused else transfer
      else -> working
    }
    val result = if (failed) FluidActionState.Failed else FluidActionState.Succeeded
    val hold = fluidActionHoldMs(presets[i], FluidActionHold.Preset, result)
    return if (ms - outcome < hold) result else FluidActionState.Idle
  }

  private fun update(ms: Int) {
    compose.runOnUiThread {
      fraction = when {
        ms < 464 -> 0f
        ms < 880 -> .48f * (ms - 464) / 416f
        ms <= 1200 -> .48f
        else -> (.48f + .52f * (ms - 1200) / 448f).coerceAtMost(1f)
      }
      favoriteAccepted = ms >= 1680
      for (i in states.indices) states[i] = stateAt(ms, i % 6, i >= 6)
    }
  }

  private fun show(reduced: Boolean) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      Column(Modifier.fillMaxSize().background(Color.Gray), verticalArrangement = Arrangement.Center) {
        Scene(dark = false, reduced)
        Scene(dark = true, reduced)
      }
    }
    repeat(28) { frame() }
  }

  private fun downloadButton() = compose.onNode(
    (hasTestTag("chiaro-0-scarica") or hasAnyAncestor(hasTestTag("chiaro-0-scarica"))) and
      SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
    useUnmergedTree = true,
  )

  private fun tags() = listOf("chiaro", "scuro").flatMap { theme ->
    (0..1).flatMap { result -> names.map { "$theme-$result-$it" } }
  }

  @Test fun `fotogrammi di tutti i viaggi e catture del vetro`() {
    val enabled = FluidGlassDiagnostics.enabled
    val warm = FluidGlassDiagnostics.warmUpEnabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.warmUpEnabled = false
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(true)
    val node = RenderNode("F6B.3b")
    try {
      val beforeScene = FluidGlassDiagnostics.totalCaptures
      show(reduced = false)
      out.mkdirs()
      fun bounds() = tags().associateWith { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInWindow }
      val initial = bounds()
      File(out, "ritagli.csv").printWriter().use { log ->
        log.println("tasto,x,y,w,h")
        initial.forEach { (tag, r) -> log.println("$tag,${r.left.roundToInt()},${r.top.roundToInt()},${r.width.roundToInt()},${r.height.roundToInt()}") }
      }
      node.setPosition(0, 0, view.width, view.height)
      fun draw() = compose.runOnUiThread {
        val canvas = node.beginRecording()
        try { view.draw(canvas) } finally { node.endRecording() }
      }
      draw(); frame(); draw()
      val captures = FluidGlassDiagnostics.totalCaptures
      assertTrue("controllo positivo: i vetri in scena sono stati catturati", captures > beforeScene)
      File(out, "cronologia.csv").printWriter().use { log ->
        log.println("frame,ms,frazione,pausa,catture")
        for (index in 0..280) {
          val ms = index * 16
          update(ms)
          frame()
          draw()
          val shot = window()
          File(out, "f%03d.png".format(index)).outputStream().use { shot.compress(Bitmap.CompressFormat.PNG, 100, it) }
          if (ms == 2304) {
            // Invia espone la spunta F6B.3a alla taglia del posto. Il confronto delle sagome
            // include lo spazio intorno: un disco o un segno ingrandito lo farebbero fallire.
            for (theme in listOf("chiaro", "scuro")) {
              fun ink(name: String): Set<Pair<Int, Int>> {
                val center = initial.getValue("$theme-0-$name").center
                return buildSet {
                  for (y in -34..34) for (x in -34..34) {
                    val rgb = shot.getPixel(center.x.roundToInt() + x, center.y.roundToInt() + y)
                    val red = android.graphics.Color.red(rgb)
                    if (if (theme == "chiaro") red < 100 else red > 185) add(x to y)
                  }
                }
              }
              val reference = ink("invia")
              val save = ink("salva")
              assertTrue("riferimento della spunta visibile ($theme)", reference.isNotEmpty())
              assertTrue("solo la spunta a taglia icona ($theme)", (-1..1).any { dx -> (-1..1).any { dy ->
                val overlap = save.count { (x, y) -> (x + dx to y + dy) in reference }
                overlap.toFloat() / (save.size + reference.size - overlap) > .88f
              } })
            }
          }
          shot.recycle()
          log.println("$index,$ms,$fraction,${ms in 880 until 1200},${FluidGlassDiagnostics.totalCaptures - captures}")
          if (ms in listOf(480, 976, 1232, 1744, 1920, 4208)) assertEquals("misure a $ms", initial, bounds())
          if (ms == 976) {
            val config = downloadButton().fetchSemanticsNode().config
            assertEquals(listOf("In pausa"), config[SemanticsProperties.ContentDescription])
            assertEquals("48%", config[SemanticsProperties.StateDescription])
            assertTrue(config.contains(SemanticsProperties.Disabled))
          }
        }
      }
      println("F6B.3b: 281 fotogrammi, 24 tasti, catture iniziali=${captures - beforeScene}, durante=${FluidGlassDiagnostics.totalCaptures - captures}")
      assertEquals("i glifi non ricatturano il fondale", captures, FluidGlassDiagnostics.totalCaptures)
    } finally {
      node.discardDisplayList()
      FluidGlassDiagnostics.resetForTesting()
      FluidGlassDiagnostics.warmUpEnabled = warm
      FluidGlassDiagnostics.enabled = enabled
    }
  }

  @Test fun `la pausa congela anche un lettore esterno che cambia`() {
    show(reduced = false)
    update(880)
    repeat(24) { frame() }
    val node = downloadButton()
    fun percentage() = node.fetchSemanticsNode().config[SemanticsProperties.StateDescription]
    assertEquals("48%", percentage())
    compose.runOnUiThread { fraction = .91f }
    repeat(16) { frame() }
    assertEquals("48%", percentage())
    update(1200)
    frame()
    assertEquals("25%", percentage()) // In marcia l'annuncio torna ai quarti, l'arco resta al 48%.
    update(1488)
    frame()
    assertEquals("75%", percentage())
  }

  @Test fun `con movimento ridotto il primo fotogramma di ogni esito e gia la posa`() {
    show(reduced = true)
    // PixelCopy è anche il primo disegno del fondale su questo banco: portiamo la finestra a riposo prima del confronto.
    repeat(24) { window().recycle(); frame() }
    val report = File(out.parentFile, "ridotto").apply { mkdirs() }
    val bounds = tags().associateWith { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInWindow }

    // Confronto al bit dei soli tasti, per tutti i fotogrammi. PixelCopy può consegnare ancora la posa di riposo
    // prima della traversata Android: il primo fotogramma che cambia deve essere già uguale a quello finale.
    fun capture(label: String?): Map<String, IntArray> {
      val image = window()
      try {
        if (label != null) File(report, "$label.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bounds.mapValues { (_, r) ->
          val w = r.width.roundToInt()
          val h = r.height.roundToInt()
          IntArray(w * h).also { image.getPixels(it, 0, w, r.left.roundToInt(), r.top.roundToInt(), w, h) }
        }
      } finally { image.recycle() }
    }

    val rest = capture("riposo")
    compose.runOnUiThread {
      for (i in states.indices) states[i] = if (i < 6) FluidActionState.Succeeded else FluidActionState.Failed
    }
    val frames = (1..33).map { index ->
      frame()
      capture(when (index) { 1 -> "primo"; 2 -> "secondo"; 33 -> "posato"; else -> null })
    }
    val errors = mutableListOf<String>()
    File(report, "pose.csv").printWriter().use { log ->
      log.println("tasto,primo_cambio_frame,fotogrammi_intermedi")
      for (tag in bounds.keys) {
        val from = rest.getValue(tag)
        val settled = frames.last().getValue(tag)
        val first = frames.indexOfFirst { !from.contentEquals(it.getValue(tag)) }
        val moving = frames.indices.filter { it >= first && !settled.contentEquals(frames[it].getValue(tag)) }
        log.println("$tag,${first + 1},${moving.size}")
        if (first !in 0..1 || from.contentEquals(settled) || moving.isNotEmpty()) {
          errors += "$tag primo=${first + 1} intermedi=${moving.map { it + 1 }}"
        }
      }
    }
    assertTrue("movimento ridotto: $errors", errors.isEmpty())
  }
}
