package dev.antigravity.fluidengine.sample

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPresentation
import dev.antigravity.fluidengine.ui.fluid.FluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.FluidSheetScaffold
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * F8.24 · Il foglio «a misura del contenuto» (`FluidGlassModalPortal(presentation = Sheet)`) con un contenuto più alto
 * della finestra, la cosa che sembrava non fermarsi mai nella prova di «Segnala un problema».
 *
 * Tempo e memoria fissi: l'orologio delle prove è fermo e lo si fa avanzare a mano, un fotogramma da 16 ms alla volta,
 * per un numero di fotogrammi deciso qui; nessuna attesa che dipende dal foglio. Due prove:
 * - il foglio lungo **si ferma**: dopo l'apertura, altri 90 fotogrammi non compongono, non misurano e non disegnano niente;
 * - lo scorrimento che usano TalkBack e `performScrollTo` (l'azione `ScrollBy`) **è animato**: con l'orologio fermo non
 *   avanza, e avanza appena l'orologio va. È la causa vera dell'attesa senza fine: `performScrollTo` con
 *   `mainClock.autoAdvance = false` aspetta uno scorrimento che non arriva mai, e intanto la coda delle tracce di
 *   Robolectric (`ShadowTrace`) cresce fino a finire la memoria.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class FluidPortalTallSheetTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  private var compositions = 0
  private var layouts = 0
  private var draws = 0

  @Before fun start() {
    compose.mainClock.autoAdvance = false
  }

  /** [frames] fotogrammi da 16 ms, uno alla volta. */
  private fun frames(frames: Int) {
    repeat(frames) {
      compose.mainClock.advanceTimeBy(16)
      shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16))
      Snapshot.sendApplyNotifications()
    }
  }

  private fun tallSheet(reduced: Boolean) {
    compose.setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        val host = rememberFluidGlassModalHostState()
        CompositionLocalProvider(
          LocalFluidMotionPolicy provides FluidMotionPolicy(durationScale = if (reduced) 0f else 1f),
          LocalFluidGlassModalHostState provides host,
        ) {
          Box(Modifier.fillMaxSize()) {
            Text("Pagina")
            FluidGlassModalPortal(
              visible = true,
              onDismissRequest = {},
              presentation = FluidGlassModalPresentation.Sheet,
              paneTitle = "Un foglio lungo",
            ) {
              FluidSheetScaffold(title = "Un foglio lungo") { TallContent() }
            }
            FluidGlassModalHost(state = host, backdrop = null)
          }
        }
      }
    }
  }

  /** 40 righe da 48 dp: 1 920 dp, più del doppio della finestra (900 dp). */
  @Composable
  private fun TallContent() {
    SideEffect { compositions++ }
    Column(
      Modifier
        .layout { measurable, constraints ->
          layouts++
          val placeable = measurable.measure(constraints)
          layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
        .drawBehind { draws++ },
    ) {
      repeat(40) { i -> Text("Riga $i", Modifier.height(48.dp).testTag("riga-$i")) }
    }
  }

  @Test
  fun ilFoglioLungoSiFerma() {
    tallSheet(reduced = false)
    frames(90) // l'apertura col movimento pieno: un secondo e mezzo
    val opened = Triple(compositions, layouts, draws)
    assertTrue("il foglio si è aperto: $opened", opened.first > 0 && opened.second > 0)
    frames(90)
    assertEquals("altri 90 fotogrammi: niente da comporre, misurare o disegnare", opened, Triple(compositions, layouts, draws))
  }

  @Test
  fun ilFoglioLungoSiFermaColMovimentoRidotto() {
    tallSheet(reduced = true)
    frames(30)
    val opened = Triple(compositions, layouts, draws)
    frames(90)
    assertEquals(opened, Triple(compositions, layouts, draws))
  }

  @Test
  fun loScorrimentoDiTalkBackAspettaLOrologio() {
    tallSheet(reduced = false)
    frames(90)
    val last = compose.onNodeWithTag("riga-39")
    val before = last.fetchSemanticsNode().positionInRoot.y
    // Lo scorrimento più vicino alla riga: quello del contenuto del foglio.
    compose.onAllNodes(hasScrollAction() and hasAnyDescendant(hasTestTag("riga-39"))).onLast()
      .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 600f) }
    // Con l'orologio fermo il looper gira ma lo scorrimento non avanza.
    repeat(30) {
      shadowOf(Looper.getMainLooper()).idle()
      Snapshot.sendApplyNotifications()
    }
    assertEquals("con l'orologio fermo lo scorrimento resta dov'è", before, last.fetchSemanticsNode().positionInRoot.y, 0.5f)
    // Appena l'orologio va, scorre.
    frames(60)
    val after = last.fetchSemanticsNode().positionInRoot.y
    assertTrue("con l'orologio che va scorre: $before → $after", before - after > 300f)
  }
}
