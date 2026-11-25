package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Le misure e le parole del [FluidSelectionMark]. */
object FluidSelectionMarkDefaults {
  /** Il lato: 24 dp, quanto l'icona di una riga. */
  val Size: Dp = 24.dp

  /** Lo spessore dell'anello a [Size]: 1,75 dp, e scala con il lato. */
  val RingWidth: Dp = 1.75.dp

  /**
   * Quanto il velo scuro sotto l'anello di un segno [FluidSelectionMark] sopra una foto: abbastanza da
   * leggere l'anello bianco su un cielo, poco da non far sembrare la foto sporca.
   */
  const val PhotoScrimAlpha: Float = 0.28f

  /** Cosa legge TalkBack, per stato. Un'app in un'altra lingua passa la sua. */
  fun description(selected: Boolean): String = if (selected) "Selezionato" else "Non selezionato"

  /**
   * Cosa legge TalkBack per un segno col numero d'ordine (F8.14): «Selezionato, numero 2». Senza numero, come
   * [description].
   */
  fun description(selected: Boolean, number: Int?): String =
    if (selected && number != null) "Selezionato, numero $number" else description(selected)
}

/**
 * Il colore della spunta sopra un disco pieno di [fill]: scuro se il disco e' chiaro, bianco se e'
 * scuro. Pura, perche' e' il caso che un colore passato dall'app (la tinta di una materia, di una
 * cartella) trasforma in una spunta invisibile se nessuno la guarda.
 */
internal fun fluidSelectionCheckColor(fill: Color): Color =
  if (fill.luminance() > 0.5f) Color.Black.copy(alpha = 0.87f) else Color.White

/**
 * La spunta come tre punti in un quadrato unitario (x, y, x, y, x, y): disegnata a mano e non con
 * un'icona, perche' il segno vive in una griglia di cento celle e un nodo `Icon` per cella, anche
 * spento, sono cento nodi per niente.
 *
 * Stanno tutti dentro il disco con lo spessore del tratto: lo prova un test.
 */
internal val FluidSelectionCheckPoints: FloatArray = floatArrayOf(
  0.30f, 0.52f,
  0.435f, 0.655f,
  0.70f, 0.365f,
)

/** Lo spessore del tratto della spunta, in frazione del lato. */
internal const val FluidSelectionCheckStroke: Float = 0.085f

/**
 * Il segno della selezione multipla: un cerchio vuoto quando la riga si puo' scegliere, un disco
 * pieno con la spunta quando e' scelta. Nello spazio `leading` di una `FluidListRow`, o sull'angolo
 * di una miniatura in una griglia.
 *
 * Due app se l'erano scritto (Pampa notes con due icone di Material che si scambiano, MyCloud con un
 * disco e una spunta animati), e quella delle due icone **saltava**: il passaggio da un cerchio vuoto
 * a uno pieno era un cambio di vettore, senza un fotogramma in mezzo. Qui il disco si riempie e la
 * spunta entra con la molla viva della casa ([FluidMotion.fluid], quella dei segni piccoli: un
 * tre per cento di rimbalzo e' energia, a questa taglia), e con le animazioni ridotte il segno cambia
 * stato senza passaggi.
 *
 * **Un solo nodo.** Disco, anello e spunta sono un disegno solo, letto nel disegno e non in
 * composizione: una griglia di cento celle in selezione non ricompone niente mentre i segni si
 * riempiono.
 *
 * @param onPhoto sopra una foto a tutta cella l'anello e' bianco su un velo scuro
 *   ([FluidSelectionMarkDefaults.PhotoScrimAlpha]), qualunque sia la foto; il disco pieno resta
 *   dell'accento. Sopra una superficie normale l'anello e' quello del testo secondario.
 * @param tint il colore del disco pieno e dell'anello, quando sta sopra una superficie piena che
 *   l'accento non leggerebbe (la tessera colorata di una materia). La spunta prende da sola il colore
 *   che si legge sul disco ([fluidSelectionCheckColor]). Di serie: l'accento e la sua coppia
 *   `onPrimary`.
 * @param contentDescription cosa legge TalkBack per questo segno; di serie «Selezionato» o «Non
 *   selezionato» ([FluidSelectionMarkDefaults.description]). Null lo rende muto — quando e' la riga
 *   a dire lo stato (`Modifier.semantics { selected = ... }`) e dirlo due volte e' rumore.
 * @param number il numero d'ordine della scelta (F8.14, la selezione ordinata del selettore di foto, come
 *   `PHPickerConfiguration.Selection.ordered` di iOS): a segno pieno il disco porta il numero al posto della spunta. Quando
 *   il numero cambia senza che il segno si spenga (si toglie la scelta numero 2 e la 3 diventa 2: lo fa l'app, non il
 *   dito su questo segno) le cifre scorrono sfocate con [FluidNumericText], come il numero di un badge (R7). Col numero un
 *   segno spento non disegna niente (ne' l'anello ne' il velo): come la scelta ordinata del selettore di iOS, dove le foto
 *   non scelte non hanno l'anello. Null, di serie: la spunta.
 */
@Composable
fun FluidSelectionMark(
  selected: Boolean,
  modifier: Modifier = Modifier,
  size: Dp = FluidSelectionMarkDefaults.Size,
  onPhoto: Boolean = false,
  tint: Color = Color.Unspecified,
  contentDescription: String? = FluidSelectionMarkDefaults.description(selected),
  number: Int? = null,
) {
  val scheme = MaterialTheme.colorScheme
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val fill = animateFloatAsState(
    targetValue = if (selected) 1f else 0f,
    animationSpec = if (reducedMotion) snap() else FluidMotion.fluid(),
    label = "fluid selection mark",
  )
  val hasTint = tint != Color.Unspecified
  val fillColor = if (hasTint) tint else scheme.primary
  val checkColor = if (hasTint) fluidSelectionCheckColor(tint) else scheme.onPrimary
  val idleRing = when {
    onPhoto -> Color.White
    hasTint -> tint
    else -> scheme.onSurfaceVariant.copy(alpha = 0.75f)
  }

  val described = modifier
    .size(size)
    .then(
      if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
      } else {
        Modifier
      },
    )
  if (number != null) {
    // F8.14: il numero d'ordine al posto della spunta. Il disco e l'anello restano un disegno solo; il numero e' un
    // testo a parte solo sui segni che lo portano (pochi: le scelte, non le cento celle).
    Box(described, contentAlignment = Alignment.Center) {
      FluidSelectionDisk(fill = { fill.value }, onPhoto = onPhoto, fillColor = fillColor, idleRing = idleRing,
        check = null, modifier = Modifier.matchParentSize(), ringAtRest = false)
      // Il testo c'e' solo mentre il disco ha qualcosa da mostrare: un segno vuoto a riposo resta il solo disegno.
      val lit by remember { derivedStateOf { fill.value > 0.001f } }
      if (selected || lit) FluidNumericText(
        value = number,
        color = checkColor,
        style = MaterialTheme.typography.labelMedium.copy(
          fontWeight = FontWeight.SemiBold,
          fontFeatureSettings = "tnum",
          textAlign = TextAlign.Center,
        ),
        modifier = Modifier.graphicsLayer {
          val p = fill.value
          alpha = p.coerceIn(0f, 1f)
          scaleX = p
          scaleY = p
        },
      )
    }
    return
  }
  FluidSelectionDisk(fill = { fill.value }, onPhoto = onPhoto, fillColor = fillColor, idleRing = idleRing,
    check = checkColor, modifier = described)
}

/**
 * Il disco, l'anello e (con [check]) la spunta del [FluidSelectionMark]: un disegno solo, con [fill] letto nel disegno.
 */
@Composable
private fun FluidSelectionDisk(
  fill: () -> Float,
  onPhoto: Boolean,
  fillColor: Color,
  idleRing: Color,
  check: Color?,
  modifier: Modifier,
  ringAtRest: Boolean = true,
) {
  Canvas(modifier = modifier) {
    val progress = fill()
    val shown = progress.coerceIn(0f, 1f)
    val radius = this.size.minDimension / 2f
    // Lo spessore dell'anello scala col lato: a 24 dp e' 1,75, a 20 non deve restare 1,75.
    val ring = FluidSelectionMarkDefaults.RingWidth.toPx() * (this.size.minDimension / FluidSelectionMarkDefaults.Size.toPx())

    // Col numero (ringAtRest falso) il segno spento non c'e': anello e velo arrivano col disco.
    if (!ringAtRest && shown <= 0f) return@Canvas
    if (onPhoto && shown < 1f && ringAtRest) {
      drawCircle(
        color = Color.Black.copy(alpha = FluidSelectionMarkDefaults.PhotoScrimAlpha * (1f - shown)),
        radius = radius,
      )
    }
    if (shown > 0f) {
      drawCircle(color = fillColor, radius = radius, alpha = shown)
    }
    // Sulla foto l'anello bianco resta anche a segno pieno (e' lui a staccare il disco dalla foto);
    // altrimenti sfuma verso il colore del disco e sparisce dentro.
    val ringColor = if (onPhoto) idleRing else lerp(idleRing, fillColor, shown)
    drawCircle(
      color = ringColor,
      radius = radius - ring / 2f,
      style = Stroke(width = ring),
      alpha = if (ringAtRest) 1f else shown,
    )
    if (shown > 0f && check != null) {
      val w = this.size.width
      val h = this.size.height
      val p = FluidSelectionCheckPoints
      val path = Path().apply {
        moveTo(p[0] * w, p[1] * h)
        lineTo(p[2] * w, p[3] * h)
        lineTo(p[4] * w, p[5] * h)
      }
      // La spunta cresce dal centro con la molla (supera di pochi centesimi e si posa) e si accende
      // insieme: scala e opacita' sono lo stesso numero.
      scale(scaleX = progress, scaleY = progress, pivot = Offset(w / 2f, h / 2f)) {
        drawPath(
          path = path,
          color = check,
          alpha = shown,
          style = Stroke(
            width = FluidSelectionCheckStroke * minOf(w, h),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
          ),
        )
      }
    }
  }
}
