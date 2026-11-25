package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.foundation.FluidConditionPhrases
import dev.antigravity.fluidengine.foundation.SyncState
import dev.antigravity.fluidengine.foundation.SyncStatus
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionButton
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.emptyBackdrop
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionPreset
import dev.antigravity.fluidengine.ui.fluid.action.FluidActionWords
import dev.antigravity.fluidengine.ui.fluid.action.rememberFluidActionRunner
import dev.antigravity.fluidengine.ui.fluid.icons.Check
import dev.antigravity.fluidengine.ui.fluid.icons.CloudOff
import dev.antigravity.fluidengine.ui.fluid.icons.Error
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Sync
import dev.antigravity.fluidengine.ui.fluid.icons.Wifi
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import dev.antigravity.fluidengine.ui.haptics.LocalFluidHaptics
import kotlinx.coroutines.delay

// ================================================================================================================
// La capsula della rete (F8.20, `stato-rete-e-offline`)
//
// Apple non ha un componente pubblico: e' il comportamento di Mail e di Note quando manca la rete, sopra NWPathMonitor.
// Qui una capsula di vetro che galleggia sul contenuto dice «Offline · 3 modifiche in attesa», al ritorno della rete
// «Di nuovo in linea · sincronizzo», poi «Tutto aggiornato», e sparisce. Al tocco nasce dalla capsula un pannello (il
// popover dalla sorgente di F5B.1) con l'ultima sincronizzazione, cosa aspetta e «Sincronizza ora».
//
// La capsula non legge la rete da se': `engine-ui` non conosce `engine-net`. L'app le passa `online` (di solito da
// `EngineNetwork.states`, che ha gia' la tenuta contro le micro-cadute) e lo stato della sincronizzazione (`SyncStatus`,
// con `pending`: `FluidSync.status` di `engine-sync` lo da' cosi' com'e').
// ================================================================================================================

/**
 * Le fasi della capsula. Non e' un `enum` (regola 3.11).
 *
 * - [Hidden]: in linea, niente da dire;
 * - [Offline]: nessuna rete;
 * - [NoInternet]: c'e' una rete ma non porta a internet (il Wi-Fi d'albergo);
 * - [BackOnline]: la rete e' tornata, si sincronizza (o non c'era niente da mandare);
 * - [Synced]: la sincronizzazione al ritorno e' finita bene: «Tutto aggiornato», poi sparisce.
 */
@Immutable
class FluidConnectionPhase private constructor(val name: String) {
  /** La capsula si vede. */
  val visible: Boolean get() = this != Hidden

  /** Senza internet (la rete manca o non porta fuori). */
  val offline: Boolean get() = this == Offline || this == NoInternet

  override fun equals(other: Any?): Boolean = other is FluidConnectionPhase && other.name == name
  override fun hashCode(): Int = name.hashCode()
  override fun toString(): String = "FluidConnectionPhase($name)"

  companion object {
    val Hidden: FluidConnectionPhase = FluidConnectionPhase("hidden")
    val Offline: FluidConnectionPhase = FluidConnectionPhase("offline")
    val NoInternet: FluidConnectionPhase = FluidConnectionPhase("no-internet")
    val BackOnline: FluidConnectionPhase = FluidConnectionPhase("back-online")
    val Synced: FluidConnectionPhase = FluidConnectionPhase("synced")
  }
}

/** I tempi della capsula: a occhio (25 novembre 2026), sul ritmo dei brevi avvisi di Mail («Aggiornato adesso»). */
object FluidConnectionCapsuleDefaults {
  /** Quanto resta «Tutto aggiornato» prima di sparire. */
  const val SyncedHoldMillis: Long = 1_600L

  /** Quanto resta «Di nuovo in linea» quando non c'e' niente da sincronizzare. */
  const val BackOnlineHoldMillis: Long = 2_000L

  /**
   * Il tetto di «Di nuovo in linea · sincronizzo»: una sincronizzazione che dura di piu' continua senza la capsula (lo
   * stato resta nell'indicatore della barra e nell'avviso, `FluidSyncIndicator` e `FluidSyncNotice`).
   */
  const val BackOnlineMaxMillis: Long = 10_000L

  /** L'altezza della capsula: 36 dp, come la pillola di attivita' compatta; il bersaglio del tocco e' 44 dp. */
  val Height = 36.dp

  /** Il bersaglio del tocco, la base dell'accessibilita'. */
  val TouchTarget = 44.dp
}

/**
 * La macchina della capsula, pura: la fase per lo stato della rete ([online], [connected]) e della sincronizzazione
 * ([status]), sapendo la fase di prima. Le attese (quanto resta «Tutto aggiornato») le fa chi la usa.
 */
internal object FluidConnectionRule {
  fun next(previous: FluidConnectionPhase, online: Boolean, connected: Boolean, status: SyncStatus): FluidConnectionPhase {
    if (!online) return if (connected) FluidConnectionPhase.NoInternet else FluidConnectionPhase.Offline
    return when (previous) {
      FluidConnectionPhase.Offline, FluidConnectionPhase.NoInternet -> FluidConnectionPhase.BackOnline
      FluidConnectionPhase.BackOnline -> when {
        status.state == SyncState.SYNCING || status.pending > 0 -> FluidConnectionPhase.BackOnline
        status.state == SyncState.ERROR || status.state == SyncState.PARTIAL -> FluidConnectionPhase.Hidden
        else -> FluidConnectionPhase.Synced
      }
      else -> previous
    }
  }

  /** Vero se al ritorno c'e' qualcosa da sincronizzare: «sincronizzo» dopo «Di nuovo in linea». */
  fun syncing(status: SyncStatus): Boolean = status.state == SyncState.SYNCING || status.pending > 0
}

/**
 * Le parole della capsula e del suo pannello, di serie in italiano ([english] per l'inglese). Si cambiano alla radice con
 * [LocalFluidConnectionLabels].
 */
@Immutable
data class FluidConnectionLabels(
  val offline: String = "Offline",
  val noInternet: String = "Nessuna connessione a internet",
  val backOnline: String = "Di nuovo in linea",
  val syncing: String = "sincronizzo",
  val synced: String = "Tutto aggiornato",
  /** «3 modifiche in attesa». */
  val pending: (Int) -> String = { n -> if (n == 1) "una modifica in attesa" else "$n modifiche in attesa" },
  /** Il separatore fra le due parti della capsula. */
  val separator: String = " · ",
  val panelOffline: String = "Sei offline",
  val panelNoInternet: String = "La rete non porta a internet",
  val panelOnline: String = "In linea",
  val lastSync: String = "Ultima sincronizzazione",
  val waiting: String = "In attesa",
  val nothingWaiting: String = "Niente",
  /** Il «3 modifiche» del pannello. */
  val changes: (Int) -> String = { n -> if (n == 1) "Una modifica" else "$n modifiche" },
  val offlineDetail: String = "Le modifiche restano sul telefono e partono da sole quando torna la rete.",
  val noInternetDetail: String = "Sei collegato a una rete che non porta a internet (per esempio un Wi-Fi che chiede l'accesso). Le modifiche aspettano qui.",
  val onlineDetail: String = "Le modifiche partono da sole.",
  val syncNow: String = "Sincronizza ora",
  val syncingNow: String = "Sincronizzo…",
  val synced2: String = "Fatto",
  val syncFailed: String = "Non riuscito",
  /** L'azione di TalkBack sulla capsula: apre il pannello. */
  val showDetails: String = "Mostra i dettagli",
  /** Le parole dei motivi delle condizioni del telefono (la usano anche l'aggiornamento in-app e gli stati pronti). */
  val conditions: FluidConditionPhrases = FluidConditionPhrases(),
  /**
   * F8.20b · Il valore di «Ultima sincronizzazione» nel pannello: solo il tempo, mai «Aggiornato adesso», che offline con
   * le modifiche in attesa diceva il contrario della riga sotto (Tab S9, 25/11).
   */
  val lastSyncJustNow: String = "meno di un minuto fa",
  val lastSyncMinutes: (Int) -> String = { "$it min fa" },
  val lastSyncHours: (Int) -> String = { n -> if (n == 1) "un'ora fa" else "$n ore fa" },
  val lastSyncOn: (String) -> String = { "il $it" },
  val lastSyncNever: String = "mai",
) {
  companion object {
    /** Le parole inglesi. */
    fun english(): FluidConnectionLabels = FluidConnectionLabels(
      offline = "Offline",
      noInternet = "No internet connection",
      backOnline = "Back online",
      syncing = "syncing",
      synced = "All up to date",
      pending = { n -> if (n == 1) "1 change waiting" else "$n changes waiting" },
      panelOffline = "You're offline",
      panelNoInternet = "This network has no internet",
      panelOnline = "Online",
      lastSync = "Last sync",
      waiting = "Waiting",
      nothingWaiting = "Nothing",
      changes = { n -> if (n == 1) "1 change" else "$n changes" },
      offlineDetail = "Your changes stay on the phone and go out on their own when you're back online.",
      noInternetDetail = "You're on a network that doesn't reach the internet (for example a Wi-Fi that asks you to sign in). Your changes wait here.",
      onlineDetail = "Your changes go out on their own.",
      syncNow = "Sync now",
      syncingNow = "Syncing…",
      synced2 = "Done",
      syncFailed = "Failed",
      showDetails = "Show details",
      conditions = FluidConditionPhrases.english(),
      lastSyncJustNow = "less than a minute ago",
      lastSyncMinutes = { "$it min ago" },
      lastSyncHours = { n -> if (n == 1) "1 hour ago" else "$n hours ago" },
      lastSyncOn = { "on $it" },
      lastSyncNever = "never",
    )
  }
}

/**
 * Il valore di «Ultima sincronizzazione» nel pannello della capsula: quanto tempo fa, senza dire «aggiornato» (offline
 * con modifiche in attesa non lo e'). Durante una sincronizzazione, «Sincronizzo…». Pura.
 */
fun fluidConnectionLastSync(
  status: SyncStatus,
  labels: FluidConnectionLabels = FluidConnectionLabels(),
  nowEpochMillis: Long = System.currentTimeMillis(),
  zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
): String {
  if (status.state == dev.antigravity.fluidengine.foundation.SyncState.SYNCING) return labels.syncingNow
  val last = status.lastSuccessfulSyncEpochMillis ?: return labels.lastSyncNever
  val age = (nowEpochMillis - last).coerceAtLeast(0L)
  val minute = 60_000L
  val hour = 60 * minute
  return when {
    age < minute -> labels.lastSyncJustNow
    age < hour -> labels.lastSyncMinutes((age / minute).toInt())
    age < 24 * hour -> labels.lastSyncHours((age / hour).toInt())
    else -> labels.lastSyncOn(
      java.time.format.DateTimeFormatter.ofLocalizedDateTime(java.time.format.FormatStyle.SHORT)
        .withZone(zone).format(java.time.Instant.ofEpochMilli(last)),
    )
  }
}

/** Le parole della capsula della rete, di serie in italiano. */
val LocalFluidConnectionLabels = staticCompositionLocalOf { FluidConnectionLabels() }

/** I tag per le prove. */
object FluidConnectionCapsuleTags {
  const val Capsule: String = "fluid:connection:capsule"
  const val Panel: String = "fluid:connection:panel"
  const val SyncNow: String = "fluid:connection:sync-now"
}

/** Lo stato della capsula: la fase, e da quando. Uno per capsula. */
@Stable
internal class FluidConnectionCapsuleState {
  var phase: FluidConnectionPhase by mutableStateOf(FluidConnectionPhase.Hidden)
}

/** La frase della capsula per [phase] (con le modifiche in attesa di [status]). Pura. */
internal fun fluidConnectionPhrase(phase: FluidConnectionPhase, status: SyncStatus, labels: FluidConnectionLabels): String {
  val pending = status.pending
  return when (phase) {
    FluidConnectionPhase.Offline -> if (pending > 0) labels.offline + labels.separator + labels.pending(pending) else labels.offline
    FluidConnectionPhase.NoInternet ->
      if (pending > 0) labels.noInternet + labels.separator + labels.pending(pending) else labels.noInternet
    FluidConnectionPhase.BackOnline ->
      if (FluidConnectionRule.syncing(status)) labels.backOnline + labels.separator + labels.syncing else labels.backOnline
    FluidConnectionPhase.Synced -> labels.synced
    else -> ""
  }
}

/** Cosa mostra la capsula in un fotogramma: la chiave del cambio sfocato e' la frase. */
@Immutable
private data class FluidConnectionFace(val phase: FluidConnectionPhase, val text: String, val spinning: Boolean)

/**
 * **La capsula della rete** (F8.20, `stato-rete-e-offline`): una capsula di vetro che galleggia sul contenuto e dice
 * «Offline · 3 modifiche in attesa»; quando la rete torna, «Di nuovo in linea · sincronizzo», poi «Tutto aggiornato», e
 * sparisce. Al tocco, dalla capsula nasce un pannello con l'ultima sincronizzazione, cosa aspetta e «Sincronizza ora».
 *
 * ```
 * val online by remember { EngineNetwork.states(context).map { it.online } }.collectAsState(initial = true)
 * val status by sync.status.collectAsState()                        // engine-sync: pending compreso
 * Box(Modifier.fillMaxSize()) {
 *   Content()
 *   FluidConnectionCapsule(online, status, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp),
 *     onSyncNow = { sync.syncAll() })
 * }
 * ```
 *
 * - **Non lampeggia**: la tenuta contro le micro-cadute e' di chi da' [online] (`EngineNetwork.states` la ha, 2,5 s); la
 *   capsula aggiunge solo le uscite a tempo ([FluidConnectionCapsuleDefaults]).
 * - **Vetro dell'engine**: un pezzo di un `FluidGlassContainer`, compare e scompare per lente
 *   ([FluidGlassTransition.Materialize]); la frase che cambia entra sfocata ([FluidGlassContentTransition]: la cambia
 *   l'app, non il dito).
 * - **Ferma non ridisegna**: niente gira a riposo; il giro dell'icona c'e' solo mentre sincronizza.
 * - **TalkBack** sente la frase (regione viva: «Offline» si annuncia da solo) e ha l'azione «Mostra i dettagli»; il
 *   bersaglio e' di 44 dp. Col movimento ridotto il vetro e la frase cambiano a dissolvenza (lo fa il piano).
 * - **Il pannello** e' un popover di vetro che nasce dalla capsula: serve un host dei modali alla radice
 *   (`FluidGlassModalHost`), come per ogni popover dell'engine.
 *
 * @param online c'e' internet.
 * @param status lo stato della sincronizzazione: le modifiche in attesa ([SyncStatus.pending]), se sincronizza, l'ultima
 *   riuscita.
 * @param connected c'e' una rete anche senza internet: la capsula dice «Nessuna connessione a internet» invece di
 *   «Offline».
 * @param onSyncNow «Sincronizza ora» nel pannello (un tasto con esito: riesce se finisce, non riesce se lancia). Null:
 *   il tasto non c'e'.
 * @param backdrop cosa rifrange il vetro: **quello della pagina**, che la capsula riceve stando nell'`overlay` della
 *   [FluidScreen] (`overlay = { glass -> FluidConnectionCapsule(…, backdrop = glass) }`). Senza un fondale (posata in un
 *   `Box` sopra il contenuto) il vetro non avrebbe niente da sfocare e resterebbe limpido sopra il testo: allora la
 *   capsula e' piena, come un vetro a cui il tetto della memoria nega la cattura (F7.18, F8.20b).
 */
@Composable
fun FluidConnectionCapsule(
  online: Boolean,
  status: SyncStatus,
  modifier: Modifier = Modifier,
  connected: Boolean = false,
  onSyncNow: (suspend () -> Unit)? = null,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
) {
  val labels = LocalFluidConnectionLabels.current
  val state = remember { FluidConnectionCapsuleState() }
  val latestStatus by rememberUpdatedState(status)
  // La fase: il cambio arriva col cambio degli ingressi, le uscite a tempo con un'attesa. Niente gira a ogni fotogramma.
  LaunchedEffect(online, connected, status.state, status.pending) {
    state.phase = FluidConnectionRule.next(state.phase, online, connected, latestStatus)
    when (state.phase) {
      FluidConnectionPhase.BackOnline -> {
        if (!FluidConnectionRule.syncing(latestStatus)) {
          delay(FluidConnectionCapsuleDefaults.BackOnlineHoldMillis)
          if (state.phase == FluidConnectionPhase.BackOnline) state.phase = FluidConnectionPhase.Hidden
        } else {
          delay(FluidConnectionCapsuleDefaults.BackOnlineMaxMillis)
          if (state.phase == FluidConnectionPhase.BackOnline) state.phase = FluidConnectionPhase.Hidden
        }
      }
      FluidConnectionPhase.Synced -> {
        delay(FluidConnectionCapsuleDefaults.SyncedHoldMillis)
        if (state.phase == FluidConnectionPhase.Synced) state.phase = FluidConnectionPhase.Hidden
      }
      else -> Unit
    }
  }
  val phase = state.phase
  val face = FluidConnectionFace(
    phase = phase,
    text = fluidConnectionPhrase(phase, status, labels),
    spinning = phase == FluidConnectionPhase.BackOnline && FluidConnectionRule.syncing(status),
  )
  val namespace = rememberFluidGlassNamespace()
  var panelOpen by remember { mutableStateOf(false) }
  // Nessun fondale da sfocare: la capsula piena (F8.20b). Sul Tab S9 il vetro limpido lasciava leggere il testo sotto.
  val solid = backdrop.backdrop === emptyBackdrop()
  val solidFill = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f)
  val haptics = LocalFluidHaptics.current

  FluidGlassContainer(modifier = modifier) {
    if (phase.visible) {
      Box(
        modifier = Modifier
          .fluidTransitionSource(CapsuleSourceId, namespace, FluidCapsuleShape)
          // Il bersaglio da 44 dp (il tocco e TalkBack): il vetro e' la capsula da 36 dp al centro.
          .heightIn(min = FluidConnectionCapsuleDefaults.TouchTarget)
          .testTag(FluidConnectionCapsuleTags.Capsule)
          .clickable(interactionSource = null, indication = null, role = Role.Button) {
            haptics.play(FluidHapticEvent.Tap)
            panelOpen = true
          }
          .clearAndSetSemantics {
            contentDescription = face.text
            role = Role.Button
            liveRegion = LiveRegionMode.Polite
            onClick(label = labels.showDetails) {
              panelOpen = true
              true
            }
          },
        contentAlignment = Alignment.Center,
      ) {
        Box(
          modifier = Modifier
            .fluidGlassId(CapsuleGlassId, namespace)
            .fluidGlassTransition(FluidGlassTransition.Materialize)
            // Il vetro dei pannelli, come `FluidFloatingCapsule`: la capsula porta una frase, non e' un controllo (F8.20b:
            // col vetro dei controlli il testo della pagina sotto restava al limite della lettura).
            .fluidGlassPanel(backdrop, GlassDefaults.floatingTint(), FluidCapsuleShape)
            .then(if (solid) Modifier.background(solidFill, FluidCapsuleShape) else Modifier)
            .heightIn(min = FluidConnectionCapsuleDefaults.Height)
            .widthIn(max = MaxCapsuleWidth)
            .padding(horizontal = 14.dp),
          contentAlignment = Alignment.Center,
        ) {
          FluidGlassContentTransition(targetState = face, key = { it.text }) { shown ->
            FluidConnectionFaceRow(shown)
          }
        }
      }
    }
  }

  FluidGlassModalPortal(
    item = if (panelOpen) FluidConnectionPanelModel(phase, status) else null,
    onDismissRequest = { panelOpen = false },
    paneTitle = if (phase.offline) labels.panelOffline else labels.panelOnline,
    source = FluidTransitionSource(CapsuleSourceId, namespace),
    compactAdaptation = FluidCompactAdaptation.Popover,
  ) { model ->
    FluidConnectionPanel(model, labels, online = online, onSyncNow = onSyncNow)
  }
}

/** Una riga della capsula: il segno e la frase. */
@Composable
private fun FluidConnectionFaceRow(face: FluidConnectionFace) {
  val ink = LocalContentColor.current
  Row(verticalAlignment = Alignment.CenterVertically) {
    when {
      face.spinning -> FluidSpinner(size = 14.dp, color = ink)
      face.phase == FluidConnectionPhase.Synced -> FluidIcon(FluidIcons.Check, null, size = 16.dp)
      face.phase == FluidConnectionPhase.NoInternet -> FluidIcon(FluidIcons.Wifi, null, size = 16.dp)
      face.phase == FluidConnectionPhase.BackOnline -> FluidIcon(FluidIcons.Sync, null, size = 16.dp)
      else -> FluidIcon(FluidIcons.CloudOff, null, size = 16.dp)
    }
    Spacer(Modifier.width(8.dp))
    Text(
      text = face.text,
      style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
      color = ink,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** Cosa mostra il pannello: la fase e lo stato di adesso (cambia sotto gli occhi se la rete torna a pannello aperto). */
@Immutable
private data class FluidConnectionPanelModel(val phase: FluidConnectionPhase, val status: SyncStatus)

/** Il pannello: com'e' la rete, l'ultima sincronizzazione, cosa aspetta, «Sincronizza ora». */
@Composable
private fun FluidConnectionPanel(
  model: FluidConnectionPanelModel,
  labels: FluidConnectionLabels,
  online: Boolean,
  onSyncNow: (suspend () -> Unit)?,
) {
  val ink = LocalContentColor.current
  val secondary = ink.copy(alpha = ink.alpha * 0.66f)
  val title = when (model.phase) {
    FluidConnectionPhase.Offline -> labels.panelOffline
    FluidConnectionPhase.NoInternet -> labels.panelNoInternet
    else -> labels.panelOnline
  }
  val detail = when (model.phase) {
    FluidConnectionPhase.Offline -> labels.offlineDetail
    FluidConnectionPhase.NoInternet -> labels.noInternetDetail
    else -> labels.onlineDetail
  }
  Column(
    modifier = Modifier
      .widthIn(min = 240.dp, max = 320.dp)
      .padding(16.dp)
      .testTag(FluidConnectionCapsuleTags.Panel),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      FluidIcon(if (model.phase.offline) FluidIcons.CloudOff else FluidIcons.Sync, null, size = 20.dp)
      Spacer(Modifier.width(10.dp))
      Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = ink)
    }
    FluidConnectionPanelRow(labels.lastSync, fluidConnectionLastSync(model.status, labels), ink, secondary)
    FluidConnectionPanelRow(
      labels.waiting,
      if (model.status.pending > 0) labels.changes(model.status.pending) else labels.nothingWaiting,
      ink,
      secondary,
    )
    Text(detail, style = MaterialTheme.typography.bodyMedium, color = secondary)
    if (onSyncNow != null) {
      val preset = remember(labels) {
        FluidActionPreset.custom(
          idle = FluidIcons.Sync,
          success = FluidIcons.Check,
          failure = FluidIcons.Error,
          words = FluidActionWords(
            idle = labels.syncNow,
            working = labels.syncingNow,
            succeeded = labels.synced2,
            failed = labels.syncFailed,
            cancelled = labels.syncNow,
          ),
        )
      }
      val runner = rememberFluidActionRunner(preset)
      val action by rememberUpdatedState(onSyncNow)
      FluidActionButton(
        preset = preset,
        state = runner.state,
        onClick = { runner.run { action() } },
        label = labels.syncNow,
        enabled = online,
        modifier = Modifier.testTag(FluidConnectionCapsuleTags.SyncNow),
      )
    }
  }
}

@Composable
private fun FluidConnectionPanelRow(
  label: String,
  value: String,
  ink: androidx.compose.ui.graphics.Color,
  secondary: androidx.compose.ui.graphics.Color,
) {
  Row(
    modifier = Modifier.semantics(mergeDescendants = true) { },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(label, style = MaterialTheme.typography.bodyMedium, color = secondary, modifier = Modifier.weight(1f))
    Spacer(Modifier.width(12.dp))
    Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = ink)
  }
}

private object CapsuleSourceIdKey {
  override fun toString(): String = "fluid:connection"
}

private val CapsuleSourceId: Any = CapsuleSourceIdKey
private const val CapsuleGlassId = "fluid:connection:glass"

/** La capsula non supera questa larghezza: la frase si accorcia coi puntini. */
private val MaxCapsuleWidth = 320.dp
