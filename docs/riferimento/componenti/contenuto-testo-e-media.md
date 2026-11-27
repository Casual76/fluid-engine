<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/contenuto-testo-e-media.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Contenuto: caricamento, segni piccoli, identità, testo, visore, riordino e file

Terza scheda della famiglia «Contenuto» ([`contenuto.md`](contenuto.md) ha l'indice, il «Scegliere in fretta» e le regole trasversali; [`contenuto-righe-liste.md`](contenuto-righe-liste.md) ha righe, liste, impostazioni, card e aperture).
Qui: gli scheletri, lo spinner e le barre, `FluidStepProgress`, i segni piccoli (`FluidLiveDot`, `FluidSelectionMark`, `FluidPagerDots`), il testo (`FluidMarkdown` e il suo parser, `FluidRevealingText`, `FluidSpokenText`),
il visore di immagini con lo zoom, il riordino con trascinamento, e dalla 3.1.0 l'**identità e la selezione** (`FluidAvatar`, `FluidAvatarStack`, `fluidMonogram`, `FluidIdentityColors`, `FluidBadge`, `FluidSelectionState`) e i **file** (`FluidFileTile`, `FluidFileChip`). Firme complete: [`../api/contenuto.md`](../api/contenuto.md).

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

**Il numero d'ordine** (F8.14). `number = 2`: a segno pieno il disco porta la cifra al posto della spunta, come la scelta
ordinata del selettore di foto di iOS (cifra da 12–13 pt nello stesso posto). Quando il numero cambia a segno acceso (si
toglie la scelta numero 2 e la 3 diventa 2) le cifre scorrono sfocate con `FluidNumericText`, come un badge; il testo c'è
solo mentre il disco è acceso, e **col numero un segno spento non disegna niente** (né anello né velo: nel selettore di iOS
le foto non scelte non hanno l'anello). TalkBack: «Selezionato, numero 2»
(`FluidSelectionMarkDefaults.description(selected, number)`).

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

## Identità e selezione (Fase 1 di Pampa Mail, 3.1.0)

Chi è, cosa è scelto, cosa c'è di nuovo: il cerchio di una persona, il suo colore, il segno «c'è qualcosa», e lo stato di una selezione multipla anche su una lista paginata. Cinque cose fatte per stare insieme in una riga:
`FluidAvatar` (con `fluidMonogram` e `FluidIdentityColors`), `FluidAvatarStack`, `FluidBadge`, `FluidSelectionState`. La riga che le ospita è `FluidListRow` ([`contenuto-righe-liste.md`](contenuto-righe-liste.md)); le barre che le
usano (la barra laterale annidata, l'azione con avatar, la capsula con azioni accese) sono in [`schermata.md`](schermata.md), [`controlli.md`](controlli.md) e [`barre.md`](barre.md). File: `FluidAvatar.kt`,
`FluidIdentityColors.kt`, `FluidBadge.kt`, `FluidSelectionState.kt`.

### Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| il cerchio di una persona, di un gruppo, di un servizio | `FluidAvatar(name, key)` |
| più persone in un cerchio solo (conversazione, gruppo, più account) | `FluidAvatarStack(items)` + il nome per TalkBack con `fluidAvatarStackDescription` |
| solo le iniziali (un titolo, un'etichetta, un'altra forma) | `fluidMonogram(name)` |
| il colore stabile di una persona/materia/etichetta | `fluidIdentityColor(key)` / `FluidIdentityColors.colorFor(key)` |
| il colore che l'utente ha scelto da una palette | `FluidIdentityColors.colorAt(index)` (con giro) |
| «ci sono 3 non lette», «c'è qualcosa» | `FluidBadge(count)` o `FluidBadge()` (pallino) |
| la lista entra in selezione, con «Seleziona tutto» su una lista paginata | `rememberFluidSelectionState` + `selectAll()` / `countIn(total)` |
| il back che esce dalla selezione e non dalla pagina | `FluidSelectionBackHandler(state)`, composto nel punto giusto |
| una cartella dentro una cartella, in una barra laterale | `FluidSidebarRow(level = …)` |
| l'avatar dell'utente in alto a destra | `FluidBarAction(contentDescription, onClick) { FluidAvatar(…) }` |
| un'azione che si accende e si spegne (grassetto, filtro) | `FluidCapsuleAction(selected = …)` |

### `FluidAvatar`, `FluidAvatarStack`, `fluidMonogram`

**Cos'è.** Il cerchio di una persona: un monogramma su un colore che non cambia mai, o un'immagine che porta l'app; in una lista in selezione, al posto del volto, il segno di selezione. Ogni app con persone dentro se ne era
scritto uno (un cerchio colorato con la prima lettera, i colori in un elenco incollato nel composable, l'immagine caricata **dentro** il componente, e allora l'engine faceva rete). Qui è una cosa sola, e le tre cose che
sbagliavano tutti sono provate da test: le iniziali, il colore, il contrasto del monogramma.

**Firma essenziale.** `FluidAvatar(name, modifier, key = name, size = 40.dp, color = Color.Unspecified, image = null, selected = null, corner = null, onClick = null, contentDescription = null)`;
`FluidAvatarStack(items: List<FluidAvatarItem>, modifier, size, max = 4, contentDescription, onClick)` con `FluidAvatarItem(name, key = name, color, image)`; `fluidMonogram(name, maxLetters = 2)`.

**Uso.**
```kotlin
FluidListRow(title = "Anna Rossi", leading = { FluidAvatar(name = "Anna Rossi", key = "anna@x.it") }, leadingTile = false, onClick = …)   // decorativo: la riga dice già il nome
FluidAvatar(name = "Anna Rossi", key = id, selected = state.isSelected(id), onClick = { state.toggle(id) })                                 // in selezione: Role.Checkbox
```

**Comportamento.**
- **L'engine non carica niente.** `image` è un disegno dell'app (una foto già decodificata, un logo locale, un `AsyncImage` suo) e sta **sopra** il monogramma, che resta sempre sotto: finché l'immagine non arriva, se non
  arriva mai o se ha trasparenza, si vede il monogramma e non un cerchio vuoto. Chi non vuole il monogramma sotto un logo trasparente mette un fondo suo. Nel mosaico un'immagine **non** ha il monogramma sotto (una cella è troppo
  piccola per due strati leggibili).
- **Le iniziali (`fluidMonogram`) sono per grafemi, non per caratteri.** «Émile» scritto con l'accento separato è una lettera; «👨‍👩‍👧 Famiglia» comincia con un'emoji che è sette caratteri; «🇮🇹 Italia» con una bandiera che
  ne sono due. `name[0]` dava un quadratino, mezza emoji o una E senza accento: il bug più visibile di un avatar. Regole, in ordine: si tolgono controlli e marcatori bidi (un nome costruito per ribaltare la riga accanto non
  lo fa); un indirizzo vale per la parte locale senza il suffisso dopo il `+`; le parole le separano spazi e punteggiatura ma **non** trattino e apostrofo («Jean-Pierre», «D'Angelo» sono una parola); di ogni parola vale il
  primo grafema che sia lettera, cifra o emoji; si prendono fino a `maxLetters` iniziali, e con più parole **la prima e l'ultima** («Anna Maria Rossi» è «AR»); maiuscola solo del primo grafema («ß» è «S», non «SS»). Una
  parola sola dà **una** lettera. Nome vuoto o solo simboli: il glifo di una persona. Il cinese e il giapponese senza spazi sono una parola sola: danno il primo carattere, non il cognome (non si indovina da una stringa).
- **Il colore è stabile e leggibile.** Viene da `FluidIdentityColors` per `key` (di serie il nome; per una persona conviene una chiave che non cambia quando si corregge il nome: l'indirizzo, l'identificatore). Un colore
  dell'app (`color`: la tinta di un account) che nessun inchiostro regge a 4,5:1 viene portato **del minimo che basta** (`fluidReadableOn`), restando riconoscibile.
- **Misure.** Il cerchio è `size` dp e col testo di sistema al 200% **non cresce**: il monogramma è un disegno, e un avatar più grande del doppio mangia la riga; la persona la nomina il testo accanto, che cresce. Se è
  toccabile occupa almeno 48 dp (`FluidAvatarDefaults.TouchTarget`), centrato: un'area invisibile più grande del disegno sovrapporrebbe i bersagli della riga e dell'avatar.
- **Selezione.** Con `selected != null` il volto lascia il posto a `FluidSelectionMark` (cerchio vuoto = scegliibile, disco con spunta = scelta): è quello che si vede in ogni riga quando la lista entra in selezione, e perché
  il segno sta **nel posto** dell'avatar una riga non cambia larghezza. Il cambio è una dissolvenza incrociata, secca col movimento ridotto.
- **`corner`** è un segno piccolo in basso a destra (a sinistra in RTL), sporge di un ottavo del lato: il colore di un account, un `FluidBadge`.
- **Mosaico.** Due celle sono due metà, tre una metà e due quarti, quattro quattro quarti; oltre `max` l'ultima cella dice quanti mancano («+4»). Ogni cella porta la **prima lettera** sul colore della persona. Con una persona
  sola è un `FluidAvatar`, con nessuna il glifo. `max` è portato a 2–4.

**Accessibilità.** Un avatar è un nodo solo e muto per i suoi pezzi (monogramma, segno, angolo non si leggono mai a pezzi).
- `contentDescription = null`: decorativo. È il caso dentro una riga, che dice già il nome (la riga è un nodo solo): senza questo TalkBack direbbe «AR» prima di «Anna Rossi».
- Toccabile **senza** descrizione: legge `name` (un bottone senza nome è un errore, non uno stile).
- Con `selected`: è una casella (`Role.Checkbox`) con lo stato detto nella lingua dell'app (`FluidLabels.selected`, `FluidLabels.notSelected`).
- Il contenuto di `corner` **non si legge**: se il segno d'angolo significa qualcosa (un'identità, un conteggio) va nel `contentDescription`.
- Mosaico: oltre `max` il resto lo dice **il nome** (`fluidAvatarStackDescription(names, max)` lo compone con le parole dell'app, `FluidLabels.moreAvatars`: «e altri 3»). Passa i nomi di **tutte** le persone, nell'ordine
  del mosaico.

**Trappole.** `FluidAvatar` come bersaglio dentro una `FluidListRow` con `onClick` sono due bersagli: l'azione dell'avatar (entrare in selezione) va **anche** in `customActions` della riga, perché per TalkBack il controllo
dentro la riga fusa non esiste. L'avatar in selezione non sostituisce il segno di selezione della riga: è lo stesso, un posto solo.

### `FluidIdentityColors`: la palette è un contratto

**Cos'è.** Dodici tinte distinguibili fra loro, per tutto ciò che deve **riconoscersi** senza dire un numero: il fondo di un monogramma, il colore di un account o di una materia, un'etichetta, la serie di un grafico (i grafici di [`grafici.md`](grafici.md) la usano per le serie). Ogni tinta è un `FluidIdentityColor(light, dark)`: una **coppia**, come ogni ruolo del tema (un blu che si legge su carta è quasi invisibile sul nero). Il lato chiaro è abbastanza scuro da reggere un monogramma bianco
(4,5:1) e da staccarsi dalla pagina (3:1); il lato scuro abbastanza chiaro da reggere un monogramma scuro e da staccarsi dal nero. Lo verificano i test su tutta la palette.

**`indexFor(key)` è FNV-1a a 32 bit sui byte UTF-8**, senza segno, modulo la dimensione della palette. È definito qui, nel dettaglio, perché è il contratto di stabilità: `String.hashCode()` è specificato dalla JVM ma non è
una promessa dell'engine. La chiave vuota non è un caso speciale. `colorFor(key)` è `palette[indexFor(key)]`; `colorAt(index)` ha **giro** (`floorMod`: un indice scelto dall'utente resta valido se un domani la palette ha meno
tinte, e uno negativo non esplode). `fluidIdentityColor(key)` e `fluidIdentityColor(index)` scelgono il lato dal tema in scope (dalla luminosità della superficie, non da `isSystemInDarkTheme`, che non sa se l'app ha il suo tema).

**L'ordine è un contratto.** Lo stesso testo dà lo stesso indice **per sempre**, su ogni telefono e in ogni versione dell'engine: la persona ha imparato che «Anna» è verde. Cambiare la palette (l'ordine, il numero di tinte,
anche una sola tonalità vistosa) **o l'hash** sposta i colori a tutti gli utenti al primo avvio dopo l'aggiornamento: è un cambio visivo breaking, si fa solo in una versione maggiore e va nel changelog. Un colore nuovo si aggiunge
**in coda**, e anche così cambia `indice % dimensione` per tutte le chiavi: nemmeno l'aggiunta è innocua.

**Normalizzazione: di chi chiama.** `"Anna@x.it"` e `"anna@x.it"` sono due chiavi: chi vuole la stessa persona passa la chiave già normalizzata (minuscole, spazi tolti). L'engine non sa se una chiave è un indirizzo, un nome o un
id, e normalizzare quello sbagliato spaccherebbe un'app che distingue le maiuscole.

**Usi e non usi.** Fondo di un monogramma (il lato giusto regge un inchiostro scelto per contrasto); segno grafico (un punto, una barra) che regge 3:1 contro le superfici. **Non è per testo colorato su una superficie**: per quello
serve il colore del testo del tema. Per una palette del tema vedi anche [`tema.md`](tema.md).

### `FluidBadge`

**Cos'è.** Il segno piccolo di «c'è qualcosa»: un pallino, o una capsula con un numero («3», «99+»). Le non lette di una cartella, gli aggiornamenti, le comunicazioni, il «nuovo» di una zona della barra: la stessa cosa
disegnata in cinque modi, con il numero a 99 che in tre usciva dalla capsula. Cifre di larghezza fissa (niente salti mentre il numero cambia) e contrasto calcolato.

**Firma essenziale.** `FluidBadge(count: Int? = null, modifier, max = 99, color = Color.Unspecified /* l'accento */, contentDescription: String? = null)`; `fluidBadgeText(count, max)`.

**Tre stati, e zero non è il pallino.** `count = null`: il **pallino** (c'è qualcosa, senza dire quanto). `count = 0` (o negativo, che è un dato sbagliato): **niente**, non si disegna e non occupa spazio: un pallino acceso su
«0 non lette» è una bugia. `count > 0`: la capsula, «99+» oltre `max`.

**Il numero si dice una volta.** Di serie è **muto**: lo porta una riga che dice già «Posta in arrivo, 3 non lette» in un nodo solo, e dirlo anche dal badge sarebbe «3, Posta in arrivo, 3 non lette». Un badge che sta da solo
(un'icona con un numero) passa la frase intera **con la quantità vera**: «150 non lette» anche se il disegno dice «99+». Un pallino senza descrizione non è leggibile da nessuno, ed è voluto (i segni che non devono disturbare).

**Misure e contrasto.** Il pallino è 8 dp e non cresce; la capsula è alta almeno 16 dp e **cresce col testo di sistema** (a 200% «99+» resta dentro la sua capsula): il numero è informazione, il pallino no. Il colore che passi è
un'**identità**, non una garanzia di leggibilità: il riempimento si porta, del minimo che serve, a 3:1 da tutte le superfici (anche dalla pellicola del vetro, quasi bianca in tema scuro) e la cifra a 4,5:1. Il colore che si vede
può quindi essere un poco più scuro o chiaro di quello passato. Compare e scompare con la riga che lo porta; **il numero
scorre** quando cambia (F8.1b), con le cifre di `FluidNumericText`: solo quelle cambiate, sfocate in viaggio, e la capsula
segue la larghezza nuova.

### `FluidSelectionState`: selezione multipla, anche su liste paginate

**Cos'è.** Lo stato della selezione di una lista: cosa è scelto, come si entra e si esce. Ogni app aveva uno stato suo (`Set<Id>` in un ViewModel) e tre pezzi sbagliati a modo loro: «Seleziona tutto» che sceglieva solo
le righe caricate (l'azione poi riguardava il quinto di ciò che la persona credeva), il back che usciva dalla pagina invece che dalla selezione, e una rotazione che azzerava tutto.

**Firma essenziale.** `rememberFluidSelectionState<K>(keySaver = null)`, `FluidSelectionState<K>` con `toggle`, `select`, `deselect`, `setSelected`, `selectAll(snapshot)`, `clear`, `isSelected(key, belongsToSnapshot)`,
`countIn(total)`, `exitIfEmpty(total)`, `retainOnly(valid)`, `forget(keys)`, `clearIfSnapshotDiffers(current)`, `mode: FluidSelectionMode`; `FluidSelectionBackHandler(state, enabled, onExit)`.

**Due forme.** `FluidSelectionMode.Explicit(keys)`: sono scelte **queste** chiavi (una persona che tocca tre righe). `FluidSelectionMode.AllExcept(excluded, snapshot)`: è scelto **tutto, tranne** queste (il «Seleziona tutto» di
una lista paginata: le chiavi non sono in memoria, 50.000 righe in un database). Rappresentarlo come un insieme di chiavi vorrebbe dire o scegliere in silenzio solo le righe caricate, o caricarle tutte per scriverne la lista.

**«Seleziona tutto» non conta e non cerca niente.** `selectAll()` passa a `AllExcept` con esclusioni vuote, **senza materializzare nessuna chiave**; da lì ogni riga, caricata o no, è scelta finché qualcuno non la `toggle` fuori. Il
**numero** lo dà chi conosce il totale (`countIn(total)`, la query del database), con lo **stesso testimone** della selezione: due query diverse darebbero il numero giusto per la seconda e sbagliato per quello che la persona ha
scelto. Nessun elenco di 50.000 identificatori passa da questo oggetto.

**Il risultato «al momento del tocco».** «Seleziona tutto» non è «e tutto quello che arriverà dopo». `selectAll(snapshot)` conserva un testimone che l'app lega a quel risultato («query + istante del tocco»; una stringa,
perché deve sopravvivere alla morte del processo); `isSelected(key, belongsToSnapshot = false)` dice che una riga arrivata dopo **non** è scelta; `clearIfSnapshotDiffers(current)` esce se la persona ha cambiato filtro, account,
ricerca: un'azione di massa applicata a righe che non ha guardato è l'incidente da evitare. L'engine non interpreta il testimone: lo conserva e lo restituisce.

**Uscire.** La selezione è attiva (`isActive`) finché c'è qualcosa di scelto: togliere l'ultima riga esce da sola. Con `AllExcept` il motore non sa quando le esclusioni hanno eguagliato il totale: lo dice l'app con
`exitIfEmpty(total)`. `clear()` e il back escono sempre. `retainOnly(valid)` (lista **tutta in memoria**) e `forget(keys)` (lista **paginata**: le righe che l'app sa non esistere più, archiviate, eliminate, spostate) tolgono
le chiavi sparite; `retainOnly` con le sole righe caricate toglierebbe le scelte sulle righe non caricate.

**Il saver ha un tetto.** `rememberFluidSelectionState` sopravvive a rotazione e morte del processo, ma un `Bundle` ha circa un mega per tutta l'app: diecimila chiavi lo farebbero esplodere a tempo di salvataggio
(`TransactionTooLargeException`), e il crash ci sarebbe quando l'app va in secondo piano, non quando la persona sceglie. Oltre `FluidSelectionState.DefaultMaxSavedKeys` (1.000) lo stato si salva **vuoto**: la selezione è
transitoria, perderla è un fastidio e il crash un guasto. Una chiave che non è già salvabile (un oggetto) vuole il suo `keySaver`: nel `Bundle` va l'identificatore, mai l'oggetto di dominio.

**L'ordine del back.** `FluidSelectionBackHandler` registra un `BackHandler` che svuota la selezione solo quando è attiva. **L'ordine di composizione è l'ordine del back**: vince quello registrato per ultimo, quindi va
composto **dopo** il contenuto che ha i suoi (la pagina, l'host di navigazione) e **prima** di chi deve avere la meglio (i menu, i fogli, `FluidDrawer`, che registrano il loro quando si aprono, dopo). Risultato: menu, poi
pannello, poi selezione, poi pagina. Posato prima della pagina, il back chiude la pagina con la selezione ancora accesa.

**L'ordine e il tetto** (F8.14). La forma esplicita tiene le chiavi **nell'ordine in cui sono state scelte** (chi rientra va
in fondo): è così che il selettore di foto numera le scelte. Con `FluidSelectionState(limit = 3)` (o
`rememberFluidSelectionState(limit = 3)`, il `selectionLimit` di iOS) una scelta oltre il tetto non entra, `atLimit` dice
quando le altre righe vanno mostrate spente, e «Seleziona tutto» non fa niente; il tetto sopravvive al salvataggio
(`saver(limit = …)`), e uno stato salvato con più chiavi del tetto tiene le prime.

**Trappole.** Le chiavi sono insiemi: `K` con `equals`/`hashCode` veri (un id, non un oggetto mutabile) e **stabili fra aggiornamenti dei dati** (un id di database, non l'indice: una riga arrivata in cima sposterebbe la scelta
su quella sbagliata). `isSelected` legge lo stato intero: a ogni scelta ricompongono le righe che lo leggono (le visibili); una riga costosa legge il suo valore con `remember(key) { derivedStateOf { state.isSelected(key) } }`.
L'engine non sa nulla di account, query o permessi e non decide se un'azione è ammessa su un `AllExcept`: il cambio di account o di filtro è un evento dell'app, e lo comunica (`clear`, `clearIfSnapshotDiffers`). Si modifica
dal thread principale.

## Testo

### `FluidDetectedText` e `FluidTextDetection`

Un avviso, una nota o il corpo di una mail in testo semplice. Il testo è visibile subito e i dati vengono
sottolineati dopo il riconoscimento. Il segno ha lo stesso colore del testo, senza cambiare i caratteri
o gli a capo. Il tocco e la pressione lunga aprono il menu contestuale di vetro, ancorato al dato toccato.
Per un link web il tocco apre la destinazione e la pressione lunga apre il menu. Con TalkBack o tastiera
si può attivare il dato per aprire il menu e scegliere fra tutte le voci.

```kotlin
FluidDetectedText(
  text = notice,
  detection = FluidTextDetection(kinds = setOf(FluidDetectedTextKind.Date, FluidDetectedTextKind.Time)) { data ->
    listOf(FluidContextAction("Aggiungi ai compiti") { addTask(data.date, data.time) })
  },
)
FluidMarkdown(markdown = note, selectable = true, detection = FluidTextDetection())
```

**Azioni.** Data e ora permettono di aggiungere un evento o mostrare il giorno nell’agenda di Android. Una data
senza ora è un evento giornaliero. Per un numero il menu offre il tastierino, una bozza di messaggio e Contatti.
Per un indirizzo offre indicazioni in Google Maps e apertura nell’app di mappe. Per una mail offre una bozza
e Contatti. Copia usa la grafia del testo mostrato; per i link il menu offre anche Apri il link. Le voci dell'app ricevono un
`FluidDetectedValue`, con gli indici nel testo e i valori normalizzati, e sono lette quando si apre il menu.
La data mostra un'anteprima locale del giorno. L'app ospita il menu nello stesso `FluidGlassModalHost`
delle altre azioni. In una schermata senza host i link web restano apribili e gli altri dati sono solo sottolineati.

**Lingua e selezione.** `FluidTextDetection` segue la lingua dell'app di serie, oppure riceve il locale del documento.
Le date e gli orari semplici sono risolti dalle regole italiane e inglesi, con la data di oggi al momento
del riconoscimento. Il servizio Android 9+ integra indirizzi, link, mail e telefoni, soggetti alle regole di
convalida. Un servizio disattivato o guasto (chiederlo, chiederne il limite, chiedergli i link: ognuna di
queste chiamate può lanciare) lascia i soli dati delle regole locali, senza eccezioni e senza tenerli in cache,
così al giro dopo si riprova. Il riconoscimento gira in background e la cache in memoria ha dimensione limitata. Il testo non
sottolineato resta selezionabile con `selectable = true`. Nei paragrafi Markdown il riconoscimento parte a
testo arrivato e mantiene i link dell'autore e il codice in linea.

**Indirizzi e link, dalla review R2.** «via», «corso» e «largo» sono anche parole di tutti i giorni: con loro
la regola italiana vuole un nome con la maiuscola («via Roma 12», «Corso Vittorio Emanuele II 5») o un segno di
civico (la virgola, «n.», «12/A»), così «mandato via posta il 12» o «corso di inglese 10» non diventano un
indirizzo; «via roma 12» scritto tutto minuscolo non si sottolinea più, «via roma, 12» sì. Le altre parole
(viale, vicolo, piazza, piazzale) bastano da sole. Un link con lettere accentate (`https://città.it/menu`), col
trattino basso nell'host o con un carattere che `java.net.URI` non accetta (`|`) si sottolinea e si apre; l'host
resta obbligatorio (con un punto), nome e password nell'indirizzo restano rifiutati.

### `FluidMarkdown` e `FluidMarkdownParser`

**Cos'è.** Il Markdown disegnato, senza libreria esterna e senza dipendenza dall'assistente: una nota, le novità di una versione, il testo di una guida, la risposta di un modello. Il **parser** (`FluidMarkdownParser`) è puro Kotlin, senza una riga di Compose:
è il pezzo che le app avevano scritto ognuna per conto suo (tre `MarkdownLite` da un centinaio di righe, una libreria esterna per le note, un secondo renderer a blocchi per lo streaming di una chat) e si prova sul computer. Dalla 3.0.0 vive in `engine-ui` (era in `engine-ai-ui`).

**Firma essenziale.**
```kotlin
FluidMarkdown(markdown: String, streaming: Boolean = false, animateFirst: Boolean = streaming,
  style: FluidMarkdownStyle = FluidMarkdownDefaults.style(), strings: FluidMarkdownStrings = FluidMarkdownStrings(),
  options: FluidMarkdownOptions = FluidMarkdownOptions(), onLinkClick: ((String) -> Unit)? = null,
  selectable: Boolean = false, detection: FluidTextDetection? = null)

FluidMarkdownDefaults.style(textColor, mutedColor, blockSpacing = 8.dp, body = bodyLarge, headings = emptyList())
FluidMarkdownDefaults.documentHeadings()        // 4 misure per `#`…`####`, da passare come `headings`
FluidMarkdownOptions(hideChipMarkers = false, softBreak = " ")
FluidMarkdownParser.parse(markdown, options): List<FluidMdBlock>     // l'albero
FluidMarkdownParser.plainText(markdown, includeCode = false, bullets = false, options)   // senza segni
```
Tutti i parametri: [`../api/contenuto.md`](../api/contenuto.md).

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

**Firma essenziale.** `FluidRevealingText(text: AnnotatedString, style: TextStyle, color: Color, maxLines, overflow, revealMillis = FluidRevealMillis /* 260 dalla 4.0 */, rewriteMillis = FluidRewriteMillis /* 120 */, softWrap = true, animateFirst = false, reveal = true, arrival = FluidTextArrival.Streaming)`.

**Dalla 4.0 (F8.1) le parole arrivano sfocate e una dopo l'altra** (la nota di Alessio: «non tutto insieme ma gradualmente, col blur»): è il primitivo del testo che arriva, descritto sotto in «Testo e numeri in movimento». `FluidMarkdown(streaming = true)` e quindi la bolla dell'assistente di `engine-ai-ui` lo usano già, senza una riga cambiata. `arrival = FluidTextArrival.Fade` torna alla sola dissolvenza di prima.

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

## Testo e numeri in movimento (F8.1, 4.0)

Gli elementi scelti da Alessio il 23 novembre 2026 per il testo che si muove. Il tono è uno: **ciò che arriva o cambia è
sfocato mentre si muove e si mette a fuoco** (R7, la nota «col blur durante il movimento»). I numeri di movimento stanno in
`FluidTextMotion`, ognuno con la sua fonte o «a occhio» con la data. Da fermo nessuno di questi elementi ridisegna; col
movimento ridotto il numero cambia a dissolvenza e il testo compare intero, senza sfocatura; anche se «Riduci movimento» si
accende mentre il testo sta arrivando, le parole in attesa entrano subito (R2). Le scene sono nella pagina
«Elementi nuovi» della galleria (`--es gallery.page NewElements --es novita.scene <id>`).

### Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un numero che cambia (saldo, contatore, percentuale, media) | `FluidNumericText(text, value)` o `FluidNumericText(value)` |
| un testo che arriva da un modello, parola per parola | `FluidMarkdown(streaming = true)` o `FluidRevealingText` (sfocati di serie) |
| un testo che compare intero o cambia, riga per riga | `FluidArrivingText(text)` |
| un paragrafo lungo chiuso a N righe con «altro» | `FluidExpandableText(text, collapsedMaxLines = 3)` |
| il tempo che manca, da cronometro | `FluidTimerText(endsAt)`; in una capsula con l'anello `FluidCountdownBadge(endsAt, total = …)` |
| «2 min fa» che si aggiorna da solo | `FluidRelativeText(instant)`, con `FluidLiveClockProvider` una volta per pagina |
| scrivere una data, un peso, un elenco, una durata | `FluidFormat` di `engine-foundation` ([`../moduli/foundation.md`](../moduli/foundation.md)) |

### `FluidNumericText`: i numeri che scorrono

**Cos'è.** `Text(…).contentTransition(.numericText(value:))` di SwiftUI: quando il valore cambia **scorrono solo le cifre
cambiate**, in su se cresce e in giù se cala, sfocate mentre viaggiano. Il riferimento sono i timer di Orologio e i saldi di
Wallet.

**Firma essenziale.** `FluidNumericText(text: String, value: Double, modifier, style = FluidTextStyles.numeric, color, contentDescription = null)`;
`FluidNumericText(value: Number, modifier, style, color, format = { FluidFormat.number(it.toDouble()) })`.

**Comportamento.** Il testo si divide in tre parti: ciò che sta prima della prima cifra (il segno, «€ »), il corpo dalla
prima all'ultima cifra, l'unità. Il corpo si allinea **da destra**, per valore di posizione (99 → 100 fa entrare l'1 e
scorrere le altre due); segno, virgola e unità **non scorrono mai**: restano, scivolano di lato se il numero si allunga, o
cambiano a dissolvenza sul posto (MB → GB). Anche un separatore che nasce o se ne va (999 → 1.000) cambia sul posto: prima
entrava viaggiando con le cifre (R2). Il verso viene dal **valore**, non dal testo: un conto alla rovescia scende anche
da «1:00» a «0:59». La molla è quella di serie di SwiftUI da iOS 17 (0,5 s senza rimbalzo); la cifra viaggia di 0,62 righe
e la sua σ arriva a 0,15 dell'altezza delle maiuscole (R7). Un testo solo, impaginato una volta per valore; il movimento si
legge nel disegno e nella misura (la larghezza segue fra il vecchio e il nuovo), mai nella composizione. Le cifre in viaggio
sono il testo vero in due livelli sfocati (chi esce, chi entra), solo mentre viaggiano; sotto il mezzo pixel di σ si
disegnano nitide, così la cifra che si ferma non salta. TalkBack sente solo il valore finale.

**Cambi fitti** (F8.1b). Un valore che cambia più spesso di 300 ms (`FluidTextMotion.DigitMinIntervalMillis`: i dB di un
fonometro, otto volte al secondo) **non scorre**: cambia di colpo, come un `Text`, e torna a scorrere quando rallenta. Niente
tremolio, e niente da limitare nell'app.

**Solo ciò che muove l'app** (regola di Alessio del 24 novembre 2026: ciò che muove il dito resta nitido, si sfoca solo ciò
che muove l'app da sola). **Mentre il dito trascina un controllo dell'engine che cambia un valore** (`FluidSlider`,
`FluidRangeSlider`, `FluidRulerDial`, `FluidWheelPicker`) ogni `FluidNumericText` cambia di colpo, nitido: un quadrante o
un'etichetta guidati da un cursore non si sfocano sotto il dito, anche trascinando piano. **Niente blur neanche
nell'inerzia di un lancio partito dal dito**: ruota e righello restano del dito finché non si posano, posa compresa (la
ruota dice la voce scelta solo da ferma, a dito già alzato: anche quel cambio è nitido); il tocco che porta una voce al
centro e il doppio tocco che riporta la ghiera al neutro pure. I controlli lo dicono con un osservatore che guarda il
passaggio `Initial` e non consuma niente (`fluidFingerDrivesValue`) e con la corsa partita dal dito (`fluidFingerMotion`),
interni; il conto vale ancora 50 ms dopo l'ultimo rilascio, perché l'ultimo valore si ricompone un fotogramma più tardi e
lo slider dice il valore di un tocco sulla pista proprio al sollevamento. Ciò che l'app cambia da sola (un `selected` nuovo
che la ruota segue con la molla) torna a scorrere. Un numero che segue il dito di un gesto tuo (il valore sotto il dito di
un grafico) è un `Text`, non un `FluidNumericText`. Lo usano `FluidBadge` e il numero di `FluidGauge`.

**Trappole.** Le cifre decimali fisse le dà il formato (`FluidFormat.number(fractionDigits = 2)`): con un numero di
decimali che cambia il corpo si allinea comunque da destra.

### Il testo che arriva: `FluidTextArrival`, `FluidArrivingText`

**Cos'è.** Il primitivo riusabile del testo che arriva, per il testo che si espande **e** per il testo generato da PampAI:
un pezzo alla volta (`FluidTextArrivalUnit.Word` o `FluidTextArrivalUnit.Line`), sfocato che si mette a fuoco.

**Firma essenziale.** `FluidTextArrival(unit, staggerMillis, maxSpreadMillis, blur = true, peakBlur = 0.20f)` con i preset
`FluidTextArrival.Streaming` (parole, passo 24 ms, al più 200 ms indietro), `FluidTextArrival.Lines` (righe, passo 55 ms, al
più 440 ms) e `FluidTextArrival.Fade` (la dissolvenza di prima); `FluidArrivingText(text, modifier, style, color, animateFirst = true, arrival = FluidTextArrival.Lines)`.

**Comportamento.** Un `Text` solo e un layout solo, come `FluidRevealingText`: i pezzi che arrivano sono nascosti dalla
maschera sui loro riquadri e ridisegnati come un fantasma sfocato che si mette a fuoco sulla parola vera: il testo stesso,
ritagliato sulle parole che arrivano, in al più sei livelli sfocati alti quanto il paragrafo (le parole raggruppate per
quanto sono ancora nascoste). **Come Apple, all'inizio si vede solo il fantasma sfocato** (F8.1b, scelta di Alessio del 24
novembre 2026): σ fino a 0,20 dell'altezza delle maiuscole (sulla tavola WWDC24 Apple ha 0,14 al primo fotogramma visibile
e 0,06 a metà), il testo nitido entra solo nella seconda metà dell'arrivo. Il tempo resta svelto: 260 ms a parola, 380 a
riga. Nessun livello per parola e nessun livello a tutto schermo; i livelli e il layer fuori schermo
del testo ci sono solo mentre qualcosa arriva. I pezzi di uno stesso arrivo nascono **uno dopo l'altro**, di un passo; il passo non si accumula (un pezzo aspetta al
più il ritardo massimo) e **l'ordine resta**, anche fra due arrivi vicini. `FluidArrivingText` fa arrivare il testo nuovo dal
primo carattere diverso; ciò che resta uguale non si muove.

**Trappole.** La sfocatura vuole Android 12 (`RenderEffect`): sotto resta la dissolvenza. Non si disegna la sagoma dei
glifi: `TextLayoutResult.getPathForRange` dà il riquadro della selezione (il primo giro sul Tab S9 ha mostrato rettangoli
al posto delle lettere sfocate), quindi il fantasma è sempre il testo vero, coi colori dei suoi link.

### `FluidExpandableText`: il testo che si espande

**Cos'è.** Un paragrafo chiuso a N righe con «altro» in fondo all'ultima riga, come le descrizioni e le novità dell'App
Store (in SwiftUI non c'è: l'App Store lo fa con `lineLimit`).

**Firma essenziale.** `FluidExpandableText(text: String | AnnotatedString, modifier, collapsedMaxLines = 3, style = bodyLarge, color, moreLabel = «altro», moreColor = primary, collapsible = false, arrival = FluidTextArrival.Lines, onExpandedChange)`.

**Comportamento.** Il taglio si misura sul layout vero (una volta per larghezza, testo e stile): «… altro» sta nell'ultima
riga senza coprire il testo, e se la misura del pezzo e dell'insieme non coincidono al pixel si toglie una lettera piuttosto
che mandare «altro» a capo. Il taglio cade sempre fra due grafemi interi (R2): un emoji o una lettera con l'accento unito non
si spezzano più prima di «… altro». Un tocco apre il paragrafo sul posto: l'altezza cresce con una molla senza rimbalzo (0,5 s) e il
resto **arriva riga per riga, sfocato** (lo stesso arrivo del testo di PampAI). Un tocco su un link apre il link, non il
paragrafo. Lo stato si salva; TalkBack ha «Espandi» sul paragrafo e lo stato «Testo ridotto» / «Testo intero». Aperto resta
aperto, come nell'App Store; `collapsible = true` lo richiude.

**Trappole.** Il paragrafo misura con `BoxWithConstraints`: in una lista lunga va bene, ma non metterne cento in una
`Column` che scorre.

### Tempi vivi: `FluidRelativeText`, `FluidTimerText`, `FluidCountdownBadge`, `FluidLiveClock`

**Cos'è.** `Text(date, style: .relative)` e `Text(timerInterval:)` di SwiftUI, e la capsula col tempo che scende dei timer di
WidgetKit e delle Live Activity.

**Firma essenziale.**
```kotlin
FluidRelativeText(instant, modifier, style, color, width = FluidFormatWidth.Short, locale)        // «12 min fa»
FluidTimerText(endsAt: Instant, modifier, style = FluidTextStyles.numeric, color, onExpire)      // «4:59»
FluidCountdownBadge(endsAt, modifier, total: Duration? = null, tone = primary, onExpire)          // anello + cifre
FluidLiveClockProvider(clock = rememberFluidLiveClock()) { … }                                    // l'orologio della pagina
LocalFluidTimeSource provides FluidTimeSource { … }                                               // l'ora, per le prove
```

**Comportamento.** Nessuno ridisegna a ogni fotogramma: ognuno sa **quando il suo testo cambia** e aspetta fino ad allora
(«12 min fa» una volta al minuto, «3 h fa» una volta all'ora, il cronometro una volta al secondo). Con
`FluidLiveClockProvider` sulla pagina c'è **un'attesa sola** per tutti i tempi della pagina, arrotondata al secondo pieno: una
lista di cento righe batte al più una volta al secondo, e solo quando un testo cambia. Senza, ogni testo aspetta da solo.
Il cronometro scorre con le cifre di `FluidNumericText`, verso il basso; a zero chiama `onExpire` una volta. In sottofondo non
batte (`repeatOnLifecycle(STARTED)`), e dalla R2 nemmeno i tempi relativi e l'orologio della pagina: al ritorno i testi il cui
cambio è passato si aggiornano subito. L'anello della capsula avanza di un passo al secondo con una molla breve e poi sta
fermo. TalkBack legge la forma intera («2 minuti fa», «Mancano 4 minuti e 59 secondi»).

**Trappole.** La capsula è quieta, nel tono: sta nella riga o nella barra. Sopra una foto la si mette in un vetro dell'app
(`Modifier.fluidGlass` dentro un `FluidGlassContainer`).

## Immagine che arriva: `FluidAsyncImage`, `FluidImagePhase`, `FluidThumbHash` (F8.1, 4.0)

**Cos'è.** Le fasi di `AsyncImage` di SwiftUI: **segnaposto, poi sfocata, poi nitida**, o una tessera d'errore con «Riprova».
Il segnaposto è l'impronta ThumbHash (poche decine di byte salvate accanto all'immagine) coi colori veri.

**Firma essenziale.**
```kotlin
FluidAsyncImage(phase: FluidImagePhase, contentDescription, modifier, thumbHash: ByteArray? = null,
  contentScale = ContentScale.Crop, shape = RectangleShape, onRetry = null)
FluidImagePhase.Loading / FluidImagePhase.Success(painter | imageBitmap) / FluidImagePhase.Failure(error)
FluidThumbHash.encode(width, height, rgba) / decode(hash) / imageBitmap(hash) / averageColor(hash) / aspectRatio(hash)
// un'impronta corrotta (meno di 5 byte, o 5 col bit dell'alfa) lancia IllegalArgumentException, mai un indice fuori (R2)
```

**Comportamento.** L'app fa il caricamento vero (Coil o il suo client) e dice la fase; con Coil la traduzione è di poche righe
(c'è l'esempio nel KDoc di `FluidImagePhase`), quindi **nessun modulo adattatore**. La foto entra sopra l'impronta e si
mette a fuoco in 300 ms (il T del contenuto di R7, σ 6 dp), sul livello della sola foto e solo mentre arriva; una foto già
pronta alla prima composizione non si anima. Le impronte decodificate stanno in una cache (128): in una griglia lunga la
stessa impronta non si decodifica a ogni scorrimento. ThumbHash è portato come sorgente, MIT (`LICENSES/ThumbHash.md`, e
la voce in `FluidEngineLicenses`).

**Trappole.** L'impronta si calcola **al caricamento** (lavoro dell'app o del server), su un'immagine di al più 100 pixel per
lato. La sfocatura dell'arrivo vuole Android 12; prima è la sola dissolvenza.

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
  aggiunta all'host, `FluidGlassModalHost.kt`). Il documento `docs/10-coerenza.md` dice ancora «non nero»: dalla 3.0.1 `background = Color.Black` esiste.
- `fluidZoomable` va su un nodo che ha **le misure del contenitore** (il contenuto dentro è adattato); i gesti stanno *fuori* dal layer scalato, nelle coordinate del contenitore.
- `pageCount <= 0` non disegna niente. A ogni apertura si riparte da `initialPage` (il pager vive anche a visore chiuso).

**Non usarlo quando.** Vuoi un'immagine ingrandibile **dentro** una tua pagina (usa `Modifier.fluidZoomable` direttamente) o hai bisogno di un fondo che si schiarisce col dito. Per un file qualunque (video, audio, PDF, testo, sconosciuto, e con i moduli di famiglia anche Office, archivi, dati, immagini speciali, font e 3D) su una rotta vera, con il ritorno nella miniatura, le barre al tocco, il pannello info e gli strumenti, c'è il telaio dei visori: [`visori.md`](visori.md) (i moduli: [`../moduli/viewer-famiglie.md`](../moduli/viewer-famiglie.md)). Dalla 4.0 `FluidImageViewer` usa le stesse pagine del suo preset immagine.

**Versione.** 3.0.0; `background` 3.0.1.

### `FluidBeforeAfter`: il confronto a tendina (F8.15, 4.0)

**Cos'è.** Due versioni della stessa immagine una sopra l'altra e una maniglia che si sposta col dito: a sinistra «prima»,
a destra «dopo» (idea `confronto-prima-dopo`). Le capsule «Prima» e «Dopo» in alto mostrano ciascuna versione intera, e un
altro tocco torna a metà. In Foto di Apple la tendina non c'è (Foto mostra l'originale tenendo premuta la foto, e lo fa
l'editor di `engine-editor`, [`../moduli/editor.md`](../moduli/editor.md)); qui serve dove le due versioni vanno guardate
insieme: un ritocco in MyCloud Gallery, gli artefatti della compressione in Universal Converter, una scansione corretta.

**Firma essenziale.**
```kotlin
FluidBeforeAfter(
  before: @Composable () -> Unit, after: @Composable () -> Unit, modifier = Modifier,
  state = rememberFluidBeforeAfterState(initial = 0.5f),   // fraction, snapTo, showBefore, showAfter, split
  labels = FluidBeforeAfterLabels(before = "Prima", after = "Dopo"), showLabels = true,
)
```

**Uso**, dentro il visore dei file, con lo zoom condiviso:
```kotlin
FluidFileViewer(count = 1, content = { FluidViewerContent.Image(ratio) {
  FluidBeforeAfter(before = { Image(originale, null) }, after = { Image(compressa, null) },
    labels = FluidBeforeAfterLabels(before = "Originale", after = "JPEG 12"))
} })
```

**Comportamento.**
- **Due immagini già allineate** (stesse misure e inquadratura): l'allineamento è dell'app.
- **Lo zoom è condiviso**: nel visore il pizzico muove le due versioni insieme; la maniglia resta della sua misura e nel
  centro di ciò che si vede (`currentFluidViewerZoom`). La maniglia tiene il suo dito (44 dp attorno alla linea): da lì lo
  sfoglio del visore non parte.
- **Dito nitido**: la linea segue il dito 1:1; un `Threshold` passando da metà. Le capsule portano la tendina con la molla
  standard (di colpo col movimento ridotto).
- **TalkBack**: la maniglia è un cursore («Prima al 50%») con «aumenta», «diminuisci» e «imposta». Mentre il dito la
  trascina la sua semantica resta ferma e si aggiorna a fine gesto (`fluidSettledSemantics` di F7.24, R1): con un servizio
  di accessibilità acceso il dito non cambia la semantica a ogni passo. Un valore dato da TalkBack o dall'app arriva
  subito. Il pomello di vetro si sposta **nel disegno** (F7.31, `glassDrawOffset`), non nel layout: prima, con
  l'accessibilità accesa, la pagina si ricontrollava al più ogni 100 ms mentre il dito lo portava. In un contenitore di vetro
  (`FluidGlassContainer`) resta nel layout: lì il pomello è un pezzo che il contenitore disegna dove dice il layout.
- Ferma, non ridisegna: il taglio si legge nel disegno. Mossa dal dito non si ricompone (R1): linea, taglio e maniglia si
  leggono nel disegno e nella posa, e le capsule cambiano solo quando la tendina passa la loro soglia.

**Non usarlo quando.** Le due immagini non sono la stessa inquadratura (allinea prima), o serve solo «com'era» mentre si
modifica (tieni premuto nell'editor).

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


## File: `FluidFileTile` e `FluidFileChip` (Fase 1 di Pampa Mail, 3.1.0)

### `FluidFileTile`, `FluidFileChip`, `FluidFileKind`, `FluidFileStatus`

**Cos'è.** Un file dentro un gruppo di contenuto: l'allegato di un messaggio, un documento condiviso, un'esportazione. `FluidFileTile` è la riga (icona o miniatura, nome fino a due righe, dimensione, stato, azioni);
`FluidFileChip` è la versione compatta per righe dense. File: `FluidFileTile.kt`, `FluidFileInfo.kt`.

**Firma essenziale.** `FluidFileTile(name, onClick, modifier, mimeType = null, sizeBytes: Long? = null, status = FluidFileStatus.Ready, onCancel = null, onRetry = null, onRemove = null, thumbnail = null, trailingMenu = null, locale)`;
`FluidFileChip(name, onClick, modifier, mimeType = null)`; `fluidFormatFileSize(bytes, locale)`.

**Lo stato lo decide l'app.** `FluidFileStatus.Remote` (da scaricare), `Ready`, `Transferring(progress: () -> Float?)`, `Failed`. **L'engine non scarica niente, non apre URI e non carica immagini**: `onClick` scarica un `Remote` o
apre un `Ready`, durante il trasferimento e dopo un errore è spento. Il progresso è una lambda di **lettura differita** (`{ state.value }`): non leggere il progresso nella riga chiamante, o ogni avanzamento ricompone la lista.
`null` è indeterminato. Annulla (`onCancel`, solo in trasferimento), riprova (`onRetry`, solo dopo l'errore) e rimuovi (`onRemove`) compaiono solo se li passi.

**Comportamento.**
- **Il tipo del file è conservativo.** `FluidFileKind.from(mimeType, name)`: l'**ultima** estensione prevale su un MIME dichiarato e **un eseguibile non viene mai declassato**, né dal nome (un `.apk` con MIME `application/pdf`) né dal
  MIME (un `foto.jpg` dichiarato eseguibile). Il tipo serve all'icona e a TalkBack, non è una certificazione di sicurezza: `FluidFileKind` dice «categoria visiva generica», non «sicuro».
- **Un eseguibile ha l'icona d'avviso e la miniatura non la sostituisce.** `thumbnail` è dell'app (una foto già decodificata), ma per `Executable` si disegna sempre l'icona di attenzione, col tono di avviso: un `.exe` con
  un'anteprima da immagine è il modo in cui un allegato si fa passare per una foto.
- **L'estensione vera è sempre visibile.** Il nome va fino a due righe e si accorcia nel mezzo conservando l'estensione finale (`fluidTruncateFileName`, per code point: mai mezza emoji): `fattura-di-marzo…pdf.exe` si
  legge come eseguibile. Un'estensione patologica più larga di due righe richiede al chiamante spazio sufficiente per leggerla.
- **Le dimensioni sono localizzate** (`fluidFormatFileSize`, decimali: 1000 byte = 1 KB, una cifra decimale al massimo, «1,2 MB» o «1.2 MB» secondo il `locale`; mai «1000 KB»). Dimensione ignota: `null`, non zero.
- **Il chip** ha il bersaglio da 48 dp espanso **fuori dal layout** (`ViewConfiguration`): la superficie è più bassa, il dito la prende lo stesso. Il gesto cede agli scroll degli antenati.

**Accessibilità.** Il nome del tile è un nodo solo: «nome, tipo, dimensione, stato» (`FluidLabels.fileKind` e le parole dei file, `FluidLabels.fileRemote`, `fileTransferring`, `fileFailed`, `fileExecutable`: «Eseguibile o
pacchetto, attenzione»). Le azioni (annulla, riprova, rimuovi) sono bersagli a parte con nome proprio. `trailingMenu` deve usare gli host Fluid (`FluidContextAction`).

**Trappole.** Lascia spazio fra chip vicini: i bersagli espansi si sovrapporrebbero. `FluidFileKind` è un `enum` **nuovo e pubblico**: un `when` esaustivo di un'app si rompe se un domani si aggiunge un tipo, quindi chi lo usa
mette un `else`.
