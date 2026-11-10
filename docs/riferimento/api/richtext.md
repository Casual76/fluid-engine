<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/richtext.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-richtext

_Generato sull'engine 3.1.3 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-richtext/richtext/FluidRichTextDocument.kt`

- `enum class FluidRichTextInlineStyle` — Gli stili di carattere che l'editor sa mettere e togliere da una selezione.
- `enum class FluidRichTextListKind` — Il tipo di elenco di un paragrafo. Gli elenchi si annidano (vedi [FluidRichTextParagraphStyle]): un sotto-elenco ha il suo tipo, indipendente da quello sopra. **Puo' crescere**: niente `when` esaustivi senza `else`.
  - `val block: FluidRichTextBlock` — Il contenitore che fa di un paragrafo una voce di questo elenco.
- `enum class FluidRichTextBlock` — Un contenitore in cui sta un paragrafo: una citazione, o un elenco (di cui il paragrafo e' una voce, o dentro la cui voce sta). Sono i tre blocchi che l'HTML di una mail annida (`blockquote`, `ul`, `ol`) e che il Markdown sa scrivere con i rientri. **Puo' crescere**: niente `when` esaustivi senza `else`.
  - `val listKind: FluidRichTextListKind?` — Il tipo di elenco, o null per la citazione.
- `data class FluidRichTextInlineFormat(val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false, val link: String? = null)` — Come appare un tratto di testo: i tre interruttori e, se e' un link, il suo indirizzo.
  - `fun has(style: FluidRichTextInlineStyle): Boolean` — Vero se [style] e' acceso.
  - `fun with(style: FluidRichTextInlineStyle, on: Boolean): FluidRichTextInlineFormat` — Lo stesso formato con [style] acceso o spento.
  - `val isPlain: Boolean get()` — Nessuno stile e nessun link.
  - `companion object`
    - `val Plain: FluidRichTextInlineFormat` — Testo semplice.
- `class FluidRichTextParagraphStyle(blocks: List<FluidRichTextBlock>)` — Lo stile di un paragrafo: i contenitori in cui sta, dal piu' esterno al piu' interno ([blocks]).
  - `val blocks: List<FluidRichTextBlock>` — I contenitori, dal piu' esterno al piu' interno; vuota per un paragrafo normale. Mai oltre [MaxDepth].
  - `val depth: Int get()` — Il livello di rientro: quanti contenitori. 0 per un paragrafo normale.
  - `val list: FluidRichTextListKind? get()` — Il tipo dell'elenco di cui il paragrafo e' una voce (il contenitore piu' interno), o null.
  - `val quote: Boolean get()` — Vero se il paragrafo sta in almeno una citazione (a qualunque livello).
  - `val listLevel: Int get()` — Il livello della voce fra gli elenchi: 1 per una voce di un elenco, 2 per una voce di un sotto-elenco, e cosi' via (le citazioni non contano). 0 se il paragrafo non e' una voce. Decide il segno dei puntati e cio' che TalkBack annuncia («elenco puntato, livello 2»).
  - `val quoteLevel: Int get()` — In quante citazioni sta il paragrafo, una dentro l'altra.
  - `fun copy(list: FluidRichTextListKind? = this.list, quote: Boolean = this.quote): FluidRichTextParagraphStyle` — Lo stesso stile con l'elenco e la citazione cambiati come li cambiano i tasti della barra: [list] diverso sostituisce l'elenco della voce (o ne fa una voce, dentro i contenitori che ha; null toglie gli elenchi in fondo alla pila); [quote] falso toglie tutte le citazioni, vero mette in una citazione tutto il paragrafo se non lo era. Su uno stile a un livello e' la `copy` di prima.
  - `companion object`
    - `const val MaxDepth: Int` — Quanti contenitori al massimo, uno dentro l'altro: i livelli di rientro vanno da 0 a questo.
    - `val Plain: FluidRichTextParagraphStyle` — Un paragrafo normale.
    - `fun of(vararg blocks: FluidRichTextBlock): FluidRichTextParagraphStyle` — Lo stile con i contenitori [blocks], dal piu' esterno: `of(Quote, Bullet, Bullet)`.
- `data class FluidRichTextSpan(val start: Int, val end: Int, val format: FluidRichTextInlineFormat)` — Un tratto di testo con un formato non vuoto, `[start, end)` nel testo del documento.
- `data class FluidRichTextParagraph(val start: Int, val end: Int, val style: FluidRichTextParagraphStyle)` — Un paragrafo: `[start, end)` nel testo, senza l'a capo che lo chiude, e il suo stile.
- `class FluidRichTextDocument internal constructor(val text: String, internal val runs: List<FluidRichTextRun>, val paragraphStyles: List<FluidRichTextParagraphStyle>)` — Un testo con formattazione: il modello dell'editor, e il formato in cui l'app lo riceve e lo da'.
  - `val paragraphCount: Int get()` — Quanti paragrafi: almeno uno, anche nel documento vuoto.
  - `fun isEmpty(): Boolean` — Vero se non c'e' testo (puo' esserci comunque uno stile di paragrafo: un elenco appena iniziato).
  - `fun paragraphStart(index: Int): Int` — Dove comincia il paragrafo [index].
  - `fun paragraphEnd(index: Int): Int` — Dove finisce il paragrafo [index], escluso l'a capo.
  - `fun paragraphIndexAt(offset: Int): Int` — In quale paragrafo sta la posizione [offset] (fra 0 e la lunghezza del testo, compresi).
  - `val paragraphs: List<FluidRichTextParagraph>` — I paragrafi con le loro posizioni.
  - `val spans: List<FluidRichTextSpan>` — I tratti con un formato (quelli di testo semplice non ci sono), nell'ordine.
  - `fun formatAt(offset: Int): FluidRichTextInlineFormat` — Il formato del carattere in [offset] (`0 until text.length`).
  - `fun listNumber(paragraph: Int): Int` — Il numero di una voce di elenco numerato: 1 per la prima voce dell'elenco, e poi a salire. Le voci dello stesso elenco sono quelle con la stessa pila di contenitori; cio' che sta in mezzo con una pila piu' lunga (un sotto-elenco, una citazione nella voce) non interrompe la numerazione, cio' che ne esce si'. Ogni sotto-elenco riparte da 1. Zero per un paragrafo che non e' una voce numerata.
  - `fun slice(start: Int, end: Int): FluidRichTextDocument` — Il pezzo `[start, end)` come documento a se': testo, formati e stili dei paragrafi toccati. E' quello che si copia negli appunti, e quello che un'app prende per citare una parte.
  - `fun toPlainText(): String` — Il testo semplice, con i segni degli elenchi e delle citazioni: vedi [FluidRichTextPlainText].
  - `companion object`
    - `val Empty: FluidRichTextDocument` — Il documento vuoto: un paragrafo normale senza testo.
    - `fun fromPlainText(text: String): FluidRichTextDocument` — Un documento di solo testo: ogni riga un paragrafo normale. Gli a capo di ogni piattaforma (`\r\n`, `\r`, separatori Unicode) diventano `\n`, e i caratteri di controllo spariscono.
- `fun buildFluidRichText(block: FluidRichTextBuilder.() -> Unit): FluidRichTextDocument` — Costruisce un documento a paragrafi, per il codice: una firma, un testo generato, un esempio.
- `class FluidRichTextBuilder internal constructor()` — Il costruttore di [buildFluidRichText].
  - `fun paragraph(style: FluidRichTextParagraphStyle = FluidRichTextParagraphStyle.Plain, content: FluidRichTextParagraphScope.() -> Unit = {})` — Un paragrafo con lo stile [style] e il contenuto scritto da [content].
- `class FluidRichTextParagraphScope internal constructor(private val assembler: DocumentAssembler)` — Il contenuto di un paragrafo in [buildFluidRichText].
  - `fun append(text: String, format: FluidRichTextInlineFormat = FluidRichTextInlineFormat.Plain)` — Testo con il formato [format].
  - `fun bold(text: String)` — Testo in grassetto.
  - `fun italic(text: String)` — Testo in corsivo.
  - `fun underline(text: String)` — Testo sottolineato.
  - `fun link(text: String, url: String)` — Un link: [text] che porta a [url] (se [url] non e' sicuro resta il solo testo).

## `engine-richtext/richtext/FluidRichTextEditor.kt`

- `object FluidRichTextEditorDefaults` — I valori di serie di [FluidRichTextEditor].
  - `val KeyboardOptions: KeyboardOptions` — La tastiera di chi scrive un testo: maiuscola a inizio frase, correzione e suggerimenti accesi, il tasto invio che va a capo.
  - `const val ListIndentEm: Float` — Il rientro di una voce di elenco: lo spazio del segno, in multipli della dimensione del testo.
  - `const val QuoteIndentEm: Float` — Il rientro di un paragrafo citato: la barra e l'aria dopo.
  - `const val MaxIndentFraction: Float` — Il rientro piu' grande che si vede, in frazione della larghezza del campo: quando il paragrafo piu' profondo andrebbe oltre (livelli profondi, testo al 200%, telefono stretto), tutti i rientri del documento si stringono della stessa misura, cosi' i livelli restano a passi uguali e distinguibili e le barre delle citazioni in colonna; il posto del segno della voce resta intero.
- `@Composable fun FluidRichTextEditor(state: FluidRichTextState, modifier: Modifier = Modifier, placeholder: String? = null, contentDescription: String? = null, enabled: Boolean = true, readOnly: Boolean = false, minLines: Int = 1, textStyle: TextStyle = MaterialTheme.typography.bodyLarge, keyboardOptions: KeyboardOptions = FluidRichTextEditorDefaults.KeyboardOptions)` — Un editor di testo con formattazione: grassetto, corsivo, sottolineato, elenchi puntati e numerati, link, citazione. Per il corpo di una mail, una firma, una nota, un messaggio lungo.

## `engine-richtext/richtext/FluidRichTextEdits.kt`

- `data class FluidRichTextFormat(val bold: Boolean = false, val italic: Boolean = false, val underline: Boolean = false, val link: String? = null, val list: FluidRichTextListKind? = null, val quote: Boolean = false, val listLevel: Int = 0, val quoteLevel: Int = 0, val canIncreaseIndent: Boolean = false, val canDecreaseIndent: Boolean = false)` — Cosa e' acceso dove sta il cursore (o su tutta la selezione): quello che la barra di formattazione mostra e TalkBack annuncia.
  - `fun has(style: FluidRichTextInlineStyle): Boolean` — Vero se [style] e' acceso.

## `engine-richtext/richtext/FluidRichTextHtml.kt`

- `object FluidRichTextHtml` — Il documento da e verso un **HTML ristretto**: quello che si manda in una mail, si salva come bozza o firma, si mette negli appunti; e la lettura di qualunque HTML (incollato, ricevuto, salvato) tenendo solo cio' che l'editor sa mostrare.
  - `const val Version: Int` — La versione del dialetto scritto da [write]. Sale se un giorno l'uscita cambia in modo che un lettore vecchio non capirebbe; [read] legge ogni versione (e ogni HTML).
  - `const val MaxInputLength: Int` — Oltre questa lunghezza l'ingresso si legge fino a qui: un testo da incollare non e' mai cosi' lungo.
  - `fun write(document: FluidRichTextDocument): String` — L'HTML ristretto di [document].
  - `fun read(html: String): FluidRichTextDocument` — Il documento letto da [html], qualunque cosa contenga. Mai un'eccezione per un input malformato.

## `engine-richtext/richtext/FluidRichTextLabels.kt`

- `data class FluidRichTextLabels(val formatting: String = "Formattazione", val formattingBar: String = "Barra di formattazione", val bold: String = "Grassetto", val italic: String = "Corsivo", val underline: String = "Sottolineato", val bulletList: String = "Elenco puntato", val numberedList: String = "Elenco numerato", val quote: String = "Citazione", val link: String = "Link", val undo: String = "Annulla", val redo: String = "Ripeti", val linkTitle: String = "Link", val linkText: String = "Testo", val linkAddress: String = "Indirizzo", val linkAddressPlaceholder: String = "esempio.it", val linkInvalid: String = "Questo indirizzo non si può usare", val linkConfirm: String = "Fatto", val linkCancel: String = "Annulla", val linkRemove: String = "Togli il link", val linkTo: String = "link a %1\$s", val separator: String = ", ", val increaseIndent: String = "Aumenta rientro", val decreaseIndent: String = "Riduci rientro", val level: String = "livello %1\$d")` — Le parole che l'editor e la sua barra dicono da se': i nomi dei tasti per TalkBack, lo stato al cursore, il foglio del link.
  - `companion object`
    - `fun english(): FluidRichTextLabels` — Le stesse parole in inglese.
- `val LocalFluidRichTextLabels: ProvidableCompositionLocal<FluidRichTextLabels>` — Le parole dell'editor per l'albero sotto. Di serie l'italiano.

## `engine-richtext/richtext/FluidRichTextLinks.kt`

- `object FluidRichTextLinks` — Quali indirizzi possono diventare un link, e come si ripulisce quello che scrive una persona.
  - `val AllowedSchemes: Set<String>` — Gli schemi ammessi, in minuscolo.
  - `const val MaxLength: Int` — Oltre questa lunghezza un indirizzo e' scartato: nessun link vero e' cosi' lungo, un attacco si'.
  - `fun sanitize(url: String): String?` — L'indirizzo pronto per un link, o null se non e' sicuro.
  - `fun fromUserInput(input: String): String?` — L'indirizzo che una persona scrive nel campo di un link, completato: `esempio.it` diventa `https://esempio.it`, `nome@esempio.it` diventa `mailto:nome@esempio.it`, `+39 055 123` diventa `tel:+39055123`. Null se anche cosi' non e' un indirizzo sicuro.
  - `fun displayText(url: String): String` — Come si legge un indirizzo a una persona: senza `mailto:`/`tel:` e senza la barra finale.
- `object FluidRichTextPlainText` — Il documento come testo semplice: la parte «solo testo» di una mail, un'anteprima, una notifica.
  - `fun write(document: FluidRichTextDocument): String` — Il testo semplice di [document].

## `engine-richtext/richtext/FluidRichTextMarkdown.kt`

- `object FluidRichTextMarkdown` — Il documento da e verso **Markdown**, per le app che salvano testo (le note) e per chi vuole un formato leggibile anche senza editor.
  - `fun write(document: FluidRichTextDocument): String` — Il Markdown di [document].
  - `fun read(markdown: String): FluidRichTextDocument` — Il documento letto da [markdown]. Mai un'eccezione.

## `engine-richtext/richtext/FluidRichTextState.kt`

- `enum class FluidRichTextChangeSource` — Da dove viene l'ultima modifica del documento: serve a un'app che salva di continuo per distinguere cio' che l'utente ha scritto da cio' che ha messo lei (una firma, un testo generato) o dall'annulla. **Puo' crescere**: niente `when` esaustivi senza `else`.
- `class FluidRichTextState(initial: FluidRichTextDocument = FluidRichTextDocument.Empty, initialSelection: TextRange = TextRange(initial.text.length))` — Lo stato di un [FluidRichTextEditor]: il documento, il cursore, la cronologia per annullare, e cosa mostra la barra di formattazione.
  - `val document: FluidRichTextDocument get()` — Il documento com'e' adesso.
  - `val selection: TextRange get()` — Il cursore, o la selezione (`start` puo' essere dopo `end` se si e' selezionato all'indietro).
  - `val composition: TextRange? get()` — La parola che la tastiera sta ancora componendo (sottolineata da Gboard mentre si scrive o si detta), o null. Un'app non deve sostituire il testo li' dentro: la tastiera la riscriverebbe.
  - `var revision: Long by mutableLongStateOf(0L)` — Cresce di uno a ogni modifica del documento (testo o stile). Per salvare di continuo.
  - `var lastChangeSource: FluidRichTextChangeSource by mutableStateOf(FluidRichTextChangeSource.Program)` — Chi ha fatto l'ultima modifica.
  - `val format: FluidRichTextFormat get()` — Cosa e' acceso al cursore o su tutta la selezione: vedi [FluidRichTextFormat].
  - `var isFocused: Boolean by mutableStateOf(false)` — Vero mentre l'editor ha il fuoco (la tastiera e' sua).
  - `var isFormattingRequested: Boolean by mutableStateOf(false)` — Il tasto «Aa»: vero quando l'utente ha chiesto la barra di formattazione anche senza selezione. Lo cambiano [FluidRichTextFormatButton] e il primo tasto della barra; un'app puo' scriverlo.
  - `val isFormattingBarVisible: Boolean` — Vero quando la barra di formattazione deve stare sopra la tastiera: editor col fuoco e una selezione, o «Aa».
  - `fun requestFocus()` — Chiede il fuoco per l'editor (e quindi la tastiera). Senza un editor in scena non fa niente.
  - `val canUndo: Boolean get()` — Vero se c'e' qualcosa da annullare.
  - `val canRedo: Boolean get()` — Vero se c'e' qualcosa da ripetere.
  - `fun undo()` — Annulla l'ultimo passo (una parola, uno stile, un incolla, un inserimento dal codice).
  - `fun redo()` — Ripete l'ultimo passo annullato.
  - `fun clearHistory()` — Dimentica la cronologia (per esempio dopo aver aperto una bozza: non si annulla l'apertura).
  - `fun setDocument(document: FluidRichTextDocument, selection: TextRange = TextRange(document.text.length))` — Sostituisce tutto il documento (una bozza riaperta, un testo generato accettato). Si annulla, a meno che subito dopo l'app chiami [clearHistory]. Il cursore va dove dice [selection], di serie in fondo.
  - `fun replace(start: Int, end: Int, fragment: FluidRichTextDocument)` — Sostituisce `[start, end)` con [fragment], paragrafi e stili compresi, e mette il cursore dopo il frammento. E' il modo di inserire o rimpiazzare un blocco noto: l'app sa dov'e' (cerca nel [FluidRichTextDocument.text]) e lo sostituisce.
  - `fun insert(offset: Int, fragment: FluidRichTextDocument)` — Inserisce [fragment] in [offset].
  - `fun replaceSelection(fragment: FluidRichTextDocument)` — Mette [fragment] al posto della selezione (o al cursore).
  - `fun select(selection: TextRange)` — Sposta il cursore o la selezione.
  - `fun selectedDocument(): FluidRichTextDocument` — Il pezzo selezionato, con i suoi stili. Vuoto senza selezione.
  - `fun toggle(style: FluidRichTextInlineStyle)` — Accende o spegne [style]. Con una selezione vale per tutta (se era acceso su tutta si spegne, altrimenti si accende su tutta); col cursore fermo vale per cio' che si scrivera' da li'.
  - `fun toggleList(kind: FluidRichTextListKind)` — Mette o toglie l'elenco [kind] ai paragrafi toccati dalla selezione.
  - `fun toggleQuote()` — Mette o toglie la citazione ai paragrafi toccati dalla selezione: se stanno gia' tutti in una citazione ne escono del tutto (anche dalle citazioni annidate; gli elenchi restano), altrimenti chi non c'era entra in una citazione che lo contiene tutto (elenchi e sotto-elenchi compresi).
  - `fun increaseIndent()` — Aumenta di un livello il rientro dei paragrafi toccati che ne hanno uno: una voce diventa la voce di un sotto-elenco dello stesso tipo (la numerazione riparte), un paragrafo citato una citazione nella citazione. I paragrafi normali e quelli gia' a [FluidRichTextParagraphStyle.MaxDepth] restano dove sono. Si annulla in un passo. Il tasto della barra e Tab da una tastiera fisica fanno questo;
  - `fun decreaseIndent()` — Diminuisce di un livello il rientro dei paragrafi toccati: una voce di un sotto-elenco torna voce dell'elenco sopra, una voce di primo livello torna paragrafo, una citazione annidata torna citazione semplice, un paragrafo citato esce dalla citazione. Il tasto della barra e Maiusc+Tab.
  - `fun setLink(url: String, text: String? = null): Boolean` — Fa della selezione un link a [url] (completato con [FluidRichTextLinks.fromUserInput]: `esempio.it` va bene). Col cursore dentro un link ne cambia l'indirizzo; col cursore fermo altrove inserisce [text] (o l'indirizzo) come link. Con [text] diverso dal testo attuale lo sostituisce.
  - `fun removeLink()` — Toglie il link dalla selezione, o dal link che il cursore tocca. Il testo resta.
  - `companion object`
    - `const val MaxHistory: Int` — Quanti passi tiene l'annulla.
    - `const val MaxSavedLength: Int` — Oltre questa lunghezza lo stato salvato da Android non porta il documento (vedi la nota sul salvataggio in [FluidRichTextState]).
    - `val Saver: Saver<FluidRichTextState, Any>` — Salva documento, selezione e «Aa» in tipi che un `Bundle` accetta. Non la cronologia.
- `@Composable fun rememberFluidRichTextState(initial: FluidRichTextDocument = FluidRichTextDocument.Empty): FluidRichTextState` — Crea e ricorda lo stato di un editor, salvato per la rotazione e la morte del processo.

## `engine-richtext/richtext/FluidRichTextToolbar.kt`

- `@Composable fun FluidRichTextToolbar(state: FluidRichTextState, modifier: Modifier = Modifier, backdrop: GlassBackdropState = currentGlassBackdrop(), placement: FluidFloatingCapsulePlacement = FluidFloatingCapsulePlacement.ReplacingTabBar, showHistory: Boolean = true, avoidKeyboard: Boolean = true)` — La barra di formattazione sopra la tastiera: «Aa», grassetto, corsivo, sottolineato, elenco puntato, elenco numerato, citazione, riduci e aumenta rientro, link, e annulla/ripeti.
- `@Composable fun FluidRichTextFormatButton(state: FluidRichTextState, modifier: Modifier = Modifier, enabled: Boolean = true)` — Il tasto «Aa» che chiede la barra di formattazione anche senza selezione: un interruttore (acceso quando la barra e' chiesta), piatto come le azioni della capsula, con il bersaglio da 48 dp. Se l'editor non ha il fuoco glielo da', cosi' compaiono insieme la tastiera e la barra.
