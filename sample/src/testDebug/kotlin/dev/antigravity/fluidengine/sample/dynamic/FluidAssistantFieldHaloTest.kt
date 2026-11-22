@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.dynamic

import android.graphics.RenderNode
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.antigravity.fluidengine.aiui.chat.FluidAssistantFieldHalo
import dev.antigravity.fluidengine.aiui.chat.FluidComposerAction
import dev.antigravity.fluidengine.aiui.chat.FluidComposerState
import dev.antigravity.fluidengine.aiui.chat.fluidAssistantField
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidComposerHalo
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.field.glassBodyProbe
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowTrace
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * L'alone animato del composer dell'assistente (F6B.7, il giro chiesto da Alessio il 22 novembre 2026), con le sonde di
 * F6.2 e F6B.3a (`FluidGlassDiagnostics.totalCaptures` per le catture, `glassBodyProbe` per i corpi installati) e un
 * `draw()` della finestra a ogni fotogramma:
 * - **a riposo** (campo chiuso) l'alone non si ridisegna mai: non anima e non chiede fotogrammi;
 * - **a campo aperto** si ridisegna a ogni fotogramma (il giro di luce, il respiro) e **non ricattura** il vetro né ne
 *   reinstalla la catena: sta nella barra, fuori dalla cattura;
 * - richiuso, il moto si spegne e l'alone torna fermo;
 * - **col movimento ridotto** è fermo anche a campo aperto.
 * Il campo lo apre lo schermo (l'app non tiene `active`): l'alone lo sa dal campo (`fieldActiveChanged`).
 *
 * Misura anche **il costo del solo disegno dell'alone** per fotogramma (dietro e sopra il vetro, registrato in un
 * `RenderNode` con la grafica nativa di Robolectric sul PC): è il tempo di CPU della registrazione, non del GPU.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h720dp-mdpi")
class FluidAssistantFieldHaloTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  /** L'alone vero, con un contatore dei disegni e del loro tempo. */
  private class CountingHalo(val inner: FluidComposerHalo) : FluidComposerHalo {
    var draws = 0
    var nanos = 0L
    override fun DrawScope.drawBehindGlass(glass: RoundRect) {
      val start = System.nanoTime()
      with(inner) { drawBehindGlass(glass) }
      nanos += System.nanoTime() - start
      draws++
    }
    override fun DrawScope.drawOverGlass(glass: RoundRect) {
      val start = System.nanoTime()
      with(inner) { drawOverGlass(glass) }
      nanos += System.nanoTime() - start
    }
    override fun fieldActiveChanged(active: Boolean) = inner.fieldActiveChanged(active)
  }

  private var halo: CountingHalo? = null

  private fun scene(reduced: Boolean, open: MutableState<Boolean>) {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        CompositionLocalProvider(LocalFluidMotionPolicy provides if (reduced) FluidMotionPolicy(0f) else FluidMotionPolicy.Default) {
          val state = remember { FluidComposerState() }
          val spec = fluidAssistantField(state, FluidComposerAction.Voice, onSend = {}, requestMicPermission = false)
          val inner = spec.composer!!.halo!!
          val counted = remember(inner) { CountingHalo(inner).also { halo = it } }
          // L'apertura la tiene il test, come uno schermo: l'alone la sa solo dal campo.
          FluidScreen(title = "Assistente", search = spec.copy(composer = spec.composer!!.copy(halo = counted),
            active = open.value, onActiveChange = { open.value = it })) {
            items(40) { Text("Messaggio $it") }
          }
        }
      }
    }
  }

  private var frames = 0

  private fun frame() {
    compose.mainClock.advanceTimeByFrame()
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(8))
    Snapshot.sendApplyNotifications()
    // Robolectric tiene ogni sezione di `Trace` in una coda senza fondo: con centinaia di fotogrammi del vetro (che
    // traccia le sue sezioni) la coda riempie la memoria. La si svuota ogni tanto; non tocca niente di ciò che si misura.
    if (++frames % 30 == 0) ShadowTrace.getPreviousSections().clear()
  }

  private inline fun <T> drawing(block: (draw: () -> Unit) -> T): T {
    val view = compose.activity.window.decorView
    val node = RenderNode("F6B.7")
    node.setPosition(0, 0, view.width, view.height)
    val draw = {
      compose.runOnUiThread {
        val canvas = node.beginRecording()
        try { view.draw(canvas) } finally { node.endRecording() }
      }
    }
    try { return block(draw) } finally { node.discardDisplayList() }
  }

  private inline fun <T> probed(block: (bodies: () -> Int) -> T): T {
    val enabled = FluidGlassDiagnostics.enabled
    val warm = FluidGlassDiagnostics.warmUpEnabled
    FluidGlassDiagnostics.enabled = true
    FluidGlassDiagnostics.warmUpEnabled = false
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(true)
    val previousProbe = glassBodyProbe
    var bodies = 0
    glassBodyProbe = { _, _ -> bodies++ }
    try {
      return block { bodies }
    } finally {
      glassBodyProbe = previousProbe
      FluidGlassDiagnostics.resetForTesting()
      FluidGlassDiagnostics.enabled = enabled
      FluidGlassDiagnostics.warmUpEnabled = warm
    }
  }

  @Test fun `l'alone e' fermo a riposo, si anima a campo aperto e non ricattura il vetro`() = probed { bodies ->
    val open = mutableStateOf(false)
    scene(reduced = false, open = open)
    drawing { draw ->
      repeat(45) { frame(); draw() }
      val counted = halo!!
      val start = FluidGlassDiagnostics.totalCaptures
      assertTrue("controllo: il vetro del campo è in scena", bodies() >= 1)

      // 1. A riposo: niente si ridisegna, per 90 fotogrammi.
      val restDraws = counted.draws
      repeat(90) { frame(); draw() }
      assertEquals("a riposo l'alone non si ridisegna (non anima, non chiede fotogrammi)", restDraws, counted.draws)
      val rested = FluidGlassDiagnostics.totalCaptures

      // 2. Il campo si apre: l'alone lo sa, il moto entra in dissolvenza.
      compose.runOnUiThread { open.value = true }
      repeat(45) { frame(); draw() }
      val inner = counted.inner as FluidAssistantFieldHalo
      assertTrue("aperto, il moto è pieno: ${inner.motionNow}", inner.motionNow > 0.99f)
      val opened = FluidGlassDiagnostics.totalCaptures
      val openedBodies = bodies()

      // 3. Attivo: si ridisegna a ogni fotogramma, il tempo avanza, e il vetro non si ricattura.
      val beforeDraws = counted.draws
      val beforeNanos = counted.nanos
      val beforeTime = inner.clock.time
      repeat(90) { frame(); draw() }
      val activeDraws = counted.draws - beforeDraws
      val perFrameUs = (counted.nanos - beforeNanos) / 1000.0 / activeDraws.coerceAtLeast(1)
      val animated = FluidGlassDiagnostics.totalCaptures
      println("F6B.7 alone: catture riposo=${rested - start} apertura=${opened - rested} attivo=${animated - opened}; " +
        "corpi attivo=${bodies() - openedBodies}; disegni attivo=$activeDraws/90; costo del disegno dell'alone " +
        "%.1f µs per fotogramma (registrazione, CPU del PC); tempo dell'orologio %.2f → %.2f s".format(perFrameUs, beforeTime, inner.clock.time))
      assertTrue("attivo l'alone si ridisegna a ogni fotogramma: $activeDraws su 90", activeDraws >= 85)
      assertTrue("il tempo dell'alone avanza", inner.clock.time > beforeTime + 1f)
      assertEquals("l'alone animato non ricattura il vetro", opened, animated)
      assertEquals("l'alone animato non reinstalla la catena del vetro", openedBodies, bodies())
      assertTrue("il disegno dell'alone resta piccolo: %.1f µs".format(perFrameUs), perFrameUs < 2000.0)

      // 4. Richiuso: il moto esce in dissolvenza, poi l'alone è di nuovo fermo.
      compose.runOnUiThread { open.value = false }
      repeat(100) { frame(); draw() }
      assertTrue("richiuso, il moto è spento: ${inner.motionNow}", inner.motionNow < 0.01f)
      val closedDraws = counted.draws
      val closedTime = inner.clock.time
      repeat(90) { frame(); draw() }
      assertEquals("richiuso l'alone torna fermo", closedDraws, counted.draws)
      assertEquals(closedTime, inner.clock.time, 0f)
    }
  }

  /**
   * Il campo che cresce (anche senza composer né alone) si posa: la posa del suo vetro non si rifà senza fine. Prima di
   * questa prova `onPlaced` leggeva e scriveva lo stesso stato (`version++`) dentro la posa osservata, e Compose non
   * arrivava mai a riposo (milioni di pose, nessuna misura in più); con l'orologio fermo `setContent` aspetta il riposo.
   */
  @Test fun `il campo che cresce si posa`() {
    compose.mainClock.autoAdvance = false
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val text = remember { mutableStateOf("") }
        FluidScreen(title = "Messaggio", search = FluidSearchSpec.message(text.value, { text.value = it }, onSend = {})) {
          items(10) { Text("Riga $it") }
        }
      }
    }
    repeat(30) { frame() }
  }

  @Test fun `col movimento ridotto l'alone e' fermo anche a campo aperto`() = probed { _ ->
    val open = mutableStateOf(false)
    scene(reduced = true, open = open)
    drawing { draw ->
      repeat(30) { frame(); draw() }
      // Il campo si apre dopo la composizione (il cursore che lampeggia non è dell'alone).
      compose.runOnUiThread { open.value = true }
      repeat(45) { frame(); draw() }
      val counted = halo!!
      val inner = counted.inner as FluidAssistantFieldHalo
      assertEquals(0f, inner.motionNow, 0f)
      val draws = counted.draws
      val time = inner.clock.time
      val captures = FluidGlassDiagnostics.totalCaptures
      repeat(90) { frame(); draw() }
      assertEquals("col movimento ridotto l'alone non si ridisegna", draws, counted.draws)
      assertEquals(time, inner.clock.time, 0f)
      assertEquals(captures, FluidGlassDiagnostics.totalCaptures)
    }
  }
}
