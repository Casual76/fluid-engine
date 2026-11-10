# Contenuto web: FluidWebContent

Un contenitore per **HTML locale che arriva da qualcun altro** (il corpo di una mail, una newsletter, le note di una versione scritte altrove, un documento esportato), **chiuso per costruzione**: JavaScript spento, nessuna
rete nativa, nessuna navigazione, ogni richiesta consegnata all'app, che di serie la blocca, e i suggerimenti di rete tolti prima che il documento arrivi al renderer (il sigillo, dalla 3.1.1). Famiglia «Contenuto» ([`contenuto.md`](contenuto.md) ha l'indice). File: `engine-ui/.../ui/fluid/FluidWebContent.kt`. Dalla 3.1.0
(Pampa Mail, F1.5, gruppo G-C). Catalogo delle firme: [`../api/contenuto-web.md`](../api/contenuto-web.md) (generato).

**Modello mentale in due frasi.** Il contenitore è un **recinto**: non decide cosa è sicuro mostrare, impedisce che quello che mostra faccia qualcosa. L'engine non sa cos'è una mail né quale immagine è un tracker: **sanificare
l'HTML, decidere quali risorse caricare e verificare dove portano i link sono dell'app**, e restano dell'app anche quando il contenitore le dà in mano un byte alla volta.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| mostrare un HTML di cui non mi fido (una mail, una newsletter) | `FluidWebContent(html, onLinkClick)`: rete e script chiusi di serie |
| il testo di una mail in una pagina con altro contenuto | `sizing = FluidWebSizing.WrapContent(maxHeight)` (di serie 2048 dp): cede lo scroll alla pagina finché il contenuto entra |
| un documento lungo in una viewport fissa | `FluidWebSizing.Viewport(height)` (di serie 600 dp), meglio in una pagina senza altro scroll |
| caricare le immagini che l'utente ha permesso | `onRequest = { req -> … FluidWebResponse.Serve(mime, encoding) { stream } }` col **tuo** client, senza cookie né referer |
| il foglio di stile esterno di una newsletter (per esempio i suoi caratteri) | niente di speciale: `<link rel="stylesheet">` resta una richiesta per `onRequest`, come un'immagine. Gli altri `<link>` no: vedi «Il sigillo» |
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
- **Nessuna rete nativa.** Ogni risorsa (immagine, foglio di stile, font, video) è una **richiesta consegnata all'app** (`onRequest`); l'app o serve byte o blocca. **Non si può restituire `null`**
  (autorizzerebbe il caricamento nativo): `FluidWebResponse.Block` è una risposta vuota locale. Quello che in un documento **non** è una richiesta, cioè i suggerimenti di rete (`preconnect`, `dns-prefetch`), lo toglie **il
  sigillo** prima che il documento arrivi al renderer (sezione sotto, dalla 3.1.1). La garanzia, nei termini in cui è provata: con i documenti del censimento e delle varianti travestite, sull'emulatore di prova, **nessuna
  connessione TCP e nessuna richiesta DNS** parte senza passare da `onRequest`. Non è una promessa su ogni versione futura del provider: è ciò che le prove osservano, e le prove si rilanciano.
- **Nessuna navigazione interna.** Toccare un link **non naviga**: `onLinkClick` riceve la **destinazione vera** e, quando il renderer riesce a fornirlo senza eseguire script, il **testo visibile** (`FluidWebLink.url`,
  `visibleText`). È il pezzo che serve contro il phishing («https://banca.it» che porta altrove): l'app confronta e decide. Un `javascript:` non esegue niente.
- **Nessuna identificazione.** Lo `User-Agent` è `WebContent/1.0`: né dispositivo, né versione, né app.
- **Il documento è servito su un'origine casuale `.invalid`** (`https://<uuid>.invalid/document`), con CSP `sandbox` e `no-store`. **Nessun `baseUrl` pubblico**: un'origine controllata dall'HTML potrebbe condividere dati con
  altre viste. Il documento si legge come byte locali, non con `loadDataWithBaseURL` (che avrebbe allentato gli stessi header).
- **Profili e cookie isolati.** Ogni istanza ha un **profilo dedicato, mai riutilizzato** (`fluid-content-<uuid>`), cookie e cache compresi; il `CookieManager` e lo `WebStorage` globali non si toccano mai. **Senza
  `MULTI_PROFILE` il contenitore fallisce chiuso** (`FluidWebFailure.IsolationUnavailable`) invece di cambiare il `CookieManager` globale o condividere la cache con le altre WebView dell'app: un'app con un provider vecchio
  mostra il testo alternativo, non una vista meno isolata. **Il profilo non sparisce alla chiusura del documento**: AndroidX vieta di cancellare un profilo caricato nello stesso processo, e sul provider di prova un profilo resta
  caricato anche dopo `destroy()`. Resta fino alla fine del processo dell'app e viene eliminato all'avvio successivo, al primo documento (pochi per volta: vedi «Chiudere un documento»). **Non promettere una cancellazione
  immediata**, e leggi il costo dei profili rimasti in «Prestazioni».
- **Il renderer che muore si recupera.** `FluidWebFailure.RendererGone` arriva allo slot `failure`; `state.retry()` **distrugge e ricrea** la vista con un profilo nuovo (non rianima un renderer morto) e non salva né HTML né
  consensi fra istanze. `RendererGone` riguarda solo un renderer morto **da sé** con il documento ancora aperto: il renderer che il contenitore spegne alla chiusura (sotto) non è un errore e non arriva a nessuno.

## Chiudere un documento (3.1.1)

**Il difetto della 3.1.0.** Il provider dà a ogni profilo un processo renderer suo, e quel processo **sopravvive a `destroy()`** finché vive il processo dell'app. Con un profilo per documento, ogni documento aperto in una sessione
lasciava quindi un renderer vivo: decine di megabyte l'uno. Misurato su emulatore (2 GB, WebView Google 156): dopo 15 viste aperte e chiuse, 16 renderer vivi (le 15 più quello dell'app); 27 documenti esaurivano la memoria.

**Cosa fa ora il contenitore quando un documento si chiude** (cambio di `html`, `darkening`, `retry()`, uscita dalla composizione, rotazione, `Timeout`): ferma il caricamento, sostituisce il client con uno che non lascia passare
richieste e che dichiara **gestita** la morte del renderer, distrugge la vista e **solo dopo termina il processo renderer** (`WebViewRenderProcess.terminate()`). L'ordine è la garanzia: quando il processo muore non ha più una vista viva
a cui riportarlo, quindi né l'app né un documento aperto dopo ricevono un `RendererGone` per un documento che l'app ha chiuso. Il renderer si può terminare senza toccare nient'altro **proprio perché il profilo è di quella sola
vista**: le WebView dell'app (profilo predefinito, o altri profili suoi) stanno su altri processi e non se ne accorgono (provato: una WebView ordinaria dell'app, viva per tutto il ciclo, alla fine carica e disegna ancora e non ha
mai visto morire il suo renderer). Se la creazione della vista è fallita prima di ottenere il profilo dedicato, il renderer sarebbe quello dell'app: non viene nemmeno preso.

- **Un documento chiuso nei primi istanti** (sfogliando in fretta): `terminate()` risponde `false` finché il processo sta ancora partendo, e quel processo finirebbe di partire per restare orfano. Il contenitore riprova a intervalli
  crescenti (da 50 ms a 30 s, meno di un minuto in tutto); ogni tentativo è una chiamata che non fa nulla se il processo non c'è. Non tiene in vita né la vista né l'`Activity`: solo il riferimento al processo.
- **Dove il provider non sa farlo** (`GET_WEB_VIEW_RENDERER` o `WEB_VIEW_RENDERER_TERMINATE` mancanti, o renderer nello stesso processo dell'app) il contenitore si comporta come nella 3.1.0: nessun errore, e il renderer resta fino alla
  fine del processo. Non è un requisito di sicurezza (il documento è già chiuso), quindi qui non si fallisce chiusi.
- **I profili lasciati dal processo precedente** si eliminano al primo documento del processo successivo, **pochi per giro del thread principale** (l'API di AndroidX va chiamata lì). Dopo una sessione lunga sono centinaia:
  cancellarli tutti insieme, com'era nella 3.1.0, fermava il thread principale per circa 1,5 secondi con 300 profili (emulatore); a rate il primo documento non li aspetta.

## Il sigillo: i suggerimenti di rete (3.1.1)

**Il difetto della 3.1.0.** `<link rel="preconnect" href="https://host">` faceva aprire a Chromium **due connessioni TLS native** verso quell'host, e `<link rel="dns-prefetch">` una **richiesta DNS**, senza una sola chiamata a
`onRequest`. Con un host diverso per ogni destinatario, a un mittente bastava per sapere che il documento era stato aperto, quando e da quale indirizzo, anche con i contenuti remoti rifiutati. Le tre chiusure di allora non
c'entravano: un suggerimento **non è un caricamento**, il renderer lo passa al processo browser, che apre il socket o risolve il nome senza creare una richiesta. Quindi `blockNetworkLoads` (che ferma le richieste),
`setNetworkAvailable(false)` (che cambia solo `navigator.onLine`) e la CSP (che non ha una direttiva per i suggerimenti) non lo vedevano. Anche **`<iframe srcdoc>`** apriva connessioni: `frame-src 'none'` non ferma un documento
scritto dentro un attributo, e dentro ci stava un `preconnect`, anche codificato a entità.

**Perché non un filtro che legge l'HTML.** WebView non ha un'impostazione che spegne i suggerimenti (le due leve del motore che esistono sono dichiarate sotto, e non bastano). Resta riscrivere il documento, e un filtro che
«capisce» l'HTML quasi come il browser è proprio il modo in cui questi filtri si bucano. Chromium, poi, ha **due** analizzatori che non concordano, e le prove lo mostrano: con `rel=stylesheet rel=preconnect` l'albero tiene
il primo `rel` e lo scanner di precaricamento l'ultimo (e apre il socket); dentro `<svg>` l'albero non crea un link HTML e lo scanner sì. Il sigillo si appoggia a un fatto solo, vero per entrambi e verificato col vero
Chromium: **un elemento `link`, `meta` o `iframe` nasce soltanto dalla sequenza letterale `<` + nome** (maiuscole e minuscole ASCII). Niente entità, NUL, caratteri invisibili o codifica la sostituiscono. E il nome deve finire
lì: **se prosegue con una lettera o una cifra ASCII è un altro elemento** (`<metadata>` degli SVG in linea, `<linkx>`, `<iframes>`), che Chromium non tratta come `meta`, `link` o `iframe` né nell'albero né nello scanner di
precaricamento (provato: nessun socket, nessun elemento), e che il sigillo **lascia intatto**. Neutralizzarlo romperebbe il documento: `<metadata>` diventerebbe un tag che il suo `</metadata>` non chiude più, e le forme dell'SVG
che lo seguono non verrebbero disegnate. Ogni altra continuazione (`<link-x`, `<meta:x`, un NUL, una lettera non ASCII, la fine del testo) resta trattata come il nome intero, per prudenza.

**Cosa fa**, in una sola passata, **senza guardare il contesto** (vale anche dentro commenti, CDATA, attributi, `<style>`, `<svg>`, `<template>` e tag non chiusi):

| nel documento | diventa | perché |
|---|---|---|
| `<link` | `<basefont` | un elemento vuoto, ammesso in `head` e in `body` come `link`, che Chromium non implementa: l'albero conserva la forma, gli attributi restano attaccati a qualcosa che non fa nulla |
| `<meta` | `<basefont` | lo stesso; così spariscono gli `http-equiv` (`refresh`, `x-dns-prefetch-control`, una CSP del documento, `origin-trial`, `accept-ch`) |
| `<iframe`, `</iframe` | `<noframes`, `</noframes` | testo grezzo nascosto, com'era il contenuto di ripiego dell'iframe: **nessun documento annidato**, nemmeno da `srcdoc` |

Prima dell'originale neutralizzato il sigillo scrive un elemento **canonico**, composto da lui e non copiato, solo in tre casi utili e innocui:

- **un foglio di stile semplice**: `rel` uguale a `stylesheet` e basta, con `href` e, se ci sono, `media` e `title`. Diventa `<link rel="stylesheet" href="…">` e resta una richiesta per `onRequest`;
- `<meta name="viewport" content="…">`;
- `<meta name="color-scheme" content="…">`.

**In caso di dubbio rinuncia**: attributo duplicato, entità che non sia numerica o una delle cinque dell'XML, virgolette o spazi mancanti, NUL, `rel` con più parole (`alternate stylesheet`, `stylesheet preconnect`), `type`
diverso da `text/css`, `disabled`, `integrity`, `crossorigin` (il contenitore non può onorarli: le intestazioni della risposta sono sue), `<meta>` con `http-equiv`. Un errore di lettura può quindi solo **togliere un foglio di
stile** o sbagliarne l'indirizzo (che passa comunque da `onRequest`); **non può produrre un suggerimento**, perché nell'elemento canonico `rel` è una costante e i valori sono riscritti con le virgolette protette. Prima di
caricare, il contenitore ricontrolla l'esito con una grammatica indipendente; se non torna non carica nulla (`FluidWebFailure.Unavailable`). **Un documento senza `<link`, `<meta` e `<iframe` arriva al renderer identico, byte
per byte.**

**Cosa cambia per un documento rispetto alla 3.1.0** (senza effetto visibile su una mail ordinaria): non esistono più icone (`rel="icon"`), `preload`, `prefetch` (erano richieste per `onRequest`); un `<meta http-equiv>` non ha
più effetto, compresa una CSP scritta dal documento (quella del contenitore resta, in intestazione); `charset` e `referrer` in `<meta>` erano già ininfluenti (UTF-8 e `no-referrer` sono in intestazione); **gli iframe non
vengono resi**, nemmeno `srcdoc`; un foglio con `integrity` o `crossorigin` non viene richiesto. Il testo `<link`, `<meta` o `<iframe` scritto **dentro** `<textarea>`, `<title>`, `<xmp>` o un commento cambia anch'esso (lì è
solo testo): una mail che mostra codice lo scrive con `&lt;` e non è toccata.

**Le due leve del motore**, usate come strati in più e dichiarate per quello che sono: l'intestazione `X-DNS-Prefetch-Control: off` sul documento (spegne la risoluzione anticipata delle ancore; sul provider di prova le
ancore non risolvono nomi nemmeno con il `<meta>` acceso, quindi **non c'è una prova differenziale** che serva) e il precaricamento speculativo di `androidx.webkit` dichiarato spento (era già il valore di serie; la prova
controlla che lo sia). `frame`, `object` ed `embed` non hanno `srcdoc`: hanno un indirizzo, che la CSP rifiuta (`frame-src`, `object-src`), e il censimento lo prova coi socket.

**Il censimento dei canali** (`FluidWebContentNetworkCensusTest`, 55 documenti minimi, un socket locale che conta le connessioni; emulatore API 37, WebView Google 156):

| canale | nella 3.1.0 | dalla 3.1.1 | cosa lo chiude |
|---|---|---|---|
| `<link rel="preconnect">` (https, http, con `crossorigin`, nel `body`, insieme a `dns-prefetch`) | **2 connessioni TLS native** (1 TCP con http), nessuna chiamata a `onRequest` | 0 | il sigillo |
| `<link rel="dns-prefetch">`, `preconnect` verso un nome | **1 richiesta DNS** | 0 | il sigillo |
| `<iframe srcdoc>` con un suggerimento dentro, anche scritto a entità o annidato due volte | **2 connessioni TLS native** | 0 | il sigillo (`iframe` → `noframes`) |
| 43 varianti travestite su 76 (maiuscole, tabulazioni e avanzamenti di pagina, entità in `rel` e in `href`, `rel` duplicato, più parole, NUL in un altro attributo, dentro `svg`, `math`, `noscript`, tabelle, `select`, radice ombra dichiarativa, dopo commenti e CDATA, dopo 150.000 caratteri) | **2 connessioni** ciascuna | 0 | il sigillo |
| `preload`, `prefetch`, `icon`, `alternate stylesheet` | richiesta consegnata a `onRequest` | l'elemento non esiste più | il sigillo |
| `<link rel="stylesheet">` | richiesta consegnata a `onRequest` | **uguale** | `onRequest` |
| `modulepreload`, `prerender`, `manifest`, `apple-touch-icon`, `search`, `canonical`, `pingback` | niente | niente | il provider non li carica; il sigillo |
| `<meta http-equiv>`: `refresh`, `Link`, `x-dns-prefetch-control` con ancore, `report-uri` di una CSP, `origin-trial`, `accept-ch` | niente (0 connessioni, 0 DNS) | niente | `sandbox` e provider; il sigillo; l'intestazione DNS |
| script, `fetch`, `sendBeacon`, `WebSocket`, regole di speculazione | niente | niente | JavaScript spento, CSP, precaricamento speculativo spento |
| immagini (`src`, `srcset`, `picture`, pigre, `input type=image`, sfondi), CSS (`@import`, `@font-face`, `url()`, `image-set`), SVG esterni, video e audio (anche HLS), `track` | richiesta consegnata a `onRequest`, 0 connessioni, 0 DNS | uguale | l'intercettazione |
| `object`, `embed`, `frame`, `iframe src`, anche verso un documento `data:` con un suggerimento codificato nell'indirizzo; `base href` | niente | niente | la CSP (`frame-src`, `object-src`, `base-uri`) |
| un SVG usato come immagine con un `<link>` dentro | niente | niente | Chromium isola i documenti-immagine |
| toccare un link, `ping`, `target="_blank"`, `download`, un modulo (GET e POST), una mappa immagine; pressione lunga su link e immagine | 0 connessioni | uguale | nessuna navigazione, CSP |

**Limiti dichiarati.** Le prove girano su **un** provider (WebView Google 156, emulatore API 37): una versione diversa può avere canali diversi, e per questo le prove sono scritte per essere rilanciate, non per essere
citate. Il DNS si osserva dal **contatore di sistema** (`dumpsys connmetrics`), che dice quante risoluzioni e non quali: la prova usa nomi nuovi a ogni tentativo e ripete per distinguere il rumore di altre app. Il traffico **del
provider** che non dipende dal documento (aggiornamento dei componenti, variazioni, statistiche di WebView) non è nel recinto: il documento non lo può indirizzare e il contenitore non lo governa. Un elemento nuovo di una
versione futura di Chromium, che aprisse la rete senza passare dal caricatore, non sarebbe coperto finché il censimento non lo aggiunge.

## Cosa resta all'app (e non è poco)

Il contenitore **non è un sanificatore** (lo dice anche il KDoc): `FluidWebResponse.Serve` non rende sicuro ciò che serve.

| dell'app | perché non può essere dell'engine |
|---|---|
| **sanificare l'HTML e il CSS** prima di passarli (e il corpus di prova) | l'engine non sa cosa un'app considera accettabile (un `<form>` in una newsletter? i colori di fondo?). Il sigillo toglie **solo** ciò che uscirebbe dal recinto senza passare da `onRequest`: non toglie tracker, moduli, stili invadenti né testo ingannevole |
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
(di serie 6 righe di scheletro: lo spazio c'è, la lista non salta); l'HTML non viene copiato sul thread principale. `failure` riceve il `FluidWebFailure` e lo disegna l'app: `IsolationUnavailable` (il provider non sa isolare il profilo), `RendererGone` (il renderer è morto), `Timeout` (nessuna prima pittura entro 15 secondi), `ContentTooLarge` (oltre 4 milioni di caratteri), `Unavailable` (la creazione della `WebView` ha lanciato un'eccezione: per esempio un provider mancante o disattivato). Lo stato è **effimero**: dopo la ricreazione di una
`Activity` il contenuto va fornito di nuovo dall'app (**mai nel `Bundle`**). Tutte le azioni e le letture UI sul thread principale. La creazione di una `WebView` richiede comunque il thread principale: nessuna promessa di
latenza di avvio a freddo.

## Rifrazione del vetro

Una WebView è una superficie nativa, e di norma il vetro che la copre rifrange il vuoto. Qui **è verificato su emulatore** (`glassBackdropActuallyContainsWebViewPixels`): un pannello di vetro sopra il contenitore campiona i pixel
della WebView, come campiona una pagina di Compose. Vale come **prova di principio**: il costo e l'aspetto su un telefono vero si guardano in mano come ogni vetro (D1 in `docs/10-coerenza.md`).

## Prestazioni

**L'engine non promette nessuna latenza.** Né di avvio, né di prima pittura, né di scorrimento: dipendono dal provider WebView installato sul telefono, dal telefono e dal documento, e il contenitore non ha niente che li renda
prevedibili. Gli obiettivi di prodotto (primo contenuto ≤ 300 ms, HTML tipico completo ≤ 800 ms, mail enorme leggibile entro 1 s) sono **traguardi da misurare su un telefono vero** (voce D-F1-01 del backlog del dispositivo),
non garanzie dell'engine: un'app che li promette ai suoi utenti li promette lei. Cosa è certo, dal codice:

- **`Ready` è la prima pittura, non il completamento della richiesta.** Scatta quando il renderer conferma di aver dipinto il primo contenuto (`onPageCommitVisible` + il callback di stato visuale) e solo allora il
  `placeholder` si toglie. È il momento giusto per togliere uno scheletro o far comparire altro: da lì il documento si vede, anche se le immagini bloccate o servite dall'app non sono ancora arrivate. Non è «il documento è
  finito di caricare» e non dice niente su immagini e font.
- **La creazione della `WebView` è sul thread UI** (non c'è modo di farla altrove) e il suo costo è dell'app che compone il contenitore (di norma la prima `WebView` del processo paga anche il caricamento del provider, e non si sa quanto: dipende dal telefono). Fuori dal thread UI
  passa solo il lavoro che può passarci: l'HTML si converte in byte su `Dispatchers.Default`, **mai** sul thread principale, e `onRequest` gira su thread di I/O.
- **Niente riuso e niente preriscaldamento.** Ogni istanza crea una vista nuova e un profilo nuovo, mai riutilizzato (è parte del contratto chiuso: nessun cookie, cache o stato passa da un documento al successivo), e
  **nessun pool globale** tiene `WebView` o `Activity` in giro. L'engine non ha né un `prewarm()` né una vista da «riscaldare», e `FluidWebContent` non accetta una `WebView` fornita dall'app: chi vuole ammortizzare l'avvio non ha un'API per farlo.
- **Cosa resta di un documento chiuso.** Il processo renderer no: dalla 3.1.1 viene terminato (sopra). **Il profilo sì, fino alla fine del processo dell'app**, e non è gratis. Misurato su emulatore (API 37, 2 GB, WebView Google 156),
  per ogni vista creata: circa **24 descrittori di file, 2 thread, 0,7 MB di memoria** nel processo dell'app e **90 KB di file** su disco. Con 301 viste aperte e chiuse in un solo processo (200 documenti, alcuni ricreati per
  `retry` e cambio di tema): memoria dell'app da 273 a 490 MB, thread da 61 a 675, descrittori da 265 a 7.518, 26 MB su disco; i processi renderer, intanto, restano quelli dell'app ospite, mentre nella 3.1.0 ne sarebbe rimasto uno per vista. Sono numeri
  **indicativi** (emulatore): l'ordine di grandezza è quello, i valori veri si misurano su un telefono. Tutto torna a zero alla fine del processo.
- **Perché non si riusa un profilo, per ora.** Riusare pochi profili terrebbe piatte quelle cifre (provato: 151 viste con 3 profili, descrittori e thread fermi), ma solo a patto che fra due documenti sullo stesso profilo non passi
  niente. Con vista distrutta, renderer terminato e `deleteBrowsingData` (cache, cookie, storage) passa una cosa sola, che il provider non lascia cancellare: **l'elenco degli indirizzi visitati del profilo**. Contiene gli indirizzi
  casuali dei documenti precedenti, che un documento non può conoscere; ma una pagina con script può aggiungervi indirizzi suoi (`history.pushState`, misurato con una WebView di prova), e quindi potrebbe farlo un renderer compromesso. Finché questo non è deciso, il contratto resta «un profilo per documento».
- **Cosa ricrea la vista** (e quindi paga di nuovo la creazione e riconsegna le risorse a `onRequest`): un `html` diverso, `darkening`, `state.retry()`, un renderer morto. **Non** la ricrea `textScale`, né cambiare `onRequest`
  (che non ricarica nemmeno il documento). Una lista con un `FluidWebContent` per riga crea tante `WebView` quante righe sono composte: tienine montato uno per volta nel dettaglio, o una sola vista per un documento
  lungo, invece di una per anteprima.
- **I tetti sono quelli della memoria e del tempo**: HTML fino a 4.000.000 di caratteri (oltre, `ContentTooLarge` prima di copiare nulla), altezza fisica fino a 4096 px, e se la prima pittura non arriva entro **15 secondi** il
  contenitore passa a `FluidWebFailure.Timeout` (slot `failure`, `retry()` riparte).
- **Le misure sull'emulatore non valgono.** `FluidWebContentPerformanceTest` (`sample/androidTest`, solo su dispositivo o emulatore) stampa nel log `FluidWebTiming` la prima pittura di una newsletter da 2.346 caratteri e di un
  HTML malformato da 576.024; **non impone soglie** e non decide niente: sull'AVD il renderer ha tempi e memoria che un telefono vero non ha, e QLT-02 si giudica solo con la prova D-F1-01. Non citare quei numeri come tempi
  dell'engine.

## Trappole

- **Un foglio di stile è una richiesta come un'immagine.** `<link rel="stylesheet">` sopravvive al sigillo e arriva a `onRequest`: servirlo senza la stessa politica delle immagini dice al mittente che il documento è stato
  aperto. Gli altri `<link>` (icone, `preload`) non arrivano più: non aspettarli in `onRequest`.
- **Gli iframe non ci sono.** Nemmeno `srcdoc`: un documento che contava su un iframe mostra il resto e basta.
- **Ogni documento aperto lascia un profilo caricato fino alla fine del processo.** Non più un renderer (dalla 3.1.1 il contenitore lo termina alla chiusura: nella 3.1.0 ne restava uno per documento, e 27 documenti di seguito
  esaurivano un emulatore da 2 GB), ma il profilo sì: circa 24 descrittori di file, 2 thread e 0,7 MB per vista sull'emulatore di prova, cioè circa 90 MB, 375 thread e 3.900 descrittori dopo 150 viste. Un'app che in una
  sessione apre centinaia di documenti lo vede: misuralo su un telefono vero (`dumpsys meminfo`, `/proc/<pid>/fd`), e ricorda che ricreare la vista (`html` instabile, `darkening`, `retry`) conta come un documento in più.
- **Non è un sanificatore.** Passare HTML non filtrato e servire ogni richiesta «per far vedere le immagini» fa di un contenitore chiuso un client HTTP aperto. Serve solo ciò che l'app ha deciso, con il client privo di identità.
- **`onRequest` gira su thread di I/O, anche in parallelo**: dev'essere thread-safe e finito; non restituire stream condivisi fra richieste (la WebView li chiude). Gli schemi inline personalizzati (per esempio `cid:`) passano dallo
  stesso gestore; `data:`, `blob:`, `about:` sono interni al renderer.
- **Cambiare `onRequest` non ricarica il documento.** Un consenso dato dopo si applica con `retry()`.
- **`darkening` ricrea tutto.** Non legarlo a un valore che cambia spesso.
- **Il fuoco non passa ad altro quando la vista se ne va.** Un tocco (un link, una selezione) dà il fuoco Android alla WebView. Quando il contenitore si ricrea (`html`, `darkening`, `retry`) o l'app lo toglie dalla
  composizione, il fuoco **viene lasciato, non spostato**: come per un elemento Compose focalizzato che sparisce. Prima della correzione, dentro una `LazyColumn` questa rimozione chiudeva l'app
  (`ComposeRuntimeError`: «pending composition has not been applied»). Se dopo serve il fuoco altrove (tastiera fisica, TalkBack), lo chiede l'app.
- **Il link è già «vero»**: l'engine non ti dice «sospetto». `visibleText` può essere `null` (il renderer non sempre lo fornisce senza eseguire script).
- **Nessuna copia del documento in `rememberSaveable`**: dopo la ricreazione il contenuto lo ridai tu.
- **Dipendenza nuova.** `engine-ui` dipende dalla 3.1.0 da `androidx.webkit` (`implementation`): un'app che compila `engine-ui` dai sorgenti ottiene `androidx.webkit` nel build, anche se non usa mai `FluidWebContent`. Per le sole
  prove strumentate della galleria serve Espresso 3.7.0 esplicito (la versione transitiva chiamava `InputManager.getInstance`, rimosso su API 37).
- **Testo al 200% + documento enorme non sono una promessa su ogni provider.** Il test strumentato lo prova su un'unica combinazione (WebView Google 149, emulatore API 37): un telefono con un provider più vecchio o più
  povero può metterci di più, o arrivare al `Timeout`. Prevedi sempre lo slot `failure` (con il testo semplice del corpo), non solo il caso felice.
- **Cambiare `html` ricrea la vista.** Non è un aggiornamento in place: un `html` che cambia a ogni composizione (una stringa ricostruita) ricrea `WebView` e profilo a ogni passata. Passa una stringa stabile.
- **Il tag del provider.** `MULTI_PROFILE` non c'è su ogni WebView: dillo nei limiti dell'app («aggiorna Android System WebView») invece di scoprirlo in produzione.

## Cosa è verificato dai test

- **`FluidWebContentStateTest`** (`engine-ui`, JVM, 5): vuoto, caricamento, pittura, errore e rilascio; i callback di un renderer smontato non cambiano il documento nuovo; `retry` crea una revisione nuova e un nuovo proprietario azzera le misure; uno stato non può avere due viste.
  **`FluidWebContentTest`** (`sample`, Robolectric, 2): un contenuto vuoto non entra in `Loading`; un documento troppo grande usa lo slot `failure` dell'app senza creare la WebView; il vuoto a testo al 200%, scuro e movimento ridotto.
- **`FluidWebSealTest`** (`engine-ui`, JVM, 15): il sigillo sul testo. Un documento senza i tre tag è la stessa istanza; i suggerimenti travestiti non lasciano un `<link`; il foglio di stile semplice torna in forma canonica e quello
  dubbio no; `viewport` e `color-scheme` restano e ogni `http-equiv` no; nessun `iframe`; un nome che prosegue con una lettera o una cifra ASCII (`<metadata>`, `<linkx>`, `<iframes>`) resta intatto, e ogni altra continuazione no; circa 160.000 combinazioni sistematiche (nomi, separatori, modi di scrivere `rel`, chiusure, contesti) e 40.000 documenti di frammenti a
  caso, ognuno controllato con espressioni regolari scritte a parte e con il controllo del contenitore; documenti al limite di 4 milioni di caratteri, costruiti per far rileggere lo stesso testo, restano una passata.
- **`FluidWebContentNetworkCensusTest`** (`sample/androidTest`, 55, su emulatore API 37, WebView Google 156): il censimento della tabella sopra, un documento per canale, **zero connessioni** su un socket locale che conta anche
  quelle senza richiesta HTTP. Sulla 3.1.0 ne falliscono 7 (i cinque `preconnect` e i due `srcdoc`).
- **`FluidWebContentSealInstrumentedTest`** (`sample/androidTest`, 12): le 76 varianti travestite nel contenitore danno zero connessioni (sulla 3.1.0, 43 ne aprono); nessun canale fa salire il contatore DNS di sistema (sulla
  3.1.0 tre sì); **il vero analizzatore di Chromium**, interrogato sul DOM di 132 documenti sigillati, non trova un `link` che non sia il foglio canonico, un `meta` fuori dai due ammessi né un `iframe`, e conferma che le
  sequenze non letterali (NUL o caratteri invisibili nel nome, maiuscole Unicode, entità) e **i nomi più lunghi** (`<linkx>`, `<link1>`, `<metadata http-equiv>`, `<iframes srcdoc>`) non creano l'elemento e non aprono socket;
  un SVG in linea con `<metadata>` prima di una forma, sigillato, ha ancora la forma come figlia dell'SVG, e nel contenitore la forma arriva sullo schermo; l'albero di un `head` realistico conserva la forma; un foglio di stile servito
  dall'app si applica ancora. Due **sentinelle** caricano gli stessi suggerimenti in una WebView di prova senza sigillo e pretendono di **vedere** il socket e la richiesta DNS: se un giorno non li vedessero, gli zero delle altre
  prove non proverebbero più nulla. `tools/qa/web-network-tests.sh` lancia tutte le prove web e raccoglie esiti e log.
- **`FluidWebContentRendererLifecycleTest`** (`sample/androidTest`, 4, emulatore API 37, WebView Google 156): **60 documenti aperti e chiusi in un solo processo** in tutti i modi in cui un'app li chiude (documento successivo,
  `retry`, cambio di tema, uscita dalla composizione, chiusura mentre carica, rotazione), con una WebView ordinaria dell'app ospite viva per tutto il ciclo. Conta i processi renderer **dell'app** (`dumpsys activity services`
  incrociato con `ps`): a ogni controllo sono quelli dell'ospite più quello del documento aperto, e alla fine tornano **esattamente** al numero di partenza, senza margine; nessun `RendererGone` riportato, la WebView ospite non vede
  morire il suo renderer e alla fine carica e disegna; ogni vista ha avuto un profilo suo. Sulla 3.1.0 fallisce al primo controllo (16 renderer vivi dopo 10 documenti, cioè 15 viste, dove ne sono ammessi 2). Una seconda prova chiude 24 documenti **mentre stanno
  caricando**: nessun renderer orfano. Due prove sugli **avanzi** (da lanciare in un processo nuovo dopo molti documenti): il contenitore elimina i profili del processo precedente senza far aspettare il primo documento, e quanto
  costerebbe eliminarli tutti insieme. Le cifre di memoria, thread, descrittori e disco finiscono nel log `FluidWebLifecycle` come misure, **senza soglie**. `-e webDocuments N` cambia quanti documenti apre la prima prova.
- **`FluidWebContentInstrumentedTest`** (`sample/androidTest`, su **emulatore** API 37, WebView Google 156, **9/9 passati**): un documento ostile non può aprire connessioni né eseguire script (`hostileDocumentCannotSendRequestsOrExecuteScripts`, dalla 3.1.1 con un socket in ascolto al posto del conteggio delle richieste HTTP, che non vedeva un `preconnect`);
  gli stream serviti usano solo le intestazioni dell'app e le risorse inline passano dallo stesso gestore; i tocchi riportano la destinazione vera e il testo visibile **senza navigare**; un `javascript:` non esegue nulla;
  il tema ricrea la vista e la scala del font si aggiorna **senza** ricaricare le risorse; la morte del renderer è segnalata e `retry` usa vista e profilo nuovi; un documento enorme e malformato ha altezza limitata a 200% e **può rimpicciolirsi**,
  con uno swipe vero che scorre dentro una `LazyColumn`; **il vetro contiene davvero i pixel della WebView**; una WebView che ha preso il fuoco toccando un link, dentro una `LazyColumn`, sopravvive alla ricreazione per `darkening` e per `html` e alla rimozione dal genitore (`focusedViewInsideLazyColumnSurvivesRecreationAndRemoval`; senza la correzione F1.8a il processo cade).
- **`FluidWebContentPerformanceTest`** (`sample/androidTest`, 1, solo su dispositivo): una misura **esplorativa** della prima pittura, nel log `FluidWebTiming`; **non impone soglie** e non conta come prova di QLT-02: vedi «Prestazioni».
- **Non provato da test**: provider diversi da quello dell'emulatore; un provider senza `WEB_VIEW_RENDERER_TERMINATE` (lì il renderer resta, come nella 3.1.0); la memoria su un telefono vero; il traffico proprio del provider; la qualità visiva dello scurimento su documenti reali (si guarda: `07-scuro-corretto`, `08-originale-corretto` nelle evidenze del gruppo), la stampa fino alla stampante, il pizzico a dita vere.

## Dove guardare

| file | cosa c'è |
|---|---|
| `engine-ui/.../ui/fluid/FluidWebContent.kt` | il composable, i tipi (`FluidWebRequest`, `FluidWebResponse`, `FluidWebLink`, `FluidWebPhase`, `FluidWebFailure`, `FluidWebSizing`), `FluidWebContentDefaults` (con la CSP), `FluidWebContentState`, la vista chiusa e il suo profilo |
| `sample/.../catalog/WebContentDemo.kt` | il call-site vero: campioni ostili (scheda semplice, newsletter larga, HTML ostile, lungo e malformato), scurimento, scala 200%, stampa, retry |
| `engine-ui/.../ui/fluid/FluidWebSeal.kt` | il sigillo (`seal`, `isSealed`): interno, senza API pubblica; il perché di ogni regola è nel suo KDoc |
| `engine-ui/src/test/.../FluidWebSealTest.kt` | le prove JVM del sigillo |
| `sample/src/androidTest/.../FluidWebContentInstrumentedTest.kt` | le nove prove strumentate (rete chiusa, link, renderer, altezza, vetro, fuoco alla rimozione) |
| `sample/src/androidTest/.../FluidWebContentNetworkCensusTest.kt`, `FluidWebContentSealInstrumentedTest.kt` | il censimento dei canali; varianti travestite, DNS, DOM vero, sentinelle |
| `sample/src/androidTest/.../FluidWebContentRendererLifecycleTest.kt` | i renderer e i profili di decine di documenti aperti e chiusi in un processo; le misure di memoria, thread, descrittori e disco |
| `tools/qa/web-network-tests.sh` | tutte le prove web su un emulatore, un processo nuovo per lancio |
| `sample/src/androidTest/.../WebNetworkCorpus.kt`, `WebNetworkProbes.kt` | i documenti del censimento e delle varianti; il socket in ascolto, il contatore DNS, la WebView senza sigillo, l'analizzatore di Chromium interrogato da prova |
| `docs/fase-1/evidenze/gc/` (nel repo di Pampa Mail) | gli screenshot e il verbale della verifica su emulatore |
