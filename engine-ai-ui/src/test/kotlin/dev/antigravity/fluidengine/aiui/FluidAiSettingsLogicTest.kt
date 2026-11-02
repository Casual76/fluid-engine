package dev.antigravity.fluidengine.aiui

import dev.antigravity.fluidengine.ai.keys.AiSettings
import dev.antigravity.fluidengine.ai.keys.KeyState
import dev.antigravity.fluidengine.ai.net.RateLimitInfo
import dev.antigravity.fluidengine.ai.orchestrator.AiRequestLog
import dev.antigravity.fluidengine.ai.orchestrator.AskMode
import dev.antigravity.fluidengine.ai.orchestrator.ModelUse
import dev.antigravity.fluidengine.ai.orchestrator.ToolTrace
import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ModelInfo
import dev.antigravity.fluidengine.ai.provider.ModelKind
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.OpenRouterKeyInfo
import dev.antigravity.fluidengine.ai.provider.ProviderId
import dev.antigravity.fluidengine.ai.provider.Usage
import dev.antigravity.fluidengine.aiui.settings.FluidAiSettingsState
import dev.antigravity.fluidengine.aiui.settings.FluidKeyCheck
import dev.antigravity.fluidengine.aiui.settings.FluidKeyShape
import dev.antigravity.fluidengine.aiui.settings.FluidModelFilter
import dev.antigravity.fluidengine.aiui.settings.FluidModelPicker
import dev.antigravity.fluidengine.aiui.settings.FluidModelSection
import dev.antigravity.fluidengine.aiui.settings.FluidModelText
import dev.antigravity.fluidengine.aiui.settings.FluidOpenRouterQuota
import dev.antigravity.fluidengine.aiui.settings.FluidProviderOrder
import dev.antigravity.fluidengine.aiui.settings.fluidOpenRouterLines
import dev.antigravity.fluidengine.aiui.settings.fluidQuotaParts
import dev.antigravity.fluidengine.aiui.settings.fluidRequestDetails
import dev.antigravity.fluidengine.aiui.settings.fluidSummary
import dev.antigravity.fluidengine.aiui.settings.keyHint
import dev.antigravity.fluidengine.aiui.strings.FluidAiSettingsStrings
import dev.antigravity.fluidengine.aiui.strings.FluidProviderGuides
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le impostazioni dell'assistente: l'ordine dei servizi, la forma di una chiave, il picker di
 * OpenRouter, le righe di dettaglio. Tutto cio' che nelle quattro copie stava dentro un composable.
 */
class FluidAiSettingsLogicTest {

  private val s = FluidAiSettingsStrings()
  private val all = ProviderId.entries.toSet()

  // ---- l'ordine dei provider ---------------------------------------------------------------------

  @Test
  fun `l'ordine mostra solo i provider con una chiave verificata`() {
    val order = listOf(ProviderId.GROQ, ProviderId.GEMINI, ProviderId.OPENROUTER)
    assertEquals(listOf(ProviderId.GROQ, ProviderId.OPENROUTER), FluidProviderOrder.visible(order, setOf(ProviderId.GROQ, ProviderId.OPENROUTER)))
    assertEquals(emptyList<ProviderId>(), FluidProviderOrder.visible(order, emptySet()))
  }

  @Test
  fun `spostare una voce ritorna l'ordine intero`() {
    val order = listOf(ProviderId.GROQ, ProviderId.GEMINI, ProviderId.OPENROUTER)
    assertEquals(
      listOf(ProviderId.GEMINI, ProviderId.GROQ, ProviderId.OPENROUTER),
      FluidProviderOrder.move(order, all, from = 1, to = 0),
    )
    assertEquals(
      listOf(ProviderId.GEMINI, ProviderId.OPENROUTER, ProviderId.GROQ),
      FluidProviderOrder.move(order, all, from = 0, to = 2),
    )
  }

  @Test
  fun `un provider senza chiave resta in coda e non si perde`() {
    // Gemini non ha la chiave: spostare Groq sotto OpenRouter non deve far dimenticare dove stava lui.
    val order = listOf(ProviderId.GROQ, ProviderId.GEMINI, ProviderId.OPENROUTER)
    val available = setOf(ProviderId.GROQ, ProviderId.OPENROUTER)
    val moved = FluidProviderOrder.move(order, available, from = 0, to = 1)
    assertEquals(listOf(ProviderId.OPENROUTER, ProviderId.GROQ, ProviderId.GEMINI), moved)
    assertEquals(order.toSet(), moved.toSet())
  }

  @Test
  fun `uno spostamento impossibile lascia l'ordine com'e'`() {
    val order = listOf(ProviderId.GROQ, ProviderId.GEMINI, ProviderId.OPENROUTER)
    assertEquals(order, FluidProviderOrder.move(order, all, 1, 1))
    assertEquals(order, FluidProviderOrder.move(order, all, 0, -1))
    assertEquals(order, FluidProviderOrder.move(order, all, 0, 3))
    assertEquals(order, FluidProviderOrder.move(order, all, 5, 0))
    // Una voce nascosta non e' una posizione della lista visibile.
    assertEquals(order, FluidProviderOrder.move(order, setOf(ProviderId.GROQ), 0, 1))
  }

  @Test
  fun `il trascinamento passa una riga quando il dito ne ha attraversata una intera`() {
    // Righe da 100 px, in una lista da tre.
    assertEquals(0, FluidProviderOrder.dragTarget(0, 99f, 100f, 3))
    assertEquals(1, FluidProviderOrder.dragTarget(0, 100f, 100f, 3))
    assertEquals(2, FluidProviderOrder.dragTarget(0, 250f, 100f, 3))
    assertEquals(1, FluidProviderOrder.dragTarget(2, -100f, 100f, 3))
    // Oltre la lista non si va, e senza misura non si muove niente.
    assertEquals(2, FluidProviderOrder.dragTarget(1, 900f, 100f, 3))
    assertEquals(0, FluidProviderOrder.dragTarget(1, -900f, 100f, 3))
    assertEquals(1, FluidProviderOrder.dragTarget(1, 500f, 0f, 3))
    assertEquals(1, FluidProviderOrder.dragTarget(1, 500f, 100f, 0))
  }

  // ---- la forma di una chiave --------------------------------------------------------------------

  @Test
  fun `ogni servizio ha il suo prefisso e si riconosce da quello`() {
    assertEquals("gsk_", FluidKeyShape.prefix(ProviderId.GROQ))
    assertEquals("AIza", FluidKeyShape.prefix(ProviderId.GEMINI))
    assertEquals("sk-or-", FluidKeyShape.prefix(ProviderId.OPENROUTER))
    assertEquals(ProviderId.GROQ, FluidKeyShape.detect("gsk_abcdefghijklmnopqrstuvwxyz"))
    assertEquals(ProviderId.GEMINI, FluidKeyShape.detect("AIzaSyAbcdefghijklmnopqrstuvwxyz"))
    assertEquals(ProviderId.OPENROUTER, FluidKeyShape.detect("sk-or-v1-abcdefghijklmnop"))
    assertNull(FluidKeyShape.detect("qualcosaltro"))
    // Il prefisso e' maiuscolo/minuscolo: "aiza" non e' una chiave di Gemini.
    assertNull(FluidKeyShape.detect("aizaSyAbcdefghijklmnopqrstuvwxyz"))
  }

  @Test
  fun `una chiave incollata si ripulisce di spazi, a capo, virgolette e intestazione`() {
    assertEquals("gsk_abc123", FluidKeyShape.clean("  gsk_abc123 \n"))
    assertEquals("gsk_abc123", FluidKeyShape.clean("\"gsk_abc123\""))
    assertEquals("gsk_abc123", FluidKeyShape.clean("'gsk_abc123'"))
    assertEquals("gsk_abc123", FluidKeyShape.clean("Bearer gsk_abc123"))
    assertEquals("gsk_abc123", FluidKeyShape.clean("Authorization: Bearer gsk_abc123"))
    // Una chiave non ha spazi dentro: una copia dal terminale a capo li porta.
    assertEquals("gsk_abcdef", FluidKeyShape.clean("gsk_abc\ndef"))
    assertEquals("gsk_abcdef", FluidKeyShape.clean("gsk_abc def"))
    assertEquals("", FluidKeyShape.clean("   "))
  }

  @Test
  fun `la chiave di un altro servizio e' l'errore piu' comune e si dice`() {
    val gemini = "AIzaSyAbcdefghijklmnopqrstuvwxyz012345"
    assertEquals(FluidKeyCheck.WrongProvider(ProviderId.GEMINI), FluidKeyShape.check(ProviderId.GROQ, gemini))
    assertEquals(FluidKeyCheck.Looks, FluidKeyShape.check(ProviderId.GEMINI, gemini))
    assertEquals(FluidKeyCheck.Empty, FluidKeyShape.check(ProviderId.GROQ, "  "))
  }

  @Test
  fun `una chiave troppo corta o fuori forma avverte ma non vieta`() {
    assertEquals(FluidKeyCheck.TooShort, FluidKeyShape.check(ProviderId.GROQ, "gsk_ab"))
    assertEquals(FluidKeyCheck.TooShort, FluidKeyShape.check(ProviderId.GROQ, "abc"))
    val odd = FluidKeyShape.check(ProviderId.GROQ, "xyzxyzxyzxyzxyzxyzxyzxyz")
    assertEquals(FluidKeyCheck.UnexpectedShape("gsk_"), odd)
    assertTrue(odd.isWarning)
    assertFalse(FluidKeyCheck.Looks.isWarning)
    assertFalse(FluidKeyCheck.Empty.isWarning)
  }

  @Test
  fun `gli avvisi sono frasi vere, e niente per una chiave a posto`() {
    assertEquals("Sembra una chiave di Gemini, non di Groq.", keyHint(FluidKeyCheck.WrongProvider(ProviderId.GEMINI), ProviderId.GROQ, s))
    assertTrue(keyHint(FluidKeyCheck.UnexpectedShape("gsk_"), ProviderId.GROQ, s)!!.contains("gsk_"))
    assertTrue(keyHint(FluidKeyCheck.TooShort, ProviderId.GROQ, s)!!.contains("corta"))
    assertNull(keyHint(FluidKeyCheck.Looks, ProviderId.GROQ, s))
    assertNull(keyHint(FluidKeyCheck.Empty, ProviderId.GROQ, s))
  }

  @Test
  fun `le guide dei tre servizi sono complete`() {
    for (provider in ProviderId.entries) {
      val guide = FluidProviderGuides.italian.getValue(provider)
      assertTrue("$provider: indirizzo", guide.consoleUrl.startsWith("https://"))
      assertTrue("$provider: host nell'indirizzo", guide.consoleUrl.contains(guide.consoleHost))
      assertEquals("$provider: quattro passi", 4, guide.steps.size)
      assertTrue("$provider: segnaposto nel prefisso", guide.placeholder.startsWith(FluidKeyShape.prefix(provider)))
      assertTrue("$provider: l'ultimo passo e' incollare", guide.steps.last().contains("Verifica"))
      assertTrue(guide.tagline.isNotBlank())
    }
    // Solo OpenRouter ha un secondo indirizzo (i crediti), e ha anche la parola per il suo tasto.
    assertTrue(FluidProviderGuides.italian.getValue(ProviderId.OPENROUTER).extraUrl!!.startsWith("https://"))
    assertEquals("Crediti", FluidProviderGuides.italian.getValue(ProviderId.OPENROUTER).extraLabel)
    assertNull(FluidProviderGuides.italian.getValue(ProviderId.GROQ).extraUrl)
  }

  @Test
  fun `una guida mancante in una traduzione ricade sull'italiano, non sul vuoto`() {
    val partial = s.copy(guides = emptyMap())
    assertTrue(partial.guide(ProviderId.GEMINI).steps.isNotEmpty())
  }

  // ---- il picker ---------------------------------------------------------------------------------

  private fun model(
    id: String,
    name: String = id,
    tools: Boolean = true,
    reasoning: Boolean = false,
    vision: Boolean = false,
    free: Boolean = false,
    context: Int? = 128_000,
    price: Double? = null,
  ) = ModelInfo(
    id = id,
    displayName = name,
    kind = ModelKind.CHAT,
    contextWindow = context,
    supportsTools = tools,
    supportsReasoning = reasoning,
    supportsVision = vision,
    free = free,
    pricePromptPerM = price,
    priceCompletionPerM = price,
  )

  private val catalogue = ModelCatalogue(
    chat = listOf(
      model("qwen/qwen3-big:free", "Qwen3 Big (free)", reasoning = true, free = true),
      model("google/gemini-flash", "Gemini Flash", vision = true, price = 0.3, reasoning = true),
      model("meta/llama-vision:free", "Llama Vision", vision = true, free = true),
      model("gemma/no-tools", "Gemma", tools = false, free = true),
      model("acme/plain", "Plain"),
    ),
    stt = emptyList(),
  )

  @Test
  fun `un modello senza strumenti non si propone mai`() {
    val ids = FluidModelPicker.filtered(catalogue, FluidModelFilter()).map { it.id }
    assertFalse("gemma/no-tools" in ids)
    assertEquals(4, ids.size)
  }

  @Test
  fun `la ricerca guarda l'id e il nome, senza badare alle maiuscole`() {
    assertEquals(listOf("google/gemini-flash"), FluidModelPicker.filtered(catalogue, FluidModelFilter(query = "GEMINI")).map { it.id })
    assertEquals(listOf("meta/llama-vision:free"), FluidModelPicker.filtered(catalogue, FluidModelFilter(query = "llama")).map { it.id })
    assertEquals(emptyList<String>(), FluidModelPicker.filtered(catalogue, FluidModelFilter(query = "zzz")).map { it.id })
  }

  @Test
  fun `i filtri si sommano`() {
    val free = FluidModelPicker.filtered(catalogue, FluidModelFilter(onlyFree = true)).map { it.id }
    assertEquals(setOf("qwen/qwen3-big:free", "meta/llama-vision:free"), free.toSet())
    val freeVision = FluidModelPicker.filtered(catalogue, FluidModelFilter(onlyFree = true, onlyVision = true)).map { it.id }
    assertEquals(listOf("meta/llama-vision:free"), freeVision)
    val reasoning = FluidModelPicker.filtered(catalogue, FluidModelFilter(onlyReasoning = true)).map { it.id }
    assertEquals(setOf("qwen/qwen3-big:free", "google/gemini-flash"), reasoning.toSet())
  }

  @Test
  fun `le sezioni vuote non si mostrano, e un modello puo' stare in piu' di una`() {
    val sections = FluidModelPicker.sections(catalogue, FluidModelFilter())
    val kinds = sections.map { it.first }
    assertTrue(FluidModelSection.All in kinds)
    assertTrue(FluidModelSection.Free in kinds)
    assertEquals(kinds, kinds.distinct())
    val all = sections.first { it.first == FluidModelSection.All }.second
    val free = sections.first { it.first == FluidModelSection.Free }.second
    assertTrue(free.all { it in all })
    // Nessun filtro corrisponde: nessuna sezione.
    assertTrue(FluidModelPicker.sections(catalogue, FluidModelFilter(query = "zzz")).isEmpty())
    // Senza gratuiti la sezione dei gratuiti non c'e'.
    val paid = ModelCatalogue(listOf(model("a/b", price = 1.0)), emptyList())
    assertFalse(FluidModelPicker.sections(paid, FluidModelFilter()).any { it.first == FluidModelSection.Free })
  }

  @Test
  fun `ogni sezione ha il suo tetto`() {
    val many = ModelCatalogue((1..300).map { model("p/m$it", free = true, reasoning = true) }, emptyList())
    val sections = FluidModelPicker.sections(many, FluidModelFilter()).toMap()
    assertEquals(24, sections.getValue(FluidModelSection.Free).size)
    assertEquals(200, sections.getValue(FluidModelSection.All).size)
    assertTrue(sections.getValue(FluidModelSection.Recommended).size <= 24)
  }

  @Test
  fun `le riserve sono al massimo due, si tolgono sempre, e il primario non puo' esserlo`() {
    assertEquals(listOf("a"), FluidModelPicker.toggleFallback(emptyList(), "a"))
    assertEquals(listOf("a", "b"), FluidModelPicker.toggleFallback(listOf("a"), "b"))
    // Piene: una terza non entra (le prime due restano).
    assertEquals(listOf("a", "b"), FluidModelPicker.toggleFallback(listOf("a", "b"), "c"))
    assertFalse(FluidModelPicker.canToggleFallback(listOf("a", "b"), "c"))
    // Ma togliere si puo' sempre.
    assertEquals(listOf("b"), FluidModelPicker.toggleFallback(listOf("a", "b"), "a"))
    assertTrue(FluidModelPicker.canToggleFallback(listOf("a", "b"), "a"))
    assertEquals(listOf("b"), FluidModelPicker.afterChoosingPrimary(listOf("a", "b"), "a"))
    assertEquals(listOf("a", "b"), FluidModelPicker.afterChoosingPrimary(listOf("a", "b"), "z"))
  }

  @Test
  fun `il contesto e il prezzo si leggono in una riga`() {
    assertEquals("1M", FluidModelText.contextLabel(1_000_000))
    assertEquals("2M", FluidModelText.contextLabel(2_500_000))
    assertEquals("128k", FluidModelText.contextLabel(128_000))
    assertEquals("900", FluidModelText.contextLabel(900))
    assertNull(FluidModelText.contextLabel(null))
    assertEquals("—", FluidModelText.priceLabel(null))
    assertEquals("0 $", FluidModelText.priceLabel(0.0))
    assertEquals("<0,01 $", FluidModelText.priceLabel(0.004))
    assertEquals("0.30 $", FluidModelText.priceLabel(0.3, Locale.US))
    assertEquals("0,30 $", FluidModelText.priceLabel(0.3, Locale.ITALY))
  }

  @Test
  fun `il nome di un modello per una riga delle impostazioni`() {
    assertEquals("come la chat", (null as ModelCatalogue?).fluidSummary(null, s))
    assertEquals("Qwen3 Big (free) · gratuito", catalogue.fluidSummary("qwen/qwen3-big:free", s))
    assertEquals("Gemini Flash", catalogue.fluidSummary("google/gemini-flash", s))
    // Un modello che il catalogo non conosce (ancora non scaricato) si mostra com'e'.
    assertEquals("ignoto/x", catalogue.fluidSummary("ignoto/x", s))
    assertEquals("ignoto/x", (null as ModelCatalogue?).fluidSummary("ignoto/x", s))
  }

  // ---- OpenRouter --------------------------------------------------------------------------------

  private val info = OpenRouterKeyInfo(label = null, limitUsd = 10.0, limitRemainingUsd = 7.5, usageUsd = 2.5, usageDailyUsd = 0.1234, isFreeTier = false)

  @Test
  fun `i crediti, il modello scelto e se e' gratuito`() {
    val settings = AiSettings(chatModels = mapOf(ProviderId.OPENROUTER to "qwen/qwen3-big:free"))
    val lines = fluidOpenRouterLines(settings, catalogue, info, null, s, Locale.US)
    assertEquals("Crediti: 7.50 $ · oggi 0.123 $ · account con crediti", lines[0])
    assertEquals("Modello scelto: qwen/qwen3-big:free (gratuito)", lines[1])
    assertEquals(2, lines.size)
  }

  @Test
  fun `un modello a pagamento lo dice`() {
    val settings = AiSettings(chatModels = mapOf(ProviderId.OPENROUTER to "google/gemini-flash"))
    val lines = fluidOpenRouterLines(settings, catalogue, info, null, s, Locale.US)
    assertTrue(lines[1], lines[1].contains("a pagamento"))
  }

  @Test
  fun `senza informazioni sulla chiave dice di verificarla, e senza limite i crediti sono illimitati`() {
    val settings = AiSettings(chatModels = mapOf(ProviderId.OPENROUTER to "acme/plain"))
    assertEquals("Crediti: verifica la chiave per leggerli", fluidOpenRouterLines(settings, catalogue, null, null, s)[0])
    val unlimited = info.copy(limitUsd = null, limitRemainingUsd = null, isFreeTier = true)
    assertEquals("Crediti: illimitati · oggi 0.123 $ · account gratuito", fluidOpenRouterLines(settings, catalogue, unlimited, null, s, Locale.US)[0])
  }

  @Test
  fun `i gratuiti di oggi solo se il modello scelto e' gratuito, contro il tetto dell'account`() {
    val free = AiSettings(chatModels = mapOf(ProviderId.OPENROUTER to "qwen/qwen3-big:free"))
    val paid = AiSettings(chatModels = mapOf(ProviderId.OPENROUTER to "google/gemini-flash"))
    val quota = FluidOpenRouterQuota(used = 12, cap = FluidOpenRouterQuota.capFor(isFreeTier = true))
    assertEquals(50, quota.cap)
    assertEquals("Gratuiti di oggi: 12 di 50", fluidOpenRouterLines(free, catalogue, info, quota, s, Locale.US).last())
    assertFalse(fluidOpenRouterLines(paid, catalogue, info, quota, s, Locale.US).any { it.contains("Gratuiti di oggi") })
    assertEquals(1000, FluidOpenRouterQuota.capFor(isFreeTier = false))
    // Senza sapere se l'account e' gratuito si presume di si': il tetto piu' basso e' quello prudente.
    assertEquals(50, FluidOpenRouterQuota.capFor(null))
  }

  // ---- lo stato ----------------------------------------------------------------------------------

  @Test
  fun `l'assistente e' acceso solo se l'utente l'ha acceso, c'e' una chiave buona e nessuno lo ha sospeso`() {
    val keys = mapOf(ProviderId.GROQ to KeyState(present = true, verifiedAtMillis = 1L))
    assertTrue(FluidAiSettingsState(AiSettings(enabled = true), keys).enabled)
    assertFalse(FluidAiSettingsState(AiSettings(enabled = false), keys).enabled)
    assertFalse("senza chiave verificata non risponde nessuno", FluidAiSettingsState(AiSettings(enabled = true), mapOf(ProviderId.GROQ to KeyState(true, null))).enabled)
    assertFalse(FluidAiSettingsState(AiSettings(enabled = true), keys, locked = dev.antigravity.fluidengine.aiui.settings.FluidAiLock("sospeso")).enabled)
    assertEquals(setOf(ProviderId.GROQ), FluidAiSettingsState(AiSettings(), keys).verified)
  }

  // ---- le ultime richieste -----------------------------------------------------------------------

  private fun log(
    modelsUsed: List<ModelUse> = emptyList(),
    switchedTo: List<ProviderId> = emptyList(),
    tools: List<ToolTrace> = emptyList(),
    usage: Usage? = null,
    waited: Int = 0,
    error: String? = null,
    groups: List<String> = emptyList(),
  ) = AiRequestLog(
    startedAtMillis = 0L,
    question = "che tempo fa?",
    mode = AskMode.TEXT,
    provider = ProviderId.GROQ,
    model = "llama",
    switchedTo = switchedTo,
    groups = groups,
    classifierUsed = true,
    tools = tools,
    steps = 3,
    usage = usage,
    durationMillis = 4_500,
    outcome = "ok",
    error = error,
    rateLimit = null,
    waitedSeconds = waited,
    models = mapOf(ModelTier.CHAT to "llama"),
    modelsUsed = modelsUsed,
  )

  @Test
  fun `una domanda del registro dice chi ha risposto in ordine, passi e secondi`() {
    val chain = listOf(
      ModelUse(ProviderId.GEMINI, ModelTier.CHAT, "flash"),
      ModelUse(ProviderId.GEMINI, ModelTier.DEEP, "flash"),
      ModelUse(ProviderId.OPENROUTER, ModelTier.CHAT, "minimax"),
    )
    assertEquals("Gemini flash → OpenRouter minimax · 3 passi · 4 s", fluidRequestDetails(log(modelsUsed = chain), s))
  }

  @Test
  fun `senza la catena resta la vecchia forma, servizio e modelli`() {
    assertEquals("Groq → Gemini · llama · 3 passi · 4 s", fluidRequestDetails(log(switchedTo = listOf(ProviderId.GEMINI)), s))
    assertEquals("Groq · llama · 3 passi · 4 s", fluidRequestDetails(log(), s))
  }

  @Test
  fun `gruppi, strumenti coi tempi, token con costo, attesa ed errore su righe a parte`() {
    val details = fluidRequestDetails(
      log(
        groups = listOf("meteo", "app"),
        tools = listOf(ToolTrace("meteo_ora", 120, true, 80), ToolTrace("apri", 30, false, 10)),
        usage = Usage(promptTokens = 900, completionTokens = 120, totalTokens = 1020, costUsd = 0.00123),
        waited = 7,
        error = "429",
      ),
      s,
      Locale.US,
    )
    val lines = details.lines()
    assertEquals("Groq · llama · 3 passi · 4 s", lines[0])
    assertEquals("gruppi: meteo, app", lines[1])
    assertEquals("strumenti: meteo_ora 120 ms, apri 30 ms ✕", lines[2])
    assertEquals("token: 900 in, 120 out · 0.0012 $", lines[3])
    assertEquals("attesa: 7 s", lines[4])
    assertEquals("429", lines[5])
  }

  @Test
  fun `le quote che gli header hanno detto`() {
    assertEquals(listOf("5000 token", "30 richieste"), fluidQuotaParts(RateLimitInfo(remainingTokens = 5000, remainingRequests = 30), s))
    assertEquals(emptyList<String>(), fluidQuotaParts(RateLimitInfo.EMPTY, s))
  }

  @Test
  fun `ogni stringa delle impostazioni con un segnaposto lo ha davvero`() {
    // Un modello di formato rovinato (un `%` perso) si vede qui prima che in una pagina.
    assertEquals("Chiave valida: 3 modelli di chat, 1 di trascrizione", dev.antigravity.fluidengine.aiui.strings.fluidFormat(s.keyValid, 3, 1))
    assertEquals("Letto il oggi", dev.antigravity.fluidengine.aiui.strings.fluidFormat(s.modelsRefreshedAt, "oggi"))
    assertEquals("Apri console.groq.com", dev.antigravity.fluidengine.aiui.strings.fluidFormat(s.keyOpenConsole, "console.groq.com"))
  }
}
