<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/transcript.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-transcript

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-transcript/transcript/FluidTranscript.kt`

- `data class FluidTranscriptSpan(val start: Int, val end: Int, val startMillis: Long, val endMillis: Long)` — Un tratto di testo con il suo tempo: una frase, un segmento del riconoscitore. [start] e [end] sono caratteri del testo del paragrafo (fine esclusa), [startMillis] e [endMillis] tempi dell'audio.
- `data class FluidTranscriptParagraph(val text: String, val startMillis: Long? = null, val endMillis: Long? = null, val words: List<FluidSpokenWord> = emptyList(), val segments: List<FluidTranscriptSpan> = emptyList(), val speaker: String? = null, val key: Any? = null)` — Un **paragrafo** della trascrizione (F8.13, idea `ascolto-dal-paragrafo`): il testo e i suoi tempi, a tre livelli, dal più fine al più grosso.
  - `val start: Long? get()` — L'inizio vero: quello dato, o la prima parola, o la prima frase.
  - `val end: Long? get()` — La fine vera: quella data, o l'ultima parola, o l'ultima frase.
  - `val hasTimes: Boolean get()` — Vero se il paragrafo ha almeno un tempo: si può toccare per ascoltare.
- `class FluidTranscript(val paragraphs: List<FluidTranscriptParagraph>)` — Una **trascrizione**: i paragrafi in ordine di tempo, con le domande che la vista fa a ogni battito («quale paragrafo si sente», «quale parola») risolte con una ricerca binaria. Si costruisce una volta (con [fromSegments], o dai paragrafi dell'app) e si tiene: cambia solo quando cambia il testo.
  - `val hasTimes: Boolean` — Vero se almeno un paragrafo ha i tempi.
  - `val hasWordTimes: Boolean` — Vero se almeno un paragrafo ha i tempi per parola.
  - `fun paragraphAt(positionMillis: Long): Int` — L'indice del paragrafo che si sente a [positionMillis]: l'ultimo cominciato. -1 prima del primo, o senza tempi. Una pausa fra due paragrafi resta sul paragrafo di prima (la pagina non salta avanti nel silenzio).
  - `fun spokenOffset(index: Int, positionMillis: Long): Int` — Il carattere fin dove il paragrafo [index] è stato detto a [positionMillis]: l'inizio della parola che si sente (o della frase, senza parole). -1 se il paragrafo non ha tempi fini. È il punto che la vista tiene in vista.
  - `companion object`
    - `val Empty: FluidTranscript` — La trascrizione vuota.
    - `fun fromSegments(segments: List<FluidTimedSegment>, paragraphBreakMillis: Long = 2_000L, maxSegmentsPerParagraph: Int = 6): FluidTranscript` — I paragrafi dai **segmenti** di un riconoscitore (una frase per segmento, con o senza le parole): il testo dei segmenti si unisce con uno spazio, e si va a capo - a una **pausa** di almeno [paragraphBreakMillis] fra un segmento e il successivo (2 s di serie: è la pausa di chi cambia argomento in una lezione; a occhio, 25 novembre 2026, come `TranscriptParagraphs` di Pampa Notes);
- `data class FluidTimedSegment(val text: String, val startMillis: Long, val endMillis: Long, val words: List<FluidTimedWord> = emptyList(), val speaker: String? = null)` — Un segmento di un riconoscitore: il testo, i suoi tempi, le parole se ci sono, chi parla.
- `data class FluidTimedWord(val text: String, val startMillis: Long, val endMillis: Long)` — Una parola con i suoi tempi, come la dà un riconoscitore.
- `object FluidTranscriptSeek` — Il **salto** (F8.13): dove porta un tocco sul testo di un paragrafo. È la regola, fuori dalla vista, perché l'app possa usarla anche altrove (un risultato di ricerca, un segnalibro).
  - `fun target(paragraph: FluidTranscriptParagraph, offset: Int): Long?` — Dove saltare per un tocco sul carattere [offset] di [paragraph]: 1. con i tempi per parola, **la parola toccata** (il tocco sullo spazio dopo una parola va alla parola dopo, nella stessa frase; la punteggiatura in fondo resta sull'ultima); 2. in una frase senza parole (o senza parole in tutto il paragrafo), l'inizio **della frase toccata**; 3. senza nessuno dei due, l'inizio **del paragrafo**; 4.
  - `fun paragraphStart(paragraph: FluidTranscriptParagraph): Long?` — Dove saltare per un tocco sull'ora (o su TalkBack, «Ascolta da qui»): l'inizio del paragrafo.

## `engine-transcript/transcript/FluidTranscriptView.kt`

- `data class FluidTranscriptStrings(val returnToVoice: String = "Torna alla voce", val listenFromHere: String = "Ascolta da qui", val nowPlaying: String = "In ascolto", val empty: String = "Nessuna trascrizione")` — I testi della vista, in italiano di serie.
- `class FluidTranscriptState(val list: LazyListState)` — Lo stato di una [FluidTranscriptView]: la lista e se la pagina **segue la voce**. Segue di serie; smette appena il dito scorre la lista (si sta leggendo altrove: la pagina non si strappa di mano), e riprende con un tocco sul testo, con «Torna alla voce» o con [follow].
  - `var following: Boolean by mutableStateOf(true)` — Vero se la pagina tiene in vista la parola che si sente.
  - `fun follow()` — Torna a seguire la voce: la pagina ci va.
  - `fun stopFollowing()` — Smette di seguire (come fa da sé quando il dito scorre).
- `@Composable fun rememberFluidTranscriptState(list: LazyListState = rememberLazyListState()): FluidTranscriptState` — Lo stato per questa composizione.
- `object FluidTranscriptDefaults` — Le misure della vista.
  - `const val BandTop: Float` — La fascia in cui la riga che si sente può stare senza che la pagina si muova: dal 18% al 62% dell'altezza. Uscita dalla fascia, la pagina la riporta al [RestFraction]. È quello che tiene ferma la pagina mentre si legge: si muove una volta ogni qualche riga, non a ogni parola. A occhio, 25 novembre 2026.
  - `const val BandBottom: Float`
  - `const val RestFraction: Float`
- `@Composable fun FluidTranscriptView(transcript: FluidTranscript, position: () -> Long, onSeek: (Long) -> Unit, modifier: Modifier = Modifier, playing: Boolean = false, state: FluidTranscriptState = rememberFluidTranscriptState(), contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12…, backdrop: GlassBackdropState = currentGlassBackdrop(), strings: FluidTranscriptStrings = FluidTranscriptStrings(), header: (LazyListScope.() -> Unit)? = null)` — La **trascrizione che si ascolta** (F8.13, idea `ascolto-dal-paragrafo`): i paragrafi di [transcript] in una lista pigra (si compongono solo quelli in vista, anche in una lezione di due ore), ognuno con la sua ora; il paragrafo che si sente ha l'ora nell'accento e **la parola che si sente accesa** nel testo (`FluidSpokenText`: il detto pieno, il resto velato); gli altri sono testo pieno, da leggere.
