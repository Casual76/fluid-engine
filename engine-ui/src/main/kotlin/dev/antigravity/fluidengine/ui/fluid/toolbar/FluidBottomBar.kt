package dev.antigravity.fluidengine.ui.fluid.toolbar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingCapsuleDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidBottomInset
import dev.antigravity.fluidengine.ui.fluid.LocalFluidChromeController
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassQuality
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop

/** Uno spaziatore da solo non apre una barra. Una posizione esplicita prevale su quella del gruppo. */
internal fun FluidToolbarEntry.declaresBottomAction(parentBottom: Boolean): Boolean {
  fun bottom(placement: FluidToolbarPlacement) = placement == FluidToolbarPlacement.BottomBar ||
    placement == FluidToolbarPlacement.Automatic && parentBottom
  return when (this) {
    is FluidToolbarSpacerEntry -> false
    is FluidToolbarButtonEntry -> bottom(placement)
    is FluidToolbarGroupEntry -> children.any { it.declaresBottomAction(bottom(placement)) }
  }
}

internal fun fluidBottomBarMetrics(): FluidToolbarMetrics = fluidToolbarMetrics().copy(
  buttonWidthDp = FluidFloatingCapsuleDefaults.ActionSize.value,
  itemPitchDp = FluidFloatingCapsuleDefaults.ActionSize.value,
)

/**
 * Dove sta la barra degli strumenti in basso delle pagine (F6B.15): **una misura sola**, condivisa dalla barra in basso
 * dichiarata ([FluidBottomBar]: Posta, la selezione che non prende la tab bar, «Invia»…), dal campo che occupa la riga in
 * basso (`FluidToolbarSearchOverlay`), dallo spazio che la lista le lascia ([fluidBottomBarExtraInset]) e, da F6C.20,
 * dalla barra in basso dei visori. La tab bar non la usa: resta com'è ([FluidFloatingTabBarDefaults.BottomMargin]).
 */
internal object FluidBottomBarPlacement {
  /**
   * Dal bordo basso dello schermo al fondo del vetro dei tasti: **28 dp**. È iOS 26: il tasto tondo in basso alto 47,8 pt
   * ha il fondo a 28 pt dal bordo dello schermo (±1,5 pt; WWDC25 219, `f9170`, misurato in
   * `docs/liquid-glass/ricerca/icone-e-misure.md`), cioè il centro a ~52 pt, la stessa quota del centro della tab bar
   * (20,9 + 62,6 / 2). Fiducia media. Prima la barra stava 16 dp sopra la barra di sistema: con i gesti (24 dp) il fondo a
   * 40 dp e il centro a 64, 12 dp più su di iOS. Scelta di Alessio del 23 novembre 2026, pomeriggio: «i tasti nella parte
   * inferiore dello schermo stanno davvero tanto in alto, troppo».
   */
  val ScreenEdgeDistance: Dp = 28.dp

  /**
   * Il minimo fra la barra di sistema e il vetro, quando la barra di sistema è più alta della distanza (la navigazione a
   * tre tasti, 48 dp): 4 dp, quanto resta sopra il gesto da 24 dp. A occhio, 23 novembre 2026.
   */
  val NavigationClearance: Dp = 4.dp
}

/**
 * Lo spazio fra la barra di sistema ([systemInset], già tolto da `navigationBarsPadding`) e il fondo del vetro della
 * barra in basso, dato lo spazio che l'app riserva alla sua navigazione ([appInset], [LocalFluidBottomInset]: la tab bar).
 * Con la tab bar la barra le sta sopra di [FluidFloatingTabBarDefaults.Spacing], come prima; senza, il fondo del vetro sta
 * a [FluidBottomBarPlacement.ScreenEdgeDistance] dal bordo dello schermo, e mai a meno di
 * [FluidBottomBarPlacement.NavigationClearance] dalla barra di sistema. Pura.
 */
internal fun fluidBottomBarLift(appInset: Dp, systemInset: Dp): Dp =
  if (appInset > 0.dp) {
    appInset + FluidFloatingTabBarDefaults.Spacing
  } else {
    maxOf(FluidBottomBarPlacement.ScreenEdgeDistance - systemInset, FluidBottomBarPlacement.NavigationClearance)
  }

/**
 * [fluidBottomBarLift] con gli inset di adesso: quelli che posano la barra (`navigationBarsPadding`). I visori
 * ([legacy]: `FluidToolbarDeclaration.legacyBottomLift`) tengono il margine di prima, [fluidBottomBarOffset], fino
 * all'unione con F6C.20.
 */
@Composable
internal fun fluidBottomBarLift(legacy: Boolean = false): Dp =
  if (legacy) fluidBottomBarOffset(LocalFluidBottomInset.current)
  else fluidBottomBarLift(LocalFluidBottomInset.current, WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())

/** Quanto la lista aggiunge in fondo per la barra in basso, oltre alla barra di sistema e allo spazio dell'app. Pura. */
internal fun fluidBottomBarExtraInset(appInset: Dp, systemInset: Dp): Dp =
  fluidBottomBarLift(appInset, systemInset) + FluidFloatingCapsuleDefaults.ActionSize - appInset

/**
 * Il margine di prima (fino a F6B.14): 16 dp sopra la barra di sistema, o la tab bar più 8. Lo leggono ancora i visori
 * (`viewer/`, che F6C.20 porta a [FluidBottomBarPlacement]); la barra in basso delle pagine usa [fluidBottomBarLift].
 */
internal fun fluidBottomBarOffset(navigation: Dp): Dp =
  if (navigation > 0.dp) navigation + FluidFloatingTabBarDefaults.Spacing else FluidFloatingTabBarDefaults.BottomMargin

/** Lo spazio di prima per la lista, col margine di [fluidBottomBarOffset]: lo leggono ancora i visori. */
internal fun fluidBottomBarExtraInset(navigation: Dp): Dp =
  fluidBottomBarOffset(navigation) + FluidFloatingCapsuleDefaults.ActionSize - navigation

/**
 * Una sede per schermata o per host. Il modello, i viaggi e i bersagli sono quelli della barra dichiarata.
 *
 * Senza tab bar (F6B.1) è questa barra a cambiare azioni quando la pagina entra in una selezione: la dichiarazione nuova
 * è un cambio di stato dello stesso contenitore, con lo scambio sfocato e la larghezza che scorre della barra in alto
 * che cambia contesto (R4 B2). Una selezione che prende il posto della tab bar
 * ([FluidSelectionBarPlacement.ReplacingTabBar]) non si disegna qui quando la tab bar dice di disegnarla lei
 * ([dev.antigravity.fluidengine.ui.fluid.FluidChromeController.claimBottomReplacement], F6B.2): un vetro solo.
 */
@Composable
internal fun FluidBottomBar(
  declaration: FluidToolbarDeclaration?,
  backdrop: GlassBackdropState,
  minimize: FluidToolbarMinimizeProgress?,
  modifier: Modifier = Modifier,
) {
  val chrome = LocalFluidChromeController.current
  val elected = chrome?.bottomReplacement?.value
  val shellDraws = chrome?.bottomReplacementClaimed == true && (declaration?.bottomReplacesTabBar == true ||
    elected?.replacesTabBar == true && elected.declaration === declaration)
  // Anche il figlio uscente di AnimatedVisibility leggerebbe la dichiarazione nuova:
  // una pagina con comandi in basso disegnerebbe la selezione una seconda volta.
  if (shellDraws) return
  val present = declaration?.hasBottomBar == true
  val visibility = remember { MutableTransitionState(present) }
  visibility.targetState = present
  if (!present && visibility.isIdle) return
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val appInset = LocalFluidBottomInset.current
  val systemInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val bottom = if (declaration?.viewerLayout == true && appInset == 0.dp) maxOf(30.dp - systemInset, 4.dp)
    else if (declaration?.legacyBottomLift == true) fluidBottomBarOffset(appInset) // i visori, fino a F6C.20
    else fluidBottomBarLift(appInset, systemInset)
  AnimatedVisibility(
    visibleState = visibility,
    modifier = modifier.navigationBarsPadding().padding(bottom = bottom),
    enter = FluidFloatingCapsuleDefaults.enter(reduced),
    exit = FluidFloatingCapsuleDefaults.exit(reduced),
  ) {
    CompositionLocalProvider(
      LocalGlassBackdrop provides backdrop,
      LocalFluidCanvasBackdrop provides null,
      LocalFluidGlassQuality provides null,
      LocalFluidToolbarMinimize provides minimize,
    ) {
      FluidToolbarBar(declaration ?: EmptyBottomDeclaration, bar = FluidToolbarBar.Bottom)
    }
  }
}

private val EmptyBottomDeclaration = buildFluidToolbar(null) {}
