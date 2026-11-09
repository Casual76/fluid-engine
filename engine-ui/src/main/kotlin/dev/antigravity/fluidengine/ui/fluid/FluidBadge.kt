package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Le misure del [FluidBadge]. */
object FluidBadgeDefaults {
  /** Il diametro del pallino: 8 dp. Non cresce con il testo di sistema: non contiene niente da leggere. */
  val DotSize: Dp = 8.dp

  /** L'altezza minima della capsula col numero: 16 dp. Col testo grande la capsula cresce con il numero. */
  val MinHeight: Dp = 16.dp

  /** Il numero oltre il quale la capsula dice «99+». */
  const val Max: Int = 99
}

/**
 * Il testo di una capsula numerica: il numero, o «99+» se supera [max]; vuoto per zero e per i valori
 * negativi (un badge a zero non si disegna).
 *
 * Pura, perche' il troncamento e' la parte che sbagliano tutti («100+» invece di «99+», «0» in un
 * cerchietto) e si prova senza una composizione.
 */
fun fluidBadgeText(count: Int, max: Int = FluidBadgeDefaults.Max): String {
  val cap = max.coerceAtLeast(1)
  return when {
    count <= 0 -> ""
    count > cap -> "$cap+"
    else -> count.toString()
  }
}

/**
 * Il segno piccolo di «c'e' qualcosa»: un pallino, o una capsula con un numero («3», «99+»).
 *
 * **Perche' esiste.** Le non lette di una cartella, gli aggiornamenti di un negozio, le comunicazioni
 * di una scuola, il «c'e' qualcosa di nuovo» di una zona della barra: la stessa cosa disegnata in
 * cinque modi, con il numero a 99 che in tre di questi usciva dalla capsula e in uno non andava a
 * capo ma si tagliava. Qui il segno e' uno, con le cifre di larghezza fissa (nessun salto di
 * larghezza mentre il numero cambia) e il contrasto calcolato.
 *
 * **Tre stati, e zero non e' il pallino.**
 * - [count] null: il **pallino**, «c'e' qualcosa» senza dire quanto.
 * - [count] 0 (o negativo, che e' un dato sbagliato e non un caso): **niente**, non si disegna nulla
 *   e non occupa spazio. Zero non diventa un pallino per conto suo: un pallino acceso su «0 non lette»
 *   e' una bugia.
 * - [count] > 0: la capsula col numero, «99+» oltre [max].
 *
 * **Accessibilita': il numero si dice una volta.** E' muto di serie ([contentDescription] null): lo
 * porta una riga o una voce che gia' dice «Posta in arrivo, 3 non lette» in un nodo solo, e dirlo
 * anche dal badge sarebbe «3, Posta in arrivo, 3 non lette». Un badge che sta da solo (un'icona
 * con un numero) passa la frase intera, **con la quantita' vera**: «150 non lette» anche se il disegno
 * dice «99+». Un pallino senza descrizione non e' leggibile da nessuno, ed e' voluto: vale per i segni
 * che non devono disturbare (la zona Chat con novita').
 *
 * **Misure.** Il pallino e' [FluidBadgeDefaults.DotSize] e non cresce; la capsula e' alta almeno
 * [FluidBadgeDefaults.MinHeight] e **cresce con il testo di sistema** (a 200% «99+» resta dentro la sua
 * capsula, che si allarga): il numero e' informazione, il pallino no.
 *
 * **Contrasto.** Il colore passato e' un'identita' (l'accento, il colore di un account), non una
 * garanzia di leggibilita': il riempimento si porta, **del minimo che serve**, dove si stacca a 3:1 da
 * tutte le superfici (anche dalla pellicola del vetro, che in tema scuro e' quasi bianca) e la cifra
 * che ci sta sopra arriva a 4,5:1 ([fluidBadgeColors]). Il colore che si vede puo' quindi essere un
 * poco piu' scuro (o chiaro) di quello passato.
 *
 * Non fa animazioni: il segno compare e scompare con la riga che lo porta.
 *
 * @param count vedi sopra.
 * @param max oltre questo numero la capsula scrive «max+»; da 1 in su.
 * @param color il colore del segno; di serie l'accento del tema.
 * @param contentDescription la frase letta da TalkBack, o null per un badge muto.
 */
@Composable
fun FluidBadge(
  count: Int? = null,
  modifier: Modifier = Modifier,
  max: Int = FluidBadgeDefaults.Max,
  color: Color = Color.Unspecified,
  contentDescription: String? = null,
) {
  if (count != null && count <= 0) return
  val scheme = MaterialTheme.colorScheme
  val base = if (color != Color.Unspecified) color else scheme.primary
  val film = GlassDefaults.glassFilm()
  val colors = remember(base, scheme, film) { fluidBadgeColors(base, scheme, film) }
  // Un nodo solo in tutti e due i casi: con la frase, la frase e basta (la cifra «99+» che il disegno
  // mostra non si legge, o TalkBack direbbe «100 non lette, 99 piu»); muto, niente.
  val semanticsModifier = if (contentDescription != null) {
    Modifier.clearAndSetSemantics { this.contentDescription = contentDescription }
  } else {
    Modifier.clearAndSetSemantics { }
  }

  if (count == null) {
    Box(
      modifier = modifier
        .then(semanticsModifier)
        .size(FluidBadgeDefaults.DotSize)
        .clip(FluidCapsuleShape)
        .background(colors.fill),
    )
    return
  }

  Box(
    modifier = modifier
      .then(semanticsModifier)
      .defaultMinSize(minWidth = FluidBadgeDefaults.MinHeight, minHeight = FluidBadgeDefaults.MinHeight)
      .clip(FluidCapsuleShape)
      .background(colors.fill)
      .padding(horizontal = 5.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = fluidBadgeText(count, max),
      color = colors.ink,
      maxLines = 1,
      softWrap = false,
      style = MaterialTheme.typography.labelSmall.copy(
        fontWeight = FontWeight.SemiBold,
        // Cifre di larghezza fissa: «11» e «88» occupano lo stesso, e un contatore che sale non fa
        // ballare la riga.
        fontFeatureSettings = "tnum",
        textAlign = TextAlign.Center,
      ),
    )
  }
}

/**
 * Il riempimento di un [FluidBadge] e la cifra che ci sta sopra.
 *
 * Due passi, nell'ordine: il riempimento si porta a 3:1 da ogni superficie su cui puo' stare (la
 * pagina, una card, una card piu' alta e la pellicola del vetro: [legibleFill], la stessa regola delle
 * barre di avanzamento), poi la cifra si porta a 4,5:1 sul riempimento ([fluidReadableOn]). I due
 * passi spingono dalla stessa parte (via dalla superficie, verso il contrasto con l'inchiostro), quindi
 * il secondo non disfa il primo.
 *
 * Pura: e' aritmetica di colori da provare su tutta la palette, in chiaro e in scuro.
 */
internal fun fluidBadgeColors(color: Color, scheme: ColorScheme, glassFilm: Color? = null): FluidReadableColors {
  val surfaces = buildList {
    add(scheme.surface)
    add(scheme.surfaceContainerLow)
    add(scheme.surfaceContainerHigh)
    glassFilm?.let { add(it) }
  }
  return fluidReadableOn(legibleFill(color.copy(alpha = 1f), surfaces))
}
