package dev.antigravity.fluidengine.aiui.settings

import dev.antigravity.fluidengine.ai.provider.ModelCatalogue
import dev.antigravity.fluidengine.ai.provider.ModelInfo
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.OpenRouterCatalog
import dev.antigravity.fluidengine.ai.provider.ProviderId
import java.util.Locale

// La logica delle impostazioni dell'assistente, senza una riga di Compose. Nelle quattro copie stava
// dentro i composable (un `move` dentro la lista dell'ordine, i filtri dentro la lista di
// OpenRouter, il controllo della forma di una chiave in nessun posto), cioe' dove non si prova.

/**
 * L'ordine dei provider. Solo quelli con una chiave verificata compaiono; gli altri restano in
 * coda all'ordine salvato, e ci tornano quando avranno una chiave: spostare "Groq" sopra "Gemini"
 * non deve far dimenticare dove stava OpenRouter che oggi non ha la chiave.
 */
object FluidProviderOrder {

  /** Cio' che si vede: l'ordine salvato ristretto ai provider disponibili. */
  fun visible(order: List<ProviderId>, available: Set<ProviderId>): List<ProviderId> = order.filter { it in available }

  /**
   * Sposta la voce [from] in [to] fra quelle visibili e ritorna l'ordine **intero**: le visibili
   * nel nuovo ordine, poi i provider nascosti nell'ordine in cui erano. Un indice fuori dalla lista
   * visibile, o uno spostamento da una posizione a se stessa, ritorna l'ordine com'era.
   */
  fun move(order: List<ProviderId>, available: Set<ProviderId>, from: Int, to: Int): List<ProviderId> {
    val visible = visible(order, available)
    if (from == to || from !in visible.indices || to !in visible.indices) return order
    val moved = visible.toMutableList()
    val item = moved.removeAt(from)
    moved.add(to, item)
    return moved + order.filter { it !in available }
  }

  /**
   * Dove porta un trascinamento: la riga [index] spostata di [offsetPx] verso il basso (o l'alto,
   * se negativo) di righe alte [rowHeightPx], tenuta dentro la lista di [count] voci. Una riga e'
   * "passata" quando il dito ne ha attraversata una intera, non a meta'.
   */
  fun dragTarget(index: Int, offsetPx: Float, rowHeightPx: Float, count: Int): Int {
    if (count <= 0 || rowHeightPx <= 0f) return index
    val shift = (offsetPx / rowHeightPx).toInt()
    return (index + shift).coerceIn(0, count - 1)
  }
}

/**
 * La forma di una chiave, a occhio: di che servizio sembra e se e' plausibile. **Non e' una
 * validazione** — la verita' la dice il servizio, con la verifica, e un prefisso che cambia domani
 * non deve impedire a nessuno di incollare una chiave buona. E' un avviso gentile, prima del giro di
 * rete: la chiave di Gemini incollata nel campo di Groq e' l'errore piu' comune (e dall'esito
 * "rifiutata" non si capisce perche').
 */
object FluidKeyShape {

  /** Quello che il servizio si aspetta in testa a una chiave sua. */
  fun prefix(provider: ProviderId): String = when (provider) {
    ProviderId.GROQ -> "gsk_"
    ProviderId.GEMINI -> "AIza"
    ProviderId.OPENROUTER -> "sk-or-"
  }

  /**
   * Cio' che si incolla, ripulito: spazi e a capo in testa, in coda e dentro (una chiave non ne ha:
   * una copia dal terminale a capo li porta), virgolette, e l'intestazione `Bearer ` di chi ha
   * copiato la riga di una richiesta.
   */
  fun clean(input: String): String {
    var text = input.trim()
    text = text.removePrefix("Authorization:").trim()
    text = text.removePrefix("Bearer ").trim()
    text = text.trim('"', '\'', '`')
    return text.filterNot { it.isWhitespace() }
  }

  /** Di quale servizio sembra la chiave, dal prefisso; null se non somiglia a nessuno dei tre. */
  fun detect(key: String): ProviderId? = ProviderId.entries.firstOrNull { key.startsWith(prefix(it), ignoreCase = false) }

  /** Una chiave non e' mai piu' corta di questo. */
  const val MinLength = 20

  /** Cosa dire di una chiave incollata per [provider]. */
  fun check(provider: ProviderId, raw: String): FluidKeyCheck {
    val key = clean(raw)
    if (key.isEmpty()) return FluidKeyCheck.Empty
    val actual = detect(key)
    return when {
      actual != null && actual != provider -> FluidKeyCheck.WrongProvider(actual)
      actual == provider -> if (key.length < MinLength) FluidKeyCheck.TooShort else FluidKeyCheck.Looks
      key.length < MinLength -> FluidKeyCheck.TooShort
      else -> FluidKeyCheck.UnexpectedShape(prefix(provider))
    }
  }
}

/** L'esito di [FluidKeyShape.check]: solo [WrongProvider] e [TooShort] sconsigliano di provare. */
sealed interface FluidKeyCheck {
  data object Empty : FluidKeyCheck
  data object Looks : FluidKeyCheck

  /** Comincia come la chiave di un altro servizio. */
  data class WrongProvider(val actual: ProviderId) : FluidKeyCheck

  /** Non comincia come ci si aspetta e non somiglia a nessuno: forse e' buona lo stesso. */
  data class UnexpectedShape(val expectedPrefix: String) : FluidKeyCheck

  data object TooShort : FluidKeyCheck

  /** Vero se vale la pena di avvisare, prima di spendere una chiamata. */
  val isWarning: Boolean get() = this is WrongProvider || this is UnexpectedShape || this is TooShort
}

/** Cosa si sta scegliendo nel picker: un livello di chat di un provider, o il modello di trascrizione. */
data class FluidModelPickRequest(val provider: ProviderId, val tier: ModelTier?, val stt: Boolean = false)

/** I filtri della lista di OpenRouter. Gli strumenti sono obbligatori e non si tolgono: un modello senza tool non serve a un assistente. */
data class FluidModelFilter(
  val query: String = "",
  val onlyReasoning: Boolean = false,
  val onlyFree: Boolean = false,
  val onlyVision: Boolean = false,
)

/** Le tre sezioni del picker di OpenRouter. */
enum class FluidModelSection(val limit: Int) { Recommended(24), Free(24), All(200) }

/**
 * Il catalogo di OpenRouter e' pubblico e grande (centinaia di modelli): ricerca, filtri, e tre
 * sezioni — Consigliati, Gratuiti, Tutti — con un tetto ciascuna, perche' una lista di trecento righe
 * non si legge e costa. E fino a due riserve, che OpenRouter prova da solo se il primario fallisce.
 */
object FluidModelPicker {

  /** Tutto cio' che passa il filtro, e che sa usare gli strumenti. */
  fun filtered(catalogue: ModelCatalogue, filter: FluidModelFilter): List<ModelInfo> =
    catalogue.chat.filter { it.supportsTools }.filter { matches(it, filter) }

  fun matches(model: ModelInfo, filter: FluidModelFilter): Boolean =
    (filter.query.isBlank() || model.id.contains(filter.query, ignoreCase = true) || model.displayName.contains(filter.query, ignoreCase = true)) &&
      (!filter.onlyReasoning || model.supportsReasoning) &&
      (!filter.onlyFree || model.free) &&
      (!filter.onlyVision || model.supportsVision)

  /**
   * Le sezioni non vuote, nell'ordine in cui si mostrano, ognuna col suo tetto. Un modello puo'
   * stare in piu' sezioni (un consigliato gratuito sta in due): e' voluto, ogni sezione risponde
   * a una domanda diversa.
   */
  fun sections(catalogue: ModelCatalogue, filter: FluidModelFilter): List<Pair<FluidModelSection, List<ModelInfo>>> {
    val filtered = filtered(catalogue, filter)
    val recommended = OpenRouterCatalog.recommended(catalogue).filter { it in filtered }
    return listOf(
      FluidModelSection.Recommended to recommended.take(FluidModelSection.Recommended.limit),
      FluidModelSection.Free to filtered.filter { it.free }.take(FluidModelSection.Free.limit),
      FluidModelSection.All to filtered.take(FluidModelSection.All.limit),
    ).filter { it.second.isNotEmpty() }
  }

  /** Quante riserve al massimo. */
  const val MaxFallbacks = 2

  /** Aggiunge [id] alle riserve, o lo toglie se c'e' gia'; al massimo [MaxFallbacks], le prime restano. */
  fun toggleFallback(fallbacks: List<String>, id: String): List<String> =
    if (id in fallbacks) fallbacks - id else (fallbacks + id).take(MaxFallbacks)

  /** Vero se [id] si puo' mettere o togliere: togliere si puo' sempre, mettere finche' c'e' posto. */
  fun canToggleFallback(fallbacks: List<String>, id: String): Boolean = id in fallbacks || fallbacks.size < MaxFallbacks

  /** Sceglierlo come primario lo toglie dalle riserve: non puo' essere riserva di se stesso. */
  fun afterChoosingPrimary(fallbacks: List<String>, id: String): List<String> = fallbacks - id
}

/** I numeri di un modello, scritti per una riga di lista. Puri, e provati. */
object FluidModelText {

  /** 1_000_000 -> "1M", 128_000 -> "128k", 900 -> "900"; null se il catalogo non lo dice. */
  fun contextLabel(tokens: Int?): String? = tokens?.let {
    when {
      it >= 1_000_000 -> "${it / 1_000_000}M"
      it >= 1_000 -> "${it / 1_000}k"
      else -> it.toString()
    }
  }

  /** Dollari per milione di token: "—" se sconosciuto, "0 $" se gratis, "<0,01 $" sotto il centesimo. */
  fun priceLabel(perM: Double?, locale: Locale = Locale.getDefault()): String = when {
    perM == null -> "—"
    perM == 0.0 -> "0 $"
    perM < 0.01 -> "<0,01 $"
    else -> String.format(locale, "%.2f $", perM)
  }
}

/** Quanto resta dei gratuiti di OpenRouter oggi: il tetto e' 50 per un account senza crediti, 1000 con. */
data class FluidOpenRouterQuota(val used: Int, val cap: Int) {
  companion object {
    const val FREE_ACCOUNT_CAP = 50
    const val PAID_ACCOUNT_CAP = 1000

    fun capFor(isFreeTier: Boolean?): Int = if (isFreeTier == false) PAID_ACCOUNT_CAP else FREE_ACCOUNT_CAP
  }
}
