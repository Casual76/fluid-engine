/*
 * DA UNA SERIE DI LETTURE A UN LATO che non sfarfalla (piano «iOS in Android», 3.9 e F2.1).
 *
 * La sonda (`GlassProbe.kt`) dà ogni tanto la luminanza media dello sfondo sotto un vetro. Il vetro piccolo deve
 * decidere da quale lato stare (chiaro o scuro: R4 B6, il «giro») senza saltare avanti e indietro quando lo sfondo sta
 * a metà, quando una scacchiera passa sotto, quando la lettura trema di un centesimo. Tre difese, come in R3 cap. 3:
 *
 * - una **media mobile esponenziale** a tempo continuo, con costante di tempo [GlassAdaptiveTimeConstantMillis];
 * - **due soglie distanti** ([GlassAdaptiveDarkBelow], [GlassAdaptiveLightAbove]): si va sullo scuro sotto la prima,
 *   si torna sul chiaro sopra la seconda, in mezzo si resta dove si è;
 * - una **permanenza minima** ([GlassAdaptiveMinDwellMillis]) dopo ogni giro.
 *
 * Tutti e tre i numeri sono i punti di partenza di R3 (costante 150–250 ms, soglie attorno a 0,45 e 0,62, permanenza
 * 300–500 ms): **ipotesi da tarare in S3**, non misure. Sono qui e non in un profilo perché sono il comportamento del
 * filtro, non il gusto del materiale; F2.2 può passarne altri al costruttore.
 *
 * **La media a tempo continuo.** Le letture arrivano a istanti irregolari, e fra una lettura e l'altra lo sfondo si
 * considera fermo al valore dell'ultima (è così che la sonda funziona: legge quando qualcosa cambia). La media quindi
 * non salta alla lettura nuova, la *insegue*: al tempo t vale `l + (m - l)·e^(-(t - t₀)/τ)`, dove `l` è l'ultima
 * lettura e `m` la media al suo arrivo. Il conto giusto anche quando fra due letture passa un secondo, e il motivo per
 * cui il filtro ha una **scadenza** ([nextDeadlineMillis]): dopo l'ultima lettura la media continua a muoversi, e se
 * attraverserà una soglia lo farà a un istante calcolabile, quando nessuna lettura nuova arriverà a ricordarlo. Chi
 * possiede il filtro lo richiama allora ([advance]).
 *
 * **L'uscita è un bersaglio**, non un'animazione: il lato (`dark`) è un booleano che cambia una volta per giro, e chi
 * lo usa lo anima con i tempi di R4 B6 (ritardo 130 ms, corpo in 300 ms, inchiostro in 500–700 ms: F2.3). La
 * luminanza, la trama e il colore dell'ultima lettura sono bersagli continui per i vetri grandi, che non girano ma
 * scuriscono in continuo (R4 A6): li anima F2.2.
 *
 * **La prima lettura decide subito.** Un vetro che nasce sopra una foto scura non deve prima mostrarsi chiaro (il lato
 * del tema) e poi girare: alla prima lettura il lato si sceglie con le stesse due soglie partendo da quello del tema,
 * senza attesa. La permanenza vale dai giri successivi.
 *
 * Puro: gli istanti glieli dà chi chiama. Lo prova `GlassAdaptiveFilterTest`.
 */
package dev.antigravity.fluidengine.ui.glass.field

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln

/** La costante di tempo della media, in ms (R3 cap. 3: 150–250). Ipotesi da tarare in S3. */
internal const val GlassAdaptiveTimeConstantMillis: Float = 200f

/** Sotto questa luminanza media (0..1) il vetro piccolo va sul lato scuro (R3 cap. 3: attorno a 0,45). Da tarare in S3. */
internal const val GlassAdaptiveDarkBelow: Float = 0.45f

/** Sopra questa torna sul lato chiaro (R3 cap. 3: attorno a 0,62). Da tarare in S3. */
internal const val GlassAdaptiveLightAbove: Float = 0.62f

/** Quanto resta su un lato, al minimo, dopo un giro (R3 cap. 3: 300–500 ms). Da tarare in S3. */
internal const val GlassAdaptiveMinDwellMillis: Long = 400L

/**
 * Ciò che il filtro consegna: il lato deciso e i numeri dell'ultima lettura, come bersagli. Uguaglianza strutturale:
 * scriverlo uguale in uno stato di snapshot non invalida niente.
 *
 * @property dark il lato: vero = scuro. Cambia una volta per giro.
 * @property luminance la luminanza media dell'ultima lettura (0..1), per i vetri che scuriscono in continuo.
 * @property smoothedLuminance la media del filtro all'istante della consegna: quella che decide il lato.
 * @property texture quanto lo sfondo è fitto (0 = piatto, 1 = testo o immagine fitta): `GlassBackdropStats.texture`.
 * @property color il colore medio dell'ultima lettura (per il colore dell'ombra).
 * @property measured falso finché non è arrivata la prima lettura: il lato è ancora quello del tema.
 */
@Immutable
internal data class GlassAdaptiveTarget(
  val dark: Boolean,
  val luminance: Float,
  val smoothedLuminance: Float,
  val texture: Float,
  val color: Color,
  val measured: Boolean,
) {
  companion object {
    /** Prima di ogni lettura: il lato del tema e nient'altro. */
    fun unmeasured(dark: Boolean): GlassAdaptiveTarget = GlassAdaptiveTarget(
      dark = dark,
      luminance = if (dark) 0f else 1f,
      smoothedLuminance = if (dark) 0f else 1f,
      texture = 0f,
      color = Color.Unspecified,
      measured = false,
    )
  }
}

/**
 * Il filtro di una sonda. Vedi l'intestazione del file.
 *
 * @param initialDark il lato del tema (`GlassDefaults.isDarkSurface()`): vale finché non arriva una lettura, ed è il
 *   punto da cui la prima lettura sceglie.
 */
internal class GlassAdaptiveFilter(
  initialDark: Boolean,
  private val timeConstantMillis: Float = GlassAdaptiveTimeConstantMillis,
  private val darkBelow: Float = GlassAdaptiveDarkBelow,
  private val lightAbove: Float = GlassAdaptiveLightAbove,
  private val minDwellMillis: Long = GlassAdaptiveMinDwellMillis,
) {
  init {
    require(darkBelow < lightAbove) { "le due soglie devono stare distanti: $darkBelow non è sotto $lightAbove" }
    require(timeConstantMillis > 0f) { "la costante di tempo deve essere positiva" }
  }

  /** Il lato deciso adesso. */
  var dark: Boolean = initialDark
    private set

  /** Se è arrivata almeno una lettura. */
  var measured: Boolean = false
    private set

  /** Quanti giri ha fatto. Per i test e per la diagnostica. */
  var flips: Int = 0
    private set

  private var mean = 0f
  private var meanAt = 0L
  private var held = 0f
  private var lastFlipAt = Long.MIN_VALUE

  /** La media a [nowMillis], con lo sfondo fermo all'ultima lettura. Prima di ogni lettura: NaN. */
  fun smoothedAt(nowMillis: Long): Float {
    if (!measured) return Float.NaN
    val elapsed = (nowMillis - meanAt).coerceAtLeast(0L)
    return held + (mean - held) * exp(-elapsed / timeConstantMillis)
  }

  /**
   * Una lettura nuova, [luminance] (0..1), a [nowMillis]. Dice se il lato è cambiato. Una lettura non finita (NaN)
   * si scarta: è una lettura fallita, non uno sfondo nero.
   */
  fun sample(nowMillis: Long, luminance: Float): Boolean {
    if (!luminance.isFinite()) return false
    val value = luminance.coerceIn(0f, 1f)
    if (!measured) {
      measured = true
      mean = value
      held = value
      meanAt = nowMillis
      val decided = decide(dark, value)
      val changed = decided != dark
      if (changed) {
        dark = decided
        flips++
      }
      // La prima scelta non aspetta, ma conta come un giro per la permanenza: il secondo giro non è immediato.
      lastFlipAt = nowMillis
      return changed
    }
    // Prima la media corre fino a qui con lo sfondo di prima, poi lo sfondo diventa quello nuovo.
    mean = smoothedAt(nowMillis)
    meanAt = nowMillis
    held = value
    return evaluate(nowMillis)
  }

  /** Nessuna lettura nuova: la media è andata avanti da sola fino a [nowMillis]. Dice se il lato è cambiato. */
  fun advance(nowMillis: Long): Boolean {
    if (!measured) return false
    return evaluate(nowMillis)
  }

  /**
   * Quando richiamare [advance] se non arriva niente: l'istante in cui la media attraverserà la soglia verso cui sta
   * andando, o la fine della permanenza se è più tardi. `Long.MAX_VALUE` se, con lo sfondo fermo all'ultima lettura,
   * il lato non cambierà più.
   */
  fun nextDeadlineMillis(): Long {
    if (!measured) return Long.MAX_VALUE
    val threshold = when {
      !dark && held < darkBelow -> darkBelow
      dark && held > lightAbove -> lightAbove
      else -> return Long.MAX_VALUE
    }
    // Quando `held + (mean - held)·e^(-t/τ)` vale la soglia. Se la media è già oltre, adesso.
    val ratio = (mean - held) / (threshold - held)
    val crossing = if (ratio <= 1f) {
      meanAt
    } else {
      meanAt + ceil(timeConstantMillis * ln(ratio)).toLong() + 1L
    }
    val dwellEnd = if (lastFlipAt == Long.MIN_VALUE) Long.MIN_VALUE else lastFlipAt + minDwellMillis
    return maxOf(crossing, dwellEnd)
  }

  /** Il bersaglio da consegnare a [nowMillis], dall'ultima lettura ([stats]). */
  fun target(nowMillis: Long, stats: GlassBackdropStats?): GlassAdaptiveTarget {
    if (!measured || stats == null) return GlassAdaptiveTarget.unmeasured(dark)
    return GlassAdaptiveTarget(
      dark = dark,
      luminance = stats.luminance,
      smoothedLuminance = smoothedAt(nowMillis),
      texture = stats.texture,
      color = stats.color,
      measured = true,
    )
  }

  private fun evaluate(nowMillis: Long): Boolean {
    if (lastFlipAt != Long.MIN_VALUE && nowMillis - lastFlipAt < minDwellMillis) return false
    val decided = decide(dark, smoothedAt(nowMillis))
    if (decided == dark) return false
    dark = decided
    lastFlipAt = nowMillis
    flips++
    return true
  }

  /** Le due soglie: sotto la prima scuro, sopra la seconda chiaro, in mezzo [current]. */
  private fun decide(current: Boolean, value: Float): Boolean = when {
    value < darkBelow -> true
    value > lightAbove -> false
    else -> current
  }
}
