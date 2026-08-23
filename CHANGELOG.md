# Changelog

Le versioni seguono il semantic versioning: **patch** correzioni, **minor** aggiunte compatibili,
**major** rotture. Le voci che richiedono una modifica nelle app che aggiornano sono marcate
**BREAKING** — `engine-update.ps1` le evidenzia mentre aggiorna.

<!-- nuove versioni qui sopra -->

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
