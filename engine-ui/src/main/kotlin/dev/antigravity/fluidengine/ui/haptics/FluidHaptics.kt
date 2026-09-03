package dev.antigravity.fluidengine.ui.haptics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Il vocabolario aptico del design system (1.19.0): ogni evento e' una cosa che l'utente sente
 * sotto il dito e che vuol dire sempre la stessa cosa in ogni app Pampa. I componenti dell'engine
 * lo usano da soli (un tasto di vetro fa [Tap], uno switch [ToggleOn]/[ToggleOff], un menu che si
 * apre [Open]); le app aggiungono i loro momenti (una soglia raggiunta, un errore, un'allerta).
 *
 * [continuous] marca i tick dei gesti che si ripetono (scorrere un indice, trascinare una griglia):
 * sono quelli che spariscono in risparmio energetico e che si diradano se arrivano troppo fitti.
 */
enum class FluidHapticEvent(val continuous: Boolean = false) {
  /** Il rilascio su un controllo: tasto di vetro, chip, riga. Leggero, mai invadente. */
  Tap,
  Confirm,
  Reject,
  ToggleOn,
  ToggleOff,

  /** Un gesto ha superato la soglia che lo fa contare (lo swipe di una pillola, un pull-to-refresh). */
  Threshold,

  /** Un aggancio in un gesto continuo: una riga dell'indice, una cella della griglia, un fotogramma. */
  Tick(continuous = true),

  /** Come [Tick], ma per quando gli agganci arrivano fitti (trascinamento veloce). */
  FrequentTick(continuous = true),
  GestureStart,
  GestureEnd,

  /** Qualcosa si apre: un menu, un pannello, un foglio. */
  Open,
  Close,
  Success,
  Warning,
  Error,

  /** L'assistente: inizio ascolto (sale), parlato rilevato, fine ascolto (scende), risposta pronta. */
  ListenStart,
  SpeechDetected,
  ListenEnd,
  ReplyReady,

  /** Un secondo di attesa in piu' (un limite del servizio): un tick lento e basso. */
  WaitTick(continuous = true),
  Stop,
  ActionConfirmed,
  ProviderSwitched,

  /** Il barometro cambia livello: sorveglianza, allerta, rientro. */
  AlertWatch,
  AlertAlarm,
  AlertClear,
}

/** Con cosa si vibra: le composizioni di primitive (ricche), le costanti di piattaforma, o niente. */
enum class FluidHapticEngine { Composition, Platform, Off }

interface FluidHaptics {
  val engine: FluidHapticEngine

  fun play(event: FluidHapticEvent)
}

/** Fuori da un [dev.antigravity.fluidengine.ui.theme.FluidTheme] (anteprime, test) non si vibra. */
object NoOpFluidHaptics : FluidHaptics {
  override val engine: FluidHapticEngine = FluidHapticEngine.Off
  override fun play(event: FluidHapticEvent) = Unit
}

val LocalFluidHaptics: ProvidableCompositionLocal<FluidHaptics> = staticCompositionLocalOf { NoOpFluidHaptics }

/** L'aptica del tema corrente. Statico: cambia solo con le impostazioni, non ricompone i figli. */
@Composable
fun rememberFluidHaptics(): FluidHaptics = LocalFluidHaptics.current

/**
 * Le primitive di `VibrationEffect.Composition`, con gli stessi valori numerici di Android
 * (API 30+): tenute qui come costanti proprie perche' i pattern devono essere puri e provati sul
 * computer, dove `android.os.VibrationEffect` non esiste.
 */
object HapticPrimitive {
  const val CLICK = 1
  const val THUD = 2
  const val SPIN = 3
  const val QUICK_RISE = 4
  const val SLOW_RISE = 5
  const val QUICK_FALL = 6
  const val TICK = 7
  const val LOW_TICK = 8

  /** Tutte le primitive, nell'ordine di Android: per elencarle in una pagina di prova. */
  val all: List<Int> = listOf(CLICK, THUD, SPIN, QUICK_RISE, SLOW_RISE, QUICK_FALL, TICK, LOW_TICK)

  /** Il nome della primitiva come lo scrive Android (`PRIMITIVE_CLICK`...), senza il prefisso. */
  fun label(id: Int): String = when (id) {
    CLICK -> "CLICK"
    THUD -> "THUD"
    SPIN -> "SPIN"
    QUICK_RISE -> "QUICK_RISE"
    SLOW_RISE -> "SLOW_RISE"
    QUICK_FALL -> "QUICK_FALL"
    TICK -> "TICK"
    LOW_TICK -> "LOW_TICK"
    else -> "#$id"
  }

  /** Durate nominali in millisecondi, per il budget dei pattern (Google le documenta cosi'). */
  fun nominalMillis(id: Int): Int = when (id) {
    CLICK -> 20
    THUD -> 40
    SPIN -> 90
    QUICK_RISE -> 50
    SLOW_RISE -> 100
    QUICK_FALL -> 50
    TICK -> 10
    LOW_TICK -> 20
    else -> 0
  }
}

/** Una primitiva in una composizione: quale, quanto forte (0..1), dopo quanto dalla precedente. */
data class HapticPrimitiveStep(val id: Int, val scale: Float, val delayMillis: Int = 0)

/**
 * I pattern, evento per evento: la composizione (secca e breve, stile iOS — tick e click per i
 * controlli, rampe solo per l'assistente e i gesti, il tonfo solo per errori e allerta; nessun
 * pattern oltre i 400 ms, nessun controllo oltre i 120) e il ripiego di piattaforma per i device
 * senza primitive. Da tarare sul telefono con la pagina "Prova i feedback" delle app.
 */
object FluidHapticPatterns {

  fun composition(event: FluidHapticEvent): List<HapticPrimitiveStep> = when (event) {
    FluidHapticEvent.Tap -> listOf(HapticPrimitiveStep(HapticPrimitive.TICK, 0.5f))
    FluidHapticEvent.Confirm -> listOf(HapticPrimitiveStep(HapticPrimitive.CLICK, 0.7f))
    FluidHapticEvent.Reject -> listOf(HapticPrimitiveStep(HapticPrimitive.LOW_TICK, 1f), HapticPrimitiveStep(HapticPrimitive.LOW_TICK, 1f, 60))
    FluidHapticEvent.ToggleOn -> listOf(HapticPrimitiveStep(HapticPrimitive.TICK, 0.6f), HapticPrimitiveStep(HapticPrimitive.CLICK, 0.5f, 30))
    FluidHapticEvent.ToggleOff -> listOf(HapticPrimitiveStep(HapticPrimitive.CLICK, 0.4f))
    FluidHapticEvent.Threshold -> listOf(HapticPrimitiveStep(HapticPrimitive.CLICK, 0.8f))
    FluidHapticEvent.Tick -> listOf(HapticPrimitiveStep(HapticPrimitive.LOW_TICK, 0.35f))
    FluidHapticEvent.FrequentTick -> listOf(HapticPrimitiveStep(HapticPrimitive.LOW_TICK, 0.2f))
    FluidHapticEvent.GestureStart -> listOf(HapticPrimitiveStep(HapticPrimitive.QUICK_RISE, 0.4f))
    FluidHapticEvent.GestureEnd -> listOf(HapticPrimitiveStep(HapticPrimitive.QUICK_FALL, 0.4f))
    FluidHapticEvent.Open -> listOf(HapticPrimitiveStep(HapticPrimitive.QUICK_RISE, 0.3f), HapticPrimitiveStep(HapticPrimitive.TICK, 0.5f))
    FluidHapticEvent.Close -> listOf(HapticPrimitiveStep(HapticPrimitive.QUICK_FALL, 0.3f))
    FluidHapticEvent.Success -> listOf(HapticPrimitiveStep(HapticPrimitive.TICK, 0.5f), HapticPrimitiveStep(HapticPrimitive.CLICK, 0.8f, 80))
    FluidHapticEvent.Warning -> listOf(HapticPrimitiveStep(HapticPrimitive.LOW_TICK, 0.8f), HapticPrimitiveStep(HapticPrimitive.LOW_TICK, 0.8f, 70))
    FluidHapticEvent.Error -> listOf(HapticPrimitiveStep(HapticPrimitive.THUD, 0.6f), HapticPrimitiveStep(HapticPrimitive.LOW_TICK, 0.5f))
    FluidHapticEvent.ListenStart -> listOf(HapticPrimitiveStep(HapticPrimitive.QUICK_RISE, 0.6f))
    FluidHapticEvent.SpeechDetected -> listOf(HapticPrimitiveStep(HapticPrimitive.TICK, 0.3f))
    FluidHapticEvent.ListenEnd -> listOf(HapticPrimitiveStep(HapticPrimitive.QUICK_FALL, 0.6f))
    FluidHapticEvent.ReplyReady -> listOf(HapticPrimitiveStep(HapticPrimitive.TICK, 0.4f), HapticPrimitiveStep(HapticPrimitive.TICK, 0.6f, 70))
    FluidHapticEvent.WaitTick -> listOf(HapticPrimitiveStep(HapticPrimitive.LOW_TICK, 0.3f))
    FluidHapticEvent.Stop -> listOf(HapticPrimitiveStep(HapticPrimitive.CLICK, 0.5f))
    FluidHapticEvent.ActionConfirmed -> listOf(HapticPrimitiveStep(HapticPrimitive.CLICK, 0.6f), HapticPrimitiveStep(HapticPrimitive.TICK, 0.4f))
    FluidHapticEvent.ProviderSwitched -> listOf(HapticPrimitiveStep(HapticPrimitive.LOW_TICK, 0.4f), HapticPrimitiveStep(HapticPrimitive.TICK, 0.4f, 50))
    FluidHapticEvent.AlertWatch -> listOf(HapticPrimitiveStep(HapticPrimitive.TICK, 0.45f), HapticPrimitiveStep(HapticPrimitive.TICK, 0.45f, 90))
    FluidHapticEvent.AlertAlarm -> listOf(HapticPrimitiveStep(HapticPrimitive.THUD, 1f), HapticPrimitiveStep(HapticPrimitive.TICK, 0.8f, 90))
    FluidHapticEvent.AlertClear -> listOf(HapticPrimitiveStep(HapticPrimitive.QUICK_FALL, 0.5f))
  }

  /** Le costanti di Compose per i device senza primitive; sotto API 30 Compose degrada da solo. */
  fun fallback(event: FluidHapticEvent): HapticFeedbackType = when (event) {
    FluidHapticEvent.Tap -> HapticFeedbackType.VirtualKey
    FluidHapticEvent.Confirm, FluidHapticEvent.Success, FluidHapticEvent.ActionConfirmed, FluidHapticEvent.ReplyReady -> HapticFeedbackType.Confirm
    FluidHapticEvent.Reject, FluidHapticEvent.Error -> HapticFeedbackType.Reject
    FluidHapticEvent.ToggleOn -> HapticFeedbackType.ToggleOn
    FluidHapticEvent.ToggleOff -> HapticFeedbackType.ToggleOff
    FluidHapticEvent.Threshold -> HapticFeedbackType.GestureThresholdActivate
    FluidHapticEvent.Tick, FluidHapticEvent.WaitTick, FluidHapticEvent.SpeechDetected -> HapticFeedbackType.SegmentTick
    FluidHapticEvent.FrequentTick -> HapticFeedbackType.SegmentFrequentTick
    FluidHapticEvent.GestureStart, FluidHapticEvent.Open, FluidHapticEvent.ListenStart -> HapticFeedbackType.ContextClick
    FluidHapticEvent.GestureEnd, FluidHapticEvent.Close, FluidHapticEvent.AlertClear, FluidHapticEvent.ListenEnd, FluidHapticEvent.Stop -> HapticFeedbackType.GestureEnd
    FluidHapticEvent.Warning, FluidHapticEvent.AlertWatch, FluidHapticEvent.AlertAlarm, FluidHapticEvent.ProviderSwitched -> HapticFeedbackType.LongPress
  }

  /** Quanto dura un pattern, secondo le durate nominali: il test tiene i controlli sotto i 120 ms. */
  fun durationMillis(event: FluidHapticEvent): Int =
    composition(event).sumOf { it.delayMillis + HapticPrimitive.nominalMillis(it.id) }

  /** Gli eventi dei controlli: quelli che devono restare secchi. */
  val controlEvents: Set<FluidHapticEvent> = setOf(
    FluidHapticEvent.Tap, FluidHapticEvent.Confirm, FluidHapticEvent.Reject, FluidHapticEvent.ToggleOn, FluidHapticEvent.ToggleOff,
    FluidHapticEvent.Threshold, FluidHapticEvent.Tick, FluidHapticEvent.FrequentTick, FluidHapticEvent.Open, FluidHapticEvent.Close,
  )

  /** Tutte le primitive che i pattern usano: il motore le chiede al device una volta sola. */
  val primitivesUsed: IntArray = FluidHapticEvent.entries.flatMap { composition(it).map { step -> step.id } }.distinct().toIntArray()
}
