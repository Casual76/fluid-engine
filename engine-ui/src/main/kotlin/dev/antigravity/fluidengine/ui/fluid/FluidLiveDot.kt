package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

// --- Il respiro --------------------------------------------------------------------------------
//
// Il pallino "dal vivo" delle app pulsava a 700 ms in una e a 900 in un'altra, scritto a mano ogni
// volta, e lo scheletro di caricamento e il passo corrente di un percorso respiravano a 900 per conto
// loro. Un respiro solo, perche' due cose vive sulla stessa schermata che non battono insieme sembrano
// rotte.

// I numeri del respiro sono token (fase 10): `FluidMotion.PulseMillis` (900: meta' di un respiro, la
// durata che quattro app su cinque avevano gia' scelto da sole e quella dello spinner), `PulseLowAlpha`
// (0,35: il punto basso di un segno pieno, abbastanza basso da vedersi che si spegne, abbastanza alto da
// non sparire su un fondo chiaro) e `PulseFillLowAlpha` (0,55, per un riempimento).

/**
 * Da che punto del respiro parte chi entra in scena adesso, in millisecondi.
 *
 * Il respiro e' una funzione dell'orologio, non del momento in cui il pallino e' stato composto: una
 * lista con dieci partenze dal vivo che entrano a fotogrammi diversi, ognuna col suo conto da zero,
 * ha dieci pallini che pulsano ognuno per conto suo, e a occhio e' un tremolio. Partendo tutti dal
 * punto in cui l'orologio li trova battono insieme, qualunque sia il momento in cui sono arrivati.
 *
 * [cycleMillis] e' il periodo intero (andata e ritorno): `2 x` [FluidMotion.PulseMillis] con `Reverse`.
 */
internal fun fluidPulsePhaseOffsetMillis(nowMillis: Long, cycleMillis: Int): Int {
  if (cycleMillis <= 0) return 0
  return Math.floorMod(nowMillis, cycleMillis.toLong()).toInt()
}

/**
 * Se un respiro deve muoversi: lo vuole chi lo usa ([active]) **e** la politica di movimento lo
 * permette. Con la scala di animazione di sistema a zero e' un'impostazione di accessibilita', non un
 * suggerimento, e il segno resta fermo. Pura, per provare la regola senza una composizione.
 */
internal fun fluidPulseEnabled(active: Boolean, policy: FluidMotionPolicy): Boolean =
  active && policy.allowDecorativeMotion

/**
 * Il respiro come numero da 0 a 1 (0 il punto basso, 1 l'alto), o `null` quando non deve muoversi.
 *
 * Null con le animazioni ridotte ([FluidMotionPolicy.allowDecorativeMotion]) o quando [active] e'
 * falso: chi lo legge disegna allora il segno fermo, e **non crea nemmeno la transizione infinita** —
 * una animazione che gira per niente tiene sveglio il thread di disegno per tutta la vita della
 * schermata. Va letto nel disegno (`drawBehind`, `graphicsLayer`), mai in composizione: il pallino
 * non deve ricomporsi a ogni fotogramma.
 */
@Composable
internal fun rememberFluidPulse(active: Boolean = true, periodMillis: Int = FluidMotion.PulseMillis): State<Float>? {
  if (!fluidPulseEnabled(active, LocalFluidMotionPolicy.current)) return null
  val transition = rememberInfiniteTransition(label = "fluid pulse")
  val offset = remember(periodMillis) {
    fluidPulsePhaseOffsetMillis(System.currentTimeMillis(), periodMillis * 2)
  }
  return transition.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = FluidMotion.pulse(periodMillis, startOffsetMillis = offset),
    label = "fluid pulse value",
  )
}

/** Le misure del [FluidLiveDot]. */
object FluidLiveDotDefaults {
  /** Il diametro: 7 dp, accanto a un testo da 13-17 sp il pallino si legge senza farsi guardare. */
  val Size: Dp = 7.dp
}

/**
 * Il pallino che pulsa accanto a un dato vivo: i minuti di un bus seguito dal feed, «in diretta», un
 * lettore che sta registrando.
 *
 * Tre app se lo erano scritto a mano, a 700 e a 900 ms. Qui il respiro e' uno ([FluidMotion.PulseMillis]) ed
 * e' quello dell'orologio: tutti i pallini in scena battono insieme ([fluidPulsePhaseOffsetMillis]).
 * Pulsa solo l'opacita' — scala e alone farebbero muovere il testo accanto — fra [FluidMotion.PulseLowAlpha]
 * e pieno.
 *
 * **Fermo** con le animazioni di sistema ridotte e con `pulsing = false` (un dato che e' vivo ma ora
 * non arriva: il pallino resta, il respiro no). Fermo e' pieno, mai a meta': un segno a mezza opacita'
 * che non si muove si legge come spento.
 *
 * Decorativo per TalkBack, salvo che [contentDescription] dica il perche' del pallino: un segno che
 * dice «dal vivo» a chi vede va detto anche a chi no, ma una volta sola, e di solito lo dice gia' il
 * testo accanto.
 *
 * @param color di serie l'accento; un'app passa il tono del dato (il ritardo, l'orario di tabella).
 */
@Composable
fun FluidLiveDot(
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary,
  size: Dp = FluidLiveDotDefaults.Size,
  pulsing: Boolean = true,
  contentDescription: String? = null,
) {
  val pulse = rememberFluidPulse(active = pulsing)
  Box(
    modifier = modifier
      .size(size)
      .then(
        if (contentDescription != null) {
          Modifier.semantics { this.contentDescription = contentDescription }
        } else {
          Modifier
        },
      )
      .drawBehind {
        val alpha = lerp(FluidMotion.PulseLowAlpha, 1f, pulse?.value ?: 1f)
        drawCircle(color = color, alpha = alpha)
      },
  )
}
