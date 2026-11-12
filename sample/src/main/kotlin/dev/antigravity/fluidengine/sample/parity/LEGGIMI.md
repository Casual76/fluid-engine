# Parità F1B.6

La pagina mette **fianco a fianco, sullo stesso fondo**, la stessa forma resa dalle strade diverse del materiale:

| riga | colonne |
|---|---|
| tasto · 44 × 44 · `Interactive`, ottica dei tasti | `glassSurface` · la stessa forma come superficie di Fluid-physics ferma · vetro dipinto (`fluidStaticGlassSurface`) |
| capsula · 160 × 44 · `Floating` | come sopra |
| pannello · 200 × 120, angolo continuo 28 · `Modal`, ottica del pannello | come sopra |
| pillola e goccia | un tasto di `glassSurface` di riferimento · la pillola di una `FluidTabBar` a due schede · `FluidSwitch` acceso (pomello della goccia) · `FluidSlider` |

Tutte le superfici che prendono una tinta hanno la stessa, che lascia il velo al materiale (`GlassTintMode.Neutral`).
Il riquadro di Fluid-physics ha un nodo più grande della forma di 48 dp per lato: il suo layer è ritagliato sul nodo,
e l'ombra nel corpo si vede solo lì dentro (come fa la barra fra le pagine). Pillola e goccia hanno le loro taglie:
non si possono rendere a 44, 160 o 200 dp.

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

Gli extra valgono nella build di debug, come quelli delle altre pagine. Con il livello simulato
(`FluidGlassDiagnostics.simulateApiLevel`, `gallery.api 32`) la pagina mostra i ripieghi.

## Cosa scrive in logcat

Al lancio e a ogni cambio: `AVVIO <scena> · <tema> · respiro … · <vetro> | API effettiva N`. Dopo un secondo e mezzo,
una riga per riquadro con la strada che le regole del codice danno (`riquadro tasto/glassSurface: Liquid`,
`riquadro capsula/fisica: Liquid (Full)`, `riquadro pannello/dipinto: ricetta …`, `riquadro pillola: …`,
`riquadro goccia: …`), il censimento della diagnostica (`liveSurfaces`, che la pagina accende finché è in scena: conta
anche i tasti della pagina e le barre della galleria) e gli shader rifiutati. Se il censimento non torna con le righe,
la regola scritta nella pagina non è più quella dell'engine.

## Cosa guardare sul tablet

1. **Una riga, tre colonne**: `glassSurface` e Fluid-physics ferma devono essere lo stesso vetro (velo, filo, riflesso,
   ombra) nei due temi e sui tre fondi; il JVM dice che gli ingressi coincidono, il banco che i pixel stanno entro 2
   livelli dentro la sagoma. Sul tasto, dove il filo è un pixel e mezzo, guardare il contorno a 4×: Fluid-physics ha
   il ritaglio emulato nel corpo, `glassSurface` quello vero del nodo.
2. **Il dipinto** è un colore pieno: su piatto deve somigliare alle altre due colonne, su testo e foto no (è la
   differenza dichiarata). Filo e riflesso devono essere gli stessi.
3. **Respiro acceso**: il primo e l'ultimo fotogramma di ogni respiro non devono avere uno scalino di velo, filo o
   ombra. Sul tema scuro, fra tasto e pannello, il JVM ha trovato un passo di velo al primo fotogramma del viaggio
   (`GlassRecipeParityTest`, nota F1B.6): qui non c'è un viaggio fra piccolo e grande, ma vale la pena guardarlo nella
   pagina «Menu» col tema scuro.
4. **Pillola e goccia**: la pillola è il vetro della barra un po' più scuro; il pomello a riposo ha la luce del tasto
   di riferimento accanto (filo, riflesso, ombra).
5. **`parity.model classic`**: tutto deve tornare al vetro di prima, **tranne il dipinto**, che resta derivato dalla
   ricetta (disparità nota di F1B.6). Fotografare la colonna «vetro dipinto» con `Liquid` e con `Classic`.
6. Con `gallery.api 32` (livello simulato): come con `Classic`.
