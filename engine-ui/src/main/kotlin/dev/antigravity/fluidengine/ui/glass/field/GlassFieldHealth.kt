package dev.antigravity.fluidengine.ui.glass.field

import android.os.Build
import android.util.Log
import androidx.annotation.ChecksSdkIntAtLeast
import dev.antigravity.fluidengine.ui.fluidphysics.PolyMorphDispersionShaderString
import dev.antigravity.fluidengine.ui.fluidphysics.PolyMorphRefractionShaderString
import dev.antigravity.fluidengine.ui.fluidphysics.SlabGroupDispersionShaderString
import dev.antigravity.fluidengine.ui.fluidphysics.SlabGroupRefractionShaderString
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.AmbientHighlightShaderString
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.DefaultHighlightShaderString
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.RoundedRectRefractionShaderString
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.RoundedRectRefractionWithDispersionShaderString
import dev.antigravity.fluidengine.ui.glass.backdrop.SharedRuntimeShaders
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.intellij.lang.annotations.Language

/**
 * Il bagliore che segue il dito sul vetro (`GlassTouchHighlight`, `glass/interaction/GlassInteraction.kt`).
 *
 * Lo shader è di Kyant, alla lettera: il catalogo di AndroidLiquidGlass, `InteractiveHighlight.kt`
 * (Apache-2.0, Copyright 2025 Kyant; voce 15 di `LICENSES/AndroidLiquidGlass.md`). Fino alla 3.1.2 era una
 * stringa senza nome dentro la chiamata a `RuntimeShader`: la sonda non la poteva provare, il banco e il test
 * strumentato non la compilavano, e un rifiuto del compilatore faceva cadere l'app alla costruzione del
 * bagliore. Sta qui perché qui ha un nome, sta nell'elenco della sonda, e il banco legge i file di questa
 * cartella.
 */
@Language("AGSL")
internal const val GlassGlowShaderString = """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float core;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * core, dist);
    return color * intensity;
}"""

/**
 * Una sorgente AGSL completa dell'engine, con il nome che la riconosce ovunque.
 *
 * Il nome è quello della costante Kotlin, di proposito: lo stesso nome si legge in logcat, nei verdetti di
 * `FluidGlassDiagnostics`, nel banco (`glassbench.py check`), nel lint delle sorgenti e nel test strumentato,
 * e chi lo trova in uno di quei posti arriva al file senza tradurre niente.
 */
internal class GlassShaderSource(val name: String, val text: String) {

  /**
   * La risposta di [GlassFieldHealth.usable], una volta data. Sta accanto alla sorgente perché a regime chi
   * disegna paghi una lettura e basta: né la mappa dei verdetti né la sonda.
   */
  @Volatile
  var usable: Boolean? = null
}

/**
 * L'elenco di tutte le sorgenti AGSL complete dell'engine: ciò che la sonda sa provare.
 *
 * Un elenco scritto a mano può dimenticare una sorgente nuova, ed è così che una sorgente sfugge a ogni
 * controllo. Per questo `ShaderSourceLintTest` legge i sorgenti Kotlin, trova ogni stringa con un `main`, e
 * fallisce se una non è qui o se il testo di qui non è quello del file.
 */
internal object GlassShaderSources {
  // Il renderer di Kyant (bd/internal/Shaders.kt): lente e riflessi del vetro di oggi.
  val RoundedRectRefraction =
    GlassShaderSource("RoundedRectRefractionShaderString", RoundedRectRefractionShaderString)
  val RoundedRectRefractionWithDispersion = GlassShaderSource(
    "RoundedRectRefractionWithDispersionShaderString",
    RoundedRectRefractionWithDispersionShaderString,
  )
  val DefaultHighlight = GlassShaderSource("DefaultHighlightShaderString", DefaultHighlightShaderString)
  val AmbientHighlight = GlassShaderSource("AmbientHighlightShaderString", AmbientHighlightShaderString)

  // Il bagliore del tocco, dal catalogo di Kyant: la sorgente è in questo file.
  val Glow = GlassShaderSource("GlassGlowShaderString", GlassGlowShaderString)

  // Fluid-physics (fp/FluidPhysicsShaders.kt): il campo dei morph, senza e con la frangia.
  val SlabGroupRefraction = GlassShaderSource("SlabGroupRefractionShaderString", SlabGroupRefractionShaderString)
  val PolyMorphRefraction = GlassShaderSource("PolyMorphRefractionShaderString", PolyMorphRefractionShaderString)
  val SlabGroupDispersion = GlassShaderSource("SlabGroupDispersionShaderString", SlabGroupDispersionShaderString)
  val PolyMorphDispersion = GlassShaderSource("PolyMorphDispersionShaderString", PolyMorphDispersionShaderString)

  /** Nell'ordine in cui `compileAll` le prova: prima il vetro di oggi (lente, riflessi, bagliore), poi Fluid-physics. */
  val all: List<GlassShaderSource> = listOf(
    RoundedRectRefraction,
    RoundedRectRefractionWithDispersion,
    DefaultHighlight,
    AmbientHighlight,
    Glow,
    SlabGroupRefraction,
    PolyMorphRefraction,
    SlabGroupDispersion,
    PolyMorphDispersion,
  )
}

/**
 * Il verdetto del compilatore su ogni sorgente: una compilazione per sorgente, la prima volta che serve.
 *
 * Compilatore e registro degli errori arrivano da fuori perché la logica dei verdetti (si ricorda, si
 * ferma alla prima rifiutata, scrive una riga sola per sorgente) si provi sul JVM, dove `RuntimeShader`
 * non esiste. Le sorgenti sono costanti, quindi un verdetto vale per tutto il processo.
 *
 * @param compile compila la sorgente e lancia se il compilatore la rifiuta.
 * @param onRefused chiamata una volta sola per ogni sorgente rifiutata.
 */
internal class GlassShaderProbe(
  private val sources: List<GlassShaderSource>,
  private val compile: (GlassShaderSource) -> Unit,
  private val onRefused: (GlassShaderSource, Throwable) -> Unit,
) {

  /** Involucro perché `ConcurrentHashMap` non tiene `null`, e «compila» è proprio un errore `null`. */
  private class Verdict(val error: Throwable?)

  private val known = ConcurrentHashMap<String, Verdict>()

  /** `null` se il compilatore accetta [source], altrimenti il suo errore. */
  fun verdict(source: GlassShaderSource): Throwable? {
    known[source.name]?.let { return it.error }
    val fresh = Verdict(runCatching { compile(source) }.exceptionOrNull())
    // Due fili possono compilare la stessa sorgente insieme (succedeva anche prima, ed è innocuo: il
    // risultato è lo stesso): vince il primo, e solo il primo scrive la riga d'errore.
    val previous = known.putIfAbsent(source.name, fresh)
    if (previous != null) return previous.error
    fresh.error?.let { onRefused(source, it) }
    return fresh.error
  }

  /**
   * Vero se tutte [family] compilano. Si ferma alla prima rifiutata, come la sonda di sempre: le
   * successive non si compilano per niente, e il loro verdetto resta da dare.
   */
  fun healthy(family: Iterable<GlassShaderSource>): Boolean = family.all { verdict(it) == null }

  /** I verdetti dati finora, nell'ordine dell'elenco: nome → `null` (compila) o l'errore. */
  fun verdicts(): Map<String, Throwable?> {
    val out = LinkedHashMap<String, Throwable?>()
    for (source in sources) {
      known[source.name]?.let { out[source.name] = it.error }
    }
    return out
  }

  /** Prova ogni sorgente non ancora provata (anche dopo un rifiuto) e restituisce tutti i verdetti. */
  fun probeAll(): Map<String, Throwable?> {
    val out = LinkedHashMap<String, Throwable?>()
    for (source in sources) out[source.name] = verdict(source)
    return out
  }
}

/**
 * La sonda del processo: il compilatore AGSL vero del dispositivo, un verdetto per sorgente.
 *
 * Dalla 3.0.0 alla 3.1.2 una variabile chiamata `flat` (parola riservata di SkSL) faceva rifiutare due
 * shader di Fluid-physics, e la sonda di allora lo diceva con un solo `Log.w`: tutto il motore è rimasto a
 * `Balanced` su ogni dispositivo per una settimana senza che nessuno se ne accorgesse. Da qui: ogni rifiuto
 * è una riga di log a livello **errore** con il nome della sorgente e il messaggio del compilatore, ogni
 * verdetto si legge in `FluidGlassDiagnostics`, e il test strumentato della galleria compila tutto.
 *
 * E ogni rifiuto ripiega, invece di lanciare. Fluid-physics chiede le sue famiglie e scende di livello
 * (`FluidPhysicsShaderHealth`); lente, riflessi e bagliore del vetro di oggi chiedono la propria sorgente con
 * [usable] e, se il compilatore l'ha rifiutata, prendono il ramo che hanno sotto Android 13. Fino alla 3.1.2
 * quei tre non chiedevano: un rifiuto era un'eccezione dentro il disegno, a ogni schermata di vetro.
 */
internal object GlassFieldHealth {

  private const val LogTag = "FluidGlass"

  @Volatile
  internal var probe: GlassShaderProbe = deviceProbe()
    private set

  /**
   * Il dispositivo ha AGSL. Una funzione da chiamare e non una lettura diretta perché i test JVM la fissino
   * vera ([installForTesting]) e attraversino il percorso di disegno vero: sul JVM `SDK_INT` vale zero.
   */
  @Volatile
  private var agslSupported: () -> Boolean = ::isRuntimeShaderSupported

  private val consultations = AtomicInteger()

  /** Vero se il dispositivo ha AGSL e il suo compilatore accetta tutte [family]. */
  fun healthy(family: Iterable<GlassShaderSource>): Boolean =
    agslSupported() && probe.healthy(family)

  /**
   * Vero se [source] si può disegnare: il dispositivo ha AGSL e il suo compilatore la accetta.
   *
   * È la domanda che lente, riflessi e bagliore fanno prima di costruire il proprio shader. Falso, prendono il
   * ramo di Android 12, che esiste già e non è un aspetto nuovo (niente lente, riflesso a tinta piatta,
   * bagliore piatto); un rifiuto lo dice una riga d'errore della sonda, non un'eccezione nel disegno.
   *
   * La prima volta può compilare, una volta per processo, e compila nella cache condivisa degli shader: lo
   * shader che la prova accetta è lo stesso che poi si disegna, quindi la domanda non aggiunge compilazioni.
   * Poi la risposta resta sulla sorgente ([GlassShaderSource.usable]) e a regime costa una lettura.
   *
   * Sotto Android 13 è sempre falsa: per questo il lint di Android la legge come un controllo di versione.
   */
  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
  fun usable(source: GlassShaderSource): Boolean {
    source.usable?.let { return it }
    consultations.incrementAndGet()
    val result = agslSupported() && probe.verdict(source) == null
    source.usable = result
    return result
  }

  /** I verdetti dati finora in questo processo: nome della sorgente → `null` o l'errore. */
  fun verdicts(): Map<String, Throwable?> = probe.verdicts()

  /** Compila ogni sorgente non ancora provata. Sotto Android 13 non c'è un compilatore AGSL: vuota. */
  fun compileAll(): Map<String, Throwable?> =
    if (agslSupported()) probe.probeAll() else emptyMap()

  /** Quante volte [usable] ha dovuto chiedere alla sonda dall'ultimo azzeramento: a regime non cresce. Per i test. */
  internal val consultationsForTesting: Int get() = consultations.get()

  /**
   * Solo per i test dell'engine: AGSL c'è o non c'è come dice [agsl], e il compilatore è [compile] (lancia per
   * rifiutare). I verdetti di prima si dimenticano.
   */
  internal fun installForTesting(
    agsl: Boolean,
    compile: (GlassShaderSource) -> Unit,
    onRefused: (GlassShaderSource, Throwable) -> Unit,
  ) {
    agslSupported = { agsl }
    probe = GlassShaderProbe(GlassShaderSources.all, compile, onRefused)
    forget()
  }

  /** Solo per i test dell'engine: il dispositivo vero e nessun verdetto. */
  internal fun resetForTesting() {
    agslSupported = ::isRuntimeShaderSupported
    probe = deviceProbe()
    forget()
  }

  private fun forget() {
    for (source in GlassShaderSources.all) source.usable = null
    consultations.set(0)
  }

  private fun deviceProbe() = GlassShaderProbe(
    sources = GlassShaderSources.all,
    compile = { source ->
      if (isRuntimeShaderSupported()) {
        // Nella cache condivisa: la compilazione della prova è quella che poi disegna (vedi [usable]).
        SharedRuntimeShaders.obtain(source.text)
      } else {
        throw UnsupportedOperationException("AGSL esiste da Android 13")
      }
    },
    onRefused = { source, error ->
      // Senza questa riga un compilatore che rifiuta è indistinguibile da un dispositivo che non può.
      Log.e(
        LogTag,
        "Shader AGSL «${source.name}» rifiutato dal compilatore del dispositivo, chi lo usa disegna senza: " +
          "${error.message}",
        error,
      )
    },
  )
}
