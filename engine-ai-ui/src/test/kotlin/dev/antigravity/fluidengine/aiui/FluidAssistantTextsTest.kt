package dev.antigravity.fluidengine.aiui

import dev.antigravity.fluidengine.ai.orchestrator.AnswerChip
import dev.antigravity.fluidengine.ai.orchestrator.AskMode
import dev.antigravity.fluidengine.ai.orchestrator.AssistantState
import dev.antigravity.fluidengine.ai.orchestrator.FailureKind
import dev.antigravity.fluidengine.ai.orchestrator.PendingConfirmation
import dev.antigravity.fluidengine.ai.orchestrator.ProviderSwitch
import dev.antigravity.fluidengine.ai.orchestrator.SwitchReason
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantStrings
import dev.antigravity.fluidengine.aiui.strings.FluidAssistantTexts
import dev.antigravity.fluidengine.aiui.strings.fluidFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le frasi dell'assistente, che nelle quattro app erano quattro tabelle (due con le risorse, due
 * senza) e che nessuno aveva mai provato: qui si prova la scelta, non la poesia.
 */
class FluidAssistantTextsTest {

  private val s = FluidAssistantStrings()

  private fun working(key: String, tier: ModelTier = ModelTier.CHAT, extra: Int = 0) =
    AssistantState.Working("domanda", 1, 6, key, extra, ProviderId.GROQ, tier)

  @Test
  fun `una chiave sconosciuta all'app vale la frase generica, una conosciuta la sua`() {
    assertEquals("Penso…", FluidAssistantTexts.status("voti", ModelTier.CHAT, s))
    val withDomain = s.copy(statusFor = { key, _ -> if (key == "voti") "Guardo i voti…" else null })
    assertEquals("Guardo i voti…", FluidAssistantTexts.status("voti", ModelTier.CHAT, withDomain))
    assertEquals("Penso…", FluidAssistantTexts.status("sconosciuta", ModelTier.CHAT, withDomain))
  }

  @Test
  fun `le chiavi dell'engine non passano dalla tabella dell'app`() {
    val withDomain = s.copy(statusFor = { _, _ -> "SEMPRE" })
    assertEquals("Mi serve dell'altro…", FluidAssistantTexts.status("more_tools", ModelTier.CHAT, withDomain))
    assertEquals("Passo al modello più capace…", FluidAssistantTexts.status("deep_model", ModelTier.CHAT, withDomain))
    assertEquals("Penso…", FluidAssistantTexts.status("thinking", ModelTier.CHAT, withDomain))
  }

  @Test
  fun `sul livello profondo lo dice, tranne per il semplice penso che ha la sua variante`() {
    assertEquals("Analizzo con calma…", FluidAssistantTexts.status("thinking", ModelTier.DEEP, s))
    assertEquals("Mi serve dell'altro… (modello profondo)", FluidAssistantTexts.status("more_tools", ModelTier.DEEP, s))
  }

  @Test
  fun `le chiamate in parallelo si contano`() {
    assertEquals("Penso… (+2)", FluidAssistantTexts.statusLine(working("thinking", extra = 2), s))
    assertEquals("Penso…", FluidAssistantTexts.statusLine(working("thinking", extra = 0), s))
  }

  @Test
  fun `la riga di stato per ogni stato`() {
    assertEquals("Ti ascolto…", FluidAssistantTexts.statusLine(AssistantState.Listening(0), s))
    assertEquals("Trascrivo…", FluidAssistantTexts.statusLine(AssistantState.Transcribing, s))
    assertEquals("Capisco cosa serve…", FluidAssistantTexts.statusLine(AssistantState.Classifying("q", ProviderId.GROQ), s))
    assertEquals("Rispondo…", FluidAssistantTexts.statusLine(AssistantState.Answering("q", "x", ProviderId.GROQ), s))
    assertEquals("Non ho sentito niente", FluidAssistantTexts.statusLine(AssistantState.HeardNothing, s))
    assertEquals("Fermato", FluidAssistantTexts.statusLine(AssistantState.Cancelled("q", null), s))
    assertEquals("Groq è al limite: riprovo fra 12 s", FluidAssistantTexts.statusLine(AssistantState.WaitingRateLimit("q", ProviderId.GROQ, 12), s))
    assertEquals("Serve una conferma", FluidAssistantTexts.statusLine(AssistantState.AwaitingConfirmation("q", PendingConfirmation(1, "t", null), ProviderId.GROQ), s))
    assertNull(FluidAssistantTexts.statusLine(AssistantState.Idle, s))
    assertNull(FluidAssistantTexts.statusLine(done(), s))
  }

  private fun done(switches: List<ProviderSwitch> = emptyList(), chips: List<AnswerChip> = emptyList()) =
    AssistantState.Done("q", "risposta", chips, ProviderId.GEMINI, AskMode.TEXT, null, emptyList(), 100, switches = switches)

  @Test
  fun `un cambio di servizio dice perche' solo se lo sa`() {
    val without = AssistantState.SwitchingProvider("q", ProviderId.GROQ, ProviderId.GEMINI)
    assertEquals("Passo a Gemini…", FluidAssistantTexts.statusLine(without, s))
    val with = AssistantState.SwitchingProvider("q", ProviderId.GROQ, ProviderId.GEMINI, SwitchReason.RATE_LIMITED)
    assertEquals("Groq era al limite: passo a Gemini…", FluidAssistantTexts.statusLine(with, s))
  }

  @Test
  fun `il limite di richieste nomina il servizio, ricorda la riserva e conta i secondi`() {
    val withSeconds = FluidAssistantTexts.failure(FailureKind.RATE_LIMITED, s, retryAfterSec = 40, provider = ProviderId.GROQ)
    assertTrue(withSeconds, withSeconds.startsWith("Groq è al limite: riprova fra 40 s"))
    assertTrue(withSeconds.contains("riserva automatica"))
    val without = FluidAssistantTexts.failure(FailureKind.RATE_LIMITED, s)
    assertTrue(without, without.startsWith("Il servizio è al limite di richieste"))
  }

  @Test
  fun `se la riserva li ha provati tutti non si consiglia piu' la riserva`() {
    val text = FluidAssistantTexts.failure(
      FailureKind.RATE_LIMITED,
      s,
      retryAfterSec = 30,
      tried = listOf(ProviderId.GROQ, ProviderId.GEMINI, ProviderId.OPENROUTER),
    )
    assertEquals("Sono al limite Groq, Gemini e OpenRouter: riprova fra 30 s.", text)
    assertFalse(text.contains("riserva"))
    val soon = FluidAssistantTexts.failure(FailureKind.RATE_LIMITED, s, tried = listOf(ProviderId.GROQ, ProviderId.GEMINI))
    assertEquals("Sono al limite Groq e Gemini: riprova fra poco.", soon)
  }

  @Test
  fun `ogni fallimento ha una frase e un titolo, e nessuno e' vuoto`() {
    for (kind in FailureKind.entries) {
      val frase = FluidAssistantTexts.failure(kind, s, provider = ProviderId.GROQ)
      val titolo = FluidAssistantTexts.failureTitle(kind, s)
      assertTrue("$kind senza frase", frase.isNotBlank())
      assertTrue("$kind senza titolo", titolo.isNotBlank())
      // Un segnaposto non riempito in una frase mostrata all'utente e' il difetto che questo test cerca.
      assertFalse("$kind: $frase", frase.contains("%"))
    }
  }

  @Test
  fun `un fallimento nomina il servizio che lo porta, o quello passato`() {
    val failed = AssistantState.Failed("q", FailureKind.PROVIDER, null, null, null, provider = ProviderId.OPENROUTER)
    assertTrue(FluidAssistantTexts.statusLine(failed, s)!!.startsWith("OpenRouter ha risposto con un errore"))
    val nameless = AssistantState.Failed("q", FailureKind.PROVIDER, null, null, null)
    assertTrue(FluidAssistantTexts.statusLine(nameless, s, provider = ProviderId.GEMINI)!!.startsWith("Gemini ha risposto con un errore"))
  }

  @Test
  fun `i servizi provati, in ordine e senza doppioni`() {
    val switches = listOf(
      ProviderSwitch(ProviderId.GROQ, ProviderId.GEMINI, SwitchReason.RATE_LIMITED),
      ProviderSwitch(ProviderId.GEMINI, ProviderId.OPENROUTER, SwitchReason.TIMEOUT),
    )
    assertEquals(listOf(ProviderId.GROQ, ProviderId.GEMINI, ProviderId.OPENROUTER), FluidAssistantTexts.tried(switches))
    assertEquals(emptyList<ProviderId>(), FluidAssistantTexts.tried(emptyList()))
  }

  @Test
  fun `l'elenco dei nomi si legge come una frase`() {
    assertEquals("Groq", FluidAssistantTexts.names(listOf(ProviderId.GROQ), s))
    assertEquals("Groq e Gemini", FluidAssistantTexts.names(listOf(ProviderId.GROQ, ProviderId.GEMINI), s))
    assertEquals("Groq, Gemini e OpenRouter", FluidAssistantTexts.names(ProviderId.entries, s))
  }

  @Test
  fun `la riga di chi ha risposto dice chi e perche', e niente senza cambi`() {
    assertNull(FluidAssistantTexts.switchLine(emptyList(), s))
    val line = FluidAssistantTexts.switchLine(
      listOf(ProviderSwitch(ProviderId.GROQ, ProviderId.GEMINI, SwitchReason.RATE_LIMITED)),
      s,
    )
    assertEquals("Ha risposto Gemini: Groq era al limite", line)
    val twice = FluidAssistantTexts.switchLine(
      listOf(
        ProviderSwitch(ProviderId.GROQ, ProviderId.GEMINI, SwitchReason.RATE_LIMITED),
        ProviderSwitch(ProviderId.GEMINI, ProviderId.OPENROUTER, SwitchReason.TIMEOUT),
      ),
      s,
    )
    assertEquals("Ha risposto OpenRouter: Groq era al limite, Gemini non rispondeva", twice)
  }

  @Test
  fun `la pillola ridotta dice risposta pronta a risposta finita, altrimenti lo stato`() {
    assertEquals("Risposta pronta", FluidAssistantTexts.collapsed(done(), s))
    assertEquals("Rispondo…", FluidAssistantTexts.collapsed(AssistantState.Answering("q", "x", ProviderId.GROQ), s))
    assertEquals("Assistente", FluidAssistantTexts.collapsed(AssistantState.Idle, s))
  }

  @Test
  fun `il corpo e la domanda di uno stato`() {
    assertEquals("pezzo", FluidAssistantTexts.bodyText(AssistantState.Answering("q", "pezzo", ProviderId.GROQ)))
    assertEquals("risposta", FluidAssistantTexts.bodyText(done()))
    assertEquals("parziale", FluidAssistantTexts.bodyText(AssistantState.Failed("q", FailureKind.NETWORK, null, null, "parziale")))
    assertNull(FluidAssistantTexts.bodyText(AssistantState.Idle))
    assertEquals("q", FluidAssistantTexts.question(done()))
    assertNull(FluidAssistantTexts.question(AssistantState.Listening(10)))
  }

  @Test
  fun `la card espansa a una risposta, un errore, una conferma`() {
    assertTrue(FluidAssistantTexts.wantsExpanded(done()))
    assertTrue(FluidAssistantTexts.wantsExpanded(AssistantState.Failed("q", FailureKind.UNKNOWN, null, null, null)))
    assertFalse(FluidAssistantTexts.wantsExpanded(AssistantState.Transcribing))
    assertFalse(FluidAssistantTexts.wantsExpanded(AssistantState.Idle))
  }

  @Test
  fun `una traduzione con un segnaposto in meno non fa cadere l'app`() {
    assertEquals("Passo a …", fluidFormat("Passo a …", "Gemini"))
    // E uno in piu': la frase esce com'e', non un'eccezione mentre si mostra un errore.
    assertEquals("%1\$s e %2\$s", fluidFormat("%1\$s e %2\$s", "uno"))
    assertNotNull(s.reasonText(SwitchReason.PARSE))
  }

  @Test
  fun `ogni motivo di cambio ha una frase`() {
    for (reason in SwitchReason.entries) assertTrue("$reason", s.reasonText(reason).isNotBlank())
  }
}
