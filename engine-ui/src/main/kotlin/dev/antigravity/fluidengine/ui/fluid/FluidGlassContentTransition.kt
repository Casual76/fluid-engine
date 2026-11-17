package dev.antigravity.fluidengine.ui.fluid

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key as identity
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import dev.antigravity.fluidengine.ui.glass.field.GlassEdgeBlurKernel
import dev.antigravity.fluidengine.ui.fluidphysics.FluidCornerRadii
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
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

  /**
   * Quanto si sfoca, in frazione di [PeakBlur], un contenuto che **resta** in un vetro che cambia forma
   * ([GlassContentLayer.soft], la variante «morbida» di F4A.5b): 0,8. Sulle due clip a tempo vero (219, il telefono a 4:44 e
   * il render a 4:48) il «+» che resta mentre la capsula si chiude su di lui ha σ 0,13 e 0,17 della sagoma a 133 ms e torna
   * nitido a ~300, con la stessa curva dello scambio: adattata con la curva di serie (T 300 ms), l'intensità è 0,80–0,86
   * (`artifacts/liquid-glass/sessioni/f4a-5b/misure.md`, fiducia **alta** sulla forma, **media** sul numero).
   */
  const val KeptSoftness: Float = 0.8f
}

/** Che parte fa uno strato di contenuto nel cambio. */
internal enum class GlassContentRole {
  /** Il contenuto di prima: si sfoca, rimpicciolisce di poco e svanisce (R7 A1). */
  Exit,

  /** Il contenuto nuovo al posto di un altro: arriva sfocato a scala 0,93 e si fa nitido (R7 A1). */
  Enter,

  /** Il contenuto nuovo dove prima non c'era niente: come [Enter], ma da scala 0,75 (R7 A6). */
  Born,

  /**
   * Fermo: nitido, opaco, a scala 1. È anche il contenuto con la stessa chiave nei due stati (R7 A3). Uno strato fermo
   * **ammorbidito** ([GlassContentLayer.soft], F4A.5b) si sfoca appena e torna nitido senza smettere di essere fermo.
   */
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
    GlassContentRole.Enter, GlassContentRole.Born -> softBlur(u)
  }

  /**
   * σ in frazione del massimo di uno strato che si sfoca e torna nitido senza svanire: la salita fino a metà T, la discesa
   * fino a T. È la curva di chi entra, e quella del contenuto che resta in un vetro che cambia forma (F4A.5b).
   */
  fun softBlur(u: Float): Float = if (u <= GlassContentDefaults.PeakAt) blurRise(u) else blurFall(u)

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
   * L'istante della salita di σ che ha la sfocatura [blur] (frazione del massimo), al più [cap] (di serie
   * [GlassContentDefaults.EnterAt]; il contenuto ammorbidito risale fino al massimo, [GlassContentDefaults.PeakAt]).
   * È il punto da cui riparte un cambio interrotto: il contenuto che diventa uscente ha già quella sfocatura, e il
   * nuovo arriva come se il cambio fosse partito lì.
   */
  fun riseTimeFor(blur: Float, cap: Float = GlassContentDefaults.EnterAt): Float {
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
  /**
   * Vero per uno strato fermo che il vetro, cambiando forma, **ammorbidisce** (F4A.5b, [GlassContentJourney.soften]): da
   * [startMs] si sfoca con la curva dello scambio ([GlassContentDefaults.KeptSoftness] del massimo) e in T torna nitido,
   * opaco e a scala 1 per tutto il tempo. Falso: fermo e nitido, com'era.
   */
  val soft: Boolean = false,
  val recovering: Boolean = false,
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
    if (layer.recovering && role == GlassContentRole.Enter) {
      val peak = GlassContentDefaults.PeakAt
      val remaining = (GlassContentCurves.blur(role, u) / GlassContentCurves.blur(role, peak)).coerceIn(0f, 1f)
      return GlassContentLook(1f - (1f - layer.from.alpha) * remaining,
        1f - (1f - layer.from.scale) * remaining, layer.from.blur * remaining)
    }
    return when (role) {
      GlassContentRole.Rest -> if (!layer.soft) GlassContentLook.Sharp else
        GlassContentLook(1f, 1f, GlassContentCurves.softBlur(u) * blurScale * GlassContentDefaults.KeptSoftness)
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
    if (layer.role == GlassContentRole.Rest) !layer.soft || u(layer, tMs) >= 1f
    else u(layer, tMs) >= GlassContentCurves.end(layer.role)

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
  fun retarget(tMs: Double, key: Any?, resumeLabel: Boolean = false): GlassContentJourney {
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
      val returning = if (resumeLabel) kept.find { it.key == key } else null
      if (returning != null) {
        kept.remove(returning)
        kept += GlassContentLayer(returning.id, key, GlassContentRole.Enter, tMs, GlassContentDefaults.PeakAt,
          look(returning, tMs), recovering = true)
      } else if (resumeLabel && current != null && !done(current, tMs) && current.role != GlassContentRole.Rest) {
        kept += GlassContentLayer(id++, key, GlassContentRole.Enter, tMs, GlassContentDefaults.PeakAt,
          GlassContentLook(0f, GlassContentCurves.scale(role, GlassContentDefaults.PeakAt),
            if (blurAvailable) timing.peakBlur else 0f), recovering = true)
      } else kept += GlassContentLayer(id++, key, role, tMs, fromU)
    }
    return GlassContentJourney(kept, timing, blurAvailable, tMs, fromU, id)
  }

  /**
   * Il vetro attorno al contenuto d'arrivo **cambia forma** all'istante [tMs] (F4A.5b, la variante «morbida»): il contenuto
   * che resta si sfoca appena e torna nitido ([GlassContentLayer.soft]), senza svanire. Riparte da dove si trova: un
   * contenuto già sfocato dallo stesso motivo riprende la salita dal punto che ha la sua sfocatura di adesso. Un contenuto
   * che sta ancora arrivando (sfocato perché nuovo) non cambia, e nemmeno il vuoto.
   */
  fun soften(tMs: Double): GlassContentJourney {
    val current = layers.lastOrNull { it.role != GlassContentRole.Exit } ?: return this
    if (current.role != GlassContentRole.Rest || current.key == null) return this
    val reach = timing.peakBlur * GlassContentDefaults.KeptSoftness
    val blurNow = look(current, tMs).blur
    val fromU = if (reach > 0f) GlassContentCurves.riseTimeFor(blurNow / reach, GlassContentDefaults.PeakAt) else 0f
    val kept = GlassContentLayer(current.id, current.key, GlassContentRole.Rest, tMs, fromU, soft = true)
    return GlassContentJourney(layers.map { if (it === current) kept else it }, timing, blurAvailable, tintStartMs, tintFromU, nextId)
  }

  /** Il viaggio finito: resta solo il contenuto d'arrivo, fermo. Il composable lo usa per smettere di contare il tempo. */
  fun settle(): GlassContentJourney {
    val target = layers.lastOrNull { it.role != GlassContentRole.Exit } ?: return GlassContentJourney(
      emptyList(), timing, blurAvailable, Double.NaN, 0f, nextId,
    )
    return GlassContentJourney(
      listOf(target.copy(role = GlassContentRole.Rest, startMs = 0.0, fromU = 0f, from = GlassContentLook.Sharp, soft = false)),
      timing, blurAvailable, Double.NaN, 0f, nextId,
    )
  }

  /**
   * La policy vale anche a chiave invariata durante una corsa: spegnere il movimento porta subito all'arrivo.
   * Non legge né divide il tempo, quindi vale anche passando alla scala di sistema zero fra due fotogrammi.
   */
  fun withReducedMotion(reduced: Boolean): GlassContentJourney = if (reduced && !settled) settle() else this

  /** Fermo: nessuno strato si muove, e non serve contare il tempo. */
  val settled: Boolean get() = layers.all { it.role == GlassContentRole.Rest && !it.soft }

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
//  - **chi compare per lente** (A8): da 0,25 il simbolo diventa visibile ancora a 2×, traslucido; si contrae soprattutto
//    fra 234 e 367 ms, con l'ultimo assestamento fino a 430 ms (correzione F3 del 17 novembre 2026);
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
   * F3, correzione del 17 novembre 2026: anticipo della sola opacità di chi nasce. Sull'orologio del rilascio di
   * F3.12c, 0,08 di fase vale circa 30 ms: prima traccia a ~200, leggibilità a ~267–300. Fuoco e scala tengono il
   * proprio orologio; l'opacità cresce più piano e finisce a 1,30 (~630 ms), come la massa dei simboli R4 B2 (b).
   */
  const val BornAlphaAt: Float = 0.42f
  const val BornAlphaEnd: Float = 1.30f

  /**
   * La fase di riferimento di scala e fuoco di chi nasce: 0,5. Fino a F3.12c iniziava qui anche l'opacità; ora parte
   * prima, da [BornAlphaAt], senza anticipare il fuoco. Nella tavola «Select» → «Done» (classe C,
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
   * La fase in cui il contenuto di chi nasce ha finito la curva di F3.4 per l'opacità e la scala (u = 1): 0,78. Nella
   * clip di Apple (35 fotogrammi da «Select» nitido, f9031, al riposo, f9066) il contenuto nuovo compare al 51–54%
   * (f9049–f9050), al 60% è una macchia sfocata, al 70% si legge, ed è della sua misura. La **sfocatura** non finisce qui
   * (F3.12c): va a zero con la sua rampa fino a [BornFocusEnd].
   */
  const val BornSharp: Float = 0.78f

  /**
   * La fase in cui il contenuto di chi nasce è **nitido** (σ zero, e il contenuto è fermo): 1,0 della fase del contenuto
   * (con un donatore fermo ~497 ms). Dal colmo della sfocatura (~260 ms) ci arriva con due rampe: quasi tutta scende presto,
   * `(1 − x)^`[BornFocusPower], e una piccola parte piano, [BornFocusTail]. Con un donatore fermo: ~1,3 dp a 300 ms (si
   * legge, ancora morbido), ~0,5 a 333, ~0,2 al colmo del superamento (~370), ~0,1 a 400, nitido a ~497; i decimi di dp,
   * dove il Laplaciano del simbolo sale, durano ~120 ms (fino a F3.12a la sfocatura lineare finiva di colpo, in due
   * fotogrammi).
   *
   * Il tempo è quello delle clip **a tempo vero** (R4 B2 (b), 4:49 e 4:57, sull'orologio corretto), il riferimento di
   * Alessio: il «1 +» che nasce è sfocato a ~233 ms, morbido ma leggibile a ~267, quasi nitido a ~300 e nitido dal colmo;
   * la larghezza dei suoi segni dice una sfocatura sotto il dp da ~267 ms, mentre l'opacità sale piano fino a ~630
   * (`sessioni/f3-12c/rapporto.md`). Il render rallentato «Select → Done» (l'analisi fotogramma per fotogramma, rilievo 5,
   * sul Laplaciano normalizzato alla fase del render) mette il 90% della nitidezza a ~0,97 della fase: una prima versione
   * di F3.12c lo seguiva (nitido a 1,25, ~630 ms) e sulla tavola a tempo vero la scritta restava sfocata fino a ~430 ms,
   * 130 ms dopo Apple. Del rilievo resta la continuità: nessuno scalino e nessun superamento.
   */
  const val BornFocusEnd: Float = 1f

  /** La forma della rampa della sfocatura di chi nasce verso [BornFocusEnd]: vedi lì. */
  const val BornFocusPower: Float = 7f

  /**
   * La parte della sfocatura di chi nasce che scende **piano** (F3.12c): 0,08 del massimo (~0,4 dp su un'icona di 44 dp),
   * con la rampa `(1 − x)^`[BornFocusTailPower] fino a [BornFocusEnd]; il resto scende presto, con [BornFocusPower]. Il
   * Laplaciano del simbolo sente soprattutto i decimi di dp: con la sola rampa veloce saliva dal 20 al 90% in tre
   * fotogrammi (l'analisi rifatta su F3.12c), con questa coda in ~90 ms. Nella clip a tempo vero la larghezza dei segni
   * dice ~0,4 dp a 300–367 ms, ~0,2 a 400, poi niente: la stessa coda.
   */
  const val BornFocusTail: Float = 0.08f

  /** La forma della coda lenta della sfocatura di chi nasce: vedi [BornFocusTail]. */
  const val BornFocusTailPower: Float = 1.5f

  /** Inizio del simbolo nella comparsa per lente (R4 B3, fiducia media: ~107 ms sui 430). */
  const val LensFrom: Float = 0.25f

  /** Vedi [LensFrom]. */
  const val LensTo: Float = 1f

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
   * Il simbolo che **scompare per lente** (R4 B3): 0,44, zero a ~136 ms sui 470. La coda traslucida diventa invisibile
   * prima dello zero: con 0,40 S4 lo vedeva già sparito a 101–118 ms, contro ~130 di Apple. Correzione del 17 novembre
   * 2026; la durata visibile effettiva va confermata in mano. Il cambio di contesto conserva la sua curva.
   */
  const val LensExitSpan: Float = 0.44f

  /**
   * Lo stesso per il contenuto di un vetro che **confluisce in un vicino** (F3.12c): 0,65, cioè via a 0,43 del volo (~230
   * ms sui 540 del riposo a vista), al massimo della sfocatura a ~175 ms. In Apple a tempo vero (R4 B2 (b), 4:51 e 4:59,
   * classe B) il «1 +» di chi confluisce resta visibile **sfocato** e viaggia col suo vetro dentro chi accoglie: nitido a
   * 33–67 ms, sfocato e ancora leggibile a 100–133, molto sfocato a 167, una traccia a 200, sparito a ~230 (la «massa»
   * dei simboli: 78–90% a 33 ms, 62–74% a 67, 36–50% a 100, 15–26% a 133). Fino a F3.12a valeva [ExitSpan], la fase del
   * render, e spariva a ~80 ms. La **staffetta** (un vetro che confluisce in chi nasce da lui: è lo stesso vetro che cambia
   * identità, «Select → Done») resta su [ExitSpan].
   */
  const val MergeExitSpan: Float = 0.65f

  /** In quanta parte del volo un contenuto che resta torna nitido dall'aspetto che aveva. */
  const val RecoverSpan: Float = 0.5f
}

/**
 * Curve separate perché il 2× deve essere visibile, non consumarsi mentre α è ancora zero (review F3, C R8).
 * R4 B3 e fotogrammi f5404–f5412: a 167–234 ms il simbolo è ancora grande e traslucido, a ~367 quasi contratto;
 * l'ultimo assestamento arriva al bilancio di 430 ms. Numeri tarati sul banco il 17 novembre 2026, fiducia media.
 */
internal object GlassPieceContentCurves {
  val lensScale = GlassContentCurve(
    floatArrayOf(GlassPieceContentDefaults.LensFrom, 0.39f, 0.545f, 0.70f, 0.86f, GlassPieceContentDefaults.LensTo),
    floatArrayOf(GlassPieceContentDefaults.LensScale, 1.99f, 1.90f, 1.35f, 1.04f, 1f),
  )
  val lensAlpha = GlassContentCurve(
    floatArrayOf(GlassPieceContentDefaults.LensFrom, 0.39f, 0.545f, 0.70f, 0.86f, GlassPieceContentDefaults.LensTo),
    floatArrayOf(0f, 0.22f, 0.40f, 0.72f, 0.96f, 1f),
  )
  val lensBlur = GlassContentCurve(
    floatArrayOf(GlassPieceContentDefaults.LensFrom, 0.39f, 0.545f, 0.70f, 0.86f, GlassPieceContentDefaults.LensTo),
    floatArrayOf(1f, 0.12f, 0.08f, 0.02f, 0f, 0f),
  )
  val bornAlpha = GlassContentCurve(
    floatArrayOf(GlassPieceContentDefaults.BornAlphaAt, 0.50f, 0.60f, 0.70f, 0.78f, 1f, GlassPieceContentDefaults.BornAlphaEnd),
    floatArrayOf(0f, 0.12f, 0.42f, 0.78f, 0.87f, 0.96f, 1f),
  )
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
      // Solo l'opacità è anticipata e più lenta (correzione F3); scala e fuoco tengono l'orologio di F3.12c.
      val u = glassPieceBornU(p)
      if (p <= GlassPieceContentDefaults.BornAlphaAt) {
        GlassContentInvisible
      } else {
        GlassContentLook(
          alpha = if (blurAvailable) GlassPieceContentCurves.bornAlpha.at(p)
            else GlassContentCurves.alpha(GlassContentRole.Born, u, false),
          scale = GlassContentCurves.scale(GlassContentRole.Born, u),
          blur = glassPieceBornBlur(max(p, GlassPieceContentDefaults.BornAt)) * peak,
        )
      }
    }
    GlassPieceContentRole.Lens -> {
      if (p <= GlassPieceContentDefaults.LensFrom) {
        GlassContentInvisible
      } else {
        GlassContentLook(
          alpha = GlassPieceContentCurves.lensAlpha.at(p),
          scale = GlassPieceContentCurves.lensScale.at(p),
          blur = peak * GlassPieceContentCurves.lensBlur.at(p),
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

/**
 * La sfocatura di chi nasce alla [phase] del contenuto, in frazione del massimo (F3.12c): la salita di F3.4 fino al
 * massimo (a u = 0,5), poi `(1 − x)^`[GlassPieceContentDefaults.BornFocusPower] fino a zero a
 * [GlassPieceContentDefaults.BornFocusEnd]. Continua, con la pendenza zero all'arrivo: la nitidezza sale col vetro che
 * arriva al colmo, senza lo scalino degli ultimi due fotogrammi e senza superare il riposo. Pura.
 */
internal fun glassPieceBornBlur(phase: Float): Float {
  val u = glassPieceBornU(phase)
  if (u <= GlassContentDefaults.PeakAt) return GlassContentCurves.blurRise(u)
  val from = GlassPieceContentDefaults.BornAt + (GlassContentDefaults.PeakAt - GlassContentDefaults.EnterAt) /
    (1f - GlassContentDefaults.EnterAt) * (GlassPieceContentDefaults.BornSharp - GlassPieceContentDefaults.BornAt)
  val x = ((phase - from) / (GlassPieceContentDefaults.BornFocusEnd - from)).coerceIn(0f, 1f)
  val rest = (1f - x).toDouble()
  val tail = GlassPieceContentDefaults.BornFocusTail.toDouble()
  return ((1.0 - tail) * rest.pow(GlassPieceContentDefaults.BornFocusPower.toDouble()) +
    tail * rest.pow(GlassPieceContentDefaults.BornFocusTailPower.toDouble())).toFloat()
}

/** Vero quando [content] ha finito di muoversi alla [phase]: da lì è fermo (o sparito, per chi esce). */
internal fun glassPieceContentDone(content: GlassPieceContent, phase: Float): Boolean = when (content.role) {
  GlassPieceContentRole.Rest, GlassPieceContentRole.Hidden -> true
  GlassPieceContentRole.Born -> phase >= GlassPieceContentDefaults.BornAlphaEnd &&
    phase >= GlassPieceContentDefaults.BornFocusEnd
  GlassPieceContentRole.Lens -> phase >= GlassPieceContentDefaults.LensTo
  GlassPieceContentRole.Exit -> phase / glassPieceExitSpan(content) >= GlassContentDefaults.ExitEnd
  GlassPieceContentRole.Recover -> phase >= GlassPieceContentDefaults.RecoverSpan
}

/** La parte del volo in cui sta l'uscita di [content]: la sua, se è un numero sensato, altrimenti quella di serie. */
private fun glassPieceExitSpan(content: GlassPieceContent): Float =
  if (content.span.isFinite() && content.span > 0f) content.span else GlassPieceContentDefaults.ExitSpan

/**
 * Che cosa fa il vetro di un pezzo in un viaggio, per il suo contenuto: la sola parte di `GlassPlaneChange` che conta qui.
 * [MergesIntoNeighbour] (F3.12c) è chi se ne va confluendo in un vicino vero (non la staffetta): il suo contenuto ha
 * [GlassPieceContentDefaults.MergeExitSpan].
 */
internal enum class GlassPieceArrival { Stays, BornFromNeighbour, ThroughLens, Instant, MergesIntoNeighbour }

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
    // Il simbolo di chi scompare per lente ha i tempi della scomparsa (R4 B3); chi confluisce in un vicino quelli della
    // confluenza a tempo vero (F3.12c, R4 B2 (b)); la staffetta quelli del cambio di contesto (R4 B2 (d)).
    val span = when (arrival) {
      GlassPieceArrival.ThroughLens -> GlassPieceContentDefaults.LensExitSpan
      GlassPieceArrival.MergesIntoNeighbour -> GlassPieceContentDefaults.MergeExitSpan
      else -> GlassPieceContentDefaults.ExitSpan
    }
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
    GlassPieceArrival.Stays, GlassPieceArrival.MergesIntoNeighbour -> when (now) {
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
) = FluidGlassContentTransitionWithShape(
  targetState, modifier, key, blurReference, contentColor, contentAlignment, shapeKey = null, content = content,
)

/**
 * [FluidGlassContentTransition] che sa anche **la forma del vetro** che porta il contenuto (F4A.5b): quando [shapeKey]
 * cambia e il contenuto resta lo stesso, il contenuto si ammorbidisce e torna nitido ([GlassContentJourney.soften]), come
 * il «+» della barra di Apple mentre la capsula si chiude su di lui. Null, o una chiave che non cambia: il comportamento
 * di [FluidGlassContentTransition], al bit. Il primo valore non ammorbidisce niente: la pagina compare, non cambia.
 */
@Composable
internal fun <S> FluidGlassContentTransitionWithShape(
  targetState: S,
  modifier: Modifier = Modifier,
  key: (S) -> Any? = { it },
  blurReference: Dp = GlassContentDefaults.BlurReference,
  contentColor: ((S) -> Color)? = null,
  contentAlignment: Alignment = Alignment.Center,
  shapeKey: Any? = null,
  labelAnchor: FluidGlassLabelAnchor? = null,
  content: @Composable (S) -> Unit,
) {
  val policy = LocalFluidMotionPolicy.current
  val blurAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
  val timing = remember(policy.durationScale) {
    GlassContentTiming(durationMs = GlassContentDefaults.DurationMs * policy.durationScale.coerceAtLeast(0.01f))
  }
  val targetKey = key(targetState)
  val density = LocalDensity.current
  val referencePx = with(density) { blurReference.toPx() }
  val glyphPx = referencePx * GlassContentDefaults.GlyphFraction

  // Lo stato di ogni chiave: chi esce continua a disegnare il proprio contenuto anche quando lo stato è cambiato.
  val states = remember { HashMap<Any?, S>() }
  states[targetKey] = targetState

  var journey by remember { mutableStateOf(GlassContentJourney.rest(targetKey, timing, blurAvailable)) }
  // Il tempo del viaggio in corso. Lo leggono i `graphicsLayer` (ogni fotogramma ridisegna, non ricompone) e il
  // colore solo mentre un colore sta cambiando.
  var nowMs by remember { mutableDoubleStateOf(0.0) }
  // Il colore da cui parte il cambio di colore in corso; `null` quando non ce n'è uno.
  var colorFrom by remember { mutableStateOf<Color?>(null) }

  // Va consultata a ogni cambio di policy, anche senza una nuova chiave. L'effetto con `running = false` cancella
  // l'orologio in corso; nessun fotogramma della vecchia corsa può rimettere strati o tinta dopo l'arrivo.
  val policyJourney = journey.withReducedMotion(policy.reducedMotion)
  if (policyJourney !== journey) {
    journey = policyJourney
    nowMs = 0.0
    colorFrom = null
  }

  // La forma vista per ultima: un contenitore, non uno stato (cambiarla non deve ricomporre).
  val shapeSeen = remember { arrayOf(shapeKey) }
  val reshaped = shapeSeen[0] != shapeKey
  shapeSeen[0] = shapeKey
  if (reshaped && !policy.reducedMotion && journey.targetKey == targetKey) journey = journey.soften(nowMs)

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
      journey.retarget(nowMs, targetKey, resumeLabel = labelAnchor != null)
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
      if (frame.finished && labelAnchor?.waitsForSpace(referencePx) != true) {
        journey = journey.settle()
        nowMs = 0.0
        colorFrom = null
        break
      }
      journey = journey.pruned(nowMs)
    }
  }

  // Il margine del layer: una gaussiana tagliata al bordo del riquadro darebbe una macchia squadrata.
  val marginPx = ceil(3f * GlassContentDefaults.PeakBlur * glyphPx).toInt()
  val marginDp = with(density) { marginPx.toDp() }

  val layers: @Composable () -> Unit = {
    for (layer in journey.layers) {
      if (layer.key == null) continue
      val state = states[layer.key] ?: continue
      identity(layer.id) {
        val coordinates = if (labelAnchor == null) null else remember {
          mutableStateOf<LayoutCoordinates?>(null, neverEqualPolicy())
        }
        val isTarget = layer.role != GlassContentRole.Exit
        val labelModifier = when {
          coordinates == null -> Modifier
          isTarget -> Modifier.onPlaced {
            coordinates.value = it
            labelAnchor?.targetCoordinates = it
          }
          else -> Modifier.onPlaced { coordinates.value = it }.clearAndSetSemantics { hideFromAccessibility() }
        }
        Box(
          labelModifier
            .layout { measurable, constraints ->
              // Il layer è più grande del contenuto di [marginPx] per lato, ma il layout non se ne accorge.
              val placeable = measurable.measure(constraints.offset(2 * marginPx, 2 * marginPx))
              layout(max(0, placeable.width - 2 * marginPx), max(0, placeable.height - 2 * marginPx)) {
                placeable.place(-marginPx, -marginPx)
              }
            }
            .graphicsLayer {
              val natural = journey.look(layer, nowMs)
              val look = if (labelAnchor != null && layer.role != GlassContentRole.Rest)
                labelAnchor.look(natural, coordinates?.value, referencePx) else natural
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
  if (labelAnchor == null) {
    Box(modifier, contentAlignment = contentAlignment, content = { layers() })
  } else {
    // La Row riceve solo la misura dell'arrivo. Chi esce mantiene la sua misura ed è centrato nello stesso posto,
    // anche quando è più largo: altrimenti terrebbe aperta la capsula finché non è sparito.
    Layout(content = layers, modifier = modifier) { measurables, constraints ->
      val children = measurables.map { it.measure(constraints) }
      val target = children.last()
      val extraRecordingPadding = IntSize(
        (children.maxOf { it.width } - target.width + 1) / 2,
        (children.maxOf { it.height } - target.height + 1) / 2,
      )
      layout(target.width, target.height) {
        labelAnchor.recordingPadding = extraRecordingPadding
        children.forEach { it.place((target.width - it.width) / 2, (target.height - it.height) / 2) }
      }
    }
  }
}

/** Collega la misura dell'etichetta alla stessa sagoma che ritaglia la registrazione del pezzo. */
@Stable
internal class FluidGlassLabelAnchor {
  var piece by mutableStateOf<FluidGlassPiece?>(null)
  var targetCoordinates: LayoutCoordinates? = null
  /** La registrazione che avvolge le etichette deve poter conservare anche il layer uscente. */
  var recordingPadding by mutableStateOf(IntSize.Zero)

  /** Se l'orologio si chiude in anticipo sul vetro, il gate resta attivo fino a quando il testo ha posto. */
  fun waitsForSpace(referencePx: Float): Boolean {
    val node = piece?.coordinates?.takeIf { it.isAttached } ?: return false
    if (targetCoordinates?.isAttached != true || piece?.contentFlightForCurrentLayout(node.size.width.toFloat())?.clip == null)
      return false
    return look(GlassContentLook.Sharp, targetCoordinates, referencePx).alpha < 1f
  }

  fun look(look: GlassContentLook, coordinates: LayoutCoordinates?, referencePx: Float): GlassContentLook {
    val piece = piece ?: return look.copy(alpha = 0f)
    val node = piece.coordinates?.takeIf { it.isAttached } ?: return look.copy(alpha = 0f)
    val label = coordinates?.takeIf { it.isAttached } ?: return look.copy(alpha = 0f)
    val flight = piece.contentFlightForCurrentLayout(node.size.width.toFloat())
    val origin = node.localPositionOf(label, Offset.Zero)
    val end = node.localPositionOf(label, Offset(label.size.width.toFloat(), label.size.height.toFloat()))
    val bounds = Rect(origin, end)
    val shift = flight?.offset ?: Offset.Zero
    val clip = flight?.clip ?: Rect(0f, 0f, node.size.width.toFloat(), node.size.height.toFloat())
    return glassLabelLook(look, bounds.translate(shift), clip, flight?.radii ?: piece.content.placeRadii, referencePx)
  }
}

/**
 * La scritta diventa visibile quando la sua misura di layout, a scala R7, entra col margine della gaussiana.
 * La rampa usa lo spazio libero dal punto più stretto della capsula, anche agli angoli. Vale nei due versi, così
 * un'uscente ancora visibile si spegne prima che il bordo possa tagliarla. Il ritaglio resta la difesa finale.
 */
internal fun glassLabelLook(
  look: GlassContentLook,
  label: Rect,
  clip: Rect,
  radii: FluidCornerRadii,
  referencePx: Float,
): GlassContentLook {
  // Un'entrante che ridiventa uscente non accumula le due riduzioni di scala.
  val scale = max(look.scale, GlassContentDefaults.ExitScale)
  if (look.alpha <= 0f || clip.isEmpty || !clip.isFinite || label.isEmpty || !label.isFinite) {
    return look.copy(alpha = 0f, scale = scale)
  }
  val halfWidth = label.width * scale / 2f
  val halfHeight = label.height * scale / 2f
  val x = label.center.x - clip.center.x
  val y = label.center.y - clip.center.y
  val clipHalfWidth = clip.width / 2f
  val clipHalfHeight = clip.height / 2f
  fun clearance(dx: Float, dy: Float): Float {
    val corner = if (dy < 0f) {
      if (dx < 0f) radii.topLeft else radii.topRight
    } else {
      if (dx < 0f) radii.bottomLeft else radii.bottomRight
    }
    val radius = corner.coerceIn(0f, min(clipHalfWidth, clipHalfHeight))
    val qx = abs(dx) - clipHalfWidth + radius
    val qy = abs(dy) - clipHalfHeight + radius
    val ox = max(qx, 0f)
    val oy = max(qy, 0f)
    return radius - sqrt(ox * ox + oy * oy) - min(max(qx, qy), 0f)
  }
  val space = min(
    min(clearance(x - halfWidth, y - halfHeight), clearance(x + halfWidth, y - halfHeight)),
    min(clearance(x - halfWidth, y + halfHeight), clearance(x + halfWidth, y + halfHeight)),
  )
  val sigma = glassContentSigmaPx(look.blur, referencePx)
  // HWUI parte da σ 0,5 px per qualsiasi raggio positivo. Un pixel copre l'antialias del bordo.
  val guard = 1f + if (sigma > 0.05f) 3f * max(sigma, 0.5f) else 0f
  val fit = glassContentSmoothstep(guard, guard + max(1f, referencePx * GlassContentDefaults.PeakBlur), space)
  return look.copy(alpha = look.alpha * fit, scale = scale)
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
