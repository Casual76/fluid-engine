package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.antigravity.fluidengine.ui.theme.FluidToneTileSize

/**
 * Le misure e i toni degli scheletri.
 *
 * L'opacita' e' quella di `onSurface`, mai un grigio: lo stesso segnaposto sta bene sulla pagina
 * chiara, su AMOLED e dentro un pannello di vetro, perche' prende il colore del testo che sostituisce.
 */
object FluidSkeletonDefaults {
  /** Il punto basso e quello alto del respiro: un velo del 6% che sale al 13%. */
  const val LowAlpha: Float = 0.06f
  const val HighAlpha: Float = 0.13f

  /** Fermo (animazioni ridotte): fra i due, perche' non e' ne' il punto basso ne' l'alto di niente. */
  const val RestAlpha: Float = 0.09f

  /** L'angolo dei blocchi: lo stesso di un'etichetta, `FluidRadius.Small`. */
  val Shape: Shape = ContinuousCornerShape(FluidRadius.Small)

  /** L'angolo di una tessera: quello di una card. */
  val TileShape: Shape = ContinuousCornerShape(FluidRadius.Card)

  /** Un'altezza di riga di testo corrente e quella di un titolo, per costruire gli scheletri a mano. */
  val LineHeight: Dp = 13.dp
  val TitleHeight: Dp = 15.dp
  val CaptionHeight: Dp = 11.dp
}

/** Cosa c'e' davanti a una [FluidSkeletonRow]: niente, una piastrella come quella di `FluidListRow`, o un tondo. */
enum class FluidSkeletonLeading { None, Tile, Circle }

/**
 * L'opacita' dello scheletro dato il respiro (0..1) — o fermo, se il respiro non c'e'.
 *
 * Pura, perche' la scelta "fermo vale RestAlpha" e' quella che le animazioni ridotte esigono, e si
 * prova meglio qui che guardando uno schermo.
 */
internal fun fluidSkeletonAlpha(pulse: Float?): Float =
  if (pulse == null) {
    FluidSkeletonDefaults.RestAlpha
  } else {
    lerp(FluidSkeletonDefaults.LowAlpha, FluidSkeletonDefaults.HighAlpha, pulse.coerceIn(0f, 1f))
  }

/**
 * Le larghezze (frazioni) delle righe di un paragrafo finto: tutte piene tranne l'ultima, che e'
 * [lastFraction]. Un paragrafo a una riga sola e' "l'ultima" e basta.
 *
 * Righe tutte uguali sembrano una tabella, non un testo che sta per arrivare; l'ultima corta dice
 * "qui finisce una frase".
 */
internal fun fluidSkeletonLineFractions(lines: Int, lastFraction: Float = 0.64f): List<Float> {
  val count = lines.coerceAtLeast(0)
  return List(count) { index -> if (index == count - 1) lastFraction.coerceIn(0.1f, 1f) else 1f }
}

/**
 * La larghezza del titolo di una riga finta, per [variant]: alterna 72% e 56%. Una colonna di
 * rettangoli identici sembra una tabella e non un elenco di nomi.
 */
internal fun fluidSkeletonTitleFraction(variant: Int): Float = if (variant % 2 == 0) 0.72f else 0.56f

/**
 * L'altezza minima di una riga finta con [lines] righe di testo: quella della riga vera
 * (`ListItem`: 56 dp con una riga, 72 con due, 88 con tre), cosi' quando arrivano i dati non salta
 * niente.
 */
internal fun fluidSkeletonRowMinHeight(lines: Int): Dp = 56.dp + 16.dp * (lines.coerceIn(1, 3) - 1)

/**
 * Il respiro condiviso di un gruppo di scheletri.
 *
 * Un blocco da solo crea il suo; una riga, una tessera o un paragrafo — cioe' quattro o cinque blocchi
 * — ne creano **uno** e lo danno a tutti, e una lista di dieci righe ne ha dieci invece di
 * cinquanta. Battono comunque insieme fra gruppi diversi, perche' il respiro parte dall'orologio
 * ([fluidPulsePhaseOffsetMillis]).
 */
private val LocalSkeletonPulse = compositionLocalOf<State<Float>?> { null }

@Composable
private fun SkeletonGroup(content: @Composable () -> Unit) {
  // Se un gruppo c'e' gia', e' suo il respiro: non se ne crea un secondo dentro.
  if (LocalSkeletonPulse.current != null) {
    content()
    return
  }
  CompositionLocalProvider(LocalSkeletonPulse provides rememberFluidPulse(), content = content)
}

/** Il rettangolo che respira: l'opacita' si legge nel disegno, mai in composizione. */
@Composable
private fun SkeletonSurface(modifier: Modifier, shape: Shape) {
  val pulse = LocalSkeletonPulse.current ?: rememberFluidPulse()
  val ink = MaterialTheme.colorScheme.onSurface
  Box(
    modifier = modifier.drawWithCache {
      val outline = shape.createOutline(size, layoutDirection, this)
      onDrawBehind {
        drawOutline(outline = outline, color = ink, alpha = fluidSkeletonAlpha(pulse?.value))
      }
    },
  )
}

/**
 * Il segnaposto di un dato che sta arrivando: un blocco che respira.
 *
 * Dove un'app aspetta dei dati, una rotellina in mezzo al vuoto dice solo «sto facendo qualcosa»; le
 * sagome dicono anche *cosa* sta per comparire, e quando compare non salta niente perche' il suo
 * posto c'era gia'. Nata in Fluid-transit (che nel suo KDoc diceva che il suo posto era l'engine).
 *
 * **Respira, non scorre.** Un'opacita' che va e viene — [FluidPulseMillis], lo stesso respiro del
 * [FluidLiveDot] — e non una banda di luce che attraversa: e' la stessa quiete del resto del design
 * system, e sotto una schermata di venti segnaposto una banda e' venti strisce che corrono. Con le
 * animazioni ridotte e' un velo fermo ([FluidSkeletonDefaults.RestAlpha]).
 *
 * La larghezza la da' il [modifier] (`fillMaxWidth(0.6f)`, `width(44.dp)`); per le forme gia' fatte
 * ci sono [FluidSkeletonText], [FluidSkeletonRow], [FluidSkeletonTile] e [FluidSkeletonList].
 *
 * Muto per TalkBack: e' un velo, non un contenuto. Chi vuole dire «sto caricando» lo dice una volta
 * sola, con la `label` di [FluidSkeletonList] o con un testo suo.
 */
@Composable
fun FluidSkeleton(
  modifier: Modifier = Modifier,
  height: Dp = FluidSkeletonDefaults.LineHeight,
  shape: Shape = FluidSkeletonDefaults.Shape,
) {
  SkeletonSurface(modifier = modifier.height(height), shape = shape)
}

/**
 * Un paragrafo che sta arrivando: [lines] righe, l'ultima corta ([lastLineFraction]).
 */
@Composable
fun FluidSkeletonText(
  modifier: Modifier = Modifier,
  lines: Int = 3,
  lineHeight: Dp = FluidSkeletonDefaults.LineHeight,
  spacing: Dp = 9.dp,
  lastLineFraction: Float = 0.64f,
) {
  SkeletonGroup {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(spacing)) {
      fluidSkeletonLineFractions(lines, lastLineFraction).forEach { fraction ->
        FluidSkeleton(modifier = Modifier.fillMaxWidth(fraction), height = lineHeight)
      }
    }
  }
}

/**
 * Una riga di elenco che sta arrivando, della misura di `FluidListRow`: piastrella, titolo, sotto
 * titolo, e a destra un valore.
 *
 * @param lines le righe di testo: 1 solo titolo, 2 titolo e sottotitolo (la riga di sempre).
 * @param variant fa variare la larghezza del titolo da una riga alla successiva; in un elenco si passa
 *   l'indice ([FluidSkeletonList] lo fa da solo).
 */
@Composable
fun FluidSkeletonRow(
  modifier: Modifier = Modifier,
  leading: FluidSkeletonLeading = FluidSkeletonLeading.Tile,
  lines: Int = 2,
  trailing: Boolean = false,
  variant: Int = 0,
) {
  SkeletonGroup {
    Row(
      modifier = modifier
        .fillMaxWidth()
        .heightIn(min = fluidSkeletonRowMinHeight(lines))
        .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      when (leading) {
        FluidSkeletonLeading.None -> Unit
        // La stessa piastrella della riga vera: nel suo posto, alla sua misura.
        FluidSkeletonLeading.Tile -> SkeletonSurface(
          modifier = Modifier.size(FluidToneTileSize),
          shape = ContinuousCornerShape(FluidRadius.Small),
        )
        FluidSkeletonLeading.Circle -> SkeletonSurface(modifier = Modifier.size(40.dp), shape = FluidCapsuleShape)
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FluidSkeleton(
          modifier = Modifier.fillMaxWidth(fluidSkeletonTitleFraction(variant)),
          height = FluidSkeletonDefaults.TitleHeight,
        )
        if (lines >= 2) {
          FluidSkeleton(
            modifier = Modifier.fillMaxWidth(0.38f),
            height = FluidSkeletonDefaults.CaptionHeight,
          )
        }
        if (lines >= 3) {
          FluidSkeleton(
            modifier = Modifier.fillMaxWidth(0.52f),
            height = FluidSkeletonDefaults.CaptionHeight,
          )
        }
      }
      if (trailing) {
        FluidSkeleton(modifier = Modifier.width(46.dp), height = 16.dp)
      }
    }
  }
}

/**
 * Una tessera di griglia che sta arrivando: un riquadro ([aspectRatio] come la miniatura vera) e,
 * sotto, la didascalia.
 */
@Composable
fun FluidSkeletonTile(
  modifier: Modifier = Modifier,
  aspectRatio: Float = 1f,
  caption: Boolean = true,
  shape: Shape = FluidSkeletonDefaults.TileShape,
) {
  SkeletonGroup {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SkeletonSurface(
        modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio.coerceAtLeast(0.1f)),
        shape = shape,
      )
      if (caption) {
        FluidSkeleton(modifier = Modifier.fillMaxWidth(0.7f), height = FluidSkeletonDefaults.TitleHeight)
      }
    }
  }
}

/**
 * Un elenco che sta arrivando: [rows] righe che respirano insieme, con i titoli di larghezze diverse.
 *
 * @param label cosa dice TalkBack mentre l'elenco e' un segnaposto («Caricamento delle partenze»).
 *   Null: niente — le righe non hanno parole, e dirne una per riga sarebbe rumore.
 */
@Composable
fun FluidSkeletonList(
  modifier: Modifier = Modifier,
  rows: Int = 5,
  leading: FluidSkeletonLeading = FluidSkeletonLeading.Tile,
  lines: Int = 2,
  trailing: Boolean = false,
  label: String? = null,
) {
  SkeletonGroup {
    Column(
      modifier = modifier.then(
        if (label != null) Modifier.semantics { contentDescription = label } else Modifier,
      ),
    ) {
      repeat(rows.coerceAtLeast(0)) { index ->
        FluidSkeletonRow(leading = leading, lines = lines, trailing = trailing, variant = index)
      }
    }
  }
}
