# engine-ai-ui: le parole (stringhe, tabelle di frasi, errori e limiti di richieste)

Un componente non puo' leggere le risorse da se': le legge l'app, prima, e le passa. `engine-ai-ui` ha quindi **tre oggetti di parole**, tutti con i default
**italiani** (come `FluidAppearanceStrings`), piu' una tabella **pura** che sceglie la frase giusta per uno stato:

| oggetto | di chi sono le parole | dove si passa |
|---|---|---|
| `FluidAssistantStrings` | overlay, card, dock, errori, foglio «Dettagli», riga della voce del composer | `strings` / `assistantStrings` di overlay, card, dock, bolle, composer-voce |
| `FluidAiSettingsStrings` | la sezione impostazioni: chiavi, ordine, modelli, preferenze, OpenRouter, diagnostica (+ le guide dei servizi) | `strings` di `fluidAiSettingsSection` e di tutti i pezzi di `settings/` |
| `FluidChatStrings` | il kit della chat: composer, bolle, coda, azioni, esiti | `strings` di `FluidComposer`, `FluidUserBubble`, `FluidAssistantBubble`, `FluidQueuedBubble`, `fluidOutcomeLines` |
| `FluidAssistantTexts` | **nessuna parola propria**: sceglie da `FluidAssistantStrings` | si chiama direttamente (`statusLine`, `failure`, …) |

Overlay in [ai-ui-overlay.md](ai-ui-overlay.md), chat in [ai-ui-chat.md](ai-ui-chat.md), impostazioni in [ai-ui-impostazioni.md](ai-ui-impostazioni.md).
Le firme complete dei tre `data class` (centinaia di campi) sono in [../api/ai-ui.md](../api/ai-ui.md): qui c'e' **come usarli** e **dove si rompono**.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un'app in italiano, con parole di serie | niente: i default |
| un'app in inglese | `FluidAssistantStrings.english()`, `FluidAiSettingsStrings.english()`, `FluidChatStrings.english()` |
| le frasi dei **miei** gruppi di strumenti («Guardo gli elementi…») | `FluidAssistantStrings(statusFor = { key, tier -> … })` o `.english().copy(statusFor = …)` |
| il nome dell'assistente al posto di «Assistente» | `title`, `askPlaceholder` (`FluidAssistantStrings`), `enabledTitle`, `diagTitle` (`FluidAiSettingsStrings`), `assistantLabel` (`FluidChatStrings`) |
| dire cosa parte verso il servizio (dominio) | `FluidAiSettingsStrings(keyDisclosure = { provider -> … }, privacyNote = "…")` |
| un'app **senza** l'interruttore della riserva automatica | `FluidAssistantStrings(failoverSwitch = false)` |
| la guida di una chiave in un'altra lingua | `FluidAiSettingsStrings(guides = mapOf(ProviderId.GROQ to FluidProviderGuide(…), …))` |
| una frase di stato/errore fuori dalle mie schermate (notifica, log) | `FluidAssistantTexts.statusLine`, `failure`, `failureTitle` |

**Non cosi'**: non lasciare **un solo** campo al default in un'app tradotta: e' una frase italiana in mezzo a una pagina inglese, e nessuno se ne accorge finche' non gira il
telefono. Passa l'oggetto `english()` per intero e sovrascrivi con `copy`.

## Come si traduce (e cosa fa cadere la build)

```kotlin
val assistantStrings = FluidAssistantStrings.english().copy(
  title = "Helper",                                   // il nome dell'assistente
  statusFor = { key, _ -> when (key) { "items" -> "Looking at the items…"; else -> null } },   // il dominio
)
val settingsStrings = FluidAiSettingsStrings.english().copy(
  keyDisclosure = { provider -> "Your questions go to ${provider.label}." },
  privacyNote = "…",
)
val chatStrings = FluidChatStrings.english()
```

- **Un test per insieme, per riflessione** (`FluidEnglishStringsTest`): fa cadere la build se un campo inglese resta **uguale all'italiano**, resta **vuoto**, o **perde un
  segnaposto** rispetto all'italiano. Un campo aggiunto domani senza la sua traduzione non passa. Eccezioni dichiarate (uguali apposta): `parallel`;
  `orderChat`, `tierChat`, `tierRouter`, `thinkingAuto`; nessuna per `FluidChatStrings`. I campi `Boolean` e le lambda (`failoverSwitch`, `statusFor`, `keyDisclosure`) non si confrontano.
- Le guide: `FluidProviderGuides.english` ha **gli stessi indirizzi, host, segnaposto e numero di passi** dell'italiano (un test lo verifica); `FluidAiSettingsStrings.guide(provider)` di un servizio
  mancante in una traduzione **ricade sull'italiano**, non sul vuoto.
- **`english()` non tocca `failoverSwitch`** (resta `true`) **ne' `statusFor`** (resta «niente»): per l'app inglese senza riserva automatica e' `FluidAssistantStrings.english().copy(failoverSwitch = false)`.
- I modelli con segnaposto usano il formato di `String.format` (`%1$s`, `%2$d`): sono quelli che una risorsa Android ha gia'. `fluidFormat` e' **tollerante**: una traduzione con un segnaposto in meno
  (o in piu') produce una frase storta, **non fa cadere l'app** mentre mostra un errore (ritorna il modello com'e'). Usa la `Locale` di default.
- Se carichi le parole da risorse Android (`stringResource`), costruisci i tre oggetti **una volta** per configurazione (`remember(locale)`), non a ogni ricomposizione: sono `@Immutable`
  e li legge mezza pagina.

## `FluidAssistantStrings`: le parole dell'overlay, della card e degli errori

Gruppi di campi (nomi veri; tutti `String` salvo dove detto):

- **Titolo e campo**: `title`, `askPlaceholder`.
- **Righe di stato**: `listening`, `transcribing`, `classifying`, `thinking`, `thinkingDeep`, `moreTools`, `deepModel`, `deepSuffix`, `parallel`, `waiting`, `switching`, `switchingWhy`, `writing`,
  `needsConfirmation`, `heardNothing`, `stopped`, `answerReady`, `idleStatus`, `dockDone`.
- **I perche' di un cambio di servizio, al passato** (uno per `SwitchReason`): `reasonRateLimited`, `reasonServer`, `reasonTimeout`, `reasonNetwork`, `reasonToolUseFailed`, `reasonModelUnavailable`,
  `reasonContextTooLong`, `reasonBadRequest`, `reasonEmptyAnswer`, `reasonParse`; piu' `answeredBy`, `theService`. Si leggono dopo il nome di un servizio: «Groq `era al limite`».
- **I fallimenti** (uno per `FailureKind`): `failNoKeys`, `failUnauthorized`, `failRateLimited…` (cinque, sotto), `failNetwork`, `failTimeout`, `failBlocked`, `failProvider`, `failModelUnavailable`,
  `failContextTooLong`, `failMicrophone`, `failTranscription`, `failUnknown`; parole di servizio `thisService`, `and`, `soon`.
- **I titoli brevi dei fallimenti** (per il foglio): `failTitleNoKeys` … `failTitleUnknown`.
- **Tasti ed etichette**: `stop`, `send`, `voice`, `newConversation`, `tapToFinish`, `confirm`, `cancel`, `close`, `finishedSpeaking`, `details`, `examples`.
- **Il foglio «Dettagli» e il suo «Copia»**: `failureDetailsTitle`, `failureWhatHappened`, `failureService`, `failureReason`, `failureServiceAnswer`, `failureCode`, `failureNoCode`, `failureTried`,
  `failureCopy`, `failureCopyHeader`, `failureCopyHttp`, `failureCopyAnswer`, `failureCopyTechnical`, `failureCopyService`, `failureCopyReason`, `failureCopyTried`.
- **`failoverSwitch: Boolean`** (di serie `true`) e **`statusFor: (key: String, tier: ModelTier) -> String?`** (di serie «niente»).

**Firme dei segnaposto** (l'ordine e il tipo contano: `%d` vuole un intero, `%s` una stringa):

| campo | segnaposto |
|---|---|
| `parallel` | `%1$s` base, `%2$d` chiamate in parallelo |
| `waiting` | `%1$s` servizio, `%2$d` secondi |
| `switching` | `%1$s` servizio d'arrivo |
| `switchingWhy` | `%1$s` chi lascia, `%2$s` perche' (da `reasonText`), `%3$s` chi prende |
| `answeredBy` | `%1$s` chi ha risposto, `%2$s` i perche' |
| `failRateLimitedIn` / `failRateLimitedInNoSwitch` | `%1$s` servizio, `%2$d` secondi |
| `failRateLimited` / `failRateLimitedNoSwitch` | `%1$s` servizio |
| `failRateLimitedAll` | `%1$s` i nomi («Groq e Gemini»), `%2$s` il tempo **gia' scritto** («40 s» o `soon`) |
| `failProvider`, `failModelUnavailable`, `failContextTooLong` | `%1$s` servizio; se non si sa, `failProvider` dice `theService` («Il servizio»), gli altri due `thisService` («questo servizio») |
| `failureCode`, `failureCopyHttp` | `%1$d` codice HTTP |
| `failureCopyAnswer`, `failureCopyTechnical`, `failureCopyService`, `failureCopyReason`, `failureCopyTried` | `%1$s` |

`deepSuffix` non e' un modello: e' un **suffisso** concatenato alla frase di stato quando si lavora col livello profondo (`" (modello profondo)"`, con lo spazio dentro).

`FluidAssistantStrings.reasonText(reason)` e' la tabella `SwitchReason` -> frase (ogni motivo ha la sua: un test lo verifica).

## `FluidAiSettingsStrings` e `FluidChatStrings`

`FluidAiSettingsStrings` raggruppa: interruttore (`enabledTitle`, `enabledNeedsKey`, `enabledOn`, `enabledOff`), chiavi (`keysTitle` … `keyTooShort`, `keyDisclosure`, `guides`), ordine
(`orderTitle`, `orderDetail`, `orderDetailNoFailover`, …), modelli (`modelsTitle`, `tier*`, `modelSameAsChat`, `modelAsChat`, `modelsRefresh*`), picker (`pickerSearch`, `filter*`, `section*`,
`fallback*`, `meta*`), OpenRouter (`or*`), preferenze (`prefsTitle`, `thinking*`, `speak*`, `actions*`, `failover*`, `mic*`), `privacyNote`, ultime richieste (`recent*`), diagnostica (`diag*`).
Aiutanti: `tierLabel(tier)`, `tierHint(tier)`, `thinkingLabel(level)`, `guide(provider)`.

Segnaposto (solo quelli che sbagliano facilmente): `keyVerifiedOn` `%1$s` data corta; `keyOpenConsole` `%1$s` host («console.groq.com»); `keyPlaceholderNew` `%1$s` l'inizio della chiave
(«gsk_…»); `keyValid` `%1$d` modelli di chat, `%2$d` di trascrizione; `keyChosenFree`/`keyChosenPaid` `%1$s` modello (**cominciano con « · »**: si concatenano alla riga di prima); `keyFailed` `%1$s`;
`keyWrongProvider` `%1$s` quale sembra, `%2$s` quale era il campo; `keyUnexpectedShape` `%1$s` servizio, `%2$s` prefisso atteso; `modelsRefreshedAt` `%1$s` data+ora; `metaContext` `%1$s`; `metaPrice`
`%1$s` prompt, `%2$s` completamento; `orCredits` `%1$s` crediti, `%2$s` uso di oggi; `orChosenFree`/`orChosenPaid` `%1$s`; `orFreeToday` `%1$d` usati, `%2$d` tetto; `recentSummary` `%1$s` chi, `%2$d` passi,
`%3$d` secondi; `recentGroups`, `recentTools` `%1$s`; `recentTokens` `%1$d` in, `%2$d` out; `recentWaited` `%1$d`; `diagOn` `%1$s` la catena; `diagQuota` `%1$s` servizio; `diagQuotaTokens`,
`diagQuotaRequests` `%1$d`.

- `keyPlaceholderKeep` e' **corto apposta** (3.0.4): il campo e' stretto, accanto a «Verifica», e una frase lunga andava a capo. Una traduzione piu' lunga di ~28 caratteri rompe di nuovo il layout (il test lo tiene
  sotto i 28 per le due lingue).
- `modelSameAsChat` comincia con « · »; `orderDetail` nomina la riserva automatica, `orderDetailNoFailover` no.
- `keyDisclosure: (ProviderId) -> String?` e `privacyNote` sono **dominio**: l'app li scrive; la nota di serie e' quella generica («le tue domande e i dati che gli strumenti leggono partono verso il servizio scelto»).

`FluidChatStrings`: `composerPlaceholder`, `composerBusyPlaceholder`, `composerEditing`, `voiceStart`, `voiceStop`, `stop`, `send`, `sendQueued`, `mute`, `userLabel`, `assistantLabel`, `queued`, `queuedRemove`,
`queuedAttachments` (`%1$d`), `copy`, `copied`, `share`, `shareChooser`, `retry`, `regenerate`, `details`, `cancelledBeforeAnswer`, `cancelledHere`, `noAnswer`, `interrupted`, `failedGeneric`. Le frasi dello stato
(«Ti ascolto…», «Trascrivo…») **non** sono qui: la riga della voce del composer le legge da `FluidAssistantStrings`.

**Campi di `FluidChatStrings` che nessun componente usa da solo** (esistono perche' l'app li usa nelle sue azioni): `composerBusyPlaceholder`, `composerEditing`, `copy`, `copied`, `share`, `shareChooser`,
`retry`, `regenerate`, `details`, `failedGeneric`. Passarli non cambia niente se non li leggi tu; il test di traduzione li controlla comunque. Quelli che i componenti **leggono davvero**: `composerPlaceholder`
(solo come default del parametro, vedi sotto), `voiceStart`, `voiceStop`, `stop`, `send`, `sendQueued`, `mute`, `userLabel`, `assistantLabel`, `queued`, `queuedRemove`, `queuedAttachments`, `cancelledBeforeAnswer`,
`cancelledHere`, `noAnswer`, `interrupted`.

## Parole che NON passano dalle stringhe (restano in italiano anche con `english()`)

Letto dal sorgente; sono i punti dove un'app tradotta deve intervenire a mano:

- `FluidComposer(placeholder = FluidChatStrings().composerPlaceholder)`: il default e' costruito **con i default italiani**, indipendentemente da `strings`. Passa `placeholder = strings.composerPlaceholder`.
- `FluidComposerPill(removeDescription = "Togli")`.
- `fluidCopyToClipboard(label = "Assistente")` (etichetta dell'appunto) e il `confirmation` che di serie manca (sotto Android 13 nessun avviso). Il «Copia» di `FluidFailureDetailsSheet` usa `strings.failureDetailsTitle` come etichetta ma **non**
  passa `confirmation`.
- `FluidStopPill(contentDescription = FluidAssistantStrings().stop)`: il default e' italiano; la card lo passa da se' (`strings.stop`), ma un uso diretto no.
- `FluidModelText.priceLabel`: «0 $», «<0,01 $», `"%.2f $"`; `fluidOpenRouterLines`: `"%.2f $"`, `"%.3f $"`; `fluidRequestDetails`: « ms», «✕», `"%.4f $"`; `FluidListeningHint`: «12s». I numeri seguono la `Locale`, il simbolo e i
  separatori dell'etichetta no.

## `FluidAssistantTexts`: la tabella pura

**Cos'e'.** L'**unico** posto in cui la UI dell'assistente sceglie una frase. **Pura**, senza Compose e senza risorse: le quattro copie avevano la stessa tabella in quattro posti (due con le risorse, due senza), e
quello che si poteva provare sul computer non lo era mai. Il dominio non c'e': per una chiave sconosciuta vale la frase generica.

| funzione | cosa da' |
|---|---|
| `status(key, tier, strings)` | la frase di un gruppo di strumenti: `thinking` (con variante profonda), `more_tools`, `deep_model`, altrimenti `statusFor(key, tier)` o «Penso…»; sul livello profondo aggiunge `deepSuffix` (tranne per il semplice «Penso», che ha la sua variante `thinkingDeep`) |
| `statusLine(state, strings, provider)` | la riga di stato per ogni stato; **`null`** a riposo e a `Done` (ha la risposta, non una riga) |
| `failure(kind, strings, retryAfterSec, provider, tried)` | la frase di un fallimento (tabella sotto) |
| `failureTitle(kind, strings)` | il fallimento in due-tre parole |
| `collapsed(state, strings)` | la riga della pillola: «Risposta pronta» a `Done`, altrimenti lo stato (o `title`) |
| `switchLine(switches, strings)` | «Ha risposto Gemini: Groq era al limite»; `null` senza cambi |
| `tried(switches)` / `names(providers, strings)` | i servizi per cui e' passata (in ordine, senza doppioni) / «Groq, Gemini e OpenRouter» |
| `bodyText(state)` | il testo del corpo: `Answering.partial`, `Done.answer`, `Failed.partial`, `Cancelled.partial`; `null` altrimenti |
| `question(state)` | la domanda che lo stato porta con se' (non in ascolto ne' in trascrizione) |
| `wantsExpanded(state)` | vero per `Done`, `Failed`, `AwaitingConfirmation`, `Answering` |

`statusLine` per ogni stato: `Listening` -> `listening`; `Transcribing`; `Classifying`; `Working` -> `status(...)` e, se `statusExtra > 0`, `parallel`; `WaitingRateLimit` -> `waiting`; `SwitchingProvider` ->
`switchingWhy` se c'e' il motivo, altrimenti `switching`; `Answering` -> `writing`; `AwaitingConfirmation`; `HeardNothing`; `Failed` -> `failure(...)`; `Cancelled` -> `stopped`.

## Errori e limiti di richieste

**Cosa vede l'utente, in ordine, quando un servizio e' al limite** (la meccanica del failover e' dell'orchestratore: [ai-orchestrator.md](ai-orchestrator.md)):

1. `AssistantState.WaitingRateLimit(provider, secondsLeft)`: riga di stato `waiting` («Groq e' al limite: riprovo fra 40 s»); aptica `WaitTick` **un tick per secondo diverso**.
2. Con la riserva automatica accesa: `SwitchingProvider(from, to, reason)`: `switchingWhy` («Groq era al limite: passo a Gemini…»), aptica `ProviderSwitched`. La stessa `reason` diventa, a risposta finita
   (`Done.switches`), la riga **«Ha risposto Gemini: Groq era al limite»** (card, bolla, `FluidAssistantTexts.switchLine`).
3. Se si arrendono tutti: `Failed(kind = RATE_LIMITED, retryAfterSec, provider)`: riga rossa + aptica `Error` + (se l'app passa `onDetails`) «Dettagli».

**Quale frase e' `failRateLimited…`** (`FluidAssistantTexts.failure(RATE_LIMITED, …)`):

| situazione | frase |
|---|---|
| `tried` ha **due o piu'** servizi (la riserva li ha gia' provati tutti) | `failRateLimitedAll`: «Sono al limite Groq e Gemini: riprova fra 40 s» (o `soon` senza secondi) |
| c'e' `retryAfterSec`, app **con** interruttore della riserva | `failRateLimitedIn`: «… riprova fra 40 s (o accendi la riserva automatica nelle impostazioni)» |
| c'e' `retryAfterSec`, `failoverSwitch = false` | `failRateLimitedInNoSwitch` |
| niente secondi, con interruttore | `failRateLimited` |
| niente secondi, `failoverSwitch = false` | `failRateLimitedNoSwitch` |

**Perche' cosi'.** Con la riserva accesa (di serie) un limite arriva qui solo quando sono al limite **tutti**; spenta, l'assistente aspetta e poi si arrende, e chi legge «riprova fra 40 s» deve sapere che
l'alternativa esiste. Se la riserva li ha gia' provati tutti il consiglio sarebbe sbagliato, e nominarne uno solo pure. Un'app **senza** l'interruttore non puo' consigliare di accendere quello che non c'e'.
Il servizio si nomina (`provider.label`, o `theService` / `thisService` se non lo si sa).

**Trappola importante: `tried` non arriva dallo stato.** `AssistantState.Failed` **non** porta i cambi di servizio. Di conseguenza:

- la **riga di stato** della card/overlay (`statusLine` -> `failure(kind, strings, retryAfterSec, provider)`) **non usa mai** `failRateLimitedAll`: nomina un servizio solo;
- il tasto «Dettagli» della card passa `FluidFailureReport.of(state)` senza `switches`, quindi `summary()` nel foglio nemmeno;
- `failRateLimitedAll` compare solo se **l'app** conosce i servizi provati (`AiRequestLog.switches`, `Done.switches` di un tentativo prima, ...) e li passa: `FluidFailureReport.of(state, switches)`, oppure
  `FluidAssistantTexts.failure(kind, strings, retryAfterSec, provider, tried = FluidAssistantTexts.tried(switches))` per il messaggio di un `FluidBubbleOutcome.Failed`.

Il foglio «Dettagli» mostra servizio, motivo, risposta del servizio (codice e messaggio), servizi provati con i loro perche': [ai-ui-overlay.md](ai-ui-overlay.md).

**Altri fallimenti, in una riga**: `NO_KEYS`/`UNAUTHORIZED` mandano alle impostazioni; `NETWORK` («Niente rete…»); `TIMEOUT` («dividi la domanda…»); `BLOCKED` (filtri del servizio: riformula o rigenera con un altro);
`PROVIDER` (nomina il servizio); `MODEL_UNAVAILABLE` («ne ho scelto un altro, riprova»); `CONTEXT_TOO_LONG` («aprine una nuova»); `MICROPHONE` («chiudi l'app che lo sta usando»); `TRANSCRIPTION`; `UNKNOWN`.
Nessun fallimento ha frase o titolo vuoti (un test lo verifica per ogni `FailureKind`).

## Cosa e' verificato dai test

`FluidAssistantTextsTest` (18): la tabella per ogni stato, i gruppi dell'app e quelli dell'engine (le chiavi `thinking`/`more_tools`/`deep_model` non passano da `statusFor`), il livello profondo, le
chiamate in parallelo, il cambio di servizio **con perche' solo se lo sa**, il limite (nomina il servizio, ricorda la riserva, conta i secondi), **«se la riserva li ha provati tutti non si consiglia piu' la riserva»**,
ogni fallimento ha frase e titolo, i servizi provati e l'elenco dei nomi, la riga di chi ha risposto, la pillola, il corpo e la domanda, la card espansa, una traduzione con un segnaposto in meno non fa cadere l'app,
ogni motivo di cambio ha una frase. `FluidEnglishStringsTest` (11): le tre `english()` (nessun campo italiano, nessuno vuoto, segnaposto uguali), le guide, `failoverSwitch = false` (italiano e inglese non nominano
la riserva), `orderDetailNoFailover`. `FluidAiSettingsLogicTest`: ogni stringa delle impostazioni con un segnaposto lo ha davvero. **Non verificato**: che l'app passi le sue traduzioni ai punti elencati sopra.

## Dove guardare

| file (`.../aiui/strings/`) | cosa c'e' |
|---|---|
| `FluidAssistantStrings.kt` | `FluidAssistantStrings`, `english()`, `reasonText`, `fluidFormat` |
| `FluidAssistantTexts.kt` | la tabella pura |
| `FluidAiSettingsStrings.kt` | `FluidProviderGuide`, `FluidProviderGuides`, `FluidAiSettingsStrings`, `english()` |
| `FluidChatStrings.kt` | `FluidChatStrings`, `english()` |
