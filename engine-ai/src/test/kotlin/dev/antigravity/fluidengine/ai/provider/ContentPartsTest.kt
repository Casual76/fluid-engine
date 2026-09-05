package dev.antigravity.fluidengine.ai.provider

import dev.antigravity.fluidengine.ai.keys.ModelCatalogStore
import dev.antigravity.fluidengine.ai.net.at
import dev.antigravity.fluidengine.ai.net.get
import dev.antigravity.fluidengine.ai.net.string
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPartsTest {

  private val pdf = ContentPart.Document(byteArrayOf(37, 80, 68, 70), "application/pdf", "circolare.pdf")
  private val jpeg = ContentPart.Image(byteArrayOf(1, 2, 3), "image/jpeg")

  @Test
  fun `un utente di solo testo resta una stringa, uno con parti diventa una lista`() {
    val plain = OpenAiCompatCodec.messages(listOf(Message.User("ciao")))
    assertEquals("ciao", plain.at(0)["content"].string())
    val rich = OpenAiCompatCodec.messages(listOf(Message.User(listOf(ContentPart.Text("guarda"), jpeg))))
    val content = rich.at(0)["content"]
    assertEquals("text", content.at(0)["type"].string())
    assertEquals("image_url", content.at(1)["type"].string())
    assertTrue(content.at(1)["image_url"]["url"].string()!!.startsWith("data:image/jpeg;base64,"))
  }

  @Test
  fun `un documento e' una parte file solo dove il provider la accetta`() {
    val user = Message.User(listOf(ContentPart.Text("leggi"), pdf))
    val groq = OpenAiCompatCodec.messages(listOf(user), fileParts = false).at(0)["content"]
    assertEquals("text", groq.at(1)["type"].string())
    assertTrue(groq.at(1)["text"].string()!!.contains("circolare.pdf"))
    val openRouter = OpenAiCompatCodec.messages(listOf(user), fileParts = true).at(0)["content"]
    assertEquals("file", openRouter.at(1)["type"].string())
    assertEquals("circolare.pdf", openRouter.at(1)["file"]["filename"].string())
    assertTrue(openRouter.at(1)["file"]["file_data"].string()!!.startsWith("data:application/pdf;base64,"))
    assertTrue(ChatRequest("m", listOf(user)).hasDocuments)
    assertFalse(ChatRequest("m", listOf(Message.User("x"))).hasDocuments)
  }

  @Test
  fun `Gemini mette immagini e documenti in inlineData`() {
    val parts = GeminiCodec.userParts(listOf(ContentPart.Text("ecco"), pdf, jpeg))
    assertEquals("ecco", parts.at(0)["text"].string())
    assertEquals("application/pdf", parts.at(1)["inlineData"]["mimeType"].string())
    assertEquals("image/jpeg", parts.at(2)["inlineData"]["mimeType"].string())
    val contents = GeminiCodec.contents(listOf(Message.User(listOf(ContentPart.Text("a"), jpeg))))
    assertEquals("user", contents.at(0)["role"].string())
    assertEquals(2, (contents.at(0)["parts"] as kotlinx.serialization.json.JsonArray).size)
  }

  @Test
  fun `le capacita' vengono dal catalogo o dal nome`() {
    assertTrue(ProviderCapabilities.guess(ProviderId.GEMINI, "gemini-3.6-flash").documents)
    assertFalse(ProviderCapabilities.guess(ProviderId.GROQ, "qwen/qwen3.8-27b").vision)
    assertTrue(ProviderCapabilities.guess(ProviderId.GROQ, "meta-llama/llama-4-maverick-17b").vision)
    assertFalse(ProviderCapabilities.guess(ProviderId.GROQ, "meta-llama/llama-4-maverick-17b").documents)
    assertTrue(ProviderCapabilities.guess(ProviderId.OPENROUTER, "qualcosa/qualsiasi").documents)
    val catalogue = ModelCatalogue(listOf(ModelInfo("x", "x", ModelKind.CHAT, supportsVision = true, supportsDocuments = false)), emptyList())
    assertEquals(ModelCapabilities(vision = true, documents = false), ProviderCapabilities.of(ProviderId.GROQ, "x", catalogue))
    assertTrue(ModelCapabilities(vision = false, documents = true).accepts(pdf))
    assertFalse(ModelCapabilities(vision = false, documents = true).accepts(jpeg))
  }

  @Test
  fun `il livello profondo si sceglie dal catalogo per documenti, visione e contesto, e non un modello piccolo`() {
    val catalogue = ModelCatalogue(
      chat = listOf(
        ModelInfo("qwen/qwen3.8-27b", "Qwen", ModelKind.CHAT, contextWindow = 131_072, supportsReasoning = true),
        ModelInfo("llama-3.1-8b-instant", "Llama 8b", ModelKind.CHAT, contextWindow = 131_072),
        ModelInfo("meta-llama/llama-4-maverick-17b", "Maverick", ModelKind.CHAT, contextWindow = 131_072, supportsVision = true),
      ),
      stt = emptyList(),
    )
    assertEquals("meta-llama/llama-4-maverick-17b", TierDefaults.pickDeep(ProviderId.GROQ, catalogue, "qwen/qwen3.8-27b")?.id)
    val gemini = ModelCatalogue(
      chat = listOf(
        ModelInfo("gemini-3.6-flash", "Flash", ModelKind.CHAT, contextWindow = 1_048_576, supportsReasoning = true, supportsVision = true, supportsDocuments = true),
        ModelInfo("gemini-3.6-pro", "Pro", ModelKind.CHAT, contextWindow = 1_048_576, supportsReasoning = true, supportsVision = true, supportsDocuments = true),
        ModelInfo("gemini-3.5-flash-lite", "Lite", ModelKind.CHAT, contextWindow = 1_048_576, supportsVision = true, supportsDocuments = true),
      ),
      stt = emptyList(),
    )
    assertEquals("gemini-3.6-pro", TierDefaults.pickDeep(ProviderId.GEMINI, gemini, "gemini-3.6-flash")?.id)
    // Nessun candidato migliore della chat: la chat fa anche da profondo.
    val flat = ModelCatalogue(listOf(ModelInfo("a", "a", ModelKind.CHAT, supportsTools = true)), emptyList())
    assertEquals("a", TierDefaults.pickDeep(ProviderId.GROQ, flat, "a")?.id)
    assertNull(TierDefaults.pickDeep(ProviderId.GROQ, ModelCatalogue(emptyList(), emptyList()), "a"))
  }

  @Test
  fun `il catalogo su disco conserva visione e documenti`() {
    val catalogue = ModelCatalogue(
      chat = listOf(ModelInfo("m", "M", ModelKind.CHAT, supportsVision = true, supportsDocuments = true)),
      stt = emptyList(),
    )
    val decoded = ModelCatalogStore.decode(Json.parseToJsonElement(ModelCatalogStore.encode(catalogue).toString()))!!
    assertTrue(decoded.chat.single().supportsVision)
    assertTrue(decoded.chat.single().supportsDocuments)
  }

  @Test
  fun `il catalogo OpenRouter legge la visione dalle modalita' e i documenti sempre`() {
    val body = Json.parseToJsonElement(
      """{"data":[
        {"id":"google/gemini-3.6-flash","name":"Gemini","context_length":1048576,"architecture":{"input_modalities":["text","image","file"],"output_modalities":["text"]},"pricing":{"prompt":"0.00000075","completion":"0.00000375"},"supported_parameters":["tools"]},
        {"id":"x/text-only:free","name":"T","context_length":32000,"architecture":{"input_modalities":["text"],"output_modalities":["text"]},"pricing":{"prompt":"0","completion":"0"},"supported_parameters":["tools"]}
      ]}""",
    )
    val catalogue = ModelCatalog.openRouter(body)
    assertTrue(catalogue.chat("google/gemini-3.6-flash")!!.supportsVision)
    assertFalse(catalogue.chat("x/text-only:free")!!.supportsVision)
    assertTrue(catalogue.chat("x/text-only:free")!!.supportsDocuments)
  }
}
