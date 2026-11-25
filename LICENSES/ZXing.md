# ZXing

Il modulo facoltativo `engine-qr` dipende, **come libreria binaria** (non come sorgente), da ZXing core.

- Progetto: **ZXing** («Zebra Crossing»), dei ZXing authors
- Repository: https://github.com/zxing/zxing
- Artefatto: `com.google.zxing:core:3.5.4` (Maven Central), fissato in `versions.gradle` (`engine.zxing`)
- Licenza: **Apache License 2.0**

## Cosa usiamo

| Dove sta da noi | Cosa usa |
|---|---|
| `engine-qr/src/main/kotlin/dev/antigravity/fluidengine/qr/FluidZxingQr.kt` | `QRCodeWriter` per calcolare la matrice di un QR (correzione M, UTF-8) |
| `engine-qr/src/test/…/FluidZxingQrTest.kt` | `QRCodeReader`, solo nei test, per rileggere il codice |

L'avviso viaggia con l'app nell'elenco dei crediti: un'app che include `engine-qr` aggiunge `FluidQrLicenses` a
`fluidLicensesSection(licences = …)`. Il testo della licenza è in https://www.apache.org/licenses/LICENSE-2.0.
