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
import android.view.ViewTreeObserver
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
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
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
import dev.antigravity.fluidengine.ui.glass.backdrop.GlassSamplePlacement
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
 * l'ultimo lato misurato (nessuno scatto). Dalla F7.25b (review R2) né l'uno né l'altro si spengono per sempre: riprovano
 * al primo evento dopo un'attesa che cresce ([glassProbeReaderRetryMillis]), e una casella liberata si riprende.
 */
internal const val GlassProbeMaxSlots: Int = 64

/**
 * F7.25b (review R2, difetto 5) · Dopo quanto una sonda rimasta senza lettore (caselle piene, sfrattata, lettore che non è
 * nato) riprova, alla [misses]-esima volta di fila: la cadenza massima raddoppiata a ogni volta, fino al tetto delle
 * letture fallite (240, 480, 960, 1920, poi 2000 ms), come le letture fallite. Si riprova al primo evento dopo l'attesa,
 * non a tempo: a pagina ferma una sonda senza casella non sveglia niente.
 */
internal fun glassProbeReaderRetryMillis(misses: Int): Long {
  val shift = misses.coerceIn(1, 16)
  val grown = GlassProbeMinIntervalMillis shl shift
  return if (grown <= 0L) GlassProbeMaxBackoffMillis else min(grown, GlassProbeMaxBackoffMillis)
}

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

/**
 * Quante letture uguali di fila (F7.4, [GlassProbeRest]) prima che la sonda rallenti: due, cioè la terza lettura che
 * torna come le due prima. Una sola uguale può essere un caso (il contenuto che si ferma proprio lì).
 */
internal const val GlassProbeRestAfter: Int = 2

/**
 * Il tetto dello spazio fra due letture di una sonda che rallenta (F7.4): quattro volte la cadenza massima. È anche il
 * ritardo più lungo con cui una sonda a riposo si accorge di un cambio **sotto** il suo vetro mentre la pagina si
 * ridisegna di continuo **altrove** (il solo caso in cui la leva cambia un comportamento): sotto il mezzo secondo, la
 * stessa scala dell'inchiostro che gira (500–700 ms).
 */
internal const val GlassProbeRestMaxMillis: Long = 4 * GlassProbeMinIntervalMillis

/**
 * Una revisione che arriva dopo tanto silenzio è un cambio vero, non il fotogramma di un'animazione (F7.4): la sonda
 * smette di riposare e legge con la cadenza di sempre. Due volte la cadenza massima: un'animazione ridisegna ogni 8–16 ms,
 * un cursore che lampeggia ogni 500.
 */
internal const val GlassProbeRestFreshMillis: Long = 2 * GlassProbeMinIntervalMillis

/**
 * Due letture uguali, entro mezzo livello su 255 per le medie e un millesimo relativo per varianze ed energie: lo stesso
 * contenuto riletto dà gli stessi byte, quindi gli stessi numeri; la tolleranza assorbe solo un arrotondamento della GPU.
 * Un dettaglio che cambia sotto il vetro (un'icona che gira, una riga che entra) li sposta molto di più.
 */
internal fun GlassBackdropStats.sameAs(other: GlassBackdropStats): Boolean {
  val level = 0.5f / 255f
  fun near(a: Float, b: Float) = a == b || abs(a - b) <= level
  fun nearRelative(a: Float, b: Float) = a == b || abs(a - b) <= 1e-3f * max(abs(a), abs(b)) + 1e-7f
  fun nearShare(a: Float, b: Float) = (a.isNaN() && b.isNaN()) || near(a, b)
  return near(luminance, other.luminance) && near(red, other.red) && near(green, other.green) &&
    near(blue, other.blue) && near(coverage, other.coverage) && nearShare(darkShare, other.darkShare) &&
    nearRelative(variance, other.variance) && nearRelative(gradientEnergy, other.gradientEnergy) &&
    nearRelative(textEnergy, other.textEnergy)
}

/**
 * Il riposo di una sonda (F7.4, leva 7 del piano: «sonda sospesa quando la cattura non cambia; frequenza per
 * contenitore»). Puro: gli istanti glieli dà chi chiama.
 *
 * Il caso. Una sorgente si ridisegna a ogni fotogramma per qualcosa che sta **altrove** sulla pagina (un indicatore che
 * gira, la card vivida della scheda «Materiale», un orologio), e ogni vetro radice di quella pagina riceve una revisione a
 * ogni fotogramma: oggi rilegge ogni 120 ms, 8 letture al secondo per ciascuno, e ogni lettura tiene il principale 2–2,7
 * ms (S1-bis) per scoprire che sotto di lui non è cambiato niente. Qui la sonda guarda le sue letture: dopo
 * [GlassProbeRestAfter] letture uguali di fila lo spazio fra due letture raddoppia, fino a [GlassProbeRestMaxMillis].
 *
 * La frequenza è così **per contenitore** (una sonda per contenitore o per vetro radice, F3.2): ognuna rallenta per conto
 * suo, secondo ciò che ha sotto. La barra sopra la lista che scorre resta alla cadenza di sempre, il tasto sopra un fondo
 * fermo rallenta.
 *
 * Non rallenta mai quando il vetro o le sue sorgenti si spostano ([onMoved]: una geometria nuova, il contenuto che scorre
 * sotto la barra, anche su un tratto uniforme, dove due letture uguali non dicono niente di ciò che sta per entrare), né
 * alla prima revisione dopo un silenzio ([GlassProbeRestFreshMillis], [onRevision]: il fondo che passa da chiaro a scuro su
 * una pagina ferma si legge come prima). Non tocca la cadenza fissa delle sorgenti che non avvisano: lì è la sola difesa.
 */
internal class GlassProbeRest(
  private val minIntervalMillis: Long = GlassProbeMinIntervalMillis,
  private val restAfter: Int = GlassProbeRestAfter,
  private val maxIntervalMillis: Long = GlassProbeRestMaxMillis,
  private val freshMillis: Long = GlassProbeRestFreshMillis,
) {
  /** Le letture uguali di fila (la prima di una serie non conta: è il termine di paragone). */
  var steady: Int = 0
    private set

  private var previous: GlassBackdropStats? = null
  private var lastRevisionAt = Long.MIN_VALUE
  private var lastReadAt = Long.MIN_VALUE

  /** Una sorgente si è registrata di nuovo. Dopo un silenzio più lungo di [freshMillis] è un cambio: niente riposo. */
  fun onRevision(nowMillis: Long) {
    if (lastRevisionAt == Long.MIN_VALUE || nowMillis - lastRevisionAt > freshMillis) wake()
    lastRevisionAt = nowMillis
  }

  /** Il vetro si è spostato rispetto alle sorgenti, o il contenuto sotto di lui scorre: niente riposo. */
  fun onMoved() = wake()

  /** Una lettura è partita a [nowMillis]. */
  fun onReadSent(nowMillis: Long) {
    lastReadAt = nowMillis
  }

  /** I numeri di una lettura consegnata: uguali ai precedenti allungano la serie, diversi la chiudono. */
  fun onReading(stats: GlassBackdropStats) {
    val before = previous
    steady = if (before != null && before.sameAs(stats)) steady + 1 else 0
    previous = stats
  }

  /** Lo spazio fra due letture adesso: la cadenza di sempre, o di più se le ultime letture tornano uguali. */
  fun interval(): Long {
    if (steady < restAfter) return minIntervalMillis
    val shift = min(steady - restAfter + 1, 16)
    val grown = minIntervalMillis shl shift
    return if (grown <= 0L) maxIntervalMillis else min(grown, maxIntervalMillis)
  }

  /**
   * Prima di quando la sonda non legge, se riposa; `Long.MIN_VALUE` se può leggere quando lo schedulatore lo dice. Vale
   * solo per le letture chieste da una revisione: chi chiama non lo chiede per una geometria nuova, né per la cadenza
   * fissa delle sorgenti che non avvisano.
   */
  fun notBefore(): Long =
    if (steady < restAfter || lastReadAt == Long.MIN_VALUE) Long.MIN_VALUE else lastReadAt + interval()

  private fun wake() {
    steady = 0
  }
}

/**
 * Il cancello del riposo (F7.4), puro: prima di quando può partire la lettura [decision] che lo schedulatore ha deciso
 * adesso, o `Long.MIN_VALUE` se parte subito. Solo le letture chieste da una revisione aspettano: una geometria nuova
 * (la miniatura da rifare, la prima lettura) e la cadenza fissa delle sorgenti che non avvisano ([unannounced]) no.
 * [enabled] è la leva (`GlassFieldLevers.probeRest`).
 */
internal fun glassProbeRestGate(
  decision: GlassProbeDecision.Read,
  rest: GlassProbeRest?,
  unannounced: Boolean,
  enabled: Boolean,
): Long = if (enabled && rest != null && !decision.rerecord && !unannounced) rest.notBefore() else Long.MIN_VALUE

private const val GlassProbeLogTag = "FluidGlass"

// ------------------------------------------------------------------------------------------------------------
// Le statistiche
// ------------------------------------------------------------------------------------------------------------

/**
 * Le statistiche di un fondo **uniforme** di colore [color] (opaco, senza trama): ciò che il lettore finto dei test
 * ([FluidGlassProbeDiagnostics.readerForTesting]) consegna. Luminanza come quella della miniatura (Rec. 709 sui valori
 * sRGB), coperto del tutto.
 */
internal fun glassProbeUniformStats(color: Color): GlassBackdropStats {
  val r = color.red.coerceIn(0f, 1f)
  val g = color.green.coerceIn(0f, 1f)
  val b = color.blue.coerceIn(0f, 1f)
  val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
  return GlassBackdropStats(
    luminance = y, variance = 0f, gradientEnergy = 0f, red = r, green = g, blue = b, coverage = 1f,
    darkShare = if (y < GlassFlipCoverageDarkLuma) 1f else 0f,
  )
}

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
 * @property darkShare la quota dei punti della miniatura più scuri di [GlassFlipCoverageDarkLuma] (G1, per
 *   `flipby=coverage`); NaN se chi l'ha costruita non la sa.
 * @property textEnergy [gradientEnergy] senza i **bordi netti** (F3.2): tolti, per asse, i due bordi diritti più forti
 *   (ognuno sulla sua linea di confine fra celle e sulla vicina più forte, dove cade un bordo a metà cella). Il bordo di
 *   un riquadro a tinta unita sotto il vetro non è testo: contato come tale, rinforzava l'ombra «sul testo» sopra una
 *   card piatta. Il testo, una foto e una scacchiera hanno l'energia sparsa su molte linee e restano fitti (la prova sui
 *   casi del banco è in `artifacts/liquid-glass/sessioni/f3-2/trama.md`). Chi costruisce le statistiche a mano e non lo
 *   sa la lascia uguale a [gradientEnergy].
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
  val darkShare: Float = Float.NaN,
  val textEnergy: Float = gradientEnergy,
  /**
   * F7.33c · La luma di ogni cella della miniatura, [GlassProbeStatsSide] × [GlassProbeStatsSide], per righe dall'alto:
   * la legge un contenitore per sapere che lato ha sotto ogni suo grappolo (la coerenza del gruppo, [GlassProbeGroupReading]).
   * Null nelle letture uniformi (il lettore finto di serie). Non conta in [sameAs]; nell'uguaglianza conta per contenuto
   * ([GlassProbeCells]), così due letture uguali restano uguali per lo stato che le pubblica.
   */
  val cells: GlassProbeCells? = null,
) {
  /** La deviazione standard della luminanza. */
  val deviation: Float get() = sqrt(max(variance, 0f))

  /** La radice dell'energia dei gradienti, in unità di luminanza. */
  val gradient: Float get() = sqrt(max(gradientEnergy, 0f))

  /** Il colore medio (per il colore dell'ombra, F2.2). */
  val color: Color get() = Color(red.coerceIn(0f, 1f), green.coerceIn(0f, 1f), blue.coerceIn(0f, 1f))

  /**
   * Quanto lo sfondo è fitto: 0 piatto, 1 testo o immagine fitta. Vedi [GlassProbeTextureFlat]. Dalla radice di
   * [textEnergy] (F3.2), non di [gradientEnergy]: un bordo netto da solo non è trama.
   */
  val texture: Float get() = glassBackdropTexture(sqrt(max(textEnergy, 0f)))
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

  /** L'energia di ogni confine verticale fra due colonne di celle (i bordi verticali), e di ogni confine orizzontale. */
  private val columnEdges = FloatArray(statsSide - 1)
  private val rowEdges = FloatArray(statsSide - 1)

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
    var darkCells = 0
    for (i in 0 until count) {
      val d = lumas[i] - mean
      squares += d * d
      if (lumas[i] < GlassFlipCoverageDarkLuma) darkCells++
    }
    var energy = 0.0
    columnEdges.fill(0f)
    rowEdges.fill(0f)
    for (row in 0 until statsSide) {
      for (col in 0 until statsSide) {
        val here = lumas[row * statsSide + col]
        if (col + 1 < statsSide) {
          val d = lumas[row * statsSide + col + 1] - here
          energy += d * d
          columnEdges[col] += d * d
        }
        if (row + 1 < statsSide) {
          val d = lumas[(row + 1) * statsSide + col] - here
          energy += d * d
          rowEdges[row] += d * d
        }
      }
    }
    val pairs = 2 * statsSide * (statsSide - 1)
    val edges = glassProbeStraightEdges(columnEdges) + glassProbeStraightEdges(rowEdges)
    return GlassBackdropStats(
      luminance = mean,
      variance = (squares / count).toFloat(),
      gradientEnergy = (energy / pairs).toFloat(),
      red = (sumR / count).toFloat(),
      green = (sumG / count).toFloat(),
      blue = (sumB / count).toFloat(),
      coverage = (sumA / count).toFloat(),
      darkShare = darkCells.toFloat() / count,
      textEnergy = (max(energy - edges, 0.0) / pairs).toFloat(),
      cells = GlassProbeCells(lumas.copyOf()),
    )
  }
}

/** Quanti bordi diritti per asse [glassProbeStraightEdges] toglie dalla trama: i due lati di un riquadro. */
internal const val GlassProbeStraightEdgesPerAxis: Int = 2

/**
 * L'energia dei bordi diritti di un asse (F3.2), da togliere alla trama: [lines] è l'energia di ogni confine fra due
 * file di celle (lo **riscrive**). Per [GlassProbeStraightEdgesPerAxis] volte si prende il confine più forte e, con lui,
 * il vicino più forte: un bordo che cade a metà cella lascia una cella intermedia e divide la sua energia su due confini
 * accanto. Due bordi per asse sono i quattro lati di un riquadro tutto dentro la miniatura; un bordo obliquo resta
 * trama (è sparso su molti confini), e così il testo, che ha l'energia su molte linee. Pura.
 */
internal fun glassProbeStraightEdges(lines: FloatArray): Double {
  var removed = 0.0
  repeat(GlassProbeStraightEdgesPerAxis) {
    var strongest = -1
    for (i in lines.indices) if (lines[i] > 0f && (strongest < 0 || lines[i] > lines[strongest])) strongest = i
    if (strongest < 0) return removed
    removed += lines[strongest]
    lines[strongest] = 0f
    val before = if (strongest > 0) lines[strongest - 1] else -1f
    val after = if (strongest + 1 < lines.size) lines[strongest + 1] else -1f
    val neighbour = if (after > before) strongest + 1 else strongest - 1
    if (neighbour in lines.indices && lines[neighbour] > 0f) {
      removed += lines[neighbour]
      lines[neighbour] = 0f
    }
  }
  return removed
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
  // Di serie il filtro di sempre; in una build di debug le manopole del giro (`flipwait`, `flipon`, `flipoff`, S3;
  // `flipdwell`, `flipby`, `flipcover`, G1).
  internal val filter: GlassAdaptiveFilter = GlassAdaptiveTuning.filter(initialDark),
  /** Su che cosa decide il filtro (G1, `flipby`): di serie la luminanza media. */
  internal val by: GlassFlipBy = GlassAdaptiveTuning.by,
  /** Che cosa fa mentre lo sfondo scorre (G1, `flipscroll`): di serie decide come sempre. */
  internal val scroll: GlassFlipScroll = GlassAdaptiveTuning.scroll,
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

  /**
   * F7.33c · Chi legge la lettura per un gruppo di vetri prima del filtro (un `FluidGlassContainer`, la coerenza del
   * gruppo): può dare al filtro un altro valore, con le stesse soglie e gli stessi tempi. Null di serie. Dal principale.
   */
  internal var group: GlassProbeGroupReading? = null

  /** Una lettura nuova a [nowMillis]. Dal filo principale, fuori dal disegno. */
  internal fun publish(nowMillis: Long, stats: GlassBackdropStats) {
    this.stats = stats
    val value = glassFlipValue(stats, by)
    val grouped = if (by == GlassFlipBy.Luminance) group?.value(stats, filter) ?: Float.NaN else Float.NaN
    filter.sample(nowMillis, if (grouped.isFinite()) grouped else value)
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

/**
 * F7.33c · Le lume delle celle di una lettura ([GlassBackdropStats.cells]), uguali per contenuto: lo stato della sonda è
 * di serie strutturale, e un array uguale solo per riferimento farebbe nuova ogni lettura di uno sfondo fermo.
 */
internal class GlassProbeCells(val values: FloatArray) {
  override fun equals(other: Any?): Boolean = other is GlassProbeCells && values.contentEquals(other.values)
  override fun hashCode(): Int = values.contentHashCode()
  override fun toString(): String = "GlassProbeCells(${values.size})"
}

/**
 * F7.33c · La lettura di un gruppo di vetri (un `FluidGlassContainer`): dalla lettura grezza [stats] (con le sue celle) il
 * valore da dare al [filter] al posto della luminanza media, o NaN per la media. Dal filo principale, a ogni lettura.
 */
internal fun interface GlassProbeGroupReading {
  fun value(stats: GlassBackdropStats, filter: GlassAdaptiveFilter): Float
}

/**
 * F7.33c · Le statistiche di una miniatura finta della parte [where] (pixel della finestra): ogni cella prende il colore
 * che [colorAt] dice per il suo riquadro. Passa dalla stessa riduzione del lettore vero ([GlassProbeStatsReducer]). Solo
 * per il lettore finto delle prove ([FluidGlassProbeDiagnostics.readerInWindowForTesting]).
 */
internal fun glassProbeStatsOfRegion(
  where: Rect,
  colorAt: (Rect) -> Color,
  readSide: Int = GlassProbeReadSide,
  statsSide: Int = GlassProbeStatsSide,
): GlassBackdropStats {
  val rgba = ByteArray(readSide * readSide * 4)
  val cell = readSide / statsSide
  val w = where.width / statsSide
  val h = where.height / statsSide
  for (row in 0 until statsSide) for (col in 0 until statsSide) {
    val c = colorAt(Rect(where.left + col * w, where.top + row * h, where.left + (col + 1) * w, where.top + (row + 1) * h))
    val r = (c.red.coerceIn(0f, 1f) * 255f).toInt().toByte()
    val g = (c.green.coerceIn(0f, 1f) * 255f).toInt().toByte()
    val b = (c.blue.coerceIn(0f, 1f) * 255f).toInt().toByte()
    for (y in row * cell until (row + 1) * cell) for (x in col * cell until (col + 1) * cell) {
      val at = (y * readSide + x) * 4
      rgba[at] = r; rgba[at + 1] = g; rgba[at + 2] = b; rgba[at + 3] = 0xFF.toByte()
    }
  }
  return glassProbeStats(rgba, readSide, statsSide)
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

  /** Il riposo di questa sonda (F7.4, [GlassProbeRest]): rallenta quando le sue letture tornano uguali. */
  private var rest: GlassProbeRest? = null
  private var view: View? = null
  private var quality: FluidGlassQuality? = null
  private var coordinates: LayoutCoordinates? = null
  private var sources: List<LayerBackdrop> = emptyList()
  private var sourcesUnannounced = true

  /**
   * F7.31: dove il vetro è disegnato rispetto al suo layout (un pomello spostato nel disegno, `glassDrawOffset`): la sonda
   * legge sotto il vetro che si vede. Riletto a ogni giro, nelle letture osservate: lo spostamento che cambia la sveglia.
   */
  private val placement = GlassSamplePlacement()

  /** La parte del nodo sotto cui leggere (F2.3b): vedi [glassProbe]. */
  internal var region: (() -> Rect?)? = null

  private var reader: GlassProbeWindowReader? = null
  private var slot = -1

  /** Vero solo dove la sonda non può esistere (sotto Android 10): per sempre. I lettori mancati sono passeggeri. */
  private var unavailable = false

  /** F7.25b · Lettori mancati di fila (caselle piene, sfratto, lettore che non nasce) e quando si può riprovare. */
  private var readerMisses = 0
  private var readerRetryAt = Long.MIN_VALUE

  override var thumbnailLayer: GraphicsLayer? = null
    private set
  override var priority: Float = 0f
    private set
  private var thumbnailDirty = true
  private var recordedGeometry: FloatArray? = null
  private var lastGeometry: FloatArray? = null

  /** G1, `flipscroll=ahead`: di quanti pixel la lettura guarda avanti adesso, e con quanto è registrata la miniatura. */
  private var aheadPx = 0f
  private var recordedAheadPx = 0f

  private var pumpPosted = false
  private var revisionSeen = false
  private var timerAt = Long.MAX_VALUE

  /**
   * F7.25e (review R3, difetto 1) · Se la sonda è iscritta alle sorgenti ([subscribe]). Si iscrive quando il suo vetro è
   * posato (il primo [onGloballyPositioned] dopo l'attacco), non quando si attacca. Un vetro composto e non posato non ha
   * una posizione e non può leggere: la bolla dello scorrimento rapido a maniglia libera (F7.25, `ScrubberBubbleHost`).
   * Prima la sua sonda si iscriveva alla pagina appena attaccata, e a ogni ridisegno della pagina chiedeva una lettura. Il
   * lettore della finestra nasceva per lei, il ping andava al RenderThread, e col RenderThread indietro c'erano l'attesa
   * breve (fino a 1 ms di principale) e il tetto. Poi la lettura finiva a vuoto, senza miniatura. Da non iscritta non
   * sente le revisioni e non conta fra i lettori della pagina.
   */
  private var subscribed = false

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
      // F7.25e: un vetro non ancora posato si iscrive al suo primo posizionamento.
      if (isAttached && !unavailable && coordinates != null) {
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
    rest = GlassProbeRest()
    thumbnailLayer = requireGraphicsContext().createGraphicsLayer()
    thumbnailDirty = true
    recordedGeometry = null
    lastGeometry = null
    unavailable = false
    readerMisses = 0
    readerRetryAt = Long.MIN_VALUE
    placement.invalidate()
    FluidGlassProbeDiagnostics.onProbeAttached(1)
    // F7.25e: l'iscrizione e il primo segnale arrivano col primo posizionamento ([onGloballyPositioned]).
  }

  override fun onDetach() {
    FluidGlassProbeDiagnostics.onProbeAttached(-1)
    unsubscribe()
    handler.removeCallbacks(pumpMessage)
    handler.removeCallbacks(timerMessage)
    pumpPosted = false
    timerAt = Long.MAX_VALUE
    releaseSlot()
    thumbnailLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
    thumbnailLayer = null
    coordinates = null
    placement.clear()
    scheduler = null
    rest = null
    view = null
    quality = null
  }

  override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
    this.coordinates = coordinates
    // F7.25e: posato, adesso può leggere.
    if (!subscribed && !unavailable) subscribe()
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
    // Una sonda autonoma legge anche senza un DrawBackdropNode. La scena del foglio e le
    // trasformazioni possono disegnare layer diversi da quelli che annunciano le revisioni.
    val reads = mutableSetOf<LayerBackdrop>()
    backdrop.forEachPart { if (it is LayerBackdrop && it.onDemand) reads.add(it) }
    sampledLayers = reads
    for (layer in reads) layer.addReader()
    subscribed = true
  }

  private var sampledLayers: Set<LayerBackdrop> = emptySet()

  private fun unsubscribe() {
    for (layer in sampledLayers) layer.removeReader()
    sampledLayers = emptySet()
    for (layer in sources) layer.removeRevisionListener(revisionListener)
    sources = emptyList()
    subscribed = false
  }

  /** Il giro del messaggio: consuma i segnali, chiede allo schedulatore, fa partire la lettura o arma l'attesa. */
  private fun pump() {
    // F7.25e: senza una posizione non c'è niente da leggere (e senza iscrizione non arrivano revisioni).
    if (!isAttached || unavailable || coordinates == null) return
    val scheduler = scheduler ?: return
    val now = SystemClock.uptimeMillis()
    // Le letture osservate: dove stanno le sorgenti (`layerCoordinates` cambia a ogni loro posizionamento) e la qualità.
    observeReads {
      for (layer in sources) layer.layerCoordinates
      quality?.level
      region?.invoke()
      placement.update(this, requireDensity(), Size.Zero, null, coordinates)
    }
    if (revisionSeen) {
      revisionSeen = false
      scheduler.onRevision(now)
      rest?.onRevision(now)
    }
    val moved = geometryMovement()
    if (moved != null) {
      scheduler.onGeometry(now, moved)
      rest?.onMoved()
    }
    val unannounced = sourcesUnannounced || sources.any { !it.announcesRevisions }
    scheduler.setUnannounced(unannounced)
    // F7.4: il contenuto che scorre sotto il vetro non lascia riposare la sonda, anche dove due letture tornano uguali.
    if (contentScrolling(now)) rest?.onMoved()
    val suspended = (quality?.level ?: 1f) < GlassProbeQualityFloor
    scheduler.setSuspended(suspended)
    // G1: le varianti «cosa fa il vetro mentre scorri» (`flipscroll`). Di serie (`live`) non si legge niente qui.
    val scrollWake = if (state.scroll == GlassFlipScroll.Live) Long.MAX_VALUE else followScroll(now)
    if (state.advance(now)) {
      // Il filtro ha girato alla sua scadenza, senza una lettura nuova: chi ascolta lo sa con l'ultima lettura.
      state.stats?.let { last -> onReading?.invoke(last, state.target, false) }
    }
    var wakeAt = min(state.nextDeadlineMillis(), scrollWake)
    when (val decision = scheduler.next(now)) {
      is GlassProbeDecision.Read -> {
        // F7.4: una lettura chiesta da una revisione aspetta, se le ultime sono tornate uguali. Non una geometria nuova
        // (la miniatura da rifare), non la cadenza fissa delle sorgenti che non avvisano.
        val rested = glassProbeRestGate(decision, rest, unannounced, GlassFieldLevers.probeRest)
        // F7.25b: dopo un lettore mancato si riprova solo passata la sua attesa (un evento arrivato prima la aspetta).
        val notBefore = max(rested, readerRetryAt)
        if (now < notBefore) {
          if (now < rested) FluidGlassProbeDiagnostics.onWake(GlassProbeWake.Rested)
          wakeAt = min(wakeAt, notBefore)
        } else {
          startRead(now, decision.rerecord)
        }
      }
      is GlassProbeDecision.WaitUntil -> wakeAt = min(wakeAt, decision.atMillis)
      GlassProbeDecision.Idle ->
        if (suspended && scheduler.hasPending) wakeAt = min(wakeAt, now + GlassProbeSuspendedRecheckMillis)
    }
    armTimer(now, wakeAt)
  }

  /**
   * G1, `flipscroll=rest|ahead`: legge il moto delle sorgenti ([LayerBackdrop.scrollMotion]). Con `rest` trattiene il
   * lato mentre il contenuto scorre più svelto di [GlassFlipRestSpeedDp]; con `ahead` sposta la lettura verso la fascia
   * che sta entrando. Dice quando ripassare (il contenuto si considera fermo [GlassFlipQuietMillis] dopo l'ultimo passo),
   * o `Long.MAX_VALUE`.
   */
  private fun followScroll(now: Long): Long {
    var velocity = 0f
    var lastAt = Long.MIN_VALUE
    for (layer in sources) {
      val motion = layer.scrollMotion
      val v = motion.velocityAt(now)
      if (abs(v) > abs(velocity)) velocity = v
      lastAt = max(lastAt, motion.lastAtMillis)
    }
    when (state.scroll) {
      GlassFlipScroll.Rest -> state.filter.holding = abs(velocity) > GlassFlipRestSpeedDp * requireDensity().density
      GlassFlipScroll.Ahead -> {
        val ahead = -velocity * GlassFlipAheadMillis / 1000f
        if (ahead != aheadPx) {
          aheadPx = ahead
          // Una lettura nuova con la fascia nuova (lo schedulatore le raccoglie: al più una ogni 120 ms).
          if (ahead != recordedAheadPx) scheduler?.onRevision(now)
        }
      }
      GlassFlipScroll.Live -> Unit
    }
    if (lastAt == Long.MIN_VALUE) return Long.MAX_VALUE
    val quietAt = lastAt + GlassFlipQuietMillis
    return if (quietAt > now) quietAt else if (velocity != 0f) now + GlassFlipQuietMillis else Long.MAX_VALUE
  }

  /**
   * G1, `flipscroll=ahead`: lo spostamento verticale della lettura dentro le sorgenti, al più
   * [GlassFlipAheadMaxHeights] altezze del vetro e mai fuori dalla registrazione (fuori non c'è niente da leggere: sotto
   * la tab bar in fondo allo schermo, sopra la barra in cima).
   */
  private fun clampedAhead(self: LayoutCoordinates, area: Rect): Float {
    if (aheadPx == 0f) return 0f
    val reach = GlassFlipAheadMaxHeights * area.height
    var ahead = aheadPx.coerceIn(-reach, reach)
    for (layer in sources) {
      val source = layer.layerCoordinates ?: return 0f
      if (!source.isAttached) return 0f
      val top = try {
        source.localPositionOf(self).y + area.top
      } catch (_: Exception) {
        return 0f
      }
      val lowest = -top
      val highest = source.size.height - top - area.height
      ahead = if (lowest > highest) 0f else ahead.coerceIn(lowest, highest)
    }
    return ahead
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
    val fake = FluidGlassProbeDiagnostics.readerForTesting
    val fakeAt = FluidGlassProbeDiagnostics.readerInWindowForTesting
    if (fake != null || fakeAt != null) {
      // F7.33b: il lettore finto che sa dove legge la sonda (la parte letta, in pixel della finestra). F7.33c: cella per
      // cella, come la miniatura vera, così un contenitore vede che lato ha sotto ogni grappolo.
      val where = if (fakeAt != null) probedWindowRect() else null
      scheduler.onReadSent(now, rerecord)
      rest?.onReadSent(now)
      FluidGlassProbeDiagnostics.onWake(if (rerecord) GlassProbeWake.ReadRerecord else GlassProbeWake.Read)
      FluidGlassProbeDiagnostics.onRequest()
      handler.post {
        if (isAttached) {
          val stats = if (fakeAt != null && where != null) glassProbeStatsOfRegion(where, fakeAt)
          else glassProbeUniformStats(fake?.invoke() ?: Color.Black)
          onProbeResult(GlassProbeOutcome.Read, stats)
        }
      }
      return
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
      unavailable = true
      unsubscribe()
      return
    }
    val reader = obtainReader()
    if (reader == null) {
      // Nessun lettore adesso (caselle finite, finestra non ancora accelerata, lettore che non è nato). F7.25b (review R2,
      // difetto 5): prima la sonda si spegneva per tutta la vita del nodo, e una tab bar restava sul lato del tema anche
      // quando le caselle si liberavano. Ora resta iscritta alle sorgenti e riprova al primo evento dopo un'attesa che
      // cresce; intanto tiene l'ultimo lato (o quello del tema, se non ha mai letto).
      onReaderMissed(now)
      return
    }
    readerMisses = 0
    readerRetryAt = Long.MIN_VALUE
    scheduler.onReadSent(now, rerecord)
    rest?.onReadSent(now)
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
      if (readerMisses == 0) {
        Log.w(GlassProbeLogTag, "Sonda dello sfondo: più di $GlassProbeMaxSlots vetri radice in una finestra, questo riprova quando c'è posto")
      }
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
      self.localToWindow(placement.origin)
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
    // Un vetro più importante ha preso la casella: questo resta con l'ultima lettura (nessuno scatto). F7.25b (review R2,
    // difetto 5): non si spegne più per sempre, riprova al primo evento dopo un'attesa che cresce.
    reader = null
    slot = -1
    FluidGlassProbeDiagnostics.onSlotRefused()
    if (readerMisses == 0) {
      Log.w(GlassProbeLogTag, "Sonda dello sfondo: casella ceduta a un vetro più grande o più centrale, questo tiene l'ultimo lato e riprova")
    }
    val now = SystemClock.uptimeMillis()
    // Una lettura chiesta e non tornata non tornerà più a questa sonda (la casella ha un'altra generazione): lo
    // schedulatore la conta saltata, così l'evento resta in attesa e la sonda non resta «in volo» per sempre.
    scheduler?.let { if (it.inFlight) it.onReadDone(now, GlassProbeOutcome.Skipped) }
    onReaderMissed(now)
  }

  /** F7.25b · Un lettore mancato: si riprova al primo evento dopo [glassProbeReaderRetryMillis]. */
  private fun onReaderMissed(now: Long) {
    readerMisses++
    readerRetryAt = now + glassProbeReaderRetryMillis(readerMisses)
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
    val origin = placement.origin
    if (sources.isEmpty()) {
      val position = self.localToWindow(origin)
      out[2] = position.x + area.left
      out[3] = position.y + area.top
      return out
    }
    for ((index, layer) in sources.withIndex()) {
      val sourceCoordinates = layer.layerCoordinates ?: return null
      if (!sourceCoordinates.isAttached) return null
      val offset = try {
        sourceCoordinates.localPositionOf(self, origin)
      } catch (_: Exception) {
        return null
      }
      out[2 + 2 * index] = offset.x + area.left
      out[3 + 2 * index] = offset.y + area.top
    }
    return out
  }

  /** F7.33b · La parte letta in pixel della finestra, per il lettore finto delle prove; null se non si sa. */
  private fun probedWindowRect(): Rect? {
    val self = coordinates ?: return null
    if (!self.isAttached) return null
    val area = probedArea(self) ?: return null
    val position = try {
      self.positionInWindow()
    } catch (_: Exception) {
      return null
    }
    return area.translate(position)
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

  /**
   * Il contenuto di una sorgente è scorso da poco ([GlassProbeRestFreshMillis]): lo dicono gli scorrimenti annunciati
   * ([LayerBackdrop.scrollMotion], gli stessi che legge `flipscroll`). F7.4: mentre scorre, la sonda non riposa.
   */
  private fun contentScrolling(now: Long): Boolean {
    for (layer in sources) {
      val at = layer.scrollMotion.lastAtMillis
      if (at != Long.MIN_VALUE && now - at <= GlassProbeRestFreshMillis) return true
    }
    return false
  }

  override fun prepareThumbnail(): Boolean {
    if (!isAttached) return false
    val self = coordinates ?: return false
    if (!self.isAttached || !backdrop.isReadyToSample()) return false
    val geometry = currentGeometry() ?: return false
    val layer = thumbnailLayer ?: return false
    val recorded = recordedGeometry
    // G1, `flipscroll=ahead`: la fascia che sta entrando (di serie sempre zero).
    val ahead = if (state.scroll == GlassFlipScroll.Ahead) probedArea(self)?.let { clampedAhead(self, it) } ?: 0f else 0f
    if (thumbnailDirty || recorded == null || !recorded.contentEquals(geometry) || ahead != recordedAheadPx) {
      val density = requireDensity()
      val area = (probedArea(self) ?: return false).let { if (ahead != 0f) it.translate(0f, ahead) else it }
      recordedAheadPx = ahead
      val scaleX = GlassProbeReadSide / area.width
      val scaleY = GlassProbeReadSide / area.height
      val source = backdrop
      layer.record(density, LayoutDirection.Ltr, IntSize(GlassProbeReadSide, GlassProbeReadSide)) {
        scale(scaleX, scaleY, pivot = Offset.Zero) {
          translate(left = -area.left, top = -area.top) {
            with(source) { drawBackdrop(density, self, placement.blockFor(null)) }
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
      rest?.onReading(stats)
      FluidGlassProbeDiagnostics.onReading()
      onReading?.invoke(stats, state.target, true)
    } else if (outcome == GlassProbeOutcome.Failed) {
      FluidGlassProbeDiagnostics.onFailure()
    }
    // Dopo una lettura saltata non si riparte subito: un nuovo tentativo salterebbe di nuovo, e il prossimo evento (la
    // finestra che torna e ridisegna, la sorgente che avvisa, il vetro che si posa) arriva comunque. F7.25b (review R2,
    // difetto 7): non per una sorgente che non avvisa (un `CanvasBackdrop`), che a pagina ferma non manda eventi: per lei
    // il timer della cadenza fissa, finché il tetto delle letture a vuoto lo permette.
    if (outcome != GlassProbeOutcome.Skipped) {
      signal()
    } else {
      armTimer(now, scheduler.afterSkipMillis(now))
    }
  }

  override fun onReaderLost() {
    reader = null
    slot = -1
    if (isAttached) onProbeResult(GlassProbeOutcome.Failed, null)
  }
}

// ------------------------------------------------------------------------------------------------------------
// La sorgente che si ridisegna (F3.8a)
// ------------------------------------------------------------------------------------------------------------

/**
 * Fa avvisare [layer] ([LayerBackdrop.bumpRevision]) ogni volta che **il contenuto della sorgente si ridisegna**, anche
 * quando il nodo che la registra non lo fa. Va posato come ultimo modificatore di una sorgente di vetro, **dentro** il
 * suo layer fuori schermo (`glassBackdropSource`: dopo `graphicsLayer(compositingStrategy = Offscreen)`).
 *
 * Perché serve (S3, F3-g: «la sonda non sente un fondo che cambia solo in disegno»). Una sorgente è
 * `layerBackdrop(layer).graphicsLayer(Offscreen)`: tutto ciò che la pagina disegna sta nel layer fuori schermo, e il nodo
 * che registra e avvisa sta fuori, nel layer di chi contiene la sorgente. Quando la pagina cambia solo in disegno (un
 * fondo che passa da chiaro a scuro, un colore che si anima, un testo che cambia senza cambiare misura) Compose rifà il
 * layer fuori schermo e nient'altro: se la sorgente sta a sua volta dentro il layer di un antenato (una pagina sotto
 * `fluidGlassModalObscured`, dentro una transizione, dentro la registrazione della chrome), il nodo che registra non
 * ridisegna, nessuno avvisa, e nessuna sonda rilegge. I vetri sopra restano sul lato di prima: nelle scene
 * `adattamento` e `clear` del piano flottante e nella pagina «Assistente» non girava niente, né il contenitore né i tasti
 * a sé. Dove nessun antenato ha un layer il nodo ridisegna a ogni fotogramma della finestra, e il difetto non si vede:
 * per questo in «Adattività» girava (lì una sorgente sorella senza layer suo sporcava il layer dell'antenato).
 *
 * Questo nodo sta nel layer fuori schermo, quindi ridisegna esattamente quando lui si rifà, e avvisa. Lo scorrimento di
 * un figlio e un figlio con un layer suo restano come prima (`GlassScrollAnnouncer`, `GlassBackdropState.notifyChanged`).
 * Non costa letture a pagina ferma: senza ridisegni non c'è avviso, e lo schedulatore raccoglie quelli di un'animazione
 * (al più una lettura ogni 120 ms).
 *
 * Montato da `glassBackdropSource` dalla 4.0 (F3.10): le pagine non annunciano più a mano (`notifyChanged()` nel disegno
 * del fondo, il ripiego di F3.8a). [frozen] è quello della sorgente: tenuta ferma (uno scorrimento veloce) la sorgente non
 * si registra e non avvisa, e nemmeno questo nodo, come il nodo che la registra.
 */
internal fun Modifier.glassSourceRedraws(layer: LayerBackdrop, frozen: () -> Boolean = NeverFrozen): Modifier =
  this then GlassSourceRedrawElement(layer, frozen)

private val NeverFrozen: () -> Boolean = { false }

private class GlassSourceRedrawElement(
  val layer: LayerBackdrop,
  val frozen: () -> Boolean,
) : ModifierNodeElement<GlassSourceRedrawNode>() {
  override fun create(): GlassSourceRedrawNode = GlassSourceRedrawNode(layer, frozen)

  override fun update(node: GlassSourceRedrawNode) {
    node.layer = layer
    node.frozen = frozen
  }

  override fun equals(other: Any?): Boolean =
    this === other || other is GlassSourceRedrawElement && other.layer == layer && other.frozen === frozen

  override fun hashCode(): Int = 31 * layer.hashCode() + System.identityHashCode(frozen)

  override fun InspectorInfo.inspectableProperties() {
    name = "glassSourceRedraws"
  }
}

/** Vedi [glassSourceRedraws]. */
internal class GlassSourceRedrawNode(
  var layer: LayerBackdrop,
  var frozen: () -> Boolean = NeverFrozen,
) : Modifier.Node(), DrawModifierNode {
  override fun ContentDrawScope.draw() {
    drawContent()
    if (!frozen()) announce()
  }

  /** Il contenuto della sorgente è stato appena ridisegnato: chi la legge lo deve sapere. Dal disegno: lascia un segnale. */
  fun announce() {
    FluidGlassProbeDiagnostics.onSourceRedraw()
    layer.bumpRevision()
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

  // F7.25b (review R2, difetto 6) · Le parti che tengono una superficie, un renderer o un filo nascono in un blocco solo:
  // se una non nasce, quelle già nate si chiudono prima di rilanciare. Prima il filo partiva per primo e, con il renderer
  // rifiutato, restava aperto per la vita del processo, insieme all'`ImageReader`: uno per vetro radice che riprovava.
  // Il filo parte per ultimo, dopo le parti che possono essere rifiutate; poi il ping del RenderThread (F7.25).
  private val reader: ImageReader
  private val rootNode: RenderNode
  private val renderer: HardwareRenderer
  private val thread: HandlerThread
  private val worker: Handler
  private val fence: GlassProbeFence?

  init {
    var image: ImageReader? = null
    var node: RenderNode? = null
    var hardware: HardwareRenderer? = null
    var started: HandlerThread? = null
    var ping: GlassProbeFence? = null
    try {
      val newImage = ImageReader.newInstance(imageWidth, imageHeight, PixelFormat.RGBA_8888, MaxImages).also { image = it }
      val newNode = RenderNode("FluidGlass.sonda").also {
        it.setPosition(0, 0, imageWidth, imageHeight)
        node = it
      }
      constructionStepForTesting?.invoke(StepRenderer)
      HardwareRenderer().also {
        hardware = it
        it.setName("FluidGlass.sonda")
        it.setContentRoot(newNode)
        it.setSurface(newImage.surface)
        // Una casella senza sonda deve leggersi vuota, non sporca del fotogramma di prima.
        it.isOpaque = false
      }
      constructionStepForTesting?.invoke(StepThread)
      started = HandlerThread("FluidGlass.sonda").apply { start() }
      constructionStepForTesting?.invoke(StepPing)
      ping = fenceForTesting?.invoke { seq -> onPingReturned(seq) }
        ?: if (Build.FINGERPRINT == "robolectric") null else GlassProbeRenderThreadPing { seq -> onPingReturned(seq) }
    } catch (error: Throwable) {
      ping?.release()
      started?.quitSafely()
      hardware?.destroy()
      node?.discardDisplayList()
      image?.close()
      throw error
    }
    reader = checkNotNull(image)
    rootNode = checkNotNull(node)
    renderer = checkNotNull(hardware)
    thread = checkNotNull(started)
    worker = Handler(thread.looper)
    fence = ping
  }
  private val drawScope = CanvasDrawScope()
  private val clients = arrayOfNulls<GlassProbeClient>(GlassProbeMaxSlots)
  private val generations = IntArray(GlassProbeMaxSlots)
  private val pending = ConcurrentLinkedQueue<Batch>()
  private var requested = 0L
  private var watchdogPosted = false

  // Del filo di lavoro soltanto.
  private val reducer = GlassProbeStatsReducer(side, GlassProbeStatsSide)
  private var row = ByteArray(0)

  @Volatile
  private var released = false

  /** Vero finché il lettore si può usare. */
  val isAlive: Boolean get() = !released

  private val watchdog = Runnable { checkStuck() }

  // F7.25 · Quando chiedere: solo col RenderThread libero (`GlassProbeTiming.kt`). `syncAndDraw` aspetta che il
  // RenderThread finisca quello che sta facendo. F7.22 chiedeva al commit della finestra e, se intanto la finestra aveva
  // già disegnato un fotogramma più nuovo, dopo tre rinvii chiedeva subito dopo il disegno (la fase `post` di S1-bis):
  // sul Tab S9, nel PDF di 3000 pagine, 4,5–5,7 ms di thread principale fermo a lettura, a ogni lettura della maniglia.
  // Ora un ping ([GlassProbeFence]) dice quando il RenderThread ha finito il fotogramma della finestra, e si legge lì:
  // fra due fotogrammi, o al pre-disegno di quello dopo; se non ha finito si salta un fotogramma, e il vetro tiene la
  // lettura di prima ([GlassProbeReadGate]: al più [GlassProbeCeilingNanos], 250 ms dalla F7.25d).
  // Il ping ([fence]) nasce nel blocco del costruttore, con le altre parti che vanno chiuse se una non nasce.
  private val gate = GlassProbeReadGate()
  private val frameCallback = Choreographer.FrameCallback { onFrame() }
  private val afterFrame = Runnable { onAfterFrame() }
  private val preDraw = ViewTreeObserver.OnPreDrawListener {
    atPreDraw()
    true
  }
  private var preDrawObserver: ViewTreeObserver? = null

  /** Una lettura aspetta il suo momento (o, senza ping, il disegno della finestra come prima). */
  private var waiting = false
  private var pingSent = 0

  /** `drawingTime` della finestra quando è partito l'ultimo ping: se cambia, la finestra ha disegnato dopo. */
  private var pingDrawingTime = Long.MIN_VALUE

  /** Il nome della sezione di traccia della prossima `syncAndDraw`: da quale strada parte (F7.22, F7.25). */
  private var sendSection = SectionLegacy

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
    if (!waiting) startWaiting()
    return true
  }

  /** F7.25 · Una lettura comincia ad aspettare il RenderThread libero (o, senza ping, il disegno della finestra). */
  private fun startWaiting() {
    waiting = true
    val ping = fence
    if (ping == null || !ping.healthy || !root.isAttachedToWindow) {
      // Robolectric (nessun RenderThread), o il ping non è nato: subito dopo il disegno della finestra, come prima.
      Choreographer.getInstance().postFrameCallback(legacyFrameCallback)
      return
    }
    gate.begin(glassProbeNowNanos())
    // Il RenderThread può avere ancora in mano un fotogramma disegnato prima: un ping dietro di lui.
    sendPing()
    root.viewTreeObserver.let { observer ->
      if (observer.isAlive) {
        observer.addOnPreDrawListener(preDraw)
        preDrawObserver = observer
      }
    }
    Choreographer.getInstance().postFrameCallback(frameCallback)
  }

  /** La lettura è partita (o il lettore chiude): niente più fotogrammi, ping né pre-disegni per lei. */
  private fun stopWaiting() {
    waiting = false
    Choreographer.getInstance().removeFrameCallback(frameCallback)
    Choreographer.getInstance().removeFrameCallback(legacyFrameCallback)
    main.removeCallbacks(afterFrame)
    main.removeCallbacks(legacyAfterDraw)
    preDrawObserver?.let { observer -> if (observer.isAlive) observer.removeOnPreDrawListener(preDraw) }
    preDrawObserver = null
  }

  private fun sendPing() {
    pingSent++
    pingDrawingTime = root.drawingTime
    fence?.ping(pingSent)
  }

  /** Il RenderThread è libero: il ping dell'ultimo fotogramma della finestra è tornato, e lei non ne ha disegnato altri. */
  private fun renderThreadFree(): Boolean = (fence?.returned ?: 0) >= pingSent && root.drawingTime == pingDrawingTime

  /** Nella fase delle animazioni di ogni fotogramma, mentre una lettura aspetta. */
  private fun onFrame() {
    if (!waiting || released) return
    gate.onFrame(glassProbeNowNanos())
    main.post(afterFrame)
  }

  /** Dopo il fotogramma sul principale: se la finestra ha disegnato, un ping dietro il suo fotogramma; se no, si decide. */
  private fun onAfterFrame() {
    if (!waiting || released) return
    if (root.drawingTime != pingDrawingTime) {
      sendPing()
    } else {
      // Un fotogramma senza disegno della finestra: fra due fotogrammi, il principale libero.
      decide(beforeDraw = false)
      if (!waiting) return
    }
    Choreographer.getInstance().postFrameCallback(frameCallback)
  }

  /** Il pre-disegno della finestra (prima che registri e sincronizzi il suo fotogramma). */
  private fun atPreDraw() {
    if (!waiting || released) return
    decide(beforeDraw = true)
  }

  /** Il ping [seq] è tornato, sul principale e fra due fotogrammi: se la finestra non ha disegnato altro, si legge. */
  private fun onPingReturned(seq: Int) {
    if (!waiting || released || seq != pingSent || root.drawingTime != pingDrawingTime) return
    read(SectionFree, beforeDraw = false)
  }

  /** La regola dei fotogrammi saltati ([GlassProbeReadGate]): leggere adesso, aspettare poco, o saltare. */
  private fun decide(beforeDraw: Boolean) {
    val free = renderThreadFree()
    when (gate.verdict(free)) {
      GlassProbeGateVerdict.ReadNow -> read(if (beforeDraw) SectionBeforeDraw else SectionFree, beforeDraw)
      GlassProbeGateVerdict.Skip -> Unit
      GlassProbeGateVerdict.WaitBriefly -> {
        val returned = fence?.await(pingSent, GlassProbeBriefWaitNanos) == true
        if (returned && root.drawingTime == pingDrawingTime) read(SectionAwaited, beforeDraw)
      }
      GlassProbeGateVerdict.ReadAnyway -> read(SectionCeiling, beforeDraw)
    }
  }

  /**
   * Si legge, dalla strada [section]. Prima di un disegno della finestra ([beforeDraw]) la radice si invalida sempre:
   * il disegno di questo stesso fotogramma ha il danno intero, e ciò che la lettura ha sincronizzato per prima (le
   * proprietà dei nodi condivisi cambiate dalla misura) non si perde. Fra due fotogrammi si invalida se la finestra aveva
   * già qualcosa da ridisegnare (`isDirty`, come al commit di F7.22): il suo fotogramma dopo si ridisegna tutto.
   */
  private fun read(section: String, beforeDraw: Boolean) {
    val frames = gate.frames
    stopWaiting()
    val pendingRedraw = beforeDraw || root.isDirty
    sendSection = section
    android.os.Trace.setCounter(CounterFrames, frames.toLong())
    FluidGlassProbeDiagnostics.onTimedRead(section, frames)
    if (send() && pendingRedraw) root.invalidate()
  }

  private val legacyAfterDraw = Runnable {
    waiting = false
    sendSection = SectionLegacy
    send()
  }
  private val legacyFrameCallback = Choreographer.FrameCallback { main.post(legacyAfterDraw) }

  /** Registra ciò che serve e chiede il fotogramma del lettore. Vero se la `syncAndDraw` è partita. */
  private fun send(): Boolean {
    if (released) return false
    val asked = requested
    if (asked == 0L) return false
    if (pending.size >= MaxInFlight) {
      // Le richieste restano: le riprende l'arrivo dell'immagine in volo (vedi `deliver`).
      return false
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
    if (drawable == 0L) return false
    recordRoot(drawable)
    val batch = Batch(drawable, generations.copyOf(), SystemClock.uptimeMillis())
    pending.add(batch)
    val result = try {
      // F7.22, F7.25: una sezione per strada, per leggere nelle tracce Perfetto quanto aspetta (GlassProbeTiming.kt).
      android.os.Trace.beginSection(sendSection)
      try {
        renderer.createRenderRequest().setVsyncTime(System.nanoTime()).syncAndDraw()
      } finally {
        android.os.Trace.endSection()
      }
    } catch (error: RuntimeException) {
      Log.w(GlassProbeLogTag, "Sonda dello sfondo: syncAndDraw ha lanciato ($error)")
      RefusedSync
    }
    if (result and RefusedSync != 0) {
      // Nessun fotogramma, quindi nessuna immagine: la richiesta non resta in coda ad aspettarla.
      pending.remove(batch)
      fail(batch)
      // La sincronizzazione può esserci stata anche se il fotogramma è rifiutato: chi chiama invalida comunque.
      return true
    }
    FluidGlassProbeDiagnostics.onRequest()
    if (!watchdogPosted) {
      watchdogPosted = true
      main.postDelayed(watchdog, GlassProbeReadTimeoutMillis)
    }
    return true
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
    if (requested != 0L && !waiting) startWaiting()
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
    stopWaiting()
    fence?.release()
    main.removeCallbacks(watchdog)
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

    /**
     * F7.25 · Le sezioni di traccia della lettura, una per strada (`GlassProbeTiming.kt`): «libera» fra due fotogrammi
     * col ping tornato; «pre-disegno» prima del disegno della finestra col ping tornato; «attesa» dopo l'attesa breve
     * del ping; «al tetto» dopo [GlassProbeCeilingNanos] (250 ms); «come prima» senza ping (Robolectric), subito
     * dopo il disegno della finestra. Il contatore dice quanti fotogrammi la lettura ha aspettato.
     */
    internal const val SectionFree = "Glass.sonda libera"
    internal const val SectionBeforeDraw = "Glass.sonda pre-disegno"
    internal const val SectionAwaited = "Glass.sonda attesa"
    internal const val SectionCeiling = "Glass.sonda al tetto"
    internal const val SectionLegacy = "Glass.sonda come prima"
    private const val CounterFrames = "Glass.sonda fotogrammi saltati"

    /**
     * F7.25 · Per le prove: un ping finto al posto di [GlassProbeRenderThreadPing] (che in Robolectric non c'è). Riceve
     * la funzione da chiamare, sul principale, quando un ping torna. Null di serie.
     */
    @Volatile
    internal var fenceForTesting: ((onReturn: (Int) -> Unit) -> GlassProbeFence)? = null

    /**
     * F7.25b · Per le prove (R2, difetti 5 e 6): chiamata a ogni passo della costruzione del lettore col nome del passo;
     * se lancia, la costruzione fallisce lì, come su un dispositivo che rifiuta il renderer. Null di serie.
     */
    @Volatile
    internal var constructionStepForTesting: ((step: String) -> Unit)? = null
    internal const val StepRenderer = "renderer"
    internal const val StepThread = "filo"
    internal const val StepPing = "ping"

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
/**
 * `Rested` (F7.4): un giro in cui lo schedulatore avrebbe letto e la sonda ha aspettato perché le sue ultime letture
 * erano uguali ([GlassProbeRest]). Uno per giro, non per lettura risparmiata: con una pagina che si ridisegna a ogni
 * fotogramma cresce di uno a fotogramma.
 */
internal enum class GlassProbeWake { Revision, Positioned, ObservedReads, Timer, Read, ReadRerecord, Rested }

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
  private val live = AtomicInteger()
  private val wakes = Array(GlassProbeWake.entries.size) { AtomicLong() }
  private val sourceRedraws = AtomicLong()

  /**
   * Quante volte il contenuto di una sorgente di vetro si è ridisegnato e l'ha detto alle sonde (F3.10, il nodo di
   * `glassSourceRedraws` dentro il layer di `glassBackdropSource`): un fondo che cambia solo in disegno.
   */
  val totalSourceRedraws: Long get() = sourceRedraws.get()

  internal fun onSourceRedraw() {
    sourceRedraws.incrementAndGet()
  }

  /**
   * Un **lettore finto**, per i test Robolectric (F3.10), dove il lettore vero non consegna mai l'immagine: con un valore,
   * ogni lettura che una sonda chiede torna subito (al messaggio dopo, sul filo principale) col colore medio che questa
   * funzione dice di vedere sotto il vetro in quel momento, uniforme. Le letture si contano come quelle vere
   * ([totalRequests], [totalReadings]). Null (di serie): il lettore vero. Non è un'API per le app.
   */
  @Volatile
  var readerForTesting: (() -> Color)? = null

  /**
   * F7.33b · Come [readerForTesting], ma la funzione riceve **dove** legge la sonda: la parte letta, in pixel della
   * finestra. Serve alle prove di uno sfondo misto (la carta da una parte, il tavolo scuro dall'altra), dove ogni sonda
   * vede il suo colore. Dalla F7.33c la funzione si chiama per ogni cella della miniatura (16 × 16 riquadri della parte
   * letta), come se la sonda leggesse davvero. Vince su [readerForTesting]. Null di serie. Non è un'API per le app.
   */
  @Volatile
  var readerInWindowForTesting: ((Rect) -> Color)? = null

  /**
   * Le sonde attaccate adesso, in tutto il processo (F3.2): un nodo di sonda in scena, letto o no. Un
   * `FluidGlassContainer` ne ha **una** per tutti i suoi pezzi; ogni vetro radice fuori da un contenitore la sua.
   */
  val liveProbes: Int get() = live.get()

  internal fun onProbeAttached(delta: Int) {
    live.addAndGet(delta)
  }

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
    sourceRedraws.set(0)
    readerForTesting = null
    readerInWindowForTesting = null
    requests.set(0)
    readings.set(0)
    failures.set(0)
    refused.set(0)
    peak.set(0)
    wakes.forEach { it.set(0) }
    timedPaths.clear()
    timedFrames.forEach { it.set(0) }
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

  // F7.25 · Le letture di `GlassProbeWindowReader` per strada, e quanti fotogrammi hanno aspettato il RenderThread
  // libero (`GlassProbeTiming.kt`). Per le prove: sul dispositivo le stesse cose sono nelle sezioni di traccia.
  private val timedPaths = java.util.concurrent.ConcurrentHashMap<String, AtomicLong>()
  // F7.25d: il tetto è 250 ms, cioè 30 fotogrammi a 120 Hz e 36 a 144: 64 caselle, l'ultima per «oltre».
  private val timedFrames = Array(64) { AtomicLong() }

  internal fun onTimedRead(section: String, frames: Int) {
    timedPaths.getOrPut(section) { AtomicLong() }.incrementAndGet()
    timedFrames[frames.coerceIn(0, timedFrames.size - 1)].incrementAndGet()
  }

  /** Le letture per strada (le sezioni di traccia `Glass.sonda …`), da [resetForTesting]. */
  internal fun timedReads(): Map<String, Long> = timedPaths.mapValues { it.value.get() }

  /** Quante letture hanno aspettato 0, 1, 2… fotogrammi (l'ultima casella: oltre il tetto). */
  internal fun timedFrames(): LongArray = LongArray(timedFrames.size) { timedFrames[it].get() }
}
