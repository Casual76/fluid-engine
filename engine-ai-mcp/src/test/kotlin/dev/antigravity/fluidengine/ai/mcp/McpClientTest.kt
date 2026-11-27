package dev.antigravity.fluidengine.ai.mcp

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** F9.2 - Il protocollo MCP contro il server finto in memoria: strumenti, chiamate, errori, versioni, flussi interrotti. */
class McpClientTest {

  private fun tool(name: String, description: String = "fa $name", readOnly: Boolean = true) = McpMemoryTool(
    McpToolInfo(name, title = name.replaceFirstChar { it.uppercase() }, description = description,
      inputSchema = McpMemoryServer.schema("quando" to "il giorno"), annotations = McpToolAnnotations(readOnlyHint = readOnly)),
  ) { args -> McpMemoryServer.text("$name: ${args["quando"]?.let { (it as JsonPrimitive).content } ?: "oggi"}") }

  private fun server(vararg names: String) = McpMemoryServer(names.map { tool(it) }, title = "Calendario finto")

  private inline fun <reified T : Throwable> failure(block: () -> Unit): T {
    try {
      block()
    } catch (e: Throwable) {
      if (e is T) return e
      throw AssertionError("atteso ${T::class.simpleName}, arrivato $e", e)
    }
    fail("nessun errore")
    error("non raggiungibile")
  }

  @Test
  fun `apre la sessione con la versione piu' nuova, legge gli strumenti a pagine e li chiama`() = runBlocking {
    val server = server("eventi", "evento_crea", "promemoria", "liste", "note").apply { pageSize = 2 }
    val client = McpClient(server)
    val session = client.connect()
    assertEquals(McpProtocol.LATEST, session.protocolVersion)
    assertEquals("sessione-1", session.sessionId)
    assertEquals("Calendario finto", session.server.title)
    val tools = client.listTools()
    assertEquals(listOf("eventi", "evento_crea", "promemoria", "liste", "note"), tools.map { it.name })
    assertEquals(3, server.log.count { it == "POST tools/list" })
    val result = client.callTool("eventi", buildJsonObject { put("quando", "domani") })
    assertEquals("eventi: domani", (result.content.single() as McpContent.Text).text)
    assertFalse(result.isError)
    assertEquals(listOf("POST initialize", "POST notifications/initialized"), server.log.take(2))
  }

  @Test
  fun `accetta una versione vecchia che conosce, e rifiuta quella che non conosce`() = runBlocking {
    val older = server("eventi").apply { version = "2025-06-18" }
    assertEquals("2025-06-18", McpClient(older).connect().protocolVersion)
    val oldest = server("eventi").apply { version = "2025-03-26" }
    assertEquals("2025-03-26", McpClient(oldest).connect().protocolVersion)

    val unknown = server("eventi").apply { version = "2024-11-05" }
    val error = failure<McpError.UnsupportedVersion> { runBlocking { McpClient(unknown).connect() } }
    assertEquals("2024-11-05", error.serverVersion)
    // La sessione aperta dal server si chiude.
    assertTrue(unknown.log.contains("DELETE"))
    assertFalse(unknown.log.contains("POST notifications/initialized"))
  }

  @Test
  fun `in un flusso risponde al ping del server, ignora le notifiche e prende la risposta`() = runBlocking {
    val server = server("eventi").apply { streaming = true }
    val client = McpClient(server)
    val result = client.callTool("eventi", JsonObject(emptyMap()))
    assertEquals("eventi: oggi", (result.content.single() as McpContent.Text).text)
    // Il ping del server ha avuto la sua risposta vuota.
    assertTrue(server.clientReplies.any { it["result"] is JsonObject })
  }

  @Test
  fun `un flusso interrotto si riprende con Last-Event-ID`() = runBlocking {
    val server = server("eventi").apply { streaming = true }
    val client = McpClient(server)
    client.connect()
    server.interruptNextStream = true
    val result = client.callTool("eventi", JsonObject(emptyMap()))
    assertEquals("eventi: oggi", (result.content.single() as McpContent.Text).text)
    assertTrue(server.log.contains("GET"))
    // Il tool ha girato una volta sola: la ripresa non rimanda la richiesta.
    assertEquals(1, server.calls.size)
  }

  @Test
  fun `un flusso interrotto che non si riprende e' un errore incerto`() = runBlocking {
    val server = server("evento_crea").apply { streaming = true; forgetEvents = true }
    val client = McpClient(server)
    client.connect()
    server.interruptNextStream = true
    val error = failure<McpError.Interrupted> { runBlocking { client.callTool("evento_crea", JsonObject(emptyMap())) } }
    assertTrue(error.uncertain)
  }

  @Test
  fun `una sessione scaduta si riapre e la richiesta si ripete una volta`() = runBlocking {
    val server = server("eventi")
    val client = McpClient(server)
    client.connect()
    server.expireSession = true
    client.listTools()
    assertEquals(2, server.log.count { it == "POST initialize" })
    assertEquals("sessione-2", client.session.value?.sessionId)
  }

  @Test
  fun `gli errori del server e quelli dei tool`() = runBlocking {
    val server = McpMemoryServer(listOf(McpMemoryTool(McpToolInfo("rotto", null, "si rompe", McpMemoryServer.schema())) { McpMemoryServer.text("disco pieno", isError = true) }))
    val client = McpClient(server)
    val rpc = failure<McpError.Rpc> { runBlocking { client.callTool("inesistente", JsonObject(emptyMap())) } }
    assertEquals(McpProtocol.INVALID_PARAMS, rpc.code)
    val result = client.callTool("rotto", JsonObject(emptyMap()))
    assertTrue(result.isError)
  }

  @Test
  fun `un 401 chiede l'accesso coi metadati del server, e un gettone rinnovato riprova`() = runBlocking {
    val server = server("eventi").apply { requiredToken = "buono" }
    val error = failure<McpError.AuthRequired> { runBlocking { McpClient(server).connect() } }
    assertEquals("https://auth.example/.well-known/oauth-protected-resource", error.resourceMetadataUrl)
    assertEquals("tools", error.scope)

    var token = "scaduto"
    val credentials = object : McpCredentials {
      override suspend fun bearer() = token
      override suspend fun refreshAfterUnauthorized(rejected: String?): Boolean {
        token = "buono"
        return true
      }
    }
    val session = McpClient(server, credentials).connect()
    assertNotNull(session.sessionId)
  }

  @Test
  fun `una risposta che non arriva in tempo finisce, e il server lo sa`() = runBlocking {
    val server = server("eventi")
    val slow = object : McpTransport {
      override suspend fun send(request: McpHttpRequest, onMessage: suspend (McpIncoming) -> Boolean): McpExchange {
        val method = (request.body?.get("method") as? JsonPrimitive)?.content
        if (method == "tools/call") delay(5_000)
        return server.send(request, onMessage)
      }
    }
    // F7.32 · Il tempo massimo vale per ogni richiesta, anche per le due di `connect()`, in tempo vero: con 300 ms sul PC
    // carico poteva scadere già lì. La chiamata lenta dura 5 s: con 2 s la prova dice la stessa cosa.
    val client = McpClient(slow, requestTimeoutMillis = 2_000)
    client.connect()
    val error = failure<McpError.Timeout> { runBlocking { client.callTool("eventi", JsonObject(emptyMap())) } }
    assertTrue(error.uncertain)
    assertTrue(server.log.contains("POST notifications/cancelled"))
  }

  @Test
  fun `il ping e la chiusura della sessione`() = runBlocking {
    val server = server("eventi")
    val client = McpClient(server)
    assertTrue(client.ping())
    client.close()
    assertTrue(server.log.contains("DELETE"))
    assertEquals(null, client.session.value)
  }

  @Test
  fun `i parametri di WWW-Authenticate e degli indirizzi di ritorno`() {
    val header = "Bearer realm=\"mcp\", resource_metadata=\"https://x.example/.well-known/oauth-protected-resource/mcp\", scope=\"read write\""
    assertEquals("https://x.example/.well-known/oauth-protected-resource/mcp", McpOAuth.parameter(header, "resource_metadata"))
    assertEquals("read write", McpOAuth.parameter(header, "scope"))
    assertEquals(null, McpOAuth.parameter(header, "error"))
    assertEquals(mapOf("code" to "a b", "state" to "x"), McpOAuth.query("app:/mcp?code=a+b&state=x"))
    assertEquals("https://mcp.example.com/server", McpUrls.canonical("HTTPS://MCP.Example.com:443/server/"))
    assertTrue(McpUrls.allowed("https://mcp.example.com"))
    assertTrue(McpUrls.allowed("http://127.0.0.1:9000/mcp"))
    assertFalse(McpUrls.allowed("http://mcp.example.com"))
    assertFalse(McpUrls.allowed("file:///etc/passwd"))
  }
}
