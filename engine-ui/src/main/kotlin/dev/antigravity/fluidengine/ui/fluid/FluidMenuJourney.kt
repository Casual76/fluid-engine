package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import dev.antigravity.fluidengine.ui.fluidphysics.FluidForm
import dev.antigravity.fluidengine.ui.fluidphysics.FluidPhysicsState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/*
 * Il viaggio di un menu (3.0): l'ancora che diventa il pannello e il pannello che torna ancora.
 *
 * Le funzioni pure del viaggio — finestre di opacita', mappa del contenuto verso la sagoma, gonfiore
 * dell'oltrepasso, il rettangolo del tasto premuto, le misure e il posto del pannello — stanno qui
 * perche' sono le parti che si sbagliano senza che nessuno se ne accorga (un numero sbagliato di un
 * decimo e' un menu che "non sembra giusto") e si possono inchiodare in JVM, senza un frame clock.
 *
 * Accanto, i due pezzi con stato che i due host condividono (fase 7): [FluidMenuAnchor], l'ancora che
 * si nasconde e passa il dito, e [FluidMenuJourney], il progresso con la sua molla che guida la
 * finestra di Fluid-physics. Un menu che nasce da un tasto, quello di una riga e quello di una barra
 * fanno lo stesso viaggio perche' e' lo stesso oggetto a farlo.
 */

// --- Finestre di opacita' -------------------------------------------------------------

/**
 * L'opacita' del CONTENUTO del pannello (le voci) e della goccia, al progresso [p] del viaggio.
 *
 * **Il contenuto sta dentro il vetro formato, mai sopra il vetro che si forma** (3.0, il vetro
 * liquido). In apertura le voci arrivano mentre il corpo si forma (da 0,4 a 0,75; fino alla 3.0.3 da
 * 0,55 a 0,9, e il menu sembrava aprirsi lento), ritagliate dentro di lui; in
 * chiusura se ne vanno PER PRIME (da 0,94 a 0,62, una sessantina di millisecondi con la molla del
 * ritorno), attaccate al corpo e rimpicciolite verso l'ancora, e il liquido si ritira vuoto. Prima la
 * chiusura le teneva leggibili fino a 0,12 mentre il pannello si ritirava: la scala del contenuto,
 * fermata a 0,72, non stava piu' dentro una sagoma che scendeva verso il 25% — le scritte uscivano dal
 * vetro e galleggiavano sulla pagina mezze trasparenti. Era "il testo che se ne va in modo strano".
 *
 * Due finestre e non una perche' un viaggio puo' essere invertito a meta': [closing] sceglie la
 * finestra del verso in cui si sta andando, e la stessa p da' due opacita' diverse nei due versi —
 * voluto, e' la differenza fra il tasto che si apre e quello che si chiude.
 *
 * Lineare e fermata ai capi. [p] oltre 1 (l'oltrepasso della molla) e sotto 0 non cambiano niente.
 */
internal fun fluidMenuContentAlpha(p: Float, closing: Boolean): Float =
  if (closing) {
    unitRamp(p, FluidMotion.LiquidContentCloseFrom, FluidMotion.LiquidContentCloseTo)
  } else {
    unitRamp(p, FluidMotion.LiquidContentOpenFrom, FluidMotion.LiquidContentOpenTo)
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

/**
 * La mappa delle voci di un menu di vetro liquido (3.0): traslate sulla sagoma a riposo di adesso
 * (spostata con la goccia: [current]), e scalate "di poco verso l'ancora" con la loro opacita' —
 * `0,86 + 0,14 · alpha`, [FluidMotion.LiquidContentMinScale] — non un menu in miniatura che
 * rimpicciolisce col corpo. Dove il corpo e' piu' piccolo del contenuto lo ritaglia (`travelClip`).
 */
internal fun fluidMenuLiquidContentMap(target: Rect, current: Rect, alpha: Float): FluidMenuContentMap {
  if (target.minDimension <= 0f || current.minDimension <= 0f) return FluidMenuContentMap.Identity
  val a = alpha.coerceIn(0f, 1f)
  return FluidMenuContentMap(
    translation = current.center - target.center,
    scale = FluidMotion.LiquidContentMinScale + (1f - FluidMotion.LiquidContentMinScale) * a,
  )
}

/**
 * Il gonfiore della goccia oltre il pannello (3.0): uniforme, [FluidMotion.DropInflationGain] per
 * unita' d'oltrepasso, e sui fianchi mai oltre quanto lo spazio fra il pannello e il margine minimo
 * ([edgeMinPx]) consente al picco della molla ([FluidMotion.DropPeakOvershoot]).
 */
internal fun fluidDropInflation(paneW: Float, availW: Float, edgeMinPx: Float): FluidMenuInflation {
  if (paneW <= 0f) return FluidMenuInflation.None
  val gain = FluidMotion.DropInflationGain
  val room = ((availW - 2f * edgeMinPx - paneW).coerceAtLeast(0f) / paneW) / FluidMotion.DropPeakOvershoot
  return FluidMenuInflation(x = min(gain, room), y = gain)
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

// --- Il tasto premuto -----------------------------------------------------------------

/**
 * Il rettangolo di un tasto mentre il dito lo preme: [rest] trasformato da [transform], come lo
 * trasforma il `layerBlock` di `glassControlSurface` (scala attorno al centro, poi la deriva).
 *
 * Si legge al click lungo, quando la pressione e' ancora ~1: e' da li' che la sagoma del menu parte.
 * Partire dal rettangolo a riposo voleva dire un primo fotogramma piu' piccolo del tasto che il dito
 * stava ancora gonfiando — un sussulto all'indietro proprio dove l'occhio guarda.
 */
internal fun fluidPressedBounds(rest: Rect, transform: FluidPressTransform): Rect {
  if (rest.width <= 0f || rest.height <= 0f || transform.isIdentity) return rest
  val cx = rest.center.x + transform.translationX
  val cy = rest.center.y + transform.translationY
  val hw = rest.width * transform.scaleX / 2f
  val hh = rest.height * transform.scaleY / 2f
  return Rect(cx - hw, cy - hh, cx + hw, cy + hh)
}

/** [fluidPressedBounds] con la pressione di adesso di [press]. Fuori dal disegno: e' un'istantanea. */
internal fun fluidPressedBounds(rest: Rect, press: FluidPressState, density: Float): Rect =
  fluidPressedBounds(
    rest,
    fluidPressTransform(
      feedback = press.feedback,
      progress = press.progress,
      lean = press.lean,
      width = rest.width,
      height = rest.height,
      dp = density,
      reducedMotion = press.reducedMotion,
    ),
  )

// --- Il pannello: misure e posto ----------------------------------------------------------

/**
 * Le misure del pannello di un menu, in pixel. UNA aritmetica per chi risolve il pannello (l'host,
 * prima ancora di comporlo, perche' l'apertura e' sincrona) e per chi lo disegna e ne fa il
 * hit-test: con due aritmetiche il dito sceglierebbe la riga sopra quella che vede.
 */
@Immutable
internal data class FluidMenuPanelMetrics(
  val widthPx: Float,
  val rowHeightPx: Float,
  val paddingPx: Float,
  val sectionGapPx: Float,
  val radiusPx: Float,
  val gapPx: Float,
  val marginPx: Float,
  val edgeMinPx: Float,
  val refHeightPx: Float,
)

/**
 * L'altezza di una riga di menu: `max(48 dp, lineHeight + 24 dp)`. Segue il font (al testo grande
 * la riga cresce invece di tagliare l'etichetta), e a 1× e' la riga di sempre.
 */
internal fun fluidMenuRowHeight(lineHeight: Dp): Dp = max(FluidMenuMinRowHeight.value, (lineHeight + 24.dp).value).dp

/**
 * Quanto e' largo il pannello di un menu per le sue voci (3.0.1): la misura di serie
 * ([FluidMenuPanelMetrics.widthPx], 252 dp) se ci stanno, altrimenti quanto serve alla voce piu' lunga —
 * etichetta, icona e spunta — fino a [FluidMenuPanelMaxWidth] e mai oltre lo schermo meno i margini.
 * Prima era sempre 252 dp, e una voce che porta il suo stato («Salta i silenzi oltre 12 s: spento»)
 * perdeva proprio lo stato nei puntini. Oltre il tetto i puntini restano: una riga di menu e' una riga.
 *
 * @param labelWidthsPx la larghezza di ogni etichetta, nel tipo delle voci.
 * @param hasIcon, hasCheck se qualche voce ha l'icona o la spunta (lo spazio vale per tutte, le colonne
 *   restano allineate).
 */
internal fun fluidMenuPanelWidth(
  metrics: FluidMenuPanelMetrics,
  labelWidthsPx: List<Float>,
  hasIcon: Boolean,
  hasCheck: Boolean,
  density: Float,
  hostWidthPx: Float,
): Float {
  val widest = labelWidthsPx.maxOrNull() ?: 0f
  // Le misure della riga di FluidGlassMenuPanel: 16 dp per lato, 12 dp fra l'etichetta e ogni segno da 20.
  val extras = (FluidMenuRowSidePadding.value * 2f +
    (if (hasIcon) FluidMenuRowGlyph.value + FluidMenuRowGap.value else 0f) +
    (if (hasCheck) FluidMenuRowGlyph.value + FluidMenuRowGap.value else 0f)) * density +
    metrics.paddingPx * 2f
  val wanted = widest + extras
  val ceiling = minOf(FluidMenuPanelMaxWidth.value * density, hostWidthPx - metrics.marginPx * 2f)
  return wanted.coerceAtMost(ceiling).coerceAtLeast(metrics.widthPx).coerceAtMost(maxOf(metrics.widthPx, ceiling))
}

/**
 * La larghezza di un'etichetta di menu nel tipo delle voci, per [fluidMenuPanelWidth]: misurata col
 * `TextMeasurer` di Compose, fuori dal disegno, perche' l'apertura risolve il pannello prima di comporlo.
 */
@Composable
internal fun rememberFluidMenuLabelMeasure(): (String) -> Float {
  val measurer = androidx.compose.ui.text.rememberTextMeasurer()
  val style = MaterialTheme.typography.bodyLarge
  return remember(measurer, style) {
    { label -> measurer.measure(label, style = style, maxLines = 1).size.width.toFloat() }
  }
}

/** Le misure di serie, dalla densita' e dal tipo del tema. */
@Composable
internal fun rememberFluidMenuPanelMetrics(): FluidMenuPanelMetrics {
  val density = LocalDensity.current
  val body = MaterialTheme.typography.bodyLarge
  return remember(density, body) {
    with(density) {
      val line = if (body.lineHeight.isSpecified) body.lineHeight.toDp() else (body.fontSize.toDp() * 1.4f)
      FluidMenuPanelMetrics(
        widthPx = FluidMenuPanelWidth.toPx(),
        rowHeightPx = fluidMenuRowHeight(line).toPx(),
        paddingPx = FluidMenuPanelPadding.toPx(),
        sectionGapPx = FluidMenuSectionGap.toPx(),
        radiusPx = FluidRadius.Group.toPx(),
        gapPx = FluidMenuAnchorGap.toPx(),
        marginPx = FluidMenuEdgeMargin.toPx(),
        edgeMinPx = FluidMenuInflationEdgeMin.toPx(),
        refHeightPx = FluidMenuInflationReferenceHeight.toPx(),
      )
    }
  }
}

/**
 * Dove stanno le voci dentro il pannello: il bordo alto di ogni cella (coordinate del pannello) e
 * l'altezza totale. Le sezioni sono uno spazio in piu' prima della voce che ne apre una (mai prima
 * della prima): una banda, non una cella, quindi il dito che ci si ferma non sceglie niente.
 */
internal class FluidMenuPanelLayout(
  val tops: FloatArray,
  val rowHeightPx: Float,
  val height: Float,
  /** Il bordo alto di ogni banda di sezione, nell'ordine. */
  val sectionBands: FloatArray,
  val sectionGapPx: Float,
)

internal fun fluidMenuPanelLayout(
  sectionStarts: List<Boolean>,
  rowHeightPx: Float,
  paddingPx: Float,
  sectionGapPx: Float,
): FluidMenuPanelLayout {
  val tops = FloatArray(sectionStarts.size)
  val bands = ArrayList<Float>()
  var y = paddingPx
  sectionStarts.forEachIndexed { i, starts ->
    if (i > 0 && starts) {
      bands += y
      y += sectionGapPx
    }
    tops[i] = y
    y += rowHeightPx
  }
  return FluidMenuPanelLayout(
    tops = tops,
    rowHeightPx = rowHeightPx,
    height = y + paddingPx,
    sectionBands = bands.toFloatArray(),
    sectionGapPx = sectionGapPx,
  )
}

/** [fluidMenuPanelLayout] con le misure di [m]. */
internal fun fluidMenuPanelLayout(sectionStarts: List<Boolean>, m: FluidMenuPanelMetrics): FluidMenuPanelLayout =
  fluidMenuPanelLayout(sectionStarts, m.rowHeightPx, m.paddingPx, m.sectionGapPx)

/** La geometria del hit-test per un pannello che sta a [panel] (pixel della radice). */
internal fun fluidMenuGeometryOf(layout: FluidMenuPanelLayout, enabled: List<Boolean>, panel: Rect): FluidMenuGeometry =
  FluidMenuGeometry(
    cells = List(layout.tops.size) { i ->
      Rect(panel.left, panel.top + layout.tops[i], panel.right, panel.top + layout.tops[i] + layout.rowHeightPx)
    },
    enabled = enabled,
    panel = panel,
  )

/** Dove si posa il pannello rispetto all'ancora. */
internal enum class FluidMenuPlacement {
  /** Sopra l'ancora se ci sta, altrimenti sotto (il menu che si trasforma: nasce da un tasto in basso). */
  AboveOrBelow,

  /** SULL'ancora: il pannello la copre e si apre sotto il dito (le azioni della barra, `Expand`). */
  OverAnchor,

  /** Accanto, sotto se c'e' posto: la riga sollevata resta visibile (il menu contestuale). */
  BesideAnchor,
}

/**
 * Il rettangolo del pannello, puro: largo [width] (mai oltre lo spazio fra i margini), alto [height],
 * col centro orizzontale il piu' vicino possibile all'ancora e sempre dentro i margini dell'host.
 */
internal fun solveFluidMenuPanel(
  anchor: Rect,
  host: Size,
  width: Float,
  height: Float,
  gapPx: Float,
  marginPx: Float,
  placement: FluidMenuPlacement,
): Rect {
  val w = width.coerceAtMost((host.width - marginPx * 2f).coerceAtLeast(1f))
  val left = (anchor.center.x - w / 2f).coerceIn(marginPx, (host.width - marginPx - w).coerceAtLeast(marginPx))
  val maxTop = (host.height - marginPx - height).coerceAtLeast(marginPx)
  val top = when (placement) {
    FluidMenuPlacement.AboveOrBelow -> {
      val fitsAbove = anchor.top - gapPx - height >= marginPx
      if (fitsAbove) anchor.top - gapPx - height else (anchor.bottom + gapPx).coerceAtMost(maxTop)
    }

    FluidMenuPlacement.OverAnchor -> {
      // Il bordo da cui il pannello si allontana resta fermo: il tasto non salta nel fotogramma in
      // cui si apre. Verso il basso se ci sta, se no verso l'alto dal bordo basso dell'ancora.
      val fitsDown = anchor.top + height <= host.height - marginPx
      if (fitsDown) anchor.top.coerceAtLeast(marginPx) else (anchor.bottom - height).coerceIn(marginPx, maxTop)
    }

    FluidMenuPlacement.BesideAnchor -> {
      val spaceBelow = host.height - anchor.bottom - gapPx - marginPx
      val spaceAbove = anchor.top - gapPx - marginPx
      val below = height <= spaceBelow || spaceBelow >= spaceAbove
      if (below) (anchor.bottom + gapPx).coerceAtMost(maxTop) else (anchor.top - gapPx - height).coerceAtLeast(marginPx)
    }
  }
  return Rect(left, top, left + w, top + height)
}

/**
 * Il seme da cui cresce un pannello SENZA ancora da cui trasformarsi (il menu contestuale, un pop-up
 * senza origine): il pannello stesso scalato di [scale] attorno a [pivot]. Cresce per geometria — la
 * sagoma, non un layer scalato sul vetro.
 */
internal fun fluidMenuSeedFrame(panel: Rect, pivot: Offset, scale: Float): Rect =
  fluidMenuScaledFrame(panel, pivot, scale)

/**
 * La goccia da cui nasce il menu contestuale (3.0, il vetro liquido): un cerchio di [diameterPx]
 * appeso al bordo della riga che guarda il pannello, sotto il dito ([touchX], o il centro della riga),
 * che entra nella riga di [overlapPx] — esce DALLA riga, non le compare accanto. Il corpo parte da qui,
 * e il corpo del menu ne esce come una goccia ancorata li', sotto il dito.
 *
 * Prima il seme era il pannello stesso a 0,42 attorno al perno: un pannello piccolo che compariva
 * sfumando dal nulla accanto alla riga, e la riga e il menu erano due oggetti.
 */
internal fun fluidMenuDropSeed(
  row: Rect,
  panel: Rect,
  touchX: Float?,
  diameterPx: Float,
  overlapPx: Float,
): Rect {
  val d = diameterPx.coerceAtMost(row.minDimension.coerceAtLeast(1f))
  val r = d / 2f
  val minX = panel.left + r
  val maxX = (panel.right - r).coerceAtLeast(minX)
  val x = (touchX ?: row.center.x).coerceIn(minX, maxX)
  val below = panel.center.y >= row.center.y
  val top = if (below) row.bottom - overlapPx else row.top + overlapPx - d
  return Rect(x - r, top, x + r, top + d)
}

/**
 * Quanto della goccia del contestuale si vede al progresso [p]: niente sulla riga (p = 0), tutta da
 * [FluidMenuDropEmerge] in poi. E' la goccia che ESCE dalla riga, e al ritorno ci rientra.
 */
internal fun fluidMenuDropPresence(p: Float): Float {
  val x = (p / FluidMenuDropEmerge).coerceIn(0f, 1f)
  return x * x * (3f - 2f * x)
}

/** Entro questo progresso la goccia del contestuale e' tutta fuori dalla riga. */
internal const val FluidMenuDropEmerge = 0.15f

/** [panel] scalato di [scale] attorno a [pivot]. */
private fun fluidMenuScaledFrame(panel: Rect, pivot: Offset, scale: Float): Rect =
  Rect(
    pivot.x + (panel.left - pivot.x) * scale,
    pivot.y + (panel.top - pivot.y) * scale,
    pivot.x + (panel.right - pivot.x) * scale,
    pivot.y + (panel.bottom - pivot.y) * scale,
  )

/** Il punto del pannello piu' vicino all'ancora: il perno della crescita del menu contestuale. */
internal fun fluidMenuPivot(panel: Rect, anchor: Rect): Offset = Offset(
  anchor.center.x.coerceIn(panel.left, panel.right),
  if (panel.center.y >= anchor.center.y) panel.top else panel.bottom,
)

internal val FluidMenuPanelWidth = 252.dp

/** Il tetto della larghezza di un menu con voci lunghe (3.0.1): oltre, la riga va in puntini. */
internal val FluidMenuPanelMaxWidth = 340.dp

/** Le misure orizzontali di una riga di menu, le stesse di `FluidMenuPanelRow`. */
internal val FluidMenuRowSidePadding = 16.dp
internal val FluidMenuRowGap = 12.dp
internal val FluidMenuRowGlyph = 20.dp
internal val FluidMenuPanelPadding = 6.dp
internal val FluidMenuSectionGap = 8.dp
internal val FluidMenuMinRowHeight = 48.dp
internal val FluidMenuAnchorGap = 12.dp
internal val FluidMenuEdgeMargin = 16.dp

/** Sotto questa distanza dai bordi laterali il gonfiore non porta mai il pannello (vedi [fluidMenuInflation]). */
internal val FluidMenuInflationEdgeMin = 6.dp

/** L'altezza a cui il gonfiore vale il guadagno esatto: quella della finestra dei modali. */
internal val FluidMenuInflationReferenceHeight = 240.dp

/** Di quanto e' piccolo il seme del menu contestuale: quello del pop-up di sempre. */
internal const val FluidMenuSeedScale = 0.42f

/** La goccia da cui nasce il menu contestuale (vedi [fluidMenuDropSeed]). */
internal val FluidMenuDropSeedDiameter = 28.dp
internal val FluidMenuDropSeedOverlap = 4.dp

// --- L'ancora ------------------------------------------------------------------------------

/**
 * L'ancora di un menu: il tasto (o la lente di una barra) che DIVENTA il pannello.
 *
 * La tiene chi disegna il tasto, ricordata: il gesto che apre ci scrive dentro la pressione del
 * momento, l'host ci legge dove partire. Due cose in piu' rispetto a un rettangolo:
 *
 * - [hidden], letto SOLO in disegno (`drawWithContent { if (!hidden) drawContent() }`): mentre il
 *   menu e' in scena il tasto non disegna niente, e al suo posto sta la superficie dell'host con la
 *   stessa sagoma. Un cambio di proprietario, non una dissolvenza; e niente `graphicsLayer` alpha,
 *   che e' un layer sopra un vetro.
 * - [handOff], il ponte del dito: `Modifier.fluidMenuPointerRelay(anchor.handOff)` in testa al tasto.
 *
 * [face] e' la faccia del tasto (icona, etichetta) che l'host porta lungo la prima parte del viaggio:
 * deve essere inerte — icone e testo, nessuno stato catturato — perche' vive in un oggetto, non in una
 * composizione.
 */
@Stable
internal class FluidMenuAnchor {
  /** Dove sta il tasto a riposo, in pixel della radice: lo scrive il suo layout. */
  var restBounds: Rect = Rect.Zero

  /** Dove stava al click lungo, gonfiato dal dito: vedi [fluidPressedBounds]. */
  var pressedBounds: Rect = Rect.Zero

  var face: (@Composable () -> Unit)? = null

  /** Il materiale del tasto: la sagoma parte da qui e arriva a quello del pannello. */
  var tintFrom: GlassTint? = null
  var opticsFrom: GlassOptics? = null

  val handOff = FluidMenuHandOff()

  var hidden by mutableStateOf(false)
}

// --- Il viaggio ------------------------------------------------------------------------------

/**
 * Il viaggio di UN menu: un progresso geometrico per i due versi (0 = l'ancora, 1 = il pannello, puo'
 * superare 1 con la molla d'apertura) che guida la finestra di Fluid-physics con `driveExternally`.
 *
 * - **Apertura**: [FluidMotion.menuOpen], sottosmorzata; l'oltrepasso e' un gonfiore uniforme attorno
 *   al centro del pannello ([inflation]), mai il lerp oltre l'1.
 * - **Partenza viva**: col tasto premuto la sagoma parte dal rettangolo GONFIATO dal dito e, mentre il
 *   viaggio corre, la pressione si scioglie verso il riposo ([anchorRelax], criticamente smorzata).
 * - **Chiusura e inversione**: dal valore corrente, con la sua velocita' ([retarget] la legge PRIMA che
 *   la molla vecchia venga cancellata: cancellata, un `Animatable` la azzera). Un back a meta'
 *   apertura torna indietro da dove si trova, non dal pannello pieno.
 *
 * [target] e' uno stato (la chiave dell'effetto dell'host); [progress] e [anchorRelax] si leggono
 * solo in disegno.
 */
@Stable
internal class FluidMenuJourney {

  val progress = Animatable(0f, ProgressThreshold)

  /** 0 = il fotogramma di partenza e' il tasto premuto, 1 = a riposo. Nuovo a ogni apertura. */
  var anchorRelax = Animatable(1f, ProgressThreshold)
    private set

  var target by mutableFloatStateOf(0f)
    private set

  private var seedVelocity = 0f

  /** L'ancora da cui si parte, o null per un pannello che cresce da un seme ([seed]). */
  var anchor: FluidMenuAnchor? = null
    private set

  /** Dove sta l'host nella radice: le ancore parlano radice, la superficie parla host. */
  var hostOrigin: Offset = Offset.Zero
    private set

  /** Il pannello, in coordinate dell'host. */
  var to: Rect = Rect.Zero
    private set

  var toRadius: Float = 0f
    private set

  /** Il seme senza ancora, in coordinate dell'host. */
  var seed: Rect? = null
    private set

  var inflation: FluidMenuInflation = FluidMenuInflation.None
    private set

  /** Il verso di adesso: le finestre di opacita' del contenuto dipendono da questo. */
  val closing: Boolean get() = target == 0f

  /** Il viaggio di ritorno e' finito: l'host puo' rimettere il tasto e smontare. */
  val settledClosed: Boolean get() = target == 0f && !progress.isRunning && progress.value <= ProgressThreshold

  private val startFrameBlock: () -> Rect = { startFrame() }
  private val progressBlock: () -> Float = { progress.value }

  /**
   * Il fotogramma di partenza di QUESTO istante, in coordinate dell'host: il tasto premuto che torna
   * a riposo mentre il viaggio corre. Fase di disegno.
   */
  fun startFrame(): Rect {
    val a = anchor ?: return seed ?: to
    val r = lerp(a.pressedBounds, a.restBounds, anchorRelax.value.coerceIn(0f, 1f))
    return r.translate(-hostOrigin.x, -hostOrigin.y)
  }

  /**
   * Prepara un'apertura: l'ancora (o il seme), il pannello e il gonfiore. Non tocca [progress]: un
   * menu riaperto mentre si chiudeva riparte da dove si trova.
   *
   * @param freshStart un'apertura nuova (non la riapertura di un menu che si stava chiudendo, ne' una
   *   seconda chiamata per la stessa apertura): la pressione dell'ancora riparte dal tasto premuto.
   */
  fun prepare(
    anchor: FluidMenuAnchor?,
    hostOrigin: Offset,
    panel: Rect,
    radius: Float,
    inflation: FluidMenuInflation,
    seed: Rect? = null,
    freshStart: Boolean = true,
  ) {
    if (anchor != null && (freshStart || this.anchor !== anchor)) {
      // Una partenza nuova: la pressione si scioglie da qui. Un `Animatable` nuovo e non uno snapTo,
      // che e' sospeso: la partenza deve essere giusta gia' al primo fotogramma, che arriva prima di
      // qualunque coroutine.
      anchorRelax = Animatable(if (anchor.pressedBounds == anchor.restBounds) 1f else 0f, ProgressThreshold)
    }
    this.anchor = anchor
    this.hostOrigin = hostOrigin
    this.to = panel
    this.toRadius = radius
    this.inflation = inflation
    this.seed = seed
  }

  /** Mette la finestra di [physics] al servizio di questo viaggio. Gratis se gli estremi non cambiano. */
  fun install(physics: FluidPhysicsState) {
    val a = anchor
    // Il `from` e' fisso per un'apertura (il tasto premuto, o il seme): e' il segnaposto con cui
    // `driveExternally` riconosce la stessa corsa. La partenza vera, viva, la da' [startFrame].
    val start = if (a != null) a.pressedBounds.translate(-hostOrigin.x, -hostOrigin.y) else seed ?: to
    // Tondo per costruzione: il tasto e' una capsula, la goccia del contestuale un cerchio.
    val from = FluidForm.Slab(
      frame = start,
      cornerRadii = FluidCornerRadii.all(start.minDimension / 2f),
    )
    physics.driveExternally(
      from = from,
      to = FluidForm.Slab(to, FluidCornerRadii.all(toRadius)),
      progress = progressBlock,
      overshootInflationX = inflation.x,
      overshootInflationY = inflation.y,
      startFrame = if (a != null) startFrameBlock else null,
    )
  }

  /**
   * Cambia verso. Chiamata nel momento in cui si decide (il gesto, il congedo, o la composizione che
   * se ne accorge), MAI dopo: la velocita' si legge qui, finche' la molla vecchia e' viva.
   */
  fun retarget(t: Float) {
    if (target == t) return
    seedVelocity = progress.velocity
    target = t
  }

  /** Corre verso [target]. Sospende finche' non arriva; cancellabile (un [retarget] la sostituisce). */
  suspend fun run(reducedMotion: Boolean) {
    val t = target
    val v = seedVelocity
    seedVelocity = 0f
    if (reducedMotion) {
      anchorRelax.snapTo(1f)
      progress.snapTo(t)
      return
    }
    if (t >= 1f) {
      coroutineScope {
        val relax = anchorRelax
        if (relax.value < 1f) launch { relax.animateTo(1f, FluidMotion.snappy()) }
        // La goccia (3.0): l'andata in ~0,28 s (3.0.4; era 0,42), poco sottosmorzata, e il ritorno in ~0,50 s.
        progress.animateTo(1f, FluidMotion.dropOpen(), initialVelocity = v)
      }
    } else {
      progress.animateTo(0f, FluidMotion.dropClose(), initialVelocity = v)
    }
  }

  /** Dimentica l'ancora a viaggio finito: un menu chiuso non tiene in mano un tasto che non c'e'. */
  fun clearAnchor() {
    anchor = null
    seed = null
  }

  private companion object {
    const val ProgressThreshold = 0.001f
  }
}
