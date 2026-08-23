# Changelog

Le versioni seguono il semantic versioning: **patch** correzioni, **minor** aggiunte compatibili,
**major** rotture. Le voci che richiedono una modifica nelle app che aggiornano sono marcate
**BREAKING** — `engine-update.ps1` le evidenzia mentre aggiorna.

<!-- nuove versioni qui sopra -->

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
