# Parità F1B.6

La pagina mette **fianco a fianco, sullo stesso fondo**, la stessa forma resa dalle strade diverse del materiale:

| riga | riquadri (codice · strada) |
|---|---|
| tasto · 44 × 44 · `Interactive`, ottica dei tasti | **T1** `glassSurface` · **T2** la stessa forma come superficie di Fluid-physics ferma · **T3** vetro dipinto (`fluidStaticGlassSurface`) |
| capsula · 160 × 44 · `Floating` | **C1** · **C2** · **C3**, come sopra |
| pannello · 200 × 120, angolo continuo 28 · `Modal`, ottica del pannello | **P1** · **P2** · **P3**, come sopra |
| pillola e goccia | **R** un tasto di `glassSurface` di riferimento · **L** la pillola di una `FluidTabBar` a due schede · **G1** `FluidSwitch` acceso (pomello della goccia) · **G2** `FluidSlider` |

Il numero dice la strada: 1 `glassSurface`, 2 Fluid-physics, 3 dipinto. Ogni codice è scritto sopra il suo riquadro.

Tutte le superfici che prendono una tinta hanno la stessa, che lascia il velo al materiale (`GlassTintMode.Neutral`).
Il riquadro di Fluid-physics ha un nodo più grande della forma di 48 dp per lato: il suo layer è ritagliato sul nodo,
e l'ombra nel corpo si vede solo lì dentro (come fa la barra fra le pagine). Pillola e goccia hanno le loro taglie:
non si possono rendere a 44, 160 o 200 dp.

**Impaginazione.** I riquadri di una riga vanno a capo quando la finestra è stretta (il tablet in verticale, un
telefono) e la pagina scorre in verticale: nessuna colonna è tagliata e nessuna riga scorre di lato, così un riquadro
sta nello stesso punto in ogni foto con la stessa finestra. In orizzontale sul tablet le tre colonne di una riga stanno
affiancate; in verticale il pannello può andare a capo.

**Cornice di riferimento.** Ogni riquadro ha una cornice segnata da quattro angoli sottili, grande quanto il vetro più
48 dp per lato (lo spazio dell'ombra di Fluid-physics). Il vetro è al centro della cornice in tutte le colonne: per
confrontare lo stesso punto si ritaglia sugli angoli, oppure si usano le righe `RIQUADRO` qui sotto. Gli angoli stanno
lontani dal vetro e non toccano né il filo né l'ombra.

Dal catalogo Chrome: **Parità**. I tasti in alto cambiano fondo (piatto, testo, foto: lo stesso palco della pagina
«Materiale»), tema, respiro (le superfici di Fluid-physics si stringono del 7% per lato e tornano, ogni ~2 s: fermo
↔ viaggio) e il vetro chiesto alla pagina (`LocalFluidGlassModel`: `Liquid` o `Classic`).

## Comandi (da chi possiede il tablet: qui non sono stati eseguiti)

```powershell
adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity --es gallery.page parity
adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity --es gallery.page parity --es parity.scene foto --es parity.theme scuro
adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity --es gallery.page parity --es parity.scene testo --es parity.breathe on
adb shell am start -S -W -n dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity --es gallery.page parity --es parity.model classic
adb logcat -s FluidParita
```

| extra | valori |
|---|---|
| `parity.scene` | `piatto` (di serie), `testo`, `foto` (anche `flat`, `text`, `photo`) |
| `parity.theme` | `chiaro` (di serie), `scuro` |
| `parity.breathe` | `on` per far respirare le superfici di Fluid-physics |
| `parity.model` | `classic` o `liquid`; di serie il vetro che chiede la galleria (`gallery.model`) |

Gli extra valgono nella build di debug, come quelli delle altre pagine. La pagina è anche nel giro della galleria
(`tools/qa/gallery-tour.sh`, pagina `Parity`), che la fotografa con gli extra di serie. Con il livello simulato
(`FluidGlassDiagnostics.simulateApiLevel`, `gallery.api 32`) la pagina mostra i ripieghi.

## Cosa scrive in logcat

Al lancio e a ogni cambio: `AVVIO <scena> · <tema> · respiro … · <vetro> | API effettiva N`. Dopo un secondo e mezzo,
una riga per riquadro con la strada che le regole del codice danno (`riquadro tasto/glassSurface: Liquid`,
`riquadro capsula/fisica: Liquid (Full)`, `riquadro pannello/dipinto: dipinto dalla ricetta (Liquid)` oppure
`dipinto della 3.x (Classic)`, `riquadro pillola: …`, `riquadro goccia: …`), il censimento della diagnostica
(`liveSurfaces`, che la pagina accende finché è in scena: conta anche i tasti della pagina e le barre della galleria) e
gli shader rifiutati. Se il censimento non torna con le righe, la regola scritta nella pagina non è più quella
dell'engine.

Subito dopo, una riga per vetro con la sua posizione in pixel della finestra:
`RIQUADRO T1 tasto/glassSurface x=… y=… w=… h=…`, e così per T2…P3, R, L, G1, G2. Per T2 è il riquadro della forma,
non quello del nodo più grande. Le posizioni valgono per la pagina com'è in quel momento: dopo averla fatta scorrere,
il tasto **Scrivi posizioni** le riscrive. Con le righe `RIQUADRO` si ritaglia la stessa zona del vetro (per esempio
un quadrato di 24 px sull'angolo in alto a sinistra, ingrandito 4 volte) su ogni fondo, tema e vetro, senza cercarla a
occhio.

## Cosa guardare sul tablet

1. **Una riga, tre colonne**: `glassSurface` e Fluid-physics ferma devono essere lo stesso vetro (velo, filo, riflesso,
   ombra) nei due temi e sui tre fondi; il JVM dice che gli ingressi coincidono, il banco che i pixel stanno entro 2
   livelli dentro la sagoma. Sul tasto, dove il filo è un pixel e mezzo, guardare il contorno a 4×: Fluid-physics ha
   il ritaglio emulato nel corpo, `glassSurface` quello vero del nodo.
2. **Il dipinto** è un colore pieno: su piatto deve somigliare alle altre due colonne, su testo e foto no (è la
   differenza dichiarata). Filo e riflesso devono essere gli stessi.
3. **Respiro acceso**: il primo e l'ultimo fotogramma di ogni respiro non devono avere uno scalino di velo, filo o
   ombra. Il passo di velo al primo fotogramma del viaggio fra tasto e pannello, trovato da `GlassRecipeParityTest` in
   F1B.6, è stato corretto e ora è provato; qui non c'è un viaggio fra piccolo e grande, e la conferma a vista si fa
   nella pagina «Menu» col tema scuro.
4. **Pillola e goccia**: la pillola è il vetro della barra un po' più scuro; il pomello a riposo ha l'ombra del tasto
   di riferimento accanto e nessun filo: filo e riflesso arrivano con la pressione, in proporzione, e a pressione piena
   sono quelli del tasto (`--es gallery.tune knobrim=on` rimette il filo a riposo, per confrontare).
5. **`parity.model classic`**: tutto deve tornare al vetro di prima, **anche il dipinto** (T3, C3, P3), che con
   `Classic` è il vetro dipinto della 3.x per intero: il dipinto chiede il modello come ogni altro vetro
   (`paintedGlassTakesLiquid`). Fotografare la colonna 3 con `Liquid` e con `Classic`: le due foto devono essere
   diverse, e quella `Classic` uguale alla 3.1.2.
6. Con `gallery.api 32` (livello simulato): come con `Classic`.
