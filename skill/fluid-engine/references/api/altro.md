# API · Non classificato

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-ui/ui/fluid/FluidFindInText.kt`

- `data class FluidFindHighlights(val current: SpanStyle, val others: SpanStyle)` — Gli evidenziatori di «trova» (F6B.6): la corrispondenza corrente e le altre.
- `object FluidFindDefaults` — I valori di serie di «trova nella pagina».
  - `val CurrentHighlight: Color` — Il giallo della corrispondenza corrente: quello della demo di `findNavigator` (WWDC25 231, 17:34, F6B.0 §4), il giallo di sistema di Apple. Lo stesso nei due temi, con il testo nero sopra, come in Apple.
  - `@Composable fun highlights(): FluidFindHighlights` — Gli stili di serie: la corrente gialla col testo nero; le altre con un velo dell'inchiostro della pagina al 16%, che in Apple è «evidenziata chiara» (F6B.0 §4). Un'app che ha già un suo giallo ne passa uno suo.
- `class FluidFindScrollRequest internal constructor(val range: TextRange, val index: Int, val serial: Int)` — La richiesta di portare in vista un'occorrenza: la fa «trova» quando cambia la corrente (la parola, le frecce, l'invio). [serial] cresce a ogni richiesta, anche verso la stessa occorrenza: l'app ci appoggia un `LaunchedEffect(request)` e scorre.
  - `fun boundsIn(layout: TextLayoutResult): Rect` — Il riquadro dell'occorrenza nel testo impaginato (in pixel, nelle coordinate del testo): la riga del primo carattere. Il bordo alto è quello da portare in vista.
- `class FluidFindState internal constructor(private val queryState: MutableState<String>, private val currentState: MutableState<Int>, text: AnnotatedString, ignoreCase: Boolean, ignoreDiacritics: Boolean)` — «Trova nella pagina» su un testo semplice (F6B.6), il punto 6 del piano F6B: trova le occorrenze della parola in una `String` o in un `AnnotatedString` e dà ciò che serve al campo e alla pagina.
  - `val text: AnnotatedString get()` — Il testo in cui si cerca, l'ultimo passato a [rememberFluidFindState].
  - `val query: String get()` — La parola cercata.
  - `val matches: List<TextRange> by derivedStateOf` — Le occorrenze, nell'ordine del testo. Vuota con la parola vuota.
  - `val total: Int get()` — Quante sono.
  - `val current: Int get()` — La corrente, da zero; zero anche senza occorrenze. Se il testo si è accorciato, l'ultima.
  - `val currentRange: TextRange? get()` — I caratteri della corrente, o null senza occorrenze.
  - `var scrollRequest: FluidFindScrollRequest? by mutableStateOf(null)` — L'ultima richiesta di portare in vista la corrente; null finché non ce n'è stata una.
  - `val navigation: FluidSearchNavigation by derivedStateOf` — Il contatore e le frecce per il campo: la stessa istanza finché corrente e totale non cambiano.
  - `fun onQueryChange(query: String)` — La parola nuova, dal campo. La corrente resta vicina a dov'era: la prima occorrenza della parola nuova che comincia dove cominciava la corrente o dopo (scrivere una lettera in più non fa tornare all'inizio), altrimenti la prima.
  - `fun next()` — La successiva; dall'ultima si torna alla prima. Senza occorrenze non fa niente.
  - `fun previous()` — La precedente; dalla prima si va all'ultima. Senza occorrenze non fa niente.
  - `fun highlight(highlights: FluidFindHighlights): AnnotatedString` — Il testo con gli evidenziatori: [highlights] di serie sono quelli di [FluidFindDefaults.highlights].
- `@Composable fun rememberFluidFindState(text: String, ignoreCase: Boolean = true, ignoreDiacritics: Boolean = true): FluidFindState` — [FluidFindState] su un testo semplice.
- `@Composable fun rememberFluidFindState(text: AnnotatedString, ignoreCase: Boolean = true, ignoreDiacritics: Boolean = true): FluidFindState` — [FluidFindState] su un testo con gli stili: le occorrenze si cercano nelle lettere, e [FluidFindState.highlight] aggiunge gli evidenziatori sopra gli stili che il testo ha già.
