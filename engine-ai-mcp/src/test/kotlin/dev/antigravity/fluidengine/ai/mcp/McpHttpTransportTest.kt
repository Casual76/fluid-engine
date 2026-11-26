package dev.antigravity.fluidengine.ai.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * F9.2 - Il trasporto HTTP vero contro un server vero su una porta libera del computer (la sceglie il
 * sistema: mai la 8765 del companion di Pampa Notes), solo su 127.0.0.1.
 */
class McpHttpTransportTest {

  private val server = TinyHttpServer()

  @After
  fun close() = server.close()

  private val initialize = """{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2025-11-25","capabilities":{"tools":{}},"serverInfo":{"name":"vero"}}}"""

  @Test
  fun `una risposta JSON, le intestazioni della sessione e quelle chieste dalla specifica`() = runBlocking {
    var seen: TinyHttpServer.Request? = null
    server.handle("/mcp") { request, response ->
      seen = request
      response.reply(200, initialize, mapOf("Mcp-Session-Id" to "abc"))
    }
    val transport = McpHttpTransport(server.base + "/mcp")
    val got = mutableListOf<JsonObject>()
    val exchange = transport.send(McpHttpRequest("POST", McpProtocol.request(1, "initialize", null), mapOf("MCP-Protocol-Version" to "2025-11-25"))) {
      got += it.message
      true
    }
    assertTrue(exchange.done)
    assertEquals("abc", exchange.headers["mcp-session-id"])
    assertEquals("2025-11-25", (got.single()["result"]!!.jsonObject["protocolVersion"] as JsonPrimitive).content)
    assertEquals("application/json, text/event-stream", seen!!.headers["accept"])
    assertEquals("2025-11-25", seen!!.headers["mcp-protocol-version"])
  }

  @Test
  fun `un flusso di eventi con gli id, e la risposta che lo chiude`() = runBlocking {
    server.handle("/mcp") { _, response ->
      response.begin(200, mapOf("Content-Type" to "text/event-stream"))
      response.write("id: 1\ndata:\n\n")
      response.write("id: 2\nevent: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/message\",\"params\":{}}\n\n")
      response.write(": tieni viva\n\n")
      response.write("id: 3\ndata: {\"jsonrpc\":\"2.0\",\"id\":7,\n")
      response.write("data: \"result\":{\"ok\":true}}\n\n")
    }
    val transport = McpHttpTransport(server.base + "/mcp")
    val got = mutableListOf<McpIncoming>()
    val exchange = transport.send(McpHttpRequest("POST", McpProtocol.request(7, "ping", null), emptyMap())) {
      got += it
      McpProtocol.idLong(it.message) == 7L
    }
    assertTrue(exchange.streamed)
    assertTrue(exchange.done)
    assertEquals(listOf("2", "3"), got.map { it.eventId })
    assertEquals("3", exchange.lastEventId)
  }

  @Test
  fun `un flusso che si interrompe a meta' dice l'ultimo evento visto`() = runBlocking {
    server.handle("/mcp") { _, response ->
      response.begin(200, mapOf("Content-Type" to "text/event-stream"))
      response.write("id: e9\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\",\"params\":{}}\n\n")
      // Il server chiude senza la risposta.
    }
    val exchange = McpHttpTransport(server.base + "/mcp").send(McpHttpRequest("POST", McpProtocol.request(1, "tools/call", null), emptyMap())) { false }
    assertFalse(exchange.done)
    assertEquals("e9", exchange.lastEventId)
  }

  @Test
  fun `il client vero riprende un flusso interrotto con Last-Event-ID`() = runBlocking {
    var resumedWith: String? = null
    server.handle("/mcp") { request, response ->
      val body = request.body.toString(Charsets.UTF_8)
      when {
        request.method == "GET" -> {
          resumedWith = request.headers["last-event-id"]
          response.begin(200, mapOf("Content-Type" to "text/event-stream"))
          response.write("id: r2\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"ripreso\"}]}}\n\n")
        }
        body.contains("\"initialize\"") -> response.reply(200, initialize.replace("\"id\":1", "\"id\":1"), mapOf("Mcp-Session-Id" to "s1"))
        body.contains("notifications/initialized") -> response.begin(202, emptyMap(), 0)
        else -> {
          response.begin(200, mapOf("Content-Type" to "text/event-stream"))
          response.write("id: r1\ndata:\n\n")
        }
      }
    }
    val client = McpClient(McpHttpTransport(server.base + "/mcp"))
    val result = client.callTool("lento", JsonObject(emptyMap()))
    assertEquals("ripreso", (result.content.single() as McpContent.Text).text)
    assertEquals("r1", resumedWith)
  }

  @Test
  fun `un 401 porta WWW-Authenticate fino al client`() = runBlocking {
    server.handle("/mcp") { _, response ->
      response.reply(401, "{}", mapOf("WWW-Authenticate" to "Bearer resource_metadata=\"http://127.0.0.1/.well-known/oauth-protected-resource\""))
    }
    val error = runCatching { McpClient(McpHttpTransport(server.base + "/mcp")).connect() }.exceptionOrNull()
    assertTrue(error is McpError.AuthRequired)
    assertEquals("http://127.0.0.1/.well-known/oauth-protected-resource", (error as McpError.AuthRequired).resourceMetadataUrl)
  }

  @Test
  fun `un server spento e' un errore di rete, e l'http in chiaro verso fuori non si apre`() = runBlocking {
    val port = server.port
    server.close()
    val error = runCatching {
      McpHttpTransport("http://127.0.0.1:$port/mcp", connectTimeoutMillis = 2_000).send(McpHttpRequest("POST", McpProtocol.request(1, "ping", null), emptyMap())) { true }
    }.exceptionOrNull()
    assertTrue("$error", error is McpError.Network)
    assertNotNull(runCatching { McpHttpTransport("http://example.com/mcp") }.exceptionOrNull())
  }
}
