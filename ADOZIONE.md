# Portare l'engine su tutte le app

Ordine deciso da Alessio il 2026-08-23: **ClasseViva Expressive → Pampa Store → KeyVoice →
universal_converter**, con Pampa widgets in coda (vedi in fondo).

## La distinzione che conta

Un'app può stare sull'engine a tre profondità diverse, e confonderle è il modo più rapido per
promettere qualcosa che non arriva:

| profondità | cosa cambia | si vede? |
|---|---|---|
| **tema** — `FluidTheme` | colori, tipografia, angoli, curve di animazione | poco: l'app resta strutturata com'era |
| **componenti** — `FluidScreen`, `FluidListGroup`, `GlassMaterial`, la fisica dello scroll | com'è fatta ogni schermata | **sì**: è qui che arrivano il vetro e l'overscroll |
| **moduli senza UI** — `engine-update`, `engine-config` | aggiornamento in-app, feature flag | no, per definizione |

Questo documento è il piano per arrivare ai **componenti**, e lo stato di ogni app.

| app | tema | componenti | note |
|---|---|---|---|
| ClasseViva Expressive | ✅ | ✅ | li aveva gia': la migrazione ha tolto la copia locale |
| universal_converter | ✅ | ✅ | `FluidScreen` su principale, impostazioni e log |
| Pampa widgets | ✅ | ✅ | `FluidScreen` + `FluidTabBar` in vetro |
| Pampa Store | ✅ | ✅ | Android Compose nativo: `FluidScreen`, tab bar/rail, componenti e vetro condivisi |
| KeyVoice | — | — | e' tutta View: prendere i componenti significa riscriverla in Compose |

---

## Fase 1 — ClasseViva Expressive ✅ fatta (2026-08-23, engine 1.2.0)

**8499 righe cancellate, 269 rimaste.** La copia locale del design system non esiste piu':
`core-designsystem` dipende da `:engine-ui` e tiene solo quello che e' di ClasseViva.

Cosa e' rimasto nell'app, e perche':

| file | righe | perche' non e' dell'engine |
|---|---|---|
| `AppTheme.kt` | 109 | il verde del marchio, il guscio su `FluidTheme`, e la mappa da `AppSettings` a `EngineSettings` |
| `FeatureHero.kt` | 71 | `FeatureIdentity`: sa cosa sono voti, agenda e assenze |
| `GradePill.kt` | 35 | sa cos'e' la sufficienza |
| `SyncStatusBridge.kt` | 54 | "anno non iniziato" e' un booleano nel dominio e una frase nell'engine |

**Deviazione dal piano, deliberata:** il piano diceva di cancellare `core-designsystem`. Il modulo
resta, ma come **strato dell'app** invece che come copia del design system: dichiara
`api project(':engine-ui')`, quindi i sette moduli feature continuano a dipendere da lui e nessun
loro `build.gradle` e' stato toccato. La copia e' sparita, il modulo no.

**Cosa e' andato nell'engine:** `FluidHero`. Era rimasta fuori dall'estrazione perche' sembrava
specifica, e invece il dominio stava solo nei nomi dell'enum. Ora l'engine ha `FluidHeroTone` (una
posizione sull'anello delle tre famiglie di accento) e `FluidHeroMotif` (la forma sul fondo), e
l'app mappa le sue sette sezioni su quella coppia.

**Cosa e' stato cancellato senza portarlo:** `Animations.kt`, cioe' `bouncyClickable`. Era un guscio
di compatibilita' su `fluidPressable` con **zero chiamanti**.

**Verificato:** build debug e release firmata, test verdi, installata sul dispositivo, home / voti /
altro guardate una per una. Si vede identica a prima — che era il risultato atteso e va detto cosi'.

### Le tre cose che hanno richiesto attenzione

1. **I nomi dei preset dell'accento.** Le impostazioni salvano la scelta per nome e la confrontano
   con `preset.name`. L'engine chiama "fluid" quello che ClasseViva chiamava "expressive": prendere
   la lista dall'engine avrebbe fatto apparire *nessun* preset come selezionato a chiunque avesse
   gia' scelto il blu. La lista resta nell'app, con i nomi storici e gli stessi colori.
2. **Il tipo dello stato di sincronizzazione.** L'app ne ha uno nel dominio, l'engine uno suo. Non
   vanno unificati: nel dominio `schoolYearNotStarted` e' un booleano perche' decide un
   comportamento, nell'engine e' una frase perche' li' serve solo da dire. Si converte al confine.
3. **Un nome pienamente qualificato** nel codice (`dev.antigravity...theme.QuickAction(`) che la
   riscrittura degli import non vede. Cercarli prima: `grep -rn "core.designsystem\." --include=*.kt`.

---

## Fase 2 — Pampa Store ✅ fatta (2026-08-24, Android + engine 1.2.x)

Il vecchio client Compose Multiplatform e' stato rimosso. Pampa Store e' ora un'app Android Compose
nativa che include direttamente `engine-ui`: Home, Catalogo, Dettaglio e Altro usano `FluidScreen`,
la navigazione adattiva usa `FluidTabBar`/`FluidTabRail`, e liste, controlli, motion e backdrop sono
gli stessi di ClasseViva. Non esiste piu' un porto del look da riallineare a mano.

Con Fluid Engine 1.3.0 il chrome passa al nuovo Fluid Glass ottico. Le azioni refresh della barra
usano `FluidBarAction`; ricerca, filtri, hero, righe e pulsanti dentro le pagine restano solidi per
scelta, non per migrazione incompleta.

---

## Fase 3 — KeyVoice ⏸ ferma, in attesa di una decisione

**Non e' una migrazione, e' una riscrittura**, e per questo si e' fermata qui invece di procedere.
KeyVoice e' tutta View e XML: prendere i componenti dell'engine significa riscrivere l'interfaccia
in Compose, non aggiungere una dipendenza.

Lo stato oggi: KeyVoice usa `engine-update`, che era il pezzo che le serviva davvero — l'updater
in-app non e' piu' 450 righe sue. Se la risposta e' "no", l'app resta cosi' e non le manca niente.

Se la risposta e' "si'", lo scopo e' uno solo: **`MainSetupActivity`**. E' gia' una lista di
sezioni con interruttori, quindi mappa quasi uno a uno su `FluidScreen` + `FluidListGroup` +
`FluidListRow`. Restano fuori il servizio di accessibilita' e la tastiera, che sono View per buone
ragioni: disegnano dentro finestre che non sono un'Activity.

Il costo vero non e' scrivere le schermate: e' che l'app usa `viewBinding` e non ha Compose, quindi
arriva anche il compilatore Compose, il BOM e un aumento dell'APK per un'app che oggi pesa 3,4 MB.

---

## Fase 4 — universal_converter ✅ fatta (2026-08-24, engine 1.2.0)

`FluidScreen` al posto di `Scaffold` su principale, impostazioni e log: titolo grande che si ritira
nella barra, barra in vetro, bordo elastico. Impostazioni su liste raggruppate, con il controllo
segmentato dell'engine al posto dei chip per il tema. Anche l'anteprima passa al segmentato.

`PreviewScreen` resta su `Scaffold` apposta: e' un visualizzatore a tutto schermo, non una pagina
che scorre, e `FluidScreen` e' una lista.

---

## Fase 5 — Pampa widgets ✅ fatta (2026-08-24, engine 1.2.0)

`FluidTabBar` al posto di `NavigationBar`: la barra fluttua sopra il contenuto invece di occupare
una fascia in fondo, ed e' per questo che il contenuto le scorre sotto e si vede sfocato attraverso.
Lo `Scaffold` sparisce perche' la sua `bottomBar` toglie spazio; lo spazio torna al contenuto come
`bottomInset`, cosi' le liste finiscono sopra la barra e non dietro.

`FluidScreen` su store e impostazioni. Il conteggio dei widget passa da pastiglia accanto al titolo
a **faccetta della barra**, dove scorre quando il titolo si e' ritirato.

**La griglia diventa righe di due dentro la lista della pagina.** Una lista dentro un'altra lista
non scorre: quella interna prende il gesto e la pagina resta ferma, quindi il titolo non si
ritirerebbe mai e il bordo elastico non si sentirebbe. E' la trappola in cui si cade convertendo una
schermata a `FluidScreen`, e va cercata prima.

I widget Glance non usano ancora `engine-widget`: e' incluso e pronto.

---

## Fase 6 — il vetro, rifatto (2026-08-24, engine 1.4.0)

Non è una migrazione di un'app: è la sostituzione del materiale che tutte usano.

**Cosa c'era.** Un'ottica scritta a mano che fingeva la rifrazione dipingendo gradienti in un bordo
di due dp, e che per compensare alzava la tinta fino a 0.86 di opacità. Il risultato reggeva in uno
screenshot e crollava in mano: nessuna piega dello sfondo, nessuna risposta al dito, controlli che
erano pastiglie grigie con sopra un'icona.

**Cosa c'è.** La libreria `backdrop` di Kyant ([AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass),
Apache-2.0), copiata come sorgente in `engine-ui/.../ui/glass/`. Lente vera con campo di distanza
della forma del pannello, dispersione cromatica, bordo speculare orientato, ombra interna. `GlassMaterial`
non disegna più niente: sceglie quanta ottica riceve ogni ruolo. La tinta è scesa a un terzo e la
sfocatura è raddoppiata — la leggibilità si compra con il raggio, non con l'opacità.

**Cosa cambia per chi usa l'engine.** Le firme pubbliche (`rememberGlassBackdrop`,
`glassBackdropSource`, `glassSurface`, `FluidTabBar`, `FluidBarAction`) sono le stesse. Cambia
`GlassOptics`, che ora parla di altezza e quantità di rifrazione invece di larghezze di rim, e
`glassControlSurface`, che vuole il backdrop da rifrangere. Nessuna app le usava direttamente.

**Il pezzo che si sbaglia.** Un controllo appoggiato a una barra deve rifrangere la barra. Passargli
la pagina ci apre dentro un buco — ci siamo passati, si vede benissimo. Chrome espone
`LocalGlassBackdrop` già composto; usarlo.

**Come si guarda.** `sample/` è la galleria: si compila da `./gradlew.bat :sample:assembleDebug`
dentro il repo dell'engine e mostra ogni superficie sopra riquadri saturi a righe. Esiste perché il
vetro precedente è stato messo a punto interamente su screenshot, ed è precisamente il modo di
sbagliare che un materiale visibile solo mentre lo tocchi produrrà sempre.

**Crediti.** Vetro di Kyant, Apache-2.0 — `LICENSES/AndroidLiquidGlass.md`. La strada l'abbiamo
imparata da [Square](https://github.com/Lelonio/Square) di @Lelonio. `fluidLicensesSection()` mette
l'elenco nella pagina "informazioni" di ogni app: l'avviso Apache deve viaggiare con l'APK, non
restare in un Markdown nel repo. **Ogni app che aggiorna all'engine 1.4.0 deve aggiungere quella
sezione.**

---

## Fase 7 — il vetro entra nella pagina (2026-08-25, engine 1.5.0)

Di nuovo non è una migrazione: è quello che mancava perché la Fase 6 si vedesse. Il materiale 1.4.0
funzionava nella galleria e in un'app vera era **invisibile**, per un motivo che non è un bug: le
pagine sono superfici grigie piatte, e una lente che rifrange una pagina piatta produce una pagina
piatta. Non c'era niente da guardare attraverso.

**Il canvas ambientale.** `FluidScreen(ambient = FluidAmbient(tone, motif))` dipinge sotto la pagina
tre lavate radiali sull'accento e il motivo dell'hero ingrandito. È opaco: la pagina nel suo insieme
resta opaca, e l'invariante delle transizioni di rotta — con i suoi test — non si muove.

**Due registrazioni, non una.** È il vincolo che decide tutto il resto. `FluidScreen` registra il
corpo che scorre; una card *dentro* quella lista che campionasse quella registrazione conterrebbe se
stessa, ed è un feedback ottico che nel vetro si vede subito. Quindi il canvas si disegna e si chiude
*prima* che la lista esista: il vetro nel contenuto rifrange solo la lavata, la chrome rifrange
canvas + corpo impilati (`rememberCombinedGlassBackdrop`).

**Il vetro nel contenuto.** `FluidCard`, `FluidListGroup` e `FluidMetricTile` prendono `glass = true`.
È una **richiesta**: senza canvas in scope, o sotto API 31, il modificatore torna intatto e il
componente disegna la superficie opaca di sempre. E il vetro va sul contenitore: un gruppo di dodici
righe è un nodo, non dodici.

**Il modale dentro la radice.** `ModalBottomSheet` e `Dialog` vivono in una finestra di piattaforma
separata e non possono leggere il `GraphicsLayer` dell'app — non è un difetto, è un confine di
sistema, e `FluidSheet` resta deprecato con quella nota. `FluidGlassModalPortal` scrive il proprio
contenuto in un host alla radice, quindi il pixel esce sopra la tab bar e il vetro campiona davvero
la pagina. Quattro presentazioni: `Popover`, `Sheet` (trascinabile in basso per chiudere, con
nested-scroll che negozia con la lista dentro), `ContextMenu` in stile iOS e `Expand`, che cresce dal
rettangolo della riga che l'ha aperto.

**La taratura, dopo averla guardata in mano.** La sfocatura è scesa ovunque: barra 10 dp, modale 7,
pop-up 5, capsula 3.6, controlli 2.8, contenuto 1.6. Il pop-up stava a 12 dp **sopra** uno scrim che
aveva già sfocato gli stessi pixel, e il risultato non era vetro: era una card grigio chiaro con
attorno un bordo bianco. Il materiale è la *piega*, la brina serve solo a tenere leggibile il testo
sopra.

**Il difetto che si vedeva di più.** Il menù contestuale mostrava ogni riga due volte: la riga
sollevata è un pannello di vetro, e la riga vera era ancora nella pagina sotto di lei, quindi
arrivava una seconda volta rifratta e spostata di qualche pixel. Ora l'ancora smette di disegnarsi
mentre la sua copia è nell'overlay (`FluidGlassModalEntry.lifted`), e torna solo quando l'overlay ha
finito di uscire — non un fotogramma prima, o l'uscita mostrerebbe di nuovo le due.

**Misurato sul dispositivo** (SM-S931B, build di release, scheda Chrome, dieci scroll):
1.72% di fotogrammi in ritardo, 90° percentile 18 ms, un vsync perso. La scheda con più vetro della
galleria dà gli stessi numeri delle altre. Il costo è tenuto giù registrando la catena di
`RenderEffect` a risoluzione ridotta (`backdropScale`, da 1.0 a 0.4 al crescere del raggio).

**Guardato**: chiaro e scuro, `fontScale` 1.3, pop-up, menù contestuale, foglio, fold della barra,
trascinamento della pastiglia fra le schede. 78 test verdi.

---

## Fase 8 — ClasseViva col vetro addosso, e il conto pagato (2026-08-25, engine 1.5.1 → 1.5.4)

Il piano "Fluid glass redesign": fondale per sezione + contenuto in vetro (fase 1a), liste in
gruppi di vetro (1b delle liste), e la circolare che si apre in un pop-up di vetro dalla riga che
l'ha aperta (1b del piano). Le rotte `communication-detail` e `note-detail` non esistono più: il
dettaglio, che esisteva **tre volte**, è un `FluidGlassModalPortal` solo, e i deep link aprono la
tab e poi il modale.

Tre difetti trovati portandolo sul **tablet** (SM-X710), che è il motivo per cui si prova su più di
un formato:

1. **La pagina Voti arrivava nera.** Un gruppo lista è alto quanto le righe che uno ha — 84 voti,
   sedicimila pixel — e oltre il tetto delle texture GPU la registrazione torna vuota: nero. Difesa
   in due metà: la cattura si stringe da sola (`fitToTexture`, 1.5.1) e il compositing offscreen del
   nodo non esiste più (1.5.3). L'app spezza comunque a dodici righe, perché un pannello di cui non
   vedi mai i bordi non si legge come un pannello.
2. **La pagina Voti scorreva a 10 fps** (p90 101 ms). Non era la catena ottica: era il layer
   offscreen del nodo vetro, che ri-rasterizzava l'intero pannello — testo compreso — a ogni
   fotogramma, per un isolamento che su una pila tutta `SrcOver` non cambia un pixel. Tolto quello
   (e dimezzata la cattura del solo ruolo Content, `GlassOptics.backdropResolution`): **27 ms**,
   in linea con le pagine senza vetro. Misurato con `dumpsys gfxinfo framestats`, che è quello che
   ha puntato al RenderThread invece che alla GPU.
3. **Il titolo grande stava sotto il rail.** Le ancore del morph mescolavano coordinate della
   finestra e coordinate dello schermo; sul telefono coincidono, sul tablet il rail rientra la
   pagina di 100 dp e la differenza diventava una traslazione (1.5.4).

**Guardato sul tablet**: nativo e in formato telefono (`wm size 1080x2340`), chiaro e scuro, Voti
con tutti gli 84 voti, pop-up della circolare aperto dalla riga, chiuso da scrim e da back.

---

## Fase 9 — la fase 1 chiusa: interazioni, non superfici (2026-08-25, engine 1.6.0 → 1.8.0, app 7.4.0)

Il giro di QA di Alessio su telefono e tablet ha spostato il lavoro dal *come appare* al *come
risponde*, ed è lì che stavano i difetti veri — quasi tutti dell'engine, quasi tutti della stessa
famiglia: **qualcosa si muove e qualcos'altro tiene una fotografia di dove stava prima.**

- **La qualità che scende scorrendo** (1.6.0–1.7.0, `FluidGlassQuality`): durante un lancio la
  sfocatura cala verso un pavimento, la dispersione si spegne, la cattura si dimezza; a pagina ferma
  tutto risale. Approvata a occhio: "non si vede nemmeno".
- **La banda senza vetro sulla capsula**: `layerCoordinates` con `neverEqualPolicy` + cattura
  sporcata al cambio di backdrop. Una sorgente che si muove ora ridisegna chi la campiona.
- **La pillola che rompeva i tasti**: la traslazione stava nel layer del vetro, DOPO i gesti — la
  zona di tocco restava sul primo segmento. Ora sta prima, come in `FluidTabBar`.
- **La chiusura dei pop-up**: la lambda del contenuto è UN contenitore mutabile per call site — non
  esiste "l'ultima lambda buona" da rigiocare, alla chiusura le catture sono già null. La variante
  `FluidGlassModalPortal(item = ...)` congela il dato, e il pannello esce intero: morpha nel
  rettangolo esatto della riga che l'ha aperto (richiesto esplicitamente: "il pulsante diventa il
  pannello"), col vetro `sampleOnce` che viaggia con lui e l'alpha che si dissolve prima di toccare
  la riga.
- **La riga che perdeva il testo** scegliendo un'azione dal menù: la preview sollevata sfumava sul
  suo orologio mentre la riga vera restava nascosta fino a fine uscita. La preview ora non sfuma
  mai: ai due estremi coincide con la riga al pixel, e lo scambio è invisibile.
- **L'indice sezioni rifatto**: a riposo tacche e basta, toccato si dispiega in una rotaia di vetro
  a tutta altezza con una lente che segue il dito. La striscia consuma i propri gesti, o la lista
  glielo ruba.
- **I menù sui tasti barra** (`FluidBarAction(actions = ...)`): tenuto premuto, il tasto diventa il
  menù (presentazione Expand). E il clip degli angoli ora prende la stessa decisione per-misura
  della tinta: niente più mezzelune chiare sui pannelli.

Nell'app: **tutti i 17 `FluidSheet` sono diventati portali** (1.5 del piano ✅), i menù contestuali
fanno cose vere (firma dalla lista, allegati aperti direttamente, una voce per file), l'Orario
mostra un giorno alla volta coi tasti che scelgono invece di scrollare, il rail del tablet non
esiste più (capsula ripiegabile in basso a sinistra, su ogni formato), e i pannelli di vetro sono
a 8 righe perché il fotogramma d'ingresso di un pannello nuovo era il singhiozzo residuo delle
liste lunghe.

**Pubblicata** come 7.4.0 stabile sul Pampa Store, engine agganciato pulito a `engine-1.8.0`,
doctor verde, test engine e app verdi, guardata su entrambi i dispositivi.

**Noto e rimandato**: dopo "Firma per confermare" la lista si svuota per ~1 s durante il sync
forzato (repository, non engine); l'uscita del pop-up sfiora ancora la riga per un fotogramma —
lo scambio perfetto richiederebbe il lift della riga anche per i portali, come per i menù.

---

## Fase 10 — Fluid-physics, e la galleria diventa un'app (2026-08-25, engine 1.9.0, Fluid Glass 1.0.0)

La capacità che mancava: **qualsiasi forma di vetro diventa qualsiasi altra**, con la rifrazione
che segue la sagoma mentre viaggia. Un sottosistema nuovo in `engine-ui` (`ui/fluidphysics`):
`FluidForm` (Slab / Poly / Group fino a sei pezzi con ponte liquido smin), `FluidPhysicsState`
(morphTo con ritargeting e scalo automatico per i viaggi gruppo↔sagoma), `fluidPhysicsSurface`
(vetro vero sui tre tier Full/Balanced/Lite), `fluidPhysicsContent` (il contenuto non si stira:
dissolvenza e zoom uniforme). Il clip del layer resta fermo per contratto — la silhouette la
scolpisce l'alpha dello shader — e a riposo la superficie tiene un'istanza stabile: il morphing è
transiente, e la vecchia proibizione resta valida per le schermate.

Le lezioni pagate sul dispositivo, coi collage dei fotogrammi (nostri e di iOS 18 al
rallentatore): AGSL vuole indici costanti negli array uniform (il vertice precedente si trasporta,
non si indicizza); una fusione non estrapola oltre il coincidere (il doppio bordo si vede); la
molla dei contenitori è `standard`, mai `fluid` — **i pannelli Apple non rimbalzano, il rimbalzo è
degli elementi piccoli**; e il termine a cupola (depthEffect) è quello che fa leggere un pezzo da
dimostrazione come palla di vetro invece che adesivo lucido.

La `sample/` è diventata **Fluid Glass** (`dev.antigravity.fluidglass`, slug store `fluid-glass`):
cinque schede — la nuova è il Playground, il banco di prova del motore con preset, disegno a mano
libera, molle e tier dal vivo, loop A↔B per gfxinfo, forme salvate, e il lato pratico (due tasti →
menù → pop-up). Identità da store completa: firma pampa V1+V2+V3, minify, icona adattiva,
aggiornamento in-app dal manifest.json del repo, versionamento suo (1.0.x, separato dall'engine).

**Misure su S25** (release, framestats): loop cerchio↔quadrato ~120 fps con jank 2,1%; il caso
peggiore stella↔blob (anello a 60+ vertici) ~90 fps con jank 2,3%. Tab S9 da guardare al prossimo
aggancio.

**Rimandato, deliberatamente**: la migrazione dei componenti reali (FluidGlassModal, menù
contestuale) sopra Fluid-physics — il ramo 1.8.x è appena stato stabilizzato in sei release, e il
motore doveva prima dimostrarsi nel Playground. È la prossima fase naturale.

---

## Regole che valgono per ogni fase

- **La firma di release è `C:\VibeCoded Projects\pampa.jks`, alias `pampa`**, per tutte le app —
  verificato confrontando l'impronta SHA-256 con quella degli APK installati. Ogni app la legge da
  un file ignorato da git (`local.properties`, o `keystore.properties` in universal_converter).
- **Installare sempre la build di release**, mai la debug: le app hanno
  `applicationIdSuffix = ".debug"`, quindi una debug si installa *accanto* a quella vera e non
  sostituisce niente. È il modo più facile di credere di aver consegnato qualcosa che nessuno vede.
- **Una fase è finita quando la si è guardata sul telefono**, non quando compila.
- Le cinque regole del design system sono in `skill/fluid-engine/references/regole.md`.
