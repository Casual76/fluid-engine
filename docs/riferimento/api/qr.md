<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/api/qr.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# API · engine-qr

_Generato sull'engine 3.1.2 da `scripts/gen_api_catalog.py`. Non si modifica a mano._

## `engine-qr/qr/FluidZxingQr.kt`

- `object FluidZxingQr : FluidQrEncoder` — Il codificatore QR dell'engine: ZXing core, correzione d'errore **M** (il 15% del codice si puo' sporcare o coprire: quella che usano Apple e le app di pagamento per un codice mostrato su uno schermo), testo in UTF-8, nessuna zona vuota (la aggiunge [dev.antigravity.fluidengine.ui.fluid.share.FluidQrCode]). Un testo che non entra in un QR torna null.
- `@Composable fun ProvideFluidQr(content: @Composable () -> Unit)` — Accende i QR per [content]: dentro, il foglio di condivisione mostra «Mostra come QR». Si mette una volta, intorno alla radice dell'app (accanto a `FluidTheme`), o intorno alla sola pagina che condivide.
- `val FluidQrLicenses: List<FluidLicense>` — Da aggiungere a `FluidEngineLicenses` in `fluidLicensesSection(licences = …)` quando l'app include engine-qr.
