# Changelog

Le versioni seguono il semantic versioning: **patch** correzioni, **minor** aggiunte compatibili,
**major** rotture. Le voci che richiedono una modifica nelle app che aggiornano sono marcate
**BREAKING** â€” `engine-update.ps1` le evidenzia mentre aggiorna.

## 1.0.1 - 2026-08-23

- versions.gradle: compileSdk e minSdk si lasciano sovrascrivere dal gradle.properties dell app ospite (engine.compileSdk, engine.minSdk), cosi un AGP piu vecchio non esclude l app dall engine
- engine-install.ps1: -Mode copy esiste davvero, e le righe di dipendenza escono nella sintassi del DSL dell app invece che sempre in Groovy
- docs: requisiti dell app ospite, submodule da repo locale (protocol.file.allow), cartella engine gia occupata, local.properties, e la ricetta per adottare il tema senza toccare i punti di chiamata
- la skill per gli agenti vive in skill/fluid-engine dentro questo repo, cosi non puo divergere

<!-- nuove versioni qui sopra -->

## 1.0.0 - 2026-08-23

Prima versione. Estratta da ClasseViva Expressive 7.1.1, ripulita di tutto quello che sapeva cos'e'
un registro scolastico.

- `engine-ui`: il design system Fluid completo â€” angoli continui, tipografia Inter con la scala iOS
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
