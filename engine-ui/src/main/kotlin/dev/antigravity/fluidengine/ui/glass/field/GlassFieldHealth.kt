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
   * Il verdetto del compilatore per [GlassFieldHealth.usable], una volta dato: vero se la accetta. Sta accanto
   * alla sorgente perché a regime chi disegna paghi una lettura e basta: né la mappa dei verdetti né la sonda.
   *
   * Solo il verdetto, non la domanda «c'è AGSL?»: quella si rifà a ogni chiamata (costa un confronto), perché
   * in una build di debug il livello di API simulato può andare e venire, e una risposta «no» data sotto la
   * simulazione non deve restare dopo (F1A.7).
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

  // Il materiale `Liquid` (questa cartella): corpo e luce, a una lastra e a otto (cancello C3), e il corpo ad
  // anello. Registrati perché sonda, lint e test strumentato li conoscano; la lastra ferma (`Body1`, `Light1`) la
  // disegna l'innesto di F1A.6; quelle a otto pezzi e l'anello li disegna Fluid-physics (F1B.1: gruppi, viaggi,
  // goccia, sagome libere). Tutti li scalda `GlassWarmUp` (F1A.7), ognuno nella sua famiglia.
  val Body1 = GlassShaderSource("GlassBody1ShaderString", GlassBody1ShaderString)
  val Body8 = GlassShaderSource("GlassBody8ShaderString", GlassBody8ShaderString)
  val BodyFilms8 = GlassShaderSource("GlassBodyFilms8ShaderString", GlassBodyFilms8ShaderString)
  val BodyRing = GlassShaderSource("GlassBodyRingShaderString", GlassBodyRingShaderString)
  val Light1 = GlassShaderSource("GlassLight1ShaderString", GlassLight1ShaderString)
  val Light8 = GlassShaderSource("GlassLight8ShaderString", GlassLight8ShaderString)

  // La striscia del bordo dello scorrimento (F2.4, `GlassScrollEdgeShader.kt`): una sorgente per le due passate.
  // Non decide se il materiale è `Liquid`: se il compilatore la rifiuta resta la fascia di prima, e solo quella.
  val EdgeBlur = GlassEdgeBlurSource
  val MenuContentSlab = GlassShaderSource("GlassContentLensSlabShaderString", GlassContentLensSlabShaderString)
  val MenuContentRing = GlassShaderSource("GlassContentLensRingShaderString", GlassContentLensRingShaderString)

  /** Il vetro di oggi e Fluid-physics: ciò che c'era prima della 4.0.0. Nessuno di questi si scalda. */
  val today: List<GlassShaderSource> = listOf(
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

  /**
   * I programmi del materiale `Liquid`: ognuno sta in una famiglia del riscaldamento (`GlassWarmFamily`), e
   * `ShaderSourceLintTest.everyRegisteredSourceIsOfTodayOrWarmedByExactlyOneFamily` fallisce se una sorgente
   * nuova non sta né qui né in [today], o se sta qui senza una famiglia o in più d'una. È così che la striscia del bordo (F2.4) è entrata nel riscaldamento il giorno in cui si è registrata.
   */
  val liquid: List<GlassShaderSource> =
    listOf(Body1, Body8, BodyFilms8, BodyRing, Light1, Light8, EdgeBlur, MenuContentSlab, MenuContentRing)

  /**
   * Ciò che serve a una superficie di Fluid-physics per disegnarsi col materiale nuovo (F1B.1): il corpo a otto
   * pezzi (lastre, gruppi, viaggi fra lastre) e il corpo ad anello (goccia, sagome libere). La luce è nel corpo,
   * ferma o in viaggio, quindi nessun programma di luce. Se il compilatore ne rifiuta uno la superficie resta al
   * vetro di prima **per intero**: mai una goccia nuova che si posa su una lastra di prima.
   */
  val travel: List<GlassShaderSource> = listOf(Body8, BodyRing)

  /**
   * Nell'ordine in cui `compileAll` le prova: prima il vetro di oggi (lente, riflessi, bagliore), poi
   * Fluid-physics, poi il materiale `Liquid`.
   */
  val all: List<GlassShaderSource> = today + liquid
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
   * Poi il verdetto resta sulla sorgente ([GlassShaderSource.usable]) e a regime costa una lettura più il
   * confronto del livello di API.
   *
   * Sotto Android 13 è sempre falsa: per questo il lint di Android la legge come un controllo di versione.
   * Lo è anche sotto un livello simulato (F1A.7), e smette di esserlo appena la simulazione si toglie: il «no»
   * dell'API non si ricorda, si ricorda solo il verdetto del compilatore.
   */
  @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
  fun usable(source: GlassShaderSource): Boolean {
    if (!agslSupported()) return false
    source.usable?.let { return it }
    consultations.incrementAndGet()
    val result = probe.verdict(source) == null
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
  ) = installForTesting({ agsl }, compile, onRefused)

  /** Come sopra, con AGSL che può andare e venire durante il test (un livello simulato che si toglie). */
  internal fun installForTesting(
    agsl: () -> Boolean,
    compile: (GlassShaderSource) -> Unit,
    onRefused: (GlassShaderSource, Throwable) -> Unit,
  ) {
    agslSupported = agsl
    probe = GlassShaderProbe(GlassShaderSources.all, compile, onRefused)
    forget()
  }

  /** Solo per i test dell'engine: il dispositivo vero e nessun verdetto. */
  internal fun resetForTesting() {
    agslSupported = ::isRuntimeShaderSupported
    probe = deviceProbe()
    forget()
  }

  /**
   * Il gancio dei test che montano composable (Robolectric, `sample/src/testDebug`), da
   * `FluidGlassDiagnostics.forceShaderVerdictsForTesting`: **ogni** sorgente compila ([accept] vero) o è
   * rifiutata, senza toccare il compilatore: «sano» vuol dire «il modello si sceglie come su un dispositivo sano»,
   * non «si può disegnare» (che sotto Robolectric dipende dal modo grafico).
   *
   * Diversamente da [installForTesting], la domanda «c'è AGSL?» resta quella del livello di API (vero, o
   * simulato): sotto Android 13 il gancio non accende niente.
   */
  internal fun installVerdictsForTesting(accept: Boolean) {
    agslSupported = ::isRuntimeShaderSupported
    probe = GlassShaderProbe(
      sources = GlassShaderSources.all,
      compile = { source ->
        if (!accept) throw IllegalStateException("«${source.name}» rifiutata dal gancio di test")
      },
      onRefused = { _, _ -> },
    )
    forget()
  }

  private fun forget() {
    for (source in GlassShaderSources.all) source.usable = null
    consultations.set(0)
  }

  private fun deviceProbe() = GlassShaderProbe(
    sources = GlassShaderSources.all,
    compile = { source ->
      // Il livello **vero**, non quello simulato (F1A.7): chi chiede alla sonda ha già controllato
      // `agslSupported()`, e se una simulazione arrivasse fra la domanda e la compilazione il verdetto
      // registrato sarebbe un rifiuto falso, per tutto il processo.
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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

/**
 * Gli interruttori delle tre leve di F7.4 (piano «iOS in Android», F7, leve 6, 7 e 9), per il lotto A/B sul dispositivo.
 *
 * Esistono perché la regola delle leve di F7 è «ognuna misurata da sola, prima e dopo, sugli stessi scenari»: con un APK
 * solo, la sessione sul tablet spegne una leva alla volta con una proprietà di sistema e riavvia l'app, invece di chiedere
 * un APK per leva. Sono letti **una volta per processo**, alla prima domanda, fuori da ogni percorso per fotogramma: costano
 * una lettura di campo. Come quelli di F7.3 (`GlassCostLevers`), una leva si spegne con
 * `adb shell setprop log.tag.<nome> VERBOSE` prima del lancio (i nomi stanno nei commenti qui sotto e in
 * `artifacts/liquid-glass/sessioni/f7-4/riprese.md`); la metà risoluzione del bordo, che cambia la resa ed è una scelta di
 * Alessio, al contrario **si accende** così.
 *
 * I test JVM li scrivono direttamente, e li rimettono con [resetForTesting].
 */
internal object GlassFieldLevers {

  private fun flag(tag: String): Boolean = runCatching { Log.isLoggable(tag, Log.VERBOSE) }.getOrDefault(false)

  /**
   * Leva 6: il riscaldamento della lastra parte **prima** del primo disegno della finestra, fuori schermo, quando nel primo
   * fotogramma c'è una lastra `Liquid` (`GlassWarmUp`). Spento: il riscaldamento di F1A.7 e basta (dopo il primo
   * fotogramma, a finestra ferma). `log.tag.FluidF74NoEarlyWarm`.
   */
  @Volatile
  var earlyWarmUp: Boolean = !flag("FluidF74NoEarlyWarm")

  /**
   * Leva 7: la sonda rallenta da sola quando le sue letture tornano uguali mentre la pagina si ridisegna altrove
   * ([GlassProbeRest]). Spento: al più una lettura ogni 120 ms a ogni revisione, come prima. `log.tag.FluidF74NoProbeRest`.
   */
  @Volatile
  var probeRest: Boolean = !flag("FluidF74NoProbeRest")

  /**
   * Leva 9, tenuta: la cattura della striscia tiene la sua taglia mentre l'altezza del bordo si anima (la tab bar che si
   * piega) e i soli uniform seguono il bordo dei tasti. `log.tag.FluidF74NoEdgeHold`.
   */
  @Volatile
  var edgeHold: Boolean = !flag("FluidF74NoEdgeHold")

  /**
   * Leva 9, la striscia solo dove lavora: a intensità zero (nessun contenuto sotto) un effetto che dà trasparente senza le
   * due passate, e una passata sola dove il profilo non sfoca (il bordo in basso). `log.tag.FluidF74NoEdgeLean`.
   */
  @Volatile
  var edgeLean: Boolean = !flag("FluidF74NoEdgeLean")

  /**
   * Leva 9, metà risoluzione della cattura della striscia: **cambia la resa** (la coda si dissolve prima, il testo vicino al
   * bordo dei tasti è più morbido), quindi è spenta di serie finché Alessio non la sceglie; la tavola «piena / metà» è in
   * `artifacts/liquid-glass/sessioni/f7-4/`. Si accende con `log.tag.FluidF74EdgeHalf`.
   */
  @Volatile
  var edgeHalf: Boolean = flag("FluidF74EdgeHalf")

  /** Solo per i test: le leve come le dà un dispositivo senza proprietà (le quattro senza resa accese, la metà spenta). */
  fun resetForTesting() {
    earlyWarmUp = true
    probeRest = true
    edgeHold = true
    edgeLean = true
    edgeHalf = false
  }
}
