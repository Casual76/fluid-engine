package dev.antigravity.fluidengine.ui.glass.backdrop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.core.content.ContextCompat
import java.util.Collections
import java.util.WeakHashMap

/**
 * F8.10, diagnostica: la **fotografia a riposo** dello stato dei vetri vivi, a richiesta, senza toccare i tempi dei
 * fotogrammi. Il log `FluidGlassFrame` scrive a ogni fotogramma e rallenta quanto basta a far sparire un difetto di tempi
 * (la «x» sfocata del foglio di condivisione: 2 aperture su 10 a log spento, nessuna su 10 col log acceso); questa invece
 * non scrive niente finché non la si chiede:
 *
 * ```
 * adb shell am broadcast -a dev.antigravity.fluidengine.DUMP_GLASS_STATE -p <pacchetto>
 * adb logcat -d -s FluidGlassState
 * ```
 *
 * Con `--es azione <prova>` fa invece una delle prove sullo stato fermo di [ExtraAction].
 *
 * Solo in un'app **debuggable** sul dispositivo: in release (e in Robolectric) [active] resta falso, nessun ricevitore si
 * registra, nessuna sonda si iscrive e le catene dei modificatori sono quelle di sempre. Si accende al primo vetro
 * attaccato ([install], da `DrawBackdropNode`). Tutto sul thread principale.
 */
internal object GlassStateDump {
  /** L'azione del broadcast che chiede la fotografia. */
  const val Action: String = "dev.antigravity.fluidengine.DUMP_GLASS_STATE"

  private const val Tag = "FluidGlassState"

  /** Vero in un'app debuggable sul dispositivo, dal primo vetro attaccato. Letto in composizione e in disegno. */
  var active: Boolean = false
    private set

  private var installed = false
  private var dumps = 0

  /** Le sonde vive. Deboli: una sonda se ne va con chi la possiede, anche senza [unregister]. */
  private val probes: MutableSet<Probe> = Collections.newSetFromMap(WeakHashMap())

  /** Chi sa descrivere il proprio stato in una riga. [now] è `System.currentTimeMillis()` della fotografia. */
  fun interface Probe {
    fun describe(now: Long): String
  }

  /**
   * L'extra del broadcast con una prova sullo stato fermo invece della fotografia. Ogni prova fa ridisegnare una parte sola,
   * per vedere quale conserva un fotogramma vecchio rimasto sullo schermo:
   *
   * - `vetri`: ogni vetro vivo si ridisegna, la cattura resta quella;
   * - `vetri-cattura`: ogni vetro vivo rifà la cattura e si ridisegna;
   * - `tasto`: il contenuto dei tasti di vetro si registra di nuovo;
   * - `foglio`: lo strato del contenuto dei fogli si registra di nuovo (proprietà e contenuto);
   * - `posa`: lo strato di posa di ogni vetro riscrive le sue proprietà (alfa e `renderEffect` comprese, vedi
   *   [forcePoseDefaults]) e registra di nuovo il contenuto; `posa-proprieta` e `posa-contenuto` fanno una sola delle due;
   * - `nascondi-vetro` / `nascondi-tasto`: i vetri su vetro non disegnano se stessi / i tasti di vetro spariscono del
   *   tutto (per vedere chi disegna davvero una zona); `mostra` li rimette.
   *
   * Il ridisegno passa da uno **stato letto in disegno** (i contatori qui sotto): un `invalidateDraw()` chiesto da fuori di un
   * fotogramma non fa ridisegnare un `DrawBackdropNode` (il tablet, 25 novembre: la finestra rendeva due fotogrammi e nessun
   * vetro si disegnava), mentre uno stato scritto fa ridisegnare chi l'ha letto, come per ogni stato di Compose.
   */
  const val ExtraAction: String = "azione"

  /** Un vetro che sa preparare la cattura nuova di `vetri-cattura`. Sul thread principale. */
  interface Actionable {
    fun recapture()
  }

  private val glassTick: MutableIntState = mutableIntStateOf(0)
  private val buttonTick: MutableIntState = mutableIntStateOf(0)
  private val sheetTick: MutableIntState = mutableIntStateOf(0)
  private val posePropertiesTick: MutableIntState = mutableIntStateOf(0)
  private val poseContentTick: MutableIntState = mutableIntStateOf(0)
  private val hideNestedGlass: MutableIntState = mutableIntStateOf(0)
  private val hideButtons: MutableIntState = mutableIntStateOf(0)

  /** Letti in disegno da chi partecipa alle prove: una scrittura li fa ridisegnare. Solo con [active]. */
  fun readGlassTick(): Int = glassTick.intValue
  fun readButtonTick(): Int = buttonTick.intValue
  fun readSheetTick(): Int = sheetTick.intValue

  /** `posa-proprieta`: letto nel blocco dello strato di posa, che si rifà. */
  fun readPosePropertiesTick(): Int = posePropertiesTick.intValue

  /** `posa-contenuto`: letto nel contenuto dello strato di posa, che si registra di nuovo. */
  fun readPoseContentTick(): Int = poseContentTick.intValue

  /** `nascondi-vetro`: i vetri su vetro non disegnano se stessi (solo il contenuto). Letto in disegno. */
  fun readHideNestedGlass(): Boolean = hideNestedGlass.intValue != 0

  /** `nascondi-tasto`: i tasti di vetro non si disegnano affatto. Letto in disegno. */
  fun readHideButtons(): Boolean = hideButtons.intValue != 0

  /**
   * `posa`, nel blocco dello strato di posa di un vetro: riscrive alfa e `renderEffect` con un valore diverso e poi con
   * quello di serie, così Compose li riapplica davvero al `RenderNode` anche quando il blocco non li tocca (lo scope
   * condiviso segna da riapplicare solo i campi cambiati rispetto al suo ultimo valore: uno strato riusato con una
   * sfocatura rimasta non la perderebbe). Il blocco di serie, dopo, scrive i suoi.
   */
  fun forcePoseDefaults(scope: GraphicsLayerScope) {
    if (posePropertiesTick.intValue == 0) return
    forceLayerDefaults(scope)
  }

  /** Accende la fotografia se l'app è debuggable e non gira in Robolectric. Una volta per processo. */
  fun install(context: Context) {
    if (installed) return
    installed = true
    val app = context.applicationContext ?: context
    val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!debuggable || Build.FINGERPRINT == "robolectric") return
    active = true
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context, intent: Intent) {
        perform(intent.getStringExtra(ExtraAction))
      }
    }
    // Esportato: il broadcast arriva dalla shell di adb, un altro utente. Solo nelle app debuggable.
    ContextCompat.registerReceiver(app, receiver, IntentFilter(Action), ContextCompat.RECEIVER_EXPORTED)
  }

  fun register(probe: Probe) {
    if (active) probes.add(probe)
  }

  fun unregister(probe: Probe) {
    if (active) probes.remove(probe)
  }

  /** La prova [action] di [ExtraAction], o la fotografia se null o sconosciuta. Restituisce le righe scritte. */
  fun perform(action: String?): List<String> = when (action) {
    "vetri" -> listOf(emit("azione=vetri vetri=${probes.count { it is Actionable }}")).also { bump(glassTick) }
    "vetri-cattura" -> {
      val glasses = probes.filterIsInstance<Actionable>()
      glasses.forEach { it.recapture() }
      listOf(emit("azione=vetri-cattura vetri=${glasses.size}")).also { bump(glassTick) }
    }
    "tasto" -> listOf(emit("azione=tasto")).also { bump(buttonTick) }
    "foglio" -> listOf(emit("azione=foglio")).also { bump(sheetTick) }
    "posa" -> listOf(emit("azione=posa")).also {
      bump(posePropertiesTick)
      bump(poseContentTick)
    }
    "posa-proprieta" -> listOf(emit("azione=posa-proprieta")).also { bump(posePropertiesTick) }
    "posa-contenuto" -> listOf(emit("azione=posa-contenuto")).also { bump(poseContentTick) }
    "nascondi-vetro" -> listOf(emit("azione=nascondi-vetro")).also { set(hideNestedGlass, 1) }
    "nascondi-tasto" -> listOf(emit("azione=nascondi-tasto")).also { set(hideButtons, 1) }
    "mostra" -> listOf(emit("azione=mostra")).also {
      set(hideNestedGlass, 0)
      set(hideButtons, 0)
    }
    else -> dump()
  }

  private fun set(state: MutableIntState, value: Int) {
    state.intValue = value
    Snapshot.sendApplyNotifications()
  }

  private fun bump(tick: MutableIntState) {
    tick.intValue++
    Snapshot.sendApplyNotifications()
  }

  private fun emit(line: String): String {
    Log.i(Tag, line)
    return line
  }

  /** Scrive la fotografia: una riga per sonda, fra un'intestazione e una chiusura con lo stesso numero. */
  fun dump(): List<String> {
    dumps++
    val now = System.currentTimeMillis()
    val list = probes.toList()
    val lines = ArrayList<String>(list.size + 2)
    lines += emit("dump=$dumps sonde=${list.size}")
    list.forEachIndexed { index, probe ->
      val line = runCatching { probe.describe(now) }.getOrElse { "errore=$it" }
      lines += emit("dump=$dumps n=$index $line")
    }
    lines += emit("dump=$dumps fine")
    return lines
  }

  /** Solo per i test: accende o spegne la fotografia senza ricevitore, dimentica le sonde e rimette le prove a zero. */
  fun activateForTesting(on: Boolean) {
    active = on
    probes.clear()
    for (state in listOf(glassTick, buttonTick, sheetTick, posePropertiesTick, poseContentTick, hideNestedGlass, hideButtons)) {
      state.intValue = 0
    }
  }
}

/**
 * Una sonda semplice per uno strato o un contenuto: [values] sono gli ultimi valori applicati, [layerAt] quando sono
 * stati scritti, [contentAt] l'ultima volta che il contenuto si è registrato (0 = mai visto), e quante volte. Vive quanto
 * chi la tiene.
 */
internal class GlassLayerProbe(private val kind: String) : GlassStateDump.Probe {
  var values: String = ""
  var layerAt: Long = 0L
  var contentAt: Long = 0L
  private var layers = 0
  private var records = 0

  fun applied(values: String) {
    this.values = values
    layerAt = System.currentTimeMillis()
    layers++
    GlassStateDump.register(this)
  }

  fun recorded() {
    contentAt = System.currentTimeMillis()
    records++
    GlassStateDump.register(this)
  }

  override fun describe(now: Long): String =
    "kind=$kind id=${System.identityHashCode(this)} $values" +
      " layerAge=${if (layerAt == 0L) "mai" else "${now - layerAt}ms"}" +
      " contentAge=${if (contentAt == 0L) "mai" else "${now - contentAt}ms"} layers=$layers records=$records"
}
