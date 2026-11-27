package dev.antigravity.fluidengine.sample.novita

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidCelebration
import dev.antigravity.fluidengine.ui.fluid.FluidCelebrationState
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidSymbol
import dev.antigravity.fluidengine.ui.fluid.FluidSymbolEffect
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.fluidSymbolEffectWhile
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Star
import dev.antigravity.fluidengine.ui.fluid.icons.Sync
import dev.antigravity.fluidengine.ui.fluid.rememberFluidCelebrationState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * R2 (F8.5) · gli effetti continui dei simboli quando nessuno li vede (pagina coperta, ciclo di vita sotto STARTED) o
 * quando non muovono niente («Riduci movimento» su rotazione, oscillazione e rimbalzo): nessun giro di fotogrammi. E la
 * festa in un contenitore che non si piazza mai: finisce lo stesso. Come `F8p5FestaSimboliUiTest`, si contano i cambi di
 * stato applicati: ogni fotogramma di un'animazione ne scrive uno.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w412dp-h900dp-xhdpi")
class F8p5R2SymbolsTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private var changes = 0
  private var observer: ObserverHandle? = null

  @Before fun start() {
    FluidGlassDiagnostics.resetForTesting()
    FluidGlassDiagnostics.forceShaderVerdictsForTesting(accept = true)
    compose.mainClock.autoAdvance = false
    observer = Snapshot.registerApplyObserver { changed, _ -> if (changed.isNotEmpty()) changes++ }
  }

  @After fun stop() {
    observer?.dispose()
    FluidGlassDiagnostics.resetForTesting()
  }

  private fun advance(ms: Int) {
    repeat((ms + 15) / 16) {
      compose.mainClock.advanceTimeByFrame()
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
    }
  }

  private fun drawsDuring(ms: Int): Int {
    val before = changes
    advance(ms)
    return changes - before
  }

  private fun change(block: () -> Unit) {
    compose.runOnIdle {
      block()
      Snapshot.sendApplyNotifications()
    }
  }

  /** Un ciclo di vita che la prova muove a mano: quello di una pagina coperta (CREATED) che torna in cima (RESUMED). */
  private class PageLifecycle : LifecycleOwner {
    val registry = LifecycleRegistry.createUnsafe(this)
    override val lifecycle: Lifecycle get() = registry
  }

  private var reduced by mutableStateOf(false)

  private fun show(lifecycle: LifecycleOwner? = null, content: @Composable () -> Unit) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val policy = if (reduced) FluidMotionPolicy(durationScale = 0f) else FluidMotionPolicy(durationScale = 1f)
        CompositionLocalProvider(
          LocalFluidMotionPolicy provides policy,
          LocalLifecycleOwner provides (lifecycle ?: LocalLifecycleOwner.current),
        ) {
          Box(Modifier.fillMaxSize()) { content() }
        }
      }
    }
    advance(600)
  }

  // --- #45 · «Riduci movimento» -------------------------------------------------------------------------------------

  @Test fun `col movimento ridotto la rotazione continua del modificatore non chiede fotogrammi`() {
    reduced = true
    show { Icon(FluidIcons.Sync, null, Modifier.fluidSymbolEffectWhile(FluidSymbolEffect.Rotate, active = true)) }
    assertEquals(0, drawsDuring(1_000))
  }

  @Test fun `col movimento ridotto la rotazione continua del simbolo non chiede fotogrammi`() {
    reduced = true
    show { FluidSymbol(FluidIcons.Sync, "Sincronizza", effect = FluidSymbolEffect.Rotate, active = true) }
    assertEquals(0, drawsDuring(1_000))
  }

  @Test fun `spento il movimento ridotto la rotazione riparte, riacceso si ferma`() {
    reduced = true
    show {
      Icon(FluidIcons.Sync, null, Modifier.fluidSymbolEffectWhile(FluidSymbolEffect.Rotate, active = true))
      FluidSymbol(FluidIcons.Star, "Preferito", effect = FluidSymbolEffect.Wiggle, active = true)
    }
    assertEquals(0, drawsDuring(500))
    change { reduced = false }
    advance(48)
    assertTrue("di nuovo in moto", drawsDuring(1_000) > 20)
    change { reduced = true }
    advance(48)
    assertEquals(0, drawsDuring(500))
  }

  @Test fun `col movimento ridotto il respiro continua a schiarire`() {
    reduced = true
    show { FluidSymbol(FluidIcons.Star, "Preferito", effect = FluidSymbolEffect.Breathe, active = true) }
    assertTrue("le opacita' restano", drawsDuring(1_000) > 20)
  }

  // --- #45 · la pagina coperta ---------------------------------------------------------------------------------------

  @Test fun `a pagina coperta l'effetto continuo si ferma e torna in moto quando la pagina torna in cima`() {
    val page = PageLifecycle()
    compose.runOnIdle { page.registry.currentState = Lifecycle.State.CREATED }
    show(page) {
      Icon(FluidIcons.Sync, null, Modifier.fluidSymbolEffectWhile(FluidSymbolEffect.Pulse, active = true))
      FluidSymbol(FluidIcons.Star, "Preferito", effect = FluidSymbolEffect.Breathe, active = true)
    }
    assertEquals("coperta: niente fotogrammi", 0, drawsDuring(800))
    change { page.registry.currentState = Lifecycle.State.RESUMED }
    advance(48)
    assertTrue("in cima: di nuovo in moto", drawsDuring(1_000) > 20)
    change { page.registry.currentState = Lifecycle.State.CREATED }
    advance(48)
    assertEquals("coperta di nuovo", 0, drawsDuring(800))
  }

  // --- Il minore della festa ----------------------------------------------------------------------------------------

  @Test fun `la festa in un contenitore a misura zero finisce lo stesso`() {
    lateinit var state: FluidCelebrationState
    show {
      state = rememberFluidCelebrationState()
      FluidCelebration(state, Modifier.size(0.dp))
    }
    change { state.launch() }
    advance(2_000)
    assertFalse("la festa non resta in corso per sempre", state.isCelebrating)
    assertEquals(0, drawsDuring(500))
  }

  @Test fun `la festa ferma in un contenitore a misura zero finisce lo stesso`() {
    reduced = true
    lateinit var state: FluidCelebrationState
    show {
      state = rememberFluidCelebrationState()
      FluidCelebration(state, Modifier.size(0.dp))
    }
    change { state.launch() }
    advance(3_000)
    assertFalse(state.isCelebrating)
  }
}
