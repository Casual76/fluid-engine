# Changelog

Le versioni seguono il semantic versioning: **patch** correzioni, **minor** aggiunte compatibili,
**major** rotture. Le voci che richiedono una modifica nelle app che aggiornano sono marcate
**BREAKING** — `engine-update.ps1` le evidenzia mentre aggiorna.

<!-- nuove versioni qui sopra -->

## 1.3.0 - 2026-08-24

- GlassMaterial aggiunge bordi ottici, rifrazione del backdrop e profili Bar, Floating, Interactive e Modal con fallback progressivi per API e shape.
- FluidBarAction, back button, tab bar, notifiche e indici di sezione adottano lenti Fluid Glass senza blur annidati e con target touch da 48 dp.
- FluidScreen espone un overlay post-body per campionare correttamente il backdrop; sheet e alert usano un fallback ottico coerente nelle finestre modali.
- Il sampling resta ritagliato per superficie, sveglia ogni nuova anchor senza loop e crea lo shader runtime solo quando realmente supportato.


## 1.2.4 - 2026-08-24

- Il backdrop glass registra e sfoca solo le regioni realmente campionate, mantenendo blur, saturazione e texture invariati.
- Le maschere FadeDown riusano gli oggetti nello stato stabile e riducono le allocazioni durante lo scroll.
- Le superfici pressabili e i gruppi lista evitano RenderNode e clip ridondanti.


## 1.2.3 - 2026-08-24

- FluidScreen: il colore del contenuto vale anche per la barra. La 1.2.2 aveva avvolto solo la lista, e il titolo restava nero su fondo nero: quello che si vede mentre il titolo grande si ritira e la copia della barra, trasformata sull ancora del titolo, e la barra sta fuori dal corpo


## 1.2.2 - 2026-08-24

- FluidScreen dichiara il colore del contenuto. Dipingeva il proprio sfondo ma lasciava il testo a ereditare LocalContentColor, che fuori da un Surface vale nero: un app che non avvolge tutto l albero in un Surface si ritrovava il titolo grande nero su fondo nero. Chi dipinge il fondo dichiara anche cosa ci va sopra, altrimenti il componente funziona solo dentro l app da cui e stato estratto


## 1.2.1 - 2026-08-24

- Gli angoli circolari rimasti dentro l engine sono diventati continui: il gruppo di liste era a RoundedCornerShape(20.dp) mentre due righe piu sotto lo stesso file usava ContinuousCornerShape(FluidRadius.Group), la piastrella d icona e la barra a colonna erano circolari. Era la regola 1 del design system violata dentro l engine che la scrive
- Resta un solo angolo circolare, in RouteMotion, ed e deliberato: li il raggio cambia a ogni fotogramma e un angolo continuo costringerebbe il layer a ritagliare su un Path generico invece che su un rettangolo arrotondato, piu caro proprio durante l animazione che deve restare fluida. Ora c e scritto perche
- ADOZIONE.md alla radice tiene il piano per portare i componenti su tutte le app e lo stato di ognuna


## 1.2.0 - 2026-08-23

- FluidHero: l intestazione editoriale per una schermata piena di dati - un valore grande, fatti secondari che passano da tre a due a una colonna secondo lo spazio misurato e la scala del carattere, un motivo astratto sul fondo. Portata da ClasseViva, dove era rimasta fuori dall estrazione perche il suo enum si chiamava FeatureIdentity
- FluidHeroTone e FluidHeroMotif separano le due cose che quell enum teneva insieme: la posizione sull anello delle tre famiglie di accento, e la forma disegnata dietro. L app mappa le sue sezioni su tono e motivo, e il dominio resta nell app
- FluidHero: urgent promuove alla famiglia dell errore qualunque tono, non piu solo uno. Prima veniva ignorato in silenzio per sei identita su sette


## 1.1.1 - 2026-08-23

- Gli script leggevano i file con Get-Content -Raw, che in PowerShell 5.1 decodifica in ANSI e non in UTF-8: leggere e riscrivere corrompeva un po piu ogni carattere accentato a ogni esecuzione. Ha gia mangiato due trattini in questo file, e sarebbe successo al settings.gradle di qualunque app con un commento accentato


## 1.1.0 - 2026-08-23

- engine-install.ps1 -Modules: si scelgono i moduli da includere, e le dipendenze fra moduli si chiudono da sole. Senza, un app senza il plugin Compose nel build root non riusciva nemmeno a configurare il build: e la ragione per cui KeyVoice non poteva ospitare l engine
- engine.properties registra i moduli scelti; engine-doctor.ps1 verifica che il settings.gradle li rispecchi ed engine-update.ps1 non li perde piu aggiornando la versione
- EngineHttp.download verifica la dimensione attesa. Un download troncato non solleva niente e arrivava all installer come mezzo APK
- EngineHttp e open: leggere un manifest e decidere se una release e piu nuova sono logica ordinaria, e una classe final costringeva ogni app ospite a toccare la rete per testarla
- engine-update: il controllo che l APK sia davvero questa app estratto in rejectApk() e coperto da cinque test. Era inline nel flow, quindi verificabile solo su un dispositivo
- engine-update: STATUS_FAILURE_ABORTED non dice piu solo annullata. Verificato su un dispositivo reale: Play Protect blocca l installazione e Android riporta lo stesso codice
- modalita porto: un app che non puo ospitare i moduli (Compose Multiplatform) dichiara engine.mode=port e viene contata lo stesso
- versions.gradle leggeva una versione scritta a mano, ferma alla 1.0.0: ora la prende da ENGINE_VERSION
- engine-ui: rimossa un estensione Offset.times morta, gia coperta da un membro di Compose
- CHANGELOG: doppia codifica riparata, versioni dalla piu recente in giu


## 1.0.2 - 2026-08-23

- engine-install.ps1 -Mode copy: le cartelle build/ annidate finivano nella copia (centinaia di MB e percorsi troppo lunghi). Ora la copia passa da robocopy, che esclude a ogni profondita
- engine-install.ps1 esce con codice 0 esplicito: prima restituiva quello dell ultimo comando nativo, e chi automatizza l installazione lo leggeva come errore

## 1.0.1 - 2026-08-23

- versions.gradle: compileSdk e minSdk si lasciano sovrascrivere dal gradle.properties dell app ospite (engine.compileSdk, engine.minSdk), cosi un AGP piu vecchio non esclude l app dall engine
- engine-install.ps1: -Mode copy esiste davvero, e le righe di dipendenza escono nella sintassi del DSL dell app invece che sempre in Groovy
- docs: requisiti dell app ospite, submodule da repo locale (protocol.file.allow), cartella engine gia occupata, local.properties, e la ricetta per adottare il tema senza toccare i punti di chiamata
- la skill per gli agenti vive in skill/fluid-engine dentro questo repo, cosi non puo divergere

## 1.0.0 - 2026-08-23

Prima versione. Estratta da ClasseViva Expressive 7.1.1, ripulita di tutto quello che sapeva cos'e'
un registro scolastico.

- `engine-ui`: il design system Fluid completo — angoli continui, tipografia Inter con la scala iOS
  e la curva di tracking, palette derivata da un solo accento, liste raggruppate, motion unificato,
  materiale in vetro, notifiche in-app, tab bar, fogli, controlli.
- `engine-ui`: il colore del marchio e' un parametro (`FluidTheme(settings, brand)`) invece di una
  costante dell'app da cui l'engine e' nato.
- `engine-foundation`: `EngineSettings`, `SyncStatus` con il campo `notice` generico, confronto di
  versioni, modelli del manifest e risoluzione dei feature flag.
- `engine-config`: manifest remoto con flag, override per singola app, versione minima e kill
  switch; cache su DataStore e comportamento invariato quando la rete non risponde.
- `engine-update`: aggiornamento in-app via `PackageInstaller` sul modello Pampa Store, con canale
  stable/beta e verifica che l'APK scaricato sia davvero questa app a questa versione.
- `engine-widget`: palette Glance derivata dallo stesso schema colori dell'app, budget di layout per
  le celle del launcher e componenti (superficie, intestazione, gruppo, riga, hairline, pill).
- `engine-storage`: `EngineSettingsStore` e la cache del manifest.
- `tools/`: install, update, update-all, doctor, release.
