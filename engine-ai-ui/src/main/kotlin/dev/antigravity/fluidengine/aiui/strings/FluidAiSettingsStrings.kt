package dev.antigravity.fluidengine.aiui.strings

import androidx.compose.runtime.Immutable
import dev.antigravity.fluidengine.ai.keys.ThinkingLevel
import dev.antigravity.fluidengine.ai.provider.ModelTier
import dev.antigravity.fluidengine.ai.provider.ProviderId

/**
 * La guida di un servizio: dove si prende la chiave, in quattro passi. Senza dominio — sono le
 * stesse tre guide in ClasseViva, PampAI e FluidWeather (in quest'ultima nelle risorse), e a FT
 * mancavano i quattro passi. Un'app tradotta le sostituisce in [FluidAiSettingsStrings.guides].
 *
 * @param tagline una riga che dice a che servizio serve e cosa costa.
 * @param consoleUrl dove si crea la chiave; [consoleHost] e' lo stesso, scritto per un tasto.
 * @param placeholder l'inizio di una chiave, per il segnaposto del campo.
 * @param steps i passi, nell'ordine; l'ultimo e' sempre "incollala qui sotto".
 */
@Immutable
data class FluidProviderGuide(
  val tagline: String,
  val consoleUrl: String,
  val consoleHost: String,
  val placeholder: String,
  val steps: List<String>,
  /** Un secondo indirizzo, se serve (i crediti di OpenRouter), e la parola per il suo tasto. */
  val extraUrl: String? = null,
  val extraLabel: String? = null,
)

/**
 * Le tre guide con i testi italiani. Sono dati, non logica: `FluidProviderGuides` si prova solo
 * perche' un indirizzo storto o un passo mancante non si vede finche' qualcuno non li segue.
 */
object FluidProviderGuides {
  val italian: Map<ProviderId, FluidProviderGuide> = mapOf(
    ProviderId.GROQ to FluidProviderGuide(
      tagline = "Gratis e veloce, con limiti al minuto. Il primo da provare.",
      consoleUrl = "https://console.groq.com/keys",
      consoleHost = "console.groq.com",
      placeholder = "gsk_…",
      steps = listOf(
        "Apri console.groq.com e accedi (o crea un account gratuito).",
        "Nel menu scegli API Keys e premi Create API Key.",
        "Dai un nome alla chiave e copiala: comincia con gsk_.",
        "Incollala qui sotto e premi Verifica.",
      ),
    ),
    ProviderId.GEMINI to FluidProviderGuide(
      tagline = "Gratis con limiti giornalieri; vede le immagini e legge i PDF da solo.",
      consoleUrl = "https://aistudio.google.com/apikey",
      consoleHost = "aistudio.google.com",
      placeholder = "AIza…",
      steps = listOf(
        "Apri aistudio.google.com con il tuo account Google.",
        "Premi Get API key, poi Create API key.",
        "Copia la chiave: comincia con AIza.",
        "Incollala qui sotto e premi Verifica.",
      ),
    ),
    ProviderId.OPENROUTER to FluidProviderGuide(
      tagline = "Tanti modelli, alcuni gratuiti; a pagamento i migliori.",
      consoleUrl = "https://openrouter.ai/settings/keys",
      consoleHost = "openrouter.ai",
      placeholder = "sk-or-…",
      steps = listOf(
        "Apri openrouter.ai e accedi.",
        "Vai in Settings > Keys e premi Create Key.",
        "Copia la chiave: comincia con sk-or-.",
        "Incollala qui sotto e premi Verifica. I modelli gratuiti hanno un tetto giornaliero; per gli altri servono crediti.",
      ),
      extraUrl = "https://openrouter.ai/settings/credits",
      extraLabel = "Crediti",
    ),
  )
}

/**
 * Le parole delle impostazioni dell'assistente: tutte, con i default italiani, come
 * `FluidAppearanceStrings`. Un'app in una lingua sola non scrive niente, tranne le frasi del suo
 * dominio (il nome dell'assistente, cosa parte verso il servizio); un'app tradotta le passa tutte.
 *
 * I modelli con segnaposto sono nel formato di `String.format` (`%1$s`, `%2$d`).
 */
@Immutable
data class FluidAiSettingsStrings(
  // --- l'interruttore ---------------------------------------------------------------------------
  val enabledTitle: String = "Assistente",
  val enabledNeedsKey: String = "Serve almeno una chiave verificata qui sotto.",
  val enabledOn: String = "Acceso: risponde alle tue domande.",
  val enabledOff: String = "Spento: nessuna richiesta parte.",

  // --- le chiavi --------------------------------------------------------------------------------
  val keysTitle: String = "Chiavi",
  val keysDetail: String = "Restano sul telefono, cifrate. Viaggiano solo verso il servizio a cui appartengono.",
  val keyVerifiedOn: String = "Chiave verificata il %1\$s",
  val keySavedUnverified: String = "Chiave salvata, ancora da verificare",
  val keyHowTo: String = "Come si fa",
  val keyClose: String = "Chiudi",
  val keyOpenConsole: String = "Apri %1\$s",
  val keyPlaceholderNew: String = "Incolla la chiave %1\$s",
  val keyPlaceholderKeep: String = "Nuova chiave (vuoto = tieni quella salvata)",
  val keyVerify: String = "Verifica",
  val keyVerifying: String = "Verifico…",
  val keyRemove: String = "Rimuovi",
  val keyValid: String = "Chiave valida: %1\$d modelli di chat, %2\$d di trascrizione",
  val keyChosenFree: String = " · scelto %1\$s (gratuito)",
  val keyChosenPaid: String = " · scelto %1\$s (a pagamento: nessun gratuito con strumenti)",
  val keyInvalid: String = "Chiave non valida: il servizio l'ha rifiutata.",
  val keyFailed: String = "Verifica non riuscita: %1\$s",
  val keyNoAnswer: String = "nessuna risposta",
  /** Chi ha incollato la chiave di un altro servizio: quale sembra, e per quale era il campo. */
  val keyWrongProvider: String = "Sembra una chiave di %1\$s, non di %2\$s.",
  val keyUnexpectedShape: String = "Le chiavi di %1\$s di solito cominciano con %2\$s: controlla di averla copiata tutta.",
  val keyTooShort: String = "È troppo corta per essere una chiave: controlla di averla copiata tutta.",
  /** Cosa parte verso il servizio e cosa resta sul telefono, per servizio; null non mostra niente. E' dominio, lo scrive l'app. */
  val keyDisclosure: (ProviderId) -> String? = { null },
  /** Le guide dei tre servizi: dove si prende la chiave. */
  val guides: Map<ProviderId, FluidProviderGuide> = FluidProviderGuides.italian,

  // --- l'ordine ---------------------------------------------------------------------------------
  val orderTitle: String = "Ordine dei servizi",
  val orderDetail: String = "Il primo risponde; gli altri fanno da riserva se la riserva automatica è accesa.",
  val orderChat: String = "Chat",
  val orderStt: String = "Trascrizione della voce",
  val orderFirst: String = "Primo a rispondere",
  val orderBackup: String = "Riserva, se il precedente è al limite o non risponde",
  val orderMoveUp: String = "Sposta su",
  val orderMoveDown: String = "Sposta giù",
  val orderDrag: String = "Tieni premuto e trascina per riordinare",

  // --- i modelli --------------------------------------------------------------------------------
  val modelsTitle: String = "Modelli",
  val modelsDetail: String = "Tre livelli per servizio: il router sceglie gli strumenti, la chat risponde, il profondo vede immagini e legge documenti.",
  val modelsDetailChatOnly: String = "Il modello che risponde, per servizio.",
  val tierRouter: String = "Router",
  val tierChat: String = "Chat",
  val tierDeep: String = "Profondo",
  val tierRouterHint: String = "Piccolo e veloce: sceglie gli strumenti",
  val tierChatHint: String = "Risponde e usa gli strumenti",
  val tierDeepHint: String = "Immagini, documenti, testi lunghi",
  val modelSameAsChat: String = " · uguale alla chat: scegline uno più capace e l'assistente ci passerà da solo sulle domande difficili",
  val modelAsChat: String = "come la chat",
  val modelTranscription: String = "Trascrizione",
  val modelsRefresh: String = "Aggiorna il catalogo",
  val modelsRefreshedAt: String = "Letto il %1\$s",
  val modelsNeverRead: String = "Mai letto dalla rete",

  // --- il picker --------------------------------------------------------------------------------
  val pickerSearch: String = "Cerca un modello",
  val filterTools: String = "Strumenti",
  val filterReasoning: String = "Ragiona",
  val filterFree: String = "Gratuiti",
  val filterVision: String = "Vede",
  val sectionRecommended: String = "Consigliati",
  val sectionFree: String = "Gratuiti",
  val sectionAll: String = "Tutti",
  val pickerNone: String = "Nessun modello nel catalogo: verifica la chiave per scaricarlo.",
  val pickerNoMatch: String = "Nessun modello corrisponde.",
  val fallbackAdd: String = "Riserva",
  val fallbackRemove: String = "Togli riserva",
  val fallbackTag: String = "riserva",
  val fallbacksFull: String = "Hai già due riserve: togline una per sceglierne un'altra.",
  val chosen: String = "Scelto",
  val metaContext: String = "contesto %1\$s",
  val metaPrice: String = "%1\$s / %2\$s per M token",
  val metaFree: String = "gratuito",
  val metaReasons: String = "ragiona",
  val metaSees: String = "vede",
  val metaPdf: String = "legge PDF",

  // --- OpenRouter -------------------------------------------------------------------------------
  val orCredits: String = "Crediti: %1\$s · oggi %2\$s",
  val orUnlimited: String = "illimitati",
  val orFreeTier: String = "account gratuito",
  val orPaidTier: String = "account con crediti",
  val orCreditsUnknown: String = "Crediti: verifica la chiave per leggerli",
  val orChosenFree: String = "Modello scelto: %1\$s (gratuito)",
  val orChosenPaid: String = "Modello scelto: %1\$s (a pagamento: nessun gratuito con strumenti nel catalogo)",
  val orFreeToday: String = "Gratuiti di oggi: %1\$d di %2\$d",
  val orPolicyTitle: String = "Addestramento sui tuoi messaggi",
  val orPolicyAccount: String = "Come sul mio account",
  val orPolicyDeny: String = "Non usarli",
  val orPolicyAccountDetail: String = "Vale quello che hai scelto sul tuo account OpenRouter.",
  val orPolicyDenyDetail: String = "L'assistente chiede a OpenRouter di non usarli: i modelli i cui endpoint gratuiti registrano i prompt non rispondono.",

  // --- le preferenze ----------------------------------------------------------------------------
  val prefsTitle: String = "Preferenze",
  val thinkingTitle: String = "Ragionamento",
  val thinkingDetail: String = "Quanto il modello pensa prima di rispondere: più alto, più lento e più preciso.",
  val thinkingDetailAuto: String = "Quanto il modello pensa prima di rispondere. In automatico lo decide l'assistente: alto sulle domande profonde, basso su quelle secche.",
  val thinkingAuto: String = "Auto",
  val thinkingLow: String = "Basso",
  val thinkingMedium: String = "Medio",
  val thinkingHigh: String = "Alto",
  val speakTitle: String = "Leggi le risposte",
  val speakDetail: String = "Dopo una domanda a voce, la risposta viene letta ad alta voce.",
  val actionsTitle: String = "Azioni",
  val actionsDetail: String = "Le azioni che contano chiedono conferma con un tasto.",
  val failoverTitle: String = "Riserva automatica",
  val failoverDetail: String = "Se il servizio scelto è al limite o non risponde, l'assistente passa al successivo. Spenta, resta su quello scelto e aspetta.",
  val micTitle: String = "Microfono",
  val micGranted: String = "Permesso concesso: il tocco sul tasto ascolta.",
  val micDenied: String = "Serve per fare domande a voce.",
  val micAllow: String = "Consenti",

  /** Cosa parte verso il servizio, in generale. Le app con un dominio la riscrivono dicendo cosa (voti, posizione, mappa…). */
  val privacyNote: String = "Le tue domande e i dati che gli strumenti leggono per rispondere partono verso il servizio scelto, con la tua chiave. Le conversazioni restano su questo telefono.",

  // --- le ultime richieste ----------------------------------------------------------------------
  val recentTitle: String = "Ultime richieste",
  val recentDetail: String = "Solo in memoria: sparisce chiudendo l'app.",
  /** Chi ha risposto, quanti passi, quanto ci ha messo. */
  val recentSummary: String = "%1\$s · %2\$d passi · %3\$d s",
  val recentGroups: String = "gruppi: %1\$s",
  val recentTools: String = "strumenti: %1\$s",
  val recentTokens: String = "token: %1\$d in, %2\$d out",
  val recentWaited: String = "attesa: %1\$d s",

  // --- la diagnostica ---------------------------------------------------------------------------
  val diagTitle: String = "Assistente",
  val diagState: String = "Stato",
  val diagOn: String = "Acceso: %1\$s",
  val diagOff: String = "Spento",
  val diagQuota: String = "Quota %1\$s",
  val diagQuotaTokens: String = "%1\$d token",
  val diagQuotaRequests: String = "%1\$d richieste",
  val diagRequests: String = "Richieste",
  val diagNone: String = "Nessuna richiesta in questa sessione.",
) {
  fun tierLabel(tier: ModelTier): String = when (tier) {
    ModelTier.ROUTER -> tierRouter
    ModelTier.CHAT -> tierChat
    ModelTier.DEEP -> tierDeep
  }

  fun tierHint(tier: ModelTier): String = when (tier) {
    ModelTier.ROUTER -> tierRouterHint
    ModelTier.CHAT -> tierChatHint
    ModelTier.DEEP -> tierDeepHint
  }

  fun thinkingLabel(level: ThinkingLevel): String = when (level) {
    ThinkingLevel.LOW -> thinkingLow
    ThinkingLevel.MEDIUM -> thinkingMedium
    ThinkingLevel.HIGH -> thinkingHigh
  }

  fun guide(provider: ProviderId): FluidProviderGuide = guides[provider] ?: FluidProviderGuides.italian.getValue(provider)
}
