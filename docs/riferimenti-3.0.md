# Riferimenti 3.0: perché il vetro di LibrePods sembra più vetro del nostro

Documento di ricerca per il salto a Fluid Engine 3.0. Descrive **come si comporta** LibrePods (e il
catalogo di Kyant da cui in parte discende), con i numeri, e dice dove il nostro engine differisce.
Non è una guida di implementazione: è la specifica di comportamento da cui scriverla.

## Come leggerlo

- **Fonti.**
  - LibrePods, GPL-3.0, branch `android/rewrite`, commit `3d4d270`, 28 ottobre 2026. Sigla **LP**. I
    percorsi sono relativi a
    `android/app/src/main/kotlin/me/kavishdevar/librepods/presentation/`; il manifest è
    `android/app/src/main/AndroidManifest.xml`.
  - Kyant AndroidLiquidGlass, Apache-2.0, branch `kmp`, commit `65ab177`. Sigla **KY**. I percorsi sono
    relativi a `app/src/commonMain/kotlin/com/kyant/backdrop/catalog/`.
  - Il nostro engine, sigla **ENG**. I percorsi sono relativi a
    `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/`.
  - Haze (Chris Banes, Apache-2.0), documentazione dell'effetto Glass sul ramo principale, letta il
    31 ottobre 2026: `docs/effects/glass/index.md` in https://github.com/chrisbanes/haze. LP usa la
    2.0.1: i numeri di default di Haze qui sotto sono quelli della documentazione corrente e
    possono differire di poco.
- **Tutto è letto dal codice, nulla è misurato sul telefono.** Dove una cosa è una deduzione e non una
  lettura è marcata **[dedotto]**; dove va guardata in mano prima di fidarsene è marcata **[verificare]**.
  L'elenco completo è in fondo, nell'Appendice B.
- **Igiene di licenza.** Qui dentro non c'è codice GPL: ci sono comportamento, formule e numeri, con
  parole nostre. Il documento è stato scritto prima della scelta del 31 ottobre 2026 (il codice di LP
  si porta, sotto GPL-3.0: vedi la sezione 6), e quella regola l'ha tenuto pulito. Il codice di Kyant
  (Apache) si può citare, ed è citato poco. I riferimenti `file:riga` verso LP servono a controllare un
  numero e, per i file portati, a ritrovare da dove vengono: l'intestazione di ognuno dice il commit e
  le righe.
- **Numeri di riga dell'engine.** Quelli citati sono del codice 2.13.1 (21 ottobre), cioè di prima
  della 3.0: `fluid/FluidPress.kt`, `haptics/FluidHaptics.kt` e `haptics/AndroidFluidHaptics.kt`, fra gli
  altri, sono stati riscritti dalla fase 4, e le righe di quei file non tornano più.

---

## Un presupposto da correggere: non è tutto lo stesso renderer

Si era partiti dal fatto verificato che il vetro di LP è il `backdrop` 2.0.1 di Kyant, cioè il nostro
renderer. Vale per i controlli del corpo; **non vale per la barra in alto**. LP porta in casa due
librerie di vetro e le usa su pezzi diversi:

| Pezzo di LP | Renderer | Riferimento |
|---|---|---|
| `StyledButton` (modo Apple) | Kyant `drawBackdrop` | `components/primitives/StyledButton.kt:217-314` |
| **`StyledIconButton`** (tasto indietro e azioni della barra, ogni icona tonda) | **Haze Glass** (`hazeGlass`, haze-glass 2.0.1) | `StyledIconButton.kt:178-193` |
| Velo sfocato sotto il titolo | **Haze Blur** (`hazeBlur`) | `StyledScaffold.kt:370-376` |
| Pomello di `StyledSwitch` e `StyledSlider` | Kyant `drawBackdrop` | `StyledSwitch.kt:268-343`, `StyledSlider.kt:642-689` |
| `StyledBottomSheet`, `StyledConfirmationDialog` | Kyant `drawBackdrop` | `StyledBottomSheet.kt:65-79`, `StyledConfirmationDialog.kt:148-163` |
| Righe, gruppi, interruttori-riga, campi di testo | nessuno: tinta piatta `surfaceContainerHigh` | sezione 4 |

Conseguenze, tutte importanti per il resto del documento:

1. I tasti che a Alessio sembrano "esplodere" e "vibrare" in alto sono **Haze Glass con una scala
   propria di 1,5**, non lenti Kyant. La lente che si vede lì è quella di Haze.
2. Il default di LP è il modo **Material** (`data/app/DesignSystem.kt` e `database/app/AppSettingsEntity.kt:28`, `designSystem = Material`, sotto
   `android/app/src/main/kotlin/me/kavishdevar/librepods/`): il vetro "Apple" è un'impostazione. Il giudizio di Alessio è su quel modo.
3. Quasi tutti i `StyledButton` delle schermate ricevono `backdrop = rememberLayerBackdrop()`, cioè
   un fondale **mai registrato e quindi vuoto** (esempio: `screens/settings/AppSettingsScreen.kt:128-133`;
   altri 9 punti nelle schermate). Lì la lente non rifrange niente: restano il riempimento, il filo
   di luce sul bordo, l'ombra, la crescita e la vibrazione. **Il "vetro" dei pulsanti di LP è per
   gran parte movimento e aptica, non ottica.**

---

## Sommario: le cause, in ordine d'impatto probabile

1. **I tasti tondi in barra crescono del 50%, non del 13%.** LP porta la scala a `1 + 0,5·p` su un
   tasto da 48 sp con una molla sottosmorzata (smorzamento 0,5, rigidezza 300): picco ≈ ×1,58 a 0,21 s,
   rilascio che scende fino a ≈ ×0,92. Noi: 6 dp sul lato corto, un'icona da 44 dp passa a 50 dp
   (×1,14), picco ×1,15. La crescita assoluta è quattro volte la nostra. (`FluidPress.kt:795-809`)
2. **L'aptica è una sequenza di tre colpi per tocco, con le costanti di sistema.** Tocco giù:
   `SegmentFrequentTick`; sollevamento (anche di un tap che va a buon fine): `Reject`; click:
   `ContextClick`; in più un `SegmentFrequentTick` per ogni evento di movimento sopra ~18,7 px. Noi:
   un solo `Tap` (composizione TICK a 0,5) al click, e nessuno al contatto. Il colpo al **contatto** è
   la differenza. (`FluidInteractions.kt:166`, `FluidHaptics.kt:156`)
3. **La barra è ancorata.** Il tasto indietro della pagina *sotto* viene sollevato in un overlay di
   `SharedTransitionLayout`, controtraslato contro il parallasse della pagina, mentre quello della
   pagina che esce è nascosto; verso la radice un tasto "fantasma" rimpicciolisce, sbiadisce e si
   sfoca. Noi: la barra è dentro ogni `FluidScreen` e scorre con la pagina. (`StyledScaffold.kt:287-353`
   contro `FluidScreen.kt:1173-1340`)
4. **Il tasto risponde al contatto, senza ritardo e senza prendersi il gesto.** LP accende la pressione
   al *down*, non consuma niente, e la pagina può scorrere mentre il tasto cresce e segue il dito. Noi:
   100 ms di attesa dentro le pagine che scorrono, e dopo la pressione il gesto è del tasto. Lì il
   tocco veloce di LP "scatta" e il nostro "arriva". (`FluidPress.kt:501`, `:592-713`)
5. **Il gesto indietro è una traslazione pura, con parallasse di un terzo.** Due pagine opache in una
   sola scena: quella sopra segue il dito (o il progresso del sistema), quella sotto scorre di un
   terzo di larghezza, niente scala, niente blur, niente scrim. Costo per fotogramma quasi nullo. Noi:
   transizione di Navigation Compose cercata dal progresso, scala 0,91, angolo 30 dp, sfocatura di 6 dp
   a tutto schermo sulla pagina che esce e ricattura del vetro. (`SwipeBack.kt:198-257` contro `RouteMotion.kt:269-296`)
6. **Il vetro della barra è Haze, con luce fuori dall'angolo.** Luce da `BiasAlignment(-1,2; -1,5)`
   (in alto a sinistra, oltre il bordo), ombra di bordo nera al 20%, bordo morbido 1,25-1,5 dp, tinta
   `surfaceContainerHigh` all'80% (scuro, stile Regular) o al 40% (chiaro, stile Clear), su un velo
   sfocato di Haze alto 64 dp + status bar. (`StyledIconButton.kt:178-193`)
7. **Il bagliore sotto il dito è molto più grande e luminoso del nostro.** Raggio = lato lungo
   (Kyant: 1,5 × lato corto), velo 0,10 + punto 0,15, centro che segue il dito e torna con una molla.
   Il nostro bagliore su vetro chiaro è volutamente 0,015 + 0,055 con un raggio di 1,15 lati corti:
   quasi invisibile. (`StyledButton.kt:281-298` contro `GlassInteraction.kt:364-376`)
8. **Ogni pagina è una carta fisica.** Clip con i raggi veri degli angoli del telefono
   (`RoundedCorner`) e ombra di elevazione 36 dp (nera sul chiaro, grigia sullo scuro) che cade sulla
   pagina sotto. (`StyledScaffold.kt:127`, `:229-241`)
9. **Sobrietà: il vetro c'è solo dove si tocca.** Righe, gruppi, campi di testo sono tinte piatte con
   angolo di 28 dp e pressione solo di colore. Nel nostro engine anche carte e gruppi possono essere
   vetro, e il vetro "spreme" l'occhio dove non c'è niente da premere.
10. **Lente dei pulsanti più forte, ma qui conta poco** (spostamento 24 dp contro i nostri 14 dp, con
    profondità e dispersione): su un fondale vuoto non si vede. (`GlassMaterial.kt:584-607`)

**Perché "non era venuto altrettanto bene".** Il tentativo precedente ha copiato la forma (la `tanh`
della deriva, l'allungamento) ma con costanti più caute — crescita limitata a 14 dp, allungamento
limitato all'8%, deriva limitata a 8 dp — e ha tenuto una pressione dopo 100 ms e un colpo solo al
rilascio. Sono le tre cose che fanno la differenza in mano. In più, la parte "ancorata" e la parte
"indietro" sono architettura, non parametri: non si ottengono ritoccando `FluidPress`.

---

## 1. Pulsanti: `StyledButton` e `StyledIconButton`

### 1.1 `StyledButton` (modo Apple, interattivo): il comportamento passo per passo

Fonte: `components/primitives/StyledButton.kt` (416 righe). Il modo Material cade su bottoni M3 e qui
non interessa.

**Struttura.** Una `Row` alta 48 dp con 16 dp di margine orizzontale e 8 dp fra i figli
(`:408-410`). Sulla `Row`, in quest'ordine: il vetro (`drawBackdrop`), il click (`clickable` senza
indicazione e senza ripple), il rilevatore di gesto. Poiché il vetro è il modificatore più esterno,
la trasformazione `layerBlock` sposta, scala e allunga **anche l'etichetta**.

**Parametri.** `maxScale = 0,1` (`:95`; le schermate a larghezza piena passano 0,05, nove punti: es.
`AppSettingsScreen.kt:132`), `isInteractive = true`, `tint`, `surfaceColor`, `backdrop` (default: un
fondale vuoto, `:91`).

**Ottica** (`:220-229`): `vibrancy()` (saturazione 1,5), `blur(2 dp)`,
`lens(refractionHeight = 12 dp, refractionAmount = 24 dp, depthEffect = true, chromaticAberration = true)`.
Evidenziazione e ombra sono i default di Kyant. Superficie: se `tint` è specificata, `tint` in
`BlendMode.Hue` e poi la stessa a 0,75 di alpha; altrimenti bianco a 0,10; poi `surfaceColor` se c'è.

**Crescita.** `progress` va da 0 a 1 con una molla a smorzamento **0,5**, rigidezza **300**, soglia
0,001 (`:329`). Scala base = `1 + maxScale · progress`.

Sul tempo, per la molla (0,5; 300): ω₀ = 17,3 rad/s, sovraelongazione 16,3%, primo picco a ≈ 0,21 s.
`progress` vale ≈ 0,27 a 50 ms, ≈ 0,73 a 100 ms, ≈ 1,05 a 150 ms. Al rilascio la stessa molla riporta a
zero e **scende sotto zero** di un 16% del percorso fatto: un tasto con `maxScale 0,1` scende a ≈ ×0,984,
uno con 0,5 a ≈ ×0,92.

**Deriva elastica (il tasto segue il dito).** Con `d` lo spostamento accumulato del dito dal punto in
cui è sceso e `m` il lato corto del tasto (px):

    translation = m · tanh(0,05 · d / m)      (per x e per y, indipendenti)

Guadagno iniziale 5% e **nessun tetto** se non quello asintotico `m`: su un tasto da 48 dp la deriva è
1,0 dp a 20 dp di trascinamento, 5,0 dp a 100 dp, 14,5 dp a 300 dp, 26 dp a 600 dp. Cresce ancora molto
lontano dal punto di partenza.

**Allungamento direzionale.** Con `θ = atan2(d.y, d.x)`, `M` il lato lungo, `w`,`h` larghezza e altezza,
`maxDragScale = 0,1`:

    scaleX = scala + 0,1 · |cos θ · d.x / M| · min(w/h, 1)
    scaleY = scala + 0,1 · |sin θ · d.y / M| · min(h/w, 1)

È **lineare e senza tetto** (a 400 dp di trascinamento su un tasto da 160 dp: +25%). Non c'è `tanh`
sull'allungamento, ed è la parte che più "deforma" il tasto. Le formule di deriva e allungamento sono
quelle di `LiquidButton.kt:63-87` di Kyant, **identiche** (vedi 1.4).

**Ritorno.** Rilasciato (o annullato) il gesto, `d` torna a zero con una molla **criticamente
smorzata** (smorzamento 1, rigidezza 300, `:331`): niente rimbalzo sulla deriva e sull'allungamento.
Rimbalza solo la scala. Kyant usa 0,5 anche per questo.

**Bagliore sotto il dito** (`:277-311`, tutto sotto API 33 degrada a un velo bianco di 0,25·progress):

1. un velo bianco `Plus` di alpha **0,10 · progress** su tutta la faccia (Kyant: 0,08);
2. uno shader AGSL: colore bianco di alpha **0,15 · progress** per un'intensità
   `smoothstep(raggio, raggio · 0,5, distanza dal punto)`, cioè 1 entro mezzo raggio e poi giù fino a 0
   al raggio, mescolato in `Plus`;
3. **raggio = lato lungo del tasto** (`:293`; Kyant: 1,5 × lato corto, `InteractiveHighlight.kt:73`).
   Su un tasto da 160×48 dp: 160 dp contro 72 dp. Il plateau (mezzo raggio) copre da solo mezzo tasto;
4. **centro** = punto di discesa + `d` animato, bloccato dentro il tasto (`:286-298`). Segue il dito 1:1
   e, al rilascio, torna al punto di discesa con la molla critica di cui sopra.

**Gesto** (`inspectDragGestures`, `utils/DragUtils.kt:33-57`):

- *Quando parte.* Al *down*: la prima lettura è nel passaggio Initial, la conferma nel Main dello stesso
  evento. Nessuna soglia di movimento, **nessun ritardo**: `onDragStart` e la crescita partono al
  contatto.
- *Cosa consuma.* **Niente.** Non chiama mai `consume()`. Il ciclo di trascinamento si interrompe solo se
  qualcuno ha già consumato il cambio quando il Main arriva a lui (un genitore che lo consuma
  nell'Initial) o se il puntatore sparisce.
- *Con la pagina che scorre.* [dedotto] Un `verticalScroll` genitore consuma nel Main **dopo** il
  figlio, quindi il ciclo del tasto non vede mai il consumo: **il tasto continua a crescere e seguire il
  dito mentre la pagina scorre**. Il click (`clickable`) invece si annulla quando lo scorrimento
  consuma. Risultato: un dito che parte su un tasto in una lista fa partire la lista *e* il tasto, e
  al sollevamento vibra senza cliccare. Va visto sul telefono ([verificare], vedi Appendice B).
- *Ritardo del click dentro uno scorrevole.* Nessuno per la pressione; il click è quello di `clickable`.

**Aptica** (vedi la tabella 1.3): tocco giù, movimento sopra soglia, rilascio, click.

**Variante non interattiva** (`isInteractive = false`, `:176-215`): niente lente e niente gesto;
`blur(16 dp)`, riempimento piatto (`surfaceContainerHigh`, o `surfaceColor`), e se premuto scurisce
(nero 0,4 + bianco 0,2, o `surfaceColor` al 50%). Click con `ContextClick` da `detectTapGestures`.

### 1.2 `StyledIconButton` (il tasto tondo della barra)

Fonte: `StyledIconButton.kt` (277 righe). **Non usa Kyant.**

- **Misura.** `size(48.sp.toDp())` (`:245`): l'area **scala con la dimensione del carattere**. Margine
  esterno 12 dp per lato (`:148`), contenuto con 8 dp di respiro (`:248`).
- **Crescita.** Stesse molle del `StyledButton` (0,5; 300 per la scala, 1; 300 per la deriva), ma
  **la scala massima è 1,5**: `scala = 1 + 0,5 · progress` (`:160`). Un tasto da 48 dp arriva a 72 dp
  a regime e a ≈ 76 dp al picco di rimbalzo (×1,58), e al rilascio scende a ≈ ×0,92 (44 dp).
- **Deriva e allungamento.** Le stesse formule di 1.1, con `m = 48 dp`, `maxDragScale = 0,1` (`:162-176`).
  Applicate con `graphicsLayer` **prima** del vetro: il vetro è disegnato *dentro* il layer scalato.
- **Vetro** (Haze Glass, `:178-193`):
  - tema scuro: `GlassStyle.regular` più forma tonda, `lightPosition(BiasAlignment(-1,2; -1,5))`,
    `edgeShadow(nero 0,2)`, `edgeSoftness(1,5 dp)`, `tint(surfaceContainerHigh a 0,8)`;
  - tema chiaro: `GlassStyle.clear` con gli stessi, `edgeSoftness(1,25 dp)`, tinta a 0,4.
  - `surfaceColor` e `materialButtonStyle` sono parametri **inutilizzati** nel modo Apple.
- **Cosa fanno gli stili di Haze** (documentazione corrente, numeri di default):
  - `regular`: rifrazione solo sul bordo (`Edge(20 dp)`), forza 0,7, spostamento 48 dp (limitato a metà
    lato corto, quindi ≈ 24 dp per un tasto da 48), frazione d'altezza 0,15; sfocatura e profondità
    crescono col lato corto della superficie: "i controlli piccoli tengono un po' di dettaglio rifratto
    e un bordo curvo".
  - `clear`: `Edge(28 dp)`, forza 0,85, spostamento 56 dp, frazione 0,35, dettaglio di bordo 0,76, stessa
    sfocatura bassa a ogni misura.
  - Comuni: intensità speculare 0,48, risposta ambientale (Fresnel) 0,15, ombra di bordo nera al 14%
    (LP la porta al 20%), bordo morbido 1 dp (LP 1,25-1,5), nessuna dispersione cromatica.
  - Sotto un renderer di ripiego Haze **omette** rifrazione e sfocatura.
  - LP non usa l'interazione integrata di Haze (`pressed { ... }`): la crescita è il `graphicsLayer` suo.
- **Gesto, area consumata.** Il gesto è identico a quello di `StyledButton` (nessun consumo). Ma nella
  barra il contenitore del tasto **consuma ogni cambio in ingresso** (`StyledScaffold.kt:294-308`,
  `:406-420`): essendo genitore del tasto, lo fa dopo che il tasto ha visto l'evento, e impedisce che
  il trascinamento orizzontale della pagina (gesto indietro) o lo scorrimento partano da lì.
- **Aptica** (`:154`, `:198`, `:219`, `:235-239`): stessa della 1.3.
- **[dedotto/verificare]** Il layer scalato contiene il vetro; Haze campiona la sorgente alla posizione
  *non scalata*: a ×1,5 il tasto potrebbe mostrare uno sfondo ingrandito invece di uno corretto per la
  trasformazione, come fa il `layerBlock` di Kyant. Se è così, fa parte del suo carattere ("lente che
  si gonfia"), e noi dobbiamo decidere se vogliamo lo stesso o il rifrangere corretto.

### 1.3 Aptica di LP, momento per momento

Tutta con `LocalHapticFeedback.performHapticFeedback(HapticFeedbackType.…)` di Compose (che sull'Android
34+ diventa la costante `View.HapticFeedbackConstants` omonima, tarata dal produttore del telefono).
Nessun `VibrationEffect`, nessuna composizione di primitive, nessuna app di taratura.

| Momento | Tipo | Dove |
|---|---|---|
| Dito sceso su un tasto o un'icona (inizio del gesto, prima di muoversi) | `SegmentFrequentTick` | `StyledButton.kt:361-363`; `StyledIconButton.kt:219` |
| Il dito si muove: ogni evento con spostamento al quadrato > 350 (≈ 18,7 px) | `SegmentFrequentTick` | `StyledButton.kt:382-384`; `StyledIconButton.kt:235-239` |
| Gesto finito o annullato, **anche un tap che poi clicca** | `Reject` | `StyledButton.kt:336-338`; `StyledIconButton.kt:198` |
| Click | `ContextClick` | `StyledButton.kt:321`, `:400` (non interattivo); `StyledIconButton.kt:154` |
| Interruttore: cambio di `checked` (tocco, trascinamento, programmatico) | `ToggleOn` / `ToggleOff` | `StyledSwitch.kt:139-145` |
| Interruttore: durante il trascinamento il pomello passa 0,5 | `SegmentTick` | `StyledSwitch.kt:234-236` |
| Cursore: il valore attraversa un punto di aggancio | `SegmentTick` | `StyledSlider.kt:613-621` |
| Riga di lista, tap | `ContextClick` | `StyledListItem.kt:826-828` |
| Riga con interruttore, tap | `ToggleOn`/`ToggleOff` (e quello dell'interruttore, quindi due) | `StyledToggle.kt:314` |
| Gesto indietro, cambio di pagina, apertura di foglio o pop-up | **nessuno** | — |

Un tocco su un tasto fa quindi **tre impulsi in 60-150 ms** (tick al contatto, `Reject` al sollevamento,
`ContextClick` al click; l'ordine fra gli ultimi due è [dedotto]: il rilevatore interno vede il
sollevamento prima del `clickable` esterno). Un dito che scorre veloce sul tasto fa un
`SegmentFrequentTick` per ogni evento di movimento sopra ~18,7 px. A 60 Hz sono ≈ 1.100 px/s, cioè circa
420 dp/s su un telefono da 2,6 di densità: sopra quella velocità il tasto "ronza" a ogni fotogramma.

`Reject` suona a ogni rilascio, anche riuscito. Il nome è sbagliato per il caso (significa "rifiutato"), ma
è parte del carattere: sul telefono dà una coda al tocco.

### 1.4 Cosa è di Kyant e cosa è di LP

| Pezzo di LP | File Kyant | Identico | Modificato da LP |
|---|---|---|---|
| `inspectDragGestures` (`utils/DragUtils.kt`) | `utils/DragGestureInspector.kt` | l'intero algoritmo | solo i nomi |
| Deriva `tanh` e allungamento di `StyledButton`, e di `StyledIconButton` | `components/LiquidButton.kt:63-87` | formule, `maxOffset = minDimension`, `0,05`, i fattori `(w/h)` e `(h/w)` | scala `1 + maxScale` (10%) invece di `1 + 4dp/h`; `maxDragScale 0,1` invece di `4dp/h`; nel tasto tondo la scala è 1,5 |
| Evidenziazione sotto il dito | `utils/InteractiveHighlight.kt:41-58` (shader) | lo shader AGSL `smoothstep(raggio, raggio·0,5, d)` | raggio `maxDimension` invece di `1,5·minDimension`; velo 0,10 invece di 0,08; il centro è `partenza + d` animato; molla del centro critica (1) invece di 0,5 |
| Ottica del tasto | `LiquidButton.kt:58-62` | `vibrancy()` + `blur(2 dp)` + `lens(12 dp, 24 dp)` | in LP in più `depthEffect = true` e `chromaticAberration = true` |
| `tint`, `surfaceColor` | `LiquidButton.kt:91-99` | `Hue` + 0,75 | bianco 0,10 di default; varianti di pressione |
| `MomentumAnimation` del cursore | `utils/DampedDragAnimation.kt` | la struttura (quattro `Animatable`, `press`/`release` che lanciano tre molle, `VelocityTracker`) | niente animazione del valore, niente attesa al rilascio, molle diverse |
| Pomello del cursore | `components/LiquidSlider.kt:146-210` | superficie `bianco(1-p)`, `blur 8 dp (1-p)`, `Highlight.Ambient`, `Shadow 4 dp / 0,05`, `InnerShadow 4 dp·p`, formula di allungamento con la velocità | parametri della lente, niente copia della pista che si dispiega |
| Pomello dell'interruttore | `components/LiquidToggle.kt` | pista 64×28, pomello ≈ 40×24, scala 1,5, evidenziazione ambient, ombra interna | gesto, molle e copia della pista riscritti |
| Ricetta del vetro di foglio e pop-up | `destinations/DialogContent.kt:66-67` (lente 24/48 dp) | forma della ricetta | LP: blur 4 dp, `lens(12, 48, true)`, `vibrancy`, tinta più opaca |

**Originale di LP** (non da Kyant): tutta l'aptica; la barra, il gesto indietro e la navigazione; il
tasto tondo su Haze; la scala 1,5 del tasto tondo; i parametri del cursore e dell'interruttore; le righe.

### 1.5 Come siamo noi

| Aspetto | LP | ENG | Dove da noi |
|---|---|---|---|
| Crescita tasto con testo | `maxScale` 0,10 (0,05 a larghezza piena) | 6 dp sul lato corto, tetto 14 dp sul lato lungo e 14%: medio ≈ +8,75%, a larghezza piena ≈ 3,9% | `FluidPress.kt:795-809` |
| Crescita tasto tondo | **×1,5** (+24 dp su 48) | lente da 44 dp: ×1,136 (+6 dp), picco ≈ ×1,15 | `FluidGlassControls.kt:36-37`, `FluidPress.kt:807` |
| Molla di salita | 0,5; rigidezza 300 (0,27 a 50 ms, 0,73 a 100 ms, picco 1,16 a 0,21 s) | 0,62; (2π/0,22)² ≈ 816 (0,53 a 50 ms, ≈ 1 a 100 ms, picco 1,08 a 0,14 s) | `FluidPress.kt:209` |
| Molla di discesa | 0,5; 300 | 0,5; (2π/0,32)² ≈ 386 | `FluidPress.kt:210` |
| Il rilascio | parte subito dal valore corrente (un tap breve cresce poco) | **aspetta** che la salita arrivi al 96% | `FluidPress.kt:132-194`, `:190` |
| Deriva | `m·tanh(0,05 d/m)`, tetto asintotico `m` (48 dp) | `r·tanh(0,15 d/r)`, `r = min(8 dp, lato corto/6)`: ≈ 2,9 dp a 20 dp, **7,1 dp a 100 dp, 7,3 dp a 300 dp** | `FluidPress.kt:769-771`, `:814-818` |
| Allungamento | lineare, senza tetto (+25% a 400 dp) | `tanh`, tetto **8%** (`StretchMax`), assottigliamento di traverso 0,5 | `FluidPress.kt:772-778`, `:821-824` |
| Ritorno della deriva | molla critica | segue la molla di rilascio della scala (0,5) | `FluidPress.kt:210` |
| Lente del tasto | blur 2 dp; `lens(12, 24, depth, chroma)` | blur `1,4 × 2,5` = 3,5 dp, `lens(10, 14, depth, dispersione)`, +60% di spostamento a pressione piena | `GlassMaterial.kt:584-607`, `:1189-1200` |
| Bagliore | raggio lato lungo; velo 0,10; punto 0,15 | scuro: numeri Kyant (1,5×lato corto, 0,08; 0,15); **chiaro su vetro: velo 0,015, punto 0,055, raggio 1,15, plateau 0** | `GlassInteraction.kt:335-376` |
| Quando parte la pressione | al contatto | 100 ms dopo, dentro le pagine che scorrono; al contatto solo in chrome (`LocalFluidPressImmediate`) e con il mouse | `FluidPress.kt:501`, `:555`, `:614` |
| Cosa fa del gesto | non consuma, la pagina può scorrere | consuma tutto dopo i 100 ms, la pagina sta ferma | `FluidPress.kt:592-713` |
| Aptica | contatto + movimento + rilascio + click, costanti di sistema | un `Tap` al click; grana 3.0 in movimento (in corso) | `FluidInteractions.kt:166`, `FluidHaptics.kt:156` |

La differenza si somma: più crescita, un tasto che si accende prima, una vibrazione che comincia prima.

---

## 2. `StyledScaffold` e navigazione: barra ancorata e gesto indietro

### 2.1 Architettura in una frase

**Una sola scena contiene due pagine impilate; l'overlay di un `SharedTransitionLayout` serve a tenere
il tasto indietro sopra a entrambe; due `CompositionLocal` dicono a ogni barra a che punto è la
transizione.** Nessun `sharedElement`/`sharedBounds`, nessuna barra condivisa, nessuna "barra
dell'applicazione" separata dalle pagine.

I pezzi:

1. **`NavDisplay` di Navigation3** (`navigation/AppNavGraph.kt:163-191`), con le tre transizioni
   (`transitionSpec`, `popTransitionSpec`, `predictivePopTransitionSpec`) tutte a `None`. Navigation3
   cambia scena all'istante, senza animazione: tutto il movimento è di LP.
2. **Una `SceneStrategy` propria** (`navigation/SwipeBack.kt:40-87`). A ogni cambio di back stack
   calcola un'unica `Scene` con `currentEntry = ultima voce` e `previousEntry = penultima`, e una
   direzione: assente (prima volta), **indietro** se la voce corrente era già nella lista precedente,
   **avanti** altrimenti. La chiave della scena è `contentKey_hashCode` della voce corrente.
3. **Il contenuto della scena** (`SwipeBackSceneContent`, `:89-264`): una `Box` con la pagina sotto
   (`previousEntry.Content()`, `LocalIsCurrentEntry = false`) e la pagina sopra
   (`currentEntry.Content()`, `LocalIsCurrentEntry = true`). **La pagina sotto resta sempre composta
   e disegnata**, anche a riposo, dietro a quella opaca.
4. **`SharedTransitionLayout`** intorno a `NavDisplay` (`AppNavGraph.kt:159-161`), solo per ottenere
   `renderInSharedTransitionScopeOverlay`: non si condivide nessun elemento.
5. **`LocalIsCurrentEntry`** (booleano) e **`LocalTransitionProgress`** (float con segno), in
   `navigation/Transition.kt:6-9`.
6. **Un canale di richieste di "indietro"** (`backRequests`, `AppNavGraph.kt:105`): il tasto indietro
   non rimuove la voce, **manda una richiesta** alla scena corrente, che anima l'uscita e poi rimuove.
   Lo stesso canale è usato per disfare più pagine in fila se il dispositivo si disconnette
   (`:130-137`).

### 2.2 Lo stato che pilota tutto

Nella scena (`SwipeBack.kt:103-121`):

- `animatedOffset`: un `Animatable<Float>` in pixel. Parte da `W` (larghezza dello schermo,
  `LocalWindowInfo.containerDpSize.width`) se la direzione è *avanti*, da 0 altrimenti.
- `transitionProgress`: uno stato float con segno: **positivo e discendente (1 → 0) nella transizione
  avanti, negativo e discendente (0 → −1) nell'indietro, 0 a riposo**. Cambia a ogni fotogramma e
  viene letto nella composizione (`LocalTransitionProgress provides …`), quindi ricompone le barre.
- Parallasse della pagina sotto: `translationX = −(W / 3) · (1 − p)`, con `p = animatedOffset / W`
  limitato a 0..1 (`:205-212`). Ferma, `p = 0`: la pagina sotto sta a −W/3, coperta.
- Pagina sopra: `translationX = animatedOffset` (`:220-225`).

Tutto è `graphicsLayer` che legge lo stato **nella fase di disegno**: nessuna ricomposizione per il
movimento delle pagine. Niente scala, niente alpha, niente blur, niente scrim sulla pagina sotto.

### 2.3 Chi sta dove nella barra

Il modo Apple di `StyledScaffold` (`components/primitives/StyledScaffold.kt:223-438`) disegna, **dentro
la pagina**, quattro strati:

| Strato | z | Dove | Si muove con la pagina? |
|---|---|---|---|
| Contenuto (con `hazeSource`) | 0 | `:429-435`, riceve `topPadding + 64 dp` | sì |
| Velo sfocato e titolo | 2 | `:355-390`, alto `64 dp + status bar`, `hazeBlur` sulle sorgenti, titolo centrato in `Crossfade` | sì |
| Azioni a destra | 3 | `:392-427`, `AnimatedVisibility(fade + scala da 0, tween; uscita scala 0,5 in 100 ms)` | **sì** |
| Tasto indietro | 3 | `:287-353`, vedi sotto | **no, ancorato** |

- **Le azioni a destra e il titolo NON sono ancorati.** Sono nella pagina e scorrono con lei; quelle
  della pagina che sta sotto arrivano col parallasse. L'`AnimatedVisibility` fa entrare e uscire le
  azioni solo se `visible` o la lista cambiano dopo la composizione (con `visible = true` all'origine non
  si anima). Nessuna schermata usa `visible`.
- **Il titolo** si cambia con un `Crossfade` di 300 ms (default) solo se la stringa cambia dentro la
  stessa pagina. Fra pagine non c'è nessun passaggio: ognuna ha il suo.
- **Solo il tasto indietro è ancorato**, e solo per la pagina *sotto*.

### 2.4 Il tasto indietro: i quattro casi

Variabili: `showBackButton = (progress == 0) ? navigateBack ≠ null : !isCurrentEntry` (`:263`). Quindi,
**appena la transizione parte, la pagina corrente smette di disegnare il suo tasto, e la pagina sotto
lo disegna sempre**, anche se sotto non ha un tasto proprio. Il tasto è reso nell'overlay (sopra a tutto,
z 3) quando `!isCurrentEntry && progress ≠ 0` (`:309-314`).

| Caso | Tasto della pagina che esce | Tasto della pagina sotto |
|---|---|---|
| **A riposo** | disegnato dalla pagina corrente, se `navigateBack ≠ null` | non visibile |
| **Indietro**, la pagina sotto ha un tasto indietro | nascosto dal primo fotogramma | sollevato nell'overlay e **controtraslato** di `+(W/3)·(1−p)`: annulla il parallasse della sua pagina e resta fermo sullo schermo. Nessuna scala, alpha o blur. A fine corsa la scena cambia e quel tasto *è* il tasto della pagina ora corrente, **nello stesso punto**: giuntura invisibile |
| **Indietro verso la radice** (sotto non c'è tasto) | nascosto | sollevato comunque, come "fantasma", controtraslato come sopra; con `s = smoothstep(p)`: **scala `1 − 0,18·s`**, **alpha `1 − 0,28·s`**, **blur `8·p` px** (`RenderEffect`, in pixel, non dp). Parte identico al tasto reale e si spegne mentre si arriva; a fine corsa sparisce di colpo (alpha finale 0,72) |
| **Avanti** | la pagina nuova non ha tasto finché `progress ≠ 0` | sollevato, **senza** controtraslazione e senza scala/alpha/blur (quei rami valgono solo per `progress < 0`): [dedotto] scorre a sinistra col parallasse della sua pagina (da 0 a −W/3 in 220 ms) e a fine corsa compare quello della pagina nuova, senza animazione. Sembra una parte non rifinita |

Il contenitore del tasto consuma ogni cambio del puntatore (`:294-308`): dal tasto non parte né il
trascinamento indietro né lo scorrimento.

Un `Box` vuoto con `hazeSource` è reso nell'overlay con z 1 mentre dura una transizione
(`:275-285`): serve probabilmente a dare qualcosa da campionare al vetro Haze del tasto sollevato;
l'effetto non si deduce dal codice [verificare]. Un'altra cosa da guardare: il tasto sollevato
campiona le sorgenti Haze della *propria* pagina, cioè quella coperta.

### 2.5 Il gesto indietro

**Due sorgenti, lo stesso risultato.** (`SwipeBack.kt:152-257`)

**(a) Indietro di sistema, predittivo.** `PredictiveBackHandler(enabled = previousEntry ≠ null)`,
abilitato dal manifest (`android:enableOnBackInvokedCallback="true"`, `AndroidManifest.xml:58`).

- Mentre il dito scorre dal bordo, ogni evento porta `progress` (0..1, deciso dal sistema).
- La pagina sopra sta a `progress · W`, con `snapTo`; `transitionProgress = −progress`.
- **Non è la coordinata x del dito.** È il progresso che la piattaforma ricava dallo spostamento: la
  mappatura dal dito alla pagina è di Android, e dito e pagina non stanno sullo stesso pixel [dedotto].
  Nessun movimento verticale.
- *Conferma* (il flusso termina normalmente): `animateTo(W, tween(150))` e poi si rimuove la voce.
- *Annullo* (cancellazione del flusso): `animateTo(0, tween(150))`.
- Con tre pulsanti o tasto Indietro il flusso non emette niente e termina: la stessa uscita da 150 ms.
- Il gestore è disattivato alla radice (`enabled = previousEntry ≠ null`).

**(b) Trascinamento ovunque** (`draggable` orizzontale sulla pagina sopra, `:226-257`), attivo se
`swipeAnywhereForBack` (default **vero**, `AppSettingsEntity.kt:75`) e se c'è una pagina sotto.

- Segue il dito: `offset = max(0, offset + delta)`, con `snapTo`. **1:1 in pixel**, solo verso destra.
  Parte quando il dito supera la soglia di movimento di `draggable`, quindi la pagina sta dietro al dito
  di un ordine di grandezza di quella soglia [dedotto]. `transitionProgress = −offset / W`.
- Parte da qualunque punto della pagina; gli interni che consumano per primi (cursore, interruttore,
  gesti di figli) vincono, perché i figli leggono prima. Dal tasto della barra non parte (2.4).
- *Rilascio:* **conferma se `offset > 0,35 · W` oppure `velocità > 1000` px/s** (`:232`; 1000 px/s sono
  ≈ 380 dp/s a 2,6 di densità). Altrimenti torna a 0.
- *Come:* `animateTo(W o 0, tween(150))` con la velocità iniziale del dito **passata ma inutile**:
  `tween` la ignora. Quindi l'uscita è una curva di 150 ms (`FastOutSlowIn`) di durata fissa, anche
  se mancano cinque pixel. Non c'è continuità di velocità col dito.

**Tasto indietro toccato.** `navigateBack` manda una richiesta nel canale (`RenderScreenContent.kt:95-98`);
la scena corrente anima `animatedOffset` da 0 a `W` con `tween(150)` e poi rimuove la voce: stessa
animazione, stesso ancoraggio.

**Aptica.** Nessuna per il gesto indietro, né a soglia né a conferma.

### 2.6 La transizione avanti

Alla comparsa di una scena *avanti*, la pagina nuova entra da destra: `animatedOffset` da `W` a 0 con
`tween(220)` (`FastOutSlowIn`, `:123-136`). La pagina sotto scorre a sinistra di un terzo (parallasse).
Niente scala, niente scrim, niente blur. Alla fine `transitionProgress = 0`.

### 2.7 La pagina come carta

Ogni `StyledScaffold` (modo Apple) è un `Scaffold` con (`:127`, `:229-241`):

- `clip(screenCutoutShape)`: forma con i **raggi veri degli angoli del telefono**, letti da
  `rootWindowInsets.getRoundedCorner(...)` (API 31+, altrimenti 0), alto e basso a sinistra;
- `shadow(elevation = 36 dp, shape = screenCutoutShape)` con colori d'ombra **neri** se la superficie è
  chiara e **grigi** se è scura.

Quando una pagina scorre sopra l'altra, la sua ombra di 36 dp cade sulla pagina sotto, e i suoi angoli
sono quelli del vetro del telefono. Insieme al parallasse è la parte che dà peso fisico alla pagina.

### 2.8 Costo per fotogramma

Dentro il gesto: due traslazioni di layer già registrati. Niente blur a tutto schermo, niente scala,
niente nuova cattura. Il costo extra è la ricomposizione: la scena legge `transitionProgress` nella composizione, quindi si
ricompone a ogni fotogramma, e con lei i lettori di `LocalTransitionProgress` (le barre). In più la pagina
sotto resta sempre composta. `useHighestRefreshRate` (120 Hz,
`MainActivity.kt:100-110`) è di serie **spento**: non è una causa.

### 2.9 Come siamo noi

| Aspetto | LP | ENG | Dove da noi |
|---|---|---|---|
| Dove sta la barra | dentro ogni pagina, ma il tasto indietro della pagina sotto sta in un overlay | dentro ogni `FluidScreen`, scorre con la pagina | `FluidScreen.kt:1173-1340`, `:1382-1396` |
| Registro di chrome | nessuno (due `CompositionLocal`) | `FluidChromeController` registra i fondali, elegge quello "davanti"; **sa già** chi è davanti | `FluidScreen.kt:135-264`, `RouteMotion.kt:217-247` |
| Transizione avanti | scorrimento da destra, 220 ms, parallasse −W/3 | crescita dal punto toccato: scala 0,91, angolo 30 dp, sfocatura di 6 dp sulla pagina che esce, 300 ms | `RouteMotion.kt:109-126`, `:269-296`, `FluidMotion.kt:158` |
| Gesto indietro | `draggable` ovunque (1:1) + `PredictiveBackHandler` (progresso) | la `NavHost` dell'app (`FluidRouteMotionHost` è il solo pezzo nostro, un involucro per destinazione): il progresso del sistema fa da cursore della transizione | `RouteMotion.kt:221-300` |
| Conferma | 35% di larghezza o 1000 px/s, poi `tween(150)` | decide la libreria di navigazione | — |
| Pagina coperta | traslazione −W/3, nessun'altra trasformazione | sfocatura di 6 dp (`ExitBlur`) applicata dall'involucro dell'engine; la scala `ExpandParentScale = 1,04` è dichiarata e la applica la `NavHost` dell'app; il blur a tutto schermo è il costo | `RouteMotion.kt:111-112`, `:126`, `:250-257` |
| Aspetto della pagina | angoli del telefono + ombra 36 dp | angolo 30 dp finché non è tutto schermo | `RouteMotion.kt:123`, `:278-289` |
| Costo | due traslazioni | scala + clip + `RenderEffect` blur a tutto schermo + ricattura del vetro (`glassRelaxAt` abbassa la qualità) | `RouteMotion.kt:144-185` |
| Ancoraggio | tasto indietro della pagina sotto in overlay | nessuno | — |

### 2.10 Punti incerti

- La resa del tasto sollevato in *avanti* (deriva e comparsa brusca): dedotta dal codice, mai vista.
- Se l'overlay applichi davvero la trasformazione dei genitori alla posizione del tasto: la
  controtraslazione di LP lo presuppone; se non fosse così il tasto si muoverebbe invece di stare fermo.
  Il comportamento in mano è quello che Alessio descrive (resta fermo), quindi il presupposto regge.
- Se il tasto sollevato, che campiona la sorgente Haze della propria pagina, mostri sotto di sé la
  pagina sbagliata durante la corsa.

---

## 3. `StyledSlider` e `StyledSwitch` contro `FluidSlider` e `FluidSwitch`

I nostri sono **port fedeli di Kyant** (`LiquidSlider`, `LiquidToggle`) con l'aggiunta di gesto, aptica e
accessibilità. Quelli di LP **non lo sono**: ne riprendono l'aspetto con una meccanica diversa.

### 3.1 Cursore

| | KY `LiquidSlider` | LP `StyledSlider` | ENG `FluidSlider` |
|---|---|---|---|
| Pomello | 40×24, capsula | 40×24, `RoundedCornerShape(28 dp)` (capsula), `StyledSlider.kt:694` | come KY |
| Posizione | il valore è animato: molla (1; 1000; soglia) → il pomello **ritarda** ≈ 150 ms dal bersaglio | **nessuna animazione del valore**: la posizione è la frazione del `value` dato, il pomello è rigido sotto il dito | come KY (`FluidSlider.kt:209`) |
| Scala | 1,5 (`pressedScale`) | 1,5 (`StyledSlider.kt:408`) | 1,5 (`:215`) |
| Molla scala X / Y | 0,6 / 0,7, rigidezza 250 | **0,4 / 0,6, rigidezza 400** (`:113-116`): più elastico e più veloce | come KY |
| Molla di pressione | (1; 1000; 0,001) | (1; 1000; 0,01) | come KY |
| Velocità | molla (0,5; 300) alimentata dalla velocità del valore (in range/s); `v/10`, tetto ±0,2 | molla (0,5; 250; 5) alimentata dal `VelocityTracker` del dito in px/s; **`v/5000`**, tetto ±0,15 (`:665-673`) | come KY ma col **modulo** della velocità (`FluidSlider.kt:520`) |
| Segno | un verso accorcia il pomello | idem (rimasto) | corretto: modulo |
| Lente | `blur 8 dp·(1−p)`, `lens(10 dp·p, 14 dp·p, chroma)` | `blur 8 dp·(1−p)`, `lens(6 dp·p, altezza/2·p = 12 dp·p, depth, chroma)` (`:679-688`) | come KY (`:498-499`) |
| Evidenziazione | `Ambient` con larghezza e sfocatura /1,5 | `Ambient` piena, alpha = p | come KY |
| Ombre | `Shadow 4 dp / 0,05`; `InnerShadow 4 dp·p, alpha p` | uguali | uguali |
| Copia della pista sotto la lente | dispiegata: `scale(lerp(2/3,1,p), lerp(0,1,p))` | no: la pista com'è | come KY |
| Tocco sulla pista | sì, `animateToValue` | no | sì (gesto su tutta la riga) |
| Aggancio | nessuno | `snapPoints` + `snapThreshold = 0,05` **in unità del valore**, aptica `SegmentTick` al passaggio | `snapValues`, soglia come frazione (0,03), aptica `Tick` |
| Gesto | `DampedDragAnimation` senza consumo | `draggable` con `startDragImmediately = true`: **consuma subito, senza soglia** | soglia di mezzo slop in orizzontale, poi consuma |
| Fine | `onValueChange(target)` | arrotonda a due decimali | — |

Annotazione sull'aggancio di LP [dedotto]: ogni delta di trascinamento viene sommato al valore **già
agganciato** e poi agganciato di nuovo. Con soglia in unità del valore (0,05-0,1 su un range 0-2) un
dito lento può non uscire mai dal punto: il pomello "si incolla" e si stacca solo con una mossa veloce.
Se è voluto è un carattere, se no un difetto: non da copiare senza guardarlo.

### 3.2 Interruttore

| | KY `LiquidToggle` | LP `StyledSwitch` | ENG `FluidSwitch` |
|---|---|---|---|
| Pista | 64×28 | 64×28 | 64×28 (`FluidSwitch.kt:493`) |
| Pomello | 40×24 | **39×24** | 40×24 (`:495`) |
| Corsa del pomello | `dragWidth = 20 dp`, frazione = spostamento/20 dp: 1:1 | corsa ≈ 64−39−4 = 21 dp ma la frazione cresce di `dx / 64 dp`: **il pomello fa ≈ 0,33 px per ogni px del dito** (`StyledSwitch.kt:219`, `:225`) | 20 dp, 1:1 (`:500`) |
| Posizione | valore animato (1; 1000) | molla della frazione (0,5; 300; 0,001): rimbalza, **supera la fine di ≈ 3,4 dp** e non è ritagliato; in trascinamento la frazione è limitata a −0,3..1,3 (`:129`, `:225`) | come KY |
| Scala di pressione | 1,5 con due molle (X 0,6 / Y 0,7; 250) | 1,5 con **una** molla (0,5; 300; 0,001): scala uniforme `lerp(1, 1,5, p)` (`:285`) | come KY |
| Su commutazione senza dito | pressione che sale e scende con le molle | **impulso a tempo**: `tween(175)` su e `tween(175)` giù (`:153-154`), 350 ms | come KY |
| Allungamento con la velocità | `v/50`, tetto ±0,2 | nessuno | come KY (con modulo, `:441`) |
| Lente | `blur 8·(1−p)`, `lens(5·p, 10·p, chroma)` | `lens(6 dp, altezza/2 = 12 dp, depth, chroma)` **costante**, senza blur (`:335-342`); il bianco opaco a riposo la copre | come KY (`:423`) |
| Copia della pista | schiacciata e dispiegata (`2/3 → 0,75` in X, `0 → 0,75` in Y) | un `scale(0,7)` costante del fondale sotto un rettangolo di colore (`:289-305`) | come KY |
| Ombra interna | `InnerShadow 4 dp·p` | disegnata a mano: 4 dp di raggio e spostamento, nero 0,2·p (`:306-334`) | come KY |
| Aptica | — | `ToggleOn`/`ToggleOff` a ogni cambio di `checked` (`:139-145`) + `SegmentTick` al passaggio di 0,5 nel trascinamento (`:234-236`): **al passaggio sono due** | `ToggleOn`/`ToggleOff` al passaggio con isteresi dell'8% e al tocco (`:234`, `:537`) |
| Gesto | pomello soltanto | pomello soltanto, `draggable` con `startDragImmediately` | un gesto su tutto il controllo (2.13.1) |
| Soglia di commutazione | 0,5 al rilascio | 0,5 **durante** il trascinamento (cambia lo stato subito) | 0,5 al rilascio; la vibrazione al passaggio |

L'interruttore di LP è, in sostanza, **peggiore** del nostro come meccanica (corsa a un terzo del dito,
rimbalzo oltre la pista). Quello che ha di buono è l'aptica e la scala di 1,5 con un rimbalzo morbido.
Non c'è niente da portare di suo salvo, volendo, la molla della scala a 0,5.

---

## 4. Il resto: righe, liste, interruttori-riga, foglio, pop-up, campo di testo

Poca ottica e nessun movimento di pressione: la coerenza viene dalla sobrietà.

- **`StyledListItem`** (`StyledListItem.kt:696-880`): sfondo `surfaceContainerHigh`; **pressione di
  solo colore**: lo sfondo va a `surfaceContainerLow` con una animazione di colore di **500 ms** (`:737`),
  senza scala. `detectTapGestures` (pressione immediata, nessun ritardo), `ContextClick` al tap. Angoli
  di 28 dp sulla prima e sull'ultima riga, 0 nelle intermedie. Il segno di selezione sfuma in 300 ms.
- **`StyledList`** (`StyledList.kt:36-108`): colonna con sfondo `surfaceContainerHigh` e angolo di 28 dp
  (24 in modo Material), `animateContentSize` con la molla espressiva di Material; titolo e descrizione
  fuori dal gruppo. Nessun vetro.
- **`StyledToggle`** (`StyledToggle.kt:290-361`, modo Apple): riga con `StyledSwitch`; la pressione è un
  **colore piatto** `0xFFE0E0E0` (identico su scuro). Tap sulla riga: aptica `ToggleOn`/`ToggleOff`
  e anche quella dell'interruttore. Divisore fra le righe di `0x40888888`.
- **`StyledBottomSheet`** (`StyledBottomSheet.kt:29-86`): è il `ModalBottomSheet` di Material 3
  (fisica del trascinamento, mezza altezza e piena, tutta di Material) con un corpo di vetro Kyant:
  `vibrancy`, `blur(4 dp)`, `lens(12 dp, 48 dp, depthEffect = true)`, tinta scura `DarkGray` a 0,3
  oppure `0xFFE0E0E0` a 0,45; angolo che passa da 48 dp a 42 dp (`animateFloatAsState`, molla di
  default) quando si espande; **sipario trasparente**, 4 dp di margine dal bordo (foglio "galleggiante"),
  nessuna maniglia. **Va in una finestra a sé**: [verificare] se il vetro vede davvero la pagina.
- **`StyledConfirmationDialog`** (`StyledConfirmationDialog.kt:63-220`): non è una `Dialog` di sistema in
  modo Apple, ma una `Box` a tutto schermo **nella stessa composizione**, dunque il fondale Kyant lo
  vede. Entrata `scaleIn(1,05) + fadeIn`, uscita `scaleOut(1,05) + fadeOut` con le molle di default
  (smorzamento 1, rigidezza 400, ≈ 0,4 s): la finestra *scende* da 1,05 a 1 mentre sfuma. Sipario nero
  allo 0,4 che entra e esce con lei. Vetro: `vibrancy`, `blur(4 dp)`, `lens(12 dp, 48 dp, true)`, angolo
  48 dp, tinta `0xFF1F1F1F` a 0,35 (scuro) o `0xFFE0E0E0` a 0,7 (chiaro). I due `StyledButton` dentro
  campionano il fondale **esportato** dalla finestra, cioè il vetro sopra il vetro.
- **`StyledInputField`** (`StyledInputField.kt:19-168`): riga di 58 dp con sfondo `surfaceContainerHigh` e
  angolo di 28 dp; il segnaposto si alza di `32 sp` quando c'è testo (`updateTransition`); pulsante di
  cancellazione. Nessun vetro e nessuna aptica.

Cosa contribuisce al "vetro vero": **il foglio e il pop-up**, perché hanno una lente ampia (48 dp di
spostamento) su un corpo di colore uniforme e un bordo ben marcato. Il resto contribuisce per sottrazione:
essendo piatto, fa risaltare i pochi pezzi che non lo sono.

---

## 5. Perché LibrePods sembra più vetro del nostro

Le dieci cause del Sommario, con il meccanismo e il punto in cui da noi si cambia. In ordine d'impatto
probabile per quello che Alessio ha descritto (tasti che "esplodono" e "vibrano", barra ancorata, indietro
fluido).

**1. Il tasto tondo cresce di metà della sua misura.** Meccanismo: scala `1 + 0,5·p`, molla 0,5/300,
picco ×1,58, undershoot ×0,92; deriva `m·tanh(0,05 d/m)` senza tetto; allungamento lineare senza tetto.
Da noi: la crescita è in dp e ha un tetto (`ExpandShortSideDp = 6`, `ExpandLongSideDp = 14`,
`ExpandMaxScale = 1,14`, `FluidPress.kt:807-809`), e l'allungamento è limitato all'8%
(`StretchMax`, `:821`). Cosa cambia: un'opzione di crescita **proporzionale** per i tasti tondi di chrome
(`FluidGlassIconButton`, `FluidGlassControls.kt:278-320`, lente da 44 dp), e un allungamento che non si
appiattisce a 8%.

**2. L'aptica comincia al contatto e ha una coda.** Meccanismo: tre colpi per tocco (1.3), più una grana
sopra ~18,7 px per evento. Da noi: un solo `Tap`; il tocco non vibra, e nemmeno il rilascio senza click.
La finestra di fusione di 70 ms (`FluidHaptics.kt:225-231`) fonderebbe due dei tre colpi: serve una
regola per un "gesto in tre tempi". Costanti di sistema contro nostre composizioni: sul telefono
(Samsung in particolare) le prime sono tarate dal produttore e più forti.

**3. La barra è sopra la transizione.** Meccanismo: tasto indietro della pagina sotto in overlay,
controtraslato, con caso radice (2.4). Da noi: barra per pagina. Il `FluidChromeController` ha già un
registro per pagina e l'elezione di chi è davanti (`FluidScreen.kt:135-264`): è il posto naturale per
una barra **condivisa** sopra l'host delle pagine, invece di sollevare un tasto per pagina.

**4. La pressione parte al contatto e non prende il gesto.** Meccanismo: `onDragStart` al down, nessun
`consume()` (1.1). Da noi: `OwnDelayMillis = 100` (`FluidPress.kt:501`) e `LocalFluidPressImmediate`
(`:555`) solo nella chrome. Una pagina che scorre non può dare il tocco immediato senza dare una pressione
che parte e poi sparisce: una via di mezzo è una pressione **provvisoria** (crescita ridotta) in quei 100
ms che si scioglie se la pagina si prende il gesto.

**5. Il gesto indietro è cheap e preciso.** Meccanismo: due traslazioni, `draggable` ovunque a 1:1,
soglie 35% e 1000 px/s, uscita da 150 ms (2.5). Da noi: transizione della `NavHost` con scala, angolo e
sfocatura (`RouteMotion.kt:269-296`). Servirebbe un **host di pagine** nostro, indipendente dalla
libreria di navigazione, che offra anche il trascinamento ovunque.

**6. Il vetro della barra è Haze.** Meccanismo: luce da fuori angolo, ombra di bordo 20%, bordo morbido
1,25-1,5 dp, tinta al 40-80%, su un velo sfocato (1.2). Da noi: lente Kyant sul fondale `barGlass`
(`FluidScreen.kt:1173-1230`). Il carattere è diverso e non si ottiene da Kyant ritoccando due numeri:
**[verificare]** quanto del "vetro vero" venga da Haze o dalla crescita del tasto.

**7. Il bagliore è grande.** Meccanismo: raggio lato lungo, velo 0,10, punto 0,15 (1.1). Da noi: sul
chiaro 0,015/0,055 (`GlassInteraction.kt:364-376`), una scelta fatta per non far sbiancare il tasto:
giusta contro il difetto che c'era, ma che ha tolto la risposta di luce. Cosa cambia: una ricetta a
metà fra le due, da guardare a luce chiara.

**8. Le pagine sono carte con peso.** Meccanismo: angoli del telefono e ombra 36 dp (2.7). Da noi:
angolo 30 dp mentre non sono tutto schermo. Costo: ombra di elevazione su un layer a tutto schermo
durante la corsa; si può registrare l'ombra una volta.

**9. Sobrietà.** Meccanismo: righe e campi piatti, vetro solo su ciò che si tocca (4). Da noi:
`FluidCard(glass = true)`, gruppi e tessere in vetro. È una scelta di design, non di meccanica, e va
decisa a parte.

**10. Lente più forte sul tasto.** Meccanismo: spostamento 24 dp, profondità, dispersione. Da noi:
`refractionAmount = 14 dp` (+60% a pressione piena, `GlassMaterial.kt:584-607`). Conta solo dove il
fondale è pieno; su un fondale vuoto (la maggior parte dei casi in LP) non si vede.

**Cosa NON spiega la differenza.** La fisica del cursore e dell'interruttore (la nostra è più vicina a
Kyant, quella di LP è più approssimata); il refresh rate (di serie spento in LP); il numero di
sfocatura (LP usa 2 dp, noi ≈ 3,5 dp, la stessa classe).

---

## 6. Licenza

**Regola operativa (cambiata il 31 ottobre 2026, a studio finito).** LP è GPL-3.0, e Alessio ha scelto
di portarne il codice: l'engine e le app che lo aggiornano diventano GPL-3.0 (`LICENSES/LibrePods.md`).
Quindi il codice di LP **si porta com'è scritto**, a tre condizioni: l'intestazione di provenienza in
ogni file portato (i file e le righe di LP, il commit `3d4d270`, la licenza, l'elenco di quello che
abbiamo cambiato), le righe in `LICENSES/LibrePods.md` e la voce in `FluidEngineLicenses`, quindi in
`fluidLicensesSection()`. **Questa regola sostituisce quella che il documento aveva scritto**, e che
valeva fino alla 2.13: dal codice di LP *non si copia niente*, chi implementa lavora da questo
documento senza aprire LP, come per `fluidButtonGesture` della 2.12.0 («Ispirato al comportamento di
LibrePods (GPL-3.0): nessun codice»). Il documento resta com'era perché ne è nato, e per questo non
porta codice di LP, solo comportamento e numeri: sono fatti, non espressione, e si possono riscrivere
liberamente anche da chi non porta. Cosa è stato portato finora: i pulsanti (`FluidPress.kt`,
`FluidButtonGesture.kt` e le parti di aptica, bagliore e lente che li accompagnano, fase 4) e le pagine
(`ui/page/*`: i due strati, la carta, il flusso avanti e indietro e la regola di rilascio, fase 8).
Kyant e Haze sono Apache-2.0: si possono usare dalla fonte, con l'avviso e l'elenco delle modifiche
come per il resto di `LICENSES/AndroidLiquidGlass.md`.

`SwipeBack.kt` si dichiara "mostly AI generated" (`:32`) e, come i file di navigazione, non porta
l'intestazione GPL dei file di primitive; il repository è GPL-3.0 per intero e va trattato così.

Classi, pensate per la regola di prima — cosa una riscrittura pulita avrebbe potuto eguagliare —:
**(i)** derivato da Kyant, lo prendiamo da Kyant; **(ii)** originale di LP ma sostanzialmente
parametri e taratura, si rifà dalla specifica; **(iii)** originale di LP con logica non banale
(shader, algoritmo di gesto, architettura di navigazione) che una riscrittura pulita potrebbe non
eguagliare. Con la scelta di portare la domanda non si pone più per i pezzi portati (pulsanti, aptica,
gesto indietro e carta): resta una guida per quelli che non lo sono, come la barra ancorata, dove la
costruzione che vogliamo è comunque un'altra.

| Pezzo | Classe | Note |
|---|---|---|
| `inspectDragGestures` | **(i)** | identico a `DragGestureInspector.kt` di Kyant; da noi c'è già in `GlassInteraction.kt:86-138` |
| Deriva `tanh` e allungamento dei tasti | **(i)** | formule di `LiquidButton.kt`; le costanti di LP sono numeri (ii) |
| Shader del bagliore | **(i)** | AGSL di `InteractiveHighlight.kt`; da noi in `GlassInteraction.kt:459-477` con il parametro `core` in più |
| Ottica del tasto (`vibrancy`, `blur`, `lens`) | **(i)** | `LiquidButton.kt:58-62` più due flag |
| `MomentumAnimation`, pomello del cursore | **(i)** nella struttura, **(ii)** nei numeri | discende da `DampedDragAnimation` e `LiquidSlider`; le molle di LP sono numeri |
| Pomello dell'interruttore | **(ii)** | semplice e proprio; la veste è di `LiquidToggle` |
| Foglio e pop-up di vetro | **(i)+(ii)** | ricetta di Kyant (`DialogContent.kt`) con numeri ritoccati |
| Crescita 1,5 / 0,10, molle 0,5-300, ritorno critico | **(ii)** | numeri e forma di molla |
| Coreografia aptica (momenti e costanti) | **(ii)** | un elenco di eventi e di costanti pubbliche di Android; comportamento, non espressione |
| Tasto tondo su Haze (parametri dello stile) | **(ii)**, e Haze è **(i)-like** (Apache) | LP non scrive il vetro, lo compone; i parametri sono numeri. Usare Haze come libreria o come sorgente è lecito (Apache) |
| Righe, liste, campo di testo, foglio di Material | **(ii)** | tinta piatta e parametri |
| **Barra ancorata**: scena a due pagine, overlay condiviso, `LocalIsCurrentEntry`/`LocalTransitionProgress`, tabella dei quattro casi del tasto | **(iii)** | architettura originale, non da Kyant |
| **Gesto indietro** (`SwipeBack`): `draggable` ovunque + `PredictiveBackHandler`, soglie, parallasse | **(iii)** leggero | l'algoritmo è corto e le soglie sono numeri: la specifica 2.5 basta |
| Tasto "fantasma" verso la radice (scala, alpha, blur) | **(iii)** leggero | tre curve e tre numeri |

**Stima di sforzo e rischio per i (iii).** Le stime sono per una riscrittura pulita da questa
specifica, collaudata sull'S25 e sul Tab S9 con fotogrammi densi a 30 fps (regola già in uso: guardare i
primi cinque, mai "giusto per costruzione").

| Pezzo (iii) | Sforzo | Rischio | Perché |
|---|---|---|---|
| Barra ancorata | 4-6 giorni | medio-alto | `renderInSharedTransitionScopeOverlay` è sperimentale e la barra deve vivere sopra l'host di pagine. Meglio **non** copiare il trucco "un tasto per pagina nell'overlay" ma farne una **barra condivisa** sopra l'host (registro nel `FluidChromeController`), che dà lo stesso risultato con una costruzione diversa e meno delicata. Il rischio vero è di tempi e di collaudo, non di licenza |
| Host di pagine con gesto indietro | 3-5 giorni | medio | serve un contenitore a due strati indipendente dalla libreria di navigazione (le app usano `NavHost`, non Navigation3). Il progresso di sistema del back predittivo va verificato sul telefono ([verificare]); l'uscita va fatta con una molla che parte dalla velocità del dito, non con un `tween`, e così migliora su LP |
| Tasto "fantasma" | 0,5-1 giorno | basso | si ricava dalla tabella 2.4 |

Il rischio principale non è legale ma di **resa**: le curve di una barra ancorata si giudicano in mano. Il
documento dà i numeri di LP come riferimento di partenza, non come obiettivo da ricopiare: nei punti in
cui LP è visibilmente rozzo (avanti, tasto che sparisce di colpo, uscita senza velocità) conviene
migliorare, e ci sono più motivi per farlo da una specifica che da un codice.

---

## 7. Cosa portare nell'engine

Un ordine, dal più economico e più visibile al più strutturale. Ogni fase si chiude con un controllo sul
telefono (fotogrammi densi, la lista a vista) prima di dire che è fatta.

### 7.1 Pulsanti (2.14 o prima: sono parametri)

1. **Crescita proporzionale per i tasti tondi di chrome.** Un'opzione, per esempio
   `FluidPressFeedback.Pop` (scala massima parametrica, di partenza 1,3-1,5 sul tasto da 44 dp), usata
   da `FluidGlassIconButton` nella barra e non dai tasti con testo, che restano a ≈ 9%. Molla
   0,5 a rigidezza 300 per salita e discesa come punto di partenza da confrontare con la nostra
   `0,62 / 816`.
2. **Allungamento senza tetto stretto.** Una variante lineare con un tetto alto (per esempio 25%),
   al posto di `tanh` a 8%.
3. **Deriva con tetto asintotico alla misura del tasto**, guadagno 0,05, al posto di `r = min(8 dp, lato/6)`
   con guadagno 0,15. Si sposta di meno a piccoli trascinamenti e di più a quelli grandi.
4. **Ritorno della deriva con molla critica** (1; 300), separata da quella della scala.
5. **Bagliore a due ricette per il chiaro**: una via di mezzo (velo ≈ 0,04, punto ≈ 0,10, raggio
   1,3 lati corti) fra la nostra attuale (0,015/0,055) e quella di Kyant (0,08/0,15) che sbianca.
6. **Pressione provvisoria in attesa del gesto.** Nei 100 ms in cui la pagina decide, il tasto cresce
   di una frazione (per esempio 40% del percorso) e si scioglie se la pagina se lo prende. Non è di LP:
   è la via per avere la prontezza di LP senza il difetto di una pagina che non scorre.
7. **Lente dei tasti:** provare `lens(12, 24, depth, chroma)` con `refractionAmount` a 24 dp come
   alternativa al 14 dp attuale, da giudicare su un fondale pieno.

### 7.2 Aptica

1. **Un evento al contatto**, `PressDown`, sul momento in cui il tasto diventa del dito (o al down, in
   chrome): un tick secco, intensità iniziale ≈ 0,35-0,5.
2. **Una coda al rilascio**, `PressRelease`, anche senza click: un colpo breve e basso (`LOW_TICK` o
   una discesa breve), non un `Reject`.
3. **Il click resta** `Tap`. Con i tre colpi in 150 ms serve una regola nuova nella fusione di 70 ms
   (`fluidHapticCoalesces`): i tre eventi di un gesto in tre tempi non si fondono fra loro, mentre due
   tocchi di componenti diversi nello stesso istante sì.
4. **Una modalità "costanti di sistema"** (esiste già `FluidHapticEngine.Platform`): mappare i tre momenti
   a `SEGMENT_FREQUENT_TICK`, `REJECT`, `CONTEXT_CLICK` per ottenere la forza che il produttore ha tarato.
   Da offrire come preset e da provare su S25 e Tab S9 (Samsung mappa le costanti in modo suo).
5. **Grana in movimento:** la 3.0 in corso (velocità misurata, isteresi) è già meglio della soglia in
   pixel per evento di LP, perché non dipende dalla frequenza del tocco. Va solo scelta l'intensità per
   confrontarla col tick di sistema.
6. **Interruttore e cursore:** invariati. Si possono mappare a `TOGGLE_ON/OFF` e `SEGMENT_TICK` di sistema
   nello stesso preset.

### 7.3 Cursore e interruttore

Nessun cambiamento obbligato: i nostri sono più fedeli a Kyant. Opzioni, da giudicare in mano:

- **Cursore "rigido":** pomello senza ritardo del valore (la molla (1; 1000) di Kyant ritarda ≈ 150 ms
  il pomello), con la scala e la lente che restano animate. È il modo in cui LP segue il dito.
- **Molle di scala più elastiche per il cursore:** X 0,4 / Y 0,6, rigidezza 400.
- **Lente del cursore con `depthEffect` e dispersione:** `lens(6·p, 12·p, true, true)`.
- **Non portare:** la corsa a un terzo del dito dell'interruttore, il rimbalzo oltre la pista, la
  commutazione a metà trascinamento, l'impulso da 350 ms.

### 7.4 Barra ancorata

Progetto, da questa specifica e non dal codice:

1. **Una barra condivisa sopra l'host di pagine.** Ogni `FluidScreen` registra nel `FluidChromeController`
   la *descrizione* della sua barra (titolo, tasto indietro sì/no, azioni), non la disegna. L'host
   disegna una sola barra e la anima.
2. **Il tasto indietro non si muove con la pagina.** Presente → presente: fermo. Assente → presente:
   compare con scala 0,82 → 1, alpha 0,72 → 1, blur ≈ 8 px → 0 (la curva di LP al rovescio, che LP non
   fa in avanti). Presente → assente: la curva di LP: scala `1 − 0,18·smoothstep(p)`, alpha
   `1 − 0,28·smoothstep(p)`, blur `8·p` px; ma portandolo **fino a zero** invece di spegnerlo di colpo
   a 0,72.
3. **Titolo e azioni:** LP li lascia nella pagina. Portarli nella barra condivisa è un'opzione
   (cross-fade del titolo, scala da 0 delle azioni come `scaleIn` di LP); prima va deciso se vogliamo
   la morfosi del titolo grande (`FluidTitleMorphState`) o il semplice cross-fade.
4. **Il vetro della barra** campiona una sola pagina: la "davanti" (`frontMostBackdropKey`) già c'è.
5. **Costo:** non registrare una seconda sorgente a tutto schermo per la barra (vedi
   `costo-sorgenti-vetro`: Perfetto sull'S25).

### 7.5 Gesto indietro

Un host di pagine nostro (per esempio `FluidPageHost`), indipendente dal router: riceve la pila di chiavi,
la funzione che disegna una pagina e la richiesta di "indietro". Specifica:

1. **Due strati.** Pagina sopra e pagina sotto sempre composte, la sopra opaca.
2. **Due sorgenti.** `PredictiveBackHandler` (progresso `p` del sistema, posizione `p·W`) e un
   `draggable` orizzontale ovunque (1:1 dalla soglia, solo a destra). I figli che consumano per primi
   vincono; la barra e i suoi tasti non fanno partire il gesto.
3. **Parallasse** `−(W/3)·(1−p)` per la pagina sotto; niente scala, blur o scrim.
4. **Conferma** a `0,35·W` oppure velocità sopra ≈ 350 dp/s (LP: 1000 px/s). Sui tablet `0,35·W` è
   tanto: usare il tetto in dp (per esempio 120 dp) e la velocità.
5. **Uscita con una molla** che parte dalla velocità del dito, non con un `tween(150)` fisso: è il punto
   in cui si può essere migliori di LP.
6. **Pagina come carta:** angoli veri del telefono (`RoundedCorner`, API 31+) e ombra di elevazione ≈ 36 dp,
   da registrare in modo che non costi per fotogramma.
7. **Aptica:** LP non ne ha; un colpo `Threshold` al superamento della soglia di conferma è una
   possibilità nostra.

### 7.6 Transizioni

- **`FluidRouteMotion.Push`**, scorrimento da destra di 220 ms con parallasse di un terzo, per la
  navigazione gerarchica di impostazioni e dettagli. Resta `Expand` (crescita dal punto toccato) per la
  carta che si apre.
- **Togliere `ExitBlur` dal `Push`:** un blur a tutto schermo per fotogramma è il costo più alto della
  transizione di rotta (`RouteMotion.kt:131-142`, `:290-295`); il `Push` non ne ha bisogno.
- **Avanti con la barra:** il tasto indietro della pagina nuova entra (non compare di colpo a fine corsa
  come, dal codice, accade in LP).
- **Con le animazioni ridotte:** `Push` si riduce a una dissolvenza breve, come il resto.

---

## Appendice A: i numeri di LP a colpo d'occhio

| Cosa | Numero | Dove |
|---|---|---|
| Altezza e margine di `StyledButton` | 48 dp, 16 dp, 8 dp fra i figli | `StyledButton.kt:408-410` |
| `maxScale` | 0,10 (0,05 a larghezza piena) | `:95` |
| Scala del tasto tondo | `1 + 0,5·p` | `StyledIconButton.kt:160` |
| Misura del tasto tondo | `48 sp` | `:245` |
| Molla di pressione | smorzamento 0,5, rigidezza 300, soglia 0,001 | `StyledButton.kt:329` |
| Molla della deriva | 1, 300, `Offset.VisibilityThreshold` | `:330-331` |
| Deriva | `m·tanh(0,05·d/m)`, `m = lato corto` | `:237-243` |
| Allungamento | `0,1·|cosθ·dx/M|·min(w/h,1)`; `0,1·|sinθ·dy/M|·min(h/w,1)` | `:245-254` |
| Lente tasto | `blur 2 dp`, `lens(12 dp, 24 dp, depth, chroma)`, `vibrancy` | `:220-229` |
| Bagliore | velo 0,10·p; punto 0,15·p; raggio = lato lungo; plateau metà raggio | `:281-298` |
| Aptica movimento | distanza² > 350 px² | `:382` |
| Aptica | `SegmentFrequentTick` (down, movimento), `Reject` (up), `ContextClick` (click) | vedi 1.3 |
| Barra: padding laterale | 8 dp (+12 dp del tasto), top = status bar | `StyledScaffold.kt:292`, `:404` |
| Altezza del velo | `64 dp + status bar` | `:367` |
| Ombra e angoli della pagina | 36 dp, `RoundedCorner` del telefono | `:127`, `:229-241` |
| Parallasse | `−(W/3)·(1−p)` | `SwipeBack.kt:210` |
| Avanti | `tween(220)` | `:130` |
| Indietro (tutte le vie) | `tween(150)` | `:142`, `:167`, `:176`, `:238`, `:248` |
| Conferma | `offset > 0,35·W` o velocità > 1000 px/s | `:232` |
| Tasto fantasma | scala `1 − 0,18·s`, alpha `1 − 0,28·s`, blur `8·p` px, `s = smoothstep(p)` | `StyledScaffold.kt:321-336` |
| Cursore: molle | X (0,4; 400), Y (0,6; 400), pressione (1; 1000; 0,01), velocità (0,5; 250; 5) | `StyledSlider.kt:107-117` |
| Cursore: velocità | `v/5000`, tetto ±0,15; pesi 0,75 e 0,25 | `:665-673` |
| Interruttore | pista 64×28, pomello 39×24, scala 1,5, pressione (0,5; 300; 0,001), impulso 175+175 ms | `StyledSwitch.kt:115-118`, `:129`, `:153-154`, `:285` |
| Foglio, pop-up | `blur 4 dp`, `lens(12, 48, true)`, angolo 48 dp | `StyledBottomSheet.kt:69-73`, `StyledConfirmationDialog.kt:152-156` |

## Appendice B: cose da guardare sul dispositivo prima di fidarsene

1. **LP in mano, a fotogrammi densi.** Installare LP (modo Apple, "swipe anywhere" attivo) sull'S25 e sul
   Tab S9, registrare lo schermo e guardare i primi cinque fotogrammi di: tocco di un tasto in barra,
   trascinamento veloce, indietro da bordo, indietro da metà pagina, tasto indietro toccato. Misura reale
   di picco e di undershoot della scala.
2. **Il tasto in barra scala davvero a 1,5 in mano**, e il vetro di Haze si gonfia o rifrange corretto
   (1.2, ultimo punto).
3. **Una lista con un tasto dentro, trascinata in verticale a partire dal tasto:** conferma che la
   pagina scorre *e* il tasto cresce e segue (1.1, "con la pagina che scorre").
4. **Indietro da bordo:** quanto il progresso di sistema segue il dito (se la pagina e il dito stanno sullo
   stesso pixel) sull'S25 e sul Tab S9; vedere la differenza con il trascinamento da metà pagina.
5. **Avanti con tasto indietro:** deriva a sinistra e comparsa brusca (2.4, ultima riga).
6. **Aptica:** le tre vibrazioni di un tocco e l'ordine fra `Reject` e `ContextClick`; su Samsung come
   suonano le costanti di sistema contro le nostre composizioni; il peso di `Reject` a ogni rilascio.
7. **Foglio di vetro in finestra a sé:** se il fondale si vede.
8. **L'aggancio "appiccicoso" del cursore** (3.1), e se i pomelli degli interruttori oltre la pista
   sono voluti.
9. **Tablet:** `0,35·W` su 800 dp (280 dp di trascinamento) e se la velocità basta da sola.
10. **Costo:** `gfxinfo` e Perfetto di una corsa avanti e indietro in LP e da noi, a parità di
    dispositivo: conferma che la corsa di LP è davvero più economica (2.8).
