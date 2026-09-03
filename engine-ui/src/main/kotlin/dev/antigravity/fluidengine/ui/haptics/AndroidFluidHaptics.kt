package dev.antigravity.fluidengine.ui.haptics

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Il dito del device, astratto: cosi' i cancelli (impostazione, sistema, risparmio energetico,
 * anti-raffica) si provano sul computer con un vibratore finto.
 */
interface HapticPort {
  val hasVibrator: Boolean

  /** Quali delle primitive chieste il device sa fare; vuoto sotto API 30. */
  fun supportedPrimitives(ids: IntArray): Set<Int>

  fun vibrate(steps: List<HapticPrimitiveStep>, attention: Boolean)

  fun platform(type: androidx.compose.ui.hapticfeedback.HapticFeedbackType)

  val systemHapticsEnabled: Boolean
  val powerSave: Boolean
}

/**
 * L'aptica di un tema: composizioni di primitive dove il device le ha (l'S25 si'), le costanti di
 * piattaforma altrove; sempre dietro all'interruttore dell'app, all'impostazione tattile di
 * sistema e, per i tick continui, al risparmio energetico. Un tick continuo non si ripete piu'
 * spesso di ogni 40 ms: sotto, il motore lo perde comunque e la batteria no.
 */
class FluidHapticsImpl(
  private val port: HapticPort,
  private val enabled: () -> Boolean,
  private val clock: () -> Long = System::currentTimeMillis,
) : FluidHaptics {

  private val supported: Set<Int> by lazy { port.supportedPrimitives(FluidHapticPatterns.primitivesUsed) }
  // Meta' del minimo, non zero: un orologio di prova che parte da 0 deve far passare il primo tick.
  private var lastContinuousAt = Long.MIN_VALUE / 2

  override val engine: FluidHapticEngine
    get() = when {
      !port.hasVibrator -> FluidHapticEngine.Off
      supported.containsAll(FluidHapticPatterns.primitivesUsed.toList()) -> FluidHapticEngine.Composition
      else -> FluidHapticEngine.Platform
    }

  /** Vero se questo evento andra' a composizione (le sue primitive ci sono tutte). */
  fun usesComposition(event: FluidHapticEvent): Boolean =
    port.hasVibrator && FluidHapticPatterns.composition(event).all { it.id in supported }

  override fun play(event: FluidHapticEvent) {
    if (!enabled()) return
    if (!port.hasVibrator) return
    if (!port.systemHapticsEnabled) return
    if (event.continuous) {
      if (port.powerSave) return
      val now = clock()
      if (now - lastContinuousAt < CONTINUOUS_MIN_GAP_MILLIS) return
      lastContinuousAt = now
    }
    val attention = event == FluidHapticEvent.AlertAlarm || event == FluidHapticEvent.AlertWatch || event == FluidHapticEvent.AlertClear
    if (usesComposition(event)) {
      port.vibrate(FluidHapticPatterns.composition(event), attention)
    } else {
      port.platform(FluidHapticPatterns.fallback(event))
    }
  }

  /** Cosa sa fare questo device, per una pagina di prova: motore, primitive, cancelli di sistema. */
  fun info(): FluidHapticsInfo = FluidHapticsInfo(
    engine = engine,
    hasVibrator = port.hasVibrator,
    supportedPrimitives = supported,
    systemHapticsEnabled = port.systemHapticsEnabled,
    powerSave = port.powerSave,
  )

  companion object {
    const val CONTINUOUS_MIN_GAP_MILLIS = 40L
  }
}

/** La fotografia del vibratore per una pagina "Prova i feedback". */
data class FluidHapticsInfo(
  val engine: FluidHapticEngine,
  val hasVibrator: Boolean,
  val supportedPrimitives: Set<Int>,
  val systemHapticsEnabled: Boolean,
  val powerSave: Boolean,
)

/** Il vibratore vero, e le costanti di Compose per il ripiego. */
class AndroidHapticPort(private val context: Context, private val platformHaptics: HapticFeedback) : HapticPort {

  private val vibrator: Vibrator? by lazy {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }

  override val hasVibrator: Boolean get() = vibrator?.hasVibrator() == true

  override fun supportedPrimitives(ids: IntArray): Set<Int> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptySet()
    val v = vibrator ?: return emptySet()
    val flags = runCatching { v.arePrimitivesSupported(*ids) }.getOrNull() ?: return emptySet()
    return ids.filterIndexed { index, _ -> flags.getOrNull(index) == true }.toSet()
  }

  override fun vibrate(steps: List<HapticPrimitiveStep>, attention: Boolean) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val v = vibrator ?: return
    val composition = VibrationEffect.startComposition()
    steps.forEach { composition.addPrimitive(it.id, it.scale.coerceIn(0f, 1f), it.delayMillis.coerceAtLeast(0)) }
    val effect = composition.compose()
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val usage = if (attention) VibrationAttributes.USAGE_NOTIFICATION else VibrationAttributes.USAGE_TOUCH
        v.vibrate(effect, VibrationAttributes.createForUsage(usage))
      } else {
        v.vibrate(effect)
      }
    }
  }

  override fun platform(type: androidx.compose.ui.hapticfeedback.HapticFeedbackType) {
    runCatching { platformHaptics.performHapticFeedback(type) }
  }

  override val systemHapticsEnabled: Boolean
    get() = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0 }.getOrDefault(true)

  override val powerSave: Boolean
    get() = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
}

/** L'istanza per un tema: una per composizione del tema, che legge l'interruttore a ogni evento. */
@Composable
fun rememberAndroidFluidHaptics(enabled: Boolean): FluidHaptics {
  val context = LocalContext.current
  val platform = LocalHapticFeedback.current
  val enabledState = rememberUpdatedState(enabled)
  return remember(context, platform) {
    FluidHapticsImpl(AndroidHapticPort(context.applicationContext, platform), enabled = { enabledState.value })
  }
}
