<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/controlli-gesto.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Controlli a gesto: interruttore, cursore, scrubber, ruota, ghiera

Usa `FluidSwitch` per un valore acceso o spento, `FluidSlider` per un valore che l'app può cambiare mentre lo
trascini e `FluidScrubber` per raggiungere un punto in qualcosa che avanza da sé. Il pomello è pieno a riposo
e si fa lente sotto il dito. La mano può continuare il gesto in diagonale senza far scorrere la pagina.
Dalla 4.0 (F8.2) ci sono anche le due maniglie di `FluidRangeSlider`, la ruota di iOS (`FluidWheelPicker`, con le forme
pronte `FluidDurationWheel` e `FluidTimeWheel`) e il righello di «Raddrizza» (`FluidRulerDial`).

Le regole comuni di fondale, bersaglio e disattivazione sono nella [scheda dei controlli](controlli.md).
Le righe con titolo e valore sono in [contenuto.md](contenuto.md). Le firme complete sono nel
[catalogo API](../api/controlli.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| Acceso / spento | `FluidSwitch`; in una pagina di impostazioni la riga è `FluidToggleRow` ([contenuto.md](contenuto.md)) |
| Un valore in un intervallo (volume, soglia) | `FluidSlider` |
| Una scelta fra pochi scatti su una pista | `FluidSlider(snapValues = …)`; con `snapThreshold = 1f` è a gradini |
| Un cursore con titolo e valore in parole | `FluidSliderRow` ([contenuto.md](contenuto.md)) |
| Dove andare in un brano / registrazione / cronologia | `FluidScrubber` (la posizione è di un lettore che avanza da solo) |
| Una cronologia a passi con un «adesso» | `FluidScrubber(step = 1f, marks = …)` |
| Un audio da scorrere col dito, con l'onda (il messaggio audio di Messaggi) | `FluidAudioWaveform` (F8.13) |
| L'onda che scorre sotto la testina, col pizzico per lo zoom (Memo Vocali) | `FluidAudioTimeline` (F8.13) |
| Il registratore di Memo Vocali (il tasto rosso che diventa il pannello) | `FluidVoiceRecorder`, con `FluidAudioRecorder` di [engine-audio](../moduli/audio.md) |
| Un avanzamento che non si tocca | `FluidProgressBar` |
| Un minimo e un massimo (un filtro per durata, per peso) | `FluidRangeSlider` |
| Una voce fra tante, che gira col dito (la ruota di iOS) | `FluidWheelPicker` (una o più colonne) |
| Una durata come il timer di Orologio | `FluidDurationWheel` |
| Un'ora a ruota, come `UIDatePicker` | `FluidTimeWheel` |
| Un valore fine senza inizio e fine in vista (gradi, zoom, velocità, dB) | `FluidRulerDial` |
| Una scelta fra 3+ voci esclusive | `FluidSegmentedControl` ([controlli.md](controlli.md)) |
| Il vecchio nome dell'interruttore | non `FluidGlassSwitch` (deprecato, delega): `FluidSwitch` |

---

## Dal pomello alla lente

`glassDrop` disegna la faccia piena e la lente che rifrange il canvas e una copia della pista. La copia si apre
sotto il dito, così il riempimento della pista rimane visibile nel vetro. Con Liquid il profilo della lente è
limpido, ha un leggero tono scuro e la rifrazione è più forte al bordo. Con Classic o su API 31–32 si usa la
lente di Kyant. Sotto API 31 resta una pillola opaca con ombra su pista piena.

| Fase | Interruttore e cursore |
|---|---|
| Presa | Il bianco sfuma mentre la lente cresce a ×1,5. La molla critica `FluidMotion.DropPress` compie il 92% della corsa a 133 ms, senza superamento. |
| Trascinamento | La lente si allunga quando accelera nel verso del valore e si stringe quando frena o parte all'indietro. Lo stiramento si esaurisce a velocità costante. Il cursore ammette una variazione più ampia dell'interruttore. |
| Tenuta | L'alone riprende il colore della pista sotto la lente. |
| Rilascio | La stessa molla richiude la lente nel pomello senza sottopasso. |

Con movimento ridotto il cambio di valore posa il pomello direttamente. La lente conserva una crescita fino a
×1,25 e la deformazione direzionale viene soppressa. Scrubber, segmentato, rail e tab bar usano la loro goccia
con scala a molla e deformazione guidata dalla velocità.

## Presa e scorrimento della pagina

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

L'app passa `checked` e registra la commutazione in `onCheckedChange`. Nel controllo autonomo il pomello
possiede il gesto e il nodo semantico. In `FluidToggleRow` il gesto sul pomello resta identico e la riga
fornisce nome, stato e bersaglio per tastiera e TalkBack.

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

**Uso in una riga con nome.**

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.antigravity.fluidengine.ui.fluid.FluidToggleRow

@Composable
fun ImpostazioneNotifiche() {
  var enabled by remember { mutableStateOf(true) }
  FluidToggleRow(title = "Notifiche", checked = enabled, onCheckedChange = { enabled = it })
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
  - un dito sul **pomello** lo prende subito (margine 4 dp attorno): il pomello sale nel vetro e segue il
    dito dal primo pixel, con presa su due assi fino al rilascio;
  - un dito sulla **pista vuota** che va di lato per mezzo slop (prima che in verticale) prende il pomello da dove sta; se va
    prima in verticale è la pagina che scorre.
- **Il rilascio decide**: il pomello va dalla parte più vicina (oltre metà), o da quella verso cui correva se la spinta era
  veloce (oltre 250 dp/s, `fluidSwitchLanding`).
- **Ottimista**: il gesto è l'affermazione, lo stato del chiamante è come la si registra. `onCheckedChange` arriva subito;
  se il chiamante **rifiuta** (lo stato non cambia) un effetto lo riporta dov'era: non resta a mentire.
- **Tocco breve.** Il valore si aggiorna al tocco. La lente si forma sul posto, parte dopo 100 ms e inizia a
  richiudersi 60 ms dopo l'arrivo. Un nuovo dito mentre si forma anticipa la partenza. Il colore della pista segue
  il pomello con una molla critica e circa 100 ms di ritardo.
- **Si sente**: `ToggleOn` / `ToggleOff` a ogni tocco che commuta; durante un trascinamento nel momento in cui il pomello passa la
  metà più un margine dell'8% (`fluidSwitchSide`), perché un dito fermo lì non ronzi; al rilascio non ripete, a meno che il
  pomello finisca dall'altra parte rispetto all'ultima vibrazione.
- **Pagina ferma durante il trascinamento**: ogni cambio del puntatore preso è consumato, quindi `LazyColumn`/`verticalScroll`/
  il back dal bordo non vedono un movimento da cui partire.
- Con movimento ridotto la pista cambia colore insieme al posizionamento del pomello.
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
- Passa `onCheckedChange = null` per disegnarlo come indicatore passivo. Il controllo perde gesto e nodo semantico.
- La riga che contiene l'interruttore **non deve avere un tocco suo** se non commuta: il contatto è già consumato.
- Una disabilitazione sfuma a 0,5, non al quarto: dentro una riga disattivata l'interruttore non sfuma da solo (a sfumare è la
  riga).

**Non usarlo quando.** Una scelta fra più di due stati (`FluidSegmentedControl`, `FluidChoiceRow`).

---

## FluidSlider

Il cursore mostra `value` su una pista sottile riempita con l'accento. Aggiorna lo stato subito in
`onValueChange` e salva in `onValueChangeFinished` quando serve scrivere su disco o avviare un lavoro.

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

**Uso con tacche e valore parlato.**

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.antigravity.fluidengine.ui.fluid.FluidSlider

@Composable
fun CursoreTimer(saveMinutes: (Int) -> Unit) {
  var minutes by remember { mutableFloatStateOf(30f) }
  FluidSlider(
    value = minutes,
    onValueChange = { minutes = it },
    valueRange = 5f..120f,
    snapValues = listOf(5f, 15f, 30f, 60f, 120f),
    onValueChangeFinished = { saveMinutes(minutes.toInt()) },
    contentDescription = "Timer",
    stateDescription = "${minutes.toInt()} minuti",
  )
}
```

**Comportamento.**

- **Costruzione**: la pista è una *registrazione* (si dipinge in un layer suo, quello che la maniglia legge), la maniglia è
  l'unica lastra: pillola bianca con ombra a riposo, tenuta svanisce il bianco e resta una lente (10/14 dp) che rifrange la
  pagina *più una copia della pista che si dispiega* (schiacciata a due terzi in orizzontale e a niente in verticale a riposo,
  aperta del tutto sotto il dito).
- **Geometria** (privata, ma utile a chi dimensiona): riga alta 48 dp; pista 6 dp; maniglia 40 × 24 dp; margine laterale di
  un quarto di maniglia (10 dp) a ogni lato, dove la sorgente la lasciava sporgere: qui sta *dentro* i bordi, perché nulla
  nell'engine disegna fuori dai propri limiti e una maniglia che sporge viene mozzata dal primo contenitore che ritaglia. Il
  centro della maniglia non arriva mai più vicino al bordo di un quarto di sé, ma il *valore* sì (come nella sorgente).
- **La riga è tutta del dito**: un tocco sulla pista porta lì la maniglia (con la molla); un trascinamento partito da lei la
  prende **senza spostarla** (relativo: afferrarla non cambia il valore, e un dito che sborda oltre l'estremità e torna non la
  muove finché non ripassa dal punto da cui è uscito). La maniglia si afferra anche a 10 dp dal bordo.
- Un dito sollevato sulla pista senza averla trascinata porta la maniglia al punto toccato.
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

Disabilitare il controllo o cambiare l'intervallo annulla il gesto incompleto e il punto in attesa:
il rilascio già in coda non emette un seek sul nuovo intervallo. L'app che cambia la sorgente mantenendo
lo stesso intervallo delimita anche l'identità della sorgente nel proprio wrapper.

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
Si ricompone solo la riga quando cambia la *stringa* (una volta al secondo per un orologio); così anche il valore a voce per
TalkBack (R1, 26 novembre 2026): mentre si ascolta, la semantica si rifà quando cambia la scritta di `valueDescription`, non a
ogni fotogramma del lettore. Il riempimento dell'accento si allarga **nel disegno**, non nel layout: un layout in meno a ogni
fotogramma (R1). **Resta** che mentre si ascolta la maniglia di vetro si sposta con la traslazione del suo `graphicsLayer`
(il renderer la inverte quando campiona), e in Compose una traslazione che cambia avvisa l'accessibilità come un layout: la
pagina si ricontrolla ancora ogni 100 ms finché il lettore suona (R1, misurato: 33 fotogrammi con eventi su 3 s). Con tutti
e due nulli lo scrubber è la
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

## FluidRangeSlider (con FluidRangeSliderDefaults)

**Cos'è.** Il cursore con due maniglie: il minimo e il massimo di un filtro. È `FluidSlider` con una maniglia in più, e
**la maniglia e la sua fisica sono quelle dello slider**, non una copia: `FluidSlider.kt` espone all'interno del modulo
la maniglia di vetro, la sua molla e ciò che la lente rifrange, e tutte e due le maniglie le usano. Apple non ha il
controllo: il riferimento per il gesto e l'accessibilità è `UISlider`.

**Firma essenziale.**

```kotlin
FluidRangeSlider(
  value: ClosedFloatingPointRange<Float>,
  onValueChange: (ClosedFloatingPointRange<Float>) -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
  enabled: Boolean = true,
  onValueChangeFinished: (() -> Unit)? = null,   // il momento di applicare il filtro
  minDistance: Float = 0f,                        // nell'unità del valore
  snapValues: List<Float> = emptyList(),
  snapThreshold: Float = 0.03f,
  startContentDescription: String? = null,        // di serie «Minimo»
  endContentDescription: String? = null,          // di serie «Massimo»
  startStateDescription: String? = null,
  endStateDescription: String? = null,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
)
```

**Uso: la durata dei brani.**

```kotlin
var from by remember { mutableFloatStateOf(60f) }
var to by remember { mutableFloatStateOf(270f) }
FluidRangeSlider(
  value = from..to,
  onValueChange = { from = it.start; to = it.endInclusive },
  valueRange = 0f..600f,
  minDistance = 30f,
  startStateDescription = "1:00", endStateDescription = "4:30",
  onValueChangeFinished = { applyFilter(from, to) },
)
```

**Comportamento.**

- **Le maniglie non si scavalcano**: quella che si trascina si ferma sull'altra, o a `minDistance` da lei, con un
  `Threshold` quando la raggiunge; non la spinge.
- **Chi prende il dito**: una maniglia sotto il dito (o a 10 dp) la prende subito; due maniglie sovrapposte le separa il
  primo movimento (verso i valori alti il massimo, verso i bassi il minimo: così due maniglie in fondo si separano
  sempre); sulla pista nuda un tocco porta lì la più vicina, un trascinamento di lato a mezzo slop la prende.
- **La maniglia presa per ultima sta sopra**, e solo lei ha la lente che si stira col dito.
- L'accento sta **fra** le due maniglie; due maniglie che si toccano lasciano un punto d'accento, non un buco.
- Le tacche, l'aptica, il movimento ridotto, la destra a sinistra e il ripiego senza RenderEffect sono quelli di
  `FluidSlider`.
- **TalkBack**: due nodi, uno per maniglia, sopra le maniglie: ognuno con il suo nome, l'intervallo che l'altra gli
  lascia, «imposta», «Aumenta» e «Diminuisci» (le parole di `FluidLabels`): la tacca dopo, o un ventesimo dell'intervallo.

**Trappole.**

- **Parole in italiano scritte nei default**: `FluidRangeSliderDefaults` («Minimo», «Massimo»). Un'app in un'altra lingua
  passa sempre i nomi delle due maniglie.
- Un `value` più stretto di `minDistance` si mostra allargato (la fine si allontana; in fondo alla pista l'inizio
  arretra), ma `onValueChange` non arriva finché il dito non muove una maniglia.

**Non usarlo quando.** Un valore solo (`FluidSlider`); un punto in qualcosa che avanza (`FluidScrubber`).

---

## FluidWheelPicker (con FluidWheelColumn e FluidWheelDefaults)

**Cos'è.** La ruota di iOS: `Picker` con `.pickerStyle(.wheel)`, `UIPickerView`, `UIDatePicker` a ruota. Una o più
colonne di voci che girano col dito su un cilindro e si fermano a scatti sotto una **fascia grigia**, che ingrandisce
appena il valore scelto.

**Firma essenziale** (una colonna sola, o più colonne).

```kotlin
FluidWheelPicker(
  count: Int, selected: Int, onSelectedChange: (Int) -> Unit, label: (Int) -> String,
  modifier: Modifier = Modifier, cyclic: Boolean = false, unit: String? = null,
  contentDescription: String? = null, stateDescription: ((Int) -> String)? = null,
  enabled: Boolean = true,
)

FluidWheelPicker(columns: List<FluidWheelColumn>, modifier, enabled)

FluidWheelColumn(
  count, selected, onSelectedChange, label,
  cyclic = false,                 // ore e minuti: dopo l'ultima la prima
  unit = null,                    // «ore», «min»: ferma nella fascia, come in Orologio
  contentDescription = null, stateDescription = null,
  weight = 1f, textAlign = …,     // al centro, o a destra con l'unità
  isEnabled = null,               // le voci che si possono scegliere (minimo e massimo)
)
```

**Uso: una data come `UIDatePicker`** (dal `sample`, scena `ruota-data`).

```kotlin
FluidWheelPicker(
  columns = listOf(
    FluidWheelColumn(31, day - 1, { day = it + 1 }, { "${it + 1}" }, cyclic = true,
      isEnabled = { it < monthLength }),          // 29, 30 e 31 spenti quando il mese è corto
    FluidWheelColumn(12, month - 1, { month = it + 1 }, { monthName(it) }, cyclic = true, weight = 1.4f),
    FluidWheelColumn(201, year - 1900, { year = 1900 + it }, { "${1900 + it}" },
      isEnabled = { 1900 + it in 1950..2050 }),   // minimumDate e maximumDate
  ),
)
```

**Comportamento.**

- **Le misure sono quelle di iOS 26 e 27**, prese dalle schermate (il timer di Orologio, la ruota delle HIG; vedi il
  rapporto di F8.2): alta 216 dp (`FluidWheelDefaults`), voci da 32 dp (crescono col testo grande fino a 1,6) su un
  cilindro di raggio 0,39 dell'altezza (84 dp: tre voci piene per lato e una quarta schiacciata), la fascia a capsula
  alta 34 dp, la voce nella fascia ingrandita di 1,12, quelle fuori al 45% dell'inchiostro e più tenui verso i bordi.
- **Le voci sono in peso normale**, anche quella scelta, come in iOS: solo l'unità («ore», «min») è semibold (F8.2c). Il
  taglio è quello del testo di Inter: Inter Display nell'engine non ha il peso normale.
- **La fascia rientra come in iOS 26 e 27** (F8.2b): 45 dp per lato (il timer di Orologio, le HIG), mai oltre il 12%
  della larghezza su una ruota stretta; anche le colonne stanno in quello spazio, così il valore scelto resta dentro la
  capsula. In una pagina il timer va da bordo a bordo, come in Orologio.
- **La fascia è un grigio piatto, non vetro** (F8.2c): il `quaternarySystemFill` di iOS (118, 118, 128 al 18% sullo
  scuro, 116, 116, 128 all'8% sul chiaro, misurato sulle schermate), senza bordo di luce né sfumature. Non galleggia sul
  contenuto: sta ferma nella ruota.
- **La costruzione è quella di `UIPickerView`**, in tre strati: la fascia, le voci fuori dalla fascia, le voci dentro la
  fascia (piene e ingrandite). Si muove solo il contenuto.
- **Il gesto**: un trascinamento verticale la gira 1:1, con l'elastico di UIKit oltre la prima e l'ultima voce. Lasciata
  con velocità **decelera come una lista di iOS** (0,998 a millisecondo, la proiezione di mezzo secondo di velocità) e si
  ferma **esattamente** su una voce: il punto proiettato si porta sulla voce più vicina e il decadimento si ritocca per
  arrivarci, senza rimbalzo. Un dito su una ruota che gira la ferma; un tocco su una voce la porta al centro.
- **L'ultimo pixel non si aspetta** (F8.2c): sotto 1 px dalla voce il lancio finisce con la molla dell'assestamento
  (0,30 s), e il valore arriva ~0,13 s dopo che la ruota sembra ferma. Prima, dopo un dito lasciato piano, la coda
  invisibile durava 0,7–1,4 s, e AM e PM giravano quando la ruota era ferma da un pezzo.
- **La presa**: la ruota prende il dito a mezzo slop in verticale prima che di lato (l'arbitro dello slider con gli assi
  scambiati), e da lì consuma tutto: in una lista o in un foglio che si trascina gira la ruota, non la pagina. Un gesto
  che va prima di lato è di chi sta sotto (un pager).
- **Il valore è del chiamante**: `onSelectedChange` arriva **quando la ruota si ferma** (come `didSelectRow`), non a
  ogni voce. Un chiamante che rifiuta vede la ruota tornare. Un `selected` cambiato da fuori la porta con la molla (0,38 s).
- **Le voci spente** (`isEnabled`) sono al 35%; una ruota che si ferma su una di loro scivola alla più vicina.
- **Sempre nitida** (F8.2b, scelta di Alessio): la ruota la muove il dito, e ciò che muove l'utente resta nitido anche
  durante l'inerzia. Si sfoca solo ciò che l'app muove da sola, come in iOS. Vale anche fuori dalla ruota (F8.1b): un
  `FluidNumericText` che mostra la voce scelta cambia di colpo, perché la corsa partita dal dito (lancio, assestamento,
  tocco) conta come il dito fino alla posa compresa; la molla che segue un `selected` dell'app no.
- **L'aptica**: un tic per voce che passa sotto la fascia, con la rete dei 55 ms; sopra 8 voci al secondo il `DragTick`
  leggero, sotto il `Tick` di un aggancio.
- **Il movimento ridotto**: niente inerzia lunga (un lancio porta al più una voce oltre il dito, assestamento di 0,20 s);
  lo scatto resta; un valore da fuori si posa.
- **TalkBack**: un nodo per colonna, regolabile (nome, valore detto, intervallo, «imposta», «Aumenta» e «Diminuisci»; su
  una ruota che gira dopo l'ultima viene la prima).
- **Il costo**: a riposo nessuna scrittura di stato e nessun ridisegno; mentre gira si ridisegnano solo le voci (la
  posizione si legge in disegno) e il moto non alloca per fotogramma. Le voci si misurano una volta (le liste lunghe in
  un anello delle ultime 48).

**FluidDurationWheel** (con `FluidDurationUnits`): il timer di Orologio, ore, minuti e (di serie) secondi con le unità
ferme nella fascia. Il valore è uno, in secondi; **le colonne sono collegate**: una durata sotto il minimo (di serie un
secondo, o un minuto senza secondi) non si sceglie, la colonna più piccola sale al minimo, come il `countDownTimer` di
`UIDatePicker`. I minuti possono andare a passi (un divisore di 60), come in iOS. **Di serie nessuna colonna gira**,
come il timer di Orologio di iOS 26 e 27 (sopra «0 ore» e «0 s» non c'è niente); con `cyclic = true` minuti e secondi
girano, come la ruota di `countDownTimer` fino a iOS 13 (F8.2c).

**FluidTimeWheel**: l'ora di `UIDatePicker`, in 24 o 12 ore (di serie quello del sistema), le colonne nell'ordine della
lingua (AM e PM prima dell'ora in coreano e giapponese). In 12 ore la ruota delle ore che passa da 11 a 12 **gira AM e
PM**, come in iOS; i minuti non portano le ore. **Il verso viene dal moto vero della colonna** (F8.2d): la ruota conta
le voci fatte col segno, quindi 8 AM portate avanti di 6 voci fanno le 2 PM e indietro di 6 le 2 AM, un lancio lungo
gira AM e PM a ogni passaggio fra 11 e 12, e un giro intero gira solo AM e PM. Fino a F8.2c il verso era la strada corta
fra due voci, ambigua su 6 voci e sbagliata oltre. È fatta per prendere il posto dei tasti «−» e «+» di
`FluidDateTimeSheet` (F8.8).

**Trappole.**

- `label` dev'essere una funzione pura dell'indice: le voci si misurano una volta. Se le parole cambiano (un'altra
  lingua), passa una lambda nuova.
- Le parole di serie di `FluidDurationUnits` sono in italiano («ore», «min», «s»): c'è la forma inglese.
- Una colonna in cui `isEnabled` spegne tutto non ha dove posarsi: almeno una voce deve potersi scegliere.

**Non usarlo quando.** Poche scelte che stanno in vista (`FluidSegmentedControl`, un menu); un valore continuo
(`FluidSlider`, `FluidRulerDial`).

---

## FluidRulerDial

**Cos'è.** La ghiera a righello: il righello di «Raddrizza» in Foto e la ghiera dello zoom in Fotocamera. Un righello di
tacche fitte scorre di lato sotto un indice fermo; il valore è scritto sopra l'indice, dove il dito non lo copre. Apple
non ha un'API: le misure vengono dalle schermate di Foto in iOS 26 e 18.

**Firma essenziale.**

```kotlin
FluidRulerDial(
  value: Float,
  onValueChange: (Float) -> Unit,
  modifier: Modifier = Modifier,
  valueRange: ClosedFloatingPointRange<Float> = -45f..45f,   // quello di «Raddrizza»
  step: Float = 1f,                                          // il valore fra due tacche
  majorEvery: Int = 10,                                      // una principale ogni 10, dal neutro
  neutralValue: Float? = 0f,
  enabled: Boolean = true,
  onValueChangeFinished: (() -> Unit)? = null,
  valueText: ((Float) -> String)? = …,                       // di serie «−12», «2,5»
  contentDescription: String? = null,
)
// F8.15c: la stessa ghiera col valore come lambda, letto solo dove serve (effetti, disegno, semantica)
FluidRulerDial(value = { stato.valore }, onValueChange = { stato.valore = it }, …)
```

**Comportamento.**

- **Le misure** (Foto, iOS 26): una tacca ogni 9 dp, alte 14 dp, spesse 1,8 dp; le altre al 49% dell'inchiostro, le
  principali all'80%; l'indice alto 28 dp nell'accento (il giallo di Foto); le tacche spariscono negli ultimi 30 dp a
  ogni lato. Un pallino sopra il valore neutro (come in iOS 18).
- **Il gesto**: il righello segue il dito 1:1 e oltre le estremità cede con l'elastico; lanciato continua con l'inerzia
  di una lista di iOS e si ferma su una tacca, **sulla principale** se ci finisce entro un quarto dell'intervallo fra due
  principali. Un dito sulla ghiera che scorre la ferma; il **doppio tocco** torna al neutro. Presa a mezzo slop di lato
  prima che in verticale: in un pager scorre la ghiera.
- **Il neutro attira**: trascinando, entro mezza tacca il valore è il neutro, con un `Threshold` all'arrivo.
- **Sempre nitida** (F8.2b): anche lanciata, le tacche non si sfocano; la ghiera la muove il dito. E i numeri che
  mostrano il valore (`FluidNumericText`) cambiano di colpo per tutta l'inerzia e col doppio tocco (F8.1b).
- **L'aptica**: `DragTick` per tacca a dito veloce, `Tick` per tacca lenta e per ogni principale, `Threshold` sul neutro e
  alle estremità, con la rete dei 55 ms.
- **Non si ricompone a ogni valore** (F8.15c, dal tablet: il righello dell'esposizione dell'editor dava il 16% di
  fotogrammi in ritardo). Il corpo della ghiera legge il valore solo negli effetti, nel disegno delle tacche e nella
  semantica (un nodo che si invalida quando cambia la tacca); si ricompone da sola la scritta sopra l'indice, quando
  cambia la scritta. Con la forma `value: Float` si ricompone solo un adattatore sottile; con la forma
  `value: () -> Float` non si ricompone niente, nemmeno il chiamante: è quella da usare quando il valore cambia a ogni
  fotogramma. Chi scrive un controllo simile: anche una lettura dentro `remember { }` in composizione è osservata e fa
  ricomporre (il valore di partenza si legge con `Snapshot.withoutReadObservation`). `FluidRulerDialDiagnostics`
  (contatori per le prove, non API per le app) conta le composizioni del corpo e della scritta. Da F7.24 anche la scritta sopra
  l'indice si disegna (non si ricompone), e la semantica aspetta la fine del gesto (`fluidSettledSemantics`).
- **Il lancio si posa in fretta** (F7.24b): la molla dell'assestamento prende la mano quando il decadimento scende sotto
  30 px/s (`FluidRulerLook.FlingHandoffSpeedPx`), non solo a un pixel dalla tacca. Prima un rilascio a 12–13 tacche al
  secondo strisciava ~2,4 s sotto la tacca, e la pagina disegnava a 120 fotogrammi al secondo per tutto quel tempo; ora
  il lancio dura ~1,4 s, come uno scorrimento di iOS alla stessa velocità. La tacca d'arrivo non cambia.
- **Il valore è del chiamante**: `onValueChange` arriva durante il gesto e l'inerzia, già al passo; `onValueChangeFinished`
  quando si ferma (un tocco da solo non lo chiama): ~0,13 s dopo che sembra ferma, perché l'ultimo pixel lo chiude la
  molla come nella ruota (F8.2c). Il valore scritto si ricompone solo quando cambia la scritta.
- **Il movimento ridotto** toglie l'inerzia lunga; lo scatto resta.
- **TalkBack**: un nodo regolabile con la scritta come valore, «Aumenta» e «Diminuisci» di un passo.

**Trappole.**

- `step` è anche la precisione: i valori che arrivano sono multipli del passo dall'inizio dell'intervallo. Per un valore
  più fine, un passo più piccolo (0,1 dB) e principali più rade.
- La ghiera non è vetro: sta su una barra o su una pagina, come in Foto. Sopra una foto mettila in un pannello di vetro.

**Non usarlo quando.** Un valore con un inizio e una fine che si vogliono vedere (`FluidSlider`); una scelta fra voci
(`FluidWheelPicker`).

---

## L'audio: l'onda e il registratore (F8.13)

Tre pezzi di `engine-ui` vicini a Memo Vocali e a Messaggi di iOS 26, misurati sulla guida dell'iPhone (1,13 px per punto,
immagini ufficiali in `artifacts/liquid-glass/sessioni/f8-13/riferimenti/`). Disegnano l'**inviluppo** puro di
`engine-foundation` (`FluidAudioEnvelope`: un valore ogni 20 ms, già nella scala che non satura di `FluidAudioLevel`); la
cattura è di [engine-audio](../moduli/audio.md), la riproduzione di [engine-playback](../moduli/playback.md). Tutti e tre li
muove il dito, quindi sono **nitidi** (regola di Alessio del 24 novembre: niente sfocatura nel trascinamento, nel lancio, né
quando l'onda scorre da sola durante l'ascolto).

**`FluidAudioWaveform`, l'onda della riga** (il messaggio audio di Messaggi). Barre tonde ogni 4 dp (il passo misurato), larghe
2 dp; quelle già ascoltate nell'accento, le altre velate; un silenzio è un puntino. Il dito fa come lo scrubber, in tre tempi:
prendere l'onda non la sposta, il dito la sposta **di quanto si muove** (relativo, come «scorri a destra e a sinistra sul
messaggio»), al rilascio **un** `onSeek`, e il punto lasciato resta finché il lettore non l'ha raggiunto
(`FluidMotion.ScrubberHoldMillis`); un tocco salta dove si tocca. Aptica: solo le estremità (`Threshold`), come lo scrubber
continuo. Le altezze si calcolano quando cambia la larghezza o l'inviluppo, in un array riusato.

**Le due regole di F7.24, anche qui** (R1, 26 novembre 2026). L'onda della riga, l'onda che scorre e `FluidRecorderClock`
si ridisegnano **in uno strato loro** (`graphicsLayer`): durante l'ascolto il ridisegno a ogni fotogramma resta lì, e non fa
riregistrare il gruppo della lista né la sorgente del vetro della pagina. Le due onde hanno la **semantica a fine gesto**
(`fluidSettledSemantics`): ferma mentre il dito trascina o lancia, e mentre si ascolta il valore a voce («1:05 di 3:20») si
dà a TalkBack quando cambia la scritta, una volta al secondo, non a ogni fotogramma.

**`FluidAudioTimeline`, l'onda che scorre** (la registrazione e il taglio di Memo Vocali). La testina sta **ferma al centro**
(1 dp, un pallino da 7 dp sopra e sotto la fascia), l'onda scorre sotto di lei a **100 dp al secondo** (il righello di Memo
Vocali: 113 px fra due secondi), una barra da 1 dp ogni 4 dp; sotto, il **righello** con le scritte dei tempi (ogni 1, 2, 5,
10… secondi secondo lo zoom) e le tacche ai quarti, misurate una volta per scritta. Il dito **trascina l'onda 1:1** e la
**lancia** (un decadimento della velocità del dito; con le animazioni ridotte niente lancio); al rilascio o a fine lancio un
`onSeek`. Il **pizzico** cambia lo zoom attorno alla testina (`FluidAudioTimelineState.dpPerSecond`), fra l'audio intero nella
larghezza e 400 dp al secondo; TalkBack ha anche «Ingrandisci» e «Riduci». **Mentre si registra** (`recording = true`) la
testina è rossa (`systemRed`), a destra c'è la linea a puntini di ciò che deve venire, l'onda non si tocca, e con
`advancing = true` si ridisegna a ogni fotogramma (la posizione è un orologio, non uno stato).

**`FluidVoiceRecorder`, il registratore.** A riposo il **tasto rosso** (`FluidRecordButton`: un disco di vetro tinto di
`systemRed` da 52 dp, misurato sull'elenco di Memo Vocali); registrando, il suo vetro **diventa il pannello**
(`FluidRecorderPanel`, nel piano flottante: un vetro solo con la stessa identità che cambia forma con Fluid-physics, e ci
torna alla fine). Nel pannello: «Fine» in alto a destra, il titolo con l'ora, l'onda che scorre (rossa mentre registra), il
**tempo grande** «00:07.00» (`FluidRecorderClock`: 34 sp in grassetto a cifre tabulari, ridisegnato dalle cifre misurate una
volta, senza ricomporre né allocare; per TalkBack la durata ai secondi, anche nel riascolto in pausa, e oltre l'ora la
larghezza delle ore), i tasti dell'ascolto (meno e più 15 secondi e Play,
grigi mentre registra, come Memo Vocali) e la **capsula della pausa** (147 × 58 dp, due barre rosse; in pausa «Riprendi» in
rosso). In pausa, con un lettore di quel che si è registrato (`playback`), l'onda si scorre e si riascolta prima di
riprendere. Una riga dice quando il suono **taglia** (`clipping`) e quando la pausa l'ha decisa il telefono (`interrupted`).
Lo stato è dell'app: `FluidRecorderPhase` (`Idle`, `Recording`, `Paused`).

**`FluidRecordingQualityRow`, la qualità per l'utente** (F8.13b). La riga delle impostazioni «Qualità di registrazione»,
da mettere in un `FluidListGroup(glass = true)`: la qualità di adesso a destra, e al tocco la riga diventa il pop-up di
vetro di `FluidPickerRow` con le preimpostazioni di `FluidRecordingQuality` (voce per l'IA, standard, alta, senza
perdita), ognuna col formato e con lo spazio di un'ora («AAC 96 kbit/s · circa 43,6 MB all'ora», con `FluidFormat.bytes`).
I testi sono in `FluidRecordingQualityStrings`. La qualità scelta va a `FluidAudioRecorder.start(file, quality)` di
[engine-audio](../moduli/audio.md).

```kotlin
val recorder = remember { FluidAudioRecorder(context) }                       // engine-audio
val status by recorder.status.collectAsState()
FluidVoiceRecorder(
  phase = when (status) { is FluidRecorderStatus.Recording -> FluidRecorderPhase.Recording
                          is FluidRecorderStatus.Paused -> FluidRecorderPhase.Paused
                          else -> FluidRecorderPhase.Idle },
  onRecord = { microfono.request { recorder.start(File(cartella, "memo.m4a")) } },
  onPause = recorder::pause, onResume = { recorder.resume() },
  onDone = { scope.launch { recorder.stop()?.let(::salva) } },
  envelope = recorder.envelope, elapsedMillis = recorder::elapsedMillis,
  backdrop = pageGlass,                                                       // nell'overlay di FluidScreen
)
```

**In galleria**: «Elementi nuovi» › F8.13 (`--es novita.scene memo-vocali`, `onda-audio`, `riproduzione-sistema`,
`ascolto-paragrafo`). **Le prove**: `F8p13AudioUiTest` (`sample`, Robolectric, il dito vero): l'onda della riga relativa con un
solo salto e il tocco; TalkBack (lo stato a voce, le due azioni, `setProgress`); l'onda che scorre col dito, il lancio che va
oltre il dito, il pizzico, niente lancio con le animazioni ridotte, niente tocco mentre registra; il pannello nelle sue fasi;
il registratore vero di engine-audio dietro il pannello, con un microfono finto. `F8p13R1AudioTest` (R1, con
l'accessibilità accesa): nessun evento di accessibilità col dito giù sulle due onde, al più quattro in tre secondi d'ascolto
(anche lo scrubber); lo strato loro per le onde e il cronometro; il cronometro che segue il riascolto in pausa oltre l'ora; le
scene della galleria che non si ricompongono a ogni fotogramma; la trascrizione vuota che lo dice.

## La semantica a gesto finito (fluidSettledSemantics e FluidValueText, F7.24)

Con un servizio di accessibilità acceso (TalkBack, ma anche «Collegamento a Windows» o un'app che legge lo schermo: sul
Tab S9 di Alessio c'è sempre) **ogni semantica che cambia fa ricontrollare a Compose tutta la pagina**
(`checkForSemanticsChanges`, fuori dal fotogramma, 1,3–4,4 ms sul thread dell'interfaccia). Un `Text` che scrive il valore
di un righello e un `semantics { stateDescription = … }` lo fanno a ogni tacca, anche dentro un `clearAndSetSemantics`.

- **`Modifier.fluidSettledSemantics(mergeDescendants = false) { … }`** si usa come `semantics { }`. Il blocco può leggere
  stati (non ricompone: le letture si osservano nel nodo). Mentre un dito muove un valore (il gesto e l'inerzia, quello
  che i controlli dell'engine dicono a `FluidFingerDrive`) la semantica **resta ferma**: si aggiorna quando il gesto
  finisce, o al più una volta al secondo in un gesto lungo (`FluidSettledSemanticsLook.SlowPaceMillis`). Un valore cambiato
  da TalkBack («aumenta», «imposta») o dall'app arriva subito. La usano `FluidSlider`, `FluidRangeSlider`, `FluidScrubber`,
  `FluidWheelPicker`, `FluidRulerDial`, il numero di `FluidNumericText` e, da R1, `FluidAudioWaveform` e `FluidAudioTimeline`.
- **Anche un layout che cambia** a ogni fotogramma sotto un nodo di semantica (una barra che si allarga con `Modifier.layout`),
  e anche un `graphicsLayer` che cambia **traslazione** (`translationX`), fanno ricontrollare la semantica della pagina ogni
  100 ms, anche se nessuna semantica cambia (Compose li tratta come un layout). Quel che si muove a ogni fotogramma senza
  il dito va nel disegno, non nel layout né nella traslazione dello strato (R1: il riempimento di `FluidScrubber` è nel
  disegno; la sua maniglia di vetro no, ed è ancora aperto).
- **Un valore che l'app fa avanzare a ogni fotogramma** (la posizione di un lettore) arriva subito anche lui, quindi rifarebbe
  la semantica a ogni fotogramma: il blocco legge uno stato derivato della **scritta** (che cambia una volta al secondo per
  un orologio) e, solo se il valore è tenuto (il dito, un salto che aspetta il lettore), del valore; il numero
  dell'intervallo lo prende con `Snapshot.withoutReadObservation` (R1: lo scrubber e le due onde).
- **`FluidValueText(text = { … }, style, color, textAlign)`**: un testo su una riga che cambia col dito («Rotazione: 12°»,
  il numero in un tasto). Si legge nel disegno: **non si ricompone, non si rimisura**, ha uno strato suo, e TalkBack lo
  legge con `fluidSettledSemantics`. Occupa la larghezza che riceve e l'altezza di una riga; non va a capo. Al pixel è
  il `Text` di prima (stesse posizioni arrotondate).
- Un `pointerInput` di un'app che muove un valore non lo dice a `FluidFingerDrive`: la sua semantica resta immediata.
- **Il vetro vicino**: un contenuto che si ridisegna a ogni fotogramma (una foto regolata dal righello, un numero) e la
  **sorgente di un vetro** (`glassBackdropSource`) nello stesso strato fanno ricatturare tutti i vetri a ogni fotogramma:
  la sorgente si riregistra ogni volta che si riregistra lo strato in cui sta (`LayerBackdropNode`). Si dà uno strato
  suo (`Modifier.graphicsLayer()`) a chi si ridisegna, o alla sorgente. Nell'editor delle foto (F7.24): cinque vetri
  ricatturati a ogni fotogramma del righello, ~19 passate di disegno a fotogramma sul RenderThread. **Vale per ogni
  componente che si ridisegna a ogni fotogramma** (R1): le onde e il cronometro dell'audio hanno il loro strato.
- `FluidSettledSemanticsDiagnostics` (`publishes`, `deferred`) conta le semantiche date e quelle tenute ferme: **non è API
  per le app**.

## Licenze dei port

| pezzo | origine | licenza |
|---|---|---|
| `FluidSwitch` (pomello, copia schiacciata della pista, molle, stiramento) | `LiquidToggle` del catalogo di **Kyant0** (`AndroidLiquidGlass`, commit `b18eb0f…`) | Apache-2.0, Copyright 2025 Kyant |
| `FluidSlider` (pillola bianca/lente, pista registrata, copia che si dispiega) | `LiquidSlider` del catalogo di Kyant0, arrivato passando dal port già fatto in **Pampa notes** | Apache-2.0, Copyright 2025 Kyant |
| `FluidScrubber`, la goccia di `FluidSegmentedControl` | `glassDrop` (`glass/interaction/GlassDrop.kt`, la ricetta estratta dai due controlli) e la copia della pista dello slider | Apache-2.0, Kyant |
| le due maniglie di `FluidRangeSlider` | la maniglia di `FluidSlider` (`FluidSliderThumb`, 4.0, F8.2), cioè il pomello del `LiquidSlider` | Apache-2.0, Kyant |
| tasti: `FluidButton`, `FluidGlassButton`, `FluidGlassIconButton`, `FluidBarAction`, `FluidChip`, `FluidColorDot` (pressione, gesto, aptica, bagliore) | **LibrePods** (`StyledButton`, `StyledIconButton`, `DragUtils`; ramo `android/rewrite`, `3d4d270`) | **GPL-3.0** |

Il materiale di base di Kyant include le lenti, la pista registrata e le misure dei pomelli. Fluid Engine
fornisce l'accento di tema, la presa del gesto, aptica, semantica, accessibilità e tacche del cursore. La scala
di interruttore e cursore segue la pressione. La deformazione segue l'accelerazione e l'interruttore ha un
tempo di formazione e chiusura della lente. Le modifiche al port sono elencate in testa a `FluidSwitch.kt`
e `FluidSlider.kt` e in `LICENSES/AndroidLiquidGlass.md`.

Dalla 3.0 `engine-ui` include codice LibrePods. La distribuzione di app derivate avviene sotto GPL-3.0,
con i sorgenti disponibili a chi riceve l'APK. Nome e logo di LibrePods restano riservati all'attribuzione.

L'app deve riportare *AndroidLiquidGlass — Apache-2.0 — Copyright 2025 Kyant* e *LibrePods — GPL-3.0* fra le licenze di terze parti: l'engine ha già
`fluidLicensesSection` (una sezione di `LazyListScope` che elenca da sola le voci di `FluidEngineLicenses`). Testi: `LICENSES/AndroidLiquidGlass.md`,
`LICENSES/LibrePods.md`.

---

## Token, costanti, Defaults

**`FluidScrubberDefaults`**: `ContentDescription` = «Posizione», `StepBackLabel` = «Indietro», `StepForwardLabel` = «Avanti», `MaxStepDots` = 32.
**`FluidScrubbing`**: `clock`, `Elapsed`, `Remaining`, `Total`, `Spoken` (pubblici); il resto (`snap`, `nudge`, `caughtUp`, `shownValue`…) è `internal` e provato.
**`FluidWheelDefaults`**: `Height` = 216 dp, `RowHeight` = 32 dp. **`FluidRangeSliderDefaults`**: `StartDescription` = «Minimo», `EndDescription` = «Massimo».
**`FluidSwitchWidth`** = `64.dp`. **`FluidMotion.ScrubberHoldMillis`** = 3000 ms (token di attesa dello scrubber). **Pomelli**: `GlassDropLens.Slider` (10/14 dp), `GlassDropLens.Switch` (5/10 dp).
**Salita nel vetro** (`GlassDropRise`, interno, `glass/interaction/GlassDrop.kt`): crescita ×1,5 (×1,25 con le animazioni ridotte), stiramento 0,0008 per dp/s
con la velocità lenta a 14,3 rad/s e il tetto a 0,18, tocco dell'interruttore 100 ms e 60 ms all'arrivo; alone (`GlassDropGlow`) al 10%, centro 0,16 altezze sotto la lente.

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

- **`F8p2WheelsTest`** (`engine-ui`, F8.2): l'inerzia a scatti (la proiezione di UIKit, il lancio che finisce esatto sulla
  voce senza superarla e parte con la velocità del dito, lo stesso moto a 60 e 120 Hz, l'assestamento a molla, nessuna
  allocazione per fotogramma, il movimento ridotto); la ruota contro le schermate di iOS 27 (le voci 1–3 entro 2 pt, tre
  voci piene e una schiacciata, l'inchiostro fuori dalla fascia, il tocco che porta al centro la voce giusta, le voci
  spente); le ruote pronte (il minimo della durata, AM e PM che girano fra 11 e 12); la ghiera contro Foto (90 tacche, le
  principali dallo zero, lo scatto sulle principali); le due maniglie (niente scavalchi, distanza minima, chi prende il
  dito, i passi per TalkBack).
- **`F8p2GesturesUiTest`** (`sample`, Robolectric, col dito vero e il vetro vero): la ruota trascinata di tre voci si posa
  sulla terza con un tic per voce; lanciata si ferma esatta molto più in là; un dito la ferma; un tocco porta al centro
  la voce toccata; col movimento ridotto al più una voce oltre il dito; TalkBack (valore, «Aumenta», «Diminuisci»,
  «imposta», la ruota che torna alla prima); in una lista il gesto verticale è della ruota. La ghiera: dieci tacche e la
  principale, il lancio su una tacca intera, il doppio tocco allo zero con il colpo, TalkBack. Le due maniglie: niente
  scavalco con la distanza minima e il colpo, la separazione dal verso, i due nodi per TalkBack. Ogni scena di F8.2 a
  riposo non scrive niente.
- **`F8p2bWheelFramesTest`** (`sample`, Robolectric, vetro vero e PixelCopy, F8.2b): la scena del timer su uno schermo da
  393 dp a 420 dpi, in chiaro e in scuro, a riposo e a metà lancio; le colonne cominciano e finiscono a 45 dp dal bordo,
  dove comincia e finisce la fascia. I fermi vanno in `sample/build/reports/f8-2b/fermi/` (la tavola contro iOS 27).
  In `F8p2WheelsTest`: la fascia contro le misure di iOS 27, e nessun effetto di sfocatura nei file di ruota e ghiera.
- **F8.2d.** In `F8p2WheelsTest`: l'ora in 12 ore dal moto (6 voci avanti e indietro, 17 e 18 voci, un giro e due), e
  la strada corta di TalkBack. In `F8p2GesturesUiTest`, col dito vero: 8 AM avanti di 6 voci fa le 2 PM e indietro le
  2 AM; un lancio lungo delle ore (più di 6 voci) dà 8 AM più le voci fatte; «Aumenta» e «Diminuisci» fra 11 AM e
  12 PM.
- **F8.2c.** In `F8p2WheelsTest`: un lancio lento (il dito alzato a ~2,9 voci al secondo) si ferma entro 0,3 s dalla posa
  che si vede (1 px), senza scavalcare né tornare indietro; il grigio della fascia contro le schermate (sullo scuro e sul
  chiaro) e niente vetro nella ruota; le voci in peso normale e l'unità semibold. In `F8p2GesturesUiTest`, col dito vero:
  la ruota dell'ora lasciata piano dà il valore entro 0,3 s dalla posa e AM e PM partono con lui; la ghiera lasciata piano
  finisce entro 0,3 s dalla posa; il timer di serie non gira e con `cyclic` dopo 59 secondi torna lo 0. In
  `F8p2bWheelFramesTest`: la fascia nei pixel è piatta (ogni riga dello stesso colore, niente bordo di luce) ed è la
  pagina più il grigio di iOS.

- **`FluidSliderSnapTest`, `FluidSliderClaimTest`** (`engine-ui`): tacche (entro la soglia, la soglia è una frazione, `1f` è un passo, intervallo vuoto, tacca che suona solo all'atterraggio, rete dei 55 ms, afferrare fermo non suona); il test dei mezzi slop; l'isteresi dell'interruttore e dove si posa il pomello lasciato.
- **`GlassDropRiseTest`** (`engine-ui`, F6.3): la crescita mai oltre ×1,5 e mai sotto il riposo, dentro la finestra dei tempi di Apple; lo stiramento
  con il segno dell'accelerazione, che torna a zero a velocità costante e da fermo, con il tetto, uguale a 60 e a 120 Hz, ad area costante, nullo senza
  pressione; l'alone che segue la lente e la pressione. **`F63ControlsTraceTest`**: le sette tracce del banco (`tools/glassbench/traces/f6-3/`), col dito
  di Apple, e il tocco che forma la lente prima di muoverla.
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
| `FluidRangeSlider.kt` | `FluidRangeSlider`, `FluidRangeSliderDefaults`; i conti delle due maniglie |
| `FluidWheelPicker.kt` | `FluidWheelPicker` (due forme), `FluidWheelColumn`, `FluidWheelDefaults`; l'aspetto con le fonti, il cilindro, lo stato vivo di una colonna |
| `FluidWheelPresets.kt` | `FluidDurationWheel`, `FluidDurationUnits`, `FluidTimeWheel` e i loro conti |
| `FluidRulerDial.kt` | `FluidRulerDial`; l'aspetto, la scala, lo stato vivo |
| `FluidDetentInertia.kt` | l'inerzia a scatti di UIKit condivisa da ruota e ghiera |
| `FluidAudioWaveform.kt` | `FluidAudioWaveform`, `FluidAudioTimeline`, `FluidAudioTimelineState`, `FluidAudioWaveformDefaults`, `FluidAudioTimelineDefaults`; il dito in tre tempi dell'onda |
| `FluidVoiceRecorder.kt` | `FluidVoiceRecorder`, `FluidRecorderPanel`, `FluidRecordButton`, `FluidRecorderClock`, `FluidRecorderPhase`, `FluidRecorderStrings`, `FluidRecorderDefaults`; la riga della qualità `FluidRecordingQualityRow` e `FluidRecordingQualityStrings` |
| `FluidSettingRows.kt` | `FluidToggleRow` e `FluidSliderRow` — vedi [contenuto.md](contenuto.md) |
| `glass/interaction/GlassDrop.kt` | `glassDrop`, `GlassDropLens`, `GlassDropRecipe`, `GlassDropFaces` |
| `LICENSES/AndroidLiquidGlass.md`, `LICENSES/LibrePods.md` | le attribuzioni e l'elenco delle modifiche |
| `docs/10-coerenza.md` | la matrice dei comportamenti (D1–D9): sezioni «La goccia» e «Gocce» |

Tasti, scelte, campo di testo: [controlli.md](controlli.md).
