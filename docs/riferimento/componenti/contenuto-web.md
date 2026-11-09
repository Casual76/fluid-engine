<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/contenuto-web.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Contenuto web: FluidWebContent

Un contenitore per **HTML locale che arriva da qualcun altro** (il corpo di una mail, una newsletter, le note di una versione scritte altrove, un documento esportato), **chiuso per costruzione**: JavaScript spento, nessuna
rete nativa, nessuna navigazione, ogni richiesta consegnata all'app, che di serie la blocca. Famiglia «Contenuto» ([`contenuto.md`](contenuto.md) ha l'indice). File: `engine-ui/.../ui/fluid/FluidWebContent.kt`. Dalla 3.1.0
(Pampa Mail, F1.5, gruppo G-C). Catalogo delle firme: `api/contenuto-web.md` (generato).

**Modello mentale in due frasi.** Il contenitore è un **recinto**: non decide cosa è sicuro mostrare, impedisce che quello che mostra faccia qualcosa. L'engine non sa cos'è una mail né quale immagine è un tracker: **sanificare
l'HTML, decidere quali risorse caricare e verificare dove portano i link sono dell'app**, e restano dell'app anche quando il contenitore le dà in mano un byte alla volta.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| mostrare un HTML di cui non mi fido (una mail, una newsletter) | `FluidWebContent(html, onLinkClick)`: rete e script chiusi di serie |
| il testo di una mail in una pagina con altro contenuto | `sizing = FluidWebSizing.WrapContent(maxHeight)` (di serie 2048 dp): cede lo scroll alla pagina finché il contenuto entra |
| un documento lungo in una viewport fissa | `FluidWebSizing.Viewport(height)` (di serie 600 dp), meglio in una pagina senza altro scroll |
| caricare le immagini che l'utente ha permesso | `onRequest = { req -> … FluidWebResponse.Serve(mime, encoding) { stream } }` col **tuo** client, senza cookie né referer |
| sapere dove porta davvero un link | `onLinkClick(FluidWebLink(url, visibleText))`: la **destinazione vera** e il testo visibile, e la verifica è tua |
| il menu di un link (copia, apri, condividi) | `onLinkLongPress = { link -> … }` |
| scurire un documento chiaro | `darkening = true` (**ricrea** la vista: costo sotto) |
| più grande per chi legge male | `textScale` (moltiplica **sempre** la scala del carattere di sistema) |
| stampare / esportare in PDF | `state.printAdapter(jobName)` → `PrintManager.print` |
| ripartire dopo un errore o un renderer morto | `state.retry()` (vista e profilo nuovi) |
| testo vero, solo da leggere, senza stili né link | `FluidMarkdown` o un `Text`: **non** una WebView |
| modificare del testo formattato | `FluidRichTextEditor` ([`../moduli/richtext.md`](../moduli/richtext.md)): non una WebView con `contenteditable` |

## Firma essenziale

```kotlin
FluidWebContent(
  html: String,
  onLinkClick: (FluidWebLink) -> Unit,                         // main thread; l'app decide se e dove aprire
  modifier: Modifier = Modifier,
  state: FluidWebContentState = rememberFluidWebContentState(),
  onRequest: (FluidWebRequest) -> FluidWebResponse = { FluidWebResponse.Block },   // thread di I/O, anche in parallelo
  sizing: FluidWebSizing = FluidWebSizing.WrapContent(),
  darkening: Boolean = false, textScale: Float = 1f, pinchZoom: Boolean = true,
  onLinkLongPress: ((FluidWebLink) -> Unit)? = null,
  placeholder: @Composable () -> Unit = { FluidSkeletonText(lines = 6) },
  failure: @Composable (FluidWebFailure) -> Unit = {},
)
FluidWebRequest(url: Uri, method: String, isMainFrame: Boolean)      // + headers: solo un UA generico
FluidWebResponse.Block | FluidWebResponse.Serve(mimeType, encoding, body: () -> InputStream)
FluidWebLink(url: String, visibleText: String?)
FluidWebPhase { Empty, Loading, Ready, Error }  ·  FluidWebFailure { IsolationUnavailable, RendererGone, Timeout, ContentTooLarge, Unavailable }
FluidWebContentState: phase, failure, contentHeightPx, scrollsInternally, retry(), printAdapter(jobName)
FluidWebContentDefaults: UserAgent = "WebContent/1.0", MaxHtmlCharacters = 4_000_000
```

## Il contratto chiuso, e perché

Cosa è chiuso, tutto insieme e **non negoziabile dall'app** (nessun parametro lo apre):

- **JavaScript spento**, nessun bridge, nessun accesso a file o `content:`, nessuno storage, nessun form, nessuna finestra, nessun permesso. Una politica di sicurezza (CSP) in intestazione su ogni documento:
  `sandbox; script-src 'none'; object-src 'none'; frame-src 'none'; child-src 'none'; form-action 'none'; base-uri 'none'; connect-src 'none'`. Un `<script>`, un `onload`, un `<meta refresh>`, un `<svg onload>`, un
  `data:` con script: niente gira.
- **Nessuna rete nativa.** La WebView non apre connessioni da sola. Ogni risorsa (immagine, foglio di stile, font) è una **richiesta consegnata all'app** (`onRequest`); l'app o serve byte o blocca. **Non si può restituire `null`**
  (autorizzerebbe il caricamento nativo): `FluidWebResponse.Block` è una risposta vuota locale.
- **Nessuna navigazione interna.** Toccare un link **non naviga**: `onLinkClick` riceve la **destinazione vera** e, quando il renderer riesce a fornirlo senza eseguire script, il **testo visibile** (`FluidWebLink.url`,
  `visibleText`). È il pezzo che serve contro il phishing («https://banca.it» che porta altrove): l'app confronta e decide. Un `javascript:` non esegue niente.
- **Nessuna identificazione.** Lo `User-Agent` è `WebContent/1.0`: né dispositivo, né versione, né app.
- **Il documento è servito su un'origine casuale `.invalid`** (`https://<uuid>.invalid/document`), con CSP `sandbox` e `no-store`. **Nessun `baseUrl` pubblico**: un'origine controllata dall'HTML potrebbe condividere dati con
  altre viste. Il documento si legge come byte locali, non con `loadDataWithBaseURL` (che avrebbe allentato gli stessi header).
- **Profili e cookie isolati.** Ogni istanza ha un **profilo dedicato, mai riutilizzato** (`fluid-content-<uuid>`), cookie e cache compresi; il `CookieManager` e lo `WebStorage` globali non si toccano mai. **Senza
  `MULTI_PROFILE` il contenitore fallisce chiuso** (`FluidWebFailure.IsolationUnavailable`) invece di cambiare il `CookieManager` globale o condividere la cache con le altre WebView dell'app: un'app con un provider vecchio
  mostra il testo alternativo, non una vista meno isolata. Il profilo si elimina al rilascio **quando il provider lo permette**, altrimenti al primo host del processo successivo: **non promettere una cancellazione immediata**.
- **Il renderer che muore si recupera.** `FluidWebFailure.RendererGone` arriva allo slot `failure`; `state.retry()` **distrugge e ricrea** la vista con un profilo nuovo (non rianima un renderer morto) e non salva né HTML né
  consensi fra istanze.

## Cosa resta all'app (e non è poco)

Il contenitore **non è un sanificatore** (lo dice anche il KDoc): `FluidWebResponse.Serve` non rende sicuro ciò che serve.

| dell'app | perché non può essere dell'engine |
|---|---|
| **sanificare l'HTML e il CSS** prima di passarli (e il corpus di prova) | l'engine non sa cosa un'app considera accettabile (un `<form>` in una newsletter? i colori di fondo?) |
| **la politica delle immagini e dei tracker**: bloccare di serie, sbloccare per mittente, per sessione, «mai» | è una scelta di prodotto e di privacy; l'engine dà solo il posto (`onRequest`) e la garanzia che **non passi cookie, referer, credenziali né redirect impliciti** |
| **il client che scarica una risorsa servita** | deve usare un client **senza cookie, senza referer, senza credenziali e senza redirect impliciti** (`FluidWebRequest.headers` suggerisce solo l'UA generico); un'eccezione in `onRequest` **blocca** la risorsa |
| **verificare i link** (dominio visibile diverso dal vero, schemi strani, `intent:`) e aprirli (browser, scheda in-app, avviso) | solo l'app sa cosa è un link sospetto per i suoi utenti |
| il **testo alternativo** quando il contenitore fallisce | l'engine dà `failure` e un `FluidWebFailure`; cosa mostrare (il corpo in testo semplice, «Aggiorna WebView») lo decide l'app |
| **consenso e memoria** («carica sempre per questo mittente») | cambiando `onRequest` **non si ricarica il documento**: per applicare un consenso a risorse già bloccate chiama `state.retry()` |

## Altezza, viewport, scroll

L'altezza è **sempre limitata**: un documento enorme non diventa una texture enorme in una lista lazy. `FluidWebSizing.WrapContent(maxHeight = 2048.dp)` ha l'altezza del contenuto **fino al tetto**, e **cede lo scroll
verticale alla pagina finché il contenuto entra**; oltre il tetto scorre da sé (`state.scrollsInternally`, vero anche nel ripiego automatico). `FluidWebSizing.Viewport(height = 600.dp)` è una viewport fissa per documenti
lunghi. In ogni caso **l'altezza fisica massima è 4096 px** (`MaxViewportPx`, anche con font al 200%), e parte da almeno 1 dp (1 pixel fisico a densità 2,625 è meno di un pixel CSS e dava viewport iniziali degeneri).

- `state.contentHeightPx` è l'altezza vera del documento (può superare quella della viewport); durante il parsing progressivo cambia a ogni fotogramma: l'engine usa `derivedStateOf` per non ricomporre la pagina quando la viewport
  è già al tetto. Non leggere `contentHeightPx` in composizione per ridimensionare altro.
- **Dentro una `LazyColumn`** il contenitore prende il gesto verticale solo quando può scorrere internamente (l'esclusione dell'intercettazione della lista parte da `ACTION_DOWN`, e da `POINTER_DOWN` per il pizzico: da `MOVE` è
  troppo tardi). Una `Viewport` dentro una pagina che scorre già crea due scroll uno dentro l'altro: preferisci `WrapContent`.
- **Il documento enorme** (576.000 caratteri, 12.000 blocchi malformati) arriva a `Ready` con altezza limitata anche a scala 2,0. Oltre `FluidWebContentDefaults.MaxHtmlCharacters` (4 000 000) il contenitore **rifiuta prima di copiare
  l'HTML** (`FluidWebFailure.ContentTooLarge`): l'app mostra la sua versione testuale.

## Scurimento, scala del testo, stampa

- **`darkening = true` abilita lo scurimento algoritmico** se il provider lo supporta; `false` mostra l'**originale** (il tema scuro e gli stili scuri dichiarati dal documento restano quelli del provider). Una newsletter bianca
  in tema scuro è un faro: lo scurimento la scurisce, a costo di colori non voluti dall'autore; l'app offre «Mostra originale». **Cambiare `darkening` ricrea la vista e il profilo e richiede di nuovo le risorse**: Chromium legge
  `isLightTheme` alla **creazione**, non quando cambia il tema del `Context` esistente. Un'app che cambia tema a runtime paga una ricreazione (e `onRequest` viene riconsultato).
- **`textScale` moltiplica sempre la scala del carattere di sistema** (`fontScale`): a font di sistema 1,3 e `textScale` 1,5 il testo è a 195%. Cambiare solo la scala **non ricrea** la vista né ricarica le risorse. Il pizzico è
  opzionale (`pinchZoom`). La scala vive nell'intervallo 1%–1000%.
- **Stampa.** `state.printAdapter(jobName)` dà un `PrintDocumentAdapter` per `PrintManager.print` dell'`Activity` dell'app; è `null` senza una prima pittura valida. **L'app deve tenere montato il contenitore per la durata
  della stampa.**

## Stati, segnaposto, errore

`FluidWebPhase`: `Empty` (html vuoto), `Loading`, `Ready` (la **prima pittura confermata dal renderer**, non il solo completamento della richiesta HTML), `Error`. `placeholder` resta **sopra** la vista fino alla prima pittura
(di serie 6 righe di scheletro: lo spazio c'è, la lista non salta); l'HTML non viene copiato sul thread principale. `failure` riceve il `FluidWebFailure` e lo disegna l'app. Lo stato è **effimero**: dopo la ricreazione di una
`Activity` il contenuto va fornito di nuovo dall'app (**mai nel `Bundle`**). Tutte le azioni e le letture UI sul thread principale. La creazione di una `WebView` richiede comunque il thread principale: nessuna promessa di
latenza di avvio a freddo.

## Rifrazione del vetro

Una WebView è una superficie nativa, e di norma il vetro che la copre rifrange il vuoto. Qui **è verificato su emulatore** (`glassBackdropActuallyContainsWebViewPixels`): un pannello di vetro sopra il contenitore campiona i pixel
della WebView, come campiona una pagina di Compose. Vale come **prova di principio**: il costo e l'aspetto su un telefono vero si guardano in mano come ogni vetro (D1 in `docs/10-coerenza.md`).

## Prestazioni

> **SEGNAPOSTO — F1.7b.** Questa sezione è in sospeso: un altro gruppo (F1.5b, QLT-02) sta lavorando a prewarm del provider, riuso e budget di prima pittura, e non è ancora integrato in questo ramo. F1.7b la completa con i numeri
> misurati e il contratto del riuso. **Non descriverla prima**: ciò che è certo oggi è solo che il costo di creazione di una `WebView` è sul thread principale, che il contenitore **ricrea** vista e profilo per `darkening`,
> `retry` e renderer morto, e che la misura esplorativa raccolta nel gruppo (`FluidWebContentPerformanceTest`: newsletter da 2.346 caratteri, HTML malformato da 576.024) **non** raggiungeva ancora QLT-02 alla consegna di F1.5.
> Da inserire qui: prewarm (chi lo chiama, quando), riuso e **pulizia prima del riuso**, profili per tema, budget di caratteri, telemetria, e cosa significa «prima pittura» in numeri.

## Trappole

- **Non è un sanificatore.** Passare HTML non filtrato e servire ogni richiesta «per far vedere le immagini» fa di un contenitore chiuso un client HTTP aperto. Serve solo ciò che l'app ha deciso, con il client privo di identità.
- **`onRequest` gira su thread di I/O, anche in parallelo**: dev'essere thread-safe e finito; non restituire stream condivisi fra richieste (la WebView li chiude). Gli schemi inline personalizzati (per esempio `cid:`) passano dallo
  stesso gestore; `data:`, `blob:`, `about:` sono interni al renderer.
- **Cambiare `onRequest` non ricarica il documento.** Un consenso dato dopo si applica con `retry()`.
- **`darkening` ricrea tutto.** Non legarlo a un valore che cambia spesso.
- **Il link è già «vero»**: l'engine non ti dice «sospetto». `visibleText` può essere `null` (il renderer non sempre lo fornisce senza eseguire script).
- **Nessuna copia del documento in `rememberSaveable`**: dopo la ricreazione il contenuto lo ridai tu.
- **Dipendenza nuova.** `engine-ui` dipende dalla 3.1.0 da `androidx.webkit` (`implementation`): un'app che compila `engine-ui` dai sorgenti ottiene `androidx.webkit` nel build, anche se non usa mai `FluidWebContent`. Per le sole
  prove strumentate della galleria serve Espresso 3.7.0 esplicito (la versione transitiva chiamava `InputManager.getInstance`, rimosso su API 37).
- **Il tag del provider.** `MULTI_PROFILE` non c'è su ogni WebView: dillo nei limiti dell'app («aggiorna Android System WebView») invece di scoprirlo in produzione.

## Cosa è verificato dai test

- **`FluidWebContentStateTest`** (`engine-ui`, JVM, 5): vuoto, caricamento, pittura, errore e rilascio; i callback di un renderer smontato non cambiano il documento nuovo; `retry` crea una revisione nuova e un nuovo proprietario azzera le misure; uno stato non può avere due viste.
  **`FluidWebContentTest`** (`sample`, Robolectric, 2): un contenuto vuoto non entra in `Loading`; un documento troppo grande usa lo slot `failure` dell'app senza creare la WebView; il vuoto a testo al 200%, scuro e movimento ridotto.
- **`FluidWebContentInstrumentedTest`** (`sample/androidTest`, su **emulatore** API 37, WebView Google 149, **8/8 passati**): un documento ostile non può inviare richieste né eseguire script (`hostileDocumentCannotSendRequestsOrExecuteScripts`);
  gli stream serviti usano solo le intestazioni dell'app e le risorse inline passano dallo stesso gestore; i tocchi riportano la destinazione vera e il testo visibile **senza navigare**; un `javascript:` non esegue nulla;
  il tema ricrea la vista e la scala del font si aggiorna **senza** ricaricare le risorse; la morte del renderer è segnalata e `retry` usa vista e profilo nuovi; un documento enorme e malformato ha altezza limitata a 200% e **può rimpicciolirsi**,
  con uno swipe vero che scorre dentro una `LazyColumn`; **il vetro contiene davvero i pixel della WebView**.
- **`FluidWebContentPerformanceTest`**: una misura **esplorativa** della prima pittura (non impone soglie): vedi «Prestazioni».
- **Non provato da test**: la qualità visiva dello scurimento su documenti reali (si guarda: `07-scuro-corretto`, `08-originale-corretto` nelle evidenze del gruppo), la stampa fino alla stampante, il pizzico a dita vere.

## Dove guardare

| file | cosa c'è |
|---|---|
| `engine-ui/.../ui/fluid/FluidWebContent.kt` | il composable, i tipi (`FluidWebRequest`, `FluidWebResponse`, `FluidWebLink`, `FluidWebPhase`, `FluidWebFailure`, `FluidWebSizing`), `FluidWebContentDefaults` (con la CSP), `FluidWebContentState`, la vista chiusa e il suo profilo |
| `sample/.../catalog/WebContentDemo.kt` | il call-site vero: campioni ostili (scheda semplice, newsletter larga, HTML ostile, lungo e malformato), scurimento, scala 200%, stampa, retry |
| `sample/src/androidTest/.../FluidWebContentInstrumentedTest.kt` | le otto prove strumentate (rete chiusa, link, renderer, altezza, vetro) |
| `docs/fase-1/evidenze/gc/` (nel repo di Pampa Mail) | gli screenshot e il verbale della verifica su emulatore |
