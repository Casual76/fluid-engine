package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import dev.antigravity.fluidengine.ui.glass.quality.GlassOptics as ResourceOptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Solo il fondale. Il filo e la finitura del materiale hanno la griglia dello schermo. */
internal fun glassBackgroundScale(
  local: Float, optics: ResourceOptics, moving: Boolean, recorded: Float,
): Float {
  // Pieno mantiene l'isteresi locale F7.2. Un nuovo tetto si applica alla prima cattura o alla posa.
  if (moving && recorded > 0f && optics != ResourceOptics.Full) return recorded
  val ceiling = if (optics == ResourceOptics.Reduced && !moving) 1f else optics.backgroundScaleCeiling
  return minOf(local, ceiling)
}

/** Identifica la vista di una pagina interamente coperta. Una Dialog ha una vista propria. */
internal val LocalCoveredGlassView = compositionLocalOf<android.view.View?> { null }

/**
 * F7.18: vero dentro un pannello **davanti** alla pagina: il contenuto di [FluidGlassModalHost] (modali, popover, menu,
 * fogli, avvisi). Quando il tetto della memoria del vetro nega la cattura a uno di questi, la pagina sotto cede il suo
 * credito (`FluidGlassResources.frontDemand`): il pannello davanti ha la precedenza su ciò che copre.
 */
internal val LocalGlassFrontLayer = compositionLocalOf { false }

/** Segnali condivisi fra la finitura e il filtro che campiona lo sfondo, incluso il palco di viaggio. */
internal interface GlassRenderQuality {
  val optics: ResourceOptics
  val inMotion: Boolean
  val limitDecorations: Boolean get() = optics != ResourceOptics.Full && inMotion
}

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
 * bordo speculare — cioè le parti che descrivono la *forma* del materiale. La tinta resta intatta,
 * perché è quella che tiene leggibile il testo stampato sopra il vetro, e un testo che perde
 * contrasto mentre la lista scorre è peggio di qualunque fotogramma in ritardo.
 *
 * La sfocatura scende, ma molto meno della lente: va da 1 a [FluidGlassBlurFloor] (0.45) lungo la
 * stessa rampa, quindi a [ScrollingFloor] resta circa il 59% del raggio (0.45 + 0.55 × 0.25).
 * È la parte più cara della catena e l'unica il cui compito — tenere illeggibile il testo nitido
 * che passa sotto — durante un lancio non esiste, perché a quella velocità non si legge niente;
 * ma sotto quella frazione il vetro smette di sembrare vetro e il cambio si vedrebbe quando la
 * pagina si ferma. La regola della taglia (vedi [glassSizeGain]) lavora sul raggio già scalato
 * dalla qualità. La risoluzione della cattura ha due soli livelli. Ogni pannello usa questo segnale
 * insieme al numero di pixel e alla cadenza dei propri disegni per decidere quando dimezzarla.
 */
@Stable
class FluidGlassQuality internal constructor(
  /**
   * Un tetto imposto da fuori: qualunque altra cosa stia gia' consumando il fotogramma.
   *
   * Lo scorrimento non e' l'unico momento in cui il materiale costa piu' di quanto renda. Anche una
   * transizione di rotta lo e', e molto di piu': mentre due pagine si attraversano ne esistono
   * due, quindi due fondali e due serie di pannelli. Il livello finale e' il **minimo** fra i due,
   * perche' sono due ragioni indipendenti per non pagare e la piu' forte vince.
   */
  private val ceiling: () -> Float = { 1f },
) {

  private var _level by mutableFloatStateOf(1f)
  internal var scrolling by mutableStateOf(false)
    private set

  /**
   * F7.26, review R2 (difetto 4): chi fa risalire il materiale dopo la posa, un fotogramma alla volta. Lo dà
   * [rememberFluidGlassQuality] (lo scope della composizione, col suo orologio dei fotogrammi). Senza, la fine del gesto
   * riporta il materiale intero subito, come fino a F7.25.
   */
  internal var settleScope: CoroutineScope? = null
  private var settleJob: Job? = null

  /** 1 = materiale pieno, [ScrollingFloor] = quanto ne resta durante uno scorrimento veloce. */
  val level: Float get() = minOf(_level, ceiling())

  /**
   * Aggiorna il livello a partire dallo spostamento di un fotogramma.
   *
   * Prende pixel per fotogramma e non pixel al secondo di proposito: quello che decide se il vetro
   * si vede è quanto la pagina si sposta *fra due fotogrammi disegnati*, e su un dispositivo che sta
   * già perdendo fotogrammi quella distanza cresce da sola — cioè la difesa si stringe proprio
   * quando serve, senza doverlo scrivere da nessuna parte.
   */
  internal fun onScrollDelta(deltaPx: Float) = onScrollDelta(deltaPx, 0f)

  internal fun onScrollDelta(deltaPx: Float, horizontalPx: Float) {
    if (deltaPx.isFinite() && deltaPx != 0f || horizontalPx.isFinite() && horizontalPx != 0f) scrolling = true
    // F7.26, review R2 (difetto 3): la velocità è la più grande delle due componenti, come già `scrolling`. Fino a
    // F7.25 contava solo la y, e un pager o una fila lanciati in orizzontale sotto una barra di vetro tenevano il
    // materiale intero per tutto il lancio. Una componente non finita vale ferma.
    val speed = maxOf(if (deltaPx.isFinite()) abs(deltaPx) else 0f, if (horizontalPx.isFinite()) abs(horizontalPx) else 0f)
    val target = targetLevel(speed)
    _level = if (target < _level) {
      // Giù subito: la pagina si sta muovendo e nessuno vede il cambio.
      target
    } else {
      // Su piano: è il momento in cui si guarda.
      _level + (target - _level) * RecoveryRate
    }
  }

  /**
   * Il gesto è finito e non c'è più inerzia: il materiale torna pieno, **con la stessa rampa** della pagina che rallenta
   * ([RecoveryRate] della distanza per fotogramma, da [ScrollingFloor] a pieno in 40 fotogrammi: un terzo di secondo a 120 Hz).
   *
   * F7.26, review R2 (difetto 4): fino a F7.25 tornava pieno in un fotogramma. Nel lancio che si spegne da solo non si
   * vedeva (la coda lenta aveva già recuperato), ma quando il dito **ferma** un lancio veloce il livello saltava dal
   * pavimento a 1 proprio sotto il dito che aveva appena preso la pagina: la lente quattro volte più profonda, la
   * sfocatura dal 59 al 100 %, la frangia che si riaccende. Un nuovo scorrimento interrompe la rampa.
   */
  internal fun onSettled() {
    scrolling = false
    val scope = settleScope
    if (scope == null) {
      _level = 1f
      return
    }
    settleJob?.cancel()
    if (_level >= 1f) return
    settleJob = scope.launch {
      while (_level < 1f) {
        withFrameNanos { }
        if (scrolling) break
        settleFrame()
      }
    }
  }

  /** Un fotogramma della rampa di [onSettled]. */
  internal fun settleFrame() {
    val next = _level + (1f - _level) * RecoveryRate
    _level = if (next >= 1f - SettledEpsilon) 1f else next
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

    /** Sotto questa distanza dal pieno la rampa della posa chiude a 1 (mezzo per cento: non si vede). */
    private const val SettledEpsilon: Float = 0.005f

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
fun rememberFluidGlassQuality(ceiling: () -> Float = { 1f }): FluidGlassQuality {
  val scope = rememberCoroutineScope()
  return remember { FluidGlassQuality(ceiling).also { it.settleScope = scope } }
}

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
      quality.onScrollDelta(available.y, available.x)
      return Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
      // Un lancio *comincia* veloce: portare giù la qualità qui evita il fotogramma pieno che
      // altrimenti si paga proprio mentre l'inerzia parte.
      // F7.26 (R2): anche la componente orizzontale del lancio.
      quality.onScrollDelta(abs(available.y) / 60f, abs(available.x) / 60f)
      return Velocity.Zero
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
      quality.onSettled()
      return Velocity.Zero
    }
  }
