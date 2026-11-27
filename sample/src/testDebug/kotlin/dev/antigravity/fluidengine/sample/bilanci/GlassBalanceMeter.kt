@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package dev.antigravity.fluidengine.sample.bilanci

import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.unit.IntSize
import dev.antigravity.fluidengine.ui.glass.backdrop.FluidGlassDiagnostics
import kotlin.math.max

/**
 * F7.28 · Misura un elemento fase per fase, per confrontarlo con la sua riga del registro ([GlassBalanceRegistry]), e
 * controlla a ogni fotogramma le invarianti che non hanno un tetto:
 *  - **stesso campo, stessa revisione**: nessun layer (RenderNode) registrato due volte nello stesso fotogramma, e nessun
 *    vetro che cattura due volte nello stesso fotogramma (il mondo, in un fotogramma, ha una revisione sola);
 *  - **il filtro dentro il rettangolo più il margine ottico**: ogni cattura è grande quanto il vetro (o la sua finestra,
 *    nei menu) alla scala scelta, più al massimo il margine dichiarato (`@margine`, di serie zero) per lato;
 *  - **nessuna fabbrica a ogni fotogramma** (F7.29): layer, bitmap o programmi nuovi in almeno metà dei fotogrammi del
 *    moto stabile. Il tetto `fab` del moto è il massimo per fotogramma, e una lista che scorre lo raggiunge quando
 *    entra una riga; questa invariante ferma ciò che nasce a ogni fotogramma (o a fotogrammi alterni) anche sotto il
 *    tetto.
 *
 * Il misuratore non disegna: chi lo usa disegna un fotogramma e gli passa gli eventi del log `FluidGlassFrame` di quel
 * fotogramma ([frame]). Legge da solo la diagnostica del vetro, i byte del vetro ([usage]) e le fabbriche
 * ([GlassFactoryCounters], da accendere prima di [GlassBalanceMeter]).
 *
 * @param usage i byte del vetro usati adesso, residenti e in volo.
 * @param root la radice di Compose, per la taglia dei vetri (null: il filtro non si controlla).
 * @param margin il margine ottico per lato, in pixel.
 */
internal class GlassBalanceMeter(
  private val label: String,
  private val usage: () -> Long,
  private val root: () -> LayoutNode?,
  private val margin: Int = 0,
) {
  private val baseNodes = liveNodes()
  private val baseLayers = liveLayers()
  private val baseBytes = usage()

  /** Le misure per fase, nell'ordine in cui le fasi sono finite. */
  val results = LinkedHashMap<String, Map<BalanceColumn, Long>>()

  /** Le invarianti violate (doppie registrazioni, filtri oltre il margine), con fase e fotogramma. */
  val violations = ArrayList<String>()

  /** Le catture di cui non si è trovato il vetro nell'albero (il filtro non si è potuto controllare). */
  var uncheckedCaptures = 0
    private set
  var checkedCaptures = 0
    private set

  /** Il margine più largo misurato fra una cattura e il suo vetro, per lato, in pixel (per scrivere `@margine`). */
  var measuredMargin = 0
    private set

  /** Le note di diagnosi: registrazioni doppie fuori dal disegno della prova, fabbriche a riposo o in moto stabile. */
  val notes = LinkedHashSet<String>()

  private var phase: String? = null
  private var stable: IntRange? = null
  private var frameIndex = 0
  private var maxNodes = 0
  private var maxLayers = 0
  private var capturesPerFrame = 0
  private var captures = 0L
  private var pixels = 0L
  private var bytes = 0L
  private var factories = 0L
  private var effects = 0L
  private var stableFactories = 0L
  private var stableEffects = 0L
  private var stableFrameCount = 0
  private var stableFactoryFrames = 0
  private var recordings = 0L
  /** F7.32d · Nella posa: i fotogrammi disegnati, quelli con registrazioni, il tratto più lungo di fila e quello in corso. */
  private var quietFrames = 0
  private var quietRecordedFrames = 0
  private var quietLongestRun = 0
  private var quietRun = 0
  private var lastCaptures = 0L
  private var lastFactories = GlassFactoryCounters.snapshot()
  private var quietStart: Sample? = null

  /** Un campione di quiete: ciò che deve restare uguale fra due istanti separati di una posa. */
  data class Sample(val nodes: Int, val layers: Int, val bytes: Long, val captures: Long, val factories: GlassFactoryCounters.Factories)

  fun sample() = Sample(liveNodes(), liveLayers(), usage(), FluidGlassDiagnostics.totalCaptures, GlassFactoryCounters.snapshot())

  /**
   * Comincia una fase. [stableFrames] sono i fotogrammi (contati da zero) del moto stabile: lì le fabbriche contano per
   * fotogramma, e il massimo va nel registro; null vuol dire tutta la fase a totale.
   */
  fun begin(name: String, stableFrames: IntRange? = null) {
    check(phase == null) { "$label: la fase $phase non è finita" }
    phase = name
    stable = stableFrames
    GlassFactoryCounters.traceCreations = stableFrames != null
    frameIndex = 0
    maxNodes = liveNodes()
    maxLayers = liveLayers()
    capturesPerFrame = 0
    captures = 0
    pixels = 0
    bytes = usage() - baseBytes
    factories = 0
    effects = 0
    stableFactories = 0
    stableEffects = 0
    stableFrameCount = 0
    stableFactoryFrames = 0
    recordings = 0
    quietFrames = 0
    quietRecordedFrames = 0
    quietLongestRun = 0
    quietRun = 0
    lastCaptures = FluidGlassDiagnostics.totalCaptures
    lastFactories = GlassFactoryCounters.snapshot()
    GlassFactoryCounters.takeRecordings()
    GlassFactoryCounters.takeBornNodes()
  }

  /** Comincia la posa: il primo dei due campioni separati. */
  fun beginQuiet(name: String = "posa") {
    begin(name)
    quietStart = sample()
    GlassFactoryCounters.traceCreations = true
  }

  /**
   * Un fotogramma appena disegnato: [events] sono i messaggi del log `FluidGlassFrame` usciti in questo fotogramma,
   * [finishes] le finiture dei viaggi (dall'osservatore `GlassCostLevers.onCapture`).
   */
  fun frame(events: List<String>, finishes: List<IntSize> = emptyList()) {
    val name = checkNotNull(phase) { "$label: fotogramma fuori da una fase" }
    val nodes = liveNodes()
    val layers = liveLayers()
    maxNodes = max(maxNodes, nodes)
    maxLayers = max(maxLayers, layers)
    val total = FluidGlassDiagnostics.totalCaptures
    val delta = (total - lastCaptures).toInt()
    lastCaptures = total
    captures += delta
    capturesPerFrame = max(capturesPerFrame, delta)
    bytes = max(bytes, usage() - baseBytes)
    val made = GlassFactoryCounters.snapshot()
    val born = made - lastFactories
    lastFactories = made
    factories += born.heavy
    effects += born.effects
    val createdNow = GlassFactoryCounters.takeCreated()
    val bornNodes = GlassFactoryCounters.takeBornNodes()
    val inStable = stable?.contains(frameIndex) == true
    if (inStable) {
      stableFactories = max(stableFactories, born.heavy)
      stableEffects = max(stableEffects, born.effects)
      // F7.29 · Una fabbrica a ogni fotogramma: si contano i fotogrammi stabili che ne hanno (la guardia è in [end]).
      stableFrameCount++
      if (born.heavy > 0) stableFactoryFrames++
    }
    if ((inStable || quietStart != null) && born.heavy > 0) {
      val owners = runCatching { LayerOwners.describe(root(), bornNodes) }.getOrDefault(emptyMap())
      notes += "$name, fotogramma $frameIndex: fabbriche nuove ($born), layer nati: ${createdNow.groupingBy { it }.eachCount()}" +
        (if (owners.isNotEmpty()) "; di chi: $owners" else "")
    }
    for (twice in GlassFactoryCounters.takeOutside().filter { it.count > 1 }) {
      notes += "$name: ${twice.name} registrato ${twice.count} volte fuori dal disegno della prova (non conta)"
    }
    val recorded = GlassFactoryCounters.takeRecordings()
    val repeats = GlassFactoryCounters.takeRepeats()
    recordings += recorded.sumOf { it.count }
    if (quietStart != null) {
      quietFrames++
      quietRun = if (recorded.isNotEmpty()) quietRun + 1 else 0
      if (recorded.isNotEmpty()) quietRecordedFrames++
      quietLongestRun = max(quietLongestRun, quietRun)
    }
    if (quietStart != null && recorded.isNotEmpty()) {
      notes += "$name: registrati a riposo ${recorded.map { it.name }.groupingBy { it }.eachCount().toSortedMap()}"
    }
    if (quietStart != null) {
      val asked = events.filter { " kind=redrawRequest " in it }.map { it.substringAfter("reason=").substringBefore(' ') }
      if (asked.isNotEmpty()) notes += "$name: il vetro chiede di ridisegnarsi a riposo (${asked.groupingBy { it }.eachCount()})"
    }
    // Stesso campo, stessa revisione: vale in moto e in posa. In entrata e in uscita il primo fotogramma misura e posa
    // dentro il disegno, e un layer appena posato si può registrare di nuovo: lo si scrive fra le note, col chiamante.
    val guarded = name.startsWith("moto") || name.startsWith("posa")
    for (twice in recorded.filter { it.count > 1 }) {
      val who = repeats["${twice.name}@${twice.id}"]?.let { " (di nuovo da $it)" } ?: ""
      val text = "il layer ${twice.name}@${twice.id} registrato ${twice.count} volte nello stesso fotogramma$who"
      if (guarded) violations += "$label/$name, fotogramma $frameIndex: $text (stesso campo, stessa revisione)"
      else notes += "$name, fotogramma $frameIndex: $text"
    }
    val parsed = events.mapNotNull(::parseCapture)
    parsed.groupBy { it.id }.filterValues { it.size > 1 }.forEach { (id, list) ->
      violations += "$label/$name, fotogramma $frameIndex: il vetro $id ha catturato ${list.size} volte nello stesso fotogramma"
    }
    for (capture in parsed) pixels = max(pixels, capture.size.width.toLong() * capture.size.height)
    for (finish in finishes) pixels = max(pixels, finish.width.toLong() * finish.height)
    if (parsed.isNotEmpty()) checkFilters(name, parsed)
    frameIndex++
  }

  /** Finisce la fase e ne restituisce la misura (la tiene anche in [results]). */
  fun end(): Map<BalanceColumn, Long> {
    val name = checkNotNull(phase) { "$label: nessuna fase da finire" }
    val quiet = quietStart
    val release = name == "rilascio"
    if (quiet != null) {
      val now = sample()
      if (now.nodes != quiet.nodes || now.layers != quiet.layers || now.bytes != quiet.bytes) {
        violations += "$label/$name: due campioni della posa diversi: nodi ${quiet.nodes} → ${now.nodes}, " +
          "layer ${quiet.layers} → ${now.layers}, byte ${quiet.bytes} → ${now.bytes}"
      }
    }
    val stableRange = stable
    // F7.29 · Una fabbrica a ogni fotogramma. Una lista che scorre crea i layer di ciò che entra in vista solo ogni tanto
    // (a 12 px per fotogramma una riga ogni 4–12 fotogrammi, tutta in un fotogramma), e le miniature o le cifre che
    // arrivano a scaglioni in qualche fotogramma vicino: il tetto `fab` ne dice il costo. Layer, bitmap o programmi
    // nuovi in almeno metà dei fotogrammi stabili vogliono dire che qualcosa nasce a ogni fotogramma (o a fotogrammi
    // alterni): rosso anche sotto il tetto. Almeno tre, perché un tratto stabile corto con una riga che entra non basta.
    if (stableRange != null && stableFactoryFrames >= 3 && stableFactoryFrames * 2 >= stableFrameCount) {
      violations += "$label/$name, fotogrammi stabili: layer, bitmap o programmi nuovi in $stableFactoryFrames su " +
        "$stableFrameCount (una fabbrica a ogni fotogramma: almeno la metà, e almeno tre)"
    }
    GlassFactoryCounters.traceCreations = false
    val measured = linkedMapOf(
      BalanceColumn.Nodes to (if (release) liveNodes() - baseNodes else maxNodes - baseNodes).toLong(),
      BalanceColumn.Layers to (if (release) liveLayers() - baseLayers else maxLayers - baseLayers).toLong(),
      BalanceColumn.CapturesPerFrame to capturesPerFrame.toLong(),
      BalanceColumn.Captures to captures,
      BalanceColumn.Pixels to pixels,
      BalanceColumn.KiB to ((if (release) usage() - baseBytes else bytes).coerceAtLeast(0) + 1023) / 1024,
      BalanceColumn.Factories to if (stableRange != null) stableFactories else factories,
      BalanceColumn.Effects to if (stableRange != null) stableEffects else effects,
    )
    // I ridisegni contano nella posa: in entrata e in moto un layer che si registra è il lavoro della fase.
    if (quiet != null) measured[BalanceColumn.Redraws] = recordings
    // F7.32d · La cadenza a riposo, per il motivo della riga: a ogni fotogramma (un'animazione che non si ferma) o a
    // scatti (un passo al secondo con la sua molla).
    if (quiet != null && quietRecordedFrames > 0) {
      notes += "$name: registrazioni in $quietRecordedFrames fotogrammi su $quietFrames, il tratto più lungo di fila $quietLongestRun"
    }
    results[name] = measured
    phase = null
    stable = null
    quietStart = null
    return measured
  }

  /**
   * Confronta le fasi misurate con le righe di [id] in [file]. Restituisce i problemi: una fase misurata senza riga, una
   * colonna oltre il tetto, una invariante violata. Stampa anche, per ogni fase, la riga misurata (per scrivere o
   * correggere il registro) e i tetti che si potrebbero abbassare.
   */
  fun compare(file: BalanceFile, id: String, version: String = "F7.28", proof: String = "?"): List<String> {
    val problems = ArrayList<String>()
    for (violation in violations) {
      val known = file.defects.firstOrNull { it.id == id && "$id/${it.phase}," in violation && it.key in violation }
      if (known == null) problems += violation
      else println("Bilancio $id, difetto noto (${known.where}): $violation")
    }
    for ((name, measured) in results) {
      val line = buildString {
        append(id.padEnd(28)).append(' ').append(name.padEnd(14))
        for (column in BalanceColumn.entries) append(' ').append((measured[column]?.toString() ?: "-").padStart(7))
        append("  ").append(version).append(' ').append(proof)
      }
      println("Bilancio misurato [${file.name}] | $line")
      val row = file.row(id, name)
      if (row == null) {
        problems += "$id/$name: fase misurata senza riga in ${file.name}. Misurato: ${measured.entries.joinToString { "${it.key.label} ${it.value}" }}"
        continue
      }
      val over = row.exceeded(measured)
      if (over.isNotEmpty()) {
        problems += "$id/$name (${row.where}): ${over.joinToString()}. Se il costo è voluto, alzare il tetto nella riga, " +
          "con la sigla del pacchetto e nel motivo il costo e la ragione"
      }
      val slack = row.slack(measured)
      if (slack.isNotEmpty()) println("Bilancio $id/$name: sotto il tetto (${row.where}): ${slack.joinToString()}")
    }
    if (checkedCaptures + uncheckedCaptures > 0) {
      println("Bilancio $id: filtri controllati su $checkedCaptures catture, $uncheckedCaptures senza il vetro nell'albero; " +
        "margine ottico misurato $measuredMargin px (dichiarato $margin)")
    }
    for (note in notes) println("Bilancio $id, nota: $note")
    return problems
  }

  /** I vetri già trovati nell'albero, per identità: l'albero si gira solo quando arriva un vetro nuovo. */
  private val glassNodes = HashMap<Int, java.lang.ref.WeakReference<androidx.compose.ui.Modifier.Node>>()

  private fun sizeOf(id: Int): IntSize? {
    val known = glassNodes[id]?.get()?.takeIf { it.isAttached }
    if (known != null) return known.requireLayoutCoordinates().size
    val tree = root() ?: return null
    glassNodes.clear()
    collectGlassNodes(tree, glassNodes)
    return glassNodes[id]?.get()?.takeIf { it.isAttached }?.requireLayoutCoordinates()?.size
  }

  private fun checkFilters(name: String, captures: List<Capture>) {
    for (capture in captures) {
      val node = if (capture.window != null) null else sizeOf(capture.id)
      val inner = when {
        capture.window != null -> capture.window
        node != null -> IntSize((node.width * capture.scale).toInt(), (node.height * capture.scale).toInt())
        else -> null
      }
      if (inner == null) {
        uncheckedCaptures++
        continue
      }
      checkedCaptures++
      val extraX = capture.size.width - inner.width
      val extraY = capture.size.height - inner.height
      measuredMargin = max(measuredMargin, (max(extraX, extraY) + 1) / 2)
      if (extraX > 2 * margin || extraY > 2 * margin) {
        violations += "$label/$name, fotogramma $frameIndex: la cattura ${capture.size} del vetro ${capture.id} esce dal " +
          "rettangolo $inner (scala ${capture.scale}) di ${extraX / 2} × ${extraY / 2} px per lato, oltre il margine ottico $margin px"
      }
    }
  }

  private class Capture(val id: Int, val scale: Float, val size: IntSize, val window: IntSize?)

  private fun parseCapture(message: String): Capture? {
    if (" kind=capture " !in message) return null
    val id = message.substringAfter(" id=").substringBefore(' ').toIntOrNull() ?: return null
    val scale = message.substringAfter(" scale=").substringBefore(' ').toFloatOrNull() ?: return null
    val size = CaptureSize.find(message) ?: return null
    val window = Window.find(message)?.let {
      val (left, top, right, bottom) = it.destructured
      IntSize(right.toInt() - left.toInt(), bottom.toInt() - top.toInt())
    }
    return Capture(id, scale, IntSize(size.groupValues[1].toInt(), size.groupValues[2].toInt()), window)
  }

  companion object {
    private val CaptureSize = Regex("""\bsize=(\d+) x (\d+)\b""")
    private val Window = Regex("""\bwindow=IntRect\.fromLTRB\((-?\d+), (-?\d+), (-?\d+), (-?\d+)\)""")

    fun liveNodes(): Int = FluidGlassDiagnostics.liveNodes.values.sum()

    fun liveLayers(): Int = FluidGlassDiagnostics.liveLayers.values.sum()

    /**
     * La radice di Compose della finestra di [view], senza passare dalle API di prova (che aspettano l'inattività e
     * cambierebbero il copione): la prima vista che è una radice di Compose, dal suo proprietario della semantica.
     */
    fun composeRoot(view: android.view.View): LayoutNode? {
      if (view is androidx.compose.ui.platform.ViewRootForTest) {
        return view.semanticsOwner.unmergedRootSemanticsNode.layoutInfo as LayoutNode
      }
      if (view is android.view.ViewGroup) {
        for (i in 0 until view.childCount) {
          val found: LayoutNode? = composeRoot(view.getChildAt(i))
          if (found != null) return found
        }
      }
      return null
    }

    /** Ogni nodo del vetro (`DrawBackdropNode`) dell'albero, per identità (l'`id=` del log). */
    fun collectGlassNodes(root: LayoutNode, out: MutableMap<Int, java.lang.ref.WeakReference<androidx.compose.ui.Modifier.Node>>) {
      fun visit(layout: LayoutNode) {
        var node: androidx.compose.ui.Modifier.Node? = layout.nodes.head
        while (node != null) {
          if (node.javaClass.name.endsWith(".DrawBackdropNode") && node.isAttached) {
            out[System.identityHashCode(node)] = java.lang.ref.WeakReference(node)
          }
          node = node.child
        }
        for (child in layout.children) visit(child)
      }
      visit(root)
    }
  }
}
