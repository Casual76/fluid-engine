package dev.antigravity.fluidengine.ui.fluid

/**
 * I numeri di movimento del testo che si muove (F8.1): le parole e le righe che arrivano sfocate e si mettono a fuoco, le
 * cifre che scorrono, il testo che si espande, l'immagine che arriva. Stanno qui, in un profilo loro, come chiedono le
 * regole del piano («ogni numero di movimento sta nei profili o in `FluidMotion`»), e ognuno dice la fonte e la fiducia
 * oppure «a occhio» con la data.
 *
 * La sfocatura segue la grammatica del contenuto di R7 (F3.4, [GlassContentDefaults]): σ in frazione dell'altezza della
 * sagoma, che per il testo è l'altezza delle maiuscole ([CapHeight] della dimensione del carattere).
 */
object FluidTextMotion {
  /**
   * Quanto ci mette una parola che arriva a mettersi a fuoco: **260 ms**. A occhio il 23 novembre 2026 (nota di Alessio
   * «col blur»): i 180 ms della sola alfa (F3.2) bastavano a una dissolvenza, non lasciano vedere la messa a fuoco. È
   * il tempo di [FluidRevealMillis].
   */
  const val WordArrivalMillis: Int = 260

  /**
   * Il passo fra due parole dello stesso pezzo che arriva: **24 ms**, a occhio il 23 novembre 2026. Un modello manda le
   * parole a pezzi di 3–10: senza passo un pezzo comparirebbe tutto insieme, ed è proprio quello che Alessio non vuole.
   */
  const val WordStaggerMillis: Int = 24

  /**
   * Il ritardo massimo di una parola dietro al testo che arriva: **200 ms**, a occhio il 23 novembre 2026. Il passo non
   * deve accumularsi: con un modello veloce le parole restano al più 200 ms indietro, e restano nell'ordine.
   */
  const val WordSpreadMillis: Int = 200

  /**
   * Quanto ci mette una riga che arriva (il testo che si espande, una risposta che compare intera): **380 ms**, a occhio
   * il 23 novembre 2026, vicino alla molla dell'altezza ([ExpandResponseSeconds]) perché l'ultima riga si mette a fuoco
   * quando la cornice si ferma.
   */
  const val LineArrivalMillis: Int = 380

  /** Il passo fra due righe: **55 ms**, a occhio il 23 novembre 2026 (il «non tutto insieme ma gradualmente» di Alessio). */
  const val LineStaggerMillis: Int = 55

  /** Il ritardo massimo di una riga: **440 ms**, a occhio il 23 novembre 2026: un testo di 30 righe non arriva in due secondi. */
  const val LineSpreadMillis: Int = 440

  /**
   * σ della sfocatura di ciò che arriva, al massimo, in frazione dell'altezza delle maiuscole: **0,15**. È la σ massima
   * del contenuto che cambia identità di R7 A.2–A.3 (0,14–0,17 della sagoma, fiducia alta sulle icone; sul testo la
   * stessa frazione è a occhio, 23 novembre 2026).
   */
  const val ArrivalPeakBlur: Float = 0.15f

  /** L'altezza delle maiuscole di Inter in frazione del corpo: 0,727 (dai dati del carattere, `capHeight / unitsPerEm`). */
  const val CapHeight: Float = 0.727f

  /**
   * La molla delle cifre che scorrono: risposta **0,5 s**, smorzamento 1. È `.smooth`, l'animazione di serie di
   * `withAnimation` da iOS 17 (`Animation.default` è una molla senza rimbalzo di 0,5 s), con cui gira
   * `contentTransition(.numericText(value:))` nei timer di Orologio e nei saldi di Wallet. Fiducia media: la durata è
   * quella documentata, l'aspetto dei saldi è stato confrontato sulle tavole (F8.1).
   */
  const val DigitResponseSeconds: Double = 0.5

  /**
   * Di quanto viaggia una cifra che esce o entra, in frazione dell'altezza della riga: **0,62**. A occhio il 23 novembre
   * 2026 sui timer di Orologio: la cifra nuova non parte da una riga intera di distanza, sale da poco più di metà riga
   * mentre la vecchia esce sfocandosi.
   */
  const val DigitTravel: Float = 0.62f

  /** σ massima di una cifra in viaggio, in frazione dell'altezza delle maiuscole: **0,15**, come [ArrivalPeakBlur] (R7). */
  const val DigitPeakBlur: Float = 0.15f

  /**
   * La dissolvenza delle cifre col movimento ridotto: **200 ms**, quella di [FluidMotion.crossFade]. Apple col movimento
   * ridotto sostituisce lo scorrimento con una dissolvenza (Riduci movimento, «Preferisci le dissolvenze»).
   */
  const val DigitReducedFadeMillis: Int = 200

  /**
   * La molla dell'altezza del testo che si espande: risposta **0,5 s**, smorzamento 1, quella delle superfici grandi
   * ([FluidMotion.ResponseSmooth]) senza il rimbalzo: il testo che si apre nell'App Store non supera la sua altezza.
   * A occhio il 23 novembre 2026.
   */
  const val ExpandResponseSeconds: Double = 0.5

  /**
   * Quanto ci mette un'immagine che arriva a passare dall'impronta alla foto nitida: **300 ms**, il T del contenuto che
   * cambia identità di R7 ([GlassContentDefaults.DurationMs]). `AsyncImage` di SwiftUI non ha una transizione di serie:
   * l'app la sceglie, e qui è quella del contenuto dell'engine.
   */
  const val ImageArrivalMillis: Int = 300

  /** σ massima dell'immagine che arriva: **6 dp**, a occhio il 23 novembre 2026 (leggera: la foto deve leggersi subito). */
  const val ImageArrivalBlurDp: Float = 6f
}
