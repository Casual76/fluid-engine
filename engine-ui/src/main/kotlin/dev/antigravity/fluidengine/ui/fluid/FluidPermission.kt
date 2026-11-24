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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
) {
  /** Lo stato di adesso: si aggiorna dopo il dialogo e, da solo, al ritorno dalle impostazioni. */
  var status: FluidPermissionStatus by mutableStateOf(FluidPermissionStatus.NeverAsked)
    internal set

  /** La memoria è letta: prima, [status] non distingue ancora «mai chiesto» da «negato per sempre». */
  var isLoaded: Boolean by mutableStateOf(false)
    internal set

  internal val namespace = FluidGlassNamespace()
  internal val source = FluidTransitionSource(FluidPermissionSourceId, namespace)
  internal var prompt: PermissionPrompt? by mutableStateOf(null)
  internal var explained = false
  internal var asked = false
  private var pending: (() -> Unit)? = null
  internal var launch: ((Array<String>) -> Unit)? = null
  internal var settings: (() -> Unit)? = null
  internal var check: (() -> Triple<Boolean, Boolean, Boolean>)? = null
  internal var requestable = true
  internal var hasExplanation = true
  internal var onResult: (FluidPermissionStatus) -> Unit = {}

  /**
   * Il tocco del tasto: se il permesso c'è (anche in parte) [onGranted] parte subito; altrimenti la prima volta nasce la
   * spiegazione dal tasto, poi il dialogo di Android, e se l'utente consente [onGranted] parte da solo. Da negato si
   * chiede direttamente; da negato per sempre la spiegazione offre «Apri Impostazioni».
   */
  fun request(onGranted: () -> Unit = {}) {
    scope.launch {
      ensureLoaded()
      refresh()
      when (fluidPermissionStep(status, explained, hasExplanation, requestable)) {
        FluidPermissionStep.Proceed -> onGranted()
        FluidPermissionStep.Explain -> {
          pending = onGranted
          explained = true
          memory.write(FluidFirstUseKeys.permissionExplained(permission.id), "1")
          prompt = PermissionPrompt.Explain
        }
        FluidPermissionStep.AskSystem -> {
          pending = onGranted
          askSystem()
        }
        FluidPermissionStep.OpenSettings -> {
          pending = null
          prompt = PermissionPrompt.Settings
        }
      }
    }
  }

  /** Concesso in parte (le foto «solo alcune»): mostra di nuovo la scelta di Android per aggiungerne altre. */
  fun requestMore() {
    if (status == FluidPermissionStatus.Partial) askSystem()
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
  }

  private fun askSystem() {
    val permissions = permission.permissions().toTypedArray()
    if (permissions.isEmpty()) {
      prompt = PermissionPrompt.Settings
      return
    }
    launch?.invoke(permissions)
  }

  internal fun onSystemResult() {
    val (granted, partial, rationale) = check?.invoke() ?: return
    val previous = status
    val next = fluidPermissionAfterRequest(previous, granted, partial, rationale)
    status = next
    if (next != FluidPermissionStatus.NeverAsked && !asked) {
      asked = true
      scope.launch { memory.write(FluidFirstUseKeys.permissionAsked(permission.id), "1") }
    }
    val action = pending
    pending = null
    if (next == FluidPermissionStatus.Granted || next == FluidPermissionStatus.Partial) action?.invoke()
    onResult(next)
  }

  internal suspend fun ensureLoaded() {
    if (isLoaded) return
    asked = memory.read(FluidFirstUseKeys.permissionAsked(permission.id)) == "1"
    explained = memory.read(FluidFirstUseKeys.permissionExplained(permission.id)) == "1"
    isLoaded = true
    refresh()
  }
}

/**
 * Un permesso chiesto nel punto giusto (F8.11): dal tasto che ne ha bisogno.
 *
 * ```kotlin
 * val mic = rememberFluidPermission(FluidPermission.Microphone, reason = "Serve per dettare la nota mentre tieni premuto.")
 * FluidButton("Detta", onClick = { mic.request { startDictation() } }, modifier = Modifier.fluidPermissionSource(mic))
 * ```
 *
 * **La macchina a stati** ([FluidPermissionStatus]): mai chiesto, concesso, concesso in parte (le foto «solo alcune» di
 * Android 14), negato, negato per sempre. Android non distingue «mai chiesto» da «negato per sempre»: l'engine lo ricorda
 * nella memoria della prima volta ([LocalFluidFirstUseMemory], `EngineFirstUseStore` di `engine-storage`).
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
 */
@Composable
fun rememberFluidPermission(
  permission: FluidPermission,
  reason: String,
  title: String? = null,
  explain: Boolean = true,
  memory: FluidFirstUseMemory = LocalFluidFirstUseMemory.current,
  onResult: (FluidPermissionStatus) -> Unit = {},
): FluidPermissionState {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val state = remember(permission, memory) { FluidPermissionState(permission, memory, scope) }
  val latestResult by rememberUpdatedState(onResult)
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    state.onSystemResult()
  }
  state.launch = { launcher.launch(it) }
  state.requestable = permission.requestable()
  state.hasExplanation = explain
  state.onResult = { latestResult(it) }
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
