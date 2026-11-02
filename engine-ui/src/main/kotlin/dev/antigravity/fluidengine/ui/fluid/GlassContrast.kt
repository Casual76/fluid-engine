package dev.antigravity.fluidengine.ui.fluid

import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Il contrasto che il sistema chiede, da 0 (standard) a 1 (alto): Android 14+, Impostazioni >
 * Accessibilita' > Contrasto del colore, `UiModeManager.getContrast()` (3.0).
 *
 * Il vetro e' fatto per trasmettere, e la trasparenza e' la prima cosa che un utente che chiede piu'
 * contrasto non vuole: un'etichetta su una pellicola che lascia passare una foto si legge male a
 * chiunque, e malissimo a lui. Con questo valore ogni pellicola del vetro dell'engine si fa piu'
 * densa ([GlassTint.forContrast]) e il suo bordo piu' netto; a 0 non cambia niente, bit per bit.
 *
 * [FluidTheme][dev.antigravity.fluidengine.ui.theme.FluidTheme] lo legge dal sistema e lo segue
 * quando cambia ([rememberFluidSystemContrast]), quindi un'app che usa il tema non fa niente. Per
 * vederlo senza toccare le impostazioni, o per forzarlo in una schermata (la galleria lo fa), lo si
 * fornisce dentro il tema: `CompositionLocalProvider(LocalFluidSystemContrast provides 1f)`.
 */
val LocalFluidSystemContrast: ProvidableCompositionLocal<Float> = compositionLocalOf { FluidSystemContrast.Standard }

/**
 * I livelli del contrasto di sistema e cosa ne fa il vetro. Pura e senza Compose attorno: una
 * pellicola troppo timida non leggibile, o troppo piena e opaca, si vede solo in mano, quindi ogni
 * numero qui sotto e' fissato in `FluidSystemContrastTest`.
 */
object FluidSystemContrast {
  /** Il contrasto di serie del sistema. */
  const val Standard: Float = 0f

  /** "Medio": la voce di mezzo delle impostazioni di Android 14+. */
  const val Medium: Float = 0.5f

  /** "Alto". */
  const val High: Float = 1f

  /**
   * Quanto la pellicola si avvicina al pieno a [Medium]: il 40% della strada che le manca. Una barra
   * al 44% di opacita' sale al 66%, e dietro la foto si vede ancora, ma l'etichetta non la fa piu' sua.
   */
  internal const val MediumVeil: Float = 0.40f

  /**
   * Quanto a [High]: l'85%. La stessa barra arriva al 92%: e' ancora vetro (la sfocatura, il bordo, la
   * lente restano) ma quello che ha dietro non decide piu' di cosa e' fatta. Non il 100%: un
   * pannello del tutto pieno smette di dire "vetro" e si scambia per una finestra rotta.
   */
  internal const val HighVeil: Float = 0.85f

  /** Di quanto sale l'opacita' del bordo a [High]: il bordo dice dove finisce il pannello. */
  internal const val HairlineGain: Float = 2.5f

  /** L'opacita' massima di un bordo: oltre, e' una riga disegnata sopra il pannello. */
  internal const val HairlineCeiling: Float = 0.7f

  /**
   * Il valore del sistema, ripulito: Android lo da' come float e le impostazioni offrono standard,
   * medio e alto (0; 0,5; 1). Un valore sotto lo zero non e' un'offerta e qui vale "standard", uno
   * sopra l'uno vale "alto", e uno non finito non e' un livello.
   */
  fun level(raw: Float): Float = if (raw.isFinite()) raw.coerceIn(Standard, High) else Standard

  /**
   * Quanta della strada verso il pieno percorre una pellicola a questo [contrast]: 0 a standard,
   * [MediumVeil] a medio, [HighVeil] ad alto, lineare fra uno e l'altro.
   */
  fun veil(contrast: Float): Float {
    val c = level(contrast)
    return if (c <= Medium) {
      MediumVeil * (c / Medium)
    } else {
      MediumVeil + (HighVeil - MediumVeil) * ((c - Medium) / (High - Medium))
    }
  }
}

/**
 * La stessa pellicola, resa piu' densa per il [contrast] del sistema (vedi [LocalFluidSystemContrast]).
 *
 *  * la **tinta** e il **ripiego** (quello che si vede sotto API 31, dove non c'e' sfocatura) salgono
 *    verso il pieno di [FluidSystemContrast.veil]: `a' = a + (1 - a) · veil`;
 *  * il **bordo**, se c'e', diventa piu' netto — fino a 2,5 volte, mai oltre il 70% — perche' e' lui a
 *    dire dove finisce il pannello quando dentro non c'e' piu' niente da attraversare; un bordo
 *    trasparente resta trasparente (una pellicola che non ne vuole uno non ne riceve uno);
 *  * il **colore** non cambia: e' lo stesso grigio, solo che copre di piu'.
 *
 * A contrasto standard torna la pellicola stessa, non una copia.
 */
fun GlassTint.forContrast(contrast: Float): GlassTint {
  val veil = FluidSystemContrast.veil(contrast)
  if (veil <= 0f) return this
  return GlassTint(
    overlay = overlay.denser(veil),
    fallback = fallback.denser(veil),
    hairline = hairline.sharper(veil),
  )
}

/** [forContrast] col contrasto del sistema che il tema ha messo in [LocalFluidSystemContrast]. */
@Composable
fun GlassTint.forSystemContrast(): GlassTint = forContrast(LocalFluidSystemContrast.current)

private fun Color.denser(veil: Float): Color =
  if (!isSpecified) this else copy(alpha = alpha + (1f - alpha) * veil)

private fun Color.sharper(veil: Float): Color {
  if (!isSpecified || alpha <= 0f) return this
  // Il guadagno e' pieno ad alto contrasto, e a medio e' la parte che gli spetta. Un bordo che gia'
  // supera il tetto non si abbassa: il tetto frena la crescita, non toglie niente.
  val boosted = alpha * (1f + FluidSystemContrast.HairlineGain * veil / FluidSystemContrast.HighVeil)
  return copy(alpha = maxOf(alpha, minOf(boosted, FluidSystemContrast.HairlineCeiling)))
}

/**
 * Il contrasto del sistema adesso, e **si aggiorna quando l'utente lo cambia** mentre l'app e'
 * aperta. 0 prima di Android 14, dove le impostazioni non esistono (e `UiModeManager.getContrast()`
 * neppure), e dove il servizio manca.
 *
 * Lo mette [FluidTheme][dev.antigravity.fluidengine.ui.theme.FluidTheme] in [LocalFluidSystemContrast]:
 * un'app che ha un tema suo e vuole il contrasto lo chiama una volta, alla radice.
 */
@Composable
fun rememberFluidSystemContrast(): Float {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return FluidSystemContrast.Standard
  val context = LocalContext.current
  return rememberSystemContrast34(context)
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun rememberSystemContrast34(context: Context): Float {
  val manager = remember(context) { context.getSystemService(UiModeManager::class.java) }
  var level by remember(manager) { mutableFloatStateOf(readContrast34(manager)) }
  DisposableEffect(manager) {
    if (manager == null) {
      onDispose { }
    } else {
      val listener = UiModeManager.ContrastChangeListener { contrast ->
        level = FluidSystemContrast.level(contrast)
      }
      manager.addContrastChangeListener(ContextCompat.getMainExecutor(context), listener)
      // Un cambio fra la lettura e la registrazione non si perderebbe mai, ma non costa niente rileggere.
      level = readContrast34(manager)
      onDispose { manager.removeContrastChangeListener(listener) }
    }
  }
  return level
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private fun readContrast34(manager: UiModeManager?): Float =
  if (manager == null) FluidSystemContrast.Standard else FluidSystemContrast.level(manager.contrast)
