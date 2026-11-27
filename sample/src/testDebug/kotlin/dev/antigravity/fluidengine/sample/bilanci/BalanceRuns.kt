package dev.antigravity.fluidengine.sample.bilanci

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.material.MaterialFinger
import dev.antigravity.fluidengine.sample.measure.MeasureScenario
import dev.antigravity.fluidengine.sample.measure.ScenarioActions
import dev.antigravity.fluidengine.sample.measure.ScenarioScene
import dev.antigravity.fluidengine.sample.novita.NewElementScene
import dev.antigravity.fluidengine.sample.novita.NewElementsPageAt
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidChromePages
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * F7.28 · I due copioni di misura dei bilanci, uguali per tutti gli elementi: così una riga del registro vuol dire la
 * stessa cosa per ogni elemento.
 *
 * - **Una scena di «Misure»** ([measureScenario]), come `GlassNodeCountTest`: entrata (48 fotogrammi), per ogni tratto
 *   dello scenario il moto (il tratto vero, guidato dalla scena; stabile la seconda metà) e 20 fotogrammi di pausa, la
 *   posa alla fine (due campioni separati da 2 s), l'uscita (20 fotogrammi senza la scena), il rilascio (1 s dopo).
 * - **Un elemento nuovo** ([measureNewElement]): la scena della pagina «Elementi nuovi» com'è nella galleria; entrata
 *   (40 fotogrammi più 1 s), l'assestamento ([settle]), posa, moto (il dito trascina dal centro in su per 24 fotogrammi, 12 px ciascuno, senza
 *   lancio; stabili i fotogrammi 6–21), assestamento non misurato, uscita e rilascio come sopra.
 */
internal object BalanceRuns {
  /** La pausa fra due tratti, come in `GlassNodeCountTest` (20 fotogrammi disegnati, non misurati). */
  private const val RestFrames = 20

  /**
   * Il palco di `GlassNodeCountTest`: il tema, la chrome, l'host dei modali e, sotto, il fondo azzurro con la sua
   * sorgente (se [source]); [content] al centro, con l'accesso al fondo.
   */
  @Composable
  private fun Stage(source: Boolean, content: @Composable (GlassBackdropState) -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val chrome = rememberFluidChromeController()
      val host = rememberFluidGlassModalHostState()
      val backdrop = rememberGlassBackdrop()
      CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
        ProvideFluidChrome(chrome, 0.dp, remember { FluidScrollToTopBus() }) {
          Box(Modifier.fillMaxSize()) {
            if (source) Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color(0xFF7FA7D9)))
            Box(Modifier.fillMaxSize().fluidChromePages()) {
              Box(Modifier.align(Alignment.Center)) { content(backdrop) }
            }
            FluidGlassModalHost(host, chrome.activeBackdrop.value ?: backdrop)
          }
        }
      }
    }
  }

  /** Un componente da solo, fermo sul fondo azzurro: entrata, posa, uscita, rilascio (il moto non c'è). */
  fun measureComponent(harness: BalanceHarness, label: String, margin: Int, content: @Composable (GlassBackdropState) -> Unit): GlassBalanceMeter {
    val meter = harness.meter(label, margin)
    meter.begin("entrata")
    harness.show { Stage(source = true, content = content) }
    repeat(48) { harness.frame(meter) }
    meter.end()
    repeat(RestFrames) { harness.frame() }
    harness.quiet(meter)
    leave(harness, meter)
    return meter
  }

  @Composable
  private fun ScenarioStage(scenario: MeasureScenario, actions: ScenarioActions, scope: (CoroutineScope) -> Unit) {
    FluidTheme(settings = remember { EngineSettings() }) {
      scope(rememberCoroutineScope())
      val chrome = rememberFluidChromeController()
      val host = rememberFluidGlassModalHostState()
      val backdrop = rememberGlassBackdrop()
      CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
        ProvideFluidChrome(chrome, 0.dp, remember { FluidScrollToTopBus() }) {
          Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().glassBackdropSource(backdrop).background(Color(0xFF7FA7D9)))
            Box(Modifier.fillMaxSize().fluidChromePages()) {
              Box(Modifier.align(Alignment.Center)) { ScenarioScene(scenario, actions, 0.dp) }
            }
            FluidGlassModalHost(host, chrome.activeBackdrop.value ?: backdrop)
          }
        }
      }
    }
  }

  fun measureScenario(harness: BalanceHarness, scenario: MeasureScenario, margin: Int): GlassBalanceMeter {
    val actions = ScenarioActions()
    var scope: CoroutineScope? = null
    val meter = harness.meter(scenario.key, margin)
    meter.begin("entrata")
    harness.show { ScenarioStage(scenario, actions) { scope = it } }
    repeat(48) { harness.frame(meter) }
    meter.end()
    check(actions.ready) { "${scenario.key}: la scena di Misure non è pronta" }
    repeat(RestFrames) { harness.frame() }
    for (phase in scenario.phases) {
      val action = checkNotNull(actions.action(phase.name)) { "${scenario.key}/${phase.name}: azione mancante" }
      harness.runOnUi { checkNotNull(scope).launch { action() } }
      val frames = ((phase.journeyMillis + BalanceHarness.FrameMillis - 1) / BalanceHarness.FrameMillis).toInt()
      // Il moto stabile è la seconda metà del tratto, senza gli ultimi due fotogrammi: la prima metà ha la nascita di ciò
      // che si muove (il menu al distacco, il foglio che si prende), lecita una volta, con le sue fabbriche nel totale.
      meter.begin("moto:${phase.name}", stableFrames = frames / 2 until frames - 2)
      repeat(frames) { harness.frame(meter) }
      meter.end()
      repeat(RestFrames) { harness.frame() }
    }
    check(actions.failure == null) { "${scenario.key}: ${actions.failure}" }
    harness.quiet(meter)
    leave(harness, meter)
    return meter
  }

  @Composable
  private fun ElementStage(scene: NewElementScene) {
    FluidTheme(settings = remember { EngineSettings() }) {
      val host = rememberFluidGlassModalHostState()
      CompositionLocalProvider(LocalFluidGlassModalHostState provides host) {
        Box(Modifier.fillMaxSize()) {
          NewElementsPageAt(onBack = {}, initial = scene.id)
          FluidGlassModalHost(state = host, backdrop = null)
        }
      }
    }
  }

  /**
   * [ready]: per una scena che arriva dopo un lavoro su fili veri, quando è pronta (F7.29). Il banco la aspetta
   * nell'entrata, dopo i primi 40 fotogrammi e prima del secondo d'attesa ([BalanceHarness.awaitReady]): l'entrata conta
   * sempre il suo arrivo, e posa, moto e uscita misurano sempre la scena intera.
   */
  fun measureNewElement(
    harness: BalanceHarness,
    scene: NewElementScene,
    margin: Int,
    ready: ((List<SemanticsNode>) -> Boolean)? = null,
  ): GlassBalanceMeter {
    val meter = harness.meter(scene.id, margin)
    meter.begin("entrata")
    harness.show { ElementStage(scene) }
    repeat(40) { harness.frame(meter) }
    if (ready != null) harness.awaitReady(meter, scene.id, ready)
    harness.idle(1_000, drawEvery = 8, meter = meter)
    meter.end()
    settle(harness, meter)
    harness.quiet(meter)
    val view = harness.view()
    val finger = MaterialFinger(view)
    val start = Offset(view.width / 2f, view.height * 0.6f)
    harness.runOnUi { finger.down(start) }
    meter.begin("moto", stableFrames = 6..21)
    for (i in 1..24) {
      harness.runOnUi { finger.move(start - Offset(0f, 12f * i)) }
      harness.frame(meter)
    }
    meter.end()
    harness.runOnUi { finger.up(start - Offset(0f, 12f * 24)) }
    harness.idle(1_200, drawEvery = 4)
    leave(harness, meter)
    return meter
  }

  /**
   * L'assestamento prima della posa, non misurato: alcune scene finiscono di arrivare con un lavoro in sottofondo che va
   * in tempo vero (il testo letto da Tesseract, la sincronia simulata, la fotocamera finta dello scanner), mentre
   * l'orologio della prova corre. Si aspetta, mezzo secondo alla volta e al più 6 s, finché due finestre di fila non
   * cambiano niente (nodi, layer, byte, catture, fabbriche): da lì la posa è una posa.
   */
  private fun settle(harness: BalanceHarness, meter: GlassBalanceMeter) {
    var previous = meter.sample()
    repeat(12) {
      Thread.sleep(40)
      harness.idle(500, drawEvery = 8)
      val now = meter.sample()
      if (now == previous) return
      previous = now
    }
  }

  private fun leave(harness: BalanceHarness, meter: GlassBalanceMeter) {
    harness.clear()
    meter.begin("uscita")
    repeat(20) { harness.frame(meter) }
    meter.end()
    harness.idle(1_000, drawEvery = 8)
    meter.begin("rilascio")
    harness.frame(meter)
    meter.end()
  }
}
