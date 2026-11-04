# engine-ai-ui: la sezione impostazioni dell'assistente

«Impostazioni → Assistente IA» **gia' fatta**: un'estensione di `LazyListScope` che la pagina di impostazioni dell'app ospita come
ospita `fluidLicensesSection` o `fluidAppearanceSection`. Dentro: l'interruttore (che la prima volta puo' passare dal consenso, o essere
sospeso a distanza), le chiavi con la guida a quattro passi e la verifica, l'ordine dei servizi (frecce e trascinamento), i modelli per
livello con il picker nel vetro, le preferenze (ragionamento, lettura, azioni, riserva automatica, microfono), la nota sulla privacy e le
ultime richieste. La sezione **non conosce ne' un ViewModel ne' un DataStore**: riceve **dati** (`FluidAiSettingsState`), risponde con
**callback** (`FluidAiSettingsActions`), si adatta con **opzioni** (`FluidAiSettingsOptions`) e lascia posto per le voci dell'app negli
**slot** (`FluidAiSettingsSlots`).

Per `AiSettings`, `AiKeyStore`, `AiKeyVerifier`, `ModelCatalogStore`: [ai-provider.md](ai-provider.md) e il catalogo [../api/ai-chiavi-rete-voce.md](../api/ai-chiavi-rete-voce.md). Qui c'e' solo come la UI le legge.
Le parole sono in [ai-ui-stringhe.md](ai-ui-stringhe.md); l'assistente in scena in [ai-ui-overlay.md](ai-ui-overlay.md); il catalogo delle
firme e' [../api/ai-ui.md](../api/ai-ui.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| tutta la pagina «Assistente» in un colpo | `fluidAiSettingsSection(state, actions, ...)` |
| togliere i modelli (l'app sceglie da sola), il trascinamento, la trascrizione… | `FluidAiSettingsOptions(models = false, draggableOrder = false, transcription = false, …)` |
| aggiungere una mia voce (permessi, app collegate, uso) | `FluidAiSettingsSlots(afterKeys / afterModels / afterPreferences / footer)` |
| sospendere l'assistente a distanza con un motivo | `FluidAiSettingsState(locked = FluidAiLock("motivo"))` |
| passare dal consenso la prima volta | `FluidAiSettingsOptions(requireConsent = true)` + `FluidAiSettingsActions(onRequestConsent = …)` |
| la sola scheda di una chiave (p.es. in un onboarding) | `FluidAiKeySetup` |
| il solo elenco ordinabile dei servizi | `FluidProviderOrderList` |
| il solo foglio per scegliere un modello | `FluidModelPickerPortal` |
| la sezione «Assistente» di una pagina Diagnostica | `fluidAiDiagnosticsSection` |
| ancorare un suggerimento della prima volta all'interruttore | `enabledModifier` |
| tenere i cataloghi dei modelli freschi | `FluidAiCatalogueRefresh` |

**Non cosi'**: non reimplementare le righe delle chiavi o dell'ordine per «cambiare un colore»: `FluidAiKeySetup` prende `onSurface` e
`onSurfaceVariant` (per una pagina su un cielo, un onboarding). Non togliere `FluidAiSettingsOptions.privacyNote` per risparmiare una riga:
la nota dice cosa parte verso il servizio; riscrivila nelle stringhe dell'app.

## `fluidAiSettingsSection`

**Firma essenziale.**

```kotlin
fun LazyListScope.fluidAiSettingsSection(
  state: FluidAiSettingsState,
  actions: FluidAiSettingsActions,
  strings: FluidAiSettingsStrings = FluidAiSettingsStrings(),
  options: FluidAiSettingsOptions = FluidAiSettingsOptions(),
  slots: FluidAiSettingsSlots = FluidAiSettingsSlots(),
  glass: Boolean = true,
  keyPrefix: String = "fluid-ai",
  enabledModifier: Modifier = Modifier,
)
```

**Uso** (da `FluidAiUiDemo`; la pagina raccoglie i flussi e li passa come valori).

```kotlin
LazyColumn {
  fluidAiSettingsSection(
    state = FluidAiSettingsState(
      settings = settings, keys = keys, catalogues = catalogues, keyInfo = keyInfo, recent = recent,
      thinkingAuto = null,                 // null: niente chip «Auto»
      failoverEnabled = failover,          // null: niente riga «riserva automatica»
    ),
    actions = FluidAiSettingsActions(
      saveAndVerify = { provider, key -> vm.saveAndVerify(provider, key) },   // salva se key != null, poi AiKeyVerifier.verify
      removeKey = { vm.removeKey(it) },
      setEnabled = { vm.setEnabled(it) },
      setChatOrder = { vm.setChatOrder(it) },
      setModel = { provider, tier, id -> vm.setModel(provider, tier, id) },
      setFailoverEnabled = { vm.setFailover(it) },
    ),
    glass = true,
  )
}
```

(`vm` e' il tuo ViewModel o servizio: dietro ci sono `AiKeyStore`, `AiKeyVerifier` e `AiSettingsStore`, vedi [ai-provider.md](ai-provider.md).)

**Ordine delle sezioni** (e quando compaiono):

1. `slots.beforeKeys`
2. **Interruttore** (sempre). Sottotitolo: il motivo di `locked` se c'e'; altrimenti «serve una chiave verificata» se non ce n'e', «acceso» / «spento»
   secondo `settings.enabled`. L'interruttore e' **acceso davvero** solo se `FluidAiSettingsState.enabled`: l'utente l'ha acceso **e** c'e' una chiave
   verificata **e** nessuno lo ha sospeso; e' **disabilitato** senza chiavi verificate o con `locked`. Con `requireConsent` e consenso mancante
   (`!settings.consentAccepted`) l'accensione chiama `onRequestConsent()` **al posto di** `setEnabled(true)`: l'app apre il suo consenso e poi chiama `setEnabled`.
3. **Chiavi**: una `FluidAiKeySetup` per ogni `ProviderId`. Sotto OpenRouter, se ha una chiave (`present`): i dettagli (crediti, modello, gratuiti di oggi) e la riga
   della politica dei dati (`dataPolicy`).
4. `slots.afterKeys`
5. **Ordine dei servizi**: solo con **piu' di un** servizio verificato. Chat sempre; trascrizione se `transcription`. La frase sotto il titolo cambia senza
   riserva automatica (vedi sotto).
6. **Modelli**: se `options.models` **e** almeno un servizio verificato; un gruppo per servizio verificato (in ordine di enum) con le righe per livello
   (`tiers`) o la sola chat, la trascrizione, «Aggiorna il catalogo». Il picker e' dichiarato **sempre** (mai dentro un `let`).
7. `slots.afterModels` (resta anche con `models = false`)
8. **Preferenze**: ragionamento, lettura delle risposte, azioni, riserva automatica, microfono; l'intestazione compare se almeno una e' attiva.
9. `slots.afterPreferences`
10. **Nota sulla privacy**: se `options.privacyNote` e `strings.privacyNote` non e' vuota.
11. **Ultime richieste**: se `options.recentRequests` e `state.recent` non e' vuoto.
12. `slots.footer`

Ogni item ha una chiave `"$keyPrefix-..."`: se la pagina ha due sezioni di questo tipo, cambia `keyPrefix`.

### `FluidAiSettingsState` (tutto come dati)

`settings: AiSettings`, `keys: Map<ProviderId, KeyState>`, `catalogues: Map<ProviderId, ModelCatalogue>`, `keyInfo: Map<ProviderId, OpenRouterKeyInfo>`,
`recent: List<AiRequestLog>`, `thinkingAuto: Boolean?`, `failoverEnabled: Boolean?`, `openRouterQuota: FluidOpenRouterQuota?`, `locked: FluidAiLock?`.
Derivati: `verified` (i servizi con `KeyState.verified`: gli **unici** che possono rispondere) ed `enabled`.

**I `null` sono interruttori di funzione**: `thinkingAuto == null` toglie il chip «Auto» (il ragionamento decide l'utente, non l'assistente); `failoverEnabled == null`
(o `actions.setFailoverEnabled == null`) toglie la riga della riserva automatica; `openRouterQuota == null` toglie la riga dei gratuiti di oggi.

### `FluidAiSettingsActions` (tutte callback)

Obbligatorie: `saveAndVerify`, `removeKey`, `setEnabled`. Il resto ha un default che non fa niente: passa solo quello che l'app ha.
`saveAndVerify: suspend (ProviderId, String?) -> VerifyResult` salva la chiave **se non e' null** e la verifica (un `null` = «verifica quella che c'e'»).
`setThinkingAuto` e `setFailoverEnabled` sono **nullable** apposta: devono essere non-null **insieme** al loro stato per far comparire la riga.
`onVerified(provider, ok)` scatta a verifica riuscita: un'app puo' accendere l'assistente se l'utente non aveva mai scelto.

### `FluidAiSettingsOptions` (cosa si mostra; tutto acceso di serie)

E' l'**unione** delle quattro app che avevano scritto ognuna la sua pagina: ognuna spegne cio' che non ha.

| campo | spegne |
|---|---|
| `requireConsent` (falso di serie!) | acceso: la prima accensione passa da `onRequestConsent` |
| `tiers` | falso: solo il livello chat (un solo modello per servizio) |
| `transcription` | l'ordine e il modello della trascrizione della voce |
| `draggableOrder` | la maniglia per trascinare (restano le frecce) |
| `microphone` | la riga del permesso del microfono |
| `thinking`, `speakReplies`, `actions` | le tre righe delle preferenze |
| `recentRequests`, `dataPolicy`, `privacyNote` | le ultime richieste, la scelta di OpenRouter sull'addestramento, la nota |
| `initiallyExpandFirstKey` | la guida del primo servizio aperta finche' nessuna chiave e' verificata |
| `models` (3.0.4) | tutta la sezione dei modelli: intestazione, livelli, trascrizione, «aggiorna il catalogo» (`slots.afterModels` resta) |

`requireConsent` e' l'unico che e' `false` di serie: tutti gli altri sono `true`.

### `FluidAiSettingsSlots`

`beforeKeys`, `afterKeys`, `afterModels`, `afterPreferences`, `footer`: ognuno `(LazyListScope.() -> Unit)?`, un blocco di `item` che compare solo se c'e'.
E' dove l'app mette le sue voci (la voce, i permessi, le azioni fidate, le app collegate, l'uso, la cronologia, il tutorial, la diagnostica). Gli item che
aggiungi usano chiavi **tue**: non riusare `"$keyPrefix-…"`.

### `FluidAiLock`

`FluidAiLock(reason)`: un interruttore a distanza ha sospeso l'assistente. L'interruttore resta, **spento e senza risposta**, e il sottotitolo dice il motivo
invece di far sembrare rotta la pagina. Mette `state.enabled` a falso anche se l'utente l'aveva acceso.

### Riserva automatica e `slots`: come cambiano le frasi

L'app che **non ha** l'interruttore della riserva automatica (`failoverEnabled == null` o `setFailoverEnabled == null`) non puo' consigliare di accenderlo (3.0.4):

- sotto «Ordine dei servizi» la frase e' `orderDetailNoFailover` invece di `orderDetail` (`fluidAiOrderDetail`);
- il limite di richieste dell'assistente (`failRateLimited…`) deve usare le varianti `…NoSwitch`: lo dice `FluidAssistantStrings(failoverSwitch = false)`. **Le due
  cose non sono collegate da codice**: la sezione sceglie la sua frase dall'assenza dell'interruttore, ma la frase dell'errore la sceglie `failoverSwitch`, che
  imposti tu in `FluidAssistantStrings` ([ai-ui-stringhe.md](ai-ui-stringhe.md)). Sono due righe da tenere coerenti.

**Trappole.**

- `FluidAiSettingsOptions.requireConsent` vale solo se hai anche `onRequestConsent`: il default non fa niente e l'interruttore **non si accende** (la richiesta
  di accensione e' stata consumata dal consenso).
- E' un'estensione di `LazyListScope`: va chiamata nel blocco di una `LazyColumn`, accanto alle altre sezioni della pagina; non disegna niente da sola.
- Il picker dei modelli e il foglio «Dettagli» richiedono il host dei modali alla radice: senza **non si aprono e non danno errore**.
- `glass = true` (di serie) presume una pagina con un canvas ambientale, come ogni vetro dell'engine; su una pagina senza passa `glass = false`.
- Le ultime richieste sono **solo in memoria** (spariscono con l'app): se la tua `AiDiagnosticsLog` e' vuota, la sezione non c'e'.

**Versione.** Dalla 3.0.0. 3.0.4: `FluidAiSettingsOptions(models)`, `enabledModifier`, `orderDetailNoFailover`, `keyPlaceholderKeep` corto («Incolla per
sostituirla»: quello di prima andava a capo nel campo stretto accanto a «Verifica»; **cambia a vista**, e un test lo tiene sotto i 28 caratteri).

## Le chiavi: `FluidAiKeySetup` e `FluidKeyShape`

**Cos'e'.** La scheda di una chiave: stato, guida a quattro passi con il tasto che apre la console, campo, «Verifica» con l'esito inline, «Rimuovi».

```kotlin
FluidAiKeySetup(provider = ProviderId.GROQ, state = keyState, saveAndVerify = actions.saveAndVerify, onRemove = actions.removeKey, onVerified = { /* accendi se mai scelto */ })
```

**Comportamento.**

- Il campo e' `FluidTextField(secret = true)`: una chiave e' una password, non si mostra e la tastiera non la impara; l'occhio la mostra. **La chiave salvata non si
  ri-mostra mai**: campo vuoto = «lascia quella salvata»; il segnaposto lo dice (`keyPlaceholderKeep`). «Verifica» compare se c'e' testo **o** una chiave salvata;
  «Rimuovi» solo con una chiave salvata e il campo vuoto.
- Ogni carattere digitato passa da `FluidKeyShape.clean`: toglie spazi e a capo (in testa, in coda e **dentro**: una copia dal terminale a capo li porta), virgolette,
  `Authorization:` e `Bearer `. La chiave non ha mai spazi.
- **Avviso prima del giro di rete**: la chiave di un altro servizio incollata nel campo sbagliato e' l'errore piu' comune, e «il servizio l'ha rifiutata» non dice perche'.
  `FluidKeyShape.check(provider, raw)` ritorna `FluidKeyCheck`: `Empty`, `Looks`, `WrongProvider(actual)`, `UnexpectedShape(expectedPrefix)`, `TooShort` (sotto
  `MinLength` = 20). `isWarning` e' vero per tutti tranne `Empty` e `Looks`. **Non e' una validazione**: un prefisso che cambia domani non deve impedire a nessuno di
  incollare una chiave buona. Prefissi: Groq `gsk_`, Gemini `AIza`, OpenRouter `sk-or-`. L'avviso sta sotto il campo **finche' non c'e' un esito vero**; dopo parla il servizio.
- L'esito: `VerifyResult.Ok` -> «Chiave valida: N modelli di chat, M di trascrizione» (+ il modello scelto, gratuito o no) e aptica `Confirm`; `Invalid` ->
  «rifiutata» in rosso, `Error`; `Failed` -> `keyFailed` col messaggio. **Tutto dentro un solo try**: qui si tocca il Keystore, il disco e la rete, e un errore qualsiasi deve
  dire cos'e' andato storto, non far cadere l'app (la cancellazione della coroutine passa).
- La guida e' `strings.guide(provider)` (un servizio senza guida in una traduzione **ricade sull'italiano**, non sul vuoto). `strings.keyDisclosure(provider)` e' la
  spiegazione di cosa parte verso il servizio e cosa resta sul telefono: **la scrive l'app** (dice cosa, e' dominio), `null` non mostra niente. La garanzia sulla chiave si
  leggeva come «non esce niente», e non e' vero.

## Ordine dei servizi: `FluidProviderOrderList` e `FluidProviderOrder`

Frecce su/giu' **e** trascinamento (pressione lunga, come una griglia di home). Le frecce restano perche' sono l'unica strada per chi non puo' trascinare (tastiera, TalkBack);
il trascinamento perche' e' quella che la mano si aspetta. `draggable = false` toglie la maniglia.

- **Solo i servizi con chiave verificata compaiono**; gli altri restano **in coda all'ordine salvato** e ci tornano quando avranno una chiave: spostare uno sopra l'altro non
  deve far dimenticare dove stava quello che oggi non ha la chiave. `FluidProviderOrder.move(order, available, from, to)` ritorna l'ordine **intero** (visibili nel nuovo
  ordine, poi i nascosti nell'ordine in cui erano); un indice fuori dalla lista visibile o uno spostamento su se stesso ritorna l'ordine com'era.
- `FluidProviderOrder.dragTarget(index, offsetPx, rowHeightPx, count)`: una riga e' «passata» quando il dito ne ha attraversata **una intera**, non a meta'; resta dentro la
  lista. L'altezza di una riga e' stimata a 64 dp.
- Una vibrazione per momento: `GestureStart` quando la riga si prende, un `Tick` a ogni posto che passa, `GestureEnd` quando si lascia. Dopo ogni scambio lo stato di trascinamento
  si azzera.
- Il primo e' «Primo a rispondere», gli altri «Riserva, se il precedente e' al limite o non risponde».

## I modelli: `FluidModelPickerPortal` e `FluidModelPicker`

**Cos'e'.** Il picker **nel vetro dell'engine** (`FluidGlassModalPortal`, un foglio). Per Groq e Gemini un elenco col preferito in testa (`FluidSimpleModelList`: un tocco sceglie e
chiude); per OpenRouter (`FluidOpenRouterList`) ricerca, filtri, sezioni **Consigliati / Gratuiti / Tutti** e fino a **due riserve** per la chat, che OpenRouter prova da solo se il
primario fallisce. Qui c'e' solo il disegno: le regole sono di `FluidModelPicker` (pure, provate).

```kotlin
var request by remember { mutableStateOf<FluidModelPickRequest?>(null) }   // (provider, tier?, stt)
FluidModelPickerPortal(
  request = request, catalogues = catalogues,
  selectedModel = { r -> if (r.stt) settings.sttModel(r.provider) else r.tier?.let { settings.model(r.provider, it) } },
  fallbacks = settings.openRouterFallbacks,
  onSelect = { r, id -> /* salva */ }, onFallbacks = { /* salva */ },
  onDismiss = { request = null },
)
```

(Di solito non lo chiami tu: lo fa `fluidAiSettingsSection`.)

**Comportamento.**

- **Dichiaralo sempre**, mai dentro un `let`: smontarlo alla chiusura toglie l'animazione di uscita (le lambda composable sono contenitori mutabili: alla chiusura le catture sono gia' `null`).
  `request = null` lo chiude.
- Catalogo vuoto: «Nessun modello nel catalogo: verifica la chiave per scaricarlo.»
- La scelta **chiude** il foglio per Groq e Gemini (`onSelect` poi `onDismiss`); per OpenRouter **no**: si resta nella lista per aggiungere le riserve, e si chiude con un gesto.
- OpenRouter: i filtri sono **Strumenti** (acceso, **non si toglie**: un modello senza tool non serve a un assistente), Ragiona, Gratuiti, Vede; ricerca per id **o** nome senza badare
  alle maiuscole. Le sezioni vuote non si mostrano; un modello puo' stare in piu' sezioni (un consigliato gratuito sta in due) ed e' voluto. Tetti: Consigliati 24, Gratuiti 24, Tutti 200.
- Le riserve (`MaxFallbacks` = 2) solo per il livello **chat** (router e profondo di OpenRouter usano la stessa lista, senza i tasti «Riserva»): si **tolgono sempre**, si mettono finche' c'e' posto, il primario non puo' essere riserva di se stesso
  (`afterChoosingPrimary` le toglie). A due riserve compare «Hai gia' due riserve: togline una per sceglierne un'altra». Il tasto «Riserva» **cede allo scorrimento**
  (`FluidPressGrip.YieldsToScroll`): su ogni riga di una lista fino a 200 modelli il dito che scende deve poter scorrere.
- Ogni riga: nome, id, `contextLabel` (1_000_000 -> «1M», 128_000 -> «128k»), prezzo se esiste, «gratuito», «ragiona», «vede», «legge PDF».
- Una riga delle impostazioni mostra `ModelCatalogue?.fluidSummary(id, strings)`: il nome dal catalogo se c'e' (con «gratuito»), l'id altrimenti, `modelAsChat` se `id` e' `null`.
  Il livello profondo **uguale alla chat** lo dice (`modelSameAsChat`): non c'e' nessun passaggio da fare e il tool `modello_avanzato` non viene nemmeno offerto al modello.

**Trappole.**

- `FluidModelText.priceLabel` scrive «0 $» e «<0,01 $» con **virgola italiana e simbolo fisso**: la `locale` regola solo i numeri sopra il centesimo; la stringa inglese del catalogo non
  li cambia.
- Il picker passa dal host dei modali: senza, esce in silenzio.

## OpenRouter e le sue righe

- `FluidOpenRouterKeyDetails(settings, catalogue, info, quota, strings)`: due-tre righe sotto la scheda: crediti (rimanenti, o «illimitati») e l'uso di oggi, account
  gratuito o con crediti; il modello scelto e se e' gratuito; i **gratuiti di oggi contro il tetto** (solo se il modello scelto e' gratuito e `quota != null`). Senza `keyInfo` dice di
  verificare la chiave. Un modello **non ancora nel catalogo si presume gratuito**: e' la scelta che l'engine fa da solo, e dire «a pagamento» senza saperlo spaventerebbe per niente.
  Le righe sono `fluidOpenRouterLines(...)` (pura).
- `FluidOpenRouterQuota(used, cap)`: il tetto e' `FREE_ACCOUNT_CAP` 50 per un account senza crediti, `PAID_ACCOUNT_CAP` 1000 con; `capFor(isFreeTier)`.
- `FluidOpenRouterDataPolicyRow(policy, onPolicy)`: «come sul mio account» (di serie) o «non usarli». La politica sui dati e' dell'**account**, non dell'app: chiedere `deny` a ogni richiesta
  non rende niente piu' sicuro (sovrascrive con la piu' stretta la scelta gia' fatta su openrouter.ai) e sui modelli i cui endpoint gratuiti registrano i prompt non ne lascia nessuno
  disponibile («No endpoints found matching your data policy»). Per questo la frase dice cosa costa.
- `FluidChoiceRowWithChips(title, subtitle, chips)`: una riga la cui impostazione e' una scelta fra chip. I chip **non** stanno nel `badge`: quello slot prende la larghezza che gli serve
  e lascia al testo quella che avanza, e con tre-quattro voci il titolo finiva spezzato una lettera per riga. Vanno sotto, a tutta larghezza, e scorrono in orizzontale.

## Ultime richieste e diagnostica

- `FluidRecentRequestRow(log, strings)`: la domanda (80 caratteri) in una riga con l'ora; sotto, `fluidRequestDetails(log, strings)` (pura): **chi ha risposto in ordine, con il cambio di servizio**
  («Gemini gemini-2.5-flash -> OpenRouter minimax-m2.5» dice anche il passaggio, che la mappa per livello nasconde), quanti passi e quanto ci ha messo, e — se ci sono — i gruppi, gli
  strumenti coi tempi (un ✕ se falliti), i token e il costo, l'attesa, l'errore.
- `fluidAiDiagnosticsSection(settings, keys, enabled, rateLimits, requests, strings, glass, keyPrefix)`: la sezione «Assistente» di una pagina Diagnostica: stato, catena dei servizi con i
  modelli, le **quote residue lette dagli header** (`fluidQuotaParts`: token e richieste che restano), le ultime richieste. **Dati, non flussi**: l'app legge `AiDiagnosticsLog.entries`,
  `rateLimits` e `AiKeyStore.states` e passa i valori.
- `FluidAiCatalogueRefresh(verified, refresh)`: un `LaunchedEffect` che la pagina chiama **una volta**: i cataloghi si leggono dal disco all'apertura e si rinfrescano se vecchi di un giorno
  (`AiKeyVerifier.refreshIfStale`). Sta qui perche' le quattro app lo avevano scritto identico (tre lo dimenticavano, e la lista dei modelli restava quella dell'ultima verifica).

## Il permesso del microfono nelle preferenze

La riga «Microfono» legge il permesso e, se negato, mostra «Consenti». Il **primo** tocco lancia la richiesta di sistema; se e' negata per sempre, il **secondo** apre le impostazioni di sistema
dell'app (la richiesta non mostra piu' niente). Si rilegge a ogni risposta.

## Cosa e' verificato dai test

`FluidAiSettingsLogicTest` (30 test, JVM): ordine dei provider (solo i verificati si vedono, `move` ritorna l'ordine intero, un nascosto non si perde, mosse impossibili lasciano com'era,
il trascinamento passa una riga a una intera), forma delle chiavi (prefissi, pulizia, chiave di un altro servizio, troppo corta avverte ma non vieta, gli avvisi sono frasi vere), le tre guide
sono complete e una guida mancante ricade sull'italiano, i filtri di OpenRouter (senza strumenti mai proposti, ricerca per id e nome, i filtri si sommano, sezioni, tetti, le due riserve),
contesto e prezzo, i crediti/modello/gratuiti di oggi, `FluidAiSettingsState.enabled`, `fluidRequestDetails` (catena, ritorno alla forma vecchia, gruppi/strumenti/token/attesa/errore),
le quote, **ogni stringa delle impostazioni con un segnaposto lo ha davvero**. `FluidEnglishStringsTest`: `fluidAiShowsModels`, `fluidAiOrderDetail`, il segnaposto corto della chiave salvata,
le guide inglesi. **Non verificato** dai test JVM: il disegno delle schede, il foglio nel vetro, il trascinamento sotto il dito, il permesso reale, la rete.

## Dove guardare

| file (`.../aiui/settings/`) | cosa c'e' |
|---|---|
| `FluidAiSettingsSection.kt` | `fluidAiSettingsSection`, `FluidAiSettingsState/Actions/Options/Slots`, `FluidAiLock`, `FluidAiCatalogueRefresh`, preferenze e modelli (privati) |
| `FluidAiSettingsLogic.kt` | `FluidProviderOrder`, `FluidKeyShape`, `FluidKeyCheck`, `FluidModelPicker`, `FluidModelFilter`, `FluidModelSection`, `FluidModelText`, `FluidOpenRouterQuota` (tutto puro) |
| `FluidAiKeySetup.kt` | la scheda di una chiave, `keyHint` |
| `FluidModelPicker.kt` | portale, elenco semplice, elenco OpenRouter, `fluidSummary` |
| `FluidProviderOrderList.kt` | l'elenco ordinabile |
| `FluidAiDetails.kt` | dettagli e politica OpenRouter, ultime richieste, diagnostica, `FluidChoiceRowWithChips` |
