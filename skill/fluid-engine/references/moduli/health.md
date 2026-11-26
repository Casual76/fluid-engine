# engine-health

**Salute e movimento** (4.0, F8.26, `salute-e-movimento`), come HealthKit e l'app Salute: legge e scrive i dati di
**Health Connect** (passi, sonno, battito, allenamenti, peso, acqua) con i **permessi misura per misura** e **la fonte di
ogni dato in vista**, prende **i totali di Health Connect** (che toglie i doppioni con l'ordine delle app scelto dalla
persona) **senza dire cose false**, si sincronizza **per differenze** e tiene la sua copia
**cifrata nella cassaforte**, fuori da ogni telemetria. Porta i pezzi d'interfaccia: **gli anelli del giorno** (non quelli
di Fitness), **le card** con la fonte, **la settimana**, **le righe dei permessi** e la pagina della privacy che Health
Connect chiede. Pacchetto `dev.antigravity.fluidengine.health`.

È un **modulo opt-in** su `engine-ui` ed `engine-vault`, con **una sola libreria**: Health Connect
(`androidx.health.connect:connect-client` 1.1.0, Apache-2.0, che porta Guava). **Il modulo non dichiara nessun permesso
dei dati**: li dichiara l'app, solo quelli che usa.

**L'engine** fa il ponte con Health Connect, i permessi, le letture dai totali del provider, la sincronizzazione, la copia cifrata, gli
anelli, le card e le righe dei permessi. **L'app** decide cosa misurare, gli obiettivi, cosa scrivere, e dichiara i
permessi.

Il primo cliente è **Pampa Sounds**, che scrive il sonno quando si usa il timer della notte. Catalogo delle firme:
[`../api/health.md`](../api/health.md) (generato). La cassaforte è in [`vault.md`](vault.md).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| sapere se Health Connect c'è | `FluidHealthConnectGateway(context).availability()` (`Available`, `NeedsUpdate`, `Unavailable`) |
| chiedere i permessi di una misura | `val req = rememberFluidHealthPermissionRequester { dati -> … }`, `req.request(setOf(FluidHealthPermission.read(FluidHealthMetric.Steps)))` |
| le righe dei permessi nelle impostazioni | `FluidHealthPermissionRows(asked, granted, onRequest = req::request, needsSettings = req::needsSettings)` |
| scrivere il sonno (Sounds, il timer della notte) | `gateway.writeSleep(inizio, fine, title = "Timer della notte")` |
| tenere una copia locale aggiornata per differenze, cifrata | `FluidHealthSync(gateway, FluidVaultHealthStore(FluidVault.get(context).section("salute")), metriche).sync()` |
| i passi di oggi, senza contare due volte telefono e orologio | `gateway.day(FluidHealthMetric.Steps, oggi)` → `FluidHealthReading.NoData` o `Value(valore, fonti, buchi)`: il totale di Health Connect |
| una notte vista da Sounds e dall'orologio | `gateway.night(oggi)` → i minuti di sonno di Health Connect, una notte sola, con le app che l'hanno scritta |
| il battito, il peso, gli allenamenti | `FluidHealthReadings.heart(gateway.aggregate(FluidHealthMetric.HeartRate, da, a))`, `gateway.latest(FluidHealthMetric.Weight, da, a)`, `gateway.day(FluidHealthMetric.Exercise, oggi)` (i minuti) e `gateway.read(…)` (l'elenco) |
| gli anelli del giorno | `FluidHealthRings(listOf(FluidHealthRing("Passi", 7412.0, 10_000.0, "7.412", "10.000"), …))` o `FluidHealthDayCard(anelli, title = "Oggi")` |
| la card di una misura con la fonte | `FluidHealthCard(FluidHealthMetric.Steps, value = "7.412", numeric = 7412.0, unit = "passi", sources = fonti, note = labels.gapLine(buchi))` |
| la settimana | `FluidHealthWeek(FluidHealthFormat.week(gateway.week(FluidHealthMetric.Steps, lunedi), lunedi), FluidHealthMetric.Steps, goal = 10_000.0)` |
| provare senza Health Connect (prove, galleria) | `FluidMemoryHealthGateway(sourcePriority = …).apply { grant(…); put(…) }` (aggrega come Health Connect) e `FluidMemoryHealthStore()` |
| le licenze | `FluidHealthLicenses` (Health Connect, Guava) |

## Le misure e i dati

`FluidHealthMetric` (una classe e non un `enum`, perché le misure crescono): `Steps`, `Sleep`, `HeartRate`, `Exercise`,
`Weight`, `Hydration`. Un permesso è `FluidHealthPermission(metric, write)`; `FluidHealthPermissions.of(p)` dà la stringa
di Health Connect («android.permission.health.READ_STEPS»).

I dati (`FluidHealthSample`) sono di tre forme: `FluidHealthInterval` (passi, millilitri d'acqua), `FluidHealthPoint`
(battiti al minuto, chilogrammi) e `FluidHealthSession` (una notte con le fasi, un allenamento col tipo). Ognuno porta
**la fonte** (`FluidHealthSource`: il pacchetto, il nome dell'app, l'apparecchio — orologio, telefono, anello, bilancia —
e il suo nome). Il battito di Health Connect è un record con tanti campioni: qui diventa un punto per campione, con l'id
`record#indice`.

## I totali sono di Health Connect: `FluidHealthReadings`

**Lo gestisce il provider, noi sommiamo** (scelta di Alessio, F8.26b). L'engine **non ha un suo ordine delle fonti** e
**non somma a mano** i record che si sovrappongono: i totali vengono dalle aggregazioni di Health Connect
(`gateway.aggregate`, `aggregateByDuration`, `aggregateByDays`: `StepsRecord.COUNT_TOTAL`, `HydrationRecord.VOLUME_TOTAL`,
`SleepSessionRecord.SLEEP_DURATION_TOTAL`, `ExerciseSessionRecord.EXERCISE_DURATION_TOTAL`, `HeartRateRecord.BPM_MIN/MAX/AVG`
e `MEASUREMENTS_COUNT`, `WeightRecord.WEIGHT_MIN/MAX/AVG`), e ogni totale porta **le app che l'hanno dato** (`dataOrigins`).

**Come Health Connect toglie i doppioni** (developer.android.com, «Read aggregated data», sezione «Aggregate data affected
by user-selected apps priorities», aggiornata il 10/11/2026):
- l'utente mette in ordine, nelle impostazioni di Health Connect, le app di **attività** e di **sonno**; solo l'utente può
  cambiare quell'ordine (le app non lo leggono e non lo scrivono);
- un'aggregazione tiene, dove due app scrivono gli stessi momenti (i passi del telefono e dell'orologio, due notti),
  **solo i dati dell'app più in alto**; i totali di Health Connect sono quelli che mostra la sua pagina;
- **solo attività e sonno** si tolgono così: per gli altri tipi (acqua, peso, battito) l'aggregazione mette insieme i dati
  di tutte le app.

Quindi una notte scritta da Sounds col timer e dall'orologio è **una notte sola** (`gateway.night(data)`), e i passi di
telefono e orologio **non raddoppiano**: lo decide Health Connect con l'ordine della persona. L'elenco degli allenamenti
(`gateway.read`) resta tutti i record, come nell'app di Health Connect.

Cosa resta all'engine è **come dirlo**:
- **un giorno senza dati è «Nessun dato», mai «0»** (`FluidHealthReading.NoData`: l'aggregazione non ha un valore); un
  giorno con lo zero scritto è zero;
- **i buchi lunghi si dicono come fatti**, non come cause: «Niente dalle 14:15», non «orologio scarico». Vengono dai tratti
  di un quarto d'ora di `aggregateByDuration`, che Health Connect da' **solo dove ci sono dati** (la lista è rada):
  `FluidHealthGapRule`, almeno tre ore nella giornata sveglia fra le 7 e le 23 (scelta di Alessio);
- **il peso** è l'ultima misura (`gateway.latest`), mai una media inventata.

`gateway.day(metrica, data)`, `gateway.week(metrica, lunedì)` e `gateway.night(data)` fanno le letture delle card in una
riga; `FluidHealthReadings.reading(totale, tratti)` le fa da un totale già letto.

## La sincronizzazione: `FluidHealthSync`

La prima volta legge **30 giorni** (`window`, quanto Health Connect dà senza il permesso dello storico) e prende il segno
delle modifiche; poi chiede **solo le modifiche** dal segno (nuovi, cambiati, tolti), pagina per pagina. Un segno scaduto
(Health Connect lo tiene 30 giorni), un permesso dato o tolto o misure diverse rifanno la lettura intera. Le misure senza
permesso vanno in `denied`, e **la loro copia si toglie**: l'app non tiene ciò che non può più leggere. Una
sincronizzazione alla volta; i dati fuori dal thread principale.

**La copia** (`FluidHealthStore`) di serie è **un file della cassaforte** (`FluidVaultHealthStore`): AES-GCM a pezzi con
una chiave del Keystore, **fuori dal backup**. Una sezione `VaultAccess.Device()` basta; con l'impronta, la copia si legge
solo a sezione aperta. Il formato è binario con la versione in testa (`FluidHealthCodec`).

## Fuori da ogni telemetria

Nessuna classe del modulo scrive un valore o un orario nel suo `toString` (un log o un rapporto di crash vedono solo
«FluidHealthInterval(passi, ***)»), il modulo non scrive log e non parla con nessuna telemetria
(nemmeno con la diagnostica dell'engine), e la copia sta fuori dal backup. Un'app che manda rapporti di errore non ci
mette i dati della salute.

## I permessi

L'app dichiara solo ciò che usa (Sounds: solo la scrittura del sonno):

```xml
<uses-permission android:name="android.permission.health.WRITE_SLEEP" />
<!-- un'app di movimento, per esempio: -->
<uses-permission android:name="android.permission.health.READ_STEPS" />
<uses-permission android:name="android.permission.health.READ_SLEEP" />
<uses-permission android:name="android.permission.health.READ_HEART_RATE" />
<uses-permission android:name="android.permission.health.READ_EXERCISE" />
<uses-permission android:name="android.permission.health.READ_WEIGHT" />
<uses-permission android:name="android.permission.health.READ_HYDRATION" />
<!-- e l'indirizzo dell'informativa, che la pagina della privacy mostra: -->
<application>
  <meta-data android:name="dev.antigravity.fluidengine.health.PRIVACY_URL" android:value="https://…/privacy" />
</application>
```

- **Il foglio di Health Connect** si apre col contratto (`FluidHealthPermissions.requestContract()`), misura per misura:
  `rememberFluidHealthPermissionRequester` lo avvolge. **Dopo due rifiuti** della stessa misura Android non mostra più il
  foglio: il richiedente lo conta e da lì apre la pagina di Health Connect (`needsSettings`, «Consenti in Health
  Connect»).
- **Le righe** (`FluidHealthPermissionRows`): una per misura, col simbolo nel colore della misura, cosa l'app chiede
  («Lettura e scrittura») e lo stato («Consentito», «Solo lettura», «Solo scrittura», «Non consentito»); toccata, una riga
  chiede solo ciò che manca **di quella misura**. Senza Health Connect, o da aggiornare, sopra c'è l'avviso.
- **La pagina della privacy** (`FluidHealthPrivacyActivity`, dichiarata dal modulo): Health Connect la apre dal suo elenco
  dei permessi (Android 13 e prima `ACTION_SHOW_PERMISSIONS_RATIONALE`, Android 14 e poi `VIEW_PERMISSION_USAGE` con
  `HEALTH_PERMISSIONS`). Dice in parole semplici cosa l'app legge e scrive (dal suo manifest) e porta all'informativa.

## Gli anelli, le card, la settimana

- **Gli anelli del giorno** (`FluidHealthRings`): uno, due o tre archi concentrici **aperti in basso** (300°, come il
  quadrante di `FluidGauge`), a tinta piena, senza sfumature e senza frecce, con la traccia chiara sotto. **Non sono
  quelli di Fitness** (cerchi chiusi rossi, verdi e blu con la sfumatura e il secondo giro sovrapposto): oltre
  l'obiettivo l'arco resta pieno e la percentuale lo dice. I colori di serie sono l'accento e due tinte della palette
  d'identità (verde acqua, arancio). Quando i valori arrivano gli archi crescono con una molla; col movimento ridotto
  sono già al posto; fermi non ridisegnano. TalkBack: «Passi, 7.412 su 10.000, 74%».
- **La card del giorno** (`FluidHealthDayCard`): gli anelli e, accanto, il nome e «7.412 / 10.000» col numero che scorre
  sfocato quando arriva (è l'app che lo cambia), e sotto la fonte.
- **La card di una misura** (`FluidHealthCard`), come quelle di Salute: il simbolo e il nome nel colore della misura
  (passi arancio, sonno indaco, battito rosa, allenamenti verdi, peso viola, acqua blu: `FluidHealthColors`), l'ora, il
  valore grande con l'unità, **la fonte** («Da Pampa Sounds · telefono», «Da Ritmo e Pampa Sounds», «Da 3 fonti») e i
  buchi. Senza valore dice «Nessun dato». L'apparecchio non si ripete quando il nome dell'app lo dice già: «Da Orologio»,
  non «Da Orologio · orologio» (F8.26c).
- **La settimana** (`FluidHealthWeek`): sette barre fino all'obiettivo tratteggiato; un giorno **senza dati** non ha la
  barra ma un trattino, e TalkBack dice «Nessun dato».
- **Le parole** (`FluidHealthLabels`, italiano di serie e `English`) e i formati (`FluidHealthFormat`: «7.412», «7 h e
  12 min», «72,4», «1,2»).

## Cosa costa

Health Connect 1.1.0 non ha codice nativo; porta Guava (android) e kotlinx-coroutines-guava. Il DEX misurato nella bench
della galleria è nel rapporto di F8.26. Le Health Services dell'orologio (le sessioni d'allenamento su Wear OS, la pillola
di attività) **non ci sono, e non si fanno finché non c'è un'app di allenamento** (scelta di Alessio, F8.26b).

## Trappole

- *Sintomo*: il foglio dei permessi non si apre. → Il permesso non è nel manifest dell'app (Android rifiuta senza
  chiedere), oppure due rifiuti: serve la pagina di Health Connect (`needsSettings`).
- *Sintomo*: «0 passi» in un giorno senza orologio. → Un `?: 0.0` sul `FluidHealthReading`: `NoData` va mostrato come
  «Nessun dato».
- *Sintomo*: i passi di oggi sono il doppio. → I record sommati a mano: il totale si chiede a Health Connect (`gateway.day`,
  `aggregate`), che toglie i doppioni con l'ordine della persona.
- *Sintomo*: un permesso tolto e i dati ancora in vista. → La copia si aggiorna alla `sync()` dopo: chiamarla al ritorno
  da Health Connect.
- *Sintomo*: i dati della salute in un rapporto di errori. → Un oggetto dell'app che li copia in un suo `toString`: le
  classi del modulo non lo fanno.

## Come si prova

- `FluidHealthReadingsTest` (JVM): «Nessun dato» e lo zero vero, la fonte dell'aggregazione, i buchi dai tratti radi del
  provider (il pomeriggio sì, la mattina, la scrivania e la notte no), il battito dalle statistiche, le giornate e la notte,
  il peso che è l'ultimo; **«niente doppioni»**: la card prende il totale dell'aggregazione e non la somma dei record che si
  sovrappongono (4 500 e non 7 600), e l'ordine è quello della persona (col telefono primo 4 200); il finto fa come Health
  Connect (chi ha scritto prima, l'acqua di due app sommata, una notte di due app contata una volta); i `toString` senza
  valori.
- `FluidHealthSyncTest` (JVM): la prima lettura e poi le sole modifiche, il battito tolto coi suoi campioni, il segno
  scaduto, i permessi tolti e parziali, Health Connect assente, la scrittura di Sounds, **la copia cifrata** che sul disco
  non si legge e un'altra sezione non apre, il formato.
- `FluidHealthConnectGatewayTest` (Robolectric): un client finto che implementa l'interfaccia vera `HealthConnectClient`:
  i record veri con la fonte e l'apparecchio, le pagine, le modifiche, i permessi, il sonno scritto; **il totale dei passi
  è `COUNT_TOTAL` del provider** anche coi record che si sovrappongono (nessun record letto), il giorno senza aggregato è
  «Nessun dato», le unità (minuti, millilitri, chili, bpm), i tratti e i giorni dai gruppi del provider.
- `FluidHealthUiTest` (Robolectric): gli anelli con TalkBack, col movimento ridotto e con la molla (e fermi non
  ridisegnano), le card con la fonte e «Nessun dato», la settimana, le righe negate e parziali, i due rifiuti.
- `F8p26PlacesHealthUiTest` (galleria): il giorno che si aggiorna, buchi e doppioni, i permessi rifiutati due volte.
- La galleria: «Elementi nuovi › Luoghi e salute» (`salute`, `salute-fonti`, `salute-permessi`), **sempre con dati
  finti**: i dati veri di Health Connect non si leggono.

## Dove guardare

| cosa | file |
|---|---|
| le misure, le fonti, i dati | `engine-health/.../health/FluidHealthModel.kt` |
| le letture dai totali del provider, i buchi, `day`/`week`/`night` | `FluidHealthReadings.kt` |
| le aggregazioni del finto, fatte come Health Connect | `FluidMemoryHealthAggregation.kt` |
| Health Connect (anche le aggregazioni), i permessi, la porta in memoria, `writeSleep` | `FluidHealthGateway.kt` |
| la sincronizzazione, la copia cifrata, il formato | `FluidHealthSync.kt` |
| anelli, card, settimana, parole, colori | `FluidHealthUi.kt` |
| le righe dei permessi, il richiedente, la pagina della privacy, le licenze | `FluidHealthPermissionsUi.kt` |
| le icone delle misure | `engine-health/icons/icone-health.txt` → `.../health/icons/` |
