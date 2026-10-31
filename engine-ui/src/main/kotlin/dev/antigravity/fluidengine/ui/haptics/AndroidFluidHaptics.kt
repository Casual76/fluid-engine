package dev.antigravity.fluidengine.ui.haptics

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
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

  /** Vero se la vibrazione e' partita davvero: falso lascia la parola alla costante di piattaforma. */
  fun vibrate(steps: List<HapticPrimitiveStep>, attention: Boolean): Boolean

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
  private var lastDiscreteAt = Long.MIN_VALUE / 2
  private var lastGrainAt = Long.MIN_VALUE / 2

  override val engine: FluidHapticEngine
    get() = when {
      !port.hasVibrator -> FluidHapticEngine.Off
      supported.containsAll(FluidHapticPatterns.primitivesUsed.toList()) -> FluidHapticEngine.Composition
      else -> FluidHapticEngine.Platform
    }

  /** Vero se questo evento andra' a composizione (le sue primitive ci sono tutte). */
  fun usesComposition(event: FluidHapticEvent): Boolean =
    // `all` su una lista vuota e' vero: gli eventi solo di piattaforma non hanno composizione e non
    // sono "a composizione" per nessun device.
    event !in FluidHapticPatterns.platformOnly &&
      port.hasVibrator && FluidHapticPatterns.composition(event).all { it.id in supported }

  override fun play(event: FluidHapticEvent) {
    if (!enabled()) return
    if (!port.hasVibrator) return
    if (event in FluidHapticPatterns.platformOnly) {
      // I tre colpi di un tasto che cresce (3.0): la costante di sistema e basta, senza rete e
      // senza orologio. Vedi [FluidHapticPatterns.platformOnly].
      port.platform(FluidHapticPatterns.fallback(event))
      return
    }
    // Niente cancello sull'impostazione tattile di sistema: il valore di
    // `Settings.System.HAPTIC_FEEDBACK_ENABLED` non e' affidabile (su One UI resta a zero mentre
    // il telefono vibra benissimo, perche' Samsung tiene la sua impostazione altrove), e a
    // filtrare le vibrazioni di tocco ci pensa gia' il sistema attraverso `VibrationAttributes`.
    // Leggerlo per zittire tutto significava zittire tutto, e basta: successo davvero.
    if (event.continuous) {
      if (port.powerSave) return
      val now = clock()
      if (now - lastContinuousAt < CONTINUOUS_MIN_GAP_MILLIS) return
      lastContinuousAt = now
    } else if (event in FluidHapticPatterns.coalescible) {
      // Una vibrazione per momento (3.0): il secondo evento di controllo nello stesso istante e'
      // lo stesso momento detto da un altro componente, e suonarlo troncherebbe il primo.
      val now = clock()
      if (fluidHapticCoalesces(lastDiscreteAt, now)) return
      lastDiscreteAt = now
    }
    val attention = event == FluidHapticEvent.AlertAlarm || event == FluidHapticEvent.AlertWatch || event == FluidHapticEvent.AlertClear
    // Se la composizione non parte (permesso mancante, device che la rifiuta) resta la costante
    // di piattaforma: meglio un feedback diverso da quello disegnato che nessun feedback.
    if (!usesComposition(event) || !port.vibrate(FluidHapticPatterns.composition(event), attention)) {
      port.platform(FluidHapticPatterns.fallback(event))
    }
  }

  override fun grain(intensity: Float, style: FluidHapticGrainStyle) {
    if (!enabled() || !port.hasVibrator || port.powerSave) return
    if (style == FluidHapticGrainStyle.LibrePods) {
      // Un tick di sistema per ogni evento che ha deciso [FluidHapticGrain], **senza tetto**: e'
      // quello che fa LibrePods (`StyledButton.kt:382`), e il pavimento qui sotto trasformerebbe il
      // suo "ronzio a ogni evento" in una cosa piu' educata che non e' la sua. Il risparmio
      // energetico lo zittisce comunque, come ogni vibrazione che si ripete.
      port.platform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentFrequentTick)
      return
    }
    // Il pavimento della grana, non quello dei tick continui: a 40 ms la grana sarebbe di nuovo un
    // ticchettio. Il ritmo lo decide gia' FluidHapticGrain; questo e' solo il tetto del motore.
    val now = clock()
    if (now - lastGrainAt < GRAIN_MIN_GAP_MILLIS) return
    lastGrainAt = now
    val step = HapticPrimitiveStep(HapticPrimitive.TICK, intensity.coerceIn(0f, 1f))
    if (HapticPrimitive.TICK !in supported || !port.vibrate(listOf(step), attention = false)) {
      // Il tick di sistema che usa LibrePods per la stessa cosa: senza primitive non si dosa la forza,
      // ma il treno resta un treno.
      port.platform(androidx.compose.ui.hapticfeedback.HapticFeedbackType.SegmentFrequentTick)
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

    /** Il tetto della grana: un granello ogni 8 ms al massimo (sotto, il motore non li separa). */
    const val GRAIN_MIN_GAP_MILLIS = 8L
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

  // I numeri sono quelli di VibrationEffect.Composition.PRIMITIVE_*, copiati in HapticPrimitive
  // perche' i pattern si provano sul computer: lint non puo' sapere che coincidono.
  @SuppressLint("WrongConstant")
  override fun supportedPrimitives(ids: IntArray): Set<Int> {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptySet()
    val v = vibrator ?: return emptySet()
    val flags = runCatching { v.arePrimitivesSupported(*ids) }.getOrNull() ?: return emptySet()
    return ids.filterIndexed { index, _ -> flags.getOrNull(index) == true }.toSet()
  }

  override fun vibrate(steps: List<HapticPrimitiveStep>, attention: Boolean): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    val v = vibrator ?: return false
    val composition = VibrationEffect.startComposition()
    steps.forEach { composition.addPrimitive(it.id, it.scale.coerceIn(0f, 1f), it.delayMillis.coerceAtLeast(0)) }
    val effect = composition.compose()
    return runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val usage = if (attention) VibrationAttributes.USAGE_NOTIFICATION else VibrationAttributes.USAGE_TOUCH
        v.vibrate(effect, VibrationAttributes.createForUsage(usage))
      } else {
        // Fra Android 11 e 12 le VibrationAttributes non si possono passare, ma il sistema ricava lo
        // stesso uso da un AudioAttributes: ASSISTANCE_SONIFICATION e' il "tocco", e una vibrazione di
        // tocco e' quella che l'impostazione "Feedback tattile" spegne. Senza attributi la vibrazione
        // era di uso sconosciuto e partiva anche a feedback spento (fino alla 2.10.x): il cancello
        // resta del sistema, come sopra, invece di leggere un'impostazione che su One UI mente.
        val usage = if (attention) AudioAttributes.USAGE_NOTIFICATION_EVENT else AudioAttributes.USAGE_ASSISTANCE_SONIFICATION
        val audio = AudioAttributes.Builder()
          .setUsage(usage)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
        @Suppress("DEPRECATION") // sostituita dalla variante con VibrationAttributes, che qui non c'e'
        v.vibrate(effect, audio)
      }
    }.isSuccess
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
