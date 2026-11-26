package dev.antigravity.fluidengine.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/**
 * L'esito di «Aggiungi alla Home», trattato con prudenza: non tutti i launcher accettano la richiesta, e la risposta non
 * sempre torna (scheda `vetrina-dei-widget`, «rischi»). Per questo l'engine non si fida della sola risposta del
 * launcher: guarda anche se il widget c'è davvero (`AppWidgetManager.getAppWidgetIds`), quando l'app torna in primo
 * piano.
 */
sealed interface FluidWidgetPinState {
  /** Niente in corso. */
  data object Idle : FluidWidgetPinState

  /** La richiesta è sul launcher: l'utente sceglie lì. */
  data object Asking : FluidWidgetPinState

  /** Il widget è sulla Home. [appWidgetId] è il suo id, se il launcher l'ha detto o si è visto. */
  data class Added(val appWidgetId: Int?) : FluidWidgetPinState

  /** L'utente è tornato e il widget non c'è: ha chiuso la richiesta. Si può riprovare. */
  data object NotAdded : FluidWidgetPinState

  /** Il launcher ha detto di sì, ma non ha mostrato niente: si spiega la strada a mano. */
  data object NoAnswer : FluidWidgetPinState

  /** Il launcher non permette di aggiungere widget da un'app (`isRequestPinAppWidgetSupported` falso, o la richiesta rifiutata). */
  data object Unsupported : FluidWidgetPinState
}

/**
 * Le parole della vetrina, in italiano semplice. Un'app in un'altra lingua passa le sue; [appName] entra nella strada a
 * mano («cerca «Pampa Widgets»»).
 */
data class FluidWidgetShowcaseStrings(
  val add: String = "Aggiungi alla Home",
  val asking: String = "Conferma sulla Home.",
  val added: String = "Aggiunto alla Home.",
  val notAdded: String = "Non aggiunto. Puoi riprovare quando vuoi.",
  /** La strada a mano, come la guida di iPhone («Add a widget»): la dice [manualSteps]. */
  val manualSteps: (appName: String) -> String = { app ->
    "Tieni premuto su uno spazio vuoto della Home, tocca «Widget» e cerca «$app»."
  },
  val noAnswer: String = "Il launcher non ha risposto.",
  val unsupported: String = "Questo launcher non permette di aggiungere widget da un'app.",
  val preview: (title: String, size: String) -> String = { title, size -> "Anteprima del widget $title, taglia $size" },
  val sizeOf: (label: String, columns: Int, rows: Int) -> String = { label, c, r -> "$label · $c×$r" },
  /** Android non sa chiedere una taglia: senza un provider per taglia il widget arriva nella sua, e si allarga sulla Home. */
  val resizeHint: String = "Arriva nella taglia di serie: sulla Home tienilo premuto e trascina i bordi per cambiarla.",
  val nextSize: String = "Taglia dopo",
  val previousSize: String = "Taglia prima",
)

/** La frase da mostrare sotto il tasto per [state], o `null` se non c'è niente da dire. */
fun FluidWidgetShowcaseStrings.messageFor(state: FluidWidgetPinState, appName: String): String? = when (state) {
  FluidWidgetPinState.Idle -> null
  FluidWidgetPinState.Asking -> asking
  is FluidWidgetPinState.Added -> added
  FluidWidgetPinState.NotAdded -> notAdded
  FluidWidgetPinState.NoAnswer -> "$noAnswer ${manualSteps(appName)}"
  FluidWidgetPinState.Unsupported -> "$unsupported ${manualSteps(appName)}"
}

/**
 * Il giro della richiesta al launcher, senza interfaccia: la vetrina lo usa con [rememberFluidWidgetPin], un'app che
 * disegna il suo tasto lo può usare da sola.
 *
 * 1. [request]: se il launcher non sa fare la richiesta, subito [FluidWidgetPinState.Unsupported]. Altrimenti si annotano
 *    i widget di [provider] già sulla Home e si chiede `requestPinAppWidget`, con un `PendingIntent` di ritorno (il
 *    launcher lo manda quando il widget è stato messo, con il suo id). Una richiesta rifiutata è «non supportato».
 * 2. Il ritorno del launcher ([onCallback]) porta subito a «Aggiunto».
 * 3. Se il ritorno non arriva, quando l'app torna in primo piano ([onResumed]) si guarda se c'è un widget nuovo; se non
 *    c'è si riguarda dopo [RecheckMillis] (il launcher a volte lo mette un attimo dopo aver chiuso il dialogo), poi
 *    «Non aggiunto».
 * 4. Se dopo [NoAnswerMillis] l'app non è mai andata in pausa, il launcher non ha mostrato niente: «Il launcher non ha
 *    risposto», con la strada a mano.
 * 5. Un ritorno che arriva tardi, anche dopo «Non aggiunto», porta lo stesso ad «Aggiunto».
 *
 * Tutti i metodi vanno chiamati dal filo principale: [state] è uno stato di Compose (regola degli stati, `regole.md`).
 */
@Stable
class FluidWidgetPin internal constructor(
  private val context: Context,
  /** Il provider del widget (la `GlanceAppWidgetReceiver` o l'`AppWidgetProvider` dell'app). */
  val provider: ComponentName,
  private val launcher: FluidWidgetLauncher = SystemWidgetLauncher(AppWidgetManager.getInstance(context)),
) {
  /** L'esito, per la vetrina. */
  var state: FluidWidgetPinState by mutableStateOf(FluidWidgetPinState.Idle)
    private set

  private var before: Set<Int> = emptySet()
  private var pausedSinceRequest = false
  internal var requestedAtMillis: Long = 0L
    private set

  /** Vero se il launcher sa ricevere la richiesta (Android 8 e un launcher che la accetta). */
  val isSupported: Boolean get() = launcher.isRequestPinSupported

  /** Chiede al launcher di mettere il widget sulla Home. */
  fun request(nowMillis: Long = System.currentTimeMillis()) {
    if (!isSupported) {
      state = FluidWidgetPinState.Unsupported
      return
    }
    before = installedIds()
    pausedSinceRequest = false
    requestedAtMillis = nowMillis
    val callback = PendingIntent.getBroadcast(
      context,
      provider.hashCode(),
      Intent(callbackAction(context)).setPackage(context.packageName).putExtra(ExtraProvider, provider.flattenToString()),
      // Modificabile: il launcher ci aggiunge EXTRA_APPWIDGET_ID. Esplicito col pacchetto, come chiede Android 14.
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )
    val accepted = try {
      launcher.requestPin(provider, callback)
    } catch (e: IllegalStateException) {
      // Android la lancia se l'app non e' in primo piano: per l'utente e' lo stesso «non si puo' da qui».
      false
    }
    state = if (accepted) FluidWidgetPinState.Asking else FluidWidgetPinState.Unsupported
  }

  /** Il launcher ha risposto: il widget [appWidgetId] è sulla Home. */
  fun onCallback(appWidgetId: Int?) {
    state = FluidWidgetPinState.Added(appWidgetId?.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID })
  }

  /** L'app è andata in pausa (il dialogo del launcher è sopra). */
  fun onPaused() {
    if (state == FluidWidgetPinState.Asking) pausedSinceRequest = true
  }

  /**
   * L'app è tornata in primo piano. [final] vero: è il secondo sguardo, se il widget non c'è si dice «Non aggiunto».
   * Torna vero se serve un secondo sguardo.
   */
  fun onResumed(final: Boolean = false): Boolean {
    if (state != FluidWidgetPinState.Asking || !pausedSinceRequest) return false
    val added = installedIds() - before
    if (added.isNotEmpty()) {
      state = FluidWidgetPinState.Added(added.max())
      return false
    }
    if (final) {
      state = FluidWidgetPinState.NotAdded
      return false
    }
    return true
  }

  /** È passato [NoAnswerMillis] dalla richiesta: se l'app non è mai andata in pausa, il launcher non ha mostrato niente. */
  fun onTimeout() {
    if (state == FluidWidgetPinState.Asking && !pausedSinceRequest) {
      val added = installedIds() - before
      state = if (added.isNotEmpty()) FluidWidgetPinState.Added(added.max()) else FluidWidgetPinState.NoAnswer
    }
  }

  /** Torna a [FluidWidgetPinState.Idle] (per esempio cambiando taglia). */
  fun reset() {
    if (state != FluidWidgetPinState.Asking) state = FluidWidgetPinState.Idle
  }

  /** I widget di [provider] sulla Home adesso. */
  fun installedIds(): Set<Int> = launcher.ids(provider)

  companion object {
    /** Il secondo sguardo dopo il ritorno: 1,5 s. A occhio, 26/11/2026 (il launcher Samsung mette il widget dopo l'animazione). */
    const val RecheckMillis: Long = 1_500

    /** Dopo quanto un launcher che non ha mostrato niente si considera muto: 4 s. A occhio, 26/11/2026. */
    const val NoAnswerMillis: Long = 4_000

    internal const val ExtraProvider = "dev.antigravity.fluidengine.widget.PROVIDER"

    /** L'azione del ritorno, una per app (il pacchetto davanti: due app con l'engine non si confondono). */
    internal fun callbackAction(context: Context): String = "${context.packageName}.fluid.WIDGET_PINNED"
  }
}

/**
 * Il giro di [FluidWidgetPin] legato alla schermata: il ricevitore del ritorno del launcher finché la schermata c'è, la
 * pausa e il ritorno in primo piano, il secondo sguardo e il tempo del launcher muto.
 */
@Composable
fun rememberFluidWidgetPin(provider: ComponentName): FluidWidgetPin {
  val context = LocalContext.current
  val launcher = LocalFluidWidgetLauncher.current
  val pin = remember(context, provider, launcher) {
    if (launcher != null) {
      FluidWidgetPin(context.applicationContext, provider, launcher)
    } else {
      FluidWidgetPin(context.applicationContext, provider)
    }
  }
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  var recheck by remember(pin) { mutableStateOf(0) }

  DisposableEffect(pin) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(c: Context, intent: Intent) {
        if (intent.getStringExtra(FluidWidgetPin.ExtraProvider) != provider.flattenToString()) return
        pin.onCallback(intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID))
      }
    }
    // Un ricevitore non esportato: il PendingIntent e' nostro, il launcher lo manda a nome dell'app.
    ContextCompat.registerReceiver(
      context.applicationContext,
      receiver,
      IntentFilter(FluidWidgetPin.callbackAction(context)),
      ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    onDispose { context.applicationContext.unregisterReceiver(receiver) }
  }

  DisposableEffect(lifecycle, pin) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_PAUSE -> pin.onPaused()
        Lifecycle.Event.ON_RESUME -> if (pin.onResumed()) recheck++
        else -> Unit
      }
    }
    lifecycle.addObserver(observer)
    onDispose { lifecycle.removeObserver(observer) }
  }

  LaunchedEffect(pin, recheck) {
    if (recheck > 0) {
      delay(FluidWidgetPin.RecheckMillis)
      pin.onResumed(final = true)
    }
  }

  val asking = pin.state == FluidWidgetPinState.Asking
  LaunchedEffect(pin, asking, pin.requestedAtMillis) {
    if (asking) {
      delay(FluidWidgetPin.NoAnswerMillis)
      pin.onTimeout()
    }
  }
  return pin
}

/**
 * Il launcher visto dalla vetrina: `AppWidgetManager` sul telefono. Interno: le prove ne passano uno finto con
 * [LocalFluidWidgetLauncher] per i launcher che non rispondono (quello di Robolectric accetta e risponde subito).
 */
internal interface FluidWidgetLauncher {
  val isRequestPinSupported: Boolean
  fun requestPin(provider: ComponentName, callback: PendingIntent): Boolean
  fun ids(provider: ComponentName): Set<Int>
}

internal class SystemWidgetLauncher(private val manager: AppWidgetManager) : FluidWidgetLauncher {
  override val isRequestPinSupported: Boolean get() = manager.isRequestPinAppWidgetSupported
  override fun requestPin(provider: ComponentName, callback: PendingIntent): Boolean =
    manager.requestPinAppWidget(provider, null, callback)
  override fun ids(provider: ComponentName): Set<Int> = manager.getAppWidgetIds(provider)?.toSet().orEmpty()
}

/** Il launcher delle prove; `null` (di serie) e' quello vero. */
internal val LocalFluidWidgetLauncher = staticCompositionLocalOf<FluidWidgetLauncher?> { null }
