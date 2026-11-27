package dev.antigravity.fluidengine.ui.fluid

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.antigravity.fluidengine.foundation.FluidFirstUseKeys
import dev.antigravity.fluidengine.foundation.FluidFirstUseMemory
import dev.antigravity.fluidengine.foundation.FluidPermissionStatus
import dev.antigravity.fluidengine.foundation.FluidPermissionStep
import dev.antigravity.fluidengine.foundation.fluidPermissionAfterRequest
import dev.antigravity.fluidengine.foundation.fluidPermissionStatus
import dev.antigravity.fluidengine.foundation.fluidPermissionStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// F8.11 · `permesso-nel-punto-giusto`: il permesso si chiede dal tasto che ne ha bisogno. Dal tasto nasce una spiegazione
// breve (l'avviso che prende il posto del tasto e ci rientra, il viaggio d'oro), poi il dialogo di Android; poi lo stato
// giusto, ricontrollato da solo al ritorno dalle impostazioni. Le linee guida di Apple («Requesting permission»): chiedere
// nel momento d'uso, dicendo il perché, e una schermata propria prima dell'avviso di sistema solo se serve davvero.

/**
 * Un permesso di Android come lo chiede un tasto: uno o più permessi del manifest, a seconda della versione di Android, e
 * quelli che da soli valgono «in parte» (le foto «solo alcune» di Android 14).
 *
 * I preset coprono i casi delle app Pampa; [of] fa il resto. L'app dichiara i permessi nel suo manifest: senza la
 * dichiarazione Android risponde «negato» senza mostrare niente.
 *
 * @property id il nome nella memoria della prima volta (cambiarlo fa dimenticare «chiesto» e «spiegato»).
 * @property name come lo chiamano i titoli di serie della spiegazione, con la preposizione («al microfono»: «Consentire
 *   l'accesso al microfono?»). Un'app in un'altra lingua passa i suoi titoli ([FluidFirstUseLabels.english] usa [id]).
 */
@Immutable
class FluidPermission private constructor(
  val id: String,
  val name: String,
  private val required: (sdk: Int) -> List<String>,
  private val partialOnly: (sdk: Int) -> List<String>,
  private val notifications: Boolean = false,
) {
  /** I permessi da chiedere su questa versione di Android: vuoto se non ce n'è uno da chiedere (notifiche prima del 13). */
  fun permissions(sdk: Int = Build.VERSION.SDK_INT): List<String> = required(sdk) + partialOnly(sdk)

  internal fun granted(context: Context, sdk: Int = Build.VERSION.SDK_INT): Boolean {
    if (notifications && sdk < 33) return NotificationManagerCompat.from(context).areNotificationsEnabled()
    val needed = required(sdk)
    return needed.isNotEmpty() && needed.all { context.isGranted(it) }
  }

  internal fun partiallyGranted(context: Context, sdk: Int = Build.VERSION.SDK_INT): Boolean {
    val partial = partialOnly(sdk)
    return partial.isNotEmpty() && partial.all { context.isGranted(it) } && !granted(context, sdk)
  }

  internal fun requestable(sdk: Int = Build.VERSION.SDK_INT): Boolean = required(sdk).isNotEmpty()

  /** L'azione delle impostazioni giuste: quelle delle notifiche per le notifiche, la pagina dell'app per il resto. */
  internal fun settingsIntent(context: Context): Intent =
    if (notifications) {
      Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }

  override fun equals(other: Any?): Boolean = other is FluidPermission && other.id == id
  override fun hashCode(): Int = id.hashCode()
  override fun toString(): String = "FluidPermission($id)"

  companion object {
    /** Il microfono (`RECORD_AUDIO`): dettare, registrare una nota, parlare con l'assistente. */
    val Microphone: FluidPermission = of("microphone", "al microfono", Manifest.permission.RECORD_AUDIO)

    /** La fotocamera (`CAMERA`): scansionare, il visore AR. */
    val Camera: FluidPermission = of("camera", "alla fotocamera", Manifest.permission.CAMERA)

    /**
     * Le notifiche: `POST_NOTIFICATIONS` da Android 13; prima non si chiedono, e se l'utente le ha spente si accendono
     * nelle impostazioni delle notifiche dell'app.
     */
    val Notifications: FluidPermission = FluidPermission(
      id = "notifications", name = "alle notifiche",
      required = { sdk -> if (sdk >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList() },
      partialOnly = { emptyList() },
      notifications = true,
    )

    /**
     * Le foto: `READ_MEDIA_IMAGES` da Android 13 (`READ_EXTERNAL_STORAGE` prima) e, da Android 14,
     * `READ_MEDIA_VISUAL_USER_SELECTED`, che da solo vuol dire **solo alcune foto** ([FluidPermissionStatus.Partial]).
     */
    val Photos: FluidPermission = media("photos", "alle foto", video = false)

    /** Come [Photos], anche coi video (`READ_MEDIA_VIDEO`). */
    val PhotosAndVideos: FluidPermission = media("photos-videos", "alle foto e ai video", video = true)

    /** La posizione approssimativa (`ACCESS_COARSE_LOCATION`): il meteo, una fermata vicina. */
    val ApproximateLocation: FluidPermission = of("location-coarse", "alla posizione", Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Uno o più permessi qualsiasi, tutti necessari, su ogni versione. */
    fun of(id: String, name: String, vararg permissions: String): FluidPermission {
      val list = permissions.toList()
      return FluidPermission(id, name, required = { list }, partialOnly = { emptyList() })
    }

    @Suppress("InlinedApi")
    private fun media(id: String, name: String, video: Boolean) = FluidPermission(
      id = id, name = name,
      required = { sdk ->
        if (sdk >= 33) {
          listOfNotNull(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO.takeIf { video })
        } else {
          listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
      },
      partialOnly = { sdk -> if (sdk >= 34) listOf(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) else emptyList() },
    )
  }
}

private fun Context.isGranted(permission: String): Boolean =
  ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
  is Activity -> this
  is ContextWrapper -> baseContext.findActivity()
  else -> null
}

/** Cosa mostra la spiegazione che nasce dal tasto. */
internal enum class PermissionPrompt { Explain, Settings }

/**
 * Lo stato di un permesso per un tasto: [status], [request] dal tasto, [requestMore] per le foto «solo alcune»,
 * [openSettings]. Si crea con [rememberFluidPermission]; il tasto porta [Modifier.fluidPermissionSource] in testa ai suoi
 * modificatori, così la spiegazione nasce da lui e ci rientra.
 */
@Stable
class FluidPermissionState internal constructor(
  /** Il permesso. */
  val permission: FluidPermission,
  private val memory: FluidFirstUseMemory,
  private val scope: CoroutineScope,
  // R2: la spiegazione aperta e le risposte senza dialogo sopravvivono alla rotazione e al processo ucciso.
  promptState: MutableState<PermissionPrompt?> = mutableStateOf(null),
  unansweredState: MutableIntState = mutableIntStateOf(0),
  actionDueState: MutableState<Boolean> = mutableStateOf(false),
) {
  /** Lo stato di adesso: si aggiorna dopo il dialogo e, da solo, al ritorno dalle impostazioni. */
  var status: FluidPermissionStatus by mutableStateOf(FluidPermissionStatus.NeverAsked)
    internal set

  /** La memoria è letta: prima, [status] non distingue ancora «mai chiesto» da «negato per sempre». */
  var isLoaded: Boolean by mutableStateOf(false)
    internal set

  internal val namespace = FluidGlassNamespace()
  internal val source = FluidTransitionSource(FluidPermissionSourceId, namespace)
  internal var prompt: PermissionPrompt? by promptState
  internal var explained = false
  internal var asked = false

  /**
   * Le risposte di fila senza permesso e senza spiegazione di Android (R2): la prima può essere il dialogo chiuso senza
   * scegliere; la seconda è un no vero, anche se Android non ha mostrato niente (negato per sempre prima dell'engine, o un
   * permesso che il manifest non dichiara).
   */
  private var unanswered by unansweredState
  private var pending: (() -> Unit)? = null

  /**
   * Un tocco aspetta il sì (la spiegazione o il dialogo di Android sono aperti). Sopravvive alla ricreazione, l'azione
   * no: dopo una ricreazione, al sì parte [grantedAction], quella dell'albero di adesso (R2, scelta B di Alessio).
   */
  private var actionDue by actionDueState

  /** `onGranted` di [rememberFluidPermission], riletto a ogni composizione: sempre quello dell'albero di adesso. */
  internal var grantedAction: (() -> Unit)? = null

  /** Un tocco in lavorazione (la memoria si legge) o il dialogo di Android aperto: un altro tocco non fa niente (R2). */
  private var requesting = false
  private var awaitingSystem = false
  internal var launch: ((Array<String>) -> Unit)? = null
  internal var settings: (() -> Unit)? = null
  internal var check: (() -> Triple<Boolean, Boolean, Boolean>)? = null
  internal var requestable = true
  internal var hasExplanation = true
  internal var onResult: (FluidPermissionStatus) -> Unit = {}

  /**
   * Il tocco del tasto: se il permesso c'è (anche in parte) l'azione parte subito; altrimenti la prima volta nasce la
   * spiegazione dal tasto, poi il dialogo di Android, e se l'utente consente l'azione parte da sola. Da negato si chiede
   * direttamente; da negato per sempre la spiegazione offre «Apri Impostazioni».
   *
   * L'azione è [onGranted], se c'è; altrimenti quella data a [rememberFluidPermission] (`onGranted`), che è anche
   * l'unica che parte se l'Activity si ricrea mentre la spiegazione o il dialogo di Android sono sopra (una rotazione,
   * il processo ucciso): [onGranted] è una funzione dell'albero di prima, e non parte più. Per un'azione che deve
   * partire comunque dopo il sì: `request()` senza argomenti e l'azione in `rememberFluidPermission(onGranted = …)`.
   *
   * Un tocco mentre la spiegazione o il dialogo di Android sono già aperti non fa niente: conta il primo, con la sua
   * azione.
   */
  fun request(onGranted: () -> Unit = NoAction) {
    if (requesting || awaitingSystem || prompt != null) return
    requesting = true
    scope.launch {
      try {
        ensureLoaded()
        refresh()
        when (fluidPermissionStep(status, explained, hasExplanation, requestable)) {
          FluidPermissionStep.Proceed -> if (onGranted !== NoAction) onGranted() else grantedAction?.invoke()
          FluidPermissionStep.Explain -> {
            pending = onGranted.takeUnless { it === NoAction }
            actionDue = true
            explained = true
            memory.writeSafely(FluidFirstUseKeys.permissionExplained(permission.id), "1")
            prompt = PermissionPrompt.Explain
          }
          FluidPermissionStep.AskSystem -> {
            pending = onGranted.takeUnless { it === NoAction }
            actionDue = true
            askSystem()
          }
          FluidPermissionStep.OpenSettings -> {
            pending = null
            actionDue = false
            prompt = PermissionPrompt.Settings
          }
        }
      } finally {
        requesting = false
      }
    }
  }

  /** Concesso in parte (le foto «solo alcune»): mostra di nuovo la scelta di Android per aggiungerne altre. */
  fun requestMore() {
    if (status == FluidPermissionStatus.Partial && !awaitingSystem) askSystem()
  }

  /** Apre le impostazioni giuste dell'app (quelle delle notifiche, per le notifiche). Al ritorno lo stato si rilegge. */
  fun openSettings() {
    prompt = null
    settings?.invoke()
  }

  /** Rilegge lo stato da Android (lo fa da solo alla ripresa dell'Activity). */
  fun refresh() {
    val (granted, partial, rationale) = check?.invoke() ?: return
    status = fluidPermissionStatus(granted, partial, asked, rationale)
  }

  internal fun continueFromExplanation() {
    prompt = null
    askSystem()
  }

  internal fun dismissPrompt() {
    prompt = null
    pending = null
    actionDue = false
  }

  private fun askSystem() {
    val permissions = permission.permissions().toTypedArray()
    if (permissions.isEmpty()) {
      prompt = PermissionPrompt.Settings
      return
    }
    val start = launch ?: return
    awaitingSystem = true
    start(permissions)
  }

  /**
   * La risposta del dialogo di Android: [results] è la mappa del contratto, vuota se la richiesta è stata interrotta
   * (l'Activity ricreata, un'altra richiesta in corso), che non è una risposta.
   */
  internal fun onSystemResult(results: Map<String, Boolean>) {
    awaitingSystem = false
    val (granted, partial, rationale) = check?.invoke() ?: return
    val previous = status
    if (results.isEmpty()) {
      pending = null
      actionDue = false
      refresh()
      onResult(status)
      return
    }
    var next = fluidPermissionAfterRequest(previous, granted, partial, rationale)
    // Né permesso né spiegazione dopo «mai chiesto»: il dialogo chiuso senza scegliere, oppure Android che dice no senza
    // mostrare niente. La seconda volta di fila è un no vero: negato per sempre, e la strada delle impostazioni subito,
    // perché il tocco non ha mostrato niente.
    val silentNo = next == FluidPermissionStatus.NeverAsked && ++unanswered >= 2
    if (next != FluidPermissionStatus.NeverAsked) unanswered = 0
    if (silentNo) next = FluidPermissionStatus.DeniedForever
    status = next
    if (next != FluidPermissionStatus.NeverAsked && !asked) {
      asked = true
      scope.launch { memory.writeSafely(FluidFirstUseKeys.permissionAsked(permission.id), "1") }
    }
    val action = pending
    val due = actionDue
    pending = null
    actionDue = false
    // L'azione del tocco se è ancora di questo albero; dopo una ricreazione quella di `rememberFluidPermission`.
    if ((next == FluidPermissionStatus.Granted || next == FluidPermissionStatus.Partial) && due) {
      if (action != null) action() else grantedAction?.invoke()
    }
    if (silentNo) prompt = PermissionPrompt.Settings
    onResult(next)
  }

  internal suspend fun ensureLoaded() {
    if (isLoaded) return
    // Una memoria che non risponde vale «non lo so»: mai chiesto e mai spiegato (R2).
    asked = memory.readSafely(FluidFirstUseKeys.permissionAsked(permission.id)).getOrNull() == "1"
    explained = memory.readSafely(FluidFirstUseKeys.permissionExplained(permission.id)).getOrNull() == "1"
    isLoaded = true
    refresh()
  }
}

/**
 * Un permesso chiesto nel punto giusto (F8.11): dal tasto che ne ha bisogno.
 *
 * ```kotlin
 * val mic = rememberFluidPermission(
 *   FluidPermission.Microphone,
 *   reason = "Serve per dettare la nota mentre tieni premuto.",
 *   onGranted = { startDictation() },
 * )
 * FluidButton("Detta", onClick = { mic.request() }, modifier = Modifier.fluidPermissionSource(mic))
 * ```
 *
 * L'azione in `onGranted` parte anche dopo una rotazione con il dialogo di Android sopra; `mic.request { … }` va bene
 * per un'azione che dopo una ricreazione può non partire.
 *
 * **La macchina a stati** ([FluidPermissionStatus]): mai chiesto, concesso, concesso in parte (le foto «solo alcune» di
 * Android 14), negato, negato per sempre. Android non distingue «mai chiesto» da «negato per sempre»: l'engine lo ricorda
 * nella memoria della prima volta ([LocalFluidFirstUseMemory], `EngineFirstUseStore` di `engine-storage`). Un dialogo che
 * torna senza permesso e senza spiegazione la prima volta conta come chiuso senza scegliere; la seconda volta di fila è
 * un no (Android non mostra più niente: negato per sempre prima dell'engine, o un permesso assente dal manifest), e la
 * spiegazione con «Apri Impostazioni» compare subito. La spiegazione aperta resiste alla rotazione e al processo ucciso.
 *
 * **La spiegazione** nasce dal tasto (l'avviso con la sorgente, che prende il posto del tasto e ci rientra) col perché
 * dell'app ([reason]), «Continua» porta al dialogo di Android e «Non ora» chiude. Si mostra **una volta**: dopo, anche dopo
 * un rifiuto, il tocco apre direttamente il dialogo. Da negato per sempre il tocco mostra la stessa spiegazione con
 * «Apri Impostazioni»; al ritorno dall'app delle impostazioni lo stato si rilegge da solo, e l'azione non parte da sola
 * (si tocca di nuovo). Senza un `FluidGlassModalHost` la spiegazione è il `Dialog` di piattaforma.
 *
 * @param reason il perché, in una frase dell'app: cosa fa con il permesso, non che «migliora l'esperienza».
 * @param title il titolo della spiegazione; di serie «Consentire l'accesso al microfono?» dalle etichette
 *   ([FluidFirstUseLabels.permissionTitle] col [FluidPermission.name]).
 * @param explain falso per non mostrare mai la spiegazione (il tasto dice già tutto): il primo tocco chiede subito.
 * @param onResult ogni risposta al dialogo di Android, con lo stato nuovo.
 * @param onGranted l'azione del tasto quando il permesso c'è (R2, scelta B di Alessio): parte al tocco se il permesso
 *   c'è già e dopo il sì, **anche se nel frattempo l'Activity si è ricreata** (una rotazione, il processo ucciso con la
 *   spiegazione o il dialogo di Android sopra), perché è sempre quella dell'albero di adesso. Un'azione data a
 *   [FluidPermissionState.request] vince su questa nello stesso albero; null, di serie: solo quella di `request`.
 */
@Composable
fun rememberFluidPermission(
  permission: FluidPermission,
  reason: String,
  title: String? = null,
  explain: Boolean = true,
  memory: FluidFirstUseMemory = LocalFluidFirstUseMemory.current,
  onResult: (FluidPermissionStatus) -> Unit = {},
  onGranted: (() -> Unit)? = null,
): FluidPermissionState {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val savedPrompt = rememberSaveable(permission.id) { mutableStateOf<PermissionPrompt?>(null) }
  val savedUnanswered = rememberSaveable(permission.id) { mutableIntStateOf(0) }
  val savedActionDue = rememberSaveable(permission.id) { mutableStateOf(false) }
  val state = remember(permission, memory) {
    FluidPermissionState(permission, memory, scope, savedPrompt, savedUnanswered, savedActionDue)
  }
  val latestResult by rememberUpdatedState(onResult)
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
    state.onSystemResult(results)
  }
  state.launch = { launcher.launch(it) }
  state.requestable = permission.requestable()
  state.hasExplanation = explain
  state.onResult = { latestResult(it) }
  state.grantedAction = onGranted
  state.check = {
    val activity = context.findActivity()
    val rationale = activity != null && permission.permissions().any { activity.shouldShowRequestPermissionRationale(it) }
    Triple(permission.granted(context), permission.partiallyGranted(context), rationale)
  }
  state.settings = {
    val intent = permission.settingsIntent(context)
    if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
      // Un produttore senza la pagina delle notifiche: quella dell'app c'è sempre.
      runCatching {
        context.startActivity(
          Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .apply { if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
      }
    }
  }
  LaunchedEffect(state) { state.ensureLoaded() }
  // Al ritorno (dalle impostazioni, da un'altra app, dal dialogo di sistema) lo stato si rilegge da solo.
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  DisposableEffect(lifecycle, state) {
    val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME && state.isLoaded) state.refresh() }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }

  val labels = LocalFluidFirstUseLabels.current
  val shown = state.prompt
  FluidAlert(
    item = shown,
    onDismissRequest = state::dismissPrompt,
    title = { prompt ->
      when (prompt) {
        PermissionPrompt.Explain -> title ?: labels.permissionTitle(permission)
        PermissionPrompt.Settings -> labels.permissionOffTitle(permission)
      }
    },
    message = { prompt ->
      when (prompt) {
        PermissionPrompt.Explain -> reason
        PermissionPrompt.Settings -> "$reason ${labels.permissionOffHint}"
      }
    },
    actions = { prompt ->
      when (prompt) {
        PermissionPrompt.Explain -> listOf(
          FluidAlertAction(labels.permissionNotNow, state::dismissPrompt),
          FluidAlertAction(labels.permissionContinue, state::continueFromExplanation, FluidAlertAction.Emphasis.Preferred),
        )
        PermissionPrompt.Settings -> listOf(
          FluidAlertAction(labels.permissionNotNow, state::dismissPrompt),
          FluidAlertAction(labels.permissionOpenSettings, state::openSettings, FluidAlertAction.Emphasis.Preferred),
        )
      }
    },
    source = state.source,
  )
  return state
}

/**
 * Il tasto da cui nasce la spiegazione del permesso [state]: in testa ai modificatori del tasto (prima di fondo e
 * contenuto), così mentre la spiegazione è in scena il tasto non si disegna e lei ci rientra. [shape] è il contorno del
 * tasto: di serie quello di un `FluidButton` medio.
 */
fun Modifier.fluidPermissionSource(
  state: FluidPermissionState,
  shape: Shape = FluidButtonDefaults.shape(FluidButtonSize.Medium),
): Modifier = fluidTransitionSource(FluidPermissionSourceId, state.namespace, shape)

private const val FluidPermissionSourceId = "fluid-permission"

/** L'azione di serie di [FluidPermissionState.request]: nessuna, e allora vale `onGranted` di [rememberFluidPermission]. */
private val NoAction: () -> Unit = {}
