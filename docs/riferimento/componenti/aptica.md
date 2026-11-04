<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/componenti/aptica.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# Aptica

Il vocabolario di ciò che l'utente **sente sotto il dito**, e il motore che lo porta al vibratore. Ogni evento
(`FluidHapticEvent`) vuol dire sempre la stessa cosa in ogni app Pampa; un solo motore per tema (`FluidHapticsImpl`) lo
traduce in **composizioni di primitive** dove il telefono le ha (un S25 sì) e in **costanti di piattaforma** altrove,
sempre dietro l'interruttore dell'app (`EngineSettings.hapticsEnabled`) e il vibratore del device (e, per i tick che si
ripetono, il risparmio energetico), e con una rete che impedisce a due componenti di vibrare due volte per lo stesso istante. I componenti dell'engine
vibrano da soli: un'app non chiama quasi mai `play` per i controlli, lo chiama per **i suoi momenti** (una soglia
raggiunta, un esito, un'allerta) e per il codice a `View` (`fluidHapticsFor`).

Il modello mentale, in due frasi. **Una vibrazione per momento**: due eventi di controllo a meno di 70 ms l'uno dall'altro
sono lo stesso momento detto da due componenti, e il secondo non suona (con **un'eccezione voluta**: i tre colpi di un
tasto che cresce). **Il vocabolario è chiuso e il motore decide il come**: il call-site dice *cosa è successo*
(`Open`, `ToggleOn`, `Error`), non quale pattern o quale forza; per questo si tara sul telefono in un posto solo.

Catalogo delle firme: [../api/aptica.md](../api/aptica.md). Il perché storico: «Una vibrazione per momento (e
l'eccezione dei tasti)» in [../regole.md](../regole.md). La pressione che fa partire i tre colpi è in
[movimento.md](movimento.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| un tocco leggero su una superficie che cede | niente: `fluidPressable`/`fluidRowPressable` suonano `Tap` da soli |
| un pulsante con la sua risposta | niente: i pulsanti che crescono fanno `PressDown`, `PressRelease`, `PressClick` e la grana |
| un pulsante che **apre** qualcosa | `fluidPressable(haptic = FluidHapticEvent.Open)`: sostituisce `PressClick`, il contatto e il rilascio restano |
| un controllo che vibra già per conto suo | `haptic = null` (spegne contatto, rilascio, grana e click); `longClickHaptic = null` per il click lungo |
| un interruttore | niente: `FluidSwitch` suona `ToggleOn`/`ToggleOff`; una riga interruttore (`FluidToggleRow`) pure, con `fluidRowPressable(haptic = null)` |
| un aggancio in un gesto continuo (indice, griglia, tacca) | `Tick`; se arrivano fitti, `FrequentTick`; per un trascinamento di slider `DragTick` (il più leggero e corto) |
| una soglia superata (pull-to-refresh, swipe di una pillola) | `Threshold` |
| l'esito di un'azione | `Success`, `Warning`, `Error`: **non si scartano mai** |
| un'allerta che deve farsi sentire | `AlertWatch`, `AlertAlarm`, `AlertClear`: suonano con l'uso «notifica», non «tocco» |
| un assistente vocale | `ListenStart`, `SpeechDetected`, `ListenEnd`, `ReplyReady`, `WaitTick`, `Stop`, `ActionConfirmed`, `ProviderSwitched` |
| vibrare da una tastiera, un `View`, un servizio | `fluidHapticsFor(context, enabled = { … })`, **creato una volta** |
| leggere l'aptica del tema in un composable | `rememberFluidHaptics()` (o `LocalFluidHaptics.current`) |
| sapere cosa ha davvero suonato (e se un colpo è stato fuso) | `FluidHapticTrace` + `ProvideFluidHapticTrace` |
| tarare la grana di un trascinamento | **non da un'app**: `LocalFluidHapticGrainConfig` è il gancio della pagina di taratura della galleria |
| una vibrazione con un pattern tuo | non esiste: un evento nuovo si **aggiunge all'engine** (vedi «Come si compone un evento nuovo»); mai `Vibrator` a mano |
| vibrare in un test o in un'anteprima | non vibra: fuori da `FluidTheme` l'aptica è `NoOpFluidHaptics` |

## Una vibrazione per momento (e l'eccezione dei tasti)

**La regola.** Un tocco può far parlare tre componenti nello stesso istante: il tasto, il pannello che si apre, il
livello del modale. Il vibratore **tronca** la vibrazione in corso per suonare la successiva, e il risultato era un
«toc-toc» che si leggeva come un errore (`Reject`) mentre era un menu che si apriva. Quindi: **due eventi di controllo
entro 70 ms (`CoalesceWindowMillis`) sono lo stesso momento, e il secondo non suona.** La rete sta nel motore, non nel
call-site: nessun componente deve sapere cosa suona il suo vicino.

| categoria | eventi | cosa succede |
|---|---|---|
| **fondibili** (`coalescible`) | `Tap`, `Confirm`, `Reject`, `ToggleOn`, `ToggleOff`, `Threshold`, `Open`, `Close`, `GestureStart`, `GestureEnd` | il primo suona; un secondo a meno di 70 ms è **fuso** (non suona e **non rinnova** la finestra: conta il momento di quello che è suonato). A 70 ms esatti suona |
| **continui** (`continuous`) | `Tick`, `FrequentTick`, `WaitTick`, `DragTick` | cancello loro: mai più di uno ogni 40 ms (`CONTINUOUS_MIN_GAP_MILLIS`); **spariscono in risparmio energetico**; **non armano** la rete dei 70 ms |
| **esiti, allerte, assistente** | `Success`, `Warning`, `Error`, `Alert*`, `Listen*`, `SpeechDetected`, `ReplyReady`, `Stop`, `ActionConfirmed`, `ProviderSwitched` | **non si scartano mai** e non armano la rete: un `Tap` seguito da `Success` suona due volte, ed è giusto |
| **solo di piattaforma** (`platformOnly`) | `PressDown`, `PressRelease`, `PressClick` | vanno **dritti** a `performHapticFeedback`, dopo l'interruttore e il vibratore ma **prima** di qualunque rete |

**L'eccezione, una sola, voluta.** Un tasto che cresce (`FluidPressFeedback.Expand`) fa la coreografia di LibrePods
con le costanti di sistema, **tre colpi per tocco**:

| momento | evento | costante di sistema (Compose) | effetto di fabbrica (senza `View`) |
|---|---|---|---|
| il dito scende | `PressDown` | `SegmentFrequentTick` | `Tick` |
| il dito si solleva (**anche per un tap che poi clicca**) | `PressRelease` | `Reject` | `DoubleClick` |
| il click | `PressClick` | `ContextClick` | `Click` |

Più la **grana** mentre il dito scorre (un tick per evento sopra soglia, vedi sotto). **Perché è l'unica eccezione:**
lì la coreografia *è* il tasto; in mano è la differenza che si sente fra un tasto che risponde e uno che si limita a
scattare. Perché sono solo di piattaforma: il loro carattere è proprio la costante che il produttore del telefono ha
tarato (su Samsung `Reject` e `ContextClick` non sono un tick generico) e non si ricostruisce con le primitive. Perché
non armano la rete: rilascio e click cadono nello stesso istante, e se la armassero il click di un tap verrebbe scartato dal
suo stesso contatto. **Ogni altro componente resta a un momento, un colpo.**

Il nome `PressRelease` è «sbagliato per il caso» (significa «rifiutato») ma è la coda che il tocco ha su LibrePods, e
suona a ogni rilascio. Un tap su un tasto dà dunque contatto → rilascio → click in poco più di un centinaio di ms.

## `FluidHapticEvent`: il vocabolario

`enum class FluidHapticEvent(val continuous: Boolean = false)` (1.19.0). **Gli ordinali non cambiano**: i nuovi eventi
si appendono in coda, e `FluidHapticsTest` lo fissa (`Tap` è 0, `DragTick` è il quartultimo, gli ultimi tre sono i
colpi del tasto). Pattern in `HapticPrimitive@scala (+ ritardo in ms)`; durate nominali delle primitive: `TICK` 10 ms,
`CLICK` 20, `LOW_TICK` 20, `THUD` 40, `QUICK_RISE`/`QUICK_FALL` 50, `SLOW_RISE` 100, `SPIN` 90.

| evento | pattern | ripiego (`HapticFeedbackType`) | note |
|---|---|---|---|
| `Tap` | `TICK@0,5` | `VirtualKey` | il rilascio su un controllo che cede; un tasto che cresce non lo suona più |
| `Confirm` | `CLICK@0,7` | `Confirm` | |
| `Reject` | `LOW_TICK@1` + `LOW_TICK@1 (+60)` | `Reject` | due colpi: la voce di un «no» |
| `ToggleOn` | `TICK@0,6` + `CLICK@0,5 (+30)` | `ToggleOn` | |
| `ToggleOff` | `CLICK@0,4` | `ToggleOff` | |
| `Threshold` | `CLICK@0,8` | `GestureThresholdActivate` | l'estremità di slider e scrubber |
| `Tick` | `LOW_TICK@0,35` | `SegmentTick` | continuo; un aggancio che si sente da solo |
| `FrequentTick` | `LOW_TICK@0,2` | `SegmentFrequentTick` | continuo; agganci fitti |
| `DragTick` | `TICK@0,3` | `SegmentFrequentTick` | continuo; il più leggero e corto (≤ 20 ms): `TICK` e non `LOW_TICK` perché il basso, a raffica, si somma in una vibrazione continua |
| `GestureStart` | `QUICK_RISE@0,4` | `ContextClick` | di serie il click lungo |
| `GestureEnd` | `QUICK_FALL@0,4` | `GestureEnd` | |
| `Open` | `CLICK@0,6` | `ContextClick` | **un colpo solo** dalla 3.0 (era rampa + tick: tre cose in 60 ms, troncate) |
| `Close` | `QUICK_FALL@0,3` | `GestureEnd` | |
| `Success` | `TICK@0,5` + `CLICK@0,8 (+80)` | `Confirm` | non fondibile |
| `Warning` | `LOW_TICK@0,8` ×2 (+70) | `LongPress` | |
| `Error` | `THUD@0,6` + `LOW_TICK@0,5` | `Reject` | il tonfo solo per errori e allerta |
| `ListenStart` / `ListenEnd` | `QUICK_RISE@0,6` / `QUICK_FALL@0,6` | `ContextClick` / `GestureEnd` | l'assistente: sale, scende |
| `SpeechDetected` | `TICK@0,3` | `SegmentTick` | |
| `ReplyReady` | `TICK@0,4` + `TICK@0,6 (+70)` | `Confirm` | |
| `WaitTick` | `LOW_TICK@0,3` | `SegmentTick` | continuo; un secondo di attesa in più (un limite del servizio) |
| `Stop` | `CLICK@0,5` | `GestureEnd` | |
| `ActionConfirmed` | `CLICK@0,6` + `TICK@0,4` | `Confirm` | |
| `ProviderSwitched` | `LOW_TICK@0,4` + `TICK@0,4 (+50)` | `LongPress` | |
| `AlertWatch` | `TICK@0,45` ×2 (+90) | `LongPress` | uso «notifica» |
| `AlertAlarm` | `THUD@1` + `TICK@0,8 (+90)` | `LongPress` | uso «notifica» |
| `AlertClear` | `QUICK_FALL@0,5` | `GestureEnd` | uso «notifica» |
| `PressDown`, `PressRelease`, `PressClick` | **nessuna** (lista vuota, di proposito) | `SegmentFrequentTick`, `Reject`, `ContextClick` | solo di piattaforma; dalla 3.0 |

I pattern sono **secchi e brevi, stile iOS**: tick e click per i controlli, rampe solo per l'assistente e i gesti, il
tonfo solo per errori e allerta; **nessun pattern oltre i 400 ms, nessun evento di controllo oltre i 120 ms** (i test lo
tengono). I numeri si tarano sul telefono con la pagina «Prova i feedback» (`FluidHapticsInfo`, `HapticLab` nella
galleria), non a tavolino.

## `FluidHaptics`, `LocalFluidHaptics`, `NoOpFluidHaptics`

```kotlin
interface FluidHaptics {
  val engine: FluidHapticEngine                 // Composition | Platform | Off
  fun play(event: FluidHapticEvent)
  fun grain(intensity: Float, style: FluidHapticGrainStyle = FluidHapticGrainStyle.Fluid) = Unit   // di serie niente
}
val LocalFluidHaptics: ProvidableCompositionLocal<FluidHaptics>   // statico; di serie NoOpFluidHaptics
@Composable fun rememberFluidHaptics(): FluidHaptics              // = LocalFluidHaptics.current
```

**Uso.**

```kotlin
val haptics = rememberFluidHaptics()
FluidButton(text = "Salva", onClick = {
  val ok = salva()
  haptics.play(if (ok) FluidHapticEvent.Success else FluidHapticEvent.Error)
})
```

**Comportamento.**

- `FluidTheme` installa **un motore per tema** (`rememberAndroidFluidHaptics(enabled = settings.hapticsEnabled)`).
  `LocalFluidHaptics` è **statico**: cambia solo con le impostazioni e non ricompone i figli.
- Fuori da un `FluidTheme` (anteprime, test) è `NoOpFluidHaptics`: `engine = Off`, niente vibra.
- `grain` ha un corpo vuoto di default: un'implementazione scritta prima della 3.0 compila com'è.
- Un `FluidTheme` **annidato** crea un motore nuovo **con le sue reti**: due componenti che dicono lo stesso istante da
  due temi diversi non si fondono. Lo stesso per un `fluidHapticsFor` che affianca il tema.

**Trappole.** *Sintomo:* l'app «non vibra mai», su nessun device (1.21.0). *Causa:* mancava `VIBRATE` nel manifest;
adesso lo dichiara `engine-ui` e arriva col modulo. *Sintomo:* l'aptica «non si sente» ma l'app è a posto. *Causa:* l'interruttore
`hapticsEnabled` è spento, o il telefono è in risparmio energetico (solo per i continui e la grana).

## Come arriva al vibratore: `FluidHapticsImpl`

```kotlin
class FluidHapticsImpl(
  private val port: HapticPort,
  private val enabled: () -> Boolean,                       // letto a OGNI evento
  private val clock: () -> Long = System::currentTimeMillis,
) : FluidHaptics
```

`play(event)` applica i cancelli **in quest'ordine**:

1. `enabled()` falso → niente (l'interruttore dell'app, `EngineSettings.hapticsEnabled`, di serie `true`; cambiarlo vale subito).
2. `!port.hasVibrator` → niente (`engine = Off`).
3. `event in platformOnly` → `port.platform(fallback(event))` e basta: **senza rete, senza orologio, senza risparmio
   energetico** (il risparmio toglie solo ciò che si ripete).
4. evento **continuo** → se `port.powerSave` niente; se `now − ultimoContinuo < 40 ms` niente.
5. evento **fondibile** → se `now − ultimoDiscreto < 70 ms` niente (fuso); altrimenti arma la finestra.
6. `attention = AlertAlarm | AlertWatch | AlertClear`: la vibrazione parte con l'uso «notifica» invece di «tocco».
7. Se le primitive di quell'evento ci sono **tutte** nel device (`usesComposition`) e `port.vibrate(...)` riesce → composizione.
   **Altrimenti `port.platform(fallback(event))`**: meglio un feedback diverso da quello disegnato che nessuno.

`grain(intensity, style)`: niente se interruttore spento, senza vibratore o in risparmio energetico; poi la grana di
LibrePods (`port.platform(SegmentFrequentTick)`, **senza tetto**) o quella dell'engine (pavimento di 8 ms
`GRAIN_MIN_GAP_MILLIS`, un `TICK` con la forza data, ripiego `SegmentFrequentTick`).

**Cosa NON è un cancello (di proposito).** L'impostazione tattile di sistema (`Settings.System.HAPTIC_FEEDBACK_ENABLED`):
su One UI resta a zero mentre il telefono vibra benissimo (Samsung tiene la sua impostazione altrove), e leggerlo per
zittire tutto significava zittire tutto, e basta. Ci pensa il sistema: la vibrazione parte con `USAGE_TOUCH`
(`VibrationAttributes` da Android 13; da 11 a 12 un `AudioAttributes` `ASSISTANCE_SONIFICATION`), e quel tipo è quello che
l'impostazione «Feedback tattile» spegne; le costanti di piattaforma di Compose rispettano la loro. `systemHapticsEnabled`
resta nell'interfaccia solo per `info()`.

**`engine`**: `Off` senza vibratore; `Composition` se il device sa fare **tutte** le primitive che i pattern usano
(`primitivesUsed`, chieste al telefono una volta sola); `Platform` altrimenti — **ma ogni evento decide per sé**: con
tutte le primitive tranne `THUD`, `Error` ripiega e `Tap` resta in composizione.

**Trappole.**

- *Sintomo:* due `Tap` consecutivi in un test suonano uno solo. *Causa:* l'orologio vero: nello stesso millisecondo sono
  lo stesso momento. Inietta `clock = { now }` (vedi «Come si scrive un test aptico»).
- *Sintomo:* un evento `Threshold` scompare accanto a un `Tap`. *Causa:* sono tutti e due fondibili; a ≤ 69 ms il secondo non suona. Se
  è un esito vero usa `Success`/`Warning`/`Error`, che non si fondono.
- *Sintomo:* in risparmio energetico la grana e i `Tick` spariscono ma il tasto vibra ancora. *Causa:* è la regola: restano
  i controlli (`Tap`, `PressDown`…), spariscono quelli che si ripetono.

## Il porto: `HapticPort`, `AndroidHapticPort`

**Cos'è.** `HapticPort` è «il dito del device» astratto, perché i cancelli si provano **sul computer** con un vibratore
finto: `hasVibrator`, `supportedPrimitives(ids)`, `vibrate(steps, attention)`, `platform(type)`, `systemHapticsEnabled`,
`powerSave`. `AndroidHapticPort(context, platformHaptics = null)` è quello vero.

**Comportamento.**

- `supportedPrimitives` è vuoto sotto API 30; `vibrate` ritorna falso sotto API 30 (lascia la parola alla costante).
- `platform(type)`: con il `HapticFeedback` di Compose chiama `performHapticFeedback(type)` (sotto API 30 Compose degrada da
  solo). **Senza** Compose (`platformHaptics = null`, la strada di `fluidHapticsFor`) suona l'**effetto di fabbrica** più
  vicino: `Tick`, `Click`, `DoubleClick`, `HeavyClick` (`createPredefined`, API 29+) o, per Android 8-9, una forma d'onda
  `[attesa, acceso…]` (`Tick` 10 ms, `Click` 20, `DoubleClick` 15+15 a 70, `HeavyClick` 40).
- La mappa costante → effetto (`predefinedHapticFor`, interna) tiene la **forza relativa** del vocabolario: un `Tap` (tick) più leggero
  di un `Open` (click), un `Reject` e un `Error` a due colpi, un'allerta pesante; un tipo che non conosce suona un click.
- Ogni chiamata al vibratore è in `runCatching`: un vibratore che rifiuta non fa mai cadere l'app.

## Come si compone un evento nuovo

Il vocabolario è un `enum`: un evento nuovo si **aggiunge all'engine**, non all'app. I passi, e cosa ti ferma se ne salti uno:

1. **`FluidHapticEvent`**: aggiungi la voce **in coda** (gli ordinali di chi c'era già non si spostano). `continuous = true`
   se è un tick che si ripete. *Attenzione:* `FluidHapticsTest` fissa che gli **ultimi tre** siano `PressDown`,
   `PressRelease`, `PressClick` e che `DragTick` sia il quartultimo: aggiungere in coda **rompe quelle due asserzioni**, e
   vanno aggiornate di proposito (sono lì come allarme).
2. **`FluidHapticPatterns.composition`**: il `when` è esaustivo, quindi il compilatore non ti lascia dimenticarlo. Primitive
   solo da `HapticPrimitive` (1…8), scale in 0..1, ritardi in ms. **Budget:** pattern ≤ 400 ms; se è un controllo ≤ 120 ms
   (`durationMillis` somma ritardi e durate nominali). Per il «tonfo» (`THUD`) solo se è un errore o un'allerta.
3. **`FluidHapticPatterns.fallback`**: anche questo esaustivo; scegli il `HapticFeedbackType` per i device senza primitive.
   Se usi un tipo nuovo, `predefinedHapticFor` ripiega su un click: aggiungi il ramo se serve un altro effetto.
4. **Appartenenza**: è un evento di controllo che deve restare secco? → `controlEvents`. Può cadere nello stesso istante di
   un altro componente (il tasto e il pannello che apre)? → `coalescible`. È un tick che si ripete? → `continuous = true`.
   È un **esito o un'allerta** che deve arrivare sempre? → in nessuno dei due. Sono solo di piattaforma? → `platformOnly`
   (e lista vuota in `composition`).
5. **Allerta?** Se deve suonare con l'uso «notifica» aggiungila alla riga `attention` di `play` (oggi
   `AlertAlarm`/`AlertWatch`/`AlertClear`).
6. **Test**: i test del vocabolario in `FluidHapticsTest` scorrono già `FluidHapticEvent.entries`: «ogni evento ha una
   composizione, un ripiego e scale in 0-1», «nessun pattern supera i 400 ms», «i controlli restano secchi». Aggiungi il
   caso del tuo evento (cancello giusto, ripiego senza primitive).
7. **Un componente lo suona** con `haptics.play(...)` da `rememberFluidHaptics()`; un controllo con `fluidPressable`
   passa `haptic = …`. Un momento, un colpo: se due componenti dicono lo stesso istante, uno solo deve suonare (o l'evento
   è fondibile).

Una nuova primitiva fra quelle usate **abbassa `engine` a `Platform`** sui telefoni che non la hanno (`primitivesUsed`),
pur lasciando in composizione gli eventi che non la usano.

## La grana: LibrePods contro Fluid

**Cos'è.** Il treno di vibrazioni di un dito che **trascina un tasto**. Prende il posto delle vecchie tacche a distanza fissa
(un tick ogni 10 dp a qualsiasi velocità: un ticchettio anche a passo d'uomo, e a dito fermo che oscilla un rumore). Arriva
solo da un pulsante che cresce (`dragHaptics`) con la pressione in corso e `haptic != null`; il pulsante è premuto al contatto,
quindi dal primo evento.

```kotlin
enum class FluidHapticGrainStyle { LibrePods, Fluid }
data class FluidHapticGrainConfig(val style: FluidHapticGrainStyle = FluidHapticGrainStyle.LibrePods, /* … numeri di Fluid */)
val LocalFluidHapticGrainConfig: ProvidableCompositionLocal<FluidHapticGrainConfig>   // le app non lo toccano
```

| | `LibrePods` (**di serie dal 3.0**) | `Fluid` (la scrittura di prima della 3.0) |
|---|---|---|
| decisione | **un evento** del puntatore con `Δ² > 350` px² (≈ 18,7 px) suona; senza memoria | velocità **misurata** (media esponenziale in dp/s sugli istanti degli eventi) con isteresi |
| soglia | in **pixel**, non in dp (come l'originale: vale quel che vale la densità); `librePodsSquaredPx = 350` | parte sopra `onsetDpPerSec = 520`, si spegne sotto `releaseDpPerSec = 380` (una soglia sola farebbe scattare la grana a ogni evento di un dito che oscilla) |
| suono | `SegmentFrequentTick` di sistema a ogni evento deciso, **senza tetto di frequenza**; la forza non si dosa (l'intensità è sempre 1 e non conta) | `TICK` composto con la forza dalla velocità: da `minIntensity = 0,14` a `maxIntensity = 0,5` fra onset e `fullDpPerSec = 2400` (smoothstep), ±`intensityJitter` 0,2 |
| ritmo | a ogni evento sopra soglia: piano non suona niente, veloce suona a ogni evento | un granello ogni `spacingDp = 8` dp ±`spacingJitter` 0,3; pavimento `minGapMillis = 9` ms; più il tetto del motore di 8 ms |
| irregolarità | nessuna | passo e forza oscillano con un rumore **deterministico**, un seme per gesto (due strofinii uguali non suonano lo stesso treno) |
| tempo di media | — | `smoothingMillis = 24` |
| risparmio energetico | zittita | zittita |

Perché `Fluid` esiste ancora: la sua idea (la velocità misurata, non lo scatto di un evento) è ancora una proposta; si
confrontano in mano dalla galleria. Perché la velocità è misurata: gli eventi arrivano a 60, 120 o 240 Hz e a gruppi, e la
stessa soglia in pixel per evento voleva dire velocità diverse su schermi diversi. Gli eventi con lo stesso istante (un
gruppo) **si sommano** invece di dare una velocità infinita, e senza arretrati: un granello saltato per il pavimento non si
recupera dopo.

**Trappole.** `LocalFluidHapticGrainConfig` è statico: cambiarlo ricompone tutto (nella galleria i cursori applicano solo al
rilascio). Un controllo con `haptic = null` non ha grana. Il confronto di LibrePods è in **pixel**: la stessa app su un
telefono a densità più alta suona la grana a un passo in dp più corto.

## I gesti continui: `FluidHapticDetents` (interno)

Un dito che scorre manda un evento a ogni fotogramma del touchscreen (120, 240 al secondo), e vibrare a ognuno è il ronzio
che fa sembrare economico qualsiasi controllo. `FluidHapticDetents` trasforma il movimento in **tacche** con un pavimento di
tempo di **55 ms** (`DETENT_MIN_GAP_MILLIS`), sopra i 40 ms del motore perché quello è il limite di ciò che il vibratore
separa, questo di ciò che un dito riesce a contare. Per **gradino** (`step`): la maniglia di `FluidSlider` passa da un
ventesimo all'altro (`DragTick`; `Tick` se c'è una tacca, `snapValues`); le **estremità** sono un `Edge` che non subisce il
pavimento e `FluidSlider` lo suona come `Threshold` — che è però fondibile nella rete dei 70 ms del motore —; afferrare la
maniglia già in fondo (`restartAt`) non suona come arrivarci. `FluidScrubber` usa la stessa rete per i suoi passi.

## `fluidHapticsFor`: l'aptica per le View

**Cos'è.** Lo stesso motore del tema per codice che non è Compose: una tastiera (`InputMethodService`), un `View`, un
servizio, un'attività a viste. Una tastiera che chiamava `View.performHapticFeedback` a mano sceglieva da sola le costanti e
non aveva nessuna delle reti; adesso dice un evento del vocabolario e il resto è dell'engine.

```kotlin
fun fluidHapticsFor(context: Context, enabled: () -> Boolean = { true }): FluidHaptics

// Un campo del servizio o della vista, MAI uno per evento:
private val haptics = fluidHapticsFor(context) { prefs.hapticsEnabled }
button.setOnClickListener { haptics.play(FluidHapticEvent.Tap) }
```

**Comportamento.** **Creala una volta**: tiene lo stato delle reti (i 70 ms, i 40 ms, il tetto della grana) e chiede al
telefono le primitive una volta sola. `enabled` è letto a **ogni** evento (`EngineSettings.hapticsEnabled`): cambiarlo dalle
impostazioni vale subito. Il ripiego dove le primitive mancano è l'**effetto di fabbrica** più vicino (nessun `View` da cui chiedere
una costante di tocco). Usa `context.applicationContext`. `VIBRATE` lo dichiara `engine-ui`.

**Trappole.** *Sintomo:* un'app che mescola Compose e viste suona **due volte** lo stesso istante. *Causa:* due motori, due
reti che non si parlano; se lo istante lo dici da due parti, dillo da una. *Sintomo:* la tastiera sembra senza forza
distinta fra eventi. *Causa:* sui telefoni senza primitive gli eventi collassano nei quattro effetti di fabbrica (la forza relativa
resta, i dettagli no).

**Versione.** Dalla 3.0; gli effetti di fabbrica e la sua galleria in `GapsDemo` («Aptica senza Compose»).

## Senza vibratore, con l'app spenta, in risparmio energetico

| situazione | `engine` | cosa suona |
|---|---|---|
| nessun vibratore (`hasVibrator` falso) | `Off` | **niente**, mai: né eventi né grana (`FluidHapticsTest`: «senza vibratore il motore è spento») |
| interruttore dell'app spento (`hapticsEnabled = false`) | quello del device | niente, **compresi i tre colpi** del tasto |
| risparmio energetico | quello del device | spariscono i **continui** e la **grana**; i controlli e i tre colpi restano |
| device con primitive complete | `Composition` | composizioni; solo i tre colpi del tasto vanno alla costante |
| device con alcune primitive | `Platform` | per evento: composizione se le ha tutte, costante di piattaforma altrimenti |
| Android < 11 | `Platform` | costanti di Compose (degradano da sole) o, con `fluidHapticsFor`, effetti di fabbrica |
| composizione che il device rifiuta (permesso mancante) | — | ripiego alla costante, **non silenzio** |
| fuori da `FluidTheme` (test, anteprima) | `Off` | niente (`NoOpFluidHaptics`) |

`FluidHapticsImpl.info()` ritorna `FluidHapticsInfo` (motore, vibratore, primitive supportate, cancelli di sistema): è per
una pagina «Prova i feedback», non per decidere a runtime.

## Il registro: `FluidHapticTrace`

**Cos'è.** Il registro di cosa è stato **chiesto** al vibratore e quando. Esiste per una domanda che a dito non ha risposta:
**«una vibrazione per momento» è vero?** Dal polpastrello non si distingue «ne è arrivato uno» da «ne sono arrivati tre e due
sono stati fusi».

**Uso.** Dentro il `FluidTheme` (è lui a installare l'aptica):

```kotlin
val trace = remember { FluidHapticTrace() }          // capacity 240
FluidTheme(settings = settings) {
  ProvideFluidHapticTrace(trace) {
    AppContent()
  }
}
// altrove: trace.rows()   // le 20 righe più nuove per prime, con ritardi e note
```

**Comportamento.**

- È un **decoratore** (`TracingFluidHaptics`): inoltra ogni chiamata al motore vero, tale e quale e nello stesso ordine,
  e ne tiene una copia in una lista limitata (`DefaultCapacity = 240`) e osservabile. Non cambia cosa suona né quando. Senza
  `ProvideFluidHapticTrace` **nessuno lo costruisce**: nessun costo.
- **L'esito è una stima** (`FluidHapticTraceOutcome`): `Played`, `Merged` (fuso nei 70 ms), `Dropped` (troppo vicino al
  precedente dello stesso genere: i 40 ms dei continui, gli 8 ms della grana `Fluid`). L'interfaccia `FluidHaptics` non può dire
  se un evento è suonato, quindi il registro **rifà da solo le reti** del motore di serie con le stesse costanti e un orologio
  suo. Con un motore sotto che non è quello (o spento, o senza vibratore) dice cosa *avrebbe* fatto il motore di serie.
- `rows(maxRows = 20)` / `fluidHapticTraceRows(entries, maxRows)` (pura): righe leggibili, **le più nuove per prime**; i
  granelli consecutivi dello stesso stile a ≤ `FluidHapticTraceGrainRunGapMillis` (150 ms) diventano una riga sola
  («grana LibrePods ×14», con quanti scartati e, per `Fluid`, la forza). `startsMoment` è vero se la riga comincia un momento
  nuovo (≥ 70 ms dalla precedente). I ritardi si calcolano su tutte le voci, non solo su quelle visibili.
- `recording = false` ferma **solo** il registro; `clear()` svuota la lista ma **non** azzera le reti stimate.
- `trace.wrap(delegate)` è idempotente per lo stesso registro.

**Trappole.** *Sintomo:* il registro non mostra niente. *Causa:* `ProvideFluidHapticTrace` messo **fuori** dal `FluidTheme`
(sarebbe sovrascritto). *Sintomo:* un `FluidTheme` annidato «perde» il registro. *Causa:* ne crea un'aptica nuova;
dentro richiama `ProvideFluidHapticTrace { … }` **senza argomenti** (rimette quello ereditato, `LocalFluidHapticTrace`).
*Sintomo:* i controlli della pagina di taratura riempiono il registro che stanno guardando. *Causa:* vanno sotto l'aptica «vera»
(`(LocalFluidHaptics.current as? TracingFluidHaptics)?.delegate`), come fa `HapticLab`.

## Come si scrive un test aptico

Tre livelli, dal più economico. Non c'è una fixture condivisa: ogni file di test si scrive la sua (10 righe).

**1. Il motore, con una porta finta (JVM puro).** È dove si provano i cancelli, le reti e i ripieghi. Implementa
`HapticPort` e inietta **l'orologio**, o due eventi nello stesso millisecondo si fondono:

```kotlin
private class FakePort(
  override val hasVibrator: Boolean = true,
  private val primitives: Set<Int> = HapticPrimitive.all.toSet(),
  override var systemHapticsEnabled: Boolean = true,
  override var powerSave: Boolean = false,
) : HapticPort {
  val compositions = mutableListOf<List<HapticPrimitiveStep>>()
  val platforms = mutableListOf<HapticFeedbackType>()
  var vibrateSucceeds = true                                   // falso: il motore deve ripiegare
  override fun supportedPrimitives(ids: IntArray) = ids.filter { it in primitives }.toSet()
  override fun vibrate(steps: List<HapticPrimitiveStep>, attention: Boolean): Boolean {
    if (!vibrateSucceeds) return false
    compositions += steps; return true
  }
  override fun platform(type: HapticFeedbackType) { platforms += type }
}

@Test fun `due eventi di controllo nello stesso momento suonano una volta sola`() {
  var now = 1_000L
  val port = FakePort()
  val haptics = FluidHapticsImpl(port, enabled = { true }, clock = { now })
  haptics.play(FluidHapticEvent.Tap)
  now += 16
  haptics.play(FluidHapticEvent.Open)                     // fuso
  assertEquals(1, port.compositions.size)
  now += FluidHapticPatterns.CoalesceWindowMillis          // 70 ms: un momento nuovo
  haptics.play(FluidHapticEvent.Open)
  assertEquals(2, port.compositions.size)
}
```

Per un test di `fluidHapticsFor` («senza `View`») la stessa porta annota `predefinedHapticFor(type)`
(`FluidViewlessHapticsTest`). Per i vocabolari: scorri `FluidHapticEvent.entries` come fanno i test esistenti.

**2. Un componente, con un `FluidHaptics` finto (Robolectric + Compose).** Il finto **registra tutto** quello che il
componente chiede, **prima** delle reti; va messo **dentro** `FluidTheme` (il tema installa il suo e sovrascriverebbe il tuo):

```kotlin
private class TickHaptics : FluidHaptics {
  override val engine = FluidHapticEngine.Composition
  val played = mutableListOf<FluidHapticEvent>()
  var grains = 0
  override fun play(event: FluidHapticEvent) { played += event }
  override fun grain(intensity: Float, style: FluidHapticGrainStyle) { grains++ }
}

FluidTheme(settings = remember { EngineSettings() }) {
  CompositionLocalProvider(LocalFluidHaptics provides haptics) { /* il componente */ }
}
// poi: onNodeWithTag("button").performTouchInput { down(center); up() }
assertEquals(listOf(FluidHapticEvent.PressDown, FluidHapticEvent.PressRelease, FluidHapticEvent.PressClick), haptics.played)
```

Con un finto **non puoi** provare «una vibrazione per momento»: la rete vive in `FluidHapticsImpl`, e il finto vede *tutte*
le chiamate. Per provarla di punta a punta avvolgi un `FluidHapticsImpl(FakePort, clock)` oppure guarda gli esiti
(`Merged`) di un `FluidHapticTrace`.

**3. Il registro (JVM puro).** `FluidHapticTrace(capacity = 16, clock = clock)` con un orologio a mano e `trace.wrap(finto)`:
si prova che inoltra tale e quale e che `Played`/`Merged`/`Dropped` dicano la stessa cosa del motore
(`FluidHapticTraceTest`).

Per la grana: `touchMove` e la grana stanno in `FluidHapticGrainTest` (pura, il tempo è quello degli eventi, nessun orologio
proprio) e in `FluidPressTest`; un trascinamento «piano non vibra, veloce sì» è in `FluidButtonDragTest` con passi da 8 ms.

## Token, costanti, Defaults

| costante | valore | dove |
|---|---|---|
| `FluidHapticPatterns.CoalesceWindowMillis` | 70 ms | la finestra della rete dei controlli |
| `FluidHapticsImpl.CONTINUOUS_MIN_GAP_MILLIS` | 40 ms | pavimento dei tick continui |
| `FluidHapticsImpl.GRAIN_MIN_GAP_MILLIS` | 8 ms | tetto della grana `Fluid` |
| `FluidHapticDetents.DETENT_MIN_GAP_MILLIS` | 55 ms | pavimento fra due tacche di un gesto (interna) |
| `FluidHapticGrainConfig.Default` | vedi la tabella della grana | di serie `style = LibrePods`, 350 px² |
| `FluidHapticTrace.DefaultCapacity` / `DefaultRows` | 240 / 20 | |
| `FluidHapticTraceGrainRunGapMillis` | 150 ms | due granelli più lontani non sono la stessa fila |
| `FluidHapticPatterns.controlEvents`, `coalescible`, `platformOnly`, `primitivesUsed` | insiemi | appartenenze |
| `HapticPrimitive.CLICK … LOW_TICK` | 1…8 | gli stessi valori di Android (API 30+), tenuti come costanti proprie perché i pattern si provano sul computer, dove `VibrationEffect` non esiste |

## Cosa è verificato dai test

- `FluidHapticsTest` (JVM): ogni evento ha composizione (o lista vuota se solo di piattaforma), ripiego e scale in 0..1;
  nessun pattern oltre 400 ms e nessun controllo oltre 120; con tutte le primitive si compone, altrimenti si ripiega **evento
  per evento**; l'interruttore dell'app zittisce; l'impostazione tattile di sistema **non** zittisce; se la composizione non
  parte resta la costante; il risparmio energetico toglie i continui e lascia i controlli; i continui si diradano a 40 ms;
  due controlli nello stesso momento suonano una volta, esiti e allerte mai scartati e i tick non armano la rete; `Open` è un
  solo `CLICK`; le allerte vibrano con l'attenzione delle notifiche; senza vibratore niente; i tre colpi del tasto in coda al
  vocabolario, a costante di piattaforma, non continui né fondibili, non armano la rete, rispettano interruttore e vibratore ma
  **non il risparmio energetico**; la grana di LibrePods (un tick a ogni evento, senza tetto) e quella dell'engine (dosata,
  col pavimento).
- `FluidHapticGrainTest`: a passo d'uomo mai, veloce un treno senza granelli più vicini del pavimento, più forte se corre,
  isteresi, passi irregolari ma stesso seme = stesso treno, eventi dello stesso istante sommati, gesto nuovo da fermo;
  per LibrePods `Δ² > 350` e nessun altro, senza memoria, soglia in pixel, tarabile.
- `FluidHapticDetentsTest`: una tacca per passo e niente prima del primo passo intero; strada contata sul percorso; un dito
  veloce non diventa un ronzio; il colpo in fondo passa sempre; afferrare in fondo non suona come arrivarci.
- `FluidHapticTraceTest` (23): decoratore tale e quale, capacità, `recording`/`clear`, fusione e scarto stimati, righe e file
  di granelli. `FluidViewlessHapticsTest`: gli id degli effetti di fabbrica sono quelli di Android (confrontati con
  `VibrationEffect`), ogni ripiego ha un effetto, la forza relativa sopravvive, il motore senza `View` compone o ripiega e
  rispetta interruttore e rete dei 70 ms.
- `FluidButtonDragTest` / `FluidButtonGripTest` (sample): un tap suona esattamente `PressDown`, `PressRelease`, `PressClick`;
  `haptic = null` non suona niente; un altro evento sostituisce il click e tiene contatto e rilascio; trascinato piano non
  vibra, veloce sì.
- **Non verificato da nessun test:** quanto *sembrano* i pattern in mano (si tarano sul telefono con «Prova i feedback»),
  e il comportamento di un vibratore reale che tronca un'altra vibrazione.

## Dove guardare

| file (`engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/haptics/`) | cosa c'è |
|---|---|
| `FluidHaptics.kt` | `FluidHapticEvent` (il vocabolario), `FluidHaptics`, `NoOpFluidHaptics`, `HapticPrimitive`, `HapticPrimitiveStep`, `FluidHapticPatterns` (pattern, ripieghi, insiemi, finestra) |
| `AndroidFluidHaptics.kt` | `HapticPort`, `FluidHapticsImpl` (cancelli, reti), `AndroidHapticPort`, `fluidHapticsFor`, `rememberAndroidFluidHaptics`, `FluidHapticsInfo` |
| `FluidViewlessHaptics.kt` | gli effetti di fabbrica (`PredefinedHaptic`) e le forme d'onda per Android 8-9 (interni) |
| `FluidHapticGrain.kt` | `FluidHapticGrainStyle`, `FluidHapticGrainConfig`, `LocalFluidHapticGrainConfig`, l'algoritmo (interno) |
| `FluidHapticDetents.kt` | le tacche dei gesti continui, 55 ms (interno; le usano `FluidSlider` e `FluidScrubber`) |
| `FluidHapticTrace.kt` | `FluidHapticTrace`, `TracingFluidHaptics`, `ProvideFluidHapticTrace`, `fluidHapticTraceRows` |

Chi guida la grana e i tre colpi è `fluid/FluidPress.kt` + `fluid/FluidButtonGesture.kt` (vedi [movimento.md](movimento.md));
la galleria per giudicarli è `sample/.../HapticLab.kt` e la sezione «Da provare».
