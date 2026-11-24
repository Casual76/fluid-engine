# Prima volta, permessi e avvio

Quattro cose che servono a ogni app nei suoi primi secondi (F8.11, dalla 4.0): **il foglio di benvenuto e delle novità**
(`FluidWelcomeSheet`, «Benvenuto in…» e «Novità di…» delle app di Apple), **il permesso chiesto dal tasto che ne ha
bisogno** (`rememberFluidPermission`), **gli sfondi vivi** per le pagine speciali (`FluidLiveBackgroundCanvas`,
`FluidAmbient(live = …)`) e **il primo fotogramma pronto** (`Theme.Fluid` e `fluidFirstFrame()`). La parte visibile sta in
`engine-ui` (`dev.antigravity.fluidengine.ui.fluid`); le decisioni pure in `engine-foundation` (`FluidFirstUse.kt`), la
memoria in `engine-storage` (`EngineFirstUseStore`), le novità dal manifest in `engine-config`
(`EngineRemoteConfig.releaseNotes`). Catalogo delle firme: [../api/prima-volta.md](../api/prima-volta.md); le parti pure
in [../api/foundation.md](../api/foundation.md).

**Il modello mentale.** L'engine ricorda e decide, l'app scrive le frasi. Il benvenuto si mostra **una volta per versione**
e solo quando può (mai da un link, mai sopra un'azione in corso); un permesso si chiede **nel punto in cui serve**, con una
spiegazione **una volta sola**; uno sfondo vivo **si ferma da solo**; la prima pagina **è pronta subito**, e la schermata
d'avvio di Android non si tocca.

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| ricordare la prima volta (benvenuto visto, permessi chiesti) su disco | alla radice: `CompositionLocalProvider(LocalFluidFirstUseMemory provides remember { EngineFirstUseStore(context) })` |
| il benvenuto al primo avvio e le novità dopo un aggiornamento | `val welcome = rememberFluidWelcomeState(BuildConfig.VERSION_NAME, hasNews = …, suppressed = …)` e `FluidWelcomeSheet(welcome.isShowing, welcome::dismiss, pagine)` |
| le novità scritte nel manifest dello store | `remoteConfig.releaseNotes(versionName)` (`engine-config`), poi `notes.map { it.toWelcomeFeature() }` |
| non mostrarlo a chi arriva da un link o da una notifica | `suppressed = initialAddress != null || FluidLaunchOrigin.isDirect(intent)`; sulle notifiche dell'app `putExtra(FluidLaunchOrigin.ExtraFromNotification, true)` |
| più passi coi pallini | `FluidWelcomeSheet(…, pages = listOf(FluidWelcomePage(…), …))` (o `FluidWelcomeContent` in una pagina intera) |
| un passo del benvenuto che chiede un permesso | `FluidWelcomePage(…, content = { … un tasto con rememberFluidPermission … })` |
| un tasto che ha bisogno di un permesso | `val mic = rememberFluidPermission(FluidPermission.Microphone, reason = "…")`, `FluidButton(…, onClick = { mic.request { … } }, modifier = Modifier.fluidPermissionSource(mic))` |
| le foto «solo alcune» di Android 14 | `FluidPermission.Photos`: lo stato `Partial` fa partire l'azione; `requestMore()` riapre la scelta |
| uno sfondo animato dietro un onboarding, un traguardo, l'assistente vuoto | `FluidScreen(ambient = FluidAmbient(tono, motivo, live = FluidLiveBackground.Aurora))`, o `FluidLiveBackgroundCanvas` in un `Box` |
| la finestra del colore giusto prima del primo disegno | `android:theme="@style/Theme.Fluid"` (o un tema figlio) e `fluidFirstFrame()` in `onCreate`, prima di `setContent` |
| lo stesso, lasciando la schermata d'avvio di Android com'è di sistema | `android:theme="@style/Theme.Fluid.SystemSplash"` e `fluidFirstFrame()` |
| la struttura della pagina subito e il contenuto quando arriva | `FluidScreen` + `FluidContentState(stato, loading = { FluidSkeletonList() })` ([contenuto-righe-liste.md](contenuto-righe-liste.md)) |
| **non così**: una schermata d'avvio dell'engine che trattiene l'icona, un'uscita animata dall'avvio | la schermata d'avvio resta di Android (nota di Alessio, 24 novembre 2026) |
| **non così**: uno sfondo vivo in una pagina di tutti i giorni | solo pagine speciali: distrae e consuma |

## Il benvenuto e le novità: `FluidWelcomeSheet`, `rememberFluidWelcomeState`

- **Il foglio.** Un `FluidGlassSheet` al detent `Large` (opaco e attaccato ai bordi, come il foglio di pagina di iOS),
  senza maniglia: il segno del passo sopra il titolo (`FluidWelcomePage.icon`, nell'accento), il titolo grande centrato,
  le righe (`FluidWelcomeFeature`: icona nell'accento, titolo, frase) o una frase sola (`message`, per un passo come «Turn
  On Notifications»), la nota sulla privacy col segno delle persone (e il collegamento, se l'app ne dà uno) e «Continua»
  fermo in basso. Le misure (`FluidWelcomeDefaults`) vengono dallo schermo di benvenuto di **Password di iOS 18**, misurato
  in punti: icona da 56 dp e 33 sopra il titolo, 40 fra titolo e righe, 25 fra le righe, la colonna delle icone centrata a
  60 dp dal bordo e il testo da 98; quello che lo schermo non mostra viene da WhatsNewKit (65 dp in cima).
- **Le righe entrano una dopo l'altra**: salgono di 14 dp e si mettono a fuoco (σ 6 dp), dalla prima a 250 ms con un passo
  di 90 ms. Le muove l'app, quindi si sfocano; col movimento ridotto sono già ferme. Solo nel primo passo e alla prima
  composizione.
- **Col testo grande** il corpo scorre e il tasto resta fermo in basso; da 1,3× la nota sulla privacy passa nel corpo.
  Una sfumatura del colore del fondo dice che il corpo continua sotto il tasto.
- **I passi** scorrono col dito (nitidi: è il dito che li muove), i pallini (`FluidPagerDots`, «Passo 2 di 3» per
  TalkBack) stanno sopra il tasto, il tasto porta al passo dopo e l'ultimo chiama `onFinish`.
- **Quando si mostra.** `rememberFluidWelcomeState` decide con `fluidWelcomeKind` (in `engine-foundation`): il benvenuto
  a un'installazione nuova (prima installazione e ultimo aggiornamento alla stessa ora, dal `PackageManager`, se l'app
  non dice `freshInstall`), le novità a un aggiornamento che ne ha (`hasNews`; null = non si sa ancora, la decisione
  aspetta), niente alla stessa versione. Un aggiornamento senza novità si segna come visto in silenzio. Il foglio compare
  qualche fotogramma dopo la pagina (la memoria si legge dopo il primo disegno: il primo fotogramma resta la pagina),
  **mai** con `suppressed` (link, notifica, condivisione), **mai** con `busy` o con un altro modale aperto nell'host, e la
  versione si segna come vista **quando compare**. `forget()` lo fa rivedere (per le prove, o per una voce «Novità»).
- **Le novità dal manifest.** `AppRelease.highlights` (`"highlights": [{"title", "text", "symbol"}]`, campo nuovo e
  facoltativo) o, senza, il `changelog` diviso in righe («Titolo: frase» con un titolo corto diventa titolo e frase).
  `EngineRemoteConfig.releaseNotes(versionName)` legge la copia in cache e non va in rete. `fluidReleaseNoteIcon` traduce
  il simbolo (`star`, `bolt`, `lock`, `photo`, `bell`, `search`, `sync`…) in un'icona di `FluidIcons`.

## Il permesso nel punto giusto: `rememberFluidPermission`

- **Dal tasto.** Il primo tocco fa nascere dal tasto una spiegazione breve (l'avviso con la sorgente, `FluidAlert(source =
  …)`: prende il posto del tasto e ci rientra) col titolo di serie («Consentire l'accesso al microfono?») e il **perché**
  dell'app (`reason`, una frase che dice cosa fa con il permesso). «Continua» apre il dialogo di Android, «Non ora» chiude.
  La spiegazione si mostra **una volta**: poi il tocco apre direttamente il dialogo, anche dopo un rifiuto. `explain =
  false` la salta del tutto.
- **La macchina a stati** (`FluidPermissionStatus`): `NeverAsked`, `Granted`, `Partial` (le foto «solo alcune»),
  `Denied` (si chiede ancora), `DeniedForever` (Android non chiede più: il tocco mostra la spiegazione con «Apri
  Impostazioni» e porta alla pagina dell'app, o a quella delle notifiche). Al ritorno l'engine **rilegge da solo** lo
  stato (alla ripresa dell'Activity); l'azione non riparte da sola, si tocca di nuovo.
- **Perché serve la memoria.** Android non distingue «mai chiesto» da «negato per sempre» (in tutti e due i casi niente
  permesso e niente spiegazione): l'engine scrive «chiesto» in `LocalFluidFirstUseMemory` dopo il primo dialogo vero. Un
  primo dialogo chiuso senza scegliere (indietro, un tocco fuori) non conta come un no. Senza `EngineFirstUseStore` alla
  radice la memoria dura quanto il processo.
- **I preset** (`FluidPermission`): `Microphone`, `Camera`, `Notifications` (da Android 13; prima porta alle impostazioni
  delle notifiche se sono spente), `Photos` e `PhotosAndVideos` (`READ_MEDIA_*`, `READ_MEDIA_VISUAL_USER_SELECTED` da
  Android 14, `READ_EXTERNAL_STORAGE` prima), `ApproximateLocation`; `FluidPermission.of(id, nome, permessi…)` per il resto.
  **L'app dichiara i permessi nel manifest**: senza, Android risponde «no» senza mostrare niente.
- **Le parole** stanno in `FluidFirstUseLabels` (`LocalFluidFirstUseLabels`, italiano di serie, `english()`).

## Gli sfondi vivi: `FluidLiveBackgroundCanvas`, `FluidAmbient(live = …)`

- **Tre sfondi** (`FluidLiveBackground`): `Aurora` (tre aloni larghi coi colori del tono che vagano piano in alto; sotto,
  la pagina pulita dove sta il testo), `Waves` (tre onde morbide in basso), `Stars` (puntini radi che respirano; sul chiaro granelli
  dell'accento). Lentissimi: il velo o l'onda più svelti fanno un giro in 40–45 s.
- **Dove.** Solo pagine speciali: un benvenuto, un traguardo, la pagina vuota dell'assistente (la nota di Alessio). In una
  `FluidScreen` con `ambient = FluidAmbient(…, live = …)`: il fondale resta opaco, la barra e i gruppi di vetro della
  pagina lo rifrangono. Altrove `FluidLiveBackgroundCanvas` in un `Box` (sotto un `glassBackdropSource`, se sopra c'è
  vetro).
- **Si ferma da solo** su una posa ferma e leggibile: col movimento ridotto, col risparmio energetico, con la batteria
  scarica (il segnale di Android) e quando la pagina non è in primo piano (ciclo di vita sotto RESUMED: una pagina coperta
  di `FluidNavHost`, l'app in sottofondo). Riparte da dov'era.
- **Il costo.** Il Canvas della piattaforma (gradienti e tracciati costruiti una volta per misura; niente AGSL, quindi
  niente compilatore né verdetti della sonda), nessuna allocazione e nessuna ricomposizione per fotogramma (il tempo si
  legge nel disegno), e il tempo avanza a **30 Hz** (`FluidLiveBackgroundDefaults.FrameRateHz`),
  non a ogni vsync: sotto il vetro è una sorgente che cambia, e così il vetro ricampiona un quarto delle volte che a 120 Hz.
  Le misure sono nel rapporto di F8.11.

## Il primo fotogramma pronto: `Theme.Fluid`, `fluidFirstFrame()`

- **Non si tocca l'avvio.** Niente schermata d'avvio dell'engine, niente uscita animata: quella di Android resta com'è (e
  prende il fondo dalla finestra, come per ogni app).
- **`Theme.Fluid`** (risorsa di `engine-ui`, chiaro in `values`, scuro in `values-night`): la finestra ha già il colore
  della pagina Fluid (#F2F2F7 e #0D0D0F) e le icone delle barre dalla parte giusta. Un'app lo mette nel manifest
  dell'Activity o ne fa un tema figlio.
- **Lasciare la schermata d'avvio com'era: `Theme.Fluid.SystemSplash`.** È lo stesso tema senza il fondo della finestra:
  Android disegna la sua schermata d'avvio col tema Material di piattaforma, come se l'engine non ci fosse, e il primo
  fotogramma dell'app ha comunque il colore della pagina (lo mette `fluidFirstFrame()` in `onCreate`, dopo l'avvio). Si
  passa dall'uno all'altro cambiando il parent del tema dell'Activity, una riga.
- **`fluidFirstFrame()`**, in `onCreate` prima di `setContent`: rilegge da un file piccolo e sincrono il fondo e il lato che
  `FluidTheme` ha usato l'ultima volta col sistema dalla stessa parte (un'app scura su un telefono chiaro, AMOLED, un
  accento) e la finestra li prende subito; al primo avvio in assoluto prende il fondo di serie della pagina dal lato del
  sistema. Da lì `FluidTheme` tiene il fondo della finestra uguale alla pagina (anche
  quando l'utente cambia tema) e aggiorna il file. Un'Activity che non la chiama non cambia niente.
- **La struttura subito.** `FluidScreen` disegna barra e titolo al primo fotogramma; i dati che arrivano dopo passano da
  `FluidContentState` (scheletro, poi il contenuto che entra a fuoco). La misura dell'avvio (tempo al primo fotogramma,
  `reportFullyDrawn`) sta in F7.

## Trappole

- `FluidWelcomeSheet` e la spiegazione del permesso vivono nell'host dei modali (`FluidGlassModalHost` alla radice): senza
  host il foglio non si apre e la spiegazione è il `Dialog` di piattaforma.
- Le pagine del benvenuto devono restare le stesse mentre il foglio esce (la regola delle lambda dei modali,
  [menu-modali.md](menu-modali.md)).
- Con gli indirizzi delle pagine (F8.12) l'intent di lancio è già consumato da `takeInitialFluidAddress()`: si passa
  `suppressed = initialAddress != null`.
- `Modifier.fluidPermissionSource` va **in testa** ai modificatori del tasto (prima di fondo e contenuto): mentre la
  spiegazione è in scena il tasto non si disegna.
- Le prove con uno sfondo vivo usano `mainClock.autoAdvance = false`: il tempo non finisce mai.

## Dove guardare

`engine-ui/.../ui/fluid/FluidWelcome.kt`, `FluidPermission.kt`, `FluidLiveBackground.kt`, `FluidFirstFrame.kt`,
`FluidFirstUseLabels.kt`, `engine-ui/src/main/res/values*/fluid_first_frame.xml`; `engine-foundation/.../FluidFirstUse.kt`;
`engine-storage/.../EngineFirstUseStore.kt`; `engine-config/.../EngineRemoteConfig.kt` (`releaseNotes`). Le scene nella
galleria: «Elementi nuovi › F8.11» (`novita/F8p11Scenes.kt`).
