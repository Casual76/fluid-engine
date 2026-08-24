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

## Regole che valgono per ogni fase

- **La firma di release è `C:\VibeCoded Projects\pampa.jks`, alias `pampa`**, per tutte le app —
  verificato confrontando l'impronta SHA-256 con quella degli APK installati. Ogni app la legge da
  un file ignorato da git (`local.properties`, o `keystore.properties` in universal_converter).
- **Installare sempre la build di release**, mai la debug: le app hanno
  `applicationIdSuffix = ".debug"`, quindi una debug si installa *accanto* a quella vera e non
  sostituisce niente. È il modo più facile di credere di aver consegnato qualcosa che nessuno vede.
- **Una fase è finita quando la si è guardata sul telefono**, non quando compila.
- Le cinque regole del design system sono in `skill/fluid-engine/references/regole.md`.
