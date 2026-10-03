# 09 · Wear OS

`engine-wear` è il design system Fluid su un orologio. Non è un secondo design system: il vetro, le
curve di movimento, le forme, i due tagli di Inter e l'aptica sono quelli di `engine-ui`, presi così
come sono. Quello che il modulo aggiunge è la metà che un telefono non ha mai avuto bisogno di avere:
un tema Material 3 per Wear ricavato dagli stessi token, e i componenti di uno schermo rotondo.

## Installarlo

È l'unico modulo **opt-in**: `engine-install.ps1` senza `-Modules` non lo include, perché porta con sé
Material 3 per Wear, e un'app per telefono pagherebbe dipendenze e tempo di build per uno schermo che
non ha.

```powershell
powershell -ExecutionPolicy Bypass -File engine\tools\engine-install.ps1 -AppRoot . -Modules engine-foundation,engine-ui,engine-net,engine-update,engine-wear
```

```kotlin
// nel build.gradle.kts del modulo dell'orologio
implementation(project(":engine-wear"))
```

Richiede Wear Compose Material 3 1.6 (la 1.7 vuole AGP 9.1, che l'engine non usa ancora).

## Il tema

```kotlin
FluidWearTheme(brand = MioBrand) {
  // schermate dell'orologio
}
```

Due temi annidati, di proposito. Fuori c'è `FluidTheme`, quello del telefono: il vetro legge da lì le
tinte e il lato (scuro o chiaro), `fluidPressable` il colore del fuoco, l'aptica il suo interruttore.
Dentro c'è il `MaterialTheme` di Wear, che leggono `TimeText`, `ScreenScaffold`, le liste e i picker,
con lo schema colori ricavato da quello del telefono (`fluidWearColorScheme`): una sola fonte di
colore, vista in due modi. Le schermate dell'orologio leggono
`androidx.wear.compose.material3.MaterialTheme`.

Le impostazioni di partenza sono `FluidWearDefaults.settings`: AMOLED e il colore del brand, sempre.
Un orologio è vetro nero in una ghiera nera, e ogni pixel più chiaro del nero è un pixel acceso che la
batteria paga.

## I componenti

| | |
|---|---|
| `FluidWearGlass` | **il** vetro dell'orologio (dalla 2.11.0): un velo fumé con un sesto dell'accento, stesso filo e stessa lente per tutto. `tint()` sopra una copertina, `pageTint()` su una pagina nera, `noticeTint()` per un avviso che deve leggersi ovunque. Su un orologio i pannelli stanno a un centimetro l'uno dall'altro sulla stessa copertina: ruoli diversi (Floating, Interactive) lì si leggono come due materiali |
| `FluidGlassDisc` | il controllo rotondo di vetro (play, avanti, indietro): rifrange, si piega verso il dito, si gonfia, si accende dove lo tocchi. Dalla 2.11.0 niente esplosione di default (`burst` facoltativo) |
| `FluidGlassBurst` | un anello di luce che parte dal punto toccato e corre al bordo, con otto scintille. Luce, non vernice: additiva sul lato scuro, sottrattiva sul chiaro. Disegnata **sopra** il vetro, non ricattura niente. Da usare con parsimonia: una fila di dischi che esplodono sembra un difetto |
| `FluidGlassCapsule` | vetro flottante che porta testo (il titolo di un brano su una copertina). Non si piega: il testo che si muove sotto l'occhio non si legge |
| `FluidGlassTimePill`, `FluidGlassBadge` | l'ora in una capsula di vetro sopra una copertina (il `TimeText` curvo resta per le liste); un disco di vetro non premibile, per un segno che compare e svanisce |
| `FluidEdgeProgressRing`, `FluidEdgeGlowRing` | l'avanzamento lungo il bordo dello schermo. Si ridisegna solo quando la fine dell'arco si sposterebbe di mezzo pixel: un brano di tre minuti è un ridisegno ogni ~60 ms, uno in pausa nessuno. `clearTop` lascia il varco per l'orologio, `glow` un alone che cresce verso l'interno, `head` una testa luminosa; `FluidEdgeGlowRing` mette tutto sul bordo vero |
| `FluidEdgeLevelArc` | un livello (il volume) con lo stesso tratto e alone, su un tratto di bordo |
| `FluidArcRow` | una fila di azioni che segue la ghiera invece di tagliare il cerchio con una retta; con `edgeClearance` ogni figlio sta a quella distanza dal bordo su qualunque schermo |
| `FluidWearToast` | un avviso breve al centro (quello che non è andato), annunciato a TalkBack |
| `FluidWearAccent` | presta a un pezzo di schermo un accento suo (il colore della copertina), alzato finché si legge sul nero |
| `Modifier.fluidRotarySteps` | ghiera e corona in passi interi. La ghiera del Galaxy Watch scatta (un evento, un passo), la corona scorre (si accumula la distanza) |
| `FluidWearListRow`, `FluidWearPill` | la riga di una lista Wear (copertina, titolo, sottotitolo; `selected` per quella corrente) e la pillola di vetro di una home |
| `FluidWearDimens` | le misure di uno schermo rotondo: nessun dp a mano in una schermata |

## L'always-on

`rememberFluidAmbientState(activity)` registra l'osservatore dell'ambient (che è anche il modo in cui
un'app dice a Wear OS che lo supporta) e va messo in `LocalFluidWearAmbient`. In ambient lo schermo
si aggiorna circa una volta al minuto e il touch è spento: niente vetro vivo, niente animazioni, solo
quello che vale la pena guardare. `Modifier.fluidBurnInShift` sposta di pochi pixel a ogni
aggiornamento, e solo dove il pannello dichiara di averne bisogno.

## Il vetro che sta fermo

Su un orologio il vetro deve costare zero quando niente si muove, e non deve mai sembrare vecchio
quando qualcosa si muove. Le due cose stanno insieme perché una lastra ricattura lo sfondo solo in
due casi:

1. lo sfondo si è ridisegnato (una copertina nuova, una dissolvenza in corso);
2. la lastra si è spostata **rispetto** allo sfondo (il dito che la piega, un pannello che scorre
   sopra una pagina ferma).

Dalla 2.10.0 il secondo caso guarda solo la posizione relativa. Prima contava anche quella assoluta,
e uno swipe di pagina, in cui la pagina e le sue lastre si muovono insieme, faceva ricalcolare ogni
lastra a ogni fotogramma per un'immagine identica.

Le regole che ne seguono:

- **quello che cambia spesso sta fuori dalla sorgente**: l'anello, `TimeText`, un titolo che scorre.
  Dentro, ogni loro fotogramma ricalcolerebbe ogni lastra;
- **gli effetti stanno sopra il vetro**: `GlassTouchHighlight` e `FluidGlassBurst` ridisegnano un
  livello piccolo per la durata dell'animazione e non chiedono a nessuna lastra di ricatturare;
- un controllo premuto è vivo esattamente per la durata della sua animazione
  (`GlassTouchHighlight.isAnimating`), e solo lui.

Per verificarlo su un dispositivo vero: `FluidGlassDiagnostics.enabled = true` in una build di debug,
poi si lascia lo schermo fermo e `FluidGlassDiagnostics.totalCaptures` non deve muoversi.
