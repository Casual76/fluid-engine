package dev.antigravity.fluidengine.ai.mcp

import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Un indirizzo letto da un QR o scritto a mano (4.0, F9.2): l'indirizzo del server e, se c'era, un nome.
 * Il QR di un connettore e' l'indirizzo https del server, con facoltativo `#nome=...` (o la forma
 * `mcp:https://...`); niente altro, niente segreti.
 */
data class McpConnectorLink(val url: String, val name: String?) {
  companion object {
    fun parse(text: String): McpConnectorLink? {
      var raw = text.trim()
      if (raw.startsWith("mcp:", ignoreCase = true)) raw = raw.substring(4)
      if (!raw.contains("://")) raw = "https://$raw"
      val name = raw.substringAfter("#", "").split('&').firstOrNull { it.startsWith("nome=") || it.startsWith("name=") }
        ?.substringAfter('=')?.let { java.net.URLDecoder.decode(it, "UTF-8") }?.trim()?.takeIf { it.isNotEmpty() }
      val url = raw.substringBefore('#')
      if (!McpUrls.allowed(url)) return null
      return McpConnectorLink(url, name)
    }
  }
}

/** Un connettore come lo vede la pagina: il salvato, se ha i gettoni, e lo stato dei suoi strumenti. */
data class McpConnectorView(
  val connector: McpConnector,
  val signedIn: Boolean,
  val tools: List<McpToolView>,
) {
  val status: McpConnectorStatus
    get() = when {
      connector.needsSignIn && !signedIn -> McpConnectorStatus.NEEDS_SIGN_IN
      connector.lastError != null -> McpConnectorStatus.ERROR
      connector.refreshedAtMillis == null -> McpConnectorStatus.NEW
      else -> McpConnectorStatus.READY
    }
}

/** Lo stato di un connettore nella pagina. */
enum class McpConnectorStatus { NEW, READY, NEEDS_SIGN_IN, ERROR }

/** Uno strumento nella pagina: com'e' scritto dal servizio, gli avvisi, la regola, e se aspetta l'utente. */
data class McpToolView(
  val info: McpToolInfo,
  val review: McpToolReview,
  val policy: McpToolPolicy,
  val held: McpHeld?,
)

/**
 * I connettori di un'app (4.0, F9.2): la lista salvata, i gettoni, l'accesso e i tool per il catalogo. E'
 * quello che la pagina «Connettori» ([FluidConnectorsPage]) usa, e quello che l'app interroga a ogni domanda
 * per montare il catalogo ([toolSets]).
 *
 * - [add] salva l'indirizzo e prova a leggere gli strumenti ([refresh]); se il server chiede l'accesso il
 *   connettore resta "da collegare".
 * - [refresh] apre la sessione, legge gli strumenti e li salva: la prima volta li approva tutti con la
 *   regola [McpToolPolicy.ASK]; dopo, uno nuovo o cambiato resta fuori finche' l'utente non lo approva
 *   ([approve]).
 * - [beginSignIn] da' l'indirizzo da aprire nel browser; [completeSignIn] riceve il ritorno.
 * - [remove] dimentica il connettore e i suoi gettoni, e chiude la sessione.
 */
class McpConnectors(
  private val store: McpConnectorStore,
  private val tokens: McpTokenStore,
  private val oauth: McpOAuth,
  /** Il trasporto per un indirizzo: di serie [McpHttpTransport]; le prove e la galleria danno un server finto. */
  private val transport: (String) -> McpTransport = { McpHttpTransport(it) },
  private val clientInfo: McpClientInfo = McpClientInfo("Fluid Engine", "4.0"),
  /** I `client_id` che l'app ha gia' per alcuni server di accesso (per issuer), quando non si registra da se'. */
  private val presetClients: Map<String, String> = emptyMap(),
  private val clock: () -> Long = System::currentTimeMillis,
) {
  private val clients = ConcurrentHashMap<String, McpClient>()

  /** I connettori per la pagina, con lo stato dei loro strumenti. */
  val views: Flow<List<McpConnectorView>> = combine(store.connectors, tokens.signedIn) { list, signed ->
    list.map { view(it, it.id in signed) }
  }

  /** Il cliente di un connettore: uno per connettore, con le sue credenziali. */
  fun client(connector: McpConnector): McpClient = clients.getOrPut(connector.id) {
    McpClient(transport(connector.url), McpAuthorization(connector.id, connector.url, tokens, oauth, clock), clientInfo)
  }

  /** I tool di tutti i connettori, pronti per `ToolRegistry.withConnectors`. [taken] sono i nomi gia' nel catalogo dell'app. */
  suspend fun <C> toolSets(host: McpToolHost<C>, taken: Set<String> = emptySet()): List<McpToolSet<C>> {
    val names = taken.toMutableSet()
    return store.current().map { connector ->
      McpToolSet.of(connector, client(connector), host, names).also { set -> names += set.tools.map { it.name } }
    }
  }

  /** Aggiunge un connettore da un indirizzo o da un QR; torna il suo id. Lo stesso indirizzo due volte non si duplica. */
  suspend fun add(link: McpConnectorLink): String {
    store.current().firstOrNull { it.url == link.url }?.let { return it.id }
    val id = UUID.randomUUID().toString().take(8)
    val name = link.name ?: runCatching { URL(link.url).host.removePrefix("www.").removePrefix("mcp.") }.getOrNull() ?: "Connettore"
    store.put(McpConnector(id = id, name = name.take(40), url = link.url, addedAtMillis = clock()))
    refresh(id)
    return id
  }

  /**
   * Legge gli strumenti del server e li salva. Torna lo stato a cui si e' arrivati: un errore non lancia,
   * finisce nel connettore ([McpConnector.lastError], [McpConnector.needsSignIn]).
   */
  suspend fun refresh(id: String): McpConnectorStatus {
    val connector = store.get(id) ?: return McpConnectorStatus.ERROR
    val client = client(connector)
    try {
      val session = client.connect()
      val tools = client.listTools()
      store.edit(id) { current ->
        val first = current.refreshedAtMillis == null
        val approvals = if (first) tools.associate { it.name to McpApproval(McpGuard.hash(it)) } else current.approvals.filterKeys { name -> tools.any { it.name == name } }
        current.copy(
          tools = tools,
          approvals = approvals,
          server = session.server,
          instructions = session.instructions?.let { McpGuard.clean(it).take(2_000) },
          protocolVersion = session.protocolVersion,
          needsSignIn = false,
          lastError = null,
          refreshedAtMillis = clock(),
          name = if (first && current.name == runCatching { URL(current.url).host.removePrefix("www.").removePrefix("mcp.") }.getOrNull()) {
            McpGuard.clean(session.server.title ?: current.name).take(40).ifBlank { current.name }
          } else {
            current.name
          },
        )
      }
      return McpConnectorStatus.READY
    } catch (e: CancellationException) {
      throw e
    } catch (e: McpError.AuthRequired) {
      store.edit(id) { it.copy(needsSignIn = true, resourceMetadataUrl = e.resourceMetadataUrl ?: it.resourceMetadataUrl, scope = e.scope ?: it.scope, lastError = null) }
      return McpConnectorStatus.NEEDS_SIGN_IN
    } catch (e: McpError) {
      store.edit(id) { it.copy(lastError = e.message) }
      return McpConnectorStatus.ERROR
    } catch (e: IllegalArgumentException) {
      store.edit(id) { it.copy(lastError = e.message) }
      return McpConnectorStatus.ERROR
    }
  }

  /** L'inizio dell'accesso: l'indirizzo da aprire nel browser (o in una Custom Tab). */
  suspend fun beginSignIn(id: String): String {
    val connector = store.get(id) ?: throw McpError.Protocol("connettore sconosciuto")
    val (server, scopes) = oauth.discover(connector.url, connector.resourceMetadataUrl)
    val saved = tokens.client(id)?.takeIf { it.second.issuer == server.issuer }?.first
    val clientId = oauth.clientId(server, saved ?: presetClients[server.issuer])
    tokens.setClient(id, clientId, server)
    val scope = connector.scope ?: scopes.takeIf { it.isNotEmpty() }?.joinToString(" ")
    val request = oauth.begin(connector.url, server, clientId, scope)
    tokens.setPending(id, request)
    return request.authorizationUrl
  }

  /**
   * Il ritorno dal browser: [callback] e' l'indirizzo con cui l'app e' stata riaperta. Torna l'id del
   * connettore entrato, o null se il ritorno non e' di nessun connettore in attesa.
   */
  suspend fun completeSignIn(callback: String): String? {
    val state = McpOAuth.query(callback)["state"] ?: return null
    val id = tokens.pendingFor(state) ?: return null
    val request = tokens.pending(id) ?: return null
    try {
      tokens.setTokens(id, oauth.complete(request, callback))
    } finally {
      tokens.setPending(id, null)
    }
    clients.remove(id)
    refresh(id)
    return id
  }

  /** Esce: i gettoni si cancellano, il connettore resta (da collegare). */
  suspend fun signOut(id: String) {
    tokens.setTokens(id, null)
    clients.remove(id)?.close()
    store.edit(id) { it.copy(needsSignIn = true) }
  }

  /** Toglie il connettore: la sessione si chiude, i gettoni e la lista lo dimenticano. */
  suspend fun remove(id: String) {
    clients.remove(id)?.let { runCatching { it.close() } }
    tokens.forget(id)
    store.remove(id)
  }

  suspend fun rename(id: String, name: String) = store.edit(id) { it.copy(name = McpGuard.clean(name).take(40).ifBlank { it.name }) }

  /**
   * La regola di uno strumento. «Sempre» vale solo per gli strumenti che il server dice di sola lettura: una scrittura
   * chiede sempre, e la regola resta «Chiedi». Uno strumento nuovo o cambiato si approva con [approve].
   */
  suspend fun setPolicy(id: String, tool: String, policy: McpToolPolicy) = store.edit(id) { c ->
    val approval = c.approvals[tool] ?: return@edit c
    val info = c.tools.firstOrNull { it.name == tool }
    val allowed = if (policy == McpToolPolicy.ALWAYS && info?.annotations?.readOnlyHint() != true) McpToolPolicy.ASK else policy
    c.copy(approvals = c.approvals + (tool to approval.copy(policy = allowed)))
  }

  /** L'utente ha visto lo strumento com'e' adesso e lo approva: torna nel catalogo, con la regola [McpToolPolicy.ASK]. */
  suspend fun approve(id: String, tool: String) = store.edit(id) { c ->
    val info = c.tools.firstOrNull { it.name == tool } ?: return@edit c
    c.copy(approvals = c.approvals + (tool to McpApproval(McpGuard.hash(info), c.approvals[tool]?.policy?.takeIf { it != McpToolPolicy.OFF } ?: McpToolPolicy.ASK)))
  }

  private fun view(connector: McpConnector, signedIn: Boolean): McpConnectorView {
    val others = connector.tools.map { it.name }.toSet()
    return McpConnectorView(connector, signedIn, connector.tools.map { info ->
      val approval = connector.approvals[info.name]
      val held = when {
        approval == null -> McpHeld.NEW
        approval.hash != McpGuard.hash(info) -> McpHeld.CHANGED
        approval.policy == McpToolPolicy.OFF -> McpHeld.OFF
        else -> null
      }
      McpToolView(info, McpGuard.review(info, connector.name, others), approval?.policy ?: McpToolPolicy.ASK, held)
    })
  }
}
