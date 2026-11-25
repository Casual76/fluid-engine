<!-- COPIA GENERATA da skill/fluid-engine/scripts/sync_docs.py a partire da skill/fluid-engine/references/moduli/qr.md.
     Non modificare qui: si cambia la fonte e si rilancia lo script. -->

# engine-qr

**Il QR del foglio di condivisione** (4.0, F8.10): «Mostra come QR» di un link o di un testo. Pacchetto
`dev.antigravity.fluidengine.qr`.

È un **modulo opt-in e piccolo**: porta solo ZXing core (Apache-2.0, circa 550 KB prima di R8, senza parti Android e senza
fotocamera) e dipende da `engine-ui`. Il contratto e il disegno stanno in `engine-ui` (`FluidQrEncoder`, `FluidQrMatrix`,
`LocalFluidQrEncoder`, `FluidQrCode`: [../componenti/condivisione.md](../componenti/condivisione.md)); qui c'è solo il
calcolo. Un'app che non include il modulo non vede «Mostra come QR» nel foglio, e niente si rompe. Se un altro modulo
dell'engine dovrà scrivere un QR, passa da qui.

Catalogo delle firme: [`../api/qr.md`](../api/qr.md) (generato).

## Scegliere in fretta

| ho bisogno di… | uso |
|---|---|
| «Mostra come QR» nel foglio di condivisione | `implementation project(':engine-qr')`, poi `ProvideFluidQr { … }` attorno alla pagina (o alla radice dell'app) |
| un QR in una pagina mia (il codice di un invito) | `FluidZxingQr.encode(testo)?.let { FluidQrCode(it, contentDescription = "…") }` |
| i crediti | `fluidLicensesSection(licences = FluidEngineLicenses + FluidQrLicenses)` |
| **leggere** un QR con la fotocamera | non qui: il modulo scrive, non legge (niente permesso della fotocamera) |

## Come è fatto

- `FluidZxingQr.encode(testo)`: `QRCodeWriter` di ZXing, **correzione M** (il 15% del codice si può sporcare o coprire,
  quella dei codici mostrati su uno schermo), UTF-8, nessun margine. Con le misure a zero ZXing dà un pixel per modulo:
  la matrice è già il codice. Un testo vuoto o che non entra in un QR (oltre ~2300 caratteri con la correzione M) dà null.
- `ProvideFluidQr { }`: `LocalFluidQrEncoder provides FluidZxingQr`. Il foglio legge il codificatore dove è dichiarato,
  quindi basta attorno alla pagina.
- Il disegno (`FluidQrCode`, in `engine-ui`): un pixel per modulo in una bitmap fatta una volta, ingrandita senza filtro,
  con la zona vuota di 4 moduli; scuro su chiaro, mai invertito.

## Verifica

`engine-qr/src/test/…/FluidZxingQrTest.kt`, sulla JVM: il codice disegnato come lo disegna `FluidQrCode` (4 pixel per
modulo, zona vuota di 4) si rilegge con il lettore di ZXing, per un link e per un testo con gli accenti; la matrice è
quadrata con i tre occhi; testo vuoto e troppo lungo danno null.

## Licenza

ZXing, Apache License 2.0: `LICENSES/ZXing.md` e `FluidQrLicenses` per la pagina dei crediti.
