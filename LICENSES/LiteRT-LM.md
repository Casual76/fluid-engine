# LiteRT-LM ed EmbeddingGemma 2

Il modulo facoltativo `engine-search-semantic` (F8.22) dipende, **come libreria binaria** (non come sorgente), da
LiteRT-LM, e usa un modello che **non** sta nel repo: lo porta l'app, o un'altra app Pampa.

## LiteRT-LM

- Progetto: **LiteRT-LM**, di Google AI Edge — https://github.com/google-ai-edge/LiteRT-LM
- Artefatto: `com.google.ai.edge.litertlm:litertlm-android:0.18.1` (Google Maven), fissato in `versions.gradle`
  (`engine.litertlm`); la stessa versione di MyCloud Gallery 2
- Licenza: **Apache License 2.0** (l'AAR porta `LICENSE` e `THIRD_PARTY_NOTICE.txt` con le librerie native che contiene)
- Cosa porta nell'APK: `liblitertlm_jni.so` (22,2 MB arm64-v8a, 26,4 MB x86_64) e le classi del pacchetto
  `com.google.ai.edge.litertlm` (191 KB)
- Dipendenze tolte: `kotlin-reflect` 2.4.0 e `kotlinx-coroutines` 1.11.0 (`engine-search-semantic/build.gradle`)

## EmbeddingGemma 2 Text 270M

- Modello: `embeddinggemma-2-text-270m.litertlm` di **litert-community** su Hugging Face, commit `9be6e8b9`,
  164 626 432 byte, SHA-256 `2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb`
  (`FluidEmbeddingModels.EmbeddingGemma2Text270M`)
- Licenza: **Apache License 2.0**, non «gated» (scheda del modello, letta il 25/11/2026)
- Non si distribuisce con l'engine: un'app che lo scarica o lo porta riporta il credito

Tutti e due sono compatibili con la GPL-3.0 dell'engine in questo verso. L'avviso viaggia con l'app nell'elenco dei
crediti: un'app che include `engine-search-semantic` aggiunge `FluidSemanticSearchLicenses` a
`fluidLicensesSection(licences = …)`. Il testo della licenza è in https://www.apache.org/licenses/LICENSE-2.0.

`engine-search` (l'indice e «Cerca in Pampa») non porta librerie nuove: SQLite FTS4 è quello del telefono.
