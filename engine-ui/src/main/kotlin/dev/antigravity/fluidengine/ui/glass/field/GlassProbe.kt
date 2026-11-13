/*
 * LA SONDA DELLO SFONDO (piano «iOS in Android», 3.9 e F2.1; cancello C5 chiuso in S1-bis).
 *
 * Il vetro deve sapere che cosa ha dietro (luminanza, varianza, colore) per scegliere lato, inchiostro e ombra, senza
 * chiedere catture in più e senza fermare il filo principale. Questo file misura; chi usa le misure per cambiare il
 * materiale è F2.2 (ricetta) e F2.3 (giro e inchiostro).
 *
 * **Come legge.** Non con `toImageBitmap()`: sul Tab S9 ferma il filo principale circa 5 ms a lettura (4 sonde ogni
 * 100 ms: 10% di fotogrammi persi), e chiamata da un altro filo fa cadere HWUI (S1). La via è quella provata in S1-bis
 * con il prototipo della pagina «Misure» (`sample/…/measure/ProbeReader.kt`), qui portata nell'engine:
 *
 * - **un lettore per finestra** ([GlassProbeWindowReader]): un `ImageReader` RGBA_8888 con una casella per sonda, un
 *   `HardwareRenderer` che rende nella sua `Surface`, un `HandlerThread` per i conti. Una richiesta legge tutte le
 *   sonde che l'hanno chiesta nello stesso fotogramma, e costa come una sola (S1-bis: «lettore unico»);
 * - **la miniatura si registra sul filo principale** (un `GraphicsLayer` piccolo che disegna il `Backdrop` scalato), e
 *   solo quando cambia la geometria: tiene le sorgenti per riferimento, quindi dopo una revisione basta rileggerla. La
 *   radice del lettore si registra di nuovo a ogni richiesta (0,1 ms), come nel prototipo;
 * - **la richiesta parte dopo il disegno della finestra**: un messaggio accodato dalla callback del fotogramma. A quel
 *   punto la finestra ha già sincronizzato i suoi nodi, quindi il lettore non può prendersi un ridisegno che spettava
 *   a lei, e l'attesa di `syncAndDraw` (2–2,7 ms) cade nel tempo morto del principale (S1-bis, fase `post`);
 * - **i conti sul filo di lavoro**, i numeri consegnati sul principale, dove la sonda scrive lo stato fuori dal disegno.
 *
 * **La miniatura è a 64 px e i conti a 16×16** ([GlassProbeReadSide], [GlassProbeStatsSide]). La sorgente vera della
 * pagina (`glassBackdropSource`) è composta fuori schermo: disegnarla rimpicciolita campiona una texture col filtro
 * bilineare, cioè **a punti**. Una miniatura registrata direttamente a 16×16 (il prototipo) fa tremare la luminanza
 * media di 0,027–0,075 quando il testo scorre di meno di un pixel della miniatura, e di 0,06–0,15 a 8×8: fino a quasi
 * la distanza fra le due soglie del giro (0,17). Registrata a 64 e ridotta a blocchi 4×4 sul filo di lavoro trema di
 * 0,005–0,011 sul testo (16×16) e non costa di più (S1-bis: una miniatura 64×64 ha lo stesso costo di una 16×16). I numeri sono in
 * `artifacts/liquid-glass/bench/f2-1/probe_stats.md`, scritti da `tools/glassbench/probe_stats.py`, che calcola la
 * miniatura nello stesso modo e con la stessa formula ([glassProbeStats]).
 *
 * **Quando legge** lo decide [GlassProbeScheduler]: una revisione di una sorgente, uno spostamento, o (per le sorgenti
 * che non avvisano) una cadenza fissa e bassa; al più ogni 120 ms, mai durante un movimento veloce. Gli avvisi delle
 * sorgenti arrivano durante il loro disegno: lasciano un segnale (un messaggio sul principale, uno solo in coda) e
 * basta. **Come si filtra** lo decide [GlassAdaptiveFilter].
 *
 * **Dove non c'è.** La sonda esiste solo dove esiste il materiale nuovo: Android 13 e oltre ([glassProbeAvailable]),
 * e solo se chi la monta è il ramo `Liquid` di una superficie. Sotto `Classic` e sotto Android 13 nessuno la chiama, e
 * niente cambia. In F2.1 non la chiama nessun componente: l'innesto è di F2.2 e F2.5.
 *
 * **Scostamento dal testo di 3.9**: il piano dice «una coroutine del nodo, partita in `onAttach`». Qui il consumo dei
 * segnali è un messaggio sul `Handler` del principale, non una coroutine: stessa forma (segnale a un posto, consumato
 * fuori dal disegno, decisione dello schedulatore), ma gli istanti sono quelli dell'orologio di sistema e non quelli
 * dell'orologio dei fotogrammi di Compose, che nei test strumentati è finto e fermerebbe le attese.
 */
package dev.antigravity.fluidengine.ui.glass.field

import android.graphics.HardwareRenderer
import android.graphics.PixelFormat
import android.graphics.RenderNode
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.View
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ObserverModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.observeReads
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import dev.antigravity.fluidengine.ui.fluid.FluidGlassQuality
import dev.antigravity.fluidengine.ui.fluid.LocalFluidGlassQuality
import dev.antigravity.fluidengine.ui.glass.backdrop.Backdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerBackdrop
import dev.antigravity.fluidengine.ui.glass.backdrop.backdrops.LayerRevisionListener
import dev.antigravity.fluidengine.ui.glass.backdrop.isRuntimeShaderSupported
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

// ------------------------------------------------------------------------------------------------------------
// I numeri
// ------------------------------------------------------------------------------------------------------------

/**
 * Il lato della miniatura che il lettore rende, in pixel. Quattro volte [GlassProbeStatsSide]: la riduzione a blocchi
 * sul filo di lavoro fa della lettura a punti una media d'area (vedi l'intestazione). Cambiarlo cambia la prova del
 * banco: `tools/glassbench/probe_stats.py` ha la sua copia (`READ_SIDE`).
 */
internal const val GlassProbeReadSide: Int = 64

/**
 * Il lato della miniatura su cui si fanno i conti. 16 e non 8: a 8×8 un menu alto sopra testo piccolo ha una
 * deviazione standard di 0,016–0,033, che il fondo piatto con una sfumatura (0,006) avvicina troppo; a 16×16 è
 * 0,028–0,063 (`probe_stats.md`).
 */
internal const val GlassProbeStatsSide: Int = 16

/**
 * Le caselle di un lettore: al più tante sonde per finestra. Fino a F2.2 erano 8; da F2.3 ogni lastra radice ha la sua
 * sonda, e le 8 finivano già sulla scheda «Materiale» della galleria. 64 è circa il doppio della pagina più carica
 * della galleria (il giro di `GlassProbeQuietTest`, F2.3: «Icone» 33 sonde radice insieme, «Robustezza» 32, «Tutto in
 * vetro» 26, la scheda «Materiale» 15). Le caselle stanno in una griglia di [GlassProbeSlotColumns] colonne: l'immagine
 * del lettore è 512 × 512 px, 1 MiB per immagine RGBA, al più tre, cioè 3 MiB per finestra (con le 8 di prima in fila
 * erano 384 KiB), e il lato più lungo resta 512 px, molto sotto la texture minima di GLES 3.0 (2048; in fila sarebbero
 * stati 4096). Una richiesta disegna e conta **solo** le caselle chieste: il tempo di una lettura non cresce col tetto. Oltre il tetto vale la regola di [glassProbePriority]: entrano prima i vetri più grandi e più vicini al centro
 * della finestra; chi non entra non misura e tiene il lato del tema; chi viene spinto fuori da uno più importante tiene
 * l'ultimo lato misurato (nessuno scatto).
 */
internal const val GlassProbeMaxSlots: Int = 64

/** Le colonne della griglia delle caselle nell'immagine del lettore (vedi [GlassProbeMaxSlots]). */
internal const val GlassProbeSlotColumns: Int = 8

/**
 * Dove sta la casella [slot] nell'immagine del lettore, in pixel: il bordo sinistro e quello alto, per caselle di lato
 * [side]. Pure, e le stesse per chi posa le miniature e per chi le legge: se i due conti divergessero, ogni sonda
 * leggerebbe la casella di un'altra, e solo sul dispositivo (in Robolectric l'immagine non arriva).
 */
internal fun glassProbeSlotLeft(slot: Int, side: Int): Int = slot % GlassProbeSlotColumns * side

/** Vedi [glassProbeSlotLeft]. */
internal fun glassProbeSlotTop(slot: Int, side: Int): Int = slot / GlassProbeSlotColumns * side

/**
 * L'importanza di una sonda per le caselle del lettore, 0..1: l'area del vetro rispetto a quella della finestra, pesata
 * da quanto sta vicino al centro (al centro conta intera, in un angolo metà). Pura. Un vetro grande in mezzo allo
 * schermo cambia l'aspetto della pagina più di un tasto in un angolo; a pari area vince il più centrale.
 */
internal fun glassProbePriority(
  width: Float,
  height: Float,
  centerX: Float,
  centerY: Float,
  windowWidth: Float,
  windowHeight: Float,
): Float {
  if (!(windowWidth > 0f) || !(windowHeight > 0f)) return 0f
  val area = (max(width, 0f) * max(height, 0f) / (windowWidth * windowHeight)).coerceIn(0f, 1f)
  val dx = (centerX - windowWidth / 2f) / (windowWidth / 2f)
  val dy = (centerY - windowHeight / 2f) / (windowHeight / 2f)
  val distance = (sqrt(dx * dx + dy * dy) / sqrt(2f)).coerceIn(0f, 1f)
  return area * (1f - 0.5f * distance)
}

/** Una lettura che non torna entro questo tempo butta il lettore: se ne fa uno nuovo alla prossima richiesta. */
internal const val GlassProbeReadTimeoutMillis: Long = 1_000L

/**
 * Sotto questo livello di `FluidGlassQuality` la sonda tace: la pagina scorre veloce (il livello scende sopra 24 px per
 * fotogramma) e ciò che si leggerebbe non c'è più al fotogramma dopo. Ipotesi da tarare in S3.
 */
internal const val GlassProbeQualityFloor: Float = 0.9f

/**
 * Una lettura in cui meno di metà dei pixel è coperta (alfa medio sotto questa soglia) non dice niente dello sfondo: è
 * una sorgente che non ha ancora disegnato, o un buco. Non si consegna.
 */
internal const val GlassProbeMinCoverage: Float = 0.5f

/**
 * La trama ([GlassBackdropStats.texture]) è 0 sotto questa radice dell'energia dei gradienti e 1 sopra
 * [GlassProbeTextureBusy]. Da `probe_stats.md` (riduzione `engine`, 16×16): il fondo piatto sta a 0,000–0,002 (anche
 * con una sfumatura), il testo a 0,090–0,233 (0,035 il caso peggiore con la media d'area esatta: testo da 16 dp sotto
 * un menu di 900 px), la foto sintetica a 0,07–0,13. Da tarare in S3 con l'ombra sul testo (F2.2).
 */
internal const val GlassProbeTextureFlat: Float = 0.01f

/** Vedi [GlassProbeTextureFlat]. */
internal const val GlassProbeTextureBusy: Float = 0.06f

/** Mentre la sonda tace per la qualità bassa, ogni quanto ricontrolla (il ritorno della qualità a volte non avvisa). */
private const val GlassProbeSuspendedRecheckMillis: Long = 250L

private const val GlassProbeLogTag = "FluidGlass"

// ------------------------------------------------------------------------------------------------------------
// Le statistiche
// ------------------------------------------------------------------------------------------------------------

/**
 * Ciò che la sonda sa dello sfondo sotto un vetro, da una miniatura 16×16. Unità di luminanza 0..1, sui valori sRGB a
 * 8 bit (Y = 0,2126 R + 0,7152 G + 0,0722 B, come il prototipo di S1-bis e come il banco).
 *
 * @property luminance la luminanza media.
 * @property variance la varianza della luminanza fra i pixel della miniatura (della popolazione).
 * @property gradientEnergy la media dei quadrati delle differenze di luminanza fra pixel vicini, orizzontali e
 *   verticali insieme. Separa il testo dal piatto meglio della varianza, e non la inganna una sfumatura larga
 *   (`probe_stats.md`: a 16×16, testo peggiore 0,090 contro piatto peggiore 0,001 in radice; con la varianza 0,063
 *   contro 0,006).
 * @property red il rosso medio (0..1), premoltiplicato come arriva dal lettore. Così [green] e [blue].
 * @property coverage l'alfa medio: 1 = tutta la miniatura è coperta da sorgenti opache.
 */
@Immutable
internal data class GlassBackdropStats(
  val luminance: Float,
  val variance: Float,
  val gradientEnergy: Float,
  val red: Float,
  val green: Float,
  val blue: Float,
  val coverage: Float,
) {
  /** La deviazione standard della luminanza. */
  val deviation: Float get() = sqrt(max(variance, 0f))

  /** La radice dell'energia dei gradienti, in unità di luminanza. */
  val gradient: Float get() = sqrt(max(gradientEnergy, 0f))

  /** Il colore medio (per il colore dell'ombra, F2.2). */
  val color: Color get() = Color(red.coerceIn(0f, 1f), green.coerceIn(0f, 1f), blue.coerceIn(0f, 1f))

  /** Quanto lo sfondo è fitto: 0 piatto, 1 testo o immagine fitta. Vedi [GlassProbeTextureFlat]. */
  val texture: Float get() = glassBackdropTexture(gradient)
}

/** La trama da [gradient] (radice dell'energia dei gradienti): una rampa morbida fra le due soglie. Pura. */
internal fun glassBackdropTexture(gradient: Float): Float {
  if (!gradient.isFinite()) return 0f
  val t = ((gradient - GlassProbeTextureFlat) / (GlassProbeTextureBusy - GlassProbeTextureFlat)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

/**
 * Riduce una miniatura letta (RGBA a 8 bit, lato [readSide]) a [statsSide]×[statsSide] con una media a blocchi e ne
 * calcola le statistiche. Riusa i suoi array: uno per filo di lavoro, nessuna allocazione per riga.
 *
 * La stessa formula è in `tools/glassbench/probe_stats.py` (`box_reduce` e `stats`): il test la inchioda su un disegno
 * fisso con gli stessi numeri del banco (`--selftest`).
 */
internal class GlassProbeStatsReducer(
  val readSide: Int = GlassProbeReadSide,
  val statsSide: Int = GlassProbeStatsSide,
) {
  init {
    require(statsSide >= 2 && readSide >= statsSide && readSide % statsSide == 0) {
      "il lato letto ($readSide) deve essere un multiplo del lato dei conti ($statsSide)"
    }
  }

  private val factor = readSide / statsSide
  private val cells = FloatArray(statsSide * statsSide * 4)
  private val lumas = FloatArray(statsSide * statsSide)

  /** Comincia una miniatura nuova. */
  fun begin() {
    cells.fill(0f)
  }

  /** La riga [y] (0 until [readSide]): [readSide] pixel RGBA da [offset] in [bytes], [pixelStride] byte l'uno. */
  fun addRow(y: Int, bytes: ByteArray, offset: Int, pixelStride: Int) {
    val rowBase = (y / factor) * statsSide
    var at = offset
    for (x in 0 until readSide) {
      val cell = (rowBase + x / factor) * 4
      cells[cell] += (bytes[at].toInt() and 0xFF).toFloat()
      cells[cell + 1] += (bytes[at + 1].toInt() and 0xFF).toFloat()
      cells[cell + 2] += (bytes[at + 2].toInt() and 0xFF).toFloat()
      cells[cell + 3] += (bytes[at + 3].toInt() and 0xFF).toFloat()
      at += pixelStride
    }
  }

  /** Le statistiche della miniatura cominciata con [begin]. */
  fun finish(): GlassBackdropStats {
    val count = statsSide * statsSide
    val perCell = (factor * factor) * 255f
    var sumY = 0.0
    var sumR = 0.0
    var sumG = 0.0
    var sumB = 0.0
    var sumA = 0.0
    for (i in 0 until count) {
      val r = cells[i * 4] / perCell
      val g = cells[i * 4 + 1] / perCell
      val b = cells[i * 4 + 2] / perCell
      val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
      lumas[i] = y
      sumY += y
      sumR += r
      sumG += g
      sumB += b
      sumA += cells[i * 4 + 3] / perCell
    }
    val mean = (sumY / count).toFloat()
    var squares = 0.0
    for (i in 0 until count) {
      val d = lumas[i] - mean
      squares += d * d
    }
    var energy = 0.0
    for (row in 0 until statsSide) {
      for (col in 0 until statsSide) {
        val here = lumas[row * statsSide + col]
        if (col + 1 < statsSide) {
          val d = lumas[row * statsSide + col + 1] - here
          energy += d * d
        }
        if (row + 1 < statsSide) {
          val d = lumas[(row + 1) * statsSide + col] - here
          energy += d * d
        }
      }
    }
    val pairs = 2 * statsSide * (statsSide - 1)
    return GlassBackdropStats(
      luminance = mean,
      variance = (squares / count).toFloat(),
      gradientEnergy = (energy / pairs).toFloat(),
      red = (sumR / count).toFloat(),
      green = (sumG / count).toFloat(),
      blue = (sumB / count).toFloat(),
      coverage = (sumA / count).toFloat(),
    )
  }
}

/**
 * Le statistiche di una miniatura RGBA a 8 bit, fitta (lato [readSide], quattro byte per pixel, nessun margine di
 * riga). Pura: è la stessa riduzione che il lettore fa riga per riga, per i test e per il confronto col banco.
 */
internal fun glassProbeStats(
  rgba: ByteArray,
  readSide: Int = GlassProbeReadSide,
  statsSide: Int = GlassProbeStatsSide,
): GlassBackdropStats {
  require(rgba.size >= readSide * readSide * 4) { "servono ${readSide * readSide * 4} byte, ne sono arrivati ${rgba.size}" }
  val reducer = GlassProbeStatsReducer(readSide, statsSide)
  reducer.begin()
  for (y in 0 until readSide) reducer.addRow(y, rgba, y * readSide * 4, 4)
  return reducer.finish()
}

// ------------------------------------------------------------------------------------------------------------
// Lo stato che la sonda pubblica
// ------------------------------------------------------------------------------------------------------------

/**
 * Ciò che una sonda pubblica, come stato di snapshot scritto sul filo principale **fuori dal disegno**. È ciò che F2.2
 * legge per la ricetta e F2.3 per il giro: [target] (lato filtrato e bersagli) e [stats] (l'ultima lettura grezza).
 *
 * @param initialDark il lato del tema (`GlassDefaults.isDarkSurface()`): resta la sorgente iniziale e il ripiego
 *   finché non arriva una lettura (3.9).
 */
@Stable
internal class GlassProbeState(
  initialDark: Boolean,
  internal val filter: GlassAdaptiveFilter = GlassAdaptiveFilter(initialDark),
) {
  /** L'ultima lettura consegnata, o null se non ce n'è ancora una. */
  var stats: GlassBackdropStats? by mutableStateOf(null)
    private set

  /** Il lato filtrato e i bersagli. Prima della prima lettura: il lato del tema, `measured` falso. */
  var target: GlassAdaptiveTarget by mutableStateOf(GlassAdaptiveTarget.unmeasured(initialDark))
    private set

  /** Quante letture sono state consegnate. Non è stato di snapshot: per i test e la diagnostica. */
  internal var readings: Int = 0
    private set

  /** Una lettura nuova a [nowMillis]. Dal filo principale, fuori dal disegno. */
  internal fun publish(nowMillis: Long, stats: GlassBackdropStats) {
    this.stats = stats
    filter.sample(nowMillis, stats.luminance)
    target = filter.target(nowMillis, stats)
    readings++
  }

  /** Nessuna lettura, ma il filtro può aver raggiunto una sua scadenza. Dice se il lato è cambiato. Dal principale. */
  internal fun advance(nowMillis: Long): Boolean {
    if (!filter.advance(nowMillis)) return false
    target = filter.target(nowMillis, stats)
    return true
  }

  /** Quando richiamare [advance] se non arriva niente. */
  internal fun nextDeadlineMillis(): Long = filter.nextDeadlineMillis()
}

/** Uno stato di sonda che vive quanto la composizione. Per F2.2: il ramo `Liquid` della superficie radice. */
@Composable
internal fun rememberGlassProbeState(initialDark: Boolean): GlassProbeState = remember { GlassProbeState(initialDark) }

// ------------------------------------------------------------------------------------------------------------
// Il modificatore e il nodo
// ------------------------------------------------------------------------------------------------------------

/** Se la sonda può esistere qui: Android 13 e oltre (anche quello simulato in debug), come il materiale nuovo. */
internal fun glassProbeAvailable(): Boolean = isRuntimeShaderSupported()

/**
 * Mette una sonda su [backdrop] (lo stesso `Backdrop` che la superficie campiona) e pubblica in [state] ciò che vede.
 * Va accanto a `drawBackdrop` nel ramo `Liquid` di una superficie **radice**: una superficie annidata in un pannello o
 * iscritta a un contenitore prende lato e inchiostro da chi la ospita (3.9). Non tocca la firma di `drawBackdrop` né la
 * sua cattura. Sotto Android 13, o con [enabled] falso, non aggiunge niente.
 *
 * [region] (F2.3b): la parte del nodo sotto cui leggere, in pixel del nodo, quando il vetro non è grande quanto il suo
 * nodo (un menu di Fluid-physics si disegna in un palco grande quanto la finestra, e la sua sagoma è il rettangolo del
 * piano). Letta col nodo che osserva: quando cambia, la sonda lo sa come uno spostamento. Null = tutto il nodo; un
 * rettangolo vuoto = niente da leggere adesso.
 */
internal fun Modifier.glassProbe(
  backdrop: Backdrop,
  state: GlassProbeState,
  enabled: Boolean = true,
  region: (() -> Rect?)? = null,
): Modifier =
  if (!enabled || !glassProbeAvailable()) this else this then GlassProbeElement(backdrop, state, null, region)

/**
 * Chi vuole sapere di ogni consegna, oltre allo stato (la diagnostica): l'ultima lettura, il bersaglio, e se è una
 * lettura nuova (`true`) o un giro che il filtro ha fatto alla sua scadenza senza letture nuove (`false`).
 */
internal typealias GlassProbeListener = (GlassBackdropStats, GlassAdaptiveTarget, Boolean) -> Unit

private class GlassProbeElement(
  val backdrop: Backdrop,
  val state: GlassProbeState?,
  val onReading: GlassProbeListener?,
  val region: (() -> Rect?)? = null,
) : ModifierNodeElement<GlassProbeNode>() {

  override fun create(): GlassProbeNode =
    GlassProbeNode(backdrop, state ?: GlassProbeState(initialDark = false), onReading).also { it.region = region }

  override fun update(node: GlassProbeNode) {
    node.update(backdrop, state, onReading, region)
  }

  override fun InspectorInfo.inspectableProperties() {
    name = "glassProbe"
    properties["backdrop"] = backdrop
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GlassProbeElement) return false
    return backdrop == other.backdrop && state === other.state && onReading === other.onReading &&
      region === other.region
  }

  override fun hashCode(): Int {
    var result = backdrop.hashCode()
    result = 31 * result + (state?.hashCode() ?: 0)
    result = 31 * result + (onReading?.hashCode() ?: 0)
    result = 31 * result + (region?.hashCode() ?: 0)
    return result
  }
}

/**
 * La sonda di un vetro. Vedi l'intestazione del file per il flusso; qui le regole del nodo:
 *
 * - **gli avvisi non lavorano**: l'avviso di revisione di una sorgente (che arriva nel suo disegno), il
 *   riposizionamento e il cambio delle letture osservate lasciano un segnale, cioè accodano **un** messaggio sul
 *   principale ([signal]); il messaggio ([pump]) consuma i segnali, chiede a [GlassProbeScheduler] e fa partire la
 *   lettura;
 * - **la miniatura si registra solo se è cambiata la geometria** (taglia e posizione rispetto a ogni sorgente), e
 *   sempre sul principale, subito prima della richiesta al lettore ([prepareThumbnail]);
 * - **lo stato si scrive sul principale, fuori dal disegno**, quando i numeri tornano ([onProbeResult]).
 */
internal class GlassProbeNode(
  private var backdrop: Backdrop,
  private var state: GlassProbeState,
  private var onReading: GlassProbeListener?,
) : Modifier.Node(),
  GlobalPositionAwareModifierNode,
  CompositionLocalConsumerModifierNode,
  ObserverModifierNode,
  GlassProbeClient {

  override val shouldAutoInvalidate: Boolean = false

  private val handler = Handler(Looper.getMainLooper())
  private var scheduler: GlassProbeScheduler? = null
  private var view: View? = null
  private var quality: FluidGlassQuality? = null
  private var coordinates: LayoutCoordinates? = null
  private var sources: List<LayerBackdrop> = emptyList()
  private var sourcesUnannounced = true

  /** La parte del nodo sotto cui leggere (F2.3b): vedi [glassProbe]. */
  internal var region: (() -> Rect?)? = null

  private var reader: GlassProbeWindowReader? = null
  private var slot = -1
  private var unavailable = false

  override var thumbnailLayer: GraphicsLayer? = null
    private set
  override var priority: Float = 0f
    private set
  private var thumbnailDirty = true
  private var recordedGeometry: FloatArray? = null
  private var lastGeometry: FloatArray? = null

  private var pumpPosted = false
  private var revisionSeen = false
  private var timerAt = Long.MAX_VALUE

  private val revisionListener = LayerRevisionListener { _, _ ->
    revisionSeen = true
    FluidGlassProbeDiagnostics.onWake(GlassProbeWake.Revision)
    signal()
  }

  private val pumpMessage = Runnable {
    pumpPosted = false
    pump()
  }

  private val timerMessage = Runnable {
    timerAt = Long.MAX_VALUE
    FluidGlassProbeDiagnostics.onWake(GlassProbeWake.Timer)
    pump()
  }

  /** Dall'elemento: un `Backdrop` diverso è un'altra sonda; uno stato nullo (la diagnostica) tiene il proprio. */
  fun update(
    backdrop: Backdrop,
    state: GlassProbeState?,
    onReading: GlassProbeListener?,
    region: (() -> Rect?)? = null,
  ) {
    this.onReading = onReading
    if (state != null) this.state = state
    if (region !== this.region) {
      this.region = region
      recordedGeometry = null
      if (isAttached) signal()
    }
    if (backdrop != this.backdrop) {
      unsubscribe()
      this.backdrop = backdrop
      recordedGeometry = null
      lastGeometry = null
      if (isAttached) {
        subscribe()
        signal()
      }
    }
  }

  override fun onAttach() {
    view = currentValueOf(LocalView)
    quality = currentValueOf(LocalFluidGlassQuality)
    val density = requireDensity().density
    scheduler = GlassProbeScheduler(fastPxPerSecond = GlassProbeFastMotionDpPerSecond * density)
    thumbnailLayer = requireGraphicsContext().createGraphicsLayer()
    thumbnailDirty = true
    recordedGeometry = null
    lastGeometry = null
    unavailable = false
    subscribe()
    signal()
  }

  override fun onDetach() {
    unsubscribe()
    handler.removeCallbacks(pumpMessage)
    handler.removeCallbacks(timerMessage)
    pumpPosted = false
    timerAt = Long.MAX_VALUE
    releaseSlot()
    thumbnailLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
    thumbnailLayer = null
    coordinates = null
    scheduler = null
    view = null
    quality = null
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    this.coordinates = coordinates
    FluidGlassProbeDiagnostics.onWake(GlassProbeWake.Positioned)
    signal()
  }

  override fun onObservedReadsChanged() {
    FluidGlassProbeDiagnostics.onWake(GlassProbeWake.ObservedReads)
    signal()
  }

  /** Il segnale: un messaggio sul principale, uno solo in coda. Si può chiamare dal disegno. */
  private fun signal() {
    if (pumpPosted || !isAttached) return
    pumpPosted = true
    handler.post(pumpMessage)
  }

  private fun subscribe() {
    val layers = ArrayList<LayerBackdrop>()
    for (source in backdrop.layerSources) if (source is LayerBackdrop) layers += source
    sources = layers
    sourcesUnannounced = layers.isEmpty() || layers.size != backdrop.layerSources.size
    for (layer in layers) layer.addRevisionListener(revisionListener)
  }

  private fun unsubscribe() {
    for (layer in sources) layer.removeRevisionListener(revisionListener)
    sources = emptyList()
  }

  /** Il giro del messaggio: consuma i segnali, chiede allo schedulatore, fa partire la lettura o arma l'attesa. */
  private fun pump() {
    if (!isAttached || unavailable) return
    val scheduler = scheduler ?: return
    val now = SystemClock.uptimeMillis()
    // Le letture osservate: dove stanno le sorgenti (`layerCoordinates` cambia a ogni loro posizionamento) e la qualità.
    observeReads {
      for (layer in sources) layer.layerCoordinates
      quality?.level
      region?.invoke()
    }
    if (revisionSeen) {
      revisionSeen = false
      scheduler.onRevision(now)
    }
    val moved = geometryMovement()
    if (moved != null) scheduler.onGeometry(now, moved)
    scheduler.setUnannounced(sourcesUnannounced || sources.any { !it.announcesRevisions })
    val suspended = (quality?.level ?: 1f) < GlassProbeQualityFloor
    scheduler.setSuspended(suspended)
    if (state.advance(now)) {
      // Il filtro ha girato alla sua scadenza, senza una lettura nuova: chi ascolta lo sa con l'ultima lettura.
      state.stats?.let { last -> onReading?.invoke(last, state.target, false) }
    }
    var wakeAt = state.nextDeadlineMillis()
    when (val decision = scheduler.next(now)) {
      is GlassProbeDecision.Read -> startRead(now, decision.rerecord)
      is GlassProbeDecision.WaitUntil -> wakeAt = min(wakeAt, decision.atMillis)
      GlassProbeDecision.Idle ->
        if (suspended && scheduler.hasPending) wakeAt = min(wakeAt, now + GlassProbeSuspendedRecheckMillis)
    }
    armTimer(now, wakeAt)
  }

  private fun armTimer(now: Long, atMillis: Long) {
    if (atMillis == Long.MAX_VALUE) return
    if (timerAt != Long.MAX_VALUE && timerAt <= atMillis) return
    handler.removeCallbacks(timerMessage)
    timerAt = atMillis
    handler.postDelayed(timerMessage, max(0L, atMillis - now))
  }

  private fun startRead(now: Long, rerecord: Boolean) {
    val scheduler = scheduler ?: return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      unavailable = true
      return
    }
    val reader = obtainReader()
    if (reader == null) {
      // Nessun lettore per questa finestra (non accelerata, caselle finite, piattaforma che rifiuta): la sonda resta
      // sul lato del tema per tutta la vita del nodo. Non si riprova a ogni evento.
      unavailable = true
      return
    }
    scheduler.onReadSent(now, rerecord)
    FluidGlassProbeDiagnostics.onWake(if (rerecord) GlassProbeWake.ReadRerecord else GlassProbeWake.Read)
    if (rerecord) thumbnailDirty = true
    // Una richiesta che il lettore non accetta chiude subito la lettura: una sonda «in volo» per sempre non legge più.
    if (!reader.request(slot)) scheduler.onReadDone(now, GlassProbeOutcome.Failed)
  }

  @RequiresApi(Build.VERSION_CODES.Q)
  private fun obtainReader(): GlassProbeWindowReader? {
    reader?.let { if (it.isAlive) return it }
    reader = null
    slot = -1
    val target = view ?: return null
    val obtained = GlassProbeWindowReader.obtain(target) ?: return null
    priority = currentPriority(target)
    val assigned = obtained.register(this)
    if (assigned < 0) {
      FluidGlassProbeDiagnostics.onSlotRefused()
      Log.w(GlassProbeLogTag, "Sonda dello sfondo: più di $GlassProbeMaxSlots vetri radice in una finestra, questo resta sul lato del tema")
      return null
    }
    reader = obtained
    slot = assigned
    thumbnailDirty = true
    return obtained
  }

  /** L'importanza di questo vetro adesso, nella finestra di [view] (vedi [glassProbePriority]). */
  private fun currentPriority(view: View): Float {
    val self = coordinates ?: return 0f
    if (!self.isAttached) return 0f
    val position = try {
      self.positionInWindow()
    } catch (_: Exception) {
      return 0f
    }
    val area = probedArea(self) ?: return 0f
    val root = view.rootView
    return glassProbePriority(
      width = area.width,
      height = area.height,
      centerX = position.x + area.center.x,
      centerY = position.y + area.center.y,
      windowWidth = root.width.toFloat(),
      windowHeight = root.height.toFloat(),
    )
  }

  override fun onEvicted() {
    // Un vetro più importante ha preso la casella: questo resta con l'ultima lettura (nessuno scatto) e non legge più.
    reader = null
    slot = -1
    unavailable = true
    FluidGlassProbeDiagnostics.onSlotRefused()
    Log.w(GlassProbeLogTag, "Sonda dello sfondo: casella ceduta a un vetro più grande o più centrale, questo tiene l'ultimo lato")
  }

  private fun releaseSlot() {
    val current = reader
    if (current != null && slot >= 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) current.unregister(slot)
    reader = null
    slot = -1
  }

  /**
   * La geometria adesso: taglia, e posizione rispetto a ogni sorgente (o nella finestra, se il `Backdrop` non dice di
   * che cosa è fatto). Null se manca qualcosa per saperla.
   */
  private fun currentGeometry(): FloatArray? {
    val self = coordinates ?: return null
    if (!self.isAttached) return null
    val area = probedArea(self) ?: return null
    val out = FloatArray(2 + 2 * max(1, sources.size))
    out[0] = area.width
    out[1] = area.height
    if (sources.isEmpty()) {
      val position = self.positionInWindow()
      out[2] = position.x + area.left
      out[3] = position.y + area.top
      return out
    }
    for ((index, layer) in sources.withIndex()) {
      val sourceCoordinates = layer.layerCoordinates ?: return null
      if (!sourceCoordinates.isAttached) return null
      val offset = try {
        sourceCoordinates.localPositionOf(self)
      } catch (_: Exception) {
        return null
      }
      out[2 + 2 * index] = offset.x + area.left
      out[3 + 2 * index] = offset.y + area.top
    }
    return out
  }

  /** La parte del nodo sotto cui si legge, in pixel del nodo: [region], o tutto il nodo. Null se vuota. */
  private fun probedArea(self: LayoutCoordinates): Rect? {
    val size = self.size
    val asked = region
    val area = if (asked == null) Rect(0f, 0f, size.width.toFloat(), size.height.toFloat()) else asked() ?: return null
    return if (area.width > 0f && area.height > 0f && area.width.isFinite() && area.height.isFinite()) area else null
  }

  /** Di quanto si è mosso il vetro dall'ultimo giro (lo scarto più grande), 0 alla nascita, null se è fermo. */
  private fun geometryMovement(): Float? {
    val current = currentGeometry() ?: return null
    val previous = lastGeometry
    lastGeometry = current
    if (previous == null || previous.size != current.size) return 0f
    var moved = 0f
    for (i in current.indices) moved = max(moved, abs(current[i] - previous[i]))
    return if (moved > 0f) moved else null
  }

  override fun prepareThumbnail(): Boolean {
    if (!isAttached) return false
    val self = coordinates ?: return false
    if (!self.isAttached || !backdrop.isReadyToSample()) return false
    val geometry = currentGeometry() ?: return false
    val layer = thumbnailLayer ?: return false
    val recorded = recordedGeometry
    if (thumbnailDirty || recorded == null || !recorded.contentEquals(geometry)) {
      val density = requireDensity()
      val area = probedArea(self) ?: return false
      val scaleX = GlassProbeReadSide / area.width
      val scaleY = GlassProbeReadSide / area.height
      val source = backdrop
      layer.record(density, LayoutDirection.Ltr, IntSize(GlassProbeReadSide, GlassProbeReadSide)) {
        scale(scaleX, scaleY, pivot = Offset.Zero) {
          translate(left = -area.left, top = -area.top) {
            with(source) { drawBackdrop(density, self, null) }
          }
        }
      }
      recordedGeometry = geometry
      thumbnailDirty = false
    }
    return true
  }

  override fun onProbeResult(outcome: GlassProbeOutcome, stats: GlassBackdropStats?) {
    if (!isAttached) return
    val scheduler = scheduler ?: return
    val now = SystemClock.uptimeMillis()
    val delivered = outcome == GlassProbeOutcome.Read && stats != null && stats.coverage >= GlassProbeMinCoverage
    // Una lettura che torna senza abbastanza da leggere (una sorgente vuota o non registrata) non è una lettura (F2.3c):
    // per lo schedulatore è a vuoto, e dopo il tetto la cadenza fissa si ferma.
    scheduler.onReadDone(now, if (outcome == GlassProbeOutcome.Read && !delivered) GlassProbeOutcome.Empty else outcome)
    if (delivered && stats != null) {
      state.publish(now, stats)
      FluidGlassProbeDiagnostics.onReading()
      onReading?.invoke(stats, state.target, true)
    } else if (outcome == GlassProbeOutcome.Failed) {
      FluidGlassProbeDiagnostics.onFailure()
    }
    // Dopo una lettura saltata non si riparte da soli: un nuovo tentativo subito salterebbe di nuovo, e il prossimo
    // evento (la finestra che torna e ridisegna, la sorgente che avvisa, il vetro che si posa) arriva comunque.
    if (outcome != GlassProbeOutcome.Skipped) signal()
  }

  override fun onReaderLost() {
    reader = null
    slot = -1
    if (isAttached) onProbeResult(GlassProbeOutcome.Failed, null)
  }
}

// ------------------------------------------------------------------------------------------------------------
// Il lettore di una finestra
// ------------------------------------------------------------------------------------------------------------

/** Chi occupa una casella del lettore. Tutto sul filo principale. */
internal interface GlassProbeClient {
  /** La miniatura da posare nella casella, già registrata da [prepareThumbnail]. */
  val thumbnailLayer: GraphicsLayer?

  /** L'importanza della sonda per le caselle ([glassProbePriority]), fissata quando ha chiesto la casella. */
  val priority: Float

  /** Un vetro più importante ha preso la casella: questa sonda non legge più, e il vetro tiene l'ultimo lato misurato. */
  fun onEvicted()

  /** Subito prima della richiesta, sul principale: registra la miniatura se serve. Falso = niente da leggere adesso. */
  fun prepareThumbnail(): Boolean

  /** Com'è andata la lettura chiesta; [stats] solo se [outcome] è `Read`. */
  fun onProbeResult(outcome: GlassProbeOutcome, stats: GlassBackdropStats?)

  /** Il lettore si è rotto ed è stato buttato: la casella non c'è più. */
  fun onReaderLost()
}

/**
 * Il lettore di una finestra: un `HardwareRenderer` che rende le miniature delle sonde, una per casella, in un
 * `ImageReader`, e un filo di lavoro che ne fa i conti. È il `ProbeReaderHub` di S1-bis (`sample/…/measure/
 * ProbeReader.kt`) con le scelte che lì si sono provate: radice ri-registrata a ogni richiesta, al più due fotogrammi
 * in volo, un `syncAndDraw` rifiutato è una lettura fallita e basta, lettore non opaco e con le impostazioni di serie
 * (le varianti `opaque`, `start`, `usage gpu` non cambiavano niente).
 *
 * Uno per finestra: la chiave è la vista radice. Nasce alla prima sonda che legge e si chiude quando l'ultima se ne va,
 * o quando una lettura non torna entro [GlassProbeReadTimeoutMillis] (le sonde ne chiedono uno nuovo).
 */
@RequiresApi(Build.VERSION_CODES.Q)
internal class GlassProbeWindowReader private constructor(private val root: View) {

  /** Una richiesta partita: quali caselle, con quale generazione (una casella liberata e ripresa non riceve dati vecchi). */
  private class Batch(val mask: Long, val generations: IntArray, val sentAt: Long)

  private val side = GlassProbeReadSide
  // Non `width`: dentro `RenderNode(...).apply { }` quel nome sarebbe la larghezza del nodo, cioè zero (il tranello di
  // S1-bis, «una radice larga zero non disegna niente e non dà nessun errore»).
  private val imageWidth = side * GlassProbeSlotColumns
  private val imageHeight = side * ((GlassProbeMaxSlots + GlassProbeSlotColumns - 1) / GlassProbeSlotColumns)
  private val density = root.resources.displayMetrics.density
  private val main = Handler(Looper.getMainLooper())
  private val thread = HandlerThread("FluidGlass.sonda").apply { start() }
  private val worker = Handler(thread.looper)
  private val reader = ImageReader.newInstance(imageWidth, imageHeight, PixelFormat.RGBA_8888, MaxImages)
  private val rootNode = RenderNode("FluidGlass.sonda").also { it.setPosition(0, 0, imageWidth, imageHeight) }
  private val renderer = HardwareRenderer().also {
    it.setName("FluidGlass.sonda")
    it.setContentRoot(rootNode)
    it.setSurface(reader.surface)
    // Una casella senza sonda deve leggersi vuota, non sporca del fotogramma di prima.
    it.isOpaque = false
  }
  private val drawScope = CanvasDrawScope()
  private val clients = arrayOfNulls<GlassProbeClient>(GlassProbeMaxSlots)
  private val generations = IntArray(GlassProbeMaxSlots)
  private val pending = ConcurrentLinkedQueue<Batch>()
  private var requested = 0L
  private var framePosted = false
  private var watchdogPosted = false

  // Del filo di lavoro soltanto.
  private val reducer = GlassProbeStatsReducer(side, GlassProbeStatsSide)
  private var row = ByteArray(0)

  @Volatile
  private var released = false

  /** Vero finché il lettore si può usare. */
  val isAlive: Boolean get() = !released

  private val afterDraw = Runnable { send() }
  private val frameCallback = Choreographer.FrameCallback { main.post(afterDraw) }
  private val watchdog = Runnable { checkStuck() }

  init {
    reader.setOnImageAvailableListener({ source -> drain(source) }, worker)
  }

  /**
   * Prende una casella per [client]. Se sono finite, la prende a chi ha l'importanza ([GlassProbeClient.priority]) più
   * bassa, se è più bassa di quella di [client]: quello resta col suo ultimo lato misurato e non legge più
   * ([GlassProbeClient.onEvicted]). -1 se [client] non entra. Dal principale.
   */
  @MainThread
  fun register(client: GlassProbeClient): Int {
    if (released) return -1
    for (index in clients.indices) {
      if (clients[index] == null) {
        clients[index] = client
        generations[index]++
        FluidGlassProbeDiagnostics.onSlotsInUse(clients.count { it != null })
        return index
      }
    }
    var weakest = -1
    var weakestPriority = client.priority
    for (index in clients.indices) {
      val other = clients[index] ?: continue
      if (other.priority < weakestPriority) {
        weakest = index
        weakestPriority = other.priority
      }
    }
    if (weakest < 0) return -1
    val evicted = clients[weakest]
    clients[weakest] = client
    generations[weakest]++
    requested = requested and (1L shl weakest).inv()
    evicted?.onEvicted()
    return weakest
  }

  /** Libera la casella [slot]; l'ultima chiude il lettore. Dal principale. */
  @MainThread
  fun unregister(slot: Int) {
    if (slot !in clients.indices) return
    clients[slot] = null
    generations[slot]++
    requested = requested and (1L shl slot).inv()
    if (clients.all { it == null }) release()
  }

  /**
   * La casella [slot] vuole una lettura: parte dopo il disegno del prossimo fotogramma della finestra, e la risposta
   * arriva con `onProbeResult`. Falso se il lettore non la accetta (chiuso, casella non sua): nessuna risposta arriverà.
   */
  @MainThread
  fun request(slot: Int): Boolean {
    if (released || slot !in clients.indices || clients[slot] == null) return false
    requested = requested or (1L shl slot)
    if (!framePosted) {
      framePosted = true
      Choreographer.getInstance().postFrameCallback(frameCallback)
    }
    return true
  }

  /** Dopo il disegno della finestra: registra ciò che serve e chiede il fotogramma del lettore. */
  private fun send() {
    framePosted = false
    if (released) return
    val asked = requested
    if (asked == 0L) return
    if (pending.size >= MaxInFlight) {
      // Le richieste restano: le riprende l'arrivo dell'immagine in volo (vedi `deliver`).
      return
    }
    requested = 0L
    // In secondo piano non si legge: il fotogramma sarebbe rifiutato, e al ritorno la finestra ridisegna e le sorgenti
    // avvisano da sole.
    val visible = root.isAttachedToWindow && root.isShown && root.windowVisibility == View.VISIBLE
    var drawable = 0L
    for (slot in clients.indices) {
      if (asked and (1L shl slot) == 0L) continue
      val client = clients[slot] ?: continue
      if (visible && client.prepareThumbnail() && client.thumbnailLayer != null) {
        drawable = drawable or (1L shl slot)
      } else {
        client.onProbeResult(GlassProbeOutcome.Skipped, null)
      }
    }
    if (drawable == 0L) return
    recordRoot(drawable)
    val batch = Batch(drawable, generations.copyOf(), SystemClock.uptimeMillis())
    pending.add(batch)
    val result = try {
      renderer.createRenderRequest().setVsyncTime(System.nanoTime()).syncAndDraw()
    } catch (error: RuntimeException) {
      Log.w(GlassProbeLogTag, "Sonda dello sfondo: syncAndDraw ha lanciato ($error)")
      RefusedSync
    }
    if (result and RefusedSync != 0) {
      // Nessun fotogramma, quindi nessuna immagine: la richiesta non resta in coda ad aspettarla.
      pending.remove(batch)
      fail(batch)
      return
    }
    FluidGlassProbeDiagnostics.onRequest()
    if (!watchdogPosted) {
      watchdogPosted = true
      main.postDelayed(watchdog, GlassProbeReadTimeoutMillis)
    }
  }

  /** La radice: la miniatura di ogni casella chiesta, al suo posto. Le altre restano trasparenti. */
  private fun recordRoot(mask: Long) {
    val canvas = rootNode.beginRecording()
    try {
      drawScope.draw(
        Density(density),
        LayoutDirection.Ltr,
        androidx.compose.ui.graphics.Canvas(canvas),
        Size(imageWidth.toFloat(), imageHeight.toFloat()),
      ) {
        for (slot in clients.indices) {
          if (mask and (1L shl slot) == 0L) continue
          val layer = clients[slot]?.thumbnailLayer ?: continue
          translate(left = glassProbeSlotLeft(slot, side).toFloat(), top = glassProbeSlotTop(slot, side).toFloat()) {
            clipRect(0f, 0f, side.toFloat(), side.toFloat()) { drawLayer(layer) }
          }
        }
      }
    } finally {
      rootNode.endRecording()
    }
  }

  /** Sul filo di lavoro: ogni immagine pronta, letta e restituita subito. */
  private fun drain(source: ImageReader) {
    while (true) {
      val image = try {
        source.acquireNextImage()
      } catch (_: IllegalStateException) {
        // Il lettore si è chiuso mentre l'avviso era in coda, o le immagini in mano sono troppe.
        null
      } ?: return
      val batch = pending.poll()
      var results: Array<GlassBackdropStats?>? = null
      try {
        if (batch != null) results = read(image, batch.mask)
      } catch (_: IllegalStateException) {
        // L'immagine è stata invalidata dalla chiusura del lettore: niente da consegnare.
        results = null
      } finally {
        image.close()
      }
      if (batch != null) {
        val delivered = results
        main.post { deliver(batch, delivered) }
      }
    }
  }

  /** Le statistiche delle caselle in [mask]. Sul filo di lavoro. */
  private fun read(image: Image, mask: Long): Array<GlassBackdropStats?> {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val rowBytes = side * pixelStride
    if (row.size < rowBytes) row = ByteArray(rowBytes)
    val out = arrayOfNulls<GlassBackdropStats>(GlassProbeMaxSlots)
    for (slot in 0 until GlassProbeMaxSlots) {
      if (mask and (1L shl slot) == 0L) continue
      reducer.begin()
      val top = glassProbeSlotTop(slot, side)
      val left = glassProbeSlotLeft(slot, side) * pixelStride
      for (y in 0 until side) {
        buffer.position((top + y) * rowStride + left)
        buffer.get(row, 0, rowBytes)
        reducer.addRow(y, row, 0, pixelStride)
      }
      out[slot] = reducer.finish()
    }
    return out
  }

  /** Sul principale: i numeri alle sonde che li avevano chiesti e sono ancora lì. */
  private fun deliver(batch: Batch, results: Array<GlassBackdropStats?>?) {
    if (released) return
    for (slot in clients.indices) {
      if (batch.mask and (1L shl slot) == 0L) continue
      if (generations[slot] != batch.generations[slot]) continue
      val client = clients[slot] ?: continue
      val stats = results?.get(slot)
      client.onProbeResult(if (stats != null) GlassProbeOutcome.Read else GlassProbeOutcome.Failed, stats)
    }
    if (released) return
    // Richieste rimaste indietro mentre due fotogrammi erano in volo.
    if (requested != 0L && !framePosted) {
      framePosted = true
      Choreographer.getInstance().postFrameCallback(frameCallback)
    }
  }

  private fun fail(batch: Batch) {
    for (slot in clients.indices) {
      if (batch.mask and (1L shl slot) == 0L) continue
      if (generations[slot] != batch.generations[slot]) continue
      clients[slot]?.onProbeResult(GlassProbeOutcome.Failed, null)
    }
  }

  /** Una lettura che non torna: il lettore si butta e le sonde ne chiederanno un altro. */
  private fun checkStuck() {
    watchdogPosted = false
    if (released) return
    val oldest = pending.peek() ?: return
    val age = SystemClock.uptimeMillis() - oldest.sentAt
    if (age < GlassProbeReadTimeoutMillis) {
      watchdogPosted = true
      main.postDelayed(watchdog, GlassProbeReadTimeoutMillis - age)
      return
    }
    Log.w(GlassProbeLogTag, "Sonda dello sfondo: una lettura non è tornata in $age ms, il lettore della finestra si rifà")
    FluidGlassProbeDiagnostics.onFailure()
    val orphans = clients.copyOf()
    release()
    for (client in orphans) client?.onReaderLost()
  }

  /** Chiude tutto. Dal principale. */
  @MainThread
  fun release() {
    if (released) return
    released = true
    if (readers[root] === this) readers.remove(root)
    live.decrementAndGet()
    main.removeCallbacks(afterDraw)
    main.removeCallbacks(watchdog)
    Choreographer.getInstance().removeFrameCallback(frameCallback)
    reader.setOnImageAvailableListener(null, null)
    renderer.stop()
    renderer.destroy()
    reader.close()
    rootNode.discardDisplayList()
    thread.quitSafely()
    pending.clear()
    clients.fill(null)
  }

  companion object {
    /** Immagini che l'`ImageReader` può dare insieme: una in lettura e margine per quella che arriva (S1-bis). */
    private const val MaxImages = 3

    /** Oltre due fotogrammi in volo il RenderThread resterebbe ad aspettare un buffer libero (S1-bis). */
    private const val MaxInFlight = 2

    private const val RefusedSync = HardwareRenderer.SYNC_LOST_SURFACE_REWARD_IF_FOUND or
      HardwareRenderer.SYNC_CONTEXT_IS_STOPPED or
      HardwareRenderer.SYNC_FRAME_DROPPED

    /** I lettori vivi, per vista radice. Solo dal principale. */
    private val readers = WeakHashMap<View, GlassProbeWindowReader>()

    private val live = AtomicInteger(0)

    /** Quanti lettori sono aperti in questo processo. */
    val liveCount: Int get() = live.get()

    /** Il lettore della finestra di [view], creato se serve; null se la finestra non è accelerata o se il lettore non nasce. */
    @MainThread
    fun obtain(view: View): GlassProbeWindowReader? {
      if (!view.isHardwareAccelerated) return null
      val root = view.rootView
      readers[root]?.let { if (it.isAlive) return it }
      return try {
        GlassProbeWindowReader(root).also {
          readers[root] = it
          live.incrementAndGet()
        }
      } catch (error: RuntimeException) {
        Log.w(GlassProbeLogTag, "Sonda dello sfondo: il lettore della finestra non è nato ($error), i vetri restano sul lato del tema")
        null
      }
    }
  }
}

// ------------------------------------------------------------------------------------------------------------
// La diagnostica per le prove sul dispositivo
// ------------------------------------------------------------------------------------------------------------

/** Perché una sonda si è svegliata (vedi [FluidGlassProbeDiagnostics.wakes]). */
internal enum class GlassProbeWake { Revision, Positioned, ObservedReads, Timer, Read, ReadRerecord }

/**
 * Una lettura della sonda, come la vede una prova sul dispositivo. Non è un'API per le app.
 *
 * @property luminance luminanza media (0..1).
 * @property deviation deviazione standard della luminanza.
 * @property gradient radice dell'energia dei gradienti.
 * @property coverage alfa medio.
 * @property dark il lato deciso dal filtro adesso.
 * @property fresh vero se è una lettura nuova; falso se il filtro ha girato alla sua scadenza (stessi numeri di prima).
 * @property uptimeMillis quando è arrivata, in `SystemClock.uptimeMillis()`.
 */
@Immutable
class FluidGlassProbeReading internal constructor(
  val luminance: Float,
  val deviation: Float,
  val gradient: Float,
  val red: Float,
  val green: Float,
  val blue: Float,
  val coverage: Float,
  val dark: Boolean,
  val fresh: Boolean,
  val uptimeMillis: Long,
) {
  override fun toString(): String =
    "FluidGlassProbeReading(luminance=$luminance, deviation=$deviation, gradient=$gradient, " +
      "rgb=($red, $green, $blue), coverage=$coverage, dark=$dark, fresh=$fresh, at=$uptimeMillis)"
}

/**
 * La sonda dello sfondo vista da fuori, per le prove strumentate (`GlassProbeInstrumentedTest` nel modulo della
 * galleria, che non vede gli `internal` dell'engine). Non è un'API per le app: nessun componente la usa, e la sonda
 * vera entra nei vetri con F2.2.
 */
object FluidGlassProbeDiagnostics {
  private val requests = AtomicLong()
  private val readings = AtomicLong()
  private val failures = AtomicLong()
  private val refused = AtomicLong()
  private val peak = AtomicInteger()
  private val wakes = Array(GlassProbeWake.entries.size) { AtomicLong() }

  /**
   * Perché le sonde si sono svegliate, per motivo (F2.3): una revisione di una sorgente, un riposizionamento, una
   * lettura osservata cambiata, il timer (cadenza o scadenza del filtro), e le letture partite (con o senza miniatura
   * nuova). Su una pagina ferma, dopo la prima misura, non deve crescere niente.
   */
  fun wakes(): Map<String, Long> = GlassProbeWake.entries.associate { it.name to wakes[it.ordinal].get() }

  /** Le sonde rimaste senza casella (o spinte fuori da una più importante). */
  val totalSlotsRefused: Long get() = refused.get()

  /**
   * Il massimo di caselle occupate insieme in un lettore di finestra, da [resetPeakSlots] (F2.3): quante sonde radice ha
   * la pagina più carica che si è aperta intanto. Il tetto è 64.
   */
  val peakSlots: Int get() = peak.get()

  /** Riparte il conto di [peakSlots]. */
  fun resetPeakSlots() {
    peak.set(0)
  }

  internal fun onSlotsInUse(count: Int) {
    peak.accumulateAndGet(count) { a, b -> max(a, b) }
  }

  internal fun onWake(reason: GlassProbeWake) {
    wakes[reason.ordinal].incrementAndGet()
  }

  internal fun onSlotRefused() {
    refused.incrementAndGet()
  }

  /** Azzera i contatori, per i test. */
  fun resetForTesting() {
    requests.set(0)
    readings.set(0)
    failures.set(0)
    refused.set(0)
    peak.set(0)
    wakes.forEach { it.set(0) }
  }

  /** I fotogrammi chiesti ai lettori in tutto il processo (una richiesta può leggere più sonde). */
  val totalRequests: Long get() = requests.get()

  /** Le letture consegnate alle sonde. */
  val totalReadings: Long get() = readings.get()

  /** Le letture fallite (fotogramma rifiutato, lettore che non risponde). */
  val totalFailures: Long get() = failures.get()

  /** I lettori di finestra aperti adesso. */
  val liveReaders: Int
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) GlassProbeWindowReader.liveCount else 0

  /** Se la sonda può esistere su questo dispositivo (Android 13 e oltre). */
  val isAvailable: Boolean get() = glassProbeAvailable()

  /**
   * Una sonda su [backdrop], con il proprio filtro (lato iniziale chiaro): [onReading] riceve, sul filo principale,
   * ogni lettura (`fresh` vero) e ogni giro che il filtro fa alla sua scadenza (`fresh` falso). Sotto Android 13 non
   * aggiunge niente.
   */
  fun probe(backdrop: Backdrop, onReading: (FluidGlassProbeReading) -> Unit): Modifier {
    if (!glassProbeAvailable()) return Modifier
    val forward: GlassProbeListener = { stats, target, fresh ->
      onReading(
        FluidGlassProbeReading(
          luminance = stats.luminance,
          deviation = stats.deviation,
          gradient = stats.gradient,
          red = stats.red,
          green = stats.green,
          blue = stats.blue,
          coverage = stats.coverage,
          dark = target.dark,
          fresh = fresh,
          uptimeMillis = SystemClock.uptimeMillis(),
        ),
      )
    }
    return Modifier then GlassProbeElement(backdrop, null, forward)
  }

  internal fun onRequest() {
    requests.incrementAndGet()
  }

  internal fun onReading() {
    readings.incrementAndGet()
  }

  internal fun onFailure() {
    failures.incrementAndGet()
  }
}
