<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/movimento.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Pressione, gesto e movimento

Tutto ciò che risponde al dito o si muove nell'engine passa da qui: la **pressione** dei pulsanti e delle
superfici (`fluidPressable`, `FluidPressFeedback`, `FluidPressState`), il **gesto** che decide di chi è il dito
(`FluidPressGrip`), il **vocabolario del movimento** (`FluidMotion`: molle, curve, durate, un nome per ogni numero),
la **politica del movimento ridotto** (`FluidMotionPolicy`) e due pezzi piccoli (`Modifier.gooey`, `rememberCurrentDate`).
Le pagine (`FluidPageTransition`, `FluidPageHost`) hanno la loro scheda; i menu e i modali usano i token della «goccia»
elencati più sotto ma ne hanno una loro. L'aptica che accompagna la pressione è in [aptica.md](aptica.md).

Il modello mentale, in due frasi. **Un pulsante è una cosa piccola e sola: cresce sotto il dito, lo segue e vibra
(come LibrePods); una superficie (card, riga, tessera) cede di pochi centesimi e sta ferma**, perché una che crescesse
invaderebbe le vicine. **Il movimento non si scrive mai a mano: ogni molla, durata e curva ha un nome in
`FluidMotion`**, così tutta l'app finisce i suoi movimenti in tempi che sono multipli voluti l'uno dell'altro, ed è
questo, più di ogni singola curva, che fa leggere i movimenti come un sistema solo.

Catalogo delle firme: [../api/movimento.md](../api/movimento.md). Il perché storico: «I pulsanti crescono, le superfici
cedono» e «Un solo vocabolario di movimento» in [../regole.md](../regole.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un'area cliccabile con la risposta di un **pulsante** (cresce, segue il dito) | `Modifier.fluidPressable(onClick, feedback = FluidPressFeedback.Expand)`; per un tasto tondo solo icona `FluidPressFeedback.ExpandRound` |
| un'area cliccabile con la risposta di una **superficie** (card, tessera, cella) | `Modifier.fluidPressable(onClick)` e basta: il default cede a 0,974 |
| una **riga** in una lista raggruppata | `Modifier.fluidRowPressable(onClick)`: velo di colore, mai scala |
| un pulsante dentro una **fila che scorre** (chip, pallini di un selettore) | `fluidPressable(..., feedback = Expand, grip = FluidPressGrip.YieldsToScroll)` |
| un pulsante che apre qualcosa di suo (menu) | `haptic = FluidHapticEvent.Open` al posto del default; i colpi del contatto restano |
| un controllo che **vibra già per conto suo** | `haptic = null`: spegne contatto, rilascio, grana e click |
| due pezzi (click e vetro) che devono leggere **la stessa pressione** | `rememberFluidPressState(...)` e lo stesso `pressState` a chi guida (`fluidPressable(pressState = s)`) e a chi disegna (`glassControlSurface(pressState = s)`) |
| un vetro che segue il dito **senza** un click proprio | non serve nulla: `glassControlSurface` lo fa; `FluidPressFeedback.None` se la pressione la disegni tu |
| una molla per un valore a schermo | un token: `FluidMotion.snappy()`, `standard()`, `smooth()`… — **mai `spring(...)` a mano** |
| una molla per `Dp`, `IntOffset`, `IntSize` o pixel | `FluidMotion.dp()`, `intOffset()`, `intSize()`, `pixels()`: hanno la soglia giusta, quella di serie fa girare la molla ben oltre l'ultimo pixel visibile |
| scegliere smorzamento e rigidezza a tempo di esecuzione | `FluidMotion.tuned(DampingChrome, ResponseSnappy)` con i token, non con numeri |
| una dissolvenza o un cambio colore | `fadeIn/fadeOut/crossFade/color`: durate con curva, **non** molle (l'alfa non ha slancio da conservare) |
| un valore che si accende e si spegne in un lampo | `FluidMotion.flick()` (la molla di serie di Compose, ma col suo nome) |
| un segno che pulsa (un pallino «dal vivo») | `FluidMotion.pulse(...)` dentro `rememberInfiniteTransition`, **solo se** `LocalFluidMotionPolicy.current.allowDecorativeMotion` |
| un giro che si ripete (spinner, barra indeterminata) | `FluidMotion.loop(durationMillis, ...)` con `SpinnerMillis` / `IndeterminateMillis` |
| spegnere il movimento decorativo | `FluidMotionPolicyProvider` in radice **e** `LocalFluidMotionPolicy` nei tuoi componenti: vedi «Movimento ridotto» (l'engine **non** lo installa da solo) |
| sapere che «oggi» è oggi anche dopo mezzanotte | `rememberCurrentDate()`; **non** `remember { LocalDate.now() }` |
| due forme che si fondono come gocce | `Modifier.gooey { raggioPx }` (offscreen layer + soglia sull'alfa) |
| un'animazione di **pagina** | non qui: [pagine](../api/pagine.md) e la scheda delle pagine |
| una barra di scorrimento o un cursore | non una molla tua: `FluidFastScroller`, `FluidScrubber`, `FluidSlider` hanno già i loro token (`FastScrollerHideMillis`, `ScrubberHoldMillis`) |

## I pulsanti crescono, le superfici cedono

È la regola che regge tutta la famiglia, ed è una scelta di Alessio, non una convenzione: **dalla 3.0 un pulsante è
quello di LibrePods**, perché in mano risponde meglio del nostro. Le differenze erano poche e tutte nello stesso
verso: un tasto che aspettava, che si prendeva il dito dopo un ritardo, che vibrava una volta sola.

| | pulsante (`Expand`) | superficie (`yielding`) |
|---|---|---|
| esempi | `FluidButton`, `FluidGlassButton`, `FluidGlassIconButton`, chip, ancore dei menu, tasto indietro | card, tessere, celle, `FluidVividCard` |
| pressione | **al contatto**, senza ritardo né soglia | dopo il ritardo di `clickable` dentro uno scorrevole: una pagina che parte a scorrere da una card non la accende mai |
| movimento | cresce `1 + maxScale`, segue il dito (deriva elastica), si allunga nel verso in cui scorre | cede a `pressedScale` (0,974) e sta ferma |
| dito | **suo** fino al sollevamento (`HoldsFinger`): la pagina sotto non scorre | della pagina: se parte da una card la lista scorre |
| aptica | tre colpi (`PressDown`, `PressRelease`, `PressClick`) + grana mentre scorre | un `Tap` |
| molle | `spring(0,5; 300)` in salita e in discesa (LibrePods) | salita `instant` critica, ritorno `fluid` (0,74, ~0,38 s) con un filo di rimbalzo |
| rilascio | **subito**, dal valore e dalla velocità in cui trova la salita | aspetta che la salita arrivi al 96% (`ArrivalFraction`): un tap di 20 ms mostra la pressione intera |

**Non scrivere una terza risposta al dito in una schermata**: è `FluidPress.kt`, o non è. Prima della 2.11 c'erano tre
implementazioni (una scala in un `LaunchedEffect`, una molla dentro il bagliore del vetro, un `animateFloatAsState` nello
slider), e tutte e tre avevano lo stesso difetto: il rilascio ripartiva da dove era arrivata la pressione.

### I numeri veri

Tutti dal sorgente (`FluidPress.kt`); sono i numeri di LibrePods tranne dove è detto.

| cosa | valore |
|---|---|
| crescita di un tasto **con testo** (`Expand`) | `maxScale = 0,10`: scala `1 + 0,10·progress` |
| crescita di un tasto **tondo** (`ExpandRound`) | `maxScale = 0,5`: un tasto da 44 dp arriva a 66, al picco della molla a ~70 |
| molla di pressione e di rilascio dei pulsanti | `spring(0,5; 300; 0,001)`: arriva in cima in ~0,21 s sorpassandola del ~16%; al rilascio scende sotto il riposo di ~16% del percorso fatto — è il «pop» |
| picco di `progress` | ~1,16 (e sotto lo 0 in rilascio): lo stato **esce** da [0, 1] di proposito |
| **margine** del tetto del lato lungo, tasto con testo | `longSideCapDp = 16`: la crescita totale del lato lungo non supera 16 dp |
| effetto del tetto | 120 dp → 10% (12 dp); 160 dp → 10% (il limite); 200 dp → 8%; 320 dp (a tutta larghezza) → **5%**, il `maxScale` che LibrePods passa a mano ai suoi tasti larghi |
| tetto del tasto tondo | `RoundLongSideCapDp = 24`: ×1,5 intero fino a **48 dp**; oltre cresce di 24 dp in tutto (12 per lato): 54→78 (×1,44), 62→86, 76→100 (non 114) |
| **deriva** del dito | `m·tanh(0,05·d/m)` per asse, con `m` il lato corto e `d` la strada del dito: su 48 dp, ~1 dp a 20 dp di strada, ~5 a 100, ~14,5 a 300. **Non** pesata dalla pressione |
| **allungamento** | `scala + 0,1·|cos θ · d.x / M|·min(w/h, 1)` (e il simmetrico in y), `M` lato lungo; lineare e **senza tetto** (a 400 dp di strada su un tasto da 160 dp, +25%) |
| ritorno di deriva e allungamento | molla **critica** `(1; 300)`: non rimbalzano, rimbalza solo la scala |
| movimento ridotto | crescita **dimezzata** (`ReducedAmplitude = 0,5`), niente deriva né allungamento, molle critiche (`ResponseInstant` in salita, `ResponseSnappy` in discesa), nessun rimbalzo |
| margine del click dopo un trascinamento | `FluidPressClickSlack = 16 dp` (o il tocco minimo di Compose, se più grande) |
| il gesto prende il dito oltre | metà dello slop del sistema (`ClaimFraction = 0,5`); per un mouse da subito (soglia 0) |
| fuoco da tastiera (ogni `fluidPressable`) | si fa avanti a 1,03; con `focusShape` anche un velo dell'accento (alfa 0,18) |

La **deriva è la somma degli spostamenti del dito sullo schermo, non la sua posizione sul tasto**: se la pagina scorre
sotto un dito fermo su un tasto, il tasto lo segue lo stesso (il dito si è mosso, anche se non sul tasto). È voluto,
come in LibrePods, e per questo anche il click lungo misura lo slop sulla strada del dito. Al sollevamento la somma
torna a zero con la molla critica e si porta dietro il bagliore del vetro.

## `fluidPressable`

**Cos'è.** Il modificatore che dà a un elemento il click, il click lungo, il fuoco da tastiera e la risposta al dito,
nella forma che Apple le dà: *è la cosa stessa* che risponde. Niente ripple (un'increspatura dice «qui hai toccato»,
un cambio di taglia dice «la cosa che hai toccato ha risposto»), niente morphing di angoli ad hoc (vive in un posto
solo, Fluid-physics).

**Firma essenziale.**

```kotlin
@Composable
fun Modifier.fluidPressable(
  onClick: (() -> Unit)? = null,
  onLongClick: (() -> Unit)? = null,
  enabled: Boolean = true,
  pressedScale: Float = FluidPressFeedback.DefaultYieldScale,   // solo se feedback == null
  role: Role? = null,
  haptic: FluidHapticEvent? = FluidHapticEvent.Tap,             // null: niente aptica di nessun tipo
  feedback: FluidPressFeedback? = null,                         // null = yielding(pressedScale)
  pressState: FluidPressState? = null,                          // condiviso con chi disegna
  longClickHaptic: FluidHapticEvent? = FluidHapticEvent.GestureStart,
  grip: FluidPressGrip = FluidPressGrip.HoldsFinger,            // conta solo per chi cresce
  // + interactionSource, focusShape: tutti i parametri in api/movimento.md
): Modifier
```

**Uso.** Un pulsante vero dell'engine fa esattamente questo (`FluidButton`):

```kotlin
Box(
  modifier
    .fluidPressable(
      onClick = onClick,
      role = Role.Button,
      haptic = haptic,
      feedback = FluidPressFeedback.Expand,
      grip = grip,
    )            // PRIMA di clip e background: dopo, scalerebbe solo ciò che sta dentro
    .clip(shape)
    .background(container),
)
```

Una card che cede, invece:

```kotlin
Modifier.fluidPressable(onClick = { /* apre l'elemento */ })   // 0,974, un Tap al rilascio
```

**Comportamento.**

- **Se `onClick` e `onLongClick` sono nulli ritorna `this`**: niente pressione, niente fuoco, niente semantica.
- **Il default è `yielding`, non `Expand`.** Un `fluidPressable(onClick)` nudo dà una superficie. Per un pulsante
  bisogna passare `feedback = FluidPressFeedback.Expand` (i componenti dell'engine lo fanno già).
- **Si disegna in fase di disegno** (`drawWithContent`), da stato letto lì: una pressione, e un dito trascinato su un
  tasto premuto, invalidano solo il disegno di quel nodo. Niente ricomposizione, niente `RenderNode` tenuto vivo per
  ogni card ferma.
- **Con `pressState` dato il modificatore guida (pressione, rilascio, dito, tacche) ma non disegna** nessuna
  trasformazione: `feedback` e `pressedScale` sono ignorati, vale quello con cui hai creato lo stato.
- **Pulsante (`expands`).** Il puntatore è di `fluidButtonGesture`; `clickable` resta per il click di un tocco, la
  tastiera (Invio, Spazio, centro del D-pad premono finché restano giù: `fluidButtonKeys`), TalkBack e il ruolo. Il
  click lungo **non** passa da `clickable`: lo decide il gesto (tempo + slop) e a TalkBack resta un'azione
  semantica `onLongClick`.
- **Superficie.** `combinedClickable` con `hapticFeedbackEnabled = false`: il `LongPress` di piattaforma che Compose
  suonerebbe da sé si sommerebbe al colpo del vocabolario (`longClickHaptic`).
- **`haptic`.** Per un pulsante il default `Tap` **diventa** `PressClick` (il click di LibrePods); un evento diverso
  lo sostituisce e i colpi del contatto e del rilascio restano; `null` spegne tutto (contatto, rilascio, grana, click)
  perché un controllo che ha il suo colpo non ne deve avere tre in più. `longClickHaptic = null` per chi ha un colpo suo
  (un menu che suona `Open` solo se si apre).
- **`enabled = false`** su un pulsante: nessun gesto, nessun seguire il dito.

**Trappole.**

- *Sintomo:* il pulsante cresce ma il fondo e gli angoli restano fermi (per anni `FluidButton` rimpiccioliva la sua
  etichetta dentro un contenitore fermo, e nessuno lo vedeva: era il 2,6% di una parola). *Causa:* `fluidPressable`
  messo **dopo** `clip`/`background`. Va prima.
- *Sintomo:* un tasto di vetro rifrange un fondo sbagliato mentre cresce. *Causa:* una scala messa da un antenato
  sopra un nodo di vetro. La trasformazione del tasto di vetro sta nel `layerBlock` di `drawBackdrop`
  (`glassControlSurface`), mai in un `graphicsLayer` sopra: per questo il vetro si guida con `pressState`.
- *Sintomo:* in un `LazyColumn` ogni riga ha un pulsante e la lista «non scorre più» partendo da lì. *Causa:*
  `HoldsFinger` fa quello che dice. Vedi la sezione successiva: `grip = YieldsToScroll`, o parti da fuori dal pulsante.
- *Sintomo:* il bagliore e il click di un tasto di vetro vanno a ritmi diversi. *Causa:* due `FluidPressState`
  diversi. Un dito, una corsa: uno stato solo condiviso.
- *Sintomo:* il click arriva due volte. *Causa:* un secondo `clickable` sullo stesso nodo (o sul genitore) che legge
  lo stesso rilascio; il gesto consuma il rilascio quando clicca, un genitore cliccabile non lo vede.

**Non usarlo quando.** Per una riga di una lista raggruppata (`fluidRowPressable`: una riga che si rimpicciolisce
rompe la sagoma del gruppo). Per un controllo il cui puntatore ha già una logica sua (slider, interruttore, scrubber).

**Versione.** `FluidPressState` e `Expand` dalla 2.11.0; il pulsante che tiene il dito dalla 2.12 (con 100 ms di
attesa) e dalla 3.0 **senza attesa**, come LibrePods; `grip` e `ExpandRound` dalla 3.0; il tetto del tasto tondo
(24 dp) dalla 3.0. `LocalFluidPressImmediate` è `@Deprecated` e **non fa più niente** dalla 3.0: si toglie.

## `FluidPressFeedback`, `FluidPressState`, `rememberFluidPressState`

**Cos'è.** `FluidPressFeedback` dice *come* un elemento risponde (la sagoma, non il gusto); `FluidPressState` è la
corsa della pressione più la strada del dito, condivisibile fra chi la guida e chi la disegna.

```kotlin
data class FluidPressFeedback(
  val pressedScale: Float = DefaultYieldScale,   // 0,974: quanto cede una superficie
  val expands: Boolean = false,                  // cresce invece di cedere
  val followsFinger: Boolean = false,            // deriva + allungamento
  val dragHaptics: Boolean = false,              // grana mentre il dito scorre
  val maxScale: Float = TextMaxScale,            // 0,10 (frazione, solo se expands)
  val longSideCapDp: Float = TextLongSideCapDp,  // 16
)
```

| valore | cosa è |
|---|---|
| `FluidPressFeedback.Expand` | tasti con testo: cresce 10%, segue il dito, grana |
| `FluidPressFeedback.ExpandRound` | tasti tondi solo icona: cresce 50% (tetto 24 dp); `maxScale` 0,5, `longSideCapDp` 24 |
| `FluidPressFeedback.yielding(scale)` | superfici: cede fino a `scale` (0,974) e sta ferma |
| `FluidPressFeedback.None` | nessuna trasformazione: per chi si disegna la pressione (un vetro, una pastiglia) |
| `FluidPressFeedback.DefaultYieldScale` | 0,974: «la cessione storica» |

Per tarare un tasto tondo largo senza inventare un tipo: `FluidPressFeedback.ExpandRound.copy(longSideCapDp = 16f)`.

**`FluidPressState.progress`**: 0 a riposo, 1 premuto; le molle escono di poco da entrambi i lati. È l'unica cosa
pubblica dello stato: chi disegna la legge **in fase di disegno** (`drawBehind`, `graphicsLayer { }`), mai in
composizione, o un tasto premuto ricompone a ogni fotogramma. Il resto (`lean`, `glow`, `origin`) è interno: la deriva è
una *somma di spostamenti* con la sua molla critica di ritorno, il bagliore sta dove il dito è sceso più la strada fatta.

```kotlin
val press = rememberFluidPressState(FluidPressFeedback.Expand)
Box(
  Modifier
    .fluidPressable(onClick = onOpen, pressState = press)     // guida
    .glassControlSurface(/* … */ pressState = press),         // disegna: stessa corsa, stesso dito
)
```

**Comportamento.**

- `rememberFluidPressState` legge l'aptica del tema, il movimento ridotto e la grana (`LocalFluidHapticGrainConfig`) e
  li aggiorna a ogni composizione con un `SideEffect`: cambiare `feedback` o il movimento ridotto riconfigura le molle
  senza ricreare lo stato.
- Una nuova pressione durante il ritorno lo interrompe e risale da dove si trova (un doppio tap non aspetta il primo);
  una pressione a dito già giù non fa niente.
- Il gesto non segue un secondo dito: finisce quando il primo si solleva.
- Fuori da un `FluidTheme` l'aptica è `NoOpFluidHaptics`: la pressione funziona, non vibra.

**Trappole.** Passare `feedback` a `fluidPressable` insieme a un `pressState` non fa niente (vale quello dello stato).
Leggere `press.progress` in composizione fa ricomporre a ogni fotogramma.

## `FluidPressGrip`: di chi è il dito

**Cos'è.** Quando un pulsante sta dentro qualcosa che scorre, qualcuno deve decidere di chi è il dito che è sceso sul
pulsante. È una scelta di **chi costruisce** il pulsante (dipende da dove sta), non un'impostazione.

| | `HoldsFinger` (di serie) | `YieldsToScroll` |
|---|---|---|
| a che serve | un pulsante da solo in una pagina che scorre: per scorrere si parte **fuori** da lui, e un fuori c'è sempre | un pulsante in una fila che scorre a sua volta (chip di un filtro, pallini di un selettore), o ripetuto su ogni riga di una lista o di un pager: non c'è un fuori da cui partire |
| pressione e vibrazione | al contatto, come sempre | al contatto, come sempre |
| spostamento del dito | **consumato** nel passaggio Main, prima di ogni antenato, appena supera un tremito | **mai consumato** |
| la pagina sotto | non parte: `LazyColumn`, `verticalScroll`, pager, back a scorrimento non scorrono | parte; appena un antenato prende il gesto il pulsante si scioglie (cala, smette di seguire, suona `PressRelease`) e **non clicca** |
| il click | di `clickable` per un tocco fermo; **del gesto** per un dito che ha trascinato, se si solleva sul pulsante + 16 dp | sempre di `clickable`, mai del gesto |
| oltre il margine | il pulsante si scioglie, solo `PressRelease`, nessun click | — |

**Come decide `HoldsFinger`** (`FluidPressGesture`, pura, provata in `FluidPressTest`):

1. **Possesso.** Il gesto *prende* il dito quando la strada percorsa (somma delle lunghezze: un dito che va e torna
   conta due volte) supera `claimDistance` = metà dello slop del sistema, o quando il dito esce dall'area in cui
   `clickable` conta ancora un click. Da lì ogni cambio è consumato e `clickable` ha annullato.
2. **Perché la metà e non lo slop intero.** Le pagine che scorrono usano lo slop del sistema e decidono se il dito è un
   trascinamento *dopo* averlo letto; il pulsante sta sotto di loro nel passaggio Main, quindi consuma prima: non può
   arrivare dopo. Il tremito di un tocco vero sta sotto la soglia e lì il consumo non parte (resta il click di
   `clickable`: un tocco è esattamente quello di LibrePods). Per un mouse la soglia è zero.
3. **Click al sollevamento** (`clicks`): sì se il pulsante tiene il dito, il click lungo non è scattato, `clickable`
   non cliccherebbe e il dito è dentro il pulsante + margine. Conta **solo dove si solleva**, anche dopo aver vagato
   lontano e tornato. Un tocco mai mosso non è un click del gesto: due sorgenti per lo stesso tocco sono due click.
4. **Click lungo.** Scatta a `longPressTimeoutMillis` del sistema, **una volta sola**, se il dito è rimasto entro lo
   slop dal punto in cui è sceso (slop sulla strada sullo schermo, non sulla posizione sul tasto); tornare al punto di
   partenza non lo riarma. Anche a dito fermo: un timer sul prossimo scadere, perché non arriva nessun evento. Dopo, il
   rilascio è **consumato** (così `clickable` non clicca) e non suona `PressRelease`: quel momento ha già il suo colpo.
5. Un cambio già consumato da un discendente (un interruttore dentro il pulsante) o da un antenato nell'Initial (il
   relè di un menu che ha preso il dito) **finisce il gesto**; il mouse col tasto destro non preme; la rotella non è
   un movimento; se il pulsante esce dalla composizione a gesto in corso, la pressione si scioglie **senza vibrare**.

```kotlin
// Un chip in una LazyRow: cede il dito alla fila.
FluidButton(text = "Filtro", onClick = { /* … */ }, grip = FluidPressGrip.YieldsToScroll)
```

**Trappole.** Un pulsante ripetuto su ogni riga di una lista che scorre, o in un pager, **deve** passare
`YieldsToScroll`, altrimenti ogni riga è un punto da cui non si può scorrere. Righe e card non c'entrano: cedono già.
Il back a scorrimento di una pagina non parte se il dito scende su un pulsante `HoldsFinger` (è voluto: «per scorrere
si parte fuori dai pulsanti»).

**Versione.** Dalla 3.0 (`FluidPressGrip`). Prima, dalla 2.12, il possesso c'era ma con 100 ms di attesa.

## `fluidRowPressable`

**Cos'è.** La risposta di una riga dentro una lista raggruppata: **non scala mai**, perché una riga che si
rimpicciolisce in una pila di righe uguali rompe la sagoma del gruppo. Come iOS, **tinge lo sfondo** finché il dito
è giù.

```kotlin
Modifier.fluidRowPressable(
  onClick = { /* … */ },
  haptic = FluidHapticEvent.Tap,                       // null per chi ha un colpo suo (un interruttore)
  longClickHaptic = FluidHapticEvent.GestureStart,     // null se il colpo lo suona il menu che si apre
  role = Role.Button,                                  // nullo di serie: una riga interruttore ha il suo ruolo
)
```

**Comportamento.** Velo `onSurface` a 0,07 (`highlightColor`): sale con `instant`, scende con `fadeOut(260)` (così un
tap rapido non sembra un lampo: il velo resta visibile almeno quanto la dissolvenza). Con un mouse o un trackpad la riga
sotto il puntatore si accende a metà (0,5): dice cosa aprirebbe cliccando. Il fuoco da tastiera è un bordo di 2 dp
dell'accento lungo la riga. `animateFeedback = false` spegne il velo (non il click). `shape` per le righe d'angolo.

**Trappole.** `role`: una riga che *è* un interruttore o una scelta ha già il suo ruolo detto da chi la costruisce, un
secondo ruolo qui lo contraddirebbe. Una riga interruttore passa `haptic = null` perché suona `ToggleOn`/`ToggleOff`.

## `FluidMotion`: il vocabolario

**Cos'è.** `object FluidMotion`, l'unico posto dove vivono molle, durate e curve. I token sono espressi come
**`response`** (il tempo in secondi che una molla critica impiega ad arrivare), come in SwiftUI, e convertiti nella
rigidezza di Compose con `stiffnessForResponse(response) = (2π / response)²`. È esatto, perché il solver di Compose
usa `√stiffness` come pulsazione. Tenere i token in `response` è ciò che impedisce al movimento di derivare.

**Perché non scrivere mai una molla a mano** (`spring(0.9f, 300f)`, un `tween(130, …)`, un `animate*AsState` senza
spec): (1) il numero scritto nel componente è un metro che nessuno vede più, e per pillola, slider e interruttore un
numero spostato è un metro cambiato; (2) la fase 10 della 3.0 ha tolto tutti i numeri scritti a mano, **con gli stessi
valori di prima**, dando loro un nome (`FluidMotionTokensTest` rifà l'espressione come stava scritta e confronta la
traiettoria ogni 25 ms); (3) un `spring` su `Dp`/`IntOffset`/`IntSize` con la soglia di serie continua a girare ben
oltre l'ultimo pixel visibile e, con più animazioni sovrapposte, dà quel tremolio che si legge come «glitch». Un grep di
`spring(`, `tween(` e `keyframes` fuori da `FluidMotion.kt`, dal renderer, da Fluid-physics, dalle molle dei tasti
(`FluidPressSprings`) e dalle pagine non deve trovare niente: **un movimento nuovo si aggiunge a `FluidMotion`**, non al
file del componente.

### Quale token per cosa

Rigidezza = `(2π/response)²`, calcolata: i numeri sono quelli che arrivano a `spring()`.

| token | smorzamento · risposta (rigidezza) | per cosa |
|---|---|---|
| `FluidMotion.instant()` | `DampingChrome` 1 · 0,20 s (~987) | micro-feedback che deve arrivare **prima** che il dito si sollevi; la pressione che cede; l'attacco di un velo |
| `FluidMotion.snappy()` | 1 · 0,30 s (~439) | stati di pressione, interruttori, dissolvenze della chrome |
| `FluidMotion.standard()` | `DampingStandard` 0,9 · 0,38 s (~273) | il cavallo da tiro: cambi di lista, selezione, la maggior parte di entrate e uscite |
| `FluidMotion.smooth()` | 0,9 · 0,50 s (~158) | superfici grandi: fogli, card che si espandono, hero |
| `FluidMotion.gentle()` | 1 · 0,62 s (~103) | schermo intero o lungo viaggio |
| `FluidMotion.fluid()` | `DampingFluid` 0,74 · 0,38 s | rimbalzo **visibile**, solo per elementi giocosi (il ritorno di una card che cede) |
| `ResponseTitle` | 0,26 s (~584) | il passaggio di consegne del titolo grande: abbastanza stretto da seguire un dito, abbastanza lento da leggersi (nessuna `fun` pronta: si usa con `tuned`) |

Gli smorzamenti: **`DampingChrome` (1, critico)** per tutto ciò che è chrome (barre, sfondi, opacità): *una barra di
navigazione che rimbalza è il segno più rumoroso di «questo non è premium»*; `DampingStandard` (0,9) è «un sospiro di
assestamento», fisico senza sembrare elastico; `DampingFluid` (0,74) solo dove il rimbalzo è il carattere. Le costanti
`ResponseInstant`…`ResponseGentle` sono **rigidezze** (`Float`), non secondi: si passano come `stiffness` a `dp()`,
`tuned()`, `pixels()`.

```kotlin
val offset by animateDpAsState(target, animationSpec = FluidMotion.dp(), label = "offset")          // standard
val x by animateFloatAsState(target, animationSpec = FluidMotion.snappy(), label = "x")
val drag = remember { Animatable(0f) }   // un trascinamento che torna a zero, in pixel
LaunchedEffect(released) { drag.animateTo(0f, FluidMotion.pixels(FluidMotion.DampingChrome, FluidMotion.ResponseSnappy)) }
val enter = FluidMotion.tuned<Float>(if (entering) FluidMotion.DampingStandard else FluidMotion.DampingChrome, FluidMotion.ResponseStandard)
```

### Tipi, curve e durate

| token | cosa |
|---|---|
| `dp(damping, stiffness)`, `intOffset(...)`, `intSize(...)` | molle con la **soglia giusta** per quel tipo (di serie `standard`) |
| `pixels(damping, stiffness)` + `PixelThreshold` (0,5) | per una distanza in pixel (un trascinamento che torna, uno scatto di scorrimento): sotto mezzo pixel è ferma |
| `tuned<T>(damping, stiffness)` | smorzamento e rigidezza **dai token**, scelti a tempo di esecuzione |
| `flick<T>()` | ~0,16 s senza rimbalzo: la molla di serie di Compose (rigidezza 1500) con un nome, per un lavaggio di selezione o l'alfa di una lente |
| `EaseOut` | `(0,16; 1; 0,3; 1)` — per ciò che entra |
| `EaseInOut` | `(0,4; 0; 0,2; 1)` — dissolvenze incrociate e colori |
| `EaseIn` | `(0,4; 0; 1; 1)` — per ciò che esce: parte subito, l'uscita non è mai appiccicosa |
| `EaseEmphasized` | `(0,2; 0; 0; 1)` — partenza decisa, assestamento lungo e calmo, **nessun overshoot**: per pagine intere |
| `EaseSpringy` | stessa forma con ~3% di overshoot: **solo per elementi piccoli** (chip, icona, badge). Su una pagina il 3% sono trenta pixel di layout che crescono e tornano: si legge come la pagina che rimbalza |
| `fadeIn(220)`, `fadeOut(160)`, `crossFade(200)`, `color(240)` | tween con la curva giusta; i colori su un'ease semplice, «le molle fanno ballare le tinte» |

**Le durate (tween) sono riservate all'opacità e al colore**, dove una molla non compra niente: l'alfa non ha slancio
da conservare e la coda di una molla critica fa sembrare una dissolvenza più lenta della sua durata nominale.

### Durate di rotta, menu, goccia, componenti

Questi tempi sono **del componente che li usa**: un'app non li sceglie, li legge se deve allinearsi.

| gruppo | token principali | note |
|---|---|---|
| rotte | `DurationExpand` 300, `DurationCollapse` 260, `DurationPeer` 280, `DurationPeerFadeIn` 110, `DurationPeerFadeOut` 150, `PeerSlideFraction` 0,13 | 300 e non 340: i fotogrammi di un cambio di rotta sono i più costosi che l'app disegna. `DurationRouteFadeOut` è `@Deprecated` (nessuna pagina sfuma dalla 3.0). Per le pagine vedi la scheda delle pagine |
| menu | `menuOpen()` (ζ 0,62, ~0,36 s), `menuClose()` (critica, 0,38 s), `MenuPeakOvershoot` 0,084, `MenuInflationGain` 0,6, `MenuFadeOutMillis` 280, `MenuFaceTo` 0,35 | l'apertura sottosmorzata è il «pop»; la chiusura non oscilla. `MenuContent*` sono `@Deprecated` (usa `LiquidContent*`) |
| goccia (vetro liquido) | `dropOpen()` (ζ 0,72, k 500, ~0,28 s), `dropClose()` (k 158, ~0,50 s), `DropPoints` 48, `DropStiffnessNear` 1500, `DropStiffnessFar` 320, `DropDamping` 0,72, `DropEdgeTensionDefault` 0,2, `DropInflationGain` 0,55, `DropPeakOvershoot` 0,0384 | lo scelgono menu e morph; l'andata è stata portata da 0,42 a 0,28 s in 3.0.4 (k 224→500) |
| contenuto liquido | `LiquidContentOpenFrom/To` 0,4–0,75, `LiquidContentCloseFrom/To` 0,62–0,94, `LiquidContentMinScale` 0,86, `LiquidLensFullSpeedDp` 700, `LiquidLensFollowSeconds` 0,06 | il contenuto arriva a corpo quasi formato e **se ne va per primo** |
| pop-up e fogli | `PopoverFadeInMillis` 110, `PopoverFadeOutMillis` 240, `PopoverWindow*`, `PopoverOpenX/Y`, `PopoverExit`, `SheetSettle` | `PopoverOpenX` e `PopoverOpenY` hanno smorzamenti diversi apposta: due molle diverse danno lo squash, due uguali un pallone |
| barra a pillola | `TabTugReturn`, `GooeyMillis` 300, `GooeyPulse`, `PuckEnter`, `FoldCrossFade`, `TabBarFold`, `TabPuckLeadMillis` 160, `TabEnter*`, `tabEnterAlpha`, `tabEnterBlur` | stanno qui perché fra i numeri scritti a mano della fase 10 |
| goccia di Kyant / bagliore | `dropValue`, `dropVelocity`, `DropPress`, `DropScaleX/Y`, `GlowPosition`, `GlowPress` | `GlowPress` è la stessa molla `(0,5; 300)` dei tasti di LibrePods |
| respiro e giri | `pulse(...)`, `PulseMillis` 900, `PulseLowAlpha` 0,35, `PulseFillLowAlpha` 0,55, `loop(...)`, `SpinnerMillis` 900, `IndeterminateMillis` 1100 | vedi sotto |
| attese | `FastScrollerHideMillis` 1200, `ScrubberHoldMillis` 3000 | attese, non movimento |

**Il respiro** (`pulse`): un'andata e ritorno di `periodMillis` per fase (`RepeatMode.Reverse`), che parte da
`startOffsetMillis` *dentro il ciclo* (`StartOffsetType.FastForward`). Passando come offset il resto dell'orologio sul
periodo intero, tutti i pallini in scena battono **insieme** qualunque sia il momento in cui sono entrati (`FluidLiveDot`
lo fa con `System.currentTimeMillis() mod 2·periodo`). `PulseFillLowAlpha` (0,55) è più alto di `PulseLowAlpha` perché
un riempimento a 0,35 sembrerebbe il passo *da fare*. `loop` riparte da capo (`Restart`), lineare di serie.

**Trappole.**

- Le `Response*` sono rigidezze: `FluidMotion.tuned(DampingChrome, 0.3f)` **non** è `snappy()` (0,3 è una molla
  lentissima, rigidezza 0,3). Usa `ResponseSnappy`.
- `FluidMotion.instant<T>()` e gli altri generici usano la soglia di serie per `Float` (0,01). Per `Dp`, `IntOffset`,
  `IntSize` e pixel usa le varianti tipizzate, o la molla continua a muoversi sotto il pixel.
- Un `tween` o una curva nuovi *dentro* una schermata fanno sembrare quella schermata di un'altra app. Aggiungi il
  token a `FluidMotion` e il test a `FluidMotionTokensTest`.
- I tempi di un menu non si cambiano da un componente solo: il menu del morph, quello contestuale e l'avviso li
  condividono («un numero solo decida il "pop" di ogni finestra di menu»).

## `FluidMotionScheme`: Material con le nostre molle

**Cos'è.** `object FluidMotionScheme : MotionScheme`, in `ui/theme/Motion.kt`. `FluidTheme` lo passa a `MaterialTheme`
(`motionScheme = FluidMotionScheme`), così i componenti Material3 che leggono `MaterialTheme.motionScheme` (switch,
chip, fogli) si muovono sugli stessi tempi della chrome custom.

| slot Material | token |
|---|---|
| `defaultSpatialSpec` | `standard()` |
| `fastSpatialSpec` | `snappy()` |
| `slowSpatialSpec` | `smooth()` |
| `defaultEffectsSpec` | `snappy()` |
| `fastEffectsSpec` | `instant()` |
| `slowEffectsSpec` | `smooth()` |

Un'app non lo installa: arriva col tema. Un componente Material usato **fuori** da `FluidTheme` usa le molle di Material.

## Movimento ridotto: `FluidMotionPolicy`

**Cos'è.** «Una sorgente di verità» per il movimento che deve sparire quando la scala di animazione di sistema è a zero.
È un'impostazione di accessibilità, **non un suggerimento**.

```kotlin
@Immutable
data class FluidMotionPolicy(val durationScale: Float) {
  val reducedMotion: Boolean          // durationScale <= 0
  val allowDecorativeMotion: Boolean  // !reducedMotion
  val allowElasticity: Boolean        // !reducedMotion
  companion object { val Default = FluidMotionPolicy(durationScale = 1f) }
}
val LocalFluidMotionPolicy: ProvidableCompositionLocal<FluidMotionPolicy>   // statico, di serie Default
@Composable fun rememberFluidMotionPolicy(): FluidMotionPolicy              // legge MotionDurationScale
@Composable fun FluidMotionPolicyProvider(policy: FluidMotionPolicy = rememberFluidMotionPolicy(), content: @Composable () -> Unit)
```

**Come si spegne il movimento decorativo.** Due metà, e la prima **l'engine non la fa per te**:

1. **Installare la policy in radice.** `FluidTheme` **non** installa `FluidMotionPolicyProvider` (verificato: nessuno
   nell'engine, nel `sample` né nei `templates` lo fa; `LocalFluidMotionPolicy` resta `Default`, scala 1, per ogni
   componente sotto). Se vuoi che i componenti dell'engine (pressione, menu, pagine, respiro, scorrimento rapido,
   overscroll…) rispettino la scala a zero, avvolgi il contenuto:

   ```kotlin
   FluidTheme(settings = settings) {
     FluidMotionPolicyProvider {   // policy = rememberFluidMotionPolicy(): legge la scala di sistema
       AppContent()
     }
   }
   ```

2. **Leggerla nei tuoi componenti** e togliere ciò che è decorazione (non la risposta al dito):

   ```kotlin
   val policy = LocalFluidMotionPolicy.current
   val pulse = if (policy.allowDecorativeMotion) {
     rememberInfiniteTransition(label = "pulse").animateFloat(0f, 1f, FluidMotion.pulse(), label = "p")
   } else null   // segno fermo e PIENO: a mezza opacità che non si muove sembra spento
   ```

`rememberFluidMotionPolicy()` legge `MotionDurationScale` dal contesto della coroutine di composizione (è la scala
«Durata animazione» delle opzioni sviluppatore) e vale `max(scala, 0)`. Anche senza provider, **le animazioni di
Compose** (molle, tween) con scala 0 arrivano a destinazione da sole in un fotogramma: la policy serve per le decisioni
che non sono un'animazione (non creare la transizione infinita, dimezzare la crescita, togliere la deriva).

**Cosa cambia per i componenti con `reducedMotion`:**

- *Pressione:* crescita dimezzata (`ReducedAmplitude`), niente deriva né allungamento, molle critiche (`ReducedIn`
  `ResponseInstant`, `ReducedOut` `ResponseSnappy`), e anche i pulsanti **aspettano** che la salita arrivi (la corsa resta
  intera: a durata zero, un fotogramma premuto e uno rilasciato, mai uno saltato).
- *Respiro* (`FluidLiveDot`): segno fermo e pieno, **e non crea nemmeno la transizione infinita** (un'animazione che gira
  per niente tiene sveglio il thread di disegno per tutta la vita della schermata). Idem lo scintillio di `FluidVividCard`.
- *Pagine, menu, modali, notifica, overscroll, riordino, immagine a schermo intero, barra, slider, interruttore…:*
  leggono `LocalFluidMotionPolicy` (ci sono ~50 punti nell'engine); cosa cambia lo dice la scheda di ciascuno.
- **Eccezioni note** (R1, da `docs/10-coerenza.md`): lo squash di pillola e interruttore **non guarda** il movimento
  ridotto; era così e cambiarlo è una decisione, non una correzione.

**Trappole.** La più grossa è la prima metà: un'app che non installa il provider ha tutti i componenti dell'engine sul
`Default`. Poi: leggere `LocalFluidMotionPolicy` in composizione per un valore che cambia a ogni fotogramma (leggilo nel
disegno); `staticCompositionLocalOf` ricompone **tutto** l'albero se il valore cambia (accade solo se l'utente cambia la
scala di sistema).

## `Modifier.gooey`

**Cos'è.** Compone l'elemento in **un solo layer offscreen** e lo «gommifica»: lo sfoca di `blurRadiusPx` e poi riporta
il bordo netto con una soglia sull'alfa (matrice colore con pendenza 10, centrata a 0,5). L'offscreen è ciò che fa
fondere l'alfa di forme sovrapposte *prima* della soglia: due layer filtrati separatamente sarebbero due macchie sfocate
con una cucitura, non una fusione. Vendorizzato da FloatingTabBar di Elyes Mansour.

```kotlin
fun Modifier.gooey(blurRadiusPx: () -> Float): Modifier
```

**Uso.** `Modifier.gooey { progresso.value * piccoPx }` — il raggio è una lambda, letta in `graphicsLayer`, per non
ricomporre; per una fusione che va a zero e torna, `FluidMotion.GooeyPulse` (300 ms) ne è il progresso.

**Comportamento.** **Costo zero a riposo**: con raggio 0 (o prima di API 31, `isRenderEffectSupported()`) il modificatore
non fa niente — niente layer offscreen, niente filtro. Il raggio è quantizzato al pixel intero e il `RenderEffect` è
**memorizzato per raggio** (una transizione riusa un effetto per ogni raggio distinto, invece di allocarne uno a ogni
fotogramma).

**Trappole.** Offscreen = un layer intero in memoria per tutta la durata della fusione: tienilo su elementi piccoli.
Lo usa la barra flottante per la piegatura fra forma in linea ed estesa (`Modifier.gooey { gooeyBlurPx * gooeyProgress.value }`
con `FluidMotion.GooeyPulse`: picco a metà della piegatura, zero a riposo); è anche un'API pubblica per le app.

## `rememberCurrentDate`

**Cos'è.** «La data di oggi, che resta oggi.» `remember { LocalDate.now() }` sembra innocuo e non lo è: la composizione
sopravvive al passaggio in background, il valore si congela al primo disegno e dopo mezzanotte la schermata continua a
chiamare «oggi» il giorno prima.

```kotlin
@Composable fun rememberCurrentDate(): LocalDate
```

**Comportamento.** Rilegge il giorno in due momenti: a `ON_RESUME` (il caso normale, il telefono è stato in tasca tutta
la notte) e allo scoccare della mezzanotte successiva **più un secondo** (l'orologio di sistema e quello della coroutine non
sono lo stesso orologio: svegliarsi un istante prima rileggerebbe ancora ieri). In mezzo non gira nessun timer.
**Dalla 1.31.0.**

## Token, costanti, Defaults

- `FluidPressFeedback.DefaultYieldScale` = 0,974; costanti interne di `FluidPress.kt`: `TextMaxScale` 0,10,
  `RoundMaxScale` 0,5, `TextLongSideCapDp` 16, `RoundLongSideCapDp` 24, `ReducedAmplitude` 0,5, `LeanGain` 0,05,
  `StretchGain` 0,1 (tutte `internal`: si leggono nel sorgente, si tarano con `FluidPressFeedback.copy(...)`).
- `FluidPressClickSlack` = 16 dp (interna); `FluidPressGesture.ClaimFraction` = 0,5.
- Molle della pressione (interne, `FluidPressSprings`): `ExpandIn`/`ExpandOut` `(0,5; 300)`, `LeanReturn` `(1; 300)`,
  `YieldIn` `(DampingChrome; ResponseInstant)`, `YieldOut` `(DampingFluid; ResponseStandard)`, `ReducedIn`
  `(DampingChrome; ResponseInstant)`, `ReducedOut` `(DampingChrome; ResponseSnappy)`.
- `FluidMotion.*`: tutti i token delle tabelle sopra; l'elenco completo con le firme è in
  [../api/movimento.md](../api/movimento.md).
- `FluidMotionPolicy.Default` = scala 1. `FluidMotionScheme`: i sei slot sopra.

## Cosa è verificato dai test

- `FluidPressTest` (engine-ui, ~70 casi, JVM puro con orologio di fotogrammi finto): la corsa (un tap rapidissimo fa
  vedere la pressione intera per le superfici, i pulsanti rilasciano subito come LibrePods, nuova pressione durante il
  ritorno risale, a durata zero un fotogramma per parte), le molle di LibrePods, i numeri della crescita (10% / 50%,
  tetto 16 dp, tetto del tondo a 24 dp a ogni densità, `44 dp` resta ×1,5 con il pixel arrotondato), la deriva
  `m·tanh`, l'allungamento lineare, il dito come somma con ritorno critico, la grana per stile, il possesso del dito e il
  click del gesto (16 dp, mouse, `YieldsToScroll`), il click lungo (una volta sola, mai dopo lo slop).
- `FluidButtonDragTest`, `FluidButtonGripTest`, `FluidTouchTest` (`sample`, Robolectric con dito finto in un albero
  Compose vero): un pulsante in `LazyColumn`/`verticalScroll`/pager/back non fa scorrere la pagina e clicca una volta
  sola, un tap suona **esattamente** contatto, rilascio, click in quest'ordine, `YieldsToScroll` cede su `FluidButton`,
  `FluidGlassButton`, `FluidGlassIconButton`, `FluidCapsuleAction`, `haptic = null` non suona niente.
- `FluidMotionTokensTest`: ogni token rifatto come stava scritto nel componente e confrontato per traiettoria, un passo
  ogni 25 ms (il valore dei token non cambia di nascosto). `FluidMotionPolicyTest`: scala 0 spegne decorativo ed
  elastico, 0,5/1/2 no.
- **Non verificato da nessun test:** com'è la pressione *in mano* (la grana e la crescita si giudicano sul telefono), la
  fusione di `Modifier.gooey` e `rememberCurrentDate` al passaggio della mezzanotte.

## Dove guardare

| file (`engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/…`) | cosa c'è |
|---|---|
| `fluid/FluidPress.kt` | `FluidPressFeedback`, `FluidPressMotion` (la corsa), `FluidPressSprings`, `FluidPressState`, la trasformazione pura (`fluidPressTransform`, `fluidPressGrowth`), i numeri di LibrePods; intestazione con la licenza GPL-3.0 |
| `fluid/FluidButtonGesture.kt` | `FluidPressGrip`, `FluidPressGesture` (click lungo, possesso, click del gesto), `fluidButtonGesture`, `fluidButtonKeys` |
| `fluid/FluidInteractions.kt` | `fluidPressable` e `fluidRowPressable`: la catena di modificatori e il perché del suo ordine |
| `fluid/FluidMotion.kt` | `FluidMotion`: tutti i token |
| `fluid/FluidMotionPolicy.kt` | `FluidMotionPolicy`, `LocalFluidMotionPolicy`, `FluidMotionPolicyProvider` |
| `theme/Motion.kt` | `FluidMotionScheme` |
| `fluid/FluidGooey.kt` | `Modifier.gooey` |
| `fluid/FluidClock.kt` | `rememberCurrentDate` |
| `haptics/` | l'aptica che accompagna la pressione: [aptica.md](aptica.md) |
