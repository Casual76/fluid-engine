package dev.antigravity.fluidengine.viewer.scene

import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.Scene
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.android.filament.utils.Utils
import dev.antigravity.fluidengine.viewer.FluidViewerSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.abs

/** Sorgente di un modello con file a fianco. L'app apre solo percorsi relativi nella cartella concessa. */
interface FluidSceneSource : FluidViewerSource {
  suspend fun openRelative(path: String): InputStream?
}

/** Contatori e misure del glTF preparato, nelle unità originali del modello. */
data class FluidSceneStats(
  val triangles: Long,
  val vertices: Long,
  val materials: Int,
  val textures: Int,
  val animations: Int,
  val width: Double,
  val height: Double,
  val depth: Double,
)

/**
 * Modello riusabile, letto senza Filament. [load] crea un'istanza per l'engine dell'app, senza applicare
 * l'inquadratura o il piano d'ombra del visore. Funziona anche dopo la chiusura della sorgente.
 */
class FluidSceneModel internal constructor(
  private val bytes: ByteArray,
  private val sidecars: Map<String, ByteArray>,
  val stats: FluidSceneStats,
) {
  /** Da chiamare sul thread dell'engine. L'istanza va chiusa sullo stesso thread, prima dell'engine. */
  fun load(engine: Engine): FluidLoadedScene {
    Utils.init()
    val materials = UbershaderProvider(engine)
    var loader: AssetLoader? = null
    var asset: FilamentAsset? = null
    var resources: ResourceLoader? = null
    try {
      val assetLoader = AssetLoader(engine, materials, EntityManager.get())
      loader = assetLoader
      val model = assetLoader.createAsset(ByteBuffer.wrap(bytes)) ?: sceneCorrupt("Filament non legge il modello")
      asset = model
      val resourceLoader = ResourceLoader(engine, true)
      resources = resourceLoader
      for (uri in model.resourceUris) {
        if (uri.startsWith("data:")) continue // gltfio decodes embedded resources itself.
        val data = sidecars[uri] ?: sceneUnsupported("Manca una risorsa del modello")
        resourceLoader.addResourceData(uri, ByteBuffer.wrap(data))
      }
      if (!resourceLoader.asyncBeginLoad(model)) sceneCorrupt("Filament non riesce a caricare le risorse")
      model.releaseSourceData()
      return FluidLoadedScene(model, assetLoader, resourceLoader, materials)
    } catch (t: Throwable) {
      resources?.asyncCancelLoad()
      resources?.destroy()
      asset?.let { loader?.destroyAsset(it) }
      loader?.destroy()
      materials.destroyMaterials()
      materials.destroy()
      throw t
    }
  }

  companion object {
    /** Tetto del visore per glTF e GLB. Si contano anche le istanze ripetute della stessa mesh. */
    const val MaxTriangles = 1_000_000
  }
}

/** Istanza legata a un engine Filament. L'app può usare [asset] e collegarlo a una scena qui o nel proprio renderer. */
class FluidLoadedScene internal constructor(
  val asset: FilamentAsset,
  private val loader: AssetLoader,
  private val resources: ResourceLoader,
  private val materials: UbershaderProvider,
) : Closeable {
  private val attached = LinkedHashSet<Scene>()
  private val ready = ArrayList<Int>()
  private val batch = IntArray(128)
  private var closed = false

  fun attach(scene: Scene) {
    check(!closed)
    if (!attached.add(scene)) return
    scene.addEntities(asset.lightEntities)
    if (ready.isNotEmpty()) scene.addEntities(ready.toIntArray())
  }

  fun detach(scene: Scene) {
    if (attached.remove(scene)) scene.removeEntities(asset.entities)
  }

  /** Da chiamare a ogni fotogramma fino a 1, mentre Filament decodifica le texture. */
  fun updateResources(): Float {
    check(!closed)
    resources.asyncUpdateLoad()
    while (true) {
      val count = asset.popRenderables(batch)
      if (count == 0) break
      val entities = batch.copyOf(count)
      entities.forEach { ready.add(it) }
      attached.forEach { it.addEntities(entities) }
    }
    return resources.asyncGetLoadProgress()
  }

  override fun close() {
    if (closed) return
    closed = true
    attached.toList().forEach(::detach)
    resources.asyncCancelLoad()
    resources.destroy()
    loader.destroyAsset(asset)
    loader.destroy()
    materials.destroyMaterials()
    materials.destroy()
  }
}

/** Prepara GLB, glTF, OBJ o STL. Chi chiama mantiene la proprietà della sorgente e la chiude quando gli serve. */
suspend fun openFluidSceneModel(source: FluidViewerSource): FluidSceneModel = readModel(source, null)

internal suspend fun readModel(source: FluidViewerSource, suggestedFormat: String?): FluidSceneModel = withContext(Dispatchers.IO) {
  var remaining = MaxModelBytes
  val input = source.open().use { it.readSceneBytes(remaining) }
  remaining -= input.size
  val sidecars = HashMap<String, ByteArray?>()
  suspend fun read(path: String): ByteArray? {
    if (sidecars.containsKey(path)) return sidecars[path]
    val data = (source as? FluidSceneSource)?.openRelative(path)?.use { it.readSceneBytes(remaining) }
    sidecars[path] = data
    if (data != null) remaining -= data.size
    return data
  }
  val binary = input.size >= 4 && input.le().getInt(0) == GlbMagic
  val format = if (binary) "glb" else suggestedFormat ?: source.name.extension()
  val bytes = when (format) {
    "stl" -> convertStl(input)
    "obj" -> convertObj(input, ::read)
    "glb", "gltf" -> input
    else -> sceneUnsupported("Formato 3D non supportato")
  }
  val json = readGltf(bytes, format != "gltf")
  val document = GltfInfo(json)
  val stats = document.stats()
  val resources = LinkedHashMap<String, ByteArray>()
  for ((uri, requiredBytes) in document.externalResources()) {
    val path = localResource(uri, encoded = true)
    val data = read(path) ?: sceneUnsupported("Manca il file $path nella cartella del modello")
    if (data.size < requiredBytes) sceneCorrupt("Il file $path è incompleto")
    resources[uri] = data
  }
  FluidSceneModel(document.renderBytes(bytes, format != "gltf"), resources, stats)
}

private fun readGltf(bytes: ByteArray, binary: Boolean): JSONObject {
  val json = if (!binary) {
    if (bytes.size > MaxGltfJson) sceneLimit("Il documento glTF supera il limite di lettura")
    bytes.decodeToString().removePrefix("\uFEFF")
  } else {
    val buffer = bytes.le()
    if (bytes.size < 20 || buffer.int != GlbMagic || buffer.int != 2 || buffer.int != bytes.size)
      sceneCorrupt("Intestazione GLB non valida")
    val size = buffer.int
    val type = buffer.int
    if (type != JsonChunk || size <= 0 || size % 4 != 0 || size > buffer.remaining()) sceneCorrupt("Indice GLB non valido")
    if (size > MaxGltfJson) sceneLimit("L'indice GLB supera il limite di lettura")
    val text = String(bytes, 20, size, Charsets.UTF_8).trimEnd(' ', '\u0000')
    buffer.position(20 + size)
    while (buffer.hasRemaining()) {
      if (buffer.remaining() < 8) sceneCorrupt("Sezione GLB incompleta")
      val length = buffer.int
      buffer.int
      if (length < 0 || length % 4 != 0 || length > buffer.remaining()) sceneCorrupt("Sezione GLB incompleta")
      buffer.position(buffer.position() + length)
    }
    text
  }
  return try {
    JSONObject(json)
  } catch (e: org.json.JSONException) {
    sceneCorrupt("Il documento glTF non contiene un oggetto JSON valido")
  }
}

private const val MaxGltfJson = 24 * 1024 * 1024

private class GltfInfo(private val json: JSONObject) {
  private val nodes = json.array("nodes")
  private val meshes = json.array("meshes")
  private val accessors = json.array("accessors")
  private val scenes = json.array("scenes")
  private val materials = json.array("materials")
  private val textures = json.array("textures")
  private val animations = json.array("animations")
  private val boundsMin = DoubleArray(3) { Double.POSITIVE_INFINITY }
  private val boundsMax = DoubleArray(3) { Double.NEGATIVE_INFINITY }
  private var triangles = 0L
  private var vertices = 0L
  private var primitiveInstances = 0
  private val seen = HashSet<Int>()
  private val roots = ArrayList<Int>()

  init {
    if (json.optJSONObject("asset")?.optString("version") != "2.0")
      sceneUnsupported("Serve un modello glTF 2.0")
    if (listOf(nodes, meshes, accessors).any { it.length() > 100_000 } ||
      listOf(materials, textures, animations).any { it.length() > 4096 }) sceneLimit("Il modello contiene troppi elementi")
    val required = json.array("extensionsRequired")
    for (i in 0 until required.length()) {
      val name = required.getString(i)
      if (name !in supportedExtensions) sceneUnsupported("Estensione glTF richiesta non supportata: $name")
    }
  }

  fun externalResources(): Map<String, Int> = buildMap {
    fun collect(value: JSONArray, buffer: Boolean) {
      for (i in 0 until value.length()) {
        val entry = value.getJSONObject(i)
        val uri = entry.optString("uri").takeIf { it.isNotEmpty() } ?: continue
        val size = if (buffer) entry.optLong("byteLength", -1) else 0L
        if (size !in 0..MaxModelBytes.toLong()) sceneLimit("Una risorsa del modello supera il limite di lettura")
        if (uri.startsWith("data:")) continue
        if (this.size >= 4096 && !containsKey(uri)) sceneLimit("Il modello ha troppe risorse esterne")
        put(uri, maxOf(get(uri) ?: 0, size.toInt()))
      }
    }
    collect(json.array("buffers"), true)
    collect(json.array("images"), false)
  }

  fun stats(): FluidSceneStats {
    if (scenes.length() > 0) {
      val index = json.optInt("scene", 0)
      val contents = scenes.optJSONObject(index)?.array("nodes") ?: sceneCorrupt("La scena glTF non esiste")
      for (i in 0 until contents.length()) roots += contents.getInt(i)
    } else {
      val children = HashSet<Int>()
      for (i in 0 until nodes.length()) {
        val refs = nodes.getJSONObject(i).array("children")
        for (j in 0 until refs.length()) children += refs.getInt(j)
      }
      for (i in 0 until nodes.length()) if (i !in children) roots += i
    }
    roots.forEach { node(it, identity(), 0) }
    if (vertices == 0L) sceneCorrupt("La scena non contiene geometria")
    val extents = DoubleArray(3) { i ->
      if (boundsMax[i].isFinite() && boundsMin[i].isFinite()) boundsMax[i] - boundsMin[i] else 0.0
    }
    return FluidSceneStats(triangles, vertices, materials.length(), textures.length(), animations.length(),
      extents[0], extents[1], extents[2])
  }

  /** Android gltfio exposes all ready renderables as one set. Give it only the declared default scene,
   * preserving the original node indices for skins and animations. */
  fun renderBytes(original: ByteArray, binary: Boolean): ByteArray {
    var changed = scenes.length() != 1
    for (i in 0 until nodes.length()) {
      if (i in seen) continue
      val node = nodes.getJSONObject(i)
      if (node.remove("mesh") != null) {
        node.remove("skin")
        changed = true
      }
      if (node.optJSONObject("extensions")?.remove("KHR_lights_punctual") != null) changed = true
    }
    if (!changed) return original
    json.put("scene", 0).put("scenes", JSONArray().put(JSONObject().put("nodes", JSONArray(roots))))
    val text = json.toString().toByteArray()
    if (text.size > MaxGltfJson) sceneLimit("L'indice glTF supera il limite di lettura")
    if (!binary) return text
    val tail = 20 + original.le().getInt(12)
    val padding = (-text.size) and 3
    val length = 20 + text.size + padding + original.size - tail
    if (length > MaxModelBytes) sceneLimit("Il modello GLB supera il limite di lettura")
    return ByteBuffer.allocate(length).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
      putInt(GlbMagic).putInt(2).putInt(length).putInt(text.size + padding).putInt(JsonChunk).put(text)
      repeat(padding) { put(32.toByte()) }
      put(original, tail, original.size - tail)
    }.array()
  }

  private fun node(index: Int, parent: DoubleArray, depth: Int) {
    if (depth >= 128) sceneLimit("La scena contiene troppi livelli")
    if (!seen.add(index)) sceneCorrupt("Indice dei nodi glTF ripetuto o circolare")
    val node = nodes.optJSONObject(index) ?: sceneCorrupt("Nodo glTF fuori indice")
    val matrix = multiply(parent, transform(node))
    val meshIndex = node.optInt("mesh", -1)
    if (meshIndex >= 0) {
      val mesh = meshes.optJSONObject(meshIndex) ?: sceneCorrupt("Mesh glTF fuori indice")
      val primitives = mesh.array("primitives")
      for (i in 0 until primitives.length()) primitive(primitives.getJSONObject(i), matrix)
    }
    val children = node.array("children")
    for (i in 0 until children.length()) node(children.getInt(i), matrix, depth + 1)
  }

  private fun primitive(primitive: JSONObject, matrix: DoubleArray) {
    if (++primitiveInstances > 16_384) sceneLimit("La scena contiene troppe parti")
    val positionIndex = primitive.optJSONObject("attributes")?.optInt("POSITION", -1) ?: -1
    val position = accessors.optJSONObject(positionIndex) ?: sceneCorrupt("Geometria glTF senza posizioni")
    val count = position.optLong("count", -1)
    val indices = primitive.optInt("indices", -1)
    val elements = if (indices < 0) count else accessors.optJSONObject(indices)?.optLong("count", -1) ?: -1
    if (count !in 1..3_000_000L || elements !in 1..3_000_000L) sceneLimit("La geometria supera il limite di vertici o indici")
    vertices += count
    triangles += when (primitive.optInt("mode", 4)) {
      4 -> elements / 3
      5, 6 -> (elements - 2).coerceAtLeast(0)
      in 0..3 -> 0
      else -> sceneUnsupported("Primitiva glTF non supportata")
    }
    if (triangles > FluidSceneModel.MaxTriangles || vertices > 3_000_000)
      sceneLimit("La scena supera un milione di triangoli o tre milioni di vertici")
    val min = position.optJSONArray("min") ?: return
    val max = position.optJSONArray("max") ?: return
    for (p in 0..7) {
      val coords = DoubleArray(3) { axis -> (if (p and (1 shl axis) == 0) min else max).getDouble(axis) }
      for (axis in 0..2) {
        val value = matrix[axis] * coords[0] + matrix[4 + axis] * coords[1] +
          matrix[8 + axis] * coords[2] + matrix[12 + axis]
        if (!value.isFinite() || abs(value) > 1e15) sceneCorrupt("Le misure del modello non sono valide")
        boundsMin[axis] = minOf(boundsMin[axis], value)
        boundsMax[axis] = maxOf(boundsMax[axis], value)
      }
    }
  }

  private fun transform(node: JSONObject): DoubleArray {
    node.optJSONArray("matrix")?.let { matrix -> return DoubleArray(16, matrix::getDouble) }
    fun values(name: String, initial: DoubleArray) = node.optJSONArray(name)?.let {
      DoubleArray(initial.size, it::getDouble)
    } ?: initial
    val t = values("translation", doubleArrayOf(0.0, 0.0, 0.0))
    val s = values("scale", doubleArrayOf(1.0, 1.0, 1.0))
    val (x, y, z, w) = values("rotation", doubleArrayOf(0.0, 0.0, 0.0, 1.0))
    return doubleArrayOf(
      (1 - 2 * (y*y + z*z)) * s[0], (2 * (x*y + z*w)) * s[0], (2 * (x*z - y*w)) * s[0], 0.0,
      (2 * (x*y - z*w)) * s[1], (1 - 2 * (x*x + z*z)) * s[1], (2 * (y*z + x*w)) * s[1], 0.0,
      (2 * (x*z + y*w)) * s[2], (2 * (y*z - x*w)) * s[2], (1 - 2 * (x*x + y*y)) * s[2], 0.0,
      t[0], t[1], t[2], 1.0,
    )
  }

  private fun identity() = DoubleArray(16) { if (it % 5 == 0) 1.0 else 0.0 }

  private fun multiply(a: DoubleArray, b: DoubleArray) = DoubleArray(16) { i ->
    val row = i % 4
    val column = i / 4 * 4
    a[row] * b[column] + a[row + 4] * b[column + 1] + a[row + 8] * b[column + 2] + a[row + 12] * b[column + 3]
  }

  private companion object {
    val supportedExtensions = setOf(
      "KHR_materials_unlit", "KHR_materials_clearcoat", "KHR_materials_transmission", "KHR_materials_volume",
      "KHR_materials_ior", "KHR_materials_specular", "KHR_materials_sheen", "KHR_materials_emissive_strength",
      "KHR_texture_transform", "KHR_mesh_quantization", "KHR_lights_punctual", "KHR_texture_basisu",
    )
  }
}

private fun JSONObject.array(name: String): JSONArray = optJSONArray(name) ?: JSONArray()
