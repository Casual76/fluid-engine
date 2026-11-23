/*
 * Il VETRO TINTO (F6B.16, piano «iOS in Android»): gli elementi che l'engine riempiva col colore del tema (la pillola e il
 * chip scelti, il gettone, il tasto principale della barra, il disco di «Invia», il tasto in evidenza, i tasti pieni dei
 * visori e `FluidButton` pieno) diventano vetro tinto, come il vetro «prominente» e tinto di iOS 26.
 *
 * Parole di Alessio, 23 novembre 2026, sulla pillola scelta piena di verde: «questi tasti o elementi, con questo sfondo
 * del tema così tanto spinto non mi sono mai piaciuti molto: sembrano voler essere in vetro senza esserlo, e con dei
 * colori molto spinti rispetto a tutto il resto». Quindi:
 *  - **la tinta è mescolata al vetro, non stesa sopra**: è la tinta di Apple del materiale (`GlassTintMode.Colored`), posata
 *    sopra il velo con un'alfa minore di uno e con la luminosità che segue lo sfondo (R4 A7: piena sul bianco, al 58% sul
 *    nero; WWDC25 219 «Meet Liquid Glass»: la tinta genera una gamma di toni dalla luminosità di ciò che sta dietro, come
 *    un vetro colorato vero). Dove c'è una cattura il vetro rifrange, ha il filo chiaro della tinta (la tinta schiarita e
 *    meno satura di R4 A7, scelta C di Alessio del 12 novembre), il riflesso e l'ombra del colore della tinta;
 *  - **meno satura e più luminosa**: l'accento si schiarisce e si spegne in OKLab (luminosità verso il bianco, croma
 *    ridotta, tonalità ferma), quanto dice la dose di serie ([FluidTintedGlassProfile.Shipped]);
 *  - **lo scritto resta leggibile con l'inchiostro adattivo**: l'inchiostro segue il lato del vetro (chiaro o scuro) ed è
 *    quello di ogni altro vetro dell'engine (`#121214` sul lato chiaro, `#F2F2F5` sul lato scuro), così il vetro tinto non
 *    si stacca dal resto con un colore suo; dove non regge 4,5:1 cade sul nero o sul bianco pieni.
 *
 * La dose è una sola per tutta l'app ([FluidTintedGlassProfile.Shipped]): la C della tavola di F6B.16, la più piena,
 * scelta da Alessio il 23 novembre 2026. Le altre due colonne della tavola non sono rimaste nel codice.
 *
 * Due strade, la stessa ricetta:
 *  - **vetro vero** dove l'elemento è un vetro suo (il chip scelto su una pagina col canvas, il tasto principale della
 *    barra, il tasto in evidenza): `glassControlSurface` o `fluidGlass` con la tinta di [fluidTintedGlassTint];
 *  - **vetro dipinto** ([fluidTintedGlassFill]) dove l'elemento sta **dentro un altro vetro** (il gettone e «Invia» nel
 *    campo) o dove non c'è niente da catturare (una pagina piatta, un foglio di piattaforma): il corpo, l'ombreggiatura,
 *    il filo, la riga chiara e l'alone del vetro vero calcolati dalla stessa ricetta (`GlassPaintedProfile.kt`), con la
 *    stessa tinta. Su un vetro uniforme come il corpo di un campo la lente non mostrerebbe niente: il dipinto non perde
 *    nulla di ciò che si vedrebbe, e non aggiunge catture.
 *
 * Col vetro di prima ([FluidGlassModel.Classic], sotto Android 13 o chiesto da un antenato) la tinta `Colored` non si
 * legge: lì restano la pellicola d'accento e il tasto in evidenza di prima, che reggono l'etichetta da soli.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.LocalGlassPaintedPanel
import dev.antigravity.fluidengine.ui.glass.field.glassPainted
import dev.antigravity.fluidengine.ui.glass.field.glassPaintedBody
import dev.antigravity.fluidengine.ui.glass.field.glassPaintedControlSeen
import dev.antigravity.fluidengine.ui.glass.field.glassRecipe
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

// ------------------------------------------------------------------------------------------------------------
// La dose di serie
// ------------------------------------------------------------------------------------------------------------

/**
 * Quanto il vetro tinto si allontana dall'accento.
 *
 * @property lighten quanto la luminosità percettiva della tinta (la L di OKLab) va verso il bianco, 0..1.
 * @property chroma quanta saturazione (la C di OKLab) resta, 0..1; la tonalità non cambia.
 * @property coverage quanto la tinta copre il vetro: l'alfa della tinta `Colored` sopra il velo, 0..1. Il resto è vetro
 *   (velo, sfondo sfocato e rifratto).
 */
internal data class FluidTintedGlassMix(val lighten: Float, val chroma: Float, val coverage: Float)

/** I numeri del vetro tinto. */
internal object FluidTintedGlassProfile {
  /**
   * **La dose di serie: la C della tavola di F6B.16, scelta di Alessio del 23 novembre 2026, sera**, fra le tre colonne
   * fotografate sul Tab S9 in formato telefono accanto ai fermi di iOS 26 di R4 A7
   * (`artifacts/liquid-glass/sessioni/f6b-16/per-alessio/tavola-vetro-tinto.png`). È la più piena: quasi l'accento, un
   * decimo verso il bianco, la croma al 95%, e il vetro si vede per poco più di un quinto, col suo filo chiaro, il riflesso
   * e l'ombra del colore. Le altre due colonne erano A (0,40 / 0,70 / 0,45) e B (0,25 / 0,82 / 0,60, la proposta di
   * F6B.16): scritte qui per la storia, non sono più nel codice. Il comportamento della tinta è R4 A7 (tonalità ferma,
   * luminosità che segue lo sfondo); il contrasto dello scritto è provato su ogni accento della palette, in chiaro e in
   * scuro (`FluidTintedGlassTest`).
   */
  val Shipped: FluidTintedGlassMix = FluidTintedGlassMix(lighten = 0.10f, chroma = 0.95f, coverage = 0.78f)

  /**
   * Il margine con cui si sceglie l'inchiostro: 4,5:1 (WCAG 1.4.3) per un ventesimo in più, come il tasto in evidenza
   * ([FluidProminentContrastMargin]): il conto è sul corpo pieno, e il vetro aggiunge sopra luce e riflessi.
   */
  val InkMinContrast: Float get() = FluidProminentLabelContrast * FluidProminentContrastMargin

  /**
   * I fondi su cui l'inchiostro si prova, per lato: la pagina dell'engine, le sue superfici e i due estremi. Sono i fondi
   * su cui il vetro sta di quel lato (un vetro piccolo gira col fondo: sul lato chiaro c'è un fondo chiaro).
   */
  val LightGrounds: List<Color> = listOf(Color.White, Color(0xFFF2F2F7), Color(0xFFFCFCFE), Color(0xFFEBEBF0))
  val DarkGrounds: List<Color> = listOf(Color.Black, Color(0xFF0D0D0F), Color(0xFF1D1D20), Color(0xFF2C2C2F))

  /** La taglia di riferimento con cui si chiede la ricetta per il conto dell'inchiostro: una pillola. */
  const val ReferenceWidthDp: Float = 96f
  const val ReferenceHeightDp: Float = 40f
}

// ------------------------------------------------------------------------------------------------------------
// La tinta, il corpo, l'inchiostro: aritmetica pura
// ------------------------------------------------------------------------------------------------------------

/**
 * La tinta del vetro per l'accento [accent]: schiarita e spenta in OKLab come dice la dose di serie, con l'alfa della
 * copertura. È il colore da dare a `FluidGlass.Regular.tint(…)` o all'`overlay` di una tinta `Colored`. L'alfa di
 * [accent] non conta: gli accenti del tema sono opachi, e quanto copre lo decide la dose. Pura.
 */
internal fun fluidTintedGlassColor(accent: Color): Color {
  val mix = FluidTintedGlassProfile.Shipped
  val lab = oklab(accent.copy(alpha = 1f))
  val chroma = hypot(lab[1], lab[2]) * mix.chroma
  val hue = atan2(lab[2], lab[1])
  val lightness = lab[0] + (1f - lab[0]) * mix.lighten
  return oklchInGamut(lightness, chroma, hue).copy(alpha = mix.coverage)
}

/**
 * La tinta di vetro (`GlassTintMode.Colored`) per l'accento [accent]: la tinta di [fluidTintedGlassColor] sopra il velo,
 * con la luminosità che segue lo sfondo, il filo chiaro della tinta e l'ombra del suo colore (la ricetta lo fa da sé per
 * ogni tinta `Colored`). Ripiego e filetto restano quelli di [base]. Pura.
 */
internal fun fluidTintedGlassTint(accent: Color, base: GlassTint): GlassTint {
  val film = fluidTintedGlassColor(accent)
  return GlassTint(
    overlay = film,
    fallback = film.compositeOver(base.fallback),
    hairline = base.hairline,
    mode = GlassTintMode.Colored,
  )
}

/**
 * Il corpo del vetro tinto di [accent] sopra il fondo pieno [ground], dal lato [darkSide]: la ricetta di un controllo
 * (`GlassRole.Interactive`, ottica dei tasti) con la tinta `Colored` e la formula dello shader del corpo su un colore
 * pieno (`glassPaintedBody`). È il colore che sta dietro lo scritto. Pura.
 */
internal fun fluidTintedGlassBody(
  accent: Color,
  ground: Color,
  darkSide: Boolean,
  widthDp: Float = FluidTintedGlassProfile.ReferenceWidthDp,
  heightDp: Float = FluidTintedGlassProfile.ReferenceHeightDp,
  profile: GlassLiquidProfile = GlassLiquidProfile.Default,
): Color {
  val recipe = glassRecipe(
    role = GlassRole.Interactive,
    widthDp = widthDp,
    heightDp = heightDp,
    darkSide = darkSide,
    optics = GlassDefaults.buttonOptics(),
    tint = fluidTintedGlassColor(accent),
    profile = profile,
  )
  return glassPaintedBody(recipe, ground)
}

/** I fondi di prova del lato [darkSide] ([FluidTintedGlassProfile.LightGrounds], [FluidTintedGlassProfile.DarkGrounds]). */
internal fun fluidTintedGlassGrounds(darkSide: Boolean): List<Color> =
  if (darkSide) FluidTintedGlassProfile.DarkGrounds else FluidTintedGlassProfile.LightGrounds

/** Il contrasto peggiore dell'inchiostro [ink] sul vetro tinto di [accent], dal lato [darkSide], sui fondi del lato. Pura. */
internal fun fluidTintedGlassWorstContrast(
  accent: Color,
  ink: Color,
  darkSide: Boolean,
): Float = fluidTintedGlassGrounds(darkSide).minOf { ground ->
  fluidContrastRatio(ink, fluidTintedGlassBody(accent, ground, darkSide))
}

/**
 * Gli inchiostri che si provano, nell'ordine: quello neutro del lato (lo stesso degli altri vetri), il pieno del lato,
 * quello opposto. Pura.
 */
internal fun fluidTintedGlassInkCandidates(darkSide: Boolean): List<Color> =
  if (darkSide) listOf(FluidGlassInkOnDark, Color.White, Color.Black) else listOf(FluidGlassInkOnLight, Color.Black, Color.White)

private val inkCache = HashMap<Pair<Long, Boolean>, Color>()

/**
 * L'inchiostro dello scritto sul vetro tinto di [accent], dal lato [darkSide]: il primo di
 * [fluidTintedGlassInkCandidates] che regge [FluidTintedGlassProfile.InkMinContrast] su tutti i fondi del lato; se
 * nessuno regge, quello che fa più contrasto. Il conto si fa una volta per accento e lato. Pura.
 */
internal fun fluidTintedGlassInk(accent: Color, darkSide: Boolean): Color {
  val key = Pair(accent.copy(alpha = 1f).value.toLong(), darkSide)
  synchronized(inkCache) { inkCache[key]?.let { return it } }
  val candidates = fluidTintedGlassInkCandidates(darkSide)
  val scored = candidates.map { it to fluidTintedGlassWorstContrast(accent, it, darkSide) }
  val ink = scored.firstOrNull { it.second >= FluidTintedGlassProfile.InkMinContrast }?.first
    ?: scored.maxBy { it.second }.first
  synchronized(inkCache) { inkCache[key] = ink }
  return ink
}

// ------------------------------------------------------------------------------------------------------------
// In composizione
// ------------------------------------------------------------------------------------------------------------

/**
 * Se il vetro tinto di questo punto dell'albero è quello del materiale nuovo: la stessa regola del vetro dipinto
 * ([rememberPaintedGlassLiquid]). Col vetro di prima restano la pellicola d'accento e il tasto in evidenza di prima.
 */
@Composable
internal fun rememberFluidTintedGlassActive(): Boolean = rememberPaintedGlassLiquid(LocalFluidGlassModel.current)

/**
 * L'inchiostro dello scritto sul vetro tinto di [accent] **dal lato su cui si sta** (`GlassDefaults.isDarkSurface()`):
 * da chiamare dentro il contenuto del vetro, dove il lato è quello del vetro, che può aver girato col fondo.
 */
@Composable
internal fun rememberFluidTintedGlassInk(accent: Color = MaterialTheme.colorScheme.primary): Color {
  val dark = GlassDefaults.isDarkSurface()
  val liquid = rememberFluidTintedGlassActive()
  return remember(accent, dark, liquid) {
    // Col vetro di prima l'elemento è l'accento pieno: l'inchiostro che sull'accento si legge meglio, come prima.
    if (liquid) fluidTintedGlassInk(accent, dark) else fluidProminentInk(accent)
  }
}

/**
 * Per chi dà a un vetro una tinta d'accento piena (`FluidGlass.Regular.tint(accent)`, il tasto principale della barra):
 * la tinta del vetro tinto col materiale nuovo ([fluidTintedGlassColor]), l'accento com'era col vetro di prima.
 */
@Composable
internal fun rememberFluidTintedGlassColorOf(): (Color) -> Color {
  val liquid = rememberFluidTintedGlassActive()
  return remember(liquid) { { accent -> if (liquid) fluidTintedGlassColor(accent) else accent } }
}

/**
 * L'inchiostro del vetro tinto di un accento qualunque dal lato su cui si sta (vedi [rememberFluidTintedGlassInk]), per
 * chi ha più tasti tinti di colori diversi; col vetro di prima [classic], l'inchiostro di prima.
 */
@Composable
internal fun rememberFluidTintedGlassInkOf(classic: Color): (Color) -> Color {
  val dark = GlassDefaults.isDarkSurface()
  val liquid = rememberFluidTintedGlassActive()
  return remember(dark, liquid, classic) {
    { accent -> if (liquid) fluidTintedGlassInk(accent, dark) else classic }
  }
}

/**
 * Il vetro tinto **dipinto** di [shape] per l'accento [accent] (vedi l'intestazione del file): corpo, ombreggiatura, filo,
 * riga chiara e alone della ricetta di serie, con la tinta di [fluidTintedGlassColor], e l'ombra del vetro. Disegna
 * dietro il contenuto. Per l'inchiostro dello scritto, [rememberFluidTintedGlassInk].
 *
 * @param nested vero dentro un altro vetro (il gettone e «Invia» nel campo): velo e ombra ridotti dalla regola
 *   dell'annidato del profilo, come un vetro annidato vero.
 * @param pressure 0..1: il bagliore del vetro premuto, uniforme.
 * @param shadow falso per un vetro senza l'ombra che stacca: un elemento che stava piatto in una tavola (i tasti dei
 *   visori) resta della sua sagoma e non scurisce i vicini.
 */
@Composable
internal fun Modifier.fluidTintedGlassFill(
  shape: Shape,
  accent: Color = MaterialTheme.colorScheme.primary,
  nested: Boolean = false,
  pressure: () -> Float = { 0f },
  shadow: Boolean = true,
): Modifier {
  // La stessa scelta del modello di ogni vetro dipinto (`fluidStaticGlassSurface`): col vetro di prima, l'accento pieno.
  if (!rememberPaintedGlassLiquid(LocalFluidGlassModel.current)) {
    // Il vetro di prima: l'accento com'era, pieno. Non c'è una tinta `Colored` da leggere.
    return this
      .fluidTintedGlassMark(FluidTintedGlassMark(painted = true, liquid = false))
      .drawFluidClassicFill(shape, accent)
  }
  val scheme = MaterialTheme.colorScheme
  val darkSide = GlassDefaults.isDarkSurface()
  val panel = LocalGlassPaintedPanel.current
  val preference = LocalFluidGlassPreference.current
  val seen = glassPaintedControlSeen(panel, scheme.background, darkSide)
  val tint = remember(accent) { fluidTintedGlassColor(accent) }
  val inside = nested || panel != null
  val shadowOptics = remember(inside, darkSide) {
    val optics = GlassDefaults.panelOptics()
    if (!inside) optics
    else optics.copy(shadowAlpha = optics.shadowAlpha * GlassLiquidProfile.Default.nesting(darkSide).shadow.coerceIn(0f, 1f))
  }
  return this
    .fluidTintedGlassMark(FluidTintedGlassMark(painted = true, liquid = true))
    .then(if (shadow) Modifier.glassShadow(shape, shadowOptics) else Modifier)
    .glassPainted(
      shape = shape,
      darkSide = darkSide,
      seen = seen,
      nested = inside,
      tint = tint,
      pressure = pressure,
      preference = preference,
    )
}

/** Il riempimento d'accento di prima, per il vetro di prima ([FluidGlassModel.Classic]). */
private fun Modifier.drawFluidClassicFill(shape: Shape, accent: Color): Modifier =
  this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    onDrawBehind { drawOutline(outline, accent) }
  }

// ------------------------------------------------------------------------------------------------------------
// La prova: gli elementi tinti dicono di essere vetro
// ------------------------------------------------------------------------------------------------------------

/**
 * Ciò che un elemento tinto dice di sé, per le prove (F6B.16): se è vetro vero o dipinto, e se è il materiale nuovo. Non
 * è un'API per le app.
 */
internal data class FluidTintedGlassMark(val painted: Boolean, val liquid: Boolean)

/** La chiave della semantica con cui un elemento tinto si registra come vetro tinto. */
internal val FluidTintedGlassKey: SemanticsPropertyKey<FluidTintedGlassMark> = SemanticsPropertyKey("FluidTintedGlass")

/** Registra [mark] nella semantica del nodo (senza unire niente: la semantica di chi sta sopra resta com'è). */
internal fun Modifier.fluidTintedGlassMark(mark: FluidTintedGlassMark): Modifier =
  this.semantics { this[FluidTintedGlassKey] = mark }

// ------------------------------------------------------------------------------------------------------------
// OKLab (Björn Ottosson, 2020): la luminosità e la croma percettive in cui si schiarisce e si spegne la tinta
// ------------------------------------------------------------------------------------------------------------

private fun linear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

private fun encoded(c: Float): Float {
  val v = c.coerceIn(0f, 1f)
  return if (v <= 0.0031308f) 12.92f * v else 1.055f * v.pow(1f / 2.4f) - 0.055f
}

/** [color] (sRGB) in OKLab: L, a, b. */
internal fun oklab(color: Color): FloatArray {
  val r = linear(color.red)
  val g = linear(color.green)
  val b = linear(color.blue)
  val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
  val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
  val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
  return floatArrayOf(
    0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s,
    1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s,
    0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s,
  )
}

/** OKLab in sRGB, senza tagliare: null se il colore sta fuori dalla gamma sRGB. */
private fun fromOklab(lightness: Float, a: Float, b: Float): Color? {
  val l = (lightness + 0.3963377774f * a + 0.2158037573f * b).let { it * it * it }
  val m = (lightness - 0.1055613458f * a - 0.0638541728f * b).let { it * it * it }
  val s = (lightness - 0.0894841775f * a - 1.2914855480f * b).let { it * it * it }
  val r = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s
  val g = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s
  val bl = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s
  val tolerance = 0.0005f
  if (r < -tolerance || g < -tolerance || bl < -tolerance || r > 1f + tolerance || g > 1f + tolerance || bl > 1f + tolerance) return null
  return Color(encoded(r), encoded(g), encoded(bl))
}

/** Il colore OKLCh ([lightness], [chroma], [hue] in radianti) dentro la gamma sRGB: la croma scende finché ci sta. */
internal fun oklchInGamut(lightness: Float, chroma: Float, hue: Float): Color {
  val l = lightness.coerceIn(0f, 1f)
  var c = chroma.coerceAtLeast(0f)
  repeat(48) {
    fromOklab(l, c * cos(hue), c * sin(hue))?.let { return it }
    c *= 0.94f
  }
  return fromOklab(l, 0f, 0f) ?: Color(encoded(l * l * l), encoded(l * l * l), encoded(l * l * l))
}
