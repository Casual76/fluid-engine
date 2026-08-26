package dev.antigravity.fluidengine.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.sample.playground.PlaygroundTab
import dev.antigravity.fluidengine.ui.fluid.FluidContextAction
import dev.antigravity.fluidengine.ui.fluid.FluidFoldingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFoldingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidGlassButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassIconButton
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalHost
import dev.antigravity.fluidengine.ui.fluid.FluidNotificationHost
import dev.antigravity.fluidengine.ui.fluid.FluidScrollToTopBus
import dev.antigravity.fluidengine.ui.fluid.FluidTabItem
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.ProvideFluidChrome
import dev.antigravity.fluidengine.ui.fluid.fluidGlassModalObscured
import dev.antigravity.fluidengine.ui.fluid.rememberFluidBarFold
import dev.antigravity.fluidengine.ui.fluid.rememberFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassModalHostState
import dev.antigravity.fluidengine.ui.fluid.rememberFluidNotificationHostState
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuButton
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuHost
import dev.antigravity.fluidengine.ui.fluidphysics.FluidMorphMenuState
import dev.antigravity.fluidengine.ui.fluidphysics.rememberFluidMorphMenuState
import dev.antigravity.fluidengine.ui.theme.FluidTheme

/**
 * Fluid Glass.
 *
 * Nata come galleria interna dell'engine, e ancora quello — ogni componente si guarda qui prima di
 * finire in un'app — ma adesso anche un'app del Pampa Store, perché la quinta scheda è diventata
 * una cosa che non esiste da nessun'altra parte: il Playground di **Fluid-physics**, il motore che
 * trasforma qualsiasi forma di vetro in qualsiasi altra.
 *
 *  | Materiale    | i quattro `GlassRole` sopra contenuto volutamente rumoroso                  |
 *  | Playground   | Fluid-physics: preset, disegno a mano, molle, livelli, tasti→menù→pop-up   |
 *  | Controlli    | interruttori, segmenti, pillole, pulsanti e campi sul canvas ambientale     |
 *  | Chrome       | modale in-root, menù contestuale, notifiche, e il confine di piattaforma    |
 *  | Impostazioni | canvas ambientale più contenuto in vetro, con versione e crediti            |
 */
class GalleryActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      FluidTheme(settings = remember { EngineSettings() }) {
        Gallery()
      }
    }
  }
}

private const val RouteMaterial = "material"
private const val RoutePlayground = "playground"
private const val RouteControls = "controls"
private const val RouteChrome = "chrome"
private const val RouteSettings = "settings"

private val Tabs = listOf(
  FluidTabItem(route = RouteMaterial, label = "Materiale", icon = Icons.Rounded.Home),
  FluidTabItem(route = RoutePlayground, label = "Fisica", icon = Icons.Rounded.Animation),
  FluidTabItem(route = RouteControls, label = "Controlli", icon = Icons.Rounded.GridView),
  FluidTabItem(route = RouteChrome, label = "Chrome", icon = Icons.Rounded.AutoAwesome),
  FluidTabItem(route = RouteSettings, label = "Impostazioni", icon = Icons.Rounded.Settings),
)

/**
 * The shell, in the shape a real app has one.
 *
 * The tab bar, the notification host and the modal host are all siblings of the content and all read
 * `chromeController.activeBackdrop` — which is exactly how ClasseViva and Pampa Store are wired. A
 * gallery that special-cased its own chrome would be testing an arrangement no app uses.
 */
@Composable
private fun Gallery() {
  var route by remember { mutableStateOf(RouteMaterial) }
  val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val chromeController = rememberFluidChromeController()
  val scrollToTop = remember { FluidScrollToTopBus() }
  val modalHost = rememberFluidGlassModalHostState()
  val notificationHost = rememberFluidNotificationHostState()
  val fallbackBackdrop = rememberGlassBackdrop()
  val backdrop = chromeController.activeBackdrop.value ?: fallbackBackdrop
  val morphMenu = rememberFluidMorphMenuState()

  val barFold = rememberFluidBarFold()

  CompositionLocalProvider(
    LocalFluidGlassModalHostState provides modalHost,
    LocalFluidNotificationHostState provides notificationHost,
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      ProvideFluidChrome(
        controller = chromeController,
        bottomInset = FluidFoldingTabBarDefaults.ContentInset,
        scrollToTop = scrollToTop,
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .nestedScroll(barFold.connection)
            .fluidGlassModalObscured(),
        ) {
          when (route) {
            RoutePlayground -> PlaygroundTab(bottomInset)
            RouteControls -> ControlsTab(bottomInset)
            RouteChrome -> ChromeTab(bottomInset)
            RouteSettings -> SettingsTab(bottomInset)
            else -> MaterialTab(bottomInset)
          }
        }
      }

      Box(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .navigationBarsPadding()
          .padding(
            horizontal = FluidFoldingTabBarDefaults.HorizontalMargin,
            vertical = FluidFoldingTabBarDefaults.BottomMargin,
          ),
      ) {
        FluidFoldingTabBar(
          items = Tabs,
          selectedRoute = route,
          onSelect = { route = it.route },
          onReselect = { scrollToTop.request() },
          onExpandRequest = barFold::unfold,
          backdrop = backdrop,
          fold = { barFold.progress.value },
        )
      }

      if (route == RouteMaterial) {
        GalleryFloatingControls(
          backdrop = backdrop,
          morphMenu = morphMenu,
          bottomInset = bottomInset,
          fold = { barFold.progress.value },
          modifier = Modifier.align(Alignment.BottomCenter),
        )
      }

      // Above the tab bar, and that placement is the whole reason the modal is in-root: a pop-up
      // that a floating navigation capsule can sit on top of is not a modal.
      FluidGlassModalHost(state = modalHost, backdrop = backdrop)
      // Il tasto che diventa il proprio menù: la superficie che si trasforma vive qui, alla
      // radice, per la stessa ragione dei modali.
      FluidMorphMenuHost(state = morphMenu, backdrop = backdrop)
      FluidNotificationHost(
        state = notificationHost,
        backdrop = backdrop,
        modifier = Modifier.align(Alignment.TopCenter),
      )
    }
  }
}

/**
 * Controls parked over the page rather than inside it.
 *
 * A glass button that lives in a list has an opaque row behind it and cannot show what it does; the
 * only honest place to look at one is floating over moving content, which is also the only place a
 * real app should use one.
 */
@Composable
private fun GalleryFloatingControls(
  backdrop: GlassBackdropState,
  morphMenu: FluidMorphMenuState,
  bottomInset: androidx.compose.ui.unit.Dp,
  fold: () -> Float,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val openPx = with(density) {
    (FluidFoldingTabBarDefaults.OpenHeight + FluidFoldingTabBarDefaults.BottomMargin + 18.dp).toPx()
  }
  val foldedPx = with(density) {
    (FluidFoldingTabBarDefaults.FoldedHeight + FluidFoldingTabBarDefaults.BottomMargin + 18.dp)
      .toPx()
  }
  CompositionLocalProvider(LocalGlassBackdrop provides backdrop) {
    Row(
      modifier = modifier
        // Riding the fold rather than parked at a fixed height. Left alone, these sat exactly where
        // the open bar used to be — so folding the bar left them hanging in the middle of nothing,
        // which is worse than either state on its own.
        .graphicsLayer {
          translationY = -androidx.compose.ui.util.lerp(openPx, foldedPx, fold().coerceIn(0f, 1f))
        }
        .padding(bottom = bottomInset)
        .padding(horizontal = 18.dp)
        .fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      FluidGlassButton(text = "Attiva", onClick = {}, backdrop = backdrop, selected = true)
      // Tenuto premuto, il tasto SI ESPANDE nel proprio menù: Fluid-physics al lavoro in un
      // componente vero — la capsula viaggia fino a essere il pannello, e torna.
      FluidMorphMenuButton(
        state = morphMenu,
        text = "Azione",
        onClick = {},
        backdrop = backdrop,
        actions = {
          listOf(
            FluidContextAction("Condividi", Icons.Rounded.Share) {},
            FluidContextAction("Aggiungi ai preferiti", Icons.Rounded.Star) {},
            FluidContextAction("Elimina", Icons.Rounded.Delete, destructive = true) {},
          )
        },
      )
      FluidGlassIconButton(onClick = {}, backdrop = backdrop) {
        Icon(
          imageVector = Icons.Rounded.AutoAwesome,
          contentDescription = "Effetto",
          modifier = Modifier.size(22.dp),
        )
      }
    }
  }
}
