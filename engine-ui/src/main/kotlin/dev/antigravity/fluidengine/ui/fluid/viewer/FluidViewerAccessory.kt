package dev.antigravity.fluidengine.ui.fluid.viewer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.LocalFluidCanvasBackdrop
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalGlassBackdrop

// F8.17 · Gli agganci del testo nelle immagini (engine-viewer-ocr), senza che engine-ui sappia leggere niente: il posto
// dello schermo per l'accessorio di una pagina (il tasto di Testo attivo di VisionKit, in basso a destra sopra la fila
// dei comandi) e le capacità facoltative di una sorgente di pagine (il disegno sopra ogni pagina e la ricerca). Come
// FluidAudioSpeed e FluidPageNotes, un contratto che chi non lo implementa non vede.

/**
 * F8.17 · Un accessorio della pagina del visore, **nello spazio dello schermo**: non segue lo zoom né il pager, sta in
 * basso a destra sopra la fila dei comandi (come il tasto di Testo attivo e le azioni rapide di VisionKit,
 * `ImageAnalysisInteraction`) e si ritira con le barre al tocco. Lo chiama il contenuto di una pagina (dentro
 * `FluidViewerContent.Image.draw` o una pagina `Custom`); vale solo per la pagina ferma. Fuori dal visore non fa niente.
 */
@Composable
fun FluidViewerPageAccessory(content: @Composable BoxScope.() -> Unit) {
  val page = LocalViewerPage.current ?: return
  val accessories = LocalViewerAccessories.current ?: return
  val current = rememberUpdatedState(content)
  val stable: @Composable BoxScope.() -> Unit = remember(current) { { current.value(this) } }
  DisposableEffect(accessories, page.index, stable) {
    accessories.set(page.index, stable)
    onDispose { accessories.remove(page.index, stable) }
  }
}

/** Gli accessori delle pagine composte, uno per pagina; il telaio mostra quello della pagina corrente. */
@Stable
internal class ViewerAccessories {
  private val blocks = mutableStateMapOf<Int, @Composable BoxScope.() -> Unit>()

  fun of(index: Int): (@Composable BoxScope.() -> Unit)? = blocks[index]

  fun set(index: Int, block: @Composable BoxScope.() -> Unit) {
    blocks[index] = block
  }

  fun remove(index: Int, block: @Composable BoxScope.() -> Unit) {
    if (blocks[index] === block) blocks.remove(index)
  }
}

internal val LocalViewerAccessories = staticCompositionLocalOf<ViewerAccessories?> { null }

/**
 * L'accessorio della pagina [index], in basso a destra a 14 dp dal bordo e 12 dp sopra la fila dei comandi. Si ritira
 * con le barre (la stessa sfumatura del telaio, 200 ms per andare e 350 per tornare) e col movimento ridotto cambia
 * subito.
 *
 * F8.17b: l'accessorio riceve la larghezza dello schermo meno 14 dp per lato (e meno le barre di sistema ai lati, in
 * orizzontale), così una riga che si adatta sa quanto posto ha; e il vetro che ci sta dentro campiona [backdrop], la
 * pagina del visore, come i tasti del telaio: senza, un tasto di vetro prendeva il fondale d'ambiente dello schermo (o
 * nessuno) e lasciava la foto nitida sotto di sé.
 */
@Composable
internal fun BoxScope.ViewerAccessory(
  accessories: ViewerAccessories,
  index: Int,
  visible: Boolean,
  backdrop: GlassBackdropState,
) {
  val block = accessories.of(index) ?: return
  val motion = LocalFluidMotionPolicy.current
  val shown by animateFloatAsState(if (visible) 1f else 0f,
    if (motion.reducedMotion) tween(0) else tween(if (visible) ViewerChromeMotion.ShowMillis else ViewerChromeMotion.HideMillis),
    label = "ViewerAccessory")
  if (shown <= 0f && !visible) return
  Box(Modifier.align(Alignment.BottomEnd)
    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
    .padding(start = 14.dp, end = 14.dp, bottom = viewerBottomCapsuleTop() + 12.dp)
    .graphicsLayer { alpha = shown }, contentAlignment = Alignment.BottomEnd) {
    CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalFluidCanvasBackdrop provides null) { block() }
  }
}

/**
 * F8.17 · Una capacità facoltativa di [FluidPageSource]: un disegno sopra ogni pagina del preset delle pagine, nelle
 * coordinate della pagina (la pagina intera è il riquadro del composable, che cresce col pizzico). Il testo nelle
 * immagini la usa per le righe riconosciute di un PDF scansito.
 */
interface FluidPageOverlays {
  /** Il disegno sopra la pagina [index]; [active] è vero mentre la pagina è in vista e ferma. */
  @Composable
  fun PageOverlay(index: Int, active: Boolean)
}

/**
 * F8.17 · Una capacità facoltativa di [FluidPageSource]: la ricerca nel testo delle pagine, quando l'app non ne dà una
 * ([FluidViewerInfo.searchPages] vince). Con lei il preset delle pagine mostra «Trova» anche senza il pannello info: un
 * PDF scansito si cerca nelle parole riconosciute.
 */
interface FluidPageTextSearch {
  /** Le pagine e i riquadri (0..1) di [query]. */
  suspend fun searchPages(query: String): List<FluidViewerPageMatch>
}
