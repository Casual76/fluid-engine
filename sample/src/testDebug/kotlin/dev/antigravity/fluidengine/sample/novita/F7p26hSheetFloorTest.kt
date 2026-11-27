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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.FluidRadius
import dev.antigravity.fluidengine.ui.fluid.FluidSheetDetent
import dev.antigravity.fluidengine.ui.fluid.GlassRenderQuality
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassFrameDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.GlassBodyKernel
import dev.antigravity.fluidengine.ui.glass.field.GlassFloorInflatePx
import dev.antigravity.fluidengine.ui.glass.field.glassBodyProbe
import dev.antigravity.fluidengine.ui.glass.quality.android.FluidGlassResources
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.io.File
import java.time.Duration
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
 * F7.26h · **Il foglio di vetro al gradino più basso** (la finestra del Tab S9 del 27 novembre, «Foglio trascinato»).
 *
 * 1. **Gli angoli.** Il corpo del foglio si costruisce alla taglia e alla densità della cattura, e il foglio ha gli angoli
 *    in pixel: al gradino più basso il raggio restava quello dello schermo, e a un ottavo di scala il corpo era una
 *    capsula, un ovale tinto dentro il rettangolo ritagliato a piena risoluzione. La prova legge il corpo che il gradino
 *    più basso installa (`glassBodyProbe`) e vuole il raggio del foglio, 38 dp, alla densità della cattura.
 * 2. **Il credito che torna a metà trascinamento.** Il foglio trascinato dal dito (e la sua molla) è moto, come lo
 *    scorrimento: il gradino più basso si lascia a gesto finito (D7), e il cambio di misura del foglio in moto resta nel
 *    gradino invece di chiedere il registro (D6). Fino a F7.26g il foglio usciva dal gradino a metà gesto.
 *
 * Il vetro è `Liquid` (verdetti accettati): il corpo passa da `GlassBody1`, che Robolectric compila a sdk 35. Le immagini
 * finiscono in `sample/build/f7p26h-pixels/`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F7p26hSheetFloorTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private lateinit var frameLog: Lazy<Boolean>
  private var previousFrameLog: Any? = null
  private val previousProbe = glassBodyProbe

  @Before fun start() {
    frameLog = ReflectionHelpers.getStaticField(GlassFrameDiagnostics::class.java, "enabled\$delegate")
    previousFrameLog = ReflectionHelpers.getField(frameLog, "_value")
    ReflectionHelpers.setField(frameLog, "_value", true)
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
  }

  @After fun stop() {
    glassBodyProbe = previousProbe
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

  /** Robolectric disegna la finestra solo alla PixelCopy: ogni cattura è un disegno del vetro. */
  private fun capture(name: String? = null): Bitmap {
    val window = compose.activity.window
    val image = Bitmap.createBitmap(window.decorView.width, window.decorView.height, Bitmap.Config.ARGB_8888)
    var result = -1
    compose.runOnIdle { PixelCopy.request(window, image, { result = it }, Handler(Looper.getMainLooper())) }
    shadowOf(Looper.getMainLooper()).idle()
    assertEquals("PixelCopy", PixelCopy.SUCCESS, result)
    if (name != null) {
      val dir = File("build/f7p26h-pixels").apply { mkdirs() }
      File(dir, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    return image
  }

  private var open by mutableStateOf(false)

  private fun scene() {
    compose.setContent {
      view = LocalView.current
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        val page = rememberGlassBackdrop()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassBackdropSource(page).background(Color.White)) {
              Column(Modifier.padding(top = 80.dp, start = 12.dp)) {
                repeat(40) {
                  BasicText(
                    "Scadenza 2026-11-25, vacanze 21/12 – 6/1, spegnimento 45 min",
                    style = TextStyle(fontSize = 17.sp, color = Color.Black),
                    maxLines = 1,
                  )
                }
              }
            }
            FluidGlassSheet(
              visible = open,
              onDismissRequest = { open = false },
              detents = setOf(FluidSheetDetent.Medium, FluidSheetDetent.Large),
              paneTitle = "Luoghi vicini",
            ) {
              Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("corpo")) {
                repeat(8) { BasicText("Luogo ${it + 1} · a ${it + 2} minuti", Modifier.padding(vertical = 10.dp)) }
              }
            }
            FluidGlassModalHost(state = host, backdrop = page)
          }
        }
      }
    }
    advance(600)
  }

  private fun frameLogs() = ShadowLog.getLogsForTag("FluidGlassFrame")

  private fun count(from: Int, kind: String) = frameLogs().drop(from).count { " kind=$kind " in it.msg }

  /** Tutto il credito del vetro occupato, poi il foglio che si apre e arriva al gradino più basso. */
  private fun <T> inFloor(block: (held: AutoCloseable?, resources: FluidGlassResources, cursor: Int) -> T): T {
    val resources = FluidGlassResources.get(compose.activity)
    val budget = resources.budget
    val glass = FluidMemoryCategory.GLASS
    val usage = budget.usage(glass)
    val held = if (usage.limitBytes > usage.usedBytes) budget.tryReserve(glass, usage.limitBytes - usage.usedBytes) else null
    val cursor = frameLogs().size
    try {
      open = true
      advance(1600)
      capture()
      advance(160)
      capture()
      advance(160)
      capture("gradino-aperto")
      assertTrue("il foglio non è arrivato al gradino più basso", count(cursor, "glassFloor") > 0)
      return block(held, resources, cursor)
    } finally {
      held?.close()
      resources.refresh()
    }
  }

  private class FloorBody(val size: Size, val density: Float, val rects: FloatArray, val radii: FloatArray)

  @Test fun `al gradino piu basso il corpo del foglio ha gli angoli del foglio e non e' una capsula`() {
    val bodies = ArrayList<FloorBody>()
    glassBodyProbe = { scope, inputs ->
      if ((scope as? GlassRenderQuality)?.glassmorphism == true && inputs.kernel == GlassBodyKernel.One && inputs.body.count == 1) {
        bodies += FloorBody(scope.size, scope.density, inputs.body.rects.copyOf(4), inputs.body.radii.copyOf(4))
      }
    }
    scene()
    inFloor { _, _, _ ->
      // Il foglio è il vetro più grande della scena.
      val sheet = bodies.maxByOrNull { it.rects[2] * it.rects[3] }
      checkNotNull(sheet) { "il gradino più basso non ha installato nessun corpo a una lastra" }
      val scale = sheet.density / compose.activity.resources.displayMetrics.density
      val expected = FluidRadius.Sheet.value * sheet.density + GlassFloorInflatePx
      val half = minOf(sheet.rects[2], sheet.rects[3])
      println("F7p26h angoli: ${bodies.size} corpi, foglio ${sheet.size} a scala $scale, mezzi lati ${sheet.rects[2]} x " +
        "${sheet.rects[3]}, raggi ${sheet.radii.joinToString()}, atteso in alto $expected")
      assertTrue("la cattura del gradino più basso è a scala piena: la prova non prova niente (scala $scale)", scale < 0.75f)
      assertEquals("il raggio in alto del foglio alla scala della cattura", expected, sheet.radii[0], 0.5f)
      assertTrue("il corpo del foglio è una capsula: raggio ${sheet.radii[0]} contro mezzo lato $half", sheet.radii[0] < half * 0.5f)
    }
  }

  @Test fun `il foglio trascinato lascia il gradino piu basso solo a gesto finito`() {
    scene()
    inFloor { held, resources, _ ->
      val body = compose.onNodeWithTag("corpo", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
      val finger = MaterialFinger(view)
      var at = Offset(body.center.x, body.top + 60f)
      finger.down(at)
      advance(32)
      // Il dito tira su il foglio: cambia misura a ogni passo, e a ogni passo il vetro si disegna.
      repeat(6) {
        at = Offset(at.x, at.y - 28f)
        finger.move(at)
        advance(32)
        capture()
      }
      // A metà gesto il credito torna.
      held?.close()
      resources.refresh()
      val back = frameLogs().size
      repeat(30) {
        at = Offset(at.x, at.y - 10f)
        finger.move(at)
        advance(48)
        capture()
      }
      capture("trascinato-col-credito")
      val reservedDuring = count(back, "glassBudgetReserve")
      val retries = count(back, "glassBudgetRetry")
      // Il dito si alza: la molla porta il foglio al detent, poi la riprova del credito trova il vetro fermo.
      val lifted = frameLogs().size
      finger.up(at)
      repeat(16) {
        advance(200)
        capture()
      }
      capture("dopo-il-gesto")
      val reservedAfter = count(lifted, "glassBudgetReserve")
      println("F7p26h trascinamento: riprove col dito giù $retries, prenotazioni nel registro col dito giù $reservedDuring, " +
        "a gesto finito $reservedAfter, ${resources.usageLine()}")
      frameLogs().drop(back).filter { " kind=glassBudget" in it.msg || " kind=glassFloor" in it.msg }.take(30)
        .forEach { println("F7p26hLOG | " + it.msg.substringAfter("kind=").take(220)) }
      assertEquals("a metà trascinamento il foglio ha lasciato il gradino più basso per il registro", 0, reservedDuring)
      assertTrue("a gesto finito il foglio non ha lasciato il gradino più basso", reservedAfter > 0)
    }
  }
}
