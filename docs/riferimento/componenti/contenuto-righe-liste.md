<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/contenuto-righe-liste.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Contenuto: righe, liste, impostazioni, card e aperture

Seconda scheda della famiglia «Contenuto» ([`contenuto.md`](contenuto.md) ha l'indice, il «Scegliere in fretta» e le regole trasversali: vetro sul contenitore, colore sulla piastrella, un nodo per riga, una vibrazione per momento).
Qui: il gruppo di righe (`FluidListGroup` + `FluidListRow` + `FluidListDivider` + `FluidToneTile`), le intestazioni e gli stati vuoti, le liste lunghe di vetro (`fluidGlassGroups`), **dalla 3.1.0 le liste lunghe a una riga per elemento** (`fluidGroupedItems`, `fluidSwipeActions`, `FluidJumpPill`), le quattro righe di impostazioni,
`FluidCard` e le aperture di sezione (`FluidHero`, `FluidHeroBand`, `FluidVividCard`). Il seguito (caricamento, segni piccoli, testo, visore, riordino) è in [`contenuto-testo-e-media.md`](contenuto-testo-e-media.md).
Firme complete: [`../api/contenuto.md`](../api/contenuto.md) e [`../api/tema.md`](../api/tema.md).

## Il gruppo, la riga, il separatore, la piastrella

### `FluidListGroup`, `FluidListRow`, `FluidListDivider`, `FluidToneTile`

**Cos'è.** `FluidListGroup` è il contenitore arrotondato (raggio `FluidRadius.Group`, 22 dp, angolo continuo) in cui le righe condividono
un fondo, come in una tabella iOS «inset grouped»; clippa, quindi la tinta di pressione di una riga è tagliata sugli angoli del gruppo.
`FluidListRow` è la riga (titolo, sottotitolo, icona in piastrella, valore, freccia). `FluidListDivider` è il filo fra due righe.
`FluidToneTile` è la piastrella quadrata colorata dietro un'icona, pubblica dalla 3.0 per usarla anche fuori da una riga.

**Firma essenziale.**
```kotlin
FluidListGroup(modifier, glass = false, segment = FluidGroupSegment.Whole) { /* righe e FluidListDivider() */ }

FluidListRow(
  title: String /* o AnnotatedString */, subtitle: String,       // il sottotitolo è obbligatorio, "" per non averlo
  eyebrow: String? = null, meta: String? = null,                 // riga sopra il titolo (nel colore del tono) / riga piccola in fondo
  tone: FluidTone = FluidTone.Neutral,                           // colore di piastrella e occhiello, mai della riga
  badge: (@Composable () -> Unit)? = null,                       // a destra, accanto alla freccia: stretto
  labels: (@Composable () -> Unit)? = null,                      // pillole di stato: accanto al titolo o sotto
  leading: (@Composable () -> Unit)? = null, leadingTile: Boolean = true,
  onClick: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null,
  contextActions: (() -> List<FluidContextAction>)? = null,      // menu contestuale al tocco lungo
  selected: Boolean = false, disclosure: Boolean = true,
  titleMaxLines: Int = Int.MAX_VALUE, subtitleMaxLines: Int = Int.MAX_VALUE,
  animatePress: Boolean = true, animateContent: Boolean = false,
)

FluidListDivider()
FluidToneTile(tone: FluidTone, size = FluidToneTileSize) { Icon(...) }          // tono dell'engine
FluidToneTile(color: Color, size = FluidToneTileSize, veil = color.copy(alpha = 0.16f)) { Icon(...) }  // colore qualunque
```
`FluidTone` ha sei valori: `Primary`, `Success`, `Warning`, `Danger`, `Info`, `Neutral`. Tutti i parametri: [`../api/tema.md`](../api/tema.md).

**Uso** (la forma della galleria, `RowsAndSheetsDemo`/`SettingsTab`):
```kotlin
item(key = "about-header") { FluidSectionHeader(title = "Informazioni") }
item(key = "about") {
  FluidListGroup(glass = true) {
    FluidListRow(title = "Versione", subtitle = "1.2.0")
    FluidListDivider()
    FluidListRow(
      title = "Aggiornamenti", subtitle = "Tocca per controllare",
      tone = FluidTone.Info, leading = { Icon(Icons.Rounded.Sync, contentDescription = null) },
      onClick = { /* … */ },
    )
  }
}
```
Un colore per categoria, scelto dall'app (`FluidToneTile(color = …)`, come in `SettingsTab`):
```kotlin
FluidListRow(
  title = "Documenti", subtitle = "12 elementi", onClick = open,
  leading = { FluidToneTile(color = categoryColor) { Icon(Icons.Rounded.Folder, contentDescription = null) } },
  leadingTile = false,   // altrimenti l'icona finisce in una piastrella dentro la piastrella
)
```

**Comportamento.**
- **Il separatore lo metti tu**, fra una riga e l'altra (non prima della prima né dopo l'ultima). È un filo da 0,5 dp,
  `onSurface` al 10%, con un margine sinistro **fisso di 16 dp**: non segue il testo se c'è una piastrella (il KDoc di `FluidListDivider` dice «parte dove parte
  il testo», il codice no). `fluidGlassGroups` e `FluidGlassGroup` il separatore lo mettono da sole.
- **La piastrella** è 32 dp (`FluidToneTileSize`), raggio 10 dp (`FluidRadius.Small`) e contenuto da 19 dp al centro; raggio e contenuto scalano con `size`.
  Il velo è il 16% del colore del tono. Con `leadingTile = true` (di serie) `leading` entra *nella* piastrella del `tone`: passa un'`Icon`, non un'altra piastrella.
- **Altezze**: una riga è alta quanto un `ListItem` di Material: 56 dp con un solo testo, 72 con due, 88 con tre; gli scheletri (`FluidSkeletonRow`) copiano queste misure.
- **Freccia e ruolo**: con `onClick` e `disclosure = true` c'è una chevron da 18 dp e il nodo è `Role.Button`. Senza `onClick` la riga è un'etichetta: niente freccia, niente ruolo,
  niente pressione. `disclosure = false` serve dove la riga *sceglie* invece di *aprire* (elenco+dettaglio) e dove il `badge` è già un controllo.
- **`selected`** è la riga «mostrata accanto» in un elenco+dettaglio (velo dell'accento al 12%, e `selected` per TalkBack). **Non** è il segno della selezione multipla: quello è `FluidSelectionMark`.
- **`labels` o `badge`.** Il `badge` sta nello slot a destra, che `ListItem` misura *per primo*: due pillole e la freccia lasciavano al titolo una colonna di sillabe. Le `labels` si misurano
  insieme al titolo: stanno accanto (allineate alla linea di base della prima riga) se il titolo ci sta su una riga o se le pillole occupano al più un quarto della larghezza
  (`FluidRowLabelsMaxShare`, interna), altrimenti vanno sotto.
- **`contextActions`** apre il menu contestuale al tocco lungo (la riga si solleva, il menu cresce accanto) e suona `Open` solo se il menu si apre davvero; senza `contextActions`, `onLongClick` suona
  `GestureStart`. Con tutti e due, il click lungo dell'app parte solo se il menu non si è aperto. Preferisci `contextActions`: un tocco lungo che fa una cosa sola senza dirlo non lo scopre nessuno.
- **`animateContent = true`** anima l'altezza se il testo cambia (di serie spento: dentro un gruppo che cresce è la riga a muoversi).
- `FluidListRow(title: AnnotatedString, …)` è lo stesso con stili propri nel titolo (le parole cambiate dall'ultima volta, un termine evidenziato in una ricerca).
- `subtitleMaxLines` tronca con i puntini un riassunto il cui testo intero sta altrove (3.0.1).
- `FluidGroupSegment` (`Whole`, `First`, `Middle`, `Last`) lo usa **solo chi taglia** una lista in pannelli (`fluidGlassGroups`): un gruppo con due angoli tondi per altri motivi sembra rotto il giorno in
  cui la lista sopra o sotto cambia lunghezza. Un lato aperto non porta bordo: è ritagliato di `FluidGroupOpenEdge` (2 dp), per togliere di mezzo il bordo speculare del vetro.

**Trappole.**
- *Sintomo*: titolo spezzato in una colonna di sillabe → pillole di stato nel `badge`. Passale come `labels`.
- *Sintomo*: l'icona dell'app sta in un quadrato dentro un quadrato → hai passato una `FluidToneTile` come `leading` senza `leadingTile = false`.
- *Sintomo*: due vibrazioni (o due bersagli) su una riga con un interruttore → hai messo un `FluidSwitch` nel `badge` di una riga con `onClick`. Dalla 3.0.1 toccare l'interruttore dentro una riga con `onClick`
  non accende più anche la riga, ma la strada giusta è `FluidToggleRow`.
- *Sintomo*: un test che cerca la riga col tag trova un contenitore vuoto → dalla 3.0.0 il tag è sul nodo col testo e col clic (un nodo solo).
- *Sintomo*: «TalkBack non legge il valore a destra» in una riga di impostazioni → il `badge` di `FluidToggleRow`/`FluidChoiceRow`/`FluidPickerRow` non è letto; il valore va nel sottotitolo (il selettore lo dice come stato).
- Il sottotitolo vuoto non lascia una riga vuota sotto il titolo; ma `subtitle` resta un parametro obbligatorio: passa `""`.

**Non usarlo quando.** Hai una card sola, non un elenco (`FluidCard`). Hai righe a vetro una per una (il vetro va sul gruppo). `FluidListItem` è deprecato e delega a `FluidListRow`
(il vecchio `subtitle` è l'`eyebrow`, `supporting` è `subtitle`, `trailing` è `badge`).

**Versione.** `selected`/`disclosure` dalla 2.5; `labels`, `titleMaxLines` e l'overload con `AnnotatedString` dalla 2.8; `leadingTile`, `FluidToneTile` pubblica dalla 3.0; `subtitleMaxLines` dalla 3.0.1.

### Intestazioni, note, stati vuoti (`FluidContent.kt` e `FluidEmptyState`)

- `FluidSectionHeader(title, detail)` è la didascalia quieta sopra un gruppo: titolo in maiuscolo nel colore secondario (un'intestazione in accento fa concorrenza al contenuto e rende «affollata» una pagina di
  impostazioni); è `heading()` per TalkBack dalla 3.0.4, il `detail` no.
- `FluidSectionFootnote(text)` è la nota sotto il gruppo, per la spiegazione che un sottotitolo renderebbe troppo lunga.
- `FluidEmptyState(title, detail, illustration)` dice **perché** non c'è niente e cosa si può fare. Con `illustration` la colonna si centra e prende più aria (24 dp di margine verticale invece di 12); senza è allineata
  a sinistra come una riga. L'illustrazione eredita `onSurfaceVariant` ed è **muta** per TalkBack (decorazione: il titolo dice tutto). L'engine non decide cosa disegnare, dà solo il posto (dalla 3.0.0).
- `FluidLoadingBlock(minHeight = 180.dp)` è uno spinner centrato in un blocco *dentro lo scroll*, sotto il titolo: l'intestazione resta ferma e la schermata non salta quando arrivano i dati.

## Liste lunghe di vetro

### `fluidGlassGroups`, `fluidGlassGroupsIndexed`, `FluidGlassGroup`

**Cos'è.** Il `items { }` di una lista pigra, ma in pannelli di vetro: una sezione di righe diventa uno o più `FluidListGroup(glass = true)`. Esiste perché *il vetro va sul contenitore* ma un
pannello di vetro è una **texture della GPU con un tetto**: una pagina di ottantaquattro righe sono sedicimila pixel, e oltre il tetto il pannello arriva **nero**. Quindi la sezione si spezza, e il taglio
non deve vedersi.

**Firma essenziale.**
```kotlin
fun <T> LazyListScope.fluidGlassGroups(items: List<T>, key: String? = null, maxRows: Int = FluidGlassGroupMaxRows /* 8 */, row: @Composable (T) -> Unit)
fun <T> LazyListScope.fluidGlassGroupsIndexed(items: List<T>, key: String? = null, maxRows: Int = FluidGlassGroupMaxRows, row: @Composable (index: Int, item: T) -> Unit)
@Composable fun <T> FluidGlassGroup(items: List<T>, modifier: Modifier = Modifier, row: @Composable (T) -> Unit)   // un pannello solo
```

**Uso** (da `GapsDemo`):
```kotlin
item(key = "groups-header") { FluidSectionHeader(title = "Lista lunga") }
fluidGlassGroupsIndexed(items = (1..20).toList(), key = "groups") { index, number ->
  FluidListRow(title = "Riga $number (indice $index)", subtitle = "", onClick = {})
}
```

**Comportamento.**
- Ogni pannello è **un `item` della lista pigra** (`contentType = "fluid-glass-group"`, chiave `"$key:0"`, `"$key:1"`…; con `key = null` senza chiave). Venti righe a otto per pannello sono tre item, non venti.
- Il separatore fra le righe lo mette la funzione: non aggiungerlo. L'`index` di `fluidGlassGroupsIndexed` è quello in `items`, non quello nel pannello (per il numero di una classifica, una riga che fa diversamente
  se è la prima, un'animazione sfalsata).
- **Il taglio invisibile** richiede due cose insieme: i pezzi arrotondano solo gli angoli che sono davvero l'inizio e la fine (`FluidGroupSegment`), e il vuoto fra due pezzi si richiude. Dentro una `LazyColumn` l'unico modo è che il pezzo
  **dichiari** un'altezza minore di quella che disegna, esattamente di `spacing + 2 × FluidGroupOpenEdge`: il successivo viene posato a filo. Disegnare oltre i propri limiti è lecito (la lista clippa il viewport, non i singoli item).
  Il separatore della giuntura sta dentro il pannello, *sopra* la fascia ritagliata.
- **Il tetto di otto** (`FluidGlassGroupMaxRows`) è un numero di *composizione* prima che di texture: un pannello è un item solo e tutto il suo contenuto è composto e misurato nel fotogramma in cui entra; a dodici righe multi-riga
  quel fotogramma costava 50–70 ms. Otto tiene il materiale alla scala a cui si legge e il fotogramma d'ingresso nel bilancio.
- `maxRows < 1` lancia `IllegalArgumentException`. Lista vuota: non emette niente (anche `FluidGlassGroup`).
- Serve dentro `FluidScreen` (o qualunque lista che provveda `LocalFluidItemSpacing`, 14 dp di serie) e, per vedere il vetro, un canvas ambientale: senza, i pannelli sono opachi e il taglio resta invisibile lo stesso.
- `FluidGlassGroup` è fatto per le sezioni di un cruscotto da quattro o sei righe dentro una colonna (`fluidColumns`); non spezza, quindi un elenco lungo resta in `fluidGlassGroups`.

**Trappole.**
- **Il prezzo è `animateItem()`**, che funziona solo su un item *diretto* della lista: le righe non si riordinano più con un'animazione propria. Va bene per elenchi che arrivano già ordinati e che nessuno riordina mentre li guarda.
  Per lo stesso motivo non puoi mettere `fluidReorderable` sulle righe di un pannello (la riga non è un item della lista): per un elenco riordinabile ogni riga deve essere un `item` suo.
- Più sezioni nella stessa lista: dai a ognuna una `key` diversa, o le chiavi dei pannelli si pestano.
- Righe molto alte moltiplicano il costo d'ingresso: se le righe sono multi-riga, valuta `maxRows` più basso, non più alto.

**Non usarlo quando.** La lista è breve e senza canvas (basta un `FluidListGroup` in un `item`), o le righe devono riordinarsi/animarsi singolarmente.

**Versione.** 3.0.0 (portate da un'app); `fluidGlassGroupsIndexed` 3.0.1.

## Liste lunghe, una riga per elemento (Fase 1 di Pampa Mail, 3.1.0)

Il caso: una casella che riceve mentre la si legge, un feed paginato, un elenco da cui le righe escono con un gesto. `fluidGlassGroups` non lo regge (sotto, perché) e il vetro per riga non è consentito. Cinque pezzi,
pensati per stare insieme: `fluidGroupedItems` (il contenitore), `FluidGroupedMaterial` (il fondo), `Modifier.fluidSwipeActions` (il gesto), le azioni di TalkBack sulla riga (`customActions`, `fluidAccessibilityActions`),
`FluidJumpPill` (le novità fuori vista). File: `FluidGroupedItems.kt`, `FluidSwipeActions.kt`, `FluidJumpPill.kt`. In più, due ritocchi alle righe vicine: `FluidEmptyState` con un'azione e `FluidStatusBadge` a colore libero
(in fondo a questa sezione).

### Scegliere in fretta

| la mia lista… | uso |
|---|---|
| è breve, o arriva ordinata e nessuno la guarda cambiare, e voglio il **vetro** | `fluidGlassGroups` (pannelli da otto righe, un item per pannello) |
| è lunga, **cambia mentre la si legge** (arrivano righe in cima, ne escono) o è paginata | `fluidGroupedItems(count, key = { id })`: un item per riga, fondo opaco |
| ha righe da archiviare, eliminare, segnare con un gesto | `Modifier.fluidSwipeActions` sul `modifier` della `FluidListRow` |
| deve dire «ci sono 3 nuove» senza spostare la lista sotto il dito | `FluidJumpPill` nell'`overlay` di `FluidScreen` |
| ha una stella, un avatar o un chip toccabili dentro la riga | `FluidListRow(customActions = …)`: per TalkBack sono azioni della riga |
| è vuota o in errore e c'è una cosa da fare | `FluidEmptyState(title, detail, actionLabel, onAction)` |
| ha un'etichetta in un colore che sceglie l'utente | `FluidStatusBadge(label, color)` |
| riordina le righe a mano | un `item` per riga e `fluidReorderable`; non `fluidGlassGroups` |

**`fluidGlassGroups` o `fluidGroupedItems`?** `fluidGlassGroups` mette otto righe in un pannello e il pannello è l'item: perfetto per una sezione che arriva ordinata e resta ferma, ed è l'unico dei due che dà il vetro.
Per una lista che cambia ha tre limiti che nessuno aggira da fuori: le chiavi sono **per posizione** (`"$key:3"`: una riga nuova in cima fa slittare ogni pannello, e chi leggeva a metà lista vede scorrere tutto di un posto),
la lista dev'essere una `List<T>` tutta in memoria (a 50.000 righe no, e una lista paginata non è una `List`), e `animateItem` non arriva alle singole righe. `fluidGroupedItems` ha una **chiave per riga** (l'identificativo del
dato), un'API per **indice** (`count`, `key(index)`, `contentType(index)`, contenuto per indice: Paging si collega senza che l'engine dipenda da Paging) e ogni riga è un item, quindi entra, esce e si sposta con le molle di
`FluidMotion`. Il prezzo: il vetro, che qui non è di serie.

### `fluidGroupedItems`

**Cos'è.** Le righe di una sezione come **un item della lista pigra per riga**, raggruppate: angoli tondi solo sulla prima e sull'ultima, filo da 0,5 dp fra una e l'altra, nessun vuoto in mezzo. Due forme: per indice
(`count`, `key = { index -> id }`) e su una `List<T>` (`key = { item -> id }`, come `items(list)`).

**Uso** (da `LongListsDemo`):
```kotlin
fluidGroupedItems(count = ids.size, key = { index -> ids[index] }) { index ->     // this: FluidGroupedItemScope
  val swipe = rememberFluidSwipeState()                                           // dentro l'item: uno per riga
  FluidListRow(
    title = …, subtitle = …, onClick = { … },
    customActions = listOf(CustomAccessibilityAction("Fissa") { …; true }),       // TalkBack, sul nodo della riga
    modifier = Modifier.fluidSwipeActions(
      state = swipe,
      startAction = FluidSwipeAction("Segna come letto", icon, scheme.primary) { … },
      endAction = FluidSwipeAction("Archivia", icon, scheme.tertiary, removesItem = true) { … },
      shape = segmentShape, fill = rowFill,                                        // dallo scope della riga
    ),
  )
}
```

**Comportamento.**
- **Ancoraggio.** Con la chiave la lista si ancora alla riga che l'utente guarda: una riga che entra sopra la sua posizione **non sposta** niente. Senza chiave la lista si ancora agli indici e le righe scorrono: ammesso solo
  per una lista che non cambia.
- **Come si richiude il vuoto.** La lista mette fra due item `LocalFluidItemSpacing`; fra due righe della stessa sezione non ci deve essere. La riga che non è l'ultima **dichiara** un'altezza minore di quella che
  disegna, esattamente di quello spazio (lo stesso trucco di `fluidGlassGroups`), e la successiva si posa a filo. Il contenuto resta misurato e posato a altezza intera, quindi anche la fascia che sta oltre i limiti dichiarati
  riceve il dito.
- **Lo scope della riga** (`FluidGroupedItemScope`) dà quello che la riga deve sapere del suo posto: `segment` (prima, mezzo, ultima, sola), `segmentShape` e `rowFill`, un fondo **opaco** che somiglia a quello della riga, da
  dipingere quando la riga si sposta (vedi `fluidSwipeActions`). Si ricalcola da indice e conteggio: quando una riga entra o esce, angoli e filo si richiudono da soli.
- `contentType` di serie è `FluidGroupedItemsDefaults.ContentType`: tutte le righe si riciclano fra loro. Righe di forma diversa (una riga e un'intestazione di giorno) passano i loro tipi.
- `dividers = false` per righe che disegnano un filo proprio; `animateItems = false` spegne `animateItem` (col movimento ridotto non anima comunque).
- `count <= 0` non emette niente.

**Trappole.**
- **Le chiavi sono dell'intera lista**: uniche e salvabili in un `Bundle` (stringhe, numeri). Due sezioni con gli stessi id si prefissano (`"posta:$id"`). Mai l'indice.
- **L'ultima riga cambia angoli di colpo** quando quella sopra esce (un raggio animato fuori da Fluid-physics è la regola che non si piega). Con l'uscita in dissolvenza non si nota; si guarda nei fotogrammi.
- Un segnaposto di Paging (`null` all'indice) lo disegna l'app, alla stessa altezza della riga (`FluidSkeletonRow`), dentro lo stesso scope.
- `FluidFastScroller` conta gli item della lista: con Paging servono i segnaposto attivi per avere il totale, l'etichetta per indice dev'essere O(1), e gli item prima della sezione (il titolo di `FluidScreen`,
  un'intestazione) spostano gli indici: l'etichetta li sottrae.
- Il separatore lo disegna la riga di sopra: righe che ne hanno uno proprio vogliono `dividers = false`, o se ne vedono due.

**Non usarlo quando.** Vuoi il vetro sul contenitore e la lista arriva ordinata e ferma (`fluidGlassGroups`), o la sezione è di quattro righe in una colonna (`FluidGlassGroup`, `FluidListGroup`).

### `FluidGroupedMaterial`: opaco, tinto, vetro per riga

Il fondo delle righe. **Non è un `enum`**: un valore nuovo in un `enum` pubblico rompe ogni `when` esaustivo delle app, qui un valore nuovo è una costante in più.

| valore | cos'è | quando |
|---|---|---|
| `FluidGroupedMaterial.Opaque` (di serie) | il fondo di un `FluidListGroup` senza vetro (`surfaceContainerLow`) | sempre, per una lista lunga: il più economico e quello che la regola consente |
| `FluidGroupedMaterial.Tinted` | la pellicola del vetro **dipinta**, senza catturare niente; su un canvas ambientale lascia vedere la lavata (nitida, non rifratta); senza canvas è uguale a `Opaque` | quando il vetro non regge e si vuole comunque la lavata |
| `FluidGroupedMaterial.GlassSegments` | **una lastra di vetro di contenuto per riga**, con `sampleOnce` | **solo** per il banco di misura; richiede `@OptIn(FluidExperimentalRowGlass::class)` |

**Il vetro per riga è sperimentale e va contro la regola «vetro sul contenitore»**: dodici righe a schermo sono dodici lastre, ognuna una registrazione di layer e una catena di `RenderEffect`; una riga che entra è una lastra nuova
che cattura. `FluidExperimentalRowGlass` è `RequiresOptIn` a livello `ERROR` perché non lo si usi per distrazione. La variante si promuove solo se su un telefono vero i fotogrammi lenti restano sotto l'1% e
`FluidGlassDiagnostics.totalCaptures` sta fermo a schermo fermo; altrimenti la lista lunga resta opaca o tinta e il vetro sta su barra, pillola, menu e fogli. Sul vetro `animateItems` anima solo lo spostamento (mai un'alfa
sopra una lastra).

### `Modifier.fluidSwipeActions`, `FluidSwipeAction`, `FluidSwipeState`

**Cos'è.** Le azioni che una riga rivela scorrendo di lato: archivia, segna come letta, elimina, fissa. Una per verso (`startAction` verso la fine: a destra in LTR; `endAction` verso l'inizio), colore e icona **dell'app**
(l'engine non sa cosa vuol dire «archivia»), il colore dell'icona **del contrasto** (≥ 4,5:1, lo stesso conto di `FluidStatusBadge`: un giallo chiaro non porta un'icona bianca).

**`FluidSwipeAction(label, icon, color, removesItem, onAction)`.** `label` è obbligatorio e non è decorazione: è il nome che TalkBack offre fra le azioni della riga, perché un gesto che solo chi vede può fare non è
un'azione. `removesItem = true`: confermata, la riga esce del tutto e `onAction` parte **a uscita finita**; l'app toglie il dato e la lista richiude il posto. `false`: `onAction` parte al rilascio e la riga torna a posto
(letta/non letta, fissa). L'azione parte **una volta per gesto**, anche se la lista cambia durante l'uscita.

**Va sul nodo della riga.** Per `FluidListRow` è il suo `modifier` (sta sul `ListItem` stesso, un nodo solo): così le azioni di TalkBack si **sommano** a quelle della riga invece di finire su un discendente, dove nessuno le
raggiunge. Lo stato (`rememberFluidSwipeState()`) si crea **dentro** l'item, uno per riga, ed è effimero di proposito: un gesto a metà non sopravvive a una rotazione, e una riga riciclata torna a posto.

**Chi ha il dito.**
- **Lo scorrimento verticale vince sempre.** Il gesto si decide alla soglia di tocco: orizzontale solo se almeno una volta e mezza più orizzontale che verticale; un primo movimento verticale lascia il dito alla lista, un
  diagonale non lo prende nessuno per conto della riga.
- **Il bordo non è della riga.** I primi 20 dp per lato (`FluidPageMotion.EdgeZoneDp`, la zona del back dell'engine) e gli inset dei gesti di sistema non avviano lo scorrimento: lì vincono il back dell'engine e quello di
  sistema. La riga non chiede mai un'esclusione dai gesti di sistema.
- **La pressione lunga resta del menu contestuale**; un trascinamento orizzontale preso dalla riga annulla click lungo e click. Un tasto dentro la riga tiene il dito. Un secondo dito, o un dito che sparisce, è un annullo.

**Il gesto.** Segue il dito 1:1 fino alla soglia (35% della larghezza, al più 180 dp: le soglie del back a dito), poi a metà velocità; dal lato senza azione un elastico di 16 dp (niente, col movimento ridotto). Attraversare la
soglia suona `Threshold`, in entrambi i versi, con isteresi di 12 dp (un dito che trema non vibra a raffica). Al rilascio conferma: oltre la soglia, o con una spinta di 400 dp/s verso l'azione da almeno metà soglia; una spinta
contraria annulla anche oltre la soglia. Le molle partono dalla velocità del dito (`FluidMotion.swipeSettle`, `FluidMotion.swipeExit`) e col movimento ridotto diventano un salto.

**Trappole.**
- **Passa `shape` e `fill` dello scope** (`segmentShape`, `rowFill`): la riga disegna il proprio fondo mentre si sposta, perché dietro c'è il colore dell'azione; un fondo diverso da quello del gruppo si vede come una riga di
  un altro colore.
- Lo spostamento è **di disegno**: tocco e semantica restano dove la riga sta a riposo.
- Una riga uscita (`FluidSwipeState.isDismissed`) che l'app non toglie resta vuota: l'app la rimette con `FluidSwipeState.reset()` (anche per un «Annulla»).
- `enabled = false` spegne gesto **e** azioni di TalkBack: in selezione, per esempio, un gesto che archivia mentre si sta scegliendo è un incidente.
- Il gesto non è l'unica via: l'azione si offre anche dal menu contestuale (`contextActions`) e da TalkBack; un'azione solo-gesto è un trucco.

### Azioni di TalkBack sulla riga: `customActions`, `fluidAccessibilityActions`

Per chi non vede, un controllo dentro una riga fusa è una parola nel nome e niente più: stella, archivia, seleziona, un chip o un avatar toccabili devono essere **azioni sul nodo della riga**, perché messe su un
discendente sono irraggiungibili. `FluidListRow(customActions = listOf(CustomAccessibilityAction(…)))` le mette lì. Per una riga composta a mano, `Modifier.fluidAccessibilityActions(actions)`.

**Si sommano, in qualunque ordine.** Un nodo ne riceve da più parti (la riga, lo scorrimento di lato, l'app) e `semantics { customActions = … }` scritto due volte sullo stesso nodo tiene solo l'ultimo: una delle due liste
spariva in silenzio. `fluidAccessibilityActions` le unisce; lista vuota = il modificatore non c'è (stesso albero di prima). L'azione di TalkBack deve fare lo stesso del gesto o del tasto: stesso esito, stessa conferma.
`customActions` è l'ultimo parametro di `FluidListRow` e di serie è vuoto: la riga di sempre, nodo per nodo.

### `FluidJumpPill`

**Cos'è.** La pillola di vetro «N nuove ↑»: arrivano elementi sopra (o sotto) il punto in cui l'utente sta leggendo, e invece di spostargli la lista glielo si dice, con un tocco per andarci. Con le chiavi stabili di
`fluidGroupedItems` la lista resta ferma e le righe nuove entrano fuori vista: senza un segno l'utente non sa che ci sono. `FluidJumpDirection.Up` (di serie) per una casella o un feed, `Down` per una conversazione.

**Uso.** Nell'`overlay` di `FluidScreen`, che passa il `backdrop`:
```kotlin
FluidJumpPill(visible = newAbove > 0 && !atTop, label = "$newAbove nuove in alto",
  onClick = { newAbove = 0; scope.launch { listState.animateScrollToItem(0) } }, backdrop = backdrop)
```
**Quando mostrarla lo decide l'app** (di solito: ci sono righe nuove sopra **e** la prima riga visibile non è in cima); toccandola l'app scorre e azzera il conteggio, e lo azzera anche quando l'utente ci arriva da solo.

**Comportamento.** È un `FluidGlassButton` compatto (bersaglio 48 dp, pressione e aptica dei tasti). Entra per **offset e alfa, mai per scala** (un layer scalato sopra un vetro rifrange ciò che non c'è più: `regole.md`,
«Un ingresso non scala un vetro»); col movimento ridotto compare e sparisce e basta. TalkBack la annuncia senza interrompere (`liveRegion` educata) quando compare e quando l'etichetta cambia.

**Trappole.** L'etichetta è quella che si legge e deve dire **cosa e dove** («3 nuove in alto»): la freccia è decorazione. La posizione (sotto la barra di stato, sopra la pillola della barra) la decide l'app col `modifier`.
Non è una notifica: non ha durata, sparisce quando il conteggio torna a zero.

### `FluidEmptyState` con un'azione, `FluidStatusBadge` a colore libero

- **`FluidEmptyState(title, detail, actionLabel, onAction, modifier, illustration)`** è un **overload**, non due parametri in coda, di proposito: l'ultimo parametro di `FluidEmptyState` è una lambda (l'`illustration`), e chi la
  passava come lambda finale l'avrebbe vista finire su un `onAction` aggiunto dopo di lei. Qui l'azione è obbligatoria e l'illustrazione resta l'ultima. Il tasto è un `FluidButton` tinto, sotto il testo, allineato come il
  testo (al centro con l'illustrazione, a sinistra senza), alto almeno 48 dp, e a testo grande va a capo invece di tagliarsi: è lui il controllo per TalkBack, il titolo resta il titolo. Si usa per «Riprova» in uno stato
  d'errore, «Aggiungi un account» in un vuoto: il posto dove l'utente cerca il tasto.
- **`FluidStatusBadge(label, color)`** è la pillola di stato in un **colore qualunque**: l'etichetta scelta dall'utente, la cartella, la materia. I sei toni dell'engine non bastano quando il colore è un dato. Il fondo è il
  colore pieno e **il testo lo sceglie il contrasto**, mai l'occhio: scuro o chiaro, quello che col fondo fa almeno 4,5:1 (`fluidLegibleInk`: il quasi-nero o il quasi-bianco; nei grigi medi, dove i due si equivalgono, nero o
  bianco puri, che ci arrivano sempre, il peggiore è 4,58:1). Un colore **con trasparenza** si misura già composto sulla superficie, cioè come lo si vede. Stessa forma, misura e tipografia della pillola a tono: in una riga
  le due stanno insieme (`FluidListRow(labels = { … })`). È una pillola, **non un controllo**: non si tocca.
  Trappola: un colore che l'app cambia a runtime non porta mai una pillola illeggibile, ma l'**identità** del colore (due etichette quasi uguali) non si distingue da questo: per distinguere serve anche il testo.

**Versione.** `fluidGroupedItems`, `FluidGroupedMaterial`, `fluidSwipeActions`, `FluidJumpPill`, `customActions`, `FluidEmptyState` con azione, `FluidStatusBadge(label, color)`: 3.1.0 (Pampa Mail, F1.2).

## Righe di impostazioni (`FluidSettingRows.kt`)

Quattro righe che esistevano in ogni app in forme diverse e sbagliavano in modo diverso la stessa cosa. Tutte: stanno dentro un `FluidListGroup`, cedono (tinta, mai scala), sfumano al 50% se `enabled = false`
(`FluidSettingRowDisabledAlpha`, interna, la stessa dell'interruttore; sfumano anche la tinta di pressione e il fuoco, perché l'alpha è fuori da tutto) e non rispondono da spente, hanno `tone`, `leading`, `leadingTile` come `FluidListRow`.

| riga | cosa fa | ruolo TalkBack | aptica |
|---|---|---|---|
| `FluidToggleRow` | la riga **è** l'interruttore | `Role.Switch` + `ToggleableState` | `ToggleOn`/`ToggleOff`, mai `Tap` sopra |
| `FluidChoiceRow` | una scelta singola, spunta a destra | `Role.RadioButton` + `selected` | `Tick` solo se la scelta si sposta |
| `FluidPickerRow` | la riga diventa un pop-up di scelte | `Role.DropdownList`, stato = scelta | `Open` all'apertura, `Tick` se cambia |
| `FluidSliderRow` | titolo, valore in parole, slider sotto | lo slider (nome = titolo, stato = `valueLabel`) | quella dello slider |

### `FluidToggleRow`

**Cos'è.** Una riga on/off in cui il bersaglio è tutta la riga (almeno 48 dp, in pratica 56 o 72 con il sottotitolo). Prima chi dava il callback all'interruttore *e* alla riga aveva due bersagli per un tocco; chi non lo dava a
nessuno aveva un bersaglio alto quanto la pista, 28 dp.

**Firma essenziale.**
```kotlin
FluidToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit,
  subtitle: String = "", enabled: Boolean = true, tone: FluidTone = FluidTone.Neutral,
  leading: (@Composable () -> Unit)? = null, leadingTile: Boolean = true)
```

**Uso.**
```kotlin
FluidListGroup(glass = glassOn) {
  FluidToggleRow(
    title = "Notifiche", subtitle = "Un avviso quando qualcosa cambia.",
    checked = notifications, onCheckedChange = { notifications = it },
    tone = FluidTone.Info, leading = { Icon(Icons.Rounded.Notifications, contentDescription = null) },
  )
  FluidListDivider()
  FluidToggleRow(title = "Sincronizza", checked = sync, onCheckedChange = { sync = it }, enabled = notifications)
}
```

**Comportamento.**
- Un tocco ovunque sulla riga commuta; **un dito sul pomello lo trascina** (dalla 3.0.4: prima era un disegno, `onCheckedChange = null`, e nessun interruttore di riga si poteva far scorrere): il rilascio decide, un tocco commuta, la pagina sotto non
  scorre e il back dal bordo non c'entra. Un tocco sul pomello e un tocco altrove commutano ciascuno **una** volta.
- Una vibrazione per cambio: la suona chi commuta (l'interruttore per il suo gesto, la riga per il suo tocco); i due gesti non si sovrappongono. La vibrazione generica della riga è spenta (`fluidRowPressable(haptic = null)`): l'aptica
  fonde due eventi a meno di 70 ms ma fondere sceglie il **primo**, cioè quello sbagliato.
- Disattivata sfuma e non risponde; per TalkBack è «disattivato», non solo grigia, e non si trascina.

**Trappole.** Quello che sta nella riga oltre titolo e sottotitolo non è letto (vedi la regola 3 in [`contenuto.md`](contenuto.md)). Il `tone` colora la piastrella di `leading`, non la riga.

**Non usarlo quando.** L'interruttore deve stare fuori da una lista (`FluidSwitch`, nella famiglia dei controlli).

**Versione.** 3.0.0; `leadingTile` 3.0.1; pomello trascinabile 3.0.4.

### `FluidChoiceRow`

**Cos'è.** Una scelta singola con la **spunta a destra**, come nelle impostazioni di iOS, non un radio: tre radio sono tre cerchi che chiedono attenzione per dire una cosa sola; la spunta ne disegna uno, sulla riga scelta.
Per TalkBack è comunque un radio (`Role.RadioButton`, `selected`).

**Firma essenziale.**
```kotlin
FluidChoiceRow(title: String, selected: Boolean, onSelect: () -> Unit,
  subtitle: String = "", enabled: Boolean = true, tone: FluidTone = FluidTone.Neutral,
  leading: (@Composable () -> Unit)? = null, leadingTile: Boolean = true)
```

**Uso.**
```kotlin
FluidListGroup(modifier = Modifier.selectableGroup(), glass = glassOn) {   // "1 di 3" per TalkBack
  options.forEachIndexed { index, option ->
    if (index > 0) FluidListDivider()
    FluidChoiceRow(title = option.label, subtitle = option.detail, selected = sort == option, onSelect = { sort = option })
  }
}
```

**Comportamento.**
- La spunta sta in uno spazio **sempre riservato** (24 dp): comparendo solo sulla riga scelta spostava il testo di venti dp a ogni cambio e un titolo lungo si spezzava solo mentre era scelto. Il passaggio fra due righe è una
  dissolvenza incrociata (entra 140 ms, esce 120 ms, onora il movimento ridotto).
- `Tick` solo se la riga **non** era già scelta. `onSelect` si chiama comunque a ogni tocco, anche sulla riga già scelta: il chiamante è spesso un foglio che deve chiudersi anche quando la scelta è quella di prima.

**Trappole.** Senza `Modifier.selectableGroup()` sul gruppo TalkBack non dice «1 di 3». Non fare del `leading` una piastrella tua senza `leadingTile = false`.

**Non usarlo quando.** Le alternative sono molte o secondarie (`FluidPickerRow`), o la scelta è multipla (`FluidToggleRow` per voce, o `FluidSelectionMark`).

**Versione.** 3.0.0; `leadingTile` 3.0.1.

### `FluidPickerRow`

**Cos'è.** Il selettore delle impostazioni di iOS: titolo a sinistra, scelta attuale a destra con le due frecce (`UnfoldMore`), e al tocco **la riga diventa il pop-up** con le alternative come righe di scelta. Tre app se l'erano scritto con
un `FluidGlassModalPortal` e una lista di `FluidChoiceRow`.

**Firma essenziale.**
```kotlin
FluidPickerOption<K>(key: K, label: String, subtitle: String = "")
FluidPickerRow(title: String, selectedKey: K, options: List<FluidPickerOption<K>>, onSelected: (K) -> Unit,
  subtitle: String = "", enabled: Boolean = true, tone: FluidTone = FluidTone.Neutral,
  leading: (@Composable () -> Unit)? = null, leadingTile: Boolean = true, paneTitle: String = title)
```

**Uso** (da `SettingsTab`):
```kotlin
FluidPickerRow(
  title = "Qualità", selectedKey = quality,
  options = listOf(FluidPickerOption("bassa", "Bassa", subtitle = "File piccoli"), FluidPickerOption("media", "Media")),
  onSelected = { quality = it },
)
```

**Comportamento.** Una scelta chiude il pop-up e chiama `onSelected` **solo se la chiave cambia**; un tocco fuori lo chiude e basta. Con molte alternative il pop-up scorre (alto al più l'85% dello schermo). Per TalkBack è **un** nodo,
un elenco a discesa con il titolo come nome e la scelta come stato; nel pop-up le alternative sono un gruppo di radio. Un colpo `Open` all'apertura, un `Tick` se la scelta si sposta. Mentre il pop-up è aperto la riga non si disegna:
è lei che è diventata il pannello (origine della trasformazione).

**Trappole.**
- **Serve un `FluidGlassModalHost` alla radice**, come per ogni portale: senza, la riga c'è e il tocco non apre niente (e non va in crash).
- Se `selectedKey` non è fra le chiavi di `options` il valore a destra è **vuoto**, senza errore.
- Il pop-up è in vetro: il vetro lo dà l'host, non la riga.

**Non usarlo quando.** Le alternative sono due o tre e conviene vederle (gruppo di `FluidChoiceRow`).

**Versione.** 3.0.1.

### `FluidSliderRow`

**Cos'è.** Una riga con uno slider nella misura di una riga di gruppo: titolo e valore in parole sopra, `FluidSlider` sotto (luminosità, volume, soglia, minuti di un timer).

**Firma essenziale.**
```kotlin
FluidSliderRow(title: String, value: Float, onValueChange: (Float) -> Unit,
  valueLabel: String? = null, subtitle: String = "", valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  snapValues: List<Float> = emptyList(), enabled: Boolean = true, onValueChangeFinished: (() -> Unit)? = null,
  tone: FluidTone = FluidTone.Neutral, leading: (@Composable () -> Unit)? = null, leadingTile: Boolean = true)
```

**Uso.** `FluidSliderRow(title = "Volume", value = volume, onValueChange = { volume = it }, valueLabel = "${(volume * 100).roundToInt()}%")`.

**Comportamento.** `valueLabel` («30 minuti», «-12 dB») è anche **lo stato che TalkBack legge**: senza, direbbe una percentuale della pista. Il titolo è il nome dello slider. Le cifre sono tabulari (`tnum`): il valore non balla mentre il dito si muove.
Il gesto è dello slider (un trascinamento di lato è suo, uno verticale è della pagina); la riga non ha un tocco suo, non è un bersaglio. Margini: 16 dp ai lati, 12 sopra e sotto. `snapValues`: vedi `FluidSlider`
(famiglia dei controlli, [`../api/controlli.md`](../api/controlli.md)). `onValueChangeFinished` è il momento di salvare.

**Trappole.** Se `enabled = false` sfuma il testo e lo slider si spegne da solo (non insieme, o arriverebbero al quarto dell'opacità). Il testo sopra è muto per TalkBack: dice tutto lo slider. Una riga senza `valueLabel` fa leggere una percentuale.

**Versione.** 3.0.1.

## Card

### `FluidCard` (e `FluidMetricTile`, `FluidEditorialCard`)

**Cos'è.** Il riquadro autonomo: una superficie con angolo continuo `FluidRadius.Card` (18 dp), padding 16 dp, contenuto in colonna con 10 dp fra gli elementi. Il corrispettivo «da solo» del `FluidListGroup`: stessa regola del vetro.

**Firma essenziale.**
```kotlin
FluidCard(modifier, highlighted: Boolean = false, onClick: (() -> Unit)? = null, enabled: Boolean = true,
  glass: Boolean = false, sampleOnce: Boolean = true, resampleIntervalMillis: Long = 0L,
  animateContent: Boolean = true, animatePress: Boolean = true, content: @Composable ColumnScope.() -> Unit)
```

**Uso.**
```kotlin
FluidCard(glass = true) {
  Text("Titolo", style = MaterialTheme.typography.titleMedium)
  Text("Una cosa da dire.", style = MaterialTheme.typography.bodyMedium)
  FluidButton(text = "Avvia", onClick = start)
}
```

**Comportamento.**
- **Fondo.** Opaca: `surfaceContainerLow` (o `surfaceContainerHigh` se `highlighted`). Su vetro: trasparente, e `highlighted` sopravvive come una **lavata** al 28% di `surfaceContainerHigh` (una card evidenziata che diventasse opaca sarebbe l'unica
  della pagina non fatta dello stesso materiale: si legge come un errore, non come enfasi).
- **Con `onClick`** cede con una scala a 0,974 (`animatePress = false` la toglie); il nodo cliccabile è suo. Senza `onClick` e abilitata è una superficie inerte.
- **`enabled = false`** (3.0.1): non risponde e il suo **contenuto** sfuma al 50% (la card no: un alpha sul vetro farebbe sfumare anche quello che il vetro rifrange, e la card spenta deve restare dello stesso materiale delle vicine).
  Per TalkBack è «disattivata».
- **`animateContent`** (di serie vero) anima l'altezza quando il contenuto cambia.
- **`sampleOnce` e `resampleIntervalMillis` (3.0.4).** Sono i due parametri del vetro di livello più basso. Di serie la card **campiona il fondale una volta** e tiene la cattura mentre si muove (scorre con la lista): il fondale di una pagina è la
  lavata ferma, e una lavata che viaggia con il pannello non si distingue da una ferma. Se invece la card sta sopra un fondale che **cambia da solo** (un canvas animato, un'immagine che scorre) la cattura vecchia si porterebbe in giro:
  passa `sampleOnce = false`, e per non rifare la cattura a ogni fotogramma aggiungi `resampleIntervalMillis` (100 ms sotto una sfocatura da 10 dp non si distinguono, e costano un dodicesimo; misurato su un Galaxy S25:
  44 ms a fotogramma con una cattura per pannello, 61 ms con nove vive). Dal sorgente di `drawBackdrop` (dedotto): con `sampleOnce = true` la cattura si rifà solo se cambia la **dimensione** della card, quindi `resampleIntervalMillis` da solo,
  con `sampleOnce = true`, non cambia niente. Zero vuol dire «quando serve», cioè a ogni fotogramma su un fondale animato.
- Non ha un `Modifier` per il menu contestuale: per quello `Modifier.fluidContextMenu` (famiglia dei menu).

**Trappole.** `glass = true` senza canvas ambientale è una card opaca, non un errore. Una card di vetro dentro un foglio di vetro diventa vetro impilato (3.0.0). Non annidare una `FluidCard(glass = true)` dentro un `FluidListGroup(glass = true)`
della stessa pagina senza guardarla: sono due pannelli di vetro uno sull'altro.

**Non usarla quando.** Hai un elenco di voci (`FluidListGroup`). Vuoi un'apertura di schermata con un valore in primo piano (`FluidHero`). Vuoi un riquadro di un solo colore saturo (`FluidVividCard`).

**Versione.** `glass` dalla 1.5.0; `enabled` 3.0.1; `sampleOnce`/`resampleIntervalMillis` 3.0.4.

Gli altri riquadri di `theme/`: `FluidMetricTile(label, value, detail, tone, onClick, glass)` è una tessera di dato (etichetta maiuscola, valore `headlineSmall` grassetto nel colore del tono, dettaglio); con `glass = true` tiene il colore del
tono al 34% sul vetro, così una tessera `Warning` resta riconoscibile. `FluidEditorialCard(color, contentColor)` è un riempimento da 20 dp senza vetro né pressione (nessuna ombra: un gruppo si stacca dallo sfondo per colore, l'ombra è per le cose che
galleggiano davvero). Le due non hanno `enabled` né `sampleOnce`.

## Aperture di sezione: Hero, fascia, card vivida

### `FluidHero`, `FluidHeroBand`, `FluidHeroTone`, `FluidHeroMotif`

**Cos'è.** L'apertura editoriale di una schermata piena di dati: **un solo valore vero in primo piano** e i fatti secondari compatti. Il *tono* è una posizione su un anello costruito attorno alle tre famiglie di accento di Material con
miscele nei punti intermedi: serve a far sì che sei o sette destinazioni si distinguano restando imparentate (colori arbitrari per sezione le fanno sembrare sette app; tutti uguali le rende indistinguibili). Il *motivo* è una forma astratta
dietro (barre, punti, onde): è quello che rende riconoscibili due schermate vicine che condividono la famiglia tonale. **Il dominio resta fuori**: l'app mappa le sue sezioni su tono e motivo.

**Firma essenziale.**
```kotlin
enum class FluidHeroTone { Primary, PrimaryToSecondary, Secondary, SecondaryToTertiary, Tertiary, TertiaryToPrimary, Alert }
enum class FluidHeroMotif { Glow, Bars, Dots, Ripples, Ticks, Cards, Figures }
FluidHeroMetric(label: String, value: String, onClick: (() -> Unit)? = null)

FluidHero(tone, motif, eyebrow: String, value: String, title: String, description: String, icon: ImageVector,
  metrics: List<FluidHeroMetric> = emptyList(), urgent: Boolean = false, actionLabel: String? = null, onAction: (() -> Unit)? = null)

FluidHeroBand(tone, motif, eyebrow: String, value: String, label: String, icon: ImageVector,
  urgent: Boolean = false, trailing: (@Composable RowScope.() -> Unit)? = null)
```

**Uso** (la fascia, da `MaterialTab`):
```kotlin
FluidHeroBand(tone = FluidHeroTone.Tertiary, motif = FluidHeroMotif.Bars, eyebrow = "Fascia di sezione",
  value = "7", label = "toni sull'anello", icon = Icons.Rounded.Notifications)
```
Lo stesso anello pilota il fondale di `FluidScreen`: `FluidAmbient(tone = FluidHeroTone.Primary, motif = FluidHeroMotif.Cards)`, così intestazione e fondale non possono divergere.

**Comportamento.**
- **`FluidHero`** è un pannello sul contenitore *slavato* del tono (`primaryContainer` ecc., con un velo del colore d'accento, 9–14% secondo il tono), raggio 28 dp, padding 20×18 dp; il motivo sta in alto a destra in 152×132 dp.
  Il valore (`displaySmall`, grassetto, una riga) **scorre in verticale** quando cambia, in una finestra ritagliata: senza il clip si vedrebbero per un istante due copie traslucide.
  Le `metrics` sono al più **tre** (`take(3)`), disposte in 3, 2 o 1 colonna secondo lo spazio misurato e la scala del carattere: celle minime di 104 dp × `fontScale` (limitata a 1–1,7), spazio 8 dp (`fluidHeroMetricColumnCount`).
  Una metrica con `onClick` ha freccia e ruolo pulsante; il pulsante d'azione appare solo se **entrambi** `actionLabel` e `onAction` sono dati, è una capsula alta almeno 48 dp che cresce sotto il dito.
- **La navigazione resta attaccata** a una metrica esplicita o all'azione: l'apertura non nasconde mai più destinazioni dietro un solo bersaglio (il pannello in sé non è cliccabile).
- **`urgent`** promuove **qualsiasi** tono alla famiglia dell'errore (non solo `Alert`): una schermata che ha qualcosa di urgente da dire lo dice allo stesso modo ovunque. `Alert` è un secondario virato appena verso l'errore.
- **`FluidHeroBand`** è la stessa idea quando lo spazio conta: una fascia di circa ottanta dp sul colore **pieno** della sezione (gradiente verso la famiglia successiva dell'anello), con **un solo fatto** (valore + etichetta); i fatti secondari stanno
  nella pagina. Il contenuto (chiaro o scuro) lo sceglie `FluidVividColors.from` sul capo peggiore del gradiente. `trailing` è uno slot a destra.
- Niente vetro, niente `glass`.

**Trappole.**
- *Sintomo*: «`onAction` non fa niente / il pulsante non c'è» → hai dato solo uno dei due, `actionLabel` o `onAction`.
- *Sintomo*: una quarta metrica non compare → `FluidHero` ne mostra tre.
- Sette toni sono sette partenze **diverse** solo con gli accenti che stanno lontani dagli ancoraggi della derivazione: un preset senza poli posato *sopra* un ancoraggio fa collassare terziario o secondario nel primario
  (`FluidVividColorsTest` prova l'anello pieno solo per i preset elencati). Se aggiungi un ottavo tono, il test dell'anello cade apposta.

**Non usarlo quando.** La schermata non è «piena di dati» o non ha un valore da mettere in primo piano (una `FluidCard(highlighted = true)` per un riquadro che dice una cosa). `FluidHeroCard` è deprecato senza delega.

**Versione.** `FluidHero` 1.2.0; `FluidHeroBand` 1.10.0; `urgent` per tutti i toni da 1.2.0.

### `FluidVividCard`, `FluidVividColors`, `FluidVividEffect`

**Cos'è.** Una superficie **interamente del proprio colore**: gradiente saturo e contenuto nel colore leggibile su di esso. Il vocabolario degli elementi *separati*, e il controcanto della regola dei gruppi: dentro un `FluidListGroup` il tono sta sulla
piastrella perché le righe tinte fanno un mosaico, ma un elemento che sta da solo nella pagina può portare il colore su tutta la superficie, e dietro una chrome di vetro un campo di colore vero è ciò che rende visibile la rifrazione.

**Firma essenziale.**
```kotlin
FluidVividColors(start: Color, end: Color, content: Color)
FluidVividColors.from(start: Color, end: Color = start)          // sceglie il contenuto, mai a occhio
enum class FluidVividEffect { None, Sheen, Ruled }
FluidVividCard(colors, effect = FluidVividEffect.None, onClick = null, onLongClick = null,
  contextActions: (() -> List<FluidContextAction>)? = null, contentPadding = PaddingValues(18.dp)) { /* ColumnScope */ }
```

**Uso** (da `MaterialTab`):
```kotlin
FluidVividCard(colors = FluidVividColors.from(Color(0xFF00A86B), Color(0xFF00C7BE)), effect = FluidVividEffect.Sheen) {
  Text("8,5", style = FluidTextStyles.largeNumeric)
  Text("Con lo sheen: la banda di luce passa solo quando la card è a schermo.", style = MaterialTheme.typography.bodyMedium)
}
```

**Comportamento.**
- **`from` sceglie il contenuto sul capo peggiore.** Confronta un quasi-nero (`0xFF121214`) e un quasi-bianco (`0xFFFDFDFF`) contro *entrambi* gli estremi del gradiente e prende quello con il rapporto peggiore più alto: una card è leggibile quanto il suo angolo
  meno leggibile. Il bianco fallisce su diversi accenti (2,2:1 sull'arancione di iOS): per questo il contenuto è calcolato, non assunto. Su blu e viola pieni nessun contenuto singolo arriva a 4,5:1 (il bianco sul blu-indaco fa ~3,95): il test chiede 4,5 per le famiglie
  saturate normali e 3,5 per quelle a luminanza media.
- **`Sheen`**: una banda di luce diagonale (picco al 13% di opacità) attraversa la superficie, 2,6 s di moto e 1,9 s di pausa. Per **la** card di una pagina che si è guadagnata di sembrare preziosa: su ogni card è carta da parati. Gira **solo mentre la card è
  almeno al 30% in vista** (`onVisibilityChanged`) e solo se la politica concede il movimento decorativo; a movimento ridotto la banda è dipinta una volta, ferma al 30% dell'altezza, così la card tiene la sua distinzione senza muoversi. È il solo `tween` ripetuto fuori da
  un token del movimento: è periodico, una molla non avrebbe niente a cui aggrapparsi. La banda è disegnata **sopra** il contenuto (un riflesso che saltasse il testo si leggerebbe come dietro l'inchiostro).
- **`Ruled`**: righe di quaderno tenuissime (11% del colore del contenuto, la prima a 52 dp, poi ogni 22 dp, con un margine a 14 dp), ferme; vanno bene su tutte le tessere di una griglia perché non sono decorazione, sono ciò che quelle tessere sono. Disegnate **dietro** il testo.
- Angolo `FluidRadius.Group` (22 dp), cede con scala 0,97 (non cresce: è una superficie); con `onClick` è `Role.Button`. Con `contextActions` il menu prende il **colore della card** (un pannello grigio sopra una superficie satura sembra arrivato da un'altra parte).
- Non ha vetro né `enabled`.

**Trappole.** Non passare un `content` scelto a occhio (`Color.White`): il bianco su arancione è 2,2:1. Non usare `Sheen` su una griglia di card: ogni card in viewport tiene un'animazione infinita.

**Non usarla quando.** Sei dentro un `FluidListGroup` (colore sulla piastrella) o la superficie deve essere di vetro (`FluidCard(glass = true)`).

**Versione.** 1.10.0 (insieme a `FluidHeroBand` e al menu contestuale col colore della card).

