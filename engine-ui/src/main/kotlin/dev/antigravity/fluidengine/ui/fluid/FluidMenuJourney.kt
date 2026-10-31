package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlin.math.min

/*
 * Il viaggio di un menu (3.0): l'ancora che diventa il pannello e il pannello che torna ancora.
 *
 * Qui stanno SOLO le funzioni pure del viaggio — finestre di opacita', mappa del contenuto verso la
 * sagoma, gonfiore dell'oltrepasso — perche' sono le parti che si sbagliano senza che nessuno se ne
 * accorga (un numero sbagliato di un decimo e' un menu che "non sembra giusto") e si possono
 * inchiodare in JVM, senza un frame clock.
 *
 * TODO(menu 3.0, passi 5-6): `fluidPressedBounds(rest, FluidPressTransform)` (il rettangolo del tasto
 * mentre il dito lo preme, letto al click lungo), `FluidMenuAnchor` e `FluidMenuJourney` (il
 * progresso con la sua molla, `anchorRelax`, `install(physics, anchor)`) arrivano dopo: poggiano su
 * `fluidButtonGesture` / `fluidPressTransform` di FluidPress.kt, che la fase dei tasti sta ancora
 * cambiando, e scriverli adesso vorrebbe dire scriverli due volte.
 */

// --- Finestre di opacita' -------------------------------------------------------------

/**
 * L'opacita' del CONTENUTO del pannello (le voci) e della goccia, al progresso [p] del viaggio.
 *
 * In apertura le voci arrivano nella seconda parte del viaggio (da 0,25 a 0,75, la finestra di
 * `fluidPhysicsContent` Incoming), cosi' non sono mai leggibili mentre la sagoma e' ancora un tasto.
 * In chiusura ([closing]) la finestra e' piu' larga, da 0,92 a 0,12: le voci seguono la sagoma che si
 * ritira e restano leggibili ~60-200 ms invece di ~40 (era l'apertura rovesciata, e a una molla
 * critica che scende veloce cio' voleva dire sparire prima ancora di muoversi).
 *
 * Due finestre e non una perche' un viaggio puo' essere invertito a meta': [closing] sceglie la
 * finestra del verso in cui si sta andando, e la stessa p da' due opacita' diverse nei due versi —
 * voluto, e' la differenza fra il tasto che si apre e quello che si chiude.
 *
 * Lineare e fermata ai capi: e' la stessa forma che hanno le finestre di Fluid-physics. [p] oltre 1
 * (l'oltrepasso della molla) e sotto 0 non cambiano niente.
 */
internal fun fluidMenuContentAlpha(p: Float, closing: Boolean): Float =
  if (closing) {
    unitRamp(p, FluidMotion.MenuContentCloseFrom, FluidMotion.MenuContentCloseTo)
  } else {
    unitRamp(p, FluidMotion.MenuContentOpenFrom, FluidMotion.MenuContentOpenTo)
  }

/**
 * L'opacita' della FACCIA dell'ancora (l'icona o l'etichetta del tasto) al progresso [p]: piena alla
 * partenza, spenta da [FluidMotion.MenuFaceTo] in poi (la finestra `Outgoing` di Fluid-physics). La
 * faccia se ne va nella prima parte del viaggio perche' le voci arrivano nella seconda, e i due
 * testi non devono mai essere leggibili insieme.
 */
internal fun fluidMenuFaceAlpha(p: Float): Float = 1f - unitRamp(p, 0f, FluidMotion.MenuFaceTo)

/**
 * Quanto del materiale del PANNELLO e' gia' sulla sagoma al progresso [p]: 0 e' il materiale
 * dell'ancora, 1 quello del pannello. Una smoothstep che arriva a 1 al 60% del viaggio: la sagoma
 * cambia pelle mentre viaggia, e a pannello quasi arrivato la pelle e' gia' quella finale — niente
 * gradino di luminosita' quando il viaggio si posa, che e' dove l'occhio guarda.
 */
internal fun fluidMenuMaterialBlend(p: Float): Float {
  val x = unitRamp(p, 0f, MaterialArrivesAt)
  return x * x * (3f - 2f * x)
}

/** A questo punto del viaggio la sagoma ha gia' il materiale del pannello. */
internal const val MaterialArrivesAt = 0.6f

/** 0 fino a [from], 1 da [to] in poi, lineare in mezzo. */
private fun unitRamp(p: Float, from: Float, to: Float): Float = ((p - from) / (to - from)).coerceIn(0f, 1f)

// --- La mappa del contenuto ----------------------------------------------------------

/**
 * Dove sta il contenuto del pannello mentre la sagoma viaggia: una traslazione e UNA scala uniforme,
 * mai due scale (un contenuto stirato e' il testo schiacciato del design rigettato del 1.8.x).
 */
internal data class FluidMenuContentMap(val translation: Offset, val scale: Float) {
  companion object {
    /** Il contenuto dov'e': a viaggio finito, o per una geometria che non si sa mappare. */
    val Identity = FluidMenuContentMap(Offset.Zero, 1f)
  }
}

/**
 * La mappa che porta il contenuto, posato alla taglia della posa finale ([target]), sulla sagoma di
 * questo istante ([current]): traslato sul centro corrente e scalato di quanto la sagoma e' piu'
 * piccola o grande del traguardo. E' la regola di `fluidPhysicsContent` (Incoming), che viaggia con
 * la superficie invece di aspettarla: senza, il testo sta fermo mentre il pannello gli cresce sotto
 * — la "tendina tirata su un cartello gia' scritto".
 *
 * La scala e' il rapporto dei lati CORTI, fermata fra [minScale] e [maxScale] (0,72 e 1,12): sotto, le
 * voci diventerebbero un puntino che nessuna opacita' giustifica; sopra, l'oltrepasso gonfierebbe
 * una riga larga fuori dal pannello.
 *
 * Identita' se uno dei due rettangoli e' degenere: meglio il contenuto fermo che un NaN nel layer.
 * Vale per le voci e per la goccia, che e' dello stesso contenuto.
 */
internal fun fluidMenuContentMap(
  target: Rect,
  current: Rect,
  minScale: Float = 0.72f,
  maxScale: Float = 1.12f,
): FluidMenuContentMap {
  if (target.minDimension <= 0f || current.minDimension <= 0f) return FluidMenuContentMap.Identity
  return FluidMenuContentMap(
    translation = current.center - target.center,
    scale = (current.minDimension / target.minDimension).coerceIn(minScale, maxScale),
  )
}

// --- Il gonfiore dell'oltrepasso -------------------------------------------------------

/** Il gonfiore dell'oltrepasso per asse: la frazione di crescita per unita' di (progress − 1). */
internal data class FluidMenuInflation(val x: Float, val y: Float) {
  companion object {
    val None = FluidMenuInflation(0f, 0f)
  }
}

/**
 * Il gonfiore con cui la sagoma oltrepassa il pannello (`overshootInflationX/Y` di
 * `driveExternally`). Estratto da `FluidGlassModal.kt` (la finestra dei pop-up), dove era scritto in
 * linea, perche' il menu del morph, il menu contestuale e l'avviso hanno lo stesso bisogno e non
 * possono avere tre copie di un'aritmetica che si tara a occhio.
 *
 * - **Y**: la molla e' GRANDE QUANTO IL VIAGGIO. Un pannello alto arriva con piu' slancio di un
 *   biglietto, quindi il gonfiore scala con l'altezza ([paneH]) rispetto a un'altezza di riferimento
 *   ([refHeightPx]), fermato fra 0,55 e 1,6, per [gain].
 * - **X**: lo schermo limita i fianchi, il fondo no. La X e' il minimo fra la Y e quanto spazio resta
 *   davvero fra il pannello e il margine minimo ([edgeMinPx]) su ogni lato, in frazione della
 *   larghezza, diviso per il picco atteso dell'oltrepasso ([peakOvershoot]): al picco il pannello
 *   non finisce a ridosso del bordo dello schermo, e la Y non perde vita per colpa dei fianchi.
 *
 * [availW] e' la larghezza disponibile (quella dello schermo o del contenitore che ospita la
 * finestra). Con una taglia non positiva non c'e' niente da gonfiare: [FluidMenuInflation.None].
 *
 * I default ([FluidMotion.MenuInflationGain], [FluidMotion.MenuPeakOvershoot]) sono quelli del menu;
 * il modale di prima passava 0,6 e 0,15 (la molla del morph, che oltrepassava di piu'), ed e' con
 * quei due numeri che un test confronta questa funzione con la sua formula originale.
 */
internal fun fluidMenuInflation(
  paneW: Float,
  paneH: Float,
  availW: Float,
  edgeMinPx: Float,
  refHeightPx: Float,
  gain: Float = FluidMotion.MenuInflationGain,
  peakOvershoot: Float = FluidMotion.MenuPeakOvershoot,
): FluidMenuInflation {
  if (paneW <= 0f || paneH <= 0f || refHeightPx <= 0f || peakOvershoot <= 0f) return FluidMenuInflation.None
  val y = gain * (paneH / refHeightPx).coerceIn(0.55f, 1.6f)
  val room = ((availW - 2f * edgeMinPx - paneW).coerceAtLeast(0f) / paneW) / peakOvershoot
  return FluidMenuInflation(x = min(y, room), y = y)
}
