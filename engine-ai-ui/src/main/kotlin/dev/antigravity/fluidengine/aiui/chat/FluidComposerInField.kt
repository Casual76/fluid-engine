package dev.antigravity.fluidengine.aiui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.aiui.halo.FluidHaloClock
import dev.antigravity.fluidengine.aiui.halo.FluidHaloColours
import dev.antigravity.fluidengine.aiui.halo.FluidHaloFieldSpec
import dev.antigravity.fluidengine.aiui.halo.FluidHaloMood
import dev.antigravity.fluidengine.aiui.halo.HaloPresenceFloor
import dev.antigravity.fluidengine.aiui.halo.haloApproach
import dev.antigravity.fluidengine.aiui.halo.haloBlendForTheme
import dev.antigravity.fluidengine.aiui.halo.wrap01
import dev.antigravity.fluidengine.aiui.strings.FluidChatStrings
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleAction
import dev.antigravity.fluidengine.ui.fluid.FluidSearchFieldDefaults
import dev.antigravity.fluidengine.ui.fluid.FluidComposerHalo
import dev.antigravity.fluidengine.ui.fluid.FluidComposerSlots
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidSearchBehavior
import dev.antigravity.fluidengine.ui.fluid.FluidSearchExpansion
import dev.antigravity.fluidengine.ui.fluid.FluidSearchPlacement
import dev.antigravity.fluidengine.ui.fluid.FluidSearchSpec
import dev.antigravity.fluidengine.ui.fluid.FluidSearchTrailing
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionState
import dev.antigravity.fluidengine.ui.fluid.icons.Add
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.VolumeOff
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

// Il composer nuovo dell'assistente (F6B.7, piano F6B §7). Scelta di Alessio del 21 novembre 2026: «ne costruiamo uno
// nuovo sui nuovi componenti aggiornati, sempre reso un po' speciale (tipo campo di testo con alone attorno può essere
// un'idea)». È il campo dell'engine (`FluidSearchSpec.assistant` con le parti del composer) più l'alone attorno al
// vetro. `FluidComposer` di prima resta com'è: le app passano a questo a mano, quando lo decide Alessio.

/**
 * Il composer dell'assistente **dentro il campo** (F6B.7): restituisce lo [FluidSearchSpec] già pieno, da dare a
 * `FluidScreen(search = …)`. Il campo sta in basso, sale sopra la tastiera e cresce di riga in riga fino a [maxLines];
 * dentro ha:
 * - lo **slot a sinistra**, il «+» ([onAdd]) o ciò che l'app mette in [leading];
 * - il **vassoio** sopra il testo, con i [chips] (il modello) e le [pills] (allegati, plugin, la strada per le
 *   impostazioni): c'è solo se l'app ne dà;
 * - il **tasto a destra a stati** sulla stessa sagoma ([fluidAssistantFieldTrailing]): il microfono a campo vuoto,
 *   «Invia» con del testo, lo stop mentre l'assistente lavora o ascolta, «Zitta» mentre legge;
 * - la **riga della voce** al posto del testo mentre ascolta ([voiceLine], per esempio [FluidComposerVoiceLine]);
 * - **l'alone attorno al vetro** ([FluidAssistantFieldHalo]): una luce continua che abbraccia il bordo e sfuma fuori
 *   (F6B.16), ferma e bassa a riposo, viva quando si scrive, segue la voce in ascolto e scorre mentre l'assistente lavora
 *   o scrive.
 *
 * Come in [FluidComposer], il campo è sempre usabile: mentre l'assistente lavora si scrive e si manda lo stesso (la
 * tastiera invia e la domanda va in coda); il tasto a destra resta lo stop. [onSend] svuota il campo
 * (`state.sent()`) solo se la domanda è partita davvero.
 *
 * @param state il testo, salvato con la pagina: una rotazione non lo perde.
 * @param action il tasto a destra, di solito `fluidComposerAction(...)`.
 * @param level il livello del microfono, 0..1, **come lambda**: lo legge solo il disegno dell'alone.
 * @param failed l'ultima risposta è fallita: l'alone prende i colori dell'errore.
 * @param sending lo stato dell'invio (F6B.3a): mentre lavora «Invia» è spento e mostra l'anello.
 * @param halo falso: niente alone.
 * @param active l'apertura del campo, se la tiene l'app; null in [onActiveChange]: la tiene lo schermo.
 */
@Composable
fun fluidAssistantField(
  state: FluidComposerState,
  action: FluidComposerAction,
  onSend: () -> Unit,
  onStop: () -> Unit = {},
  onVoice: () -> Unit = {},
  onStopListening: () -> Unit = {},
  onMute: () -> Unit = {},
  onAdd: (() -> Unit)? = null,
  prompt: String = FluidChatStrings().composerPlaceholder,
  strings: FluidChatStrings = FluidChatStrings(),
  addDescription: String = "Aggiungi",
  leading: (@Composable () -> Unit)? = null,
  chips: (@Composable RowScope.() -> Unit)? = null,
  pills: (@Composable () -> Unit)? = null,
  voiceLine: (@Composable () -> Unit)? = null,
  voiceActive: Boolean = action == FluidComposerAction.StopListening,
  level: () -> Float = { 0f },
  failed: Boolean = false,
  sending: FluidActionState = FluidActionState.Idle,
  halo: Boolean = true,
  active: Boolean = false,
  onActiveChange: ((Boolean) -> Unit)? = null,
  maxLines: Int = 6,
  requestMicPermission: Boolean = true,
  placement: FluidSearchPlacement = FluidSearchPlacement.BottomBar,
  expansion: FluidSearchExpansion = FluidSearchExpansion.AboveKeyboard,
): FluidSearchSpec {
  val context = LocalContext.current
  val micGranted = remember { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
  val currentVoice by rememberUpdatedState(onVoice)
  val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) currentVoice() }
  val startVoice = remember(micGranted, requestMicPermission, micLauncher) {
    { if (micGranted || !requestMicPermission) currentVoice() else micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
  }
  val trailing = fluidAssistantFieldTrailing(
    action = action, hasInput = state.text.isNotBlank(), strings = strings, sending = sending,
    onVoice = startVoice, onStop = onStop, onStopListening = onStopListening, onMute = onMute,
  )
  val fieldHalo = if (halo) rememberFluidAssistantFieldHalo(fluidAssistantFieldMood(action, failed), focused = active, level = level) else null
  val plus: (@Composable () -> Unit)? = leading ?: onAdd?.let { add ->
    { FluidCapsuleAction(FluidIcons.Add, addDescription, add, size = AssistantFieldLeadingSize) }
  }
  val tray: (@Composable () -> Unit)? = if (chips == null && pills == null) null else {
    {
      Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(AssistantFieldTrayGap),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        chips?.invoke(this)
        pills?.invoke()
      }
    }
  }
  val line: (@Composable () -> Unit)? = if (voiceActive && voiceLine != null) {
    { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { voiceLine() } }
  } else null
  return FluidSearchSpec.assistant(
    question = state.text,
    onQuestionChange = { state.text = it },
    onAsk = onSend,
    prompt = prompt,
    placement = placement,
    behavior = FluidSearchBehavior.AlwaysVisible,
    expansion = expansion,
    maxLines = maxLines,
    active = active,
    onActiveChange = onActiveChange,
    composer = FluidComposerSlots(leading = plus, attachments = tray, trailing = trailing, voiceLine = line, halo = fieldHalo),
  )
}

/** Il lato del «+» nel campo: quello dei comandi del campo (40 dp). */
private val AssistantFieldLeadingSize: Dp = FluidSearchFieldDefaults.ActionSize

/** Lo spazio fra le pillole del vassoio: quello delle pillole del composer di prima. */
private val AssistantFieldTrayGap: Dp = 6.dp

/**
 * Il tasto a destra del composer dell'assistente, dal tasto di [FluidComposer] ([fluidComposerAction]). Pura. Un tasto
 * solo, sulla stessa sagoma:
 * - [FluidComposerAction.Voice] e [FluidComposerAction.Send] con del testo: il microfono a campo vuoto, «Invia» con del
 *   testo ([FluidSearchTrailing.voice]);
 * - [FluidComposerAction.Send] a campo vuoto (niente microfono): «Invia», che a campo vuoto è la X;
 * - [FluidComposerAction.Stop] e [FluidComposerAction.StopAndQueue]: lo stop; con del testo la tastiera lo manda in
 *   coda, come l'invio accanto allo stop del composer di prima;
 * - [FluidComposerAction.StopListening]: lo stop dell'ascolto;
 * - [FluidComposerAction.Mute]: «Zitta», con l'altoparlante barrato.
 */
fun fluidAssistantFieldTrailing(
  action: FluidComposerAction,
  hasInput: Boolean,
  strings: FluidChatStrings,
  sending: FluidActionState,
  onVoice: () -> Unit,
  onStop: () -> Unit,
  onStopListening: () -> Unit,
  onMute: () -> Unit,
): FluidSearchTrailing = when (action) {
  FluidComposerAction.Voice -> FluidSearchTrailing.voice(onVoice, sending)
  FluidComposerAction.Send -> if (hasInput) FluidSearchTrailing.voice(onVoice, sending) else FluidSearchTrailing.send(sending)
  FluidComposerAction.Stop, FluidComposerAction.StopAndQueue -> FluidSearchTrailing.stop(onStop, strings.stop)
  FluidComposerAction.StopListening -> FluidSearchTrailing.stop(onStopListening, strings.voiceStop)
  FluidComposerAction.Mute -> FluidSearchTrailing.stop(onMute, strings.mute, FluidIcons.VolumeOff)
}

/**
 * L'umore dell'alone del campo dal tasto a destra (F6B.7). Pura: ascolta, lavora (anche mentre trascrive), legge ad alta
 * voce (scrive), l'errore dell'ultima risposta, altrimenti a riposo.
 */
fun fluidAssistantFieldMood(action: FluidComposerAction, failed: Boolean = false): FluidHaloMood = when (action) {
  FluidComposerAction.StopListening -> FluidHaloMood.LISTENING
  FluidComposerAction.Stop, FluidComposerAction.StopAndQueue -> FluidHaloMood.WORKING
  FluidComposerAction.Mute -> FluidHaloMood.WRITING
  FluidComposerAction.Voice, FluidComposerAction.Send -> if (failed) FluidHaloMood.ERROR else FluidHaloMood.IDLE
}

// ---- L'alone attorno al campo ----------------------------------------------------------------------------------------

/**
 * I numeri dell'alone del campo (F6B.7).
 *
 * **Dalla F6B.16 l'alone non ha più macchie, anello né scia**: è la luce continua di `FluidAssistantFieldGlow.kt` (uno
 * shader, un disegno per fotogramma), coi suoi numeri. Di qui restano in uso presenza, ampiezze, passi per umore, respiro
 * e dissolvenze; [Spec], i numeri dell'anello (`Ring…`) e della scia (`Comet…`) restano per compatibilità e non si
 * disegnano più.
 *
 * **La luce e i colori: a occhio**, scelti il 21 novembre 2026 partendo da `glow` del [FluidComposer] di prima (sei
 * macchie da 28 dp all'alfa 0,40, anello da 1,5 dp che gira in 8 s): Apple non ha un campo con l'alone da misurare (il
 * campo di «Scrivi a Siri» ha un bordo luminoso, nessuna clip nel progetto). Il campo è alto 40 dp e non due righe: le
 * macchie sono un po' più piccole, così sbordano di una dozzina di dp e non sulla chat; sono appena più accese, perché
 * qui l'alone c'è sempre ed è il segno del composer (la prima prova sul banco, a 22 dp e 0,34, a riposo quasi non si
 * vedeva).
 *
 * **Il moto: a occhio, scelta di Alessio del 22 novembre 2026** («l'alone deve essere animato»): mentre il campo è
 * attivo (aperto, o l'assistente ascolta, lavora, legge) un **giro di luce** corre lungo il bordo e l'alone **respira**
 * piano; a riposo e col movimento ridotto è fermo. Il moto entra ed esce in dissolvenza e rallenta fino a fermarsi:
 * niente parte o si ferma di colpo, e niente lampeggia (il respiro cambia l'alfa del 12% in 4,5 s).
 */
object FluidAssistantFieldHaloProfile {
  /** Le macchie: sei da 26 dp, accese al più al 45%, senza filo (l'anello lo fa il disegno sopra il vetro). */
  val Spec: FluidHaloFieldSpec = FluidHaloFieldSpec(blobCount = 6, blobRadius = 26.dp, peakAlpha = 0.45f, edgeStroke = 0.dp)

  /** A riposo (il campo chiuso, l'assistente fermo) l'alone c'è ma al 70%, ed è **fermo**: nessun fotogramma. */
  const val RestPresence: Float = 0.70f

  /** L'ampiezza a campo aperto mentre si scrive: viva ma bassa. */
  const val FocusAmplitude: Float = 0.35f

  /** L'ampiezza mentre l'assistente lavora, scrive o ha sbagliato: quella dell'alone di sessione. */
  const val BusyAmplitude: Float = 0.6f

  /**
   * Il passo dell'orologio per umore (tempo dell'orologio per secondo vero). A campo aperto 1: il giro di luce fa il bordo
   * in 5 s, le macchie in ~17 s. In ascolto un po' più svelto, al lavoro e mentre legge di più (lo scorrere dice «sta
   * facendo»), nell'errore più lento.
   */
  const val FocusTempo: Float = 1f
  const val ListeningTempo: Float = 1.2f
  const val WorkingTempo: Float = 1.6f
  const val WritingTempo: Float = 1.4f
  const val ErrorTempo: Float = 0.8f

  /** L'anello sul bordo del vetro: spessore. */
  val RingWidth: Dp = 2.dp

  /** L'alfa dell'anello: dal 38% a riposo, più il 40% dell'ampiezza (fino al 78% con la voce piena). */
  const val RingFloorAlpha: Float = 0.38f
  const val RingAmplitudeAlpha: Float = 0.40f

  /** I colori girano sull'anello un giro ogni 8 s (di tempo dell'orologio), come l'anello del composer di prima. */
  const val RingCyclesPerSec: Float = 0.125f

  /** Quante fermate ha il gradiente dell'anello. */
  const val RingStops: Int = 12

  /** Il giro di luce: un giro del bordo ogni 5 s di tempo dell'orologio (3,1 s al lavoro). */
  const val CometLapsPerSec: Float = 0.2f

  /** Quanto è lunga la scia, in frazione del bordo (~125 dp su un campo di 368 × 40). */
  const val CometLength: Float = 0.16f

  /** In quante fette si disegna la scia, dalla coda (trasparente) alla testa (piena). */
  const val CometSlices: Int = 5

  /** La testa della scia: un filo da 2,5 dp all'alfa 0,65, dentro un bagliore da 7 dp all'alfa 0,22. */
  val CometWidth: Dp = 2.5.dp
  const val CometAlpha: Float = 0.65f
  val CometGlowWidth: Dp = 7.dp
  const val CometGlowAlpha: Float = 0.22f

  /** Il respiro: l'alfa di macchie, anello e scia va su e giù del 12% in 4,5 s di tempo dell'orologio. */
  const val BreathDepth: Float = 0.12f
  const val BreathPeriod: Float = 4.5f

  /** Il moto entra in 400 ms ed esce in 600 (e intanto rallenta fino a fermarsi). */
  const val MotionFadeInMs: Int = 400
  const val MotionFadeOutMs: Int = 600

  /** La presenza entra in 220 ms ed esce in 260, come l'alone del composer di prima. */
  const val FadeInMs: Int = 220
  const val FadeOutMs: Int = 260
}

/**
 * Quanto è acceso l'alone del campo, da 0 a 1 (F6B.7). Pura. A riposo zero (resta la presenza bassa e ferma), a campo
 * aperto [FluidAssistantFieldHaloProfile.FocusAmplitude], in ascolto segue il microfono senza scendere sotto il 30%, al
 * lavoro, mentre scrive e nell'errore [FluidAssistantFieldHaloProfile.BusyAmplitude]. Col movimento ridotto la voce non
 * fa pulsare niente: l'ascolto resta al suo minimo.
 */
fun fluidAssistantFieldAmplitude(mood: FluidHaloMood, focused: Boolean, level: Float, reducedMotion: Boolean = false): Float = when (mood) {
  FluidHaloMood.HIDDEN -> 0f
  FluidHaloMood.LISTENING -> 0.30f + if (reducedMotion) 0f else 0.70f * level.coerceIn(0f, 1f)
  FluidHaloMood.WORKING, FluidHaloMood.WRITING, FluidHaloMood.ERROR -> FluidAssistantFieldHaloProfile.BusyAmplitude
  FluidHaloMood.IDLE, FluidHaloMood.DONE -> if (focused) FluidAssistantFieldHaloProfile.FocusAmplitude else 0f
}

/** La presenza dell'alone: bassa e ferma a riposo, piena altrimenti. Pura. */
fun fluidAssistantFieldPresence(mood: FluidHaloMood, focused: Boolean): Float = when {
  mood == FluidHaloMood.HIDDEN -> 0f
  !focused && (mood == FluidHaloMood.IDLE || mood == FluidHaloMood.DONE) -> FluidAssistantFieldHaloProfile.RestPresence
  else -> 1f
}

/**
 * Se l'alone si muove (F6B.7): il campo è attivo, cioè aperto ([focused]) o con l'assistente che ascolta, lavora o legge.
 * Pura. A riposo no: niente fotogrammi finché il campo è chiuso e l'assistente fermo. Col movimento ridotto mai.
 */
fun fluidAssistantFieldMoves(mood: FluidHaloMood, focused: Boolean, reducedMotion: Boolean): Boolean =
  !reducedMotion && mood != FluidHaloMood.HIDDEN && (focused || (mood != FluidHaloMood.IDLE && mood != FluidHaloMood.DONE))

/** Il passo dell'orologio dell'alone per [mood] ([FluidAssistantFieldHaloProfile.FocusTempo] e i suoi vicini). Pura. */
fun fluidAssistantFieldTempo(mood: FluidHaloMood): Float = when (mood) {
  FluidHaloMood.IDLE, FluidHaloMood.DONE, FluidHaloMood.HIDDEN -> FluidAssistantFieldHaloProfile.FocusTempo
  FluidHaloMood.LISTENING -> FluidAssistantFieldHaloProfile.ListeningTempo
  FluidHaloMood.WORKING -> FluidAssistantFieldHaloProfile.WorkingTempo
  FluidHaloMood.WRITING -> FluidAssistantFieldHaloProfile.WritingTempo
  FluidHaloMood.ERROR -> FluidAssistantFieldHaloProfile.ErrorTempo
}

/** L'alfa dell'anello per [amplitude] e [presence]. Pura. */
fun fluidAssistantFieldRingAlpha(amplitude: Float, presence: Float): Float =
  (FluidAssistantFieldHaloProfile.RingFloorAlpha + FluidAssistantFieldHaloProfile.RingAmplitudeAlpha * amplitude.coerceIn(0f, 1f)) *
    presence.coerceIn(0f, 1f)

/**
 * Il respiro dell'alone al tempo [time] dell'orologio, con il moto a [motion] (0 fermo, 1 pieno): un fattore sull'alfa
 * attorno a 1, ±[FluidAssistantFieldHaloProfile.BreathDepth]. Fermo vale 1. Pura.
 */
fun fluidAssistantFieldBreath(time: Float, motion: Float): Float =
  1f + FluidAssistantFieldHaloProfile.BreathDepth * motion.coerceIn(0f, 1f) *
    sin(2f * PI.toFloat() * time / FluidAssistantFieldHaloProfile.BreathPeriod)

/** Dove sta la testa del giro di luce al tempo [time], in frazione del bordo (0..1, in senso orario). Pura. */
fun fluidAssistantFieldCometHead(time: Float): Float = wrap01(time * FluidAssistantFieldHaloProfile.CometLapsPerSec)

/** Il peso della fetta [slice] della scia (0 la coda, l'ultima la testa): cresce col quadrato verso la testa. Pura. */
fun fluidAssistantFieldCometWeight(slice: Int, slices: Int = FluidAssistantFieldHaloProfile.CometSlices): Float {
  val w = (slice + 1).toFloat() / slices
  return w * w
}

/**
 * L'alone attorno al campo del composer dell'assistente (F6B.7, rifatto in F6B.16): **una luce continua** che abbraccia il
 * bordo del vetro e sfuma verso l'esterno, disegnata **dietro** di lui (dentro la copre il vetro). I colori del tema
 * scorrono piano lungo il perimetro e un'onda larga di luce (due creste, mai una macchia) gira col suo passo; mentre il
 * campo è attivo tutto **respira** piano. Niente sfere: parole di Alessio del 23 novembre 2026, «un alone non è composto
 * da sfere, ma è qualcosa più dinamico», con Gemini come riferimento. Lo costruisce [rememberFluidAssistantFieldHalo].
 *
 * - **Vivo ma non fastidioso**: fermo e basso a riposo; attivo (aperto, in ascolto, al lavoro) i colori fanno il giro del
 *   bordo in 14 s e l'alfa respira del 12% in 4,5 s; il moto entra in 400 ms ed esce in 600, rallentando fino a fermarsi.
 * - **Movimento ridotto**: la luce è ferma e la voce non la fa pulsare; restano i colori, che entrano ed escono in
 *   dissolvenza.
 * - **Niente ricatture del vetro**: il campo lo disegna nella barra, sopra la pagina, fuori dalla cattura del vetro
 *   ([FluidComposerHalo]). Tempo, ampiezza, presenza e moto si leggono nel disegno: a centoventi hertz si ridisegna solo
 *   il suo strato, e nessuno ricompone. A riposo l'orologio non gira.
 * - **Il costo** di un fotogramma è un disegno solo: un rettangolo attorno al vetro con lo shader della luce (Android 13+),
 *   o un filo col gradiente a ventaglio dove lo shader non c'è.
 */
@Stable
class FluidAssistantFieldHalo internal constructor(
  internal val clock: FluidHaloClock,
  private val presence: State<Float>,
  private val motion: State<Float>,
  private val blobs: List<State<Color>>,
  private val ring: List<Color>,
  private val blend: BlendMode,
  private val fieldActive: MutableState<Boolean>,
) : FluidComposerHalo {
  private val frameColours = MutableList(GlowColours) { Color.Transparent }
  private val painter = FluidAssistantFieldGlowPainter()

  /** La presenza di adesso, 0..1. */
  val presenceNow: Float get() = presence.value

  /** Il moto di adesso, 0 fermo e 1 pieno: il giro di luce e il respiro ci sono quanto vale. */
  val motionNow: Float get() = motion.value

  /** Il campo dice se è aperto (F6B.7): anche quando la sua apertura la tiene lo schermo, l'alone lo sa. */
  override fun fieldActiveChanged(active: Boolean) {
    if (fieldActive.value != active) fieldActive.value = active
  }

  /** Vero se la luce si disegna con lo shader (Android 13+); falso col ripiego, il solo filo sul bordo. */
  internal val shaded: Boolean get() = painter.shaded

  /** La luce continua, un disegno solo, dietro il vetro (dentro la copre lui). */
  override fun DrawScope.drawBehindGlass(glass: RoundRect) {
    val p = presence.value
    if (p <= HaloPresenceFloor) return
    // I tre colori del ciclo dell'umore (animati quando l'umore cambia): quelli del tema, o l'errore.
    for (i in frameColours.indices) frameColours[i] = blobs[i % blobs.size].value
    val breath = fluidAssistantFieldBreath(clock.time, motion.value)
    with(painter) { draw(glass, frameColours, clock.time, clock.amplitude, p * breath, blend) }
  }

  /** Sopra il vetro niente (F6B.16): il filo di luce sta appena fuori dal bordo, nella luce sola. */
  override fun DrawScope.drawOverGlass(glass: RoundRect) = Unit
}

/** Quanti colori scorrono lungo il bordo: i primi tre del ciclo dell'umore. */
private const val GlowColours: Int = 3

/**
 * L'alone del campo per [mood] (di solito [fluidAssistantFieldMood]): [focused] è il campo aperto, [level] il livello del
 * microfono, letto solo dal loop di fotogrammi. Il campo dice anche da sé se è aperto ([FluidComposerHalo.fieldActiveChanged]):
 * basta l'uno o l'altro.
 */
@Composable
fun rememberFluidAssistantFieldHalo(
  mood: FluidHaloMood,
  focused: Boolean,
  level: () -> Float = { 0f },
  colours: FluidHaloColours = FluidHaloColours.fromTheme(),
  blend: BlendMode = haloBlendForTheme(),
): FluidAssistantFieldHalo {
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val currentLevel by rememberUpdatedState(level)
  val fieldActive = remember { mutableStateOf(false) }
  val open = focused || fieldActive.value
  val moving = fluidAssistantFieldMoves(mood, open, reduced)
  // Il moto: entra ed esce in dissolvenza; col movimento ridotto è zero subito.
  val motion = remember { Animatable(if (moving) 1f else 0f) }
  LaunchedEffect(moving, reduced) {
    // Già al suo valore non anima: nessun fotogramma chiesto (a riposo l'alone non chiede niente, nemmeno all'avvio).
    when {
      reduced -> motion.snapTo(0f)
      moving -> if (motion.value != 1f) motion.animateTo(1f, FluidMotion.fadeIn(FluidAssistantFieldHaloProfile.MotionFadeInMs))
      else -> if (motion.value != 0f) motion.animateTo(0f, FluidMotion.fadeOut(FluidAssistantFieldHaloProfile.MotionFadeOutMs))
    }
  }
  val clock = rememberAssistantFieldClock(
    moving = moving,
    speed = fluidAssistantFieldTempo(mood),
    target = { fluidAssistantFieldAmplitude(mood, open, currentLevel(), reduced) },
    motion = { motion.value },
  )
  val presenceTarget = fluidAssistantFieldPresence(mood, open)
  val presence = remember { Animatable(presenceTarget) }
  LaunchedEffect(presenceTarget) {
    if (presence.value == presenceTarget) return@LaunchedEffect
    val rising = presenceTarget > presence.value
    presence.animateTo(presenceTarget, if (rising) FluidMotion.fadeIn(FluidAssistantFieldHaloProfile.FadeInMs)
      else FluidMotion.fadeOut(FluidAssistantFieldHaloProfile.FadeOutMs))
  }
  val cycle = remember(colours, mood) { colours.cycle(mood) }
  val blobs = cycle.mapIndexed { index, colour -> animateColorAsState(colour, FluidMotion.color(), label = "fieldHalo$index") }
  val ring = remember(colours) { listOf(colours.accent, colours.secondary, colours.tertiary) }
  return remember(clock, blend, ring) {
    FluidAssistantFieldHalo(clock, presence.asState(), motion.asState(), blobs, ring, blend, fieldActive)
  }
}

/**
 * L'orologio dell'alone del campo: come quello del campo di luce ([dev.antigravity.fluidengine.aiui.halo.rememberFluidHaloClock]),
 * ma il tempo avanza per il moto ([motion], 0..1): con la dissolvenza del moto rallenta fino a fermarsi, e fermo il ciclo
 * **si ferma** col tempo dov'è, senza chiedere fotogrammi. Col movimento ridotto l'ampiezza va al bersaglio in un colpo e
 * il tempo non avanza.
 */
@Composable
private fun rememberAssistantFieldClock(moving: Boolean, speed: Float, target: () -> Float, motion: () -> Float): FluidHaloClock {
  val clock = remember { FluidHaloClock() }
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val currentSpeed by rememberUpdatedState(speed)
  val currentTarget by rememberUpdatedState(target)
  val currentMotion by rememberUpdatedState(motion)
  LaunchedEffect(moving, reduced) {
    if (reduced) {
      snapshotFlow { currentTarget().coerceIn(0f, 1f) }.collect { clock.amplitude = it }
      return@LaunchedEffect
    }
    fun settled() = !moving && currentMotion() <= MotionFloor && abs(clock.amplitude - currentTarget()) <= AmplitudeSettled
    // Già fermo e al suo posto (a riposo, all'avvio): nessun fotogramma.
    if (settled()) {
      clock.amplitude = currentTarget().coerceIn(0f, 1f)
      return@LaunchedEffect
    }
    var last = 0L
    while (true) {
      withFrameNanos { now ->
        if (last != 0L) {
          val dt = ((now - last) / 1_000_000_000f).coerceAtMost(1f / 15f)
          clock.time += dt * currentSpeed * currentMotion().coerceIn(0f, 1f)
          clock.amplitude = haloApproach(clock.amplitude, currentTarget().coerceIn(0f, 1f), dt)
        }
        last = now
      }
      if (settled()) {
        clock.amplitude = currentTarget().coerceIn(0f, 1f)
        break
      }
    }
  }
  return clock
}

/** Sotto questa distanza dal bersaglio l'ampiezza ferma si posa e l'orologio smette di chiedere fotogrammi. */
private const val AmplitudeSettled = 0.004f

/** Sotto questo moto il giro di luce non si disegna e l'orologio fermo può smettere. */
private const val MotionFloor = 0.002f
