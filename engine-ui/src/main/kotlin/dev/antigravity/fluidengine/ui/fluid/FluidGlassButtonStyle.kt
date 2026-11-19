/*
 * Lo stile, la taglia e la larghezza dei tasti di vetro come valori d'ambiente (piano «iOS in Android», F6.4): i
 * `buttonStyle(.glass / .glassProminent / .glass(_:))`, `controlSize` e `buttonSizing` di SwiftUI.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ProvidedValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.isSpecified
import dev.antigravity.fluidengine.ui.theme.highestContrastContent

/**
 * Lo stile di un tasto di vetro ([FluidGlassButton], [FluidGlassIconButton]): `buttonStyle(.glass)`,
 * `.glassProminent` e `.glass(_:)` di SwiftUI.
 *
 *     FluidGlassButton("Fatto", onClick, style = FluidGlassButtonStyle.Prominent)        // l'azione principale
 *     FluidGlassIconButton(onClick, style = FluidGlassButtonStyle.Clear) { … }           // sopra una foto
 *     CompositionLocalProvider(LocalFluidGlassButtonStyle provides FluidGlassButtonStyle.Clear) { … }  // per un gruppo
 *
 * **Perche' esiste.** Fino alla 4.0 un tasto di vetro era uno solo, e un'app che voleva l'azione principale colorata
 * se la dipingeva da se' con una `GlassTint` scritta a mano: una tinta a meta' opacita' sopra una foto lasciava
 * l'etichetta a 3,9:1 su una foto scura e a 3,2:1 su una chiara (chiusura di F2). Lo stile lo dice l'app, il disegno
 * lo decide l'engine, come in SwiftUI.
 *
 * - [Glass] e' il vetro di serie dei tasti: smerigliato, adattivo, l'etichetta segue il lato del vetro.
 * - [Clear] e' il vetro limpido ([FluidGlass.Clear]): da usare sopra contenuto ricco, con contenuto chiaro e marcato.
 * - [Glass] con un argomento prende un [FluidGlass] qualunque: una tinta di Apple (`FluidGlass.Regular.tint(c)`, la
 *   tonalita' resta e la luminosita' segue lo sfondo) o [FluidGlass.Identity], il tasto senza vetro.
 * - [Prominent] e' l'azione principale: il vetro tinto dell'accento (o della tinta passata) **abbastanza pieno da
 *   reggere l'etichetta**. La tinta resta quella scelta dall'app; cresce solo quanto copre, fino a dove l'etichetta sta
 *   ad almeno [FluidProminentLabelContrast] sul fondo peggiore, nero o bianco ([fluidProminentCoverage]). L'etichetta
 *   prende l'inchiostro che sulla tinta si legge meglio, nero o bianco, e non gira col fondo: il tasto pieno e' lui
 *   stesso il fondo. Come in Apple, un tasto in evidenza per schermata.
 *
 * Il tasto reagisce sempre al dito: l'`interactive` del vetro passato non conta, come per lo stile `.glass` di SwiftUI.
 *
 * Uguaglianza strutturale: uno stile ricreato a ogni ricomposizione non invalida niente.
 */
@Immutable
class FluidGlassButtonStyle private constructor(
  /** Il vetro del tasto. Per [Prominent] e' il vetro di serie: la tinta la porta [tint]. */
  val glass: FluidGlass,
  /** Vero per lo stile in evidenza ([Prominent]). */
  val isProminent: Boolean,
  /**
   * Per [Prominent] la tinta scelta dall'app, o [Color.Unspecified] per l'accento del tema; per gli altri la tinta del
   * vetro ([FluidGlass.tint]).
   */
  val tint: Color,
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FluidGlassButtonStyle) return false
    return glass == other.glass && isProminent == other.isProminent && tint == other.tint
  }

  override fun hashCode(): Int {
    var result = glass.hashCode()
    result = 31 * result + isProminent.hashCode()
    result = 31 * result + tint.hashCode()
    return result
  }

  override fun toString(): String = when {
    isProminent && tint.isSpecified -> "FluidGlassButtonStyle.Prominent($tint)"
    isProminent -> "FluidGlassButtonStyle.Prominent"
    this == Glass -> "FluidGlassButtonStyle.Glass"
    this == Clear -> "FluidGlassButtonStyle.Clear"
    else -> "FluidGlassButtonStyle.Glass($glass)"
  }

  companion object {
    /** Il vetro di serie dei tasti (`.glass`). */
    val Glass: FluidGlassButtonStyle = FluidGlassButtonStyle(FluidGlass.Regular, isProminent = false, Color.Unspecified)

    /** Il vetro limpido (`.glass(.clear)`): sopra foto e video, con contenuto chiaro e marcato. */
    val Clear: FluidGlassButtonStyle = FluidGlassButtonStyle(FluidGlass.Clear, isProminent = false, Color.Unspecified)

    /** L'azione principale (`.glassProminent`), tinta dell'accento del tema. */
    val Prominent: FluidGlassButtonStyle = FluidGlassButtonStyle(FluidGlass.Regular, isProminent = true, Color.Unspecified)

    /**
     * Un vetro qualunque (`.glass(_:)`): `FluidGlass.Regular.tint(c)` per una tinta di Apple, [FluidGlass.Identity] per
     * un tasto senza vetro. L'interattivita' del vetro non conta: un tasto reagisce sempre al dito.
     */
    fun Glass(glass: FluidGlass): FluidGlassButtonStyle = when (glass.interactive(false)) {
      FluidGlass.Regular -> Glass
      FluidGlass.Clear -> Clear
      else -> FluidGlassButtonStyle(glass.interactive(false), isProminent = false, glass.tint)
    }

    /**
     * L'azione principale con una tinta sua (`.glassProminent` con `.tint(_:)`). [Color.Unspecified] vale l'accento.
     * Conta il colore, non la sua alfa: quanto copre lo decide l'etichetta ([fluidProminentCoverage]).
     */
    fun Prominent(tint: Color): FluidGlassButtonStyle =
      if (!tint.isSpecified) Prominent else FluidGlassButtonStyle(FluidGlass.Regular, isProminent = true, tint)
  }
}

/**
 * Quanto un tasto di vetro e' largo (`buttonSizing` di SwiftUI).
 *
 * [Fitted] e' largo quanto la sua etichetta piu' i margini, com'e' sempre stato. [Flexible] riempie la larghezza che
 * gli si da' (`fillMaxWidth`), con l'etichetta al centro: in una `Row` di tasti flessibili si da' a ciascuno
 * `Modifier.weight(1f)`, perche' il peso lo puo' dare solo la riga.
 */
enum class FluidButtonSizing { Fitted, Flexible }

/**
 * La taglia dei tasti di vetro di questo punto dell'albero (`controlSize` di SwiftUI): un [FluidGlassIconButton] o un
 * [FluidGlassButton] senza `size` prende questa. Di serie [FluidGlassSize.Regular].
 *
 * **Perche' esiste.** Una barra di tasti `Compact` (la riga di un composer) doveva scrivere la taglia su ogni tasto, e
 * un tasto aggiunto dopo tornava `Regular`. Fornita una volta, vale per tutti i tasti dentro; e l'icona di ciascuno
 * ne discende, perche' il tasto da' a una [FluidIcon] senza `size` 0,6 del suo vetro ([LocalFluidIconSize]).
 *
 * I fogli, i popover, gli avvisi e i menu dell'host (`FluidGlassModalHost`, `FluidGlassSheet`) la riportano al valore
 * di serie, insieme allo stile e alla larghezza: in iOS 27 un foglio non eredita lo stile della barra che lo apre.
 */
val LocalFluidGlassSize: ProvidableCompositionLocal<FluidGlassSize> = staticCompositionLocalOf { FluidGlassSize.Regular }

/**
 * Lo stile dei tasti di vetro di questo punto dell'albero: un tasto senza `style` prende questo. Di serie
 * [FluidGlassButtonStyle.Glass]. Fogli e popover dell'host lo riportano al valore di serie (vedi [LocalFluidGlassSize]).
 */
val LocalFluidGlassButtonStyle: ProvidableCompositionLocal<FluidGlassButtonStyle> =
  staticCompositionLocalOf { FluidGlassButtonStyle.Glass }

/**
 * La larghezza dei tasti con etichetta di questo punto dell'albero ([FluidGlassButton] senza `sizing`). Di serie
 * [FluidButtonSizing.Fitted]. Fogli e popover dell'host la riportano al valore di serie (vedi [LocalFluidGlassSize]).
 */
val LocalFluidButtonSizing: ProvidableCompositionLocal<FluidButtonSizing> =
  staticCompositionLocalOf { FluidButtonSizing.Fitted }

/**
 * I valori di serie dei tasti, da fornire dove una presentazione comincia (foglio, popover, avviso, menu): iOS 27
 * riporta `controlSize`, `buttonSizing` e la forma ai valori di serie dentro fogli e popover (R5, note di rilascio), e
 * un foglio aperto da una barra di tasti `Compact` ha i tasti alla taglia di serie.
 */
internal fun fluidGlassButtonDefaults(): Array<ProvidedValue<*>> = arrayOf(
  LocalFluidGlassSize provides FluidGlassSize.Regular,
  LocalFluidGlassButtonStyle provides FluidGlassButtonStyle.Glass,
  LocalFluidButtonSizing provides FluidButtonSizing.Fitted,
)

// ------------------------------------------------------------------------------------------------------------
// L'etichetta del tasto in evidenza
// ------------------------------------------------------------------------------------------------------------

/**
 * Il contrasto che l'etichetta di un tasto [FluidGlassButtonStyle.Prominent] tiene sul fondo peggiore: 4,5:1, la soglia
 * WCAG 1.4.3 per il testo normale. Il tasto tinto della 4.0 scritto a mano stava a 3,90:1 su una foto scura in tema
 * chiaro e a 3,20:1 su una chiara in tema scuro (chiusura di F2, 2026-11-16).
 */
const val FluidProminentLabelContrast: Float = 4.5f

/**
 * Il margine con cui [fluidProminentCoverage] cerca la copertura: il conto suppone che dietro alla tinta ci sia un
 * colore pieno, mentre il vetro aggiunge sopra luce e riflessi (pochi livelli al centro, dove sta l'etichetta). Un
 * ventesimo di margine li assorbe; dove la tinta da sola non arriva al margine, il tasto la copre per intero.
 */
internal const val FluidProminentContrastMargin: Float = 1.05f

/**
 * L'inchiostro dell'etichetta di un tasto in evidenza sulla tinta [tint]: quello fra il quasi nero e il quasi bianco
 * del tema che sulla tinta piena fa piu' contrasto, come `onPrimary` per l'accento. Su una tinta di mezzo tono (luminanza
 * vicina a 0,18) nemmeno quello arriva a [FluidProminentLabelContrast] (un grigio #777777 sta a 4,41:1 col quasi nero):
 * allora il nero o il bianco pieni, che su qualunque tinta piena ne fanno almeno 4,58. Pura.
 */
internal fun fluidProminentInk(tint: Color): Color {
  val opaque = tint.copy(alpha = 1f)
  val themed = highestContrastContent(opaque)
  if (fluidContrastRatio(themed, opaque) >= FluidProminentLabelContrast) return themed
  return if (fluidContrastRatio(Color.Black, opaque) >= fluidContrastRatio(Color.White, opaque)) Color.Black else Color.White
}

/**
 * Quanto la tinta [tint] deve coprire il vetro perche' [ink] ci si legga ad almeno [minContrast] **qualunque cosa ci
 * sia dietro**: si prova la tinta composta sul nero e sul bianco (la luminanza del vetro sta fra le due, perche' cresce
 * con ogni canale) e si cerca, per bisezione, la copertura piu' bassa che regge su entrambi. L'alfa di [tint] non conta:
 * gli accenti del tema sono opachi, e prenderla come pavimento faceva di ogni tasto in evidenza una pastiglia piena,
 * senza vetro. Se nemmeno la tinta piena arriva a [minContrast], la copertura e' piena.
 *
 * Pura: e' aritmetica di colori che `FluidGlassButtonStyleTest` prova su tutti gli accenti della palette.
 */
internal fun fluidProminentCoverage(
  tint: Color,
  ink: Color = fluidProminentInk(tint),
  minContrast: Float = FluidProminentLabelContrast * FluidProminentContrastMargin,
): Float {
  val opaque = tint.copy(alpha = 1f)
  val floor = 0f
  fun holds(coverage: Float): Boolean {
    val film = opaque.copy(alpha = coverage)
    return fluidContrastRatio(ink, film.compositeOver(Color.Black)) >= minContrast &&
      fluidContrastRatio(ink, film.compositeOver(Color.White)) >= minContrast
  }
  if (holds(floor)) return floor
  if (!holds(1f)) return 1f
  var low = floor
  var high = 1f
  repeat(24) {
    val mid = (low + high) / 2f
    if (holds(mid)) high = mid else low = mid
  }
  return high
}

/** Il contrasto peggiore dell'etichetta [ink] sulla tinta [tint] alla copertura [coverage], sul nero o sul bianco. Pura. */
internal fun fluidProminentWorstContrast(tint: Color, coverage: Float, ink: Color = fluidProminentInk(tint)): Float {
  val film = tint.copy(alpha = coverage.coerceIn(0f, 1f))
  return minOf(
    fluidContrastRatio(ink, film.compositeOver(Color.Black)),
    fluidContrastRatio(ink, film.compositeOver(Color.White)),
  )
}

/**
 * La pellicola di un tasto in evidenza: la tinta [tint] alla copertura che regge l'etichetta, dipinta com'e'
 * ([GlassTintMode.Fixed]: prende il posto del velo e non segue la luminosita' dello sfondo, perche' e' lei il fondo
 * dell'etichetta). Ripiego e filetto restano quelli di [base].
 */
internal fun fluidProminentTint(tint: Color, base: GlassTint): GlassTint {
  val film = tint.copy(alpha = fluidProminentCoverage(tint))
  return GlassTint(
    overlay = film,
    fallback = film.compositeOver(base.fallback),
    hairline = base.hairline,
    mode = GlassTintMode.Fixed,
  )
}
