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

/**
 * Il vibratore vero, e le costanti di Compose per il ripiego.
 *
 * @param platformHaptics il `HapticFeedback` di Compose, da cui il ripiego chiede la costante di
 *   tocco del sistema. Null (dalla 3.0): non c'e' composizione, e il ripiego e' l'effetto di fabbrica
 *   piu' vicino suonato direttamente dal vibratore ([predefinedHapticFor]). E' la strada di
 *   [fluidHapticsFor], per il codice a `View` che non ha `LocalHapticFeedback`.
 */
class AndroidHapticPort(
  private val context: Context,
  private val platformHaptics: HapticFeedback? = null,
) : HapticPort {

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
    val composition = VibrationEffect.startComposition()
    steps.forEach { composition.addPrimitive(it.id, it.scale.coerceIn(0f, 1f), it.delayMillis.coerceAtLeast(0)) }
    return play(composition.compose(), attention)
  }

  /**
   * Suona un effetto con l'uso giusto: "tocco" (che il sistema filtra con la sua impostazione) o, per
   * le allerte, "notifica". Condivisa dalle composizioni e dagli effetti di fabbrica del ripiego
   * senza `View`.
   */
  private fun play(effect: VibrationEffect, attention: Boolean): Boolean {
    val v = vibrator ?: return false
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
    val compose = platformHaptics
    if (compose != null) {
      runCatching { compose.performHapticFeedback(type) }
      return
    }
    // Senza Compose non c'e' una costante di tocco da chiedere a un View: si suona l'effetto di
    // fabbrica piu' vicino. Con la stessa rete di sempre sotto: un vibratore che rifiuta non deve
    // far cadere chi sta scrivendo su una tastiera.
    runCatching {
      val predefined = predefinedHapticFor(type)
      val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        VibrationEffect.createPredefined(predefined.effectId)
      } else {
        VibrationEffect.createWaveform(legacyWaveformFor(predefined), NO_REPEAT)
      }
      play(effect, attention = false)
    }
  }

  override val systemHapticsEnabled: Boolean
    get() = runCatching { Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0 }.getOrDefault(true)

  override val powerSave: Boolean
    get() = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
}

/** `VibrationEffect.createWaveform(timings, repeat)`: -1 e' "suona una volta e basta". */
private const val NO_REPEAT = -1

/**
 * L'aptica dell'engine per il codice che non e' Compose: una tastiera (`InputMethodService`), un
 * `View`, un servizio, un'attivita' a viste.
 *
 * E' lo stesso motore di quello che `FluidTheme` da' ai componenti — composizioni di primitive dove il
 * telefono le ha, una vibrazione sola per momento, i tick continui che si diradano e spariscono in
 * risparmio energetico, l'interruttore dell'app davanti a tutto — con una differenza: il ripiego dove le
 * primitive mancano non e' la costante di tocco del sistema (serve un `View`, o il `HapticFeedback` di
 * Compose, e qui non ci sono) ma l'effetto di fabbrica del vibratore piu' vicino ([predefinedHapticFor]).
 * Una tastiera che chiamava `View.performHapticFeedback` a mano sceglieva da sola le costanti e non
 * aveva nessuna di queste reti; adesso dice un evento del vocabolario, e il resto e' dell'engine.
 *
 * **Creala una volta** (un campo del servizio o della vista), non a ogni evento: tiene lo stato delle
 * reti — i 70 ms che fanno dei due eventi di un controllo uno solo, i 40 ms dei tick, il tetto della
 * grana — e chiede al telefono le primitive una volta sola. Un'istanza diversa da quella del tema non
 * condivide le reti con lui: un'app che mescola Compose e viste suona due volte lo stesso istante
 * se lo dice da due parti.
 *
 * Il permesso `VIBRATE` lo dichiara `engine-ui`.
 *
 * @param enabled letto a **ogni** evento: e' l'interruttore dell'app (`EngineSettings.hapticsEnabled`),
 *   e cambiarlo dalle impostazioni vale subito, senza ricreare niente.
 */
fun fluidHapticsFor(context: Context, enabled: () -> Boolean = { true }): FluidHaptics =
  FluidHapticsImpl(AndroidHapticPort(context.applicationContext ?: context), enabled = enabled)

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
