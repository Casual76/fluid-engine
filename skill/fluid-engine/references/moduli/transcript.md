# engine-transcript

**L'ascolto dal paragrafo** (F8.13, idea `ascolto-dal-paragrafo`): una registrazione lunga si legge per paragrafi, e il
testo si tocca per ascoltarlo. Il **contratto dei tempi** (paragrafi, frasi e parole, ognuno col suo inizio e la sua fine),
il **contratto del salto** (un tocco su una parola salta **a quella parola**, uno sull'ora del paragrafo **al suo inizio**),
la costruzione dei paragrafi dai segmenti di un riconoscitore, e la **vista**: i paragrafi caricati a vista, la parola che si
sente accesa nel testo (`FluidSpokenText` di engine-ui), la pagina che **segue la voce senza saltare** e smette appena il dito
scorre, il tasto «Torna alla voce». Pacchetto `dev.antigravity.fluidengine.transcript`. **Opt-in**, sopra engine-ui, senza
librerie.

**Apple.** Le trascrizioni di Podcast su iPhone: «tocca un paragrafo qualsiasi per andare a quel punto della puntata» e
«puoi seguire una puntata leggendo il testo mentre la riproduci» (guida dell'iPhone, iOS 26); la parola che si sente
evidenziata nelle trascrizioni di Memo Vocali.

**La nota di Alessio.** Servono i tempi **per paragrafo e per parola**. Pampa Notes ne ha una versione fatta «non molto bene»:
il tocco salta all'inizio della frase anche quando ci sono i tempi per parola (`SessionScreen.kt:1305`,
`segmentAt(offset).sessionStartMs`). Qui **la parola vince sulla frase, e la frase sul paragrafo**.

L'app dà il testo, i tempi e il lettore (di serie [playback](playback.md)), e decide dove conservare la trascrizione.

Catalogo delle firme: [`../api/transcript.md`](../api/transcript.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| la trascrizione che si ascolta | `FluidTranscriptView(transcript, position = { player.positionMillis }, onSeek = player::seekTo, playing = player.playing)` |
| i paragrafi dai segmenti di un riconoscitore (frasi, con o senza parole) | `FluidTranscript.fromSegments(listOf(FluidTimedSegment(text, start, end, words = …)))` |
| i miei paragrafi, coi miei tempi | `FluidTranscript(listOf(FluidTranscriptParagraph(text, startMillis, words = …, segments = …)))` |
| dove porta un tocco, fuori dalla vista (una ricerca, un segnalibro) | `FluidTranscriptSeek.target(paragraph, offset)`, `FluidTranscriptSeek.paragraphStart(paragraph)` |
| quale paragrafo e quale parola si sentono | `transcript.paragraphAt(ms)`, `transcript.spokenOffset(index, ms)` |
| riportare la pagina alla voce da un mio tasto | `state.follow()` con `val state = rememberFluidTranscriptState()` |
| un'intestazione sopra il testo (il titolo, il lettore) | `FluidTranscriptView(…, header = { item { … } })` |
| i testi in un'altra lingua | `FluidTranscriptStrings(returnToVoice = …, listenFromHere = …, nowPlaying = …, empty = …)` |
| una trascrizione non ancora pronta | `FluidTranscript(emptyList())`: la vista dice «Nessuna trascrizione» (`FluidTranscriptStrings.empty`; vuota, niente frase) |

## Come funziona

**I tempi.** Un `FluidTranscriptParagraph` ha il testo e tre livelli di tempi: le parole (`FluidSpokenWord`, caratteri e
tempi), le frasi (`FluidTranscriptSpan`) e il paragrafo (`startMillis`). Ognuno è facoltativo: l'inizio vero (`start`) è
quello dato, o la prima parola, o la prima frase. Senza nessun tempo il paragrafo si legge e basta (niente tocco).

**Il salto** (`FluidTranscriptSeek.target`). Si cerca la frase del tocco e l'ultima parola cominciata prima del carattere
toccato. Se la parola è **nella stessa frase**: dentro la parola (fino al suo ultimo carattere: la fine è esclusa) si salta a
lei; nello spazio dopo, alla parola dopo; sulla punteggiatura in fondo alla frase, all'ultima parola. Una frase **senza parole** (il riconoscitore non
le ha date) salta al suo inizio, non all'ultima parola della frase prima. Senza parole né frasi, l'inizio del paragrafo.
`offset` è l'indice del carattere toccato, non un punto di inserimento (R3): la vista guarda in quale carattere cade il
dito, perché `getOffsetForPosition` dà lo stesso numero alla metà destra dell'ultima lettera e alla metà sinistra dello
spazio dopo. Lo spazio in fondo a una riga che va a capo resta della parola prima.

**Il paragrafo che si sente** è l'ultimo cominciato: nella pausa fra due paragrafi resta quello di prima (la pagina non salta
avanti nel silenzio); un paragrafo senza tempi non «si sente».

**Seguire senza far saltare la pagina.** Mentre suona, a ogni fotogramma (senza allocare) la vista guarda quale paragrafo e
quale parola si sentono; solo quando cambia la **riga** controlla dov'è: se sta nella fascia dal 18% al 62% dell'altezza
(`FluidTranscriptDefaults`) non fa niente; se ne esce, **una** scrollata morbida la riporta al 28%. Così la pagina si muove una
volta ogni qualche riga, non a ogni parola. Un paragrafo lontano (un salto) si raggiunge per indice. Appena il dito trascina
la lista la pagina **smette di seguire** e in fondo compare «Torna alla voce» (vetro); un tocco sul testo salta e torna a
seguire **senza spostare la pagina** (la riga toccata è già sotto il dito). Con le **animazioni ridotte** la pagina si posa
invece di scorrere. Niente sfocatura: è testo da leggere, e lo scorrimento è la stessa pagina.

**L'evidenziazione.** Il paragrafo che si sente usa `FluidSpokenText`: il detto nel colore pieno, il resto velato, il confine
che avanza parola per parola (la posizione è letta nel disegno: non si ricompone a ogni battito). Gli altri paragrafi sono
testo pieno, da leggere. L'ora del paragrafo che si sente è nell'accento.

**TalkBack.** Ogni paragrafo è un nodo (l'ora e il testo) con l'azione «Ascolta da qui»; quello che si sente dice «In ascolto».

**Vuota** (R1, 26 novembre 2026). Senza paragrafi (la trascrizione non è ancora pronta, o non c'è) la vista mostra sotto
l'intestazione dell'app la frase `FluidTranscriptStrings.empty` («Nessuna trascrizione»), invece di una pagina bianca.

## Trappole

- **`position` è una lambda** che legge uno stato (la `positionMillis` di un `FluidPlaybackController`): la vista la legge a ogni
  fotogramma fuori dalla composizione. Un valore passato direttamente ricomporrebbe la pagina a ogni battito.
- **Le chiavi** dei paragrafi (`key`) devono stare in un `Bundle` (una stringa, un numero): sono le chiavi della lista.
- **La vista è una lista**: non va dentro un'altra lista che scorre senza un'altezza (come ogni `LazyColumn`).
- **I tempi per parola** di un riconoscitore spesso non coincidono col testo corretto a mano: `fromSegments` cerca le parole
  nel testo della frase in ordine, e salta quelle che non trova senza spostare le altre.

## Cosa è verificato dai test

`engine-transcript/src/test/.../FluidTranscriptTest.kt`: il tocco su una parola va a quella parola e non alla frase (il caso
di Pampa Notes), l'ultimo carattere della parola a lei e lo spazio dopo alla parola dopo (R3: prima la prova dava lo spazio
per «ultimo carattere»), la punteggiatura all'ultima; senza parole la frase, senza frasi il paragrafo,
senza tempi niente; l'ora al paragrafo; il paragrafo che si sente resta nella pausa e salta quelli senza tempi; i paragrafi dai
segmenti vanno a capo alle pause, ogni sei frasi e quando cambia chi parla, con le parole al loro posto. Nella galleria
(`F8p13AudioUiTest`), col dito vero sulla lezione coi tempi veri: il tocco su «laghi» salta a «laghi»; in
`R3m1TranscriptTest`, col testo impaginato davvero, la metà destra dell'ultima lettera di una parola resta sua e tutte e
due le metà dello spazio vanno alla parola dopo (R3); l'ora e TalkBack al
paragrafo; senza i tempi per parola, al paragrafo; la pagina segue la voce, si ferma per il dito, torna con «Torna alla voce»;
con le animazioni ridotte si posa in un fotogramma.

## Dove guardare

- `engine-transcript/src/main/kotlin/dev/antigravity/fluidengine/transcript/FluidTranscript.kt` — il contratto dei tempi e del
  salto, i paragrafi dai segmenti.
- `FluidTranscriptView.kt` — la vista, il seguire, «Torna alla voce».
- `engine-ui/.../FluidSpokenText.kt` — il testo che si accende.

## Dipendenze e configurazione minima

```groovy
implementation project(':engine-transcript')   // porta engine-ui; nessuna libreria
```

Licenze: nessuna opera di terzi.
