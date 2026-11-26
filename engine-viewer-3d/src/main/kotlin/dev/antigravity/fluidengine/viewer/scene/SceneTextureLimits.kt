package dev.antigravity.fluidengine.viewer.scene

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * R2 · Il lato più lungo di una texture: 8192 px, il `GL_MAX_TEXTURE_SIZE` più piccolo dei telefoni di oggi (Adreno e
 * Mali ne danno 8192 o 16384). Fiducia media, a occhio sul resto: 26 novembre 2026. Una PNG di pochi KB può dichiararne
 * 16384×16384, un GB di pixel alla decodifica.
 */
internal const val MaxTextureSide = 8192

/**
 * R2 · I pixel di tutte le texture di un modello insieme: 8192², 64 Mi di pixel, 256 MiB in RGBA8 una volta decodificati
 * da gltfio, che lo fa in nativo, fuori dai bilanci di memoria dell'engine. Ci stanno quattro texture 4K. A occhio, 26
 * novembre 2026.
 */
internal const val MaxTexturePixels = 8192L * 8192

/**
 * R2 · Misura le immagini del glTF [json] prima di darle a Filament: le intestazioni PNG, JPEG, KTX2 e WebP, incorporate
 * nel GLB [model] ([binary]), in un data URI o in un file a fianco ([resources], per URI). Oltre [MaxTextureSide] per
 * lato o [MaxTexturePixels] in tutto è «troppo grande»; un formato che non è nessuno dei quattro (gli unici del glTF 2.0
 * e delle sue estensioni) non si apre, perché la sua misura non si conosce prima della decodifica. Un'immagine senza
 * byte raggiungibili non si conta: Filament non avrebbe niente da decodificare.
 */
internal fun checkSceneTextures(json: JSONObject, model: ByteArray, binary: Boolean, resources: Map<String, ByteArray>) {
  val images = json.optJSONArray("images") ?: return
  if (images.length() == 0) return
  val buffers = json.optJSONArray("buffers") ?: JSONArray()
  val views = json.optJSONArray("bufferViews") ?: JSONArray()
  val bin = if (binary) glbBinary(model) else null
  val decoded = HashMap<Int, ByteArray?>()

  fun buffer(index: Int): TextureBytes? {
    val entry = buffers.optJSONObject(index) ?: return null
    val uri = entry.optString("uri")
    return when {
      uri.isEmpty() -> bin.takeIf { index == 0 }
      uri.startsWith("data:") -> decoded.getOrPut(index) { dataUri(uri) }?.let { TextureBytes(it, 0, it.size) }
      else -> resources[uri]?.let { TextureBytes(it, 0, it.size) }
    }
  }

  fun view(index: Int): TextureBytes? {
    val view = views.optJSONObject(index) ?: return null
    val buffer = buffer(view.optInt("buffer", -1)) ?: return null
    val start = view.optLong("byteOffset", 0)
    val length = view.optLong("byteLength", -1)
    if (start < 0 || length < 0 || start + length > buffer.length) return null
    return TextureBytes(buffer.array, buffer.offset + start.toInt(), length.toInt())
  }

  var total = 0L
  for (i in 0 until images.length()) {
    val image = images.optJSONObject(i) ?: continue
    val uri = image.optString("uri")
    val bytes = when {
      uri.startsWith("data:") -> dataUri(uri)?.let { TextureBytes(it, 0, it.size) }
      uri.isNotEmpty() -> resources[uri]?.let { TextureBytes(it, 0, it.size) }
      else -> view(image.optInt("bufferView", -1))
    } ?: continue
    val size = textureSize(bytes) ?: sceneUnsupported("Una texture del modello non è PNG, JPEG, KTX2 o WebP")
    if (size.width > MaxTextureSide || size.height > MaxTextureSide || size.depth > MaxTextureSide)
      sceneLimit("Una texture del modello supera $MaxTextureSide pixel per lato")
    val pixels = size.width * size.height * size.depth
    if (pixels > MaxTexturePixels || size.count > MaxTexturePixels) sceneLimit("Le texture del modello superano il limite di memoria")
    total += pixels * size.count
    if (total > MaxTexturePixels) sceneLimit("Le texture del modello superano il limite di memoria")
  }
}

/** Byte di un'immagine dentro un array più grande (il blocco binario di un GLB). */
internal class TextureBytes(val array: ByteArray, val offset: Int, val length: Int) {
  fun u8(at: Int): Int = if (at in 0 until length) array[offset + at].toInt() and 0xFF else -1
  fun be16(at: Int): Int = (u8(at) shl 8) or u8(at + 1)
  fun be32(at: Int): Long = (be16(at).toLong() shl 16) or be16(at + 2).toLong()
  fun le16(at: Int): Int = u8(at) or (u8(at + 1) shl 8)
  fun le24(at: Int): Int = le16(at) or (u8(at + 2) shl 16)
  fun le32(at: Int): Long = le16(at).toLong() or (le16(at + 2).toLong() shl 16)
  fun ascii(at: Int, text: String): Boolean = text.indices.all { u8(at + it) == text[it].code }
}

/** Le misure dichiarate di una texture: [depth] per le texture 3D del KTX2, [count] gli strati per le facce. */
internal class TextureSize(val width: Long, val height: Long, val depth: Long = 1, val count: Long = 1)

/** La misura scritta nell'intestazione di una PNG, JPEG, KTX2 o WebP; `null` se non è nessuna di queste o è rotta. */
internal fun textureSize(bytes: TextureBytes): TextureSize? = when {
  bytes.length >= 24 && bytes.u8(0) == 0x89 && bytes.ascii(1, "PNG\r\n\u001a\n") && bytes.ascii(12, "IHDR") ->
    TextureSize(bytes.be32(16), bytes.be32(20))
  bytes.length >= 4 && bytes.u8(0) == 0xFF && bytes.u8(1) == 0xD8 -> jpegSize(bytes)
  bytes.length >= 44 && bytes.u8(0) == 0xAB && bytes.ascii(1, "KTX 20") && bytes.u8(7) == 0xBB -> {
    val layers = bytes.le32(32).coerceAtLeast(1)
    val faces = bytes.le32(36).coerceAtLeast(1)
    TextureSize(bytes.le32(20), bytes.le32(24).coerceAtLeast(1), bytes.le32(28).coerceAtLeast(1), layers * faces)
  }
  bytes.length >= 30 && bytes.ascii(0, "RIFF") && bytes.ascii(8, "WEBP") -> when {
    bytes.ascii(12, "VP8X") -> TextureSize(bytes.le24(24) + 1L, bytes.le24(27) + 1L)
    bytes.ascii(12, "VP8L") && bytes.u8(20) == 0x2F -> {
      val bits = bytes.le32(21)
      TextureSize((bits and 0x3FFF) + 1, ((bits shr 14) and 0x3FFF) + 1)
    }
    bytes.ascii(12, "VP8 ") -> TextureSize((bytes.le16(26) and 0x3FFF).toLong(), (bytes.le16(28) and 0x3FFF).toLong())
    else -> null
  }
  else -> null
}

/** Una JPEG: i segmenti fino al primo SOF (C0–CF, tranne C4, C8, CC), che porta altezza e larghezza. */
private fun jpegSize(bytes: TextureBytes): TextureSize? {
  var at = 2
  while (at + 4 <= bytes.length) {
    if (bytes.u8(at) != 0xFF) return null
    val marker = bytes.u8(at + 1)
    when {
      marker == 0xFF -> { at++; continue }
      marker == 0x01 || marker in 0xD0..0xD7 -> { at += 2; continue }
      marker == 0xD9 || marker == 0xDA -> return null
    }
    val length = bytes.be16(at + 2)
    if (length < 2) return null
    if (marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
      if (at + 9 > bytes.length) return null
      return TextureSize(bytes.be16(at + 7).toLong(), bytes.be16(at + 5).toLong())
    }
    at += 2 + length
  }
  return null
}

/** Il blocco binario di un GLB (il buffer 0 senza `uri`), se c'è. */
private fun glbBinary(model: ByteArray): TextureBytes? {
  if (model.size < 20) return null
  val buffer = model.le()
  val at = 20 + buffer.getInt(12)
  if (at < 20 || at + 8 > model.size || buffer.getInt(at + 4) != BinChunk) return null
  val length = buffer.getInt(at)
  if (length < 0 || at + 8L + length > model.size) return null
  return TextureBytes(model, at + 8, length)
}

/** I byte di un data URI in base64; `null` per gli altri. */
private fun dataUri(uri: String): ByteArray? {
  val header = uri.substringBefore(',', "")
  if (!header.endsWith(";base64")) return null
  return try {
    Base64.decode(uri.substringAfter(',', ""), Base64.DEFAULT)
  } catch (_: IllegalArgumentException) {
    null
  }
}
