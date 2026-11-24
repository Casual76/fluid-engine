package dev.antigravity.fluidengine.foundation

import java.net.URI
import java.net.URISyntaxException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.CharBuffer
import kotlin.reflect.KClass

/** I valori di una pagina già decodificati. Un segmento può contenere anche una barra o un +. */
data class FluidAddressParameters(
  val segments: List<String> = emptyList(),
  val query: Map<String, String> = emptyMap(),
)

/** I tre esiti dell'app. Per i dati riservati controllare l'accesso prima dell'esistenza. */
enum class FluidAddressAvailability { Available, Missing, SignInRequired }

/**
 * Una riga per tipo concreto di pagina. [name] è la prima parola dell'indirizzo (es. «nota»).
 * [decode] restituisce null per parametri inattesi. [encode] scrive gli stessi valori.
 * [parent] indica il contesto naturale della pagina. [public] autorizza un ingresso da fuori.
 */
class FluidAddressPage<P : Any, T : P>(
  internal val type: KClass<T>,
  internal val name: String,
  internal val decode: (FluidAddressParameters) -> T?,
  private val encode: (T) -> FluidAddressParameters,
  private val parent: (T) -> P?,
  internal val public: Boolean = false,
  private val availability: (T) -> FluidAddressAvailability = { FluidAddressAvailability.Available },
) {
  internal fun parameters(page: P) = encode(type.javaObjectType.cast(page))
  internal fun parentOf(page: P) = parent(type.javaObjectType.cast(page))
  internal fun stateOf(page: P) = availability(type.javaObjectType.cast(page))
}

/** Un indirizzo in ingresso con l'esito della verifica del mittente. Di serie è esterno. */
data class FluidAddressRequest(val url: String, val trusted: Boolean = false)

/** Richiede sia un nome ammesso sia la stessa firma. Il nome viene dal sistema operativo. */
fun fluidAddressCallerTrusted(
  callerPackage: String?,
  allowedPackages: Set<String>,
  sameSignature: (String) -> Boolean,
): Boolean = callerPackage != null && callerPackage in allowedPackages && sameSignature(callerPackage)

/** La pila visibile, dalla radice alla cima, oppure un ingresso da ignorare. */
sealed interface FluidAddressResult<out P> {
  /** Tutte le pagine sono disponibili. */
  data class Open<P>(val stack: List<P>) : FluidAddressResult<P>
  /** La prima pagina mancante. L'app presenta un avviso sopra i genitori disponibili. */
  data class Missing<P>(val parents: List<P>) : FluidAddressResult<P>
  /** Dopo l'accesso va rivalutata la richiesta originale, con la stessa provenienza. */
  data class SignIn<P>(val parents: List<P>, val pending: FluidAddressRequest) : FluidAddressResult<P>
  /** Indirizzo malformato, sconosciuto o non pubblico per un mittente esterno. */
  data object Rejected : FluidAddressResult<Nothing>
}

private val pageName = Regex("[a-z][a-z0-9-]*")
private val appScheme = Regex("[a-z][a-z0-9+.-]*")
private const val hex = "0123456789ABCDEF"

/** Codifica per componente URI, non per moduli HTML. */
private fun encodePart(value: String): String {
  require(value.none(Char::isISOControl)) { "Carattere di controllo nell'indirizzo" }
  val bytes = Charsets.UTF_8.newEncoder().encode(CharBuffer.wrap(value))
  return buildString {
    while (bytes.hasRemaining()) {
      val n = bytes.get().toInt() and 255
      val c = n.toChar()
      if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~") append(c)
      else append('%').append(hex[n ushr 4]).append(hex[n and 15])
    }
  }
}

// URI ha già validato la sintassi delle percentuali. Si decodifica una volta sola
// e si rifiuta l'UTF-8 malformato, così due identificatori diversi non collassano sullo stesso.
private fun decodePart(raw: String): String? {
  val input = raw.toByteArray(Charsets.UTF_8)
  val buffer = ByteBuffer.allocate(input.size)
  var i = 0
  while (i < input.size) {
    if (input[i] == '%'.code.toByte()) {
      val high = Character.digit(input[i + 1].toInt().toChar(), 16)
      val low = Character.digit(input[i + 2].toInt().toChar(), 16)
      buffer.put(((high shl 4) or low).toByte())
      i += 3
    } else {
      buffer.put(input[i++])
    }
  }
  buffer.flip()
  return try {
    Charsets.UTF_8.newDecoder().decode(buffer).toString().takeUnless { it.any(Char::isISOControl) }
  } catch (_: CharacterCodingException) {
    null
  }
}

/**
 * La tabella bidirezionale dell'app. [httpsHost] abilita anche https://dominio/nome/parametri
 * sul solo host esatto. L'associazione Android del dominio rimane a carico dell'app.
 */
class FluidAddressTable<P : Any>(
  private val scheme: String,
  pages: List<FluidAddressPage<P, out P>>,
  private val httpsHost: String? = null,
) {
  private val byName = pages.associateBy { it.name }
  private val byType = pages.associateBy { it.type }

  init {
    require(appScheme.matches(scheme) && scheme !in setOf("http", "https"))
    require(pages.all { pageName.matches(it.name) })
    require(byType.size == pages.size && byName.size == pages.size) { "Tipo o nome pagina duplicato" }
    require(httpsHost == null || (URI("https://" + httpsHost).host == httpsHost && httpsHost.isNotEmpty()))
  }

  /** L'indirizzo canonico della pagina, pronto per gli appunti o la condivisione. */
  fun addressOf(page: P): String {
    val row = requireNotNull(byType[page::class]) { "Pagina assente dalla tabella" }
    val (segments, query) = row.parameters(page)
    return buildString {
      append(scheme).append("://").append(row.name)
      for (segment in segments) {
        require(segment.isNotEmpty() && segment != "." && segment != "..")
        append('/').append(encodePart(segment))
      }
      query.toSortedMap().entries.forEachIndexed { index, (key, value) ->
        require(key.isNotEmpty())
        append(if (index == 0) '?' else '&').append(encodePart(key)).append('=').append(encodePart(value))
      }
    }
  }

  /** Risolve fino a 64 pagine inclusa la destinazione. Per un esterno anche i genitori devono essere pubblici. */
  fun resolve(request: FluidAddressRequest): FluidAddressResult<P> {
    val (name, params) = parse(request.url) ?: return FluidAddressResult.Rejected
    val row = byName[name] ?: return FluidAddressResult.Rejected
    if (!row.public && !request.trusted) return FluidAddressResult.Rejected
    var page = row.decode(params) ?: return FluidAddressResult.Rejected
    val chain = linkedSetOf<P>()
    while (true) {
      val current = byType[page::class] ?: return FluidAddressResult.Rejected
      if (!current.public && !request.trusted) return FluidAddressResult.Rejected
      // Un genitore errato non deve bloccare il thread UI, neppure se produce sempre nuovi id.
      if (chain.size >= 64 || !chain.add(page)) return FluidAddressResult.Rejected
      page = current.parentOf(page) ?: break
    }
    val available = mutableListOf<P>()
    for (item in chain.toList().asReversed()) {
      when (byType.getValue(item::class).stateOf(item)) {
        FluidAddressAvailability.Available -> available.add(item)
        FluidAddressAvailability.Missing -> return FluidAddressResult.Missing(available)
        FluidAddressAvailability.SignInRequired -> return FluidAddressResult.SignIn(available, request)
      }
    }
    return FluidAddressResult.Open(available)
  }

  private fun parse(url: String): Pair<String, FluidAddressParameters>? {
    if (!Charsets.UTF_8.newEncoder().canEncode(url)) return null
    val uri = try {
      URI(url)
    } catch (_: URISyntaxException) {
      return null
    }
    if (uri.isOpaque || uri.rawFragment != null || uri.rawAuthority == null) return null
    val path = uri.rawPath.orEmpty()
    val parts = if (path.isEmpty()) emptyList() else path.removePrefix("/").split('/')
    val web = uri.scheme.equals("https", ignoreCase = true)
    val name = when {
      web && httpsHost != null && uri.rawAuthority.equals(httpsHost, ignoreCase = true) -> parts.firstOrNull()
      uri.scheme.equals(scheme, ignoreCase = true) -> uri.rawAuthority.lowercase()
      else -> null
    } ?: return null
    val segments = (if (web) parts.drop(1) else parts).map {
      val part = decodePart(it) ?: return null
      if (part.isEmpty() || part == "." || part == "..") return null
      part
    }
    val query = mutableMapOf<String, String>()
    uri.rawQuery?.split('&')?.forEach {
      val delimiter = it.indexOf('=')
      if (delimiter <= 0) return null
      val key = decodePart(it.substring(0, delimiter)) ?: return null
      val value = decodePart(it.substring(delimiter + 1)) ?: return null
      if (query.put(key, value) != null) return null
    }
    return name to FluidAddressParameters(segments, query)
  }
}
