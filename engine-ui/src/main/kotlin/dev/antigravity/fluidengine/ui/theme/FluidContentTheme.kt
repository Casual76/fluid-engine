/*
 * Il tema di una sola pagina tinto dal suo contenuto (4.0, F8.7, idea `colore-dal-contenuto`). Codice del Fluid Engine.
 *
 * Dai colori letti da un'immagine (`FluidContentColors`) escono i **ruoli** col contrasto verificato, in chiaro e in
 * scuro: la tinta della pagina, il vetro delle barre e l'accento locale, ognuno col suo inchiostro ad almeno 4,5:1. Il
 * tema li applica alla sola pagina che lo avvolge e, quando l'app lo spegne, torna al tema normale sfumando: e' un cambio
 * guidato dall'app, quindi puo' sfumare («dito nitido, app sfocata»).
 *
 * Il riferimento e' Apple Music, che tinge la pagina del brano con la copertina e cambia tinta col brano. L'engine fa
 * l'estrazione, i ruoli e il passaggio; l'app sceglie l'immagine, la superficie (pagina, accento, i suoi vetri) e quando.
 * Una pagina che non lo usa non paga niente: senza `FluidContentTheme` non c'e' niente da leggere.
 */
package dev.antigravity.fluidengine.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.GlassTintMode
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.LocalFluidSurfaceSide
import dev.antigravity.fluidengine.ui.fluid.forSystemContrast
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * I ruoli di una pagina tinta dal suo contenuto, per un lato del tema ([isDark]). Ogni colore di fondo viaggia col suo
 * inchiostro, e ogni coppia fa **almeno 4,5:1** (WCAG 1.4.3, il testo), provato da un test su tutta la ruota dei colori,
 * sui grigi, sul nero, sul bianco e sui colori «tossici».
 *
 * @property pageTint il fondo della pagina: la tinta della copertina, chiara nel tema chiaro e scura nel tema scuro (il
 *   lato del tema non cambia: il vetro, le barre di sistema e i controlli restano dalla loro parte).
 * @property onPage il testo sulla pagina.
 * @property onPageVariant il testo secondario sulla pagina (anche lui a 4,5:1: e' testo).
 * @property barGlass la pellicola dei vetri delle barre che l'app tinge (un mini player, una barra in basso): opaca qui,
 *   l'alfa la mette [barTint].
 * @property onBar il testo e le icone su quel vetro, a 4,5:1 sia sulla pellicola sia sulla pagina che il vetro lascia
 *   passare.
 * @property accent l'accento locale (il tasto Riproduci, i link): a 4,5:1 sulla pagina, perche' si usa anche come testo.
 * @property onAccent il testo su un fondo pieno dell'accento.
 */
@Immutable
data class FluidContentRoles(
  val isDark: Boolean,
  val pageTint: Color,
  val onPage: Color,
  val onPageVariant: Color,
  val barGlass: Color,
  val onBar: Color,
  val accent: Color,
  val onAccent: Color,
) {
  /**
   * La tinta per un vetro di barra che l'app vuole del colore della pagina: la pellicola [barGlass] come tinta di Apple
   * (`GlassTintMode.Colored`), col filetto del suo inchiostro. Va con `fluidGlassPanel` o con i vetri che accettano una
   * `GlassTint`.
   */
  @Composable
  fun barTint(): GlassTint = GlassTint(
    overlay = barGlass.copy(alpha = if (isDark) FluidContentRecipe.BarAlphaDark else FluidContentRecipe.BarAlphaLight),
    fallback = barGlass.copy(alpha = 0.96f),
    hairline = onBar.copy(alpha = if (isDark) 0.16f else 0.10f),
    mode = GlassTintMode.Colored,
  ).forSystemContrast()
}

/**
 * Le quote dei ruoli, in OKLCH (luminosita' percepita `L` fra 0 e 1, croma `C`). A occhio, 24 novembre 2026, sulla
 * pagina del brano di Apple Music: la tinta della pagina si riconosce ma resta un fondo, l'accento e' la copertina piu'
 * piena. La croma e' un tetto: una copertina spenta da' una pagina spenta, e i colori «tossici» si fermano li'. Poi ogni
 * inchiostro si sposta del minimo che serve per arrivare a 4,5:1.
 */
internal object FluidContentRecipe {
  const val MinContrast = 4.5f

  // Tema chiaro.
  const val PageLLight = 0.94f
  const val PageCLight = 0.06f
  const val BarLLight = 0.975f
  const val BarCLight = 0.03f
  const val InkLLight = 0.22f
  const val InkCLight = 0.03f
  const val VariantLLight = 0.45f
  const val VariantCLight = 0.04f
  const val AccentLLight = 0.55f
  const val AccentCLight = 0.17f

  // Tema scuro.
  const val PageLDark = 0.24f
  const val PageCDark = 0.07f
  const val BarLDark = 0.31f
  const val BarCDark = 0.05f
  const val InkLDark = 0.97f
  const val InkCDark = 0.012f
  const val VariantLDark = 0.80f
  const val VariantCDark = 0.025f
  const val AccentLDark = 0.78f
  const val AccentCDark = 0.14f

  /** L'alfa della pellicola di una barra tinta: quella di `barTint()` dell'engine, un poco piu' piena (0,58 / 0,62). */
  const val BarAlphaLight = 0.62f
  const val BarAlphaDark = 0.58f
}

/**
 * I ruoli di una pagina tinta da [colors], per il lato [isDark]. Pura: si prova sulla JVM su tutta la ruota dei colori.
 *
 * Tinta e croma vengono dal seme ([FluidContentColors.seed]); un'immagine monocroma da' ruoli neutri. Ogni inchiostro
 * parte dalla sua quota ([FluidContentRecipe]) e si allontana dal suo fondo del minimo che serve per fare 4,5:1; l'accento
 * fa 4,5:1 sulla pagina e col suo inchiostro.
 */
fun fluidContentRoles(colors: FluidContentColors, isDark: Boolean): FluidContentRoles {
  val seed = Oklch.of(colors.seed)
  val chroma = if (colors.monochrome) 0f else seed.c
  val hue = seed.h
  fun tone(l: Float, cMax: Float): Color = Oklch(l, min(chroma, cMax), hue).toColor()
  val r = FluidContentRecipe
  val page = if (isDark) tone(r.PageLDark, r.PageCDark) else tone(r.PageLLight, r.PageCLight)
  val bar = if (isDark) tone(r.BarLDark, r.BarCDark) else tone(r.BarLLight, r.BarCLight)
  val min = r.MinContrast
  fun legibleOn(start: Color, grounds: List<Color>): Color =
    legibleTone(start, towardLight = isDark) { c -> grounds.all { contrastRatio(c, it) >= min } }

  val onPage = legibleOn(if (isDark) tone(r.InkLDark, r.InkCDark) else tone(r.InkLLight, r.InkCLight), listOf(page, bar))
  val onPageVariant = legibleOn(
    if (isDark) tone(r.VariantLDark, r.VariantCDark) else tone(r.VariantLLight, r.VariantCLight),
    listOf(page),
  )
  val accentStart = if (isDark) tone(r.AccentLDark, r.AccentCDark) else tone(r.AccentLLight, r.AccentCLight)
  val accent = legibleTone(accentStart, towardLight = isDark) { c ->
    contrastRatio(c, page) >= min && contrastRatio(highestContrastContent(c), c) >= min
  }
  return FluidContentRoles(
    isDark = isDark,
    pageTint = page,
    onPage = onPage,
    onPageVariant = onPageVariant,
    barGlass = bar,
    onBar = onPage,
    accent = accent,
    onAccent = highestContrastContent(accent),
  )
}

/**
 * Da che parte sta la pagina tinta.
 *
 * - [Content], di serie: **come Apple Music** (iOS 26.4, la pagina dell'album e della playlist). La pagina prende il
 *   colore della copertina pieno e medio-scuro, col testo bianco, in chiaro e in scuro; solo una copertina chiarissima e
 *   gialla (dove scurire darebbe un verde fango) resta chiara col testo nero, come la playlist gialla. Dentro, il lato
 *   del vetro, dei controlli e delle icone di stato e' quello della pagina, non quello del tema.
 * - [Theme]: la pagina resta dalla parte del tema (una tinta chiara col testo scuro nel tema chiaro, una scura nel tema
 *   scuro): piu' discreta, per un'app che tinge una pagina qualsiasi e non vuole che il tema cambi lato.
 */
enum class FluidContentSide { Content, Theme }

/**
 * Le quote del lato [FluidContentSide.Content], misurate sulle immagini di Apple Music (F8.7, `riferimenti/fonti.md`):
 * la copertina azzurro chiaro (#B7E3F6) diventa uno sfondo azzurro medio (#5D90A5), stessa tinta, molta meno
 * saturazione, piu' scuro di quanto serve al testo bianco; il blu e il rosso della 26.4 restano pieni. Fiducia media
 * (cinque immagini), 24 novembre 2026.
 */
internal object FluidContentApple {
  /** Quanto la pagina scende sotto la luminosita' del seme (OKLCH): **0,18**. */
  const val PageDrop = 0.18f

  /** La pagina scura sta fra **0,34** e **0,52** di luminosita' (OKLCH). */
  const val PageMinL = 0.34f
  const val PageMaxL = 0.52f

  /** La croma della pagina: **l'80 %** del seme, al massimo **0,13** (il rosso della 26.4 resta rosso, il neon no). */
  const val PageChromaShare = 0.8f
  const val PageMaxC = 0.13f

  /** La pagina chiara (la copertina gialla chiarissima): luminosita' **0,90**, croma fino a **0,16**. */
  const val LightPageL = 0.90f
  const val LightPageMaxC = 0.16f

  /** Una copertina e' «chiarissima» sopra **0,86** di luminosita' (OKLCH). */
  const val LightSeedL = 0.86f

  /** La fascia del giallo in OKLCH, in gradi: **80–120°**. */
  const val YellowFrom = 80f
  const val YellowTo = 120f

  /** Una copertina monocroma da' una pagina chiara sopra **0,80** di luminosita' della sua media. */
  const val MonoLightL = 0.80f

  /**
   * Il peso della saturazione nella scelta del seme (F8.7b): ogni fascia di tinta conta per il suo peso per la croma
   * OKLCH della sua media alla **2**. Con la media della finestra piu' pesante il fiore della galleria (foglie gialle,
   * verdi e bruno-rosse, croma 0,08–0,10) dava un oliva spento; cosi' vince il verde delle foglie, come Apple Music
   * prende il colore vivo di una copertina. Sulle copertine di una tinta sola vince la stessa fascia di prima e il seme
   * non cambia. A occhio, 24 novembre 2026, sulla tavola contro Apple Music 26.4.
   */
  const val SeedChromaPower = 2f

  /** Una fascia diversa dalla piu' pesante vince solo se pesa almeno il **15 %** della massa colorata (una macchia no). */
  const val VividMinShare = 0.15

  /**
   * ...e solo se e' un altro colore: almeno **20°** di tinta OKLCH di distanza dal colore vivo di prima, e almeno il
   * **15 %** di croma in piu'. Sulla copertina «Sabbia e luna» la fascia vincente e' la stessa tinta (1° di distanza) e il
   * seme resta al livello; sul fiore la tinta si sposta di 27° e la croma cresce del 32 %. A occhio, 24 novembre 2026.
   */
  const val VividHueGap = 20.0
  const val VividChromaGain = 1.15f

  /**
   * La pagina presa dal colore vivo ([FluidContentColors.vivid] diverso da [FluidContentColors.vibrant]) parte da
   * **0,46** di luminosita' invece che dal seme meno [PageDrop]: scesa di 0,18 la foglia verde del fiore diventava un
   * verde quasi nero (0,36). Poi scende quanto serve al testo, come ogni pagina. Le copertine di una tinta sola non
   * passano di qui. A occhio, 24 novembre 2026, sulla tavola contro Apple Music 26.4 (l'album blu e' un blu medio).
   */
  const val VividPageL = 0.46f
}

/**
 * I ruoli di una pagina tinta da [colors], dalla parte [side]: [FluidContentSide.Theme] e' [fluidContentRoles] col lato
 * del tema ([isDark]); [FluidContentSide.Content] e' il lato della copertina, come Apple Music, e non dipende dal tema.
 * Ogni coppia fa almeno 4,5:1 in tutti e due i casi.
 */
fun fluidContentRoles(colors: FluidContentColors, isDark: Boolean, side: FluidContentSide): FluidContentRoles =
  when (side) {
    FluidContentSide.Theme -> fluidContentRoles(colors, isDark)
    FluidContentSide.Content -> contentSideRoles(colors)
  }

private fun contentSideRoles(colors: FluidContentColors): FluidContentRoles {
  val a = FluidContentApple
  val min = FluidContentRecipe.MinContrast
  // Il seme del lato della copertina e' il colore piu' vivo che pesa davvero (F8.7b), non la media della tinta piu' pesante.
  val seed = Oklch.of(if (colors.monochrome) colors.seed else colors.vivid)
  val hue = seed.h
  val degrees = ((Math.toDegrees(hue.toDouble()) + 360.0) % 360.0).toFloat()
  val lightPage = if (colors.monochrome) {
    Oklch.of(colors.dominant).l >= a.MonoLightL
  } else {
    seed.l >= a.LightSeedL && degrees in a.YellowFrom..a.YellowTo
  }
  val chroma = if (colors.monochrome) 0f else seed.c
  fun tone(l: Float, c: Float): Color = Oklch(l, c, hue).toColor()
  if (lightPage) {
    val page = tone(a.LightPageL, minOf(chroma, a.LightPageMaxC))
    val bar = tone(0.95f, minOf(chroma, a.LightPageMaxC * 0.6f))
    val ink = legibleTone(tone(0.20f, minOf(chroma, 0.03f)), towardLight = false) { c ->
      contrastRatio(c, page) >= min && contrastRatio(c, bar) >= min
    }
    val variant = legibleTone(tone(0.40f, minOf(chroma, 0.05f)), towardLight = false) { contrastRatio(it, page) >= min }
    val accent = legibleTone(tone(0.28f, minOf(chroma, 0.10f)), towardLight = false) { c -> contrastRatio(c, page) >= min }
    return FluidContentRoles(
      isDark = false, pageTint = page, onPage = ink, onPageVariant = variant, barGlass = bar, onBar = ink,
      accent = accent, onAccent = if (contrastRatio(page, accent) >= min) page else highestContrastContent(accent),
    )
  }
  val pageC = minOf(chroma * a.PageChromaShare, a.PageMaxC)
  val variantStart = tone(0.86f, minOf(chroma, 0.04f))
  val vividSeed = !colors.monochrome && colors.vivid != colors.vibrant
  val startL = (seed.l - a.PageDrop).let { if (vividSeed) maxOf(it, a.VividPageL) else it }.coerceIn(a.PageMinL, a.PageMaxL)
  // La pagina scende quanto serve perche' anche il testo secondario faccia 4,5:1 (e il bianco di piu').
  val page = legibleTone(tone(startL, pageC), towardLight = false) { c ->
    contrastRatio(variantStart, c) >= min
  }
  val pageL = Oklch.of(page).l
  val bar = tone(minOf(pageL + 0.06f, 0.6f), pageC)
  val ink = legibleTone(tone(0.98f, minOf(chroma, 0.01f)), towardLight = true) { c ->
    contrastRatio(c, page) >= min && contrastRatio(c, bar) >= min
  }
  val variant = legibleTone(variantStart, towardLight = true) { contrastRatio(it, page) >= min }
  // L'accento: una tinta chiara della copertina, come il tasto Riproduci bianco della 26.4 col testo del colore della pagina.
  val accent = legibleTone(tone(0.92f, minOf(chroma, 0.06f)), towardLight = true) { c -> contrastRatio(c, page) >= min }
  return FluidContentRoles(
    isDark = true, pageTint = page, onPage = ink, onPageVariant = variant, barGlass = bar, onBar = ink,
    accent = accent, onAccent = if (contrastRatio(page, accent) >= min) page else highestContrastContent(accent),
  )
}

/** Il rapporto di contrasto WCAG fra due colori opachi. */
internal fun contrastRatio(a: Color, b: Color): Float {
  val la = a.luminance()
  val lb = b.luminance()
  return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/**
 * [start], o lo stesso colore spostato in luminosita' (verso il chiaro con [towardLight], verso lo scuro altrimenti) del
 * minimo che soddisfa [ok]. Il contrasto con un fondo dell'altro lato cresce in modo monotono con lo spostamento, e una
 * ricerca binaria trova il punto; se nemmeno l'estremo basta, il bianco o il nero.
 */
private fun legibleTone(start: Color, towardLight: Boolean, ok: (Color) -> Boolean): Color {
  if (ok(start)) return start
  val lch = Oklch.of(start)
  val end = if (towardLight) 1f else 0f
  val extreme = Oklch(end, lch.c, lch.h).toColor()
  if (!ok(extreme)) return if (towardLight) Color.White else Color.Black
  var low = 0f
  var high = 1f
  repeat(18) {
    val mid = (low + high) / 2f
    if (ok(Oklch(lch.l + (end - lch.l) * mid, lch.c, lch.h).toColor())) high = mid else low = mid
  }
  return Oklch(lch.l + (end - lch.l) * high, lch.c, lch.h).toColor()
}

/**
 * Un colore in OKLCH (Björn Ottosson, 2020): luminosita' percepita [l] (0–1), croma [c], tinta [h] in radianti. Serve a
 * spostare la luminosita' tenendo la tinta, cosa che HSV non sa fare (un giallo e un blu «al 50 %» hanno luminosita'
 * diversissime). [toColor] riporta in sRGB riducendo la croma finche' il colore non ci sta.
 */
internal data class Oklch(val l: Float, val c: Float, val h: Float) {
  fun toColor(): Color {
    val lc = l.coerceIn(0f, 1f)
    if (inGamut(lc, c, h)) return srgb(lc, c, h)
    var low = 0f
    var high = c
    repeat(16) {
      val mid = (low + high) / 2f
      if (inGamut(lc, mid, h)) low = mid else high = mid
    }
    return srgb(lc, low, h)
  }

  companion object {
    fun of(color: Color): Oklch {
      val r = toLinear(color.red)
      val g = toLinear(color.green)
      val b = toLinear(color.blue)
      val l = cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b)
      val m = cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b)
      val s = cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b)
      val ll = 0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s
      val aa = 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s
      val bb = 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s
      return Oklch(ll, hypot(aa, bb), atan2(bb, aa))
    }

    private fun linear(l: Float, c: Float, h: Float): FloatArray {
      val a = c * cos(h)
      val b = c * sin(h)
      val l1 = l + 0.3963377774f * a + 0.2158037573f * b
      val m1 = l - 0.1055613458f * a - 0.0638541728f * b
      val s1 = l - 0.0894841775f * a - 1.2914855480f * b
      val l3 = l1 * l1 * l1
      val m3 = m1 * m1 * m1
      val s3 = s1 * s1 * s1
      return floatArrayOf(
        4.0767416621f * l3 - 3.3077115913f * m3 + 0.2309699292f * s3,
        -1.2684380046f * l3 + 2.6097574011f * m3 - 0.3413193965f * s3,
        -0.0041960863f * l3 - 0.7034186147f * m3 + 1.7076147010f * s3,
      )
    }

    private fun inGamut(l: Float, c: Float, h: Float): Boolean =
      linear(l, c, h).all { it in -0.0005f..1.0005f }

    private fun srgb(l: Float, c: Float, h: Float): Color {
      val rgb = linear(l, c, h)
      return Color(fromLinear(rgb[0]), fromLinear(rgb[1]), fromLinear(rgb[2]))
    }

    private fun toLinear(c: Float): Float = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

    private fun fromLinear(c: Float): Float {
      val v = c.coerceIn(0f, 1f)
      return if (v <= 0.0031308f) 12.92f * v else 1.055f * v.pow(1f / 2.4f) - 0.055f
    }
  }
}

/**
 * I ruoli della pagina tinta in cui ci si trova, mentre `FluidContentTheme` la tinge (anche a meta' del passaggio), o
 * null fuori da una pagina tinta o a tema normale. Per i pezzi che l'app disegna da se': il vetro di una barra
 * ([FluidContentRoles.barTint]), un'aura, un fondo che sfuma nella copertina.
 */
val LocalFluidContentRoles = compositionLocalOf<FluidContentRoles?> { null }

/**
 * Tinge **la sola pagina** che avvolge coi colori del suo contenuto, e torna al tema normale quando [colors] diventa null
 * (o [enabled] falso), sfumando in [FluidContentColorDefaults.TransitionMillis]. Anche il cambio da una tinta all'altra
 * (un brano dopo l'altro) sfuma: e' un cambio guidato dall'app, quindi puo' sfumare. Col movimento ridotto e' immediato.
 *
 * Cosa cambia dentro: `MaterialTheme.colorScheme` con lo sfondo, le superfici, il testo e i contorni tinti dalla pagina
 * ([page]) e `primary` con l'accento locale ([accent]), dai [FluidContentRoles] del lato del tema. Tutto quello che legge
 * il tema (righe, card, tasti, il velo dei vetri) segue da se'. I ruoli si leggono da [LocalFluidContentRoles].
 *
 * Il lato (chiaro o scuro) lo dice [side]: di serie quello della copertina, come Apple Music (la pagina blu col testo
 * bianco anche nel tema chiaro; dentro, il vetro, i controlli e le icone di stato girano col lato della pagina); con
 * [FluidContentSide.Theme] quello del tema di fuori. Da mettere attorno al contenuto della pagina, non attorno all'app.
 *
 * Ferma non costa niente; durante il passaggio il tema cambia a ogni fotogramma (solo il colore, per mezzo secondo).
 */
@Composable
fun FluidContentTheme(
  colors: FluidContentColors?,
  enabled: Boolean = true,
  page: Boolean = true,
  accent: Boolean = true,
  side: FluidContentSide = FluidContentSide.Content,
  content: @Composable () -> Unit,
) {
  val base = MaterialTheme.colorScheme
  val isDark = GlassDefaults.darkSurface(LocalFluidSurfaceSide.current, base.surface)
  val target = remember(colors, enabled, isDark, side) {
    if (enabled && colors != null) fluidContentRoles(colors, isDark, side) else null
  }
  // Lo schema di base del lato della pagina: quello del tema, o quello dell'altro lato con lo stesso accento.
  val sides = LocalFluidThemeSides.current
  val baseFor: (Boolean) -> ColorScheme = { dark ->
    if (dark == isDark) base else sides?.scheme(dark) ?: if (dark) darkColorScheme() else lightColorScheme()
  }
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val transition = remember { ContentTintTransition(target) }
  LaunchedEffect(target, reduced) { transition.animateTo(target, reduced, baseRoles(base, isDark)) }

  val fraction = transition.fraction
  val from = transition.from
  val to = transition.to
  val fromScheme = remember(base, from, page, accent) { from?.let { tintedScheme(baseFor(it.isDark), it, page, accent) } ?: base }
  val toScheme = remember(base, to, page, accent) { to?.let { tintedScheme(baseFor(it.isDark), it, page, accent) } ?: base }
  // Il lato che vedono il vetro e i controlli: quello della pagina d'arrivo da meta' del passaggio in poi.
  val shownSide = if (fraction >= 0.5f) to?.isDark ?: isDark else from?.isDark ?: isDark
  val scheme = when {
    fraction >= 1f -> toScheme
    fraction <= 0f -> fromScheme
    else -> lerpScheme(fromScheme, toScheme, fraction)
  }
  val roles = when {
    to == null && fraction >= 1f -> null
    from == null && to == null -> null
    else -> lerpRoles(from ?: baseRoles(base, isDark), to ?: baseRoles(base, isDark), fraction.coerceIn(0f, 1f))
  }
  // Sempre lo stesso albero, tinto o no: accendere e spegnere la tinta non deve ricomporre la pagina da capo (lo stato
  // dello scorrimento, i campi, le animazioni restano).
  CompositionLocalProvider(
    LocalFluidContentRoles provides roles,
    LocalFluidSurfaceSide provides if (page) shownSide else isDark,
  ) {
    MaterialTheme(
      colorScheme = scheme,
      typography = MaterialTheme.typography,
      shapes = MaterialTheme.shapes,
      motionScheme = MaterialTheme.motionScheme,
      content = content,
    )
  }
}

/** Il passaggio fra due tinte (o fra il tema normale e una tinta): da [from] a [to], a [fraction]. */
@Stable
internal class ContentTintTransition(initial: FluidContentRoles?) {
  var from: FluidContentRoles? by mutableStateOf(initial)
    private set
  var to: FluidContentRoles? by mutableStateOf(initial)
    private set
  private val progress = Animatable(1f)
  val fraction: Float get() = progress.value

  /** Porta la tinta a [target] (null: il tema normale, i cui ruoli sono [base]). */
  suspend fun animateTo(target: FluidContentRoles?, reduced: Boolean, base: FluidContentRoles) {
    if (target == to && progress.value >= 1f) return
    // Il punto di partenza e' quello che si vede adesso, anche a meta' di un passaggio interrotto.
    val f = progress.value
    val current = to
    val previous = from
    from = when {
      f >= 1f || previous == current -> current
      else -> lerpRoles(previous ?: base, current ?: base, f)
    }
    to = target
    progress.snapTo(0f)
    if (reduced) {
      progress.snapTo(1f)
    } else {
      progress.animateTo(1f, FluidMotion.color(FluidContentColorDefaults.TransitionMillis))
    }
  }
}

/** I ruoli del tema normale, per sfumare da e verso una tinta. */
private fun baseRoles(base: ColorScheme, isDark: Boolean) = FluidContentRoles(
  isDark = isDark,
  pageTint = base.background,
  onPage = base.onSurface,
  onPageVariant = base.onSurfaceVariant,
  barGlass = base.surface,
  onBar = base.onSurface,
  accent = base.primary,
  onAccent = base.onPrimary,
)

internal fun lerpRoles(a: FluidContentRoles, b: FluidContentRoles, t: Float) = FluidContentRoles(
  isDark = b.isDark,
  pageTint = lerp(a.pageTint, b.pageTint, t),
  onPage = lerp(a.onPage, b.onPage, t),
  onPageVariant = lerp(a.onPageVariant, b.onPageVariant, t),
  barGlass = lerp(a.barGlass, b.barGlass, t),
  onBar = lerp(a.onBar, b.onBar, t),
  accent = lerp(a.accent, b.accent, t),
  onAccent = lerp(a.onAccent, b.onAccent, t),
)

/**
 * Lo schema del tema con la pagina e l'accento tinti dai ruoli. Le superfici salgono dalla tinta della pagina come quelle
 * dell'engine salgono dal fondo neutro: le card un poco piu' chiare nel tema chiaro (come le righe raggruppate di iOS
 * sopra un fondo colorato), un poco piu' chiare anche nel tema scuro.
 */
internal fun tintedScheme(base: ColorScheme, roles: FluidContentRoles, page: Boolean, accent: Boolean): ColorScheme {
  var scheme = base
  if (page) {
    val p = roles.pageTint
    val ink = roles.onPage
    val light = !roles.isDark
    fun up(share: Float) = if (light) lerp(p, Color.White, share) else lerp(p, ink, share)
    scheme = scheme.copy(
      background = p,
      onBackground = ink,
      surface = p,
      onSurface = ink,
      surfaceVariant = if (light) lerp(p, ink, 0.05f) else lerp(p, ink, 0.10f),
      onSurfaceVariant = roles.onPageVariant,
      surfaceBright = if (light) up(0.6f) else up(0.14f),
      surfaceDim = p,
      surfaceContainerLowest = if (light) up(0.75f) else lerp(p, Color.Black, 0.25f),
      surfaceContainerLow = if (light) up(0.6f) else up(0.05f),
      surfaceContainer = if (light) up(0.45f) else up(0.07f),
      surfaceContainerHigh = if (light) lerp(p, ink, 0.04f) else up(0.10f),
      surfaceContainerHighest = if (light) lerp(p, ink, 0.08f) else up(0.14f),
      outline = lerp(p, ink, if (light) 0.30f else 0.32f),
      outlineVariant = lerp(p, ink, if (light) 0.14f else 0.18f),
    )
  }
  if (accent) {
    val container = lerp(if (page) roles.pageTint else base.surfaceContainerLow, roles.accent, if (roles.isDark) 0.28f else 0.17f)
    scheme = scheme.copy(
      primary = roles.accent,
      onPrimary = roles.onAccent,
      inversePrimary = roles.accent,
      primaryContainer = container,
      onPrimaryContainer = highestContrastContent(container),
    )
  }
  return scheme
}

private fun lerpScheme(a: ColorScheme, b: ColorScheme, t: Float): ColorScheme = b.copy(
  primary = lerp(a.primary, b.primary, t),
  onPrimary = lerp(a.onPrimary, b.onPrimary, t),
  inversePrimary = lerp(a.inversePrimary, b.inversePrimary, t),
  primaryContainer = lerp(a.primaryContainer, b.primaryContainer, t),
  onPrimaryContainer = lerp(a.onPrimaryContainer, b.onPrimaryContainer, t),
  background = lerp(a.background, b.background, t),
  onBackground = lerp(a.onBackground, b.onBackground, t),
  surface = lerp(a.surface, b.surface, t),
  onSurface = lerp(a.onSurface, b.onSurface, t),
  surfaceVariant = lerp(a.surfaceVariant, b.surfaceVariant, t),
  onSurfaceVariant = lerp(a.onSurfaceVariant, b.onSurfaceVariant, t),
  surfaceBright = lerp(a.surfaceBright, b.surfaceBright, t),
  surfaceDim = lerp(a.surfaceDim, b.surfaceDim, t),
  surfaceContainerLowest = lerp(a.surfaceContainerLowest, b.surfaceContainerLowest, t),
  surfaceContainerLow = lerp(a.surfaceContainerLow, b.surfaceContainerLow, t),
  surfaceContainer = lerp(a.surfaceContainer, b.surfaceContainer, t),
  surfaceContainerHigh = lerp(a.surfaceContainerHigh, b.surfaceContainerHigh, t),
  surfaceContainerHighest = lerp(a.surfaceContainerHighest, b.surfaceContainerHighest, t),
  outline = lerp(a.outline, b.outline, t),
  outlineVariant = lerp(a.outlineVariant, b.outlineVariant, t),
)

/** Vero se due colori sono quasi uguali (per i test: sotto mezzo livello su 255 per canale). */
internal fun sameColor(a: Color, b: Color): Boolean =
  abs(a.red - b.red) < 0.002f && abs(a.green - b.green) < 0.002f && abs(a.blue - b.blue) < 0.002f && abs(a.alpha - b.alpha) < 0.002f
