/*
 * Il profilo della PILLOLA della tab bar (il «puck» di `FluidFloatingTabBar`) e della riga nascosta che la pillola
 * campiona mentre è premuta (F1B.4, piano «iOS in Android», sezione F1B).
 *
 * Non ha numeri del materiale suoi: ogni profilo qui è **derivato** da quello di serie ([GlassLiquidProfile.Default])
 * con `copy`, quindi se domani cambia il velo, il bordo, la smerigliatura o l'ombra di serie, la pillola li segue.
 * I soli numeri propri sono quelli che dicono «questa è la scheda scelta» ([GlassPuckSelection]): quanto la pillola
 * è più scura della barra.
 *
 * ### Cosa dice Apple
 * La pillola della scheda scelta è vetro nel vetro della barra, **più scura** della barra in tutti e due i temi:
 *  - chiaro: 219 su una pagina 238 (−19 sulla pagina; il corpo della barra sta a 240–243), rientrata di 0,076 H,
 *    alta 0,85 H (R5 M4, alta: `T6_scheda_prominente_269_0519.jpg`);
 *  - scuro: «più scura del corpo (47–57)», con glifo ed etichetta tinti (R5 M3, a occhio: corpo 58–78 sul varco
 *    nero di `M3_tab_bar_scura_292_0632.jpg`).
 * Grigia, non tinta d'accento: la scelta la dicono la pillola e il colore del glifo (R6). Niente ombra visibile,
 * nessun secondo strato smerigliato: sotto la pillola si vede la stessa pagina sfocata che si vede sotto la barra.
 *
 * ### Come lo fa l'engine
 * La pillola a riposo **non** campiona il pannello della barra (vetro su vetro, che sfocherebbe due volte: il
 * difetto della 2.12.1), campiona la pagina cruda e rifà da sola il materiale della barra: stessa smerigliatura
 * (stesso ruolo, e sotto i 64 dp di lato corto la regola della taglia vale zero per la barra e per la pillola),
 * **lo stesso velo della barra** ([glassPuckBarVeil], alla taglia della barra e non a quella della pillola), e
 * sopra la selezione. Il velo che la regola del vetro annidato toglierebbe a una lastra posata su un altro vetro
 * qui non c'è da togliere: è quello della barra, rifatto, non un secondo velo sopra il primo. Della regola
 * dell'annidato ([GlassLiquidProfile.nesting]) resta quindi l'**ombra**, ridotta come quella di un tasto dentro un
 * pannello; e i fattori dell'annidato del profilo della pillola sono «come sulla pagina», perché il nodo, se un
 * giorno la trovasse dentro un vetro, non li applichi una seconda volta.
 *
 * Premuta, la pillola guarda la riga nascosta: le icone tinte d'accento sopra **il materiale della barra**
 * ([glassPuckRowProfile], la stessa ricetta della barra con il velo della barra). Quella riga è già smerigliata e
 * velata, quindi la pillola premuta ([glassPuckOverRowProfile]) non sfoca e non satura (la mescola a uno, la catena
 * salta la sfocatura) e del velo posa soltanto la selezione: barra + selezione, cioè la pillola a riposo. Il raggio
 * resta quello del profilo, così il margine della cattura non cambia fra i due.
 *
 * Il bordo è quello di serie, sui due lati: il filo sottile e il riflesso della barra, sulla sagoma della pillola; sul
 * chiaro il filo scuro a metà ([GlassPuckSelection.lightRim]).
 *
 * Fuori da qui (F4B): la pillola che si fa lente limpida quando la trascini, la barra che si riduce a un tasto.
 */
package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp

/**
 * La **selezione**: quanto la pillola è più scura della barra, come un nero di questa alfa posato sopra il velo
 * della barra, e quanto del filo della barra tiene la sua sagoma. Per lato del tema.
 *
 * I valori di serie ([GlassPuckSelections.Ios27]) sono tarati sul banco contro le misure di R5 (vedi lì).
 *
 * @param lightRim con [darkRim], la frazione dell'alfa del filo di serie sulla sagoma della pillola (il riflesso
 *   resta intero): uno = il filo della barra.
 */
@Immutable
internal data class GlassPuckSelection(
  val light: Float,
  val dark: Float,
  val lightRim: Float = 1f,
  val darkRim: Float = 1f,
) {
  /** La selezione di quel lato. */
  fun of(darkSide: Boolean): Float = if (darkSide) dark else light

  /** La frazione del filo di quel lato. */
  fun rimOf(darkSide: Boolean): Float = if (darkSide) darkRim else lightRim
}

/** Le varianti della selezione. */
internal object GlassPuckSelections {

  /**
   * **iOS 27** (di serie). Tarata sul banco, scene `tools/glassbench/scenes/f1b/puck-*` a densità 3, contro
   * R5 M4 (chiaro, alta: pillola 219 su pagina 238, −19 sulla pagina, −21…−24 sul corpo della barra) e R5 M3
   * (scuro, a occhio: pillola 47–57 contro un corpo 58–78, cioè una decina di livelli sotto la barra).
   *
   * Sul banco (centro della pillola, fondo piatto): chiaro 219 su pagina 242 e barra 243, cioè −24 sulla barra e −23
   * sulla pagina (0,10 dava −27/−28); scuro 44 contro una barra a 60, −17. La pillola premuta sopra la barra sta
   * entro 2 livelli da quella a riposo su fondo piatto e foto, entro 6 sul testo chiaro (dove il velo della barra si
   * ispessisce e quello della pillola no, vedi [glassPuckSelectedVeil]).
   *
   * Il filo: sul chiaro a metà. Pieno, il filo scuro della pillola correva a 4 dp da quello della barra e faceva un
   * secondo contorno disegnato, che sul ritaglio di Apple (`T6_scheda_prominente_269_0519.jpg`) non c'è: la pillola
   * stacca per il grigio, e il bordo è appena accennato. Sullo scuro il filo chiaro resta intero: sul banco somiglia
   * già al bordo appena più chiaro della pillola di `M3_tab_bar_scura_292_0632.jpg`. A occhio, sul banco.
   */
  val Ios27: GlassPuckSelection = GlassPuckSelection(light = 0.09f, dark = 0.24f, lightRim = 0.5f, darkRim = 1f)

  /** **Nessuna**: la pillola è la barra stessa, col suo filo. Per i confronti. */
  val None: GlassPuckSelection = GlassPuckSelection(light = 0f, dark = 0f)
}

/**
 * Il velo che la barra ha davvero, di lato corto [barShortSideDp]: sul lato scuro il velo dei piccoli e quello dei
 * grandi si mescolano con la taglia (come fa `glassRecipe`), e la pillola, più bassa della barra, prenderebbe da
 * sola una mescola diversa. Sul chiaro i due veli sono lo stesso, e il risultato è quel velo.
 */
internal fun glassPuckBarVeil(
  base: GlassLiquidProfile,
  darkSide: Boolean,
  barShortSideDp: Float,
): GlassLiquidVeil {
  val small = base.veil(darkSide)
  val large = base.largeVeil(darkSide)
  val t = glassLiquidLargeVeilGain(barShortSideDp)
  if (t <= 0f || small == large) return small
  if (t >= 1f) return large
  return GlassLiquidVeil(
    color = mixColor(small.color, large.color, t),
    adapt = mix(small.adapt, large.adapt, t),
    shade = mixColor(small.shade, large.shade, t),
    shadeDepth = Dp(mix(small.shadeDepth.value, large.shadeDepth.value, t)),
    shadeLeanX = mix(small.shadeLeanX, large.shadeLeanX, t),
    shadeLeanY = mix(small.shadeLeanY, large.shadeLeanY, t),
  )
}

/**
 * Un velo con sopra un nero di alfa [selection], come **un** velo solo: è la composizione di due strati
 * (`sopra(sopra(fondo, velo), nero)`) scritta come `sopra(fondo, velo')`, esatta per un velo che non si ispessisce.
 *
 * Il velo che ne esce **non si ispessisce** (`adapt` zero), anche se quello d'origine sì. L'ispessimento fa crescere
 * l'alfa dove lo sfondo somiglia al colore del velo; sul velo composto, più scuro, crescerebbe verso un grigio più
 * scuro, che la barra non ha: sul banco la pillola su pagina chiara usciva 39 livelli sotto la barra invece dei
 * 21–24 di Apple, e 9 sotto la pillola premuta. Senza, è giusto dove conta: dove lo sfondo somiglia al velo il corpo
 * della barra è quasi lo sfondo qualunque sia l'alfa (pagina 242: barra 243, pillola 219, premuta 219), e dove non
 * gli somiglia l'ispessimento della barra vale già quasi zero.
 */
internal fun glassPuckSelectedVeil(veil: GlassLiquidVeil, selection: Float): GlassLiquidVeil {
  val s = clampUnit(selection)
  if (s <= 0f) return veil
  val color = underBlack(veil.color, s) ?: return veil
  return veil.copy(color = color, adapt = 0f)
}

/**
 * Un colore [color] (non premoltiplicato) con sopra un nero di alfa [s], come **un** colore solo:
 * `sopra(sopra(fondo, color), nero)` scritto come `sopra(fondo, risultato)`, esatto. `null` se non resta niente.
 */
private fun underBlack(color: Color, s: Float): Color? {
  val a = clampUnit(color.alpha)
  val alpha = 1f - (1f - a) * (1f - s)
  if (!(alpha > 0f)) return null
  // Il nero non porta colore: resta quello del velo, pesato per quanto ne passa sotto il nero.
  val k = a * (1f - s) / alpha
  return Color(red = color.red * k, green = color.green * k, blue = color.blue * k, alpha = alpha)
}

/**
 * La pellicola della pillola **a riposo** quando la barra non ha il velo del profilo ma una **pellicola dipinta** com'è
 * ([barFilm]): sopra il contrasto di sistema standard la tinta della barra (`GlassDefaults.floatingTint()`, che passa da
 * `forSystemContrast`) si fa densa e `Fixed`, e col materiale nuovo una tinta `Fixed` prende il posto del velo. La
 * pillola, che rifà il materiale della barra, deve rifare **quella** pellicola, non il velo del profilo: prima restava
 * velata e trasparente sopra una barra densa (F1B.5, giro di correzione). È la pellicola della barra con sopra la
 * selezione del lato, come una pellicola sola (lo stesso conto di [glassPuckSelectedVeil]); la riga nascosta prende
 * [barFilm] così com'è, e la pillola premuta sopra di lei posa solo la selezione ([glassPuckOverRowProfile]).
 *
 * @param barFilm la pellicola della barra, o `Color.Unspecified` se la barra ha il velo del profilo: allora anche il
 *   risultato è `Unspecified` e la pillola resta quella di [glassPuckProfile].
 */
internal fun glassPuckFilm(
  barFilm: Color,
  darkSide: Boolean,
  selection: GlassPuckSelection = GlassPuckSelections.Ios27,
): Color {
  if (!barFilm.isSpecified) return Color.Unspecified
  val s = clampUnit(selection.of(darkSide))
  if (s <= 0f) return barFilm
  return underBlack(barFilm, s) ?: barFilm
}

/**
 * La riga nascosta che la pillola premuta campiona: **il materiale della barra**, la stessa ricetta di serie con il
 * velo che la barra ha alla sua taglia ([glassPuckBarVeil]). La riga è più bassa della barra (sta dentro il suo
 * margine), e da sola prenderebbe sullo scuro un velo un poco diverso.
 */
internal fun glassPuckRowProfile(
  barShortSideDp: Float,
  base: GlassLiquidProfile = GlassLiquidProfile.Default,
): GlassLiquidProfile {
  val light = glassPuckBarVeil(base, darkSide = false, barShortSideDp = barShortSideDp)
  val dark = glassPuckBarVeil(base, darkSide = true, barShortSideDp = barShortSideDp)
  return base.copy(lightVeil = light, darkVeil = dark, darkLargeVeil = dark)
}

/**
 * La pillola **a riposo**: campiona la pagina cruda, e su quella è barra + selezione. Vedi l'intestazione del file.
 *
 * @param barShortSideDp il lato corto della barra che la porta (la sua altezza), per il velo della barra.
 */
internal fun glassPuckProfile(
  barShortSideDp: Float,
  base: GlassLiquidProfile = GlassLiquidProfile.Default,
  selection: GlassPuckSelection = GlassPuckSelections.Ios27,
): GlassLiquidProfile {
  val light = glassPuckSelectedVeil(glassPuckBarVeil(base, false, barShortSideDp), selection.light)
  val dark = glassPuckSelectedVeil(glassPuckBarVeil(base, true, barShortSideDp), selection.dark)
  fun edge(edge: GlassLiquidEdge, fraction: Float): GlassLiquidEdge =
    if (fraction == 1f) edge else edge.copy(rim = edge.rim.copy(alpha = edge.rim.alpha * clampUnit(fraction)))
  return base.copy(
    lightVeil = light,
    darkVeil = dark,
    darkLargeVeil = dark,
    lightEdge = edge(base.lightEdge, selection.lightRim),
    darkEdge = edge(base.darkEdge, selection.darkRim),
    // La regola del vetro annidato, per l'ombra: quella di un tasto dentro un pannello, del suo lato.
    lightShadow = base.lightShadow.scaled(clampUnit(base.lightNesting.shadow)),
    darkShadow = base.darkShadow.scaled(clampUnit(base.darkNesting.shadow)),
    // Già applicata qui sopra: il nodo non deve applicarla di nuovo.
    lightNesting = GlassLiquidNestings.Unchanged,
    darkNesting = GlassLiquidNestings.Unchanged,
  )
}

/**
 * La pillola **premuta**, sopra la riga nascosta ([glassPuckRowProfile]) che porta già sfocatura, saturazione e
 * velo della barra: la mescola a uno (niente sfocatura in più, la catena la salta), saturazione spenta, e del velo
 * solo la selezione (un nero di quell'alfa, che non si ispessisce). L'ombreggiatura verso il contorno, il bordo,
 * la lente e l'ombra sono quelli della pillola a riposo: la sagoma resta la stessa.
 */
internal fun glassPuckOverRowProfile(
  barShortSideDp: Float,
  base: GlassLiquidProfile = GlassLiquidProfile.Default,
  selection: GlassPuckSelection = GlassPuckSelections.Ios27,
): GlassLiquidProfile {
  val rest = glassPuckProfile(barShortSideDp, base, selection)
  fun film(veil: GlassLiquidVeil, s: Float): GlassLiquidVeil =
    veil.copy(color = Color.Black.copy(alpha = clampUnit(s)), adapt = 0f)
  return rest.copy(
    lightVeil = film(rest.lightVeil, selection.light),
    darkVeil = film(rest.darkVeil, selection.dark),
    darkLargeVeil = film(rest.darkLargeVeil, selection.dark),
    frost = rest.frost.copy(mix = 1f, saturation = 1f),
  )
}

private fun clampUnit(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

private fun mix(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Componente per componente in sRGB, alfa compresa: lo stesso conto della ricetta. */
private fun mixColor(a: Color, b: Color, t: Float): Color = Color(
  red = mix(a.red, b.red, t),
  green = mix(a.green, b.green, t),
  blue = mix(a.blue, b.blue, t),
  alpha = mix(a.alpha, b.alpha, t),
)
