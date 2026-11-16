package dev.antigravity.fluidengine.aiui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassAppearance

// Il vetro dei pezzi dell'assistente e il patto dell'inchiostro (4.0, piano «iOS in Android», F3.2b).
//
// Il patto pubblico dell'engine e' il contenitore (`FluidGlassContainer`): un vetro gira chiaro/scuro con lo sfondo solo
// se sta in un contenitore, che gli da' una sonda e da' al contenuto l'inchiostro del lato. I pezzi piccoli
// dell'assistente (tasti tondi, stop, chip, la capsula dell'ascolto) da soli girano come i tasti dell'engine: si fanno un
// contenitore con un pezzo solo. Sopra un pannello di vetro restano del lato del pannello, come oggi.

/**
 * Vero dentro un **pannello di vetro che non gira**: la card e il dock dell'assistente, la capsula del composer e la
 * barra di scrittura lo dicono al loro contenuto. Lì i pezzi piccoli (`FluidGlassRoundButton`, `FluidStopPill`,
 * `FluidChipPill`, `FluidListeningHint`) non si fanno un contenitore loro: sono vetro annidato e prendono il lato del
 * pannello, che oggi è quello del tema. Se girassero da soli, sopra una foto scura un tasto diventerebbe scuro su una
 * card rimasta chiara.
 *
 * Un'app che posa questi pezzi su un **suo** pannello di vetro (un `glassSurface`, che non gira) lo dice allo stesso modo:
 *
 *     CompositionLocalProvider(LocalFluidAssistantPanel provides true) { FluidChipPill(…) }
 *
 * Non serve dentro un `FluidGlassContainer` né dentro un vetro adattivo dell'engine (foglio, pop-up): lì i pezzi lo
 * capiscono da soli e diventano pezzi di quel contenitore, o vetro annidato di quel vetro.
 */
val LocalFluidAssistantPanel: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/**
 * I colori di serie dei pezzi dell'assistente. [Default] vuol dire «**l'inchiostro del lato**»: dentro un contenitore che
 * è girato sul lato scuro (o chiaro) il colore è quello del lato, sul lato del tema è `onSurface`, com'era. Un colore
 * scritto dall'app resta il suo, su tutti e due i lati: chi colora a mano e vuole seguire il vetro legge
 * `LocalFluidGlassAppearance`.
 */
object FluidAssistantInk {
  /** Il valore di serie di un `tint` o di un `color`: l'inchiostro del lato. */
  val Default: Color = Color.Unspecified
}

/**
 * Il colore che un pezzo disegna: quello chiesto dall'app, o [sideInk] (l'inchiostro del lato, letto **dentro** il
 * contenitore) se l'app non ne ha chiesto uno. Pura: la prova `FluidAssistantInkTest`.
 *
 * Perché il valore di serie non è più `MaterialTheme.colorScheme.onSurface` scritto nella firma: un valore di serie si
 * legge dove la funzione comincia, **fuori** dal contenitore che il pezzo si fa, cioè sempre sul lato del tema. Letto
 * dentro, lo schema di colori è quello del lato del vetro.
 */
internal fun fluidAssistantInk(requested: Color, sideInk: Color): Color =
  if (requested.isSpecified) requested else sideInk

/**
 * Se un pezzo piccolo si fa **un contenitore suo** (e allora gira chiaro/scuro con lo sfondo): solo da solo sulla pagina.
 * Su un pannello dell'assistente ([onPanel]) è vetro annidato e prende il lato del pannello; dentro un vetro adattivo
 * dell'engine o un `FluidGlassContainer` ([onAdaptiveGlass], cioè c'è un `LocalFluidGlassAppearance`) il lato lo decide
 * già chi lo contiene, e un `glassControlSurface` dentro un contenitore ne è un pezzo da sé. Pura.
 */
internal fun fluidAssistantOwnsContainer(onPanel: Boolean, onAdaptiveGlass: Boolean): Boolean = !onPanel && !onAdaptiveGlass

/**
 * Un pezzo piccolo di vetro dell'assistente: da solo in un `FluidGlassContainer` con un pezzo solo (il patto
 * dell'inchiostro), sopra un pannello o dentro un vetro che decide il lato com'era. [piece] riceve il `modifier`
 * dell'app quando non c'è un contenitore suo; quando c'è, il `modifier` va al contenitore (è lui il figlio del genitore:
 * `align`, `weight`, `padding` e i tag restano dove l'app li ha scritti) e [piece] riceve `Modifier`.
 *
 * Il vetro, sul lato del tema, è quello di prima: dentro il contenitore un `glassControlSurface` lo disegna il contenitore,
 * come lastra di un pezzo solo, con la stessa tinta, la stessa ottica, lo stesso ruolo (`Interactive`), lo stesso filo
 * (nessuno) e la stessa pressione del tasto da solo (letto nel codice dell'engine, F3.2: `glassControlPiece` e la lastra
 * di riposo del contenitore). Cambia che gira. Col vetro di prima e sotto Android 13 il contenitore non disegna e il
 * pezzo è il tasto di sempre.
 */
@Composable
internal fun FluidAssistantSmallGlass(modifier: Modifier, piece: @Composable (Modifier) -> Unit) {
  val own = fluidAssistantOwnsContainer(
    onPanel = LocalFluidAssistantPanel.current,
    onAdaptiveGlass = LocalFluidGlassAppearance.current != null,
  )
  if (own) {
    FluidGlassContainer(modifier) { piece(Modifier) }
  } else {
    piece(modifier)
  }
}
