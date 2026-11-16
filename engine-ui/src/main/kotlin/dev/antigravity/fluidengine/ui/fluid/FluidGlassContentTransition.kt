package dev.antigravity.fluidengine.ui.fluid

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key as identity
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import dev.antigravity.fluidengine.ui.glass.field.GlassEdgeBlurKernel
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/*
 * Il contenuto di un vetro che cambia stato (piano F3.4, R7 A.6): chi esce si sfoca e svanisce, chi entra arriva
 * sfocato e si fa nitido, e i due si incrociano al massimo della sfocatura. Ciò che ha la stessa chiave nei due stati
 * non si tocca.
 *
 * Il movimento è descritto una volta sola, dalla funzione pura [GlassContentJourney.frame]: la usa il composable
 * [FluidGlassContentTransition] e la usano le tracce del banco (`GlassContentTraces`). Se la traccia è giusta e il
 * dispositivo no, il difetto è nel tempo, non nella forma (piano 4.3).
 *
 * I numeri di gusto stanno qui, in [GlassContentTiming], finché il contenitore di F3.2 non li porta in `FluidMotion`.
 */

/**
 * Il tempo e le manopole di un cambio di contenuto. Ogni istante delle curve è una **frazione di [durationMs]** (`u`),
 * perché R7 ha misurato la forma su clip di durate diverse (330 ms al telefono, 434 ms nella clip ingrandita di 16:02)
 * e la forma è la stessa: cambia solo T.
 *
 * Le chiavi delle curve vengono dalla tabella di R7 A.3 e dalla figura `r7/A4_curve_scambio_icone.png` (clip 16:02,
 * classe B, T ≈ 434 ms), fiducia alta salvo dove detto.
 *
 * @param durationMs T, l'istante in cui il contenuto nuovo è di nuovo nitido. Di serie **300 ms**, il bilancio di un
 *   cambio di contesto del piano ([FluidMotion.PlaneContextMillis]): vedi [GlassContentDefaults.DurationMs]. L'opacità
 *   del nuovo arriva a 1 poco dopo, a 1,15 T (R7 A.3, «contrasto pieno»).
 * @param peakBlur σ massima in frazione della **sagoma** del contenuto: 0,15 (R7: 0,14–0,17, su otto icone).
 */
@Immutable
internal data class GlassContentTiming(
  val durationMs: Float = GlassContentDefaults.DurationMs,
  val peakBlur: Float = GlassContentDefaults.PeakBlur,
) {
  init {
    require(durationMs > 0f && durationMs.isFinite()) { "durata non valida: $durationMs" }
    require(peakBlur >= 0f && peakBlur.isFinite()) { "sfocatura non valida: $peakBlur" }
  }
}

/**
 * Le costanti della grammatica del contenuto, con la loro scheda. Sono numeri di gusto: restano in questo file finché
 * non li porta `FluidMotion` (F3.2).
 */
internal object GlassContentDefaults {
  /**
   * T di serie: **300 ms**, quanto un cambio di contesto del piano ([FluidMotion.PlaneContextMillis]); un test le tiene
   * uguali. Fino a F3.8a erano 400, la manopola «consigliata» di R7 A.6 (300 / 400 / 450), a metà fra il telefono e la
   * clip ingrandita.
   *
   * Perché 300 (F3.8a, punto 7 di S3). Le due misure di Apple sono la stessa curva a due velocità: sulla clip ingrandita
   * di 16:02 (classe B, T ≈ 434 ms) le icone cominciano a sfocarsi a +67 ms dall'ultimo fotogramma fermo (f28857), il
   * massimo cade a +200–267 (f28863–f28865, le due icone insieme) e quelle nuove sono nitide a +400–434; sul telefono
   * (219 4:44, classe A, «la più affidabile per i tempi») tutto lo scambio dura 270–330 ms, e il massimo cade a metà:
   * 135–165 ms, che è ciò che S3 ha letto allineando sulla fase. La forma è la stessa (il massimo a 0,5 T), cambia T.
   * Il vetro del piano viaggia col tempo del telefono (300 ms): con T a 400 le icone toccavano il massimo a 200 ms e
   * finivano 100–160 ms dopo il vetro. Con la stessa T del vetro il massimo cade a 150 ms e l'icona nuova è nitida
   * quando il vetro è fermo a vista.
   */
  const val DurationMs: Float = 300f

  /** σ massima / altezza della sagoma (R7 A.2–A.3: 0,14–0,17 h, alta). */
  const val PeakBlur: Float = 0.15f

  /**
   * Quanta parte del riquadro dell'icona è sagoma: 0,75 (la stessa stima di `FluidMetrics.IconLensRatio`, R6). R7
   * misura σ sull'altezza della sagoma (icone alte 100–121 px in un vetro da 235 px), quindi su un riquadro da 26 dp
   * σ massima vale 0,15 · 0,75 · 26 ≈ 2,9 dp, cioè i 16–18 px misurati / 5,34 px per punto ≈ 3,0–3,4 pt. Il «3,6–4,4
   * dp» di R7 A.3 moltiplica 0,15 per il riquadro (26 dp) invece che per la sagoma: è più largo del 30%.
   */
  const val GlyphFraction: Float = 0.75f

  /** Il riquadro di riferimento di serie: l'icona di un'azione in barra o in capsula (`FluidMetrics.BarIconSize`). */
  val BlurReference: Dp = 26.dp

  /** u dove σ tocca il massimo: 0,5 T (R7 A.3: 200–234 ms su 434, «al 45–55% di T»). */
  const val PeakAt: Float = 0.5f

  /**
   * u in cui il contenuto nuovo comincia a esserci: 0,36 T. R7 A.3 lo vede a 167 ms su 434 (0,38 T) con α già 0,2 in
   * due posti su tre: parte poco prima, dal nulla.
   */
  const val EnterAt: Float = 0.36f

  /**
   * u in cui il vecchio è sparito del tutto: 0,66 T. R7 A1 scrive «0 a ≈0,62 T», ma a 0,62 T la figura A4 ha ancora
   * 0,05 / 0,1 / 0,35 nei tre posti: la coda corta porta via α ≈0,05, e allunga la convivenza delle due icone (sopra
   * 0,1 tutte e due) a 72 ms con T = 400 e 77 ms con T = 434, dentro i 70–100 ms di R7. Con 0,62 erano 55–60 ms.
   */
  const val ExitEnd: Float = 0.66f

  /** u in cui il nuovo ha l'opacità piena: 1,15 T (R7 A.3, «contrasto pieno a ≈1,15 T»). */
  const val EnterEnd: Float = 1.15f

  /** Finestra della tinta: si incrocia fra 0,25 T e 0,5 T, dentro la sfocatura (R7 A4, alta per il simbolo). */
  const val TintFrom: Float = 0.25f
  const val TintTo: Float = 0.5f

  /** Scala di chi esce (R7: 1 → 0,9 dopo 0,25 T; manopola **0,9** / 1,0). */
  const val ExitScale: Float = 0.9f

  /** Scala di partenza di chi entra al posto di un altro (R7 A.3: 0,93 → 1). */
  const val EnterScale: Float = 0.93f

  /** Scala di partenza di chi nasce senza predecessore (R7 A6: il «più», 0,75 → 1; fiducia media). */
  const val BornScale: Float = 0.75f
}

/** Che parte fa uno strato di contenuto nel cambio. */
internal enum class GlassContentRole {
  /** Il contenuto di prima: si sfoca, rimpicciolisce di poco e svanisce (R7 A1). */
  Exit,

  /** Il contenuto nuovo al posto di un altro: arriva sfocato a scala 0,93 e si fa nitido (R7 A1). */
  Enter,

  /** Il contenuto nuovo dove prima non c'era niente: come [Enter], ma da scala 0,75 (R7 A6). */
  Born,

  /** Fermo: nitido, opaco, a scala 1. È anche il contenuto con la stessa chiave nei due stati (R7 A3). */
  Rest,
}

/**
 * L'aspetto di uno strato in un fotogramma.
 *
 * @param blur σ della gaussiana **uniforme** (R7 A2: σx = σy, niente scie anche se il contenuto si sposta) in
 *   frazione dell'altezza della sagoma. Il composable la porta in pixel con il riquadro di riferimento.
 */
@Immutable
internal data class GlassContentLook(
  val alpha: Float,
  val scale: Float,
  val blur: Float,
) {
  companion object {
    val Sharp = GlassContentLook(alpha = 1f, scale = 1f, blur = 0f)
  }
}

/** Uno strato nel fotogramma: chi è (la chiave del suo contenuto), che parte fa, come appare. */
@Immutable
internal data class GlassContentLayerFrame(
  val id: Long,
  val key: Any?,
  val role: GlassContentRole,
  val look: GlassContentLook,
)

/**
 * Un fotogramma del cambio.
 *
 * @param layers gli strati da disegnare, dal basso in alto (chi esce sotto, chi entra sopra), compresi quelli a opacità
 *   zero che non sono ancora finiti: il composable li tiene composti per non ricomporli quando compaiono.
 * @param tint quanto il colore è passato dal vecchio al nuovo, 0..1: entra **dentro** la sfocatura (R7 A4), e vale per
 *   tutti gli strati insieme, così il colore arriva prima della forma.
 * @param finished nessuno strato si muove più: resta solo il contenuto d'arrivo, nitido.
 */
@Immutable
internal data class GlassContentFrame(
  val layers: List<GlassContentLayerFrame>,
  val tint: Float,
  val finished: Boolean,
)

/**
 * Una curva a chiavi con l'interpolazione cubica monotona di Fritsch e Carlson: passa per le chiavi misurate, non
 * supera mai i valori di due chiavi vicine (un'opacità non rimbalza sopra 1 né sotto 0) e ha derivata continua, quindi
 * nessuno scalino di velocità fra un tratto e l'altro. Prima della prima chiave e dopo l'ultima tiene il valore.
 */
internal class GlassContentCurve(private val us: FloatArray, private val values: FloatArray) {
  private val tangents = FloatArray(us.size)

  init {
    require(us.size == values.size && us.size >= 2) { "servono almeno due chiavi" }
    for (i in 1 until us.size) require(us[i] > us[i - 1]) { "chiavi non crescenti" }
    val n = us.size
    val slopes = FloatArray(n - 1) { (values[it + 1] - values[it]) / (us[it + 1] - us[it]) }
    // Agli estremi la curva parte e arriva ferma: un'opacità o una scala che si fermano non scattano.
    tangents[0] = 0f
    tangents[n - 1] = 0f
    for (i in 1 until n - 1) {
      tangents[i] = if (slopes[i - 1] * slopes[i] <= 0f) 0f else (slopes[i - 1] + slopes[i]) / 2f
    }
    for (i in 0 until n - 1) {
      if (slopes[i] == 0f) {
        tangents[i] = 0f
        tangents[i + 1] = 0f
        continue
      }
      val a = tangents[i] / slopes[i]
      val b = tangents[i + 1] / slopes[i]
      val h = a * a + b * b
      if (h > 9f) {
        val k = 3f / sqrt(h)
        tangents[i] = k * a * slopes[i]
        tangents[i + 1] = k * b * slopes[i]
      }
    }
  }

  fun at(u: Float): Float {
    if (u <= us.first()) return values.first()
    if (u >= us.last()) return values.last()
    var i = 0
    while (u > us[i + 1]) i++
    val h = us[i + 1] - us[i]
    val s = (u - us[i]) / h
    val s2 = s * s
    val s3 = s2 * s
    return (2f * s3 - 3f * s2 + 1f) * values[i] + (s3 - 2f * s2 + s) * h * tangents[i] +
      (-2f * s3 + 3f * s2) * values[i + 1] + (s3 - s2) * h * tangents[i + 1]
  }
}

/** `smoothstep` di GLSL: 0 prima di [from], 1 dopo [to], in mezzo una S con derivata nulla ai capi. */
internal fun glassContentSmoothstep(from: Float, to: Float, x: Float): Float {
  val t = ((x - from) / (to - from)).coerceIn(0f, 1f)
  return t * t * (3f - 2f * t)
}

/**
 * Le curve del contenuto, per ruolo, in funzione di `u` (frazione di T). Sono la sola descrizione del movimento: il
 * composable e le tracce le leggono da qui.
 */
internal object GlassContentCurves {

  /**
   * La salita di σ, comune a chi esce e a chi entra: una `smoothstep` fino al massimo a 0,5 T. Contro R7 A.3
   * (σ/h a u = 0,08 / 0,15 / 0,23 / 0,31 / 0,38: 0,02 / 0,04 / 0,065 / 0,10 / 0,13) dà 0,010 / 0,034 / 0,066 /
   * 0,101 / 0,128: l'andamento accelerato, con lo scarto più grande nel primo fotogramma, dove σ è sotto il pixel.
   */
  fun blurRise(u: Float): Float = glassContentSmoothstep(0f, GlassContentDefaults.PeakAt, u)

  /**
   * σ di chi entra dopo il massimo: scende quasi in linea retta a zero in T (R7 A1: 0,10–0,14 h a 0,62 T, 0,07 a
   * 0,77 T, ≤0,03 a T).
   */
  fun blurFall(u: Float): Float =
    (1f - (u - GlassContentDefaults.PeakAt) / (1f - GlassContentDefaults.PeakAt)).coerceIn(0f, 1f)

  /** σ in frazione del massimo per [role] al tempo [u]. */
  fun blur(role: GlassContentRole, u: Float): Float = when (role) {
    GlassContentRole.Rest -> 0f
    // Chi esce non torna nitido mentre svanisce: dopo il massimo resta al massimo finché non è sparito.
    GlassContentRole.Exit -> blurRise(u)
    GlassContentRole.Enter, GlassContentRole.Born ->
      if (u <= GlassContentDefaults.PeakAt) blurRise(u) else blurFall(u)
  }

  /**
   * L'opacità di chi esce (R7 A.3 e A4): 1 → 0,75 a 0,3 T, poi giù fino all'incrocio (α ≈ 0,35 a 0,47 T, dove la
   * somma con chi entra fa ≈0,7), 0,15 a 0,56 T e zero a 0,66 T.
   */
  val exitAlpha = GlassContentCurve(
    floatArrayOf(0f, 0.30f, 0.47f, 0.56f, GlassContentDefaults.ExitEnd),
    floatArrayOf(1f, 0.75f, 0.35f, 0.15f, 0f),
  )

  /** L'opacità di chi entra: da zero a 0,36 T, 0,35 all'incrocio, 0,6 a 0,62 T, 0,78 a 0,77 T, 0,9 a T, 1 a 1,15 T. */
  val enterAlpha = GlassContentCurve(
    floatArrayOf(GlassContentDefaults.EnterAt, 0.47f, 0.62f, 0.77f, 1f, GlassContentDefaults.EnterEnd),
    floatArrayOf(0f, 0.35f, 0.6f, 0.78f, 0.9f, 1f),
  )

  /** Scala di chi esce: ferma fino a 0,25 T, poi 0,9 a 0,36 T (R7 A.3: 0,95 a 0,31 T, 0,9 a 0,38 T). */
  val exitScale = GlassContentCurve(
    floatArrayOf(0f, 0.25f, GlassContentDefaults.EnterAt),
    floatArrayOf(1f, 1f, GlassContentDefaults.ExitScale),
  )

  /** Scala di chi entra al posto di un altro: 0,93 fino a 0,62 T, 0,97 a 0,77 T, 1 a T (R7 A4, curva blu). */
  val enterScale = GlassContentCurve(
    floatArrayOf(GlassContentDefaults.EnterAt, 0.62f, 0.77f, 1f),
    floatArrayOf(GlassContentDefaults.EnterScale, GlassContentDefaults.EnterScale, 0.97f, 1f),
  )

  /** Scala di chi nasce: 0,75 fino a 0,62 T, 0,89 a 0,77 T, 1 a T (R7 A4, curva verde). */
  val bornScale = GlassContentCurve(
    floatArrayOf(GlassContentDefaults.EnterAt, 0.62f, 0.77f, 1f),
    floatArrayOf(GlassContentDefaults.BornScale, GlassContentDefaults.BornScale, 0.89f, 1f),
  )

  /**
   * Sotto API 31 non c'è sfocatura, e una dissolvenza con le curve di sopra mostrerebbe per 70–100 ms due icone
   * nitide e leggibili una sopra l'altra: l'errore che la sfocatura serve a coprire (R7 A.4). Qui si danno quindi il
   * cambio: il vecchio è via a 0,45 T, il nuovo comincia lì; un buco breve vale meno di un'immagine doppia.
   */
  val plainExitAlpha = GlassContentCurve(floatArrayOf(0f, 0.45f), floatArrayOf(1f, 0f))
  val plainEnterAlpha = GlassContentCurve(floatArrayOf(0.45f, 0.9f), floatArrayOf(0f, 1f))

  fun alpha(role: GlassContentRole, u: Float, blurAvailable: Boolean): Float = when (role) {
    GlassContentRole.Rest -> 1f
    GlassContentRole.Exit -> if (blurAvailable) exitAlpha.at(u) else plainExitAlpha.at(u)
    GlassContentRole.Enter, GlassContentRole.Born -> if (blurAvailable) enterAlpha.at(u) else plainEnterAlpha.at(u)
  }

  fun scale(role: GlassContentRole, u: Float): Float = when (role) {
    GlassContentRole.Rest -> 1f
    GlassContentRole.Exit -> exitScale.at(u)
    GlassContentRole.Enter -> enterScale.at(u)
    GlassContentRole.Born -> bornScale.at(u)
  }

  /** La tinta (R7 A4): si incrocia fra 0,25 e 0,5 T, mentre σ è sopra 0,5 del massimo. */
  fun tint(u: Float): Float = glassContentSmoothstep(GlassContentDefaults.TintFrom, GlassContentDefaults.TintTo, u)

  /** Quando [role] ha finito di muoversi, in u. */
  fun end(role: GlassContentRole): Float = when (role) {
    GlassContentRole.Rest -> 0f
    GlassContentRole.Exit -> GlassContentDefaults.ExitEnd
    GlassContentRole.Enter, GlassContentRole.Born -> GlassContentDefaults.EnterEnd
  }

  /**
   * L'istante della salita di σ che ha la sfocatura [blur] (frazione del massimo), al più [GlassContentDefaults.EnterAt].
   * È il punto da cui riparte un cambio interrotto: il contenuto che diventa uscente ha già quella sfocatura, e il
   * nuovo arriva come se il cambio fosse partito lì.
   */
  fun riseTimeFor(blur: Float): Float {
    val cap = GlassContentDefaults.EnterAt
    if (blur >= blurRise(cap)) return cap
    if (blur <= 0f) return 0f
    var lo = 0f
    var hi = cap
    repeat(24) {
      val mid = (lo + hi) / 2f
      if (blurRise(mid) <= blur) lo = mid else hi = mid
    }
    return lo
  }
}

/**
 * Uno strato in viaggio.
 *
 * @param startMs l'istante del viaggio in cui lo strato ha preso il suo [role].
 * @param fromU da quale `u` delle curve parte: 0 per un cambio nuovo, di più per uno che riparte a metà.
 * @param from l'aspetto che lo strato aveva quando ha preso il suo ruolo: chi esce parte da lì, senza scalini.
 */
@Immutable
internal data class GlassContentLayer(
  val id: Long,
  val key: Any?,
  val role: GlassContentRole,
  val startMs: Double,
  val fromU: Float = 0f,
  val from: GlassContentLook = GlassContentLook.Sharp,
)

/**
 * Il cambio di contenuto di un vetro, come valore: gli strati in viaggio e il tempo. È immutabile: [retarget] ne dà uno
 * nuovo, e [frame] è una funzione pura del tempo. Il composable lo tiene in uno stato, il banco lo valuta sul PC.
 *
 * Una chiave `null` è il vuoto: nessun contenuto.
 *
 * @param tintFromU l'`u` da cui parte la tinta del cambio in corso (per l'interruzione).
 * @param blurAvailable `false` sotto API 31: niente sfocatura, il cambio di [GlassContentCurves.plainExitAlpha].
 */
@Immutable
internal class GlassContentJourney private constructor(
  val layers: List<GlassContentLayer>,
  val timing: GlassContentTiming,
  val blurAvailable: Boolean,
  private val tintStartMs: Double,
  private val tintFromU: Float,
  private val nextId: Long,
) {

  /** La chiave del contenuto d'arrivo, o `null` se si va verso il vuoto. */
  val targetKey: Any? get() = layers.lastOrNull { it.role != GlassContentRole.Exit }?.key

  private fun u(layer: GlassContentLayer, tMs: Double): Float =
    layer.fromU + ((tMs - layer.startMs) / timing.durationMs).toFloat().coerceAtLeast(0f)

  /** L'aspetto di [layer] all'istante [tMs] del viaggio. */
  fun look(layer: GlassContentLayer, tMs: Double): GlassContentLook {
    val u = u(layer, tMs)
    val role = layer.role
    val blurScale = if (blurAvailable) timing.peakBlur else 0f
    return when (role) {
      GlassContentRole.Rest -> GlassContentLook.Sharp
      GlassContentRole.Exit -> {
        // Chi esce parte dall'aspetto che aveva: l'opacità e la scala scendono in proporzione alla curva, la
        // sfocatura non torna mai indietro. Per un cambio nuovo (`from` nitido, `fromU` zero) è la curva pura.
        val a0 = GlassContentCurves.alpha(role, layer.fromU, blurAvailable)
        val alpha = if (a0 > 0f) layer.from.alpha * GlassContentCurves.alpha(role, u, blurAvailable) / a0 else 0f
        val scale = layer.from.scale * GlassContentCurves.scale(role, u) / GlassContentCurves.scale(role, layer.fromU)
        val blur = max(GlassContentCurves.blur(role, u) * blurScale, layer.from.blur)
        GlassContentLook(alpha.coerceIn(0f, 1f), scale, blur)
      }
      GlassContentRole.Enter, GlassContentRole.Born -> GlassContentLook(
        alpha = GlassContentCurves.alpha(role, u, blurAvailable),
        scale = GlassContentCurves.scale(role, u),
        blur = GlassContentCurves.blur(role, u) * blurScale,
      )
    }
  }

  private fun done(layer: GlassContentLayer, tMs: Double): Boolean =
    layer.role == GlassContentRole.Rest || u(layer, tMs) >= GlassContentCurves.end(layer.role)

  /** Il fotogramma all'istante [tMs] del viaggio (millisecondi dallo stesso zero di [start] e [retarget]). */
  fun frame(tMs: Double): GlassContentFrame {
    val visible = layers.mapNotNull { layer ->
      // Chi esce e ha finito non si disegna più; chi entra resta anche finito (è il contenuto d'arrivo).
      if (layer.role == GlassContentRole.Exit && done(layer, tMs)) return@mapNotNull null
      GlassContentLayerFrame(layer.id, layer.key, layer.role, look(layer, tMs))
    }
    val finished = layers.all { done(it, tMs) }
    val tint = if (tintStartMs.isNaN()) 1f else {
      val u = tintFromU + ((tMs - tintStartMs) / timing.durationMs).toFloat().coerceAtLeast(0f)
      val t0 = GlassContentCurves.tint(tintFromU)
      // Riparte da zero anche se l'interruzione cade dentro la finestra della tinta: nessuno scalino di colore.
      if (t0 >= 1f) 1f else ((GlassContentCurves.tint(u) - t0) / (1f - t0)).coerceIn(0f, 1f)
    }
    return GlassContentFrame(visible, tint, finished)
  }

  /**
   * Il viaggio dopo che lo stato è diventato [key] all'istante [tMs]: **riparte da dove si trova** (invariante 16).
   *
   * - Stessa chiave del contenuto d'arrivo: non cambia niente, il contenuto non si tocca (R7 A3).
   * - Chiave nuova: il contenuto d'arrivo di adesso diventa uscente dal suo aspetto di adesso; chi stava già uscendo
   *   continua per conto suo; il nuovo entra partendo dal punto della salita di σ che il vecchio ha già raggiunto
   *   ([GlassContentCurves.riseTimeFor]), così i due si incrociano ancora al massimo della sfocatura e il nuovo non
   *   compare mai di colpo (ci arriva al più a 0,36 T, dove la sua opacità è zero).
   * - Chiave `null`: il contenuto se ne va e non arriva niente. Da `null` a una chiave: il nuovo nasce (scala 0,75).
   */
  fun retarget(tMs: Double, key: Any?): GlassContentJourney {
    val current = layers.lastOrNull { it.role != GlassContentRole.Exit }
    if (current != null && current.key == key) return this
    if (current == null && key == null) return this
    var id = nextId
    val kept = layers.filter { it.role == GlassContentRole.Exit && !done(it, tMs) }.toMutableList()
    var fromU = 0f
    if (current != null) {
      val look = look(current, tMs)
      fromU = GlassContentCurves.riseTimeFor(if (timing.peakBlur > 0f) look.blur / timing.peakBlur else 0f)
      if (look.alpha > 0f) {
        kept += GlassContentLayer(current.id, current.key, GlassContentRole.Exit, tMs, fromU, look)
      }
    }
    // Senza nessuno visibile davanti (vuoto, o un nuovo non ancora comparso), il nuovo nasce: non ha un predecessore
    // dentro cui incrociarsi.
    val predecessor = kept.any { it.role == GlassContentRole.Exit }
    if (key != null) {
      val role = if (predecessor) GlassContentRole.Enter else GlassContentRole.Born
      kept += GlassContentLayer(id++, key, role, tMs, fromU)
    }
    return GlassContentJourney(kept, timing, blurAvailable, tMs, fromU, id)
  }

  /** Il viaggio finito: resta solo il contenuto d'arrivo, fermo. Il composable lo usa per smettere di contare il tempo. */
  fun settle(): GlassContentJourney {
    val target = layers.lastOrNull { it.role != GlassContentRole.Exit } ?: return GlassContentJourney(
      emptyList(), timing, blurAvailable, Double.NaN, 0f, nextId,
    )
    return GlassContentJourney(
      listOf(target.copy(role = GlassContentRole.Rest, startMs = 0.0, fromU = 0f, from = GlassContentLook.Sharp)),
      timing, blurAvailable, Double.NaN, 0f, nextId,
    )
  }

  /** Fermo: nessuno strato si muove, e non serve contare il tempo. */
  val settled: Boolean get() = layers.all { it.role == GlassContentRole.Rest }

  /**
   * Lo stesso viaggio senza gli strati usciti del tutto all'istante [tMs]: il composable li toglie dalla composizione
   * (una ricomposizione quando uno strato finisce, non una per fotogramma). L'orologio non cambia.
   */
  fun pruned(tMs: Double): GlassContentJourney {
    val alive = layers.filter { it.role != GlassContentRole.Exit || !done(it, tMs) }
    return if (alive.size == layers.size) this else
      GlassContentJourney(alive, timing, blurAvailable, tintStartMs, tintFromU, nextId)
  }

  companion object {
    /** Fermo su [key]: un solo strato nitido (o nessuno, se [key] è `null`). */
    fun rest(key: Any?, timing: GlassContentTiming = GlassContentTiming(), blurAvailable: Boolean = true) =
      GlassContentJourney(
        if (key == null) emptyList() else listOf(GlassContentLayer(0L, key, GlassContentRole.Rest, 0.0)),
        timing, blurAvailable, Double.NaN, 0f, 1L,
      )

    /** Un cambio da [from] a [to] che parte all'istante zero. */
    fun start(
      from: Any?,
      to: Any?,
      timing: GlassContentTiming = GlassContentTiming(),
      blurAvailable: Boolean = true,
    ): GlassContentJourney = rest(from, timing, blurAvailable).retarget(0.0, to)
  }
}

/**
 * Il fotogramma di un cambio da [from] a [to], senza interruzioni: la forma corta di [GlassContentJourney], quella che
 * il banco e i test chiamano per primi. `from = null` è un contenuto che nasce; `to = null` uno che se ne va.
 */
internal fun glassContentFrame(
  tMs: Double,
  from: Any?,
  to: Any?,
  timing: GlassContentTiming = GlassContentTiming(),
  blurAvailable: Boolean = true,
): GlassContentFrame = GlassContentJourney.start(from, to, timing, blurAvailable).frame(tMs)

// ================================================================================================================
// Il contenuto di un pezzo del piano flottante (F3.8a)
// ================================================================================================================
//
// Un vetro che nasce, compare, se ne va o si sposta dentro un `FluidGlassContainer` porta con sé il suo contenuto, e fino
// a F3.8 il contenuto era solo «al suo posto d'arrivo»: scritto sulla pagina prima che il vetro lo raggiungesse («Fatto»
// sulla foto senza vetro per 60–140 ms, S3). Le regole sono quelle di R7 A.6, sulla **fase del volo** del pezzo (0 alla
// partenza, 1 al riposo a vista: `GlassPlanePieceFrame.phase`), e il contenitore le applica ritagliando sempre il
// contenuto sulla sagoma del vetro in volo:
//
//  - **chi nasce da un vicino** (A6): il contenuto non ha un predecessore. Non c'è finché il vetro non è arrivato al suo
//    posto, poi arriva sfocato da scala 0,75 e si fa nitido per ultimo. Apple, «Select» → «Done»: il contenuto nuovo
//    compare a ~60% del viaggio ed è nitido negli ultimi fotogrammi. Da F3.12a **sta al centro del suo vetro** e fa il
//    suo rimbalzo (fino a F3.11 restava fermo al posto d'arrivo e il vetro gli oscillava attorno): in Apple i simboli di
//    un pezzo che nasce superano il loro posto di 0,15–0,20 D col vetro e tornano, senza cambiare misura
//    (`artifacts/liquid-glass/sessioni/f3-12a/misure.md`);
//  - **chi compare per lente** (A8): il simbolo si condensa da 2× e sfocato a 1× e nitido fra 0,25 e 0,85 della comparsa,
//    senza pause;
//  - **chi se ne va** (A5, A8): si sfoca e svanisce nel primo 30% del volo (i 130 ms di R4 B3 su 470), e il bordo che si
//    ritira lo copre. Chi scompare per lente resta dov'era; chi **confluisce** in un vicino ci entra col suo vetro
//    mentre svanisce (F3.12a: R4 B2 (b), 4:51 e 4:59);
//  - **chi resta** (A3): nitido, opaco, e si sposta col suo vetro (anche nel rinculo del donatore di una nascita);
//  - **un viaggio che riparte a metà** (invariante 16): ogni contenuto riparte dall'aspetto che ha.

/** Che parte fa il contenuto di un pezzo nel viaggio del suo vetro. */
internal enum class GlassPieceContentRole {
  /** Fermo, o in viaggio col suo vetro: nitido e opaco (R7 A3). */
  Rest,

  /** Non si vede: un pezzo appena composto, finché il piano non ha deciso come entra. */
  Hidden,

  /** Il contenuto di un vetro nato da un vicino (R7 A6). */
  Born,

  /** Il contenuto di un vetro che compare per lente (R7 A8). */
  Lens,

  /** Torna nitido dall'aspetto che aveva: un contenuto che stava entrando o uscendo e ora resta. */
  Recover,

  /** Se ne va dall'aspetto che aveva (R7 A5, A8). */
  Exit,
}

/** Le fasi della grammatica del contenuto di un pezzo, con la loro scheda. Numeri di gusto, fiducia media. */
internal object GlassPieceContentDefaults {
  /**
   * La fase in cui il contenuto di chi nasce comincia a esserci: 0,5. Nella tavola «Select» → «Done» (classe C,
   * rallentata: vale l'ordine delle fasi) al 50% il vetro è ancora vuoto e al 60% «Done» è una macchia chiara. A quella
   * fase la lastra che nasce ha già coperto il posto d'arrivo (la molla del piano fa più del 90% della corsa nella prima
   * metà del tempo), quindi il contenuto non compare mai fuori dal vetro; il ritaglio sulla sagoma lo garantisce comunque.
   *
   * Da F3.12a la fase è quella **del contenuto** (`GlassPlanePieceFrame.contentPhase`): conta dal primo rilascio del
   * viaggio. In quella clip il primo nato lascia il donatore a 0,29, quindi 0,5 è 0,21 dopo il rilascio; quando il
   * donatore resta dov'è e rilascia a 0,17 (le clip di Apple a tempo vero: i simboli nuovi si vedono da 0,40) il
   * contenuto compare a 0,38 del viaggio, dentro il vetro che sta ancora arrivando, e viaggia con lui.
   */
  const val BornAt: Float = 0.5f

  /**
   * La fase in cui il contenuto di chi nasce è nitido (σ zero): 0,78. Nella clip di Apple (35 fotogrammi da «Select»
   * nitido, f9031, al riposo, f9066) il contenuto nuovo compare al 51–54% (f9049–f9050), al 60% è una macchia sfocata,
   * al 70% è quasi nitido, nitido al 77–80% (f9058–f9059), e i colli si rompono dopo, al 90%. Sulla tavola del banco
   * (`bench/f3-8a/tavole/`) con 0,82 al 70% la scritta era ancora sfocata dove quella di Apple si legge già.
   */
  const val BornSharp: Float = 0.78f

  /** Da dove a dove della comparsa per lente il simbolo si condensa (R7 A8: fra 0,25 e 0,85). */
  const val LensFrom: Float = 0.25f

  /** Vedi [LensFrom]. */
  const val LensTo: Float = 0.85f

  /** La scala da cui il simbolo si condensa (R4 B3: ≈2×). */
  const val LensScale: Float = 2f

  /**
   * In quanta parte del volo sta la curva d'uscita di F3.4 (T) per il contenuto di un vetro che se ne va **confluendo**
   * in un altro, cioè in un cambio di contesto: 0,24, il contenuto è via al 16% del volo ([GlassContentDefaults.ExitEnd]
   * × 0,24), 47 ms sui 300 di un cambio di contesto. In Apple «Select» è sfocato dal primo fotogramma (3%) e sparito al
   * 14–17% (R4 B2 (d), sulla tavola di F3.8a); fino a F3.10 era 0,4, cioè il 26%, ed era l'ultima cosa del contenuto
   * lontana dalla clip. L'ingresso del nuovo non cambia (sfocato dal 60%, nitido all'80%).
   */
  const val ExitSpan: Float = 0.24f

  /**
   * Lo stesso per il simbolo di un vetro che **scompare per lente** (R4 B3): 0,4, cioè via a 0,26 del volo, 124 ms sui
   * 470 di una scomparsa («simbolo via in 130 ms»). È la scheda che lo misura, e non è quella del cambio di contesto.
   */
  const val LensExitSpan: Float = 0.4f

  /** In quanta parte del volo un contenuto che resta torna nitido dall'aspetto che aveva. */
  const val RecoverSpan: Float = 0.5f
}

/**
 * La parte del contenuto di un pezzo e l'aspetto da cui la comincia ([from]: conta per [GlassPieceContentRole.Exit] e
 * [GlassPieceContentRole.Recover], che partono da dove il contenuto si trova).
 */
@Immutable
internal data class GlassPieceContent(
  val role: GlassPieceContentRole,
  val from: GlassContentLook = GlassContentLook.Sharp,
  /**
   * Per [GlassPieceContentRole.Exit]: in quanta parte del volo sta la curva d'uscita. [GlassPieceContentDefaults.ExitSpan]
   * per chi confluisce in un vicino (un cambio di contesto), [GlassPieceContentDefaults.LensExitSpan] per chi scompare per
   * lente. Le altre parti non lo leggono.
   */
  val span: Float = GlassPieceContentDefaults.ExitSpan,
) {
  companion object {
    val Rest = GlassPieceContent(GlassPieceContentRole.Rest)
    val Hidden = GlassPieceContent(GlassPieceContentRole.Hidden)
  }
}

/** L'aspetto di un contenuto che non si vede: è ciò da cui parte chi entra. */
internal val GlassContentInvisible: GlassContentLook = GlassContentLook(alpha = 0f, scale = 1f, blur = 0f)

/**
 * L'aspetto del contenuto [content] alla [phase] del volo del suo vetro. Pura: la usano il contenitore e le tracce.
 * `blur` è σ in frazione dell'altezza della sagoma, come in [GlassContentJourney.look]; sotto API 31 ([blurAvailable]
 * falso) non c'è sfocatura e chi esce e chi entra si danno il cambio senza sovrapporsi.
 */
internal fun glassPieceContentLook(
  content: GlassPieceContent,
  phase: Float,
  blurAvailable: Boolean = true,
  peakBlur: Float = GlassContentDefaults.PeakBlur,
): GlassContentLook {
  val p = if (phase.isFinite()) phase.coerceAtLeast(0f) else 1f
  val peak = if (blurAvailable) peakBlur else 0f
  return when (content.role) {
    GlassPieceContentRole.Rest -> GlassContentLook.Sharp
    GlassPieceContentRole.Hidden -> GlassContentInvisible
    GlassPieceContentRole.Born -> {
      // La curva di chi nasce di F3.4 (opacità, scala da 0,75, σ che scende a zero), portata sulla finestra del volo.
      val u = glassPieceBornU(p)
      if (p < GlassPieceContentDefaults.BornAt) {
        GlassContentInvisible
      } else {
        GlassContentLook(
          alpha = GlassContentCurves.alpha(GlassContentRole.Born, u, blurAvailable),
          scale = GlassContentCurves.scale(GlassContentRole.Born, u),
          blur = GlassContentCurves.blur(GlassContentRole.Born, u) * peak,
        )
      }
    }
    GlassPieceContentRole.Lens -> {
      val w = ((p - GlassPieceContentDefaults.LensFrom) /
        (GlassPieceContentDefaults.LensTo - GlassPieceContentDefaults.LensFrom)).coerceIn(0f, 1f)
      if (p <= GlassPieceContentDefaults.LensFrom) {
        GlassContentInvisible
      } else {
        val shrink = (1f - w) * (1f - w) * (1f - w)
        GlassContentLook(
          alpha = glassContentSmoothstep(0f, 0.8f, w),
          scale = 1f + (GlassPieceContentDefaults.LensScale - 1f) * shrink,
          blur = peak * (1f - glassContentSmoothstep(0f, 1f, w)),
        )
      }
    }
    GlassPieceContentRole.Exit -> {
      val u = p / glassPieceExitSpan(content)
      val from = content.from
      GlassContentLook(
        alpha = (from.alpha * GlassContentCurves.alpha(GlassContentRole.Exit, u, blurAvailable)).coerceIn(0f, 1f),
        scale = from.scale * GlassContentCurves.scale(GlassContentRole.Exit, u),
        // La sfocatura non torna mai indietro mentre il contenuto svanisce.
        blur = max(GlassContentCurves.blur(GlassContentRole.Exit, u) * peak, from.blur),
      )
    }
    GlassPieceContentRole.Recover -> {
      val s = glassContentSmoothstep(0f, GlassPieceContentDefaults.RecoverSpan, p)
      val from = content.from
      if (s >= 1f) GlassContentLook.Sharp else GlassContentLook(
        alpha = from.alpha + (1f - from.alpha) * s,
        scale = from.scale + (1f - from.scale) * s,
        blur = from.blur * (1f - s),
      )
    }
  }
}

/** L'`u` delle curve di F3.4 a cui sta chi nasce alla fase [phase] del volo: 0,36 a [GlassPieceContentDefaults.BornAt], 1 a `BornSharp`. */
internal fun glassPieceBornU(phase: Float): Float =
  GlassContentDefaults.EnterAt + (phase - GlassPieceContentDefaults.BornAt) * (1f - GlassContentDefaults.EnterAt) /
    (GlassPieceContentDefaults.BornSharp - GlassPieceContentDefaults.BornAt)

/** Vero quando [content] ha finito di muoversi alla [phase]: da lì è fermo (o sparito, per chi esce). */
internal fun glassPieceContentDone(content: GlassPieceContent, phase: Float): Boolean = when (content.role) {
  GlassPieceContentRole.Rest, GlassPieceContentRole.Hidden -> true
  GlassPieceContentRole.Born -> glassPieceBornU(phase) >= GlassContentDefaults.EnterEnd
  GlassPieceContentRole.Lens -> phase >= GlassPieceContentDefaults.LensTo
  GlassPieceContentRole.Exit -> phase / glassPieceExitSpan(content) >= GlassContentDefaults.ExitEnd
  GlassPieceContentRole.Recover -> phase >= GlassPieceContentDefaults.RecoverSpan
}

/** La parte del volo in cui sta l'uscita di [content]: la sua, se è un numero sensato, altrimenti quella di serie. */
private fun glassPieceExitSpan(content: GlassPieceContent): Float =
  if (content.span.isFinite() && content.span > 0f) content.span else GlassPieceContentDefaults.ExitSpan

/** Che cosa fa il vetro di un pezzo in un viaggio, per il suo contenuto: la sola parte di `GlassPlaneChange` che conta qui. */
internal enum class GlassPieceArrival { Stays, BornFromNeighbour, ThroughLens, Instant }

/**
 * La parte che il contenuto di un pezzo prende quando un viaggio parte o riparte.
 *
 * @param arrival come arriva il suo vetro; non conta per chi se ne va.
 * @param now l'aspetto che il contenuto ha adesso (anche quello ereditato dal nodo che aveva la stessa identità), o
 *   null se non è mai stato disegnato: un pezzo appena composto, senza un predecessore a vista.
 * @param departed vero se il pezzo non c'è più nello stato d'arrivo: il suo contenuto esce.
 */
internal fun glassPieceContentFor(arrival: GlassPieceArrival, now: GlassContentLook?, departed: Boolean): GlassPieceContent {
  if (departed) {
    // Un vetro che sparisce senza transizione porta via il contenuto con sé; uno mai visto non ha niente da far uscire.
    // Il simbolo di chi scompare per lente ha i tempi della scomparsa (R4 B3); chi confluisce quelli del cambio di
    // contesto (R4 B2 (d)).
    val span = if (arrival == GlassPieceArrival.ThroughLens) GlassPieceContentDefaults.LensExitSpan
      else GlassPieceContentDefaults.ExitSpan
    return if (arrival == GlassPieceArrival.Instant || now == null || now.alpha <= 0f) GlassPieceContent.Hidden
      else GlassPieceContent(GlassPieceContentRole.Exit, now, span)
  }
  return when (arrival) {
    GlassPieceArrival.BornFromNeighbour -> GlassPieceContent(GlassPieceContentRole.Born)
    GlassPieceArrival.ThroughLens -> GlassPieceContent(GlassPieceContentRole.Lens)
    GlassPieceArrival.Instant -> GlassPieceContent.Rest
    // Un vetro che resta: il contenuto che si vedeva continua (nitido, o tornando nitido da dov'era). Uno mai visto è
    // un nodo nuovo su un vetro che c'era già (un pezzo tolto e rimesso a metà viaggio): entra come chi nasce, quando il
    // vetro l'ha raggiunto, invece di comparire di colpo.
    GlassPieceArrival.Stays -> when (now) {
      null -> GlassPieceContent(GlassPieceContentRole.Born)
      GlassContentLook.Sharp -> GlassPieceContent.Rest
      else -> GlassPieceContent(GlassPieceContentRole.Recover, now)
    }
  }
}

/** Il raggio da dare a `BlurEffect` per una σ in pixel: HWUI usa σ = 0,57735 r + 0,5 (lo stesso conto del bordo). */
internal fun glassContentBlurRadiusPx(sigmaPx: Float): Float =
  if (sigmaPx <= 0f) 0f else GlassEdgeBlurKernel.sigmaToRadius(sigmaPx)

/**
 * Il contenuto di un vetro che cambia, alla maniera di Apple (`blurReplace` dentro un vetro, R7 A.2): chi esce si
 * sfoca, rimpicciolisce appena e svanisce; chi entra arriva sfocato e si fa nitido; i due si incrociano al massimo della
 * sfocatura, e la tinta cambia lì dentro. Il contenuto con la **stessa [key]** nei due stati non si tocca: resta
 * nitido anche se il vetro intorno corre (R7 A3, D2 di Alessio).
 *
 * Serve una transizione per ogni posto: in una barra di tre icone, tre [FluidGlassContentTransition], così l'icona
 * che resta non si sfoca con le altre. La forma del vetro non è affare suo (il contenitore la fa cambiare); il
 * contenuto sta già al suo posto d'arrivo e il vetro lo ritaglia (R7 A5).
 *
 * Uno stato nuovo a metà del cambio riparte da dove si trova (invariante 16): niente torna nitido di colpo, niente
 * compare di colpo. Sotto Android 12 (API 31) non c'è `RenderEffect`: il cambio è una dissolvenza senza sovrapposizione.
 * Con il movimento ridotto il contenuto cambia di colpo.
 *
 * @param targetState lo stato da mostrare.
 * @param key l'identità del contenuto (simbolo, testo, colore): se non cambia, il contenuto non si sfoca. `null` è il
 *   vuoto: niente da disegnare, e il contenuto dopo il vuoto nasce da scala 0,75.
 * @param blurReference il riquadro su cui si misura la sfocatura: per un'icona la sua taglia (26 dp in barra), per una
 *   riga di testo la sua altezza di riga. σ massima = 0,15 della sagoma, cioè ≈2,9 dp su un'icona da 26.
 * @param contentColor se c'è, il colore di ogni stato, fornito come `LocalContentColor` a tutti gli strati e
 *   incrociato dentro la sfocatura (R7 A4): il colore arriva prima della forma.
 * @param content il contenuto di uno stato. Durante il cambio è chiamato per due (o più) stati insieme.
 */
@Composable
fun <S> FluidGlassContentTransition(
  targetState: S,
  modifier: Modifier = Modifier,
  key: (S) -> Any? = { it },
  blurReference: Dp = GlassContentDefaults.BlurReference,
  contentColor: ((S) -> Color)? = null,
  contentAlignment: Alignment = Alignment.Center,
  content: @Composable (S) -> Unit,
) {
  val policy = LocalFluidMotionPolicy.current
  val blurAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val timing = remember(policy.durationScale) {
    GlassContentTiming(durationMs = GlassContentDefaults.DurationMs * policy.durationScale.coerceAtLeast(0.01f))
  }
  val targetKey = key(targetState)

  // Lo stato di ogni chiave: chi esce continua a disegnare il proprio contenuto anche quando lo stato è cambiato.
  val states = remember { HashMap<Any?, S>() }
  states[targetKey] = targetState

  var journey by remember { mutableStateOf(GlassContentJourney.rest(targetKey, timing, blurAvailable)) }
  // Il tempo del viaggio in corso. Lo leggono i `graphicsLayer` (ogni fotogramma ridisegna, non ricompone) e il
  // colore solo mentre un colore sta cambiando.
  var nowMs by remember { mutableDoubleStateOf(0.0) }
  // Il colore da cui parte il cambio di colore in corso; `null` quando non ce n'è uno.
  var colorFrom by remember { mutableStateOf<Color?>(null) }

  if (journey.targetKey != targetKey) {
    val previous = journey.targetKey
    colorFrom = contentColor?.let { colorOf ->
      val before = states[previous]?.let(colorOf)
      // Il colore che si vede adesso, anche a metà di un altro cambio: il nuovo cambio parte da lì.
      before?.let { mixedColor(colorFrom ?: it, it, journey.frame(nowMs).tint) }
    }
    journey = if (policy.reducedMotion) {
      colorFrom = null
      GlassContentJourney.rest(targetKey, timing, blurAvailable)
    } else {
      journey.retarget(nowMs, targetKey)
    }
  }
  // Le chiavi che nessuno strato disegna più non servono: la mappa non cresce con la storia degli stati.
  states.keys.retainAll(journey.layers.mapTo(HashSet()) { it.key } + targetKey)

  val running = !journey.settled
  LaunchedEffect(running) {
    if (!running) return@LaunchedEffect
    // Un orologio solo per tutta la corsa: un'interruzione cambia il viaggio, non riavvia il conto del tempo.
    var lastNanos = -1L
    var steps = 0
    while (true) {
      val frameNanos = withFrameNanos { it }
      if (lastNanos >= 0L) {
        // I primi passi hanno lo stesso tetto del viaggio del vetro (F3.8a, [FluidMotion.PlaneFirstStepMillis]): un buco
        // di fotogrammi alla partenza (il pannello che sale da 30 a 120 Hz) è tempo perso, non sfocatura saltata.
        nowMs += glassContentClockStep((frameNanos - lastNanos) / 1_000_000.0, steps)
        steps++
      }
      lastNanos = frameNanos
      val frame = journey.frame(nowMs)
      if (frame.finished) {
        journey = journey.settle()
        nowMs = 0.0
        colorFrom = null
        break
      }
      journey = journey.pruned(nowMs)
    }
  }

  val density = LocalDensity.current
  val glyphPx = with(density) { blurReference.toPx() } * GlassContentDefaults.GlyphFraction
  // Il margine del layer: una gaussiana tagliata al bordo del riquadro darebbe una macchia squadrata.
  val marginPx = ceil(3f * GlassContentDefaults.PeakBlur * glyphPx).toInt()
  val marginDp = with(density) { marginPx.toDp() }

  Box(modifier, contentAlignment = contentAlignment) {
    for (layer in journey.layers) {
      if (layer.key == null) continue
      val state = states[layer.key] ?: continue
      identity(layer.id) {
        Box(
          Modifier
            .layout { measurable, constraints ->
              // Il layer è più grande del contenuto di [marginPx] per lato, ma il layout non se ne accorge.
              val placeable = measurable.measure(constraints.offset(2 * marginPx, 2 * marginPx))
              layout(max(0, placeable.width - 2 * marginPx), max(0, placeable.height - 2 * marginPx)) {
                placeable.place(-marginPx, -marginPx)
              }
            }
            .graphicsLayer {
              val look = journey.look(layer, nowMs)
              alpha = look.alpha
              scaleX = look.scale
              scaleY = look.scale
              val sigmaPx = look.blur * glyphPx
              renderEffect = if (blurAvailable && sigmaPx > 0.05f) {
                val radius = glassContentBlurRadiusPx(sigmaPx)
                BlurEffect(radius, radius, TileMode.Decal)
              } else {
                null
              }
            }
            .padding(marginDp),
          contentAlignment = contentAlignment,
        ) {
          val colorOf = contentColor
          if (colorOf == null) {
            content(state)
          } else {
            val target = colorOf(states[journey.targetKey] ?: state)
            val from = colorFrom
            val color = if (from == null) target else mixedColor(from, target, journey.frame(nowMs).tint)
            CompositionLocalProvider(LocalContentColor provides color) { content(state) }
          }
        }
      }
    }
  }
}

private fun mixedColor(from: Color, to: Color, tint: Float): Color = lerp(from, to, tint.coerceIn(0f, 1f))

/**
 * Di quanto avanza l'orologio di un cambio di contenuto in un fotogramma lungo [frameMs], il [step]-esimo dalla partenza:
 * i primi [FluidMotion.PlaneFirstSteps] non più di [FluidMotion.PlaneFirstStepMillis], gli altri quanto è passato. Pura.
 */
internal fun glassContentClockStep(frameMs: Double, step: Int): Double {
  val elapsed = if (frameMs.isFinite()) frameMs.coerceAtLeast(0.0) else 0.0
  return if (step < FluidMotion.PlaneFirstSteps) min(elapsed, FluidMotion.PlaneFirstStepMillis.toDouble()) else elapsed
}

/** Quanti pixel di σ: la conversione del composable, esposta al test. */
internal fun glassContentSigmaPx(blur: Float, referencePx: Float): Float =
  blur * referencePx * GlassContentDefaults.GlyphFraction

/**
 * La proposta per il rischio dell'immagine doppia di `w` (piano F3, «Rischi e ripieghi»; codice di F3.3, che qui non si
 * tocca). `w` mescola il ramo nitido e quello sfocato della cattura: è una dissolvenza, non una sfocatura intermedia, e
 * per i valori di `w` lontani da 0 e da 1 si vedono le lettere nitide sopra la loro copia sfocata. Con una rampa
 * lineare negli ultimi 150 ms di un viaggio (nove fotogrammi a 60 Hz) quel tratto dura cinque o sei fotogrammi.
 *
 * Il rimedio scelto è la **curva più ripida**: `w` passa per una `smoothstep` stretta attorno a 0,5, e i fotogrammi
 * ambigui scendono a due. Costa zero (un numero diverso nello stesso uniform) e non cambia il raggio né il margine
 * della cattura (invariante 1). L'alternativa, il raggio che scende con `w`, toglie l'immagine doppia del tutto ma
 * ricostruisce l'effetto a ogni fotogramma del tratto (`GlassFieldEffects.upstream` conta le `builds`): da tenere per
 * la comparsa, dove il tratto è lungo 430 ms e una curva ripida diventerebbe uno scatto.
 *
 * @param halfWindow metà della finestra della S, in unità di `w`: 0,25 dà la finestra 0,25..0,75.
 */
internal fun glassMixSteep(w: Float, halfWindow: Float = GlassMixSteepHalfWindow): Float =
  glassContentSmoothstep(0.5f - halfWindow, 0.5f + halfWindow, w)

internal const val GlassMixSteepHalfWindow: Float = 0.25f

/**
 * Un fotogramma mostra un'immagine doppia quando **tutti e due** i rami pesano almeno [visible] (una copia al 20% del
 * contrasto si legge ancora sopra l'altra). Restituisce quanti fotogrammi di [ws] lo fanno.
 */
internal fun glassMixAmbiguousFrames(ws: List<Float>, visible: Float = 0.2f): Int =
  ws.count { min(it, 1f - it) >= visible }

/**
 * Il rimedio alternativo, per la comparsa: invece di mescolare due rami, un ramo solo con il raggio che scende con `w`
 * (`w = 1` nitido, `w = 0` raggio pieno). Nessuna immagine doppia, a ogni `w`; il prezzo è un `RenderEffect` nuovo a
 * ogni fotogramma del tratto.
 */
internal fun glassMixRadius(w: Float, fullRadiusPx: Float): Float = fullRadiusPx * (1f - w.coerceIn(0f, 1f))
