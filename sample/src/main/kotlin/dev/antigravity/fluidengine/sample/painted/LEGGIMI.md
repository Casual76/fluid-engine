# Vetro dipinto (fuori dall'host)

La galleria ha un `FluidGlassModalHost` alla radice (`GalleryActivity.kt`). Con un host, `FluidSheet` e `FluidAlert`
non aprono una finestra di piattaforma: sono il foglio e l'avviso di vetro dell'host, col vetro vero e lo scrim dei
modali (nero al 12% sul chiaro, al 20% sullo scuro). Per questo, nelle altre pagine, il **vetro dipinto** non va mai a
schermo: né il `ModalBottomSheet` di `FluidSheet`, né la `Dialog` di `FluidAlert`, né il loro scrim.

Questa pagina li mostra:

| cosa | come si apre | che cosa si vede |
|---|---|---|
| **foglio di piattaforma** | «Foglio senza host», o `painted.open sheet` | `FluidSheet` dentro `CompositionLocalProvider(LocalFluidGlassModalHostState provides null)`: il `ModalBottomSheet`, col vetro dipinto, il suo scrim e un tasto dipinto annidato |
| **avviso di piattaforma** | «Avviso senza host», o `painted.open alert` | `FluidAlert(inHost = false)`: la `Dialog`, col vetro dipinto e il suo velo |
| **tasto e pannello dipinti** | sempre, sulla pagina | `fluidStaticGlassSurface` su una capsula 160 × 44 e un pannello 200 × 120 (angolo 28), sopra la pagina e senza scrim |

Il materiale è quello che la galleria chiede (`gallery.model`; nella build di debug c'è anche il tasto «Vetro: …»,
che cambia il vetro di tutta la galleria). Cosa deve succedere:

- **`Liquid`** (da Android 13, sorgenti accettate): il dipinto dalla ricetta (filo sul perimetro, riga chiara e alone,
  ombra larga, corpo pieno), lo scrim unico 12/20% sotto foglio e avviso.
- **`Classic`**, `gallery.api 32` o sorgenti rifiutate: il foglio e l'avviso della 3.x per intero (contenitore al 92%
  da cui si legge la pagina, scrim nero al 42% sotto il foglio, il velo di piattaforma della `Dialog` sotto l'avviso,
  nessuna ombra del vetro).

## Comandi (da chi possiede il tablet: qui non sono stati eseguiti)

```powershell
$A = "dev.antigravity.fluidglass.debug/dev.antigravity.fluidengine.sample.GalleryActivity"
adb shell am start -S -W -n $A --es gallery.page painted
adb shell am start -S -W -n $A --es gallery.page painted --es painted.open sheet
adb shell am start -S -W -n $A --es gallery.page painted --es painted.open sheet --es gallery.model classic
adb shell am start -S -W -n $A --es gallery.page painted --es painted.open alert --es gallery.model liquid
adb shell am start -S -W -n $A --es gallery.page painted --es painted.open alert --es gallery.model classic --es gallery.theme dark
adb logcat -s FluidDipinto
```

| extra | valori |
|---|---|
| `gallery.page` | `painted` |
| `painted.open` | `sheet` (o `foglio`), `alert` (o `avviso`); senza, la pagina e basta |
| `gallery.model` | `classic` o `liquid`, come in ogni pagina |

In logcat (`FluidDipinto`): `AVVIO vetro chiesto <modello> | API effettiva N`, e `FOGLIO aperto senza host …` o
`AVVISO aperto senza host …` quando si aprono. Dalla scheda Chrome: sezione **«Fuori dall'host (vetro dipinto)»**. La
pagina è anche nel giro `tools/qa/gallery-tour.sh` (pagina `Painted`).

## L'avviso della bozza

L'altro avviso fuori da un host è quello della bozza in `PagesDemoActivity` (Chrome → Pagine → «Apri una bozza» →
indietro): quell'attività non ha un host. Dalla correzione legge `gallery.model` con lo stesso codice della galleria,
che glielo passa quando la apre (`pagesDemoIntent`): lanciando la galleria con `--es gallery.model classic` anche
l'avviso della bozza è quello della 3.x. L'attività non è esportata, quindi non si lancia da `adb` direttamente: ci si
arriva dalla galleria.

## Cosa guardare sul tablet

1. Lo scrim sotto il foglio: con `Liquid` la pagina scende del 12% (chiaro) o del 20% (scuro); con `Classic` del 42%.
2. Lo scrim sotto l'avviso: con `Liquid` lo stesso 12/20%; con `Classic` il velo di piattaforma della `Dialog` (circa
   il 60% sul Tab S9).
3. Il tasto e il pannello sulla pagina: nessuno scrim sotto, il filo dei tasti di vetro accanto.
4. Il tasto dentro il foglio: si posa come un vetro annidato (meno velo e meno ombra) sopra il corpo del foglio.
