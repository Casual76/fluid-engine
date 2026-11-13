/*
 * Il vetro che si adatta (piano «iOS in Android», 3.9, F2.2 e F2.3): il cursore dell'utente fra trasparente e colorato,
 * ciò che un vetro sa dello sfondo che ha dietro, lo stato che porta la misura della sonda al materiale, il giro
 * chiaro/scuro dei piccoli e l'inchiostro che lo segue.
 *
 * Chi misura è la sonda (`ui/glass/field/GlassProbe.kt`, F2.1); chi trasforma la misura in numeri del materiale è la
 * ricetta (`glassRecipe`, con `GlassAdaptiveMaterial`). Qui c'è il tratto in mezzo: i bersagli della sonda, che
 * arrivano poche volte al secondo, diventano valori animati coi tempi di Apple, letti dalla superficie in disegno.
 *
 * **Il giro (F2.3).** Un vetro piccolo sopra uno sfondo che passa da chiaro a scuro gira lato: il corpo scende in
 * 300 ms, l'inchiostro attraversa il corpo (un fotogramma a contrasto zero) e risale dall'altra parte (R4 B6). La legge
 * è [GlassFlipLaw], pura; lo stato ([GlassAdaptiveState]) la fa correre sull'orologio dei fotogrammi e scrive due numeri
 * che si leggono **in disegno**: il lato del corpo (la ricetta) e la trasparenza dell'inchiostro (il contenuto). La
 * composizione vede solo **da che parte** sta l'inchiostro, che cambia una volta per giro, nel fotogramma in cui il
 * contenuto è trasparente: nessuna ricomposizione per fotogramma.
 *
 * **Chi gira.** Solo un vetro il cui contenuto segue l'inchiostro ([GlassInk]): un vetro che girasse con il testo scuro
 * del tema sopra diventerebbe illeggibile proprio nel lato in cui doveva aiutare. Lo forniscono i componenti dell'engine
 * che sanno cosa disegnano dentro (i tasti di vetro, la capsula flottante); un `glassSurface` qualunque resta del lato
 * del tema, col materiale adattivo di F2.2.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveMaterial
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveTuning
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveTarget
import dev.antigravity.fluidengine.ui.glass.field.GlassBackdropStats
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidAdaptiveMillis
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidLargeDarkenMillis
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeState
import dev.antigravity.fluidengine.ui.glass.field.glassBlackShare
import dev.antigravity.fluidengine.ui.theme.LocalFluidThemeSides
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

// ------------------------------------------------------------------------------------------------------------
// Il cursore dell'utente
// ------------------------------------------------------------------------------------------------------------

/**
 * I punti notevoli del cursore del vetro: in iOS 27 è in Impostazioni › Aspetto › Liquid Glass, e ha sostituito la
 * scelta a due della 26.1 (R5 M2). Il valore va da [Transparent] a [Colored]; [Default] è il vetro dell'engine così
 * com'è, al bit.
 */
object FluidGlassPreference {
  /** Ultra trasparente: poca diffusione, il testo dietro il vetro si legge piegato dalla lente. */
  const val Transparent: Float = 0f

  /** Di serie: il vetro del profilo, senza ritocchi. */
  const val Default: Float = 0.5f

  /** Del tutto colorato: una lastra quasi opaca del colore dello sfondo, nessuna forma dietro. */
  const val Colored: Float = 1f
}

/**
 * Il cursore del vetro per questo sottoalbero, da [FluidGlassPreference.Transparent] (0) a
 * [FluidGlassPreference.Colored] (1); di serie [FluidGlassPreference.Default].
 *
 * Muove insieme la diffusione e il velo di ogni vetro del materiale nuovo, soprattutto la diffusione (in iOS 27 il velo
 * cambia poco fra gli estremi). Non tocca la variante limpida, il vetro di prima ([FluidGlassModel.Classic]) né il
 * vetro sotto Android 13. **Il valore lo conserva l'app**: la riga facoltativa di `fluidAppearanceSection`
 * (`glassPreference`, `onGlassPreferenceChange`) disegna il cursore e restituisce il valore, e l'app lo fornisce qui
 * attorno al suo contenuto, come fa con il resto dell'aspetto:
 *
 *     CompositionLocalProvider(LocalFluidGlassPreference provides settings.glassPreference) { content() }
 *
 * Un valore fuori da 0..1 si legge al bordo; uno non finito si legge [FluidGlassPreference.Default].
 */
val LocalFluidGlassPreference: ProvidableCompositionLocal<Float> = compositionLocalOf { FluidGlassPreference.Default }

// ------------------------------------------------------------------------------------------------------------
// Ciò che un vetro sa dello sfondo
// ------------------------------------------------------------------------------------------------------------

/**
 * Ciò che un vetro del materiale nuovo sa di sé e dello sfondo che ha dietro, per chi disegna **sopra** di lui: da quale
 * lato sta (chiaro o scuro), quanto è scurito, e quanto è luminoso lo sfondo.
 *
 * Non si costruisce: lo fornisce il vetro al suo contenuto con [LocalFluidGlassAppearance], insieme all'inchiostro
 * (`LocalContentColor`) del suo lato. Fuori da un vetro adattivo non c'è. Cambia **una volta per giro**, nel momento in
 * cui l'inchiostro passa dall'altra parte: non è un valore che scorre durante l'animazione (quella si disegna senza
 * ricomporre), e i suoi numeri sono quelli della lettura che ha deciso il lato.
 *
 * @property isDark se il vetro sta sul lato scuro: il contenuto sopra deve essere chiaro.
 * @property darkness quanto il vetro è scurito, 0..1. Un vetro piccolo gira e passa da 0 a 1 nel giro; uno grande
 *   (pannelli, fogli) scurisce in continuo con la quota di nero che ha sotto e può stare in mezzo. [isDark] è vero da
 *   metà in su.
 * @property backdropLuminance la luminanza media dello sfondo sotto il vetro, 0..1, o `NaN` finché non è misurata.
 * @property isMeasured falso finché la sonda non ha letto lo sfondo: fino ad allora il lato è quello del tema.
 */
@Immutable
class FluidGlassAppearance internal constructor(
  val isDark: Boolean,
  val darkness: Float,
  val backdropLuminance: Float,
  val isMeasured: Boolean,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is FluidGlassAppearance) return false
    return isDark == other.isDark &&
      darkness == other.darkness &&
      (backdropLuminance == other.backdropLuminance || (backdropLuminance.isNaN() && other.backdropLuminance.isNaN())) &&
      isMeasured == other.isMeasured
  }

  override fun hashCode(): Int {
    var result = isDark.hashCode()
    result = 31 * result + darkness.hashCode()
    result = 31 * result + (if (backdropLuminance.isNaN()) 0 else backdropLuminance.hashCode())
    result = 31 * result + isMeasured.hashCode()
    return result
  }

  override fun toString(): String =
    "FluidGlassAppearance(isDark=$isDark, darkness=$darkness, backdropLuminance=$backdropLuminance, " +
      "isMeasured=$isMeasured)"
}

/**
 * L'aspetto del vetro su cui sta questo contenuto, o `null` fuori da un vetro adattivo. Lo fornisce il vetro (F2.3).
 * `GlassDefaults.isDarkSurface()` lo legge per primo: dentro un vetro misurato il lato è quello del vetro.
 */
val LocalFluidGlassAppearance: ProvidableCompositionLocal<FluidGlassAppearance?> = staticCompositionLocalOf { null }

/**
 * L'aspetto da un bersaglio della sonda e dal lato continuo [darkness] (non finito = il lato del bersaglio, che prima
 * della misura è quello del tema). Pura.
 */
internal fun fluidGlassAppearance(target: GlassAdaptiveTarget, darkness: Float = Float.NaN): FluidGlassAppearance {
  val d = if (darkness.isFinite()) darkness.coerceIn(0f, 1f) else if (target.dark) 1f else 0f
  return FluidGlassAppearance(
    isDark = d >= 0.5f,
    darkness = d,
    backdropLuminance = if (target.measured) target.luminance else Float.NaN,
    isMeasured = target.measured,
  )
}

// ------------------------------------------------------------------------------------------------------------
// Il giro dei piccoli (F2.3)
// ------------------------------------------------------------------------------------------------------------

/**
 * I tempi di un giro chiaro/scuro, contati dall'istante in cui il filtro della sonda decide il lato.
 *
 * Il ritardo di R4 B6 (130 ms fra lo scuro che copre tutto il tasto e il corpo che si muove) **lo dà già il filtro**: la
 * media mobile e la cadenza delle letture decidono il lato 130–160 ms dopo la copertura piena, sulla stessa scena di
 * B6 (simulazione in `artifacts/liquid-glass/bench/f2-3/giro.md`). Un ritardo in più qui raddoppierebbe quello di
 * Apple: di serie è zero, e resta per le varianti.
 *
 * @param bodyDelayMillis quanto aspetta il corpo dopo la decisione.
 * @param bodyMillis quanto ci mette il corpo da un lato all'altro (curva [GlassFlipBodyEasing]).
 * @param inkCrossMillis quando l'inchiostro vecchio è svanito nel corpo: il fotogramma a contrasto zero, dove
 *   l'inchiostro cambia lato.
 * @param inkMillis quando l'inchiostro nuovo è pieno.
 */
@Immutable
internal data class GlassFlipTiming(
  val bodyDelayMillis: Int,
  val bodyMillis: Int,
  val inkCrossMillis: Int,
  val inkMillis: Int,
) {
  init {
    require(bodyDelayMillis >= 0 && bodyMillis > 0) { "tempi del corpo non validi" }
    require(inkCrossMillis in 1 until inkMillis) { "l'inchiostro deve attraversare il corpo prima di finire" }
  }
}

/** Le varianti dei tempi del giro (tavola `artifacts/liquid-glass/tavole/f2/giro/`, la prima). */
internal object GlassFlipTimings {
  /**
   * **Di serie**: i tempi di R4 B6 (alta), misurati sull'interfaccia vera a 6:52 e ricontati qui dalla decisione del
   * filtro (~146 ms dopo la copertura piena): corpo in 300 ms, quasi tutto nei primi 200; l'inchiostro scuro svanisce
   * nel corpo in 110 ms (Apple: contrasto zero a 230–270 ms dalla copertura piena) e quello chiaro è pieno a 700 ms
   * (Apple: 90% a 600, coda fino a 830).
   */
  val Ios26: GlassFlipTiming =
    GlassFlipTiming(bodyDelayMillis = 0, bodyMillis = 300, inkCrossMillis = 110, inkMillis = 700)

  /** Più svelto: corpo in 200 ms, inchiostro in 450. Scelta di gusto da confrontare (tavola 1, B). */
  val Swift: GlassFlipTiming =
    GlassFlipTiming(bodyDelayMillis = 0, bodyMillis = 200, inkCrossMillis = 70, inkMillis = 450)

  /**
   * Più lento (S3): corpo in 450 ms, inchiostro pieno a 1000; l'inchiostro vecchio svanisce in 160 ms, nella stessa
   * proporzione di [Ios26] (110 su 700). Da confrontare sul tablet, non una scelta.
   */
  val Slow: GlassFlipTiming =
    GlassFlipTiming(bodyDelayMillis = 0, bodyMillis = 450, inkCrossMillis = 160, inkMillis = 1000)

  /** Le varianti per nome, come le chiede la manopola di debug `fliptime` (`GlassAdaptiveTuning`). */
  val Named: Map<String, GlassFlipTiming> = linkedMapOf("ios26" to Ios26, "swift" to Swift, "slow" to Slow)
}

/**
 * La curva del corpo nel giro: la curva di Bézier che meglio segue il corpo del tasto di Apple a 6:52 (R4 B6, misurato
 * fotogramma per fotogramma su `wwdc2025-219`, f12360–f12373: scarto quadratico medio di 0,6 su 93 livelli). Al 67%
 * del tempo è al 92%: «quasi tutto nei primi 200 ms».
 */
internal val GlassFlipBodyEasing: Easing = CubicBezierEasing(0.25f, 0.15f, 0.3f, 1f)

/**
 * Quanto resta dell'inchiostro vecchio a [s] (0..1) della sua dissolvenza: lento all'inizio e svelto alla fine, perché
 * il tratto illeggibile attorno al contrasto zero duri il meno possibile.
 */
internal fun glassFlipInkFade(s: Float): Float {
  val u = s.coerceIn(0f, 1f)
  return 1f - u * u
}

/**
 * Quanto c'è dell'inchiostro nuovo a [s] (0..1) della sua comparsa: svelto all'inizio (si legge presto), con la coda
 * lunga di Apple (R4 B6: 90% della luminanza a 600 ms, pieno a 830).
 */
internal fun glassFlipInkRise(s: Float): Float {
  val u = s.coerceIn(0f, 1f)
  return 1f - (1f - u).pow(GlassFlipInkRisePower)
}

/** L'esponente della comparsa: con la dissolvenza di [GlassFlipTimings.Ios26] dà il 90% della luminanza a 454 ms. */
private const val GlassFlipInkRisePower: Float = 2.5f

/**
 * La legge del giro di **un** vetro, pura: due canali che vanno da un lato all'altro, il **corpo** (0 = il lato
 * chiaro della ricetta, 1 = lo scuro) e l'**inchiostro** (0 = l'inchiostro del vetro chiaro, pieno; 0,5 = svanito nel
 * corpo; 1 = l'inchiostro del vetro scuro, pieno). Gli istanti glieli dà chi chiama.
 *
 * - **Nessun salto**: un giro che cambia idea a metà riparte dai valori di quell'istante, e torna indietro in un tempo
 *   proporzionale alla strada che resta (un giro fatto a metà si disfa in metà tempo).
 * - **Monotona**: dentro un giro ogni canale va solo verso il lato di arrivo.
 * - **L'inchiostro non si mescola**: è sempre uno dei due inchiostri, più o meno trasparente sopra il corpo
 *   ([inkAlpha]); passa dall'uno all'altro solo da trasparente, cioè nel fotogramma a contrasto zero di R4 B6.
 */
internal class GlassFlipLaw(private val timing: GlassFlipTiming = GlassFlipTimings.Ios26) {

  /** Il lato verso cui si va: 0 chiaro, 1 scuro. */
  var target: Float = 0f
    private set

  private var startAt = 0L
  private var bodyFrom = 0f
  private var inkFrom = 0f

  /** Si mette subito su un lato, senza animazione: la prima misura, un cambio di tema. */
  fun snap(dark: Boolean) {
    target = if (dark) 1f else 0f
    bodyFrom = target
    inkFrom = target
    startAt = 0L
  }

  /**
   * Il filtro ha deciso il lato [dark] a [nowMillis]. Falso se ci si stava già andando (o ci si è già): niente cambia.
   * Altrimenti il giro riparte da dove sono i due canali adesso.
   */
  fun turn(nowMillis: Long, dark: Boolean): Boolean {
    val to = if (dark) 1f else 0f
    if (to == target) return false
    bodyFrom = body(nowMillis)
    inkFrom = ink(nowMillis)
    target = to
    startAt = nowMillis
    return true
  }

  /** Il corpo a [nowMillis]. */
  fun body(nowMillis: Long): Float {
    val distance = abs(target - bodyFrom)
    if (distance <= 0f) return target
    val delay = timing.bodyDelayMillis * distance
    val duration = max(1f, timing.bodyMillis * distance)
    val s = (nowMillis - startAt - delay) / duration
    if (s <= 0f) return bodyFrom
    if (s >= 1f) return target
    return bodyFrom + (target - bodyFrom) * GlassFlipBodyEasing.transform(s)
  }

  /** L'inchiostro a [nowMillis], 0..1 (vedi la classe). */
  fun ink(nowMillis: Long): Float {
    val toSign = if (target >= 0.5f) 1f else -1f
    // L'inchiostro come alfa con segno: -1 quello del vetro chiaro pieno, +1 quello del vetro scuro pieno.
    val from = 2f * inkFrom - 1f
    val elapsed = (nowMillis - startAt).toFloat()
    val riseMillis = (timing.inkMillis - timing.inkCrossMillis).toFloat()
    val signed = if (from * toSign < 0f) {
      // Ancora dalla parte di partenza: svanisce nel corpo, poi compare dall'altra.
      val cross = timing.inkCrossMillis * abs(from)
      if (elapsed < cross) {
        from * glassFlipInkFade(elapsed / cross)
      } else {
        toSign * glassFlipInkRise((elapsed - cross) / riseMillis)
      }
    } else {
      // Già dalla parte d'arrivo (o trasparente): cresce da dov'è.
      val a0 = abs(from)
      val duration = riseMillis * (1f - a0)
      if (duration <= 0f) toSign else toSign * (a0 + (1f - a0) * glassFlipInkRise(elapsed / duration))
    }
    return ((signed + 1f) / 2f).coerceIn(0f, 1f)
  }

  /** Vero quando a [nowMillis] i due canali sono arrivati. */
  fun settled(nowMillis: Long): Boolean = body(nowMillis) == target && ink(nowMillis) == target

  companion object {
    /** La trasparenza del contenuto per un inchiostro [ink] (0..1): piena ai lati, zero a metà. */
    fun inkAlpha(ink: Float): Float = abs(2f * ink.coerceIn(0f, 1f) - 1f)

    /** Da che parte sta l'inchiostro [ink]: vero = quello del vetro scuro (chiaro). */
    fun inkOnDark(ink: Float): Boolean = ink >= 0.5f
  }
}

/** L'inchiostro sul vetro chiaro che ha girato da un tema scuro: il testo del tema chiaro dell'engine (R4 A6: 14–20). */
internal val FluidGlassInkOnLight: Color = Color(0xFF121214)

/** L'inchiostro sul vetro scuro che ha girato da un tema chiaro: il testo del tema scuro dell'engine (R4 A6: 236). */
internal val FluidGlassInkOnDark: Color = Color(0xFFF2F2F5)

// ------------------------------------------------------------------------------------------------------------
// Dalla sonda al materiale
// ------------------------------------------------------------------------------------------------------------

/**
 * Lo stato adattivo di **una** superficie: i bersagli della sonda trasformati in valori animati, che la ricetta legge
 * in disegno con [fill]. Lo crea [rememberGlassAdaptiveState].
 *
 * Tre tempi: i bersagli continui del materiale (luminanza, sua deviazione, trama, colore) li raggiunge in
 * [GlassLiquidAdaptiveMillis] (il corpo del giro, R4 B6); il lato dei **grandi** in [GlassLiquidLargeDarkenMillis]
 * (R4 A6: da 143 a 48 su nero in ≈1,5 s); il lato dei **piccoli** che girano con la legge del giro ([GlassFlipLaw]).
 * **La prima misura non si anima**: un vetro che nasce sopra una foto scura non deve prima mostrarsi del lato del tema
 * e poi cambiare (come fa il filtro della sonda col lato).
 *
 * Nessuna animazione gira se niente cambia: a riposo la superficie non paga niente (F1A-f).
 */
@Stable
internal class GlassAdaptiveState(private val timing: GlassFlipTiming = GlassFlipTimings.Ios26) {
  private var measured by mutableStateOf(false)
  private var drivesDarkness by mutableStateOf(false)
  private val luminance = Animatable(0f)
  private val deviation = Animatable(0f)
  private val texture = Animatable(0f)
  private val red = Animatable(0f)
  private val green = Animatable(0f)
  private val blue = Animatable(0f)
  private val darkness = Animatable(0f)

  // ---- Il giro e l'inchiostro (F2.3) ----
  private val law = GlassFlipLaw(timing)
  private var flips by mutableStateOf(false)
  private var inks by mutableStateOf(false)
  private var sideMeasured = false
  private var body by mutableFloatStateOf(0f)
  private var ink by mutableFloatStateOf(0f)

  /** Da che parte sta l'inchiostro, per la composizione: cambia una volta per giro. */
  val inkOnDark: Boolean by derivedStateOf { GlassFlipLaw.inkOnDark(ink) }

  /** La parte che il contenuto ha composto per ultima ([noteComposedInk]), letta in disegno. */
  // Stato dalla 4.0 (F2.3c): chi legge [inkAlpha] in disegno (un `graphicsLayer`) deve rileggerlo quando la composizione
  // annota la parte nuova. Da campo semplice, un vetro che nasceva già sul lato opposto (la prima lettura scatta, e
  // l'inchiostro poi non si muove più) lasciava la scheda scelta della tab bar trasparente per sempre: il suo layer
  // aveva letto «trasparente» prima dell'annotazione, e niente lo rileggeva.
  private var composedInkOnDark by mutableStateOf(false)

  /** Il lato dei piccoli e dei grandi, adesso, per i vetri annidati che lo prendono da qui. Letto in disegno. */
  val sideNow: Float
    get() = when {
      drivesDarkness -> darkness.value
      flips -> body
      else -> Float.NaN
    }

  /**
   * Riempie [out] coi valori di adesso, per una ricetta. Letture di stato: chiamata dentro un disegno o dagli effetti,
   * li iscrive ai valori, e un'animazione li ridisegna. Restituisce [out].
   */
  fun fill(out: GlassAdaptiveMaterial, profile: GlassLiquidProfile = GlassLiquidProfile.Default): GlassAdaptiveMaterial {
    val side = sideNow
    if (!measured) {
      out.unmeasured()
    } else {
      out.measure(
        luminance = luminance.value,
        deviation = deviation.value,
        texture = texture.value,
        color = Color(red.value.coerceIn(0f, 1f), green.value.coerceIn(0f, 1f), blue.value.coerceIn(0f, 1f)),
        spread = profile.adaptive.darkLevelSpread,
      )
    }
    out.darkness = side
    // Un piccolo che non gira (il contenuto non segue l'inchiostro) tiene il lato del tema: sul lato scuro prende il
    // soffitto del velo dei grandi (GlassRecipe).
    out.holdsSide = !flips
    return out
  }

  /**
   * Quanto è visibile il contenuto adesso: uno a riposo, zero nel fotogramma in cui l'inchiostro cambia lato. Letto
   * **in disegno**. Se la composizione non ha ancora l'inchiostro della parte giusta (il fotogramma del cambio), zero:
   * mai un fotogramma con l'inchiostro sbagliato.
   */
  fun inkAlpha(): Float {
    if (!inks) return 1f
    val p = ink
    if (GlassFlipLaw.inkOnDark(p) != composedInkOnDark) return 0f
    return GlassFlipLaw.inkAlpha(p)
  }

  /** Il contenuto ha composto l'inchiostro della parte [onDark]. Da un `SideEffect`, prima del disegno. */
  fun noteComposedInk(onDark: Boolean) {
    composedInkOnDark = onDark
  }

  /**
   * L'aspetto da dare al contenuto. Cambia **solo** quando l'inchiostro cambia parte (o arriva la prima misura): le
   * letture della sonda che non spostano il lato non lo toccano, e il contenuto non si ricompone a ogni lettura. I suoi
   * numeri sono quelli della lettura che ha deciso.
   */
  var appearance: FluidGlassAppearance by mutableStateOf(themeAppearance(false))
    private set

  private fun themeAppearance(darkSide: Boolean) =
    FluidGlassAppearance(isDark = darkSide, darkness = if (darkSide) 1f else 0f, backdropLuminance = Float.NaN, isMeasured = false)

  /** Riscrive [appearance] se la parte dell'inchiostro di adesso non è quella che dice. */
  private fun noteInkSide(luminance: Float) {
    val onDark = GlassFlipLaw.inkOnDark(ink)
    val current = appearance
    if (current.isMeasured && current.isDark == onDark) return
    appearance = FluidGlassAppearance(
      isDark = onDark,
      darkness = if (drivesDarkness) darkness.value else if (onDark) 1f else 0f,
      backdropLuminance = luminance,
      isMeasured = true,
    )
  }

  /**
   * Insegue una lettura nuova. [continuous] vero = questa superficie scurisce in continuo (un grande), e il suo lato è
   * la quota di nero ([glassBlackShare]) sul tema chiaro, lo scuro pieno sul tema scuro ([darkSide]): i grandi
   * scuriscono, non schiariscono (R4 A6 misura solo quello).
   */
  suspend fun follow(target: GlassAdaptiveTarget, stats: GlassBackdropStats?, darkSide: Boolean, continuous: Boolean) {
    val darkTarget = if (darkSide) 1f else if (target.measured && stats != null) glassBlackShare(stats.luminance, stats.variance) else 0f
    if (!target.measured || stats == null) {
      measured = false
      drivesDarkness = continuous
      darkness.snapTo(if (darkSide) 1f else 0f)
      return
    }
    val first = !measured
    val color = target.color
    val r = if (color.isSpecifiedColor()) color.red else stats.red
    val g = if (color.isSpecifiedColor()) color.green else stats.green
    val b = if (color.isSpecifiedColor()) color.blue else stats.blue
    if (first || !drivesDarkness && continuous) {
      // La prima misura (e il passaggio a una superficie che scurisce) decidono subito.
      luminance.snapTo(stats.luminance)
      deviation.snapTo(stats.deviation)
      texture.snapTo(target.texture)
      red.snapTo(r)
      green.snapTo(g)
      blue.snapTo(b)
      darkness.snapTo(darkTarget)
      measured = true
      drivesDarkness = continuous
      return
    }
    drivesDarkness = continuous
    val body = tween<Float>(durationMillis = GlassLiquidAdaptiveMillis)
    coroutineScope {
      launch { luminance.animateTo(stats.luminance, body) }
      launch { deviation.animateTo(stats.deviation, body) }
      launch { texture.animateTo(target.texture, body) }
      launch { red.animateTo(r, body) }
      launch { green.animateTo(g, body) }
      launch { blue.animateTo(b, body) }
      launch { darkness.animateTo(darkTarget, tween(durationMillis = GlassLiquidLargeDarkenMillis)) }
    }
  }

  /**
   * Il modo del giro: [flipping] = un piccolo il cui contenuto segue l'inchiostro (gira corpo e inchiostro);
   * [inking] = il contenuto segue l'inchiostro (un grande lo cambia quando il suo lato continuo passa metà). Si torna
   * sul lato del tema [darkSide], senza animazione.
   */
  fun reset(darkSide: Boolean, flipping: Boolean, inking: Boolean) {
    flips = flipping
    inks = inking
    sideMeasured = false
    law.snap(darkSide)
    body = law.target
    ink = law.target
    composedInkOnDark = darkSide
    appearance = themeAppearance(darkSide)
  }

  /**
   * Dove vuole andare il lato adesso: -1 senza misura (il lato del tema), 0 chiaro, 1 scuro. Per un piccolo è il lato
   * del filtro; per un grande, se il suo lato continuo ha passato metà. Letture di stato: per `snapshotFlow`.
   */
  fun wanted(target: GlassAdaptiveTarget): Int = when {
    !target.measured -> -1
    drivesDarkness -> glassLargeInkSide(darkness.value, GlassFlipLaw.inkOnDark(ink))
    else -> if (target.dark) 1 else 0
  }

  /**
   * Va verso il lato [wanted] (vedi [wanted]) con la legge del giro, scrivendo corpo e inchiostro a ogni fotogramma
   * finché non arrivano. La prima misura ci va subito. Cancellabile: un lato nuovo riparte da dove si è.
   */
  suspend fun turn(wanted: Int, darkSide: Boolean, luminance: () -> Float) {
    if (!flips && !inks) return
    if (wanted < 0) {
      if (sideMeasured) return // una misura persa non rimette il lato del tema: resta l'ultimo deciso
      law.snap(darkSide)
      body = law.target
      ink = law.target
      return
    }
    val dark = wanted == 1
    if (!sideMeasured) {
      sideMeasured = true
      law.snap(dark)
      body = law.target
      ink = law.target
      noteInkSide(luminance())
      return
    }
    val start = withFrameMillis { it }
    if (!law.turn(start, dark)) return
    var now = start
    while (true) {
      body = law.body(now)
      ink = law.ink(now)
      noteInkSide(luminance())
      if (law.settled(now)) break
      now = withFrameMillis { it }
    }
  }

  private fun Color.isSpecifiedColor(): Boolean = this != Color.Unspecified
}

/**
 * Dove passa l'inchiostro di un **grande** (F2.3b): il suo lato continuo ([GlassAdaptiveMaterial.darkness], la quota di
 * nero animata) passa metà, con un'isteresi di [GlassLargeInkHysteresis] per parte. Un foglio fermo sopra uno sfondo
 * a metà (la quota oscilla di qualche centesimo da una lettura all'altra) non fa ballare il tema del suo contenuto: va
 * sullo scuro a 0,5 + h e torna sul chiaro a 0,5 − h. Pura: 1 scuro, 0 chiaro.
 *
 * @param darkness il lato continuo adesso, 0..1.
 * @param onDark se l'inchiostro adesso è quello del lato scuro.
 */
internal fun glassLargeInkSide(darkness: Float, onDark: Boolean): Int {
  val d = if (darkness.isFinite()) darkness else 0f
  return if (onDark) {
    if (d > GlassLargeInkTurn - GlassLargeInkHysteresis) 1 else 0
  } else {
    if (d >= GlassLargeInkTurn + GlassLargeInkHysteresis) 1 else 0
  }
}

/** Che cosa fa il lato di una superficie col materiale nuovo e la sonda (F2.3b): vedi [glassInkMode]. */
internal enum class GlassInkMode {
  /** Resta del lato del tema: nessun patto dell'inchiostro, o una pellicola fissa. Il velo e l'ombra seguono lo stesso. */
  Theme,

  /** Gira, corpo e inchiostro, con la legge del giro ([GlassFlipLaw]): i piccoli, le barre, la tab bar. */
  Flip,

  /** Scurisce in continuo con la quota di nero, e l'inchiostro passa di lato a metà ([glassLargeInkSide]): i grandi. */
  Continuous,
}

/**
 * La regola, pura (F2.3b). Senza il patto dell'inchiostro ([inkFollows] falso) il vetro non sa di che colore è ciò che
 * gli sta sopra, e resta del tema. Con il patto, un grande ([large]: pannelli, fogli, menu, il pannello laterale)
 * scurisce in continuo; un piccolo gira, salvo che [flipAllowed] sia falso (la pellicola fissa del tasto tinto o della
 * selezione d'accento: il suo colore è la sua informazione).
 */
internal fun glassInkMode(inkFollows: Boolean, large: Boolean, flipAllowed: Boolean): GlassInkMode = when {
  !inkFollows -> GlassInkMode.Theme
  large -> GlassInkMode.Continuous
  flipAllowed -> GlassInkMode.Flip
  else -> GlassInkMode.Theme
}

/**
 * Il lato continuo a cui l'inchiostro di un grande passa di lato: metà. Il contenuto segue la parte più grande del
 * pannello; sopra uno sfondo diviso a metà nessun inchiostro va bene su tutte e due le parti (R4 A6, F2-e in
 * `artifacts/liquid-glass/bench/f2-3b/contrasto.md`).
 */
internal const val GlassLargeInkTurn: Float = 0.5f

/**
 * Metà della fascia in cui l'inchiostro di un grande non cambia (F2.3b): 0,06 di lato continuo, cioè circa sei punti di
 * quota di nero. La quota stimata da una miniatura ferma trema di meno di un punto (`bench/f2-1/probe_stats.md`), e la
 * banda morta delle letture (`GlassReadingDeadBand`) ne toglie già un centesimo.
 */
internal const val GlassLargeInkHysteresis: Float = 0.06f

/**
 * Lo stato adattivo di una superficie radice che porta la sonda [probe]: segue `probe.target` e `probe.stats` e li anima
 * per la ricetta (vedi [GlassAdaptiveState]).
 *
 * @param role il ruolo della superficie: decide se scurisce in continuo (`GlassLiquidAdaptive.continuous`) o gira.
 * @param darkSide il lato del tema (`GlassDefaults.isDarkSurface()`).
 * @param inkFollows se il contenuto di questa superficie segue l'inchiostro del suo lato ([GlassInk]). Solo allora i
 *   piccoli girano e i grandi scuriscono davvero con la quota di nero: un pannello chiaro che scurisce sopra il nero
 *   con il testo scuro del tema lo renderebbe illeggibile (il conto in `artifacts/liquid-glass/bench/f2-2/contrasto.md`).
 * @param flipAllowed falso per un vetro che non deve girare anche se l'inchiostro lo seguirebbe (una pellicola fissa,
 *   come la selezione d'accento).
 * @param large se la superficie scurisce in continuo invece di girare: di serie lo dice il ruolo
 *   (`GlassLiquidAdaptive.continuous`: pannelli, fogli, card); un componente lo può dire da sé (F2.3b: la barra
 *   laterale è `Floating` ma è un pannello laterale, e i pannelli laterali di Apple non girano).
 * @param timing i tempi del giro: di serie [GlassFlipTimings.Ios26], o quelli della manopola di debug `fliptime` (S3).
 */
@Composable
internal fun rememberGlassAdaptiveState(
  probe: GlassProbeState,
  role: GlassRole,
  darkSide: Boolean,
  profile: GlassLiquidProfile = GlassLiquidProfile.Default,
  inkFollows: Boolean = false,
  flipAllowed: Boolean = true,
  timing: GlassFlipTiming = GlassAdaptiveTuning.flipTiming,
  large: Boolean = profile.adaptive.darkensContinuously(role),
): GlassAdaptiveState {
  val state = remember(probe, timing) { GlassAdaptiveState(timing) }
  val mode = glassInkMode(inkFollows = inkFollows, large = large, flipAllowed = flipAllowed)
  val continuous = mode == GlassInkMode.Continuous
  val flipping = mode == GlassInkMode.Flip
  val inking = flipping || continuous
  // Il modo si fissa in composizione, non in un effetto: il primo fotogramma deve già stare sul lato del tema (un
  // effetto arriva dopo il primo disegno, e il lato di partenza dello stato è il chiaro).
  remember(state, darkSide, flipping, inking) {
    state.reset(darkSide, flipping, inking)
    Unit
  }
  // Le letture della sonda si seguono fuori dalla composizione: lette qui, ricomporrebbero chi possiede il vetro a ogni
  // lettura (fino a otto volte al secondo mentre lo sfondo scorre).
  LaunchedEffect(state, darkSide, continuous) {
    snapshotFlow { probe.target to probe.stats }
      // Una lettura quasi uguale all'ultima seguita non rianima niente: ogni ripartenza sono 300 ms di fotogrammi e una
      // catena del corpo rifatta a ogni fotogramma, per un cambio che non si vede.
      .distinctUntilChanged { old, new -> glassReadingsAlike(old.first, old.second, new.first, new.second) }
      .collectLatest { (target, stats) -> state.follow(target, stats, darkSide, continuous) }
  }
  LaunchedEffect(state, darkSide, flipping, inking) {
    if (!inking) return@LaunchedEffect
    snapshotFlow { state.wanted(probe.target) }.collectLatest { wanted ->
      state.turn(wanted, darkSide) { Snapshot.withoutReadObservation { probe.target.luminance } }
    }
  }
  return state
}

/**
 * Quanto può cambiare una lettura della sonda senza che il materiale la insegua (F2.3): un centesimo di luminanza e di
 * deviazione, due di trama e di colore. Sotto, il corpo cambia di meno di un livello su 255.
 */
internal const val GlassReadingDeadBand: Float = 0.01f

/**
 * Vero se due letture della sonda chiedono lo stesso materiale, a meno di [GlassReadingDeadBand]: stesso stato di
 * misura, stesso lato, stessi numeri entro la banda. Pura. La media del filtro (`smoothedLuminance`) non conta: decide il
 * lato, e il lato è già confrontato.
 */
internal fun glassReadingsAlike(
  a: GlassAdaptiveTarget,
  aStats: GlassBackdropStats?,
  b: GlassAdaptiveTarget,
  bStats: GlassBackdropStats?,
  band: Float = GlassReadingDeadBand,
): Boolean {
  if (a.measured != b.measured || a.dark != b.dark) return false
  if (!a.measured) return true
  if ((aStats == null) != (bStats == null)) return false
  fun near(x: Float, y: Float, tolerance: Float) = x == y || abs(x - y) < tolerance
  if (!near(a.luminance, b.luminance, band) || !near(a.texture, b.texture, 2f * band)) return false
  if (a.color != b.color && (!near(a.color.red, b.color.red, 2f * band) ||
      !near(a.color.green, b.color.green, 2f * band) || !near(a.color.blue, b.color.blue, 2f * band))
  ) {
    return false
  }
  if (aStats != null && bStats != null) {
    if (!near(aStats.luminance, bStats.luminance, band) || !near(aStats.deviation, bStats.deviation, band)) return false
  }
  return true
}

// ------------------------------------------------------------------------------------------------------------
// L'inchiostro che segue il lato (F2.3)
// ------------------------------------------------------------------------------------------------------------

/**
 * Il patto fra un vetro e il contenuto che ci sta sopra: il contenuto **segue l'inchiostro** del lato del vetro.
 *
 * Lo crea il componente che possiede sia il vetro sia il contenuto ([rememberGlassInk]), lo passa alla superficie, e
 * avvolge il contenuto in [GlassInkContent]. Solo con questo patto un vetro gira (vedi [rememberGlassAdaptiveState]):
 * la superficie lega qui il suo stato quando ha una sonda, e lo slega quando non l'ha più (vetro di prima, annidato,
 * sotto Android 13).
 */
@Stable
internal class GlassInk(
  /**
   * Se il contenuto è quello di un pannello **grande** che scurisce in continuo (F2.3b), o null: lo dice il ruolo della
   * superficie (`GlassLiquidAdaptive.continuous`). Un componente lo dice da sé quando il ruolo non basta (la barra
   * laterale, `Floating` ma pannello laterale).
   */
  val large: Boolean? = null,
) {
  /** Lo stato della superficie che gira, o null se non gira. Scritto in composizione dalla superficie. */
  var state: GlassAdaptiveState? by mutableStateOf(null)
    private set

  /** Il lato del tema della superficie. */
  var darkSide: Boolean by mutableStateOf(false)
    private set

  /**
   * La superficie ha una sonda e il suo stato: il contenuto segue [state]. Chiamata in composizione dalla superficie,
   * prima che il contenuto si componga: scrive solo se cambia qualcosa.
   */
  fun bind(state: GlassAdaptiveState, darkSide: Boolean) {
    if (this.state !== state) this.state = state
    if (this.darkSide != darkSide) this.darkSide = darkSide
  }

  /** La superficie non misura (vetro di prima, annidata, senza sonda): il contenuto resta col suo inchiostro. */
  fun unbind() {
    if (state != null) state = null
  }
}

/** Un [GlassInk] che vive quanto il componente. [large]: vedi [GlassInk.large]. */
@Composable
internal fun rememberGlassInk(large: Boolean? = null): GlassInk = remember(large) { GlassInk(large) }

/**
 * Lo stato adattivo del vetro che ospita questo contenuto, per i vetri **annidati** dentro di lui: prendono il lato dal
 * suo, in continuo, invece di misurare lo sfondo che non vedono (3.9). Lo fornisce [GlassInkContent].
 */
internal val LocalGlassAdaptiveHost: ProvidableCompositionLocal<GlassAdaptiveState?> = staticCompositionLocalOf { null }

/**
 * La dissolvenza dell'inchiostro del vetro che contiene (F2.3b), da leggere **in disegno**: uno a riposo, zero nel
 * fotogramma in cui l'inchiostro cambia lato. Il contenuto disegnato nel nodo del vetro la riceve già; serve a ciò che il
 * vetro mostra senza disegnarlo nel suo contenuto: la riga d'accento nascosta della tab bar, che il puck campiona da un
 * suo layer. Fuori da un vetro col patto, sempre uno. Lo fornisce [GlassInkContent].
 */
internal val LocalGlassInkFade: ProvidableCompositionLocal<() -> Float> = staticCompositionLocalOf { NoInkFade }

private val NoInkFade: () -> Float = { 1f }

/**
 * Un ospite che sta **sullo stesso fondale** dei vetri che ospita (4.0, F2.3c): la barra in alto di una schermata, la
 * cui striscia legge la pagina sotto la barra, e i tasti che stanno sulla striscia. I tasti ospitati prendono da lui
 * il lato **e la misura** (la pagina sotto la barra è anche quella sotto di loro), e il loro contenuto svanisce e
 * risale con la sua dissolvenza ([fade], in disegno): non sta dentro un vetro che lo dissolve per loro.
 *
 * Diverso dall'ospite di un foglio o di un pop-up ([LocalGlassAdaptiveHost] e basta): lì il vetro annidato sta sul
 * materiale dell'ospite, non sulla pagina, e prende solo il lato.
 */
internal class GlassSharedHost(val fade: () -> Float)

/** Vedi [GlassSharedHost]. Null fuori da una barra che lo fornisce. */
internal val LocalGlassSharedHost: ProvidableCompositionLocal<GlassSharedHost?> = staticCompositionLocalOf { null }

/**
 * Il contenuto di un vetro che segue l'inchiostro [ink]: `LocalContentColor` del lato del vetro ([themeInk] sul lato
 * del tema, al pixel di oggi; dall'altro lato [FluidGlassInkOnLight] o [FluidGlassInkOnDark]),
 * [LocalFluidGlassAppearance] e [LocalGlassAdaptiveHost]. La struttura non cambia mai, legato o no: il contenuto non
 * perde stato quando la superficie trova (o perde) la sonda.
 *
 * **Il tema del lato del vetro** (F2.3b). Quando l'inchiostro è dall'altro lato del tema, dentro il vetro c'è anche lo
 * schema di colori di quel lato, con lo stesso accento ([LocalFluidThemeSides], da `FluidTheme`), e
 * `LocalFluidSurfaceSide` dice quel lato: così segue anche il contenuto che legge `MaterialTheme.colorScheme` (il testo
 * di un foglio, una riga di un menu, la voce scelta della tab bar), non solo chi legge `LocalContentColor`. Cambia
 * **una volta per attraversamento**, nel fotogramma in cui il contenuto è trasparente (la dissolvenza la fa la
 * superficie in disegno, `glassContentFade`): mai una ricomposizione per fotogramma, mai un fotogramma col testo del
 * lato sbagliato ([GlassAdaptiveState.inkAlpha]). Il `MaterialTheme` c'è sempre, girato o no, perché la struttura non
 * cambi; lo stile del testo e l'indicazione di chi sta fuori passano com'erano (`MaterialTheme` li rimetterebbe di
 * serie). Fuori da `FluidTheme` resta solo l'inchiostro.
 *
 * Un colore scritto dall'app (un `tint`, un `color` passato a mano) resta quello dell'app: chi vuole seguire il vetro
 * legge [LocalFluidGlassAppearance].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GlassInkContent(ink: GlassInk, themeInk: Color, content: @Composable () -> Unit) {
  val state = ink.state
  val darkSide = ink.darkSide
  val onDark = state?.inkOnDark ?: darkSide
  val flipped = state != null && onDark != darkSide
  val color = when {
    !flipped -> themeInk
    onDark -> FluidGlassInkOnDark
    else -> FluidGlassInkOnLight
  }
  // Un vetro che non gira (annidato, di prima, senza sonda) lascia passare l'aspetto e l'ospite di chi lo contiene.
  val appearance = state?.appearance ?: LocalFluidGlassAppearance.current
  val host = state ?: LocalGlassAdaptiveHost.current
  if (state != null) SideEffect { state.noteComposedInk(onDark) }
  val sides = LocalFluidThemeSides.current
  val scheme = if (flipped && sides != null) sides.scheme(onDark) else MaterialTheme.colorScheme
  val side = if (flipped) onDark else LocalFluidSurfaceSide.current
  val textStyle = LocalTextStyle.current
  val indication = LocalIndication.current
  val fade = remember(state) { if (state == null) NoInkFade else { { state.inkAlpha() } } }
  MaterialTheme(
    colorScheme = scheme,
    motionScheme = MaterialTheme.motionScheme,
    shapes = MaterialTheme.shapes,
    typography = MaterialTheme.typography,
  ) {
    CompositionLocalProvider(
      LocalTextStyle provides textStyle,
      LocalIndication provides indication,
      LocalContentColor provides color,
      LocalFluidSurfaceSide provides side,
      LocalFluidGlassAppearance provides appearance,
      LocalGlassAdaptiveHost provides host,
      LocalGlassInkFade provides fade,
      content = content,
    )
  }
}
