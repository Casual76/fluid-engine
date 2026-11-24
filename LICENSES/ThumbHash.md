# ThumbHash

`engine-ui` include, **come sorgente**, il codificatore e il decodificatore di ThumbHash.

- Progetto: **ThumbHash**, di **Evan Wallace** ([@evanw](https://github.com/evanw))
- Repository: https://github.com/evanw/thumbhash
- Da dove viene: la versione di riferimento in Java (`java/com/madebyevan/thumbhash/ThumbHash.java`) e in JavaScript
  (`js/thumbhash.js`), portate in Kotlin il 23 novembre 2026 (F8.1)
- Licenza: **MIT**
- Copyright (c) 2023 Evan Wallace

## Cosa abbiamo preso

| Dove sta da noi | Cosa è | Da dove viene |
|---|---|---|
| `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/fluid/FluidThumbHash.kt` | `FluidThumbHash.encode`, `decode`, `averageColor`, `aspectRatio`: l'impronta di un'immagine in poche decine di byte e la sua miniatura sfocata coi colori veri | `rgbaToThumbHash`, `thumbHashToRGBA`, `thumbHashToAverageRGBA`, `thumbHashToApproximateAspectRatio` |

Le formule sono quelle dell'originale, riga per riga. Cosa è nostro: i nomi e i commenti in italiano, la cache delle
impronte decodificate (`FluidThumbHash.imageBitmap`) e il passaggio a `ImageBitmap` di Compose. L'avviso è anche
nell'elenco dei crediti che le app mostrano (`FluidEngineLicenses`, `fluidLicensesSection()`).

## Il testo della licenza

```
Copyright (c) 2023 Evan Wallace

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
