# Haze

Dalla 4.0 `engine-ui` include, **come sorgente**, un pezzo di Haze: il nucleo della sfocatura progressiva a due
passate con cui la striscia del bordo dello scorrimento scioglie il contenuto sotto i tasti di una barra (piano «iOS
in Android», F2.4, 3.10). Il codice è portato, non riscritto da una descrizione; il resto della striscia (profilo,
velo, scurimento, dissolvenza, nodo, API) è dell'engine.

- Progetto: **Haze**, di **Chris Banes** e dei contributori di Haze
- Repository: https://github.com/chrisbanes/haze
- Ramo e commit da cui abbiamo portato: `main`, `95c0c48a915d3593e7f8d34039404f54f4e538e7` (letto il 12 novembre 2026)
- Licenza: **Apache License, Version 2.0**: testo https://www.apache.org/licenses/LICENSE-2.0
- Copyright 2024–2026, Christopher Banes and the Haze project contributors (`SPDX-License-Identifier: Apache-2.0`
  in testa a ogni file)

Apache-2.0 è compatibile con la GPL-3.0 dell'engine in questo verso: il codice di Haze si può copiare e modificare
tenendo l'avviso, dichiarando le modifiche e citando la fonte (sezione 4 della licenza). Il verso contrario no:
nulla dell'engine torna in Haze sotto Apache.

## Cosa abbiamo preso

| Dove sta da noi | Cosa è | Da dove viene |
|---|---|---|
| `engine-ui/.../ui/glass/field/GlassScrollEdgeShader.kt`, `GlassEdgeBlurKernel.forSigma` | il nucleo gaussiano limitato: il peso del centro e al più **venti coppie** di campioni per passata, ognuna al baricentro gaussiano di un gruppo di pixel (gruppi di almeno due, esatti con la lettura bilineare; fino a otto sottocampioni per gruppo), supporto a 3 sigma; sotto mezzo pixel di sigma un campione solo a un pixel; fino a 2 pixel le posizioni riscalate perché la varianza sia proprio sigma² | `haze-glass/src/commonMain/kotlin/dev/chrisbanes/haze/glass/SemanticBlurKernel.kt`, `SemanticBlurKernel.createForSigma`, `MAX_TAP_PAIRS`, `SIGMA_SUPPORT`, `MAX_INTEGRATION_SAMPLES_PER_TAP` |
| idem, `GlassEdgeBlurKernel.radiusToSigma` | la sigma di un raggio, `0,57735 r + 0,5 · min(r, 1)` (è anche il conto di HWUI) | `SemanticBlurKernel.radiusToSigma` |
| idem, `GlassEdgeBlurMaxRadiusPx` | il tetto di 38,5 px di raggio, nello spazio della cattura, oltre il quale i gruppi si allargano e su un testo nitido si vede il disegno dei campioni | `SemanticBlurKernel.MAX_PROGRESSIVE_RADIUS_PX` |
| idem, `GlassEdgeBlurShaderString` | la forma della passata: il centro più le coppie lette ai due segni lungo una direzione, con le **posizioni scalate per pixel** dalla quota di sfocatura locale (la sfocatura progressiva resta gaussiana a ogni raggio); i campioni tenuti dentro il ritaglio, mezzo pixel dentro il bordo | `haze-glass/.../GlassShaders.kt`, `buildBlur(horizontal, progressive = true)`; `haze-utils/src/commonMain/kotlin/dev/chrisbanes/haze/ProgressiveBlurRenderEffect.kt`, `clampToCrop`; le due passate in catena (orizzontale, poi verticale che legge la prima) come in `createProgressiveBlurRenderEffect` |

Il file porta in testa la provenienza (file, commit, licenza) e cosa è cambiato.

## Cosa abbiamo cambiato

- **I nomi, la lingua dei commenti e il formato** (due spazi). Le formule e le costanti del nucleo sono le loro.
- **Le coppie stanno in due array** (`offsets`, `weights`) invece di una lista di oggetti `Tap`: si scrivono così
  negli uniform, e il calcolo non alloca liste. Il nucleo riceve la sigma, non un raggio di Compose.
- **Lo shader è riscritto.** Haze genera venti coppie di uniform (`offset0`, `weight0`, …) e una lettura per coppia
  srotolata nel testo; qui c'è un array `taps[20]` e un ciclo a limite costante (una sorgente sola, che il banco e la
  sonda di salute leggono come `const val`). La quota di sfocatura per pixel non viene da una maschera (`uniform
  shader mask`) ma da una rampa analitica in altezze dei tasti, e la direzione è uno uniform: una variante sola per
  le due passate.
- **Ciò che sta dopo la sfocatura è dell'engine**: il velo verso il colore del fondo, lo scurimento sul contenuto
  scuro, la dissolvenza d'alfa dove la striscia finisce (col velo diviso per la copertura), il profilo tarato su R4
  B7, il nodo `FluidScrollEdge` e la sua API.

Non abbiamo preso altro da Haze: né il renderer del vetro (`haze-glass`), né la scala adattiva, né i ripieghi sotto
Android 13. Le idee di Haze che il piano cita per altre fasi (campo fuso, scala con isteresi) sono idee, e se
arriveranno come codice avranno la loro riga qui.
