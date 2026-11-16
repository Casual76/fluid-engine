package dev.antigravity.fluidengine.aiui.overlay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import dev.antigravity.fluidengine.ui.fluid.FluidGlassAnimation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContainer
import dev.antigravity.fluidengine.ui.fluid.FluidGlassNested
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassAppearance

// Il vetro dell'assistente e il patto dell'inchiostro (4.0, piano «iOS in Android», F3.2b e F3.2e).
//
// Il patto pubblico dell'engine e' il contenitore (`FluidGlassContainer`): un vetro gira chiaro/scuro con lo sfondo solo
// se sta in un contenitore, che gli da' una sonda e da' al contenuto l'inchiostro del lato. Ogni superficie
// dell'assistente che sta da sola sulla pagina se ne fa uno con un pezzo solo: i pezzi piccoli (tasti tondi, stop, chip,
// la capsula dell'ascolto, F3.2b) e i pannelli (card, dock, barra di scrittura, pillola ridotta, capsula del composer,
// F3.2e). I pezzi piccoli che stanno sopra un pannello sono vetro annidato del pannello e ne prendono il lato.

/**
 * Vero dentro il contenuto di un **pannello di vetro dell'assistente** (la card, il dock, la capsula del composer, la
 * barra di scrittura, F3.2e: lo dicono al loro contenuto). Lì i pezzi piccoli (`FluidGlassRoundButton`, `FluidStopPill`,
 * `FluidChipPill`, `FluidListeningHint`) non si fanno un contenitore loro: sono vetro annidato del pannello, ne
 * campionano il materiale e ne prendono il lato, che gira o scurisce col pannello. Se girassero da soli, sopra una foto
 * scura un tasto diventerebbe scuro su una card rimasta chiara (o il contrario).
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
 * `align`, `weight`, `padding` e i tag restano dove l'app li ha scritti) e [piece] riceve `Modifier`; dal F3.2e il
 * contenitore passa al pezzo i vincoli minimi, quindi un `weight` o una misura imposta allargano il vetro come prima.
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
    // I vincoli minimi arrivano al pezzo (F3.2e): un `weight` o una misura dell'app sul `modifier` allargano il vetro.
    FluidGlassContainer(modifier, propagateMinConstraints = true) { piece(Modifier) }
  } else {
    piece(modifier)
  }
}

/**
 * Le cinque superfici-pannello dell'assistente e la loro famiglia (F3.2e): il ruolo del vetro, lo stesso di prima, e se
 * la superficie è un **grande** che scurisce in continuo col lato (card e dock: pannelli con del testo, come la notifica e
 * il foglio) o un **piccolo** che gira (la barra di scrittura, la pillola ridotta, la capsula del composer: barre, come il
 * campo di ricerca flottante e la capsula flottante). Il ruolo da solo sbaglierebbe due volte: la barra è `Modal` ma
 * alta una riga, il dock è `Floating` ma a raggio di foglio e a tutta larghezza. Pura: la prova `FluidAssistantGlassTest`.
 */
internal enum class FluidAssistantSurface(val role: GlassRole, val large: Boolean) {
  Card(GlassRole.Modal, large = true),
  Dock(GlassRole.Floating, large = true),
  TextBar(GlassRole.Modal, large = false),
  CollapsedPill(GlassRole.Floating, large = false),

  /**
   * `Floating`, con riserva (da prima di F3.2e). E' il ruolo giusto per una capsula che galleggia sulla chat: la lente e'
   * la stessa del `Modal` (19/29 dp contro 20/28), cambia la sfocatura, 1.8 contro 3.5, e con la pagina rifratta sotto
   * la piu' leggera e' quella che lascia vedere che *c'e'* una pagina. Sull'emulatore ogni build con `Floating` su una
   * superficie di questa misura finiva in ANR nel disegno ("main thread affamato", non un loop), e quelle con `Modal`
   * no: sul telefono va provato, e il ripiego e' `GlassRole.Modal`, una parola (e `large = false` resta: e' una barra).
   */
  Composer(GlassRole.Floating, large = false),
}

/**
 * Un pannello di vetro dell'assistente (F3.2e): da solo sulla pagina in un `FluidGlassContainer` con un pezzo solo, il
 * pannello (un `fluidGlassPanel` con la ricetta di prima); dentro un contenitore dell'app o un vetro adattivo
 * dell'engine com'era, pezzo di quello o vetro annidato. Il `modifier` dell'app va al contenitore, che passa al pannello i
 * vincoli minimi (`propagateMinConstraints`): `weight`, `align`, `padding`, una misura imposta e i tag restano dove l'app
 * li ha scritti, e allargano il vetro come prima.
 *
 * **Nessun viaggio** (`FluidGlassAnimation.None`): il pannello sta da solo, non ha vicini da cui nascere né in cui
 * confluire, e il suo contenuto cresce mentre la risposta arriva o la tastiera sale. Con la molla del piano il vetro
 * inseguirebbe il contenuto per un terzo di secondo, col testo fuori dal bordo; così lo segue nel fotogramma dopo.
 */
@Composable
internal fun FluidAssistantPanelGlass(modifier: Modifier, panel: @Composable (Modifier) -> Unit) {
  val own = fluidAssistantOwnsContainer(
    onPanel = LocalFluidAssistantPanel.current,
    onAdaptiveGlass = LocalFluidGlassAppearance.current != null,
  )
  if (own) {
    FluidGlassContainer(modifier, animation = FluidGlassAnimation.None, propagateMinConstraints = true) { panel(Modifier) }
  } else {
    panel(modifier)
  }
}

/**
 * Il contenuto di un pannello dell'assistente: i vetri che porta sono vetro annidato del pannello (`FluidGlassNested`,
 * non pezzi del suo contenitore) e i pezzi piccoli lo sanno ([LocalFluidAssistantPanel]). Nessun nodo di layout: lo scope
 * della `Column` o della `Row` del pannello resta in vista.
 */
@Composable
internal fun FluidAssistantPanelContent(content: @Composable () -> Unit) {
  FluidGlassNested {
    CompositionLocalProvider(LocalFluidAssistantPanel provides true, content = content)
  }
}
