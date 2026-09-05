package dev.antigravity.fluidengine.ai.provider

/**
 * Cosa un modello sa prendere in ingresso, quando il catalogo non lo dice. Gemini e' multimodale
 * per intero; OpenRouter legge i documenti di chiunque col suo parser e vede le immagini dove il
 * catalogo lo dichiara (qui si indovina dal nome); Groq vede solo coi modelli che lo dicono nel
 * nome e non legge documenti mai.
 */
object ProviderCapabilities {

  private val visionHints = listOf("vision", "llama-4", "scout", "maverick", "gemini", "gpt-4o", "gpt-5", "claude", "pixtral", "qwen-vl", "qwen3-vl", "vl-")

  fun guess(provider: ProviderId, model: String): ModelCapabilities {
    val lower = model.lowercase()
    return when (provider) {
      ProviderId.GEMINI -> ModelCapabilities(vision = true, documents = true)
      ProviderId.OPENROUTER -> ModelCapabilities(vision = visionHints.any { it in lower }, documents = true)
      ProviderId.GROQ -> ModelCapabilities(vision = visionHints.any { it in lower }, documents = false)
    }
  }

  /** Il modello del catalogo se c'e', l'euristica altrimenti. */
  fun of(provider: ProviderId, model: String, catalogue: ModelCatalogue?): ModelCapabilities =
    catalogue?.chat(model)?.capabilities ?: guess(provider, model)
}

/**
 * Quale modello del catalogo fa da livello profondo, se l'utente non ne ha scelto uno: quello che
 * legge documenti e vede immagini, col contesto piu' grande, e che ragiona; sui gratuiti prima
 * dove i gratuiti esistono. Null se il catalogo e' vuoto: allora il livello profondo e' la chat.
 */
object TierDefaults {

  fun pickDeep(provider: ProviderId, catalogue: ModelCatalogue, chatModel: String?): ModelInfo? {
    val candidates = catalogue.chat.filter { it.supportsTools }
    if (candidates.isEmpty()) return null
    val scored = candidates.map { it to score(provider, it) }
    val best = scored.maxWithOrNull(
      compareBy<Pair<ModelInfo, Int>> { it.second }
        .thenByDescending { it.first.contextWindow ?: 0 }
        .thenBy { it.first.pricePromptPerM ?: 0.0 },
    )?.first ?: return null
    // Se il migliore e' peggio della chat su tutto, tanto vale la chat: un livello in piu' che non
    // aggiunge niente e' solo un modello in piu' da spiegare.
    val chat = chatModel?.let { catalogue.chat(it) }
    if (chat != null && score(provider, best) <= score(provider, chat)) return chat
    return best
  }

  fun score(provider: ProviderId, model: ModelInfo): Int {
    var score = 0
    val capabilities = if (model.supportsVision || model.supportsDocuments) model.capabilities else ProviderCapabilities.guess(provider, model.id)
    if (capabilities.documents) score += 4
    if (capabilities.vision) score += 3
    if (model.supportsReasoning) score += 2
    val context = model.contextWindow ?: 0
    if (context >= 128_000) score += 2 else if (context >= 64_000) score += 1
    if (context >= 500_000) score += 1
    val lower = model.id.lowercase()
    if ("pro" in lower && "preview" !in lower) score += 2
    if ("lite" in lower || "mini" in lower || "nano" in lower || "8b" in lower || "instant" in lower) score -= 3
    if ("preview" in lower || "exp" in lower) score -= 1
    if (provider == ProviderId.OPENROUTER) {
      if (model.free) score += 3 else if ((model.pricePromptPerM ?: 0.0) > 5.0) score -= 2
    }
    return score
  }
}
