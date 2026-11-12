/*
 * Il vetro che si adatta (piano «iOS in Android», 3.9 e F2.2): il cursore dell'utente fra trasparente e colorato, ciò
 * che un vetro sa dello sfondo che ha dietro, e lo stato che porta la misura della sonda al materiale.
 *
 * Chi misura è la sonda (`ui/glass/field/GlassProbe.kt`, F2.1); chi trasforma la misura in numeri del materiale è la
 * ricetta (`glassRecipe`, con `GlassAdaptiveMaterial`). Qui c'è il tratto in mezzo: i bersagli della sonda, che
 * arrivano poche volte al secondo, diventano valori animati coi tempi di Apple, letti dalla superficie in disegno.
 *
 * Il giro chiaro/scuro dei piccoli e l'inchiostro adattivo sono di F2.3: lo stato qui sotto lascia il lato dei piccoli
 * al tema (`darkness` non finito), e [FluidGlassAppearance] è il tipo che F2.3 fornirà al contenuto.
 */
package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveMaterial
import dev.antigravity.fluidengine.ui.glass.field.GlassAdaptiveTarget
import dev.antigravity.fluidengine.ui.glass.field.GlassBackdropStats
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidAdaptiveMillis
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidLargeDarkenMillis
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassProbeState
import dev.antigravity.fluidengine.ui.glass.field.glassBlackShare
import kotlinx.coroutines.coroutineScope
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
 * Non si costruisce: lo fornisce il vetro al suo contenuto con [LocalFluidGlassAppearance] (l'inchiostro adattivo
 * passa di qui, F2.3). Fuori da un vetro adattivo non c'è.
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
// Dalla sonda al materiale
// ------------------------------------------------------------------------------------------------------------

/**
 * Lo stato adattivo di **una** superficie: i bersagli della sonda trasformati in valori animati, che la ricetta legge
 * in disegno con [fill]. Lo crea [rememberGlassAdaptiveState].
 *
 * Due tempi: i bersagli continui del materiale (luminanza, sua deviazione, trama, colore) li raggiunge in
 * [GlassLiquidAdaptiveMillis] (il corpo del giro, R4 B6), il lato dei **grandi** in [GlassLiquidLargeDarkenMillis]
 * (R4 A6: da 143 a 48 su nero in ≈1,5 s). **La prima misura non si anima**: un vetro che nasce sopra una foto scura non
 * deve prima mostrarsi del lato del tema e poi cambiare (come fa il filtro della sonda col lato).
 *
 * Nessuna animazione gira se niente cambia: a riposo la superficie non paga niente (F1A-f).
 */
@Stable
internal class GlassAdaptiveState {
  private var measured by mutableStateOf(false)
  private var drivesDarkness by mutableStateOf(false)
  private val luminance = Animatable(0f)
  private val deviation = Animatable(0f)
  private val texture = Animatable(0f)
  private val red = Animatable(0f)
  private val green = Animatable(0f)
  private val blue = Animatable(0f)
  private val darkness = Animatable(0f)

  /**
   * Riempie [out] coi valori di adesso, per una ricetta. Letture di stato: chiamata dentro un disegno o dagli effetti,
   * li iscrive ai valori, e un'animazione li ridisegna. Restituisce [out].
   */
  fun fill(out: GlassAdaptiveMaterial, profile: GlassLiquidProfile = GlassLiquidProfile.Default): GlassAdaptiveMaterial {
    val side = if (drivesDarkness) darkness.value else Float.NaN
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
    return out
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

  private fun Color.isSpecifiedColor(): Boolean = this != Color.Unspecified
}

/**
 * Lo stato adattivo di una superficie radice che porta la sonda [probe]: segue `probe.target` e `probe.stats` e li anima
 * per la ricetta (vedi [GlassAdaptiveState]).
 *
 * @param role il ruolo della superficie: decide se scurisce in continuo (`GlassLiquidAdaptive.continuous`) o gira.
 * @param darkSide il lato del tema (`GlassDefaults.isDarkSurface()`).
 * @param darkensLarge se i grandi scuriscono davvero con la quota di nero. **Falso finché l'inchiostro non segue il
 *   lato** (F2.3): un pannello chiaro che scurisce sopra il nero con il testo scuro del tema lo rende illeggibile (il
 *   conto in `artifacts/liquid-glass/bench/f2-2/contrasto.md`). Chi fornisce l'inchiostro adattivo lo accende.
 */
@Composable
internal fun rememberGlassAdaptiveState(
  probe: GlassProbeState,
  role: GlassRole,
  darkSide: Boolean,
  profile: GlassLiquidProfile = GlassLiquidProfile.Default,
  darkensLarge: Boolean = false,
): GlassAdaptiveState {
  val state = remember(probe) { GlassAdaptiveState() }
  val continuous = darkensLarge && profile.adaptive.darkensContinuously(role)
  val target = probe.target
  val stats = probe.stats
  LaunchedEffect(state, target, stats, darkSide, continuous) {
    state.follow(target, stats, darkSide, continuous)
  }
  return state
}
