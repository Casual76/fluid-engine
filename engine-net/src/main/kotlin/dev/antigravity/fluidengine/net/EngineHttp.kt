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
 *
 * `open` for one reason: everything above the network — deciding whether a release is newer, reading
 * a manifest, resolving a download URL — is ordinary logic that deserves ordinary tests, and a final
 * class here would force every host app to reach the network to test any of it.
 */
open class EngineHttp(
  private val userAgent: String = DefaultUserAgent,
  private val connectTimeoutMillis: Int = 15_000,
  private val readTimeoutMillis: Int = 30_000,
) {

  /**
   * Reads a small document (a manifest, a config) as UTF-8 text. At most 8 MiB: a bigger body is not a
   * manifest, and reading it whole could run the app out of memory. An error's body is not read at all.
   */
  open suspend fun readText(url: String, headers: Map<String, String> = emptyMap()): String =
    withContext(Dispatchers.IO) {
      val connection = open(url, headers + ("Accept" to "application/json"))
      try {
        val code = connection.responseCode
        if (code !in 200..299) {
          error("Richiesta non riuscita ($code).")
        }
        if (connection.contentLengthLong > MaxTextBytes) error("Risposta troppo grande.")
        val bytes = connection.inputStream.use { input ->
          val out = java.io.ByteArrayOutputStream()
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
          while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > MaxTextBytes) error("Risposta troppo grande.")
          }
          out.toByteArray()
        }
        String(bytes, Charsets.UTF_8)
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
  open suspend fun download(
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
      var downloaded = 0L
      connection.inputStream.use { input ->
        temporary.outputStream().use { output ->
          val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
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
      // Un download troncato non solleva niente: la connessione si chiude e il file resta a meta'.
      // Quando chi chiama sa quanto deve pesare — il manifest lo dice — non verificarlo significa
      // consegnare mezzo APK all'installer, che lo rifiuta con un errore che non spiega niente.
      if (expectedBytes > 0 && downloaded != expectedBytes) {
        error("File incompleto: $downloaded byte invece di $expectedBytes.")
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
   *
   * A `Location` is resolved against the current URL (a relative one too). The credentials
   * (`Authorization`, `Proxy-Authorization`, `Cookie`) stay behind on a hop to another origin (scheme,
   * host or port) and never come back: a release host's token must not reach its CDN. A redirect from
   * https to http, or to anything that is not http, is refused.
   */
  private fun openFollowingRedirects(url: String, headers: Map<String, String>): HttpURLConnection {
    var current = URL(url)
    var sent = headers
    var redirects = 0
    while (true) {
      val connection = (current.openConnection() as HttpURLConnection).apply {
        connectTimeout = connectTimeoutMillis
        readTimeout = 60_000
        instanceFollowRedirects = false
        setRequestProperty("User-Agent", userAgent)
        sent.forEach { (name, value) -> setRequestProperty(name, value) }
      }
      val code = connection.responseCode
      if (code in 300..399 && redirects < MaxRedirects) {
        val location = connection.getHeaderField("Location")
        if (!location.isNullOrBlank()) {
          connection.disconnect()
          val next = URL(current, location)
          val scheme = next.protocol.lowercase()
          val downgrade = current.protocol.equals("https", ignoreCase = true) && scheme != "https"
          if (downgrade || (scheme != "http" && scheme != "https")) error("Download non riuscito ($code).")
          if (!sameOrigin(current, next)) sent = sent.filterKeys { it.lowercase() !in CredentialHeaders }
          current = next
          redirects++
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

    /** The most [readText] reads: 8 MiB, far above any manifest. */
    private const val MaxTextBytes = 8L * 1024L * 1024L

    /** What does not follow a redirect to another origin. */
    private val CredentialHeaders = setOf("authorization", "proxy-authorization", "cookie")

    private fun sameOrigin(a: URL, b: URL): Boolean =
      a.protocol.equals(b.protocol, ignoreCase = true) &&
        a.host.equals(b.host, ignoreCase = true) &&
        (if (a.port == -1) a.defaultPort else a.port) == (if (b.port == -1) b.defaultPort else b.port)
  }
}
