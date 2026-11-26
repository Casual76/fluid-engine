<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/controlli.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Controlli

I controlli sono le cose che si toccano per dire all'app qualcosa: i **tasti** (`FluidButton`, i tasti di vetro, le
azioni di barra), le **scelte** (`FluidSegmentedControl`, `FluidChip`, `FluidColorDot`), i controlli **a gesto**
(`FluidSwitch`, `FluidSlider`, `FluidScrubber`), lo **stepper** (`FluidStepper`, dalla 4.0) e il **testo** (`FluidTextField`,
`FluidTextEdit`), più due accessori minuti (`FluidHairline`, `FluidRowValue`). Stanno tutti in `engine-ui`, pacchetto
`dev.antigravity.fluidengine.ui.fluid`, e si appoggiano ai tre motori delle altre schede: la pressione e l'aptica
([movimento.md](movimento.md)), il vetro (`glassSurface`, `GlassBackdropState`) e il tema (l'accento è sempre
`MaterialTheme.colorScheme.primary`: nessun controllo ha un colore suo).

Il tasto reagisce al contatto e trattiene il gesto fino al rilascio. Sul vetro Liquid la lastra cresce, cede lungo
il movimento e si schiarisce dal punto toccato. I controlli a gesto hanno invece una maniglia da afferrare. Il
pomello pieno di interruttore e cursore diventa una lente che mostra la pista e torna piena quando lo si lascia.
La maniglia prende subito il dito. Un trascinamento verticale partito dalla pista vuota scorre la pagina.
I tempi, la presa e le differenze fra i controlli sono nella [scheda dei gesti](controlli-gesto.md).

**La scheda è in due file**: questo (tasti, scelte, testo, regole comuni) e [controlli-gesto.md](controlli-gesto.md)
(`FluidSwitch`, `FluidSlider`, `FluidScrubber`, e le licenze dei port di Kyant0 e LibrePods).

Le righe di impostazioni che contengono un interruttore o un cursore (`FluidToggleRow`, `FluidSliderRow`) sono nella
scheda [contenuto.md](contenuto.md); qui si dice solo come interruttore e cursore si comportano *dentro* la riga.
Catalogo delle firme: [../api/controlli.md](../api/controlli.md).

Il selettore di colore (il pozzetto, la riga col pozzetto, la fila corta di campioni, il foglio con griglia, spettro e
cursori, il contagocce) ha la sua scheda, [colore.md](colore.md): usa `FluidColorDot` e `FluidSegmentedControl` di qui.

I campi che sanno cosa contengono (un numero con l'unità, un codice a caselle, una password con le regole, un valore
riservato, la copia che si cancella, il tasto incolla) hanno la loro scheda, [campi-speciali.md](campi-speciali.md): sono
tutti questo campo di testo, senza un aspetto loro.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| L'unica azione di cui parla la schermata (Salva, Continua) | `FluidButton` (`Filled`, di serie). Uno per schermata |
| Un'azione secondaria che merita comunque una superficie | `FluidButton(style = FluidButtonStyle.Tinted)` |
| Un'azione «di testo», senza sfondo (Annulla, Altro) | `FluidButton(style = FluidButtonStyle.Plain)` |
| Elimina, revoca, esci | `FluidButton(style = FluidButtonStyle.Destructive)`. Uno per schermata |
| Un pulsante che avvia un lavoro e deve dirlo | `FluidButton(loading = true)`: lo spinner prende il posto dell'icona, il pulsante non cambia larghezza |
| Un pulsante **ripetuto su ogni riga** di una lista che scorre, o dentro un pager | `grip = FluidPressGrip.YieldsToScroll` (su `FluidButton`, `FluidGlassButton`, `FluidGlassIconButton`). **Non** lasciare il default: il dito resterebbe del pulsante e la lista non scorrerebbe mai partendo da lì |
| Un'azione con testo sopra una foto, una mappa, un hero, una barra | `FluidGlassButton` |
| Un'azione solo icona sopra qualcosa (chrome, overlay, composer) | `FluidGlassIconButton` (taglie `FluidGlassSize.Compact`/`Regular`/`Large`) |
| Un tasto tondo di una misura che non è 40/46/54 dp (disco di un player, hero) | `FluidGlassIconButton(onClick, size = 76.dp)` (l'overload con `Dp`) |
| L'azione principale sopra una foto, una mappa, una barra (Fatto, Invia) | `FluidGlassButton(…, style = FluidGlassButtonStyle.Prominent)`: l'accento pieno quanto serve all'etichetta. Uno per schermata |
| Un tasto di vetro sopra una foto o un video, con contenuto chiaro e marcato | `style = FluidGlassButtonStyle.Clear` |
| Il tasto **pieno**, senza vetro, fra due tasti tondi di vetro («Play» bianco della pagina dell'album di Apple Music, iOS 26.4) | `style = FluidGlassButtonStyle.Filled(Color.White, ink = colorePagina)`: l'etichetta prende `ink` se ci fa 4,5:1, se no quella che si legge. Uno per schermata |
| Una riga di tasti tutti `Compact` (o `Large`), o tutti limpidi | la taglia o lo stile **d'ambiente**: `CompositionLocalProvider(LocalFluidGlassSize provides FluidGlassSize.Compact) { … }`, `LocalFluidGlassButtonStyle` |
| Due tasti che si dividono una riga (Annulla / Conferma) | `FluidGlassButton(…, Modifier.weight(1f), sizing = FluidButtonSizing.Flexible)` |
| Un'azione nella barra superiore di una schermata | `FluidBarAction`; con `actions = { … }` il tasto diventa il proprio menu |
| Un'azione a vetro con una pillola di più azioni | non è questa scheda: `FluidFloatingCapsule` / `FluidCapsuleAction` |
| Una scelta esclusiva fra 2–5 voci **corte** | `FluidSegmentedControl` |
| Filtri che non entrano su una riga, o che si accendono insieme | `FluidChip` (in una `LazyRow`) |
| La scelta di un colore d'accento | `FluidColorDot` |
| Acceso / spento | `FluidSwitch`; in una pagina di impostazioni la riga è `FluidToggleRow` ([contenuto.md](contenuto.md)) |
| Un valore in un intervallo, o una scelta fra pochi scatti | `FluidSlider` (`snapValues`); in impostazioni `FluidSliderRow` ([contenuto.md](contenuto.md)) |
| Dove andare in qualcosa che dura (brano, registrazione, cronologia di fotogrammi) | `FluidScrubber`. **Non** `FluidSlider`: la posizione è di un lettore che avanza da solo |
| Una barra che mostra un avanzamento e basta | `FluidProgressBar`: lo scrubber si tocca, quella no |
| Un valore (numero, testo) sul bordo destro di una riga | `FluidRowValue` (colore secondario, mai l'accento) |
| Un numero piccolo da aggiustare a passi (copie, persone, minuti) | `FluidStepper` nel `badge` della riga, accanto a un `FluidRowValue` (sezione «FluidStepper») |
| Un filetto tra due righe | `FluidHairline` (0,5 dp) |
| Testo a una riga, a più righe, una password | `FluidTextField` (`secret = true` per i segreti) |
| Una ricerca | `FluidSearchField`, non `FluidTextField` |
| Un editor con i pulsanti «grassetto», «titolo» | `FluidTextField` a `TextFieldValue` + `FluidTextEdit` |
| Un editor di **testo ricco** (grassetto, elenchi, citazioni, link) con barra sopra la tastiera, salvataggio continuo, HTML/Markdown | modulo opt-in `engine-richtext`: `FluidRichTextEditor` → [`moduli/richtext.md`](../moduli/richtext.md). **Non** `FluidTextEdit`, che è solo il Markdown a segni di un campo semplice |
| Più indirizzi, etichette o destinatari in un campo solo, ciascuno un gettone | `FluidTokenField(value, onValueChange, createToken)` (sezione «FluidTokenField e FluidSuggestionRow») |
| Un elenco di suggerimenti sotto il campo a gettoni | `FluidSuggestionRow` in pagina, mai un menu a comparsa |
| L'avatar o una foto come azione della barra superiore | `FluidBarAction(contentDescription, onClick) { FluidAvatar(…) }` |
| Il vecchio nome dell'interruttore | non `FluidGlassSwitch` (deprecato, delega): `FluidSwitch` |
| Vetro dentro un foglio o un dialog di piattaforma (finestra a sé) | `Modifier.fluidStaticGlassSurface`, non `glassControlSurface`: l'altra finestra non può campionare il fondale |
| Un tasto che si trasforma nel menu che apre | `FluidBarAction(actions = …)` in barra; altrove la scheda dei menu |

## Regole comuni

### Pressione, presa e aptica

Vale per `FluidButton`, `FluidGlassButton`, `FluidGlassIconButton`, `FluidBarAction`, `FluidChip` e `FluidColorDot`
(questi ultimi due con una differenza, sotto). È la ricetta dei pulsanti di LibrePods portata come codice (GPL-3.0,
vedi «Licenze» in [controlli-gesto.md](controlli-gesto.md)); tutti i numeri e i perché stanno in [movimento.md](movimento.md), qui solo quello che serve a
scegliere:

- **La pressione parte al contatto.** La capsula con testo cresce fino al 10%, con un tetto di 16 dp sul lato lungo.
  Il tondo Liquid cresce fino al **35%**. Il tondo Classic e il feedback ordinario `FluidPressFeedback.ExpandRound`
  arrivano al 50%. Il tetto per il tondo è di 24 dp sul diametro. Un tondo Liquid da 46 dp arriva a 62,1 dp.
- **La lastra Liquid segue il dito.** Il campo applica crescita, deriva e flessione al contorno. La flessione
  direzionale si attenua sulle capsule larghe. Trascinata, la lastra è **molle e senza riquadro** (F7.23): la deriva è
  l'elastico di iOS sulla distanza dal contatto, in ogni direzione uguale, e cresce sempre meno senza fermarsi; l'asse
  della flessione segue la tirata verso il dito e la velocità filtrata, e non salta da un fotogramma all'altro. I numeri
  in [movimento.md](movimento.md). L'icona del tondo cresce con la lastra e si sfoca fino a una sigma
  di 2,2 dp sullo schermo. Al rilascio la molla riparte dalla posa e dalla velocità raggiunte e la taglia compie
  un solo piccolo sottopasso, anche quando il dito si solleva prima della crescita completa. Con il movimento
  ridotto la crescita si dimezza, la flessione e la deriva si azzerano e la taglia rientra senza sottopasso.
- **Il tasto tiene il dito** (`FluidPressGrip.HoldsFinger`, di serie): finché il dito è giù la pagina sotto —
  `LazyColumn`, `verticalScroll`, pager, back a scorrimento — non parte. Per scorrere si parte *fuori* dai pulsanti.
  Un dito che ha trascinato clicca ancora se si solleva sul tasto più 16 dp di margine.
- **`FluidChip` e `FluidColorDot` cedono il dito** (`FluidPressGrip.YieldsToScroll`, **fisso**: non è un parametro):
  stanno in file che scorrono di lato, senza un «fuori» da cui partire. Se il dito fa scorrere la fila il chip si
  scioglie (cala, smette di seguire il dito, `PressRelease`) e non clicca.
- **Aptica a tre colpi**: `PressDown` al contatto, `PressRelease` al rilascio, `PressClick` al click. Per un tasto il
  default `haptic = FluidHapticEvent.Tap` *significa* `PressClick`; un altro evento lo sostituisce (e contatto e
  rilascio restano); **`null` spegne tutto** — contatto, rilascio, grana, click — per un tasto la cui azione ha già
  il suo colpo (un microfono, un menu). È l'unica eccezione, voluta, a «una vibrazione per momento».
- Un dito che trascina veloce sul tasto fa sentire la grana (`SegmentFrequentTick` per evento del puntatore); lento
  non suona niente.

### Stato disattivato

Un controllo spento resta del suo colore e perde opacità; per TalkBack la semantica dice «disattivato». Il grigio
Material cancellerebbe il rapporto di colore su cui è costruita la schermata.

| componente | opacità da spento |
|---|---|
| `FluidButton`, `FluidChip` | 0,4 |
| `FluidGlassButton`, `FluidGlassIconButton` | 0,38 (e `interactive = false`: tiene il materiale, perde ogni gesto) |
| `FluidSwitch`, `FluidSlider`, `FluidScrubber`, `FluidSegmentedControl` | 0,5 |

### Ripieghi del materiale

| situazione | cosa disegna il controllo |
|---|---|
| API < 31 (nessun `RenderEffect`) | `FluidSwitch`, `FluidSlider`, `FluidScrubber`: pillola bianca con ombra (2 dp) su pista piena, che cresce del 15% sotto il dito. `FluidSegmentedControl`: pista a pozzetto e goccia piena. `FluidChip`: pastiglia opaca. I tasti di vetro: vedi il vetro (`glassSurface`) |
| pagina senza canvas né chrome in scope | `FluidChip` e `FluidSegmentedControl`: pastiglia / pozzetto opachi. Interruttore, slider, scrubber: la pista da piegare c'è comunque, ed è la parte che conta |
| dentro un foglio/dialog di piattaforma | niente campionamento possibile: `fluidStaticGlassSurface` |

### Fondale campionato

I parametri `backdrop` hanno default diversi, ed è una trappola:

| componente | default |
|---|---|
| `FluidSwitch`, `FluidSlider`, `FluidScrubber`, `FluidGlassButton` | `currentCanvasBackdrop() ?: currentGlassBackdrop()`: il canvas della pagina se c'è, altrimenti la chrome |
| `FluidGlassIconButton` | `currentGlassBackdrop()`: **solo la chrome** (è il tasto della barra) |
| `FluidChip`, `FluidSegmentedControl` | non hanno il parametro: il canvas o, dove non c'è, il vetro della barra/capsula che li contiene; sotto API 31 niente |

Dentro una barra il canvas non c'è (la barra lo toglie) e resta il vetro della barra. Un vetro dentro un altro vetro
(un tasto su un pop-up, il pomello su una card di vetro) campiona il *materiale* del pannello e ci aggiunge la
propria ottica: da solo, senza chiederlo.

### Bersagli da 48 dp

| componente | misura disegnata | bersaglio |
|---|---|---|
| `FluidGlassIconButton` (e `FluidBarAction`, l'indietro di `FluidScreen`) | vetro 40 / 46 / 54 dp, o `size: Dp` | `max(vetro, 48 dp)` in layout; il dito lo colpisce tutto |
| `FluidGlassButton` | altezza 40 / 48 / 54 dp | sotto i 48 dp il layout occupa comunque 48 dp d'altezza (`fluidTouchTarget`); il dito lo colpisce tutto |
| `FluidChip` | alto almeno 48 dp (`defaultMinSize`) | 48 dp |
| `FluidColorDot` | cerchio 30 dp | `max(48 dp, size + 10 dp)` |
| `FluidSlider`, `FluidScrubber` | pista 6 dp | riga alta 48 dp |
| `FluidSwitch` | 64 × 28 dp | l'alone che Compose aggiunge attorno a un controllo < 48 dp (il tocco nel margine commuta) |
| tasto «cancella» di `FluidTextField` | disegno 17 dp in 28 dp | 48 dp, senza occupare spazio |
| `FluidSegmentedControl` | alto 32 dp | 32 dp (è una pista, non un tasto) |
| `FluidButton` | altezza minima 50 / 42 / 32 dp | l'altezza è un **minimo**, non toccato oltre |

Nei tasti di vetro il gesto e l'azione di TalkBack occupano il **riquadro misurato**. Il dito nel margine attorno
alla lente clicca anche quando l'app riduce `ViewConfiguration.minimumTouchTargetSize`. Il bagliore legge il punto
di contatto nelle coordinate del vetro. Vale per ogni taglia, per le misure libere, per i tasti con testo e per
Indietro. `FluidGlassButtonStylesUiTest` prova anche il tocco S5 a 2 dp fuori dal disco `Compact`.

### TalkBack

| componente | nodo |
|---|---|
| `FluidButton`, tasti di vetro | `Role.Button`; il nome è il testo (o il `contentDescription` dell'icona nel contenuto) |
| `FluidChip` | `Role.Button` + `selected` |
| `FluidColorDot` | `Role.RadioButton` + `selected`, nome = `label` |
| `FluidSegmentedControl` | ogni segmento `Role.Tab` + `selected` dentro un `selectableGroup`; con `content` il nome è `label(option)` |
| `FluidSwitch` | `Role.Switch` (`toggleable`) **senza nome**; vedi le trappole |
| `FluidSlider` | `progressBarRangeInfo` + `setProgress`; nome e stato solo se li passi |
| `FluidScrubber` | un nodo solo: nome, valore a voce, intervallo, `setProgress` e due azioni (indietro/avanti di `keyStep`) |
| `FluidTextField` | il campo di testo; segreto = password; tasto «cancella» e occhio sono pulsanti con nome da `FluidLabels` |

Le parole che l'engine dice da sé (`clear`, `showSecret`, `hideSecret`) sono in `FluidLabels` (italiano di serie,
`FluidLabels.english()` per l'inglese): un'app tradotta le passa una volta alla radice.

---

## FluidButton

**Cos'è.** Il pulsante dell'engine: quattro stili che portano il peso con *quanto accento usano* (l'interfaccia ha un
solo accento), tre taglie, pressione che cresce. Differisce da quello Material in tre cose che si sentono senza
saperle dire: risponde al dito con tutto il corpo invece di una ripple, ha gli angoli continui
(`ContinuousCornerShape`), e da disattivato sfuma invece di diventare grigio.

**Firma essenziale.**

```kotlin
FluidButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  style: FluidButtonStyle = FluidButtonStyle.Filled,   // Filled | Tinted | Plain | Destructive
  size: FluidButtonSize = FluidButtonSize.Medium,      // Large | Medium | Small
  enabled: Boolean = true,
  loading: Boolean = false,
  fillWidth: Boolean = false,
  haptic: FluidHapticEvent? = FluidHapticEvent.Tap,    // null = silenzio totale
  grip: FluidPressGrip = FluidPressGrip.HoldsFinger,
  leading: (@Composable () -> Unit)? = null,           // icona 18 dp davanti al testo
)
```

Tutti i parametri: [../api/controlli.md](../api/controlli.md).

**Uso.**

```kotlin
Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
  FluidButton(text = "Continua", onClick = onNext, size = FluidButtonSize.Large, fillWidth = true)
  FluidButton(text = "Annulla", onClick = onCancel, style = FluidButtonStyle.Tinted, size = FluidButtonSize.Large, fillWidth = true)
  FluidButton(text = "Elimina", onClick = onDelete, style = FluidButtonStyle.Destructive)
}
```

**Comportamento.**

| stile | contenitore | testo |
|---|---|---|
| `Filled` | `primary` | `onPrimary` |
| `Tinted` | `primaryContainer` | `onPrimaryContainer` |
| `Plain` | trasparente | `primary` |
| `Destructive` | `errorContainer` | `onErrorContainer` |

Il `Destructive` è quindi un contenitore *tinto* di errore, non un rosso pieno.

| taglia (`FluidButtonDefaults`) | altezza minima | padding orizzontale | angoli | etichetta |
|---|---|---|---|---|
| `Large` | 50 dp | 22 dp | continui 14 dp | `bodyLarge` SemiBold |
| `Medium` | 42 dp | 18 dp | continui 12 dp | `bodyLarge` SemiBold |
| `Small` | 32 dp | 14 dp | capsula | `bodyMedium` SemiBold |

- **L'altezza è un minimo** (`heightIn(min)`): a scala del font 200% il pulsante cresce per tenere intera l'etichetta
  invece di tagliarla. A 100% non cambia niente.
- **`loading`**: il pulsante smette di rispondere (`enabled && !loading`) ma **non sfuma** (l'opacità dipende solo da
  `enabled`); lo spinner (16 dp in un riquadro da 18) prende il posto di `leading` e *non* sostituisce il testo, così
  la larghezza non cambia e la riga non si ridispone nel momento in cui parte il lavoro.
- **La pressione sta fuori da clip e sfondo** (dalla 2.11.0): è il contenitore che cresce. Prima restava dentro e
  cedeva solo l'etichetta, il contenitore non si muoveva mai.
- Colore, `Role.Button`, ordine dei modificatori: `modifier` → `fillMaxWidth` (se `fillWidth`) → opacità →
  `fluidPressable` → clip → sfondo → altezza minima → padding.

**Trappole.**

- Un contenitore che ritaglia a filo il pulsante (`clip` sul genitore, una `Row` con `clipToBounds`) ne taglia la
  crescita: il disegno esce dai limiti del tasto. Lascia margine attorno, o non ritagliare.
- Un pulsante dentro una riga di una lista lunga col default `HoldsFinger` fa sì che il dito partito su di lui non
  scorra la lista. Passa `FluidPressGrip.YieldsToScroll` (così fa già il «Riserva» di `FluidModelPickerPortal`).
- `haptic = null` toglie *anche* contatto e rilascio, non solo il click: usalo solo se l'azione ha già un suo colpo.
- Non c'è una variante solo-icona: per quella, `FluidGlassIconButton`.

**Non usarlo quando.** L'azione sta sopra una foto/mappa/hero (opaco sopra un'immagine è sbagliato: `FluidGlassButton`);
è ripetuta in una riga che deve restare «una riga» (la riga cede e non cresce: `FluidListRow` con `onClick`); è in una
barra (`FluidBarAction`).

**Versione.** Cresce dalla 2.11.0; dalla 3.0 è il pulsante di LibrePods (10%, tre colpi, tiene il dito) e ha `haptic` e
`grip`; l'altezza come minimo dalla 3.0.4. `FluidButtonSize.Large` (50 dp) esisteva già: la 3.0 lo ha solo fissato con
un test.

---

## FluidGlassIconButton, FluidGlassButton e FluidGlassSize

**Cos'è.** I tasti per tutto ciò che *galleggia sul contenuto*: la lente di vetro (`glassControlSurface`) più la stessa
pressione di `FluidButton`. `FluidGlassIconButton` è il tondo solo icona (chrome, overlay, composer);
`FluidGlassButton` è il pulsante con testo, con le stesse proporzioni e la stessa tipografia di `FluidButton`, ma la
superficie *trasmette* invece di coprire. Esistono come componenti perché le app se li riscrivevano a mano per avere
un'altra misura, e con la misura riscrivevano pressione, vetro e aptica: le tre cose che il tasto esiste per non far
riscrivere. Una taglia è un numero, non un componente nuovo.

**Firma essenziale.**

```kotlin
FluidGlassIconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = currentGlassBackdrop(),
  enabled: Boolean = true,
  selected: Boolean = false,
  shape: Shape = FluidCapsuleShape,
  onLongClick: (() -> Unit)? = null,
  size: FluidGlassSize = LocalFluidGlassSize.current,   // oppure l'overload con size: Dp; di serie Regular
  haptic: FluidHapticEvent? = FluidHapticEvent.Tap,
  longClickHaptic: FluidHapticEvent? = FluidHapticEvent.GestureStart,
  grip: FluidPressGrip = FluidPressGrip.HoldsFinger,
  style: FluidGlassButtonStyle = LocalFluidGlassButtonStyle.current,   // Glass | Clear | Prominent | Glass(glass) | Filled(fill, ink)
  content: @Composable BoxScope.() -> Unit,
)

FluidGlassButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  backdrop: GlassBackdropState = currentCanvasBackdrop() ?: currentGlassBackdrop(),
  enabled: Boolean = true,
  selected: Boolean = false,
  shape: Shape = FluidCapsuleShape,
  size: FluidGlassSize = LocalFluidGlassSize.current,
  haptic: FluidHapticEvent? = FluidHapticEvent.Tap,
  grip: FluidPressGrip = FluidPressGrip.HoldsFinger,
  leading: (@Composable () -> Unit)? = null,
  maxLines: Int = Int.MAX_VALUE,
  style: FluidGlassButtonStyle = LocalFluidGlassButtonStyle.current,
  sizing: FluidButtonSizing = LocalFluidButtonSizing.current,       // Fitted | Flexible
)
```

In SwiftUI: `style` è `buttonStyle(.glass / .glass(.clear) / .glassProminent / .glass(_:))`, la taglia d'ambiente è
`controlSize`, `sizing` è `buttonSizing`, la forma di serie (`FluidCapsuleShape`) è `buttonBorderShape(.capsule)`.

**Uso in una barra compatta.** Il contenitore raccoglie i tasti nello stesso campo. Passa la taglia, lo stile
e la larghezza che valgono per il gruppo. Il tasto principale sceglie `Prominent` per sé.

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import dev.antigravity.fluidengine.ui.fluid.*
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Share

@Composable
fun BarraConferma(onShare: () -> Unit, onCancel: () -> Unit, onConfirm: () -> Unit) {
  CompositionLocalProvider(
    LocalFluidGlassSize provides FluidGlassSize.Compact,
    LocalFluidGlassButtonStyle provides FluidGlassButtonStyle.Glass,
    LocalFluidButtonSizing provides FluidButtonSizing.Flexible,
  ) {
    FluidGlassContainer {
      Row(horizontalArrangement = Arrangement.spacedBy(FluidMetrics.GlassSpacing)) {
        FluidGlassIconButton(onClick = onShare) {
          FluidIcon(FluidIcons.Share, contentDescription = "Condividi")
        }
        FluidGlassButton("Annulla", onCancel, Modifier.weight(1f))
        FluidGlassButton("Conferma", onConfirm, Modifier.weight(1f), style = FluidGlassButtonStyle.Prominent)
      }
    }
  }
}
```

**Comportamento.**

- **Taglie** (`FluidGlassSize`, `lens` / `buttonHeight`): `Compact` 40/40 dp, `Regular` 46/48 dp, `Large` 54/54 dp.
  `touchTarget = lens.coerceAtLeast(48 dp)`; `iconSize = FluidMetrics.iconSizeIn(lens)` = 0,6 del lato al dp intero (24, 28, 32 dp).
  La ricetta della pressione vale **in proporzione** (crescita = frazione, deriva sul lato corto
  del vetro, bagliore Classic sul lato lungo): nessun numero in dp da riscalare, tranne il tetto di crescita dei tondi (24 dp
  in tutto).
- **Misura libera** (`size: Dp`): `size` è il lato del *vetro*; il riquadro è `max(size, 48 dp)`. Tra le due strade si
  sceglie dal tipo: `size = FluidGlassSize.Large` o `size = 76.dp`. Senza `size` vale l'overload con la taglia, `Regular`.
- **`FluidGlassButton`**: altezza `buttonHeight`, padding laterale 16 / 20 / 24 dp, icona `leading` 18 / 20 / 22 dp,
  etichetta `bodyMedium` (Compact) o `bodyLarge`, SemiBold, spazio fra icona e testo 8 dp. Colore del contenuto
  `onSurface`, e `onPrimaryContainer` se `selected`. `maxLines = 1` tronca con i puntini; senza tetto un'etichetta a
  capo **fa crescere** il tasto oltre l'altezza (minimo, non misura).
- **`selected`**: l'accento *si unisce al materiale* (overlay accento al 36%, bordo al 45%), non lo sostituisce con un
  riempimento: sopra una foto il vetro continua a far vedere la foto. Il contrasto segue il contrasto di sistema.
- **`onLongClick`** (solo icona): il click lungo suona `longClickHaptic` (default `GestureStart`; `null` se il click
  lungo ha il suo colpo, come un menu che suona `Open` solo se si apre); dopo un click lungo non c'è `PressRelease`.
- **Bagliore Liquid.** Parte al contatto e si estende dal dito fino a riempire la capsula in circa 330 ms. Il
  contributo superficiale bianco ha un massimo di 0,76 sul chiaro e 0,88 sullo scuro, con attenuazione al filo.
  Al rilascio conserva l'estensione raggiunta e si spegne entro 500 ms. Nel `FluidGlassContainer` la stessa
  sorgente raggiunge i ponti e le lastre vicine fino a 108 dp, con salita e discesa da 300 ms. Sulle lastre
  ferme questa variazione riguarda la passata di luce. Lo spostamento e la deformazione del tasto premuto
  possono aggiornare anche il corpo. In movimento ridotto la luce resta ancorata al contatto, con raggio
  pieno e intensità attenuata.
- Lo stato disabilitato sfuma a 0,38 e smette di rispondere tenendo il materiale.
- **Stili** (`FluidGlassButtonStyle`, F6.4). `Glass` è il vetro di sempre (stessi ingressi, stessi nodi). `Clear` è il
  vetro limpido (`FluidGlass.Clear`: niente smerigliatura, scurimento del 35% sotto): sopra foto e video, con contenuto
  chiaro e marcato. `Glass(glass)` prende un `FluidGlass` qualunque: `FluidGlass.Regular.tint(c)` è la tinta di Apple (la
  tonalità resta, la luminosità segue lo sfondo, l'etichetta segue il lato del vetro), `FluidGlass.Identity` è il tasto
  senza vetro (la pressione la disegna il gesto, che cede). `Prominent` (o `Prominent(tint)`) è l'azione principale:
  la tinta resta quella scelta (l'accento di serie), e **copre quanto serve** perché l'etichetta stia ad almeno 4,5:1
  sul fondo peggiore, nero o bianco, con un margine del 5% (`fluidProminentCoverage`: 0,92 per l'accento chiaro di
  serie, 0,67 per lo scuro; l'alfa della tinta non conta). L'etichetta prende l'inchiostro che sulla tinta si legge
  meglio (il quasi nero o il quasi bianco del tema; il nero o il bianco pieni sulle tinte di mezzo tono) e **non gira**
  col fondo: il tasto pieno è lui il fondo. Sopra una foto la tinta a metà opacità scritta a mano stava a 3,90:1 e
  3,20:1 (chiusura di F2); `Prominent` sta sopra 4,5 su ogni accento della palette (`FluidGlassButtonStyleTest`).
  `Filled(fill, ink)` (4.0, F8.7b) è il tasto **pieno**: una capsula di `fill`, senza vetro (la pressione la disegna il
  gesto, che cede, come per `FluidGlass.Identity`), come il «Play» bianco col testo del colore della pagina nell'album di
  Apple Music di iOS 26.4. L'etichetta e l'icona prendono `ink` se ci fa almeno 4,5:1 (`FluidProminentLabelContrast`),
  altrimenti l'inchiostro che sul pieno si legge meglio, come `Prominent` (`fluidFilledInk`, `F8p7bFilledButtonTest`).
- **Taglia, stile e larghezza d'ambiente.** Senza `size`, `style` e `sizing` il tasto prende `LocalFluidGlassSize`,
  `LocalFluidGlassButtonStyle` e `LocalFluidButtonSizing` (di serie `Regular`, `Glass`, `Fitted`: senza un fornitore
  niente cambia). L'icona discende dalla taglia: il tasto dà a una `FluidIcon` senza `size` 0,6 del suo vetro.
  **Fogli, popover, avvisi e menu dell'host** (`FluidGlassModalHost`, `FluidGlassSheet`) riportano i tre valori a quelli
  di serie: come in iOS 27, un foglio aperto da una barra `Compact` ha i tasti `Regular`.
- **`sizing`** (solo `FluidGlassButton`): `Fitted` è largo quanto l'etichetta; `Flexible` riempie la larghezza data
  (`fillMaxWidth`), con l'etichetta al centro. In una `Row` si dà a ciascuno `Modifier.weight(1f)`: il peso lo dà la riga.

**Trappole.**

- Un `FluidGlassIconButton` **senza `contentDescription` sull'`Icon`** è muto per TalkBack: il nodo è `Role.Button` ma il
  nome viene dal contenuto.
- Il default `backdrop` dell'icona è la chrome (`currentGlassBackdrop()`), non il canvas come per `FluidGlassButton`: su
  una pagina ambientale, un tasto *icona* fuori da una barra deve ricevere `backdrop = currentCanvasBackdrop() ?: currentGlassBackdrop()` se vuoi
  la lavata e non il corpo della pagina. Il `FluidGlassButton` lo fa da sé dalla 3.0.1 (prima rifrangeva il corpo della
  pagina, con dentro se stesso).
- Il tasto non è clippato: la crescita esce dal riquadro. Un genitore con `clip` la taglia.
- Una riga di tasti tondi vicini: con 16 dp fra due dischi la crescita capata a 24 dp non li fa toccare, con meno sì.
- Tasto in una lista che scorre: `grip = FluidPressGrip.YieldsToScroll` (stessa regola di `FluidButton`).
- `Prominent` non ha uno stato `selected`: la sua pellicola è la tinta, e la selezione (accento al 36%) la coprirebbe.
- `FluidBarAction` senza menu e l'indietro di `FluidScreen` seguono la taglia d'ambiente, quelli con menu no (sono
  `Regular` scritti): non fornire `LocalFluidGlassSize` attorno a una `FluidScreen`, ma attorno alla riga che la vuole.
- Un `Icon` di Material con la taglia scritta resta della sua taglia anche in un tasto `Compact`: per seguire, `FluidIcon`.

**Non usarli quando.** Serve il pulsante pieno di una form su fondo piatto (`FluidButton`); il tasto sta in una barra di
schermata (`FluidBarAction`, che li usa); serve una pillola con più azioni (`FluidFloatingCapsule`).

### Riflesso in movimento e accessibilità

Da Android 13 il riflesso delle lastre Liquid che disegnano la luce in una passata separata può seguire
l'inclinazione. `LocalFluidGlassMotionLight` è **false di serie**. Forniscila sopra `FluidTheme` perché la finestra
condivida un ambiente. L'accelerometro si attiva alla presenza della prima lastra compatibile. Il filtro smette
di pubblicare variazioni a posa stabile. L'ascoltatore si stacca all'uscita dell'ultima lastra, in secondo piano,
in risparmio energetico, con movimento ridotto o quando l'utente spegne l'opzione.

Conserva la scelta nello stato dell'app. `fluidAppearanceSection` mostra la riga quando riceve valore e callback.

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.antigravity.fluidengine.foundation.EngineSettings
import dev.antigravity.fluidengine.ui.fluid.FluidScreen
import dev.antigravity.fluidengine.ui.fluid.fluidAppearanceSection
import dev.antigravity.fluidengine.ui.glass.field.LocalFluidGlassMotionLight
import dev.antigravity.fluidengine.ui.theme.FluidTheme

@Composable
fun Aspetto(
  settings: EngineSettings,
  onSettingsChange: (EngineSettings) -> Unit,
  motionLight: Boolean,
  onMotionLightChange: (Boolean) -> Unit,
) {
  CompositionLocalProvider(LocalFluidGlassMotionLight provides motionLight) {
    FluidTheme(settings = settings) {
      FluidScreen(title = "Impostazioni") {
        fluidAppearanceSection(
          settings = settings,
          onSettingsChange = onSettingsChange,
          glassMotionLight = motionLight,
          onGlassMotionLightChange = onMotionLightChange,
        )
      }
    }
  }
}
```

`FluidTheme` fornisce il contrasto e il movimento di sistema. Per le anteprime si possono usare
`LocalFluidSystemContrast`, `LocalFluidReduceTransparency` e `LocalFluidMotionPolicy`. La trasparenza ridotta
aumenta l'opacità della pellicola Liquid. Il contrasto aumentato rinforza anche il contorno. Una Activity in
multi-finestra che perde il fuoco attenua i riflessi e rende il velo più pieno, con inchiostro ancora leggibile.
La tab Controlli della galleria permette di mettere alla prova le prime tre impostazioni sullo stesso tasto.

---

## FluidBarAction

**Cos'è.** L'azione della barra superiore: un `FluidGlassIconButton` Regular con l'icona nell'inchiostro del testo (28 dp, `FluidMetrics.BarIconSize`; fino alla 3.x
in tinta d'accento a 22) sul vetro condiviso della barra, quindi con un bordo fisico senza pagare un altro passaggio di sfocatura. In una barra
non si usa `FluidGlassIconButton` a mano. L'icona la mette l'app e **la taglia arriva dal posto**: `icon = FluidIcons.Share` (il set dell'engine, al tratto)
o un'icona Material, che continua a funzionare; l'accento resta a chi passa `tint` (l'azione principale, `error` per quella distruttiva). Il vetro
sta a 16 dp dal bordo (`FluidMetrics.ScreenEdgeMargin`) e a 8 dal vetro vicino (`GlassSpacing`); la freccia Indietro misura 25 dp
(`BackIconSize`) e il titolo agganciato parte a 86 dp dai bordi.

**Firma essenziale.**

```kotlin
FluidBarAction(
  icon: ImageVector,
  contentDescription: String?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  actions: (() -> List<FluidContextAction>)? = null,   // le azioni in più: il tasto diventa il menu
  opensOn: FluidMenuOpensOn = FluidMenuOpensOn.LongPress,
  tint: Color = Color.Unspecified,                      // 4.0: Unspecified = inchiostro del testo; l'accento si chiede qui
)
```

**Uso** (da `sample`, `MenusDemo`).

```kotlin
FluidBarAction(
  icon = FluidIcons.Filled.Star, contentDescription = "Preferiti",
  onClick = { /* tocco: l'azione di sempre */ },
  actions = { demoActions },                 // tenuto premuto: il tasto diventa il menu
)
FluidBarAction(
  icon = FluidIcons.MoreVert, contentDescription = "Altro", onClick = {},
  actions = { demoActions }, opensOn = FluidMenuOpensOn.Tap,   // il tocco apre il menu; onClick non è chiamato
)
```

**Comportamento.**

- Senza `actions` è un tasto di vetro e basta.
- Con `actions` il tasto **diventa** il menu: tenuto premuto (`LongPress`, di serie) il suo rettangolo si apre nel pannello
  e il dito passa al menu (si sceglie trascinando e rilasciando); un tocco resta `onClick`, così chi non scopre il menu non
  perde niente. Con `FluidMenuOpensOn.Tap` il tocco apre il menu, `onClick` non viene chiamato (per l'azione il cui unico
  mestiere è il menu: i tre puntini, «Ordina»), e il tenuto premuto apre comunque portando il dito sul menu.
- L'apertura è **sincrona nel gesto**: il tasto si nasconde nello stesso fotogramma in cui la finestra compare sulla sua
  sagoma. Un solo `Open` (dal click lungo, e solo se il menu si apre davvero; con `Tap` è l'`haptic` del tasto).
- Dentro la barra ancorata di una pagina il tasto dichiara la posizione della sua lente e, mentre le pagine cambiano,
  spegne il proprio vetro: lo porta una superficie sola di Fluid-physics che fa diventare i tasti della pagina che esce
  quelli della pagina che arriva.

**Trappole.** Il menu ha bisogno di un host di modali in scope (`LocalFluidGlassModalHostState`): senza, `actions` non apre
niente e il tasto tiene solo `onClick`. Una lista di `actions` vuota non apre. `contentDescription = null` rende il tasto
muto; passa sempre un nome (è anche il titolo del menu per TalkBack).

**`FluidBarAction` con un contenuto tuo** (3.1.0). `FluidBarAction(contentDescription, onClick, modifier, enabled, actions, opensOn) { content }` è un overload che al posto dell'icona disegna **il tuo
contenuto**: l'avatar dell'utente, un mosaico di account, una foto. È la stessa lente, la stessa pressione e lo stesso menu, e per il menu la stessa regola: con `actions` il tasto **diventa** il menu e il menu si richiude nel tasto.
Il caso per cui esiste: l'avatar in alto a destra che apre il menu degli account.
- **Il `content` è disegnato due volte**: nel tasto, e come faccia del tasto mentre il menu si apre e si chiude (l'host lo porta lungo il primo tratto del viaggio). Quindi dev'essere **solo disegno**: un `FluidAvatar`, un
  mosaico, un'immagine già decodificata; nessun effetto collaterale (niente `LaunchedEffect` che carica, niente stato catturato che cambi mentre il menu è aperto).
- **Misure.** La lente è quella di un tasto della barra, 46 dp. Un avatar da `FluidBarActionDefaults.ContentSize` (32 dp) lascia 7 dp di vetro per lato. Il bersaglio misura 48 dp.
- **TalkBack: il nome è `contentDescription`.** Il contenuto va **muto** (un `FluidAvatar` senza `contentDescription`): un avatar che dicesse il suo nome accanto produrrebbe «Alessio Pampaloni, Account, pulsante». Senza
  `contentDescription` il tasto non ha nome, e va bene solo se è il contenuto a darlo. Nel menu la faccia non porta nomi: il tasto non c'è più e due nodi con lo stesso nome sono rumore.
- Questo overload e quello con `icon` condividono il corpo (interno): stesso comportamento, stesso albero per chi non cambia chiamata.

**Versione.** Lenti di vetro con bersaglio da 48 dp: 1.3.0; `opensOn` e il passaggio al pannello unico dei menu liquidi: 3.0
(il CHANGELOG non data l'arrivo di `actions`).

---

## glassControlSurface e fluidStaticGlassSurface

**Cos'è.** `Modifier.glassControlSurface` è «un vetro che si comporta da controllo»: ciò che sta sotto ai tasti di vetro,
e che serve per costruirne uno a mano senza riscrivere la pressione. La versione dipinta, per cui un controllo sembrava
vetro in uno screenshot e tasto in mano, non c'è più.

**Firma essenziale.**

```kotlin
@Composable
fun Modifier.glassControlSurface(
  backdrop: GlassBackdropState,
  shape: Shape = FluidCapsuleShape,
  tint: GlassTint = GlassDefaults.controlTint(),
  optics: GlassOptics = GlassDefaults.buttonOptics(),
  selected: Boolean = false,
  interactive: Boolean = true,
  depth: () -> Float = { 1f },
  frozen: () -> Boolean = { false },
  heldWhileMoving: () -> Boolean = { false },
  pressState: FluidPressState? = null,
): Modifier
```

**Comportamento.** Al contatto il vetro Liquid cresce nella geometria del campo. Il tondo da 46 dp
raggiunge 62,1 dp (×1,35). Il tasto con testo conserva il tetto di 16 dp sul lato lungo. Trascinando, la lastra
si allunga lungo la velocità e si comprime di traverso. Sul lato del dito si forma un piccolo
rigonfiamento. Il margine di cattura ospita la sporgenza e il filo segue la distanza dal nuovo contorno.
Nel `layerBlock` resta la traslazione che permette di campionare il fondale mentre il tasto si sposta.

Il contenuto ha un layer proprio. L’icona del tasto tondo cresce con il vetro e su API 31+ raggiunge una sigma visibile di 2,2 dp,
misurata sulla freccia di WWDC25 219. Il blur parte al contatto insieme alla crescita. Al distacco
si attenua subito, a circa 1,1 dp dopo 100 ms e 0,4 dp dopo 200 ms. A 333 ms l’icona è nitida. Il rilascio conserva la velocità della pressione anche se il dito si alza a metà crescita.
La taglia e la posizione passano una volta oltre il riposo. Con movimento ridotto la crescita è dimezzata,
il contorno mantiene le proporzioni e il ritorno si posa direttamente.


- `pressState`: la pressione del controllo, guidata dal suo `fluidPressable(pressState = la stessa)`. Il vetro risponde allora
  dal contatto e segue il dito anche fuori. Se è `null` il vetro segue il dito da solo e non consuma niente. Non passare lo
  stesso `FluidPressState` a due inseguitori.
- `interactive = false`: tiene il materiale, perde ogni gesto (decorativo o disabilitato), senza bisogno di un secondo
  componente più quieto.
- `depth`: peso ottico indipendente, per un controllo quasi invisibile a riposo che si ispessisce a richiesta.
- `selected`: l'accento si unisce al materiale (vedi sopra).
- `optics` di serie `GlassDefaults.buttonOptics()`: la lente di sempre a riposo, quella di LibrePods sotto il dito.

**Costruire un tasto a mano:**

```kotlin
val press = rememberFluidPressState(FluidPressFeedback.Expand)
Box(
  Modifier
    .glassControlSurface(backdrop = currentGlassBackdrop(), pressState = press)
    .fluidPressable(onClick = onClick, pressState = press, role = Role.Button),
)
```

**`fluidStaticGlassSurface`.** Per l'unico posto dove il vetro vivo non può andare: un foglio o un dialog, che la piattaforma
mette in una finestra a sé e che quindi non legge il `GraphicsLayer` di un'altra. I gradienti dipinti sono lì un ripiego
onesto, e una pessima idea ovunque esista un fondale vero. Chiaro o scuro lo decide `GlassDefaults.isDarkSurface()`;
`pressure: () -> Float` (0..1) dà la pressione.

**Trappole.** In una barra ancorata, il modificatore registra un pezzo (`FluidBarPiece`) per l'animazione fra pagine: non
metterlo in un `LazyColumn` dentro la barra.

**Versione.** `backdrop` obbligatorio e `fluidStaticGlassSurface` per la versione dipinta: 1.4.0 (BREAKING); `pressState` 2.11.0;
`frozen`/`heldWhileMoving` 2.10.0; `buttonOptics` come default e bagliore di LibrePods 3.0.

---

## FluidSegmentedControl

**Cos'è.** Il segmentato: una pista, le etichette stampate sopra, e una **goccia** che le cavalca. È la disposizione che
usava `FluidTabBar` e che il puck della barra flottante ancora usa, abbassata a 32 dp. Il segmento scelto non è colorato:
mentre lo si tiene è *visto attraverso il vetro*.

**Firma essenziale.**

```kotlin
FluidSegmentedControl<T>(
  options: List<T>,
  selected: T,
  onSelect: (T) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: (@Composable (option: T, selected: Boolean) -> Unit)? = null,   // un'icona al posto della parola
  label: (T) -> String,                                                   // obbligatoria, anche con content
)
```

**Uso** (dal `sample`, `ControlsTab`).

```kotlin
FluidSegmentedControl(
  options = listOf("Giorno", "Settimana", "Mese"),
  selected = segment,
  onSelect = { segment = it },
  label = { it },
)
```

**Comportamento.**

- **Costruzione in quattro strati**: (1) la pista, con le etichette stampate nel colore di riposo e registrate in un
  proprio layer; (2) una **seconda copia delle etichette, invisibile** (alfa 0, tinta d'accento), anch'essa registrata;
  (3) la goccia (`glassDrop`, lente 10/14 dp come il pomello dello slider), che a riposo è piena (bianca sul chiaro, grigio
  iOS sullo scuro) e sotto il dito rifrange pista *e* copia accentata, quindi l'etichetta sotto appare ingrandita e in
  accento; (4) l'etichetta scelta ridisegnata sopra la goccia piena (una faccia piena coprirebbe quella sotto), che sfuma
  tre volte più in fretta della pressione.
- **La pista**: una lastra statica (`GlassRole.Content`, il materiale di un gruppo di lista) dove c'è qualcosa da
  attraversare, altrimenti un pozzetto piatto; il velo `onSurface` al 7% sta sempre, perché la goccia bianca su una lastra
  chiara ha bisogno di una pista più scura per staccarsi. Angoli continui 9 dp, goccia 7 dp, margine interno 2 dp, altezza 32 dp.
- **Gesti**: un tocco su un segmento lo sceglie (pressione 0,96, `focusShape` capsula). Un dito sulla goccia la trascina; un
  dito che scende su un segmento **non scelto** e scivola di lato (superato lo slop orizzontale) si prende la goccia, che gli
  viene sotto, lo segue e **al rilascio sceglie dove si trova** («come iOS»); fermo è il tocco; in verticale è della pagina.
  Dal momento in cui la goccia prende il dito il segmento sotto non si preme più e non clicca.
- **Un dito che scende sul segmentato è del segmentato** (3.0.1): il contatto è sempre consumato, anche sulla goccia, e sulla
  goccia i cambi appena va di lato (mezzo slop). Serviva perché il back «ovunque» della pagina decide dopo i figli,
  guardando se uno ha consumato, e un trascinamento orizzontale partito sul segmento scelto portava via la pagina.
- **Aptica**: un `Tick` per ogni segmento attraversato, letto sul valore arrotondato (il dito sente nell'istante in cui la goccia
  si poserebbe lì). Crescita della goccia sotto il dito `78/56` ≈ 1,39 (non l'1,5 dei pomelli: in una pista alta quanto lei a
  1,5 coprirebbe due segmenti); con le animazioni ridotte 1 e la goccia si posa senza viaggiare.
- **Da destra a sinistra**: il layout specchia da solo i segmenti, ma la goccia si muove con una traslazione di layer e
  col dito, che non specchia nessuno: i due versi li dice `segmentedDirection` (internal, puro). Vedi il test.
- La selezione che arriva da fuori (stato ripristinato, reset di un filtro) fa viaggiare la goccia **senza** chiamare `onSelect`.
- Se `selected` non è in `options` la goccia sta sul primo segmento. Con `options` vuota non disegna niente.

**Trappole.**

- **`content` è composto tre volte** (pista, copia invisibile, goccia a riposo): deve essere un'immagine di un segmento e
  non il posto dove far partire un lavoro. Con `content` la `label` diventa il `contentDescription`: serve ancora.
- Larghezza: i segmenti sono pesi uguali (`weight(1f)`), l'etichetta è una riga `bodySmall` con 6 dp di padding. Cinque
  parole lunghe in uno schermo stretto si troncano: per tante voci o voci lunghe, `FluidChip` in una riga.
- Costa: per un controllo di 32 dp sono due registrazioni in più (`rememberGlassBackdrop(blurRadius = 0.dp)` × 2) più la
  lastra. È il prezzo scelto per la lente; vale per un segmentato per schermata, non per uno per riga di lista.
- `options.indexOf(selected)`: due opzioni uguali per `equals` sono lo stesso segmento.
- Fu un difetto (corretto in 3.0): da destra a sinistra la goccia e la copia della faccia scelta si traslavano nel verso sbagliato
  e il trascinamento andava al contrario. Oggi lo dicono `segmentedDropTranslation` e `segmentedDragDelta`.

**Non usarlo quando.** Le voci non entrano su una riga o si accendono insieme (`FluidChip`); sono schede di navigazione
(`FluidTabBar`, `FluidFloatingTabBar`).

**Versione.** Goccia e pista di vetro: 3.0 (fase 9); dito scivolato da un altro segmento: 3.0; contatto sempre consumato: 3.0.1;
da destra a sinistra corretto: 3.0.

---

## FluidChip

**Cos'è.** La pillola di filtro. Dove un segmentato non può stare: le opzioni non entrano su una riga, o più d'una può
essere accesa. Dalla 3.0 (fase 9) è un **tasto di vetro** dove c'è qualcosa da attraversare (canvas di pagina, foglio,
barra), la stessa lastra di `FluidGlassButton`; dove no, la pastiglia opaca di sempre.

**Firma essenziale.**

```kotlin
FluidChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  leading: (@Composable () -> Unit)? = null,   // icona 15 dp
)
```

**Uso** (da `ControlsTab`).

```kotlin
LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
  items(listOf("Tutti", "Letti", "Da leggere")) { option ->
    FluidChip(label = option, selected = chip == option, onClick = { chip = option })
  }
}
```

**Comportamento.**

- Alto almeno 48 dp (`defaultMinSize(minHeight = 48.dp)`), padding 14 × 6 dp, etichetta `bodyMedium` Medium su **una riga**.
- **Su vetro**: il chip scelto ha l'accento *dentro il materiale* (non un riempimento opaco), etichetta `onPrimaryContainer`;
  non scelto `onSurface`. Pressione e aptica sono quelle dei tasti: cresce, segue il dito, tre colpi.
- **Opaco** (senza fondale o sotto API 31): scelto = `primary` con testo `onPrimary`; non scelto = velo `onSurface` al 6%,
  testo `onSurface`; i colori passano con `FluidMotion.color(200)`.
- **Cede il dito alla fila** (`YieldsToScroll`): in una `LazyRow` che scorre il chip si scioglie e non clicca; un tocco
  clicca una volta, con i tre colpi.
- Semantica: `Role.Button` e `selected`. Non è un `Checkbox` né un `RadioButton` per TalkBack.

**Trappole.** Non c'è un parametro `haptic` né `grip`. Il chip cede il dito **ovunque** stia, anche fuori da una riga:
un dito che parte su di lui può far scorrere la pagina (a differenza di un pulsante, che la tiene ferma).

**Non usarlo quando.** Una scelta esclusiva di 2-5 voci corte (`FluidSegmentedControl`); un'azione (`FluidGlassButton`).
`FluidQuickAction` è deprecato e delega a `FluidChip(selected = false)`.

**Versione.** Tasto di vetro: 3.0 (fase 9); cede il dito: 3.0; cresce col contenitore: 2.11.0.

---

## FluidColorDot, FluidHairline, FluidRowValue

**`FluidColorDot(color, selected, onClick, label, modifier, size = 30.dp)`** — un cerchio per scegliere un colore d'accento.
Il bersaglio è `max(48 dp, size + 10 dp)`; il selezionato ha un anello a 4 dp dal bordo (1,5 dp di tratto, opacità `0,55` di
`onSurface`, scelta perché il contrasto finale sia almeno 3:1 sulle superfici chiara e scura) che sfuma in 180 ms. Semantica
`Role.RadioButton` + `selected`, nome = `label`. Cede il dito alla fila (`YieldsToScroll`). Un selettore è una riga di questi
in una `Row`/`LazyRow`.

**`FluidHairline(modifier, startInset = 0.dp, color = onSurface al 10%)`** — il filetto da 0,5 dp: una larghezza vera su ogni
schermo dell'app, ed è quello che disegna iOS. A tutta larghezza (`fillMaxWidth`); `startInset` lo fa partire dopo l'icona
di una riga.

**`FluidRowValue(text, modifier)`** — il valore (numero o testo) sul bordo destro di una riga di impostazioni: `bodyLarge`,
colore `onSurfaceVariant`, una riga. Mai nell'accento: il valore è informazione e la cosa che si tocca è la riga.

**Non usarli quando.** Un valore che si cambia sul posto (`FluidSlider`, `FluidSegmentedControl`); una divisione fra righe di
un gruppo (la lista ci pensa da sola: `FluidListGroup`).

---

## FluidTextField e FluidTextEdit

**Cos'è.** Il campo di testo. Il campo con contorno di Material ha un'etichetta che sale nel bordo, una linea indicatrice e un cambio di colore
al fuoco: tre parti in movimento per dire «qui si scrive». iOS gli dà un rettangolo pieno e quieto e lascia che sia il cursore a parlare.
L'unica interazione che il campo aggiunge è il tasto che svuota, che compare solo se c'è qualcosa da svuotare.

**Due forme.** `FluidTextField(value: String, …)`: comoda per un campo che vuole solo il testo (la selezione vive dentro, il chiamante
vede una stringa; muovere il cursore non è un valore nuovo). `FluidTextField(value: TextFieldValue, …)`: per chi ha bisogno del cursore (un
editor con i pulsanti di formattazione, che devono scrivere *dove* si sta scrivendo) — la prima è un guscio della seconda.

**Firma essenziale.**

```kotlin
FluidTextField(
  value: String,                               // oppure TextFieldValue
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String? = null,
  label: String? = null,                       // sopra il campo, non dentro
  enabled: Boolean = true,
  readOnly: Boolean = false,
  singleLine: Boolean = true,
  minLines: Int = 1,
  maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
  isError: Boolean = false,
  supportingText: String? = null,
  minHeight: Dp = FluidTextFieldDefaults.MinHeight,   // 40 dp (dalla 4.0; prima 44)
  keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
  keyboardActions: KeyboardActions = KeyboardActions.Default,
  visualTransformation: VisualTransformation = VisualTransformation.None,
  showClearButton: Boolean = singleLine,
  secret: Boolean = false,
  leading: (@Composable () -> Unit)? = null,   // icona 18 dp
  trailing: (@Composable () -> Unit)? = null,  // slot 24 dp
)
```

**Uso** (dal `sample`, `ControlsTab` e `GapsDemo`).

```kotlin
FluidTextField(value = query, onValueChange = { query = it }, placeholder = "Cerca")

FluidTextField(
  value = key, onValueChange = { key = it },
  label = "Chiave di accesso", placeholder = "Incolla la chiave",
  secret = true,                               // puntini + occhio; niente tasto «cancella»
)

FluidTextField(
  value = notes, onValueChange = { notes = it },
  label = "Note", minLines = 4,                // minLines > 1 = campo a più righe, anche col singleLine di serie
)
```

**Comportamento.**

- **Aspetto**: un pozzo (riempimento) con angoli continui di `FluidRadius.Control` (12 dp), altezza minima `minHeight` (44 dp), padding 12 × 10
  dp, testo `bodyLarge` in `onSurface`, **cursore dell'accento**. L'etichetta, se c'è, è `bodyMedium` `onSurfaceVariant` sopra il campo; il
  `supportingText` (se non vuoto) è `bodySmall` sotto, in `error` se `isError`.
- **Il pozzo sul vetro è opaco, per scelta.** Su una pagina opaca una velatura al 6% di `onSurface` si legge come un incavo. Dentro un pannello
  di vetro (`LocalFluidCanvasIsGlass`) sotto non c'è una superficie piatta ma la pagina dell'app trasmessa a metà: la stessa velatura si modula
  con lei e il campo vuoto diventa una finestra sulla lista dietro (misurato: la luminanza in una casella costante andava da 175 a 215). Quindi
  sopra il vetro il pozzo è un fondo *opaco*, del colore del pannello (`GlassDefaults.glassFilm()`) scurito del 18% verso `onSurface` (misurato:
  un decimo lo lasciava sei punti sotto il pannello, meno stacco di prima; 18 centesimi danno circa 210 contro i 230 del pannello). Con
  `isError` sul vetro il pozzo tende al `error` per 22%, su pagina piatta è `error` al 10%.
- **Segnaposto**: appare solo con testo vuoto, `bodyLarge` `onSurfaceVariant`; a una riga si tronca con i puntini (3.0.4), non va a capo né
  allarga il campo.
- **Tasto che svuota**: un cerchio disegnato (17 dp, nella casella da 28 dp) con una croce, `onSurface` al 28%; area di tocco da 48 dp che non
  occupa spazio. Compare (dissolvenza + scala da 0,6) solo con testo non vuoto e campo abilitato; per TalkBack è un pulsante col nome
  `FluidLabels.clear` («Cancella»). Appare solo se `showClearButton && oneLine && !secret`: `showClearButton` di serie segue `singleLine`, ma
  un campo con `minLines > 1` non è «a una riga» e non lo ha.
- **Righe**: `singleLine` di serie è vero, e con `singleLine` il campo di testo ignora `minLines`; per questo chi chiede `minLines > 1` ottiene un
  campo a più righe anche senza togliere `singleLine` (i campi «descrizione», «dettagli» uscivano alti una riga). `maxLines` ≤ 1 o < `minLines`
  è trattato come nessun tetto.
- **`secret`** (3.0): il testo è puntini e a destra un occhio lo mostra e lo nasconde. Parte nascosto a **ogni** composizione (non ricorda di
  essere stato mostrato: una rotazione o il ritorno da un'altra app non rimette in chiaro una password); non ha il tasto che svuota (due tasti
  piccoli accanto sono il modo più rapido per scoprire quale dei due si è colpito: un segreto si riscrive); tastiera di serie quella delle
  password senza correzione (`keyboardOptions` la sostituisce, se la passi). L'occhio (24 dp, cresce sotto il dito) dice a TalkBack cosa farà,
  non cosa è (`FluidLabels.showSecret` / `hideSecret`). Per chi legge lo schermo il campo resta una password anche dopo l'occhio: la fissa la
  libreria di testo quando nasce nascosto, e un segreto mostrato resta un segreto. Se c'è `trailing`, sta prima dell'occhio. Mascherato usa
  `PasswordVisualTransformation` e ignora la tua `visualTransformation`.
- **Slot `trailing` da 24 dp** (non 18): è l'altezza che il contenuto di un campo da 44 ha comunque (44 meno i 10 dp di margine sopra e sotto),
  quindi il campo non cresce, e un'icona di 24 dp ci sta intera. MyCloud si era scritto un suo «Mostra»/«Nascondi» perché a 18 dp il bersaglio
  era troppo piccolo.
- **String ↔ `TextFieldValue`**: la forma a `String` tiene la selezione e la composizione IME in uno stato locale e il testo è sempre quello del
  chiamante; se il chiamante cambia il testo da fuori la selezione locale si tiene e il campo la riporta nei limiti da solo.

**`FluidTextEdit`** — le operazioni di un editor semplice, **sul valore** (pure: `TextFieldValue` dentro, `TextFieldValue` fuori, si provano in
JVM). Esistono perché un pulsante «grassetto» che appende `**` in fondo al testo è peggio di nessun pulsante: chi scrive ha il cursore in un punto
preciso.

| funzione | cosa fa |
|---|---|
| `FluidTextEdit.insert(value, snippet)` | mette lo snippet al posto della selezione, o al cursore, e lascia il cursore dopo |
| `FluidTextEdit.wrap(value, prefix, suffix = prefix)` | avvolge la selezione e la **tiene selezionata** (un secondo tocco può avvolgerla ancora); senza selezione mette la coppia e il cursore in mezzo |
| `FluidTextEdit.toggleLinePrefix(value, prefix)` | mette `prefix` (`# `, `- `, `> `) all'inizio della **riga del cursore**, in qualunque punto stia, o lo toglie se c'è; la selezione si sposta di quanto aggiunto/tolto e non va mai prima dell'inizio riga |

```kotlin
var text by remember { mutableStateOf(TextFieldValue("")) }
FluidTextField(value = text, onValueChange = { text = it }, singleLine = false, minLines = 6)
Row {
  FluidGlassIconButton(onClick = { text = FluidTextEdit.wrap(text, "**") }) { Icon(Icons.Rounded.FormatBold, contentDescription = "Grassetto") }
  FluidGlassIconButton(onClick = { text = FluidTextEdit.toggleLinePrefix(text, "# ") }) { Icon(Icons.Rounded.Title, contentDescription = "Titolo") }
  FluidGlassIconButton(onClick = { text = FluidTextEdit.insert(text, "---\n") }) { Icon(Icons.Rounded.HorizontalRule, contentDescription = "Linea") }
}
```

**Trappole.**

- **Il tasto «cancella» compare anche con `readOnly = true`** (la condizione è `value.text.isNotEmpty() && enabled`, non guarda `readOnly`) e
  chiama `onValueChange("")`: un campo in sola lettura con testo mostra un tasto che, toccato, chiede di svuotarlo. Passa `showClearButton = false`
  per un campo `readOnly`.
- `isError` colora il pozzo e il testo di supporto ma il codice non imposta nessuna semantica d'errore per TalkBack: lo dice il testo di supporto,
  se lo scrivi.
- L'etichetta e il segnaposto sono due `Text` accanto al campo, non il suo nome accessibile: il codice non li collega al nodo di testo. Per un campo
  senza altre indicazioni, verifica con TalkBack.
- `FluidTextField` è a tutta larghezza (`fillMaxWidth`); `modifier` è sulla `Column` che contiene etichetta, campo e testo di supporto, non sul solo
  campo.
- `FluidTextEdit.insert`/`wrap` ricostruiscono un `TextFieldValue` nuovo: la composizione IME in corso si perde (come ogni inserimento da fuori).

**Le misure di Apple (4.0, F6.6).** Il campo pieno di iOS 27 nel contenuto (la ricerca sotto il titolo grande di Podcast, sessione 278 di WWDC26,
11:15, misurata al pixel in 4K a 3,67 px/pt) è una **capsula alta 40 pt**, 18 livelli sotto la pagina bianca (il `tertiarySystemFill` di UIKit), col
testo da 17 pt, l'icona a 13 pt dal bordo e il testo a 37. Il campo a una riga ora è quello: **capsula da 40 dp** (`FluidTextFieldDefaults.MinHeight`),
margini di 8 dp sopra e sotto e 12 ai lati (icona a 12, testo a 38), riempimento `onSurface` all'**8% sul chiaro e al 12% sullo scuro** (prima un
rettangolo di 44 dp, raggio 12, velo del 6%). Su più righe (`singleLine = false` o `minLines > 1`) è un rettangolo arrotondato col raggio della
capsula a una riga (`FluidTextFieldDefaults.MultilineRadius`, 20 dp). Sopra un vetro il pozzo opaco resta quello di prima. Lo slot a destra resta di
24 dp: è il contenuto di un campo da 40 meno gli 8 dp sopra e sotto. Un'app che voleva i 44 dp li chiede con `minHeight = 44.dp`.

**Non usarlo quando.** Una ricerca (`FluidSearchField`: lente, crocetta, contatore dei risultati e frecce); un valore fra pochi scelti
(`FluidSegmentedControl`, `FluidSlider`).

**Versione.** `FluidTextField` a `TextFieldValue` e `FluidTextEdit`: 1.32.0; `minLines > 1` = più righe: 2.5.2; `secret` e slot a 24 dp: 3.0; parole
dell'occhio da `FluidLabels`: 3.0.1; nome per TalkBack del tasto che svuota (`FluidLabels.clear`, «Cancella») e segnaposto a una riga troncato: 3.0.4;
capsula da 40 dp e riempimento di Apple (`FluidTextFieldDefaults`): 4.0.0 (F6.6).

---

## FluidStepper

Usa `FluidStepper` per modificare un numero intero a piccoli passi. L'app mantiene il valore e lo mostra accanto
alla capsula. `range` chiude i due estremi, `step` regola l'incremento e `repeatOnHold` consente la ripetizione.

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.FluidRowValue
import dev.antigravity.fluidengine.ui.fluid.FluidStepper
import dev.antigravity.fluidengine.ui.theme.FluidListRow

@Composable
fun RigaCopie() {
  var copies by remember { mutableIntStateOf(1) }
  FluidListRow(
    title = "Copie",
    subtitle = "Da 1 a 10",
    disclosure = false,
    badge = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FluidRowValue(copies.toString())
        FluidStepper(value = copies, onValueChange = { copies = it }, range = 1..10, label = "Copie")
      }
    },
  )
}
```

**Comportamento.**
- **Forma e taglia di Apple**: capsula di 94 × 32 dp, le misure di `UIStepper` (documentate da UIKit fino a iOS 18; iOS 26 lo mette fra i controlli
  da rivedere senza dire come, R1 K6: fiducia media). Due metà uguali, un filo verticale fra le due (1 × 18 dp), i segni disegnati (13 dp, tratto 2 dp).
- **È un controllo di vetro** dove c'è qualcosa da attraversare (il canvas di una pagina ambientale, un foglio, una barra: la regola di `FluidChip`):
  la stessa lastra di `FluidGlassButton`, con **la pressione e la luce del dito del vetro** (cresce, si piega verso il dito, si accende dal punto di
  contatto: una `FluidPressState` sola per il vetro e per il tocco). Il punto acceso è quello del dito, quindi sta sulla metà toccata. Su una pagina
  piatta (o sotto API 31) è la pastiglia opaca nel grigio di riempimento dei campi, che cresce sotto il dito allo stesso modo.
- **Tenuto premuto ripete** (`UIStepper.autorepeat`, di serie in Apple): dal click lungo un passo ogni 150 ms, poi 100 e, dopo venti passi, 50 (a
  occhio: Apple non dà i tempi). Ogni passo ripetuto è un `Tick`. Se la lista prende il dito per scorrere, la ripetizione smette. Tenuto fermo
  su «+» il valore sale finché il dito è giù. `FluidStepperHoldTest` verifica la ripetizione nel vetro e nella pastiglia
  opaca con eventi Compose e orologio virtuale.
- **Ai capi** la metà che non può andare oltre si spegne (il segno al 30%) e non fa niente. Il conto è in `Long`: un passo da `Int.MAX_VALUE` non gira;
  un valore fuori da `range` ci torna al primo passo.
- **Per TalkBack** è un nodo solo: il nome (`label`), il valore come stato (`valueText`) e le azioni «aumenta» e «diminuisci» (le parole di
  `FluidLabels.dateTimeIncrease`/`dateTimeDecrease`), solo quando c'è un passo da fare.
- Il **valore non lo mostra**: in Apple sta a sinistra dello stepper, nella riga. Mettilo con `FluidRowValue`.

**Trappole.** La capsula è alta 32 dp e il tocco è quello (sotto i 48 dp di Android, come il segmentato): lo stepper sta nel `badge` di una riga, che
ha già la sua altezza. Il valore è dell'app: se non lo aggiorna, lo stepper chiede ogni volta lo stesso passo. `step` deve essere positivo.

**Non usarlo quando.** Il valore va da un capo all'altro di un intervallo grande (`FluidSlider`); si scrive (`FluidTextField` con
`KeyboardType.Number`).

---

## FluidTokenField e FluidSuggestionRow (Fase 1 di Pampa Mail, 3.1.0)

**Cos'è.** Il campo che trasforma ciò che si scrive in **gettoni**: i destinatari di un messaggio, le etichette di una nota, i partecipanti di un evento. Cresce **nella pagina** (a più righe, senza limite verticale interno:
va dentro lo scroll del chiamante), ogni gettone è un bersaglio da 48 dp, e **non sa che cosa sia un gettone valido**: la validazione, la normalizzazione e la deduplica sono dell'app. File: `FluidTokenField.kt`.

**Firma essenziale.**
```kotlin
FluidTokenField(
  value: FluidTokenFieldValue,                         // tokens, draft (TextFieldValue), selectedTokenId
  onValueChange: (FluidTokenFieldValue) -> Unit,
  createToken: (text: String, existing: List<FluidToken>) -> FluidToken?,   // valida, normalizza, deduplica; null = rifiuta
  modifier, label = null, placeholder = null,
  separators: Set<Char> = FluidTokenFieldDefaults.Separators,               // virgola, punto e virgola, a capo
  enabled = true, keyboardOptions = KeyboardOptions.Default,
  onEditToken: ((FluidToken) -> Unit)? = null,
  suggestions: List<FluidTokenSuggestion> = emptyList(), onSuggestionClick: (FluidTokenSuggestion) -> Unit = {},
)
FluidToken(id, label, isError = false)                 // id unico, deciso dall'app
FluidSuggestionRow(title, subtitle = null, leading = null, onClick, modifier, enabled = true, selected = false)
```

**Comportamento.**
- **Si conferma solo una porzione terminata da un separatore**, mai durante la composizione della tastiera (IME): `FluidTokenFieldDefaults.update` non conferma mentre c'è una composizione. Invio, la perdita di fuoco o
  un incolla esplicito di una lista completa chiedono anche il resto (`commitRemainder`). Il separatore di serie è virgola, punto e virgola e a capo; **non lo spazio**, che aggiungi tu solo per domini senza nomi composti («Anna Rossi»).
- **`createToken` vede anche i gettoni appena aggiunti** nella stessa passata, quindi la deduplica è interamente dell'app (incollare «a@x.it, a@x.it» si deduplica se la tua funzione lo decide). `null` **conserva il testo
  rifiutato**, separatore compreso: nessun dato non riconosciuto va perso in silenzio.
- **Un gettone non valido** (`isError = true`) resta, col colore d'errore (`errorContainer`). Lo decide l'app: `createToken` può restituirlo non valido invece di `null`. Per TalkBack il gettone dice «non valido» (`FluidLabels.tokenInvalid`, errore semantico),
  quindi il segnale non è solo il colore. Ogni gettone ha anche la sua «x» da 48 dp per rimuoverlo.
- **Cancellazione in due tempi**: la prima pressione di cancella, a bozza vuota, **seleziona** l'ultimo gettone (bordo da 1,5 dp, non solo colore); la seconda lo rimuove. Con testo o una composizione in corso, cancella agisce
  sul testo. Per osservare `deleteSurroundingText` della tastiera a input vuoto il campo mette una **sentinella invisibile** (zero-width space) in testa all'editor, nascosta dalla trasformazione visiva: `FluidTokenFieldValue.draft`
  è la bozza **senza** sentinella, e il cursore dell'app non la vede.
- **Suggerimenti in pagina, non a comparsa.** `suggestions` sono dati della query corrente, pilotati dall'app: l'app scarta le risposte obsolete e gestisce caricamento ed errore. Compaiono sotto il campo come
  `FluidSuggestionRow` (`FluidTokenSuggestion(id, title, subtitle, leading)`); le frecce su/giù ne scelgono uno, Invio lo accetta **senza spostare il fuoco dall'input**, e le righe non prendono fuoco (`canFocus = false`):
  la tastiera non si chiude mentre si sceglie. L'`id` stabile evita che un aggiornamento della lista faccia scegliere un'altra riga.
- **`onEditToken`** (opzionale): se c'è, toccare un gettone lo rimette in modifica; l'app decide se rimuoverlo e mettere la sua etichetta nella bozza. Per TalkBack ogni gettone ha le azioni «modifica» (se c'è
  `onEditToken`) e «rimuovi» (`FluidLabels.tokenEdit`, `FluidLabels.tokenRemove`).
- Il pozzo del campo, su un canvas di vetro, è la pellicola del vetro schiarita (`glassFilm` verso `onSurface`); su pagina piatta un velo di `onSurface` al 6%.

**Stato e salvataggio.** `FluidTokenFieldValue(tokens, draft, selectedTokenId)` è un **valore**: lo stato lo tiene l'app (un ViewModel o `rememberSaveable(stateSaver = FluidTokenFieldValue.Saver)`). Il `Saver` salva anche il
cursore, gli errori e la selezione del gettone; la **composizione IME è transiente e non si ripristina** dopo la morte del processo.

**Trappole.** Gli id dei gettoni devono essere **unici e stabili** (sono le chiavi della `FlowRow`). Separatori sintattici dentro nomi composti («Rossi, Anna» da una rubrica) richiedono una policy dell'app **prima**
dell'inserimento: il campo spezza dove trova un separatore. La validità la stabilisce l'app e può cambiare dopo (un indirizzo che il server rifiuta): aggiorna `isError`, non cancellare il gettone. Non c'è limite verticale: dentro
una `LazyColumn` va in un `item`, non come figlio con altezza fissa.

**Non usarlo quando.** Il valore è uno solo (`FluidTextField`) o la scelta è chiusa e piccola (`FluidChip`, `FluidSegmentedControl`).

## Token, costanti, Defaults

**`FluidButtonDefaults`**

| funzione | `Large` | `Medium` | `Small` |
|---|---|---|---|
| `height(size)` | 50 dp | 42 dp | 32 dp |
| `horizontalPadding(size)` | 22 dp | 18 dp | 14 dp |
| `shape(size)` | `ContinuousCornerShape(14.dp)` | `ContinuousCornerShape(12.dp)` | `FluidCapsuleShape` |

**`FluidGlassSize`** (`lens`, `buttonHeight`, `touchTarget`, `iconSize`)

| | `lens` | `buttonHeight` | `touchTarget` | `iconSize` |
|---|---|---|---|---|
| `Compact` | 40 dp | 40 dp | 48 dp | 24 dp |
| `Regular` | 46 dp | 48 dp | 48 dp | 28 dp |
| `Large` | 54 dp | 54 dp | 54 dp | 32 dp |

**Dal tema**: `FluidRadius.Control` = 12 dp (il pozzo del campo), `FluidCapsuleShape`. **Parole**: `FluidLabels.clear`, `showSecret`, `hideSecret`.

Costanti non pubbliche che conviene sapere (per dimensionare attorno):

| costante | valore | dove |
|---|---|---|
| segmentato: altezza / goccia / margine / angoli pista | 32 dp / pillola 7 dp / 2 dp / 9 dp | `FluidControls.kt` |
| crescita della goccia del segmentato | 78/56 (`SegmentDropPressedScale`) | idem |
| anello del `FluidColorDot` | opacità 0,55 (`FluidSelectedRingAlpha`) | idem |
| clic dopo un trascinamento | fino a 16 dp oltre il bordo (`FluidPressClickSlack`) | `FluidButtonGesture.kt` |
| tetti di crescita dei tasti | 16 dp (con testo), 24 dp (tondi) | `FluidPress.kt` |
| slot a destra di un campo | 24 dp (`FluidFieldTrailingSize`) | `FluidTextField.kt` |

I numeri di slider, interruttore e scrubber sono in [controlli-gesto.md](controlli-gesto.md).

## Cosa è verificato dai test

- **`FluidPressTest`, `FluidPressTransformTest`** (`engine-ui`): feedback ordinario e Classic, crescita 0,10 / ×1,5, tetto a 16 dp e a 24 dp, molle di LibrePods, deriva `tanh`, rilascio immediato; vedi [movimento.md](movimento.md).
- **`FluidGlassSizeTest`, `FluidGlassControlsTest`** (`engine-ui`): lente 40/46/54, bersaglio mai sotto 48 dp, padding e icona crescono con la taglia, l'icona a 0,6 del lato (24/28/32), il tondo Classic conserva la scala di LibrePods fino al bersaglio. In Liquid `F61GelTest` verifica ×1,35 e la curva dell’icona; il tasto tondo da 46 dp ha un bersaglio da 48.
- **`FluidSegmentedRtlTest`** (`engine-ui`): i due versi della goccia sono specchi esatti in ogni punto della corsa. **`FluidTextEditTest`**: `insert`, `wrap`, `toggleLinePrefix` (cursore, selezione, riga).
- **`F7p23GlassDragTest`** (`sample`, Robolectric, col dito vero e il vetro Liquid) e **`F7p23GelDragTest`** (`engine-ui`): il tondo da 62 dp trascinato fino a sei lati va sempre avanti e sempre meno, in diagonale come in orizzontale, nel verso del dito; sotto un dito rumoroso (±1,5 px, un evento ogni 4 ms) che gira attorno al contatto l'asse dell'ellisse non salta più di 10° per fotogramma (col codice di F6.1: 90°); la curva è l'elastico di iOS coi numeri della scheda; un pezzo fuso resta nel margine del suo grappolo; a dito fermo la velocità torna a zero e non chiede fotogrammi; la manopola del bianco `pressglow`.
- **`FluidButtonDragTest`, `FluidButtonGripTest`** (`sample`, Robolectric): un pulsante in `LazyColumn`/`verticalScroll`/pager non li fa scorrere e segue il dito; un tap fa tre colpi in ordine; il click vale fino a 16 dp oltre il bordo; `haptic = null` clicca in silenzio; `HoldsFinger`/`YieldsToScroll` sui quattro pulsanti (`FluidButton`, `FluidGlassButton`, `FluidGlassIconButton`, `FluidCapsuleAction`); un chip in `LazyRow` si scioglie e non clicca, un tocco clicca una volta sola.
- **`AllGlassTest`, `FluidMatrixTest`** (`sample`): un chip su canvas è un tasto di vetro che clicca ed è scelto, su pagina piatta è la pastiglia opaca, spento non clicca; segmentato: il tocco sceglie una volta (su vetro e su pagina piatta), il trascinamento suona un tick per segmento e si posa sul più vicino, un dito che atterra su un altro segmento e scivola prende la goccia, in verticale non sceglie nulla, da destra a sinistra.
- **`FluidGapsTest`, `FluidGlassFreeSizeTest`** (`sample`): un tasto tondo da 40 dp occupa 48 dp e il margine clicca; il tasto con testo non è mai più basso di 48; il campo segreto è una password finché non si tocca l'occhio, e con una tastiera scelta dal chiamante resta nascosto; un campo normale non ha l'occhio; la misura libera: vetro di `size` dp, riquadro ≥ 48, una taglia dell'enum e la stessa misura in dp sono lo stesso tasto.
- **`FluidTokenFieldTest`** (`engine-ui`, 9): `update` conferma solo porzioni terminate da separatore e mai durante la composizione, `commitRemainder`, deduplica vista da `createToken`, `null` che conserva il testo con il separatore,
  cancellazione a due tempi (seleziona, poi rimuove; non agisce con testo o composizione). **`FluidTokenFieldInteractionTest`** (`sample`, 7): suggerimenti in pagina, frecce e Invio senza perdere il fuoco, azioni di TalkBack del gettone.
- **Non provato dai test**: il vetro dei controlli guardato in mano (D1 in `docs/10-coerenza.md`), `FluidBarAction(opensOn = FluidMenuOpensOn.Tap)` (solo la demo), il passaggio dal vetro del tasto alla superficie fisica in una barra, la luce additiva di un tasto di vetro a luce chiara, il tasto «cancella» di un campo `readOnly`.

## Dove guardare

Radice: `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/`.

| file | cosa c'è dentro |
|---|---|
| `FluidControls.kt` | `FluidButton`, `FluidButtonStyle`/`Size`/`Defaults`, `FluidSegmentedControl` (+ funzioni pure `segmentedDirection`, `segmentedDropTranslation`, `segmentedDragDelta`, `segmentedFingerValue`, `segmentedTouchOnDrop`), `fluidControlGlassBackdrop`, `FluidChip`, `FluidRowValue`, `FluidHairline`, `FluidColorDot` |
| `FluidGlassControls.kt` | `FluidGlassSize` (+ padding e icona interni), `fluidTouchTarget`, `glassControlSurface`, `fluidStaticGlassSurface`, `FluidGlassIconButton` (due overload) e `FluidGlassIconButtonCore` (con i ganci di un'ancora di menu), `FluidGlassButton` |
| `FluidScreen.kt` (riga ~1672) | `FluidBarAction`, `FluidBackButton` (interno) |
| `FluidTextField.kt` | `FluidTextField` (due overload), il pozzo e perché sul vetro è opaco, `FluidRevealButton`, `FluidClearButton` |
| `FluidTextEdit.kt` | `FluidTextEdit` |
| `FluidTokenField.kt` | `FluidTokenField`, `FluidToken`, `FluidTokenFieldValue` (+ `Saver`), `FluidTokenFieldDefaults` (`update`, `backspace`), `FluidTokenSuggestion`, `FluidSuggestionRow` |
| `FluidButtonGesture.kt`, `FluidPress.kt` | `FluidPressGrip`, il gesto del pulsante, `FluidPressFeedback`, `fluidPressable`, `rememberFluidPressState` — vedi [movimento.md](movimento.md) |
| `FluidLabels.kt` | `FluidLabels` (`clear`, `showSecret`, `hideSecret`, …) e `FluidLabels.english()` |
| `docs/10-coerenza.md` | la matrice dei comportamenti (D1–D9) per ogni controllo |

Interruttore, slider, scrubber: [controlli-gesto.md](controlli-gesto.md). Catalogo delle firme: [../api/controlli.md](../api/controlli.md).
