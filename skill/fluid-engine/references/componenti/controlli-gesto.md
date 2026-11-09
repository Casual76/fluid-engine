# Controlli a gesto: interruttore, cursore, scrubber

Seconda metà della scheda [controlli.md](controlli.md): i controlli che si **afferrano e scorrono** (`FluidSwitch`,
`FluidSlider`, `FluidScrubber`), più le licenze dei port (Kyant0 e LibrePods) che stanno dietro a questi tre e ai tasti.
Tasti, scelte, campo di testo e le regole comuni (disattivato, bersagli da 48 dp, `backdrop`, TalkBack, ripiego senza vetro)
sono nel file principale. Le righe di impostazioni che *contengono* un interruttore o un cursore (`FluidToggleRow`,
`FluidSliderRow`) sono nella scheda [contenuto.md](contenuto.md): qui si dice come i due controlli si comportano dentro la
riga. Catalogo delle firme: [../api/controlli.md](../api/controlli.md).

Il modello mentale: un pomello a goccia (bianco e opaco a riposo, lente di vetro sotto il dito), un gesto che è della riga
intera, il rilascio che decide, e la pagina che non scorre mentre il dito li trascina. Lo scrubber ne è il parente per ciò
che **non** è tuo: la posizione la possiede un lettore.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| Acceso / spento | `FluidSwitch`; in una pagina di impostazioni la riga è `FluidToggleRow` ([contenuto.md](contenuto.md)) |
| Un valore in un intervallo (volume, soglia) | `FluidSlider` |
| Una scelta fra pochi scatti su una pista | `FluidSlider(snapValues = …)`; con `snapThreshold = 1f` è a gradini |
| Un cursore con titolo e valore in parole | `FluidSliderRow` ([contenuto.md](contenuto.md)) |
| Dove andare in un brano / registrazione / cronologia | `FluidScrubber` (la posizione è di un lettore che avanza da solo) |
| Una cronologia a passi con un «adesso» | `FluidScrubber(step = 1f, marks = …)` |
| Un avanzamento che non si tocca | `FluidProgressBar` |
| Una scelta fra 3+ voci esclusive | `FluidSegmentedControl` ([controlli.md](controlli.md)) |
| Il vecchio nome dell'interruttore | non `FluidGlassSwitch` (deprecato, delega): `FluidSwitch` |

---

### 2. La goccia: pomelli che si afferrano

`FluidSwitch`, `FluidSlider`, `FluidScrubber` e la pillola di `FluidSegmentedControl` condividono la stessa
costruzione (il `glassDrop` di Kyant, `glass/interaction/GlassDrop.kt`): a riposo una pillola piena (bianca; nel
segmentato su scuro il grigio di iOS), sotto il dito la faccia sfuma e resta una **lente** che rifrange la pagina e
una *copia della pista che si dispiega*. L'accento sotto il pomello si apre davvero: è questo che fa «vetro», e
nessuna disposizione di una superficie generica lo produce. Il pomello si allunga con la velocità (con il *modulo*
della velocità: nella sorgente un verso accorciava la pillola, qui no; l'interruttore tiene il segno originale). Con
le animazioni ridotte il pomello non viaggia, si posa.

### 3. Un dito sul controllo è del controllo

Una pagina che scorre e un controllo che si trascina vogliono lo stesso dito, e vince chi legge per primo (il passo
principale va dal figlio al padre). Le regole, uguali in slider, interruttore e scrubber:

| dove cade il dito | di chi è |
|---|---|
| sul pomello (o a 10 dp dal bordo, 4 dp per l'interruttore) | del controllo, subito: ogni cambio di quel puntatore è consumato, su due assi, fino al rilascio |
| sulla pista nuda | indeciso: del controllo a **mezzo slop di lato** e più di lato che in verticale (`fluidSliderClaim`), della pagina se va prima in verticale oltre uno slop |
| sollevato senza essersi mosso | un tocco: porta lì il pomello (slider, scrubber) o commuta (interruttore) |

Conseguenza pratica: uno slider a tutta larghezza non diventa una fascia da cui non si scorre; e uno slider dentro un
`HorizontalPager` vince contro il pager sul movimento di lato partito dalla sua pista.

---

## FluidSwitch

**Cos'è.** L'interruttore: il **LiquidToggle di Kyant0** portato, non reinventato. Il pomello è l'unica lastra e ciò che
rifrange è una copia della pista schiacciata, che si apre sotto il dito. L'interruttore che c'era prima (pista piena con una
lente per pomello, costruita con `glassSurface`) aveva la costruzione sbagliata; il primo tentativo di una versione di vetro
faceva trasmettere la *pista*, sbagliato anche quello.

**Firma essenziale.**

```kotlin
FluidSwitch(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,    // null = disegna e anima soltanto
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
)
```

**Uso** (dal `sample`, `SwitchRow` in `ControlsTab`).

```kotlin
Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
  Text("Notifiche", modifier = Modifier.weight(1f))
  FluidSwitch(checked = checked, onCheckedChange = { checked = it })
}
```

**Comportamento.**

- **Misure** (da Kyant, tenute intere): pista 64 × 28 dp, pomello a pillola 40 × 24 dp, margine 2 dp, corsa 20 dp.
  `FluidSwitchWidth` vale `64.dp` per riservare lo spazio senza istanziare l'interruttore (era 56: chi scriveva 56 a mano è
  otto dp corto). Il colore è l'accento (`primary`), a pista accesa; da spento la pista è il grigio neutro della sorgente
  (`0x787880` al 36% sullo scuro, `0x787878` al 20% sul chiaro) — *non* `onSurface`, che su una palette Material You
  tingerebbe di accento la metà vuota, e «spento» è proprio lo stato che non è l'accento.
- **Un solo gesto su tutto il controllo** (2.13.1), letto nel passo `Initial` (dal padre al figlio):
  - un **tocco** (sollevamento entro uno slop) commuta, ovunque cada: sul pomello, sulla metà vuota della pista, nell'alone;
  - un dito sul **pomello** lo prende subito (margine 4 dp attorno): il pomello si gonfia (scala 1,5) e segue il dito senza zona
    morta dal primo pixel, e il gesto è suo, su due assi, fino al rilascio;
  - un dito sulla **pista vuota** che va di lato per mezzo slop (prima che in verticale) prende il pomello da dove sta; se va
    prima in verticale è la pagina che scorre.
- **Il rilascio decide**: il pomello va dalla parte più vicina (oltre metà), o da quella verso cui correva se la spinta era
  veloce (oltre 250 dp/s, `fluidSwitchLanding`).
- **Ottimista**: il gesto è l'affermazione, lo stato del chiamante è come la si registra. Il pomello si posa subito;
  se il chiamante **rifiuta** (lo stato non cambia) un effetto lo riporta dov'era: non resta a mentire.
- **Si sente**: `ToggleOn` / `ToggleOff` a ogni tocco che commuta; durante un trascinamento nel momento in cui il pomello passa la
  metà più un margine dell'8% (`fluidSwitchSide`), perché un dito fermo lì non ronzi; al rilascio non ripete, a meno che il
  pomello finisca dall'altra parte rispetto all'ultima vibrazione.
- **Pagina ferma durante il trascinamento**: ogni cambio del puntatore preso è consumato, quindi `LazyColumn`/`verticalScroll`/
  il back dal bordo non vedono un movimento da cui partire.
- Con `reducedMotion` il pomello si posa (`snapToValue`); lo squash del pomello (con segno, `/50`) **non** guarda le animazioni
  ridotte: eccezione nota, è il metro della ricetta.
- Semantica: `toggleable(role = Role.Switch)` solo se `enabled && onCheckedChange != null`; è lì per tastiera, fuoco e TalkBack,
  non per il puntatore (che è del gesto). Nessuna ripple: il pomello è il feedback.
- **Dentro `FluidToggleRow`** ([contenuto.md](contenuto.md)): il pomello **si trascina** anche lì (3.0.4). La riga lo disegna con
  il controllo interno `FluidSwitchControl(ownsSemantics = false)`: il gesto (tocco e trascinamento) resta tutto dell'interruttore,
  che prende il contatto nell'`Initial` e così la riga non clicca sotto di lui; il nodo per TalkBack, la tastiera e il fuoco
  sono della riga, che per chi non vede *è* l'interruttore e ha il bersaglio da 48 dp; un tocco altrove sulla riga commuta come
  prima; una vibrazione per cambio (chi commuta la suona, i due gesti non si sovrappongono). Disattivata, la riga passa
  `onCheckedChange = null` e il pomello non risponde.

**Trappole.**

- **`FluidSwitch` da solo non ha un nome per TalkBack**: il nodo è `Role.Switch` con lo stato, ma il `Text` accanto non è un
  suo discendente e non si fonde. Per un interruttore con un'etichetta, in una pagina di impostazioni, `FluidToggleRow`; fuori
  da lì dai tu un nome al contenitore (`Modifier.semantics(mergeDescendants = true)` sulla riga con `clickable` che commuta, o
  un `contentDescription`).
- `onCheckedChange = null` è un disegno: nessun gesto, **nessun nodo semantico**. Fino alla 3.0.3 era così che la riga lo
  pilotava (e nessun interruttore in riga si poteva trascinare). Oggi serve a chi vuole un interruttore a sola vista.
- La riga che contiene l'interruttore **non deve avere un tocco suo** se non commuta: il contatto è già consumato.
- Una disabilitazione sfuma a 0,5, non al quarto: dentro una riga disattivata l'interruttore non sfuma da solo (a sfumare è la
  riga).

**Non usarlo quando.** Una scelta fra più di due stati (`FluidSegmentedControl`, `FluidChoiceRow`).

**Versione.** Il LiquidToggle di Kyant0 *nel sorgente*: 2.2.0 (BREAKING: il CHANGELOG della 2.0.0 lo dichiarava già, ma era rimasto il
56×31 col pomello tondo); 56 → 64 dp con lei (`FluidSwitchWidth`); la pagina non scorre mentre si trascina il pomello: 2.12.0;
trascinamento da qualunque punto e vibrazione: 2.13.1; si trascina dentro `FluidToggleRow`: 3.0.4. `FluidGlassSwitch` (nato in 2.1.0,
deprecato in 2.2.0) era il nome di questo controllo quando stava accanto al vecchio.

---

## FluidSlider

**Cos'è.** Il cursore: il **LiquidSlider di Kyant0** portato, arrivato in engine passando dal port che Pampa notes aveva già
fatto. Una pista sottile (6 dp) riempita con l'accento e una pillola che a riposo è bianca e opaca e si fa lente mentre il dito
la tiene. Quello di prima era una pista da 30 dp con una lente da 26 sempre accesa: la stessa costruzione già scartata per
l'interruttore.

**Firma essenziale.**

```kotlin
FluidSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  enabled: Boolean = true,
  onValueChangeFinished: (() -> Unit)? = null,   // il momento di salvare
  snapValues: List<Float> = emptyList(),          // tacche
  snapThreshold: Float = 0.03f,                   // frazione dell'intervallo, mai unità del valore
  contentDescription: String? = null,
  stateDescription: String? = null,               // «30 minuti»
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
)
```

**Uso** (dal `sample`, `CapsulesAndPartsDemo` e `PagesDemo`).

```kotlin
var minutes by remember { mutableFloatStateOf(30f) }
FluidSlider(
  value = minutes,
  onValueChange = { minutes = it },
  valueRange = 5f..120f,
  snapValues = listOf(5f, 15f, 30f, 60f, 120f),
  onValueChangeFinished = { save(minutes) },
  contentDescription = "Timer",
  stateDescription = "${minutes.toInt()} minuti",
)
```

**Comportamento.**

- **Costruzione**: la pista è una *registrazione* (si dipinge in un layer suo, quello che la maniglia legge), la maniglia è
  l'unica lastra: pillola bianca con ombra a riposo, tenuta svanisce il bianco e resta una lente (10/14 dp) che rifrange la
  pagina *più una copia della pista che si dispiega* (schiacciata a due terzi in orizzontale e a niente in verticale a riposo,
  aperta del tutto sotto il dito). La maniglia cresce di 1,5 sotto il dito e si allunga con il modulo della velocità.
- **Geometria** (privata, ma utile a chi dimensiona): riga alta 48 dp; pista 6 dp; maniglia 40 × 24 dp; margine laterale di
  un quarto di maniglia (10 dp) a ogni lato, dove la sorgente la lasciava sporgere: qui sta *dentro* i bordi, perché nulla
  nell'engine disegna fuori dai propri limiti e una maniglia che sporge viene mozzata dal primo contenitore che ritaglia. Il
  centro della maniglia non arriva mai più vicino al bordo di un quarto di sé, ma il *valore* sì (come nella sorgente).
- **La riga è tutta del dito**: un tocco sulla pista porta lì la maniglia (con la molla); un trascinamento partito da lei la
  prende **senza spostarla** (relativo: afferrarla non cambia il valore, e un dito che sborda oltre l'estremità e torna non la
  muove finché non ripassa dal punto da cui è uscito). La maniglia si afferra anche a 10 dp dal bordo.
- **Il dito è dello slider** (2.11.0): vedi la regola 3. Sollevato senza essersi mosso, sulla pista, è un tocco.
- **Tacche** (`snapValues`): entro `snapThreshold` (frazione dell'intervallo) dalla tacca più vicina il valore *è* esattamente
  quella e la maniglia ci resta; fuori è libero. A pari distanza vince la prima della lista; tacche fuori intervallo si
  appoggiano al bordo. Sotto la pista, un puntino da 2 dp per tacca (a 7 dp sotto il bordo): accento se la maniglia l'ha
  raggiunto, neutro altrimenti. `snapThreshold = 1f` è uno slider a **gradini** (ogni posizione cade sulla tacca più vicina).
- **Aptica**: con le tacche, un solo `Tick` quando il valore *si posa* su una (non finché ci sta; afferrare la maniglia ferma su una
  tacca non suona); senza tacche un `DragTick` ogni ventesimo dell'intervallo; alle estremità sempre un `Threshold`, e se
  l'estremità è anche una tacca non suona due volte. Tutto passa da `FluidHapticDetents` con la rete dei 55 ms: a un dito veloce
  toccano più gradini di quanti un motore ne sappia rispondere, e chiederli tutti dà un ronzio.
- **Il valore è del chiamante**: la maniglia segue sempre `value` (dito, tocco, ripristino di una preferenza). Un chiamante che
  arrotonda, rifiuta o salva in ritardo vede la maniglia dove dice il suo stato. `onValueChange` si chiama solo quando il valore
  cambia davvero. Con il movimento ridotto la maniglia non viaggia, si posa.
- **`onValueChangeFinished`** alla fine di un trascinamento o di un tocco sulla pista, e anche per TalkBack (`setProgress`):
  il momento di scrivere sul disco.
- **Semantica**: `progressBarRangeInfo` (valore dentro l'intervallo, senza passi) e `setProgress` che si attacca alle tacche.
  Senza `contentDescription` il nodo non ha nome; senza `stateDescription` TalkBack legge una percentuale della pista, che di
  un elenco di scatti non dice niente.
- **Dentro `FluidSliderRow`** ([contenuto.md](contenuto.md)): il titolo è il `contentDescription`, il `valueLabel` è lo
  `stateDescription`; la riga di testo sopra è azzerata per TalkBack (detta dallo slider), e la riga non ha un tocco suo: il
  gesto è tutto dello slider.
- Da destra a sinistra la pista e la maniglia si specchiano (la x si specchia una volta, nel gesto).

**Trappole.**

- `value` fuori da `valueRange` è portato dentro; un intervallo vuoto o al contrario non divide per zero (la maniglia sta a
  zero): è un errore di chi chiama, ma non rompe.
- Una chiave di `pointerInput` che cambiasse a metà gesto (la larghezza, se la riga si ridimensiona mentre la si trascina)
  azzererebbe il gesto: per questo il gesto non ha chiavi e legge tutto a ogni evento (`rememberUpdatedState`). Se aggiungi
  un parametro, leggilo come gli altri.
- `snapThreshold` è una **frazione**: 0,03 su `0f..19f` vale 0,57; su `0f..1f` 0,03. Un valore in unità del valore qui non
  esiste, apposta.
- Un chiamante che non aggiorna `value` in `onValueChange` vede la maniglia ferma (non c'è stato interno): non è un difetto,
  è la regola «il valore è tuo».
- Uno slider dentro un `HorizontalPager` vince sul movimento di lato partito dalla sua pista; se non lo vuoi, non metterlo a tutta
  larghezza in un pager.

**Non usarlo quando.** La posizione è di un lettore che avanza da solo e risponde ai salti in ritardo (`FluidScrubber`: lo
slider combatterebbe col dito); non è un valore modificabile ma un avanzamento (`FluidProgressBar`).

**Versione.** Dalla 2.12.0 è il LiquidSlider di Kyant0 (file `FluidSlider.kt`) con `snapValues`, `snapThreshold`,
`contentDescription`, `stateDescription`, `backdrop`; un trascinamento non fa scorrere la pagina dalla 2.11.0; l'aspetto è di
Kyant, il gesto è nostro.

---

## FluidScrubber (con FluidScrubbing e FluidScrubberDefaults)

**Cos'è.** La barra con cui si sceglie dove andare in qualcosa che dura: un brano, una registrazione, la cronologia di un radar.
Della famiglia della goccia: la maniglia è quella di `FluidSlider`. Quello che lo rende un'altra cosa è che **la posizione non
è sua**: la possiede un lettore, che la fa avanzare da solo e risponde ai salti con ritardo. Un cursore che si limita a
seguire il valore che gli arriva combatte col dito (a ogni battito la maniglia torna dove il lettore è ancora) e, a dito alzato,
lampeggia indietro finché il salto non è arrivato. Tre app (Fluidify, Pampa notes, il radar di FluidWeather) se l'erano scritto a mano.

**Firma essenziale** (la lambda e l'overload `Float`: stessi parametri).

```kotlin
FluidScrubber(
  position: () -> Float,                       // dov'è la riproduzione, nell'unità di valueRange
  onSeek: (Float) -> Unit,                      // il salto: UNA volta
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  enabled: Boolean = true,
  buffered: (() -> Float)? = null,              // fin dove è pronto
  step: Float = 0f,                             // 0 = continuo
  marks: List<Float> = emptyList(),             // punti di riferimento (capitoli, «adesso»)
  onScrubStart: (() -> Unit)? = null,
  onScrub: ((Float) -> Unit)? = null,           // valore sotto il dito, per un'anteprima viva
  onScrubEnd: (() -> Unit)? = null,
  startLabel: FluidScrubberFormat? = null,
  endLabel: FluidScrubberFormat? = null,
  contentDescription: String = FluidScrubberDefaults.ContentDescription,
  valueDescription: FluidScrubberFormat = FluidScrubbing.Spoken,
  keyStep: Float = 0f,
  stepBackLabel: String = FluidScrubberDefaults.StepBackLabel,
  stepForwardLabel: String = FluidScrubberDefaults.StepForwardLabel,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
  onStartLabelClick: (() -> Unit)? = null,
  onEndLabelClick: (() -> Unit)? = null,
)
```

`FluidScrubberFormat` è `(position: Float, range: ClosedFloatingPointRange<Float>) -> String`, nell'unità dell'app (millisecondi,
secondi, indice di fotogramma).

**Uso: un lettore di brani** (dal `sample`, `MoreComponentsDemo`).

```kotlin
FluidScrubber(
  position = { player.positionMs },                 // lambda: letta nel disegno, non ricompone a ogni battito
  onSeek = { target -> player.seekTo(target.toLong()) },
  valueRange = 0f..player.durationMs,
  buffered = { player.bufferedMs },
  onScrubStart = { /* es. mettere in pausa */ },
  onScrub = { preview.show(it) },                   // anteprima viva mentre il dito scorre
  onScrubEnd = { /* es. riprendere */ },
  startLabel = FluidScrubbing.Elapsed,              // «1:05»
  endLabel = if (showRemaining) FluidScrubbing.Remaining else FluidScrubbing.Total,
  onEndLabelClick = { showRemaining = !showRemaining },
  keyStep = 10_000f,                                // dieci secondi per tasto / azione TalkBack
)
```

**Uso: una cronologia a passi** (il radar: 13 fotogrammi, «adesso» segnato).

```kotlin
FluidScrubber(
  position = frame.toFloat(),                       // overload a Float: la posizione cambia di rado
  onSeek = { frame = it.roundToInt() },
  valueRange = 0f..12f,
  step = 1f,
  marks = listOf(nowFrame.toFloat()),
  onScrubStart = { playing = false },
  onScrub = { frame = it.roundToInt() },            // un fotogramma per passo
  endLabel = { shown, _ -> frameLabel(shown.roundToInt()) },
  valueDescription = { shown, _ -> frameLabel(shown.roundToInt()) },   // non «1:05 di 3:20»!
  contentDescription = "Orario del fotogramma",
)
```

**Comportamento — la regola dei tre tempi** (`FluidScrubbing.shownValue`; tutto il resto ne discende):

| tempo | cosa mostra la maniglia | la `position` che arriva da fuori |
|---|---|---|
| **a riposo** | la `position`, con la molla della goccia (un battito a 4 al secondo diventa moto continuo) | segue |
| **sotto il dito** | solo il dito, 1:1 (relativo come lo slider; continuo: la maniglia sta *sul* dito senza la molla, che a un dito veloce resta indietro) | **ignorata del tutto**: il lettore che avanza non sposta niente |
| **dopo il rilascio** | il punto lasciato, finché la `position` non lo ha raggiunto o passano `FluidMotion.ScrubberHoldMillis` (3 s) | ignorata fino a lì. Niente ritorno, niente lampo; un seek rifiutato non tiene la maniglia dove nessuno riproduce |

«Raggiunto» (`caughtUp`) è relativo alla *distanza del salto*, non all'intervallo: la posizione ha raggiunto il punto quando
è a meno di metà strada **dal punto** (a passi: entro mezzo passo). Così un colpetto di un minuto su una registrazione di
diciannove ore (lo 0,09% dell'intervallo) non è mai «raggiunto» prima che il lettore si muova, e un seek che si appoggia a un
fotogramma chiave vicino ma non uguale lo è comunque.

Se un nuovo salto sostituisce quello ancora in attesa, la tolleranza è limitata anche dalla distanza
dal punto richiesto precedente: la sua conferma non libera il nuovo punto. Vale nei due versi e nei
cambi di direzione, senza allargare la tolleranza di un salto singolo. Il timeout resta di tre secondi.

**Le chiamate, nell'ordine di un trascinamento**: `onScrubStart` (il dito prende la maniglia; non per un tocco) → `onScrub` a ogni
valore *nuovo* sotto il dito (a ogni passo, se a passi) → `onSeek` **una volta sola**, al rilascio, col valore finale → `onScrubEnd`
subito dopo, o da sola se la coroutine del gesto è stata cancellata (allora niente salto e la maniglia torna alla posizione del
lettore: il lettore può riprendere). Compose recapita un `ACTION_CANCEL` di sistema come un sollevamento, e lo scrubber lo tratta
così, saltando dov'era il dito. Un **tocco sulla pista** è un salto: la maniglia ci viaggia con la molla dello slider e c'è solo
`onSeek`. Lo stesso per tastiera e TalkBack.

**Continuo o a passi.**

- `step = 0`: ogni punto è un valore, **niente aptica durante il moto** (un brano non ha tacche, e un tick ogni tanto sotto il dito
  sarebbe un ronzio).
- `step > 0`: il valore cade sul multiplo più vicino a partire dall'*inizio* dell'intervallo (a metà vince quello avanti); se
  l'intervallo non è un multiplo anche l'estremità è un valore (si arriva a fine brano). Un puntino per passo sotto la pista, fino
  a `FluidScrubberDefaults.MaxStepDots` (32): oltre sarebbe un tratteggio che sotto un polpastrello non si conta, e il passo c'è
  lo stesso. Un `Tick` per passo attraversato, con la rete dei 55 ms.
- Arrivare a un'estremità suona sempre un `Threshold` (arrivarci, non starci; nessuna rete). Un tocco su una pista continua suona un
  `Tap` e, se è un'estremità, solo il `Threshold`: un momento, un colpo. A passi un tocco suona un `Tick` solo se il passo cambia.
- `marks`: punti da 5 dp (più grandi dei passi) sulla stessa riga dei puntini, accento se la maniglia li ha passati.

**Etichette.** `startLabel` / `endLabel` stanno sotto la pista, calcolate dal valore *mostrato* (sotto il dito è il suo, non quello del
lettore), `labelMedium` a cifre tabulari in `onSurfaceVariant`, tirate su verso la pista di 8 dp (la riga è alta 48 dp per il dito).
Si ricompone solo la riga quando cambia la *stringa* (una volta al secondo per un orologio). Con tutti e due nulli lo scrubber è la
sola riga da 48 dp. `onStartLabelClick` / `onEndLabelClick` (3.0.1) rendono toccabile la scritta (il tocco sulla durata che diventa il
tempo che manca e torna indietro): lo scrubber non sa cosa scambiare, l'app cambia il formato che passa. La scritta diventa un
pulsante per TalkBack con il suo testo; senza, resta muta (il valore è già nel nodo dello scrubber).

**Pista.** 6 dp: velo `onSurface` al 12% (chiaro) / 17% (scuro) — gli stessi alpha dello slider; il *pronto* (`buffered`) è
`onSurface` al 26% / 36%, a metà fra il velo e l'accento; l'accento fino al punto mostrato. Il riempimento è per *larghezza*, non per
scala (una scala schiaccerebbe la testa tonda).

**Accessibilità.** Per TalkBack è uno slider, un nodo solo: `contentDescription`, `stateDescription = valueDescription(...)` (di serie «1:05
di 3:20»), `progressBarRangeInfo` con i passi *interni* se a passi, `setProgress`; più due azioni custom, `stepBackLabel` /
`stepForwardLabel`, che muovono di `keyStep` senza indovinare una percentuale su una registrazione di ore. Da tastiera e D-pad:
sinistra/destra (specchiate da destra a sinistra) di `keyStep`, pagina su/giù di un decimo (o `keyStep` se più grande), Home/End;
**su e giù non si prendono** (sono del fuoco: uno scrubber che non lascia uscire un D-pad in verticale è una trappola). `keyStep = 0`
vuol dire: un passo se è a passi, il 5% dell'intervallo se è continuo. Il fuoco è un anello d'accento da 2 dp *dentro* il bordo della
pillola (fuori lo mozzerebbe il primo antenato che ritaglia); al tocco il nodo non prende il fuoco. Disabilitato: nodo `disabled`,
opacità 0,5, nessun gesto.

**FluidScrubbing** (pubblico per i formati): `clock(millis)` scrive «3:20», «59:59», «1:02:03» (per difetto al secondo, mai sotto
zero); `Elapsed` «1:05» (conta dall'*inizio* dell'intervallo, non da zero); `Remaining` «−2:15» (con il meno tipografico `−`); `Total`
«3:20»; `Spoken` «1:05 di 3:20». Sono **per i millisecondi**: per un'altra unità si passa un formato proprio. Un'app che mostra il
tempo da sé accanto alla barra usa gli stessi.

**Trappole.**

- **`valueDescription` per un'altra unità va passato**: di serie dice «1:05 di 3:20» come se il valore fossero millisecondi, e la
  cronologia di un radar direbbe un orario che non è.
- **Parole in italiano scritte nei default**: `FluidScrubberDefaults.ContentDescription` («Posizione»), `StepBackLabel` («Indietro»),
  `StepForwardLabel` («Avanti») non passano da `FluidLabels` né da `FluidLabels.english()`. Un'app in un'altra lingua li passa
  *sempre*, o TalkBack dice una frase italiana.
- **Presa e rilascio senza muoversi chiamano comunque `onSeek`** (col valore mostrato in quel momento, cioè la posizione corrente):
  il codice fa `currentOnSeek(value)` a ogni sollevamento che conclude un gesto della maniglia. Un lettore che reagisce al seek
  con un riavvio vedrà un salto a se stesso.
- `position` è una **lambda** apposta: a quattro battiti al secondo (o trenta) un parametro `Float` ricomporrebbe il chiamante a
  ogni battito, mentre letta nel disegno invalida solo il disegno. Passa `{ player.positionMs }` (uno stato letto *dentro* la
  lambda), non un valore letto prima in composizione. L'overload a `Float` è per chi la cambia di rado.
- `valueRange` vuoto, al contrario o con un NaN è un intervallo di un punto (un brano di durata ancora ignota non divide per zero):
  quando la durata arriva, il `valueRange` nuovo basta.
- Il salto da tastiera e da TalkBack non fa aptica (`travel = false`): niente vibrazione per un'azione che il dito non ha fatto.
- Il contatto sul pomello è consumato: uno scrubber in un pager o in una lista che scorre non li fa scorrere dal pomello.
- Non provati (documentati in `docs/10-coerenza.md`): il vetro guardato in mano, il passaggio dal dito alla molla senza lampo, le
  animazioni ridotte, il ripiego sotto API 31, testo a 1,3×, il costo con la posizione a 30 Hz.

**Non usarlo quando.** Una barra che si limita a mostrare un avanzamento (`FluidProgressBar`); un valore modificabile senza un lettore
(`FluidSlider`); la forma ripiegata a filo (non c'è: vorrebbe alfa o scala sopra un vetro).

**Versione.** Dalla 3.0 (`FluidScrubber`, `FluidScrubbing`, `FluidScrubberDefaults`; token `FluidMotion.ScrubberHoldMillis`); scritte
toccabili e `onStartLabelClick` / `onEndLabelClick`: 3.0.1.

---

## Licenze dei port: cosa viene da dove e cosa è cambiato

Due licenze, non una, e la differenza conta a chi distribuisce l'app.

| pezzo | origine | licenza |
|---|---|---|
| `FluidSwitch` (pomello, copia schiacciata della pista, molle, stiramento) | `LiquidToggle` del catalogo di **Kyant0** (`AndroidLiquidGlass`, commit `b18eb0f…`) | Apache-2.0, Copyright 2025 Kyant |
| `FluidSlider` (pillola bianca/lente, pista registrata, copia che si dispiega) | `LiquidSlider` del catalogo di Kyant0, arrivato passando dal port già fatto in **Pampa notes** | Apache-2.0, Copyright 2025 Kyant |
| `FluidScrubber`, la goccia di `FluidSegmentedControl` | `glassDrop` (`glass/interaction/GlassDrop.kt`, la ricetta estratta dai due controlli) e la copia della pista dello slider | Apache-2.0, Kyant |
| tasti: `FluidButton`, `FluidGlassButton`, `FluidGlassIconButton`, `FluidBarAction`, `FluidChip`, `FluidColorDot` (pressione, gesto, aptica, bagliore) | **LibrePods** (`StyledButton`, `StyledIconButton`, `DragUtils`; ramo `android/rewrite`, `3d4d270`) | **GPL-3.0** |

**Conseguenza da dire ad alta voce**: dalla 3.0 `engine-ui` contiene codice di LibrePods, e **un'app che include l'engine è un'opera
derivata, da distribuire sotto GPL-3.0** (sorgenti a chi riceve l'APK). È una scelta di Alessio (31 ottobre 2026): i tasti si comportano
*esattamente* come quelli di LibrePods perché portano lo stesso codice. Fino alla 2.13 era un comportamento guardato e riscritto, senza copiare
codice. Il nome e il logo di LibrePods non si usano fuori da questa attribuzione.

**Cosa è cambiato rispetto all'originale di Kyant0** (dichiarato in testa a `FluidSwitch.kt` e `FluidSlider.kt`, in `LICENSES/AndroidLiquidGlass.md`,
punto 11 e seguenti):

- il colore è l'**accento dell'app** al posto del verde iOS fisso (interruttore) e, per lo slider, il velo della pista preso da `onSurface` con alpha
  ricalcolati perché cada dove cade il grigio della sorgente (12% chiaro / 17% scuro);
- aggiunti `enabled`, il tocco aptico, la semantica per TalkBack, il movimento ridotto e un ripiego senza `RenderEffect`: li hanno tutti gli altri controlli;
- **interruttore**: un tocco *ovunque* lo commuta (la sorgente rispondeva solo al pomello); dalla 2.13.1 un gesto suo su tutto il controllo al posto del
  `DragGestureInspector` di Kyant (una spinta veloce conta; vibra alla metà); consuma il puntatore del pomello trascinato; si trascina anche dentro una riga;
- **slider**: lo stiramento con la velocità usa il *modulo* e non il segno (nella sorgente un verso allunga e l'altro accorcia: uno slider che si rimpicciolisce a
  sinistra è un difetto); pista e maniglia stanno *dentro* i bordi (un quarto di maniglia di margine invece di sporgere); il gesto sta su tutta la riga; **le tacche di
  scatto sono un'aggiunta nostra**; consuma il puntatore della maniglia;
- le molle (`GlassDragAnimation`) e i numeri della ricetta (lente 10/14 e 5/10 dp, sfocatura 8 dp, `Ambient` ÷ 1,5, ombra interna 4 dp, ombra fissa 4 dp al 5%)
  restano quelli della sorgente: è «il metro».

L'app deve riportare *AndroidLiquidGlass — Apache-2.0 — Copyright 2025 Kyant* e *LibrePods — GPL-3.0* fra le licenze di terze parti: l'engine ha già
`fluidLicensesSection` (una sezione di `LazyListScope` che elenca da sola le voci di `FluidEngineLicenses`). Testi: `LICENSES/AndroidLiquidGlass.md`,
`LICENSES/LibrePods.md`.

---

---

## Token, costanti, Defaults

**`FluidScrubberDefaults`**: `ContentDescription` = «Posizione», `StepBackLabel` = «Indietro», `StepForwardLabel` = «Avanti», `MaxStepDots` = 32.
**`FluidScrubbing`**: `clock`, `Elapsed`, `Remaining`, `Total`, `Spoken` (pubblici); il resto (`snap`, `nudge`, `caughtUp`, `shownValue`…) è `internal` e provato.
**`FluidSwitchWidth`** = `64.dp`. **`FluidMotion.ScrubberHoldMillis`** = 3000 ms (token di attesa dello scrubber). **Pomelli**: `GlassDropLens.Slider` (10/14 dp), `GlassDropLens.Switch` (5/10 dp).

Costanti non pubbliche che conviene sapere (per dimensionare attorno):

| costante | valore | dove |
|---|---|---|
| pista dello slider / scrubber | 6 dp | `FluidSlider.kt`, `FluidScrubber.kt` |
| maniglia | 40 × 24 dp | idem |
| margine laterale di slider / scrubber | 10 dp (un quarto di maniglia) | idem |
| altezza della riga di slider / scrubber | 48 dp | idem |
| presa della maniglia di slider / scrubber | maniglia/2 + 10 dp | idem |
| tacca (puntino) / punto di riferimento | 2 dp / 5 dp, a 7 dp dal bordo della pista | idem |
| tacche aptiche senza `snapValues` | 20 passi | `FluidSlider.kt` |
| rete aptica fra due tacche | 55 ms (`FluidHapticDetents.DETENT_MIN_GAP_MILLIS`) | `FluidHapticDetents.kt` |
| soglia per prendere un gesto dalla pista nuda | mezzo slop di lato (`SliderClaimSlopFraction`) | `FluidSlider.kt` |
| pista dell'interruttore / pomello / corsa | 64 × 28 / 40 × 24 / 20 dp | `FluidSwitch.kt` |
| presa del pomello dell'interruttore | 4 dp oltre il bordo | `FluidSwitch.kt` |
| spinta che decide l'interruttore | 250 dp/s (`FlingVelocity`) | `FluidSwitch.kt` |
| isteresi del segno dell'interruttore | 8% attorno alla metà (`FluidSwitchSideHysteresis`) | `FluidSwitch.kt` |
| pista: velo di `onSurface` (chiaro / scuro) | 12% / 17% | `FluidSlider.kt`, `FluidScrubber.kt` |
| pronto dello scrubber (chiaro / scuro) | 26% / 36% | `FluidScrubber.kt` |

## Cosa è verificato dai test

- **`FluidSliderSnapTest`, `FluidSliderClaimTest`** (`engine-ui`): tacche (entro la soglia, la soglia è una frazione, `1f` è un passo, intervallo vuoto, tacca che suona solo all'atterraggio, rete dei 55 ms, afferrare fermo non suona); il test dei mezzi slop; l'isteresi dell'interruttore e dove si posa il pomello lasciato.
- **`FluidGlassControlsTest`** (`engine-ui`): la riga dello slider e la posizione del pomello (matematica pura: valore ↔ pista, il pomello mai oltre un quarto di sé dal bordo, un intervallo vuoto o una larghezza zero non divide per zero).
- **`FluidScrubberTest`** (`engine-ui`, 38): frazione ↔ posizione, passi, `keyStep`/pagina, formati dell'orologio, *quale valore si mostra* nei tre tempi, quando il lettore ha raggiunto il punto, aptica (estremità, passi, tocco, rete), i due token di attesa.
- **`FluidTouchTest`** (`sample`, Robolectric): slider e interruttore in `LazyColumn`/`verticalScroll` non fanno scorrere la pagina con una deriva diagonale/verticale; la passata verticale partita sulla pista scorre ancora; pomello preso dal bordo senza saltare; tacche che attaccano e suonano una volta; l'interruttore si trascina dalla pista, si commuta con una spinta veloce, vibra a tocco e a metà, funziona in una `FluidScreen` e in una riga cliccabile.
- **`MoreComponentsTest`** (`sample`, 16 sullo scrubber): maniglia sotto il dito dal prendere al lasciare e un solo `onSeek`, a passi un tick per passo, un nodo per TalkBack con intervallo/voce/due azioni, passi interni, spento, tastiera.
- **Non provato dai test**: il vetro dello scrubber guardato in mano, il passaggio dal dito alla molla senza lampo, le animazioni ridotte, il ripiego sotto API 31, testo a 1,3×, il costo con la posizione a 30 Hz (`docs/10-coerenza.md`).

## Dove guardare

Radice: `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/`.

| file | cosa c'è dentro |
|---|---|
| `FluidSwitch.kt` | `FluidSwitch`, `FluidSwitchControl` (interno: `ownsSemantics`), `FluidGlassSwitch` (deprecato), `FluidSwitchWidth`, `fluidSwitchSide`, `fluidSwitchLanding`, `fluidSwitchHaptic` |
| `FluidSlider.kt` | `FluidSlider` e tutta la matematica interna (`fluidSliderFraction`, `fluidSliderValue`, `fluidSliderThumbCentre`, `fluidSliderStopIndex`, `fluidSliderSnap`, `fluidSliderClaim`, `FluidSliderNotchGate`) |
| `FluidScrubber.kt` | `FluidScrubber` (due overload), `FluidScrubberFormat`, `FluidScrubberDefaults`, `FluidScrubbing` (formati pubblici; conti interni), `FluidScrubberHold` e `FluidScrubberPhase` (i tre tempi) |
| `FluidSettingRows.kt` | `FluidToggleRow` e `FluidSliderRow` — vedi [contenuto.md](contenuto.md) |
| `glass/interaction/GlassDrop.kt` | `glassDrop`, `GlassDropLens`, `GlassDropRecipe`, `GlassDropFaces` |
| `LICENSES/AndroidLiquidGlass.md`, `LICENSES/LibrePods.md` | le attribuzioni e l'elenco delle modifiche |
| `docs/10-coerenza.md` | la matrice dei comportamenti (D1–D9): sezioni «La goccia» e «Gocce» |

Tasti, scelte, campo di testo: [controlli.md](controlli.md).
