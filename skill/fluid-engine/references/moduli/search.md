# engine-search ed engine-search-semantic

**La ricerca** (4.0, F8.22, `indice-ricerca` e `ricerca-in-tutte-le-app`): l'indice sul telefono per cercare nei dati
di un'app, come **Core Spotlight** (`CSSearchableIndex`, la ricerca di Note, Mail e Foto), e **«Cerca in Pampa»**, la
stessa domanda a tutte le app Pampa del telefono col ponte firmato che le app usano gia' con PampAI, coi risultati per
app come Spotlight. Pacchetti `dev.antigravity.fluidengine.search` e `dev.antigravity.fluidengine.search.semantic`.

Due moduli **opt-in**:

- **`engine-search`**: l'indice (SQLite **FTS4** del telefono, nessuna libreria nativa), l'italiano dell'engine, gli
  errori di battitura, gli inizi di parola, l'ordine per pertinenza, i pezzi di testo con le parole evidenziate, la
  cifratura con la cassaforte, l'aggiornamento a pezzi e il lavoro pesante in carica, l'aggancio al campo di ricerca che
  c'e' gia' (`FluidSearchSpec`), «Cerca in Pampa» e i vettori del significato (il confronto, non il modello). Porta
  `engine-ui`, `engine-ai-bridge`, `engine-vault` e WorkManager.
- **`engine-search-semantic`**: il **modello del significato** sul telefono, EmbeddingGemma 2 Text 270M con **LiteRT-LM**
  (`FluidLiteRtEmbedder`), lo stesso motore e lo stesso modello di MyCloud Gallery 2. A parte perche' pesa (sotto,
  «Il peso»). Il modello (157 MiB) **non** sta nel modulo: lo porta l'app, o lo presta un'altra app Pampa.

Catalogo delle firme: [`../api/search.md`](../api/search.md) e [`../api/search-semantic.md`](../api/search-semantic.md)
(generati). Il campo di ricerca e i suggerimenti sono in [`../componenti/barre.md`](../componenti/barre.md); il ponte
firmato in [`ai-bridge.md`](ai-bridge.md); la cassaforte in [`vault.md`](vault.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un indice delle mie note (o mail, comunicazioni, foto) | `FluidSearchIndex.open(context, FluidSearchSchema.titleAndBody("note"))`, poi `index.put(documenti)` |
| campi coi pesi (titolo 3, mittente 2, corpo 1) | `FluidSearchSchema(name, version, listOf(FluidSearchField("title", weight = 3f), …))` |
| cercare | `index.search(FluidSearchQuery(testo, kinds = setOf("nota")))` → `FluidSearchResults` (titolo e pezzo coi `highlights`) |
| il campo di ricerca della pagina gia' agganciato, coi gettoni dei tipi, le recenti e le parole che completano | `val state = rememberFluidSearchState(index)`, `FluidScreen(search = state.spec(prompt, kinds))` e `fluidSearchResults(state, kinds, onOpen)` nel contenuto |
| un dato da non lasciare leggibile sul disco (una password, un referto) | `FluidSearchField("secret", sensitive = true)` e `FluidSearchIndex.open(…, cipher = FluidSearchCipher.vault(section))` |
| tenere l'indice allineato ai dati, a pezzi | la mia `FluidSearchSource` e `FluidSearchIndexer(index, source).update()`; il resto in carica con `FluidSearchWork.schedule(context)` |
| far trovare i miei dati a «Cerca in Pampa» (e a PampAI) | `FluidSearchBridge.searchTool(index, FluidSearchExposure(…))` nel registry del mio `AiToolHostProvider`, e l'intent-filter `FluidSearchBridge.ActionSearch` sul provider (sotto) |
| la pagina «Cerca in Pampa» | `FluidPampaSearch.create(context)`, `rememberFluidPampaSearchState(search)`, `fluidPampaResults(state, onOpen)` |
| cercare per significato («la mail del dentista» → «Studio Odontoiatrico Rossi») | `engine-search-semantic`: `FluidLiteRtEmbedder(modello, cache)`, `FluidSemanticIndexer(index, embedder).update()` in carica, `rememberFluidSearchState(index, embedder)` |
| il significato senza il modello nella mia app | `FluidSearchBridge.remoteEmbedder(calls, authority)` verso l'app che lo tiene, che espone `FluidSearchBridge.embedTool(embedder)` |
| dati di prova (le pagine di prova, le prove) | `FluidSearchSamples` |

## Perche' SQLite FTS4 e non AppSearch

Le due strade erano AppSearch di androidx (`appsearch-local-storage` 1.1.0, il motore Icing di Google) e SQLite FTS
(FTS5 non e' garantito nel SQLite di Android; FTS4 c'e' su ogni telefono). La scelta e' **FTS4 con l'analisi e l'ordine
dell'engine**, per quattro motivi misurati:

| | SQLite FTS4 (scelto) | AppSearch, archivio locale |
|---|---|---|
| peso nell'APK | **0**: e' il SQLite del telefono | `libicing.so` 3,4 MiB in arm64-v8a, 3,6 MiB in x86_64, piu' 0,6 MB di classi (AAR 1.1.0, misurato il 25/11/2026) |
| l'italiano | l'analisi e' nostra: «l'indirizzo» sono due parole, «perché» = «perche», «riunioni» = «riunione» | le parole le taglia ICU (UAX #29): l'apostrofo fra due lettere **non** separa (WB6/WB7, «l'indirizzo» e' una parola sola; la prova `IcuWordsTest` scrive come le taglia), nessuna radice per i plurali |
| errori di battitura | il vocabolario dell'indice e la distanza di Damerau-Levenshtein | non ci sono (solo i suggerimenti per inizio, `searchSuggestion`) |
| cifratura | il testo conservato chiuso con la cassaforte, i campi riservati come impronte con chiave | nessuna: i dati stanno in chiaro nella cartella di Icing |
| ordine | BM25F coi pesi dei campi e delle varianti, calcolato in Kotlin da `matchinfo` | punteggio di Icing coi pesi delle proprieta' |
| API | `suspend`, una transazione per pezzo col suo segno | `ListenableFuture`, schemi e migrazioni di Icing |

Il vantaggio vero di AppSearch, **l'indice di sistema** (Android 12+, la ricerca del launcher), resta fuori: oggi solo i
Pixel lo leggono, e su Samsung no. Se un giorno servira', l'indice puo' copiare i suoi documenti anche li' senza cambiare
il resto (Domande di F8.22).

## L'indice

**Lo schema** ([`FluidSearchSchema`](../api/search.md)) dice i campi, il titolo e i campi dei pezzi. Ogni campo ha un
**peso** (`FluidSearchField.weight`: il titolo 3, il resto 1, a occhio dalle prove), puo' essere **non conservato**
(`stored = false`: si cerca ma non si mostra) o **riservato** (`sensitive`, sotto). **La versione** dello schema e' dell'app:
cambiandola (o cambiando i campi) l'indice si butta e si rifa' vuoto, e `rebuilt` lo dice. L'indice e' una copia: rifarlo
costa tempo, non dati.

**I documenti** ([`FluidSearchDocument`](../api/search.md)) hanno un `id` (lo stesso sostituisce), un tipo (`kind`, per i
gruppi e i gettoni: «nota», «allegato»), i campi, l'ora (`timestamp`: a parita' viene prima il piu' recente), una spinta
(`boost`) e l'indirizzo che li apre (`link`, un deep link di F8.12: lo usa «Cerca in Pampa»).

**L'italiano** (`SearchAnalyzer`, interno):
- **accenti**: «perché», «perche'» e «perche» sono la stessa parola (NFD e via i segni; anche «ß» → «ss»);
- **apostrofi**: «l'indirizzo» sono «l» e «indirizzo»; vale per `'`, `’`, `ʼ`, `` ` `` e `´`; la parte elisa e' una parola
  vuota;
- **plurali e generi**: la radice leggera di Savoy (quella di Lucene `ItalianLightStemmer`) da 4 lettere: «riunione» e
  «riunioni» → «riunion», «amica» e «amiche» → «amic», «voto» e «voti» → «vot»;
- **parole vuote**: articoli, preposizioni semplici e articolate, «e», «o», «ma», «che». La domanda le toglie, tranne
  l'ultima parola che si sta scrivendo («la» puo' diventare «lavoro»).

**La domanda** diventa un piano: per ogni parola la radice esatta (peso 1), l'**inizio di parola** (0,9 l'ultima, che
si sta scrivendo; 0,75 le altre, come Spotlight che cerca ogni parola come inizio), e se la parola **non c'e'** le
**parole vicine** del vocabolario (un errore fino a 6 lettere, due da 7; peso 0,6 e 0,42): «riunone» trova «riunione», e
`FluidSearchResults.corrected` la dice («Risultati per riunione · cercato anche «riunone»», come nell'anteprima del
banco). Una parola che c'e' non si corregge: «rossi» non diventa «rosso». Chi scrive «verifich» sta scrivendo
«verifiche»: la radice e' piu' corta di cio' che e' scritto, e il piano la prova.

**L'ordine**: tutte le varianti vanno in un'espressione FTS4 piatta (in OR) e l'ordine si calcola in Kotlin con
**BM25F** (k1 1,2, b 0,75) sui numeri di `matchinfo`, col peso di ogni campo e di ogni variante; le lunghezze medie
sono quelle dei documenti che hanno il campo (la media di FTS4 conta le righe vuote, e un campo raro sembrerebbe
lunghissimo). Servono tutte le parole della domanda; se nessun documento le ha, vengono quelli a cui ne manca una, a
mezzo punteggio.

**I pezzi** (`FluidSearchSnippet`): il titolo intero e, sotto, il pezzo di 120 caratteri con piu' parole trovate,
cominciato a una frase vicina o a una parola, con «…» ai tagli; `highlights` dice dove sono le parole. Un inizio di
parola si evidenzia solo per la parte scritta («Riun» in «Riunione»). Nella pagina le parole trovate sono
**semibold e col colore pieno del testo** sul grigio del pezzo (`fluidSearchHighlight`), come Mail di iOS: non solo il
colore.

**Le recenti** (`addRecent`, `recents`, `removeRecent`) stanno nell'indice, chiuse con lui: si cancellano con lui
(`destroy`).

## I dati sensibili

Con un [`FluidSearchCipher`](../api/search.md) (di solito `FluidSearchCipher.vault(section)` di una sezione
`VaultAccess.Device()`):
- **il testo conservato** (titoli e pezzi), le **recenti** e i **vettori** si chiudono sul disco, legati al documento;
- **i campi riservati** (`sensitive = true`) entrano nell'indice come **impronte** HMAC-SHA256 con una chiave dell'indice
  (64 bit; la chiave sta chiusa nella cassaforte): il file non contiene ne' il testo ne' le parole. Si trovano per parola
  intera e per inizio (da 3 lettere), **non** con gli errori di battitura; non vanno mai nel modello del significato e
  mai alle altre app (`FluidSearchQuery.includeSensitive` falso nel tool del ponte).

Cosa resta leggibile: le radici delle parole dei campi **non** riservati (l'indice delle parole e' fatto di loro) e
quanti documenti ci sono, come Core Spotlight, che protegge l'indice con la classe dei file e non parola per parola.
Una chiave che non apre piu' (telefono nuovo, Keystore azzerato) rifa' l'indice vuoto (`rebuilt`); una sezione solo
chiusa (all'impronta) no: l'errore arriva all'app, che la apre e riprova (`FluidSearchCipher.isLost`).

## L'aggiornamento a pezzi e il lavoro in carica

L'app dice da dove vengono i documenti con una [`FluidSearchSource`](../api/search.md): `changes(cursor, limit)` da' le
nuove, le cambiate e le tolte da un segno in poi. [`FluidSearchIndexer`](../api/search.md) le applica **a pezzi**
(`FluidSearchIndexPolicy.chunk`, 100): ogni pezzo entra **col suo segno in una transazione sola**, cosi' un'app chiusa a
meta' riparte dal pezzo dopo (`FluidSearchIndexingTest`).

- **Con l'app aperta** (`update()`) si fa il lavoro leggero, fino a `foregroundDocs` (300).
- **Il resto e' pesante**: `FluidSearchWork.schedule(context)` lo mette in coda per WorkManager **solo in carica e con la
  batteria non scarica** (e solo in Wi-Fi con `heavyNeedsWifi`, per una sorgente che scarica). Il lavoro
  (`FluidSearchWorker`) chiede alla `Application` dell'app (`FluidSearchHost.fluidSearchJobs`) cosa far girare, un giro
  fino a 8 minuti, e si rimette in coda se resta lavoro. `FluidSearchWork.heavyAllowedNow` dice se lo si puo' fare
  adesso in primo piano (in carica, senza risparmio energetico ne' caldo).
- **I vettori** ([`FluidSemanticIndexer`](../api/search.md)) sono sempre lavoro pesante: il modello costa decine di
  millisecondi per pezzo di testo.

## L'aggancio al campo di ricerca

[`rememberFluidSearchState`](../api/search.md) da' lo stato di una ricerca: il testo, i gettoni, i risultati (un valore
intero, scritto dal filo principale: regola F7.21), le recenti e le parole che completano. `state.spec(prompt, kinds)` e'
il campo di serie (`FluidSearchSpec.search`) gia' agganciato:
- **i gettoni dei tipi**: la fila delle pillole (`fluidSearchKindFilters`) che, scelte, diventano gettoni nel campo
  (F6B.8b) e tengono solo quel tipo;
- **i suggerimenti** sopra i risultati: a campo vuoto le **recenti** con la ✕ (`FluidSearchSuggestions`), scrivendo le
  **parole dell'indice che completano** quella che si scrive (al piu' 3);
- **l'invio** ricorda la domanda fra le recenti; anche aprire un risultato.

Nel contenuto della pagina `fluidSearchResults(state, kinds, onOpen, meta)` mette i **gruppi per tipo** («NOTE · 2»,
«TRASCRIZIONI · 1»), una riga per risultato col titolo e il pezzo evidenziati (la `FluidListRow` col sottotitolo
annotato, F8.22), la riga «Risultati per … · cercato anche …» quando la parola e' corretta e «Nessun risultato per …»
solo dopo una ricerca finita. Si cerca **60 ms** dopo l'ultima lettera (`FluidSearchDefaults.DebounceMillis`); il
significato **300 ms** dopo (come il selettore di foto), da 4 lettere.

**Il movimento.** I risultati che cambiano mentre si scrive cambiano **nitidi e subito**, come Spotlight: li muove il
dito. Cio' che l'app aggiunge da sola dopo (il significato, un'app che risponde piu' tardi in «Cerca in Pampa») **arriva
sfocato** in 300 ms (`fluidSearchArrival`, R7); con «Riduci movimento» arriva fermo. Il valore si legge nel layer:
nessuna ricomposizione per fotogramma.

**TalkBack**: ogni riga e' un pulsante col titolo e il pezzo; le intestazioni dei gruppi sono titoli; quanti risultati ci
sono lo dice un'area viva («3 risultati»).

## «Cerca in Pampa»

La domanda va a tutte le app Pampa che la sanno ricevere, **col ponte firmato** di `engine-ai-bridge` (nota del piano):
una chiamata Binder fra app con la stessa firma, mai la rete.

**Chi risponde** (ogni app con un indice) aggiunge il tool della ricerca al registry che il suo `AiToolHostProvider`
espone gia' a PampAI, e l'azione `FluidSearchBridge.ActionSearch` al suo provider, che lo rende visibile a chi cerca
(Android 11+; i `<queries>` li dichiara il modulo):

```kotlin
override fun registry() = ToolRegistry(
  graph().tools + FluidSearchBridge.searchTool(graph().searchIndex, FluidSearchExposure(
    kinds = setOf("nota", "allegato"),                 // le note private restano fuori
    kindLabels = mapOf("nota" to "Note", "allegato" to "Allegati"),
    locked = { graph().appLock.isLocked },             // engine-security: bloccata, non risponde coi dati
  )),
  graph().groups + FluidSearchBridge.Group,
)
```

```xml
<provider android:name=".bridge.NotesToolHostProvider" android:authorities="${applicationId}.ai.tools" android:exported="true">
  <meta-data android:name="dev.antigravity.fluidengine.ai.TOOL_HOST" android:value="1" />
  <intent-filter><action android:name="dev.antigravity.fluidengine.search.SEARCH" /></intent-filter>
</provider>
```

Il tool si chiama `cerca_in_pampa` (`FluidSearchBridge.ToolName`): risponde coi **risultati tipizzati** del protocollo 2
(titolo, pezzo e dove sono le parole, tipo, ora, indirizzo) e col testo per un modello, cosi' **anche PampAI** puo'
cercare nelle note e nella posta senza un tool scritto a mano per ogni app. Legge e basta: nessuna conferma. I campi
riservati non si cercano e non escono mai; un'app bloccata risponde «bloccata» senza dati.

**Chi cerca**: [`FluidPampaSearch`](../api/search.md) trova le app (`discoverApps`: il catalogo di ogni ospite del ponte,
che stabilisce anche il protocollo 2), le chiama **insieme** (2,5 s al piu' per app) e restituisce un valore a ogni
risposta: i gruppi restano **nell'ordine delle app**, niente salti mentre si legge. Con un modello del significato il
vettore della domanda si calcola **una volta** e va a tutte: chi ha i vettori dello stesso spazio li confronta (MyCloud
Gallery compresa, che usa lo stesso modello). `fluidPampaResults` mostra «Cercato sul telefono, in 5 app» (o «Cerco in 2
app…»), un gruppo per app («PAMPA MAIL · 2») e la riga di un'app bloccata, che la apre. `FluidPampaSearch.open` apre un
risultato nella sua app (solo in quella).

## Il significato: `engine-search-semantic`

**Il modello** e' **EmbeddingGemma 2 Text 270M** per LiteRT-LM (`FluidEmbeddingModels.EmbeddingGemma2Text270M`:
litert-community, Apache-2.0, non «gated», 164 626 432 byte, SHA-256 `2d079ee2…`), lo stesso file delle domande di MyCloud
Gallery 2: piu' di cento lingue, l'italiano compreso, 768 dimensioni (Matrjoska fino a 128), **senza prefissi** (la scelta
di MyCloud: con i prefissi di EmbeddingGemma i risultati sulle dieci domande di prova non migliorano). I vettori si
salvano a 8 bit con la scala: l'errore sul coseno sta sotto 0,002 (misurato).

**Il confronto** sta in `engine-search` (`FluidSearchQuery.meaning`): il coseno con tutti i pezzi, e passano quelli a
**1,75 scarti tipo sopra la media** (`FluidSearchIndex.MeaningMinZ`) e quelli a 0,03 dal migliore (`NearTop`: le due mail
dello stesso studio in un indice piccolo). Parole e significato si uniscono **per rango** (k = 10; le parole pesano 1, il
significato 0,8): chi ha la parola esatta resta in cima.

**Le misure** (le prove le rifanno sul tablet; i numeri del Tab S9 sono nel rapporto di F8.22):

| | valore | fonte |
|---|---|---|
| modello | 157 MiB sul disco | il file |
| libreria nativa | `liblitertlm_jni.so` 22,2 MB arm64-v8a, 26,4 MB x86_64, **non compressa nell'APK** (allineamento a 16 KB) | AAR 0.18.1 |
| avvio del motore | 0,48–0,57 s sul PC (CPU); 0,39 s sull'S25 col modello per percorso | sonda sul PC, MyCloud Gallery P4 |
| un vettore | 43–50 ms sul PC (CPU); 53–58 ms sull'S25; 27 ms sul S26 Ultra | sonda, MyCloud, scheda del modello |
| memoria del motore | ~330 MB mentre e' acceso | Google (S26 Ultra), MyCloud |
| le dieci domande di prova | 9 su 10 col documento giusto fra i primi tre (l'altra: «quando scade la luce» per la bolletta) | `FluidSemanticSearchTest` |

**Dove sta il modello.** 157 MiB per ogni app che cerca per significato sono troppi: e' una delle Domande di F8.22. Il
contratto e' pronto per tenerlo **una volta sola** sul telefono: l'app che lo ha espone `FluidSearchBridge.embedTool` e
le altre chiedono i vettori con `FluidSearchBridge.remoteEmbedder` (fino a 32 testi per chiamata). Il motore va acceso
quando si apre la ricerca e spento quando si chiude (`release`).

**LiteRT-LM e il build.** `litertlm-android` 0.18.1 e' compilata con Kotlin 2.4: il modulo la legge con
`-Xskip-metadata-version-check` e toglie kotlin-reflect e le coroutine che porta (come MyCloud Gallery 2); le regole R8
tengono il pacchetto `com.google.ai.edge.litertlm` (la parte nativa chiama per nome). Le classi della libreria sono
bytecode Java 21: sulla JVM delle prove (Java 17) non si caricano, quindi le prove usano i **vettori veri** calcolati una
volta sul PC (`fluid-search/meaning-embeddinggemma2-270m.tsv`) e il motore si prova sul tablet.

## L'API per le app Pampa

| app | cosa indicizza | come |
|---|---|---|
| **Pampa Notes** | note, trascrizioni (per paragrafo), PDF allegati (il testo dai visori di `engine-viewer`) | uno schema `title`/`body`/`folder`, `kind` «nota», «trascrizione», «allegato»; la sorgente dal `updatedAt` di Room; risponde a «Cerca in Pampa» |
| **Pampa Mail** | i messaggi salvati: oggetto, mittente (peso 2), corpo | `FluidSearchField("from", 2f)`; i corpi lunghi con `stored = false` e il pezzo dal riassunto; la cassaforte per le caselle protette; il significato per «la mail del dentista» |
| **MyCloud Gallery** | nomi, album, luoghi, il testo letto nelle foto | l'indice delle parole qui; i vettori delle foto restano i suoi (stesso spazio): risponde a «Cerca in Pampa» col vettore della domanda che arriva |
| **PampAI** | le conversazioni passate | e' anche il cliente: cerca nelle altre app con lo stesso tool (`cerca_in_pampa`), e puo' tenere il modello per tutti |
| **ClasseViva** | voti, comunicazioni, agenda | `kind` «voto», «comunicazione», «compito»; il blocco dell'app in `locked` |

## Trappole

- **L'indice e' una copia**: la verita' sta nei dati dell'app. Dopo `rebuilt` l'app rimanda tutto (i segni delle
  sorgenti ripartono da soli).
- **Le transazioni di SQLite sono del filo che le apre**: l'indice ha un filo suo, e una scrittura che sospende (la
  cassaforte che cifra) riprende su quello. Non chiamare il `SQLiteDatabase` dell'indice da fuori.
- **Un campo riservato non si trova con gli errori di battitura** e non da' pezzi alle altre app: e' voluto.
- **Il significato vuole i vettori dello stesso spazio**: `useMeaningSpace` butta quelli di un modello diverso, e
  `usesMeaningSpace` dice se una domanda per significato ha senso.
- **Un'app che risponde a «Cerca in Pampa» senza l'intent-filter** non si vede dalle altre app su Android 11+ (si vede
  solo da chi ha `QUERY_ALL_PACKAGES`, come PampAI).
- **Il modello non va nell'APK di serie**: si scarica col consenso, o lo presta l'app che lo tiene.

## Prove

- `engine-search/src/test` (JVM e Robolectric, col SQLite FTS4 vero): `SearchTextTest` (accenti, apostrofi, radici,
  distanza, il piano, i pezzi, BM25F), `FluidSearchIndexTest` (il corpus italiano: errori, accenti, apostrofi, plurali,
  inizi, pertinenza, pezzi, tipi, cambi, recenti, schema nuovo), `FluidSearchCipherTest` (il file non contiene testo,
  parole riservate e recenti; la chiave persa), `FluidSearchIndexingTest` (i pezzi col segno, l'errore a meta', il lavoro
  in carica coi vincoli), `FluidSemanticSearchTest` (i vettori veri: «la mail del dentista», le dieci domande, parole e
  significato insieme), `FluidPampaSearchTest` (due app finte e il ponte vero: per app, la firma diversa, l'app bloccata,
  il significato una volta sola, i campi riservati), `IcuWordsTest` (le parole di ICU e le nostre).
- `sample/src/testDebug/.../novita/F8p22SearchUiTest.kt` (Compose): i gruppi e la parola corretta, TalkBack, il
  significato che arriva sfocato e fermo con «Riduci movimento», «Cerca in Pampa» con le cinque app della galleria.

## Dove guardare

- `engine-search/src/main/kotlin/dev/antigravity/fluidengine/search/FluidSearchIndex.kt` — l'indice, le tabelle, la
  cifratura, i vettori.
- `.../SearchAnalyzer.kt` — l'italiano; `.../SearchRanking.kt` — il piano, BM25F, i pezzi.
- `.../FluidSearchIndexing.kt` — la sorgente, l'aggiornamento a pezzi, il lavoro in carica, il contratto del modello.
- `.../FluidSearchUi.kt` — lo stato, il campo, i risultati; `.../FluidSearchBridge.kt` e `.../FluidPampaSearch.kt` — «Cerca
  in Pampa».
- `engine-search-semantic/.../FluidLiteRtEmbedder.kt` — il motore e il modello.
- `sample/.../novita/F8p22Scenes.kt` — le quattro scene della galleria.
