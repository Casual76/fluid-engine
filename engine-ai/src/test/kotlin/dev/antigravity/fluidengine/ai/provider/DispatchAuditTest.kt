package dev.antigravity.fluidengine.ai.provider

import dev.antigravity.fluidengine.ai.net.AiHttp
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import java.io.File
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class DispatchAuditTest {
  @Test fun parameterRejectionRecordsBothActualWireAttemptsWithoutChangingModel() = runBlocking {
    // Local HTTP fixture only. No real provider, credentials or external side effects.
    val server = ServerSocket(0)
    val bodies = java.util.Collections.synchronizedList(mutableListOf<String>())
    val worker = thread(isDaemon = true) {
      repeat(2) { index -> server.accept().use { socket ->
        val input = socket.getInputStream().bufferedReader()
        var length = 0
        while (true) {
          val line = input.readLine() ?: break
          if (line.isEmpty()) break
          if (line.startsWith("Content-Length:", true)) length = line.substringAfter(':').trim().toInt()
        }
        val payload = CharArray(length)
        var read = 0
        while (read < length) { val n = input.read(payload, read, length - read); if (n < 0) break; read += n }
        bodies += String(payload)
        val body = if (index == 0) """{"error":{"message":"unsupported reasoning_effort","type":"invalid_request_error"}}"""
          else """{"choices":[{"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}"""
        val bytes = body.toByteArray()
        val status = if (index == 0) "400 Bad Request" else "200 OK"
        socket.getOutputStream().apply {
          write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
          write(bytes); flush()
        }
      } }
    }
    val provider = object : OpenAiCompatProvider(AiHttp("fixture"), "http://127.0.0.1:${server.localPort}", "fixture") {
      override val id = ProviderId.GROQ
      override fun JsonObjectBuilder.providerFields(request: ChatRequest, stream: Boolean, dropped: Set<String>) {
        if ("reasoning_effort" !in dropped) put("reasoning_effort", "high")
      }
      override suspend fun listModels() = ModelCatalogue(emptyList(), emptyList())
      override suspend fun transcribe(audio: File, mime: String, options: TranscribeOptions): Transcript = error("unused")
    }
    val audits = mutableListOf<ProviderDispatch>()
    try {
      provider.complete(ChatRequest("selected-exact", listOf(Message.User("hi")), reasoning = ReasoningLevel.HIGH,
        beforeDispatch = { audits += it; assertEquals(bodies.size, audits.size - 1) }))
      assertEquals(2, audits.size)
      assertEquals(listOf("selected-exact", "selected-exact"), audits.map { it.model })
      assertEquals("SENT_UNVERIFIED", audits.first().support)
      assertEquals("OMITTED", audits.last().support)
      assertNull(audits.last().effective)
      assertEquals("reasoning_effort", audits.last().adaptation)
      assertFalse(Json.parseToJsonElement(bodies.last()).jsonObject.containsKey("reasoning_effort"))
    } finally { server.close(); worker.join(2000) }
  }
}
