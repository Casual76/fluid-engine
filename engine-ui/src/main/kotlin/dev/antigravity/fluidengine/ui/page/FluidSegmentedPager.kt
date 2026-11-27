package dev.antigravity.fluidengine.ui.page

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBar
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidFloatingTabBarInlineBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidTabBarPuckDriver
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidTabBarPuckDriver
import dev.antigravity.fluidengine.ui.fluid.glassBackdropSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidFloatingTabBarScrollConnection
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop
import kotlinx.coroutines.launch

/**
 * Lo stato di un [FluidSegmentedPager]: il pager ([pagerState]) e dove sta la pillola ([indicatorPosition]).
 *
 * @param pagerState il pager di Compose: l'app ne legge `currentPage` e `settledPage`, o lo fa scorrere.
 */
@Stable
class FluidSegmentedPagerState(val pagerState: PagerState) {
  internal val driver = FluidTabBarPuckDriver()

  /** Vero mentre le pagine si muovono per un dito (trascinate o lanciate), non per un tocco sul segmentato. */
  internal var userDriven by mutableStateOf(false)

  /** La pagina scelta: quella su cui il pager si e' fermato. */
  val selectedPage: Int get() = pagerState.settledPage

  /**
   * Dove sta la pillola adesso, in segmenti (0 il primo, 1,5 a meta' fra il secondo e il terzo): la posizione animata
   * della goccia di vetro, che segue il pager mentre il dito lo trascina. Prima che la barra sia composta, la pagina.
   */
  val indicatorPosition: Float
    get() = driver.puckValue().takeUnless { it.isNaN() } ?: (pagerState.currentPage + pagerState.currentPageOffsetFraction)

  /** Porta il pager a [page] con l'animazione delle pagine; la pillola ci va da sola. */
  suspend fun animateScrollToPage(page: Int) {
    userDriven = false
    pagerState.animateScrollToPage(page)
  }
}

/** Uno stato di [FluidSegmentedPager] che sopravvive alla rotazione (la pagina la salva il pager). */
@Composable
fun rememberFluidSegmentedPagerState(initialPage: Int = 0, pageCount: () -> Int): FluidSegmentedPagerState {
  val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = initialPage, pageCount = pageCount)
  return remember(pager) { FluidSegmentedPagerState(pager) }
}

/** Le misure del [FluidSegmentedPager]. */
object FluidSegmentedPagerDefaults {
  /**
   * L'altezza della capsula: 48 dp. Il segmentato flottante di iOS 26 sopra le pagine (le schede di App Store, «Giorno
   * / Settimana / Mese» di Salute) sta fra il `UISegmentedControl` grande (36 pt) e la tab bar (62,6 pt); 48 e' la
   * riga del tocco dell'engine per un campo nella barra (F6B.8b). A occhio sulle schermate, 2026-11-24, fiducia media.
   */
  val Height: Dp = 48.dp

  /** La larghezza massima di un segmento: 112 dp (le schede si stringono uguali se lo schermo e' stretto). A occhio. */
  val SegmentWidth: Dp = 112.dp

  /** Lo spazio fra il bordo alto (sotto la barra di stato) e la capsula: 8 dp. */
  val TopMargin: Dp = 8.dp

  /**
   * Quanto le pagine devono lasciare libero in alto **sotto la barra di stato** perche' il loro contenuto parta sotto la
   * capsula: 64 dp. Il margine che [FluidSegmentedPager] da' alle pagine aggiunge la barra di stato (visto sul tablet il
   * 24 novembre: senza, la foto in cima alla pagina finiva sotto la capsula).
   */
  val ContentInset: Dp = Height + TopMargin + 8.dp
}

/**
 * Un pager orizzontale con sopra un controllo segmentato flottante, sincronizzati (la scheda `pager-segmentato`).
 *
 * **La selezione e' la pillola della tab bar** (nota di Alessio: «non tanto come l'anteprima ma più tipo la pillola di
 * navigazione»). Il segmentato **e'** un [FluidFloatingTabBar] a schede di sole parole, con il suo vetro, la sua goccia e
 * la sua fisica: non e' rifatto. Quello che aggiunge e' il collegamento col pager:
 *
 * - **il dito che trascina le pagine trascina la pillola**: mentre le pagine si muovono per un dito la goccia si preme e
 *   diventa lente, come sotto il dito sulla barra, segue la frazione esatta del pager con la molla rigida del
 *   trascinamento, si allunga con la velocita' e tira la capsula di qualche dp nel verso del moto; quando le pagine si
 *   posano si rilascia;
 * - **un tocco su un segmento** muove la goccia come sulla tab bar e porta il pager li' con l'animazione delle pagine;
 * - **la goccia trascinata sulla capsula** sceglie al rilascio, con un tic per segmento, e il pager la segue.
 *
 * Le pagine vicine a quella in vista restano composte ([beyondViewportPageCount], di serie 1): la pillola non si ferma
 * a meta' viaggio aspettando una pagina che si costruisce (la regola della tab bar). Il contenuto di ogni pagina scorre
 * **sotto** la capsula e la capsula lo rifrange: [page] riceve il margine da lasciare in alto.
 *
 * **TalkBack**: ogni segmento e' un tasto col suo titolo, e quello scelto dice «selezionato». **Movimento ridotto**: la goccia arriva
 * al suo posto senza viaggio e senza lente, e il pager cambia pagina con la sua animazione di sistema.
 *
 * @param segments i titoli dei segmenti, uno per pagina. Vuota (i dati non ancora arrivati) non mostra niente, né pagine
 *   né capsula, e `selectedPage` resta 0: lo stato vuoto lo mostra l'app. Se si accorcia mentre il pager è su una pagina
 *   che non c'è più, il pager torna sull'ultima che c'è.
 * @param state il pager e la pillola ([rememberFluidSegmentedPagerState]).
 * @param backdrop cio' che la capsula rifrange; null: le pagine stesse (di serie).
 * @param page il contenuto della pagina [index]; `contentPadding` e' il margine in alto da lasciare alla capsula,
 *   barra di stato compresa.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FluidSegmentedPager(
  segments: List<String>,
  modifier: Modifier = Modifier,
  state: FluidSegmentedPagerState = rememberFluidSegmentedPagerState(pageCount = { segments.size }),
  backdrop: GlassBackdropState? = null,
  beyondViewportPageCount: Int = 1,
  page: @Composable (index: Int, contentPadding: PaddingValues) -> Unit,
) {
  val pager = state.pagerState
  val scope = rememberCoroutineScope()
  val pagesBackdrop = backdrop ?: rememberGlassBackdrop()
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion

  // Chi muove le pagine: un dito (la pillola lo segue) o il segmentato (la pillola va da sola).
  LaunchedEffect(pager) {
    pager.interactionSource.interactions.collect { interaction ->
      if (interaction is DragInteraction.Start) state.userDriven = true
    }
  }
  LaunchedEffect(pager) {
    snapshotFlow { pager.isScrollInProgress }.collect { moving -> if (!moving) state.userDriven = false }
  }
  val driver = state.driver
  // La capsula sta sotto la barra di stato: il margine delle pagine la conta.
  val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  driver.position = { pager.currentPage + pager.currentPageOffsetFraction }
  driver.active = { state.userDriven }

  Box(modifier.fillMaxSize()) {
    HorizontalPager(
      state = pager,
      beyondViewportPageCount = beyondViewportPageCount,
      modifier = Modifier.fillMaxSize().then(if (backdrop == null) Modifier.glassBackdropSource(pagesBackdrop) else Modifier),
    ) { index ->
      page(index, PaddingValues(top = statusTop + FluidSegmentedPagerDefaults.ContentInset))
    }
    val connection = rememberFluidFloatingTabBarScrollConnection(inlineBehavior = FluidFloatingTabBarInlineBehavior.Never)
    val count = segments.size
    CompositionLocalProvider(LocalFluidTabBarPuckDriver provides driver) {
      Box(
        Modifier
          .align(Alignment.TopCenter)
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(
            horizontal = FluidFloatingTabBarDefaults.HorizontalMargin,
            vertical = FluidSegmentedPagerDefaults.TopMargin,
          )
          .wrapContentWidth(Alignment.CenterHorizontally),
      ) {
        FluidFloatingTabBar(
          selectedTabKey = state.selectedPage,
          scrollConnection = connection,
          backdrop = pagesBackdrop,
          sizes = FluidFloatingTabBarDefaults.sizes(
            tabWidth = FluidSegmentedPagerDefaults.SegmentWidth,
            barHeight = FluidSegmentedPagerDefaults.Height,
          ),
        ) {
          for (i in 0 until count) {
            tab(
              key = i,
              title = {
                Text(
                  text = segments[i],
                  style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                  textAlign = TextAlign.Center,
                  maxLines = 1,
                  // TalkBack dice «selezionato» sul segmento scelto (come le schede della tab bar a elenco).
                  modifier = Modifier.semantics { selected = i == state.selectedPage },
                )
              },
              icon = {},
              onClick = {
                if (pager.currentPage != i || pager.currentPageOffsetFraction != 0f) {
                  scope.launch { if (reducedMotion) pager.scrollToPage(i) else state.animateScrollToPage(i) }
                }
              },
            )
          }
        }
      }
    }
  }
}
