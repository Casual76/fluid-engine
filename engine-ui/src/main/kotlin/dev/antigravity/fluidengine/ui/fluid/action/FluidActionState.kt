package dev.antigravity.fluidengine.ui.fluid.action

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.Close
import dev.antigravity.fluidengine.ui.fluid.icons.CloudUpload
import dev.antigravity.fluidengine.ui.fluid.icons.ContentCopy
import dev.antigravity.fluidengine.ui.fluid.icons.Download
import dev.antigravity.fluidengine.ui.fluid.icons.Error
import dev.antigravity.fluidengine.ui.fluid.icons.Favorite
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Refresh
import dev.antigravity.fluidengine.ui.fluid.icons.Save
import dev.antigravity.fluidengine.ui.fluid.icons.Send
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/*
 * I tasti con esito (piano «iOS in Android», F6B.3a): lo stato, i preset, l'esecutore facoltativo e la logica pura di
 * ciò che il tasto mostra, dice a TalkBack e fa vibrare. I composable stanno in `FluidActionButton.kt`; i glifi animati
 * dei preset arrivano con F6B.3b (`FluidActionGlyphs.kt`).
 *
 * La regola di fondo (piano, F6B, «Tasti con stato di lavoro ed esito», punto 1): **lo stato è dell'app**. Il tasto non
 * ha logica, legge un [FluidActionState] e lo disegna: la rotazione e la ricomposizione non lo azzerano, perché lo
 * stato sta nel modello dell'app (un ViewModel, uno stato salvato) o nell'esecutore ([FluidActionRunner]), che si salva
 * da sé.
 */

// ================================================================================================================
// Lo stato
// ================================================================================================================

/**
 * Lo stato di un tasto con esito: a riposo, al lavoro, riuscito, non riuscito, annullato. È `UIButton.Configuration.
 * showsActivityIndicator` di UIKit più l'esito, che UIKit lascia all'app (R1 K7, HIG Feedback).
 *
 * È un valore: lo possiede l'app, e il tasto lo disegna. Un'app che non vuole scrivere i tempi a mano usa
 * [rememberFluidActionRunner], che trasforma una funzione `suspend` in questi stati con le regole di R8 (la soglia dei
 * 300 ms, il tasto spento mentre lavora, la tenuta dell'esito).
 */
@Immutable
sealed class FluidActionState {
  /** A riposo: l'icona del preset, il tasto risponde. */
  data object Idle : FluidActionState()

  /**
   * Al lavoro: un anello sottile attorno all'icona, dentro il vetro (scelta di Alessio del 21 novembre 2026). Il tasto è
   * **spento**: non risponde al tocco e TalkBack lo dice disattivato; l'anello resta a piena tinta, perché è lui il
   * riscontro (R1 K7: l'indicatore sta al posto dell'immagine).
   *
   * @property progress quanto è fatto, 0..1, letto **a ogni fotogramma** mentre l'anello si disegna (non ricompone): null
   *   è il lavoro indeterminato, l'anello che gira. Con un valore l'arco parte a ore 12 e va in senso orario (F6B.0, §6,
   *   Variable Draw: alta sull'ordine e sul verso), e i preset che lo chiedono (Scarica, Carica) mostrano la percentuale.
   */
  class Working internal constructor(
    val progress: (() -> Float)?,
    /**
     * Il lavoro c'è ma è ancora sotto la soglia ([FluidActionDefaults.MinWorking]): il tasto è già spento, la faccia è
     * ancora quella di riposo. Lo scrive solo l'esecutore; uno stato scritto dall'app si vede subito.
     */
    internal val pending: Boolean,
  ) : FluidActionState() {
    /** Un lavoro che si vede subito: indeterminato senza [progress], con la percentuale con. */
    constructor(progress: (() -> Float)? = null) : this(progress, pending = false)

    override fun equals(other: Any?): Boolean =
      other is Working && other.progress === progress && other.pending == pending

    override fun hashCode(): Int = 31 * System.identityHashCode(progress) + pending.hashCode()

    override fun toString(): String = "Working(${if (progress == null) "indeterminato" else "con avanzamento"}" +
      "${if (pending) ", sotto soglia" else ""})"
  }

  /** Riuscito: l'icona dell'esito (la spunta), poi di nuovo a riposo dopo la tenuta del preset. */
  data object Succeeded : FluidActionState()

  /**
   * Non riuscito: l'icona del fallimento nella tinta d'errore, e **solo il contenuto** trema; il vetro resta fermo e i
   * vicini non saltano (scelta di Alessio del 21 novembre 2026).
   */
  data object Failed : FluidActionState()

  /** Annullato: la faccia di riposo; TalkBack dice che è stato annullato. */
  data object Cancelled : FluidActionState()
}

/** Se il tasto in questo stato risponde al tocco: no solo mentre lavora. */
val FluidActionState.acceptsTap: Boolean get() = this !is FluidActionState.Working

// ================================================================================================================
// La tenuta dell'esito
// ================================================================================================================

/** Quanto resta l'esito prima di tornare a riposo: quanto dice il preset, un tempo dato, o finché l'app lo chiede. */
@Immutable
sealed class FluidActionHold {
  /** Il tempo del preset ([FluidActionPreset]): per esempio ≈1 s la spunta di Copia. */
  data object Preset : FluidActionHold()

  /** Un tempo dato, uguale per tutti gli esiti. [Duration.ZERO]: si torna subito a riposo. */
  data class For(val duration: Duration) : FluidActionHold() {
    init {
      require(!duration.isNegative()) { "tenuta negativa: $duration" }
    }
  }

  /** L'esito resta finché l'app chiama [FluidActionRunner.reset] o fa ripartire il lavoro. */
  data object UntilReset : FluidActionHold()
}

// ================================================================================================================
// Il profilo: i numeri del tasto con esito
// ================================================================================================================

/**
 * I numeri del tasto con esito, tutti qui (regola comune del piano: ogni numero di movimento sta in un profilo con la
 * sua fonte e la sua fiducia). Le tenute per preset sono nei preset ([FluidActionPreset]); i loro valori stanno qui.
 */
object FluidActionDefaults {
  /**
   * Sotto questa durata il lavoro non si vede: il tasto si spegne e mostra subito l'esito. **300 ms**: regola di progetto
   * di R8 (`ricerca/componenti-dinamici.md`), confermata dal piano (Bersagli, «soglia oltre la quale si mostra il
   * lavoro»). Fiducia: regola, non una misura di Apple.
   */
  val MinWorking: Duration = 300.milliseconds

  internal const val MinWorkingMs: Long = 300L
}

/**
 * I numeri di gusto del tasto con esito che non sono soglie dell'app: le tenute, la scossa del fallimento, l'anello.
 * **A occhio**, scelti in F6B.3a il 21 novembre 2026, da confermare con Alessio sul tablet (F6B.14), salvo dove detto.
 */
internal object FluidActionProfile {
  /** La spunta di Copia: «≈1 s» (piano, punto 3; F6B.0 §6: scelta Fluid, nessun riferimento Apple). */
  const val CopySuccessMs: Long = 1_000L

  /** La spunta di Invia, Salva: 1,5 s, abbastanza per leggerla con la coda dell'occhio. A occhio. */
  const val SuccessMs: Long = 1_500L

  /** La spunta di Scarica e Carica: 2 s, un lavoro lungo merita un esito che resta di più. A occhio. */
  const val TransferSuccessMs: Long = 2_000L

  /** Il fallimento resta più della riuscita: va notato (HIG Feedback). 2,5 s. A occhio. */
  const val FailureMs: Long = 2_500L

  /** Il preferito rifiutato torna vuoto più in fretta: 1,5 s. A occhio. */
  const val FavoriteFailureMs: Long = 1_500L

  /** L'annullato non cambia faccia: resta quanto basta perché TalkBack lo dica, 0,8 s. A occhio. */
  const val CancelledMs: Long = 800L

  /**
   * La scossa del fallimento: solo il contenuto, in orizzontale, smorzata. Ampiezza 0,14 della taglia dell'icona (≈4 dp
   * su 28), tre mezzi periodi in 420 ms, smorzamento esponenziale. A occhio (F6B.0 §6: nessun tempo Apple per la
   * scossa); è la stessa famiglia del Wiggle di WWDC24 10188 (0,074 D al primo picco), più netta perché dice «no».
   */
  const val ShakeAmplitude: Float = 0.14f
  const val ShakeMs: Long = 420L
  const val ShakeHalfPeriods: Int = 3

  /**
   * La scossa parte quando la faccia del fallimento si legge: a 0,7 T dello scambio sfocato (R7, T = 300 ms), cioè dopo
   * ≈210 ms. Partita col cambio, tremava una sfocatura: sui fotogrammi del banco la X si spostava di 0,7 dp al più, contro
   * i 3,9 dp del colmo (F6B.3a, primo giro). A occhio.
   */
  const val ShakeDelayFraction: Float = 0.7f

  /**
   * L'anello: diametro 1,3 volte la taglia dell'icona (36 dp attorno a un'icona da 28, dentro il vetro da 46), tratto
   * 0,075 della taglia ma non meno di 1,5 dp, la pista sotto all'arco al 22% dell'inchiostro. L'icona al lavoro sta a
   * 0,7 della taglia, perché l'anello le giri attorno senza toccarla. A occhio.
   */
  const val RingDiameter: Float = 1.3f
  const val RingStroke: Float = 0.075f
  const val RingMinStrokeDp: Float = 1.5f
  const val RingTrackAlpha: Float = 0.22f
  const val WorkingIconScale: Float = 0.7f

  /** L'arco del lavoro indeterminato: un quarto abbondante di giro, un giro al secondo (`UIActivityIndicatorView`, a occhio). */
  const val SpinnerSweep: Float = 0.28f
  const val SpinnerTurnMs: Int = 1_000

  /**
   * Con il movimento ridotto l'anello non gira: è intero, a questa frazione dell'inchiostro (più piena della pista), e dice
   * «sto lavorando» senza moto. A occhio.
   */
  const val ReducedRingAlpha: Float = 0.6f

  /** Le cifre nell'anello occupano al più questa frazione del diametro interno. */
  const val DigitsFill: Float = 0.74f

  /**
   * TalkBack dice l'avanzamento a passi del 25%: con un'area viva, a ogni punto sarebbe un fiume di annunci. A occhio,
   * da provare con TalkBack in F6B.14.
   */
  const val SpokenProgressStep: Float = 0.25f
}

// ================================================================================================================
// Le parole
// ================================================================================================================

/**
 * Le parole di un'azione per TalkBack: il nome a riposo, e cosa dice al lavoro e a ogni esito («Invio in corso…»,
 * «Inviato», «Invio non riuscito»). Il tasto le dice con un'area viva (`liveRegion`), così l'esito si sente anche senza
 * il fuoco sul tasto.
 */
@Immutable
data class FluidActionWords(
  val idle: String,
  val working: String,
  val succeeded: String,
  val failed: String,
  val cancelled: String,
)

/**
 * Le parole dei preset, **in italiano di serie** come [dev.antigravity.fluidengine.ui.fluid.FluidLabels]: un'app tradotta
 * le passa una volta alla radice con [LocalFluidActionLabels] ([english] per l'inglese). Un preset su misura porta le sue.
 */
@Immutable
data class FluidActionLabels(
  val send: FluidActionWords = FluidActionWords("Invia", "Invio in corso…", "Inviato", "Invio non riuscito", "Invio annullato"),
  val save: FluidActionWords = FluidActionWords(
    "Salva", "Salvataggio in corso…", "Salvato", "Salvataggio non riuscito", "Salvataggio annullato",
  ),
  val copy: FluidActionWords = FluidActionWords("Copia", "Copia in corso…", "Copiato", "Copia non riuscita", "Copia annullata"),
  val download: FluidActionWords = FluidActionWords(
    "Scarica", "Scaricamento in corso…", "Scaricato", "Scaricamento non riuscito", "Scaricamento annullato",
  ),
  val upload: FluidActionWords = FluidActionWords(
    "Carica", "Caricamento in corso…", "Caricato", "Caricamento non riuscito", "Caricamento annullato",
  ),
  val favorite: FluidActionWords = FluidActionWords(
    "Aggiungi ai preferiti", "Aggiunta ai preferiti…", "Aggiunto ai preferiti", "Non aggiunto ai preferiti",
    "Aggiunta annullata",
  ),
  val unfavorite: FluidActionWords = FluidActionWords(
    "Rimuovi dai preferiti", "Rimozione dai preferiti…", "Rimosso dai preferiti", "Non rimosso dai preferiti",
    "Rimozione annullata",
  ),
  /** Come si dice l'avanzamento: «42%». */
  val percent: (Int) -> String = { "$it%" },
) {
  companion object {
    /** Le parole inglesi. */
    fun english(): FluidActionLabels = FluidActionLabels(
      send = FluidActionWords("Send", "Sending…", "Sent", "Couldn't send", "Sending canceled"),
      save = FluidActionWords("Save", "Saving…", "Saved", "Couldn't save", "Saving canceled"),
      copy = FluidActionWords("Copy", "Copying…", "Copied", "Couldn't copy", "Copy canceled"),
      download = FluidActionWords("Download", "Downloading…", "Downloaded", "Couldn't download", "Download canceled"),
      upload = FluidActionWords("Upload", "Uploading…", "Uploaded", "Couldn't upload", "Upload canceled"),
      favorite = FluidActionWords(
        "Add to favorites", "Adding to favorites…", "Added to favorites", "Couldn't add to favorites", "Adding canceled",
      ),
      unfavorite = FluidActionWords(
        "Remove from favorites", "Removing from favorites…", "Removed from favorites", "Couldn't remove from favorites",
        "Removing canceled",
      ),
      percent = { "$it%" },
    )
  }
}

/** Le parole dei preset per l'albero: di serie in italiano. */
val LocalFluidActionLabels: ProvidableCompositionLocal<FluidActionLabels> = staticCompositionLocalOf { FluidActionLabels() }

// ================================================================================================================
// I preset
// ================================================================================================================

/** Quale preset: la chiave con cui F6B.3b sceglie il glifo animato ([FluidActionGlyphs]). */
internal enum class FluidActionKind { Send, Save, Copy, Download, Upload, Favorite, Custom }

/**
 * Un tasto con esito già pensato: le icone di ogni stato, le parole per TalkBack, quanto resta l'esito e come si mostra
 * il lavoro. I cinque della prima versione (scelta di Alessio dell'11 novembre 2026) sono [Send], [Save], [Copy],
 * [Download] e [Upload], più [Favorite]; un tasto su misura si fa con [custom].
 *
 * Le icone sono quelle dell'engine, al tratto; il cambio fra uno stato e l'altro è lo scambio sfocato di R7 (F3.4). I
 * glifi animati veri (l'aeroplanino che parte, la spunta che si disegna) li porta F6B.3b, senza cambiare questa API.
 */
@Immutable
class FluidActionPreset internal constructor(
  internal val kind: FluidActionKind,
  /** L'icona a riposo. */
  val idle: ImageVector,
  /** L'icona dentro l'anello mentre lavora (di solito quella a riposo). */
  val working: ImageVector,
  /** L'icona della riuscita. */
  val success: ImageVector,
  /** L'icona del fallimento, nella tinta d'errore. */
  val failure: ImageVector,
  internal val words: (FluidActionLabels) -> FluidActionWords,
  internal val successHoldMs: Long,
  internal val failureHoldMs: Long,
  internal val cancelledHoldMs: Long = FluidActionProfile.CancelledMs,
  /** Falso: il lavoro non si vede mai (Copia, che è istantanea), si vede solo l'esito. */
  internal val showsWork: Boolean = true,
  /** Con l'avanzamento le cifre stanno nell'anello, al posto dell'icona (Scarica, Carica). */
  internal val percentInRing: Boolean = false,
  /** Ottimistico (Preferito): la faccia della riuscita si vede subito, al tocco; se l'app rifiuta torna indietro. */
  internal val optimistic: Boolean = false,
) {
  override fun equals(other: Any?): Boolean = other is FluidActionPreset && other.kind == kind && other.idle == idle &&
    other.working == working && other.success == success && other.failure == failure &&
    other.successHoldMs == successHoldMs && other.failureHoldMs == failureHoldMs &&
    other.cancelledHoldMs == cancelledHoldMs && other.showsWork == showsWork && other.percentInRing == percentInRing &&
    other.optimistic == optimistic && (kind != FluidActionKind.Custom || other.words == words)

  override fun hashCode(): Int = ((kind.hashCode() * 31 + idle.hashCode()) * 31 + success.hashCode()) * 31 + failure.hashCode()

  override fun toString(): String = "FluidActionPreset($kind)"

  companion object {
    /** Invia: l'aeroplanino; al lavoro l'anello, poi la spunta; al fallimento «Riprova». */
    val Send: FluidActionPreset = FluidActionPreset(
      FluidActionKind.Send, FluidIcons.Send, FluidIcons.Send, FluidIcons.Check, FluidIcons.Refresh,
      { it.send }, FluidActionProfile.SuccessMs, FluidActionProfile.FailureMs,
    )

    /** Salva: l'anello sottile attorno all'icona (scelta di Alessio), poi la spunta; al fallimento una X. */
    val Save: FluidActionPreset = FluidActionPreset(
      FluidActionKind.Save, FluidIcons.Save, FluidIcons.Save, FluidIcons.Check, FluidIcons.Close,
      { it.save }, FluidActionProfile.SuccessMs, FluidActionProfile.FailureMs,
    )

    /** Copia: nessun lavoro, l'icona diventa spunta per ≈1 s (piano, punto 3). */
    val Copy: FluidActionPreset = FluidActionPreset(
      FluidActionKind.Copy, FluidIcons.ContentCopy, FluidIcons.ContentCopy, FluidIcons.Check, FluidIcons.Close,
      { it.copy }, FluidActionProfile.CopySuccessMs, FluidActionProfile.FailureMs, showsWork = false,
    )

    /** Scarica: con l'avanzamento la percentuale nell'anello; poi la spunta. */
    val Download: FluidActionPreset = FluidActionPreset(
      FluidActionKind.Download, FluidIcons.Download, FluidIcons.Download, FluidIcons.Check, FluidIcons.Error,
      { it.download }, FluidActionProfile.TransferSuccessMs, FluidActionProfile.FailureMs, percentInRing = true,
    )

    /** Carica: come Scarica, verso l'alto. */
    val Upload: FluidActionPreset = FluidActionPreset(
      FluidActionKind.Upload, FluidIcons.CloudUpload, FluidIcons.CloudUpload, FluidIcons.Check, FluidIcons.Error,
      { it.upload }, FluidActionProfile.TransferSuccessMs, FluidActionProfile.FailureMs, percentInRing = true,
    )

    /** Preferito, non ancora scelto: [favorite] con `selected = false`. */
    val Favorite: FluidActionPreset = favorite(selected = false)

    /**
     * Preferito, ottimistico (piano, punto 3): al tocco il cuore si riempie subito; se l'app rifiuta torna com'era con la
     * scossa. [selected] è lo stato dell'app: col cuore pieno l'azione è «Rimuovi dai preferiti». Finito il lavoro
     * l'esecutore torna subito a riposo, e l'app, che ha cambiato [selected], mostra la faccia nuova senza un altro
     * scambio.
     */
    fun favorite(selected: Boolean): FluidActionPreset {
      val empty = FluidIcons.Favorite
      val full = FluidIcons.Filled.Favorite
      return FluidActionPreset(
        FluidActionKind.Favorite, idle = if (selected) full else empty, working = if (selected) empty else full,
        success = if (selected) empty else full, failure = if (selected) full else empty,
        words = if (selected) ({ it.unfavorite }) else ({ it.favorite }),
        successHoldMs = 0L, failureHoldMs = FluidActionProfile.FavoriteFailureMs, showsWork = false, optimistic = true,
      )
    }

    /**
     * Un tasto con esito su misura: le sue icone e le sue parole, con i tempi e i modi dei preset.
     *
     * @param working l'icona dentro l'anello; di serie quella a riposo.
     * @param successHold quanto resta la riuscita (di serie 1,5 s), [failureHold] il fallimento (2,5 s).
     * @param showsWork falso per un'azione istantanea: si vede solo l'esito.
     * @param percentInRing vero perché, con l'avanzamento, le cifre stiano nell'anello al posto dell'icona.
     */
    fun custom(
      idle: ImageVector,
      success: ImageVector,
      failure: ImageVector,
      words: FluidActionWords,
      working: ImageVector = idle,
      successHold: Duration = FluidActionProfile.SuccessMs.milliseconds,
      failureHold: Duration = FluidActionProfile.FailureMs.milliseconds,
      showsWork: Boolean = true,
      percentInRing: Boolean = false,
    ): FluidActionPreset {
      require(!successHold.isNegative() && !failureHold.isNegative()) { "tenuta negativa" }
      return FluidActionPreset(
        FluidActionKind.Custom, idle, working, success, failure, { words },
        successHold.inWholeMilliseconds, failureHold.inWholeMilliseconds,
        showsWork = showsWork, percentInRing = percentInRing,
      )
    }
  }
}

/** Le parole del preset, prese da [labels]. */
internal fun FluidActionPreset.wordsIn(labels: FluidActionLabels): FluidActionWords = words(labels)

/** Quanto resta [outcome] con la tenuta [hold] (ms); [Long.MAX_VALUE] finché lo si chiede. Pura. */
internal fun fluidActionHoldMs(preset: FluidActionPreset?, hold: FluidActionHold, outcome: FluidActionState): Long = when (hold) {
  FluidActionHold.UntilReset -> Long.MAX_VALUE
  is FluidActionHold.For -> hold.duration.inWholeMilliseconds
  FluidActionHold.Preset -> when (outcome) {
    FluidActionState.Succeeded -> preset?.successHoldMs ?: FluidActionProfile.SuccessMs
    FluidActionState.Failed -> preset?.failureHoldMs ?: FluidActionProfile.FailureMs
    FluidActionState.Cancelled -> preset?.cancelledHoldMs ?: FluidActionProfile.CancelledMs
    else -> 0L
  }
}

// ================================================================================================================
// Ciò che il tasto mostra, dice e fa sentire: pura
// ================================================================================================================

/** Il lavoro della faccia: nessuno, l'anello che gira, l'arco che avanza. */
internal enum class FluidActionRing { None, Indeterminate, Determinate }

/** L'inchiostro della faccia: quello del posto, o la tinta d'errore. */
internal enum class FluidActionInk { Content, Error }

/** Che momento è, per le parole e per l'aptica. */
internal enum class FluidActionPhase { Idle, Working, Succeeded, Failed, Cancelled }

/**
 * Ciò che il tasto mostra in uno stato. Due facce uguali sono lo stesso contenuto: il tasto non le scambia. Quando la
 * faccia cambia, il contenuto si scambia sfocato (R7), o col glifo animato di F6B.3b. L'avanzamento non è nella faccia:
 * lo legge l'anello a ogni fotogramma.
 *
 * @property icon l'icona, o null quando le cifre dell'avanzamento ne prendono il posto ([digits]).
 * @property iconScale la scala dell'icona nel suo riquadro: [FluidActionProfile.WorkingIconScale] dentro l'anello.
 */
@Immutable
internal data class FluidActionFace(
  val kind: FluidActionKind,
  val icon: ImageVector?,
  val ring: FluidActionRing = FluidActionRing.None,
  val digits: Boolean = false,
  val ink: FluidActionInk = FluidActionInk.Content,
  val iconScale: Float = 1f,
)

/** La fase di [state] per [preset]: l'ottimistico al lavoro dice già la riuscita. Pura. */
internal fun fluidActionPhase(preset: FluidActionPreset, state: FluidActionState): FluidActionPhase = when (state) {
  FluidActionState.Idle -> FluidActionPhase.Idle
  is FluidActionState.Working -> if (preset.optimistic) FluidActionPhase.Succeeded else FluidActionPhase.Working
  FluidActionState.Succeeded -> FluidActionPhase.Succeeded
  FluidActionState.Failed -> FluidActionPhase.Failed
  FluidActionState.Cancelled -> FluidActionPhase.Cancelled
}

/**
 * La faccia di [state] per [preset]. Pura.
 *
 * - Il lavoro sotto soglia ([FluidActionState.Working] dall'esecutore, prima di [FluidActionDefaults.MinWorking]) e quello
 *   di un preset che non lo mostra (Copia) hanno la faccia di riposo: così un lavoro breve mostra **solo** l'esito.
 * - L'ottimistico (Preferito) al lavoro ha già la faccia della riuscita.
 * - L'annullato ha la faccia di riposo: niente da scambiare.
 */
internal fun fluidActionFace(preset: FluidActionPreset, state: FluidActionState): FluidActionFace {
  val rest = FluidActionFace(preset.kind, preset.idle)
  return when (state) {
    FluidActionState.Idle, FluidActionState.Cancelled -> rest
    FluidActionState.Succeeded -> FluidActionFace(preset.kind, preset.success)
    FluidActionState.Failed -> FluidActionFace(preset.kind, preset.failure, ink = FluidActionInk.Error)
    is FluidActionState.Working -> when {
      preset.optimistic -> FluidActionFace(preset.kind, preset.success)
      state.pending || !preset.showsWork -> rest
      state.progress == null -> FluidActionFace(preset.kind, preset.working, FluidActionRing.Indeterminate,
        iconScale = FluidActionProfile.WorkingIconScale)
      preset.percentInRing -> FluidActionFace(preset.kind, null, FluidActionRing.Determinate, digits = true)
      else -> FluidActionFace(preset.kind, preset.working, FluidActionRing.Determinate,
        iconScale = FluidActionProfile.WorkingIconScale)
    }
  }
}

/**
 * Cosa dice TalkBack in [state]: il nome dell'azione a riposo, poi «Invio in corso…», «Inviato», «Invio non riuscito».
 * [name] è il nome che l'app ha dato al tasto (vince sul nome del preset solo a riposo). Pura.
 */
internal fun fluidActionSpoken(preset: FluidActionPreset, state: FluidActionState, labels: FluidActionLabels, name: String? = null): String {
  val words = preset.wordsIn(labels)
  // Sotto soglia il tasto è già spento: dice che lavora anche se la faccia è ancora quella di riposo.
  return when (fluidActionPhase(preset, state)) {
    FluidActionPhase.Idle -> name ?: words.idle
    FluidActionPhase.Working -> words.working
    FluidActionPhase.Succeeded -> words.succeeded
    FluidActionPhase.Failed -> words.failed
    FluidActionPhase.Cancelled -> words.cancelled
  }
}

/** L'avanzamento detto da TalkBack, a passi di [FluidActionProfile.SpokenProgressStep], o null. Pura. */
internal fun fluidActionSpokenProgress(progress: Float?, labels: FluidActionLabels): String? {
  if (progress == null || !progress.isFinite()) return null
  val step = FluidActionProfile.SpokenProgressStep
  val stepped = (kotlin.math.floor(progress.coerceIn(0f, 1f) / step + 1e-4f) * step).coerceIn(0f, 1f)
  return labels.percent((stepped * 100f).roundToInt())
}

/** Le cifre nell'anello: 0..100, arrotondate per difetto (un lavoro al 99,6% non dice 100). Pura. */
internal fun fluidActionPercent(progress: Float): Int =
  if (!progress.isFinite()) 0 else (progress.coerceIn(0f, 1f) * 100f + 1e-3f).toInt().coerceIn(0, 100)

/**
 * L'evento aptico del passaggio da [from] a [to] (l'aptica dell'engine, un evento per momento), o null. Pura.
 *
 * - Il tocco lo suona già il tasto ([FluidHapticEvent.PressClick] sul vetro che cresce, [FluidHapticEvent.Tap] nella
 *   barra): il lavoro che compare dopo 300 ms non vibra, sarebbe un secondo colpo staccato dal dito.
 * - Riuscito: [FluidHapticEvent.Success]. Non riuscito: [FluidHapticEvent.Error]. Annullato: [FluidHapticEvent.Close].
 * - L'ottimistico (Preferito) suona al tocco l'interruttore ([FluidHapticEvent.ToggleOn] o `ToggleOff`) e niente alla
 *   conferma, che non cambia ciò che si vede; il rifiuto è un [FluidHapticEvent.Error].
 */
internal fun fluidActionHaptic(preset: FluidActionPreset, from: FluidActionState, to: FluidActionState): FluidHapticEvent? {
  val before = fluidActionPhase(preset, from)
  val after = fluidActionPhase(preset, to)
  if (before == after) return null
  if (preset.optimistic) {
    return when {
      to is FluidActionState.Working -> if (preset.idle == FluidIcons.Filled.Favorite) FluidHapticEvent.ToggleOff else FluidHapticEvent.ToggleOn
      after == FluidActionPhase.Failed -> FluidHapticEvent.Error
      else -> null
    }
  }
  return when (after) {
    FluidActionPhase.Succeeded -> FluidHapticEvent.Success
    FluidActionPhase.Failed -> FluidHapticEvent.Error
    FluidActionPhase.Cancelled -> FluidHapticEvent.Close
    FluidActionPhase.Idle, FluidActionPhase.Working -> null
  }
}

// ================================================================================================================
// L'esecutore
// ================================================================================================================

/** Il tempo dell'esecutore: monotono, e continua attraverso la ricreazione dell'attività (la rotazione). */
internal val FluidActionClock: () -> Long = { SystemClock.elapsedRealtime() }

/**
 * Il lavoro in corso, visto da dentro la funzione che lo fa: l'avanzamento e il fallimento esplicito.
 */
@Stable
class FluidActionWork internal constructor(private val runner: FluidActionRunner) {
  /**
   * Quanto è fatto, 0..1, o null per un lavoro di cui non si sa: col primo valore l'anello passa da quello che gira
   * all'arco (e, nei preset che lo chiedono, alla percentuale). Si può scrivere da qualunque thread.
   */
  var progress: Float?
    get() = runner.progressOrNull()
    set(value) = runner.reportProgress(value)

  /** Il lavoro non è riuscito, senza un'eccezione da lanciare: il tasto mostra il fallimento. */
  fun fail(): Nothing = throw FluidActionFailure()
}

private class FluidActionFailure : RuntimeException("azione non riuscita") {
  override fun fillInStackTrace(): Throwable = this
}

/**
 * L'esecutore facoltativo dei tasti con esito: trasforma una funzione `suspend` negli stati di [FluidActionState] con le
 * regole di R8.
 *
 * - **Sotto ≈300 ms di lavoro si vede solo l'esito** ([minWorking], [FluidActionDefaults.MinWorking]): finché il lavoro
 *   è sotto soglia il tasto è già spento ma mostra ancora la faccia di riposo.
 * - **Il tasto è spento mentre lavora**: [run] non riparte finché il lavoro non finisce, e il tasto non risponde.
 * - **L'esito resta** per la tenuta ([hold]): quella del preset, un tempo dato, o finché l'app chiama [reset].
 *
 * La funzione riesce se finisce, non riesce se lancia (o chiama [FluidActionWork.fail]), è annullata da [cancel]. Un
 * `withTimeout` scaduto dentro di lei è un fallimento, non un annullamento.
 *
 * Si tiene dove vive lo stato: [rememberFluidActionRunner] in un composable (si salva da sé e sopravvive alla
 * rotazione con l'esito e il suo tempo), oppure `FluidActionRunner(viewModelScope, …)` in un ViewModel, dove anche il
 * lavoro sopravvive alla rotazione.
 *
 * @param scope dove gira il lavoro.
 * @param clock il tempo, in ms, monotono: di serie `SystemClock.elapsedRealtime()`.
 */
@Stable
class FluidActionRunner(
  private val scope: CoroutineScope,
  /** Il preset del tasto: dice le tenute e se il lavoro si mostra. Si può cambiare (il Preferito che si riempie). */
  var preset: FluidActionPreset? = null,
  /** La tenuta dell'esito. */
  var hold: FluidActionHold = FluidActionHold.Preset,
  /** Sotto questa durata il lavoro non si vede. */
  var minWorking: Duration = FluidActionDefaults.MinWorking,
  private val clock: () -> Long = FluidActionClock,
) {
  /** Lo stato da dare al tasto. */
  var state: FluidActionState by mutableStateOf(FluidActionState.Idle)
    private set

  /** Se un lavoro è in corso (anche sotto soglia). */
  val isRunning: Boolean get() = runToken != null

  /** Quando l'esito in mostra torna a riposo, sull'orologio [clock]; null se non c'è un esito a tempo. */
  internal var outcomeUntil: Long? = null
    private set

  /** La corsa in corso: un gettone nuovo per ogni [run], così una corsa vecchia non scrive mai sopra una nuova. */
  private var runToken: Any? = null
  private var work: Job? = null
  private var holdJob: Job? = null
  private val progressState = mutableFloatStateOf(0f)
  private var determinate by mutableStateOf(false)
  private val reader: () -> Float = { progressState.floatValue }

  /**
   * Fa partire [block]. Falso (e non fa niente) se un lavoro è già in corso: il tasto spento non accoda un secondo invio.
   */
  fun run(block: suspend FluidActionWork.() -> Unit): Boolean {
    if (runToken != null) return false
    clearOutcome()
    determinate = false
    progressState.floatValue = 0f
    val token = Any()
    runToken = token
    val shows = preset?.showsWork != false
    state = FluidActionState.Working(null, pending = true)
    val handle = FluidActionWork(this)
    val threshold = minWorking
    val job = scope.launch {
      val reveal = if (shows) launch {
        delay(threshold)
        if (runToken === token && state is FluidActionState.Working) {
          state = FluidActionState.Working(currentReader(), pending = false)
        }
      } else null
      val outcome = try {
        handle.block()
        FluidActionState.Succeeded
      } catch (cancel: CancellationException) {
        // Un `withTimeout` scaduto dentro il lavoro lancia una CancellationException a lavoro ancora vivo: è un fallimento.
        if (isActive) FluidActionState.Failed else {
          reveal?.cancel()
          if (runToken === token) finish(FluidActionState.Cancelled)
          throw cancel
        }
      } catch (error: Throwable) {
        FluidActionState.Failed
      }
      reveal?.cancel()
      if (runToken === token) finish(outcome)
    }
    // Un ambito già chiuso non fa partire il lavoro: senza questo il tasto resterebbe spento per sempre.
    job.invokeOnCompletion { if (runToken === token) finish(FluidActionState.Cancelled) }
    if (runToken === token) work = job
    return true
  }

  /** Annulla il lavoro in corso: il tasto dice «annullato» e torna a riposo. */
  fun cancel() {
    work?.cancel()
  }

  /** Torna a riposo subito, senza aspettare la tenuta. Non ferma un lavoro in corso (per quello [cancel]). */
  fun reset() {
    if (runToken != null) return
    clearOutcome()
    state = FluidActionState.Idle
  }

  internal fun progressOrNull(): Float? = if (determinate) progressState.floatValue else null

  internal fun reportProgress(value: Float?) {
    if (value == null) {
      if (!determinate) return
      determinate = false
      (state as? FluidActionState.Working)?.let { if (!it.pending) state = FluidActionState.Working(null, pending = false) }
      return
    }
    progressState.floatValue = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    if (determinate) return
    determinate = true
    // Il primo valore: l'anello che gira diventa l'arco (una faccia nuova); sotto soglia resta tutto com'è.
    (state as? FluidActionState.Working)?.let { if (!it.pending) state = FluidActionState.Working(reader, pending = false) }
  }

  private fun currentReader(): (() -> Float)? = if (determinate) reader else null

  private fun finish(outcome: FluidActionState) {
    runToken = null
    work = null
    val holdMs = fluidActionHoldMs(preset, hold, outcome)
    show(outcome, holdMs)
  }

  private fun show(outcome: FluidActionState, holdMs: Long) {
    clearOutcome()
    if (holdMs <= 0L) {
      state = FluidActionState.Idle
      return
    }
    state = outcome
    if (holdMs == Long.MAX_VALUE) return
    outcomeUntil = clock() + holdMs
    holdJob = scope.launch {
      delay(holdMs)
      if (state === outcome && runToken == null) {
        state = FluidActionState.Idle
        outcomeUntil = null
      }
    }
  }

  private fun clearOutcome() {
    holdJob?.cancel()
    holdJob = null
    outcomeUntil = null
  }

  /** Ciò che si salva: l'esito in mostra e fin quando resta. Un lavoro in corso non si salva (è morto con l'ambito). */
  internal fun snapshot(): List<Long>? {
    val outcome = when (state) {
      FluidActionState.Succeeded -> 1L
      FluidActionState.Failed -> 2L
      FluidActionState.Cancelled -> 3L
      else -> return null
    }
    return listOf(outcome, outcomeUntil ?: Long.MAX_VALUE)
  }

  /**
   * Riprende [saved] dopo la ricreazione: l'esito torna con il tempo che gli restava; se è già scaduto, il tasto è a
   * riposo. Un lavoro che era in corso è finito con la composizione che lo portava: il tasto torna a riposo, non resta
   * un anello che gira per sempre.
   */
  internal fun restore(saved: List<Long>?) {
    if (saved == null || saved.size != 2) return
    val outcome = when (saved[0]) {
      1L -> FluidActionState.Succeeded
      2L -> FluidActionState.Failed
      3L -> FluidActionState.Cancelled
      else -> return
    }
    val until = saved[1]
    if (until == Long.MAX_VALUE) {
      show(outcome, Long.MAX_VALUE)
      return
    }
    val left = until - clock()
    if (left > 0L) show(outcome, left) else state = FluidActionState.Idle
  }

  companion object {
    /** Il [Saver] di un esecutore, che rinasce in [scope] con [create]. */
    internal fun saver(scope: CoroutineScope, create: (CoroutineScope) -> FluidActionRunner): Saver<FluidActionRunner, Any> =
      Saver(
        save = { runner -> runner.snapshot() ?: emptyList<Long>() },
        restore = { saved ->
          @Suppress("UNCHECKED_CAST")
          create(scope).also { runner -> runner.restore((saved as? List<Long>)?.takeIf { it.size == 2 }) }
        },
      )
  }
}

/**
 * Un [FluidActionRunner] che vive con la composizione e si salva da sé: dopo una rotazione l'esito in mostra torna col
 * tempo che gli restava. Il lavoro gira nell'ambito della composizione: se la composizione se ne va (la pagina chiusa,
 * l'attività ricreata) il lavoro è annullato. Un lavoro che deve sopravvivere alla rotazione va in un ViewModel:
 * `FluidActionRunner(viewModelScope, preset)`.
 *
 * ```
 * val send = rememberFluidActionRunner(FluidActionPreset.Send)
 * FluidActionButton(FluidActionPreset.Send, send.state, onClick = { send.run { api.send(draft) } })
 * ```
 */
@Composable
fun rememberFluidActionRunner(
  preset: FluidActionPreset? = null,
  hold: FluidActionHold = FluidActionHold.Preset,
  minWorking: Duration = FluidActionDefaults.MinWorking,
): FluidActionRunner {
  val scope = rememberCoroutineScope()
  val runner = rememberSaveable(saver = FluidActionRunner.saver(scope) { FluidActionRunner(it, preset, hold, minWorking) }) {
    FluidActionRunner(scope, preset, hold, minWorking)
  }
  // Valori semplici, non stato: li legge solo il prossimo `run`.
  runner.preset = preset
  runner.hold = hold
  runner.minWorking = minWorking
  return runner
}

/** Il nome di uno stato per i test e le tracce. */
internal val FluidActionState.label: String get() = when (this) {
  FluidActionState.Idle -> "riposo"
  is FluidActionState.Working -> if (pending) "lavoro sotto soglia" else if (progress == null) "lavoro" else "lavoro con avanzamento"
  FluidActionState.Succeeded -> "riuscito"
  FluidActionState.Failed -> "non riuscito"
  FluidActionState.Cancelled -> "annullato"
}
