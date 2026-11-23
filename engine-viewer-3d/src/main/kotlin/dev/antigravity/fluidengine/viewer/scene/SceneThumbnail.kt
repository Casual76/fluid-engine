package dev.antigravity.fluidengine.viewer.scene

import android.graphics.Bitmap
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
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * F6C.17 · La miniatura di un modello 3D: **il modello disegnato**, come la vista «Oggetto» di AR Quick Look, su un fondo
 * chiaro (o scuro col tema) con l'ombra a terra.
 *
 * **Il ripiego dichiarato.** Filament fuori schermo vorrebbe un contesto EGL per ogni miniatura, e sulla JVM delle prove
 * non c'è. La miniatura è quindi disegnata dal modulo sulla CPU, dagli stessi triangoli che legge la pagina (GLB, e STL
 * e OBJ convertiti in GLB da [convertStl] e [convertObj]): proiezione a tre quarti, luce di Lambert da sinistra in
 * alto con una luce d'ambiente, il colore di base dei materiali (senza texture), l'ombra a terra sfumata. Un glTF con
 * file esterni, uno USDZ o un modello che non si legge danno il simbolo del 3D con l'estensione.
 */
internal suspend fun sceneThumbnail(request: FluidViewerRequest, sizePx: Int): ImageBitmap = withContext(Dispatchers.IO) {
  val look = FluidThumbnailLook.current()
  val drawn = try {
    val input = request.source.open().use { it.readSceneBytes(MaxModelBytes) }
    val binary = input.size >= 4 && input.le().getInt(0) == GlbMagic
    val glb = when (if (binary) "glb" else request.type.id) {
      "stl" -> convertStl(input)
      "obj" -> convertObj(input) { null }
      "glb" -> input
      else -> null
    }
    glb?.let { SceneMesh.read(it) }?.takeIf { it.triangles > 0 }?.let { renderScene(it, sizePx, look) }
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
internal class SceneMesh(val positions: FloatArray, val colors: IntArray) {
  val triangles: Int get() = colors.size

  companion object {
    /**
     * I triangoli della scena di un GLB: i nodi con le loro trasformazioni, le primitive a triangoli con POSITION in
     * virgola mobile e gli indici (8, 16 o 32 bit), il `baseColorFactor` del materiale. Il resto si ignora.
     */
    suspend fun read(glb: ByteArray): SceneMesh? {
      val buffer = glb.le()
      if (glb.size < 20 || buffer.getInt(0) != GlbMagic) return null
      val jsonLength = buffer.getInt(12)
      if (buffer.getInt(16) != JsonChunk || 20 + jsonLength > glb.size) return null
      val json = JSONObject(String(glb, 20, jsonLength, Charsets.UTF_8).trimEnd(' ', '\u0000'))
      val binStart = 20 + jsonLength + 8
      if (binStart > glb.size || buffer.getInt(20 + jsonLength + 4) != BinChunk) return null
      val binLength = buffer.getInt(20 + jsonLength)
      val bin = ByteBuffer.wrap(glb, binStart, min(binLength, glb.size - binStart)).slice().order(ByteOrder.LITTLE_ENDIAN)
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

      fun accessorData(index: Int): Triple<ByteBuffer, Int, JSONObject>? {
        val accessor = accessors.optJSONObject(index) ?: return null
        val view = views.optJSONObject(accessor.optInt("bufferView", -1)) ?: return null
        if (view.optInt("buffer", 0) != 0) return null
        val offset = view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
        return Triple(bin, offset, accessor)
      }

      fun visit(index: Int, parent: FloatArray, depth: Int) {
        if (depth > 64) return
        val node = nodes.optJSONObject(index) ?: return
        val world = multiply(parent, localMatrix(node))
        val mesh = meshes.optJSONObject(node.optInt("mesh", -1))
        val primitives = mesh?.optJSONArray("primitives")
        if (primitives != null) for (p in 0 until primitives.length()) {
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
          val factor = materials.optJSONObject(primitive.optInt("material", -1))
            ?.optJSONObject("pbrMetallicRoughness")?.optJSONArray("baseColorFactor")
          val color = if (factor != null && factor.length() >= 3) Color.rgb(
            (factor.optDouble(0, 0.8) * 255).roundToInt().coerceIn(0, 255),
            (factor.optDouble(1, 0.8) * 255).roundToInt().coerceIn(0, 255),
            (factor.optDouble(2, 0.8) * 255).roundToInt().coerceIn(0, 255),
          ) else 0xFFB8BCC4.toInt()
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
          for (t in 0 until triangles) {
            val i0 = index(t * 3); val i1 = index(t * 3 + 1); val i2 = index(t * 3 + 2)
            if (i0 !in 0 until count || i1 !in 0 until count || i2 !in 0 until count) continue
            vertex(i0, a); vertex(i1, b); vertex(i2, c)
            positions.add(a); positions.add(b); positions.add(c)
            colors.add(color)
          }
        }
        val children = node.optJSONArray("children") ?: return
        for (i in 0 until children.length()) visit(children.optInt(i), world, depth + 1)
      }

      for (i in 0 until roots.length()) {
        currentCoroutineContext().ensureActive()
        visit(roots.optInt(i), identity(), 0)
      }
      return SceneMesh(positions.toArray(), colors.toArray())
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
  val lx = -0.42f; val ly = 0.74f; val lz = 0.52f
  for ((k, t) in order.withIndex()) {
    val o = t * 9
    val ax = view[o + 3] - view[o]; val ay = view[o + 4] - view[o + 1]; val az = view[o + 5] - view[o + 2]
    val bx = view[o + 6] - view[o]; val by = view[o + 7] - view[o + 1]; val bz = view[o + 8] - view[o + 2]
    var nx = ay * bz - az * by; var ny = az * bx - ax * bz; var nz = ax * by - ay * bx
    val len = sqrt(nx * nx + ny * ny + nz * nz).takeIf { it > 0f } ?: 1f
    nx /= len; ny /= len; nz /= len
    if (nz < 0f) { nx = -nx; ny = -ny; nz = -nz }
    val light = 0.38f + 0.62f * max(0f, nx * lx + ny * ly + nz * lz) + 0.08f * nz
    val base = mesh.colors[(t * step).coerceAtMost(mesh.triangles - 1)]
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
  canvas.drawVertices(Canvas.VertexMode.TRIANGLES, vertices.size, vertices, 0, null, 0, shades, 0, null, 0, 0, Paint())
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
  fun toArray(): FloatArray = data.copyOf(size)
}

private class IntArrayList {
  private var data = IntArray(1024)
  private var size = 0
  fun add(value: Int) {
    if (size == data.size) data = data.copyOf(data.size * 2)
    data[size++] = value
  }
  fun toArray(): IntArray = data.copyOf(size)
}
