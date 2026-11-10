package dev.antigravity.fluidengine.ui.glass.field

import android.util.Log
import dev.antigravity.fluidengine.ui.fluidphysics.PolyMorphDispersionShaderString
import dev.antigravity.fluidengine.ui.fluidphysics.PolyMorphRefractionShaderString
import dev.antigravity.fluidengine.ui.fluidphysics.SlabGroupDispersionShaderString
import dev.antigravity.fluidengine.ui.fluidphysics.SlabGroupRefractionShaderString
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.AmbientHighlightShaderString
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.DefaultHighlightShaderString
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.RoundedRectRefractionShaderString
import dev.antigravity.fluidengine.ui.glass.backdrop.internal.RoundedRectRefractionWithDispersionShaderString
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import java.util.concurrent.ConcurrentHashMap

/**
 * Una sorgente AGSL completa dell'engine, con il nome che la riconosce ovunque.
 *
 * Il nome è quello della costante Kotlin, di proposito: lo stesso nome si legge in logcat, nei verdetti di
 * `FluidGlassDiagnostics`, nel banco (`glassbench.py check`), nel lint delle sorgenti e nel test strumentato,
 * e chi lo trova in uno di quei posti arriva al file senza tradurre niente.
 */
internal class GlassShaderSource(val name: String, val text: String)

/**
 * L'elenco di tutte le sorgenti AGSL complete dell'engine: ciò che la sonda sa provare.
 *
 * Un elenco scritto a mano può dimenticare una sorgente nuova, ed è così che una sorgente sfugge a ogni
 * controllo. Per questo `ShaderSourceLintTest` legge i sorgenti Kotlin, trova ogni stringa con un `main`, e
 * fallisce se una non è qui o se il testo di qui non è quello del file.
 */
internal object GlassShaderSources {
  // Il renderer di Kyant (bd/internal/Shaders.kt): lente e riflesso di `Classic`.
  val RoundedRectRefraction =
    GlassShaderSource("RoundedRectRefractionShaderString", RoundedRectRefractionShaderString)
  val RoundedRectRefractionWithDispersion = GlassShaderSource(
    "RoundedRectRefractionWithDispersionShaderString",
    RoundedRectRefractionWithDispersionShaderString,
  )
  val DefaultHighlight = GlassShaderSource("DefaultHighlightShaderString", DefaultHighlightShaderString)
  val AmbientHighlight = GlassShaderSource("AmbientHighlightShaderString", AmbientHighlightShaderString)

  // Fluid-physics (fp/FluidPhysicsShaders.kt): il campo dei morph, senza e con la frangia.
  val SlabGroupRefraction = GlassShaderSource("SlabGroupRefractionShaderString", SlabGroupRefractionShaderString)
  val PolyMorphRefraction = GlassShaderSource("PolyMorphRefractionShaderString", PolyMorphRefractionShaderString)
  val SlabGroupDispersion = GlassShaderSource("SlabGroupDispersionShaderString", SlabGroupDispersionShaderString)
  val PolyMorphDispersion = GlassShaderSource("PolyMorphDispersionShaderString", PolyMorphDispersionShaderString)

  /** Nell'ordine in cui `compileAll` le prova: prima `Classic`, poi Fluid-physics. */
  val all: List<GlassShaderSource> = listOf(
    RoundedRectRefraction,
    RoundedRectRefractionWithDispersion,
    DefaultHighlight,
    AmbientHighlight,
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
 * verdetto si legge in `FluidGlassDiagnostics`, e il test strumentato della galleria compila tutto. Quale
 * famiglia ripiega, e fin dove, lo decide chi chiede: qui si danno solo i fatti.
 */
internal object GlassFieldHealth {

  private const val LogTag = "FluidGlass"

  @Volatile
  internal var probe: GlassShaderProbe = deviceProbe()
    private set

  /** Vero se il dispositivo ha AGSL e il suo compilatore accetta tutte [family]. */
  fun healthy(family: Iterable<GlassShaderSource>): Boolean =
    isRuntimeShaderSupported() && probe.healthy(family)

  /** I verdetti dati finora in questo processo: nome della sorgente → `null` o l'errore. */
  fun verdicts(): Map<String, Throwable?> = probe.verdicts()

  /** Compila ogni sorgente non ancora provata. Sotto Android 13 non c'è un compilatore AGSL: vuota. */
  fun compileAll(): Map<String, Throwable?> =
    if (isRuntimeShaderSupported()) probe.probeAll() else emptyMap()

  /** Solo per i test dell'engine: dimentica i verdetti. */
  internal fun resetForTesting() {
    probe = deviceProbe()
  }

  private fun deviceProbe() = GlassShaderProbe(
    sources = GlassShaderSources.all,
    compile = { source ->
      if (isRuntimeShaderSupported()) {
        android.graphics.RuntimeShader(source.text)
      } else {
        throw UnsupportedOperationException("AGSL esiste da Android 13")
      }
    },
    onRefused = { source, error ->
      // Senza questa riga un compilatore che rifiuta è indistinguibile da un dispositivo che non può.
      Log.e(LogTag, "Shader AGSL «${source.name}» rifiutato dal compilatore del dispositivo: ${error.message}", error)
    },
  )
}
