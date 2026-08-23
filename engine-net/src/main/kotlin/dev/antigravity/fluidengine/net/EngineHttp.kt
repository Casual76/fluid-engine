package dev.antigravity.fluidengine.net

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The two HTTP calls the engine itself makes: read a small document, and download a large file.
 *
 * Built on `HttpURLConnection` rather than a client library on purpose. These calls run before the
 * app's own network stack is necessarily up — the update check happens on a cold start, and the
 * config fetch has to work in an app that talks to nothing else — and an engine that forces a
 * particular HTTP client on its host is an engine that loses an argument with the host's own.
 */
class EngineHttp(
  private val userAgent: String = DefaultUserAgent,
  private val connectTimeoutMillis: Int = 15_000,
  private val readTimeoutMillis: Int = 30_000,
) {

  suspend fun readText(url: String, headers: Map<String, String> = emptyMap()): String =
    withContext(Dispatchers.IO) {
      val connection = open(url, headers + ("Accept" to "application/json"))
      try {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
          error("Richiesta non riuscita ($code).")
        }
        body
      } finally {
        connection.disconnect()
      }
    }

  /**
   * Downloads to `<target>.part` and renames on success.
   *
   * A half-written file under the final name is indistinguishable from a complete one, and the next
   * run would happily try to install it.
   */
  suspend fun download(
    url: String,
    target: File,
    expectedBytes: Long = 0L,
    headers: Map<String, String> = emptyMap(),
    onProgress: (progress: Float, downloaded: Long, total: Long) -> Unit = { _, _, _ -> },
  ): File = withContext(Dispatchers.IO) {
    target.parentFile?.mkdirs()
    val temporary = File(target.parentFile, "${target.name}.part")
    temporary.delete()

    var connection: HttpURLConnection? = null
    try {
      connection = openFollowingRedirects(url, headers + ("Accept" to "application/octet-stream"))
      val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedBytes
      connection.inputStream.use { input ->
        temporary.outputStream().use { output ->
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          var downloaded = 0L
          while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            output.write(buffer, 0, read)
            downloaded += read
            val progress = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
            onProgress(progress.coerceIn(0f, 1f), downloaded, total)
          }
        }
      }
      if (target.exists()) target.delete()
      if (!temporary.renameTo(target)) {
        error("Impossibile finalizzare il file scaricato.")
      }
      target
    } finally {
      connection?.disconnect()
      if (temporary.exists()) temporary.delete()
    }
  }

  private fun open(url: String, headers: Map<String, String>): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
      connectTimeout = connectTimeoutMillis
      readTimeout = readTimeoutMillis
      instanceFollowRedirects = true
      setRequestProperty("User-Agent", userAgent)
      headers.forEach { (name, value) -> setRequestProperty(name, value) }
    }

  /**
   * `HttpURLConnection` refuses to follow a redirect that changes protocol, which is exactly what a
   * release host does when it hands over to its CDN. Following them by hand is not optional.
   */
  private fun openFollowingRedirects(url: String, headers: Map<String, String>): HttpURLConnection {
    var current = url
    var redirects = 0
    while (true) {
      val connection = (URL(current).openConnection() as HttpURLConnection).apply {
        connectTimeout = connectTimeoutMillis
        readTimeout = 60_000
        instanceFollowRedirects = false
        setRequestProperty("User-Agent", userAgent)
        headers.forEach { (name, value) -> setRequestProperty(name, value) }
      }
      val code = connection.responseCode
      if (code in 300..399 && redirects < MaxRedirects) {
        val location = connection.getHeaderField("Location")
        if (!location.isNullOrBlank()) {
          current = location
          redirects++
          connection.disconnect()
          continue
        }
      }
      if (code !in 200..299) {
        connection.disconnect()
        error("Download non riuscito ($code).")
      }
      return connection
    }
  }

  companion object {
    const val DefaultUserAgent: String = "FluidEngine/1.0"
    private const val MaxRedirects = 5
  }
}
