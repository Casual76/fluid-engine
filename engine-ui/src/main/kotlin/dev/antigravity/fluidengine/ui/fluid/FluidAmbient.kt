package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * Il fondale di una schermata: una lavata di colore e un motivo, e nient'altro.
 *
 * Esiste per una ragione sola, e vale la pena scriverla: **il vetro sopra il grigio è invisibile per
 * costruzione.** Una superficie che rifrange una pagina piatta produce una pagina piatta, quindi in
 * un'app di superfici grigie il materiale non ha niente da fare e la sua assenza si legge come un
 * bug. Il canvas è quello che si guarda *attraverso*.
 *
 * Riusa l'anello di toni e i motivi che [FluidHero] ha già — stessa mappa fra sezione e colore, così
 * l'intestazione di una schermata e il suo fondale non possono divergere — ma li porta a schermo
 * intero e molto più diluiti. Il motivo è procedurale, quindi il fondale segue accento dinamico,
 * tema AMOLED e Material You senza un asset e senza altro lavoro.
 *
 * **Il canvas è opaco.** È l'invariante che tiene in piedi tutto il resto: le transizioni di rotta
 * esigono esattamente una pagina leggibile a ogni pixel di ogni fotogramma, e la traslucenza di
 * questo disegno è *interna* alla pagina, mai fra pagine.
 */
@Immutable
data class FluidAmbient(
  val tone: FluidHeroTone,
  val motif: FluidHeroMotif,
  /**
   * Quanto il fondale si allontana dal fondo piatto. 0 è esattamente il fondo di oggi, 1 la lavata
   * piena. Sotto 1 non spegne il motivo: lo diluisce insieme al colore, perché un motivo che resta
   * a piena forza su un fondo che è tornato neutro si legge come una decorazione dimenticata.
   */
  val intensity: Float = 1f,
  /** Promuove qualunque tono alla famiglia dell'errore, come fa [FluidHero]. */
  val urgent: Boolean = false,
)

/**
 * Il fondale, disegnato a schermo intero.
 *
 * Tre strati, dal fondo:
 *
 *  1. **Il fondo dell'app**, opaco. Se [FluidAmbient.intensity] è 0 il disegno finisce qui.
 *  2. **La lavata**: un alone del colore-contenitore della sezione nell'angolo in alto, che si
 *     esaurisce ben prima del fondo dello schermo. Concentrarla in alto non è estetica: è dove sta
 *     la chrome (che deve avere qualcosa da rifrangere) e dove *non* sta il grosso del testo lungo.
 *  3. **Il motivo**, grande, in alto a destra, sfumato verso il basso. È l'unica parte con dei
 *     bordi, ed è quella che rende la rifrazione visibile *come* rifrazione: piegare una rampa
 *     liscia produce un'altra rampa liscia, e non si distingue da un blur qualunque.
 */
/**
 * I tre colori del fondale, risolti su una palette.
 *
 * Funzione pura, e la ragione per cui è separata dal disegno è che l'unica cosa che vale la pena
 * verificare qui non si può verificare disegnando: che a `intensity` 0 il fondale sia *esattamente*
 * il fondo di oggi, e che sette toni diano sette colori diversi. Sono le due proprietà che si
 * rompono in silenzio quando qualcuno tocca l'anello.
 */
@Immutable
internal data class FluidAmbientColors(
  val base: Color,
  val wash: Color,
  val glow: Color,
  val motif: Color,
  val motifAlpha: Float,
)

internal fun fluidAmbientColors(
  scheme: ColorScheme,
  ambient: FluidAmbient,
): FluidAmbientColors {
  val amount = ambient.intensity.let { if (it.isFinite()) it.coerceIn(0f, 1f) else 1f }
  val background = scheme.background
  // Il contenitore del tono è già una superficie chiara in tema chiaro e scura in tema scuro, quindi
  // mescolarcelo dentro non può ribaltare la polarità del fondo: `onBackground` resta leggibile
  // sopra il risultato in entrambi i temi.
  val colors = fluidHeroColors(scheme, ambient.tone, ambient.urgent)
  return FluidAmbientColors(
    base = lerp(background, colors.container, AmbientBaseStrength * amount),
    wash = lerp(background, colors.accent, AmbientWashStrength * amount),
    glow = lerp(background, colors.accent, AmbientGlowStrength * amount),
    motif = colors.accent,
    motifAlpha = amount * AmbientMotifStrength,
  )
}

@Composable
fun FluidAmbientCanvas(
  ambient: FluidAmbient,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val background = scheme.background
  val colors = remember(scheme, ambient) { fluidAmbientColors(scheme, ambient) }
  val amount = colors.motifAlpha

  Box(modifier = modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .drawBehind {
          drawRect(background)
          if (amount <= 0.001f) return@drawBehind
          // La lavata: aloni decentrati, non un gradiente verticale. Un gradiente da bordo a bordo
          // dà a ogni riga della lista lo stesso colore e il fondale sparisce di nuovo; tre aloni
          // fuori asse danno alla pagina un *sopra* e un *sotto*, che è quello che il vetro poi
          // mostra muovendosi.
          drawRect(
            brush = Brush.radialGradient(
              colors = listOf(colors.base, colors.base.copy(alpha = 0f)),
              center = Offset(size.width * 0.5f, size.height * -0.10f),
              radius = size.height * 0.92f,
            ),
          )
          drawRect(
            brush = Brush.radialGradient(
              colors = listOf(colors.wash, colors.wash.copy(alpha = 0f)),
              center = Offset(size.width * 0.92f, size.height * 0.02f),
              radius = size.height * 0.52f,
            ),
          )
          drawRect(
            brush = Brush.radialGradient(
              colors = listOf(colors.glow, colors.glow.copy(alpha = 0f)),
              center = Offset(size.width * -0.05f, size.height * 0.34f),
              radius = size.height * 0.40f,
            ),
          )
        },
    )
    if (amount > 0.001f) {
      FluidHeroDecoration(
        motif = ambient.motif,
        color = colors.motif,
        modifier = Modifier
          .align(Alignment.TopEnd)
          // Il motivo del hero è 152 x 132 dentro una card; qui è la stessa figura in una stanza
          // molto più grande, quindi cresce di conseguenza invece di restare un francobollo. Deve
          // superare la metà della larghezza — sotto, torna a essere un ornamento d'angolo — e
          // fermarsi ben prima della metà dell'altezza, perché quello che scende più in basso
          // finisce dietro al corpo del testo.
          .size(width = AmbientMotifWidth, height = AmbientMotifHeight)
          // La sfumatura qui sotto è un `DstIn`, e un blend mode senza un proprio buffer cancella
          // quello che c'è già: senza questo layer la maschera si mangiava un rettangolo netto da
          // tutta la pagina disegnata prima.
          .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
          .drawWithCache {
            // La sfumatura sta fuori dal disegno del motivo e non dentro i suoi colori: il motivo è
            // fatto di forme con alpha propria, e moltiplicarle una per una lascerebbe comunque un
            // bordo netto dove il Canvas finisce. Un `DstIn` sul risultato già rasterizzato è
            // l'unico modo di far svanire una figura invece che di schiarirla.
            val fade = Brush.verticalGradient(
              colors = listOf(Color.White.copy(alpha = amount), Color.Transparent),
            )
            onDrawWithContent {
              drawContent()
              drawRect(brush = fade, blendMode = BlendMode.DstIn)
            }
          },
      )
    }
  }
}

/** Il contenitore del tono, largo e centrato in alto: il letto su cui stanno gli altri due. */
private const val AmbientBaseStrength = 0.75f

/**
 * L'accento vero, in alto a destra.
 *
 * Circa un sesto, e non di più: l'accento in tema chiaro è un colore saturo e scuro, e oltre questa
 * soglia il testo di contenuto comincia a perdere il contrasto che il vetro gli deve.
 */
private const val AmbientWashStrength = 0.17f

/** Lo stesso accento nell'angolo opposto, più debole: dà alla pagina una diagonale. */
private const val AmbientGlowStrength = 0.10f

/** Quanto resta del motivo in cima, prima che la sfumatura lo porti a zero. */
private const val AmbientMotifStrength = 0.85f

private val AmbientMotifWidth = 320.dp
private val AmbientMotifHeight = 300.dp
