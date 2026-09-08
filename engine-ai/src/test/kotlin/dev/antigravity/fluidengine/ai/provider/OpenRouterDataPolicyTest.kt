package dev.antigravity.fluidengine.ai.provider

import dev.antigravity.fluidengine.ai.net.AiHttp
import dev.antigravity.fluidengine.ai.net.get
import dev.antigravity.fluidengine.ai.net.string
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private class PolicyProbe(policy: OpenRouterDataPolicy) :
  OpenRouterProvider(AiHttp("test"), "k", "https://example.test", "Test", dataPolicy = policy) {
  fun provider(request: ChatRequest) = buildJsonObject { providerFields(request, stream = false, dropped = emptySet()) }["provider"]
}

/**
 * La politica sui dati che si manda a OpenRouter.
 *
 * Il difetto che questi test chiudono: `data_collection: "deny"` partiva a ogni richiesta, sempre,
 * senza che si potesse cambiarlo. Non rendeva niente piu' sicuro -- sovrascriveva la politica che
 * l'utente aveva gia' scelto sul suo account con la piu' stretta possibile -- e su un modello i cui
 * endpoint gratuiti registrano i prompt lasciava zero endpoint: "No endpoints found matching your
 * data policy", e il modello smetteva di rispondere da un giorno all'altro.
 */
class OpenRouterDataPolicyTest {

  private val request = ChatRequest(model = "m", messages = listOf(Message.User("ciao")))

  @Test
  fun `senza una scelta esplicita non si dice niente e vale l'account`() {
    val provider = PolicyProbe(OpenRouterDataPolicy.ACCOUNT).provider(request)
    assertNull(provider["data_collection"])
    assertEquals("price", provider["sort"].string())
  }

  @Test
  fun `chi la vuole piu' stretta chiede deny`() {
    val provider = PolicyProbe(OpenRouterDataPolicy.DENY).provider(request)
    assertEquals("deny", provider["data_collection"].string())
  }

  @Test
  fun `il default della sonda e' quello del costruttore, cioe' l'account`() {
    assertEquals(OpenRouterDataPolicy.ACCOUNT, OpenRouterDataPolicy.entries.first())
  }
}
