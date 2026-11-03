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
 * Le tre guide con i testi italiani (e, dalla 3.0.4, [english]). Sono dati, non logica: `FluidProviderGuides` si prova solo
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

  /** Le stesse tre guide in inglese: gli indirizzi sono quelli di sempre, cambiano le frasi. */
  val english: Map<ProviderId, FluidProviderGuide> = mapOf(
    ProviderId.GROQ to FluidProviderGuide(
      tagline = "Free and fast, with per-minute limits. The first one to try.",
      consoleUrl = "https://console.groq.com/keys",
      consoleHost = "console.groq.com",
      placeholder = "gsk_…",
      steps = listOf(
        "Open console.groq.com and sign in (or create a free account).",
        "In the menu choose API Keys and press Create API Key.",
        "Give the key a name and copy it: it starts with gsk_.",
        "Paste it below and press Verify.",
      ),
    ),
    ProviderId.GEMINI to FluidProviderGuide(
      tagline = "Free with daily limits; it sees images and reads PDFs on its own.",
      consoleUrl = "https://aistudio.google.com/apikey",
      consoleHost = "aistudio.google.com",
      placeholder = "AIza…",
      steps = listOf(
        "Open aistudio.google.com with your Google account.",
        "Press Get API key, then Create API key.",
        "Copy the key: it starts with AIza.",
        "Paste it below and press Verify.",
      ),
    ),
    ProviderId.OPENROUTER to FluidProviderGuide(
      tagline = "Many models, some free; the best ones are paid.",
      consoleUrl = "https://openrouter.ai/settings/keys",
      consoleHost = "openrouter.ai",
      placeholder = "sk-or-…",
      steps = listOf(
        "Open openrouter.ai and sign in.",
        "Go to Settings > Keys and press Create Key.",
        "Copy the key: it starts with sk-or-.",
        "Paste it below and press Verify. Free models have a daily cap; the others need credits.",
      ),
      extraUrl = "https://openrouter.ai/settings/credits",
      extraLabel = "Credits",
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
  /** Corto apposta (3.0.4): il campo e' stretto, accanto al tasto «Verifica», e una frase lunga andava a capo. */
  val keyPlaceholderKeep: String = "Incolla per sostituirla",
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
  /** Come [orderDetail] per l'app senza l'interruttore della riserva automatica (`failoverEnabled == null`): 3.0.4. */
  val orderDetailNoFailover: String = "Il primo risponde; gli altri fanno da riserva se è al limite o non risponde.",
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
  companion object {
    /**
     * Le stesse parole in inglese (3.0.4), con le guide in inglese; il default dell'engine resta l'italiano. Le
     * frasi del dominio (`keyDisclosure`, `privacyNote`) le scrive l'app: la nota sulla privacy qui e' quella generica.
     */
    fun english(): FluidAiSettingsStrings = FluidAiSettingsStrings(
      enabledTitle = "Assistant",
      enabledNeedsKey = "At least one verified key is needed below.",
      enabledOn = "On: it answers your questions.",
      enabledOff = "Off: no request leaves the phone.",
      keysTitle = "Keys",
      keysDetail = "They stay on the phone, encrypted. They only travel to the service they belong to.",
      keyVerifiedOn = "Key verified on %1\$s",
      keySavedUnverified = "Key saved, still to be verified",
      keyHowTo = "How to",
      keyClose = "Close",
      keyOpenConsole = "Open %1\$s",
      keyPlaceholderNew = "Paste the %1\$s key",
      keyPlaceholderKeep = "Paste to replace it",
      keyVerify = "Verify",
      keyVerifying = "Verifying…",
      keyRemove = "Remove",
      keyValid = "Key valid: %1\$d chat models, %2\$d transcription models",
      keyChosenFree = " · chosen %1\$s (free)",
      keyChosenPaid = " · chosen %1\$s (paid: no free model with tools)",
      keyInvalid = "Key rejected by the service.",
      keyFailed = "Could not verify: %1\$s",
      keyNoAnswer = "no answer",
      keyWrongProvider = "This looks like a %1\$s key, not a %2\$s one.",
      keyUnexpectedShape = "%1\$s keys usually start with %2\$s: check that you copied all of it.",
      keyTooShort = "That is too short to be a key: check that you copied all of it.",
      guides = FluidProviderGuides.english,
      orderTitle = "Service order",
      orderDetail = "The first one answers; the others are the fallback if automatic fallback is on.",
      orderDetailNoFailover = "The first one answers; the others are the fallback if it is at its limit or not answering.",
      orderChat = "Chat",
      orderStt = "Voice transcription",
      orderFirst = "First to answer",
      orderBackup = "Fallback, if the previous one is at its limit or not answering",
      orderMoveUp = "Move up",
      orderMoveDown = "Move down",
      orderDrag = "Press and hold, then drag to reorder",
      modelsTitle = "Models",
      modelsDetail = "Three tiers per service: the router picks the tools, the chat answers, the deep one sees images and reads documents.",
      modelsDetailChatOnly = "The model that answers, per service.",
      tierRouter = "Router",
      tierChat = "Chat",
      tierDeep = "Deep",
      tierRouterHint = "Small and fast: picks the tools",
      tierChatHint = "Answers and uses the tools",
      tierDeepHint = "Images, documents, long texts",
      modelSameAsChat = " · same as chat: pick a more capable one and the assistant will switch to it on hard questions",
      modelAsChat = "same as chat",
      modelTranscription = "Transcription",
      modelsRefresh = "Refresh the catalogue",
      modelsRefreshedAt = "Read on %1\$s",
      modelsNeverRead = "Never read from the network",
      pickerSearch = "Search a model",
      filterTools = "Tools",
      filterReasoning = "Reasons",
      filterFree = "Free",
      filterVision = "Sees",
      sectionRecommended = "Recommended",
      sectionFree = "Free",
      sectionAll = "All",
      pickerNone = "No model in the catalogue: verify the key to download it.",
      pickerNoMatch = "No model matches.",
      fallbackAdd = "Fallback",
      fallbackRemove = "Remove fallback",
      fallbackTag = "fallback",
      fallbacksFull = "You already have two fallbacks: remove one to pick another.",
      chosen = "Chosen",
      metaContext = "%1\$s context",
      metaPrice = "%1\$s / %2\$s per M tokens",
      metaFree = "free",
      metaReasons = "reasons",
      metaSees = "sees",
      metaPdf = "reads PDFs",
      orCredits = "Credits: %1\$s · today %2\$s",
      orUnlimited = "unlimited",
      orFreeTier = "free account",
      orPaidTier = "account with credits",
      orCreditsUnknown = "Credits: verify the key to read them",
      orChosenFree = "Chosen model: %1\$s (free)",
      orChosenPaid = "Chosen model: %1\$s (paid: no free model with tools in the catalogue)",
      orFreeToday = "Free requests today: %1\$d of %2\$d",
      orPolicyTitle = "Training on your messages",
      orPolicyAccount = "As on my account",
      orPolicyDeny = "Do not use them",
      orPolicyAccountDetail = "What you chose on your OpenRouter account applies.",
      orPolicyDenyDetail = "The assistant asks OpenRouter not to use them: models whose free endpoints log prompts will not answer.",
      prefsTitle = "Preferences",
      thinkingTitle = "Reasoning",
      thinkingDetail = "How much the model thinks before answering: higher is slower and more precise.",
      thinkingDetailAuto = "How much the model thinks before answering. In automatic mode the assistant decides: high on deep questions, low on short ones.",
      thinkingAuto = "Auto",
      thinkingLow = "Low",
      thinkingMedium = "Medium",
      thinkingHigh = "High",
      speakTitle = "Read answers aloud",
      speakDetail = "After a question asked by voice, the answer is read out loud.",
      actionsTitle = "Actions",
      actionsDetail = "Actions that matter ask for confirmation with a button.",
      failoverTitle = "Automatic fallback",
      failoverDetail = "If the chosen service is at its limit or not answering, the assistant moves on to the next one. Off, it stays on the chosen one and waits.",
      micTitle = "Microphone",
      micGranted = "Permission granted: tapping the button listens.",
      micDenied = "Needed to ask questions by voice.",
      micAllow = "Allow",
      privacyNote = "Your questions and the data the tools read to answer them go to the chosen service, with your key. Conversations stay on this phone.",
      recentTitle = "Latest requests",
      recentDetail = "In memory only: it disappears when the app closes.",
      recentSummary = "%1\$s · %2\$d steps · %3\$d s",
      recentGroups = "groups: %1\$s",
      recentTools = "tools: %1\$s",
      recentTokens = "tokens: %1\$d in, %2\$d out",
      recentWaited = "waited: %1\$d s",
      diagTitle = "Assistant",
      diagState = "State",
      diagOn = "On: %1\$s",
      diagOff = "Off",
      diagQuota = "%1\$s quota",
      diagQuotaTokens = "%1\$d tokens",
      diagQuotaRequests = "%1\$d requests",
      diagRequests = "Requests",
      diagNone = "No requests in this session.",
    )
  }

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
