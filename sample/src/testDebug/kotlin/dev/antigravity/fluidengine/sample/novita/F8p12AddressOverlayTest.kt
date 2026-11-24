package dev.antigravity.fluidengine.sample.novita

import android.content.Intent
import android.net.Uri
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.test.platform.app.InstrumentationRegistry
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.foundation.FluidAddressPage
import dev.antigravity.fluidengine.foundation.FluidAddressParameters
import dev.antigravity.fluidengine.foundation.FluidAddressRequest
import dev.antigravity.fluidengine.foundation.FluidAddressTable
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.FluidGlassSheet
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.fluidContextMenuAnchor
import dev.antigravity.fluidengine.ui.fluid.rememberFluidContextMenu
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.navigation.FluidAddressNavigation
import dev.antigravity.fluidengine.ui.navigation.FluidNavHost
import dev.antigravity.fluidengine.ui.navigation.composable
import dev.antigravity.fluidengine.ui.navigation.newFluidAddress
import dev.antigravity.fluidengine.ui.navigation.rememberFluidNavController
import dev.antigravity.fluidengine.ui.theme.FluidTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h900dp-xhdpi")
class F8p12AddressOverlayTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private lateinit var nav: NavHostController
  private lateinit var modals: FluidGlassModalHostState
  private var editorBacks = 0
  private var picks = 0
  private var allowSheetDismiss = true
  private var sheetDismissals = 0
  private enum class Surface { Sheet, Menu }

  private val routes = listOf("home", "precedente", "quaderno", "nota")
  private val destinationStack = listOf("home", "quaderno", "nota")
  private val addresses = FluidAddressNavigation(FluidAddressTable("app", listOf(
    FluidAddressPage<String, String>(String::class, "pagina",
      { it.segments.singleOrNull()?.takeIf(routes::contains) }, { FluidAddressParameters(listOf(it)) },
      parent = { when (it) { "home" -> null; "nota" -> "quaderno"; else -> "home" } }, public = true),
  )), { it }, "mancante", "accesso")

  @Composable
  private fun Overlay(surface: Surface, sheetBody: @Composable () -> Unit = {}) {
    when (surface) {
      Surface.Sheet -> {
        var open by remember { mutableStateOf(false) }
        Text("Apri", Modifier.clickable { open = true })
        FluidGlassSheet(visible = open, onDismissRequest = {
          sheetDismissals++
          if (allowSheetDismiss) open = false
        }) { Text("Azioni aperte"); sheetBody() }
      }
      Surface.Menu -> {
        val menu = rememberFluidContextMenu(actions = { listOf(FluidContextAction("Azioni aperte") { picks++ }) })
        Text("Apri", Modifier.fluidContextMenuAnchor(menu).clickable { menu.open() })
      }
    }
  }

  private fun show(
    surface: Surface, global: Boolean, editor: Boolean,
    restore: StateRestorationTester? = null, sheetBody: @Composable () -> Unit = {},
  ) {
    val tree: @Composable () -> Unit = {
      FluidTheme(settings = remember { EngineSettings() }) {
        nav = rememberFluidNavController()
        modals = rememberFluidGlassModalHostState()
        val backdrop = rememberGlassBackdrop()
        CompositionLocalProvider(LocalFluidGlassModalHostState provides modals) {
          Box(Modifier.fillMaxSize()) {
            FluidNavHost(nav, "home", addresses, null, Modifier.fillMaxSize()) {
              for (route in routes) composable(route) {
                Text(route)
                if (route == "precedente" && !global) Overlay(surface)
                if (route == "nota" && editor) {
                  var dirty by remember { mutableStateOf(true) }
                  BackHandler(enabled = dirty) { dirty = false; editorBacks++ }
                }
              }
            }
            if (global) Overlay(surface, sheetBody)
            FluidGlassModalHost(modals, backdrop)
          }
        }
      }
    }
    if (restore == null) compose.setContent(tree) else restore.setContent(tree)
    compose.runOnIdle {
      compose.activity.addOnNewIntentListener { intent ->
        val request = requireNotNull(compose.activity.newFluidAddress(intent))
        assertTrue(addresses.open(nav, request))
      }
      nav.navigate("precedente")
    }
    compose.onNodeWithText("Apri").performClick()
    compose.onNodeWithText("Azioni aperte").assertExists()
    compose.runOnIdle { assertTrue(modals.isPresenting) }
  }

  private fun stack() = nav.currentBackStack.value.filter { it.destination !is NavGraph }.map { it.destination.route }

  private fun deliver(global: Boolean) {
    compose.mainClock.autoAdvance = false
    try {
      compose.runOnUiThread {
        if (global) nav.addOnDestinationChangedListener { _, dest, _ ->
          if (dest.route == "nota") assertFalse("il modale ha ancora pixel all'arrivo", modals.isOnScreen)
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("app://pagina/nota"))
        InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(compose.activity, intent)
        assertEquals(if (global) listOf("home", "precedente") else destinationStack, stack())
      }
      if (global) {
        var sawExit = false
        val states = mutableSetOf<String>()
        for (frame in 1..180) {
          compose.mainClock.advanceTimeByFrame()
          compose.waitForIdle()
          val arrived = compose.runOnUiThread {
            val route = nav.currentDestination?.route
            states += "$route / aperto=${modals.isPresenting} / visibile=${modals.isOnScreen}"
            if (modals.isOnScreen) assertEquals("precedente", route)
            if (modals.isOnScreen && !modals.isPresenting) sawExit = true
            route == "nota"
          }
          if (arrived) break
        }
        assertTrue("manca il normale ritorno: $states", sawExit)
      }
    } finally { compose.mainClock.autoAdvance = true }
    compose.waitForIdle()
    compose.runOnIdle { assertEquals(BackState(0, false, destinationStack), readBackState()) }
  }

  private fun back() {
    compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
    compose.waitForIdle()
  }

  private data class BackState(val editor: Int, val modal: Boolean, val stack: List<String?>)

  private fun readBackState() = BackState(editorBacks, modals.isPresenting, stack())

  private fun gesture(commit: Boolean) {
    val dispatcher = compose.activity.onBackPressedDispatcher
    compose.runOnIdle {
      dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 350f, 0f, BackEventCompat.EDGE_LEFT))
      dispatcher.dispatchOnBackProgressed(BackEventCompat(260f, 350f, .5f, BackEventCompat.EDGE_LEFT))
    }
    compose.mainClock.advanceTimeByFrame()
    compose.runOnUiThread {
      if (commit) dispatcher.onBackPressed() else dispatcher.dispatchOnBackCancelled()
    }
    compose.waitForIdle()
  }

  private fun check(surface: Surface, global: Boolean, editor: Boolean = false) {
    show(surface, global, editor)
    deliver(global)
    gesture(commit = false)
    compose.runOnIdle {
      assertEquals(destinationStack, stack())
      assertFalse(modals.isPresenting)
      assertEquals(0, editorBacks)
    }
    if (editor) {
      back()
      compose.runOnIdle {
        assertEquals(1, editorBacks)
        assertEquals(destinationStack, stack())
      }
    }
    gesture(commit = true)
    compose.runOnIdle { assertEquals(listOf("home", "quaderno"), stack()) }
    back()
    compose.runOnIdle {
      assertEquals(listOf("home"), stack())
      assertEquals(0, picks)
    }
  }

  @Test fun foglioDiPagina() = check(Surface.Sheet, global = false)
  @Test fun menuDiPagina() = check(Surface.Menu, global = false)
  @Test fun foglioGlobale() = check(Surface.Sheet, global = true)
  @Test fun menuGlobale() = check(Surface.Menu, global = true)
  @Test fun foglioGlobaleConEditorInArrivo() = check(Surface.Sheet, global = true, editor = true)
  @Test fun menuGlobaleConEditorInArrivo() = check(Surface.Menu, global = true, editor = true)

  private fun directNavigation(surface: Surface) {
    show(surface, global = true, editor = true)
    compose.runOnIdle { nav.navigate("nota") }
    compose.waitForIdle()
    val ordinary = listOf("home", "precedente", "nota")
    gesture(commit = false)
    compose.runOnIdle { assertEquals(BackState(0, true, ordinary), readBackState()) }
    if (surface == Surface.Menu) gesture(commit = true) else back()
    compose.runOnIdle {
      assertEquals("il primo back chiude il modale", BackState(0, false, ordinary), readBackState())
    }
    back()
    compose.runOnIdle { assertEquals("il secondo back torna all'editor", BackState(1, false, ordinary), readBackState()) }
    back()
    compose.runOnIdle { assertEquals(listOf("home", "precedente"), stack()) }
  }

  @Test fun controlloFoglioConNavigateOrdinario() = directNavigation(Surface.Sheet)
  @Test fun controlloMenuConNavigateOrdinario() = directNavigation(Surface.Menu)

  @Test fun ilContenutoDelFoglioMantieneIlProprioIndietro() {
    var selectionsClosed = 0
    show(Surface.Sheet, global = true, editor = true) {
      var selected by remember { mutableStateOf(true) }
      BackHandler(enabled = selected) {
        selected = false
        selectionsClosed++
      }
    }
    compose.runOnIdle { nav.navigate("nota") }
    compose.waitForIdle()
    back()
    compose.runOnIdle {
      assertEquals(1, selectionsClosed)
      assertTrue(modals.isPresenting)
      assertEquals(0, editorBacks)
    }
    back()
    compose.runOnIdle { assertFalse(modals.isPresenting); assertEquals(0, editorBacks) }
  }

  private fun request(page: String) = addresses.open(nav, FluidAddressRequest("app://pagina/$page"))

  @Test fun linkRifiutatoLasciaIlFoglioApertoEUltimoValidoVinceDuranteLUscita() {
    show(Surface.Sheet, global = true, editor = false)
    compose.runOnIdle { assertFalse(request("inesistente")) }
    compose.runOnIdle { assertTrue(modals.isPresenting); assertEquals(0, sheetDismissals) }
    var sawNote = false
    compose.mainClock.autoAdvance = false
    try {
      compose.runOnUiThread {
        nav.addOnDestinationChangedListener { _, dest, _ -> if (dest.route == "nota") sawNote = true }
        assertTrue(request("nota"))
      }
      // L'intent arriva dal main looper Android e il foglio si aggiorna sul frame Compose.
      for (frame in 1..180) {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        if (compose.runOnUiThread { !modals.isPresenting }) break
      }
      compose.runOnUiThread {
        assertFalse(modals.isPresenting)
        assertTrue(modals.isOnScreen)
        assertTrue(request("quaderno"))
        assertFalse(request("inesistente"))
      }
    } finally { compose.mainClock.autoAdvance = true }
    compose.runOnIdle {
      assertEquals(listOf("home", "quaderno"), stack())
      assertFalse(sawNote)
      assertEquals(1, sheetDismissals)
    }
  }

  @Test fun ilLinkInAttesaTornaConIlNuovoController() {
    val restore = StateRestorationTester(compose)
    show(Surface.Sheet, global = true, editor = false, restore = restore)
    allowSheetDismiss = false
    val before = nav
    compose.runOnIdle { assertTrue(request("nota")) }
    compose.runOnIdle {
      assertEquals(1, sheetDismissals)
      assertEquals(listOf("home", "precedente"), stack())
    }
    restore.emulateSavedInstanceStateRestore()
    compose.runOnIdle {
      assertNotSame(before, nav)
      assertFalse(modals.isOnScreen)
      assertEquals(destinationStack, stack())
    }
  }

  @Test fun linkChiudePrimaIlMenuInternoPoiIlFoglioCheLoContiene() {
    show(Surface.Sheet, global = true, editor = false) {
      val inner = rememberFluidContextMenu(actions = { listOf(FluidContextAction("Voce interna") { picks++ }) })
      Text("Apri menu interno", Modifier.fluidContextMenuAnchor(inner).clickable { inner.open() })
    }
    compose.onNodeWithText("Apri menu interno").performClick()
    compose.onNodeWithText("Voce interna").assertExists()
    compose.runOnIdle {
      nav.addOnDestinationChangedListener { _, dest, _ ->
        if (dest.route == "nota") assertFalse(modals.isOnScreen)
      }
      assertTrue(request("nota"))
      assertEquals("precedente", nav.currentDestination?.route)
    }
    compose.runOnIdle {
      assertEquals(1, sheetDismissals)
      assertFalse(modals.isOnScreen)
      assertEquals(destinationStack, stack())
      assertEquals(0, picks)
    }
  }
}
