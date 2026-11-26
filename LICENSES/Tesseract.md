# Tesseract, il modello dell'italiano, Leptonica e JavaCPP

Il modulo facoltativo `engine-viewer-ocr` (F8.17, «Leggi testo») dipende, **come librerie binarie**, da Tesseract e
Leptonica attraverso i collegamenti JavaCPP di bytedeco, e porta **negli asset** il modello LSTM dell'italiano di
Tesseract.

| cosa | dove | licenza |
|---|---|---|
| Tesseract OCR 5.5.3 | `org.bytedeco:tesseract:5.5.3-1.5.14` (Maven Central), librerie native per arm64-v8a e x86_64 | Apache-2.0 |
| Leptonica 1.87.0 | `org.bytedeco:leptonica:1.87.0-1.5.14`, con libpng, libjpeg-turbo, libtiff, libwebp, OpenJPEG, giflib, zlib | BSD-2-Clause (Leptonica); libpng, IJG/BSD-3-Clause, libtiff, BSD-3-Clause, BSD-2-Clause, MIT, zlib |
| JavaCPP e JavaCPP Presets 1.5.14 | `org.bytedeco:javacpp`, i collegamenti di `tesseract` e `leptonica` | Apache-2.0 (JavaCPP è anche GPLv2 con l'eccezione del classpath: si usa la Apache-2.0) |
| `ita.traineddata` (tessdata_fast) | `engine-viewer-ocr/src/main/assets/fluid-ocr/tessdata/`, 2.701.314 byte, SHA-256 `b8f89e1e785118dac4d51ae042c029a64edb5c3ee42ef73027a6d412748d8827`, da https://github.com/tesseract-ocr/tessdata_fast (commit del 14/09/2017) | Apache-2.0 |

Sono tutte licenze libere, compatibili con la GPL-3.0 dell'engine: per questo modulo **non serve** il permesso della
sezione 7 (`Permesso-librerie-Google.md`). Il testo della Apache-2.0 è in https://www.apache.org/licenses/LICENSE-2.0.

Gli avvisi viaggiano con l'app nell'elenco dei crediti: un'app che include `engine-viewer-ocr` aggiunge `FluidOcrLicenses`
a `fluidLicensesSection(licences = …)`.
