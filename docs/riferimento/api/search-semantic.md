<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/search-semantic.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-search-semantic

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-search-semantic/search/semantic/FluidLiteRtEmbedder.kt`

- `class FluidEmbeddingModel(val fileName: String, val sizeBytes: Long, val sha256: String, val url: String, val license: String, val space: FluidEmbeddingSpace)` — Un modello del significato da scaricare o da trovare sul telefono: il file, quanto pesa, la sua impronta e da dove viene. Il modulo non lo porta: lo porta l'app (scaricato col consenso, o da un'altra app Pampa).
- `object FluidEmbeddingModels` — I modelli che l'engine conosce.
  - `val EmbeddingGemma2Text270M: FluidEmbeddingModel` — **EmbeddingGemma 2 Text 270M** per LiteRT-LM (litert-community, commit `9be6e8b9`, Apache-2.0, non «gated»): 270 milioni di parametri a 4 bit, piu' di cento lingue, l'italiano compreso; 768 dimensioni (Matrjoska fino a 128). E' il modello delle domande di MyCloud Gallery 2 (stesso file, stessa impronta: `ModelCatalog.QUERY_270M`), quindi lo stesso spazio di vettori. Misure in `moduli/search.md`.
  - `fun verify(file: File, model: FluidEmbeddingModel = EmbeddingGemma2Text270M): Boolean` — Vero se [file] e' proprio [model]: la dimensione e lo SHA-256 (lettura intera: fuori dal filo principale).
- `class FluidLiteRtEmbedder(private val model: File, private val cacheDir: File, dimensions: Int = FluidEmbeddingSpace.EmbeddingGemma2.dimensions, private val maxInputTokens: Int = 256) : FluidTextEmbedder` — Il modello del significato sul telefono, con **LiteRT-LM** (`EmbeddingEngine`, CPU), come MyCloud Gallery 2: lo stesso motore, lo stesso modello, gli stessi vettori.
  - `var lastInitMillis: Long` — I millisecondi dell'ultimo avvio del motore (-1: mai avviato).
  - `var lastEmbedMillisPerText: Double` — I millisecondi dell'ultimo [embed], diviso per i testi.
  - `suspend fun warmUp(): Long` — Avvia il motore se non c'e': i millisecondi dell'avvio (0 se era gia' acceso).

## `engine-search-semantic/search/semantic/FluidSemanticLicenses.kt`

- `val FluidSemanticSearchLicenses: List<FluidLicense>` — Da aggiungere a `FluidEngineLicenses` in `fluidLicensesSection(licences = …)` quando l'app include engine-search-semantic (e il modello). Il perche' e le versioni: `LICENSES/LiteRT-LM.md`.
