package dev.antigravity.fluidengine.sample

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.antigravity.fluidengine.ui.page.FluidPageHost
import dev.antigravity.fluidengine.ui.page.FluidPageTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class FluidPagePreparationTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

  @Test fun laPrimaRigaSiComponePrimaDelPushESiPosaSoltantoAlPush() {
    val stack = mutableStateOf(listOf("A"))
    val hint = mutableStateOf<String?>("B")
    val rows = mutableMapOf<String, Any>()
    val owners = mutableMapOf<String, Lifecycle>()
    val placed = mutableSetOf<String>()
    val disposed = mutableListOf<String>()
    compose.setContent {
      FluidPageHost(stack = stack.value, precomposePage = hint.value, onBack = {}) { page ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
          LazyColumn(Modifier.fillMaxSize()) {
            items(100) { index ->
              if (index == 0) {
                val instance = remember { Any() }
                val owner = LocalLifecycleOwner.current
                DisposableEffect(page) {
                  rows[page] = instance
                  owners[page] = owner.lifecycle
                  onDispose { disposed += page }
                }
              }
              BasicText("Riga $index", Modifier.height(64.dp).onPlaced { placed += page })
            }
          }
        }
      }
    }
    compose.waitForIdle()
    val first = compose.runOnIdle {
      assertTrue("riga lazy già composta", rows.containsKey("B"))
      assertEquals(Lifecycle.State.CREATED, owners.getValue("B").currentState)
      assertFalse("la pagina preparata non si posa", "B" in placed)
      hint.value = "C"
      rows.getValue("B")
    }
    compose.waitForIdle()
    compose.runOnIdle {
      assertEquals(listOf("B"), disposed)
      assertEquals(Lifecycle.State.DESTROYED, owners.getValue("B").currentState)
      assertFalse("C" in placed)
      hint.value = "B"
    }
    compose.waitForIdle()
    val promoted = compose.runOnIdle {
      assertNotSame(first, rows.getValue("B"))
      stack.value = listOf("A","B")
      hint.value = null
      rows.getValue("B")
    }
    compose.waitForIdle()
    compose.runOnIdle {
      assertSame(promoted, rows.getValue("B"))
      assertTrue("B" in placed)
      assertEquals(1, disposed.count { it == "B" })
      assertEquals(Lifecycle.State.RESUMED, owners.getValue("B").currentState)
    }
  }

  @Test fun laDestinazioneExpandNonSiComponePerUnaPrevisione() {
    val composed = mutableSetOf<String>()
    compose.setContent {
      FluidPageHost(stack = listOf("A"), onBack = {}, precomposePage = "Z",
        transition = { FluidPageTransition.Expand }) { page ->
        DisposableEffect(page) { composed += page; onDispose {} }
        BasicText(page)
      }
    }
    compose.waitForIdle()
    compose.runOnIdle { assertEquals(setOf("A"), composed) }
  }
}
