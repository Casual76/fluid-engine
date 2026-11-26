/*
 * engine-map (4.0, F8.26): i segnaposto e i loro grappoli. Codice puro: si prova sulla JVM. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Un segnaposto: `Marker` di MapKit per SwiftUI (iOS 17), il pallino colorato col simbolo bianco e il nome sotto.
 *
 * @param id unico fra i segnaposto della mappa: e' la chiave della selezione, dei grappoli e delle animazioni.
 * @param tint il colore del pallino; `Unspecified` prende l'accento del tema.
 * @param glyph il simbolo dentro il pallino; null mette il segno del luogo.
 * @param count quante cose rappresenta (le foto scattate in quel punto, in «Luoghi» di Foto): un grappolo somma i
 *   [count] e non i segnaposto. Almeno 1.
 */
@Immutable
data class FluidMapMarker(
  val id: String,
  val point: FluidGeoPoint,
  val title: String,
  val subtitle: String? = null,
  val tint: Color = Color.Unspecified,
  val glyph: ImageVector? = null,
  val count: Int = 1,
) {
  init {
    require(count >= 1) { "Un segnaposto rappresenta almeno una cosa: $count" }
  }
}

/**
 * Un grappolo a un livello di zoom: uno o piu' segnaposto vicini sullo schermo, mostrati come uno solo (il cerchio col
 * numero di MapKit, `MKClusterAnnotation`). Un grappolo di un solo segnaposto e' il segnaposto stesso ([isSingle]).
 *
 * L'[id] e' stabile: lo stesso insieme di segnaposto ha lo stesso id a ogni zoom in cui resta unito, cosi' un grappolo
 * che non cambia non si anima.
 */
@Immutable
class FluidMapCluster internal constructor(
  val id: String,
  val point: FluidGeoPoint,
  val markers: List<FluidMapMarker>,
  /** Il livello (lo zoom intero) a cui appartiene. */
  val level: Int,
  internal val x: Double,
  internal val y: Double,
  /** L'id del grappolo che lo contiene al livello sotto, o null al livello piu' basso. */
  internal val parentId: String?,
) {
  /** Quante cose contiene: la somma dei [FluidMapMarker.count]. */
  val count: Int = markers.sumOf { it.count }

  /** Un segnaposto solo. */
  val isSingle: Boolean get() = markers.size == 1

  /** Il primo segnaposto (per un grappolo di uno, il segnaposto). */
  val first: FluidMapMarker get() = markers.first()

  /** Il riquadro dei suoi segnaposto. */
  val bounds: FluidGeoBounds get() = FluidGeoBounds.of(markers.map { it.point })!!

  override fun equals(other: Any?): Boolean = other is FluidMapCluster && other.id == id && other.level == level
  override fun hashCode(): Int = id.hashCode() * 31 + level
  override fun toString(): String = "FluidMapCluster($id, ${markers.size} segnaposto, livello $level)"
}

/**
 * I grappoli di un elenco di segnaposto a ogni livello di zoom, calcolati una volta (l'algoritmo di Supercluster di
 * Mapbox, ISC: dal livello piu' alto in giu', ogni punto raccoglie i vicini entro [radiusDp] sullo schermo e il grappolo
 * va nel loro baricentro pesato). Poi chiedere i grappoli di un riquadro costa poco: si leggono dalle celle della
 * griglia di quel livello.
 *
 * Si costruisce fuori dal thread principale ([build] e' pura), e si legge da li'.
 */
class FluidMapClusterIndex private constructor(
  val minZoom: Int,
  val maxZoom: Int,
  val radiusDp: Double,
  private val levels: Array<Level>,
  /** Il numero di segnaposto. */
  val size: Int,
) {
  private class Level(val clusters: List<FluidMapCluster>, val cells: Map<Long, IntArray>, val byId: Map<String, FluidMapCluster>, val cellsPerSide: Int)

  /** Tutti i grappoli del livello [level]. */
  fun clusters(level: Int): List<FluidMapCluster> = levelAt(level).clusters

  /** Il grappolo [id] al livello [level], se c'e'. */
  fun cluster(level: Int, id: String): FluidMapCluster? = levelAt(level).byId[id]

  /**
   * I grappoli del livello [level] dentro il cerchio di centro ([x], [y]) e raggio [radius], in coordinate normalizzate
   * (la x anche fuori da 0..1). Nell'ordine del livello, che e' stabile.
   */
  fun around(level: Int, x: Double, y: Double, radius: Double): List<FluidMapCluster> {
    val l = levelAt(level)
    val n = l.cellsPerSide
    val r = radius.coerceAtMost(1.0)
    val x0 = floor((x - r) * n).toInt()
    val x1 = floor((x + r) * n).toInt()
    val y0 = floor((y - r) * n).toInt().coerceAtLeast(0)
    val y1 = floor((y + r) * n).toInt().coerceAtMost(n - 1)
    val hits = ArrayList<Int>()
    val seenColumns = HashSet<Int>()
    for (cxRaw in x0..x1) {
      val cx = ((cxRaw % n) + n) % n
      if (!seenColumns.add(cx)) continue
      for (cy in y0..y1) {
        val cell = l.cells[key(cx, cy)] ?: continue
        for (i in cell) hits.add(i)
      }
    }
    hits.sort()
    return hits.map { l.clusters[it] }
  }

  /**
   * L'antenato di [id] (un grappolo del livello [from]) al livello [to] (piu' basso), cioe' il grappolo che lo contiene
   * quando si allontana; lo stesso grappolo se [to] >= [from].
   */
  fun ancestor(id: String, from: Int, to: Int): FluidMapCluster? {
    var level = from.coerceIn(minZoom, maxZoom)
    var current = levelAt(level).byId[id] ?: return null
    while (level > to.coerceIn(minZoom, maxZoom)) {
      val parent = current.parentId ?: return current
      level -= 1
      current = levelAt(level).byId[parent] ?: return current
    }
    return current
  }

  /**
   * Lo zoom a cui il grappolo si divide: il primo livello sopra il suo in cui il suo primo segnaposto non sta piu' con
   * tutti gli altri. E' dove porta il tocco su un grappolo (come `MKMapView` che mostra i membri). [maxZoom] se non si
   * divide mai (segnaposto nello stesso punto).
   */
  fun expansionZoom(cluster: FluidMapCluster): Int {
    var level = cluster.level
    while (level < maxZoom) {
      level += 1
      val inside = levelAt(level).byId.values.firstOrNull { c -> c.markers.any { it.id == cluster.first.id } } ?: return level
      if (inside.markers.size < cluster.markers.size) return level
    }
    return maxZoom
  }

  private fun levelAt(level: Int): Level = levels[level.coerceIn(minZoom, maxZoom) - minZoom]

  companion object {
    /**
     * Il raggio di raccolta di serie: 48 dp, poco piu' del pallino di un segnaposto (30 dp) col suo margine, perche' due
     * pallini non si coprano mai. A occhio, 26/11/2026 (Supercluster usa 40 px su tessere da 256, cioe' 80 su 512: con
     * i segnaposto di MapKit, piu' piccoli, il grappolo arrivava troppo presto).
     */
    const val DefaultRadiusDp: Double = 48.0

    /** Un indice vuoto. */
    val Empty: FluidMapClusterIndex = build(emptyList())

    /**
     * Costruisce i grappoli di [markers] dal livello [maxZoom] (sopra, ogni segnaposto e' solo) a [minZoom]. Pura,
     * deterministica (lo stesso elenco da' gli stessi grappoli e gli stessi id) e senza Android: da chiamare fuori dal
     * thread principale per elenchi lunghi (diecimila segnaposto: decine di millisecondi).
     */
    fun build(markers: List<FluidMapMarker>, radiusDp: Double = DefaultRadiusDp, minZoom: Int = 0, maxZoom: Int = 18): FluidMapClusterIndex {
      require(minZoom in 0..maxZoom) { "Livelli non validi: $minZoom..$maxZoom" }
      val ids = HashSet<String>()
      for (m in markers) require(ids.add(m.id)) { "Due segnaposto con lo stesso id: ${m.id}" }
      // Le foglie, nell'ordine degli id: lo stesso elenco in un altro ordine da' gli stessi grappoli.
      val leaves: List<Node> = markers.sortedBy { it.id }.map { m ->
        Node(m.id, FluidMapProjection.x(m.point.longitude), FluidMapProjection.y(m.point.latitude), listOf(m), m.count.toDouble())
      }
      val levels = arrayOfNulls<Level>(maxZoom - minZoom + 1)
      // Al livello piu' alto i segnaposto restano tutti soli: l'id del grappolo e' quello del segnaposto.
      var previous: List<Node> = leaves
      for (level in maxZoom downTo minZoom) {
        val clustered = if (level == maxZoom) leaves else clusterOnce(previous, radiusDp / (FluidMapProjection.TileSize * 2.0.pow(level)))
        // I figli del livello sopra trovano qui il loro genitore.
        if (level < maxZoom) {
          val parentOf = HashMap<String, String>()
          for (node in clustered) for (child in node.children) parentOf[child] = node.id
          levels[level + 1 - minZoom] = levels[level + 1 - minZoom]!!.withParents(parentOf, level + 1)
        }
        levels[level - minZoom] = makeLevel(clustered, level)
        previous = clustered
      }
      @Suppress("UNCHECKED_CAST")
      return FluidMapClusterIndex(minZoom, maxZoom, radiusDp, levels as Array<Level>, markers.size)
    }

    private class Node(val id: String, val x: Double, val y: Double, val markers: List<FluidMapMarker>, val weight: Double, val children: List<String> = emptyList())

    /** Un passo di Supercluster: ogni nodo non ancora preso raccoglie i vicini non presi entro [r] (normalizzato). */
    private fun clusterOnce(nodes: List<Node>, r: Double): List<Node> {
      if (nodes.isEmpty()) return nodes
      // La griglia di lato r: i vicini di un punto stanno nelle nove celle attorno.
      val grid = HashMap<Long, MutableList<Int>>()
      val n = (1.0 / r).toLong().coerceIn(1, 1L shl 30)
      fun cellOf(v: Double): Long = floor(v * n).toLong()
      nodes.forEachIndexed { i, node -> grid.getOrPut(pack(cellOf(node.x), cellOf(node.y))) { ArrayList(2) }.add(i) }
      val taken = BooleanArray(nodes.size)
      val out = ArrayList<Node>(nodes.size)
      val r2 = r * r
      for (i in nodes.indices) {
        if (taken[i]) continue
        taken[i] = true
        val p = nodes[i]
        val members = ArrayList<Int>(4)
        members.add(i)
        val gx = cellOf(p.x)
        val gy = cellOf(p.y)
        for (dx in -1L..1L) for (dy in -1L..1L) {
          val bucket = grid[pack(wrapCell(gx + dx, n), gy + dy)] ?: continue
          for (j in bucket) {
            if (taken[j]) continue
            val q = nodes[j]
            var ddx = q.x - p.x
            if (ddx > 0.5) ddx -= 1.0 else if (ddx < -0.5) ddx += 1.0
            val ddy = q.y - p.y
            if (ddx * ddx + ddy * ddy <= r2) {
              taken[j] = true
              members.add(j)
            }
          }
        }
        if (members.size == 1) {
          out.add(Node(p.id, p.x, p.y, p.markers, p.weight, listOf(p.id)))
          continue
        }
        var wx = 0.0
        var wy = 0.0
        var w = 0.0
        val markers = ArrayList<FluidMapMarker>()
        val children = ArrayList<String>(members.size)
        for (k in members) {
          val q = nodes[k]
          var qx = q.x
          if (qx - p.x > 0.5) qx -= 1.0 else if (qx - p.x < -0.5) qx += 1.0
          wx += qx * q.weight
          wy += q.y * q.weight
          w += q.weight
          markers.addAll(q.markers)
          children.add(q.id)
        }
        markers.sortBy { it.id }
        val x = ((wx / w) % 1.0 + 1.0) % 1.0
        out.add(Node(clusterId(markers), x, wy / w, markers, w, children))
      }
      return out
    }

    private fun clusterId(markers: List<FluidMapMarker>): String =
      if (markers.size == 1) markers[0].id else "grappolo:${markers[0].id}:${markers.size}"

    private fun wrapCell(c: Long, n: Long): Long = ((c % n) + n) % n
    private fun pack(x: Long, y: Long): Long = (x shl 32) xor (y and 0xffffffffL)

    private fun makeLevel(nodes: List<Node>, level: Int): Level {
      val clusters = nodes.map { node ->
        FluidMapCluster(node.id, FluidGeoPoint(FluidMapProjection.latitude(node.y), FluidMapProjection.longitude(node.x)),
          node.markers, level, node.x, node.y, parentId = null)
      }
      return buildLevel(clusters, level)
    }

    private fun buildLevel(clusters: List<FluidMapCluster>, level: Int): Level {
      // Le celle della lettura: una tessera del livello (512 dp a quello zoom), almeno una per il mondo intero.
      val perSide = (1 shl level.coerceAtMost(20)).coerceAtLeast(1)
      val cells = HashMap<Long, MutableList<Int>>()
      clusters.forEachIndexed { i, c ->
        val cx = floor(c.x * perSide).toInt().coerceIn(0, perSide - 1)
        val cy = floor(c.y * perSide).toInt().coerceIn(0, perSide - 1)
        cells.getOrPut(key(cx, cy)) { ArrayList(2) }.add(i)
      }
      return Level(clusters, cells.mapValues { it.value.toIntArray() }, clusters.associateBy { it.id }, perSide)
    }

    private fun Level.withParents(parentOf: Map<String, String>, level: Int): Level {
      val updated = clusters.map { c -> FluidMapCluster(c.id, c.point, c.markers, level, c.x, c.y, parentOf[c.id]) }
      return Level(updated, cells, updated.associateBy { it.id }, cellsPerSide)
    }

    private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) xor (y.toLong() and 0xffffffffL)
  }
}

/** La distanza fra due punti normalizzati, col giro del mondo piu' breve. */
internal fun normalizedDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
  var dx = x2 - x1
  if (dx > 0.5) dx -= 1.0 else if (dx < -0.5) dx += 1.0
  val dy = y2 - y1
  return sqrt(dx * dx + dy * dy)
}
