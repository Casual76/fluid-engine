package dev.antigravity.fluidengine.ui.glass.field.trace

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import java.io.File
import java.util.Locale
import kotlin.math.abs

/*
 * Le tracce del banco del movimento (piano 4.3, schema in `tools/glassbench/TRACE.md`): il modello e
 * lo scrittore.
 *
 * Stanno fra i sorgenti di TEST e non in `main` (il piano le metteva in `gf/trace/`): oggi nessun codice
 * di produzione scrive una traccia, le scrivono solo i test che le esportano, e un JSON scritto a mano con
 * le sue regole di formato non ha niente da fare in ogni APK che usa l'engine. Quello che deve stare in
 * `main` sono le funzioni di fotogramma (`menuJourneyFrame`, `pressFrame`...) che i composable e i test
 * chiameranno insieme: quelle nascono con le fasi che le estraggono, e accanto al codice che le usa.
 */

/**
 * Un fotogramma: l'istante e gli elementi nell'ordine in cui si disegnano, nel vocabolario delle scene.
 *
 * Gli elementi sono mappe e non classi: il vocabolario e' quello del banco (`kind`, `pieces`, `ring`,
 * `uniforms`, `content`...) e cresce con lui; una classe per ogni campo andrebbe tenuta al passo con un
 * file Python, e il validatore di `glassmotion.py` controlla gia' la forma, con il punto esatto.
 */
internal class GlassTraceFrame(val tMs: Double, val elements: List<Map<String, Any?>>)

/**
 * Una traccia: la testa e i fotogrammi.
 *
 * [head] porta i campi facoltativi della testa (`background`, `page`, `scene`, `palette`, `apple`,
 * `events`, `probes`, `_`), scritti cosi' come sono.
 */
internal class GlassTrace(
  val name: String,
  val phase: String,
  val density: Float,
  val theme: String,
  val widthDp: Float,
  val heightDp: Float,
  val frames: List<GlassTraceFrame>,
  val fps: Int = 60,
  val head: Map<String, Any?> = emptyMap(),
) {
  init {
    require(NamePattern.matches(name)) { "nome di traccia non valido: $name" }
    require(PhasePattern.matches(phase)) { "fase non valida: $phase" }
    require(frames.isNotEmpty()) { "una traccia senza fotogrammi" }
    for (i in 1 until frames.size) {
      require(frames[i].tMs > frames[i - 1].tMs) { "$name: i tempi devono crescere (fotogramma $i)" }
    }
    // TRACE.md: il fotogramma a t = 0 c'e' sempre, ed e' quello che `apple.t0Frame` indica nel video.
    require(frames.any { abs(it.tMs) < 1e-9 }) { "$name: manca il fotogramma a t = 0" }
  }

  /** La traccia come albero JSON, nell'ordine dei campi di TRACE.md. */
  fun toJson(): Map<String, Any?> {
    val out = LinkedHashMap<String, Any?>()
    out["schema"] = 1
    out["name"] = name
    out["phase"] = phase
    out["fps"] = fps
    // Facoltativo, e il validatore lo vuole positivo: una scena ferma (un fotogramma a t = 0) non lo porta.
    if (frames.last().tMs > 0.0) out["durationMs"] = frames.last().tMs
    out["density"] = density
    out["theme"] = theme
    out["size"] = listOf(widthDp, heightDp)
    out.putAll(head)
    out["frames"] = frames.map { frame -> linkedMapOf("t" to frame.tMs, "elements" to frame.elements) }
    return out
  }

  fun encode(): String = GlassTraceJson.encode(toJson())

  fun write(file: File) {
    file.parentFile?.mkdirs()
    file.writeText(encode(), Charsets.UTF_8)
  }

  companion object {
    private val NamePattern = Regex("^[a-z0-9][a-z0-9-]*$")
    private val PhasePattern = Regex("^[A-Za-z0-9-]+$")
  }
}

/** L'istante del fotogramma [index] a [fps]: `i * 1000 / fps`, come lo chiede TRACE.md. */
internal fun glassTraceTimeMs(index: Int, fps: Int = 60): Double = index * 1000.0 / fps

/** L'istante in nanosecondi dello stesso fotogramma: l'orologio finto che alimenta molle e goccia. */
internal fun glassTraceTimeNanos(index: Int, fps: Int = 60): Long = index * 1_000_000_000L / fps

/**
 * Una lunghezza in dp nella forma del banco (`"12.345dp"`): tre decimali bastano (TRACE.md), ma un
 * valore piccolo e diverso da zero non deve diventare zero — l'altezza della lente tenuta a un decimillesimo
 * di pixel finirebbe divisore nullo nello shader. Quei valori si scrivono in notazione esponenziale, che il
 * banco accetta.
 */
internal fun glassDp(value: Float): String = glassDp(value.toDouble())

internal fun glassDp(value: Double): String {
  require(value.isFinite()) { "una lunghezza non finita: $value" }
  val plain = GlassTraceJson.fixed(value, 3)
  return if (plain == "0" && value != 0.0) {
    String.format(Locale.ROOT, "%.3E", value) + "dp"
  } else {
    plain + "dp"
  }
}

/** Un colore non premoltiplicato `#RRGGBBAA`, la forma degli uniform `layout(color)`. */
internal fun glassColor(color: Color): String {
  val argb = color.toArgb()
  val a = (argb ushr 24) and 0xFF
  val r = (argb ushr 16) and 0xFF
  val g = (argb ushr 8) and 0xFF
  val b = argb and 0xFF
  return String.format(Locale.ROOT, "#%02X%02X%02X%02X", r, g, b, a)
}

/**
 * Lo scrittore JSON delle tracce. Tre regole, quelle di TRACE.md: punto decimale qualunque sia la lingua
 * della macchina (`Locale.ROOT`: con l'italiano `String.format` scrive la virgola), mai `NaN` o infiniti
 * (un numero non finito e' un difetto da trovare, non da scrivere: si ferma con il punto esatto), e sei
 * decimali al piu'.
 *
 * Le liste di soli numeri o stringhe (un vertice, un uniform, un colore) stanno su una riga: una traccia
 * con un anello di 96 vertici per fotogramma resta leggibile e confrontabile con `git diff` a occhio.
 */
internal object GlassTraceJson {

  fun encode(value: Any?): String = StringBuilder().also { write(value, it, 0, "$") }.append('\n').toString()

  /** [value] con al piu' [decimals] decimali, senza zeri in coda e senza `-0`. */
  fun fixed(value: Double, decimals: Int): String {
    require(value.isFinite()) { "numero non finito: $value" }
    if (value == Math.rint(value) && abs(value) < 1e15) return value.toLong().toString()
    val text = String.format(Locale.ROOT, "%.${decimals}f", value).trimEnd('0').trimEnd('.')
    return if (text == "-0") "0" else text
  }

  private fun write(value: Any?, out: StringBuilder, indent: Int, path: String) {
    when (value) {
      null -> out.append("null")
      is Boolean -> out.append(value)
      is Int, is Long, is Short, is Byte -> out.append(value.toString())
      is Float -> number(value.toDouble(), out, path)
      is Double -> number(value, out, path)
      is Number -> number(value.toDouble(), out, path)
      is String -> string(value, out)
      is Map<*, *> -> obj(value, out, indent, path)
      is List<*> -> array(value, out, indent, path)
      is FloatArray -> array(value.toList(), out, indent, path)
      is Array<*> -> array(value.toList(), out, indent, path)
      else -> throw IllegalArgumentException("$path: tipo non scrivibile in una traccia: ${value::class}")
    }
  }

  private fun number(value: Double, out: StringBuilder, path: String) {
    require(value.isFinite()) { "$path: numero non finito ($value)" }
    out.append(fixed(value, 6))
  }

  private fun string(value: String, out: StringBuilder) {
    out.append('"')
    for (c in value) {
      when {
        c == '"' -> out.append("\\\"")
        c == '\\' -> out.append("\\\\")
        c == '\n' -> out.append("\\n")
        c == '\r' -> out.append("\\r")
        c == '\t' -> out.append("\\t")
        c < ' ' -> out.append(String.format(Locale.ROOT, "\\u%04x", c.code))
        else -> out.append(c)
      }
    }
    out.append('"')
  }

  private fun obj(map: Map<*, *>, out: StringBuilder, indent: Int, path: String) {
    if (map.isEmpty()) {
      out.append("{}")
      return
    }
    out.append("{\n")
    var first = true
    for ((key, item) in map) {
      require(key is String) { "$path: una chiave non testuale ($key)" }
      if (!first) out.append(",\n")
      first = false
      pad(out, indent + 1)
      string(key, out)
      out.append(": ")
      write(item, out, indent + 1, "$path.$key")
    }
    out.append('\n')
    pad(out, indent)
    out.append('}')
  }

  private fun array(list: List<*>, out: StringBuilder, indent: Int, path: String) {
    if (list.isEmpty()) {
      out.append("[]")
      return
    }
    if (list.all { it.isLeaf() }) {
      out.append('[')
      list.forEachIndexed { i, item ->
        if (i > 0) out.append(", ")
        write(item, out, indent, "$path[$i]")
      }
      out.append(']')
      return
    }
    out.append("[\n")
    list.forEachIndexed { i, item ->
      if (i > 0) out.append(",\n")
      pad(out, indent + 1)
      write(item, out, indent + 1, "$path[$i]")
    }
    out.append('\n')
    pad(out, indent)
    out.append(']')
  }

  /** Un valore che sta su una riga: un numero, un testo, o una lista di soli numeri e testi. */
  private fun Any?.isLeaf(): Boolean = when (this) {
    null, is Boolean, is Number, is String -> true
    is List<*> -> all { it == null || it is Boolean || it is Number || it is String }
    is FloatArray -> true
    else -> false
  }

  private fun pad(out: StringBuilder, indent: Int) {
    repeat(indent) { out.append("  ") }
  }
}

/**
 * Dove finiscono le tracce: `<GLASS_TRACE_DIR>/<fase>/<nome>/trace.json`, la cartella in cui il banco del
 * movimento mette i prodotti di quella traccia.
 *
 * **Niente si scrive se la variabile d'ambiente non c'e'** (piano 4.3): i giri normali dei test calcolano
 * e controllano le tracce ma non lasciano file. Il valore e' la cartella che contiene le fasi, assoluta o
 * relativa alla radice del repo; quella delle tracce dell'engine e' `artifacts/liquid-glass/moto`:
 *
 * ```
 * $env:GLASS_TRACE_DIR = "artifacts/liquid-glass/moto"
 * powershell -File artifacts\liquid-glass\tools\build-gradle.ps1 -Task ":engine-ui:testDebugUnitTest --rerun" -Name lg-...
 * ```
 *
 * (`--rerun` perche' Gradle non guarda le variabili d'ambiente: con i test gia' passati salterebbe il task.)
 *
 * La radice del repo si cerca risalendo dalla cartella di lavoro del test (Gradle la mette nel modulo) fino
 * a quella che ha `settings.gradle` e `tools/glassbench/TRACE.md`: un percorso scritto a mano varrebbe solo
 * su questo PC e in questo worktree.
 */
internal object GlassTraceOutput {

  const val Variable = "GLASS_TRACE_DIR"

  fun repoRoot(start: File = File(System.getProperty("user.dir")).absoluteFile): File {
    var dir: File? = start
    while (dir != null) {
      val settings = File(dir, "settings.gradle").isFile || File(dir, "settings.gradle.kts").isFile
      if (settings && File(dir, "tools/glassbench/TRACE.md").isFile) return dir
      dir = dir.parentFile
    }
    error("radice del repo non trovata risalendo da $start (cerco settings.gradle e tools/glassbench/TRACE.md)")
  }

  /** La cartella delle fasi chiesta da [value] (la variabile d'ambiente), o null: niente da scrivere. */
  fun requestedDir(value: String? = System.getenv(Variable), root: () -> File = { repoRoot() }): File? {
    if (value.isNullOrBlank()) return null
    val dir = File(value.trim())
    return if (dir.isAbsolute) dir else File(root(), value.trim())
  }

  /** Il file di [trace] sotto [dir]. */
  fun fileFor(dir: File, trace: GlassTrace): File = File(File(File(dir, trace.phase), trace.name), "trace.json")

  /** Scrive [trace] se la variabile d'ambiente lo chiede; il file scritto, o null. */
  fun writeIfRequested(trace: GlassTrace): File? {
    val dir = requestedDir() ?: return null
    val file = fileFor(dir, trace)
    trace.write(file)
    return file
  }
}
