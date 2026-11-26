/*
 * engine-scan (4.0, F8.17): l'inseguimento del foglio fra un fotogramma e l'altro, la scritta che guida e lo scatto da
 * solo quando il telefono è fermo, come la modalità «Auto» di VisionKit. Codice del Fluid Engine.
 */
package dev.antigravity.fluidengine.scan

import androidx.compose.runtime.Immutable

/**
 * Cosa dice la scritta sopra la fotocamera, come VisionKit («Position the document in view», «Move closer», «Hold
 * still»). Non è un `enum` di proposito (regola 3.11): un consiglio nuovo è una costante in più.
 */
@Immutable
@JvmInline
value class FluidScanHint private constructor(private val id: Int) {
  override fun toString(): String = when (id) {
    NoneId -> "Find"
    CloserId -> "Closer"
    SteadyId -> "Steady"
    CapturingId -> "Capturing"
    else -> "Ready"
  }

  companion object {
    private const val NoneId = 0
    private const val CloserId = 1
    private const val SteadyId = 2
    private const val CapturingId = 3
    private const val ReadyId = 4

    /** Nessun foglio: «Inquadra un documento». */
    val Find: FluidScanHint = FluidScanHint(NoneId)
    /** Un foglio piccolo: «Avvicinati». */
    val Closer: FluidScanHint = FluidScanHint(CloserId)
    /** Un foglio che si muove ancora: «Tieni fermo». */
    val Steady: FluidScanHint = FluidScanHint(SteadyId)
    /** Lo scatto automatico in corso: «Scansione…». */
    val Capturing: FluidScanHint = FluidScanHint(CapturingId)
    /** Manuale, foglio trovato: nessuna scritta, si scatta col tasto. */
    val Ready: FluidScanHint = FluidScanHint(ReadyId)
  }
}

/** Lo stato dell'inseguimento dopo un fotogramma. */
@Immutable
data class FluidScanTrack(
  /** Il foglio ammorbidito da mostrare, `null` se non c'è. */
  val quad: FluidScanQuad?,
  /** Larghezza su altezza dell'immagine dritta in cui sta [quad]. */
  val aspect: Float,
  /** Quanto manca allo scatto automatico: da 0 (appena trovato) a 1 (si scatta). */
  val steadiness: Float,
  /** Vero sul fotogramma in cui lo scatto automatico deve partire (una volta sola per foglio). */
  val shouldCapture: Boolean,
  val hint: FluidScanHint,
)

/**
 * L'inseguimento del foglio: ammorbidisce gli angoli (il riconoscimento trema di qualche pixel), conta da quanto il
 * telefono è fermo e dice quando scattare da solo. Dopo uno scatto aspetta un foglio nuovo: che quello di prima sparisca
 * o si sposti, come VisionKit, che non fotografa due volte la stessa pagina.
 *
 * Le misure stanno in [FluidScanTrackerDefaults].
 */
class FluidScanTracker(
  private val steadyMillis: Long = FluidScanTrackerDefaults.SteadyMillis,
  private val stillDistance: Float = FluidScanTrackerDefaults.StillDistance,
  private val smoothing: Float = FluidScanTrackerDefaults.Smoothing,
  private val lostMillis: Long = FluidScanTrackerDefaults.LostMillis,
) {
  private var smooth: FluidScanQuad? = null
  private var aspect = 1f
  private var stillSince = -1L
  private var lastSeen = -1L
  private var captured: FluidScanQuad? = null
  private var armed = true

  /** Il fotogramma nuovo: [detection] trovato all'istante [timeMillis] (o `null`), con lo scatto automatico [auto]. */
  fun update(detection: FluidScanDetection?, timeMillis: Long, auto: Boolean = true): FluidScanTrack {
    if (detection == null) {
      if (lastSeen >= 0 && timeMillis - lastSeen > lostMillis) {
        smooth = null
        stillSince = -1L
        // Il foglio di prima è sparito: il prossimo che arriva si può scattare.
        if (!armed) { armed = true; captured = null }
      }
      val shown = smooth
      return FluidScanTrack(shown, aspect, 0f, false, if (shown == null) FluidScanHint.Find else FluidScanHint.Steady)
    }
    lastSeen = timeMillis
    aspect = detection.aspect
    val previous = smooth
    val next = if (previous == null) detection.quad else previous.lerp(detection.quad, smoothing)
    val moved = previous == null || detection.quad.distanceTo(previous, aspect) > stillDistance
    if (moved) stillSince = timeMillis else if (stillSince < 0) stillSince = timeMillis
    smooth = next
    // Un foglio che si è spostato di più di un quinto dopo lo scatto è un foglio nuovo.
    val last = captured
    if (!armed && last != null && next.distanceTo(last, aspect) > FluidScanTrackerDefaults.NewSheetDistance) {
      armed = true
      captured = null
    }
    val small = next.area(aspect) / aspect < FluidScanTrackerDefaults.CloserAreaFraction
    val held = (timeMillis - stillSince).coerceAtLeast(0L)
    val steadiness = if (!auto || !armed || small) 0f else (held.toFloat() / steadyMillis).coerceIn(0f, 1f)
    val capture = auto && armed && !small && held >= steadyMillis
    if (capture) {
      armed = false
      captured = next
    }
    val hint = when {
      small -> FluidScanHint.Closer
      !auto -> FluidScanHint.Ready
      capture -> FluidScanHint.Capturing
      !armed -> FluidScanHint.Ready
      else -> FluidScanHint.Steady
    }
    return FluidScanTrack(next, aspect, steadiness, capture, hint)
  }

  /** Uno scatto fatto col tasto: il foglio di adesso non si riscatta da solo. */
  fun capturedManually() {
    armed = false
    captured = smooth
  }

  /** Ricomincia da zero (la fotocamera che riparte). */
  fun reset() {
    smooth = null
    stillSince = -1L
    lastSeen = -1L
    captured = null
    armed = true
  }
}

/** Le misure dell'inseguimento. */
object FluidScanTrackerDefaults {
  /**
   * Quanto deve restare fermo il foglio prima dello scatto automatico: 900 ms. A occhio (25 novembre 2026), sulle
   * riprese di Note di iOS 18 e 26, dove fra «Tieni fermo» e lo scatto passa circa un secondo. Fiducia media.
   */
  const val SteadyMillis: Long = 900L

  /**
   * Il tremito che vale ancora come fermo: l'1,5% della diagonale fra due fotogrammi. A occhio: una mano che regge il
   * telefono trema di circa mezzo grado.
   */
  const val StillDistance: Float = 0.015f

  /** L'ammorbidimento degli angoli: ogni fotogramma ne prende il 45%. A occhio, senza ritardo visibile a 30 fps. */
  const val Smoothing: Float = 0.45f

  /** Dopo quanto un foglio che non si vede più è perso: 350 ms. A occhio: tre o quattro fotogrammi d'analisi. */
  const val LostMillis: Long = 350L

  /** Lo spostamento che fa un foglio nuovo dopo uno scatto: un quinto della diagonale. A occhio. */
  const val NewSheetDistance: Float = 0.2f

  /** Sotto questa parte dell'immagine la scritta chiede di avvicinarsi: il 18%. A occhio, come «Move closer». */
  const val CloserAreaFraction: Float = 0.18f
}
