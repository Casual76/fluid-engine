# Contenuto: caricamento, segni piccoli, testo, visore e riordino

Terza scheda della famiglia «Contenuto» ([`contenuto.md`](contenuto.md) ha l'indice, il «Scegliere in fretta» e le regole trasversali; [`contenuto-righe-liste.md`](contenuto-righe-liste.md) ha righe, liste, impostazioni, card e aperture).
Qui: gli scheletri, lo spinner e le barre, `FluidStepProgress`, i segni piccoli (`FluidLiveDot`, `FluidSelectionMark`, `FluidPagerDots`), il testo (`FluidMarkdown` e il suo parser, `FluidRevealingText`, `FluidSpokenText`),
il visore di immagini con lo zoom e il riordino con trascinamento. Firme complete: [`../api/contenuto.md`](../api/contenuto.md).

## Stato di caricamento

### Scheletri: `FluidSkeleton`, `FluidSkeletonText`, `FluidSkeletonRow`, `FluidSkeletonTile`, `FluidSkeletonList`

**Cos'è.** Il segnaposto di un dato che sta arrivando. Una rotellina nel vuoto dice solo «sto facendo qualcosa»; le sagome dicono anche *cosa* sta per comparire, e quando compare non salta niente perché il suo posto c'era già. Nata in un'app di
trasporti, il cui KDoc diceva che il suo posto era l'engine.

**Firma essenziale.**
```kotlin
FluidSkeleton(height = FluidSkeletonDefaults.LineHeight, shape = FluidSkeletonDefaults.Shape)       // un blocco; la larghezza dal modifier
FluidSkeletonText(lines = 3, lineHeight = FluidSkeletonDefaults.LineHeight, spacing = 9.dp, lastLineFraction = 0.64f)
FluidSkeletonRow(leading = FluidSkeletonLeading.Tile, lines = 2, trailing = false, variant = 0)
FluidSkeletonTile(aspectRatio = 1f, caption = true, shape = FluidSkeletonDefaults.TileShape)
FluidSkeletonList(rows = 5, leading = FluidSkeletonLeading.Tile, lines = 2, trailing = false, label: String? = null)
enum class FluidSkeletonLeading { None, Tile, Circle }
```

**Uso** (da `CapsulesAndPartsDemo`):
```kotlin
if (loading) {
  FluidListGroup { FluidSkeletonList(rows = 3, trailing = true, label = "Caricamento delle partenze") }
  Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    FluidSkeletonTile(modifier = Modifier.weight(1f)); FluidSkeletonTile(modifier = Modifier.weight(1f), aspectRatio = 1.4f)
  }
  FluidSkeletonText(lines = 3)
} else { /* i dati occupano lo stesso spazio: non salta niente */ }
```

**Comportamento.**
- **Respira, non scorre.** È un'opacità che va e viene (`FluidMotion.PulseMillis`, 900 ms per fase, lo stesso respiro del `FluidLiveDot`): fra il 6% (`LowAlpha`) e il 13% (`HighAlpha`) del colore `onSurface`, mai un grigio — così sta bene sulla pagina
  chiara, su AMOLED e dentro un pannello di vetro. Una banda di luce che attraversa, sotto venti segnaposto, sono venti strisce che corrono. Il respiro è **uno per gruppo** (riga, tessera, paragrafo, lista creano un solo clock e lo danno a tutti i blocchi:
  dieci righe ne hanno uno, non cinquanta) e batte in fase con l'orologio fra gruppi diversi. Letto nel disegno, non in composizione.
- **La misura è quella vera**: `FluidSkeletonRow` ha l'altezza minima di una riga (56/72/88 dp per 1/2/3 righe di testo), piastrella da 32 dp, titolo al 72% o 56% della larghezza **alternando** da una riga all'altra (con `variant`; `FluidSkeletonList`
  passa l'indice) perché una colonna di rettangoli identici sembra una tabella; l'ultima riga di un paragrafo è corta (`lastLineFraction`, tenuta fra 0,1 e 1).
- **Muti per TalkBack**: sono un velo. Chi vuole dire «sto caricando» lo dice **una volta**, con `label` di `FluidSkeletonList` o con un testo suo; le righe non hanno parole e una frase per riga sarebbe rumore.
- Movimento ridotto: velo fermo a `RestAlpha` (9%), e nessuna transizione infinita viene creata.

**Trappole.** Uno scheletro dentro un `FluidListGroup` non porta il separatore: la lista di scheletri è una colonna di righe senza fili (di proposito, sono sagome). La larghezza di `FluidSkeleton` la decide il `modifier`
(`fillMaxWidth(0.6f)`, `width(44.dp)`): senza, prende quella del genitore.

**Non usarlo quando.** Non sai la forma di quello che arriva (`FluidLoadingBlock`), o l'attesa è un'azione di breve durata (`FluidButton(loading = true)`).

**Versione.** 3.0.0.

### Spinner e barre (`FluidProgress.kt`)

- `FluidSpinner(size = 20.dp, color = onSurfaceVariant, progress = null)`: **otto raggi** affusolati che scalano, e l'intera rampa *scatta* di un raggio alla volta, non ruota (lo scatto è la parte che si riconosce); un giro completo in 900 ms
  (`FluidMotion.SpinnerMillis`). Con `progress` (0..1) disegna solo la parte di corona corrispondente e non gira: è per il pull-to-refresh mentre il dito è ancora giù. `FluidLoading` è lo stesso a 24 dp nel colore d'accento.
- `FluidProgressBar(progress: () -> Float, color, height = 4.dp)`: capsula sottile, **la pista è il colore del testo al 10%** (non un colore di contenitore: funziona su qualunque superficie), senza indicatore di arresto né gap. Il riempimento si legge
  **sempre almeno a 3:1** sulla pista (3.0), qualunque sia `color`: se non basta si scurisce (sul chiaro) o si schiarisce (sullo scuro) del minimo che serve tenendo la tinta (`legibleFill`); sopra un pannello di vetro la pista poggia sulla pellicola del vetro.
  `progress` è una lambda letta nel disegno: aggiornare la barra non ricompone.
- `FluidIndeterminateBar(color, height = 3.dp)`: un segmento (35% della larghezza) che attraversa con andamento morbido ai due capi, 1100 ms (`FluidMotion.IndeterminateMillis`); quello di Material accelera fuori campo e riparte di colpo.

**Trappole.** **Né la barra né lo spinner pubblicano una semantica di avanzamento**: per TalkBack una `FluidProgressBar` nuda è muta. Mettigli un `Modifier.semantics { progressBarRangeInfo = … }` o un testo accanto; `FluidStepProgress` invece la pubblica.
Su un fondo che cambia colore il riempimento è calcolato una volta per accento e palette (non a ogni fotogramma): non passare un `color` diverso a ogni ricomposizione.

### `FluidStepProgress`

**Cos'è.** L'intestazione di una procedura a passi: «Passo 2 di 5» e una barra che dice a che punto si è, senza rubare la scena al titolo. Due forme dello stesso pezzo: barra continua (di serie) o a segmenti.

**Firma essenziale.**
```kotlin
FluidStepProgress(step: Int, steps: Int, label: String = FluidStepProgressDefaults.label(step, steps),
  detail: String? = null, segmented: Boolean = false, pulseCurrent: Boolean = false,
  color: Color = MaterialTheme.colorScheme.primary, labels: List<String>? = null)
```

**Uso** (da `CapsulesAndPartsDemo`):
```kotlin
FluidStepProgress(step = step, steps = 5)
FluidStepProgress(step = step, steps = 5, segmented = true, pulseCurrent = true, detail = "Converto il file",
  labels = listOf("File", "Formato", "Opzioni", "Converti", "Fine"))
```

**Comportamento.**
- **Il passo 2 di 5 è il 40%, non il 25%**: «passo 2» dice che ci si è arrivati e la barra deve confermarlo. A passo 1 di 5 vuota sembrerebbe che non si sia partiti, e all'ultimo passo non è mai piena in meno di `steps/steps`: è piena.
- Si muove con la molla della casa (`FluidMotion.smooth`) e non salta di passo in passo; nella forma a segmenti il riempimento corre da un segmento al successivo con lo stesso numero. Movimento ridotto: senza passaggi.
- **A segmenti solo se richiesti *e* se ce ne stanno**: da 2 a 12 passi (`FluidStepProgressDefaults.MaxSegments`); oltre, e con un passo solo, ripiega da sola sulla barra continua. Segmenti alti 4 dp con 4 dp di spazio.
- `pulseCurrent`: il passo in corso respira fra il 55% e il pieno (`FluidMotion.PulseFillLowAlpha`, più alto del 35% di un pallino: a 0,35 sembrerebbe il passo *da fare*). Per un'operazione che sta girando e altrimenti sembra ferma, **non** per un modulo da compilare. Solo a segmenti.
- `labels` (3.0.1): un nome per passo sotto la barra, i fatti e quello in corso nel colore del testo, quello in corso in grassetto, i successivi attenuati. Se la lunghezza non è `steps`, **nessun nome** (silenzioso).
- **TalkBack**: un blocco solo, letto come `label`, con `progressBarRangeInfo` (passo corrente su `1..steps`) e il nome del passo in corso come stato (se ci sono `labels`).
- Le cifre sono tabulari: «Passo 9 di 14» e «Passo 10 di 14» non spostano quello che hanno a destra. Un'app in un'altra lingua passa la sua frase come `label`.

**Trappole.** `step` fuori da `0..steps` si ferma ai limiti. `labels` di lunghezza sbagliata sparisce senza errore.

**Versione.** 3.0.0; `labels` 3.0.1.

## Segni piccoli

### `FluidLiveDot`

**Cos'è.** Il pallino che pulsa accanto a un dato vivo (i minuti di un autobus seguiti da un feed, «in diretta», un lettore che registra). Tre app se lo erano scritto a 700 e 900 ms.

**Firma essenziale.** `FluidLiveDot(color = MaterialTheme.colorScheme.primary, size = FluidLiveDotDefaults.Size /* 7 dp */, pulsing = true, contentDescription: String? = null)`.

**Uso.** `FluidLiveDot(contentDescription = "In diretta")`; `FluidLiveDot(color = MaterialTheme.colorScheme.error)` per il ritardo; `FluidLiveDot(color = onSurfaceVariant, pulsing = false)` per un dato vivo che ora non arriva.

**Comportamento.** Il respiro è **uno** (`FluidMotion.PulseMillis` 900 ms) ed è una funzione dell'orologio, non del momento della composizione: dieci pallini che entrano a fotogrammi diversi battono comunque insieme
(`fluidPulsePhaseOffsetMillis`), perché cose vive sulla stessa schermata che non battono insieme sembrano rotte. Pulsa **solo l'opacità**, fra 0,35 (`PulseLowAlpha`) e pieno (scala o alone muoverebbero il testo accanto). Fermo (movimento ridotto o
`pulsing = false`) è **pieno**. Quando è fermo non crea nemmeno la transizione infinita (un'animazione che gira per niente tiene sveglio il thread di disegno). Decorativo per TalkBack **salvo** `contentDescription`: dillo una volta, di solito lo dice già il testo accanto.

### `FluidSelectionMark`

**Cos'è.** Il segno della selezione multipla: un cerchio vuoto quando la riga si può scegliere, un disco pieno con la spunta quando è scelta. Pampa notes lo faceva con due icone che si scambiavano, e quella **saltava** (cambio di vettore, nessun fotogramma in mezzo).

**Firma essenziale.** `FluidSelectionMark(selected, size = FluidSelectionMarkDefaults.Size /* 24 dp */, onPhoto = false, tint = Color.Unspecified, contentDescription: String? = FluidSelectionMarkDefaults.description(selected))`.

**Uso** (da `CapsulesAndPartsDemo`): `FluidSelectionMark(selected = picked, onPhoto = true, modifier = Modifier.align(Alignment.TopStart).padding(6.dp))` sull'angolo di una miniatura;
`FluidSelectionMark(selected = rowSelected, size = 20.dp, tint = Color.White)` sopra una tessera colorata.

**Comportamento.** Disco, anello e spunta sono **un disegno solo** (un nodo, letto nel disegno): una griglia di cento celle in selezione non ricompone niente mentre i segni si riempiono — la spunta è disegnata a mano e non un'`Icon`
perché cento `Icon` spente sono cento nodi per niente. Il disco si riempie con la molla viva `FluidMotion.fluid` (un 3% di rimbalzo è energia a questa taglia), la spunta cresce dal centro; movimento ridotto: cambio di stato senza passaggi.
`onPhoto`: anello **bianco** su un velo scuro (`PhotoScrimAlpha` 0,28) qualunque sia la foto, e l'anello bianco resta anche a segno pieno (è lui a staccare il disco dalla foto). `tint`: colore del disco e dell'anello su una superficie piena che l'accento non
leggerebbe; **la spunta prende da sola il colore che si legge sul disco** (scuro su disco chiaro, bianco su scuro). L'anello scala col lato (1,75 dp a 24).

**Trappole.** Non gestisce il tocco: lo gestisce la riga o la cella. Il `contentDescription` di serie dice «Selezionato»/«Non selezionato»: se è già la riga a dire lo stato (`Modifier.semantics { selected = … }`), passa `null`, o lo dici due volte.
In una `FluidListRow` va nel `leading` con `leadingTile = false` (dedotto: con la piastrella il segno finisce nei 19 dp interni, più piccoli dei suoi 24).

### `FluidPagerDots`

**Cos'è.** Il punto-per-pagina di un pager: quello corrente più grande e pieno, che **segue il dito** invece di scattare a pagina assestata (cinque `Box` che confrontavano `index == currentPage` cambiavano il punto un terzo di secondo dopo che l'occhio aveva già visto la pagina).

**Firma essenziale.**
```kotlin
FluidPagerDots(state: PagerState, color = onSurface, activeColor = color, maxVisible = 7, hideForSinglePage = true, pageLabel = { page, count -> "Pagina $page di $count" })
FluidPagerDots(pageCount: Int, position: () -> Float, /* stessi */)      // per un pager che non è un PagerState
```

**Comportamento.** Ogni punto prende il suo peso dalla posizione frazionaria (a metà fra la 2 e la 3 sono entrambi «mezzo correnti»): passa da 7 a 10 dp e dal 35% al 95% di opacità. **Il layout non si muove mai**: ogni punto ha un posto fisso da 10 dp + 8 dp di
spazio, e dentro cresce e cala; la larghezza della fila non cambia durante uno scorrimento. Tutto è un disegno solo con `position` letta nel disegno. Oltre `maxVisible` (7) la fila scorre e i punti ai bordi si rimpiccioliscono **in modo continuo**
(non un interruttore «c'è altro da questo lato», che farebbe saltare il primo punto a un terzo nel fotogramma in cui la fila si muove). Con una pagina sola non disegna niente (`hideForSinglePage`). Non è un controllo: non si tocca. Per TalkBack è un'etichetta
«Pagina 2 di 5». `color` sopra una foto: bianco.

## Testo

### `FluidMarkdown` e `FluidMarkdownParser`

**Cos'è.** Il Markdown disegnato, senza libreria esterna e senza dipendenza dall'assistente: una nota, le novità di una versione, il testo di una guida, la risposta di un modello. Il **parser** (`FluidMarkdownParser`) è puro Kotlin, senza una riga di Compose:
è il pezzo che le app avevano scritto ognuna per conto suo (tre `MarkdownLite` da un centinaio di righe, una libreria esterna per le note, un secondo renderer a blocchi per lo streaming di una chat) e si prova sul computer. Dalla 3.0.0 vive in `engine-ui` (era in `engine-ai-ui`).

**Firma essenziale.**
```kotlin
FluidMarkdown(markdown: String, streaming: Boolean = false, animateFirst: Boolean = streaming,
  style: FluidMarkdownStyle = FluidMarkdownDefaults.style(), strings: FluidMarkdownStrings = FluidMarkdownStrings(),
  options: FluidMarkdownOptions = FluidMarkdownOptions(), onLinkClick: ((String) -> Unit)? = null, selectable: Boolean = false)

FluidMarkdownDefaults.style(textColor, mutedColor, blockSpacing = 8.dp, body = bodyLarge, headings = emptyList())
FluidMarkdownDefaults.documentHeadings()        // 4 misure per `#`…`####`, da passare come `headings`
FluidMarkdownOptions(hideChipMarkers = false, softBreak = " ")
FluidMarkdownParser.parse(markdown, options): List<FluidMdBlock>     // l'albero
FluidMarkdownParser.plainText(markdown, includeCode = false, bullets = false, options)   // senza segni
```
Tutti i parametri: `api/contenuto.md`.

**Uso.** Una nota: `FluidMarkdown(markdown = note, style = FluidMarkdownDefaults.style(headings = FluidMarkdownDefaults.documentHeadings()), selectable = true, onLinkClick = { open(it) })`.
Una risposta che arriva: `FluidMarkdown(text, streaming = streaming, animateFirst = freshStart)` (così fa la chat di `engine-ai-ui`).

**Cosa sa renderizzare (dal parser e dai suoi test).**

| costrutto | supportato | note |
|---|---|---|
| paragrafi | sì | gli a capo singoli diventano `options.softBreak` (uno spazio, come in Markdown); con `softBreak = "\n"` le righe restano come scritte |
| titoli `#`…`######` | sì | solo la forma con i cancelletti **e uno spazio** (`#tag` non è un titolo); i cancelletti di chiusura si tolgono solo se staccati da uno spazio (`## Estate ##`), `Imparare C#` resta intero |
| grassetto `**x**` / `__x__`, corsivo `*x*` / `_x_`, barrato `~~x~~` | sì, anche annidati (`**a *b***`) | `_` apre solo all'inizio di una parola (`snake_case_name` è un nome); un asterisco circondato da spazi è letterale (`2 * 3 * 4`); `~x~` con una tilde sola non è barrato |
| codice in linea `` `x` `` | sì | anche con più apici (`` `` a ` b `` ``); dentro il codice niente è Markdown; un solo spazio di margine ai due lati si toglie |
| blocchi di codice ``` e `~~~` | sì | la lingua è la prima parola dopo l'apertura; **un blocco non chiuso è già un blocco** (`closed = false`), non tre apici nudi |
| link (`[testo]` seguito dall'indirizzo fra parentesi tonde) | sì | anche con parentesi annidate; indirizzi nudi `http://`/`https://` diventano link (la punteggiatura che chiude la frase non è dell'indirizzo) |
| immagini (`!` davanti a un link) | **no, come immagine** | restano il loro testo alternativo, sottolineato come un link (l'URL se l'alt è vuoto): un'immagine non si disegna in una card |
| elenchi puntati (`-` `*` `+` `•`) e numerati (`1.` `1)`) | sì | il numero di partenza si conserva (uno che comincia da 3 resta da 3); annidati per rientro (tab = 4); una riga rientrata continua la voce; una riga vuota fra due voci non chiude l'elenco |
| cose da fare `- [ ]`, `- [x]`, `- [X]` | sì (3.0.1) | serve lo spazio dopo la parentesi (`[x]y` è testo); casella disegnata, non un carattere |
| citazioni `>` | sì | contengono blocchi, anche annidate; il testo è attenuato |
| tabelle | sì | intestazione + riga dei trattini con **lo stesso numero di celle**; allineamenti `:--` `:-:` `--:`; `\|` per una barra dentro una cella; un'intestazione senza la riga dei trattini è ancora un paragrafo e diventa tabella quando la riga arriva |
| righe orizzontali `---` `***` `___` (anche con spazi) | sì | |
| un numero davanti a una frase **in mezzo a un paragrafo** | non apre un elenco | un elenco numerato interrompe un paragrafo solo se comincia da 1 |
| titoli «setext» (`Titolo` sottolineato da `===` o `---`) | **no** | `---` sotto un testo è una riga orizzontale; `===` resta testo |
| codice a rientro di quattro spazi | **no** | è un paragrafo (o la continuazione di una voce) |
| HTML, note a piè di pagina, definizioni di link `[x]: url`, matematica | **no** | restano testo letterale |
| a capo forzato (due spazi o `\` a fine riga) | **no** | vale come un a capo qualunque (`softBreak`) |
| escape con barra rovescia | sì | per `` \ ` * _ { } [ ] ( ) # + - . ! \| ~ > `` |
| marcatori `[[id]]`, `[[id:valore]]` | restano **testo** | `hideChipMarkers = true` li toglie: li accende l'interfaccia dell'assistente (sono i chip sotto la risposta); in una nota `[[cosi']]` è un collegamento fra pagine |

**Perché tollera lo stream.** Un flusso si ferma ovunque, quindi ogni costrutto aperto e mai chiuso ha **una sola lettura innocua**: un `**` o un `` ` `` senza chiusura resta testo (non si porta dietro il paragrafo), un blocco di codice senza chiusura
è già un blocco, un'intestazione di tabella senza trattini è un paragrafo.

**Comportamento del disegno.**
- **Un solo disegno per il testo che arriva e per quello arrivato.** Con `streaming` le parole nuove sfumano dentro (`FluidRevealingText`); quando `streaming` torna falso il nodo è lo stesso: non c'è un secondo renderer che prende il posto del primo, quindi nessun
  salto di misure. `animateFirst` (di serie = `streaming`): anche il primo testo si rivela (risposta appena nata); falso per una risposta vecchia riaperta, già letta. Un testo che non arriva a pezzi (una nota) lascia `streaming = false`: nessuna sfumatura.
  I blocchi presenti alla prima composizione sono «già letti»; quelli che nascono dopo, mentre il testo arriva, si rivelano parola per parola (anche le voci di elenco aggiunte).
- **Titoli: due misure di serie**, non sei (`#`/`##` = `titleMedium` semigrassetto, dal `###` `titleSmall`): in una risposta un titolo separa i paragrafi, non costruisce una gerarchia; `## Estate` a 45 sp in mezzo a una chat è un cartello. Una nota o un
  changelog vuole la gerarchia: `style(headings = documentHeadings())` (`titleLarge`, `titleMedium`, `titleSmall`, `bodyLarge` semigrassetto; l'ultima vale anche per i livelli più profondi).
- **Codice**: etichetta in testa (la lingua o `strings.codeLabel`), tasto «copia» (clipboard, poi «copiato» per 1,6 s), testo monospaziato che **scorre in orizzontale** invece di essere tagliato (`softWrap = false`: una riga tagliata a metà non è codice, è un'immagine di codice).
  Il tasto «copia» **non compare finché il blocco è aperto e il testo sta arrivando** (copierebbe mezza funzione). Raggio `FluidRadius.Control`.
- **Tabelle** scorrono in orizzontale: ogni colonna ha almeno 128 dp e la tabella mai meno dello schermo (colonne dividendo lo schermo restavano larghe un pollice con il contenuto nei puntini); intestazione semigrassetto, righe separate da un filo.
- **Link**: colore `linkColor` sottolineato; con `onLinkClick = null` apre l'indirizzo col gestore di sistema; con la callback il tocco va **solo a lei** (aprirlo è affar tuo).
- **`selectable`** (di serie no): in una card o in una bolla la selezione si prenderebbe il tocco lungo del contenitore.
- **`FluidMarkdownStyle`**: chi vuole il testo su un altro fondo (sopra un cielo di onboarding, dentro una bolla colorata) cambia `textColor` e `mutedColor` e il resto segue. Un'app tradotta passa tutte le `FluidMarkdownStrings`.

**Trappole.**
- I titoli **non sono `heading()`** per TalkBack (il renderer non lo dichiara): per una navigazione per titoli serve altro.
- `FluidMarkdownStrings.codeBlockDescription` esiste ma **non è letta da nessuna parte** (dichiarata e mai usata): un blocco di codice non ha ancora la descrizione «Blocco di …»; TalkBack legge il testo e il tasto (`copyDescription`).
- Cambiare del tutto il `markdown` (un'altra conversazione, un'altra nota) con `streaming = true` anima i blocchi oltre il numero che c'era alla prima composizione (dedotto da `firstCount = remember { blocks.size }`): per un testo nuovo metti il composable dentro `key(id)`.
- `hideChipMarkers` è **spento di serie**: sono i marcatori dell'assistente. `engine-ai-ui` li accende con `FluidAssistantMarkdownOptions`.

**Non usarlo quando.** Ti serve solo il testo senza segni (voce di sistema, anteprima in cronologia, condivisione in chiaro: `FluidMarkdownParser.plainText`, che salta il codice di serie e tiene una voce per riga), o l'HTML.

**Versione.** 3.0.0 (sale da `engine-ai-ui` con `selectable`, `headings`, `documentHeadings`, `plainText(options)`); cose da fare 3.0.1.

### `FluidRevealingText`

**Cos'è.** Testo che *arriva*: le parole nuove sfumano dentro, quelle già viste restano ferme. Un unico `Text`, un solo layout; la sfumatura è una maschera `DstOut` sui glifi delle parole nuove, in un layer fuori schermo acceso **solo mentre c'è qualcosa da rivelare**.

**Firma essenziale.** `FluidRevealingText(text: AnnotatedString, style: TextStyle, color: Color, maxLines, overflow, revealMillis = FluidRevealMillis /* 180 */, rewriteMillis = FluidRewriteMillis /* 120 */, softWrap = true, animateFirst = false, reveal = true)`.

**Comportamento.** Solo alfa (un `Text` già impaginato non può spostare una parola). I «token» sono le sequenze massimali di non-spazio; si tiene il prefisso comune col testo precedente e ciò che segue nasce in attesa. Se il nuovo testo diverge **prima** della
fine del vecchio è una **riscrittura** (il riconoscitore che cambia idea, il modello che chiude un `**`): la coda dalla divergenza rinasce, più veloce (120 ms, perché al posto della parola vecchia ce n'è già una nuova da leggere). Una parola tagliata («Prima» poi «vera»)
tiene la testa e fa nascere solo la coda, così i pezzi di parola non lampeggiano a ogni chunk. Il **primo testo mai visto non si anima** (riapertura di una chat) salvo `animateFirst`. `reveal = false`: un `Text` come un altro, **stesso nodo**, quindi nessun rifacimento di layout
quando passa da «arriva» a «è arrivato». Movimento ridotto: durate zero, tutto compare com'è. L'orologio è un `LaunchedEffect` che gira solo finché c'è lavoro e scrive in uno stato letto solo nel disegno: invalida il disegno, non ricompone.

### `FluidSpokenText`

**Cos'è.** Il testo che si accende mentre l'audio va: il detto nel colore pieno, il da dire velato, il confine che si muove parola per parola. È l'unica cosa che tiene insieme una registrazione e la sua trascrizione.

**Firma essenziale.**
```kotlin
FluidSpokenWord(start: Int, end: Int, startMs: Long, endMs: Long)      // offset di carattere nel testo, e tempi
FluidSpokenText(text: String, words: List<FluidSpokenWord>, positionMs: () -> Long,
  style = LocalTextStyle.current, spokenColor = onSurface, pendingColor = onSurfaceVariant, onTextLayout = {})
fluidSpokenChars(words, positionMs): Int
```

**Comportamento.** **La posizione è una lambda, non un parametro**: a cinque battiti al secondo un parametro rimisurerebbe il testo cinque volte al secondo (su dieci righe un fotogramma perso ogni volta); letta dentro il disegno invalida solo il disegno. Due passate: tutto in `pendingColor`
(che è il `Text` vero, quindi accessibilità, selezione e layout) e poi la parte detta in `spokenColor`, ritagliata sulle righe intere più il rettangolo parziale della riga corrente (da destra a sinistra il taglio parte dall'altro lato). `fluidSpokenChars` fa una ricerca binaria sulle
parole e, dentro una parola, avanza **in proporzione** (l'evidenziazione scorre invece di scattare); nel silenzio fra due parole resta accesa la precedente, tutta; senza parole non si accende niente.

**Trappole.** `words` deve essere in **ordine di tempo** (ricerca binaria). `start`/`end` sono indici nel `text` passato. Per TalkBack è un `Text` normale: legge tutto, non segue l'audio.

## Visore di immagini

### `FluidImageViewer`, `Modifier.fluidZoomable`, `FluidZoomState`, `FluidZoom`

**Cos'è.** Il visore a tutto schermo, **dentro l'host dei modali**: una o più immagini, pizzico e doppio tocco per ingrandire, spostamento limitato all'immagine, congedo trascinando in giù. Tre app ne avevano uno dentro un `Dialog` di piattaforma; nessuno aveva i limiti dello spostamento
(l'immagine si poteva spingere fuori dallo schermo) e tutti passavano il dito al pager o al congedo con la regola «se non è zoomato, non consumo» scritta a mano e in modo diverso. **Non carica niente**: `page` è l'immagine che l'app disegna (Coil, un `Bitmap`, un `Image`).

**Firma essenziale.**
```kotlin
FluidImageViewer(visible: Boolean, onDismissRequest: () -> Unit, pageCount: Int = 1, initialPage: Int = 0,
  onPageChange: (Int) -> Unit = {}, title: (page, pageCount) -> String? = /* "2 di 5" se più di una */, maxScale: Float = FluidZoom.DefaultMaxScale,
  contentAspectRatio: (page: Int) -> Float? = { null }, strings = FluidImageViewerStrings(), background: Color? = null,
  page: @Composable (page: Int) -> Unit)

Modifier.fluidZoomable(state: FluidZoomState, contentAspectRatio: Float? = null, enabled = true, onTap: (() -> Unit)? = null, strings = FluidImageViewerStrings())
rememberFluidZoomState(maxScale = FluidZoom.DefaultMaxScale)
```

**Uso** (da `MoreComponentsDemo`):
```kotlin
FluidImageViewer(
  visible = open, onDismissRequest = { open = false },
  pageCount = pictures.size, onPageChange = { page = it },
  contentAspectRatio = { 1.5f },      // i limiti dello spostamento seguono l'immagine, non il riquadro
  background = Color.Black,           // 3.0.1: il fondo delle foto; titolo e chiusura si fanno chiari da soli
) { index -> Box(Modifier.fillMaxWidth().aspectRatio(1.5f)) { /* l'immagine `index` */ } }
```
Serve un `FluidGlassModalHost` in scope: senza non si apre e **non va in crash**.

**Comportamento.**
- **Di chi è il dito** (la parte che i tre visori sbagliavano): a **scala 1 con un dito solo** il visore non consuma niente (lo scorrimento laterale è del pager, quello in giù è del congedo); con **due dita** consuma sempre; **da ingrandita** con un dito sposta l'immagine e
  consuma solo se l'immagine si è mossa — o se il gesto è verticale, perché un'immagine ingrandita non si chiude trascinando giù per sbaglio; al bordo laterale il resto del gesto passa al pager e si cambia pagina. Per «ingrandita» si intende scala > 1,01: un pizzico lasciato a 1,004 non toglie il dito al pager.
- **Numeri.** Ingrandimento massimo 4× (`FluidZoom.DefaultMaxScale`) l'immagine adattata al contenitore; doppio tocco alterna 1 e 2,5 (`DoubleTapScale`) attorno al punto toccato (e un colpo `Tap`); da ingrandita torna a 1. Il pizzico tiene fermo il punto sotto le dita
  (`zoomAbout`). Lo spostamento massimo per asse è metà di quanto l'immagine in scala eccede il contenitore; un'immagine che in scala sta ancora dentro resta **al centro**, non si porta in un angolo. L'immagine non lascia mai un vuoto fra il suo bordo e quello dello schermo.
  `zoomTo` anima con una molla critica (un'immagine che ingrandisce non rimbalza), a movimento ridotto arriva subito.
- **Congedo**: la pagina intera segue il dito e al rilascio parte dalla sua velocità; si chiude oltre **110 dp** o con una spinta, altrimenti torna con una molla. Una pagina che si lascia indietro torna a scala 1.
- **Accessibilità**: due azioni per TalkBack, «Ingrandisci» e «Riduci» (`FluidImageViewerStrings`), **a gradini** (1 → 2,5 → massimo), e lo zoom come stato («Zoom 250%»). Il tasto indietro (anche predittivo), il fuoco, la pagina dietro nascosta ai servizi di accessibilità e il tasto di chiusura in cima sono del portale.
- **Dove sta**: il visore è nello slot dei tasti fermi (`footer`) del portale a pagina intera, **non nel corpo**: il corpo di una pagina intera scorre e un contenuto che deve riempire lo spazio non ha un'altezza da scorrere.

**Trappole.**
- *Sintomo*: «non si può spostare l'immagine quanto dovrebbe / si vedono bande vuote» → manca `contentAspectRatio`: senza, i limiti sono quelli del riquadro intero e un'immagine più stretta lascia le bande spingibili.
- Il fondo è la **pagina opaca dell'host** (il tema) oppure `background`; lo scurimento dietro **non è proporzionale al trascinamento** (il fondale di un modale pagina intera è un velo fisso; una presentazione con sfondo che si schiarisce col dito vorrebbe una
  aggiunta all'host, `FluidGlassModal.kt`). Il documento `docs/10-coerenza.md` dice ancora «non nero»: dalla 3.0.1 `background = Color.Black` esiste.
- `fluidZoomable` va su un nodo che ha **le misure del contenitore** (il contenuto dentro è adattato); i gesti stanno *fuori* dal layer scalato, nelle coordinate del contenitore.
- `pageCount <= 0` non disegna niente. A ogni apertura si riparte da `initialPage` (il pager vive anche a visore chiuso).

**Non usarlo quando.** Vuoi un'immagine ingrandibile **dentro** una tua pagina (usa `Modifier.fluidZoomable` direttamente) o hai bisogno di un fondo che si schiarisce col dito.

**Versione.** 3.0.0; `background` 3.0.1.

## Riordino con trascinamento

### `rememberFluidReorderState`, `fluidReorderable`, `fluidMoved`, `FluidReorder`

**Cos'è.** Il riordino per trascinamento di una `LazyColumn`, `LazyRow` o `LazyVerticalGrid`: **tieni premuto, sollevi, trascini**. Tre app se lo erano scritto (la sola matematica con il gesto a parte, uno stato con altezze misurate a mano, due frecce perché un trascinamento non c'era):
qui gesto, moto, aptica e matematica sono uno solo e funzionano allo stesso modo su lista, riga e griglia.

**Firma essenziale.**
```kotlin
val reorder = rememberFluidReorderState(listState /* o gridState */, onMove = { from, to -> … }, onDragEnd = { … }, canMoveTo = { index -> true })
LazyItemScope.fluidReorderable(state, key: Any, enabled = true, shape: Shape = ContinuousCornerShape(FluidRadius.Card), strings = FluidReorderStrings()): Modifier   // anche LazyGridItemScope
fun <T> List<T>.fluidMoved(from: Int, to: Int): List<T>
```

**Uso** (da `MoreComponentsDemo`):
```kotlin
var rows by remember { mutableStateOf(initial) }
val listState = rememberLazyListState()
val reorder = rememberFluidReorderState(listState, onMove = { from, to -> rows = rows.fluidMoved(from, to) }, onDragEnd = { save(rows) })
LazyColumn(state = listState, contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
  itemsIndexed(rows, key = { _, name -> name }) { _, name ->
    Row(modifier = fluidReorderable(reorder, key = name).fillMaxWidth() /* … */) { /* maniglia e testo */ }
  }
}
```
`fluidReorderable` è un'estensione dello **scope dell'elemento**, non di `Modifier`: si chiama senza prefisso dentro `items { }` e restituisce un `Modifier` da cui continuare la catena (i vicini scalano con `animateItem`, che esiste solo lì dentro).

**Come si dichiara.** Uno stato per lista (`rememberFluidReorderState`), un `key` per elemento (la **stessa** di `items(key = …)`), e `onMove(from, to)` che applichi alla tua lista con `fluidMoved`. Lo scambio avviene **a ogni posto passato**, non al rilascio: l'ordine nuovo è già nella lista
quando il dito si solleva, e `onDragEnd` è il momento di salvarlo. `onMove` riceve gli **indici della lista pigra**: se prima degli elementi riordinabili ci sono altri `item` (una testata), vanno tolti.

**Il gesto.** Un tocco che resta fermo per il tempo del click lungo di sistema (entro lo `touchSlop`) solleva; da lì il dito muove l'elemento fino al rilascio. **Prima del click lungo non consuma niente**: se il dito si muove, la lista scorre come se il nodo non esistesse. Dopo,
il dito è dell'elemento nel passaggio `Initial`, **prima** che lo veda un discendente (una riga cliccabile leggerebbe il sollevamento come un click, la lista come l'inizio di uno scorrimento). Il gesto sta fuori dal layer che si muove, nelle coordinate del posto in lista.
Un solo elemento alla volta. Si scambia quando il **centro** della sagoma trascinata entra nel riquadro di un vicino (a metà della corsa, dove una mano se l'aspetta; niente oscillazioni, senza bisogno di isteresi: dopo lo scambio il centro è dentro il *suo* riquadro, non in quello lasciato).
In una lista a un asse conta solo quell'asse, in una griglia tutti e due; fra due elementi (la spaziatura) non c'è nessuno e non si scambia.

**Aptica.** Un momento, un colpo: `GestureStart` al sollevamento, un `Tick` per ogni posto attraversato (lo scorrimento automatico ne può attraversare uno a fotogramma: il cancello dei `Tick`, 40 ms, li dirada da solo), `GestureEnd` alla posa. `moveBy` suona un solo `Tick`.

**Il moto.** L'elemento sollevato è una **goccia di vetro** (`glassDrop`) dove c'è un canvas ambientale e il vetro è disponibile (dietro l'elemento si apre la goccia; a riposo non c'è nulla); dove non c'è (sotto API 31, senza canvas, movimento ridotto) cresce e proietta un'ombra di
piattaforma (`FluidReorderDefaults.FallbackShadow`, 10 dp × la pressione). **In tutti e due i casi la crescita sta nel layer dell'elemento e mai in un antenato del vetro**: un layer scalato sopra un vetro rifrange ciò che non c'è più. Cresce del 4% (`LiftScale` 1,04): una riga che
cresce del 4% è una cosa presa in mano, del 10% è un pulsante, e una tessera di griglia più grande invaderebbe le vicine. **Segue il dito sul serio**: la traslazione è dalla posizione che aveva quando è stato sollevato più quanto si è mosso il dito, **meno dove sta adesso**, così uno
scambio che gli cambia posto o uno scorrimento che glielo sposta sotto i piedi non lo spostano sotto il dito; non esce mai dal riquadro della lista. I vicini scalano con `FluidMotion.intOffset()`; quello trascinato no. Si posa **dalla velocità del dito** con una molla critica
(non rimbalza). Sopra tutti (`zIndex`). Movimento ridotto: niente scala né ombra, niente animazione di posa né di spostamento dei vicini.

**Scorrimento automatico.** Ai bordi della lista, in una fascia di 72 dp (`AutoScrollEdge`), la lista scorre da sola: conta il **bordo della sagoma**, non il dito (un elemento alto è al bordo molto prima che il dito ci arrivi); la velocità cresce col **quadrato** della profondità nella fascia,
fino a 900 dp/s (`AutoScrollMaxSpeed`, più di quattro righe al secondo). Il ciclo parte solo quando la sagoma è vicina a un bordo e finisce da solo (un orologio sempre acceso farebbe una schermata mai a riposo).

**Accessibilità.** Per chi non può trascinare (TalkBack, tastiera, interruttore) l'elemento ha due **azioni** personalizzate, «Sposta prima» e «Sposta dopo» (`FluidReorderStrings`), che chiamano `FluidReorderState.moveBy(key, ±1)` (rispettano `canMoveTo` e i limiti: il primo non va prima dell'inizio). Sono azioni del nodo, non tasti da disegnare.

**Trappole.**
- *Sintomo*: i vicini non scalano o l'elemento sbagliato si solleva → mancano le **chiavi** in `items(key = …)` (la lista non sa quale elemento è dove) o la `key` di `fluidReorderable` è diversa da quella dell'`item`.
- *Sintomo*: l'ombra o la crescita vengono tagliate → la lista (o il suo contenitore) clippa: dai un po' di `contentPadding`.
- *Sintomo*: il tenuto premuto non fa niente su una riga con un menu → un elemento con `onLongClick` suo (o `contextActions`) è riordinabile **solo da disabilitato**: usa `enabled` per una modalità «ordina», fuori dalla quale il tenuto premuto resta alla riga.
- *Sintomo*: nessuno scambio con una `LazyColumn` dentro `fluidGlassGroups` → le righe di un pannello non sono item della lista (vedi «Liste lunghe di vetro» in [`contenuto-righe-liste.md`](contenuto-righe-liste.md)).
- `onMove` può arrivare più volte per un solo trascinamento (uno per posto): non salvare lì.
- Se i dati cambiano sotto il dito e l'elemento sollevato sparisce dalla lista, viene lasciato andare (`onDragEnd` chiamato, stato azzerato).

**Non usarlo quando.** L'ordine si cambia con due frecce senza trascinamento (`FluidReorderState.moveBy` dal tuo pulsante) o la lista non è pigra.

**Versione.** 3.0.0.

