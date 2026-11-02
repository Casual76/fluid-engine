package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * Il contrasto minimo fra il riempimento di una barra e la sua pista: 3:1, la soglia che WCAG 1.4.11
 * chiede a un oggetto grafico (una barra di avanzamento non e' testo, ma si legge o non si legge).
 */
internal const val FluidFillMinContrast = 3f

/** Quanta della tinta di testo sta nella pista di una barra: un decimo, come sempre. */
internal const val FluidProgressTrackAlpha = 0.10f

/** Il rapporto di contrasto WCAG fra due colori opachi: `(L chiaro + 0,05) / (L scuro + 0,05)`. */
internal fun fluidContrastRatio(foreground: Color, background: Color): Float {
  val high = maxOf(foreground.luminance(), background.luminance())
  val low = minOf(foreground.luminance(), background.luminance())
  return (high + 0.05f) / (low + 0.05f)
}

/**
 * Il riempimento di una barra, portato dove si legge sulla sua pista.
 *
 * Una barra di avanzamento disegna l'accento sopra una pista che e' il colore del testo al 10%, cioe'
 * un grigio quasi della superficie: e' il caso in cui un accento di mezzo tono sparisce. Misurato su
 * una galleria, alcuni accenti facevano 1,7:1 con la pista, e una barra al 60% non si distingueva da
 * una al 100%. L'accento e' una scelta di identita', non di leggibilita': qui si tiene com'e' quando
 * gia' basta, e altrimenti si porta **del minimo che serve** verso il nero (su una pista chiara) o
 * verso il bianco (su una scura), cosi' resta riconoscibile come lo stesso colore invece di
 * diventare il colore del testo. E' la stessa idea di `legibleInk` della tab bar, ma su una lista
 * di fondi invece che sul peggiore di uno: una barra sta su una pagina, su una card o su un
 * pannello di vetro, e il riempimento deve leggersi su tutti.
 *
 * Il verso lo decide quale estremo, nero o bianco, fa piu' contrasto col peggiore dei fondi, non il
 * confronto con l'accento: un giallo chiaro su una pista chiara e' *piu' chiaro* della pista, e
 * "allontanarsi" vorrebbe dire sbiancarlo, che non arriva mai a 3:1.
 *
 * Il contrasto non e' monotono dall'accento all'estremo (un colore piu' chiaro del fondo prima
 * gli si avvicina, poi lo supera), ma la condizione "fa almeno [minContrast]" lo e': vale da un
 * punto in poi, e una ricerca binaria trova quel punto.
 *
 * Pura e fuori dalla composizione perche' e' aritmetica di colori da verificare su tutti gli
 * accenti della palette, non su un telefono.
 *
 * @param color l'accento che il chiamante vuole; se ha trasparenza si misura gia' composto sul fondo.
 * @param backgrounds i fondi su cui la pista puo' trovarsi, **gia' composti** (pista sopra superficie).
 */
internal fun legibleFill(
  color: Color,
  backgrounds: List<Color>,
  minContrast: Float = FluidFillMinContrast,
): Color {
  if (backgrounds.isEmpty()) return color
  fun worst(candidate: Color): Float =
    backgrounds.minOf { fluidContrastRatio(candidate.compositeOver(it), it) }

  if (worst(color) >= minContrast) return color
  val toward = if (worst(Color.Black) >= worst(Color.White)) Color.Black else Color.White
  var low = 0f
  var high = 1f
  repeat(14) {
    val mid = (low + high) / 2f
    if (worst(lerp(color, toward, mid)) >= minContrast) high = mid else low = mid
  }
  return lerp(color, toward, high)
}

/**
 * I fondi su cui sta la pista di una barra, ciascuno con la pista gia' sopra.
 *
 * Tre superfici della palette — la pagina, una card, una card piu' alta — perche' una barra sta
 * dove la mette l'app e il riempimento non puo' dipendere da quale: lo scarto fra le tre e' di
 * pochi punti di luminanza, ma sono quelli che separano 3,0 da 2,9. Su un pannello di vetro si
 * aggiunge la pellicola del vetro ([glassFilm], quasi bianca anche in tema scuro: li' la pista
 * poggia su qualcosa di piu' chiaro della pagina, e il riempimento deve salire).
 */
internal fun progressFillBackgrounds(scheme: ColorScheme, glassFilm: Color? = null): List<Color> {
  val track = scheme.onSurface.copy(alpha = FluidProgressTrackAlpha)
  val surfaces = buildList {
    add(scheme.surface)
    add(scheme.surfaceContainerLow)
    add(scheme.surfaceContainerHigh)
    glassFilm?.let { add(it) }
  }
  return surfaces.map { track.compositeOver(it) }
}

/** Il riempimento di una barra con la palette [scheme]: vedi [legibleFill]. */
internal fun fluidProgressFill(color: Color, scheme: ColorScheme, glassFilm: Color? = null): Color =
  legibleFill(color, progressFillBackgrounds(scheme, glassFilm))
