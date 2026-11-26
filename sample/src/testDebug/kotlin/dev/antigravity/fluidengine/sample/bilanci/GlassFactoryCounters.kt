package dev.antigravity.fluidengine.sample.bilanci

import java.util.IdentityHashMap
import java.util.WeakHashMap

/**
 * F7.28 · I contatori delle fabbriche, scritti dalle ombre di Robolectric ([CountingRenderNode], [CountingBitmap],
 * [CountingRenderEffect], [CountingRuntimeShader]) e letti dal misuratore dei bilanci ([GlassBalanceMeter]).
 *
 * Contano solo mentre una misura e' accesa ([active]): una prova che non li usa non paga niente oltre un confronto.
 * Tutto dal filo principale di Robolectric (dove Compose disegna); il blocco serve solo a una lettura da un altro filo.
 */
object GlassFactoryCounters {

  @Volatile
  var active: Boolean = false

  private val lock = Any()
  private var renderNodes = 0L
  private var bitmaps = 0L
  private var bitmapPixels = 0L
  private var renderEffects = 0L
  private var runtimeShaders = 0L

  /** Il nome di ogni RenderNode nato a misura accesa: serve solo ai messaggi («graphicsLayer», il nome della prova). */
  private val names = WeakHashMap<Any, String>()

  /**
   * Le registrazioni di ogni RenderNode **dentro il disegno della prova** (fra l'inizio e la fine della registrazione di
   * [harnessNode]): è il fotogramma, una revisione sola del mondo. Si svuotano a ogni [takeRecordings].
   */
  private var recordings = IdentityHashMap<Any, Int>()

  /**
   * Le registrazioni fuori dal disegno della prova: il disegno di sistema di Robolectric (che rigioca i layer in una
   * traversata sua) e chi registra dopo il disegno (la radice della sonda dello sfondo). Si stampano, non cadono.
   */
  private var outside = IdentityHashMap<Any, Int>()

  /** I nomi dei RenderNode nati dall'ultima [takeCreated] (per dire chi sono le fabbriche contate). */
  private var created = ArrayList<String>()

  /** Vero dove ogni RenderNode che nasce va attribuito (il moto stabile, la posa): costa una pila per nascita. */
  @Volatile
  var traceCreations: Boolean = false

  /** Vero mentre la prova disegna la finestra nel suo nodo. */
  @Volatile
  private var insideHarness = false

  /** Il nodo della prova che fa da finestra: si registra una volta per fotogramma e non conta. */
  @Volatile
  var harnessNode: Any? = null

  /**
   * La finestra che la prova disegna (la `decorView`). La prova chiama `decorView.draw(canvas)` direttamente, e così lo
   * sfondo della finestra (il suo `ColorDrawable`, con un RenderNode suo) si registra a ogni disegno: sul dispositivo no.
   * Quel nodo non conta.
   */
  @Volatile
  var harnessView: android.view.View? = null

  private val ignored = java.util.Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

  private val backgroundField by lazy {
    runCatching { android.view.View::class.java.getDeclaredField("mBackgroundRenderNode").apply { isAccessible = true } }.getOrNull()
  }

  @JvmStatic
  fun renderNodeCreated(node: Any, name: String?) {
    if (!active) return
    synchronized(lock) {
      renderNodes++
      names[node] = name?.substringAfterLast('.') ?: "?"
      if (created.size < 64) created.add(if (traceCreations) "${name ?: "?"} da ${origin()}" else name ?: "?")
    }
  }

  /** Dà un nome a un nodo senza contarlo (le viste, i nodi di sistema). Solo se non ne ha già uno. */
  @JvmStatic
  fun nameNode(node: Any, name: String?) {
    synchronized(lock) { if (node !in names) names[node] = name?.substringAfterLast('.') ?: "?" }
  }

  /** Chi ha registrato di nuovo un nodo nello stesso fotogramma (il secondo chiamante), per nome del nodo. */
  private var repeats = LinkedHashMap<String, String>()

  @JvmStatic
  fun recorded(node: Any) {
    if (!active) return
    if (node === harnessNode) {
      insideHarness = true
      return
    }
    synchronized(lock) {
      if (node in ignored) return
      val map = if (insideHarness) recordings else outside
      val count = (map[node] ?: 0) + 1
      map[node] = count
      if (count == 2 && insideHarness && repeats.size < 32) {
        repeats["${names[node] ?: "vista"}@${System.identityHashCode(node)}"] = origin()
      }
    }
  }

  /** Il primo chiamante dell'engine o della galleria (fuori dai bilanci), con le due chiamate sopra. */
  private fun origin(): String {
    val frames = Throwable().stackTrace
    val first = frames.indexOfFirst { it.className.startsWith("dev.antigravity.") && ".bilanci." !in it.className }
    if (first < 0) return frames.drop(3).take(2).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}" }
    return frames.drop(first).take(3).joinToString(" < ") { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }
  }

  @JvmStatic
  fun recordingEnded(node: Any) {
    if (node !== harnessNode) return
    insideHarness = false
    val background = harnessView?.let { view -> backgroundField?.get(view) }
    if (background != null) synchronized(lock) { ignored.add(background) }
  }

  @JvmStatic
  fun bitmapCreated(width: Int, height: Int) {
    if (!active) return
    synchronized(lock) {
      bitmaps++
      bitmapPixels += width.toLong() * height
    }
  }

  @JvmStatic
  fun renderEffectCreated() {
    if (active) synchronized(lock) { renderEffects++ }
  }

  @JvmStatic
  fun runtimeShaderCreated() {
    if (active) synchronized(lock) { runtimeShaders++ }
  }

  /** Quanto e' nato finora. */
  fun snapshot(): Factories = synchronized(lock) {
    Factories(renderNodes, bitmaps, bitmapPixels, renderEffects, runtimeShaders)
  }

  /** Le registrazioni nel disegno della prova dall'ultima chiamata, per nodo (col nome, se è nato a misura accesa). */
  fun takeRecordings(): List<Recording> = synchronized(lock) {
    val taken = recordings
    recordings = IdentityHashMap()
    taken.entries.map { (node, count) -> Recording(System.identityHashCode(node), names[node] ?: "vista", count) }
  }

  /** Le registrazioni fuori dal disegno della prova dall'ultima chiamata. */
  fun takeOutside(): List<Recording> = synchronized(lock) {
    val taken = outside
    outside = IdentityHashMap()
    taken.entries.map { (node, count) -> Recording(System.identityHashCode(node), names[node] ?: "vista", count) }
  }

  /** Chi ha registrato due volte un nodo nel disegno della prova, dall'ultima chiamata (nodo → secondo chiamante). */
  fun takeRepeats(): Map<String, String> = synchronized(lock) {
    val taken = repeats
    repeats = LinkedHashMap()
    taken
  }

  /** I nomi dei RenderNode nati dall'ultima chiamata. */
  fun takeCreated(): List<String> = synchronized(lock) {
    val taken = created
    created = ArrayList()
    taken
  }

  /** Azzera tutto (all'inizio di una misura). */
  fun reset() = synchronized(lock) {
    renderNodes = 0
    bitmaps = 0
    bitmapPixels = 0
    renderEffects = 0
    runtimeShaders = 0
    names.clear()
    recordings = IdentityHashMap()
    outside = IdentityHashMap()
    created = ArrayList()
    repeats = LinkedHashMap()
    insideHarness = false
  }

  /** Le fabbriche contate. [layers] sono i RenderNode, cioè i layer di Compose e del vetro e le display list a mano. */
  data class Factories(
    val layers: Long,
    val bitmaps: Long,
    val bitmapPixels: Long,
    val effects: Long,
    val programs: Long,
  ) {
    operator fun minus(other: Factories) = Factories(
      layers - other.layers, bitmaps - other.bitmaps, bitmapPixels - other.bitmapPixels,
      effects - other.effects, programs - other.programs,
    )

    /** Layer, bitmap e programmi: le fabbriche che in moto stabile devono restare a zero. */
    val heavy: Long get() = layers + bitmaps + programs

    override fun toString(): String = "layer $layers, bitmap $bitmaps ($bitmapPixels px), filtri $effects, programmi $programs"
  }

  /** Un nodo registrato [count] volte in un fotogramma. */
  data class Recording(val id: Int, val name: String, val count: Int)
}
