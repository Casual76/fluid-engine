package dev.antigravity.fluidengine.ui.fluid

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.antigravity.fluidengine.ui.fluid.icons.FluidIcons
import dev.antigravity.fluidengine.ui.fluid.icons.Person
import dev.antigravity.fluidengine.ui.haptics.FluidHapticEvent
import java.text.BreakIterator
import java.util.Locale

/** Le misure dell'[FluidAvatar] e del suo mosaico. */
object FluidAvatarDefaults {
  /** Il diametro di serie: 40 dp, quello di un avatar in una riga a due righe di testo. */
  val Size: Dp = 40.dp

  /**
   * Il bersaglio minimo di un avatar **toccabile**: 48 dp. Un avatar di 40 dp che si tocca occupa 48 dp
   * nel layout (e li' si prende il tocco), centrato: l'alternativa, un'area invisibile piu' grande del
   * disegno, sovrapporrebbe i bersagli della riga e dell'avatar.
   */
  val TouchTarget: Dp = 48.dp

  /** Quante celle al massimo ha un mosaico ([FluidAvatarStack]): una griglia due per due. */
  const val StackMax: Int = 4

  /** Quante lettere porta di serie un monogramma ([fluidMonogram]). */
  const val MonogramLetters: Int = 2
}

/**
 * Una persona (o un gruppo, o un servizio) per il mosaico di [FluidAvatarStack].
 *
 * @param name il nome da cui vengono le iniziali.
 * @param key da cui viene il colore ([fluidIdentityColor]); di serie il nome. Per una persona e'
 *   meglio una chiave che non cambia quando si corregge il nome (l'indirizzo, l'identificatore).
 * @param color un colore deciso dall'app (l'account, la materia); [Color.Unspecified] usa quello della chiave.
 * @param image il disegno al posto del monogramma (un logo, una foto): lo disegna l'app, l'engine non
 *   carica niente dalla rete. Vedi [FluidAvatar].
 */
@Immutable
data class FluidAvatarItem(
  val name: String,
  val key: String = name,
  val color: Color = Color.Unspecified,
  val image: (@Composable () -> Unit)? = null,
)

/**
 * Il cerchio di una persona: un monogramma su un colore che non cambia mai, oppure un'immagine che
 * porta l'app, e al posto del volto — in una lista che sta selezionando — il segno di selezione.
 *
 * **Perche' esiste.** Ogni app con delle persone dentro (rubrica, posta, comunicazioni della scuola,
 * condivisioni) se ne era scritto uno: un cerchio colorato con la prima lettera, il colore preso da un
 * elenco incollato nel composable, l'immagine caricata dentro il componente (e allora l'engine
 * faceva rete, che non deve). Qui il cerchio e' una cosa sola e le tre cose che sbagliavano tutti sono
 * provate da un test: le iniziali (emoji, accenti, nomi di una lettera, scritture non latine, RTL), il
 * colore (stabile, dalla [FluidIdentityColors]) e il contrasto del monogramma (calcolato, non sperato).
 *
 * **L'engine non carica niente.** [image] e' un disegno dell'app: una foto gia' decodificata, un logo
 * di un catalogo locale, un `AsyncImage` suo. Sta **sopra** il monogramma, e il monogramma sta sempre
 * sotto: finche' l'immagine non e' arrivata, o se non arriva mai, o se ha trasparenza, si vede il
 * monogramma — non un cerchio vuoto. Un'app che non vuole il monogramma sotto un logo trasparente mette
 * un fondo suo.
 *
 * **Selezione.** Con [selected] diverso da null il volto lascia il posto al segno ([FluidSelectionMark]):
 * un cerchio vuoto se la riga si puo' scegliere, un disco con la spunta se e' scelta. E' quello che si
 * vede in tutte le righe quando una lista entra in selezione, e perche' il segno sta nel posto
 * dell'avatar (non accanto) una riga non cambia larghezza entrando e uscendo. Il cambio e' una
 * dissolvenza incrociata, secca con le animazioni ridotte.
 *
 * **Misure.** Il cerchio e' [size] dp, e col testo di sistema al 200% **non cresce**: il monogramma
 * e' un disegno, e un avatar piu' grande del doppio mangia la riga; la persona e' nominata dal testo
 * accanto, che cresce. Se e' toccabile ([onClick]) occupa almeno 48 dp ([FluidAvatarDefaults.TouchTarget]).
 *
 * **Accessibilita'.** Un avatar e' un nodo solo e muto per i suoi pezzi: il monogramma, il segno
 * e l'angolo non si leggono mai a pezzi.
 * - [contentDescription] null: decorativo. E' il caso di un avatar dentro una riga, che dice gia' il
 *   nome (la riga e' un nodo solo): senza questo TalkBack direbbe «AR» prima di «Anna Rossi».
 * - toccabile **senza** descrizione: legge [name] (un bottone senza nome e' un errore, non uno stile).
 * - con [selected]: e' una casella (`Role.Checkbox`) con lo stato detto nella lingua dell'app
 *   ([FluidLabels.selected] e [FluidLabels.notSelected]).
 * - il contenuto di [corner] non si legge: se il segno d'angolo significa qualcosa (un'identita',
 *   un conteggio) va nel [contentDescription].
 *
 * @param name il nome da cui vengono le iniziali ([fluidMonogram]). Vuoto o solo simboli: il glifo di
 *   una persona.
 * @param key da cui viene il colore; di serie il nome. Normalizzata da chi chiama ([FluidIdentityColors]).
 * @param color un colore dell'app (la tinta di un account); [Color.Unspecified] usa quello della chiave.
 *   Se nessun inchiostro lo regge a 4,5:1 il fondo si porta del minimo che basta ([fluidReadableOn]).
 * @param image vedi sopra.
 * @param selected null: niente selezione. true/false: il segno al posto del volto.
 * @param corner un segno piccolo sull'angolo in basso a destra (a sinistra in RTL): il colore di un
 *   account, un [FluidBadge]. Sporge dal cerchio di un ottavo del lato.
 * @param onClick rende l'avatar un bottone (entra in selezione, apre un profilo). Null: non e' toccabile.
 */
@Composable
fun FluidAvatar(
  name: String,
  modifier: Modifier = Modifier,
  key: String = name,
  size: Dp = FluidAvatarDefaults.Size,
  color: Color = Color.Unspecified,
  image: (@Composable () -> Unit)? = null,
  selected: Boolean? = null,
  corner: (@Composable () -> Unit)? = null,
  onClick: (() -> Unit)? = null,
  contentDescription: String? = null,
) {
  val labels = LocalFluidLabels.current
  val base = if (color != Color.Unspecified) color else fluidIdentityColor(key)
  val readable = remember(base) { fluidReadableOn(base) }

  val label = contentDescription ?: if (onClick != null) name.takeIf { it.isNotBlank() } else null
  val toggle = selected?.let { ToggleableState(it) }
  val touch = if (onClick != null) {
    Modifier
      .defaultMinSize(FluidAvatarDefaults.TouchTarget, FluidAvatarDefaults.TouchTarget)
      .fluidPressable(
        onClick = onClick,
        role = if (selected != null) Role.Checkbox else Role.Button,
        // Il suono dice cosa succedera': toccando un avatar non scelto si sceglie.
        haptic = when (selected) {
          null -> FluidHapticEvent.Tap
          false -> FluidHapticEvent.ToggleOn
          true -> FluidHapticEvent.ToggleOff
        },
        focusShape = FluidCapsuleShape,
      )
  } else {
    Modifier
  }
  val outer = modifier
    .then(touch)
    .then(
      if (label != null || toggle != null) {
        Modifier.semantics(mergeDescendants = true) {
          if (label != null) this.contentDescription = label
          if (toggle != null) {
            this.toggleableState = toggle
            this.role = Role.Checkbox
            this.stateDescription = if (selected == true) labels.selected else labels.notSelected
          }
        }
      } else {
        Modifier
      },
    )

  Box(modifier = outer, contentAlignment = Alignment.Center) {
    Box(modifier = Modifier.size(size).clearAndSetSemantics { }) {
      FluidAvatarFace(
        name = name,
        size = size,
        readable = readable,
        image = image,
        selected = selected,
      )
      if (corner != null) {
        Box(
          modifier = Modifier
            .align(Alignment.BottomEnd)
            .offset(x = size / 8, y = size / 8),
        ) { corner() }
      }
    }
  }
}

/** Il disco, il monogramma, l'immagine e il segno di selezione: tutto cio' che sta dentro il cerchio. */
@Composable
private fun FluidAvatarFace(
  name: String,
  size: Dp,
  readable: FluidReadableColors,
  image: (@Composable () -> Unit)?,
  selected: Boolean?,
) {
  val reducedMotion = LocalFluidMotionPolicy.current.reducedMotion
  val markAlpha by animateFloatAsState(
    targetValue = if (selected != null) 1f else 0f,
    animationSpec = if (reducedMotion) snap() else FluidMotion.crossFade(),
    label = "fluid avatar selection",
  )
  // Mentre il segno si dissolve (la selezione e' finita) selected e' gia' null: serve l'ultimo valore
  // vero, o il segno salterebbe a «non scelto» a meta' dissolvenza.
  val lastSelected = remember { BooleanHolder() }
  if (selected != null) lastSelected.value = selected

  Box(
    modifier = Modifier.fillMaxSize().clip(FluidCapsuleShape),
    contentAlignment = Alignment.Center,
  ) {
    // Il disco colorato, il monogramma e l'immagine sfumano insieme: in selezione resta solo il segno,
    // sulla superficie della riga (un disco colorato dietro l'anello lo farebbe sembrare un'altra cosa).
    Box(
      modifier = Modifier.fillMaxSize().alpha(1f - markAlpha).background(readable.fill),
      contentAlignment = Alignment.Center,
    ) {
      val monogram = remember(name) { fluidMonogram(name) }
      if (monogram.isEmpty()) {
        FluidIcon(
          imageVector = FluidIcons.Person,
          contentDescription = null,
          tint = readable.ink,
          modifier = Modifier.size(size * FluidAvatarGlyphFraction),
        )
      } else {
        FluidMonogramText(monogram, size, readable.ink, monogramFraction(monogram))
      }
      image?.invoke()
    }
    if (markAlpha > 0f) {
      Box(modifier = Modifier.fillMaxSize().alpha(markAlpha), contentAlignment = Alignment.Center) {
        FluidSelectionMark(
          selected = selected ?: lastSelected.value,
          size = size,
          contentDescription = null,
        )
      }
    }
  }
}

private class BooleanHolder {
  var value: Boolean = false
}

/** Il glifo della persona, quando non c'e' niente da scrivere: poco piu' della meta' del cerchio. */
private const val FluidAvatarGlyphFraction = 0.58f

/** Il corpo del monogramma in frazione del lato: due lettere stanno piu' strette di una. */
private fun monogramFraction(monogram: String): Float =
  if (graphemes(monogram).size <= 1) 0.42f else 0.36f

/**
 * Il monogramma come testo in un corpo **fisso in dp**, non in sp: l'avatar non cresce col testo di
 * sistema, e un monogramma che cresce da solo uscirebbe dal cerchio.
 */
@Composable
private fun FluidMonogramText(text: String, size: Dp, ink: Color, fraction: Float) {
  val fontSize = with(LocalDensity.current) { (size * fraction).toSp() }
  Text(
    text = text,
    color = ink,
    maxLines = 1,
    softWrap = false,
    style = MaterialTheme.typography.labelLarge.copy(
      fontSize = fontSize,
      lineHeight = fontSize,
      fontWeight = FontWeight.SemiBold,
      textAlign = TextAlign.Center,
      // La direzione la decide la scrittura delle iniziali, non quella dell'app: «مع» in un'app
      // italiana non si legge da sinistra.
      textDirection = TextDirection.Content,
    ),
  )
}

/**
 * Un mosaico di piu' persone in un cerchio solo: la conversazione con piu' interlocutori, il gruppo,
 * le caselle di un'app con piu' account.
 *
 * Due celle sono due meta', tre sono una meta' e due quarti, quattro sono quattro quarti; con piu'
 * persone di [max] l'ultima cella dice quante mancano («+4»). Ogni cella porta la **prima lettera**
 * ([fluidMonogram] con una lettera) sul colore della persona, o la sua immagine; un'immagine nel
 * mosaico non ha il monogramma sotto, perche' una cella e' troppo piccola per due strati leggibili.
 *
 * Tetto e nome: oltre [max] il mosaico non cresce, e il resto **lo dice il nome**: [contentDescription]
 * e' il posto, e [fluidAvatarStackDescription] lo compone con le parole dell'app. Nullo, il mosaico
 * e' decorativo, come un [FluidAvatar].
 *
 * Con una persona sola e' un [FluidAvatar]; con nessuna, il glifo della persona.
 *
 * @param max da 2 a [FluidAvatarDefaults.StackMax] celle; fuori da questi estremi si porta al piu' vicino.
 */
@Composable
fun FluidAvatarStack(
  items: List<FluidAvatarItem>,
  modifier: Modifier = Modifier,
  size: Dp = FluidAvatarDefaults.Size,
  max: Int = FluidAvatarDefaults.StackMax,
  contentDescription: String? = null,
  onClick: (() -> Unit)? = null,
) {
  val cap = max.coerceIn(2, FluidAvatarDefaults.StackMax)
  val shown = fluidAvatarStackCells(items.size, cap)
  if (items.size <= 1) {
    val only = items.firstOrNull()
    FluidAvatar(
      name = only?.name.orEmpty(),
      modifier = modifier,
      key = only?.key ?: "",
      size = size,
      color = only?.color ?: Color.Unspecified,
      image = only?.image,
      onClick = onClick,
      contentDescription = contentDescription,
    )
    return
  }

  val label = contentDescription ?: if (onClick != null) items.first().name else null
  val touch = if (onClick != null) {
    Modifier
      .defaultMinSize(FluidAvatarDefaults.TouchTarget, FluidAvatarDefaults.TouchTarget)
      .fluidPressable(onClick = onClick, role = Role.Button, focusShape = FluidCapsuleShape)
  } else {
    Modifier
  }
  val outer = modifier
    .then(touch)
    .then(
      if (label != null) {
        Modifier.semantics(mergeDescendants = true) { this.contentDescription = label }
      } else {
        Modifier
      },
    )
  val gap = FluidAvatarStackGap
  val gapColor = MaterialTheme.colorScheme.surface

  Box(modifier = outer, contentAlignment = Alignment.Center) {
    Box(
      modifier = Modifier
        .size(size)
        .clearAndSetSemantics { }
        .clip(FluidCapsuleShape)
        .background(gapColor),
    ) {
      val hidden = items.size - shown.visible
      @Composable
      fun Cell(index: Int, cellModifier: Modifier) {
        if (index == shown.overflowCell && hidden > 0) {
          FluidAvatarOverflowCell("+$hidden", size, cellModifier)
        } else {
          FluidAvatarCell(items[index], size, cellModifier)
        }
      }
      when (shown.cells) {
        2 -> Row(Modifier.fillMaxSize(), Arrangement.spacedBy(gap)) {
          Cell(0, Modifier.weight(1f).fillMaxSize())
          Cell(1, Modifier.weight(1f).fillMaxSize())
        }
        3 -> Row(Modifier.fillMaxSize(), Arrangement.spacedBy(gap)) {
          Cell(0, Modifier.weight(1f).fillMaxSize())
          Column(Modifier.weight(1f).fillMaxSize(), Arrangement.spacedBy(gap)) {
            Cell(1, Modifier.weight(1f).fillMaxSize())
            Cell(2, Modifier.weight(1f).fillMaxSize())
          }
        }
        else -> Column(Modifier.fillMaxSize(), Arrangement.spacedBy(gap)) {
          Row(Modifier.weight(1f).fillMaxSize(), Arrangement.spacedBy(gap)) {
            Cell(0, Modifier.weight(1f).fillMaxSize())
            Cell(1, Modifier.weight(1f).fillMaxSize())
          }
          Row(Modifier.weight(1f).fillMaxSize(), Arrangement.spacedBy(gap)) {
            Cell(2, Modifier.weight(1f).fillMaxSize())
            Cell(3, Modifier.weight(1f).fillMaxSize())
          }
        }
      }
    }
  }
}

/** Il filo fra due celle del mosaico: abbastanza per staccarle, poco da non mangiare una cella da 20 dp. */
private val FluidAvatarStackGap = 1.5.dp

/** Quante celle ha un mosaico di [total] persone con tetto [cap], e quale cella dice il resto. */
internal data class FluidAvatarStackLayout(
  /** Le celle disegnate: da 2 a 4. */
  val cells: Int,
  /** Le persone che si vedono davvero (una in meno delle celle, se l'ultima cella dice il resto). */
  val visible: Int,
  /** L'indice della cella che dice «+N», o -1. */
  val overflowCell: Int,
)

internal fun fluidAvatarStackCells(total: Int, cap: Int): FluidAvatarStackLayout {
  val limit = cap.coerceIn(2, FluidAvatarDefaults.StackMax)
  val cells = total.coerceIn(2, limit)
  return if (total > cells) {
    // Piu' persone che celle: l'ultima cella dice quante sono le altre, e non e' una persona.
    FluidAvatarStackLayout(cells = cells, visible = cells - 1, overflowCell = cells - 1)
  } else {
    FluidAvatarStackLayout(cells = cells, visible = total, overflowCell = -1)
  }
}

@Composable
private fun FluidAvatarCell(item: FluidAvatarItem, size: Dp, modifier: Modifier) {
  val base = if (item.color != Color.Unspecified) item.color else fluidIdentityColor(item.key)
  val readable = remember(base) { fluidReadableOn(base) }
  val letter = remember(item.name) { fluidMonogram(item.name, maxLetters = 1) }
  Box(
    modifier = modifier.background(readable.fill),
    contentAlignment = Alignment.Center,
  ) {
    if (letter.isEmpty()) {
      FluidIcon(
        imageVector = FluidIcons.Person,
        contentDescription = null,
        tint = readable.ink,
        modifier = Modifier.size(size * 0.28f),
      )
    } else {
      FluidMonogramText(letter, size, readable.ink, 0.26f)
    }
    item.image?.invoke()
  }
}

@Composable
private fun FluidAvatarOverflowCell(text: String, size: Dp, modifier: Modifier) {
  val scheme = MaterialTheme.colorScheme
  Box(modifier = modifier.background(scheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
    FluidMonogramText(text, size, scheme.onSurface, 0.22f)
  }
}

/**
 * Il nome per TalkBack di un mosaico: i nomi che si vedono, e il resto detto con le parole dell'app
 * ([FluidLabels.moreAvatars]: «e altri 3»).
 *
 * @param names i nomi di **tutte** le persone, nell'ordine del mosaico.
 * @param max lo stesso tetto del mosaico; i nomi oltre le celle vere (una in meno dell'ultima, se ce n'e'
 *   una che dice il resto) vanno nel «e altri».
 */
fun fluidAvatarStackDescription(
  names: List<String>,
  max: Int = FluidAvatarDefaults.StackMax,
  labels: FluidLabels = FluidLabels(),
): String {
  val layout = fluidAvatarStackCells(names.size, max)
  val named = names.take(layout.visible.coerceAtLeast(0))
  val rest = names.size - named.size
  val list = named.joinToString(", ")
  return if (rest > 0) "$list ${String.format(Locale.ROOT, labels.moreAvatars, rest)}".trim() else list
}

// --- Il monogramma -------------------------------------------------------------------------------

/**
 * Le iniziali di un nome, per grafemi e non per caratteri: la lettera che si mette in un cerchio.
 *
 * **Perche' per grafemi.** «Émile» scritto con la E e l'accento come due caratteri separati e'
 * una lettera sola; «👨‍👩‍👧 Famiglia» comincia con un'emoji che e' sette caratteri; «🇮🇹 Italia»
 * con una bandiera che ne sono due. Prendere `name[0]` produceva un quadratino, mezza emoji o una E
 * senza accento — e un cerchio con un quadratino dentro e' il bug piu' visibile di un avatar.
 *
 * **Regole**, nell'ordine:
 * 1. Si tolgono i caratteri di controllo e quelli che invertono la direzione (marcatori bidi,
 *    spazi di larghezza zero, la sillabazione): non devono finire in un cerchio, e un nome costruito per
 *    ribaltare la riga accanto non lo fa. Lo ZWJ resta: tiene insieme le emoji composte.
 * 2. Un indirizzo (`anna.rossi+lavoro@x.it`) vale per la sua parte locale senza il suffisso dopo il `+`:
 *    «AR». Una `@mano` vale per `mano`.
 * 3. Le parole sono separate da spazi e da `. , ; : / \ _ ( ) [ ] { } < > « » " |`. Il trattino
 *    e l'apostrofo **non** separano: «Jean-Pierre» e «D'Angelo» sono una parola.
 * 4. Di ogni parola vale il primo grafema che sia una lettera, una cifra o un'emoji: `"Anna"` fra
 *    virgolette e `(Ufficio)` danno «A» e «U». Le parole fatte solo di simboli si saltano.
 * 5. Si prendono fino a [maxLetters] iniziali: una per parola, e se le parole sono di piu' la prima
 *    e l'ultima — «Anna Maria Rossi» e' «AR», non «AM». Con piu' di due lettere: le prime
 *    `maxLetters - 1` parole e l'ultima. Con una lettera sola, quella della prima parola.
 * 6. Maiuscole **del primo grafema**: «ß» diventa «S», non «SS» (due lettere in un cerchio da una). Le
 *    scritture senza maiuscole (arabo, ebraico, cinese) restano come sono.
 *
 * Una parola sola da' **una** lettera, anche con `maxLetters = 2`: «Anna» e' «A». Un nome senza
 * parole valide (vuoto, solo simboli o spazi) da' la stringa vuota: l'avatar mostra il glifo di una
 * persona. Non si guarda oltre i primi 512 caratteri: un nome piu' lungo non e' un nome.
 *
 * Il cinese e il giapponese senza spazi sono una parola sola e danno il primo carattere, non il cognome:
 * sapere dove finisce il cognome non e' una cosa che si indovina da una stringa.
 *
 * @param maxLetters da 1 in su; valori sotto 1 valgono 1.
 */
fun fluidMonogram(name: String, maxLetters: Int = FluidAvatarDefaults.MonogramLetters): String {
  val limit = maxLetters.coerceAtLeast(1)
  val clean = fluidMonogramSanitize(name.take(FluidMonogramMaxInput))
  if (clean.isBlank()) return ""

  var source = clean
  if ('@' in source && source.none { it.isWhitespace() }) {
    val local = source.substringBefore('@').ifEmpty { source.substringAfter('@') }
    source = local.substringBefore('+').ifEmpty { local }
  }
  val initials = source
    .split(FluidMonogramSeparators)
    .mapNotNull { word -> fluidFirstInitial(word) }
  if (initials.isEmpty()) return ""

  val picked = if (initials.size <= limit) {
    initials
  } else if (limit == 1) {
    initials.take(1)
  } else {
    initials.take(limit - 1) + initials.last()
  }
  return picked.joinToString("")
}

private const val FluidMonogramMaxInput = 512

private val FluidMonogramSeparators = Regex("[\\s\\u00A0\\u3000.,;:/\\\\_()\\[\\]{}<>«»\"|]+")

/** Toglie controlli, marcatori bidi e spazi di larghezza zero; porta `\t`, `\n`, `\r` a spazi. */
internal fun fluidMonogramSanitize(text: String): String {
  val out = StringBuilder(text.length)
  var i = 0
  while (i < text.length) {
    val cp = text.codePointAt(i)
    i += Character.charCount(cp)
    when {
      cp == '\t'.code || cp == '\n'.code || cp == '\r'.code -> out.append(' ')
      cp < 0x20 || cp in 0x7F..0x9F -> Unit
      cp == 0x00AD || cp == 0x061C || cp == 0x180E -> Unit
      cp == 0x200B || cp == 0x200E || cp == 0x200F -> Unit
      cp in 0x202A..0x202E || cp in 0x2060..0x2064 || cp in 0x2066..0x2069 -> Unit
      cp == 0xFEFF -> Unit
      else -> out.appendCodePoint(cp)
    }
  }
  return out.toString()
}

/** Il primo grafema di [word] che sia una lettera, una cifra o un'emoji, in maiuscolo; null se non c'e'. */
private fun fluidFirstInitial(word: String): String? {
  for (g in graphemes(word)) {
    val first = g.codePointAt(0)
    if (Character.isLetterOrDigit(first) || isEmojiLike(first)) {
      val upper = g.uppercase(Locale.ROOT)
      // «ß» -> «SS»: due grafemi dove ne serviva uno.
      return graphemes(upper).firstOrNull() ?: g
    }
  }
  return null
}

/** Emoji e pittogrammi, abbastanza per non scambiarli per punteggiatura. */
private fun isEmojiLike(cp: Int): Boolean =
  cp in 0x1F000..0x1FAFF ||
    cp in 0x2600..0x27BF ||
    cp in 0x2B00..0x2BFF ||
    cp == 0x2122 || cp == 0x2139 || cp == 0x203C || cp == 0x2049 || cp == 0x00A9 || cp == 0x00AE

/**
 * I grafemi di un testo: i confini del sistema (`BreakIterator`: segni combinanti, hangul, indic) piu'
 * le sequenze che quel confine, nelle versioni di Java piu' vecchie, taglia a meta': le emoji tenute
 * insieme dallo ZWJ, i modificatori di tono della pelle, i selettori di variante, le tessere dei
 * tasti e le bandiere (due indicatori regionali).
 */
internal fun graphemes(text: String): List<String> {
  if (text.isEmpty()) return emptyList()
  val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
  iterator.setText(text)
  val clusters = ArrayList<String>()
  var start = iterator.first()
  var end = iterator.next()
  while (end != BreakIterator.DONE) {
    clusters += text.substring(start, end)
    start = end
    end = iterator.next()
  }
  val merged = ArrayList<String>(clusters.size)
  for (cluster in clusters) {
    val previous = merged.lastOrNull()
    if (previous != null && joinsPrevious(previous, cluster)) {
      merged[merged.lastIndex] = previous + cluster
    } else {
      merged += cluster
    }
  }
  return merged
}

private fun joinsPrevious(previous: String, next: String): Boolean {
  val nextFirst = next.codePointAt(0)
  val previousLast = previous.codePointBefore(previous.length)
  return previousLast == 0x200D ||
    nextFirst == 0x200D ||
    nextFirst in 0xFE00..0xFE0F ||
    nextFirst in 0x1F3FB..0x1F3FF ||
    nextFirst == 0x20E3 ||
    nextFirst in 0xE0020..0xE007F ||
    (isRegionalIndicator(nextFirst) && previous.codePointCount(0, previous.length) == 1 &&
      isRegionalIndicator(previous.codePointAt(0)))
}

private fun isRegionalIndicator(cp: Int): Boolean = cp in 0x1F1E6..0x1F1FF
