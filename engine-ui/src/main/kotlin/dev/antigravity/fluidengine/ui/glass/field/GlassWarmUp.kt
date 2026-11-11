/*
 * IL RISCALDAMENTO dei programmi del materiale `Liquid` (piano «iOS in Android», F1A.7, cancello C6), e la scelta
 * del modello contata nella diagnostica, che è il punto da cui parte.
 *
 * Perché esiste. Un programma AGSL nuovo costa, la prima volta che si disegna, la sua compilazione sul RenderThread:
 * sul Tab S9 i cinque programmi dei prototipi del materiale (corpo e luce a uno e a otto pezzi, la striscia del bordo)
 * a freddo costano 139–141 ms in tutto, a caldo 26–30 (S1-bis; gli shader definitivi non erano ancora scritti, e si
 * rimisurano sul dispositivo). Il primo
 * tasto, la prima capsula, il primo menu di un processo pagherebbero quello scatto nel fotogramma in cui compaiono.
 * Il cancello C6 ha deciso dove si paga invece: **non** nel percorso dell'avvio (è più di un fotogramma), ma dopo il
 * primo fotogramma, per famiglia, quando la finestra è ferma, con un disegno fuori schermo per programma che scalda
 * la GPU **e** il percorso del filo principale (ricetta, catena, pennello).
 *
 * Dove si aggancia: alla scelta del modello (`rememberFluidGlassModel`), cioè alla prima superficie di vetro che
 * entra in composizione in un albero che chiede `Liquid` su un dispositivo che lo può avere. Non al tema, per due
 * ragioni: il tema non sa se ci sarà del vetro, e un albero che chiede `Classic` (l'orologio) non deve compilare
 * programmi che non disegnerà mai. E non alla prima superficie `Liquid`: la barra in alto di ogni `FluidScreen` resta
 * `Classic` (la fascia, fino a F2.5) ma vive in un albero che chiede `Liquid`, ed è spesso l'unico vetro della prima
 * schermata; agganciarsi lì scalda la lastra prima del primo menu o della prima capsula. Le app non chiamano niente.
 */
package dev.antigravity.fluidengine.ui.glass.field

import android.content.res.Resources
import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.MessageQueue
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.compose.runtime.RememberObserver
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.FluidCapsuleShape
import dev.antigravity.fluidengine.ui.fluid.FluidGlassLiquidMinSdk
import dev.antigravity.fluidengine.ui.fluid.FluidGlassModel
import dev.antigravity.fluidengine.ui.fluid.GlassDefaults
import dev.antigravity.fluidengine.ui.fluid.GlassFalloff
import dev.antigravity.fluidengine.ui.fluid.GlassRole
import dev.antigravity.fluidengine.ui.fluid.sanitized
import dev.antigravity.fluidengine.ui.glass.backdrop.BackdropEffectScopeImpl
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassModelReason
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassWarmUpState
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassWarmUpStatus
import dev.antigravity.fluidengine.ui.glass.backdrop.RuntimeShaderCacheImpl
import dev.antigravity.fluidengine.ui.glass.backdrop.asComposeShader
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.RuntimeShaderEffect
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import dev.antigravity.fluidengine.ui.glass.backdrop.obtainEngineShader
import java.util.EnumMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

// ------------------------------------------------------------------------------------------------------------
// Che cosa si scalda
// ------------------------------------------------------------------------------------------------------------

/**
 * Un disegno fuori schermo: **un programma** (un corpo con la sua catena, o una luce nei suoi due modi di
 * comporre). Uno per volta, così una compilazione a freddo tiene il RenderThread al più per un programma (20–40 ms
 * sul Tab S9) e non per tutta la famiglia, se l'utente tocca proprio in quel momento.
 */
internal enum class GlassWarmStep {
  /**
   * Il corpo a un pezzo, **con la sua catena a monte** (saturazione, sfocatura, mescola): in S1-bis il primo corpo
   * che entra paga anche quei programmi (31–39 ms contro 19–20 del secondo). Costruito dalla stessa superficie che
   * disegna un tasto, quindi scalda anche ricetta e catena sul filo principale.
   */
  SlabBody,

  /** La luce a un pezzo, nei due disegni della lastra ferma: prima `Plus`, poi `SrcOver`. */
  SlabLight,

  /** Il corpo a otto pezzi, sulla stessa catena a monte (già calda dopo [SlabBody]). */
  GroupBody,

  /** La luce a otto pezzi, nei due modi di comporre. */
  GroupLight;

  /** La sorgente del programma che questo passo compila. */
  val source: GlassShaderSource
    get() = when (this) {
      SlabBody -> GlassShaderSources.Body1
      SlabLight -> GlassShaderSources.Light1
      GroupBody -> GlassShaderSources.Body8
      GroupLight -> GlassShaderSources.Light8
    }

  val family: GlassWarmFamily get() = GlassWarmFamily.entries.first { this in it.steps }
}

/**
 * Una famiglia: i programmi che servono insieme a una cosa che può stare in scena. Si scalda per intero o per
 * niente: se il compilatore ne rifiuta uno, la cosa resta `Classic` e scaldare gli altri non serve.
 *
 * La **striscia del bordo dello scorrimento** (due passate, il programma più caro a freddo: 43–51 ms) entra qui come
 * famiglia sua quando esisterà (F2.4): `GlassShaderSources.liquid` e `ShaderSourceLintTest` non lasciano registrare
 * un programma nuovo del materiale senza una famiglia.
 */
internal enum class GlassWarmFamily(val steps: List<GlassWarmStep>) {
  /** La lastra ferma: tasti, capsule, menu, pannelli. La chiede ogni albero che vuole `Liquid` (vedi sopra). */
  Slab(listOf(GlassWarmStep.SlabBody, GlassWarmStep.SlabLight)),

  /**
   * I gruppi e i viaggi fra lastre (`Body8`, `Light8`). Oggi non li disegna nessuno, quindi nessuno la chiede: la
   * chiederà F1B quando una superficie che viaggia entra in composizione. Si scalda già con
   * `FluidGlassDiagnostics.warmUpNow`, ed è così che il test strumentato la prova.
   */
  Group(listOf(GlassWarmStep.GroupBody, GlassWarmStep.GroupLight));

  val sources: List<GlassShaderSource> get() = steps.map { it.source }
}

// ------------------------------------------------------------------------------------------------------------
// La scelta del modello, contata, e il punto da cui parte il riscaldamento
// ------------------------------------------------------------------------------------------------------------

/**
 * Perché [model] è il modello che `resolveFluidGlassModel` ha dato con questi ingressi: il primo motivo che vale,
 * nello stesso ordine. Non rifà la domanda al compilatore: se nessun'altra ragione tiene il vetro di prima, è stato
 * lui. `GlassWarmUpTest` confronta le due funzioni su tutta la tabella.
 */
internal fun glassModelReason(
  model: FluidGlassModel,
  sdk: Int,
  requested: FluidGlassModel,
  falloff: GlassFalloff,
  genericShape: Boolean,
  flat: Boolean,
): FluidGlassModelReason = when {
  model == FluidGlassModel.Liquid -> FluidGlassModelReason.Liquid
  requested != FluidGlassModel.Liquid -> FluidGlassModelReason.ClassicRequested
  sdk < FluidGlassLiquidMinSdk -> FluidGlassModelReason.ApiLevel
  falloff == GlassFalloff.FadeDown -> FluidGlassModelReason.FadeDown
  genericShape -> FluidGlassModelReason.GenericShape
  flat -> FluidGlassModelReason.FlatVeil
  else -> FluidGlassModelReason.ShadersRefused
}

/**
 * La risposta di `rememberFluidGlassModel`, ricordata finché la superficie resta in composizione.
 *
 * È un [RememberObserver] per due cose che devono stare esattamente quanto la superficie: il conteggio nella
 * diagnostica (entra con la superficie, esce con lei, e non resta indietro se la composizione si abbandona), e la
 * richiesta del riscaldamento, che parte quando la scelta entra davvero in scena e non a ogni ricomposizione: la
 * risposta si ricorda con le sue chiavi, quindi una ricomposizione non la ricrea.
 *
 * @property warms vero se l'albero chiede `Liquid` dove può esistere e il compilatore non ha rifiutato la lastra:
 *   anche quando questa superficie resta `Classic` per la forma, la caduta o il velo (vedi il commento in testa).
 */
internal class GlassModelChoice(
  val model: FluidGlassModel,
  val reason: FluidGlassModelReason,
  val warms: Boolean,
  private val view: View?,
) : RememberObserver {

  private var counted = false

  override fun onRemembered() {
    if (FluidGlassDiagnostics.enabled) {
      counted = true
      FluidGlassDiagnostics.countChoice(reason, +1)
    }
    if (warms) GlassWarmUp.request(GlassWarmFamily.Slab, view)
  }

  override fun onForgotten() {
    if (counted) {
      counted = false
      FluidGlassDiagnostics.countChoice(reason, -1)
    }
  }

  // Mai ricordata: niente è stato contato, niente è stato chiesto.
  override fun onAbandoned() = Unit
}

/**
 * La scelta di una superficie, con il suo motivo e se deve far partire il riscaldamento. Pura, salvo [view] che
 * passa com'è.
 *
 * @param model la risposta di `resolveFluidGlassModel` con questi stessi ingressi.
 */
internal fun glassModelChoice(
  model: FluidGlassModel,
  sdk: Int,
  requested: FluidGlassModel,
  falloff: GlassFalloff,
  genericShape: Boolean,
  flat: Boolean,
  view: View?,
): GlassModelChoice {
  val reason = glassModelReason(model, sdk, requested, falloff, genericShape, flat)
  val warms = requested == FluidGlassModel.Liquid &&
    sdk >= FluidGlassLiquidMinSdk &&
    reason != FluidGlassModelReason.ShadersRefused
  return GlassModelChoice(model, reason, warms, view)
}

// ------------------------------------------------------------------------------------------------------------
// Lo stato del riscaldamento: puro
// ------------------------------------------------------------------------------------------------------------

/**
 * Lo stato del riscaldamento, famiglia per famiglia, senza la piattaforma: chi decide **quando** è [GlassWarmUp],
 * chi disegna è il `draw` passato a [run]. Così le regole (idempotente, una famiglia rifiutata si salta, un passo
 * fallito ferma la sua famiglia e non le altre, una finestra che se ne va non perde ciò che è già scaldato) si
 * provano sul JVM.
 *
 * @param agsl il dispositivo ha AGSL (il livello di API, vero o simulato).
 * @param usable il compilatore accetta la sorgente (la sonda: la prima volta può costruire il `RuntimeShader`).
 */
internal class GlassWarmUpCore(
  private val agsl: () -> Boolean,
  private val usable: (GlassShaderSource) -> Boolean,
  private val nanoTime: () -> Long = System::nanoTime,
) {

  private class Entry {
    var state = FluidGlassWarmUpState.Idle
    val warmed = ArrayList<GlassWarmStep>()
    var detail: String? = null
    var mainNanos = 0L
  }

  private val entries = EnumMap<GlassWarmFamily, Entry>(GlassWarmFamily::class.java).apply {
    for (family in GlassWarmFamily.entries) put(family, Entry())
  }

  /**
   * Chiede di scaldare [family]. Vero se è entrata in attesa adesso; falso se c'era già (in attesa, a metà, fatta,
   * fallita: **non riparte**) o se qui non c'è niente da scaldare, e allora resta [FluidGlassWarmUpState.Skipped]
   * con il motivo. Una famiglia saltata si può richiedere: il motivo può non valere più (una simulazione tolta, una
   * finestra disegnata dalla GPU).
   *
   * @param gpuWindow la finestra che la chiede è disegnata dalla GPU: senza, non ci sono programmi da scaldare.
   */
  @Synchronized
  fun request(family: GlassWarmFamily, gpuWindow: Boolean): Boolean {
    val entry = entries.getValue(family)
    if (entry.state != FluidGlassWarmUpState.Idle && entry.state != FluidGlassWarmUpState.Skipped) return false
    if (!agsl()) return skip(entry, NoAgsl)
    if (!gpuWindow) return skip(entry, NoGpuWindow)
    entry.state = FluidGlassWarmUpState.Waiting
    entry.detail = null
    return true
  }

  /** Vero se qualche famiglia aspetta o è a metà. */
  val pending: Boolean
    @Synchronized get() = entries.values.any { it.isPending() }

  /**
   * Il prossimo passo da disegnare, o `null`. Prima del primo passo di una famiglia chiede i verdetti delle sue
   * sorgenti: una rifiutata salta tutta la famiglia, senza disegnare niente.
   */
  @Synchronized
  fun next(): GlassWarmStep? {
    for ((family, entry) in entries) {
      if (!entry.isPending()) continue
      if (!agsl()) {
        skip(entry, NoAgsl)
        continue
      }
      if (entry.state == FluidGlassWarmUpState.Waiting) {
        val refused = family.sources.filterNot(usable)
        if (refused.isNotEmpty()) {
          skip(entry, "rifiutate dal compilatore: " + refused.joinToString { it.name })
          continue
        }
        entry.state = FluidGlassWarmUpState.Running
      }
      return family.steps.first { it !in entry.warmed }
    }
    return null
  }

  /**
   * Disegna [step] con [draw] e ne tiene il conto: fatto, o fallito con il motivo (un passo fallito ferma la sua
   * famiglia, per tutto il processo; le altre vanno avanti). Vero se il disegno è partito.
   */
  fun run(step: GlassWarmStep, draw: (GlassWarmStep) -> Unit): Boolean {
    val started = nanoTime()
    val error: Throwable? = try {
      draw(step)
      null
    } catch (e: Exception) {
      e
    } catch (e: LinkageError) {
      // Una piattaforma senza la libreria che serve (un ambiente di prova): come un disegno rifiutato.
      e
    }
    val spent = nanoTime() - started
    synchronized(this) {
      val entry = entries.getValue(step.family)
      entry.mainNanos += spent
      if (error != null) {
        entry.state = FluidGlassWarmUpState.Failed
        entry.detail = "${step.source.name}: $error"
        return false
      }
      if (step !in entry.warmed) entry.warmed += step
      if (entry.warmed.size == step.family.steps.size) entry.state = FluidGlassWarmUpState.Done
      return true
    }
  }

  /**
   * La finestra che si aspettava se n'è andata, o il riscaldamento è stato spento: chi aspettava torna fermo. Ciò
   * che è già scaldato resta scaldato, e alla prossima richiesta si riparte dal passo che mancava.
   */
  @Synchronized
  fun cancel() {
    for (entry in entries.values) if (entry.isPending()) entry.state = FluidGlassWarmUpState.Idle
  }

  @Synchronized
  fun status(): List<FluidGlassWarmUpStatus> = entries.map { (family, entry) ->
    FluidGlassWarmUpStatus(
      family = family.name,
      programs = family.sources.map { it.name },
      state = entry.state,
      warmed = entry.warmed.map { it.source.name },
      detail = entry.detail,
      mainThreadMillis = entry.mainNanos / 1_000_000f,
    )
  }

  @Synchronized
  fun reset() {
    for (entry in entries.values) {
      entry.state = FluidGlassWarmUpState.Idle
      entry.warmed.clear()
      entry.detail = null
      entry.mainNanos = 0L
    }
  }

  private fun Entry.isPending() =
    state == FluidGlassWarmUpState.Waiting || state == FluidGlassWarmUpState.Running

  private fun skip(entry: Entry, why: String): Boolean {
    entry.state = FluidGlassWarmUpState.Skipped
    entry.detail = why
    return false
  }

  companion object {
    const val NoAgsl = "AGSL non c'è (sotto Android 13, vero o simulato)"
    const val NoGpuWindow = "la finestra non è disegnata dalla GPU: non ci sono programmi da scaldare"
  }
}

// ------------------------------------------------------------------------------------------------------------
// Quando: dopo il primo fotogramma, a finestra ferma
// ------------------------------------------------------------------------------------------------------------

/**
 * Quanto la finestra deve restare senza ridisegnarsi perché la si consideri ferma. Più di un fotogramma a 60 Hz per
 * sette, e meno del cursore che lampeggia (500 ms): un'animazione in corsa ridisegna ogni 8–16 ms e non lascia mai
 * passare tanto, una pagina ferma sì. Non è una durata del movimento ma una soglia di osservazione: per questo non
 * viene da `FluidMotion`.
 */
internal const val GlassWarmUpQuietMillis: Long = 120L

/**
 * Se dopo il legame la finestra non si ridisegna più (una pagina già ferma), il suo primo fotogramma c'è stato
 * comunque: dopo questa attesa lo si dà per avvenuto.
 */
internal const val GlassWarmUpFirstFrameFallbackMillis: Long = 4 * GlassWarmUpQuietMillis

/** Oltre questo, un fotogramma fuori schermo che non torna si rilascia lo stesso. */
internal const val GlassWarmUpReleaseTimeoutMillis: Long = 1_000L

/**
 * Il riscaldamento del processo: [GlassWarmUpCore] più il **quando**, sul filo principale.
 *
 * Una richiesta (da [GlassModelChoice]) lega il riscaldamento alla finestra della superficie: un ascoltatore dei suoi
 * disegni dice quando ha disegnato l'ultima volta. Il primo passo parte dopo che la finestra ha disegnato almeno una
 * volta (o dopo [GlassWarmUpFirstFrameFallbackMillis] se è già ferma), quando non ridisegna da
 * [GlassWarmUpQuietMillis], è visibile, e la coda del filo principale è vuota (`IdleHandler`); ogni passo successivo
 * aspetta di nuovo tutto questo, e il fotogramma fuori schermo del passo prima. Un'animazione, uno scorrimento, un
 * tocco rimandano il passo; una pagina che non si ferma mai non lo fa partire (il primo vetro della famiglia pagherà
 * la compilazione, com'era). Niente qui gira nel percorso dell'avvio.
 *
 * Senza effetti dove non c'è niente da scaldare: sotto Android 13, in un albero che chiede `Classic`, dove la sonda
 * ha rifiutato, in una finestra non disegnata dalla GPU, o con [enabled] spento. (Robolectric dichiara le sue finestre
 * accelerate e compila AGSL in modo grafico `NATIVE`: lì il riscaldamento si chiede e, se l'orologio avanza, gira;
 * `GlassPathSelectionTest` lo prova.)
 *
 * Ogni famiglia che finisce lo scrive in logcat (tag `FluidGlass`): fatta a livello informazione, fallita a livello
 * errore, perché un ripiego non deve passare in silenzio.
 */
internal object GlassWarmUp {

  /** Vedi `FluidGlassDiagnostics.warmUpEnabled`. */
  @Volatile
  var enabled: Boolean = true
    set(value) {
      field = value
      if (!value) mainHandler.post { stop() }
    }

  private val core = GlassWarmUpCore(agsl = ::isRuntimeShaderSupported, usable = GlassFieldHealth::usable)

  private val mainHandler: Handler by lazy { Handler(Looper.getMainLooper()) }

  // Il legame con la finestra: tutto dal filo principale.
  private var view: View? = null
  private var observer: ViewTreeObserver? = null
  private var density = 0f
  private var boundAt = 0L
  private var lastDrawAt = 0L
  private var drawn = false
  private var inFlight = false
  private var idleArmed = false

  private val onDraw = ViewTreeObserver.OnDrawListener {
    lastDrawAt = SystemClock.uptimeMillis()
    drawn = true
    mainHandler.removeCallbacks(tick)
    mainHandler.postDelayed(tick, GlassWarmUpQuietMillis)
  }

  private val onAttach = object : View.OnAttachStateChangeListener {
    override fun onViewAttachedToWindow(v: View) = Unit

    override fun onViewDetachedFromWindow(v: View) {
      unbind()
      core.cancel()
    }
  }

  private val tick = Runnable { armIdle() }

  private val idle = MessageQueue.IdleHandler {
    idleArmed = false
    step()
    false
  }

  private val released: () -> Unit = {
    inFlight = false
    if (view != null) mainHandler.postDelayed(tick, GlassWarmUpQuietMillis)
  }

  /**
   * Chiede di scaldare [family] per la finestra di [view]. Idempotente: una famiglia già chiesta, a metà o fatta non
   * riparte, e la finestra legata resta quella finché è attaccata.
   */
  fun request(family: GlassWarmFamily, view: View?) {
    if (!enabled) return
    if (Looper.myLooper() !== Looper.getMainLooper()) {
      mainHandler.post { request(family, view) }
      return
    }
    if (view != null && view.isHardwareAccelerated) {
      core.request(family, gpuWindow = true)
      if (core.pending) bind(view)
    } else {
      core.request(family, gpuWindow = false)
    }
  }

  fun status(): List<FluidGlassWarmUpStatus> = core.status()

  /** Vedi `FluidGlassDiagnostics.warmUpNow`: tutto, adesso, aspettando ogni fotogramma, anche a interruttore spento. */
  @MainThread
  fun runNow(): List<FluidGlassWarmUpStatus> {
    check(Looper.myLooper() === Looper.getMainLooper()) { "Il riscaldamento registra nodi di resa: solo dal filo principale." }
    val screen = Resources.getSystem().displayMetrics.density
    for (family in GlassWarmFamily.entries) core.request(family, gpuWindow = true)
    while (true) {
      val next = core.next() ?: break
      core.run(next) { step -> drawOffscreen(step, screen, blocking = true, onReleased = {}) }
      log(next.family)
    }
    return core.status()
  }

  /** Una riga quando una famiglia arriva in fondo: fatta (informazione) o fallita (errore). */
  private fun log(family: GlassWarmFamily) {
    val status = core.status().firstOrNull { it.family == family.name } ?: return
    when (status.state) {
      FluidGlassWarmUpState.Done -> Log.i(LogTag, "Riscaldamento del vetro: $status")
      FluidGlassWarmUpState.Failed -> Log.e(
        LogTag,
        "Riscaldamento del vetro fallito, il primo vetro della famiglia pagherà la compilazione: $status",
      )
      else -> Unit
    }
  }

  private const val LogTag = "FluidGlass"

  /** Solo per i test: niente legato, niente chiesto, interruttore acceso. */
  fun resetForTesting() {
    val reset = Runnable {
      stop()
      core.reset()
      inFlight = false
    }
    if (Looper.myLooper() === Looper.getMainLooper()) reset.run() else mainHandler.post(reset)
    enabled = true
  }

  private fun stop() {
    unbind()
    core.cancel()
  }

  private fun bind(target: View) {
    val current = view
    if (current === target) return
    if (current != null && current.isAttachedToWindow) return
    unbind()
    val vto = target.viewTreeObserver
    if (!vto.isAlive) return
    view = target
    observer = vto
    density = target.resources.displayMetrics.density
    boundAt = SystemClock.uptimeMillis()
    lastDrawAt = boundAt
    drawn = false
    vto.addOnDrawListener(onDraw)
    target.addOnAttachStateChangeListener(onAttach)
    mainHandler.postDelayed(tick, GlassWarmUpFirstFrameFallbackMillis)
  }

  private fun unbind() {
    mainHandler.removeCallbacks(tick)
    if (idleArmed) {
      Looper.getMainLooper().queue.removeIdleHandler(idle)
      idleArmed = false
    }
    observer?.let { if (it.isAlive) it.removeOnDrawListener(onDraw) }
    view?.removeOnAttachStateChangeListener(onAttach)
    observer = null
    view = null
  }

  private fun armIdle() {
    if (idleArmed) return
    idleArmed = true
    Looper.getMainLooper().queue.addIdleHandler(idle)
  }

  /** Un passo, se è il momento. Ogni uscita senza passo lascia a un evento futuro il compito di riprovare. */
  private fun step() {
    val target = view ?: return
    if (!enabled) {
      stop()
      return
    }
    if (!core.pending) {
      unbind()
      return
    }
    // Il fotogramma fuori schermo di prima non è ancora tornato: lo rilascia `released`, che riarma.
    if (inFlight) return
    val now = SystemClock.uptimeMillis()
    // Il primo fotogramma: la finestra ha disegnato dopo il legame, o è ferma da abbastanza da averlo già fatto.
    if (!drawn && now - boundAt < GlassWarmUpFirstFrameFallbackMillis) return
    // In secondo piano non si scalda: al ritorno la finestra ridisegna, e il prossimo disegno riarma.
    if (!target.isShown || target.windowVisibility != View.VISIBLE) return
    val quietFor = now - lastDrawAt
    if (drawn && quietFor < GlassWarmUpQuietMillis) {
      mainHandler.postDelayed(tick, GlassWarmUpQuietMillis - quietFor)
      return
    }
    val next = core.next()
    if (next == null) {
      unbind()
      return
    }
    inFlight = true
    val started = core.run(next) { s -> drawOffscreen(s, density, blocking = false, onReleased = released) }
    log(next.family)
    if (!started) {
      inFlight = false
      mainHandler.postDelayed(tick, GlassWarmUpQuietMillis)
    }
  }

  private fun drawOffscreen(step: GlassWarmStep, density: Float, blocking: Boolean, onReleased: () -> Unit) {
    if (!isRuntimeShaderSupported()) throw IllegalStateException(GlassWarmUpCore.NoAgsl)
    GlassOffscreenWarmer.draw(step, density, blocking, mainHandler, onReleased)
  }
}

// ------------------------------------------------------------------------------------------------------------
// Come: un disegno fuori schermo per programma
// ------------------------------------------------------------------------------------------------------------

/** Il lato della lastra che si scalda: un tasto tondo di vetro, il vetro più comune (44 dp, R6). */
internal const val GlassWarmSlabSideDp: Float = 44f

/**
 * La superficie che si scalda: un tasto di vetro a riposo sul lato chiaro, con l'ottica e la forma di serie. È la
 * stessa classe che disegna ogni lastra `Liquid` ([GlassLiquidSurface]), quindi il disegno fuori schermo passa per
 * la stessa ricetta, la stessa catena e gli stessi due disegni della luce. Il lato chiaro perché lì la luce fa
 * **tutti e due** i disegni (sul lato scuro il filo non c'è, e il suo modo di comporre resterebbe freddo).
 */
internal fun glassWarmSlabSurface(density: Float): GlassLiquidSurface = GlassLiquidSurface(
  role = GlassRole.Interactive,
  optics = GlassDefaults.buttonOptics().sanitized(),
  shape = FluidCapsuleShape,
  darkSide = false,
  screenDensity = density,
  intensity = { 1f },
  opticalDepth = { 1f },
  pressed = { 0f },
  quality = { 1f },
  film = { Color.Unspecified },
  tint = { Color.Unspecified },
  touch = { null },
)

/**
 * Il disegno fuori schermo: un `HardwareRenderer` suo, su un `ImageReader` grande quanto un tasto, che rende **una
 * volta** e si distrugge.
 *
 * Perché così: il programma di un effetto si compila la prima volta che la GPU lo esegue, quindi costruire il
 * `RuntimeShader` (meno di un millisecondo) non basta; ci vuole un disegno vero. Tutti i renderer di un processo
 * condividono il RenderThread e il suo contesto di Skia, quindi un programma compilato qui è compilato per la
 * finestra; e HWUI lo scrive anche nella cache su disco, 4 s dopo (S1-bis). Ma questo renderer ha la sua superficie:
 * niente di ciò che disegna arriva allo schermo, e niente di ciò che è a schermo viene toccato o ri-registrato.
 *
 * Non restano risorse: nodi, renderer e lettore si rilasciano appena il fotogramma è tornato (o dopo
 * [GlassWarmUpReleaseTimeoutMillis]). Restano solo ciò che è il punto (i programmi nella cache di Skia) e ciò che
 * qualunque disegno lascia (le texture di appoggio della sfocatura, che Skia riusa e libera da sé).
 *
 * Gli uniform delle varianti a otto pezzi non si impostano: il programma non dipende dai loro valori, e l'istanza
 * dello shader, che è una per processo, resta com'era.
 *
 * Da sapere: la finestra dell'app è sRGB in quasi tutti i casi, e questo renderer anche; una finestra in gamma
 * larga potrebbe chiedere un programma con la conversione di colore in più (voce per il dispositivo).
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal object GlassOffscreenWarmer {

  private const val RefusedSync = HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND or
    HardwareRenderer.SYNC_CONTEXT_IS_STOPPED or
    HardwareRenderer.SYNC_FRAME_DROPPED

  /** Il fondale della cattura finta: opaco, perché un'immagine vuota potrebbe far saltare la catena. */
  private const val BackdropArgb = android.graphics.Color.GRAY

  fun draw(step: GlassWarmStep, density: Float, blocking: Boolean, handler: Handler, onReleased: () -> Unit) {
    val d = if (density > 0f && density.isFinite()) density else 1f
    val side = max(1, (GlassWarmSlabSideDp * d).roundToInt())
    val surface = glassWarmSlabSurface(d)
    val nodeSize = Size(side.toFloat(), side.toFloat())
    val scale = surface.effectiveCaptureScale(nodeSize)
    val frame = Frame(side, side)
    try {
      when (step) {
        GlassWarmStep.SlabBody -> {
          val scope = WarmEffectScope(d * scale, Size(side * scale, side * scale))
          scope.apply(surface.effects)
          val effect = scope.renderEffect ?: error("la catena del corpo è vuota")
          frame.recordBody(effect.asAndroidRenderEffect(), scope.padding, scale)
        }
        GlassWarmStep.SlabLight -> frame.recordLight(d) { surface.onDrawLight(this) }
        GlassWarmStep.GroupBody -> {
          val inputs = surface.drawInputs(nodeSize, scale = scale)
          val upstream = GlassBodyChain().upstream(inputs.saturation, inputs.blurPx, inputs.mix)
          val body = RuntimeShaderCacheImpl().obtainEngineShader("GlassBody8", GlassShaderSources.Body8)
            ?: error("${GlassShaderSources.Body8.name} non è disponibile")
          val effect = RuntimeShaderEffect(body, "content").asAndroidRenderEffect()
          val chain = if (upstream == null) effect else android.graphics.RenderEffect.createChainEffect(effect, upstream)
          frame.recordBody(chain, inputs.paddingPx, scale)
        }
        GlassWarmStep.GroupLight -> {
          val light = RuntimeShaderCacheImpl().obtainEngineShader("GlassLight8", GlassShaderSources.Light8)
            ?: error("${GlassShaderSources.Light8.name} non è disponibile")
          val brush = ShaderBrush(light.asComposeShader())
          frame.recordLight(d) {
            // I due modi di comporre della luce a riposo: prima la parte che somma, poi il filo.
            drawRect(brush = brush, blendMode = BlendMode.Plus)
            drawRect(brush = brush)
          }
        }
      }
      frame.submit(blocking, handler, onReleased)
    } catch (t: Throwable) {
      frame.release()
      throw t
    }
  }

  /** Uno scope degli effetti senza nodo: taglia e densità della cattura di un tasto. */
  private class WarmEffectScope(density: Float, size: Size) : BackdropEffectScopeImpl() {
    override val shape: Shape = FluidCapsuleShape

    init {
      this.density = density
      this.size = size
    }
  }

  /** Un fotogramma fuori schermo: radice, cattura finta, renderer e lettore, rilasciati insieme. */
  private class Frame(private val width: Int, private val height: Int) {
    private val reader = ImageReader.newInstance(
      width,
      height,
      PixelFormat.RGBA_8888,
      1,
      HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
    )
    // Non `apply`: lì dentro `width` e `height` sarebbero quelli del nodo, cioè zero (lo stesso tranello di
    // `ProbeReader` nella pagina «Misure»).
    private val root = RenderNode("FluidGlassWarmUp").also { it.setPosition(0, 0, width, height) }
    private val capture = RenderNode("FluidGlassWarmUp.cattura")
    private val renderer = HardwareRenderer().apply {
      setName("FluidGlassWarmUp")
      setContentRoot(root)
      setSurface(reader.surface)
      isOpaque = false
      start()
    }
    private var released = false

    /**
     * Il corpo come lo disegna il nodo del vetro: la cattura (qui un fondale pieno) con la catena come
     * `RenderEffect`, rimpicciolita della frazione di risoluzione e posata al suo margine.
     */
    fun recordBody(effect: android.graphics.RenderEffect, padding: Float, scale: Float) {
      val capturePx = max(1, ceil(width * scale + 2f * padding).toInt())
      capture.setPosition(0, 0, capturePx, capturePx)
      val inside = capture.beginRecording()
      try {
        inside.drawColor(BackdropArgb)
      } finally {
        capture.endRecording()
      }
      capture.setRenderEffect(effect)
      val canvas = root.beginRecording()
      try {
        canvas.save()
        canvas.scale(1f / scale, 1f / scale)
        canvas.translate(-padding, -padding)
        canvas.drawRenderNode(capture)
        canvas.restore()
      } finally {
        root.endRecording()
      }
    }

    /** La luce come la disegna il nodo: sul rettangolo della lastra, a risoluzione piena. */
    fun recordLight(density: Float, block: DrawScope.() -> Unit) {
      val canvas = root.beginRecording()
      try {
        CanvasDrawScope().draw(
          Density(density),
          LayoutDirection.Ltr,
          androidx.compose.ui.graphics.Canvas(canvas),
          Size(width.toFloat(), height.toFloat()),
        ) { block() }
      } finally {
        root.endRecording()
      }
    }

    fun submit(blocking: Boolean, handler: Handler, onReleased: () -> Unit) {
      if (!blocking) {
        reader.setOnImageAvailableListener({ source ->
          try {
            source.acquireLatestImage()?.close()
          } catch (_: IllegalStateException) {
            // Il lettore si è chiuso mentre l'avviso era in coda: niente da prendere.
          }
          if (release()) onReleased()
        }, handler)
        handler.postDelayed({ if (release()) onReleased() }, GlassWarmUpReleaseTimeoutMillis)
      }
      val result = renderer.createRenderRequest()
        .setVsyncTime(System.nanoTime())
        .setWaitForPresent(blocking)
        .syncAndDraw()
      if (result and RefusedSync != 0) {
        release()
        throw IllegalStateException("fotogramma fuori schermo rifiutato (syncAndDraw = $result)")
      }
      if (blocking && release()) onReleased()
    }

    /** Vero se ha rilasciato adesso; falso se era già fatto. */
    fun release(): Boolean {
      if (released) return false
      released = true
      reader.setOnImageAvailableListener(null, null)
      renderer.stop()
      renderer.destroy()
      reader.close()
      root.discardDisplayList()
      capture.discardDisplayList()
      return true
    }
  }
}
