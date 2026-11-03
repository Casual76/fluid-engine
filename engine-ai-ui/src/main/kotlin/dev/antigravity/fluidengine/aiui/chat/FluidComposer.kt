package dev.antigravity.fluidengine.aiui.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.aiui.halo.FluidHaloClock
import dev.antigravity.fluidengine.aiui.halo.FluidHaloColours
import dev.antigravity.fluidengine.aiui.halo.FluidHaloFieldSpec
import dev.antigravity.fluidengine.aiui.halo.FluidHaloMood
import dev.antigravity.fluidengine.aiui.halo.HaloPresenceFloor
import dev.antigravity.fluidengine.aiui.halo.drawFluidHaloField
import dev.antigravity.fluidengine.aiui.halo.haloBlendForTheme
import dev.antigravity.fluidengine.aiui.halo.rememberFluidHaloClock
import dev.antigravity.fluidengine.aiui.halo.sampleCycle
import dev.antigravity.fluidengine.aiui.halo.wrap01
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantStrings
import dev.antigravity.fluidengine.aiui.strings.FluidChatStrings
import dev.antigravity.fluidengine.ui.fluid.ContinuousCornerShape
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidMotion
import dev.antigravity.fluidengine.ui.fluid.FluidRevealingText
import dev.antigravity.fluidengine.ui.fluid.GlassBackdropState
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.GlassTint
import dev.antigravity.fluidengine.ui.fluid.fluidPressable
import dev.antigravity.fluidengine.ui.fluid.glassControlSurface
import dev.antigravity.fluidengine.ui.fluid.glassSurface
import dev.antigravity.fluidengine.ui.fluid.rememberCombinedGlassBackdrop
import dev.antigravity.fluidengine.ui.fluid.rememberGlassBackdrop

/**
 * Cio' che il composer ricorda fra una ricomposizione e l'altra: il testo nel campo. Sta fuori dal
 * composer perche' anche la pagina ci scrive — un suggerimento toccato mentre l'assistente lavora
 * finisce nel campo, una domanda tolta dalla coda torna qui — e perche' un'anteprima possa costruire un
 * composer con una riga sola. Si salva con la pagina: una rotazione non perde quello che si stava scrivendo.
 *
 * Lo "stato di modifica" ("modifica e rinvia") di PampAI qui non c'e': e' dominio (un messaggio con
 * le sue versioni), e l'app lo tiene accanto a questo.
 */
@Stable
class FluidComposerState(text: String = "") {
  var text by mutableStateOf(text)

  /** Un testo che arriva da fuori si aggiunge a quello gia' scritto, non lo cancella. */
  fun fill(value: String) {
    text = if (text.isBlank()) value else text.trimEnd() + "\n\n" + value
  }

  /** La domanda e' partita (o e' in coda): campo vuoto. */
  fun sent() {
    text = ""
  }

  companion object {
    val Saver: Saver<FluidComposerState, String> = Saver(save = { it.text }, restore = { FluidComposerState(it) })
  }
}

@Composable
fun rememberFluidComposerState(): FluidComposerState = rememberSaveable(saver = FluidComposerState.Saver) { FluidComposerState() }

/** Il tasto a destra del composer: cosa fa dipende da cosa c'e' scritto e da cosa sta facendo l'assistente. */
enum class FluidComposerAction {
  /** Il microfono: nessun testo, niente in corso. */
  Voice,

  /** L'invio. */
  Send,

  /** Lo stop: l'assistente lavora e non c'e' niente di scritto. */
  Stop,

  /** Lo stop **e** l'invio che mette in coda: l'assistente lavora ma c'e' gia' la domanda dopo. */
  StopAndQueue,

  /** Smette di ascoltare. */
  StopListening,

  /** Zittisce la voce che sta leggendo la risposta. */
  Mute,
}

/**
 * Quale tasto mostra il composer. Pura, perche' e' un'albero di casi dove un caso dimenticato
 * (l'assistente che lavora con il campo pieno: lo stop non deve togliere l'invio) e' un tasto che
 * manca nel momento sbagliato; PampAI lo aveva dentro un `when` del composable.
 *
 * Ordine: sta ascoltando > lavora con qualcosa di scritto > lavora > legge ad alta voce e il campo e'
 * vuoto > campo vuoto (microfono) > invio.
 */
fun fluidComposerAction(hasInput: Boolean, busy: Boolean, listening: Boolean, speaking: Boolean, micAvailable: Boolean = true): FluidComposerAction = when {
  listening -> FluidComposerAction.StopListening
  busy && hasInput -> FluidComposerAction.StopAndQueue
  busy -> FluidComposerAction.Stop
  speaking && !hasInput -> FluidComposerAction.Mute
  !hasInput && micAvailable -> FluidComposerAction.Voice
  else -> FluidComposerAction.Send
}

/**
 * La riga della voce prende il posto del campo: la voce e' in corso (ascolto o trascrizione, secondo chi
 * chiama: vedi `FluidComposer(voiceActive)`) e l'app ne ha data una.
 */
internal fun fluidComposerShowsVoiceLine(voiceActive: Boolean, hasVoiceLine: Boolean): Boolean = voiceActive && hasVoiceLine

/**
 * L'alone del composer mentre ascolta: le macchie del campo di luce viaggiano sul perimetro della
 * capsula **dietro** al contenuto e un anello sottile con i colori del tema che girano sta **sopra**
 * il bordo ([fluidListeningGlow]). Lo costruisce [rememberFluidListeningGlow].
 */
@Stable
class FluidListeningGlow(
  val clock: FluidHaloClock,
  val presence: () -> Float,
  val colours: FluidHaloColours,
  val blend: BlendMode,
)

/**
 * L'alone di un composer che ascolta: l'orologio segue il microfono (letto nel loop di frame, non in
 * composizione: lo `StateFlow` a cinquanta hertz non ricompone nessuno) e la presenza sfuma dentro e
 * fuori. Mentre trascrive rallenta e si assesta: la voce e' finita, si aspetta.
 *
 * @param level il livello del microfono, 0..1, **come lambda**: lo legge solo il loop di frame.
 */
@Composable
fun rememberFluidListeningGlow(
  listening: Boolean,
  transcribing: Boolean,
  level: () -> Float,
  colours: FluidHaloColours = FluidHaloColours.fromTheme(),
  blend: BlendMode = haloBlendForTheme(),
): FluidListeningGlow {
  val voiceActive = listening || transcribing
  val clock = rememberFluidHaloClock(
    running = voiceActive,
    speed = if (transcribing) GlowTranscribingTempo else 1f,
    target = { if (transcribing) GlowTranscribingAmplitude else GlowFloorAmplitude + (1f - GlowFloorAmplitude) * level().coerceIn(0f, 1f) },
  )
  val presence = animateFloatAsState(
    targetValue = if (voiceActive) 1f else 0f,
    animationSpec = if (voiceActive) FluidMotion.fadeIn(GlowFadeInMs) else FluidMotion.fadeOut(GlowFadeOutMs),
    label = "composerGlow",
  )
  return remember(clock, colours, blend) { FluidListeningGlow(clock, { presence.value }, colours, blend) }
}

/**
 * La barra in fondo, nella forma delle chat che si usano: il campo sopra, e sotto la riga con
 * [leading] (il "+", dove l'app mette gli allegati e le modalita'), [chips] (il chip del modello) e il
 * tasto a destra (microfono, invio, stop: [fluidComposerAction]). Sopra la barra, quando ci sono, le
 * [pills]: gli allegati, il plugin scelto, la strada per le impostazioni quando manca una chiave.
 *
 * Il campo e' sempre usabile. Mentre l'assistente lavora si scrive e si manda lo stesso: la domanda
 * va in coda e parte quando ha finito (accanto all'invio resta lo stop, [FluidComposerAction.StopAndQueue]).
 *
 * **Il vetro**: la capsula rifrange [backdrop] (fondale e lista insieme) e **esporta** il proprio
 * materiale finito, cosi' i tasti tondi che ci stanno sopra piegano la capsula e non la pagina tre
 * strati piu' giu' — come fa la barra di `FluidScreen` con le sue azioni. Prima campionavano la
 * pagina direttamente, e ogni tasto era un buco nitido nel composer. [leading] riceve quel fondale
 * ([controlBackdrop]) per i suoi tasti.
 *
 * Quello che l'engine non sa e che resta all'app, negli slot: gli **allegati** (fotocamera, foto,
 * file: i launcher e le miniature sono suoi), i **menu** del "+" e del modello (si aprono con
 * `FluidGlassModalPortal`, ancorati a [onBoundsChange]), la **modifica** di un messaggio, i plugin.
 *
 * @param action il tasto a destra; di solito `fluidComposerAction(...)`.
 * @param onSend la domanda parte (o va in coda): l'app svuota il campo con `composer.sent()` se e'
 *   partita davvero, e lo lascia se non e' potuta partire (spenta, senza chiavi).
 * @param voiceLine al posto del campo mentre ascolta (di serie [FluidComposerVoiceLine] la fa chi la passa).
 * @param voiceActive la riga della voce resta al posto del campo (3.0.4). Di serie solo mentre ascolta
 *   (`action == StopListening`); durante la trascrizione il tasto e' lo stop dell'assistente e il campo
 *   tornava, mentre la riga ha la sua scritta «Trascrivo…»: passa `state.isVoiceActive()` per tenerla anche li'.
 * @param glow l'alone mentre ascolta, da [rememberFluidListeningGlow]; null non ne ha.
 * @param onBoundsChange il rettangolo della capsula in coordinate di radice: l'ancora dei pop-up.
 * @param resampleIntervalMillis ogni quanto, al massimo, la capsula rifa' la propria cattura; zero e' "quando serve". Sopra un fondale animato lo alza chi anima il fondale.
 */
@Composable
fun FluidComposer(
  value: String,
  onValueChange: (String) -> Unit,
  backdrop: GlassBackdropState,
  action: FluidComposerAction,
  onSend: () -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String = FluidChatStrings().composerPlaceholder,
  strings: FluidChatStrings = FluidChatStrings(),
  onStop: () -> Unit = {},
  onVoice: () -> Unit = {},
  onStopListening: () -> Unit = {},
  onMute: () -> Unit = {},
  pills: (@Composable () -> Unit)? = null,
  leading: (@Composable RowScope.(controlBackdrop: GlassBackdropState) -> Unit)? = null,
  chips: (@Composable RowScope.() -> Unit)? = null,
  voiceLine: (@Composable () -> Unit)? = null,
  glow: FluidListeningGlow? = null,
  focusRequester: FocusRequester? = null,
  maxLines: Int = 6,
  requestMicPermission: Boolean = true,
  onBoundsChange: ((Rect) -> Unit)? = null,
  resampleIntervalMillis: Long = 0L,
  shape: Shape = ComposerShape,
  voiceActive: Boolean = action == FluidComposerAction.StopListening,
) {
  val context = LocalContext.current
  // Il materiale della capsula, pubblicato per i tasti che ci stanno sopra: una lente appoggiata su
  // vetro smerigliato mostra la smerigliatura, non la pagina sotto.
  val composerGlass = rememberGlassBackdrop()
  val controlBackdrop = rememberCombinedGlassBackdrop(backdrop, composerGlass)
  val micGranted = remember { context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED }
  val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) onVoice() }
  val inputFocus = focusRequester ?: remember { FocusRequester() }

  Column(modifier) {
    pills?.let {
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
      ) { it() }
    }
    // La scatola esterna porta l'alone: le macchie stanno dietro al vetro e sbordano attorno, l'anello
    // sopra il bordo. Non clippa, e non deve: la luce vive fuori dalla capsula.
    Box(if (glow != null) Modifier.fluidListeningGlow(glow, shape) else Modifier) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .onGloballyPositioned { onBoundsChange?.invoke(it.boundsInRoot()) }
          .glassSurface(
            state = backdrop,
            tint = composerTint(),
            shape = shape,
            role = ComposerRole,
            exports = composerGlass,
            resampleIntervalMillis = resampleIntervalMillis,
          )
          .padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp),
      ) {
        if (fluidComposerShowsVoiceLine(voiceActive, voiceLine != null)) {
          voiceLine?.invoke()
        } else {
          FluidComposerField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            enabled = true,
            onSend = onSend,
            maxLines = maxLines,
            modifier = Modifier.fillMaxWidth().focusRequester(inputFocus),
          )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          leading?.invoke(this, controlBackdrop)
          if (leading != null) Spacer(Modifier.width(8.dp))
          chips?.invoke(this)
          Spacer(Modifier.weight(1f))
          when (action) {
            FluidComposerAction.StopListening -> FluidGlassRoundButton(Icons.Rounded.Stop, strings.voiceStop, controlBackdrop, tint = MaterialTheme.colorScheme.error, onClick = onStopListening)
            FluidComposerAction.StopAndQueue -> {
              FluidGlassRoundButton(Icons.Rounded.Stop, strings.stop, controlBackdrop, onClick = onStop)
              Spacer(Modifier.width(8.dp))
              FluidGlassRoundButton(Icons.Rounded.ArrowUpward, strings.sendQueued, controlBackdrop, tint = MaterialTheme.colorScheme.primary, onClick = onSend)
            }
            FluidComposerAction.Stop -> FluidGlassRoundButton(Icons.Rounded.Stop, strings.stop, controlBackdrop, onClick = onStop)
            FluidComposerAction.Mute -> FluidGlassRoundButton(Icons.AutoMirrored.Rounded.VolumeOff, strings.mute, controlBackdrop, onClick = onMute)
            FluidComposerAction.Voice -> FluidGlassRoundButton(
              Icons.Rounded.Mic,
              strings.voiceStart,
              controlBackdrop,
              onClick = { if (micGranted || !requestMicPermission) onVoice() else micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            )
            FluidComposerAction.Send -> FluidGlassRoundButton(Icons.Rounded.ArrowUpward, strings.send, controlBackdrop, tint = MaterialTheme.colorScheme.primary, onClick = onSend)
          }
        }
      }
    }
  }
}

/** Il raggio della capsula. Preesistente e non un token `FluidRadius`: cambiarlo e' una scelta visiva. */
val ComposerCorner: Dp = 26.dp

/** La sagoma della capsula, condivisa con chi deve nascerci dentro o ritornarci (i menu del "+"). */
val ComposerShape: Shape = ContinuousCornerShape(ComposerCorner)

/**
 * `Floating`, con riserva. E' il ruolo giusto per una capsula che galleggia sulla chat: la lente e'
 * la stessa del `Modal` (19/29 dp contro 20/28), cambia la sfocatura, 1.8 contro 3.5, e con la
 * pagina rifratta sotto la piu' leggera e' quella che lascia vedere che *c'e'* una pagina. Sull'emulatore
 * ogni build con `Floating` su una superficie di questa misura finiva in ANR nel disegno ("main thread
 * affamato", non un loop), e quelle con `Modal` no: sul telefono va provato, e il ripiego e'
 * `GlassRole.Modal`, una parola.
 */
private val ComposerRole = GlassRole.Floating

/**
 * Quanto del film dei `floatingTint` resta sulla capsula. Il 52% (chiaro) era pensato per una
 * pillola di navigazione con etichette di otto pixel sopra contenuto arbitrario; con la pagina
 * rifratta sotto e le parole grandi del campo, era un velo bianco che copriva il vetro. Quattro
 * quinti: 0.52 -> 0.42 in chiaro, 0.48 -> 0.38 in scuro.
 */
private const val ComposerFilmShare = 0.80f

@Composable
private fun composerTint(): GlassTint {
  val base = GlassDefaults.floatingTint()
  return base.copy(overlay = base.overlay.copy(alpha = base.overlay.alpha * ComposerFilmShare))
}

/**
 * Il campo del composer: le parole direttamente sul vetro. `FluidTextField` disegna un pozzo grigio
 * sotto il testo, giusto in un modulo e sbagliato qui: un rettangolo pieno dentro una capsula di vetro
 * e' quello che fa leggere il vetro come "una trasparenza". Nelle chat che si usano il testo sta sulla
 * superficie, e il bordo della capsula e' l'unico contorno.
 *
 * @param maxLines quante righe il campo puo' crescere prima di scorrere.
 */
@Composable
fun FluidComposerField(
  value: String,
  onValueChange: (String) -> Unit,
  placeholder: String,
  onSend: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  maxLines: Int = 6,
) {
  val scheme = MaterialTheme.colorScheme
  BasicTextField(
    value = value,
    onValueChange = onValueChange,
    enabled = enabled,
    maxLines = maxLines,
    textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
    cursorBrush = SolidColor(scheme.primary),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
    keyboardActions = KeyboardActions(onSend = { onSend() }),
    modifier = modifier.padding(horizontal = 10.dp, vertical = 12.dp),
    decorationBox = { inner ->
      Box {
        if (value.isEmpty()) {
          Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant.copy(alpha = 0.75f))
        }
        inner()
      }
    },
  )
}

/** Il chip del modello: una pillola bassa, che non ruba la riga al campo di testo. */
@Composable
fun FluidComposerChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .height(30.dp)
      .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f), FluidCapsuleShape)
      .fluidPressable(onClick = onClick, role = Role.Button, haptic = null)
      .padding(start = 12.dp, end = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.width(2.dp))
    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
  }
}

/**
 * Una pillola sopra la barra: un allegato, il plugin, la modalita'. Toccarla la toglie; con
 * [removable] falso e' un collegamento (le impostazioni) e al posto della X c'e' una freccia.
 */
@Composable
fun FluidComposerPill(
  icon: ImageVector,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  accent: Boolean = false,
  removable: Boolean = true,
  removeDescription: String = "Togli",
  trailingLink: ImageVector? = null,
) {
  val bg = if (accent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
  val fg = if (accent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
  Row(
    modifier = modifier
      .height(30.dp)
      .background(bg, FluidCapsuleShape)
      .fluidPressable(onClick = onClick, role = Role.Button, haptic = null)
      .padding(start = 10.dp, end = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
    Spacer(Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Spacer(Modifier.width(4.dp))
    if (removable) {
      Icon(Icons.Rounded.Close, contentDescription = removeDescription, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
    } else if (trailingLink != null) {
      Icon(trailingLink, contentDescription = null, tint = fg.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
    }
  }
}

/**
 * Un tasto tondo di vetro, come quelli della barra: il "+", il microfono, l'invio, lo stop. Lo stesso
 * disco per tutti, cambia solo l'icona e, quando serve, il colore. Il [backdrop] e' quello che il disco
 * rifrange: sul composer e' la pila pagina + capsula ([FluidComposer] lo passa a [leading]).
 */
@Composable
fun FluidGlassRoundButton(
  icon: ImageVector,
  description: String,
  backdrop: GlassBackdropState,
  modifier: Modifier = Modifier,
  tint: Color = MaterialTheme.colorScheme.onSurface,
  onClick: () -> Unit,
) {
  Box(
    modifier = modifier
      .size(40.dp)
      .glassControlSurface(backdrop = backdrop, shape = FluidCapsuleShape)
      .fluidPressable(onClick = onClick, role = Role.Button),
    contentAlignment = Alignment.Center,
  ) {
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
  }
}

/**
 * La riga della capsula mentre l'assistente ascolta: un punto che pulsa con la voce e, accanto, le
 * parole riconosciute che *arrivano*, non che si sostituiscono. Il riconoscitore riemette l'intera
 * frase due-cinque volte al secondo; qui la frase intera va a [FluidRevealingText], che tiene ferme le
 * parole gia' viste e sfuma dentro solo quelle nuove (e riscrive in fretta la coda quando il
 * riconoscitore cambia idea). Il transcript sta in una finestra alta una riga e mezza che segue la fine:
 * le righe vecchie scorrono su e svaniscono sotto una dissolvenza di otto dp. Un tocco sulla riga
 * annulla l'ascolto.
 *
 * Alta quanto il campo di testo a una riga (ventiquattro dp piu' dodici di padding sopra e sotto),
 * cosi' la capsula non cambia misura quando si passa dalla penna alla voce: a quarantaquattro, misurato
 * sul telefono, il bordo saltava di quattro dp.
 *
 * @param partial le parole riconosciute finora; null o vuoto mostra "Ti ascolto…".
 * @param amplitude l'ampiezza smussata della voce (0-1), letta solo nel disegno del punto.
 */
@Composable
fun FluidComposerVoiceLine(
  partial: String?,
  transcribing: Boolean,
  amplitude: () -> Float,
  onTap: () -> Unit,
  modifier: Modifier = Modifier,
  strings: FluidAssistantStrings = FluidAssistantStrings(),
) {
  val scheme = MaterialTheme.colorScheme
  val caption = transcribing || partial.isNullOrBlank()
  val text = when {
    transcribing -> strings.transcribing
    partial.isNullOrBlank() -> strings.listening
    else -> partial
  }
  // La didascalia e' piu' tenue delle parole vere; il passaggio e' una sfumatura, non uno scatto.
  val colour by animateColorAsState(if (caption) scheme.onSurfaceVariant else scheme.onSurface, FluidMotion.color(), label = "voiceLineColour")
  val dot = scheme.primary
  val scroll = rememberScrollState()
  // Si segue la fine dopo il layout, non alla composizione: `maxValue` cambia quando il testo nuovo e'
  // stato impaginato, e uno scroll lanciato nella composizione leggerebbe quello vecchio.
  LaunchedEffect(scroll) {
    snapshotFlow { scroll.maxValue }.collect { scroll.animateScrollTo(it, FluidMotion.snappy()) }
  }
  Row(
    modifier = modifier
      .height(VoiceLineHeight)
      .fluidPressable(onClick = onTap, role = Role.Button)
      .padding(start = 12.dp, end = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Spacer(
      Modifier.size(DotHaloRadius * 2).drawBehind {
        val a = amplitude().coerceIn(0f, 1f)
        drawCircle(dot.copy(alpha = 0.10f + 0.25f * a), radius = DotHaloRadius.toPx() * (0.6f + 0.4f * a))
        drawCircle(dot.copy(alpha = 0.7f + 0.3f * a), radius = (DotRadiusMin + DotRadiusVoice * a).toPx())
      },
    )
    Spacer(Modifier.width(10.dp))
    Box(
      Modifier
        .weight(1f)
        .height(VoiceLineHeight)
        // Il layer fuori schermo serve alla dissolvenza `DstIn`, e sta solo su questa scatola
        // piccola: senza layer il `DstIn` bucherebbe il vetro invece del testo.
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
          drawContent()
          drawRect(
            brush = Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black, startY = 0f, endY = VoiceFadeHeight.toPx()),
            blendMode = BlendMode.DstIn,
          )
        }
        .verticalScroll(scroll)
        // Almeno alta quanto la finestra: una riga sola sta al centro, due o piu' scorrono.
        .defaultMinSize(minHeight = VoiceLineHeight)
        .fillMaxWidth(),
      contentAlignment = Alignment.CenterStart,
    ) {
      FluidRevealingText(
        text = AnnotatedString(text),
        style = MaterialTheme.typography.bodyLarge,
        color = colour,
        animateFirst = true,
      )
    }
  }
}

/** L'altezza della riga in ascolto: la stessa del campo di testo a una riga. */
val VoiceLineHeight: Dp = 48.dp

/** Quanto e' alta la dissolvenza in cima al transcript, dove le righe vecchie scorrono via. */
private val VoiceFadeHeight = 8.dp

/** Il punto: il disco al centro va da tre a cinque dp di raggio con la voce, l'alone attorno arriva a otto. */
private val DotRadiusMin = 3.dp
private val DotRadiusVoice = 2.dp
private val DotHaloRadius = 8.dp

// ---- L'alone attorno alla capsula -------------------------------------------------------------

/**
 * La forma del campo di luce attorno alla capsula: sei macchie da ventotto dp. Sono poche e piccole
 * rispetto all'alone di una sessione (quattordici da sessanta) perche' qui il perimetro e' quello di
 * una capsula larga un telefono e alta due righe, non quello di uno schermo: piu' macchie si
 * toccherebbero e farebbero una cornice piena; piu' grandi sbordarebbero sulla chat. Niente filo sul
 * bordo dal pittore: l'anello lo disegna [fluidListeningGlow] sopra il vetro, con i colori che girano.
 */
internal val GlowSpec = FluidHaloFieldSpec(blobCount = 6, blobRadius = 28.dp, peakAlpha = 0.40f, edgeStroke = 0.dp)

/** Lo spessore dell'anello di luce sul bordo della capsula. */
private val RingWidth = 1.5.dp

/**
 * Quanti giri al secondo (di tempo dell'orologio) fanno i colori dell'anello: uno ogni otto secondi.
 * Piu' lento delle macchie no, perche' l'anello e' sottile e senza moto sembra un bordo stampato;
 * molto piu' veloce no, perche' diventa una giostra.
 */
private const val RingCyclesPerSec = 0.125f

/** Quante fermate ha il gradiente dell'anello: dodici bastano a non vedere gli spigoli fra un colore e l'altro. */
private const val RingStops = 12

/** L'alpha dell'anello: da un quarto a sette decimi con l'ampiezza della voce, per la presenza. */
private const val RingFloorAlpha = 0.25f
private const val RingVoiceAlpha = 0.45f

/** L'alone in ascolto: quanto ci mette a comparire e a sparire. In uscita un po' piu' lungo, cosi' la luce si posa invece di spegnersi. */
private const val GlowFadeInMs = 220
private const val GlowFadeOutMs = 260

/** L'ampiezza dell'alone nel silenzio: fra due sillabe non si spegne. Con la voce va a uno. */
private const val GlowFloorAmplitude = 0.25f

/** Mentre trascrive l'alone si assesta basso e va a un terzo del passo: la voce e' finita, si aspetta. */
private const val GlowTranscribingAmplitude = 0.30f
private const val GlowTranscribingTempo = 0.35f

/**
 * L'alone della capsula mentre ascolta, come modificatore da mettere su un contenitore *esterno* al
 * pannello di vetro, che non clippa: dentro la capsula le macchie le copre il vetro (il fondale
 * rifratto e il suo film), fuori sbordano di una ventina di dp e diventano l'alone; l'anello,
 * disegnato dopo `drawContent`, corre sul filo del bordo. Non si scala e non si sposta il vetro
 * (scalare un layer di vetro scala anche l'immagine campionata): la capsula resta ferma, e' la luce
 * a muoversi.
 *
 * `Path` e `PathMeasure` si rifanno solo quando cambia la misura (`drawWithCache`); tempo, ampiezza e
 * presenza si leggono dentro il draw, cosi' a sessanta fotogrammi al secondo si invalida solo questo
 * disegno e nessuno ricompone. Sotto la soglia di presenza non si disegna niente: a presenza zero un
 * `Plus` costa e non si vede.
 */
fun Modifier.fluidListeningGlow(glow: FluidListeningGlow, shape: Shape = ComposerShape): Modifier = drawWithCache {
  val outline = shape.createOutline(size, layoutDirection, this)
  val path = Path().apply { addOutline(outline) }
  val measure = PathMeasure().apply { setPath(path, forceClosed = true) }
  val spec = GlowSpec.copy(blendMode = glow.blend)
  val blobs = glow.colours.cycle(FluidHaloMood.LISTENING)
  val ring = listOf(glow.colours.accent, glow.colours.secondary, glow.colours.tertiary)
  // Le fermate dell'anello: una lista riempita a ogni fotogramma, non riallocata.
  val stops = MutableList(RingStops + 1) { Color.Transparent }
  val center = Offset(size.width / 2f, size.height / 2f)
  val stroke = Stroke(width = RingWidth.toPx())
  onDrawWithContent {
    val p = glow.presence()
    val lit = p > HaloPresenceFloor
    if (lit) drawFluidHaloField(measure, path, glow.clock.time, glow.clock.amplitude, p, blobs, spec)
    drawContent()
    if (lit) {
      // I colori girano sull'anello: fermate fisse, colore campionato dal ciclo con la fase che avanza
      // col tempo (la prima e l'ultima coincidono, cosi' il giro si chiude senza cucitura).
      val phase = wrap01(glow.clock.time * RingCyclesPerSec)
      for (k in 0..RingStops) stops[k] = sampleCycle(ring, k / RingStops.toFloat() - phase)
      drawPath(
        path = path,
        brush = Brush.sweepGradient(stops, center = center),
        alpha = (RingFloorAlpha + RingVoiceAlpha * glow.clock.amplitude) * p,
        style = stroke,
        blendMode = glow.blend,
      )
    }
  }
}

/** Vero se lo stato e' uno di quelli in cui il composer ascolta o trascrive: l'alone e la riga della voce. */
fun AssistantState.isVoiceActive(): Boolean = this is AssistantState.Listening || this == AssistantState.Transcribing
