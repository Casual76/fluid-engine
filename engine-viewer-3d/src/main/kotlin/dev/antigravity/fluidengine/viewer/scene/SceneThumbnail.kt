package dev.antigravity.fluidengine.viewer.scene

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Layers
import dev.antigravity.fluidengine.viewer.FluidThumbnailLook
import dev.antigravity.fluidengine.viewer.FluidThumbnailPainter
import dev.antigravity.fluidengine.viewer.FluidViewerRequest
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The CPU uses the same glTF positions and base-color UVs on Android and in the local tests. */
internal suspend fun sceneThumbnail(request: FluidViewerRequest, sizePx: Int): ImageBitmap = withContext(Dispatchers.IO) {
  val look = FluidThumbnailLook.current()
  val drawn = try {
    val source = request.source
    val input = source.open().use { it.readSceneBytes(MaxModelBytes) }
    var remaining = MaxModelBytes - input.size
    val sidecars = mutableMapOf<String, ByteArray?>()
    suspend fun local(path: String): ByteArray? {
      if (path !in sidecars) {
        val data = (source as? FluidSceneSource)?.openRelative(path)?.use { it.readSceneBytes(remaining) }
        sidecars[path] = data
        remaining -= data?.size ?: 0
      }
      return sidecars[path]
    }
    suspend fun resource(uri: String): ByteArray? = if (uri.startsWith("data:")) {
      val header = uri.substringBefore(',')
      if (header.endsWith(";base64")) Base64.decode(uri.substringAfter(',', ""), Base64.DEFAULT) else null
    } else local(localResource(uri, encoded = true))

    val binary = input.size >= 4 && input.le().getInt(0) == GlbMagic
    val model = when (if (binary) "glb" else request.type.id) {
      "stl" -> convertStl(input)
      "obj" -> convertObj(input, ::local)
      "glb", "gltf" -> input
      else -> null
    }
    model?.let { SceneMesh.read(it, ::resource) }?.let { mesh ->
      try {
        if (mesh.triangles == 0) null else renderScene(mesh, sizePx, look)
      } finally {
        mesh.textures.values.forEach { it.bitmap.recycle() }
      }
    }
  } catch (e: kotlinx.coroutines.CancellationException) {
    throw e
  } catch (_: Exception) {
    null
  }
  drawn ?: FluidThumbnailPainter.kindSymbol(FluidIcons.Layers, request.source.name.substringAfterLast('.', ""), sizePx, look,
    placeholder = true)
}

/** Quanti triangoli disegna al più la miniatura: oltre, ne prende uno ogni tanti. Basta per la sagoma. */
private const val MaxThumbnailTriangles = 60_000

/** La vista a tre quarti: 35° attorno all'asse verticale, 22° dall'alto. A occhio, 23 novembre 2026. */
private const val YawDegrees = 35.0
private const val PitchDegrees = 22.0

/** I triangoli del modello nel mondo: tre vertici xyz ciascuno, e il colore di base. */
internal class SceneMesh(
  val positions: FloatArray,
  val colors: IntArray,
  val uv: FloatArray,
  val textureIds: IntArray,
  val textures: Map<Int, SceneThumbnailTexture>,
) {
  val triangles: Int get() = colors.size

  companion object {
    suspend fun read(input: ByteArray, resource: suspend (String) -> ByteArray? = { null }): SceneMesh? {
      val bytes = input.le()
      val binary = input.size >= 4 && bytes.getInt(0) == GlbMagic
      val json: JSONObject
      val embedded: ByteBuffer?
      if (binary) {
        if (input.size < 20 || bytes.getInt(4) != 2) return null
        val jsonLength = bytes.getInt(12)
        if (bytes.getInt(16) != JsonChunk || jsonLength !in 1..(input.size - 20)) return null
        val chunk = 20 + jsonLength
        json = JSONObject(String(input, 20, jsonLength, Charsets.UTF_8).trimEnd(' ', '\u0000'))
        embedded = if (chunk <= input.size - 8 && bytes.getInt(chunk + 4) == BinChunk) {
          val length = bytes.getInt(chunk)
          if (length !in 0..(input.size - chunk - 8)) return null
          ByteBuffer.wrap(input, chunk + 8, length).slice().order(ByteOrder.LITTLE_ENDIAN)
        } else null
      } else {
        json = JSONObject(input.decodeToString().removePrefix("\uFEFF"))
        embedded = null
      }
      val buffers = json.optJSONArray("buffers") ?: JSONArray()
      if (buffers.length() > 64) return null
      val dataBuffers = List(buffers.length()) { i ->
        val uri = buffers.getJSONObject(i).optString("uri")
        if (uri.isEmpty()) embedded.takeIf { i == 0 } else resource(uri)?.le()
      }
      val nodes = json.optJSONArray("nodes") ?: JSONArray()
      val meshes = json.optJSONArray("meshes") ?: JSONArray()
      val accessors = json.optJSONArray("accessors") ?: JSONArray()
      val views = json.optJSONArray("bufferViews") ?: JSONArray()
      val materials = json.optJSONArray("materials") ?: JSONArray()
      val scenes = json.optJSONArray("scenes")
      val roots = scenes?.optJSONObject(json.optInt("scene", 0))?.optJSONArray("nodes")
        ?: JSONArray().apply { for (i in 0 until nodes.length()) put(i) }
      val positions = FloatArrayList()
      val colors = IntArrayList()
      val coordinates = FloatArrayList()
      val textureIds = IntArrayList()
      val textures = mutableMapOf<Int, SceneThumbnailTexture>()
      val cancellation = currentCoroutineContext()

      fun viewData(view: JSONObject): ByteBuffer? {
        val buffer = dataBuffers.getOrNull(view.optInt("buffer", 0)) ?: return null
        val start = view.optInt("byteOffset", 0)
        val length = view.optInt("byteLength", -1)
        if (start < 0 || length < 0 || start.toLong() + length > buffer.limit()) return null
        return buffer.duplicate().apply { position(start); limit(start + length) }.slice().order(ByteOrder.LITTLE_ENDIAN)
      }

      val images = json.optJSONArray("images") ?: JSONArray()
      val definitions = json.optJSONArray("textures") ?: JSONArray()
      val samplers = json.optJSONArray("samplers")
      val textureIdsUsed = (0 until materials.length()).asSequence().mapNotNull {
        materials.optJSONObject(it)?.optJSONObject("pbrMetallicRoughness")
          ?.optJSONObject("baseColorTexture")?.optInt("index", -1)
      }.filter { it >= 0 }.distinct().take(16)
      try {
        for (id in textureIdsUsed) {
          cancellation.ensureActive()
          val texture = definitions.optJSONObject(id) ?: continue
          val image = images.optJSONObject(texture.optInt("source", -1)) ?: continue
          val uri = image.optString("uri")
          val encoded = if (uri.isNotEmpty()) resource(uri) else {
            val view = views.optJSONObject(image.optInt("bufferView", -1)) ?: continue
            val data = viewData(view) ?: continue
            if (data.remaining() > MaxThumbnailTextureBytes) continue
            ByteArray(data.remaining()).also { data.get(it) }
          }
          val bitmap = encoded?.let(::decodeThumbnailTexture) ?: continue
          val sampler = samplers?.optJSONObject(texture.optInt("sampler", -1))
          textures[id] = SceneThumbnailTexture(bitmap, wrap(sampler?.optInt("wrapS")), wrap(sampler?.optInt("wrapT")))
        }

        fun accessorData(index: Int): Triple<ByteBuffer, Int, JSONObject>? {
          val accessor = accessors.optJSONObject(index) ?: return null
          val view = views.optJSONObject(accessor.optInt("bufferView", -1)) ?: return null
          val data = viewData(view) ?: return null
          return Triple(data, accessor.optInt("byteOffset", 0), accessor)
        }

        fun visit(index: Int, parent: FloatArray, depth: Int) {
          if (depth > 64 || colors.size >= MaxThumbnailTriangles) return
          cancellation.ensureActive()
          val node = nodes.optJSONObject(index) ?: return
          val world = multiply(parent, localMatrix(node))
          val mesh = meshes.optJSONObject(node.optInt("mesh", -1))
          val primitives = mesh?.optJSONArray("primitives")
          if (primitives != null) for (p in 0 until primitives.length()) {
            if (colors.size >= MaxThumbnailTriangles) break
            val primitive = primitives.getJSONObject(p)
            if (primitive.optInt("mode", 4) != 4) continue
            val (data, offset, accessor) = accessorData(primitive.getJSONObject("attributes").optInt("POSITION", -1)) ?: continue
            if (accessor.optInt("componentType") != 5126 || accessor.optString("type") != "VEC3") continue
            val count = accessor.optInt("count")
            val view = views.getJSONObject(accessor.getInt("bufferView"))
            val stride = view.optInt("byteStride", 12).takeIf { it > 0 } ?: 12
            fun vertex(i: Int, out: FloatArray) {
              val at = offset + i * stride
              val x = data.getFloat(at); val y = data.getFloat(at + 4); val z = data.getFloat(at + 8)
              out[0] = world[0] * x + world[4] * y + world[8] * z + world[12]
              out[1] = world[1] * x + world[5] * y + world[9] * z + world[13]
              out[2] = world[2] * x + world[6] * y + world[10] * z + world[14]
            }
            val material = materials.optJSONObject(primitive.optInt("material", -1))?.optJSONObject("pbrMetallicRoughness")
            val factor = material?.optJSONArray("baseColorFactor")
            val info = material?.optJSONObject("baseColorTexture")
            val textureId = info?.optInt("index", -1) ?: -1
            val texCoord = info?.optInt("texCoord", 0) ?: 0
            val uvIndex = primitive.getJSONObject("attributes").optInt("TEXCOORD_$texCoord", -1)
            val uv = if (textures.containsKey(textureId)) accessorData(uvIndex) else null
            val component = uv?.third?.optInt("componentType")
            val uvWidth = when (component) { 5121 -> 1; 5123 -> 2; 5126 -> 4; else -> 0 }
            val uvStride = uv?.third?.let { views.optJSONObject(it.optInt("bufferView"))?.optInt("byteStride", uvWidth * 2) }
              ?: (uvWidth * 2)
            val hasTexture = uv != null && uvWidth > 0 && uv.third.optString("type") == "VEC2" &&
              uv.third.optInt("count") >= count && (component == 5126 || uv.third.optBoolean("normalized"))
            fun appendUv(index: Int) {
              val (data, offset, _) = requireNotNull(uv)
              val at = offset + index * uvStride
              fun channel(position: Int): Float = when (component) {
                5121 -> (data.get(position).toInt() and 255) / 255f
                5123 -> (data.getShort(position).toInt() and 65535) / 65535f
                else -> data.getFloat(position)
              }
              val image = textures.getValue(textureId).bitmap
              coordinates.add(channel(at) * image.width)
              coordinates.add(channel(at + uvWidth) * image.height)
            }
            val color = if (factor != null && factor.length() >= 3) Color.rgb(
              tint(factor.optDouble(0, 1.0), hasTexture),
              tint(factor.optDouble(1, 1.0), hasTexture),
              tint(factor.optDouble(2, 1.0), hasTexture),
            ) else if (hasTexture) Color.WHITE else 0xFFB8BCC4.toInt()
            val indices = primitive.optInt("indices", -1).takeIf { it >= 0 }?.let { accessorData(it) }
            val triangles = (indices?.third?.optInt("count") ?: count) / 3
            val a = FloatArray(3); val b = FloatArray(3); val c = FloatArray(3)
            fun index(i: Int): Int {
              val (ib, io, ia) = indices ?: return i
              return when (ia.optInt("componentType")) {
                5121 -> ib.get(io + i).toInt() and 0xFF
                5123 -> ib.getShort(io + i * 2).toInt() and 0xFFFF
                else -> ib.getInt(io + i * 4)
              }
            }
            val budget = MaxThumbnailTriangles - colors.size
            val step = ((triangles.toLong() + budget - 1) / budget).toInt().coerceAtLeast(1)
            for (t in 0 until triangles step step) {
              if (colors.size and 1023 == 0) cancellation.ensureActive()
              val i0 = index(t * 3); val i1 = index(t * 3 + 1); val i2 = index(t * 3 + 2)
              if (i0 !in 0 until count || i1 !in 0 until count || i2 !in 0 until count) continue
              vertex(i0, a); vertex(i1, b); vertex(i2, c)
              positions.add(a); positions.add(b); positions.add(c)
              colors.add(color)
              textureIds.add(if (hasTexture) textureId else -1)
              if (hasTexture) {
                appendUv(i0); appendUv(i1); appendUv(i2)
              } else repeat(6) { coordinates.add(0f) }
            }
          }
          val children = node.optJSONArray("children") ?: return
          for (i in 0 until children.length()) visit(children.optInt(i), world, depth + 1)
        }

        for (i in 0 until roots.length()) {
          currentCoroutineContext().ensureActive()
          visit(roots.optInt(i), identity(), 0)
        }
        return SceneMesh(positions.toArray(), colors.toArray(), coordinates.toArray(), textureIds.toArray(), textures)
      } catch (failure: Throwable) {
        textures.values.forEach { it.bitmap.recycle() }
        throw failure
      }
    }

    private fun tint(value: Double, textured: Boolean): Int {
      val linear = value.coerceIn(0.0, 1.0)
      val srgb = if (!textured) linear else if (linear <= 0.0031308) 12.92 * linear else 1.055 * linear.pow(1.0 / 2.4) - 0.055
      return (srgb * 255).roundToInt()
    }

    private fun wrap(mode: Int?): Shader.TileMode = when (mode) {
      33071 -> Shader.TileMode.CLAMP
      33648 -> Shader.TileMode.MIRROR
      else -> Shader.TileMode.REPEAT
    }

    private fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    /** La matrice locale di un nodo glTF: `matrix`, oppure traslazione, rotazione (quaternione) e scala. */
    private fun localMatrix(node: JSONObject): FloatArray {
      node.optJSONArray("matrix")?.takeIf { it.length() == 16 }?.let { m -> return FloatArray(16) { m.optDouble(it).toFloat() } }
      val t = node.optJSONArray("translation")
      val r = node.optJSONArray("rotation")
      val s = node.optJSONArray("scale")
      val tx = t?.optDouble(0)?.toFloat() ?: 0f; val ty = t?.optDouble(1)?.toFloat() ?: 0f; val tz = t?.optDouble(2)?.toFloat() ?: 0f
      val qx = r?.optDouble(0)?.toFloat() ?: 0f; val qy = r?.optDouble(1)?.toFloat() ?: 0f
      val qz = r?.optDouble(2)?.toFloat() ?: 0f; val qw = r?.optDouble(3)?.toFloat() ?: 1f
      val sx = s?.optDouble(0)?.toFloat() ?: 1f; val sy = s?.optDouble(1)?.toFloat() ?: 1f; val sz = s?.optDouble(2)?.toFloat() ?: 1f
      return floatArrayOf(
        (1 - 2 * (qy * qy + qz * qz)) * sx, (2 * (qx * qy + qz * qw)) * sx, (2 * (qx * qz - qy * qw)) * sx, 0f,
        (2 * (qx * qy - qz * qw)) * sy, (1 - 2 * (qx * qx + qz * qz)) * sy, (2 * (qy * qz + qx * qw)) * sy, 0f,
        (2 * (qx * qz + qy * qw)) * sz, (2 * (qy * qz - qx * qw)) * sz, (1 - 2 * (qx * qx + qy * qy)) * sz, 0f,
        tx, ty, tz, 1f,
      )
    }

    /** a × b, colonne prima (come glTF). */
    private fun multiply(a: FloatArray, b: FloatArray): FloatArray = FloatArray(16) { i ->
      val col = i / 4; val row = i % 4
      a[row] * b[col * 4] + a[4 + row] * b[col * 4 + 1] + a[8 + row] * b[col * 4 + 2] + a[12 + row] * b[col * 4 + 3]
    }
  }
}

internal class SceneThumbnailTexture(val bitmap: Bitmap, wrapS: Shader.TileMode, wrapT: Shader.TileMode) {
  val shader = BitmapShader(bitmap, wrapS, wrapT)
}

private const val MaxThumbnailTextureBytes = 8 * 1024 * 1024
private const val MaxThumbnailTextureEdge = 512

private fun decodeThumbnailTexture(bytes: ByteArray): Bitmap? {
  if (bytes.size > MaxThumbnailTextureBytes) return null
  val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
  BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
  val edge = max(bounds.outWidth, bounds.outHeight)
  if (edge <= 0) return null
  var sample = 1
  while (edge / sample > MaxThumbnailTextureEdge) sample *= 2
  return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
}

/**
 * Il modello su un foglio 4:3 che riempie la tessera: fondo sfumato del tema, ombra a terra, triangoli dal più lontano
 * al più vicino. Si disegna al doppio e si riduce, così i bordi non hanno scalini.
 */
private suspend fun renderScene(mesh: SceneMesh, sizePx: Int, look: FluidThumbnailLook): ImageBitmap {
  val size = FluidThumbnailPainter.clampSize(sizePx)
  val w = size
  val h = max(1, (size * 0.75f).roundToInt())
  val scaleUp = 2
  val bitmap = Bitmap.createBitmap(w * scaleUp, h * scaleUp, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bitmap)
  val W = bitmap.width.toFloat()
  val H = bitmap.height.toFloat()
  val (top, bottom) = if (look.dark) 0xFF3A3A3C.toInt() to 0xFF1C1C1E.toInt() else 0xFFFAFAFC.toInt() to 0xFFE3E3E8.toInt()
  canvas.drawRect(0f, 0f, W, H, Paint().apply { shader = LinearGradient(0f, 0f, 0f, H, top, bottom, Shader.TileMode.CLAMP) })

  // La vista: prima attorno all'asse verticale, poi dall'alto.
  val yaw = Math.toRadians(YawDegrees)
  val pitch = Math.toRadians(PitchDegrees)
  val cy = cos(yaw).toFloat(); val sy = sin(yaw).toFloat()
  val cp = cos(pitch).toFloat(); val sp = sin(pitch).toFloat()
  val step = max(1, mesh.triangles / MaxThumbnailTriangles)
  val count = (mesh.triangles + step - 1) / step
  val view = FloatArray(count * 9)
  var minX = Float.POSITIVE_INFINITY; var maxX = Float.NEGATIVE_INFINITY
  var minY = Float.POSITIVE_INFINITY; var maxY = Float.NEGATIVE_INFINITY
  var n = 0
  for (t in 0 until mesh.triangles step step) {
    for (v in 0 until 3) {
      val src = (t * 3 + v) * 3
      val x = mesh.positions[src]; val y = mesh.positions[src + 1]; val z = mesh.positions[src + 2]
      val x1 = cy * x + sy * z
      val z1 = -sy * x + cy * z
      val y2 = cp * y - sp * z1
      val z2 = sp * y + cp * z1
      val dst = (n * 3 + v) * 3
      view[dst] = x1; view[dst + 1] = y2; view[dst + 2] = z2
      minX = min(minX, x1); maxX = max(maxX, x1); minY = min(minY, y2); maxY = max(maxY, y2)
    }
    n++
    if (n % 4096 == 0) currentCoroutineContext().ensureActive()
  }
  val spanX = max(maxX - minX, 1e-6f)
  val spanY = max(maxY - minY, 1e-6f)
  val scale = min(W * 0.66f / spanX, H * 0.62f / spanY)
  val ox = W / 2f - (minX + maxX) / 2f * scale
  val oy = H * 0.46f + (minY + maxY) / 2f * scale
  fun sx(x: Float) = ox + x * scale
  fun sy(y: Float) = oy - y * scale

  // L'ombra a terra, sotto il punto più basso.
  val floor = sy(minY)
  val shadowW = spanX * scale * 0.55f
  canvas.save()
  canvas.scale(1f, 0.22f, W / 2f, floor)
  canvas.drawCircle(W / 2f, floor, shadowW, Paint(Paint.ANTI_ALIAS_FLAG).apply {
    shader = RadialGradient(W / 2f, floor, shadowW, intArrayOf(if (look.dark) 0x80000000.toInt() else 0x40000000, 0),
      floatArrayOf(0.2f, 1f), Shader.TileMode.CLAMP)
  })
  canvas.restore()

  // Dal più lontano al più vicino (z più grande è più vicino a chi guarda).
  val order = (0 until n).sortedBy { t -> view[t * 9 + 2] + view[t * 9 + 5] + view[t * 9 + 8] }
  val vertices = FloatArray(n * 6)
  val shades = IntArray(n * 3)
  val uv = FloatArray(n * 6)
  val textureIds = IntArray(n)
  val lx = -0.42f; val ly = 0.74f; val lz = 0.52f
  for ((k, t) in order.withIndex()) {
    if (k and 1023 == 0) currentCoroutineContext().ensureActive()
    val o = t * 9
    val ax = view[o + 3] - view[o]; val ay = view[o + 4] - view[o + 1]; val az = view[o + 5] - view[o + 2]
    val bx = view[o + 6] - view[o]; val by = view[o + 7] - view[o + 1]; val bz = view[o + 8] - view[o + 2]
    var nx = ay * bz - az * by; var ny = az * bx - ax * bz; var nz = ax * by - ay * bx
    val len = sqrt(nx * nx + ny * ny + nz * nz).takeIf { it > 0f } ?: 1f
    nx /= len; ny /= len; nz /= len
    if (nz < 0f) { nx = -nx; ny = -ny; nz = -nz }
    val light = 0.38f + 0.62f * max(0f, nx * lx + ny * ly + nz * lz) + 0.08f * nz
    val triangle = t * step
    val base = mesh.colors[triangle]
    textureIds[k] = mesh.textureIds[triangle]
    mesh.uv.copyInto(uv, k * 6, triangle * 6, triangle * 6 + 6)
    val shade = Color.rgb(
      (Color.red(base) * light).roundToInt().coerceIn(0, 255),
      (Color.green(base) * light).roundToInt().coerceIn(0, 255),
      (Color.blue(base) * light).roundToInt().coerceIn(0, 255),
    )
    for (v in 0 until 3) {
      vertices[k * 6 + v * 2] = sx(view[o + v * 3])
      vertices[k * 6 + v * 2 + 1] = sy(view[o + v * 3 + 1])
      shades[k * 3 + v] = shade
    }
  }
  val paint = Paint(Paint.FILTER_BITMAP_FLAG)
  var start = 0
  while (start < n) {
    if (start and 1023 == 0) currentCoroutineContext().ensureActive()
    val id = textureIds[start]
    var end = start + 1
    while (end < n && textureIds[end] == id) end++
    paint.shader = mesh.textures[id]?.shader
    // Some Android Canvas implementations use the vertex offset for the UV pointer even without a shader.
    // Keep both arrays aligned and present for every batch, including solid materials.
    val offset = start * 6
    canvas.drawVertices(Canvas.VertexMode.TRIANGLES, (end - start) * 6, vertices, offset,
      uv, offset, shades, start * 3, null, 0, 0, paint)
    start = end
  }
  val small = Bitmap.createScaledBitmap(bitmap, w, h, true)
  bitmap.recycle()
  return FluidThumbnailPainter.picture(small)
}

private class FloatArrayList {
  private var data = FloatArray(4096)
  private var size = 0
  fun add(xyz: FloatArray) {
    if (size + 3 > data.size) data = data.copyOf(data.size * 2)
    data[size++] = xyz[0]; data[size++] = xyz[1]; data[size++] = xyz[2]
  }
  fun add(value: Float) {
    if (size == data.size) data = data.copyOf(data.size * 2)
    data[size++] = value
  }
  fun toArray(): FloatArray = data.copyOf(size)
}

private class IntArrayList {
  private var data = IntArray(1024)
  var size = 0
    private set
  fun add(value: Int) {
    if (size == data.size) data = data.copyOf(data.size * 2)
    data[size++] = value
  }
  fun toArray(): IntArray = data.copyOf(size)
}
