# MapLibre Native e OpenStreetMap

Il modulo facoltativo `engine-map` dipende, **come libreria binaria** (non come sorgente), da MapLibre Native per
Android, e mostra le mappe di OpenStreetMap servite da OpenFreeMap.

## MapLibre Native

- Progetto: **MapLibre Native**, dei MapLibre contributors (nato dal codice aperto di Mapbox GL Native)
- Repository: https://github.com/maplibre/maplibre-native
- Artefatto: `org.maplibre.gl:android-sdk-opengl:13.7.1` (Maven Central), fissato in `versions.gradle` (`engine.maplibre`)
- Licenza: **BSD 2-Clause**, il cui testo va con ogni app che la distribuisce:

```
Copyright (c) 2021 MapLibre contributors
Copyright (c) 2018-2021 MapBox

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following
   disclaimer.
2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
   disclaimer in the documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

MapLibre porta a runtime librerie sue e di altri (OkHttp e Okio, Apache-2.0; Timber, Apache-2.0; i gesti, geojson e turf
di MapLibre, BSD e MIT); le licenze di terzi del motore nativo sono nel file `LICENSE.md` del repository di MapLibre.

| Dove sta da noi | Cosa usa |
|---|---|
| `engine-map/src/main/kotlin/dev/antigravity/fluidengine/map/FluidMapLibreEngine.kt` | `MapView` (in `TextureView` o `SurfaceView`), `MapLibreMap` (camera, gesti, stile), `MapSnapshotter` (la cartolina) |

Il resto del modulo (proiezione, grappoli, comandi, foglio, cartolina, attribuzione) è codice dell'engine. L'algoritmo dei
grappoli è quello di Supercluster di Mapbox (ISC), riscritto in Kotlin: nessuna riga copiata.

## I dati e le tessere

- **I dati della mappa** sono © dei contributori di **OpenStreetMap**, sotto la **Open Database License (ODbL) 1.0**
  (https://www.openstreetmap.org/copyright). L'ODbL chiede l'**attribuzione**: `FluidMap` e `FluidMapPostcard` scrivono
  sempre «© OpenStreetMap», e toccando l'attribuzione della mappa si arriva ai crediti interi e alla pagina del copyright
  (le linee guida dell'attribuzione della OpenStreetMap Foundation).
- **Le tessere** di serie arrivano da **OpenFreeMap** (https://openfreemap.org, Hyperknot Software Kft.): un servizio
  gratuito, senza chiave, senza account e senza limiti di visite, anche per uso commerciale; chiede l'attribuzione
  «OpenFreeMap © OpenMapTiles Data from OpenStreetMap», e per i download senza rete al più 3 000 tessere e 50 MB l'uno, al
  più 4 richieste insieme e uno User-Agent che dice l'app. È «così com'è» e può cambiare o fermarsi (i termini:
  https://openfreemap.org/tos/, letti il 26/11/2026).
- **Lo schema e gli stili** delle tessere sono di **OpenMapTiles** (codice BSD-3-Clause, disegno CC-BY-4.0:
  https://www.openmaptiles.org/).

L'avviso viaggia con l'app nell'elenco dei crediti: un'app che include `engine-map` aggiunge `FluidMapLicenses` a
`fluidLicensesSection(licences = …)`.
