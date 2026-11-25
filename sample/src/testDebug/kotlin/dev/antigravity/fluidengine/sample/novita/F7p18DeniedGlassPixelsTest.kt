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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.memory.FluidMemoryCategory
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidCompactAdaptation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidPresentationMode
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
import kotlin.math.max
import kotlin.math.min
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
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers

/**
 * F7.18 · Mai trasparente: un popover nato da una sorgente, sopra righe di testo nero su bianco, con **tutto** il credito
 * del vetro già occupato, così che ogni cattura del pannello venga negata. Prima di F7.18 il pannello senza cattura era
 * la tinta sopra il niente, e la pagina sotto si leggeva parola per parola (il Tab S9, 24 novembre 2026). Ora il
 * materiale è pieno: sotto il pannello l'escursione della luminanza resta sotto la soglia di F8.8b (40 su 255).
 *
 * Il vetro è quello di ripiego (`Balanced`, verdetti rifiutati), come in [F8p8bPopoverPixelsTest]: col materiale `Liquid`
 * la finestra passa da `GlassBody8`, che Robolectric non compila. Il ramo `Liquid` del pieno (il corpo su un ingresso
 * pieno) si guarda sul tablet. Le immagini finiscono in `sample/build/f7p18-pixels/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F7p18DeniedGlassPixelsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var frameLog: Lazy<Boolean>
  private var previousFrameLog: Any? = null

  @Before fun start() {
    frameLog = ReflectionHelpers.getStaticField(GlassFrameDiagnostics::class.java, "enabled\$delegate")
    previousFrameLog = ReflectionHelpers.getField(frameLog, "_value")
    ReflectionHelpers.setField(frameLog, "_value", true)
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = false)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    ReflectionHelpers.setField(frameLog, "_value", previousFrameLog)
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

  private lateinit var view: android.view.View

  private fun tap(at: Offset) {
    val finger = MaterialFinger(view)
    finger.down(at)
    advance(48)
    finger.up(at)
  }

  private fun capture(name: String): Bitmap {
    val window = compose.activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnIdle { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    val dir = File("build/f7p18-pixels").apply { mkdirs() }
    File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return image
  }

  private fun luminance(image: Bitmap, x: Int, y: Int): Float {
    val pixel = image.getPixel(x, y)
    return 0.2126f * android.graphics.Color.red(pixel) + 0.7152f * android.graphics.Color.green(pixel) +
      0.0722f * android.graphics.Color.blue(pixel)
  }

  /** L'escursione della luminanza nel ritaglio: 99° meno 1° percentile, su 255, come in F8.8b. */
  private fun spread(image: Bitmap, area: Rect): Float {
    val values = ArrayList<Float>()
    val x0 = max(0, area.left.toInt()); val x1 = min(image.width - 1, area.right.toInt())
    val y0 = max(0, area.top.toInt()); val y1 = min(image.height - 1, area.bottom.toInt())
    for (y in y0..y1) for (x in x0..x1) values += luminance(image, x, y)
    values.sort()
    return values[(values.size * 0.99f).toInt().coerceAtMost(values.size - 1)] - values[(values.size * 0.01f).toInt()]
  }

  private var open by mutableStateOf(false)

  @Test fun `con la cattura negata il popover e' un pannello pieno e la pagina sotto non si legge`() {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        val page = rememberGlassBackdrop()
        val namespace = rememberFluidGlassNamespace()
        val source = remember(namespace) { FluidTransitionSource("prova", namespace) }
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassBackdropSource(page).background(Color.White)) {
              Column(Modifier.padding(top = 180.dp, start = 12.dp)) {
                repeat(30) {
                  BasicText(
                    "Scadenza 2026-11-25, vacanze 21/12 – 6/1, spegnimento 45 min",
                    style = TextStyle(fontSize = 17.sp, color = Color.Black),
                    maxLines = 1,
                  )
                }
              }
            }
            Box(Modifier.padding(top = 100.dp, start = 120.dp)) {
              Box(
                Modifier
                  .fluidTransitionSource("prova", namespace, FluidCapsuleShape)
                  .size(140.dp, 34.dp)
                  .background(Color(0xFFE0E0E4), FluidCapsuleShape)
                  .fluidPressable(onClick = { open = true })
                  .testTag("sorgente"),
              )
              FluidGlassModalPortal(
                visible = open,
                onDismissRequest = { open = false },
                source = source,
                compactAdaptation = FluidCompactAdaptation.Popover,
                mode = FluidPresentationMode.Parallel,
              ) {
                Box(Modifier.size(320.dp, 300.dp).testTag("contenuto"))
              }
            }
            FluidGlassModalHost(state = host, backdrop = page)
          }
        }
      }
    }
    advance(600)
    val bareArea = Rect(40f, 420f, 700f, 900f)
    val bare = spread(capture("pagina"), bareArea)

    // Tutto il credito del vetro occupato: ogni cattura del popover, a qualunque scala, viene negata.
    val resources = FluidGlassResources.get(compose.activity)
    val budget = resources.budget
    val glass = FluidMemoryCategory.GLASS
    val usage = budget.usage(glass)
    val held = if (usage.limitBytes > usage.usedBytes) budget.tryReserve(glass, usage.limitBytes - usage.usedBytes) else null
    val cursor = ShadowLog.getLogsForTag("FluidGlassFrame").size
    try {
      tap(compose.onNodeWithTag("sorgente", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center)
      advance(1600)
      val content = compose.onNodeWithTag("contenuto", useUnmergedTree = true)
      val area = content.fetchSemanticsNode().boundsInRoot.let { Rect(it.left + 24f, it.top + 24f, it.right - 24f, it.bottom - 24f) }
      // Robolectric disegna la finestra solo alla PixelCopy: al primo disegno il nodo aspetta la lettura della memoria
      // (un fotogramma senza cattura, com'era), al secondo chiede il credito e se lo vede negare.
      capture("negato-primo-disegno")
      advance(160)
      val under = spread(capture("negato-aperto"), area)
      val all = ShadowLog.getLogsForTag("FluidGlassFrame")
      val logs = all.drop(cursor)
      val kinds = logs.groupingBy { Regex("""kind=(\w+)""").find(it.msg)?.groupValues?.get(1) ?: "?" }.eachCount()
      println("F7p18 log: $cursor righe prima, ${all.size} dopo, tipi $kinds")
      logs.filter { " kind=glassBudget" in it.msg || " kind=capture " in it.msg || " kind=glassDeniedFill" in it.msg }.take(12)
        .forEach { println("F7p18LOG | " + it.msg.substringAfter("kind=").take(260)) }
      val denied = logs.count { " kind=glassBudgetDenied " in it.msg }
      val filled = logs.count { " kind=glassDeniedFill " in it.msg || " kind=glassSolid " in it.msg }
      val captured = logs.count { " kind=capture " in it.msg }
      println("F7p18 negato: pagina nuda ${"%.1f".format(bare)}, sotto il pannello ${"%.1f".format(under)}, " +
        "rifiuti $denied, pieni $filled, catture $captured, ritaglio $area, ${resources.usageLine()}")
      assertTrue("la pagina non ha testo da coprire: $bare", bare > 150f)
      assertTrue("il tetto non ha negato niente: la prova non prova il ripiego", denied > 0)
      assertEquals("con tutto il credito occupato non deve entrare nessuna cattura", 0, captured)
      assertTrue("il ripiego pieno non si è disegnato", filled > 0)
      assertTrue("sotto il popover senza cattura il testo della pagina si legge: escursione $under", under < SPREAD_LIMIT)
    } finally {
      held?.close()
      resources.refresh()
    }
  }

  private companion object {
    /** La soglia di F8.8b ([F8p8bPopoverPixelsTest]): 40 su 255, oltre le lettere sono ancora lettere. */
    const val SPREAD_LIMIT = 40f
  }
}
