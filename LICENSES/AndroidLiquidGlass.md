# AndroidLiquidGlass (backdrop)

`engine-ui` include, **come sorgente**, la libreria che disegna davvero il vetro del Fluid Glass.

- Progetto: **AndroidLiquidGlass** (`backdrop`), di **Kyant** ([@Kyant0](https://github.com/Kyant0))
- Repository: https://github.com/Kyant0/AndroidLiquidGlass
- Artefatto pubblicato: `io.github.kyant0:backdrop` (v2.0.0)
- Commit da cui abbiamo copiato: `b18eb0ff12c616546a68c72e7d0097f1ab286c87`
- Licenza: **Apache License, Version 2.0** — testo: https://www.apache.org/licenses/LICENSE-2.0
- Copyright 2025 Kyant

## Cosa abbiamo preso

| Dove sta da noi | Cosa è | Da dove viene |
|---|---|---|
| `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/glass/backdrop/**` | il renderer: cattura del backdrop, catena di `RenderEffect`, shader AGSL della lente e del bordo speculare, ombra interna ed esterna | il modulo `backdrop` della libreria |
| `engine-ui/src/main/kotlin/dev/antigravity/fluidengine/ui/glass/interaction/GlassInteraction.kt` | le primitive di gesto: riconoscimento del drag senza soglia, molle di pressione e schiacciamento, evidenziazione che segue il dito | i componenti d'esempio dell'app *catalog* dello stesso repo |

Ogni file copiato porta in testa l'avviso Apache-2.0 e l'elenco delle modifiche, come richiede la
sezione 4 della licenza.

## Perché è copiata invece che dichiarata come dipendenza

La libreria è pubblicata su Maven Central ed è **Compose Multiplatform**. L'engine è Android puro e
finisce dentro app che dichiarano le proprie dipendenze Compose: aggiungere un artefatto KMP a
cinque `build.gradle` diversi, ognuno con la propria BOM, è un modo affidabile di rompere un'app
alla volta. Le sorgenti copiate compilano contro la BOM che l'app ha già.

Il prezzo è che gli aggiornamenti a monte vanno riportati a mano. Per renderlo possibile i file
sono tenuti il più vicino possibile all'originale, e ogni divergenza è marcata nel codice con un
commento che comincia con **`Fluid Engine change:`** — bastano quelli per fare il diff con una
versione nuova.

Le modifiche fatte finora:

1. **KMP fuso in un solo source set Android.** Le coppie `expect`/`actual` sono state unite:
   sopravvivono i corpi Android, `expect` e `actual` spariscono.
2. **Rinominato il package** `com.kyant.backdrop` → `dev.antigravity.fluidengine.ui.glass.backdrop`.
3. **Tolto il supporto per `io.github.kyant0:shapes`** nella lente: l'engine ha già le sue forme a
   raccordo continuo (`ContinuousCornerShape`) e non voleva una seconda dipendenza per le stesse.
4. **`recordLayer` prende il nodo come parametro** invece che come *context parameter*: quella
   funzione di Kotlin è ancora sperimentale, e accenderla per `engine-ui` la accenderebbe per ogni
   app che ospita il modulo.
5. **La lente non lancia più eccezioni.** A monte, dare al vetro una forma che il campo di distanza
   non sa descrivere è un errore del programmatore e solleva `UnsupportedOperationException`. Qui
   il rettangolo (una barra a tutta larghezza — il vetro più ordinario che esista) è gestito come
   una forma a raggi zero, e qualsiasi altra forma non descrivibile perde la rifrazione e tiene
   sfocatura e vividezza. Un design system che finisce in cinque app deve degradare, non chiudersi.
6. **`@SuppressLint("NewApi")` sulla cache degli shader**, con il contratto scritto accanto: la
   libreria a monte è un modulo multipiattaforma e non passa mai dal lint di Android.

## Cosa fare in pratica

Tenere questo file nel repo e riportare **AndroidLiquidGlass — Apache-2.0 — Copyright 2025 Kyant**
fra le licenze di terze parti nella schermata "informazioni" dell'app. L'engine la fornisce già
pronta: `FluidLicensesSection` in `engine-ui` elenca da sola tutte le voci di
`FluidEngineLicenses`, e quindi anche questa.

## E Square

L'implementazione precedente del vetro era nostra e non funzionava: rifrazione finta dipinta in un
bordo di due dp, opacità alzata per compensare, nessuna risposta al dito. Che la strada giusta
fosse la libreria di Kyant — e che si potesse copiare come sorgente invece che dipendere dal KMP —
l'abbiamo imparato da **[Square](https://github.com/Lelonio/Square)** di
[@Lelonio](https://github.com/Lelonio), un client musicale Android che l'ha fatto per primo e bene.

Da Square non è stato copiato codice: il suo repo è GPL-3.0 e il suo vetro è la stessa libreria
Apache-2.0 che abbiamo preso direttamente da monte. Il debito è di indirizzo, non di righe, ed è
segnalato qui e nel `README.md` perché è comunque un debito.
