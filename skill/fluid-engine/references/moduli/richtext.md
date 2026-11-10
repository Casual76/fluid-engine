# engine-richtext

**L'editor di testo con formattazione**: grassetto, corsivo, sottolineato, elenchi puntati e numerati **annidati**, citazioni **annidate**, link, con la barra dei comandi sopra la tastiera, annulla e ripeti, incolla filtrato e
tre formati di scambio (HTML ristretto, Markdown, testo semplice). Per il corpo di una mail, una firma, una nota, un messaggio lungo. Pacchetto `dev.antigravity.fluidengine.richtext`, dalla 3.1.0 (Pampa Mail, F1.4 e F1.4b).

È un **modulo opt-in**, come `engine-wear` e `engine-ai-ui`: dipende solo da `engine-ui` (che porta `engine-foundation`), non porta librerie esterne (il lettore di HTML e quello di Markdown sono scritti qui, in Kotlin puro) e
un'app che non lo chiede non compila una riga né si porta dietro il suo lettore di HTML. Perché un modulo e non un file in `engine-ui`: è il componente più grande e delicato della famiglia del testo (modello a intervalli,
IME, incolla da HTML ostile, annulla/ripeti) e per ora lo chiede una sola app. Modello mentale: **un campo di testo normale per la tastiera, un documento immutabile per l'app, e uno stato che li tiene allineati.**

Catalogo delle firme: [`../api/richtext.md`](../api/richtext.md) (generato). I controlli della famiglia del testo («Un editor con i pulsanti…») sono in [`../componenti/controlli.md`](../componenti/controlli.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un campo per scrivere testo con grassetto, elenchi, link | `FluidRichTextEditor(state)` + `FluidRichTextToolbar(state)` nell'overlay della pagina |
| soltanto un campo di testo, magari con un pulsante «grassetto» a segni | **non questo modulo**: `FluidTextField` + `FluidTextEdit` ([controlli.md](../componenti/controlli.md)) |
| lo stato (documento, cursore, annulla) | `rememberFluidRichTextState(initial)` |
| aprire una bozza | `state.setDocument(FluidRichTextHtml.read(html))` e poi `state.clearHistory()` |
| salvare una bozza mentre si scrive | `snapshotFlow { state.revision }` + una pausa (debounce), poi `FluidRichTextHtml.write(state.document)` |
| inserire una firma o il testo di un assistente | `state.insert(offset, doc)` / `state.replaceSelection(doc)` (si annullano) |
| costruire un documento da codice | `buildFluidRichText { paragraph { bold("…") } }` |
| mandare una mail | `FluidRichTextHtml.write(doc)` per la parte HTML, `doc.toPlainText()` per la parte «solo testo» |
| leggere un HTML ricevuto o incollato | `FluidRichTextHtml.read(html)` (tratta l'ingresso come ostile; **non** lo sanifica per mostrarlo altrove: [`../componenti/contenuto-web.md`](../componenti/contenuto-web.md)) |
| salvare in un formato leggibile senza editor | `FluidRichTextMarkdown.write(doc)` (ma per salvare, l'HTML è il formato di riferimento) |
| il tasto «Aa» dove voglio io | `FluidRichTextFormatButton(state)` |
| tradurre le parole del modulo | `LocalFluidRichTextLabels provides FluidRichTextLabels(…)` |

## Come è fatto

### Il modello: testo + tratti + paragrafi, immutabile

`FluidRichTextDocument` è una `String` normale con `\n` **fra un paragrafo e l'altro (e solo lì)**, più due strati che il testo non vede: i **tratti di formato** dei caratteri (grassetto, corsivo, sottolineato, link: coprono
il testo senza buchi e senza due vicini uguali) e **uno stile per ogni paragrafo**. Per questo cercare, contare, confrontare il testo sono operazioni su stringhe, e per questo la tastiera (che vede solo il testo) e il modello
non possono raccontare due storie diverse. È **immutabile**: ogni modifica produce un documento nuovo, la cronologia dell'annulla tiene i precedenti senza copiarli, e l'app confronta due versioni con `==` e legge `document` in O(1).

Sotto, l'editor è un `BasicTextField` a stato che contiene **solo il testo**: Gboard (suggerimenti, correzione, scrittura col dito, dettatura) vede un campo normale. Ogni modifica passa da una **trasformazione d'ingresso** che
la riporta sul documento (con gli stili spostati, ereditati, fusi) e gli stili si disegnano con una **trasformazione d'uscita**. I segni degli elenchi e le barre delle citazioni si disegnano **accanto** al testo, nel rientro
del paragrafo: nessun segno è inserito nel testo, nessuna posizione da tradurre. Il testo che vede la tastiera e quello del documento sono lo stesso, carattere per carattere.

### Il modello a pila di contenitori e i livelli

`FluidRichTextParagraphStyle` non è un numero né due flag: è una **pila di contenitori** (`FluidRichTextBlock`: `Quote`, `Bullet`, `Numbered`), dal più esterno al più interno, come li annida l'HTML di una mail. `[Bullet, Bullet]`
è una voce di un sotto-elenco puntato; `[Quote, Numbered]` una voce numerata dentro una citazione; `[Bullet, Quote]` una citazione dentro una voce; `[Quote, Quote]` una citazione dentro una citazione.

- **Livello** (`depth`): quanti contenitori, da 0 (paragrafo normale) a `FluidRichTextParagraphStyle.MaxDepth` = **6**. Oltre, un HTML incollato con cento citazioni una dentro l'altra tiene i primi cinque e il più interno: il
  paragrafo resta una voce se lo era e **il testo resta tutto**.
- **Voce o contenuto.** Se il contenitore più interno è un elenco il paragrafo è una **voce** (`list`); se è una citazione è un paragrafo citato. Due paragrafi vicini con la stessa pila sono voci dello stesso elenco; un paragrafo
  con una pila più lunga che comincia come quella sopra sta **dentro** la voce sopra. È per questo che la numerazione (`listNumber`) **prosegue dopo un sotto-elenco e riparte in ogni sotto-elenco**.
- **Livello della voce** (`listLevel`): 1 per un elenco, 2 per un sotto-elenco (le citazioni non contano); decide il segno dei puntati (•, ◦, ▪, poi di nuovo) e cosa annuncia TalkBack («Elenco puntato, livello 2»).
- **Il rientro che si vede ha un tetto** (`FluidRichTextEditorDefaults.MaxIndentFraction`, 0,4 della larghezza del campo): quando il paragrafo più profondo andrebbe oltre (sei livelli col testo al 200% su un telefono
  stretto), **tutti** i rientri del documento si stringono della stessa scala, così i livelli restano a passi uguali e distinguibili e le barre delle citazioni in colonna; il posto del segno resta intero. Il prezzo: portare una
  voce oltre il tetto stringe un po' tutti i rientri (col testo normale su un telefono non succede prima del sesto livello).

### I comandi

Tutti passano da `FluidRichTextState` (e si annullano in un passo): `toggle(style)` (`FluidRichTextInlineStyle`: `Bold`, `Italic`, `Underline`), `toggleList(kind)`, `toggleQuote()`, `increaseIndent()`, `decreaseIndent()`,
`setLink(url, text)`, `removeLink()`, `undo()`, `redo()`, `clearHistory()`. Le regole che sorprendono:

- **Con una selezione** uno stile vale per tutta (se era acceso su tutta si spegne, altrimenti si accende su tutta); **col cursore fermo** vale per ciò che si scriverà da lì.
- **`toggleList`**: se i paragrafi toccati sono già tutti voci di quel tipo (a qualunque livello) escono dagli elenchi, **dentro le citazioni che avevano**; una voce dell'altro tipo cambia tipo restando al suo livello; un
  paragrafo (anche citato) diventa una voce lì dove sta.
- **`toggleQuote`**: se stanno tutti in una citazione ne escono **del tutto** (anche dalle annidate; gli elenchi restano), altrimenti chi non c'era entra in una citazione che contiene tutto (elenchi e sotto-elenchi compresi).
- **`increaseIndent` / `decreaseIndent`**: una voce diventa la voce di un sotto-elenco **dello stesso tipo** (la numerazione riparte), un paragrafo citato una citazione nella citazione; i paragrafi normali e quelli già al
  tetto non cambiano (il tasto è spento: `FluidRichTextFormat.canIncreaseIndent`/`canDecreaseIndent`). Riduci: voce di sotto-elenco → voce dell'elenco sopra → paragrafo; citazione annidata → semplice → fuori. Sono anche **Tab e
  Maiusc+Tab** da tastiera fisica (il Tab lo prende solo dove cambia un rientro: altrove resta del campo), e ciò che fanno **a capo su una voce vuota** e **cancella in testa a una voce**: risalgono **di un livello alla volta**
  prima di uscire dall'elenco. **Agiscono sui paragrafi toccati, non sui loro figli**: aumentare o ridurre il rientro di una voce **non sposta** le voci del sotto-elenco che le sta sotto (si selezionano anche quelle per portarle
  dietro).
- **`setLink(url, text)`** completa l'indirizzo (`esempio.it` → `https://esempio.it`) e ritorna **falso, senza cambiare niente**, se non è sicuro. Un link **si allarga se scrivi dentro**, ma **non a chi scrive in fondo** (chi
  finisce un indirizzo e va avanti non vuole che la frase dopo sia parte del link): è la regola di ogni editor. Grassetto, corsivo e sottolineato invece si allargano anche in fondo.
- **Incolla** dagli appunti: l'HTML (da un browser, da Google Documenti, da un'altra mail) tiene ciò che l'editor sa mostrare; il testo con stili di un'altra app pure; il testo semplice entra col formato del punto in cui
  si incolla; ciò che non è testo (un'immagine dalla tastiera) è ignorato. **Copia** mette negli appunti anche l'HTML della selezione: copiare e incollare dentro l'editor non perde gli stili.
  **Cosa l'incolla non conserva** (non è un difetto: l'editor non ha questi concetti): colori e caratteri, la dimensione del testo, il barrato (`<s>`, `<del>`, `line-through`), le immagini, le tabelle (le righe diventano
  paragrafi e il testo resta), le classi e gli stili che non siano grassetto, corsivo e sottolineato. I **titoli** (`<h1>`…`<h6>`, `<th>`) diventano un paragrafo in **grassetto**: il testo c'è, la gerarchia no. Si incolla
  solo dagli appunti: un testo **trascinato** cade come testo scritto, col formato del punto. **Un frammento annidato incollato porta i livelli che aveva** (assoluti, non sommati a quelli del punto in cui cade) per i suoi
  paragrafi interi; il paragrafo parziale in cui si incolla tiene lo stile che ha già.
- **Scorciatoie** da tastiera fisica: Ctrl+Z, Ctrl+Maiusc+Z / Ctrl+Y (la cronologia è quella dell'editor, con gli stili), Ctrl+B/I/U.
- L'annulla tiene al più `FluidRichTextState.MaxHistory` = 100 passi (una parola, uno stile, un incolla, un inserimento dal codice).

## Lo stato e il salvataggio continuo

`rememberFluidRichTextState(initial)` crea e ricorda lo stato (`initial` conta solo la prima volta), e **lo possiede l'app**: l'editor sta nella pagina e la barra nell'overlay sopra la tastiera, due punti dell'albero con lo
**stesso** `state`.

| campo | cos'è |
|---|---|
| `document` | il documento adesso (immutabile, O(1)) |
| `revision` | **cresce di uno a ogni modifica** del documento (testo o stile): è stato di Compose, `snapshotFlow { state.revision }` è il modo economico di sapere che c'è da salvare, senza confrontare documenti |
| `lastChangeSource` | `FluidRichTextChangeSource`: `Typing` (tastiera, IME, correzione, dettatura), `Formatting`, `Paste`, `Program` (dall'app), `History` (annulla/ripeti): per distinguere ciò che l'utente ha scritto da ciò che ha messo l'app (una firma, un testo generato). **Può crescere**: niente `when` esaustivi senza `else` |
| `selection`, `composition` | il cursore; la parola che la tastiera sta ancora componendo (**non sostituire il testo lì dentro**: la tastiera lo riscriverebbe) |
| `format` | `FluidRichTextFormat`: cosa è acceso al cursore o su **tutta** la selezione (`bold`, `italic`, `underline`, `link`, `list`, `quote`, `listLevel`, `quoteLevel`, `canIncreaseIndent`, `canDecreaseIndent`) |
| `isFormattingBarVisible` | la barra deve stare sopra la tastiera: editor col fuoco e una selezione, o «Aa» |

**Il salvataggio vero è dell'app.** Lo stato salvato da Android (`rememberSaveable`) serve a ricreare la schermata (rotazione, morte del processo: documento, selezione e «Aa», **non la cronologia**), non è una garanzia contro
la perdita di testo, e **non salva il documento** in due casi (un `Bundle` troppo grande fa cadere l'app): oltre `FluidRichTextState.MaxSavedLength` = 50 000 caratteri, **oppure** (dalla 3.1.1) quando la dimensione serializzata, stimata per eccesso, supera **256 KB**. La stima conta il testo (2 byte per carattere), i tratti con formato, gli indirizzi dei link (2 byte per carattere, ciascuno una volta sola) e gli stili dei paragrafi: molti link lunghi pesano molto anche in poco testo, quindi un documento sotto i 50 000 caratteri può superare il secondo tetto. Sopra uno dei due tetti lo stato salvato **non porta il documento**: dopo la ricreazione della schermata `rememberFluidRichTextState` riparte da `initial`, senza errori e senza avvisi, e l'app lo riapre dalla sua bozza (per questo la bozza su disco non è facoltativa). Sotto i due tetti si ripristina tutto. Schema
tipico: `snapshotFlow { state.revision }.debounce(…)` → `FluidRichTextHtml.write(state.document)` su disco; all'apertura `setDocument(FluidRichTextHtml.read(salvato))` + `clearHistory()` (l'apertura non si annulla). Il formato dello
stato salvato è versionato (2 da quando gli elenchi si annidano: pila in un `Int`, profondità nei 4 bit bassi e 2 bit per contenitore); uno stato salvato senza versione, quello a un livello di prima, **si legge ancora**.

**Il testo non si cambia in altro modo che dai metodi dello stato**: lo stato del campo sotto è privato, perché testo e stili non divergano. Se divergono lo stesso (un istante fra due scritture) la trasformazione d'uscita
non disegna niente per un fotogramma (un intervallo fuori dal testo farebbe cadere il campo) e l'editor ha una rete di sicurezza che riallinea il documento al testo.

## I formati

| formato | uscita | ingresso | per cosa |
|---|---|---|---|
| **HTML ristretto** (`FluidRichTextHtml`) | `div`, `ul`/`ol`/`li`, `blockquote`, `b`, `i`, `u`, `a href`; **nessun attributo** salvo `href`, nessuno stile, nessuna classe | qualsiasi HTML, trattato come **ostile** | il formato di riferimento: la parte HTML di una mail, una bozza, gli appunti |
| **Markdown** (`FluidRichTextMarkdown`) | `**…**`, `*…*`, `<u>…</u>`, `[testo]` e l'indirizzo fra parentesi tonde, `- `, `1. `, `> `, annidamenti come CommonMark (due puntati nuovi sulla stessa riga alternano il segno, `- * `, perché `- - ` sarebbe una linea orizzontale) | più ampio (`__`, `_`, `* `, `+ `, `1)`, titoli → grassetto, link automatici, codice come testo) | note, scambio leggibile; **non** il formato di riferimento |
| **Testo semplice** (`doc.toPlainText()`) | un paragrafo per riga, `• `/`◦ `/`▪ `/`1. `, `> `; il link seguito dall'indirizzo fra parentesi | `FluidRichTextDocument.fromPlainText` | la parte «solo testo» di una mail, un'anteprima, una notifica |

- **HTML, uscita.** Il paragrafo vuoto è `<div><br></div>` (come lo scrivono i client di posta: la spaziatura è la stessa ovunque); spazi doppi, in testa e in coda diventano `&nbsp;` (l'HTML li collasserebbe), il tab `&#9;`. Un
  **sotto-elenco sta dentro il `<li>` della voce sopra** (`<ul><li>a<ul><li>b</li></ul></li></ul>`), una citazione nell'altra è un `<blockquote>` dentro l'altro: come scrivono Gmail e Outlook.
- **HTML, versione.** `FluidRichTextHtml.Version` = **2** (annidato). La 1 aveva elenchi e citazioni a un livello; **un HTML della versione 1 è anche un HTML della 2 e si legge identico**; un lettore della 1 che riceve la 2
  appiattisce gli annidamenti **senza perdere testo**. `read` legge ogni versione e ogni HTML.
- **HTML, ingresso ostile.** Non si esegue niente e **non si carica niente** (immagini, fogli di stile, font, frame: mai, il lettore non ha nemmeno un modo per farlo). Si tengono testo, paragrafi (`p`, `div`, titoli, righe
  di tabella, `br`), elenchi e citazioni con gli annidamenti (fino a `MaxDepth`; anche `<ul>` direttamente dentro `<ul>` come li scrivono alcuni editor), grassetto/corsivo/sottolineato anche come `style="font-weight:…"` (come
  li copia Google Documenti, compreso il suo `<b style="font-weight:normal">` che **non** è grassetto) e i link con indirizzo sicuro. Sparisce il **contenuto** di `script`, `style`, `head`, `template`, `svg`, `math`, `iframe`,
  `object`, `noscript`, `select`, `textarea`; tutto il resto (tag sconosciuti, colori, caratteri, tabelle) perde il tag e tiene il testo. Tetti contro l'HTML ostile profondo: `FluidRichTextHtml.MaxInputLength` = 4 000 000 caratteri
  (oltre, si legge fino a lì), 256 elementi annidati, 32 attributi per tag. **Il lettore non è un parser HTML5 completo**: nei casi patologici (tag chiusi nell'ordine sbagliato, `<p>` dentro `<b>`) non ricostruisce l'albero come un
  browser, ma il risultato è sempre un documento valido con tutto il testo leggibile (i test lo provano su input ostili).
- **Markdown: una riga = un paragrafo** (il dialetto delle app di note e dei messaggi, non CommonMark in cui due righe si fondono): un documento scritto e riletto torna identico, righe vuote comprese. I caratteri che
  il Markdown leggerebbe come segni (`*`, `_`, `[`, `]`, `\`, `` ` ``, `<`, e in testa `#`, `>`, `-`, `+`, `1.`) escono preceduti da `\`; il sottolineato si scrive `<u>` perché Markdown non ha un segno suo. Il `_` dentro una
  parola (`snake_case`) non è mai un segno. Gli annidamenti si leggono con rientri di 2-4 spazi o tab, e anche più segni sulla stessa riga (`- > citazione nella voce`).
- **Link sicuri** (`FluidRichTextLinks`): solo `http`, `https`, `mailto`, `tel`; **compresi gli indirizzi relativi, che sono scartati** (in un HTML incollato non hanno una base). Spazi e caratteri di controllo si tolgono
  **ovunque**, anche dentro lo schema (è così che `java&#10;script:` passa i filtri ingenui); `http(s)` vogliono un host; lunghezza massima `FluidRichTextLinks.MaxLength` = 2048. Il testo del link resta sempre: si perde solo
  l'indirizzo. `FluidRichTextLinks.fromUserInput` completa ciò che scrive una persona (`nome@esempio.it` → `mailto:`, `+39 055 123` → `tel:+39055123`).

## La barra e il tasto «Aa»

`FluidRichTextToolbar(state, modifier, backdrop, placement = ReplacingTabBar, showHistory = true, avoidKeyboard = true)` è una `FluidFloatingCapsule` con `avoidKeyboard`: **compare quando serve e non occupa spazio
altrimenti** (editor col fuoco e del testo selezionato, o dopo un tocco su «Aa»), galleggia sopra la pagina e sale con la tastiera. Va **nell'overlay della pagina** (`FluidScreen.overlay`, o un `Box` sopra il contenuto),
allineata in basso. Ogni stile è un interruttore: `FluidCapsuleAction(selected = …)` ([`../componenti/barre.md`](../componenti/barre.md)): ruolo `Switch`, disco dell'accento, **non solo un colore**. Riduci/aumenta rientro,
annulla e ripeti sono tasti semplici, spenti quando non c'è niente da fare. Toccare un tasto **non toglie il fuoco all'editor**: la tastiera resta e la selezione pure. Il **link** apre un foglio con testo e indirizzo (un
portale: **serve un `FluidGlassModalHost` alla radice**, come ogni foglio dell'engine). Su un telefono stretto o col testo al 200% i tasti scorrono di lato nella capsula; sono sempre da 48 dp.

## Cosa è dell'app, cosa dell'engine

| dell'engine | dell'app |
|---|---|
| il documento, i comandi, l'annulla, gli stili, l'incolla filtrato | dove salvare (la bozza su disco, il database) e quando (il debounce) |
| i tre formati e la sicurezza dei link | mandare la mail, **sanificare l'HTML che mostra a qualcuno** (un HTML ricevuto si mostra in `FluidWebContent`, non qui) |
| la barra, il tasto «Aa», le parole in italiano e in inglese | il contenitore (pagina che scorre, card), la firma, i modelli, gli allegati, la ricerca nel testo |
| l'accessibilità: lo stato al cursore, le azioni | il nome del campo (`contentDescription`), la lingua di sistema delle parole |

## Trappole

- **Un `FluidTextField` non diventa ricco.** `FluidTextEdit` scrive segni Markdown in un campo semplice: è un'altra cosa, per testi che restano Markdown. Se serve anche solo l'elenco annidato, è questo modulo.
- **Il campo non ha pozzo né bordo.** Occupa la larghezza che riceve e cresce col testo; il contenitore è dell'app. Dentro uno spazio di altezza fissa scorre da sé. Non metterlo in una `LazyColumn` come figlio con altezza fissa.
- **`contentDescription`** è il nome del campo per TalkBack («Corpo», «Firma»): il segnaposto non basta, sparisce appena si scrive.
- **Non sostituire il testo dentro `composition`**: la parola che Gboard sta componendo verrebbe riscritta.
- **L'annulla non conosce l'apertura di una bozza**: dopo `setDocument` chiama `clearHistory()`, altrimenti «annulla» riporta al documento vuoto.
- **`revision` cresce anche per modifiche dell'app** (`setDocument`, `replace`): filtra con `lastChangeSource` se non vuoi salvare ciò che hai appena letto.
- **Gli enum possono crescere** (`FluidRichTextInlineStyle`, `FluidRichTextListKind`, `FluidRichTextBlock`, `FluidRichTextChangeSource`): barrato e codice sono candidati. Un `when` senza `else` si rompe.
- **La barra senza `FluidGlassModalHost`** non apre il foglio del link; senza host il link si imposta solo da codice con `setLink`.
- **La barra sta fuori dal `content` della pagina.** Va nell'overlay (`FluidScreen.overlay`, o un `Box` sopra il contenuto), non come riga dentro lo scroll: è una capsula flottante ancorata in basso che sale con la tastiera, non un pezzo del contenuto.
- **Firme e testi generati non si scrivono con la tastiera finta.** Si inseriscono con `state.insert(offset, doc)`, `replace(start, end, doc)` o `replaceSelection(doc)`: una sola modifica che si annulla in un passo, con
  `lastChangeSource = Program`. Non toccare il testo dentro `composition`.
- **API sperimentali sotto.** L'incolla passa da `contentReceiver` e la trasformazione d'ingresso legge la lista delle modifiche del campo a stato (`TextFieldBuffer.changes`) di Compose Foundation (1.10 con la BOM `2026.03.00` di
  `versions.gradle`): sono API **sperimentali** (`@OptIn(ExperimentalFoundationApi::class)`) e un aggiornamento di Compose può richiedere un ritocco al modulo. L'API pubblica del modulo non le espone.
- **La citazione del messaggio a cui si risponde resta dell'app.** Il documento ha citazioni annidate, ma l'HTML originale di una mail ricevuta non passa da questo modello: l'app lo tiene come HTML e lo mostra con
  `FluidWebContent`; nell'editor entra solo ciò che l'utente può modificare.
- **L'HTML non è un sanificatore.** `FluidRichTextHtml.read` produce un documento sicuro da modificare e da riscrivere; mostrare l'HTML di un altro dentro l'app è un altro problema (la scheda di `FluidWebContent`).
- Tetto di rientro e `MaxDepth` sono **per il disegno e per la sicurezza**, non una scelta di prodotto: un HTML con cento livelli si legge, perde la profondità, non il testo.

## Accessibilità

TalkBack legge il testo; **lo stato del campo dice cosa è acceso al cursore** («Grassetto, elenco puntato, livello 2») con le parole di `LocalFluidRichTextLabels`. I tasti della barra sono interruttori col loro stato, il
tasto «Aa» ha il suo nome (`FluidRichTextLabels.formatting`), la barra ha un titolo (`formattingBar`) quando compare. I tasti sono bersagli da 48 dp. Le parole del modulo sono `FluidRichTextLabels` (italiano di serie,
`FluidRichTextLabels.english()`, via `LocalFluidRichTextLabels`): **un'app tradotta le passa tutte**.

## Cosa è verificato dai test

- `engine-richtext/src/test` (JVM, 96 test in sette file): `FluidRichTextDocumentTest` (19), `FluidRichTextEditsTest` (13), `FluidRichTextHtmlTest` (15), `FluidRichTextLinksTest` (3), `FluidRichTextMarkdownTest` (6),
  `FluidRichTextStateTest` (12), `FluidRichTextNestingTest` (28: pila di contenitori, livelli, rientro e riduzione, numerazione per sotto-elenco, tetto contro l'HTML ostile profondo). Leggili per il caso preciso: il modello, le
  modifiche pure del testo sul documento, i due lettori su input ostili, i comandi e il salvataggio.
- `FluidRichTextEditorTest` (`sample`, Robolectric, 22): l'editor e la barra nella galleria — il testo che prende il formato scelto, una composizione alla Gboard che tiene formato e testo, Invio che continua l'elenco e il doppio Invio che ne esce, cancella a inizio voce (anche da tastiera fisica), la barra che compare con la selezione e riflette gli stili, il tasto «Aa» senza selezione, TalkBack che dice stili e **livello** in italiano e in inglese, incolla di HTML che tiene solo il supportato, copia che mette l'HTML negli appunti, Ctrl+Z, modifiche dal codice che arrivano al campo, i tasti di rientro che si spengono quando non servono, Tab e Maiusc+Tab, Invio e cancella che **risalgono un livello alla volta**, e a testo grande e da destra a sinistra (anche con l'annidamento profondo) il testo resta leggibile e il rientro parte da destra.
- **Non provato da test**: la tastiera vera (Gboard, correzione, dettatura: si guardano in mano) e il vetro della barra.

## Dove guardare

`engine-richtext/src/main/kotlin/dev/antigravity/fluidengine/richtext/`:

| file | cosa c'è dentro |
|---|---|
| `FluidRichTextDocument.kt` | il modello: stili, pila di contenitori, documento, `buildFluidRichText` |
| `FluidRichTextState.kt` | lo stato, i comandi, `FluidRichTextChangeSource`, `FluidRichTextFormat`, il `Saver`, `rememberFluidRichTextState` |
| `FluidRichTextEditor.kt` | il campo, le trasformazioni d'ingresso e d'uscita, i segni accanto al testo, gli appunti |
| `FluidRichTextEdits.kt` | le modifiche pure del testo sul documento (interno, testato) |
| `FluidRichTextToolbar.kt` | la barra e il tasto «Aa» |
| `FluidRichTextHtml.kt`, `FluidRichTextMarkdown.kt`, `FluidRichTextLinks.kt` | i tre formati, la sicurezza dei link, il testo semplice |
| `FluidRichTextLabels.kt` | le parole del modulo, in italiano e in inglese |
| `sample/.../catalog/RichTextDemo.kt` | il call-site vero: editor, barra, incolla di HTML sporco, le tre uscite |

## Dipendenze e configurazione minima

Installazione in un'app: `engine-install.ps1 -AppRoot . -Modules engine-richtext` (le dipendenze si chiudono da sole: `engine-foundation`, `engine-ui`; il modulo è **opt-in**, entra solo se chiesto per nome). Nel `build.gradle` dell'app:
`implementation project(':engine-richtext')`. Nessun permesso, nessuna risorsa: le parole sono `FluidRichTextLabels`, non risorse Android. `minSdk` e toolchain: quelli dell'engine ([`../mappa.md`](../mappa.md)).
