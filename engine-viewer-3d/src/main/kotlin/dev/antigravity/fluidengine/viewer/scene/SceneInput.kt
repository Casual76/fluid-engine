package dev.antigravity.fluidengine.viewer.scene

import dev.antigravity.fluidengine.viewer.FluidViewerException
import dev.antigravity.fluidengine.viewer.FluidViewerFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

internal const val MaxModelBytes = 96 * 1024 * 1024
internal const val MaxConvertedTriangles = 250_000
internal const val GlbMagic = 0x46546c67
internal const val JsonChunk = 0x4e4f534a
internal const val BinChunk = 0x004e4942

internal fun sceneCorrupt(message: String): Nothing = throw FluidViewerException(FluidViewerFailure.Corrupt, message)
internal fun sceneUnsupported(message: String): Nothing = throw FluidViewerException(FluidViewerFailure.Unsupported, message)
internal fun sceneLimit(message: String): Nothing = throw FluidViewerException(FluidViewerFailure.TooLarge, message)

internal suspend fun InputStream.readSceneBytes(limit: Int): ByteArray {
  val buffer = ByteArray(32 * 1024)
  val out = ByteArrayOutputStream()
  while (true) {
    currentCoroutineContext().ensureActive()
    val n = read(buffer)
    if (n < 0) return out.toByteArray()
    if (n > limit - out.size()) sceneLimit("Il modello e le sue risorse superano il limite di lettura")
    out.write(buffer, 0, n)
  }
}

internal fun ByteArray.le(): ByteBuffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
internal fun String.extension(): String = substringAfterLast('.', "").lowercase(Locale.ROOT)

internal fun localResource(path: String, base: String = "", encoded: Boolean = false): String {
  val decoded = if (encoded) {
    val uri = try { URI(path) } catch (_: java.net.URISyntaxException) { sceneUnsupported("Percorso della risorsa non valido") }
    if (uri.isAbsolute || uri.rawAuthority != null || uri.rawQuery != null || uri.rawFragment != null)
      sceneUnsupported("Il modello richiede una risorsa fuori dalla cartella")
    uri.path ?: sceneUnsupported("Percorso della risorsa non valido")
  } else path
  if (decoded.isBlank() || decoded.startsWith('/') || decoded.contains('\\') ||
    decoded.any { it <= '\u001f' || it == ':' } || decoded.split('/').any { it == ".." })
    sceneUnsupported("Il modello richiede una risorsa fuori dalla cartella")
  val relative = decoded.split('/').filter { it.isNotBlank() && it != "." }.joinToString("/")
  if (relative.isEmpty()) sceneUnsupported("Percorso della risorsa non valido")
  return if (base.isEmpty()) relative else "$base/$relative"
}
