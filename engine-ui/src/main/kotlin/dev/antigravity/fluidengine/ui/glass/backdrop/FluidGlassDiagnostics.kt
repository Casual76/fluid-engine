package dev.antigravity.fluidengine.ui.glass.backdrop

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.glass.field.GlassFieldHealth
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidProfile
import dev.antigravity.fluidengine.ui.glass.field.GlassLiquidTuning
import dev.antigravity.fluidengine.ui.glass.field.GlassShaderSources
import dev.antigravity.fluidengine.ui.glass.field.GlassWarmUp
import dev.antigravity.fluidengine.ui.glass.field.installGlassLiquidProfileOverride
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicLong

/**
 * Counts how often glass re-captures what is behind it.
 *
 * A capture is the expensive half of the material: a replay of the backdrop's whole recording plus
 * the effect chain over it. The design is that a pane captures only when something it shows has
 * changed — its backdrop redrew, or it moved relative to it — and holds still otherwise. This is how
 * that claim is checked on a real device instead of being believed: switch [enabled] on in a debug
 * build, leave a screen alone, and [totalCaptures] must stop moving.
 *
 * Off by default, and then it costs one volatile read per capture.
 *
 * Dalla 4.0.0 dice anche se gli shader del vetro **compilano** su questo dispositivo ([shaderVerdicts],
 * [compileAllShaders]). Se il compilatore rifiuta una sorgente, chi la usa prende il ramo che ha sotto
 * Android 13 invece di lanciare: la lente sparisce (restano sfocatura e vividezza), il riflesso diventa una
 * tinta piatta, il bagliore del tocco una lavata uniforme, Fluid-physics scende a `Balanced` o perde la
 * frangia. Un vetro più povero e non un'app che cade, e quel ripiego deve vedersi: da qui, e da una riga
 * d'errore in logcat (tag `FluidGlass`). Queste letture non dipendono da [enabled].
 *
 * Dalla 4.0.0 (F1A.7) dice anche **quale vetro** c'è in scena e quanto costa tenerlo: quante superfici
 * disegnano il materiale nuovo e quante quello di prima ([liveSurfaces]) e perché ([liveModelChoices]), quanti
 * nodi e layer del vetro sono vivi ([liveNodes], [liveLayers]), a che punto è il riscaldamento dei programmi
 * ([warmUp]). I conteggi, come [totalCaptures], costano solo con [enabled] acceso, e contano ciò che nasce
 * **dopo** averlo acceso: lo si accende prima di aprire la schermata da guardare.
 *
 * Dalla 4.0.0 (F1A.9), nelle sole build di debug, cambia anche i numeri del materiale nuovo
 * ([tuneLiquidProfile]): le sessioni sul dispositivo fotografano così le varianti delle tavole di gusto.
 */
object FluidGlassDiagnostics {

  @Volatile
  var enabled: Boolean = false

  private val captures = AtomicLong()
  private val exports = AtomicLong()

  /** Captures taken since [enabled] was first switched on. */
  val totalCaptures: Long get() = captures.get()

  /**
   * Materiali pubblicati (registrati) da quando [enabled] e' acceso: un pannello che registra cio'
   * che disegna dietro al contenuto perche' del vetro dentro di lui lo campioni. Dalla 2.13.0 si
   * registra solo se qualcuno lo guarda, quindi su una schermata senza vetro su vetro questo numero
   * non si muove, e a schermo fermo non si muove mai.
   */
  val totalExports: Long get() = exports.get()

  internal fun onCapture() {
    if (enabled) captures.incrementAndGet()
  }

  internal fun onExport() {
    if (enabled) exports.incrementAndGet()
  }

  /**
   * I nomi di tutte le sorgenti AGSL dell'engine, nell'ordine in cui [compileAllShaders] le prova. Non dipende
   * dal dispositivo: anche sotto Android 13 l'elenco è intero, vuoti sono solo i verdetti.
   */
  val shaderSourceNames: List<String> get() = GlassShaderSources.all.map { it.name }

  /**
   * I verdetti che il vetro ha già chiesto in questo processo: nome della sorgente → `null` se il compilatore
   * del dispositivo l'ha accettata, o il suo errore (il messaggio è quello del compilatore, con riga e
   * motivo). Una sorgente che nessuno ha ancora usato non compare: la sonda compila solo quando serve.
   * Sotto Android 13 la sonda non compila niente e la mappa resta vuota.
   *
   * Esiste perché dalla 3.0.0 alla 3.1.2 un rifiuto è passato inosservato per una settimana, con una riga
   * di log a livello avviso e nessun altro segno: un ripiego si deve poter leggere da fuori, e chi lo
   * mostra (la galleria, un test) lo legge da qui.
   */
  val shaderVerdicts: Map<String, Throwable?> get() = GlassFieldHealth.verdicts()

  /**
   * Compila adesso ogni sorgente AGSL dell'engine che non sia già stata provata, e restituisce tutti i
   * verdetti: nome della sorgente → `null` o l'errore del compilatore. Ogni rifiuto scrive anche una riga
   * di log a livello errore (tag `FluidGlass`).
   *
   * Solo diagnostica: lo chiama il test strumentato della galleria a ogni sessione sul dispositivo, perché
   * le sorgenti sono interne all'engine e il test sta in un altro modulo. Costa una compilazione per
   * sorgente la prima volta, niente le successive. Sotto Android 13 non c'è un compilatore AGSL e il
   * risultato è vuoto.
   */
  fun compileAllShaders(): Map<String, Throwable?> = GlassFieldHealth.compileAll()

  // ---- Quale vetro c'è in scena (4.0.0, F1A.7) ----------------------------------------------------------------

  private val surfaces = AtomicIntegerArray(FluidGlassModel.entries.size)
  private val choices = AtomicIntegerArray(FluidGlassModelReason.entries.size)
  private val nodes = AtomicIntegerArray(FluidGlassNodeKind.entries.size)
  private val layers = AtomicIntegerArray(FluidGlassLayerKind.entries.size)

  /**
   * Le superfici di vetro vive, per il materiale che disegnano: una per nodo di `drawBackdrop` attaccato.
   * `Liquid` è una superficie che disegna la luce del materiale nuovo, qualunque strada l'abbia scelta (anche
   * Fluid-physics, la goccia e la pillola della tab bar, quando sono sul materiale nuovo); tutte le altre sono il
   * vetro di prima, comprese quelle di chi chiama `drawBackdrop` a mano.
   *
   * Sono queste le superfici da contare per sapere «quante lastre nuove ci sono su questa pagina»; il
   * perché di ognuna sta in [liveModelChoices].
   */
  val liveSurfaces: Map<FluidGlassModel, Int> get() = snapshot(FluidGlassModel.entries, surfaces)

  /**
   * Le risposte vive della risoluzione del modello (`rememberFluidGlassModel`), per motivo: una per chiamata
   * in composizione, finché la chiamata resta in composizione.
   *
   * Non sono superfici: `glassControlSurface` che resta `Classic` chiede due volte (per sé, e `glassSurface`
   * dentro di lui), e l'ombra staccata di una barra flottante chiede per la sua sagoma senza essere una
   * superficie. Le superfici sono [liveSurfaces]; da qui si legge **perché** un vetro è rimasto quello di
   * prima.
   */
  val liveModelChoices: Map<FluidGlassModelReason, Int> get() = snapshot(FluidGlassModelReason.entries, choices)

  /**
   * I nodi del vetro vivi che tengono un layer, per tipo. Contati dove nascono e dove si staccano.
   *
   * **Non** ci sono ancora i tre nodi del vetro di prima che stanno nei file di Kyant fuori dal nodo di
   * `drawBackdrop` (anello, ombra, ombra interna, un layer ciascuno): una lastra `Classic` ne ha tre in più di
   * quanti si leggono qui. Vedi il rapporto di F1A.7.
   */
  val liveNodes: Map<FluidGlassNodeKind, Int> get() = snapshot(FluidGlassNodeKind.entries, nodes)

  /** I layer del vetro vivi, per tipo: creati e rilasciati dai nodi di [liveNodes]. Stesso limite. */
  val liveLayers: Map<FluidGlassLayerKind, Int> get() = snapshot(FluidGlassLayerKind.entries, layers)

  private fun <K> snapshot(keys: List<K>, values: AtomicIntegerArray): Map<K, Int> {
    val out = LinkedHashMap<K, Int>(keys.size)
    for ((index, key) in keys.withIndex()) out[key] = values.get(index)
    return out
  }

  internal fun countSurface(model: FluidGlassModel, delta: Int) {
    surfaces.addAndGet(model.ordinal, delta)
  }

  internal fun countChoice(reason: FluidGlassModelReason, delta: Int) {
    choices.addAndGet(reason.ordinal, delta)
  }

  internal fun countNode(kind: FluidGlassNodeKind, delta: Int) {
    nodes.addAndGet(kind.ordinal, delta)
  }

  internal fun countLayer(kind: FluidGlassLayerKind, delta: Int) {
    layers.addAndGet(kind.ordinal, delta)
  }

  // ---- Il riscaldamento (4.0.0, F1A.7) -----------------------------------------------------------------------

  /**
   * Il riscaldamento automatico dei programmi del materiale nuovo: dopo il primo fotogramma, a finestra ferma,
   * un disegno fuori schermo per programma (vedi `GlassWarmUp`). Acceso di serie.
   *
   * L'interruttore è per i test e per le misure: spento, il primo vetro di ogni famiglia paga la compilazione
   * nel suo primo fotogramma, come fino alla 3.1.2, ed è così che si misura quanto il riscaldamento risparmia.
   * Acceso, non toglie quel costo al vetro che c'è già nel primo fotogramma della finestra, perché parte dopo:
   * sul Tab S9 il primo fotogramma di un avvio a freddo costa uguale, acceso o spento.
   * Spegnerlo ferma anche un riscaldamento in attesa; riaccenderlo lo fa ripartire alla prossima superficie
   * che entra in composizione.
   */
  var warmUpEnabled: Boolean
    get() = GlassWarmUp.enabled
    set(value) {
      GlassWarmUp.enabled = value
    }

  /** A che punto è il riscaldamento, famiglia per famiglia. Si legge sempre, anche con [enabled] spento. */
  val warmUp: List<FluidGlassWarmUpStatus> get() = GlassWarmUp.status()

  /**
   * Scalda **adesso**, sul filo principale e aspettando ogni fotogramma fuori schermo, tutte le famiglie che
   * il dispositivo può avere, anche con [warmUpEnabled] spento; restituisce lo stato finale.
   *
   * Solo diagnostica: lo chiama il test strumentato della galleria, che deve sapere che il riscaldamento gira
   * e che dopo i verdetti ci sono tutti. Blocca il filo principale per il tempo delle compilazioni (decine di
   * millisecondi a freddo): nel codice di un'app non si chiama mai. Sotto Android 13, o dove il compilatore ha
   * rifiutato una sorgente, non disegna niente e lo dice nello stato.
   */
  @MainThread
  fun warmUpNow(): List<FluidGlassWarmUpStatus> = GlassWarmUp.runNow()

  // ---- Il livello di API simulato (4.0.0, F1A.7) -------------------------------------------------------------

  /** Il livello di API che il vetro sta simulando, o `null`. Fuori da un'app `debuggable` è sempre `null`. */
  val simulatedApiLevel: Int? get() = simulatedGlassApiLevel()

  /**
   * Fa rispondere il vetro dell'engine come su un Android più vecchio: `32` (Android 12: `RenderEffect` sì,
   * AGSL no), `30` (Android 11: né l'uno né l'altro), `null` per tornare al vero.
   *
   * Le due domande che decidono i ripieghi del vetro (`isRenderEffectSupported()`,
   * `isRuntimeShaderSupported()`) e la scelta del modello rispondono con questo livello: l'engine disegna i
   * **suoi** rami senza AGSL o senza `RenderEffect`, sullo Skia e sul driver del dispositivo vero. Serve a
   * vedere sul tablet, accanto all'APK di riferimento, se un innesto nuovo ha cambiato quei rami; non prova la
   * piattaforma vecchia (piano «iOS in Android», 4.4).
   *
   * **Solo nelle build di debug.** Il livello si accetta solo se l'app che lo chiede è `debuggable`
   * (`ApplicationInfo.FLAG_DEBUGGABLE`, che le build di rilascio non hanno): altrimenti la chiamata non fa
   * niente e restituisce `false`. Il livello vive in un campo che solo questa funzione scrive, quindi in
   * un'app di rilascio resta vuoto per tutta la vita del processo e il percorso simulato non si può prendere.
   * Si accetta solo un livello **sotto** quello vero e non sotto 26 (il minimo dell'engine).
   *
   * Vale per ciò che entra in composizione **dopo**: una superficie sceglie il materiale una volta, e non lo
   * cambia a metà vita. Chi cambia livello ricrea l'attività (`Activity.recreate()`).
   *
   * @return vero se la richiesta è stata applicata.
   */
  @MainThread
  fun simulateApiLevel(context: Context, level: Int?): Boolean {
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!glassApiSimulationAllowed(level, Build.VERSION.SDK_INT, debuggable)) return false
    installSimulatedGlassApiLevel(level)
    return true
  }

  // ---- Le manopole del materiale nuovo (4.0.0, F1A.9) --------------------------------------------------------

  @Volatile
  private var tuning: Map<String, String> = emptyMap()

  /**
   * Le manopole del materiale nuovo in vigore, coi nomi e i valori normalizzati: vuota vuol dire il profilo di
   * serie. Fuori da un'app `debuggable` è sempre vuota.
   */
  val liquidProfileTuning: Map<String, String> get() = tuning

  /**
   * Le manopole che [tuneLiquidProfile] riconosce, ognuna con i valori che accetta: le varianti delle tavole di
   * gusto per nome (`edge` → `ios27`, `ios26`, `today`…), i numeri come intervallo (`blur` → `0..64`).
   */
  val liquidProfileKnobs: Map<String, List<String>> get() = GlassLiquidTuning.knobs

  /**
   * Cambia i numeri del materiale nuovo per le lastre che nascono **da adesso**: le varianti delle tavole di gusto
   * (bordo, velo, ombra, smerigliatura, lente, grandi, tema scuro) e i numeri in mezzo, per fotografarle sul
   * pannello vero senza ricompilare (piano «iOS in Android», F1A.9). Ogni chiamata sostituisce tutte le manopole
   * di prima; `null` o vuota torna al profilo di serie. Una chiave o un valore che non si riconosce si scarta: si
   * confronta [knobs] con [liquidProfileTuning] per sapere quali.
   *
   * **Solo nelle build di debug**, come [simulateApiLevel]: se l'app non è `debuggable` non fa niente e
   * restituisce `false`, e il profilo resta quello di serie per tutta la vita del processo. Le lastre già in scena
   * tengono il profilo con cui sono nate: chi cambia le manopole ricrea ciò che vuole vedere.
   *
   * @return vero se la richiesta è stata applicata.
   */
  @MainThread
  fun tuneLiquidProfile(context: Context, knobs: Map<String, String>?): Boolean {
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    if (!debuggable) return false
    installLiquidTuning(knobs)
    return true
  }

  private fun installLiquidTuning(knobs: Map<String, String>?) {
    val tuned = if (knobs.isNullOrEmpty()) null else GlassLiquidTuning.apply(GlassLiquidProfile.Shipped, knobs)
    installGlassLiquidProfileOverride(tuned?.profile?.takeIf { tuned.accepted.isNotEmpty() })
    tuning = tuned?.accepted ?: emptyMap()
  }

  // ---- Ganci dei test che montano composable ------------------------------------------------------------------

  /**
   * Solo per i test della galleria (Robolectric): ogni sorgente AGSL dell'engine risulta compilata ([accept]
   * vero) o rifiutata, senza chiedere niente al compilatore. Senza questo gancio, sul livello 35 il modello
   * dipenderebbe da ciò che Robolectric fa di un `RuntimeShader`: in modo grafico `NATIVE` lo compila (F1A.7 l'ha
   * visto), negli altri modi no.
   *
   * Con [accept] vero il modello si sceglie come su un dispositivo sano, ma disegnare quelle superfici chiede
   * uno shader vero: il test non le disegna. Con [accept] falso è il caso «un driver che rifiuta», e si può
   * disegnare. Sotto Android 13 (vero o simulato) non accende niente. Annullato da [resetForTesting].
   */
  @VisibleForTesting
  fun forceShaderVerdictsForTesting(accept: Boolean) {
    GlassFieldHealth.installVerdictsForTesting(accept)
  }

  /**
   * Solo per i test: la sonda vera senza verdetti, nessun livello simulato, il riscaldamento acceso e mai
   * partito, il profilo di serie. I conteggi vivi non si azzerano: scendono da soli quando le superfici se ne
   * vanno.
   */
  @VisibleForTesting
  fun resetForTesting() {
    GlassFieldHealth.resetForTesting()
    installSimulatedGlassApiLevel(null)
    GlassWarmUp.resetForTesting()
    installLiquidTuning(null)
  }
}

/**
 * Se una build può simulare [level]: solo un'app `debuggable`, solo sotto il livello vero, mai sotto il minimo
 * dell'engine. `null` (togliere la simulazione) si accetta sempre da un'app `debuggable`. Pura, per il test.
 */
internal fun glassApiSimulationAllowed(level: Int?, realSdk: Int, debuggable: Boolean): Boolean = when {
  !debuggable -> false
  level == null -> true
  else -> level >= GlassMinSimulatedApiLevel && level < realSdk
}

/** Il minimo dell'engine: sotto non c'è un ramo da vedere. */
internal const val GlassMinSimulatedApiLevel: Int = 26

/**
 * Perché una superficie ha il materiale che ha: i motivi della risoluzione del modello (`resolveFluidGlassModel`
 * in `ui/fluid/FluidGlassModel.kt`), nell'ordine in cui si chiedono. Il primo che vale decide.
 */
enum class FluidGlassModelReason(val model: FluidGlassModel) {
  /** Tutto vero: il materiale nuovo. */
  Liquid(FluidGlassModel.Liquid),

  /** Un antenato ha chiesto il vetro di prima (`LocalFluidGlassModel`): l'orologio, una pagina di confronto. */
  ClassicRequested(FluidGlassModel.Classic),

  /** Sotto Android 13 (vero, o simulato da una build di debug): niente AGSL. */
  ApiLevel(FluidGlassModel.Classic),

  /** La fascia in alto (`GlassFalloff.FadeDown`): resta quella di oggi fino al bordo dello scorrimento. */
  FadeDown(FluidGlassModel.Classic),

  /**
   * Una forma che il campo non sa rendere: un contorno generico, angoli tagliati, o (4.0.0) un angolo che alla
   * taglia della superficie scende oltre mezzo lato corto.
   */
  GenericShape(FluidGlassModel.Classic),

  /** Un velo senza lente, bordo né ombra: lo scrim di un modale, non una lastra. */
  FlatVeil(FluidGlassModel.Classic),

  /** Il compilatore del dispositivo ha rifiutato il corpo o la luce della lastra. */
  ShadersRefused(FluidGlassModel.Classic),
}

/** I nodi del vetro che tengono un layer. */
enum class FluidGlassNodeKind {
  /** Il nodo di `drawBackdrop`: uno per superficie, di qualunque materiale. */
  Backdrop,

  /** Il nodo dell'ombra del materiale nuovo (due maschere in un layer). */
  Shadow,
}

/** I layer del vetro. */
enum class FluidGlassLayerKind {
  /** La cattura dello sfondo con la catena degli effetti: una per superficie. */
  Capture,

  /** Il layer in cui la superficie si posa, ritagliata dal clip hardware: uno per superficie, dalla prima posa. */
  Placement,

  /** Il materiale che una superficie pubblica per il vetro che le sta dentro, solo se qualcuno lo campiona. */
  Material,

  /** L'ombra del materiale nuovo. */
  Shadow,
}

/** Lo stato del riscaldamento di una famiglia di programmi. */
enum class FluidGlassWarmUpState {
  /** Nessuno l'ha chiesto ancora (o il riscaldamento è spento). */
  Idle,

  /** Chiesto: aspetta il primo fotogramma e una finestra ferma. */
  Waiting,

  /** A metà: alcuni programmi scaldati, gli altri al prossimo momento fermo. */
  Running,

  /** Tutti i programmi della famiglia disegnati fuori schermo una volta. */
  Done,

  /** Non c'è niente da scaldare qui: sotto Android 13, una sorgente rifiutata, una finestra senza GPU. */
  Skipped,

  /** Un disegno fuori schermo non è riuscito: il primo vetro della famiglia pagherà la compilazione. */
  Failed,
}

/**
 * Il riscaldamento di una famiglia di programmi, come lo legge chi lo mostra.
 *
 * @property family il nome della famiglia (`Slab`: la lastra ferma; `Group`: i gruppi a otto pezzi).
 * @property programs le sorgenti della famiglia, nell'ordine in cui si scaldano.
 * @property warmed quelle già disegnate fuori schermo.
 * @property detail perché è [FluidGlassWarmUpState.Skipped] o [FluidGlassWarmUpState.Failed].
 * @property mainThreadMillis quanto ha tenuto il filo principale in tutto, costruzione e richiesta del
 *   fotogramma comprese (non la compilazione, che è del RenderThread, salvo in [FluidGlassDiagnostics.warmUpNow]).
 */
class FluidGlassWarmUpStatus internal constructor(
  val family: String,
  val programs: List<String>,
  val state: FluidGlassWarmUpState,
  val warmed: List<String>,
  val detail: String?,
  val mainThreadMillis: Float,
) {
  override fun toString(): String = buildString {
    append(family).append(": ").append(state)
    append(" (").append(warmed.size).append('/').append(programs.size).append(')')
    if (detail != null) append(", ").append(detail)
    append(", principale ").append("%.1f".format(java.util.Locale.ROOT, mainThreadMillis)).append(" ms")
  }
}

/**
 * Ciò che un nodo del vetro ha contato nella diagnostica, per restituirlo esattamente quando se ne va.
 *
 * Si conta solo a diagnostica accesa, e un nodo nato a diagnostica spenta non toglie niente: i numeri non
 * scendono mai sotto zero, e contano ciò che è nato dopo l'accensione. Un nodo, un'istanza; tutto dal filo
 * principale, dove i nodi nascono e si staccano.
 */
internal class GlassNodeCensus(private val kind: FluidGlassNodeKind) {

  private var node = false
  private val layers = BooleanArray(FluidGlassLayerKind.entries.size)
  private var surface: FluidGlassModel? = null

  /** Il nodo si è attaccato. */
  fun attached() {
    if (!node && FluidGlassDiagnostics.enabled) {
      node = true
      FluidGlassDiagnostics.countNode(kind, +1)
    }
  }

  /** Il nodo tiene un layer di questo tipo (una volta per tipo e per attacco; le chiamate in più non contano). */
  fun layer(kind: FluidGlassLayerKind) {
    if (!layers[kind.ordinal] && FluidGlassDiagnostics.enabled) {
      layers[kind.ordinal] = true
      FluidGlassDiagnostics.countLayer(kind, +1)
    }
  }

  /**
   * La superficie disegna [model]. Se prima ne disegnava un altro, il conteggio passa di là anche a diagnostica
   * spenta: ciò che è stato contato deve poter scendere.
   */
  fun surface(model: FluidGlassModel) {
    val held = surface
    if (held == model) return
    if (held != null) {
      FluidGlassDiagnostics.countSurface(held, -1)
      surface = null
    }
    if (held != null || FluidGlassDiagnostics.enabled) {
      surface = model
      FluidGlassDiagnostics.countSurface(model, +1)
    }
  }

  /** Il nodo si è staccato: rilascia tutto ciò che aveva contato. */
  fun detached() {
    if (node) {
      node = false
      FluidGlassDiagnostics.countNode(kind, -1)
    }
    for (layerKind in FluidGlassLayerKind.entries) {
      if (layers[layerKind.ordinal]) {
        layers[layerKind.ordinal] = false
        FluidGlassDiagnostics.countLayer(layerKind, -1)
      }
    }
    surface?.let { FluidGlassDiagnostics.countSurface(it, -1) }
    surface = null
  }
}
