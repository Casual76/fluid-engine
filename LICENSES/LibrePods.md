# LibrePods

Dalla 3.0 `engine-ui` include, **come sorgente**, il comportamento dei pulsanti di LibrePods: come
crescono sotto il dito, come lo seguono, come vibrano. Il codice è portato, non riscritto da una
descrizione: per questo l'engine, e ogni app che lo distribuisce, è sotto GPL-3.0.

- Progetto: **LibrePods** — "AirPods liberated from Apple's ecosystem"
- Autori: **LibrePods contributors**, Copyright (C) 2025 LibrePods contributors
- Repository: https://github.com/librepods-org/librepods
- Ramo e commit da cui abbiamo portato: `android/rewrite`, `3d4d270` (28 ottobre 2026)
- Percorsi, relativi a `android/app/src/main/kotlin/me/kavishdevar/librepods/`:
  `presentation/components/primitives/StyledButton.kt`, `.../StyledIconButton.kt` e
  `utils/DragUtils.kt`
- Licenza: **GNU General Public License, versione 3** — testo: https://www.gnu.org/licenses/gpl-3.0.html.
  I file di LibrePods dicono "versione 3, o una qualunque versione successiva"
- Copyright (C) 2025 LibrePods contributors

## Cosa questo vuol dire per chi usa l'engine

**Un'app che include `engine-ui` dalla 3.0 in poi è un'opera derivata, e va distribuita sotto
GPL-3.0**: il codice sorgente dell'app a chi riceve l'APK, con la stessa licenza. Fino alla 2.13
non era così: il comportamento di LibrePods era stato guardato e riscritto, senza copiare codice (era
la regola, e stava scritta nel `README.md` e nel `CHANGELOG.md` della 2.12.0). Dalla 3.0 la regola è
un'altra, per decisione di Alessio: le app Pampa che usano l'engine sono GPL-3.0, e i tasti si
comportano **esattamente** come quelli di LibrePods perché portano lo stesso codice.

Il nome e il logo di LibrePods non ci appartengono: non li usiamo se non qui, per dire da dove viene il
codice, e senza dire che il progetto abbia a che fare con le nostre app.

## Cosa abbiamo preso

| Dove sta da noi | Cosa è | Da dove viene |
|---|---|---|
| `engine-ui/.../ui/fluid/FluidPress.kt` | le molle (0,5; 300 per la scala, 1; 300 per la deriva), la deriva elastica `m·tanh(0,05·d/m)`, l'allungamento nel verso del dito, la scala `1 + maxScale` (0,10 i tasti con testo, 0,5 i tondi), il dito come somma di spostamenti | `StyledButton.kt:230-255`, `:326-389`; `StyledIconButton.kt:134-137`, `:158-177`, `:194-244` |
| `engine-ui/.../ui/fluid/FluidButtonGesture.kt` | il ciclo del puntatore: pressione al contatto, nessun consumo, una sola fine per rilascio e annullo | `StyledButton.kt:326-389`; `StyledIconButton.kt:194-244`; `utils/DragUtils.kt` |
| `engine-ui/.../ui/haptics/FluidHaptics.kt`, `AndroidFluidHaptics.kt`, `FluidHapticGrain.kt` | i momenti e le costanti aptiche: `SegmentFrequentTick` al contatto, `Reject` al rilascio, `ContextClick` al click, un `SegmentFrequentTick` per ogni evento con distanza al quadrato > 350 px² | `StyledButton.kt:321`, `:336-338`, `:361-363`, `:382-384`; `StyledIconButton.kt:154`, `:198`, `:219`, `:235-239` |
| `engine-ui/.../ui/glass/interaction/GlassInteraction.kt` (`glassButtonGlowRecipe`) | i numeri del bagliore sotto il dito: velo 0,10, punto 0,15, raggio il lato lungo, plateau mezzo raggio. Lo shader è di Kyant (Apache-2.0) e non è cambiato | `StyledButton.kt:277-311` |
| `engine-ui/.../ui/fluid/GlassMaterial.kt` (`GlassDefaults.buttonOptics`) | la lente del pulsante: altezza 12 dp, spostamento 24 dp, profondità, dispersione cromatica | `StyledButton.kt:220-229` |

Ogni file porta in testa il commento di provenienza: il file e le righe di LibrePods, il commit, la
licenza e l'elenco di quello che abbiamo cambiato.

## Cosa abbiamo cambiato

- **I nomi, la lingua dei commenti e il formato.** Le formule e le costanti sono le loro.
- **La pressione è uno stato condiviso** (`FluidPressState`), letto insieme dal click, dal layer del
  vetro e dal bagliore: in LibrePods ogni pulsante ha tre `Animatable` dentro il composable. La scala
  massima non è un parametro del chiamante ma una proprietà del feedback (`FluidPressFeedback.maxScale`),
  con un tetto sulla crescita del lato lungo che fa il lavoro del `maxScale = 0,05` che LibrePods
  passa a mano ai suoi tasti a larghezza piena.
- **L'aptica è il vocabolario dell'engine** (`FluidHapticEvent.PressDown`, `PressRelease`, `PressClick`,
  solo di piattaforma) invece di `LocalHapticFeedback` chiamato dal pulsante, ed è dietro l'interruttore
  delle app e al risparmio energetico. I momenti e le costanti sono quelli di LibrePods. Un tasto che
  apre qualcosa di suo (un menu) mette il proprio evento al posto del click.
- **Il click lungo**, che LibrePods non ha e che i nostri menu usano: scatta se il dito è rimasto entro lo
  slop per il tempo del click lungo, e dopo non c'è click al rilascio.
- **L'accento, l'accessibilità, il movimento ridotto**: il tasto cresce la metà e non segue il dito;
  tastiera, fuoco e TalkBack restano a `clickable`.
- **Il vetro è il nostro** (Kyant, Apache-2.0): la lente di LibrePods vale sotto il dito, e a riposo il
  pulsante tiene la lente di sempre; `GlassRole.Interactive` non è cambiato.
- **La grana in movimento ha due stili**: quello di LibrePods (di serie) e quello che l'engine aveva
  scritto prima, a velocità misurata (`FluidHapticGrainStyle`), per poterli confrontare in mano.

## Cosa NON abbiamo preso

Il tasto tondo su Haze Glass, la barra ancorata, il gesto indietro, le righe, l'interruttore e il
cursore di LibrePods: `docs/riferimenti-3.0.md` li descrive e li confronta con i nostri, ma qui non
c'è codice loro.

## Cosa fare in pratica

Riportare **LibrePods — GPL-3.0 — Copyright (C) 2025 LibrePods contributors** fra le licenze di terze
parti nella schermata "informazioni" dell'app, e dire che l'app è distribuita sotto GPL-3.0. L'engine
fornisce già la voce: `fluidLicensesSection()` elenca da sola tutte quelle di `FluidEngineLicenses`, e
quindi anche questa.

## Kyant e LibrePods

LibrePods, a sua volta, ha preso da Kyant0 ([AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass),
Apache-2.0) l'algoritmo di `inspectDragGestures`, le formule di deriva e allungamento (`LiquidButton.kt`)
e lo shader del bagliore. Quelle parti le avevamo già, dalla fonte, e restano elencate in
`LICENSES/AndroidLiquidGlass.md`.
