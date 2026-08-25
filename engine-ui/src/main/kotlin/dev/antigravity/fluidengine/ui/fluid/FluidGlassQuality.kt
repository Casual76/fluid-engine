package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs

/**
 * Quanta ottica il vetro riceve **adesso**, fra 0 e 1.
 *
 * Esiste perché il costo del materiale e la sua leggibilità non arrivano nello stesso momento. Il
 * vetro si guarda quando la pagina è ferma: è lì che l'occhio segue un bordo che piega, una lente
 * che si gonfia, una tinta che trasmette. Durante uno scorrimento veloce nessuna di quelle cose è
 * visibile — passa qualche decina di millisecondi per fotogramma davanti a un occhio che sta
 * seguendo il *movimento* — mentre il conto si paga per intero, ed è il momento in cui il conto fa
 * più danno, perché è l'unico in cui un fotogramma perso si vede.
 *
 * Quindi la qualità è una funzione della velocità, non una costante. A pagina ferma è 1 e il
 * materiale è quello di sempre; sopra [FluidGlassQuality.FullQualityVelocity] scende verso
 * [FluidGlassQuality.ScrollingFloor], e il ritorno è più lento della discesa: scendere in fretta
 * non si nota perché la pagina si sta muovendo, risalire in fretta sì, perché la pagina si è appena
 * fermata ed è esattamente lì che si sta guardando.
 *
 * **Cosa scala e cosa no.** Scalano la lente e i suoi extra — dislocazione, dispersione, ombre,
 * bordo speculare — cioè le parti che descrivono la *forma* del materiale. La sfocatura e la tinta
 * restano intatte, perché sono le due che tengono leggibile il testo stampato sopra il vetro, e un
 * testo che perde contrasto mentre la lista scorre è peggio di qualunque fotogramma in ritardo.
 */
@Stable
class FluidGlassQuality internal constructor() {

  private var _level by mutableFloatStateOf(1f)

  /** 1 = materiale pieno, [ScrollingFloor] = quanto ne resta durante uno scorrimento veloce. */
  val level: Float get() = _level

  /**
   * Aggiorna il livello a partire dallo spostamento di un fotogramma.
   *
   * Prende pixel per fotogramma e non pixel al secondo di proposito: quello che decide se il vetro
   * si vede è quanto la pagina si sposta *fra due fotogrammi disegnati*, e su un dispositivo che sta
   * già perdendo fotogrammi quella distanza cresce da sola — cioè la difesa si stringe proprio
   * quando serve, senza doverlo scrivere da nessuna parte.
   */
  internal fun onScrollDelta(deltaPx: Float) {
    val speed = abs(deltaPx)
    val target = targetLevel(speed)
    _level = if (target < _level) {
      // Giù subito: la pagina si sta muovendo e nessuno vede il cambio.
      target
    } else {
      // Su piano: è il momento in cui si guarda.
      _level + (target - _level) * RecoveryRate
    }
  }

  /** Il gesto è finito e non c'è più inerzia: il materiale torna pieno. */
  internal fun onSettled() {
    _level = 1f
  }

  companion object {
    /**
     * Sotto questo spostamento per fotogramma il materiale resta intero. Circa mezzo centimetro a
     * 60 Hz: uno scorrimento lento, quello in cui si legge mentre si scorre.
     */
    const val FullQualityVelocity: Float = 24f

    /** Sopra questo, il materiale è al minimo. Un lancio vero. */
    const val MinQualityVelocity: Float = 140f

    /**
     * Quanto resta al minimo. Non zero: a zero il vetro *sparisce*, e una superficie che appare e
     * scompare col gesto è molto più evidente di una che si assottiglia.
     */
    const val ScrollingFloor: Float = 0.25f

    /** Frazione della distanza recuperata per fotogramma quando la pagina rallenta. */
    private const val RecoveryRate: Float = 0.12f

    internal fun targetLevel(speedPxPerFrame: Float): Float {
      if (!speedPxPerFrame.isFinite()) return 1f
      if (speedPxPerFrame <= FullQualityVelocity) return 1f
      if (speedPxPerFrame >= MinQualityVelocity) return ScrollingFloor
      val t = (speedPxPerFrame - FullQualityVelocity) / (MinQualityVelocity - FullQualityVelocity)
      return 1f - t * (1f - ScrollingFloor)
    }
  }
}

/**
 * Il livello di qualità in scope. Vale 1 fisso quando nessuno lo sta guidando, quindi una schermata
 * che non aggancia niente si comporta esattamente come prima.
 */
val LocalFluidGlassQuality = compositionLocalOf<FluidGlassQuality?> { null }

@Composable
fun rememberFluidGlassQuality(): FluidGlassQuality = remember { FluidGlassQuality() }

/** Il valore che una superficie deve usare adesso. */
@Composable
fun currentGlassQuality(): Float = LocalFluidGlassQuality.current?.level ?: 1f

/**
 * Collega un contenitore scorrevole al livello di qualità.
 *
 * Sta su `nestedScroll` e non sul `LazyListState` perché così vede *tutto* quello che muove la
 * pagina — trascinamento, inerzia, overscroll, e le liste annidate — e perché una schermata lo
 * aggancia una volta sola al proprio corpo invece che a ogni lista che ci mette dentro.
 */
fun fluidGlassQualityScrollConnection(quality: FluidGlassQuality): NestedScrollConnection =
  object : NestedScrollConnection {

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
      quality.onScrollDelta(available.y)
      return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
      // Un lancio *comincia* veloce: portare giù la qualità qui evita il fotogramma pieno che
      // altrimenti si paga proprio mentre l'inerzia parte.
      quality.onScrollDelta(abs(available.y) / 60f)
      return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
      quality.onSettled()
      return Velocity.Zero
    }
  }
