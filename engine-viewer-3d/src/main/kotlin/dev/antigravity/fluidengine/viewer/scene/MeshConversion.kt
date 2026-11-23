package dev.antigravity.fluidengine.viewer.scene

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

private data class Vector3(val x: Float, val y: Float, val z: Float) {
  operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
  fun cross(other: Vector3) = Vector3(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x)
  fun unit(): Vector3 {
    val length = sqrt(x * x + y * y + z * z)
    return if (length.isFinite() && length > 0f) Vector3(x / length, y / length, z / length) else Vector3(0f, 1f, 0f)
  }
}

private data class TexCoord(val u: Float, val v: Float)
private data class Corner(val position: Vector3, val normal: Vector3? = null, val uv: TexCoord? = null)
private data class ObjMaterial(val rgba: List<Float>, val texture: String?, val roughness: Float)

private class Surface {
  private var data = FloatArray(24 * 16)
  val boundsMin = FloatArray(3) { Float.POSITIVE_INFINITY }
  val boundsMax = FloatArray(3) { Float.NEGATIVE_INFINITY }
  var vertices = 0
    private set

  fun triangle(a: Corner, b: Corner, c: Corner) {
    if (vertices * 8 + 24 > data.size) data = data.copyOf(data.size + data.size / 2)
    val faceNormal = (b.position - a.position).cross(c.position - a.position).unit()
    for (corner in listOf(a, b, c)) {
      val p = corner.position
      val n = corner.normal?.unit() ?: faceNormal
      val offset = vertices++ * 8
      data[offset] = p.x
      data[offset + 1] = p.y
      data[offset + 2] = p.z
      data[offset + 3] = n.x
      data[offset + 4] = n.y
      data[offset + 5] = n.z
      data[offset + 6] = corner.uv?.u ?: 0f
      data[offset + 7] = corner.uv?.v ?: 0f
      for (axis in 0..2) {
        boundsMin[axis] = minOf(boundsMin[axis], data[offset + axis])
        boundsMax[axis] = maxOf(boundsMax[axis], data[offset + axis])
      }
    }
  }

  fun putIn(output: ByteBuffer) {
    for (i in 0 until vertices * 8) output.putFloat(data[i])
  }
}

private class MeshBuilder {
  val groups = LinkedHashMap<String, Surface>()
  val materials = HashMap<String, ObjMaterial>()
  var triangles = 0
    private set

  fun add(key: String, a: Corner, b: Corner, c: Corner) {
    if (triangles >= MaxConvertedTriangles) sceneLimit("STL e OBJ supportano fino a $MaxConvertedTriangles triangoli")
    triangles++
    val surface = groups[key] ?: run {
      if (groups.size >= 512) sceneLimit("Il modello usa troppi materiali")
      Surface().also { groups[key] = it }
    }
    surface.triangle(a, b, c)
  }
}

internal suspend fun convertStl(input: ByteArray): ByteArray {
  val mesh = MeshBuilder()
  val buffer = input.le()
  val count = if (input.size >= 84) buffer.getInt(80).toLong() and 0xffffffffL else 0
  if (input.size.toLong() == 84L + count * 50L) {
    if (count > MaxConvertedTriangles) sceneLimit("STL supporta fino a $MaxConvertedTriangles triangoli")
    buffer.position(84)
    repeat(count.toInt()) { index ->
      if (index and 1023 == 0) currentCoroutineContext().ensureActive()
      fun vector() = Vector3(finite(buffer.float), finite(buffer.float), finite(buffer.float))
      val normal = vector().takeUnless { it == Vector3(0f, 0f, 0f) }
      mesh.add("", Corner(vector(), normal), Corner(vector(), normal), Corner(vector(), normal))
      buffer.short
    }
  } else {
    val corners = ArrayList<Corner>(3)
    var normal: Vector3? = null
    input.inputStream().bufferedReader().use { reader ->
      while (true) {
        currentCoroutineContext().ensureActive()
        val line = reader.readLine() ?: break
        val (key, value) = parts(line)
        when (key) {
          "facet" -> {
            if (corners.isNotEmpty()) sceneCorrupt("Faccia STL incompleta")
            normal = vector(tokens(value.removePrefix("normal"))).takeUnless { it == Vector3(0f, 0f, 0f) }
          }
          "vertex" -> {
            if (corners.size >= 3) sceneCorrupt("Troppi vertici in una faccia STL")
            corners += Corner(vector(tokens(value)), normal)
          }
          "endfacet" -> {
            if (corners.size != 3) sceneCorrupt("Faccia STL incompleta")
            mesh.add("", corners[0], corners[1], corners[2])
            corners.clear()
          }
        }
      }
    }
    if (corners.isNotEmpty()) sceneCorrupt("Faccia STL incompleta")
  }
  return makeGlb(mesh, emptyMap())
}

internal suspend fun convertObj(input: ByteArray, read: suspend (String) -> ByteArray?): ByteArray {
  val mesh = MeshBuilder()
  val positions = ArrayList<Vector3>()
  val normals = ArrayList<Vector3>()
  val uv = ArrayList<TexCoord>()
  val libraries = HashSet<String>()
  var group = ""
  input.inputStream().bufferedReader().use { reader ->
    while (true) {
      currentCoroutineContext().ensureActive()
      val line = reader.readLine() ?: break
      val (key, value) = parts(line)
      if (positions.size >= 1_000_000 || normals.size >= 1_000_000 || uv.size >= 1_000_000)
        sceneLimit("L'indice OBJ supera un milione di voci per attributo")
      when (key) {
        "v" -> positions += vector(tokens(value))
        "vn" -> normals += vector(tokens(value))
        "vt" -> {
          val values = tokens(value)
          uv += TexCoord(number(values.getOrNull(0)), 1f - (values.getOrNull(1)?.let(::number) ?: 0f))
        }
        "usemtl" -> group = value
        "mtllib" -> {
          val full = localResource(value)
          if (full !in libraries) {
            val whole = read(full)
            if (whole != null) {
              libraries += full
              mesh.materials.putAll(loadMtl(whole, full))
            } else for (name in tokens(value)) {
              val path = localResource(name)
              if (libraries.add(path)) {
                val data = read(path) ?: sceneUnsupported("Manca il materiale OBJ $path")
                mesh.materials.putAll(loadMtl(data, path))
              }
            }
          }
        }
        "f" -> {
          val refs = tokens(value)
          if (refs.size !in 3..4096) sceneUnsupported("La faccia OBJ ha un numero di vertici non supportato")
          val corners = refs.map { token ->
            val fields = token.split('/', limit = 3)
            Corner(positions[objIndex(fields.first(), positions.size)],
              fields.getOrNull(2)?.takeIf(String::isNotEmpty)?.let { normals[objIndex(it, normals.size)] },
              fields.getOrNull(1)?.takeIf(String::isNotEmpty)?.let { uv[objIndex(it, uv.size)] })
          }
          for ((a, b, c) in triangulate(corners)) mesh.add(group, a, b, c)
        }
      }
    }
  }
  val textures = LinkedHashMap<String, ByteArray>()
  for (key in mesh.groups.keys) {
    val texture = mesh.materials[key]?.texture ?: continue
    if (texture !in textures) {
      val bytes = read(texture) ?: sceneUnsupported("Manca la texture OBJ $texture")
      imageMime(bytes)
      textures[texture] = bytes
    }
  }
  return makeGlb(mesh, textures)
}

private fun objIndex(source: String, size: Int): Int {
  val index = source.toIntOrNull() ?: sceneCorrupt("Indice OBJ non valido")
  val resolved = if (index < 0) size + index else index - 1
  if (resolved !in 0 until size) sceneCorrupt("Indice OBJ fuori dalla tabella")
  return resolved
}

/** Ear clipping in the dominant plane, preserving winding and per-corner attributes. */
private fun triangulate(vertices: List<Corner>): List<Triple<Corner, Corner, Corner>> {
  if (vertices.size == 3) return listOf(Triple(vertices[0], vertices[1], vertices[2]))
  val origin = vertices[0].position
  var x = 0.0
  var y = 0.0
  var z = 0.0
  for (i in 1 until vertices.lastIndex) {
    val normal = (vertices[i].position - origin).cross(vertices[i + 1].position - origin)
    x += normal.x
    y += normal.y
    z += normal.z
  }
  val axis = listOf(kotlin.math.abs(x), kotlin.math.abs(y), kotlin.math.abs(z)).withIndex().maxBy { it.value }.index
  val sign = if (listOf(x, y, z)[axis] < 0) -1 else 1
  val plane = vertices.map { (p) ->
    when (axis) {
      0 -> p.y.toDouble() to p.z.toDouble()
      1 -> p.z.toDouble() to p.x.toDouble()
      else -> p.x.toDouble() to p.y.toDouble()
    }
  }
  fun side(a: Int, b: Int, c: Int): Double {
    val (ax, ay) = plane[a]
    val (bx, by) = plane[b]
    val (cx, cy) = plane[c]
    return sign * ((bx - ax) * (cy - ay) - (by - ay) * (cx - ax))
  }
  val remaining = vertices.indices.toMutableList()
  return buildList {
    while (remaining.size > 3) {
      val i = remaining.indices.firstOrNull { i ->
        val a = remaining[(i + remaining.lastIndex) % remaining.size]
        val b = remaining[i]
        val c = remaining[(i + 1) % remaining.size]
        side(a, b, c) > 0 && remaining.none { it != a && it != b && it != c &&
          side(a, b, it) >= 0 && side(b, c, it) >= 0 && side(c, a, it) >= 0 }
      } ?: sceneUnsupported("La faccia OBJ è degenere o si interseca")
      add(Triple(vertices[remaining[(i + remaining.lastIndex) % remaining.size]], vertices[remaining[i]],
        vertices[remaining[(i + 1) % remaining.size]]))
      remaining.removeAt(i)
    }
    add(Triple(vertices[remaining[0]], vertices[remaining[1]], vertices[remaining[2]]))
  }
}

private fun loadMtl(bytes: ByteArray, path: String): Map<String, ObjMaterial> {
  var name: String? = null
  var color = listOf(0.7f, 0.73f, 0.76f)
  var alpha = 1f
  var texture: String? = null
  var roughness = 0.55f
  return buildMap {
    bytes.inputStream().bufferedReader().useLines { lines ->
      for (line in lines) {
        val (key, value) = parts(line)
        when (key) {
          "newmtl" -> {
            name?.let { put(it, ObjMaterial(color + alpha, texture, roughness)) }
            if (size >= 512) sceneLimit("La libreria OBJ usa troppi materiali")
            name = value
            color = listOf(0.7f, 0.73f, 0.76f)
            alpha = 1f
            texture = null
            roughness = 0.55f
          }
          "Kd" -> color = tokens(value).take(3).map { number(it).coerceIn(0f, 1f) }.also {
            if (it.size != 3) sceneCorrupt("Colore MTL incompleto")
          }
          "d" -> alpha = number(value.removePrefix("-halo ").trim()).coerceIn(0f, 1f)
          "Tr" -> alpha = 1f - number(value).coerceIn(0f, 1f)
          "Ns" -> roughness = sqrt(2f / (number(value).coerceAtLeast(0f) + 2f))
          "map_Kd" -> texture = localResource(texturePath(value), path.substringBeforeLast('/', ""))
        }
      }
    }
    name?.let { put(it, ObjMaterial(color + alpha, texture, roughness)) }
  }
}

private fun texturePath(value: String): String {
  val parts = tokens(value)
  var start = 0
  while (start < parts.size && parts[start].startsWith('-')) {
    val option = parts[start++]
    when (option) {
      "-o", "-s", "-t" -> {
        var count = 0
        while (count < 3 && parts.getOrNull(start)?.toFloatOrNull() != null) { start++; count++ }
      }
      "-mm" -> start += 2
      "-blendu", "-blendv", "-cc", "-clamp", "-imfchan", "-texres", "-bm", "-type", "-boost" -> start++
      else -> sceneUnsupported("Opzione della texture MTL non supportata")
    }
  }
  return parts.drop(start).joinToString(" ")
}

private fun imageMime(data: ByteArray): String = when {
  data.size >= 8 && data.copyOfRange(0, 8).contentEquals(byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)) -> "image/png"
  data.size >= 3 && data[0].toInt() == -1 && data[1].toInt() == -40 && data[2].toInt() == -1 -> "image/jpeg"
  else -> sceneUnsupported("La texture MTL deve essere PNG o JPEG")
}

private val whitespace = Regex("\\s+")
private fun tokens(text: String) = text.trim().split(whitespace).filter(String::isNotEmpty)
private fun parts(line: String): Pair<String, String> {
  val clean = line.substringBefore('#').trim()
  val split = clean.indexOfFirst(Char::isWhitespace)
  return if (split < 0) clean to "" else clean.substring(0, split) to clean.substring(split).trim()
}
private fun finite(value: Float): Float = value.takeIf { it.isFinite() && kotlin.math.abs(it) <= 1e15f }
  ?: sceneCorrupt("Il modello contiene coordinate non valide")
private fun number(value: String?) = finite(value?.toFloatOrNull() ?: sceneCorrupt("Coordinata incompleta"))
private fun vector(values: List<String>) = Vector3(number(values.getOrNull(0)), number(values.getOrNull(1)), number(values.getOrNull(2)))

private fun align4(n: Int) = (n + 3) and -4
private fun jsonNumbers(numbers: List<Number>) = JSONArray().apply { numbers.forEach { put(it) } }

private fun makeGlb(mesh: MeshBuilder, images: Map<String, ByteArray>): ByteArray {
  if (mesh.triangles == 0) sceneCorrupt("Il modello non contiene triangoli")
  val views = JSONArray()
  val accessors = JSONArray()
  val primitives = JSONArray()
  val materials = JSONArray()
  var cursor = 0
  fun view(length: Int, stride: Int? = null, target: Int? = null): Int {
    val index = views.length()
    views.put(JSONObject().put("buffer", 0).put("byteOffset", cursor).put("byteLength", length).apply {
      stride?.let { put("byteStride", it) }
      target?.let { put("target", it) }
    })
    cursor += align4(length)
    return index
  }
  fun accessor(view: Int, offset: Int, kind: String, count: Int, type: Int = 5126, bounds: Surface? = null): Int {
    val index = accessors.length()
    accessors.put(JSONObject().put("bufferView", view).put("byteOffset", offset).put("componentType", type)
      .put("count", count).put("type", kind).apply {
        bounds?.let { put("min", jsonNumbers(it.boundsMin.toList())); put("max", jsonNumbers(it.boundsMax.toList())) }
      })
    return index
  }
  val textureNames = images.keys.toList()
  for ((name, surface) in mesh.groups) {
    val vertices = view(surface.vertices * 32, 32, 34962)
    val indexSize = if (surface.vertices <= 65536) 2 else 4
    val indices = view(surface.vertices * indexSize, target = 34963)
    val position = accessor(vertices, 0, "VEC3", surface.vertices, bounds = surface)
    val normal = accessor(vertices, 12, "VEC3", surface.vertices)
    val uv = accessor(vertices, 24, "VEC2", surface.vertices)
    val index = accessor(indices, 0, "SCALAR", surface.vertices, if (indexSize == 2) 5123 else 5125)
    val materialIndex = materials.length()
    val material = mesh.materials[name] ?: ObjMaterial(listOf(0.7f, 0.73f, 0.76f, 1f), null, 0.55f)
    val pbr = JSONObject().put("metallicFactor", 0.0).put("roughnessFactor", material.roughness.toDouble())
      .put("baseColorFactor", jsonNumbers(material.rgba))
    material.texture?.let { pbr.put("baseColorTexture", JSONObject().put("index", textureNames.indexOf(it))) }
    materials.put(JSONObject().put("name", name).put("pbrMetallicRoughness", pbr)
      .put("alphaMode", if (material.rgba.last() < 1f) "BLEND" else "OPAQUE").put("doubleSided", true))
    primitives.put(JSONObject().put("indices", index).put("material", materialIndex)
      .put("attributes", JSONObject().put("POSITION", position).put("NORMAL", normal).put("TEXCOORD_0", uv)))
  }
  val textures = JSONArray()
  val imageViews = JSONArray()
  for (data in images.values) {
    textures.put(JSONObject().put("source", imageViews.length()))
    imageViews.put(JSONObject().put("bufferView", view(data.size)).put("mimeType", imageMime(data)))
  }
  val json = JSONObject().put("asset", JSONObject().put("version", "2.0"))
    .put("scene", 0).put("scenes", JSONArray().put(JSONObject().put("nodes", JSONArray().put(0))))
    .put("nodes", JSONArray().put(JSONObject().put("mesh", 0))).put("meshes", JSONArray().put(JSONObject().put("primitives", primitives)))
    .put("materials", materials).put("bufferViews", views).put("accessors", accessors)
    .put("buffers", JSONArray().put(JSONObject().put("byteLength", cursor)))
  if (images.isNotEmpty()) json.put("images", imageViews).put("textures", textures)
  val utf8 = json.toString().toByteArray()
  val jsonSize = align4(utf8.size)
  val total = 28 + jsonSize + cursor
  if (total > MaxModelBytes) sceneLimit("Il modello convertito è troppo grande")
  val out = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN)
  out.putInt(GlbMagic).putInt(2).putInt(total).putInt(jsonSize).putInt(JsonChunk).put(utf8)
  repeat(jsonSize - utf8.size) { out.put(32.toByte()) }
  out.putInt(cursor).putInt(BinChunk)
  for (surface in mesh.groups.values) {
    surface.putIn(out)
    if (surface.vertices <= 65536) {
      repeat(surface.vertices) { out.putShort(it.toShort()) }
      if (surface.vertices % 2 != 0) out.putShort(0.toShort())
    } else repeat(surface.vertices) { out.putInt(it) }
  }
  for (data in images.values) {
    out.put(data)
    repeat(align4(data.size) - data.size) { out.put(0.toByte()) }
  }
  return out.array()
}
