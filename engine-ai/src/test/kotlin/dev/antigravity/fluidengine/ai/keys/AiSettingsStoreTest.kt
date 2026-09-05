package dev.antigravity.fluidengine.ai.keys

import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSettingsStoreTest {

  @Test
  fun `l'ordine salvato accoda i provider che non nomina`() {
    assertEquals(
      listOf(ProviderId.GEMINI, ProviderId.GROQ, ProviderId.OPENROUTER),
      AiSettingsStore.parseOrder("gemini,groq"),
    )
    assertEquals(ProviderId.defaultOrder, AiSettingsStore.parseOrder(null))
    assertEquals(
      listOf(ProviderId.OPENROUTER, ProviderId.GROQ, ProviderId.GEMINI),
      AiSettingsStore.parseOrder("openrouter, sconosciuto ,openrouter"),
    )
  }

  @Test
  fun `i default dei modelli seguono le decisioni di prodotto, il profondo viene dal catalogo`() {
    val settings = AiSettings()
    assertEquals("qwen/qwen3.8-27b", settings.chatModel(ProviderId.GROQ))
    assertEquals("gemini-3.6-flash", settings.chatModel(ProviderId.GEMINI))
    assertNull(settings.chatModel(ProviderId.OPENROUTER))
    assertEquals("whisper-large-v3", settings.sttModel(ProviderId.GROQ))
    assertEquals("gemini-3.5-transcribe", settings.sttModel(ProviderId.GEMINI))
    assertEquals("openai/whisper-large-v3", settings.sttModel(ProviderId.OPENROUTER))
    assertEquals("llama-3.1-8b-instant", settings.classifierModel(ProviderId.GROQ))
    assertNull(settings.classifierModel(ProviderId.OPENROUTER))
    assertNull(settings.deepModel(ProviderId.GROQ))
    val chosen = settings.copy(chatModels = mapOf(ProviderId.OPENROUTER to "x/y:free"), deepModels = mapOf(ProviderId.GROQ to "grande"))
    assertEquals("x/y:free", chosen.chatModel(ProviderId.OPENROUTER))
    assertEquals("grande", chosen.model(ProviderId.GROQ, ModelTier.DEEP))
    assertEquals("llama-3.1-8b-instant", chosen.model(ProviderId.GROQ, ModelTier.ROUTER))
    assertFalse(settings.consentAccepted)
    assertTrue(settings.copy(consentAcceptedAtMillis = 1L).consentAccepted)
  }
}
