package dev.antigravity.fluidengine.people.client

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.net.ssl.SSLSocketFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** Perché il canale si è chiuso: dal server (col codice, per esempio 4003 «dispositivo uscito»), o per la rete. */
data class ChannelClosed(val code: Int, val reason: String)

/**
 * Il canale aperto con la casella (`GET /v1/casella/ws`, `d\server-e-chiavi.md` § 5): un WebSocket con ibernazione,
 * **solo mentre lo Store o Pampa Mail sono aperti**. Porta solo «c'è posta» (`{"t":"posta","n":…}`); il telefono manda
 * al massimo «ping», a cui il server risponde da sé senza svegliare la casella. Si chiude con il token (15 minuti).
 *
 * È un client RFC 6455 minimo, senza librerie: testo, ping/pong e chiusura, i frame del client mascherati. Le prove lo
 * parlano col WebSocket di MockWebServer (OkHttp), il lato server vero di un'altra implementazione.
 */
class PeopleSocket(
  /** `wss://account.pampa-apps.com/v1/casella/ws` (o `ws://` nelle prove). */
  private val url: String,
  private val auth: PeopleAuth,
  private val storeVersion: String,
  private val pingEveryMillis: Long = 30_000,
  private val connect: (host: String, port: Int, tls: Boolean) -> Socket = ::defaultSocket,
) {
  @Volatile private var socket: Socket? = null

  /** Apre il canale e resta in ascolto finché si chiude; [onMail] riceve il numero dell'ultima busta. */
  suspend fun run(onMail: suspend (Long) -> Unit): ChannelClosed = withContext(Dispatchers.IO) {
    val uri = URI(url)
    val tls = uri.scheme == "wss"
    val port = if (uri.port > 0) uri.port else if (tls) 443 else 80
    val s = connect(uri.host, port, tls)
    socket = s
    try {
      val input = BufferedInputStream(s.getInputStream())
      val output = s.getOutputStream()
      handshake(uri, input, output)
      coroutineScope {
        val pinger = launch {
          while (true) {
            delay(pingEveryMillis)
            synchronized(output) { writeFrame(output, OP_TEXT, "ping".toByteArray()) }
          }
        }
        // La lettura è bloccante: se chi ascolta smette (l'app va in sottofondo), chiudere il socket la sblocca.
        val closer = launch {
          try {
            awaitCancellation()
          } finally {
            runCatching { s.close() }
          }
        }
        try {
          readLoop(input, output, onMail)
        } finally {
          pinger.cancel()
          closer.cancel()
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: IOException) {
      ChannelClosed(1006, e.message ?: "rete")
    } finally {
      runCatching { s.close() }
      socket = null
    }
  }

  /** Chiude il canale (l'app va in sottofondo): da lì in poi suona il campanello. */
  fun close() {
    runCatching { socket?.close() }
  }

  private suspend fun handshake(uri: URI, input: InputStream, output: OutputStream) {
    val key = Base64.getEncoder().encodeToString(ByteArray(16).also { random.nextBytes(it) })
    val path = (uri.rawPath ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
    val request = buildString {
      append("GET $path HTTP/1.1\r\n")
      append("Host: ${uri.host}${if (uri.port > 0) ":${uri.port}" else ""}\r\n")
      append("Upgrade: websocket\r\nConnection: Upgrade\r\n")
      append("Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n")
      append("Authorization: Bearer ${auth.token()}\r\n")
      append("pampa-store-version: $storeVersion\r\n\r\n")
    }
    output.write(request.toByteArray(Charsets.US_ASCII))
    output.flush()
    val status = readLine(input)
    val headers = HashMap<String, String>()
    while (true) {
      val line = readLine(input)
      if (line.isEmpty()) break
      val i = line.indexOf(':')
      if (i > 0) headers[line.substring(0, i).trim().lowercase()] = line.substring(i + 1).trim()
    }
    if (!status.contains(" 101 ")) throw IOException("il server non apre il canale: $status")
    val expected = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray()))
    if (headers["sec-websocket-accept"] != expected) throw IOException("risposta del canale non valida")
  }

  private suspend fun readLoop(input: InputStream, output: OutputStream, onMail: suspend (Long) -> Unit): ChannelClosed {
    val message = ByteArrayOutputStream()
    var messageOp = 0
    while (true) {
      val b0 = input.read()
      if (b0 < 0) return ChannelClosed(1006, "fine del flusso")
      val b1 = input.read()
      if (b1 < 0) return ChannelClosed(1006, "fine del flusso")
      val fin = b0 and 0x80 != 0
      val op = b0 and 0x0F
      var len = (b1 and 0x7F).toLong()
      if (len == 126L) len = ((input.read() shl 8) or input.read()).toLong()
      else if (len == 127L) { len = 0; repeat(8) { len = (len shl 8) or input.read().toLong() } }
      if (len > MAX_FRAME) throw IOException("frame troppo grande")
      val mask = if (b1 and 0x80 != 0) ByteArray(4).also { readFully(input, it) } else null
      val payload = ByteArray(len.toInt()).also { readFully(input, it) }
      if (mask != null) for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
      when (op) {
        OP_PING -> synchronized(output) { writeFrame(output, OP_PONG, payload) }
        OP_PONG -> Unit
        OP_CLOSE -> {
          val code = if (payload.size >= 2) ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF) else 1005
          val reason = if (payload.size > 2) payload.copyOfRange(2, payload.size).decodeToString() else ""
          runCatching { synchronized(output) { writeFrame(output, OP_CLOSE, payload.copyOf(minOf(2, payload.size))) } }
          return ChannelClosed(code, reason)
        }
        OP_TEXT, OP_BINARY, OP_CONTINUATION -> {
          if (op != OP_CONTINUATION) messageOp = op
          message.write(payload)
          if (fin) {
            val bytes = message.toByteArray()
            message.reset()
            if (messageOp == OP_TEXT) onText(bytes.decodeToString(), onMail)
          }
        }
      }
    }
  }

  private suspend fun onText(text: String, onMail: suspend (Long) -> Unit) {
    if (text == "pong") return
    val o = runCatching { Json.parseToJsonElement(text) as JsonObject }.getOrNull() ?: return
    if (o.s("t") == "posta") onMail(o.l("n"))
  }

  companion object {
    private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    private const val OP_CONTINUATION = 0x0
    private const val OP_TEXT = 0x1
    private const val OP_BINARY = 0x2
    private const val OP_CLOSE = 0x8
    private const val OP_PING = 0x9
    private const val OP_PONG = 0xA
    private const val MAX_FRAME = 1L shl 20
    private val random = SecureRandom()

    fun defaultSocket(host: String, port: Int, tls: Boolean): Socket =
      (if (tls) SSLSocketFactory.getDefault().createSocket(host, port) else Socket(host, port)).apply { tcpNoDelay = true }

    /** Un frame del client, sempre mascherato (RFC 6455 § 5.3). */
    internal fun writeFrame(output: OutputStream, op: Int, payload: ByteArray) {
      val header = ByteArrayOutputStream()
      header.write(0x80 or op)
      when {
        payload.size < 126 -> header.write(0x80 or payload.size)
        payload.size < 65536 -> { header.write(0x80 or 126); header.write(payload.size ushr 8); header.write(payload.size and 0xFF) }
        else -> { header.write(0x80 or 127); for (s in 56 downTo 0 step 8) header.write(((payload.size.toLong() ushr s) and 0xFF).toInt()) }
      }
      val mask = ByteArray(4).also { random.nextBytes(it) }
      header.write(mask)
      val masked = ByteArray(payload.size) { (payload[it].toInt() xor mask[it % 4].toInt()).toByte() }
      output.write(header.toByteArray())
      output.write(masked)
      output.flush()
    }

    private fun readLine(input: InputStream): String {
      val out = ByteArrayOutputStream()
      while (true) {
        val c = input.read()
        if (c < 0) throw IOException("risposta del canale interrotta")
        if (c == '\n'.code) break
        if (c != '\r'.code) out.write(c)
        if (out.size() > 8192) throw IOException("intestazione troppo lunga")
      }
      return out.toString(Charsets.US_ASCII.name())
    }

    private fun readFully(input: InputStream, into: ByteArray) {
      var done = 0
      while (done < into.size) {
        val n = input.read(into, done, into.size - done)
        if (n < 0) throw IOException("frame interrotto")
        done += n
      }
    }
  }
}
