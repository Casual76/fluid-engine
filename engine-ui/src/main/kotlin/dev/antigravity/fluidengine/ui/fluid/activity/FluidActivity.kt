package dev.antigravity.fluidengine.ui.fluid.activity

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.InfiniteAnimationPolicy
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidButton
import dev.antigravity.fluidengine.ui.fluid.FluidButtonSize
import dev.antigravity.fluidengine.ui.fluid.FluidButtonStyle
import dev.antigravity.fluidengine.ui.fluid.FluidCompactAdaptation
import dev.antigravity.fluidengine.ui.fluid.FluidGlassContentTransition
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModalPortal
import dev.antigravity.fluidengine.ui.fluid.FluidIcon
import dev.antigravity.fluidengine.ui.fluid.FluidMetrics
import dev.antigravity.fluidengine.ui.fluid.FluidPaneRole
import dev.antigravity.fluidengine.ui.fluid.LocalFluidPaneRole
import dev.antigravity.fluidengine.ui.fluid.FluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.fluidTransitionSource
import dev.antigravity.fluidengine.ui.fluid.rememberFluidGlassNamespace
import dev.antigravity.fluidengine.ui.fluid.smoothStep
import dev.antigravity.fluidengine.ui.fluid.GlassContentDefaults
import dev.antigravity.fluidengine.ui.fluid.LocalFluidMotionPolicy
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionProfile
import dev.antigravity.fluidengine.ui.fluid.icons.AutoAwesome
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.CloudDownload
import dev.antigravity.fluidengine.ui.fluid.icons.CloudOff
import dev.antigravity.fluidengine.ui.fluid.icons.CloudUpload
import dev.antigravity.fluidengine.ui.fluid.icons.Error
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Mic
import dev.antigravity.fluidengine.ui.fluid.icons.OpenInNew
import dev.antigravity.fluidengine.ui.fluid.icons.Pause
import dev.antigravity.fluidengine.ui.fluid.icons.PlayArrow
import dev.antigravity.fluidengine.ui.fluid.icons.Schedule
import dev.antigravity.fluidengine.ui.fluid.icons.Stop
import dev.antigravity.fluidengine.ui.fluid.icons.Sync
import dev.antigravity.fluidengine.ui.fluid.rememberFluidToolbarTransitionSource
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarButtonEntry
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarDeclaration
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarElementType
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarEntry
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarGroupEntry
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarKind
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarKinds
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPlacement
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarPriority
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarRole
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarScope
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTitleMode
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarMinimizeProgress
import dev.antigravity.fluidengine.ui.fluid.toolbar.rememberFluidToolbarRetracting
import dev.antigravity.fluidengine.ui.fluid.toolbar.LocalFluidToolbarMinimize
import dev.antigravity.fluidengine.ui.fluid.toolbar.LocalFluidToolbarRetraction
import dev.antigravity.fluidengine.ui.fluid.toolbar.FluidToolbarTypeSlot
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import dev.antigravity.fluidengine.ui.theme.LocalFluidRouteFront
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ================================================================================================================
// La pillola di attività (piano «iOS in Android», F6B.10)
//
// Un lavoro lungo (una sincronizzazione, un trasferimento, una registrazione, l'assistente che risponde, un timer) è
// **dell'app, non della pagina**: l'app lo pubblica una volta alla radice ([FluidActivityHostState.publish]) e ogni barra
// in alto lo mostra **al posto del titolo**, finché dura (scelta di Alessio del 21 novembre 2026: «il titolo è già
// dinamico in base alla pagina: questa sarebbe un'altra dinamicità persistente nel mentre che l'attività è in corso»).
// Il titolo esce sfocato e la pillola entra per lente (R4 B3, il contenuto sfocato di R7); alla fine il viaggio è inverso.
// Fra due pagine di un host la pillola resta ferma: è il titolo della pagina d'arrivo che aspetta.
//
// La pillola è un **elemento della barra dichiarata** (il punto unico di registrazione di F6B.3a): un tipo, non un ruolo
// nuovo, che la risoluzione posa al centro come un elemento `Principal`. Ha tre forme: compatta (anello o icona, una frase
// breve, tempo o percentuale), minima (il solo anello: quando la barra è piena) ed estesa (al tocco, una card
// che nasce dalla pillola col popover dalla sorgente di F5B.1).
//
// Non c'è un elemento così nelle app di Apple: la grammatica compatta, minima ed estesa viene dalle Live Activities e
// dall'isola dinamica (HIG Live Activities; F6B.0 §9, WWDC26 223), la comparsa per lente da R4 B3.
// ================================================================================================================

// ---------------------------------------------------------------------------------------------------------------
// Il modello pubblico
// ---------------------------------------------------------------------------------------------------------------

/**
 * Il genere di un'attività: l'icona di serie, l'**importanza** (fra più attività la pillola mostra la più importante) e il
 * segno con cui la pillola apre: l'anello del lavoro, con l'icona piccola dentro ([ring]), oppure l'icona da sola.
 *
 * Non è un `enum` di proposito (regola 3.11 del piano): i generi nuovi si aggiungono come valori di questa classe senza
 * rompere i `when` delle app, e [custom] ne fa uno su misura. Due generi con lo stesso [name] sono lo stesso.
 */
@Immutable
class FluidActivityKind private constructor(
  /** Il nome del genere: l'identità. */
  val name: String,
  /** L'icona di serie: dentro l'anello, o da sola. Un'attività può darne una sua ([FluidActivity.icon]). */
  val icon: ImageVector,
  /** Quanto conta: fra più attività la pillola mostra quella col numero più alto. */
  val importance: Int,
  /** Vero: la pillola apre con l'anello del lavoro (con l'icona piccola dentro). Falso: con l'icona da sola. */
  val ring: Boolean,
  /** Vero: il segno ha la tinta d'allarme (la registrazione: il microfono è acceso). */
  val alert: Boolean,
) {
  override fun equals(other: Any?): Boolean = other is FluidActivityKind && other.name == name
  override fun hashCode(): Int = name.hashCode()
  override fun toString(): String = "FluidActivityKind($name)"

  companion object {
    /** Una sincronizzazione: l'anello che gira, o che avanza se l'app dà l'avanzamento. La meno importante. */
    val Sync: FluidActivityKind = FluidActivityKind("sync", FluidIcons.Sync, FluidActivityProfile.SyncImportance, ring = true, alert = false)

    /** Un trasferimento (caricamento, scaricamento): l'anello con la percentuale. */
    val Transfer: FluidActivityKind =
      FluidActivityKind("transfer", FluidIcons.CloudDownload, FluidActivityProfile.TransferImportance, ring = true, alert = false)

    /** Un timer: l'anello che si svuota e il tempo che resta. */
    val Timer: FluidActivityKind = FluidActivityKind("timer", FluidIcons.Schedule, FluidActivityProfile.TimerImportance, ring = true, alert = false)

    /** L'assistente che lavora o risponde: la sua icona. */
    val Assistant: FluidActivityKind =
      FluidActivityKind("assistant", FluidIcons.AutoAwesome, FluidActivityProfile.AssistantImportance, ring = false, alert = false)

    /** Una registrazione: il microfono con la tinta d'allarme e il tempo trascorso. La più importante: deve sempre vedersi. */
    val Recording: FluidActivityKind =
      FluidActivityKind("recording", FluidIcons.Mic, FluidActivityProfile.RecordingImportance, ring = false, alert = true)

    /**
     * Senza rete (F8.20): la nuvola sbarrata, ferma, senza anello: non e' un lavoro che avanza, e' un lavoro che aspetta
     * («Offline · 3 modifiche in attesa»). Meno importante di tutto: se c'e' un lavoro vero, la pillola mostra quello.
     */
    val Offline: FluidActivityKind =
      FluidActivityKind("offline", FluidIcons.CloudOff, FluidActivityProfile.SyncImportance - 10, ring = false, alert = false)

    /** Un genere su misura. [importance] di serie è quello di [Sync]: non passa davanti a niente. */
    fun custom(
      name: String,
      icon: ImageVector,
      importance: Int = FluidActivityProfile.SyncImportance,
      ring: Boolean = true,
      alert: Boolean = false,
    ): FluidActivityKind = FluidActivityKind(name, icon, importance, ring, alert)
  }
}

/**
 * Il tempo che la pillola mostra al posto della percentuale, con cifre tabulari e una larghezza fissa. I millisecondi sono
 * quelli di `SystemClock.elapsedRealtime()`, l'orologio che non salta col cambio d'ora.
 */
@Immutable
class FluidActivityTime private constructor(internal val anchorMillis: Long, internal val mode: Int) {
  /** Quanti millisecondi mostra a [nowMillis]: mai meno di zero. */
  internal fun shownMillis(nowMillis: Long): Long = when (mode) {
    Since -> nowMillis - anchorMillis
    Until -> anchorMillis - nowMillis
    else -> anchorMillis
  }.coerceAtLeast(0L)

  /** Vero se il tempo scende (un timer): i secondi si arrotondano per eccesso, come in un conto alla rovescia. */
  internal val countsDown: Boolean get() = mode == Until

  /** Vero se il tempo cambia da sé: va riletto ogni secondo. */
  internal val ticks: Boolean get() = mode != Fixed

  override fun equals(other: Any?): Boolean = other is FluidActivityTime && other.anchorMillis == anchorMillis && other.mode == mode
  override fun hashCode(): Int = 31 * anchorMillis.hashCode() + mode

  companion object {
    private const val Since = 0
    private const val Until = 1
    private const val Fixed = 2

    /** Il tempo trascorso da [startedAtMillis]: una registrazione. */
    fun since(startedAtMillis: Long): FluidActivityTime = FluidActivityTime(startedAtMillis, Since)

    /** Il tempo che resta fino a [endsAtMillis]: un timer. */
    fun until(endsAtMillis: Long): FluidActivityTime = FluidActivityTime(endsAtMillis, Until)

    /** Un tempo fermo, [millis]: un'attività in pausa. */
    fun fixed(millis: Long): FluidActivityTime = FluidActivityTime(millis.coerceAtLeast(0L), Fixed)
  }
}

/**
 * Un comando della card estesa (pausa, annulla, interrompi…): una scritta, che è anche il nome per TalkBack, e cosa fa.
 * [destructive] lo colora come un'azione che non si torna indietro.
 */
@Immutable
class FluidActivityAction(
  val label: String,
  val onClick: () -> Unit,
  val icon: ImageVector? = null,
  val destructive: Boolean = false,
)

/**
 * Un lavoro lungo dell'app, da mostrare nella pillola di attività ([FluidActivityHostState.publish]).
 *
 * L'[id] è l'identità: pubblicare di nuovo lo stesso `id` **aggiorna** l'attività (la frase, la pausa, le azioni) senza
 * cambiarne il posto nella coda. L'avanzamento è [progress], letto dalla pillola quando disegna: meglio se legge uno
 * stato di Compose (si ridisegna da solo quando cambia); un valore che non lo è viene riletto dieci volte al secondo.
 *
 * @param kind il genere: l'icona, l'importanza, l'anello o l'icona.
 * @param progress l'avanzamento, da 0 a 1, o null per un lavoro senza misura (l'anello gira).
 * @param title la frase breve della pillola («Sincronizzazione»), e il titolo della card.
 * @param actions i comandi della card estesa.
 * @param detail la riga sotto il titolo nella card («3 di 10 messaggi»).
 * @param time il tempo da mostrare al posto della percentuale (una registrazione, un timer).
 * @param paused in pausa: l'anello si ferma e si attenua, il segno diventa la pausa.
 * @param icon un'icona al posto di quella del genere.
 * @param importance un'importanza al posto di quella del genere.
 * @param onOpen «Apri» nella card: porta dove il lavoro si vede per intero.
 */
@Immutable
data class FluidActivity(
  val id: String,
  val kind: FluidActivityKind,
  val progress: (() -> Float)? = null,
  val title: String,
  val actions: List<FluidActivityAction> = emptyList(),
  val detail: String? = null,
  val time: FluidActivityTime? = null,
  val paused: Boolean = false,
  val icon: ImageVector? = null,
  val importance: Int? = null,
  val onOpen: (() -> Unit)? = null,
) {
  companion object {
    /** Una sincronizzazione: l'anello che gira, o che avanza con [progress]. */
    fun sync(
      id: String = "sync",
      title: String? = null,
      progress: (() -> Float)? = null,
      detail: String? = null,
      onCancel: (() -> Unit)? = null,
      onOpen: (() -> Unit)? = null,
      labels: FluidActivityLabels = FluidActivityLabels(),
    ): FluidActivity = FluidActivity(
      id = id, kind = FluidActivityKind.Sync, progress = progress, title = title ?: labels.sync,
      actions = listOfNotNull(onCancel?.let { FluidActivityAction(labels.cancel, it, FluidIcons.Close, destructive = true) }),
      detail = detail, onOpen = onOpen,
    )

    /**
     * Senza rete (F8.20, `stato-rete-e-offline`): la pillola dice «Offline» e, nella card, quante modifiche aspettano
     * ([pending]); con [onSyncNow] la card offre «Sincronizza ora» (che parte quando torna la rete). Si toglie
     * ([FluidActivityHostState.remove]) o si chiude con l'esito della sincronizzazione al ritorno della rete
     * ([FluidActivityHostState.finish]). Per la capsula che galleggia sul contenuto c'e' `FluidConnectionCapsule`.
     */
    fun offline(
      pending: Int = 0,
      id: String = "sync",
      onSyncNow: (() -> Unit)? = null,
      onOpen: (() -> Unit)? = null,
      labels: FluidActivityLabels = FluidActivityLabels(),
    ): FluidActivity = FluidActivity(
      id = id, kind = FluidActivityKind.Offline, title = labels.offline,
      actions = listOfNotNull(onSyncNow?.let { FluidActivityAction(labels.syncNow, it, FluidIcons.Sync) }),
      detail = if (pending > 0) labels.pendingChanges(pending) else labels.offlineDetail,
      onOpen = onOpen,
    )

    /** Un trasferimento con la percentuale; [upload] sceglie la freccia in su. In pausa offre «Riprendi», se no «Pausa». */
    fun transfer(
      id: String,
      progress: () -> Float,
      title: String? = null,
      upload: Boolean = false,
      detail: String? = null,
      paused: Boolean = false,
      onPause: (() -> Unit)? = null,
      onResume: (() -> Unit)? = null,
      onCancel: (() -> Unit)? = null,
      onOpen: (() -> Unit)? = null,
      labels: FluidActivityLabels = FluidActivityLabels(),
    ): FluidActivity = FluidActivity(
      id = id, kind = FluidActivityKind.Transfer, progress = progress,
      title = title ?: if (upload) labels.upload else labels.download,
      actions = listOfNotNull(
        fluidActivityPauseAction(paused, onPause, onResume, labels),
        onCancel?.let { FluidActivityAction(labels.cancel, it, FluidIcons.Close, destructive = true) },
      ),
      detail = detail, paused = paused, icon = if (upload) FluidIcons.CloudUpload else FluidIcons.CloudDownload, onOpen = onOpen,
    )

    /**
     * Una registrazione col tempo trascorso da [startedAtMillis] (`SystemClock.elapsedRealtime()`). In pausa si passa
     * [pausedElapsedMillis], il tempo registrato fin lì: il tempo si ferma.
     */
    fun recording(
      startedAtMillis: Long,
      id: String = "recording",
      title: String? = null,
      pausedElapsedMillis: Long? = null,
      onPause: (() -> Unit)? = null,
      onResume: (() -> Unit)? = null,
      onStop: (() -> Unit)? = null,
      onOpen: (() -> Unit)? = null,
      labels: FluidActivityLabels = FluidActivityLabels(),
    ): FluidActivity = FluidActivity(
      id = id, kind = FluidActivityKind.Recording, title = title ?: labels.recording,
      actions = listOfNotNull(
        fluidActivityPauseAction(pausedElapsedMillis != null, onPause, onResume, labels),
        onStop?.let { FluidActivityAction(labels.stop, it, FluidIcons.Stop, destructive = true) },
      ),
      time = pausedElapsedMillis?.let(FluidActivityTime::fixed) ?: FluidActivityTime.since(startedAtMillis),
      paused = pausedElapsedMillis != null, onOpen = onOpen,
    )

    /** L'assistente che lavora o risponde; [detail] è ciò che sta facendo («Cerco nelle tue note»). */
    fun assistant(
      id: String = "assistant",
      title: String? = null,
      detail: String? = null,
      onStop: (() -> Unit)? = null,
      onOpen: (() -> Unit)? = null,
      labels: FluidActivityLabels = FluidActivityLabels(),
    ): FluidActivity = FluidActivity(
      id = id, kind = FluidActivityKind.Assistant, title = title ?: labels.assistant,
      actions = listOfNotNull(onStop?.let { FluidActivityAction(labels.stop, it, FluidIcons.Stop) }),
      detail = detail, onOpen = onOpen,
    )

    /**
     * Un timer che finisce a [endsAtMillis] (`SystemClock.elapsedRealtime()`); con [totalMillis] l'anello si svuota. In
     * pausa si passa [pausedRemainingMillis], il tempo che resta: il tempo si ferma.
     */
    fun timer(
      endsAtMillis: Long,
      id: String = "timer",
      title: String? = null,
      totalMillis: Long? = null,
      pausedRemainingMillis: Long? = null,
      onPause: (() -> Unit)? = null,
      onResume: (() -> Unit)? = null,
      onCancel: (() -> Unit)? = null,
      onOpen: (() -> Unit)? = null,
      labels: FluidActivityLabels = FluidActivityLabels(),
    ): FluidActivity {
      val total = totalMillis?.takeIf { it > 0L }
      val progress: (() -> Float)? = total?.let { all ->
        if (pausedRemainingMillis != null) {
          val fraction = (pausedRemainingMillis.toFloat() / all).coerceIn(0f, 1f)
          ({ fraction })
        } else {
          ({ ((endsAtMillis - SystemClock.elapsedRealtime()).coerceAtLeast(0L).toFloat() / all).coerceIn(0f, 1f) })
        }
      }
      return FluidActivity(
        id = id, kind = FluidActivityKind.Timer, progress = progress, title = title ?: labels.timer,
        actions = listOfNotNull(
          fluidActivityPauseAction(pausedRemainingMillis != null, onPause, onResume, labels),
          onCancel?.let { FluidActivityAction(labels.cancel, it, FluidIcons.Close, destructive = true) },
        ),
        time = pausedRemainingMillis?.let(FluidActivityTime::fixed) ?: FluidActivityTime.until(endsAtMillis),
        paused = pausedRemainingMillis != null, onOpen = onOpen,
      )
    }
  }
}

/** «Pausa» o «Riprendi», secondo lo stato, se l'app ne dà la callback. */
private fun fluidActivityPauseAction(
  paused: Boolean,
  onPause: (() -> Unit)?,
  onResume: (() -> Unit)?,
  labels: FluidActivityLabels,
): FluidActivityAction? =
  if (paused) onResume?.let { FluidActivityAction(labels.resume, it, FluidIcons.PlayArrow) }
  else onPause?.let { FluidActivityAction(labels.pause, it, FluidIcons.Pause) }

/**
 * Come finisce un'attività ([FluidActivityHostState.finish]).
 *
 * Non è un `enum` (regola 3.11): un esito nuovo non romperà i `when` delle app.
 */
@Immutable
class FluidActivityOutcome private constructor(internal val code: Int, val message: String?) {
  /** Vero per un esito riuscito. */
  val succeeded: Boolean get() = code == Success

  /** Vero per un esito non riuscito. */
  val failed: Boolean get() = code == Failure

  internal val showsInPill: Boolean get() = code == Success || code == Failure

  override fun equals(other: Any?): Boolean = other is FluidActivityOutcome && other.code == code && other.message == message
  override fun hashCode(): Int = 31 * code + (message?.hashCode() ?: 0)

  companion object {
    private const val Success = 0
    private const val Failure = 1
    private const val Handoff = 2

    /**
     * Riuscita: la pillola mostra la spunta e [message] (di serie il titolo), pulsa e poi esce per lente. Un'attività che
     * non era nella pillola (ce n'era una più importante) se ne va in silenzio: il contatore scende.
     */
    fun succeeded(message: String? = null): FluidActivityOutcome = FluidActivityOutcome(Success, message)

    /** Non riuscita: la pillola mostra il segno d'errore e [message], pulsa nella tinta d'errore e resta di più. Anche se non era lei in vista. */
    fun failed(message: String? = null): FluidActivityOutcome = FluidActivityOutcome(Failure, message)

    /**
     * **Passa il testimone**: l'esito lo dice qualcun altro (un tasto con esito di F6B.3, il contatore dell'annullo di
     * F6B.4), e la pillola esce subito, per lente, mentre lui lo mostra. Senza parole e senza vibrazione sue.
     */
    val HandedOff: FluidActivityOutcome = FluidActivityOutcome(Handoff, null)
  }
}

/**
 * Dove una barra mostra la pillola ([activity]). Non è un `enum` (regola 3.11).
 */
@Immutable
class FluidActivityPlacement private constructor(internal val name: String) {
  override fun toString(): String = "FluidActivityPlacement($name)"

  companion object {
    /** **Al posto del titolo in linea** (di serie, scelta di Alessio del 21 novembre 2026). Col titolo grande, questo resta sotto. */
    val Title: FluidActivityPlacement = FluidActivityPlacement("title")

    /** Fra i tasti di fine riga, col suo vetro: per una pagina che vuole tenere il titolo. */
    val Trailing: FluidActivityPlacement = FluidActivityPlacement("trailing")
  }
}

/** Le parole della pillola e della card, di serie in italiano ([english] per l'inglese). Si cambiano alla radice con [LocalFluidActivityLabels]. */
@Immutable
data class FluidActivityLabels(
  /** Breve di proposito: la frase della pillola sta in ≈120 dp (FluidActivityProfile.PhraseMax). */
  val sync: String = "Sincronizzo",
  val upload: String = "Caricamento",
  val download: String = "Scaricamento",
  val recording: String = "Registrazione",
  val assistant: String = "Assistente",
  val timer: String = "Timer",
  val pause: String = "Pausa",
  val resume: String = "Riprendi",
  val cancel: String = "Annulla",
  val stop: String = "Interrompi",
  val open: String = "Apri",
  val paused: String = "In pausa",
  val succeeded: String = "Fatto",
  val failed: String = "Non riuscito",
  /** Il nome dell'azione di TalkBack sulla pillola: apre la card. */
  val showDetails: String = "Mostra i dettagli",
  /** Il titolo della lista delle altre attività nella card. */
  val alsoRunning: String = "Anche in corso",
  /** Le altre attività in corso, per TalkBack. */
  val others: (Int) -> String = { count -> if (count == 1) "e un'altra attività" else "e altre $count attività" },
  val percent: (Int) -> String = { "$it%" },
  /** F8.20: la pillola senza rete ([FluidActivity.offline]). */
  val offline: String = "Offline",
  /** Le modifiche che aspettano la rete, nella card. */
  val pendingChanges: (Int) -> String = { n -> if (n == 1) "Una modifica in attesa" else "$n modifiche in attesa" },
  /** La card senza modifiche in attesa. */
  val offlineDetail: String = "Le modifiche restano qui",
  /** «Sincronizza ora» nella card. */
  val syncNow: String = "Sincronizza ora",
) {
  companion object {
    /** Le parole inglesi. */
    fun english(): FluidActivityLabels = FluidActivityLabels(
      sync = "Syncing", upload = "Uploading", download = "Downloading", recording = "Recording", assistant = "Assistant",
      timer = "Timer", pause = "Pause", resume = "Resume", cancel = "Cancel", stop = "Stop", open = "Open", paused = "Paused",
      succeeded = "Done", failed = "Failed", showDetails = "Show details", alsoRunning = "Also running",
      others = { count -> if (count == 1) "and 1 more activity" else "and $count more activities" },
      percent = { "$it%" },
      offline = "Offline",
      pendingChanges = { n -> if (n == 1) "1 change waiting" else "$n changes waiting" },
      offlineDetail = "Your changes stay here",
      syncNow = "Sync now",
    )
  }
}

/** Le parole della pillola, di serie in italiano. */
val LocalFluidActivityLabels = staticCompositionLocalOf { FluidActivityLabels() }

/** Le identità della pillola nella barra: per aprire qualcosa dalla pillola con `rememberFluidToolbarTransitionSource`. */
object FluidActivityIds {
  /** L'`id` dell'elemento della barra che è la pillola. */
  val Pill: Any = FluidActivityPillId
}

private object FluidActivityPillId {
  override fun toString(): String = "fluid:activity"
}

// ---------------------------------------------------------------------------------------------------------------
// I numeri
// ---------------------------------------------------------------------------------------------------------------

/**
 * I numeri della pillola. Apple non ha un elemento così dentro le app (F6B.0 §9): dove non c'è una fonte sono **a occhio,
 * 22 novembre 2026 (F6B.10)**, da guardare sul tablet in F6B.14. La comparsa e l'uscita sono quelle del piano con la
 * comparsa morbida (terzo giro: `fluidGlassSoftMaterialize`, le curve `GlassPlaneSoftAppearCurve` e
 * `GlassPlaneSoftDisappearCurve`, la scritta con R7), l'apertura della card è il popover dalla sorgente di F5B.1, lo
 * scambio del contenuto è R7: nessuno di questi tempi è qui.
 */
internal object FluidActivityProfile {
  // L'importanza dei generi: la registrazione davanti a tutto (il microfono acceso deve vedersi sempre, come il punto
  // arancione di iOS), poi l'assistente che parla adesso, poi i tempi che scadono, poi i lavori chiesti, poi quelli di
  // sottofondo. A occhio.
  const val SyncImportance: Int = 100
  const val TransferImportance: Int = 200
  const val TimerImportance: Int = 300
  const val AssistantImportance: Int = 400
  const val RecordingImportance: Int = 500

  /** Quanto resta in vista l'esito riuscito prima che la pillola esca per lente. A occhio: poco più del «Salva» riuscito di F6B.3a (1,5 s). */
  const val SuccessHoldMs: Long = 1_600L

  /** Quanto resta in vista l'esito non riuscito: il doppio, perché si legga. A occhio. */
  const val FailureHoldMs: Long = 3_200L

  /** Il margine dei due lati della pillola compatta: quello delle scritte nei tasti della barra, meno 4 dp. A occhio. */
  val SidePadding: Dp = 12.dp

  /** Il segno (l'anello o l'icona) della pillola compatta e minima: l'icona da 28 dp della barra, meno 6. A occhio. */
  val Glyph: Dp = 22.dp

  /** L'icona dentro l'anello, in frazione del segno. Come l'icona nell'anello del tasto con esito (0,7 di 26 su 34). */
  const val RingIconFraction: Float = 0.54f

  /** Il tratto dell'anello in dp. A occhio, sottile come quello del tasto con esito (≈2 dp). */
  val RingStroke: Dp = 2.dp

  /** Lo spazio fra il segno, la frase e il tempo. A occhio. */
  val Gap: Dp = 8.dp

  /** Lo spazio prima del contatore. A occhio. */
  val CounterGap: Dp = 6.dp

  /** Il margine del contatore («+2») dentro la sua pellicola. A occhio. */
  val CounterPadding: Dp = 6.dp

  /**
   * La frase più lunga: oltre, i puntini. Una pillola non è un titolo lungo. A occhio, dal conto della barra di un telefono
   * da 411 dp con l'indietro e due icone: resta posto per una pillola compatta di ≈214 dp, cioè una frase di 120 dp con la
   * percentuale. Oltre, la pillola diventa minima.
   */
  val PhraseMax: Dp = 120.dp

  /** Il contatore più alto scritto per intero; oltre si scrive «9+». */
  const val MaxCounter: Int = 9

  /** L'opacità della pellicola del contatore. Quella della selezione di un tasto della barra, più tenue. A occhio. */
  const val CounterFilmAlpha: Float = 0.14f

  /** Quando parte il battito dell'esito, in frazione dello scambio sfocato (R7): quando l'esito si legge, come la scossa di F6B.3a. */
  const val PulseDelayFraction: Float = FluidActionProfile.ShakeDelayFraction

  /** Quanto dura il battito dell'esito. A occhio. */
  const val PulseMs: Int = 560

  /** Quanto cresce il segno al colmo del battito. A occhio, il rimbalzo di un simbolo (WWDC24 10188, «Bounce»). */
  const val PulseScale: Float = 1.22f

  /** Quanto si accende la pellicola dentro il vetro al colmo del battito. A occhio. */
  const val PulseFilmAlpha: Float = 0.16f

  /** Ogni quanto si rilegge un avanzamento che non è uno stato di Compose (dieci volte al secondo). A occhio. */
  const val ProgressPollMs: Long = 100L

  /** Lo spicchio dell'anello che gira e il suo giro: quelli del tasto con esito (`UIActivityIndicatorView`). */
  const val SpinnerSweep: Float = FluidActionProfile.SpinnerSweep
  const val SpinnerTurnMs: Int = FluidActionProfile.SpinnerTurnMs

  /** La pista dell'anello e l'anello in pausa: quelli del tasto con esito. */
  const val RingTrackAlpha: Float = FluidActionProfile.RingTrackAlpha
  const val PausedRingAlpha: Float = FluidActionProfile.ReducedRingAlpha

  /**
   * L'anellino a barra ritirata (scelta di Alessio, 22 novembre 2026): il segno della pillola rimpicciolito a 16 dp, poco
   * più delle icone della barra di stato. A occhio.
   */
  val RingSmall: Dp = 16.dp

  /** L'area che si tocca intorno all'anellino: dentro la striscia della barra di stato (24–40 dp). A occhio. */
  val RingTouch: Dp = 36.dp

  /** Quanto l'anellino sta lontano dal foro della fotocamera, e dal bordo in alto se la barra di stato non c'è. A occhio. */
  val RingMargin: Dp = 6.dp

  /** Il segno nella card estesa. A occhio. */
  val CardGlyph: Dp = 30.dp

  /** Il segno nelle righe delle altre attività della card. A occhio. */
  val CardRowGlyph: Dp = 18.dp

  /** La card: la larghezza più piccola e quella più grande. A occhio, dentro i 360 dp del popover con sorgente (F5B.1). */
  val CardMinWidth: Dp = 264.dp
  val CardMaxWidth: Dp = 340.dp
}

// ---------------------------------------------------------------------------------------------------------------
// La coda (pura)
// ---------------------------------------------------------------------------------------------------------------

/** Un'attività nella coda: l'ordine d'arrivo ([seq]) e, se è finita, l'esito e quando. */
@Immutable
internal data class FluidActivityEntry(
  val activity: FluidActivity,
  val seq: Long,
  val outcome: FluidActivityOutcome? = null,
  val endedAtMillis: Long = 0L,
) {
  val running: Boolean get() = outcome == null
  val importance: Int get() = activity.importance ?: activity.kind.importance
}

/**
 * Ciò che la pillola mostra adesso: l'attività, il suo esito se è finita, e le altre in corso ([rest], dalla più
 * importante: il contatore è il loro numero).
 */
@Immutable
internal data class FluidActivityDisplay(
  val activity: FluidActivity,
  val outcome: FluidActivityOutcome? = null,
  val rest: List<FluidActivity> = emptyList(),
) {
  val others: Int get() = rest.size
}

/**
 * La coda delle attività, **pura e immutabile**: ogni cambio dà una coda nuova, e lo stato dell'host la tiene in uno stato
 * di Compose. Così due attività che iniziano, finiscono o cambiano insieme (nello stesso fotogramma) danno un solo
 * cambio della pillola, e le regole si provano senza Compose (`FluidActivityQueueTest`).
 *
 * **Chi prende la pillola** ([shown]):
 * 1. l'attività della card aperta ([pinned]): finché la si guarda non cambia sotto il dito;
 * 2. un esito in vista, il più recente: la pillola lo mostra per il suo tempo, poi passa il testimone;
 * 3. la più importante ([FluidActivityEntry.importance]); a parità, **la prima arrivata**: una nuova di pari importanza
 *    non ruba la pillola a quella che c'è (la pillola non salta da una all'altra).
 *
 * **Chi finisce**: un esito riuscito si vede solo se l'attività era nella pillola; se no se ne va in silenzio. Un esito
 * non riuscito si vede sempre. [FluidActivityOutcome.HandedOff] e [remove] la tolgono subito.
 */
@Immutable
internal class FluidActivityQueue(
  val entries: List<FluidActivityEntry> = emptyList(),
  private val nextSeq: Long = 0L,
  /** L'attività della card aperta, o null. */
  val pinned: String? = null,
) {
  fun entry(id: String): FluidActivityEntry? = entries.firstOrNull { it.activity.id == id }

  /** Aggiunge l'attività in fondo alla coda, o la aggiorna al suo posto (e torna in corso, se era finita). */
  fun publish(activity: FluidActivity): FluidActivityQueue {
    val old = entry(activity.id)
    return if (old == null) {
      FluidActivityQueue(entries + FluidActivityEntry(activity, nextSeq), nextSeq + 1, pinned)
    } else {
      FluidActivityQueue(entries.map { if (it === old) FluidActivityEntry(activity, old.seq) else it }, nextSeq, pinned)
    }
  }

  /** L'attività [id] finisce con [outcome] a [nowMillis]. Vedi le regole sopra. */
  fun finish(id: String, outcome: FluidActivityOutcome, nowMillis: Long): FluidActivityQueue {
    val old = entry(id) ?: return this
    val visible = shown?.activity?.id == id
    val keeps = outcome.failed || outcome.succeeded && visible
    if (!keeps) return remove(id)
    return FluidActivityQueue(
      entries.map { if (it === old) it.copy(outcome = outcome, endedAtMillis = nowMillis) else it }, nextSeq, pinned,
    )
  }

  /** Toglie l'attività [id] subito, senza esito; se era nella card aperta, la card si chiude. */
  fun remove(id: String): FluidActivityQueue {
    if (entry(id) == null) return this
    return FluidActivityQueue(entries.filterNot { it.activity.id == id }, nextSeq, pinned?.takeIf { it != id })
  }

  /** Toglie gli esiti il cui tempo è passato a [nowMillis], tranne quello della card aperta. */
  fun expire(nowMillis: Long): FluidActivityQueue {
    val kept = entries.filterNot { it.expiredAt(nowMillis) }
    return if (kept.size == entries.size) this else FluidActivityQueue(kept, nextSeq, pinned)
  }

  /** Quando scade il primo esito in vista, o null. */
  fun nextDeadline(): Long? = entries.mapNotNull { it.deadline() }.minOrNull()

  /**
   * Apre ([id]) o chiude (null) la card. La card tiene ferma la sua attività; alla chiusura il suo esito, se c'è, riparte
   * col suo tempo intero da [nowMillis]: un esito guardato nella card si vede ancora, poco, nella pillola.
   */
  fun pin(id: String?, nowMillis: Long): FluidActivityQueue {
    if (id == pinned) return this
    if (id != null && entry(id) == null) return this
    val released = pinned
    val updated = if (released == null) entries else entries.map {
      if (it.activity.id == released && it.outcome != null) it.copy(endedAtMillis = nowMillis) else it
    }
    return FluidActivityQueue(updated, nextSeq, id)
  }

  /** Chi ha la pillola: vedi le regole della classe. */
  val shown: FluidActivityEntry?
    get() {
      pinned?.let { id -> entry(id)?.let { return it } }
      entries.filter { it.outcome != null }.maxByOrNull { it.endedAtMillis }?.let { return it }
      return entries.sortedWith(fluidActivityOrder).firstOrNull()
    }

  /** Ciò che la pillola mostra, o null se non c'è niente in corso. */
  val display: FluidActivityDisplay?
    get() {
      val top = shown ?: return null
      val rest = entries.filter { it.running && it !== top }.sortedWith(fluidActivityOrder).map { it.activity }
      return FluidActivityDisplay(top.activity, top.outcome, rest)
    }

  private fun FluidActivityEntry.deadline(): Long? {
    val outcome = outcome ?: return null
    if (activity.id == pinned) return null
    return endedAtMillis + if (outcome.failed) FluidActivityProfile.FailureHoldMs else FluidActivityProfile.SuccessHoldMs
  }

  private fun FluidActivityEntry.expiredAt(nowMillis: Long): Boolean = deadline()?.let { nowMillis >= it } == true

  override fun equals(other: Any?): Boolean =
    other is FluidActivityQueue && other.entries == entries && other.nextSeq == nextSeq && other.pinned == pinned

  override fun hashCode(): Int = 31 * (31 * entries.hashCode() + nextSeq.hashCode()) + (pinned?.hashCode() ?: 0)
}

/** La più importante prima; a parità la prima arrivata. */
private val fluidActivityOrder: Comparator<FluidActivityEntry> =
  compareByDescending<FluidActivityEntry> { it.importance }.thenBy { it.seq }

// ---------------------------------------------------------------------------------------------------------------
// L'host alla radice
// ---------------------------------------------------------------------------------------------------------------

/**
 * Le attività dell'app, **una pillola per l'app** (scelta di Alessio del 21 novembre 2026): si pubblica qui, e ogni barra
 * in alto la mostra al posto del titolo. Si tiene alla radice con [FluidActivityHost], come le notifiche; una funzione
 * dell'app la trova con [LocalFluidActivityHostState].
 *
 * ```
 * val activities = rememberFluidActivityHostState()
 * FluidActivityHost(activities) { App() }
 * // in un punto qualsiasi dell'app:
 * activities.publish(FluidActivity.sync(progress = { sync.progress }))
 * activities.finish("sync")                                   // la spunta, un battito, e la pillola esce
 * activities.finish("sync", FluidActivityOutcome.failed("Rete assente"))
 * ```
 *
 * Va usato sul thread dell'interfaccia: due cambi nello stesso fotogramma sono un cambio solo della pillola.
 */
@Stable
class FluidActivityHostState internal constructor(private val clock: () -> Long) {
  /** Uno stato nuovo, con l'orologio di sistema. */
  constructor() : this(SystemClock::elapsedRealtime)

  internal var queue: FluidActivityQueue by mutableStateOf(FluidActivityQueue())
    private set

  /** La sorgente della card aperta: la pillola della pagina in cui la si è toccata. */
  internal var expandedSource: FluidTransitionSource? by mutableStateOf(null)
    private set

  /** Cresce quando un tempo cambia classe di larghezza (passa l'ora): le barre rimisurano la pillola. */
  internal var remeasure: Int by mutableIntStateOf(0)
    private set

  /**
   * Quale barra mostra la pillola quando **due barre in alto sono in vista insieme** (la lista e il dettaglio affiancati
   * su un tablet, [FluidPaneRole]). La pillola è una per l'app: se ne vede una sola. Di serie sta nella barra del
   * dettaglio ([FluidPaneRole.Detail]); con [FluidPaneRole.List] va in quella della lista. Con una barra sola in vista
   * (il telefono, o il tablet con un pannello) la pillola sta in quella, qualunque sia il suo pannello; se la barra
   * scelta l'ha nascosta (`activity(hidden = true)`) la pillola va nell'altra. La regola è [fluidActivityOwnerPane].
   */
  var pane: FluidPaneRole by mutableStateOf(FluidPaneRole.Detail)

  /** Le barre in alto che accolgono la pillola adesso, col loro pannello: le registra [fluidToolbarWithActivity]. */
  internal val seats = mutableStateListOf<FluidActivitySeat>()

  /** L'anellino della barra che si ritira (F4A.4): vedi [FluidActivityRetractedRing]. */
  internal val ring = FluidActivityRing()

  /**
   * Se la barra del pannello [role] mostra la pillola. [self] è la barra che lo chiede, se non è ancora registrata: alla
   * prima composizione conta come in vista, così una barra che apre non aspetta un fotogramma per avere la pillola.
   */
  internal fun showsIn(role: FluidPaneRole?, self: FluidActivitySeat? = null): Boolean {
    val inView = seats.filter { it.inView }.map { it.role }.toMutableList()
    if (self != null && self !in seats) inView += self.role
    return fluidActivityShowsIn(role, fluidActivityOwnerPane(inView, pane))
  }

  internal fun now(): Long = clock()

  /**
   * Chi segue le attività fuori dall'app (F8.19, `mirrorOutside`): riceve ogni pubblicazione, fine e rimozione così come
   * arrivano, anche quella di un'attività che la pillola non mostrava. Null: nessuno.
   */
  internal var observers: List<FluidActivityObserver> = emptyList()

  /** Pubblica [activity], o la aggiorna se c'è già un'attività con lo stesso `id`. */
  fun publish(activity: FluidActivity) {
    queue = queue.publish(activity)
    observers.forEach { it.published(activity) }
  }

  /** Finisce l'attività [id] con [outcome]: di serie riuscita. Vedi [FluidActivityOutcome]. */
  fun finish(id: String, outcome: FluidActivityOutcome = FluidActivityOutcome.succeeded()) {
    queue = if (outcome.showsInPill) queue.finish(id, outcome, clock()) else queue.remove(id)
    observers.forEach { it.finished(id, outcome) }
  }

  /** Toglie l'attività [id] subito, senza esito (annullata). */
  fun remove(id: String) {
    queue = queue.remove(id)
    observers.forEach { it.removed(id) }
  }

  /** Le attività in corso, dalla più importante. */
  val activities: List<FluidActivity>
    get() = queue.entries.filter { it.running }.sortedWith(fluidActivityOrder).map { it.activity }

  /** L'attività che la pillola mostra adesso, o null. */
  val current: FluidActivity? get() = queue.display?.activity

  /** Vero se la card estesa è aperta. */
  val expanded: Boolean get() = queue.pinned != null

  /**
   * Chiude la card estesa: rientra nella pillola. La sorgente resta quella dell'apertura: il ritorno la legge (F5B.1).
   */
  fun collapse() {
    queue = queue.pin(null, clock())
  }

  /** Apre la card dell'attività in vista, dalla pillola [source] (quella toccata); la chiude se è già aperta. */
  internal fun toggle(source: FluidTransitionSource?) {
    if (expanded) {
      collapse()
      return
    }
    val id = queue.display?.activity?.id ?: return
    expandedSource = source
    queue = queue.pin(id, clock())
  }

  internal fun expire() {
    queue = queue.expire(clock())
  }

  internal fun remeasured() {
    remeasure++
  }
}

/**
 * Il pannello la cui barra mostra la pillola, dati i pannelli delle barre in vista che la accolgono ([inView]; null per
 * una barra fuori da un pannello) e quello scelto dall'app ([preferred], [FluidActivityHostState.pane]):
 *
 * - nessuna barra in vista dentro un pannello: null, cioè nessuna regola (la barra che c'è mostra la pillola);
 * - il pannello scelto, se una sua barra è in vista;
 * - altrimenti il primo in vista fra dettaglio, lista e lato.
 *
 * Pura.
 */
internal fun fluidActivityOwnerPane(inView: Collection<FluidPaneRole?>, preferred: FluidPaneRole): FluidPaneRole? {
  val roles = inView.filterNotNullTo(HashSet())
  if (roles.isEmpty()) return null
  if (preferred in roles) return preferred
  return FluidActivityPaneOrder.first { it in roles }
}

private val FluidActivityPaneOrder = listOf(FluidPaneRole.Detail, FluidPaneRole.List, FluidPaneRole.Side)

/**
 * Se la barra del pannello [role] mostra la pillola, quando la regola dà [owner] ([fluidActivityOwnerPane]): una barra
 * fuori da un pannello sempre, una dentro un pannello solo se è quello. Pura.
 */
internal fun fluidActivityShowsIn(role: FluidPaneRole?, owner: FluidPaneRole?): Boolean =
  role == null || owner == null || role == owner

/** Una barra in alto che accoglie la pillola: il suo pannello e se è in vista (davanti, `LocalFluidRouteFront`). */
@Stable
internal class FluidActivitySeat(val role: FluidPaneRole?) {
  var inView: Boolean by mutableStateOf(true)
}

/**
 * L'anellino (F6B.10, scelta di Alessio del 22 novembre 2026): mentre la barra in alto si ritira scorrendo (F4A.4), della
 * pillola resta solo l'anello, piccolo, in alto. Qui stanno il centro dell'anello della pillola nella barra (nella radice,
 * in px: lo scrive la pillola dopo il layout) e il ritiro della barra che mostra la pillola, finché l'anellino c'è. Si
 * leggono solo in disegno.
 */
@Stable
internal class FluidActivityRing {
  /** L'anello della pillola a riposo: dove l'anellino parte e dove torna se la pillola è compatta. */
  var glyphCenter: Offset by mutableStateOf(Offset.Unspecified)

  /** Il centro della pillola a riposo: dove sta il suo anello nella forma minima, che la barra ritirata prende. */
  var faceCenter: Offset by mutableStateOf(Offset.Unspecified)

  /**
   * Il ritiro della barra che ha l'anellino adesso: quella della schermata davanti (due schermate impilate hanno due
   * barre, e due ritiri). La pillola di un'altra barra non gli cede l'anello e non gli scrive dove sta.
   */
  var retraction: FluidToolbarMinimizeProgress? by mutableStateOf(null)

  /**
   * Vero quando l'anellino ha preso il posto dell'anello della pillola della barra che si ritira con [own]: la pillola
   * allora non lo disegna.
   */
  fun covers(own: FluidToolbarMinimizeProgress?): Boolean {
    val minimize = retraction ?: return false
    return minimize === own && !minimize.reducedMotion && minimize.progress > 0f
  }

  /**
   * Se la pillola della barra che si ritira con [own] scrive dove sta: solo a barra ferma (durante il ritiro la barra si
   * sposta col suo livello), e solo se l'anellino è il suo o non c'è.
   */
  fun settled(own: FluidToolbarMinimizeProgress?): Boolean {
    val minimize = retraction ?: return true
    return minimize === own && minimize.progress <= 0f
  }
}

/** Uno stato delle attività ricordato nella composizione. Per tenerlo in un ViewModel, `FluidActivityHostState()`. */
@Composable
fun rememberFluidActivityHostState(): FluidActivityHostState = remember { FluidActivityHostState() }

private val LocalFluidActivityHost = staticCompositionLocalOf<FluidActivityHostState?> { null }

/** Lo stato delle attività dell'app, dato da [FluidActivityHost]; null fuori. */
val LocalFluidActivityHostState: CompositionLocal<FluidActivityHostState?> get() = LocalFluidActivityHost

/**
 * L'host delle attività, **alla radice**: dà [state] a tutta l'app ([content]), tiene il tempo degli esiti, suona
 * l'esito e ospita la card estesa (un popover dalla pillola: va dentro l'host dei modali, `LocalFluidGlassModalHostState`).
 * Ogni barra in alto dichiarata (`FluidScreen(toolbar = …)`, `FluidPageChrome`, la barra dell'host delle pagine in ogni
 * pagina) mostra la pillola da sola; una pagina che non la vuole scrive `activity(hidden = true)` nella sua barra.
 */
@Composable
fun FluidActivityHost(state: FluidActivityHostState, content: @Composable () -> Unit) {
  LaunchedEffect(state) {
    // Il tempo degli esiti. Un cambio della coda riparte da capo con la scadenza nuova.
    snapshotFlow { state.queue.nextDeadline() }.collectLatest { deadline ->
      var next = deadline
      while (next != null) {
        val wait = next - state.now()
        if (wait > 0L) delay(wait)
        state.expire()
        next = state.queue.nextDeadline()
      }
    }
  }
  // L'esito si sente una volta, dall'host, quando la pillola lo mostra.
  val haptics = LocalFluidHaptics.current
  val shownOutcome = state.queue.display?.let { display -> display.outcome?.let { display.activity.id to it } }
  LaunchedEffect(shownOutcome) {
    val outcome = shownOutcome?.second ?: return@LaunchedEffect
    haptics.play(if (outcome.failed) FluidHapticEvent.Error else FluidHapticEvent.Success)
  }
  CompositionLocalProvider(LocalFluidActivityHost provides state) {
    content()
    FluidActivityCard(state)
  }
}

// ---------------------------------------------------------------------------------------------------------------
// La dichiarazione nella barra
// ---------------------------------------------------------------------------------------------------------------

/** Ciò che una pagina chiede alla pillola con [activity]. */
@Immutable
internal data class FluidActivityRequest(val hidden: Boolean, val placement: FluidActivityPlacement)

/**
 * Il portatore della richiesta nella dichiarazione: un gruppo vuoto, inerte per il modello (niente vetri, niente spazio,
 * niente tasti), come il portatore della selezione di F6B.1. Viaggia con la dichiarazione fino alla schermata e all'host.
 */
private class FluidActivityCarrier(val request: FluidActivityRequest) {
  override fun equals(other: Any?): Boolean = other is FluidActivityCarrier
  override fun hashCode(): Int = 0xAC71
  override fun toString(): String = "fluid:activity-request"
}

private fun FluidToolbarEntry.activityCarrier(): FluidActivityCarrier? = when (this) {
  is FluidToolbarGroupEntry -> id as? FluidActivityCarrier ?: children.firstNotNullOfOrNull { it.activityCarrier() }
  else -> null
}

/** Ciò che questa barra chiede alla pillola, o null (di serie: la pillola al posto del titolo). */
internal val FluidToolbarDeclaration.activityRequest: FluidActivityRequest?
  get() = entries.firstNotNullOfOrNull { it.activityCarrier()?.request }

/**
 * La pillola di attività in questa barra (F6B.10). Di serie ogni barra in alto la mostra **al posto del titolo**, senza
 * scrivere niente: questa chiamata serve a una pagina che non la vuole ([hidden], un lettore, un foglio che chiede
 * attenzione) o che la vuole fra i tasti ([placement] [FluidActivityPlacement.Trailing]).
 */
fun FluidToolbarScope.activity(hidden: Boolean = false, placement: FluidActivityPlacement = FluidActivityPlacement.Title) {
  entries.removeAll { it.activityCarrier() != null }
  entries += FluidToolbarGroupEntry(
    id = FluidActivityCarrier(FluidActivityRequest(hidden, placement)),
    placement = FluidToolbarPlacement.Automatic,
    priority = FluidToolbarPriority.Automatic,
    overflow = false,
    sharedBackground = true,
    children = emptyList(),
  )
}

/**
 * La dichiarazione [declaration] con la pillola dentro, se l'app ha un'attività in corso e la pagina non l'ha nascosta;
 * [declaration] stessa altrimenti (e se la pillola c'è già: si può chiamare due volte). È **il punto in cui la pillola
 * entra in una barra**: la schermata, `FluidPageChrome` e l'host delle pagine lo chiamano dove costruiscono la barra.
 *
 * La pillola è un elemento ordinario della barra (il punto unico di registrazione di F6B.3a): un `Text` col suo vetro,
 * `Principal` (al centro, al posto del titolo: il titolo cede il posto come al conteggio della selezione) o in fondo
 * alla riga, d'importanza alta. Se la pagina ha già qualcosa al centro (il conteggio di una selezione), o se
 * [minimalOnly] (una pagina con le `actions` di sempre, i cui tasti la barra non misura), la pillola è minima.
 */
@Composable
internal fun fluidToolbarWithActivity(
  declaration: FluidToolbarDeclaration?,
  minimalOnly: Boolean = false,
): FluidToolbarDeclaration? {
  val host = LocalFluidActivityHost.current ?: return declaration
  if (declaration == null || !fluidActivityAccepts(declaration)) return declaration
  // Due barre in vista: la pillola in una sola (FluidActivityHostState.pane). La barra si registra col suo pannello;
  // se è in vista lo porta un effetto, così la composizione non legge lo stato «davanti» mentre lo si scrive.
  val role = LocalFluidPaneRole.current
  val front = LocalFluidRouteFront.current
  val seat = remember(host, role) { FluidActivitySeat(role) }
  DisposableEffect(host, seat) {
    host.seats += seat
    onDispose { host.seats -= seat }
  }
  LaunchedEffect(seat, front) { snapshotFlow { front.value }.collect { seat.inView = it } }
  if (!host.showsIn(role, seat)) return declaration
  val display = host.queue.display ?: return declaration
  val labels = LocalFluidActivityLabels.current
  val source = rememberFluidToolbarTransitionSource(FluidActivityIds.Pill)
  val styles = fluidActivityPillStyles()
  // Rilegge quando un tempo passa l'ora (cambia la classe di larghezza).
  val revision = host.remeasure
  val now = host.now()
  val readout = remember(display.activity, revision) { fluidActivityReadout(display.activity, now) }
  val widthDp = fluidActivityPillWidthDp(display, styles, now)
  return fluidToolbarWithActivityOf(declaration, display, labels, widthDp, readout, minimalOnly) { host.toggle(source) }
}

/** Se la barra [declaration] accoglie la pillola: non ce l'ha già e la pagina non l'ha nascosta. Pura. */
internal fun fluidActivityAccepts(declaration: FluidToolbarDeclaration): Boolean =
  FluidActivityIds.Pill !in declaration.buttons && declaration.activityRequest?.hidden != true

/**
 * La barra [declaration] con la pillola di [display] dentro: la parte pura di [fluidToolbarWithActivity]. [compactDp] è
 * la larghezza della forma compatta, [readout] la classe della parte del tempo, [onTap] apre la card.
 */
internal fun fluidToolbarWithActivityOf(
  declaration: FluidToolbarDeclaration,
  display: FluidActivityDisplay,
  labels: FluidActivityLabels,
  compactDp: Float,
  readout: FluidActivityReadout,
  minimalOnly: Boolean,
  onTap: () -> Unit,
): FluidToolbarDeclaration {
  // F6B.11: un titolo che è un selettore è un comando, e non cede il posto: la pillola gli sta accanto, minima, fra i tasti.
  val selectorTitle = declaration.titleSelector != null && declaration.titleMode != FluidToolbarTitleMode.None &&
    declaration.activityRequest == null
  val placement = declaration.activityRequest?.placement
    ?: if (selectorTitle) FluidActivityPlacement.Trailing else FluidActivityPlacement.Title
  val inTitle = placement == FluidActivityPlacement.Title
  val forcedMinimal = minimalOnly || inTitle && declaration.hasPrincipal || selectorTitle
  val type = FluidToolbarActivityType(
    display = display, labels = labels, compactDp = compactDp, inTitle = inTitle, minimal = forcedMinimal,
    forcedMinimal = forcedMinimal, onTap = onTap, readout = readout,
  )
  val pill = FluidToolbarButtonEntry(
    id = FluidActivityIds.Pill,
    role = FluidToolbarRole.Text,
    placement = if (inTitle) FluidToolbarPlacement.Principal else FluidToolbarPlacement.Automatic,
    priority = FluidToolbarPriority.High,
    label = fluidActivityPhrase(display),
    onClick = onTap,
    type = type,
  )
  return FluidToolbarDeclaration(
    declaration.entries + pill, declaration.titleMode, declaration.subtitle, declaration.search,
    declaration.overflowLabel, declaration.keyboard, declaration.titleSelector,
    viewerLayout = declaration.viewerLayout, subtitleAccent = declaration.subtitleAccent,
    subtitleOrdinal = declaration.subtitleOrdinal,
  )
}

/** Vero se l'app ha un'attività da mostrare in una barra: la usa l'host delle pagine per le pagine di sempre. */
@Composable
internal fun fluidActivityShown(): Boolean = LocalFluidActivityHost.current?.queue?.display != null

// ---------------------------------------------------------------------------------------------------------------
// L'anellino: la pillola a barra ritirata
// ---------------------------------------------------------------------------------------------------------------

/**
 * L'anellino (F6B.10; «resta come anellino», scelta di Alessio del 22 novembre 2026). Mentre la barra in alto si ritira
 * scorrendo (F4A.4) titolo, tasti e pillola si sfocano e svaniscono col ritiro; **l'anello della pillola no**: sta fuori
 * dal livello che ritira la barra, sale piccolo ([FluidActivityProfile.RingSmall]) nella striscia della barra di stato,
 * sopra il campo che sale al posto della barra, e resta lì finché la barra è ritirata. Quando la barra torna l'anello
 * torna nella pillola, che a barra ferma riprende la forma compatta. È una cosa sola che si muove: la pillola non disegna
 * il suo anello finché l'anellino lo porta ([FluidActivityRing.covers]).
 *
 * - **Dove.** In orizzontale al centro della barra (dove sta la pillola al posto del titolo; una pillola fra i tasti ci
 *   arriva in diagonale), spostato a destra del foro della fotocamera se ci cade sopra; in verticale a metà della barra di
 *   stato ([fluidActivityRingSpot]). Parte dall'anello della pillola e lo segue mentre la pillola si stringe
 *   ([fluidActivityRingHome], [fluidActivityRingAt]).
 * - **Tocco.** A barra ritirata del tutto l'anellino è un tasto: apre la card, che nasce da lui. Prima no: fino a lì il
 *   tasto è la pillola.
 * - **Movimento ridotto.** La barra svanisce sul posto e l'anellino compare sul suo posto in dissolvenza, senza viaggio.
 *
 * Lo chiama chi ospita la barra **fuori** dal livello che la ritira: la barra in alto della schermata e la barra
 * dell'host delle pagine, con il loro ritiro ([minimize]) e la barra dichiarata ([declaration]). Non fa niente senza
 * attività, senza ritiro, in una barra che ha nascosto la pillola o in quella che non la mostra (due barre in vista,
 * [FluidActivityHostState.pane]). Legge il ritiro solo in disegno; la composizione ne vede solo due «sì o no», portati da
 * un effetto (vedi `rememberFluidToolbarRetracting`).
 */
@Composable
internal fun FluidActivityRetractedRing(
  minimize: FluidToolbarMinimizeProgress?,
  declaration: FluidToolbarDeclaration?,
  modifier: Modifier = Modifier,
) {
  val host = LocalFluidActivityHost.current ?: return
  if (minimize == null || declaration == null || declaration.activityRequest?.hidden == true) return
  val display = host.queue.display ?: return
  if (!host.showsIn(LocalFluidPaneRole.current)) return
  val ring = host.ring
  // L'anellino è della schermata davanti: lo riprende quando torna davanti (un pop che la scopre).
  val front = LocalFluidRouteFront.current
  DisposableEffect(ring, minimize) {
    if (front.value) ring.retraction = minimize
    onDispose { if (ring.retraction === minimize) ring.retraction = null }
  }
  LaunchedEffect(ring, minimize, front) {
    snapshotFlow { front.value }.collect { isFront -> if (isFront) ring.retraction = minimize }
  }
  val policy = LocalFluidMotionPolicy.current
  val reduced = minimize.reducedMotion || policy.reducedMotion
  // La pillola si stringe col ritiro (lo stesso «sì o no» della barra): l'anellino passa dall'anello della pillola
  // compatta al centro, col tempo dello scambio del contenuto (R7).
  val narrow = rememberFluidToolbarRetracting(minimize)
  val home = animateFloatAsState(
    targetValue = if (narrow) 1f else 0f,
    animationSpec = tween((GlassContentDefaults.DurationMs * policy.durationScale).roundToInt().coerceAtLeast(0)),
    label = "activity ring home",
  )
  val retracted = rememberFluidActivityRetracted(minimize)
  val density = LocalDensity.current
  val statusBarPx = WindowInsets.statusBars.getTop(density).toFloat()
  val cutouts = fluidActivityTopCutouts()
  val namespace = rememberFluidGlassNamespace()
  val source = remember(namespace) { FluidTransitionSource(FluidActivityRingSourceId, namespace) }
  val labels = LocalFluidActivityLabels.current
  val ink = LocalContentColor.current
  val error = MaterialTheme.colorScheme.error
  val activity = display.activity
  val meter = rememberFluidActivityMeter(activity.progress, activity.paused)
  val clock = rememberFluidActivityClock(activity.time)
  val spoken by remember(meter, clock, activity, labels) {
    derivedStateOf { fluidActivitySpokenReadout(activity, meter.spokenPercent(), clock.millis.longValue, labels) }
  }
  val place = remember { FluidActivityRingPlace() }
  // Alto zero: non sposta niente di chi lo ospita. L'anellino si disegna fuori, dove deve stare.
  Box(modifier.fillMaxWidth().height(0.dp).onPlaced { place.coordinates = it }, contentAlignment = Alignment.TopCenter) {
    val tap = if (!retracted) Modifier.clearAndSetSemantics { } else Modifier
      .semantics {
        val speech = fluidActivitySpeech(display, labels, spoken.takeIf { display.outcome == null })
        contentDescription = speech.name
        stateDescription = speech.state
      }
      .clickable(onClickLabel = labels.showDetails, role = Role.Button) { host.toggle(source) }
    Box(
      Modifier
        .requiredSize(FluidActivityProfile.RingTouch)
        .graphicsLayer {
          val progress = minimize.progress
          alpha = fluidActivityRingAlpha(progress, reduced)
          val coordinates = place.coordinates?.takeIf { it.isAttached }
          if (coordinates == null || alpha <= 0f) return@graphicsLayer
          val origin = coordinates.positionInRoot()
          val spot = fluidActivityRingSpot(
            centerX = origin.x + coordinates.size.width / 2f,
            statusBarPx = statusBarPx,
            radiusPx = FluidActivityProfile.RingSmall.toPx() / 2f,
            marginPx = FluidActivityProfile.RingMargin.toPx(),
            cutouts = cutouts,
          )
          val rest = fluidActivityRingHome(ring.glyphCenter, ring.faceCenter, home.value) ?: spot
          val at = if (reduced) spot else fluidActivityRingAt(rest, spot, progress)
          // Il riquadro ha il centro a metà della larghezza di chi lo ospita, sulla sua riga alta zero (requiredSize).
          translationX = at.x - origin.x - coordinates.size.width / 2f
          translationY = at.y - origin.y
        }
        .fluidTransitionSource(FluidActivityRingSourceId, namespace, CircleShape)
        .then(tap),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        Modifier.graphicsLayer {
          val scale = fluidActivityRingScale(minimize.progress, reduced)
          scaleX = scale
          scaleY = scale
        },
      ) {
        FluidActivityGlyph(activity, display.outcome, FluidActivityProfile.Glyph, ink, error, meter, reduced)
      }
    }
  }
}

/** Dove sta chi ospita l'anellino: letto in disegno. */
private class FluidActivityRingPlace {
  var coordinates: LayoutCoordinates? = null
}

private object FluidActivityRingSourceId {
  override fun toString(): String = "fluid:activity-ring"
}

/** Se la barra è ritirata del tutto: portato da un effetto, come `rememberFluidToolbarRetracting`. */
@Composable
private fun rememberFluidActivityRetracted(minimize: FluidToolbarMinimizeProgress): Boolean {
  var retracted by remember(minimize) { mutableStateOf(Snapshot.withoutReadObservation { minimize.progress >= 1f }) }
  LaunchedEffect(minimize) { snapshotFlow { minimize.progress >= 1f }.collect { retracted = it } }
  return retracted
}

/** I fori dello schermo in alto (la fotocamera), nella finestra: l'anellino non ci va sotto. Vuoto prima di Android 9. */
@Composable
private fun fluidActivityTopCutouts(): List<Rect> {
  // La barra di stato cambia con la rotazione: leggerla qui fa rileggere anche i fori.
  WindowInsets.statusBars.getTop(LocalDensity.current)
  val view = LocalView.current
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return emptyList()
  val rects = view.rootWindowInsets?.displayCutout?.boundingRects ?: return emptyList()
  return rects.map { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) }
}

/**
 * Il posto dell'anellino a barra ritirata, nella radice: al centro della barra ([centerX]), a metà della barra di stato
 * ([statusBarPx]; almeno [marginPx] dal bordo se la barra di stato non c'è), a destra di un foro della fotocamera
 * ([cutouts]) che toccherebbe. [radiusPx] è il raggio dell'anellino. Pura.
 */
internal fun fluidActivityRingSpot(
  centerX: Float,
  statusBarPx: Float,
  radiusPx: Float,
  marginPx: Float,
  cutouts: List<Rect>,
): Offset {
  val y = max(statusBarPx / 2f, radiusPx + marginPx)
  var x = centerX
  for (cutout in cutouts.sortedBy { it.left }) {
    val touches = cutout.top <= y + radiusPx + marginPx && cutout.bottom >= y - radiusPx - marginPx &&
      cutout.left <= x + radiusPx + marginPx && cutout.right >= x - radiusPx - marginPx
    if (touches) x = cutout.right + marginPx + radiusPx
  }
  return Offset(x, y)
}

/**
 * Da dove parte l'anellino: l'anello della pillola compatta ([glyph]) o il centro della pillola, dove sta l'anello della
 * forma minima ([face]), secondo [narrow] (0 compatta, 1 minima, in mezzo mentre la pillola si stringe). Null se la
 * pillola non si è ancora posata. Pura.
 */
internal fun fluidActivityRingHome(glyph: Offset, face: Offset, narrow: Float): Offset? {
  val compact = glyph.takeIf { it.isSpecified } ?: face.takeIf { it.isSpecified } ?: return null
  val minimal = face.takeIf { it.isSpecified } ?: compact
  return lerp(compact, minimal, narrow.coerceIn(0f, 1f))
}

/** Dove sta l'anellino al ritiro [progress]: da [home] (0) al suo posto in alto [spot] (1), morbido ai due capi. Pura. */
internal fun fluidActivityRingAt(home: Offset, spot: Offset, progress: Float): Offset =
  lerp(home, spot, smoothStep(progress.coerceIn(0f, 1f)))

/** La misura dell'anellino al ritiro [progress], in frazione del segno della pillola: da 1 a [FluidActivityProfile.RingSmall]. Pura. */
internal fun fluidActivityRingScale(progress: Float, reduced: Boolean): Float {
  val small = FluidActivityProfile.RingSmall.value / FluidActivityProfile.Glyph.value
  if (reduced) return small
  return 1f + (small - 1f) * smoothStep(progress.coerceIn(0f, 1f))
}

/**
 * L'opacità dell'anellino al ritiro [progress]: pieno da quando il ritiro lascia lo zero (prende il posto dell'anello
 * della pillola, che smette di disegnarlo); col movimento ridotto entra in dissolvenza mentre la barra svanisce. Pura.
 */
internal fun fluidActivityRingAlpha(progress: Float, reduced: Boolean): Float = when {
  progress <= 0f -> 0f
  reduced -> smoothStep(progress.coerceIn(0f, 1f))
  else -> 1f
}

/** Scrive dove sta questo nodo (il centro, nella radice) nell'anellino [ring], solo a barra ferma ([FluidActivityRing.settled]). */
private fun Modifier.fluidActivityRingReport(
  ring: FluidActivityRing?,
  own: FluidToolbarMinimizeProgress?,
  write: (FluidActivityRing, Offset) -> Unit,
): Modifier =
  if (ring == null) this else onGloballyPositioned { coordinates ->
    if (!coordinates.isAttached || !ring.settled(own)) return@onGloballyPositioned
    val center = coordinates.positionInRoot() + Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
    write(ring, center)
  }

// ---------------------------------------------------------------------------------------------------------------
// La pillola come tipo della barra
// ---------------------------------------------------------------------------------------------------------------

/**
 * Che cosa la pillola mostra, senza valori continui: è l'identità del suo contenuto nella barra. Quando cambia, il
 * contenuto si scambia sfocato (R7) nello stesso vetro; l'avanzamento e il tempo no (si ridisegnano soltanto).
 */
@Immutable
internal data class FluidActivityFace(
  val id: String,
  /** 0 in corso, 1 riuscita, 2 non riuscita. */
  val outcome: Int,
  val paused: Boolean,
  val minimal: Boolean,
  val readout: FluidActivityReadout,
)

/** Che cosa sta nella parte del tempo: niente, la percentuale, un tempo sotto l'ora o uno sopra. */
internal enum class FluidActivityReadout { None, Percent, Clock, LongClock }

/**
 * La pillola per il modello della barra ([FluidToolbarKind]). La larghezza ([widthDp]) è **fissa fra i valori**: cambia
 * solo quando cambia ciò che la pillola dice (la frase, la parte del tempo, il contatore che compare), mai con
 * l'avanzamento, perché il piano non regge un pezzo che si misura in continuo. L'esito tiene la larghezza del lavoro.
 * [count] (il contatore) è lo stato: cambia dentro la pillola, sfocato, senza scambiare il resto.
 */
@Immutable
internal data class FluidToolbarActivityKind(
  val face: FluidActivityFace,
  val count: Int,
  override val widthDp: Float,
  private val slim: FluidToolbarActivityKind?,
  private val inTitle: Boolean,
) : FluidToolbarKind {
  override val type: String get() = FluidToolbarKinds.Activity
  override val identity: Any get() = face
  override val state: Any get() = count
  override val narrower: FluidToolbarKind? get() = slim
  override val takesTitlePlace: Boolean get() = inTitle
  override val materializes: Boolean get() = true
}

/** Il codice dell'esito per la faccia: 0 in corso, 1 riuscita, 2 non riuscita. */
private fun FluidActivityOutcome?.faceCode(): Int = when {
  this == null -> 0
  failed -> 2
  else -> 1
}

/** La parte del tempo di [activity] a [nowMillis]: il tempo vince sulla percentuale. */
internal fun fluidActivityReadout(activity: FluidActivity, nowMillis: Long): FluidActivityReadout = when {
  activity.time != null ->
    if (activity.time.shownMillis(nowMillis) >= FluidActivityHourMillis) FluidActivityReadout.LongClock else FluidActivityReadout.Clock
  activity.progress != null -> FluidActivityReadout.Percent
  else -> FluidActivityReadout.None
}

private const val FluidActivityHourMillis = 3_600_000L

/**
 * La pillola per il modello: compatta larga [compactDp] (o minima, la lente da 46 dp, con [minimal]), con la forma
 * minima come forma più stretta finché non è già quella ([forcedMinimal]). Pura.
 */
internal fun fluidToolbarActivityKind(
  display: FluidActivityDisplay,
  compactDp: Float,
  inTitle: Boolean,
  minimal: Boolean,
  forcedMinimal: Boolean,
  readout: FluidActivityReadout,
): FluidToolbarActivityKind {
  fun of(slim: Boolean): FluidToolbarActivityKind = FluidToolbarActivityKind(
    face = FluidActivityFace(display.activity.id, display.outcome.faceCode(), display.activity.paused, slim, readout),
    count = display.others,
    widthDp = if (slim) FluidMetrics.GlassButtonLens.value else compactDp,
    slim = if (slim || forcedMinimal) null else of(true),
    inTitle = inTitle,
  )
  return of(minimal || forcedMinimal)
}

/**
 * Il tipo della barra (F6B.3a) che disegna la pillola: la forma ([minimal]) l'ha scelta la barra
 * ([resolvedAs] dalla forma risolta del [kind]); la faccia, il tempo e l'avanzamento li legge da [display].
 */
internal class FluidToolbarActivityType(
  val display: FluidActivityDisplay,
  val labels: FluidActivityLabels,
  private val compactDp: Float,
  private val inTitle: Boolean,
  val minimal: Boolean,
  private val forcedMinimal: Boolean,
  private val onTap: () -> Unit,
  /** La parte del tempo, decisa quando la barra si compone: la sua classe di larghezza. */
  val readout: FluidActivityReadout = FluidActivityReadout.None,
) : FluidToolbarElementType() {

  override val kind: FluidToolbarActivityKind =
    fluidToolbarActivityKind(display, compactDp, inTitle, minimal, forcedMinimal, readout)

  override fun resolvedAs(kind: FluidToolbarKind?): FluidToolbarElementType {
    val face = (kind as? FluidToolbarActivityKind)?.face ?: return this
    return if (face.minimal == minimal) this
      else FluidToolbarActivityType(display, labels, compactDp, inTitle, face.minimal, forcedMinimal, onTap, readout)
  }

  @Composable
  override fun rememberSlot(live: Boolean): FluidToolbarTypeSlot {
    val activity = display.activity
    val meter = rememberFluidActivityMeter(activity.progress, activity.paused)
    val clock = rememberFluidActivityClock(activity.time)
    val pulse = if (live) rememberFluidActivityPulse(display.outcome) else null
    val styles = fluidActivityPillStyles()
    val scheme = MaterialTheme.colorScheme
    val reduced = LocalFluidMotionPolicy.current.reducedMotion
    // Le parole cambiano a passi (un quarto; il tempo al secondo), non a ogni valore dell'anello. Lette qui, nella
    // composizione: il posto si ricompone a ogni passo e TalkBack legge le parole nuove quando torna sulla pillola.
    val spokenState = remember(meter, clock, activity, labels) {
      derivedStateOf { fluidActivitySpokenReadout(activity, meter.spokenPercent(), clock.millis.longValue, labels) }
    }
    val spoken = spokenState.value
    val host = LocalFluidActivityHost.current
    // Il ritiro della barra di questa pillola: quello che la barra applica, o quello della riga che lo applica per lei.
    val retraction = LocalFluidToolbarMinimize.current ?: LocalFluidToolbarRetraction.current
    if (live && host != null && activity.time != null) {
      // Il tempo che passa l'ora cambia la classe di larghezza: le barre rimisurano la pillola (una volta).
      val long = readout == FluidActivityReadout.LongClock
      LaunchedEffect(clock, long) {
        snapshotFlow { clock.millis.longValue >= FluidActivityHourMillis }.collect { now -> if (now != long) host.remeasured() }
      }
    }
    val type = this
    return object : FluidToolbarTypeSlot {
      @Composable
      override fun Face(color: Color, iconSize: Dp, onTint: Boolean) {
        FluidActivityPillFace(
          display = display, minimal = type.minimal, widthDp = type.kind.widthDp, readout = type.readout, ink = color,
          error = if (onTint) color else scheme.error, accent = if (onTint) color else scheme.primary,
          meter = meter, clock = clock, pulse = pulse, styles = styles, reduced = reduced, ring = host?.ring,
          retraction = retraction,
        )
      }

      override fun describe(receiver: SemanticsPropertyReceiver, name: String?) {
        val speech = fluidActivitySpeech(display, labels, spoken.takeIf { display.outcome == null })
        receiver.contentDescription = speech.name
        receiver.stateDescription = speech.state
        if (speech.live) receiver.liveRegion = LiveRegionMode.Polite
        receiver.onClick(label = labels.showDetails) {
          onTap()
          true
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------------------------
// La faccia: compatta e minima
// ---------------------------------------------------------------------------------------------------------------

/** Gli stili della pillola: la frase, la parte del tempo e il contatore (cifre tabulari), e chi li misura. */
@Stable
internal class FluidActivityPillStyles(
  val phrase: TextStyle,
  val readout: TextStyle,
  val counter: TextStyle,
  val measurer: TextMeasurer,
  val density: Float,
)

@Composable
internal fun fluidActivityPillStyles(): FluidActivityPillStyles {
  val type = MaterialTheme.typography
  val measurer = rememberTextMeasurer()
  val density = LocalDensity.current.density
  return remember(type, measurer, density) {
    FluidActivityPillStyles(
      phrase = type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
      readout = type.labelLarge.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.Medium),
      counter = type.labelMedium.copy(fontFeatureSettings = "tnum", fontWeight = FontWeight.SemiBold),
      measurer = measurer,
      density = density,
    )
  }
}

/** La frase della pillola: il messaggio dell'esito, se c'è, altrimenti il titolo dell'attività. */
internal fun fluidActivityPhrase(display: FluidActivityDisplay): String =
  display.outcome?.message ?: display.activity.title

/** La scritta più larga della parte del tempo, per la larghezza fissa. */
private fun FluidActivityReadout.template(): String? = when (this) {
  FluidActivityReadout.None -> null
  FluidActivityReadout.Percent -> "100%"
  FluidActivityReadout.Clock -> "88:88"
  FluidActivityReadout.LongClock -> "8:88:88"
}

/** Il contatore come si scrive: «+2», oltre il tetto «9+». */
internal fun fluidActivityCounterText(count: Int): String =
  if (count > FluidActivityProfile.MaxCounter) "${FluidActivityProfile.MaxCounter}+" else "+$count"

/**
 * La larghezza della pillola compatta in dp, data la misura della frase ([phraseDp], già con il tetto), della parte del
 * tempo ([readoutDp], 0 se non c'è) e del contatore ([counterDp], 0 se non c'è). Pura: è la regola della larghezza fissa.
 */
internal fun fluidActivityPillWidthOf(phraseDp: Float, readoutDp: Float, counterDp: Float): Float {
  val p = FluidActivityProfile
  var width = p.SidePadding.value * 2f + p.Glyph.value + p.Gap.value + phraseDp
  if (readoutDp > 0f) width += p.Gap.value + readoutDp
  if (counterDp > 0f) width += p.CounterGap.value + counterDp
  return ceil(max(width, FluidMetrics.GlassButtonLens.value))
}

/**
 * La larghezza della pillola compatta per [display]: quella del **lavoro** (il titolo e la parte del tempo), anche quando
 * mostra l'esito, così l'esito non cambia la misura del vetro.
 */
internal fun fluidActivityPillWidthDp(display: FluidActivityDisplay, styles: FluidActivityPillStyles, nowMillis: Long): Float {
  fun measure(text: String, style: TextStyle): Float =
    styles.measurer.measure(text, style, maxLines = 1, softWrap = false).size.width / styles.density
  val activity = display.activity
  val phrase = min(measure(activity.title, styles.phrase), FluidActivityProfile.PhraseMax.value)
  val readout = fluidActivityReadout(activity, nowMillis).template()?.let { measure(it, styles.readout) } ?: 0f
  val counter = if (display.others > 0) {
    measure(fluidActivityCounterText(FluidActivityProfile.MaxCounter + 1), styles.counter) + FluidActivityProfile.CounterPadding.value * 2f
  } else 0f
  return fluidActivityPillWidthOf(phrase, readout, counter)
}

/** L'avanzamento come lo legge la pillola: dallo stato dell'app (si ridisegna da solo) o riletto dieci volte al secondo. */
@Stable
internal class FluidActivityMeter(private val source: (() -> Float)?, private val stateBacked: Boolean) {
  internal val polled = mutableFloatStateOf(source?.let { fluidActivitySanitize(it()) } ?: 0f)

  /** L'avanzamento adesso, 0..1. Letto in un disegno, lo ridisegna quando cambia. */
  fun read(): Float = if (stateBacked && source != null) fluidActivitySanitize(source()) else polled.floatValue

  val determinate: Boolean get() = source != null

  /** L'avanzamento a passi di un quarto, per TalkBack. */
  fun spokenPercent(): Int? = if (source == null) null else ((read() * 4f).toInt().coerceIn(0, 4) * 25)

  internal val polls: Boolean get() = source != null && !stateBacked
}

private fun fluidActivitySanitize(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

@Composable
private fun rememberFluidActivityMeter(source: (() -> Float)?, paused: Boolean): FluidActivityMeter {
  val meter = remember(source) {
    // Uno stato di Compose letto dalla sorgente ridisegna l'anello da solo; altrimenti si rilegge. La prova e la prima
    // lettura non le vede la composizione: la barra non si ricompone a ogni passo dell'avanzamento.
    Snapshot.withoutReadObservation {
      var reads = false
      if (source != null) Snapshot.observe(readObserver = { reads = true }) { source() }
      FluidActivityMeter(source, reads)
    }
  }
  if (meter.polls && !paused) {
    LaunchedEffect(meter) {
      fluidActivityRepeat {
        while (true) {
          meter.polled.floatValue = fluidActivitySanitize(source!!())
          delay(FluidActivityProfile.ProgressPollMs)
        }
      }
    }
  }
  return meter
}

/**
 * Un lavoro che non finisce finché la pillola c'è (il tempo, un avanzamento riletto): passa dalla politica delle animazioni
 * infinite, così un test d'interfaccia (dell'engine o di un'app) può ancora dire «fermo» con una pillola in vista. Fuori
 * dai test corre come un `delay`, senza tenere sveglio ogni fotogramma.
 */
private suspend fun fluidActivityRepeat(block: suspend () -> Unit) {
  val policy = coroutineContext[InfiniteAnimationPolicy]
  if (policy == null) block() else policy.onInfiniteOperation { block() }
}

/** Il tempo come lo legge la pillola: i millisecondi mostrati, aggiornati ogni secondo che cambia. */
@Stable
internal class FluidActivityClock(val time: FluidActivityTime?, now: () -> Long) {
  val millis = mutableLongStateOf(time?.shownMillis(now()) ?: 0L)
}

@Composable
private fun rememberFluidActivityClock(time: FluidActivityTime?): FluidActivityClock {
  val host = LocalFluidActivityHost.current
  val now: () -> Long = host?.let { it::now } ?: SystemClock::elapsedRealtime
  val clock = remember(time) { FluidActivityClock(time, now) }
  if (time != null && time.ticks) {
    LaunchedEffect(clock) {
      fluidActivityRepeat {
        while (true) {
          val shown = time.shownMillis(now())
          clock.millis.longValue = shown
          // Fino al prossimo secondo che cambia la scritta.
          val toNext = if (time.countsDown) (shown % 1000L).let { if (it == 0L) 1000L else it } else 1000L - shown % 1000L
          delay(toNext.coerceIn(16L, 1000L))
        }
      }
    }
  }
  return clock
}

/** La scritta del tempo: «m:ss» sotto l'ora, «h:mm:ss» sopra; un conto alla rovescia arrotonda per eccesso. */
internal fun fluidActivityClockText(millis: Long, countsDown: Boolean): String {
  val seconds = if (countsDown) (millis + 999L) / 1000L else millis / 1000L
  val h = seconds / 3600L
  val m = seconds % 3600L / 60L
  val s = seconds % 60L
  return if (h > 0L) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** La percentuale scritta: arrotondata, da 0 a 100. */
internal fun fluidActivityPercent(value: Float): Int = (fluidActivitySanitize(value) * 100f).roundToInt()

/** Il battito dell'esito: 0 a riposo, poi da 0 a 1 una volta per esito. */
@Stable
internal class FluidActivityPulse {
  val phase = Animatable(1f)
}

@Composable
private fun rememberFluidActivityPulse(outcome: FluidActivityOutcome?): FluidActivityPulse {
  val pulse = remember { FluidActivityPulse() }
  val policy = LocalFluidMotionPolicy.current
  LaunchedEffect(outcome, policy.reducedMotion) {
    if (outcome == null || policy.reducedMotion) {
      pulse.phase.snapTo(1f)
      return@LaunchedEffect
    }
    // L'esito entra sfocato (R7); il battito parte quando lo si legge, come la scossa del tasto con esito.
    pulse.phase.snapTo(1f)
    delay((GlassContentDefaults.DurationMs * FluidActivityProfile.PulseDelayFraction * policy.durationScale).toLong().coerceAtLeast(0L))
    pulse.phase.snapTo(0f)
    pulse.phase.animateTo(1f, tween((FluidActivityProfile.PulseMs * policy.durationScale).roundToInt().coerceAtLeast(0), easing = LinearEasing))
  }
  return pulse
}

/** Il battito alla fase [u]: una campana, zero ai due capi. Pura. */
internal fun fluidActivityPulseAt(u: Float): Float = if (u <= 0f || u >= 1f) 0f else sin(PI.toFloat() * u)

/**
 * La faccia della pillola nel posto della barra, grande quanto il posto ([widthDp] × 46 dp): il segno, e nella forma
 * compatta la frase, la parte del tempo e il contatore. Tutto è contenuto: niente tocca il vetro né lo ricattura
 * (l'anello, le cifre e il battito si ridisegnano soltanto).
 */
@Composable
private fun FluidActivityPillFace(
  display: FluidActivityDisplay,
  minimal: Boolean,
  widthDp: Float,
  readout: FluidActivityReadout,
  ink: Color,
  error: Color,
  accent: Color,
  meter: FluidActivityMeter,
  clock: FluidActivityClock,
  pulse: FluidActivityPulse?,
  styles: FluidActivityPillStyles,
  reduced: Boolean,
  ring: FluidActivityRing? = null,
  /** Il ritiro della barra di questa pillola (null se non si ritira): l'anellino le prende l'anello solo se è il suo. */
  retraction: FluidToolbarMinimizeProgress? = null,
) {
  val activity = display.activity
  val outcome = display.outcome
  val film = when {
    outcome == null -> null
    outcome.failed -> error
    else -> accent
  }
  Box(
    Modifier
      .size(widthDp.dp, FluidMetrics.GlassButtonLens)
      .fluidActivityRingReport(ring, retraction) { target, center -> target.faceCenter = center }
      .drawBehind {
        val u = pulse?.phase?.value ?: 1f
        val amount = fluidActivityPulseAt(u)
        if (film != null && amount > 0f) {
          val inset = 3.dp.toPx()
          drawRoundRect(
            film.copy(alpha = film.alpha * FluidActivityProfile.PulseFilmAlpha * amount),
            topLeft = Offset(inset, inset),
            size = Size(size.width - inset * 2f, size.height - inset * 2f),
            cornerRadius = CornerRadius((size.height - inset * 2f) / 2f),
          )
        }
      },
    contentAlignment = Alignment.Center,
  ) {
    val glyphScale: () -> Float = {
      val u = pulse?.phase?.value ?: 1f
      1f + (FluidActivityProfile.PulseScale - 1f) * fluidActivityPulseAt(u)
    }
    // L'anello della pillola: dice all'anellino dov'è, e non si disegna mentre l'anellino lo porta (F6B.10).
    val anchor = Modifier.fluidActivityRingReport(ring, retraction) { target, center -> target.glyphCenter = center }
      .then(if (ring == null) Modifier else Modifier.graphicsLayer { alpha = if (ring.covers(retraction)) 0f else 1f })
    if (minimal) {
      Box(contentAlignment = Alignment.Center) {
        Box(anchor) { FluidActivityGlyph(activity, outcome, FluidActivityProfile.Glyph, ink, error, meter, reduced, glyphScale) }
        if (display.others > 0) FluidActivityMinimalBadge(display.others, ink, styles)
      }
    } else {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Box(anchor) { FluidActivityGlyph(activity, outcome, FluidActivityProfile.Glyph, ink, error, meter, reduced, glyphScale) }
        Spacer(Modifier.width(FluidActivityProfile.Gap))
        Text(
          text = fluidActivityPhrase(display),
          style = styles.phrase,
          color = if (outcome?.failed == true) error else ink,
          maxLines = 1,
          softWrap = false,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.widthIn(max = FluidActivityProfile.PhraseMax),
        )
        // La parte del tempo ha la larghezza della sua classe, decisa dalla barra; l'esito non ce l'ha (lo dice il segno).
        val template = readout.template()
        if (outcome == null && template != null) {
          Spacer(Modifier.width(FluidActivityProfile.Gap))
          FluidActivityReadoutText(activity, meter, clock, template, styles, ink, Alignment.CenterEnd)
        }
        if (display.others > 0) {
          Spacer(Modifier.width(FluidActivityProfile.CounterGap))
          FluidActivityCounter(display.others, ink, styles)
        }
      }
    }
  }
}

/**
 * La parte del tempo: la percentuale o il tempo, disegnati (non composti) in un riquadro largo quanto [template] con le
 * cifre tabulari, allineati alla fine: le cifre cambiano, la misura no.
 */
@Composable
private fun FluidActivityReadoutText(
  activity: FluidActivity,
  meter: FluidActivityMeter,
  clock: FluidActivityClock,
  template: String,
  styles: FluidActivityPillStyles,
  ink: Color,
  alignment: Alignment,
) {
  val measured = remember(template, styles) { styles.measurer.measure(template, styles.readout, maxLines = 1, softWrap = false) }
  val widthDp = measured.size.width / styles.density
  val heightDp = measured.size.height / styles.density
  Box(
    Modifier.size(widthDp.dp, heightDp.dp).drawBehind {
      val text = fluidActivityReadoutValue(activity, meter.read(), clock.millis.longValue)
      val layout = styles.measurer.measure(text, styles.readout, maxLines = 1, softWrap = false)
      val x = when (alignment) {
        Alignment.CenterStart -> 0f
        Alignment.Center -> (size.width - layout.size.width) / 2f
        else -> size.width - layout.size.width
      }
      drawText(layout, ink, Offset(x, (size.height - layout.size.height) / 2f))
    },
  )
}

/** La scritta della parte del tempo adesso: il tempo se c'è, altrimenti la percentuale. */
internal fun fluidActivityReadoutValue(activity: FluidActivity, progress: Float, millis: Long): String = when {
  activity.time != null -> fluidActivityClockText(millis, activity.time.countsDown)
  else -> "${fluidActivityPercent(progress)}%"
}

/** Il contatore della forma compatta: «+2» su una pellicola, che cambia sfocato (R7) senza toccare il resto. */
@Composable
private fun FluidActivityCounter(count: Int, ink: Color, styles: FluidActivityPillStyles) {
  val width = remember(styles) {
    styles.measurer.measure(fluidActivityCounterText(FluidActivityProfile.MaxCounter + 1), styles.counter).size.width / styles.density
  }
  Box(
    Modifier
      .size((width + FluidActivityProfile.CounterPadding.value * 2f).dp, 20.dp)
      .drawBehind {
        drawRoundRect(ink.copy(alpha = ink.alpha * FluidActivityProfile.CounterFilmAlpha), cornerRadius = CornerRadius(size.height / 2f))
      },
    contentAlignment = Alignment.Center,
  ) {
    // Un contatore che compare nasce dal vuoto, sfocato (R7), come un tasto nuovo in un vetro che c'era.
    var arrived by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { arrived = true }
    FluidGlassContentTransition(targetState = count.takeIf { arrived }, blurReference = 20.dp) { shown ->
      if (shown != null) Text(fluidActivityCounterText(shown), style = styles.counter, color = ink, maxLines = 1)
    }
  }
}

/** Il contatore della forma minima: un tondo piccolo in alto a destra del segno. */
@Composable
private fun FluidActivityMinimalBadge(count: Int, ink: Color, styles: FluidActivityPillStyles) {
  Box(
    Modifier
      .padding(start = FluidActivityProfile.Glyph * 0.9f, bottom = FluidActivityProfile.Glyph * 0.7f)
      .size(14.dp)
      .drawBehind { drawCircle(ink.copy(alpha = ink.alpha * 0.85f)) },
    contentAlignment = Alignment.Center,
  ) {
    val surface = MaterialTheme.colorScheme.surface
    Text(
      if (count > FluidActivityProfile.MaxCounter) "+" else "$count",
      style = styles.counter.copy(fontSize = styles.counter.fontSize * 0.8f),
      color = surface,
      maxLines = 1,
    )
  }
}

/**
 * Il segno della pillola: l'esito (la spunta, o il segno d'errore nella sua tinta), o l'anello del lavoro con l'icona
 * dentro, o l'icona da sola. In pausa l'icona diventa la pausa e l'anello si attenua. [scale] è il battito dell'esito.
 */
@Composable
private fun FluidActivityGlyph(
  activity: FluidActivity,
  outcome: FluidActivityOutcome?,
  size: Dp,
  ink: Color,
  error: Color,
  meter: FluidActivityMeter,
  reduced: Boolean,
  scale: () -> Float = { 1f },
) {
  val layer = Modifier.graphicsLayer {
    val s = scale()
    scaleX = s
    scaleY = s
  }
  when {
    outcome != null -> Box(Modifier.requiredSize(size).then(layer), contentAlignment = Alignment.Center) {
      FluidIcon(if (outcome.failed) FluidIcons.Error else FluidIcons.Check, null, tint = if (outcome.failed) error else ink, size = size)
    }
    activity.kind.ring -> FluidActivityRing(activity, size, ink, meter, reduced, layer)
    else -> Box(Modifier.requiredSize(size).then(layer), contentAlignment = Alignment.Center) {
      val icon = if (activity.paused) FluidIcons.Pause else activity.icon ?: activity.kind.icon
      FluidIcon(icon, null, tint = if (activity.kind.alert && !activity.paused) error else ink, size = size * 0.92f)
    }
  }
}

/** L'anello del lavoro: la pista, l'arco che avanza da ore 12 o lo spicchio che gira, e l'icona piccola dentro. */
@Composable
private fun FluidActivityRing(
  activity: FluidActivity,
  size: Dp,
  ink: Color,
  meter: FluidActivityMeter,
  reduced: Boolean,
  layer: Modifier,
) {
  val spinning = !meter.determinate && !activity.paused && !reduced
  val angle = remember { mutableFloatStateOf(0f) }
  if (spinning) {
    val scale = LocalFluidMotionPolicy.current.durationScale.coerceAtLeast(0.01f)
    LaunchedEffect(scale) {
      var previous = withInfiniteAnimationFrameNanos { it }
      while (true) {
        val now = withInfiniteAnimationFrameNanos { it }
        angle.floatValue = (angle.floatValue + 360f * ((now - previous) / 1_000_000f) / (FluidActivityProfile.SpinnerTurnMs * scale)) % 360f
        previous = now
      }
    }
  }
  val determinate = meter.determinate
  val paused = activity.paused
  Box(
    Modifier.requiredSize(size).then(layer).drawBehind {
      val stroke = FluidActivityProfile.RingStroke.toPx()
      val diameter = this.size.minDimension - stroke
      val topLeft = Offset((this.size.width - diameter) / 2f, (this.size.height - diameter) / 2f)
      val arc = Size(diameter, diameter)
      val arcInk = if (paused) ink.copy(alpha = ink.alpha * FluidActivityProfile.PausedRingAlpha) else ink
      drawArc(ink.copy(alpha = ink.alpha * FluidActivityProfile.RingTrackAlpha), 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
      when {
        determinate -> {
          val sweep = 360f * meter.read()
          if (sweep > 0f) drawArc(arcInk, -90f, sweep, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        spinning -> drawArc(arcInk, angle.floatValue - 90f, FluidActivityProfile.SpinnerSweep * 360f, false, topLeft, arc,
          style = Stroke(stroke, cap = StrokeCap.Round))
        // Fermo (pausa, movimento ridotto): l'anello intero attenuato dice «sto lavorando» senza girare.
        else -> drawArc(ink.copy(alpha = ink.alpha * FluidActivityProfile.PausedRingAlpha), 0f, 360f, false, topLeft, arc, style = Stroke(stroke))
      }
    },
    contentAlignment = Alignment.Center,
  ) {
    val icon = if (paused) FluidIcons.Pause else activity.icon ?: activity.kind.icon
    FluidIcon(icon, null, tint = ink, size = size * FluidActivityProfile.RingIconFraction)
  }
}

// ---------------------------------------------------------------------------------------------------------------
// Le parole (pure)
// ---------------------------------------------------------------------------------------------------------------

/** Ciò che TalkBack dice della pillola: il nome, lo stato, e se è un'area viva (l'esito si annuncia, l'avanzamento no). */
@Immutable
internal data class FluidActivitySpeech(val name: String, val state: String, val live: Boolean)

/** La parte del tempo per TalkBack: la percentuale a passi di un quarto, o il tempo. Pura. */
internal fun fluidActivitySpokenReadout(activity: FluidActivity, percent: Int?, millis: Long, labels: FluidActivityLabels): String? =
  when {
    activity.time != null -> fluidActivityClockText(millis, activity.time.countsDown)
    percent != null -> labels.percent(percent)
    else -> null
  }

/**
 * Le parole della pillola per [display]: il nome è il titolo dell'attività; lo stato dice l'esito, la pausa, la parte del
 * tempo ([readout]) e le altre attività in corso. L'esito è un'area viva gentile: TalkBack lo annuncia senza spostare il
 * fuoco; l'avanzamento no, perché si leggerebbe a ogni passo. Pura.
 */
internal fun fluidActivitySpeech(display: FluidActivityDisplay, labels: FluidActivityLabels, readout: String?): FluidActivitySpeech {
  val outcome = display.outcome
  val parts = buildList {
    when {
      outcome?.failed == true -> add(outcome.message ?: labels.failed)
      outcome != null -> add(outcome.message ?: labels.succeeded)
      else -> {
        if (display.activity.paused) add(labels.paused)
        readout?.let(::add)
      }
    }
    if (display.others > 0) add(labels.others(display.others))
  }
  return FluidActivitySpeech(display.activity.title, parts.joinToString(", "), live = outcome != null)
}

/** Un comando della card: l'azione, e se è «Apri» (il principale, che chiude la card e porta al lavoro). */
@Immutable
internal data class FluidActivityCardCommand(val action: FluidActivityAction, val opens: Boolean)

/**
 * I comandi della card per [display], nell'ordine: quelli dell'attività, poi «Apri». Un'attività finita non ne ha: la card
 * dice l'esito. Pura.
 */
internal fun fluidActivityCardCommands(display: FluidActivityDisplay, labels: FluidActivityLabels): List<FluidActivityCardCommand> {
  if (display.outcome != null) return emptyList()
  val activity = display.activity
  return activity.actions.map { FluidActivityCardCommand(it, opens = false) } +
    listOfNotNull(activity.onOpen?.let { FluidActivityCardCommand(FluidActivityAction(labels.open, it, FluidIcons.OpenInNew), opens = true) })
}

// ---------------------------------------------------------------------------------------------------------------
// La card estesa
// ---------------------------------------------------------------------------------------------------------------

/**
 * La forma estesa: al tocco una card **nasce dalla pillola** col popover dalla sorgente di F5B.1 (la pillola si raccoglie
 * in goccia, la card si apre attorno a lei col contenuto sfocato e piegato dalla lente, poi si posa) e ci rientra. Resta
 * un popover anche sul telefono ([FluidCompactAdaptation.Popover]): la card è della pillola, come l'isola che si allarga
 * (F6B.0 §9, WWDC26 223: ≈367 ms dalla prima deviazione alla posa, col contenuto sfocato fino alla posa).
 */
@Composable
private fun FluidActivityCard(state: FluidActivityHostState) {
  val display = state.queue.display?.takeIf { state.expanded }
  val labels = LocalFluidActivityLabels.current
  FluidGlassModalPortal(
    item = display,
    onDismissRequest = state::collapse,
    paneTitle = display?.activity?.title,
    source = state.expandedSource,
    compactAdaptation = FluidCompactAdaptation.Popover,
  ) { shown ->
    // Un comando non chiude la card da sé: la chiude l'app che toglie l'attività, o «Apri», che porta altrove. Pausa e
    // Riprendi la lasciano aperta, e la card mostra il nuovo stato; un'attività che finisce ci mostra l'esito.
    FluidActivityCardContent(shown, labels, now = state::now, onCommand = { command ->
      command.action.onClick()
      if (command.opens) state.collapse()
    })
  }
}

/** Il contenuto della card: il segno, il titolo, i dettagli e la parte del tempo; le altre attività; i comandi. */
@Composable
private fun FluidActivityCardContent(
  display: FluidActivityDisplay,
  labels: FluidActivityLabels,
  now: () -> Long,
  onCommand: (FluidActivityCardCommand) -> Unit,
) {
  val activity = display.activity
  val scheme = MaterialTheme.colorScheme
  val ink = LocalContentColor.current
  val secondary = ink.copy(alpha = ink.alpha * 0.66f)
  val styles = fluidActivityPillStyles()
  val reduced = LocalFluidMotionPolicy.current.reducedMotion
  val meter = rememberFluidActivityMeter(activity.progress, activity.paused)
  val clock = rememberFluidActivityClock(activity.time)
  val spoken by remember(meter, clock, activity, labels) {
    derivedStateOf { fluidActivitySpokenReadout(activity, meter.spokenPercent(), clock.millis.longValue, labels) }
  }
  Column(
    Modifier.widthIn(min = FluidActivityProfile.CardMinWidth, max = FluidActivityProfile.CardMaxWidth).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    val speech = fluidActivitySpeech(display, labels, spoken.takeIf { display.outcome == null })
    Row(
      verticalAlignment = Alignment.CenterVertically,
      // Una riga sola per TalkBack: il titolo, poi lo stato.
      modifier = Modifier.semantics(mergeDescendants = true) {
        contentDescription = speech.name
        stateDescription = speech.state
      },
    ) {
      FluidActivityGlyph(activity, display.outcome, FluidActivityProfile.CardGlyph, ink, scheme.error, meter, reduced)
      Spacer(Modifier.width(12.dp))
      Column(Modifier.weight(1f)) {
        Text(activity.title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
          color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val line = display.outcome?.let { it.message ?: if (it.failed) labels.failed else labels.succeeded }
          ?: if (activity.paused) labels.paused else activity.detail
        if (line != null) {
          Text(line, style = MaterialTheme.typography.bodyMedium,
            color = if (display.outcome?.failed == true) scheme.error else secondary, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
      }
      val template = fluidActivityCardTemplate(activity, clock)
      if (display.outcome == null && template != null) {
        Spacer(Modifier.width(12.dp))
        FluidActivityReadoutText(activity, meter, clock, template, styles, ink, Alignment.CenterEnd)
      }
    }
    if (display.rest.isNotEmpty()) {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(labels.alsoRunning, style = MaterialTheme.typography.labelMedium, color = secondary)
        for (other in display.rest) key(other.id) {
          FluidActivityCardRow(other, ink, scheme.error, styles, reduced)
        }
      }
    }
    val commands = fluidActivityCardCommands(display, labels)
    if (commands.isNotEmpty()) {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for (command in commands) key(command.action.label) {
          val action = command.action
          FluidButton(
            text = action.label,
            onClick = { onCommand(command) },
            style = when {
              action.destructive -> FluidButtonStyle.Destructive
              command.opens -> FluidButtonStyle.Filled
              else -> FluidButtonStyle.Tinted
            },
            size = FluidButtonSize.Small,
            leading = action.icon?.let { icon -> { FluidIcon(icon, null, size = 16.dp) } },
          )
        }
      }
    }
  }
}

/** La scritta più larga della parte del tempo di [activity] nella card, o null se non ne ha una. */
private fun fluidActivityCardTemplate(activity: FluidActivity, clock: FluidActivityClock): String? = when {
  activity.time != null ->
    (if (clock.millis.longValue >= FluidActivityHourMillis) FluidActivityReadout.LongClock else FluidActivityReadout.Clock).template()
  activity.progress != null -> FluidActivityReadout.Percent.template()
  else -> null
}

/** Una riga delle altre attività nella card: il segno piccolo, il titolo, la parte del tempo. */
@Composable
private fun FluidActivityCardRow(
  activity: FluidActivity,
  ink: Color,
  error: Color,
  styles: FluidActivityPillStyles,
  reduced: Boolean,
) {
  val meter = rememberFluidActivityMeter(activity.progress, activity.paused)
  val clock = rememberFluidActivityClock(activity.time)
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics(mergeDescendants = true) { }) {
    FluidActivityGlyph(activity, null, FluidActivityProfile.CardRowGlyph, ink, error, meter, reduced)
    Spacer(Modifier.width(8.dp))
    Text(activity.title, style = MaterialTheme.typography.bodyMedium, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f))
    val template = fluidActivityCardTemplate(activity, clock)
    if (template != null) {
      Spacer(Modifier.width(8.dp))
      FluidActivityReadoutText(activity, meter, clock, template, styles, ink, Alignment.CenterEnd)
    }
  }
}

/** L'altezza della pillola, per chi la disegna fuori dalla barra (le tracce del banco). */
internal val FluidActivityPillHeight: Dp get() = FluidMetrics.GlassButtonLens

/** La parte del tempo per le tracce: la scritta più larga. */
internal fun fluidActivityReadoutTemplate(readout: FluidActivityReadout): String? = readout.template()
