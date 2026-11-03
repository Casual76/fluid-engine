package dev.antigravity.fluidengine.aiui

import dev.antigravity.fluidengine.ai.keys.KeyState
import dev.antigravity.fluidengine.ai.orchestrator.FailureKind
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.aiui.chat.fluidComposerShowsVoiceLine
import dev.antigravity.fluidengine.aiui.settings.FluidAiSettingsOptions
import dev.antigravity.fluidengine.aiui.settings.FluidAiSettingsState
import dev.antigravity.fluidengine.aiui.settings.fluidAiOrderDetail
import dev.antigravity.fluidengine.aiui.settings.fluidAiShowsModels
import dev.antigravity.fluidengine.aiui.strings.FluidAiSettingsStrings
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantStrings
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantTexts
import dev.antigravity.fluidengine.aiui.strings.FluidChatStrings
import dev.antigravity.fluidengine.aiui.strings.FluidProviderGuide
import dev.antigravity.fluidengine.aiui.strings.FluidProviderGuides
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le versioni inglesi delle parole dell'assistente (3.0.4): nessun campo resta uguale all'italiano
 * (salvo le parole uguali in tutte e due le lingue), e ogni frase conserva i suoi segnaposto.
 *
 * Legge i campi per riflessione, quindi un campo aggiunto domani senza traduzione fa cadere il test da
 * solo. Le funzioni (`statusFor`, `keyDisclosure`) sono il dominio dell'app e non si confrontano.
 */
class FluidEnglishStringsTest {

  private fun fields(set: Any): Map<String, Any?> =
    set.javaClass.declaredFields
      .filter { !Modifier.isStatic(it.modifiers) }
      .associate { field ->
        field.isAccessible = true
        field.name to field.get(set)
      }

  /** Le frasi di un insieme: i campi stringa, e le guide dei servizi una per una. */
  private fun flatten(set: Any): Map<String, String> = buildMap {
    for ((name, value) in fields(set)) {
      when (value) {
        is String -> put(name, value)
        is Boolean, is Function<*> -> Unit
        is Map<*, *> -> @Suppress("UNCHECKED_CAST") (value as Map<ProviderId, FluidProviderGuide>).forEach { (provider, guide) ->
          put("$name.$provider.tagline", guide.tagline)
          guide.steps.forEachIndexed { index, step -> put("$name.$provider.step$index", step) }
          guide.extraLabel?.let { put("$name.$provider.extraLabel", it) }
        }
        else -> error("$name: tipo non previsto dal test (${value?.javaClass})")
      }
    }
  }

  private val placeholder = Regex("%\\d\\$[sd]")

  private fun assertTranslated(label: String, italian: Any, english: Any, neutral: Set<String>) {
    val ita = flatten(italian)
    val en = flatten(english)
    assertEquals("$label: gli stessi campi nelle due lingue", ita.keys, en.keys)
    val same = ita.filter { (name, value) -> en.getValue(name) == value && name !in neutral }.keys
    assertTrue("$label: campi inglesi rimasti uguali all'italiano: $same", same.isEmpty())
    val gone = neutral.filter { name -> name !in ita }
    assertTrue("$label: eccezioni che non esistono piu': $gone", gone.isEmpty())
    val blank = en.filterValues { value -> value.isBlank() }.keys
    assertTrue("$label: campi inglesi vuoti: $blank", blank.isEmpty())
    val skewed = ita.keys.filter { name ->
      placeholder.findAll(ita.getValue(name)).map { m -> m.value }.sorted().toList() !=
        placeholder.findAll(en.getValue(name)).map { m -> m.value }.sorted().toList()
    }
    assertTrue("$label: segnaposto diversi dall'italiano in: $skewed", skewed.isEmpty())
  }

  @Test
  fun `FluidAssistantStrings in inglese non lascia campi italiani`() {
    assertTranslated("FluidAssistantStrings", FluidAssistantStrings(), FluidAssistantStrings.english(), neutral = setOf("parallel"))
  }

  @Test
  fun `FluidAiSettingsStrings in inglese non lascia campi italiani`() {
    assertTranslated(
      "FluidAiSettingsStrings",
      FluidAiSettingsStrings(),
      FluidAiSettingsStrings.english(),
      neutral = setOf("orderChat", "tierChat", "tierRouter", "thinkingAuto"),
    )
  }

  @Test
  fun `FluidChatStrings in inglese non lascia campi italiani`() {
    assertTranslated("FluidChatStrings", FluidChatStrings(), FluidChatStrings.english(), neutral = emptySet())
  }

  @Test
  fun `le guide inglesi hanno gli stessi indirizzi e gli stessi passi di quelle italiane`() {
    for (provider in ProviderId.entries) {
      val ita = FluidProviderGuides.italian.getValue(provider)
      val en = FluidProviderGuides.english.getValue(provider)
      assertEquals(ita.consoleUrl, en.consoleUrl)
      assertEquals(ita.consoleHost, en.consoleHost)
      assertEquals(ita.placeholder, en.placeholder)
      assertEquals(ita.steps.size, en.steps.size)
      assertEquals(ita.extraUrl, en.extraUrl)
      assertEquals(ita.extraLabel == null, en.extraLabel == null)
    }
  }

  @Test
  fun `l'italiano resta il default`() {
    assertEquals("Assistente", FluidAssistantStrings().title)
    assertEquals("Chiavi", FluidAiSettingsStrings().keysTitle)
    assertEquals("Copia", FluidChatStrings().copy)
    assertTrue(FluidAssistantStrings().failoverSwitch)
  }

  // ---- le varianti per l'app senza riserva automatica ------------------------------------------------------------------

  @Test
  fun `il limite di richieste consiglia la riserva solo se l'app ce l'ha`() {
    val with = FluidAssistantStrings()
    val without = with.copy(failoverSwitch = false)
    for (retry in listOf(40, null)) {
      val a = FluidAssistantTexts.failure(FailureKind.RATE_LIMITED, with, retryAfterSec = retry, provider = ProviderId.GROQ)
      val b = FluidAssistantTexts.failure(FailureKind.RATE_LIMITED, without, retryAfterSec = retry, provider = ProviderId.GROQ)
      assertTrue("con l'interruttore: $a", a.contains("riserva automatica"))
      assertFalse("senza l'interruttore: $b", b.contains("riserva"))
      assertTrue(b.contains(ProviderId.GROQ.label))
    }
    assertTrue(FluidAssistantTexts.failure(FailureKind.RATE_LIMITED, without, retryAfterSec = 40, provider = ProviderId.GROQ).contains("40"))
  }

  @Test
  fun `la variante inglese del limite senza interruttore non nomina la riserva`() {
    val en = FluidAssistantStrings.english().copy(failoverSwitch = false)
    assertFalse(FluidAssistantTexts.failure(FailureKind.RATE_LIMITED, en, retryAfterSec = 5, provider = ProviderId.GEMINI).contains("fallback"))
    assertFalse(FluidAssistantTexts.failure(FailureKind.RATE_LIMITED, en, provider = ProviderId.GEMINI).contains("fallback"))
  }

  @Test
  fun `la frase dell'ordine dei servizi cambia senza l'interruttore`() {
    val s = FluidAiSettingsStrings()
    assertEquals(s.orderDetail, fluidAiOrderDetail(s, failoverSwitch = true))
    assertEquals(s.orderDetailNoFailover, fluidAiOrderDetail(s, failoverSwitch = false))
    assertFalse(s.orderDetailNoFailover.contains("riserva automatica"))
    val en = FluidAiSettingsStrings.english()
    assertFalse(en.orderDetailNoFailover.contains("automatic fallback"))
  }

  // ---- le opzioni -----------------------------------------------------------------------------------------------------

  @Test
  fun `i modelli si possono nascondere anche con le chiavi verificate`() {
    val verified = FluidAiSettingsState(
      keys = mapOf(ProviderId.GROQ to KeyState(present = true, verifiedAtMillis = 1L)),
    )
    assertTrue(fluidAiShowsModels(verified, FluidAiSettingsOptions()))
    assertFalse(fluidAiShowsModels(verified, FluidAiSettingsOptions(models = false)))
    assertFalse(fluidAiShowsModels(FluidAiSettingsState(), FluidAiSettingsOptions()))
  }

  @Test
  fun `la riga della voce resta anche mentre trascrive, se chi chiama lo dice`() {
    assertTrue(fluidComposerShowsVoiceLine(voiceActive = true, hasVoiceLine = true))
    assertFalse(fluidComposerShowsVoiceLine(voiceActive = false, hasVoiceLine = true))
    assertFalse(fluidComposerShowsVoiceLine(voiceActive = true, hasVoiceLine = false))
  }

  @Test
  fun `il segnaposto della chiave salvata sta in una riga del campo stretto`() {
    // Il campo sta accanto al tasto «Verifica»: un segnaposto lungo andava a capo e il campo cresceva.
    assertTrue(FluidAiSettingsStrings().keyPlaceholderKeep.length <= 28)
    assertTrue(FluidAiSettingsStrings.english().keyPlaceholderKeep.length <= 28)
  }
}
