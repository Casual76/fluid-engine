# tools/icons: il set FluidIcons

`FluidIcons` sono le icone dell'engine: Material Symbols, stile arrotondato, **peso 500**, al tratto, e
piene per le icone che ne hanno bisogno (tab bar, forma «accesa» di uno stato, comandi di riproduzione).
Le ha scelte Alessio il 2026-11-10 sulle prove di R6 (`docs/liquid-glass/ricerca/icone-e-misure.md`);
la specifica è la sezione 3.14 di `docs/liquid-glass/piano.md`.

Il codice delle icone **non si scrive a mano**: nasce da un elenco e da un generatore.

## Aggiungere un'icona

1. Cerca il nome su https://fonts.google.com/icons (stile *Rounded*): è quello minuscolo con `_`.
2. Aggiungi una riga a `fluid-icons.txt`: `nome`, poi `pieno` se serve anche la variante piena, poi
   `# perché` (finisce nel KDoc).
3. Lancia dalla radice dell'engine:

   ```
   python tools/icons/gen_fluid_icons.py
   ```

   Scarica la sorgente nuova (serve la rete solo per quella), rigenera il codice Kotlin, i tracciati del
   banco e la tabella di corrispondenza, e aggiorna la cache.
4. Se l'icona prende il posto di una Material che le app usano con un altro nome, o è la forma piena di
   una coppia, scrivilo in `equivalenze.txt` e rilancia.

Controllo (non scrive, non usa la rete, esce **1** se il codice non corrisponde all'elenco, se manca una
sorgente, o se nella cartella del set c'è un file in più):

```
python tools/icons/gen_fluid_icons.py --check
```

## I file

| file | cosa | chi lo scrive |
|---|---|---|
| `fluid-icons.txt` | **l'elenco**: un nome di Material Symbols per riga, `pieno`, il motivo | a mano |
| `equivalenze.txt` | Material → Material Symbols dove il nome non si traduce da solo, le forme piene, le icone Phosphor di Fluidify | a mano |
| `censimento-app.txt`, `censimento-app.md` | il censimento delle icone Material usate dalle app, dall'engine e dalla galleria (2026-11-10) | censimento, sola lettura |
| `gen_fluid_icons.py` | il generatore (solo libreria standard, Python 3.9+) | — |
| `tavola.py` | la tavola di confronto e il foglio del set, resi con Skia | — |
| `sorgenti/material-symbols-rounded-500.json` | la cache delle sorgenti: rigenerare e `--check` non chiedono la rete | generatore |
| `corrispondenza.md`, `corrispondenza.csv` | per ogni icona del censimento e di Fluidify, l'equivalente `FluidIcons` (o il nome da aggiungere) | generatore |
| `engine-ui/…/ui/fluid/icons/FluidIcons.kt`, `FluidIconsOutline.kt`, `FluidIconsFilled.kt` | il set | generatore |
| `tools/glassbench/icons/fluid-icons.json` | gli stessi tracciati per il banco di render | generatore |

## Da dove vengono le sorgenti

Repository ufficiale `google/material-design-icons`, commit **`10ecc14790612ca8fd164e3f4ffe9c16fc50d5b7`**
(«Update Symbols», 2026-10-23), fissato in `gen_fluid_icons.py` (`COMMIT`). Per ogni icona il generatore
legge il VectorDrawable Android

```
symbols/android/<nome>/materialsymbolsrounded/<nome>_wght500_24px.xml        (al tratto)
symbols/android/<nome>/materialsymbolsrounded/<nome>_wght500fill1_24px.xml   (pieno)
```

da `raw.githubusercontent.com`, e l'elenco dei nomi esistenti dall'API di GitHub (`git/trees` di
`symbols/android`). Dall'XML prende i tracciati, il riquadro e `android:autoMirrored`: le icone che si
girano nelle lingue da destra a sinistra (frecce, invio, annulla, rientri) lo dicono le sorgenti di Google,
non una lista nostra. Licenza: Apache-2.0, in `LICENSES/MaterialSymbols.md`.

La cache (`sorgenti/`, circa 220 KB di testo) tiene, per ogni icona dell'elenco, i tracciati della
sorgente senza i segmenti di lunghezza zero (la pulizia non perde nulla) e i nomi citati di cui si è
verificata l'esistenza. Sta nel repo perché pesa meno del codice che produce e rende `--check` possibile
ovunque, senza rete. **Cambiare commit**: aggiorna `COMMIT` e `COMMIT_DATE` nel generatore e la riga in
`LICENSES/MaterialSymbols.md`, lancia il generatore (rifà la cache da sé), rilancia `tavola.py` e guarda
la tavola prima di consegnare: Google ritocca le sagome fra una versione e l'altra.

## Cosa fa il generatore ai tracciati

Riquadro portato a 24 unità (come le icone Material di Compose: l'API `icon: ImageVector` dell'engine non
cambia), coordinate assolute a due decimali (0,005 unità su un'icona da 28 dp a 3,5 px/dp sono 0,02 px),
via i segmenti nulli e i sottotracciati ridotti a un punto. Ogni icona diventa una proprietà costruita al
primo uso e tenuta, come le icone Material: chi non la nomina non la paga, e R8 la toglie.

## Nomi e file del set

- `FluidIcons.<Nome>` è la forma al tratto, `FluidIcons.Filled.<Nome>` la piena (solo per le righe
  `pieno`). `<Nome>` è il nome di Material Symbols in forma Kotlin: `arrow_back_ios_new` →
  `ArrowBackIosNew`, `qr_code_scanner` → `QrCodeScanner`; un nome che comincia con una cifra prende `_`.
- Le icone sono estensioni dell'oggetto `FluidIcons`, nello stesso pacchetto
  (`dev.antigravity.fluidengine.ui.fluid.icons`): un solo import per nome porta sia la forma al tratto sia
  la piena (`import dev.antigravity.fluidengine.ui.fluid.icons.Home`).
- Tre file: `FluidIcons.kt` (l'oggetto), `FluidIconsOutline.kt` (al tratto), `FluidIconsFilled.kt` (pieni).
  Pochi file grandi invece di un file per icona: nel dex ogni file è una classe con i suoi metadati Kotlin,
  e R8 toglie comunque le singole proprietà che nessuno usa.

## Un'app con icone sue allo stesso peso

```
python engine/tools/icons/gen_fluid_icons.py --app icone-app.txt \
    --package com.esempio.ui.icons --out app/src/main/kotlin/com/esempio/ui/icons
```

Stesso formato dell'elenco. Escono `FluidIconsOutline.kt` e `FluidIconsFilled.kt` nel pacchetto dell'app,
come estensioni di `FluidIcons` (si scrivono `FluidIcons.Rocket` come quelle dell'engine); la cache va
accanto all'elenco (`icone-app.cache.json`, o `--cache`). Un nome che l'engine ha già è un errore: si usa
quello dell'engine. `--check` vale anche qui.

## La tavola

```
artifacts\liquid-glass\venv\Scripts\python -I tools\icons\tavola.py
```

Scrive `artifacts/liquid-glass/bench/f0-8/tavola-icone.png` (le venti icone più usate dalle app: Material
di oggi, `FluidIcons`, la barra di Apple di R6, alla stessa scala) e `foglio-set.png` (tutto il set con i
nomi; `<>` = si gira da destra a sinistra). Usa il font Material Icons Round scaricato per R6 in
`artifacts/liquid-glass/riferimenti/icone/fonti/`.
